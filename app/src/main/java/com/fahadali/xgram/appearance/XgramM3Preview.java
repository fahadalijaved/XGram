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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive for the long-press preview (ForceTouchView): an elevated card (28dp corners,
 * surfaceContainerHigh, the chip step with Pure black, level 3 shadow) over a 32% scrim, which springs open from the
 * long-pressed row. The header row sits inside the card (40dp avatar, titleMedium name, bodySmall status); the content
 * under it is clipped to the card and its top corners are rounded like the chat's message container. The actions are
 * a floating toolbar under the card: a full-corner pill of icon buttons; the one under the finger fills with
 * secondaryContainer and a plain tooltip names it. Only used while {@link XgramExpressive#isEnabled()} is true.
 * Main thread only; nothing is allocated per frame.
 */
public final class XgramM3Preview {
  private XgramM3Preview () { }

  // Card

  public static final float CARD_CORNER = XgramExpressive.CORNER_XL;
  /** M3 scrim opacity behind modal surfaces. */
  public static final float SCRIM_ALPHA = .32f;
  /** Scale the card opens from (and closes to); 1 with Reduce motion, which only fades. */
  private static final float REVEAL_SCALE = .8f;
  /** Inner margins of the header row: the avatar sits 16dp from the card's edge (ComplexHeaderView adds 4dp). */
  public static final float HEADER_START = 12f, HEADER_END = 16f;

  /** The card's color: surfaceContainerHigh, a floating surface (the chip step with Pure black, see XgramAmoled). */
  @ColorInt
  public static int cardColor () {
    return XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER_HIGH));
  }

  public static int cardRadius () {
    return Screen.dp(CARD_CORNER);
  }

  public static float cardElevation () {
    return Screen.dpf(XgramExpressive.ELEVATION_3);
  }

  @ColorInt
  public static int scrimColor () {
    return XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SCRIM), SCRIM_ALPHA);
  }

  public static float revealScale () {
    return XgramMotion.reduce() ? 1f : REVEAL_SCALE;
  }

  /** The scrim behind the card; the color is read on every draw, so theme switches need no listener. */
  public static final class ScrimDrawable extends Drawable {
    @Override
    public void draw (@NonNull Canvas c) {
      c.drawColor(scrimColor());
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  private static final Paint cornersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final Path cornersPath = new Path();
  private static final RectF cornersOval = new RectF();
  private static int cornersWidth = -1, cornersRadius = -1;

  /**
   * Rounds the top corners of the content under the header row (the chat's 28dp container corners): paints the two
   * corner notches of a box from {@code left} to {@code right} whose top is {@code top} in {@code color}, the card's.
   */
  public static void drawContentCorners (@NonNull Canvas c, float left, float top, float right, @ColorInt int color) {
    final int width = Math.round(right - left);
    final int r = cardRadius();
    if (width < r * 2 || r <= 0 || ((color >>> 24) & 0xff) == 0) {
      return;
    }
    if (cornersWidth != width || cornersRadius != r) {
      cornersWidth = width;
      cornersRadius = r;
      cornersPath.rewind();
      cornersPath.moveTo(0, 0);
      cornersPath.lineTo(r, 0);
      cornersOval.set(0, 0, r * 2, r * 2);
      cornersPath.arcTo(cornersOval, 270f, -90f, false);
      cornersPath.close();
      cornersPath.moveTo(width, 0);
      cornersPath.lineTo(width, r);
      cornersOval.set(width - r * 2, 0, width, r * 2);
      cornersPath.arcTo(cornersOval, 0f, -90f, false);
      cornersPath.close();
    }
    cornersPaint.setColor(color);
    c.save();
    c.translate(left, top);
    c.drawPath(cornersPath, cornersPaint);
    c.restore();
  }

  // Floating toolbar

  public static final float BAR_HEIGHT = 64f, BAR_PADDING = 8f, BAR_GAP = 12f, BUTTON_HEIGHT = 48f;
  // 48dp slots, like the sticker preview's toolbar and the chat list's long-press toolbar
  private static final float SLOT_MAX = 48f, SLOT_MIN = 40f;

  /** Space the toolbar takes under the card: the gap and the bar. */
  public static int barSpace () {
    return Screen.dp(BAR_GAP + BAR_HEIGHT);
  }

  public static int barGap () {
    return Screen.dp(BAR_GAP);
  }

  /** How far the button under the finger is filled (0..1, a spring that can pass 1 a little on the way in). */
  public interface Hover {
    float hoverFactor ();
  }

  /** The content color of an action icon: onSecondaryContainer under the finger, onSurfaceVariant otherwise. */
  @ColorInt
  public static int iconColor (boolean hovered) {
    return XgramExpressive.color(hovered ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
  }

  /** Layout params of a toolbar button; the width is set again when the bar is measured. */
  public static LinearLayout.LayoutParams buttonParams () {
    return new LinearLayout.LayoutParams(Screen.dp(SLOT_MAX), Screen.dp(BUTTON_HEIGHT));
  }

  /**
   * The M3E floating toolbar of the preview's actions: a full-corner pill in the card's color, 64dp tall with 8dp
   * padding and a level 3 shadow, holding 48dp buttons (narrower when many share the width). A
   * button whose tag is a {@link Hover} gets a secondaryContainer pill behind it while it is under the finger.
   */
  public static final class ActionBar extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public ActionBar (Context context) {
      super(context);
      setOrientation(LinearLayout.HORIZONTAL);
      setGravity(Gravity.CENTER);
      final int padding = Screen.dp(BAR_PADDING);
      setPadding(padding, padding, padding, padding);
      setWillNotDraw(false);
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
        }
      });
      setElevation(cardElevation());
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int count = getChildCount();
      if (count > 0) {
        final int available = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE :
          MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        final int slot = Math.max(Screen.dp(SLOT_MIN), Math.min(Screen.dp(SLOT_MAX), available / count));
        for (int i = 0; i < count; i++) {
          ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
          if (params != null && params.width != slot) {
            params.width = slot; // read by the measure below; no new layout pass needed
          }
        }
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float height = getHeight();
      rect.set(0, 0, getWidth(), height);
      paint.setColor(cardColor());
      c.drawRoundRect(rect, height / 2f, height / 2f, paint);
      final int hoverColor = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      final int inset = Screen.dp(2f);
      for (int i = 0; i < getChildCount(); i++) {
        final View child = getChildAt(i);
        final Object tag = child.getTag();
        if (!(tag instanceof Hover) || child.getVisibility() != View.VISIBLE) {
          continue;
        }
        final float f = ((Hover) tag).hoverFactor();
        if (f <= 0f) {
          continue;
        }
        final float scale = .7f + .3f * f;
        final float cx = child.getLeft() + child.getWidth() / 2f, cy = child.getTop() + child.getHeight() / 2f;
        final float halfWidth = Math.max(0f, child.getWidth() / 2f - inset) * scale;
        final float halfHeight = child.getHeight() / 2f * scale;
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
        paint.setColor(XgramExpressive.withAlpha(hoverColor, Math.min(1f, f)));
        c.drawRoundRect(rect, halfHeight, halfHeight, paint);
      }
    }
  }

  // Plain tooltip (the action's name above its button)

  private static TextPaint tooltipTextPaint;
  private static final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF tooltipRect = new RectF();

  /** bodySmall (12sp, 400). */
  public static TextPaint tooltipTextPaint () {
    if (tooltipTextPaint == null) {
      tooltipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      tooltipTextPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_S, false));
    }
    tooltipTextPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_S)));
    return tooltipTextPaint;
  }

  public static int measureTooltip (@NonNull String text) {
    return (int) Math.ceil(U.measureText(text, tooltipTextPaint()));
  }

  /**
   * An M3 plain tooltip (inverseSurface, 4dp corners, 24dp tall, bodySmall in inverseOnSurface) centred on
   * {@code centerX} with its bottom at {@code bottom}, kept 8dp inside {@code viewWidth}; it fades in and rises 4dp
   * into place as {@code factor} goes from 0 to 1.
   */
  public static void drawTooltip (@NonNull Canvas c, @NonNull String text, int textWidth, float centerX, float bottom, int viewWidth, float factor) {
    final float alpha = Math.max(0f, Math.min(1f, factor));
    if (alpha <= 0f) {
      return;
    }
    final int padding = Screen.dp(8f);
    final float width = textWidth + padding * 2;
    final float height = Screen.dp(24f);
    final float margin = Screen.dp(8f);
    float left = centerX - width / 2f;
    left = Math.max(margin, Math.min(viewWidth - margin - width, left));
    final float rectBottom = bottom + Screen.dp(4f) * (1f - factor);
    tooltipRect.set(left, rectBottom - height, left + width, rectBottom);
    final float radius = Screen.dp(XgramExpressive.CORNER_XS);
    tooltipPaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.INVERSE_SURFACE), alpha));
    c.drawRoundRect(tooltipRect, radius, radius, tooltipPaint);
    final TextPaint textPaint = tooltipTextPaint();
    textPaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.INVERSE_ON_SURFACE), alpha));
    final float baseline = tooltipRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f;
    c.drawText(text, left + padding, baseline, textPaint);
  }
}
