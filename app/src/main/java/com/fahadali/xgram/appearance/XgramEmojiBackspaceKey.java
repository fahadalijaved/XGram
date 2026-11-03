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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.animator.FactorAnimator;

/**
 * XGram M3E: the emoji panel's backspace key, in the end slot of the emoji category row (EmojiHeaderView), with
 * Expressive design. Telegram X's floating backspace circle sat over the emoji grid at the bottom end, so it took the
 * taps (a tap on an emoji under it deleted a character) and the scrolls that started on it. The key is a standard
 * icon button in the row instead: the 24dp backspace icon in onSurfaceVariant (mirrored in RTL) that presses in the
 * category tabs' 40x32dp pill, shown while the message box has text. It pops in and out (XgramAppMotion.Bounce).
 *
 * A tap deletes a character on release, as Telegram X's circle did (a touch the system takes back, such as a back
 * swipe from the screen edge, or a drag that slides off the key, deletes nothing). Holding it deletes and repeats, first
 * after the system's long-press timeout ("Touch & hold delay", 400ms by default, with the long-press haptic), then
 * faster and faster, from 100ms down to 35ms a character. Lifting the finger, sliding off the key, a cancel, the key or
 * the panel hiding, or the text running out stops it. Accessibility services, a hardware keyboard or a D-pad click it:
 * one character.
 */
@SuppressLint("ViewConstructor")
public final class XgramEmojiBackspaceKey extends View implements FactorAnimator.Target {
  public interface Callback {
    /** Deletes one character before the cursor. */
    void onBackspace ();

    /** The key's shown factor (0 hidden, 1 shown, clamped) changed: its row moves its tabs and its fill with it. */
    void onBackspaceShownFactorChanged (float factor);
  }

  /** The width of the row's end slot the key takes. */
  public static final float SLOT_DP = 48f;

  /** Telegram X's optical centering of the backspace icon (its pointed side is lighter), as on its circle. */
  private static final float ICON_OFFSET_DP = 1.5f;
  private static final float MIN_SCALE = .6f;

  private static final long REPEAT_FIRST_INTERVAL_MS = 100L, REPEAT_MIN_INTERVAL_MS = 35L;
  private static final float REPEAT_SPEEDUP = .85f;

  private final Callback callback;
  private final Drawable icon;
  private final XgramAppMotion.Bounce shown;
  private boolean wanted, mirrored;

  private @Nullable Paint iconPaint;
  private int iconPaintColor;

  private boolean touching, repeating;
  private long repeatInterval;
  private final Runnable repeatStep = this::onRepeatStep;

  public XgramEmojiBackspaceKey (@NonNull Context context, @NonNull Callback callback) {
    super(context);
    this.callback = callback;
    this.icon = Drawables.get(context.getResources(), R.drawable.baseline_backspace_24);
    this.shown = new XgramAppMotion.Bounce(this);
    if (XgramExpressive.isEnabled()) {
      // The category tabs' press, so the row's targets press alike: a 40x32dp full pill, centered (inside the panel's
      // 28dp corner at the row's end)
      XgramPillStateLayer.attach(this, (view, out) -> {
        final float cx = view.getWidth() / 2f, cy = view.getHeight() / 2f;
        out.set(cx - Screen.dp(20f), cy - Screen.dp(16f), cx + Screen.dp(20f), cy + Screen.dp(16f));
        return XgramPillStateLayer.CORNER_FULL;
      });
    }
    setContentDescription(Lang.getString(R.string.Delete));
    // Accessibility services, a hardware keyboard or a D-pad: one character. Touches go through onTouchEvent
    setOnClickListener(v -> callback.onBackspace());
    setVisibility(INVISIBLE);
    applyShownFactor(false);
  }

  /** Shows the key (the message box has text) or hides it. */
  public void setShown (boolean shown, boolean animated) {
    if (this.wanted == shown) {
      return;
    }
    this.wanted = shown;
    if (shown) {
      setVisibility(VISIBLE);
    } else {
      stopRepeat(); // the text ran out
    }
    this.shown.setValue(shown, animated);
  }

  public boolean isWanted () {
    return wanted;
  }

  /** The app's language changed: TalkBack's label follows it. */
  public void onLanguageChanged () {
    setContentDescription(Lang.getString(R.string.Delete));
  }

  /** 0 hidden, 1 shown (clamped: the pop-in overshoots only the scale). */
  public float getShownFactor () {
    return XgramMotion.clamp01(shown.getFloatValue());
  }

  /** RTL: the icon points the other way (Material's backspace is a mirrored icon). */
  public void setMirrored (boolean mirrored) {
    if (this.mirrored != mirrored) {
      this.mirrored = mirrored;
      invalidate();
    }
  }

  private void applyShownFactor (boolean notify) {
    final float raw = shown.getFloatValue();
    final float factor = XgramMotion.clamp01(raw);
    final float scale = MIN_SCALE + (1f - MIN_SCALE) * raw;
    setAlpha(factor);
    setScaleX(scale);
    setScaleY(scale);
    if (notify) {
      callback.onBackspaceShownFactorChanged(factor);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    applyShownFactor(true);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (!wanted && finalFactor == 0f) {
      setVisibility(INVISIBLE); // out of TalkBack's reach too
    }
    applyShownFactor(true);
  }

  // Touches: a keyboard key

  /** Hidden, fading out, or with its row hidden (the tabs slid away on scroll): the touch goes elsewhere. */
  private boolean isUsable () {
    if (!wanted || !isEnabled() || getVisibility() != VISIBLE || getAlpha() == 0f) {
      return false;
    }
    final ViewParent parent = getParent();
    return !(parent instanceof View) || ((View) parent).getAlpha() != 0f;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!isUsable()) {
          return false;
        }
        touching = true;
        final ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(true); // a held key keeps its finger
        }
        drawableHotspotChanged(e.getX(), e.getY());
        setPressed(true);
        postDelayed(repeatStep, ViewConfiguration.getLongPressTimeout());
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touching) {
          drawableHotspotChanged(e.getX(), e.getY());
          final float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
          final float x = e.getX(), y = e.getY();
          if (x < -slop || y < -slop || x > getWidth() + slop || y > getHeight() + slop) {
            endTouch(); // slid off the key: no more repeats
          }
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (touching && !repeating && wanted) {
          // A tap: one character on release (the circle's click sound and click event)
          playSoundEffect(SoundEffectConstants.CLICK);
          sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
          callback.onBackspace();
        }
        endTouch();
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        endTouch(); // taken back (a back swipe from the edge, a parent): nothing deleted
        return true;
      }
    }
    return true;
  }

  private void onRepeatStep () {
    if (!touching || !wanted) {
      stopRepeat();
      return;
    }
    if (!repeating) {
      repeating = true;
      repeatInterval = REPEAT_FIRST_INTERVAL_MS;
      com.fahadali.xgram.XgramHaptics.longPress(this); // the hold took: it deletes and repeats from here
      playSoundEffect(SoundEffectConstants.CLICK);
    } else {
      repeatInterval = Math.max(REPEAT_MIN_INTERVAL_MS, (long) (repeatInterval * REPEAT_SPEEDUP));
    }
    callback.onBackspace();
    if (touching && wanted) {
      postDelayed(repeatStep, repeatInterval);
    }
  }

  private void stopRepeat () {
    repeating = false;
    removeCallbacks(repeatStep);
  }

  private void endTouch () {
    touching = false;
    stopRepeat();
    setPressed(false);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    endTouch();
  }

  // The key or the panel hid while a finger holds it (a second finger opened the keyboard): no unseen deleting

  @Override
  protected void onVisibilityChanged (@NonNull View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (visibility != VISIBLE) {
      endTouch();
    }
  }

  @Override
  protected void onWindowVisibilityChanged (int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility != VISIBLE) {
      endTouch();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    if (iconPaint == null || iconPaintColor != color) {
      iconPaint = Paints.createPorterDuffPaint(iconPaint, iconPaintColor = color);
    }
    final float cx = getWidth() / 2f, cy = getHeight() / 2f;
    final int save = c.save();
    if (mirrored) {
      c.scale(-1f, 1f, cx, cy);
    }
    Drawables.drawCentered(c, icon, cx - Screen.dp(ICON_OFFSET_DP), cy, iconPaint);
    c.restoreToCount(save);
  }
}
