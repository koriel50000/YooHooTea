package com.github.koriel50000.yoohootea;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.api.AIConfiguration;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.Constants;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.RecordingThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import twitter4j.FilterQuery;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private static final String LIKE_TEXT = "\uD83D\uDC4D";
    private static final String PHONE_TEXT = "\u260E";

    private MessageView messageView;
    private User myAccount;
    private User yooHooBot;
    private User tweetBot;

    private Message currentMessage;
    private LikeHandler likeHandler;

    private int preVolume = -1;
    private RecordingThread recordingThread;

    private SpeechTask speechTask;
    private TweetTask tweetTask;
    private ReplyTask replyTask;
    private TimelineTask timelineTask;

    private TextToSpeech textToSpeech;

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPreferences();
        initMessageView();
        initTextToSpeech();

        speechTask = new SpeechTask(this);
        tweetTask = new TweetTask();
        replyTask = new ReplyTask();
        timelineTask = new TimelineTask();
        likeHandler = new LikeHandler();

        setProperVolume();
        AppResCopy.copyResFromAssetsToSD(this);
        recordingThread = new RecordingThread(handle);
        recordingThread.startRecording();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // use this method to disconnect from speech recognition service
        // Not destroying the SpeechRecognition object in onPause method would block other apps from using SpeechRecognition service
        speechTask.pause();
        tweetTask.pause();
        replyTask.pause();
        timelineTask.pause();

        recordingThread.stopRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // use this method to reinit connection to recognition service
        speechTask.resume();
        tweetTask.resume();
        replyTask.resume();
        timelineTask.resume();

        recordingThread.startRecording();
    }

    @Override
    public void onDestroy() {
        recordingThread.stopRecording();
        restoreVolume();
        textToSpeech.shutdown();

        TwitterUtils.destroy();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String token = sharedPreferences.getString("oauth_token", "");
        String tokenSecret = sharedPreferences.getString("oauth_token_secret", "");
        long userId = sharedPreferences.getLong("user_id", 0);
        String screenName = sharedPreferences.getString("screen_name", ""); // FIXME screenNameの変更に対応できない
        String imageURL = sharedPreferences.getString("profile_image_url", "");

        TwitterUtils.initialize(token, tokenSecret, userId, screenName, imageURL);
    }

    private void initMessageView() {
        String screenName = TwitterUtils.getScreenName();
        String imageURL = TwitterUtils.getProfileImageURL();
        myAccount = new User(this, 0, screenName, imageURL); // FIXME 認証直後は反映されない
        yooHooBot = new User(this, 1, "YooHoo");
        tweetBot = new User(this, 2, "John Smith");

        messageView = findViewById(R.id.message_view);

        messageView.setBackgroundColor(Color.rgb(121, 148, 191));
        messageView.setLeftBubbleColor(Color.WHITE);
        messageView.setRightBubbleColor(Color.rgb(155, 224,97));
        messageView.setLeftMessageTextColor(Color.DKGRAY);
        messageView.setRightMessageTextColor(Color.DKGRAY);
        messageView.setUsernameTextColor(Color.WHITE);
        messageView.setSendTimeTextColor(Color.WHITE);
        messageView.setDateSeparatorTextColor(Color.WHITE);
        messageView.setMessageMaxWidth(640);
    }

    private void showMessage(final User user, final boolean isRight, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentMessage = new Message.Builder()
                        .setUser(user)
                        .setRight(isRight)
                        .setText(text)
                        .build();
                messageView.setMessage(currentMessage);
                messageView.scrollToEnd();
            }
        });
    }

    private void replaceMessage(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageView.remove(currentMessage);
                currentMessage = new Message.Builder()
                        .setUser(currentMessage.getUser())
                        .setRight(currentMessage.isRight())
                        .setText(text)
                        .build();
                messageView.setMessage(currentMessage);
            }
        });
    }

    private void setProperVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        preVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int properVolume = (int) ((float) maxVolume * 0.40); // 最大音量のnn%に設定 default:20%
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, properVolume, 0);
    }

    private void restoreVolume() {
        if (preVolume >= 0) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, preVolume, 0);
        }
    }

    private Handler handle = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            MsgEnum message = MsgEnum.getMsgEnum(msg.what);
            switch(message) {
                case MSG_ACTIVE:
                    hotwordDetected();
                    break;
                case MSG_INFO:
                    Log.d(TAG," ----> " + message);
                    break;
                case MSG_VAD_SPEECH:
                    Log.d(TAG," ----> normal voice");
                    break;
                case MSG_VAD_NOSPEECH:
                    Log.d(TAG," ----> no speech");
                    break;
                case MSG_ERROR:
                    Log.e(TAG, " ----> " + msg.toString());
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (TextToSpeech.SUCCESS == status) {
                    Log.d(TAG, "initialized");
                } else {
                    Log.e(TAG, "failed to initialize");
                }
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "onStart");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "onDone");
                speakEnded();
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "onError");
                speakEnded();
            }
        });
    }

    private void speechSynthesis(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
        }
    }

    private void hotwordDetected() {
        likeHandler.cancel();

        // hotword検出時に停止しているのでここでは不要
        //recordingThread.stopRecording();

        speechTask.startListening();
    }

    private void speechRecognition(final String keyword) {
        String speech = "おーい " + keyword;
        showMessage(myAccount, true, speech);

        //tweetTask.execute(speech, keyword);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                tweetResponsed(null, keyword + "ですか？");
            }
        }, 2000);
    }

    private void speechRecognitionCanceled() {
        // AIServiceがAudioRecordを開放するのを待ってから開始
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recordingThread.startRecording();
            }
        }, 500);
    }

    private void speechRecognitionFailed() {
        showMessage(myAccount, true, "...");

        recordingThread.startRecording();
    }

    private void tweetResponsed(Long statusId, String keyword) {
        if (statusId != null) {
            replyTask.execute(statusId, keyword);
        } else {
            //String speech = "呼んだ？";
            String speech = keyword;
            showMessage(yooHooBot, false, speech);

            speechSynthesis(speech);
        }
    }

    private void tweetResponseFailed() {
        String speech = "どうしたの？";
        showMessage(yooHooBot, false, speech);

        speechSynthesis(speech);
    }

    private void replyResponsed(String speech, String screenName, String imageURL,
                                long statusId , boolean favorited)
    {
        Log.d(TAG, "replyResponsed: " + speech + " screenName: " + screenName + " profileImageURL: " + imageURL);
        if (!screenName.equals("")) {
            tweetBot.setName(screenName);
            tweetBot.setImageURL(this, imageURL);
            if (favorited) {
                speech += LIKE_TEXT;
            }
            showMessage(tweetBot, false, speech);
            likeHandler.accept(statusId, favorited, 30 * 1000); // 30秒間受け付ける
        } else {
            showMessage(yooHooBot, false, speech);
        }

        speechSynthesis(speech);
    }

    private void replyResponseFailed() {
        String speech = "少し待てる？";
        showMessage(yooHooBot, false, speech);

        speechSynthesis(speech);
    }

    private void speakEnded() {
        recordingThread.startRecording();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (likeHandler.accepting() && e.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "KEYCODE_VOLUME_UP");
                    createFavorite();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (likeHandler.accepting() && e.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "KEYCODE_VOLUME_DOWN");
                    destroyFavorite();
                }
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void createFavorite() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (likeHandler.favorite()) {
                    replaceMessage(currentMessage.getText() + LIKE_TEXT);
                }
                return null;
            }
        }.execute();
    }

    private void destroyFavorite() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (likeHandler.unfavorite()) {
                    String text = currentMessage.getText();
                    if (text.endsWith(LIKE_TEXT)) {
                        text = text.substring(0, text.length() - LIKE_TEXT.length());
                        replaceMessage(text);
                    }
                }
                return null;
            }
        }.execute();
    }

    private static class LikeHandler {

        private Twitter twitter;

        private Handler hander;
        private Runnable runner;
        private MediaPlayer likePlayer;

        private long statusId;
        private boolean favorited;

        private volatile boolean accepting;

        private LikeHandler() {
            twitter = TwitterUtils.getInstance();
            hander = new Handler();
            runner = new Runnable() {
                @Override
                public void run() {
                    accepting = false;
                }
            };
            accepting = false;
            initAudio();
        }

        private void initAudio() {
            try {
                likePlayer = new MediaPlayer();
                likePlayer.setDataSource(Constants.DEFAULT_WORK_SPACE + "like.ogg");
                likePlayer.prepare();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        private boolean favorite() {
            if (favorited) {
                return false;
            }
            try {
                Status status = twitter.createFavorite(statusId);
                Log.d(TAG, status.toString());
                favorited = status.getFavoriteCount() > 0;
                likePlayer.start();
                return true;
            } catch (TwitterException e) {
                Log.d(TAG, e.getMessage(), e);
                return false;
            }
        }

        private boolean unfavorite() {
            if (!favorited) {
                return false;
            }
            try {
                Status status = twitter.destroyFavorite(statusId);
                Log.d(TAG, status.toString());
                favorited = status.getFavoriteCount() > 0;
                return true;
            } catch (TwitterException e) {
                Log.d(TAG, e.getMessage(), e);
                return false;
            }
        }

        private void accept(long statusId, boolean favorited, long timeout) {
            this.statusId = statusId;
            this.favorited = favorited;
            accepting = true;
            hander.postDelayed(runner, timeout);
        }

        private void cancel() {
            accepting = false;
            hander.removeCallbacks(runner);
        }

        private boolean accepting() {
            return accepting;
        }
    }

    private static class SoundChanges {

        enum State {
            IDLING,
            CONTINUE,
            NEXT,
            SUFFICIENT,
            OUTOFRANGE,
            TIMEOUT
        }

        private static class Sound {

            private boolean isSilent;
            private int minTime;
            private int maxTime;

            Sound(boolean isSilent, int minTime, int maxTime) {
                this.isSilent = isSilent;
                this.minTime = minTime;
                this.maxTime = maxTime;
            }

            boolean inRange(Queue<Float> levels) {
                float minmax = Float.NaN;
                float sum = 0.0f;
                for (float level : levels) {
                    sum += level;
                    if (Float.isNaN(minmax)) {
                        minmax = level;
                    } else if (isSilent && level > minmax) {
                        minmax = level; // 無音の場合は最大値を除いて平均
                    } else if (!isSilent && level < minmax) {
                        minmax = level; // 有音の場合は最小値を除いて平均
                    }
                }
                float avg = (sum - minmax) / (levels.size() - 1);

                if (isSilent) {
                    return avg <= THRESHOLD_LEVEL; // 無音の場合は平均値が閾値以下ならば範囲内
                } else {
                    return avg > THRESHOLD_LEVEL; // 有音の場合は平均値が閾値より大きければ範囲内
                }
            }

            boolean isSufficient(int timer) {
                return timer >= minTime;
            }

            boolean isTimeout(int timer) {
                return timer > maxTime;
            }

            boolean isSilent() {
                return isSilent;
            }
        }

        private static final int IDLING_TIME = 6;
        private static final int WINDOW_SIZE = 4;
        private static final float THRESHOLD_LEVEL = 2.0f;

        private List<Sound> sounds;

        private Queue<Float> levels;
        private Sound current;
        private int index;
        private int timer;
        private boolean idling;

        SoundChanges() {
            sounds = new ArrayList<>();
            levels = new ArrayDeque<>(WINDOW_SIZE);
        }

        SoundChanges add(boolean isSilent, int minTime, int maxTime) {
            sounds.add(new Sound(isSilent, minTime, maxTime));
            return this;
        }

        void reset() {
            index = 0;
            timer = 0;
            idling = true;
            levels.clear();
        }

        State level(float level) {
            if (levels.size() == WINDOW_SIZE) {
                levels.remove();
            } // Javaにサイズ固定キューってなかったっけ？
            levels.add(level);
            timer++;

            if (idling) {
                if (timer < IDLING_TIME) {
                    return State.IDLING;
                }
                idling = false;
                current = sounds.get(index++);
                timer = 0;
            }
            Log.d(TAG, levels + ", sound=" + current.isSilent() + ", index=" + index + ", timer=" + timer);

            if (current.isTimeout(timer)) {
                return State.TIMEOUT;
            }

            if (index < sounds.size()) { // 最後以外の音量変化
                if (current.inRange(levels)) {
                    return State.CONTINUE;
                } else if (current.isSufficient(timer)) {
                    current = sounds.get(index++);
                    timer = 0;
                    return State.NEXT;
                } else {
                    return State.OUTOFRANGE;
                }
            } else { // 最後の音量変化
                if (current.isSufficient(timer)) {
                    return State.SUFFICIENT;
                } else if (current.inRange(levels)) {
                    return State.CONTINUE;
                } else {
                    return State.OUTOFRANGE;
                }
            }
        }

        boolean currentSilent() {
            return current.isSilent();
        }
    }

    private class SpeechTask implements AIListener {

        private SoundChanges soundChanges = new SoundChanges()
                .add(false, 2, 10)
                .add(true, 3, 80)
                .add(false, 3, 120)
                .add(true, 4, 20);

        private AIService aiService;
        private boolean listening;

        private SpeechTask(Context context) {
            AIConfiguration config = new AIConfiguration(
                    "6cab6813dc8c416f92c3c2e2b4a7bc27",
                    AIConfiguration.SupportedLanguages.fromLanguageTag("ja"),
                    AIConfiguration.RecognitionEngine.System);
            aiService = AIService.getService(context, config);
            aiService.setListener(this);
        }

        private void pause() {
            //aiService.pause();
            aiService.stopListening();
            listening = false;
        }

        private void resume() {
            //aiService.resume();
        }

        private void startListening() {
            aiService.startListening();
            soundChanges.reset();
            listening = true;
        }

        @Override
        public void onResult(AIResponse response) {
            listening = false;
            speechRecognition(response.getResult().getResolvedQuery());
        }

        @Override
        public void onError(AIError error) {
            Log.e(TAG, error.toString());
            speechRecognitionFailed();
        }

        @Override
        public void onAudioLevel(float level) {
            if (!listening) {
                return;
            }
            level = level > 0 ? level : 0;
            Log.d(TAG, String.format("onAudioLevel: %s", level));

            switch (soundChanges.level(level)) {
                case IDLING:
                    Log.d(TAG,"IDLING:");
                    break;
                case CONTINUE:
                    Log.d(TAG,"CONTINUE: " + soundChanges.currentSilent());
                    break;
                case NEXT:
                    Log.d(TAG,"NEXTSTATE:" + soundChanges.currentSilent());
                    break;
                case SUFFICIENT:
                    Log.d(TAG,"SUFFICIENT:");
                    listening = false;
                    aiService.stopListening();
                    break;
                case OUTOFRANGE:
                    Log.d(TAG,"OUTOFRANGE:");
                    listening = false;
                    aiService.cancel();
                    break;
                case TIMEOUT:
                    Log.d(TAG,"TIMEOUT:");
                    listening = false;
                    aiService.cancel();
                    break;
            }
        }

        @Override
        public void onListeningStarted() { }

        @Override
        public void onListeningCanceled() {
            Log.d(TAG, "onListeningCanceled");
            aiService.stopListening(); // FIXME 必要？
            speechRecognitionCanceled();
        }

        @Override
        public void onListeningFinished() { }
    }

    private class TweetTask {

        private AsyncTask<Void, Void, Long> task;

        private Twitter twitter;

        private String wherePhrase;
        private String whoPhrase;

        private volatile boolean retweeted;

        private TweetTask() {
            twitter = TwitterUtils.getInstance();
        }

        private void initPhrase() {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String location = sharedPreferences.getString("location", "somewhere");
            String gender = sharedPreferences.getString("gender", "other");

            String[] values = getResources().getStringArray(R.array.location_values);
            for (int i = 0; i < values.length; i++) {
                if (location.equals(values[i])) {
                    String entry = getResources().getStringArray(R.array.location_entries)[i];
                    wherePhrase = entry + "の";
                    break;
                }
            }
            if (location.equals("somewhere") || wherePhrase == null) {
                wherePhrase = "どこかで";
            }

            switch (gender) {
                case "male":
                    whoPhrase = "おじいちゃんが";
                    break;
                case "female":
                    whoPhrase = "おばあちゃんが";
                    break;
                default:
                    whoPhrase = "誰かが";
                    break;
            }
        }

        private void pause() {
        }

        private void resume() {
        }

        private void execute(final String speech, final String keyword) {
            initPhrase(); // 仮に設定を毎回確認する
            task = new AsyncTask<Void, Void, Long>() {
                @Override
                protected Long doInBackground(Void... params) {
                    try {
                        final CountDownLatch latch = new CountDownLatch(1);
                        retweeted = false;

                        Long duplicateId = getDuplicateTweet(speech);
                        Log.d(TAG, "speech: " + speech + ", duplicateId: " + duplicateId);
                        if (duplicateId != null) {
                            return duplicateId;
                        }
                        Long statusId = tweet(speech);

                        HttpUtils.requestToRetweetAsync(statusId, new Callback() {
                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                Log.d(TAG, "requestToRetweetAsync - onResponse");
                                HttpUtils.JsonResponse jsonResponse = new HttpUtils.JsonResponse(
                                        response.body().string());

                                retweeted = jsonResponse.getBoolean("result");
                                latch.countDown();
                            }

                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.e(TAG, e.getMessage(), e);
                                latch.countDown();
                            }
                        });

                        latch.await();
                        Log.d(TAG, "statusId: " + statusId + ", retweeted: " + retweeted);

                        return retweeted ? statusId : null;
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        cancel(true); // onPostExecuteは呼ばれない
                        tweetResponseFailed();
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(Long statusId) {
                    tweetResponsed(statusId, keyword);
                }
            };
            task.execute();
        }

        private Long getDuplicateTweet(String speech) throws TwitterException {
            String screenName = TwitterUtils.getScreenName();
            Query searchQuery = new Query()
                    .query("from:" + screenName + " -filter:retweets")
                    .resultType(Query.ResultType.recent)
                    .lang("ja");
            QueryResult result = twitter.search(searchQuery);

            Long statusId = null;
            for (Status status : result.getTweets()) {
                // 12時間以内、リツイートと引用リツイートを除外、本文が"『speech』"に後方一致
                if (status.getCreatedAt().after(new Date(System.currentTimeMillis() - 12 * 60 * 60 * 1000)) &&
                        !status.isRetweet() && status.getQuotedStatus() == null &&
                        status.getText().endsWith("『" + speech + "』"))
                {
                    Log.d(TAG, "duplicate tweet: " + status.toString());
                    statusId = status.getId();
                    break;
                }
            }
            return statusId;
        }

        private long tweet(String speech) throws TwitterException {
            String message = wherePhrase + whoPhrase + "つぶやいてます。\n"
                    + "『" + speech + "』";
            twitter4j.Status status = twitter.updateStatus(message);
            Log.d(TAG, "tweet: " + status.toString());
            return status.getId();
        }
    }

    private class ReplyTask {

        private AsyncTask<Void, Void, String> task;

        private Twitter twitter;
        private TwitterStream twitterStream;

        private volatile String replyMessage;
        private volatile long replyId;
        private volatile String screenName;
        private volatile String imageURL;
        private volatile boolean favorited;
        private CountDownLatch latch;

        private ReplyTask() {
            twitter = TwitterUtils.getInstance();
            twitterStream = TwitterUtils.getStreamInstance();
        }

        private void pause() {
        }

        private void resume() {
        }

        private void execute(final long statusId, final String keyword) {
            task = new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    try {
                        replyMessage = null;
                        latch = new CountDownLatch(1); // 本来は検索も非同期にしてカウントを2にすべき

                        startWaitingForReply(TwitterUtils.getUserId(), statusId);
                        twitter4j.Status status = searchNotHashtag(keyword); // ハッシュタグ検索がうまくいかないため緊急回避

                        latch.await(30, TimeUnit.SECONDS); // 30秒以内

                        if (replyMessage == null && status != null) {
                            replyId = status.isFavorited() ? 0 : status.getId();
                            screenName = status.getUser().getScreenName();
                            imageURL = status.getUser().getProfileImageURL();
                            favorited = status.getFavoriteCount() > 0;
                            replyMessage = TwitterUtils.parseText(status.getText());
                        } else if (replyMessage == null) {
                            replyId = 0;
                            screenName = "";
                            imageURL = "";
                            favorited = false;
                            replyMessage = "今、手が離せなくて。";
                        }
                        Log.d(TAG, "replyMessage: " + replyMessage);
                        return replyMessage;
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        cancel(true);
                        replyResponseFailed();
                        return null;
                    } finally {
                        twitterStream.clearListeners();
                    }
                }

                @Override
                protected void onPostExecute(String speech) {
                    replyResponsed(speech, screenName, imageURL, replyId, favorited);
                }

                private void startWaitingForReply(long followId, final long statusId) {
                    twitterStream.addListener(new StatusAdapter() {
                        @Override
                        public void onStatus(twitter4j.Status status) {
                            Log.d(TAG, "onStatus: " + status.getText());
                            if (status.getInReplyToStatusId() == statusId) {
                                boolean result = true; //HttpUtils.requestToReview(status.getText());
                                if (result) {
                                    replyId = status.isFavorited() ? 0 : status.getId();
                                    screenName = status.getUser().getScreenName();
                                    imageURL = status.getUser().getProfileImageURL();
                                    favorited = status.getFavoriteCount() > 0;
                                    replyMessage = TwitterUtils.parseText(status.getText());
                                    latch.countDown();
                                }
                            }
                        }

                        @Override
                        public void onException(Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                            latch.countDown();
                        }
                    });
                    FilterQuery filterQuery = new FilterQuery()
                            .follow(followId)
                            .language("ja");
                    twitterStream.filter(filterQuery);
                    Log.d(TAG, "filter: " + filterQuery.toString());
                }
            };
            task.execute();
        }

        private Status searchNotHashtag(String keyword) throws TwitterException {
            // FIXME なぜかTwitter4Jではto:検索がエラーとなる
            String screenName = TwitterUtils.getScreenName();
            Query searchQuery = new Query()
                    .query("to:" + screenName + " -filter:retweets")
                    .resultType(Query.ResultType.recent)
                    .lang("ja");
            QueryResult result = twitter.search(searchQuery);
            Log.d(TAG, "searchQuery: " + searchQuery +  " result count: " + result.getCount());

            List<Status> replies = new ArrayList<>();
            for (Status status : result.getTweets()) {
                // リツイートと引用リツイートを除外
                if (!status.isRetweet() && status.getQuotedStatus() == null) {
                    long replyId = status.getInReplyToStatusId();
                    Status replyStatus = twitter.showStatus(replyId);
                    // リプライ元の本文が『おーい keyword』に後方一致
                    if (replyStatus.getText().endsWith("『おーい " + keyword + "』")) {
                        replies.add(status);
                    }
                }
            }
            Log.d(TAG, "replies count: " + replies.size());

            Status status = null;
            if (replies.size() > 0) {
                int index = new Random().nextInt(replies.size());
                status = replies.get(index);
                Log.d(TAG, "search tweet: " + status.getText());
            }
            return status;
        }
    }

    private class TimelineTask {

        private Twitter twitter;
        private String screenName;

        private Handler handler;
        private Runnable runnable;
        private volatile boolean alive;

        private TimelineTask() {
            twitter = TwitterUtils.getInstance();
            screenName = TwitterUtils.getScreenName();
            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        schedule();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            };
            //start();
        }

        private void pause() {
            //start();
        }

        private void resume() {
            //stop();
        }

        private void start() {
            if (!alive) {
                alive = true;
                handler.post(runnable);
            }
        }

        private void stop() {
            alive = false;
            handler.removeCallbacks(runnable);
        }

        private void schedule() throws TwitterException, InterruptedException {
            while (alive) {
                long sinceId = restoreSinceId();
                String screenName = TwitterUtils.getScreenName();
                Query searchQuery = new Query()
                        .query("to:" + screenName + " -filter:retweets")
                        .sinceId(sinceId)
                        .lang("ja");
                QueryResult result = twitter.search(searchQuery);
                List<Status> replies = result.getTweets();

                Collections.reverse(replies); // すべて取得できた想定で古い順に処理する
                for (Status status : replies) {
                    if (!alive) {
                        break;
                    }
                    long replyId = status.getInReplyToStatusId();
                    Status replyStatus = twitter.showStatus(replyId);
                    // リプライ元の本文が『おーい ○○』に後方一致
                    Matcher m = Pattern.compile("『おーい (.+)』$").matcher(replyStatus.getText());
                    if (m.matches()) {
                        String keyword = m.group();
                        retweet(status.getId(), keyword);
                    }

                    sinceId = status.getId();
                    storeSinceid(sinceId);
                    Log.d(TAG, "sinceId: " + sinceId);
                    Thread.sleep(2 * 1000); // ロボット除け？
                }
            }
            if (alive) {
                handler.postDelayed(runnable, 120 * 1000); // 2分後に再実行
            }
        }

        private long retweet(long statusId, String keyword) throws TwitterException {
            twitter4j.Status status = twitter.retweetStatus(statusId);
            Log.d(TAG, "retweet: " + status.toString());
            return status.getId();
        }

        private long restoreSinceId() {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            return sharedPreferences.getLong("timeline_since_id", 1L);
        }

        private void storeSinceid(long sinceId) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("timeline_since_id", sinceId);
            editor.commit();
        }
    }
}
