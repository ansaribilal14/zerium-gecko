package com.zerium.gecko.dl;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Foreground service keeping the process alive while downloads run and
 * handling notification actions (pause / resume / cancel).
 */
public class DownloadService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, DownloadNotifier.get(this).silentSummary());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            long id = intent.getLongExtra("id", -1);
            DownloadEngine engine = DownloadEngine.get(this);
            switch (intent.getAction()) {
                case "com.zerium.gecko.dl.pause":
                    engine.pause(id);
                    break;
                case "com.zerium.gecko.dl.resume":
                    engine.resume(id);
                    break;
                case "com.zerium.gecko.dl.cancel":
                    engine.cancel(id);
                    break;
                default:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
