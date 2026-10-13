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
package com.fahadali.xgram.history;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;

/**
 * "Show deleted messages in red": the text and captions of messages kept after the sender deleted them are drawn in
 * the theme's red, made readable on the bubble (or chat background) right behind them. Links and other clickable
 * parts keep their own colors. Everything is worked out while drawing, so theme changes and the toggle apply at once.
 */
public final class XgramDeletedColor {
  private XgramDeletedColor () { }

  /** WCAG AA for normal text; 3:1 is the floor when a theme leaves no room for more. */
  private static final double TARGET_CONTRAST = 4.5, MIN_CONTRAST = 3.0;

  public static boolean isEnabled () {
    return XgramSettings.instance().historyRedDeleted();
  }

  /** The color set for a message's own text or caption: red once it is deleted and the setting is on. */
  public static TextColorSet wrap (TGMessage message, TextColorSet colorSet) {
    return new TextColorSetOverride(colorSet) {
      @Override
      public int defaultTextColor () {
        int color = super.defaultTextColor();
        if (isEnabled() && message.xgramIsDeleted()) {
          return deletedTextColor(message.getContentBackgroundColor(), color);
        }
        return color;
      }
    };
  }

  // Color

  private static final int CACHE_SIZE = 4;
  private static final int[] cacheBackground = new int[CACHE_SIZE], cacheText = new int[CACHE_SIZE], cacheRed = new int[CACHE_SIZE], cacheResult = new int[CACHE_SIZE];
  private static final boolean[] cacheUsed = new boolean[CACHE_SIZE];
  private static int cacheNext;

  /**
   * @param background what is behind the text, possibly translucent (a bubble over the chat background)
   * @param textColor the color the text would have, it tells which side (light or dark) the theme reads on
   */
  @ColorInt
  public static int deletedTextColor (@ColorInt int background, @ColorInt int textColor) {
    int red = Theme.getColor(ColorId.textNegative);
    synchronized (cacheResult) {
      for (int i = 0; i < CACHE_SIZE; i++) {
        if (cacheUsed[i] && cacheBackground[i] == background && cacheText[i] == textColor && cacheRed[i] == red) {
          return cacheResult[i];
        }
      }
      int result = compute(background, textColor, red);
      int i = cacheNext;
      cacheNext = (cacheNext + 1) % CACHE_SIZE;
      cacheBackground[i] = background;
      cacheText[i] = textColor;
      cacheRed[i] = red;
      cacheResult[i] = result;
      cacheUsed[i] = true;
      return result;
    }
  }

  private static int compute (int background, int textColor, int red) {
    int bg = background;
    if (Color.alpha(bg) < 255) {
      int chat = Theme.getColor(ColorId.chatBackground) | 0xFF000000;
      bg = ColorUtils.compositeColors(bg, chat);
      if (Color.alpha(bg) < 255) {
        bg = ColorUtils.compositeColors(bg, Color.BLACK);
      }
    }
    textColor = ColorUtils.compositeColors(textColor | 0xFF000000, bg);
    red |= 0xFF000000;

    // Which way to go: the side the theme's own text reads on.
    boolean goLighter = ColorUtils.calculateLuminance(textColor) > ColorUtils.calculateLuminance(bg);

    float[] hsl = new float[3];
    ColorUtils.colorToHSL(red, hsl);
    if (hsl[1] < .5f || !isRedHue(hsl[0])) {
      // A theme whose "negative" is not much of a red still has to read as deleted.
      hsl[0] = 356f;
      hsl[1] = Math.max(hsl[1], .75f);
    }

    float[] bgHsl = new float[3];
    ColorUtils.colorToHSL(bg, bgHsl);
    boolean redBackground = isRedHue(bgHsl[0]) && bgHsl[1] >= .25f && bgHsl[2] > .12f && bgHsl[2] < .92f;
    if (redBackground) {
      // Red on a red or pink bubble: a fully saturated red pushed far from the bubble's lightness, so it stands out
      // from both the bubble and the normal text (deep crimson on light pink, light rose on dark red).
      hsl[0] = 352f;
      hsl[1] = 1f;
      hsl[2] = goLighter ? Math.max(hsl[2], .80f) : Math.min(hsl[2], .30f);
    }

    int color = ColorUtils.HSLToColor(hsl);
    double contrast = ColorUtils.calculateContrast(color, bg);
    int best = color;
    double bestContrast = contrast;
    float step = .02f;
    while (contrast < TARGET_CONTRAST) {
      float lightness = hsl[2] + (goLighter ? step : -step);
      if (lightness < 0f || lightness > 1f) {
        break;
      }
      hsl[2] = lightness;
      color = ColorUtils.HSLToColor(hsl);
      contrast = ColorUtils.calculateContrast(color, bg);
      if (contrast > bestContrast) {
        best = color;
        bestContrast = contrast;
      }
    }
    if (bestContrast < MIN_CONTRAST) {
      // The theme's text side was the wrong guess for this color (a mid-tone background): try the other way.
      ColorUtils.colorToHSL(best, hsl);
      for (float lightness = hsl[2]; lightness >= 0f && lightness <= 1f; lightness += goLighter ? -step : step) {
        hsl[2] = lightness;
        color = ColorUtils.HSLToColor(hsl);
        contrast = ColorUtils.calculateContrast(color, bg);
        if (contrast > bestContrast) {
          best = color;
          bestContrast = contrast;
          if (contrast >= TARGET_CONTRAST) {
            break;
          }
        }
      }
    }
    return best;
  }

  private static boolean isRedHue (float hue) {
    return hue >= 330f || hue <= 15f;
  }
}
