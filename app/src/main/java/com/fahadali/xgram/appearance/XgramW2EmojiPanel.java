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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E wave 2: the emoji tone picker (EmojiToneListView, the long press on an emoji) as a Material 3 menu
 * surface: surfaceContainer, 16dp corners (the M3 Expressive menu shape), elevation level 2 (3dp) drawn as the key
 * shadow under it, no pointer arrow; the tone under the finger is the selected item in secondaryContainer with 12dp
 * corners. Main thread only; the paints are made once.
 */
public final class XgramW2EmojiPanel {
  private XgramW2EmojiPanel () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  /** Menu container corner (dp). */
  public static final float MENU_CORNER = XgramExpressive.CORNER_L;
  /** Selected item corner (dp). */
  public static final float ITEM_CORNER = XgramExpressive.CORNER_M;

  private static Paint menuPaint, itemPaint;
  private static int menuShadowRadius = -1;
  private static final RectF rect = new RectF();

  /**
   * One row of the picker (bounds without the old bubble's shadow margins). The shadow is the elevation level 2 key
   * shadow: 3dp blur, 1dp down, black at 15%.
   */
  public static void drawMenuRow (@NonNull Canvas c, float left, float top, float right, float bottom) {
    if (right <= left || bottom <= top) {
      return;
    }
    if (menuPaint == null) {
      menuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      menuPaint.setStyle(Paint.Style.FILL);
    }
    final int shadowRadius = Screen.dp(XgramExpressive.ELEVATION_2);
    if (menuShadowRadius != shadowRadius) {
      menuShadowRadius = shadowRadius;
      menuPaint.setShadowLayer(shadowRadius, 0, Screen.dp(1f), 0x26000000);
    }
    menuPaint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER));
    final float radius = Math.min(Screen.dpf(MENU_CORNER), Math.min(right - left, bottom - top) / 2f);
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, menuPaint);
  }

  /** The tone under the finger. */
  public static void drawSelectedItem (@NonNull Canvas c, float left, float top, float right, float bottom) {
    if (right <= left || bottom <= top) {
      return;
    }
    if (itemPaint == null) {
      itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      itemPaint.setStyle(Paint.Style.FILL);
    }
    itemPaint.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
    final float radius = Math.min(Screen.dpf(ITEM_CORNER), Math.min(right - left, bottom - top) / 2f);
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, itemPaint);
  }
}
