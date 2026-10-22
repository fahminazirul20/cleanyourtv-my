package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Continue Watching: Android's Watch Next row (android.media.tv.TvContract.WatchNextPrograms),
 * filtered to Netflix, YouTube and Disney+ only. Any app targeting Android TV can publish here, the
 * same table the stock leanback launcher reads for its own "Play Next" row; no extra permission is
 * needed to query it. Decorative on Home, like the race and prayer panels: not focusable, no resume
 * from the remote in this version.
 */
final class WatchNext {

    private static final String TAG = "WatchNext";
    private static final List<String> PACKAGES = Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus");
    private static final int MAX_ENTRIES = 8;

    static final class Entry {
        final String title;
        final String packageName;
        final Uri posterUri;
        final String intentUri;
        /** 0-100, or -1 when the publishing app did not report a duration. */
        final int progress;

        Entry(String title, String packageName, Uri posterUri, String intentUri, int progress) {
            this.title = title;
            this.packageName = packageName;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.progress = progress;
        }
    }

    private WatchNext() {
    }

    /** Reads the Watch Next table on a background thread; onDone runs on the main thread with the
     *  result (possibly empty), never null. */
    static void load(Context ctx, java.util.function.Consumer<List<Entry>> onDone) {
        HandlerThread thread = new HandlerThread("watch-next");
        thread.start();
        Handler bg = new Handler(thread.getLooper());
        Handler main = new Handler(Looper.getMainLooper());
        bg.post(() -> {
            List<Entry> result = query(ctx);
            thread.quitSafely();
            main.post(() -> onDone.accept(result));
        });
    }

    private static List<Entry> query(Context ctx) {
        List<Entry> out = new ArrayList<>();
        String[] projection = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        };
        try (Cursor c = ctx.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, projection, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX_ENTRIES) {
                String pkg = c.getString(0);
                if (pkg == null || !PACKAGES.contains(pkg)) continue;
                String title = c.getString(1);
                String posterStr = c.getString(2);
                String intentUri = c.getString(3);
                if (title == null || posterStr == null || intentUri == null) continue;
                long pos = c.getLong(4);
                long dur = c.getLong(5);
                int progress = dur > 0 ? (int) Math.max(0, Math.min(100, pos * 100 / dur)) : -1;
                out.add(new Entry(title, pkg, Uri.parse(posterStr), intentUri, progress));
            }
        } catch (RuntimeException e) {
            // Queryable without a permission on every Android TV build this launcher supports, but a
            // vendor skin could still refuse it; an empty row is a plain miss, not a crash.
            Log.w(TAG, "query failed: " + e);
        }
        return out;
    }

    /** Decodes a poster on a background thread; onDone runs on the main thread with the bitmap, or
     *  null if it could not be read (a dead content:// link from an app that has since cleared it). */
    static void loadPoster(Context ctx, Uri uri, java.util.function.Consumer<Bitmap> onDone) {
        HandlerThread thread = new HandlerThread("watch-next-poster");
        thread.start();
        Handler bg = new Handler(thread.getLooper());
        Handler main = new Handler(Looper.getMainLooper());
        ContentResolver resolver = ctx.getContentResolver();
        bg.post(() -> {
            Bitmap bmp = null;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in != null) bmp = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.w(TAG, "poster failed for " + uri + ": " + e);
            }
            thread.quitSafely();
            Bitmap result = bmp;
            main.post(() -> onDone.accept(result));
        });
    }

    /** The resume Intent from COLUMN_INTENT_URI, or null if it does not parse. */
    static Intent resumeIntent(Entry entry) {
        try {
            return Intent.parseUri(entry.intentUri, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return null;
        }
    }
}
