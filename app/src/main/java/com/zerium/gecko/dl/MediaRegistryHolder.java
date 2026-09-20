package com.zerium.gecko.dl;

import android.content.Context;

/** Process-wide singleton access to the {@link MediaRegistry}. */
public final class MediaRegistryHolder {

    private static volatile MediaRegistry sInstance;

    private MediaRegistryHolder() {}

    public static MediaRegistry get(Context c) {
        if (sInstance == null) {
            synchronized (MediaRegistryHolder.class) {
                if (sInstance == null) sInstance = new MediaRegistry();
            }
        }
        return sInstance;
    }
}
