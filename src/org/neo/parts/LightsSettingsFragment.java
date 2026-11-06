/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Locale;

/**
 * Rear "X" lights (AW22xxx controller). The values go to persist.sys.neo.led.*
 * and vendor init applies them (device/xiaomi/ingres/neo/lights/neo_lights.rc).
 */
public class LightsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_EFFECT = "lights_effect";
    private static final String KEY_CUSTOM = "lights_custom_color";
    private static final String KEY_COLOR = "lights_color";
    private static final String KEY_SPEED = "lights_speed";
    private static final String KEY_BRIGHTNESS = "lights_brightness";
    private static final String KEY_BOOTGLOW = "lights_bootglow";

    private static final String PROP = "persist.sys.neo.led.";
    private static final String PROP_EFFECT = PROP + "effect";
    private static final String PROP_CFG = PROP + "cfg";
    private static final String PROP_COLOR = PROP + "color";   // RRGGBB, for this UI
    private static final String PROP_RGB = PROP + "rgb";       // BBRRGG, for the chip
    private static final String PROP_FRQ = PROP + "frq";
    private static final String PROP_IMAX = PROP + "imax";
    private static final String PROP_BOOTGLOW = PROP + "bootglow";
    private static final String PROP_APPLY = PROP + "apply";

    private static final String DEFAULT_COLOR = "00B4FF";  // Neo blue
    private static final int DEFAULT_SPEED = 1500;
    private static final int DEFAULT_IMAX = 3;

    // Per-event looks (calls, notifications, low battery, charging), applied by the lights HAL
    // (device/xiaomi/ingres/neo/lights/hal). Properties: persist.sys.neo.led.<event>.*
    private static final String[] EVENTS = {"call", "notif", "low", "charge"};
    private static final boolean[] EVENT_DEFAULT_ENABLED = {true, true, true, false};

    private final SwitchPreferenceCompat[] mEventEnable = new SwitchPreferenceCompat[EVENTS.length];
    private final ListPreference[] mEventEffect = new ListPreference[EVENTS.length];
    private final ListPreference[] mEventColor = new ListPreference[EVENTS.length];

    private ListPreference mEffect;
    private SwitchPreferenceCompat mCustom;
    private ListPreference mColor;
    private SeekBarPreference mSpeed;
    private SeekBarPreference mBrightness;
    private SwitchPreferenceCompat mBootGlow;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.lights_settings, rootKey);

        mEffect = findPreference(KEY_EFFECT);
        mCustom = findPreference(KEY_CUSTOM);
        mColor = findPreference(KEY_COLOR);
        mSpeed = findPreference(KEY_SPEED);
        mBrightness = findPreference(KEY_BRIGHTNESS);
        mBootGlow = findPreference(KEY_BOOTGLOW);

        for (Preference p : new Preference[] {mEffect, mCustom, mColor, mSpeed, mBrightness,
                mBootGlow}) {
            p.setOnPreferenceChangeListener(this);
        }

        for (int i = 0; i < EVENTS.length; i++) {
            mEventEnable[i] = findPreference("lights_" + EVENTS[i] + "_enable");
            mEventEffect[i] = findPreference("lights_" + EVENTS[i] + "_effect");
            mEventColor[i] = findPreference("lights_" + EVENTS[i] + "_color");
            mEventEnable[i].setOnPreferenceChangeListener(this);
            mEventEffect[i].setOnPreferenceChangeListener(this);
            mEventColor[i].setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mEffect.setValue(SystemProperties.get(PROP_EFFECT, "0"));
        mCustom.setChecked("2".equals(SystemProperties.get(PROP_CFG, "1")));
        mColor.setValue(SystemProperties.get(PROP_COLOR, DEFAULT_COLOR).toUpperCase(Locale.ROOT));
        mSpeed.setValue(SystemProperties.getInt(PROP_FRQ, DEFAULT_SPEED));
        mBrightness.setValue(SystemProperties.getInt(PROP_IMAX, DEFAULT_IMAX));
        mBootGlow.setChecked(SystemProperties.getBoolean(PROP_BOOTGLOW, true));
        loadEvents();
        updateSummaries();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        for (int i = 0; i < EVENTS.length; i++) {
            if (preference == mEventEnable[i]) {
                mEventEnable[i].setChecked((Boolean) newValue);
                SystemProperties.set(PROP + EVENTS[i] + ".enable", (Boolean) newValue ? "1" : "0");
            } else if (preference == mEventEffect[i]) {
                mEventEffect[i].setValue((String) newValue);
                SystemProperties.set(PROP + EVENTS[i] + ".effect",
                        "default".equals(newValue) ? "" : (String) newValue);
            } else if (preference == mEventColor[i]) {
                mEventColor[i].setValue((String) newValue);
                setEventColor(EVENTS[i], (String) newValue);
            } else {
                continue;
            }
            bump();
            updateSummaries();
            return false;  // values were set above
        }
        if (preference == mEffect) {
            mEffect.setValue((String) newValue);
        } else if (preference == mCustom) {
            mCustom.setChecked((Boolean) newValue);
        } else if (preference == mColor) {
            mColor.setValue((String) newValue);
        } else if (preference == mSpeed) {
            mSpeed.setValue((Integer) newValue);
        } else if (preference == mBrightness) {
            mBrightness.setValue((Integer) newValue);
        } else if (preference == mBootGlow) {
            SystemProperties.set(PROP_BOOTGLOW, (Boolean) newValue ? "1" : "0");
            return true;
        }
        apply();
        updateSummaries();
        return false;  // values were set above
    }

    private void apply() {
        final String color = mColor.getValue() == null ? DEFAULT_COLOR : mColor.getValue();
        SystemProperties.set(PROP_EFFECT, mEffect.getValue());
        SystemProperties.set(PROP_CFG, mCustom.isChecked() ? "2" : "1");
        SystemProperties.set(PROP_COLOR, color);
        SystemProperties.set(PROP_RGB, toChipOrder(color));
        SystemProperties.set(PROP_FRQ, Integer.toString(mSpeed.getValue()));
        SystemProperties.set(PROP_IMAX, Integer.toString(mBrightness.getValue()));
        // written last: the lights HAL applies everything when this changes
        bump();
    }

    private void bump() {
        SystemProperties.set(PROP_APPLY, Long.toString(System.currentTimeMillis() % 1000000000L));
    }

    private void loadEvents() {
        for (int i = 0; i < EVENTS.length; i++) {
            final String p = PROP + EVENTS[i] + ".";
            mEventEnable[i].setChecked(SystemProperties.getBoolean(p + "enable",
                    EVENT_DEFAULT_ENABLED[i]));
            final String effect = SystemProperties.get(p + "effect", "");
            mEventEffect[i].setValue(effect.isEmpty() ? "default" : effect);
            final String cfg = SystemProperties.get(p + "cfg", "");
            final String color = SystemProperties.get(p + "color", "");
            mEventColor[i].setValue("1".equals(cfg) ? "stock"
                    : color.isEmpty() ? "default" : color.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * "default" = use the colour and blink timing Android asks for, "stock" = the effect in its own
     * colours, anything else = RRGGBB (stored in the chip's BBRRGG order as well).
     */
    private static void setEventColor(String event, String value) {
        final String p = PROP + event + ".";
        if ("default".equals(value)) {
            SystemProperties.set(p + "cfg", "");
            SystemProperties.set(p + "color", "");
            SystemProperties.set(p + "rgb", "");
        } else if ("stock".equals(value)) {
            SystemProperties.set(p + "cfg", "1");
            SystemProperties.set(p + "color", "");
            SystemProperties.set(p + "rgb", "");
        } else {
            SystemProperties.set(p + "cfg", "2");
            SystemProperties.set(p + "color", value);
            SystemProperties.set(p + "rgb", toChipOrder(value));
        }
    }

    /** RRGGBB -> BBRRGG: the AW22xxx takes each zone's bytes as blue, red, green. */
    static String toChipOrder(String rrggbb) {
        final String c = rrggbb.toLowerCase(Locale.ROOT);
        if (c.length() != 6) {
            return "ff00b4";
        }
        return c.substring(4, 6) + c.substring(0, 2) + c.substring(2, 4);
    }

    private void updateSummaries() {
        final boolean off = "0".equals(mEffect.getValue());
        mCustom.setEnabled(!off);
        mColor.setEnabled(!off && mCustom.isChecked());
        mSpeed.setEnabled(!off && mCustom.isChecked());
        mBrightness.setEnabled(!off);
        mSpeed.setSummary(getString(R.string.lights_speed_summary, mSpeed.getValue()));
        for (int i = 0; i < EVENTS.length; i++) {
            final boolean on = mEventEnable[i].isChecked();
            mEventEffect[i].setEnabled(on);
            mEventColor[i].setEnabled(on && !"0".equals(mEventEffect[i].getValue()));
        }
    }
}
