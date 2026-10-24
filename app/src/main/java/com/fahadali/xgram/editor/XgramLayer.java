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
package com.fahadali.xgram.editor;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.tool.Screen;

import java.lang.ref.WeakReference;

import me.vkryl.core.util.Blob;

/**
 * A movable layer of the photo editor's drawing (PaintState): text ({@link XgramTextLayer}), or an emoji, a sticker or
 * a GIF's first frame ({@link XgramStickerLayer}). Layers are drawn over the brush strokes, in the order they were
 * added. A layer lives in the drawing's own space, as the strokes do (the photo's pixels before its rotation, crop and
 * mirror): its centre as 0..1 of the drawing, its size as a share of the drawing's width and its turn in degrees, so
 * the editor, thumbnails and the sent photo draw it alike at any size. The editor moves, scales and turns a layer in
 * place, so undo keeps copies of it ({@link #copy()}).
 */
public abstract class XgramLayer {
  public static final int TYPE_TEXT = 1, TYPE_STICKER = 2;

  /** The centre, 0..1 of the drawing's width and height. */
  float cx = .5f, cy = .5f;
  /** The base size as a share of the drawing's width: a text's font size, a sticker's long side. */
  float size = .1f;
  /** Degrees clockwise, in the drawing's own orientation (the editor turns new layers upright on screen). */
  float rotation;

  // The editor's, not saved: hidden while its text is being edited, faded while it is over the trash
  private volatile boolean hidden;
  private float fade;
  private @Nullable WeakReference<PaintState> owner;

  public abstract int getType ();

  /** A copy of the layer (its position and content). */
  @NonNull
  public abstract XgramLayer copy ();

  /** Takes the content (not the position) of {@code other}, a layer of the same type. */
  protected abstract void setContent (@NonNull XgramLayer other);

  protected abstract boolean contentEquals (@NonNull XgramLayer other);

  /** The content's bounds around the layer's centre, in units of the layer's size (size 1 is 1). */
  protected abstract void getContentBounds (@NonNull RectF out);

  /** Draws the content around (0, 0), where {@code unit} pixels are the layer's size. */
  protected abstract void drawContent (@NonNull Canvas c, float unit, float alpha);

  protected abstract void saveContent (@NonNull Blob blob);

  protected abstract void restoreContent (@NonNull Blob blob);

  // Position

  public float getCenterX () {
    return cx;
  }

  public float getCenterY () {
    return cy;
  }

  public float getSize () {
    return size;
  }

  public float getRotation () {
    return rotation;
  }

  /** The content's width, in units of the layer's size (a text's font size, a picture's long side). */
  public float getContentWidth () {
    final RectF rect = new RectF();
    synchronized (this) {
      getContentBounds(rect);
    }
    return rect.width();
  }

  /** The content's height, in units of the layer's size. */
  public float getContentHeight () {
    final RectF rect = new RectF();
    synchronized (this) {
      getContentBounds(rect);
    }
    return rect.height();
  }

  public void setPosition (float cx, float cy, float size, float rotation) {
    this.cx = cx;
    this.cy = cy;
    this.size = size;
    this.rotation = normalizeDegrees(rotation);
  }

  /** Takes {@code other}'s position and content (undo and redo). */
  public void set (@NonNull XgramLayer other) {
    if (other.getType() != getType()) {
      return;
    }
    this.cx = other.cx;
    this.cy = other.cy;
    this.size = other.size;
    this.rotation = other.rotation;
    setContent(other);
  }

  protected final void copyPositionTo (@NonNull XgramLayer copy) {
    copy.cx = cx;
    copy.cy = cy;
    copy.size = size;
    copy.rotation = rotation;
  }

  public boolean compare (@Nullable XgramLayer other) {
    return other != null && other.getType() == getType() &&
      other.cx == cx && other.cy == cy && other.size == size && other.rotation == rotation &&
      contentEquals(other);
  }

  /** Degrees in -180..180. */
  public static float normalizeDegrees (float degrees) {
    degrees %= 360f;
    if (degrees > 180f) {
      degrees -= 360f;
    } else if (degrees <= -180f) {
      degrees += 360f;
    }
    return degrees;
  }

  // Editor state

  public boolean isHidden () {
    return hidden;
  }

  public void setHidden (boolean hidden) {
    if (this.hidden != hidden) {
      this.hidden = hidden;
      invalidateOwner();
    }
  }

  /** 0..1: how far it fades out (over the trash). */
  public void setFade (float fade) {
    if (this.fade != fade) {
      this.fade = fade;
      invalidateOwner();
    }
  }

  /** The drawing it is in, told when the layer changes on its own (a sticker's picture has loaded). */
  public void attach (@Nullable PaintState owner) {
    this.owner = owner != null ? new WeakReference<>(owner) : null;
  }

  protected final void invalidateOwner () {
    PaintState state = owner != null ? owner.get() : null;
    if (state != null) {
      state.notifySimpleDrawingChanged();
    }
  }

  // Drawing

  /** Draws the layer into the drawing at (left, top, left + width, top + height). */
  public final void draw (@NonNull Canvas c, float left, float top, float width, float height) {
    if (hidden || width <= 0f || height <= 0f) {
      return;
    }
    final float unit = size * width;
    if (unit <= 0f) {
      return;
    }
    final float alpha = 1f - Math.max(0f, Math.min(1f, fade)) * .6f;
    synchronized (this) { // the editor and the photo generation may draw one layer at once: its paints are its own
      final int saveCount = c.save();
      c.translate(left + cx * width, top + cy * height);
      if (rotation != 0f) {
        c.rotate(rotation);
      }
      drawContent(c, unit, alpha);
      c.restoreToCount(saveCount);
    }
  }

  private final RectF bounds = new RectF();

  /**
   * Whether (x, y), in a drawing of width x height, is on the layer or within {@code tolerance} pixels of its
   * content.
   */
  public boolean hitTest (float x, float y, int width, int height, float tolerance) {
    if (hidden || width <= 0 || height <= 0) {
      return false;
    }
    final float unit = size * width;
    if (unit <= 0f) {
      return false;
    }
    float dx = x - cx * width, dy = y - cy * height;
    if (rotation != 0f) {
      // turned back by the layer's rotation
      final double rad = Math.toRadians(-rotation);
      final float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
      final float rx = dx * cos - dy * sin, ry = dx * sin + dy * cos;
      dx = rx;
      dy = ry;
    }
    synchronized (this) {
      getContentBounds(bounds);
    }
    return dx >= bounds.left * unit - tolerance && dx <= bounds.right * unit + tolerance &&
      dy >= bounds.top * unit - tolerance && dy <= bounds.bottom * unit + tolerance;
  }

  // The frame around a held layer

  private static Paint framePaint;
  private static final float FRAME_PADDING_DP = 8f;
  private final RectF frameRect = new RectF();

  /**
   * The frame around the layer while a finger holds it, in a drawing of width x height shown {@code screenScale}
   * times its size: its lines and padding keep their size on screen. Telegram X look: a dashed white line; Material 3
   * Expressive: a solid 2dp line with 12dp corners. Both over a faint dark line, so it shows on a white photo too.
   */
  public void drawFrame (@NonNull Canvas c, float width, float height, float screenScale, boolean expressive) {
    if (hidden || width <= 0f || height <= 0f) {
      return;
    }
    final float unit = size * width;
    if (unit <= 0f) {
      return;
    }
    final float scale = Math.max(.01f, screenScale);
    final RectF rect = frameRect;
    synchronized (this) {
      getContentBounds(rect);
    }
    final float padding = Screen.dpf(FRAME_PADDING_DP) / scale;
    rect.set(rect.left * unit - padding, rect.top * unit - padding, rect.right * unit + padding, rect.bottom * unit + padding);
    if (framePaint == null) {
      framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      framePaint.setStyle(Paint.Style.STROKE);
    }
    final Paint paint = framePaint;
    final float radius = Screen.dpf(expressive ? 12f : 4f) / scale;
    final int saveCount = c.save();
    c.translate(cx * width, cy * height);
    if (rotation != 0f) {
      c.rotate(rotation);
    }
    paint.setPathEffect(null);
    paint.setStrokeWidth(Screen.dpf(expressive ? 4f : 3f) / scale);
    paint.setColor(0x33000000);
    c.drawRoundRect(rect, radius, radius, paint);
    paint.setStrokeWidth(Screen.dpf(expressive ? 2f : 1.5f) / scale);
    paint.setColor(0xffffffff);
    if (!expressive) {
      paint.setPathEffect(new DashPathEffect(new float[] {Screen.dpf(6f) / scale, Screen.dpf(4f) / scale}, 0f));
    }
    c.drawRoundRect(rect, radius, radius, paint);
    paint.setPathEffect(null);
    c.restoreToCount(saveCount);
  }

  // Blob

  public final void save (@NonNull Blob blob) {
    blob.writeFloat(cx);
    blob.writeFloat(cy);
    blob.writeFloat(size);
    blob.writeFloat(rotation);
    saveContent(blob);
  }

  /** The layer saved by {@link #save}, or null for a type this version doesn't know or broken data. */
  @Nullable
  public static XgramLayer restore (int type, @NonNull byte[] data) {
    final XgramLayer layer;
    switch (type) {
      case TYPE_TEXT:
        layer = new XgramTextLayer();
        break;
      case TYPE_STICKER:
        layer = new XgramStickerLayer();
        break;
      default:
        return null;
    }
    try {
      Blob blob = new Blob(data);
      layer.cx = blob.readFloat();
      layer.cy = blob.readFloat();
      layer.size = blob.readFloat();
      layer.rotation = blob.readFloat();
      layer.restoreContent(blob);
    } catch (Throwable t) {
      Log.w("Unable to read a photo editor layer", t);
      return null;
    }
    if (!(layer.size > 0f) || Float.isNaN(layer.cx) || Float.isNaN(layer.cy) || Float.isNaN(layer.rotation)) {
      return null;
    }
    return layer;
  }
}
