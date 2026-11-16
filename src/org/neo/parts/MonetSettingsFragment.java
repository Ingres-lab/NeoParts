/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SeekBarPreference;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/**
 * Neo Monet engine.
 *
 * - Theme style and seed colour: Settings.Secure theme_customization_overlay_packages, the
 *   standard place the wallpaper picker uses (NeoThemeSettings).
 * - Individual palette colours and fine tuning: Settings.Secure neo_monet_* integers, read by
 *   SystemUI's ThemeOverlayController and applied by NeoMonetTuning in frameworks/libs.
 *
 * SystemUI regenerates the theme on every change; this activity is then recreated with the new
 * colours, which refreshes the preview and the automatic swatches.
 */
public class MonetSettingsFragment extends SettingsBasePreferenceFragment
        implements SelectorWithWidgetPreference.OnClickListener {

    // Keep in sync with com.android.systemui.monet.NeoMonetTuning
    private static final String KEY_ACCENT = "neo_monet_accent_chroma";
    private static final String KEY_NEUTRAL = "neo_monet_neutral_chroma";
    private static final String KEY_HUE = "neo_monet_hue_shift";
    private static final String[] KEY_COLORS = {
            "neo_monet_color_primary",
            "neo_monet_color_secondary",
            "neo_monet_color_tertiary",
            "neo_monet_color_neutral",
            "neo_monet_color_neutral_variant",
    };
    private static final String[] COLOR_PREFS = {
            "color_primary", "color_secondary", "color_tertiary", "color_neutral",
            "color_neutral_variant",
    };
    private static final int[] COLOR_TITLES = {
            R.string.monet_color_primary_title, R.string.monet_color_secondary_title,
            R.string.monet_color_tertiary_title, R.string.monet_color_neutral_title,
            R.string.monet_color_neutral_variant_title,
    };
    private static final int[] COLOR_DESCS = {
            R.string.monet_color_primary_desc, R.string.monet_color_secondary_desc,
            R.string.monet_color_tertiary_desc, R.string.monet_color_neutral_desc,
            R.string.monet_color_neutral_variant_desc,
    };

    private static final String STYLE_PREFIX = "style_";
    private static final String KEY_SOURCE_WALLPAPER = "source_wallpaper";
    private static final String KEY_SOURCE_CUSTOM = "source_custom";

    private PreferenceCategory mStyles;
    private SelectorWithWidgetPreference mSourceWallpaper;
    private SelectorWithWidgetPreference mSourceCustom;
    private ColorPreference mSeed;
    private final ColorPreference[] mColors = new ColorPreference[KEY_COLORS.length];
    private SeekBarPreference mAccent;
    private SeekBarPreference mNeutral;
    private SeekBarPreference mHue;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.monet_settings, rootKey);

        mStyles = findPreference("monet_category_style");
        for (int i = 0; i < mStyles.getPreferenceCount(); i++) {
            ((SelectorWithWidgetPreference) mStyles.getPreference(i)).setOnClickListener(this);
        }

        mSourceWallpaper = findPreference(KEY_SOURCE_WALLPAPER);
        mSourceWallpaper.setOnClickListener(this);
        mSourceCustom = findPreference(KEY_SOURCE_CUSTOM);
        mSourceCustom.setOnClickListener(this);
        mSeed = findPreference("seed_color");
        mSeed.setOnPreferenceClickListener(p -> {
            pickSeed();
            return true;
        });

        for (int i = 0; i < mColors.length; i++) {
            final int index = i;
            mColors[i] = findPreference(COLOR_PREFS[i]);
            mColors[i].setOnPreferenceClickListener(p -> {
                ColorPickerDialog.show(requireContext(), getString(COLOR_TITLES[index]),
                        mColors[index].getColor(), true, color -> {
                            put(KEY_COLORS[index], color);
                            updateColors();
                        });
                return true;
            });
        }

        mAccent = bind("monet_accent_chroma", KEY_ACCENT, 100);
        mNeutral = bind("monet_neutral_chroma", KEY_NEUTRAL, 100);
        mHue = bind("monet_hue_shift", KEY_HUE, 0);

        findPreference("monet_reset").setOnPreferenceClickListener(p -> {
            resetAll();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStyle();
        updateSource();
        updateColors();
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference pref) {
        final String key = pref.getKey();
        if (key.startsWith(STYLE_PREFIX)) {
            NeoThemeSettings.setStyle(requireContext(), key.substring(STYLE_PREFIX.length()));
            updateStyle();
        } else if (KEY_SOURCE_WALLPAPER.equals(key)) {
            NeoThemeSettings.useWallpaperColors(requireContext());
            updateSource();
        } else if (KEY_SOURCE_CUSTOM.equals(key)) {
            pickSeed();
        }
    }

    private void pickSeed() {
        final int current = NeoThemeSettings.getSeedColor(requireContext());
        ColorPickerDialog.show(requireContext(), getString(R.string.monet_seed_title),
                current != 0 ? current : MonetPreviewView.representative(requireContext(), 0),
                false, color -> {
                    NeoThemeSettings.setSeedColor(requireContext(), color);
                    updateSource();
                });
    }

    private void updateStyle() {
        final String style = NeoThemeSettings.getStyle(requireContext());
        for (int i = 0; i < mStyles.getPreferenceCount(); i++) {
            final SelectorWithWidgetPreference p =
                    (SelectorWithWidgetPreference) mStyles.getPreference(i);
            p.setChecked(p.getKey().equals(STYLE_PREFIX + style));
        }
    }

    private void updateSource() {
        final int seed = NeoThemeSettings.getSeedColor(requireContext());
        mSourceWallpaper.setChecked(seed == 0);
        mSourceCustom.setChecked(seed != 0);
        mSeed.setColor(seed, MonetPreviewView.representative(requireContext(), 0));
        mSeed.setSummary(seed != 0 ? ColorPickerDialog.hex(seed)
                : getString(R.string.monet_seed_summary_wallpaper));
    }

    private void updateColors() {
        for (int i = 0; i < mColors.length; i++) {
            final int color = get(KEY_COLORS[i], 0);
            mColors[i].setColor(color, MonetPreviewView.representative(requireContext(), i));
            mColors[i].setSummary(color == 0
                    ? getString(R.string.monet_color_summary_automatic, getString(COLOR_DESCS[i]))
                    : getString(R.string.monet_color_summary_custom, ColorPickerDialog.hex(color),
                            getString(COLOR_DESCS[i])));
        }
    }

    private void resetAll() {
        for (String key : KEY_COLORS) {
            put(key, 0);
        }
        put(KEY_ACCENT, 100);
        put(KEY_NEUTRAL, 100);
        put(KEY_HUE, 0);
        mAccent.setValue(100);
        mNeutral.setValue(100);
        mHue.setValue(0);
        NeoThemeSettings.useWallpaperColors(requireContext());
        NeoThemeSettings.setStyle(requireContext(), NeoThemeSettings.DEFAULT_STYLE);
        updateStyle();
        updateSource();
        updateColors();
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
