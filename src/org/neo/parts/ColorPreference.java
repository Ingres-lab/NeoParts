/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * A preference with a round colour swatch at the end. A picked colour is drawn filled; while the
 * colour is automatic, the swatch shows the colour the system currently uses, as a dashed ring.
 */
public class ColorPreference extends Preference {
    private int mColor;
    private int mAutomaticColor;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.neo_color_swatch);
    }

    /** @param color the picked colour, or 0 for automatic */
    void setColor(int color, int automaticColor) {
        mColor = color;
        mAutomaticColor = automaticColor;
        notifyChanged();
    }

    int getColor() {
        return mColor;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final ImageView swatch = (ImageView) holder.findViewById(R.id.color_swatch);
        if (swatch == null) {
            return;
        }
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (mColor != 0) {
            d.setColor(mColor);
        } else {
            d.setColor(0);
            d.setStroke(dp(3), mAutomaticColor, dp(4), dp(3));
        }
        swatch.setImageDrawable(d);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }
}
