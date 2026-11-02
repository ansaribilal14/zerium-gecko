package com.zerium.gecko;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM planner behind the Chrome-style grouped-card tab UI.
 *
 * <p>The old switcher rendered full-width group header rows followed by their
 * member cards. The redesigned overview instead renders <em>one compact group
 * card per group</em> (colour strip, name chip, member previews) followed by
 * the ungrouped cards and the incognito cards; tapping a group card opens a
 * dedicated group-detail grid. This class decides which rows each surface
 * shows, so the interaction model stays unit-testable without Android.</p>
 *
 * <p>Row order in the main overview: group cards in stable group-creation
 * order, then ungrouped tabs of the active space in session order, then the
 * incognito tabs (incognito tabs belong to no space and can never be grouped).
 * While a search filter is active the overview flattens to matching tab cards
 * only — group cards would hide their members' matches. The detail surface
 * lists exactly the members of one group in session order.</p>
 *
 * <p>Groups with no live member are hidden from the overview (the host prunes
 * empty groups; a restored session may briefly produce one). The persisted
 * {@code collapsed} flag stays in the model for persistence compatibility but
 * no longer drives the UI: the group card <em>is</em> the collapsed form, and
 * expansion happens in the dedicated detail grid.</p>
 */
public final class GroupRowPlanner {

    /** One presentation row: either a group card or a tab card. */
    public static final class Row {
        public final TabGroups.Group group;
        public final Tab tab;

        private Row(TabGroups.Group group, Tab tab) {
            this.group = group;
            this.tab = tab;
        }

        public static Row of(TabGroups.Group g) { return new Row(g, null); }
        public static Row of(Tab t) { return new Row(null, t); }

        public boolean isGroup() { return group != null; }
    }

    private GroupRowPlanner() {}

    /** Rows for the main tab overview (grouped-card model). */
    public static List<Row> planMain(List<Tab> tabs, TabGroups groups,
                                     String activeSpaceId, String query) {
        List<Row> rows = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        boolean searching = !q.isEmpty();
        List<TabGroups.Group> gs = groups.forSpace(activeSpaceId);
        List<List<Tab>> members = new ArrayList<>();
        for (int i = 0; i < gs.size(); i++) members.add(new ArrayList<Tab>());
        List<Tab> ungrouped = new ArrayList<>();
        List<Tab> incognito = new ArrayList<>();
        for (Tab t : tabs) {
            if (t.incognito) {
                if (matches(t, q)) incognito.add(t);
                continue;
            }
            if (activeSpaceId == null || !activeSpaceId.equals(t.spaceId)) continue;
            TabGroups.Group g = t.groupId == null ? null : groups.get(t.groupId);
            if (g != null && g.spaceId.equals(activeSpaceId)) {
                int gi = gs.indexOf(g);
                if (gi >= 0 && matches(t, q)) members.get(gi).add(t);
            } else if (matches(t, q)) {
                ungrouped.add(t);
            }
        }
        if (searching) {
            // Flat result list: every matching card, groups not shown.
            for (List<Tab> mem : members) rows.addAll(toRows(mem));
            rows.addAll(toRows(ungrouped));
            rows.addAll(toRows(incognito));
            return rows;
        }
        for (int i = 0; i < gs.size(); i++) {
            if (!members.get(i).isEmpty()) rows.add(Row.of(gs.get(i)));
        }
        rows.addAll(toRows(ungrouped));
        rows.addAll(toRows(incognito));
        return rows;
    }

    /** Rows for the dedicated group-detail grid: this group's members only. */
    public static List<Row> planDetail(List<Tab> tabs, TabGroups groups,
                                       TabGroups.Group detail) {
        List<Row> rows = new ArrayList<>();
        if (detail == null || groups.get(detail.id) == null) return rows;
        for (Tab t : tabs) {
            if (t.incognito || t.groupId == null) continue;
            if (!detail.id.equals(t.groupId)) continue;
            if (detail.spaceId == null || !detail.spaceId.equals(t.spaceId)) continue;
            rows.add(Row.of(t));
        }
        return rows;
    }

    /** Live member count of a group (incognito never counts). */
    public static int memberCount(List<Tab> tabs, TabGroups.Group g) {
        int n = 0;
        if (g == null) return 0;
        for (Tab t : tabs) {
            if (t.incognito || t.groupId == null) continue;
            if (g.id.equals(t.groupId)) n++;
        }
        return n;
    }

    private static boolean matches(Tab t, String q) {
        if (q.isEmpty()) return true;
        String title = t.title == null ? "" : t.title.toLowerCase();
        String url = t.url == null ? "" : t.url.toLowerCase();
        return title.contains(q) || url.contains(q);
    }

    private static List<Row> toRows(List<Tab> tabs) {
        List<Row> out = new ArrayList<>();
        for (Tab t : tabs) out.add(Row.of(t));
        return out;
    }
}
