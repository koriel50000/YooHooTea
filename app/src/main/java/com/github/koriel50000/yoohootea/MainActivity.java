package com.github.koriel50000.yoohootea;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;
import com.github.bassaer.chatmessageview.view.MessageView;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private MessageView messageView;
    private ChatUser myAccount;
    private ChatUser yooHooBot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMessageView();
    }

    private void initMessageView() {
        Log.d(TAG, "initMessageView");
        int myId = 0;
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_user);
        String myName = "Koriel";
        myAccount = new ChatUser(myId, myName, icon);

        int botId = 1;
        String botName = "YooHoo";
        yooHooBot = new ChatUser(botId, botName, icon);

        messageView = findViewById(R.id.message_view);
//        chatView.setRightBubbleColor(ContextCompat.getColor(this, R.color.green500));
//        chatView.setLeftBubbleColor(Color.WHITE);
//        chatView.setBackgroundColor(ContextCompat.getColor(this, R.color.blueGray500));
//        chatView.setSendButtonColor(ContextCompat.getColor(this, R.color.lightBlue500));
//        chatView.setSendIcon(R.drawable.ic_action_send);
//        chatView.setRightMessageTextColor(Color.WHITE);
//        chatView.setLeftMessageTextColor(Color.BLACK);
//        chatView.setUsernameTextColor(Color.WHITE);
//        chatView.setSendTimeTextColor(Color.WHITE);
//        chatView.setDateSeparatorColor(Color.WHITE);
//        chatView.setMessageMarginTop(5);
//        chatView.setMessageMarginBottom(5);

        Message message = new Message.Builder()
                .setUser(yooHooBot)
                .setRightMessage(false)
                .setMessageText("おーい")
                .build();
        messageView.setMessage(message);
    }
}
