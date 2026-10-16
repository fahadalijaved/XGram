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

import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.attach.CustomItemAnimator;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

/**
 * App-wide Material 3 Expressive motion outside settings: cached spring interpolators for BoolAnimator /
 * FactorAnimator sites, a drop-in BounceAnimator on springs, spring item animators for lists and entrance motion.
 * Everything returns Telegram X's own values with Expressive design off.
 */
public final class XgramAppMotion {
  private XgramAppMotion () { }

  // Cached spring interpolators (by the token after the Reduce motion mapping), so a site that picks a curve per
  // animation allocates nothing.

  private static final int TOKEN_COUNT = 9;
  private static final Interpolator[] SPRINGS = new Interpolator[TOKEN_COUNT];
  private static final Interpolator[] CLAMPED = new Interpolator[TOKEN_COUNT];
  private static final long[] DURATIONS = new long[TOKEN_COUNT];

  /** The spring of {@code token} as an interpolator (overshooting springs go above 1). Pair with {@link #springDuration}. */
  public static Interpolator spring (int token) {
    int t = XgramMotion.token(token);
    Interpolator i = SPRINGS[t];
    if (i == null) {
      i = XgramMotion.interpolator(t);
      SPRINGS[t] = i;
    }
    return i;
  }

  /** {@link #spring} clamped to [0, 1], for alpha, colors and anything that feeds layout. */
  public static Interpolator springClamped (int token) {
    int t = XgramMotion.token(token);
    Interpolator i = CLAMPED[t];
    if (i == null) {
      final Interpolator s = spring(t);
      i = f -> XgramMotion.clamp01(s.getInterpolation(f));
      CLAMPED[t] = i;
    }
    return i;
  }

  public static long springDuration (int token) {
    int t = XgramMotion.token(token);
    long d = DURATIONS[t];
    if (d == 0) {
      d = XgramMotion.duration(t);
      DURATIONS[t] = d;
    }
    return d;
  }

  public static Interpolator interp (int token, Interpolator tgxFallback) {
    return XgramExpressive.isEnabled() ? spring(token) : tgxFallback;
  }

  public static Interpolator interpClamped (int token, Interpolator tgxFallback) {
    return XgramExpressive.isEnabled() ? springClamped(token) : tgxFallback;
  }

  public static long duration (int token, long tgxFallback) {
    return XgramExpressive.isEnabled() ? springDuration(token) : tgxFallback;
  }

  /** Puts {@code animator} on {@code token} (clamped or not) before an animateTo; nothing with Expressive design off. */
  public static void use (@Nullable FactorAnimator animator, int token, boolean clamped) {
    if (animator == null || !XgramExpressive.isEnabled()) {
      return;
    }
    animator.setInterpolator(clamped ? springClamped(token) : spring(token));
    animator.setDuration(springDuration(token));
  }

  /** {@link #use(FactorAnimator, int, boolean)} for a BoolAnimator. */
  public static void use (@Nullable BoolAnimator animator, int token, boolean clamped) {
    if (animator == null || !XgramExpressive.isEnabled()) {
      return;
    }
    animator.setInterpolator(clamped ? springClamped(token) : spring(token));
    animator.setDuration(springDuration(token));
  }

  /**
   * A shown / hidden flag that pops in: vkryl's BounceAnimator (OVERSHOOT 210 ms in, DECELERATE 100 ms out) with
   * Expressive design off; SPATIAL_FAST in (about 9.5% overshoot) and EFFECTS_FAST out with it on.
   * Draw the scale from the raw {@link #getFloatValue()}, and clamp it for alpha and widths.
   */
  public static final class Bounce {
    private final BoolAnimator animator;

    public Bounce (FactorAnimator.Target target) {
      this(0, target);
    }

    public Bounce (int id, FactorAnimator.Target target) {
      this.animator = new BoolAnimator(id, target, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210L);
    }

    public void setValue (boolean value, boolean animated) {
      if (animated) {
        if (XgramExpressive.isEnabled()) {
          int token = value ? XgramExpressive.SPATIAL_FAST : XgramExpressive.EFFECTS_FAST;
          animator.setInterpolator(value ? spring(token) : springClamped(token));
          animator.setDuration(springDuration(token));
        } else if (value && animator.getFloatValue() == 0f) {
          animator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          animator.setDuration(210L);
        } else {
          animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          animator.setDuration(100L);
        }
      }
      animator.setValue(value, animated);
    }

    public float getFloatValue () {
      return animator.getFloatValue();
    }

    public boolean getValue () {
      return animator.getValue();
    }
  }

  // Lists

  /**
   * A spring item animator (XgramMotion.ItemSprings: removals fade, moves and the closing gap spring, additions fade
   * and scale in) whose plain changes redraw in place. Null with Expressive design off, as Telegram X runs these lists.
   */
  @Nullable
  public static RecyclerView.ItemAnimator listAnimator () {
    if (!XgramExpressive.isEnabled()) {
      return null;
    }
    CustomItemAnimator animator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    animator.setSupportsChangeAnimations(false);
    return animator;
  }

  // Entrances

  /**
   * Pops {@code view} in after {@code delayMs}: scale from {@code fromScale} on SPATIAL_DEFAULT, or a rise of
   * {@code risePx} on SPATIAL_FAST, with the alpha on the same curve (RenderNode clamps it). RenderNode properties
   * only. Reduce motion: alpha only.
   */
  public static void popIn (@Nullable View view, float fromScale, float risePx, long delayMs) {
    if (view == null || !XgramExpressive.isEnabled()) {
      return;
    }
    view.animate().cancel();
    boolean reduce = XgramMotion.reduce();
    view.setAlpha(0f);
    if (!reduce && fromScale != 1f) {
      view.setScaleX(fromScale);
      view.setScaleY(fromScale);
    }
    if (!reduce && risePx != 0f) {
      view.setTranslationY(risePx);
    }
    final int spatial = risePx != 0f && fromScale == 1f ? XgramExpressive.SPATIAL_FAST : XgramExpressive.SPATIAL_DEFAULT;
    view.animate()
      .alpha(1f)
      .scaleX(1f)
      .scaleY(1f)
      .translationY(0f)
      .setStartDelay(delayMs)
      .setDuration(springDuration(spatial))
      .setInterpolator(spring(spatial))
      .setUpdateListener(null)
      .withLayer()
      .start();
  }
}
