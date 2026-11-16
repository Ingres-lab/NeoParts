/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/**
 * Neo home: one entry per area with a coloured icon, grouped into rounded cards like the
 * Settings homepage. Entries whose state is worth seeing at a glance show it as their summary.
 */
public class NeoSettingsFragment extends SettingsBasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_settings, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBattery();
        setSummary("gestures", getString(ThreeFingerScreenshot.isEnabled(requireContext())
                ? R.string.page_gestures_summary_on : R.string.page_gestures_summary));
        setSummary("graphics", getString(R.string.page_graphics_summary_state,
                getString(NeoGraphicsFragment.isTurnipActive()
                        ? R.string.gpu_name_turnip : R.string.gpu_name_adreno)));
        setSummary("security", getString(R.string.page_security_summary_state,
                getString(NeoSecurityFragment.isEnforcing()
                        ? R.string.selinux_state_enforcing : R.string.selinux_state_permissive)));
    }

    private void updateBattery() {
        final Intent battery = requireContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return;
        }
        final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final boolean bypass = SystemProperties.getBoolean(NeoBatteryFragment.PROP_BYPASS, false);
        setSummary("battery", getString(R.string.page_battery_summary_state, level,
                getString(bypass ? R.string.page_battery_bypass_on
                        : R.string.page_battery_bypass_off)));
    }

    private void setSummary(String key, CharSequence summary) {
        final Preference pref = findPreference(key);
        if (pref != null) {
            pref.setSummary(summary);
        }
    }
}
