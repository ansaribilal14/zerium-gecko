package com.zerium.gecko;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid adapter for the tab switcher (live preview cards).
 *
 * <p>Within the active space the cards are organized into Chrome-style tab
 * groups: each group renders a coloured header row (name, live tab count,
 * collapse chevron) followed by its member cards, then an "Ungrouped"
 * section header (only when groups exist) and the remaining cards. Private
 * tabs stay plain. Grouping itself is drag-and-drop driven by the host
 * activity; this adapter only presents the structure and exposes a
 * {@link DragStarter} hook for the card long-press.</p>
 */
public class TabsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onOpen(Tab tab);
        void onClose(Tab tab);
        /** Long-press on a card that did not turn into a drag: move to another Space. */
        void onLongPress(Tab tab);
        /** Tap on a group header: expand / collapse. */
        void onGroupToggle(TabGroups.Group group);
        /** Long-press on a group header: rename / colour / ungroup-all menu. */
        void onGroupMenu(TabGroups.Group group);
    }

    /** Lets the host start an ItemTouchHelper drag from the card long-press. */
    public interface DragStarter {
        void startDrag(RecyclerView.ViewHolder vh);
    }

    static final int TYPE_TAB = 0;
    static final int TYPE_HEADER = 1;

    /** Tag set on the "Ungrouped" section header (drop target = ungroup). */
    public static final Object TAG_UNGROUPED = new Object();

    /** Palette indices into R.color.group_*; order matches the colour picker. */
    public static final int[] GROUP_COLORS = {
            R.color.group_blue, R.color.group_teal, R.color.group_green,
            R.color.group_yellow, R.color.group_orange, R.color.group_red,
            R.color.group_pink, R.color.group_purple
    };

    private final List<Tab> tabs;
    private final TabManager manager;
    private final TabGroups tabGroups;
    private final Listener listener;
    private DragStarter dragStarter;
    private String query = "";
    private String activeSpaceId = Spaces.DEFAULT_ID;

    /** Flat presentation: Tab or TabGroups.Group entries (rebuilt lazily). */
    private final List<Object> rows = new ArrayList<>();

    public TabsAdapter(List<Tab> tabs, TabManager manager, TabGroups tabGroups,
                       Listener listener) {
        this.tabs = tabs;
        this.manager = manager;
        this.tabGroups = tabGroups;
        this.listener = listener;
    }

    public void setDragStarter(DragStarter ds) {
        dragStarter = ds;
    }

    /** Brave-style "Search your tabs": case-insensitive title/URL filter. */
    public void filter(String q) {
        this.query = q == null ? "" : q.trim().toLowerCase();
        notifyDataSetChanged();
    }

    /** Space whose tabs the switcher shows (plus all private tabs). */
    public void setActiveSpaceId(String spaceId) {
        activeSpaceId = spaceId == null ? Spaces.DEFAULT_ID : spaceId;
        notifyDataSetChanged();
    }

    private boolean matches(Tab t) {
        if (query.isEmpty()) return true;
        String title = t.title == null ? "" : t.title.toLowerCase();
        String url = t.url == null ? "" : t.url.toLowerCase();
        return title.contains(query) || url.contains(query);
    }

    /** Rebuilds the flat row list: per group [header + members], then the
     *  ungrouped section header (only when groups exist), ungrouped tabs,
     *  and finally private tabs. While the search filter is active every
     *  group is rendered expanded so matches stay visible. */
    private void buildRows() {
        rows.clear();
        List<TabGroups.Group> groups = tabGroups.forSpace(activeSpaceId);
        List<List<Tab>> members = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) members.add(new ArrayList<Tab>());
        List<Tab> ungrouped = new ArrayList<>();
        List<Tab> priv = new ArrayList<>();
        for (Tab t : tabs) {
            if (t.incognito) {
                priv.add(t);
                continue;
            }
            if (!activeSpaceId.equals(t.spaceId)) continue;
            TabGroups.Group g = t.groupId == null ? null : tabGroups.get(t.groupId);
            if (g != null && g.spaceId.equals(activeSpaceId)) {
                int gi = groups.indexOf(g);
                if (gi >= 0 && matches(t)) members.get(gi).add(t);
            } else if (matches(t)) {
                ungrouped.add(t);
            }
        }
        boolean searching = !query.isEmpty();
        for (int i = 0; i < groups.size(); i++) {
            List<Tab> mem = members.get(i);
            TabGroups.Group g = groups.get(i);
            if (searching && mem.isEmpty()) continue;
            rows.add(g);
            if (searching || !g.collapsed) rows.addAll(mem);
        }
        if (!searching && !groups.isEmpty() && !ungrouped.isEmpty()) {
            rows.add(TAG_UNGROUPED);
        }
        rows.addAll(ungrouped);
        rows.addAll(priv);
    }

    private Object rowAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        return rows.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        buildRows();
        Object o = rowAt(position);
        return (o instanceof TabGroups.Group || o == TAG_UNGROUPED)
                ? TYPE_HEADER : TYPE_TAB;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_tab_group_header, parent, false));
        }
        return new TabVH(inf.inflate(R.layout.item_tab, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        buildRows();
        Object o = rowAt(position);
        if (holder instanceof HeaderVH) {
            bindHeader((HeaderVH) holder, o instanceof TabGroups.Group ? (TabGroups.Group) o : null);
        } else if (o instanceof Tab) {
            bindTab((TabVH) holder, (Tab) o);
        }
    }

    private void bindHeader(HeaderVH h, final TabGroups.Group g) {
        h.itemView.setTag(g == null ? TAG_UNGROUPED : g);
        if (g == null) {
            h.name.setText(R.string.tab_groups_ungrouped);
            h.name.setTypeface(Typeface.DEFAULT_BOLD);
            h.dot.setVisibility(View.GONE);
            h.count.setText(R.string.tab_groups_ungrouped_hint);
            h.count.setVisibility(View.VISIBLE);
            h.chevron.setVisibility(View.GONE);
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
            h.itemView.setOnLongClickListener(null);
            h.itemView.setLongClickable(false);
            return;
        }
        h.name.setText(g.name);
        h.name.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(colorOf(h.itemView, g.color));
        h.dot.setBackground(bg);
        h.dot.setVisibility(View.VISIBLE);
        h.count.setText(h.itemView.getContext().getString(
                R.string.spaces_tab_count, memberCount(g)));
        h.count.setVisibility(View.VISIBLE);
        h.chevron.setImageResource(g.collapsed
                ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_up);
        h.chevron.setVisibility(View.VISIBLE);
        h.itemView.setOnClickListener(v -> listener.onGroupToggle(g));
        h.itemView.setOnLongClickListener(v -> {
            listener.onGroupMenu(g);
            return true;
        });
    }

    private void bindTab(TabVH h, final Tab t) {
        h.itemView.setTag(t);
        h.title.setText(t.title == null || t.title.isEmpty() ? t.url : t.title);
        h.url.setText(displayHost(t));
        h.incognito.setVisibility(t.incognito ? View.VISIBLE : View.GONE);
        if (t.preview != null && !t.preview.isRecycled()) {
            h.preview.setImageBitmap(t.preview);
        } else {
            h.preview.setImageDrawable(null);
        }
        boolean current = tabs.indexOf(t) == manager.current();
        h.card.setStrokeWidth(current ? 3 : 0);
        h.card.setStrokeColor(ContextCompat.getColor(h.itemView.getContext(),
                R.color.primary));
        TabGroups.Group g = t.groupId == null ? null : tabGroups.get(t.groupId);
        if (g != null && g.spaceId.equals(activeSpaceId)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(colorOf(h.itemView, g.color));
            h.dot.setBackground(bg);
            h.dot.setVisibility(View.VISIBLE);
        } else {
            h.dot.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.close.setOnClickListener(v -> listener.onClose(t));
        h.itemView.setOnLongClickListener(v -> {
            if (t.incognito) return true;
            if (dragStarter != null) dragStarter.startDrag(h);
            else listener.onLongPress(t);
            return true;
        });
    }

    private static int colorOf(View v, int paletteIndex) {
        int[] palette = GROUP_COLORS;
        int idx = Math.max(0, Math.min(paletteIndex, palette.length - 1));
        return ContextCompat.getColor(v.getContext(), palette[idx]);
    }

    /** Live member count of a group (tabs currently in memory). */
    private int memberCount(TabGroups.Group g) {
        int n = 0;
        for (Tab t : tabs) {
            if (t.incognito || t.groupId == null) continue;
            if (g.id.equals(t.groupId)) n++;
        }
        return n;
    }

    private static String displayHost(Tab t) {
        String u = t.url == null ? "" : t.url;
        if (u.isEmpty() || u.equals(MainActivity.HOME_URL) || u.startsWith("data:")) {
            return t.incognito ? "Private" : "Zerium G";
        }
        String host = Utils.hostOf(u);
        return host == null || host.isEmpty() ? u : host;
    }

    @Override
    public int getItemCount() {
        buildRows();
        return rows.size();
    }

    static class TabVH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView preview;
        final TextView title, url, incognito;
        final TextView close;
        final View dot;

        TabVH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.tabCard);
            preview = v.findViewById(R.id.tabPreview);
            title = v.findViewById(R.id.tabTitle);
            url = v.findViewById(R.id.tabUrl);
            incognito = v.findViewById(R.id.tabIncognito);
            close = v.findViewById(R.id.tabClose);
            dot = v.findViewById(R.id.tabGroupDot);
        }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView count;
        final ImageView chevron;

        HeaderVH(@NonNull View v) {
            super(v);
            v.setTag(null);
            dot = v.findViewById(R.id.grpDot);
            name = v.findViewById(R.id.grpName);
            count = v.findViewById(R.id.grpCount);
            chevron = v.findViewById(R.id.grpChevron);
        }
    }
}
