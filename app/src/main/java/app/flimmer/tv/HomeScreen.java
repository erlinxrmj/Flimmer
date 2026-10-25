package app.flimmer.tv;

import android.app.Activity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Startseite: Filter-Chips oben, darunter das Video-Raster. */
public class HomeScreen extends Screen {

    private static final String FOR_YOU = "Für dich";

    /** Chip-Name → Kiosk-ID oder Suchbegriff (Präfix "q:"). */
    private static final String[][] CHIPS = {
            {FOR_YOU, ""},
            {"Live", Repo.KIOSK_LIVE},
            {"Musik", Repo.KIOSK_MUSIC},
            {"Gaming", Repo.KIOSK_GAMING},
            {"Filme & Trailer", Repo.KIOSK_MOVIES},
            {"Podcasts", Repo.KIOSK_PODCASTS},
            {"Nachrichten", "q:Nachrichten heute"},
            {"Sport", "q:Sport Highlights"},
            {"Comedy", "q:Comedy"},
            {"Technik", "q:Technik Review"},
            {"Kochen", "q:Rezept kochen"},
            {"Wissen", "q:Wissen Doku"},
            {"Autos", "q:Auto Test"},
    };

    private final VideoGrid grid;
    private final List<TextView> chips = new ArrayList<>();
    private int selected = 0;
    private int token;
    private Repo.Paged paged;
    private boolean filterChannels;

    public HomeScreen(Activity a) {
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
        for (int i = 0; i < CHIPS.length; i++) {
            TextView chip = Ui.chip(a, CHIPS[i][0]);
            final int idx = i;
            chip.setOnClickListener(v -> select(idx, idx == selected));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(a, 12);
            row.addView(chip, lp);
            chips.add(chip);
        }
        grid.setHeader(hs);
        grid.setListener(new VideoGrid.Listener() {
            @Override
            public void onVideo(Vid v) {
                PlayerActivity.open(activity, v.url);
            }

            @Override
            public void onVideoMenu(Vid v) {
                Menus.video(activity, v, null);
            }
        });
        Ui.setChipSelected(chips.get(0), true);
    }

    @Override
    protected void onShow(boolean firstTime) {
        if (firstTime) select(0, false);
    }

    @Override
    public boolean focusContent() {
        return grid.focusFirstVideo();
    }

    private void select(int idx, boolean force) {
        for (int i = 0; i < chips.size(); i++) Ui.setChipSelected(chips.get(i), i == idx);
        selected = idx;
        final int t = ++token;
        grid.setOnLoadMore(null);
        String key = CHIPS[idx][1];
        String label = CHIPS[idx][0];

        if (FOR_YOU.equals(label)) {
            grid.showLoading();
            Bg.run(() -> Repo.home(force), new Bg.Done<Repo.Home>() {
                @Override
                public void ok(Repo.Home home) {
                    if (t != token || !alive()) return;
                    if (home.items.isEmpty()) {
                        grid.showMessage("Gerade konnte nichts geladen werden.\nPrüfe die Internetverbindung und versuche es nochmal.");
                        return;
                    }
                    grid.setItems(home.items);
                    paged = home.more;
                    filterChannels = false;
                    if (paged != null) grid.setOnLoadMore(HomeScreen.this::loadMore);
                    maybeFocusFirst();
                }

                @Override
                public void fail(Exception e) {
                    if (t != token || !alive()) return;
                    grid.showMessage("Die Startseite konnte nicht geladen werden.\n" + msg(e));
                }
            });
            return;
        }

        grid.showLoading();
        final boolean isSearch = key.startsWith("q:");
        Bg.run(() -> isSearch ? Repo.search(key.substring(2)) : Repo.kiosk(key), new Bg.Done<Repo.Paged>() {
            @Override
            public void ok(Repo.Paged p) {
                if (t != token || !alive()) return;
                paged = p;
                filterChannels = isSearch;
                List<Vid> items = isSearch ? withoutChannels(p.items) : p.items;
                if (items.isEmpty()) {
                    grid.showMessage("Hier gibt es gerade nichts.");
                    return;
                }
                grid.setItems(items);
                grid.setOnLoadMore(HomeScreen.this::loadMore);
            }

            @Override
            public void fail(Exception e) {
                if (t != token || !alive()) return;
                grid.showMessage("Konnte nicht geladen werden.\n" + msg(e));
            }
        });
    }

    private void loadMore() {
        final Repo.Paged p = paged;
        final int t = token;
        if (p == null || !p.hasMore()) return;
        Bg.run(() -> p.more(false), new Bg.Done<List<Vid>>() {
            @Override
            public void ok(List<Vid> more) {
                if (t != token || !alive()) return;
                List<Vid> items = filterChannels ? withoutChannels(more) : more;
                if (!items.isEmpty()) grid.append(items);
                else grid.loadMoreDone();
            }

            @Override
            public void fail(Exception e) {
                grid.loadMoreDone();
            }
        });
    }

    private void maybeFocusFirst() {
        if (activity instanceof MainActivity && ((MainActivity) activity).contentShouldTakeFocus()) {
            grid.focusFirstVideo();
        }
    }

    private static List<Vid> withoutChannels(List<Vid> in) {
        List<Vid> out = new ArrayList<>();
        for (Vid v : in) if (!v.isChannel) out.add(v);
        return out;
    }

    static String msg(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "Keine Internetverbindung.";
        return e.getMessage() == null ? "" : e.getMessage();
    }
}
