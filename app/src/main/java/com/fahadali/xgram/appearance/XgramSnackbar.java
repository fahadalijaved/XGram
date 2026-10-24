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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.lang.ref.WeakReference;

/**
 * Material 3 Expressive snackbar in place of the system toast: a floating tonal card ({@link #styleContainer}: a pill
 * while its text fits on one line, 24dp corners once it wraps, elevation level 3) {@link #SIDE_GAP_DP} from the sides
 * (at most {@link #MAX_WIDTH_DP} wide, centred) and {@link #BOTTOM_GAP_DP} above the navigation bar, the keyboard or a
 * {@link #setBottomObstruction bottom obstruction}. Its content is a {@link Row}: an optional leading icon, up to two
 * lines of bodyMedium and an optional tonal pill action. It comes and goes as {@link Motion} says; a new message for a
 * bar that shows fades its content through instead. A bar with an action swipes sideways to dismiss; one without takes
 * no touches (as the system toast it replaces). 2 or 3.5 seconds, 5 with an action, longer when the accessibility
 * timeout asks. Shown while a BaseActivity is resumed: over the topmost popup window (a sheet, the media viewer) when
 * one is showing, over the activity's content otherwise; when the app is in the background the caller falls back to a
 * Toast.
 */
@SuppressLint("ViewConstructor")
public final class XgramSnackbar extends FrameLayout implements RootFrameLayout.MarginModifier {
  /**
   * The gap between a floating snackbar and the screen's bottom (above the navigation bar or the keyboard), shared by
   * this toast replacement and the in-app SnackBar (update, connection), so both float at the same height. The chat
   * list lifts its FABs over the in-app one by this gap.
   */
  public static final float BOTTOM_GAP_DP = 12f;
  /** The gap between a floating bar and the sides of the window. */
  public static final float SIDE_GAP_DP = 16f;
  /** The widest a floating bar gets (a tablet, an unfolded Fold): centred over the bottom. */
  public static final float MAX_WIDTH_DP = 560f;
  /** The container's corners: a pill while the text fits on one line (the row is 56dp tall), 24dp once it wraps. */
  public static final float CORNER_ONE_LINE_DP = XgramExpressive.CORNER_XL, CORNER_WRAPPED_DP = 24f;
  private static final long SHORT_MS = 2000L, LONG_MS = 3500L, ACTION_MS = 5000L;
  /** A swiped bar goes past this share of its width, or flung faster than this (dp per second) the same way. */
  private static final float SWIPE_DISMISS_FRACTION = .35f, SWIPE_FLING_DP = 800f;

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

  private final Row row;
  private final XgramExpressive.MorphDrawable container;
  private final Motion motion;
  /** The horizontal offset of a swipe: follows the finger, then springs back or throws the bar off the side. */
  private final XgramMotion.Value swipe;
  private final Runnable hideRunnable = this::dismiss;
  private final int touchSlop;
  private long durationMs = SHORT_MS;
  /** On its way out (it may still turn around for a new message); gone: swiped away or detached, never reused. */
  private boolean dismissing, gone;
  private boolean laidOut;
  private boolean tracking, dragging;
  private float downX, downY, downTranslationX;
  private @Nullable VelocityTracker velocityTracker;

  private XgramSnackbar (Context context) {
    super(context);
    container = styleContainer(this, CORNER_ONE_LINE_DP);
    row = new Row(context);
    addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    motion = new Motion(this, null);
    swipe = new XgramMotion.Value(XgramExpressive.SPATIAL_FAST, 0f, (v, value, velocity) -> applySwipe(value)).setThreshold(.5f);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    // TalkBack reads the bar as it appears (INVISIBLE to VISIBLE, see show) and again when its content changes
    setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS, null, (v, args) -> {
      dismiss();
      return true;
    });
  }

  /** Main thread. False when there is no resumed activity to show it in. */
  public static boolean show (CharSequence text, int duration) {
    return show(text, duration, null, null);
  }

  /** {@link #show(CharSequence, int, CharSequence, Runnable)} with an "Undo" action (and the undo arrow), shown for 5 seconds. */
  public static boolean showUndo (CharSequence text, @Nullable Runnable onUndo) {
    return show(text, Toast.LENGTH_LONG, onUndo != null ? Lang.getString(R.string.XgramR5SheetsUndo) : null, onUndo);
  }

  /**
   * Main thread. A snackbar with an optional action ({@code actionText} and {@code onAction}, e.g. "Undo" after a
   * removal): tapping it dismisses the bar and runs the action. The leading icon is picked for the known actions and
   * messages ({@link #autoIcon}). False when there is no resumed activity to show it in (the app is in the background):
   * the caller then shows a Toast, or applies the action's alternative itself.
   */
  public static boolean show (CharSequence text, int duration, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
    return show(text, duration, TextUtils.isEmpty(text) ? 0 : autoIcon(text, actionText, onAction), actionText, onAction);
  }

  /**
   * {@link #show(CharSequence, int, CharSequence, Runnable)} with the leading icon {@code iconRes} (a 24dp drawable,
   * through the icon pack), or no icon for 0.
   */
  public static boolean show (CharSequence text, int duration, @DrawableRes int iconRes, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
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
    if (bar != null && (bar.getParent() != parent || bar.gone)) {
      bar.removeNow();
      bar = null;
    }
    final boolean isNew = bar == null;
    if (isNew) {
      bar = new XgramSnackbar(activity);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      params.leftMargin = params.rightMargin = Screen.dp(SIDE_GAP_DP);
      bar.setVisibility(View.INVISIBLE); // visible once it holds its message, below: TalkBack reads the live region then
      parent.addView(bar, params);
      current = new WeakReference<>(bar);
    } else if (bar.dismissing) {
      // On its way out: it turns around where it is and takes the new message
      bar.dismissing = false;
      bar.motion.show(true);
    }
    final XgramSnackbar target = bar;
    final boolean hasAction = !TextUtils.isEmpty(actionText) && onAction != null;
    // A bar that shows fades its content through to the new message; one still coming in just takes it
    bar.row.setContent(text, iconRes, actionText, hasAction ? () -> target.runAction(onAction) : null, !isNew && bar.motion.visibility() > .5f);
    bar.updateBottomMargin(parent);
    bar.durationMs = hasAction ? ACTION_MS : duration == Toast.LENGTH_LONG ? LONG_MS : SHORT_MS;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // The user's "Time to take action" accessibility setting
      try {
        android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
          bar.durationMs = manager.getRecommendedTimeoutMillis((int) bar.durationMs,
            android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT |
            (iconRes != 0 ? android.view.accessibility.AccessibilityManager.FLAG_CONTENT_ICONS : 0) |
            (hasAction ? android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS : 0));
        }
      } catch (Throwable ignored) { }
    }
    if (!bar.dragging) {
      bar.scheduleHide(); // a bar under a finger starts its time when it is let go
    }
    if (isNew) {
      bar.setVisibility(View.VISIBLE);
      bar.motion.show(true);
    }
    return true;
  }

  /**
   * The leading icon of a message, for the known ones: the undo arrow for an Undo action, the retry arrow for Retry,
   * Try again and Download again, the connection pill's icons for the connection's messages (offline, can't connect);
   * 0 (no icon) for the rest.
   */
  @DrawableRes
  private static int autoIcon (@NonNull CharSequence text, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
    if (onAction != null && !TextUtils.isEmpty(actionText)) {
      final String action = actionText.toString();
      if (action.equals(Lang.getString(R.string.XgramR5SheetsUndo))) {
        return R.drawable.baseline_undo_24;
      }
      if (action.equals(Lang.getString(R.string.XgramNetworkRetry)) || action.equals(Lang.getString(R.string.XgramR6B1TryAgain)) ||
        action.equals(Lang.getString(R.string.XgramR6B3DownloadAgain))) {
        return R.drawable.baseline_replay_24;
      }
    }
    final String message = text.toString();
    if (message.equals(Lang.getString(R.string.XgramNetworkOffline)) || message.equals(Lang.getString(R.string.XgramR6B1Offline))) {
      return R.drawable.xgram_cloud_off_24;
    }
    if (message.equals(Lang.getString(R.string.XgramNetworkCantConnect))) {
      return R.drawable.baseline_sync_problem_24;
    }
    return 0;
  }

  /** The action was tapped: the bar leaves first, so a snackbar the action shows turns this one around with its message. */
  private void runAction (@NonNull Runnable action) {
    if (dismissing || gone) {
      return;
    }
    dismiss();
    action.run();
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

  /** Over a popup (a RootFrameLayout, which sets its children's margins to its insets on every measure): the gaps inside them. */
  @Override
  public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    final int side = Screen.dp(SIDE_GAP_DP);
    params.leftMargin = side + Math.max(0, insets.left);
    params.rightMargin = side + Math.max(0, insets.right);
    int bottom = Math.max(0, insets.bottom);
    if (getParent() instanceof ViewGroup) {
      bottom = Math.max(bottom, bottomInsetIn((ViewGroup) getParent()));
    }
    params.bottomMargin = Math.max(Screen.dp(BOTTOM_GAP_DP), bottomObstruction(getParent() instanceof View ? (View) getParent() : null)) + bottom;
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
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    // The corners follow the text's lines: they spring when a new message wraps (or stops wrapping) while it shows
    updateShape(container, this, row.getLineCount(), laidOut && motion.visibility() > 0f);
    laidOut = true;
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    motion.updatePivot();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    // Removed, or its popup closed under it: it is never shown again
    gone = dismissing = true;
    removeCallbacks(hideRunnable);
    motion.cancel();
    swipe.cancel();
    row.cancel();
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
    if (current != null && current.get() == this) {
      current = null;
    }
  }

  private void scheduleHide () {
    removeCallbacks(hideRunnable);
    if (!dismissing) {
      postDelayed(hideRunnable, durationMs);
    }
  }

  private void dismiss () {
    if (dismissing) {
      return;
    }
    dismissing = true;
    removeCallbacks(hideRunnable);
    if (!motion.hide(true, this::removeNow)) {
      removeNow();
    }
  }

  private void removeNow () {
    dismissing = gone = true;
    removeCallbacks(hideRunnable);
    motion.cancel();
    swipe.cancel();
    row.cancel();
    ViewGroup parent = (ViewGroup) getParent();
    if (parent != null) {
      parent.removeView(this);
    }
    if (current != null && current.get() == this) {
      current = null;
    }
  }

  // Swipe to dismiss

  /** Only a bar with an action takes touches (a swipe sends it away); one without lets them through, as the system toast. */
  private boolean takesTouches () {
    return !dismissing && !gone && row.hasAction();
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      tracking = takesTouches();
    }
    if (!tracking) {
      return false;
    }
    trackVelocity(e);
    return super.dispatchTouchEvent(e);
  }

  private void trackVelocity (MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (velocityTracker == null) {
        velocityTracker = VelocityTracker.obtain();
      } else {
        velocityTracker.clear();
      }
    }
    if (velocityTracker != null) {
      // In screen coordinates: the bar moves under the finger
      final MotionEvent screen = MotionEvent.obtain(e);
      screen.setLocation(e.getRawX(), e.getRawY());
      velocityTracker.addMovement(screen);
      screen.recycle();
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        onDown(e);
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        return checkDrag(e); // a sideways drag that starts on the action takes it from the action (which cancels its press)
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (!dragging) {
          settle();
          scheduleHide();
        }
        return false;
      }
    }
    return false;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        onDown(e);
        return true; // a press on the text or the icon does nothing, but it may become a swipe
      }
      case MotionEvent.ACTION_MOVE: {
        if (checkDrag(e)) {
          drag(e.getRawX() - downX);
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (dragging) {
          release(e.getActionMasked() == MotionEvent.ACTION_UP);
        } else {
          settle();
          scheduleHide();
        }
        dragging = false;
        return true;
      }
    }
    return true;
  }

  private void onDown (MotionEvent e) {
    downX = e.getRawX();
    downY = e.getRawY();
    dragging = false;
    removeCallbacks(hideRunnable); // it stays while a finger is on it
    swipe.cancel(); // caught on its way back
    downTranslationX = getTranslationX();
  }

  /** A tap that caught it on its way back from a short swipe (onDown stopped the spring): it goes on back to rest. */
  private void settle () {
    if (!dismissing && getTranslationX() != 0f) {
      swipe.setSpring(XgramExpressive.SPATIAL_FAST).setEndListener(null);
      swipe.animateTo(0f);
    }
  }

  private boolean checkDrag (MotionEvent e) {
    if (!dragging) {
      final float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
      if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
        dragging = true;
        downX += Math.signum(dx) * touchSlop; // it starts moving from where it is, not by the slop
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(true);
        }
      }
    }
    return dragging;
  }

  private void drag (float dx) {
    final float x = downTranslationX + dx;
    swipe.setValue(x);
    applySwipe(x);
  }

  private void applySwipe (float x) {
    setTranslationX(x);
    motion.apply(); // it fades by how far it went (Motion reads the offset)
  }

  /** Let go: off the side past a third of its width or when flung that way, back where it was otherwise. */
  private void release (boolean up) {
    float velocity = 0f;
    if (velocityTracker != null) {
      velocityTracker.computeCurrentVelocity(1000);
      velocity = velocityTracker.getXVelocity();
    }
    final float x = getTranslationX();
    final boolean fling = Math.abs(velocity) > Screen.dp(SWIPE_FLING_DP) && Math.signum(velocity) == Math.signum(x);
    if (up && x != 0f && (Math.abs(x) > getWidth() * SWIPE_DISMISS_FRACTION || fling)) {
      dismissing = gone = true;
      removeCallbacks(hideRunnable);
      if (XgramMotion.reduce()) {
        // Reduce motion: it fades out where the finger left it
        if (!motion.hide(true, this::removeNow)) {
          removeNow();
        }
      } else {
        // Thrown off the side at the finger's speed (the standard spring, no bounce); it fades as it goes
        swipe.setSpring(XgramExpressive.STD_SPATIAL_FAST).setEndListener(this::removeNow);
        swipe.animateTo(Math.signum(x) * (getWidth() + Screen.dp(SIDE_GAP_DP)), velocity);
      }
    } else {
      // Back on the fast spatial spring (a small bounce), and its time starts again
      swipe.setSpring(XgramExpressive.SPATIAL_FAST).setEndListener(null);
      swipe.animateTo(0f, velocity);
      scheduleHide();
    }
  }

  // The floating bar, shared with XgramR6B3Shell.ProgressBar and widget.SnackBar (Expressive design)

  /**
   * Puts the container of a floating bar on {@code bar} ({@link Container}): its background, its outline and elevation
   * level 3, resting at {@code cornerDp}. The returned drawable takes {@link #updateShape}.
   */
  public static XgramExpressive.MorphDrawable styleContainer (@NonNull View bar, float cornerDp) {
    final Container background = new Container(cornerDp);
    background.setHost(bar); // its corner springs redraw the outline (the shadow) too
    bar.setBackground(background);
    bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    bar.setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    return background;
  }

  /** The corners for {@code lines} of text: a pill on one line, {@link #CORNER_WRAPPED_DP} once it wraps; they spring when {@code animated}. */
  public static void updateShape (@NonNull XgramExpressive.MorphDrawable background, @NonNull View bar, int lines, boolean animated) {
    background.setCornerTarget(lines > 1 ? CORNER_WRAPPED_DP : Float.NaN, animated);
    if (!animated) {
      bar.invalidateOutline(); // a snapped corner: the shadow follows at once
    }
  }

  /**
   * The container of a floating bar. In a dark theme surfaceContainerHighest: a tonal step above the page and the bars
   * under it, never the bright inverseSurface slab. In a light theme surfaceContainerLowest with a 1dp outlineVariant
   * hairline: surfaceContainerHighest is tone 90 there, as secondaryContainer (the tonal action, the progress track),
   * which would not show on it, and the hairline keeps the light card apart from the light page with the shadow. Read
   * when drawn, so a day / night switch while it shows repaints it.
   */
  private static final class Container extends XgramExpressive.MorphDrawable {
    private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hairlineRect = new RectF();

    Container (float cornerDp) {
      super(0, cornerDp, Float.NaN);
      hairline.setStyle(Paint.Style.STROKE);
      hairline.setStrokeWidth(Math.max(1, Screen.dp(1f)));
    }

    @Override
    public int getDrawColor () {
      return XgramExpressive.color(Theme.isDark() ? XgramExpressive.SURFACE_CONTAINER_HIGHEST : XgramExpressive.SURFACE_CONTAINER_LOWEST);
    }

    @Override
    public void draw (@NonNull Canvas c) {
      super.draw(c);
      if (!Theme.isDark()) {
        // Inside the edge (the view clips what is outside), on the same corners less half its width
        final float half = hairline.getStrokeWidth() / 2f;
        final float radius = Math.max(0f, cornerPx() - half);
        hairlineRect.set(getBounds());
        hairlineRect.inset(half, half);
        hairline.setColor(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT));
        c.drawRoundRect(hairlineRect, radius, radius, hairline);
      }
    }
  }

  /**
   * How a floating bar comes and goes. In: it rises 24dp and grows from 92 % (from its bottom edge) on the spatial spring
   * (SPATIAL_DEFAULT, a small bounce) while it fades in on the effects spring (EFFECTS_DEFAULT). Out, quicker: it fades,
   * shrinks to 96 % and drops 12dp on the effects spring. Reduce motion: the fades only. Showing a bar on its way out
   * turns it around where it is; hiding one on its way in leaves from where it got to. The listener hears the bar's
   * visibility (0..1, a little over 1 in the bounce) on every frame, for what makes room for it (the chat list's FAB).
   * Main thread only.
   */
  public static final class Motion {
    public interface Listener {
      void onVisibility (float visibility);
    }

    private static final float RISE_DP = 24f, ENTER_SCALE = .92f, FALL_DP = 12f, EXIT_SCALE = .96f;

    private final View view;
    private final @Nullable Listener listener;
    private final XgramMotion.Value rise, fade, exit;
    private boolean entered;
    private @Nullable Runnable onHidden;

    public Motion (@NonNull View view, @Nullable Listener listener) {
      this.view = view;
      this.listener = listener;
      this.rise = new XgramMotion.Value(XgramExpressive.SPATIAL_DEFAULT, 0f, (v, value, velocity) -> apply());
      this.fade = new XgramMotion.Value(XgramExpressive.EFFECTS_DEFAULT, 0f, (v, value, velocity) -> apply());
      this.exit = new XgramMotion.Value(XgramExpressive.EFFECTS_DEFAULT, 0f, (v, value, velocity) -> apply());
      this.exit.setEndListener(() -> {
        if (entered && exit.getTarget() == 1f) {
          finishHide();
        }
      });
      apply();
    }

    /** In (on the springs, or at once); a bar on its way out turns around. */
    public void show (boolean animated) {
      onHidden = null;
      if (!animated) {
        cancel();
        rise.setValue(1f);
        fade.setValue(1f);
        exit.setValue(0f);
        entered = true;
        apply();
        return;
      }
      if (!entered) {
        entered = true;
        rise.setValue(0f);
        fade.setValue(0f);
        exit.setValue(0f);
        apply();
        if (XgramMotion.reduce()) {
          rise.setValue(1f); // reduce motion: no rise or growth, the fade only
        } else {
          rise.animateTo(1f);
        }
        fade.animateTo(1f);
      } else {
        exit.animateTo(0f);
        rise.animateTo(1f);
        fade.animateTo(1f);
      }
    }

    /**
     * Out (on the effects spring, or at once), then {@code onHidden}. False, with nothing done, for a bar that is not in
     * (never shown, or already gone), as Telegram X's BoolAnimator does.
     */
    public boolean hide (boolean animated, @Nullable Runnable onHidden) {
      if (!entered) {
        return false;
      }
      this.onHidden = onHidden;
      if (animated) {
        exit.animateTo(1f); // finishHide when it gets there (at once when it is there already)
      } else {
        exit.setValue(1f);
        apply();
        finishHide();
      }
      return true;
    }

    private void finishHide () {
      entered = false;
      rise.cancel();
      fade.cancel();
      final Runnable after = onHidden;
      onHidden = null;
      if (after != null) {
        after.run();
      }
    }

    /** How much of the bar is in: 0 (out) to 1 (in), a little over 1 in the bounce of its rise. */
    public float visibility () {
      final float in = XgramMotion.reduce() ? XgramMotion.clamp01(fade.getValue()) : Math.max(0f, rise.getValue());
      return in * (1f - XgramMotion.clamp01(exit.getValue()));
    }

    /**
     * The bar's alpha from a sideways swipe, on top of its own: 1 at rest, 0 a whole width off. Read from its
     * translationX, so a swipe that moves the bar itself (XgramConnectionPill.attachSwipeDismiss, with a view animator
     * on its way back) and a spring frame here never disagree on how faded it is.
     */
    private float swipeAlpha () {
      final float x = view.getTranslationX();
      return x == 0f ? 1f : 1f - XgramMotion.clamp01(Math.abs(x) / Math.max(1f, view.getWidth()));
    }

    /** Scales from the middle of the bottom edge; again when the bar's size changes. */
    public void updatePivot () {
      view.setPivotX(view.getWidth() / 2f);
      view.setPivotY(view.getHeight());
    }

    /** Sets the bar's alpha, offset and scale for the current values and tells the listener. */
    public void apply () {
      updatePivot();
      final float r = rise.getValue(), q = XgramMotion.clamp01(exit.getValue());
      view.setAlpha(XgramMotion.clamp01(fade.getValue()) * (1f - q) * swipeAlpha());
      if (XgramMotion.reduce()) {
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
      } else {
        view.setTranslationY((1f - r) * Screen.dpf(RISE_DP) + q * Screen.dpf(FALL_DP));
        final float scale = (ENTER_SCALE + (1f - ENTER_SCALE) * r) * (1f - (1f - EXIT_SCALE) * q);
        view.setScaleX(scale);
        view.setScaleY(scale);
      }
      if (listener != null) {
        listener.onVisibility(visibility());
      }
    }

    public void cancel () {
      rise.cancel();
      fade.cancel();
      exit.cancel();
    }
  }

  /**
   * The content of a floating bar, at least 56dp tall: an optional 24dp leading icon in onSurfaceVariant (16dp from the
   * start, so it sits in the middle of the pill's round end), up to two lines of bodyMedium in onSurface (ellipsized
   * after the second; 20dp from the start without an icon, 12dp after one) and an optional tonal pill action at the end
   * (secondaryContainer and onSecondaryContainer, labelLarge, 40dp tall with 16dp sides in a 48dp touch target, 8dp from
   * the end so it nests in the pill; its corners squeeze to 8dp while pressed). Text and label are in sp, so a large
   * system font grows the row and the pill. Icon and action swap sides in a
   * right-to-left language. A change while it shows fades through: the old content out on EFFECTS_FAST, the new one in
   * on EFFECTS_DEFAULT, the requests in between taken together. Colors are read when drawn. Used by XgramSnackbar and,
   * with Expressive design, by widget.SnackBar (the update and connection snackbars).
   */
  public static final class Row extends LinearLayout {
    private static final float MIN_HEIGHT_DP = 56f, ICON_DP = 24f, ICON_START_DP = 16f, ICON_GAP_DP = 12f,
      TEXT_SIDE_DP = 20f, TEXT_VERTICAL_DP = 14f, ACTION_GAP_DP = 8f, ACTION_END_DP = 8f,
      ACTION_HEIGHT_DP = 40f, ACTION_TOUCH_DP = 48f, ACTION_PADDING_DP = 16f, ACTION_LABEL_PADDING_DP = 10f, ACTION_MAX_WIDTH_DP = 240f;

    private final XgramMotion.Value swap;
    private final ImageView iconView;
    private final TextView textView, actionView;
    // What it is to show (taken at once, or at the bottom of a fade-through)
    private CharSequence text = "";
    private @DrawableRes int iconRes;
    private @Nullable CharSequence actionText;
    private @Nullable Runnable onAction;
    // What it shows
    private CharSequence shownText = "";
    private @DrawableRes int shownIconRes;
    private @Nullable CharSequence shownActionText;
    private @Nullable Runnable shownAction;
    private int iconColor;

    public Row (Context context) {
      super(context);
      swap = new XgramMotion.Value(XgramExpressive.EFFECTS_FAST, 1f, (v, value, velocity) -> setContentAlpha(XgramMotion.clamp01(value)));
      swap.setEndListener(() -> {
        if (swap.getTarget() == 0f) {
          // At the bottom: the new content comes in
          applyContent();
          swap.setSpring(XgramExpressive.EFFECTS_DEFAULT);
          swap.animateTo(1f);
        }
      });
      setOrientation(HORIZONTAL);
      setGravity(Gravity.CENTER_VERTICAL);
      setBaselineAligned(false);
      setMinimumHeight(Screen.dp(MIN_HEIGHT_DP));

      iconView = new ImageView(context);
      iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
      iconView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // the text says it
      iconView.setVisibility(View.GONE);
      LayoutParams iconParams = new LayoutParams(Screen.dp(ICON_DP), Screen.dp(ICON_DP));
      iconParams.setMarginStart(Screen.dp(ICON_START_DP));
      addView(iconView, iconParams);

      textView = new TextView(context);
      XgramExpressive.applyText(textView, XgramExpressive.TS_BODY_M, false);
      // In sp, unlike Telegram X's fixed rows: a large system font grows the bar (two lines, then the ellipsis)
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M));
      textView.setIncludeFontPadding(false);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        textView.setLineHeight(Screen.sp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M)));
      }
      textView.setMaxLines(2);
      textView.setEllipsize(TextUtils.TruncateAt.END);
      addView(textView, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

      actionView = new TextView(context);
      XgramExpressive.applyText(actionView, XgramExpressive.TS_LABEL_L, false);
      actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
      actionView.setSingleLine(true);
      actionView.setEllipsize(TextUtils.TruncateAt.END);
      actionView.setGravity(Gravity.CENTER);
      actionView.setIncludeFontPadding(false);
      // 48dp tall (the 40dp pill and its touch margin) at the default font size; a large font makes it taller
      final int inset = Screen.dp((ACTION_TOUCH_DP - ACTION_HEIGHT_DP) / 2f);
      final int labelPadding = inset + Screen.dp(ACTION_LABEL_PADDING_DP);
      actionView.setMinHeight(Screen.dp(ACTION_TOUCH_DP));
      actionView.setMinWidth(Screen.dp(ACTION_TOUCH_DP));
      actionView.setMaxWidth(Screen.dp(ACTION_MAX_WIDTH_DP));
      XgramExpressive.MorphDrawable pill = XgramExpressive.attachPressMorph(actionView,
        XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      pill.setColorRole(XgramExpressive.SECONDARY_CONTAINER); // read when drawn: follows a day / night switch
      // The 40dp pill in the middle of its 48dp touch target
      actionView.setBackground(new InsetDrawable(actionView.getBackground(), 0, inset, 0, inset));
      // after setBackground: it replaces the padding with the inset's (0 at the sides)
      actionView.setPadding(Screen.dp(ACTION_PADDING_DP), labelPadding, Screen.dp(ACTION_PADDING_DP), labelPadding);
      actionView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo (@NonNull View host, @NonNull AccessibilityNodeInfo info) {
          super.onInitializeAccessibilityNodeInfo(host, info);
          info.setClassName(Button.class.getName()); // TalkBack: "Undo, button"
        }
      });
      actionView.setOnClickListener(v -> {
        final Runnable action = shownAction;
        if (action != null && swap.getTarget() == 1f) { // not the old action of content on its way out
          action.run();
        }
      });
      actionView.setVisibility(View.GONE);
      LayoutParams actionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      actionParams.setMarginEnd(Screen.dp(ACTION_END_DP));
      addView(actionView, actionParams);

      applyContent();
    }

    /**
     * The content: {@code text}, the leading icon {@code iconRes} (0 for none) and the action ({@code actionText} and
     * {@code onAction}, none when either is missing). With {@code animated} a change fades through; the same words only
     * take the new {@code onAction}.
     */
    public void setContent (@Nullable CharSequence text, @DrawableRes int iconRes, @Nullable CharSequence actionText, @Nullable Runnable onAction, boolean animated) {
      final boolean hasAction = onAction != null && !TextUtils.isEmpty(actionText);
      this.text = text != null ? text : "";
      this.iconRes = iconRes;
      this.actionText = hasAction ? actionText : null;
      this.onAction = hasAction ? onAction : null;
      update(animated);
    }

    public void setText (@Nullable CharSequence text, boolean animated) {
      setContent(text, iconRes, actionText, onAction, animated);
    }

    public void setIcon (@DrawableRes int iconRes, boolean animated) {
      setContent(text, iconRes, actionText, onAction, animated);
    }

    public void setAction (@Nullable CharSequence actionText, @Nullable Runnable onAction, boolean animated) {
      setContent(text, iconRes, actionText, onAction, animated);
    }

    private void update (boolean animated) {
      if (TextUtils.equals(text, shownText) && iconRes == shownIconRes && TextUtils.equals(actionText, shownActionText)) {
        shownAction = onAction; // the same words: only what the action runs may change
        if (swap.getTarget() == 0f) {
          swap.setSpring(XgramExpressive.EFFECTS_DEFAULT);
          swap.animateTo(1f); // a change taken back before it showed: the content comes back as it was
        }
        return;
      }
      if (!animated || !isAttachedToWindow()) {
        swap.setValue(1f);
        setContentAlpha(1f);
        applyContent();
        return;
      }
      swap.setSpring(XgramExpressive.EFFECTS_FAST);
      swap.animateTo(0f); // applyContent at the bottom (at once when it is there already)
    }

    private void applyContent () {
      shownText = text;
      textView.setText(text);
      if (shownIconRes != iconRes) {
        shownIconRes = iconRes;
        if (iconRes != 0) {
          iconView.setImageDrawable(Drawables.get(getResources(), iconRes)); // through the icon pack
          iconColor = 0; // tinted in applyColors
          iconView.setVisibility(View.VISIBLE);
        } else {
          iconView.setImageDrawable(null);
          iconView.setVisibility(View.GONE);
        }
      }
      shownActionText = actionText;
      shownAction = onAction;
      final boolean hasAction = actionText != null;
      actionView.setText(hasAction ? actionText : "");
      actionView.setVisibility(hasAction ? View.VISIBLE : View.GONE);
      textView.setPaddingRelative(Screen.dp(iconRes != 0 ? ICON_GAP_DP : TEXT_SIDE_DP), Screen.dp(TEXT_VERTICAL_DP),
        Screen.dp(hasAction ? ACTION_GAP_DP : TEXT_SIDE_DP), Screen.dp(TEXT_VERTICAL_DP));
      applyDirection();
      applyColors();
    }

    private void setContentAlpha (float alpha) {
      iconView.setAlpha(alpha);
      textView.setAlpha(alpha);
      actionView.setAlpha(alpha);
    }

    /**
     * Icon, text and action in the app language's direction (Telegram X keeps views left-to-right and mirrors by
     * Lang.rtl(), whatever the system locale): the icon and the action swap sides, the text aligns to its start.
     */
    public void applyDirection () {
      setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
      textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
    }

    /** onSurface text, onSurfaceVariant icon, onSecondaryContainer action label; the containers read their roles when drawn. */
    public void applyColors () {
      final int textColor = XgramExpressive.color(XgramExpressive.ON_SURFACE);
      if (textView.getCurrentTextColor() != textColor) {
        textView.setTextColor(textColor);
      }
      final int actionColor = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      if (actionView.getCurrentTextColor() != actionColor) {
        actionView.setTextColor(actionColor);
      }
      final int icon = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (iconColor != icon) {
        iconColor = icon;
        iconView.setColorFilter(new PorterDuffColorFilter(icon, PorterDuff.Mode.SRC_IN));
      }
    }

    /** The lines of text it shows (1 before its first layout). */
    public int getLineCount () {
      return Math.max(1, textView.getLineCount());
    }

    /** True while it shows an action. */
    public boolean hasAction () {
      return shownAction != null;
    }

    /** Stops a fade-through where it is (the bar is going). */
    public void cancel () {
      swap.cancel();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      applyDirection();
      applyColors();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      applyColors(); // a day / night switch while the bar is up
      super.dispatchDraw(c);
    }
  }
}
