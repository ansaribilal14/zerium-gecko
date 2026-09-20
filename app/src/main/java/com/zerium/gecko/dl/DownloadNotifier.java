package com.zerium.gecko.dl;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.zerium.gecko.R;

/**
 * Per-task download notifications: progress with pause/cancel actions,
 * completion with an Open action, failure with retry.
 */
public final class DownloadNotifier {

    private static final String CH_ACTIVE = "downloads_active";
    private static final String CH_DONE = "downloads_done";
    private static volatile DownloadNotifier sInstance;

    public static DownloadNotifier get(Context c) {
        if (sInstance == null) {
            synchronized (DownloadNotifier.class) {
                if (sInstance == null) sInstance = new DownloadNotifier(c);
            }
        }
        return sInstance;
    }

    private final Context ctx;
    private final NotificationManager nm;

    private DownloadNotifier(Context c) {
        ctx = c.getApplicationContext();
        nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel active = new NotificationChannel(CH_ACTIVE,
                ctx.getString(R.string.dl_channel_active),
                NotificationManager.IMPORTANCE_LOW);
        active.setShowBadge(false);
        nm.createNotificationChannel(active);
        NotificationChannel done = new NotificationChannel(CH_DONE,
                ctx.getString(R.string.dl_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(done);
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void update(DownloadTask t) {
        if (!allowed()) return;
        switch (t.status) {
            case RUNNING:
                show(activeNotif(t), (int) t.id);
                break;
            case PAUSED:
                show(pausedNotif(t), (int) t.id);
                break;
            case COMPLETED:
                show(doneNotif(t), (int) t.id);
                break;
            case FAILED:
                show(failedNotif(t), (int) t.id);
                break;
            case QUEUED:
                show(queuedNotif(t), (int) t.id);
                break;
        }
    }

    public void cancel(int id) {
        nm.cancel(id);
    }

    /** Silent summary notification for the foreground service. */
    public android.app.Notification silentSummary() {
        return new NotificationCompat.Builder(ctx, CH_ACTIVE)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(ctx.getString(R.string.dl_channel_active))
                .setContentText(ctx.getString(R.string.dl_service_running))
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void show(android.app.Notification n, int id) {
        try {
            nm.notify(id, n);
        } catch (Exception ignored) {}
    }

    private android.app.Notification activeNotif(DownloadTask t) {
        String speed = t.speedBps > 0 ? human(t.speedBps) + "/s" : "";
        String eta = "";
        if (t.speedBps > 0 && t.totalBytes > 0) {
            long secs = (t.totalBytes - t.doneBytes) / t.speedBps;
            if (secs > 0 && secs < 86400) {
                eta = secs < 90 ? secs + "s" : secs < 5400 ? (secs / 60) + "min"
                        : (secs / 3600) + "h";
            }
        }
        StringBuilder line = new StringBuilder();
        if (t.totalBytes > 0) {
            line.append(human(t.doneBytes)).append(" / ").append(human(t.totalBytes));
        } else {
            line.append(human(t.doneBytes));
        }
        if (!speed.isEmpty()) line.append(" \u00b7 ").append(speed);
        if (!eta.isEmpty()) line.append(" \u00b7 ").append(eta);

        NotificationCompat.Builder b = base(t, CH_ACTIVE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentText(line.toString());
        if (t.totalBytes > 0) {
            int pct = (int) (t.progressFraction() * 100);
            b.setProgress(100, pct, false);
        } else {
            b.setProgress(0, 0, true);
        }
        b.addAction(0, ctx.getString(R.string.dl_pause), action(t.id, "pause"));
        b.addAction(0, ctx.getString(R.string.dl_cancel), action(t.id, "cancel"));
        return b.build();
    }

    private android.app.Notification pausedNotif(DownloadTask t) {
        NotificationCompat.Builder b = base(t, CH_ACTIVE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentText(ctx.getString(R.string.dl_paused)
                        + (t.totalBytes > 0
                        ? " \u00b7 " + (int) (t.progressFraction() * 100) + "%" : ""));
        b.addAction(0, ctx.getString(R.string.dl_resume), action(t.id, "resume"));
        b.addAction(0, ctx.getString(R.string.dl_cancel), action(t.id, "cancel"));
        return b.build();
    }

    private android.app.Notification queuedNotif(DownloadTask t) {
        return base(t, CH_ACTIVE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentText(ctx.getString(R.string.dl_queued))
                .build();
    }

    private android.app.Notification doneNotif(DownloadTask t) {
        NotificationCompat.Builder b = base(t, CH_DONE)
                .setContentText(ctx.getString(R.string.dl_complete)
                        + (t.totalBytes > 0 ? " \u00b7 " + human(t.totalBytes) : ""))
                .setAutoCancel(true);
        if (t.finalUri != null && !t.finalUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(t.finalUri);
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri, t.mime == null || t.mime.isEmpty()
                        ? "*/*" : t.mime);
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pi = PendingIntent.getActivity(ctx,
                        (int) (t.id % 100000) + 7,
                        view,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(0, ctx.getString(R.string.dl_open), pi)
                        .setContentIntent(pi);
            } catch (Exception ignored) {}
        }
        return b.build();
    }

    private android.app.Notification failedNotif(DownloadTask t) {
        String why = t.error == null || t.error.isEmpty()
                ? ctx.getString(R.string.dl_err_network) : t.error;
        NotificationCompat.Builder b = base(t, CH_DONE)
                .setContentText(ctx.getString(R.string.dl_failed) + " \u00b7 " + why)
                .setAutoCancel(true);
        b.addAction(0, ctx.getString(R.string.dl_retry), action(t.id, "resume"));
        return b.build();
    }

    private NotificationCompat.Builder base(DownloadTask t, String channel) {
        return new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(t.filename)
                .setSilent(channel.equals(CH_ACTIVE))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);
    }

    private PendingIntent action(long id, String action) {
        Intent i = new Intent(ctx, DownloadService.class);
        i.setAction("com.zerium.gecko.dl." + action);
        i.putExtra("id", id);
        return PendingIntent.getService(ctx,
                (int) (id % 100000) + action.hashCode() % 50 + 10,
                i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format(java.util.Locale.US, "%.1f KB", b / 1024f);
        if (b < 1073741824L) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576f);
        return String.format(java.util.Locale.US, "%.2f GB", b / 1073741824f);
    }
}
