package com.zerium.gecko;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local bookmarks. Never leaves the device. */
public class BookmarksDB extends SQLiteOpenHelper {

    public static class Entry {
        public final long id;
        public final String url;
        public final String title;
        public Entry(long id, String url, String title) {
            this.id = id; this.url = url; this.title = title;
        }
    }

    public BookmarksDB(Context c) {
        super(c, "gecko_bookmarks.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY, url TEXT UNIQUE, title TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int a, int b) { }

    public void add(String url, String title) {
        ContentValues cv = new ContentValues();
        cv.put("url", url);
        cv.put("title", title == null ? "" : title);
        getWritableDatabase().insertWithOnConflict("bookmarks", null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public boolean contains(String url) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM bookmarks WHERE url = ?", new String[]{url})) {
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    public void remove(long id) {
        getWritableDatabase().delete("bookmarks", "id = ?", new String[]{String.valueOf(id)});
    }

    public java.util.List<Entry> all() {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, url, title FROM bookmarks ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2)));
            }
        } catch (Exception ignored) {}
        return out;
    }
}
