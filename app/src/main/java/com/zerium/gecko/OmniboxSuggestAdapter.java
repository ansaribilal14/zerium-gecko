package com.zerium.gecko;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropdown rows under the omnibox while it is focused (native omnibox
 * suggestions — not the start-page typeahead, which is a separate surface).
 * Renders {@link SuggestEngine.Suggestion} rows; picking is delegated to
 * the host, which navigates without ever recording or re-displaying
 * internal pages.
 */
public class OmniboxSuggestAdapter extends RecyclerView.Adapter<OmniboxSuggestAdapter.VH> {

    public interface Host {
        void onPick(SuggestEngine.Suggestion suggestion);
    }

    private final List<SuggestEngine.Suggestion> rows = new ArrayList<>();
    private final Host host;

    public OmniboxSuggestAdapter(Host host) {
        this.host = host;
    }

    /** Replaces the visible rows (already merged + capped by SuggestEngine). */
    public void submit(List<SuggestEngine.Suggestion> suggestions) {
        rows.clear();
        if (suggestions != null) rows.addAll(suggestions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_omnibox_suggest, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final SuggestEngine.Suggestion s = rows.get(position);
        h.title.setText(s.title);
        int icon;
        String sub;
        switch (s.type) {
            case SuggestEngine.TYPE_HISTORY:
                icon = R.drawable.ic_history;
                sub = s.payload;
                break;
            case SuggestEngine.TYPE_URL:
                icon = R.drawable.ic_globe;
                sub = "Open address";
                break;
            case SuggestEngine.TYPE_SEARCH:
                icon = R.drawable.ic_search;
                sub = "Search";
                break;
            default:
                icon = R.drawable.ic_globe;
                sub = s.payload;
                break;
        }
        h.icon.setImageResource(icon);
        h.icon.setColorFilter(ContextCompat.getColor(h.itemView.getContext(),
                R.color.on_surface_variant));
        if (sub == null || sub.isEmpty() || sub.equals(s.title)) {
            h.sub.setVisibility(View.GONE);
        } else {
            h.sub.setVisibility(View.VISIBLE);
            h.sub.setText(sub);
        }
        h.itemView.setOnClickListener(v -> host.onPick(s));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView sub;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.suggestIcon);
            title = v.findViewById(R.id.suggestTitle);
            sub = v.findViewById(R.id.suggestSub);
        }
    }
}
