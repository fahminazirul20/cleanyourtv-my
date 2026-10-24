package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView widgetChoice;
    private boolean showPrayerCard;
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
    private TextView screensaverImagesChoice;
    private TextView screensaverIntervalChoice;
    private static final int STORAGE_PERMISSION_REQUEST = 4200;
    /** What to retry once the storage-permission system dialog answers, whichever picker asked. */
    private Runnable pendingStorageAction;
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
        showPrayerCard = config.showPrayerCard;
        azanEnabled = config.azanEnabled;

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
        widgetChoice = findViewById(R.id.widgetChoice);
        widgetChoice.setOnClickListener(v -> {
            showPrayerCard = !showPrayerCard;
            choices.edit().putBoolean("show_prayer_card", showPrayerCard).apply();
            AzanManager.scheduleNext(this);
            setResult(RESULT_OK);
            refresh();
        });
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
        TextView testAzanChoice = findViewById(R.id.testAzanChoice);
        testAzanChoice.setText(R.string.test_azan_setting);
        testAzanChoice.setOnClickListener(v -> showTestAzanPicker());
        appBackgroundsChoice = findViewById(R.id.appBackgroundsChoice);
        appBackgroundsChoice.setOnClickListener(v -> showAppBackgroundsPicker());
        screensaverImagesChoice = findViewById(R.id.screensaverImagesChoice);
        screensaverImagesChoice.setOnClickListener(v -> showScreensaverImagesPicker());
        screensaverIntervalChoice = findViewById(R.id.screensaverIntervalChoice);
        screensaverIntervalChoice.setOnClickListener(v -> showScreensaverIntervalPicker());
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

    /** Previews the full-screen azan alert on demand -- same AzanActivity a real prayer time would
     *  show, audio included -- so the picture, text and sound can all be checked without waiting. */
    private void showTestAzanPicker() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.test_azan_setting)
                .setItems(new String[]{getString(R.string.test_azan_subuh), getString(R.string.test_azan_other)},
                        (d, which) -> startActivity(new Intent(this, AzanActivity.class)
                                .putExtra(AzanActivity.EXTRA_PRAYER, which == 0 ? "Subuh" : "Zohor")))
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

        // Checking "Malaysia" clears and locks out the individual states, and checking any state
        // un-checks "Malaysia": the two are mutually exclusive, enforced live as the owner picks.
        // Plain CheckBox widgets, not AlertDialog.setMultiChoiceItems: ListView.setItemChecked(),
        // called either from a raw OnItemClickListener or the documented OnMultiChoiceClickListener,
        // silently failed to clear "Malaysia" on this TV's Android build -- it stayed checked (both
        // visually and in isItemChecked()), so picking a single state still saved as "all states".
        // A real CheckBox's own setChecked() has no such ListView-choice-mode layer to misbehave in.
        float density = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * density);
        box.setPadding(pad, pad / 2, pad, pad / 2);

        android.widget.CheckBox malaysiaBox = new android.widget.CheckBox(this);
        malaysiaBox.setText(R.string.holiday_states_all);
        malaysiaBox.setTextColor(0xFFFFFFFF);
        malaysiaBox.setChecked(allMode);
        box.addView(malaysiaBox);

        android.widget.CheckBox[] stateBoxes = new android.widget.CheckBox[HOLIDAY_STATES.length];
        for (int i = 0; i < HOLIDAY_STATES.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(HOLIDAY_STATES[i][0]);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(!allMode && current.contains(HOLIDAY_STATES[i][1]));
            stateBoxes[i] = cb;
            box.addView(cb);
        }
        malaysiaBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) for (android.widget.CheckBox cb : stateBoxes) cb.setChecked(false);
        });
        for (android.widget.CheckBox cb : stateBoxes) {
            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) malaysiaBox.setChecked(false);
            });
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(R.string.holiday_states_setting)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    List<String> picked = new ArrayList<>();
                    if (!malaysiaBox.isChecked()) {
                        for (int i = 0; i < HOLIDAY_STATES.length; i++) {
                            if (stateBoxes[i].isChecked()) picked.add(HOLIDAY_STATES[i][1]);
                        }
                    }
                    choices.edit().putString("holiday_states",
                            malaysiaBox.isChecked() ? "" : String.join(",", picked)).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNeutralButton(R.string.holiday_states_auto, (d, which) -> {
                    choices.edit().remove("holiday_states").apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Default background, then every installed app (same LEANBACK_LAUNCHER/LAUNCHER query the
     *  App library uses, so this isn't limited to a hardcoded handful) sorted by label; a custom
     *  picture already set shows a checkmark. Picking one opens showAppBackgroundAction. */
    private void showAppBackgroundsPicker() {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        keys.add(AppBackgrounds.DEFAULT_KEY);
        labels.add(getString(R.string.app_background_default));

        PackageManager pm = getPackageManager();
        String self = getPackageName();
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(self) && !unique.containsKey(pkg)) unique.put(pkg, info);
            }
        }
        List<Map.Entry<String, String>> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : unique.entrySet()) {
            apps.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(),
                    Cards.cleanLabel(String.valueOf(e.getValue().loadLabel(pm)), e.getKey())));
        }
        apps.sort((a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
        for (Map.Entry<String, String> e : apps) {
            keys.add(e.getKey());
            labels.add(e.getValue());
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

    /** Lists pictures from app_backgrounds/ (no permission needed) plus, once granted, the TV's own
     *  Pictures/DCIM/Download — so any photo already on the TV can be picked, not only one pushed
     *  into app_backgrounds/. Asks for the storage permission the first time this opens without it,
     *  then re-opens itself once the system dialog answers. */
    private void showAppBackgroundAction(String key, String label) {
        if (!AppBackgrounds.hasStoragePermission(this)) {
            pendingStorageAction = () -> showAppBackgroundAction(key, label);
            requestPermissions(new String[]{AppBackgrounds.storagePermission()}, STORAGE_PERMISSION_REQUEST);
            return;
        }
        List<File> images = new ArrayList<>();
        for (File dir : AppBackgrounds.sourceDirs(this)) images.addAll(WallpaperRotation.images(dir));
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
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getAbsolutePath().equals(current)) checked = i;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    AppBackgrounds.set(this, key, images.get(which).getAbsolutePath());
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST && pendingStorageAction != null) {
            Runnable action = pendingStorageAction;
            pendingStorageAction = null;
            action.run();
        }
    }

    /** All images App backgrounds would offer: app_backgrounds/ (no permission needed) plus, once
     *  granted, the TV's own Pictures/DCIM/Download. */
    private List<File> allBackgroundImages() {
        List<File> images = new ArrayList<>();
        for (File dir : AppBackgrounds.sourceDirs(this)) images.addAll(WallpaperRotation.images(dir));
        return images;
    }

    /** Checkboxes over the same image list App backgrounds offers; OK saves the checked ones in
     *  their list order as the screensaver's rotation, replacing whatever was set before. Asks for
     *  the storage permission first if it isn't granted yet, same as App backgrounds. */
    private void showScreensaverImagesPicker() {
        if (!AppBackgrounds.hasStoragePermission(this)) {
            pendingStorageAction = this::showScreensaverImagesPicker;
            requestPermissions(new String[]{AppBackgrounds.storagePermission()}, STORAGE_PERMISSION_REQUEST);
            return;
        }
        List<File> images = allBackgroundImages();
        if (images.isEmpty()) {
            Toast.makeText(this, getString(R.string.app_background_empty_folder,
                    "Android/data/" + getPackageName() + "/files/" + AppBackgrounds.FOLDER_NAME),
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[images.size()];
        boolean[] checked = new boolean[images.size()];
        List<File> current = ScreensaverImages.images(this);
        for (int i = 0; i < images.size(); i++) {
            names[i] = images.get(i).getName();
            checked[i] = current.contains(images.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_images_setting)
                .setMultiChoiceItems(names, checked, null)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    android.widget.ListView list = ((AlertDialog) d).getListView();
                    List<File> picked = new ArrayList<>();
                    for (int i = 0; i < images.size(); i++) if (list.isItemChecked(i)) picked.add(images.get(i));
                    ScreensaverImages.setImages(this, picked);
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNeutralButton(R.string.app_background_remove, (d, which) -> {
                    ScreensaverImages.setImages(this, new ArrayList<>());
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showScreensaverIntervalPicker() {
        String[] labels = new String[ScreensaverImages.INTERVAL_PRESETS.length];
        int checked = -1;
        int current = ScreensaverImages.intervalSeconds(this);
        for (int i = 0; i < ScreensaverImages.INTERVAL_PRESETS.length; i++) {
            int secs = ScreensaverImages.INTERVAL_PRESETS[i];
            labels[i] = secs < 60 ? getString(R.string.screensaver_interval_seconds, secs)
                    : getString(R.string.screensaver_interval_minutes, secs / 60);
            if (secs == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_interval_setting)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    ScreensaverImages.setIntervalSeconds(this, ScreensaverImages.INTERVAL_PRESETS[which]);
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        widgetChoice.setText(getString(R.string.widget_setting_state,
                getString(showPrayerCard ? R.string.on : R.string.off)));
        JakimZones.Zone zone = JakimZones.byCode(prayerZone);
        prayerZoneChoice.setText(getString(R.string.prayer_zone_setting_state,
                zone != null ? zone.state + " (" + zone.code + ")" : prayerZone));
        holidayStatesChoice.setText(getString(R.string.holiday_states_setting_state, holidayStatesSummary()));
        azanChoice.setText(getString(R.string.azan_setting_state,
                getString(azanEnabled ? R.string.on : R.string.off)));
        int bgCount = AppBackgrounds.countAssigned(this);
        appBackgroundsChoice.setText(getString(R.string.app_backgrounds_setting_state,
                bgCount == 0 ? getString(R.string.app_backgrounds_none)
                        : getString(R.string.app_backgrounds_count, bgCount)));
        int ssCount = ScreensaverImages.images(this).size();
        screensaverImagesChoice.setText(getString(R.string.screensaver_images_setting_state,
                ssCount == 0 ? getString(R.string.screensaver_images_none)
                        : getString(R.string.screensaver_images_count, ssCount)));
        int interval = ScreensaverImages.intervalSeconds(this);
        screensaverIntervalChoice.setText(getString(R.string.screensaver_interval_setting_state,
                interval < 60 ? getString(R.string.screensaver_interval_seconds, interval)
                        : getString(R.string.screensaver_interval_minutes, interval / 60)));
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
