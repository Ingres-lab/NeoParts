/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.provider.DeviceConfig;
import android.util.Log;

import java.util.Map;

/**
 * Turns on the Android System Intelligence (ASI) At a Glance features that Google's servers only
 * enable for Pixels.
 *
 * The Google app sends At a Glance cards (weather, its own cards and settings) to ASI with
 * ContentCaptureManager.shareData(); ASI turns them into smartspace targets. Google's servers
 * never push the Pixel values of the flags below to this phone, so they are set here.
 *
 * Do not add Echo__smartspace_enable_private_weather: with it on, ASI drops the Google app's
 * weather cards and waits for a "private weather" source that does not exist here.
 *
 * Google Play services rewrites the whole device_personalization_services namespace when it
 * syncs, so the values are put back whenever the namespace changes.
 */
final class AtAGlanceFlags {
    private static final String TAG = "NeoAtAGlance";
    private static final String NAMESPACE = "device_personalization_services";
    private static final Map<String, String> FLAGS = Map.of(
            "EchoSmartspace__enable_agsa_settings_read", "true",
            "Echo__smartspace_enable_battery_notification_parser", "true",
            // "Flashlight is on" card with a tap-to-turn-off chip, shown only while the flashlight
            // is on. (enable_light_predictor would also suggest the flashlight whenever the room
            // is dark.)
            "Echo__smartspace_enable_light_off_predictor", "true",
            "Echo__smartspace_use_flashlight_action_chip", "true");
    // Now Playing is left alone on purpose: it defaults to Pixel's always-on audio DSP model,
    // which this phone lacks, and the normal-mic fallback costs battery.

    static void init(Context context) {
        apply();
        DeviceConfig.addOnPropertiesChangedListener(NAMESPACE, context.getMainExecutor(),
                properties -> apply());
    }

    private static void apply() {
        for (Map.Entry<String, String> flag : FLAGS.entrySet()) {
            try {
                if (!flag.getValue().equals(DeviceConfig.getProperty(NAMESPACE, flag.getKey()))) {
                    DeviceConfig.setProperty(NAMESPACE, flag.getKey(), flag.getValue(), false);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "could not set " + NAMESPACE + "/" + flag.getKey(), e);
            }
        }
    }
}
