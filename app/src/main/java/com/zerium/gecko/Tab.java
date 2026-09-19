package com.zerium.gecko;

import android.graphics.Bitmap;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/** One browser tab: its GeckoSession, display and per-tab state. */
public class Tab {
    public final long id;
    public final GeckoSession session;
    public final GeckoView view;
    public final boolean incognito;
    public String url = "";
    public String title = "";
    public Bitmap preview;
    /** Latest serializable session state, delivered via onSessionStateChange. */
    public GeckoSession.SessionState state;
    public volatile long blockedOnPage;
    /** Last known back/forward availability (NavigationDelegate callbacks). */
    public volatile boolean canGoBack;
    public volatile boolean canGoForward;
    /** Last known TLS state (ProgressDelegate.onSecurityChange). */
    public volatile boolean secure;

    public Tab(long id, GeckoSession session, GeckoView view, boolean incognito) {
        this.id = id;
        this.session = session;
        this.view = view;
        this.incognito = incognito;
    }

    public void destroy() {
        if (preview != null) {
            preview.recycle();
            preview = null;
        }
        try {
            session.stop();
            session.close();
        } catch (Exception ignored) {}
    }
}
