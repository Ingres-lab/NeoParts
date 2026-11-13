/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * "Smooth motion video": doubles the frame rate of video in the chosen apps with
 * Qualcomm's motion-compensated frame interpolation (VPP FRC on the compute DSP).
 * Only properties are written here; the MediaCodec hook in frameworks/av reads
 * them inside each app when it sets up a hardware video decoder, so a change
 * applies to the next video the app opens.
 */
public class SmoothMotionFragment extends PreferenceFragmentCompat {

    static final String PROP_ENABLE = "persist.sys.neo.smooth.enable";
    static final String PROP_LEVEL = "persist.sys.neo.smooth.level";
    static final String PROP_APP_PREFIX = "persist.sys.neo.smooth.app.";

    private static final String KEY_ENABLE = "smooth_enable";
    private static final String KEY_LEVEL = "smooth_level";
    private static final String KEY_APPS = "smooth_apps";

    /* Video players listed first in the app list. */
    private static final Set<String> KNOWN_VIDEO_APPS = new HashSet<>(Arrays.asList(
            "org.videolan.vlc", "is.xyz.mpv", "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro", "com.google.android.youtube",
            "app.revanced.android.youtube", "app.rvx.android.youtube",
            "org.schabi.newpipe", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus", "tv.twitch.android.app", "com.google.android.apps.photos",
            "com.brouken.player", "dev.anilbeesetti.nextplayer", "org.jellyfin.mobile",
            "com.plexapp.android", "com.kodi", "org.xbmc.kodi", "com.miui.videoplayer"));

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.smooth_motion_settings, rootKey);

        SwitchPreferenceCompat enable = findPreference(KEY_ENABLE);
        enable.setChecked(SystemProperties.getBoolean(PROP_ENABLE, false));
        enable.setOnPreferenceChangeListener((p, v) -> {
            SystemProperties.set(PROP_ENABLE, (Boolean) v ? "1" : "0");
            return true;
        });

        ListPreference level = findPreference(KEY_LEVEL);
        level.setValue(SystemProperties.get(PROP_LEVEL, "medium"));
        level.setOnPreferenceChangeListener((p, v) -> {
            SystemProperties.set(PROP_LEVEL, (String) v);
            return true;
        });

        populateApps(findPreference(KEY_APPS));
    }

    private void populateApps(PreferenceCategory category) {
        final Context context = getPreferenceManager().getContext();
        final PackageManager pm = context.getPackageManager();
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);

        final List<String[]> apps = new ArrayList<>();  // {package, label}
        final Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            apps.add(new String[] {pkg, ri.loadLabel(pm).toString()});
        }
        apps.sort((a, b) -> {
            final boolean va = KNOWN_VIDEO_APPS.contains(a[0]);
            final boolean vb = KNOWN_VIDEO_APPS.contains(b[0]);
            if (va != vb) {
                return va ? -1 : 1;
            }
            return a[1].compareToIgnoreCase(b[1]);
        });

        for (String[] app : apps) {
            final String prop = PROP_APP_PREFIX + app[0];
            final SwitchPreferenceCompat sw = new SwitchPreferenceCompat(context);
            sw.setKey("smooth_app_" + app[0]);
            sw.setPersistent(false);
            sw.setTitle(app[1]);
            sw.setSummary(app[0]);
            try {
                sw.setIcon(pm.getApplicationIcon(app[0]));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            sw.setChecked(SystemProperties.getBoolean(prop, false));
            sw.setOnPreferenceChangeListener((p, v) -> {
                SystemProperties.set(prop, (Boolean) v ? "1" : "0");
                return true;
            });
            category.addPreference(sw);
        }
    }
}
