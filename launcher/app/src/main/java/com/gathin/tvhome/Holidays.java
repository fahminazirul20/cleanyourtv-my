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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Malaysia public holidays for the Public Holidays card, for whichever state the Prayer zone
 * setting resolves to (see JakimZones.holidayState), from the open
 * mycal-api.huijun00100101.workers.dev REST API (no key needed; see
 * github.com/Junhui20/malaysia-calendar-api). Fetched once a day per year and cached in
 * SharedPreferences; a year that crosses into the next one (looking at next month in December) is
 * fetched as a second call and merged in.
 */
final class Holidays {

    private static final String TAG = "Holidays";
    private static final String PREFS = "holidays";
    private static final String KEY_YEARS = "years";       // "2026,2027"
    private static final String KEY_STATE = "state";        // the state this cache was fetched for
    private static final String KEY_DATA = "data";          // JSON: {"2026-10-22": {"en":"...", "states":["johor",...]}}

    static final class Holiday {
        final LocalDate date;
        final String name;
        final List<String> states;

        Holiday(LocalDate date, String name, List<String> states) {
            this.date = date;
            this.name = name;
            this.states = states;
        }
    }

    // date -> holiday; read and written only from the main thread.
    private static Map<LocalDate, Holiday> cache;
    private static String cachedYears = "";
    private static String cachedState = "";

    private Holidays() {
    }

    /** Loads the cached years (if any) and fetches whichever of this month's and next month's years
     *  are missing for these states. {@code states} is usually just the Prayer zone's own state, plus
     *  any extra states from a theme's {@code extra_holiday_states} (comma list, e.g. a second home or
     *  workplace). Safe to call every time Home starts, and whenever the Prayer zone setting changes;
     *  each year is fetched once a day at most per state combination. */
    static void refreshIfNeeded(Context ctx, List<String> states, Runnable onUpdated) {
        String stateKey = String.join(",", states);
        LocalDate today = LocalDate.now(PrayerTimes.MALAYSIA);
        int thisYear = today.getYear();
        int nextMonthYear = today.plusMonths(1).getYear();
        String wantedYears = thisYear == nextMonthYear ? String.valueOf(thisYear) : thisYear + "," + nextMonthYear;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today8 = ZonedDateTime.now(PrayerTimes.MALAYSIA).toLocalDate().toString();
        String lastFetchDay = prefs.getString("fetch_day", "");

        if (cache == null && wantedYears.equals(prefs.getString(KEY_YEARS, ""))
                && stateKey.equals(prefs.getString(KEY_STATE, "")) && today8.equals(lastFetchDay)) {
            cache = readCache(prefs.getString(KEY_DATA, "{}"));
            cachedYears = wantedYears;
            cachedState = stateKey;
        }
        if (cache != null && wantedYears.equals(cachedYears) && stateKey.equals(cachedState)) {
            if (onUpdated != null) onUpdated.run();
            return;
        }

        String[] years = wantedYears.split(",");
        HandlerThread thread = new HandlerThread("holidays-fetch");
        thread.start();
        Handler bg = new Handler(thread.getLooper());
        Handler main = new Handler(Looper.getMainLooper());
        bg.post(() -> {
            Map<LocalDate, Holiday> merged = new TreeMap<>();
            boolean ok = true;
            for (String year : years) {
                if (states.isEmpty()) {
                    // "Malaysia (all states)": one unfiltered request is the full master list.
                    if (!fetchYear(year.trim(), null, merged)) ok = false;
                } else {
                    for (String state : states) {
                        if (!fetchYear(year.trim(), state, merged)) ok = false;
                    }
                }
            }
            if (states.isEmpty()) {
                addManualOverrides(merged, "selangor"); // "all states" includes every state's own overrides
            } else {
                for (String state : states) addManualOverrides(merged, state);
            }
            thread.quitSafely();
            if (!ok || merged.isEmpty()) return;
            prefs.edit().putString(KEY_YEARS, wantedYears).putString(KEY_STATE, stateKey)
                    .putString("fetch_day", today8).putString(KEY_DATA, writeCache(merged)).apply();
            main.post(() -> {
                cache = merged;
                cachedYears = wantedYears;
                cachedState = stateKey;
                if (onUpdated != null) onUpdated.run();
            });
        });
    }

    /**
     * One-off holidays gazetted too recently for the open data source to have picked up yet.
     * {@code putIfAbsent} so an upstream update, once it lands, wins over this instead of being
     * overwritten by it.
     */
    private static void addManualOverrides(Map<LocalDate, Holiday> merged, String state) {
        if (!"selangor".equals(state)) return;
        // Selangor: Sultan Sharafuddin Idris Shah's Silver Jubilee, declared 2026-09, falls on a
        // Sunday so the state also observes the Monday after (Selangor Labour Dept, 2026-10-01).
        List<String> selangor = java.util.Collections.singletonList("selangor");
        merged.putIfAbsent(LocalDate.of(2026, 10, 25),
                new Holiday(LocalDate.of(2026, 10, 25), "Sultan Selangor's Silver Jubilee", selangor));
        merged.putIfAbsent(LocalDate.of(2026, 10, 26),
                new Holiday(LocalDate.of(2026, 10, 26), "Sultan Selangor's Silver Jubilee (observed)", selangor));
    }

    /** Fetches one year. {@code state} is a single mycal-api slug to filter server-side, or null for
     *  no filter at all (the "Malaysia (all states)" picker choice) — the full master list, every
     *  state's holidays plus federal ones, in one request instead of looping every state. Either
     *  way each entry's own "states" field from the response is what gets stored, not the query. */
    private static boolean fetchYear(String year, String state, Map<LocalDate, Holiday> merged) {
        try {
            String url = "https://mycal-api.huijun00100101.workers.dev/v1/holidays?year=" + year
                    + (state != null ? "&state=" + state : "");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            try {
                if (conn.getResponseCode() != 200) return false;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONArray data = new JSONObject(sb.toString()).getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject h = data.getJSONObject(i);
                    LocalDate date = LocalDate.parse(h.getString("date"));
                    String name = h.getJSONObject("name").optString("en", h.getJSONObject("name").optString("ms", ""));
                    List<String> states = new ArrayList<>();
                    JSONArray statesArr = h.optJSONArray("states");
                    if (statesArr != null) {
                        for (int j = 0; j < statesArr.length(); j++) states.add(statesArr.getString(j));
                    }
                    Holiday existing = merged.get(date);
                    // A different holiday already recorded for this date (rare): keep the first, a
                    // second entry on the same day is not worth a second line on a small card.
                    if (existing == null) merged.put(date, new Holiday(date, name, states));
                }
                return true;
            } finally {
                conn.disconnect();
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "fetch failed for " + year + "/" + state + ": " + e);
            return false;
        }
    }

    private static Map<LocalDate, Holiday> readCache(String json) {
        Map<LocalDate, Holiday> map = new TreeMap<>();
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject h = obj.getJSONObject(key);
                List<String> states = new ArrayList<>();
                JSONArray arr = h.getJSONArray("states");
                for (int i = 0; i < arr.length(); i++) states.add(arr.getString(i));
                map.put(LocalDate.parse(key), new Holiday(LocalDate.parse(key), h.getString("en"), states));
            }
        } catch (JSONException ignored) {
            return new TreeMap<>();
        }
        return map;
    }

    private static String writeCache(Map<LocalDate, Holiday> map) {
        JSONObject obj = new JSONObject();
        try {
            for (Holiday h : map.values()) {
                JSONObject entry = new JSONObject();
                entry.put("en", h.name);
                entry.put("states", new JSONArray(h.states));
                obj.put(h.date.toString(), entry);
            }
        } catch (JSONException ignored) {
        }
        return obj.toString();
    }

    /** Today's holiday, or null if today is not one. Null before the first fetch lands. */
    static Holiday today() {
        if (cache == null) return null;
        return cache.get(LocalDate.now(PrayerTimes.MALAYSIA));
    }

    /** The next holiday after today (today itself excluded), or null if none is cached far enough
     *  ahead. Null before the first fetch lands. */
    static Holiday next() {
        if (cache == null) return null;
        LocalDate today = LocalDate.now(PrayerTimes.MALAYSIA);
        for (Map.Entry<LocalDate, Holiday> e : cache.entrySet()) {
            if (e.getKey().isAfter(today)) return e.getValue();
        }
        return null;
    }

    /** Every cached holiday in this month and next month, today included, in date order. Empty
     *  before the first fetch lands. */
    static List<Holiday> thisAndNextMonth() {
        List<Holiday> list = new ArrayList<>();
        if (cache == null) return list;
        YearMonth thisMonth = YearMonth.now(PrayerTimes.MALAYSIA);
        YearMonth nextMonth = thisMonth.plusMonths(1);
        for (Holiday h : cache.values()) {
            YearMonth m = YearMonth.from(h.date);
            if (m.equals(thisMonth) || m.equals(nextMonth)) list.add(h);
        }
        return list;
    }
}
