package app.flimmer.tv;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/** Menü bei langem Drücken auf eine Kachel. */
public final class Menus {
    private Menus() {}

    public static void video(Activity a, Vid v, Runnable removeFromList) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add("Abspielen");
        actions.add(() -> PlayerActivity.open(a, v.url));

        boolean later = App.db().isLater(v.id);
        labels.add(later ? "Aus „Später ansehen“ entfernen" : "Später ansehen");
        actions.add(() -> App.db().setLater(v, !later));

        if (v.uploaderUrl != null) {
            labels.add("Zum Kanal" + (v.uploader != null ? ": " + v.uploader : ""));
            actions.add(() -> ChannelActivity.open(a, v.uploaderUrl, v.uploader));

            boolean subbed = App.db().isSubscribed(v.uploaderUrl);
            labels.add(subbed ? "Kanal deabonnieren" : "Kanal abonnieren");
            actions.add(() -> {
                if (subbed) App.db().unsubscribe(v.uploaderUrl);
                else App.db().subscribe(v.uploaderUrl, v.uploader, null);
                Repo.invalidateFeed();
            });
        }

        if (removeFromList != null) {
            labels.add("Aus dieser Liste entfernen");
            actions.add(removeFromList);
        }

        new AlertDialog.Builder(a)
                .setTitle(v.title)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }
}
