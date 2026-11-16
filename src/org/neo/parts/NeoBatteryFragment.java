/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.Locale;

/** Neo > Battery and charging: live battery current and bypass charging. */
public class NeoBatteryFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_BYPASS = "bypass_charging";
    private static final String KEY_BATTERY = "battery_monitor";

    static final String PROP_BYPASS = "persist.sys.neo.bypass";

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
    private BatteryManager mBatteryManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.neo_battery, rootKey);
        mBatteryManager = requireContext().getSystemService(BatteryManager.class);
        mBypass = findPreference(KEY_BYPASS);
        mBypass.setOnPreferenceChangeListener(this);
        mBattery = findPreference(KEY_BATTERY);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBypass.setChecked(SystemProperties.getBoolean(PROP_BYPASS, false));
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
        SystemProperties.set(PROP_BYPASS, enabled ? "1" : "0");
        mChargingAboveHoldSamples = 0;
        mBypass.setChecked(enabled);
        updateBattery();
        return true;
    }

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
}
