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
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

  /** {@link #snackbar(CharSequence, boolean)} with an action (a text button, "Retry"); the toast fallback has none. */
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
   * A persistent progress bar that floats like a snackbar, for a long job (a chat export): inverseSurface with 4dp
   * corners, a bodyMedium line ("1,240 messages…") over a determinate wavy linear indicator in inversePrimary (the
   * value on a spring), and an optional text button action ("Cancel") in inversePrimary at the end. It stays until
   * {@link #dismiss()}. It enters and leaves like the snackbar (a fade and a 24dp rise; a fade only with Reduce motion).
   */
  @SuppressLint("ViewConstructor")
  public static final class ProgressBar extends View {
    private static final float PADDING_H = 16f, PADDING_TOP = 14f, LINE_HEIGHT = 20f, TEXT_GAP = 8f, BAR_AREA = 10f, PADDING_BOTTOM = 14f;
    private static final float THICKNESS = 4f, AMPLITUDE = 3f, WAVELENGTH = 40f, GAP = 4f, MAX_WIDTH = 560f;
    private static final float ACTION_HEIGHT = 40f, ACTION_PADDING = 12f, ACTION_END = 8f, ACTION_GAP = 8f;

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), actionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF actionRect = new RectF();
    private final XgramExpressive.Spring progress, visibility;
    private CharSequence text = "";
    private @Nullable CharSequence shownText;
    private int shownTextWidth = -1;
    private @Nullable CharSequence actionText;
    private @Nullable Runnable onAction;
    private float actionTextWidth;
    private boolean actionPressed, dismissing;

    private ProgressBar (Context context) {
      super(context);
      textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
      textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
      actionPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      actionPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
      statePaint.setStyle(Paint.Style.FILL);
      XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_XS, Float.NaN);
      background.setColorRole(XgramExpressive.INVERSE_SURFACE);
      setBackground(background);
      setOutlineProvider(ViewOutlineProvider.BACKGROUND);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
      progress = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      visibility = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> applyVisibility(value));
      applyVisibility(0f);
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
      params.leftMargin = params.rightMargin = Screen.dp(XgramSnackbar.BOTTOM_GAP_DP);
      params.bottomMargin = Screen.dp(XgramSnackbar.BOTTOM_GAP_DP) + bottomInset(content);
      bar.setText(text);
      bar.setAction(actionText, onAction);
      ((FrameLayout) content).addView(bar, params);
      bar.visibility.animateTo(1f);
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
      invalidate();
    }

    /** 0..1; springs (SPATIAL_DEFAULT) to the new value, snaps with Reduce motion. */
    public void setProgress (float fraction) {
      progress.animateTo(Math.max(0f, Math.min(1f, fraction)));
    }

    /** Fades out and goes. */
    public void dismiss () {
      if (dismissing) {
        return;
      }
      dismissing = true;
      visibility.setEndListener(this::removeNow);
      visibility.animateTo(0f);
      if (visibility.getValue() == 0f && !visibility.isRunning()) {
        removeNow();
      }
    }

    private void removeNow () {
      dismissing = true;
      progress.cancel();
      visibility.cancel();
      final ViewGroup parent = (ViewGroup) getParent();
      if (parent != null) {
        parent.removeView(this);
      }
    }

    private void applyVisibility (float value) {
      setAlpha(Math.max(0f, Math.min(1f, value)));
      setTranslationY(XgramMotion.reduce() ? 0f : (1f - value) * Screen.dp(24f));
    }

    private boolean isRtl () {
      return Lang.rtl();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
        width = Screen.dp(MAX_WIDTH);
      }
      width = Math.min(width, Screen.dp(MAX_WIDTH));
      setMeasuredDimension(width, Screen.dp(PADDING_TOP + LINE_HEIGHT + TEXT_GAP + BAR_AREA + PADDING_BOTTOM));
    }

    /** The start and end of the text and the bar (the action takes the end), in LTR terms before mirroring. */
    private float contentEnd () {
      float end = getWidth() - Screen.dp(PADDING_H);
      if (actionText != null) {
        end = getWidth() - Screen.dp(ACTION_END) - actionTextWidth - Screen.dp(ACTION_PADDING * 2f) - Screen.dp(ACTION_GAP);
      }
      return end;
    }

    private void layoutAction () {
      final float width = actionTextWidth + Screen.dp(ACTION_PADDING * 2f);
      final float height = Screen.dp(ACTION_HEIGHT);
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
      final int onInverse = XgramExpressive.color(XgramExpressive.INVERSE_ON_SURFACE);
      final int inversePrimary = XgramExpressive.color(XgramExpressive.INVERSE_PRIMARY);
      textPaint.setColor(onInverse);
      final float lineTop = Screen.dp(PADDING_TOP);
      final float baseline = lineTop + Screen.dp(LINE_HEIGHT) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
      final CharSequence line = shownText;
      final float textWidth = textPaint.measureText(line, 0, line.length());
      c.drawText(line, 0, line.length(), rtl ? width - start - textWidth : start, baseline, textPaint);

      final float cy = lineTop + Screen.dp(LINE_HEIGHT + TEXT_GAP + BAR_AREA / 2f);
      final float value = Math.max(0f, Math.min(1f, progress.getValue()));
      final int saveCount = c.save();
      if (rtl) {
        c.scale(-1f, 1f, width / 2f, cy);
      }
      XgramWavyLinear.draw(c, start, end, cy, value, inversePrimary, XgramExpressive.withAlpha(onInverse, .24f),
        Screen.dp(THICKNESS), Screen.dp(AMPLITUDE), Screen.dp(WAVELENGTH), Screen.dp(GAP), true);
      c.restoreToCount(saveCount);

      if (actionText != null) {
        layoutAction();
        if (actionPressed) {
          statePaint.setColor(XgramExpressive.withAlpha(inversePrimary, XgramExpressive.STATE_PRESSED));
          final float radius = actionRect.height() / 2f;
          c.drawRoundRect(actionRect, radius, radius, statePaint);
        }
        actionPaint.setColor(inversePrimary);
        final float actionBaseline = actionRect.centerY() - (actionPaint.descent() + actionPaint.ascent()) / 2f;
        c.drawText(actionText, 0, actionText.length(), actionRect.left + Screen.dp(ACTION_PADDING), actionBaseline, actionPaint);
      }
      if (XgramWavyLinear.isWaveMoving(value, Screen.dp(AMPLITUDE)) && isAttachedToWindow()) {
        postInvalidateOnAnimation();
      }
    }

    private boolean inAction (MotionEvent e) {
      return actionText != null && e.getX() >= actionRect.left && e.getX() < actionRect.right && e.getY() >= actionRect.top - Screen.dp(4f) && e.getY() < actionRect.bottom + Screen.dp(4f);
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
          actionPressed = inAction(e);
          if (actionPressed) {
            invalidate();
          }
          return actionPressed;
        }
        case MotionEvent.ACTION_MOVE: {
          if (actionPressed && !inAction(e)) {
            actionPressed = false;
            invalidate();
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          if (actionPressed) {
            actionPressed = false;
            invalidate();
            if (inAction(e)) {
              final Runnable action = onAction;
              if (action != null) {
                action.run();
              }
            }
          }
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (actionPressed) {
            actionPressed = false;
            invalidate();
          }
          return true;
        }
      }
      return super.onTouchEvent(e);
    }
  }
}
