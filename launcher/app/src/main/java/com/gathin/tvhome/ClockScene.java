package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the clock, date and (show_prayer_card) the
 * Waktu Solat table, centred on black over a slow ambient glow. Every minute the content jitters a
 * few pixels off centre and the glow shifts colour, so nothing burns in. Shared by ClockDream (the
 * system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    private final TextView prayer;
    private final View prayerTable;
    private final TextView[] prayerCells;
    private final boolean showPrayer;
    private final String prayerZone;
    private int colorIndex;
    private int glowColor;

    private final ImageView photo;
    private final View photoScrim;
    private final List<File> photoFiles;
    private final long photoIntervalMs;
    private int photoIndex;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            updatePrayer();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    private final Runnable photoTick = new Runnable() {
        @Override
        public void run() {
            photoIndex = (photoIndex + 1) % photoFiles.size();
            showPhoto(photoFiles.get(photoIndex));
            handler.postDelayed(this, photoIntervalMs);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(Context context, View root) {
        content = root.findViewById(R.id.dreamContent);
        prayer = root.findViewById(R.id.dreamPrayer);
        prayerTable = root.findViewById(R.id.dreamPrayerTable);
        prayerCells = new TextView[]{
                root.findViewById(R.id.dreamImsak), root.findViewById(R.id.dreamSubuh),
                root.findViewById(R.id.dreamSyuruk), root.findViewById(R.id.dreamZohor),
                root.findViewById(R.id.dreamAsar), root.findViewById(R.id.dreamMaghrib),
                root.findViewById(R.id.dreamIsyak)};
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
        Config cfg = Config.load(context);
        showPrayer = cfg.showPrayerCard;
        prayerZone = cfg.prayerZone;
        if (showPrayer) PrayerTimes.refreshIfNeeded(context, prayerZone, this::updatePrayer);

        photo = root.findViewById(R.id.dreamPhoto);
        photoScrim = root.findViewById(R.id.dreamPhotoScrim);
        photoFiles = ScreensaverImages.images(context);
        photoIntervalMs = ScreensaverImages.intervalSeconds(context) * 1000L;
        if (!photoFiles.isEmpty()) {
            photoIndex = random.nextInt(photoFiles.size());
            photo.setVisibility(View.VISIBLE);
            photoScrim.setVisibility(View.VISIBLE);
            showPhoto(photoFiles.get(photoIndex));
        }
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            updatePrayer();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (photoFiles.size() > 1) {
            handler.removeCallbacks(photoTick);
            handler.postDelayed(photoTick, photoIntervalMs);
        }
    }

    /** Decoded the same way App backgrounds shows a photo (AppBackgrounds.load): recognisable, not
     *  blurred down to a colour wash. A failed decode just leaves the previous picture on screen. */
    private void showPhoto(File file) {
        android.graphics.Bitmap bmp = AppBackgrounds.load(file);
        if (bmp != null) photo.setImageBitmap(bmp);
    }

    /** Text only, same once-a-minute cadence as the clock drift: no network on this call, the
     *  underlying data is whatever PrayerTimes already has cached. */
    private void updatePrayer() {
        if (!showPrayer) return;
        PrayerTimes.Entry next = PrayerTimes.next();
        java.util.List<PrayerTimes.Entry> all = PrayerTimes.todayAll();
        if (next == null || all.size() != 7) {
            prayer.setVisibility(View.GONE);
            prayerTable.setVisibility(View.GONE);
            return;
        }
        java.time.format.DateTimeFormatter when = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())
                .withZone(PrayerTimes.MALAYSIA);
        java.time.Duration until = java.time.Duration.between(java.time.Instant.now(), next.time);
        boolean soon = !until.isNegative() && until.toMinutes() < 15;
        prayer.setText(prayer.getResources().getString(R.string.prayer_next, next.name, when.format(next.time),
                RaceWeekend.countdown(prayer.getResources(), until)));
        prayer.setTextColor(soon ? 0xFFE8A33D : 0xB3FFFFFF);
        prayer.setVisibility(View.VISIBLE);

        java.time.Instant now = java.time.Instant.now();
        for (int i = 0; i < prayerCells.length; i++) {
            PrayerTimes.Entry entry = all.get(i);
            prayerCells[i].setText(when.format(entry.time));
            if (entry.name.equals(next.name)) {
                prayerCells[i].setTypeface(prayerCells[i].getTypeface(), android.graphics.Typeface.BOLD);
                prayerCells[i].setTextColor(soon ? 0xFFE8A33D : 0xFFFFFFFF);
            } else if (entry.time.isBefore(now)) {
                prayerCells[i].setTypeface(prayerCells[i].getTypeface(), android.graphics.Typeface.NORMAL);
                prayerCells[i].setTextColor(0x66FFFFFF);
            } else {
                prayerCells[i].setTypeface(prayerCells[i].getTypeface(), android.graphics.Typeface.NORMAL);
                prayerCells[i].setTextColor(0xD9FFFFFF);
            }
        }
        prayerTable.setVisibility(View.VISIBLE);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** Dead centre, with a few pixels of random jitter each minute so nothing burns in; the glow
     *  stays centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        float centerX = (parent.getWidth() - content.getWidth()) / 2f;
        float centerY = (parent.getHeight() - content.getHeight()) / 2f;
        float jitter = 24 * content.getResources().getDisplayMetrics().density;
        float x = centerX + (random.nextFloat() * 2 - 1) * jitter;
        float y = centerY + (random.nextFloat() * 2 - 1) * jitter;
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
