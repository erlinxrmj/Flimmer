package app.flimmer.tv;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Holt Inhalte über die NewPipe-Bibliothek. Alles läuft ohne Konto. */
public final class Repo {

    /** Eigener Pool für parallele Teilaufgaben, damit sich nichts gegenseitig blockiert. */
    private static final java.util.concurrent.ExecutorService INNER =
            java.util.concurrent.Executors.newFixedThreadPool(8);

    private Repo() {}

    public static StreamingService yt() {
        return ServiceList.YouTube;
    }

    // ---------------- Listen mit Weiterblättern ----------------

    /** Ergebnisliste, die sich beim Scrollen nachladen lässt. */
    public static class Paged {
        public List<Vid> items = new ArrayList<>();
        Page next;
        SearchQueryHandler query;
        ListLinkHandler tab;
        String kioskId;

        public boolean hasMore() {
            return Page.isValid(next);
        }

        /** Nächste Seite laden (im Hintergrund aufrufen). */
        public List<Vid> more(boolean allowChannels) throws Exception {
            if (!hasMore()) return Collections.emptyList();
            ListExtractor.InfoItemsPage<? extends InfoItem> page;
            if (query != null) {
                page = SearchInfo.getMoreItems(yt(), query, next);
            } else if (tab != null) {
                page = ChannelTabInfo.getMoreItems(yt(), tab, next);
            } else if (kioskId != null) {
                KioskExtractor ex = yt().getKioskList().getExtractorById(kioskId, next);
                page = ex.getPage(next);
            } else {
                return Collections.emptyList();
            }
            next = page.getNextPage();
            return Vid.fromAll(page.getItems(), allowChannels);
        }
    }

    // ---------------- Suche ----------------

    public static Paged search(String q) throws Exception {
        SearchQueryHandler h = yt().getSearchQHFactory().fromQuery(q,
                Collections.singletonList(YoutubeSearchQueryHandlerFactory.ALL), "");
        SearchInfo info = SearchInfo.getInfo(yt(), h);
        Paged p = new Paged();
        p.query = h;
        p.next = info.getNextPage();
        p.items = Vid.fromAll(info.getRelatedItems(), true);
        return p;
    }

    public static List<String> suggestions(String q) throws Exception {
        if (q == null || q.trim().isEmpty()) return Collections.emptyList();
        return yt().getSuggestionExtractor().suggestionList(q.trim());
    }

    // ---------------- Angesagt (Kioske) ----------------

    public static final String KIOSK_LIVE = "live";
    public static final String KIOSK_MUSIC = "trending_music";
    public static final String KIOSK_GAMING = "trending_gaming";
    public static final String KIOSK_MOVIES = "trending_movies_and_shows";
    public static final String KIOSK_PODCASTS = "trending_podcasts_episodes";

    public static Paged kiosk(String id) throws Exception {
        KioskExtractor ex = yt().getKioskList().getExtractorById(id, null);
        ex.fetchPage();
        KioskInfo info = KioskInfo.getInfo(ex);
        Paged p = new Paged();
        p.kioskId = id;
        p.next = info.getNextPage();
        p.items = Vid.fromAll(info.getRelatedItems(), false);
        return p;
    }

    // ---------------- Kanäle ----------------

    public static class ChannelData {
        public String url;
        public String name;
        public String avatar;
        public String banner;
        public long subscribers = -1;
        public Paged videos = new Paged();
    }

    public static ChannelData channel(String url) throws Exception {
        ChannelInfo info = ChannelInfo.getInfo(yt(), url);
        ChannelData d = new ChannelData();
        d.url = info.getUrl();
        d.name = info.getName();
        d.avatar = Img.best(info.getAvatars(), 176);
        d.banner = Img.best(info.getBanners(), 360);
        d.subscribers = info.getSubscriberCount();
        ListLinkHandler videosTab = null;
        for (ListLinkHandler tab : info.getTabs()) {
            if (tab.getContentFilters().contains(ChannelTabs.VIDEOS)) {
                videosTab = tab;
                break;
            }
        }
        if (videosTab == null && !info.getTabs().isEmpty()) videosTab = info.getTabs().get(0);
        if (videosTab != null) {
            ChannelTabInfo tabInfo = ChannelTabInfo.getInfo(yt(), videosTab);
            d.videos.tab = videosTab;
            d.videos.next = tabInfo.getNextPage();
            d.videos.items = Vid.fromAll(tabInfo.getRelatedItems(), false);
        }
        // Abo-Eintrag aktuell halten (Name/Bild können sich ändern)
        if (App.db().isSubscribed(url)) App.db().updateChannel(url, d.name, d.avatar);
        return d;
    }

    /** Neueste Videos eines Kanals – möglichst mit nur einer Anfrage. */
    public static List<Vid> latestOf(String channelUrl) throws Exception {
        String u = Db.norm(channelUrl);
        int i = u.indexOf("/channel/");
        if (i >= 0) {
            String id = u.substring(i + 9);
            int slash = id.indexOf('/');
            if (slash > 0) id = id.substring(0, slash);
            try {
                ListLinkHandler lh = yt().getChannelTabLHFactory()
                        .fromQuery(id, Collections.singletonList(ChannelTabs.VIDEOS), "");
                ChannelTabInfo tab = ChannelTabInfo.getInfo(yt(), lh);
                List<Vid> out = Vid.fromAll(tab.getRelatedItems(), false);
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                // weiter mit dem langsameren Weg
            }
        }
        try {
            return channel(channelUrl).videos.items;
        } catch (Exception e) {
            FeedInfo feed = FeedInfo.getInfo(yt(), channelUrl);
            return Vid.fromAll(feed.getRelatedItems(), false);
        }
    }

    // ---------------- Abo-Feed ----------------

    private static List<Vid> feedCache;
    private static long feedCacheAt;

    public static synchronized void invalidateFeed() {
        feedCache = null;
        homeCache = null;
    }

    /** Neueste Videos aller gefolgten Kanäle, neueste zuerst. */
    public static List<Vid> subsFeed(boolean force) throws Exception {
        synchronized (Repo.class) {
            if (!force && feedCache != null && System.currentTimeMillis() - feedCacheAt < 15 * 60_000) {
                return new ArrayList<>(feedCache);
            }
        }
        List<Db.Channel> subs = App.db().subs();
        List<Future<List<Vid>>> jobs = new ArrayList<>();
        for (Db.Channel c : subs) {
            jobs.add(INNER.submit(() -> {
                try {
                    List<Vid> l = latestOf(c.url);
                    if (l.size() > 12) return new ArrayList<Vid>(l.subList(0, 12));
                    return l;
                } catch (Exception e) {
                    return Collections.<Vid>emptyList();
                }
            }));
        }
        List<Vid> all = new ArrayList<>();
        // Reihenfolge innerhalb eines Kanals beibehalten, falls Daten fehlen
        Map<Vid, Integer> rank = new HashMap<>();
        for (Future<List<Vid>> f : jobs) {
            List<Vid> l;
            try {
                l = f.get(40, TimeUnit.SECONDS);
            } catch (Exception e) {
                continue;
            }
            for (int i = 0; i < l.size(); i++) {
                rank.put(l.get(i), i);
                all.add(l.get(i));
            }
        }
        Collections.sort(all, (a, b) -> {
            if (a.publishedMs > 0 && b.publishedMs > 0) return Long.compare(b.publishedMs, a.publishedMs);
            Integer ra = rank.get(a), rb = rank.get(b);
            return Integer.compare(ra == null ? 99 : ra, rb == null ? 99 : rb);
        });
        List<Vid> dedup = dedupe(all);
        synchronized (Repo.class) {
            feedCache = dedup;
            feedCacheAt = System.currentTimeMillis();
        }
        return new ArrayList<>(dedup);
    }

    // ---------------- Startseite „Für dich“ ----------------

    public static class Home {
        /** Abwechselnd VideoGrid.Section und Vid. */
        public List<Object> items = new ArrayList<>();
        public Paged more;
    }

    private static Home homeCache;
    private static long homeCacheAt;

    /**
     * Baut die Startseite ohne Konto:
     * 1. Neu von deinen Kanälen (lokale Abos)
     * 2. Empfohlen für dich: ähnliche Videos zu deinem Verlauf und zu deinen Kanälen,
     *    bevorzugt von Kanälen, denen du noch nicht folgst (= ähnliche Creator)
     * 3. Angesagt als Auffüllung, die sich endlos weiterladen lässt
     */
    public static Home home(boolean force) throws Exception {
        synchronized (Repo.class) {
            if (!force && homeCache != null && System.currentTimeMillis() - homeCacheAt < 20 * 60_000) {
                return homeCache;
            }
        }
        Db db = App.db();
        Set<String> watched = db.watchedIds();
        Set<String> subUrls = db.subUrls();
        Set<String> shown = new HashSet<>();
        Home home = new Home();

        // Angesagt schon parallel anfangen
        Future<Paged> trendingJob = INNER.submit(() -> {
            try {
                return kiosk(KIOSK_GAMING);
            } catch (Exception e) {
                return null;
            }
        });
        Future<Paged> musicJob = INNER.submit(() -> {
            try {
                return kiosk(KIOSK_MUSIC);
            } catch (Exception e) {
                return null;
            }
        });

        // 1. Neu von deinen Kanälen
        List<Vid> feed = Collections.emptyList();
        if (!subUrls.isEmpty()) {
            try {
                feed = subsFeed(force);
            } catch (Exception ignored) {
            }
            List<Vid> fresh = new ArrayList<>();
            for (Vid v : feed) {
                if (fresh.size() >= 8) break;
                if (v.id != null && !watched.contains(v.id)) fresh.add(v);
            }
            if (fresh.size() < 4) {
                for (Vid v : feed) {
                    if (fresh.size() >= 8) break;
                    if (!fresh.contains(v)) fresh.add(v);
                }
            }
            if (!fresh.isEmpty()) {
                home.items.add(new VideoGrid.Section("Neu von deinen Kanälen", null));
                for (Vid v : fresh) {
                    shown.add(v.id);
                    home.items.add(v);
                }
            }
        }

        // 2. Empfohlen für dich – Ausgangsvideos sammeln
        List<String> seeds = new ArrayList<>();
        for (Vid v : db.history(6)) {
            if (seeds.size() >= 3) break;
            seeds.add(v.url);
        }
        Random rnd = new Random();
        List<Vid> feedPool = new ArrayList<>(feed.size() > 20 ? feed.subList(0, 20) : feed);
        Collections.shuffle(feedPool, rnd);
        for (Vid v : feedPool) {
            if (seeds.size() >= 6) break;
            if (!seeds.contains(v.url)) seeds.add(v.url);
        }

        List<Future<List<Vid>>> relatedJobs = new ArrayList<>();
        for (String url : seeds) {
            relatedJobs.add(INNER.submit(() -> {
                try {
                    return related(url);
                } catch (Exception e) {
                    return Collections.<Vid>emptyList();
                }
            }));
        }
        List<List<Vid>> relatedLists = new ArrayList<>();
        for (Future<List<Vid>> f : relatedJobs) {
            try {
                relatedLists.add(f.get(45, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }

        // Abwechselnd aus allen Listen nehmen; Kanäle, denen du nicht folgst, zuerst
        // (das sind die „ähnlichen Creator“), dann Videos deiner eigenen Kanäle.
        List<Vid> fromNew = new ArrayList<>();
        List<Vid> fromSubs = new ArrayList<>();
        Map<String, Integer> perChannel = new HashMap<>();
        int maxLen = 0;
        for (List<Vid> l : relatedLists) maxLen = Math.max(maxLen, l.size());
        for (int i = 0; i < maxLen; i++) {
            for (List<Vid> l : relatedLists) {
                if (i >= l.size()) continue;
                Vid v = l.get(i);
                if (v.id == null || v.live || watched.contains(v.id) || shown.contains(v.id)) continue;
                String ch = v.uploaderUrl == null ? "" : Db.norm(v.uploaderUrl);
                int n = perChannel.containsKey(ch) ? perChannel.get(ch) : 0;
                if (n >= 3) continue; // Vielfalt: höchstens 3 Videos pro Kanal
                perChannel.put(ch, n + 1);
                shown.add(v.id);
                if (subUrls.contains(ch)) fromSubs.add(v);
                else fromNew.add(v);
            }
        }
        List<Vid> recs = new ArrayList<>();
        int a = 0, b = 0;
        while ((a < fromNew.size() || b < fromSubs.size()) && recs.size() < 48) {
            // etwa 3 neue Creator auf 1 bekannten
            for (int k = 0; k < 3 && a < fromNew.size(); k++) recs.add(fromNew.get(a++));
            if (b < fromSubs.size()) recs.add(fromSubs.get(b++));
        }
        if (!recs.isEmpty()) {
            home.items.add(new VideoGrid.Section("Empfohlen für dich",
                    "Passend zu deinem Verlauf und deinen Kanälen – auch von ähnlichen Creatorn"));
            home.items.addAll(recs);
        }

        // 3. Angesagt
        Paged trending = null, music = null;
        try {
            trending = trendingJob.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        try {
            music = musicJob.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        List<Vid> hot = new ArrayList<>();
        int max = Math.max(trending == null ? 0 : trending.items.size(), music == null ? 0 : music.items.size());
        for (int i = 0; i < max; i++) {
            if (trending != null && i < trending.items.size()) hot.add(trending.items.get(i));
            if (music != null && i < music.items.size()) hot.add(music.items.get(i));
        }
        List<Vid> hotFiltered = new ArrayList<>();
        for (Vid v : hot) {
            if (v.id != null && !shown.contains(v.id)) {
                shown.add(v.id);
                hotFiltered.add(v);
            }
        }
        if (!hotFiltered.isEmpty()) {
            home.items.add(new VideoGrid.Section(
                    "Angesagt",
                    recs.isEmpty() ? "Schau ein paar Videos oder folge Kanälen – dann wird die Startseite persönlich"
                            : null));
            home.items.addAll(hotFiltered);
            home.more = trending != null ? trending : music;
        }

        synchronized (Repo.class) {
            homeCache = home;
            homeCacheAt = System.currentTimeMillis();
        }
        return home;
    }

    // ---------------- Ähnliche Videos ----------------

    private static final Map<String, List<Vid>> relatedCache = Collections.synchronizedMap(
            new LinkedHashMap<String, List<Vid>>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Vid>> e) {
                    return size() > 40;
                }
            });

    public static List<Vid> related(String url) throws Exception {
        String id = Vid.idFromUrl(url);
        if (id != null) {
            List<Vid> cached = relatedCache.get(id);
            if (cached != null) return cached;
        }
        StreamInfo info = StreamInfo.getInfo(yt(), url);
        List<Vid> l = Vid.fromAll(info.getRelatedItems(), false);
        if (id != null) relatedCache.put(id, l);
        return l;
    }

    public static void cacheRelated(String id, List<Vid> l) {
        if (id != null && l != null) relatedCache.put(id, l);
    }

    public static List<Vid> dedupe(List<Vid> in) {
        Set<String> seen = new HashSet<>();
        List<Vid> out = new ArrayList<>();
        for (Vid v : in) {
            String key = v.id != null ? v.id : v.url;
            if (key != null && seen.add(key)) out.add(v);
        }
        return out;
    }
}
