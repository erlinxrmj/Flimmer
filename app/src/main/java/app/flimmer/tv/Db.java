package app.flimmer.tv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Alles, was die App sich merkt, liegt nur hier auf dem Fernseher. Kein Konto, keine Cloud. */
public class Db extends SQLiteOpenHelper {

    public static class Channel {
        public String url;
        public String name;
        public String avatar;
        public long addedAt;
    }

    public Db(Context c) {
        super(c, "flimmer.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, url TEXT, title TEXT, uploader TEXT,"
                + " uploader_url TEXT, thumb TEXT, duration INTEGER, views INTEGER, date_text TEXT,"
                + " watched_at INTEGER, position INTEGER DEFAULT 0, watch_count INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE later (id TEXT PRIMARY KEY, url TEXT, title TEXT, uploader TEXT,"
                + " uploader_url TEXT, thumb TEXT, duration INTEGER, views INTEGER, date_text TEXT,"
                + " added_at INTEGER)");
        db.execSQL("CREATE TABLE searches (query TEXT PRIMARY KEY, at INTEGER)");
        db.execSQL("CREATE TABLE subs (url TEXT PRIMARY KEY, name TEXT, avatar TEXT, added_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}

    // ---------------- Verlauf ----------------

    public void addHistory(Vid v) {
        if (Prefs.historyPaused() || v == null || v.id == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = videoValues(v);
        cv.put("watched_at", System.currentTimeMillis());
        int updated = db.update("history", cv, "id=?", new String[]{v.id});
        if (updated == 0) {
            cv.put("position", 0);
            db.insert("history", null, cv);
        } else {
            db.execSQL("UPDATE history SET watch_count = watch_count + 1 WHERE id=?", new Object[]{v.id});
        }
    }

    public void savePosition(String id, long positionMs) {
        if (Prefs.historyPaused() || id == null) return;
        ContentValues cv = new ContentValues();
        cv.put("position", positionMs);
        getWritableDatabase().update("history", cv, "id=?", new String[]{id});
    }

    public long position(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT position FROM history WHERE id=?",
                new String[]{id})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public List<Vid> history(int limit) {
        List<Vid> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,url,title,uploader,uploader_url,thumb,duration,views,date_text,position"
                        + " FROM history ORDER BY watched_at DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                Vid v = readVideo(c);
                v.positionMs = c.getLong(9);
                out.add(v);
            }
        }
        return out;
    }

    public Set<String> watchedIds() {
        Set<String> out = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM history", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** Am häufigsten angeschaute Kanäle (für Empfehlungen). */
    public List<String> topUploaders(int limit) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT uploader_url, SUM(watch_count) AS n FROM history WHERE uploader_url IS NOT NULL"
                        + " GROUP BY uploader_url ORDER BY n DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public void removeHistory(String id) {
        getWritableDatabase().delete("history", "id=?", new String[]{id});
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    // ---------------- Später ansehen ----------------

    public boolean isLater(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM later WHERE id=?", new String[]{id})) {
            return c.moveToFirst();
        }
    }

    public void setLater(Vid v, boolean on) {
        if (v == null || v.id == null) return;
        if (on) {
            ContentValues cv = videoValues(v);
            cv.put("added_at", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("later", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            getWritableDatabase().delete("later", "id=?", new String[]{v.id});
        }
    }

    public List<Vid> later() {
        List<Vid> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT l.id,l.url,l.title,l.uploader,l.uploader_url,l.thumb,l.duration,l.views,l.date_text,"
                        + " COALESCE(h.position,0) FROM later l LEFT JOIN history h ON h.id=l.id"
                        + " ORDER BY l.added_at DESC", null)) {
            while (c.moveToNext()) {
                Vid v = readVideo(c);
                v.positionMs = c.getLong(9);
                out.add(v);
            }
        }
        return out;
    }

    // ---------------- Suchverlauf ----------------

    public void addSearch(String q) {
        if (Prefs.historyPaused() || q == null || q.trim().isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("query", q.trim());
        cv.put("at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("searches", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<String> searches(String prefix, int limit) {
        List<String> out = new ArrayList<>();
        String p = prefix == null ? "" : prefix.trim();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT query FROM searches WHERE query LIKE ? ORDER BY at DESC LIMIT " + limit,
                new String[]{p + "%"})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public void removeSearch(String q) {
        getWritableDatabase().delete("searches", "query=?", new String[]{q});
    }

    public void clearSearches() {
        getWritableDatabase().delete("searches", null, null);
    }

    // ---------------- Abos (gefolgte Kanäle, nur lokal) ----------------

    public boolean isSubscribed(String url) {
        if (url == null) return false;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM subs WHERE url=?", new String[]{norm(url)})) {
            return c.moveToFirst();
        }
    }

    public void subscribe(String url, String name, String avatar) {
        ContentValues cv = new ContentValues();
        cv.put("url", norm(url));
        cv.put("name", name);
        cv.put("avatar", avatar);
        cv.put("added_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("subs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void unsubscribe(String url) {
        getWritableDatabase().delete("subs", "url=?", new String[]{norm(url)});
    }

    public void updateChannel(String url, String name, String avatar) {
        ContentValues cv = new ContentValues();
        if (name != null) cv.put("name", name);
        if (avatar != null) cv.put("avatar", avatar);
        if (cv.size() == 0) return;
        getWritableDatabase().update("subs", cv, "url=?", new String[]{norm(url)});
    }

    public List<Channel> subs() {
        List<Channel> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT url,name,avatar,added_at FROM subs ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                Channel ch = new Channel();
                ch.url = c.getString(0);
                ch.name = c.getString(1);
                ch.avatar = c.getString(2);
                ch.addedAt = c.getLong(3);
                out.add(ch);
            }
        }
        return out;
    }

    public Set<String> subUrls() {
        Set<String> out = new HashSet<>();
        for (Channel c : subs()) out.add(c.url);
        return out;
    }

    /** Kanal-Links vereinheitlichen, damit dieselben Kanäle erkannt werden. */
    public static String norm(String url) {
        if (url == null) return null;
        String u = url.trim();
        u = u.replace("http://", "https://").replace("://m.youtube.com", "://www.youtube.com")
                .replace("://youtube.com", "://www.youtube.com");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    // ---------------- Helfer ----------------

    private static ContentValues videoValues(Vid v) {
        ContentValues cv = new ContentValues();
        cv.put("id", v.id);
        cv.put("url", v.url);
        cv.put("title", v.title);
        cv.put("uploader", v.uploader);
        cv.put("uploader_url", v.uploaderUrl == null ? null : norm(v.uploaderUrl));
        cv.put("thumb", v.thumb);
        cv.put("duration", v.durationSec);
        cv.put("views", v.views);
        cv.put("date_text", v.dateText);
        return cv;
    }

    private static Vid readVideo(Cursor c) {
        Vid v = new Vid();
        v.id = c.getString(0);
        v.url = c.getString(1);
        v.title = c.getString(2);
        v.uploader = c.getString(3);
        v.uploaderUrl = c.getString(4);
        v.thumb = c.getString(5);
        v.durationSec = c.getLong(6);
        v.views = c.getLong(7);
        v.dateText = c.getString(8);
        return v;
    }
}
