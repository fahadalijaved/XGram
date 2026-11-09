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
package com.fahadali.xgram;

import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Every haptic of the app goes through here (Telegram X's UI.forceVibrate / hapticVibrate / forceVibrateError too), so
 * Appearance › Haptic feedback turns them all off. vkryl's ClickHelper (a submodule, not changed) vibrates its views
 * directly: those views override performHapticFeedback with {@link #enabled} and {@link #viewFlags}. Never with
 * FLAG_IGNORE_GLOBAL_SETTING: the phone's own Touch feedback setting applies as well (Android 13+ ignores that flag for
 * apps anyway). Each kind falls back to an older constant where the newer one doesn't exist.
 *
 * <p>Ticks (steps, slots, rows under a finger) are spaced at least {@link #TICK_INTERVAL_MS} apart, so a fast drag
 * doesn't turn into one long buzz.
 */
public final class XgramHaptics {
  private XgramHaptics () { }

  public static final long TICK_INTERVAL_MS = 40;
  private static long lastTickTime;

  /** Appearance › Haptic feedback (on by default). */
  public static boolean enabled () {
    return XgramSettings.instance().hapticFeedback();
  }

  /**
   * The flags a view's performHapticFeedback(int, int) override passes on: without FLAG_IGNORE_GLOBAL_SETTING (vkryl's
   * ViewUtils.hapticVibrate adds it), so the phone's Touch feedback setting applies on Android 12 and older too.
   */
  @SuppressWarnings("deprecation")
  public static int viewFlags (int flags) {
    return flags & ~HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
  }

  /** [constant] on [view] if haptics are on; true if the view took it. */
  public static boolean perform (@Nullable View view, int constant) {
    if (view == null || !enabled()) {
      return false;
    }
    try {
      return view.performHapticFeedback(constant);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean performTick (@Nullable View view, int constant) {
    if (view == null || !enabled()) {
      return false;
    }
    long now = SystemClock.uptimeMillis();
    if (lastTickTime != 0 && now - lastTickTime < TICK_INTERVAL_MS) {
      return false;
    }
    lastTickTime = now;
    try {
      return view.performHapticFeedback(constant);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static int clockTick () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.KEYBOARD_TAP;
  }

  /** A long press took: a menu, a selection, a preview. */
  public static boolean longPress (@Nullable View view) {
    return perform(view, HapticFeedbackConstants.LONG_PRESS);
  }

  /** A light tap: a key, a button that acts at once. */
  public static boolean tap (@Nullable View view) {
    return perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
  }

  /** A swipe or pull reached the point where letting go acts (Telegram's KEYBOARD_TAP: swipe to reply, record lock). */
  public static boolean threshold (@Nullable View view) {
    return perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
  }

  /**
   * A pull armed ([armed]) or let go of its threshold: Android 14's threshold pair; before, a tap on arming only.
   */
  public static boolean gestureThreshold (@Nullable View view, boolean armed) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return perform(view, armed ? HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE : HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE);
    }
    return armed && perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
  }

  // A pull (the archive's, the next channel's): a tick at each step that grows as the pull nears its threshold, then
  // a firm click there. Android 13+ plays them as touch vibrations (the phone's touch feedback intensity and switch
  // apply): the composition primitives where the phone has them (a low tick turning into a tick), else a short buzz
  // whose strength grows; older Androids get the view's constants.

  /** The steps a pull's way to its threshold is cut into (a tick at each). */
  public static final int PULL_STEPS = 8;

  @Nullable private static Boolean primitivesSupported;

  @Nullable
  private static android.os.Vibrator vibrator (android.content.Context context) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.os.VibratorManager manager = (android.os.VibratorManager) context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
        return manager != null ? manager.getDefaultVibrator() : null;
      }
      return (android.os.Vibrator) context.getSystemService(android.content.Context.VIBRATOR_SERVICE);
    } catch (Throwable t) {
      return null;
    }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private static boolean hasPrimitives (android.os.Vibrator vibrator) {
    Boolean supported = primitivesSupported;
    if (supported == null) {
      supported = vibrator.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK, android.os.VibrationEffect.Composition.PRIMITIVE_TICK, android.os.VibrationEffect.Composition.PRIMITIVE_CLICK);
      primitivesSupported = supported;
    }
    return supported;
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private static boolean vibrateTouch (View view, @Nullable android.os.VibrationEffect effect) {
    final android.os.Vibrator vibrator = effect != null ? vibrator(view.getContext()) : null;
    if (vibrator == null || !vibrator.hasVibrator()) {
      return false;
    }
    try {
      vibrator.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_TOUCH));
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  /** A pull crossed a step at {@code progress} (0..1 of its way to the threshold): a tick as strong as it is far. */
  public static boolean pullStep (@Nullable View view, float progress) {
    if (view == null || !enabled()) {
      return false;
    }
    final long now = SystemClock.uptimeMillis();
    if (lastTickTime != 0 && now - lastTickTime < TICK_INTERVAL_MS) {
      return false;
    }
    lastTickTime = now;
    final float p = Math.max(0f, Math.min(1f, progress));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      final android.os.Vibrator vibrator = vibrator(view.getContext());
      if (vibrator != null && vibrator.hasVibrator()) {
        android.os.VibrationEffect effect = null;
        if (hasPrimitives(vibrator)) {
          effect = android.os.VibrationEffect.startComposition()
            .addPrimitive(p < .5f ? android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK : android.os.VibrationEffect.Composition.PRIMITIVE_TICK, .25f + .75f * p)
            .compose();
        } else if (vibrator.hasAmplitudeControl()) {
          effect = android.os.VibrationEffect.createOneShot(10, Math.round(30 + 170 * p));
        }
        if (vibrateTouch(view, effect)) {
          return true;
        }
      }
    }
    try {
      return view.performHapticFeedback(clockTick());
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** A pull reached its threshold: letting go acts now. A firm click. */
  public static boolean pullArmed (@Nullable View view) {
    if (view == null || !enabled()) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      final android.os.Vibrator vibrator = vibrator(view.getContext());
      if (vibrator != null && vibrator.hasVibrator()) {
        final android.os.VibrationEffect effect = hasPrimitives(vibrator)
          ? android.os.VibrationEffect.startComposition().addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, 1f).compose()
          : vibrator.hasAmplitudeControl() ? android.os.VibrationEffect.createOneShot(22, 255) : android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK);
        if (vibrateTouch(view, effect)) {
          lastTickTime = SystemClock.uptimeMillis();
          return true;
        }
      }
    }
    return gestureThreshold(view, true);
  }

  /** A clock tick: a row or value under the finger changed. */
  public static boolean tick (@Nullable View view) {
    return performTick(view, clockTick());
  }

  /** A step of a stepped control (slider stop, reorder slot, segment). */
  public static boolean segmentTick (@Nullable View view) {
    return performTick(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? HapticFeedbackConstants.SEGMENT_TICK : clockTick());
  }

  /** A fine step that comes often (a scrubber, a fast scroll). */
  public static boolean frequentTick (@Nullable View view) {
    return performTick(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : clockTick());
  }

  /** A text handle or a chart's selected point moved by one place (Telegram's vibrateCursor). */
  public static boolean textHandle (@Nullable View view) {
    return performTick(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ? HapticFeedbackConstants.TEXT_HANDLE_MOVE : clockTick());
  }

  /** Something succeeded: sent, saved, correct. */
  public static boolean confirm (@Nullable View view) {
    return perform(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
  }

  /** Something was refused: a wrong code, a limit, an action that can't be done (Telegram X's forceVibrateError). */
  public static boolean reject (@Nullable View view) {
    return perform(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
  }

  /** A switch was flipped to [on] (Pixel apps' toggle haptic on Android 14+, a tick before). */
  public static boolean toggle (@Nullable View view, boolean on) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return perform(view, on ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF);
    }
    return perform(view, clockTick());
  }

  /** A drag (reorder, drag and drop) picked its item up. */
  public static boolean dragStart (@Nullable View view) {
    return perform(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? HapticFeedbackConstants.DRAG_START : HapticFeedbackConstants.LONG_PRESS);
  }

  /** A secondary click: a context action, a lens or a mode picked. */
  public static boolean contextClick (@Nullable View view) {
    return perform(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.KEYBOARD_TAP);
  }

  /** A virtual key (a navigation button). */
  public static boolean virtualKey (@Nullable View view) {
    return perform(view, HapticFeedbackConstants.VIRTUAL_KEY);
  }
}
