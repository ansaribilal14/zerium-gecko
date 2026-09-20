package com.zerium.gecko.dl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** SQLite persistence for download tasks (one JSON blob per row). */
public class DownloadStore extends SQLiteOpenHelper {

    private static final String DB = "zerium_downloads.db";
    private static final int VERSION = 1;
    private static final String TABLE = "tasks";

    public DownloadStore(Context c) {
        super(c.getApplicationContext(), DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE
                + " (id INTEGER PRIMARY KEY, json TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public synchronized List<DownloadTask> all() {
        List<DownloadTask> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase()
                .query(TABLE, new String[]{"json"}, null, null, null, null,
                        "id DESC")) {
            while (c != null && c.moveToNext()) {
                try {
                    out.add(DownloadTask.fromJson(new JSONObject(c.getString(0))));
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public synchronized void put(DownloadTask t) {
        ContentValues cv = new ContentValues();
        cv.put("id", t.id);
        cv.put("json", t.toJson().toString());
        getWritableDatabase().insertWithOnConflict(TABLE, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void remove(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearFinished() {
        getWritableDatabase().delete(TABLE,
                "status IN ('COMPLETED','FAILED')", null);
    }
}
