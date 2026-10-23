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
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.lang.ref.WeakReference;

/**
 * Material 3 snackbar in place of the system toast (experimental): an inverse surface bar with 4dp corners floating
 * {@link #BOTTOM_GAP_DP} above the navigation bar or the keyboard, bodyMedium text, 2 or 3.5 seconds, and an optional
 * action ("Undo") as a text button in inversePrimary at the end; a bar with an action swipes sideways to dismiss, one
 * without takes no touches (as the system toast it replaces). Shown while a BaseActivity is resumed: over the topmost
 * popup window (a sheet, the media viewer) when one is showing, over the activity's content otherwise; when the app is
 * in the background the caller falls back to a Toast.
 */
@SuppressLint("ViewConstructor")
public final class XgramSnackbar extends TextView implements RootFrameLayout.MarginModifier {
  /**
   * The gap between a floating snackbar and the screen's bottom (above the navigation bar or the keyboard) and its
   * sides, shared by this toast replacement and the in-app SnackBar (update, connection), so both float at the same
   * height. The chat list lifts its FABs over the in-app one by this gap.
   */
  public static final float BOTTOM_GAP_DP = 12f;
  private static final long SHORT_MS = 2000L, LONG_MS = 3500L, ACTION_MS = 5000L;
  private static final float MAX_WIDTH_DP = 560f;
  /** The action: a 40dp text button with 12dp side padding, 8dp from the end and 8dp after the text. */
  private static final float ACTION_HEIGHT_DP = 40f, ACTION_PADDING_DP = 12f, ACTION_END_DP = 8f, ACTION_GAP_DP = 8f;

  private static @Nullable WeakReference<XgramSnackbar> current;

  // XGram: views at the bottom of the window that snackbars float above while they are attached and visible (the call
  // screen's controls, the floating navigation toolbar); the highest one wins
  private static final class Obstruction {
    final WeakReference<View> owner;
    int px;
    boolean ownWindowOnly;

    Obstruction (View owner) {
      this.owner = new WeakReference<>(owner);
    }
  }

  private static final java.util.ArrayList<Obstruction> obstructions = new java.util.ArrayList<>();

  /**
   * XGram: {@code owner} covers the bottom {@code px} of the window (above the bottom inset) while it is attached and
   * visible, so a snackbar floats over it rather than on it (the call screen: above its toggle row, not over End); 0
   * clears it, from that owner only. Main thread.
   */
  public static void setBottomObstruction (View owner, int px) {
    setBottomObstruction(owner, px, false);
  }

  /**
   * {@link #setBottomObstruction(View, int)}; with {@code ownWindowOnly} it counts only for a snackbar in the owner's
   * window, not for one over a popup above it (the navigation toolbar under a sheet). Main thread, no allocation for an
   * owner already set (the toolbar reports on every frame of its slide).
   */
  public static void setBottomObstruction (View owner, int px, boolean ownWindowOnly) {
    Obstruction found = null;
    for (int i = obstructions.size() - 1; i >= 0; i--) {
      final Obstruction o = obstructions.get(i);
      final View v = o.owner.get();
      if (v == null) {
        obstructions.remove(i);
      } else if (v == owner) {
        found = o;
      }
    }
    if (px > 0) {
      if (found == null) {
        found = new Obstruction(owner);
        obstructions.add(found);
      }
      found.px = px;
      found.ownWindowOnly = ownWindowOnly;
    } else if (found != null) {
      obstructions.remove(found);
    }
  }

  /** How far above the bottom inset a snackbar in {@code parent} must stay, or 0. */
  private static int bottomObstruction (@Nullable View parent) {
    final View parentRoot = parent != null ? parent.getRootView() : null;
    int px = 0;
    for (int i = 0; i < obstructions.size(); i++) {
      final Obstruction o = obstructions.get(i);
      final View owner = o.owner.get();
      if (owner == null || owner.getWindowToken() == null || owner.getVisibility() != View.VISIBLE) {
        continue;
      }
      if (o.ownWindowOnly && owner.getRootView() != parentRoot) {
        continue;
      }
      px = Math.max(px, o.px);
    }
    return px;
  }

  private final XgramExpressive.Spring visibility;
  private final Runnable hideRunnable = () -> dismiss();
  private long durationMs = SHORT_MS;
  private boolean dismissing;
  private float downX;
  private boolean dragging;

  private @Nullable CharSequence actionText;
  private @Nullable Runnable actionRunnable;
  /** The action as an accessibility action (TalkBack cannot tap the drawn button), View.NO_ID with no action. */
  private int a11yActionId = View.NO_ID;
  private float actionTextWidth;
  private boolean actionPressed;
  private final RectF actionRect = new RectF();
  private final TextPaint actionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint actionStatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
    actionPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    actionPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    actionStatePaint.setStyle(Paint.Style.FILL);
    visibility = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> applyVisibility(value));
    applyVisibility(0f);
  }

  /** Main thread. False when there is no resumed activity to show it in. */
  public static boolean show (CharSequence text, int duration) {
    return show(text, duration, null, null);
  }

  /** {@link #show(CharSequence, int, CharSequence, Runnable)} with an "Undo" action, shown for 5 seconds. */
  public static boolean showUndo (CharSequence text, @Nullable Runnable onUndo) {
    return show(text, Toast.LENGTH_LONG, onUndo != null ? Lang.getString(R.string.XgramR5SheetsUndo) : null, onUndo);
  }

  /**
   * Main thread. A snackbar with an optional action ({@code actionText} and {@code onAction}, e.g. "Undo" after a
   * removal): tapping it runs the action and dismisses the bar. False when there is no resumed activity to show it in
   * (the app is in the background): the caller then shows a Toast, or applies the action's alternative itself.
   */
  public static boolean show (CharSequence text, int duration, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
    if (!XgramExpressive.isEnabled() || TextUtils.isEmpty(text)) {
      return false;
    }
    BaseActivity activity = UI.getUiContext();
    if (activity == null || UI.getUiState() != UI.State.RESUMED || activity.isFinishing()) {
      return false;
    }
    FrameLayout parent = null;
    if (activity.isWindowPopupShowing()) {
      // XGram M3E r5: over the topmost popup that is showing (a sheet, the media viewer), in its own window, so the
      // snackbar is never hidden under it; a popup on its way out leaves the bar to the one below it or the content
      PopupLayout popup = activity.xgramTopShownPopup();
      if (popup != null && popup.isAttachedToWindow() && popup.getWidth() > 0) {
        parent = popup;
      }
    }
    if (parent == null) {
      View content = activity.findViewById(android.R.id.content);
      if (!(content instanceof FrameLayout) || !content.isAttachedToWindow() || content.getWidth() == 0) {
        return false;
      }
      parent = (FrameLayout) content;
    }
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
    bar.setAction(actionText, onAction);
    bar.updateBottomMargin(parent);
    final boolean hasAction = bar.actionRunnable != null;
    bar.durationMs = hasAction ? ACTION_MS : duration == Toast.LENGTH_LONG ? LONG_MS : SHORT_MS;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // The user's "Time to take action" accessibility setting
      try {
        android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
          bar.durationMs = manager.getRecommendedTimeoutMillis((int) bar.durationMs,
            android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT | (hasAction ? android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS : 0));
        }
      } catch (Throwable ignored) { }
    }
    bar.scheduleHide();
    if (isNew) {
      bar.visibility.animateTo(1f);
    }
    bar.announceForAccessibility(hasAction ? text + ". " + bar.actionText : text);
    return true;
  }

  private void setAction (@Nullable CharSequence text, @Nullable Runnable action) {
    if (TextUtils.isEmpty(text) || action == null) {
      actionText = null;
      actionRunnable = null;
      actionTextWidth = 0f;
    } else {
      actionText = text;
      actionRunnable = action;
      actionTextWidth = actionPaint.measureText(text, 0, text.length());
    }
    actionPressed = false;
    // the text ends 8dp before the action button, which ends 8dp before the bar's end
    final int end = actionRunnable != null ? Screen.dp(ACTION_END_DP + ACTION_GAP_DP + ACTION_PADDING_DP * 2) + (int) Math.ceil(actionTextWidth) : Screen.dp(16f);
    setPaddingRelative(Screen.dp(16f), Screen.dp(14f), end, Screen.dp(14f));
    if (a11yActionId != View.NO_ID) {
      androidx.core.view.ViewCompat.removeAccessibilityAction(this, a11yActionId);
      a11yActionId = View.NO_ID;
    }
    if (actionRunnable != null && actionText != null) {
      a11yActionId = androidx.core.view.ViewCompat.addAccessibilityAction(this, actionText.toString(), (v, args) -> {
        final Runnable a = actionRunnable;
        actionRunnable = null;
        if (a != null) {
          a.run();
        }
        dismiss();
        return true;
      });
    }
    invalidate();
  }

  private boolean isRtl () {
    return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; // as the relative padding that keeps the text off the action
  }

  private void layoutAction () {
    final float width = actionTextWidth + Screen.dp(ACTION_PADDING_DP * 2);
    final float height = Screen.dp(ACTION_HEIGHT_DP);
    final float top = (getHeight() - height) / 2f;
    if (isRtl()) {
      final float left = Screen.dp(ACTION_END_DP);
      actionRect.set(left, top, left + width, top + height);
    } else {
      final float right = getWidth() - Screen.dp(ACTION_END_DP);
      actionRect.set(right - width, top, right, top + height);
    }
  }

  /** The bottom inset under the bar in {@code parent}: the navigation bar or the keyboard, less what the parent already ends above. */
  private int bottomInsetIn (ViewGroup parent) {
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
    return inset;
  }

  private void updateBottomMargin (FrameLayout parent) {
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
    int bottom = bottomInsetIn(parent) + Math.max(Screen.dp(BOTTOM_GAP_DP), bottomObstruction(parent));
    if (params.bottomMargin != bottom) {
      params.bottomMargin = bottom;
      setLayoutParams(params);
    }
  }

  /** Over a popup (a RootFrameLayout, which sets its children's margins to its insets on every measure): 12dp inside them. */
  @Override
  public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    final int gap = Screen.dp(BOTTOM_GAP_DP);
    params.leftMargin = gap + Math.max(0, insets.left);
    params.rightMargin = gap + Math.max(0, insets.right);
    int bottom = Math.max(0, insets.bottom);
    if (getParent() instanceof ViewGroup) {
      bottom = Math.max(bottom, bottomInsetIn((ViewGroup) getParent()));
    }
    params.bottomMargin = Math.max(gap, bottomObstruction(getParent() instanceof View ? (View) getParent() : null)) + bottom;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int maxWidth = Screen.dp(MAX_WIDTH_DP);
    if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
      widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int textColor = XgramExpressive.color(XgramExpressive.INVERSE_ON_SURFACE);
    if (getCurrentTextColor() != textColor) {
      setTextColor(textColor); // a day / night switch while the bar is up
    }
    super.onDraw(c);
    if (actionRunnable != null && actionText != null) {
      layoutAction();
      final int color = XgramExpressive.color(XgramExpressive.INVERSE_PRIMARY);
      if (actionPressed) {
        actionStatePaint.setColor(XgramExpressive.withAlpha(color, XgramExpressive.STATE_PRESSED));
        final float radius = actionRect.height() / 2f;
        c.drawRoundRect(actionRect, radius, radius, actionStatePaint);
      }
      actionPaint.setColor(color);
      final float baseline = actionRect.centerY() - (actionPaint.descent() + actionPaint.ascent()) / 2f;
      c.drawText(actionText, 0, actionText.length(), actionRect.left + Screen.dp(ACTION_PADDING_DP), baseline, actionPaint);
    }
  }

  private void scheduleHide () {
    removeCallbacks(hideRunnable);
    postDelayed(hideRunnable, durationMs);
  }

  private void applyVisibility (float value) {
    float v = Math.max(0f, Math.min(1f, value));
    setAlpha(v);
    // Reduce motion: a fade only
    setTranslationY(XgramMotion.reduce() ? 0f : (1f - value) * Screen.dp(24f));
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

  private boolean inAction (MotionEvent e) {
    return actionRunnable != null && e.getX() >= actionRect.left && e.getX() < actionRect.right && e.getY() >= actionRect.top - Screen.dp(4f) && e.getY() < actionRect.bottom + Screen.dp(4f);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (dismissing) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (actionRunnable == null) {
          // XGram: like the system toast it replaces, a bar with no action takes no touches: a tap goes to what is
          // under it (the chat's composer) rather than only keeping the bar up
          return false;
        }
        downX = e.getRawX();
        dragging = false;
        removeCallbacks(hideRunnable);
        if (actionRunnable != null) {
          layoutAction();
          actionPressed = inAction(e);
          if (actionPressed) {
            invalidate();
          }
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        float dx = e.getRawX() - downX;
        if (!dragging && Math.abs(dx) > Screen.getTouchSlop()) {
          dragging = true;
          if (actionPressed) {
            actionPressed = false;
            invalidate();
          }
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
        if (actionPressed) {
          actionPressed = false;
          invalidate();
          if (e.getActionMasked() == MotionEvent.ACTION_UP && !dragging && inAction(e)) {
            final Runnable action = actionRunnable;
            actionRunnable = null;
            if (action != null) {
              action.run();
            }
            dismiss();
            return true;
          }
        }
        final boolean reduce = XgramMotion.reduce();
        if (dragging && Math.abs(dx) > getWidth() / 3f) {
          dismissing = true;
          if (reduce) {
            removeNow();
          } else {
            animate().translationX(Math.signum(dx) * getWidth()).alpha(0f).setDuration(150L).withEndAction(this::removeNow).start();
          }
        } else {
          dragging = false;
          if (reduce) {
            setTranslationX(0f);
            setAlpha(1f);
          } else {
            animate().translationX(0f).alpha(1f).setDuration(150L).start();
          }
          scheduleHide();
        }
        return true;
      }
    }
    return super.onTouchEvent(e);
  }
}
