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
import android.text.TextPaint;
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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

/**
 * The Cut out tool's tolerance (XgramCutoutEditor), in the colour row's place: "Tolerance", a horizontal slider and the
 * value in percent, from the start edge.
 * Telegram X look: a thin white line, brighter up to a white knob. Material 3 Expressive: a 16dp slider track (white
 * up to the handle, white at 30% after it, full outer and 2dp inner corners) split by a 4 x 44dp handle that thins to
 * 2dp while held, with a stop dot at the end, as the brush size slider.
 */
public final class XgramToleranceRow extends View implements FactorAnimator.Target {
  public interface Callback {
    /** The user moved the slider to {@code value} (0..1); {@code done}: let go (or changed by TalkBack). */
    void onToleranceChanged (XgramToleranceRow row, float value, boolean done);
  }

  /** The row sits between Undo and Redo (their margins), so no padding of its own. */
  private static final float PADDING_DP = 0f, GAP_DP = 8f, TOUCH_SLOP_DP = 16f;
  /** Narrower than this with the label, the label goes (TalkBack still reads it): the track keeps its room. */
  private static final float MIN_TRACK_DP = 120f;

  private final boolean expressive;
  private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final RectF rect = new RectF();
  private final float[] radii = new float[8];
  private final String label;
  private final float labelWidth, valueWidth;
  private final BoolAnimator draggingAnimator;
  private @Nullable Callback callback;
  private float value;
  private boolean dragging;

  public XgramToleranceRow (Context context) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.label = Lang.getString(R.string.XgramEditorCutoutTolerance);
    final float textSize = Screen.dpf(expressive ? XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L) : 14f);
    labelPaint.setTextSize(textSize);
    valuePaint.setTextSize(textSize);
    if (expressive) {
      labelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      valuePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      labelPaint.setColor(XgramM3MediaChrome.white(.8f));
      valuePaint.setColor(XgramM3MediaChrome.white(.8f));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        valuePaint.setFontFeatureSettings("tnum");
      }
    } else {
      labelPaint.setTypeface(Fonts.getRobotoRegular());
      valuePaint.setTypeface(Fonts.getRobotoRegular());
      labelPaint.setColor(0xccffffff);
      valuePaint.setColor(0xccffffff);
    }
    labelWidth = labelPaint.measureText(label);
    valueWidth = valuePaint.measureText("100%");
    draggingAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    setContentDescription(label);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Sets the value without telling the callback. */
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

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  // Geometry: the label at the start, the value at the end, the track between them

  /** Whether the label fits with a track at least MIN_TRACK_DP long (a narrow screen: only the track and the value). */
  private boolean labelShown () {
    return getWidth() - Screen.dp(PADDING_DP) * 2 - labelWidth - valueWidth - Screen.dp(GAP_DP) * 2 >= Screen.dp(MIN_TRACK_DP);
  }

  private float trackStart () {
    return Screen.dp(PADDING_DP) + (labelShown() ? labelWidth + Screen.dp(GAP_DP) : 0f);
  }

  private float trackEnd () {
    return getWidth() - Screen.dp(PADDING_DP) - valueWidth - Screen.dp(GAP_DP);
  }

  /** The handle's x for {@code value}: from the start edge (the right in RTL). */
  private float handleX () {
    final float start = trackStart(), end = trackEnd();
    final float offset = (end - start) * value;
    return Lang.rtl() ? getWidth() - start - offset : start + offset;
  }

  // Touch

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        final float start = trackStart(), end = trackEnd();
        if (end - start <= 0f || !isEnabled()) {
          return false;
        }
        final float x = Lang.rtl() ? getWidth() - e.getX() : e.getX();
        if (x < start - Screen.dp(TOUCH_SLOP_DP) || x > end + Screen.dp(TOUCH_SLOP_DP)) {
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
          if (callback != null) {
            callback.onToleranceChanged(this, value, true);
          }
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
    final float value = MathUtils.clamp((x - start) / Math.max(1f, end - start));
    if (this.value != value) {
      if (value == 0f || value == 1f) {
        com.fahadali.xgram.XgramHaptics.segmentTick(this); // an end of the slider
      }
      applyValue(value);
    }
  }

  private void applyValue (float value) {
    this.value = value;
    invalidate();
    if (callback != null) {
      callback.onToleranceChanged(this, value, !dragging);
    }
  }

  private void setDragging (boolean dragging) {
    if (this.dragging != dragging) {
      this.dragging = dragging;
      XgramAppMotion.use(draggingAnimator, XgramExpressive.SPATIAL_FAST, true);
      draggingAnimator.setValue(dragging, true);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    dragging = false;
    draggingAnimator.setValue(false, false);
  }

  // Accessibility: a seek bar in percent that TalkBack steps by 5%

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

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int width = getWidth();
    final float cy = getHeight() / 2f;
    final boolean rtl = Lang.rtl();
    final Paint.FontMetrics metrics = labelPaint.getFontMetrics();
    final float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
    final float padding = Screen.dp(PADDING_DP);
    final String valueText = Math.round(value * 100f) + "%";
    final float valueTextWidth = valuePaint.measureText(valueText);
    final boolean labelShown = labelShown();
    if (rtl) {
      if (labelShown) {
        c.drawText(label, width - padding - labelWidth, baseline, labelPaint);
      }
      c.drawText(valueText, padding + valueWidth - valueTextWidth, baseline, valuePaint);
    } else {
      if (labelShown) {
        c.drawText(label, padding, baseline, labelPaint);
      }
      c.drawText(valueText, width - padding - valueTextWidth, baseline, valuePaint);
    }
    final float start = trackStart(), end = trackEnd();
    if (end - start <= 0f) {
      return;
    }
    // the track's two ends on screen, the start's first (on the right in RTL)
    final float startX = rtl ? width - start : start, endX = rtl ? width - end : end;
    if (expressive) {
      drawExpressive(c, startX, endX, cy, rtl);
    } else {
      drawClassic(c, startX, endX, cy);
    }
  }

  private void segment (float left, float right, float top, float bottom, float leftRadius, float rightRadius) {
    if (right - left <= 0f) {
      return;
    }
    rect.set(left, top, right, bottom);
    radii[0] = radii[1] = radii[6] = radii[7] = leftRadius;
    radii[2] = radii[3] = radii[4] = radii[5] = rightRadius;
    path.addRoundRect(rect, radii, Path.Direction.CW);
  }

  private void drawExpressive (Canvas c, float startX, float endX, float cy, boolean rtl) {
    final float half = Screen.dpf(8f);
    final float outer = half, inner = Screen.dpf(2f), gap = Screen.dpf(6f);
    final float held = MathUtils.clamp(draggingAnimator.getFloatValue());
    final float thickness = Screen.dpf(4f) - Screen.dpf(2f) * held;
    final float handleHeight = Math.min(Screen.dpf(44f), getHeight());
    final float x = handleX();
    final float left = Math.min(startX, endX), right = Math.max(startX, endX);
    final float beforeHandle = x - thickness / 2f - gap, afterHandle = x + thickness / 2f + gap;

    // active from the start to the handle, inactive after it
    path.rewind();
    segment(left, beforeHandle, cy - half, cy + half, outer, inner);
    paint.setColor(XgramM3MediaChrome.white(rtl ? .3f : 1f));
    c.drawPath(path, paint);
    path.rewind();
    segment(afterHandle, right, cy - half, cy + half, inner, outer);
    paint.setColor(XgramM3MediaChrome.white(rtl ? 1f : .3f));
    c.drawPath(path, paint);

    // the stop at the end
    final float stopX = rtl ? left + outer : right - outer;
    if (Math.abs(stopX - x) >= Screen.dp(12f)) {
      paint.setColor(XgramM3MediaChrome.white(1f));
      c.drawCircle(stopX, cy, Screen.dpf(2f), paint);
    }

    rect.set(x - thickness / 2f, cy - handleHeight / 2f, x + thickness / 2f, cy + handleHeight / 2f);
    paint.setColor(XgramM3MediaChrome.white(1f));
    c.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint);
  }

  private void drawClassic (Canvas c, float startX, float endX, float cy) {
    final float x = handleX();
    final float line = Screen.dpf(1f);
    paint.setColor(0x4dffffff);
    rect.set(Math.min(startX, endX), cy - line, Math.max(startX, endX), cy + line);
    c.drawRoundRect(rect, line, line, paint);
    paint.setColor(0xffffffff);
    rect.set(Math.min(startX, x), cy - line, Math.max(startX, x), cy + line);
    c.drawRoundRect(rect, line, line, paint);
    final float held = MathUtils.clamp(draggingAnimator.getFloatValue());
    final float radius = Screen.dpf(8f) + Screen.dpf(2f) * held;
    paint.setColor(0x40000000);
    c.drawCircle(x, cy, radius + Screen.dpf(1f), paint);
    paint.setColor(0xffffffff);
    c.drawCircle(x, cy, radius, paint);
  }
}
