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
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * The Material 3 Expressive unread divider of the chat ("New messages", and "Discussion started" under a channel
 * post): a small chip with full corners centred in the row, like the date chips (26dp, labelMedium, primary text on
 * surfaceContainerHigh), with an outlineVariant hairline on each side of it. The row keeps Telegram X's height, so
 * the counts and the scroll anchor of the first unread message do not change. Only drawn with Expressive design.
 */
public final class XgramM3UnreadDivider {
  private XgramM3UnreadDivider () { }

  private static final float CHIP_HEIGHT = XgramR5Chat.CHIP_HEIGHT, PADDING = XgramR5Chat.CHIP_PADDING, ICON_GAP = 2f, ICON_END_PADDING = 4f,
    LINE_INSET = 16f, LINE_GAP = 8f, LINE_WIDTH = 1f;

  /**
   * The divider's label: with Expressive design, "New messages" in sentence case while the language is Telegram X's
   * English (its own string is title case); a translation is kept as it is.
   */
  @NonNull
  public static String newMessages () {
    final String tgx = Lang.getString(R.string.NewMessages);
    if (!XgramExpressive.isEnabled()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(R.string.NewMessages))) {
        return Lang.getString(R.string.XgramM3NewMessages);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  /**
   * Draws the divider in the badge row from {@code top} to {@code bottom} of a message {@code width} wide: the chip
   * holding {@code text} ({@code textWidth} px, measured with XgramM3Chat.dateMeasurePaint) and, when {@code icon} is
   * set, the chevron after it in {@code iconPaint}; then the hairlines from 16dp in to 8dp short of the chip.
   */
  public static void draw (@NonNull Canvas c, int width, int top, int bottom, @NonNull String text, float textWidth,
                           @Nullable Drawable icon, @Nullable Paint iconPaint, @ColorInt int chipColor, @ColorInt int textColor) {
    draw(c, width, top, bottom, text, textWidth, icon, iconPaint, chipColor, textColor, 1f);
  }

  /**
   * Round 5: the tonal chat chip (the date chip's size and type, {@code chipColor} secondaryContainer and
   * {@code textColor} onSecondaryContainer) without the chevron, drawn at {@code alpha} (the divider fades out once
   * the chat is read).
   */
  public static void draw (@NonNull Canvas c, int width, int top, int bottom, @NonNull String text, float textWidth,
                           @ColorInt int chipColor, @ColorInt int textColor, float alpha) {
    draw(c, width, top, bottom, text, textWidth, null, null, chipColor, textColor, alpha);
  }

  private static void draw (@NonNull Canvas c, int width, int top, int bottom, @NonNull String text, float textWidth,
                            @Nullable Drawable icon, @Nullable Paint iconPaint, @ColorInt int chipColor, @ColorInt int textColor, float alpha) {
    if (alpha <= 0f) {
      return;
    }
    if (alpha < 1f) {
      chipColor = XgramExpressive.withAlpha(chipColor, alpha);
      textColor = XgramExpressive.withAlpha(textColor, alpha);
    }
    final float centerY = (top + bottom) / 2f;
    final float halfHeight = Screen.dp(CHIP_HEIGHT) / 2f;
    final float contentWidth = textWidth + (icon != null ? Screen.dp(ICON_GAP) + icon.getMinimumWidth() : 0);
    final float chipWidth = Screen.dp(PADDING) + contentWidth + Screen.dp(icon != null ? ICON_END_PADDING : PADDING);
    final float chipLeft = width / 2f - chipWidth / 2f, chipRight = chipLeft + chipWidth;

    final int lineWidth = Math.max(1, Screen.dp(LINE_WIDTH));
    final float lineTop = Math.round(centerY - lineWidth / 2f);
    final Paint linePaint = Paints.fillingPaint(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT), alpha));
    final float startLeft = Screen.dp(LINE_INSET), startRight = chipLeft - Screen.dp(LINE_GAP);
    if (startRight > startLeft) {
      c.drawRect(startLeft, lineTop, startRight, lineTop + lineWidth, linePaint);
    }
    final float endLeft = chipRight + Screen.dp(LINE_GAP), endRight = width - Screen.dp(LINE_INSET);
    if (endRight > endLeft) {
      c.drawRect(endLeft, lineTop, endRight, lineTop + lineWidth, linePaint);
    }

    if (((chipColor >>> 24) & 0xff) != 0) {
      final RectF rect = Paints.getRectF();
      rect.set(chipLeft, centerY - halfHeight, chipRight, centerY + halfHeight);
      c.drawRoundRect(rect, halfHeight, halfHeight, Paints.fillingPaint(chipColor));
    }
    final TextPaint textPaint = XgramM3Chat.datePaint(textColor);
    final float textLeft = chipLeft + Screen.dp(PADDING);
    c.drawText(text, textLeft, centerY - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    if (icon != null) {
      Drawables.draw(c, icon, textLeft + textWidth + Screen.dp(ICON_GAP), centerY - icon.getMinimumHeight() / 2f, iconPaint);
    }
  }
}
