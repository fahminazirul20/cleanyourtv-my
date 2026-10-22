package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Waktu Solat (Malaysia prayer times) for the Home card (config.xml, show_prayer_card / prayer_zone).
 * A month of JAKIM zone data comes from api.waktusolat.app, no key needed, fetched once a day and
 * cached in SharedPreferences; the minute tick only recomputes which of today's five prayers is next,
 * no network. Mirrors RaceWeekend: a small panel under the clock, same once-a-minute redraw.
 */
final class PrayerTimes {

    private static final String TAG = "PrayerTimes";
    private static final String PREFS = "prayer_times";
    private static final String KEY_ZONE = "zone";
    private static final String KEY_MONTH = "month";
    private static final String KEY_DAYS = "days";
    static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    static final class Entry {
        final String name;
        final Instant time;

        Entry(String name, Instant time) {
            this.name = name;
            this.time = time;
        }
    }

    // Read and written only from the main thread.
    private static JSONArray cachedDays;
    private static String cachedZone;

    private PrayerTimes() {
    }

    /** The JAKIM zone's state name in Malay, e.g. "JHR02" to "Johor"; the zone code itself if unknown. */
    static String stateName(String zone) {
        if (zone == null || zone.length() < 3) return zone;
        switch (zone.substring(0, 3).toUpperCase(Locale.ROOT)) {
            case "JHR": return "Johor";
            case "KDH": return "Kedah";
            case "KTN": return "Kelantan";
            case "MLK": return "Melaka";
            case "NGS": return "Negeri Sembilan";
            case "PHG": return "Pahang";
            case "PRK": return "Perak";
            case "PLS": return "Perlis";
            case "PNG": return "Pulau Pinang";
            case "SBH": return "Sabah";
            case "SWK": return "Sarawak";
            case "SGR": return "Selangor";
            case "TRG": return "Terengganu";
            case "WLY": return "Wilayah Persekutuan";
            default: return zone;
        }
    }

    /**
     * Loads the cached month (if any) and fetches in the background when the zone or month changed.
     * Safe to call every time Home starts; the network call only runs once a day per zone.
     */
    static void refreshIfNeeded(Context ctx, String zone, Runnable onUpdated) {
        if (zone == null || zone.trim().isEmpty()) return;
        String z = zone.trim();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String thisMonth = ZonedDateTime.now(MALAYSIA).format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT));

        if (cachedDays == null) {
            String savedZone = prefs.getString(KEY_ZONE, "");
            String savedMonth = prefs.getString(KEY_MONTH, "");
            if (z.equals(savedZone) && thisMonth.equals(savedMonth)) {
                try {
                    cachedDays = new JSONArray(prefs.getString(KEY_DAYS, "[]"));
                    cachedZone = z;
                } catch (JSONException ignored) {
                    // Falls through to a fresh fetch below.
                }
            }
        }
        if (cachedDays != null && z.equals(cachedZone)) {
            if (onUpdated != null) onUpdated.run();
            return;
        }

        HandlerThread thread = new HandlerThread("prayer-fetch");
        thread.start();
        Handler bg = new Handler(thread.getLooper());
        Handler main = new Handler(Looper.getMainLooper());
        bg.post(() -> {
            JSONArray days = fetch(z);
            thread.quitSafely();
            if (days == null) return;
            prefs.edit().putString(KEY_ZONE, z).putString(KEY_MONTH, thisMonth)
                    .putString(KEY_DAYS, days.toString()).apply();
            main.post(() -> {
                cachedDays = days;
                cachedZone = z;
                if (onUpdated != null) onUpdated.run();
            });
        });
    }

    private static JSONArray fetch(String zone) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.waktusolat.app/v2/solat/" + zone);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString()).getJSONArray("prayers");
        } catch (IOException | JSONException e) {
            Log.w(TAG, "fetch failed: " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * The next of today's five daily prayers after now, or tomorrow's Subuh once today's are all
     * past. Null before the first fetch lands.
     */
    static Entry next() {
        if (cachedDays == null) return null;
        ZonedDateTime now = ZonedDateTime.now(MALAYSIA);
        JSONObject today = dayFor(now.getDayOfMonth());
        if (today == null) return null;
        String[] keys = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
        String[] labels = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
        for (int i = 0; i < keys.length; i++) {
            Instant t = Instant.ofEpochSecond(today.optLong(keys[i], 0));
            if (t.isAfter(now.toInstant())) return new Entry(labels[i], t);
        }
        JSONObject tomorrow = dayFor(now.getDayOfMonth() + 1);
        if (tomorrow != null) {
            return new Entry("Subuh", Instant.ofEpochSecond(tomorrow.optLong("fajr", 0)));
        }
        // Last cached day of the month and tomorrow's row is not in yet: today's Fajr plus a day is
        // within a minute or two of tomorrow's real time, close enough until tomorrow's own fetch.
        return new Entry("Subuh", Instant.ofEpochSecond(today.optLong("fajr", 0)).plusSeconds(86400));
    }

    /**
     * All seven of today's prayer-related times, in order (Imsak, Subuh, Syuruk, Zohor, Asar,
     * Maghrib, Isyak), regardless of whether each has already passed. Empty before the first fetch
     * lands or if today is missing from the cached month.
     */
    static java.util.List<Entry> todayAll() {
        java.util.List<Entry> list = new java.util.ArrayList<>();
        if (cachedDays == null) return list;
        JSONObject today = dayFor(ZonedDateTime.now(MALAYSIA).getDayOfMonth());
        if (today == null) return list;
        String[] keys = {"imsak", "fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
        String[] labels = {"Imsak", "Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
        for (int i = 0; i < keys.length; i++) {
            list.add(new Entry(labels[i], Instant.ofEpochSecond(today.optLong(keys[i], 0))));
        }
        return list;
    }

    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
            "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"};

    /** Today's Hijri date, e.g. "11 Jamadilawal 1448", from the cached month's "hijri" field
     *  ("1448-04-19" style). Empty before the first fetch lands or if today's row has none. */
    static String todayHijri() {
        if (cachedDays == null) return "";
        JSONObject today = dayFor(ZonedDateTime.now(MALAYSIA).getDayOfMonth());
        if (today == null) return "";
        String raw = today.optString("hijri", "");
        String[] parts = raw.split("-");
        if (parts.length != 3) return "";
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12) return "";
            return day + " " + HIJRI_MONTHS[month - 1] + " " + year;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static JSONObject dayFor(int day) {
        if (cachedDays == null) return null;
        for (int i = 0; i < cachedDays.length(); i++) {
            JSONObject o = cachedDays.optJSONObject(i);
            if (o != null && o.optInt("day", -1) == day) return o;
        }
        return null;
    }
}
