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

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * The photo under a drawing, for the photo editor's Blur and Pixelate brushes (SimpleDrawing.TYPE_BLUR and
 * TYPE_PIXELATE): they paint with a blurred or pixelated copy of it, so the photo shows blurred where the stroke goes.
 * The copy's strength follows the drawing's size, not the bitmap's, so the editor, thumbnails and the sent photo match.
 * Copies are small (about 120 or 40 pixels on the drawing's long side) and cached by bitmap, made once for a photo
 * the first time a stroke needs them. A base lives for one draw: it caches its shaders for that draw only.
 */
public final class XgramPaintBase {
  public static final int KIND_BLUR = 0, KIND_PIXELATE = 1;

  /** The blurred copy: this many pixels on the drawing's long side, then blurred twice by BLUR_RADIUS. */
  private static final float BLUR_SIDE = 120f;
  private static final int BLUR_RADIUS = 3;
  /** The pixelated copy: this many blocks on the drawing's long side. */
  private static final float PIXEL_SIDE = 40f;

  private final Bitmap bitmap;
  private final boolean mirrorHorizontally, mirrorVertically;
  private final float left, top, right, bottom;

  private final Shader[] shaders = new Shader[2];
  private final int[] shaderKeys = new int[2];

  /**
   * {@code bitmap} drawn at (left, top, right, bottom) in the drawing's coordinates, mirrored around that rect's centre
   * as the photo is shown.
   */
  public XgramPaintBase (@NonNull Bitmap bitmap, boolean mirrorHorizontally, boolean mirrorVertically, float left, float top, float right, float bottom) {
    this.bitmap = bitmap;
    this.mirrorHorizontally = mirrorHorizontally;
    this.mirrorVertically = mirrorVertically;
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  /**
   * The shader for a {@code kind} stroke of a drawing {@code drawingWidth} x {@code drawingHeight}, for a canvas
   * translated by (translateX, translateY) from the drawing's coordinates. Null when the copy can't be made.
   */
  @Nullable
  public Shader shader (int kind, int drawingWidth, int drawingHeight, float translateX, float translateY) {
    final float width = right - left, height = bottom - top;
    final int side = Math.max(drawingWidth, drawingHeight);
    if (side <= 0 || width <= 0f || height <= 0f || kind < 0 || kind >= shaders.length) {
      return null;
    }
    final float scale = (kind == KIND_BLUR ? BLUR_SIDE : PIXEL_SIDE) / (float) side;
    final int copyWidth = Math.max(1, Math.round(width * scale));
    final int copyHeight = Math.max(1, Math.round(height * scale));
    final int key = copyWidth * 31 + copyHeight + (int) translateX * 17 + (int) translateY * 13;
    if (shaders[kind] != null && shaderKeys[kind] == key) {
      return shaders[kind];
    }
    final Bitmap copy = obtain(bitmap, kind, copyWidth, copyHeight);
    if (copy == null) {
      return null;
    }
    final BitmapShader shader = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // blocks stay sharp; earlier versions follow the paint's filter flag (SimpleDrawing clears it for pixelate)
      shader.setFilterMode(kind == KIND_PIXELATE ? BitmapShader.FILTER_MODE_NEAREST : BitmapShader.FILTER_MODE_LINEAR);
    }
    final float sx = width / (float) copy.getWidth(), sy = height / (float) copy.getHeight();
    final Matrix matrix = new Matrix();
    matrix.setScale(mirrorHorizontally ? -sx : sx, mirrorVertically ? -sy : sy);
    matrix.postTranslate((mirrorHorizontally ? right : left) - translateX, (mirrorVertically ? bottom : top) - translateY);
    shader.setLocalMatrix(matrix);
    shaders[kind] = shader;
    shaderKeys[kind] = key;
    return shader;
  }

  // Copies

  private static final int CACHE_SIZE = 8;

  private static final class Copy {
    final WeakReference<Bitmap> source;
    final int kind, width, height;
    final Bitmap bitmap;

    Copy (Bitmap source, int kind, int width, int height, Bitmap bitmap) {
      this.source = new WeakReference<>(source);
      this.kind = kind;
      this.width = width;
      this.height = height;
      this.bitmap = bitmap;
    }
  }

  /** Most recently used last. The editor, thumbnails and the photo generation thread share it. */
  private static final ArrayList<Copy> cache = new ArrayList<>(CACHE_SIZE + 1);

  @Nullable
  private static Bitmap obtain (@NonNull Bitmap source, int kind, int width, int height) {
    synchronized (cache) {
      for (int i = cache.size() - 1; i >= 0; i--) {
        Copy copy = cache.get(i);
        Bitmap copySource = copy.source.get();
        if (copySource == null) {
          cache.remove(i);
          continue;
        }
        if (copySource == source && copy.kind == kind && copy.width == width && copy.height == height) {
          if (i != cache.size() - 1) {
            cache.remove(i);
            cache.add(copy);
          }
          return copy.bitmap;
        }
      }
    }
    if (source.isRecycled()) {
      return null;
    }
    final Bitmap bitmap = make(source, kind, width, height);
    if (bitmap == null) {
      return null;
    }
    synchronized (cache) {
      cache.add(new Copy(source, kind, width, height, bitmap));
      while (cache.size() > CACHE_SIZE) {
        cache.remove(0);
      }
    }
    return bitmap;
  }

  @Nullable
  private static Bitmap make (@NonNull Bitmap source, int kind, int width, int height) {
    try {
      Bitmap readable = source;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.getConfig() == Bitmap.Config.HARDWARE) {
        readable = source.copy(Bitmap.Config.ARGB_8888, false);
        if (readable == null) {
          return null;
        }
      }
      Bitmap result = scaleDown(readable, width, height);
      if (readable != source) {
        readable.recycle();
      }
      if (kind == KIND_BLUR) {
        // twice: closer to a gaussian than one pass
        U.blurBitmap(result, BLUR_RADIUS, 1);
        U.blurBitmap(result, BLUR_RADIUS, 1);
      }
      return result;
    } catch (Throwable t) {
      Log.e("Unable to make the photo editor's blur or pixelate copy", t);
      return null;
    }
  }

  /** Halves while it stays over twice the size (each step averages 2 x 2 pixels), then scales to width x height. */
  private static Bitmap scaleDown (Bitmap source, int width, int height) {
    Bitmap current = source;
    while (current.getWidth() / 2 >= width * 2 && current.getHeight() / 2 >= height * 2) {
      Bitmap half = draw(current, current.getWidth() / 2, current.getHeight() / 2);
      if (current != source) {
        current.recycle();
      }
      current = half;
    }
    Bitmap result = draw(current, width, height);
    if (current != source) {
      current.recycle();
    }
    return result;
  }

  private static Bitmap draw (Bitmap source, int width, int height) {
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(result);
    c.drawBitmap(source, null, new Rect(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
    return result;
  }
}
