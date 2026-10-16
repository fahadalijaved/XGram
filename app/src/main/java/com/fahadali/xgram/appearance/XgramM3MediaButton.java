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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive download, play and progress button that FileProgressComponent draws on every photo, video,
 * GIF, round video, document, voice and music message (and in the viewer, wallpapers and inline results), plus the
 * on-media chips around it. Styles and role pairs only; FileProgressComponent keeps the state. Main thread only,
 * except {@link #measureChipText}.
 */
public final class XgramM3MediaButton {
  private XgramM3MediaButton () { }

  // Styles

  /** Expressive design off: every Telegram X branch runs unchanged. */
  public static final int NONE = 0;
  /** Theme file circles (ColorId.file, bubbleOut_file): primary / onPrimary. */
  public static final int ROLE = 1;
  /** APKs (ColorId.fileGreen): tertiary / onTertiary. */
  public static final int ACCENT_GREEN = 2;
  /** Archives (ColorId.fileYellow): secondary / onSecondary. */
  public static final int ACCENT_YELLOW = 3;
  /** PDFs (ColorId.fileRed): error / onError. */
  public static final int ACCENT_RED = 4;
  /** Any overlay color (photos, videos, GIFs, viewer, wallpapers, inline results): a tonal primaryContainer disk. */
  public static final int MEDIA = 5;
  /** Self-destructing media: keeps the dark overlay and white content. */
  public static final int SCRIM = 6;

  // Geometry (dp)

  /** 48dp button on photos, videos and GIFs (the loading indicator container). */
  public static final float MEDIA_RADIUS = 24f;
  /** 56dp play button (M icon button). */
  public static final float PLAY_RADIUS = 28f;
  /** The 4dp ring sits 2dp inside the button. */
  public static final float RING_INSET = 6f;
  /** 16dp large badge for voice, music and streaming downloads, with a 2dp ring in the bubble color. */
  public static final float BADGE_RADIUS = 8f, BADGE_RING = 2f, BADGE_GLYPH = 12f, BADGE_PROGRESS_RADIUS = 4.5f;

  /** primaryContainer at 92% over a picture. */
  public static final float MEDIA_CONTAINER_ALPHA = .92f;
  /** Black 50%: the on-media scrim of chips. */
  public static final int CHIP_SCRIM = 0x80000000;
  /** Telegram X's self-destructing media overlay. */
  public static final int SCRIM_COLOR = 0x66000000;

  /** The style of a button whose background is {@code color} (a color id when {@code isColorId}). */
  public static int resolve (boolean isColorId, int color, boolean scrim) {
    if (!isColorId) {
      return scrim ? SCRIM : MEDIA;
    }
    if (color == ColorId.file || color == ColorId.bubbleOut_file) {
      return ROLE;
    }
    if (color == ColorId.fileGreen) {
      return ACCENT_GREEN;
    }
    if (color == ColorId.fileYellow) {
      return ACCENT_YELLOW;
    }
    if (color == ColorId.fileRed) {
      return ACCENT_RED;
    }
    return NONE;
  }

  @ColorInt
  public static int containerColor (int style) {
    switch (style) {
      case ROLE:
        return XgramExpressive.color(XgramExpressive.PRIMARY);
      case ACCENT_GREEN:
        return XgramExpressive.color(XgramExpressive.TERTIARY);
      case ACCENT_YELLOW:
        return XgramExpressive.color(XgramExpressive.SECONDARY);
      case ACCENT_RED:
        return XgramExpressive.color(XgramExpressive.ERROR);
      case MEDIA:
        return XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), MEDIA_CONTAINER_ALPHA);
      default:
        return SCRIM_COLOR;
    }
  }

  @ColorInt
  public static int contentColor (int style) {
    switch (style) {
      case ROLE:
        return XgramExpressive.color(XgramExpressive.ON_PRIMARY);
      case ACCENT_GREEN:
        return XgramExpressive.color(XgramExpressive.ON_TERTIARY);
      case ACCENT_YELLOW:
        return XgramExpressive.color(XgramExpressive.ON_SECONDARY);
      case ACCENT_RED:
        return XgramExpressive.color(XgramExpressive.ON_ERROR);
      case MEDIA:
        return XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
      default:
        return 0xffffffff;
    }
  }

  // Shapes

  /** A disk of {@code radius}, or a rounded square when {@code cornerPx} is smaller than the radius. */
  public static void drawShape (Canvas c, float cx, float cy, float radius, float cornerPx, @ColorInt int color, RectF rect) {
    if (radius <= 0f) {
      return;
    }
    final Paint paint = Paints.fillingPaint(color);
    if (cornerPx >= radius - .5f) {
      c.drawCircle(cx, cy, radius, paint);
    } else {
      rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
      final float corner = Math.max(0f, cornerPx);
      c.drawRoundRect(rect, corner, corner, paint);
    }
  }

  // Badge glyphs (drawn on a 12dp grid, so they scale with the badge)

  private static Paint glyphPaint;

  private static Paint glyphPaint (@ColorInt int color, float strokePx) {
    if (glyphPaint == null) {
      glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      glyphPaint.setStyle(Paint.Style.STROKE);
      glyphPaint.setStrokeCap(Paint.Cap.ROUND);
      glyphPaint.setStrokeJoin(Paint.Join.ROUND);
    }
    glyphPaint.setStrokeWidth(strokePx);
    glyphPaint.setColor(color);
    return glyphPaint;
  }

  /** Download glyph (an arrow onto a line) filling a box of {@code sizePx}. */
  public static void drawDownload (Canvas c, float cx, float cy, float sizePx, @ColorInt int color) {
    if (sizePx <= 0f || Color.alpha(color) == 0) {
      return;
    }
    final float u = sizePx / 12f;
    final Paint p = glyphPaint(color, 1.5f * u);
    c.drawLine(cx, cy - 4f * u, cx, cy + 1.5f * u, p);
    c.drawLine(cx - 2.75f * u, cy - 1.25f * u, cx, cy + 1.5f * u, p);
    c.drawLine(cx + 2.75f * u, cy - 1.25f * u, cx, cy + 1.5f * u, p);
    c.drawLine(cx - 3.5f * u, cy + 4f * u, cx + 3.5f * u, cy + 4f * u, p);
  }

  /** Check glyph filling a box of {@code sizePx}, drawn up to {@code factor} of its length. */
  public static void drawCheck (Canvas c, float cx, float cy, float sizePx, @ColorInt int color, float factor) {
    if (sizePx <= 0f || factor <= 0f || Color.alpha(color) == 0) {
      return;
    }
    final float u = sizePx / 12f;
    final Paint p = glyphPaint(color, 1.5f * u);
    final float x0 = cx - 3.5f * u, y0 = cy + .25f * u;
    final float x1 = cx - 1f * u, y1 = cy + 2.75f * u;
    final float x2 = cx + 3.75f * u, y2 = cy - 2.5f * u;
    final float l1 = (float) Math.hypot(x1 - x0, y1 - y0);
    final float l2 = (float) Math.hypot(x2 - x1, y2 - y1);
    final float d = (l1 + l2) * Math.min(1f, factor);
    if (d <= l1) {
      final float t = d / l1;
      c.drawLine(x0, y0, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, p);
    } else {
      final float t = (d - l1) / l2;
      c.drawLine(x0, y0, x1, y1, p);
      c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, p);
    }
  }

  // On-media chips (duration, size, streaming status)

  private static TextPaint chipLabelPaint, chipBodyPaint, chipMeasurePaint;

  /** labelMedium (12sp, 500) in white, alpha 255. */
  public static TextPaint chipLabelPaint () {
    if (chipLabelPaint == null) {
      chipLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      chipLabelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
    }
    chipLabelPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    chipLabelPaint.setColor(Color.WHITE);
    return chipLabelPaint;
  }

  /** bodySmall (12sp, 400) in white, alpha 255 (the second line draws it at 80%). */
  public static TextPaint chipBodyPaint () {
    if (chipBodyPaint == null) {
      chipBodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      chipBodyPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_S, false));
    }
    chipBodyPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_S)));
    chipBodyPaint.setColor(Color.WHITE);
    return chipBodyPaint;
  }

  /**
   * Width of {@code text} in the chip label style (the wider of the two chip styles). Safe off the main thread
   * (media messages are laid out in the background); called at layout time only.
   */
  public static int measureChipText (@Nullable String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    synchronized (XgramM3MediaButton.class) {
      if (chipMeasurePaint == null) {
        chipMeasurePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        chipMeasurePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
      }
      chipMeasurePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
      return (int) U.measureText(text, chipMeasurePaint);
    }
  }
}
