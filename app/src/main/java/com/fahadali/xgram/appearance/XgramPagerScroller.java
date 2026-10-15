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
import android.widget.Scroller;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * The settle of every ViewPager page swipe on Compose's pager spring (SPATIAL_DEFAULT: 380, .8) with the finger's
 * velocity, instead of androidx ViewPager's quintic scroller. At the first and last page the spring is critically
 * damped and clamped, so it never scrolls past the adapter's ends. Installed into ViewPager.mScroller by reflection.
 */
public class XgramPagerScroller extends Scroller {
  private static final float K = 380f, Z = .8f;
  private static final long PENDING_VELOCITY_TIMEOUT = 50, ABORT_VELOCITY_TIMEOUT = 32;
  private static final float MAX_V0 = 60f;

  private final @Nullable ViewPager pager;

  private boolean finished = true;
  private int startX, startY, finalX, finalY, currX, currY;
  private long startTime;
  private float k = K, z = Z, v0;
  private float settleSeconds;
  private boolean clampToFinal;

  private float pendingVelocity;
  private long pendingVelocityTime;
  private float lastAbortVelocity;
  private long lastAbortTime;

  public XgramPagerScroller (Context context, @Nullable ViewPager pager) {
    super(context);
    this.pager = pager;
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
  public void startScroll (int startX, int startY, int dx, int dy) {
    startScroll(startX, startY, dx, dy, 0);
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
    start(startX, startY, dx, dy, velocity, now);
  }

  private void start (int startX, int startY, int dx, int dy, float velocityPx, long now) {
    this.startX = startX;
    this.startY = startY;
    this.finalX = startX + dx;
    this.finalY = startY + dy;
    this.currX = startX;
    this.currY = startY;
    this.startTime = now;
    int d = Math.abs(dx) >= Math.abs(dy) ? dx : dy;
    if (d == 0) {
      finished = true;
      currX = finalX;
      currY = finalY;
      return;
    }
    boolean edge = dx != 0 && isEdge(finalX);
    this.clampToFinal = edge;
    this.k = K;
    this.z = edge || XgramMotion.reduce() ? 1f : Z;
    this.v0 = Math.max(-MAX_V0, Math.min(MAX_V0, velocityPx / d));
    float scale = durationScale();
    this.settleSeconds = scale == 0f ? 0f : XgramMotion.settleSeconds(k, z, v0, .5f / Math.abs(d)) * scale;
    this.finished = false;
  }

  private float elapsedSeconds () {
    float scale = durationScale();
    float t = (AnimationUtils.currentAnimationTimeMillis() - startTime) / 1000f;
    return scale > 0f ? t / scale : Float.MAX_VALUE;
  }

  private int position (int from, int to, float progress) {
    int d = to - from;
    if (d == 0) {
      return to;
    }
    if (clampToFinal) {
      progress = Math.min(progress, 1f);
    }
    return from + Math.round(d * progress);
  }

  @Override
  public boolean computeScrollOffset () {
    if (finished) {
      return false;
    }
    float t = elapsedSeconds();
    if (t * Math.max(durationScale(), 1e-6f) >= settleSeconds) {
      currX = finalX;
      currY = finalY;
      finished = true;
      return true;
    }
    float p = XgramMotion.springValue(k, z, v0, t);
    currX = position(startX, finalX, p);
    if (currX < 0 && startX >= 0 && finalX >= 0) {
      currX = 0; // never before the first page
    }
    currY = position(startY, finalY, p);
    return true;
  }

  /** px per second along the larger axis. */
  private float currentVelocityPx () {
    if (finished) {
      return 0f;
    }
    int d = Math.abs(finalX - startX) >= Math.abs(finalY - startY) ? finalX - startX : finalY - startY;
    return d * XgramMotion.springVelocity(k, z, v0, elapsedSeconds());
  }

  @Override
  public float getCurrVelocity () {
    return Math.abs(currentVelocityPx());
  }

  @Override
  public void abortAnimation () {
    if (!finished) {
      lastAbortVelocity = currentVelocityPx();
      lastAbortTime = AnimationUtils.currentAnimationTimeMillis();
    }
    currX = finalX;
    currY = finalY;
    finished = true;
  }

  @Override
  public void forceFinished (boolean finished) {
    this.finished = finished;
  }

  @Override
  public boolean isFinished () {
    return finished;
  }

  @Override
  public int getCurrX () {
    return currX;
  }

  @Override
  public int getCurrY () {
    return currY;
  }

  @Override
  public int getStartX () {
    return startX;
  }

  @Override
  public int getStartY () {
    return startY;
  }

  @Override
  public int getFinalX () {
    return finalX;
  }

  @Override
  public int getFinalY () {
    return finalY;
  }

  @Override
  public int getDuration () {
    return Math.round(settleSeconds * 1000f);
  }

  @Override
  public int timePassed () {
    return (int) (AnimationUtils.currentAnimationTimeMillis() - startTime);
  }

  @Override
  public void setFinalX (int newX) {
    if (finished) {
      finalX = newX;
      currX = newX;
      return;
    }
    // Resized while settling: continue from here to the new end with the current velocity.
    computeScrollOffset();
    float velocity = currentVelocityPx();
    int fromX = currX, fromY = currY;
    start(fromX, fromY, newX - fromX, finalY - fromY, velocity, AnimationUtils.currentAnimationTimeMillis());
  }

  @Override
  public void setFinalY (int newY) {
    finalY = newY;
    if (finished) {
      currY = newY;
    }
  }
}
