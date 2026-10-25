package app.flimmer.tv;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/** Lädt Seiten für die Video-Bibliothek. Kein Konto, keine Cookies, kein Tracking. */
public final class DownloaderImpl extends Downloader {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private static DownloaderImpl instance;
    private final OkHttpClient client;

    private DownloaderImpl() {
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized DownloaderImpl get() {
        if (instance == null) instance = new DownloaderImpl();
        return instance;
    }

    public OkHttpClient client() {
        return client;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        final String method = request.httpMethod();
        final String url = request.url();
        final byte[] data = request.dataToSend();

        RequestBody body = null;
        if (data != null) {
            body = RequestBody.create(data);
        } else if ("POST".equals(method) || "PUT".equals(method)) {
            body = RequestBody.create(new byte[0]);
        }

        okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                .method(method, body)
                .url(url)
                .header("User-Agent", USER_AGENT);

        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                List<String> values = e.getValue();
                if (values == null || values.isEmpty()) continue;
                if (values.size() > 1) {
                    b.removeHeader(e.getKey());
                    for (String v : values) b.addHeader(e.getKey(), v);
                } else {
                    b.header(e.getKey(), values.get(0));
                }
            }
        }

        try (okhttp3.Response r = client.newCall(b.build()).execute()) {
            if (r.code() == 429) {
                throw new ReCaptchaException("Zu viele Anfragen", url);
            }
            ResponseBody rb = r.body();
            String text = rb != null ? rb.string() : null;
            return new Response(r.code(), r.message(), r.headers().toMultimap(), text,
                    r.request().url().toString());
        }
    }
}
