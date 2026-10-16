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
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E wave 2: the media viewer's send screen (gallery mode) with Expressive design.
 * <ul>
 *   <li>The edit actions (crop, draw, adjust, timer) sit in a floating toolbar: a full-corner container in the dark
 *   scheme's surfaceContainer, inset 4dp inside the 56dp action row (the row keeps Telegram X's height, so the toolbar
 *   is 48dp tall with 40dp icon button state layers, 4dp around them), next to the send FAB.</li>
 *   <li>The caption field is a filled field in the dark scheme's surfaceContainerHigh, 24dp corners (full for one
 *   line), 8dp from the screen edges, its text 16dp inside it.</li>
 *   <li>The action row has no band: a bottom scrim (black, clear at the top, 50% at the middle, 60% at the bottom,
 *   the mirror of the header's top scrim) keeps the white back icon readable over light media.</li>
 * </ul>
 * Media stays dark whatever the app theme, so the colours come from the dark scheme
 * ({@link XgramM3MediaChrome#onMedia}). Nothing is allocated while drawing.
 */
public final class XgramW2MediaViewer {
  private XgramW2MediaViewer () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  /** Vertical inset of the floating toolbar inside the 56dp action row. */
  public static final float TOOLBAR_INSET_DP = 4f;
  /** Caption field: side inset, vertical inset, corner. */
  public static final float FIELD_INSET_H_DP = 8f, FIELD_INSET_V_DP = 2f, FIELD_CORNER_DP = 24f;
  /**
   * Where the caption text starts while the caption is not being edited, from the caption view's start: 16dp inside
   * the field. The field's insets pad the caption row (an InsetDrawable background pads its view by its insets), so
   * the caption view starts at the field's edge.
   */
  public static final float FIELD_TEXT_START_DP = 16f;

  /** The floating toolbar container, set as the background of the edit buttons row. */
  @NonNull
  public static Drawable newToolbarBackground () {
    GradientDrawable pill = new GradientDrawable();
    pill.setShape(GradientDrawable.RECTANGLE);
    // With pure black saved, onMedia's dark scheme makes surfaceContainer the black page level, which vanishes on the
    // black scrim over letterboxed media: the pill takes the chip step, distinct from the scrim and the caption field
    pill.setColor(XgramM3MediaChrome.onMedia(XgramAmoled.isEnabled() ? XgramExpressive.SURFACE_CONTAINER_HIGHEST : XgramExpressive.SURFACE_CONTAINER));
    // GradientDrawable clamps the radius to half the shorter side: full corners
    pill.setCornerRadius(Screen.dp(100f));
    final int inset = Screen.dp(TOOLBAR_INSET_DP);
    return new InsetDrawable(pill, 0, inset, 0, inset);
  }

  /** The caption field container, set as the background of the caption row. */
  @NonNull
  public static Drawable newCaptionFieldBackground () {
    GradientDrawable field = new GradientDrawable();
    field.setShape(GradientDrawable.RECTANGLE);
    field.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH));
    field.setCornerRadius(Screen.dp(FIELD_CORNER_DP));
    final int h = Screen.dp(FIELD_INSET_H_DP), v = Screen.dp(FIELD_INSET_V_DP);
    return new InsetDrawable(field, h, v, h, v);
  }

  /** The scrims' end: black at 60% (0x99), where the bars below them (navigation bar area, action row) continue. */
  public static final int SCRIM_END = 0x99000000;
  private static final float SCRIM_END_ALPHA = (float) (SCRIM_END >>> 24) / 255f;

  /**
   * Bottom scrim behind the action row: black, clear at the top, 50% at the middle, 60% at the bottom. When the bottom
   * controls' scrim ({@link ControlsScrimDrawable}) ends on its top at 60% x {@code topFactor}, the row's gradient starts
   * there instead of clear ({@link #setTopFactor}), so the two read as one gradient with no step: a flat 60% x f layer,
   * then the gradient scaled so the bottom stays at 60%. Two paints, no shader rebuild per frame.
   */
  public static final class BottomScrimDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.DITHER_FLAG);
    private final Paint fillPaint = new Paint();
    private int shaderHeight = -1;
    private int alpha = 255;
    private float topFactor;

    public BottomScrimDrawable () {
      fillPaint.setColor(0xff000000);
    }

    /** 0: the row's own gradient (clear at its top); 1: 60% across the row, continuing the scrim above it. */
    public void setTopFactor (float factor) {
      factor = Math.max(0f, Math.min(1f, factor));
      if (this.topFactor != factor) {
        this.topFactor = factor;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      int height = bounds.height();
      if (height <= 0 || bounds.width() <= 0 || alpha == 0) {
        return;
      }
      if (shaderHeight != height) {
        shaderHeight = height;
        paint.setShader(new LinearGradient(0, 0, 0, height, new int[] {0x00000000, 0x80000000, SCRIM_END}, new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
      }
      c.save();
      c.translate(bounds.left, bounds.top);
      float gradientScale = 1f;
      if (topFactor > 0f) {
        // 1 - (1 - flat) * (1 - scale * 60%) = 60% at the bottom
        final float flat = SCRIM_END_ALPHA * topFactor;
        gradientScale = topFactor >= 1f ? 0f : Math.max(0f, (1f - (1f - SCRIM_END_ALPHA) / (1f - flat)) / SCRIM_END_ALPHA);
        fillPaint.setAlpha(Math.round(alpha * flat));
        c.drawRect(0, 0, bounds.width(), height, fillPaint);
      }
      if (gradientScale > 0f) {
        paint.setAlpha(Math.round(alpha * gradientScale));
        c.drawRect(0, 0, bounds.width(), height, paint);
      }
      c.restore();
    }

    @Override
    public void setAlpha (int alpha) {
      if (this.alpha != alpha) {
        this.alpha = alpha;
        invalidateSelf();
      }
    }

    @Override
    public int getAlpha () {
      return alpha;
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
      fillPaint.setColorFilter(colorFilter);
      invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * The scrim behind the viewer's bottom controls, set as the background of the bottom wrap: it draws behind the
   * caption card, the caption field and the controls, never over them, anchored to the wrap's bottom, black clear at
   * its top and 60% at its bottom, where the bar below continues it (the navigation bar area, the album strip, or the
   * send screen's action row via {@link BottomScrimDrawable#setTopFactor}).
   * <ul>
   *   <li>{@link #setControls}: the video controls' 96dp scrim (clear, 35% at 55%, 60%), at the controls' alpha, so it
   *   is gone for photos and GIFs.</li>
   *   <li>{@link #setSection}: an edit section of the send screen (crop, filters, quality, paint), in place of Telegram
   *   X's flat 44% band: the band's height, clear at its top, 50% 24dp down, 60% at the bottom. The fade stays inside
   *   the band, which sits below the media (the media is laid out above the section), so the photo being edited is
   *   never darkened.</li>
   * </ul>
   * The shader is rebuilt only when the shape changes (a section switch), never per frame. Its outline has alpha 0
   * (Drawable's default), so the wrap's elevation casts no shadow.
   */
  public static final class ControlsScrimDrawable extends Drawable {
    public static final float CONTROLS_HEIGHT_DP = 96f, SECTION_FADE_DP = 24f;
    private static final int CONTROLS = -1;

    private final Paint paint = new Paint(Paint.DITHER_FLAG);
    private int band = CONTROLS;
    private float factor;
    private int shaderHeight = -1, shaderBand = Integer.MIN_VALUE;
    private int alpha = 255;

    /** The video controls' scrim at {@code factor} (their alpha times their inner alpha). */
    public void setControls (float factor) {
      set(CONTROLS, factor);
    }

    /** An edit section's scrim: {@code bandHeightPx} tall, its fade inside its top, at {@code factor}. */
    public void setSection (int bandHeightPx, float factor) {
      set(Math.max(0, bandHeightPx), factor);
    }

    private void set (int band, float factor) {
      factor = Math.max(0f, Math.min(1f, factor));
      if (this.band != band || this.factor != factor) {
        this.band = band;
        this.factor = factor;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      if (factor <= 0f || alpha == 0 || bounds.width() <= 0 || bounds.height() <= 0) {
        return;
      }
      final int height = band == CONTROLS ? Screen.dp(CONTROLS_HEIGHT_DP) : band;
      if (height <= 0) {
        return;
      }
      if (shaderHeight != height || shaderBand != band) {
        shaderHeight = height;
        shaderBand = band;
        if (band == CONTROLS) {
          paint.setShader(new LinearGradient(0, 0, 0, height, new int[] {0x00000000, 0x59000000, SCRIM_END}, new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
        } else {
          final float fade = Math.min(.5f, (float) Screen.dp(SECTION_FADE_DP) / (float) height);
          paint.setShader(new LinearGradient(0, 0, 0, height, new int[] {0x00000000, 0x80000000, SCRIM_END}, new float[] {0f, fade, 1f}, Shader.TileMode.CLAMP));
        }
      }
      paint.setAlpha(Math.round(alpha * factor));
      c.save();
      c.translate(bounds.left, bounds.bottom - height);
      c.drawRect(0, 0, bounds.width(), height, paint);
      c.restore();
    }

    @Override
    public void setAlpha (int alpha) {
      if (this.alpha != alpha) {
        this.alpha = alpha;
        invalidateSelf();
      }
    }

    @Override
    public int getAlpha () {
      return alpha;
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
      invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
