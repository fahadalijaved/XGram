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

import org.thunderdog.challegram.unsorted.Settings;

/**
 * Material 3 Expressive linear progress (Compose LinearWavyProgressIndicator and the Expressive slider), drawn
 * straight on a Canvas: the active indicator from the start to the progress, a gap, then the flat inactive track
 * with a stop dot at its end. With an amplitude the indicator is a sine wave that travels one wavelength a second
 * and flattens near 0 % and 100 % ({@link XgramWavyProgress#amplitudeFactor}); with a handle, a bar sits at the
 * progress with the gap on both sides. Spec tokens (material3 1.5): 4dp thick, 4dp gap, 4dp stop dot, amplitude
 * 3dp, wavelength 40dp. No allocation per call. Main thread only. The phase is 0 under Reduce motion.
 */
public final class XgramWavyLinear {
  private XgramWavyLinear () { }

  private static final long WAVE_PERIOD_MS = 1000L;
  /** Distance between two samples of the wave. */
  private static final float STEP_PX = 2f;

  private static final Path path = new Path();
  private static final RectF rect = new RectF();
  private static Paint stroke, fill;

  private static void ensurePaints () {
    if (stroke == null) {
      stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeCap(Paint.Cap.ROUND);
      stroke.setStrokeJoin(Paint.Join.ROUND);
      fill = new Paint(Paint.ANTI_ALIAS_FLAG);
      fill.setStyle(Paint.Style.FILL);
    }
  }

  /** True while the wave moves: the caller redraws every frame then (postInvalidateOnAnimation). */
  public static boolean isWaveMoving (float progress, float amplitudePx) {
    return amplitudePx * XgramWavyProgress.amplitudeFactor(progress) >= .5f && !Settings.instance().needReduceMotion();
  }

  /**
   * Indicator and track between {@code left} and {@code right}, centred on {@code cy}.
   *
   * @param amplitudePx peak wave amplitude; 0 for a flat indicator
   * @param stopIndicator draws the 4dp stop dot (thickness wide) at the end of the track
   */
  public static void draw (Canvas c, float left, float right, float cy, float progress, @ColorInt int indicator, @ColorInt int track,
                           float thicknessPx, float amplitudePx, float wavelengthPx, float gapPx, boolean stopIndicator) {
    draw(c, left, right, cy, progress, indicator, track, thicknessPx, amplitudePx, wavelengthPx, gapPx, stopIndicator, 0f, 0f);
  }

  /**
   * As {@link #draw(Canvas, float, float, float, float, int, int, float, float, float, float, boolean)}, with a
   * {@code handleWidthPx} x {@code handleHeightPx} bar (the indicator color) at the progress, the gap on both sides.
   */
  public static void draw (Canvas c, float left, float right, float cy, float progress, @ColorInt int indicator, @ColorInt int track,
                           float thicknessPx, float amplitudePx, float wavelengthPx, float gapPx, boolean stopIndicator,
                           float handleWidthPx, float handleHeightPx) {
    if (right <= left || thicknessPx <= 0f) {
      return;
    }
    ensurePaints();
    progress = Math.max(0f, Math.min(1f, progress));
    final float half = thicknessPx / 2f;
    final float start = left + half, end = right - half; // centre line: the round caps stay inside left..right
    final boolean handle = handleWidthPx > 0f && handleHeightPx > 0f;
    final float width = right - left;

    float activeEnd, trackStart;
    float handleX = 0f;
    if (handle) {
      handleX = Math.max(left + handleWidthPx / 2f, Math.min(right - handleWidthPx / 2f, left + width * progress));
      activeEnd = handleX - handleWidthPx / 2f - gapPx - half;
      trackStart = handleX + handleWidthPx / 2f + gapPx + half;
    } else {
      float x = left + width * progress;
      activeEnd = Math.min(end, x - half);
      trackStart = progress > 0f ? x + gapPx + half : start;
    }

    stroke.setStrokeWidth(thicknessPx);

    // Inactive track and its stop dot
    if ((track >>> 24) != 0 && trackStart <= end) {
      stroke.setColor(track);
      c.drawLine(trackStart, cy, end, cy, stroke);
    }
    if (stopIndicator && (indicator >>> 24) != 0 && trackStart <= end && progress < 1f) {
      fill.setColor(indicator);
      c.drawCircle(end, cy, half, fill);
    }

    // Active indicator
    if ((indicator >>> 24) != 0 && progress > 0f && activeEnd >= start) {
      stroke.setColor(indicator);
      final float amplitude = amplitudePx * XgramWavyProgress.amplitudeFactor(progress);
      if (amplitude < .5f || wavelengthPx <= 0f || activeEnd - start < STEP_PX) {
        c.drawLine(start, cy, activeEnd, cy, stroke);
      } else {
        final double phase = Settings.instance().needReduceMotion() ? 0.0 : 2.0 * Math.PI * (double) (SystemClock.uptimeMillis() % WAVE_PERIOD_MS) / WAVE_PERIOD_MS;
        final double k = 2.0 * Math.PI / wavelengthPx;
        final int steps = Math.max(2, (int) Math.ceil((activeEnd - start) / STEP_PX));
        path.rewind();
        for (int i = 0; i <= steps; i++) {
          final float x = start + (activeEnd - start) * i / (float) steps;
          final float y = cy + (float) (amplitude * Math.sin(k * (x - left) - phase));
          if (i == 0) {
            path.moveTo(x, y);
          } else {
            path.lineTo(x, y);
          }
        }
        c.drawPath(path, stroke);
      }
    }

    // Handle
    if (handle && (indicator >>> 24) != 0) {
      fill.setColor(indicator);
      final float r = handleWidthPx / 2f;
      rect.set(handleX - r, cy - handleHeightPx / 2f, handleX + r, cy + handleHeightPx / 2f);
      c.drawRoundRect(rect, r, r, fill);
    }
  }
}
