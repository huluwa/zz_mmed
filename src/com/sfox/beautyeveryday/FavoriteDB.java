package com.sfox.beautyeveryday;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.sfox.beautyeveryday.DataSource.ImageEntry;

import java.util.ArrayList;

public class FavoriteDB {

    private static final String TABLE_NAME = "favorite";
    private static final String[] sCols = new String[3];
    
    private DBHelper mHelper;
    private SQLiteDatabase mDb;
    
    static {
        sCols[0] = "url";
        sCols[1] = "width";
        sCols[2] = "height";
    }
    
    private class DBHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "favorite.db";
        private static final int DATABASE_VERSION = 1;
        
        public DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + 
                    "(_id INTEGER PRIMARY KEY AUTOINCREMENT, url VARCHAR, width INTEGER, height INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // do nothing right now
        }
    }
    
    public FavoriteDB(Context ctx) {
        mHelper = new DBHelper(ctx);
        mDb = mHelper.getWritableDatabase();
    }
    
    public boolean exist(String url) {
        boolean exists = false;
        String selection = "url=?";
        String[] selectionArgs = new String[] {url};
        Cursor c = mDb.query(TABLE_NAME, sCols, selection, selectionArgs, null, null, null);
        if (c.getCount() > 0) {
            exists = true;
        }
        return exists;
    }
    
    public boolean add(String url, int w, int h) {
        if (exist(url)) {
            return true;
        }
        ContentValues values = new ContentValues();
        values.put("url", url);
        values.put("width", w);
        values.put("height", h);
        return (mDb.insert(TABLE_NAME, null, values) >= 0);
    }
    
    public boolean del(String url) {
        String whereClause = "url=?";
        String[] whereArgs = new String[] {url};
        return (mDb.delete(TABLE_NAME, whereClause, whereArgs) > 0);
    }
    
    public int clear() {
        return mDb.delete(TABLE_NAME, "1", null);
    }
    
    public ArrayList<ImageEntry> get() {
        ArrayList<ImageEntry> imgs = new ArrayList<ImageEntry>();
        
        Cursor c = mDb.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        if (c.getCount() > 0) {
            c.moveToLast();
            do {
                ImageEntry img = new ImageEntry();
                img.id = String.valueOf(c.getInt(c.getColumnIndex("_id")));
                img.downloadUrl = c.getString(c.getColumnIndex("url"));
                img.imageWidth = c.getInt(c.getColumnIndex("width"));
                img.imageHeight = c.getInt(c.getColumnIndex("height"));
                img.favorite = true;
                
                imgs.add(img);
            } while (c.moveToPrevious());
        }
        c.close();
        return imgs;
    }
    
    public void close() {
        mDb.close();
    }
}
