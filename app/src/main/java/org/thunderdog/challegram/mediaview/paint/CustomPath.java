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
 * File created on 11/01/2018
 */
package org.thunderdog.challegram.mediaview.paint;

import android.graphics.Path;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import me.vkryl.core.collection.FloatList;
import me.vkryl.core.util.Blob;

public class CustomPath {
  private final SimpleDrawing context;
  private final Path path;

  private int width, height;

  private static final int ACTION_MOVE_TO = 0; // x,y
  private static final int ACTION_LINE_TO = 1; // x,y
  private static final int ACTION_QUAD_TO = 2; // x1,y1, x2,y2
  private static final int ACTION_CUBIC_TO = 3; // x1,y1, x2,y2, x3,y3
  private final FloatList actions;

  public CustomPath (SimpleDrawing context) {
    this.context = context;
    this.path = new Path();

    this.width = context.canvasWidth;
    this.height = context.canvasHeight;

    this.actions = new FloatList(120);
  }

  public boolean isEmpty () {
    return path.isEmpty();
  }

  public int getActionCount () {
    return actions.size();
  }

  public CustomPath (SimpleDrawing context, Blob blob) {
    this.context = context;
    this.path = new Path();

    int size = blob.readVarint();
    float[] data = new float[size];
    for (int i = 0; i < size; i++) {
      data[i] = blob.readFloat();
    }
    this.actions = new FloatList(data);
  }

  public CustomPath (SimpleDrawing context, RandomAccessFile file) throws IOException {
    this.context = context;
    this.path = new Path();

    int size = Blob.readVarint(file);
    float[] data = new float[size];
    for (int i = 0; i < size; i++) {
      data[i] = file.readFloat();
    }
    this.actions = new FloatList(data);
  }

  public boolean compare (CustomPath path) {
    return path == this || path.actions == actions || (path.actions.size() == actions.size() && Arrays.equals(path.actions.get(), actions.get()));
  }

  public void saveData (RandomAccessFile file) throws IOException {
    int size = actions.size();
    Blob.writeVarint(file, size);
    for (int i = 0; i < size; i++) {
      file.writeFloat(actions.get(i));
    }
  }

  public void saveData (Blob out) {
    int size = actions.size();
    out.writeVarint(size);
    for (int i = 0; i < size; i++) {
      out.writeFloat(actions.get(i));
    }
  }

  public int getEstimatedOutputSize () {
    int size = actions.size();
    return Blob.sizeOf(size) + size * 4;
  }

  public void trim () {
    actions.trim();
  }

  private void rebuild (int width, int height) {
    path.reset();
    final int size = actions.size();
    int i = 0;
    while (i < size) {
      int type = (int) actions.get(i++);
      switch (type) {
        case ACTION_MOVE_TO: {
          float x = actions.get(i++);
          float y = actions.get(i++);
          path.moveTo(width * x, height * y);
          break;
        }
        case ACTION_LINE_TO: {
          float x = actions.get(i++);
          float y = actions.get(i++);
          path.lineTo(width * x, height * y);
          break;
        }
        case ACTION_QUAD_TO: {
          float x1 = actions.get(i++);
          float y1 = actions.get(i++);
          float x2 = actions.get(i++);
          float y2 = actions.get(i++);
          path.quadTo(width * x1, height * y1, width * x2, height * y2);
          break;
        }
        case ACTION_CUBIC_TO: {
          float x1 = actions.get(i++);
          float y1 = actions.get(i++);
          float x2 = actions.get(i++);
          float y2 = actions.get(i++);
          float x3 = actions.get(i++);
          float y3 = actions.get(i++);
          path.cubicTo(width * x1, height * y1, width * x2, height * y2, width * x3, height * y3);
          break;
        }
        default: {
          throw new IllegalArgumentException("type == " + type);
        }
      }
    }
  }

  private void appendAction (int action, float x, float y) {
    actions.append(action);
    actions.append(context.getRelativeX(x));
    actions.append(context.getRelativeY(y));
  }

  private void appendAction (int action, float x1, float y1, float x2, float y2) {
    actions.append(action);
    actions.append(context.getRelativeX(x1));
    actions.append(context.getRelativeY(y1));
    actions.append(context.getRelativeX(x2));
    actions.append(context.getRelativeY(y2));
  }

  private void appendAction (int action, float x1, float y1, float x2, float y2, float x3, float y3) {
    actions.append(action);
    actions.append(context.getRelativeX(x1));
    actions.append(context.getRelativeY(y1));
    actions.append(context.getRelativeX(x2));
    actions.append(context.getRelativeY(y2));
    actions.append(context.getRelativeX(x3));
    actions.append(context.getRelativeY(y3));
  }

  public void moveTo (float x, float y) {
    path.moveTo(x, y);
    appendAction(ACTION_MOVE_TO, x, y);
  }

  public void lineTo (float x, float y) {
    path.lineTo(x, y);
    appendAction(ACTION_LINE_TO, x, y);
  }

  public void quadTo (float x1, float y1, float x2, float y2) {
    path.quadTo(x1, y1, x2, y2);
    appendAction(ACTION_QUAD_TO, x1, y1, x2, y2);
  }

  public void cubicTo (float x1, float y1, float x2, float y2, float x3, float y3) {
    path.cubicTo(x1, y1, x2, y2, x3, y3);
    appendAction(ACTION_CUBIC_TO, x1, y1, x2, y2, x3, y3);
  }

  // XGram: the eraser's hit test on the path as drawn at width x height: whether (x, y) is within maxDistance of it.
  // Curves are followed through four points on each.

  private static final int HIT_STEPS = 4;

  public boolean hitTest (float x, float y, int width, int height, float maxDistance) {
    final float maxSq = maxDistance * maxDistance;
    final int size = actions.size();
    float lastX = 0f, lastY = 0f;
    int i = 0;
    while (i < size) {
      int type = (int) actions.get(i++);
      switch (type) {
        case ACTION_MOVE_TO: {
          lastX = width * actions.get(i++);
          lastY = height * actions.get(i++);
          if (SimpleDrawing.segmentDistanceSq(x, y, lastX, lastY, lastX, lastY) <= maxSq) {
            return true;
          }
          break;
        }
        case ACTION_LINE_TO: {
          float x1 = width * actions.get(i++);
          float y1 = height * actions.get(i++);
          if (SimpleDrawing.segmentDistanceSq(x, y, lastX, lastY, x1, y1) <= maxSq) {
            return true;
          }
          lastX = x1;
          lastY = y1;
          break;
        }
        case ACTION_QUAD_TO: {
          float cx = width * actions.get(i++);
          float cy = height * actions.get(i++);
          float x2 = width * actions.get(i++);
          float y2 = height * actions.get(i++);
          float prevX = lastX, prevY = lastY;
          for (int step = 1; step <= HIT_STEPS; step++) {
            float t = (float) step / (float) HIT_STEPS, u = 1f - t;
            float px = u * u * lastX + 2f * u * t * cx + t * t * x2;
            float py = u * u * lastY + 2f * u * t * cy + t * t * y2;
            if (SimpleDrawing.segmentDistanceSq(x, y, prevX, prevY, px, py) <= maxSq) {
              return true;
            }
            prevX = px;
            prevY = py;
          }
          lastX = x2;
          lastY = y2;
          break;
        }
        case ACTION_CUBIC_TO: {
          float c1x = width * actions.get(i++);
          float c1y = height * actions.get(i++);
          float c2x = width * actions.get(i++);
          float c2y = height * actions.get(i++);
          float x3 = width * actions.get(i++);
          float y3 = height * actions.get(i++);
          float prevX = lastX, prevY = lastY;
          for (int step = 1; step <= HIT_STEPS; step++) {
            float t = (float) step / (float) HIT_STEPS, u = 1f - t;
            float px = u * u * u * lastX + 3f * u * u * t * c1x + 3f * u * t * t * c2x + t * t * t * x3;
            float py = u * u * u * lastY + 3f * u * u * t * c1y + 3f * u * t * t * c2y + t * t * t * y3;
            if (SimpleDrawing.segmentDistanceSq(x, y, prevX, prevY, px, py) <= maxSq) {
              return true;
            }
            prevX = px;
            prevY = py;
          }
          lastX = x3;
          lastY = y3;
          break;
        }
        default: {
          return false;
        }
      }
    }
    return false;
  }

  public Path getPath (int width, int height) {
    if (this.width != width || this.height != height) {
      this.width = width;
      this.height = height;
      rebuild(width, height);
    }
    return path;
  }
}
