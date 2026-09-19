package com.zerium.gecko;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Add-ons manager: list installed WebExtensions (the built-in Zerium G
 * Shield first), enable/disable/uninstall, allow-in-private, and install
 * Mozilla-signed add-ons from the curated AMO catalog or a .xpi file.
 *
 * Honest scope (documented in README/PRIVACY): the Gecko engine only
 * accepts Mozilla-signed packages, so installs come from
 * addons.mozilla.org — raw Chrome .crx or unsigned .xpi files are
 * rejected by the engine with ERROR_SIGNEDSTATE_REQUIRED.
 */
public class ExtensionsActivity extends AppCompatActivity {

    /** Curated add-ons resolved to a signed .xpi at install time via the
     *  public AMO v5 API (current_version.file.url) — no stale URLs. */
    private static final String[][] CATALOG = {
            {"ublock-origin", "uBlock Origin", "Efficient wide-spectrum content blocker"},
            {"darkreader", "Dark Reader", "Dark mode for every website"},
            {"sponsorblock", "SponsorBlock", "Skip sponsored segments in videos"},
            {"privacy-badger17", "Privacy Badger", "Learns to block invisible trackers"},
            {"bitwarden-password-manager", "Bitwarden", "Password manager"},
            {"i-dont-care-about-cookies", "I don't care about cookies", "Hides cookie consent banners"},
            {"localcdn-f2d", "LocalCDN", "Serves popular web libraries locally"},
            {"decentraleyes", "Decentraleyes", "Protects against tracking through CDNs"},
    };
    private static final String AMO_DETAIL = "https://addons.mozilla.org/api/v5/addons/addon/";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ADDON = 1;
    private static final int TYPE_HINT = 2;
    private static final int TYPE_SECTION = 3;
    private static final int TYPE_CATALOG = 4;
    private static final int TYPE_FILE = 5;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private RecyclerView list;
    private RowAdapter adapter;
    private final List<Object> rows = new ArrayList<>();

    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Uri uri = result.getData() != null ? result.getData().getData() : null;
                        if (uri != null) installFromFile(uri);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.addons_title);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.addonsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.installFile).setOnClickListener(v -> pickFile());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.addonListRefresh = this::refresh;
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainActivity.addonListRefresh = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (MainActivity.addonListRefresh == this::refresh) {
            MainActivity.addonListRefresh = null;
        }
        io.shutdown();
    }

    private void refresh() {
        GeckoRuntimeHolder h = runtime();
        if (h == null) {
            finish();
            return;
        }
        h.getController().list().then(extensions -> {
            runOnUiThread(() -> {
                rows.clear();
                rows.add(TYPE_SECTION);
                if (extensions == null || extensions.isEmpty()) {
                    rows.add(TYPE_HINT);
                } else {
                    for (WebExtension e : extensions) rows.add(e);
                }
                rows.add(TYPE_SECTION);
                rows.add(getString(R.string.addons_recommended));
                for (String[] c : CATALOG) rows.add(c);
                adapter.notifyDataSetChanged();
            });
            return null;
        });
    }

    private GeckoRuntimeHolder runtime() {
        org.mozilla.geckoview.GeckoRuntime rt = MainActivity.runtimeForAddons;
        if (rt == null) return null;
        return new GeckoRuntimeHolder(rt);
    }

    private static class GeckoRuntimeHolder {
        final org.mozilla.geckoview.GeckoRuntime runtime;
        GeckoRuntimeHolder(org.mozilla.geckoview.GeckoRuntime rt) { runtime = rt; }
        WebExtensionController getController() { return runtime.getWebExtensionController(); }
    }

    // ---------- actions ----------

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            filePicker.launch(i);
        } catch (Exception e) {
            toast(R.string.addons_fetch_url_failed);
        }
    }

    /** Copies the picked document into cache (the engine reads file://). */
    private void installFromFile(Uri uri) {
        io.execute(() -> {
            File tmp = new File(getCacheDir(), "addon-" + System.currentTimeMillis() + ".xpi");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new java.io.FileOutputStream(tmp)) {
                if (in == null) throw new IllegalStateException("stream");
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                runOnUiThread(() -> install(tmp.toURI().toString(),
                        getString(R.string.addons_install_file),
                        WebExtensionController.INSTALLATION_METHOD_FROM_FILE));
            } catch (Exception e) {
                // noinspection ResultOfMethodCallIgnored
                tmp.delete();
                runOnUiThread(() -> toast(R.string.addons_fetch_url_failed));
            }
        });
    }

    private void installFromCatalog(String slug, String name) {
        toast(getString(R.string.addons_install_started, name));
        io.execute(() -> {
            String url = null;
            try {
                InputStream in = new java.net.URL(AMO_DETAIL + slug).openStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                org.json.JSONObject o = new org.json.JSONObject(bos.toString("UTF-8"));
                url = o.getJSONObject("current_version").getJSONObject("file").getString("url");
            } catch (Exception ignored) {}
            final String f = url;
            runOnUiThread(() -> {
                if (f == null) {
                    toast(R.string.addons_fetch_url_failed);
                } else {
                    install(f, name, WebExtensionController.INSTALLATION_METHOD_MANAGER);
                }
            });
        });
    }

    private void install(String url, String name, @InstallationMethod String method) {
        GeckoRuntimeHolder h = runtime();
        if (h == null) return;
        try {
            h.getController().install(url, method)
                    .then(ext -> {
                        runOnUiThread(() -> {
                            toast(getString(R.string.addons_install_ok,
                                    ext != null && ext.metaData != null && ext.metaData.name != null
                                            ? ext.metaData.name : name));
                            refresh();
                        });
                        return null;
                    })
                    .exceptionally(err -> {
                        runOnUiThread(() -> {
                            String reason = installErrorText(err);
                            toast(getString(R.string.addons_install_failed, reason));
                            refresh();
                        });
                        return null;
                    });
        } catch (Exception e) {
            toast(R.string.addons_fetch_url_failed);
        }
    }

    /** Maps engine InstallException codes to honest, user-facing text. */
    private String installErrorText(Throwable err) {
        while (err != null && !(err instanceof WebExtension.InstallException)) {
            err = err.getCause();
        }
        if (!(err instanceof WebExtension.InstallException)) {
            return getString(R.string.addons_err_generic);
        }
        switch (((WebExtension.InstallException) err).errorCode) {
            case WebExtension.InstallException.ERROR_NETWORK_FAILURE:
                return getString(R.string.addons_err_network);
            case WebExtension.InstallException.ERROR_CORRUPT_FILE:
                return getString(R.string.addons_err_corrupt);
            case WebExtension.InstallException.ERROR_SIGNEDSTATE_REQUIRED:
                return getString(R.string.addons_err_unsigned);
            case WebExtension.InstallException.ERROR_BLOCKLISTED:
                return getString(R.string.addons_err_blocklisted);
            default:
                return getString(R.string.addons_err_generic);
        }
    }

    private void toggle(WebExtension ext, boolean enable) {
        GeckoRuntimeHolder h = runtime();
        if (h == null) return;
        try {
            if (enable) {
                h.getController().enable(ext, WebExtensionController.EnableSource.USER);
            } else {
                h.getController().disable(ext, WebExtensionController.EnableSource.USER);
            }
        } catch (Exception ignored) {}
    }

    private void uninstall(WebExtension ext) {
        GeckoRuntimeHolder h = runtime();
        if (h == null) return;
        h.getController().uninstall(ext).then(v -> {
            runOnUiThread(this::refresh);
            return null;
        });
    }

    private void setAllowedInPrivate(WebExtension ext, boolean allow) {
        GeckoRuntimeHolder h = runtime();
        if (h == null) return;
        h.getController().setAllowedInPrivateBrowsing(ext, allow);
    }

    private void showDetails(WebExtension ext) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_addon_details, null);
        ImageView icon = view.findViewById(R.id.dIcon);
        TextView name = view.findViewById(R.id.dName);
        TextView meta = view.findViewById(R.id.dMeta);
        TextView desc = view.findViewById(R.id.dDesc);
        MaterialSwitch priv = view.findViewById(R.id.dPrivate);
        MaterialSwitch enabled = view.findViewById(R.id.dEnabled);
        TextView remove = view.findViewById(R.id.dRemove);
        boolean builtIn = ext.isBuiltIn;
        name.setText(ext.metaData != null && ext.metaData.name != null
                && !ext.metaData.name.isEmpty() ? ext.metaData.name : ext.id);
        String version = ext.metaData != null ? ext.metaData.version : "";
        boolean on = ext.metaData == null || ext.metaData.enabled;
        meta.setText(getString(R.string.addons_enabled) + " v" + (version == null ? "" : version));
        if (ext.metaData != null && ext.metaData.description != null) {
            desc.setText(ext.metaData.description);
            desc.setVisibility(View.VISIBLE);
        } else if (builtIn) {
            desc.setText(R.string.addons_shield_desc);
            desc.setVisibility(View.VISIBLE);
        } else {
            desc.setVisibility(View.GONE);
        }
        priv.setChecked(ext.metaData != null && ext.metaData.allowedInPrivateBrowsing);
        enabled.setChecked(on);
        remove.setVisibility(builtIn ? View.GONE : View.VISIBLE);
        TextView note = view.findViewById(R.id.dNote);
        note.setText(builtIn ? R.string.addons_built_in_note : R.string.addons_signed_by_mozilla);
        enabled.setOnCheckedChangeListener((b, isChecked) -> toggle(ext, isChecked));
        priv.setOnCheckedChangeListener((b, isChecked) -> setAllowedInPrivate(ext, isChecked));
        remove.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.addons_remove))
                    .setMessage(name.getText())
                    .setPositiveButton(R.string.addons_remove, (d, w) -> {
                        uninstall(ext);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        loadIcon(ext, icon);
        new AlertDialog.Builder(this)
                .setTitle(R.string.addons_details)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void loadIcon(WebExtension ext, ImageView target) {
        if (ext.metaData == null || ext.metaData.icon == null) return;
        try {
            ext.metaData.icon.getBitmap(64).then(bmp -> {
                if (bmp != null) {
                    runOnUiThread(() -> {
                        if (target.isAttachedToWindow()) target.setImageBitmap(bmp);
                    });
                }
                return null;
            });
        } catch (Exception ignored) {}
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- adapter ----------

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

        @Override
        public int getItemViewType(int position) {
            Object o = rows.get(position);
            if (o instanceof Integer) return (Integer) o;   // TYPE_* markers
            if (o instanceof WebExtension) return TYPE_ADDON;
            if (o instanceof String) return TYPE_SECTION;
            return TYPE_CATALOG;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            switch (viewType) {
                case TYPE_SECTION: layout = R.layout.item_addon_section; break;
                case TYPE_ADDON: layout = R.layout.item_addon; break;
                case TYPE_HINT: layout = R.layout.item_addon_hint; break;
                case TYPE_CATALOG: layout = R.layout.item_addon_catalog; break;
                default: layout = R.layout.item_addon_hint; break;
            }
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Object o = rows.get(position);
            if (o instanceof WebExtension) {
                WebExtension ext = (WebExtension) o;
                boolean builtIn = ext.isBuiltIn;
                String name = ext.metaData != null && ext.metaData.name != null
                        && !ext.metaData.name.isEmpty() ? ext.metaData.name : ext.id;
                String version = ext.metaData != null ? ext.metaData.version : "";
                boolean on = ext.metaData == null || ext.metaData.enabled;
                h.title.setText(name);
                h.subtitle.setText((on ? getString(R.string.addons_enabled)
                        : getString(R.string.addons_disabled))
                        + (version == null || version.isEmpty() ? "" : " \u00b7 v" + version));
                h.subtitle.setTextColor(on
                        ? getResources().getColor(R.color.on_surface_variant, getTheme())
                        : getResources().getColor(R.color.error, getTheme()));
                h.itemView.setOnClickListener(v -> showDetails(ext));
                if (h.icon != null) {
                    h.icon.setImageResource(builtIn ? R.drawable.ic_shield : R.drawable.ic_extension);
                    loadIcon(ext, h.icon);
                }
                return;
            }
            if (o instanceof String) {
                h.title.setText((String) o);
                return;
            }
            if (o instanceof String[]) {
                String[] c = (String[]) o;
                h.title.setText(c[1]);
                h.subtitle.setText(c[2]);
                h.itemView.setOnClickListener(v -> installFromCatalog(c[0], c[1]));
            }
        }

        @Override
        public int getItemCount() { return rows.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle;
            final ImageView icon;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.addonTitle);
                subtitle = v.findViewById(R.id.addonSubtitle);
                icon = v.findViewById(R.id.addonIcon);
            }
        }
    }
}
