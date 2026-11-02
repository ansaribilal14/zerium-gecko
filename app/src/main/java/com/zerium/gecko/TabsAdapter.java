package com.zerium.gecko;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid adapter for the redesigned, Chrome-style tab switcher (live preview
 * cards).
 *
 * <p><b>Main overview (grouped-card model).</b> Instead of the old flat
 * layout — a full-width header row per group followed by its member cards —
 * every group renders as <em>one compact group card</em>: a colour strip, a
 * name/count chip row and up to four member page-preview tiles. Ungrouped
 * tabs and private tabs sit directly in the grid as regular cards, so the
 * "Ungrouped" section header is gone. Tapping a group card opens the
 * dedicated group-detail grid; long-pressing it opens the group overflow
 * menu. Group cards are drag/drop targets: dropping a tab on one joins the
 * group, dropping a grouped tab on empty grid space ungroups it.</p>
 *
 * <p><b>Group detail.</b> The same adapter class also serves the dedicated
 * detail surface via {@link #setDetailGroupId(String)}: it then lists exactly
 * the members of one group (session order), where "Add tab" creates a tab
 * that joins the group, dragging a member onto empty space ungroups it, and
 * every card keeps its normal open/close/long-press actions.</p>
 *
 * <p>Rows are decided by {@link GroupRowPlanner} (pure JVM, unit-tested);
 * grouping rules live in {@link GroupOps}. Grouping is metadata only — this
 * adapter never touches GeckoSessions, it only re-renders {@link Tab#groupId}.
 * While the search filter is active the overview flattens to matching cards
 * so no match can hide behind a group card.</p>
 */
public class TabsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onOpen(Tab tab);
        void onClose(Tab tab);
        /** Bare long-press on a card (no drag): add-to-group / remove / move. */
        void onTabMenu(Tab tab);
        /** Tap on a group card: open the dedicated group-detail grid. */
        void onOpenGroup(TabGroups.Group group);
        /** Long-press on a group card: rename / colour / ungroup / delete. */
        void onGroupMenu(TabGroups.Group group);
    }

    /** Lets the host start an ItemTouchHelper drag from the card long-press. */
    public interface DragStarter {
        void startDrag(RecyclerView.ViewHolder vh);
    }

    static final int TYPE_TAB = 0;
    static final int TYPE_GROUP = 1;

    /** Palette indices into R.color.group_*; order matches the colour picker. */
    public static final int[] GROUP_COLORS = {
            R.color.group_blue, R.color.group_teal, R.color.group_green,
            R.color.group_yellow, R.color.group_orange, R.color.group_red,
            R.color.group_pink, R.color.group_purple
    };

    /** Group cards show at most this many member preview tiles. */
    private static final int MAX_TILES = 4;

    private final List<Tab> tabs;
    private final TabManager manager;
    private final TabGroups tabGroups;
    private final Listener listener;
    private DragStarter dragStarter;
    private String query = "";
    private String activeSpaceId = Spaces.DEFAULT_ID;
    /** Non-null when this instance drives the group-detail grid. */
    private String detailGroupId;

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

    /** Puts this instance into (or out of) group-detail mode. */
    public void setDetailGroupId(String groupId) {
        detailGroupId = groupId;
        notifyDataSetChanged();
    }

    private List<GroupRowPlanner.Row> buildRows() {
        if (detailGroupId != null) {
            return GroupRowPlanner.planDetail(tabs, tabGroups,
                    tabGroups.get(detailGroupId));
        }
        return GroupRowPlanner.planMain(tabs, tabGroups, activeSpaceId, query);
    }

    @Override
    public int getItemViewType(int position) {
        GroupRowPlanner.Row r = positionAt(position);
        return r != null && r.isGroup() ? TYPE_GROUP : TYPE_TAB;
    }

    private GroupRowPlanner.Row positionAt(int position) {
        List<GroupRowPlanner.Row> rows = buildRows();
        if (position < 0 || position >= rows.size()) return null;
        return rows.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            return new GroupVH(inf.inflate(R.layout.item_tab_group, parent, false));
        }
        return new TabVH(inf.inflate(R.layout.item_tab, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupRowPlanner.Row row = positionAt(position);
        if (row == null) return;
        if (holder instanceof GroupVH && row.isGroup()) {
            bindGroup((GroupVH) holder, row.group);
        } else if (holder instanceof TabVH && !row.isGroup()) {
            bindTab((TabVH) holder, row.tab);
        }
    }

    private void bindGroup(final GroupVH h, final TabGroups.Group g) {
        h.itemView.setTag(g);
        int color = colorOf(h.itemView, g.color);
        h.strip.setBackgroundColor(color);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        h.dot.setBackground(dot);
        h.name.setText(g.name);
        int count = GroupRowPlanner.memberCount(tabs, g);
        h.count.setText(h.itemView.getContext().getString(R.string.spaces_tab_count, count));
        bindTiles(h.tiles, g);
        h.itemView.setOnClickListener(v -> listener.onOpenGroup(g));
        h.itemView.setOnLongClickListener(v -> {
            listener.onGroupMenu(g);
            return true;
        });
    }

    /** Up to {@link #MAX_TILES} member previews; neutral tiles fill the rest. */
    private void bindTiles(LinearLayout tiles, TabGroups.Group g) {
        tiles.removeAllViews();
        List<Tab> members = new ArrayList<>();
        for (Tab t : tabs) {
            if (t.incognito || t.groupId == null || !g.id.equals(t.groupId)) continue;
            members.add(t);
            if (members.size() == MAX_TILES) break;
        }
        if (members.isEmpty()) return; // empty groups are hidden by the planner
        int pad = (int) (3 * tiles.getResources().getDisplayMetrics().density);
        for (int i = 0; i < Math.min(MAX_TILES, Math.max(members.size(), 2)); i++) {
            Tab t = i < members.size() ? members.get(i) : null;
            tiles.addView(placeholderTile(tiles, pad, t == null ? null : t.preview));
        }
    }

    /** One preview tile: the page snapshot, or a neutral placeholder. */
    private View placeholderTile(LinearLayout parent, int pad, Bitmap preview) {
        ImageView iv = new ImageView(parent.getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(pad, pad, pad, 0);
        iv.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * parent.getResources().getDisplayMetrics().density);
        android.util.TypedValue tv = new android.util.TypedValue();
        parent.getContext().getTheme().resolveAttribute(
                android.R.attr.colorBackground, tv, true);
        bg.setColor(tv.data);
        iv.setBackground(bg);
        iv.setClipToOutline(true);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (preview != null && !preview.isRecycled()) iv.setImageBitmap(preview);
        return iv;
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
        // Chrome-style colour coding: member cards carry their group colour.
        TabGroups.Group g = t.groupId == null ? null : tabGroups.get(t.groupId);
        boolean grouped = g != null && g.spaceId.equals(activeSpaceId);
        if (grouped) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(colorOf(h.itemView, g.color));
            h.dot.setBackground(dot);
            h.dot.setVisibility(View.VISIBLE);
            if (!current) {
                h.card.setStrokeWidth(2);
                h.card.setStrokeColor(colorOf(h.itemView, g.color));
            }
        } else {
            h.dot.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.close.setOnClickListener(v -> listener.onClose(t));
        h.itemView.setOnLongClickListener(v -> {
            if (t.incognito) return true;
            if (dragStarter != null) dragStarter.startDrag(h);
            else listener.onTabMenu(t);
            return true;
        });
    }

    private static int colorOf(View v, int paletteIndex) {
        int[] palette = GROUP_COLORS;
        int idx = Math.max(0, Math.min(paletteIndex, palette.length - 1));
        return ContextCompat.getColor(v.getContext(), palette[idx]);
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
        return buildRows().size();
    }

    static class TabVH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView preview;
        final TextView title, url, incognito;
        final ImageButton close;
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

    static class GroupVH extends RecyclerView.ViewHolder {
        final View strip;
        final View dot;
        final TextView name;
        final TextView count;
        final LinearLayout tiles;

        GroupVH(@NonNull View v) {
            super(v);
            v.setTag(null);
            strip = v.findViewById(R.id.groupStrip);
            dot = v.findViewById(R.id.grpDot);
            name = v.findViewById(R.id.grpName);
            count = v.findViewById(R.id.grpCount);
            tiles = v.findViewById(R.id.groupTiles);
        }
    }
}
