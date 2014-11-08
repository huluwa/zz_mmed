
package com.sfox.beautyeveryday;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import cn.waps.AppConnect;

import com.astuetz.viewpager.extensions.FixedTabsView;
import com.astuetz.viewpager.extensions.TabsAdapter;
import com.astuetz.viewpager.extensions.ViewPagerTabButton;
import com.umeng.analytics.MobclickAgent;
import com.umeng.fb.FeedbackAgent;
import com.umeng.update.UmengUpdateAgent;

public class MainActivity extends ActionBarActivity 
                    implements ImageListFragment.UpdateListener {
    private static final String TAG = "[UI] ";
    
    private static final int MSG_BACK_TO_EXIT = 1;
    private static final int MSG_UPDATE_DATASOURCE = 2;
    private static final int MSG_CHECK_UPDATE = 3;  // check software update
    
    private AppSectionsPagerAdapter mPagerAdapter;
    private ViewPager mViewPager;
    private ActionBar mActionBar;
    
    private FixedTabsView mFixedTabs;
    private FixedTabsAdapter mFixedTabsAdapter;
    private Toast mToast;
    private SpinnerAdapter mTagsAdapter;
    private ActionBar.OnNavigationListener mTagsNavListener = new ActionBar.OnNavigationListener() {
        
        @Override
        public boolean onNavigationItemSelected(int pos, long itemid) {
            if (mRunOnce == false) {
                mRunOnce = true;
                return true;
            }
            
            Utils.log(Utils.INFO, TAG + "onNavigationItemSelected tag = " + pos);
            Context ctx = MainActivity.this;
            Prefs.setImageTag(ctx, pos);
            if (mHotFragment != null && mViewPager.getCurrentItem() == 0) {
                mHotFragment.updateDataSource(ctx, true);
            }
            if (mLatestFragment != null && mViewPager.getCurrentItem() == 1) {
                mLatestFragment.updateDataSource(ctx, true);
            }
            return true;
        }
    };
    
    private ImageListFragment mHotFragment;
    private ImageListFragment mLatestFragment;
    
    private int mRefreshing;
    
    private boolean mRunOnce = false;
    private boolean mBackKeyPressedOnce = false;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BACK_TO_EXIT:
                    mBackKeyPressedOnce = false;
                    break;
                case MSG_UPDATE_DATASOURCE:
                    if (mLatestFragment != null) {
                        mLatestFragment.updateDataSource(true);
                    } else {
                        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DATASOURCE, 50);
                    }
                    break;
                case MSG_CHECK_UPDATE:
                    FeedbackAgent fb = new FeedbackAgent(MainActivity.this);
                    fb.sync();
                    UmengUpdateAgent.silentUpdate(MainActivity.this);
                    break;
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        mActionBar = getSupportActionBar();
        mActionBar.setHomeButtonEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mTagsAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, 
                ImageUrls.getTags(this));
        mActionBar.setListNavigationCallbacks(mTagsAdapter, mTagsNavListener);
        mActionBar.setSelectedNavigationItem(Prefs.getImageTag(this));
        
        mPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mPagerAdapter);
        
        mFixedTabsAdapter = new FixedTabsAdapter();
        mFixedTabs = (FixedTabsView) findViewById(R.id.fixed_tabs);
        mFixedTabs.setAdapter(mFixedTabsAdapter);
        mFixedTabs.setViewPager(mViewPager);

        mViewPager.setCurrentItem(1);
        
        Utils.setOfflineMode(false);
        if (Prefs.downloadOnlyInWIFI(this) == false) {
            if (Utils.wifiConnected(this) == false) {
                alertUser();
            } else {
                mHandler.sendEmptyMessage(MSG_UPDATE_DATASOURCE);
            }
        } else {
            if (Utils.wifiConnected(this)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_DATASOURCE);
            } else {
                mToast = Toast.makeText(getApplicationContext(), R.string.offline_mode, Toast.LENGTH_SHORT);
                mToast.show();
                Utils.setOfflineMode(true);
            }
        }
        
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_UPDATE, 10000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onResume(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onDestroy() {
        Utils.log(Utils.INFO, TAG + "MainActivity onDestroy");
        AppConnect.getInstance(this).close();
        ImageFileCache.trimCache(this);
        mRunOnce = false;
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (mHotFragment != null && mViewPager.getCurrentItem() == 0) {
                    mHotFragment.updateDataSource(true);
                }
                if (mLatestFragment != null && mViewPager.getCurrentItem() == 1) {
                    mLatestFragment.updateDataSource(true);
                }
                break;
            case R.id.action_setting:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        if (mRefreshing > 0) {
            menu.findItem(R.id.action_refresh).setVisible(false);
        } else {
            menu.findItem(R.id.action_refresh).setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mBackKeyPressedOnce) {
                mToast.cancel();
                finish();
            } else {
                mBackKeyPressedOnce = true;
                if (mToast == null) {
                    mToast = Toast.makeText(getApplicationContext(), R.string.press_to_exit, Toast.LENGTH_SHORT);
                } else {
                    mToast.setDuration(Toast.LENGTH_SHORT);
                    mToast.setText(R.string.press_to_exit);
                }
                mToast.show();
                mHandler.sendEmptyMessageDelayed(MSG_BACK_TO_EXIT, 3000);
            }
        }
        return true;
    }

    private void alertUser() {
        new AlertDialog.Builder(this)
        .setTitle(R.string.data_alert)
        .setMessage(R.string.data_throughput_alert)
        .setNegativeButton(R.string.view_offline, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Utils.setOfflineMode(true);
            }
        })
        .setPositiveButton(R.string.IAmOK, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mLatestFragment.updateDataSource(true);
            }
        }).create().show();
    }
    
    class AppSectionsPagerAdapter extends FragmentPagerAdapter {

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            Utils.log(Utils.INFO, TAG + "AppSectionsPagerAdapter getItem " + i);
            switch (i) {
                case 0:
                    if (mHotFragment == null) {
                        mHotFragment = new ImageListFragment(0);
                    }
                    mHotFragment.setUpdateListener(MainActivity.this);
                    return mHotFragment;
                case 1:
                    if (mLatestFragment == null) {
                        mLatestFragment = new ImageListFragment(1);
                    }
                    mLatestFragment.setUpdateListener(MainActivity.this);
                    return mLatestFragment;

                default:
                    return new ImageFavoriteFragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getStringArray(R.array.image_category)[position];
        }
    }

    class FixedTabsAdapter implements TabsAdapter {
        public FixedTabsAdapter() {
        }
        
        @Override
        public View getView(int position) {
            ViewPagerTabButton tab;
            
            LayoutInflater inflater = getLayoutInflater();
            tab = (ViewPagerTabButton) inflater.inflate(R.layout.tab_fixed, null);
            String[] catNames = getResources().getStringArray(R.array.image_category);
            if (position < catNames.length) tab.setText(catNames[position]);
            
            return tab;
        }
    }

    @Override
    public void onStartUpdate() {
        mRefreshing ++;
        if (mRefreshing > 0) {
            supportInvalidateOptionsMenu();
            setSupportProgressBarIndeterminateVisibility(true);
            
        }
    }

    @Override
    public void onFinishUpdate(boolean success) {
        mRefreshing --;
        if (mRefreshing <= 0) {
            supportInvalidateOptionsMenu();
            setSupportProgressBarIndeterminateVisibility(false);
        }
        if (!success) {
            int msgid = R.string.update_failed;
            if (Utils.offlineMode()) {
                msgid = R.string.offline_mode;
            }
            if (mToast == null) {
                mToast = Toast.makeText(getApplicationContext(), msgid, Toast.LENGTH_LONG);
            } else {
                mToast.setDuration(Toast.LENGTH_LONG);
                mToast.setText(msgid);
            }
            mToast.show();
        }
    }
}

