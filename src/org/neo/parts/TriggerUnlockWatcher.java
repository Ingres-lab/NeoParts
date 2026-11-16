/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;

/**
 * Watches sys.neo.trigger.pop, which neo_triggerd sets whenever a shoulder trigger is moved
 * between its two positions (locked = retracted, unlocked = popped up), and tells the overlay
 * service. SystemProperties change callbacks fire for every property, so only a real change of
 * the mask is acted on.
 */
final class TriggerUnlockWatcher {

    private static int sLast;

    private TriggerUnlockWatcher() {}

    static void init(Context context) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        sLast = TriggerConfig.unlockedMask();
        SystemProperties.addChangeCallback(() -> main.post(() -> {
            final int now = TriggerConfig.unlockedMask();
            if (now == sLast) {
                return;
            }
            final int unlockedNow = now & ~sLast;
            sLast = now;
            if (now == 0) {
                TriggerOverlayService.triggersLocked(app);
            } else if (unlockedNow != 0) {
                TriggerOverlayService.triggerUnlocked(app);
            }
        }));
    }
}
