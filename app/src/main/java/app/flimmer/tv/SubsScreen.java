package app.flimmer.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

/** Abos: gefolgte Kanäle oben, darunter ihre neuesten Videos. Ohne Konto, nur auf diesem Gerät. */
public class SubsScreen extends Screen {

    private final VideoGrid grid;
    private final LinearLayout channelRow;
    private int token;
    private int lastSubCount = -1;

    public SubsScreen(Activity a) {
        super(a);
        grid = new VideoGrid(a);
        addView(grid, new LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8), 0);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        channelRow = new LinearLayout(a);
        channelRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(channelRow);
        header.addView(hs);
        TextView title = Ui.text(a, 22, Color.WHITE, Ui.bold(a));
        title.setText("Neueste Videos");
        title.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 4));
        header.addView(title);
        grid.setHeader(header);
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
    }

    @Override
    protected void onShow(boolean firstTime) {
        int n = App.db().subs().size();
        if (firstTime || n != lastSubCount) load(false);
    }

    @Override
    public void onResumeScreen() {
        if (App.db().subs().size() != lastSubCount) load(false);
    }

    @Override
    public boolean focusContent() {
        if (channelRow.getChildCount() > 0) return channelRow.getChildAt(0).requestFocus();
        return grid.focusFirstVideo();
    }

    private void load(boolean force) {
        List<Db.Channel> subs = App.db().subs();
        lastSubCount = subs.size();
        buildChannelRow(subs);
        if (subs.isEmpty()) {
            grid.showMessage("Du folgst noch keinem Kanal.\n\nÖffne ein Video oder einen Kanal und wähle „Abonnieren“. "
                    + "Die neuesten Videos erscheinen dann hier und oben auf der Startseite – ganz ohne Konto.");
            return;
        }
        final int t = ++token;
        grid.showLoading();
        Bg.run(() -> Repo.subsFeed(force), new Bg.Done<List<Vid>>() {
            @Override
            public void ok(List<Vid> feed) {
                if (t != token || !alive()) return;
                if (feed.isEmpty()) grid.showMessage("Keine neuen Videos gefunden.");
                else grid.setItems(feed);
            }

            @Override
            public void fail(Exception e) {
                if (t != token || !alive()) return;
                grid.showMessage("Konnte nicht geladen werden.\n" + HomeScreen.msg(e));
            }
        });
    }

    private void buildChannelRow(List<Db.Channel> subs) {
        channelRow.removeAllViews();
        Context c = getContext();
        if (!subs.isEmpty()) {
            TextView refresh = Ui.chip(c, "↻  Aktualisieren");
            refresh.setOnClickListener(v -> load(true));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            rlp.rightMargin = Ui.dp(c, 18);
            rlp.gravity = Gravity.CENTER_VERTICAL;
            channelRow.addView(refresh, rlp);
        }
        for (Db.Channel ch : subs) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
            ImageView av = new ImageView(c);
            GradientDrawable ph = new GradientDrawable();
            ph.setShape(GradientDrawable.OVAL);
            ph.setColor(Ui.SURFACE);
            av.setBackground(ph);
            Ui.clipCircle(av);
            item.addView(av, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
            TextView name = Ui.lines(Ui.text(c, 12.5f, Ui.TEXT2, Ui.medium(c)), 1);
            name.setText(ch.name);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(c, 8), 0, 0);
            item.addView(name, new LinearLayout.LayoutParams(Ui.dp(c, 88), -2));
            if (ch.avatar != null) Glide.with(av).load(ch.avatar).circleCrop().into(av);
            item.setOnFocusChangeListener((v, has) -> {
                item.setBackground(has ? Ui.round(0x26FFFFFF, Ui.dp(c, 14)) : null);
                name.setTextColor(has ? Color.WHITE : Ui.TEXT2);
                item.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
            });
            item.setOnClickListener(v -> ChannelActivity.open(activity, ch.url, ch.name));
            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(activity)
                        .setTitle(ch.name)
                        .setItems(new String[]{"Kanal öffnen", "Deabonnieren"}, (d, which) -> {
                            if (which == 0) ChannelActivity.open(activity, ch.url, ch.name);
                            else {
                                App.db().unsubscribe(ch.url);
                                Repo.invalidateFeed();
                                load(false);
                            }
                        })
                        .show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 6);
            channelRow.addView(item, lp);
        }
    }
}
