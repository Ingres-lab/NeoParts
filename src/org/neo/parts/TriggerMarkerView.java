/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Draws and drags the L and R trigger markers over the whole display. Positions are
 * in view pixels; {@link #save()} converts them to natural panel points
 * ({@link TriggerConfig}), so a point stays on the same spot of the glass in any
 * rotation. Used by the overlay editor that runs on top of a game.
 */
final class TriggerMarkerView extends View {

    private static final int NEO_BLUE = 0xFF00B4FF;

    private final WindowManager mWindowManager;
    private final float[] mX = new float[2], mY = new float[2];
    private final boolean[] mSet = new boolean[2];
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mRadius;
    private int mDragging = -1;
    private boolean mLoaded;

    TriggerMarkerView(Context context) {
        super(context);
        mWindowManager = context.getSystemService(WindowManager.class);
        mRadius = dp(34);
        mFill.setColor(0x6600B4FF);
        mRing.setColor(NEO_BLUE);
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(dp(3));
        mText.setColor(Color.WHITE);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTextSize(dp(22));
        mText.setFakeBoldText(true);
    }

    void clear(int i) {
        mSet[i] = false;
        invalidate();
    }

    /** Writes both points (an unset marker clears its trigger) and enables the mapping. */
    void save() {
        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        final int rotation = getDisplay().getRotation();
        final int[] origin = new int[2];
        getLocationOnScreen(origin);
        for (int i = 0; i < 2; i++) {
            final String prop = i == 0 ? TriggerConfig.PROP_LEFT : TriggerConfig.PROP_RIGHT;
            if (!mSet[i]) {
                TriggerConfig.set(prop, null);
                continue;
            }
            TriggerConfig.set(prop, TriggerConfig.fromScreen(
                    origin[0] + mX[i] - bounds.left, origin[1] + mY[i] - bounds.top,
                    bounds.width(), bounds.height(), rotation));
        }
        TriggerConfig.setEnabled(true);
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
        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
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
            final float[] s = TriggerConfig.toScreen(p, bounds.width(), bounds.height(), rotation);
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
                    // a tap on an empty spot places a cleared marker there
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

    private static float clamp(float v, int max) {
        return Math.max(0f, Math.min(max, v));
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
