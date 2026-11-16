/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Neo > Video and graphics: smooth motion video and the Vulkan driver. */
public class NeoGraphicsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_GPU = "gpu_turnip";

    static final String PROP_GPU = "persist.sys.neo.gpu";
    private static final String GPU_TURNIP = "turnip";
    private static final String GPU_ADRENO = "adreno";
    private static final String VULKAN_HAL = "/vendor/lib64/hw/vulkan.adreno.so";
    private static final String VULKAN_TURNIP = "/vendor/lib64/hw/vulkan.turnip.so";

    private SwitchPreferenceCompat mGpu;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_graphics, rootKey);
        mGpu = findPreference(KEY_GPU);
        mGpu.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGpu();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(Boolean) newValue) {
            setGpu(GPU_ADRENO);
            return false;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gpu_dialog_title)
                .setMessage(R.string.gpu_dialog_message)
                .setPositiveButton(R.string.gpu_dialog_confirm,
                        (dialog, which) -> setGpu(GPU_TURNIP))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return false;
    }

    private void setGpu(String driver) {
        SystemProperties.set(PROP_GPU, driver);
        updateGpu();
        if (isTurnipActive() != GPU_TURNIP.equals(driver)) {
            promptRestart();
        }
    }

    private void updateGpu() {
        if (stat(VULKAN_TURNIP) == null) {
            mGpu.setEnabled(false);
            mGpu.setChecked(false);
            mGpu.setSummary(R.string.gpu_missing);
            return;
        }
        final boolean selected = GPU_TURNIP.equals(SystemProperties.get(PROP_GPU, GPU_ADRENO));
        final boolean active = isTurnipActive();
        mGpu.setEnabled(true);
        mGpu.setChecked(selected);
        final String activeText = getString(
                active ? R.string.gpu_active_turnip : R.string.gpu_active_adreno);
        if (selected == active) {
            mGpu.setSummary(activeText);
        } else {
            mGpu.setSummary(getString(R.string.gpu_summary_format, activeText, getString(
                    selected ? R.string.gpu_pending_turnip : R.string.gpu_pending_adreno)));
        }
    }

    /* Turnip is active when the Vulkan HAL path is bind-mounted to the Turnip file. */
    static boolean isTurnipActive() {
        final StructStat hal = stat(VULKAN_HAL);
        final StructStat turnip = stat(VULKAN_TURNIP);
        return hal != null && turnip != null
                && hal.st_dev == turnip.st_dev && hal.st_ino == turnip.st_ino;
    }

    private static StructStat stat(String path) {
        try {
            return Os.stat(path);
        } catch (ErrnoException e) {
            return null;
        }
    }

    private void promptRestart() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restart_title)
                .setMessage(R.string.restart_message)
                .setPositiveButton(R.string.restart_now, (dialog, which) ->
                        requireContext().getSystemService(PowerManager.class).reboot(null))
                .setNegativeButton(R.string.restart_later, null)
                .show();
    }
}
