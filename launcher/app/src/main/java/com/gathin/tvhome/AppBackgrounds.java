package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-app cinematic backdrop photos (Launcher settings, App backgrounds): a picture shown behind
 * the dock as itself — not run through HeroArt's icon-softening blur, which would reduce any photo
 * to an unrecognisable colour wash. A choice is stored as an absolute path, so it can point at
 * app_backgrounds/ in the launcher's external files dir (pushed by tools/theme.sh, a file-transfer
 * app, or adb push — no permission needed, works on every TV) or, with the storage permission
 * granted, any picture already on the TV in Pictures/DCIM/Download — see sourceDirs. The "default"
 * entry is Home's own idle backdrop — the top bar focused, or nothing focused yet right after Home
 * starts — not a silent fallback for every app that has no picture of its own; cinematic_home must
 * be on for either to show.
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

    /** The one folder that always works, no permission needed, plus the TV's own Pictures, DCIM
     *  and Download once the storage permission is granted — so the picker can offer a photo
     *  that's simply already on the TV, not only one pushed into app_backgrounds/. */
    static List<File> sourceDirs(Context ctx) {
        List<File> dirs = new ArrayList<>();
        File own = dir(ctx);
        if (own != null) dirs.add(own);
        if (hasStoragePermission(ctx)) {
            for (String type : new String[]{Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_DOWNLOADS}) {
                File d = Environment.getExternalStoragePublicDirectory(type);
                if (d != null) dirs.add(d);
            }
        }
        return dirs;
    }

    static String storagePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static boolean hasStoragePermission(Context ctx) {
        return ctx.checkSelfPermission(storagePermission()) == PackageManager.PERMISSION_GRANTED;
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

    /** Stored value is an absolute path (current picker) or, from before storage browsing existed,
     *  a bare filename meaning app_backgrounds/<name>. */
    private static File file(Context ctx, String key) {
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String stored = choices.getString(KEY_PREFIX + key, null);
        if (stored == null) return null;
        File file = stored.indexOf('/') >= 0 ? new File(stored) : resolveLegacy(ctx, stored);
        return file != null && file.isFile() ? file : null;
    }

    private static File resolveLegacy(Context ctx, String name) {
        File dir = dir(ctx);
        return dir != null ? new File(dir, name) : null;
    }

    static boolean has(Context ctx, String key) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).contains(KEY_PREFIX + key);
    }

    /** How many app_bg: entries are set, default included — for the settings row's summary. */
    static int countAssigned(Context ctx) {
        int n = 0;
        for (String k : ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getAll().keySet()) {
            if (k.startsWith(KEY_PREFIX)) n++;
        }
        return n;
    }

    /** path is the picture's absolute path (File.getAbsolutePath()). */
    static void set(Context ctx, String key, String path) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PREFIX + key, path).apply();
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
