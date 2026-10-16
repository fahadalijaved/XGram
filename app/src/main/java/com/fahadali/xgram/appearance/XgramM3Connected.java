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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;

/**
 * Connected (segmented) button blocks, Material 3 Expressive: the bot reply keyboard and inline keyboards. The cells
 * form a grid whose rows each span the full width, 2dp apart both ways. The block's four outer corners are 20dp (the
 * bubble radius), every inner corner 8dp, and a lone cell is a pill. While a cell is pressed all of its corners spring
 * to 4dp (8dp for a pill).
 * <p>
 * Corner arrays are in physical order: top left, top right, bottom right, bottom left. Both keyboards lay their cells
 * out left to right whatever the language direction, so the cell shape follows the column, not the text direction.
 */
public final class XgramM3Connected {
  private XgramM3Connected () { }

  /** Gap between cells, both ways (dp). */
  public static final float GAP = 2f;
  /** Outer corners of the block (dp). */
  public static final float OUTER = XgramExpressive.CORNER_L_INC;
  /** Inner corners (dp). */
  public static final float INNER = XgramExpressive.CORNER_S;
  /** Pressed corner of a cell in a group (dp). */
  public static final float PRESSED = XgramExpressive.CORNER_XS;
  /** Pressed corner of a lone (pill) cell (dp). */
  public static final float PRESSED_SINGLE = XgramExpressive.CORNER_S;

  public static boolean isSingle (int rowCount, int colCount) {
    return rowCount <= 1 && colCount <= 1;
  }

  public static float pressedCorner (boolean single) {
    return single ? PRESSED_SINGLE : PRESSED;
  }

  /** Resting corners (dp, {@link XgramExpressive#CORNER_FULL} for a pill) of cell (row, col) in a block of {@code rowCount} rows; {@code colCount} is the cell count of its own row. */
  public static void radii (int row, int col, int rowCount, int colCount, @NonNull float[] outDp) {
    radii(isSingle(rowCount, colCount), row == 0, row == rowCount - 1, col, colCount, outDp);
  }

  /** As {@link #radii(int, int, int, int, float[])}; {@code single} forces a pill (a standalone button). */
  public static void radii (boolean single, boolean firstRow, boolean lastRow, int col, int colCount, @NonNull float[] outDp) {
    if (single) {
      outDp[0] = outDp[1] = outDp[2] = outDp[3] = XgramExpressive.CORNER_FULL;
      return;
    }
    final boolean first = col == 0, last = col == colCount - 1;
    outDp[0] = firstRow && first ? OUTER : INNER;
    outDp[1] = firstRow && last ? OUTER : INNER;
    outDp[2] = lastRow && last ? OUTER : INNER;
    outDp[3] = lastRow && first ? OUTER : INNER;
  }

  /**
   * Path radii ({@code float[8]}, px) for a {@code w} x {@code h} cell: the resting corners moved towards
   * {@code pressedDp} by {@code press} (0 resting, 1 pressed; the spring may overshoot), clamped to a pill.
   */
  public static void radiiPx (@NonNull float[] cornersDp, float w, float h, float pressedDp, float press, @NonNull float[] outPx) {
    final float max = Math.max(0f, Math.min(w, h) / 2f);
    final float pressedPx = XgramExpressive.cornerPx(pressedDp, w, h);
    for (int i = 0; i < 4; i++) {
      float v = XgramExpressive.cornerPx(cornersDp[i], w, h);
      if (press != 0f) {
        v += (pressedPx - v) * press;
      }
      v = Math.max(0f, Math.min(max, v));
      outPx[i * 2] = v;
      outPx[i * 2 + 1] = v;
    }
  }

  /**
   * A connected button view: a morphing container in {@code containerRole} (resolved at draw time, so it follows
   * theme changes) with a ripple in the same shape. Shape it with {@link #shape} once its place in the block is known.
   */
  @NonNull
  public static XgramExpressive.MorphDrawable attachButton (@NonNull View view, int containerRole) {
    XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(view, XgramExpressive.color(containerRole), INNER, PRESSED);
    morph.setColorRole(containerRole);
    return morph;
  }

  /** Shapes the button view's morph as cell (col) of a row; call at bind or layout time, never per frame. */
  public static void shape (@Nullable View view, boolean firstRow, boolean lastRow, int col, int colCount) {
    shape(view, firstRow, lastRow, col, colCount, true);
  }

  /**
   * As {@link #shape(View, boolean, boolean, int, int)}; with {@code allowPill} false a lone cell keeps the block's
   * outer corners (20dp, pressed 4dp) instead of becoming a pill: a large cell (a bot keyboard that fills the keyboard
   * area) would otherwise get corners of half its height.
   */
  public static void shape (@Nullable View view, boolean firstRow, boolean lastRow, int col, int colCount, boolean allowPill) {
    if (view == null) {
      return;
    }
    shape(XgramExpressive.findMorph(view.getBackground()), firstRow, lastRow, col, colCount, allowPill);
  }

  public static void shape (@Nullable XgramExpressive.MorphDrawable morph, boolean firstRow, boolean lastRow, int col, int colCount) {
    shape(morph, firstRow, lastRow, col, colCount, true);
  }

  public static void shape (@Nullable XgramExpressive.MorphDrawable morph, boolean firstRow, boolean lastRow, int col, int colCount, boolean allowPill) {
    if (morph == null) {
      return;
    }
    final boolean lone = firstRow && lastRow && colCount <= 1;
    final boolean single = allowPill && lone;
    float tl, tr, br, bl;
    if (single) {
      tl = tr = br = bl = XgramExpressive.CORNER_FULL;
    } else if (lone) {
      tl = tr = br = bl = OUTER; // the whole block: its four outer corners
    } else {
      final boolean first = col == 0, last = col == colCount - 1;
      tl = firstRow && first ? OUTER : INNER;
      tr = firstRow && last ? OUTER : INNER;
      br = lastRow && last ? OUTER : INNER;
      bl = lastRow && first ? OUTER : INNER;
    }
    // MorphDrawable corners are start / end; the cells are placed left to right, so swap them under RTL
    final boolean rtl = Lang.rtl();
    morph.setCorners(rtl ? tr : tl, rtl ? tl : tr, rtl ? bl : br, rtl ? br : bl);
    morph.setPressedCorner(pressedCorner(single));
  }
}
