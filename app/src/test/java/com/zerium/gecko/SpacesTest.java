package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Zerium Spaces core + per-space tab archive: roundtrips, invariants. */
public class SpacesTest {

    @Test
    public void emptyStateYieldsDefaultSpace() {
        Spaces s = Spaces.load("");
        assertEquals(1, s.count());
        assertEquals(Spaces.DEFAULT_ID, s.activeId());
        assertNotNull(s.active());
        assertFalse(s.active().isolated);
    }

    @Test
    public void garbageInputStillYieldsDefaultSpace() {
        Spaces s = Spaces.load("not json at all");
        assertEquals(1, s.count());
        assertEquals(Spaces.DEFAULT_ID, s.activeId());
    }

    @Test
    public void createRenameRemoveRoundtrip() {
        Spaces s = Spaces.load("");
        Spaces.Space work = s.create("Work", true);
        assertNotNull(work);
        assertTrue(work.isolated);
        assertTrue(s.rename(work.id, "Engineering"));
        assertEquals(2, s.count());

        Spaces s2 = Spaces.load(s.dump());
        assertEquals(2, s2.count());
        assertEquals("Engineering", s2.get(work.id).name);
        assertTrue(s2.get(work.id).isolated);

        assertTrue(s2.remove(work.id));
        assertEquals(1, s2.count());
        assertNull(s2.get(work.id));
    }

    @Test
    public void activeIdSurvivesRoundtripAndFallsBack() {
        Spaces s = Spaces.load("");
        Spaces.Space work = s.create("Work", true);
        s.setActive(work.id);
        Spaces s2 = Spaces.load(s.dump());
        assertEquals(work.id, s2.activeId());

        assertTrue(s2.remove(work.id));
        assertEquals(Spaces.DEFAULT_ID, s2.activeId());
    }

    @Test
    public void defaultSpaceIsProtected() {
        Spaces s = Spaces.load("");
        assertFalse(s.remove(Spaces.DEFAULT_ID));
        assertFalse(s.remove(null));
        assertEquals(1, s.count());
    }

    @Test
    public void archiveRoundtripPerSpace() {
        SpaceTabArchive a = new SpaceTabArchive();
        a.append("s1", new SpaceTabArchive.Entry("https://example.com", "{\"hist\":1}"));
        a.append("s1", new SpaceTabArchive.Entry("https://mozilla.org", ""));
        a.append("s2", new SpaceTabArchive.Entry("https://example.org", "STATE"));
        assertEquals(2, a.countFor("s1"));
        assertEquals(1, a.countFor("s2"));
        assertEquals(0, a.countFor("s3"));

        SpaceTabArchive b = SpaceTabArchive.load(a.dump());
        assertEquals(2, b.countFor("s1"));
        assertEquals("https://example.com", b.tabsFor("s1").get(0).url);
        assertEquals("{\"hist\":1}", b.tabsFor("s1").get(0).state);
        assertEquals("STATE", b.tabsFor("s2").get(0).state);

        // Replace wholesale, then clear.
        b.setTabs("s1", java.util.Collections.singletonList(
                new SpaceTabArchive.Entry("https://z.dev", "")));
        assertEquals(1, b.countFor("s1"));
        b.setTabs("s1", new java.util.ArrayList<>());
        assertEquals(0, b.countFor("s1"));
    }

    @Test
    public void archiveGarbageAndEmptyUrlsAreSafe() {
        SpaceTabArchive a = SpaceTabArchive.load("garbage");
        assertEquals(0, a.countFor("default"));
        a.append("default", new SpaceTabArchive.Entry(null, null));
        assertEquals(1, a.countFor("default"));
        assertEquals("", a.tabsFor("default").get(0).url);
        assertEquals("", a.tabsFor("default").get(0).state);
        assertEquals("{}", new SpaceTabArchive().dump());
    }
}
