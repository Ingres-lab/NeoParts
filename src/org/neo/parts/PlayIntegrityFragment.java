/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.neo.NeoPropHooks;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Tools > Play Integrity: loads a pif.json (Build fields of a certified device) into
 * persist.sys.neo.pif.<FIELD>. NeoPropHooks (frameworks/base) applies them only inside
 * Google's DroidGuard process, so the rest of the system keeps the real identity.
 * This can give BASIC integrity; DEVICE and STRONG need hardware attestation of a
 * locked bootloader, which no fingerprint can provide.
 */
public class PlayIntegrityFragment extends PreferenceFragmentCompat {

    static final String PROP_PREFIX = "persist.sys.neo.pif.";
    private static final String PROP_ENABLE = PROP_PREFIX + "enable";
    /* maintained by the Neo maintainers next to the releases */
    static final String UPDATE_URL =
            "https://raw.githubusercontent.com/Ingres-lab/releases/main/pif.json";
    private static final int PROP_VALUE_MAX = 91;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Preference mCurrent;
    private ActivityResultLauncher<String[]> mPicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::onPicked);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final SwitchPreferenceCompat enable = new SwitchPreferenceCompat(context);
        enable.setPersistent(false);
        enable.setTitle(R.string.pif_enable_title);
        enable.setSummary(R.string.pif_enable_summary);
        enable.setChecked(SystemProperties.getBoolean(PROP_ENABLE, true));
        enable.setOnPreferenceChangeListener((p, v) -> {
            SystemProperties.set(PROP_ENABLE, (Boolean) v ? "1" : "0");
            restartDroidGuard(context);
            return true;
        });
        screen.addPreference(enable);

        mCurrent = new Preference(context);
        mCurrent.setSelectable(false);
        mCurrent.setTitle(R.string.pif_current_title);
        screen.addPreference(mCurrent);

        screen.addPreference(action(context, R.string.pif_update_title,
                R.string.pif_update_summary, this::download));
        screen.addPreference(action(context, R.string.pif_import_title,
                R.string.pif_import_summary,
                () -> mPicker.launch(new String[] {"application/json", "text/plain", "*/*"})));
        screen.addPreference(action(context, R.string.pif_clear_title, 0, () -> {
            apply(context, new LinkedHashMap<>());
            toast(R.string.pif_cleared);
        }));

        final Preference footer = new Preference(context);
        footer.setSelectable(false);
        footer.setSummary(R.string.pif_footer);
        screen.addPreference(footer);
        updateCurrent();
    }

    private static Preference action(Context context, int title, int summary, Runnable r) {
        final Preference p = new Preference(context);
        p.setTitle(title);
        if (summary != 0) {
            p.setSummary(summary);
        }
        p.setOnPreferenceClickListener(x -> {
            r.run();
            return true;
        });
        return p;
    }

    private void updateCurrent() {
        final String fp = SystemProperties.get(PROP_PREFIX + "FINGERPRINT", "");
        mCurrent.setSummary(fp.isEmpty() ? getString(R.string.pif_current_none)
                : SystemProperties.get(PROP_PREFIX + "MODEL", "?") + "\n" + fp
                        + "\n" + getString(R.string.pif_current_patch,
                                SystemProperties.get(PROP_PREFIX + "VERSION.SECURITY_PATCH", "?")));
    }

    private void onPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = requireContext();
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            load(context, read(in));
        } catch (Exception e) {
            toast(R.string.pif_invalid);
        }
    }

    private void download() {
        final Context context = requireContext().getApplicationContext();
        toast(R.string.pif_downloading);
        new Thread(() -> {
            String json = null;
            try {
                final HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = c.getInputStream()) {
                        json = read(in);
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
            }
            final String result = json;
            mHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (result == null) {
                    toast(R.string.pif_download_failed);
                } else {
                    load(context, result);
                }
            });
        }).start();
    }

    private void load(Context context, String json) {
        final Map<String, String> fields = parse(json);
        if (fields == null || !fields.containsKey("FINGERPRINT")) {
            toast(R.string.pif_invalid);
            return;
        }
        apply(context, fields);
        toast(R.string.pif_loaded);
    }

    /**
     * Accepts the usual pif.json layouts: Build field names (FINGERPRINT, MODEL, ...),
     * Build.VERSION ones with or without "VERSION." (SECURITY_PATCH), and FIRST_API_LEVEL
     * for DEVICE_INITIAL_SDK_INT. Anything else (spoofProvider, *.security_patch...) is
     * ignored.
     */
    static Map<String, String> parse(String json) {
        try {
            final JSONObject o = new JSONObject(json);
            final Map<String, String> out = new LinkedHashMap<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                final String key = it.next();
                String field = key.trim();
                if (field.equals("FIRST_API_LEVEL") || field.equals("DEVICE_INITIAL_SDK_INT")) {
                    field = "VERSION.DEVICE_INITIAL_SDK_INT";
                } else if (field.equals("SECURITY_PATCH") || field.equals("RELEASE")
                        || field.equals("INCREMENTAL")) {
                    field = "VERSION." + field;
                }
                if (!isKnownField(field)) {
                    continue;
                }
                final String value = o.optString(key, "").trim();
                if (value.isEmpty() || value.length() > PROP_VALUE_MAX) {
                    continue;
                }
                out.put(field, value);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isKnownField(String field) {
        for (String f : NeoPropHooks.PIF_FIELDS) {
            if (f.equals(field)) {
                return true;
            }
        }
        return false;
    }

    private void apply(Context context, Map<String, String> fields) {
        for (String f : NeoPropHooks.PIF_FIELDS) {
            SystemProperties.set(PROP_PREFIX + f, fields.getOrDefault(f, ""));
        }
        restartDroidGuard(context);
        updateCurrent();
    }

    /* DroidGuard reads the fields when its process starts */
    private static void restartDroidGuard(Context context) {
        context.getSystemService(ActivityManager.class)
                .killBackgroundProcesses("com.google.android.gms");
    }

    private static String read(InputStream in) throws java.io.IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private void toast(int text) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
    }
}
