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
                    R.mipmap.ic_yoohoobot_round);
        }
        this.id = String.valueOf(id);
        this.name = name;
        this.icon = defaultIcon;
    }

    public User(Context context, long id, String name, String url) {
        this(context, id, name);
        if (url != null && !url.equals("")) {
            setImageURL(context, url);
        }
    }

    public void setImageURL(Context context, String imageURL) {
        Picasso.with(context).load(imageURL).into(new Target() {
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
    public Bitmap getIcon() {
        return icon;
    }

    @Override
    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }
}
