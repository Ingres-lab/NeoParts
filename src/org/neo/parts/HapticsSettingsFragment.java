/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Haptics for the AW86927 motor. The master strength is read by the Neo vibrator HAL on every
 * vibration (persist.sys.neo.haptic.strength, percent), so a change applies to the next one.
 * The effect and primitive rows play through the normal vibrator service, i.e. exactly what an
 * app gets, to judge the waveforms by feel. The switches are the stock Android settings.
 */
public class HapticsSettingsFragment extends PreferenceFragmentCompat {

    static final String PROP_STRENGTH = "persist.sys.neo.haptic.strength";

    private static final String KEY_STRENGTH = "haptics_strength";
    private static final String KEY_RESET = "haptics_reset";
    private static final String KEY_EFFECTS = "haptics_effects";
    private static final String KEY_PRIMITIVES = "haptics_primitives";
    private static final String KEY_TOUCH = "haptics_touch";
    private static final String KEY_KEYBOARD = "haptics_keyboard";
    private static final String KEY_CHARGING = "haptics_charging";
    private static final String KEY_RINGING = "haptics_ringing";

    private static final int STRENGTH_DEFAULT = 100;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Vibrator mVibrator;
    private SeekBarPreference mStrength;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.haptics_settings, rootKey);
        mVibrator = requireContext().getSystemService(Vibrator.class);

        mStrength = findPreference(KEY_STRENGTH);
        mStrength.setValue(SystemProperties.getInt(PROP_STRENGTH, STRENGTH_DEFAULT));
        mStrength.setOnPreferenceChangeListener((p, v) -> {
            final int pct = Math.round(((Integer) v) / 5f) * 5;
            SystemProperties.set(PROP_STRENGTH, Integer.toString(pct));
            // let the user feel the new strength once the slider rests
            mHandler.removeCallbacks(mPreview);
            mHandler.postDelayed(mPreview, 350);
            return true;
        });
        findPreference(KEY_RESET).setOnPreferenceClickListener(p -> {
            SystemProperties.set(PROP_STRENGTH, Integer.toString(STRENGTH_DEFAULT));
            mStrength.setValue(STRENGTH_DEFAULT);
            mHandler.postDelayed(mPreview, 150);
            return true;
        });

        addEffect(KEY_EFFECTS, R.string.haptics_fx_click, VibrationEffect.EFFECT_CLICK);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_double_click, VibrationEffect.EFFECT_DOUBLE_CLICK);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_tick, VibrationEffect.EFFECT_TICK);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_heavy_click, VibrationEffect.EFFECT_HEAVY_CLICK);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_thud, VibrationEffect.EFFECT_THUD);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_pop, VibrationEffect.EFFECT_POP);
        addEffect(KEY_EFFECTS, R.string.haptics_fx_texture, VibrationEffect.EFFECT_TEXTURE_TICK);
        addAction(KEY_EFFECTS, R.string.haptics_fx_buzz,
                () -> VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));

        addPrimitive(R.string.haptics_pr_click, VibrationEffect.Composition.PRIMITIVE_CLICK);
        addPrimitive(R.string.haptics_pr_thud, VibrationEffect.Composition.PRIMITIVE_THUD);
        addPrimitive(R.string.haptics_pr_spin, VibrationEffect.Composition.PRIMITIVE_SPIN);
        addPrimitive(R.string.haptics_pr_quick_rise, VibrationEffect.Composition.PRIMITIVE_QUICK_RISE);
        addPrimitive(R.string.haptics_pr_slow_rise, VibrationEffect.Composition.PRIMITIVE_SLOW_RISE);
        addPrimitive(R.string.haptics_pr_quick_fall, VibrationEffect.Composition.PRIMITIVE_QUICK_FALL);
        addPrimitive(R.string.haptics_pr_tick, VibrationEffect.Composition.PRIMITIVE_TICK);
        addPrimitive(R.string.haptics_pr_low_tick, VibrationEffect.Composition.PRIMITIVE_LOW_TICK);

        bindSetting(KEY_TOUCH, false, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
        bindSetting(KEY_KEYBOARD, false, Settings.System.KEYBOARD_VIBRATION_ENABLED, 1);
        bindSetting(KEY_RINGING, false, Settings.System.VIBRATE_WHEN_RINGING, 0);
        bindSetting(KEY_CHARGING, true, "charging_vibration_enabled", 1);
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mPreview);
        mVibrator.cancel();
        super.onDestroyView();
    }

    private final Runnable mPreview = () -> play(VibrationEffect.createPredefined(
            VibrationEffect.EFFECT_CLICK));

    /** Plays as an alarm-class vibration, which the touch-feedback switch never suppresses. */
    private void play(VibrationEffect effect) {
        mVibrator.cancel();
        mVibrator.vibrate(effect, VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_ALARM));
    }

    private void addEffect(String category, int title, int effect) {
        addAction(category, title, () -> VibrationEffect.createPredefined(effect));
    }

    private void addPrimitive(int title, int primitive) {
        addAction(KEY_PRIMITIVES, title, () -> VibrationEffect.startComposition()
                .addPrimitive(primitive, 1.0f).compose());
    }

    private interface EffectSource {
        VibrationEffect make();
    }

    private void addAction(String category, int title, EffectSource source) {
        final PreferenceCategory group = findPreference(category);
        final Preference row = new Preference(requireContext());
        row.setTitle(title);
        row.setPersistent(false);
        row.setIconSpaceReserved(false);
        row.setOnPreferenceClickListener(p -> {
            play(source.make());
            return true;
        });
        group.addPreference(row);
    }

    private void bindSetting(String key, boolean secure, String name, int def) {
        final SwitchPreferenceCompat sw = findPreference(key);
        final ContentResolver cr = requireContext().getContentResolver();
        sw.setChecked((secure ? Settings.Secure.getInt(cr, name, def)
                : Settings.System.getInt(cr, name, def)) != 0);
        sw.setOnPreferenceChangeListener((p, v) -> {
            final int value = (Boolean) v ? 1 : 0;
            if (secure) {
                Settings.Secure.putInt(cr, name, value);
            } else {
                Settings.System.putInt(cr, name, value);
            }
            return true;
        });
    }
}
