package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

/**
 * Per-app cinematic backdrop photos (Launcher settings, App backgrounds): a picture from
 * app_backgrounds/ in the launcher's external files dir, fed into HeroArt's own softening pipeline
 * in place of the app's icon/banner — same tiny-bitmap blur, same memory story, just a different
 * source image. Not an in-app file/photo picker: most Android TV firmware (this fork was built
 * against one) ships no app that answers ACTION_OPEN_DOCUMENT or ACTION_GET_CONTENT for images, so
 * pictures are copied into the folder the same way wallpapers/ already is — tools/theme.sh, a
 * file-transfer app, or adb push; see themes/README.md. One "default" entry covers every app
 * without its own picture; cinematic_home must be on for either to show.
 */
final class AppBackgrounds {
    static final String FOLDER_NAME = "app_backgrounds";
    private static final String KEY_PREFIX = "app_bg:";
    static final String DEFAULT_KEY = "default";

    private AppBackgrounds() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, FOLDER_NAME) : null;
    }

    /** This app's own picture, or the default one, or null for the usual icon/banner backdrop. */
    static File forPackage(Context ctx, String pkg) {
        File dir = dir(ctx);
        if (dir == null) return null;
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String name = choices.getString(KEY_PREFIX + pkg, null);
        if (name == null) name = choices.getString(KEY_PREFIX + DEFAULT_KEY, null);
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

    /** Decoded down to a few times HeroArt's own 48×27 target — no point keeping more, it is about to
     *  be shrunk further and blurred. Null if the file cannot be decoded. */
    static Drawable load(Context ctx, File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > HeroArt.W * 8 || bounds.outHeight / sample > HeroArt.H * 8) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), opts);
        return bmp != null ? new BitmapDrawable(ctx.getResources(), bmp) : null;
    }
}
