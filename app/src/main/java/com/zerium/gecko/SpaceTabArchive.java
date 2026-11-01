package com.zerium.gecko;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-space tab archive for Zerium Spaces.
 *
 * <p>GeckoView supports exactly one engine runtime per process, so only the
 * active space's tabs live in memory; every other space's tabs (url + full
 * session state) wait here. When a space becomes active its archived tabs
 * are restored into sessions under that space's engine profile.</p>
 *
 * <p>Pure-JVM core (org.json only), unit-testable. Persisted document:
 * {@code {"spaceId":[{"u":url,"s":sessionState},...],...}}.</p>
 */
public final class SpaceTabArchive {

    /** One archived tab. */
    public static final class Entry {
        public final String url;
        public final String state;
        /** Tab group inside the space (null = ungrouped; see TabGroups). */
        public final String groupId;

        public Entry(String url, String state) {
            this(url, state, null);
        }

        public Entry(String url, String state, String groupId) {
            this.url = url == null ? "" : url;
            this.state = state == null ? "" : state;
            this.groupId = groupId == null || groupId.isEmpty() ? null : groupId;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("u", url);
                if (!state.isEmpty()) o.put("s", state);
                if (groupId != null) o.put("g", groupId);
            } catch (Exception ignored) {}
            return o;
        }

        static Entry fromJson(JSONObject o) {
            if (o == null) return null;
            return new Entry(o.optString("u", ""), o.optString("s", ""),
                    o.optString("g", ""));
        }
    }

    private final Map<String, List<Entry>> bySpace = new LinkedHashMap<>();

    public static SpaceTabArchive load(String json) {
        SpaceTabArchive a = new SpaceTabArchive();
        if (json == null || json.isEmpty()) return a;
        try {
            JSONObject root = new JSONObject(json);
            java.util.Iterator<String> keys = root.keys();
            while (keys != null && keys.hasNext()) {
                String spaceId = keys.next();
                JSONArray arr = root.optJSONArray(spaceId);
                if (arr == null) continue;
                List<Entry> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    Entry e = Entry.fromJson(arr.optJSONObject(i));
                    if (e != null) list.add(e);
                }
                if (!list.isEmpty()) a.bySpace.put(spaceId, list);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public String dump() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<Entry>> en : bySpace.entrySet()) {
                JSONArray arr = new JSONArray();
                for (Entry e : en.getValue()) arr.put(e.toJson());
                root.put(en.getKey(), arr);
            }
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<Entry> tabsFor(String spaceId) {
        List<Entry> list = bySpace.get(spaceId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** Replaces one space's archived list (empty list clears it). */
    public void setTabs(String spaceId, List<Entry> tabs) {
        if (spaceId == null) return;
        if (tabs == null || tabs.isEmpty()) bySpace.remove(spaceId);
        else bySpace.put(spaceId, new ArrayList<>(tabs));
    }

    public void append(String spaceId, Entry entry) {
        if (spaceId == null || entry == null) return;
        List<Entry> list = bySpace.get(spaceId);
        if (list == null) {
            list = new ArrayList<>();
            bySpace.put(spaceId, list);
        }
        list.add(entry);
    }

    public void remove(String spaceId) {
        if (spaceId != null) bySpace.remove(spaceId);
    }

    public int countFor(String spaceId) {
        List<Entry> list = bySpace.get(spaceId);
        return list == null ? 0 : list.size();
    }
}
