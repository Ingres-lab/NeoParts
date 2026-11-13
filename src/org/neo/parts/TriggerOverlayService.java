/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.Service;
import android.app.StatusBarManager;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

/**
 * Shoulder trigger overlay, independent of the Settings app.
 *
 * While trigger mapping is on (and Settings > Shoulder triggers > Floating button is
 * on), a small draggable trigger bubble floats above every app. Tapping it opens the
 * L/R editor as a full-screen overlay window on top of the running game: the game
 * stays resumed, keeps its orientation and keeps drawing underneath, so the markers
 * can be dropped exactly on its buttons. Long-press hides the bubble until mapping
 * is switched again or the Trigger points tile is used.
 *
 * Overlay windows (TYPE_APPLICATION_OVERLAY) need SYSTEM_ALERT_WINDOW, a signature
 * permission NeoParts gets as a platform app. NeoParts is persistent, so starting
 * this service from the tile or at boot is not a background start.
 */
public class TriggerOverlayService extends Service {

    static final String KEY_BUBBLE = "trigger_bubble";
    private static final String KEY_BUBBLE_X = "trigger_bubble_x";
    private static final String KEY_BUBBLE_Y = "trigger_bubble_y";

    private static final String ACTION_UPDATE = "org.neo.parts.trigger.UPDATE";
    private static final String ACTION_EDIT = "org.neo.parts.trigger.EDIT";

    private static final int BUBBLE_DP = 48;
    private static final long LONG_PRESS_MS = 600;

    private Context mContext;  // window context for TYPE_APPLICATION_OVERLAY
    private WindowManager mWindowManager;
    private SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private BubbleView mBubble;
    private WindowManager.LayoutParams mBubbleParams;
    private View mEditor;
    /* long-pressed away; shown again when mapping is toggled, on save or from the tile */
    private boolean mBubbleHidden;

    private final ComponentCallbacks mConfigCallback = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            if (mBubble != null) {
                // rotated: keep the bubble on screen
                placeBubble(mBubbleParams.x, mBubbleParams.y);
            }
        }

        @Override
        public void onLowMemory() {
        }
    };

    /** Shows or hides the bubble for the current settings. */
    static void update(Context context) {
        context.startService(new Intent(context, TriggerOverlayService.class)
                .setAction(ACTION_UPDATE));
    }

    /** Opens the overlay editor over whatever is on screen. */
    static void edit(Context context) {
        context.startService(new Intent(context, TriggerOverlayService.class)
                .setAction(ACTION_EDIT));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final Display display = getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        final Context windowContext = createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        mContext = new ContextThemeWrapper(windowContext,
                android.R.style.Theme_DeviceDefault);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        windowContext.registerComponentCallbacks(mConfigCallback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : ACTION_UPDATE;
        if (ACTION_EDIT.equals(action)) {
            mBubbleHidden = false;
            showEditor();
        } else {
            // a settings change shows a long-pressed bubble again
            mBubbleHidden = false;
        }
        updateBubble();
        if (mBubble == null && mEditor == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeBubble();
        removeEditor();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean wantBubble() {
        return TriggerConfig.isEnabled() && mPrefs.getBoolean(KEY_BUBBLE, true)
                && !mBubbleHidden && mEditor == null;
    }

    private void updateBubble() {
        if (wantBubble()) {
            addBubble();
        } else {
            removeBubble();
        }
    }

    // --- bubble -------------------------------------------------------------------

    private void addBubble() {
        if (mBubble != null) {
            return;
        }
        final int size = dp(BUBBLE_DP);
        mBubble = new BubbleView(mContext);
        mBubbleParams = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        mBubbleParams.gravity = Gravity.TOP | Gravity.START;
        mBubbleParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mBubbleParams.setTitle("NeoTriggerBubble");
        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        placeBubbleParams(mPrefs.getInt(KEY_BUBBLE_X, bounds.width() - size - dp(8)),
                mPrefs.getInt(KEY_BUBBLE_Y, bounds.height() / 3));
        mWindowManager.addView(mBubble, mBubbleParams);
    }

    private void removeBubble() {
        if (mBubble != null) {
            mWindowManager.removeViewImmediate(mBubble);
            mBubble = null;
        }
    }

    private void placeBubbleParams(int x, int y) {
        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        final int size = dp(BUBBLE_DP);
        mBubbleParams.x = Math.max(0, Math.min(bounds.width() - size, x));
        mBubbleParams.y = Math.max(0, Math.min(bounds.height() - size, y));
    }

    private void placeBubble(int x, int y) {
        placeBubbleParams(x, y);
        mWindowManager.updateViewLayout(mBubble, mBubbleParams);
    }

    /** Round trigger button: drag to move, tap to edit, long-press to hide. */
    private final class BubbleView extends View {
        private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable mIcon;
        private final int mSlop;
        private float mDownRawX, mDownRawY;
        private int mDownX, mDownY;
        private boolean mDragged, mLongPressed;

        private final Runnable mLongPress = () -> {
            mLongPressed = true;
            mBubbleHidden = true;
            removeBubble();
            Toast.makeText(TriggerOverlayService.this, R.string.trigger_bubble_hidden,
                    Toast.LENGTH_LONG).show();
            stopIfIdle();
        };

        BubbleView(Context context) {
            super(context);
            mBg.setColor(0xCC101418);
            mRing.setColor(0xFF00B4FF);
            mRing.setStyle(Paint.Style.STROKE);
            mRing.setStrokeWidth(dp(2));
            mIcon = context.getDrawable(R.drawable.ic_trigger).mutate();
            mIcon.setTint(Color.WHITE);
            mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setAlpha(0.8f);
            setContentDescription(getString(R.string.trigger_tile_label));
        }

        @Override
        protected void onDraw(Canvas c) {
            final float r = getWidth() / 2f;
            c.drawCircle(r, r, r - dp(1), mBg);
            c.drawCircle(r, r, r - dp(1), mRing);
            final int inset = getWidth() / 4;
            mIcon.setBounds(inset, inset, getWidth() - inset, getHeight() - inset);
            mIcon.draw(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownRawX = e.getRawX();
                    mDownRawY = e.getRawY();
                    mDownX = mBubbleParams.x;
                    mDownY = mBubbleParams.y;
                    mDragged = false;
                    mLongPressed = false;
                    setAlpha(1f);
                    mHandler.postDelayed(mLongPress, LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    final float dx = e.getRawX() - mDownRawX;
                    final float dy = e.getRawY() - mDownRawY;
                    if (!mDragged && Math.hypot(dx, dy) > mSlop) {
                        mDragged = true;
                        mHandler.removeCallbacks(mLongPress);
                    }
                    if (mDragged && mBubble != null) {
                        placeBubble(mDownX + (int) dx, mDownY + (int) dy);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeCallbacks(mLongPress);
                    setAlpha(0.8f);
                    if (mLongPressed) {
                        return true;
                    }
                    if (mDragged) {
                        mPrefs.edit().putInt(KEY_BUBBLE_X, mBubbleParams.x)
                                .putInt(KEY_BUBBLE_Y, mBubbleParams.y).apply();
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        showEditor();
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    // --- editor -------------------------------------------------------------------

    private void showEditor() {
        if (mEditor != null) {
            return;
        }
        collapseShade();
        removeBubble();

        final FrameLayout root = new FrameLayout(mContext);
        final TriggerMarkerView markers = new TriggerMarkerView(mContext);
        root.addView(markers, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView hint = new TextView(mContext);
        hint.setText(R.string.trigger_editor_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(40), dp(24), 0);
        root.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        final LinearLayout bar = new LinearLayout(mContext);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, 0, 0, dp(32));
        bar.addView(button(R.string.trigger_editor_clear_left, v -> markers.clear(0)));
        bar.addView(button(R.string.trigger_editor_clear_right, v -> markers.clear(1)));
        bar.addView(button(android.R.string.cancel, v -> closeEditor()));
        bar.addView(button(R.string.trigger_editor_save, v -> {
            markers.save();
            closeEditor();
        }));
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // cover the whole panel, bars and cutout included, so view and screen coordinates
        // line up with the game's
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("NeoTriggerEditor");
        mEditor = root;
        mWindowManager.addView(root, lp);
    }

    private void closeEditor() {
        removeEditor();
        mBubbleHidden = false;
        updateBubble();
        stopIfIdle();
    }

    private void removeEditor() {
        if (mEditor != null) {
            mWindowManager.removeViewImmediate(mEditor);
            mEditor = null;
        }
    }

    private void stopIfIdle() {
        if (mBubble == null && mEditor == null) {
            stopSelf();
        }
    }

    private Button button(int text, View.OnClickListener l) {
        final Button b = new Button(mContext);
        b.setText(text);
        b.setOnClickListener(l);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** The editor is often opened from the Quick Settings tile. */
    private void collapseShade() {
        final StatusBarManager sbm = getSystemService(StatusBarManager.class);
        if (sbm != null) {
            sbm.collapsePanels();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
