package com.zerium.gecko;

import android.content.Context;

import org.mozilla.geckoview.GeckoRuntimeSettings;

import java.io.File;

/**
 * Engine bridge for isolated Spaces on GeckoView.
 *
 * <p>GeckoView allows exactly one GeckoRuntime per process, and the runtime's
 * profile directory is selected with the standard {@code -profile} process
 * argument at launch. Each isolated space therefore owns a profile directory
 * under the app's private storage; the runtime is launched with that
 * directory, and switching isolated spaces restarts the browser process
 * (tabs are persisted first and restored into the new profile — their
 * cookies, site data and installed add-ons live in the profile, so logins
 * survive across restarts).</p>
 *
 * <p>The default space and non-isolated spaces run without {@code -profile}
 * and keep using the engine's original data directory, so existing user data
 * is untouched.</p>
 */
public final class SpaceRuntime {

    private SpaceRuntime() {}

    /** Engine profile directory for a space id. */
    public static File dirFor(Context c, String spaceId) {
        return new File(c.getFilesDir(), "spaces" + File.separator + spaceId);
    }

    /** On this edition every isolated space truly isolates. */
    public static boolean isIsolated(Spaces.Space space) {
        return space != null && space.isolated;
    }

    /**
     * Points the runtime builder at the active space's profile directory
     * when the active space is isolated. Must run before
     * {@link GeckoRuntime#create}.
     */
    public static void prepareLaunch(Context c, Spaces spaces,
                                     GeckoRuntimeSettings.Builder builder) {
        Spaces.Space active = spaces.active();
        if (!isIsolated(active)) return;
        File dir = dirFor(c, active.id);
        if (!dir.exists() && !dir.mkdirs()) {
            // Directory creation failed: fall back to the default profile
            // rather than launching with a broken path.
            return;
        }
        builder.arguments(new String[]{"-profile", dir.getAbsolutePath()});
    }

    /** True when the RUNNING process was launched for an isolated space. */
    public static boolean runningIsolated(Spaces spaces) {
        return isIsolated(spaces.active());
    }

    /** Recursively deletes a space's engine profile directory. */
    public static void deleteDir(Context c, String spaceId) {
        deleteRecursively(dirFor(c, spaceId));
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
