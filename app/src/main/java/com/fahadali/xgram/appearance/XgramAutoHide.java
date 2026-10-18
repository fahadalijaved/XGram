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

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Hide controls while playing (Messages > Videos): the timer that hides a video player's chrome (the media viewer's
 * title bar, caption and controls, the in-viewer picture in picture buttons) after {@link #DELAY_MS} without
 * interaction, like Telegram.
 *
 * Every event that should keep the chrome up a little longer calls {@link #restart()}; the host only says, when the
 * timer fires, whether hiding is allowed at all ({@link Host#canAutoHide}) and whether something holds the chrome up
 * for now ({@link Host#isAutoHideHeld}: buffering, scrubbing, a menu). A hold is polled every {@link #HOLD_POLL_MS}
 * and, once it ends, a fresh full delay starts. A finger down anywhere stops the timer until it is lifted. With
 * TalkBack on nothing ever hides, and turning TalkBack on brings the chrome back.
 *
 * The delay is the user's "Time to take action" accessibility setting when it is longer (API 29+). Main thread only.
 */
public final class XgramAutoHide {
  public static final long DELAY_MS = 3000L;
  private static final long HOLD_POLL_MS = 250L;

  public interface Host {
    /** The chrome is shown and a video plays: hiding is allowed. Asked only when the timer fires. */
    boolean canAutoHide ();
    /** Something keeps the chrome up for now (buffering, scrubbing, a menu): asked again shortly. */
    boolean isAutoHideHeld ();
    /** Hide the chrome. */
    void onAutoHide ();
    /** TalkBack was turned on: show the chrome. */
    void onTouchExplorationEnabled ();
  }

  private final Host host;
  private final @Nullable AccessibilityManager accessibility;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable fire = this::fire;
  private boolean pending, touching, held;
  private @Nullable Object touchExplorationListener; // AccessibilityManager.TouchExplorationStateChangeListener (API 19)

  public XgramAutoHide (@NonNull Context context, @NonNull Host host) {
    this.host = host;
    this.accessibility = manager(context);
  }

  /** Starts following TalkBack. Call {@link #detach()} when the player goes away. */
  public void attach () {
    if (touchExplorationListener != null || accessibility == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      return;
    }
    AccessibilityManager.TouchExplorationStateChangeListener listener = enabled -> {
      if (Looper.myLooper() == Looper.getMainLooper()) {
        onTouchExplorationChanged(enabled);
      } else {
        handler.post(() -> onTouchExplorationChanged(enabled));
      }
    };
    touchExplorationListener = listener;
    accessibility.addTouchExplorationStateChangeListener(listener);
  }

  /** Stops the timer and stops following TalkBack. */
  public void detach () {
    cancel();
    touching = false;
    if (touchExplorationListener != null && accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      accessibility.removeTouchExplorationStateChangeListener((AccessibilityManager.TouchExplorationStateChangeListener) touchExplorationListener);
    }
    touchExplorationListener = null;
  }

  private void onTouchExplorationChanged (boolean enabled) {
    if (touchExplorationListener == null) {
      return; // detached meanwhile
    }
    if (enabled) {
      cancel();
      host.onTouchExplorationEnabled();
    } else {
      restart();
    }
  }

  /** A full delay from now (nothing while a finger is down or with TalkBack on). */
  public void restart () {
    cancel();
    if (!touching && !touchExploration(accessibility)) {
      pending = true;
      handler.postDelayed(fire, delayMs(accessibility));
    }
  }

  public void cancel () {
    held = false;
    if (pending) {
      pending = false;
      handler.removeCallbacks(fire);
    }
  }

  /** Every touch on the player: a finger down stops the timer, lifting it starts a full delay. */
  public void onTouch (@NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        touching = true;
        cancel();
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        onTouchEnd();
        break;
    }
  }

  /** The finger was lifted somewhere {@link #onTouch} didn't see (a gesture taken over by a parent). */
  public void onTouchEnd () {
    touching = false;
    restart();
  }

  private void fire () {
    pending = false;
    if (touching || !host.canAutoHide() || touchExploration(accessibility)) {
      held = false;
      return;
    }
    if (host.isAutoHideHeld()) {
      held = true;
      pending = true;
      handler.postDelayed(fire, HOLD_POLL_MS);
      return;
    }
    if (held) {
      restart(); // a hold just ended: a full delay from now
      return;
    }
    host.onAutoHide();
  }

  // Accessibility

  private static @Nullable AccessibilityManager manager (@Nullable Context context) {
    if (context == null) {
      return null;
    }
    try {
      return (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** TalkBack (or another touch exploration service) is on: the chrome never hides. */
  public static boolean touchExploration (@Nullable Context context) {
    return touchExploration(manager(context));
  }

  public static boolean touchExploration (@Nullable AccessibilityManager manager) {
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }

  /** {@link #DELAY_MS}, or longer with the "Time to take action" accessibility setting (API 29+). */
  public static long delayMs (@Nullable Context context) {
    return delayMs(manager(context));
  }

  public static long delayMs (@Nullable AccessibilityManager manager) {
    if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        return Math.max(DELAY_MS, manager.getRecommendedTimeoutMillis((int) DELAY_MS,
          AccessibilityManager.FLAG_CONTENT_ICONS | AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS));
      } catch (Throwable ignored) { }
    }
    return DELAY_MS;
  }
}
