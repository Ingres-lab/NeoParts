/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BugreportManager;
import android.os.BugreportParams;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/**
 * Logs for hunting the random-shutdown / battery-percentage problem.
 *
 * Everything is recorded by the system itself: logcatd keeps all log buffers, kernel log included,
 * on disk across reboots, and neo_batrecd writes the battery, gauge and suspend state into the
 * log every 10 s. "Share logs" asks Android for a full bug report (which contains all of it plus
 * battery stats, power state, crashes and boot information) and opens the share sheet.
 */
public class LogsSettingsFragment extends SettingsBasePreferenceFragment {

    private static final String TAG = "NeoLogs";
    static final String PROP_MODE = "persist.sys.neo.batrec.mode";
    private static final String AUTHORITY = "org.neo.parts.fileprovider";

    private static final String KEY_SHARE = "logs_share";
    private static final String KEY_MODE = "logs_mode";

    private Preference mShare;
    private boolean mRunning;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.logs_settings, rootKey);

        final ListPreference mode = findPreference(KEY_MODE);
        mode.setValue(SystemProperties.get(PROP_MODE, "quiet"));
        mode.setOnPreferenceChangeListener((p, v) -> {
            SystemProperties.set(PROP_MODE, (String) v);
            return true;
        });

        mShare = findPreference(KEY_SHARE);
        mShare.setOnPreferenceClickListener(p -> {
            startReport();
            return true;
        });
    }

    private void startReport() {
        if (mRunning) {
            return;
        }
        final Context context = requireContext().getApplicationContext();
        final BugreportManager bm = context.getSystemService(BugreportManager.class);
        final File dir = new File(context.getCacheDir(), "bugreports");
        dir.mkdirs();
        final File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                f.delete();
            }
        }
        final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        final File out = new File(dir, "neo-logs-" + stamp + ".zip");
        final ParcelFileDescriptor fd;
        try {
            // Write-only + append, as BugreportManager requires: SELinux only lets dumpstate
            // append to app private files, so a read-write fd fails the binder fd handoff.
            fd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
        } catch (IOException e) {
            Log.e(TAG, "cannot create " + out, e);
            mShare.setSummary(context.getString(R.string.logs_failed, e.getMessage()));
            return;
        }
        mRunning = true;
        mShare.setEnabled(false);
        mShare.setSummary(R.string.logs_working);
        try {
            bm.startBugreport(fd, null, new BugreportParams(BugreportParams.BUGREPORT_MODE_FULL),
                    context.getMainExecutor(), new BugreportManager.BugreportCallback() {
                        @Override
                        public void onProgress(float progress) {
                            if (mShare != null) {
                                mShare.setSummary(context.getString(R.string.logs_progress,
                                        Math.round(progress)));
                            }
                        }

                        @Override
                        public void onError(int errorCode) {
                            Log.e(TAG, "bugreport failed: " + errorCode);
                            finish(context, fd, null, context.getString(R.string.logs_failed,
                                    Integer.toString(errorCode)));
                        }

                        @Override
                        public void onFinished() {
                            finish(context, fd, out, null);
                        }
                    });
        } catch (RuntimeException e) {
            Log.e(TAG, "startBugreport", e);
            finish(context, fd, null, context.getString(R.string.logs_failed, e.getMessage()));
        }
    }

    private void finish(Context context, ParcelFileDescriptor fd, File out, String error) {
        try {
            fd.close();
        } catch (IOException ignored) {
        }
        mRunning = false;
        if (mShare != null) {
            mShare.setEnabled(true);
        }
        if (out == null || !out.exists() || out.length() == 0) {
            if (mShare != null) {
                mShare.setSummary(error != null ? error : context.getString(R.string.logs_failed, "empty"));
            }
            return;
        }
        if (mShare != null) {
            mShare.setSummary(context.getString(R.string.logs_ready,
                    Formatter.formatShortFileSize(context, out.length())));
        }
        final Uri uri = FileProvider.getUriForFile(context, AUTHORITY, out);
        final Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, out.getName())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final Intent chooser = Intent.createChooser(send, context.getString(R.string.logs_share_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }
}
