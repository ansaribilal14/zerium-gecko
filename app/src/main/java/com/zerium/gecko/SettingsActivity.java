package com.zerium.gecko;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Settings: search engines, blocking, privacy, permissions, appearance, data. */
public class SettingsActivity extends AppCompatActivity {

    private static final String[] LOCALES = {
            "", "en", "de", "fr", "es", "it", "pt", "ru", "ja", "zh", "ko", "ar", "hi"
    };
    private static final String[] LOCALE_LABELS = {
            "System default", "English", "Deutsch", "Français", "Español", "Italiano",
            "Português", "Русский", "日本語", "中文", "한국어", "العربية", "हिन्दी"
    };
    private static final float[] SIZES = {0.85f, 1.0f, 1.15f, 1.3f, 1.5f};

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.menu_settings);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        prefs = new Prefs(this);

        // Search engine + custom engines
        TextView engineValue = findViewById(R.id.valueEngine);
        engineValue.setText(Utils.engineAt(prefs, prefs.searchEngine()).name);
        findViewById(R.id.rowEngine).setOnClickListener(v -> pickEngine());
        findViewById(R.id.rowCustomEngines).setOnClickListener(v -> editCustomEngines());

        // Content blocking
        bindSwitch(R.id.swBlockAds, prefs.blockAds(), () -> {
            prefs.blockAds(!prefs.blockAds());
            return prefs.blockAds();
        });
        bindSwitch(R.id.swCookieBanners, prefs.cookieBanners(), () -> {
            prefs.cookieBanners(!prefs.cookieBanners());
            return prefs.cookieBanners();
        });
        bindSwitch(R.id.swPopups, prefs.blockPopups(), () -> {
            prefs.blockPopups(!prefs.blockPopups());
            return prefs.blockPopups();
        });
        findViewById(R.id.rowPerms).setOnClickListener(v -> editPermissions());
        findViewById(R.id.rowAutofill).setOnClickListener(v -> openAutofillSettings());
        findViewById(R.id.rowAddons).setOnClickListener(v ->
                startActivity(new Intent(this, ExtensionsActivity.class)));
        TextView httpsValue = findViewById(R.id.valueHttpsOnly);
        String[] httpsModes = getResources().getStringArray(R.array.https_only_modes);
        httpsValue.setText(httpsModes[Math.max(0, Math.min(prefs.httpsOnly(), 2))]);
        findViewById(R.id.rowHttpsOnly).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.https_only)
                    .setSingleChoiceItems(httpsModes, prefs.httpsOnly(), (d, which) -> {
                        prefs.httpsOnly(which);
                        httpsValue.setText(httpsModes[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        refreshPermSummary();

        // Privacy
        bindSwitch(R.id.swJs, prefs.javascriptEnabled(), () -> {
            prefs.javascriptEnabled(!prefs.javascriptEnabled());
            return prefs.javascriptEnabled();
        });
        findViewById(R.id.rowAllowlist).setOnClickListener(v -> editAllowlist());

        // Page color scheme
        TextView schemeValue = findViewById(R.id.valueScheme);
        String[] schemes = getResources().getStringArray(R.array.page_schemes);
        schemeValue.setText(schemes[prefs.colorScheme()]);
        findViewById(R.id.rowScheme).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.page_color_scheme)
                    .setSingleChoiceItems(schemes, prefs.colorScheme(), (d, which) -> {
                        prefs.colorScheme(which);
                        schemeValue.setText(schemes[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // Appearance
        TextView themeValue = findViewById(R.id.valueTheme);
        String[] themes = getResources().getStringArray(R.array.themes);
        themeValue.setText(themes[prefs.appTheme()]);
        findViewById(R.id.rowTheme).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_theme)
                    .setSingleChoiceItems(themes, prefs.appTheme(), (d, which) -> {
                        prefs.appTheme(which);
                        themeValue.setText(themes[which]);
                        applyTheme(which);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // Text size + content language + gestures
        TextView sizeValue = findViewById(R.id.valueTextSize);
        sizeValue.setText(sizeLabel(prefs.fontSizeFactor()));
        findViewById(R.id.rowTextSize).setOnClickListener(v -> {
            String[] labels = new String[SIZES.length];
            int cur = 1;
            for (int i = 0; i < SIZES.length; i++) {
                labels[i] = sizeLabel(SIZES[i]);
                if (SIZES[i] == prefs.fontSizeFactor()) cur = i;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.text_size)
                    .setSingleChoiceItems(labels, cur, (d, which) -> {
                        prefs.fontSizeFactor(SIZES[which]);
                        sizeValue.setText(labels[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        TextView localeValue = findViewById(R.id.valueLocale);
        localeValue.setText(localeLabel(prefs.locale()));
        findViewById(R.id.rowLocale).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.content_language)
                    .setSingleChoiceItems(LOCALE_LABELS, currentLocaleIndex(), (d, which) -> {
                        prefs.locale(LOCALES[which]);
                        localeValue.setText(LOCALE_LABELS[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        bindSwitch(R.id.swGestures, prefs.gestures(), () -> {
            prefs.gestures(!prefs.gestures());
            return prefs.gestures();
        });
        bindSwitch(R.id.swPull, prefs.pullToRefresh(), () -> {
            prefs.pullToRefresh(!prefs.pullToRefresh());
            return prefs.pullToRefresh();
        });
        bindSwitch(R.id.swTurbo, prefs.turboDownloads(), () -> {
            prefs.turboDownloads(!prefs.turboDownloads());
            return prefs.turboDownloads();
        });
        bindSwitch(R.id.swMediaGrabber, prefs.mediaGrabber(), () -> {
            prefs.mediaGrabber(!prefs.mediaGrabber());
            return prefs.mediaGrabber();
        });

        // Appearance (Compose screen: theme mode, dynamic color, accent)
        findViewById(R.id.rowAppearance).setOnClickListener(v ->
                startActivity(new Intent(this, com.zerium.gecko.ui.AppearanceActivity.class)));

        // Data
        findViewById(R.id.rowClearHistory).setOnClickListener(v ->
                confirm(R.string.clear_history_title, () -> {
                    new HistoryDB(this).clear();
                    Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
                }));
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
    }

    private String sizeLabel(float factor) {
        if (factor == 1.0f) return getString(R.string.text_size_default);
        return Math.round(factor * 100) + "%";
    }

    private int currentLocaleIndex() {
        String cur = prefs.locale();
        for (int i = 0; i < LOCALES.length; i++) {
            if (LOCALES[i].equals(cur)) return i;
        }
        return 0;
    }

    private String localeLabel(String code) {
        int idx = 0;
        for (int i = 0; i < LOCALES.length; i++) {
            if (LOCALES[i].equals(code)) { idx = i; break; }
        }
        return LOCALE_LABELS[idx];
    }

    private void refreshPermSummary() {
        TextView v = findViewById(R.id.valuePerms);
        int remembered = prefs.permissionMemoryCount();
        v.setText(getString(
                prefs.permDefault() == 1 ? R.string.site_permissions_deny : R.string.site_permissions_ask,
                remembered));
    }

    private void editPermissions() {
        String[] options = {
                getString(R.string.perm_default_ask),
                getString(R.string.perm_default_deny),
                getString(R.string.perm_clear_memory, prefs.permissionMemoryCount())
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.site_permissions)
                .setItems(options, (d, which) -> {
                    if (which == 0) prefs.permDefault(0);
                    else if (which == 1) prefs.permDefault(1);
                    else if (which == 2) {
                        prefs.clearPermissionMemory();
                        Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
                    }
                    refreshPermSummary();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Platform autofill (password managers): status + link to system settings. */
    private void openAutofillSettings() {
        TextView v = findViewById(R.id.valueAutofill);
        boolean enabled = false;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.view.autofill.AutofillManager afm = getSystemService(
                    android.view.autofill.AutofillManager.class);
            enabled = afm != null && afm.hasEnabledAutofillServices();
        }
        v.setText(enabled ? R.string.autofill_on : R.string.autofill_off);
        if (!enabled) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }
    }

    private void editCustomEngines() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_engines, null);
        EditText input = view.findViewById(R.id.enginesInput);
        input.setText(prefs.customEngines());
        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_engines)
                .setMessage(R.string.custom_engines_hint)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String raw = input.getText().toString();
                    List<Utils.Engine> parsed = Utils.parseCustomEngines(raw);
                    if (parsed.isEmpty()
                            && !raw.trim().isEmpty() && !raw.trim().equals("[]")) {
                        Toast.makeText(this, R.string.custom_engines_invalid,
                                Toast.LENGTH_LONG).show();
                    } else {
                        // store a normalized array of VALID entries
                        JSONArray arr = new JSONArray();
                        for (Utils.Engine e : parsed) {
                            try {
                                JSONObject o = new JSONObject();
                                o.put("name", e.name);
                                o.put("url", e.query);
                                arr.put(o);
                            } catch (Exception ignored) {}
                        }
                        prefs.customEngines(arr.toString());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface SwitchLogic { boolean toggle(); }

    private void bindSwitch(int id, boolean current, SwitchLogic logic) {
        com.google.android.material.materialswitch.MaterialSwitch sw = findViewById(id);
        sw.setChecked(current);
        sw.setOnClickListener(v -> sw.setChecked(logic.toggle()));
    }

    private void applyTheme(int which) {
        int mode = which == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : which == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
        getDelegate().applyDayNight();
    }

    private void pickEngine() {
        TextView value = findViewById(R.id.valueEngine);
        List<Utils.Engine> all = Utils.allEngines(prefs);
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).custom ? all.get(i).name + " \u2605" : all.get(i).name;
        }
        int current = Math.max(0, Math.min(prefs.searchEngine(), all.size() - 1));
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_engine)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    prefs.searchEngine(which);
                    value.setText(all.get(which).name);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editAllowlist() {
        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_allowlist, null);
        EditText input = view.findViewById(R.id.allowlistInput);
        input.setText(prefs.allowlist());
        new AlertDialog.Builder(this)
                .setTitle(R.string.allowlist_title)
                .setMessage(R.string.allowlist_message)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) ->
                        prefs.setAllowlist(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirm(int titleRes, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setPositiveButton(R.string.ok, (d, w) -> action.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAbout() {
        String version;
        try {
            version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.1.0";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_body, version))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
