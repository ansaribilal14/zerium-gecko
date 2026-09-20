package com.zerium.gecko;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

/** Application class: applies Material You dynamic color where available. */
public class ZeriumGApp extends Application {

    private static volatile ZeriumGApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Material You dynamic color honours the user's Appearance setting.
        if (new Prefs(this).dynamicColor()) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }

    public static ZeriumGApp get() {
        return instance;
    }
}
