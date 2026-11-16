/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * The five Monet palettes the system is using right now, as rows of tones from light to dark.
 * Reads the public system_* colour resources, which SystemUI's theme overlay rewrites; NeoParts
 * restarts with the new colours when the theme changes, so the preview follows every edit.
 */
public class MonetPreviewView extends View {

    /** Tones shown per row (system_*_TONE resources): 10, 50, 100 ... 900. */
    static final int[][] PALETTES = {
            {android.R.color.system_accent1_10, android.R.color.system_accent1_50,
                    android.R.color.system_accent1_100, android.R.color.system_accent1_200,
                    android.R.color.system_accent1_300, android.R.color.system_accent1_400,
                    android.R.color.system_accent1_500, android.R.color.system_accent1_600,
                    android.R.color.system_accent1_700, android.R.color.system_accent1_800,
                    android.R.color.system_accent1_900},
            {android.R.color.system_accent2_10, android.R.color.system_accent2_50,
                    android.R.color.system_accent2_100, android.R.color.system_accent2_200,
                    android.R.color.system_accent2_300, android.R.color.system_accent2_400,
                    android.R.color.system_accent2_500, android.R.color.system_accent2_600,
                    android.R.color.system_accent2_700, android.R.color.system_accent2_800,
                    android.R.color.system_accent2_900},
            {android.R.color.system_accent3_10, android.R.color.system_accent3_50,
                    android.R.color.system_accent3_100, android.R.color.system_accent3_200,
                    android.R.color.system_accent3_300, android.R.color.system_accent3_400,
                    android.R.color.system_accent3_500, android.R.color.system_accent3_600,
                    android.R.color.system_accent3_700, android.R.color.system_accent3_800,
                    android.R.color.system_accent3_900},
            {android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
                    android.R.color.system_neutral1_100, android.R.color.system_neutral1_200,
                    android.R.color.system_neutral1_300, android.R.color.system_neutral1_400,
                    android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
                    android.R.color.system_neutral1_700, android.R.color.system_neutral1_800,
                    android.R.color.system_neutral1_900},
            {android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
                    android.R.color.system_neutral2_100, android.R.color.system_neutral2_200,
                    android.R.color.system_neutral2_300, android.R.color.system_neutral2_400,
                    android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
                    android.R.color.system_neutral2_700, android.R.color.system_neutral2_800,
                    android.R.color.system_neutral2_900},
    };

    /** The tone used to represent each palette elsewhere (swatches of automatic colours). */
    static int representative(Context context, int palette) {
        return context.getColor(PALETTES[palette][6]);
    }

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final String[] mLabels;
    private final float mRowHeight;
    private final float mGap;
    private final float mLabelWidth;

    public MonetPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLabels = context.getResources().getStringArray(R.array.monet_palette_short_names);
        mRowHeight = dp(22);
        mGap = dp(6);
        mLabelWidth = dp(92);
        mText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                getResources().getDisplayMetrics()));
        final TypedArray a = context.obtainStyledAttributes(
                new int[] {android.R.attr.textColorSecondary});
        mText.setColor(a.getColor(0, 0xFF888888));
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = (int) (PALETTES.length * (mRowHeight + mGap) - mGap)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float left = getPaddingLeft() + mLabelWidth;
        final float right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        for (int row = 0; row < PALETTES.length; row++) {
            final int[] tones = PALETTES[row];
            final float cell = (right - left) / tones.length;
            canvas.drawText(mLabels[row], getPaddingLeft(),
                    top + mRowHeight / 2 - (mText.ascent() + mText.descent()) / 2, mText);
            for (int i = 0; i < tones.length; i++) {
                mFill.setColor(getContext().getColor(tones[i]));
                mRect.set(left + i * cell + 1, top, left + (i + 1) * cell - 1, top + mRowHeight);
                final float r = (i == 0 || i == tones.length - 1) ? mRowHeight / 2 : dp(3);
                canvas.drawRoundRect(mRect, r, r, mFill);
            }
            top += mRowHeight + mGap;
        }
    }

    private float dp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
