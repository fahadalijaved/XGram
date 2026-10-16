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
    pill.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER));
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

  /** Bottom scrim behind the action row: black, clear at the top, 50% at the middle, 60% at the bottom. */
  public static final class BottomScrimDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.DITHER_FLAG);
    private int shaderHeight = -1;
    private int alpha = 255;

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      int height = bounds.height();
      if (height <= 0 || bounds.width() <= 0 || alpha == 0) {
        return;
      }
      if (shaderHeight != height) {
        shaderHeight = height;
        paint.setShader(new LinearGradient(0, 0, 0, height, new int[] {0x00000000, 0x80000000, 0x99000000}, new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
      }
      paint.setAlpha(alpha);
      c.save();
      c.translate(bounds.left, bounds.top);
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
