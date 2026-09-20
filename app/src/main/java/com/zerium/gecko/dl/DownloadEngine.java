package com.zerium.gecko.dl;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Turbo download engine: multi-connection range downloads, pause/resume,
 * retry with backoff, bounded queue, HLS playlist support and in-page blob
 * capture. All tasks are persisted in {@link DownloadStore}.
 */
public final class DownloadEngine {

    public enum Flow { CONTINUE, PAUSE, CANCEL }

    static class PauseSignal extends RuntimeException {
        PauseSignal() { super("pause"); }
    }

    static class CancelSignal extends RuntimeException {
        CancelSignal() { super("cancel"); }
    }

    /** Delivered on the main thread. */
    public interface Listener {
        void onChanged(DownloadTask t);
        void onRemoved(long id);
    }

    private static final int MAX_CONCURRENT = 3;
    private static final int MAX_CONNECTIONS = 8;
    private static final int MIN_SIZE_FOR_SPLIT = 1_500_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 3000;
    /** Blobs above this are refused (JS side also checks). */
    private static final long BLOB_LIMIT = 300L * 1024 * 1024;

    private static volatile DownloadEngine sInstance;

    public static DownloadEngine get(Context c) {
        if (sInstance == null) {
            synchronized (DownloadEngine.class) {
                if (sInstance == null) sInstance = new DownloadEngine(c);
            }
        }
        return sInstance;
    }

    private final Context ctx;
    private final DownloadStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Long, DownloadTask> tasks = new LinkedHashMap<>();
    private final Map<Long, Runner> runners = new HashMap<>();
    private final Map<String, BlobSession> blobSessions = new HashMap<>();
    private final ScheduledExecutorService ticker = new ScheduledThreadPoolExecutor(1);

    private class BlobSession {
        final DownloadTask task;
        final File file;
        boolean ended;

        BlobSession(DownloadTask task, File file) {
            this.task = task;
            this.file = file;
        }
    }

    private DownloadEngine(Context c) {
        ctx = c.getApplicationContext();
        store = new DownloadStore(ctx);
        for (DownloadTask t : store.all()) {
            // Anything marked RUNNING/QUEUED from a dead process becomes PAUSED.
            if (t.status == DownloadTask.Status.RUNNING
                    || t.status == DownloadTask.Status.QUEUED) {
                if (t.blobId != null && !t.blobId.isEmpty()) {
                    // Blob captures cannot survive process death.
                    cleanupParts(t);
                    store.remove(t.id);
                    continue;
                }
                t.status = DownloadTask.Status.PAUSED;
                store.put(t);
            }
            tasks.put(t.id, t);
        }
        ticker.scheduleWithFixedDelay(this::tick, 700, 700, TimeUnit.MILLISECONDS);
    }

    // ---------- Public API ----------

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized List<DownloadTask> snapshot() {
        return new ArrayList<>(tasks.values());
    }

    public synchronized DownloadTask byId(long id) {
        return tasks.get(id);
    }

    public DownloadTask enqueue(String url, String filename, String mime,
                                String userAgent, String referer,
                                String sourcePage, String pageTitle,
                                boolean hls, boolean turbo) {
        DownloadTask t = new DownloadTask();
        t.id = newId();
        t.url = url;
        t.filename = sanitizeName(filename);
        t.mime = mime;
        t.userAgent = userAgent;
        t.referer = referer;
        t.sourcePage = sourcePage == null ? "" : sourcePage;
        t.pageTitle = pageTitle == null ? "" : pageTitle;
        t.hls = hls;
        t.turbo = turbo;
        t.category = CategoryResolver.resolve(url, mime, t.filename);
        if (hls && t.filename.toLowerCase().contains(".m3u8")) {
            t.filename = t.filename.replaceAll("(?i)\\.m3u8$", "") + ".ts";
        }
        t.createdAt = System.currentTimeMillis();
        synchronized (this) {
            tasks.put(t.id, t);
            store.put(t);
        }
        notifyChanged(t);
        pump();
        return t;
    }

    public void pause(long id) {
        Runner r;
        DownloadTask t;
        synchronized (this) {
            r = runners.get(id);
            t = tasks.get(id);
        }
        if (r != null) {
            r.request = Flow.PAUSE;
        } else if (t != null && t.status == DownloadTask.Status.QUEUED) {
            t.status = DownloadTask.Status.PAUSED;
            store.put(t);
            notifyChanged(t);
        }
    }

    public void resume(long id) {
        DownloadTask t;
        synchronized (this) {
            t = tasks.get(id);
        }
        if (t == null) return;
        if (t.status == DownloadTask.Status.PAUSED
                || t.status == DownloadTask.Status.FAILED) {
            t.status = DownloadTask.Status.QUEUED;
            t.error = "";
            t.attempts = 0;
            store.put(t);
            notifyChanged(t);
            pump();
        }
    }

    public void cancel(long id) {
        Runner r;
        DownloadTask t;
        synchronized (this) {
            r = runners.get(id);
            t = tasks.get(id);
        }
        if (r != null) {
            r.request = Flow.CANCEL;
        } else if (t != null) {
            removeTask(t, true);
        }
    }

    public void remove(long id, boolean deleteParts) {
        cancel(id);
    }

    public void clearFinished() {
        List<DownloadTask> snap = snapshot();
        for (DownloadTask t : snap) {
            if (t.status == DownloadTask.Status.COMPLETED
                    || t.status == DownloadTask.Status.FAILED) {
                removeTask(t, true);
            }
        }
    }

    /** Records a finished pass-through download (e.g. GeckoView external responses). */
    public void recordCompleted(String url, String filename, String mime,
                                String finalUri, long size) {
        DownloadTask t = new DownloadTask();
        t.id = newId();
        t.url = url;
        t.filename = sanitizeName(filename);
        t.mime = mime;
        t.category = CategoryResolver.resolve(url, mime, t.filename);
        t.status = DownloadTask.Status.COMPLETED;
        t.totalBytes = size;
        t.doneBytes = size;
        t.finalUri = finalUri;
        t.createdAt = System.currentTimeMillis();
        t.completedAt = t.createdAt;
        synchronized (this) {
            tasks.put(t.id, t);
            store.put(t);
        }
        notifyChanged(t);
    }

    // ---------- Blob capture (in-page fetch streamed over the JS bridge) ----------

    public synchronized File beginBlob(String sessionId, long totalSize,
                                       String mime, String suggestedName) {
        if (totalSize > BLOB_LIMIT) return null;
        DownloadTask t = new DownloadTask();
        t.id = newId();
        t.blobId = sessionId;
        t.url = "captured:" + suggestedName;
        t.filename = sanitizeName(suggestedName);
        t.mime = mime;
        t.totalBytes = totalSize;
        t.status = DownloadTask.Status.RUNNING;
        t.category = CategoryResolver.resolve(null, mime, t.filename);
        t.createdAt = System.currentTimeMillis();
        File dir = partsDir(t.id);
        dir.mkdirs();
        File f = new File(dir, "capture.bin");
        blobSessions.put(sessionId, new BlobSession(t, f));
        tasks.put(t.id, t);
        store.put(t);
        updateService();
        return f;
    }

    public File blobFile(String sessionId) {
        BlobSession s;
        synchronized (this) { s = blobSessions.get(sessionId); }
        return s == null ? null : s.file;
    }

    public void blobProgress(String sessionId, long written) {
        BlobSession s;
        synchronized (this) { s = blobSessions.get(sessionId); }
        if (s == null || s.ended) return;
        s.task.doneBytes = written;
        notifyChanged(s.task);
    }

    public void endBlob(String sessionId, String error) {
        BlobSession s;
        synchronized (this) { s = blobSessions.get(sessionId); }
        if (s == null || s.ended) return;
        s.ended = true;
        blobSessions.remove(sessionId);
        DownloadTask t = s.task;
        if (error != null && !error.isEmpty()) {
            t.status = DownloadTask.Status.FAILED;
            t.error = "blob-capture-failed";
            store.put(t);
            notifyChanged(t);
            return;
        }
        try {
            t.finalUri = Publish.publish(ctx, s.file, t.filename, t.category, t.mime);
            t.status = DownloadTask.Status.COMPLETED;
            t.completedAt = System.currentTimeMillis();
            store.put(t);
        } catch (IOException e) {
            t.status = DownloadTask.Status.FAILED;
            t.error = "publish-failed";
            store.put(t);
        }
        cleanupParts(t);
        notifyChanged(t);
        updateService();
    }

    // ---------- Internal ----------

    private long newId() {
        return System.currentTimeMillis() * 10 + (int) (Math.random() * 10);
    }

    private static String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) return "download.bin";
        String clean = name.trim().replaceAll("[/\\\\]", "_");
        return clean.length() > 100 ? clean.substring(clean.length() - 100) : clean;
    }

    private File partsDir(long id) {
        return new File(ctx.getExternalFilesDir(null), "parts/" + id);
    }

    private void removeTask(DownloadTask t, boolean deleteParts) {
        DownloadTask removed;
        synchronized (this) {
            removed = tasks.remove(t.id);
        }
        if (deleteParts) cleanupParts(t);
        store.remove(t.id);
        DownloadNotifier.get(ctx).cancel((int) t.id);
        if (removed != null) {
            for (Listener l : listeners) {
                main.post(() -> l.onRemoved(t.id));
            }
        }
        updateService();
    }

    private void cleanupParts(DownloadTask t) {
        if (t.blobId == null || t.blobId.isEmpty()) {
            deleteRecursive(partsDir(t.id));
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Starts queued tasks while slots are free. */
    private void pump() {
        List<DownloadTask> snap = snapshot();
        int running;
        synchronized (this) {
            running = runners.size();
        }
        for (DownloadTask t : snap) {
            if (running >= MAX_CONCURRENT) break;
            if (t.status != DownloadTask.Status.QUEUED) continue;
            synchronized (this) {
                if (runners.containsKey(t.id)) continue;
                if (runners.size() >= MAX_CONCURRENT) break;
                Runner r = new Runner(t);
                runners.put(t.id, r);
                new Thread(r, "dl-" + t.id).start();
                running++;
            }
        }
        updateService();
    }

    private void tick() {
        List<DownloadTask> snap = snapshot();
        boolean changed = false;
        for (DownloadTask t : snap) {
            if (t.status != DownloadTask.Status.RUNNING) continue;
            if (t.blobId != null && !t.blobId.isEmpty()) {
                // Blob capture progress is driven by the JS bridge, not the
                // segment bookkeeping — do not overwrite it here.
                continue;
            }
            long done = 0;
            synchronized (t.segments) {
                for (DownloadTask.Segment s : t.segments) done += s.done;
            }
            long prev = t.doneBytes;
            // For HLS, done/total hold segment counts (progress bar only);
            // speed is derived from the same delta either way.
            t.doneBytes = done;
            long inst = Math.max(0, done - prev) * 1000 / 700;
            t.speedBps = (long) (t.speedBps * 0.5 + inst * 0.5);
            changed = true;
            notifyChanged(t);
        }
        if (changed) {
            // Persist progress at ticker rate is too aggressive; the runner
            // persists on pause/complete and every ~2s on its own.
            updateService();
        }
    }

    private void notifyChanged(DownloadTask t) {
        for (Listener l : listeners) {
            main.post(() -> l.onChanged(t));
        }
        DownloadNotifier.get(ctx).update(t);
    }

    private void updateService() {
        boolean active = false;
        for (DownloadTask t : snapshot()) {
            if (t.status == DownloadTask.Status.RUNNING
                    || t.status == DownloadTask.Status.QUEUED) {
                active = true;
                break;
            }
        }
        Intent i = new Intent(ctx, DownloadService.class);
        try {
            if (active) {
                androidx.core.content.ContextCompat.startForegroundService(ctx, i);
            } else {
                ctx.stopService(i);
            }
        } catch (Exception ignored) {}
    }

    // ---------- Runner ----------

    private class Runner implements Runnable, HlsDownloader.Callback {

        final DownloadTask task;
        volatile Flow request = Flow.CONTINUE;
        volatile long lastPersist;

        Runner(DownloadTask t) { this.task = t; }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                if (task.hls) runHls();
                else runHttp();
            } catch (PauseSignal ps) {
                task.status = DownloadTask.Status.PAUSED;
                task.speedBps = 0;
                store.put(task);
                notifyChanged(task);
            } catch (CancelSignal cs) {
                removeTask(task, true);
            } catch (IOException e) {
                fail(e);
            } catch (Throwable e) {
                fail(new IOException(e.getMessage() == null ? "error" : e.getMessage()));
            } finally {
                synchronized (DownloadEngine.this) {
                    runners.remove(task.id);
                }
                pump();
            }
        }

        private void fail(IOException e) {
            // A server that silently ignores Range requests would fail every
            // segment at its own offset — fall back to a single connection.
            if ("range-ignored".equals(e.getMessage()) && task.segments.size() > 1) {
                synchronized (task.segments) {
                    task.segments.clear();
                    if (task.totalBytes > 0) {
                        task.segments.add(new DownloadTask.Segment(0, task.totalBytes - 1));
                    }
                }
                cleanupParts(task);
                task.doneBytes = 0;
                task.status = DownloadTask.Status.QUEUED;
                store.put(task);
                notifyChanged(task);
                main.postDelayed(DownloadEngine.this::pump, RETRY_DELAY_MS);
                return;
            }
            task.attempts++;
            if (task.attempts < MAX_ATTEMPTS && request != Flow.CANCEL) {
                task.status = DownloadTask.Status.QUEUED;
                store.put(task);
                notifyChanged(task);
                main.postDelayed(DownloadEngine.this::pump, RETRY_DELAY_MS * task.attempts);
            } else {
                task.status = DownloadTask.Status.FAILED;
                task.error = friendly(e);
                task.speedBps = 0;
                store.put(task);
                notifyChanged(task);
            }
        }

        private String friendly(IOException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            switch (m) {
                case "encrypted-stream": return ctx.getString(R.string.dl_err_encrypted);
                case "range-ignored": return ctx.getString(R.string.dl_err_range);
                case "http-403": return ctx.getString(R.string.dl_err_forbidden);
                case "http-404": return ctx.getString(R.string.dl_err_notfound);
                case "publish-failed":
                case "mediastore-insert": return ctx.getString(R.string.dl_err_space);
                default:
                    if (m.startsWith("http-")) {
                        return ctx.getString(R.string.dl_err_http, m.substring(5));
                    }
                    return ctx.getString(R.string.dl_err_network);
            }
        }

        // ----- HTTP (segmented) -----

        private void runHttp() throws IOException {
            Probed p = probe(task.url);
            task.totalBytes = p.length;
            if (task.mime == null || task.mime.isEmpty()) task.mime = p.mime;

            File dir = partsDir(task.id);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs");

            synchronized (task.segments) {
                if (task.segments.isEmpty()) {
                    buildSegments(p.length, p.ranges);
                }
            }

            List<Thread> threads = new ArrayList<>();
            final IOException[] failure = {null};
            List<DownloadTask.Segment> segs;
            synchronized (task.segments) {
                segs = new ArrayList<>(task.segments);
            }
            for (int i = 0; i < segs.size(); i++) {
                DownloadTask.Segment seg = segs.get(i);
                if (isFinished(seg)) continue;
                File segFile = new File(dir, "seg" + i);
                Thread th = new Thread(() -> {
                    try {
                        fetchSegment(p.finalUrl, seg, segFile);
                    } catch (IOException | RuntimeException e) {
                        synchronized (failure) {
                            if (failure[0] == null) {
                                if (e instanceof PauseSignal) {
                                    failure[0] = new IOException("pause");
                                } else if (e instanceof CancelSignal) {
                                    failure[0] = new IOException("cancel");
                                } else {
                                    failure[0] = e instanceof IOException
                                            ? (IOException) e
                                            : new IOException(e.getMessage());
                                }
                            }
                        }
                    }
                }, "dl-seg-" + task.id);
                threads.add(th);
                th.start();
            }
            for (Thread th : threads) {
                try { th.join(); } catch (InterruptedException ignored) {}
            }
            if (request == Flow.PAUSE && failure[0] == null) throw new PauseSignal();
            if (request == Flow.CANCEL) throw new CancelSignal();
            if (failure[0] != null) {
                if ("pause".equals(failure[0].getMessage())) throw new PauseSignal();
                if ("cancel".equals(failure[0].getMessage())) throw new CancelSignal();
                throw failure[0];
            }

            // Aggregate final byte count, then merge + publish.
            long done = 0;
            synchronized (task.segments) {
                for (DownloadTask.Segment s : task.segments) done += s.done;
            }
            task.doneBytes = done;
            if (task.totalBytes < 0) task.totalBytes = done;

            File out;
            if (segs.size() == 1) {
                out = new File(dir, "seg0");
            } else {
                out = new File(dir, "merged.bin");
                merge(dir, segs, out);
            }
            String finalUri = Publish.publish(ctx, out, task.filename, task.category, task.mime);
            deleteRecursive(dir);
            task.finalUri = finalUri;
            task.status = DownloadTask.Status.COMPLETED;
            task.completedAt = System.currentTimeMillis();
            task.speedBps = 0;
            store.put(task);
            notifyChanged(task);
        }

        private void buildSegments(long total, boolean ranges) {
            if (total <= 0) {
                task.segments.add(new DownloadTask.Segment(0, -1));
                return;
            }
            if (!ranges || !task.turbo || total < MIN_SIZE_FOR_SPLIT) {
                task.segments.add(new DownloadTask.Segment(0, total - 1));
                return;
            }
            int n = (int) Math.min(MAX_CONNECTIONS, Math.max(2, total / MIN_SIZE_FOR_SPLIT));
            long chunk = total / n;
            for (int i = 0; i < n; i++) {
                long start = i * chunk;
                long end = (i == n - 1) ? total - 1 : (start + chunk - 1);
                task.segments.add(new DownloadTask.Segment(start, end));
            }
        }

        private static boolean isFinished(DownloadTask.Segment s) {
            return s.end >= 0 && s.done >= (s.end - s.start + 1);
        }

        private void fetchSegment(String url, DownloadTask.Segment seg, File segFile)
                throws IOException {
            byte[] buf = new byte[65536];
            while (!isFinished(seg)) {
                checkFlow();
                // Crash between write and offset bookkeeping would leave the
                // segment file longer than seg.done — truncate to the recorded
                // offset before appending again.
                if (segFile.exists() && segFile.length() > seg.done) {
                    try (java.io.RandomAccessFile raf =
                                 new java.io.RandomAccessFile(segFile, "rw")) {
                        raf.setLength(seg.done);
                    }
                }
                HttpURLConnection c = open(url,
                        seg.start + seg.done,
                        seg.end < 0 ? -1 : seg.end);
                int code = c.getResponseCode();
                if (code == 416 && seg.end < 0) {
                    // Range past the end on an unknown-size stream: complete.
                    c.disconnect();
                    break;
                }
                if (code == 200 && (seg.start + seg.done) > 0) {
                    c.disconnect();
                    throw new IOException("range-ignored");
                }
                if (code < 200 || code >= 300) {
                    c.disconnect();
                    throw new IOException("http-" + code);
                }
                try (InputStream is = c.getInputStream();
                     OutputStream os = new FileOutputStream(segFile, true)) {
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        seg.done += n;
                        checkFlow();
                    }
                } finally {
                    c.disconnect();
                }
                maybePersist();
                if (seg.end < 0) {
                    // Unknown total size: one full pass over the stream is the
                    // whole download — stop instead of re-requesting.
                    break;
                }
            }
        }

        private void maybePersist() {
            long now = System.currentTimeMillis();
            if (now - lastPersist > 2000) {
                lastPersist = now;
                store.put(task);
            }
        }

        private void merge(File dir, List<DownloadTask.Segment> segs, File out)
                throws IOException {
            try (OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                for (int i = 0; i < segs.size(); i++) {
                    File f = new File(dir, "seg" + i);
                    try (InputStream is = new FileInputStream(f)) {
                        int n;
                        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
            }
        }

        // ----- HLS -----

        private void runHls() throws IOException {
            File dir = partsDir(task.id);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs");
            File out = new File(dir, "merged.ts");
            HlsDownloader hls = new HlsDownloader(task.url, task.userAgent,
                    task.referer, out, this);
            hls.run();
            String finalUri = Publish.publish(ctx, out, task.filename, task.category, "video/mp2t");
            deleteRecursive(dir);
            task.finalUri = finalUri;
            task.status = DownloadTask.Status.COMPLETED;
            task.completedAt = System.currentTimeMillis();
            task.speedBps = 0;
            store.put(task);
            notifyChanged(task);
        }

        @Override
        public void onProgress(int doneSegs, int totalSegs, long segBytes) {
            task.doneBytes = doneSegs;
            task.totalBytes = totalSegs;
            if (System.currentTimeMillis() - lastPersist > 2000) {
                lastPersist = System.currentTimeMillis();
                store.put(task);
            }
            notifyChanged(task);
        }

        @Override
        public DownloadEngine.Flow checkFlow() {
            return request;
        }

        // ----- Shared plumbing -----

        private void checkFlow() {
            if (request == Flow.PAUSE) throw new PauseSignal();
            if (request == Flow.CANCEL) throw new CancelSignal();
        }

        private class Probed {
            String finalUrl;
            long length = -1;
            String mime;
            boolean ranges;
        }

        /** HEAD probe with manual cross-scheme redirect handling. */
        private Probed probe(String url) throws IOException {
            String current = url;
            for (int hop = 0; hop < 6; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod("HEAD");
                applyHeaders(c);
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303
                        || code == 307 || code == 308) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null || loc.isEmpty()) throw new IOException("http-" + code);
                    current = resolveUrl(loc, current);
                    continue;
                }
                if (code == 405 || code == 501) {
                    // HEAD refused: probe with a 1-byte range request.
                    c.disconnect();
                    return probeGet(current);
                }
                if (code < 200 || code >= 300) {
                    c.disconnect();
                    throw new IOException("http-" + code);
                }
                Probed p = new Probed();
                p.finalUrl = current;
                p.length = c.getContentLengthLong();
                p.mime = c.getContentType();
                String accept = c.getHeaderField("Accept-Ranges");
                p.ranges = accept != null && accept.contains("bytes");
                c.disconnect();
                // Some servers report ranges in HEAD but ignore them on GET;
                // total < split threshold means single segment anyway.
                return p;
            }
            throw new IOException("too-many-redirects");
        }

        private Probed probeGet(String url) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("GET");
            c.setRequestProperty("Range", "bytes=0-0");
            applyHeaders(c);
            int code = c.getResponseCode();
            Probed p = new Probed();
            p.finalUrl = url;
            p.mime = c.getContentType();
            if (code == 206) {
                p.ranges = true;
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.contains("/")) {
                    try {
                        p.length = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1));
                    } catch (Exception ignored) {}
                }
            } else if (code == 200) {
                p.ranges = false;
                p.length = c.getContentLengthLong();
            } else {
                c.disconnect();
                throw new IOException("http-" + code);
            }
            c.disconnect();
            return p;
        }

        private HttpURLConnection open(String url, long from, long to)
                throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod("GET");
            String range = from > 0
                    ? "bytes=" + from + "-" + (to >= 0 ? to : "")
                    : (to >= 0 && task.segments.size() > 1
                        ? "bytes=" + from + "-" + to : null);
            if (range != null) c.setRequestProperty("Range", range);
            applyHeaders(c);
            return c;
        }

        private void applyHeaders(HttpURLConnection c) {
            if (task.userAgent != null && !task.userAgent.isEmpty()) {
                c.setRequestProperty("User-Agent", task.userAgent);
            }
            if (task.referer != null && !task.referer.isEmpty()) {
                c.setRequestProperty("Referer", task.referer);
            }
            // GeckoView cookies are engine-internal; downloads send UA + Referer only.
        }

        private static String resolveUrl(String loc, String base) {
            try {
                return new java.net.URI(base).resolve(loc).toString();
            } catch (Exception e) {
                return loc;
            }
        }
    }
}
