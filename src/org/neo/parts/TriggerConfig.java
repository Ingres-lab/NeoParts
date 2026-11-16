/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.SystemProperties;
import android.view.Surface;

/**
 * Shoulder trigger mapping, shared with /vendor/bin/neo_triggerd.
 *
 * Points are stored in the panel's natural (portrait) orientation as 1/10000
 * of its width and height, so a point stays on the same spot of the glass in
 * any rotation: a landscape game's button does not move when the phone turns.
 */
final class TriggerConfig {

    static final String PROP_ENABLE = "persist.sys.neo.trigger.enable";
    static final String PROP_LEFT = "persist.sys.neo.trigger.left";
    static final String PROP_RIGHT = "persist.sys.neo.trigger.right";

    /** Published by neo_triggerd: bit 0 = left trigger unlocked (popped up), bit 1 = right. */
    static final String PROP_POPPED = "sys.neo.trigger.pop";

    static final int SCALE = 10000;

    private TriggerConfig() {}

    /** A point in natural panel coordinates, 0..SCALE on both axes. */
    static final class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = clamp(x);
            this.y = clamp(y);
        }

        @Override
        public String toString() {
            return x + "," + y;
        }
    }

    /** Bit mask of the triggers that are currently unlocked (popped up). */
    static int unlockedMask() {
        return SystemProperties.getInt(PROP_POPPED, 0);
    }

    static boolean isEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLE, false);
    }

    static void setEnabled(boolean enabled) {
        SystemProperties.set(PROP_ENABLE, enabled ? "1" : "0");
    }

    static Point get(String prop) {
        final String v = SystemProperties.get(prop, "");
        final String[] parts = v.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void set(String prop, Point p) {
        SystemProperties.set(prop, p == null ? "" : p.toString());
    }

    /**
     * Screen position (pixels in the current rotation, full display) to a
     * natural panel point. w/h are the display size in the current rotation.
     */
    static Point fromScreen(float x, float y, int w, int h, int rotation) {
        final double nx, ny;
        switch (rotation) {
            case Surface.ROTATION_90:   // natural top is on the left
                nx = 1d - y / h;
                ny = x / w;
                break;
            case Surface.ROTATION_180:
                nx = 1d - x / w;
                ny = 1d - y / h;
                break;
            case Surface.ROTATION_270:  // natural top is on the right
                nx = y / h;
                ny = 1d - x / w;
                break;
            default:
                nx = x / w;
                ny = y / h;
                break;
        }
        return new Point((int) Math.round(nx * SCALE), (int) Math.round(ny * SCALE));
    }

    /** Inverse of {@link #fromScreen}: returns {x, y} in pixels. */
    static float[] toScreen(Point p, int w, int h, int rotation) {
        final double nx = p.x / (double) SCALE, ny = p.y / (double) SCALE;
        final double x, y;
        switch (rotation) {
            case Surface.ROTATION_90:
                x = ny * w;
                y = (1d - nx) * h;
                break;
            case Surface.ROTATION_180:
                x = (1d - nx) * w;
                y = (1d - ny) * h;
                break;
            case Surface.ROTATION_270:
                x = (1d - ny) * w;
                y = nx * h;
                break;
            default:
                x = nx * w;
                y = ny * h;
                break;
        }
        return new float[] {(float) x, (float) y};
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(SCALE, v));
    }
}
