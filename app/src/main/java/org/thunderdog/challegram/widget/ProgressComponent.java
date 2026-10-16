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
 * File created on 11/11/2016
 */
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramLoadingIndicator;
import com.fahadali.xgram.appearance.XgramWavyProgress;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.TimerParticles;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class ProgressComponent implements Runnable, FactorAnimator.Target, Destroyable, BaseActivity.SimpleStateListener {
  public static boolean USE_ROUNDED_CAP = true;

  private static final float MIN_DEGREES_BIG = USE_ROUNDED_CAP ? 8f : 32f;
  private static final float MIN_DEGREES = 25f;
  private static final float MIN_DEGREES_10DP = 10f;

  private static final long TOTAL_ANIMATION_TIME = 1600l;
  private static final long REPEAT_TIME = TOTAL_ANIMATION_TIME * 20;
  private static final long TIME_PER_SWEEP = 600l;
  private static final long TIME_BETWEEN_SWEEP = (TOTAL_ANIMATION_TIME - TIME_PER_SWEEP * 2) / 2;

  private long totalAnimationTime = TOTAL_ANIMATION_TIME;
  private long timeBetweenSweep = TIME_BETWEEN_SWEEP;
  private long repeatTime = REPEAT_TIME;
  private long timePerSweep = TIME_PER_SWEEP;

  private int radius;
  private final Rect drawingRect;
  private final RectF arcRect;

  private int color;
  private boolean colorForced;
  private float alpha = 1f;
  private final MultipleViewProvider currentViews;
  private ViewProvider viewProvider;
  private long startTime;
  private boolean monotonic;

  private boolean useLargerPaint;
  private float strokeWidth;

  private final BaseActivity context;
  private boolean uiResumed = true;

  private long timerStartMs = -1, timerEndMs = -1;

  public ProgressComponent (BaseActivity context, int radius) {
    this.radius = radius;
    this.drawingRect = new Rect();
    this.arcRect = new RectF();
    this.currentViews = new MultipleViewProvider();
    this.viewProvider = currentViews;
    this.context = context;
    /*context.addSimpleStateListener(this);
    this.uiResumed = context.getActivityState() == UI.STATE_RESUMED;*/
  }

  @Override
  public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
    setUiResumed(newState == UI.State.RESUMED);
  }

  @Override
  public void performDestroy () {
    context.removeSimpleStateListener(this);
    setUiResumed(false);
    if (XgramExpressive.isEnabled() && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
      // XGram: a destroyed component leaves no frame callback behind
      android.view.Choreographer.getInstance().removeFrameCallback(xgramFrame);
      scheduled = false;
    }
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider != null ? viewProvider : currentViews;
  }

  public void setRadius (int radius) {
    if (this.radius != radius) {
      this.radius = radius;
      setBounds(drawingRect.left, drawingRect.top, drawingRect.right, drawingRect.bottom);
    }
  }

  public void setDurations (long totalTime, long timePerSweep) {
    this.totalAnimationTime = totalTime;
    this.timePerSweep = timePerSweep;
    this.timeBetweenSweep = (totalAnimationTime - timePerSweep * 2) / 2;
    setRepeatTime(totalAnimationTime * 10);
  }

  private boolean isPrecise;

  // XGram M3E: precise progress drawn as the wavy ring (set by FileProgressComponent for theme colored file circles)
  private boolean xgramWavy;

  public void setXgramWavy (boolean wavy) {
    if (this.xgramWavy != wavy) {
      this.xgramWavy = wavy;
      invalidate();
    }
  }

  // Set by FileProgressComponent right before each draw (no invalidation of their own)
  private boolean xgramTrackSet;
  private int xgramTrackColor;
  private float xgramAmplitude = -1f;
  private float xgramQueued, xgramQueuedSize;

  /** Track of the wavy ring (alpha is multiplied by the component alpha), instead of the indicator at 30 %. */
  public void setXgramTrackColor (int color) {
    this.xgramTrackSet = true;
    this.xgramTrackColor = color;
  }

  /** Peak wave amplitude in px (0: a flat ring), instead of min(1.5dp, stroke x .4). */
  public void setXgramAmplitude (float amplitudePx) {
    this.xgramAmplitude = amplitudePx;
  }

  /**
   * Queued (0 %) crossfade of the wavy ring: at {@code factor} 1 the contained loading indicator of
   * {@code indicatorSizePx} (or, when that is 0, an indeterminate flat arc on the ring) replaces the ring.
   */
  public void setXgramQueued (float factor, float indicatorSizePx) {
    this.xgramQueued = Math.max(0f, Math.min(1f, factor));
    this.xgramQueuedSize = indicatorSizePx;
  }

  private static Paint xgramSpinnerPaint;

  private static Paint xgramSpinnerPaint (int color, float strokeWidth) {
    if (xgramSpinnerPaint == null) {
      xgramSpinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      xgramSpinnerPaint.setStyle(Paint.Style.STROKE);
      xgramSpinnerPaint.setStrokeCap(Paint.Cap.ROUND);
    }
    xgramSpinnerPaint.setStrokeWidth(strokeWidth);
    xgramSpinnerPaint.setColor(color);
    return xgramSpinnerPaint;
  }

  public void setIsPrecise () {
    this.isPrecise = true;
    this.repeatTime = 1800l;
  }

  public void setRepeatTime (long repeatTime) {
    this.repeatTime = repeatTime;
  }

  public void setSlowerDurations () {
    setDurations(1700l, 600);
  }

  public void setUseLargerPaint () {
    setUseLargerPaint(Screen.dp(2f));
  }

  public void setUseLargerPaint (float strokeWidth) {
    this.useLargerPaint = true;
    this.strokeWidth = strokeWidth;
  }

  public void setBounds (int left, int top, int right, int bottom) {
    drawingRect.set(left, top, right, bottom);

    int centerX = drawingRect.centerX();
    int centerY = drawingRect.centerY();
    arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
  }

  public void forceColor (int color) {
    if (this.color != color) {
      boolean prevIsActive = isActive();
      this.color = color;
      this.colorForced = true;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  private void setUiResumed (boolean isResumed) {
    if (this.uiResumed != isResumed) {
      boolean prevIsActive = isActive();
      this.uiResumed = isResumed;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  public float getAlpha () {
    return alpha;
  }

  public void setAlpha (float alpha) {
    if (this.alpha != alpha) {
      boolean prevIsActive = isActive();
      this.alpha = alpha;
      scheduleInvalidateIfNeeded(false, prevIsActive);
    }
  }

  public void attachToView (@Nullable View view) {
    boolean prevIsActive = isActive();
    this.currentViews.attachToView(view);
    scheduleInvalidateIfNeeded(false, prevIsActive);
  }

  public void detachFromView (@Nullable View view) {
    this.currentViews.detachFromView(view);
  }

  private boolean isActive () {
    return viewProvider.hasAnyTargetToInvalidate() && (Color.alpha(color) > 0 || !colorForced) && uiResumed;
  }

  private boolean useStupidInvalidate;

  public void setUseStupidInvalidate () {
    this.useStupidInvalidate = true;
  }

  private int getColor () {
    return ColorUtils.alphaColor(alpha, colorForced ? color : Theme.progressColor());
  }

  public void restartAnimationIfNeeded () {
    scheduleInvalidateIfNeeded(false, true /*unknown, actually*/);
  }

  private boolean scheduled;

  private void scheduleInvalidateIfNeeded (boolean byMyself, boolean prevIsActive) {
    if (viewProvider.hasAnyTargetToInvalidate()) {
      if (isActive() && !prevIsActive) {
        startTime = SystemClock.uptimeMillis();
      }
      if (!byMyself) {
        invalidate();
      }
      if (Color.alpha(getColor()) > 0) {
        if (XgramExpressive.isEnabled() && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
          // XGram: redraw on vsync (120 Hz) instead of a handler delay
          android.view.Choreographer choreographer = android.view.Choreographer.getInstance();
          if (!byMyself || scheduled) {
            choreographer.removeFrameCallback(xgramFrame);
          }
          scheduled = true;
          choreographer.postFrameCallback(xgramFrame);
          return;
        }
        if (!byMyself || scheduled) {
          UI.getProgressHandler().removeCallbacks(this);
        }
        scheduled = true;
        UI.getProgressHandler().postDelayed(this, monotonic ? 3 : getFrameDelay());
      }
    }
  }

  private static long getFrameDelay () {
    return Math.max(8, ValueAnimator.getFrameDelay());
  }

  private void invalidate () {
    if (useStupidInvalidate) {
      viewProvider.invalidate();
    } else {
      viewProvider.invalidate(drawingRect);
    }
  }

  @Override
  public void run () {
    scheduled = false;
    invalidate();
  }

  private final android.view.Choreographer.FrameCallback xgramFrame = frameTimeNanos -> run();

  // Progress utils

  private static final int PRECISE_ANIMATOR = 0;
  private float preciseFactor;
  private FactorAnimator preciseAnimator;
  private static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator(.72f);

  public void setProgress (float progress, boolean animated) {
    progress = Math.min(1f, Math.max(0f, progress));
    if (animated && isActive() && (preciseFactor != progress || preciseAnimator != null)) {
      if (preciseAnimator == null) {
        preciseAnimator = new FactorAnimator(PRECISE_ANIMATOR, this, DECELERATE_INTERPOLATOR, 180l, preciseFactor); // 400
      }
      XgramAppMotion.use(preciseAnimator, XgramExpressive.EFFECTS_DEFAULT, true); // XGram M3E: progress moves on a spring
      preciseAnimator.animateTo(progress);
    } else {
      if (preciseAnimator != null) {
        preciseAnimator.cancel();
        preciseAnimator.forceFactor(progress);
      }
      this.preciseFactor = progress;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case PRECISE_ANIMATOR: {
        setPreciseFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private void setPreciseFactor (float factor) {
    if (this.preciseFactor != factor) {
      this.preciseFactor = factor;
      if (isPrecise && XgramExpressive.isEnabled()) {
        invalidate(); // XGram M3E: precise rings are not redrawn every frame, so the animated value redraws itself
      }
      // invalidate();
      // No invalidate since it's scheduled?
    }
  }

  public void setMonotonic (boolean isMonotonic) {
    this.monotonic = isMonotonic;
  }

  private boolean noStartDelay;

  public void setNoStartDelay (boolean noStartDelay) {
    this.noStartDelay = noStartDelay;
  }

  public void setTimer (long fromDate, long toDate) {
    long date = System.currentTimeMillis();
    long toDiff = toDate - date;
    long fromDiff = fromDate - date;

    long now = SystemClock.uptimeMillis();

    this.timerStartMs = now + fromDiff;
    this.timerEndMs = now + toDiff;
  }

  private boolean isTimer () {
    return timerStartMs != -1 && timerEndMs != -1;
  }

  public long getTimerRemainingTimeMillis () {
    return (long) ((float) (timerEndMs - timerStartMs) * getTimerValue());
  }

  private float getTimerValue () {
    long now = SystemClock.uptimeMillis();
    if (now <= timerStartMs) {
      return 1f;
    } else if (now >= timerEndMs) {
      return 0f;
    } else {
      now -= timerStartMs;
      return 1f - ((float) now / (float) (timerEndMs - timerStartMs));
    }
  }

  // Drawing

  private TimerParticles timerParticles;

  public void draw (Canvas c) {
    if (alpha <= 0f)
      return;

    final long timeMs = (monotonic ? SystemClock.uptimeMillis() : SystemClock.uptimeMillis() - startTime) + (noStartDelay ? timeBetweenSweep + timePerSweep + timeBetweenSweep : 0);
    final float startAngle, sweepAngle;
    final boolean isTimer = isTimer();

    if (!isTimer && !isPrecise && radius >= Screen.dp(3f) && XgramExpressive.isEnabled()) {
      // XGram M3E: the expressive loading indicator for every indeterminate spinner of 3dp and more (round 5: the
      // small ones too); the old spinner diameter stands for its active indicator, at least 16dp, but never larger
      // than the box it was given when that box is smaller
      float activeSize = Math.max(radius * 2f, Screen.dpf(16f));
      final int boxSize = Math.min(drawingRect.width(), drawingRect.height());
      if (boxSize > 0 && boxSize < activeSize) {
        activeSize = Math.max(radius * 2f, boxSize);
      }
      final int indicatorColor = ColorUtils.alphaColor(alpha, colorForced ? this.color : XgramExpressive.color(XgramExpressive.PRIMARY));
      XgramLoadingIndicator.draw(c, arcRect.centerX(), arcRect.centerY(), activeSize, indicatorColor, timeMs);
      if (!scheduled && isActive()) {
        scheduleInvalidateIfNeeded(true, true);
      }
      return;
    }

    if (xgramWavy && isPrecise && !isTimer && XgramExpressive.isEnabled()) {
      // XGram M3E: wavy determinate ring with a flat track (the track color set by the owner, or the indicator at 30 %).
      // At 0 % (queued) the contained loading indicator, or a flat indeterminate arc where it would be too small, fades in.
      final int indicatorColor = ColorUtils.alphaColor(alpha, colorForced ? this.color : Theme.progressColor());
      final float stroke = useLargerPaint ? strokeWidth : Screen.dp(2f);
      final int trackColor = xgramTrackSet ? ColorUtils.alphaColor(alpha, xgramTrackColor) : XgramExpressive.withAlpha(indicatorColor, .3f);
      final float amplitude = xgramAmplitude >= 0f ? xgramAmplitude : Math.min(Screen.dp(1.5f), stroke * .4f);
      final float queued = xgramQueued;
      final float cx = arcRect.centerX(), cy = arcRect.centerY();
      if (queued < 1f) {
        XgramWavyProgress.drawCircular(c, cx, cy, radius, stroke, preciseFactor,
          queued > 0f ? ColorUtils.alphaColor(1f - queued, indicatorColor) : indicatorColor,
          queued > 0f ? ColorUtils.alphaColor(1f - queued, trackColor) : trackColor,
          amplitude);
      }
      if (queued > 0f) {
        if (xgramQueuedSize > 0f) {
          XgramLoadingIndicator.draw(c, cx, cy, xgramQueuedSize, ColorUtils.alphaColor(queued, indicatorColor), timeMs);
        } else {
          XgramWavyProgress.drawCircularIndeterminate(c, cx, cy, radius, stroke, ColorUtils.alphaColor(queued, indicatorColor), ColorUtils.alphaColor(queued, trackColor), timeMs);
        }
      }
      // Frames only while something moves: the queued indicator, the traveling wave, or the progress spring
      final boolean needFrames = queued > 0f ||
        (amplitude > 0f && preciseFactor > 0f && preciseFactor < 1f && !org.thunderdog.challegram.unsorted.Settings.instance().needReduceMotion()) ||
        (preciseAnimator != null && preciseAnimator.isAnimating());
      if (needFrames && !scheduled && isActive()) {
        scheduleInvalidateIfNeeded(true, true);
      }
      return;
    }

    if (!isTimer && !isPrecise && XgramExpressive.isEnabled()) {
      // XGram M3E: spinners under 10dp keep Telegram X's sweep, with round caps over a full track
      final long time = timeMs % totalAnimationTime;
      final long timeStart1 = timeBetweenSweep;
      final long timeStart2 = timePerSweep + timeBetweenSweep + timeBetweenSweep;
      final float factor1 = time < timeStart1 ? 0f : time > timeStart1 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart1) / (float) timePerSweep);
      final float factor2 = time < timeStart2 ? 0f : time > timeStart2 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart2) / (float) timePerSweep);
      final float spinnerStart = (270f * factor2 + (360f + 90f) * ((float) time / (float) totalAnimationTime) + (360f * ((float) (timeMs % repeatTime) / (float) repeatTime))) % 360f;
      final float spinnerSweep = (radius >= Screen.dp(18f) ? MIN_DEGREES_BIG : radius >= Screen.dp(10f) ? MIN_DEGREES_10DP : MIN_DEGREES) + factor1 * 270f * (1f - factor2);
      final int spinnerColor = ColorUtils.alphaColor(alpha, colorForced ? this.color : XgramExpressive.color(XgramExpressive.PRIMARY));
      final float spinnerStroke = Math.max(Screen.dpf(1.5f), radius / 5f);
      c.drawCircle(arcRect.centerX(), arcRect.centerY(), radius, xgramSpinnerPaint(XgramExpressive.withAlpha(spinnerColor, .24f), spinnerStroke));
      c.drawArc(arcRect, spinnerStart, spinnerSweep, false, xgramSpinnerPaint(spinnerColor, spinnerStroke));
      if (!scheduled && isActive()) {
        scheduleInvalidateIfNeeded(true, true);
      }
      return;
    }

    if (isTimer) {
      sweepAngle = 360f * getTimerValue();
      startAngle = 270f - sweepAngle;
    } else if (isPrecise) {
      if (XgramExpressive.isEnabled()) {
        startAngle = -90f; // XGram M3E: determinate rings never spin, they start at 12 o'clock
      } else {
        startAngle = (360f * ((float) (timeMs % repeatTime) / (float) repeatTime)) % 360f;
      }
      sweepAngle = MIN_DEGREES_10DP + (360f - MIN_DEGREES_10DP) * preciseFactor;
    } else {
      final long time = timeMs % totalAnimationTime;

      final long timeStart1 = timeBetweenSweep;
      final long timeStart2 = timePerSweep + timeBetweenSweep + timeBetweenSweep;

      final float factor1 = time < timeStart1 ? 0f : time > timeStart1 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart1) / (float) timePerSweep);
      final float factor2 = time < timeStart2 ? 0f : time > timeStart2 + timePerSweep ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (time - timeStart2) / (float) timePerSweep);

      startAngle = (270f * factor2 + (360f + 90f) * ((float) time / (float) totalAnimationTime) + (360f * ((float) (timeMs % repeatTime) / (float) repeatTime))) % 360f;
      sweepAngle = (radius >= Screen.dp(18f) ? MIN_DEGREES_BIG : radius >= Screen.dp(10f) ? MIN_DEGREES_10DP : MIN_DEGREES) + factor1 * 270f * (1f - factor2);
    }

    final int color = ColorUtils.alphaColor(alpha, colorForced ? this.color : Theme.progressColor());
    c.drawArc(arcRect, startAngle, sweepAngle, false, useLargerPaint ? Paints.getProgressPaint(color, strokeWidth) : Paints.getProgressOuterPaint(color));

    if (isTimer) {
      float finalAngle = MathUtils.modulo(startAngle, 360);
      double rad = Math.toRadians(finalAngle);
      double sin = Math.sin(rad);
      double cos = Math.cos(rad);

      float cx = (float) (arcRect.centerX() + arcRect.width() / 2f * cos);
      float cy = (float) (arcRect.centerY() + arcRect.height() / 2f * sin);

      c.drawCircle(cx, cy, strokeWidth / 2f, Paints.fillingPaint(color));
      if (timerParticles == null) {
        timerParticles = new TimerParticles();
      }
      timerParticles.draw(c, color, strokeWidth, arcRect, 360f - sweepAngle, alpha);
    }

    if (isPrecise && !isTimer && XgramExpressive.isEnabled()) {
      // XGram M3E: a ring that does not spin needs frames only while its progress animates
      if (preciseAnimator != null && preciseAnimator.isAnimating() && !scheduled && isActive()) {
        scheduleInvalidateIfNeeded(true, true);
      }
      return;
    }

    if (!scheduled && isActive()) {
      scheduleInvalidateIfNeeded(true, true);
    }
  }

  public static ProgressComponent simpleInstance (View view, float radiusDp, int left, int top, int width, int height) {
    ProgressComponent progress = new ProgressComponent(UI.getContext(view.getContext()), Screen.dp(radiusDp));
    progress.setUseLargerPaint();
    progress.setSlowerDurations();
    progress.forceColor(0xffffffff);
    progress.setAlpha(0f);
    progress.setBounds(left, top, left + width, top + height);
    progress.attachToView(view);
    return progress;
  }
}
