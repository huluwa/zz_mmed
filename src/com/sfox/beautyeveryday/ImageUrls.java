package com.sfox.beautyeveryday;

import android.content.Context;

import java.net.URLEncoder;

public class ImageUrls {

    String mChannel;
    String[] mTags;
    
    private static ImageUrls sInstance;
    
    private static ImageUrls getInstance(Context ctx) {
        if (sInstance == null) {
            Context context = ctx.getApplicationContext();
            sInstance = new ImageUrls(context);
        }
        return sInstance;
    }
    
    private ImageUrls(Context ctx) {
        mChannel = ctx.getResources().getString(R.string.channel);
        mTags = ctx.getResources().getStringArray(R.array.image_tag_name);
    }
    
    public static String[] getTags(Context ctx) {
        ImageUrls urls = ImageUrls.getInstance(ctx);
        return urls.mTags;
    }
    
    public static String getTagName(Context ctx, int tag) {
        ImageUrls urls = ImageUrls.getInstance(ctx);
        return urls.mTags[tag];
    }
    
    /* hot: 1 -> hot; 0 -> latest */
    public static String getUrl(Context ctx, int tag, int hot) {
        ImageUrls urls = ImageUrls.getInstance(ctx);
        String format = "http://image.baidu.com/channel?c=%s&t=%s&s=%d";
        String url = null;
        if (tag >= urls.mTags.length) {
            tag = 0;
        }
        String tagName = urls.mTags[tag];
        try {
            url = String.format(format, URLEncoder.encode(urls.mChannel, "UTF-8"),
                    URLEncoder.encode(tagName, "UTF-8"), hot);
        } catch (Exception e) {
            Utils.log(Utils.ERROR, "getUrl error for " + tag + "\n" + e);
            url = "http://image.baidu.com/channel?c=%E7%BE%8E%E5%A5%B3";
        }
        return url;
    }
}

