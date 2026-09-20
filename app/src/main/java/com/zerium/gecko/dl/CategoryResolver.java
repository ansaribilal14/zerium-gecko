package com.zerium.gecko.dl;

import java.util.Locale;

/**
 * Maps URLs, file names and MIME types onto the download categories used
 * for the on-device folder layout (Download/Zerium/&lt;Category&gt;/).
 */
public final class CategoryResolver {

    public static final int VIDEO = 0;
    public static final int AUDIO = 1;
    public static final int IMAGE = 2;
    public static final int DOC = 3;
    public static final int ARCHIVE = 4;
    public static final int APK = 5;
    public static final int OTHER = 6;
    public static final int COUNT = 7;

    /** Sub-folder under Download/Zerium/. */
    public static final String[] FOLDER = {
            "Video", "Audio", "Images", "Docs", "Archives", "APKs", "Other"
    };

    private static final String[] VIDEO_EXT = {"mp4", "mkv", "webm", "mov", "avi",
            "m4v", "3gp", "ts", "flv", "mpg", "mpeg", "wmv", "m4s", "m3u8", "mpd"};
    private static final String[] AUDIO_EXT = {"mp3", "m4a", "aac", "ogg", "opus",
            "wav", "flac", "wma", "mid", "amr"};
    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "gif", "webp",
            "bmp", "heic", "avif", "svg", "ico"};
    private static final String[] DOC_EXT = {"pdf", "doc", "docx", "xls", "xlsx",
            "ppt", "pptx", "txt", "csv", "epub", "rtf", "odt", "ods", "odp", "json", "xml"};
    private static final String[] ARCHIVE_EXT = {"zip", "rar", "7z", "tar", "gz",
            "tgz", "bz2", "xz", "iso", "cab"};
    private static final String[] APK_EXT = {"apk", "apks", "xapk", "apkm"};

    private CategoryResolver() {}

    public static int resolve(String url, String mime, String filename) {
        String ext = extOf(filename);
        if (ext.isEmpty() && url != null) ext = extOf(url);
        int byExt = fromExt(ext);
        if (byExt >= 0) return byExt;
        int byMime = fromMime(mime);
        if (byMime >= 0) return byMime;
        return OTHER;
    }

    private static String extOf(String s) {
        if (s == null || s.isEmpty()) return "";
        String p = s.split("[?#]")[0];
        int i = p.lastIndexOf('.');
        if (i < 0 || i == p.length() - 1) return "";
        String e = p.substring(i + 1).toLowerCase(Locale.US);
        return e.length() > 8 ? "" : e;
    }

    private static int fromExt(String ext) {
        if (ext.isEmpty()) return -1;
        if (contains(APK_EXT, ext)) return APK;
        if (contains(VIDEO_EXT, ext)) return VIDEO;
        if (contains(AUDIO_EXT, ext)) return AUDIO;
        if (contains(IMAGE_EXT, ext)) return IMAGE;
        if (contains(ARCHIVE_EXT, ext)) return ARCHIVE;
        if (contains(DOC_EXT, ext)) return DOC;
        return -1;
    }

    private static int fromMime(String mime) {
        if (mime == null || mime.isEmpty()) return -1;
        String m = mime.toLowerCase(Locale.US);
        if (m.startsWith("video/")) return VIDEO;
        if (m.startsWith("audio/")) return AUDIO;
        if (m.startsWith("image/")) return IMAGE;
        if (m.equals("application/vnd.android.package-archive")) return APK;
        if (m.contains("zip") || m.contains("tar") || m.contains("compressed")) {
            return ARCHIVE;
        }
        if (m.startsWith("text/") || m.contains("pdf") || m.contains("document")
                || m.contains("sheet") || m.contains("presentation")
                || m.contains("msword") || m.contains("officedocument")
                || m.equals("application/epub+zip")) {
            return DOC;
        }
        return -1;
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }
}
