/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SELinux;
import android.os.SystemProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Locale;

public class NeoSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_BYPASS = "bypass_charging";
    private static final String KEY_BATTERY = "battery_monitor";
    private static final String KEY_GPU = "gpu_turnip";
    private static final String KEY_SELINUX = "selinux_enforcing";

    static final String PROP_BYPASS = "persist.sys.neo.bypass";
    static final String PROP_GPU = "persist.sys.neo.gpu";
    static final String PROP_SELINUX = "persist.sys.neo.selinux";

    private static final String MODE_ENFORCING = "enforcing";
    private static final String MODE_PERMISSIVE = "permissive";

    private static final String GPU_TURNIP = "turnip";
    private static final String GPU_ADRENO = "adreno";
    private static final String VULKAN_HAL = "/vendor/lib64/hw/vulkan.adreno.so";
    private static final String VULKAN_TURNIP = "/vendor/lib64/hw/vulkan.turnip.so";

    /* The vendor helper applies the mode asynchronously; re-read it shortly after. */
    private static final long SELINUX_REFRESH_DELAY_MS = 500;

    /* Battery monitor refresh while the page is visible. */
    private static final long BATTERY_REFRESH_MS = 1000;

    /*
     * The ingres fuel gauge reports current_now as flow out of the battery:
     * negative while charging, positive while discharging. The health HAL and
     * BatteryManager pass it through unchanged, so flip it to show flow into
     * the battery (charging = positive).
     */
    private static final int CURRENT_INTO_BATTERY_SIGN = -1;

    /* Below this the battery counts as idle (sensor noise, firmware hold). */
    private static final int IDLE_THRESHOLD_MA = 100;

    /* The firmware only holds the battery at or above this level in bypass. */
    private static final int BYPASS_HOLD_LEVEL = 80;

    /*
     * The charger needs a moment to switch after the toggle, and the gauge
     * reports short top-up pulses even while bypass holds. Only call bypass
     * ineffective after this many consecutive charging readings (1 s apart).
     */
    private static final int BYPASS_NOT_HOLDING_SAMPLES = 10;

    private int mChargingAboveHoldSamples;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mBatteryUpdater = new Runnable() {
        @Override
        public void run() {
            updateBattery();
            mHandler.postDelayed(this, BATTERY_REFRESH_MS);
        }
    };

    private SwitchPreferenceCompat mBypass;
    private Preference mBattery;
    private SwitchPreferenceCompat mGpu;
    private SwitchPreferenceCompat mSelinux;

    private BatteryManager mBatteryManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_settings, rootKey);

        mBatteryManager = requireContext().getSystemService(BatteryManager.class);

        mBypass = findPreference(KEY_BYPASS);
        mBypass.setOnPreferenceChangeListener(this);

        mBattery = findPreference(KEY_BATTERY);

        mGpu = findPreference(KEY_GPU);
        mGpu.setOnPreferenceChangeListener(this);

        mSelinux = findPreference(KEY_SELINUX);
        mSelinux.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBypass.setChecked(SystemProperties.getBoolean(PROP_BYPASS, false));
        updateGpu();
        updateSelinux();
        mHandler.post(mBatteryUpdater);
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
            mChargingAboveHoldSamples = 0;
            mBypass.setChecked(enabled);
            updateBattery();
            return true;
        }
        if (preference == mGpu) {
            if (!enabled) {
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

    /* ---------------- Battery monitor ---------------- */

    private void updateBattery() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final int currentUa =
                mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        final Intent battery = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (currentUa == Integer.MIN_VALUE || battery == null) {
            mBattery.setTitle(R.string.battery_monitor_title);
            mBattery.setSummary(R.string.battery_monitor_unavailable);
            return;
        }

        final int intoBatteryMa = CURRENT_INTO_BATTERY_SIGN * currentUa / 1000;
        final int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final boolean plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

        final String watts = String.format(Locale.getDefault(), "%.1f",
                Math.abs(intoBatteryMa) * (double) voltageMv / 1_000_000d);
        final String flow;
        if (intoBatteryMa > IDLE_THRESHOLD_MA) {
            flow = getString(R.string.battery_flow_charging, intoBatteryMa, watts);
        } else if (intoBatteryMa < -IDLE_THRESHOLD_MA) {
            flow = getString(R.string.battery_flow_discharging, -intoBatteryMa, watts);
        } else {
            flow = getString(R.string.battery_flow_idle, intoBatteryMa);
        }
        final String details = getString(R.string.battery_details,
                String.format(Locale.getDefault(), "%.2f", voltageMv / 1000d), level,
                getString(plugged ? R.string.battery_plugged : R.string.battery_unplugged));

        mBattery.setTitle(flow);
        final String bypassState = mBypass.isChecked()
                ? bypassState(plugged, intoBatteryMa, level) : null;
        mBattery.setSummary(bypassState == null ? details : details + "\n" + bypassState);
    }

    /* How the measured current compares with what bypass should be doing. */
    private String bypassState(boolean plugged, int intoBatteryMa, int level) {
        if (!plugged) {
            return getString(R.string.bypass_state_unplugged);
        }
        final boolean chargingAboveHold =
                intoBatteryMa > IDLE_THRESHOLD_MA && level >= BYPASS_HOLD_LEVEL;
        mChargingAboveHoldSamples = chargingAboveHold ? mChargingAboveHoldSamples + 1 : 0;
        if (intoBatteryMa > IDLE_THRESHOLD_MA) {
            if (level < BYPASS_HOLD_LEVEL) {
                return getString(R.string.bypass_state_filling);
            }
            return getString(mChargingAboveHoldSamples >= BYPASS_NOT_HOLDING_SAMPLES
                    ? R.string.bypass_state_not_holding
                    : R.string.bypass_state_switching);
        }
        if (intoBatteryMa < -IDLE_THRESHOLD_MA) {
            return getString(R.string.bypass_state_draining);
        }
        return getString(R.string.bypass_state_working);
    }

    /* ---------------- GPU driver ---------------- */

    private void setGpu(String driver) {
        SystemProperties.set(PROP_GPU, driver);
        updateGpu();
        if (isTurnipActive() != GPU_TURNIP.equals(driver)) {
            promptRestart();
        }
    }

    private void updateGpu() {
        final StructStat turnip = stat(VULKAN_TURNIP);
        if (turnip == null) {
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
    private static boolean isTurnipActive() {
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

    /* ---------------- SELinux ---------------- */

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
