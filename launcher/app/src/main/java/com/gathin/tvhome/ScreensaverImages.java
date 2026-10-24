package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Photos behind the clock screensaver (Launcher settings, Screensaver images): zero, one, or
 * several pictures from the same places App backgrounds offers (AppBackgrounds.sourceDirs),
 * rotating every intervalSeconds when there's more than one. Off (no photo, the plain glow) unless
 * at least one is chosen.
 */
final class ScreensaverImages {
    private static final String KEY_IMAGES = "screensaver_images";
    private static final String KEY_INTERVAL = "screensaver_interval_seconds";
    static final int DEFAULT_INTERVAL = 10;
    /** Offered in the interval picker; a remote-friendly preset list rather than typing a number. */
    static final int[] INTERVAL_PRESETS = {5, 10, 20, 30, 60, 120, 300};

    private ScreensaverImages() {
    }

    /** Stored order, missing files skipped (moved or deleted since being chosen). */
    static List<File> images(Context ctx) {
        List<File> result = new ArrayList<>();
        String stored = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_IMAGES, "");
        if (stored.isEmpty()) return result;
        for (String path : stored.split("\n")) {
            File f = new File(path);
            if (f.isFile()) result.add(f);
        }
        return result;
    }

    static void setImages(Context ctx, List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(f.getAbsolutePath());
        }
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_IMAGES, sb.toString()).apply();
    }

    static int intervalSeconds(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_INTERVAL, DEFAULT_INTERVAL);
    }

    static void setIntervalSeconds(Context ctx, int seconds) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_INTERVAL, seconds).apply();
    }
}
