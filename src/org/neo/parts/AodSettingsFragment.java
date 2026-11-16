/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.Calendar;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Always-on display mode, brief timeout and schedule. AodController applies them. */
public class AodSettingsFragment extends SettingsBasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ListPreference mMode;
    private ListPreference mBriefSeconds;
    private Preference mStart;
    private Preference mEnd;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.aod_settings, rootKey);

        mMode = findPreference(AodController.KEY_MODE);
        mBriefSeconds = findPreference(AodController.KEY_BRIEF_SECONDS);
        mStart = findPreference(AodController.KEY_SCHEDULE_START);
        mEnd = findPreference(AodController.KEY_SCHEDULE_END);

        mStart.setOnPreferenceClickListener(p -> pickTime(AodController.KEY_SCHEDULE_START,
                AodController.DEFAULT_SCHEDULE_START));
        mEnd.setOnPreferenceClickListener(p -> pickTime(AodController.KEY_SCHEDULE_END,
                AodController.DEFAULT_SCHEDULE_END));
        updateUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs().registerOnSharedPreferenceChangeListener(this);
        // The mode may have changed from the stock switch or the QS tile meanwhile
        mMode.setValue(prefs().getString(AodController.KEY_MODE, AodController.MODE_OFF));
        updateUi();
    }

    @Override
    public void onPause() {
        prefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (AodController.KEY_MODE.equals(key)) {
            mMode.setValue(prefs.getString(key, AodController.MODE_OFF));
        }
        updateUi();
    }

    private boolean pickTime(String key, int def) {
        final int minutes = prefs().getInt(key, def);
        new TimePickerDialog(getContext(), (view, hour, minute) ->
                prefs().edit().putInt(key, hour * 60 + minute).apply(),
                minutes / 60, minutes % 60, DateFormat.is24HourFormat(getContext())).show();
        return true;
    }

    private void updateUi() {
        final String mode = mMode.getValue();
        mBriefSeconds.setVisible(AodController.MODE_BRIEF.equals(mode));
        mStart.setVisible(AodController.MODE_SCHEDULE.equals(mode));
        mEnd.setVisible(AodController.MODE_SCHEDULE.equals(mode));
        mStart.setSummary(formatTime(prefs().getInt(AodController.KEY_SCHEDULE_START,
                AodController.DEFAULT_SCHEDULE_START)));
        mEnd.setSummary(formatTime(prefs().getInt(AodController.KEY_SCHEDULE_END,
                AodController.DEFAULT_SCHEDULE_END)));
    }

    private String formatTime(int minutes) {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        return DateFormat.getTimeFormat(getContext()).format(c.getTime());
    }

    private SharedPreferences prefs() {
        return getPreferenceManager().getSharedPreferences();
    }
}
