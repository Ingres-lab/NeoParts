/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: opens the trigger point editor over the current app. */
public class TriggerTileService extends TileService {

    @Override
    public void onStartListening() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        final boolean enabled = TriggerConfig.isEnabled();
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(enabled ? R.string.trigger_tile_on : R.string.trigger_tile_off));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        final Intent intent = new Intent(this, TriggerEditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
