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
 *
 * <p>One design for every state (download, queued, loading, paused, open, failed, play and pause, uploading): a
 * circle in one tonal role pair that never changes shape at rest. Loading draws the M3E circular wavy progress
 * indicator inside it (4dp indicator and track, 4dp track gap, 15dp wavelength, 1.6dp amplitude) with a 4dp gap to
 * the circle's edge; queued draws the contained loading indicator; a press springs the corners to 12dp (the M3E
 * medium icon button pressed shape) under a 10% content state layer. Failed uses errorContainer.
 */
public final class XgramM3MediaButton {
  private XgramM3MediaButton () { }

  // Styles

  /** Expressive design off: every Telegram X branch runs unchanged. */
  public static final int NONE = 0;
  /**
   * File, voice and music buttons on the chat surface or in an incoming bubble (surfaceContainerHigh): a tonal
   * primaryContainer / onPrimaryContainer circle, the container pair of the M3E contained loading indicator, so the
   * button, its queued indicator and its ring share one pair. Every file type (APKs, archives and PDFs too).
   */
  public static final int TONAL = 1;
  /**
   * The same buttons in an outgoing bubble, which is primaryContainer itself: the M3 filled icon button roles
   * primary / onPrimary, the only role pair that contrasts with primaryContainer in both light and dark schemes.
   */
  public static final int FILLED = 2;
  /** Any overlay color (photos, videos, GIFs, viewer, wallpapers, inline results): a tonal primaryContainer disk. */
  public static final int MEDIA = 5;
  /** Self-destructing media: keeps the dark overlay and white content. */
  public static final int SCRIM = 6;

  // Geometry (dp)

  /** 48dp button on photos, videos and GIFs (the wavy progress indicator container, WaveSize). */
  public static final float MEDIA_RADIUS = 24f;
  /** 56dp play button (M icon button). */
  public static final float PLAY_RADIUS = 28f;
  /** CircularWavyProgressIndicator tokens: 4dp indicator and track, 1.6dp wave amplitude, 4dp track gap. */
  public static final float RING_STROKE = 4f, RING_AMPLITUDE = 1.6f, RING_GAP = 4f;
  /**
   * From the button's edge to the center line of its ring: a 4dp gap, the wave amplitude and half the stroke, so
   * the wave never touches the edge and the ring reads as a ring inside the circle, not as a wavy edge.
   */
  public static final float RING_INSET = RING_GAP + RING_AMPLITUDE + RING_STROKE / 2f;
  /** 16dp large badge for voice, music and streaming downloads, with a 2dp ring in the bubble color. */
  public static final float BADGE_RADIUS = 8f, BADGE_RING = 2f, BADGE_GLYPH = 12f, BADGE_PROGRESS_RADIUS = 4.5f;

  /** primaryContainer at 92% over a picture. */
  public static final float MEDIA_CONTAINER_ALPHA = .92f;
  /** The ring's track: its indicator color at 24%, so it shows on every container. */
  public static final float RING_TRACK_ALPHA = .24f;
  /** Black 50%: the on-media scrim of chips. */
  public static final int CHIP_SCRIM = 0x80000000;
  /** Telegram X's self-destructing media overlay. */
  public static final int SCRIM_COLOR = 0x66000000;

  /**
   * The style of a button whose background is {@code color} (a color id when {@code isColorId}); {@code outgoing}
   * when it sits in an outgoing bubble.
   */
  public static int resolve (boolean isColorId, int color, boolean scrim, boolean outgoing) {
    if (!isColorId) {
      return scrim ? SCRIM : MEDIA;
    }
    if (color == ColorId.bubbleOut_file) {
      return FILLED;
    }
    if (color == ColorId.file || color == ColorId.fileGreen || color == ColorId.fileYellow || color == ColorId.fileRed) {
      return outgoing ? FILLED : TONAL;
    }
    return NONE;
  }

  /** {@link #resolve(boolean, int, boolean, boolean)} outside a bubble. */
  public static int resolve (boolean isColorId, int color, boolean scrim) {
    return resolve(isColorId, color, scrim, false);
  }

  @ColorInt
  public static int containerColor (int style) {
    switch (style) {
      case TONAL:
        return XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER);
      case FILLED:
        return XgramExpressive.color(XgramExpressive.PRIMARY);
      case MEDIA:
        return XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), MEDIA_CONTAINER_ALPHA);
      default:
        return SCRIM_COLOR;
    }
  }

  @ColorInt
  public static int contentColor (int style) {
    switch (style) {
      case TONAL:
      case MEDIA:
        return XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
      case FILLED:
        return XgramExpressive.color(XgramExpressive.ON_PRIMARY);
      default:
        return 0xffffffff;
    }
  }

  /**
   * The small download badge of voice and music buttons: primary / onPrimary, set off from the button by a ring in
   * the bubble color. Primary contrasts with the tonal button, the filled one (through the ring) and both bubbles.
   */
  @ColorInt
  public static int badgeContainerColor () {
    return XgramExpressive.color(XgramExpressive.PRIMARY);
  }

  @ColorInt
  public static int badgeContentColor () {
    return XgramExpressive.color(XgramExpressive.ON_PRIMARY);
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
