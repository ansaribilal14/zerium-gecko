package com.zerium.gecko;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Zerium Spaces: named tab groups, each optionally isolated.
 *
 * <p>An isolated space gets its own engine profile (androidx.webkit
 * multi-profile on this edition), so every site keeps an independent set of
 * logins and site data per space. The default space always exists, can not
 * be removed, and keeps using the shared default profile.</p>
 *
 * <p>Pure-JVM core (org.json only) so the persistence logic is unit-testable;
 * the host app stores {@link #dump()} and reloads with {@link #load(String)}.
 * The persisted document is {@code [[spaces...], "activeId"]}.</p>
 */
public final class Spaces {

    public static final String DEFAULT_ID = "default";

    /** One space. Identity and isolation are fixed at creation; the name
     *  can be renamed later. */
    public static final class Space {
        public final String id;
        public String name;
        public final boolean isolated;
        public final long createdAt;

        Space(String id, String name, boolean isolated, long createdAt) {
            this.id = id;
            this.name = name;
            this.isolated = isolated;
            this.createdAt = createdAt;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("isolated", isolated);
                o.put("created", createdAt);
            } catch (Exception ignored) {}
            return o;
        }

        static Space fromJson(JSONObject o) {
            if (o == null) return null;
            String id = o.optString("id", "");
            if (id.isEmpty()) return null;
            return new Space(id, o.optString("name", "Space"),
                    o.optBoolean("isolated", false), o.optLong("created", 0L));
        }
    }

    private final List<Space> spaces = new ArrayList<>();
    private String activeId = DEFAULT_ID;

    /** Parses persisted state; always guarantees the default space exists. */
    public static Spaces load(String json) {
        Spaces s = new Spaces();
        if (json != null && json.startsWith("[")) {
            try {
                JSONArray outer = new JSONArray(json);
                JSONArray arr;
                if (outer.length() == 2 && outer.opt(0) instanceof JSONArray) {
                    arr = outer.optJSONArray(0);
                    s.activeId = outer.optString(1, DEFAULT_ID);
                } else {
                    arr = outer; // legacy bare array (no active marker)
                }
                for (int i = 0; i < arr.length(); i++) {
                    Space sp = Space.fromJson(arr.optJSONObject(i));
                    if (sp != null) s.spaces.add(sp);
                }
            } catch (Exception ignored) {}
        }
        if (s.get(DEFAULT_ID) == null) {
            s.spaces.add(0, new Space(DEFAULT_ID, "Personal", false, 0L));
        }
        if (s.get(s.activeId) == null) s.activeId = DEFAULT_ID;
        return s;
    }

    /** Serializes the space list plus the active id marker. */
    public String dump() {
        try {
            JSONArray arr = new JSONArray();
            for (Space sp : spaces) arr.put(sp.toJson());
            JSONArray outer = new JSONArray();
            outer.put(arr);
            outer.put(activeId);
            return outer.toString();
        } catch (Exception e) {
            return "[[],\"default\"]";
        }
    }

    /** All spaces in stable order (default first). */
    public List<Space> all() {
        return new ArrayList<>(spaces);
    }

    public Space get(String id) {
        if (id == null) return null;
        for (Space sp : spaces) if (sp.id.equals(id)) return sp;
        return null;
    }

    public Space active() {
        Space sp = get(activeId);
        return sp != null ? sp : get(DEFAULT_ID);
    }

    public String activeId() {
        return activeId;
    }

    /** Switches the active space; unknown ids are ignored. */
    public void setActive(String id) {
        if (get(id) != null) activeId = id;
    }

    /** Creates a space; returns null when the trimmed name is empty. */
    public Space create(String name, boolean isolated) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return null;
        String id = "s" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toHexString((int) (Math.random() * 0xffff));
        Space sp = new Space(id, n, isolated, System.currentTimeMillis());
        spaces.add(sp);
        return sp;
    }

    /** Renames a space; returns false when the trimmed name is empty. */
    public boolean rename(String id, String name) {
        Space sp = get(id);
        String n = name == null ? "" : name.trim();
        if (sp == null || n.isEmpty()) return false;
        sp.name = n;
        return true;
    }

    /** Deletes a space; the default space can not be deleted. */
    public boolean remove(String id) {
        if (id == null || id.equals(DEFAULT_ID)) return false;
        Space sp = get(id);
        if (sp == null) return false;
        spaces.remove(sp);
        if (activeId.equals(id)) activeId = DEFAULT_ID;
        return true;
    }

    public int count() {
        return spaces.size();
    }
}
