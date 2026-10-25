package app.flimmer.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";

    public static void open(Context c, String url) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra(EXTRA_URL, url);
        c.startActivity(i);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Playback playback = new Playback();

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout topOverlay;
    private TextView titleView, metaView, skipToast, seekLabel, errorText, upNextTitle;
    private LinearLayout errorBox, relatedPanel;
    private LinearLayout btnChannel, btnSubscribe, btnLater, btnQuality, btnSpeed;
    private ImageView channelAvatar;
    private ProgressBar loading;
    private RecyclerView relatedList;
    private final RelatedAdapter relatedAdapter = new RelatedAdapter();

    private String url;
    private StreamInfo info;
    private Vid vid;
    private List<Vid> related = new ArrayList<>();
    private List<SponsorBlock.Segment> segments = new ArrayList<>();
    private List<Integer> heights = new ArrayList<>();
    private int maxHeight;
    private String channelAvatarUrl;
    private boolean controllerShown;
    private boolean destroyed;
    private int loadToken;
    private int retries;
    private int countdown = -1;
    private long lastSkipAt;

    private final Runnable ticker = new Runnable() {
        private int saveCounter;

        @Override
        public void run() {
            if (player != null) {
                checkSegments();
                if (player.isPlaying() && ++saveCounter >= 20) { // ca. alle 5 s
                    saveCounter = 0;
                    savePosition();
                }
            }
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (countdown < 0) return;
            if (countdown == 0) {
                countdown = -1;
                if (!related.isEmpty()) load(related.get(0).url);
                return;
            }
            upNextTitle.setText("Nächstes Video in " + countdown + " s  •  OK zum Abspielen, Zurück zum Abbrechen");
            countdown--;
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        player.setPlaybackSpeed(Prefs.speed());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    loading.setVisibility(View.GONE);
                    retries = 0;
                } else if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    loading.setVisibility(View.GONE);
                    onEnded();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                android.util.Log.w("Flimmer", "Wiedergabefehler", error);
                if (retries < 2 && url != null) {
                    // Links laufen nach einiger Zeit ab: neu laden und an gleicher Stelle weitermachen
                    retries++;
                    long pos = player.getCurrentPosition();
                    reload(pos);
                } else {
                    showError("Das Video konnte nicht abgespielt werden.\n" + error.getErrorCodeName());
                }
            }
        });
        playerView.setPlayer(player);
        handler.post(ticker);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String u = intent.getStringExtra(EXTRA_URL);
        if (u == null && intent.getData() != null) u = intent.getData().toString();
        if (u == null) {
            finish();
            return;
        }
        if (Vid.idFromUrl(u) == null) {
            // Kein Video-Link, vermutlich ein Kanal
            ChannelActivity.open(this, u, null);
            finish();
            return;
        }
        load(u);
    }

    // ------------------------------------------------------------------ Oberfläche

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player_view, root, false);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        playerView.setControllerShowTimeoutMs(4500);
        playerView.setControllerAutoShow(true);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowSubtitleButton(false);
        playerView.setShowShuffleButton(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controllerShown = visibility == View.VISIBLE;
            topOverlay.animate().alpha(controllerShown ? 1f : 0f).setDuration(180)
                    .withEndAction(() -> {
                        if (!controllerShown) topOverlay.setVisibility(View.GONE);
                    }).start();
            if (controllerShown) topOverlay.setVisibility(View.VISIBLE);
        });

        // Oben: Titel, Kanal, Knöpfe
        topOverlay = new LinearLayout(this);
        topOverlay.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable topGrad = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE6000000, 0x99000000, 0x00000000});
        topOverlay.setBackground(topGrad);
        int pad = Ui.dp(this, 40);
        topOverlay.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 60));
        titleView = Ui.lines(Ui.text(this, 24, Color.WHITE, Ui.bold(this)), 2);
        topOverlay.addView(titleView);
        metaView = Ui.lines(Ui.text(this, 14, 0xFFDDDDDD, Ui.regular(this)), 1);
        metaView.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        topOverlay.addView(metaView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        hs.addView(buttons);
        topOverlay.addView(hs);

        btnChannel = Ui.iconButton(this, 0, "Kanal");
        channelAvatar = new ImageView(this);
        Ui.clipCircle(channelAvatar);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
        alp.rightMargin = Ui.dp(this, 8);
        btnChannel.addView(channelAvatar, 0, alp);
        btnSubscribe = Ui.iconButton(this, R.drawable.ic_subs, "Abonnieren");
        btnLater = Ui.iconButton(this, R.drawable.ic_later, "Später ansehen");
        btnQuality = Ui.iconButton(this, R.drawable.ic_quality, "Qualität");
        btnSpeed = Ui.iconButton(this, R.drawable.ic_speed, "Tempo 1×");
        for (View v : new View[]{btnChannel, btnSubscribe, btnLater, btnQuality, btnSpeed}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 12);
            buttons.addView(v, lp);
            v.setOnFocusChangeListener(chain(v.getOnFocusChangeListener()));
        }
        btnChannel.setOnClickListener(v -> {
            if (vid != null && vid.uploaderUrl != null) ChannelActivity.open(this, vid.uploaderUrl, vid.uploader);
        });
        btnSubscribe.setOnClickListener(v -> toggleSubscribe());
        btnLater.setOnClickListener(v -> toggleLater());
        btnQuality.setOnClickListener(v -> showQualityDialog());
        btnSpeed.setOnClickListener(v -> showSpeedDialog());

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(topOverlay, tlp);
        topOverlay.setVisibility(View.GONE);

        // Hinweis „Sponsor übersprungen“
        skipToast = Ui.text(this, 15, Color.WHITE, Ui.semibold(this));
        skipToast.setBackground(Ui.round(0xE6202020, Ui.dp(this, 22)));
        skipToast.setPadding(Ui.dp(this, 18), Ui.dp(this, 11), Ui.dp(this, 18), Ui.dp(this, 11));
        skipToast.setCompoundDrawablePadding(Ui.dp(this, 8));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        slp.setMargins(Ui.dp(this, 40), 0, 0, Ui.dp(this, 120));
        root.addView(skipToast, slp);
        skipToast.setVisibility(View.GONE);

        // Schnelles Spulen ohne Steuerleiste
        seekLabel = Ui.text(this, 22, Color.WHITE, Ui.bold(this));
        seekLabel.setBackground(Ui.round(0xB3000000, Ui.dp(this, 30)));
        seekLabel.setPadding(Ui.dp(this, 26), Ui.dp(this, 14), Ui.dp(this, 26), Ui.dp(this, 14));
        root.addView(seekLabel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        seekLabel.setVisibility(View.GONE);

        loading = new ProgressBar(this);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        root.addView(loading, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.CENTER));

        // Fehleranzeige
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER_HORIZONTAL);
        errorBox.setBackground(Ui.round(0xF0181818, Ui.dp(this, 20)));
        errorBox.setPadding(Ui.dp(this, 36), Ui.dp(this, 28), Ui.dp(this, 36), Ui.dp(this, 28));
        errorText = Ui.text(this, 17, Color.WHITE, Ui.medium(this));
        errorText.setGravity(Gravity.CENTER);
        errorText.setLineSpacing(Ui.dp(this, 4), 1f);
        errorBox.addView(errorText);
        LinearLayout retry = Ui.iconButton(this, 0, "Erneut versuchen");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = Ui.dp(this, 20);
        errorBox.addView(retry, rlp);
        retry.setOnClickListener(v -> {
            retries = 0;
            if (url != null) load(url);
        });
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(Ui.dp(this, 560), -2, Gravity.CENTER);
        root.addView(errorBox, elp);
        errorBox.setVisibility(View.GONE);

        // Unten: Als Nächstes / ähnliche Videos
        relatedPanel = new LinearLayout(this);
        relatedPanel.setOrientation(LinearLayout.VERTICAL);
        relatedPanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xCC000000, 0x00000000}));
        relatedPanel.setPadding(0, Ui.dp(this, 70), 0, Ui.dp(this, 26));
        upNextTitle = Ui.text(this, 18, Color.WHITE, Ui.bold(this));
        upNextTitle.setPadding(Ui.dp(this, 40), 0, Ui.dp(this, 40), Ui.dp(this, 12));
        relatedPanel.addView(upNextTitle);
        relatedList = new RecyclerView(this);
        relatedList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        relatedList.setAdapter(relatedAdapter);
        relatedList.setClipToPadding(false);
        relatedList.setPadding(Ui.dp(this, 32), Ui.dp(this, 8), Ui.dp(this, 32), Ui.dp(this, 8));
        relatedList.setItemAnimator(null);
        relatedPanel.addView(relatedList, new LinearLayout.LayoutParams(-1, -2));
        root.addView(relatedPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        relatedPanel.setVisibility(View.GONE);

        setContentView(root);
    }

    /** Hält die Steuerung sichtbar, solange ein oberer Knopf den Fokus hat. */
    private View.OnFocusChangeListener chain(View.OnFocusChangeListener inner) {
        return (v, has) -> {
            if (inner != null) inner.onFocusChange(v, has);
            if (has) playerView.showController();
        };
    }

    // ------------------------------------------------------------------ Laden

    private void load(String newUrl) {
        savePosition();
        countdown = -1;
        handler.removeCallbacks(countdownTick);
        url = newUrl;
        info = null;
        vid = null;
        segments = new ArrayList<>();
        related = new ArrayList<>();
        relatedAdapter.notifyDataSetChanged();
        relatedPanel.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        player.stop();
        player.clearMediaItems();
        maxHeight = Prefs.maxHeight();
        String id = Vid.idFromUrl(newUrl);
        long resumeAt = id != null ? App.db().position(id) : 0;
        final int token = ++loadToken;

        Bg.run(() -> StreamInfo.getInfo(Repo.yt(), newUrl), new Bg.Done<StreamInfo>() {
            @Override
            public void ok(StreamInfo result) {
                if (destroyed || token != loadToken) return;
                onInfo(result, resumeAt, token);
            }

            @Override
            public void fail(Exception e) {
                if (destroyed || token != loadToken) return;
                showError(explain(e));
            }
        });
        Bg.run(() -> SponsorBlock.fetch(id), new Bg.Done<List<SponsorBlock.Segment>>() {
            @Override
            public void ok(List<SponsorBlock.Segment> result) {
                if (token == loadToken) segments = result;
            }
        });
    }

    private void onInfo(StreamInfo si, long resumeAt, int token) {
        info = si;
        vid = Vid.from(si);
        App.db().addHistory(vid);
        related = Vid.fromAll(si.getRelatedItems(), false);
        Repo.cacheRelated(vid.id, related);
        relatedAdapter.notifyDataSetChanged();
        heights = Playback.heights(si);
        channelAvatarUrl = Img.best(si.getUploaderAvatars(), 88);

        titleView.setText(si.getName());
        StringBuilder meta = new StringBuilder();
        if (si.getUploaderName() != null) meta.append(si.getUploaderName());
        String subs = Fmt.subs(si.getUploaderSubscriberCount());
        if (subs != null) meta.append(" (").append(subs).append(")");
        String views = Fmt.views(si.getViewCount(), vid.live);
        if (views != null) meta.append("  •  ").append(views);
        if (si.getTextualUploadDate() != null && !vid.live) meta.append("  •  ").append(si.getTextualUploadDate());
        metaView.setText(meta.toString());
        Ui.setButtonLabel(btnChannel, si.getUploaderName() == null ? "Kanal" : si.getUploaderName());
        Glide.with(this).load(channelAvatarUrl).placeholder(new ColorDrawable(Ui.SURFACE)).circleCrop().into(channelAvatar);
        updateSubscribeButton();
        updateLaterButton();
        btnQuality.setVisibility(vid.live ? View.GONE : View.VISIBLE);

        long durMs = si.getDuration() * 1000;
        long start = 0;
        if (!vid.live && resumeAt > 15_000 && (durMs <= 0 || resumeAt < durMs * 0.95)) start = resumeAt;
        if (si.getStartPosition() > 0 && start == 0) start = si.getStartPosition() * 1000;
        final long startAt = start;
        buildSource(si, startAt, token, startAt > 0);
    }

    private void buildSource(StreamInfo si, long positionMs, int token, boolean announceResume) {
        loading.setVisibility(View.VISIBLE);
        final int h = maxHeight;
        Bg.run(() -> playback.build(si, h), new Bg.Done<Playback.Result>() {
            @Override
            public void ok(Playback.Result r) {
                if (destroyed || token != loadToken) return;
                player.setMediaSource(r.source, positionMs > 0 ? positionMs : C.TIME_UNSET);
                player.prepare();
                player.setPlayWhenReady(true);
                Ui.setButtonLabel(btnQuality, r.qualityLabel == null ? "Qualität" : r.qualityLabel);
                if (announceResume) showToast("Fortgesetzt bei " + Fmt.time(positionMs));
            }

            @Override
            public void fail(Exception e) {
                if (destroyed || token != loadToken) return;
                showError(explain(e));
            }
        });
    }

    /** Nach einem Fehler: frische Links holen und an derselben Stelle weitermachen. */
    private void reload(long positionMs) {
        final String u = url;
        final int token = ++loadToken;
        loading.setVisibility(View.VISIBLE);
        Bg.run(() -> StreamInfo.getInfo(Repo.yt(), u), new Bg.Done<StreamInfo>() {
            @Override
            public void ok(StreamInfo result) {
                if (destroyed || token != loadToken) return;
                info = result;
                buildSource(result, positionMs, token, false);
            }

            @Override
            public void fail(Exception e) {
                if (destroyed || token != loadToken) return;
                showError(explain(e));
            }
        });
    }

    private String explain(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof AgeRestrictedContentException)
                return "Dieses Video ist altersbeschränkt und lässt sich ohne Konto nicht abspielen.";
            if (t instanceof GeographicRestrictionException)
                return "Dieses Video ist in deinem Land nicht verfügbar.";
            if (t instanceof org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException)
                return "Der Videodienst blockiert gerade Anfragen von deinem Internetanschluss.\n"
                        + "Das geht meist nach einer Weile von selbst vorbei – bitte später nochmal versuchen.";
            if (t instanceof ReCaptchaException)
                return "Der Server möchte kurz eine Pause. Bitte in ein paar Minuten nochmal versuchen.";
            if (t instanceof ContentNotAvailableException)
                return "Dieses Video ist nicht verfügbar.\n" + (t.getMessage() == null ? "" : t.getMessage());
            if (t instanceof java.net.UnknownHostException)
                return "Keine Internetverbindung.";
            t = t.getCause();
        }
        return "Das Video konnte nicht geladen werden.\n" + (e.getMessage() == null ? "" : e.getMessage());
    }

    private void showError(String text) {
        loading.setVisibility(View.GONE);
        errorText.setText(text);
        errorBox.setVisibility(View.VISIBLE);
        errorBox.getChildAt(1).requestFocus();
    }

    // ------------------------------------------------------------------ SponsorBlock

    private void checkSegments() {
        if (segments.isEmpty() || !player.isPlaying()) return;
        long pos = player.getCurrentPosition();
        for (SponsorBlock.Segment s : segments) {
            if (pos >= s.startMs && pos < s.endMs - 400) {
                long now = System.currentTimeMillis();
                if (now - lastSkipAt < 800) return;
                lastSkipAt = now;
                long dur = player.getDuration();
                long target = s.endMs;
                if (dur != C.TIME_UNSET && target >= dur - 300) target = dur;
                player.seekTo(target);
                showToast("Übersprungen: " + s.label());
                return;
            }
        }
    }

    private final Runnable hideToast = () -> skipToast.animate().alpha(0f).setDuration(250)
            .withEndAction(() -> skipToast.setVisibility(View.GONE)).start();

    private void showToast(String text) {
        skipToast.setText(text);
        skipToast.setAlpha(0f);
        skipToast.setVisibility(View.VISIBLE);
        skipToast.animate().alpha(1f).setDuration(200).start();
        handler.removeCallbacks(hideToast);
        handler.postDelayed(hideToast, 2600);
    }

    // ------------------------------------------------------------------ Knöpfe

    private void updateSubscribeButton() {
        boolean subbed = vid != null && App.db().isSubscribed(vid.uploaderUrl);
        Ui.setButtonLabel(btnSubscribe, subbed ? "Abonniert" : "Abonnieren");
        btnSubscribe.setVisibility(vid != null && vid.uploaderUrl != null ? View.VISIBLE : View.GONE);
    }

    private void toggleSubscribe() {
        if (vid == null || vid.uploaderUrl == null) return;
        Db db = App.db();
        if (db.isSubscribed(vid.uploaderUrl)) {
            db.unsubscribe(vid.uploaderUrl);
            showToast("Abo für " + vid.uploader + " entfernt");
        } else {
            db.subscribe(vid.uploaderUrl, vid.uploader, channelAvatarUrl);
            showToast(vid.uploader + " abonniert – neue Videos erscheinen auf der Startseite");
        }
        Repo.invalidateFeed();
        updateSubscribeButton();
    }

    private void updateLaterButton() {
        boolean later = vid != null && App.db().isLater(vid.id);
        Ui.setButtonLabel(btnLater, later ? "Gespeichert" : "Später ansehen");
    }

    private void toggleLater() {
        if (vid == null) return;
        boolean now = !App.db().isLater(vid.id);
        App.db().setLater(vid, now);
        updateLaterButton();
        showToast(now ? "Zu „Später ansehen“ hinzugefügt" : "Aus „Später ansehen“ entfernt");
    }

    private void showQualityDialog() {
        if (info == null || heights.isEmpty()) return;
        String[] labels = new String[heights.size()];
        int checked = -1;
        int current = Math.min(maxHeight, heights.get(0));
        for (int i = 0; i < heights.size(); i++) {
            int h = heights.get(i);
            labels[i] = h + "p" + (h >= 2160 ? " (4K)" : h >= 1440 ? " (2K)" : h >= 1080 ? " (Full HD)" : h >= 720 ? " (HD)" : "");
            if (checked < 0 && h <= current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Qualität für dieses Video")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    maxHeight = heights.get(which);
                    long pos = player.getCurrentPosition();
                    buildSource(info, pos, loadToken, false);
                })
                .show();
    }

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private void showSpeedDialog() {
        String[] labels = new String[SPEEDS.length];
        int checked = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = speedLabel(SPEEDS[i]);
            if (Math.abs(SPEEDS[i] - player.getPlaybackParameters().speed) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Wiedergabe-Tempo")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    player.setPlaybackSpeed(SPEEDS[which]);
                    Prefs.speed(SPEEDS[which]);
                    Ui.setButtonLabel(btnSpeed, "Tempo " + speedLabel(SPEEDS[which]));
                })
                .show();
    }

    private static String speedLabel(float s) {
        String t = (s == (int) s) ? String.valueOf((int) s) : String.valueOf(s).replace('.', ',');
        return t + "×";
    }

    // ------------------------------------------------------------------ Als Nächstes

    private void onEnded() {
        if (vid != null && info != null && info.getDuration() > 0) {
            App.db().savePosition(vid.id, info.getDuration() * 1000);
        }
        if (!related.isEmpty() && Prefs.autoplay()) {
            showRelated(true);
        } else if (!related.isEmpty()) {
            showRelated(false);
        }
    }

    private void showRelated(boolean withCountdown) {
        if (related.isEmpty()) return;
        playerView.hideController();
        relatedPanel.setVisibility(View.VISIBLE);
        relatedPanel.setAlpha(0f);
        relatedPanel.animate().alpha(1f).setDuration(200).start();
        if (withCountdown) {
            countdown = 8;
            handler.removeCallbacks(countdownTick);
            handler.post(countdownTick);
        } else {
            upNextTitle.setText("Als Nächstes");
        }
        relatedList.scrollToPosition(0);
        relatedList.post(() -> {
            RecyclerView.ViewHolder h = relatedList.findViewHolderForAdapterPosition(0);
            if (h != null) h.itemView.requestFocus();
        });
    }

    private void hideRelated() {
        countdown = -1;
        handler.removeCallbacks(countdownTick);
        relatedPanel.setVisibility(View.GONE);
        playerView.requestFocus();
    }

    private void cancelCountdown() {
        if (countdown >= 0) {
            countdown = -1;
            handler.removeCallbacks(countdownTick);
            upNextTitle.setText("Als Nächstes");
        }
    }

    // ------------------------------------------------------------------ Fernbedienung

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down && e.getRepeatCount() == 0) {
                if (code == KeyEvent.KEYCODE_MEDIA_PLAY) player.play();
                else if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) player.pause();
                else player.setPlayWhenReady(!player.getPlayWhenReady());
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (down) quickSeek(code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 10_000 : -10_000);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (down && !related.isEmpty()) load(related.get(0).url);
            return true;
        }

        if (errorBox.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(e);
        }

        if (relatedPanel.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (down) {
                    hideRelated();
                    if (player.getPlaybackState() == Player.STATE_ENDED) finish();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP && down) {
                hideRelated();
                return true;
            }
            if (down && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) cancelCountdown();
            return super.dispatchKeyEvent(e);
        }

        if (!controllerShown) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) quickSeek(-10_000);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) quickSeek(10_000);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && e.getRepeatCount() == 0) showRelated(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && e.getRepeatCount() == 0) {
                        player.setPlayWhenReady(!player.getPlayWhenReady());
                        playerView.showController();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_MENU:
                    if (down) playerView.showController();
                    return true;
                default:
                    break;
            }
        } else {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (down) playerView.hideController();
                return true;
            }
            if (down) playerView.showController(); // Zeit bis zum Ausblenden zurücksetzen
        }
        return super.dispatchKeyEvent(e);
    }

    private final Runnable hideSeek = () -> seekLabel.setVisibility(View.GONE);

    private void quickSeek(long delta) {
        if (vid != null && vid.live) return;
        long dur = player.getDuration();
        long target = Math.max(0, player.getCurrentPosition() + delta);
        if (dur != C.TIME_UNSET) target = Math.min(dur - 500, target);
        player.seekTo(target);
        String label = (delta > 0 ? "» " : "« ") + Fmt.time(target)
                + (dur != C.TIME_UNSET ? "  /  " + Fmt.time(dur) : "");
        seekLabel.setText(label);
        seekLabel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSeek);
        handler.postDelayed(hideSeek, 900);
    }

    // ------------------------------------------------------------------ Lebenszyklus

    private void savePosition() {
        if (player == null || vid == null || vid.live) return;
        long pos = player.getCurrentPosition();
        if (pos > 0) App.db().savePosition(vid.id, pos);
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        savePosition();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Liste „Als Nächstes“

    private class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, meta, dur;
            final View ring;

            H(LinearLayout card) {
                super(card);
                Context c = card.getContext();
                thumb = (ImageView) ((FrameLayout) card.getChildAt(0)).getChildAt(0);
                dur = (TextView) ((FrameLayout) card.getChildAt(0)).getChildAt(1);
                ring = ((FrameLayout) card.getChildAt(0)).getChildAt(2);
                title = (TextView) card.getChildAt(1);
                meta = (TextView) card.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            int w = Ui.dp(c, 250);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, -2);
            lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8));

            float r = Ui.dp(c, 10);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.round(Ui.SURFACE, r));
            Ui.clipRound(box, r);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            TextView d = Ui.text(c, 11.5f, Color.WHITE, Ui.semibold(c));
            d.setBackground(Ui.round(0xCC000000, Ui.dp(c, 4)));
            d.setPadding(Ui.dp(c, 5), Ui.dp(c, 2), Ui.dp(c, 5), Ui.dp(c, 2));
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, Ui.dp(c, 6), Ui.dp(c, 6));
            box.addView(d, dlp);
            View ring = new View(c);
            ring.setBackground(Ui.ring(Color.WHITE, Ui.dp(c, 3), r));
            ring.setVisibility(View.GONE);
            box.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            card.addView(box, new LinearLayout.LayoutParams(-1, (w - Ui.dp(c, 12)) * 9 / 16));

            TextView t = Ui.lines(Ui.text(c, 14, Color.WHITE, Ui.semibold(c)), 2);
            t.setPadding(Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2), 0);
            card.addView(t);
            TextView m = Ui.lines(Ui.text(c, 12, Ui.TEXT2, Ui.regular(c)), 1);
            m.setPadding(Ui.dp(c, 2), Ui.dp(c, 5), Ui.dp(c, 2), 0);
            card.addView(m);

            H h = new H(card);
            card.setOnFocusChangeListener((v, has) -> {
                h.ring.setVisibility(has ? View.VISIBLE : View.GONE);
                card.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start();
            });
            card.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos >= 0 && pos < related.size()) load(related.get(pos).url);
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Vid v = related.get(position);
            h.title.setText(v.title);
            h.meta.setText(Fmt.meta(v, true));
            String d = v.live ? "LIVE" : Fmt.duration(v.durationSec);
            h.dur.setText(d);
            h.dur.setVisibility(d == null ? View.GONE : View.VISIBLE);
            Glide.with(h.thumb).load(v.thumb).centerCrop().into(h.thumb);
        }

        @Override
        public int getItemCount() {
            return related.size();
        }
    }

    @SuppressWarnings("unused")
    private static Uri parse(String s) {
        return Uri.parse(s);
    }
}
