package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM tests for the Chrome-style grouped-card row planner: the main
 * overview (one group card per group, then ungrouped, then incognito), the
 * search flatten, the dedicated group-detail grid, Space scoping and
 * process-death restoration.
 */
public class GroupRowPlannerTest {

    private static final String SP = Spaces.DEFAULT_ID;
    private static final String SP2 = "swork";

    private static Tab tab(String url, String spaceId, String groupId) {
        Tab t = new Tab(0L, null, null, false);
        t.url = url;
        t.title = url;
        t.spaceId = spaceId;
        t.groupId = groupId;
        return t;
    }

    private static List<Tab> tabsOf(List<GroupRowPlanner.Row> rows) {
        List<Tab> out = new ArrayList<>();
        for (GroupRowPlanner.Row r : rows) if (r.tab != null) out.add(r.tab);
        return out;
    }

    private static int groupCards(List<GroupRowPlanner.Row> rows) {
        int n = 0;
        for (GroupRowPlanner.Row r : rows) if (r.isGroup()) n++;
        return n;
    }

    @Test
    public void mainOverviewOrdersGroupsThenUngroupedThenIncognito() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "News", 2);
        Tab a = tab("https://a.example", SP, null);
        Tab b = tab("https://b.example", SP, g.id);
        Tab inc = new Tab(0L, null, null, true);
        inc.url = "https://private.example";
        List<Tab> tabs = new ArrayList<>();
        tabs.add(a);
        tabs.add(b);
        tabs.add(inc);

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, groups, SP, "");
        // Normal mode: group card, then ungrouped. Incognito tabs render in
        // the switcher's private mode only (two-mode selector).
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isGroup());
        assertEquals(g.id, rows.get(0).group.id);
        assertEquals(a, rows.get(1).tab);
    }

    @Test
    public void privateModeShowsOnlyIncognitoTabs() {
        TabGroups groups = new TabGroups();
        groups.create(SP, "News", 2);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://a.example", SP, null));
        Tab inc1 = new Tab(0L, null, null, true);
        inc1.url = "https://private1.example";
        Tab inc2 = new Tab(0L, null, null, true);
        inc2.url = "https://private2.example";
        tabs.add(inc1);
        tabs.add(inc2);

        List<GroupRowPlanner.Row> rows = GroupRowPlanner.planPrivate(tabs, "");
        assertEquals(2, rows.size());
        assertEquals(inc1, rows.get(0).tab);
        assertEquals(inc2, rows.get(1).tab);
        // No group cards ever: private tabs cannot be grouped.
        assertEquals(0, groupCards(rows));
    }

    @Test
    public void privateModeSearchFiltersAndNeverLeadsIntoGroups() {
        TabGroups groups = new TabGroups();
        List<Tab> tabs = new ArrayList<>();
        Tab inc = new Tab(0L, null, null, true);
        inc.url = "https://mail.private.example";
        inc.title = "Private mail";
        tabs.add(inc);
        tabs.add(tab("https://normal.example", SP, null));

        assertEquals(1, GroupRowPlanner.planPrivate(tabs, "mail").size());
        assertEquals(0, GroupRowPlanner.planPrivate(tabs, "normal").size());
        assertEquals(1, GroupRowPlanner.planPrivate(tabs, "").size());
    }

    @Test
    public void selectorCountsMatchModeContents() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://a.example", SP, null));
        tabs.add(tab("https://b.example", SP, null));
        tabs.add(tab("https://c.example", SP2, null)); // other space
        Tab inc1 = new Tab(0L, null, null, true);
        tabs.add(inc1);
        Tab inc2 = new Tab(0L, null, null, true);
        tabs.add(inc2);
        assertEquals(2, GroupRowPlanner.normalTabCount(tabs, SP));
        assertEquals(1, GroupRowPlanner.normalTabCount(tabs, SP2));
        assertEquals(2, GroupRowPlanner.incognitoTabCount(tabs));
    }

    @Test
    public void emptyGroupsAreHiddenInMainOverview() {
        TabGroups groups = new TabGroups();
        groups.create(SP, "Empty", 1);
        TabGroups.Group live = groups.create(SP, "Live", 2);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://a.example", SP, live.id));

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, groups, SP, "");
        assertEquals(1, groupCards(rows));
        assertEquals(live.id, rows.get(0).group.id);
    }

    @Test
    public void searchFlattensToMatchingCardsIncludingGroupedOnes() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "News", 2);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://cnn.example/world", SP, g.id));
        tabs.add(tab("https://shop.example", SP, null));
        tabs.add(tab("https://weather.example", SP, g.id));

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, groups, SP, "world");
        assertEquals(0, groupCards(rows));
        List<Tab> hits = tabsOf(rows);
        assertEquals(1, hits.size());
        assertEquals("https://cnn.example/world", hits.get(0).url);
    }

    @Test
    public void searchOnlySeesTheActiveSpace() {
        TabGroups groups = new TabGroups();
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://news.example", SP, null));
        tabs.add(tab("https://news.example", SP2, null));
        tabs.add(new Tab(0L, null, null, true)); // incognito, no url

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, groups, SP2, "news");
        List<Tab> hits = tabsOf(rows);
        assertEquals(1, hits.size());
        assertEquals(SP2, hits.get(0).spaceId);
    }

    @Test
    public void detailRowsAreOnlyThisGroupsMembers() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "News", 2);
        TabGroups.Group other = groups.create(SP, "Other", 5);
        List<Tab> tabs = new ArrayList<>();
        Tab inGroup = tab("https://a.example", SP, g.id);
        Tab inOther = tab("https://b.example", SP, other.id);
        Tab ungrouped = tab("https://c.example", SP, null);
        Tab wrongSpace = tab("https://d.example", SP2, g.id); // defensive
        tabs.add(inGroup);
        tabs.add(inOther);
        tabs.add(ungrouped);
        tabs.add(wrongSpace);

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planDetail(tabs, groups, g);
        assertEquals(1, rows.size());
        assertEquals(inGroup, rows.get(0).tab);
        assertFalse(rows.get(0).isGroup());
    }

    @Test
    public void detailOfUnknownGroupIsEmpty() {
        TabGroups groups = new TabGroups();
        assertTrue(GroupRowPlanner.planDetail(
                new ArrayList<Tab>(), groups, null).isEmpty());
        assertTrue(GroupRowPlanner.planDetail(
                new ArrayList<Tab>(), groups, groups.get("ghost")).isEmpty());
    }

    @Test
    public void memberCountIgnoresIncognito() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "News", 2);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://a.example", SP, g.id));
        Tab inc = new Tab(0L, null, null, true);
        inc.spaceId = SP;
        inc.groupId = g.id; // can never happen via the UI; must not count
        tabs.add(inc);
        assertEquals(1, GroupRowPlanner.memberCount(tabs, g));
        assertEquals(0, GroupRowPlanner.memberCount(tabs, null));
    }

    @Test
    public void processDeathRestoreRebuildsTheSameRows() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "Research", 4);
        groups.rename(g.id, "Deep work");
        groups.setColor(g.id, 6);
        // Simulate process death: reload the model from its persisted dump.
        TabGroups restored = TabGroups.load(groups.dump());

        List<Tab> tabs = new ArrayList<>();
        Tab a = tab("https://a.example", SP, restored.get(g.id).id);
        Tab b = tab("https://b.example", SP, restored.get(g.id).id);
        Tab c = tab("https://c.example", SP, null);
        tabs.add(a);
        tabs.add(b);
        tabs.add(c);

        // restoreSession re-binds via belongsTo before the switcher shows.
        assertTrue(restored.belongsTo(a.groupId, SP));

        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, restored, SP, "");
        assertEquals(1, groupCards(rows));
        assertEquals("Deep work", rows.get(0).group.name);
        assertEquals(6, rows.get(0).group.color);
        assertEquals(2, GroupRowPlanner.memberCount(tabs, rows.get(0).group));
        List<GroupRowPlanner.Row> detail =
                GroupRowPlanner.planDetail(tabs, restored, rows.get(0).group);
        assertEquals(2, detail.size());
        assertEquals(a, detail.get(0).tab);
        assertEquals(b, detail.get(1).tab);
    }

    @Test
    public void spaceSwitchingChangesTheVisibleRows() {
        TabGroups groups = new TabGroups();
        TabGroups.Group work = groups.create(SP2, "Work", 1);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(tab("https://work.example", SP2, work.id));
        tabs.add(tab("https://home.example", SP, null));

        List<GroupRowPlanner.Row> inDefault =
                GroupRowPlanner.planMain(tabs, groups, SP, "");
        assertEquals(0, groupCards(inDefault));
        assertEquals(1, tabsOf(inDefault).size());

        List<GroupRowPlanner.Row> inWork =
                GroupRowPlanner.planMain(tabs, groups, SP2, "");
        assertEquals(1, groupCards(inWork));
        assertEquals(work.id, inWork.get(0).group.id);
    }

    @Test
    public void rowsNeverExposeIncognitoAsGroupMembers() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "News", 2);
        List<Tab> tabs = new ArrayList<>();
        Tab inc = new Tab(0L, null, null, true);
        inc.url = "https://x.example";
        inc.groupId = g.id; // can never happen via the UI; must not leak in
        tabs.add(inc);
        // The overview may show the incognito card, but never a group card
        // for g, and the detail grid stays empty.
        List<GroupRowPlanner.Row> rows =
                GroupRowPlanner.planMain(tabs, groups, SP, "");
        assertEquals(0, groupCards(rows));
        assertTrue(GroupRowPlanner.planDetail(tabs, groups, g).isEmpty());
        assertNull(groups.get(null));
        assertNotNull(groups.get(g.id));
    }
}
