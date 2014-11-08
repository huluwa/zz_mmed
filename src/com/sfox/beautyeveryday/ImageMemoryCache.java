package com.sfox.beautyeveryday;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

public class ImageMemoryCache {
    private static final String TAG = "[MemCache] ";
    
    private static final int SOFT_CACHE_SIZE = 20;  // SoftReference cache size 
    private static LruCache<String, Bitmap> sLruCache;  // LruCache is hard reference  
    private static LinkedHashMap<String, SoftReference<Bitmap>> sSoftCache;  // SoftReference cache
    private static LruCache<String, Bitmap> mFSBLruCache;  // LruCache for full screen bitmap  
    private static boolean sInited = false;
    private static Object mLock = new Object();
    
    private static void init(Context context) {
        synchronized (mLock) {
            if (sInited) {
                return;
            }
            
            int memClass = ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
            int cacheSize = 1024 * 1024 * memClass / 8;
            Utils.log(Utils.INFO, TAG + "Cache Memory Size: " + cacheSize);
            sLruCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    if (value != null)
                        return value.getRowBytes() * value.getHeight();
                    else
                        return 0;
                }
    
                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                    if (oldValue != null) {
                        sSoftCache.put(key, new SoftReference<Bitmap>(oldValue));
                    }
                }
            };
            sSoftCache = new LinkedHashMap<String, SoftReference<Bitmap>>(SOFT_CACHE_SIZE, 0.75f, true) {
                private static final long serialVersionUID = 6040103833198203725L;
                @Override
                protected boolean removeEldestEntry(Entry<String, SoftReference<Bitmap>> eldest) {
                    if (size() > SOFT_CACHE_SIZE){    
                        return true;  
                    }  
                    return false; 
                }
            };
            
            cacheSize = 1024 * 1024 * memClass / 8;
            mFSBLruCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    if (value != null) {
                        return value.getRowBytes() * value.getHeight();
                    } else  {
                        return 0;
                    }
                }
    
                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                }
            };
            
            sInited = true;
        }
    }

    public static Bitmap getBitmapFromCache(Context ctx, String url, boolean bThumbnail) {
        Bitmap bitmap;
        init(ctx.getApplicationContext());
        
        if (bThumbnail == false) {
            synchronized (mFSBLruCache) {
                bitmap = mFSBLruCache.get(url);
                if (bitmap != null) {
                    // move to the head of lru cache to make sure it's delete in the last order
                    mFSBLruCache.remove(url);
                    mFSBLruCache.put(url, bitmap);
                    Utils.log(Utils.VERB, TAG + "FullScreen Bitmap LruCache hit.");
                    return bitmap;
                }
            }
            return null;
        }
        
        synchronized (sLruCache) {
            bitmap = sLruCache.get(url);
            if (bitmap != null) {
                // move to the head of lru cache to make sure it's delete in the last order
                sLruCache.remove(url);
                sLruCache.put(url, bitmap);
                Utils.log(Utils.VERB, TAG + "LruCache hit.");
                return bitmap;
            }
        }
        // find from soft ref
        synchronized (sSoftCache) { 
            SoftReference<Bitmap> bitmapReference = sSoftCache.get(url);
            if (bitmapReference != null) {
                bitmap = bitmapReference.get();
                if (bitmap != null) {
                    // move to hard ref
                    sLruCache.put(url, bitmap);
                    sSoftCache.remove(url);
                    Utils.log(Utils.VERB, TAG + "SoftCache hit.");
                    return bitmap;
                } else {
                    Utils.log(Utils.DEBUG, TAG + "SoftCache hit failed because of it's recycled.");
                    sSoftCache.remove(url);
                }
            }
        }
        return null;
    }
    
    public static void addBitmapToCache(Context ctx, String url, Bitmap bitmap, boolean bThumbnail) {
        if (bitmap == null) return;
        init(ctx.getApplicationContext());
        
        Utils.log(Utils.DEBUG, TAG + "cache for [" + bitmap.getWidth() + " X " 
                + bitmap.getHeight() + "]" + "; thumb = " + bThumbnail);
        if (bitmap != null) {
            if (bThumbnail) {
                synchronized (sLruCache) {
                    sLruCache.put(url, bitmap);
                }
            } else {
                synchronized (mFSBLruCache) {
                    mFSBLruCache.put(url, bitmap);
                }
            }
        }
    }
    
    public void clearCache() {
        Utils.log(Utils.DEBUG, TAG + "clearCache. size=" + sSoftCache.size());
        sSoftCache.clear();
    }
}

