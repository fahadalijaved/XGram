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
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.TextStyleProvider;

/**
 * Material 3 Expressive pieces for the content drawn inside message bubbles: the link preview card, chips over media,
 * the flat linear progress of poll results, pill state layers and the round video ring. Everything here is only used
 * while {@link XgramExpressive#isEnabled()} is true. Drawing helpers are main thread only and allocate nothing per
 * call; {@link #labelLargeProvider()} may be called from the threads that build message layouts.
 */
public final class XgramM3Content {
  private XgramM3Content () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Tokens

  /** On-media scrim: black at 50 %, with white content. */
  public static final int ON_MEDIA_SCRIM = 0x80000000;

  /** Circular wavy progress: 4dp stroke, 1.6dp amplitude. */
  public static final float WAVY_STROKE_DP = 4f, WAVY_AMPLITUDE_DP = 1.6f;

  /** Flat linear progress: 4dp thick, 4dp gap, 4dp stop dot. */
  public static final float LINEAR_THICKNESS_DP = 4f, LINEAR_GAP_DP = 4f;

  /** Link preview card corner. */
  public static final float CARD_CORNER_DP = XgramExpressive.CORNER_M;

  private static Paint fillPaint, strokePaint;
  private static final RectF rect = new RectF();

  private static Paint fill (@ColorInt int color) {
    if (fillPaint == null) {
      fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      fillPaint.setStyle(Paint.Style.FILL);
    }
    fillPaint.setColor(color);
    return fillPaint;
  }

  private static Paint stroke (@ColorInt int color, float width) {
    if (strokePaint == null) {
      strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      strokePaint.setStyle(Paint.Style.STROKE);
      strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }
    strokePaint.setColor(color);
    strokePaint.setStrokeWidth(width);
    return strokePaint;
  }

  // Text

  private static TextPaint onMediaLabelPaint, labelLargeEndPaint, labelMediumEndPaint;
  private static TextStyleProvider labelLargeProvider;

  /** labelMedium (12sp, 500) in white, for chips over media. */
  public static TextPaint onMediaLabelPaint () {
    if (onMediaLabelPaint == null) {
      onMediaLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      onMediaLabelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
      onMediaLabelPaint.setColor(0xffffffff);
    }
    onMediaLabelPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    return onMediaLabelPaint;
  }

  /** labelLarge (14sp, 500), aligned to its end: x is where the text ends. */
  public static TextPaint labelLargeEndPaint (@ColorInt int color) {
    if (labelLargeEndPaint == null) {
      labelLargeEndPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      labelLargeEndPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      labelLargeEndPaint.setTextAlign(Paint.Align.RIGHT);
    }
    labelLargeEndPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    labelLargeEndPaint.setColor(color);
    return labelLargeEndPaint;
  }

  /** labelMedium (12sp, 500), aligned to its end: x is where the text ends. */
  public static TextPaint labelMediumEndPaint (@ColorInt int color) {
    if (labelMediumEndPaint == null) {
      labelMediumEndPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      labelMediumEndPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
      labelMediumEndPaint.setTextAlign(Paint.Align.RIGHT);
    }
    labelMediumEndPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    labelMediumEndPaint.setColor(color);
    return labelMediumEndPaint;
  }

  /** labelLarge (14, weight 500 for regular and bold, so allBold() texts keep it) for texts laid out in a message. */
  public static TextStyleProvider labelLargeProvider () {
    synchronized (XgramM3Content.class) {
      if (labelLargeProvider == null) {
        Typeface label = XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false);
        Fonts.TextPaintStorage storage = new Fonts.TextPaintStorage(label, label, Fonts.getRobotoItalic(), null, Fonts.getRobotoMono(), label, 0);
        labelLargeProvider = new TextStyleProvider(storage).setTextSize(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
      }
      return labelLargeProvider;
    }
  }

  /** Width of a string, measured again only when the string changes. One per owner. */
  public static final class CachedWidth {
    private @Nullable String text;
    private float width;
    private float textSize = -1f;

    public float of (@Nullable String s, TextPaint paint) {
      if (s != text || paint.getTextSize() != textSize) {
        text = s;
        textSize = paint.getTextSize();
        width = s != null ? paint.measureText(s) : 0f;
      }
      return width;
    }
  }

  // Shapes

  /** A full-corner (pill) shape filled with {@code color}. */
  public static void drawPill (Canvas c, float left, float top, float right, float bottom, @ColorInt int color) {
    if (right <= left || bottom <= top || (color >>> 24) == 0) {
      return;
    }
    final float radius = Math.min(right - left, bottom - top) / 2f;
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, fill(color));
  }

  /** A rounded rectangle with {@code radius} corners filled with {@code color}. */
  public static void drawRoundRect (Canvas c, float left, float top, float right, float bottom, float radius, @ColorInt int color) {
    if (right <= left || bottom <= top || (color >>> 24) == 0) {
      return;
    }
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, fill(color));
  }

  /** A full-corner chip on the on-media scrim; {@link #onMediaLabelPaint()} draws its text. */
  public static void drawMediaChip (Canvas c, float left, float top, float right, float bottom) {
    drawPill(c, left, top, right, bottom, ON_MEDIA_SCRIM);
  }

  /**
   * A tonal card with a leading line clipped to its corners (the M3E reply block look). The clip path is cached per
   * card size, so scrolling only translates it. One per owner.
   */
  public static final class TonalCard {
    private final Path path = new Path();
    private float pathWidth = -1f, pathHeight = -1f, pathRadius = -1f;

    public void draw (Canvas c, float left, float top, float right, float bottom, float radius, @ColorInt int fillColor, @ColorInt int lineColor, float lineWidth, boolean lineAtEnd) {
      final float width = right - left, height = bottom - top;
      if (width <= 0f || height <= 0f) {
        return;
      }
      if (width != pathWidth || height != pathHeight || radius != pathRadius) {
        pathWidth = width;
        pathHeight = height;
        pathRadius = radius;
        path.rewind();
        rect.set(0f, 0f, width, height);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
      }
      rect.set(left, top, right, bottom);
      c.drawRoundRect(rect, radius, radius, fill(fillColor));
      if (lineWidth <= 0f || (lineColor >>> 24) == 0) {
        return;
      }
      final int saveCount = c.save();
      c.translate(left, top);
      c.clipPath(path);
      if (lineAtEnd) {
        c.drawRect(width - lineWidth, 0f, width, height, fill(lineColor));
      } else {
        c.drawRect(0f, 0f, lineWidth, height, fill(lineColor));
      }
      c.restoreToCount(saveCount);
    }
  }

  // Progress

  /**
   * M3 flat linear progress (LinearProgressIndicator) from {@code left} to {@code right} centered on {@code cy}: the
   * active part with round caps, a gap, the track at the rest of the width and a stop dot at the end.
   */
  public static void drawLinearProgress (Canvas c, float left, float right, float cy, float progress, @ColorInt int activeColor, @ColorInt int trackColor) {
    final float thickness = Screen.dpf(LINEAR_THICKNESS_DP);
    final float half = thickness / 2f;
    final float width = right - left;
    if (width <= thickness) {
      return;
    }
    progress = Math.max(0f, Math.min(1f, progress));
    final float activeEnd = left + width * progress;
    float trackStart = left;
    if (progress > 0f && (activeColor >>> 24) != 0) {
      final float from = left + half;
      final float to = Math.max(from, activeEnd - half);
      c.drawLine(from, cy, to, cy, stroke(activeColor, thickness));
      trackStart = Math.max(from, to) + half + Screen.dpf(LINEAR_GAP_DP);
    }
    if (progress < 1f) {
      final float trackFrom = trackStart + half;
      final float trackTo = right - half;
      if (trackTo > trackFrom && (trackColor >>> 24) != 0) {
        c.drawLine(trackFrom, cy, trackTo, cy, stroke(trackColor, thickness));
      }
      if ((activeColor >>> 24) != 0 && trackTo >= trackFrom) {
        c.drawCircle(right - half, cy, half, fill(activeColor));
      }
    }
  }

  /** True while a wavy indicator moves on its own and needs the next frame (never under Reduce motion). */
  public static boolean needsWaveFrame (float progress, float wave) {
    return wave > 0f && progress > 0f && progress < 1f && !Settings.instance().needReduceMotion();
  }

  /**
   * The round video ring: a circular wavy indicator in primary on a white track at 24 %, 4dp stroke, its amplitude
   * {@code wave} x 1.6dp (1 while playing, 0 when paused).
   */
  public static void drawRoundVideoRing (Canvas c, float cx, float cy, float radius, float progress, float wave, float alpha) {
    if (alpha <= 0f) {
      return;
    }
    XgramWavyProgress.drawCircular(c, cx, cy, radius, Screen.dpf(WAVY_STROKE_DP), progress,
      XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), alpha),
      XgramExpressive.withAlpha(0xffffffff, .24f * alpha),
      Screen.dpf(WAVY_AMPLITUDE_DP) * Math.max(0f, wave));
  }

  /** Center line radius of the round video ring in a {@code size} circle, so the wave stays inside the clip. */
  public static float roundVideoRingRadius (float size) {
    return size / 2f - Screen.dpf(WAVY_STROKE_DP) / 2f - Screen.dpf(WAVY_AMPLITUDE_DP) - Screen.dpf(.5f);
  }
}
