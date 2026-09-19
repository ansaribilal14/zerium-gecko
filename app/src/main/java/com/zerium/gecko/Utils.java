package com.zerium.gecko;

import android.net.Uri;

/** Small shared helpers: search engines and URL parsing. */
public class Utils {

    public static class Engine {
        public final String name;
        public final String query;
        public Engine(String name, String query) { this.name = name; this.query = query; }
    }

    private static final Engine[] ENGINES = {
            new Engine("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
            new Engine("Startpage", "https://www.startpage.com/sp/search?query=%s"),
            new Engine("Brave Search", "https://search.brave.com/search?q=%s"),
            new Engine("Google", "https://www.google.com/search?q=%s"),
            new Engine("Bing", "https://www.bing.com/search?q=%s"),
            new Engine("Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
    };

    public static Engine engine(Prefs prefs) {
        int i = prefs.searchEngine();
        if (i < 0 || i >= ENGINES.length) i = 0;
        return ENGINES[i];
    }

    public static String hostOf(String url) {
        if (url == null) return null;
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns a loadable URL for the omnibox text: URL if it looks like one, else a search query. */
    public static String smartUrl(String input, Prefs prefs) {
        if (input == null) return null;
        input = input.trim();
        if (input.isEmpty()) return null;
        String lower = input.toLowerCase();
        boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://");
        boolean looksLikeHost = !input.contains(" ")
                && (input.contains(".") || lower.equals("localhost"))
                && !input.endsWith(".");
        if (hasScheme || looksLikeHost) {
            return hasScheme ? input : "https://" + input;
        }
        String q = Uri.encode(input);
        return engine(prefs).query.replace("%s", q);
    }

    public static String fileNameFromUrl(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (path == null || path.isEmpty()) return "zerium-download";
            return path;
        } catch (Exception e) {
            return "zerium-download";
        }
    }

    // ---------- Site lists (newline-separated lowercase hosts) ----------

    public static boolean siteListContains(String list, String host) {
        if (list == null || list.isEmpty() || host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        for (String entry : list.split("\n")) {
            if (entry.trim().toLowerCase().equals(h)) return true;
        }
        return false;
    }

    public static String siteListAdd(String list, String host) {
        if (host == null || host.isEmpty()) return list;
        String h = host.toLowerCase();
        if (siteListContains(list, h)) return list;
        return list == null || list.trim().isEmpty() ? h : list.trim() + "\n" + h;
    }

    public static String siteListRemove(String list, String host) {
        if (list == null || list.isEmpty() || host == null) return list;
        String h = host.toLowerCase();
        StringBuilder out = new StringBuilder();
        for (String entry : list.split("\n")) {
            String e = entry.trim().toLowerCase();
            if (e.isEmpty() || e.equals(h)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(e);
        }
        return out.toString();
    }
}
