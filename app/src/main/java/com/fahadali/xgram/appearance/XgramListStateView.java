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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive list states, centred in the view: the loading indicator (contained: a 48dp primaryContainer
 * circle holding the 38dp indicator in onPrimaryContainer; plain: the indicator in primary) and the empty state (an
 * icon on a soft burst in primaryContainer, a titleLarge headline and a bodyMedium supporting line).
 */
public final class XgramListStateView extends View {
  private static final int STATE_LOADING = 0, STATE_EMPTY = 1, STATE_NONE = 2;

  private int state = STATE_LOADING;
  private boolean contained = true;
  private float indicatorDp = 38f;
  private long startTime = -1;

  private @Nullable Drawable icon;
  private @Nullable CharSequence title, text;
  private @Nullable StaticLayout titleLayout, textLayout;
  private int layoutWidth = -1;
  private @Nullable TextPaint titlePaint, textPaint;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Path shape = new Path();
  private final Matrix matrix = new Matrix();

  public XgramListStateView (Context context) {
    super(context);
    paint.setStyle(Paint.Style.FILL);
  }

  /** The loading indicator: contained (48dp circle) or plain, {@code sizeDp} being the active indicator (38dp). */
  public void setLoading (boolean contained, float sizeDp) {
    if (state != STATE_LOADING) {
      startTime = -1;
    }
    this.state = STATE_LOADING;
    this.contained = contained;
    this.indicatorDp = sizeDp;
    invalidate();
  }

  /** Draws nothing (a list whose own loading indicator is elsewhere, while it loads); the next empty state enters. */
  public void setNone () {
    if (state != STATE_NONE) {
      this.state = STATE_NONE;
      this.fadeLoading = false;
      this.emptyStart = -1;
      invalidate();
    }
  }

  /**
   * As a list row (SettingsAdapter's empty states): with {@code wrap} on, a height that is not exact is the empty
   * state's own height (burst, headline, supporting text and the padding) instead of all the space offered.
   */
  public void setWrapContent (boolean wrap) {
    if (this.wrapContent != wrap) {
      this.wrapContent = wrap;
      requestLayout();
    }
  }

  private boolean wrapContent;

  public boolean isEmptyState () {
    return state == STATE_EMPTY;
  }

  @Nullable
  public CharSequence getEmptyTitle () {
    return state == STATE_EMPTY ? title : null;
  }

  @Nullable
  public CharSequence getEmptyText () {
    return state == STATE_EMPTY ? text : null;
  }

  /** The empty state's height for {@code width}, padding included. */
  public int measureEmptyHeight (int width) {
    checkLayouts(width);
    int total = Screen.dp(112f);
    if (titleLayout != null) {
      total += Screen.dp(16f) + titleLayout.getHeight();
    }
    if (textLayout != null) {
      total += (titleLayout != null ? Screen.dp(8f) : Screen.dp(16f)) + textLayout.getHeight();
    }
    return total + getPaddingTop() + getPaddingBottom();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (!wrapContent || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      return;
    }
    final int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int height = state == STATE_EMPTY ? measureEmptyHeight(width) : Screen.dp(48f) + Screen.dp(32f) + getPaddingTop() + getPaddingBottom();
    if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
      height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
    }
    setMeasuredDimension(width, height);
  }

  /** {@code title} may be null: the icon and the supporting text only (a hint that is one long sentence). */
  public void setEmpty (@DrawableRes int iconRes, @Nullable CharSequence title, @Nullable CharSequence text) {
    if (state != STATE_EMPTY) {
      // Entrance: the burst scales in, then the headline and the supporting text fade and rise (50 ms apart);
      // a loading indicator that was on screen crossfades out
      fadeLoading = state == STATE_LOADING && startTime >= 0;
      emptyStart = -1;
    }
    final boolean sizeChanged = wrapContent && (!eq(this.title, title) || !eq(this.text, text));
    this.state = STATE_EMPTY;
    this.icon = iconRes != 0 ? Drawables.get(iconRes) : null;
    this.title = title;
    this.text = text;
    this.layoutWidth = -1;
    if (sizeChanged) {
      requestLayout();
    }
    invalidate();
  }

  private static boolean eq (@Nullable CharSequence a, @Nullable CharSequence b) {
    return a == null ? b == null : b != null && a.toString().equals(b.toString());
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    startTime = -1;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight() - getPaddingTop() - getPaddingBottom();
    if (width <= 0 || height <= 0) {
      return;
    }
    if (state == STATE_LOADING) {
      drawLoading(c, width / 2f, getPaddingTop() + height / 2f, 1f);
    } else if (state == STATE_EMPTY) {
      drawEmpty(c, width, height);
    }
  }

  private void drawLoading (Canvas c, float cx, float cy, float alpha) {
    final int indicatorColor;
    if (contained) {
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), alpha));
      c.drawCircle(cx, cy, Screen.dp(24f), paint);
      indicatorColor = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), alpha);
    } else {
      indicatorColor = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), alpha);
    }
    long now = AnimationUtils.currentAnimationTimeMillis();
    if (startTime < 0) {
      startTime = now;
    }
    XgramLoadingIndicator.draw(c, cx, cy, Screen.dp(indicatorDp), indicatorColor, now - startTime);
    // Not while faded out (a progress view some callers keep at alpha 0 once loaded): onSetAlpha restarts the frames
    if (getWindowToken() != null && getVisibility() == View.VISIBLE && getAlpha() > 0f) {
      postInvalidateOnAnimation();
    }
  }

  @Override
  protected boolean onSetAlpha (int alpha) {
    if (alpha > 0 && state == STATE_LOADING && getAlpha() > 0f) {
      invalidate(); // back from alpha 0: the loading frames stopped there
    }
    return super.onSetAlpha(alpha);
  }

  private void checkLayouts (int width) {
    if (layoutWidth == width && (titleLayout != null || textLayout != null || (title == null && text == null))) {
      return;
    }
    layoutWidth = width;
    if (titlePaint == null) {
      titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      titlePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_TITLE_L, false));
      titlePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_L)));
      textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
      textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
    }
    int maxWidth = Math.max(0, Math.min(width - Screen.dp(16f) * 2, Screen.dp(320f)));
    titleLayout = title != null && maxWidth > 0 ? new StaticLayout(title, titlePaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1f, Screen.dp(4f), false) : null;
    textLayout = text != null && text.length() > 0 && maxWidth > 0 ? new StaticLayout(text, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1f, Screen.dp(4f), false) : null;
  }

  private long emptyStart = -1;
  private boolean fadeLoading;

  /** Spring position of {@code token} {@code t} seconds after it started (0 before). */
  private static float springAt (int token, float t) {
    if (t <= 0f) {
      return 0f;
    }
    return XgramMotion.springValue(XgramMotion.stiffness(token), XgramMotion.damping(token), 0f, t);
  }

  private void drawEmpty (Canvas c, int width, int height) {
    checkLayouts(width);
    if (titleLayout == null && textLayout == null) {
      return;
    }
    long now = AnimationUtils.currentAnimationTimeMillis();
    if (emptyStart < 0) {
      emptyStart = now;
    }
    final float t = (now - emptyStart) / 1000f;
    final boolean entering = t < .6f && getWindowToken() != null;
    final boolean reduce = XgramMotion.reduce();
    if (fadeLoading && entering) {
      float out = 1f - XgramMotion.clamp01(springAt(XgramExpressive.EFFECTS_DEFAULT, t));
      if (out > 0f) {
        drawLoading(c, width / 2f, getPaddingTop() + height / 2f, out);
      }
    }
    final float burstAlpha = entering ? XgramMotion.clamp01(springAt(XgramExpressive.EFFECTS_DEFAULT, t)) : 1f;
    final float burstScale = entering && !reduce ? .8f + .2f * springAt(XgramExpressive.SPATIAL_DEFAULT, t) : 1f;
    final float titleAlpha = entering ? XgramMotion.clamp01(springAt(XgramExpressive.EFFECTS_DEFAULT, t - .05f)) : 1f;
    final float titleRise = entering && !reduce ? Screen.dp(8f) * (1f - springAt(XgramExpressive.SPATIAL_FAST, t - .05f)) : 0f;
    final float textAlpha = entering ? XgramMotion.clamp01(springAt(XgramExpressive.EFFECTS_DEFAULT, t - .1f)) : 1f;
    final float textRise = entering && !reduce ? Screen.dp(8f) * (1f - springAt(XgramExpressive.SPATIAL_FAST, t - .1f)) : 0f;
    if (entering) {
      postInvalidateOnAnimation();
    } else {
      fadeLoading = false;
    }
    final int shapeSize = Screen.dp(112f), gap = Screen.dp(16f), textGap = Screen.dp(8f);
    final int titleHeight = titleLayout != null ? titleLayout.getHeight() : 0;
    final int total = shapeSize + (titleLayout != null ? gap + titleHeight : 0) + (textLayout != null ? (titleLayout != null ? textGap : gap) + textLayout.getHeight() : 0);
    final float top = getPaddingTop() + Math.max(0, (height - total) / 2f);
    final float cx = width / 2f, cy = top + shapeSize / 2f;

    XgramShapes.toPath(XgramShapes.SOFT_BURST, shape);
    XgramShapes.fit(shape, cx, cy, shapeSize, 0f, matrix);
    final boolean scaled = burstScale != 1f;
    if (scaled) {
      c.save();
      c.scale(burstScale, burstScale, cx, cy);
    }
    paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), burstAlpha));
    c.drawPath(shape, paint);

    if (icon != null && icon.getMinimumWidth() > 0) {
      // The icon at 48dp
      float scale = Screen.dp(48f) / (float) icon.getMinimumWidth();
      c.save();
      c.scale(scale, scale, cx, cy);
      // The fade goes through the paint alpha over the opaque color's cached filter: a filter per alpha would be a new
      // PorterDuffColorFilter every entrance frame, churning the shared 20-entry cache in Paints
      final android.graphics.ColorFilter iconFilter = Paints.getColorFilter(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER));
      if (iconPaint.getColorFilter() != iconFilter) {
        iconPaint.setColorFilter(iconFilter);
      }
      iconPaint.setAlpha((int) (255f * XgramMotion.clamp01(burstAlpha)));
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, iconPaint);
      c.restore();
    }
    if (scaled) {
      c.restore();
    }

    c.save();
    if (titleLayout != null) {
      c.translate((width - titleLayout.getWidth()) / 2f, top + shapeSize + gap + titleRise);
      titlePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), titleAlpha));
      titleLayout.draw(c);
      if (textLayout != null) {
        c.translate(0, titleLayout.getHeight() + textGap - titleRise + textRise);
      }
    } else if (textLayout != null) {
      c.translate((width - textLayout.getWidth()) / 2f, top + shapeSize + gap + textRise);
    }
    if (textLayout != null) {
      textPaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), textAlpha));
      textLayout.draw(c);
    }
    c.restore();
  }
}
