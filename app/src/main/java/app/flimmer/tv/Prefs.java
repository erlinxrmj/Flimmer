package app.flimmer.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Prefs {

    private static SharedPreferences sp;

    /** SponsorBlock-Kategorien: Schlüssel → deutscher Name. */
    public static final Map<String, String> SB_CATEGORIES = new LinkedHashMap<>();

    static {
        SB_CATEGORIES.put("sponsor", "Sponsoren & Werbung im Video");
        SB_CATEGORIES.put("selfpromo", "Eigenwerbung & Merch");
        SB_CATEGORIES.put("interaction", "„Abonnieren & liken“-Hinweise");
        SB_CATEGORIES.put("intro", "Intros & Pausen-Animationen");
        SB_CATEGORIES.put("outro", "Abspann & Endkarten");
        SB_CATEGORIES.put("preview", "Vorschau & Rückblick");
        SB_CATEGORIES.put("filler", "Füllmaterial & Abschweifungen");
        SB_CATEGORIES.put("music_offtopic", "Musikvideos: Nicht-Musik-Teile");
    }

    private Prefs() {}

    public static void init(Context c) {
        sp = c.getSharedPreferences("flimmer", Context.MODE_PRIVATE);
    }

    public static String region() { return sp.getString("region", "CH"); }
    public static void region(String v) { sp.edit().putString("region", v).apply(); }

    /** Maximale Bildhöhe, z. B. 1080. */
    public static int maxHeight() { return sp.getInt("max_height", 1080); }
    public static void maxHeight(int v) { sp.edit().putInt("max_height", v).apply(); }

    /** "avc" = kompatibel (H.264), "vp9" = bevorzugt VP9, "any" = egal. */
    public static String codec() { return sp.getString("codec", "avc"); }
    public static void codec(String v) { sp.edit().putString("codec", v).apply(); }

    public static boolean sponsorBlock() { return sp.getBoolean("sb_on", true); }
    public static void sponsorBlock(boolean v) { sp.edit().putBoolean("sb_on", v).apply(); }

    public static boolean sbCategory(String cat) {
        // Füllmaterial ist standardmässig aus, weil es oft echte Inhalte trifft
        return sp.getBoolean("sb_" + cat, !"filler".equals(cat));
    }
    public static void sbCategory(String cat, boolean v) { sp.edit().putBoolean("sb_" + cat, v).apply(); }

    public static boolean hideShorts() { return sp.getBoolean("hide_shorts", false); }
    public static void hideShorts(boolean v) { sp.edit().putBoolean("hide_shorts", v).apply(); }

    public static boolean autoplay() { return sp.getBoolean("autoplay", true); }
    public static void autoplay(boolean v) { sp.edit().putBoolean("autoplay", v).apply(); }

    public static boolean historyPaused() { return sp.getBoolean("history_paused", false); }
    public static void historyPaused(boolean v) { sp.edit().putBoolean("history_paused", v).apply(); }

    public static float speed() { return sp.getFloat("speed", 1f); }
    public static void speed(float v) { sp.edit().putFloat("speed", v).apply(); }
}
