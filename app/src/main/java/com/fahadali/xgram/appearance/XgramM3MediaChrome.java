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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;

/**
 * Material 3 Expressive chrome drawn over media: the media viewer's header and video controls, the in-viewer
 * buffering indicator. Media stays dark whatever the app theme, so the content is white and the colour roles come from
 * the dark scheme. Every helper does nothing with Expressive design off.
 */
public final class XgramM3MediaChrome {
  private XgramM3MediaChrome () { }

  /** Start and end inset of the controls row (M3 16dp). */
  public static final float EDGE_DP = 16f;
  /** Touch target of the play / pause button in the controls row. */
  public static final float PLAY_TARGET_WIDTH_DP = 48f;
  /** Inactive seek track: white at 24%. */
  public static final float TRACK_INACTIVE_ALPHA = .24f;
  /** Buffered part of the seek track: white at 40%. */
  public static final float TRACK_BUFFERED_ALPHA = .40f;
  /** Contained loading indicator: 48dp container, 38dp active indicator. */
  public static final float LOADING_CONTAINER_DP = 48f, LOADING_ACTIVE_DP = 38f;

  /** {@code role} in the dark scheme, for anything drawn on media. */
  @ColorInt
  public static int onMedia (int role) {
    return XgramExpressive.colorFor(role, true);
  }

  /** White at {@code alpha}. */
  @ColorInt
  public static int white (float alpha) {
    return XgramExpressive.withAlpha(0xffffffff, alpha);
  }

  // Header

  /**
   * Standard icon button over media: a 40dp white state layer whose full corners spring to 8dp on press, matching the
   * white icons. Nothing with Expressive design off.
   */
  public static void iconButton (@Nullable View v) {
    if (v == null || !XgramExpressive.isEnabled()) {
      return;
    }
    v.setBackground(XgramExpressiveC.iconButtonRipple(0xffffffff, XgramExpressiveC.ICON_BUTTON_BOX));
  }

  /** {@link #iconButton} on every child of a header menu. */
  public static void iconButtons (@Nullable ViewGroup menu) {
    if (menu == null || !XgramExpressive.isEnabled()) {
      return;
    }
    for (int i = 0; i < menu.getChildCount(); i++) {
      iconButton(menu.getChildAt(i));
    }
  }

  /**
   * A two-line header over media (DoubleHeaderView: title, then subtitle): titleMedium over bodySmall, centred in the
   * 56dp bar. Call once after the view is built, before its texts are set.
   */
  public static void styleHeader (@Nullable ViewGroup doubleHeader) {
    if (doubleHeader == null || !XgramExpressive.isEnabled() || doubleHeader.getChildCount() < 2) {
      return;
    }
    View title = doubleHeader.getChildAt(0);
    View subtitle = doubleHeader.getChildAt(1);
    if (title instanceof TextView) {
      XgramExpressive.applyText((TextView) title, XgramExpressive.TS_TITLE_M, false);
      Views.setTopMargin(title, Screen.dp(8f));
    }
    if (subtitle instanceof TextView) {
      XgramExpressive.applyText((TextView) subtitle, XgramExpressive.TS_BODY_S, false);
      Views.setTopMargin(subtitle, Screen.dp(31f));
    }
  }

  /**
   * Keeps the titleMedium typeface after the header set its title (it switches to Roboto Medium on every title).
   * Scripts that need fake bold keep Telegram X's choice.
   */
  public static void restoreTitleTypeface (@Nullable ViewGroup doubleHeader, @Nullable CharSequence title) {
    if (doubleHeader == null || !XgramExpressive.isEnabled() || doubleHeader.getChildCount() < 1 || title == null) {
      return;
    }
    View v = doubleHeader.getChildAt(0);
    if (v instanceof TextView && !Text.needFakeBold(title)) {
      TextView textView = (TextView) v;
      android.graphics.Typeface typeface = XgramExpressive.typeface(XgramExpressive.TS_TITLE_M, false);
      if (textView.getTypeface() != typeface) {
        textView.setTypeface(typeface);
      }
    }
  }

  /**
   * Top scrim behind a header over media: black at 60% fading to clear, the mirror of the video controls' bottom
   * scrim. The shader is rebuilt only when the height changes.
   */
  public static final class TopScrimDrawable extends Drawable {
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
        paint.setShader(new LinearGradient(0, 0, 0, height, new int[] {0x99000000, 0x59000000, 0x00000000}, new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP));
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
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  // Buffering

  /** The 48dp container of the contained loading indicator, at {@code alpha}, centred on (cx, cy). */
  public static void drawLoadingContainer (@NonNull Canvas c, float cx, float cy, float alpha, @NonNull Paint paint) {
    if (alpha <= 0f) {
      return;
    }
    paint.setColor(XgramExpressive.withAlpha(onMedia(XgramExpressive.PRIMARY_CONTAINER), alpha));
    c.drawCircle(cx, cy, Screen.dp(LOADING_CONTAINER_DP) / 2f, paint);
  }
}
