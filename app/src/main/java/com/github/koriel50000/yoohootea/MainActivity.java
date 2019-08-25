package com.github.koriel50000.yoohootea;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.MessageView;

import ai.kitt.snowboy.AppResCopy;
import ai.kitt.snowboy.MsgEnum;
import ai.kitt.snowboy.audio.AudioDataSaver;
import ai.kitt.snowboy.audio.RecordingThread;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private int preVolume = -1;
    private RecordingThread recordingThread;

    private MessageView messageView;
    private User myAccount;
    private User yooHooBot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMessageView();

        setProperVolume();
        AppResCopy.copyResFromAssetsToSD(this);
        recordingThread = new RecordingThread(handle, new AudioDataSaver());
        recordingThread.startRecording();
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
        Message sendMessage = new Message.Builder()
                .setUser(myAccount)
                .setRightMessage(true)
                .setMessageText("おーい")
                .build();
        messageView.setMessage(sendMessage);

        final Message receivedMessage = new Message.Builder()
                .setUser(yooHooBot)
                .setRightMessage(false)
                .setMessageText("？？？")
                .build();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                messageView.setMessage(receivedMessage);
            }
        }, 2000);
    }
}
