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
import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.TextStyleProvider;

/**
 * XGram M3E wave 2: the cards and pills drawn inside the message list with Expressive design on.
 * <ul>
 *   <li>Service messages ("X pinned a video", "X joined the group"): the same chip as the date separators, labelMedium
 *   (12sp, weight 500) on a full-corner pill, 12dp from the text on both sides.</li>
 *   <li>Contact and location messages: an inner card with 12dp corners (the link preview card, XgramM3Content), titleMedium
 *   over bodyMedium, and the actions as a connected button group (size S: 40dp tall, 16dp label padding, 2dp gaps,
 *   full outer and 8dp inner corners, 4dp while pressed; tonal secondaryContainer / onSecondaryContainer, labelLarge).</li>
 * </ul>
 * Paints used for measuring are separate from the drawing paints and synchronized: messages are laid out off the main
 * thread. Drawing helpers are main thread only and allocate nothing per call.
 */
public final class XgramW2Cards {
  private XgramW2Cards () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Tokens (dp)

  /** Inner card corner: 12dp (CornerMedium; the bubble's 20dp minus its 8dp content padding). */
  public static final float CARD_CORNER = XgramExpressive.CORNER_M;
  /** Inner card padding. */
  public static final float CARD_PADDING = 12f;
  /** List item leading avatar. */
  public static final float AVATAR = 40f;
  /** Space between the leading avatar and the text (M3 list item). */
  public static final float AVATAR_GAP = 16f;
  /** Connected button group, size S. */
  public static final float BUTTON_HEIGHT = 40f, BUTTON_PADDING = 16f;
  /** Space between the text block and the buttons. */
  public static final float BUTTONS_GAP = 12f;
  /** titleMedium and bodyMedium line heights. */
  public static final float TITLE_LINE = 24f, BODY_LINE = 20f;
  /** Service chip: horizontal padding around the text (the date separator's 12dp). */
  public static final float SERVICE_PADDING_H = XgramR5Chat.CHIP_PADDING;

  // Colors

  /** The inner card's fill: onPrimaryContainer at 8% in outgoing bubbles, primary at 10% elsewhere (link previews). */
  @ColorInt
  public static int cardColor (boolean outgoing) {
    return outgoing ?
      XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), .08f) :
      XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), .10f);
  }

  @ColorInt
  public static int buttonColor () {
    return XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
  }

  @ColorInt
  public static int buttonContentColor () {
    return XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
  }

  // Text

  private static TextStyleProvider serviceProvider;
  private static TextPaint titleMeasure, bodyMeasure, labelMeasure;
  private static TextPaint titleDraw, bodyDraw, labelDraw;
  private static final Paint.FontMetrics drawMetrics = new Paint.FontMetrics();

  /**
   * Service message text: labelMedium (12, weight 500 for regular and bold, so allBold() keeps it), the date
   * separator's style. Safe off the main thread.
   */
  public static TextStyleProvider serviceLabelProvider () {
    synchronized (XgramW2Cards.class) {
      if (serviceProvider == null) {
        Typeface label = XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false);
        Fonts.TextPaintStorage storage = new Fonts.TextPaintStorage(label, label, Fonts.getRobotoItalic(), null, Fonts.getRobotoMono(), label, 0);
        serviceProvider = new TextStyleProvider(storage).setTextSize(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M));
      }
      return serviceProvider;
    }
  }

  private static TextPaint newPaint (int ts) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(ts)));
    return paint;
  }

  /** titleMedium (16sp, 500) for measuring and ellipsizing. Any thread; use the result inside the lock only for measuring. */
  public static synchronized float measureTitle (@NonNull String text, boolean fakeBold) {
    if (titleMeasure == null) {
      titleMeasure = newPaint(XgramExpressive.TS_TITLE_M);
    }
    titleMeasure.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
    titleMeasure.setFakeBoldText(fakeBold);
    return titleMeasure.measureText(text);
  }

  public static synchronized String ellipsizeTitle (@NonNull String text, boolean fakeBold, float maxWidth) {
    if (titleMeasure == null) {
      titleMeasure = newPaint(XgramExpressive.TS_TITLE_M);
    }
    titleMeasure.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
    titleMeasure.setFakeBoldText(fakeBold);
    return android.text.TextUtils.ellipsize(text, titleMeasure, Math.max(0f, maxWidth), android.text.TextUtils.TruncateAt.END).toString();
  }

  /** bodyMedium (14sp, 400). */
  public static synchronized float measureBody (@NonNull String text) {
    if (bodyMeasure == null) {
      bodyMeasure = newPaint(XgramExpressive.TS_BODY_M);
    }
    bodyMeasure.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
    return bodyMeasure.measureText(text);
  }

  public static synchronized String ellipsizeBody (@NonNull String text, float maxWidth) {
    if (bodyMeasure == null) {
      bodyMeasure = newPaint(XgramExpressive.TS_BODY_M);
    }
    bodyMeasure.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
    return android.text.TextUtils.ellipsize(text, bodyMeasure, Math.max(0f, maxWidth), android.text.TextUtils.TruncateAt.END).toString();
  }

  /** labelLarge (14sp, 500): button labels. */
  public static synchronized float measureLabel (@NonNull String text) {
    if (labelMeasure == null) {
      labelMeasure = newPaint(XgramExpressive.TS_LABEL_L);
    }
    labelMeasure.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    return labelMeasure.measureText(text);
  }

  /** Main thread: titleMedium in {@code color}. */
  public static TextPaint titlePaint (@ColorInt int color, boolean fakeBold) {
    if (titleDraw == null) {
      titleDraw = newPaint(XgramExpressive.TS_TITLE_M);
    }
    titleDraw.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
    titleDraw.setFakeBoldText(fakeBold);
    titleDraw.setColor(color);
    return titleDraw;
  }

  /** Main thread: bodyMedium in {@code color}. */
  public static TextPaint bodyPaint (@ColorInt int color) {
    if (bodyDraw == null) {
      bodyDraw = newPaint(XgramExpressive.TS_BODY_M);
    }
    bodyDraw.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
    bodyDraw.setColor(color);
    return bodyDraw;
  }

  /** Main thread: labelLarge in {@code color}, centred on x. */
  public static TextPaint labelPaint (@ColorInt int color) {
    if (labelDraw == null) {
      labelDraw = newPaint(XgramExpressive.TS_LABEL_L);
      labelDraw.setTextAlign(Paint.Align.CENTER);
    }
    labelDraw.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    labelDraw.setColor(color);
    return labelDraw;
  }

  /** Main thread: the baseline that centres one line of {@code paint} in a line box from {@code top} to {@code top + lineHeight}. */
  public static float baseline (@NonNull Paint paint, float top, float lineHeight) {
    paint.getFontMetrics(drawMetrics);
    return top + (lineHeight - (drawMetrics.descent - drawMetrics.ascent)) / 2f - drawMetrics.ascent;
  }

  // Shapes

  private static Paint fillPaint;
  private static final RectF rect = new RectF();

  private static Paint fill (@ColorInt int color) {
    if (fillPaint == null) {
      fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      fillPaint.setStyle(Paint.Style.FILL);
    }
    fillPaint.setColor(color);
    return fillPaint;
  }

  /** The inner card (12dp corners) filled with {@code color}. */
  public static void drawCard (@NonNull Canvas c, float left, float top, float right, float bottom, @ColorInt int color) {
    if (right <= left || bottom <= top || (color >>> 24) == 0) {
      return;
    }
    float radius = Math.min(Screen.dpf(CARD_CORNER), Math.min(right - left, bottom - top) / 2f);
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, fill(color));
  }

  /** The date separator chip's height (TGMessage.drawDate with Expressive design): one line is a 26dp pill. */
  public static final float SERVICE_PILL_HEIGHT = XgramR5Chat.CHIP_HEIGHT;

  /**
   * The service chip around a laid out text: 12dp on both sides (the date chip's padding), 5dp above and 4dp below
   * the text's line boxes (as Telegram X lays the pill out), at least the date chip's 26dp (grown evenly around the
   * text), corners of the 26dp pill (13dp): one line is a full-corner pill like the date separators, more lines keep
   * the same corner.
   */
  public static void drawServicePill (@NonNull Canvas c, float textLeft, float textTop, float textRight, float textBottom, @ColorInt int color) {
    if ((color >>> 24) == 0 || textRight <= textLeft) {
      return;
    }
    final float h = Screen.dpf(SERVICE_PADDING_H);
    float top = textTop - Screen.dpf(5f), bottom = textBottom + Screen.dpf(4f);
    final float minHeight = Screen.dpf(SERVICE_PILL_HEIGHT);
    if (bottom - top < minHeight) {
      final float grow = (minHeight - (bottom - top)) / 2f;
      top -= grow;
      bottom += grow;
    }
    rect.set(textLeft - h, top, textRight + h, bottom);
    final float radius = Math.min(minHeight / 2f, rect.height() / 2f);
    c.drawRoundRect(rect, radius, radius, fill(color));
  }

  /**
   * One button of a connected group laid out left to right ({@code col} of {@code count}; a lone button is a pill),
   * with its pressed state layer. {@code press} is 0..1 (the spring may overshoot). {@code path} and {@code radii}
   * (float[8]) and {@code cornersDp} (float[4]) belong to the caller and are reused.
   */
  public static void drawConnectedButton (@NonNull Canvas c, @NonNull RectF bounds, int col, int count, float press,
                                          @ColorInt int containerColor, @ColorInt int contentColor,
                                          @NonNull Path path, @NonNull float[] cornersDp, @NonNull float[] radii) {
    final boolean single = count <= 1;
    XgramM3Connected.radii(single, true, true, col, count, cornersDp);
    XgramM3Connected.radiiPx(cornersDp, bounds.width(), bounds.height(), XgramM3Connected.pressedCorner(single), press, radii);
    path.rewind();
    path.addRoundRect(bounds, radii, Path.Direction.CW);
    c.drawPath(path, fill(containerColor));
    if (press > 0f) {
      c.drawPath(path, fill(XgramExpressive.withAlpha(contentColor, XgramExpressive.STATE_PRESSED * Math.min(1f, press))));
    }
  }
}
