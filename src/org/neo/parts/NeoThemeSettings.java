/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Theme style and seed colour, stored where the wallpaper picker stores them:
 * Settings.Secure theme_customization_overlay_packages (a JSON object). SystemUI's
 * ThemeOverlayController observes it and regenerates the Monet palettes.
 */
final class NeoThemeSettings {
    private static final String TAG = "NeoThemeSettings";

    private static final String KEY = Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES;
    private static final String STYLE = "android.theme.customization.theme_style";
    private static final String SOURCE = "android.theme.customization.color_source";
    private static final String SYSTEM_PALETTE = "android.theme.customization.system_palette";
    private static final String ACCENT_COLOR = "android.theme.customization.accent_color";
    private static final String COLOR_INDEX = "android.theme.customization.color_index";
    private static final String COLOR_BOTH = "android.theme.customization.color_both";
    private static final String TIMESTAMP = "_applied_timestamp";

    private static final String SOURCE_WALLPAPER = "home_wallpaper";
    private static final String SOURCE_PRESET = "preset";

    static final String DEFAULT_STYLE = "TONAL_SPOT";

    private NeoThemeSettings() {}

    static String getStyle(Context context) {
        return read(context).optString(STYLE, DEFAULT_STYLE);
    }

    static void setStyle(Context context, String style) {
        final JSONObject json = read(context);
        put(json, STYLE, style);
        write(context, json);
    }

    /** The custom seed colour, or 0 while the colours come from the wallpaper. */
    static int getSeedColor(Context context) {
        final JSONObject json = read(context);
        if (!SOURCE_PRESET.equals(json.optString(SOURCE))) {
            return 0;
        }
        final String value = json.optString(SYSTEM_PALETTE, "");
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Color.parseColor(value.startsWith("#") ? value : "#" + value);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    static void setSeedColor(Context context, int color) {
        final JSONObject json = read(context);
        final String hex = String.format(Locale.US, "%08X", color | 0xFF000000);
        put(json, SOURCE, SOURCE_PRESET);
        put(json, SYSTEM_PALETTE, hex);
        put(json, ACCENT_COLOR, hex);
        put(json, COLOR_BOTH, "1");
        json.remove(COLOR_INDEX);
        write(context, json);
    }

    static void useWallpaperColors(Context context) {
        final JSONObject json = read(context);
        put(json, SOURCE, SOURCE_WALLPAPER);
        put(json, COLOR_BOTH, "1");
        json.remove(SYSTEM_PALETTE);
        json.remove(ACCENT_COLOR);
        json.remove(COLOR_INDEX);
        write(context, json);
    }

    private static JSONObject read(Context context) {
        final String value = Settings.Secure.getString(context.getContentResolver(), KEY);
        if (value != null && !value.isEmpty()) {
            try {
                return new JSONObject(value);
            } catch (JSONException e) {
                Log.w(TAG, "unreadable " + KEY + ", starting over", e);
            }
        }
        return new JSONObject();
    }

    private static void write(Context context, JSONObject json) {
        // a new timestamp makes the value differ, so SystemUI re-applies even an unchanged choice
        put(json, TIMESTAMP, System.currentTimeMillis());
        Settings.Secure.putString(context.getContentResolver(), KEY, json.toString());
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            // only thrown for non-finite numbers
        }
    }
}
