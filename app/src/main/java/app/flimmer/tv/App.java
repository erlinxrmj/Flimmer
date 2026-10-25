package app.flimmer.tv;

import android.app.Application;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

public class App extends Application {

    private static App instance;
    private Db db;

    public static App get() {
        return instance;
    }

    public static Db db() {
        return instance.db;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new Db(this);
        Prefs.init(this);
        applyRegion();
    }

    /** Sprache Deutsch, Inhalte für die gewählte Region (Standard: Schweiz). */
    public static void applyRegion() {
        String country = Prefs.region();
        NewPipe.init(DownloaderImpl.get(), new Localization("de", country), new ContentCountry(country));
    }
}
