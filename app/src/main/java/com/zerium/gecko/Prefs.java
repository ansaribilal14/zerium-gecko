package com.zerium.gecko;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Central access to all Zerium G user preferences. */
public class Prefs {
    private static final String NAME = "zerium_g_prefs";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Engine-level blocking extension toggle (applies live via native messaging). */
    public boolean blockAds() { return sp.getBoolean("block_ads", true); }
    public void blockAds(boolean v) { sp.edit().putBoolean("block_ads", v).apply(); }

    /** Global JavaScript default for NEW sessions; per-session toggle overrides live. */
    public boolean javascriptEnabled() { return sp.getBoolean("javascript", true); }
    public void javascriptEnabled(boolean v) { sp.edit().putBoolean("javascript", v).apply(); }

    /** Preferred page color scheme: 0 system, 1 light, 2 dark. */
    public int colorScheme() { return sp.getInt("color_scheme", 0); }
    public void colorScheme(int v) { sp.edit().putInt("color_scheme", v).apply(); }

    /** App theme: 0 system, 1 light, 2 dark. */
    public int appTheme() { return sp.getInt("app_theme", 0); }
    public void appTheme(int v) { sp.edit().putInt("app_theme", v).apply(); }

    /** Search engine index into Utils.allEngines(prefs). */
    public int searchEngine() { return sp.getInt("search_engine", 0); }
    public void searchEngine(int v) { sp.edit().putInt("search_engine", v).apply(); }

    /** User-defined search engines (JSON array of {name,url}). */
    public String customEngines() { return sp.getString("custom_engines", "[]"); }
    public void customEngines(String v) { sp.edit().putString("custom_engines", v == null ? "[]" : v).apply(); }

    /** Sites exempt from engine-level blocking (newline-separated hosts). */
    public String allowlist() { return sp.getString("allowlist", ""); }
    public void setAllowlist(String v) { sp.edit().putString("allowlist", v == null ? "" : v).apply(); }

    /** Pull-to-refresh gesture. */
    public boolean pullToRefresh() { return sp.getBoolean("pull_to_refresh", true); }
    public void pullToRefresh(boolean v) { sp.edit().putBoolean("pull_to_refresh", v).apply(); }

    /** Edge-swipe tab switching. */
    public boolean gestures() { return sp.getBoolean("gestures", true); }
    public void gestures(boolean v) { sp.edit().putBoolean("gestures", v).apply(); }

    /** Block popups (engine popup prompts are denied unless the site is exempt). */
    public boolean blockPopups() { return sp.getBoolean("block_popups", true); }
    public void blockPopups(boolean v) { sp.edit().putBoolean("block_popups", v).apply(); }

    /** Sites allowed to open popups (newline-separated hosts). */
    public String popupAllowed() { return sp.getString("popup_allowed", ""); }
    public void popupAllowed(String v) { sp.edit().putString("popup_allowed", v == null ? "" : v).apply(); }

    /** Auto-reject cookie banners (Gecko cookie-banner service). */
    public boolean cookieBanners() { return sp.getBoolean("cookie_banners", true); }
    public void cookieBanners(boolean v) { sp.edit().putBoolean("cookie_banners", v).apply(); }

    /** Text scaling factor applied to all page text (GeckoRuntime fontSizeFactor). */
    public float fontSizeFactor() { return sp.getFloat("font_size_factor", 1.0f); }
    public void fontSizeFactor(float v) { sp.edit().putFloat("font_size_factor", v).apply(); }

    /** Content locale override ("", or a language tag like "de"). */
    public String locale() { return sp.getString("locale", ""); }
    public void locale(String v) { sp.edit().putString("locale", v == null ? "" : v).apply(); }

    /** Content permission default: 0 = ask (dialog), 1 = deny silently. */
    public int permDefault() { return sp.getInt("perm_default", 0); }
    public void permDefault(int v) { sp.edit().putInt("perm_default", v).apply(); }

    /** Remembered content-permission decisions: JSON {host: {"<perm>": 1|-1}}. */
    public String permGrants() { return sp.getString("perm_grants", "{}"); }
    public void permGrants(String v) { sp.edit().putString("perm_grants", v == null ? "{}" : v).apply(); }

    /** Reader view text scale (1.0 = default). */
    public float readerTextScale() { return sp.getFloat("reader_text_scale", 1.0f); }
    public void readerTextScale(float v) { sp.edit().putFloat("reader_text_scale", v).apply(); }

    /** HTTPS-only mode: 0 off, 1 private tabs only (default), 2 all tabs. */
    public int httpsOnly() { return sp.getInt("https_only", 1); }
    public void httpsOnly(int v) { sp.edit().putInt("https_only", v).apply(); }

    // Session restore: JSON [{"u": url, "s": sessionState}, ...]
    public String savedTabs() { return sp.getString("saved_tabs", ""); }
    public void savedTabs(String v) { sp.edit().putString("saved_tabs", v == null ? "" : v).apply(); }
    public int savedTabIndex() { return sp.getInt("saved_tab_index", 0); }
    public void savedTabIndex(int v) { sp.edit().putInt("saved_tab_index", v).apply(); }

    // Blocked counter (fed by the extension over native messaging)
    public long totalBlocked() { return sp.getLong("total_blocked", 0L); }
    public void addTotalBlocked(long n) {
        if (n <= 0) return;
        sp.edit().putLong("total_blocked", totalBlocked() + n).apply();
    }

    // ---------- remembered content permissions ----------

    /** Returns +1 (remembered allow), -1 (remembered deny) or 0 (no memory). */
    public int rememberedPermission(String host, String permKey) {
        try {
            JSONObject all = new JSONObject(permGrants());
            JSONObject site = all.optJSONObject(host);
            if (site == null) return 0;
            return site.optInt(permKey, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Stores a remembered decision; value is +1 or -1. Private tabs never call this. */
    public void rememberPermission(String host, String permKey, int value) {
        try {
            JSONObject all = new JSONObject(permGrants());
            JSONObject site = all.optJSONObject(host);
            if (site == null) { site = new JSONObject(); all.put(host, site); }
            site.put(permKey, value);
            permGrants(all.toString());
        } catch (Exception ignored) {}
    }

    /** Clears every remembered content-permission decision. */
    public void clearPermissionMemory() {
        permGrants("{}");
    }

    /** Hosts remembered for a given permission value, for the settings summary. */
    public int permissionMemoryCount() {
        try {
            JSONObject all = new JSONObject(permGrants());
            int n = 0;
            Iterator<String> it = all.keys();
            while (it.hasNext()) { it.next(); n++; }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }
}
