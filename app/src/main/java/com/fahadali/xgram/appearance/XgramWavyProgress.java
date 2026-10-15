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
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Material 3 Expressive wavy circular progress (Compose CircularWavyProgressIndicator, experimental): the indicator
 * is a sine wave along the arc that travels one wavelength a second, the track a flat arc with a 4dp gap at both
 * ends. The wave flattens near 0 % and 100 %. Main thread only.
 */
public final class XgramWavyProgress {
  private XgramWavyProgress () { }

  private static final float WAVELENGTH_DP = 15f, GAP_DP = 4f;
  private static final long WAVE_PERIOD_MS = 1000L;

  private static final Path path = new Path();
  private static final RectF arc = new RectF();
  private static Paint paint;

  /** 0 at 0 %, full from 10 % to 95 %, 0 again at 100 %. */
  public static float amplitudeFactor (float progress) {
    if (progress <= 0f || progress >= 1f) {
      return 0f;
    }
    if (progress < .1f) {
      return progress / .1f;
    }
    if (progress > .95f) {
      return (1f - progress) / .05f;
    }
    return 1f;
  }

  /**
   * Determinate ring around (cx, cy) starting at 12 o'clock.
   *
   * @param radius center line of the stroke
   * @param amplitudePx peak wave amplitude, min(1.5dp, stroke x .4) by default
   */
  public static void drawCircular (Canvas c, float cx, float cy, float radius, float strokePx, float progress, @ColorInt int indicatorColor, @ColorInt int trackColor, float amplitudePx) {
    if (radius <= 0f) {
      return;
    }
    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
    }
    progress = Math.max(0f, Math.min(1f, progress));
    paint.setStrokeWidth(strokePx);
    final float sweep = 360f * progress;
    final float gapDeg = (float) Math.toDegrees((Screen.dp(GAP_DP) + strokePx) / radius);

    if ((trackColor >>> 24) != 0 && progress < 1f) {
      arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
      paint.setColor(trackColor);
      if (sweep <= 0f) {
        c.drawCircle(cx, cy, radius, paint);
      } else {
        float trackSweep = 360f - sweep - gapDeg * 2f;
        if (trackSweep > 0f) {
          c.drawArc(arc, -90f + sweep + gapDeg, trackSweep, false, paint);
        }
      }
    }

    if (sweep <= 0f || (indicatorColor >>> 24) == 0) {
      return;
    }
    final double circumference = 2.0 * Math.PI * radius;
    final int waves = Math.max(3, (int) Math.round(circumference / Screen.dp(WAVELENGTH_DP)));
    final float amplitude = amplitudePx * amplitudeFactor(progress);
    final double phase = Settings.instance().needReduceMotion() ? 0.0 : 2.0 * Math.PI * (double) (SystemClock.uptimeMillis() % WAVE_PERIOD_MS) / WAVE_PERIOD_MS;
    final int steps = Math.max(4, (int) Math.ceil(sweep / 1.5f));
    path.rewind();
    for (int i = 0; i <= steps; i++) {
      final double deg = sweep * i / (double) steps;
      final double angle = Math.toRadians(deg - 90.0);
      final double r = radius + amplitude * Math.sin(waves * Math.toRadians(deg) - phase);
      final float x = (float) (cx + r * Math.cos(angle));
      final float y = (float) (cy + r * Math.sin(angle));
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    paint.setColor(indicatorColor);
    c.drawPath(path, paint);
  }
}
