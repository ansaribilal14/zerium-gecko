package com.zerium.gecko;

import java.util.ArrayList;
import java.util.List;

/** Owns the list of tabs and the current selection. */
public class TabManager {
    private final List<Tab> tabs = new ArrayList<>();
    private int current = -1;
    private long nextId = 1;

    public List<Tab> tabs() { return tabs; }
    public int count() { return tabs.size(); }
    public int current() { return current; }
    public void setCurrent(int i) { if (i >= 0 && i < tabs.size()) current = i; }
    public Tab currentTab() { return (current >= 0 && current < tabs.size()) ? tabs.get(current) : null; }

    public Tab add(Tab t) {
        tabs.add(t);
        current = tabs.size() - 1;
        return t;
    }

    public int remove(Tab t) {
        int idx = tabs.indexOf(t);
        if (idx < 0) return current;
        tabs.remove(idx);
        if (tabs.isEmpty()) {
            current = -1;
            return -1;
        }
        if (idx < current) current--;
        else if (idx == current) current = Math.min(idx, tabs.size() - 1);
        return current;
    }

    public boolean hasIncognito() {
        for (Tab t : tabs) if (t.incognito) return true;
        return false;
    }
}
