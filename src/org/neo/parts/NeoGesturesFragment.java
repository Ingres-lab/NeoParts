/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Neo > Gestures. */
public class NeoGesturesFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_THREE_FINGER = "three_finger_screenshot";

    private SwitchPreferenceCompat mThreeFinger;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_gestures, rootKey);
        mThreeFinger = findPreference(KEY_THREE_FINGER);
        mThreeFinger.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mThreeFinger.setChecked(ThreeFingerScreenshot.isEnabled(requireContext()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ThreeFingerScreenshot.setEnabled(requireContext(), (Boolean) newValue);
        return true;
    }
}
