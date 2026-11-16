/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.hardware.display.ColorDisplayManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Neo > Display: always-on display, Monet engine and colour accuracy. */
public class NeoDisplayFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_FLAT = "color_accuracy";

    /* Xiaomi flat (color-accurate) panel mode; init.neo.rc writes mi_disp's disp_param */
    static final String PROP_FLAT = "persist.sys.neo.flat";
    /* Display color mode (ColorDisplayManager vendor range) used while Color accuracy is on */
    private static final int COLOR_MODE_SRGB = 267;
    /* Mode to go back to when the switch is turned off; the user's previous choice */
    static final String PROP_FLAT_PREV_MODE = "persist.sys.neo.flat_prev_mode";

    private SwitchPreferenceCompat mFlat;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_display, rootKey);
        mFlat = findPreference(KEY_FLAT);
        mFlat.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mFlat.setChecked(SystemProperties.getBoolean(PROP_FLAT, false));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean enabled = (Boolean) newValue;
        SystemProperties.set(PROP_FLAT, enabled ? "1" : "0");
        applyColorMode(enabled);
        return true;
    }

    /*
     * Panel flat mode (init.neo.rc) alone is a subtle DDIC tweak. Also move the display
     * color mode to sRGB so colors are accurate, and restore the previous mode afterwards.
     */
    private void applyColorMode(boolean accurate) {
        final ColorDisplayManager cdm =
                requireContext().getSystemService(ColorDisplayManager.class);
        if (cdm == null) {
            return;
        }
        try {
            if (accurate) {
                final int current = cdm.getColorMode();
                if (current != COLOR_MODE_SRGB) {
                    SystemProperties.set(PROP_FLAT_PREV_MODE, Integer.toString(current));
                }
                cdm.setColorMode(COLOR_MODE_SRGB);
            } else {
                final int prev = SystemProperties.getInt(PROP_FLAT_PREV_MODE, -1);
                if (prev >= 0) {
                    cdm.setColorMode(prev);
                }
            }
        } catch (RuntimeException e) {
            Log.w("NeoParts", "setColorMode failed", e);
        }
    }
}
