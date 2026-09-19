package com.zerium.gecko;

import android.graphics.Bitmap;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/** One browser tab: its GeckoSession, display and per-tab state. */
public class Tab {

    /** Reader-mode article extracted by the shield's content script. */
    public static class ReaderArticle {
        public String url = "";
        public String title = "";
        public String byline = "";
        public String siteName = "";
        public String content = "";
        public int length;
    }

    public final long id;
    public final GeckoSession session;
    public final GeckoView view;
    public final boolean incognito;
    public String url = "";
    public String title = "";
    public Bitmap preview;
    /** Latest serializable session state, delivered via onSessionStateChange. */
    public GeckoSession.SessionState state;
    /** Latest vertical scroll position (ScrollDelegate.onScrollChanged). */
    public volatile int scrollY;
    public volatile long blockedOnPage;
    /** Last known back/forward availability (NavigationDelegate callbacks). */
    public volatile boolean canGoBack;
    public volatile boolean canGoForward;
    /** Last known TLS state (ProgressDelegate.onSecurityChange). */
    public volatile boolean secure;
    /** Reader article extracted from the current page, if any. */
    public volatile ReaderArticle readerArticle;
    /** True while the session is showing the generated reader page. */
    public boolean readerActive;
    /** The URL the reader page was opened from (null when not in reader). */
    public String readerSourceUrl;

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
