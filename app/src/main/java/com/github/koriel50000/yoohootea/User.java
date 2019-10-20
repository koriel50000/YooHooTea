package com.github.koriel50000.yoohootea;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import com.github.bassaer.chatmessageview.model.IChatUser;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class User implements IChatUser {

    private static Bitmap defaultIcon;

    private String id;
    private String name;
    private Bitmap icon;

    public User(Context context, long id, String name) {
        if (defaultIcon == null) {
            defaultIcon = BitmapFactory.decodeResource(
                    context.getResources(),
                    R.drawable.ic_action_user);
        }
        this.id = String.valueOf(id);
        this.name = name;
        this.icon = defaultIcon;
    }

    public User(Context context, long id, String name, String url) {
        this(context, id, name);
        Picasso.with(context).load(url).into(new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                icon = bitmap;
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        });
    }

    public User(int id, String name, Bitmap icon) {
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

    public void setName(String name) { this.name = name; }

    @Override
    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }
}
