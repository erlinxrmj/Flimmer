package app.flimmer.tv;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ein Video (oder ein Kanal-Eintrag in Suchergebnissen). */
public class Vid {

    private static final Pattern ID = Pattern.compile(
            "(?:v=|/shorts/|/live/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})");

    public String url;
    public String id;
    public String title;
    public String uploader;
    public String uploaderUrl;
    public String thumb;
    public long durationSec = -1;
    public long views = -1;
    public String dateText;
    /** Zeitpunkt der Veröffentlichung in ms (0 = unbekannt), für die Sortierung im Abo-Feed. */
    public long publishedMs;
    public boolean live;
    public boolean isShort;

    /** Gesetzt, wenn dieser Eintrag ein Kanal ist (nur in der Suche). */
    public boolean isChannel;
    public long subscribers = -1;

    /** Wiedergabefortschritt in ms (aus dem Verlauf) */
    public long positionMs;

    public static String idFromUrl(String url) {
        if (url == null) return null;
        Matcher m = ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static Vid from(InfoItem item) {
        if (item instanceof StreamInfoItem) {
            StreamInfoItem s = (StreamInfoItem) item;
            Vid v = new Vid();
            v.url = s.getUrl();
            v.id = idFromUrl(v.url);
            v.title = s.getName();
            v.uploader = s.getUploaderName();
            v.uploaderUrl = s.getUploaderUrl();
            v.thumb = Img.best(s.getThumbnails(), 360);
            v.durationSec = s.getDuration();
            v.views = s.getViewCount();
            v.dateText = s.getTextualUploadDate();
            v.publishedMs = millis(s.getUploadDate());
            StreamType t = s.getStreamType();
            v.live = t == StreamType.LIVE_STREAM || t == StreamType.AUDIO_LIVE_STREAM;
            v.isShort = s.isShortFormContent() || (v.url != null && v.url.contains("/shorts/"));
            return v;
        }
        if (item instanceof ChannelInfoItem) {
            ChannelInfoItem c = (ChannelInfoItem) item;
            Vid v = new Vid();
            v.isChannel = true;
            v.url = c.getUrl();
            v.uploaderUrl = c.getUrl();
            v.title = c.getName();
            v.uploader = c.getName();
            v.thumb = Img.best(c.getThumbnails(), 176);
            v.subscribers = c.getSubscriberCount();
            return v;
        }
        return null;
    }

    public static Vid from(StreamInfo s) {
        Vid v = new Vid();
        v.url = s.getUrl();
        v.id = idFromUrl(v.url);
        if (v.id == null) v.id = s.getId();
        v.title = s.getName();
        v.uploader = s.getUploaderName();
        v.uploaderUrl = s.getUploaderUrl();
        v.thumb = Img.best(s.getThumbnails(), 360);
        v.durationSec = s.getDuration();
        v.views = s.getViewCount();
        v.dateText = s.getTextualUploadDate();
        v.publishedMs = millis(s.getUploadDate());
        StreamType t = s.getStreamType();
        v.live = t == StreamType.LIVE_STREAM || t == StreamType.AUDIO_LIVE_STREAM;
        return v;
    }

    /** Wandelt eine Liste von Ergebnissen um und filtert, was nicht angezeigt werden soll. */
    public static List<Vid> fromAll(List<? extends InfoItem> items, boolean allowChannels) {
        List<Vid> out = new ArrayList<>();
        if (items == null) return out;
        boolean hideShorts = Prefs.hideShorts();
        for (InfoItem it : items) {
            Vid v;
            try {
                v = from(it);
            } catch (Exception e) {
                continue;
            }
            if (v == null) continue;
            if (v.isChannel && !allowChannels) continue;
            if (!v.isChannel && v.id == null) continue;
            if (hideShorts && v.isShort) continue;
            out.add(v);
        }
        return out;
    }

    static long millis(DateWrapper d) {
        if (d == null) return 0;
        try {
            return d.getInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    static String firstUrl(List<Image> images) {
        return Img.best(images, 360);
    }
}
