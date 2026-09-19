package com.zerium.gecko;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small shared helpers: search engines (built-in + custom) and URL parsing. */
public class Utils {

    public static final String[] ENGINE_NAMES = {
            "DuckDuckGo", "Startpage", "Brave Search", "Google", "Bing", "Wikipedia"
    };
    public static final String[] ENGINE_QUERIES = {
            "https://duckduckgo.com/?q=%s",
            "https://www.startpage.com/sp/search?query=%s",
            "https://search.brave.com/search?q=%s",
            "https://www.google.com/search?q=%s",
            "https://www.bing.com/search?q=%s",
            "https://en.wikipedia.org/w/index.php?search=%s"
    };
    /** Number of built-in engines; custom engine i is engine index BUILTIN_ENGINES + i. */
    public static final int BUILTIN_ENGINES = ENGINE_NAMES.length;

    /** One selectable search engine (built-in or user-defined). */
    public static class Engine {
        public final String name;
        public final String query;
        public final boolean custom;

        public Engine(String name, String query, boolean custom) {
            this.name = name;
            this.query = query;
            this.custom = custom;
        }
    }

    /** All engines: built-ins first, then the user's custom ones. Never null. */
    public static List<Engine> allEngines(Prefs prefs) {
        List<Engine> out = new ArrayList<>();
        for (int i = 0; i < BUILTIN_ENGINES; i++) {
            out.add(new Engine(ENGINE_NAMES[i], ENGINE_QUERIES[i], false));
        }
        if (prefs != null) {
            out.addAll(parseCustomEngines(prefs.customEngines()));
        }
        return out;
    }

    /** Engine for an index, clamped into range. Never null. */
    public static Engine engineAt(Prefs prefs, int index) {
        List<Engine> all = allEngines(prefs);
        return all.get(Math.max(0, Math.min(index, all.size() - 1)));
    }

    /** Parses the stored custom-engine JSON; invalid entries are skipped silently. */
    public static List<Engine> parseCustomEngines(String raw) {
        List<Engine> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                String url = o.optString("url", "").trim();
                if (validCustomEngine(name, url)) out.add(new Engine(name, url, true));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static boolean validCustomEngine(String name, String url) {
        return !name.isEmpty() && !url.isEmpty()
                && (url.startsWith("http://") || url.startsWith("https://"))
                && url.contains("%s");
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
        return engineAt(prefs, prefs.searchEngine()).query.replace("%s", q);
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
