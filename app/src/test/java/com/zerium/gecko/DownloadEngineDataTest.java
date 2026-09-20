package com.zerium.gecko;

import com.zerium.gecko.dl.CategoryResolver;
import com.zerium.gecko.dl.DownloadTask;
import com.zerium.gecko.dl.MediaRegistry;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Unit tests for the download engine data layer. */
public class DownloadEngineDataTest {

    @Test
    public void category_byExtension() {
        assertEquals(CategoryResolver.VIDEO,
                CategoryResolver.resolve("https://x.example/movie.mp4?token=1", "", ""));
        assertEquals(CategoryResolver.AUDIO,
                CategoryResolver.resolve("https://x.example/track.flac", "", ""));
        assertEquals(CategoryResolver.IMAGE,
                CategoryResolver.resolve("https://x.example/pic.WEBP", "", ""));
        assertEquals(CategoryResolver.ARCHIVE,
                CategoryResolver.resolve("https://x.example/backup.7z", "", ""));
        assertEquals(CategoryResolver.APK,
                CategoryResolver.resolve("https://x.example/app.apk", "", ""));
        assertEquals(CategoryResolver.DOC,
                CategoryResolver.resolve("https://x.example/paper.pdf", "", ""));
    }

    @Test
    public void category_byMime_whenExtensionMissing() {
        assertEquals(CategoryResolver.VIDEO,
                CategoryResolver.resolve("https://x.example/seg", "video/mp2t", ""));
        assertEquals(CategoryResolver.AUDIO,
                CategoryResolver.resolve("https://x.example/seg", "audio/ogg", ""));
        assertEquals(CategoryResolver.OTHER,
                CategoryResolver.resolve("https://x.example/seg", "text/html", ""));
    }

    @Test
    public void category_hlsManifestIsVideo() {
        assertEquals(CategoryResolver.VIDEO,
                CategoryResolver.resolve("https://x.example/stream.m3u8", "", "stream.m3u8"));
    }

    @Test
    public void task_jsonRoundtrip_preservesSegments() {
        DownloadTask t = new DownloadTask();
        t.id = 12345L;
        t.url = "https://x.example/file.bin";
        t.filename = "file.bin";
        t.mime = "application/octet-stream";
        t.pageTitle = "Page";
        t.userAgent = "UA";
        t.referer = "https://x.example/";
        t.category = CategoryResolver.ARCHIVE;
        t.status = DownloadTask.Status.PAUSED;
        t.totalBytes = 1000;
        t.doneBytes = 250;
        t.turbo = true;
        t.createdAt = 42;
        t.segments.add(new DownloadTask.Segment(0, 499));
        t.segments.get(0).done = 250;
        t.segments.add(new DownloadTask.Segment(500, 999));
        t.segments.get(1).done = 0;

        JSONObject json = t.toJson();
        DownloadTask back = DownloadTask.fromJson(json);

        assertEquals(t.id, back.id);
        assertEquals(t.url, back.url);
        assertEquals(t.filename, back.filename);
        assertEquals(t.category, back.category);
        assertEquals(DownloadTask.Status.PAUSED, back.status);
        assertEquals(t.totalBytes, back.totalBytes);
        assertEquals(t.doneBytes, back.doneBytes);
        assertEquals(2, back.segments.size());
        assertEquals(0, back.segments.get(0).start);
        assertEquals(499, back.segments.get(0).end);
        assertEquals(250, back.segments.get(0).done);
        assertEquals(500, back.segments.get(1).start);
    }

    @Test
    public void progressFraction_clamps() {
        DownloadTask t = new DownloadTask();
        t.totalBytes = 100;
        t.doneBytes = 250;
        assertEquals(1f, t.progressFraction(), 0.0001f);
        t.totalBytes = -1;
        assertEquals(0f, t.progressFraction(), 0.0001f);
    }

    @Test
    public void registry_dedupesByHostAndCapsSize() {
        MediaRegistry r = new MediaRegistry();
        for (int i = 0; i < 200; i++) {
            r.add(1L, new MediaRegistry.Item("https://x.example/v" + i + ".mp4",
                    MediaRegistry.TAG_VIDEO, "", "", -1));
        }
        // duplicate is ignored
        r.add(1L, new MediaRegistry.Item("https://x.example/v0.mp4",
                MediaRegistry.TAG_VIDEO, "", "", -1));
        assertEquals(150, r.count(1L));
        assertEquals(0, r.count(2L));
        r.clear(1L);
        assertEquals(0, r.count(1L));
    }

    @Test
    public void registry_classifiesUrls() {
        assertEquals(MediaRegistry.TAG_HLS,
                MediaRegistry.classifyUrl("https://x.example/master.m3u8?a=1"));
        assertEquals(MediaRegistry.TAG_VIDEO,
                MediaRegistry.classifyUrl("https://x.example/seg-3.ts"));
        assertEquals(MediaRegistry.TAG_AUDIO,
                MediaRegistry.classifyUrl("https://x.example/a.opus"));
        assertNull(MediaRegistry.classifyUrl("https://x.example/page.html"));
        assertTrue(MediaRegistry.isMediaMime("video/mp4"));
        assertFalse(MediaRegistry.isMediaMime("text/html"));
    }
}
