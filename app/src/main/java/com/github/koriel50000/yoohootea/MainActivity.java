package com.github.koriel50000.yoohootea;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ai.api.AIConfiguration;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import twitter4j.FilterQuery;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.StatusAdapter;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.auth.AccessToken;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private MessageView messageView;
    private User myAccount;
    private User yooHooBot;

    private int preVolume = -1;
    private RecordingThread recordingThread;

    private SpeechTask speechTask;
    private TweetTask tweetTask;
    private ReplyTask replyTask;
    private TimelineTask timelineTask;

    private TextToSpeech textToSpeech;

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

        setProperVolume();
        AppResCopy.copyResFromAssetsToSD(this);
        recordingThread = new RecordingThread(handle, new AudioDataSaver());
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
        long userId = Long.parseLong(sharedPreferences.getString("user_id", "0"));
        String screenName = sharedPreferences.getString("screen_name", ""); // FIXME screenNameの変更に対応できない
        if (!token.equals("") && !tokenSecret.equals("")) {
            TwitterUtils.initialize(token, tokenSecret, userId, screenName);
        }
    }

    private void initMessageView() {
        String screenName = TwitterUtils.getScreenName();
        String url = "https://pbs.twimg.com/profile_images/1185423827125161984/yMLq3Qln_normal.jpg";
        // FIXME url
        myAccount = new User(this, 0, screenName, url); // FIXME 認証直後は反映されない
        yooHooBot = new User(this, 1, "YooHoo");

        messageView = findViewById(R.id.message_view);
    }

    private void showMessage(final User user, final boolean isRight, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Message callMessage = new Message.Builder()
                        .setUser(user)
                        .setRight(isRight)
                        .setText(text)
                        .build();
                messageView.setMessage(callMessage);
                messageView.scrollToEnd();
            }
        });
    }

    private void setProperVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        preVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int properVolume = (int) ((float) maxVolume * 0.2); // 適切な音量として最大音量の20%に設定
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
        recordingThread.stopRecording();

        speechTask.startListening();
    }

    private void speechRecognition(String keyword) {
        String speech = "おーい " + keyword;
        showMessage(myAccount, true, speech);

        tweetTask.execute(speech, keyword);
    }

    private void speechRecognitionCanceled() {
        recordingThread.startRecording();
    }

    private void speechRecognitionFailed() {
        showMessage(myAccount, true, "...");

        recordingThread.startRecording();
    }

    private void tweetResponsed(Long statusId, String keyword) {
        if (statusId != null) {
            replyTask.execute(statusId, keyword);
        } else {
            String speech = "どうしたの？";
            showMessage(yooHooBot, false, speech);

            speechSynthesis(speech);
        }
    }

    private void tweetResponseFailed() {
        String speech = "なんですか？";
        showMessage(yooHooBot, false, speech);

        speechSynthesis(speech);
    }

    private void replyResponsed(String speech) {
        showMessage(yooHooBot, false, speech);

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

    private static class SoundChanges {

        enum State {
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
                if (levels.size() < WINDOW_SIZE) {
                    return true;
                }

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

                Log.d(TAG, "minmax=" + minmax + ", avg=" + avg);
                if (isSilent) {
                    return avg <= THRESHOLD_LEVEL; // 無音の場合は平均値が閾値以下ならば範囲内
                } else {
                    return avg > THRESHOLD_LEVEL; // 有音の場合は平均値が閾値より大きければ範囲内
                }
            }

            boolean isSufficient(int timer) {
                return timer > minTime;
            }

            boolean isTimeout(int timer) {
                return timer > maxTime;
            }

            boolean isSilent() {
                return isSilent;
            }
        }

        private static final int WINDOW_SIZE = 5;
        private static final float THRESHOLD_LEVEL = 3.0f;

        private List<Sound> sounds;

        private Queue<Float> levels;
        private Sound current;
        private int index;
        private int timer;

        SoundChanges() {
            sounds = new ArrayList<>();
            levels = new ArrayDeque<>(WINDOW_SIZE);
            index = 0;
            timer = 0;
        }

        SoundChanges add(boolean isSilent, int minTime, int maxTime) {
            sounds.add(new Sound(isSilent, minTime, maxTime));
            return this;
        }

        void reset() {
            index = 0;
            timer = 0;
            current = sounds.get(index++);
            levels.clear();
        }

        State level(float level) {
            if (levels.size() == WINDOW_SIZE) {
                levels.remove();
            } // Javaにサイズ固定キューってなかったっけ？
            levels.add(Math.abs(level));
            timer++;

            Log.d(TAG, levels + ", sound=" + current.isSilent() + ", index=" + index + ", timer=" + timer);

            if (current.isTimeout(timer)) {
                return State.TIMEOUT;
            }

            if (index == sounds.size()) { // 最後の音量変化
                if (!current.inRange(levels)) {
                    return State.OUTOFRANGE;
                } else if (current.isSufficient(timer)) {
                    return State.SUFFICIENT;
                } else {
                    return State.CONTINUE;
                }
            } else { // 最後以外の音量変化
                if (current.inRange(levels)) {
                    return State.CONTINUE;
                } else if (current.isSufficient(timer)) {
                    current = sounds.get(index++);
                    timer = 0;
                    return State.NEXT;
                } else {
                    return State.OUTOFRANGE;
                }
            }
        }
    }

    private class SpeechTask implements AIListener {

        private SoundChanges soundChanges = new SoundChanges()
                .add(true, 5, 50)
                .add(false, 5, 100)
                .add(true, 10, 20);

        private AIService aiService;

        private SpeechTask(Context context) {
            AIConfiguration config = new AIConfiguration(
                    "6cab6813dc8c416f92c3c2e2b4a7bc27",
                    AIConfiguration.SupportedLanguages.fromLanguageTag("ja"),
                    AIConfiguration.RecognitionEngine.System);

            aiService = AIService.getService(context, config);
            aiService.setListener(this);
        }

        private void pause() {
            aiService.pause();
        }

        private void resume() {
            aiService.resume();
        }

        private void startListening() {
            aiService.startListening();
            soundChanges.reset();
        }

        @Override
        public void onResult(AIResponse response) {
            speechRecognition(response.getResult().getResolvedQuery());
        }

        @Override
        public void onError(AIError error) {
            Log.e(TAG, error.toString());
            speechRecognitionFailed();
        }

        @Override
        public void onAudioLevel(float level) {
            //Log.d(TAG, String.format("onAudioLevel: %s", level));

            switch (soundChanges.level(level)) {
                case CONTINUE:
                    //Log.d(TAG,"CONTINUE:");
                    break;
                case NEXT:
                    //Log.d(TAG,"NEXTSTATE:");
                    break;
                case SUFFICIENT:
                    //Log.d(TAG,"SUFFICIENT:");
                    aiService.stopListening();
                    break;
                case OUTOFRANGE:
                    //Log.d(TAG,"OUTOFRANGE:");
                    aiService.cancel();
                    break;
                case TIMEOUT:
                    //Log.d(TAG,"TIMEOUT:");
                    aiService.cancel();
                    break;
            }
        }

        @Override
        public void onListeningStarted() { }

        @Override
        public void onListeningCanceled() {
            //Log.d(TAG, "onListeningCanceled");
            aiService.stopListening(); // FIXME 必要？
            speechRecognitionCanceled();
        }

        @Override
        public void onListeningFinished() { }
    }

    private class TweetTask {

        private AsyncTask<Void, Void, Long> task;

        private Twitter twitter;

        private String location;
        private String gender;

        private TweetTask() {
            twitter = TwitterUtils.getInstance();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            location = sharedPreferences.getString("location", "somewhere");
            gender = sharedPreferences.getString("gender", "other");
        }

        private void pause() {
        }

        private void resume() {
        }

        private void execute(final String speech, final String keyword) {
            task = new AsyncTask<Void, Void, Long>() {
                @Override
                protected Long doInBackground(Void... params) {
                    try {
                        Long duplicateId = getDuplicateTweet(speech);
                        Log.d(TAG, "speech: " + speech + ", duplicateId: " + duplicateId);
                        Long statusId;
                        if (duplicateId == null) {
                            statusId = tweet(speech);
                        } else {
                            statusId = retweet(duplicateId);
                        }
                        boolean retweeted = HttpUtils.requestToRetweet(statusId);
                        Log.d(TAG, "statusId: " + statusId + ", retweeted: " + retweeted);
                        return retweeted ? statusId : null;
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        cancel(true);
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
                    .query("from:" + screenName)
                    .resultType(Query.ResultType.recent)
                    .lang("ja");
            QueryResult result = twitter.search(searchQuery);
            Long statusId = null;
            for (Status status : result.getTweets()) {
                // リツイートと引用リツイートを除外、本文が"『speech』"に後方一致
                if (!status.isRetweet() && status.getQuotedStatus() == null &&
                        status.getText().endsWith("『" + speech + "』"))
                {
                    statusId = status.getId();
                    break;
                }
            }
            return statusId;
        }

        private long tweet(String speech) throws TwitterException {
            Log.d(TAG, "gender: " + gender + ", location: " + location);
            String message = "北海道のおばあちゃんがつぶやいてます。\n"
                    + "『" + speech + "』";
            twitter4j.Status status = twitter.updateStatus(message);
            Log.d(TAG, "tweet: " + status.toString());
            return status.getId();
        }

        private long retweet(long statusId) throws TwitterException {
            twitter4j.Status status = twitter.retweetStatus(statusId);
            Log.d(TAG, "retweet: " + status.toString());
            return status.getId();
        }
    }

    private class ReplyTask {

        private AsyncTask<Void, Void, String> task;

        private Twitter twitter;
        private TwitterStream twitterStream;

        private volatile long replyId;
        private volatile String replyMessage;
        private CountDownLatch latch;

        private ReplyTask() {
            twitter = TwitterUtils.getInstance();
            twitterStream = TwitterUtils.getStreamInstance();
        }

        private void pause() {
        }

        private void resume() {
        }

        private void execute(final long tweetId, final String keyword) {
            task = new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    try {
                        latch = new CountDownLatch(2);

                        long userId = TwitterUtils.getUserId();
                        startWaitingForReply(userId, tweetId);
                        twitter4j.Status status = searchHashtag(keyword);

                        latch.await(60, TimeUnit.SECONDS);

                        if (replyMessage == null && status != null) {
                            replyId = status.getId();
                            replyMessage = status.getText();
                        }
                        if (replyMessage != null) {
                            quotedRetweet(replyId, keyword);
                        } else {
                            replyMessage = "今、手が離せなくて。";
                        }
                        return replyMessage;
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        cancel(true);
                        replyResponseFailed();
                        return null;
                    } finally {
                        twitterStream.shutdown();
                    }
                }

                @Override
                protected void onPostExecute(String speech) {
                    replyResponsed(speech);
                }
            };
            task.execute();
        }

        private void startWaitingForReply(long followId, final long tweetId) {
            twitterStream.addListener(new StatusAdapter() {
                @Override
                public void onStatus(Status status) {
                    if (status.getInReplyToStatusId() == tweetId) {
                        boolean result = HttpUtils.requestToReview(status.getText());
                        if (result) {
                            replyId = status.getId();
                            replyMessage = status.getText();
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
        }

        private Status searchHashtag(String keyword) throws TwitterException {
            Query searchQuery = new Query()
                    .query("to:yoohootea #おーい #" + keyword)
                    .resultType(Query.ResultType.mixed)
                    .lang("ja");
            QueryResult result = twitter.search(searchQuery);
            int count = result.getCount();
            Status status = null;
            if (count > 0) {
                int index = new Random().nextInt(count);
                status = result.getTweets().get(index);
                Log.d(TAG, "search: " + status.toString());
            }
            return status;
        }

        private void quotedRetweet(long replyId, String keyword) throws TwitterException {
            // TODO 引用リツイートの方法は？
            twitter.retweetStatus(replyId);
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
        }

        private void pause() {
        }

        private void resume() {
        }

        private void schedule() throws TwitterException, InterruptedException {
            while (alive) {
                long sinceId = 0L; // TODO 保存から取得
                Paging paging = new Paging().sinceId(sinceId);
                for (Status status : twitter.getUserTimeline(screenName, paging)) {
                    long replyId = status.getInReplyToStatusId();
                    Status replyStatus = twitter.showStatus(replyId);
                    // TODO リプライの本文がおーい○○
                    if (replyStatus.getText().endsWith("")) {
                        String query = "";

                    }
                    sinceId = status.getId();
                    // TODO sinceIdを保存
                    Thread.sleep(2 * 1000);
                }
            }
            handler.postDelayed(runnable, 120 * 1000);
        }

        private void start() {
            if (!alive) {
                alive = true;
                handler.post(runnable);
            }
        }

        private void stop() {
            alive = false;
        }
    }
}
