package com.sfox.beautyeveryday;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class Prefs {

    private static final String PREFS_NAME = "config";
    private static final String PREFS_TAG = "tag";
    
    public static final String PREFS_AUTO_HIDE_TITLE_BAR = "pref_key_auto_hide_title";
    public static final String PREFS_CLEAR_CACHE = "pref_key_clear_cache";
    public static final String PREFS_CLEAR_FAVORITE = "pref_key_clear_favorite";
    public static final String PREFS_CACHE_LIMIT = "pref_key_cache_limit";
    public static final String PREFS_COLUMN_NUMBER = "pref_key_column_number";
    public static final String PREFS_CHECK_VERSION = "pref_key_check_version";
    public static final String PREFS_DOWNLOAD_ONLY_IN_WIFI = "pref_key_download_only_in_wifi";
    public static final String PREFS_DISABLE_ADS = "pref_key_disable_ads";
    public static final String PREFS_HOT_APPS = "pref_key_hot_apps";
    public static final String PREFS_FEEDBACK = "pref_key_feedback";
    public static final String PREFS_ABOUT = "pref_key_about";
    
    private static int sImageTag = -1;
    
    public static boolean setImageTag(Context ctx, int tag) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Editor e = prefs.edit();
        e.putInt(PREFS_TAG, tag);
        sImageTag = tag;
        return e.commit();
    }
    
    public static int getImageTag(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (sImageTag == -1) {
            sImageTag = prefs.getInt(PREFS_TAG, 0);
            if (sImageTag >= ImageUrls.getTags(ctx).length) {
                sImageTag = 0;
            }
        }
        return sImageTag;
    }
    
    public static boolean autoHideTitleBar(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(PREFS_AUTO_HIDE_TITLE_BAR, false);
    }
    
    public static int cacheSizeLimit(Context ctx) {
        int val = 50;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String str = prefs.getString(PREFS_CACHE_LIMIT, "50");
        try {
            val = Integer.decode(str);
        } catch (NumberFormatException e) {
        }
        return val;
    }
    
    public static int cellColumnNumber(Context ctx) {
        int val = 2;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String str = prefs.getString(PREFS_COLUMN_NUMBER, "2");
        try {
            val = Integer.decode(str);
        } catch (NumberFormatException e) {
        }
        return val;
    }
    
    public static boolean downloadOnlyInWIFI(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(PREFS_DOWNLOAD_ONLY_IN_WIFI, true);
    }
    
    public static void setDisableAds(Context ctx, boolean disable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        Editor e = prefs.edit();
        e.putBoolean(PREFS_DISABLE_ADS, disable);
        e.commit();
    }
    
    public static boolean disableAds(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(PREFS_DISABLE_ADS, false);
    }
    
    public static void registerPrefsChangeListener(Context ctx,  
            SharedPreferences.OnSharedPreferenceChangeListener l) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.registerOnSharedPreferenceChangeListener(l);
    }
    
    public static void unregisterPrefsChangeListener(Context ctx,
            SharedPreferences.OnSharedPreferenceChangeListener l) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }
}
