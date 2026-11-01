package com.zerium.gecko;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/**
 * Zerium Spaces manager sheet: lists every space with its tab count and
 * isolation badge, switches the active space on tap, and exposes per-space
 * actions (rename, delete) on long-press. Also provides the create/rename
 * input dialogs. Programmatic layout, matching the menu sheet's Brave-style
 * language.
 */
public final class SpacesSheet {

    private SpacesSheet() {}

    public interface Host {
        Spaces spaces();

        /** Live tab count for a space id (open tabs of any engine state). */
        int tabCount(String spaceId);

        /** Optional one-line explainer shown under the title; null hides it. */
        String footerText();

        void onSwitch(Spaces.Space space);

        void onNewSpace();

        void onRename(Spaces.Space space);

        void onDelete(Spaces.Space space);
    }

    /** Text input result for the create/rename dialogs. */
    public interface NameCallback {
        void onName(String name, boolean isolated);
    }

    public static void show(AppCompatActivity act, Host host) {
        float d = act.getResources().getDisplayMetrics().density;
        BottomSheetDialog dialog = new BottomSheetDialog(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (10 * d), (int) (14 * d), (int) (10 * d), (int) (18 * d));
        root.setBackgroundResource(R.color.surface_container_lowest);

        TextView title = new TextView(act);
        title.setText(R.string.spaces_title);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(act, R.color.on_surface));
        title.setPadding((int) (10 * d), 0, (int) (10 * d), (int) (4 * d));
        root.addView(title);

        String footer = host.footerText();
        if (footer != null && !footer.isEmpty()) {
            TextView info = new TextView(act);
            info.setText(footer);
            info.setTextSize(12.5f);
            info.setTextColor(ContextCompat.getColor(act, R.color.on_surface_variant));
            info.setPadding((int) (10 * d), 0, (int) (10 * d), (int) (8 * d));
            root.addView(info);
        }

        List<Spaces.Space> all = host.spaces().all();
        String activeId = host.spaces().activeId();
        for (final Spaces.Space sp : all) {
            boolean active = sp.id.equals(activeId);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (56 * d));
            row.setPadding((int) (10 * d), 0, (int) (12 * d), 0);
            row.setBackground(ripple(act));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                host.onSwitch(sp);
            });
            row.setOnLongClickListener(v -> {
                showSpaceOptions(act, host, sp);
                return true;
            });

            row.addView(monogram(act, sp, d));

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding((int) (14 * d), 0, 0, 0);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout nameRow = new LinearLayout(act);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(act);
            name.setText(sp.name);
            name.setTextSize(15);
            name.setTextColor(ContextCompat.getColor(act, R.color.on_surface));
            if (active) name.setTypeface(Typeface.DEFAULT_BOLD);
            nameRow.addView(name);
            if (sp.isolated) nameRow.addView(badge(act, d,
                    act.getString(R.string.spaces_badge_isolated)));
            if (sp.id.equals(Spaces.DEFAULT_ID)) nameRow.addView(badge(act, d,
                    act.getString(R.string.spaces_badge_default)));
            col.addView(nameRow);

            TextView count = new TextView(act);
            count.setText(act.getString(R.string.spaces_tab_count, host.tabCount(sp.id)));
            count.setTextSize(12.5f);
            count.setTextColor(ContextCompat.getColor(act, R.color.on_surface_variant));
            col.addView(count);
            row.addView(col);

            if (active) {
                ImageView check = new ImageView(act);
                check.setImageResource(R.drawable.ic_check);
                check.setColorFilter(ContextCompat.getColor(act, R.color.primary));
                check.setLayoutParams(new LinearLayout.LayoutParams(
                        (int) (20 * d), (int) (20 * d)));
                row.addView(check);
            }
            root.addView(row);
        }

        // New space row.
        LinearLayout addRow = new LinearLayout(act);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setMinimumHeight((int) (52 * d));
        addRow.setPadding((int) (10 * d), 0, (int) (12 * d), 0);
        addRow.setBackground(ripple(act));
        addRow.setOnClickListener(v -> {
            dialog.dismiss();
            host.onNewSpace();
        });
        ImageView plus = new ImageView(act);
        plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(ContextCompat.getColor(act, R.color.on_surface_variant));
        GradientDrawable plusBg = new GradientDrawable();
        plusBg.setShape(GradientDrawable.OVAL);
        plusBg.setColor(ContextCompat.getColor(act, R.color.surface_container_high));
        plus.setBackground(plusBg);
        plus.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
        plus.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (36 * d), (int) (36 * d)));
        addRow.addView(plus);
        TextView addLbl = new TextView(act);
        addLbl.setText(R.string.spaces_new);
        addLbl.setTextSize(15);
        addLbl.setTextColor(ContextCompat.getColor(act, R.color.on_surface));
        addLbl.setPadding((int) (16 * d), 0, 0, 0);
        addRow.addView(addLbl);
        root.addView(addRow);

        dialog.setContentView(root);
        dialog.show();
    }

    /** Rename / delete options for one space (long-press on its row). */
    private static void showSpaceOptions(AppCompatActivity act, Host host, Spaces.Space sp) {
        boolean isDefault = sp.id.equals(Spaces.DEFAULT_ID);
        List<String> items = new java.util.ArrayList<>();
        items.add(act.getString(R.string.spaces_rename));
        if (!isDefault) items.add(act.getString(R.string.spaces_delete));
        new AlertDialog.Builder(act)
                .setTitle(sp.name)
                .setItems(items.toArray(new String[0]), (dlg, which) -> {
                    if (which == 0) host.onRename(sp);
                    else if (!isDefault) host.onDelete(sp);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Create-space dialog: name + isolation toggle (when supported). */
    public static void promptNewSpace(AppCompatActivity act, boolean isolationSupported,
                                      NameCallback cb) {
        float d = act.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * d);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText input = new EditText(act);
        input.setHint(R.string.spaces_name_hint);
        input.setSingleLine(true);
        box.addView(input);
        final CheckBox iso = new CheckBox(act);
        iso.setText(R.string.spaces_isolated);
        iso.setChecked(isolationSupported);
        iso.setEnabled(isolationSupported);
        box.addView(iso);
        if (!isolationSupported) {
            TextView note = new TextView(act);
            note.setText(R.string.spaces_isolated_unsupported);
            note.setTextSize(12.5f);
            note.setTextColor(ContextCompat.getColor(act, R.color.on_surface_variant));
            box.addView(note);
        }
        new AlertDialog.Builder(act)
                .setTitle(R.string.spaces_new)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    cb.onName(name, iso.isChecked());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Rename dialog for an existing space. */
    public static void promptRename(AppCompatActivity act, String preset, NameCallback cb) {
        float d = act.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * d);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText input = new EditText(act);
        input.setSingleLine(true);
        input.setText(preset);
        box.addView(input);
        new AlertDialog.Builder(act)
                .setTitle(R.string.spaces_rename)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    cb.onName(name, false);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static View monogram(AppCompatActivity act, Spaces.Space sp, float d) {
        TextView tv = new TextView(act);
        tv.setText(sp.name == null || sp.name.isEmpty()
                ? "S" : sp.name.substring(0, 1).toUpperCase());
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(act, R.color.primary));
        tv.setBackground(bg);
        tv.setTextColor(ContextCompat.getColor(act, R.color.on_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (34 * d), (int) (34 * d));
        tv.setLayoutParams(lp);
        return tv;
    }

    private static View badge(AppCompatActivity act, float d, String text) {
        TextView tv = new TextView(act);
        tv.setText(text);
        tv.setTextSize(10.5f);
        tv.setTextColor(ContextCompat.getColor(act, R.color.on_surface_variant));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(ContextCompat.getColor(act, R.color.surface_container_high));
        tv.setBackground(bg);
        tv.setPadding((int) (7 * d), (int) (2 * d), (int) (7 * d), (int) (2 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (8 * d), 0, 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static RippleDrawable ripple(AppCompatActivity act) {
        float d = act.getResources().getDisplayMetrics().density;
        android.content.res.TypedArray ta = act.getTheme().obtainStyledAttributes(
                new int[]{android.R.attr.colorControlHighlight});
        int highlight = ta.getColor(0, Color.parseColor("#1F000000"));
        ta.recycle();
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(14 * d);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(14 * d);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(highlight),
                content, mask);
    }
}
