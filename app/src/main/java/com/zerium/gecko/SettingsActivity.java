package com.zerium.gecko;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/** Settings: search engine, blocking, privacy, appearance, data. */
public class SettingsActivity extends AppCompatActivity {

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

        // Search engine
        TextView engineValue = findViewById(R.id.valueEngine);
        engineValue.setText(Utils.engine(prefs).name);
        findViewById(R.id.rowEngine).setOnClickListener(v -> pickEngine());

        // Content blocking
        bindSwitch(R.id.swBlockAds, prefs.blockAds(), () -> {
            prefs.blockAds(!prefs.blockAds());
            return prefs.blockAds();
        });

        // Privacy
        bindSwitch(R.id.swJs, prefs.javascriptEnabled(), () -> {
            prefs.javascriptEnabled(!prefs.javascriptEnabled());
            return prefs.javascriptEnabled();
        });

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

        // Data
        findViewById(R.id.rowClearHistory).setOnClickListener(v ->
                confirm(R.string.clear_history_title, () -> {
                    new HistoryDB(this).clear();
                    Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
                }));
        findViewById(R.id.rowAllowlist).setOnClickListener(v -> editAllowlist());

        // About
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
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
        String[] names = new String[Utils.ENGINES.length];
        for (int i = 0; i < Utils.ENGINES.length; i++) names[i] = Utils.ENGINES[i].name;
        int current = Math.max(0, Math.min(prefs.searchEngine(), Utils.ENGINES.length - 1));
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_engine)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    prefs.searchEngine(which);
                    value.setText(Utils.ENGINES[which].name);
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
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.0.0";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_body, version))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
