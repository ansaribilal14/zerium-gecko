package com.zerium.gecko;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Pure-JVM core of the native omnibox suggestions (no android.* imports).
 *
 * <p>Three suggestion kinds feed the dropdown under the address field:</p>
 * <ul>
 *   <li><b>Current page</b> — the focused tab's title and URL, always first
 *       while the omnibox has focus (tapping it keeps the page).</li>
 *   <li><b>Local history</b> — title + URL matches from the history
 *       database (parameterized, deduplicated; see {@link HistoryDB#suggest}).
 *       Selecting one opens the exact stored URL.</li>
 *   <li><b>Remote search suggestions</b> — from the selected engine's
 *       suggest endpoint (Google included). Only when the user enabled the
 *       Search Suggestions setting, which discloses that typed text is sent
 *       to the provider. Custom engines have no known endpoint → local only.</li>
 * </ul>
 *
 * <p>Start-page typeahead (in-page JavaScript) is a separate surface and
 * stays independent of this engine.</p>
 */
public final class SuggestEngine {

    public static final int MAX_SUGGESTIONS = 8;
    public static final int TYPE_URL = 0;
    public static final int TYPE_SEARCH = 1;
    public static final int TYPE_HISTORY = 2;
    public static final int TYPE_CURRENT = 3;

    /** One omnibox suggestion row. */
    public static final class Suggestion {
        public final int type;
        /** Display line (page title, suggestion text or action label). */
        public final String title;
        /**
         * Payload: full URL for TYPE_URL / TYPE_HISTORY / TYPE_CURRENT,
         * raw query text for TYPE_SEARCH.
         */
        public final String payload;

        public Suggestion(int type, String title, String payload) {
            this.type = type;
            this.title = title == null ? "" : title;
            this.payload = payload == null ? "" : payload;
        }
    }

    private SuggestEngine() {}

    // ---------- Remote endpoints ----------

    /**
     * Suggest endpoint for a built-in engine, with the user's query
     * substituted. Returns null for custom engines (no known endpoint) and
     * for blank queries.
     */
    public static String remoteUrl(Utils.Engine engine, String query) {
        if (engine == null || engine.custom || query == null) return null;
        String q = query.trim();
        if (q.isEmpty()) return null;
        String e = urlEncode(q);
        switch (engine.name) {
            case "DuckDuckGo":
                return "https://duckduckgo.com/ac/?q=" + e + "&type=list";
            case "Startpage":
                return "https://www.startpage.com/do/suggest?query=" + e + "&format=json";
            case "Brave Search":
                return "https://search.brave.com/api/suggest?q=" + e;
            case "Google":
                return "https://suggestqueries.google.com/complete/search?client=firefox&q=" + e;
            case "Bing":
                return "https://api.bing.com/osjson.aspx?query=" + e;
            case "Wikipedia":
                return "https://en.wikipedia.org/w/api.php?action=opensearch&search="
                        + e + "&limit=" + MAX_SUGGESTIONS + "&namespace=0&format=json";
            default:
                return null;
        }
    }

    /**
     * Parses a suggest response body across the endpoint shapes the
     * built-in engines use: Firefox/OpenSearch arrays
     * ({@code ["q",["a","b"]]}), bare string arrays, arrays of objects
     * carrying text-like fields, and objects with a suggestions-like
     * array ({@code {"results":[{"text":…}]}}). Never throws; broken
     * bodies yield an empty list.
     */
    public static List<String> parseResponse(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) return out;
        String b = body.trim();
        if (b.isEmpty()) return out;
        try {
            if (b.startsWith("[")) {
                collect(new JSONArray(b), out);
            } else if (b.startsWith("{")) {
                JSONObject o = new JSONObject(b);
                String[] keys = {"results", "suggestions", "1"};
                for (String key : keys) {
                    if (o.has(key) && (o.opt(key) instanceof JSONArray)) {
                        collect(o.optJSONArray(key), out);
                        if (!out.isEmpty()) break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void collect(JSONArray arr, List<String> out) {
        if (arr == null) return;
        // Firefox/OpenSearch shape: ["query-echo", ["a", "b", …]] — the
        // first element only echoes the query, the suggestions live in
        // the first nested array.
        if (arr.length() >= 2 && arr.opt(0) instanceof String
                && arr.opt(1) instanceof JSONArray) {
            collect(arr.optJSONArray(1), out);
            return;
        }
        for (int i = 0; i < arr.length() && out.size() < MAX_SUGGESTIONS * 2; i++) {
            Object v = arr.opt(i);
            if (v instanceof String) {
                addSuggestion((String) v, out);
            } else if (v instanceof JSONArray) {
                if (out.isEmpty()) collect((JSONArray) v, out);
            } else if (v instanceof JSONObject) {
                JSONObject o = (JSONObject) v;
                String text = o.optString("text", o.optString("phrase",
                        o.optString("q", o.optString("suggestion", ""))));
                addSuggestion(text, out);
            }
        }
    }

    private static void addSuggestion(String s, List<String> out) {
        if (s == null) return;
        String t = s.trim();
        if (!t.isEmpty()) out.add(t);
    }

    // ---------- Merging ----------

    /**
     * Builds the final dropdown list: current page (if any), the direct
     * URL row (when the input is a web address), the search row, local
     * history matches, then remote suggestions. Deduplicated
     * (case-insensitive), capped at {@link #MAX_SUGGESTIONS}.
     *
     * @param currentTitle/currentUrl the focused tab's identity (nullable)
     * @param input                   the text typed so far
     * @param localHistory            {title, url} pairs (already filtered)
     * @param remote                  parsed remote suggestion strings
     * @param engineName              display name for the search row
     */
    public static List<Suggestion> merge(String currentTitle, String currentUrl,
                                         String input, List<String[]> localHistory,
                                         List<String> remote, String engineName) {
        List<Suggestion> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String q = input == null ? "" : input.trim();
        if (!q.isEmpty() && currentUrl != null && !currentUrl.isEmpty()) {
            out.add(new Suggestion(TYPE_CURRENT,
                    currentTitle == null || currentTitle.isEmpty() ? currentUrl : currentTitle,
                    currentUrl));
            seen.add(key(currentUrl));
        }
        if (!q.isEmpty()) {
            // Direct URL row when the text itself is an address.
            String direct = q.contains(" ") ? null : directUrl(q);
            if (direct != null && !seen.contains(key(direct))) {
                out.add(new Suggestion(TYPE_URL, direct, direct));
                seen.add(key(direct));
            }
            String label = engineName == null || engineName.isEmpty()
                    ? "Search" : engineName;
            String searchKey = key("\u0001" + q.toLowerCase());
            if (!seen.contains(searchKey)) {
                out.add(new Suggestion(TYPE_SEARCH, label + ": " + q, q));
                seen.add(searchKey);
            }
        }
        if (localHistory != null) {
            for (String[] h : localHistory) {
                if (out.size() >= MAX_SUGGESTIONS) break;
                if (h == null || h.length < 2 || h[1] == null || h[1].isEmpty()) continue;
                String k = key(h[1]);
                if (seen.contains(k)) continue;
                seen.add(k);
                out.add(new Suggestion(TYPE_HISTORY,
                        h[0] == null || h[0].isEmpty() ? h[1] : h[0], h[1]));
            }
        }
        if (remote != null) {
            for (String r : remote) {
                if (out.size() >= MAX_SUGGESTIONS) break;
                if (r == null || r.trim().isEmpty()) continue;
                String k = key("\u0001" + r.trim().toLowerCase());
                if (seen.contains(k)) continue;
                seen.add(k);
                out.add(new Suggestion(TYPE_SEARCH, r, r));
            }
        }
        return out.size() > MAX_SUGGESTIONS
                ? new ArrayList<>(out.subList(0, MAX_SUGGESTIONS)) : out;
    }

    /** Adds https:// to bare hosts (mirrors Utils.smartUrl's URL branch). */
    public static String directUrl(String input) {
        if (input == null) return null;
        String t = input.trim();
        if (t.isEmpty() || t.contains(" ") || t.contains("about:")) return null;
        String lower = t.toLowerCase(Locale.US);
        boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://");
        boolean looksLikeHost = (t.contains(".") || lower.equals("localhost"))
                && !t.endsWith(".");
        if (!hasScheme && !looksLikeHost) return null;
        return hasScheme ? t : "https://" + t;
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
