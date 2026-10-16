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

import androidx.annotation.ColorInt;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.theme.ThemeManager;

/**
 * Pure black (AMOLED) night mode for Expressive design. While it is on and the app is dark, the Material You surfaces
 * are black and the containers above them near-black steps, so the M3 elevation order (lowest, low, container, high,
 * highest, bright) still reads. Content colors (onSurface, outlines) and the accent containers stay the dynamic
 * scheme's, and the light scheme is never changed. Everything drawn with the surface roles follows: Telegram X's colors
 * (XgramM3Colors through XgramExpressive.colorFor), the status and navigation bars, the panes, the rail, sheets, the
 * chat background without a wallpaper, and the Compose pages (XgramM3Theme).
 */
public final class XgramAmoled {
  private XgramAmoled () { }

  // Switch

  /** -1: not read yet, 0: off, 1: on. */
  private static volatile int savedState = -1;

  /** The switch is only offered with Expressive design: its colors are the ones made black. */
  public static boolean isAvailable () {
    return XgramExpressive.isEnabled();
  }

  /** The saved choice, kept while Expressive design is off. Cached. */
  public static boolean isSaved () {
    int state = savedState;
    if (state == -1) {
      state = XgramSettings.instance().pureBlack() ? 1 : 0;
      savedState = state;
    }
    return state == 1;
  }

  /** Pure black applies to the dark scheme: Expressive design and the switch are on. Cheap, safe in onDraw. */
  public static boolean isEnabled () {
    return isSaved() && XgramExpressive.isEnabled();
  }

  /**
   * Saves the switch and repaints the app at once with the new surfaces, as after a wallpaper color change: the role
   * caches are dropped and every theme listener (views, bars, Compose pages) is told the colors changed.
   */
  public static void setEnabled (boolean on) {
    XgramSettings.instance().setPureBlack(on);
    savedState = on ? 1 : 0;
    XgramExpressive.invalidateColors();
    try {
      ThemeManager.xgramNotifyColorsChanged();
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Pure black refresh failed", t);
    }
  }

  // Surfaces

  /** Steps above black keep a trace of the Material You hue: half the dynamic surface's chroma, at most this (HCT). */
  private static final float TINT_CHROMA_MAX = 2f, TINT_CHROMA_SCALE = .5f;

  /**
   * The grey level (sRGB, the same in red, green and blue) of each surface role with pure black, or -1 where the role
   * is not a surface. Black for the surface itself (and background, dim, lowest), then about the steps of the dark
   * scheme's containers: #0B0B0B, #121212, #1A1A1A, #222222 and #262626 for bright.
   */
  private static int greyOf (int role) {
    switch (role) {
      case XgramExpressive.SURFACE:
      case XgramExpressive.SURFACE_DIM:
      case XgramExpressive.SURFACE_CONTAINER_LOWEST:
        return 0x00;
      case XgramExpressive.SURFACE_CONTAINER_LOW:
        return 0x0B;
      case XgramExpressive.SURFACE_CONTAINER:
        return 0x12;
      case XgramExpressive.SURFACE_CONTAINER_HIGH:
        return 0x1A;
      case XgramExpressive.SURFACE_CONTAINER_HIGHEST:
        return 0x22;
      case XgramExpressive.SURFACE_BRIGHT:
        return 0x26;
      default:
        return -1;
    }
  }

  /** Whether pure black replaces {@code role} (the surface and surface container roles). */
  public static boolean isSurfaceRole (int role) {
    return greyOf(role) != -1;
  }

  /**
   * The color of {@code role} in the light or the dark scheme, {@code color} being the dynamic one: the pure black
   * step while it is on and {@code dark}, else {@code color} itself. Allocates only when the dynamic color changed.
   */
  @ColorInt
  public static int override (int role, @ColorInt int color, boolean dark) {
    if (!dark || !isEnabled()) {
      return color;
    }
    return pureBlack(role, color);
  }

  /** As {@link #override(int, int, boolean)}, in the scheme of the current theme (day or night). */
  @ColorInt
  public static int override (int role, @ColorInt int color) {
    if (!isEnabled()) {
      return color;
    }
    return override(role, color, ThemeManager.instance().isCurrentThemeDark());
  }

  private static final class Step {
    final int in, out;

    Step (int in, int out) {
      this.in = in;
      this.out = out;
    }
  }

  /** The last step worked out per role, for the dynamic color it was made from. */
  private static final Step[] steps = new Step[XgramExpressive.ROLE_COUNT];

  /**
   * The pure black color of {@code role} whatever the switch: black for the surface, a near-black step tinted very
   * slightly with the hue of {@code dynamicColor} (the dark scheme's own color of the role) for the containers above
   * it. Other roles keep {@code dynamicColor}.
   */
  @ColorInt
  public static int pureBlack (int role, @ColorInt int dynamicColor) {
    int grey = greyOf(role);
    if (grey == -1) {
      return dynamicColor;
    }
    if (grey == 0) {
      return 0xff000000;
    }
    Step step = steps[role];
    if (step != null && step.in == dynamicColor) {
      return step.out;
    }
    int out = tint(grey, dynamicColor);
    steps[role] = new Step(dynamicColor, out);
    return out;
  }

  /** The grey {@code grey} with the hue of {@code dynamicColor} and a trace of its chroma, at the grey's own tone. */
  @ColorInt
  private static int tint (int grey, @ColorInt int dynamicColor) {
    int plain = 0xff000000 | (grey << 16) | (grey << 8) | grey;
    try {
      float[] hct = new float[3];
      androidx.core.graphics.ColorUtils.colorToM3HCT(plain, hct);
      float tone = hct[2];
      androidx.core.graphics.ColorUtils.colorToM3HCT(dynamicColor | 0xff000000, hct);
      float chroma = Math.min(hct[1] * TINT_CHROMA_SCALE, TINT_CHROMA_MAX);
      if (chroma < .5f) {
        return plain; // a grey wallpaper scheme: nothing to tint with
      }
      return androidx.core.graphics.ColorUtils.M3HCTToColor(hct[0], chroma, tone) | 0xff000000;
    } catch (Throwable t) {
      return plain;
    }
  }
}
