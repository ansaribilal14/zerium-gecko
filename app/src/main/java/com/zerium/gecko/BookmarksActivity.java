package com.zerium.gecko;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Bookmarks list. Tap to open in the browser, long-press to delete. */
public class BookmarksActivity extends AppCompatActivity {

    private BookmarksDB db;
    private List<BookmarksDB.Entry> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_bookmarks);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        db = new BookmarksDB(this);

        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                BookmarksDB.Entry e = rows.get(position);
                t1.setText(e.title == null || e.title.isEmpty() ? e.url : e.title);
                t2.setText(e.url);
                t2.setTextColor(0xFF888888);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> p, View view, int pos, long id) -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(rows.get(pos).url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        list.setOnItemLongClickListener((AdapterView<?> p, View view, int pos, long id) -> {
            db.remove(rows.get(pos).id);
            reload();
            return true;
        });
        reload();
    }

    private void reload() {
        rows = db.all();
        labels.clear();
        for (BookmarksDB.Entry e : rows) labels.add(e.url);
        adapter.notifyDataSetChanged();
        findViewById(R.id.empty).setVisibility(rows.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }
}
