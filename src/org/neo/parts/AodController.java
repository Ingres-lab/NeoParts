/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Calendar;

/**
 * Always-on display modes on top of Settings.Secure.DOZE_ALWAYS_ON, which this class owns:
 *
 *  - off / always: the setting is 0 / 1.
 *  - schedule: 1 between the start and end time (the window may cross midnight), 0 outside.
 *  - brief: 1 while the screen is on and for N seconds after it turns off, then 0.
 *
 * Changing the setting while the phone is already dozing needs the DozeTriggers patch in
 * frameworks/base (SystemUI otherwise only reads it when doze starts).
 *
 * Flipping the stock "Always show time and info" switch or the AOD Quick Settings tile is
 * taken as choosing "always" / "off" here, so the two never fight.
 */
public final class AodController implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NeoAod";

    static final String KEY_MODE = "aod_mode";
    static final String KEY_BRIEF_SECONDS = "aod_brief_seconds";
    static final String KEY_SCHEDULE_START = "aod_schedule_start";  // minutes after midnight
    static final String KEY_SCHEDULE_END = "aod_schedule_end";

    static final String MODE_OFF = "off";
    static final String MODE_ALWAYS = "always";
    static final String MODE_SCHEDULE = "schedule";
    static final String MODE_BRIEF = "brief";

    static final String DEFAULT_BRIEF_SECONDS = "5";
    static final int DEFAULT_SCHEDULE_START = 7 * 60;
    static final int DEFAULT_SCHEDULE_END = 23 * 60;

    private static final String SCHEDULE_ACTION = "org.neo.parts.AOD_SCHEDULE";

    private static AodController sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences mPrefs;
    private final AlarmManager mAlarmManager;
    private final PowerManager mPowerManager;

    private boolean mInteractive = true;
    /* brief mode: the screen went off and the N seconds are over */
    private boolean mBriefExpired;
    /* the last value this class wrote, to tell our writes from the user's */
    private int mWritten = -1;

    private final AlarmManager.OnAlarmListener mBriefAlarm = () -> {
        mBriefExpired = true;
        apply("brief timeout");
    };
    /* PendingIntent + allow-while-idle: a schedule boundary must fire in deep Doze too */
    private PendingIntent mScheduleIntent;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    mInteractive = true;
                    mBriefExpired = false;
                    mAlarmManager.cancel(mBriefAlarm);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mInteractive = false;
                    mBriefExpired = false;
                    if (MODE_BRIEF.equals(mode())) {
                        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + briefSeconds() * 1000L,
                                TAG + ":brief", mBriefAlarm, mHandler);
                    }
                    break;
                default:  // schedule boundary, time, time zone or date changed
                    break;
            }
            apply(intent.getAction());
        }
    };

    private final ContentObserver mAlwaysOnObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final int value = readAlwaysOn();
            if (value == mWritten) {
                return;
            }
            // Someone else (Settings switch, QS tile) changed it: follow the user
            Log.i(TAG, "doze_always_on set to " + value + " outside Neo, mode follows");
            mWritten = value;
            mPrefs.edit().putString(KEY_MODE, value == 1 ? MODE_ALWAYS : MODE_OFF).apply();
        }
    };

    static synchronized void init(Context context) {
        if (sInstance == null) {
            sInstance = new AodController(context.getApplicationContext());
            sInstance.start();
        }
    }

    private AodController(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mAlarmManager = context.getSystemService(AlarmManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    private void start() {
        if (!mPrefs.contains(KEY_MODE)) {
            // First run: keep whatever the stock switch says
            mPrefs.edit().putString(KEY_MODE, readAlwaysOn() == 1 ? MODE_ALWAYS : MODE_OFF)
                    .apply();
        }
        mInteractive = mPowerManager.isInteractive();
        mWritten = readAlwaysOn();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        mContext.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED);
        mContext.registerReceiver(mReceiver, new IntentFilter(SCHEDULE_ACTION), null, mHandler,
                Context.RECEIVER_NOT_EXPORTED);
        mScheduleIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(SCHEDULE_ACTION).setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON), false,
                mAlwaysOnObserver);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        apply("start");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key != null && key.startsWith("aod_")) {
            if (KEY_MODE.equals(key) && !MODE_BRIEF.equals(mode())) {
                mAlarmManager.cancel(mBriefAlarm);
                mBriefExpired = false;
            }
            apply("pref " + key);
        }
    }

    private void apply(String why) {
        final String mode = mode();
        final boolean on;
        switch (mode) {
            case MODE_ALWAYS:
                on = true;
                break;
            case MODE_SCHEDULE:
                on = inSchedule();
                scheduleNextBoundary();
                break;
            case MODE_BRIEF:
                on = mInteractive || !mBriefExpired;
                break;
            default:
                on = false;
                break;
        }
        if (!MODE_SCHEDULE.equals(mode)) {
            mAlarmManager.cancel(mScheduleIntent);
        }
        writeAlwaysOn(on ? 1 : 0);
        Log.d(TAG, "apply(" + why + "): mode=" + mode + " aod=" + on
                + " interactive=" + mInteractive);
    }

    private boolean inSchedule() {
        final int start = mPrefs.getInt(KEY_SCHEDULE_START, DEFAULT_SCHEDULE_START);
        final int end = mPrefs.getInt(KEY_SCHEDULE_END, DEFAULT_SCHEDULE_END);
        final Calendar now = Calendar.getInstance();
        final int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (start == end) {
            return true;
        }
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    private void scheduleNextBoundary() {
        final int start = mPrefs.getInt(KEY_SCHEDULE_START, DEFAULT_SCHEDULE_START);
        final int end = mPrefs.getInt(KEY_SCHEDULE_END, DEFAULT_SCHEDULE_END);
        final long next = Math.min(nextOccurrence(start), nextOccurrence(end));
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, mScheduleIntent);
    }

    private static long nextOccurrence(int minuteOfDay) {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTimeInMillis();
    }

    private String mode() {
        return mPrefs.getString(KEY_MODE, MODE_OFF);
    }

    private int briefSeconds() {
        try {
            return Integer.parseInt(mPrefs.getString(KEY_BRIEF_SECONDS, DEFAULT_BRIEF_SECONDS));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_BRIEF_SECONDS);
        }
    }

    private int readAlwaysOn() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, 0);
    }

    private void writeAlwaysOn(int value) {
        if (readAlwaysOn() == value) {
            mWritten = value;
            return;
        }
        mWritten = value;
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, value);
    }
}
