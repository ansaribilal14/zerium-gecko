package com.zerium.gecko;

import android.content.Context;
import android.content.SharedPreferences;

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

    /** Search engine index into Utils.engines(). */
    public int searchEngine() { return sp.getInt("search_engine", 0); }
    public void searchEngine(int v) { sp.edit().putInt("search_engine", v).apply(); }

    /** Sites exempt from engine-level blocking (newline-separated hosts). */
    public String allowlist() { return sp.getString("allowlist", ""); }
    public void setAllowlist(String v) { sp.edit().putString("allowlist", v == null ? "" : v).apply(); }

    // Session restore
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
}
