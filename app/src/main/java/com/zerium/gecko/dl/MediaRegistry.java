package com.zerium.gecko.dl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-tab registry of media items detected by the in-page scanner (DOM) and
 * the network sniffer. Feeds the media grabber sheet.
 */
public final class MediaRegistry {

    public static final String TAG_VIDEO = "video";
    public static final String TAG_AUDIO = "audio";
    public static final String TAG_HLS = "hls";
    public static final String TAG_BLOB = "blob";
    public static final String TAG_FILE = "file";

    /** One detected downloadable resource. */
    public static class Item {
        public String url;
        public String tag;
        public String mime;
        public String label;      // resolution, host hint, etc.
        public long size = -1;    // bytes, -1 unknown

        public Item(String url, String tag, String mime, String label, long size) {
            this.url = url;
            this.tag = tag;
            this.mime = mime;
            this.label = label;
            this.size = size;
        }
    }

    private static final int MAX_PER_TAB = 150;
    private static final int MAX_NET = 200;

    private final Map<Long, LinkedHashMap<String, Item>> perTab = new LinkedHashMap<>();
    /** Network-sniffed media with no tab association (GeckoView background). */
    private final LinkedHashMap<String, Item> network = new LinkedHashMap<>();

    public synchronized void add(long tabId, Item item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        LinkedHashMap<String, Item> m = perTab.get(tabId);
        if (m == null) {
            m = new LinkedHashMap<>();
            perTab.put(tabId, m);
        }
        if (!m.containsKey(item.url)) m.put(item.url, item);
        while (m.size() > MAX_PER_TAB) {
            String first = m.keySet().iterator().next();
            m.remove(first);
        }
    }

    public synchronized void addNetwork(Item item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        if (!network.containsKey(item.url)) network.put(item.url, item);
        while (network.size() > MAX_NET) {
            String first = network.keySet().iterator().next();
            network.remove(first);
        }
    }

    public synchronized List<Item> items(long tabId) {
        LinkedHashMap<String, Item> m = perTab.get(tabId);
        if (m == null || m.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(m.values());
    }

    public synchronized List<Item> networkItems() {
        return new ArrayList<>(network.values());
    }

    public synchronized int count(long tabId) {
        LinkedHashMap<String, Item> m = perTab.get(tabId);
        return m == null ? 0 : m.size();
    }

    public synchronized void clear(long tabId) {
        perTab.remove(tabId);
    }

    public synchronized void clearNetwork() {
        network.clear();
    }

    // ---------- URL classification ----------

    private static final String[] VIDEO_EXT = {"mp4", "mkv", "webm", "mov", "avi",
            "m4v", "3gp", "ts", "flv", "mpg", "mpeg", "wmv", "m4s"};
    private static final String[] AUDIO_EXT = {"mp3", "m4a", "aac", "ogg", "opus",
            "wav", "flac", "wma", "mid", "amr"};
    private static final String[] FILE_EXT = {"zip", "rar", "7z", "tar", "gz",
            "tgz", "bz2", "xz", "iso", "pdf", "doc", "docx", "xls", "xlsx",
            "ppt", "pptx", "epub", "apk"};

    /** Java-side classification for network-level sniffing. */
    public static String classifyUrl(String url) {
        if (url == null) return null;
        String p = url.split("[?#]")[0].toLowerCase();
        if (p.endsWith(".m3u8")) return TAG_HLS;
        if (p.endsWith(".mpd")) return TAG_HLS; // DASH manifest (HLS-class handling)
        int dot = p.lastIndexOf('.');
        if (dot < 0 || dot == p.length() - 1) return null;
        String e = p.substring(dot + 1);
        for (String x : VIDEO_EXT) if (x.equals(e)) return TAG_VIDEO;
        for (String x : AUDIO_EXT) if (x.equals(e)) return TAG_AUDIO;
        for (String x : FILE_EXT) if (x.equals(e)) return TAG_FILE;
        return null;
    }

    /** True when the MIME type marks a media resource worth listing. */
    public static boolean isMediaMime(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase();
        return m.startsWith("video/") || m.startsWith("audio/")
                || m.contains("mpegurl") || m.contains("dash+xml");
    }
}
