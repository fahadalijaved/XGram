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
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E: the {@link XgramLoadingIndicator} as a drawable (a title's leading element). While running it asks its
 * host for the next frame after each draw, so the host view redraws on vsync and nothing else does.
 */
public final class XgramLoadingIndicatorDrawable extends Drawable implements Animatable {
  private final int sizePx;
  private final int colorRole;
  private @ColorInt int colorOverride;
  private long startTime;
  private boolean running;
  private int alpha = 255;

  public XgramLoadingIndicatorDrawable (float boxDp, int colorRole) {
    this.sizePx = Screen.dp(boxDp);
    this.colorRole = colorRole;
    setBounds(0, 0, sizePx, sizePx);
  }

  /** A fixed color in place of the role's (the call screen's status over video: white), or 0 for the role again. */
  public void setColorOverride (@ColorInt int color) {
    if (this.colorOverride != color) {
      this.colorOverride = color;
      invalidateSelf();
    }
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect b = getBounds();
    if (b.isEmpty()) {
      return;
    }
    if (startTime == 0L) {
      startTime = SystemClock.uptimeMillis();
    }
    int color = XgramExpressive.withAlpha(colorOverride != 0 ? colorOverride : XgramExpressive.color(colorRole), alpha / 255f);
    float size = Math.min(b.width(), b.height()) * XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE;
    XgramLoadingIndicator.draw(c, b.exactCenterX(), b.exactCenterY(), size, color, SystemClock.uptimeMillis() - startTime);
    if (running) {
      invalidateSelf();
    }
  }

  @Override
  public void start () {
    if (!running) {
      running = true;
      startTime = 0L;
      invalidateSelf();
    }
  }

  @Override
  public void stop () {
    running = false;
  }

  @Override
  public boolean isRunning () {
    return running;
  }

  @Override
  public int getIntrinsicWidth () {
    return sizePx;
  }

  @Override
  public int getIntrinsicHeight () {
    return sizePx;
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
}
