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
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zerium.gecko.dl.DownloadEngine;
import com.zerium.gecko.dl.MediaRegistry;
import com.zerium.gecko.dl.MediaRegistryHolder;
import com.zerium.gecko.ui.MediaGrabberSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;
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
public class MainActivity extends AppCompatActivity implements ExtActionSupport.Host, MediaGrabberSheet.Host {

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
    private static final int MENU_ADDONS = 19;
    private static final int MENU_DELETE_DATA = 20;
    private static final int MENU_BACK = 21;
    private static final int MENU_FORWARD = 22;
    private static final int MENU_RELOAD = 23;
    private static final int MENU_SHARE_QA = 24;
    private static final int MENU_MEDIA = 25;
    private static final int MENU_SPACES = 26;

    private Prefs prefs;
    private GeckoRuntime runtime;
    private WebExtension shield;
    private WebExtension.Port shieldPort;
    private BookmarksDB bookmarks;
    private HistoryDB history;
    private DownloadEngine engine;
    private MediaRegistry mediaRegistry;
    private Spaces spaces;
    private SpaceTabArchive tabArchive;
    private TabGroups tabGroups;
    private LinearLayout spaceChips;

    private GSwipeLayout swipe;
    private FrameLayout webContainer;
    private EditText omnibox;
    private ImageButton btnSecurity, btnRefresh, btnBack, btnForward, btnHome, btnExtensions;
    private TextView btnTabs;
    private ProgressBar progress;
    private LinearLayout topBar, bottomBar, findBar;
    private EditText findInput;
    private TextView findCount;
    private View tabSwitcher;
    private RecyclerView tabsGrid;
    private TabsAdapter tabsAdapter;
    private EditText tabSearch;
    private TextView noMatchingTabs;
    private ItemTouchHelper dragHelper;

    // Dedicated group-detail surface (Chrome-style group grid)
    private View groupDetail;
    private RecyclerView groupGrid;
    private TabsAdapter groupAdapter;
    private TabGroups.Group detailGroup;
    private ItemTouchHelper groupDragHelper;
    private View newTabFab;

    // Long-press drag state (tab grouping inside the active space)
    private Tab draggedTab;
    private float dragDistance;
    private float dragLastX, dragLastY;
    private RecyclerView.ViewHolder hoverVH;
    /** RecyclerView the running drag started from (overview vs detail grid). */
    private RecyclerView dragSourceRv;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private GeckoSession fullscreenSession;
    private ExtActionSupport extActions;

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

    /** ExtensionsActivity reads the runtime through this holder; the main
     *  activity stays alive underneath it, so it is always valid there. */
    public static volatile GeckoRuntime runtimeForAddons;
    /** Add-on list refresh callback set by ExtensionsActivity. */
    public static volatile Runnable addonListRefresh;

    private final TabManager tabs = new TabManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        spaces = Spaces.load(prefs.spacesJson());
        tabArchive = SpaceTabArchive.load(prefs.spacesTabsJson());
        tabGroups = TabGroups.load(prefs.tabGroupsJson());
        // Wipe profile directories of spaces deleted while they were active
        // (deletion must wait for the restart that switched away from them).
        String pendingDirs = prefs.pendingSpaceDirs();
        if (pendingDirs != null && !pendingDirs.isEmpty()) {
            for (String id : pendingDirs.split(",")) {
                if (!id.isEmpty()) SpaceRuntime.deleteDir(this, id);
            }
            prefs.pendingSpaceDirs("");
        }
        engine = DownloadEngine.get(this);
        mediaRegistry = MediaRegistryHolder.get(this);
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
        spaceChips = findViewById(R.id.spaceChips);
        tabSearch = findViewById(R.id.searchTabs);
        noMatchingTabs = findViewById(R.id.noMatchingTabs);

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(0xFF000000);
        addContentView(fullscreenContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.GONE);

        createRuntime();

        // Add-on toolbar actions (browser/page action popups).
        extActions = new ExtActionSupport(this, runtime, this);
        extActions.refresh();

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
        btnExtensions = findViewById(R.id.btnExtensions);
        btnExtensions.setOnClickListener(this::onExtensionsClicked);
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
        findViewById(R.id.btnMenuBottom).setOnClickListener(this::showBottomMenu);

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
        tabsAdapter = new TabsAdapter(tabs.tabs(), tabs, tabGroups, new TabsAdapter.Listener() {
            @Override
            public void onOpen(Tab tab) {
                hideTabSwitcher();
                switchTab(tab);
            }

            @Override
            public void onClose(Tab tab) {
                closeTab(tab);
            }

            @Override
            public void onTabMenu(Tab tab) {
                showTabContextMenu(tab);
            }

            @Override
            public void onOpenGroup(TabGroups.Group group) {
                showGroupDetail(group);
            }

            @Override
            public void onGroupMenu(TabGroups.Group group) {
                showGroupMenu(group);
            }
        });
        tabsAdapter.setActiveSpaceId(spaces.activeId());
        tabsGrid.setAdapter(tabsAdapter);
        dragHelper = buildDragHelper(tabsGrid);
        dragHelper.attachToRecyclerView(tabsGrid);
        tabsAdapter.setDragStarter(vh -> dragHelper.startDrag(vh));
        tabSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                tabsAdapter.filter(s.toString());
                noMatchingTabs.setVisibility(tabsAdapter.getItemCount() == 0
                        ? View.VISIBLE : View.GONE);
            }
        });

        // Dedicated group-detail grid (same adapter class, detail mode).
        newTabFab = findViewById(R.id.btnNewTab);
        groupDetail = findViewById(R.id.groupDetail);
        groupGrid = groupDetail.findViewById(R.id.groupGrid);
        groupGrid.setLayoutManager(new GridLayoutManager(this, 2));
        groupAdapter = new TabsAdapter(tabs.tabs(), tabs, tabGroups,
                new TabsAdapter.Listener() {
                    @Override
                    public void onOpen(Tab tab) {
                        hideTabSwitcher();
                        switchTab(tab);
                    }

                    @Override
                    public void onClose(Tab tab) {
                        closeTab(tab);
                    }

                    @Override
                    public void onTabMenu(Tab tab) {
                        showTabContextMenu(tab);
                    }

                    @Override
                    public void onOpenGroup(TabGroups.Group group) { }

                    @Override
                    public void onGroupMenu(TabGroups.Group group) {
                        showGroupMenu(group);
                    }
                });
        groupGrid.setAdapter(groupAdapter);
        groupDragHelper = buildDragHelper(groupGrid);
        groupDragHelper.attachToRecyclerView(groupGrid);
        groupAdapter.setDragStarter(vh -> groupDragHelper.startDrag(vh));
        groupDetail.findViewById(R.id.btnGroupDetailBack)
                .setOnClickListener(v -> closeGroupDetail());
        groupDetail.findViewById(R.id.btnGroupDetailMenu)
                .setOnClickListener(v -> {
                    if (detailGroup != null) showGroupMenu(detailGroup);
                });
        groupDetail.findViewById(R.id.btnGroupAddTab).setOnClickListener(v -> {
            if (detailGroup != null) openTabInGroup(detailGroup);
        });

        findViewById(R.id.btnSwitcherData).setOnClickListener(v -> deleteBrowsingData());
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

    /** Builds runtime settings; isolated active spaces launch the engine
     *  with their own profile directory via the -profile argument. */
    private GeckoRuntimeSettings buildRuntimeSettings() {
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
        GeckoRuntimeSettings.Builder rb = new GeckoRuntimeSettings.Builder()
                .contentBlocking(cb.build())
                .preferredColorScheme(scheme)
                .fontSizeFactor(prefs.fontSizeFactor())
                .allowInsecureConnections(httpsOnlyConstant())
                .aboutConfigEnabled(false)
                .debugLogging(false);
        SpaceRuntime.prepareLaunch(this, spaces, rb);
        return rb.build();
    }

    private void createRuntime() {
        runtime = GeckoRuntime.create(this, buildRuntimeSettings());
        runtimeForAddons = runtime;
        applyLocales();
        installExtensionDelegates();
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

    private @GeckoRuntimeSettings.HttpsOnlyMode int httpsOnlyConstant() {
        switch (prefs.httpsOnly()) {
            case 0: return GeckoRuntimeSettings.ALLOW_ALL;
            case 2: return GeckoRuntimeSettings.HTTPS_ONLY;
            default: return GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE;
        }
    }

    /**
     * Controller-level delegates for the add-on platform:
     * install/optional/update permission prompts (mandatory — without a
     * PromptDelegate, installs never complete) and a state-change hook
     * the Add-ons screen listens to.
     */
    private void installExtensionDelegates() {
        WebExtensionController c = runtime.getWebExtensionController();
        c.setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    @NonNull WebExtension extension,
                    @NonNull String[] permissions,
                    @NonNull String[] origins) {
                return askAddonPermissions(extension, permissions, origins);
            }

            @Override
            public GeckoResult<AllowOrDeny> onOptionalPrompt(
                    @NonNull WebExtension extension,
                    @NonNull String[] permissions,
                    @NonNull String[] origins) {
                return askAddonAllowDeny(getString(R.string.addons_install_confirm,
                        addonName(extension)), permissions, origins);
            }

            @Override
            public GeckoResult<AllowOrDeny> onUpdatePrompt(
                    @NonNull WebExtension currentlyInstalled,
                    @NonNull WebExtension updatedExtension,
                    @NonNull String[] newPermissions,
                    @NonNull String[] newOrigins) {
                return askAddonAllowDeny(getString(R.string.addons_install_confirm,
                        addonName(updatedExtension)), newPermissions, newOrigins);
            }
        });
        c.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override
            public void onInstalled(@NonNull WebExtension extension) {
                notifyAddonListChanged();
            }

            @Override
            public void onUninstalled(@NonNull WebExtension extension) {
                notifyAddonListChanged();
            }

            @Override
            public void onEnabled(@NonNull WebExtension extension) {
                notifyAddonListChanged();
            }

            @Override
            public void onDisabled(@NonNull WebExtension extension) {
                notifyAddonListChanged();
            }

            @Override
            public void onReady(@NonNull WebExtension extension) {
                notifyAddonListChanged();
            }
        });
    }

    private static String addonName(WebExtension ext) {
        if (ext == null || ext.metaData == null
                || ext.metaData.name == null || ext.metaData.name.isEmpty()) {
            return ext == null || ext.id == null ? "add-on" : ext.id;
        }
        return ext.metaData.name;
    }

    private void notifyAddonListChanged() {
        Runnable r = addonListRefresh;
        if (r != null) runOnUiThread(r);
        if (extActions != null) extActions.refresh();
    }

    /** Install-time permission dialog: permission list + private-mode grant. */
    private GeckoResult<WebExtension.PermissionPromptResponse> askAddonPermissions(
            WebExtension ext, String[] permissions, String[] origins) {
        final GeckoResult<WebExtension.PermissionPromptResponse> out = new GeckoResult<>();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        StringBuilder body = new StringBuilder();
        if (permissions.length == 0 && origins.length == 0) {
            body.append(getString(R.string.addons_permissions_none));
        } else {
            for (String p : permissions) body.append("\u2022 ").append(p).append('\n');
            for (String o : origins) body.append("\u2022 ").append(o).append('\n');
        }
        TextView msg = new TextView(this);
        msg.setText(body.toString());
        box.addView(msg);
        android.widget.CheckBox priv = new android.widget.CheckBox(this);
        priv.setText(R.string.addons_allow_private);
        box.addView(priv);
        TextView hint = new TextView(this);
        hint.setText(R.string.addons_private_hint);
        hint.setTextSize(12);
        hint.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        box.addView(hint);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.addons_install_confirm, addonName(ext)))
                .setView(box)
                .setPositiveButton(R.string.addons_install, (d, w) ->
                        out.complete(new WebExtension.PermissionPromptResponse(true, priv.isChecked())))
                .setNegativeButton(R.string.cancel, (d, w) ->
                        out.complete(new WebExtension.PermissionPromptResponse(false, false)))
                .setOnCancelListener(d ->
                        out.complete(new WebExtension.PermissionPromptResponse(false, false)))
                .show();
        return out;
    }

    /** Optional/update permission dialog: plain Allow / Deny. */
    private GeckoResult<AllowOrDeny> askAddonAllowDeny(String title,
                                                       String[] permissions, String[] origins) {
        final GeckoResult<AllowOrDeny> out = new GeckoResult<>();
        StringBuilder body = new StringBuilder();
        for (String p : permissions) body.append("\u2022 ").append(p).append('\n');
        for (String o : origins) body.append("\u2022 ").append(o).append('\n');
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body.length() == 0 ? getString(R.string.addons_permissions_none)
                        : body.toString())
                .setPositiveButton(R.string.perm_allow, (d, w) -> out.complete(AllowOrDeny.ALLOW))
                .setNegativeButton(R.string.perm_deny, (d, w) -> out.complete(AllowOrDeny.DENY))
                .setOnCancelListener(d -> out.complete(AllowOrDeny.DENY))
                .show();
        return out;
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
            runtime.getSettings().setAllowInsecureConnections(httpsOnlyConstant());
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
        String type = o.optString("type");
        if ("count".equals(type)) {
            long n = o.optLong("blocked", 0);
            Tab t = tabs.currentTab();
            if (t != null) t.blockedOnPage += n;
            prefs.addTotalBlocked(n);
        } else if ("media-net".equals(type)) {
            // Network-sniffed media (background webRequest observer).
            String u = o.optString("url");
            if (u.isEmpty()) return;
            String tag = MediaRegistry.classifyUrl(u);
            if (tag == null) tag = mimeToTag(o.optString("mime"));
            if (tag == null) return;
            mediaRegistry.addNetwork(new MediaRegistry.Item(u, tag,
                    o.optString("mime", ""), "", o.optLong("size", -1)));
        } else if ("grab-result".equals(type)) {
            if (!o.optBoolean("ok", false)) {
                toast(R.string.media_blob_unavailable);
            }
        }
    }

    private static String mimeToTag(String mime) {
        if (mime == null || mime.isEmpty()) return null;
        String m = mime.toLowerCase();
        if (m.contains("mpegurl") || m.contains("dash+xml")) return MediaRegistry.TAG_HLS;
        if (m.startsWith("video/")) return MediaRegistry.TAG_VIDEO;
        if (m.startsWith("audio/")) return MediaRegistry.TAG_AUDIO;
        return null;
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
            openExternalLink(intent.getDataString());
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                String url = Utils.smartUrl(text, prefs);
                if (url != null) openExternalLink(url);
            }
        }
    }

    /**
     * Opens a link handed over by another app (chooser, share sheet) in the
     * space chosen under Settings → Links. Only http(s) is accepted; the
     * default space is the landing zone, so an incoming link never ends up
     * in an isolated profile the user did not explicitly pick.
     */
    private void openExternalLink(String url) {
        if (url == null) return;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) return;
        int mode = prefs.externalLinks();
        if (mode == Prefs.LINKS_ASK) {
            showLinkSpacePicker(u);
            return;
        }
        openLinkInSpace(u,
                mode == Prefs.LINKS_CURRENT ? spaces.activeId() : Spaces.DEFAULT_ID);
    }

    /** Space picker for the "Ask every time" external-link mode. */
    private void showLinkSpacePicker(final String url) {
        final java.util.List<Spaces.Space> all = spaces.all();
        if (all.isEmpty()) return;
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Spaces.Space sp = all.get(i);
            names[i] = sp.isolated ? sp.name + "  \u00b7  "
                    + getString(R.string.spaces_badge_isolated) : sp.name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.external_links_pick)
                .setItems(names, (d, which) -> openLinkInSpace(url, all.get(which).id))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Routes one external link into a space, honouring the engine's
     * one-profile-per-process rule. GeckoView runs exactly one engine
     * profile per process: tabs may only open on the shared default profile
     * while the active space is itself non-isolated. Anything else parks the
     * link and completes it right after the space-switch restart.
     */
    private void openLinkInSpace(String url, String spaceId) {
        Spaces.Space target = spaces.get(spaceId);
        if (target == null) target = spaces.active();
        boolean activeIsolated = SpaceRuntime.isIsolated(spaces.active());
        boolean sameTarget = target.id.equals(spaces.activeId());
        if (!activeIsolated && !SpaceRuntime.isIsolated(target)) {
            openTab(url, false, target.id); // shared default profile
            return;
        }
        if (sameTarget) {
            openTab(url, false, target.id); // already running on it
            return;
        }
        prefs.pendingLink(url);
        toast(getString(R.string.link_opening_in, target.name));
        switchSpace(target);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    // ---------- Tab lifecycle ----------

    private void openTab(String url, boolean incognito) {
        openTab(url, incognito, null);
    }

    /**
     * Opens a tab in a Zerium Space. New normal tabs join the active space
     * unless a target space is given (external links). Isolated spaces run
     * on the engine profile selected at process start — see
     * {@link #openLinkInSpace} for how links reach other isolated spaces.
     */
    private void openTab(String url, boolean incognito, String spaceId) {
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
        if (extActions != null) extActions.attachSession(session);

        Tab tab = tabs.add(new Tab(tabs.count() + System.identityHashCode(session), session, view, incognito));
        tab.spaceId = incognito ? Spaces.DEFAULT_ID
                : (spaceId != null && spaces.get(spaceId) != null ? spaceId : spaces.activeId());
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
        if (extActions != null) extActions.detachSession(tab.session);
        if (!tab.incognito && tab.spaceId != null) {
            pruneEmptyGroups(tab.spaceId, null);
            persistGroups();
        }
        if (tabs.count() == 0) {
            openTab(null, false);
            tabsAdapter.notifyDataSetChanged();
            if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
            syncDetail();
            return;
        }
        showCurrentWebView();
        updateChrome(tabs.currentTab());
        tabsAdapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
        syncDetail();
    }

    /** Closes the active space's tabs (the switcher's current view scope). */
    private void closeAllTabs() {
        closeActiveSpaceTabsInMemory();
        hideTabSwitcher();
        if (tabs.count() == 0) openTab(null, false);
        else {
            showCurrentWebView();
            updateChrome(tabs.currentTab());
        }
        tabsAdapter.notifyDataSetChanged();
        buildSpaceChips();
    }

    /** Destroys the in-memory tabs of the active space. */
    private void closeActiveSpaceTabsInMemory() {
        String scope = spaces.activeId();
        int i = 0;
        while (i < tabs.tabs().size()) {
            Tab t = tabs.tabs().get(i);
            if (!t.incognito && scope.equals(t.spaceId)) {
                webContainer.removeView(t.view);
                tabs.remove(t);
                t.destroy();
                if (extActions != null) extActions.detachSession(t.session);
            } else {
                i++;
            }
        }
        tabGroups.removeSpace(scope);
        persistGroups();
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
                    if (changed) mediaRegistry.clear(tab.id);
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
                final boolean onlyPassword = authOnlyPassword(prompt);
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
                        final int chosen = value;
                        allowNotifications(perm).then(v -> {
                            result.complete(v == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                    ? chosen
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

    /** True when the auth prompt only wants a password (AuthOptions flag). */
    private static boolean authOnlyPassword(GeckoSession.PromptDelegate.AuthPrompt prompt) {
        try {
            return (prompt.authOptions.flags
                    & GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0;
        } catch (Exception e) {
            return false;
        }
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

    /** Per-session message delegate that receives reader articles and media
     *  grabber traffic from the shield content script (sender.session = tab). */
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
                            String type = o.optString("type");
                            if ("reader".equals(type)) {
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
                            handleTabMediaMessage(tab, o, type);
                            return null;
                        }
                    }, NATIVE_APP);
        } catch (Exception ignored) {}
    }

    /** Media grabber traffic from the per-tab content script. */
    private void handleTabMediaMessage(Tab tab, JSONObject o, String type) {
        switch (type) {
            case "media": {
                JSONArray items = o.optJSONArray("items");
                if (items == null) return;
                for (int i = 0; i < items.length() && i < 50; i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;
                    String u = it.optString("url", "");
                    if (u.isEmpty()) continue;
                    mediaRegistry.add(tab.id, new MediaRegistry.Item(
                            u, it.optString("tag", "video"), it.optString("mime", ""),
                            it.optString("label", ""), it.optLong("size", -1)));
                }
                break;
            }
            case "blob-begin": {
                String name = (tab.title == null || tab.title.isEmpty()
                        ? Utils.hostOf(tab.url) : tab.title);
                if (name == null || name.isEmpty()) name = "capture";
                name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                String mime = o.optString("mime", "");
                String ext = mime.contains("video/") ? ".mp4"
                        : mime.contains("audio/") ? ".m4a" : ".bin";
                engine.beginBlob(o.optString("sid", ""), o.optLong("size", -1),
                        mime, name + ext);
                break;
            }
            case "blob-chunk": {
                String sid = o.optString("sid", "");
                String b64 = o.optString("b64", "");
                if (sid.isEmpty() || b64.isEmpty()) return;
                try {
                    File f = engine.blobFile(sid);
                    if (f == null) return;
                    byte[] data = Base64.getDecoder().decode(b64);
                    try (FileOutputStream os = new FileOutputStream(f, true)) {
                        os.write(data);
                    }
                    engine.blobProgress(sid, f.length());
                } catch (Exception e) {
                    engine.endBlob(sid, "write-failed");
                }
                break;
            }
            case "blob-end": {
                String err = o.optString("error", "");
                engine.endBlob(o.optString("sid", ""), err);
                if ("too-large".equals(err)) {
                    runOnUiThread(() -> toast(R.string.dl_blob_too_large));
                }
                break;
            }
            default:
                break;
        }
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

    /** Brave-style start page: centered search, privacy-stats card, favorite
     *  tiles with favicon overlay, DuckDuckGo suggestions while typing. */
    private String startPageHtml() {
        String action = Utils.engineAt(prefs, prefs.searchEngine()).query;
        // Tiles: bookmarks first, then most-visited hosts from local history.
        StringBuilder tiles = new StringBuilder();
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        try {
            for (BookmarksDB.Entry b : bookmarks.all()) {
                String host = Utils.hostOf(b.url);
                if (host == null || host.isEmpty() || !used.add(host)) continue;
                tiles.append(tileHtml(host, host, "https://", ""));
                if (used.size() >= 8) break;
            }
        } catch (Exception ignored) {}
        try {
            for (String host : history.topHosts(8)) {
                if (host == null || host.isEmpty() || !used.add(host)) continue;
                tiles.append(tileHtml(host, host, "https://", ""));
                if (used.size() >= 8) break;
            }
        } catch (Exception ignored) {}
        // Honest privacy-stats estimates (Brave's conservative formula).
        long blocked = prefs.totalBlocked();
        String dataSaved = humanBytes(blocked * 50L * 1024L);
        String timeSaved = humanTime(blocked * 50L);
        String blockedFmt = String.format(java.util.Locale.US, "%,d", blocked);
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:system-ui,sans-serif;background:#f6f7fb;color:#1b1b1f;"
                + "display:flex;flex-direction:column;align-items:center;"
                + "min-height:100vh;margin:0;padding:28px 24px 40px}"
                + ".logo{font-size:40px;font-weight:800;letter-spacing:-1.5px;background:"
                + "linear-gradient(135deg,#4355b9,#7c9cff);-webkit-background-clip:text;"
                + "background-clip:text;color:transparent;margin-top:2vh}"
                + ".search{display:flex;align-items:center;background:#fff;border:1px solid #e2e2ea;"
                + "border-radius:28px;padding:4px 4px 4px 18px;width:100%;max-width:580px;margin-top:26px;"
                + "box-shadow:0 8px 28px rgba(20,25,60,.07);position:relative}"
                + "input{flex:1;min-width:0;padding:13px 12px;font-size:16px;border:0;outline:none;"
                + "background:transparent;color:#1b1b1f}"
                + "button{flex:none;border:0;border-radius:22px;padding:11px 22px;font-size:14px;"
                + "font-weight:600;color:#fff;background:#4355b9}"
                + ".sug{position:absolute;left:0;right:0;top:calc(100% + 6px);background:#fff;"
                + "border:1px solid #e2e2ea;border-radius:16px;box-shadow:0 12px 32px rgba(20,25,60,.12);"
                + "overflow:hidden;display:none;z-index:5;text-align:left}"
                + ".sug a{display:block;padding:11px 18px;font-size:14px;color:#1b1b1f;"
                + "text-decoration:none}.sug a:hover{background:#f3f3f8}"
                + ".note{font-size:11px;color:#9a9aa6;margin-top:8px;display:none}"
                + ".pstats{margin-top:30px;width:100%;max-width:580px;border-radius:20px;padding:16px 8px;"
                + "background:linear-gradient(135deg,rgba(67,85,185,.10),rgba(124,156,255,.10));"
                + "border:1px solid rgba(67,85,185,.14)}"
                + ".phead{display:flex;align-items:center;gap:8px;padding:0 14px 10px;font-size:13px;"
                + "color:#5f5f6b;font-weight:600}"
                + ".prow{display:flex}"
                + ".pcol{flex:1;text-align:center;padding:2px 6px}"
                + ".pnum{font-size:26px;font-weight:800}"
                + ".pnum.b{color:#e8710a}.pnum.d{color:#7c9cff}.pnum.t{color:#4355b9}"
                + ".plbl{font-size:11px;color:#5f5f6b;margin-top:3px;line-height:1.35}"
                + ".tiles{display:grid;grid-template-columns:repeat(4,minmax(64px,86px));gap:10px;"
                + "margin-top:26px;width:100%;max-width:580px}"
                + ".tile{display:flex;flex-direction:column;align-items:center;gap:6px;text-decoration:none;"
                + "background:#fff;border:1px solid #e2e2ea;border-radius:16px;padding:12px 4px;"
                + "box-shadow:0 4px 14px rgba(20,25,60,.05)}"
                + ".ic{position:relative;width:34px;height:34px;border-radius:50%;overflow:hidden;"
                + "background:linear-gradient(135deg,#4355b9,#7c9cff);display:flex;align-items:center;"
                + "justify-content:center}"
                + ".ic .g{color:#fff;font-weight:700;font-size:15px}"
                + ".ic img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;"
                + "background:#fff}"
                + ".th{font-size:10px;color:#5f5f6b;max-width:100%;overflow:hidden;text-overflow:ellipsis;"
                + "white-space:nowrap}"
                + ".foot{margin-top:26px;font-size:11px;color:#9a9aa6;text-align:center;max-width:420px;line-height:1.5}"
                + "@media (prefers-color-scheme:dark){body{background:#0e1016;color:#e4e2e6}"
                + ".search{background:#171a23;border-color:#2a2d38}input{color:#e4e2e6}"
                + ".sug{background:#171a23;border-color:#2a2d38}.sug a{color:#e4e2e6}.sug a:hover{background:#1f232e}"
                + ".tile{background:#171a23;border-color:#2a2d38}"
                + ".pstats{background:linear-gradient(135deg,rgba(124,156,255,.10),rgba(67,85,185,.14));"
                + "border-color:rgba(124,156,255,.18)}"
                + ".phead{color:#9a9aa6}.plbl{color:#9a9aa6}.th{color:#9a9aa6}"
                + ".pnum.d{color:#7c9cff}.pnum.t{color:#b6c4ff}.ic img{background:#e4e2e6}}"
                + "</style></head><body>"
                + "<div class='logo'>Zerium&nbsp;G</div>"
                + "<form onsubmit='var q=document.getElementById(\"q\").value.trim();"
                + "if(q){var t=\"" + action + "\";location.href=t.replace(\"%s\",encodeURIComponent(q));}"
                + "return false'>"
                + "<div class='search'><input id='q' type='search' placeholder='"
                + getString(R.string.search_hint) + "' autocomplete='off'><button type='submit'>Go</button>"
                + "<div class='sug' id='sug'></div></div>"
                + "<div class='note' id='note'>" + escHtml(getString(R.string.start_suggestions_note))
                + "</div></form>"
                + "<div class='pstats'>"
                + "<div class='phead'>\uD83D\uDEE1 " + escHtml(getString(R.string.stats_title)) + "</div>"
                + "<div class='prow'>"
                + "<div class='pcol'><div class='pnum b'>" + blockedFmt + "</div>"
                + "<div class='plbl'>" + escHtml(getString(R.string.stats_blocked)) + "</div></div>"
                + "<div class='pcol'><div class='pnum d'>" + escHtml(dataSaved) + "</div>"
                + "<div class='plbl'>" + escHtml(getString(R.string.stats_data)) + "</div></div>"
                + "<div class='pcol'><div class='pnum t'>" + escHtml(timeSaved) + "</div>"
                + "<div class='plbl'>" + escHtml(getString(R.string.stats_time)) + "</div></div>"
                + "</div></div>"
                + (tiles.length() > 0 ? "<div class='tiles'>" + tiles + "</div>" : "")
                + "<div class='foot'>" + escHtml(getString(R.string.stats_note)) + "</div>"
                + suggestionJs()
                + "</body></html>";
    }

    private static String tileHtml(String host, String label, String scheme, String extra) {
        String letter = host.isEmpty() ? "?" : host.substring(0, 1).toUpperCase();
        String safeHost = escHtml(android.net.Uri.encode(host));
        String safeLabel = escHtml(label.length() > 18 ? label.substring(0, 17) + "\u2026" : label);
        String safeLetter = escHtml(letter);
        return "<a class='tile' href='" + scheme + safeHost + "/'>"
                + "<span class='ic'><span class='g'>" + safeLetter + "</span>"
                + "<img src='" + scheme + safeHost + "/favicon.ico' loading='lazy' onerror=\"this.remove()\"></span>"
                + "<span class='th'>" + safeLabel + "</span></a>";
    }

    /** DuckDuckGo typeahead for the start-page search box (only fires while
     *  the user types there; fails silently offline). */
    private static String suggestionJs() {
        return "<script>(function(){var i=document.getElementById('q'),s=document.getElementById('sug'),"
                + "n=document.getElementById('note'),t=null;"
                + "if(!i||!s)return;"
                + "function esc(x){return x.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}"
                + "i.addEventListener('input',function(){var q=i.value.trim();clearTimeout(t);"
                + "if(!q){s.style.display='none';n.style.display='none';return}"
                + "t=setTimeout(function(){fetch('https://duckduckgo.com/ac/?q='+encodeURIComponent(q)+'&type=list')"
                + ".then(function(r){return r.json()}).then(function(d){var list=(d&&d[1])||[];"
                + "var h='';for(var k=0;k<list.length&&k<6;k++){h+='<a href=\"#\" data-q=\"'"
                + "+esc(list[k])+'\">'+esc(list[k])+'</a>'}"
                + "if(h){s.innerHTML=h;s.style.display='block';n.style.display='block'}"
                + "else{s.style.display='none';n.style.display='none'}})"
                + ".catch(function(){s.style.display='none';n.style.display='none'})},140)});"
                + "s.addEventListener('click',function(e){var a=e.target.closest('a');if(!a)return;"
                + "e.preventDefault();i.value=a.getAttribute('data-q');"
                + "var f=i.closest('form');if(f)f.requestSubmit?f.requestSubmit():f.onsubmit({preventDefault:function(){}})})})()"
                + "</script>";
    }

    /** Honest estimate strings for the privacy-stats card. */
    private static String humanBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0);
    }

    private static String humanTime(long ms) {
        if (ms <= 0) return "0s";
        if (ms < 60_000) return (ms / 1000) + "s";
        long min = ms / 60_000;
        if (min < 60) return min + "m";
        return (min / 60) + "h";
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
        // Add-on action availability depends on the active tab.
        if (extActions != null) extActions.syncToolbar();
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

    // ---------- Add-on toolbar actions ----------

    private void onExtensionsClicked(View anchor) {
        if (extActions != null) extActions.showActionList(anchor);
    }

    @Override
    public GeckoSession activeSession() {
        Tab t = tabs.currentTab();
        return t == null ? null : t.session;
    }

    @Override
    public boolean activeIncognito() {
        Tab t = tabs.currentTab();
        return t != null && t.incognito;
    }

    @Override
    public boolean jsEnabled() {
        return prefs.javascriptEnabled();
    }

    @Override
    public void setExtensionsVisible(boolean visible) {
        if (btnExtensions != null) {
            btnExtensions.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public View extensionsAnchor() {
        return btnExtensions;
    }

    // ---------- Menu ----------

    private void showMenu(View anchor) {
        if (extActions != null) extActions.dismissAll();
        Tab t = tabs.currentTab();
        boolean onPage = t != null && !isStartPage(t);
        java.util.List<MenuSheet.Entry> entries = new ArrayList<>();
        // Quick actions (Brave-style circular row).
        entries.add(MenuSheet.action(MENU_BACK, R.string.qa_back, R.drawable.ic_back,
                t != null && t.canGoBack));
        entries.add(MenuSheet.action(MENU_FORWARD, R.string.qa_forward, R.drawable.ic_forward,
                t != null && t.canGoForward));
        entries.add(MenuSheet.action(MENU_RELOAD, R.string.qa_refresh, R.drawable.ic_refresh, onPage));
        entries.add(MenuSheet.action(MENU_SHARE_QA, R.string.qa_share, R.drawable.ic_share, onPage));
        entries.add(MenuSheet.divider());
        // Browsing
        entries.add(MenuSheet.item(MENU_NEW_TAB, R.string.menu_new_tab, R.drawable.ic_plus));
        entries.add(MenuSheet.item(MENU_NEW_INCOGNITO, R.string.menu_new_incognito, R.drawable.ic_incognito));
        entries.add(MenuSheet.item(MENU_SPACES, R.string.menu_spaces, R.drawable.ic_spaces));
        entries.add(MenuSheet.divider());
        entries.add(MenuSheet.item(MENU_BOOKMARK_ADD, isCurrentBookmarked()
                        ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark,
                R.drawable.ic_bookmark, onPage));
        entries.add(MenuSheet.item(MENU_BOOKMARKS, R.string.menu_bookmarks, R.drawable.ic_bookmark));
        entries.add(MenuSheet.item(MENU_HISTORY, R.string.menu_history, R.drawable.ic_history));
        entries.add(MenuSheet.item(MENU_DOWNLOADS, R.string.menu_downloads, R.drawable.ic_download));
        entries.add(MenuSheet.item(MENU_MEDIA, R.string.menu_media, R.drawable.ic_video,
                onPage && t != null
                        && (mediaRegistry.count(t.id) > 0 || !mediaRegistry.networkItems().isEmpty())));
        entries.add(MenuSheet.item(MENU_FIND, R.string.menu_find, R.drawable.ic_search, onPage));
        entries.add(MenuSheet.divider());
        // Page tools
        entries.add(MenuSheet.item(MENU_READER, t != null && t.readerActive
                        ? R.string.menu_reader_exit : R.string.menu_reader,
                R.drawable.ic_reader, t != null && (t.readerActive || t.readerArticle != null)));
        entries.add(MenuSheet.check(MENU_DESKTOP, R.string.menu_desktop, R.drawable.ic_desktop,
                t != null && isDesktop(t)));
        entries.add(MenuSheet.check(MENU_JS, R.string.menu_javascript, R.drawable.ic_code,
                t != null && t.session.getSettings().getAllowJavascript()));
        entries.add(MenuSheet.item(MENU_TRANSLATE, R.string.menu_translate, R.drawable.ic_translate, onPage));
        entries.add(MenuSheet.item(MENU_PRINT, R.string.menu_print, R.drawable.ic_print, onPage));
        entries.add(MenuSheet.item(MENU_SAVE_PDF, R.string.menu_save_pdf, R.drawable.ic_pdf, onPage));
        entries.add(MenuSheet.item(MENU_ALLOW_SITE, R.string.menu_allow_site, R.drawable.ic_allow, onPage));
        entries.add(MenuSheet.item(MENU_BLOCK_INFO, R.string.menu_block_info, R.drawable.ic_shield));
        entries.add(MenuSheet.divider());
        // App
        entries.add(MenuSheet.item(MENU_ADDONS, R.string.menu_addons, R.drawable.ic_extension));
        entries.add(MenuSheet.item(MENU_DELETE_DATA, R.string.menu_delete_data, R.drawable.ic_trash));
        entries.add(MenuSheet.item(MENU_SETTINGS, R.string.menu_settings, R.drawable.ic_gear));
        entries.add(MenuSheet.item(MENU_EXIT, R.string.menu_exit, R.drawable.ic_exit));
        MenuSheet.show(this, entries, e -> handleMenu(e.id));
    }

    /**
     * Bottom task bar menu: a compact quick-actions sheet, deliberately
     * different from the top full menu. Everything here is one-thumb
     * territory — new tabs, spaces, destinations and the most common page
     * toggles — while the top menu keeps the complete list (quick nav row,
     * page tools, add-ons, app items). Both routes share handleMenu(int).
     */
    private void showBottomMenu(View anchor) {
        if (extActions != null) extActions.dismissAll();
        Tab t = tabs.currentTab();
        boolean onPage = t != null && !isStartPage(t);
        java.util.List<MenuSheet.Entry> entries = new ArrayList<>();
        // Quick actions (Brave-style circular row): the bottom bar already
        // carries back/forward/home/tabs, so this row is tab + space making.
        entries.add(MenuSheet.action(MENU_NEW_TAB, R.string.menu_new_tab, R.drawable.ic_plus));
        entries.add(MenuSheet.action(MENU_NEW_INCOGNITO, R.string.menu_new_incognito,
                R.drawable.ic_incognito));
        entries.add(MenuSheet.action(MENU_SPACES, R.string.menu_spaces, R.drawable.ic_spaces));
        entries.add(MenuSheet.action(MENU_DOWNLOADS, R.string.menu_downloads, R.drawable.ic_download));
        entries.add(MenuSheet.divider());
        entries.add(MenuSheet.item(MENU_BOOKMARK_ADD, isCurrentBookmarked()
                        ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark,
                R.drawable.ic_bookmark, onPage));
        entries.add(MenuSheet.item(MENU_BOOKMARKS, R.string.menu_bookmarks, R.drawable.ic_bookmark));
        entries.add(MenuSheet.item(MENU_HISTORY, R.string.menu_history, R.drawable.ic_history));
        entries.add(MenuSheet.item(MENU_MEDIA, R.string.menu_media, R.drawable.ic_video,
                onPage && t != null
                        && (mediaRegistry.count(t.id) > 0 || !mediaRegistry.networkItems().isEmpty())));
        entries.add(MenuSheet.divider());
        entries.add(MenuSheet.item(MENU_FIND, R.string.menu_find, R.drawable.ic_search, onPage));
        entries.add(MenuSheet.check(MENU_DESKTOP, R.string.menu_desktop, R.drawable.ic_desktop,
                t != null && isDesktop(t)));
        entries.add(MenuSheet.item(MENU_READER, t != null && t.readerActive
                        ? R.string.menu_reader_exit : R.string.menu_reader,
                R.drawable.ic_reader, t != null && (t.readerActive || t.readerArticle != null)));
        entries.add(MenuSheet.divider());
        entries.add(MenuSheet.item(MENU_SETTINGS, R.string.menu_settings, R.drawable.ic_gear));
        MenuSheet.show(this, entries, e -> handleMenu(e.id));
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
            case MENU_SPACES: showSpacesSheet(); break;
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
                startActivity(new Intent(this, com.zerium.gecko.ui.DownloadsActivity.class));
                break;
            case MENU_MEDIA:
                if (t != null) {
                    MediaGrabberSheet.show(getSupportFragmentManager(), t.id);
                }
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
            case MENU_ADDONS:
                startActivity(new Intent(this, ExtensionsActivity.class));
                break;
            case MENU_DELETE_DATA: deleteBrowsingData(); break;
            case MENU_BACK: {
                if (t != null && t.canGoBack) t.session.goBack();
                break;
            }
            case MENU_FORWARD: {
                if (t != null && t.canGoForward) t.session.goForward();
                break;
            }
            case MENU_RELOAD: {
                if (t != null && !isStartPage(t)) t.session.reload();
                break;
            }
            case MENU_SHARE_QA: {
                if (t != null && !isStartPage(t)) {
                    Intent qa = new Intent(Intent.ACTION_SEND);
                    qa.setType("text/plain");
                    qa.putExtra(Intent.EXTRA_TEXT, pageUrl(t));
                    startActivity(Intent.createChooser(qa, getString(R.string.menu_share)));
                }
                break;
            }
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

    /** Brave-style "Delete browsing data": history, cookies/site data and
     *  engine caches, via the engine StorageController. Scope: the running
     *  engine profile — the active space's data. Other isolated spaces keep
     *  their own profile directories untouched. */
    private void deleteBrowsingData() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        boolean anyIsolated = false;
        for (Spaces.Space sp : spaces.all()) anyIsolated |= sp.isolated;
        if (anyIsolated) {
            TextView scope = new TextView(this);
            scope.setText(R.string.spaces_delete_scope);
            scope.setTextSize(12.5f);
            scope.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            box.addView(scope);
        }
        android.widget.CheckBox historyBox = new android.widget.CheckBox(this);
        historyBox.setText(R.string.delete_data_history);
        historyBox.setChecked(true);
        android.widget.CheckBox siteBox = new android.widget.CheckBox(this);
        siteBox.setText(R.string.delete_data_site_data);
        siteBox.setChecked(true);
        android.widget.CheckBox cacheBox = new android.widget.CheckBox(this);
        cacheBox.setText(R.string.delete_data_cache);
        cacheBox.setChecked(true);
        box.addView(historyBox);
        box.addView(siteBox);
        box.addView(cacheBox);
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_data_title)
                .setView(box)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (historyBox.isChecked()) new HistoryDB(this).clear();
                    long flags = 0;
                    if (siteBox.isChecked()) flags |= StorageController.ClearFlags.COOKIES
                            | StorageController.ClearFlags.DOM_STORAGES
                            | StorageController.ClearFlags.AUTH_SESSIONS;
                    if (cacheBox.isChecked()) flags |= StorageController.ClearFlags.ALL_CACHES;
                    if (flags != 0) {
                        runtime.getStorageController().clearData(flags).then(v -> {
                            runOnUiThread(() -> toast(R.string.delete_data_done));
                            return null;
                        });
                    } else {
                        toast(R.string.delete_data_done);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
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

    /** Hands the URL to the local download engine (turbo segments + HLS). */
    private void downloadViaShield(String url) {
        try {
            ensureNotifPermission();
            Tab t = tabs.currentTab();
            String tag = MediaRegistry.classifyUrl(url);
            engine.enqueue(url, Utils.fileNameFromUrl(url), "",
                    null, url, t != null ? t.url : "", t != null ? t.title : "",
                    MediaRegistry.TAG_HLS.equals(tag), prefs.turboDownloads());
            toast(R.string.download_started);
        } catch (Exception e) {
            toast(R.string.download_failed_generic);
        }
    }

    /** Entry point for media-grabber rows (videos, streams, files, blobs). */
    @Override
    public void onMediaDownloadRequested(MediaRegistry.Item item) {
        if (item == null || item.url == null) return;
        if (MediaRegistry.TAG_BLOB.equals(item.tag)) {
            captureBlob(item.url);
            return;
        }
        startTurboDownload(item.url, MediaRegistry.TAG_HLS.equals(item.tag), item.mime);
    }

    private void startTurboDownload(String url, boolean hls, String mime) {
        try {
            ensureNotifPermission();
            Tab t = tabs.currentTab();
            engine.enqueue(url, Utils.fileNameFromUrl(url), mime,
                    null, url, t != null ? t.url : "", t != null ? t.title : "",
                    hls, prefs.turboDownloads());
            toast(R.string.download_started);
        } catch (Exception e) {
            toast(R.string.download_failed_generic);
        }
    }

    /** Asks the shield background to relay a capture request to the page. */
    private void captureBlob(String blobUrl) {
        Tab t = tabs.currentTab();
        if (t == null || shieldPort == null) {
            toast(R.string.media_blob_unavailable);
            return;
        }
        try {
            String sid = Long.toString(System.currentTimeMillis(), 36);
            JSONObject msg = new JSONObject();
            msg.put("type", "media-grab");
            msg.put("url", blobUrl.replace("'", ""));
            msg.put("sid", sid);
            msg.put("limit", 300);
            shieldPort.postMessage(msg);
            toast(R.string.download_started);
        } catch (Exception e) {
            toast(R.string.media_blob_unavailable);
        }
    }

    private void ensureNotifPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(new String[]{android.Manifest.permission.POST_NOTIFICATIONS});
        }
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
            String finalUri = item != null ? item.toString()
                    : (legacy != null ? legacy.getAbsolutePath() : "");
            engine.recordCompleted(response.uri, name, mime, finalUri, written);
            toast(getString(R.string.download_saved,
                    legacy != null ? legacy.getAbsolutePath() : name));
        } catch (Exception e) {
            toast(R.string.download_failed_generic);
        }
    }

    // ---------- Fullscreen ----------

    private void enterFullScreen(GeckoSession session) {
        if (fullscreenView != null) return;
        if (extActions != null) extActions.dismissAll();
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
        if (extActions != null) extActions.dismissAll();
        Tab current = tabs.currentTab();
        if (current != null && !current.incognito && !isStartPage(current)) capturePreview(current);
        tabSearch.setText("");
        noMatchingTabs.setVisibility(View.GONE);
        buildSpaceChips();
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
        closeGroupDetail();
        tabSwitcher.setVisibility(View.GONE);
    }

    // ---------- Session persistence ----------

    /**
     * Restores the active space's tabs from the per-space archive. A legacy
     * pre-Spaces session (prefs.savedTabs) migrates into the current
     * space's archive once.
     */
    private void restoreSession() {
        int index = prefs.savedTabIndex();
        int opened = 0;
        java.util.List<SpaceTabArchive.Entry> list = tabArchive.tabsFor(spaces.activeId());
        if (list.isEmpty()) {
            String legacy = prefs.savedTabs();
            if (legacy != null && legacy.startsWith("[")) {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(legacy);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String u = o.optString("u", "");
                        String st = o.optString("s", "");
                        if (u.isEmpty() && st.isEmpty()) continue;
                        tabArchive.append(spaces.activeId(), new SpaceTabArchive.Entry(u, st));
                    }
                    prefs.spacesTabsJson(tabArchive.dump());
                    list = tabArchive.tabsFor(spaces.activeId());
                } catch (Exception ignored) {}
            }
        }
        for (SpaceTabArchive.Entry e : list) {
            if (opened >= MAX_RESTORED_TABS) break;
            openTab(e.url.isEmpty() ? null : e.url, false);
            Tab t = tabs.tabs().get(tabs.count() - 1);
            if (!e.state.isEmpty()) {
                try {
                    GeckoSession.SessionState state =
                            GeckoSession.SessionState.fromString(e.state);
                    if (state != null) t.session.restoreState(state);
                } catch (Exception ignored) {}
            }
            // Re-bind the tab's group (group must exist in this space).
            if (e.groupId != null && tabGroups.belongsTo(e.groupId, spaces.activeId())) {
                t.groupId = e.groupId;
            }
            opened++;
        }
        if (opened == 0) {
            openTab(null, false);
        } else {
            tabs.setCurrent(Math.max(0, Math.min(index, opened - 1)));
            showCurrentWebView();
            updateChrome(tabs.currentTab());
        }
        // A link parked for this space (isolated-space handoff) opens last
        // and lands focused.
        String link = prefs.pendingLink();
        if (link != null && !link.isEmpty()) {
            prefs.pendingLink("");
            openTab(link, false);
        }
    }

    /** Saves every non-incognito tab into its space's archive (the active
     *  space's list is replaced wholesale; other spaces keep theirs). */
    private void saveSession() {
        try {
            String activeId = spaces.activeId();
            java.util.Map<String, java.util.List<SpaceTabArchive.Entry>> groups =
                    new java.util.LinkedHashMap<>();
            int current = 0;
            int activeIdx = 0;
            for (int i = 0; i < tabs.tabs().size(); i++) {
                Tab t = tabs.tabs().get(i);
                if (t.incognito) continue;
                String spId = t.spaceId == null ? Spaces.DEFAULT_ID : t.spaceId;
                String u = t.url == null ? "" : t.url;
                if (u.startsWith("data:") || u.startsWith("file:")) u = "";
                if (u.isEmpty() && t.state == null) continue;
                java.util.List<SpaceTabArchive.Entry> group = groups.get(spId);
                if (group == null) {
                    group = new java.util.ArrayList<>();
                    groups.put(spId, group);
                }
                group.add(new SpaceTabArchive.Entry(u,
                        t.state != null ? t.state.toString() : "",
                        t.groupId != null && tabGroups.belongsTo(t.groupId, spId)
                                ? t.groupId : null));
                if (i == tabs.current() && activeId.equals(spId)) current = activeIdx;
                if (activeId.equals(spId)) activeIdx++;
            }
            for (java.util.Map.Entry<String, java.util.List<SpaceTabArchive.Entry>> en
                    : groups.entrySet()) {
                tabArchive.setTabs(en.getKey(), en.getValue());
            }
            prefs.spacesTabsJson(tabArchive.dump());
            prefs.savedTabIndex(current);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
        persistGroups();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteReaderFile();
        if (extActions != null) extActions.teardown();
        runtimeForAddons = null;
        addonListRefresh = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed: color scheme, text size, content locale,
        // cookie banners, the global JS default and the shield allowlist.
        applyRuntimeSettings();
        pushShieldConfig();
        // Add-ons may have been installed or toggled on the Add-ons screen.
        if (extActions != null) extActions.refresh();
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

    // ---------- Zerium Spaces ----------

    private void persistSpaces() {
        prefs.spacesJson(spaces.dump());
    }

    private void persistArchive() {
        prefs.spacesTabsJson(tabArchive.dump());
    }

    private void persistGroups() {
        prefs.tabGroupsJson(tabGroups.dump());
    }

    /** Open normal tabs of the active space (only they live in memory). */
    private int countForSpace(String spaceId) {
        int n = 0;
        for (Tab t : tabs.tabs()) {
            if (!t.incognito && spaceId.equals(t.spaceId)) n++;
        }
        return n;
    }

    /** Total live tabs of a space: in-memory for the active space,
     *  archived (url + session state) for the others. */
    private int spaceTabCount(String spaceId) {
        return spaceId.equals(spaces.activeId())
                ? countForSpace(spaceId) : tabArchive.countFor(spaceId);
    }

    /** Rebuilds the switcher's space chip row. Long-press opens the manager. */
    private void buildSpaceChips() {
        if (spaceChips == null) return;
        spaceChips.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        String activeId = spaces.activeId();
        for (final Spaces.Space sp : spaces.all()) {
            TextView chip = spaceChip(sp.name + "  \u00b7  " + spaceTabCount(sp.id),
                    sp.id.equals(activeId), d,
                    () -> switchSpace(sp),
                    () -> showSpacesSheet());
            chip.setTag(sp.id); // drag-a-tab-onto-chip target (see resolveDrop)
            spaceChips.addView(chip);
        }
        spaceChips.addView(spaceChip("\u002b " + getString(R.string.spaces_new), false, d,
                this::showNewSpaceDialog, null));
    }

    private TextView spaceChip(String text, boolean active, float d,
                               Runnable onClick, Runnable onLongClick) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setSingleLine(true);
        chip.setPadding((int) (13 * d), (int) (6 * d), (int) (13 * d), (int) (6 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(ContextCompat.getColor(this, active
                ? R.color.accent : R.color.surface_container_high));
        chip.setBackground(bg);
        chip.setTextColor(ContextCompat.getColor(this, active
                ? R.color.on_accent : R.color.on_surface));
        chip.setOnClickListener(v -> onClick.run());
        if (onLongClick != null) {
            chip.setOnLongClickListener(v -> {
                onLongClick.run();
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) (8 * d), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    /**
     * Switches to a space. GeckoView supports one engine runtime per
     * process, so the browser restarts into the target space: tabs persist
     * to the archive, the engine relaunches with the space's profile
     * directory, and its archived tabs restore with cookies intact.
     */
    private void switchSpace(Spaces.Space sp) {
        if (sp == null || sp.id.equals(spaces.activeId())) return;
        closeGroupDetail(); // a group detail always belongs to one space
        saveSession();
        spaces.setActive(sp.id);
        persistSpaces();
        tabsAdapter.setActiveSpaceId(sp.id);
        toast(getString(R.string.spaces_switch_restarting, sp.name));
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent relaunch = new Intent(this, MainActivity.class);
                relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(relaunch);
                finishAffinity();
            } catch (Exception ignored) {
            } finally {
                Runtime.getRuntime().exit(0);
            }
        }, 350);
    }

    private void showSpacesSheet() {
        SpacesSheet.show(this, new SpacesSheet.Host() {
            @Override
            public Spaces spaces() {
                return spaces;
            }

            @Override
            public int tabCount(String spaceId) {
                return spaceTabCount(spaceId);
            }

            @Override
            public String footerText() {
                return getString(R.string.spaces_footer_gecko);
            }

            @Override
            public void onSwitch(Spaces.Space space) {
                switchSpace(space);
            }

            @Override
            public void onNewSpace() {
                showNewSpaceDialog();
            }

            @Override
            public void onRename(Spaces.Space space) {
                showRenameDialog(space);
            }

            @Override
            public void onDelete(Spaces.Space space) {
                deleteSpace(space);
            }
        });
    }

    private void showNewSpaceDialog() {
        SpacesSheet.promptNewSpace(this, true, (name, isolated) -> {
            Spaces.Space sp = spaces.create(name, isolated);
            if (sp == null) return;
            persistSpaces();
            buildSpaceChips();
            toast(R.string.spaces_created);
        });
    }

    private void showRenameDialog(final Spaces.Space sp) {
        SpacesSheet.promptRename(this, sp.name, (name, unused) -> {
            spaces.rename(sp.id, name);
            persistSpaces();
            buildSpaceChips();
        });
    }

    private void deleteSpace(final Spaces.Space sp) {
        if (sp == null || sp.id.equals(Spaces.DEFAULT_ID)) {
            toast(R.string.spaces_default_locked);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.spaces_delete_title, sp.name))
                .setMessage(getString(R.string.spaces_delete_confirm,
                        sp.name, spaceTabCount(sp.id)))
                .setPositiveButton(R.string.delete, (dlg, w) -> reallyDeleteSpace(sp))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void reallyDeleteSpace(Spaces.Space sp) {
        boolean isActive = sp.id.equals(spaces.activeId());
        tabArchive.remove(sp.id);
        persistArchive();
        tabGroups.removeSpace(sp.id);
        persistGroups();
        spaces.remove(sp.id);
        persistSpaces();
        if (isActive && SpaceRuntime.isIsolated(sp)) {
            // The engine runs with this space's profile directory: switch to
            // the default space now, wipe the directory after the restart.
            String pending = prefs.pendingSpaceDirs();
            if (!pending.contains(sp.id)) {
                prefs.pendingSpaceDirs(pending.isEmpty() ? sp.id : pending + "," + sp.id);
            }
            toast(getString(R.string.spaces_switch_restarting, spaces.active().name));
            restartForSpace();
            return;
        }
        if (SpaceRuntime.isIsolated(sp)) SpaceRuntime.deleteDir(this, sp.id);
        if (isActive) {
            tabsAdapter.setActiveSpaceId(spaces.activeId());
            closeActiveSpaceTabsInMemory();
        }
        if (tabs.count() == 0) openTab(null, false);
        else {
            showCurrentWebView();
            updateChrome(tabs.currentTab());
        }
        tabsAdapter.notifyDataSetChanged();
        buildSpaceChips();
        toast(R.string.spaces_deleted);
    }

    /** Process restart used for space switches (single runtime per process). */
    private void restartForSpace() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent relaunch = new Intent(this, MainActivity.class);
                relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(relaunch);
                finishAffinity();
            } catch (Exception ignored) {
            } finally {
                Runtime.getRuntime().exit(0);
            }
        }, 350);
    }

    /** Long-press on a tab card: move it to another space. Cross-profile
     *  moves archive the tab (it opens under the target space's profile on
     *  switch); same-profile moves just re-tag it. */
    private void showMoveTabDialog(final Tab t) {
        if (t.incognito) {
            toast(R.string.spaces_no_incognito);
            return;
        }
        final java.util.List<Spaces.Space> targets = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Spaces.Space sp : spaces.all()) {
            if (sp.id.equals(t.spaceId)) continue;
            targets.add(sp);
            names.add(sp.isolated ? sp.name + "  \u00b7  "
                    + getString(R.string.spaces_badge_isolated) : sp.name);
        }
        if (targets.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.spaces_move_title)
                .setMessage(R.string.spaces_move_note)
                .setItems(names.toArray(new String[0]), (dlg, which) ->
                        moveTabToSpace(t, targets.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moveTabToSpace(Tab t, Spaces.Space target) {
        if (t == null || target == null || target.id.equals(t.spaceId)) return;
        Spaces.Space source = spaces.get(t.spaceId);
        boolean sameProfile = !SpaceRuntime.isIsolated(source)
                && !SpaceRuntime.isIsolated(target);
        if (sameProfile) {
            String oldSpace = t.spaceId;
            t.spaceId = target.id;
            t.groupId = null; // groups live inside one space and never follow
            pruneEmptyGroups(oldSpace, null);
            persistGroups();
            persistSpaces();
            refreshGroupUi();
            buildSpaceChips();
            return;
        }
        // Cross-profile: the session cannot hop engine profiles. Archive it
        // into the target space (restores there on switch, ungrouped) and
        // close it.
        String u = t.url == null ? "" : t.url;
        if (u.startsWith("data:") || u.startsWith("file:")) u = "";
        tabArchive.append(target.id, new SpaceTabArchive.Entry(u,
                t.state != null ? t.state.toString() : ""));
        persistArchive();
        closeTab(t);
        buildSpaceChips();
    }

    // ---------- Tab groups (inside Spaces) ----------

    private ItemTouchHelper buildDragHelper(final RecyclerView rv) {
        return new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rgv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // started from the card's long-press listener
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rgv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // ordering is session order; drag only groups
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) { }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                if (vh != null) {
                    draggedTab = tabAt(vh);
                    dragSourceRv = rv;
                    dragDistance = 0f;
                    vh.itemView.setAlpha(0.9f);
                    vh.itemView.setScaleX(1.04f);
                    vh.itemView.setScaleY(1.04f);
                } else {
                    if (draggedTab != null) {
                        resolveDrop();
                        // restore visuals (resolveDrop may have rebound rows)
                        refreshGroupUi();
                    }
                    setHover(hoverVH, false);
                    hoverVH = null;
                    draggedTab = null;
                    dragSourceRv = null;
                }
                super.onSelectedChanged(vh, actionState);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rgv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                if (isCurrentlyActive && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    updateDragPointer(rv, vh, dX, dY);
                    dragDistance = Math.max(dragDistance,
                            (float) Math.hypot(dX, dY));
                    updateHover(rv);
                }
                super.onChildDraw(c, rgv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(@NonNull RecyclerView rgv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                vh.itemView.setAlpha(1f);
                vh.itemView.setScaleX(1f);
                vh.itemView.setScaleY(1f);
                super.clearView(rgv, vh);
            }
        });
    }

    private static Tab tabAt(RecyclerView.ViewHolder vh) {
        Object tag = vh.itemView.getTag();
        return tag instanceof Tab ? (Tab) tag : null;
    }

    /** Screen-space centre of the dragged card, from layout pos + drag delta. */
    private void updateDragPointer(RecyclerView rv, RecyclerView.ViewHolder vh,
                                   float dX, float dY) {
        int[] loc = new int[2];
        rv.getLocationOnScreen(loc);
        dragLastX = loc[0] + vh.itemView.getLeft() + dX + vh.itemView.getWidth() / 2f;
        dragLastY = loc[1] + vh.itemView.getTop() + dY + vh.itemView.getHeight() / 2f;
    }

    private void updateHover(RecyclerView rv) {
        if (draggedTab == null) return;
        RecyclerView.ViewHolder h = hoverTargetAt(rv, dragLastX, dragLastY);
        if (h == hoverVH) return;
        setHover(hoverVH, false);
        hoverVH = h;
        setHover(hoverVH, true);
    }

    private void setHover(RecyclerView.ViewHolder h, boolean on) {
        if (h == null) return;
        View it = h.itemView;
        it.setAlpha(on ? 0.7f : 1f);
    }

    /** What the given screen point hovers over: a Tab card, a group card —
     *  or null for empty space (the ungroup drop zone). */
    private Object dropTargetAt(RecyclerView rv, float screenX, float screenY) {
        int[] loc = new int[2];
        rv.getLocationOnScreen(loc);
        View child = rv.findChildViewUnder(screenX - loc[0], screenY - loc[1]);
        if (child == null) return null;
        RecyclerView.ViewHolder h = rv.getChildViewHolder(child);
        if (draggedTab != null && tabAt(h) == draggedTab) return null;
        return child.getTag();
    }

    private RecyclerView.ViewHolder hoverTargetAt(RecyclerView rv,
                                                  float screenX, float screenY) {
        int[] loc = new int[2];
        rv.getLocationOnScreen(loc);
        View child = rv.findChildViewUnder(screenX - loc[0], screenY - loc[1]);
        if (child == null) return null;
        RecyclerView.ViewHolder h = rv.getChildViewHolder(child);
        if (draggedTab != null && tabAt(h) == draggedTab) return null;
        return h;
    }

    /** Space chip under the given screen point, or null. */
    private Spaces.Space chipAt(float screenX, float screenY) {
        if (spaceChips == null) return null;
        for (int i = 0; i < spaceChips.getChildCount(); i++) {
            View chip = spaceChips.getChildAt(i);
            Object tag = chip.getTag();
            if (!(tag instanceof String)) continue; // the "+ New space" chip
            int[] loc = new int[2];
            chip.getLocationOnScreen(loc);
            if (screenX >= loc[0] && screenX <= loc[0] + chip.getWidth()
                    && screenY >= loc[1] && screenY <= loc[1] + chip.getHeight()) {
                return spaces.get((String) tag);
            }
        }
        return null;
    }

    private void resolveDrop() {
        final Tab t = draggedTab;
        if (t == null) return;
        // Bare long-press (no real movement): the tab context menu.
        if (dragDistance < 20 * getResources().getDisplayMetrics().density) {
            showTabContextMenu(t);
            return;
        }
        Spaces.Space chip = chipAt(dragLastX, dragLastY);
        if (chip != null) {
            if (t.incognito) toast(R.string.spaces_no_incognito);
            else if (!chip.id.equals(t.spaceId)) moveTabToSpace(t, chip);
            return;
        }
        // Incognito tabs never group (they belong to no Space).
        if (t.incognito) {
            toast(R.string.spaces_no_incognito);
            return;
        }
        RecyclerView src = dragSourceRv != null ? dragSourceRv : tabsGrid;
        Object target = dropTargetAt(src, dragLastX, dragLastY);
        if (target instanceof Tab) {
            Tab o = (Tab) target;
            int action = GroupOps.decideTabDrop(t, o);
            if (action == GroupOps.DROP_JOIN_TARGET_GROUP) {
                TabGroups.Group og = tabGroups.get(o.groupId);
                if (og != null && GroupOps.mayJoin(t, og)) assignToGroup(t, og);
            } else if (action == GroupOps.DROP_UNGROUP) {
                ungroup(t);
            } else if (action == GroupOps.DROP_CREATE_PAIR) {
                createGroupWith(t, o);
            }
            return;
        }
        if (target instanceof TabGroups.Group) {
            TabGroups.Group g = (TabGroups.Group) target;
            if (GroupOps.mayJoin(t, g)) assignToGroup(t, g);
            return;
        }
        // Empty grid space: the ungroup drop zone (main overview and the
        // group-detail grid, where this is the drag-out gesture).
        if (t.groupId != null) ungroup(t);
    }

    /** Re-binds both grids after any group-metadata change. Grouping only
     *  moves {@code groupId}; no GeckoSession is ever recreated here. */
    private void refreshGroupUi() {
        tabsAdapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
        syncDetail();
    }

    /** Keeps the detail overlay consistent; closes it when the group is gone. */
    private void syncDetail() {
        if (detailGroup == null || groupDetail == null) return;
        TabGroups.Group live = tabGroups.get(detailGroup.id);
        if (live == null || !GroupOps.hasMember(tabs.tabs(), live.id)) {
            closeGroupDetail();
            return;
        }
        detailGroup = live;
        bindGroupDetailHeader(live);
    }

    // ---------- Group detail (dedicated grid) ----------

    /** Opens the dedicated group-detail grid for one group card tap. */
    private void showGroupDetail(TabGroups.Group g) {
        if (g == null || tabGroups.get(g.id) == null) return;
        if (!g.spaceId.equals(spaces.activeId())) return;
        detailGroup = g;
        groupAdapter.setDetailGroupId(g.id);
        groupAdapter.setActiveSpaceId(g.spaceId);
        bindGroupDetailHeader(g);
        groupDetail.setVisibility(View.VISIBLE);
        newTabFab.setVisibility(View.GONE);
        tabsAdapter.notifyDataSetChanged();
        groupAdapter.notifyDataSetChanged();
    }

    private void bindGroupDetailHeader(TabGroups.Group g) {
        int count = GroupRowPlanner.memberCount(tabs.tabs(), g);
        View dot = groupDetail.findViewById(R.id.groupDetailDot);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(groupColor(this, g.color));
        dot.setBackground(bg);
        TextView title = groupDetail.findViewById(R.id.groupDetailTitle);
        title.setText(g.name + "  \u00b7  "
                + getString(R.string.spaces_tab_count, count));
        TextView empty = groupDetail.findViewById(R.id.groupDetailEmpty);
        empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void closeGroupDetail() {
        detailGroup = null;
        if (groupDetail != null) groupDetail.setVisibility(View.GONE);
        if (newTabFab != null) newTabFab.setVisibility(View.VISIBLE);
        if (groupAdapter != null) groupAdapter.setDetailGroupId(null);
    }

    /**
     * The group-detail FAB: creates a tab that joins this group and the
     * current Space. The switcher and the detail grid stay open so the new
     * card is immediately visible. A brand-new tab, so a new session here is
     * expected — existing tabs keep their sessions untouched.
     */
    private void openTabInGroup(TabGroups.Group g) {
        if (g == null || tabGroups.get(g.id) == null) return;
        if (!g.spaceId.equals(spaces.activeId())) return;
        openTab(null, false, g.spaceId);
        Tab nt = tabs.tabs().get(tabs.count() - 1);
        nt.groupId = g.id;
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
        groupAdapter.notifyDataSetChanged();
        bindGroupDetailHeader(g);
    }

    // ---------- Tab context menu (add to group / move) ----------

    /** Bare long-press on a tab card: group actions + the Space move dialog. */
    private void showTabContextMenu(final Tab t) {
        if (t == null) return;
        if (t.incognito) {
            toast(R.string.spaces_no_incognito);
            return;
        }
        final java.util.List<CharSequence> items = new java.util.ArrayList<>();
        final java.util.List<Integer> actions = new java.util.ArrayList<>();
        if (t.groupId != null) {
            items.add(getString(R.string.menu_remove_from_group));
            actions.add(0);
        }
        items.add(getString(R.string.menu_add_to_group));
        actions.add(1);
        items.add(getString(R.string.spaces_move_title));
        actions.add(2);
        new AlertDialog.Builder(this)
                .setTitle(t.title == null || t.title.isEmpty() ? t.url : t.title)
                .setItems(items.toArray(new CharSequence[0]), (dlg, which) -> {
                    int a = which < actions.size() ? actions.get(which) : -1;
                    if (a == 0) ungroup(t);
                    else if (a == 1) showAddToGroupPicker(t);
                    else showMoveTabDialog(t);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** "Add to group": every group of the tab's space, or a new one. */
    private void showAddToGroupPicker(final Tab t) {
        java.util.List<TabGroups.Group> gs = tabGroups.forSpace(t.spaceId);
        java.util.List<CharSequence> names = new java.util.ArrayList<>();
        final java.util.List<TabGroups.Group> targets = new java.util.ArrayList<>();
        for (TabGroups.Group g : gs) {
            names.add(g.name);
            targets.add(g);
        }
        names.add(getString(R.string.menu_new_group));
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_add_to_group)
                .setItems(names.toArray(new CharSequence[0]), (dlg, which) -> {
                    if (which < targets.size()) {
                        assignToGroup(t, targets.get(which));
                    } else {
                        showCreateGroupDialog(t.spaceId,
                                java.util.Collections.singletonList(t));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Group creation dialog: editable title + the eight-colour palette. Used
     * by "Add to group → New group…" and by the group menu flows.
     */
    private void showCreateGroupDialog(final String spaceId,
                                       final java.util.List<Tab> members) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.group_name_hint);
        input.setText(R.string.group_new_name);
        input.selectAll();
        box.addView(input);
        box.addView(buildSwatchRow(0, which -> { }));
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_create_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    TabGroups.Group g = tabGroups.create(spaceId,
                            input.getText().toString(),
                            swatchSelection(box));
                    if (g == null) return;
                    for (Tab t : members) {
                        if (GroupOps.mayJoin(t, g)) t.groupId = g.id;
                    }
                    persistGroups();
                    refreshGroupUi();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Reads the selected palette index back from the swatch row. */
    private static int swatchSelection(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            Object tag = v.getTag();
            if (tag instanceof int[] && ((int[]) tag).length == 2) {
                int[] s = (int[]) tag;
                if (s[1] == 1) return s[0];
            }
        }
        return 0;
    }

    /** Eight circular colour swatches; the selection carries a ring. */
    private LinearLayout buildSwatchRow(int selected,
                                        java.util.function.IntConsumer onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, 0);
        float d = getResources().getDisplayMetrics().density;
        int sel = Math.max(0, Math.min(selected, TabsAdapter.GROUP_COLORS.length - 1));
        for (int i = 0; i < TabsAdapter.GROUP_COLORS.length; i++) {
            final int index = i;
            View sw = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (34 * d), (int) (34 * d));
            lp.setMargins((int) (7 * d), 0, (int) (7 * d), 0);
            sw.setLayoutParams(lp);
            sw.setTag(new int[]{index, index == sel ? 1 : 0});
            sw.setOnClickListener(v -> {
                for (int j = 0; j < row.getChildCount(); j++) {
                    View sib = row.getChildAt(j);
                    Object t = sib.getTag();
                    if (t instanceof int[] && ((int[]) t).length == 2) {
                        ((int[]) t)[1] = j == index ? 1 : 0;
                        styleSwatch(sib, ((int[]) t)[0], j == index);
                    }
                }
                onPick.accept(index);
            });
            styleSwatch(sw, index, index == sel);
            row.addView(sw);
        }
        return row;
    }

    private void styleSwatch(View v, int index, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(groupColor(this, index));
        if (selected) {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
            bg.setStroke((int) (3 * getResources().getDisplayMetrics().density), tv.data);
        }
        v.setBackground(bg);
    }

    /** Resolves a palette index against the group colour resources. */
    private static int groupColor(Context ctx, int paletteIndex) {
        int[] palette = TabsAdapter.GROUP_COLORS;
        int idx = Math.max(0, Math.min(paletteIndex, palette.length - 1));
        return ContextCompat.getColor(ctx, palette[idx]);
    }

    private void assignToGroup(Tab t, TabGroups.Group g) {
        if (!GroupOps.mayJoin(t, g) || g.id.equals(t.groupId)) return;
        t.groupId = g.id;
        pruneEmptyGroups(t.spaceId, g.id);
        persistGroups();
        refreshGroupUi();
    }

    /** Starts a new group holding exactly the two given same-space tabs. */
    private void createGroupWith(Tab a, Tab b) {
        if (!GroupOps.mayPair(a, b)) return;
        TabGroups.Group g = tabGroups.create(a.spaceId,
                getString(R.string.group_new_name), tabGroups.nextColor());
        if (g == null) return;
        a.groupId = g.id;
        b.groupId = g.id;
        persistGroups();
        refreshGroupUi();
    }

    private void ungroup(Tab t) {
        if (t == null || t.incognito || t.groupId == null) return;
        String spaceId = t.spaceId;
        t.groupId = null;
        pruneEmptyGroups(spaceId, null);
        persistGroups();
        refreshGroupUi();
    }

    /** Deletes groups of the space that no longer have any member tabs.
     *  {@code keep} protects the group that just received a tab. */
    private void pruneEmptyGroups(String spaceId, String keep) {
        for (String id : GroupOps.pruneCandidates(tabs.tabs(),
                tabGroups.forSpace(spaceId), keep)) {
            tabGroups.remove(id);
            if (detailGroup != null && detailGroup.id.equals(id)) closeGroupDetail();
        }
    }

    /** Group-card overflow: rename, colour, ungroup all, delete group. */
    private void showGroupMenu(final TabGroups.Group g) {
        new AlertDialog.Builder(this)
                .setTitle(g.name)
                .setItems(new CharSequence[]{
                                getString(R.string.group_rename),
                                getString(R.string.group_colour),
                                getString(R.string.group_ungroup_all),
                                getString(R.string.group_delete_group)},
                        (dlg, which) -> {
                            if (which == 0) promptRenameGroup(g);
                            else if (which == 1) promptGroupColor(g);
                            else if (which == 2) ungroupAll(g);
                            else confirmDeleteGroup(g);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteGroup(final TabGroups.Group g) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_delete_group)
                .setMessage(R.string.group_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteGroup(g))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Delete group: closes every member tab, then removes the group. */
    private void deleteGroup(TabGroups.Group g) {
        java.util.List<Tab> doomed = new java.util.ArrayList<>();
        for (Tab t : tabs.tabs()) {
            if (!t.incognito && g.id.equals(t.groupId)) doomed.add(t);
        }
        if (detailGroup != null && detailGroup.id.equals(g.id)) closeGroupDetail();
        for (Tab t : doomed) closeTab(t);
        tabGroups.remove(g.id);
        persistGroups();
        refreshGroupUi();
        toast(R.string.group_removed);
    }

    private void promptRenameGroup(final TabGroups.Group g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(g.name);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_rename_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (tabGroups.rename(g.id, input.getText().toString())) {
                        persistGroups();
                        refreshGroupUi();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Group colour picker with the eight-colour swatch palette. */
    private void promptGroupColor(final TabGroups.Group g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final AlertDialog[] holder = new AlertDialog[1];
        box.addView(buildSwatchRow(g.color, which -> {
            if (tabGroups.setColor(g.id, which)) {
                persistGroups();
                refreshGroupUi();
                if (holder[0] != null) holder[0].dismiss();
            }
        }));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.group_colour)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void ungroupAll(final TabGroups.Group g) {
        for (Tab t : tabs.tabs()) {
            if (!t.incognito && g.id.equals(t.groupId)) t.groupId = null;
        }
        tabGroups.remove(g.id);
        persistGroups();
        refreshGroupUi();
        toast(R.string.group_removed);
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
            if (detailGroup != null) closeGroupDetail(); // back closes the detail first
            else hideTabSwitcher();
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
