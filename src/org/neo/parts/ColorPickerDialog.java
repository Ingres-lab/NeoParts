/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Colour picker: preset swatches, hue / saturation / brightness sliders and a hex field, with a
 * live preview. Optionally offers "Automatic" (returns 0) for colours Monet can choose itself.
 */
final class ColorPickerDialog {

    interface Listener {
        /** @param color the picked colour, or 0 for automatic */
        void onColorPicked(int color);
    }

    /** Material-ish presets across the hue wheel plus two near-neutrals. */
    private static final int[] PRESETS = {
            0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
            0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
            0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
            0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
            0xFF6D4C41, 0xFF757575, 0xFF546E7A, 0xFFB0A8B9,
    };

    private final Context mContext;
    private final float[] mHsv = new float[3];
    private View mPreview;
    private SeekBar mHue;
    private SeekBar mSat;
    private SeekBar mVal;
    private EditText mHex;
    private boolean mUpdating;

    private ColorPickerDialog(Context context, int initial) {
        mContext = context;
        Color.colorToHSV(initial == 0 ? PRESETS[5] : initial, mHsv);
    }

    static void show(Context context, CharSequence title, int initial, boolean allowAutomatic,
            Listener listener) {
        final ColorPickerDialog picker = new ColorPickerDialog(context, initial);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(picker.buildView())
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> listener.onColorPicked(picker.color()))
                .setNegativeButton(android.R.string.cancel, null);
        if (allowAutomatic) {
            builder.setNeutralButton(R.string.monet_color_automatic,
                    (d, w) -> listener.onColorPicked(0));
        }
        builder.show();
    }

    private int color() {
        return Color.HSVToColor(mHsv);
    }

    private View buildView() {
        final int pad = dp(20);
        final LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(8), pad, 0);

        mPreview = new View(mContext);
        root.addView(mPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        mHex = new EditText(mContext);
        mHex.setSingleLine(true);
        mHex.setGravity(Gravity.CENTER);
        mHex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mHex.setFilters(new InputFilter[] {new InputFilter.LengthFilter(7)});
        mHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (mUpdating) {
                    return;
                }
                String text = s.toString().trim();
                if (!text.startsWith("#")) {
                    text = "#" + text;
                }
                if (text.length() == 7) {
                    try {
                        Color.colorToHSV(Color.parseColor(text), mHsv);
                        refresh(false);
                    } catch (IllegalArgumentException ignored) {
                        // not a colour yet
                    }
                }
            }
        });
        root.addView(mHex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mHue = slider(root, R.string.monet_picker_hue, 360);
        mSat = slider(root, R.string.monet_picker_saturation, 100);
        mVal = slider(root, R.string.monet_picker_brightness, 100);

        final GridLayout grid = new GridLayout(mContext);
        grid.setColumnCount(5);
        grid.setPadding(0, dp(12), 0, dp(4));
        final int size = dp(40);
        for (int preset : PRESETS) {
            final View swatch = new View(mContext);
            swatch.setBackground(circle(preset));
            swatch.setContentDescription(hex(preset));
            swatch.setOnClickListener(v -> {
                Color.colorToHSV(preset, mHsv);
                refresh(true);
            });
            final GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(swatch, lp);
        }
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        refresh(true);
        return root;
    }

    private SeekBar slider(LinearLayout root, int label, int max) {
        final TextView title = new TextView(mContext);
        title.setText(label);
        title.setPadding(0, dp(12), 0, 0);
        root.addView(title);
        final SeekBar bar = new SeekBar(mContext);
        bar.setMax(max);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                mHsv[0] = mHue.getProgress();
                mHsv[1] = mSat.getProgress() / 100f;
                mHsv[2] = mVal.getProgress() / 100f;
                refresh(true);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(bar);
        return bar;
    }

    /** Shows the current colour everywhere; updates the hex field only when asked to. */
    private void refresh(boolean updateHex) {
        mUpdating = true;
        final int color = color();
        final GradientDrawable preview = new GradientDrawable();
        preview.setCornerRadius(dp(16));
        preview.setColor(color);
        mPreview.setBackground(preview);
        mHue.setProgress(Math.round(mHsv[0]));
        mSat.setProgress(Math.round(mHsv[1] * 100));
        mVal.setProgress(Math.round(mHsv[2] * 100));
        if (updateHex) {
            mHex.setText(hex(color));
        }
        mUpdating = false;
    }

    private static GradientDrawable circle(int color) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                mContext.getResources().getDisplayMetrics());
    }
}
