/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Publishes "a call is ringing" as sys.neo.led.call for the lights HAL. A plain incoming-call
 * notification reaches the HAL on the ordinary notification light with nothing marking it as a
 * call, so the call colour and effect from the Lights page need this.
 */
public class NeoApplication extends Application {
    private static final String TAG = "NeoParts";
    private static final String PROP_CALL = "sys.neo.led.call";

    private final TelephonyCallback mCallback = new CallListener();

    @Override
    public void onCreate() {
        super.onCreate();
        register();
        AodController.init(this);
        TriggerOverlayService.update(this);
        TriggerUnlockWatcher.init(this);
        ThreeFingerScreenshot.init(this);
        GameModeController.init(this);
        LauncherHaptics.init(this);
        AtAGlanceFlags.init(this);
    }

    private void register() {
        try {
            final TelephonyManager tm = getSystemService(TelephonyManager.class);
            tm.registerTelephonyCallback(getMainExecutor(), mCallback);
        } catch (RuntimeException e) {
            // telephony is not up yet this early in boot
            Log.w(TAG, "call state listener not registered, retrying", e);
            new Handler(Looper.getMainLooper()).postDelayed(this::register, 5000);
        }
    }

    private static class CallListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            SystemProperties.set(PROP_CALL,
                    state == TelephonyManager.CALL_STATE_RINGING ? "ringing" : "");
        }
    }
}
