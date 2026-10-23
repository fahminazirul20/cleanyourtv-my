package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the azan alarm after a reboot, which clears every AlarmManager entry. */
public final class AzanBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Config config = Config.load(context);
        if (!config.azanEnabled) return;
        PrayerTimes.refreshIfNeeded(context, config.prayerZone, () -> AzanManager.scheduleNext(context));
    }
}
