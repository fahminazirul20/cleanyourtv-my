package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Schedules the next azan (adhan) alert as one exact alarm, from whatever PrayerTimes already has
 * cached (config.xml / theme / Launcher settings, azan_enabled). Re-armed after every alert
 * (AzanReceiver), after a reboot (AzanBootReceiver) and whenever prayer data refreshes or the
 * setting is flipped (MainActivity, LauncherSettingsActivity) — cheap to call repeatedly, a no-op
 * when the time does not actually change since AlarmManager replaces a pending alarm with the same
 * PendingIntent.
 */
final class AzanManager {

    private AzanManager() {
    }

    static void scheduleNext(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pendingIntent(app);
        if (alarms == null) return;
        if (!Config.load(app).azanEnabled) {
            alarms.cancel(pending);
            return;
        }
        PrayerTimes.Entry next = PrayerTimes.next();
        if (next == null) return;   // Re-armed once PrayerTimes.refreshIfNeeded's callback lands.
        long when = next.time.toEpochMilli();
        // The prayer's name travels with the alarm: by the moment it fires, "now" has already passed
        // next.time, so PrayerTimes.next() called fresh in AzanReceiver would skip straight to the
        // prayer after this one.
        PendingIntent pending2 = pendingIntent(app, next.name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarms.canScheduleExactAlarms()) {
            // No exact-alarm permission grant on this TV (API 31+); an inexact alarm lands within a
            // few minutes instead of to the second, close enough for an adhan.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending2);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending2);
        }
    }

    private static PendingIntent pendingIntent(Context app) {
        return pendingIntent(app, null);
    }

    private static PendingIntent pendingIntent(Context app, String prayerName) {
        Intent intent = new Intent(app, AzanReceiver.class);
        if (prayerName != null) intent.putExtra(AzanReceiver.EXTRA_PRAYER, prayerName);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(app, 0, intent, flags);
    }
}
