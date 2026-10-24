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
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/**
 * XGram M3E round 6, batch 3 (the dialog, sheet, menu and tooltip shell): colors of a menu or tooltip over media
 * (a forced theme), the snackbar with its toast fallback, and the persistent progress bar of long jobs (chat export).
 * Main thread only; used while {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramR6B3Shell {
  private XgramR6B3Shell () { }

  // Colors

  /** {@code role} in the current theme, or in the light / dark scheme of {@code forcedTheme} (camera, media viewer). */
  @ColorInt
  public static int color (int role, @Nullable ThemeDelegate forcedTheme) {
    return XgramR5Sheets.role(role, forcedTheme);
  }

  /**
   * The color of a floating container (a menu, a rich tooltip) in {@code role}: with Pure black on in a dark scheme it
   * is the chip step (surfaceContainerHighest), as {@link XgramAmoled#floatingRole}, for a forced theme too.
   */
  @ColorInt
  public static int floatingColor (int role, @Nullable ThemeDelegate forcedTheme) {
    if (forcedTheme == null) {
      return XgramExpressive.color(XgramAmoled.floatingRole(role));
    }
    final boolean dark = forcedTheme.isDark();
    return XgramExpressive.colorFor(dark && XgramAmoled.isEnabled() ? XgramExpressive.SURFACE_CONTAINER_HIGHEST : role, dark);
  }

  // Feedback

  /** The M3 snackbar; a toast when Expressive design is off or no activity is resumed. */
  public static void snackbar (@Nullable CharSequence text, boolean isLong) {
    snackbar(text, isLong, null, null);
  }

  /** {@link #snackbar(CharSequence, boolean)} with an action (a tonal pill, "Retry"); the toast fallback has none. */
  public static void snackbar (@Nullable CharSequence text, boolean isLong, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
    if (TextUtils.isEmpty(text)) {
      return;
    }
    final int duration = isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
    if (!XgramSnackbar.show(text, duration, actionText, onAction)) {
      UI.showToast(text, duration);
    }
  }

  // Progress bar

  /**
   * A persistent progress bar that floats like the snackbar, for a long job (a chat export): the snackbar's container
   * ({@link XgramSnackbar#styleContainer}: the tonal card, 24dp corners, elevation level 3, 16dp from the sides, at most
   * 560dp wide), a bodyMedium line in onSurface ("1,240 messages…") over a determinate wavy linear indicator in primary
   * on a secondaryContainer track (the value on a spring), and an optional action ("Cancel") as the snackbar's tonal
   * pill at the end (secondaryContainer, labelLarge, 40dp tall in a 48dp touch target, its corners squeezing to 8dp
   * while pressed). It stays until {@link #dismiss()}, and comes and goes as the snackbar does ({@link XgramSnackbar.Motion}).
   * TalkBack reads it as a live region and offers the action as one of its actions.
   */
  @SuppressLint("ViewConstructor")
  public static final class ProgressBar extends View {
    private static final float PADDING_H = 20f, PADDING_TOP = 14f, LINE_HEIGHT = 20f, TEXT_GAP = 8f, BAR_AREA = 10f, PADDING_BOTTOM = 14f;
    private static final float THICKNESS = 4f, AMPLITUDE = 3f, WAVELENGTH = 40f, GAP = 4f;
    /** The action: a 40dp tonal pill (48dp to touch) with 16dp sides, 8dp from the end and 8dp after the indicator. */
    private static final float ACTION_HEIGHT = 40f, ACTION_TOUCH = 48f, ACTION_PADDING = 16f, ACTION_LABEL_PADDING = 10f, ACTION_END = 8f, ACTION_GAP = 8f;

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), actionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actionFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF actionRect = new RectF();
    private final XgramExpressive.Spring progress, press;
    private final XgramSnackbar.Motion motion;
    private CharSequence text = "";
    private @Nullable CharSequence shownText;
    private int shownTextWidth = -1;
    private @Nullable CharSequence actionText;
    private @Nullable Runnable onAction;
    private float actionTextWidth;
    /** The action as an accessibility action (TalkBack cannot tap the drawn pill), View.NO_ID with no action. */
    private int a11yActionId = View.NO_ID;
    private boolean actionPressed, dismissing;

    private ProgressBar (Context context) {
      super(context);
      // In sp, as the snackbar's row: a large system font grows the bar and its pill
      textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
      textPaint.setTextSize(Screen.sp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
      actionPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      actionPaint.setTextSize(Screen.sp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
      actionFill.setStyle(Paint.Style.FILL);
      XgramSnackbar.styleContainer(this, XgramSnackbar.CORNER_WRAPPED_DP); // two rows: never a pill
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
      progress = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      motion = new XgramSnackbar.Motion(this, null);
    }

    /** Shows a new bar over the resumed activity's content; null when there is none (or Expressive design is off). */
    @Nullable
    public static ProgressBar show (@NonNull CharSequence text, @Nullable CharSequence actionText, @Nullable Runnable onAction) {
      if (!XgramExpressive.isEnabled()) {
        return null;
      }
      final BaseActivity activity = UI.getUiContext();
      if (activity == null || activity.isFinishing()) {
        return null;
      }
      final View content = activity.findViewById(android.R.id.content);
      if (!(content instanceof FrameLayout)) {
        return null;
      }
      final ProgressBar bar = new ProgressBar(activity);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      params.leftMargin = params.rightMargin = Screen.dp(XgramSnackbar.SIDE_GAP_DP);
      params.bottomMargin = Screen.dp(XgramSnackbar.BOTTOM_GAP_DP) + bottomInset(content);
      bar.setText(text);
      bar.setAction(actionText, onAction);
      bar.setVisibility(View.INVISIBLE);
      ((FrameLayout) content).addView(bar, params);
      bar.setVisibility(View.VISIBLE); // TalkBack reads the live region as it appears
      bar.motion.show(true);
      return bar;
    }

    /** The navigation bar or keyboard under {@code content}, less what the content already ends above. */
    private static int bottomInset (View content) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return 0;
      }
      final WindowInsets insets = content.getRootWindowInsets();
      if (insets == null) {
        return 0;
      }
      int inset = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.ime()).bottom;
      final View root = content.getRootView();
      if (root != null) {
        final int[] location = new int[2];
        content.getLocationInWindow(location);
        inset = Math.max(0, inset - Math.max(0, root.getHeight() - (location[1] + content.getHeight())));
      }
      return inset;
    }

    public void setText (@Nullable CharSequence text) {
      final CharSequence value = text != null ? text : "";
      if (!TextUtils.equals(this.text, value)) {
        this.text = value;
        this.shownTextWidth = -1;
        setContentDescription(value);
        invalidate();
      }
    }

    private void setAction (@Nullable CharSequence text, @Nullable Runnable action) {
      if (TextUtils.isEmpty(text) || action == null) {
        actionText = null;
        onAction = null;
        actionTextWidth = 0f;
      } else {
        actionText = text;
        onAction = action;
        actionTextWidth = actionPaint.measureText(text, 0, text.length());
      }
      shownTextWidth = -1;
      if (a11yActionId != View.NO_ID) {
        ViewCompat.removeAccessibilityAction(this, a11yActionId);
        a11yActionId = View.NO_ID;
      }
      if (actionText != null) {
        a11yActionId = ViewCompat.addAccessibilityAction(this, actionText.toString(), (v, args) -> {
          runAction();
          return true;
        });
      }
      invalidate();
    }

    private void runAction () {
      final Runnable action = onAction;
      if (action != null && !dismissing) {
        action.run();
      }
    }

    /** 0..1; springs (SPATIAL_DEFAULT) to the new value, snaps with Reduce motion. */
    public void setProgress (float fraction) {
      progress.animateTo(Math.max(0f, Math.min(1f, fraction)));
    }

    /** Leaves as the snackbar does, and goes. */
    public void dismiss () {
      if (dismissing) {
        return;
      }
      dismissing = true;
      if (!motion.hide(true, this::removeNow)) {
        removeNow();
      }
    }

    private void removeNow () {
      dismissing = true;
      progress.cancel();
      press.cancel();
      motion.cancel();
      final ViewGroup parent = (ViewGroup) getParent();
      if (parent != null) {
        parent.removeView(this);
      }
    }

    private boolean isRtl () {
      return Lang.rtl();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
        width = Screen.dp(XgramSnackbar.MAX_WIDTH_DP);
      }
      width = Math.min(width, Screen.dp(XgramSnackbar.MAX_WIDTH_DP));
      setMeasuredDimension(width, Screen.dp(PADDING_TOP + TEXT_GAP + BAR_AREA + PADDING_BOTTOM) + lineHeight());
    }

    /** The text's line: 20sp (20dp at the default font size). */
    private int lineHeight () {
      return Screen.sp(LINE_HEIGHT);
    }

    /** The pill: 40dp, or the label's line and 10dp above and below it when a large font needs more. */
    private float actionHeight () {
      return Math.max(Screen.dp(ACTION_HEIGHT), Screen.sp(LINE_HEIGHT) + Screen.dp(ACTION_LABEL_PADDING * 2f));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      motion.updatePivot();
    }

    private float actionWidth () {
      return Math.max(Screen.dp(ACTION_TOUCH), actionTextWidth + Screen.dp(ACTION_PADDING * 2f));
    }

    /** The end of the text and the indicator (the action takes the end), in LTR terms before mirroring. */
    private float contentEnd () {
      float end = getWidth() - Screen.dp(PADDING_H);
      if (actionText != null) {
        end = getWidth() - Screen.dp(ACTION_END) - actionWidth() - Screen.dp(ACTION_GAP);
      }
      return end;
    }

    private void layoutAction () {
      final float width = actionWidth();
      final float height = actionHeight();
      final float top = (getHeight() - height) / 2f;
      if (isRtl()) {
        final float left = Screen.dp(ACTION_END);
        actionRect.set(left, top, left + width, top + height);
      } else {
        final float right = getWidth() - Screen.dp(ACTION_END);
        actionRect.set(right - width, top, right, top + height);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      super.onDraw(c);
      final int width = getWidth();
      if (width <= 0) {
        return;
      }
      final boolean rtl = isRtl();
      final float start = Screen.dp(PADDING_H), end = contentEnd();
      final int available = (int) Math.max(0f, end - start);
      if (shownTextWidth != available || shownText == null) {
        shownTextWidth = available;
        shownText = TextUtils.ellipsize(text, textPaint, available, TextUtils.TruncateAt.END); // only when the text or width changed
      }
      textPaint.setColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      final float lineTop = Screen.dp(PADDING_TOP);
      final float baseline = lineTop + lineHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
      final CharSequence line = shownText;
      final float textWidth = textPaint.measureText(line, 0, line.length());
      c.drawText(line, 0, line.length(), rtl ? width - start - textWidth : start, baseline, textPaint);

      final float cy = lineTop + lineHeight() + Screen.dp(TEXT_GAP + BAR_AREA / 2f);
      final float value = Math.max(0f, Math.min(1f, progress.getValue()));
      final int saveCount = c.save();
      if (rtl) {
        c.scale(-1f, 1f, width / 2f, cy);
      }
      // M3's linear indicator: primary on a secondaryContainer track
      XgramWavyLinear.draw(c, start, end, cy, value, XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER),
        Screen.dp(THICKNESS), Screen.dp(AMPLITUDE), Screen.dp(WAVELENGTH), Screen.dp(GAP), true);
      c.restoreToCount(saveCount);

      if (actionText != null) {
        layoutAction();
        // The tonal pill: its corners squeeze from round to 8dp while pressed, with the pressed state layer
        final float pressed = XgramMotion.clamp01(press.getValue());
        final float round = actionRect.height() / 2f;
        final float radius = round + (Screen.dpf(XgramExpressive.CORNER_S) - round) * pressed;
        actionFill.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
        c.drawRoundRect(actionRect, radius, radius, actionFill);
        final int onContainer = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
        if (pressed > 0f) {
          actionFill.setColor(XgramExpressive.withAlpha(onContainer, XgramExpressive.STATE_PRESSED * pressed));
          c.drawRoundRect(actionRect, radius, radius, actionFill);
        }
        actionPaint.setColor(onContainer);
        final float actionBaseline = actionRect.centerY() - (actionPaint.descent() + actionPaint.ascent()) / 2f;
        c.drawText(actionText, 0, actionText.length(), actionRect.centerX() - actionTextWidth / 2f, actionBaseline, actionPaint);
      }
      if (XgramWavyLinear.isWaveMoving(value, Screen.dp(AMPLITUDE)) && isAttachedToWindow()) {
        postInvalidateOnAnimation();
      }
    }

    /** The pill and the 4dp above and below it: a 48dp touch target. */
    private boolean inAction (MotionEvent e) {
      final float slack = Screen.dp((ACTION_TOUCH - ACTION_HEIGHT) / 2f);
      return actionText != null && e.getX() >= actionRect.left && e.getX() < actionRect.right && e.getY() >= actionRect.top - slack && e.getY() < actionRect.bottom + slack;
    }

    private void setActionPressed (boolean pressed) {
      if (actionPressed != pressed) {
        actionPressed = pressed;
        press.animateTo(pressed ? 1f : 0f);
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (dismissing || actionText == null) {
        return false;
      }
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          layoutAction();
          setActionPressed(inAction(e));
          return actionPressed;
        }
        case MotionEvent.ACTION_MOVE: {
          if (actionPressed && !inAction(e)) {
            setActionPressed(false);
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          if (actionPressed) {
            setActionPressed(false);
            if (inAction(e)) {
              runAction();
            }
          }
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          setActionPressed(false);
          return true;
        }
      }
      return super.onTouchEvent(e);
    }
  }
}
