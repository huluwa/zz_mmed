package com.sfox.beautyeveryday;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.waps.AppConnect;

import com.sfox.beautyeveryday.DataSource.ImageEntry;

import java.io.File;

public class ImageViewerActivity extends Activity 
            implements AnimationListener, View.OnClickListener {
    private static final String TAG = "[Viewer] ";
    
    private static final int MSG_TOGGLE_TITLE_BAR = 1;
    
    private static final int DUR_TITLE_BAR = 5000;
    
    private DataSource mDataSource;
    private DownloadManager mDownloadManager;
    private ViewPager mViewPager;
    private LayoutInflater mInflater;
    
    private View mTitleBar;
    private View mBottomBar;
    private ImageView mFavoriteBtn;
    private TextView mTitleView;
    private TextView mSubTitleView;
    private Animation mAnimSlideOutTop;
    private Animation mAnimSlideInTop;
    private Animation mAnimSlideOutBottom;
    private Animation mAnimSlideInBottom;
    private boolean mAnimating;
    private LinearLayout mAdsContainer;
    
    private int mCurIndex;
    private FavoriteDB mDB;
    private Toast mToast;
    
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_TOGGLE_TITLE_BAR:
                    toggleTitleBar();
                    break;
            }
        }
    };
    
    private ViewPager.OnPageChangeListener mOnPagerChangeListener = 
            new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int currentPage) {
                    mCurIndex = currentPage;
                    Context ctx = ImageViewerActivity.this;
                    ImageEntry e = mDataSource.getImageEntry(currentPage);
                    String text = String.format("%dx%d", e.imageWidth, e.imageHeight);
                    mSubTitleView.setText(text);
                    String title;
                    if (mDataSource.getDataSourceUrl().equals(Utils.URI_FAVORITE)) {
                        title = getResources().getString(R.string.my_favorite);
                    } else {
                        title = ImageUrls.getTagName(ctx, Prefs.getImageTag(ctx));
                    }
                    text = String.format("%s(%d/%d)", title,
                            currentPage + 1, mDataSource.getImageEntryNum());
                    mTitleView.setText(text);
                    if (mDB.exist(e.downloadUrl)) {
                        mFavoriteBtn.setImageResource(R.drawable.ic_already_favorite);
                    } else {
                        mFavoriteBtn.setImageResource(R.drawable.ic_favorite);
                    }
                }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN );
        setContentView(R.layout.image_viewer_activity);
        
        int imagePosition = getIntent().getIntExtra("image_position", 0);
        String url = getIntent().getStringExtra("data_source_url");
        Utils.log(Utils.INFO, TAG + "pos=" + imagePosition + "; data source=" + url);
        mInflater = getLayoutInflater();
        mDB = new FavoriteDB(this);
        mDataSource = DataSource.get(url);
        if (url == null || mDataSource == null) {
            Utils.log(Utils.ERROR, TAG + "data source null. url=" + url);
            finish();
            return;
        }
        if (mDataSource.getImageEntryNum() == 0) {
            Utils.log(Utils.ERROR, TAG + "data source size is 0 for url=" + url);
            finish();
            return;
        }
        mDownloadManager = new DownloadManager(this, mDataSource);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        ViewPagerAdapter adapter = new ViewPagerAdapter();
        mViewPager.setAdapter(adapter);
        mViewPager.setCurrentItem(imagePosition);
        mViewPager.setOnPageChangeListener(mOnPagerChangeListener);
        mViewPager.setEnabled(false);
        
        mTitleBar = findViewById(R.id.viewer_title_bar);
        mTitleView = (TextView)findViewById(R.id.title_text);
        mSubTitleView = (TextView)findViewById(R.id.title_sub_text);
        mBottomBar = findViewById(R.id.viewer_bottom_bar);
        mFavoriteBtn = (ImageView)findViewById(R.id.btn_favorite);
        mAnimSlideOutTop = AnimationUtils.loadAnimation(this, R.anim.slide_out_top);
        mAnimSlideInTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_top);
        mAnimSlideOutBottom = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
        mAnimSlideInBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
        mAnimSlideOutTop.setAnimationListener(this);
        mAnimSlideInTop.setAnimationListener(this);
        findViewById(R.id.btn_back).setOnClickListener(this);
        findViewById(R.id.btn_favorite).setOnClickListener(this);
        findViewById(R.id.btn_download).setOnClickListener(this);
        findViewById(R.id.btn_share).setOnClickListener(this);
        
        if (Prefs.autoHideTitleBar(this)) {
            mHandler.sendEmptyMessageDelayed(MSG_TOGGLE_TITLE_BAR, DUR_TITLE_BAR);
        }
        mCurIndex = imagePosition;
        mOnPagerChangeListener.onPageSelected(mCurIndex);
        
        if (Prefs.disableAds(this) == false) {
            mAdsContainer = (LinearLayout) findViewById(R.id.banner_ad_container);
            AppConnect.getInstance(this).showBannerAd(this, mAdsContainer);
        }
    }

    @Override
    protected void onDestroy() {
        mDownloadManager = null;
        mDataSource = null;
        mDB.close();
        super.onDestroy();
    }

    private void toggleTitleBar() {
        if (mAnimating) return;
        boolean visible = mTitleBar.getVisibility() == View.VISIBLE;
        if (visible) {
            mTitleBar.startAnimation(mAnimSlideOutTop);
            mBottomBar.startAnimation(mAnimSlideOutBottom);
        } else {
            hideBannerAds();
            mTitleBar.startAnimation(mAnimSlideInTop);
            mBottomBar.startAnimation(mAnimSlideInBottom);
        }
    }

    @Override
    public void onClick(View v) {
        ImageEntry e;
        String title;
        int id = v.getId();
        switch (id) {
            case R.id.image_viewer:
                toggleTitleBar();
                break;
            case R.id.btn_back:
                finish();
                break;
            case R.id.btn_favorite:
                e = mDataSource.getImageEntry(mCurIndex);
                if (!mDB.exist(e.downloadUrl)) {
                    mDB.add(e.downloadUrl, e.imageWidth, e.imageHeight);
                    ImageFileCache.addFavoriteCacheFile(this, e.downloadUrl);
                    mFavoriteBtn.setImageResource(R.drawable.ic_already_favorite);
                } else {
                    mDB.del(e.downloadUrl);
                    ImageFileCache.removeFavoriteCacheFile(this, e.downloadUrl);
                    mFavoriteBtn.setImageResource(R.drawable.ic_favorite);
                }
                break;
            case R.id.btn_download:
                new WorkTask().execute();
                break;
            case R.id.btn_share:
                Intent intent = new Intent(Intent.ACTION_SEND);
                String imgPath = getCacheFile(mDataSource, mCurIndex);
                File f = new File(imgPath);
                intent.setType("image/png");    
                Uri u = Uri.fromFile(f);
                intent.putExtra(Intent.EXTRA_STREAM, u); 
                title = getResources().getString(R.string.app_name);
                intent.putExtra(Intent.EXTRA_SUBJECT, title);    
                intent.putExtra(Intent.EXTRA_TEXT, title);    
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    
                startActivity(Intent.createChooser(intent, title)); 
                break;
        }
    }
    
    private void showBannerAds() {
        if (Prefs.disableAds(this) == false) {
            mAdsContainer.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideBannerAds() {
        if (Prefs.disableAds(this) == false) {
            mAdsContainer.setVisibility(View.INVISIBLE);
        }
    }
    
    @Override
    public void onAnimationEnd(Animation animation) {
        mAnimating = false;
        boolean visible = mTitleBar.getVisibility() == View.VISIBLE;
        if (visible) {
            mTitleBar.setVisibility(View.INVISIBLE);
            mBottomBar.setVisibility(View.INVISIBLE);
            showBannerAds();
        } else {
            mTitleBar.setVisibility(View.VISIBLE);
            mBottomBar.setVisibility(View.VISIBLE);
            if (Prefs.autoHideTitleBar(this)) {
                mHandler.removeMessages(MSG_TOGGLE_TITLE_BAR);
                mHandler.sendEmptyMessageDelayed(MSG_TOGGLE_TITLE_BAR, DUR_TITLE_BAR);
            }
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        
    }

    @Override
    public void onAnimationStart(Animation animation) {
        mAnimating = true;
    }
    
    private void toast(int msg) {
        if (mToast == null) {
            mToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(msg);
        }
        mToast.show();
    }
    
    private String getCacheFile(DataSource ds, int idx) {
        ImageEntry e = ds.getImageEntry(idx);
        File f = ImageFileCache.getCacheFile(this, e.favorite, e.downloadUrl);
        if (!f.exists()) {
            Bitmap bm = ImageMemoryCache.getBitmapFromCache(this, e.downloadUrl, false);
            if (bm != null) {
                f = ImageFileCache.saveBitmap(this, e.favorite, e.downloadUrl, bm);
            }
        }
        return f.getAbsolutePath();
    }
    
    class WorkTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            ImageEntry e = mDataSource.getImageEntry(mCurIndex);
            Bitmap bm = ImageMemoryCache.getBitmapFromCache(ImageViewerActivity.this, e.downloadUrl, false);
            ContentResolver cr = getContentResolver();
            String title = getResources().getString(R.string.app_name);
            MediaStore.Images.Media.insertImage(cr, bm, title, title + "_V" + Utils.getVerName(ImageViewerActivity.this));
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, 
                    Uri.parse("file://"+ Environment.getExternalStorageDirectory())));
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            toast(R.string.saved_to_album);
        }
    }
    
    class ViewPagerAdapter extends PagerAdapter {

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Utils.log(Utils.VERB, TAG + "instantiateItem for pos " + position);
            boolean needDownload = false;
            ImageEntry ie = mDataSource.getImageEntry(position);
            // get cache from full screen cache
            Bitmap bm = ImageMemoryCache.getBitmapFromCache(ImageViewerActivity.this, ie.downloadUrl, false);
            if (bm == null) {
                bm = ImageFileCache.readBitmap(ImageViewerActivity.this, ie.favorite, ie.downloadUrl);
            }
            if (bm == null) {
                needDownload = true;
            }
            View view = mInflater.inflate(R.layout.image_viewer, null);
            ImageView imageView = (ImageView)view.findViewById(R.id.image_viewer);
            imageView.setTag(ie);
            imageView.setImageBitmap(bm);
            imageView.setOnClickListener(ImageViewerActivity.this);
            container.addView(view);
            // start to download the image
            final View progressBar = view.findViewById(R.id.image_downloading);
            final View imageError = view.findViewById(R.id.image_error);
            imageError.setVisibility(View.INVISIBLE);
            if (needDownload) {
                progressBar.setVisibility(View.VISIBLE);
                DownloadManager.DownloadEventListener listener = new DownloadManager.DownloadEventListener() {

                    @Override
                    public void onDownloadFinished(boolean success) {
                        progressBar.setVisibility(View.INVISIBLE);
                        if (!success) {
                            imageError.setVisibility(View.VISIBLE);
                        } else {
                            mOnPagerChangeListener.onPageSelected(mCurIndex);
                        }
                    }
                };
                mDownloadManager.addItem(position, imageView, false, listener);
            } else {
                progressBar.setVisibility(View.INVISIBLE);
            }
            return view;
        }

        @Override
        public int getCount() {
            return mDataSource.getImageEntryNum();
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            Utils.log(Utils.VERB, TAG + "destroyItem for pos " + position);
            View view = (View) object;
            container.removeView(view);
            
            // remove the pending download or unref the ImageView widget
            mDownloadManager.delItem(position);
        }
    }
}
