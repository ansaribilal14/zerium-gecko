package com.zerium.gecko;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local browsing history. Never leaves the device. */
public class HistoryDB extends SQLiteOpenHelper {

    public static class Entry {
        public final long id;
        public final String url;
        public final String title;
        public Entry(long id, String url, String title) {
            this.id = id; this.url = url; this.title = title;
        }
    }

    public HistoryDB(Context c) {
        super(c, "gecko_history.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY, url TEXT, title TEXT, ts INTEGER)");
        db.execSQL("CREATE INDEX h_ts ON history(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int a, int b) { }

    public void add(String url, String title) {
        if (url == null || !url.startsWith("http")) return;
        ContentValues cv = new ContentValues();
        cv.put("url", url);
        cv.put("title", title == null ? "" : title);
        cv.put("ts", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("history", null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public java.util.List<Entry> recent(int limit) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, url, title FROM history ORDER BY ts DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }
}
