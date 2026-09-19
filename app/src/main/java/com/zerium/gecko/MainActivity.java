package com.zerium.gecko;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Zerium G — GeckoView edition. Single-activity browser on Mozilla's Gecko
 * engine: engine-level ad/tracker blocking through a built-in WebExtension
 * (webRequest + cosmetic CSS), strict Enhanced Tracking Protection, true
 * private sessions, and per-session JavaScript / desktop-site switches.
 */
public class MainActivity extends AppCompatActivity {

    static final String HOME_URL = "about:home";
    private static final String EXTENSION_LOCATION = "resource://android/assets/extension/";
    private static final String EXTENSION_ID = "shield@zerium.g";
    private static final String NATIVE_APP = "browser";
    private static final int MAX_RESTORED_TABS = 10;

    private static final int MENU_NEW_TAB = 1;
    private static final int MENU_NEW_INCOGNITO = 2;
    private static final int MENU_BOOKMARK_ADD = 3;
    private static final int MENU_BOOKMARKS = 4;
    private static final int MENU_HISTORY = 5;
    private static final int MENU_FIND = 6;
    private static final int MENU_DESKTOP = 7;
    private static final int MENU_JS = 8;
    private static final int MENU_TRANSLATE = 9;
    private static final int MENU_SHARE = 10;
    private static final int MENU_ALLOW_SITE = 11;
    private static final int MENU_BLOCK_INFO = 12;
    private static final int MENU_SETTINGS = 13;
    private static final int MENU_EXIT = 14;

    private Prefs prefs;
    private GeckoRuntime runtime;
    private WebExtension shield;
    private WebExtension.Port shieldPort;
    private BookmarksDB bookmarks;
    private HistoryDB history;

    private FrameLayout webContainer;
    private EditText omnibox;
    private ImageButton btnSecurity, btnRefresh, btnBack, btnForward, btnHome;
    private TextView btnTabs;
    private ProgressBar progress;
    private LinearLayout topBar, bottomBar, findBar;
    private EditText findInput;
    private TextView findCount;
    private View tabSwitcher;
    private RecyclerView tabsGrid;
    private TabsAdapter tabsAdapter;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private GeckoSession fullscreenSession;

    private final TabManager tabs = new TabManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        bookmarks = new BookmarksDB(this);
        history = new HistoryDB(this);

        webContainer = findViewById(R.id.webContainer);
        omnibox = findViewById(R.id.omnibox);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnTabs = findViewById(R.id.btnTabs);
        progress = findViewById(R.id.progress);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        findBar = findViewById(R.id.findBar);
        findInput = findViewById(R.id.findInput);
        findCount = findViewById(R.id.findCount);
        tabSwitcher = findViewById(R.id.tabSwitcher);
        tabsGrid = findViewById(R.id.tabsGrid);

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(0xFF000000);
        addContentView(fullscreenContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.GONE);

        createRuntime();

        btnSecurity.setOnClickListener(v -> showBlockInfo());
        btnRefresh.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && !isStartPage(t)) t.session.reload();
        });
        btnBack.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && t.canGoBack) t.session.goBack();
        });
        btnForward.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && t.canGoForward) t.session.goForward();
        });
        btnHome.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null) loadInTab(t, HOME_URL);
        });
        btnTabs.setOnClickListener(v -> {
            if (tabSwitcher.getVisibility() == View.VISIBLE) hideTabSwitcher();
            else showTabSwitcher();
        });
        findViewById(R.id.btnMenuTop).setOnClickListener(this::showMenu);
        findViewById(R.id.btnMenuBottom).setOnClickListener(this::showMenu);

        findViewById(R.id.btnFindNext).setOnClickListener(v -> findMore(true));
        findViewById(R.id.btnFindPrev).setOnClickListener(v -> findMore(false));
        findViewById(R.id.btnFindClose).setOnClickListener(v -> hideFindBar());
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            findMore(true);
            return true;
        });
        findInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                Tab t = tabs.currentTab();
                if (t == null) return;
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    t.session.getFinder().clear();
                    findCount.setText("0/0");
                } else {
                    t.session.getFinder().find(q, 0).then(result -> {
                        updateFindCount(result);
                        return null;
                    });
                }
            }
        });

        omnibox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                navigateOmnibox();
                return true;
            }
            return false;
        });
        omnibox.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                omnibox.post(() -> omnibox.selectAll());
            } else {
                updateChrome(tabs.currentTab());
                hideKeyboard();
            }
        });

        tabsGrid.setLayoutManager(new GridLayoutManager(this, 2));
        tabsAdapter = new TabsAdapter(tabs.tabs(), tabs, new TabsAdapter.Listener() {
            @Override
            public void onOpen(Tab tab) {
                hideTabSwitcher();
                switchTab(tab);
            }

            @Override
            public void onClose(Tab tab) {
                closeTab(tab);
            }
        });
        tabsGrid.setAdapter(tabsAdapter);
        findViewById(R.id.btnNewTab).setOnClickListener(v -> {
            hideTabSwitcher();
            openTab(null, false);
        });
        findViewById(R.id.btnNewIncognito).setOnClickListener(v -> {
            hideTabSwitcher();
            openTab(null, true);
        });
        findViewById(R.id.btnSwitcherClose).setOnClickListener(v -> hideTabSwitcher());
        findViewById(R.id.btnCloseAllTabs).setOnClickListener(v -> closeAllTabs());

        restoreSession();
        handleIntent(getIntent());
        pushShieldConfig();
    }

    // ---------- Runtime + built-in blocking extension ----------

    private void createRuntime() {
        @GeckoRuntimeSettings.ColorScheme int scheme = colorSchemeConstant();
        runtime = GeckoRuntime.create(this, new GeckoRuntimeSettings.Builder()
                .contentBlocking(new org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                        .antiTracking(org.mozilla.geckoview.ContentBlocking.AntiTracking.STRICT)
                        .enhancedTrackingProtectionLevel(
                                org.mozilla.geckoview.ContentBlocking.EtpLevel.STRICT)
                        .build())
                .preferredColorScheme(scheme)
                .aboutConfigEnabled(false)
                .debugLogging(false)
                .build());
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .then(ext -> {
                    shield = ext;
                    shield.setMessageDelegate(new WebExtension.MessageDelegate() {
                        @Override
                        public GeckoResult<Object> onMessage(@NonNull String nativeApp,
                                                             @NonNull Object message,
                                                             @NonNull WebExtension.MessageSender sender) {
                            return null;
                        }

                        @Override
                        public void onConnect(@NonNull WebExtension.Port port) {
                            shieldPort = port;
                            port.setDelegate(new WebExtension.PortDelegate() {
                                @Override
                                public void onPortMessage(@NonNull Object message,
                                                          @NonNull WebExtension.Port p) {
                                    handleShieldMessage(message);
                                }

                                @Override
                                public void onDisconnect(@NonNull WebExtension.Port p) {
                                    if (p == shieldPort) shieldPort = null;
                                }
                            });
                            pushShieldConfig();
                        }
                    }, NATIVE_APP);
                    return null;
                });
    }

    private @GeckoRuntimeSettings.ColorScheme int colorSchemeConstant() {
        switch (prefs.colorScheme()) {
            case 1: return GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;
            case 2: return GeckoRuntimeSettings.COLOR_SCHEME_DARK;
            default: return GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM;
        }
    }

    /** Pushes blocking on/off + the site allowlist into the extension. */
    private void pushShieldConfig() {
        if (shieldPort == null) return;
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("type", "config");
            cfg.put("enabled", prefs.blockAds());
            JSONArray allow = new JSONArray();
            for (String h : prefs.allowlist().split("\n")) {
                if (!h.trim().isEmpty()) allow.put(h.trim().toLowerCase());
            }
            cfg.put("allowlist", allow);
            shieldPort.postMessage(cfg);
        } catch (Exception ignored) {}
    }

    private void handleShieldMessage(Object message) {
        if (!(message instanceof JSONObject)) return;
        JSONObject o = (JSONObject) message;
        if (!"count".equals(o.optString("type"))) return;
        long n = o.optLong("blocked", 0);
        Tab t = tabs.currentTab();
        if (t != null) t.blockedOnPage += n;
        prefs.addTotalBlocked(n);
    }

    /** Adds the current host to the blocking allowlist and pushes it live. */
    private void allowCurrentSite() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        String host = Utils.hostOf(t.url);
        if (host == null || host.isEmpty()) return;
        String cur = prefs.allowlist();
        if (Utils.siteListContains(cur, host)) {
            toast(R.string.site_already_allowed);
            return;
        }
        prefs.setAllowlist(Utils.siteListAdd(cur, host));
        pushShieldConfig();
        toast(getString(R.string.site_allowed, host));
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            openTab(intent.getDataString(), false);
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                String url = Utils.smartUrl(text, prefs);
                if (url != null) openTab(url, false);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    // ---------- Tab lifecycle ----------

    private void openTab(String url, boolean incognito) {
        GeckoSessionSettings.Builder sb = new GeckoSessionSettings.Builder()
                .usePrivateMode(incognito)
                .useTrackingProtection(true)
                .allowJavascript(prefs.javascriptEnabled());
        GeckoSession session = new GeckoSession(sb.build());
        GeckoView view = new GeckoView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        webContainer.addView(view);
        session.open(runtime);
        view.setSession(session);

        Tab tab = tabs.add(new Tab(tabs.count() + System.identityHashCode(session), session, view, incognito));
        setupSession(tab);
        loadInTab(tab, (url == null || url.isEmpty()) ? HOME_URL : url);
        showCurrentWebView();
        updateChrome(tab);
        tabsAdapter.notifyDataSetChanged();
    }

    private void switchTab(Tab tab) {
        int idx = tabs.tabs().indexOf(tab);
        if (idx < 0) return;
        hideFindBar();
        tabs.setCurrent(idx);
        showCurrentWebView();
        updateChrome(tab);
        tabsAdapter.notifyDataSetChanged();
    }

    private void showCurrentWebView() {
        for (int i = 0; i < tabs.tabs().size(); i++) {
            Tab t = tabs.tabs().get(i);
            t.view.setVisibility(i == tabs.current() ? View.VISIBLE : View.GONE);
        }
    }

    private void closeTab(Tab tab) {
        int idx = tabs.tabs().indexOf(tab);
        if (idx < 0) return;
        webContainer.removeView(tab.view);
        tabs.remove(tab);
        tab.destroy();
        if (tabs.count() == 0) {
            openTab(null, false);
            tabsAdapter.notifyDataSetChanged();
            return;
        }
        showCurrentWebView();
        updateChrome(tabs.currentTab());
        tabsAdapter.notifyDataSetChanged();
    }

    private void closeAllTabs() {
        while (tabs.count() > 0) {
            Tab t = tabs.tabs().get(0);
            webContainer.removeView(t.view);
            tabs.remove(t);
            t.destroy();
        }
        hideTabSwitcher();
        openTab(null, false);
    }

    private void cycleTab(int dir) {
        int n = tabs.count();
        if (n < 2) return;
        int idx = ((tabs.current() + dir) % n + n) % n;
        switchTab(tabs.tabs().get(idx));
    }

    // ---------- Session wiring ----------

    private void setupSession(Tab tab) {
        GeckoSession session = tab.session;

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession s, @NonNull String url) {
                tab.url = url;
                tab.blockedOnPage = 0;
                if (tabs.currentTab() == tab) updateChrome(tab);
            }

            @Override
            public void onPageStop(@NonNull GeckoSession s, boolean success) {
                if (tabs.currentTab() == tab) {
                    updateChrome(tab);
                    capturePreview(tab);
                }
                tabsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession s, int newProgress) {
                if (tabs.currentTab() == tab) {
                    progress.setProgress(newProgress);
                    progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onSecurityChange(@NonNull GeckoSession s,
                                         @NonNull GeckoSession.ProgressDelegate.SecurityInformation info) {
                tab.secure = info.isSecure;
                if (tabs.currentTab() == tab) updateChrome(tab);
            }

            @Override
            public void onSessionStateChange(@NonNull GeckoSession s,
                                             @NonNull GeckoSession.SessionState state) {
                tab.state = state;
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void onLocationChange(@NonNull GeckoSession s, String url,
                                         List permissions,
                                         Boolean hasUserGesture) {
                if (url != null && !url.isEmpty()) {
                    boolean changed = !url.equals(tab.url);
                    tab.url = url;
                    if (changed && !tab.incognito && url.startsWith("http")) {
                        history.add(url, tab.title);
                    }
                }
                if (tabs.currentTab() == tab) updateChrome(tab);
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession s, boolean canGoBack) {
                tab.canGoBack = canGoBack;
                if (tabs.currentTab() == tab) updateChrome(tab);
            }

            @Override
            public void onCanGoForward(@NonNull GeckoSession s, boolean canGoForward) {
                tab.canGoForward = canGoForward;
                if (tabs.currentTab() == tab) updateChrome(tab);
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession s,
                                                          @NonNull GeckoSession.NavigationDelegate.LoadRequest request) {
                // External schemes leave the browser through an intent.
                Uri uri = Uri.parse(request.uri);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (scheme.equals("http") || scheme.equals("https")
                        || scheme.equals("data") || scheme.equals("about")
                        || scheme.equals("blob")) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
                if (scheme.equals("intent") || scheme.equals("market")) {
                    try {
                        startActivity(Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME));
                    } catch (Exception e) {
                        toast(R.string.no_app_for_link);
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    toast(R.string.no_app_for_link);
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY);
            }

            @Override
            @NonNull
            public GeckoResult<String> onLoadError(@NonNull GeckoSession s, String uri,
                                                   @NonNull WebRequestError error) {
                if (uri == null || uri.isEmpty()) return GeckoResult.fromValue(null);
                String page = errorHtml(uri, error.category, error.code);
                String dataUri = GeckoSession.Loader.createDataUri(
                        page.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html");
                return GeckoResult.fromValue(dataUri);
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(@NonNull GeckoSession s, String title) {
                tab.title = title == null ? "" : title;
                if (tabs.currentTab() == tab) updateChrome(tab);
                tabsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFullScreen(@NonNull GeckoSession s, boolean fullScreen) {
                if (fullScreen) enterFullScreen(s);
                else exitFullScreen();
            }

            @Override
            public void onContextMenu(@NonNull GeckoSession s, int screenX, int screenY,
                                      @NonNull GeckoSession.ContentDelegate.ContextElement element) {
                showContextMenu(tab, element);
            }

            @Override
            public void onExternalResponse(@NonNull GeckoSession s,
                                           @NonNull org.mozilla.geckoview.WebResponse response) {
                saveDownload(response);
            }

            @Override
            public void onCrash(@NonNull GeckoSession s) {
                toast(R.string.session_crashed);
                s.open(runtime);
                s.loadUri(HOME_URL.equals(tab.url) || tab.url == null || tab.url.isEmpty()
                        ? HOME_URL : tab.url);
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public void onAndroidPermissionsRequest(@NonNull GeckoSession s, @NonNull String[] permissions,
                                                    @NonNull GeckoSession.PermissionDelegate.Callback callback) {
                java.util.ArrayList<String> needed = new java.util.ArrayList<>();
                for (String p : permissions) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, p)
                            != PackageManager.PERMISSION_GRANTED) needed.add(p);
                }
                if (needed.isEmpty()) {
                    callback.grant();
                } else {
                    callback.reject();
                }
            }

            @Override
            public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession s,
                                                                   @NonNull GeckoSession.PermissionDelegate.ContentPermission perm) {
                // Privacy-first default: website content permissions (location,
                // camera, mic, notifications, ...) are denied. Documented in
                // docs/PRIVACY.md.
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
            }
        });

        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onAlertPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.AlertPrompt prompt) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.alert_dialog)
                        .setMessage(prompt.title == null ? "" : prompt.title)
                        .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                        .setOnDismissListener(d -> prompt.dismiss())
                        .show();
                return GeckoResult.fromValue(prompt.dismiss());
            }
        });
    }

    private String errorHtml(String url, int category, int code) {
        String safe = url.replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:system-ui,sans-serif;background:#f6f7fb;color:#1b1b1f;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}"
                + "main{text-align:center;padding:24px;max-width:420px}"
                + "h1{font-size:22px;margin-bottom:8px}p{color:#5f5f6b;font-size:14px;line-height:1.5}"
                + "code{font-size:12px;color:#8a8a94}</style></head>"
                + "<body><main><h1>This page can&#8217;t be loaded</h1>"
                + "<p>" + safe + "</p>"
                + "<p>The connection or the server failed. Zerium G never bypasses "
                + "certificate errors silently.</p>"
                + "<code>error " + code + "</code></main></body></html>";
    }

    // ---------- Page tools ----------

    private void loadInTab(Tab tab, String url) {
        if (tab == null || url == null) return;
        if (HOME_URL.equals(url)) {
            tab.url = HOME_URL;
            tab.title = getString(R.string.start_page);
            tab.session.load(new GeckoSession.Loader().data(startPageHtml(), "text/html"));
            updateChrome(tab);
            return;
        }
        tab.session.loadUri(url);
    }

    /** Minimal generated start page (same spirit as the WebView edition). */
    private String startPageHtml() {
        String q = "";
        String action = Utils.engine(prefs).query;
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:system-ui,sans-serif;background:#f6f7fb;color:#1b1b1f;"
                + "display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "min-height:100vh;margin:0;padding:24px}"
                + ".logo{font-size:40px;font-weight:800;letter-spacing:-1.5px;background:"
                + "linear-gradient(135deg,#4355b9,#7c9cff);-webkit-background-clip:text;"
                + "background-clip:text;color:transparent}"
                + ".tag{color:#5f5f6b;font-size:13px;margin:8px 0 30px}"
                + ".search{display:flex;align-items:center;background:#fff;border:1px solid #e2e2ea;"
                + "border-radius:28px;padding:4px 4px 4px 18px;width:100%;max-width:580px;"
                + "box-shadow:0 8px 28px rgba(20,25,60,.07)}"
                + "input{flex:1;min-width:0;padding:13px 12px;font-size:16px;border:0;outline:none;"
                + "background:transparent;color:#1b1b1f}"
                + "button{flex:none;border:0;border-radius:22px;padding:11px 22px;font-size:14px;"
                + "font-weight:600;color:#fff;background:#4355b9}"
                + "@media (prefers-color-scheme:dark){body{background:#0e1016;color:#e4e2e6}"
                + ".search{background:#171a23;border-color:#2a2d38}input{color:#e4e2e6}}"
                + "</style></head><body>"
                + "<div class='logo'>Zerium&nbsp;G</div>"
                + "<div class='tag'>Gecko engine &middot; engine-level blocking &middot; open source</div>"
                + "<form onsubmit='var q=document.getElementById(\"q\").value.trim();"
                + "if(q){var t=\"" + action + "\";location.href=t.replace(\"%s\",encodeURIComponent(q));}"
                + "return false'>"
                + "<div class='search'><input id='q' type='search' placeholder='"
                + getString(R.string.search_hint) + "' autofocus><button type='submit'>Go</button>"
                + "</div></form></body></html>";
    }

    /** Per-session desktop-site toggle (UA + viewport, Gecko-native). */
    private boolean isDesktop(Tab t) {
        return t.session.getSettings().getUserAgentMode()
                == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP;
    }

    private void toggleDesktop() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        boolean desktop = !isDesktop(t);
        t.session.getSettings().setUserAgentMode(desktop
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        t.session.getSettings().setViewportMode(desktop
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        t.session.reload();
    }

    /** Per-session JavaScript toggle. */
    private void toggleJavascript() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        boolean js = !t.session.getSettings().getAllowJavascript();
        t.session.getSettings().setAllowJavascript(js);
        t.session.reload();
        toast(js ? R.string.js_on : R.string.js_off);
    }

    private void translatePage() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        Uri u = Uri.parse(t.url);
        String host = u.getHost();
        if (host == null || host.isEmpty()) return;
        if (host.endsWith(".translate.goog") || host.equals("translate.goog")) return;
        String tl = java.util.Locale.getDefault().getLanguage();
        if (tl == null || tl.isEmpty()) tl = "en";
        Uri out = u.buildUpon()
                .scheme("https")
                .authority(host.replace('.', '-') + ".translate.goog")
                .appendQueryParameter("_x_tr_sl", "auto")
                .appendQueryParameter("_x_tr_tl", tl)
                .appendQueryParameter("_x_tr_hl", tl)
                .appendQueryParameter("_x_tr_pto", "ajax,elem")
                .build();
        loadInTab(t, out.toString());
        toast(R.string.translating);
    }

    // ---------- Find bar ----------

    private void showFindBar() {
        if (tabs.currentTab() == null) return;
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findCount.setText("0/0");
        findInput.requestFocus();
        findInput.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void findMore(boolean forward) {
        Tab t = tabs.currentTab();
        String q = findInput.getText().toString().trim();
        if (t == null || q.isEmpty()) return;
        // The Finder continues from the current match on repeated calls.
        t.session.getFinder().find(q, 0).then(result -> {
            updateFindCount(result);
            return null;
        });
    }

    private void updateFindCount(GeckoSession.FinderResult result) {
        if (result == null) return;
        // GeckoView reports whether the page has matches and the current match
        // ordinal; there is no total, so the counter shows the match index.
        if (result.found) {
            findCount.setText(String.valueOf((int) result.current + 1));
        } else {
            findCount.setText("0");
        }
    }

    private void hideFindBar() {
        if (findBar.getVisibility() != View.VISIBLE) return;
        findBar.setVisibility(View.GONE);
        Tab t = tabs.currentTab();
        if (t != null) t.session.getFinder().clear();
        hideKeyboard();
    }

    // ---------- Chrome (UI) ----------

    private void updateChrome(Tab tab) {
        if (tab == null) return;
        if (isStartPage(tab)) {
            omnibox.setText("");
            omnibox.setHint(R.string.search_hint);
            btnSecurity.setImageResource(R.drawable.ic_home);
        } else {
            omnibox.setText(displayUrl(tab.url));
            omnibox.setHint(R.string.search_hint);
            btnSecurity.setImageResource(tab.secure ? R.drawable.ic_lock : R.drawable.ic_globe);
        }
        btnBack.setEnabled(tab.canGoBack);
        btnForward.setEnabled(tab.canGoForward);
        btnBack.setAlpha(tab.canGoBack ? 1f : 0.4f);
        btnForward.setAlpha(tab.canGoForward ? 1f : 0.4f);
        int n = tabs.count();
        btnTabs.setText(n > 99 ? "99+" : String.valueOf(n));
    }

    private static String displayUrl(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://", "");
    }

    private boolean isStartPage(Tab tab) {
        return tab.url == null || tab.url.isEmpty() || tab.url.equals(HOME_URL)
                || tab.url.startsWith("data:");
    }

    private void navigateOmnibox() {
        String text = omnibox.getText().toString().trim();
        if (text.isEmpty()) return;
        hideKeyboard();
        omnibox.clearFocus();
        Tab t = tabs.currentTab();
        if (t == null) return;
        if (text.equals("about:home") || text.equalsIgnoreCase("zerium://home")) {
            loadInTab(t, HOME_URL);
            return;
        }
        String url = Utils.smartUrl(text, prefs);
        if (url != null) loadInTab(t, url);
    }

    // ---------- Menu ----------

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Tab t = tabs.currentTab();
        pm.getMenu().add(0, MENU_NEW_TAB, 0, R.string.menu_new_tab);
        pm.getMenu().add(0, MENU_NEW_INCOGNITO, 1, R.string.menu_new_incognito);
        pm.getMenu().add(0, MENU_BOOKMARK_ADD, 2, isCurrentBookmarked()
                ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark);
        pm.getMenu().add(0, MENU_BOOKMARKS, 3, R.string.menu_bookmarks);
        pm.getMenu().add(0, MENU_HISTORY, 4, R.string.menu_history);
        pm.getMenu().add(0, MENU_FIND, 5, R.string.menu_find);
        pm.getMenu().add(0, MENU_DESKTOP, 6, R.string.menu_desktop)
                .setCheckable(true).setChecked(t != null && isDesktop(t));
        pm.getMenu().add(0, MENU_JS, 7, R.string.menu_javascript)
                .setCheckable(true)
                .setChecked(t != null && t.session.getSettings().getAllowJavascript());
        pm.getMenu().add(0, MENU_TRANSLATE, 8, R.string.menu_translate)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_SHARE, 9, R.string.menu_share);
        pm.getMenu().add(0, MENU_ALLOW_SITE, 10, R.string.menu_allow_site)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_BLOCK_INFO, 11, R.string.menu_block_info);
        pm.getMenu().add(0, MENU_SETTINGS, 12, R.string.menu_settings);
        pm.getMenu().add(0, MENU_EXIT, 13, R.string.menu_exit);
        pm.setOnMenuItemClickListener(item -> {
            handleMenu(item.getItemId());
            return true;
        });
        pm.show();
    }

    private boolean isCurrentBookmarked() {
        Tab t = tabs.currentTab();
        return t != null && !isStartPage(t) && bookmarks.contains(t.url);
    }

    private void handleMenu(int id) {
        Tab t = tabs.currentTab();
        switch (id) {
            case MENU_NEW_TAB: openTab(null, false); break;
            case MENU_NEW_INCOGNITO: openTab(null, true); break;
            case MENU_BOOKMARK_ADD:
                if (t == null || isStartPage(t)) return;
                if (bookmarks.contains(t.url)) {
                    for (BookmarksDB.Entry e : bookmarks.all()) {
                        if (e.url.equals(t.url)) { bookmarks.remove(e.id); break; }
                    }
                    toast(R.string.bookmark_removed);
                } else {
                    bookmarks.add(t.url, t.title);
                    toast(R.string.bookmark_added);
                }
                break;
            case MENU_BOOKMARKS:
                startActivity(new Intent(this, BookmarksActivity.class));
                break;
            case MENU_HISTORY:
                startActivity(new Intent(this, HistoryActivity.class));
                break;
            case MENU_FIND: showFindBar(); break;
            case MENU_DESKTOP: toggleDesktop(); break;
            case MENU_JS: toggleJavascript(); break;
            case MENU_TRANSLATE: translatePage(); break;
            case MENU_SHARE:
                if (t != null && !isStartPage(t)) {
                    Intent si = new Intent(Intent.ACTION_SEND);
                    si.setType("text/plain");
                    si.putExtra(Intent.EXTRA_TEXT, t.url);
                    startActivity(Intent.createChooser(si, getString(R.string.menu_share)));
                }
                break;
            case MENU_ALLOW_SITE: allowCurrentSite(); break;
            case MENU_BLOCK_INFO: showBlockInfo(); break;
            case MENU_SETTINGS:
                startActivityForResult(new Intent(this, SettingsActivity.class), 101);
                break;
            case MENU_EXIT: finishAffinity(); break;
        }
    }

    private void showBlockInfo() {
        Tab t = tabs.currentTab();
        new AlertDialog.Builder(this)
                .setTitle(R.string.block_info_title)
                .setMessage(getString(R.string.block_info_body,
                        t == null ? 0 : t.blockedOnPage,
                        prefs.totalBlocked()))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ---------- Context menu ----------

    private void showContextMenu(Tab tab, GeckoSession.ContentDelegate.ContextElement element) {
        final String link = element.linkUri;
        final String image = element.type
                == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                ? element.srcUri : null;
        if ((link == null || link.isEmpty()) && (image == null || image.isEmpty())) return;
        final String target = link != null && !link.isEmpty() ? link : image;
        boolean http = target.startsWith("http://") || target.startsWith("https://");

        List<String> items = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        if (http) {
            items.add(getString(R.string.ctx_open_new_tab));
            actions.add(A_OPEN);
            if (image != null) {
                items.add(getString(R.string.ctx_download_image));
                actions.add(A_DOWNLOAD);
            }
        }
        items.add(getString(R.string.ctx_copy_link));
        actions.add(A_COPY);
        items.add(getString(R.string.ctx_share_link));
        actions.add(A_SHARE);

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    switch (actions.get(which)) {
                        case A_OPEN: openTab(target, false); break;
                        case A_DOWNLOAD: downloadViaShield(target); break;
                        case A_COPY: {
                            ClipboardManager cm = (ClipboardManager)
                                    getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("link", target));
                                toast(R.string.copied);
                            }
                            break;
                        }
                        case A_SHARE: {
                            Intent si = new Intent(Intent.ACTION_SEND);
                            si.setType("text/plain");
                            si.putExtra(Intent.EXTRA_TEXT, target);
                            startActivity(Intent.createChooser(si, getString(R.string.menu_share)));
                            break;
                        }
                    }
                })
                .show();
    }

    private static final int A_OPEN = 1;
    private static final int A_DOWNLOAD = 2;
    private static final int A_COPY = 3;
    private static final int A_SHARE = 4;

    /** Asks the shield extension to download a URL (browser.downloads API). */
    private void downloadViaShield(String url) {
        try {
            if (shieldPort != null) {
                JSONObject msg = new JSONObject();
                msg.put("type", "download");
                msg.put("url", url);
                shieldPort.postMessage(msg);
                toast(R.string.download_started);
                return;
            }
        } catch (Exception ignored) {}
        toast(R.string.download_failed_generic);
    }

    /**
     * Saves a Content-Disposition download. API 29+ uses the public Downloads
     * collection; older devices fall back to the app's external files dir
     * (no storage permission required) — stated in the save toast.
     */
    private void saveDownload(org.mozilla.geckoview.WebResponse response) {
        try {
            String name = Utils.fileNameFromUrl(response.uri);
            String ext = name.contains(".")
                    ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) {
                mime = "application/octet-stream";
            }
            long written = 0;
            Uri item = null;
            java.io.File legacy = null;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                item = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (item == null) {
                    toast(R.string.download_failed_generic);
                    return;
                }
            } else {
                legacy = new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                if (legacy.exists()) {
                    name = System.currentTimeMillis() + "_" + name;
                    legacy = new java.io.File(legacy.getParentFile(), name);
                }
            }
            try (OutputStream os = item != null
                            ? getContentResolver().openOutputStream(item)
                            : new java.io.FileOutputStream(legacy);
                 java.io.InputStream is = response.body) {
                if (os == null || is == null) throw new IllegalStateException("stream");
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    written += n;
                }
            }
            if (item != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(item, done, null, null);
            }
            toast(getString(R.string.download_saved,
                    legacy != null ? legacy.getAbsolutePath() : name));
        } catch (Exception e) {
            toast(R.string.download_failed_generic);
        }
    }

    // ---------- Fullscreen ----------

    private void enterFullScreen(GeckoSession session) {
        if (fullscreenView != null) return;
        fullscreenSession = session;
        fullscreenContainer.setVisibility(View.VISIBLE);
        webContainer.setVisibility(View.GONE);
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void exitFullScreen() {
        if (fullscreenSession == null) return;
        fullscreenContainer.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (fullscreenSession != null) fullscreenSession.exitFullScreen();
        fullscreenSession = null;
    }

    // ---------- Tab switcher ----------

    private void showTabSwitcher() {
        hideFindBar();
        Tab current = tabs.currentTab();
        if (current != null && !current.incognito && !isStartPage(current)) capturePreview(current);
        tabsAdapter.notifyDataSetChanged();
        tabSwitcher.setVisibility(View.VISIBLE);
        hideKeyboard();
        omnibox.clearFocus();
    }

    /** Live preview via the engine compositor (never for private tabs). */
    private void capturePreview(Tab tab) {
        if (tab.incognito) return;
        GeckoView view = tab.view;
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
        view.capturePixels().then(bmp -> {
            if (bmp != null) {
                float scale = Math.min(1f, 320f / view.getWidth());
                Bitmap scaled = Bitmap.createScaledBitmap(bmp,
                        Math.max(1, (int) (view.getWidth() * scale)),
                        Math.max(1, (int) (view.getHeight() * scale)), true);
                if (tab.preview != null && tab.preview != scaled) tab.preview.recycle();
                tab.preview = scaled;
                if (tabSwitcher.getVisibility() == View.VISIBLE) tabsAdapter.notifyDataSetChanged();
            }
            return null;
        });
    }

    private void hideTabSwitcher() {
        tabSwitcher.setVisibility(View.GONE);
    }

    // ---------- Session persistence ----------

    private void restoreSession() {
        String saved = prefs.savedTabs();
        int index = prefs.savedTabIndex();
        int opened = 0;
        if (saved != null && !saved.isEmpty()) {
            for (String u : saved.split("\\|\\|")) {
                if (u == null || u.trim().isEmpty()) continue;
                if (opened >= MAX_RESTORED_TABS) break;
                openTab(u.trim(), false);
                opened++;
            }
        }
        if (opened == 0) {
            openTab(null, false);
        } else {
            int target = Math.max(0, Math.min(index, opened - 1));
            tabs.setCurrent(target);
            showCurrentWebView();
            updateChrome(tabs.currentTab());
        }
    }

    private void saveSession() {
        StringBuilder sb = new StringBuilder();
        int current = 0;
        int i = 0;
        for (Tab t : tabs.tabs()) {
            if (t.incognito) continue;
            String u = t.url == null ? "" : t.url;
            if (u.startsWith("data:")) u = HOME_URL;
            if (sb.length() > 0) sb.append("||");
            if (i == tabs.current()) current = i;
            sb.append(u);
            i++;
            if (i >= MAX_RESTORED_TABS) break;
        }
        prefs.savedTabs(sb.toString());
        prefs.savedTabIndex(current);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed: page color scheme applies to the runtime,
        // new sessions pick up the global JS default, the shield gets the
        // current allowlist.
        if (runtime != null) {
            runtime.getSettings().setPreferredColorScheme(colorSchemeConstant());
        }
        pushShieldConfig();
        Tab t = tabs.currentTab();
        if (t != null) updateChrome(t);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            if (runtime != null) {
                runtime.getSettings().setPreferredColorScheme(colorSchemeConstant());
            }
            pushShieldConfig();
            updateChrome(tabs.currentTab());
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreenSession != null) {
            exitFullScreen();
            return;
        }
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
            return;
        }
        if (tabSwitcher.getVisibility() == View.VISIBLE) {
            hideTabSwitcher();
            return;
        }
        Tab t = tabs.currentTab();
        if (t != null && t.canGoBack) {
            t.session.goBack();
        } else if (t != null && !isStartPage(t)) {
            loadInTab(t, HOME_URL);
        } else {
            moveTaskToBack(true);
        }
    }

    // ---------- Misc ----------

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && omnibox != null) {
            imm.hideSoftInputFromWindow(omnibox.getWindowToken(), 0);
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
