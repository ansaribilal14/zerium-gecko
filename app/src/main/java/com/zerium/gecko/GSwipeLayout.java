package com.zerium.gecko;

import android.content.Context;
import android.view.MotionEvent;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Scroll-aware SwipeRefreshLayout + edge-swipe tab gesture layer.
 *
 * Pull-to-refresh: SwipeRefreshLayout.canChildScrollUp() only consults its
 * direct child, which here is a FrameLayout that never scrolls. This class
 * delegates the decision to a probe (MainActivity) that answers with the
 * GeckoSession's own scroll position, tracked via ScrollDelegate.
 *
 * Edge swipes: a horizontal drag that STARTS within EDGE_INSET_DP of the
 * screen edge and moves horizontally at least 2x its vertical component
 * switches tabs — the same gesture contract as the WebView edition's
 * WebContainerLayout (24dp edge inset, 56dp trigger, abandoned when the
 * gesture turns vertical). Detection never consumes the events, so page
 * content keeps receiving them.
 */
public class GSwipeLayout extends SwipeRefreshLayout {

    /** Answers scroll/gesture questions for the currently visible session. */
    public interface Probe {
        /** True when the visible page is scrolled down (refresh must not arm). */
        boolean canScrollUp();

        /** True when horizontal tab-switch gestures are enabled in settings. */
        boolean gesturesEnabled();

        /** Direction: -1 = previous tab (left edge), +1 = next tab (right edge). */
        void onEdgeSwipe(int dir);
    }

    private static final int EDGE_INSET_DP = 24;
    private static final int TRIGGER_DP = 56;
    private static final int VERTICAL_ABANDON_DP = 48;

    private Probe probe;
    private float downX, downY;
    private boolean edgeCandidate, edgeFired;

    public GSwipeLayout(Context context) {
        super(context);
    }

    public void setProbe(Probe p) {
        probe = p;
    }

    @Override
    public boolean canChildScrollUp() {
        return probe != null && probe.canScrollUp();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleEdgeGesture(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        handleEdgeGesture(ev);
        return super.onTouchEvent(ev);
    }

    private void handleEdgeGesture(MotionEvent ev) {
        if (probe == null || !probe.gesturesEnabled()) return;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                edgeCandidate = downX <= dp(EDGE_INSET_DP)
                        || downX >= getWidth() - dp(EDGE_INSET_DP);
                edgeFired = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!edgeCandidate || edgeFired) return;
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dy) > dp(VERTICAL_ABANDON_DP)) {
                    edgeCandidate = false;
                    return;
                }
                if (Math.abs(dx) >= dp(TRIGGER_DP) && Math.abs(dx) > 2 * Math.abs(dy)) {
                    edgeFired = true;
                    probe.onEdgeSwipe(dx > 0 ? -1 : 1);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                edgeCandidate = false;
                break;
            default:
                break;
        }
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
