/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 11/05/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.fahadali.xgram.editor.XgramPaintBase;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.util.Blob;
import me.vkryl.core.util.LocalVar;

public class SimpleDrawing {
  public interface ChangeListener {
    void onDrawingHasChanged (SimpleDrawing drawing, boolean delayed);
  }

  private static LocalVar<Paint> paint;

  public static final int TYPE_PATH = 0;
  public static final int TYPE_ARROW = 1;
  public static final int TYPE_RECTANGLE = 2;
  public static final int TYPE_FILLING = 3;

  // XGram: the photo editor's brushes and shapes. The brushes are paths; the shapes are stored as the rectangle is,
  // their rotation included. Telegram X before them cannot read a state that has them (no version byte).
  public static final int TYPE_MARKER = 4;
  public static final int TYPE_NEON = 5;
  public static final int TYPE_BLUR = 6;
  public static final int TYPE_PIXELATE = 7;
  public static final int TYPE_ELLIPSE = 8;
  public static final int TYPE_RECTANGLE_FILLED = 9;
  public static final int TYPE_ELLIPSE_FILLED = 10;

  /** XGram: the marker's colour alpha (one path, so it does not darken where the stroke crosses itself). */
  private static final float MARKER_ALPHA = .5f;

  /** XGram: the types drawn as a path (TYPE_PATH and the brushes). */
  public static boolean isPathType (int type) {
    switch (type) {
      case TYPE_PATH:
      case TYPE_MARKER:
      case TYPE_NEON:
      case TYPE_BLUR:
      case TYPE_PIXELATE:
        return true;
    }
    return false;
  }

  /** XGram: rectangles and ellipses, outlined or filled (two corners and the crop's straightening angle). */
  public static boolean isShapeType (int type) {
    switch (type) {
      case TYPE_RECTANGLE:
      case TYPE_ELLIPSE:
      case TYPE_RECTANGLE_FILLED:
      case TYPE_ELLIPSE_FILLED:
        return true;
    }
    return false;
  }

  private static boolean isKnownType (int type) {
    return type >= TYPE_PATH && type <= TYPE_ELLIPSE_FILLED;
  }

  private final int type;

  final int canvasWidth, canvasHeight;
  private final float canvasScale, canvasRotation;

  private int color;
  private float strokeRadius;

  private float x1, x2;
  private float y1, y2;

  private CustomPath path;

  private ArrayList<ChangeListener> changeListeners;

  public SimpleDrawing (int type) {
    this(type, 0, 0, 0f, 0f);
  }

  public SimpleDrawing (int type, int canvasWidth, int canvasHeight, float canvasScale, float canvasRotation) {
    this.type = type;
    this.canvasWidth = canvasWidth;
    this.canvasHeight = canvasHeight;
    this.canvasScale = canvasScale;
    this.x1 = x2 = x1;
    this.y1 = y2 = y1;
    this.canvasRotation = canvasRotation;
    this.path = isPathType(type) ? new CustomPath(this) : null; // XGram: and the brushes
    if (paint == null) {
      paint = new LocalVar<>();
      paint.set(newPaint());
    }
  }

  private static Paint newPaint () {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    return p;
  }

  float getRelativeX (float x) {
    return x / (float) canvasWidth;
  }

  float getRelativeY (float y) {
    return y / (float) canvasHeight;
  }

  public void startDrawing (MotionEvent e) { // touchDown
    float x = getRelativeX(e.getX());
    float y = getRelativeY(e.getY());
    this.x1 = this.x2 = x;
    this.y1 = this.y2 = y;
    if (isPathType(type)) { // XGram: and the brushes
      trackPoint(e, false, true);
    }
  }

  public void moveDrawing (MotionEvent e, boolean allowHistory) { // touchMove
    float x = getRelativeX(e.getX());
    float y = getRelativeY(e.getY());
    if (type == TYPE_ARROW || isShapeType(type)) { // XGram: and the other shapes
      if (this.x2 != x || this.y2 != y) {
        this.x2 = x;
        this.y2 = y;
        invalidate(true);
      }
    } else if (isPathType(type)) { // XGram: and the brushes
      trackPoint(e, false, allowHistory);
    }
  }

  public boolean completeDrawing (MotionEvent e, boolean byPinch) { // touchUp
    // XGram: the other shapes as the rectangle, the brushes as the path
    if (type == TYPE_ARROW || isShapeType(type)) {
      // XGram: after a pinch only a shape the finger really drew stays, as the path's rule (Telegram X kept the
      // unmoved one, a stray dot)
      return !byPinch || Math.hypot((x2 - x1) * canvasWidth, (y2 - y1) * canvasHeight) > Screen.getTouchSlop() * canvasScale;
    }
    if (isPathType(type)) {
      if (!byPinch || path.getActionCount() > 3) {
        trackPoint(e, true, true);
        path.trim();
        return true;
      }
      return false;
    }
    return true;
  }

  private void trackPoint (MotionEvent e, boolean finish, boolean allowHistory) {
    buildPath(e, finish, allowHistory);
    invalidate(!finish);
  }

  private int totalEventCount;
  private float pastX, pastY, pastDx, pastDy, prevX, prevY, prevDx, prevDy;
  private float checkX, checkY;

  private void buildPath (MotionEvent e, boolean finish, boolean allowHistory) {
    final boolean useHistory = allowHistory && e.getAction() == MotionEvent.ACTION_MOVE;
    final int eventCount = useHistory ? e.getHistorySize() : 1;

    for (int i = 0; i < eventCount; i++) {
      float nextX, nextY;

      if (useHistory) {
        nextX = e.getHistoricalX(i);
        nextY = e.getHistoricalY(i);
      } else {
        nextX = e.getX();
        nextY = e.getY();
      }
      if (finish) {
        if (totalEventCount == 0) {
          path.moveTo(checkX = nextX, checkY = nextY);
        }
        if (checkX == nextX && checkY == nextY) {
          path.lineTo(nextX + 1, nextY);
        } else {
          path.quadTo(checkX, checkY, nextX, nextY);
        }
        return;
      }
      if (checkX == nextX && checkY == nextY && totalEventCount > 0) {
        continue;
      }
      checkX = nextX;
      checkY = nextY;
      float dx = 0, dy = 0;
      int eventIndex = totalEventCount++;
      if (eventIndex == 0) {
        path.moveTo(nextX, nextY);
      } else {
        dx = (nextX - this.prevX) / 3f;
        dy = (nextY - this.prevY) / 3f;
      }

      if (eventIndex > 3) {
        float pointX = prevX;
        float pointY = prevY;
        float pointDx = prevDx;
        float pointDy = prevDy;
        float prevX = pastX;
        float prevY = pastY;
        float prevDx = pastDx;
        float prevDy = pastDy;
        path.cubicTo(prevX + prevDx, prevY + prevDy, pointX - pointDx, pointY - pointDy, pointX, pointY);
      } else if (eventIndex == 2) {
        //prevDx = (nextX - prevX) / 3f;
        //prevDy = (nextY - prevY) / 3f;

        float pointX = nextX;
        float pointY = nextY;
        //dx = (pointX - prevX) / 3;
        //dy = (pointY - prevY) / 3;

        // `path.cubicTo(prevX - prevDx, prevY + prevDy, pointX - dx, pointY - dy, pointX, pointY);
      }

      pastX = prevX;
      pastY = prevY;
      pastDx = prevDx;
      pastDy = prevDy;
      prevX = nextX;
      prevY = nextY;
      prevDx = dx;
      prevDy = dy;
    }

    /*final int size = points.size();
    if (size > 1) {
      for (int i = Math.max(0, size - 2); i < size; i++){
        Point point = points.get(i);
        if (i == 0) {
          Point next = points.get(i + 1);
          point.dx = ((next.x - point.x) / 3);
          point.dy = ((next.y - point.y) / 3);
        } else if (i == size - 1) {
          Point prev = points.get(i - 1);
          point.dx = ((point.x - prev.x) / 3);
          point.dy = ((point.y - prev.y) / 3);
        } else {
          Point next = points.get(i + 1);
          Point prev = points.get(i - 1);
          point.dx = ((next.x - prev.x) / 3);
          point.dy = ((next.y - prev.y) / 3);
        }
      }
    }

    boolean first = true;
    int i = 0;
    for (Point point : points) {
      if (first) {
        first = false;
        path.moveTo(point.x, point.y);
      } else {
        Point prev = points.get(i - 1);
        path.cubicTo(prev.x + prev.dx, prev.y + prev.dy, point.x - point.dx, point.y - point.dy, point.x, point.y);
      }
      i++;
    }*/
  }

  private static boolean needsCanvasRotation (int type) {
    return isShapeType(type); // XGram: Telegram X had the rectangle only
  }

  private static boolean needsStrokeRadius (int type) {
    switch (type) {
      case TYPE_FILLING:
        return false;
    }
    return true;
  }

  public void addChangeListener (ChangeListener listener) {
    if (changeListeners == null) {
      changeListeners = new ArrayList<>();
    }
    changeListeners.add(listener);
  }

  public void removeChangeListener (ChangeListener listener) {
    if (changeListeners != null) {
      changeListeners.remove(listener);
    }
  }

  private int dp (float dp, float width, float height) {
    float scale = width / canvasWidth; // , height /canvasHeight);
    return (int) (Screen.dpf(dp) * scale * canvasScale);
  }

  public boolean compare (SimpleDrawing b) {
    return b != null &&
      b.type == type &&
      b.color == color &&
      (!needsStrokeRadius(type) || b.strokeRadius == strokeRadius) &&
      (type == TYPE_FILLING || (b.canvasWidth == canvasWidth && b.canvasHeight == canvasHeight) || (b.canvasWidth / b.canvasHeight) == (canvasWidth / canvasHeight)) && // checking only for aspect ratio, since result will be the same
      (!needsCanvasRotation(type) || b.canvasRotation == canvasRotation) &&
      b.x1 == x1 && b.x2 == x2 &&
      b.y1 == y1 && b.y2 == y2 && (!isPathType(type) || b.path.compare(path)); // XGram: and the brushes
  }

  public void setBrushParameters (int color, float strokeRadius) {
    if (this.color != color || this.strokeRadius != strokeRadius) {
      this.color = color;
      this.strokeRadius = strokeRadius;
      invalidate(false);
    }
  }

  private void invalidate (boolean delayed) {
    if (this.changeListeners != null) {
      for (ChangeListener listener : changeListeners) {
        listener.onDrawingHasChanged(this, delayed);
      }
    }
  }

  private float getCanvasX (double x) {
    return (float) ((double) canvasWidth * x);
  }

  private float getCanvasY (double y) {
    return (float) ((double) canvasHeight * y);
  }

  public void draw (Canvas c, int viewLeft, int viewTop, int viewRight, int viewBottom) {
    draw(c, viewLeft, viewTop, viewRight, viewBottom, null);
  }

  /** XGram: {@code base} is the photo under the drawing, for {@link #needsBase()} (without it they draw nothing). */
  public void draw (Canvas c, int viewLeft, int viewTop, int viewRight, int viewBottom, @Nullable XgramPaintBase base) {
    int width = viewRight - viewLeft; //  (viewLeft + viewRight) / 2;
    int height = viewBottom - viewTop; // (viewTop + viewBottom) / 2;

    float x1 = (float) ((float) viewLeft + Math.ceil((double) width * this.x1));
    float x2 = (float) ((float) viewLeft + Math.floor((double) width * this.x2));
    if (x1 == x2) {
      if (x2 >= width) {
        x1--;
      } else {
        x2--;
      }
    }
    float y1 = (float) ((float) viewTop + Math.ceil((double) height * this.y1));
    float y2 = (float) ((float) viewTop + Math.floor((double) height * this.y2));
    if (y1 == y2) {
      if (y2 >= height) {
        y1--;
      } else {
        y2--;
      }
    }

    float left = Math.min(x1, x2);
    float top = Math.min(y1, y2);
    float right = Math.max(x1, x2);
    float bottom = Math.max(y1, y2);
    float strokeRadius = this.strokeRadius;

    float strokeSize = dp(strokeRadius, width, height);
    Paint paint = SimpleDrawing.paint.get();
    if (paint == null) {
      SimpleDrawing.paint.set(paint = newPaint());
    }
    paint.setStrokeWidth(strokeSize);
    paint.setColor(color);

    switch (type) {
      case TYPE_RECTANGLE: {
        RectF rectF = Paints.getRectF();
        rectF.set(left, top, right, bottom);
        final boolean saved = canvasRotation != 0f;
        if (saved) {
          c.save();
          c.rotate(-canvasRotation, left, top);
        }
        // float radius = dp(2f, width, height);
        c.drawRect(rectF, paint);
        if (saved) {
          c.restore();
        }
        break;
      }
      case TYPE_FILLING: {
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(viewLeft, viewTop, viewRight, viewBottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        break;
      }
      case TYPE_ARROW: {
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x1, y1, strokeSize / 2, paint);
        c.drawCircle(x2, y2, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);

        float diffX = (x1 - x2) / 2f;
        float diffY = (y1 - y2) / 2f;

        float maxDistance = dp(24f, width, height);

        float angle = 35f;

        float distance = Math.abs(MathUtils.distance(0, 0, diffX, diffY));
        if (distance > maxDistance) {
          float scale = maxDistance / distance;
          diffX *= scale;
          diffY *= scale;
        } else if (distance < maxDistance) {
          float minAngle = 15f;
          angle = minAngle + (angle - minAngle) * distance / maxDistance;
        }

        // double angle = Math.atan2(y2 - y1, x2 - x1);
        c.save();
        c.rotate(-angle, x2, y2);
        c.drawLine(x2, y2, x2 + diffX, y2 + diffY, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x2 + diffX, y2 + diffY, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        c.rotate(angle * 2, x2, y2);
        c.drawLine(x2, y2, x2 + diffX, y2 + diffY, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x2 + diffX, y2 + diffY, strokeSize / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        c.restore();

        break;
      }
      case TYPE_PATH: {
        c.save();
        c.clipRect(viewLeft, viewTop, viewRight, viewBottom);
        if (viewLeft != 0 || viewTop != 0) {
          c.translate(viewLeft, viewTop);
        }
        c.drawPath(path.getPath(width, height), paint);
        c.restore();
        break;
      }
      // XGram
      case TYPE_ELLIPSE:
      case TYPE_RECTANGLE_FILLED:
      case TYPE_ELLIPSE_FILLED: {
        RectF rectF = Paints.getRectF();
        rectF.set(left, top, right, bottom);
        final boolean saved = canvasRotation != 0f;
        if (saved) {
          c.save();
          c.rotate(-canvasRotation, left, top);
        }
        final boolean filled = type != TYPE_ELLIPSE;
        if (filled) {
          paint.setStyle(Paint.Style.FILL);
        }
        if (type == TYPE_RECTANGLE_FILLED) {
          c.drawRect(rectF, paint);
        } else {
          c.drawOval(rectF, paint);
        }
        if (filled) {
          paint.setStyle(Paint.Style.STROKE);
        }
        if (saved) {
          c.restore();
        }
        break;
      }
      case TYPE_MARKER:
      case TYPE_NEON:
      case TYPE_BLUR:
      case TYPE_PIXELATE: {
        c.save();
        c.clipRect(viewLeft, viewTop, viewRight, viewBottom);
        if (viewLeft != 0 || viewTop != 0) {
          c.translate(viewLeft, viewTop);
        }
        xgramDrawBrush(c, paint, path.getPath(width, height), strokeSize, width, height, viewLeft, viewTop, base);
        c.restore();
        break;
      }
    }
  }

  // XGram: the brushes

  /** Blur and pixelate paint with the photo under them: they need the base image to draw. */
  public boolean needsBase () {
    return type == TYPE_BLUR || type == TYPE_PIXELATE;
  }

  public int getType () {
    return type;
  }

  /** The glow passes of the neon brush where BlurMaskFilter is not drawn by the hardware renderer (before Android 9). */
  private static final float[] NEON_GLOW_WIDTHS = {2.6f, 2f, 1.5f};
  private static final float[] NEON_GLOW_ALPHAS = {.1f, .16f, .26f};

  /** The neon glow's mask filter for one stroke size (the editor, thumbnails and the export draw at different sizes). */
  private static final class NeonGlow {
    final float strokeSize;
    final BlurMaskFilter filter;

    NeonGlow (float strokeSize) {
      this.strokeSize = strokeSize;
      this.filter = new BlurMaskFilter(Math.max(1f, strokeSize * .9f), BlurMaskFilter.Blur.NORMAL);
    }
  }

  private volatile NeonGlow neonGlow;

  private void xgramDrawBrush (Canvas c, Paint paint, Path path, float strokeSize, int width, int height, int viewLeft, int viewTop, @Nullable XgramPaintBase base) {
    paint.setStrokeJoin(Paint.Join.ROUND);
    switch (type) {
      case TYPE_MARKER: {
        // a wide flat-ended stroke at half alpha, in one path so it does not darken where it crosses itself
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(ColorUtils.alphaColor(MARKER_ALPHA, color));
        c.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        break;
      }
      case TYPE_NEON: {
        // a glow in the colour, the stroke, then a light core
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          // one filter per size, not one per frame (a native object); it is immutable, so threads can share it
          NeonGlow glow = neonGlow;
          if (glow == null || glow.strokeSize != strokeSize) {
            neonGlow = glow = new NeonGlow(strokeSize);
          }
          paint.setMaskFilter(glow.filter);
          paint.setStrokeWidth(strokeSize * 1.8f);
          paint.setColor(ColorUtils.alphaColor(.9f, color));
          c.drawPath(path, paint);
          paint.setMaskFilter(null);
        } else {
          for (int i = 0; i < NEON_GLOW_WIDTHS.length; i++) {
            paint.setStrokeWidth(strokeSize * NEON_GLOW_WIDTHS[i]);
            paint.setColor(ColorUtils.alphaColor(NEON_GLOW_ALPHAS[i], color));
            c.drawPath(path, paint);
          }
        }
        paint.setStrokeWidth(strokeSize);
        paint.setColor(color);
        c.drawPath(path, paint);
        paint.setStrokeWidth(strokeSize * .45f);
        paint.setColor(ColorUtils.fromToArgb(color, 0xffffffff, .8f));
        c.drawPath(path, paint);
        paint.setStrokeWidth(strokeSize);
        break;
      }
      case TYPE_BLUR:
      case TYPE_PIXELATE: {
        // the photo under the stroke, blurred or pixelated (nothing without it)
        final boolean pixelate = type == TYPE_PIXELATE;
        Shader shader = base != null ? base.shader(pixelate ? XgramPaintBase.KIND_PIXELATE : XgramPaintBase.KIND_BLUR, width, height, viewLeft, viewTop) : null;
        if (shader != null) {
          paint.setShader(shader);
          paint.setColor(0xffffffff);
          paint.setFilterBitmap(!pixelate);
          c.drawPath(path, paint);
          paint.setFilterBitmap(false);
          paint.setShader(null);
        }
        break;
      }
    }
    paint.setStrokeJoin(Paint.Join.MITER);
  }

  // XGram: the eraser

  /** Whether (x, y), in a view of width x height, is within {@code tolerance} of this drawing as drawn there. */
  public boolean hitTest (float x, float y, int width, int height, float tolerance) {
    if (type == TYPE_FILLING || width <= 0 || height <= 0 || canvasWidth <= 0) {
      return false;
    }
    final float maxDistance = dp(strokeRadius, width, height) / 2f + tolerance;
    if (isPathType(type)) {
      return path != null && path.hitTest(x, y, width, height, maxDistance);
    }
    final float x1 = width * this.x1, y1 = height * this.y1, x2 = width * this.x2, y2 = height * this.y2;
    if (type == TYPE_ARROW) {
      return segmentDistanceSq(x, y, x1, y1, x2, y2) <= maxDistance * maxDistance;
    }
    if (!isShapeType(type)) {
      return false;
    }
    final float left = Math.min(x1, x2), top = Math.min(y1, y2), right = Math.max(x1, x2), bottom = Math.max(y1, y2);
    if (canvasRotation != 0f) {
      // drawn turned by -canvasRotation around its top left corner: turn the point back
      final double rad = Math.toRadians(canvasRotation);
      final float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
      final float dx = x - left, dy = y - top;
      x = left + dx * cos - dy * sin;
      y = top + dx * sin + dy * cos;
    }
    final boolean filled = type == TYPE_RECTANGLE_FILLED || type == TYPE_ELLIPSE_FILLED;
    if (type == TYPE_RECTANGLE || type == TYPE_RECTANGLE_FILLED) {
      final boolean inside = x >= left && x <= right && y >= top && y <= bottom;
      if (inside && filled) {
        return true;
      }
      final float distance;
      if (inside) {
        distance = Math.min(Math.min(x - left, right - x), Math.min(y - top, bottom - y));
      } else {
        final float dx = Math.max(Math.max(left - x, 0f), x - right);
        final float dy = Math.max(Math.max(top - y, 0f), y - bottom);
        distance = (float) Math.sqrt(dx * dx + dy * dy);
      }
      return distance <= maxDistance;
    }
    final float rx = Math.max(1f, (right - left) / 2f), ry = Math.max(1f, (bottom - top) / 2f);
    final float nx = (x - (left + right) / 2f) / rx, ny = (y - (top + bottom) / 2f) / ry;
    final float r = (float) Math.sqrt(nx * nx + ny * ny);
    if (r <= 1f && filled) {
      return true;
    }
    return Math.abs(r - 1f) * Math.min(rx, ry) <= maxDistance; // close enough to the distance from the outline
  }

  /** The squared distance from (px, py) to the segment (x1, y1)-(x2, y2). */
  static float segmentDistanceSq (float px, float py, float x1, float y1, float x2, float y2) {
    final float dx = x2 - x1, dy = y2 - y1;
    final float lengthSq = dx * dx + dy * dy;
    float t = lengthSq > 0f ? ((px - x1) * dx + (py - y1) * dy) / lengthSq : 0f;
    t = Math.max(0f, Math.min(1f, t));
    final float cx = x1 + dx * t - px, cy = y1 + dy * t - py;
    return cx * cx + cy * cy;
  }

  // Blob

  public void save (RandomAccessFile file) throws IOException {
    file.writeByte((byte) type);

    Blob.writeVarint(file, canvasWidth);
    Blob.writeVarint(file, canvasHeight);
    file.writeFloat(canvasScale);
    if (needsCanvasRotation(type)) {
      file.writeFloat(canvasRotation);
    }

    file.writeInt(color);
    if (needsStrokeRadius(type)) {
      file.writeFloat(strokeRadius);
    }

    savePositionData(file);
  }

  public void save (Blob blob) {
    blob.writeByte((byte) type);

    blob.writeVarint(canvasWidth);
    blob.writeVarint(canvasHeight);
    blob.writeFloat(canvasScale);
    if (needsCanvasRotation(type)) {
      blob.writeFloat(canvasRotation);
    }

    blob.writeInt(color);
    if (needsStrokeRadius(type)) {
      blob.writeFloat(strokeRadius);
    }

    savePositionData(blob);
  }

  // XGram: the other shapes as the rectangle, the brushes as the path

  private static boolean hasCorners (int type) {
    return type == TYPE_ARROW || isShapeType(type);
  }

  private void savePositionData (RandomAccessFile file) throws IOException {
    if (hasCorners(type)) {
      file.writeFloat(x1);
      file.writeFloat(x2);
      file.writeFloat(y1);
      file.writeFloat(y2);
    } else if (isPathType(type)) {
      path.saveData(file);
    }
  }

  private void savePositionData (Blob blob) {
    if (hasCorners(type)) {
      blob.writeFloat(x1);
      blob.writeFloat(x2);
      blob.writeFloat(y1);
      blob.writeFloat(y2);
    } else if (isPathType(type)) {
      path.saveData(blob);
    }
  }

  public int getEstimatedOutputSize () {
    return 1 + Blob.sizeOf(canvasWidth) + Blob.sizeOf(canvasHeight) + 4 + (needsCanvasRotation(type) ? 4 : 0) + 4 + (needsStrokeRadius(type) ? 4 : 0) + getEstimatedPositionSaveSize();
  }

  private int getEstimatedPositionSaveSize () {
    if (hasCorners(type)) {
      return 8 * 4;
    }
    if (isPathType(type)) {
      return path.getEstimatedOutputSize();
    }
    return 0;
  }

  public static SimpleDrawing restore (RandomAccessFile file) throws IOException {
    int type = file.read();
    if (!isKnownType(type)) { // XGram: stop instead of reading the rest out of step
      throw new IOException("Unknown drawing type: " + type);
    }

    int canvasWidth = Blob.readVarint(file);
    int canvasHeight = Blob.readVarint(file);
    float canvasScale = file.readFloat();
    float canvasRotation = 0f;
    if (needsCanvasRotation(type)) {
      canvasRotation = file.readFloat();
    }

    int color = file.readInt();
    float strokeRadius;
    if (needsStrokeRadius(type)) {
      strokeRadius = file.readFloat();
    } else {
      strokeRadius = 0f;
    }

    SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, canvasScale, canvasRotation);
    drawing.setBrushParameters(color, strokeRadius);

    restorePositionData(file, drawing);

    return drawing;
  }

  public static SimpleDrawing restore (Blob blob) {
    int type = blob.readByte();
    if (!isKnownType(type)) { // XGram: stop instead of reading the rest out of step
      throw new IllegalStateException("Unknown drawing type: " + type);
    }

    int canvasWidth = blob.readVarint();
    int canvasHeight = blob.readVarint();
    float canvasScale = blob.readFloat();
    float canvasRotation = 0f;
    if (needsCanvasRotation(type)) {
      canvasRotation = blob.readFloat();
    }

    int color = blob.readInt();
    float strokeRadius;
    if (needsStrokeRadius(type)) {
      strokeRadius = blob.readFloat();
    } else {
      strokeRadius = 0f;
    }

    SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, canvasScale, canvasRotation);
    drawing.setBrushParameters(color, strokeRadius);

    restorePositionData(blob, drawing);

    return drawing;
  }

  private static void restorePositionData (RandomAccessFile file, SimpleDrawing out) throws IOException {
    if (hasCorners(out.type)) {
      out.x1 = file.readFloat();
      out.x2 = file.readFloat();
      out.y1 = file.readFloat();
      out.y2 = file.readFloat();
    } else if (isPathType(out.type)) {
      out.path = new CustomPath(out, file);
    }
  }

  private static void restorePositionData (Blob blob, SimpleDrawing out) {
    if (hasCorners(out.type)) {
      out.x1 = blob.readFloat();
      out.x2 = blob.readFloat();
      out.y1 = blob.readFloat();
      out.y2 = blob.readFloat();
    } else if (isPathType(out.type)) {
      out.path = new CustomPath(out, blob);
    }
  }
}
