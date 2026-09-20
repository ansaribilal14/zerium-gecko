package com.zerium.gecko.dl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in the download manager. Persisted as a single JSON blob per
 * task so partial progress (segment offsets) survives process death.
 */
public class DownloadTask {

    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

    /** Range piece being fetched by one connection. */
    public static class Segment {
        public long start;
        public long end;          // inclusive byte offset
        public volatile long done;

        public Segment(long start, long end) { this.start = start; this.end = end; }
    }

    public long id;
    public String url;
    public String filename;
    public String mime;
    /** Human page title the download originated from (shown in the UI). */
    public String pageTitle;
    public String sourcePage;
    public String userAgent;
    public String referer;
    /** CategoryResolver constant. */
    public int category;
    public Status status = Status.QUEUED;
    /** -1 when the server did not declare a size. */
    public long totalBytes = -1;
    public long doneBytes;
    /** Smoothed bytes/second, recomputed by the engine ticker; not persisted. */
    public transient long speedBps;
    public String error;
    /** Content URI or file path of the finished download. */
    public String finalUri;
    /** HTTP Live Media playlist (m3u8) download. */
    public boolean hls;
    /** Multi-connection range downloading allowed (user setting). */
    public boolean turbo = true;
    /** In-page blob capture session id (null for regular URL downloads). */
    public String blobId;
    public long createdAt;
    public long completedAt;
    public int attempts;
    public final List<Segment> segments = new ArrayList<>();

    public float progressFraction() {
        if (totalBytes <= 0) return 0f;
        return Math.min(1f, doneBytes / (float) totalBytes);
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("url", str(url));
            o.put("filename", str(filename));
            o.put("mime", str(mime));
            o.put("pageTitle", str(pageTitle));
            o.put("sourcePage", str(sourcePage));
            o.put("userAgent", str(userAgent));
            o.put("referer", str(referer));
            o.put("category", category);
            o.put("status", status.name());
            o.put("totalBytes", totalBytes);
            o.put("doneBytes", doneBytes);
            o.put("error", str(error));
            o.put("finalUri", str(finalUri));
            o.put("hls", hls);
            o.put("turbo", turbo);
            o.put("blobId", str(blobId));
            o.put("createdAt", createdAt);
            o.put("completedAt", completedAt);
            o.put("attempts", attempts);
            JSONArray segs = new JSONArray();
            for (Segment s : segments) {
                segs.put(new JSONObject()
                        .put("s", s.start).put("e", s.end).put("d", s.done));
            }
            o.put("segments", segs);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static DownloadTask fromJson(JSONObject o) {
        DownloadTask t = new DownloadTask();
        t.id = o.optLong("id");
        t.url = o.optString("url", "");
        t.filename = o.optString("filename", "");
        t.mime = o.optString("mime", "");
        t.pageTitle = o.optString("pageTitle", "");
        t.sourcePage = o.optString("sourcePage", "");
        t.userAgent = o.optString("userAgent", "");
        t.referer = o.optString("referer", "");
        t.category = o.optInt("category", CategoryResolver.OTHER);
        try { t.status = Status.valueOf(o.optString("status", "QUEUED")); }
        catch (Exception e) { t.status = Status.QUEUED; }
        t.totalBytes = o.optLong("totalBytes", -1);
        t.doneBytes = o.optLong("doneBytes", 0);
        t.error = o.optString("error", "");
        t.finalUri = o.optString("finalUri", "");
        t.hls = o.optBoolean("hls", false);
        t.turbo = o.optBoolean("turbo", true);
        t.blobId = o.optString("blobId", "");
        t.createdAt = o.optLong("createdAt", 0);
        t.completedAt = o.optLong("completedAt", 0);
        t.attempts = o.optInt("attempts", 0);
        JSONArray segs = o.optJSONArray("segments");
        if (segs != null) {
            for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.optJSONObject(i);
                if (s == null) continue;
                Segment seg = new Segment(s.optLong("s"), s.optLong("e"));
                seg.done = s.optLong("d");
                t.segments.add(seg);
            }
        }
        return t;
    }

    private static String str(String v) { return v == null ? "" : v; }
}
