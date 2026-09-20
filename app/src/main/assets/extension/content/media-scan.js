/* Zerium G media grabber — in-page scanner content script.
 *
 * Detects downloadable media in the DOM (video/audio/source/embed/object and
 * direct file links) and reports them to the app through the per-session
 * message delegate (browser.runtime.sendMessage). Blob-backed players can be
 * captured on request: the app asks the shield background to relay a grab
 * command here (tabs.sendMessage), we fetch the blob in-page and stream it
 * back in base64 chunks.
 *
 * Honest scope: MSE/DASH streams whose media arrives as many encrypted or
 * indexed segments are only partially capturable; DRM (Widevine) streams are
 * never capturable. See README for the full scope.
 */
(function () {
    if (window.__zeriumMediaScanReady) return;
    window.__zeriumMediaScanReady = true;
    if (typeof browser === 'undefined' || !browser.runtime) return;

    var EXT = {
        mp4: "video", webm: "video", mkv: "video", mov: "video", avi: "video",
        m4v: "video", ts: "video", "3gp": "video", flv: "video", mpg: "video",
        mpeg: "video", wmv: "video", m4s: "video",
        mp3: "audio", m4a: "audio", aac: "audio", ogg: "audio", opus: "audio",
        wav: "audio", flac: "audio", wma: "audio",
        jpg: "image", jpeg: "image", png: "image", gif: "image", webp: "image",
        bmp: "image", avif: "image",
        m3u8: "hls", mpd: "hls",
        zip: "file", rar: "file", "7z": "file", tar: "file", gz: "file",
        pdf: "file", doc: "file", docx: "file", xls: "file", xlsx: "file",
        ppt: "file", pptx: "file", txt: "file", epub: "file", apk: "file"
    };
    var seen = {};
    var reportTimer = null;

    function abs(u) {
        try { return new URL(u, location.href).href; } catch (e) { return null; }
    }

    function extOf(u) {
        try {
            var p = new URL(u, location.href).pathname;
            var i = p.lastIndexOf(".");
            return i < 0 ? "" : p.slice(i + 1).toLowerCase();
        } catch (e) { return ""; }
    }

    function collect() {
        var out = [];
        var els = document.querySelectorAll("video,audio,source,embed,object,a[href]");
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            var u = "";
            try {
                u = el.src || el.href || el.getAttribute("data-src")
                    || (el.tagName === "OBJECT" ? el.data : null) || "";
            } catch (e) { u = ""; }
            if (!u || typeof u !== "string") continue;
            if (u.indexOf("data:") === 0) continue;
            if (u.indexOf("blob:") === 0) {
                if (el.tagName === "VIDEO" || el.tagName === "AUDIO") {
                    var dims = "";
                    if (el.videoWidth) dims = " " + el.videoWidth + "x" + el.videoHeight;
                    out.push({ url: u, tag: "blob", label: el.tagName.toLowerCase() + dims });
                }
                continue;
            }
            if (u.indexOf("http") !== 0 && u.indexOf("//") !== 0) continue;
            var a = abs(u);
            if (!a) continue;
            var e2 = extOf(a);
            var tag = EXT[e2] || null;
            if (!tag) {
                var tn = el.tagName;
                if (tn === "VIDEO" || (tn === "SOURCE" && el.parentElement
                        && el.parentElement.tagName === "VIDEO")) tag = "video";
                else if (tn === "AUDIO" || (tn === "SOURCE" && el.parentElement
                        && el.parentElement.tagName === "AUDIO")) tag = "audio";
                else continue;
            }
            var it = { url: a, tag: tag };
            if (el.tagName === "VIDEO" && el.videoWidth) {
                it.label = el.videoWidth + "x" + el.videoHeight;
            }
            out.push(it);
        }
        return out;
    }

    function report() {
        reportTimer = null;
        var list;
        try { list = collect(); } catch (e) { return; }
        var fresh = [];
        for (var i = 0; i < list.length && fresh.length < 50; i++) {
            var it = list[i];
            var k = it.tag + "|" + it.url;
            if (seen[k]) continue;
            seen[k] = 1;
            fresh.push(it);
        }
        if (fresh.length) {
            try {
                browser.runtime.sendMessage({ type: "media", items: fresh });
            } catch (e) {}
        }
    }

    function schedule() {
        if (reportTimer) return;
        reportTimer = setTimeout(report, 700);
    }

    try {
        new MutationObserver(schedule).observe(
            document.documentElement || document, { childList: true, subtree: true });
    } catch (e) {}
    document.addEventListener("DOMContentLoaded", schedule);
    window.addEventListener("load", schedule);
    schedule();

    /* ---- blob capture (relayed from the app via the background script) ---- */
    try {
        browser.runtime.onMessage.addListener(function (msg) {
            if (!msg || msg.type !== "media-grab" || !msg.url) return;
            grab(String(msg.url), String(msg.sid || ""), Number(msg.limit || 300));
        });
    } catch (e) {}

    function grab(blobUrl, sid, limitMB) {
        (async function () {
            try {
                var r = await fetch(blobUrl);
                var b = await r.blob();
                if (b.size > limitMB * 1024 * 1024) {
                    browser.runtime.sendMessage({ type: "blob-end", sid: sid, error: "too-large" });
                    return;
                }
                browser.runtime.sendMessage({
                    type: "blob-begin", sid: sid, size: b.size, mime: b.type || ""
                });
                var CHUNK = 524288;
                for (var off = 0; off < b.size; off += CHUNK) {
                    var sl = b.slice(off, Math.min(off + CHUNK, b.size));
                    var buf = await sl.arrayBuffer();
                    var bytes = new Uint8Array(buf);
                    var bin = "";
                    for (var i = 0; i < bytes.length; i += 8192) {
                        bin += String.fromCharCode.apply(
                            null, bytes.subarray(i, Math.min(i + 8192, bytes.length)));
                    }
                    browser.runtime.sendMessage({ type: "blob-chunk", sid: sid, b64: btoa(bin) });
                }
                browser.runtime.sendMessage({ type: "blob-end", sid: sid, error: "" });
            } catch (e) {
                try {
                    browser.runtime.sendMessage({ type: "blob-end", sid: sid, error: "fetch-failed" });
                } catch (e2) {}
            }
        })();
    }
})();
