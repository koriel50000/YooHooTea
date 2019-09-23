package com.github.koriel50000.yoohootea;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import ai.api.AIConfiguration;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private MessageView messageView;
    private User myAccount;
    private User yooHooBot;

    private int preVolume = -1;
    private RecordingThread recordingThread;

    private SpeechTask speechTask;
    private ChatbotTask chatbotTask;

    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speechTask = new SpeechTask(this);
        chatbotTask = new ChatbotTask(this);

        initMessageView();
        initTextToSpeech();

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
        chatbotTask.pause();

        recordingThread.stopRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // use this method to reinit connection to recognition service
        speechTask.resume();
        chatbotTask.resume();

        recordingThread.startRecording();
    }

    @Override
    public void onDestroy() {
        recordingThread.stopRecording();
        restoreVolume();
        textToSpeech.shutdown();

        super.onDestroy();
    }

    private void initMessageView() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_user);
        myAccount = new User(0, "koriel", icon);
        yooHooBot = new User(1, "YooHoo", icon);

        messageView = findViewById(R.id.message_view);
    }

    private void showMessage(final User user, final boolean isRight, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Message callMessage = new Message.Builder()
                        .setUser(user)
                        .setRightMessage(isRight)
                        .setMessageText(text)
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

    private void speechRecognition(String query) {
        String speech = "おーい " + query;
        showMessage(myAccount, true, speech);

        chatbotTask.execute(speech);
    }

    private void speechRecognitionCanceled() {
        recordingThread.startRecording();
    }

    private void speechRecognitionFailed() {
        showMessage(myAccount, true, "...");

        recordingThread.startRecording();
    }

    private void chatbotResponsed(String speech) {
        showMessage(yooHooBot, false, speech);

        speechSynthesis(speech);
    }

    private void chatbotResponseFailed() {
        String speech = "ちょっと手が離せなくて";

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
            Log.d(TAG, "onResult");
            speechRecognition(response.getResult().getResolvedQuery());
        }

        @Override
        public void onError(AIError error) {
            Log.e(TAG, error.toString());
            speechRecognitionFailed();
        }

        @Override
        public void onAudioLevel(float level) {
            Log.d(TAG, String.format("onAudioLevel: %s", level));

            switch (soundChanges.level(level)) {
                case CONTINUE:
                    Log.d(TAG,"CONTINUE:");
                    break;
                case NEXT:
                    Log.d(TAG,"NEXTSTATE:");
                    break;
                case SUFFICIENT:
                    Log.d(TAG,"SUFFICIENT:");
                    aiService.stopListening();
                    break;
                case OUTOFRANGE:
                    Log.d(TAG,"OUTOFRANGE:");
                    aiService.cancel();
                    break;
                case TIMEOUT:
                    Log.d(TAG,"TIMEOUT:");
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

    private class ChatbotTask implements AIListener {

        private AIService aiService;
        private AsyncTask<String, Void, AIResponse> task;

        private ChatbotTask(Context context) {
            AIConfiguration config = new AIConfiguration(
                    Constants.DIALOGFLOW_CLIENT_ACCESS_TOKEN,
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

        private void execute(String query) {
            task = new AsyncTask<String, Void, AIResponse>() {
                @Override
                protected AIResponse doInBackground(String... params) {
                    try {
                        return aiService.textRequest(params[0], null);
                    } catch (Exception e) {
                        cancel(true); // FIXME cancel後にonErrorは呼ばれないのでは？
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(AIResponse response) {
                    Result result = response.getResult();
                    String speech = result.getFulfillment().getSpeech();
                    chatbotResponsed(speech);
                }
            };
            task.execute(query);
        }

        @Override
        public void onResult(AIResponse result) { }

        @Override
        public void onError(AIError error) {
            Log.e(TAG, error.getMessage());
            chatbotResponseFailed();
        }

        @Override
        public void onAudioLevel(float level) { }

        @Override
        public void onListeningStarted() { }

        @Override
        public void onListeningCanceled() { }

        @Override
        public void onListeningFinished() { }
    }
}
