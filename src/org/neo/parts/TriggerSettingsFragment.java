/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Locale;

public class TriggerSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_ENABLE = "trigger_enable";
    private static final String KEY_EDIT = "trigger_edit";
    private static final String KEY_LEFT = "trigger_left";
    private static final String KEY_RIGHT = "trigger_right";
    private static final String KEY_CLEAR = "trigger_clear";
    private static final String KEY_BUBBLE = TriggerOverlayService.KEY_BUBBLE;

    private SwitchPreferenceCompat mEnable;
    private Preference mLeft;
    private Preference mRight;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.trigger_settings, rootKey);

        mEnable = findPreference(KEY_ENABLE);
        mEnable.setOnPreferenceChangeListener(this);
        mLeft = findPreference(KEY_LEFT);
        mRight = findPreference(KEY_RIGHT);

        findPreference(KEY_EDIT).setOnPreferenceClickListener(p -> {
            TriggerOverlayService.edit(requireContext());
            return true;
        });
        findPreference(KEY_BUBBLE).setOnPreferenceChangeListener((p, v) -> {
            // the preference is persisted after this returns; update once it is stored
            p.getPreferenceManager().getSharedPreferences().edit()
                    .putBoolean(KEY_BUBBLE, (Boolean) v).commit();
            TriggerOverlayService.update(requireContext());
            return true;
        });
        findPreference(KEY_CLEAR).setOnPreferenceClickListener(p -> {
            TriggerConfig.set(TriggerConfig.PROP_LEFT, null);
            TriggerConfig.set(TriggerConfig.PROP_RIGHT, null);
            update();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnable) {
            TriggerConfig.setEnabled((Boolean) newValue);
            TriggerOverlayService.update(requireContext());
            return true;
        }
        return false;
    }

    private void update() {
        mEnable.setChecked(TriggerConfig.isEnabled());
        mLeft.setSummary(describe(TriggerConfig.get(TriggerConfig.PROP_LEFT)));
        mRight.setSummary(describe(TriggerConfig.get(TriggerConfig.PROP_RIGHT)));
    }

    private String describe(TriggerConfig.Point p) {
        if (p == null) {
            return getString(R.string.trigger_point_unset);
        }
        // natural (portrait) orientation, from the top-left corner
        return getString(R.string.trigger_point_format,
                String.format(Locale.getDefault(), "%.0f", p.x * 100f / TriggerConfig.SCALE),
                String.format(Locale.getDefault(), "%.0f", p.y * 100f / TriggerConfig.SCALE));
    }
}
