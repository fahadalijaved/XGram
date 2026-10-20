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
package com.fahadali.xgram.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramLoadingIndicator;
import com.fahadali.xgram.appearance.XgramMotion;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * The Activity page's frame (its list's parent), which adds pull down to refresh at the top of the list. The list hands
 * it the drag it cannot use itself (nested scrolling, touches only): pulling down at the top brings the indicator down
 * over the list, the finger going back up takes it back first, a fling and its overscroll stay the list's own (the
 * stretch). Past the threshold (80dp of travel, after the finger's 0.5 resistance) a tick of haptics; let go there and
 * it refreshes: the indicator rests at the threshold and turns until {@link #finishRefresh}, which shows a short status
 * in its place ("Updated") and takes it back up.
 * <p>
 * With Expressive design the indicator is Material 3 Expressive's contained loading indicator (a 48dp primaryContainer
 * circle, the shape in onPrimaryContainer), its shape morphing and turning with the pull, then turning on its own; the
 * status grows out of the circle as a pill. Telegram X's look: a plain circular indicator in the theme's progress color
 * on a 40dp filling circle. Reduce motion: no springs, the indicator just shows and goes. Drawn in dispatchDraw, over
 * the list; nothing is allocated per frame.
 */
@SuppressLint("ViewConstructor")
final class XgramActivityPullFrame extends FrameLayoutFix implements NestedScrollingParent3 {
  interface Listener {
    /** Whether a pull may start now (Activity on, the page alive). */
    boolean canPull ();
    /** Let go past the threshold: refresh, then call {@link #finishRefresh}. */
    void onPullRefresh ();
  }

  /** Compose PullToRefreshDefaults: the threshold (the indicator's travel to it), the finger's resistance. */
  private static final float THRESHOLD_DP = 80f, DRAG_MULTIPLIER = .5f;
  private static final float CONTAINER_DP = 48f, CLASSIC_CONTAINER_DP = 40f, PILL_HEIGHT_DP = 40f, PILL_PADDING_DP = 16f;
  private static final float CLASSIC_RADIUS_DP = 9f, CLASSIC_STROKE_DP = 2.5f;

  private final Listener listener;
  private final boolean expressive;
  private final NestedScrollingParentHelper nested = new NestedScrollingParentHelper(this);
  private final int[] spareConsumed = new int[2];
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final int thresholdPx, containerPx;

  /** The indicator's travel from the top (px): 0 hidden above, the threshold while refreshing. */
  private float offset;
  /** A touch that may pull is on the list; the finger's pull so far beyond the list's top (px, before resistance). */
  private boolean touch, pulling, armed;
  private float rawDrag;
  private boolean refreshing;
  private long refreshStart;
  /** Where the shape's motion was when the finger let go (ms of the indicator's own time). */
  private long pullTimeMs;
  /** The status after a refresh, and the pill's growth out of the circle (0..1). */
  @Nullable private String status;
  private float statusWidth, statusFactor;
  private final XgramExpressive.Spring offsetSpring, statusSpring;
  private final Runnable retract = this::retract;

  XgramActivityPullFrame (@NonNull Context context, @NonNull Listener listener) {
    super(context);
    this.listener = listener;
    this.expressive = XgramExpressive.isEnabled();
    this.thresholdPx = Screen.dp(THRESHOLD_DP);
    this.containerPx = Screen.dp(expressive ? CONTAINER_DP : CLASSIC_CONTAINER_DP);
    paint.setStyle(Paint.Style.FILL);
    arcPaint.setStyle(Paint.Style.STROKE);
    arcPaint.setStrokeCap(Paint.Cap.ROUND);
    arcPaint.setStrokeWidth(Screen.dp(CLASSIC_STROKE_DP));
    textPaint.setTypeface(expressive ? XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false) : Fonts.getRobotoMedium());
    textPaint.setTextSize(Screen.sp(expressive ? XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L) : 14f));
    offsetSpring = new XgramExpressive.Spring(expressive ? XgramExpressive.SPATIAL_FAST : XgramExpressive.STD_SPATIAL_FAST, 0f, (s, value) -> {
      offset = value;
      if (value <= .5f && s.getTarget() <= 0f && !refreshing) {
        offset = 0f;
        clearStatus();
      }
      invalidate();
    }).setThreshold(.5f);
    statusSpring = new XgramExpressive.Spring(expressive ? XgramExpressive.SPATIAL_FAST : XgramExpressive.STD_SPATIAL_FAST, 0f, (s, value) -> {
      statusFactor = value;
      invalidate();
    });
  }

  // State

  boolean isRefreshing () {
    return refreshing;
  }

  /**
   * The refresh ended: {@code text} (null: none) shows in the indicator's place for {@code holdMs}, then the
   * indicator goes back up. Announced to TalkBack.
   */
  void finishRefresh (@Nullable String text, long holdMs) {
    if (!refreshing) {
      return;
    }
    refreshing = false;
    removeCallbacks(retract);
    if (text == null || text.isEmpty()) {
      retract();
      return;
    }
    status = text;
    statusWidth = textPaint.measureText(text);
    statusSpring.setValue(0f);
    statusFactor = 0f;
    statusSpring.animateTo(1f);
    announceForAccessibility(text);
    postDelayed(retract, holdMs);
    invalidate();
  }

  /** Everything back as it rests (the page goes, or Activity was turned off), without an animation. */
  void reset () {
    removeCallbacks(retract);
    refreshing = false;
    pulling = false;
    armed = false;
    touch = false;
    rawDrag = 0f;
    offsetSpring.setValue(0f);
    offset = 0f;
    clearStatus();
    invalidate();
  }

  private void retract () {
    removeCallbacks(retract);
    if (refreshing || pulling) {
      return;
    }
    offsetSpring.animateTo(0f);
  }

  private void clearStatus () {
    status = null;
    statusSpring.setValue(0f);
    statusFactor = 0f;
  }

  // Pulling

  /** The indicator's travel for {@code raw} px of finger: the 0.5 resistance, then a tension past the threshold (Compose's). */
  private float travelOf (float raw) {
    final float travel = raw * DRAG_MULTIPLIER;
    if (travel <= thresholdPx) {
      return travel;
    }
    final float over = Math.min(2f, travel / thresholdPx - 1f);
    return thresholdPx * (1f + over - over * over / 4f);
  }

  private void beginPull () {
    pulling = true;
    removeCallbacks(retract);
    clearStatus();
    offsetSpring.cancel();
    // A pull over an indicator still going up starts where it is
    rawDrag = Math.min(offset, thresholdPx) / DRAG_MULTIPLIER;
    armed = travelOf(rawDrag) >= thresholdPx;
  }

  private void applyDrag () {
    offset = travelOf(rawDrag);
    offsetSpring.setValue(offset);
    final boolean nowArmed = offset >= thresholdPx;
    if (nowArmed && !armed) {
      thresholdHaptic();
    }
    armed = nowArmed;
    invalidate();
  }

  /** The touch event going through now: a pull refreshes on a lift (ACTION_UP), not on a cancel. */
  private int touchAction = -1;

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    touchAction = e.getActionMasked();
    return super.dispatchTouchEvent(e);
  }

  private void release () {
    if (!pulling) {
      return;
    }
    pulling = false;
    if (armed && touchAction == MotionEvent.ACTION_UP && listener.canPull()) {
      armed = false;
      refreshing = true;
      refreshStart = SystemClock.uptimeMillis();
      pullTimeMs = (long) (Math.min(1f, offset / thresholdPx) * (XgramLoadingIndicator.MORPH_INTERVAL_MS - 1));
      offsetSpring.animateTo(thresholdPx);
      invalidate();
      listener.onPullRefresh();
    } else {
      armed = false;
      offsetSpring.animateTo(0f);
    }
  }

  private void thresholdHaptic () {
    final int feedback;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      feedback = HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE;
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      feedback = HapticFeedbackConstants.CONTEXT_CLICK;
    } else {
      feedback = HapticFeedbackConstants.VIRTUAL_KEY;
    }
    performHapticFeedback(feedback);
  }

  // Nested scrolling (androidx NestedScrollingParent3): touches only, so a fling and its overscroll stay the list's

  @Override
  public boolean onStartNestedScroll (@NonNull View child, @NonNull View target, int axes, int type) {
    if (type != ViewCompat.TYPE_TOUCH) {
      return false; // a fling's start (before the touch's stop) must not reset the touch's state
    }
    touch = (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 && listener.canPull();
    return touch;
  }

  @Override
  public void onNestedScrollAccepted (@NonNull View child, @NonNull View target, int axes, int type) {
    nested.onNestedScrollAccepted(child, target, axes, type);
  }

  @Override
  public void onNestedPreScroll (@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
    if (type == ViewCompat.TYPE_TOUCH && pulling && dy > 0 && rawDrag > 0f) {
      // The finger goes back up: the indicator goes up before the list scrolls
      final int by = (int) Math.min(rawDrag, dy);
      rawDrag -= by;
      consumed[1] += by;
      applyDrag();
    }
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
    if (type != ViewCompat.TYPE_TOUCH || !touch || dyUnconsumed >= 0 || refreshing) {
      return;
    }
    // The list is at its top and the finger goes on down: the indicator comes with it
    if (!pulling) {
      beginPull();
    }
    rawDrag -= dyUnconsumed;
    consumed[1] += dyUnconsumed;
    applyDrag();
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
    spareConsumed[0] = spareConsumed[1] = 0;
    onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, spareConsumed);
  }

  @Override
  public boolean onNestedPreFling (@NonNull View target, float velocityX, float velocityY) {
    // Let go while pulling: the release decides (onStopNestedScroll), the list does not fling
    return pulling && offset > 0f;
  }

  @Override
  public boolean onNestedFling (@NonNull View target, float velocityX, float velocityY, boolean consumed) {
    return false;
  }

  @Override
  public void onStopNestedScroll (@NonNull View target, int type) {
    nested.onStopNestedScroll(target, type);
    if (type != ViewCompat.TYPE_TOUCH) {
      return;
    }
    touch = false;
    release();
  }

  @Override
  public int getNestedScrollAxes () {
    return nested.getNestedScrollAxes();
  }

  // Drawing

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);
    if (offset <= .5f && !refreshing) {
      return;
    }
    final float size = containerPx;
    final float cx = getWidth() / 2f;
    final float cy = offset - size / 2f;
    final float pull = Math.min(1f, offset / thresholdPx);
    final float grow = status != null ? Math.max(0f, statusFactor) : 0f;
    final float fade = Math.min(1f, grow);
    // The container: the circle, growing into the status pill
    final float pillWidth = statusWidth + Screen.dp(PILL_PADDING_DP) * 2f;
    final float width = size + (Math.max(size, pillWidth) - size) * grow;
    final float height = size + (Screen.dp(PILL_HEIGHT_DP) - size) * fade;
    rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
    final int containerColor = expressive ? XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER) : Theme.fillingColor();
    paint.setColor(containerColor);
    final float radius = Math.min(rect.width(), rect.height()) / 2f;
    c.drawRoundRect(rect, radius, radius, paint);
    if (!expressive) {
      // Telegram X's look: a thin ring keeps the filling circle apart from the rows under it
      arcPaint.setStrokeWidth(Screen.dpf(1f));
      arcPaint.setColor(Theme.separatorColor());
      c.drawRoundRect(rect, radius, radius, arcPaint);
      arcPaint.setStrokeWidth(Screen.dp(CLASSIC_STROKE_DP));
    }
    final long now = SystemClock.uptimeMillis();
    // The indicator, fading as the status comes
    if (fade < 1f) {
      if (expressive) {
        final long elapsed = refreshing || status != null ? pullTimeMs + (now - refreshStart) : (long) (pull * (XgramLoadingIndicator.MORPH_INTERVAL_MS - 1));
        final float active = size * XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE * (refreshing || status != null ? 1f : .4f + .6f * pull);
        final int color = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), (1f - fade) * (refreshing || status != null ? 1f : .5f + .5f * pull));
        XgramLoadingIndicator.draw(c, cx, cy, active, color, elapsed);
      } else {
        final float arcRadius = Screen.dpf(CLASSIC_RADIUS_DP);
        rect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);
        final float start, sweep;
        if (refreshing || status != null) {
          final float t = (now - refreshStart) / 1000f;
          start = -90f + 360f * t * 1.2f;
          sweep = 30f + 240f * (.5f - .5f * (float) Math.cos(t * Math.PI * 1.5));
        } else {
          start = -90f + 270f * pull;
          sweep = 10f + 260f * pull;
        }
        arcPaint.setColor(XgramExpressive.withAlpha(Theme.progressColor(), (1f - fade) * (.4f + .6f * pull)));
        c.drawArc(rect, start, sweep, false, arcPaint);
      }
    }
    // The status, in the grown pill
    if (status != null && fade > 0f) {
      final int textColor = expressive ? XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER) : Theme.textAccentColor();
      textPaint.setColor(XgramExpressive.withAlpha(textColor, fade));
      final float baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
      c.drawText(status, cx - statusWidth / 2f, baseline, textPaint);
    }
    if (refreshing || (status != null && fade < 1f)) {
      postInvalidateOnAnimation();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    removeCallbacks(retract);
    offsetSpring.cancel();
    statusSpring.cancel();
    if (!refreshing) {
      offset = 0f;
      clearStatus();
    }
  }
}
