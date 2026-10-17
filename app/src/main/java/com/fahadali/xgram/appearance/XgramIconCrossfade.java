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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Drawables;

import me.vkryl.core.MathUtils;

/**
 * XGram M3E: the rest (outlined) to active (filled) icon crossfade of the navigation bars, button groups and tabs that
 * draw their own icons, without the blink.
 *
 * <p>Why the icons blinked: Drawables.draw hands the paint's alpha to the drawable (Drawable.setAlpha). A
 * VectorDrawable keeps that alpha on its native tree as a render thread property, synced once per frame, and HWUI
 * draws every use of the tree in that frame with the alpha set last: the alpha recorded with each draw is replaced by
 * it. The bars drew one drawable twice per frame, the rest pass at full alpha and the active pass at the selection
 * fraction, so both passes came out at the selection fraction: as a switch started (the new item) and as it ended (the
 * old one), that fraction is near 0 and the whole icon vanished for those frames, then came back.
 *
 * <p>Here the drawables are always drawn at full alpha (their color filter, which HWUI does record with each draw,
 * carries the color) and every fade is an offscreen layer: the rest drawing at 1 - f and the active one at f, added
 * (PLUS) inside one layer, so the sum stays opaque where they overlap (no see-through dip mid-fade) and each drawing
 * still goes as it should where it is alone. Callers keep one drawable per state (a second instance even when both
 * states use the same resource). The layers exist only mid-fade; at rest an icon is one plain draw. Main thread only.
 */
public final class XgramIconCrossfade {
  private XgramIconCrossfade () { }

  private static final RectF bounds = new RectF();
  private static @Nullable Paint plusPaint;

  private static Paint plusPaint (int alpha) {
    Paint paint = plusPaint;
    if (paint == null) {
      paint = new Paint();
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
      plusPaint = paint;
    }
    paint.setAlpha(alpha);
    return paint;
  }

  /**
   * Draws {@code rest} and {@code active} centred on ({@code cx}, {@code cy}), crossfaded by {@code factor} (0 rest,
   * 1 active), the whole icon at {@code alpha}. The paints carry the colors (a color filter); their alpha is ignored.
   * {@code active} may be null (the rest drawing only, still faded by {@code alpha}).
   */
  public static void draw (Canvas c, @Nullable Drawable rest, @Nullable Paint restPaint, @Nullable Drawable active, @Nullable Paint activePaint, float cx, float cy, float factor, float alpha) {
    factor = MathUtils.clamp(factor);
    alpha = MathUtils.clamp(alpha);
    if (alpha <= 0f) {
      return;
    }
    if (active == null || (rest != null && factor <= 0f)) {
      drawSingle(c, rest, restPaint, cx, cy, alpha);
      return;
    }
    if (rest == null || factor >= 1f) {
      drawSingle(c, active, activePaint, cx, cy, alpha);
      return;
    }
    setBounds(rest, active, cx, cy);
    final int outer = alpha < 1f ? c.saveLayerAlpha(bounds, Math.round(255f * alpha)) : c.saveLayer(bounds, null);
    final int restLayer = c.saveLayerAlpha(bounds, Math.round(255f * (1f - factor)));
    drawOpaque(c, rest, restPaint, cx, cy);
    c.restoreToCount(restLayer);
    final int activeLayer = c.saveLayer(bounds, plusPaint(Math.round(255f * factor)));
    drawOpaque(c, active, activePaint, cx, cy);
    c.restoreToCount(activeLayer);
    c.restoreToCount(outer);
  }

  /** One drawing at {@code alpha}: a plain draw when opaque, through a layer otherwise. */
  public static void drawSingle (Canvas c, @Nullable Drawable d, @Nullable Paint paint, float cx, float cy, float alpha) {
    if (d == null || alpha <= 0f) {
      return;
    }
    if (alpha >= 1f) {
      drawOpaque(c, d, paint, cx, cy);
      return;
    }
    setBounds(d, null, cx, cy);
    final int layer = c.saveLayerAlpha(bounds, Math.round(255f * alpha));
    drawOpaque(c, d, paint, cx, cy);
    c.restoreToCount(layer);
  }

  private static void drawOpaque (Canvas c, Drawable d, @Nullable Paint paint, float cx, float cy) {
    // On whole pixels, as the icons were drawn before, so the cached vector bitmap stays crisp
    final float x = Math.round(cx - d.getMinimumWidth() / 2f), y = Math.round(cy - d.getMinimumHeight() / 2f);
    if (paint != null) {
      final int alpha = paint.getAlpha();
      if (alpha != 255) {
        paint.setAlpha(255);
      }
      Drawables.draw(c, d, x, y, paint);
      if (alpha != 255) {
        paint.setAlpha(alpha);
      }
    } else {
      if (d.getAlpha() != 255) {
        d.setAlpha(255);
      }
      Drawables.draw(c, d, x, y, null);
    }
  }

  private static void setBounds (Drawable a, @Nullable Drawable b, float cx, float cy) {
    float halfWidth = a.getMinimumWidth() / 2f, halfHeight = a.getMinimumHeight() / 2f;
    if (b != null) {
      halfWidth = Math.max(halfWidth, b.getMinimumWidth() / 2f);
      halfHeight = Math.max(halfHeight, b.getMinimumHeight() / 2f);
    }
    // 1px of slack for the antialiased edges
    bounds.set(cx - halfWidth - 1f, cy - halfHeight - 1f, cx + halfWidth + 1f, cy + halfHeight + 1f);
  }
}
