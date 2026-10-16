/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class SliderView extends View implements FactorAnimator.Target {
  public static final int ANCHOR_MODE_START = 0;
  public static final int ANCHOR_MODE_CENTER = 1;

  private @ColorId int colorId = ColorId.sliderActive;
  private int anchorMode;
  private boolean slideEnabled;
  private int valueCount;

  public SliderView (Context context) {
    super(context);
  }

  public void setAnchorMode (int anchorMode) {
    this.anchorMode = anchorMode;
  }

  public void setValueCount (int count) {
    if (this.valueCount != count) {
      this.valueCount = count;
      invalidate();
    }
  }

  private float value; // -1f..0f -- left to center, 0..1f -- center to right
  private float secondaryValue, secondaryValueOffset; // -1f..0f -- left to center, 0..1f -- center to right

  public void setValue (float value) {
    if (this.value != value) {
      this.value = value;
      invalidate();
    }
  }

  public void setSecondaryValue (float offset, float value) {
    if (this.secondaryValueOffset != offset || this.secondaryValue != value) {
      this.secondaryValueOffset = offset;
      this.secondaryValue = value;
      invalidate();
    }
  }

  public float getValue () {
    return value;
  }

  private void changeValue (float value) {
    if (this.value != value) {
      this.value = value;
      invalidate();
      if (listener != null) {
        listener.onValueChanged(this, value);
      }
    }
  }

  private static final int ANIMATOR_CHANGE = 3;
  private FactorAnimator changeAnimator;

  public void animateValue (float toValue) {
    if (changeAnimator != null) {
      changeAnimator.cancel();
    }
    if (this.value != toValue) {
      if (changeAnimator == null) {
        changeAnimator = new FactorAnimator(ANIMATOR_CHANGE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180, value);
      } else {
        changeAnimator.forceFactor(value);
      }
      changeAnimator.animateTo(toValue);
    }
  }

  private @ColorId int forceBackgroundColorId;
  private @ColorId int forceSecondaryColorId;

  public void setForceBackgroundColorId (@ColorId int colorId) {
    this.forceBackgroundColorId = colorId;
  }

  public void setForceSecondaryColorId (@ColorId int colorId) {
    this.forceSecondaryColorId = colorId;
  }

  private FactorAnimator colorAnimator;
  private static final int ANIMATOR_COLOR = 0;
  private static final int ANIMATOR_TOUCH = 1;

  public void setColorId (@ColorId int colorId, boolean animated) {
    if (animated) {
      if (colorAnimator == null) {
        if (this.colorId == colorId) {
          return;
        }
        colorAnimator = new FactorAnimator(ANIMATOR_COLOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 0f);
      } else {
        colorAnimator.cancel();
      }
      if (this.colorId != colorId) {
        fromColorId = this.colorId;
        toColorId = colorId;
        colorAnimator.forceFactor(0f);
        colorAnimator.animateTo(1f);
      }
    } else {
      if (colorAnimator != null) {
        colorAnimator.forceFactor(0f);
      }
      setColorId(colorId);
    }
  }

  private @ColorId int fromColorId, toColorId;

  private void setColorId (int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidate();
    }
  }

  private boolean isUp;
  private FactorAnimator upAnimator;

  private void setIsUp (boolean isUp) {
    if (this.isUp != isUp) {
      this.isUp = isUp;
      getParent().requestDisallowInterceptTouchEvent(isUp);
      if (upAnimator == null) {
        upAnimator = new FactorAnimator(ANIMATOR_TOUCH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      upAnimator.animateTo(isUp ? 1f : 0f);
      if (XgramExpressive.isEnabled()) {
        if (xgramHandleWidth == null) {
          xgramHandleWidth = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, XGRAM_HANDLE_WIDTH, (s, v) -> invalidate());
        }
        xgramHandleWidth.animateTo(isUp ? XGRAM_HANDLE_WIDTH_PRESSED : XGRAM_HANDLE_WIDTH);
      }
      if (listener != null) {
        listener.onSetStateChanged(this, isUp);
      }
    }
  }

  private float upFactor;

  private void setUpFactor (float factor) {
    if (this.upFactor != factor) {
      this.upFactor = factor;
      invalidate();
    }
  }

  private static final int ANIMATOR_ENABLE = 2;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_COLOR: {
        invalidate();
        break;
      }
      case ANIMATOR_TOUCH: {
        setUpFactor(factor);
        break;
      }
      case ANIMATOR_ENABLE: {
        setEnableFactor(factor);
        break;
      }
      case ANIMATOR_CHANGE: {
        setValue(factor);
        break;
      }
      case ANIMATOR_SMALL: {
        invalidate(getTotalPaddingLeft(), 0, getMeasuredWidth() - getTotalPaddingRight(), getMeasuredHeight());
        break;
      }
    }
  }

  private float enableFactor;

  private void setEnableFactor (float factor) {
    if (this.enableFactor != factor) {
      this.enableFactor = factor;
      invalidate();
    }
  }

  public void setSlideEnabled (boolean isEnabled, boolean animated) {
    if (this.slideEnabled != isEnabled) {
      this.slideEnabled = isEnabled;
      if (animated) {
        animateEnableFactor(isEnabled ? 1f : 0f);
      } else {
        forceEnableFactor(isEnabled ? 1f : 0f);
      }
    }
  }

  private FactorAnimator enableAnimator;

  private void animateEnableFactor (float toFactor) {
    if (enableAnimator == null) {
      if (enableFactor == toFactor) {
        return;
      }
      enableAnimator = new FactorAnimator(ANIMATOR_ENABLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, enableFactor);
    }
    enableAnimator.animateTo(toFactor);
  }

  private void forceEnableFactor (float factor) {
    if (enableAnimator != null) {
      enableAnimator.forceFactor(factor);
    }
    setEnableFactor(factor);
  }

  private float smallValue = -1f;
  private FactorAnimator smallValueAnimator;
  private static final int ANIMATOR_SMALL = 4;

  private float getSmallValue () {
    return smallValueAnimator != null ? smallValueAnimator.getFactor() : smallValue;
  }

  private long lastUpdateTimeMs;

  public void setSmallValue (float value, boolean allowAnimation) {
    long now = SystemClock.uptimeMillis();
    final int availWidth = getMeasuredWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
    final float currentFactor = getSmallValue();
    if (smallValue == -1f || !allowAnimation || (currentFactor >= 0f && Math.abs((int) ((float) availWidth * value) - (int) ((float) availWidth * currentFactor)) < 2)) {
      this.lastUpdateTimeMs = now;
      this.smallValue = value;
      if (smallValueAnimator != null) {
        smallValueAnimator.forceFactor(value);
      }
      invalidate();
      return;
    }

    long duration = 180l + (long) (Math.min(800f, Math.max(180f, lastUpdateTimeMs == 0 ? 0 : (float) (now - lastUpdateTimeMs) * 1.5f) * Math.abs(currentFactor - value)));
    if (smallValueAnimator == null) {
      smallValueAnimator = new FactorAnimator(ANIMATOR_SMALL, this, AnimatorUtils.LINEAR_INTERPOLATOR, duration, this.smallValue);
    } else {
      smallValueAnimator.setDuration(duration);
    }
    smallValueAnimator.animateTo(value);
    this.lastUpdateTimeMs = now;
  }

  public interface Listener {
    void onSetStateChanged (SliderView view, boolean isSetting);
    void onValueChanged (SliderView view, float factor);
    boolean allowSliderChanges (SliderView view);
  }

  private Listener listener;

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  private float diffX;

  private int addPaddingLeft, addPaddingRight;

  public void setAddPaddingLeft (int paddingLeft) {
    if (this.addPaddingLeft != paddingLeft) {
      this.addPaddingLeft = paddingLeft;
      invalidate();
    }
  }

  public void setAddPaddingRight (int paddingRight) {
    if (this.addPaddingRight != paddingRight) {
      this.addPaddingRight = paddingRight;
      invalidate();
    }
  }

  private int getTotalPaddingLeft () {
    return getPaddingLeft() + addPaddingLeft;
  }

  private int getTotalPaddingRight () {
    return getPaddingRight() + addPaddingRight;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        int cx = findCenterX();
        int cy = findCenterY();

        int radius = Screen.dp(24f);

        if (x >= cx - radius && x <= cx + radius && y >= cy - radius && y <= cy + radius && slideEnabled && (changeAnimator == null || !changeAnimator.isAnimating()) && (listener == null || listener.allowSliderChanges(this))) {
          diffX = x - cx;
          setIsUp(true);
          return true;
        }

        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isUp) {
          x -= diffX;

          float factor;

          int left = getTotalPaddingLeft();
          int width = (getMeasuredWidth() - getTotalPaddingRight() - left);

          if (anchorMode == ANCHOR_MODE_CENTER) {
            int centerX = left + width / 2;
            float diff = x - centerX;
            factor = Math.max(-1f, Math.min(1f, diff / (float) (width / 2)));
          } else {
            float diff = x - left;
            factor = Math.max(0f, Math.min(1f, diff / (float) width));
          }

          changeValue(factor);
          return true;
        }

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isUp) {
          setIsUp(false);
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
        if (isUp) {
          setIsUp(false);
          return true;
        }
        break;
      }
    }
    return isUp;
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_COLOR: {
        if (finalFactor == 1f) {
          colorId = toColorId;
        }
        break;
      }
    }
  }

  private int findCenterY () {
    return getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingTop()) / 2;
  }

  private int findCenterX () {
    int left = getTotalPaddingLeft();
    int right = getMeasuredWidth() - getTotalPaddingRight();
    int width = right - left;
    if (anchorMode == ANCHOR_MODE_CENTER) {
      int centerX = left + width / 2;
      return centerX + (int) ((float) (width / 2) * value);
    } else {
      return left + (int) ((float) width * value);
    }
  }

  // XGram: Material 3 Expressive slider (XS): 16dp track with full outer and 2dp inside corners,
  // a 4x44dp handle with a 6dp gap on each side, stop indicators and ticks.

  private static final float XGRAM_HANDLE_WIDTH = 4f, XGRAM_HANDLE_WIDTH_PRESSED = 2f;
  private XgramExpressive.Spring xgramHandleWidth;
  private Path xgramPath;
  private float[] xgramRadii;

  // Fixed colours instead of the color ids, for sliders on media (the video seek bar); unused until set
  private boolean xgramHasColors;
  private int xgramActiveColor, xgramInactiveColor, xgramSecondaryColor;

  /** Expressive design only: the active track and handle, the inactive track and the buffered part in fixed colours. */
  public void setXgramColors (int activeColor, int inactiveColor) {
    setXgramColors(activeColor, inactiveColor, inactiveColor);
  }

  /** {@link #setXgramColors(int, int)} with its own colour for the buffered part. */
  public void setXgramColors (int activeColor, int inactiveColor, int secondaryColor) {
    if (!xgramHasColors || xgramActiveColor != activeColor || xgramInactiveColor != inactiveColor || xgramSecondaryColor != secondaryColor) {
      this.xgramHasColors = true;
      this.xgramActiveColor = activeColor;
      this.xgramInactiveColor = inactiveColor;
      this.xgramSecondaryColor = secondaryColor;
      invalidate();
    }
  }

  private void xgramAddSegment (Path path, float left, float top, float right, float bottom, float leftRadius, float rightRadius) {
    if (right - left <= 0f) {
      return;
    }
    float max = Math.min((right - left) / 2f, (bottom - top) / 2f);
    float lr = Math.min(leftRadius, max);
    float rr = Math.min(rightRadius, max);
    float[] radii = xgramRadii;
    radii[0] = radii[1] = lr;
    radii[2] = radii[3] = rr;
    radii[4] = radii[5] = rr;
    radii[6] = radii[7] = lr;
    RectF rectF = Paints.getRectF();
    rectF.set(left, top, right, bottom);
    path.addRoundRect(rectF, radii, Path.Direction.CW);
  }

  private void xgramFillSegment (Canvas c, float left, float top, float right, float bottom, float leftRadius, float rightRadius, int color) {
    if (right - left <= 0f) {
      return;
    }
    Path path = xgramPath;
    path.reset();
    xgramAddSegment(path, left, top, right, bottom, leftRadius, rightRadius);
    c.drawPath(path, Paints.fillingPaint(color));
  }

  private void xgramDraw (Canvas c) {
    if (xgramPath == null) {
      xgramPath = new Path();
      xgramRadii = new float[8];
    }
    final int left = getTotalPaddingLeft();
    final int right = getMeasuredWidth() - getTotalPaddingRight();
    final int width = right - left;
    final int cy = findCenterY();
    final float viewHeight = getMeasuredHeight();

    final float trackHeight = Math.min(Screen.dp(16f), viewHeight * .36f);
    final float top = cy - trackHeight / 2f;
    final float bottom = cy + trackHeight / 2f;
    final float outer = trackHeight / 2f;
    final float inside = Screen.dp(2f);
    final float handleHeight = Math.min(Screen.dp(44f), viewHeight);
    final float handleWidth = Screen.dp(xgramHandleWidth != null ? xgramHandleWidth.getValue() : XGRAM_HANDLE_WIDTH);
    final float gap = Screen.dp(6f);
    final float dotRadius = Screen.dp(2f);

    final float overlayAlpha = (1f - enableFactor) * .65f;
    final int origColor;
    if (xgramHasColors) {
      origColor = xgramActiveColor;
    } else {
      origColor = colorAnimator == null || !colorAnimator.isAnimating() ? Theme.getColor(colorId) : ColorUtils.fromToArgb(Theme.getColor(fromColorId), Theme.getColor(toColorId), colorAnimator.getFactor());
    }
    final int color = overlayAlpha > 0f ? ColorUtils.compositeColor(origColor, ColorUtils.color((int) (255f * overlayAlpha), 0)) : origColor;
    // sliderInactive is the generic inactive track of the in-app sliders: it becomes secondaryContainer.
    // Other forced colors (media viewer) stay as they are, unless fixed colours were set (setXgramColors).
    final int inactiveColor;
    final int secondaryColor;
    if (xgramHasColors) {
      inactiveColor = xgramInactiveColor;
      secondaryColor = xgramSecondaryColor;
    } else {
      inactiveColor = forceBackgroundColorId != 0 && forceBackgroundColorId != ColorId.sliderInactive ? Theme.getColor(forceBackgroundColorId) : XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      secondaryColor = forceSecondaryColorId != 0 ? Theme.getColor(forceSecondaryColorId) : XgramExpressive.withAlpha(color, XgramExpressive.DISABLED_CONTENT);
    }

    final float cx = findCenterX();
    final float activeEnd = cx - handleWidth / 2f - gap;
    final float inactiveStart = cx + handleWidth / 2f + gap;
    final int centerX = left + width / 2;

    if (anchorMode == ANCHOR_MODE_CENTER) {
      Path path = xgramPath;
      path.reset();
      xgramAddSegment(path, left, top, activeEnd, bottom, outer, inside);
      xgramAddSegment(path, inactiveStart, top, right, bottom, inside, outer);
      c.drawPath(path, Paints.fillingPaint(inactiveColor));
      if (cx > centerX) {
        xgramFillSegment(c, centerX, top, activeEnd, bottom, inside, inside, color);
      } else if (cx < centerX) {
        xgramFillSegment(c, inactiveStart, top, centerX, bottom, inside, inside, color);
      }
    } else {
      xgramFillSegment(c, left, top, activeEnd, bottom, outer, inside, color);
      xgramFillSegment(c, inactiveStart, top, right, bottom, inside, outer, inactiveColor);
      if (secondaryValue > 0f) {
        float secondaryLeft = Math.max(inactiveStart, left + (float) width * secondaryValueOffset);
        float secondaryRight = Math.min(right, left + (float) width * (secondaryValueOffset + secondaryValue));
        xgramFillSegment(c, secondaryLeft, top, secondaryRight, bottom, inside, secondaryRight >= right - .5f ? outer : inside, secondaryColor);
      }
    }

    final float excludeLeft = activeEnd - dotRadius;
    final float excludeRight = inactiveStart + dotRadius;
    if (valueCount > 1) {
      final float minX = left + outer, maxX = right - outer;
      for (int i = 0; i < valueCount; i++) {
        float x = Math.max(minX, Math.min(maxX, left + (float) width * (float) i / (float) (valueCount - 1)));
        if (x > excludeLeft && x < excludeRight) {
          continue;
        }
        boolean onActive = anchorMode == ANCHOR_MODE_CENTER ? (x >= Math.min(centerX, cx) && x <= Math.max(centerX, cx)) : x < cx;
        c.drawCircle(x, cy, dotRadius, Paints.fillingPaint(onActive ? inactiveColor : color));
      }
    } else {
      final float hideDistance = Screen.dp(12f);
      float endX = right - outer;
      if (endX - cx >= hideDistance) {
        c.drawCircle(endX, cy, dotRadius, Paints.fillingPaint(color));
      }
      if (anchorMode == ANCHOR_MODE_CENTER) {
        float startX = left + outer;
        if (cx - startX >= hideDistance) {
          c.drawCircle(startX, cy, dotRadius, Paints.fillingPaint(color));
        }
      }
    }

    float smallValue = getSmallValue();
    if (smallValue != -1f) {
      float x = Math.max(left + outer, Math.min(right - outer, left + width * smallValue));
      if (!(x > excludeLeft && x < excludeRight)) {
        c.drawCircle(x, cy, dotRadius, Paints.fillingPaint(x < cx ? inactiveColor : color));
      }
    }

    RectF rectF = Paints.getRectF();
    rectF.set(cx - handleWidth / 2f, cy - handleHeight / 2f, cx + handleWidth / 2f, cy + handleHeight / 2f);
    c.drawRoundRect(rectF, handleWidth / 2f, handleWidth / 2f, Paints.fillingPaint(color));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (XgramExpressive.isEnabled()) {
      xgramDraw(c);
      return;
    }
    int height = Screen.dp(1f);

    int left = getTotalPaddingLeft();
    int right = getMeasuredWidth() - getTotalPaddingRight();
    int cy = findCenterY();
    int top = cy - height;
    int bottom = cy + height;
    int width = right - left;

    float smallValue = getSmallValue();

    float overlayAlpha = (1f - enableFactor) * .65f;

    final int origColor = colorAnimator == null || !colorAnimator.isAnimating() ? Theme.getColor(colorId) : ColorUtils.fromToArgb(Theme.getColor(fromColorId), Theme.getColor(toColorId), colorAnimator.getFactor());
    final int color = overlayAlpha > 0f ? ColorUtils.compositeColor(origColor, ColorUtils.color((int) (255f * overlayAlpha), 0)) : origColor;
    int inactiveColor = forceBackgroundColorId != 0 ? Theme.getColor(forceBackgroundColorId) : ColorUtils.color(0x44, color);
    int secondaryColor = forceSecondaryColorId != 0 ? Theme.getColor(forceSecondaryColorId) : ColorUtils.color(0x88, color);

    final int gapRadius = Screen.dp(4.5f);
    final int circleRadius = Screen.dp(2.5f);

    RectF rectF = Paints.getRectF();
    rectF.set(left, top, right, bottom);
    if (valueCount > 1) {
      final int itemWidth = width / (valueCount - 1);
      int x = left;
      for (int i = 0; i < valueCount - 1; i++) {
        rectF.set(x + gapRadius, rectF.top, x + itemWidth - gapRadius, rectF.bottom);
        c.drawRoundRect(rectF, height, height, Paints.fillingPaint(inactiveColor));
        x += itemWidth;
      }
    } else {
      c.drawRoundRect(rectF, height, height, Paints.fillingPaint(inactiveColor));
    }

    if (secondaryValue > 0f && anchorMode == ANCHOR_MODE_START) {
      float leftX = left + (float) width * secondaryValueOffset;
      float rightX = leftX + (float) width * secondaryValue;
      rectF.set(leftX, top, rightX, bottom);
      c.drawRoundRect(rectF, height, height, Paints.fillingPaint(secondaryColor));
    }

    int cx;
    if (anchorMode == ANCHOR_MODE_CENTER) {
      int centerX = left + width / 2;
      cx = centerX + (int) ((float) (width / 2) * value);
      rectF.set(Math.min(centerX, cx), top, Math.max(centerX, cx), bottom);
    } else {
      cx = left + (int) ((float) width * value);
      rectF.set(left, top, cx, bottom);
    }

    if (valueCount > 1) {
      final float minX = rectF.left;
      final float maxX = rectF.right;
      final int itemWidth = width / (valueCount - 1);
      int x = left;
      for (int i = 0; i < valueCount - 1; i++) {
        if (x >= minX && x <= maxX) {
          rectF.set(Math.max(minX, x) + gapRadius, rectF.top, Math.min(maxX, x + itemWidth) - gapRadius, rectF.bottom);
          c.drawRoundRect(rectF, height, height, Paints.fillingPaint(color));
        }
        x += itemWidth;
      }
    } else {
      c.drawRoundRect(rectF, height, height, Paints.fillingPaint(color));
    }

    int smallX = smallValue != -1f ? (int) (left + width * smallValue) :- 1;
    int smallRadius = Screen.dp(3.5f);

    if (smallX != -1) {
      c.drawCircle(smallX, cy, smallRadius, Paints.fillingPaint(smallX > cx ? inactiveColor : color));
    }

    if (valueCount > 1) {
      int pointX = left;
      int itemWidth = (right - left) / (valueCount - 1);

      for (int i = 0; i < valueCount; i++) {
        c.drawCircle(pointX, cy, circleRadius, Paints.fillingPaint(pointX > cx ? inactiveColor : color));
        pointX += itemWidth;
      }
    }

    int radius = Screen.dp(6f) + (int) ((float) Screen.dp(4f) * upFactor);
    c.drawCircle(cx, cy, radius, Paints.fillingPaint(color));

    if (smallX != -1 && Math.abs(cx - smallX) < radius + smallRadius) {
      c.save();
      c.clipRect(cx - radius, cy - radius, cx + radius, cy + radius);
      float smallFactor;
      if (smallX > cx + radius && smallX < cx + radius + smallRadius) {
        smallFactor = (float) (smallX - cx - radius) / (float) smallRadius;
      } else {
        smallFactor = 0f;
      }
      c.drawCircle(smallX, cy, smallRadius, Paints.fillingPaint(ColorUtils.fromToArgb(Theme.fillingColor(), inactiveColor, smallFactor)));
      c.restore();
    }
  }
}
