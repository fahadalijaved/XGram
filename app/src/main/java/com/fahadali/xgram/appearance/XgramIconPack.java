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

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * In-app icon packs (exteraGram, AyuGram). Every Telegram X icon goes through {@code Drawables.load}, which asks
 * {@link #map} for the pack's replacement; icons a pack has no drawing for stay Telegram X's. Icons drawn once are
 * cached in many places, so a new pack applies after a restart. Packs come from tools/xgram/icon_packs/build.py.
 */
public final class XgramIconPack {
  private XgramIconPack () { }

  public static final String KEY = "xgram_icon_pack";

  public static final int PACK_TELEGRAM_X = 0;
  /** XGram's own rounded line icons. */
  public static final int PACK_LINE = 1;
  /** Solar Icons by 480 Design (CC BY 4.0), bold duotone: the look of exteraGram and AyuGram. */
  public static final int PACK_SOLAR = 2;
  /** Phosphor Icons (MIT), bold. */
  public static final int PACK_PHOSPHOR = 3;
  /** MingCute (Apache 2.0), line. */
  public static final int PACK_MINGCUTE = 4;
  /** Pixelarticons by Gerrit Halfmann (MIT): pixel art. */
  public static final int PACK_PIXEL = 5;
  /** XGram's own lines with a soft filled body. */
  public static final int PACK_DUOTONE = 6;

  public static final int[] PACKS = {PACK_TELEGRAM_X, PACK_DUOTONE, PACK_SOLAR, PACK_PHOSPHOR, PACK_MINGCUTE, PACK_PIXEL, PACK_LINE};

  private static volatile SparseIntArray map;

  public static int current () {
    int pack = Settings.instance().pmc().getInt(KEY, PACK_TELEGRAM_X);
    return pack >= PACK_TELEGRAM_X && pack <= PACK_DUOTONE ? pack : PACK_TELEGRAM_X;
  }

  public static void set (int pack) {
    Settings.instance().pmc().putInt(KEY, pack);
    // The running app keeps its map: drawables already made would not change anyway.
  }

  public static @StringRes int name (int pack) {
    switch (pack) {
      case PACK_LINE: return R.string.XgramIconPackLine;
      case PACK_DUOTONE: return R.string.XgramIconPackDuotone;
      case PACK_SOLAR: return R.string.XgramIconPackSolar;
      case PACK_PHOSPHOR: return R.string.XgramIconPackPhosphor;
      case PACK_MINGCUTE: return R.string.XgramIconPackMingCute;
      case PACK_PIXEL: return R.string.XgramIconPackPixel;
      default: return R.string.XgramIconPackClassic;
    }
  }

  public static @StringRes int description (int pack) {
    switch (pack) {
      case PACK_LINE: return R.string.XgramIconPackLineInfo;
      case PACK_DUOTONE: return R.string.XgramIconPackDuotoneInfo;
      case PACK_SOLAR: return R.string.XgramIconPackSolarInfo;
      case PACK_PHOSPHOR: return R.string.XgramIconPackPhosphorInfo;
      case PACK_MINGCUTE: return R.string.XgramIconPackMingCuteInfo;
      case PACK_PIXEL: return R.string.XgramIconPackPixelInfo;
      default: return R.string.XgramIconPackClassicInfo;
    }
  }

  @Nullable
  static int[] table (int pack) {
    switch (pack) {
      case PACK_LINE: return XgramIconPackTable.LINE;
      case PACK_DUOTONE: return XgramIconPackTable.DUOTONE;
      case PACK_SOLAR: return XgramIconPackTable.SOLAR;
      case PACK_PHOSPHOR: return XgramIconPackTable.PHOSPHOR;
      case PACK_MINGCUTE: return XgramIconPackTable.MINGCUTE;
      case PACK_PIXEL: return XgramIconPackTable.PIXEL;
      default: return XgramIconPackTable.classic();
    }
  }

  static int[] preview (int pack) {
    switch (pack) {
      case PACK_LINE: return XgramIconPackTable.PREVIEW_LINE;
      case PACK_DUOTONE: return XgramIconPackTable.PREVIEW_DUOTONE;
      case PACK_SOLAR: return XgramIconPackTable.PREVIEW_SOLAR;
      case PACK_PHOSPHOR: return XgramIconPackTable.PREVIEW_PHOSPHOR;
      case PACK_MINGCUTE: return XgramIconPackTable.PREVIEW_MINGCUTE;
      case PACK_PIXEL: return XgramIconPackTable.PREVIEW_PIXEL;
      default: return XgramIconPackTable.PREVIEW_TGX;
    }
  }

  /** The drawable to load instead of {@code res}, or {@code res} itself. */
  public static int map (int res) {
    SparseIntArray map = XgramIconPack.map;
    if (map == null) {
      map = build();
    }
    return map.size() == 0 ? res : map.get(res, res);
  }

  private static synchronized SparseIntArray build () {
    if (map != null) {
      return map;
    }
    SparseIntArray result = new SparseIntArray();
    int[] table = table(current());
    if (table != null) {
      for (int i = 0; i + 1 < table.length; i += 2) {
        result.put(table[i], table[i + 1]);
      }
    }
    map = result;
    return result;
  }
}
