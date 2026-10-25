package app.flimmer.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;

/** Einstellungen: Qualität, Werbe-/Sponsor-Überspringen, Verlauf, Region. */
public class SettingsScreen extends Screen {

    private interface Value {
        String get();
    }

    private final LinearLayout list;
    private View firstRow;
    private String pendingFocus;

    private static final int[] HEIGHTS = {2160, 1440, 1080, 720, 480, 360};
    private static final String[][] CODECS = {
            {"avc", "Kompatibel (H.264) – empfohlen für ältere Fernseher"},
            {"vp9", "VP9 bevorzugt – bessere Qualität, braucht neueren Fernseher"},
            {"any", "Beste verfügbare"},
    };
    private static final String[][] REGIONS = {
            {"CH", "Schweiz"}, {"DE", "Deutschland"}, {"AT", "Österreich"},
            {"US", "USA"}, {"GB", "Grossbritannien"}, {"IT", "Italien"}, {"FR", "Frankreich"},
    };

    public SettingsScreen(Activity a) {
        super(a);
        ScrollView sv = new ScrollView(a);
        sv.setClipToPadding(false);
        sv.setPadding(Ui.dp(a, 36), Ui.dp(a, 24), Ui.dp(a, 36), Ui.dp(a, 60));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        addView(sv, new LayoutParams(-1, -1));
        build();
    }

    @Override
    protected void onShow(boolean firstTime) {}

    @Override
    public boolean focusContent() {
        return firstRow != null && firstRow.requestFocus();
    }

    private void build() {
        list.removeAllViews();
        firstRow = null;
        Context c = getContext();

        TextView title = Ui.text(c, 30, Color.WHITE, Ui.bold(c));
        title.setText("Einstellungen");
        title.setPadding(Ui.dp(c, 8), 0, 0, Ui.dp(c, 6));
        list.addView(title);

        section("Wiedergabe");
        row("Maximale Qualität", () -> Prefs.maxHeight() + "p", () -> {
            int cur = Prefs.maxHeight();
            int idx = 0;
            for (int i = 0; i < HEIGHTS.length; i++) if (HEIGHTS[i] == cur) idx = i;
            Prefs.maxHeight(HEIGHTS[(idx + 1) % HEIGHTS.length]);
        });
        row("Videoformat", () -> {
            for (String[] s : CODECS) if (s[0].equals(Prefs.codec())) return s[1];
            return CODECS[0][1];
        }, () -> {
            int idx = 0;
            for (int i = 0; i < CODECS.length; i++) if (CODECS[i][0].equals(Prefs.codec())) idx = i;
            Prefs.codec(CODECS[(idx + 1) % CODECS.length][0]);
        });
        row("Nächstes Video automatisch abspielen", () -> onOff(Prefs.autoplay()),
                () -> Prefs.autoplay(!Prefs.autoplay()));
        row("Shorts ausblenden", () -> onOff(Prefs.hideShorts()), () -> {
            Prefs.hideShorts(!Prefs.hideShorts());
            Repo.invalidateFeed();
        });

        section("Werbung & Unterbrechungen");
        note("Eingeblendete Werbung gibt es in Flimmer nie: Die Videos werden direkt geladen. "
                + "SponsorBlock überspringt zusätzlich Werbung, die Creator selbst ins Video schneiden.");
        row("Abschnitte automatisch überspringen (SponsorBlock)", () -> onOff(Prefs.sponsorBlock()),
                () -> {
                    Prefs.sponsorBlock(!Prefs.sponsorBlock());
                    pendingFocus = "Abschnitte automatisch überspringen (SponsorBlock)";
                    post(this::build);
                });
        if (Prefs.sponsorBlock()) {
            for (Map.Entry<String, String> e : Prefs.SB_CATEGORIES.entrySet()) {
                final String cat = e.getKey();
                row("   " + e.getValue(), () -> onOff(Prefs.sbCategory(cat)),
                        () -> Prefs.sbCategory(cat, !Prefs.sbCategory(cat)));
            }
        }

        section("Startseite & Inhalte");
        row("Region für Angesagtes", () -> {
            for (String[] r : REGIONS) if (r[0].equals(Prefs.region())) return r[1];
            return Prefs.region();
        }, () -> {
            int idx = 0;
            for (int i = 0; i < REGIONS.length; i++) if (REGIONS[i][0].equals(Prefs.region())) idx = i;
            Prefs.region(REGIONS[(idx + 1) % REGIONS.length][0]);
            App.applyRegion();
            Repo.invalidateFeed();
        });
        row("Startseite neu berechnen", () -> "Empfehlungen sofort aktualisieren", () -> {
            Repo.invalidateFeed();
            toast("Wird beim nächsten Öffnen der Startseite neu geladen");
        });

        section("Verlauf (nur auf diesem Fernseher gespeichert)");
        row("Verlauf pausieren", () -> onOff(Prefs.historyPaused()),
                () -> Prefs.historyPaused(!Prefs.historyPaused()));
        row("Wiedergabeverlauf löschen", () -> "Angesehene Videos und Fortschritt", () ->
                confirm("Wiedergabeverlauf löschen?", () -> {
                    App.db().clearHistory();
                    Repo.invalidateFeed();
                    toast("Verlauf gelöscht");
                }));
        row("Suchverlauf löschen", () -> "Alle gespeicherten Suchbegriffe", () ->
                confirm("Suchverlauf löschen?", () -> {
                    App.db().clearSearches();
                    toast("Suchverlauf gelöscht");
                }));

        section("Über");
        note("Flimmer 1.0 – kein Konto, keine Werbung, kein Tracking. Abos, Verlauf und Suchen bleiben auf diesem Gerät. "
                + "Videos über NewPipe Extractor, übersprungene Abschnitte über die SponsorBlock-Community.");
    }

    private static String onOff(boolean b) {
        return b ? "An" : "Aus";
    }

    private void section(String text) {
        Context c = getContext();
        TextView t = Ui.text(c, 15, Ui.ACCENT, Ui.semibold(c));
        t.setText(text);
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 26), 0, Ui.dp(c, 8));
        list.addView(t);
    }

    private void note(String text) {
        Context c = getContext();
        TextView t = Ui.text(c, 13.5f, Ui.TEXT2, Ui.regular(c));
        t.setText(text);
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        t.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 120), Ui.dp(c, 10));
        list.addView(t);
    }

    private void row(String label, Value value, Runnable onClick) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 16));
        TextView l = Ui.text(c, 16, Ui.TEXT, Ui.medium(c));
        l.setText(label);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(c, 15, Ui.TEXT2, Ui.regular(c));
        v.setText(value.get());
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = Ui.dp(c, 24);
        r.addView(v, vlp);
        r.setOnFocusChangeListener((view, has) -> {
            r.setBackground(has ? Ui.round(Color.WHITE, Ui.dp(c, 12)) : null);
            l.setTextColor(has ? Color.BLACK : Ui.TEXT);
            v.setTextColor(has ? 0xFF444444 : Ui.TEXT2);
        });
        r.setOnClickListener(view -> {
            onClick.run();
            v.setText(value.get());
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(c, 2);
        list.addView(r, lp);
        if (firstRow == null) firstRow = r;
        if (label.equals(pendingFocus)) {
            pendingFocus = null;
            r.post(r::requestFocus);
        }
    }

    private void confirm(String title, Runnable yes) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setPositiveButton("Löschen", (d, w) -> yes.run())
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void toast(String s) {
        android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
