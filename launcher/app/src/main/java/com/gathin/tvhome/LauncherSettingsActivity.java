package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView prayerZoneChoice;
    private String prayerZone;
    private TextView holidayStatesChoice;

    /** Display label, mycal-api slug: the 16 states/territories the Public Holidays picker offers. */
    private static final String[][] HOLIDAY_STATES = {
            {"Johor", "johor"}, {"Kedah", "kedah"}, {"Kelantan", "kelantan"}, {"Melaka", "melaka"},
            {"Negeri Sembilan", "negeri-sembilan"}, {"Pahang", "pahang"}, {"Perak", "perak"},
            {"Perlis", "perlis"}, {"Pulau Pinang", "pulau-pinang"}, {"Sabah", "sabah"},
            {"Sarawak", "sarawak"}, {"Selangor", "selangor"}, {"Terengganu", "terengganu"},
            {"Kuala Lumpur", "kuala-lumpur"}, {"Putrajaya", "wp-putrajaya"}, {"Labuan", "wp-labuan"},
    };
    private TextView azanChoice;
    private boolean azanEnabled;
    private TextView appBackgroundsChoice;
    private List<String> dockApps;
    /** Apps the App backgrounds picker offers, beyond the dock's own apps: the streaming and media
     *  apps a Malaysian setup is likely to have, the same way WatchNext hardcodes its package list.
     *  A package not installed is simply left out of the picker. */
    private static final String[] BACKGROUND_PACKAGES = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus",
            "my.gov.rtm.tv", "org.jellyfin.androidtv", "com.mitv.videoplayer",
    };
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        prayerZone = config.prayerZone;
        azanEnabled = config.azanEnabled;
        dockApps = config.dockApps;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        prayerZoneChoice = findViewById(R.id.prayerZoneChoice);
        prayerZoneChoice.setOnClickListener(v -> showPrayerZonePicker());
        holidayStatesChoice = findViewById(R.id.holidayStatesChoice);
        holidayStatesChoice.setOnClickListener(v -> showHolidayStatesPicker());
        azanChoice = findViewById(R.id.azanChoice);
        azanChoice.setOnClickListener(v -> {
            azanEnabled = !azanEnabled;
            choices.edit().putBoolean("azan_enabled", azanEnabled).apply();
            AzanManager.scheduleNext(this);
            setResult(RESULT_OK);
            refresh();
        });
        appBackgroundsChoice = findViewById(R.id.appBackgroundsChoice);
        appBackgroundsChoice.setOnClickListener(v -> showAppBackgroundsPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPrayerZonePicker() {
        List<JakimZones.Zone> zones = JakimZones.all();
        String[] labels = new String[zones.size()];
        int checked = -1;
        for (int i = 0; i < zones.size(); i++) {
            JakimZones.Zone z = zones.get(i);
            labels[i] = z.state + " — " + z.district + " (" + z.code + ")";
            if (z.code.equals(prayerZone)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.prayer_zone_setting)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    prayerZone = zones.get(which).code;
                    choices.edit().putString("prayer_zone", prayerZone).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String holidayStatesSummary() {
        if (!choices.contains("holiday_states")) return getString(R.string.holiday_states_auto);
        String stored = choices.getString("holiday_states", "");
        if (stored.isEmpty()) return getString(R.string.holiday_states_all);
        List<String> names = new ArrayList<>();
        for (String slug : stored.split(",")) {
            for (String[] s : HOLIDAY_STATES) if (s[1].equals(slug)) names.add(s[0]);
        }
        return String.join(", ", names);
    }

    private void showHolidayStatesPicker() {
        String stored = choices.getString("holiday_states", null);
        boolean configured = choices.contains("holiday_states");
        List<String> current = configured ? Arrays.asList(stored.split(",", -1)) : java.util.Collections.emptyList();
        boolean allMode = configured && stored.isEmpty();

        String[] labels = new String[HOLIDAY_STATES.length + 1];
        boolean[] checked = new boolean[HOLIDAY_STATES.length + 1];
        labels[0] = getString(R.string.holiday_states_all);
        checked[0] = allMode;
        for (int i = 0; i < HOLIDAY_STATES.length; i++) {
            labels[i + 1] = HOLIDAY_STATES[i][0];
            checked[i + 1] = !allMode && current.contains(HOLIDAY_STATES[i][1]);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.holiday_states_setting)
                .setMultiChoiceItems(labels, checked, null)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    android.widget.ListView list = ((AlertDialog) d).getListView();
                    boolean malaysiaChecked = list.isItemChecked(0);
                    List<String> picked = new ArrayList<>();
                    if (!malaysiaChecked) {
                        for (int i = 0; i < HOLIDAY_STATES.length; i++) {
                            if (list.isItemChecked(i + 1)) picked.add(HOLIDAY_STATES[i][1]);
                        }
                    }
                    choices.edit().putString("holiday_states",
                            malaysiaChecked ? "" : String.join(",", picked)).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNeutralButton(R.string.holiday_states_auto, (d, which) -> {
                    choices.edit().remove("holiday_states").apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        // Checking "Malaysia" clears and locks out the individual states, and checking any state
        // un-checks "Malaysia": the two are mutually exclusive, enforced live as the owner picks.
        android.widget.ListView list = dialog.getListView();
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                if (list.isItemChecked(0)) {
                    for (int i = 1; i <= HOLIDAY_STATES.length; i++) list.setItemChecked(i, false);
                }
            } else if (list.isItemChecked(position)) {
                list.setItemChecked(0, false);
            }
        });
    }

    /** Default background, then each known app by its own label (BACKGROUND_PACKAGES plus the
     *  dock's own apps, deduped, apps not installed left out); a custom picture already set shows a
     *  checkmark. Picking one opens showAppBackgroundAction for choose/remove. */
    private void showAppBackgroundsPicker() {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        keys.add(AppBackgrounds.DEFAULT_KEY);
        labels.add(getString(R.string.app_background_default));
        PackageManager pm = getPackageManager();
        List<String> candidates = new ArrayList<>(Arrays.asList(BACKGROUND_PACKAGES));
        for (String pkg : dockApps) if (!candidates.contains(pkg)) candidates.add(pkg);
        for (String pkg : candidates) {
            if (keys.contains(pkg)) continue;
            String label;
            try {
                label = String.valueOf(pm.getApplicationInfo(pkg, 0).loadLabel(pm));
            } catch (PackageManager.NameNotFoundException e) {
                continue;   // not installed on this TV
            }
            keys.add(pkg);
            labels.add(label);
        }
        String[] items = new String[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            items[i] = AppBackgrounds.has(this, keys.get(i)) ? labels.get(i) + " ✓" : labels.get(i);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_backgrounds_setting)
                .setItems(items, (dialog, which) -> showAppBackgroundAction(keys.get(which), labels.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Lists the pictures in app_backgrounds/ (same folder tools/theme.sh pushes) to assign to this
     *  app, with Remove when one is already set. Empty folder: a Toast naming the path instead of
     *  an empty dialog, since there is nothing to pick from an external app on this TV's firmware
     *  (see AppBackgrounds's own note). */
    private void showAppBackgroundAction(String key, String label) {
        File dir = AppBackgrounds.dir(this);
        List<File> images = WallpaperRotation.images(dir);
        if (images.isEmpty()) {
            Toast.makeText(this, getString(R.string.app_background_empty_folder,
                    "Android/data/" + getPackageName() + "/files/" + AppBackgrounds.FOLDER_NAME),
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[images.size()];
        for (int i = 0; i < images.size(); i++) names[i] = images.get(i).getName();
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        String current = choices.getString("app_bg:" + key, null);
        int checked = -1;
        for (int i = 0; i < names.length; i++) if (names[i].equals(current)) checked = i;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    AppBackgrounds.set(this, key, names[which]);
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (AppBackgrounds.has(this, key)) {
            builder.setNeutralButton(R.string.app_background_remove, (d, which) -> {
                AppBackgrounds.clear(this, key);
                setResult(RESULT_OK);
                refresh();
            });
        }
        builder.show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        JakimZones.Zone zone = JakimZones.byCode(prayerZone);
        prayerZoneChoice.setText(getString(R.string.prayer_zone_setting_state,
                zone != null ? zone.state + " (" + zone.code + ")" : prayerZone));
        holidayStatesChoice.setText(getString(R.string.holiday_states_setting_state, holidayStatesSummary()));
        azanChoice.setText(getString(R.string.azan_setting_state,
                getString(azanEnabled ? R.string.on : R.string.off)));
        int bgCount = 0;
        if (AppBackgrounds.has(this, AppBackgrounds.DEFAULT_KEY)) bgCount++;
        List<String> bgCandidates = new ArrayList<>(Arrays.asList(BACKGROUND_PACKAGES));
        for (String pkg : dockApps) if (!bgCandidates.contains(pkg)) bgCandidates.add(pkg);
        for (String pkg : bgCandidates) if (AppBackgrounds.has(this, pkg)) bgCount++;
        appBackgroundsChoice.setText(getString(R.string.app_backgrounds_setting_state,
                bgCount == 0 ? getString(R.string.app_backgrounds_none)
                        : getString(R.string.app_backgrounds_count, bgCount)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
