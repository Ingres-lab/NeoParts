/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Game performance mode: while a chosen game or emulator is the focused app, raise the top-app
 * cgroup's uclamp.min (init.neo.rc, sys.neo.game.boost) so WALT cannot park its threads at the
 * cluster's minimum clock.
 *
 * Why: WALT picks the frequency from utilization. Threads that mostly wait on each other (Wine /
 * Proton synchronising through ntsync, render/game-logic hand-offs) look idle to it, so the A710
 * cluster stays at 633 MHz and every cross-core wake-up runs about 2.7x slower than at full clock
 * (measured: 34.5 us vs 12.6 us per ntsync event round trip; uclamp.min 50 gives 14.5 us).
 * The floor only applies while the game is in front, so it costs nothing elsewhere.
 */
final class GameModeController {
    private static final String TAG = "NeoGameMode";

    /** Settings.Secure: 1 = on (default), 0 = off. */
    static final String KEY_ENABLED = "neo_game_mode";
    /** Settings.Secure: comma-separated packages; unset = the defaults below. */
    static final String KEY_PACKAGES = "neo_game_packages";
    /** uclamp.min percent applied to top-app while a game is in front. */
    static final String PROP_FLOOR = "persist.sys.neo.game.floor";
    static final int DEFAULT_FLOOR = 50;
    /** 1 while a chosen game is the focused app (init.neo.rc acts on it). */
    private static final String PROP_BOOST = "sys.neo.game.boost";

    /** Wine / Proton front-ends and the X server they use, preselected when installed. */
    static final Set<String> DEFAULT_GAMES = new LinkedHashSet<>(Arrays.asList(
            "com.winlator",
            "com.winlator.cmod",
            "com.ewt45.winlator",
            "com.micewine.emu",
            "com.xiaoji.egggame",
            "com.termux",
            "com.termux.x11"));

    private static GameModeController sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Boolean mBoost;

    private GameModeController(Context context) {
        mContext = context;
        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override
                public void onTaskStackChanged() {
                    mHandler.post(GameModeController.this::update);
                }
            });
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, "cannot watch the focused app", e);
        }
        final ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                update();
            }
        };
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_ENABLED), false, observer);
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_PACKAGES), false, observer);
        update();
    }

    static void init(Context context) {
        if (sInstance == null) {
            sInstance = new GameModeController(context.getApplicationContext());
        }
    }

    static boolean isEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), KEY_ENABLED, 1) == 1;
    }

    static Set<String> games(Context context) {
        final String v = Settings.Secure.getString(context.getContentResolver(), KEY_PACKAGES);
        if (v == null) {
            return new LinkedHashSet<>(DEFAULT_GAMES);
        }
        final Set<String> out = new LinkedHashSet<>();
        for (String p : v.split(",")) {
            if (!TextUtils.isEmpty(p.trim())) {
                out.add(p.trim());
            }
        }
        return out;
    }

    static void setGame(Context context, String pkg, boolean game) {
        final Set<String> list = games(context);
        if (game ? list.add(pkg) : list.remove(pkg)) {
            // an empty string (not null) keeps "no games" distinct from "defaults"
            Settings.Secure.putString(context.getContentResolver(), KEY_PACKAGES,
                    String.join(",", list));
        }
    }

    static int floor() {
        return SystemProperties.getInt(PROP_FLOOR, DEFAULT_FLOOR);
    }

    static void setFloor(int percent) {
        SystemProperties.set(PROP_FLOOR, Integer.toString(percent));
    }

    private void update() {
        final String top = focusedPackage();
        final boolean boost = isEnabled(mContext) && top != null && games(mContext).contains(top);
        if (mBoost != null && mBoost == boost) {
            return;
        }
        mBoost = boost;
        SystemProperties.set(PROP_BOOST, boost ? "1" : "0");
        Log.i(TAG, (boost ? "boost on for " : "boost off, front: ") + top);
    }

    private static String focusedPackage() {
        try {
            final ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            final ComponentName top = info != null ? info.topActivity : null;
            return top != null ? top.getPackageName() : null;
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "focused app unknown", e);
            return null;
        }
    }
}
