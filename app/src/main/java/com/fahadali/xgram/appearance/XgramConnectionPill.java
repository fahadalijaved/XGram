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
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * XGram M3E: the chat list's connection pill (a 32dp secondary container under the app bar): a loading indicator
 * while connecting, an icon when offline, stuck or just connected. It enters and leaves on RenderNode properties only,
 * swaps its content with a fade-through on one spring and draws its container at a springing width, so it never asks
 * for a layout. Frames run only while a spring moves or the loading indicator is on screen.
 */
public final class XgramConnectionPill extends View {
  private static final float SWAP_SPLIT = .35f;

  private final int height = Screen.dp(32f), radius = Screen.dp(16f);
  private final int padStartLeading = Screen.dp(8f), padStartText = Screen.dp(12f), padEnd = Screen.dp(16f);
  private final int gap = Screen.dp(8f), box = Screen.dp(20f), iconSize = Screen.dp(18f), slideDistance = Screen.dp(6f);
  private final int travel = Screen.dp(40f);

  private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint statePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final float textCenterOffset;
  private final RectF rect = new RectF();

  /** One piece of content (leading element and text). Two of them, swapped, so nothing is allocated per change. */
  private static final class Slot {
    int state = XgramConnection.HIDDEN;
    CharSequence fullText = "";
    String text = "";
    float textWidth;
    float pillWidth;
  }

  private Slot current = new Slot(), previous = new Slot();
  private long progressStart;

  private final Drawable[] icons = new Drawable[5];
  private final int[] iconColors = new int[5];

  private int state = XgramConnection.HIDDEN;
  private boolean suppressed, shown, pressed;

  private final XgramMotion.Value width, swap, slide, flash;
  private final XgramMotion.Value translation, scale, alpha;

  /** How far the pill is in (0 gone, 1 fully in), for content that moves down to make room for it. */
  public interface OffsetListener {
    void onPillOffset (float shown);
  }

  private @Nullable OffsetListener offsetListener;
  private float lastOffset = -1f;

  public XgramConnectionPill (Context context) {
    super(context);
    textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    textCenterOffset = -(fm.ascent + fm.descent) / 2f;

    width = new XgramMotion.Value(XgramExpressive.SPATIAL_FAST, 0f, (v, value, velocity) -> {
      invalidate();
      invalidateOutline();
    }).setThreshold(.5f);
    swap = new XgramMotion.Value(XgramExpressive.EFFECTS_DEFAULT, 1f, (v, value, velocity) -> invalidate());
    slide = new XgramMotion.Value(XgramExpressive.SPATIAL_FAST, 1f, (v, value, velocity) -> invalidate());
    flash = new XgramMotion.Value(XgramExpressive.EFFECTS_DEFAULT, 0f, (v, value, velocity) -> invalidate());
    translation = new XgramMotion.Value(XgramExpressive.SPATIAL_DEFAULT, -travel, (v, value, velocity) -> {
      setTranslationY(value);
      dispatchOffset(value);
    }).setThreshold(.5f);
    scale = new XgramMotion.Value(XgramExpressive.SPATIAL_DEFAULT, .8f, (v, value, velocity) -> {
      setScaleX(value);
      setScaleY(value);
    });
    alpha = new XgramMotion.Value(XgramExpressive.EFFECTS_FAST, 0f, (v, value, velocity) -> setAlpha(Math.max(0f, Math.min(1f, value))));
    alpha.setEndListener(() -> {
      if (!shown && alpha.getValue() <= 0f) {
        setVisibility(View.INVISIBLE);
      }
    });

    setAlpha(0f);
    setTranslationY(-travel);
    setScaleX(.8f);
    setScaleY(.8f);
    setVisibility(View.INVISIBLE);
    setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
    setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline (View view, Outline outline) {
        float w = Math.max(0f, width.getValue());
        float cx = view.getWidth() / 2f;
        outline.setRoundRect(Math.round(cx - w / 2f), 0, Math.round(cx + w / 2f), height, radius);
      }
    });
    setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  }

  /** A pill at the top center of {@code parent} (the chat list container), above its other children. */
  public static XgramConnectionPill attach (ViewGroup parent) {
    XgramConnectionPill pill = new XgramConnectionPill(parent.getContext());
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pill.height, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    params.topMargin = Screen.dp(8f);
    pill.setLayoutParams(params);
    parent.addView(pill);
    return pill;
  }

  @Override
  public boolean hasOverlappingRendering () {
    return false;
  }

  // Room for the pill

  /** The room content needs to give the pill when it is fully in: its height and 8dp above and below. */
  public int getReservedHeight () {
    return height + Screen.dp(16f);
  }

  /**
   * {@code listener} follows the pill in and out on its own spring (0 when hidden or suppressed), and gets the current
   * value now. Calls come only when the value changes.
   */
  public void setOffsetListener (@Nullable OffsetListener listener) {
    this.offsetListener = listener;
    this.lastOffset = -1f;
    dispatchOffset(translation.getValue());
  }

  private void dispatchOffset (float translationY) {
    final OffsetListener listener = offsetListener;
    if (listener == null) {
      return;
    }
    // A hide with Reduce motion (or without animation) fades in place: the content goes back at once
    final float offset = !shown && !translation.isRunning() ? 0f : clamp(1f + translationY / travel);
    if (offset != lastOffset) {
      lastOffset = offset;
      listener.onPillOffset(offset);
    }
  }

  // State

  public void setDisplay (int state, CharSequence text, boolean animated) {
    if (state != XgramConnection.HIDDEN) {
      boolean fresh = !shown && alpha.getValue() <= 0f;
      if (fresh || !animated) {
        previous.state = XgramConnection.HIDDEN;
        fill(current, state, text);
        swap.setValue(1f);
        slide.setValue(1f);
        width.setValue(current.pillWidth);
        flash.setValue(state == XgramConnection.CONNECTED_FLASH ? 1f : 0f);
        invalidateOutline();
        invalidate();
      } else if (current.state != state || !TextUtils.equals(current.fullText, text)) {
        Slot old = previous;
        previous = current;
        current = old;
        fill(current, state, text);
        swap.setValue(0f);
        swap.animateTo(1f);
        if (XgramMotion.reduce()) {
          slide.setValue(1f);
        } else {
          slide.setValue(0f);
          slide.setSpring(XgramExpressive.SPATIAL_FAST).animateTo(1f);
        }
        width.setSpring(XgramExpressive.SPATIAL_FAST).animateTo(current.pillWidth);
        flash.animateTo(state == XgramConnection.CONNECTED_FLASH ? 1f : 0f);
      }
    }
    this.state = state;
    updateAccessibility(text);
    updateShown(animated);
  }

  /**
   * XGram M3E r6: the tracker this pill shows (a login screen of an account being added has its own, see
   * {@link XgramConnection#track}); its stuck tap retries through it. Null is the shared tracker.
   */
  private @Nullable XgramConnection source;

  public void setSource (@Nullable XgramConnection source) {
    this.source = source;
  }

  /** Hidden while the chat list searches, selects or has its FAB menu open. */
  public void setSuppressed (boolean suppressed, boolean animated) {
    if (this.suppressed != suppressed) {
      this.suppressed = suppressed;
      updateShown(animated);
    }
  }

  private void fill (Slot slot, int state, CharSequence text) {
    if (state == XgramConnection.PROGRESS && (slot == current ? previous : current).state != XgramConnection.PROGRESS) {
      progressStart = SystemClock.uptimeMillis();
    }
    slot.state = state;
    slot.fullText = text != null ? text : "";
    int available = (getWidth() > 0 ? getWidth() : Screen.currentWidth()) - Screen.dp(96f) - box - gap;
    slot.text = TextUtils.ellipsize(slot.fullText, textPaint, Math.max(available, Screen.dp(48f)), TextUtils.TruncateAt.END).toString();
    slot.textWidth = textPaint.measureText(slot.text);
    slot.pillWidth = padStartLeading + box + gap + slot.textWidth + padEnd;
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != oldw && current.state != XgramConnection.HIDDEN) {
      // The text was ellipsized for another width (a fill before the first layout measures against the screen)
      final long start = progressStart;
      fill(current, current.state, current.fullText);
      progressStart = start;
      if (width.isRunning()) {
        width.animateTo(current.pillWidth);
      } else {
        width.setValue(current.pillWidth);
        invalidateOutline();
      }
      invalidate();
    }
  }

  private void updateShown (boolean animated) {
    boolean show = state != XgramConnection.HIDDEN && !suppressed;
    if (shown == show) {
      if (show) {
        invalidate();
      }
      return;
    }
    shown = show;
    pressed = false;
    if (!animated || !isLaidOut()) {
      translation.setValue(show ? 0f : -travel);
      scale.setValue(show ? 1f : .8f);
      alpha.setValue(show ? 1f : 0f);
      setTranslationY(translation.getValue());
      setScaleX(scale.getValue());
      setScaleY(scale.getValue());
      setAlpha(alpha.getValue());
      setVisibility(show ? View.VISIBLE : View.INVISIBLE);
      dispatchOffset(translation.getValue());
      return;
    }
    final boolean reduce = XgramMotion.reduce();
    if (show) {
      setVisibility(View.VISIBLE);
      if (alpha.getValue() <= 0f) {
        translation.setValue(reduce ? 0f : -travel);
        scale.setValue(reduce ? 1f : .8f);
        setTranslationY(translation.getValue());
        setScaleX(scale.getValue());
        setScaleY(scale.getValue());
      }
      translation.setSpring(XgramExpressive.SPATIAL_DEFAULT).animateTo(0f);
      scale.setSpring(XgramExpressive.SPATIAL_DEFAULT).animateTo(1f);
      alpha.animateTo(1f);
    } else {
      if (!reduce) {
        translation.setSpring(XgramExpressive.STD_SPATIAL_FAST).animateTo(-travel);
        scale.setSpring(XgramExpressive.STD_SPATIAL_FAST).animateTo(.9f);
      }
      alpha.animateTo(0f);
    }
    dispatchOffset(translation.getValue()); // Reduce motion: no spring moves the translation, so the content snaps
  }

  private void updateAccessibility (CharSequence text) {
    setContentDescription(state != XgramConnection.HIDDEN ? text : null);
    setImportantForAccessibility(state != XgramConnection.HIDDEN ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
    boolean clickable = isClickableState();
    setClickable(clickable);
    if (clickable) {
      CharSequence label = state == XgramConnection.STUCK ? Lang.getString(R.string.XgramNetworkRetry) : Lang.getString(R.string.XgramNetworkSettings);
      ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, label, null);
    }
  }

  private boolean isClickableState () {
    return state == XgramConnection.STUCK || (state == XgramConnection.OFFLINE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
  }

  // Touch

  private boolean hit (float x, float y) {
    float w = width.getValue(), cx = getWidth() / 2f;
    return x >= cx - w / 2f && x <= cx + w / 2f && y >= 0 && y <= getHeight();
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!shown || !isClickableState()) {
      pressed = false;
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if (!hit(e.getX(), e.getY())) {
          return false;
        }
        pressed = true;
        invalidate();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (pressed && !hit(e.getX(), e.getY())) {
          pressed = false;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (pressed) {
          pressed = false;
          invalidate();
          performClick();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        pressed = false;
        invalidate();
        return true;
    }
    return true;
  }

  @Override
  public boolean performClick () {
    super.performClick();
    if (state == XgramConnection.STUCK) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
      }
      (source != null ? source : XgramConnection.instance()).retry();
      return true;
    }
    if (state == XgramConnection.OFFLINE) {
      return openInternetPanel(getContext());
    }
    return false;
  }

  /**
   * Lets a snackbar be swiped away sideways (Material behavior): it follows the finger and fades, then flies out and
   * runs {@code onSwiped}, or springs back. Taps on its action button still reach the button.
   */
  public static void attachSwipeDismiss (View bar, Runnable onSwiped) {
    final int slop = ViewConfiguration.get(bar.getContext()).getScaledTouchSlop();
    final float flingVelocity = Screen.dp(800f);
    bar.setOnTouchListener(new OnTouchListener() {
      private float downX;
      private boolean dragging;
      private @Nullable VelocityTracker tracker;

      @Override
      public boolean onTouch (View v, MotionEvent e) {
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN: {
            downX = e.getRawX();
            dragging = false;
            v.animate().cancel();
            if (tracker == null) {
              tracker = VelocityTracker.obtain();
            } else {
              tracker.clear();
            }
            tracker.addMovement(e);
            return true;
          }
          case MotionEvent.ACTION_MOVE: {
            if (tracker != null) {
              tracker.addMovement(e);
            }
            float dx = e.getRawX() - downX;
            if (!dragging && Math.abs(dx) > slop) {
              dragging = true;
              if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
              }
            }
            if (dragging) {
              v.setTranslationX(dx);
              v.setAlpha(clamp(1f - Math.abs(dx) / Math.max(1, v.getWidth())));
            }
            return true;
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL: {
            if (dragging) {
              float vx = 0f;
              if (tracker != null) {
                tracker.addMovement(e);
                tracker.computeCurrentVelocity(1000);
                vx = tracker.getXVelocity();
              }
              float tx = v.getTranslationX();
              boolean fling = Math.abs(vx) > flingVelocity && Math.signum(vx) == Math.signum(tx);
              if (e.getActionMasked() == MotionEvent.ACTION_UP && (Math.abs(tx) > v.getWidth() * .35f || fling)) {
                float end = Math.signum(tx) * v.getWidth();
                v.animate().translationX(end).alpha(0f)
                  .setInterpolator(XgramMotion.interpolator(XgramExpressive.STD_SPATIAL_FAST))
                  .setDuration(XgramMotion.duration(XgramExpressive.STD_SPATIAL_FAST))
                  .withEndAction(onSwiped)
                  .start();
              } else {
                v.animate().translationX(0f).alpha(1f)
                  .setInterpolator(XgramMotion.interpolator(XgramExpressive.SPATIAL_FAST))
                  .setDuration(XgramMotion.duration(XgramExpressive.SPATIAL_FAST))
                  .start();
              }
            }
            dragging = false;
            if (tracker != null) {
              tracker.recycle();
              tracker = null;
            }
            return true;
          }
        }
        return true;
      }
    });
  }

  /** The system's internet connectivity panel (Android 10+). */
  public static boolean openInternetPanel (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        context.startActivity(new Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
        return true;
      } catch (Throwable ignored) { }
    }
    return false;
  }

  // Drawing

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final float w = width.getValue();
    if (w <= 0f || getAlpha() <= 0f) {
      return;
    }
    final float cx = getWidth() / 2f, cy = height / 2f;
    rect.set(cx - w / 2f, 0, cx + w / 2f, height);
    final float flashF = Math.max(0f, Math.min(1f, flash.getValue()));
    containerPaint.setColor(ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), flashF));
    c.drawRoundRect(rect, radius, radius, containerPaint);
    if (pressed) {
      statePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), XgramExpressive.STATE_PRESSED));
      c.drawRoundRect(rect, radius, radius, statePaint);
    }

    final float s = swap.getValue();
    final float oldAlpha = previous.state != XgramConnection.HIDDEN ? 1f - clamp(s / SWAP_SPLIT) : 0f;
    final float newAlpha = clamp((s - SWAP_SPLIT) / (1f - SWAP_SPLIT));
    final long now = SystemClock.uptimeMillis();
    if (oldAlpha > 0f) {
      drawSlot(c, previous, cx, cy, oldAlpha, 1f, 0f, now);
    }
    if (newAlpha > 0f) {
      final boolean reduce = XgramMotion.reduce();
      float contentScale = reduce ? 1f : .92f + .08f * newAlpha;
      float dy = reduce ? 0f : (1f - slide.getValue()) * slideDistance;
      drawSlot(c, current, cx, cy, newAlpha, contentScale, dy, now);
    }
    if (shown && ((current.state == XgramConnection.PROGRESS && newAlpha > 0f) || (previous.state == XgramConnection.PROGRESS && oldAlpha > 0f))) {
      postInvalidateOnAnimation();
    }
  }

  private void drawSlot (Canvas c, Slot slot, float cx, float cy, float a, float contentScale, float textDy, long now) {
    final boolean rtl = Lang.rtl();
    final int contentColor = XgramExpressive.color(slot.state == XgramConnection.CONNECTED_FLASH ? XgramExpressive.ON_PRIMARY_CONTAINER : XgramExpressive.ON_SECONDARY_CONTAINER);
    final float half = slot.pillWidth / 2f;
    final float leadCx = rtl ? cx + half - padStartLeading - box / 2f : cx - half + padStartLeading + box / 2f;
    final int restore = c.save();
    if (contentScale != 1f) {
      c.scale(contentScale, contentScale, cx, cy);
    }
    if (slot.state == XgramConnection.PROGRESS) {
      XgramLoadingIndicator.draw(c, leadCx, cy, box * XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE, XgramExpressive.withAlpha(contentColor, a), now - progressStart);
    } else {
      Drawable icon = icon(slot.state, contentColor);
      if (icon != null) {
        int half2 = iconSize / 2;
        icon.setBounds(Math.round(leadCx) - half2, Math.round(cy) - half2, Math.round(leadCx) - half2 + iconSize, Math.round(cy) - half2 + iconSize);
        icon.setAlpha(Math.round(255 * a));
        icon.draw(c);
      }
    }
    float textX = rtl ? leadCx - box / 2f - gap - slot.textWidth : leadCx + box / 2f + gap;
    textPaint.setColor(XgramExpressive.withAlpha(contentColor, a));
    c.drawText(slot.text, textX, cy + textCenterOffset + textDy, textPaint);
    c.restoreToCount(restore);
  }

  @Nullable
  private Drawable icon (int state, int color) {
    final int res;
    switch (state) {
      case XgramConnection.OFFLINE:
        res = R.drawable.xgram_cloud_off_24;
        break;
      case XgramConnection.STUCK:
        res = R.drawable.baseline_sync_problem_24;
        break;
      case XgramConnection.CONNECTED_FLASH:
        res = R.drawable.baseline_check_circle_24;
        break;
      default:
        return null;
    }
    Drawable d = icons[state];
    if (d == null) {
      d = Drawables.get(getResources(), XgramIconPack.map(res));
      if (d == null) {
        return null;
      }
      icons[state] = d;
      iconColors[state] = 0;
    }
    if (iconColors[state] != color || d.getColorFilter() == null) {
      iconColors[state] = color;
      d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }
    return d;
  }

  private static float clamp (float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }
}
