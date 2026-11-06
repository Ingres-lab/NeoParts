/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen, see-through editor for the trigger touch points. Started from
 * NeoParts or the Quick Settings tile, so it can sit on top of a running game:
 * drag L and R onto the game's buttons and save.
 */
public class TriggerEditorActivity extends Activity {

    private static final int NEO_BLUE = 0xFF00B4FF;

    private MarkerView mMarkers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setDecorFitsSystemWindows(false);

        final FrameLayout root = new FrameLayout(this);
        mMarkers = new MarkerView(this);
        root.addView(mMarkers, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView hint = new TextView(this);
        hint.setText(R.string.trigger_editor_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(48), dp(24), 0);
        root.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        final LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, 0, 0, dp(40));
        bar.addView(button(R.string.trigger_editor_clear_left, v -> mMarkers.clear(0)));
        bar.addView(button(R.string.trigger_editor_clear_right, v -> mMarkers.clear(1)));
        bar.addView(button(android.R.string.cancel, v -> finish()));
        bar.addView(button(R.string.trigger_editor_save, v -> save()));
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private Button button(int text, View.OnClickListener l) {
        final Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void save() {
        final Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
        final int rotation = getDisplay().getRotation();
        final int[] origin = new int[2];
        mMarkers.getLocationOnScreen(origin);
        for (int i = 0; i < 2; i++) {
            final String prop = i == 0 ? TriggerConfig.PROP_LEFT : TriggerConfig.PROP_RIGHT;
            if (!mMarkers.isSet(i)) {
                TriggerConfig.set(prop, null);
                continue;
            }
            TriggerConfig.set(prop, TriggerConfig.fromScreen(
                    origin[0] + mMarkers.x(i) - bounds.left, origin[1] + mMarkers.y(i) - bounds.top,
                    bounds.width(), bounds.height(), rotation));
        }
        TriggerConfig.setEnabled(true);
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Draws and drags the two markers. Positions are in view pixels. */
    private final class MarkerView extends View {

        private final float[] mX = new float[2], mY = new float[2];
        private final boolean[] mSet = new boolean[2];
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mRadius = dp(34);
        private int mDragging = -1;
        private boolean mLoaded;

        MarkerView(Context context) {
            super(context);
            mFill.setColor(0x6600B4FF);
            mRing.setColor(NEO_BLUE);
            mRing.setStyle(Paint.Style.STROKE);
            mRing.setStrokeWidth(dp(3));
            mText.setColor(Color.WHITE);
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setTextSize(dp(22));
            mText.setFakeBoldText(true);
        }

        float x(int i) { return mX[i]; }
        float y(int i) { return mY[i]; }
        boolean isSet(int i) { return mSet[i]; }

        void clear(int i) {
            mSet[i] = false;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (oldw != 0 && (w != oldw || h != oldh)) {
                mLoaded = false;  // rotated: place the saved points again
                requestLayout();
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (mLoaded || getWidth() == 0) {
                return;
            }
            mLoaded = true;
            final Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            final int rotation = getDisplay().getRotation();
            final int[] origin = new int[2];
            getLocationOnScreen(origin);
            final String[] props = {TriggerConfig.PROP_LEFT, TriggerConfig.PROP_RIGHT};
            for (int i = 0; i < 2; i++) {
                final TriggerConfig.Point p = TriggerConfig.get(props[i]);
                if (p == null) {
                    // unset: start on the matching half of the screen
                    mX[i] = getWidth() * (i == 0 ? 0.25f : 0.75f);
                    mY[i] = getHeight() * 0.5f;
                    mSet[i] = true;
                    continue;
                }
                final float[] s = TriggerConfig.toScreen(p, bounds.width(), bounds.height(),
                        rotation);
                mX[i] = s[0] + bounds.left - origin[0];
                mY[i] = s[1] + bounds.top - origin[1];
                mSet[i] = true;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0x55000000);
            final String[] labels = {"L", "R"};
            final float baseline = -(mText.ascent() + mText.descent()) / 2f;
            for (int i = 0; i < 2; i++) {
                if (!mSet[i]) {
                    continue;
                }
                canvas.drawCircle(mX[i], mY[i], mRadius, mFill);
                canvas.drawCircle(mX[i], mY[i], mRadius, mRing);
                canvas.drawText(labels[i], mX[i], mY[i] + baseline, mText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDragging = hit(e.getX(), e.getY());
                    if (mDragging < 0) {
                        // tap on an empty spot places a cleared marker there
                        for (int i = 0; i < 2; i++) {
                            if (!mSet[i]) {
                                mDragging = i;
                                mSet[i] = true;
                                break;
                            }
                        }
                    }
                    // fall through
                case MotionEvent.ACTION_MOVE:
                    if (mDragging >= 0) {
                        mX[mDragging] = clamp(e.getX(), getWidth());
                        mY[mDragging] = clamp(e.getY(), getHeight());
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = -1;
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private int hit(float x, float y) {
            int best = -1;
            float bestD = mRadius * 2f;
            for (int i = 0; i < 2; i++) {
                if (!mSet[i]) {
                    continue;
                }
                final float d = (float) Math.hypot(x - mX[i], y - mY[i]);
                if (d < bestD) {
                    best = i;
                    bestD = d;
                }
            }
            return best;
        }

        private float clamp(float v, int max) {
            return Math.max(0f, Math.min(max, v));
        }
    }
}
