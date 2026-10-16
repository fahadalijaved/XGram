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
 *
 * The look follows androidx.compose.foundation NonInteractiveScrollbarDefaults (Apache License 2.0,
 * Copyright The Android Open Source Project): a 4dp full-corner thumb, 2dp main-axis inset, 70% opacity.
 */
package com.fahadali.xgram.appearance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/**
 * Thumb of every system scrollbar with Expressive design (res/drawable/xgram_scroll_thumb.xml, applied through the
 * XgramScrollbars theme overlay). The system gives it a 10dp wide box; it draws a 4dp pill 3dp in from the outer
 * edge in onSurfaceVariant at 70%, times the fade alpha the system sets. Colors are read at draw time, so a theme
 * switch needs no listener. No constant state: every view gets its own instance, so fade alphas never leak.
 */
public class XgramScrollThumbDrawable extends Drawable {
  private static final float THICKNESS_DP = 4f, MAIN_AXIS_INSET_DP = 2f, MIN_LENGTH_DP = 24f;
  private static final float OPACITY = .7f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int alpha = 255;

  public XgramScrollThumbDrawable () { }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect b = getBounds();
    int w = b.width(), h = b.height();
    if (w <= 0 || h <= 0 || alpha == 0) {
      return;
    }
    // Centered across the 10dp box, which leaves it 3dp off the outer edge on either side (RTL included).
    float thickness = Math.min(Screen.dp(THICKNESS_DP), Math.min(w, h)), main = Screen.dp(MAIN_AXIS_INSET_DP);
    if (h >= w) {
      float cx = b.exactCenterX();
      rect.set(cx - thickness / 2f, b.top + main, cx + thickness / 2f, b.bottom - main);
    } else {
      float cy = b.exactCenterY();
      rect.set(b.left + main, cy - thickness / 2f, b.right - main, cy + thickness / 2f);
    }
    if (rect.width() <= 0f || rect.height() <= 0f) {
      return;
    }
    paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), OPACITY * (alpha / 255f)));
    float r = Math.min(rect.width(), rect.height()) / 2f;
    c.drawRoundRect(rect, r, r, paint);
  }

  @Override
  public int getMinimumHeight () {
    return Screen.dp(MIN_LENGTH_DP);
  }

  @Override
  public int getMinimumWidth () {
    return Screen.dp(MIN_LENGTH_DP);
  }

  @Override
  public void setAlpha (int alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidateSelf();
    }
  }

  @Override
  public int getAlpha () {
    return alpha;
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) { }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }

  @Nullable
  @Override
  public ConstantState getConstantState () {
    return null;
  }
}
