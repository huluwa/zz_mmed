package com.sfox.beautyeveryday;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
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

public class ImageFavoriteFragment extends Fragment
        implements OnSharedPreferenceChangeListener, OnScrollListener {
    private static final String TAG = "[UI] ";
    GridView mGridView;
    ImageAdapter mAdapter;
    
    FavoriteDB mDB;
    DataSource mDataSource;
    DownloadManager mDownloadManager;
    
    LayoutInflater mInflater;
    int mCellSize;

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
    
    public ImageFavoriteFragment() {
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Utils.log(Utils.DEBUG, TAG + "onAttach for favorite");
        mDataSource = new DataSource(activity);
        mDataSource.setDataSourceUrl(Utils.URI_FAVORITE);
        mDownloadManager = new DownloadManager(activity, mDataSource);
        mDB = new FavoriteDB(activity);
        Prefs.registerPrefsChangeListener(activity, this);
    }

    @Override
    public void onDetach() {
        Utils.log(Utils.DEBUG, TAG + "onDetach for favorite");
        mDB.close();
        DataSource.del(mDataSource);
        mDownloadManager.clear();
        Prefs.unregisterPrefsChangeListener(getActivity(), this);
        super.onDetach();
    }

    @Override
    public void onPause() {
        Utils.log(Utils.DEBUG, TAG + "onPause for page fav");
        mGridView.setOnScrollListener(null);
        mDownloadManager.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Utils.log(Utils.DEBUG, TAG + "onResume for page fav");
        mDataSource.setImageEntrys(mDB.get());
        mAdapter = new ImageAdapter();
        mGridView.setAdapter(mAdapter);
        mGridView.setOnScrollListener(this);
        mDownloadManager.resume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Utils.log(Utils.DEBUG, TAG + "onCreateView for favorite");
        View rootView = inflater.inflate(R.layout.favorite_image_fragment, container, false);
        
        mGridView = (GridView) rootView.findViewById(R.id.grid_view);
        mGridView.setEmptyView(rootView.findViewById(R.id.empty_view));
        mInflater = inflater;
        setupImageCellSize();

        return rootView;
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
            Utils.log(Utils.VERB, TAG + "Favorite getView for pos " + position);
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
                        Utils.log(Utils.VERB, TAG + "onDownloadFinished favorite pos " + position + ". success=" + success);
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
