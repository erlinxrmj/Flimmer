package app.flimmer.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

public class ChannelActivity extends Activity {

    public static void open(Context c, String url, String name) {
        if (url == null) return;
        Intent i = new Intent(c, ChannelActivity.class);
        i.putExtra("url", url);
        i.putExtra("name", name);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private VideoGrid grid;
    private ImageView banner, avatar;
    private TextView name, subs;
    private LinearLayout subscribe;
    private String url;
    private Repo.ChannelData data;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        url = getIntent().getStringExtra("url");
        String initialName = getIntent().getStringExtra("name");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        grid = new VideoGrid(this);
        grid.setShowUploader(false);
        root.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        grid.setHeader(buildHeader(initialName));
        grid.setListener(new VideoGrid.Listener() {
            @Override
            public void onVideo(Vid v) {
                PlayerActivity.open(ChannelActivity.this, v.url);
            }

            @Override
            public void onVideoMenu(Vid v) {
                Menus.video(ChannelActivity.this, v, null);
            }
        });
        load();
    }

    private View buildHeader(String initialName) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));

        FrameLayout bannerBox = new FrameLayout(this);
        float r = Ui.dp(this, 16);
        bannerBox.setBackground(Ui.round(Ui.SURFACE, r));
        Ui.clipRound(bannerBox, r);
        banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bannerBox.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x66000000}));
        bannerBox.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        h.addView(bannerBox, new LinearLayout.LayoutParams(-1, Ui.dp(this, 150)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));

        avatar = new ImageView(this);
        GradientDrawable ph = new GradientDrawable();
        ph.setShape(GradientDrawable.OVAL);
        ph.setColor(Ui.SURFACE);
        avatar.setBackground(ph);
        Ui.clipCircle(avatar);
        row.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        name = Ui.lines(Ui.text(this, 30, Color.WHITE, Ui.bold(this)), 1);
        name.setText(initialName == null ? "" : initialName);
        texts.addView(name);
        subs = Ui.text(this, 15, Ui.TEXT2, Ui.regular(this));
        subs.setPadding(0, Ui.dp(this, 8), 0, 0);
        texts.addView(subs);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        subscribe = Ui.iconButton(this, R.drawable.ic_subs, "Abonnieren");
        subscribe.setOnClickListener(v -> toggleSubscribe());
        row.addView(subscribe, new LinearLayout.LayoutParams(-2, -2));
        h.addView(row);

        TextView videosTitle = Ui.text(this, 20, Color.WHITE, Ui.bold(this));
        videosTitle.setText("Videos");
        videosTitle.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        h.addView(videosTitle);

        updateSubscribe();
        return h;
    }

    private void load() {
        grid.showLoading();
        Bg.run(() -> Repo.channel(url), new Bg.Done<Repo.ChannelData>() {
            @Override
            public void ok(Repo.ChannelData d) {
                if (destroyed) return;
                data = d;
                name.setText(d.name);
                String s = Fmt.subs(d.subscribers);
                subs.setText(s == null ? "" : s);
                Glide.with(ChannelActivity.this).load(d.avatar).circleCrop().into(avatar);
                if (d.banner != null) {
                    Glide.with(ChannelActivity.this).load(d.banner)
                            .placeholder(new ColorDrawable(Ui.SURFACE)).centerCrop().into(banner);
                }
                updateSubscribe();
                if (d.videos.items.isEmpty()) {
                    grid.showMessage("Dieser Kanal hat keine Videos.");
                } else {
                    grid.setItems(d.videos.items);
                    grid.setOnLoadMore(ChannelActivity.this::loadMore);
                    subscribe.requestFocus();
                }
            }

            @Override
            public void fail(Exception e) {
                if (destroyed) return;
                grid.showMessage("Der Kanal konnte nicht geladen werden.\n" + (e.getMessage() == null ? "" : e.getMessage()));
            }
        });
    }

    private void loadMore() {
        if (data == null || !data.videos.hasMore()) return;
        Bg.run(() -> data.videos.more(false), new Bg.Done<List<Vid>>() {
            @Override
            public void ok(List<Vid> more) {
                if (destroyed) return;
                if (!more.isEmpty()) grid.append(more);
                else grid.loadMoreDone();
            }

            @Override
            public void fail(Exception e) {
                grid.loadMoreDone();
            }
        });
    }

    private String subUrl() {
        return data != null && data.url != null ? data.url : url;
    }

    private void updateSubscribe() {
        boolean on = App.db().isSubscribed(subUrl()) || App.db().isSubscribed(url);
        Ui.setButtonLabel(subscribe, on ? "Abonniert" : "Abonnieren");
    }

    private void toggleSubscribe() {
        Db db = App.db();
        if (db.isSubscribed(subUrl()) || db.isSubscribed(url)) {
            db.unsubscribe(subUrl());
            db.unsubscribe(url);
        } else {
            String n = data != null ? data.name : name.getText().toString();
            db.subscribe(subUrl(), n, data != null ? data.avatar : null);
        }
        Repo.invalidateFeed();
        updateSubscribe();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
