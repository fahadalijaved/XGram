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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;

import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

/**
 * The rows' swap on the Activity page when what it shows changes (a filter chip, Unread, the chats), with Expressive
 * design: the rows below the chips fade out quickly (EFFECTS_FAST); once the new rows are ready they are put in while
 * nothing shows, then fade in (EFFECTS_FAST) from a little lower (SPATIAL_FAST). One motion, never the old and new
 * rows together. The chips and the status tiles take no part ({@link Host#isSwappedChild}). Reduce motion: a plain
 * crossfade (out, then in), no offset. The list's item animator is off for the whole swap and comes back at its end,
 * so nothing else fades, moves or crossfades meanwhile. UI thread; nothing is allocated per frame.
 */
@UiThread
final class XgramActivitySwap implements RecyclerView.OnChildAttachStateChangeListener {
  interface Host {
    /** A child of the list that swaps: the rows, their headers, separators and states (not the chips or the tiles). */
    boolean isSwappedChild (@NonNull View child);
    /** The old rows are gone and the new ones are ready: put them in now (at the top, no item animations). */
    void onSwapPoint ();
    /** The swap is over (or was cut short): what waited for it may change the list now. */
    void onSwapEnd ();
  }

  private static final int IDLE = 0, OUT = 1, WAIT = 2, IN = 3;
  /** How far below its place a new row starts (dp). */
  private static final float IN_OFFSET_DP = 16f;
  /** Reduce motion: the crossfade's two halves (ms). */
  private static final long REDUCED_OUT_MS = 90L, REDUCED_IN_MS = 150L;

  private final RecyclerView list;
  private final Host host;
  private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
  private final ArrayList<View> layered = new ArrayList<>();

  private int state = IDLE;
  private boolean ready, reduced, animatorCancelled;
  /** What the swapped children show now: their alpha and their offset (px). */
  private float alpha = 1f, shift;
  /** The phase's start: the alpha it fades from (OUT), and the offset it keeps (OUT) or starts from (IN). */
  private float fromAlpha = 1f, fromShift;
  private long phaseMs;
  /** The list's own item animator (as the page made it), off while a swap runs. */
  @Nullable private final RecyclerView.ItemAnimator listAnimator;
  private boolean animatorSuspended;

  XgramActivitySwap (@NonNull RecyclerView list, @NonNull Host host) {
    this.list = list;
    this.host = host;
    this.listAnimator = list.getItemAnimator();
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a -> onFrame(a.getAnimatedFraction()));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationCancel (Animator animation) {
        animatorCancelled = true;
      }

      @Override
      public void onAnimationEnd (Animator animation) {
        if (animatorCancelled) {
          animatorCancelled = false;
          return;
        }
        onPhaseEnd();
      }
    });
    list.addOnChildAttachStateChangeListener(this);
  }

  /** A swap runs (fading out, waiting for the new rows, or fading them in). */
  boolean isActive () {
    return state != IDLE;
  }

  /** The old rows are gone and the new ones are not ready yet. */
  boolean isWaiting () {
    return state == WAIT;
  }

  /** The old rows are going or gone: a tap on one is not taken. */
  boolean hidesRows () {
    return state == OUT || state == WAIT;
  }

  /**
   * What the page shows changed: the rows fade out (from where they are, if a swap was running). The new rows are
   * put in at {@link #setReady}, once the old ones are gone.
   */
  void begin () {
    ready = false;
    reduced = XgramMotion.reduce();
    suspendItemAnimator();
    final int before = state;
    cancelAnimator();
    fromAlpha = before == IDLE ? 1f : before == WAIT ? 0f : alpha;
    fromShift = before == IDLE ? 0f : shift;
    state = OUT;
    phaseMs = reduced ? REDUCED_OUT_MS : XgramMotion.duration(XgramExpressive.EFFECTS_FAST);
    if (fromAlpha <= 0f || phaseMs <= 0) {
      alpha = 0f;
      apply();
      onPhaseEnd();
      return;
    }
    layerChildren();
    animator.setDuration(phaseMs);
    animator.start();
  }

  /** The new rows are ready: they go in as soon as the old ones are gone (now, if they are). */
  void setReady () {
    if (state == IDLE) {
      return;
    }
    ready = true;
    if (state == WAIT) {
      swapNow();
    }
  }

  /** Ends the swap at once (the page goes, or its look changes): every row as it rests, the animator back. */
  void cancel (boolean notifyHost) {
    if (state == IDLE) {
      return;
    }
    cancelAnimator();
    state = IDLE;
    ready = false;
    alpha = 1f;
    shift = 0f;
    apply();
    unlayerAll();
    restoreItemAnimator();
    if (notifyHost) {
      host.onSwapEnd();
    }
  }

  // Phases

  private void onPhaseEnd () {
    if (state == OUT) {
      alpha = 0f;
      apply();
      state = WAIT;
      if (ready) {
        swapNow();
      }
    } else if (state == IN) {
      state = IDLE;
      alpha = 1f;
      shift = 0f;
      apply();
      unlayerAll();
      restoreItemAnimator();
      host.onSwapEnd();
    }
  }

  private void swapNow () {
    ready = false;
    state = IN;
    alpha = 0f;
    fromShift = shift = reduced ? 0f : Screen.dp(IN_OFFSET_DP);
    // The new rows are laid out with what the children show now (onChildViewAttachedToWindow)
    host.onSwapPoint();
    if (state != IN) {
      return; // cut short meanwhile
    }
    apply();
    phaseMs = reduced ? REDUCED_IN_MS : Math.max(XgramMotion.duration(XgramExpressive.EFFECTS_FAST), XgramMotion.duration(XgramExpressive.SPATIAL_FAST));
    if (phaseMs <= 0) {
      onPhaseEnd();
      return;
    }
    layerChildren();
    animator.setDuration(phaseMs);
    animator.start();
  }

  private void onFrame (float fraction) {
    if (state == OUT) {
      final float progress = reduced ? fraction : spring(XgramExpressive.EFFECTS_FAST, fraction);
      alpha = fromAlpha * (1f - progress);
      shift = fromShift;
    } else if (state == IN) {
      alpha = reduced ? fraction : spring(XgramExpressive.EFFECTS_FAST, fraction);
      shift = reduced ? 0f : fromShift * (1f - XgramMotion.springValue(XgramExpressive.stiffness(XgramExpressive.SPATIAL_FAST),
        XgramExpressive.damping(XgramExpressive.SPATIAL_FAST), 0f, fraction * phaseMs / 1000f));
    } else {
      return;
    }
    apply();
  }

  /** The spring of {@code token} (from rest, 0 to 1) at {@code fraction} of the phase, clamped to [0, 1]. */
  private float spring (int token, float fraction) {
    if (fraction >= 1f) {
      return 1f;
    }
    return XgramMotion.clamp01(XgramMotion.springValue(XgramExpressive.stiffness(token), XgramExpressive.damping(token), 0f, fraction * phaseMs / 1000f));
  }

  // Children

  private void apply () {
    for (int i = 0; i < list.getChildCount(); i++) {
      final View child = list.getChildAt(i);
      if (child != null && host.isSwappedChild(child)) {
        set(child, alpha, shift);
      }
    }
    // The segmented list draws group tiles under its children with their alpha and offset
    list.invalidate();
  }

  private static void set (@NonNull View child, float alpha, float shift) {
    if (child.getAlpha() != alpha) {
      child.setAlpha(alpha);
    }
    if (child.getTranslationY() != shift) {
      child.setTranslationY(shift);
    }
  }

  @Override
  public void onChildViewAttachedToWindow (@NonNull View view) {
    if (state != IDLE && host.isSwappedChild(view)) {
      set(view, alpha, shift);
      if (animator.isRunning()) {
        layer(view);
      }
    }
  }

  @Override
  public void onChildViewDetachedFromWindow (@NonNull View view) {
    if (state != IDLE && host.isSwappedChild(view)) {
      // Off the list (scrolled away, or a row of the old rows): back as it rests, for whoever binds it next
      set(view, 1f, 0f);
      unlayer(view);
    }
  }

  /** A fading row gets a hardware layer while it fades (a texture blend, not an offscreen pass of the row each frame). */
  private void layerChildren () {
    for (int i = 0; i < list.getChildCount(); i++) {
      final View child = list.getChildAt(i);
      if (child != null && host.isSwappedChild(child)) {
        layer(child);
      }
    }
  }

  private void layer (@NonNull View view) {
    if (view.getLayerType() != View.LAYER_TYPE_NONE || !view.isHardwareAccelerated() || view.getWidth() <= 0 || view.getHeight() <= 0) {
      return;
    }
    if ((long) view.getWidth() * view.getHeight() > 2L * list.getWidth() * list.getHeight()) {
      return; // a row taller than the list (the empty state): not worth a layer
    }
    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    layered.add(view);
  }

  private void unlayer (@NonNull View view) {
    if (layered.remove(view)) {
      view.setLayerType(View.LAYER_TYPE_NONE, null);
    }
  }

  private void unlayerAll () {
    for (int i = layered.size() - 1; i >= 0; i--) {
      layered.get(i).setLayerType(View.LAYER_TYPE_NONE, null);
    }
    layered.clear();
  }

  private void cancelAnimator () {
    if (animator.isRunning() || animator.isStarted()) {
      animator.cancel();
    }
    animatorCancelled = false;
  }

  // The list's item animator

  private void suspendItemAnimator () {
    if (!animatorSuspended) {
      animatorSuspended = true;
      if (list.getItemAnimator() != null) {
        list.setItemAnimator(null); // ends what it runs: no row is left half faded or moved
      }
    }
  }

  private void restoreItemAnimator () {
    if (animatorSuspended) {
      animatorSuspended = false;
      if (listAnimator != null && list.getItemAnimator() == null) {
        list.setItemAnimator(listAnimator);
      }
    }
  }
}
