package com.zerium.gecko;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.io.File;
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
    private static final int MENU_DOWNLOADS = 6;
    private static final int MENU_FIND = 7;
    private static final int MENU_READER = 8;
    private static final int MENU_DESKTOP = 9;
    private static final int MENU_JS = 10;
    private static final int MENU_TRANSLATE = 11;
    private static final int MENU_PRINT = 12;
    private static final int MENU_SAVE_PDF = 13;
    private static final int MENU_SHARE = 14;
    private static final int MENU_ALLOW_SITE = 15;
    private static final int MENU_BLOCK_INFO = 16;
    private static final int MENU_SETTINGS = 17;
    private static final int MENU_EXIT = 18;

    private Prefs prefs;
    private GeckoRuntime runtime;
    private WebExtension shield;
    private WebExtension.Port shieldPort;
    private BookmarksDB bookmarks;
    private HistoryDB history;

    private GSwipeLayout swipe;
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

    /** Pending Android runtime-permission request (gecko callback or media grant). */
    private GeckoSession.PermissionDelegate.Callback pendingAndroidCallback;
    private GeckoSession.PermissionDelegate.MediaCallback pendingMediaCallback;
    private GeckoSession.PermissionDelegate.MediaSource pendingMediaVideo;
    private GeckoSession.PermissionDelegate.MediaSource pendingMediaAudio;
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> resolvePendingPermissions(result));
    /** Pending file-picker prompt (multiple documents via ACTION_OPEN_DOCUMENT). */
    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private final ActivityResultLauncher<Intent> fileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> resolveFilePrompt(result));

    private final TabManager tabs = new TabManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        bookmarks = new BookmarksDB(this);
        history = new HistoryDB(this);

        webContainer = findViewById(R.id.webContainer);
        swipe = findViewById(R.id.swipe);
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

        // Pull-to-refresh: gated by the session's real scroll position.
        swipe.setOnRefreshListener(() -> {
            swipe.setRefreshing(false);
            Tab t = tabs.currentTab();
            if (t != null && !isStartPage(t)) t.session.reload();
        });
        swipe.setColorSchemeResources(R.color.accent);
        swipe.setProbe(new GSwipeLayout.Probe() {
            @Override
            public boolean canScrollUp() {
                Tab t = tabs.currentTab();
                return t != null && t.scrollY > 0;
            }

            @Override
            public boolean gesturesEnabled() {
                return prefs.gestures() && tabSwitcher.getVisibility() != View.VISIBLE
                        && (findViewById(R.id.findBar)).getVisibility() != View.VISIBLE;
            }

            @Override
            public void onEdgeSwipe(int dir) {
                cycleTab(dir);
            }
        });

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
        org.mozilla.geckoview.ContentBlocking.Settings.Builder cb =
                new org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                        .antiTracking(org.mozilla.geckoview.ContentBlocking.AntiTracking.STRICT)
                        .enhancedTrackingProtectionLevel(
                                org.mozilla.geckoview.ContentBlocking.EtpLevel.STRICT);
        int cbMode = prefs.cookieBanners()
                ? org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
                : org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED;
        cb.cookieBannerHandlingMode(cbMode);
        cb.cookieBannerHandlingModePrivateBrowsing(cbMode);
        runtime = GeckoRuntime.create(this, new GeckoRuntimeSettings.Builder()
                .contentBlocking(cb.build())
                .preferredColorScheme(scheme)
                .fontSizeFactor(prefs.fontSizeFactor())
                .aboutConfigEnabled(false)
                .debugLogging(false)
                .build());
        applyLocales();
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
                    // Reader articles arrive from the content script through a
                    // per-session message delegate (sender.session = tab).
                    for (Tab t : tabs.tabs()) attachReaderDelegate(t);
                    return null;
                });
    }

    /** Applies the content-language override to the runtime. */
    private void applyLocales() {
        try {
            String loc = prefs.locale();
            runtime.getSettings().setLocales(loc.isEmpty() ? null : new String[]{loc});
        } catch (Exception ignored) {}
    }

    /** Re-applies every runtime-level setting from prefs (after Settings). */
    private void applyRuntimeSettings() {
        if (runtime == null) return;
        try {
            runtime.getSettings().setPreferredColorScheme(colorSchemeConstant());
            runtime.getSettings().setFontSizeFactor(prefs.fontSizeFactor());
            int cbMode = prefs.cookieBanners()
                    ? org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
                    : org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED;
            runtime.getSettings().getContentBlocking().setCookieBannerMode(cbMode);
            runtime.getSettings().getContentBlocking().setCookieBannerModePrivateBrowsing(cbMode);
            applyLocales();
        } catch (Exception ignored) {}
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
        String host = Utils.hostOf(pageUrl(t));
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
        // The default print delegate needs an Activity context for PrintManager.
        view.setActivityContextDelegate(() -> MainActivity.this);
        webContainer.addView(view);
        session.open(runtime);
        view.setSession(session);

        Tab tab = tabs.add(new Tab(tabs.count() + System.identityHashCode(session), session, view, incognito));
        setupSession(tab);
        attachReaderDelegate(tab);
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

        session.setScrollDelegate(new GeckoSession.ScrollDelegate() {
            @Override
            public void onScrollChanged(@NonNull GeckoSession s, int scrollX, int scrollY) {
                tab.scrollY = scrollY;
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
                String dataUri = "data:text/html;base64," + android.util.Base64
                        .encodeToString(page.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                android.util.Base64.NO_WRAP);
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
                    // Route through the platform runtime dialog; the Gecko
                    // callback completes when the user answers.
                    pendingAndroidCallback = callback;
                    permLauncher.launch(needed.toArray(new String[0]));
                }
            }

            @Override
            public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession s,
                                                                   @NonNull GeckoSession.PermissionDelegate.ContentPermission perm) {
                return handleContentPermission(tab, perm);
            }

            @Override
            public void onMediaPermissionRequest(@NonNull GeckoSession s, String url,
                                                 GeckoSession.PermissionDelegate.MediaSource[] video,
                                                 GeckoSession.PermissionDelegate.MediaSource[] audio,
                                                 @NonNull GeckoSession.PermissionDelegate.MediaCallback callback) {
                handleMediaPermission(tab, url, video, audio, callback);
            }
        });

        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onAlertPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.AlertPrompt prompt) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title == null || prompt.title.isEmpty()
                                ? getString(R.string.alert_dialog) : prompt.title)
                        .setMessage(prompt.message == null ? "" : prompt.message)
                        .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;    // alert() has no return value; dismiss completes it
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onButtonPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.ButtonPrompt prompt) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title == null || prompt.title.isEmpty()
                                ? getString(R.string.confirm_dialog) : prompt.title)
                        .setMessage(prompt.message == null ? "" : prompt.message)
                        .setPositiveButton(R.string.ok,
                                (d, w) -> prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                        .setNegativeButton(R.string.cancel,
                                (d, w) -> prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onBeforeUnloadPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.BeforeUnloadPrompt prompt) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.leave_page_title)
                        .setMessage(R.string.leave_page_body)
                        .setPositiveButton(R.string.leave_page_stay,
                                (d, w) -> prompt.confirm(AllowOrDeny.DENY))
                        .setNegativeButton(R.string.leave_page_go,
                                (d, w) -> prompt.confirm(AllowOrDeny.ALLOW))
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onRepostConfirmPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.RepostConfirmPrompt prompt) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.repost_title)
                        .setMessage(R.string.repost_body)
                        .setPositiveButton(R.string.repost_continue,
                                (d, w) -> prompt.confirm(AllowOrDeny.ALLOW))
                        .setNegativeButton(R.string.cancel,
                                (d, w) -> prompt.confirm(AllowOrDeny.DENY))
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onTextPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.TextPrompt prompt) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                if (prompt.message != null && !prompt.message.isEmpty()) {
                    TextView msg = new TextView(MainActivity.this);
                    msg.setText(prompt.message);
                    box.addView(msg);
                }
                EditText input = new EditText(MainActivity.this);
                input.setText(prompt.defaultValue == null ? "" : prompt.defaultValue);
                box.addView(input);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title == null || prompt.title.isEmpty()
                                ? getString(R.string.alert_dialog) : prompt.title)
                        .setView(box)
                        .setPositiveButton(R.string.ok,
                                (d, w) -> prompt.confirm(input.getText().toString()))
                        .setNegativeButton(R.string.cancel, (d, w) -> prompt.dismiss())
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onAuthPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.AuthPrompt prompt) {
                boolean onlyPassword = false;
                try {
                    onlyPassword = (prompt.authOptions.flags
                            & GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0;
                } catch (Exception ignored) {}
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                TextView msg = new TextView(MainActivity.this);
                msg.setText(prompt.message == null
                        ? getString(R.string.auth_message) : prompt.message);
                box.addView(msg);
                final EditText user = new EditText(MainActivity.this);
                user.setHint(R.string.auth_username);
                if (!onlyPassword) box.addView(user);
                final EditText pass = new EditText(MainActivity.this);
                pass.setHint(R.string.auth_password);
                pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                box.addView(pass);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title == null || prompt.title.isEmpty()
                                ? getString(R.string.auth_title) : prompt.title)
                        .setView(box)
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            if (onlyPassword) prompt.confirm(pass.getText().toString());
                            else prompt.confirm(user.getText().toString(), pass.getText().toString());
                        })
                        .setNegativeButton(R.string.cancel, (d, w) -> prompt.dismiss())
                        .setOnCancelListener(d -> prompt.dismiss())
                        .show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onChoicePrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.ChoicePrompt prompt) {
                // Choices with nested menus are flattened to their top level.
                GeckoSession.PromptDelegate.ChoicePrompt.Choice[] choices = prompt.choices;
                if (choices == null || choices.length == 0) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                String[] labels = new String[choices.length];
                for (int i = 0; i < choices.length; i++) labels[i] = choices[i].label == null ? "" : choices[i].label;
                boolean multiple = prompt.type
                        == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE;
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(prompt.title == null || prompt.title.isEmpty()
                                ? getString(R.string.choose_dialog) : prompt.title);
                if (multiple) {
                    boolean[] checked = new boolean[choices.length];
                    ArrayList<String> picked = new ArrayList<>();
                    b.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                        if (isChecked) picked.add(choices[which].id);
                        else picked.remove(choices[which].id);
                    });
                    b.setPositiveButton(R.string.ok, (d, w) ->
                            prompt.confirm(picked.toArray(new String[0])));
                } else {
                    b.setSingleChoiceItems(labels, -1, (d, which) -> {
                        prompt.confirm(choices[which].id);
                        d.dismiss();
                    });
                }
                b.setNegativeButton(R.string.cancel, (d, w) -> prompt.dismiss());
                b.setOnCancelListener(d -> prompt.dismiss());
                b.show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onColorPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.ColorPrompt prompt) {
                final String[] palette = {
                        "#000000", "#4355b9", "#1e88e5", "#43a047", "#fdd835",
                        "#fb8c00", "#e53935", "#8e24aa", "#00897b", "#6d4c41",
                        "#9e9e9e", "#ffffff"
                };
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                row.setPadding(pad, pad, pad, pad);
                final AlertDialog[] holder = new AlertDialog[1];
                for (final String color : palette) {
                    TextView sw = new TextView(MainActivity.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            (int) (34 * getResources().getDisplayMetrics().density),
                            (int) (34 * getResources().getDisplayMetrics().density));
                    lp.setMargins(6, 6, 6, 6);
                    sw.setLayoutParams(lp);
                    sw.setBackgroundColor(android.graphics.Color.parseColor(color));
                    sw.setOnClickListener(v -> {
                        prompt.confirm(color);
                        if (holder[0] != null) holder[0].dismiss();
                    });
                    row.addView(sw);
                }
                holder[0] = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.choose_color)
                        .setView(row)
                        .setNegativeButton(R.string.cancel, (dd, w) -> prompt.dismiss())
                        .setOnCancelListener(dd -> prompt.dismiss())
                        .create();
                holder[0].show();
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onDateTimePrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.DateTimePrompt prompt) {
                int type = prompt.type;
                final boolean[] done = {false};
                if (type == GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    android.app.DatePickerDialog dp = new android.app.DatePickerDialog(MainActivity.this,
                            (view, y, m, day) -> {
                                done[0] = true;
                                prompt.confirm(String.format(
                                        java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, day));
                            },
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH),
                            cal.get(java.util.Calendar.DAY_OF_MONTH));
                    dp.setOnCancelListener(d -> { if (!done[0]) prompt.dismiss(); });
                    dp.show();
                    return null;
                }
                if (type == GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    android.app.TimePickerDialog tp = new android.app.TimePickerDialog(MainActivity.this,
                            (view, h, min) -> {
                                done[0] = true;
                                prompt.confirm(String.format(
                                        java.util.Locale.US, "%02d:%02d", h, min));
                            },
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE), true);
                    tp.setOnCancelListener(d -> { if (!done[0]) prompt.dismiss(); });
                    tp.show();
                    return null;
                }
                // datetime-local / month / week pickers: dismissed honestly
                // rather than approximated with a different control.
                return GeckoResult.fromValue(prompt.dismiss());
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onFilePrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.FilePrompt prompt) {
                pendingFilePrompt = prompt;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                boolean multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE;
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
                try {
                    fileLauncher.launch(Intent.createChooser(i, getString(R.string.choose_file)));
                } catch (Exception e) {
                    pendingFilePrompt = null;
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                return null;
            }

            @Override
            public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> onPopupPrompt(
                    @NonNull GeckoSession s,
                    @NonNull GeckoSession.PromptDelegate.PopupPrompt prompt) {
                return handlePopupPrompt(tab, prompt);
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

    // ---------- Content permissions (location, notifications, DRM, ...) ----

    /**
     * Website content-permission flow. Privacy-first: silent categories
     * (tracking permission, autoplay) are always denied; everything else
     * asks, with optional per-host memory. Settings can switch the default
     * to deny-silently. Private tabs can decide per prompt but nothing is
     * remembered.
     */
    /** Pending content-permission completion (Android 13+ notification bridge). */
    private GeckoResult<Integer> pendingContentResult;
    private boolean pendingContentAllow;

    private GeckoResult<Integer> handleContentPermission(Tab tab,
            GeckoSession.PermissionDelegate.ContentPermission perm) {
        switch (perm.permission) {
            case GeckoSession.PermissionDelegate.PERMISSION_TRACKING:
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE:
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE:
                // Privacy red lines: never allow site tracking permission;
                // autoplay policy stays engine-default (denied).
                return GeckoResult.fromValue(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
            default:
                break;
        }

        String host = Utils.hostOf(perm.uri);
        if (host == null || host.isEmpty()) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        }
        int remembered = tab.incognito ? 0 : prefs.rememberedPermission(host, String.valueOf(perm.permission));
        if (remembered > 0) {
            return allowNotifications(perm);
        }
        if (remembered < 0 || prefs.permDefault() == 1) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        }

        final GeckoResult<Integer> result = new GeckoResult<>();
        String what = permissionLabel(perm.permission);
        String[] options = tab.incognito
                ? new String[]{getString(R.string.perm_allow), getString(R.string.perm_deny)}
                : new String[]{getString(R.string.perm_allow), getString(R.string.perm_deny),
                               getString(R.string.perm_always_allow), getString(R.string.perm_always_deny)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.perm_request_title, what))
                .setMessage(getString(R.string.perm_request_body, host, what))
                .setItems(options, (d, which) -> {
                    int value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY;
                    if (which == 0) {
                        value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
                    } else if (which == 2 && !tab.incognito) {
                        value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW;
                        prefs.rememberPermission(host, String.valueOf(perm.permission), 1);
                    } else if (which == 3 && !tab.incognito) {
                        value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY;
                        prefs.rememberPermission(host, String.valueOf(perm.permission), -1);
                    }
                    if (value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) {
                        allowNotifications(perm).then(v -> {
                            result.complete(v == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                    ? value
                                    : GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                            return null;
                        });
                    } else {
                        result.complete(value);
                    }
                })
                .setOnCancelListener(d -> result.complete(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY))
                .show();
        return result;
    }

    /** Android 13+ bridge: granting desktop notifications needs the platform
     *  POST_NOTIFICATIONS permission. Without the bridge needed, completes
     *  with ALLOW immediately; otherwise completes after the platform dialog. */
    private GeckoResult<Integer> allowNotifications(
            GeckoSession.PermissionDelegate.ContentPermission perm) {
        boolean needsBridge = perm.permission
                == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
                && android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED;
        if (!needsBridge) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
        }
        final GeckoResult<Integer> result = new GeckoResult<>();
        pendingContentResult = result;
        pendingContentAllow = true;
        permLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        return result;
    }

    private String permissionLabel(int perm) {
        switch (perm) {
            case GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION: return getString(R.string.perm_location);
            case GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION: return getString(R.string.perm_notifications);
            case GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE: return getString(R.string.perm_storage);
            case GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS: return getString(R.string.perm_drm);
            case GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS: return getString(R.string.perm_storage_access);
            case GeckoSession.PermissionDelegate.PERMISSION_XR: return getString(R.string.perm_xr);
            default: return getString(R.string.perm_generic);
        }
    }

    /** Camera / microphone (media) permission: dialog + platform runtime grant. */
    private void handleMediaPermission(Tab tab, String url,
                                       GeckoSession.PermissionDelegate.MediaSource[] video,
                                       GeckoSession.PermissionDelegate.MediaSource[] audio,
                                       GeckoSession.PermissionDelegate.MediaCallback callback) {
        String host = Utils.hostOf(url);
        if (host == null || host.isEmpty()) {
            callback.reject();
            return;
        }
        boolean wantsVideo = video != null && video.length > 0;
        boolean wantsAudio = audio != null && audio.length > 0;
        int remembered = tab.incognito ? 0
                : prefs.rememberedPermission(host, wantsVideo
                        ? Manifest.permission.CAMERA : Manifest.permission.RECORD_AUDIO);
        if (remembered < 0 || prefs.permDefault() == 1) {
            callback.reject();
            return;
        }

        java.util.ArrayList<String> appPerms = new java.util.ArrayList<>();
        if (wantsVideo) appPerms.add(Manifest.permission.CAMERA);
        if (wantsAudio) appPerms.add(Manifest.permission.RECORD_AUDIO);
        appPerms.removeIf(p -> ContextCompat.checkSelfPermission(this, p)
                == PackageManager.PERMISSION_GRANTED);

        String[] options = tab.incognito
                ? new String[]{getString(R.string.perm_allow), getString(R.string.perm_deny)}
                : new String[]{getString(R.string.perm_allow), getString(R.string.perm_deny),
                               getString(R.string.perm_always_allow)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.perm_media_title,
                        wantsVideo && wantsAudio ? getString(R.string.perm_camera_mic)
                                : wantsVideo ? getString(R.string.perm_camera)
                                : getString(R.string.perm_microphone)))
                .setMessage(getString(R.string.perm_request_body, host,
                        wantsVideo && wantsAudio ? getString(R.string.perm_camera_mic)
                                : wantsVideo ? getString(R.string.perm_camera)
                                : getString(R.string.perm_microphone)))
                .setItems(options, (d, which) -> {
                    if (which == 1) {
                        callback.reject();
                        return;
                    }
                    if (which == 2 && !tab.incognito) {
                        prefs.rememberPermission(host, wantsVideo
                                ? Manifest.permission.CAMERA : Manifest.permission.RECORD_AUDIO, 1);
                    }
                    if (appPerms.isEmpty()) {
                        callback.grant(wantsVideo ? video[0] : null,
                                       wantsAudio ? audio[0] : null);
                        return;
                    }
                    pendingMediaCallback = callback;
                    pendingMediaVideo = wantsVideo ? video[0] : null;
                    pendingMediaAudio = wantsAudio ? audio[0] : null;
                    permLauncher.launch(appPerms.toArray(new String[0]));
                })
                .setOnCancelListener(d -> callback.reject())
                .show();
    }

    /** Completes the pending gecko permission callback after the platform dialog. */
    private void resolvePendingPermissions(java.util.Map<String, Boolean> result) {
        if (pendingContentResult != null) {
            GeckoResult<Integer> r = pendingContentResult;
            pendingContentResult = null;
            boolean all = true;
            for (Boolean granted : result.values()) all &= granted != null && granted;
            r.complete(all && pendingContentAllow
                    ? GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    : GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
            return;
        }
        if (pendingAndroidCallback != null) {
            GeckoSession.PermissionDelegate.Callback cb = pendingAndroidCallback;
            pendingAndroidCallback = null;
            boolean all = true;
            for (Boolean granted : result.values()) all &= granted != null && granted;
            if (all) cb.grant();
            else cb.reject();
            return;
        }
        if (pendingMediaCallback != null) {
            GeckoSession.PermissionDelegate.MediaCallback cb = pendingMediaCallback;
            pendingMediaCallback = null;
            boolean all = true;
            for (Boolean granted : result.values()) all &= granted != null && granted;
            if (all) {
                cb.grant(pendingMediaVideo, pendingMediaAudio);
            } else {
                cb.reject();
            }
            pendingMediaVideo = null;
            pendingMediaAudio = null;
        }
    }

    // ---------- Popups ----------

    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> handlePopupPrompt(
            Tab tab, GeckoSession.PromptDelegate.PopupPrompt prompt) {
        String host = Utils.hostOf(prompt.targetUri);
        if (!prefs.blockPopups()
                || (host != null && Utils.siteListContains(prefs.popupAllowed(), host))) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
        }
        if (tab == null || host == null || host.isEmpty()
                || !host.equals(Utils.hostOf(tab.url))) {
            toast(getString(R.string.popup_blocked, host == null ? "" : host));
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        final GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = new GeckoResult<>();
        String[] options = {
                getString(R.string.popup_block),
                getString(R.string.popup_allow_once),
                getString(R.string.popup_always_allow, host)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.popup_title, host))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        toast(getString(R.string.popup_blocked, host));
                        result.complete(prompt.confirm(AllowOrDeny.DENY));
                    } else {
                        if (which == 2) prefs.popupAllowed(Utils.siteListAdd(prefs.popupAllowed(), host));
                        result.complete(prompt.confirm(AllowOrDeny.ALLOW));
                    }
                })
                .setOnCancelListener(d -> result.complete(prompt.confirm(AllowOrDeny.DENY)))
                .show();
        return result;
    }

    // ---------- File picker resolution ----------

    private void resolveFilePrompt(androidx.activity.result.ActivityResult result) {
        GeckoSession.PromptDelegate.FilePrompt prompt = pendingFilePrompt;
        pendingFilePrompt = null;
        if (prompt == null) return;
        Intent data = result.getData();
        ArrayList<Uri> uris = new ArrayList<>();
        if (data != null) {
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        }
        try {
            for (Uri u : uris) {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception ignored) {}
        if (uris.isEmpty()) {
            prompt.dismiss();
        } else if (uris.size() == 1) {
            prompt.confirm(this, uris.get(0));
        } else {
            prompt.confirm(this, uris.toArray(new Uri[0]));
        }
    }

    // ---------- Reader view ----------

    /** Per-session message delegate that receives reader articles from the
     *  shield content script (sender.session identifies the tab). */
    private void attachReaderDelegate(Tab tab) {
        if (shield == null || tab == null) return;
        try {
            tab.session.getWebExtensionController().setMessageDelegate(shield,
                    new WebExtension.MessageDelegate() {
                        @Override
                        public GeckoResult<Object> onMessage(@NonNull String nativeApp,
                                                             @NonNull Object message,
                                                             @NonNull WebExtension.MessageSender sender) {
                            if (sender.session != tab.session) return null;
                            if (!(message instanceof JSONObject)) return null;
                            JSONObject o = (JSONObject) message;
                            if (!"reader".equals(o.optString("type"))) return null;
                            if (!o.optBoolean("readerable", false)) return null;
                            Tab.ReaderArticle a = new Tab.ReaderArticle();
                            a.url = o.optString("url");
                            a.title = o.optString("title");
                            a.byline = o.optString("byline");
                            a.siteName = o.optString("siteName");
                            a.content = o.optString("content");
                            a.length = o.optInt("length", 0);
                            if (a.content.length() > 0) tab.readerArticle = a;
                            return null;
                        }
                    }, NATIVE_APP);
        } catch (Exception ignored) {}
    }

    private void toggleReader() {
        Tab t = tabs.currentTab();
        if (t == null) return;
        if (t.readerActive) {
            exitReader(t);
            return;
        }
        Tab.ReaderArticle a = t.readerArticle;
        if (a == null) {
            toast(R.string.reader_unavailable);
            return;
        }
        boolean dark = prefs.colorScheme() == 2
                || (prefs.colorScheme() == 0
                    && (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                       == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        ReaderSupport.Rendered r = ReaderSupport.render(this, a, dark, prefs.readerTextScale());
        if (r == null) {
            toast(R.string.reader_unavailable);
            return;
        }
        t.readerSourceUrl = t.url;
        t.readerActive = true;
        hideFindBar();
        t.session.loadUri(Uri.fromFile(r.file).toString());
        updateChrome(t);
        toast(getString(R.string.reader_opened, r.readingTime));
    }

    private void exitReader(Tab t) {
        t.readerActive = false;
        String src = t.readerSourceUrl;
        t.readerSourceUrl = null;
        deleteReaderFile();
        loadInTab(t, src == null || src.isEmpty() ? HOME_URL : src);
        updateChrome(t);
    }

    /** Reader renders happen in app cache; the file is removed on exit. */
    private void deleteReaderFile() {
        try {
            File f = new File(getCacheDir(), "reader/reader.html");
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignored) {}
    }

    // ---------- Print + Save as PDF ----------

    /** Opens the Android print dialog (which includes "Save as PDF"). */
    private void printCurrent() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        try {
            t.session.printPageContent();
        } catch (Exception e) {
            toast(R.string.print_failed);
        }
    }

    /** Renders the page to a PDF and saves it into the Downloads collection. */
    private void savePdf() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        toast(R.string.saving_pdf);
        try {
            t.session.saveAsPdf().then(stream -> {
                runOnUiThread(() -> savePdfStream(t, stream));
                return null;
            }).exceptionally(e -> {
                runOnUiThread(() -> toast(R.string.print_failed));
                return null;
            });
        } catch (Exception e) {
            toast(R.string.print_failed);
        }
    }

    private void savePdfStream(Tab t, java.io.InputStream is) {
        try {
            String base = t.title == null || t.title.trim().isEmpty() ? "zerium-page" : t.title;
            String safe = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (safe.length() > 60) safe = safe.substring(0, 60);
            String name = safe + ".pdf";
            Uri item;
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                item = getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            } else {
                item = null;
            }
            java.io.File legacy = null;
            if (item == null) {
                legacy = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                if (legacy.exists()) {
                    legacy = new File(legacy.getParentFile(),
                            System.currentTimeMillis() + "_" + name);
                }
            }
            try (java.io.OutputStream os = item != null
                            ? getContentResolver().openOutputStream(item)
                            : new java.io.FileOutputStream(legacy)) {
                if (os == null || is == null) throw new IllegalStateException("stream");
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            if (item != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(item, done, null, null);
            }
            toast(getString(R.string.pdf_saved, name));
        } catch (Exception e) {
            toast(R.string.print_failed);
        }
    }

    // ---------- Page tools ----------

    private void loadInTab(Tab tab, String url) {
        if (tab == null || url == null) return;
        // Leaving the reader page by any navigation clears reader state.
        if (tab.readerActive && (url == null || !url.startsWith("file:"))) {
            tab.readerActive = false;
            tab.readerSourceUrl = null;
        }
        if (HOME_URL.equals(url)) {
            tab.url = HOME_URL;
            tab.title = getString(R.string.start_page);
            tab.session.load(new GeckoSession.Loader().data(startPageHtml(), "text/html"));
            updateChrome(tab);
            return;
        }
        tab.session.loadUri(url);
    }

    /** The real page URL, even while the reader rendering is on screen. */
    private static String pageUrl(Tab t) {
        if (t == null) return "";
        if (t.readerActive && t.readerSourceUrl != null) return t.readerSourceUrl;
        return t.url == null ? "" : t.url;
    }

    /** Minimal generated start page (same spirit as the WebView edition). */
    private String startPageHtml() {
        String action = Utils.engineAt(prefs, prefs.searchEngine()).query;
        // Most-visited tiles (local history only) + the total block count.
        StringBuilder tiles = new StringBuilder();
        try {
            for (String host : history.topHosts(8)) {
                String letter = host.isEmpty() ? "?" : host.substring(0, 1).toUpperCase();
                tiles.append("<a class='tile' href='https://")
                        .append(android.net.Uri.encode(host))
                        .append("/'><span class='tl'>").append(escHtml(letter))
                        .append("</span><span class='th'>").append(escHtml(host))
                        .append("</span></a>");
            }
        } catch (Exception ignored) {}
        long blocked = prefs.totalBlocked();
        String stats = blocked > 0
                ? "<div class='stats'>" + String.format(java.util.Locale.US,
                        "%,d", blocked) + " trackers and ads blocked so far</div>"
                : "";
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
                + ".tiles{display:grid;grid-template-columns:repeat(4,minmax(64px,86px));gap:10px;"
                + "margin-top:34px;width:100%;max-width:580px}"
                + ".tile{display:flex;flex-direction:column;align-items:center;gap:6px;text-decoration:none;"
                + "background:#fff;border:1px solid #e2e2ea;border-radius:16px;padding:12px 4px;"
                + "box-shadow:0 4px 14px rgba(20,25,60,.05)}"
                + ".tl{width:30px;height:30px;border-radius:10px;background:linear-gradient(135deg,#4355b9,#7c9cff);"
                + "color:#fff;font-weight:700;font-size:15px;display:flex;align-items:center;justify-content:center}"
                + ".th{font-size:10px;color:#5f5f6b;max-width:100%;overflow:hidden;text-overflow:ellipsis;"
                + "white-space:nowrap}"
                + ".stats{margin-top:26px;font-size:12px;color:#5f5f6b}"
                + "@media (prefers-color-scheme:dark){body{background:#0e1016;color:#e4e2e6}"
                + ".search{background:#171a23;border-color:#2a2d38}input{color:#e4e2e6}"
                + ".tile{background:#171a23;border-color:#2a2d38}.stats{color:#9a9aa6}}"
                + "</style></head><body>"
                + "<div class='logo'>Zerium&nbsp;G</div>"
                + "<div class='tag'>Gecko engine &middot; engine-level blocking &middot; open source</div>"
                + "<form onsubmit='var q=document.getElementById(\"q\").value.trim();"
                + "if(q){var t=\"" + action + "\";location.href=t.replace(\"%s\",encodeURIComponent(q));}"
                + "return false'>"
                + "<div class='search'><input id='q' type='search' placeholder='"
                + getString(R.string.search_hint) + "' autofocus><button type='submit'>Go</button>"
                + "</div></form>"
                + (tiles.length() > 0 ? "<div class='tiles'>" + tiles + "</div>" : "")
                + stats
                + "</body></html>";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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
        Uri u = Uri.parse(pageUrl(t));
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
        // FinderResult gives the current match ordinal (1-based) and the
        // total number of matches on the page (-1 while counting).
        if (result.found) {
            int total = result.total < 0 ? 0 : result.total;
            findCount.setText((result.current) + "/" + total);
        } else {
            findCount.setText("0/0");
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
        } else if (tab.readerActive) {
            omnibox.setText(getString(R.string.reader_prefix)
                    + displayUrl(tab.readerSourceUrl == null ? tab.url : tab.readerSourceUrl));
            btnSecurity.setImageResource(R.drawable.ic_lock);
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
        // Refresh gesture only on real pages the user can reload.
        swipe.setEnabled(prefs.pullToRefresh() && !isStartPage(tab));
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
        pm.getMenu().add(0, MENU_DOWNLOADS, 5, R.string.menu_downloads);
        pm.getMenu().add(0, MENU_FIND, 6, R.string.menu_find);
        pm.getMenu().add(0, MENU_READER, 7, t != null && t.readerActive
                ? R.string.menu_reader_exit : R.string.menu_reader)
                .setEnabled(t != null && (t.readerActive || t.readerArticle != null));
        pm.getMenu().add(0, MENU_DESKTOP, 8, R.string.menu_desktop)
                .setCheckable(true).setChecked(t != null && isDesktop(t));
        pm.getMenu().add(0, MENU_JS, 9, R.string.menu_javascript)
                .setCheckable(true)
                .setChecked(t != null && t.session.getSettings().getAllowJavascript());
        pm.getMenu().add(0, MENU_TRANSLATE, 10, R.string.menu_translate)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_PRINT, 11, R.string.menu_print)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_SAVE_PDF, 12, R.string.menu_save_pdf)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_SHARE, 13, R.string.menu_share);
        pm.getMenu().add(0, MENU_ALLOW_SITE, 14, R.string.menu_allow_site)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_BLOCK_INFO, 15, R.string.menu_block_info);
        pm.getMenu().add(0, MENU_SETTINGS, 16, R.string.menu_settings);
        pm.getMenu().add(0, MENU_EXIT, 17, R.string.menu_exit);
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
                String pageNow = pageUrl(t);
                if (bookmarks.contains(pageNow)) {
                    for (BookmarksDB.Entry e : bookmarks.all()) {
                        if (e.url.equals(pageNow)) { bookmarks.remove(e.id); break; }
                    }
                    toast(R.string.bookmark_removed);
                } else {
                    bookmarks.add(pageNow, t.title);
                    toast(R.string.bookmark_added);
                }
                break;
            case MENU_BOOKMARKS:
                startActivity(new Intent(this, BookmarksActivity.class));
                break;
            case MENU_HISTORY:
                startActivity(new Intent(this, HistoryActivity.class));
                break;
            case MENU_DOWNLOADS:
                startActivity(new Intent(this, DownloadsActivity.class));
                break;
            case MENU_FIND: showFindBar(); break;
            case MENU_READER: toggleReader(); break;
            case MENU_DESKTOP: toggleDesktop(); break;
            case MENU_JS: toggleJavascript(); break;
            case MENU_TRANSLATE: translatePage(); break;
            case MENU_PRINT: printCurrent(); break;
            case MENU_SAVE_PDF: savePdf(); break;
            case MENU_SHARE:
                if (t != null && !isStartPage(t)) {
                    Intent si = new Intent(Intent.ACTION_SEND);
                    si.setType("text/plain");
                    si.putExtra(Intent.EXTRA_TEXT, pageUrl(t));
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

    /** Saves URL + full session state (back/forward history) per tab. */
    private void restoreSession() {
        String saved = prefs.savedTabs();
        int index = prefs.savedTabIndex();
        int opened = 0;
        int target = 0;
        if (saved != null && !saved.isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(saved);
                for (int i = 0; i < arr.length() && opened < MAX_RESTORED_TABS; i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String u = o.optString("u", "");
                    String st = o.optString("s", "");
                    if (u.isEmpty() && st.isEmpty()) continue;
                    openTab(u.isEmpty() ? null : u, false);
                    Tab t = tabs.tabs().get(tabs.count() - 1);
                    if (!st.isEmpty()) {
                        try {
                            GeckoSession.SessionState state =
                                    GeckoSession.SessionState.fromString(st);
                            if (state != null) t.session.restoreState(state);
                        } catch (Exception ignored) {}
                    }
                    if (i == index) target = opened;
                    opened++;
                }
            } catch (Exception ignored) {}
        }
        if (opened == 0) {
            openTab(null, false);
        } else {
            tabs.setCurrent(Math.max(0, Math.min(target, opened - 1)));
            showCurrentWebView();
            updateChrome(tabs.currentTab());
        }
    }

    private void saveSession() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            int current = 0;
            int i = 0;
            for (Tab t : tabs.tabs()) {
                if (t.incognito) continue;
                String u = t.url == null ? "" : t.url;
                if (u.startsWith("data:") || u.startsWith("file:")) u = "";
                if (u.isEmpty() && t.state == null) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("u", u);
                if (t.state != null) o.put("s", t.state.toString());
                if (i == tabs.current()) current = arr.length();
                arr.put(o);
                i++;
                if (arr.length() >= MAX_RESTORED_TABS) break;
            }
            prefs.savedTabs(arr.length() == 0 ? "" : arr.toString());
            prefs.savedTabIndex(current);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteReaderFile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed: color scheme, text size, content locale,
        // cookie banners, the global JS default and the shield allowlist.
        applyRuntimeSettings();
        pushShieldConfig();
        Tab t = tabs.currentTab();
        if (t != null) updateChrome(t);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            applyRuntimeSettings();
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
        if (t != null && t.readerActive) {
            exitReader(t);
            return;
        }
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
