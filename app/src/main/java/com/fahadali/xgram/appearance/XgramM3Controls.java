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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.WeakHashMap;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Material 3 Expressive controls drawn on a Canvas (checkbox, check mark, radio button) and the M3 dialog look.
 * Values follow the Compose material3 tokens: CheckboxTokens (18dp box, 2dp corner and outline), RadioButtonTokens
 * (20dp ring, 2dp stroke, 10dp dot), DialogTokens (surfaceContainerHigh, 28dp corners). Main thread only.
 */
public final class XgramM3Controls {
  private XgramM3Controls () { }

  // Check mark of the 18dp checkbox, in dp inside the box
  private static final float[] MARK = {4.5f, 9.4f, 7.6f, 12.4f, 13.6f, 6.2f};

  private static Paint fillPaint, strokePaint;
  private static final Path boxPath = new Path(), markPath = new Path(), markSegment = new Path();
  private static final PathMeasure markMeasure = new PathMeasure();
  private static final RectF rect = new RectF();

  private static void ensurePaints () {
    if (fillPaint == null) {
      fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      fillPaint.setStyle(Paint.Style.FILL);
      strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      strokePaint.setStyle(Paint.Style.STROKE);
      strokePaint.setStrokeCap(Paint.Cap.ROUND);
      strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }
  }

  private static int role (int role) {
    return XgramExpressive.color(role);
  }

  /**
   * M3 checkbox in a {@code size} box. {@code checked} 0..1: over the first half the primary fill grows in from the
   * 2dp outline, over the second half the check mark draws in (reversed when unchecking). {@code partial} morphs the
   * mark into the indeterminate bar, {@code circle} rounds the box into a circle.
   */
  public static void drawCheckbox (Canvas c, float left, float top, float size, float checked, float partial, float disabled, float circle, float alpha) {
    drawCheckbox(c, left, top, size, checked, partial, disabled, circle, alpha, null);
  }

  /** {@link #drawCheckbox(Canvas, float, float, float, float, float, float, float, float)} in the scheme of {@code forcedTheme} (a menu over media), or of the current theme when it is null. */
  public static void drawCheckbox (Canvas c, float left, float top, float size, float checked, float partial, float disabled, float circle, float alpha, @Nullable ThemeDelegate forcedTheme) {
    if (size <= 0f || alpha <= 0f) {
      return;
    }
    ensurePaints();
    final float fill = MathUtils.clamp(checked / .5f);
    final float mark = MathUtils.clamp((checked - .5f) / .5f);
    int boxColor = ColorUtils.fromToArgb(role(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme), role(XgramExpressive.PRIMARY, forcedTheme), fill);
    if (disabled > 0f) {
      boxColor = ColorUtils.fromToArgb(boxColor, XgramExpressive.withAlpha(role(XgramExpressive.ON_SURFACE, forcedTheme), XgramExpressive.DISABLED_CONTENT), disabled);
    }
    final float stroke = Screen.dpf(2f);
    final float half = size / 2f;
    final float radius = Math.min(half, Screen.dpf(2f) + (half - Screen.dpf(2f)) * MathUtils.clamp(circle));

    boxPath.rewind();
    boxPath.setFillType(Path.FillType.EVEN_ODD);
    rect.set(left, top, left + size, top + size);
    boxPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    final float inset = stroke + (half - stroke) * fill;
    if (inset < half - .5f) {
      final float innerRadius = Math.max(0f, radius - inset);
      rect.set(left + inset, top + inset, left + size - inset, top + size - inset);
      boxPath.addRoundRect(rect, innerRadius, innerRadius, Path.Direction.CW);
    }
    fillPaint.setColor(ColorUtils.alphaColor(alpha, boxColor));
    c.drawPath(boxPath, fillPaint);

    if (mark > 0f) {
      final int markColor = ColorUtils.fromToArgb(role(XgramExpressive.ON_PRIMARY, forcedTheme), role(XgramExpressive.SURFACE, forcedTheme), disabled);
      final float inner = size * (1f - .15f * MathUtils.clamp(circle));
      final float offset = (size - inner) / 2f;
      if (partial < 1f) {
        drawCheckMark(c, left + offset, top + offset, inner, mark, ColorUtils.alphaColor(alpha * (1f - partial), markColor), stroke);
      }
      if (partial > 0f) {
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setColor(ColorUtils.alphaColor(alpha * partial, markColor));
        final float barHalf = Math.max(0f, (5f / 18f) * size - stroke / 2f) * mark;
        c.drawLine(left + half - barHalf, top + half, left + half + barHalf, top + half, strokePaint);
      }
    }
  }

  /** The M3 check mark (round caps and joins) of an 18-unit box scaled to {@code size}, drawn in up to {@code fraction}. */
  public static void drawCheckMark (Canvas c, float left, float top, float size, float fraction, @ColorInt int color, float strokePx) {
    if (fraction <= 0f || size <= 0f || (color >>> 24) == 0) {
      return;
    }
    ensurePaints();
    final float u = size / 18f;
    markPath.rewind();
    markPath.moveTo(left + MARK[0] * u, top + MARK[1] * u);
    markPath.lineTo(left + MARK[2] * u, top + MARK[3] * u);
    markPath.lineTo(left + MARK[4] * u, top + MARK[5] * u);
    strokePaint.setStrokeWidth(strokePx);
    strokePaint.setColor(color);
    if (fraction >= 1f) {
      c.drawPath(markPath, strokePaint);
      return;
    }
    markMeasure.setPath(markPath, false);
    markSegment.rewind();
    markMeasure.getSegment(0f, markMeasure.getLength() * fraction, markSegment, true);
    c.drawPath(markSegment, strokePaint);
  }

  /** M3 radio button centered on (cx, cy): 20dp ring with a 2dp stroke, and a dot of up to 10dp ({@code dot} may overshoot 1). */
  public static void drawRadio (Canvas c, float cx, float cy, @ColorInt int ringColor, @ColorInt int dotColor, float dot) {
    ensurePaints();
    final float stroke = Screen.dpf(2f);
    strokePaint.setStrokeWidth(stroke);
    strokePaint.setColor(ringColor);
    c.drawCircle(cx, cy, Screen.dpf(10f) - stroke / 2f, strokePaint);
    if (dot > 0f) {
      fillPaint.setColor(dotColor);
      c.drawCircle(cx, cy, Screen.dpf(5f) * dot, fillPaint);
    }
  }

  // Dialogs (DialogTokens)

  /**
   * True when M3E is on and {@code theme} is the theme in use. A dialog in another theme (theme previews, the media
   * viewer) takes {@link #styleAlert(Context, AlertDialog, ThemeDelegate)} with that theme instead.
   */
  public static boolean dialogsEnabled (@Nullable ThemeDelegate theme) {
    return XgramExpressive.isEnabled() && (theme == null || theme.getId() == ThemeManager.instance().currentThemeId());
  }

  /**
   * M3 basic dialog: surfaceContainerHigh with 28dp corners, headlineSmall title in onSurface, bodyMedium message in
   * onSurfaceVariant, sentence case labelLarge text buttons in primary with a pill state layer, bodyLarge list items.
   */
  public static void styleAlert (Context context, AlertDialog dialog) {
    styleAlert(context, dialog, null);
  }

  /** The window dim behind an M3 dialog (the modal scrim: black at 32%). */
  public static final float DIALOG_DIM = .32f;
  /** DialogTokens: 24dp padding; 8dp between the action buttons. */
  private static final float DIALOG_PADDING = 24f, BUTTON_GAP = 8f;

  /**
   * {@link #styleAlert(Context, AlertDialog)} in the light or dark scheme of {@code forcedTheme} (a dialog over the
   * media viewer or a theme preview, whose theme is not the one in use), or of the current theme when it is null.
   * Message links and the selection are primary, the window dims to 32%, the action buttons are end aligned 8dp apart
   * 24dp from the edges, and every color is an M3 role (no Telegram X blue from the dialog theme).
   */
  public static void styleAlert (Context context, AlertDialog dialog, @Nullable ThemeDelegate forcedTheme) {
    final Window window = dialog.getWindow();
    if (window != null) {
      // Resolved now, like the text and button colours below: the window and its content stay in the scheme the
      // dialog opened in (a day / night switch while it is open repaints neither)
      GradientDrawable fixed = new GradientDrawable();
      fixed.setColor(role(XgramExpressive.SURFACE_CONTAINER_HIGH, forcedTheme));
      fixed.setCornerRadius(Screen.dp(XgramExpressive.CORNER_XL));
      final Drawable shape = fixed;
      window.setBackgroundDrawable(new InsetDrawable(shape, Screen.dp(WINDOW_INSET)));
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setDimAmount(DIALOG_DIM);
      // the content inside the 28dp corners (the Material date and time pickers' accent header is square)
      window.getDecorView().setClipToOutline(true);
      capWidth(window);
    }

    final int onSurface = role(XgramExpressive.ON_SURFACE, forcedTheme);
    final int onSurfaceVariant = role(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme);
    final int primary = role(XgramExpressive.PRIMARY, forcedTheme);

    styleText(dialog.findViewById(android.R.id.title), XgramExpressive.TS_HEADLINE_S, onSurface);
    styleText(Views.tryFindAndroidView(context, dialog, "alertTitle"), XgramExpressive.TS_HEADLINE_S, onSurface);
    View message = dialog.findViewById(android.R.id.message);
    styleText(message, XgramExpressive.TS_BODY_M, onSurfaceVariant);
    if (message instanceof TextView) {
      // links in primary, pressed with a primary state layer (.10, like ColorId.textLinkPressHighlight)
      ((TextView) message).setLinkTextColor(primary);
      ((TextView) message).setHighlightColor(XgramExpressive.withAlpha(primary, XgramExpressive.STATE_PRESSED));
    }
    View alertTitle = Views.tryFindAndroidView(context, dialog, "alertTitle");
    if (alertTitle instanceof TextView) {
      // DialogTitle drops an ellipsized title to the 18sp medium style on two lines: an M3 headline wraps instead
      ((TextView) alertTitle).setSingleLine(false);
      ((TextView) alertTitle).setMaxLines(4);
    }

    final int[] which = {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE};
    final int[] fallbackIds = {android.R.id.button1, android.R.id.button3, android.R.id.button2};
    ViewGroup buttonBar = null;
    for (int i = 0; i < which.length; i++) {
      View button = dialog.getButton(which[i]);
      if (button == null) {
        button = dialog.findViewById(fallbackIds[i]);
      }
      // a caller that made a button red before (Telegram X's textNegative, or the error role) keeps it destructive
      final boolean destructive = button instanceof TextView && isNegativeColor(((TextView) button).getCurrentTextColor());
      styleButton(button, destructive, forcedTheme);
      if (button != null && button.getVisibility() == View.VISIBLE) {
        if (buttonBar == null && button.getParent() instanceof ViewGroup) {
          buttonBar = (ViewGroup) button.getParent();
        }
        // 8dp between the neutral, negative and positive buttons (the neutral one sits at the start)
        if (which[i] != DialogInterface.BUTTON_NEUTRAL && button.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
          ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) button.getLayoutParams();
          params.setMarginStart(Screen.dp(BUTTON_GAP));
          button.setLayoutParams(params);
        }
      }
    }
    normalizeLayout(context, dialog, buttonBar);

    final ListView list = dialog.getListView();
    if (list != null) {
      list.setDivider(null);
      list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
        @Override
        public void onChildViewAdded (View parent, View child) {
          styleListItem(child, forcedTheme);
        }

        @Override
        public void onChildViewRemoved (View parent, View child) { }
      });
      for (int i = 0; i < list.getChildCount(); i++) {
        styleListItem(list.getChildAt(i), forcedTheme);
      }
    }

    if (window != null) {
      styleInputs(window.getDecorView(), primary);
    }
  }

  @ColorInt
  private static int role (int role, @Nullable ThemeDelegate forcedTheme) {
    return forcedTheme != null ? XgramExpressive.colorFor(role, forcedTheme.isDark()) : XgramExpressive.color(role);
  }

  // Dialog shell (M3E round 6): the window width, the platform layout's metrics, the hero icon and the filled action

  /** The window background's inset around the 28dp container: the window is this much wider than the dialog on each side. */
  private static final float WINDOW_INSET = 16f;
  /** DialogTokens: the container is at most 560dp wide, at least 280dp where it fits, and keeps 24dp from the screen's sides. */
  private static final float DIALOG_MIN_WIDTH = 280f, DIALOG_MAX_WIDTH = 560f, SCREEN_MARGIN = 24f;
  /** 16dp between the headline (or the hero icon) and the supporting text. */
  private static final float TITLE_GAP = 16f;

  /** The window width that makes the container min(560dp, screen width - 48dp) wide, for a screen {@code screenWidthPx} wide. */
  public static int dialogWindowWidth (int screenWidthPx) {
    final int inset = Screen.dp(WINDOW_INSET);
    int container = Math.min(Screen.dp(DIALOG_MAX_WIDTH), screenWidthPx - Screen.dp(SCREEN_MARGIN * 2f));
    container = Math.max(container, Math.min(Screen.dp(DIALOG_MIN_WIDTH), screenWidthPx - inset * 2));
    return Math.max(0, Math.min(screenWidthPx, container + inset * 2));
  }

  private static final WeakHashMap<Window, WidthCap> widthCaps = new WeakHashMap<>();

  /**
   * Keeps a dialog window at {@link #dialogWindowWidth}: set now and again on any layout pass after the screen width
   * changed (folding or unfolding with the dialog open). Holds the window weakly; no allocation per pass.
   */
  private static final class WidthCap implements ViewTreeObserver.OnGlobalLayoutListener {
    private final java.lang.ref.WeakReference<Window> window;
    private int lastScreenWidthDp = -1;

    WidthCap (Window window) {
      this.window = new java.lang.ref.WeakReference<>(window);
    }

    void apply (boolean force) {
      final Window window = this.window.get();
      if (window == null) {
        return;
      }
      final int screenWidthDp = window.getContext().getResources().getConfiguration().screenWidthDp;
      if (!force && screenWidthDp == lastScreenWidthDp) {
        return;
      }
      lastScreenWidthDp = screenWidthDp;
      if (screenWidthDp <= 0) {
        return;
      }
      final int width = dialogWindowWidth(Screen.dp(screenWidthDp));
      if (width > 0 && window.getAttributes().width != width) {
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
      }
    }

    @Override
    public void onGlobalLayout () {
      apply(false);
    }
  }

  private static void capWidth (Window window) {
    WidthCap cap = widthCaps.get(window);
    if (cap == null) {
      cap = new WidthCap(window);
      widthCaps.put(window, cap);
      window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(cap);
    }
    cap.apply(true);
  }

  private static boolean isShown (@Nullable View view) {
    return view != null && view.getVisibility() == View.VISIBLE;
  }

  private static void hide (@Nullable View view) {
    if (view != null && view.getVisibility() != View.GONE) {
      view.setVisibility(View.GONE);
    }
  }

  private static void setHeight (@Nullable View view, int height) {
    ViewGroup.LayoutParams params = view != null ? view.getLayoutParams() : null;
    if (params != null && params.height != height) {
      params.height = height;
      view.setLayoutParams(params);
    }
  }

  /**
   * M3 metrics on the platform alert layout (alert_dialog_material with the XgramDialogM3 themes, Holo for a dialog
   * still built with an old theme): 24dp around the content, 16dp under the headline, 24dp above the actions, no
   * 48 / 64dp minimum heights and no hairlines; in the Holo tree also wrap-content buttons at the end, the neutral one
   * at the start.
   */
  private static void normalizeLayout (Context context, AlertDialog dialog, @Nullable ViewGroup buttonBar) {
    final int pad = Screen.dp(DIALOG_PADDING);
    final boolean material = Views.tryFindAndroidView(context, dialog, "textSpacerNoTitle") != null;
    final View topPanel = Views.tryFindAndroidView(context, dialog, "topPanel");
    final View contentPanel = Views.tryFindAndroidView(context, dialog, "contentPanel");
    final View customPanel = Views.tryFindAndroidView(context, dialog, "customPanel");
    final View titleView = Views.tryFindAndroidView(context, dialog, "alertTitle");
    final boolean hasTitle = isShown(topPanel) && isShown(titleView);
    final boolean hasBody = isShown(contentPanel) || isShown(customPanel);
    final boolean hasButtons = buttonBar != null;

    final View titleTemplate = Views.tryFindAndroidView(context, dialog, "title_template");
    if (titleTemplate != null) {
      titleTemplate.setPaddingRelative(pad, pad, pad, hasBody ? Screen.dp(TITLE_GAP) : 0);
      titleTemplate.setMinimumHeight(0);
    }
    hide(Views.tryFindAndroidView(context, dialog, "titleDividerNoCustom"));
    hide(Views.tryFindAndroidView(context, dialog, "titleDivider"));
    hide(Views.tryFindAndroidView(context, dialog, "titleDividerTop"));
    if (topPanel != null) {
      topPanel.setMinimumHeight(0);
    }
    if (contentPanel != null) {
      contentPanel.setMinimumHeight(0);
    }
    if (customPanel != null) {
      customPanel.setMinimumHeight(0);
    }
    setHeight(Views.tryFindAndroidView(context, dialog, "textSpacerNoTitle"), pad);
    setHeight(Views.tryFindAndroidView(context, dialog, "textSpacerNoButtons"), pad);

    final View message = dialog.findViewById(android.R.id.message);
    if (message != null) {
      if (material) {
        message.setPaddingRelative(pad, 0, pad, 0);
      } else {
        View scrollView = Views.tryFindAndroidView(context, dialog, "scrollView");
        if (scrollView != null) {
          scrollView.setPadding(0, 0, 0, 0);
        }
        message.setPaddingRelative(pad, hasTitle ? 0 : pad, pad, hasButtons ? 0 : pad);
      }
    }

    if (buttonBar != null) {
      final View buttonPanel = Views.tryFindAndroidView(context, dialog, "buttonPanel");
      if (buttonPanel != null && buttonPanel != buttonBar) {
        buttonPanel.setPadding(0, 0, 0, 0);
        buttonPanel.setMinimumHeight(0);
        if (buttonPanel instanceof LinearLayout) {
          ((LinearLayout) buttonPanel).setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        }
      }
      // the 40dp containers (in 48dp touch targets, 4dp above and below) 24dp from the body, the sides and the bottom
      final int vertical = pad - Screen.dp(4f);
      buttonBar.setPaddingRelative(pad, vertical, pad, vertical);
      buttonBar.setMinimumHeight(0);
      if (!material && buttonBar instanceof LinearLayout) {
        normalizeHoloButtons((LinearLayout) buttonBar, dialog);
      }
    }
    if (!material) {
      final View parentPanel = Views.tryFindAndroidView(context, dialog, "parentPanel");
      if (parentPanel != null) {
        parentPanel.setPadding(0, 0, 0, 0);
      }
    }
  }

  private static final String HOLO_SPACER_TAG = "xgramR6B3HoloSpacer";

  /** Holo's equal-width split buttons: wrap content at the end, the neutral one at the start, no dividers. */
  private static void normalizeHoloButtons (LinearLayout bar, AlertDialog dialog) {
    bar.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
    bar.setMeasureWithLargestChildEnabled(false);
    bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    View spacer = null;
    for (int i = 0; i < bar.getChildCount(); i++) {
      View child = bar.getChildAt(i);
      if (HOLO_SPACER_TAG.equals(child.getTag())) {
        spacer = child;
        continue;
      }
      if (child instanceof TextView) {
        if (child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
          LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
          params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
          params.weight = 0f;
          child.setLayoutParams(params);
        }
      } else {
        hide(child); // leftSpacer / rightSpacer
      }
    }
    View neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
    if (neutral != null && neutral.getParent() == bar && isShown(neutral)) {
      if (bar.indexOfChild(neutral) != 0) {
        bar.removeView(neutral);
        bar.addView(neutral, 0);
      }
      if (spacer == null) {
        spacer = new View(bar.getContext());
        spacer.setTag(HOLO_SPACER_TAG);
        bar.addView(spacer, 1, new LinearLayout.LayoutParams(0, 1, 1f));
      }
    } else if (spacer != null) {
      hide(spacer);
    }
  }

  /**
   * An M3 dialog with a hero icon: the 24dp icon in secondary centred above a centred headline, 16dp between them.
   * Call on a shown dialog that has a title. Nothing with Expressive design off.
   */
  public static void heroIcon (@Nullable AlertDialog dialog, @DrawableRes int iconRes) {
    if (dialog == null || iconRes == 0 || !XgramExpressive.isEnabled()) {
      return;
    }
    final Context context = dialog.getContext();
    final View template = Views.tryFindAndroidView(context, dialog, "title_template");
    final View iconView = dialog.findViewById(android.R.id.icon);
    final View title = Views.tryFindAndroidView(context, dialog, "alertTitle");
    if (!(template instanceof LinearLayout) || !(iconView instanceof ImageView) || !(title instanceof TextView) || iconView.getParent() != template) {
      return;
    }
    final LinearLayout column = (LinearLayout) template;
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(Gravity.CENTER_HORIZONTAL);
    final ImageView icon = (ImageView) iconView;
    android.graphics.drawable.Drawable drawable = Drawables.get(context.getResources(), XgramIconPack.map(iconRes));
    if (drawable != null) {
      drawable.setColorFilter(Paints.getColorFilter(role(XgramExpressive.SECONDARY)));
    }
    icon.setImageDrawable(drawable);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setVisibility(View.VISIBLE);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f));
    iconParams.bottomMargin = Screen.dp(TITLE_GAP);
    icon.setLayoutParams(iconParams);
    final TextView titleText = (TextView) title;
    titleText.setGravity(Gravity.CENTER_HORIZONTAL);
    titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    titleText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /**
   * Makes button {@code which} of a shown M3 dialog the filled one (the confirming action, "Download"): a 40dp primary
   * pill (error when {@code destructive}) whose corners morph to 8dp while pressed, labelLarge in onPrimary, 16dp side
   * padding; onSurface at .12 / .38 while disabled. Nothing with Expressive design off.
   */
  public static void fillButton (@Nullable AlertDialog dialog, int which, boolean destructive) {
    if (dialog == null || !XgramExpressive.isEnabled()) {
      return;
    }
    styleFilledButton(dialog.getButton(which), destructive, null);
  }

  /** The filled dialog action on {@code view} (see {@link #fillButton}), in the scheme of {@code forcedTheme} when it is set. */
  public static void styleFilledButton (@Nullable View view, boolean destructive, @Nullable ThemeDelegate forcedTheme) {
    if (!(view instanceof TextView)) {
      return;
    }
    final TextView button = (TextView) view;
    button.setAllCaps(false);
    button.setPaintFlags(button.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG);
    XgramExpressive.applyText(button, XgramExpressive.TS_LABEL_L, false);
    final int content = role(destructive ? XgramExpressive.ON_ERROR : XgramExpressive.ON_PRIMARY, forcedTheme);
    final int disabled = XgramExpressive.withAlpha(role(XgramExpressive.ON_SURFACE, forcedTheme), XgramExpressive.DISABLED_CONTENT);
    button.setTextColor(new ColorStateList(
      new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
      new int[] {disabled, content}
    ));
    button.setMinHeight(Screen.dp(48f));
    button.setMinimumHeight(Screen.dp(48f));
    button.setMinWidth(Screen.dp(48f));
    button.setMinimumWidth(Screen.dp(48f));
    button.setPadding(Screen.dp(16f), button.getPaddingTop(), Screen.dp(16f), button.getPaddingBottom());
    final FilledContainer container = new FilledContainer(destructive ? XgramExpressive.ERROR : XgramExpressive.PRIMARY, forcedTheme);
    container.setHost(button);
    final int inset = Screen.dp(4f);
    button.setBackground(new RippleDrawable(ColorStateList.valueOf(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED)),
      new InsetDrawable(container, 0, inset, 0, inset), new InsetDrawable(container.newMask(), 0, inset, 0, inset)));
  }

  /** The container of a filled dialog action: its role, or onSurface at .12 while disabled; read at draw time. */
  private static final class FilledContainer extends XgramExpressive.MorphDrawable {
    private final int containerRole;
    private final @Nullable ThemeDelegate forcedTheme;
    private boolean enabled = true;

    FilledContainer (int containerRole, @Nullable ThemeDelegate forcedTheme) {
      super(0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      this.containerRole = containerRole;
      this.forcedTheme = forcedTheme;
    }

    @Override
    protected boolean onStateChange (int[] state) {
      boolean isEnabled = false;
      for (int s : state) {
        if (s == android.R.attr.state_enabled) {
          isEnabled = true;
          break;
        }
      }
      final boolean changed = isEnabled != enabled;
      enabled = isEnabled;
      final boolean result = super.onStateChange(state);
      if (changed) {
        invalidateSelf();
      }
      return result || changed;
    }

    @Override
    public int getDrawColor () {
      return enabled ? role(containerRole, forcedTheme) : XgramExpressive.withAlpha(role(XgramExpressive.ON_SURFACE, forcedTheme), XgramExpressive.DISABLED_CONTAINER);
    }
  }

  private static boolean isNegativeColor (@ColorInt int color) {
    try {
      return (color | 0xff000000) == (Theme.getColor(ColorId.textNegative) | 0xff000000) ||
        (color | 0xff000000) == (XgramExpressive.color(XgramExpressive.ERROR) | 0xff000000);
    } catch (Throwable t) {
      return false;
    }
  }

  /** Text fields inside a dialog (a custom view): the cursor, the selection handles and the selection in primary. */
  private static void styleInputs (@Nullable View view, @ColorInt int primary) {
    if (view instanceof EditText) {
      EditText editText = (EditText) view;
      // primary at .40, like ColorId.textSelectionHighlight
      editText.setHighlightColor(XgramExpressive.withAlpha(primary, .40f));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        tint(editText.getTextCursorDrawable(), primary);
        tint(editText.getTextSelectHandle(), primary);
        tint(editText.getTextSelectHandleLeft(), primary);
        tint(editText.getTextSelectHandleRight(), primary);
      }
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        styleInputs(group.getChildAt(i), primary);
      }
    }
  }

  private static void tint (@Nullable Drawable drawable, @ColorInt int color) {
    if (drawable != null) {
      drawable.mutate().setTint(color);
    }
  }

  private static void styleText (@Nullable View view, int ts, @ColorInt int color) {
    if (view instanceof TextView) {
      TextView textView = (TextView) view;
      textView.setPaintFlags(textView.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG);
      XgramExpressive.applyText(textView, ts, false);
      textView.setTextColor(color);
    }
  }

  private static void styleListItem (@Nullable View child, @Nullable ThemeDelegate forcedTheme) {
    TextView textView = null;
    if (child instanceof TextView) {
      textView = (TextView) child;
    } else if (child != null) {
      View text = child.findViewById(android.R.id.text1);
      if (text instanceof TextView) {
        textView = (TextView) text;
      }
    }
    if (textView != null) {
      XgramExpressive.applyText(textView, XgramExpressive.TS_BODY_L, false);
      textView.setTextColor(role(XgramExpressive.ON_SURFACE, forcedTheme));
      if (textView instanceof CheckedTextView) {
        // single and multiple choice lists: the radio / check in primary, onSurfaceVariant while unchecked
        final ColorStateList tint = new ColorStateList(
          new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
          new int[] {role(XgramExpressive.PRIMARY, forcedTheme), role(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme)}
        );
        ((CheckedTextView) textView).setCheckMarkTintList(tint);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          // the Material list rows draw the radio / check as their start drawable
          textView.setCompoundDrawableTintList(tint);
        }
      }
      // M3 one-line list items: 56dp
      if (textView.getMinHeight() < Screen.dp(56f)) {
        textView.setMinHeight(Screen.dp(56f));
      }
    }
  }

  /** M3 text button: sentence case labelLarge, primary (error when the caller made it red), 40dp pill state layer. */
  public static void styleButton (@Nullable View view) {
    styleButton(view, false, null);
  }

  /** {@link #styleButton(View)}, in error when {@code destructive}. */
  public static void styleButton (@Nullable View view, boolean destructive) {
    styleButton(view, destructive, null);
  }

  /**
   * The M3 text button of a dialog: labelLarge in primary (error when {@code destructive}), onSurface at .38 while
   * disabled (a ColorStateList, so a button the caller enables later, as the call rating's OK, changes with it), a 40dp
   * pill state layer inside a 48dp touch target, 12dp side padding.
   */
  public static void styleButton (@Nullable View view, boolean destructive, @Nullable ThemeDelegate forcedTheme) {
    if (!(view instanceof TextView)) {
      return;
    }
    TextView button = (TextView) view;
    button.setAllCaps(false);
    button.setPaintFlags(button.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG);
    XgramExpressive.applyText(button, XgramExpressive.TS_LABEL_L, false);
    final int content = role(destructive ? XgramExpressive.ERROR : XgramExpressive.PRIMARY, forcedTheme);
    final int disabled = XgramExpressive.withAlpha(role(XgramExpressive.ON_SURFACE, forcedTheme), XgramExpressive.DISABLED_CONTENT);
    button.setTextColor(new ColorStateList(
      new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
      new int[] {disabled, content}
    ));
    button.setMinHeight(Screen.dp(48f));
    button.setMinimumHeight(Screen.dp(48f));
    button.setMinWidth(Screen.dp(48f));
    button.setMinimumWidth(Screen.dp(48f));
    button.setPadding(Screen.dp(12f), button.getPaddingTop(), Screen.dp(12f), button.getPaddingBottom());
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(Screen.dp(100f));
    int ripple = XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED);
    button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), null, new InsetDrawable(mask, 0, Screen.dp(4f), 0, Screen.dp(4f))));
  }
}
