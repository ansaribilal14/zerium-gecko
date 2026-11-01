package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** Pure-JVM tests for the TabGroups model (Chrome-style groups inside a Space). */
public class TabGroupsTest {

    private static final String SP = Spaces.DEFAULT_ID;
    private static final String SP2 = "swork";

    @Test
    public void emptyModelIsUsable() {
        TabGroups t = TabGroups.load(null);
        assertEquals(0, t.countFor(SP));
        assertNull(t.get("nope"));
        assertEquals("[]", t.dump());
        // Corrupt input must not throw.
        assertTrue(TabGroups.load("not json").countFor(SP) == 0);
        assertTrue(TabGroups.load("{\"obj\":1}").countFor(SP) == 0);
    }

    @Test
    public void createValidateDumpLoad() {
        TabGroups t = new TabGroups();
        TabGroups.Group g = t.create(SP, "Shopping", 2);
        assertNotNull(g);
        assertTrue(g.id.startsWith("g"));
        assertEquals(SP, g.spaceId);
        assertEquals("Shopping", g.name);
        assertEquals(2, g.color);
        assertFalse(g.collapsed);

        // Round trip.
        TabGroups t2 = TabGroups.load(t.dump());
        assertEquals(1, t2.countFor(SP));
        TabGroups.Group g2 = t2.get(g.id);
        assertNotNull(g2);
        assertEquals("Shopping", g2.name);
        assertEquals(2, g2.color);
        assertFalse(g2.collapsed);
    }

    @Test
    public void createRejectsEmptyNameOrSpace() {
        TabGroups t = new TabGroups();
        assertNull(t.create(SP, "   ", 0));
        assertNull(t.create("", "Shopping", 0));
        assertNull(t.create(null, "Shopping", 0));
        assertNull(t.create(SP, null, 0));
    }

    @Test
    public void colorIndexIsClampedAndCycles() {
        TabGroups t = new TabGroups();
        assertEquals(0, t.nextColor());
        assertEquals(1, t.nextColor());
        // Out-of-range explicit colors fall back to the cycling palette
        // (which is at 2 after the two nextColor() calls above).
        assertEquals(2, t.create(SP, "A", 42).color);
        assertEquals(3, t.create(SP, "B", -3).color);
        // Valid explicit colors pass through untouched.
        assertEquals(7, t.create(SP, "C", 7).color);
        // Auto palette keeps cycling within bounds.
        TabGroups t2 = new TabGroups();
        for (int i = 0; i < TabGroups.COLOR_COUNT * 2; i++) {
            int c = t2.nextColor();
            assertTrue(c >= 0 && c < TabGroups.COLOR_COUNT);
        }
    }

    @Test
    public void groupsAreScopedPerSpace() {
        TabGroups t = new TabGroups();
        TabGroups.Group g1 = t.create(SP, "Personal group", 0);
        TabGroups.Group g2 = t.create(SP2, "Work group", 1);
        assertEquals(1, t.countFor(SP));
        assertEquals(1, t.countFor(SP2));
        assertEquals(g2.id, t.forSpace(SP2).get(0).id);

        // A tab of SP may not join an SP2 group.
        assertFalse(t.belongsTo(g2.id, SP));
        assertTrue(t.belongsTo(g2.id, SP2));
        assertFalse(t.belongsTo(g1.id, null));
        assertFalse(t.belongsTo(null, SP));
        assertFalse(t.belongsTo("ghost", SP));
    }

    @Test
    public void renameColorCollapse() {
        TabGroups t = new TabGroups();
        TabGroups.Group g = t.create(SP, "Research", 0);
        assertTrue(t.rename(g.id, "Deep work"));
        assertEquals("Deep work", t.get(g.id).name);
        assertFalse(t.rename(g.id, "   "));
        assertFalse(t.rename("ghost", "x"));
        assertEquals("Deep work", t.get(g.id).name);

        assertTrue(t.setColor(g.id, 5));
        assertEquals(5, t.get(g.id).color);
        assertFalse(t.setColor(g.id, 99));
        assertFalse(t.setColor("ghost", 1));
        assertEquals(5, t.get(g.id).color);

        assertTrue(t.setCollapsed(g.id, true));
        assertTrue(t.get(g.id).collapsed);
        // Collapsed state survives a round trip.
        TabGroups t2 = TabGroups.load(t.dump());
        assertTrue(t2.get(g.id).collapsed);
    }

    @Test
    public void removeDeletesOnlyThatGroup() {
        TabGroups t = new TabGroups();
        TabGroups.Group a = t.create(SP, "A", 0);
        TabGroups.Group b = t.create(SP, "B", 1);
        assertNotNull(t.remove(a.id));
        assertNull(t.get(a.id));
        assertNull(t.remove(a.id)); // idempotent
        assertNotNull(t.get(b.id));
        assertEquals(1, t.countFor(SP));
    }

    @Test
    public void removeSpaceClearsOnlyThatSpace() {
        TabGroups t = new TabGroups();
        t.create(SP, "A", 0);
        t.create(SP2, "B", 1);
        t.removeSpace(SP);
        assertEquals(0, t.countFor(SP));
        assertEquals(1, t.countFor(SP2));
    }

    @Test
    public void corruptEntriesAreSkippedAndIdsDeduped() throws Exception {
        String doc = "[{\"id\":\"g1\",\"sp\":\"default\",\"name\":\"Ok\",\"c\":3},"
                + "{\"sp\":\"default\",\"name\":\"no id\"},"
                + "{\"id\":\"g2\",\"sp\":\"\",\"name\":\"no space\"},"
                + "{\"id\":\"g1\",\"sp\":\"default\",\"name\":\"dup\"},"
                + "\"junk\"]";
        TabGroups t = TabGroups.load(doc);
        assertEquals(1, t.countFor(SP));
        assertEquals("Ok", t.get("g1").name);
        assertEquals(3, t.get("g1").color);
    }

    @Test
    public void jsonShapeIsDocumented() throws Exception {
        TabGroups t = new TabGroups();
        TabGroups.Group g = t.create(SP, "Shopping", 1);
        t.setCollapsed(g.id, true);
        JSONObject o = new org.json.JSONArray(t.dump()).getJSONObject(0);
        assertEquals(g.id, o.getString("id"));
        assertEquals(SP, o.getString("sp"));
        assertEquals("Shopping", o.getString("name"));
        assertEquals(1, o.getInt("c"));
        assertTrue(o.optBoolean("col", false));
    }
}
