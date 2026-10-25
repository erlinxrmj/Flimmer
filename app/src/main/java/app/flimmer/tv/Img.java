package app.flimmer.tv;

import org.schabi.newpipe.extractor.Image;

import java.util.List;

public final class Img {
    private Img() {}

    /** Wählt das Bild, dessen Höhe am besten zur gewünschten passt (lieber etwas grösser). */
    public static String best(List<Image> images, int wantedHeight) {
        if (images == null || images.isEmpty()) return null;
        Image best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Image img : images) {
            if (img == null || img.getUrl() == null) continue;
            int h = img.getHeight();
            int score;
            if (h <= 0) {
                score = 100000; // unbekannte Grösse: nur nehmen, wenn nichts anderes da ist
            } else if (h >= wantedHeight) {
                score = h - wantedHeight;
            } else {
                score = (wantedHeight - h) * 3;
            }
            if (best == null || score < bestScore) {
                best = img;
                bestScore = score;
            }
        }
        if (best == null) return null;
        String u = best.getUrl();
        if (u.startsWith("//")) u = "https:" + u;
        return u;
    }
}
