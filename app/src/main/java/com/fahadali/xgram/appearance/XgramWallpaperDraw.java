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

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.core.ColorUtils;

/**
 * XGram: how a chat background draws, shared by the wallpaper picker's tiles and the chat itself so the two always
 * agree. Telegram's dark-theme patterns are inverted: black, with the fill showing only through the pattern's lines
 * at the pattern's intensity (official Telegram's rendering). Telegram's bare pattern library entries have no colors
 * at all and draw as plain black. While a tile's image loads it shows the M3E placeholder, never black.
 */
public final class XgramWallpaperDraw {
  private XgramWallpaperDraw () { }

  /**
   * {@code b} draws as a plain black square: a colorless pattern (Telegram's pattern library) at no intensity, or
   * inverted, whose lines then take the black fill too.
   */
  public static boolean drawsPlainBlack (@Nullable TGBackground b) {
    return b != null && b.isColorlessPattern() && (b.getPatternIntensity() == 0f || b.isPatternInverted());
  }

  /**
   * {@code b} is an inverted pattern drawn the official way (black, the fill through the lines). TDLib marks inversion
   * "for dark themes only", so in a light theme it draws as a normal pattern. Expressive design only: with it off
   * Telegram X's own rendering stays.
   */
  public static boolean drawsInverted (@Nullable TGBackground b, boolean dark) {
    return dark && b != null && b.isPatternInverted() && !b.isColorlessPattern() && XgramExpressive.isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }

  /**
   * The fill of a fill or pattern background, as the chat draws it: a solid color, a linear gradient, or a freeform
   * gradient over its base color (the radial gradients fade to transparent, so without the base the corners would
   * show whatever is behind).
   */
  public static void drawFill (Canvas c, DrawAlgorithms.GradientCache cache, int left, int top, int right, int bottom, TGBackground w, int defaultColor, float alpha) {
    if (w.isFillGradient() || w.isPatternBackgroundGradient()) {
      DrawAlgorithms.drawGradient(c, cache, left, top, right, bottom, w.getTopColor(), w.getBottomColor(), w.getRotationAngle(), alpha);
    } else if (w.isFillFreeformGradient() || w.isPatternBackgroundFreeformGradient()) {
      c.drawColor(ColorUtils.alphaColor(alpha, w.getBackgroundColor(defaultColor)));
      DrawAlgorithms.drawMulticolorGradient(c, cache, left, top, right, bottom, w.getFreeformColors(), alpha);
    } else {
      c.drawColor(ColorUtils.alphaColor(alpha, w.getBackgroundColor(defaultColor)));
    }
  }

  private static Paint srcInPaint;

  /**
   * An inverted pattern ({@link #drawsInverted}): black, then the pattern as a white mask at its intensity, and the
   * fill kept only where the mask is (SRC_IN), so the lines show the fill at exactly the intensity, in the tile and
   * in the chat alike. {@code pattern} must be loaded; its color filter is left white at the intensity, so the
   * normal pattern path sets its own color (and full filter alpha) again before each draw.
   */
  public static void drawInverted (Canvas c, int left, int top, int right, int bottom, float alpha, Receiver pattern, DrawAlgorithms.GradientCache cache, TGBackground w) {
    c.drawRect(left, top, right, bottom, Paints.fillingPaint(ColorUtils.alphaColor(alpha, Color.BLACK)));
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return; // drawsInverted is false there; the layer bounds call needs 21
    }
    final float intensity = alpha * w.getPatternIntensity();
    if (intensity <= 0f) {
      return;
    }
    if (srcInPaint == null) {
      srcInPaint = new Paint();
      srcInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }
    final int saveCount = c.saveLayer(left, top, right, bottom, null);
    // The intensity goes in once, as the mask color's alpha. Through the paint alpha too, the repeated pattern the
    // chat draws would get it twice (paint alpha, then the filter's alpha: intensity squared, 25% for a 50% pattern)
    // while the tile's cropped thumbnail got it once, so the chat would be much darker than its tile.
    pattern.setPorterDuffColorFilter(Color.WHITE, intensity, false);
    pattern.draw(c);
    c.saveLayer(left, top, right, bottom, srcInPaint);
    drawFill(c, cache, left, top, right, bottom, w, Color.BLACK, 1f);
    c.restoreToCount(saveCount);
  }

  // The loading placeholder

  private static final long SHIMMER_MS = 1500L;
  /** The band's slant: about 20 degrees off vertical (tan 20). */
  private static final float SHIMMER_SLANT = .364f;

  private static Paint shimmerPaint;
  private static int shimmerColor, shimmerBand;
  private static final Matrix shimmerMatrix = new Matrix();

  private static boolean shimmerAnimates () {
    if (XgramMotion.reduce()) {
      return false;
    }
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
  }

  /**
   * The M3E loading placeholder: surfaceContainerHigh with a soft surfaceContainerHighest band sweeping across it
   * from the start side, still with Reduce motion or animations off. Colors are read on every draw. While it sweeps
   * it asks {@code host} for the next frame, so it stops by itself once the caller draws the loaded image instead
   * (or the view is detached and no longer draws).
   */
  public static void drawLoading (Canvas c, @Nullable View host, int left, int top, int right, int bottom) {
    c.drawRect(left, top, right, bottom, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH)));
    final int width = right - left, height = bottom - top;
    if (host == null || width <= 0 || height <= 0 || !shimmerAnimates()) {
      return;
    }
    final int highlight = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    final int band = Math.max(1, Math.min(width, height));
    if (shimmerPaint == null) {
      shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      shimmerPaint.setStyle(Paint.Style.FILL);
    }
    if (shimmerPaint.getShader() == null || shimmerColor != highlight || shimmerBand != band) {
      shimmerColor = highlight;
      shimmerBand = band;
      final int clear = highlight & 0x00FFFFFF;
      shimmerPaint.setShader(new LinearGradient(0, 0, band, 0, new int[] {clear, highlight, clear}, null, Shader.TileMode.CLAMP));
    }
    float phase = (float) (SystemClock.uptimeMillis() % SHIMMER_MS) / (float) SHIMMER_MS;
    if (Lang.rtl()) {
      phase = 1f - phase;
    }
    final float slant = height * SHIMMER_SLANT;
    final float from = left - band - slant;
    final float x = from + (right - from) * phase;
    shimmerMatrix.setSkew(SHIMMER_SLANT, 0f, 0f, top);
    shimmerMatrix.postTranslate(x, 0f);
    shimmerPaint.getShader().setLocalMatrix(shimmerMatrix);
    c.drawRect(left, top, right, bottom, shimmerPaint);
    host.postInvalidateOnAnimation();
  }
}
