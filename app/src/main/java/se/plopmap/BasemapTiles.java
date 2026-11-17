package se.plopmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.Executors;

final class BasemapTiles {
    private static final long WEEK = 7 * 24 * 60 * 60 * 1000L;
    private static final java.util.concurrent.ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> bitmaps = new LruCache<>(48);
    private final HashMap<String, Long> pending = new HashMap<>();
    private final File cache;

    BasemapTiles(Context context) {
        cache = new File(context.getCacheDir(), "osm-tiles");
    }

    Bitmap get(int zoom, int x, int y, Runnable redraw) {
        String key = zoom + "-" + x + "-" + y;
        Bitmap existing = bitmaps.get(key);
        if (existing != null) return existing;
        long now = android.os.SystemClock.elapsedRealtime();
        Long retryAt = pending.get(key);
        if (retryAt != null && retryAt > now) return null;
        if (pending.size() >= 256) pending.values().removeIf(time -> time <= now);
        if (pending.size() >= 256) return null;
        pending.put(key, Long.MAX_VALUE);
        WORKERS.execute(() -> {
            Bitmap result = load(key, zoom, x, y);
            main.post(() -> {
                if (result == null) {
                    pending.put(key, android.os.SystemClock.elapsedRealtime() + 30_000);
                } else {
                    pending.remove(key);
                    bitmaps.put(key, result);
                }
                redraw.run();
            });
        });
        return null;
    }

    private Bitmap load(String key, int zoom, int x, int y) {
        File file = new File(cache, key + ".png");
        Bitmap saved = BitmapFactory.decodeFile(file.getPath());
        if (saved != null && System.currentTimeMillis() - file.lastModified() < WEEK) return saved;
        HttpURLConnection request = null;
        File temporary = null;
        try {
            request = (HttpURLConnection) new URL("https://tile.openstreetmap.org/"
                    + zoom + "/" + x + "/" + y + ".png").openConnection();
            request.setRequestProperty("User-Agent", "PlopMap/0.1 (https://github.com/kakelakel/PlopMap)");
            request.setConnectTimeout(5000);
            request.setReadTimeout(5000);
            if (request.getResponseCode() != 200) return saved;
            byte[] data;
            try (var input = request.getInputStream(); var bytes = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 1_048_576) return saved;
                    bytes.write(buffer, 0, count);
                }
                data = bytes.toByteArray();
            }
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (image == null) return saved;
            if (cache.isDirectory() || cache.mkdirs()) {
                temporary = File.createTempFile(key, ".tmp", cache);
                try (FileOutputStream stream = new FileOutputStream(temporary)) {
                    stream.write(data);
                }
                if (temporary.renameTo(file)) prune();
            }
            return image;
        } catch (IOException error) {
            return saved;
        } finally {
            if (temporary != null) temporary.delete();
            if (request != null) request.disconnect();
        }
    }

    private synchronized void prune() {
        File[] files = cache.listFiles((directory, name) -> name.endsWith(".png"));
        if (files == null || files.length <= 512) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - 512; i++) files[i].delete();
    }
}
