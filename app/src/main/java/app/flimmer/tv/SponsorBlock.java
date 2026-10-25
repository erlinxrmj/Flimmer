package app.flimmer.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Holt von der Community markierte Abschnitte (Sponsoren, Intros, Abo-Hinweise …),
 * damit der Player sie automatisch überspringt.
 * Aus Datenschutzgründen wird nur ein kurzer Hash-Anfang der Video-ID gesendet.
 */
public final class SponsorBlock {

    public static class Segment {
        public long startMs, endMs;
        public String category;

        public String label() {
            String l = Prefs.SB_CATEGORIES.get(category);
            if ("sponsor".equals(category)) return "Sponsor";
            if ("selfpromo".equals(category)) return "Eigenwerbung";
            if ("interaction".equals(category)) return "Abo-Hinweis";
            if ("intro".equals(category)) return "Intro";
            if ("outro".equals(category)) return "Abspann";
            if ("preview".equals(category)) return "Vorschau";
            if ("filler".equals(category)) return "Füllmaterial";
            if ("music_offtopic".equals(category)) return "Nicht-Musik-Teil";
            return l == null ? category : l;
        }
    }

    private SponsorBlock() {}

    public static List<Segment> fetch(String videoId) {
        List<Segment> out = new ArrayList<>();
        if (videoId == null || !Prefs.sponsorBlock()) return out;
        try {
            JSONArray cats = new JSONArray();
            for (Map.Entry<String, String> e : Prefs.SB_CATEGORIES.entrySet()) {
                if (Prefs.sbCategory(e.getKey())) cats.put(e.getKey());
            }
            if (cats.length() == 0) return out;
            String prefix = sha256(videoId).substring(0, 4);
            String url = "https://sponsor.ajay.app/api/skipSegments/" + prefix
                    + "?categories=" + URLEncoder.encode(cats.toString(), "UTF-8")
                    + "&actionTypes=" + URLEncoder.encode("[\"skip\",\"mute\"]", "UTF-8");
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "Flimmer/1.0").build();
            try (Response r = DownloaderImpl.get().client().newCall(req).execute()) {
                ResponseBody body = r.body();
                if (!r.isSuccessful() || body == null) return out;
                JSONArray videos = new JSONArray(body.string());
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    if (!videoId.equals(v.optString("videoID"))) continue;
                    JSONArray segs = v.optJSONArray("segments");
                    if (segs == null) continue;
                    for (int k = 0; k < segs.length(); k++) {
                        JSONObject s = segs.getJSONObject(k);
                        JSONArray range = s.optJSONArray("segment");
                        if (range == null || range.length() < 2) continue;
                        Segment seg = new Segment();
                        seg.startMs = (long) (range.getDouble(0) * 1000);
                        seg.endMs = (long) (range.getDouble(1) * 1000);
                        seg.category = s.optString("category");
                        if (seg.endMs - seg.startMs < 500) continue;
                        out.add(seg);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("Flimmer", "SponsorBlock nicht erreichbar", e);
        }
        return out;
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
