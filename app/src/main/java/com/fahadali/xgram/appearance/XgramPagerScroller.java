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
import android.content.Context;
import android.os.Build;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * The settle of every ViewPager page swipe on Compose's pager spring (SPATIAL_DEFAULT: 380, .8) with the finger's
 * velocity, instead of androidx ViewPager's quintic scroller. At the first and last page the spring is critically
 * damped and clamped, so it never scrolls past the adapter's ends. Installed into ViewPager.mScroller by reflection.
 * <p>
 * Scroller's getters are final, so the spring runs through Scroller's own scroll mode: a per-scroll spring
 * interpolator over the spring's settle time.
 */
public class XgramPagerScroller extends Scroller {
  private static final float K = 380f, Z = .8f;
  private static final long PENDING_VELOCITY_TIMEOUT = 50, ABORT_VELOCITY_TIMEOUT = 32;
  private static final float MAX_V0 = 60f;

  /** Spring position for f = 0..1 of the current scroll's duration. */
  private static final class SpringCurve implements Interpolator {
    float k = K, z = Z, v0, seconds = .001f;
    float minProgress = Float.NEGATIVE_INFINITY, maxProgress = Float.POSITIVE_INFINITY;

    @Override
    public float getInterpolation (float f) {
      if (f >= 1f) {
        return 1f;
      }
      float p = XgramMotion.springValue(k, z, v0, f * seconds);
      return Math.max(minProgress, Math.min(maxProgress, p));
    }
  }

  private final @Nullable ViewPager pager;
  private final SpringCurve curve;

  private int deltaX, deltaY;
  private float pendingVelocity;
  private long pendingVelocityTime;
  private float lastAbortVelocity;
  private long lastAbortTime;

  public XgramPagerScroller (Context context, @Nullable ViewPager pager) {
    this(context, pager, new SpringCurve());
  }

  private XgramPagerScroller (Context context, @Nullable ViewPager pager, SpringCurve curve) {
    super(context, curve);
    this.pager = pager;
    this.curve = curve;
  }

  /** Scroll velocity (px/s, positive = scrollX grows) of the finger that is lifting now. */
  public void setPendingVelocity (float velocity) {
    this.pendingVelocity = velocity;
    this.pendingVelocityTime = AnimationUtils.currentAnimationTimeMillis();
  }

  private static float durationScale () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return ValueAnimator.getDurationScale();
    }
    return 1f;
  }

  private boolean isEdge (int x) {
    if (x <= 0) {
      return true;
    }
    if (pager == null) {
      return false;
    }
    PagerAdapter adapter = pager.getAdapter();
    int count = adapter != null ? adapter.getCount() : 0;
    int clientWidth = pager.getMeasuredWidth() - pager.getPaddingLeft() - pager.getPaddingRight();
    if (count <= 1 || clientWidth <= 0) {
      return true;
    }
    return x >= (count - 1) * (clientWidth + pager.getPageMargin());
  }

  @Override
  public void startScroll (int startX, int startY, int dx, int dy, int ignoredDuration) {
    long now = AnimationUtils.currentAnimationTimeMillis();
    float velocity;
    if (pendingVelocityTime != 0 && now - pendingVelocityTime < PENDING_VELOCITY_TIMEOUT) {
      velocity = pendingVelocity;
    } else if (lastAbortTime != 0 && now - lastAbortTime < ABORT_VELOCITY_TIMEOUT) {
      velocity = lastAbortVelocity;
    } else {
      velocity = 0f;
    }
    pendingVelocityTime = 0;
    lastAbortTime = 0;
    start(startX, startY, dx, dy, velocity);
  }

  private void start (int startX, int startY, int dx, int dy, float velocityPx) {
    this.deltaX = dx;
    this.deltaY = dy;
    int d = Math.abs(dx) >= Math.abs(dy) ? dx : dy;
    if (d == 0) {
      super.startScroll(startX, startY, dx, dy, 0);
      return;
    }
    boolean edge = dx != 0 && isEdge(startX + dx);
    curve.k = K;
    curve.z = edge || XgramMotion.reduce() ? 1f : Z;
    curve.v0 = Math.max(-MAX_V0, Math.min(MAX_V0, velocityPx / d));
    curve.maxProgress = edge ? 1f : Float.POSITIVE_INFINITY;
    // Never before the first page.
    curve.minProgress = dx > 0 ? -startX / (float) dx : Float.NEGATIVE_INFINITY;
    curve.seconds = XgramMotion.settleSeconds(curve.k, curve.z, curve.v0, .5f / Math.abs(d));
    float scale = durationScale();
    int duration = scale == 0f ? 0 : Math.max(1, Math.round(curve.seconds * 1000f * scale));
    super.startScroll(startX, startY, dx, dy, duration);
  }

  /** px per second along the larger axis. */
  private float currentVelocityPx () {
    if (isFinished()) {
      return 0f;
    }
    int d = Math.abs(deltaX) >= Math.abs(deltaY) ? deltaX : deltaY;
    float scale = durationScale();
    float t = scale > 0f ? timePassed() / 1000f / scale : curve.seconds;
    if (t >= curve.seconds) {
      return 0f;
    }
    return d * XgramMotion.springVelocity(curve.k, curve.z, curve.v0, t);
  }

  @Override
  public float getCurrVelocity () {
    return Math.abs(currentVelocityPx());
  }

  @Override
  public void abortAnimation () {
    if (!isFinished()) {
      lastAbortVelocity = currentVelocityPx();
      lastAbortTime = AnimationUtils.currentAnimationTimeMillis();
    }
    super.abortAnimation();
  }

  @Override
  public void setFinalX (int newX) {
    if (isFinished()) {
      super.setFinalX(newX);
      return;
    }
    // Resized while settling: continue from here to the new end with the current velocity.
    computeScrollOffset();
    float velocity = currentVelocityPx();
    int x = getCurrX(), y = getCurrY();
    start(x, y, newX - x, getFinalY() - y, velocity);
  }
}
