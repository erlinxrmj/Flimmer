package app.flimmer.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Hauptfenster: Navigationsleiste links (klappt bei Fokus auf), rechts der aktive Bereich. */
public class MainActivity extends Activity {

    private static final int HOME = 0, SEARCH = 1, SUBS = 2, LIBRARY = 3, SETTINGS = 4;
    private static final String[] LABELS = {"Startseite", "Suche", "Abos", "Mediathek", "Einstellungen"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_subs,
            R.drawable.ic_library, R.drawable.ic_settings};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout rail;
    private FrameLayout content;
    private View contentDim;
    private TextView logoText;
    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private final List<ImageView> navIcons = new ArrayList<>();
    private final Screen[] screens = new Screen[5];
    private int current = -1;
    private boolean railOpen;
    private ValueAnimator railAnim;
    private int railClosed, railOpenW;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        railClosed = Ui.dp(this, 84);
        railOpenW = Ui.dp(this, 236);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        content = new FrameLayout(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.leftMargin = railClosed;
        root.addView(content, clp);

        contentDim = new View(this);
        contentDim.setBackgroundColor(0x99000000);
        contentDim.setAlpha(0f);
        contentDim.setClickable(false);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, -1);
        dlp.leftMargin = railClosed;
        root.addView(contentDim, dlp);

        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setBackgroundColor(Ui.RAIL);
        rail.setPadding(Ui.dp(this, 14), Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 22));
        rail.setElevation(Ui.dp(this, 6));
        root.addView(rail, new FrameLayout.LayoutParams(railClosed, -1));

        // Logo
        LinearLayout logo = new LinearLayout(this);
        logo.setOrientation(LinearLayout.HORIZONTAL);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.setPadding(Ui.dp(this, 6), 0, 0, Ui.dp(this, 30));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.mipmap.ic_launcher);
        logo.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        logoText = Ui.text(this, 22, Color.WHITE, Ui.bold(this));
        logoText.setText("Flimmer");
        logoText.setPadding(Ui.dp(this, 12), 0, 0, 0);
        logoText.setSingleLine(true);
        logoText.setAlpha(0f);
        logo.addView(logoText);
        rail.addView(logo);

        for (int i = 0; i < LABELS.length; i++) {
            if (i == SETTINGS) {
                View spacer = new View(this);
                rail.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
            }
            rail.addView(navItem(i));
        }

        setContentView(root);
        show(HOME);
        content.post(this::focusContent);
    }

    private View navItem(int idx) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setClickable(true);
        int p = Ui.dp(this, 15);
        item.setPadding(p, Ui.dp(this, 13), p, Ui.dp(this, 13));
        ImageView icon = new ImageView(this);
        icon.setImageResource(ICONS[idx]);
        item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        TextView label = Ui.text(this, 17, Ui.TEXT, Ui.semibold(this));
        label.setText(LABELS[idx]);
        label.setSingleLine(true);
        label.setPadding(Ui.dp(this, 18), 0, 0, 0);
        label.setAlpha(0f);
        item.addView(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 8);
        item.setLayoutParams(lp);

        navItems.add(item);
        navLabels.add(label);
        navIcons.add(icon);

        item.setOnFocusChangeListener((v, has) -> {
            styleNav();
            handler.post(this::updateRailState);
            if (has && idx != current) {
                // beim Durchblättern kurz warten, damit nicht jeder Bereich sofort lädt
                handler.removeCallbacks(pendingSwitch);
                pendingIndex = idx;
                handler.postDelayed(pendingSwitch, 280);
            }
        });
        item.setOnClickListener(v -> {
            handler.removeCallbacks(pendingSwitch);
            if (idx != current) show(idx);
            focusContent();
        });
        item.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                handler.removeCallbacks(pendingSwitch);
                if (idx != current) show(idx);
                focusContent();
                return true;
            }
            return false;
        });
        return item;
    }

    private int pendingIndex = -1;
    private final Runnable pendingSwitch = () -> {
        if (pendingIndex >= 0 && pendingIndex != current) show(pendingIndex);
    };

    private void focusContent() {
        Screen s = screens[current];
        if (s == null || !s.focusContent()) {
            View v = content.focusSearch(View.FOCUS_RIGHT);
            if (v != null) v.requestFocus();
        }
    }

    /** Ob ein Bereich nach dem Laden selbst den Fokus nehmen darf (nur, wenn die Leiste ihn nicht hat). */
    public boolean contentShouldTakeFocus() {
        View f = getCurrentFocus();
        return !interacted || f == null;
    }

    private boolean interacted;

    private void show(int idx) {
        if (screens[idx] == null) {
            switch (idx) {
                case HOME: screens[idx] = new HomeScreen(this); break;
                case SEARCH: screens[idx] = new SearchScreen(this); break;
                case SUBS: screens[idx] = new SubsScreen(this); break;
                case LIBRARY: screens[idx] = new LibraryScreen(this); break;
                default: screens[idx] = new SettingsScreen(this); break;
            }
            content.addView(screens[idx], new FrameLayout.LayoutParams(-1, -1));
        }
        for (int i = 0; i < screens.length; i++) {
            if (screens[i] != null) screens[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        current = idx;
        styleNav();
        screens[idx].show();
    }

    private void styleNav() {
        for (int i = 0; i < navItems.size(); i++) {
            LinearLayout item = navItems.get(i);
            boolean focused = item.hasFocus();
            boolean selected = i == current;
            if (focused) {
                item.setBackground(Ui.round(Color.WHITE, Ui.dp(this, 14)));
                navIcons.get(i).setColorFilter(Color.BLACK);
                navLabels.get(i).setTextColor(Color.BLACK);
            } else if (selected) {
                item.setBackground(Ui.round(Ui.SURFACE, Ui.dp(this, 14)));
                navIcons.get(i).setColorFilter(Color.WHITE);
                navLabels.get(i).setTextColor(Color.WHITE);
            } else {
                item.setBackground(null);
                navIcons.get(i).setColorFilter(0xFF9A9A9A);
                navLabels.get(i).setTextColor(0xFFBBBBBB);
            }
        }
    }

    private void updateRailState() {
        boolean open = rail.hasFocus();
        if (open == railOpen) return;
        railOpen = open;
        if (railAnim != null) railAnim.cancel();
        int from = rail.getLayoutParams().width;
        int to = open ? railOpenW : railClosed;
        railAnim = ValueAnimator.ofInt(from, to);
        railAnim.setDuration(170);
        railAnim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = rail.getLayoutParams();
            lp.width = (int) a.getAnimatedValue();
            rail.setLayoutParams(lp);
        });
        railAnim.start();
        float alpha = open ? 1f : 0f;
        for (TextView t : navLabels) t.animate().alpha(alpha).setDuration(170).start();
        logoText.animate().alpha(alpha).setDuration(170).start();
        contentDim.animate().alpha(open ? 1f : 0f).setDuration(170).start();
    }

    private static boolean isInside(View parent, View child) {
        if (child == parent) return true;
        ViewParent p = child.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            interacted = true;
            View f = getCurrentFocus();
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && f != null && isInside(content, f)) {
                View next = f.focusSearch(View.FOCUS_LEFT);
                if (next == null || isInside(rail, next)) {
                    // aus dem Inhalt immer auf den aktiven Menüpunkt springen
                    navItems.get(current).requestFocus();
                    return true;
                }
            }
            if (code == KeyEvent.KEYCODE_BACK && e.getRepeatCount() == 0) {
                if (f != null && isInside(content, f)) {
                    navItems.get(current).requestFocus();
                    return true;
                }
                if (current != HOME) {
                    handler.removeCallbacks(pendingSwitch);
                    show(HOME);
                    navItems.get(HOME).requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current >= 0 && screens[current] != null) screens[current].onResumeScreen();
    }
}
