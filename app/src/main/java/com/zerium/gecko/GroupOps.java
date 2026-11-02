package com.zerium.gecko;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM decision rules for the Chrome-style drag/drop grouping gestures.
 *
 * <p>Grouping is metadata only: every accepted action just moves
 * {@link Tab#groupId}; the tab's WebView, browsing session and Space login
 * profile are never touched. Incognito tabs are excluded everywhere — they
 * belong to no Space and can never join a group. These rules are shared by
 * the main overview and the group-detail grid and are unit-tested
 * ({@code GroupOpsTest}) alongside the row planner.</p>
 */
public final class GroupOps {

    /** Keep the current state (incompatible drop, same tab, guards failed). */
    public static final int DROP_NONE = 0;
    /** Target row is a group card: the dragged tab joins that group. */
    public static final int DROP_JOIN_GROUP = 1;
    /** Target tab is grouped: the dragged tab joins the target's group. */
    public static final int DROP_JOIN_TARGET_GROUP = 2;
    /** Dragged tab is grouped, target is not: drop means "ungroup". */
    public static final int DROP_UNGROUP = 3;
    /** Two ungrouped, same-space tabs: form a new group holding both. */
    public static final int DROP_CREATE_PAIR = 4;

    private GroupOps() {}

    /** A tab may join a group only inside its own space; incognito never groups. */
    public static boolean mayJoin(Tab t, TabGroups.Group g) {
        return t != null && g != null && !t.incognito
                && t.spaceId != null && g.spaceId != null
                && t.spaceId.equals(g.spaceId);
    }

    /** Two tabs may form a new group only within one space, never incognito. */
    public static boolean mayPair(Tab a, Tab b) {
        return a != null && b != null && a != b && !a.incognito && !b.incognito
                && a.spaceId != null && a.spaceId.equals(b.spaceId);
    }

    /**
     * Dragged-tab-onto-tab-card decision. The caller still validates that a
     * referenced group id exists ({@code tabGroups.get(...)}) before acting.
     */
    public static int decideTabDrop(Tab dragged, Tab target) {
        if (!mayPair(dragged, target)) return DROP_NONE;
        if (target.groupId != null) return DROP_JOIN_TARGET_GROUP;
        if (dragged.groupId != null) return DROP_UNGROUP;
        return DROP_CREATE_PAIR;
    }

    /** True when at least one non-incognito tab references {@code groupId}. */
    public static boolean hasMember(List<Tab> tabs, String groupId) {
        if (tabs == null || groupId == null) return false;
        for (Tab t : tabs) {
            if (t.incognito || t.groupId == null) continue;
            if (groupId.equals(t.groupId)) return true;
        }
        return false;
    }

    /**
     * Ids of the given groups that no longer have any member tab. Groups live
     * only while they hold members; {@code keepId} protects the group that
     * just received a tab. Incognito tabs never count as members.
     */
    public static List<String> pruneCandidates(List<Tab> tabs,
                                               List<TabGroups.Group> groups,
                                               String keepId) {
        List<String> out = new ArrayList<>();
        if (groups == null) return out;
        for (TabGroups.Group g : groups) {
            if (g.id.equals(keepId)) continue;
            if (!hasMember(tabs, g.id)) out.add(g.id);
        }
        return out;
    }
}
