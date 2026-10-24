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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * The photo editor's text colour: a horizontal slider over a strip of colours, white to black through the hues, with a
 * thumb that shows the colour picked. Telegram X look: a thin rounded strip and a white-ringed knob. Material 3
 * Expressive: a 16dp track with full corners and a colour swatch thumb ringed in white that grows while it is held, on
 * the spatial fast spring. RTL runs the strip from the right. TalkBack reads it as a seek bar in percent.
 */
public final class XgramColorSlider extends View implements FactorAnimator.Target {
  public interface Callback {
    void onColorChanged (XgramColorSlider slider, int color, float value);
  }

  /** The strip: white, the hues from red round to pink, then black. */
  private static final int[] COLORS = {
    0xffffffff, 0xffff3b30, 0xffff9500, 0xffffcc00, 0xff34c759, 0xff00c7be, 0xff32ade6, 0xff007aff, 0xff5856d6, 0xffaf52de, 0xffff2d55, 0xff000000
  };

  private final boolean expressive;
  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final BoolAnimator heldAnimator;
  private @Nullable Callback callback;
  private float value;
  private boolean dragging;
  private int shaderLeft = -1, shaderRight = -1;
  private boolean shaderRtl;

  public XgramColorSlider (Context context) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.heldAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    setContentDescription(Lang.getString(R.string.XgramEditorTextColor));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Sets the thumb's place (0..1) without telling the callback. */
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

  public int getColor () {
    return colorAt(value);
  }

  /** The colour at {@code value} (0..1) along the strip. */
  public static int colorAt (float value) {
    value = MathUtils.clamp(value);
    final float position = value * (COLORS.length - 1);
    final int index = Math.min(COLORS.length - 2, (int) position);
    return ColorUtils.fromToArgb(COLORS[index], COLORS[index + 1], position - index);
  }

  /** Where on the strip {@code color} is, near enough (an old colour with no place saved), or -1. */
  public static float valueOf (int color) {
    color |= 0xff000000;
    for (int i = 0; i < COLORS.length; i++) {
      if (COLORS[i] == color) {
        return (float) i / (float) (COLORS.length - 1);
      }
    }
    return -1f;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  // Geometry

  private float edge () {
    return Screen.dp(expressive ? 20f : 18f);
  }

  private float trackStart () {
    return edge();
  }

  private float trackEnd () {
    return getWidth() - edge();
  }

  private float thumbX () {
    final float start = trackStart(), end = trackEnd();
    final float x = start + (end - start) * value;
    return Lang.rtl() ? getWidth() - x : x;
  }

  // Touch

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (trackEnd() - trackStart() <= 0f) {
          return false;
        }
        setDragging(true);
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(true);
        }
        changeValue(e.getX());
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (dragging) {
          changeValue(e.getX());
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

  private void changeValue (float x) {
    final float start = trackStart(), end = trackEnd();
    if (Lang.rtl()) {
      x = getWidth() - x;
    }
    applyValue(MathUtils.clamp((x - start) / Math.max(1f, end - start)));
  }

  private void applyValue (float value) {
    if (this.value != value) {
      this.value = value;
      invalidate();
      if (callback != null) {
        callback.onColorChanged(this, colorAt(value), value);
      }
    }
  }

  private void setDragging (boolean dragging) {
    if (this.dragging != dragging) {
      this.dragging = dragging;
      XgramAppMotion.use(heldAnimator, XgramExpressive.SPATIAL_FAST, true);
      heldAnimator.setValue(dragging, true);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    dragging = false;
    heldAnimator.setValue(false, false);
  }

  // Accessibility: a seek bar in percent, stepped by a twentieth

  private static final float ACCESSIBILITY_STEP = .05f;

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
    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    return true;
  }

  // Drawing

  private void checkShader () {
    final int left = (int) trackStart(), right = (int) trackEnd();
    final boolean rtl = Lang.rtl();
    if (shaderLeft != left || shaderRight != right || shaderRtl != rtl) {
      shaderLeft = left;
      shaderRight = right;
      shaderRtl = rtl;
      final float from = rtl ? getWidth() - left : left, to = rtl ? getWidth() - right : right;
      trackPaint.setShader(new LinearGradient(from, 0, to, 0, COLORS, null, Shader.TileMode.CLAMP));
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final float start = trackStart(), end = trackEnd();
    if (end - start <= 0f) {
      return;
    }
    checkShader();
    final float cy = getHeight() / 2f;
    final float held = MathUtils.clamp(heldAnimator.getFloatValue());
    final int color = colorAt(value);
    final float x = thumbX();
    // the strip's ends are as far in from both edges, so it sits in the same place in RTL
    final float left = start, right = end;
    if (expressive) {
      final float half = Screen.dpf(8f);
      rect.set(left - half, cy - half, right + half, cy + half);
      c.drawRoundRect(rect, half, half, trackPaint);
      // the swatch: the colour ringed in white, over a faint dark edge
      final float radius = Screen.dpf(12f) + Screen.dpf(4f) * held;
      paint.setColor(0x33000000);
      c.drawCircle(x, cy, radius + Screen.dpf(4f), paint);
      paint.setColor(0xffffffff);
      c.drawCircle(x, cy, radius + Screen.dpf(3f), paint);
      paint.setColor(color);
      c.drawCircle(x, cy, radius, paint);
    } else {
      final float half = Screen.dpf(3f);
      rect.set(left - half, cy - half, right + half, cy + half);
      c.drawRoundRect(rect, half, half, trackPaint);
      final float radius = Screen.dpf(9f) + Screen.dpf(2f) * held;
      paint.setColor(0x40000000);
      c.drawCircle(x, cy, radius + Screen.dpf(3f), paint);
      paint.setColor(0xffffffff);
      c.drawCircle(x, cy, radius + Screen.dpf(2.5f), paint);
      paint.setColor(color);
      c.drawCircle(x, cy, radius, paint);
    }
  }
}
