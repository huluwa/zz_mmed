package com.sfox.beautyeveryday;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class DataSource {
    private static final String TAG = "[DataSource] ";
    private static final String IMGS_START_TAG = "app.loadImgs(";
    private static final String IMGS_END_TAG = "});";
    
    public String mCol;
    public int mTotalNum;
    public int mStartIndex;
    public int mReturnNumber;
    
    private String mUrl;
    private int mReqIndex;
    
    private Context mContext;
    
    private ArrayList<ImageEntry> mImages = new ArrayList<ImageEntry>();;
    
    private static ArrayList<DataSource> sDataSources = new ArrayList<DataSource>();
    
    public DataSource(Context ctx) {
        mContext = ctx;
        synchronized (sDataSources) {
            sDataSources.add(this);
        }
    }
    
    private String getJsonData(String url, int startIndex, String startTag, String endTag) {
        try {
            HttpGet httpReq = new HttpGet(url);
            Utils.log(Utils.INFO, TAG + "request url: " + url);
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse httpRsp = httpClient.execute(httpReq);
            if (httpRsp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String strResult = EntityUtils.toString(httpRsp.getEntity());
                Utils.log(Utils.DEBUG, TAG + "raw data length = " + strResult.length());
                int startPos = strResult.indexOf(startTag);
                if (startPos < 0 ) {
                    Utils.log(Utils.ERROR, TAG + "data error. cannot find the start tag for " + startTag);
                    return null;
                }
                startPos += startTag.length();
                int endPos = strResult.indexOf(endTag, startPos);
                if (endPos < 0) {
                    Utils.log(Utils.ERROR, TAG + "data error. cannot find the end tag for " + endTag);
                    return null;
                }
                return strResult.substring(startPos, endPos + 1);
            } else {
                Utils.log(Utils.ERROR, TAG + "HttpResponse Error. Code = " + httpRsp.getStatusLine().getStatusCode());
                return null;
            }
        } catch (Exception e) {
            Utils.log(Utils.ERROR, TAG + "HttpResponse Exceptions.\n" + e);
            return null;
        }
    }
    
    private boolean parseJsonData(String strJson) {
        try {
            JSONTokener parser = new JSONTokener(strJson);
            JSONObject ds = (JSONObject)parser.nextValue();
            
            mCol = ds.getString("col");
            mTotalNum = ds.getInt("totalNum");
            mStartIndex = ds.getInt("startIndex");
            mReturnNumber = ds.getInt("returnNumber");
            
            JSONArray imgArray = ds.getJSONArray("imgs");
            int len = imgArray.length();
            Utils.log(Utils.INFO, TAG + "json data summary: col:" + mCol + "; totalNum:" + mTotalNum + "; startIndex:" 
                    + mStartIndex + "; returnNumber:" + mReturnNumber + "; imgArray len:" + len);
            len = Math.min(mReturnNumber, len);
            mImages.clear();
            for (int i = 0; i < len; i ++) {
                ImageEntry entry = ImageEntry.fromJsonObject(imgArray.getJSONObject(i));
                if (entry != null ) {
                    mImages.add(entry);
                } else {
                    Utils.log(Utils.WARN, TAG + "entry is null for index " + i);
                }
            }
            if (mReturnNumber != mImages.size()) {
                Utils.log(Utils.ERROR, TAG + " number not match. " + mReturnNumber + "!=" + mImages.size());
            }
        } catch (Exception e) {
            Utils.log(Utils.ERROR, TAG + "parse Json Exceptions.\n" + e + "\n" + strJson);
        }
        return (mImages.size() > 0);
    }
    
    public void setImageEntrys(ArrayList<ImageEntry> images) {
        mImages.clear();
        mImages.addAll(images);
    }
    
    public int getImageEntryNum() {
        return mImages.size();
    }
    
    public ImageEntry getImageEntry(int index) {
        if (index < mImages.size()) {
            return mImages.get(index);
        } else {
            return null;
        }
    }
    
    /* download bitmap from Internet, if bThumbnail is true, decode as thumbnail */
    public Bitmap downloadBitmap(ImageEntry entry, boolean bThumbnail) {
        if (Utils.allowNetwork(mContext) == false) {
            return null;
        }
        
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpReq = new HttpGet(entry.downloadUrl);
        try {
            Utils.log(Utils.INFO, TAG + "download image [" + entry.imageWidth + " X " 
                    + entry.imageHeight + "] id: " + entry.id);
            HttpResponse httpRsp = httpClient.execute(httpReq);
            if (httpRsp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = httpRsp.getEntity();
                if (entity != null) {
                    Utils.log(Utils.DEBUG, TAG + "contentType: " + entity.getContentType() + "; contentLength: " + entity.getContentLength());
                    InputStream inputStream = null;
                    try {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        int hRadio = (int)Math.ceil(entry.imageHeight / (float)Utils.IMAGE_MAX_HEIGHT);
                        int wRadio = (int)Math.ceil(entry.imageWidth / (float)Utils.IMAGE_MAX_WIDTH);
                        if (hRadio > 1 && wRadio > 1) {
                            opt.inSampleSize = hRadio > wRadio ? hRadio:wRadio;
                        }
                        inputStream = entity.getContent();
                        FilterInputStream fit = new FlushedInputStream(inputStream);
                        Bitmap bm = BitmapFactory.decodeStream(fit, null, opt);
                        ImageFileCache.saveBitmap(mContext, entry.favorite, entry.downloadUrl, bm);
                        Utils.log(Utils.INFO, TAG + "decode to [" + bm.getWidth() + " X " 
                                + bm.getHeight() + "] id: " + entry.id);
                        entry.imageHeight = bm.getHeight();
                        entry.imageWidth = bm.getWidth();
                        return bm;
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                            inputStream = null;
                        }
                        entity.consumeContent();
                    }
                }
            } else {
                Utils.log(Utils.ERROR, TAG + "HttpResponse Error. Code = " + httpRsp.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            httpReq.abort();
            Utils.log(Utils.ERROR, TAG + "getBitmap Exceptions.\n" + e);
            return null;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }
    
    /*
     * An InputStream that skips the exact number of bytes provided, unless it reaches EOF.
     */
    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }
                                                      
        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break;  // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }
    
    public boolean update() {
        if (Utils.allowNetwork(mContext) == false) {
            return false;
        }
        
        if (mUrl != null) {
            String jsonData = getJsonData(mUrl, mReqIndex, IMGS_START_TAG, IMGS_END_TAG);
            if (jsonData == null) return false;
            
            String fname = Utils.getCacheDir(mContext, false) + "/" + convertUrlToFileName(mUrl);
            File file = new File(fname);
            try {
                file.createNewFile();
                OutputStream outStream = new FileOutputStream(file);
                outStream.write(jsonData.getBytes());
                outStream.flush();
                outStream.close();
            } catch (Exception e) {
                Utils.log(Utils.ERROR, TAG + " write json cache error. " + fname + "\n" + e);
            }
            
            return parseJsonData(jsonData);
        } else {
            Utils.log(Utils.ERROR, TAG + "update error. no url defined!");
        }
        return false;
    }
    
    public boolean readCache() {
        String fname = Utils.getCacheDir(mContext, false) + "/" + convertUrlToFileName(mUrl);
        File file = new File(fname);
        int size;
        if (file.exists() && file.canRead()) {
            try {
                size = (int)file.length();
                byte buf[] = new byte[size];
                InputStream in = new FileInputStream(file);
                in.read(buf);
                String jsonData = new String(buf, "UTF-8");
                in.close();
                Utils.log(Utils.INFO, TAG + "cache hit for json data. size: " + size);
                return parseJsonData(jsonData);
            } catch (Exception e) {
                Utils.log(Utils.ERROR, TAG + "read json cache error. " + fname + "\n" + e);
            }
        }
        return false;
    }
    
    public String getDataSourceUrl() {
        return mUrl;
    }
    
    public void setDataSourceUrl(String url) {
            mUrl = url;
    }
    
    private String convertUrlToFileName(String url) {
        return String.valueOf(url.hashCode()) + ".json";
    }
    
    public static class ImageEntry {
        public String id;
        public String date;
        public String downloadUrl;
        public int imageWidth;
        public int imageHeight;
        public boolean favorite;
        
        public ImageEntry() {
            favorite = false;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof ImageEntry) {
                ImageEntry ie = (ImageEntry) o;
                return downloadUrl.equals(ie.downloadUrl) && favorite == ie.favorite;
            }
            return false;
        }

        public static ImageEntry fromJsonObject(JSONObject img) {
            ImageEntry entry = new ImageEntry();
            try {
                entry.id = img.getString("id");
                entry.date = img.getString("date");
                entry.downloadUrl = img.getString("downloadUrl");
                if (entry.downloadUrl == null || "".equals(entry.downloadUrl)) {
                    entry.downloadUrl = img.getString("imageUrl");
                }
                entry.imageWidth = img.getInt("imageWidth");
                entry.imageHeight = img.getInt("imageHeight");
            } catch (Exception e) {
                Utils.log(Utils.ERROR, TAG + "parse Json Exceptions.\n" + e + "\n" + img);
                entry = null;
            }
            return entry;
        }
    }
    
    public static DataSource get(String url) {
        if (url == null) return null;
        synchronized (sDataSources) {
            Utils.log(Utils.DEBUG, TAG + "data source count=" + sDataSources.size());
            for (DataSource ds : sDataSources) {
                if (ds.mUrl.equals(url) && ds.getImageEntryNum() > 0) {
                    Utils.log(Utils.INFO, TAG + "data source size=" + ds.getImageEntryNum()
                            + ". url=" + url);
                    return ds;
                }
            }
        }
        return null;
    }
    
    public static void del(DataSource ds) {
        synchronized (sDataSources) {
            sDataSources.remove(ds);
        }
    }
    
    public static void clear() {
        synchronized (sDataSources) {
            sDataSources.clear();
        }
    }
}
