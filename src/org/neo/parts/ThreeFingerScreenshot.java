/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.android.internal.util.ScreenshotHelper;

/**
 * Three-finger swipe down takes a screenshot.
 *
 * A gesture input monitor sees every touch on the default display without delaying it. When
 * three fingers come down close together and all travel down far enough, the monitor pilfers the
 * pointers (the app below gets ACTION_CANCEL, so it stops scrolling) and asks SystemUI for a normal
 * screenshot, with its preview and editor. Toggled by Settings.Secure neo_three_finger_screenshot.
 */
final class ThreeFingerScreenshot {
    private static final String TAG = "NeoThreeFinger";
    static final String SETTING = "neo_three_finger_screenshot";

    /** All three fingers must land within this time of the first one. */
    private static final long MAX_FINGER_GAP_MS = 400;
    /** Downward travel each finger needs, in dp. */
    private static final float SWIPE_DP = 70f;
    /** WindowManager.ScreenshotSource.SCREENSHOT_VENDOR_GESTURE (package-private there). */
    private static final int SCREENSHOT_VENDOR_GESTURE = 6;

    private static ThreeFingerScreenshot sInstance;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ScreenshotHelper mScreenshotHelper;
    private final float mSwipePx;

    private HandlerThread mThread;
    private InputMonitor mMonitor;
    private Receiver mReceiver;

    static void init(Context context) {
        if (sInstance == null) {
            sInstance = new ThreeFingerScreenshot(context.getApplicationContext());
        }
    }

    private ThreeFingerScreenshot(Context context) {
        mContext = context;
        mScreenshotHelper = new ScreenshotHelper(context);
        mSwipePx = SWIPE_DP * context.getResources().getDisplayMetrics().density;
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(SETTING), false,
                new ContentObserver(mMainHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        update();
                    }
                });
        update();
    }

    static boolean isEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), SETTING, 0) == 1;
    }

    static void setEnabled(Context context, boolean enabled) {
        Settings.Secure.putInt(context.getContentResolver(), SETTING, enabled ? 1 : 0);
    }

    private void update() {
        if (isEnabled(mContext)) {
            start();
        } else {
            stop();
        }
    }

    private void start() {
        if (mMonitor != null) {
            return;
        }
        try {
            mThread = new HandlerThread(TAG);
            mThread.start();
            mMonitor = mContext.getSystemService(InputManager.class)
                    .monitorGestureInput(TAG, Display.DEFAULT_DISPLAY);
            mReceiver = new Receiver(mMonitor.getInputChannel(), mThread.getLooper());
            Log.i(TAG, "listening");
        } catch (RuntimeException e) {
            Log.e(TAG, "cannot monitor touches", e);
            stop();
        }
    }

    private void stop() {
        if (mReceiver != null) {
            mReceiver.dispose();
            mReceiver = null;
        }
        if (mMonitor != null) {
            mMonitor.dispose();
            mMonitor = null;
        }
        if (mThread != null) {
            mThread.quitSafely();
            mThread = null;
        }
    }

    private void takeScreenshot() {
        // ScreenshotHelper binds SystemUI's screenshot service as UserHandle.CURRENT, which needs
        // INTERACT_ACROSS_USERS. Never let a failed request take NeoParts (persistent) down.
        try {
            mScreenshotHelper.takeScreenshot(WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                    SCREENSHOT_VENDOR_GESTURE, mMainHandler, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "screenshot request failed", e);
        }
    }

    private final class Receiver extends InputEventReceiver {
        private final SparseArray<float[]> mStart = new SparseArray<>();
        private long mDownTime;
        private boolean mArmed;
        private boolean mDone;

        Receiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent
                        && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                    onMotion((MotionEvent) event);
                }
            } finally {
                finishInputEvent(event, false);
            }
        }

        private void onMotion(MotionEvent ev) {
            final int count = ev.getPointerCount();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownTime = ev.getEventTime();
                    mArmed = false;
                    mDone = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (count == 3 && !mDone
                            && ev.getEventTime() - mDownTime <= MAX_FINGER_GAP_MS) {
                        mStart.clear();
                        for (int i = 0; i < count; i++) {
                            mStart.put(ev.getPointerId(i), new float[] {ev.getX(i), ev.getY(i)});
                        }
                        mArmed = true;
                    } else {
                        // a 4th finger, or fingers placed one by one (pinch, game controls)
                        mArmed = false;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mArmed && count == 3 && swipedDown(ev)) {
                        mArmed = false;
                        mDone = true;
                        mMonitor.pilferPointers();
                        mMainHandler.post(ThreeFingerScreenshot.this::takeScreenshot);
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    mArmed = false;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mArmed = false;
                    mDone = false;
                    break;
            }
        }

        /** Every finger moved down at least mSwipePx, and more down than sideways. */
        private boolean swipedDown(MotionEvent ev) {
            for (int i = 0; i < 3; i++) {
                final float[] start = mStart.get(ev.getPointerId(i));
                if (start == null) {
                    return false;
                }
                final float dx = Math.abs(ev.getX(i) - start[0]);
                final float dy = ev.getY(i) - start[1];
                if (dy < mSwipePx || dx > dy) {
                    return false;
                }
            }
            return true;
        }
    }
}
