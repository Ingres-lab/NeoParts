/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.neo.NeoPropHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/*
 * Tools > Pixel features: Google apps that only enable some features on Pixels (Gboard's
 * Rambler voice typing, Gemini, Pixel wallpapers...) see a Pixel 10 Pro XL. Only those
 * apps' own processes are changed (NeoPropHooks); Play Integrity has its own profile.
 */
public class PixelFeaturesFragment extends SettingsBasePreferenceFragment {

    private static final String PROP_ENABLE = "persist.sys.neo.pixel.enable";
    private static final String SETTING_APPS = "neo_pixel_spoof_apps";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final SwitchPreferenceCompat enable = new SwitchPreferenceCompat(context);
        enable.setKey("pixel_enable");
        enable.setPersistent(false);
        enable.setTitle(R.string.pixel_enable_title);
        enable.setSummary(R.string.pixel_enable_summary);
        enable.setChecked(SystemProperties.getBoolean(PROP_ENABLE, true));
        enable.setOnPreferenceChangeListener((p, v) -> {
            SystemProperties.set(PROP_ENABLE, (Boolean) v ? "1" : "0");
            for (String pkg : targets(context)) {
                stop(context, pkg);
            }
            return true;
        });
        screen.addPreference(enable);

        final PackageManager pm = context.getPackageManager();
        final Set<String> extra = readExtra(context);

        final PreferenceCategory defaults = new PreferenceCategory(context);
        defaults.setTitle(R.string.pixel_default_apps);
        screen.addPreference(defaults);
        final List<String> installed = new ArrayList<>();
        for (String pkg : NeoPropHooks.DEFAULT_PIXEL_PACKAGES) {
            try {
                pm.getApplicationInfo(pkg, 0);
                installed.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        installed.sort(String::compareTo);
        for (String pkg : installed) {
            final Preference p = new Preference(context);
            p.setSelectable(false);
            p.setTitle(label(pm, pkg));
            p.setSummary(pkg);
            try {
                p.setIcon(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            defaults.addPreference(p);
        }

        final PreferenceCategory others = new PreferenceCategory(context);
        others.setTitle(R.string.pixel_more_apps);
        screen.addPreference(others);
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final Set<String> seen = new HashSet<>(NeoPropHooks.DEFAULT_PIXEL_PACKAGES);
        seen.add(context.getPackageName());
        final List<String[]> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            final String pkg = ri.activityInfo.packageName;
            if (seen.add(pkg)) {
                apps.add(new String[] {pkg, ri.loadLabel(pm).toString()});
            }
        }
        apps.sort((a, b) -> {
            final boolean ga = a[0].startsWith("com.google."), gb = b[0].startsWith("com.google.");
            if (ga != gb) {
                return ga ? -1 : 1;  // Google apps first
            }
            return a[1].compareToIgnoreCase(b[1]);
        });
        for (String[] app : apps) {
            final String pkg = app[0];
            final SwitchPreferenceCompat sw = new SwitchPreferenceCompat(context);
            sw.setPersistent(false);
            sw.setTitle(app[1]);
            sw.setSummary(pkg);
            try {
                sw.setIcon(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            sw.setChecked(extra.contains(pkg));
            sw.setOnPreferenceChangeListener((p, v) -> {
                final Set<String> set = readExtra(context);
                if ((Boolean) v) {
                    set.add(pkg);
                } else {
                    set.remove(pkg);
                }
                Settings.Secure.putString(context.getContentResolver(), SETTING_APPS,
                        set.isEmpty() ? null : String.join("|", set));
                stop(context, pkg);
                return true;
            });
            others.addPreference(sw);
        }

        final Preference footer = new Preference(context);
        footer.setSelectable(false);
        footer.setSummary(R.string.pixel_footer);
        screen.addPreference(footer);
    }

    private static Set<String> readExtra(Context context) {
        final Set<String> out = new LinkedHashSet<>();
        final String v = Settings.Secure.getString(context.getContentResolver(), SETTING_APPS);
        if (!TextUtils.isEmpty(v)) {
            for (String p : v.split("\\|")) {
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static Set<String> targets(Context context) {
        final Set<String> all = new HashSet<>(NeoPropHooks.DEFAULT_PIXEL_PACKAGES);
        all.addAll(readExtra(context));
        return all;
    }

    /* the spoof is applied when an app process starts */
    private static void stop(Context context, String pkg) {
        context.getSystemService(ActivityManager.class).forceStopPackage(pkg);
    }

    private static CharSequence label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
