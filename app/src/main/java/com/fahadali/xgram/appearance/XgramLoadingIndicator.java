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
 * The motion follows androidx.compose.material3 LoadingIndicator (Apache License 2.0,
 * Copyright The Android Open Source Project): seven expressive shapes morph into each other,
 * each morph driven by a spring (damping 0.6, stiffness 200, visibility threshold 0.1) every 650 ms,
 * while the whole indicator turns once every 4666 ms.
 */
package com.fahadali.xgram.appearance;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.graphics.shapes.Morph;

/** Material 3 Expressive indeterminate loading indicator, drawn straight on a Canvas (main thread only). */
public final class XgramLoadingIndicator {
  public static final long MORPH_INTERVAL_MS = 650L;
  public static final long GLOBAL_ROTATION_MS = 4666L;
  /** Active indicator 38dp inside its 48dp container. */
  public static final float ACTIVE_INDICATOR_SCALE = 38f / 48f;

  private static final float MORPH_STIFFNESS = 200f;
  private static final float MORPH_DAMPING = .6f;
  private static final float MORPH_VISIBILITY_THRESHOLD = .1f;
  /** Time after which Compose considers the morph spring settled and snaps it to 1 (estimateAnimationDurationMillis). */
  private static final float MORPH_SETTLE_SECONDS = settleSeconds(MORPH_STIFFNESS, MORPH_DAMPING, MORPH_VISIBILITY_THRESHOLD);

  private static final Path path = new Path();
  private static final Matrix matrix = new Matrix();
  private static Paint paint;
  private static float scaleFactor = -1f;

  private XgramLoadingIndicator () { }

  /**
   * @param activeSizePx size of the active indicator (38dp in a 48dp container)
   * @param elapsedMs time since the indicator started
   */
  public static void draw (Canvas c, float cx, float cy, float activeSizePx, int color, long elapsedMs) {
    if (activeSizePx <= 0f || Color.alpha(color) == 0) {
      return;
    }
    final Morph[] morphs = XgramShapes.loadingMorphs();
    if (morphs == null || morphs.length == 0) {
      return;
    }
    if (scaleFactor < 0f) {
      scaleFactor = XgramShapes.scaleFactor(XgramShapes.loadingSequence());
    }
    final long t = Math.max(0L, elapsedMs);
    final long cycle = t / MORPH_INTERVAL_MS;
    final float local = (float) (t % MORPH_INTERVAL_MS) / 1000f;
    final float progress = local >= MORPH_SETTLE_SECONDS ? 1f : XgramExpressive.springProgress(MORPH_STIFFNESS, MORPH_DAMPING, local);
    final Morph morph = morphs[(int) (cycle % morphs.length)];
    final float rotation = 90f * progress + (float) ((90L * cycle) % 360L) + 360f * ((float) (t % GLOBAL_ROTATION_MS) / (float) GLOBAL_ROTATION_MS);

    XgramShapes.toPath(morph, progress, path);
    XgramShapes.fit(path, cx, cy, activeSizePx * scaleFactor, rotation, matrix);

    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setStyle(Paint.Style.FILL);
    }
    paint.setColor(color);
    c.drawPath(path, paint);
  }

  /** Port of the under-damped branch of Compose's spring duration estimate (unit displacement, no velocity). */
  private static float settleSeconds (float stiffness, float dampingRatio, float threshold) {
    final double w = Math.sqrt(stiffness);
    if (dampingRatio >= 1f) {
      return Float.MAX_VALUE;
    }
    final double r = -dampingRatio * w;
    final double wd = w * Math.sqrt(1.0 - dampingRatio * dampingRatio);
    final double c2 = -r / wd;
    final double amplitude = Math.sqrt(1.0 + c2 * c2);
    return (float) (Math.log(threshold / amplitude) / r);
  }
}
