/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** A page that is only links to other pages: shows the preference XML passed in its arguments. */
public class NeoXmlFragment extends SettingsBasePreferenceFragment {
    private static final String ARG_XML = "xml";

    static NeoXmlFragment of(int xmlRes) {
        final NeoXmlFragment fragment = new NeoXmlFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_XML, xmlRes);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(requireArguments().getInt(ARG_XML), rootKey);
    }
}
