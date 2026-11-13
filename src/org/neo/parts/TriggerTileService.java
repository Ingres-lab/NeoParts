/*
 * SPDX-FileCopyrightText: Neo
 * SPDX-License-Identifier: Apache-2.0
 */

package org.neo.parts;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: opens the trigger point overlay editor over the current app. */
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
        // Overlay editor on top of the current app; it collapses the shade itself
        TriggerOverlayService.edit(this);
    }
}
