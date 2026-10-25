package app.flimmer.tv;

import android.app.Activity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Mediathek: Verlauf und „Später ansehen“, beides nur lokal gespeichert. */
public class LibraryScreen extends Screen {

    private final VideoGrid grid;
    private final TextView chipHistory, chipLater;
    private boolean showLater;

    public LibraryScreen(Activity a) {
        super(a);
        grid = new VideoGrid(a);
        addView(grid, new LayoutParams(-1, -1));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 6));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        chipHistory = Ui.chip(a, "Verlauf");
        chipLater = Ui.chip(a, "Später ansehen");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(a, 12);
        row.addView(chipHistory, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp2.rightMargin = Ui.dp(a, 12);
        row.addView(chipLater, lp2);
        chipHistory.setOnClickListener(v -> {
            showLater = false;
            refresh();
        });
        chipLater.setOnClickListener(v -> {
            showLater = true;
            refresh();
        });
        grid.setHeader(hs);
        grid.setListener(new VideoGrid.Listener() {
            @Override
            public void onVideo(Vid v) {
                PlayerActivity.open(activity, v.url);
            }

            @Override
            public void onVideoMenu(Vid v) {
                Menus.video(activity, v, () -> {
                    if (showLater) App.db().setLater(v, false);
                    else App.db().removeHistory(v.id);
                    refresh();
                });
            }
        });
    }

    @Override
    protected void onShow(boolean firstTime) {
        refresh();
    }

    @Override
    public void onResumeScreen() {
        refresh();
    }

    @Override
    public boolean focusContent() {
        return grid.focusFirstVideo();
    }

    private void refresh() {
        Ui.setChipSelected(chipHistory, !showLater);
        Ui.setChipSelected(chipLater, showLater);
        List<Vid> list = showLater ? App.db().later() : App.db().history(300);
        if (list.isEmpty()) {
            grid.showMessage(showLater
                    ? "Noch nichts gespeichert.\nIm Player oder per langem Drücken auf ein Video: „Später ansehen“."
                    : (Prefs.historyPaused() ? "Der Verlauf ist pausiert (siehe Einstellungen)." : "Dein Verlauf ist leer."));
        } else {
            grid.setItems(list);
        }
    }
}
