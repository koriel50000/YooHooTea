package com.github.koriel50000.yoohootea;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;

import ai.api.AIConfiguration;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.RequestExtras;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;
import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;

public class MainActivity extends AppCompatActivity implements AIListener {

    public static final String TAG = MainActivity.class.getName();

    private int preVolume = -1;
    private RecordingThread recordingThread;

    private MessageView messageView;
    private User myAccount;
    private User yooHooBot;

    private AIService aiService;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initAIService();
        initTextToSpeech();
        initMessageView();

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
        if (aiService != null) {
            aiService.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // use this method to reinit connection to recognition service
        if (aiService != null) {
            aiService.resume();
        }
    }

    @Override
    public void onDestroy() {
        recordingThread.stopRecording();
        restoreVolume();

        super.onDestroy();
    }

    public Handler handle = new Handler() {
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
                    Log.d(TAG, " ----> " + msg.toString());
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

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

    private void initMessageView() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_user);
        myAccount = new User(0, "koriel", icon);
        yooHooBot = new User(1, "YooHoo", icon);

        messageView = findViewById(R.id.message_view);
    }

    private void hotwordDetected() {
        recordingThread.stopRecording();

        aiService.startListening();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                aiService.stopListening();
            }
        }, 2000);
    }

    private void callMessage(final String speech) {
        Message callMessage = new Message.Builder()
                .setUser(myAccount)
                .setRightMessage(true)
                .setMessageText("おーい " + speech)
                .build();
        messageView.setMessage(callMessage);
        messageView.scrollToEnd();

        // TODO dialogflow
        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        replyMessage("おーい " + speech);
                    }
                }, 2000);
    }

    private void replyMessage(String speech) {
        final Message replyMessage = new Message.Builder()
                .setUser(yooHooBot)
                .setRightMessage(false)
                .setMessageText(speech)
                .build();
        messageView.setMessage(replyMessage);
        messageView.scrollToEnd();

        speak(speech);
        // TODO utteranceId callback
        new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recordingThread.startRecording();
                    }
                }, 2000);
    }

    private void initAIService() {
        final AIConfiguration config = new AIConfiguration(
                "6cab6813dc8c416f92c3c2e2b4a7bc27",
                AIConfiguration.SupportedLanguages.fromLanguageTag("ja"),
                AIConfiguration.RecognitionEngine.System);

        if (aiService != null) {
            aiService.pause();
        }

        aiService = AIService.getService(this, config);
        aiService.setListener(this);
    }

    @Override
    public void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onResult");

                //resultTextView.setText(gson.toJson(response));

                Log.i(TAG, "Received success response");

                // this is example how to get different parts of result object
                final Status status = response.getStatus();
                Log.i(TAG, "Status code: " + status.getCode());
                Log.i(TAG, "Status type: " + status.getErrorType());

                final Result result = response.getResult();
                Log.i(TAG, "Resolved query: " + result.getResolvedQuery());

                Log.i(TAG, "Action: " + result.getAction());

                final String speech = result.getFulfillment().getSpeech();
                Log.i(TAG, "Speech: " + speech);

                callMessage(result.getResolvedQuery());

                final Metadata metadata = result.getMetadata();
                if (metadata != null) {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

//                final HashMap<String, JsonElement> params = result.getParameters();
//                if (params != null && !params.isEmpty()) {
//                    Log.i(TAG, "Parameters: ");
//                    for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
//                        Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
//                    }
//                }
            }
        });
    }

    @Override
    public void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, error.toString());
            }
        });
    }

    @Override
    public void onAudioLevel(final float level) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                float positiveLevel = Math.abs(level);

                if (positiveLevel > 100) {
                    positiveLevel = 100;
                }
                Log.i(TAG, String.format("onAudioLevel: %s", positiveLevel));
            }
        });
    }

    @Override
    public void onListeningStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "onListeningStarted");
            }
        });
    }

    @Override
    public void onListeningCanceled() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "onListeningCanceled");
            }
        });
    }

    @Override
    public void onListeningFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG,"onListeningFinished");
            }
        });
    }

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
    }

    private void speak(final String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
