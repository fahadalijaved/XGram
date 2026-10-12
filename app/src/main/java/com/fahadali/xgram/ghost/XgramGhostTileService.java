/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.ghost;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import com.fahadali.xgram.XgramSettings;

/** Quick Settings tile that turns ghost mode on and off (NagramX / exteraGram quick toggle). */
@RequiresApi(Build.VERSION_CODES.N)
public final class XgramGhostTileService extends TileService {
  @Override
  public void onStartListening () {
    super.onStartListening();
    updateTile();
  }

  @Override
  public void onClick () {
    super.onClick();
    XgramSettings settings = XgramSettings.instance();
    settings.setGhostModeEnabled(!settings.isAnyGhostEnabled());
    try {
      XgramGhost.applyOnlineState();
    } catch (Throwable ignored) {
      // Accounts may not be loaded yet if only the tile woke the app up; they read the setting when they start.
    }
    updateTile();
  }

  private void updateTile () {
    Tile tile = getQsTile();
    if (tile == null) {
      return;
    }
    tile.setState(XgramSettings.instance().isAnyGhostEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
    tile.updateTile();
  }
}
