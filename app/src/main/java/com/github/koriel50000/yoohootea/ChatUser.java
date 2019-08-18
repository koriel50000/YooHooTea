package com.github.koriel50000.yoohootea;

import android.graphics.Bitmap;

import com.github.bassaer.chatmessageview.model.IChatUser;

public class ChatUser implements IChatUser {

    private String id;
    private String name;
    private Bitmap icon;

    public ChatUser(int id, String name, Bitmap icon) {
        this.id = String.valueOf(id);
        this.name = name;
        this.icon = icon;
    }

    @Override
    public Bitmap getIcon() {
        return icon;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }
}
