package com.sfox.beautyeveryday;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RefreshLayout extends LinearLayout implements OnTouchListener {
    
    public static final int STATUS_PULL_TO_REFRESH = 0;
    public static final int STATUS_RELEASE_TO_REFRESH = 1;
    public static final int STATUS_REFRESHING = 2;
    public static final int STATUS_REFRESH_FINISHED = 3;

    public static final int SCROLL_SPEED = -20;

    private onRefreshListener mListener;

    private View mHeaderView;
    private GridView mGridView;
    private TextView mDescriptionView;

    private MarginLayoutParams mHeaderLayoutParams;

    private int mHeaderHeight;

    /**
     * STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH,
     * STATUS_REFRESHING or STATUS_REFRESH_FINISHED
     */
    private int mCurrentStatus = STATUS_REFRESH_FINISHED;;

    private int mLastStatus = mCurrentStatus;

    private float mYDown;

    private int mTouchSlop;
    private boolean mLoadOnce;

    private boolean mAbleToPull;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            mHeaderView.requestLayout();
        }
        
    };
    
    public RefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mHeaderView = LayoutInflater.from(context).inflate(R.layout.pull_to_refresh, null, true);
        mDescriptionView = (TextView) mHeaderView.findViewById(R.id.description);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        addView(mHeaderView, 0);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !mLoadOnce) {
            mHeaderHeight = -mHeaderView.getHeight();
            mHeaderLayoutParams = (MarginLayoutParams) mHeaderView.getLayoutParams();
            mHeaderLayoutParams.topMargin = mHeaderHeight;
            mGridView = (GridView) getChildAt(1);
            mGridView.setOnTouchListener(this);
            mLoadOnce = true;
            // sdk bug: need request layout one more time or Latest page will not layout correct
            mHandler.sendEmptyMessage(0);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        setIsAbleToPull(event);
        if (mAbleToPull) {
            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mYDown = event.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                float yMove = event.getRawY();
                int distance = (int) (yMove - mYDown);
                if (distance <= 0 && mHeaderLayoutParams.topMargin <= mHeaderHeight) {
                    return false;
                }
                if (distance < mTouchSlop) {
                    return false;
                }
                if (mCurrentStatus != STATUS_REFRESHING) {
                    if (mHeaderLayoutParams.topMargin >= 0) {
                        mCurrentStatus = STATUS_RELEASE_TO_REFRESH;
                    } else {
                        mCurrentStatus = STATUS_PULL_TO_REFRESH;
                    }
                    mHeaderLayoutParams.topMargin = (distance / 2) + mHeaderHeight;
                    if (mHeaderLayoutParams.topMargin > 0) {
                        mHeaderLayoutParams.topMargin = 0;
                    }
                    mHeaderView.setLayoutParams(mHeaderLayoutParams);
                }
                break;
            case MotionEvent.ACTION_UP:
            default:
                if (mCurrentStatus == STATUS_RELEASE_TO_REFRESH) {
                    new RefreshingTask().execute();
                } else if (mCurrentStatus == STATUS_PULL_TO_REFRESH) {
                    new HideHeaderTask().execute();
                }
                break;
            }
            
            if (mCurrentStatus == STATUS_PULL_TO_REFRESH
                    || mCurrentStatus == STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
                mGridView.setPressed(false);
                mGridView.setFocusable(false);
                mGridView.setFocusableInTouchMode(false);
                mLastStatus = mCurrentStatus;
                return true;
            }
        }
        return false;
    }

    public void setOnRefreshListener(onRefreshListener listener, int id) {
        mListener = listener;
    }

    private void setIsAbleToPull(MotionEvent event) {
        if (mCurrentStatus == STATUS_REFRESHING) {
            return;
        }
        View firstChild = mGridView.getChildAt(0);
        if (firstChild != null) {
            int firstVisiblePos = mGridView.getFirstVisiblePosition();
            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
                if (!mAbleToPull) {
                    mYDown = event.getRawY();
                }
                mAbleToPull = true;
            } else {
                if (mHeaderLayoutParams.topMargin != mHeaderHeight) {
                    mHeaderLayoutParams.topMargin = mHeaderHeight;
                    mHeaderView.setLayoutParams(mHeaderLayoutParams);
                }
                mAbleToPull = false;
            }
        } else {
            /* list view is empty */
            mAbleToPull = true;
        }
    }

    private void updateHeaderView() {
        if (mLastStatus != mCurrentStatus) {
            if (mCurrentStatus == STATUS_PULL_TO_REFRESH) {
                mDescriptionView.setText(getResources().getString(R.string.pull_to_refresh));
            } else if (mCurrentStatus == STATUS_RELEASE_TO_REFRESH) {
                mDescriptionView.setText(getResources().getString(R.string.release_to_refresh));
            } else if (mCurrentStatus == STATUS_REFRESHING) {
                mDescriptionView.setText(getResources().getString(R.string.refreshing));
            }
        }
    }

    class RefreshingTask extends AsyncTask<Void, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            if (mListener != null) {
                mListener.onRefreshStart();
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            int topMargin = mHeaderLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= mHeaderHeight) {
                    topMargin = mHeaderHeight;
                    break;
                }
                publishProgress(topMargin);
                sleep(20);
            }
            mCurrentStatus = STATUS_REFRESHING;
            publishProgress(topMargin);
            
            boolean result = true;
            if (mListener != null) {
                result = mListener.onRefresh();
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            updateHeaderView();
            mHeaderLayoutParams.topMargin = topMargin[0];
            mHeaderView.setLayoutParams(mHeaderLayoutParams);
        }

        @Override
        protected void onPostExecute(Boolean params) {
            mCurrentStatus = STATUS_REFRESH_FINISHED;
            if (mListener != null) {
                mListener.onRefreshFinished(params);
            }
        }
    }

    class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {
            int topMargin = mHeaderLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= mHeaderHeight) {
                    topMargin = mHeaderHeight;
                    break;
                }
                publishProgress(topMargin);
                sleep(20);
            }
            return topMargin;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            mHeaderLayoutParams.topMargin = topMargin[0];
            mHeaderView.setLayoutParams(mHeaderLayoutParams);
        }

        @Override
        protected void onPostExecute(Integer topMargin) {
            mHeaderLayoutParams.topMargin = topMargin;
            mHeaderView.setLayoutParams(mHeaderLayoutParams);
            mCurrentStatus = STATUS_REFRESH_FINISHED;
        }
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public interface onRefreshListener {
        /* onRefreshStart is called on UI thread */
        void onRefreshStart();
        /* onRefresh is called in on background task thread. */
        boolean onRefresh();
        /* onRefreshFinished is called on UI thread */
        void onRefreshFinished(boolean successed);
    }
}

