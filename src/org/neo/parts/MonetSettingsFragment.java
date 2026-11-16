/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/**
 * Neo Monet tuning. The values are Settings.Secure integers; SystemUI's ThemeOverlayController
 * observes them and re-tones the wallpaper colours (see NeoMonetTuning in frameworks/libs).
 */
public class MonetSettingsFragment extends SettingsBasePreferenceFragment {
    // Keep in sync with com.android.systemui.monet.NeoMonetTuning
    private static final String KEY_ACCENT = "neo_monet_accent_chroma";
    private static final String KEY_NEUTRAL = "neo_monet_neutral_chroma";
    private static final String KEY_HUE = "neo_monet_hue_shift";

    private SeekBarPreference mAccent;
    private SeekBarPreference mNeutral;
    private SeekBarPreference mHue;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.monet_settings, rootKey);

        mAccent = bind("monet_accent_chroma", KEY_ACCENT, 100);
        mNeutral = bind("monet_neutral_chroma", KEY_NEUTRAL, 100);
        mHue = bind("monet_hue_shift", KEY_HUE, 0);

        final Preference reset = findPreference("monet_reset");
        reset.setOnPreferenceClickListener(p -> {
            put(KEY_ACCENT, 100);
            put(KEY_NEUTRAL, 100);
            put(KEY_HUE, 0);
            mAccent.setValue(100);
            mNeutral.setValue(100);
            mHue.setValue(0);
            return true;
        });
    }

    private SeekBarPreference bind(String prefKey, String settingKey, int def) {
        final SeekBarPreference pref = findPreference(prefKey);
        pref.setValue(get(settingKey, def));
        pref.setOnPreferenceChangeListener((p, value) -> {
            put(settingKey, (Integer) value);
            return true;
        });
        return pref;
    }

    private int get(String key, int def) {
        return Settings.Secure.getInt(requireContext().getContentResolver(), key, def);
    }

    private void put(String key, int value) {
        Settings.Secure.putInt(requireContext().getContentResolver(), key, value);
    }
}
