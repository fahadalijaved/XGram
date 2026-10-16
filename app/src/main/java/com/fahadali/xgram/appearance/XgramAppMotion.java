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
 * FactorAnimator sites, a drop-in BounceAnimator on springs, spring item animators for lists, the icon button press
 * morph and dialog window motion.
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

  // Icon buttons

  /**
   * The M3 standard icon button press morph (a 40dp state layer whose full corners spring to 8dp on press) as the
   * background of {@code v}, in onSurfaceVariant. The color is checked on every press, so theme switches need no
   * listener. Nothing with Expressive design off.
   */
  public static void iconButton (@Nullable View v) {
    if (v == null || !XgramExpressive.isEnabled()) {
      return;
    }
    v.setBackground(new ThemedIconRipple(XgramExpressive.ON_SURFACE_VARIANT));
  }

  private static final class ThemedIconRipple extends android.graphics.drawable.RippleDrawable {
    private final int role;
    private int color;

    ThemedIconRipple (int role) {
      this(role, XgramExpressive.color(role));
    }

    private ThemedIconRipple (int role, int color) {
      super(android.content.res.ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(color)), null,
        new XgramExpressiveC.PressMorphMask(XgramExpressiveC.ICON_BUTTON_BOX, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S));
      this.role = role;
      this.color = color;
    }

    @Override
    protected boolean onStateChange (int[] stateSet) {
      int now = XgramExpressive.color(role);
      if (now != color) {
        color = now;
        setColor(android.content.res.ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(now)));
      }
      return super.onStateChange(stateSet);
    }
  }

  // Dialogs

  /**
   * M3 dialog window motion: scale .85 to 1 with a quick fade on emphasized decelerate (350 ms) in, fade and scale
   * to .95 on emphasized accelerate (150 ms) out. Window animations cannot use springs. Reduce motion: fades only.
   */
  public static void dialogWindow (@Nullable android.app.Dialog dialog) {
    if (dialog == null || !XgramExpressive.isEnabled()) {
      return;
    }
    android.view.Window window = dialog.getWindow();
    if (window != null) {
      window.setWindowAnimations(XgramMotion.reduce() ? org.thunderdog.challegram.R.style.XgramDialogFade : org.thunderdog.challegram.R.style.XgramDialogAnim);
    }
  }
}
