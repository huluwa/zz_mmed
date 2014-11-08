package com.sfox.beautyeveryday;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.sfox.beautyeveryday.DataSource.ImageEntry;

import java.util.ArrayList;
import java.util.Iterator;

public class DownloadManager {
    private static final String TAG = "[DownloadManager] ";
    private static final int RUNNING_TASK_NUM = 6;
    
    DataSource mDataSource;
    ArrayList<DownloadTask> mPendingList;
    ArrayList<DownloadTask> mRunningList;
    Context mContext;
    boolean mPaused;
    
    public interface DownloadEventListener {
        public void onDownloadFinished(boolean success);
    }
    
    public DownloadManager(Context ctx, DataSource ds) {
        mDataSource = ds;
        mContext = ctx;
        mPendingList = new ArrayList<DownloadTask>(20);
        mRunningList = new ArrayList<DownloadTask>(RUNNING_TASK_NUM);
    }
    
    public boolean addItem(int position, ImageView v, boolean thumbnail, DownloadEventListener l) {
        synchronized (this) {
            for (DownloadTask task : mRunningList) {
                if (task.mIndex == position) {
                    Utils.log(Utils.WARN, TAG + "addItem ignore duplicate pos " + position);
                    return true;
                }
            }
            for (DownloadTask task : mPendingList) {
                if (task.mIndex == position) {
                    Utils.log(Utils.WARN, TAG + "addItem ignore duplicate pos " + position);
                    return true;
                }
            }
            DownloadTask task = new DownloadTask(position, v, thumbnail, l);
            if (mRunningList.size() >= RUNNING_TASK_NUM || mPaused) {
                Utils.log(Utils.DEBUG, TAG + "add to pending list for pos " + position);
                mPendingList.add(task);
                return true;
            } else {
                Utils.log(Utils.DEBUG, TAG + "add to running list for pos " + position);
                mRunningList.add(task);
                task.execute();
                return true;
            }
        }
    }
    
    public boolean delItem(int pos) {        
        synchronized (this) {
            if (mRunningList.size() == 0 && mPendingList.size() == 0) {
                return true;
            }
            
            DownloadTask dtask = null;
            for (DownloadTask task : mPendingList) {
                if (task.mIndex == pos) {
                    Utils.log(Utils.DEBUG, TAG + "delItem in pending list for pos " + pos);
                    dtask = task;
                    break;
                }
            }
            if (dtask != null) {
                mPendingList.remove(dtask);
            }

            for (DownloadTask task : mRunningList) {
                if (task.mIndex == pos) {
                    Utils.log(Utils.DEBUG, TAG + "delItem in running list for pos " + pos);
                    dtask = task;
                    break;
                }
            }
            if (dtask != null) {
                mRunningList.remove(dtask);
                dtask.cancel(true);
            }
        }
        
        startPendingDownload();
        return true;
    }
    
    public void pause() {
        Utils.log(Utils.DEBUG, TAG + "pause");
        mPaused = true;
    }
    
    public void resume() {
        Utils.log(Utils.DEBUG, TAG + "resume");
        mPaused = false;
        startPendingDownload();
    }
    
    public void clear() {
        for (DownloadTask task : mRunningList) {
            task.cancel(true);
        }
        mRunningList.clear();
        mPendingList.clear();
    }
    
    public void setRange(int start, int end) {
        Iterator<DownloadTask> iter;
        
        iter = mRunningList.iterator();
        while (iter.hasNext()) {
            DownloadTask task = iter.next();
            if (task.mIndex < start || task.mIndex > end) {
                task.cancel(true);
                iter.remove();
            }
        }
        
        iter = mPendingList.iterator();
        while (iter.hasNext()) {
            DownloadTask task = iter.next();
            if (task.mIndex < start || task.mIndex > end) {
                iter.remove();
            }
        }
        Utils.log(Utils.DEBUG, TAG + "setRange start=" + start 
                + ", end=" + end + ", total=" + mDataSource.getImageEntryNum()
                + ", count=" + (mPendingList.size() + mRunningList.size()));
    }
    
    private void startPendingDownload() {
        if (mPaused) return;
        
        synchronized (this) {
            if (mPendingList.size() > 0 && mRunningList.size() < RUNNING_TASK_NUM) {
                DownloadTask task = mPendingList.get(0);
                mPendingList.remove(task);
                mRunningList.add(task);
                task.execute();
                Utils.log(Utils.INFO, TAG + "start pending download for pos " + task.mIndex);
            }
        }
    }

    class DownloadTask extends AsyncTask<Void, Void, Bitmap> {
        ImageView mImageView;
        int mIndex;
        boolean mThumbnail;
        DownloadEventListener mListener;
        
        public DownloadTask(int index, ImageView v, boolean thumb, DownloadEventListener l) {
            mIndex = index;
            mImageView = v;
            mListener = l;
            mThumbnail = thumb;
        }
        
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap bm = null;
            ImageEntry e = mDataSource.getImageEntry(mIndex);
            bm = ImageFileCache.readBitmap(mContext, e.favorite, e.downloadUrl);
            if (bm == null) {
                bm = mDataSource.downloadBitmap(e, mThumbnail);
                if (bm != null && mThumbnail) {
                    bm = Utils.bitmapThumbnail(bm);
                }
            }
            return bm;
        }

        @Override
        protected void onProgressUpdate(Void... params) {
        }
        
        @Override
        protected void onCancelled() {
            synchronized (DownloadManager.this) {
                mRunningList.remove(this);
            }
            Utils.log(Utils.INFO, TAG + "download cancelled.");
            startPendingDownload();
        }

        @Override
        protected void onPostExecute(Bitmap bm) {
            boolean success = false;
            if (bm != null) {
                ImageEntry e = (ImageEntry)mImageView.getTag();
                ImageEntry ie = mDataSource.getImageEntry(mIndex);
                if (e.downloadUrl.equals(ie.downloadUrl)) {
                    mImageView.setImageBitmap(bm);
                } else {
                    Utils.log(Utils.INFO, TAG + "ImageView changed before download finished");
                }
                success = true;
                ImageMemoryCache.addBitmapToCache(mContext, ie.downloadUrl, bm, mThumbnail);
            } else {
                Utils.log(Utils.ERROR, TAG + "download failed.");
            }
            if (mListener != null) {
                mListener.onDownloadFinished(success);
            }
            // download finished. need to remove from list
            synchronized (DownloadManager.this) {
                mRunningList.remove(this);
            }
            Utils.log(Utils.INFO, TAG + "download finished for pos " + mIndex + ". success=" + success);
            startPendingDownload();
        }
    }
}
