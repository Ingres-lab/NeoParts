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
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Neo > Security and app compatibility: SELinux, Play Integrity, Pixel features, dev options. */
public class NeoSecurityFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_SELINUX = "selinux_enforcing";

    static final String PROP_SELINUX = "persist.sys.neo.selinux";
    private static final String MODE_ENFORCING = "enforcing";
    private static final String MODE_PERMISSIVE = "permissive";

    /* The vendor helper applies the mode asynchronously; re-read it shortly after. */
    private static final long SELINUX_REFRESH_DELAY_MS = 500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SwitchPreferenceCompat mSelinux;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_security, rootKey);
        mSelinux = findPreference(KEY_SELINUX);
        mSelinux.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSelinux();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if ((Boolean) newValue) {
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

    private void setSelinuxMode(String mode) {
        SystemProperties.set(PROP_SELINUX, mode);
        mSelinux.setChecked(MODE_ENFORCING.equals(mode));
        mHandler.postDelayed(this::updateSelinux, SELINUX_REFRESH_DELAY_MS);
    }

    private void updateSelinux() {
        final boolean enforcing = isEnforcing();
        mSelinux.setChecked(enforcing);
        mSelinux.setSummary(enforcing
                ? R.string.selinux_summary_enforcing
                : R.string.selinux_summary_permissive);
    }

    /*
     * SELinux.isSELinuxEnforced() reads /sys/fs/selinux/enforce, which platform_app is
     * denied: it returns false, so the switch showed "permissive" while enforcing.
     * persist.sys.neo.selinux is what this page last asked the vendor helper to apply.
     */
    static boolean isEnforcing() {
        final String requested = SystemProperties.get(PROP_SELINUX, "");
        return MODE_ENFORCING.equals(requested)
                || (!MODE_PERMISSIVE.equals(requested) && SELinux.isSELinuxEnforced());
    }
}
