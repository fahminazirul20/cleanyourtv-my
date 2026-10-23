package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

/**
 * The azan (adhan) takeover (AzanManager/AzanReceiver, azan_enabled): a full-screen alert over
 * whatever is on screen, Mecca wallpaper behind the prayer's name, playing azan_subuh.mp3 (Subuh)
 * or azan_other.mp3 (the other four) from the launcher's external files dir — pushed by
 * tools/theme.sh, not bundled in the APK; see themes/README.md. Back cancels early, same as letting
 * the azan finish on its own.
 */
public final class AzanActivity extends Activity {
    static final String EXTRA_PRAYER = "prayer_name";

    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_azan);

        String prayer = getIntent().getStringExtra(EXTRA_PRAYER);
        if (prayer == null) prayer = "";
        ((TextView) findViewById(R.id.azanPrayerName)).setText(prayer);
        ((TextView) findViewById(R.id.azanNow)).setText(getString(R.string.azan_now, prayer));

        File dir = Config.dir(this);
        if (dir != null) {
            File wallpaper = new File(dir, "azan_wallpaper.jpg");
            if (wallpaper.isFile()) {
                ((ImageView) findViewById(R.id.azanWallpaper))
                        .setImageBitmap(BitmapFactory.decodeFile(wallpaper.getPath()));
            }
            File audio = new File(dir, "Subuh".equals(prayer) ? "azan_subuh.mp3" : "azan_other.mp3");
            if (audio.isFile()) play(audio);
        }
    }

    private void play(File audio) {
        player = new MediaPlayer();
        try {
            player.setDataSource(audio.getPath());
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> {
                finish();
                return true;
            });
            player.prepare();
            player.start();
        } catch (java.io.IOException e) {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the screen (Home, another app opened over it, or the user cancelling) stops the
        // azan the same way Back does; it does not keep playing once off screen.
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.release();
            player = null;
        }
    }
}
