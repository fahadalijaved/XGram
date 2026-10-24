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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * The photo editor's brush size: a vertical slider over the photo's start edge (as in Telegram's own editor), the
 * smallest size at the bottom. While it is dragged a dot beside it shows the stroke at its size on screen, in the
 * brush's colour and look. Only its 56dp strip takes touches; the rest of the view (room for the dot) lets them
 * through to the photo.
 * Telegram X look: a white wedge widening to the top and a white knob. Material 3 Expressive: a 16dp slider track
 * (white below the handle, white at 30% above it, full outer and 2dp inner corners) split by a 44 x 4dp handle that
 * thins to 2dp while held, with a stop dot at the top.
 */
public final class XgramBrushSizeSlider extends View implements FactorAnimator.Target {
  public interface Callback {
    /** The user moved the slider to {@code value} (0..1). */
    void onBrushSizeChanged (XgramBrushSizeSlider slider, float value);
    /** The dot's diameter on screen, in px, at {@code value}. */
    float getBrushDiameter (float value);
  }

  public static final int STYLE_SOLID = 0, STYLE_MARKER = 1, STYLE_NEON = 2, STYLE_OUTLINE = 3;

  private static final float STRIP_DP = 56f, TRACK_CENTER_DP = 28f, MAX_TRACK_DP = 240f, TRACK_INSET_DP = 24f;
  private static final float DOT_GAP_DP = 12f;

  private final boolean expressive;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Path path = new Path();
  private final float[] radii = new float[8];
  private @Nullable Callback callback;

  private float value;
  private int previewColor = 0xffffffff;
  private int previewStyle = STYLE_SOLID;

  private boolean dragging;
  private final BoolAnimator draggingAnimator; // the handle thins, the dot shows
  private final Runnable hideDot = () -> setDotVisible(false);
  private final BoolAnimator dotAnimator;

  public XgramBrushSizeSlider (Context context) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    strokePaint.setStyle(Paint.Style.STROKE);
    draggingAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    dotAnimator = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    setContentDescription(Lang.getString(org.thunderdog.challegram.R.string.XgramEditorSize));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Sets the value without telling the callback (a tool change, the colour handle's size gesture). */
  public void setValue (float value) {
    value = MathUtils.clamp(value);
    if (this.value != value) {
      this.value = value;
      invalidate();
    }
  }

  public float getValue () {
    return value;
  }

  /** The dot's colour and look: the brush's. */
  public void setPreview (int color, int style) {
    if (this.previewColor != color || this.previewStyle != style) {
      this.previewColor = color;
      this.previewStyle = style;
      invalidate();
    }
  }

  /** Shows the dot for a moment (the size changed some other way). */
  public void flashDot () {
    setDotVisible(true);
    removeCallbacks(hideDot);
    postDelayed(hideDot, 700L);
  }

  private void setDotVisible (boolean visible) {
    XgramAppMotion.use(dotAnimator, XgramExpressive.EFFECTS_FAST, true);
    dotAnimator.setValue(visible, true);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  // Geometry

  private float trackTop () {
    final float available = getHeight() - Screen.dp(TRACK_INSET_DP) * 2f;
    final float length = Math.max(0f, Math.min(Screen.dp(MAX_TRACK_DP), available));
    return (getHeight() - length) / 2f;
  }

  private float trackBottom () {
    return getHeight() - trackTop();
  }

  private float trackCenterX () {
    return Lang.rtl() ? getWidth() - Screen.dp(TRACK_CENTER_DP) : Screen.dp(TRACK_CENTER_DP);
  }

  private boolean inStrip (float x) {
    return Lang.rtl() ? x >= getWidth() - Screen.dp(STRIP_DP) : x <= Screen.dp(STRIP_DP);
  }

  private float handleY () {
    final float top = trackTop(), bottom = trackBottom();
    return bottom - (bottom - top) * value;
  }

  // Touch

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        final float top = trackTop(), bottom = trackBottom();
        final float slop = Screen.dp(20f);
        if (bottom - top <= 0f || !inStrip(e.getX()) || e.getY() < top - slop || e.getY() > bottom + slop) {
          return false;
        }
        setDragging(true);
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(true);
        }
        changeValue(e.getY());
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (dragging) {
          changeValue(e.getY());
        }
        return dragging;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (dragging) {
          setDragging(false);
          return true;
        }
        return false;
      }
    }
    return dragging;
  }

  private void changeValue (float y) {
    final float top = trackTop(), bottom = trackBottom();
    final float value = MathUtils.clamp((bottom - y) / Math.max(1f, bottom - top));
    if (this.value != value) {
      if (value == 0f || value == 1f) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      }
      applyValue(value);
    }
  }

  private void applyValue (float value) {
    this.value = value;
    invalidate();
    if (callback != null) {
      callback.onBrushSizeChanged(this, value);
    }
  }

  // Accessibility: a seek bar in percent that TalkBack steps by a tenth (its double tap lands off the 56dp strip)

  private static final float ACCESSIBILITY_STEP = .1f;

  @Override
  public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(android.widget.SeekBar.class.getName());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, value * 100f));
    }
    if (isEnabled()) {
      if (value > 0f) {
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
      }
      if (value < 1f) {
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
      }
    }
  }

  @Override
  public boolean performAccessibilityAction (int action, Bundle arguments) {
    if (super.performAccessibilityAction(action, arguments)) {
      return true;
    }
    if (!isEnabled()) {
      return false;
    }
    float value;
    if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      value = this.value + ACCESSIBILITY_STEP;
    } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      value = this.value - ACCESSIBILITY_STEP;
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action == android.R.id.accessibilityActionSetProgress && arguments != null && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
      value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) / 100f;
    } else {
      return false;
    }
    value = MathUtils.clamp(value);
    if (this.value == value) {
      return false;
    }
    applyValue(value);
    flashDot();
    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    return true;
  }

  private void setDragging (boolean dragging) {
    if (this.dragging != dragging) {
      this.dragging = dragging;
      XgramAppMotion.use(draggingAnimator, XgramExpressive.SPATIAL_FAST, true);
      draggingAnimator.setValue(dragging, true);
      removeCallbacks(hideDot);
      if (dragging) {
        setDotVisible(true);
      } else {
        postDelayed(hideDot, 500L);
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    removeCallbacks(hideDot);
    dragging = false;
    draggingAnimator.setValue(false, false);
    dotAnimator.setValue(false, false);
  }

  // Drawing

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final float top = trackTop(), bottom = trackBottom();
    if (bottom - top <= 0f) {
      return;
    }
    if (expressive) {
      drawExpressive(c, top, bottom);
    } else {
      drawClassic(c, top, bottom);
    }
    drawDot(c);
  }

  private void segment (float left, float top, float right, float bottom, float topRadius, float bottomRadius) {
    if (bottom - top <= 0f) {
      return;
    }
    rect.set(left, top, right, bottom);
    radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
    radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
    path.addRoundRect(rect, radii, Path.Direction.CW);
  }

  private void drawExpressive (Canvas c, float top, float bottom) {
    final float cx = trackCenterX();
    final float width = Screen.dpf(16f), half = width / 2f;
    final float outer = half, inner = Screen.dpf(2f), gap = Screen.dpf(6f);
    final float held = MathUtils.clamp(draggingAnimator.getFloatValue());
    final float handleThickness = Screen.dpf(4f) - Screen.dpf(2f) * held;
    final float handleWidth = Screen.dpf(44f);
    final float y = handleY();

    // inactive part above the handle, active part below it
    path.rewind();
    segment(cx - half, top, cx + half, y - handleThickness / 2f - gap, outer, inner);
    paint.setColor(XgramM3MediaChrome.white(.3f));
    c.drawPath(path, paint);
    path.rewind();
    segment(cx - half, y + handleThickness / 2f + gap, cx + half, bottom, inner, outer);
    paint.setColor(XgramM3MediaChrome.white(1f));
    c.drawPath(path, paint);

    // the stop at the top end
    final float stopY = top + outer;
    if (y - stopY >= Screen.dp(12f)) {
      paint.setColor(XgramM3MediaChrome.white(1f));
      c.drawCircle(cx, stopY, Screen.dpf(2f), paint);
    }

    rect.set(cx - handleWidth / 2f, y - handleThickness / 2f, cx + handleWidth / 2f, y + handleThickness / 2f);
    paint.setColor(XgramM3MediaChrome.white(1f));
    c.drawRoundRect(rect, handleThickness / 2f, handleThickness / 2f, paint);
  }

  private void drawClassic (Canvas c, float top, float bottom) {
    final float cx = trackCenterX();
    final float bottomHalf = Screen.dpf(1.5f), topHalf = Screen.dpf(6f);
    path.rewind();
    path.moveTo(cx - topHalf, top + topHalf);
    path.quadTo(cx - topHalf, top, cx, top);
    path.quadTo(cx + topHalf, top, cx + topHalf, top + topHalf);
    path.lineTo(cx + bottomHalf, bottom - bottomHalf);
    path.quadTo(cx + bottomHalf, bottom, cx, bottom);
    path.quadTo(cx - bottomHalf, bottom, cx - bottomHalf, bottom - bottomHalf);
    path.close();
    paint.setColor(0x66ffffff);
    c.drawPath(path, paint);
    strokePaint.setStrokeWidth(Screen.dpf(1f));
    strokePaint.setColor(0x33000000);
    c.drawPath(path, strokePaint);

    final float y = handleY();
    final float held = MathUtils.clamp(draggingAnimator.getFloatValue());
    final float radius = Screen.dpf(10f) + Screen.dpf(2f) * held;
    paint.setColor(0xffffffff);
    c.drawCircle(cx, y, radius, paint);
    strokePaint.setColor(0x40000000);
    c.drawCircle(cx, y, radius + Screen.dpf(.5f), strokePaint);
  }

  private void drawDot (Canvas c) {
    final float alpha = MathUtils.clamp(dotAnimator.getFloatValue());
    if (alpha <= 0f || callback == null) {
      return;
    }
    final float strip = Screen.dp(STRIP_DP), gapX = Screen.dp(DOT_GAP_DP);
    final float maxDiameter = Math.max(Screen.dp(4f), getWidth() - strip - gapX * 2f);
    final float diameter = Math.max(Screen.dpf(2f), Math.min(maxDiameter, callback.getBrushDiameter(value)));
    final float radius = diameter / 2f;
    final float cx = Lang.rtl() ? getWidth() - strip - gapX - radius : strip + gapX + radius;
    final float cy = Math.max(radius, Math.min(getHeight() - radius, handleY()));
    final float scale = .6f + .4f * alpha;
    final int saveCount = c.save();
    c.scale(scale, scale, cx, cy);
    switch (previewStyle) {
      case STYLE_OUTLINE: {
        // blur, pixelate, eraser: the area, with no colour of its own
        paint.setColor(ColorUtils.alphaColor(alpha * .2f, 0xffffffff));
        c.drawCircle(cx, cy, radius, paint);
        strokePaint.setStrokeWidth(Screen.dpf(2f));
        strokePaint.setColor(ColorUtils.alphaColor(alpha, 0xffffffff));
        c.drawCircle(cx, cy, Math.max(0f, radius - Screen.dpf(1f)), strokePaint);
        strokePaint.setStrokeWidth(Screen.dpf(1f));
        strokePaint.setColor(ColorUtils.alphaColor(alpha * .3f, 0xff000000));
        c.drawCircle(cx, cy, radius + Screen.dpf(.5f), strokePaint);
        break;
      }
      case STYLE_NEON: {
        paint.setColor(ColorUtils.alphaColor(alpha * .18f, previewColor));
        c.drawCircle(cx, cy, radius * 1.6f, paint);
        paint.setColor(ColorUtils.alphaColor(alpha * .3f, previewColor));
        c.drawCircle(cx, cy, radius * 1.25f, paint);
        paint.setColor(ColorUtils.alphaColor(alpha, previewColor));
        c.drawCircle(cx, cy, radius, paint);
        paint.setColor(ColorUtils.alphaColor(alpha, ColorUtils.fromToArgb(previewColor, 0xffffffff, .8f)));
        c.drawCircle(cx, cy, radius * .45f, paint);
        break;
      }
      default: {
        final boolean marker = previewStyle == STYLE_MARKER;
        paint.setColor(ColorUtils.alphaColor(alpha * (marker ? .5f : 1f), previewColor));
        if (marker) {
          // flat ends: a square
          rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
          c.drawRect(rect, paint);
        } else {
          c.drawCircle(cx, cy, radius, paint);
        }
        strokePaint.setStrokeWidth(Screen.dpf(1.5f));
        strokePaint.setColor(ColorUtils.alphaColor(alpha, 0xffffffff));
        if (marker) {
          rect.inset(-Screen.dpf(2f), -Screen.dpf(2f));
          c.drawRect(rect, strokePaint);
        } else {
          c.drawCircle(cx, cy, radius + Screen.dpf(2f), strokePaint);
        }
        break;
      }
    }
    c.restoreToCount(saveCount);
  }
}
