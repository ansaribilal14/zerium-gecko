package com.zerium.gecko;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Brave-style sectioned menu sheet: a leading row of circular quick
 * actions, then sectioned icon rows separated by hairline dividers,
 * rendered inside a BottomSheetDialog. Data-driven; the host supplies
 * entries and receives them back through {@link Click}.
 */
public final class MenuSheet {

    private MenuSheet() {}

    public static final int QUICK_ACTION = 0;
    public static final int ITEM = 1;
    public static final int CHECKABLE = 2;
    public static final int DIVIDER = 3;

    /** One row (or divider) of the sheet. */
    public static class Entry {
        public final int type;
        public final int id;
        public final int icon;
        public final int labelRes;
        public final String label;
        public final boolean checked;
        public final boolean enabled;

        private Entry(int type, int id, int icon, int labelRes, String label,
                      boolean checked, boolean enabled) {
            this.type = type;
            this.id = id;
            this.icon = icon;
            this.labelRes = labelRes;
            this.label = label;
            this.checked = checked;
            this.enabled = enabled;
        }
    }

    public interface Click {
        void onEntry(Entry e);
    }

    public static Entry action(int id, @StringRes int label, @DrawableRes int icon) {
        return new Entry(QUICK_ACTION, id, icon, label, null, false, true);
    }

    public static Entry action(int id, @StringRes int label, @DrawableRes int icon, boolean enabled) {
        return new Entry(QUICK_ACTION, id, icon, label, null, false, enabled);
    }

    public static Entry item(int id, @StringRes int label, @DrawableRes int icon) {
        return new Entry(ITEM, id, icon, label, null, false, true);
    }

    public static Entry item(int id, @StringRes int label, @DrawableRes int icon, boolean enabled) {
        return new Entry(ITEM, id, icon, label, null, false, enabled);
    }

    public static Entry item(int id, String label, @DrawableRes int icon, boolean enabled) {
        return new Entry(ITEM, id, icon, 0, label, false, enabled);
    }

    public static Entry check(int id, @StringRes int label, @DrawableRes int icon, boolean checked) {
        return new Entry(CHECKABLE, id, icon, label, null, checked, true);
    }

    public static Entry divider() {
        return new Entry(DIVIDER, 0, 0, 0, null, false, true);
    }

    public static void show(AppCompatActivity act, List<Entry> entries, Click click) {
        float d = act.getResources().getDisplayMetrics().density;
        BottomSheetDialog dialog = new BottomSheetDialog(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (10 * d), (int) (12 * d), (int) (10 * d), (int) (18 * d));
        root.setBackgroundResource(R.color.surface_container_lowest);

        // Quick-action row: leading actions rendered as evenly-spaced
        // circular buttons (Brave style — icons only, no labels).
        List<Entry> actions = new ArrayList<>();
        for (Entry e : entries) {
            if (e.type == QUICK_ACTION) actions.add(e);
        }
        if (!actions.isEmpty()) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, (int) (10 * d));
            for (final Entry e : actions) {
                LinearLayout cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                cell.setContentDescription(entryText(act, e));
                ImageView iv = circleIcon(act, e.icon, e.enabled, d);
                if (e.enabled) {
                    iv.setOnClickListener(v -> {
                        dialog.dismiss();
                        click.onEntry(e);
                    });
                } else {
                    iv.setAlpha(0.45f);
                }
                cell.addView(iv);
                row.addView(cell);
            }
            root.addView(row);
        }

        // Sectioned item rows with hairline dividers.
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        boolean dividerPending = false;
        for (final Entry e : entries) {
            if (e.type == QUICK_ACTION) continue;
            if (e.type == DIVIDER) {
                if (list.getChildCount() > 0) dividerPending = true;
                continue;
            }
            if (dividerPending && list.getChildCount() > 0) {
                list.addView(hairline(act, d));
                dividerPending = false;
            }
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (52 * d));
            row.setPadding((int) (8 * d), 0, (int) (12 * d), 0);
            row.setBackground(ripple(act));
            row.setContentDescription(entryText(act, e));
            row.setOnClickListener(v -> {
                if (!e.enabled) return;
                dialog.dismiss();
                click.onEntry(e);
            });
            row.addView(circleIcon(act, e.icon, e.enabled, d));
            TextView lbl = new TextView(act);
            lbl.setText(entryText(act, e));
            lbl.setTextSize(15);
            lbl.setTextColor(ContextCompat.getColor(act, e.enabled
                    ? R.color.on_surface : R.color.on_surface_variant));
            lbl.setPadding((int) (16 * d), 0, (int) (8 * d), 0);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(lbl);
            if (e.type == CHECKABLE && e.checked) {
                ImageView check = new ImageView(act);
                check.setImageResource(R.drawable.ic_check);
                check.setColorFilter(ContextCompat.getColor(act, R.color.primary));
                check.setLayoutParams(new LinearLayout.LayoutParams(
                        (int) (20 * d), (int) (20 * d)));
                row.addView(check);
            } else if (e.type == CHECKABLE) {
                View spacer = new View(act);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        (int) (20 * d), (int) (20 * d)));
                row.addView(spacer);
            }
            if (!e.enabled) row.setAlpha(0.45f);
            list.addView(row);
        }
        root.addView(list);
        dialog.setContentView(root);
        dialog.show();
    }

    private static String entryText(AppCompatActivity act, Entry e) {
        return e.label != null ? e.label : act.getString(e.labelRes);
    }

    /** Rounded ripple surface resolved from the theme's highlight color. */
    private static RippleDrawable ripple(AppCompatActivity act) {
        float d = act.getResources().getDisplayMetrics().density;
        TypedArray ta = act.getTheme().obtainStyledAttributes(
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

    private static ImageView circleIcon(AppCompatActivity act, int icon,
                                        boolean enabled, float d) {
        ImageView iv = new ImageView(act);
        int size = (int) (36 * d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setImageResource(icon);
        iv.setColorFilter(ContextCompat.getColor(act, enabled
                ? R.color.on_surface_variant : R.color.outline));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(act, R.color.surface_container_high));
        iv.setBackground(bg);
        iv.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
        return iv;
    }

    private static View hairline(AppCompatActivity act, float d) {
        View v = new View(act);
        v.setBackgroundColor(ContextCompat.getColor(act, R.color.outline_variant));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) d));
        lp.setMargins((int) (52 * d), (int) (4 * d), (int) (8 * d), (int) (4 * d));
        v.setLayoutParams(lp);
        return v;
    }
}
