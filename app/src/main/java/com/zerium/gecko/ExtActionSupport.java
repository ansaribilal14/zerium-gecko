/* Copyright (c) Zerium. GPL-3.0 — see LICENSE for details. */
package com.zerium.gecko;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Add-on toolbar actions for Zerium G (GeckoView 133 WebExtension actions).
 *
 * <p>Installed extensions report a default browser/page action through the
 * extension-level delegate and per-tab overrides through each session's
 * {@link org.mozilla.geckoview.WebExtension.SessionController}. This class
 * tracks both, shows the puzzle-piece toolbar button only when the active
 * tab has at least one visible action, lists those actions in an anchored
 * popup, and renders the action popup itself: per the GeckoView contract the
 * APP owns the popup session — the engine asks {@code onTogglePopup}, we
 * create and open a {@link GeckoSession}, the engine flags it as an extension
 * popup and loads the popup URI into it, and we display it in a
 * {@link PopupWindow}. Dismissing the window closes the session.</p>
 */
class ExtActionSupport implements WebExtension.ActionDelegate {

    /** Host surface the browser activity provides. */
    interface Host {
        /** Currently selected tab's session, or null. */
        GeckoSession activeSession();

        /** Whether the active tab is a private session. */
        boolean activeIncognito();

        /** Global JavaScript preference (mirrors normal tabs). */
        boolean jsEnabled();

        /** Show/hide the puzzle-piece toolbar button. */
        void setExtensionsVisible(boolean visible);

        /** The toolbar button, used to anchor the list and the popup window. */
        View extensionsAnchor();
    }

    /** One visible action: its extension plus the resolved Action. */
    private static class ActionInfo {
        final WebExtension ext;
        final WebExtension.Action action;

        ActionInfo(WebExtension ext, WebExtension.Action action) {
            this.ext = ext;
            this.action = action;
        }

        String label() {
            if (action.title != null && !action.title.isEmpty()) return action.title;
            if (ext.metaData != null && ext.metaData.name != null
                    && !ext.metaData.name.isEmpty()) {
                return ext.metaData.name;
            }
            return ext.id;
        }
    }

    private final Activity mActivity;
    private final GeckoRuntime mRuntime;
    private final Host mHost;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    /** extensionId -> extension, insertion-ordered for a stable list. */
    private final Map<String, WebExtension> mExtensions = new LinkedHashMap<>();
    /** Default (session == null) actions per extensionId, by action kind. */
    private final Map<String, WebExtension.Action> mBrowserDefaults = new HashMap<>();
    private final Map<String, WebExtension.Action> mPageDefaults = new HashMap<>();
    /** Per-session overrides: session -> (extensionId|kind -> Action). */
    private final Map<GeckoSession, Map<String, WebExtension.Action>> mSessionActions =
            new HashMap<>();
    /** Sessions we already registered the session-level delegate on. */
    private final Map<GeckoSession, Set<String>> mSessionDelegates = new HashMap<>();

    private PopupWindow mListPopup;
    private PopupWindow mPopupWindow;
    private GeckoSession mPopupSession;

    ExtActionSupport(Activity activity, GeckoRuntime runtime, Host host) {
        mActivity = activity;
        mRuntime = runtime;
        mHost = host;
    }

    // ---------- Registry ----------

    /** Re-syncs with the controller: picks up installs/removals and (re)binds delegates. */
    void refresh() {
        mRuntime.getWebExtensionController().list().then(exts -> {
            runOnUi(() -> {
                Set<String> ids = new HashSet<>();
                for (WebExtension ext : exts) {
                    if (ext == null || ext.id == null) continue;
                    ids.add(ext.id);
                    if (!mExtensions.containsKey(ext.id)) {
                        mExtensions.put(ext.id, ext);
                        // Extension-level delegate receives the default action
                        // (session == null) and the toggle-popup request.
                        ext.setActionDelegate(this);
                    }
                }
                mExtensions.keySet().retainAll(ids);
                mBrowserDefaults.keySet().retainAll(ids);
                mPageDefaults.keySet().retainAll(ids);
                syncToolbar();
            });
            return null;
        }).exceptionally(ex -> null);
    }

    /** Registers the session-level override delegate on a newly opened tab. */
    void attachSession(GeckoSession session) {
        if (session == null) return;
        runOnUi(() -> registerSessionDelegates(session));
    }

    /** Drops per-session state for a closed tab. */
    void detachSession(GeckoSession session) {
        if (session == null) return;
        mSessionActions.remove(session);
        mSessionDelegates.remove(session);
    }

    /** Recomputes toolbar button visibility for the active tab (idempotent, cheap). */
    void syncToolbar() {
        registerSessionDelegates(mHost.activeSession());
        List<ActionInfo> actions = actionsForActiveSession();
        mHost.setExtensionsVisible(!actions.isEmpty());
    }

    private void registerSessionDelegates(GeckoSession session) {
        if (session == null || mExtensions.isEmpty()) return;
        Set<String> done = mSessionDelegates.get(session);
        if (done == null) {
            done = new HashSet<>();
            mSessionDelegates.put(session, done);
        }
        for (WebExtension ext : mExtensions.values()) {
            if (!done.contains(ext.id)) {
                done.add(ext.id);
                session.getWebExtensionController().setActionDelegate(ext, this);
            }
        }
    }

    // ---------- ActionDelegate ----------

    @Override
    public void onBrowserAction(@NonNull WebExtension extension,
                                @Nullable GeckoSession session,
                                @NonNull WebExtension.Action action) {
        store(extension, session, action, true);
    }

    @Override
    public void onPageAction(@NonNull WebExtension extension,
                             @Nullable GeckoSession session,
                             @NonNull WebExtension.Action action) {
        store(extension, session, action, false);
    }

    private void store(WebExtension ext, GeckoSession session,
                       WebExtension.Action action, boolean browser) {
        if (ext == null || ext.id == null || action == null) return;
        if (session == null) {
            (browser ? mBrowserDefaults : mPageDefaults).put(ext.id, action);
        } else {
            Map<String, WebExtension.Action> overrides = mSessionActions.get(session);
            if (overrides == null) {
                overrides = new HashMap<>();
                mSessionActions.put(session, overrides);
            }
            overrides.put(ext.id + (browser ? "|B" : "|P"), action);
        }
        runOnUi(this::syncToolbar);
    }

    /**
     * The engine asks the app for a session to host the popup after the user
     * clicks an action that defines one. We create it here; the engine then
     * marks it as an extension popup and loads the popup URI.
     */
    @Override
    public GeckoResult<GeckoSession> onTogglePopup(@NonNull WebExtension extension,
                                                   @NonNull WebExtension.Action action) {
        return openPopupSession();
    }

    @Override
    public GeckoResult<GeckoSession> onOpenPopup(@NonNull WebExtension extension,
                                                 @NonNull WebExtension.Action action) {
        return openPopupSession();
    }

    private GeckoResult<GeckoSession> openPopupSession() {
        final GeckoResult<GeckoSession> out = new GeckoResult<>();
        runOnUi(() -> {
            if (mActivity.isFinishing() || mActivity.isDestroyed()) {
                // A null session tells the engine "no popup will be shown".
                out.complete(null);
                return;
            }
            // Toggle semantics: a second open request replaces the current popup.
            dismissAll();
            GeckoSessionSettings.Builder sb = new GeckoSessionSettings.Builder()
                    .usePrivateMode(mHost.activeIncognito())
                    .useTrackingProtection(true)
                    .allowJavascript(mHost.jsEnabled());
            GeckoSession s = new GeckoSession(sb.build());
            s.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onCloseRequest(@NonNull GeckoSession session) {
                    runOnUi(() -> {
                        if (mPopupWindow != null) mPopupWindow.dismiss();
                    });
                }
            });
            s.open(mRuntime);
            mPopupSession = s;
            out.complete(s);
            renderPopupWindow(s);
        });
        return out;
    }

    // ---------- Resolved actions for the active tab ----------

    private List<ActionInfo> actionsForActiveSession() {
        GeckoSession s = mHost.activeSession();
        Map<String, WebExtension.Action> overrides =
                s == null ? null : mSessionActions.get(s);
        List<ActionInfo> out = new ArrayList<>();
        for (WebExtension ext : mExtensions.values()) {
            WebExtension.Action bd = mBrowserDefaults.get(ext.id);
            WebExtension.Action bo = overrides == null
                    ? null : overrides.get(ext.id + "|B");
            WebExtension.Action browser = merge(bo, bd);
            // Browser actions are visible by default; only enabled=false hides.
            if (browser != null && !Boolean.FALSE.equals(browser.enabled)) {
                out.add(new ActionInfo(ext, browser));
            }
            WebExtension.Action pd = mPageDefaults.get(ext.id);
            WebExtension.Action po = overrides == null
                    ? null : overrides.get(ext.id + "|P");
            WebExtension.Action page = merge(po, pd);
            // Page actions only show when the extension explicitly shows them.
            if (page != null && Boolean.TRUE.equals(page.enabled)) {
                out.add(new ActionInfo(ext, page));
            }
        }
        return out;
    }

    /** Merges a session override with its same-kind default (override wins). */
    private static WebExtension.Action merge(WebExtension.Action override,
                                             WebExtension.Action defaultValue) {
        if (override != null && defaultValue != null) {
            try {
                return override.withDefault(defaultValue);
            } catch (IllegalArgumentException ignored) {
                return override;
            }
        }
        return override != null ? override : defaultValue;
    }

    // ---------- UI: anchored action list ----------

    /** Shows the list of actions available on the active tab. */
    void showActionList(View anchor) {
        if (mListPopup != null) {
            mListPopup.dismiss();
            return;
        }
        List<ActionInfo> actions = actionsForActiveSession();
        if (actions.isEmpty()) return;
        Context ctx = mActivity;
        float d = ctx.getResources().getDisplayMetrics().density;
        int onSurface = ContextCompat.getColor(ctx, R.color.on_surface);
        int onVariant = ContextCompat.getColor(ctx, R.color.on_surface_variant);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded((int) (16 * d), R.color.surface_container_high));

        TextView header = new TextView(ctx);
        header.setText(R.string.menu_addons);
        header.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (4 * d));
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setAllCaps(true);
        header.setTextColor(onVariant);
        box.addView(header);

        for (final ActionInfo info : actions) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (10 * d));
            applyTouchable(row);

            final ImageView icon = new ImageView(ctx);
            icon.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) (24 * d), (int) (24 * d)));
            icon.setImageResource(R.drawable.ic_extension);
            icon.setColorFilter(onVariant);
            row.addView(icon);
            if (info.action.icon != null) {
                info.action.icon.getBitmap(32).then(bmp -> {
                    runOnUi(() -> {
                        if (bmp != null) {
                            icon.setImageBitmap(bmp);
                            icon.setColorFilter(null);
                        }
                    });
                    return null;
                }).exceptionally(ex -> null);
            }

            TextView title = new TextView(ctx);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMargins((int) (12 * d), 0, (int) (8 * d), 0);
            title.setLayoutParams(tp);
            title.setText(info.label());
            title.setTextSize(15);
            title.setTextColor(onSurface);
            title.setSingleLine(true);
            row.addView(title);

            if (info.action.badgeText != null && !info.action.badgeText.isEmpty()) {
                TextView badge = new TextView(ctx);
                badge.setText(info.action.badgeText);
                badge.setTextSize(11);
                badge.setPadding((int) (6 * d), (int) (2 * d), (int) (6 * d), (int) (2 * d));
                badge.setTextColor(info.action.badgeTextColor != null
                        ? info.action.badgeTextColor
                        : ContextCompat.getColor(ctx, R.color.on_primary));
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(6 * d);
                pill.setColor(info.action.badgeBackgroundColor != null
                        ? info.action.badgeBackgroundColor
                        : ContextCompat.getColor(ctx, R.color.accent));
                badge.setBackground(pill);
                row.addView(badge);
            }

            row.setOnClickListener(v -> {
                if (mListPopup != null) mListPopup.dismiss();
                info.action.click();
            });
            box.addView(row);
        }

        int w = (int) Math.min(320 * d,
                ctx.getResources().getDisplayMetrics().widthPixels - 24 * d);
        PopupWindow pw = new PopupWindow(box, w, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(12 * d);
        pw.setOnDismissListener(() -> mListPopup = null);
        mListPopup = pw;
        pw.showAsDropDown(anchor, 0, -(int) (4 * d));
    }

    // ---------- UI: popup window ----------

    private void renderPopupWindow(GeckoSession session) {
        float d = mActivity.getResources().getDisplayMetrics().density;
        GeckoView view = new GeckoView(mActivity);
        view.setSession(session);
        FrameLayout holder = new FrameLayout(mActivity);
        holder.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.setBackground(rounded((int) (12 * d), R.color.surface_container_lowest));

        int w = (int) Math.min(360 * d,
                mActivity.getResources().getDisplayMetrics().widthPixels - 24 * d);
        int h = (int) Math.min(560 * d,
                mActivity.getResources().getDisplayMetrics().heightPixels * 0.62f);
        PopupWindow pw = new PopupWindow(holder, w, h, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(16 * d);
        pw.setOnDismissListener(() -> {
            if (mPopupSession == session) {
                session.close();
                mPopupSession = null;
            }
            if (mPopupWindow == pw) mPopupWindow = null;
        });
        mPopupWindow = pw;

        View anchor = mHost.extensionsAnchor();
        if (anchor != null && anchor.isShown()) {
            pw.showAsDropDown(anchor, 0, -(int) (2 * d));
        } else {
            View content = mActivity.findViewById(android.R.id.content);
            pw.showAtLocation(content, Gravity.TOP | Gravity.END,
                    (int) (12 * d), (int) (64 * d));
        }
    }

    // ---------- Teardown ----------

    /** Closes the list and the popup window (closing the popup session with it). */
    void dismissAll() {
        if (mListPopup != null) mListPopup.dismiss();
        if (mPopupWindow != null) mPopupWindow.dismiss();
    }

    void teardown() {
        dismissAll();
        mExtensions.clear();
        mBrowserDefaults.clear();
        mPageDefaults.clear();
        mSessionActions.clear();
        mSessionDelegates.clear();
    }

    // ---------- Helpers ----------

    private void runOnUi(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mMain.post(r);
        }
    }

    private GradientDrawable rounded(int radiusPx, int colorRes) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radiusPx);
        d.setColor(ContextCompat.getColor(mActivity, colorRes));
        return d;
    }

    private void applyTouchable(View v) {
        TypedValue tv = new TypedValue();
        mActivity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setBackgroundResource(tv.resourceId);
    }
}
