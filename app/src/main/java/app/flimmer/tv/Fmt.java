package app.flimmer.tv;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    public static String duration(long sec) {
        if (sec <= 0) return null;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    public static String time(long ms) {
        return duration(Math.max(1, ms / 1000));
    }

    /** "1,2 Mio.", "34.567", "2,1 Mrd." – wie in der deutschen Videoplattform-Ansicht. */
    public static String count(long n) {
        if (n < 0) return null;
        if (n < 10_000) return String.format(Locale.GERMANY, "%,d", n);
        if (n < 1_000_000) return String.format(Locale.GERMANY, "%s Tsd.", oneDecimal(n / 1000.0));
        if (n < 1_000_000_000L) return String.format(Locale.GERMANY, "%s Mio.", oneDecimal(n / 1_000_000.0));
        return String.format(Locale.GERMANY, "%s Mrd.", oneDecimal(n / 1_000_000_000.0));
    }

    private static String oneDecimal(double d) {
        if (d >= 100) return String.format(Locale.GERMANY, "%d", (long) d);
        String s = String.format(Locale.GERMANY, "%.1f", Math.floor(d * 10) / 10.0);
        return s.endsWith(",0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String views(long n, boolean live) {
        String c = count(n);
        if (c == null) return null;
        return c + (live ? " Zuschauer" : " Aufrufe");
    }

    public static String subs(long n) {
        String c = count(n);
        return c == null ? null : c + " Abonnenten";
    }

    /** Zeile unter dem Titel: "Kanal • 1,2 Mio. Aufrufe • vor 3 Tagen" */
    public static String meta(Vid v, boolean withUploader) {
        StringBuilder sb = new StringBuilder();
        if (withUploader && v.uploader != null) sb.append(v.uploader);
        String views = views(v.views, v.live);
        if (views != null) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(views);
        }
        if (v.dateText != null && !v.live) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(v.dateText);
        } else if (v.dateText == null && v.publishedMs > 0 && !v.live) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(ago(v.publishedMs));
        }
        return sb.toString();
    }

    public static String ago(long ms) {
        long diff = (System.currentTimeMillis() - ms) / 1000;
        if (diff < 60) return "gerade eben";
        long min = diff / 60;
        if (min < 60) return "vor " + min + (min == 1 ? " Minute" : " Minuten");
        long h = min / 60;
        if (h < 24) return "vor " + h + (h == 1 ? " Stunde" : " Stunden");
        long d = h / 24;
        if (d < 7) return "vor " + d + (d == 1 ? " Tag" : " Tagen");
        long w = d / 7;
        if (d < 30) return "vor " + w + (w == 1 ? " Woche" : " Wochen");
        long mo = d / 30;
        if (d < 365) return "vor " + mo + (mo == 1 ? " Monat" : " Monaten");
        long y = d / 365;
        return "vor " + y + (y == 1 ? " Jahr" : " Jahren");
    }
}
