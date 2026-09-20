package com.zerium.gecko.dl;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Moves a finished temp file into its public destination:
 * API 29+ uses the MediaStore Downloads collection under
 * Download/Zerium/&lt;Category&gt;/, older devices write directly into the
 * public Downloads dir (or the app dir when storage permission is missing).
 */
public final class Publish {

    private Publish() {}

    public static String publish(Context ctx, File tmp, String filename,
                                 int category, String mime) throws IOException {
        String folder = CategoryResolver.FOLDER[category];
        String name = uniqueName(filename);
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Zerium/" + folder);
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (item == null) throw new IOException("mediastore-insert");
            try (OutputStream os = ctx.getContentResolver().openOutputStream(item);
                 InputStream is = new FileInputStream(tmp)) {
                if (os == null) throw new IOException("stream");
                copy(is, os);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(item, done, null, null);
            return item.toString();
        }

        // Legacy public dir (needs WRITE_EXTERNAL_STORAGE on API <= 28)
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Zerium/" + folder);
        File out;
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs");
            out = new File(dir, name);
            try (InputStream is = new FileInputStream(tmp);
                 OutputStream os = new FileOutputStream(out)) {
                copy(is, os);
            }
        } catch (IOException e) {
            // No storage permission or card unmounted — keep the file in the
            // app dir instead of failing the whole download.
            File appDir = new File(ctx.getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS), "Zerium/" + folder);
            if (!appDir.exists() && !appDir.mkdirs()) throw new IOException("mkdirs");
            out = new File(appDir, name);
            try (InputStream is = new FileInputStream(tmp);
                 OutputStream os = new FileOutputStream(out)) {
                copy(is, os);
            }
        }
        return out.getAbsolutePath();
    }

    /** Avoids silent overwrites in the public collection. */
    private static String uniqueName(String filename) {
        String clean = filename == null || filename.trim().isEmpty()
                ? "download.bin" : filename.trim();
        // Strip path separators the web may have smuggled in.
        clean = clean.replaceAll("[/\\\\]", "_");
        String ext = "";
        String base = clean;
        int dot = clean.lastIndexOf('.');
        if (dot > 0) {
            ext = clean.substring(dot);
            base = clean.substring(0, dot);
        }
        if (base.length() > 80) base = base.substring(0, 80);
        return base + "-" + Long.toString(System.currentTimeMillis(), 36) + ext;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
