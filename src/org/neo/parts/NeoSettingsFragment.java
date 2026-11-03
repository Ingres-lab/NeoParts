/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SELinux;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class NeoSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_BYPASS = "bypass_charging";
    private static final String KEY_SELINUX = "selinux_enforcing";

    static final String PROP_BYPASS = "persist.vendor.neo.bypass";
    static final String PROP_SELINUX = "persist.vendor.neo.selinux";

    private static final String MODE_ENFORCING = "enforcing";
    private static final String MODE_PERMISSIVE = "permissive";

    /* The vendor helper applies the mode asynchronously; re-read it shortly after. */
    private static final long SELINUX_REFRESH_DELAY_MS = 500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SwitchPreferenceCompat mBypass;
    private SwitchPreferenceCompat mSelinux;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_settings, rootKey);

        mBypass = findPreference(KEY_BYPASS);
        mBypass.setOnPreferenceChangeListener(this);

        mSelinux = findPreference(KEY_SELINUX);
        mSelinux.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBypass.setChecked(SystemProperties.getBoolean(PROP_BYPASS, false));
        updateSelinux();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean enabled = (Boolean) newValue;
        if (preference == mBypass) {
            SystemProperties.set(PROP_BYPASS, enabled ? "1" : "0");
            return true;
        }
        if (preference == mSelinux) {
            if (enabled) {
                setSelinuxMode(MODE_ENFORCING);
                return true;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.selinux_dialog_title)
                    .setMessage(R.string.selinux_dialog_message)
                    .setPositiveButton(R.string.selinux_dialog_confirm,
                            (dialog, which) -> setSelinuxMode(MODE_PERMISSIVE))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return false;
        }
        return false;
    }

    private void setSelinuxMode(String mode) {
        SystemProperties.set(PROP_SELINUX, mode);
        mSelinux.setChecked(MODE_ENFORCING.equals(mode));
        mHandler.postDelayed(this::updateSelinux, SELINUX_REFRESH_DELAY_MS);
    }

    private void updateSelinux() {
        final boolean enforcing = SELinux.isSELinuxEnforced();
        mSelinux.setChecked(enforcing);
        mSelinux.setSummary(enforcing
                ? R.string.selinux_summary_enforcing
                : R.string.selinux_summary_permissive);
    }
}
