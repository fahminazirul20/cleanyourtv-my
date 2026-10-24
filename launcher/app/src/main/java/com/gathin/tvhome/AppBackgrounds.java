package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;

/**
 * Per-app cinematic backdrop photos (Launcher settings, App backgrounds): a picture from
 * app_backgrounds/ in the launcher's external files dir, shown behind the dock as itself — not
 * run through HeroArt's icon-softening blur, which would reduce any photo to an unrecognisable
 * colour wash. Not an in-app file/photo picker: most Android TV firmware (this fork was built
 * against one) ships no app that answers ACTION_OPEN_DOCUMENT or ACTION_GET_CONTENT for images, so
 * pictures are copied into the folder the same way wallpapers/ already is — tools/theme.sh, a
 * file-transfer app, or adb push; see themes/README.md. The "default" entry is Home's own idle
 * backdrop — the top bar focused, or nothing focused yet right after Home starts — not a silent
 * fallback for every app that has no picture of its own; cinematic_home must be on for either
 * to show.
 */
final class AppBackgrounds {
    static final String FOLDER_NAME = "app_backgrounds";
    private static final String KEY_PREFIX = "app_bg:";
    static final String DEFAULT_KEY = "default";
    /** Longer edge of the decoded bitmap: recognisable as a photo on a 1080p screen (stretched by
     *  the ImageView's own GPU scaling) without the memory a full-resolution decode would cost. */
    private static final int MAX_DIMENSION = 960;

    private AppBackgrounds() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, FOLDER_NAME) : null;
    }

    /** This app's own picture, or null for the usual icon/banner backdrop — the "default" entry is
     *  not a fallback here; see defaultFile for Home's idle backdrop. */
    static File forPackage(Context ctx, String pkg) {
        return file(ctx, pkg);
    }

    /** Home's idle backdrop: the top bar focused, or right after Home starts before anything else
     *  has been. Null if none is set. */
    static File defaultFile(Context ctx) {
        return file(ctx, DEFAULT_KEY);
    }

    private static File file(Context ctx, String key) {
        File dir = dir(ctx);
        if (dir == null) return null;
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String name = choices.getString(KEY_PREFIX + key, null);
        if (name == null) return null;
        File file = new File(dir, name);
        return file.isFile() ? file : null;
    }

    static boolean has(Context ctx, String key) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).contains(KEY_PREFIX + key);
    }

    static void set(Context ctx, String key, String fileName) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PREFIX + key, fileName).apply();
    }

    static void clear(Context ctx, String key) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_PREFIX + key).apply();
    }

    /** Decoded to about MAX_DIMENSION on the long edge, RGB_565 (no transparency needed for a
     *  photo, half the memory of ARGB_8888). Null if the file cannot be decoded. */
    static Bitmap load(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }

    /** Mean colour for the ambient glow, sampling roughly a 40×40 grid so it stays fast regardless
     *  of the photo's decoded size — this runs once per app focus change, not per frame. */
    static int averageColor(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int stepX = Math.max(1, w / 40), stepY = Math.max(1, h / 40);
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        return n == 0 ? Color.BLACK : Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }
}
