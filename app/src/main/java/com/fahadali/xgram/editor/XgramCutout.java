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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The photo editor's cut-out (the Cut out tool, {@link XgramCutoutEditor}): how much of each pixel of the photo is
 * removed, an 8-bit mask at the photo's resolution (at most {@link #MAX_PIXELS}, scaled down to it).
 * <p>
 * The mask is in the photo bitmap's own grid: not turned, not mirrored. It is drawn as the photo is under the drawing
 * (PaintState.draw's base: scaled to the photo's rect, mirrored around its middle as the crop mirrors the photo), in the
 * drawing's coordinates, so it follows the crop as the drawings do (the region, the quarter turns and the straightening
 * turn the drawing with the photo) and the mirror as the photo does. A mirror after the cut-out moves it with the photo.
 * <p>
 * Each change is an {@link Edit} (the changed rect, before and after, deflated), an action in the drawing's undo
 * history. Drawn over the photo it is a checkerboard where the photo is removed ({@link #draw} with cut false); for the
 * output it cuts what is under it on the canvas (cut true: DST_OUT). "Soft edges" draws (and cuts with) a copy feathered
 * by a 5-tap binomial blur, about 2 pixels; the edits keep the hard mask.
 */
public final class XgramCutout {
  /** The mask's size limit: a bigger photo's mask is scaled down to about this many pixels. */
  public static final int MAX_PIXELS = 2_000_000;
  private static final int FEATHER_RADIUS = 2;
  /** The checkerboard: squares this many times smaller than the photo's long side. */
  private static final float CHECKER_SQUARES = 40f;
  private static final int CHECKER_LIGHT = 0xfff2f2f2, CHECKER_DARK = 0xffbdbdbd;
  /**
   * The checkerboard bitmap's squares, in its pixels: before Android 13 the shader is filtered with the paint (no
   * FILTER_MODE_NEAREST), so 1-pixel squares scaled up would blur into a gradient; these only soften at their edges.
   */
  private static final int CHECKER_CELL = 16;

  public final int width, height;
  /** How much of each pixel is removed: 0 kept, 255 removed. Row by row; changed only through edits. */
  final byte[] mask;
  /** Pixels not fully kept, as of the last commit. */
  private int removedCount;
  private boolean soft;

  private XgramCutout (int width, int height, byte[] mask, int removedCount, boolean soft) {
    this.width = width;
    this.height = height;
    this.mask = mask;
    this.removedCount = removedCount;
    this.soft = soft;
  }

  /** The mask's size for a photo bitmap of photoWidth x photoHeight: the same, or scaled down to MAX_PIXELS. */
  public static int[] maskSize (int photoWidth, int photoHeight) {
    photoWidth = Math.max(1, photoWidth);
    photoHeight = Math.max(1, photoHeight);
    final double scale = Math.min(1.0, Math.sqrt((double) MAX_PIXELS / ((double) photoWidth * (double) photoHeight)));
    return new int[] {
      Math.max(1, (int) Math.round(photoWidth * scale)),
      Math.max(1, (int) Math.round(photoHeight * scale))
    };
  }

  /** A cut-out with nothing removed for a photo bitmap of photoWidth x photoHeight. */
  public static XgramCutout create (int photoWidth, int photoHeight, boolean soft) {
    final int[] size = maskSize(photoWidth, photoHeight);
    return new XgramCutout(size[0], size[1], new byte[size[0] * size[1]], 0, soft);
  }

  /** A copy to go back to (the editor's Cancel): the mask as it is, no edit in progress, no drawn copy. */
  public synchronized XgramCutout copy () {
    return new XgramCutout(width, height, mask.clone(), removedCount, soft);
  }

  /** Whether any of the photo is removed (as of the last committed change). */
  public synchronized boolean hasRemoved () {
    return removedCount > 0;
  }

  /** Whether it draws anything: something removed, or a change in progress. */
  private boolean isShown () {
    return removedCount > 0 || editing;
  }

  public synchronized boolean isSoft () {
    return soft;
  }

  /** Soft edges (feathered about 2 pixels) or hard ones; not an edit (the editor keeps the choice for next time). */
  public synchronized void setSoft (boolean soft) {
    if (this.soft != soft) {
      this.soft = soft;
      if (!soft) {
        shown = null;
      }
      invalidateShown(0, 0, width, height);
    }
  }

  /** The same mask and edges. */
  public synchronized boolean contentEquals (@Nullable XgramCutout other) {
    if (other == this) {
      return true;
    }
    if (other == null || other.width != width || other.height != height) {
      return false;
    }
    synchronized (other) {
      return other.soft == soft && Arrays.equals(mask, other.mask);
    }
  }

  /** How much (0..255) of the mask's pixel at x, y is removed. */
  public int get (int x, int y) {
    if (x < 0 || y < 0 || x >= width || y >= height) {
      return 0;
    }
    return mask[y * width + x] & 0xff;
  }

  // Edits: a tool changes the mask between beginEdit and commitEdit (or cancelEdit), noting where (markChanged)

  /** The mask as of the last commit (undo's before), made at the first edit. */
  private @Nullable byte[] committed;
  private boolean editing;
  private int editLeft, editTop, editRight, editBottom;

  /** What one action changed: the rect, and its mask before and after, deflated. */
  public static final class Edit {
    final int maskWidth, maskHeight;
    final int left, top, right, bottom;
    final byte[] before, after;

    Edit (int maskWidth, int maskHeight, int left, int top, int right, int bottom, byte[] before, byte[] after) {
      this.maskWidth = maskWidth;
      this.maskHeight = maskHeight;
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      this.before = before;
      this.after = after;
    }
  }

  synchronized void beginEdit () {
    if (committed == null) {
      committed = mask.clone();
    }
    editing = true;
    editLeft = width;
    editTop = height;
    editRight = editBottom = 0;
  }

  synchronized boolean isEditing () {
    return editing;
  }

  /** The tool changed the mask in [left, right) x [top, bottom). */
  synchronized void markChanged (int left, int top, int right, int bottom) {
    left = Math.max(0, left);
    top = Math.max(0, top);
    right = Math.min(width, right);
    bottom = Math.min(height, bottom);
    if (right <= left || bottom <= top) {
      return;
    }
    if (editing) {
      editLeft = Math.min(editLeft, left);
      editTop = Math.min(editTop, top);
      editRight = Math.max(editRight, right);
      editBottom = Math.max(editBottom, bottom);
    }
    invalidateShown(left, top, right, bottom);
  }

  /** Ends the change: what it changed, as one action, or null when nothing changed. */
  @Nullable
  synchronized Edit commitEdit () {
    if (!editing) {
      return null;
    }
    editing = false;
    final byte[] committed = this.committed;
    if (committed == null || editRight <= editLeft || editBottom <= editTop) {
      return null;
    }
    final int l = editLeft, t = editTop, r = editRight, b = editBottom;
    final int w = r - l, h = b - t;
    final byte[] before = new byte[w * h], after = new byte[w * h];
    for (int y = 0; y < h; y++) {
      final int src = (t + y) * width + l, dst = y * w;
      System.arraycopy(committed, src, before, dst, w);
      System.arraycopy(mask, src, after, dst, w);
      System.arraycopy(mask, src, committed, src, w);
    }
    int removedBefore = 0, removedAfter = 0;
    boolean changed = false;
    for (int i = 0; i < before.length; i++) {
      final byte was = before[i], now = after[i];
      if (was != 0) {
        removedBefore++;
      }
      if (now != 0) {
        removedAfter++;
      }
      if (was != now) {
        changed = true;
      }
    }
    if (!changed) {
      return null;
    }
    removedCount += removedAfter - removedBefore;
    return new Edit(width, height, l, t, r, b, deflate(before), deflate(after));
  }

  /** Ends the change undone (a pinch took the gesture over). */
  synchronized void cancelEdit () {
    if (!editing) {
      return;
    }
    editing = false;
    final byte[] committed = this.committed;
    if (committed != null && editRight > editLeft && editBottom > editTop) {
      final int w = editRight - editLeft;
      for (int y = editTop; y < editBottom; y++) {
        final int i = y * width + editLeft;
        System.arraycopy(committed, i, mask, i, w);
      }
      invalidateShown(editLeft, editTop, editRight, editBottom);
    }
  }

  /** Puts the mask back as it was before {@code edit} (undo), or as after it (redo). False when it doesn't fit. */
  public synchronized boolean applyEdit (@NonNull Edit edit, boolean after) {
    if (edit.maskWidth != width || edit.maskHeight != height || editing) {
      return false;
    }
    final int w = edit.right - edit.left, h = edit.bottom - edit.top;
    final byte[] data = inflate(after ? edit.after : edit.before, w * h);
    if (data == null) {
      return false;
    }
    int removedBefore = 0, removedAfter = 0;
    final byte[] committed = this.committed;
    for (int y = 0; y < h; y++) {
      final int i = (edit.top + y) * width + edit.left, src = y * w;
      for (int x = 0; x < w; x++) {
        if (mask[i + x] != 0) {
          removedBefore++;
        }
        if (data[src + x] != 0) {
          removedAfter++;
        }
      }
      System.arraycopy(data, src, mask, i, w);
      if (committed != null) {
        System.arraycopy(data, src, committed, i, w);
      }
    }
    removedCount += removedAfter - removedBefore;
    invalidateShown(edit.left, edit.top, edit.right, edit.bottom);
    return true;
  }

  /** Swaps what is removed and what is kept, as one action (null when nothing changed). */
  @Nullable
  synchronized Edit invert () {
    beginEdit();
    for (int i = 0; i < mask.length; i++) {
      mask[i] = (byte) (255 - (mask[i] & 0xff));
    }
    markChanged(0, 0, width, height);
    return commitEdit();
  }

  /** Brings the whole photo back, as one action (null when nothing was removed). */
  @Nullable
  public synchronized Edit clear () {
    if (removedCount == 0 || editing) {
      return null;
    }
    beginEdit();
    Arrays.fill(mask, (byte) 0);
    markChanged(0, 0, width, height);
    return commitEdit();
  }

  /** Frees the copy the edits need (the editor closed); made again at the next edit. */
  public synchronized void releaseEditBuffers () {
    if (!editing) {
      committed = null;
    }
  }

  // Drawing

  /** The mask as drawn: the feathered copy with soft edges (null: the mask itself), and its ALPHA_8 bitmap. */
  private @Nullable byte[] shown;
  private @Nullable Bitmap bitmap;
  private @Nullable byte[] uploadBuffer;
  private boolean shownDirty = true;
  private int dirtyLeft, dirtyTop, dirtyRight, dirtyBottom;
  private final Matrix matrix = new Matrix(), shaderMatrix = new Matrix();
  private @Nullable Paint previewPaint, cutPaint;
  private @Nullable BitmapShader checkerShader;

  private void invalidateShown (int left, int top, int right, int bottom) {
    if (!shownDirty) {
      shownDirty = true;
      dirtyLeft = left;
      dirtyTop = top;
      dirtyRight = right;
      dirtyBottom = bottom;
    } else {
      dirtyLeft = Math.min(dirtyLeft, left);
      dirtyTop = Math.min(dirtyTop, top);
      dirtyRight = Math.max(dirtyRight, right);
      dirtyBottom = Math.max(dirtyBottom, bottom);
    }
  }

  /** The bitmap with the latest mask (feathered with soft edges), or null when it can't be made. */
  @Nullable
  private Bitmap syncBitmap () {
    Bitmap bitmap = this.bitmap;
    if (bitmap == null || bitmap.isRecycled()) {
      try {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
      } catch (Throwable t) {
        Log.e("Unable to make the photo editor's cut-out bitmap", t);
        return null;
      }
      this.bitmap = bitmap;
      shownDirty = true;
      dirtyLeft = dirtyTop = 0;
      dirtyRight = width;
      dirtyBottom = height;
    }
    if (shownDirty) {
      shownDirty = false;
      byte[] source = mask;
      if (soft) {
        if (shown == null) {
          shown = new byte[mask.length];
          dirtyLeft = dirtyTop = 0;
          dirtyRight = width;
          dirtyBottom = height;
        }
        feather(shown, dirtyLeft, dirtyTop, dirtyRight, dirtyBottom);
        source = shown;
      }
      try {
        final int rowBytes = bitmap.getRowBytes();
        if (rowBytes == width) {
          bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(source));
        } else {
          // rows padded: copy them into a buffer with the bitmap's row length
          final int size = rowBytes * height;
          if (uploadBuffer == null || uploadBuffer.length != size) {
            uploadBuffer = new byte[size];
          }
          for (int y = 0; y < height; y++) {
            System.arraycopy(source, y * width, uploadBuffer, y * rowBytes, width);
          }
          bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(uploadBuffer));
        }
      } catch (Throwable t) {
        Log.e("Unable to update the photo editor's cut-out bitmap", t);
      }
    }
    return bitmap;
  }

  private @Nullable int[] featherRows;

  /** {@code out} in the rect (and 2 pixels around it) = the mask blurred by [1 4 6 4 1] / 16 each way. */
  private void feather (byte[] out, int left, int top, int right, int bottom) {
    final int radius = FEATHER_RADIUS;
    left = Math.max(0, left - radius);
    top = Math.max(0, top - radius);
    right = Math.min(width, right + radius);
    bottom = Math.min(height, bottom + radius);
    final int w = right - left;
    if (w <= 0 || bottom <= top) {
      return;
    }
    final byte[] mask = this.mask;
    final int strip = 64;
    final int capacity = (strip + radius * 2) * w;
    int[] rows = featherRows;
    if (rows == null || rows.length < capacity) {
      rows = featherRows = new int[capacity];
    }
    final int lastX = width - 1, lastY = height - 1;
    for (int y0 = top; y0 < bottom; y0 += strip) {
      final int y1 = Math.min(bottom, y0 + strip);
      final int rowsFrom = y0 - radius, rowsTo = y1 + radius;
      // across: the rows the strip needs, the photo's edge pixels repeated past it
      for (int ry = rowsFrom; ry < rowsTo; ry++) {
        final int src = Math.max(0, Math.min(lastY, ry)) * width;
        final int o = (ry - rowsFrom) * w - left;
        for (int x = left; x < right; x++) {
          rows[o + x] =
            (mask[src + Math.max(0, x - 2)] & 0xff) +
            ((mask[src + Math.max(0, x - 1)] & 0xff) << 2) +
            (mask[src + x] & 0xff) * 6 +
            ((mask[src + Math.min(lastX, x + 1)] & 0xff) << 2) +
            (mask[src + Math.min(lastX, x + 2)] & 0xff);
        }
      }
      // down
      for (int y = y0; y < y1; y++) {
        final int o = (y - rowsFrom) * w;
        final int dst = y * width + left;
        for (int x = 0; x < w; x++) {
          final int i = o + x;
          final int v = rows[i - 2 * w] + (rows[i - w] << 2) + rows[i] * 6 + (rows[i + w] << 2) + rows[i + 2 * w];
          out[dst + x] = (byte) Math.min(255, (v + 128) >> 8);
        }
      }
    }
  }

  /**
   * Draws the cut-out over the photo drawn at (left, top, right, bottom), mirrored around that rect's middle as the photo
   * is. {@code cut} false: a checkerboard where the photo is removed (the editor, the viewer, thumbnails); true: removes
   * that much of what is already on the canvas there (DST_OUT), for the output.
   */
  public synchronized void draw (@NonNull Canvas c, float left, float top, float right, float bottom, boolean mirrorHorizontally, boolean mirrorVertically, boolean cut) {
    final float rectWidth = right - left, rectHeight = bottom - top;
    if (rectWidth <= 0f || rectHeight <= 0f || !isShown()) {
      return;
    }
    final Bitmap bitmap = syncBitmap();
    if (bitmap == null) {
      return;
    }
    final float sx = rectWidth / (float) width, sy = rectHeight / (float) height;
    matrix.setScale(mirrorHorizontally ? -sx : sx, mirrorVertically ? -sy : sy);
    matrix.postTranslate(mirrorHorizontally ? right : left, mirrorVertically ? bottom : top);
    final Paint paint;
    if (cut) {
      if (cutPaint == null) {
        cutPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        cutPaint.setColor(0xff000000);
        cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
      }
      paint = cutPaint;
    } else {
      if (previewPaint == null) {
        previewPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        checkerShader = new BitmapShader(checkerBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          checkerShader.setFilterMode(BitmapShader.FILTER_MODE_NEAREST); // sharp squares, the mask's edge smooth
        }
        previewPaint.setShader(checkerShader);
      }
      // an ALPHA_8 bitmap paints the paint's shader where it is opaque: the checkerboard, in the mask's coordinates,
      // its squares a fixed share of the photo's long side wherever it is drawn
      final float square = Math.max(1f, Math.max(width, height) / CHECKER_SQUARES);
      shaderMatrix.setScale(square / CHECKER_CELL, square / CHECKER_CELL);
      if (checkerShader != null) {
        checkerShader.setLocalMatrix(shaderMatrix);
      }
      paint = previewPaint;
    }
    final int saveCount = c.save();
    c.concat(matrix);
    c.drawBitmap(bitmap, 0, 0, paint);
    c.restoreToCount(saveCount);
  }

  private static @Nullable Bitmap checker;

  private static Bitmap checkerBitmap () {
    synchronized (XgramCutout.class) {
      if (checker == null) {
        final Bitmap bitmap = Bitmap.createBitmap(CHECKER_CELL * 2, CHECKER_CELL * 2, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(CHECKER_LIGHT);
        final Paint paint = new Paint();
        paint.setColor(CHECKER_DARK);
        canvas.drawRect(CHECKER_CELL, 0, CHECKER_CELL * 2, CHECKER_CELL, paint);
        canvas.drawRect(0, CHECKER_CELL, CHECKER_CELL, CHECKER_CELL * 2, paint);
        checker = bitmap;
      }
      return checker;
    }
  }

  // Undo data

  private static byte[] deflate (byte[] data) {
    final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    try {
      deflater.setInput(data);
      deflater.finish();
      final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 32));
      final byte[] buffer = new byte[16384];
      while (!deflater.finished()) {
        final int count = deflater.deflate(buffer);
        if (count > 0) {
          out.write(buffer, 0, count);
        } else if (deflater.needsInput()) {
          break;
        }
      }
      return out.toByteArray();
    } finally {
      deflater.end();
    }
  }

  @Nullable
  private static byte[] inflate (byte[] data, int size) {
    final Inflater inflater = new Inflater();
    try {
      inflater.setInput(data);
      final byte[] result = new byte[size];
      int offset = 0;
      while (offset < size && !inflater.finished()) {
        final int count = inflater.inflate(result, offset, size - offset);
        if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          break;
        }
        offset += count;
      }
      return offset == size ? result : null;
    } catch (DataFormatException e) {
      Log.e("Unable to read the photo editor's cut-out change", e);
      return null;
    } finally {
      inflater.end();
    }
  }
}
