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
package com.fahadali.xgram.appearance;

import android.util.SparseIntArray;

/**
 * The icons of XGram's Compose settings pages (com.fahadali.xgram.compose: XGram's own pages and Telegram X pages drawn
 * in Compose). The user's icon pack first ({@link XgramIconPack#map}). With the Classic pack under Material 3 Expressive
 * every other icon comes from the XGram Line family too (outlined, rounded, the family the Classic pack already uses
 * for the message sheet), so a page never mixes a filled Telegram X icon with outlined ones. Built once, as the pack's
 * own map: a new pack or design applies after a restart.
 */
public final class XgramComposeSettingsIcons {
  private XgramComposeSettingsIcons () { }

  private static volatile SparseIntArray line;

  /** The drawable a Compose settings page draws for {@code res}. */
  public static int map (int res) {
    int mapped = XgramIconPack.map(res);
    if (mapped != res) {
      return mapped;
    }
    SparseIntArray table = line;
    if (table == null) {
      table = build();
    }
    return table.size() == 0 ? res : table.get(res, res);
  }

  private static synchronized SparseIntArray build () {
    if (line != null) {
      return line;
    }
    SparseIntArray result = new SparseIntArray();
    if (XgramExpressive.isEnabled() && XgramIconPack.current() == XgramIconPack.PACK_TELEGRAM_X) {
      int[] table = XgramIconPackTable.LINE;
      for (int i = 0; i + 1 < table.length; i += 2) {
        result.put(table[i], table[i + 1]);
      }
    }
    line = result;
    return result;
  }
}
