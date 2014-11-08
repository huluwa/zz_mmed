
package com.sfox.beautyeveryday;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

public class Utils {
    public static final String TAG = "MMED";

    public static final String URI_FAVORITE = "mmed://favorite";

    /* image thumbnail size which setup when app init */
    public static int IMAGE_THUMB_SIZE = 0;
    /* screen width and height will init when app init */
    public static int IMAGE_MAX_WIDTH = 0;
    public static int IMAGE_MAX_HEIGHT = 0;
    
    public static boolean FEATURE_ENABLE_CELL_SETTING = false;
    public static boolean FEATURE_ENABLE_HIDE_TITLE = false;
    
    public static final int VERB = 0;
    public static final int DEBUG = 1;
    public static final int INFO = 2;
    public static final int WARN = 3;
    public static final int ERROR = 4;

    public static final int MEM_AUTO = 0;
    public static final int MEM_EXTERNAL = 1;
    public static final int MEM_INTERNAL = 2;

    private static final String CACHE_DIR = "MMED/cache";
    private static final String FAVORITE_DIR = "MMED/favorite";
    private static final int EXTERNAL_SIZE_LIMIT = 50; // MB
    private static final int INTERNAL_SIZE_LIMIT = 18; // MB (~300KB X 60)
    public static final int MB = 1024 * 1024;

    private static final int DEBUG_LEVEL = INFO;

    public static void log(int level, String msg) {
        if (level < DEBUG_LEVEL) {
            return;
        }

        switch (level) {
            case VERB:
                Log.v(TAG, msg);
            case DEBUG:
                Log.d(TAG, msg);
                break;
            case INFO:
                Log.i(TAG, msg);
                break;
            case WARN:
                Log.w(TAG, msg);
                break;
            case ERROR:
                Log.e(TAG, msg);
                break;
        }
    }

    /* return cache dir size limit in MB base on internal/external memory */
    public static int getCacheDirSizeLimit(Context ctx) {
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            return EXTERNAL_SIZE_LIMIT;
        } else {
            return INTERNAL_SIZE_LIMIT;
        }
    }

    public static boolean isSdcardMounted() {
        return Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    public static String getCacheDir(Context ctx, boolean fav, int type) {
        String cacheRoot = null;
        String intDir = ctx.getCacheDir().getAbsolutePath();
        String extDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);

        if (type == MEM_INTERNAL) {
            cacheRoot = intDir;
        } else if (type == MEM_EXTERNAL) {
            cacheRoot = extDir;
        } else {
            // auto mode, select sd card first if available
            if (sdCardExist) {
                cacheRoot = extDir;
            } else {
                cacheRoot = intDir;
            }
        }
        if (fav) {
            cacheRoot = cacheRoot + "/" + FAVORITE_DIR;
        } else {
            cacheRoot = cacheRoot + "/" + CACHE_DIR;
        }

        File file = new File(cacheRoot);
        if (!file.exists()) {
            file.mkdirs();
        }

        return cacheRoot;
    }

    public static String getCacheDir(Context ctx, boolean fav) {
        return getCacheDir(ctx, fav, MEM_AUTO);
    }

    /** get the free space on sd card on MB **/
    public static int freeSpaceOnFileSystem() {
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
        StatFs stat;
        if (sdCardExist) {
            stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        } else {
            stat = new StatFs("/data");
        }
        double sdFreeMB = ((double) stat.getAvailableBlocks() * (double) stat.getBlockSize()) / MB;
        return (int) sdFreeMB;
    }

    public static String readableSize(long size) {
        if (size < 1024) {
            return String.format("%dB", size);
        } else if (size < 1024 * 1024) {
            return String.format("%.2fK", (float) size / (1 << 10));
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2fM", (float) size / (1 << 20));
        } else {
            return String.format("%.2fG", (float) size / (1 << 30));
        }
    }

    public static int getVerCode(Context context) {
        int verCode = -1;
        try {
            verCode = context.getPackageManager().getPackageInfo(
                    "com.sfox.beautyeveryday", 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }
        return verCode;
    }

    public static String getVerName(Context context) {
        String verName = "";
        try {
            verName = context.getPackageManager().getPackageInfo(
                    "com.sfox.beautyeveryday", 0).versionName;
        } catch (NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }
        return verName;
    }

    public static Bitmap bitmapThumbnail(Bitmap bm) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) IMAGE_THUMB_SIZE) / width;
        float scaleHeight = ((float) IMAGE_THUMB_SIZE) / height;
        if (scaleWidth >= scaleHeight) {
            scaleHeight = scaleWidth;
        } else {
            scaleWidth = scaleHeight;
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
        Utils.log(Utils.VERB, "bitmapThumbnail size:" + bm.getRowBytes() * bm.getHeight() +
                " -> " + newbm.getRowBytes() * bm.getHeight());
        return newbm;
    }

    private static boolean sOfflineMode = false;

    public static void setOfflineMode(boolean val) {
        sOfflineMode = val;
    }

    public static boolean offlineMode() {
        return sOfflineMode;
    }

    public static boolean wifiConnected(Context ctx) {
        ConnectivityManager connManager = (ConnectivityManager) ctx
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean allowNetwork(Context ctx) {
        if (Utils.wifiConnected(ctx)) {
            sOfflineMode = false;
            return true;
        }
        
        if (sOfflineMode) {
            return false;
        }

        if (Prefs.downloadOnlyInWIFI(ctx)) {
            if (Utils.wifiConnected(ctx)) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public static String getDeviceInfo(Context context) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);

            String device_id = tm.getDeviceId();

            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);

            String mac = wifi.getConnectionInfo().getMacAddress();
            json.put("mac", mac);

            if (TextUtils.isEmpty(device_id)) {
                device_id = mac;
            }

            if (TextUtils.isEmpty(device_id)) {
                device_id = android.provider.Settings.Secure.getString(
                        context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            }

            json.put("device_id", device_id);

            String str = json.toString();
            Utils.log(Utils.ERROR, TAG + str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
