package com.zerium.gecko;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zerium Tab Groups: Chrome-style grouping of tabs <em>inside</em> one Space.
 *
 * <p>The hierarchy is Zerium → Space → Tab Group → Tab. A group is pure
 * metadata belonging to an existing Space: tabs reference their group by id
 * ({@link Tab#groupId}), so moving a tab between groups never recreates its
 * WebView and never touches the Space's login profile. Groups vanish when
 * their last member leaves them (the host prunes empties), and a group's
 * colour, name and collapsed state persist across restarts.</p>
 *
 * <p>Pure-JVM core (org.json only) so the persistence logic is unit-testable;
 * the host app stores {@link #dump()} and reloads with {@link #load(String)}.
 * The persisted document is a bare array of groups; unknown or corrupt
 * entries are skipped, never fatal.</p>
 */
public final class TabGroups {

    /** Size of the colour palette shown in the UI (mirrors R.color.group_*). */
    public static final int COLOR_COUNT = 8;

    /** One group. Identity and space membership are fixed at creation;
     *  name, colour and collapsed state can change later. */
    public static final class Group {
        public final String id;
        public final String spaceId;
        public String name;
        public int color;
        public boolean collapsed;

        Group(String id, String spaceId, String name, int color, boolean collapsed) {
            this.id = id;
            this.spaceId = spaceId;
            this.name = name;
            this.color = color;
            this.collapsed = collapsed;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("sp", spaceId);
                o.put("name", name);
                o.put("c", color);
                if (collapsed) o.put("col", true);
            } catch (Exception ignored) {}
            return o;
        }

        static Group fromJson(JSONObject o) {
            if (o == null) return null;
            String id = o.optString("id", "");
            String sp = o.optString("sp", "");
            if (id.isEmpty() || sp.isEmpty()) return null;
            return new Group(id, sp, o.optString("name", "Group"),
                    clampColor(o.optInt("c", 0)), o.optBoolean("col", false));
        }
    }

    private final List<Group> groups = new ArrayList<>();
    private int nextColor = 0;

    private static int clampColor(int c) {
        if (c < 0 || c >= COLOR_COUNT) return 0;
        return c;
    }

    /** Parses persisted state; corrupt input yields an empty, usable model. */
    public static TabGroups load(String json) {
        TabGroups t = new TabGroups();
        if (json == null || !json.startsWith("[")) return t;
        Set<String> seen = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Group g = Group.fromJson(arr.optJSONObject(i));
                if (g != null && seen.add(g.id)) t.groups.add(g);
            }
        } catch (Exception ignored) {}
        return t;
    }

    /** Serializes the group list. */
    public String dump() {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) arr.put(g.toJson());
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** All groups of one space, in stable creation order. */
    public List<Group> forSpace(String spaceId) {
        List<Group> out = new ArrayList<>();
        if (spaceId == null) return out;
        for (Group g : groups) if (g.spaceId.equals(spaceId)) out.add(g);
        return out;
    }

    public Group get(String id) {
        if (id == null) return null;
        for (Group g : groups) if (g.id.equals(id)) return g;
        return null;
    }

    public int countFor(String spaceId) {
        return forSpace(spaceId).size();
    }

    /**
     * Creates a group in a space. An out-of-range colour index falls back to
     * the internal cycling palette, so callers can always pass 0 for "auto".
     * Returns null when the trimmed name is empty.
     */
    public Group create(String spaceId, String name, int color) {
        String sp = spaceId == null ? "" : spaceId.trim();
        String n = name == null ? "" : name.trim();
        if (sp.isEmpty() || n.isEmpty()) return null;
        String id = "g" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toHexString((int) (Math.random() * 0xffff));
        int c = (color >= 0 && color < COLOR_COUNT) ? color : nextColor();
        Group g = new Group(id, sp, n, c, false);
        groups.add(g);
        return g;
    }

    /** Next palette index, cycling through all eight colours. */
    public int nextColor() {
        int c = nextColor % COLOR_COUNT;
        nextColor++;
        return c;
    }

    /** Renames a group; returns false when the trimmed name is empty. */
    public boolean rename(String id, String name) {
        Group g = get(id);
        String n = name == null ? "" : name.trim();
        if (g == null || n.isEmpty()) return false;
        g.name = n;
        return true;
    }

    /** Sets the palette colour; returns false when the index is out of range. */
    public boolean setColor(String id, int color) {
        Group g = get(id);
        if (g == null || color < 0 || color >= COLOR_COUNT) return false;
        g.color = color;
        return true;
    }

    public boolean setCollapsed(String id, boolean collapsed) {
        Group g = get(id);
        if (g == null) return false;
        g.collapsed = collapsed;
        return true;
    }

    /** Deletes a group; members keep running, they just become ungrouped. */
    public Group remove(String id) {
        Group g = get(id);
        if (g == null) return null;
        groups.remove(g);
        return g;
    }

    /** Removes every group of a space (used when the space itself is deleted). */
    public void removeSpace(String spaceId) {
        if (spaceId == null) return;
        List<Group> doomed = forSpace(spaceId);
        groups.removeAll(doomed);
    }

    /** True when {@code groupId} names an existing group of {@code spaceId};
     *  used to re-bind restored tabs to their groups safely. */
    public boolean belongsTo(String groupId, String spaceId) {
        Group g = get(groupId);
        return g != null && spaceId != null && g.spaceId.equals(spaceId);
    }
}
