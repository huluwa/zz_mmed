package com.sfox.beautyeveryday;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.widget.Toast;

import cn.waps.AppConnect;

import com.umeng.analytics.MobclickAgent;
import com.umeng.fb.FeedbackAgent;
import com.umeng.update.UmengUpdateAgent;
import com.umeng.update.UmengUpdateListener;
import com.umeng.update.UpdateResponse;
import com.umeng.update.UpdateStatus;

public class SettingsActivity extends PreferenceActivity 
        implements  OnSharedPreferenceChangeListener, 
                    OnPreferenceClickListener {
    private static final String TAG = "[Settings] ";
    
    Preference mClearCache;
    Preference mClearFavorite;
    CheckBoxPreference mDisableAds;
    
    ProgressDialog mDialog;
    Toast mToast;
    
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        if (Utils.FEATURE_ENABLE_CELL_SETTING == false) {
            PreferenceGroup prefs = (PreferenceGroup) findPreference("pref_category_general");
            prefs.removePreference(findPreference(Prefs.PREFS_COLUMN_NUMBER));
        }
        if (Utils.FEATURE_ENABLE_HIDE_TITLE == false) {
            PreferenceGroup prefs = (PreferenceGroup) findPreference("pref_category_general");
            prefs.removePreference(findPreference(Prefs.PREFS_AUTO_HIDE_TITLE_BAR));
        }
        new CacheTask(CacheTask.TASK_TRIM_CACHE).execute();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPause() {
        super.onPause();
        mClearCache.setOnPreferenceClickListener(null);
        mClearFavorite.setOnPreferenceClickListener(null);
        mDisableAds.setOnPreferenceClickListener(null);
        findPreference(Prefs.PREFS_CHECK_VERSION).setOnPreferenceClickListener(null);
        findPreference(Prefs.PREFS_FEEDBACK).setOnPreferenceClickListener(null);
        findPreference(Prefs.PREFS_HOT_APPS).setOnPreferenceClickListener(null);
        
        mClearCache = null;
        mClearFavorite = null;
        Prefs.unregisterPrefsChangeListener(this, this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        mClearCache = findPreference(Prefs.PREFS_CLEAR_CACHE);
        mClearFavorite = findPreference(Prefs.PREFS_CLEAR_FAVORITE);
        mDisableAds = (CheckBoxPreference) findPreference(Prefs.PREFS_DISABLE_ADS);
        mDisableAds.setChecked(Prefs.disableAds(this));
        mClearCache.setOnPreferenceClickListener(this);
        mClearFavorite.setOnPreferenceClickListener(this);
        mDisableAds.setOnPreferenceClickListener(this);
        findPreference(Prefs.PREFS_CHECK_VERSION).setOnPreferenceClickListener(this);
        findPreference(Prefs.PREFS_FEEDBACK).setOnPreferenceClickListener(this);
        findPreference(Prefs.PREFS_HOT_APPS).setOnPreferenceClickListener(this);
        
        
        Preference p = findPreference(Prefs.PREFS_CACHE_LIMIT);
        String title = getResources().getString(R.string.preferences_cache_limit);
        String value = getReadableValue(String.valueOf(Prefs.cacheSizeLimit(this)),
                R.array.preferences_cache_limit, 
                R.array.preferences_cache_limit_values);
        p.setTitle(title + " - " + value);
        
        if (Utils.FEATURE_ENABLE_CELL_SETTING) {
            p = findPreference(Prefs.PREFS_COLUMN_NUMBER);
            title = getResources().getString(R.string.preferences_cell_number);
            value = getReadableValue(String.valueOf(Prefs.cellColumnNumber(this)),
                    R.array.preferences_cell_number, 
                    R.array.preferences_cell_number_values);
            p.setTitle(title + " - " + value);
        }
        p = findPreference(Prefs.PREFS_CHECK_VERSION);
        title = getResources().getString(R.string.preferences_check_version_prompt);
        value = Utils.getVerName(this);
        p.setSummary(title + " - " + value);
        
        new CacheTask(CacheTask.TASK_CALC_CACHE).execute();
        new CacheTask(CacheTask.TASK_CALC_FAVORITE).execute();
        
        Prefs.registerPrefsChangeListener(this, this);
    }
    
    private String getReadableValue(String value, int titleArray, int valueArray) {
        String[] ta = getResources().getStringArray(titleArray);
        String[] va = getResources().getStringArray(valueArray);
        for (int i = 0; i < va.length; i ++) {
            if (va[i].equals(value)) {
                return ta[i];
            }
        }
        return "";
    }
    
    private void toast(int msg) {
        if (mToast == null) {
            mToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(msg);
        }
        mToast.show();
    }
    
    private void waitingDialog(int msg) {
        if (mDialog == null) {
            Resources res = getResources();
            mDialog = new ProgressDialog(this);
            mDialog.setMessage(res.getString(msg));
            mDialog.setIndeterminate(true);
            mDialog.setIndeterminateDrawable(res.getDrawable(R.drawable.progress_large_holo));
            mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        } else {
            mDialog.setMessage(getResources().getString(msg));
        }
        mDialog.show();
    }
    
    class CacheTask extends AsyncTask<Void, Void, Void> {
        static final int TASK_CALC_CACHE = 1;
        static final int TASK_CALC_FAVORITE = 2;
        static final int TASK_CLEAR_CACHE = 3;
        static final int TASK_CLEAR_FAVORITE = 4;
        static final int TASK_TRIM_CACHE = 5;
        
        private int mTask;
        private long mCacheSize;
        private long mFavoriteSize;
        
        public CacheTask(int task) {
            mTask = task;
        }
        
        @Override
        protected Void doInBackground(Void... params) {
            switch(mTask) {
                case TASK_CALC_CACHE:
                    mCacheSize = ImageFileCache.getCacheSize(SettingsActivity.this, false);
                    break;
                case TASK_CALC_FAVORITE:
                    mFavoriteSize = ImageFileCache.getCacheSize(SettingsActivity.this, true);
                    break;
                case TASK_CLEAR_CACHE:
                    ImageFileCache.clearCache(SettingsActivity.this, false);
                    break;
                case TASK_CLEAR_FAVORITE:
                    ImageFileCache.clearCache(SettingsActivity.this, true);
                    FavoriteDB db = new FavoriteDB(SettingsActivity.this);
                    db.clear();
                    db.close();
                    break;
                case TASK_TRIM_CACHE:
                    ImageFileCache.trimCache(SettingsActivity.this);
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            String summary;
            switch(mTask) {
                case TASK_CALC_CACHE:
                    if (mClearCache != null) {
                        summary = getResources().getString(R.string.preferences_clear_cache_prompt);
                        mClearCache.setSummary(summary + " - " + Utils.readableSize(mCacheSize));
                    }
                    break;
                case TASK_CALC_FAVORITE:
                    if (mClearFavorite != null) {
                        summary = getResources().getString(R.string.preferences_clear_favorite_prompt);
                        mClearFavorite.setSummary(summary + " - " + Utils.readableSize(mFavoriteSize));
                    }
                    break;
                case TASK_CLEAR_CACHE:
                    mDialog.dismiss();
                    toast(R.string.clear_cache_finished);
                    summary = getResources().getString(R.string.preferences_clear_cache_prompt);
                    mClearCache.setSummary(summary + " - 0");
                    break;
                case TASK_CLEAR_FAVORITE:
                    mDialog.dismiss();
                    toast(R.string.clear_favorite_finished);
                    summary = getResources().getString(R.string.preferences_clear_favorite_prompt);
                    mClearFavorite.setSummary(summary + " - 0");
                    break;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Prefs.PREFS_CACHE_LIMIT)) {
            Preference p = findPreference(Prefs.PREFS_CACHE_LIMIT);
            String title = getResources().getString(R.string.preferences_cache_limit);
            String value = getReadableValue(String.valueOf(Prefs.cacheSizeLimit(this)),
                    R.array.preferences_cache_limit, 
                    R.array.preferences_cache_limit_values);
            p.setTitle(title + " - " + value);
        } else if (key.equals(Prefs.PREFS_COLUMN_NUMBER)) {
            Preference p = findPreference(Prefs.PREFS_COLUMN_NUMBER);
            String title = getResources().getString(R.string.preferences_cell_number);
            String value = getReadableValue(String.valueOf(Prefs.cellColumnNumber(this)),
                    R.array.preferences_cell_number, 
                    R.array.preferences_cell_number_values);
            p.setTitle(title + " - " + value);
        } else if (key.equals(Prefs.PREFS_DOWNLOAD_ONLY_IN_WIFI)) {
            if (Prefs.downloadOnlyInWIFI(this) == false) {
                Utils.setOfflineMode(false);
            } else {
                Utils.setOfflineMode(true);
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key.equals(Prefs.PREFS_CLEAR_CACHE)) {
            new CacheTask(CacheTask.TASK_CLEAR_CACHE).execute();
            waitingDialog(R.string.clear_cache);
            return true;
        } else if (key.equals(Prefs.PREFS_CLEAR_FAVORITE)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.preferences_clear_favorite)
                    .setMessage(R.string.confirm_clear_favorite)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new CacheTask(CacheTask.TASK_CLEAR_FAVORITE).execute();
                            waitingDialog(R.string.clear_favorite);
                        }
                    }).create().show();
            return true;
        } else if (key.equals(Prefs.PREFS_CHECK_VERSION)) {
            UmengUpdateAgent.setUpdateAutoPopup(false);
            UmengUpdateAgent.setUpdateListener(new UmengUpdateListener() {
                @Override
                public void onUpdateReturned(int updateStatus,UpdateResponse updateInfo) {
                    mDialog.dismiss();
                    UmengUpdateAgent.setUpdateAutoPopup(true);
                    Utils.log(Utils.INFO, TAG + "update status=" + updateStatus);
                    switch (updateStatus) {
                    case UpdateStatus.Yes: // has update
                        UmengUpdateAgent.showUpdateDialog(SettingsActivity.this, updateInfo);
                        break;
                    case UpdateStatus.No:
                        toast(R.string.no_new_version);
                        break;
                    case UpdateStatus.Timeout: // time out
                        toast(R.string.check_version_timeout);
                        break;
                    }
                }
            });
            UmengUpdateAgent.forceUpdate(this);
            waitingDialog(R.string.checking_new_version);
            return true;
        } else if (key.equals(Prefs.PREFS_FEEDBACK)) {
            FeedbackAgent fb = new FeedbackAgent(this);
            fb.startFeedbackActivity();
            return true;
        } else if (key.equals(Prefs.PREFS_HOT_APPS)) {
            AppConnect.getInstance(this).showOffers(this);
            return true;
        } else if (key.equals(Prefs.PREFS_DISABLE_ADS)) {
            if (Prefs.disableAds(this) == false) {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.preferences_disable_ads)
                    .setMessage(R.string.confirm_disable_ads)
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mDisableAds.setChecked(false);
                            Prefs.setDisableAds(SettingsActivity.this, false);
                        }
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MobclickAgent.onEvent(SettingsActivity.this, "ev_disable_ads");
                            mDisableAds.setChecked(true);
                            Prefs.setDisableAds(SettingsActivity.this, true);
                        }
                }).create().show();
            } else {
                MobclickAgent.onEvent(SettingsActivity.this, "ev_enable_ads");
                mDisableAds.setChecked(false);
                Prefs.setDisableAds(this, false);
                toast(R.string.thanks_for_support);
            }
            return true;
        }
        return false;
    }
}
