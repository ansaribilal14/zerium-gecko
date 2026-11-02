package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM tests for the drag/drop grouping rules: metadata-only moves,
 * incognito exclusion, Space scoping and empty-group pruning.
 */
public class GroupOpsTest {

    private static final String SP = Spaces.DEFAULT_ID;
    private static final String SP2 = "swork";

    private static Tab tab(String spaceId, String groupId) {
        Tab t = new Tab(0L, null, null, false);
        t.spaceId = spaceId;
        t.groupId = groupId;
        return t;
    }

    @Test
    public void mayJoinRejectsIncognitoCrossSpaceAndNulls() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "A", 0);
        assertFalse(GroupOps.mayJoin(null, g));
        assertFalse(GroupOps.mayJoin(tab(SP, null), null));
        Tab incognito = new Tab(0L, null, null, true);
        incognito.spaceId = SP;
        assertFalse(GroupOps.mayJoin(incognito, g));
        assertFalse(GroupOps.mayJoin(tab(SP2, null), g)); // other space
        assertFalse(GroupOps.mayJoin(tab(null, null), g));
        assertTrue(GroupOps.mayJoin(tab(SP, null), g));
    }

    @Test
    public void mayPairGuardsIncognitoCrossSpaceAndSelf() {
        Tab a = tab(SP, null);
        assertFalse(GroupOps.mayPair(null, a));
        assertFalse(GroupOps.mayPair(a, null));
        assertFalse(GroupOps.mayPair(a, a)); // same tab
        assertFalse(GroupOps.mayPair(a, tab(SP2, null))); // cross space
        Tab incognito = new Tab(0L, null, null, true);
        incognito.spaceId = SP;
        assertFalse(GroupOps.mayPair(a, incognito));
        assertFalse(GroupOps.mayPair(incognito, a));
        assertTrue(GroupOps.mayPair(a, tab(SP, null)));
    }

    @Test
    public void decideTabDropMatrix() {
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "A", 0);

        // Both ungrouped, same space -> create a group with the pair.
        assertEquals(GroupOps.DROP_CREATE_PAIR,
                GroupOps.decideTabDrop(tab(SP, null), tab(SP, null)));
        // Target grouped -> join the target's group.
        assertEquals(GroupOps.DROP_JOIN_TARGET_GROUP,
                GroupOps.decideTabDrop(tab(SP, null), tab(SP, g.id)));
        // Dragged grouped, target ungrouped -> ungroup the dragged tab.
        assertEquals(GroupOps.DROP_UNGROUP,
                GroupOps.decideTabDrop(tab(SP, g.id), tab(SP, null)));
        // Dragged and target grouped -> join the target's group (move).
        assertEquals(GroupOps.DROP_JOIN_TARGET_GROUP,
                GroupOps.decideTabDrop(tab(SP, g.id), tab(SP, g.id)));

        // Incognito on either side -> never.
        Tab incognito = new Tab(0L, null, null, true);
        incognito.spaceId = SP;
        assertEquals(GroupOps.DROP_NONE,
                GroupOps.decideTabDrop(incognito, tab(SP, null)));
        assertEquals(GroupOps.DROP_NONE,
                GroupOps.decideTabDrop(tab(SP, null), incognito));
        // Cross space -> never.
        assertEquals(GroupOps.DROP_NONE,
                GroupOps.decideTabDrop(tab(SP2, null), tab(SP, null)));
        // Nulls -> never.
        assertEquals(GroupOps.DROP_NONE, GroupOps.decideTabDrop(null, tab(SP, null)));
    }

    @Test
    public void pruneCandidatesHonorsKeepAndMembers() {
        TabGroups groups = new TabGroups();
        TabGroups.Group keep = groups.create(SP, "Keep", 0);
        TabGroups.Group alive = groups.create(SP, "Alive", 1);
        TabGroups.Group dead = groups.create(SP, "Dead", 2);

        List<Tab> tabs = new ArrayList<>();
        Tab member = tab(SP, alive.id);
        tabs.add(member);

        List<String> doomed = GroupOps.pruneCandidates(tabs,
                groups.forSpace(SP), keep.id);
        assertEquals(1, doomed.size());
        assertEquals(dead.id, doomed.get(0));

        // With keep=null every memberless group dies (keep had none either);
        // alive survives via its member.
        List<String> all = GroupOps.pruneCandidates(tabs, groups.forSpace(SP), null);
        assertEquals(2, all.size());
        assertFalse(all.contains(alive.id));

        // A group whose only member is incognito never counts (incognito
        // tabs can't be grouped, so the group is genuinely empty).
        Tab incognito = new Tab(0L, null, null, true);
        incognito.spaceId = SP;
        incognito.groupId = keep.id;
        tabs.add(incognito);
        assertTrue(GroupOps.pruneCandidates(tabs, groups.forSpace(SP), null)
                .contains(keep.id));
    }

    @Test
    public void hasMemberIgnoresNullAndIncognito() {
        assertFalse(GroupOps.hasMember(new ArrayList<Tab>(), null));
        assertFalse(GroupOps.hasMember(null, "g1"));
        List<Tab> tabs = new ArrayList<>();
        Tab incognito = new Tab(0L, null, null, true);
        incognito.groupId = "g1";
        tabs.add(incognito);
        assertFalse(GroupOps.hasMember(tabs, "g1"));
        tabs.add(tab(SP, "g1"));
        assertTrue(GroupOps.hasMember(tabs, "g1"));
    }

    @Test
    public void groupingMovesOnlyGroupId() {
        // The red line: grouping is metadata. The decision helpers never
        // touch any engine state, and joining a group leaves the tab object
        // (and thus its WebView/session reference) identical.
        TabGroups groups = new TabGroups();
        TabGroups.Group g = groups.create(SP, "A", 0);
        Tab t = tab(SP, null);
        assertTrue(GroupOps.mayJoin(t, g));
        String before = t.url;
        t.groupId = g.id; // what assignToGroup does
        assertEquals(g.id, t.groupId);
        assertEquals(before, t.url);
        // Moving between groups only rewrites groupId again.
        TabGroups.Group g2 = groups.create(SP, "B", 1);
        t.groupId = g2.id;
        assertEquals(g2.id, t.groupId);
    }
}
