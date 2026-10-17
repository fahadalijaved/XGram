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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Material 3 Expressive state layer of a pill tab, a chip or a rail item, as the view's background: the pressed,
 * hovered and focused layer (a ripple) inside the item's own shape, never the view's rectangle. Its color is the
 * content color at the pressed state-layer opacity (.10): onSecondaryContainer while the item sits on its selected
 * secondaryContainer indicator, onSurface otherwise. The color is read again on every state change, so day / night,
 * theme and dynamic color switches need no listener.
 *
 * The shape is asked again on every frame the layer draws, so an item whose pill changes (a tab label that grows, the
 * message options page's chips turning into tabs) presses in its current shape. Use it only with Expressive design on.
 */
public final class XgramPillStateLayer extends RippleDrawable {
  /** Corner radius for full corners: half the shorter side of the shape. */
  public static final float CORNER_FULL = -1f;

  /** Where the state layer of an item goes. Called on the main thread while the view draws or changes state. */
  public interface Shape {
    /**
     * Sets {@code outRect} to the item's shape in {@code view}'s own coordinates (empty: no state layer) and returns
     * its corner radius in pixels, or {@link #CORNER_FULL}.
     */
    float getStateLayerShape (@NonNull View view, @NonNull RectF outRect);

    /** True while the item is the selected one, on its secondaryContainer indicator. */
    default boolean isStateLayerOnIndicator (@NonNull View view) {
      return false;
    }
  }

  private final View view;
  private final Shape shape;
  private final ShapeMask mask;
  private final RectF nextShape = new RectF();
  private int color;
  /** False while RippleDrawable's constructor runs (the fields above are not set yet). */
  private final boolean ready;

  private XgramPillStateLayer (@NonNull View view, @NonNull Shape shape, @NonNull ShapeMask mask, int color) {
    super(ColorStateList.valueOf(color), null, mask);
    this.view = view;
    this.shape = shape;
    this.mask = mask;
    this.color = color;
    this.ready = true;
  }

  /** Sets the state layer as {@code view}'s background. */
  public static XgramPillStateLayer attach (@NonNull View view, @NonNull Shape shape) {
    XgramPillStateLayer layer = new XgramPillStateLayer(view, shape, new ShapeMask(), XgramExpressiveC.stateLayerColor(XgramExpressive.color(XgramExpressive.ON_SURFACE)));
    view.setBackground(layer);
    return layer;
  }

  private void updateColor () {
    int role = shape.isStateLayerOnIndicator(view) ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE;
    int now = XgramExpressiveC.stateLayerColor(XgramExpressive.color(role));
    if (now != color) {
      color = now;
      setColor(ColorStateList.valueOf(now));
    }
  }

  @Override
  protected boolean onStateChange (@NonNull int[] stateSet) {
    if (ready) {
      updateColor();
    }
    return super.onStateChange(stateSet);
  }

  @Override
  public void draw (@NonNull Canvas c) {
    nextShape.setEmpty();
    float radius = shape.getStateLayerShape(view, nextShape);
    if (mask.update(nextShape, radius)) {
      // The ripple draws its mask again (in the new shape) before it draws. The view is drawing already: no new frame
      Callback callback = getCallback();
      setCallback(null);
      invalidateSelf();
      setCallback(callback);
    }
    super.draw(c);
  }

  /** The item's shape as the ripple's mask (translucent, so the ripple is clipped to it). */
  private static final class ShapeMask extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float radius = Float.NaN;

    ShapeMask () {
      paint.setColor(0xffffffff);
    }

    /** False when the shape is the one drawn last. */
    boolean update (@NonNull RectF next, float radius) {
      if (rect.left == next.left && rect.top == next.top && rect.right == next.right && rect.bottom == next.bottom && Float.compare(this.radius, radius) == 0) {
        return false;
      }
      rect.set(next);
      this.radius = radius;
      return true;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      float width = rect.width(), height = rect.height();
      if (width <= 0f || height <= 0f) {
        return;
      }
      float max = Math.min(width, height) / 2f;
      float r = radius < 0f || Float.isNaN(radius) ? max : Math.min(radius, max);
      c.drawRoundRect(rect, r, r, paint);
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
