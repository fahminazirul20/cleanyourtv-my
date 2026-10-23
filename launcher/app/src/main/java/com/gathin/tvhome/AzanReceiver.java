package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by the alarm AzanManager scheduled: shows the azan takeover, then arms the next one. */
public final class AzanReceiver extends BroadcastReceiver {
    static final String EXTRA_PRAYER = "prayer_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        Config config = Config.load(context);
        if (config.azanEnabled) {
            String name = intent.getStringExtra(EXTRA_PRAYER);
            Intent azan = new Intent(context, AzanActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AzanActivity.EXTRA_PRAYER, name != null ? name : "");
            context.startActivity(azan);
        }
        // Re-fetch first in case the month rolled over since the last refresh: scheduling off a
        // stale cachedDays would arm tomorrow's alert at today's leftover time.
        PrayerTimes.refreshIfNeeded(context, config.prayerZone, () -> AzanManager.scheduleNext(context));
    }
}
