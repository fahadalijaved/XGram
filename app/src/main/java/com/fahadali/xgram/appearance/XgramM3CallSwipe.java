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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E: the incoming call control of the call screen, a swipe pill. A primary handle in the middle of a
 * surfaceContainerHigh track slides right to answer (it turns the accept green) or left to decline (error, the phone
 * turning into a hang-up); short of 72% of the way it springs back. Tapping the handle nudges it. Chevrons drift
 * towards both ends and the phone wiggles while it rings (not with Reduce motion). Answer and Decline are also
 * accessibility actions. Main thread only, no allocation per frame.
 */
public final class XgramM3CallSwipe extends View {
  public interface Listener {
    /** False when the call cannot be answered yet (the microphone permission is being asked): the handle goes back. */
    boolean onSwipeAnswer ();
    void onSwipeDecline ();
  }

  private static final float TRACK_H = 80f, KNOB = 64f, INSET = 8f, ICON = 28f, END_ICON = 24f;
  private static final float HINT_GAP = 12f, HINT_LINE = 20f;
  private static final float COMMIT = .72f, FLING_DP = 700f, NUDGE = .16f, TOUCH_SLOP_DP = 8f;
  private static final long CHEVRON_MS = 1500L, WIGGLE_MS = 1800L, WIGGLE_ACTIVE_MS = 520L;
  private static final float WIGGLE_DEGREES = 14f;

  private final XgramExpressive.Spring offset, press, nudge;
  private float nudgeDirection = 1f;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final RectF rect = new RectF();

  private final Drawable knobIcon, knobAnswerIcon, knobDeclineIcon, answerIcon, declineIcon;
  private int knobIconColor, knobAnswerColor, knobDeclineColor, answerIconColor, declineIconColor;

  private final String hint;
  private @Nullable String hintDrawn;
  private float hintWidth;

  private boolean dragging, moved, committed, active;
  private float downX, startValue, dragValue;
  private @Nullable VelocityTracker velocity;
  private long startTime;
  private boolean touchable = true;
  private @Nullable Listener listener;

  public XgramM3CallSwipe (Context context) {
    super(context);
    fill.setStyle(Paint.Style.FILL);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeCap(Paint.Cap.ROUND);
    stroke.setStrokeJoin(Paint.Join.ROUND);
    stroke.setStrokeWidth(Screen.dp(2.5f));
    hintPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    hintPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      hintPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
    }

    knobIcon = Drawables.get(R.drawable.baseline_phone_24);
    knobAnswerIcon = Drawables.get(R.drawable.baseline_phone_24);
    knobDeclineIcon = Drawables.get(R.drawable.baseline_phone_24);
    answerIcon = Drawables.get(R.drawable.baseline_phone_24);
    declineIcon = Drawables.get(R.drawable.baseline_call_end_24);

    offset = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    nudge = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    nudge.setEndListener(() -> {
      if (nudge.getTarget() != 0f) {
        nudge.animateTo(0f);
      }
    });

    hint = Lang.getString(R.string.XgramM3CallSwipeHint);
    setContentDescription(hint);
    setFocusable(true);
    ViewCompat.addAccessibilityAction(this, Lang.getString(R.string.XgramM3CallAnswer), (view, arguments) -> {
      commitAnswer(0f);
      return true;
    });
    ViewCompat.addAccessibilityAction(this, Lang.getString(R.string.XgramM3CallDecline), (view, arguments) -> {
      commitDecline(0f);
      return true;
    });
  }

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  /** Ringing: the chevrons and the wiggle run while the pill is shown. */
  public void setActive (boolean active) {
    if (this.active != active) {
      this.active = active;
      if (active) {
        startTime = 0L;
      }
      invalidate();
    }
  }

  /** False while the pill fades out. */
  public void setTouchable (boolean touchable) {
    this.touchable = touchable;
    if (!touchable && dragging) {
      endDrag(false, 0f);
    }
  }

  /** Back to the middle, ready for a new incoming call. */
  public void reset () {
    committed = false;
    dragging = false;
    moved = false;
    offset.setValue(0f);
    press.setValue(0f);
    nudge.setValue(0f);
    recycleVelocity();
    invalidate();
  }

  // Geometry

  private float maxOffset () {
    return Math.max(1f, getWidth() / 2f - Screen.dp(INSET) - Screen.dp(KNOB) / 2f);
  }

  private float value () {
    float v = dragging ? dragValue : offset.getValue();
    return v + nudge.getValue() * NUDGE * nudgeDirection;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Screen.dp(328f) : MeasureSpec.getSize(widthMeasureSpec);
    int height = Screen.dp(TRACK_H) + Screen.dp(HINT_GAP) + Screen.dp(HINT_LINE);
    setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    hintDrawn = TextUtils.ellipsize(hint, hintPaint, Math.max(0, w), TextUtils.TruncateAt.END).toString();
    hintWidth = hintPaint.measureText(hintDrawn);
  }

  // Commit

  private void commitAnswer (float from) {
    if (committed) {
      return;
    }
    if (listener == null || !listener.onSwipeAnswer()) {
      springBack(from);
      return;
    }
    committed = true;
    offset.setValue(from);
    offset.animateTo(1f);
    invalidate();
  }

  private void commitDecline (float from) {
    if (committed) {
      return;
    }
    committed = true;
    offset.setValue(from);
    offset.animateTo(-1f);
    invalidate();
    if (listener != null) {
      listener.onSwipeDecline();
    }
  }

  private void springBack (float from) {
    offset.setValue(from);
    offset.animateTo(0f);
    invalidate();
  }

  // Touch

  private boolean isOnKnob (float x, float y) {
    float cx = getWidth() / 2f + value() * maxOffset();
    float r = Screen.dp(KNOB) / 2f + Screen.dp(8f);
    float cy = Screen.dp(TRACK_H) / 2f;
    return Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final float x = e.getX();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!touchable || committed || !isOnKnob(x, e.getY())) {
          return false;
        }
        dragging = true;
        moved = false;
        downX = x;
        startValue = MathUtils.clamp(offset.getValue(), -1f, 1f);
        dragValue = startValue;
        offset.cancel();
        press.animateTo(1f);
        recycleVelocity();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(true);
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!dragging) {
          return false;
        }
        if (velocity != null) {
          velocity.addMovement(e);
        }
        float dx = x - downX;
        if (!moved && Math.abs(dx) >= Screen.dp(TOUCH_SLOP_DP)) {
          moved = true;
        }
        if (moved) {
          dragValue = MathUtils.clamp(startValue + dx / maxOffset(), -1f, 1f);
          invalidate();
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (!dragging) {
          return false;
        }
        float vx = 0f;
        if (velocity != null) {
          velocity.addMovement(e);
          velocity.computeCurrentVelocity(1000);
          vx = velocity.getXVelocity();
        }
        if (!moved) {
          performClick();
        }
        endDrag(true, vx);
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (dragging) {
          endDrag(false, 0f);
        }
        return true;
      }
    }
    return dragging;
  }

  @Override
  public boolean performClick () {
    boolean handled = super.performClick();
    nudgeDirection = 1f;
    nudge.setValue(0f);
    nudge.animateTo(1f);
    return handled;
  }

  private void endDrag (boolean apply, float velocityX) {
    final float from = dragValue;
    dragging = false;
    press.animateTo(0f);
    recycleVelocity();
    ViewParent parent = getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(false);
    }
    if (!moved) {
      offset.setValue(from);
      invalidate();
      return;
    }
    final float fling = Screen.dp(FLING_DP);
    if (apply && (from >= COMMIT || (velocityX >= fling && from > .2f))) {
      commitAnswer(from);
    } else if (apply && (from <= -COMMIT || (velocityX <= -fling && from < -.2f))) {
      commitDecline(from);
    } else {
      springBack(from);
    }
  }

  private void recycleVelocity () {
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    recycleVelocity();
  }

  // Drawing

  private static void tint (Drawable d, @ColorInt int color) {
    d.setColorFilter(Paints.getColorFilter(color));
  }

  private void tintIcons (int onPrimary, int onAccept, int onError, int accept, int error) {
    if (knobIconColor != onPrimary) {
      knobIconColor = onPrimary;
      tint(knobIcon, onPrimary);
    }
    if (knobAnswerColor != onAccept) {
      knobAnswerColor = onAccept;
      tint(knobAnswerIcon, onAccept);
    }
    if (knobDeclineColor != onError) {
      knobDeclineColor = onError;
      tint(knobDeclineIcon, onError);
    }
    if (answerIconColor != accept) {
      answerIconColor = accept;
      tint(answerIcon, accept);
    }
    if (declineIconColor != error) {
      declineIconColor = error;
      tint(declineIcon, error);
    }
  }

  private static void drawIcon (Canvas c, Drawable d, float cx, float cy, int sizePx, float rotation, float alpha) {
    if (alpha <= 0f) {
      return;
    }
    int left = Math.round(cx - sizePx / 2f), top = Math.round(cy - sizePx / 2f);
    d.setBounds(left, top, left + sizePx, top + sizePx);
    d.setAlpha(Math.round(255f * MathUtils.clamp(alpha)));
    if (rotation != 0f) {
      int save = c.save();
      c.rotate(rotation, cx, cy);
      d.draw(c);
      c.restoreToCount(save);
    } else {
      d.draw(c);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth();
    if (width <= 0) {
      return;
    }
    final float trackH = Screen.dp(TRACK_H);
    final float knob = Screen.dp(KNOB);
    final float inset = Screen.dp(INSET);
    final float cy = trackH / 2f;
    final float max = maxOffset();
    final float v = MathUtils.clamp(value(), -1f, 1f);
    final float toAnswer = Math.max(0f, v), toDecline = Math.max(0f, -v);

    final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
    final int onPrimary = XgramExpressive.color(XgramExpressive.ON_PRIMARY);
    final int error = XgramExpressive.color(XgramExpressive.ERROR);
    final int onError = XgramExpressive.color(XgramExpressive.ON_ERROR);
    final int accept = XgramM3CallButton.acceptColor(false);
    final int onAccept = XgramM3CallButton.acceptColor(true);
    final int variant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    tintIcons(onPrimary, onAccept, onError, accept, error);

    // Track
    rect.set(0, 0, width, trackH);
    fill.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH));
    c.drawRoundRect(rect, trackH / 2f, trackH / 2f, fill);

    // Ends: decline (start) and answer (end)
    final float endRadius = knob / 2f;
    final float declineX = inset + knob / 2f, answerX = width - inset - knob / 2f;
    fill.setColor(XgramExpressive.withAlpha(error, .16f + .24f * toDecline));
    c.drawCircle(declineX, cy, endRadius * (1f + .08f * toDecline), fill);
    fill.setColor(XgramExpressive.withAlpha(accept, .16f + .24f * toAnswer));
    c.drawCircle(answerX, cy, endRadius * (1f + .08f * toAnswer), fill);
    final int endIcon = Screen.dp(END_ICON);
    drawIcon(c, declineIcon, declineX, cy, endIcon, 0f, 1f);
    drawIcon(c, answerIcon, answerX, cy, endIcon, 0f, 1f);

    // Time (chevrons and wiggle)
    final boolean animate = active && !committed && !dragging && !XgramMotion.reduce();
    long now = SystemClock.uptimeMillis();
    if (startTime == 0L) {
      startTime = now;
    }
    final long t = animate ? now - startTime : 0L;

    // Chevrons between the handle and each end
    final float knobX = width / 2f + v * max;
    final float chevronAlpha = MathUtils.clamp(1f - Math.abs(v) * 3f) * (committed ? 0f : 1f);
    if (chevronAlpha > 0f) {
      final float size = Screen.dp(5f);
      final float step = Screen.dp(14f);
      final float phase = animate ? (float) (t % CHEVRON_MS) / CHEVRON_MS : .5f;
      stroke.setColor(variant);
      for (int side = -1; side <= 1; side += 2) {
        float start = knobX + side * (knob / 2f + Screen.dp(18f));
        for (int i = 0; i < 3; i++) {
          float x = start + side * step * i;
          float wave = animate ? 1f - Math.abs(((phase * 3f - i) % 3f + 3f) % 3f - 1f) : .6f;
          float a = chevronAlpha * (.25f + .75f * MathUtils.clamp(wave));
          stroke.setAlpha(Math.round(((variant >>> 24) & 0xff) * a));
          c.drawLine(x - side * size * .5f, cy - size, x + side * size * .5f, cy, stroke);
          c.drawLine(x + side * size * .5f, cy, x - side * size * .5f, cy + size, stroke);
        }
      }
    }

    // Handle
    final float pressValue = MathUtils.clamp(press.getValue(), 0f, 1.2f);
    final float knobRadius = knob / 2f * (1f + .06f * pressValue);
    int knobColor = primary;
    if (toAnswer > 0f) {
      knobColor = ColorUtils.fromToArgb(primary, accept, toAnswer);
    } else if (toDecline > 0f) {
      knobColor = ColorUtils.fromToArgb(primary, error, toDecline);
    }
    fill.setColor(knobColor);
    c.drawCircle(knobX, cy, knobRadius, fill);
    if (pressValue > 0f) {
      fill.setColor(XgramExpressive.withAlpha(onPrimary, XgramExpressive.STATE_PRESSED * MathUtils.clamp(pressValue)));
      c.drawCircle(knobX, cy, knobRadius, fill);
    }
    float rotation = 135f * toDecline;
    if (animate) {
      long w = t % WIGGLE_MS;
      if (w < WIGGLE_ACTIVE_MS) {
        float f = (float) w / WIGGLE_ACTIVE_MS;
        rotation += (float) Math.sin(f * Math.PI * 6f) * WIGGLE_DEGREES * (1f - f);
      }
    }
    final int iconSize = Screen.dp(ICON);
    final float colored = Math.max(toAnswer, toDecline);
    drawIcon(c, knobIcon, knobX, cy, iconSize, rotation, 1f - colored);
    if (toAnswer > 0f) {
      drawIcon(c, knobAnswerIcon, knobX, cy, iconSize, rotation, toAnswer);
    } else if (toDecline > 0f) {
      drawIcon(c, knobDeclineIcon, knobX, cy, iconSize, rotation, toDecline);
    }

    // Hint
    if (hintDrawn != null) {
      hintPaint.setColor(variant);
      hintPaint.setAlpha(Math.round(((variant >>> 24) & 0xff) * (committed ? 0f : 1f - Math.abs(v) * .6f)));
      float top = trackH + Screen.dp(HINT_GAP);
      float baseline = top + Screen.dp(HINT_LINE) / 2f - (hintPaint.descent() + hintPaint.ascent()) / 2f;
      c.drawText(hintDrawn, (width - hintWidth) / 2f, baseline, hintPaint);
    }

    if (animate) {
      postInvalidateOnAnimation();
    }
  }
}
