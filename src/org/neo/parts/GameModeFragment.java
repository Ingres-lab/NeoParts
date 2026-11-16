/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Neo > Gaming > Game performance: on/off, floor level and which apps count as games. */
public class GameModeFragment extends SettingsBasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final Preference info = new Preference(context);
        info.setSelectable(false);
        info.setSummary(R.string.gamemode_info);
        screen.addPreference(info);

        final SwitchPreferenceCompat enabled = new SwitchPreferenceCompat(context);
        enabled.setKey("gamemode_enabled");
        enabled.setPersistent(false);
        enabled.setTitle(R.string.gamemode_enable_title);
        enabled.setSummary(R.string.gamemode_enable_summary);
        enabled.setChecked(GameModeController.isEnabled(context));
        enabled.setOnPreferenceChangeListener((p, v) -> {
            Settings.Secure.putInt(context.getContentResolver(), GameModeController.KEY_ENABLED,
                    (Boolean) v ? 1 : 0);
            return true;
        });
        screen.addPreference(enabled);

        final ListPreference floor = new ListPreference(context);
        floor.setKey("gamemode_floor");
        floor.setPersistent(false);
        floor.setTitle(R.string.gamemode_floor_title);
        floor.setDialogTitle(R.string.gamemode_floor_title);
        floor.setEntries(R.array.gamemode_floor_entries);
        floor.setEntryValues(R.array.gamemode_floor_values);
        floor.setValue(Integer.toString(GameModeController.floor()));
        floor.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        floor.setOnPreferenceChangeListener((p, v) -> {
            GameModeController.setFloor(Integer.parseInt((String) v));
            return true;
        });
        screen.addPreference(floor);

        final PreferenceCategory apps = new PreferenceCategory(context);
        apps.setTitle(R.string.gamemode_apps_title);
        screen.addPreference(apps);
        populateApps(context, apps);
    }

    private void populateApps(Context context, PreferenceCategory category) {
        final PackageManager pm = context.getPackageManager();
        final Set<String> games = GameModeController.games(context);
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

        final List<String[]> list = new ArrayList<>();  // {package, label}
        final Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            final ApplicationInfo ai = ri.activityInfo.applicationInfo;
            final String pkg = ai.packageName;
            // games are user apps; keep a system app only if it was already chosen
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)
                    || ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                            && !games.contains(pkg))) {
                continue;
            }
            list.add(new String[] {pkg, ri.loadLabel(pm).toString()});
        }
        list.sort((a, b) -> {
            final boolean ga = games.contains(a[0]);
            final boolean gb = games.contains(b[0]);
            if (ga != gb) {
                return ga ? -1 : 1;
            }
            return a[1].compareToIgnoreCase(b[1]);
        });

        for (String[] app : list) {
            final String pkg = app[0];
            final SwitchPreferenceCompat sw = new SwitchPreferenceCompat(context);
            sw.setKey("gamemode_" + pkg);
            sw.setPersistent(false);
            sw.setTitle(app[1]);
            sw.setSummary(pkg);
            try {
                sw.setIcon(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            sw.setChecked(games.contains(pkg));
            sw.setOnPreferenceChangeListener((p, v) -> {
                GameModeController.setGame(context, pkg, (Boolean) v);
                return true;
            });
            category.addPreference(sw);
        }
    }
}
