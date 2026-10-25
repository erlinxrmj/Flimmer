package app.flimmer.tv;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Kleine Hilfe für Arbeit im Hintergrund mit Rückgabe im Haupt-Thread. */
public final class Bg {

    public interface Done<T> {
        void ok(T result);

        default void fail(Exception e) {}
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {}

    public static ExecutorService pool() {
        return POOL;
    }

    public static Handler main() {
        return MAIN;
    }

    public static <T> Future<?> run(Callable<T> work, Done<T> done) {
        return POOL.submit(() -> {
            try {
                T r = work.call();
                MAIN.post(() -> done.ok(r));
            } catch (Exception e) {
                android.util.Log.w("Flimmer", "Hintergrundaufgabe fehlgeschlagen", e);
                MAIN.post(() -> done.fail(e));
            }
        });
    }

    public static void ui(Runnable r) {
        MAIN.post(r);
    }
}
