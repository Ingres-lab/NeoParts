/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.provider.DeviceConfig;
import android.util.Log;

/**
 * Turns off the Pixel Launcher's "search haptic hint" for Circle to Search.
 *
 * While the nav handle is held, the launcher plays a ~480 ms swell of 50 LOW_TICK primitives
 * (0 -> 71 % strength). It is tuned for Pixel actuators; on this phone's haptics driver it comes
 * out as a rough buzz. With the hint off, only the launcher's single commit TICK plays when
 * Circle to Search opens. The launcher reads the flag from DeviceConfig namespace "launcher" and
 * reacts to changes live. Google can push launcher flags from the server, so the value is put
 * back whenever the namespace changes.
 */
final class LauncherHaptics {
    private static final String TAG = "NeoLauncherHaptics";
    private static final String NAMESPACE = "launcher";
    private static final String KEY_HINT = "ENABLE_SEARCH_HAPTIC_HINT";

    static void init(Context context) {
        apply();
        DeviceConfig.addOnPropertiesChangedListener(NAMESPACE, context.getMainExecutor(),
                properties -> {
                    if (properties.getKeyset().contains(KEY_HINT)) apply();
                });
    }

    private static void apply() {
        try {
            if (!"false".equals(DeviceConfig.getProperty(NAMESPACE, KEY_HINT))) {
                DeviceConfig.setProperty(NAMESPACE, KEY_HINT, "false", false);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not set " + NAMESPACE + "/" + KEY_HINT, e);
        }
    }
}
