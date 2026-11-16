/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;

/**
 * Watches sys.neo.trigger.pop, which neo_triggerd sets whenever a shoulder trigger moves between
 * its two positions (locked = retracted, unlocked = popped up), and tells the overlay service.
 *
 * It polls rather than using SystemProperties.addChangeCallback: that callback only fires for
 * property changes made by Java SystemProperties.set() in the SAME process (it runs the in-process
 * sysprop-change listeners). neo_triggerd is a native daemon setting the property with
 * libbase SetProperty, which never notifies this process, so the callback would never fire. The
 * daemon polls the property-area serial for the same reason.
 *
 * The poll is cheap (a shared-memory read): fast while the screen is interactive (a trigger only
 * matters while you are looking at the screen), slow otherwise so it never holds the CPU awake.
 */
final class TriggerUnlockWatcher {

    private static final long INTERACTIVE_INTERVAL_MS = 200;
    private static final long IDLE_INTERVAL_MS = 2000;

    private static int sLast;

    private TriggerUnlockWatcher() {}

    static void init(Context context) {
        final Context app = context.getApplicationContext();
        final PowerManager power = app.getSystemService(PowerManager.class);
        final HandlerThread thread = new HandlerThread("NeoTriggerPoll");
        thread.start();
        final Handler handler = new Handler(thread.getLooper());
        sLast = TriggerConfig.unlockedMask();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long next = IDLE_INTERVAL_MS;
                if (TriggerConfig.isEnabled()) {
                    poll(app);
                    next = power.isInteractive() ? INTERACTIVE_INTERVAL_MS : IDLE_INTERVAL_MS;
                }
                handler.postDelayed(this, next);
            }
        });
    }

    private static void poll(Context app) {
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
    }
}
