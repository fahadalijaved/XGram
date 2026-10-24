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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramFonts;

import org.thunderdog.challegram.tool.Fonts;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.util.Blob;

/**
 * A text layer of the photo editor: its text in one of four fonts (Google Sans Flex, the app font's Rounded, a serif
 * and a monospace one), plain, outlined or on a bubble behind each line, aligned to the start, the centre or the end.
 * Its lines break where they broke in the editor's text field (the field's width is kept in font sizes) and it is
 * laid out once, at one size, then scaled, so the editor, thumbnails and the sent photo show the same lines.
 */
public final class XgramTextLayer extends XgramLayer {
  public static final int FONT_FLEX = 0, FONT_ROUNDED = 1, FONT_SERIF = 2, FONT_MONO = 3;
  /** In the order of the editor's font chips. */
  public static final int[] FONTS = {FONT_FLEX, FONT_ROUNDED, FONT_SERIF, FONT_MONO};

  public static final int STYLE_PLAIN = 0, STYLE_OUTLINE = 1, STYLE_BUBBLE = 2;
  public static final int ALIGN_START = 0, ALIGN_CENTER = 1, ALIGN_END = 2;

  /** Text is laid out at this size and scaled to the layer's, so its lines break the same at every size. */
  private static final float LAYOUT_SIZE = 100f;
  /** Shares of the font size: the outline's width, the bubble's padding and corners. */
  private static final float OUTLINE_WIDTH = .16f, BUBBLE_PADDING_X = .3f, BUBBLE_PADDING_Y = .08f, BUBBLE_RADIUS = .3f;
  /** The longest line, in font sizes, when nothing else is known. */
  public static final float DEFAULT_WRAP_EMS = 10f;

  private String text = "";
  private int font = FONT_FLEX, style = STYLE_PLAIN, align = ALIGN_CENTER;
  private int color = 0xffffffff;
  /** The longest line, in font sizes: the width of the editor's text field it was typed in. */
  private float wrapEms = DEFAULT_WRAP_EMS;

  // Laid out at LAYOUT_SIZE on the first draw; inkBounds is around the ink's centre (originX, originY)
  private @Nullable StaticLayout layout;
  private float originX, originY;
  private final RectF inkBounds = new RectF();
  private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF lineRect = new RectF();

  public XgramTextLayer () { }

  @Override
  public int getType () {
    return TYPE_TEXT;
  }

  public void setText (@NonNull String text, int font, int style, int align, int color, float wrapEms) {
    synchronized (this) {
      this.text = text;
      this.font = clampFont(font);
      this.style = clampStyle(style);
      this.align = clampAlign(align);
      this.color = color | 0xff000000;
      this.wrapEms = wrapEms > 0f ? wrapEms : DEFAULT_WRAP_EMS;
      this.layout = null;
    }
    invalidateOwner();
  }

  public String getText () {
    return text;
  }

  public int getFont () {
    return font;
  }

  public int getStyle () {
    return style;
  }

  public int getAlign () {
    return align;
  }

  public int getColor () {
    return color;
  }

  public float getWrapEms () {
    return wrapEms;
  }

  @NonNull
  @Override
  public XgramLayer copy () {
    XgramTextLayer copy = new XgramTextLayer();
    copyPositionTo(copy);
    copy.setContent(this);
    return copy;
  }

  @Override
  protected void setContent (@NonNull XgramLayer other) {
    XgramTextLayer source = (XgramTextLayer) other;
    synchronized (this) {
      this.text = source.text;
      this.font = source.font;
      this.style = source.style;
      this.align = source.align;
      this.color = source.color;
      this.wrapEms = source.wrapEms;
      this.layout = null;
    }
  }

  @Override
  protected boolean contentEquals (@NonNull XgramLayer other) {
    XgramTextLayer b = (XgramTextLayer) other;
    return StringUtils.equalsOrBothEmpty(text, b.text) && font == b.font && style == b.style && align == b.align && color == b.color && wrapEms == b.wrapEms;
  }

  // Layout

  @NonNull
  private StaticLayout ensureLayout () {
    StaticLayout layout = this.layout;
    if (layout != null) {
      return layout;
    }
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTextSize(LAYOUT_SIZE);
    paint.setTypeface(typeface(font));
    final float maxWidth = Math.max(LAYOUT_SIZE, LAYOUT_SIZE * wrapEms);
    final float desiredWidth = Layout.getDesiredWidth(text, paint);
    // as wide as the longest line (one more pixel, so it isn't broken by rounding), at most the field's width
    final int width = Math.max(1, (int) Math.ceil(Math.min(maxWidth, desiredWidth + 1f)));
    layout = new StaticLayout(text, paint, width, alignment(align), 1f, 0f, false);

    float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
    for (int i = 0; i < layout.getLineCount(); i++) {
      final float lineLeft = layout.getLineLeft(i), lineRight = layout.getLineRight(i);
      if (lineRight > lineLeft) {
        left = Math.min(left, lineLeft);
        right = Math.max(right, lineRight);
      }
    }
    if (left > right) {
      left = right = 0f;
    }
    final float top = 0f, bottom = layout.getHeight();
    final float paddingX, paddingY;
    switch (style) {
      case STYLE_BUBBLE:
        paddingX = LAYOUT_SIZE * BUBBLE_PADDING_X;
        paddingY = LAYOUT_SIZE * BUBBLE_PADDING_Y;
        break;
      case STYLE_OUTLINE:
        paddingX = paddingY = LAYOUT_SIZE * OUTLINE_WIDTH / 2f;
        break;
      default:
        paddingX = paddingY = 0f;
        break;
    }
    originX = (left + right) / 2f;
    originY = (top + bottom) / 2f;
    inkBounds.set(left - paddingX - originX, top - paddingY - originY, right + paddingX - originX, bottom + paddingY - originY);
    this.layout = layout;
    return layout;
  }

  @Override
  protected void getContentBounds (@NonNull RectF out) {
    ensureLayout();
    out.set(inkBounds.left / LAYOUT_SIZE, inkBounds.top / LAYOUT_SIZE, inkBounds.right / LAYOUT_SIZE, inkBounds.bottom / LAYOUT_SIZE);
  }

  @Override
  protected void drawContent (@NonNull Canvas c, float unit, float alpha) {
    if (StringUtils.isEmpty(text)) {
      return;
    }
    StaticLayout layout = ensureLayout();
    final float scale = unit / LAYOUT_SIZE;
    c.scale(scale, scale);
    c.translate(-originX, -originY);
    drawText(c, layout, style, color, LAYOUT_SIZE, bubblePaint, lineRect, alpha, true);
  }

  /**
   * Draws {@code layout} (laid out at {@code fontSize}) in {@code style}: the bubbles behind its lines or its outline,
   * then, with {@code fill}, the text itself. The editor's text field draws its own text, so it passes no fill.
   */
  public static void drawText (@NonNull Canvas c, @NonNull Layout layout, int style, int color, float fontSize, @NonNull Paint bubblePaint, @NonNull RectF tmp, float alpha, boolean fill) {
    final TextPaint paint = layout.getPaint();
    switch (style) {
      case STYLE_BUBBLE: {
        // a rounded box behind each line, tall enough to meet the next one's
        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(ColorUtils.alphaColor(alpha, color));
        final float paddingX = fontSize * BUBBLE_PADDING_X, paddingY = fontSize * BUBBLE_PADDING_Y, radius = fontSize * BUBBLE_RADIUS;
        for (int i = 0; i < layout.getLineCount(); i++) {
          final float left = layout.getLineLeft(i), right = layout.getLineRight(i);
          if (right - left <= 0f) {
            continue;
          }
          tmp.set(left - paddingX, layout.getLineTop(i) - paddingY, right + paddingX, layout.getLineBottom(i) + paddingY);
          c.drawRoundRect(tmp, radius, radius, bubblePaint);
        }
        break;
      }
      case STYLE_OUTLINE: {
        final Paint.Style paintStyle = paint.getStyle();
        final Paint.Join join = paint.getStrokeJoin();
        final float strokeWidth = paint.getStrokeWidth();
        final int paintColor = paint.getColor();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(fontSize * OUTLINE_WIDTH);
        paint.setColor(ColorUtils.alphaColor(alpha, color));
        layout.draw(c);
        paint.setStyle(paintStyle);
        paint.setStrokeJoin(join);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(paintColor);
        break;
      }
    }
    if (fill) {
      paint.setColor(ColorUtils.alphaColor(alpha, fillColor(style, color)));
      layout.draw(c);
    }
  }

  /** The text's own colour: the colour itself when plain; white or black against the outline or the bubble. */
  public static int fillColor (int style, int color) {
    switch (style) {
      case STYLE_OUTLINE:
      case STYLE_BUBBLE:
        return isLight(color) ? 0xff000000 : 0xffffffff;
    }
    return color;
  }

  public static boolean isLight (int color) {
    final float luminance = (.299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color)) / 255f;
    return luminance > .72f;
  }

  /** How much room the outline or the bubble takes around the text, in font sizes (the editor's text field pads it). */
  public static float decorationPaddingX (int style) {
    switch (style) {
      case STYLE_BUBBLE:
        return BUBBLE_PADDING_X;
      case STYLE_OUTLINE:
        return OUTLINE_WIDTH / 2f;
    }
    return 0f;
  }

  public static float decorationPaddingY (int style) {
    switch (style) {
      case STYLE_BUBBLE:
        return BUBBLE_PADDING_Y;
      case STYLE_OUTLINE:
        return OUTLINE_WIDTH / 2f;
    }
    return 0f;
  }

  // Fonts, styles, alignment

  private static final Typeface[] typefaces = new Typeface[FONTS.length];

  /** The font's bold face: Google Sans Flex and its Rounded need Android 8 (Telegram X's bold before that). */
  @NonNull
  public static Typeface typeface (int font) {
    font = clampFont(font);
    synchronized (typefaces) {
      Typeface typeface = typefaces[font];
      if (typeface == null) {
        switch (font) {
          case FONT_ROUNDED:
            typeface = XgramFonts.typeface(XgramFonts.ROUNDED, 700, false, 0f);
            break;
          case FONT_SERIF:
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD);
            break;
          case FONT_MONO:
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            break;
          default:
            typeface = XgramFonts.typeface(XgramFonts.FLEX, 700, false, 0f);
            break;
        }
        if (typeface == null) {
          typeface = Fonts.getRobotoBold();
        }
        typefaces[font] = typeface;
      }
      return typeface;
    }
  }

  @NonNull
  public static Layout.Alignment alignment (int align) {
    switch (align) {
      case ALIGN_START:
        return Layout.Alignment.ALIGN_NORMAL;
      case ALIGN_END:
        return Layout.Alignment.ALIGN_OPPOSITE;
    }
    return Layout.Alignment.ALIGN_CENTER;
  }

  /** The editor text field's horizontal gravity for {@code align} (start is Layout.Alignment.ALIGN_NORMAL there too). */
  public static int gravity (int align) {
    switch (align) {
      case ALIGN_START:
        return Gravity.START;
      case ALIGN_END:
        return Gravity.END;
    }
    return Gravity.CENTER_HORIZONTAL;
  }

  public static int clampFont (int font) {
    return font >= FONT_FLEX && font <= FONT_MONO ? font : FONT_FLEX;
  }

  public static int clampStyle (int style) {
    return style >= STYLE_PLAIN && style <= STYLE_BUBBLE ? style : STYLE_PLAIN;
  }

  public static int clampAlign (int align) {
    return align >= ALIGN_START && align <= ALIGN_END ? align : ALIGN_CENTER;
  }

  // Blob

  @Override
  protected void saveContent (@NonNull Blob blob) {
    blob.writeString(text != null ? text : "");
    blob.writeByte((byte) font);
    blob.writeByte((byte) style);
    blob.writeByte((byte) align);
    blob.writeInt(color);
    blob.writeFloat(wrapEms);
  }

  @Override
  protected void restoreContent (@NonNull Blob blob) {
    text = blob.readString();
    font = clampFont(blob.readByte());
    style = clampStyle(blob.readByte());
    align = clampAlign(blob.readByte());
    color = blob.readInt() | 0xff000000;
    final float wrapEms = blob.readFloat();
    this.wrapEms = wrapEms > 0f ? wrapEms : DEFAULT_WRAP_EMS;
    layout = null;
  }
}
