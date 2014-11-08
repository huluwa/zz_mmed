package com.sfox.beautyeveryday;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;

public class ImageFileCache {
    private static final String TAG = "[FileCache] ";
    
    private static final String CACHE_FILE_SUBFIX = ".kch";
    private static final int FREE_SPACE_NEEDED_TO_CACHE = 10;

    private static ImageFileCache sCache = null;
    private static ImageFileCache sFavorite = null;
    
    private static ImageFileCache getInstance(Context ctx, boolean fav) {
        ImageFileCache inst;
        if (fav) {
            if (sFavorite == null) {
                sFavorite = new ImageFileCache();
            }
            inst = sFavorite;
        } else {
            if (sCache == null) {
                sCache = new ImageFileCache();
            }
            inst = sCache;
        }
        return inst;
    }
    
    private ImageFileCache() {
    }

    public static Bitmap readBitmap(Context ctx, boolean fav, final String url) {
        ImageFileCache inst = getInstance(ctx, fav);
        final String path = Utils.getCacheDir(ctx, fav) + "/" + inst.convertUrlToFileName(url);
        File file = new File(path);
        if (file.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) {
                file.delete();
            } else {
                Utils.log(Utils.DEBUG, TAG + "cache hit for " + path + " [" + bmp.getWidth() + " X " + bmp.getHeight() + "]");
                if (fav == false) {
                    inst.updateFileTime(path);
                }
                return bmp;
            }
        }
        return null;
    }

    public static File saveBitmap(Context ctx, boolean fav, String url, Bitmap bm) {
        if (bm == null) {
            return null;
        }
        if (FREE_SPACE_NEEDED_TO_CACHE > Utils.freeSpaceOnFileSystem()) {
            return null;
        }
        ImageFileCache inst = getInstance(ctx, fav);
        String filename = inst.convertUrlToFileName(url);
        String dir = Utils.getCacheDir(ctx, fav);
        String fname = dir + "/" + filename;
        File file = new File(fname);
        try {
            file.createNewFile();
            OutputStream outStream = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            outStream.flush();
            outStream.close();
            Utils.log(Utils.DEBUG, TAG + "save to " + fname);
        } catch (FileNotFoundException e) {
            Utils.log(Utils.ERROR, TAG + "FileNotFoundException" + fname);
        } catch (IOException e) {
            Utils.log(Utils.ERROR, TAG + "IOException" + fname);
        }
        return file;
    }
    
    public static boolean addFavoriteCacheFile(Context ctx, String url) {
        ImageFileCache inst = getInstance(ctx, false);
        String filename = inst.convertUrlToFileName(url);
        String sdir = Utils.getCacheDir(ctx, false);
        String sfname = sdir + "/" + filename;
        File sfile = new File(sfname);
        
        String ddir = Utils.getCacheDir(ctx, true);
        String dfname = ddir + "/" + filename;
        File dfile = new File(dfname);
        
        try {
            int read = 0;
            byte[] data = new byte[2048];
            BufferedOutputStream o_stream = new BufferedOutputStream(new FileOutputStream(
                    dfile));
            BufferedInputStream i_stream = new BufferedInputStream(
                    new FileInputStream(sfile));

            while ((read = i_stream.read(data, 0, 2048)) != -1)
                o_stream.write(data, 0, read);

            o_stream.flush();
            i_stream.close();
            o_stream.close();

        } catch (FileNotFoundException e) {
            Utils.log(Utils.ERROR, TAG + "FileNotFoundException: " + sfname);
            return false;
        } catch (IOException e) {
            Utils.log(Utils.ERROR, TAG + "IOException: " + sfname);
            return false;
        }
        return true;
    }
    
    public static void removeFavoriteCacheFile(Context ctx, String url) {
        ImageFileCache inst = getInstance(ctx, true);
        String filename = inst.convertUrlToFileName(url);
        String dir = Utils.getCacheDir(ctx, true);
        String fname = dir + "/" + filename;
        File file = new File(fname);
        if (file.delete() == false) {
            Utils.log(Utils.ERROR, TAG + "error to remove cache file " + fname);
        }
    }

    /**
     * delete 40% of cache
     */
    public static boolean trimCache(Context ctx) {
        String dirPath = Utils.getCacheDir(ctx, false);
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        if (files == null) {
            return true;
        }
        
        int dirSize = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().contains(CACHE_FILE_SUBFIX)) {
                dirSize += files[i].length();
            }
        }
        int cacheSizeLimit = Prefs.cacheSizeLimit(ctx);
        Utils.log(Utils.INFO, TAG + "cache dir size=" + dirSize + ", limit=" + cacheSizeLimit);
        if (dirSize > cacheSizeLimit * Utils.MB || FREE_SPACE_NEEDED_TO_CACHE > Utils.freeSpaceOnFileSystem()) {
            int removeFactor = (int) ((0.4 * files.length) + 1);
            Arrays.sort(files, new FileLastModifSort());
            for (int i = 0; i < removeFactor; i++) {
                if (files[i].getName().contains(CACHE_FILE_SUBFIX)) {
                    Utils.log(Utils.DEBUG, TAG + "delete cache file: " + files[i].getName());
                    files[i].delete();
                }
            }
        }

        if (Utils.isSdcardMounted()) {
            String intDir = Utils.getCacheDir(ctx, false, Utils.MEM_INTERNAL);
            removeDir(intDir);
        }
        
        if (Utils.freeSpaceOnFileSystem() <= cacheSizeLimit) {
            return false;
        }

        return true;
    }
    
    public static File getCacheFile(Context ctx, boolean fav, final String url) {
        ImageFileCache inst = getInstance(ctx, fav);
        final String path = Utils.getCacheDir(ctx, fav) + "/" + inst.convertUrlToFileName(url);
        File file = new File(path);
        return file;
    }
    
    private static long calcDirSize(String dir) {
        File path = new File(dir);
        if (path.exists() == false) {
            return 0;
        }
        
        File[] list = path.listFiles();
        if (list == null || list.length == 0) {
            return 0;
        }
        
        int len;
        long size = 0;
        
        if (list != null) {
            len = list.length;

            for (int i = 0; i < len; i++) {
                if (list[i].isFile()) {
                    size += list[i].length();
                }
            }
        }
        return size;
    }
    
    public static long getCacheSize(Context ctx, boolean fav) {
        String dir = Utils.getCacheDir(ctx, fav, Utils.MEM_EXTERNAL);
        long size = calcDirSize(dir);
        Utils.log(Utils.DEBUG, TAG + "getCacheSize for fav(" + fav + "). path=" + dir + ", size=" + size);
        
        dir = Utils.getCacheDir(ctx, fav, Utils.MEM_INTERNAL);
        size += calcDirSize(dir);
        Utils.log(Utils.DEBUG, TAG + "getCacheSize for fav(" + fav + "). path=" + dir + ", size=" + size);
        return size;
    }
    
    private static void removeDir(String dir) {
        File path = new File(dir);
        if (path.exists() == false) {
            return;
        }
        File[] list = path.listFiles();
        if (list == null || list.length == 0) {
            return;
        }
        int len = list.length;

        for (int i = 0; i < len; i++) {
            if (list[i].delete() == false) {
                Utils.log(Utils.WARN, TAG + "error to delete file " + list[i].getAbsolutePath());
            }
        }

        Utils.log(Utils.DEBUG, TAG + "removed " + len + " file for path: " + dir);
    }
    
    public static void clearCache(Context ctx, boolean fav) {
        String dir = Utils.getCacheDir(ctx, fav, Utils.MEM_EXTERNAL);
        removeDir(dir);
        
        dir = Utils.getCacheDir(ctx, fav, Utils.MEM_INTERNAL);
        removeDir(dir);
    }
    
    /** modify the last modified time **/
    private void updateFileTime(String path) {
        File file = new File(path);
        long newModifiedTime = System.currentTimeMillis();
        file.setLastModified(newModifiedTime);
    }

    private String convertUrlToFileName(String url) {
        return String.valueOf(url.hashCode()) + CACHE_FILE_SUBFIX;
    }

    private static class FileLastModifSort implements Comparator<File> {
        public int compare(File arg0, File arg1) {
            if (arg0.lastModified() > arg1.lastModified()) {
                return 1;
            } else if (arg0.lastModified() == arg1.lastModified()) {
                return 0;
            } else {
                return -1;
            }
        }
    }

}
