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
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.lang.ref.WeakReference;

/**
 * Material 3 snackbar in place of the system toast (experimental): an inverse surface bar with 4dp corners floating
 * {@link #BOTTOM_GAP_DP} above the navigation bar or the keyboard, bodyMedium text, 2 or 3.5 seconds, swipe sideways to
 * dismiss. Shown only while a BaseActivity is resumed and no popup window covers it; otherwise the caller falls back to
 * a Toast.
 */
@SuppressLint("ViewConstructor")
public final class XgramSnackbar extends TextView {
  /**
   * The gap between a floating snackbar and the screen's bottom (above the navigation bar or the keyboard) and its
   * sides, shared by this toast replacement and the in-app SnackBar (update, connection), so both float at the same
   * height. The chat list lifts its FABs over the in-app one by this gap.
   */
  public static final float BOTTOM_GAP_DP = 12f;
  private static final long SHORT_MS = 2000L, LONG_MS = 3500L;
  private static final float MAX_WIDTH_DP = 560f;

  private static @Nullable WeakReference<XgramSnackbar> current;

  private final XgramExpressive.Spring visibility;
  private final Runnable hideRunnable = () -> dismiss();
  private long durationMs = SHORT_MS;
  private boolean dismissing;
  private float downX;
  private boolean dragging;

  private XgramSnackbar (Context context) {
    super(context);
    XgramExpressive.applyText(this, XgramExpressive.TS_BODY_M, false);
    setTextColor(XgramExpressive.color(XgramExpressive.INVERSE_ON_SURFACE));
    setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(14f));
    setMinHeight(Screen.dp(48f));
    setMaxLines(4);
    setEllipsize(TextUtils.TruncateAt.END);
    XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_XS, Float.NaN);
    background.setColorRole(XgramExpressive.INVERSE_SURFACE);
    setBackground(background);
    setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    visibility = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> applyVisibility(value));
    applyVisibility(0f);
  }

  /** Main thread. False when there is no resumed activity to show it in. */
  public static boolean show (CharSequence text, int duration) {
    if (!XgramExpressive.isEnabled() || TextUtils.isEmpty(text)) {
      return false;
    }
    BaseActivity activity = UI.getUiContext();
    if (activity == null || UI.getUiState() != UI.State.RESUMED || activity.isFinishing() || activity.isWindowPopupShowing()) {
      return false;
    }
    View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof FrameLayout) || !content.isAttachedToWindow() || content.getWidth() == 0) {
      return false;
    }
    FrameLayout parent = (FrameLayout) content;
    XgramSnackbar bar = current != null ? current.get() : null;
    if (bar != null && (bar.getParent() != parent || bar.dismissing)) {
      bar.removeNow();
      bar = null;
    }
    boolean isNew = bar == null;
    if (isNew) {
      bar = new XgramSnackbar(activity);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      params.leftMargin = params.rightMargin = Screen.dp(BOTTOM_GAP_DP);
      parent.addView(bar, params);
      current = new WeakReference<>(bar);
    }
    bar.setText(text);
    bar.updateBottomMargin(parent);
    bar.durationMs = duration == Toast.LENGTH_LONG ? LONG_MS : SHORT_MS;
    bar.scheduleHide();
    if (isNew) {
      bar.visibility.animateTo(1f);
    }
    bar.announceForAccessibility(text);
    return true;
  }

  private void updateBottomMargin (FrameLayout parent) {
    int inset = 0;
    WindowInsets insets = parent.getRootWindowInsets();
    if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      inset = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.ime()).bottom;
      // The content frame may already end above the navigation bar
      View root = parent.getRootView();
      int[] location = new int[2];
      parent.getLocationInWindow(location);
      int gap = root != null ? root.getHeight() - (location[1] + parent.getHeight()) : 0;
      inset = Math.max(0, inset - Math.max(0, gap));
    }
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
    int bottom = inset + Screen.dp(BOTTOM_GAP_DP);
    if (params.bottomMargin != bottom) {
      params.bottomMargin = bottom;
      setLayoutParams(params);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int maxWidth = Screen.dp(MAX_WIDTH_DP);
    if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
      widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void scheduleHide () {
    removeCallbacks(hideRunnable);
    postDelayed(hideRunnable, durationMs);
  }

  private void applyVisibility (float value) {
    float v = Math.max(0f, Math.min(1f, value));
    setAlpha(v);
    setTranslationY((1f - value) * Screen.dp(24f));
  }

  private void dismiss () {
    if (dismissing) {
      return;
    }
    dismissing = true;
    removeCallbacks(hideRunnable);
    visibility.setEndListener(this::removeNow);
    visibility.animateTo(0f);
  }

  private void removeNow () {
    dismissing = true;
    removeCallbacks(hideRunnable);
    visibility.cancel();
    animate().cancel();
    ViewGroup parent = (ViewGroup) getParent();
    if (parent != null) {
      parent.removeView(this);
    }
    if (current != null && current.get() == this) {
      current = null;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (dismissing) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downX = e.getRawX();
        dragging = false;
        removeCallbacks(hideRunnable);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getRawX() - downX;
        if (!dragging && Math.abs(dx) > Screen.getTouchSlop()) {
          dragging = true;
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging) {
          setTranslationX(dx);
          setAlpha(Math.max(0f, 1f - Math.abs(dx) / Math.max(1f, getWidth())));
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        float dx = e.getRawX() - downX;
        if (dragging && Math.abs(dx) > getWidth() / 3f) {
          dismissing = true;
          animate().translationX(Math.signum(dx) * getWidth()).alpha(0f).setDuration(150L).withEndAction(this::removeNow).start();
        } else {
          dragging = false;
          animate().translationX(0f).alpha(1f).setDuration(150L).start();
          scheduleHide();
        }
        return true;
      }
    }
    return super.onTouchEvent(e);
  }
}
