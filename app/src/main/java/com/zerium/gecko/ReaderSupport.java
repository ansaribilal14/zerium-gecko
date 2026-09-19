package com.zerium.gecko;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** Builds the reader-view document from an article extracted by the
 *  shield's content script, written into the app cache directory. */
public final class ReaderSupport {

    private ReaderSupport() {}

    public static class Rendered {
        public final File file;
        public final String readingTime;
        Rendered(File file, String readingTime) {
            this.file = file;
            this.readingTime = readingTime;
        }
    }

    /** Renders the article into cacheDir/reader.html and returns the file. */
    public static Rendered render(Context ctx, Tab.ReaderArticle a,
                                  boolean dark, float textScale) {
        int mins = Math.max(1, a.length / 265 / 6);   // ~265 wpm over ~6 chars/word
        String readingTime = mins + " min read";

        StringBuilder sb = new StringBuilder(2048 + a.content.length());
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>").append(esc(a.title)).append("</title>")
          .append("<style>")
          .append(":root{--bg:#f6f7fb;--fg:#1b1b1f;--muted:#5f5f6b;--accent:#4355b9;--card:#ffffff}")
          .append(dark ? ":root{--bg:#0e1016;--fg:#e4e2e6;--muted:#9a9aa6;--accent:#8fa2ff;--card:#171a23}" : "")
          .append("*{box-sizing:border-box}")
          .append("body{margin:0;background:var(--bg);color:var(--fg);")
          .append("font-family:Georgia,'Noto Serif',serif;")
          .append("font-size:").append((int) (18 * textScale)).append("px;line-height:1.65}")
          .append("main{max-width:680px;margin:0 auto;padding:28px 18px 64px}")
          .append("h1{font-size:1.55em;line-height:1.25;margin:.4em 0}")
          .append(".meta{color:var(--muted);font-size:.78em;font-family:system-ui,sans-serif;")
          .append("margin-bottom:1.6em;border-bottom:1px solid rgba(128,128,150,.25);padding-bottom:14px}")
          .append("img,video{max-width:100%;height:auto;border-radius:10px}")
          .append("pre{overflow-x:auto;background:var(--card);padding:12px;border-radius:10px;font-size:.85em}")
          .append("blockquote{margin:0;padding-left:14px;border-left:3px solid var(--accent);color:var(--muted)}")
          .append("a{color:var(--accent)}")
          .append("table{max-width:100%;border-collapse:collapse;font-size:.85em}")
          .append("</style></head><body><main>")
          .append("<h1>").append(esc(a.title)).append("</h1>")
          .append("<div class='meta'>")
          .append(esc(a.byline.isEmpty() ? a.siteName : a.byline))
          .append(" &middot; ").append(readingTime)
          .append("</div>")
          .append(a.content)
          .append("</main></body></html>");

        try {
            File dir = new File(ctx.getCacheDir(), "reader");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "reader.html");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            return new Rendered(out, readingTime);
        } catch (Exception e) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
