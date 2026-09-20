package com.zerium.gecko.dl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal HTTP Live Media (m3u8) downloader: resolves master playlists to a
 * variant, then fetches media segments in order into a single .ts file.
 * Encrypted playlists (AES-128 / SAMPLE-AES) are refused with a clear
 * error — this is stated in the docs, not hidden.
 */
public final class HlsDownloader {

    public interface Callback {
        /** @param doneSegs finished segments; @param totalSegs total; @param segBytes bytes of current segment read */
        void onProgress(int doneSegs, int totalSegs, long segBytes);

        /** Poll the cooperative flags; returns the action to take. */
        DownloadEngine.Flow checkFlow();
    }

    private final String url;
    private final String userAgent;
    private final String referer;
    private final File outFile;
    private final Callback cb;

    public HlsDownloader(String url, String userAgent, String referer,
                         File outFile, Callback cb) {
        this.url = url;
        this.userAgent = userAgent;
        this.referer = referer;
        this.outFile = outFile;
        this.cb = cb;
    }

    /** @return number of segments written */
    public long run() throws IOException {
        List<String> segs = resolvePlaylist(url, 0);
        if (segs.isEmpty()) throw new IOException("no-segments");
        int total = segs.size();
        try (OutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[65536];
            for (int i = 0; i < total; i++) {
                DownloadEngine.Flow f = cb.checkFlow();
                if (f == DownloadEngine.Flow.PAUSE)
                    throw new DownloadEngine.PauseSignal();
                if (f == DownloadEngine.Flow.CANCEL)
                    throw new DownloadEngine.CancelSignal();
                long segBytes = fetchInto(resolve(segs.get(i), url), out, buf);
                cb.onProgress(i + 1, total, segBytes);
            }
        }
        return total;
    }

    /** Follows master playlist -> variant, max two hops. */
    private List<String> resolvePlaylist(String playlistUrl, int depth) throws IOException {
        String body = fetchText(playlistUrl);
        if (body == null || body.isEmpty()) throw new IOException("empty-playlist");
        if (!body.contains("#EXTM3U")) throw new IOException("not-playlist");

        if (body.contains("#EXT-X-STREAM-INF") && depth < 2) {
            // Pick the highest-bandwidth variant (lines come in pairs).
            String best = null;
            long bestBw = -1;
            String[] lines = body.split("\n");
            for (int i = 0; i < lines.length - 1; i++) {
                String line = lines[i].trim();
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    long bw = parseBandwidth(line);
                    String uri = lines[i + 1] != null ? lines[i + 1].trim() : "";
                    if (!uri.isEmpty() && !uri.startsWith("#") && bw > bestBw) {
                        bestBw = bw;
                        best = uri;
                    }
                }
            }
            if (best == null) throw new IOException("no-variant");
            return resolvePlaylist(resolve(best, playlistUrl), depth + 1);
        }

        if (body.contains("#EXT-X-KEY")) {
            String keyLine = extractKeyLine(body);
            if (keyLine != null && !keyLine.contains("METHOD=NONE")) {
                throw new IOException("encrypted-stream");
            }
        }

        List<String> segs = new ArrayList<>();
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            segs.add(t);
            if (segs.size() >= 5000) break;
        }
        return segs;
    }

    private static String extractKeyLine(String body) {
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#EXT-X-KEY")) return t;
        }
        return null;
    }

    private static long parseBandwidth(String line) {
        int i = line.indexOf("BANDWIDTH=");
        if (i < 0) return 0;
        StringBuilder sb = new StringBuilder();
        for (int j = i + 10; j < line.length(); j++) {
            char c = line.charAt(j);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (sb.length() > 0) break;
        }
        try { return Long.parseLong(sb.toString()); } catch (Exception e) { return 0; }
    }

    private long fetchInto(String segUrl, OutputStream out, byte[] buf) throws IOException {
        HttpURLConnection c = open(segUrl);
        long read = 0;
        try (InputStream is = c.getInputStream()) {
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                DownloadEngine.Flow f = cb.checkFlow();
                if (f == DownloadEngine.Flow.PAUSE)
                    throw new DownloadEngine.PauseSignal();
                if (f == DownloadEngine.Flow.CANCEL)
                    throw new DownloadEngine.CancelSignal();
            }
        } finally {
            c.disconnect();
        }
        return read;
    }

    private String fetchText(String playlistUrl) throws IOException {
        HttpURLConnection c = open(playlistUrl);
        try (InputStream is = c.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private HttpURLConnection open(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        if (userAgent != null && !userAgent.isEmpty()) {
            c.setRequestProperty("User-Agent", userAgent);
        }
        if (referer != null && !referer.isEmpty()) {
            c.setRequestProperty("Referer", referer);
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IOException("http-" + code);
        }
        return c;
    }

    private static String resolve(String u, String base) {
        try {
            return new java.net.URI(base).resolve(u).toString();
        } catch (Exception e) {
            if (u.startsWith("http")) return u;
            return base;
        }
    }
}
