package com.zerium.gecko;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Downloaded files: entries saved into the public Downloads collection
 *  (API 29+) or the app's external downloads dir (API 26-28). */
public class DownloadsActivity extends AppCompatActivity {

    private static class Row {
        String name;
        long size;
        long date;
        android.net.Uri uri;   // content URI (API 29+) — null for legacy files
    }

    private final List<Row> rows = new ArrayList<>();
    private DownloadsAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_downloads);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        empty = findViewById(R.id.empty);
        RecyclerView rv = findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadsAdapter();
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        rows.clear();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                String[] proj = {
                        android.provider.MediaStore.Downloads.DISPLAY_NAME,
                        android.provider.MediaStore.Downloads.SIZE,
                        android.provider.MediaStore.Downloads.DATE_MODIFIED,
                        android.provider.MediaStore.Downloads._ID
                };
                try (android.database.Cursor c = getContentResolver().query(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        proj, null, null,
                        android.provider.MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
                    if (c != null) {
                        while (c.moveToNext() && rows.size() < 200) {
                            Row r = new Row();
                            r.name = c.getString(0);
                            r.size = c.isNull(1) ? 0 : c.getLong(1);
                            r.date = c.isNull(2) ? 0 : c.getLong(2) * 1000L;
                            long id = c.getLong(3);
                            r.uri = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                            rows.add(r);
                        }
                    }
                }
            } else {
                java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File[] files = dir == null ? null : dir.listFiles();
                if (files != null) {
                    java.util.Arrays.sort(files,
                            (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (java.io.File f : files) {
                        if (rows.size() >= 200) break;
                        Row r = new Row();
                        r.name = f.getName();
                        r.size = f.length();
                        r.date = f.lastModified();
                        rows.add(r);
                    }
                }
            }
        } catch (Exception ignored) {}
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private static String humanSize(long n) {
        if (n < 1024) return n + " B";
        double v = n;
        for (String unit : new String[]{"KB", "MB", "GB"}) {
            v /= 1024;
            if (v < 1024) return String.format(Locale.US, "%.1f %s", v, unit);
        }
        return String.format(Locale.US, "%.1f TB", v / 1024);
    }

    private class DownloadsAdapter extends RecyclerView.Adapter<VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(DownloadsActivity.this)
                    .inflate(R.layout.item_download, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = rows.get(position);
            h.name.setText(r.name);
            String meta = humanSize(r.size);
            if (r.date > 0) {
                meta += " \u00b7 " + SimpleDateFormat.getDateTimeInstance(
                        SimpleDateFormat.SHORT, SimpleDateFormat.SHORT, Locale.getDefault())
                        .format(new Date(r.date));
            }
            h.meta.setText(meta);
            h.itemView.setOnClickListener(v -> {
                if (r.uri != null) {
                    try {
                        android.content.Intent i = new android.content.Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(r.uri, contentType(r.name));
                        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.no_app_for_file, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.downloads_legacy_hint, Toast.LENGTH_LONG).show();
                }
            });
        }

        private static String contentType(String name) {
            String ext = name.contains(".")
                    ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            return mime == null ? "*/*" : mime;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
        }
    }
}
