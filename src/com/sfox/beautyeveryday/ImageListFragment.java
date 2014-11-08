package com.sfox.beautyeveryday;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.sfox.beautyeveryday.DataSource.ImageEntry;

public class ImageListFragment extends Fragment 
        implements OnSharedPreferenceChangeListener, OnScrollListener {
    private static final String TAG = "[UI] ";
    
    RefreshLayout mRefreshableView;
    GridView mGridView;
    ImageAdapter mAdapter;
    
    DataSource mDataSource;
    DownloadManager mDownloadManager;
    UpdateTask mUpdateTask;
    
    LayoutInflater mInflater;
    int mCellSize;
    int mHot;
    boolean mVisibleToUser;
    UpdateListener mListener;
    boolean mOnCreateView;
    
    RefreshLayout.onRefreshListener mRefreshListener = new RefreshLayout.onRefreshListener() {
        
        @Override
        public void onRefreshStart() {
            Utils.log(Utils.INFO, TAG + "onRefreshStart");
            if (mListener != null) {
                mListener.onStartUpdate();
            }
        }
        
        @Override
        public boolean onRefresh() {
            return mDataSource.update();
        }

        @Override
        public void onRefreshFinished(boolean success) {
            Utils.log(Utils.INFO, TAG + "onRefreshFinished success=" + success);
            notifyUpdateFinish(success);
            mAdapter.notifyDataSetChanged();
        }
    };

    View.OnClickListener mOnImageItemClick = new View.OnClickListener() {
        
        @Override
        public void onClick(View v) {
            int idx = (Integer)v.getTag(R.id.image);
            Context ctx = getActivity();
            Intent intent = new Intent(ctx, ImageViewerActivity.class);
            intent.putExtra("data_source_url", mDataSource.getDataSourceUrl());
            intent.putExtra("image_position", idx);
            ctx.startActivity(intent);
        }
    };
    
    public ImageListFragment() {
    }
    
    public ImageListFragment(int hot) {
        mHot = hot;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Utils.log(Utils.DEBUG, TAG + "onAttach for page " + mHot);
        mDataSource = new DataSource(getActivity());
        mDownloadManager = new DownloadManager(getActivity(), mDataSource);
        Prefs.registerPrefsChangeListener(activity, this);
    }

    @Override
    public void onDetach() {
        Utils.log(Utils.DEBUG, TAG + "onDetach for page " + mHot);
        DataSource.del(mDataSource);
        mDownloadManager.clear();
        Prefs.unregisterPrefsChangeListener(getActivity(), this);
        super.onDetach();
    }

    @Override
    public void onPause() {
        Utils.log(Utils.DEBUG, TAG + "onPause for page " + mHot);
        mGridView.setOnScrollListener(null);
        mDownloadManager.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        Utils.log(Utils.DEBUG, TAG + "onResume for page " + mHot);
        super.onResume();
        mDownloadManager.resume();
        mGridView.setOnScrollListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Utils.log(Utils.DEBUG, TAG + "onCreateView for page " + mHot);
        View rootView = inflater.inflate(R.layout.image_list_fragment, container, false);
        
        mRefreshableView = (RefreshLayout) rootView.findViewById(R.id.refreshable_view);
        mGridView = (GridView) rootView.findViewById(R.id.grid_view);
        
        mInflater = inflater;
        setupImageCellSize();
        
        mRefreshableView.setOnRefreshListener(mRefreshListener, 0);
        
        mAdapter = new ImageAdapter();
        mGridView.setAdapter(mAdapter);
        
        mOnCreateView = true;
        setupDataSource(Prefs.getImageTag(getActivity()), false);
        mOnCreateView = false;
        
        return rootView;
    }
    
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        Utils.log(Utils.INFO, TAG + "Page " + mHot + " isVisibleToUser = " + isVisibleToUser);
        mVisibleToUser = isVisibleToUser;
        updateDataSource(false);
    }

    private void setupImageCellSize() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        int padding = this.getResources().getDimensionPixelSize(R.dimen.image_cell_spacing);
        int orientation = getResources().getConfiguration().orientation;
        Utils.log(Utils.INFO, TAG + "setupImageCellSize orientation=" + orientation 
                + ", screen width=" + dm.widthPixels);
        mCellSize = (dm.widthPixels / Prefs.cellColumnNumber(getActivity())) - padding;
        mGridView.setColumnWidth(mCellSize);
        Utils.IMAGE_THUMB_SIZE = mCellSize;
        Utils.IMAGE_MAX_WIDTH = dm.widthPixels;
        Utils.IMAGE_MAX_HEIGHT = dm.heightPixels;
    }
    
    public void updateDataSource(Context ctx, boolean forceUpdate) {
        int tag = Prefs.getImageTag(ctx);
        setupDataSource(tag, forceUpdate);
    }
    
    public void setUpdateListener(UpdateListener l) {
        mListener = l;
    }
    
    private void setupDataSource(int tag, boolean forceUpdate) {
        String url = ImageUrls.getUrl(getActivity(), tag, mHot);
        Utils.log(Utils.DEBUG, TAG + "setupDataSource tag=" 
                + tag + ", hot=" + mHot + ", forceUpdate=" + forceUpdate + ",url=" + url);
        mDataSource.setDataSourceUrl(url);
        updateDataSource(forceUpdate);
    }
    
    public void updateDataSource(boolean forceUpdate) {
        if (mVisibleToUser && mDataSource != null && mDataSource.getDataSourceUrl() != null) {
            if (mUpdateTask != null && mUpdateTask.getStatus() == AsyncTask.Status.RUNNING) {
                mUpdateTask.cancel(true);
                mUpdateTask = null;
            }

            if (!forceUpdate && mDataSource.readCache()) {
                mAdapter.notifyDataSetChanged();
            } else {
                if (mOnCreateView == false) {
                    mUpdateTask = new UpdateTask();
                    mUpdateTask.execute();
                }
            }
        } else {
            if (mVisibleToUser == false) {
                if (mUpdateTask != null && mUpdateTask.getStatus() == AsyncTask.Status.RUNNING) {
                    mUpdateTask.cancel(true);
                    mUpdateTask = null;
                }
            }
            Utils.log(Utils.DEBUG, TAG + "updateDataSource for " + mHot + 
                    " not execute. mVisibleToUser=" + mVisibleToUser);
        }
    }

    private void notifyUpdateFinish(boolean success) {
        if (mListener != null) {
            mListener.onFinishUpdate(success);
        }
    }
    
    class UpdateTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Utils.log(Utils.DEBUG, TAG + "update start");
            if (mListener != null) {
                mListener.onStartUpdate();
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            DataSource ds = mDataSource;
            return ds.update();
        }

        @Override
        protected void onProgressUpdate(Void... params) {
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Utils.log(Utils.DEBUG, TAG + "update finished. result=" + success);
            notifyUpdateFinish(success);
            mUpdateTask = null;
            if (success == false) {
                if (mDataSource.readCache()) {
                    mAdapter.notifyDataSetChanged();
                } 
            }
            mAdapter = new ImageAdapter();
            mGridView.setAdapter(mAdapter);
        }

        @Override
        protected void onCancelled() {
            Utils.log(Utils.DEBUG, TAG + "update canneled");
            notifyUpdateFinish(true);
            mUpdateTask = null;
        }
    }

    public class ImageAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mDataSource.getImageEntryNum();
        }

        @Override
        public Object getItem(int position) {
            return mDataSource.getImageEntry(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = null;
            ImageView imageView;
            Utils.log(Utils.VERB, TAG + "page " + mHot + " getView for pos " + position);
            ImageEntry ie = mDataSource.getImageEntry(position);
            if (convertView == null) {
                view = mInflater.inflate(R.layout.image_item, null);
                imageView = (ImageView)view.findViewById(R.id.image);
                LayoutParams lp = imageView.getLayoutParams();
                lp.height = mCellSize;
                lp.width = mCellSize;
                imageView.setOnClickListener(mOnImageItemClick);
            } else {
                view = convertView;
                imageView = (ImageView)view.findViewById(R.id.image);
                imageView.setImageBitmap(null);
                ImageEntry e = (ImageEntry)imageView.getTag();
                if (!ie.downloadUrl.equals(e.downloadUrl)) {
                    mDownloadManager.delItem(position);
                }
            }
            imageView.setTag(ie);
            imageView.setTag(R.id.image, position);
            final View progressBar = view.findViewById(R.id.image_downloading);
            final ImageView errorView = (ImageView)view.findViewById(R.id.image_error);
            errorView.setVisibility(View.INVISIBLE);
            Bitmap bm = ImageMemoryCache.getBitmapFromCache(getActivity(), ie.downloadUrl, true);
            if (bm != null) {
                progressBar.setVisibility(View.INVISIBLE);
                imageView.setImageBitmap(bm);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                DownloadManager.DownloadEventListener listener = new DownloadManager.DownloadEventListener() {

                    @Override
                    public void onDownloadFinished(boolean success) {
                        Utils.log(Utils.VERB, TAG + "onDownloadFinished " + "page=" + mHot + ", pos=" + position + ". success=" + success);
                        progressBar.setVisibility(View.INVISIBLE);
                        if (!success) {
                            errorView.setVisibility(View.VISIBLE);
                        }
                    }
                };
                mDownloadManager.addItem(position, imageView, true, listener);
            }
            return view;
        }
    }
    
    public interface UpdateListener {
        public void onStartUpdate();
        public void onFinishUpdate(boolean success);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(Prefs.PREFS_COLUMN_NUMBER)) {
            setupImageCellSize();
            mAdapter = new ImageAdapter();
            mGridView.setAdapter(mAdapter);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        Utils.log(Utils.DEBUG, TAG + "onScrollStateChanged scrollState=" + scrollState);
        switch (scrollState) {  
            case AbsListView.OnScrollListener.SCROLL_STATE_FLING:  
                mDownloadManager.pause();
                break;
            case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:  
                int start = mGridView.getFirstVisiblePosition();
                int end = mGridView.getLastVisiblePosition();
                if (end > mGridView.getCount()) {
                    end = mGridView.getCount() - 1;
                }
                mDownloadManager.setRange(start, end);
                mDownloadManager.resume();
                break;  
  
            default:  
                break;  
        }
    }
}

