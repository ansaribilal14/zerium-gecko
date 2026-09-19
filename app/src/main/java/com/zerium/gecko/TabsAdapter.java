package com.zerium.gecko;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

/** Grid adapter for the tab switcher (live preview cards). */
public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.VH> {

    public interface Listener {
        void onOpen(Tab tab);
        void onClose(Tab tab);
    }

    private final java.util.List<Tab> tabs;
    private final TabManager manager;
    private final Listener listener;

    public TabsAdapter(java.util.List<Tab> tabs, TabManager manager, Listener listener) {
        this.tabs = tabs;
        this.manager = manager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Tab t = tabs.get(position);
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
        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.close.setOnClickListener(v -> listener.onClose(t));
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
    public int getItemCount() { return tabs.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView preview;
        final TextView title, url, incognito, close;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.tabCard);
            preview = v.findViewById(R.id.tabPreview);
            title = v.findViewById(R.id.tabTitle);
            url = v.findViewById(R.id.tabUrl);
            incognito = v.findViewById(R.id.tabIncognito);
            close = v.findViewById(R.id.tabClose);
        }
    }
}
