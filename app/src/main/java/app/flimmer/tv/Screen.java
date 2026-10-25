package app.flimmer.tv;

import android.app.Activity;
import android.widget.FrameLayout;

/** Ein Bereich rechts neben der Navigationsleiste. */
public abstract class Screen extends FrameLayout {

    protected final Activity activity;
    private boolean shownOnce;

    public Screen(Activity a) {
        super(a);
        activity = a;
        setBackgroundColor(Ui.BG);
    }

    /** Wird aufgerufen, wenn der Bereich sichtbar wird. */
    public final void show() {
        boolean first = !shownOnce;
        shownOnce = true;
        onShow(first);
    }

    protected abstract void onShow(boolean firstTime);

    /** Fokus in den Inhalt legen (z. B. nach Druck nach rechts in der Leiste). */
    public abstract boolean focusContent();

    /** Die App kommt zurück in den Vordergrund (z. B. nach einem Video). */
    public void onResumeScreen() {}

    protected boolean alive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
