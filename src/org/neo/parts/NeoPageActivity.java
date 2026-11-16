/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Hosts one Neo sub-page, chosen by the "page" extra of the home entry's intent
 * (res/xml/neo_settings.xml), so each page needs no activity of its own.
 */
public class NeoPageActivity extends CollapsingToolbarBaseActivity {
    static final String EXTRA_PAGE = "page";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String page = getIntent().getStringExtra(EXTRA_PAGE);
        final Fragment fragment;
        final int title;
        switch (page == null ? "" : page) {
            case "battery":
                fragment = new NeoBatteryFragment();
                title = R.string.page_battery_title;
                break;
            case "display":
                fragment = new NeoDisplayFragment();
                title = R.string.category_display;
                break;
            case "gaming":
                fragment = NeoXmlFragment.of(R.xml.neo_gaming);
                title = R.string.category_gaming;
                break;
            case "gestures":
                fragment = new NeoGesturesFragment();
                title = R.string.category_gestures;
                break;
            case "graphics":
                fragment = new NeoGraphicsFragment();
                title = R.string.page_graphics_title;
                break;
            case "security":
                fragment = new NeoSecurityFragment();
                title = R.string.page_security_title;
                break;
            default:
                finish();
                return;
        }
        setTitle(title);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            fragment)
                    .commit();
        }
    }
}
