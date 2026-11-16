/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/*
 * Tools > Hide developer options: the chosen apps read "off" for developer options,
 * USB debugging and wireless debugging (Settings.Global development_settings_enabled,
 * adb_enabled, adb_wifi_enabled). The list lives in Settings.Global
 * neo_hide_devopts_packages; SettingsProvider (frameworks/base, NeoDevOptionsHider)
 * answers those apps with 0. Nothing changes for any other app or for the system.
 */
public class HideDevOptionsFragment extends SettingsBasePreferenceFragment {

    static final String KEY_PACKAGES = "neo_hide_devopts_packages";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final Preference info = new Preference(context);
        info.setSelectable(false);
        info.setSummary(R.string.hidedev_info);
        screen.addPreference(info);

        final PreferenceCategory apps = new PreferenceCategory(context);
        apps.setTitle(R.string.hidedev_apps_title);
        screen.addPreference(apps);
        populateApps(context, apps);
    }

    private void populateApps(Context context, PreferenceCategory category) {
        final PackageManager pm = context.getPackageManager();
        final Set<String> hidden = readList(context);
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

        final List<String[]> apps = new ArrayList<>();  // {package, label}
        final Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            final ApplicationInfo ai = ri.activityInfo.applicationInfo;
            final String pkg = ai.packageName;
            // Only installed apps: system apps can see the real state anyway
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)
                    || ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                            && !hidden.contains(pkg))) {
                continue;
            }
            apps.add(new String[] {pkg, ri.loadLabel(pm).toString()});
        }
        apps.sort((a, b) -> {
            final boolean ha = hidden.contains(a[0]);
            final boolean hb = hidden.contains(b[0]);
            if (ha != hb) {
                return ha ? -1 : 1;
            }
            return a[1].compareToIgnoreCase(b[1]);
        });

        for (String[] app : apps) {
            final String pkg = app[0];
            final SwitchPreferenceCompat sw = new SwitchPreferenceCompat(context);
            sw.setKey("hidedev_" + pkg);
            sw.setPersistent(false);
            sw.setTitle(app[1]);
            sw.setSummary(pkg);
            try {
                sw.setIcon(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            sw.setChecked(hidden.contains(pkg));
            sw.setOnPreferenceChangeListener((p, v) -> {
                setHidden(context, pkg, (Boolean) v);
                return true;
            });
            category.addPreference(sw);
        }
    }

    static Set<String> readList(Context context) {
        final Set<String> out = new LinkedHashSet<>();
        final String v = Settings.Global.getString(context.getContentResolver(), KEY_PACKAGES);
        if (!TextUtils.isEmpty(v)) {
            for (String p : v.split(",")) {
                if (!p.trim().isEmpty()) {
                    out.add(p.trim());
                }
            }
        }
        return out;
    }

    static void setHidden(Context context, String pkg, boolean hide) {
        final Set<String> list = readList(context);
        if (hide ? !list.add(pkg) : !list.remove(pkg)) {
            return;
        }
        Settings.Global.putString(context.getContentResolver(), KEY_PACKAGES,
                list.isEmpty() ? null : String.join(",", list));
        // A running app keeps the value it already read (Settings caches per process);
        // stop it so it reads the new one when it is opened again.
        context.getSystemService(ActivityManager.class).forceStopPackage(pkg);
    }
}
