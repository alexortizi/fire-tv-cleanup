package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches Bing's picture of the day once per ~20 hours and caches it on disk.
 * No continuous polling — only runs when the launcher asks.
 */
final class BingWallpaper {

    interface Callback {
        void onReady(File file);
    }

    private static final long FRESH_MS = 20L * 60L * 60L * 1000L;
    private static final String API =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=es-ES";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private BingWallpaper() {}

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "bing_daily.jpg");
    }

    static boolean isFresh(File f) {
        return f.exists() && f.length() > 10_000
                && System.currentTimeMillis() - f.lastModified() < FRESH_MS;
    }

    /** Delivers the cached file immediately if present; refreshes in the background when stale. */
    static void load(Context ctx, Callback cb) {
        Context app = ctx.getApplicationContext();
        File f = file(app);
        if (f.exists() && f.length() > 10_000) {
            MAIN.post(() -> cb.onReady(f));
        }
        if (isFresh(f)) return;
        IO.execute(() -> {
            try {
                if (download(app, f)) MAIN.post(() -> cb.onReady(f));
            } catch (Exception ignored) {
                // Keep the previous wallpaper (if any). Offline is fine.
            }
        });
    }

    private static boolean download(Context ctx, File dest) throws Exception {
        HttpURLConnection api = (HttpURLConnection) new URL(API).openConnection();
        api.setConnectTimeout(8_000);
        api.setReadTimeout(12_000);
        api.setRequestProperty("User-Agent", "TvHome/1.0");
        String body;
        try (InputStream in = api.getInputStream()) {
            body = readAll(in);
        } finally {
            api.disconnect();
        }
        JSONObject img = new JSONObject(body).getJSONArray("images").getJSONObject(0);
        String path = img.getString("url");
        if (path.startsWith("http")) {
            // already absolute
        } else {
            path = "https://www.bing.com" + path;
        }
        // Prefer a 1920-wide asset when Bing gives a relative OH R url.
        if (!path.contains("1920x1080") && path.contains("_")) {
            path = path.replaceAll("\\d+x\\d+", "1920x1080");
        }

        HttpURLConnection pic = (HttpURLConnection) new URL(path).openConnection();
        pic.setConnectTimeout(8_000);
        pic.setReadTimeout(30_000);
        pic.setRequestProperty("User-Agent", "TvHome/1.0");
        File tmp = new File(ctx.getFilesDir(), "bing_daily.tmp");
        try (InputStream in = pic.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            pic.disconnect();
        }
        if (tmp.length() < 10_000) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (dest.exists()) //noinspection ResultOfMethodCallIgnored
            dest.delete();
        return tmp.renameTo(dest);
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) sb.append(new String(buf, 0, n, "UTF-8"));
        return sb.toString();
    }
}
