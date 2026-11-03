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
        // Only real navigated pages are history. Internal pages
        // (about:blank, about:home, data:, javascript:) never get recorded,
        // so neither the history screen nor the omnibox suggestions can
        // ever resurface a blank/error page.
        if (!Urls.isWebUrl(url)) return;
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

    /**
     * Omnibox history suggestions: newest entries whose URL or title
     * contains the query, deduplicated by URL (newest title wins). Fully
     * parameterized — the query string is never concatenated into SQL.
     * Returns at most {@code limit} entries, newest first.
     */
    public java.util.List<Entry> suggest(String query, int limit) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || limit <= 0) return out;
        String like = "%" + q.toLowerCase() + "%";
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(id), url, MAX(title) FROM history "
                        + "WHERE LOWER(url) LIKE ? OR LOWER(title) LIKE ? "
                        + "GROUP BY url ORDER BY MAX(ts) DESC LIMIT ?",
                new String[]{like, like, String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(new Entry(c.getLong(0), c.getString(1), c.getString(2)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Most-visited hosts for the start page tiles (visit counts by host). */
    public java.util.List<String> topHosts(int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT url, COUNT(*) AS n FROM history WHERE url LIKE 'http%' "
                        + "GROUP BY url ORDER BY n DESC LIMIT ?",
                new String[]{String.valueOf(limit * 5)})) {
            while (c.moveToNext()) {
                String host = Utils.hostOf(c.getString(0));
                if (host != null && !out.contains(host)) out.add(host);
                if (out.size() >= limit) break;
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }
}
