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

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive helpers for the header, sheet and input bar parts.
 *
 * A standard icon button has no container: only its state layer shows. The state layer is a
 * box (40dp for the small icon button) centered in the touch target. It has full corners at
 * rest and morphs to small (8dp) corners while pressed, with the spatial fast spring.
 */
public final class XgramExpressiveC {
  private XgramExpressiveC () { }

  public static final float ICON_BUTTON_BOX = 40f;

  /** Ripple whose mask is the morphing state layer box. The ripple color is the content color at the pressed state-layer opacity. */
  public static RippleDrawable iconButtonRipple (@ColorInt int contentColor, float boxDp) {
    return new RippleDrawable(ColorStateList.valueOf(stateLayerColor(contentColor)), null, new PressMorphMask(boxDp, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S));
  }

  public static @ColorInt int stateLayerColor (@ColorInt int contentColor) {
    return XgramExpressive.withAlpha(contentColor, XgramExpressive.STATE_PRESSED);
  }

  /** Corner radius in px between resting and pressed, for views that draw their own container. progress 0 = resting, 1 = pressed; may overshoot. */
  public static float morphCorner (float restingDp, float pressedDp, float width, float height, float progress) {
    float resting = XgramExpressive.cornerPx(restingDp, width, height);
    float pressed = XgramExpressive.cornerPx(pressedDp, width, height);
    float radius = resting + (pressed - resting) * progress;
    return Math.max(0f, Math.min(radius, Math.min(width, height) / 2f));
  }

  /** Opaque mask shape for a RippleDrawable: a centered box whose corners spring between two tokens on press. */
  public static final class PressMorphMask extends Drawable {
    private final float boxDp, restingDp, pressedDp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final XgramExpressive.Spring progress;
    private boolean pressed;

    public PressMorphMask (float boxDp, float restingDp, float pressedDp) {
      this.boxDp = boxDp;
      this.restingDp = restingDp;
      this.pressedDp = pressedDp;
      this.paint.setColor(0xffffffff);
      this.progress = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidateSelf());
      this.progress.setThreshold(.005f);
    }

    @Override
    public boolean isStateful () {
      return true;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] state) {
      boolean isPressed = false;
      for (int item : state) {
        if (item == android.R.attr.state_pressed) {
          isPressed = true;
          break;
        }
      }
      if (this.pressed != isPressed) {
        this.pressed = isPressed;
        progress.animateTo(isPressed ? 1f : 0f);
        return true;
      }
      return false;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      float size = Screen.dp(boxDp);
      float width = Math.min(size, bounds.width());
      float height = Math.min(size, bounds.height());
      float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
      rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
      float radius = morphCorner(restingDp, pressedDp, width, height, progress.getValue());
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
