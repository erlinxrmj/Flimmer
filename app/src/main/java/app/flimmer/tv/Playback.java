package app.flimmer.tv;

import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Baut aus den Video-Infos eine abspielbare Quelle: Bild und Ton werden direkt
 * von den Video-Servern geladen. Dadurch gibt es keinerlei eingeblendete Werbung.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Playback {

    public static class Result {
        public MediaSource source;
        public String qualityLabel;
        public int height;
        public boolean live;
    }

    private static final byte[] POST_BODY = new byte[]{0x78, 0};
    private final AtomicLong requestNumber = new AtomicLong();

    private final DataSource.Factory dashDs;
    private final DataSource.Factory progressiveDs;
    private final DataSource.Factory hlsDs;
    private final DataSource.Factory plainDs;

    public Playback() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("User-Agent", DownloaderImpl.USER_AGENT);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(defaults)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        plainDs = http;
        dashDs = new ResolvingDataSource.Factory(http, spec -> youtubeSpec(spec, true, true));
        progressiveDs = new ResolvingDataSource.Factory(http, spec -> youtubeSpec(spec, false, true));
        hlsDs = new ResolvingDataSource.Factory(http, spec -> youtubeSpec(spec, false, false));
    }

    /**
     * Passt Anfragen an die Video-Server so an, wie es die offiziellen Apps tun
     * (POST-Anfrage, Bereich als Parameter, Anfragenummer). Sonst bremsen die Server.
     */
    private DataSpec youtubeSpec(DataSpec spec, boolean rangeParam, boolean rnParam) {
        Uri uri = spec.uri;
        String path = uri.getPath();
        if (path == null || !path.startsWith("/videoplayback")) return spec;
        String url = uri.toString();
        DataSpec.Builder b = spec.buildUpon();
        if (rnParam && !url.contains("&rn=")) {
            url += "&rn=" + requestNumber.getAndIncrement();
        }
        if (rangeParam && (spec.position != 0 || spec.length != androidx.media3.common.C.LENGTH_UNSET)) {
            url += "&range=" + spec.position + "-"
                    + (spec.length != androidx.media3.common.C.LENGTH_UNSET ? (spec.position + spec.length - 1) : "");
            // Der Bereich steckt jetzt im Link, also ab Anfang der Antwort lesen
            b.setPosition(0).setLength(androidx.media3.common.C.LENGTH_UNSET);
        }
        Map<String, String> headers = new HashMap<>(spec.httpRequestHeaders);
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers.put("Origin", "https://www.youtube.com");
            headers.put("Referer", "https://www.youtube.com/");
            headers.put("Sec-Fetch-Dest", "empty");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Site", "cross-site");
        }
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            headers.put("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null));
        }
        return b.setUri(Uri.parse(url))
                .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .setHttpBody(POST_BODY)
                .setHttpRequestHeaders(headers)
                .build();
    }

    // ------------------------------------------------------------------

    private static boolean usable(VideoStream s) {
        if (s == null || !s.isUrl() || s.getItagItem() == null) return false;
        DeliveryMethod d = s.getDeliveryMethod();
        return d == DeliveryMethod.PROGRESSIVE_HTTP || d == DeliveryMethod.DASH;
    }

    private static String codecOf(VideoStream s) {
        String c = s.getCodec();
        if (c == null) return "";
        return c.toLowerCase();
    }

    private static int codecScore(VideoStream s, String pref) {
        String c = codecOf(s);
        boolean avc = c.startsWith("avc");
        boolean vp9 = c.startsWith("vp9") || c.startsWith("vp09");
        boolean av1 = c.startsWith("av01");
        if ("vp9".equals(pref)) return vp9 ? 3 : avc ? 2 : av1 ? 0 : 1;
        if ("any".equals(pref)) return vp9 ? 3 : avc ? 3 : av1 ? 2 : 1;
        return avc ? 3 : vp9 ? 1 : av1 ? -1 : 0; // kompatibel: H.264 bevorzugt
    }

    /** Alle wählbaren Bildhöhen, höchste zuerst. */
    public static List<Integer> heights(StreamInfo info) {
        TreeSet<Integer> set = new TreeSet<>(Collections.reverseOrder());
        for (VideoStream s : info.getVideoOnlyStreams()) {
            if (usable(s) && s.getHeight() > 0 && codecScore(s, "any") > 1) set.add(s.getHeight());
        }
        if (set.isEmpty()) {
            for (VideoStream s : info.getVideoStreams()) if (s.getHeight() > 0) set.add(s.getHeight());
        }
        return new ArrayList<>(set);
    }

    private static VideoStream pickVideo(List<VideoStream> streams, int maxHeight, String pref) {
        VideoStream best = null;
        for (VideoStream s : streams) {
            if (!usable(s)) continue;
            if (codecScore(s, pref) < 0) continue;
            int h = s.getHeight();
            if (h <= 0 || h > maxHeight) continue;
            if (best == null || better(s, best, pref)) best = s;
        }
        if (best == null) {
            // nichts unter der Grenze: die kleinste nehmen
            for (VideoStream s : streams) {
                if (!usable(s) || codecScore(s, pref) < 0) continue;
                if (best == null || s.getHeight() < best.getHeight()) best = s;
            }
        }
        return best;
    }

    private static boolean better(VideoStream a, VideoStream b, String pref) {
        if (a.getHeight() != b.getHeight()) return a.getHeight() > b.getHeight();
        int ca = codecScore(a, pref), cb = codecScore(b, pref);
        if (ca != cb) return ca > cb;
        if (a.getFps() != b.getFps()) return a.getFps() > b.getFps();
        return a.getBitrate() > b.getBitrate();
    }

    private static AudioStream pickAudio(List<AudioStream> streams, boolean preferAac) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AudioStream s : streams) {
            if (s == null || !s.isUrl() || s.getItagItem() == null) continue;
            DeliveryMethod d = s.getDeliveryMethod();
            if (d != DeliveryMethod.PROGRESSIVE_HTTP && d != DeliveryMethod.DASH) continue;
            int score = 0;
            AudioTrackType t = s.getAudioTrackType();
            if (t == null || t == AudioTrackType.ORIGINAL) score += 100000;
            if (t == AudioTrackType.DESCRIPTIVE) score -= 100000;
            String c = s.getCodec() == null ? "" : s.getCodec().toLowerCase();
            if (preferAac && c.startsWith("mp4a")) score += 10000;
            score += Math.min(9999, Math.max(0, s.getAverageBitrate()));
            if (score > bestScore) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    private MediaSource youtubeDash(Stream s, long durationSec) throws Exception {
        String manifest;
        if (s.getDeliveryMethod() == DeliveryMethod.DASH) {
            manifest = YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(s.getContent(), s.getItagItem(), durationSec);
        } else {
            manifest = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                    s.getContent(), s.getItagItem(), durationSec);
        }
        DashManifest m = new DashManifestParser().parse(Uri.parse(s.getContent()),
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)));
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(dashDs), dashDs)
                .createMediaSource(m, new MediaItem.Builder().setUri(Uri.parse(s.getContent())).build());
    }

    private MediaSource streamSource(Stream s, long durationSec) {
        try {
            return youtubeDash(s, durationSec);
        } catch (Exception e) {
            android.util.Log.w("Flimmer", "DASH-Aufbau fehlgeschlagen, nutze Direktlink", e);
            return new ProgressiveMediaSource.Factory(progressiveDs)
                    .createMediaSource(MediaItem.fromUri(s.getContent()));
        }
    }

    /** Wird im Hintergrund aufgerufen. */
    public Result build(StreamInfo info, int maxHeight) throws Exception {
        Result r = new Result();
        StreamType type = info.getStreamType();
        boolean live = type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;
        r.live = live;

        if (live) {
            String hls = info.getHlsUrl();
            if (hls != null && !hls.isEmpty()) {
                r.source = new HlsMediaSource.Factory(hlsDs).createMediaSource(MediaItem.fromUri(hls));
                r.qualityLabel = "Live";
                return r;
            }
            String mpd = info.getDashMpdUrl();
            if (mpd != null && !mpd.isEmpty()) {
                r.source = new DashMediaSource.Factory(plainDs).createMediaSource(MediaItem.fromUri(mpd));
                r.qualityLabel = "Live";
                return r;
            }
            throw new IllegalStateException("Kein Live-Stream gefunden");
        }

        String pref = Prefs.codec();
        long dur = info.getDuration();
        VideoStream video = pickVideo(info.getVideoOnlyStreams(), maxHeight, pref);
        if (video != null) {
            boolean avc = codecOf(video).startsWith("avc");
            AudioStream audio = pickAudio(info.getAudioStreams(), avc || "avc".equals(pref));
            MediaSource vs = streamSource(video, dur);
            if (audio != null) {
                MediaSource as = streamSource(audio, dur);
                r.source = new MergingMediaSource(true, vs, as);
            } else {
                r.source = vs;
            }
            r.height = video.getHeight();
            r.qualityLabel = video.getHeight() + "p" + (video.getFps() > 30 ? video.getFps() : "");
            return r;
        }

        // Rückfall 1: Video mit eingebautem Ton
        VideoStream muxed = null;
        for (VideoStream s : info.getVideoStreams()) {
            if (!s.isUrl()) continue;
            if (muxed == null || (s.getHeight() <= maxHeight && s.getHeight() > muxed.getHeight())) muxed = s;
        }
        if (muxed != null) {
            r.source = new ProgressiveMediaSource.Factory(progressiveDs)
                    .createMediaSource(MediaItem.fromUri(muxed.getContent()));
            r.height = muxed.getHeight();
            r.qualityLabel = muxed.getHeight() + "p";
            return r;
        }

        // Rückfall 2: HLS
        String hls = info.getHlsUrl();
        if (hls != null && !hls.isEmpty()) {
            r.source = new HlsMediaSource.Factory(hlsDs).createMediaSource(MediaItem.fromUri(hls));
            r.qualityLabel = "Auto";
            return r;
        }
        throw new IllegalStateException("Für dieses Video wurde keine abspielbare Quelle gefunden");
    }
}
