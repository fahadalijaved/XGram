/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/03/2016 at 23:51
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.core.StringUtils;

public class OptionsLayout extends LinearLayout implements Animated, RootFrameLayout.MarginModifier {
  private final CustomTextView textView;
  private final CustomTextView headerView;
  private final ViewController<?> parent;
  private final @Nullable ThemeDelegate forcedTheme;

  public OptionsLayout (Context context, ViewController<?> parent, @Nullable ThemeDelegate forcedTheme) {
    super(context);
    this.parent = parent;
    this.forcedTheme = forcedTheme;

    setOrientation(LinearLayout.VERTICAL);

    final boolean xgramExpressive = XgramExpressive.isEnabled();

    headerView = new CustomTextView(context, parent.tdlib());
    headerView.setTextSize(18f);
    if (xgramExpressive) {
      // XGram: the sheet title in titleLarge (onSurface, regular weight), not Telegram X's bold 18sp; a shared style
      // (its size is never changed here)
      headerView.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(XgramExpressive.TS_TITLE_L, false, XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_L)));
    }
    headerView.setPadding(Screen.dp(16f), Screen.dp(xgramExpressive ? 0f : 18f), Screen.dp(16f), Screen.dp(6f));
    headerView.setTextColorId(ColorId.text);
    headerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    headerView.setMaxLineCount(1);
    headerView.setVisibility(View.GONE);
    addView(headerView);

    textView = new CustomTextView(context, parent.tdlib());
    textView.setPadding(Screen.dp(16f), Screen.dp(xgramExpressive ? 4f : 14f), Screen.dp(16f), Screen.dp(6f));
    textView.setTextColorId(ColorId.textLight);
    textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(textView);

    if (xgramExpressive) {
      ViewUtils.setBackground(this, new XgramSheetBackground());
      // XGram M3E r6: the drag handle's accessibility (BottomSheetDragHandleView): the sheet announces itself as the
      // handle, and its "Dismiss" action closes it as a drag down does (PopupLayout)
      setContentDescription(Lang.getString(org.thunderdog.challegram.R.string.XgramR6B3DragHandle));
      androidx.core.view.ViewCompat.addAccessibilityAction(this, Lang.getString(org.thunderdog.challegram.R.string.XgramR6B3Dismiss), (view, arguments) -> {
        org.thunderdog.challegram.widget.PopupLayout popup = org.thunderdog.challegram.widget.PopupLayout.parentOf(view);
        if (popup != null && !popup.isWindowHidden()) {
          popup.hideWindow(true);
          return true;
        }
        return false;
      });
    } else
    ViewUtils.setBackground(this, new Drawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        View view = getChildAt(0);
        int height = view != null ? view.getMeasuredHeight() : 0;
        if (height > 0)
          c.drawRect(0, height, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.filling) : Theme.getColor(ColorId.filling)));
      }

      @Override
      public void setAlpha (int alpha) { }

      @Override
      public void setColorFilter (@Nullable ColorFilter colorFilter) { }

      @Override
      @SuppressWarnings("deprecation")
      public int getOpacity () {
        return PixelFormat.UNKNOWN;
      }
    });

    if (forcedTheme != null) {
      textView.setForcedTheme(forcedTheme);
      headerView.setForcedTheme(forcedTheme);
    } else {
      // parent.addThemeTextColorListener(textView, ColorId.textLight);
      parent.addThemeInvalidateListener(textView);
      parent.addThemeInvalidateListener(headerView);
      parent.addThemeInvalidateListener(this);
    }
  }

  // XGram: Material 3 Expressive bottom sheet. The first child (the old top shadow slot) becomes
  // the 48dp drag handle band; the surface starts at its top with extra-large (28dp) top corners.

  public static int xgramHandleBandHeight () {
    return Screen.dp(48f);
  }

  private class XgramSheetBackground extends Drawable {
    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final RectF rect = new RectF();

    @Override
    public void draw (@NonNull Canvas c) {
      View view = getChildAt(0);
      if (view == null || view.getMeasuredHeight() <= 0) {
        return;
      }
      final float top = view.getTop() + view.getTranslationY();
      final float corner = Screen.dp(XgramExpressive.CORNER_XL);
      // Painted 24dp below the bottom, so the spring overshoot of the reveal never shows a gap.
      rect.set(0, top, getMeasuredWidth(), getMeasuredHeight() + Screen.dp(24f));
      radii[0] = radii[1] = radii[2] = radii[3] = corner;
      radii[4] = radii[5] = radii[6] = radii[7] = 0f;
      path.reset();
      path.addRoundRect(rect, radii, Path.Direction.CW);
      // XGram M3E r5: a forced theme (camera, media viewer) draws the same sheet in that theme's scheme
      // (the wheel pickers fade their rows into this same colour: XgramM3Wheel.sheetColor)
      final int surfaceColor = com.fahadali.xgram.appearance.XgramM3Wheel.sheetColor(forcedTheme);
      c.drawPath(path, Paints.fillingPaint(surfaceColor));

      final int handleColor = XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramR5Sheets.role(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme), .4f);
      final float handleWidth = Screen.dp(32f), handleHeight = Screen.dp(4f);
      final float handleTop = top + (xgramHandleBandHeight() - handleHeight) / 2f;
      final float cx = getMeasuredWidth() / 2f;
      RectF handle = Paints.getRectF();
      handle.set(cx - handleWidth / 2f, handleTop, cx + handleWidth / 2f, handleTop + handleHeight);
      c.drawRoundRect(handle, handleHeight / 2f, handleHeight / 2f, Paints.fillingPaint(handleColor));
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }

  @Override
  public void setLayoutParams (ViewGroup.LayoutParams params) {
    if (params instanceof FrameLayout.LayoutParams && XgramExpressive.isEnabled()) {
      // Max width 640dp, centered (unfolded Fold, tablets).
      FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
      frameParams.gravity = (frameParams.gravity & ~Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.CENTER_HORIZONTAL;
    }
    super.setLayoutParams(params);
  }

  @Override
  public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    Views.setMargins(params, insets.left, 0, insets.right, 0);
    Views.setPaddingBottom(this, insetsWithoutIme.bottom);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (XgramExpressive.isEnabled()) {
      final int maxWidth = Screen.dp(640f);
      if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
      }
      // XGram: never taller than the window less the status bar, so the rounded top and the handle band stay below it.
      // The option rows then scroll in their container (xgramAddRowsContainer) instead of every child moving up by the
      // overflow (below), which put the band, title and first rows above the window
      final int topOffset = HeaderView.getTopOffset();
      final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
      if (heightMode != MeasureSpec.UNSPECIFIED && topOffset > 0) {
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - topOffset), heightMode);
      }
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int currentHeight = getMeasuredHeight();
    int actualHeight = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        actualHeight += view.getMeasuredHeight();
      }
    }
    int diff = Math.min(0, currentHeight - actualHeight);
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        view.setTranslationY(diff);
      }
    }
  }

  public static @ColorId int getOptionColorId (@ViewController.OptionColor int color) {
    switch (color) {
      case ViewController.OptionColor.NORMAL:
        return ColorId.text;
      case ViewController.OptionColor.LIGHT:
        return ColorId.textLight;
      case ViewController.OptionColor.INACTIVE:
        return ColorId.controlInactive;
      case ViewController.OptionColor.RED:
        return ColorId.textNegative;
      case ViewController.OptionColor.BLUE:
        return ColorId.textNeutral;
      case ViewController.OptionColor.GREEN:
        return ColorId.iconPositive;
    }
    throw new IllegalArgumentException("color == " + color);
  }

  public static TextView genOptionView (Context context, int id, CharSequence string, @ViewController.OptionColor int textColor, int icon, @ViewController.OptionColor int iconColor, OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider, @Nullable ThemeDelegate forcedTheme) {
    EmojiTextView text = new EmojiTextView(context);
    text.setScrollDisabled(true);

    text.setId(id);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    final boolean xgramExpressive = XgramExpressive.isEnabled();
    if (xgramExpressive) {
      XgramExpressive.applyText(text, XgramExpressive.TS_BODY_L, false);
    }
    final int textColorId = getOptionColorId(textColor);
    if (forcedTheme != null && xgramExpressive) {
      // XGram M3E r5: the M3 role of the color in the forced theme's scheme
      text.setTextColor(com.fahadali.xgram.appearance.XgramR5Sheets.themeColor(forcedTheme, textColorId));
    } else if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(textColorId));
    } else if (xgramExpressive && textColor == ViewController.OptionColor.NORMAL) {
      text.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
    } else {
      text.setTextColor(Theme.getColor(textColorId));
      if (themeProvider != null)
        themeProvider.addThemeColorListener(text, textColorId);
    }
    if (forcedTheme == null && xgramExpressive && textColor == ViewController.OptionColor.NORMAL && themeProvider != null) {
      themeProvider.addThemeColorListener(text, ColorId.text); // XGram: onSurface follows theme changes (ColorId.text maps to it)
    }
    text.setOnClickListener(onClickListener);
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    if (xgramExpressive) {
      // XGram: the M3 list item spec of the message menu's tiles: 16dp padding and 16dp between icon and label
      text.setPadding(Screen.dp(16f), Screen.dp(1f), Screen.dp(16f), 0);
      text.setCompoundDrawablePadding(Screen.dp(16f));
    } else {
      text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
      text.setCompoundDrawablePadding(Screen.dp(18f));
    }
    if (icon != 0) {
      Drawable drawable = Drawables.get(context.getResources(), icon);
      if (drawable != null) {
        final int drawableColorId;
        if (iconColor == ViewController.OptionColor.NORMAL) {
          drawableColorId = ColorId.icon;
        } else if (iconColor == ViewController.OptionColor.INACTIVE) {
          drawableColorId = ColorId.controlInactive;
        } else {
          drawableColorId = getOptionColorId(iconColor);
        }
        if (forcedTheme == null && xgramExpressive && iconColor == ViewController.OptionColor.NORMAL) {
          drawable.setColorFilter(Paints.getColorFilter(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT)));
          if (themeProvider != null) {
            themeProvider.addThemeFilterListener(drawable, ColorId.icon); // XGram: onSurfaceVariant follows theme changes (ColorId.icon maps to it)
          }
        } else if (forcedTheme != null && xgramExpressive) {
          drawable.setColorFilter(Paints.getColorFilter(com.fahadali.xgram.appearance.XgramR5Sheets.themeColor(forcedTheme, drawableColorId)));
        } else {
          drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
          if (themeProvider != null) {
            themeProvider.addThemeFilterListener(drawable, drawableColorId);
          }
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    Views.setClickable(text);
    if (!StringUtils.isEmpty(string)) {
      text.setText(string);
    }

    return text;
  }

  public void setHeader (CharSequence text) {
    if (!StringUtils.isEmpty(text)) {
      if (XgramExpressive.isEnabled()) {
        headerView.setText(text, null, false); // XGram: titleLarge is regular weight
      } else {
        headerView.setBoldText(text, null, false);
      }
      headerView.setVisibility(View.VISIBLE);
    } else {
      headerView.setVisibility(View.GONE);
    }
  }

  // XGram M3E r5: header styles of the sheets (titleLarge by default; headlineSmall for hero, confirm and wheel sheets)

  /** The sheet title set with {@link #setHeader} in the type scale style {@code ts} (e.g. headlineSmall). */
  public void xgramSetHeaderStyle (int ts) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    headerView.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(ts, false, XgramExpressive.textSizeSp(ts)));
    headerView.setMaxLineCount(2);
    if (headerView.getVisibility() == View.VISIBLE) {
      headerView.setText(headerView.getText(), null, false);
    }
  }

  private int xgramTitleTs = XgramExpressive.TS_TITLE_L;

  /**
   * The style of a title shown through {@link #setInfo} with {@code isTitle} (showPopup, the wheel pickers): titleLarge
   * by default, headlineSmall for a wheel or hero sheet. Call before setInfo.
   */
  public void xgramSetTitleStyle (int ts) {
    this.xgramTitleTs = ts;
  }

  // XGram M3E: the option rows of showOptions

  /**
   * Whether two neighbouring options share a tile group: as in the message menu (XgramMessageMenu.sameMenuGroup, so the
   * message actions shown here group the same way there), and a destructive (red) option never joins a normal one.
   */
  public static boolean xgramSameGroup (@NonNull ViewController.OptionItem a, @NonNull ViewController.OptionItem b) {
    return com.fahadali.xgram.chats.XgramMessageMenu.sameMenuGroup(a.id, b.id) &&
      (a.textColor == ViewController.OptionColor.RED) == (b.textColor == ViewController.OptionColor.RED);
  }

  /**
   * The container of the option rows, added after the band, title, subtitle and info: a ScrollView, so a sheet taller
   * than the window keeps those (and its rounded top) on screen and scrolls the rows (see onMeasure).
   */
  public LinearLayout xgramAddRowsContainer () {
    ScrollView scrollView = new ScrollView(getContext());
    scrollView.setVerticalScrollBarEnabled(false);
    scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout rows = new LinearLayout(getContext());
    rows.setOrientation(LinearLayout.VERTICAL);
    scrollView.addView(rows, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(scrollView);
    return rows;
  }

  public static void updateSubtitle (EmojiTextView text, CharSequence name, @DrawableRes int icon, int color, @Nullable ThemeDelegate forcedTheme, @Nullable ViewController<?> themeProvider) {
    updateSubtitle(text, name, icon, color, color, forcedTheme, themeProvider);
  }

  public static void updateSubtitle (EmojiTextView text, CharSequence name, @DrawableRes int icon, int textColor, int iconColor, @Nullable ThemeDelegate forcedTheme, @Nullable ViewController<?> themeProvider) {
    final int textColorId = getOptionColorId(textColor);
    if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(textColorId));
    } else {
      text.setTextColor(Theme.getColor(textColorId));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(text, textColorId);
      }
    }
    final int iconColorId = getOptionColorId(iconColor);
    if (icon != 0) {
      Drawable drawable = Drawables.get(text.getContext().getResources(), icon);
      if (drawable != null) {
        final int drawableColorId = iconColor == ViewController.OptionColor.NORMAL ? ColorId.icon : iconColorId;
        drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(drawable, drawableColorId);
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    if (!StringUtils.isEmpty(name)) {
      text.setText(name);
    }
  }

  public static EmojiTextView genSubtitle (Context context) {
    EmojiTextView text = new EmojiTextView(context);
    text.setScrollDisabled(true);
    text.setMinHeight(Screen.dp(40f));
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    if (XgramExpressive.isEnabled()) {
      // XGram: bodyMedium with the sheet's 16dp side padding, as the info text under the title
      XgramExpressive.applyText(text, XgramExpressive.TS_BODY_M, false);
      text.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), 0);
    } else
    text.setPadding(Screen.dp(17f), Screen.dp(6f), Screen.dp(17f), 0);
    text.setCompoundDrawablePadding(Screen.dp(8f));
    text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return text;
  }

  public void setSubtitle (ViewController.OptionItem item) {
    setSubtitle(item.name, item.icon, item.textColor, item.iconColor);
  }

  public void setSubtitle (CharSequence name, @DrawableRes int icon, int textColor, int iconColor) {
    EmojiTextView text = genSubtitle(getContext());
    updateSubtitle(text, name, icon, textColor, iconColor, forcedTheme, parent);
    addView(text, 1);
  }

  public void setInfo (ViewController<?> context, Tdlib tdlib, CharSequence info, boolean isTitle, int maxLineCount) {
    if (!StringUtils.isEmpty(info)) {
      String str = info.toString();
      TextEntity[] parsed = TD.collectAllEntities(context, tdlib, info, false, null);
      setInfo(str, parsed, isTitle, maxLineCount);
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public void setInfo (String text, TextEntity[] entities, boolean isTitle, int maxLineCount) {
    if (!StringUtils.isEmpty(text) && XgramExpressive.isEnabled()) {
      // XGram: a title in titleLarge (onSurface), info in bodyMedium (onSurfaceVariant); shared styles, whose size is
      // never changed here
      textView.setVisibility(View.VISIBLE);
      textView.setMaxLineCount(maxLineCount);
      if (isTitle) {
        textView.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(xgramTitleTs, XgramExpressive.textSizeSp(xgramTitleTs)));
        textView.setTextColorId(ColorId.text);
        if (xgramTitleTs == XgramExpressive.TS_HEADLINE_S) {
          textView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(12f)); // the headline sits right under the handle band
        }
      } else {
        textView.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(XgramExpressive.TS_BODY_M, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
        textView.setTextColorId(ColorId.textLight);
      }
      textView.setText(text, entities, false);
      return;
    }
    if (!StringUtils.isEmpty(text)) {
      textView.setVisibility(View.VISIBLE);
      textView.setMaxLineCount(maxLineCount);
      if (isTitle) {
        textView.setBoldText(text, entities, false);
        textView.setTextSize(19f);
        textView.setTextColorId(ColorId.text);
      } else {
        textView.setText(text, entities, false);
        textView.setTextSize(15f);
        textView.setTextColorId(ColorId.textLight);
      }
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public int getTextHeight () {
    if (textView.getVisibility() == View.VISIBLE) {
      int width = UI.getContext(getContext()).getControllerWidth(textView);
      if (XgramExpressive.isEnabled()) {
        width = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(width); // XGram: the sheet is at most 640dp wide
      }
      return textView.getCurrentHeight(width);
    } else {
      return 0;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    super.onTouchEvent(event);
    return true;
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
