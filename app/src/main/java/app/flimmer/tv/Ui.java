package app.flimmer.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Farben, Schriften und kleine Bausteine für die Oberfläche. */
public final class Ui {

    public static final int BG = 0xFF0F0F0F;
    public static final int RAIL = 0xFF0F0F0F;
    public static final int SURFACE = 0xFF272727;
    public static final int SURFACE_HI = 0xFF3F3F3F;
    public static final int TEXT = 0xFFF1F1F1;
    public static final int TEXT2 = 0xFFAAAAAA;
    public static final int ACCENT = 0xFFFF5A3C;
    public static final int LIVE = 0xFFE53935;

    private static Typeface regular, medium, semibold, bold;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    private static Typeface load(Context c, int res, int fallbackStyle) {
        try {
            return c.getResources().getFont(res);
        } catch (Exception e) {
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }

    public static Typeface regular(Context c) {
        if (regular == null) regular = load(c, R.font.inter_regular, Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium(Context c) {
        if (medium == null) medium = load(c, R.font.inter_medium, Typeface.NORMAL);
        return medium;
    }

    public static Typeface semibold(Context c) {
        if (semibold == null) semibold = load(c, R.font.inter_semibold, Typeface.BOLD);
        return semibold;
    }

    public static Typeface bold(Context c) {
        if (bold == null) bold = load(c, R.font.inter_bold, Typeface.BOLD);
        return bold;
    }

    public static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView lines(TextView t, int max) {
        t.setMaxLines(max);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable ring(int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(strokePx, strokeColor);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static void clipRound(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    public static void clipCircle(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
    }

    /**
     * Ein „Pillen“-Knopf wie die Filter-Chips oben auf der Startseite:
     * grau, bei Fokus weiss mit schwarzer Schrift.
     */
    public static TextView chip(Context c, String label) {
        TextView t = text(c, 15, TEXT, medium(c));
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        int h = dp(c, 18), v = dp(c, 10);
        t.setPadding(h, v, h, v);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSingleLine(true);
        styleChip(t, false, false);
        t.setOnFocusChangeListener((view, has) -> styleChip(t, has, Boolean.TRUE.equals(t.getTag(R.id.tag_selected))));
        return t;
    }

    public static void setChipSelected(TextView t, boolean selected) {
        t.setTag(R.id.tag_selected, selected);
        styleChip(t, t.hasFocus(), selected);
    }

    private static void styleChip(TextView t, boolean focused, boolean selected) {
        Context c = t.getContext();
        float r = dp(c, 10);
        if (focused) {
            t.setBackground(round(Color.WHITE, r));
            t.setTextColor(Color.BLACK);
            t.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
        } else if (selected) {
            t.setBackground(round(0xFFF1F1F1, r));
            t.setTextColor(Color.BLACK);
            t.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        } else {
            t.setBackground(round(SURFACE, r));
            t.setTextColor(TEXT);
            t.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
    }

    /** Knopf mit Symbol und Text (Kanal, Abonnieren, Qualität …). */
    public static LinearLayout iconButton(Context c, int icon, String label) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, 16), v = dp(c, 9);
        b.setPadding(h, v, h + dp(c, 4), v);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = new ImageView(c);
        if (icon != 0) {
            iv.setImageResource(icon);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20));
            lp.rightMargin = dp(c, 8);
            b.addView(iv, lp);
        }
        TextView t = text(c, 14, TEXT, medium(c));
        t.setText(label);
        t.setSingleLine(true);
        b.addView(t);
        b.setTag(R.id.tag_label, t);
        b.setTag(R.id.tag_icon, iv);
        styleIconButton(b, false);
        b.setOnFocusChangeListener((view, has) -> styleIconButton(b, has));
        return b;
    }

    public static void setButtonLabel(LinearLayout b, String label) {
        Object t = b.getTag(R.id.tag_label);
        if (t instanceof TextView) ((TextView) t).setText(label);
    }

    private static void styleIconButton(LinearLayout b, boolean focused) {
        Context c = b.getContext();
        b.setBackground(round(focused ? Color.WHITE : 0x33FFFFFF, dp(c, 20)));
        Object t = b.getTag(R.id.tag_label);
        if (t instanceof TextView) ((TextView) t).setTextColor(focused ? Color.BLACK : TEXT);
        Object iv = b.getTag(R.id.tag_icon);
        if (iv instanceof ImageView) ((ImageView) iv).setColorFilter(focused ? Color.BLACK : Color.WHITE);
    }
}
