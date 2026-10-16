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
 * File created on 16/05/2015 at 16:02
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Controls;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class MenuMoreWrap extends MenuMoreWrapAbstract implements Animated {
  public static final int ITEM_HEIGHT = 48;
  public static final int PADDING = 8;

  public static final float START_SCALE = .56f;

  public static final long REVEAL_DURATION = 258l;
  public static final Interpolator REVEAL_INTERPOLATOR = AnimatorUtils.DECELERATE_INTERPOLATOR;

  public static final int ANCHOR_MODE_RIGHT = 0;
  public static final int ANCHOR_MODE_HEADER = 1;
  public static final int ANCHOR_MODE_CENTER = 2;

  // private int currentWidth;
  private int anchorMode;

  private @Nullable ThemeListenerList themeListeners;
  private @Nullable ThemeDelegate forcedTheme;
  private final ComplexReceiver complexAvatarReceiver;

  private final LinearLayout itemsLayout;

  public MenuMoreWrap (Context context) {
    this(context, false);
  }

  public MenuMoreWrap (Context context, boolean scrollable) {
    super(context);
    setWillNotDraw(false);
    complexAvatarReceiver = new ComplexReceiver(this);
    factorAnimator.forceFactor(-1);
    if (scrollable) {
      itemsLayout = new LinearLayout(context);
      itemsLayout.setOrientation(LinearLayout.VERTICAL);
      ScrollView scrollView = new ScrollView(context);
      scrollView.addView(itemsLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      scrollView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addView(scrollView);
    } else {
      itemsLayout = this;
    }
  }

  public void updateDirection () {
    if (Views.setGravity(this, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
      Views.updateLayoutParams(this);
  }

  public void init (@Nullable ThemeListenerList themeProvider) {
    init(themeProvider, /* forceTheme */ null);
  }

  public void init (@Nullable ThemeListenerList themeProvider, @Nullable ThemeDelegate forcedTheme) {
    this.themeListeners = themeProvider;
    this.forcedTheme = forcedTheme;

    setMinimumWidth(Screen.dp(196f));
    if (XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive menu container: surfaceContainerLow, large (16dp) corners,
      // elevation level 2, 8dp vertical padding inside the container. The 8dp side insets keep
      // the old position on screen.
      XgramExpressive.MorphDrawable container = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_L, XgramExpressive.CORNER_L);
      if (forcedTheme != null) {
        container.setColor(forcedTheme.getColor(ColorId.overlayFilling));
      } else {
        container.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER_LOW);
      }
      final int inset = Screen.dp(PADDING);
      ViewUtils.setBackground(this, new InsetDrawable(container, inset, 0, inset, 0));
      setPadding(inset, inset, inset, inset);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
      if (themeProvider != null && forcedTheme == null) {
        themeProvider.addThemeInvalidateListener(this);
      }
      setOrientation(VERTICAL);
      setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
      setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));
      return;
    }
    Drawable drawable;
    if (forcedTheme != null) {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(forcedTheme.getColor(ColorId.overlayFilling), PorterDuff.Mode.MULTIPLY));
    } else {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.headerFloatBackgroundColor(), PorterDuff.Mode.MULTIPLY));
    }
    ViewUtils.setBackground(this, drawable);

    if (themeProvider != null && forcedTheme == null) {
      themeProvider.addThemeSpecialFilterListener(drawable, ColorId.overlayFilling);
      themeProvider.addThemeInvalidateListener(this);
    }

    setOrientation(VERTICAL);
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));
  }

  public void setRightNumber (int number) {
    setTranslationX(-Screen.dp(49f) * number);
  }

  public void setAnchorMode (int anchorMode) {
    if (this.anchorMode != anchorMode) {
      this.anchorMode = anchorMode;
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) getLayoutParams();
      switch (anchorMode) {
        case ANCHOR_MODE_RIGHT: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
          break;
        }
        case ANCHOR_MODE_CENTER: {
          params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
          break;
        }
        case ANCHOR_MODE_HEADER: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
          setTranslationX(Lang.rtl() ? -Screen.dp(46f) : Screen.dp(46f));
          break;
        }
      }
    }
  }

  @Override
  public int getAnchorMode () {
    return anchorMode;
  }

  public void updateItem (int index, int id, CharSequence title, int icon, OnClickListener listener, @Nullable ThemeListenerList themeProvider) {
    TextView menuItem = (TextView) getChildAt(index);
    menuItem.setId(id);
    menuItem.setOnClickListener(listener);
    menuItem.setText(title);
    Drawable drawable = icon != 0 ? Drawables.get(getResources(), icon) : null;
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setVisibility(View.VISIBLE);
    if (XgramExpressive.isEnabled() && forcedTheme == null) {
      // XGram: the label and the press state layer in the current theme's onSurface (the menu is reused for the header's
      // lifetime: the theme may have changed since this row was created)
      menuItem.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      if (menuItem.getBackground() instanceof RippleDrawable) {
        ((RippleDrawable) menuItem.getBackground()).setColor(ColorStateList.valueOf(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.STATE_PRESSED)));
      }
    }
    if (drawable != null) {
      drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
      if (themeProvider != null) {
        themeProvider.addThemeFilterListener(drawable, ColorId.icon);
      }
      if (Drawables.needMirror(icon)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
      }
    } else {
      menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
  }

  public View addItem (@Nullable Tdlib tdlib, HapticMenuHelper.MenuItem menuItem, OnClickListener listener) {
    if (!menuItem.isCheckbox && ((StringUtils.isEmpty(menuItem.subtitle) && menuItem.messageSenderId == null) || tdlib == null)) {
      return addItem(menuItem.id, menuItem.title, menuItem.iconResId, menuItem.icon, listener);
    }

    final int maxWidth = Screen.dp(250);
    // XGram: Material 3 menu item spec with M3E on: 24dp icon 12dp from the start, label 12dp after it, 12dp trailing padding.
    final boolean xgramM3Layout = XgramExpressive.isEnabled();
    final int textRightOffset = xgramM3Layout ? Screen.dp(menuItem.isLocked ? 40 : 12) : Screen.dp(menuItem.isLocked ? 41 : 17);
    final int iconCenterX = Screen.dp(xgramM3Layout ? 24f : 29f);
    final int textStartX = Screen.dp(xgramM3Layout ? 48f : 59f);
    final int lockEndOffset = Screen.dp(xgramM3Layout ? 12 + 16 : 17 + 16);
    final Drawable finalIcon = menuItem.iconResId != 0 ? Drawables.get(getResources(), menuItem.iconResId) : menuItem.icon;
    final AvatarReceiver receiver = (menuItem.messageSenderId != null && menuItem.iconResId == 0) ?
      complexAvatarReceiver.getAvatarReceiver(Td.getSenderId(menuItem.messageSenderId)) : null;
    if (receiver != null) {
      receiver.requestMessageSender(tdlib, menuItem.messageSenderId, AvatarReceiver.Options.NONE);
    }

    FrameLayout.LayoutParams lp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT), Gravity.LEFT);
    final TextView[] titleTextItemRef = new TextView[1];
    FrameLayout frameLayout = new FrameLayoutFix(getContext()) {
      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        if (menuItem.isCheckbox && XgramExpressive.isEnabled()) {
          // XGram: checked items sit on a secondaryContainer pill (the standard menu colors; medium corners, 4dp inset).
          float checkedFactor = menuItem.isCheckboxSelectedAnimated != null ? menuItem.isCheckboxSelectedAnimated.getFloatValue() : menuItem.isCheckboxSelected ? 1f : 0f;
          if (forcedTheme == null) {
            int textColor = ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), checkedFactor);
            if (titleTextItemRef[0] != null && titleTextItemRef[0].getCurrentTextColor() != textColor) {
              titleTextItemRef[0].setTextColor(textColor);
            }
          }
          if (checkedFactor > 0f) {
            int pillColor = forcedTheme != null ? ColorUtils.alphaColor(.12f, forcedTheme.getColor(ColorId.text)) : XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
            xgramDrawItemPill(canvas, getMeasuredWidth(), getMeasuredHeight(), ColorUtils.alphaColor(checkedFactor, pillColor));
          }
        }
        if (receiver != null) {
          receiver.draw(canvas);
        }

        if (menuItem.isLocked) {
          Drawable icon = Drawables.get(getResources(), R.drawable.baseline_lock_16);
          if (icon != null) {
            float x = getMeasuredWidth() - lockEndOffset;
            float y = (getMeasuredHeight() - icon.getMinimumHeight()) / 2f;
            Drawables.draw(canvas, icon, x, y, PorterDuffPaint.get(ColorId.text));
          }
        }

        if (menuItem.isCheckbox) {
          int fillingColor, checkColor, outlineColor;
          if (forcedTheme != null) {
            fillingColor = forcedTheme.getColor(ColorId.checkActive);
            checkColor = forcedTheme.getColor(ColorId.checkContent);
            outlineColor = forcedTheme.getColor(ColorId.icon);
          } else {
            fillingColor = Theme.checkFillingColor();
            checkColor = Theme.checkCheckColor();
            outlineColor = Theme.getColor(ColorId.icon);
          }

          if (forcedTheme == null && XgramExpressive.isEnabled()) {
            // XGram M3E: Material 3 checkbox
            float boxSize = Screen.dp(18f);
            float checked = menuItem.isCheckboxSelectedAnimated != null ? menuItem.isCheckboxSelectedAnimated.getFloatValue() : menuItem.isCheckboxSelected ? 1f : 0f;
            XgramM3Controls.drawCheckbox(canvas, iconCenterX - boxSize / 2f, getMeasuredHeight() / 2f - boxSize / 2f, boxSize, checked, 0f, 0f, 0f, 1f);
            return;
          }

          // TODO move into SimplestCheckbox
          int checkBoxCenterX = iconCenterX;
          int progressCy = getMeasuredHeight() / 2;
          int progressRadius = Screen.dp(9f);
          RectF rectF = Paints.getRectF();
          progressRadius -= Screen.dp(1f);
          rectF.set(checkBoxCenterX - progressRadius, progressCy - progressRadius, checkBoxCenterX + progressRadius, progressCy + progressRadius);
          int outlineRadius = Screen.dp(3f);
          canvas.drawRoundRect(rectF, outlineRadius, outlineRadius, Paints.getProgressPaint(outlineColor, Screen.dp(1f)));

          float selectionFactor = menuItem.isCheckboxSelectedAnimated != null ?
            menuItem.isCheckboxSelectedAnimated.getFloatValue() :
            menuItem.isCheckboxSelected ? 1f :
            0f;

          SimplestCheckBox.draw(
            canvas,
            iconCenterX, getMeasuredHeight() / 2,
            selectionFactor, null, null,
            fillingColor, checkColor,
            SimplestCheckBox.MODE_NORMAL, 1f
          );
        } else if (finalIcon != null) {
          canvas.save();
          canvas.translate(iconCenterX - finalIcon.getMinimumWidth() / 2f, (getMeasuredHeight() - finalIcon.getMinimumHeight()) / 2f);
          if (menuItem.iconResId == 0) {
            finalIcon.draw(canvas);
          } else {
            Drawables.draw(canvas, finalIcon, 0, 0, PorterDuffPaint.get(ColorId.icon));
          }
          canvas.restore();
        }
      }
    };

    frameLayout.setLayoutParams(lp);
    frameLayout.setId(menuItem.id);
    if (menuItem.isCheckbox) {
      menuItem.isCheckboxSelectedAnimated = new BoolAnimator(frameLayout, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l, menuItem.isCheckboxSelected);
      frameLayout.setOnClickListener(v -> {
        menuItem.isCheckboxSelected = menuItem.isCheckboxSelectedAnimated.toggleValue(true);
        listener.onClick(v);
      });
    } else {
      frameLayout.setOnClickListener(listener);
    }
    frameLayout.setWillNotDraw(false);
    if (receiver != null) {
      receiver.setBounds(iconCenterX - Screen.dp(10), Screen.dp(ITEM_HEIGHT / 2f - 10), iconCenterX + Screen.dp(10), Screen.dp(ITEM_HEIGHT / 2f + 10));
      receiver.setUpdateListener(r -> frameLayout.invalidate());
    }

    TextView titleTextItem = new NoScrollTextView(getContext());
    titleTextItem.setTypeface(Fonts.getRobotoRegular());
    titleTextItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleTextItem.setText(menuItem.title);
    titleTextItem.setSingleLine(true);
    titleTextItem.setEllipsize(TextUtils.TruncateAt.END);
    titleTextItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
    if (!StringUtils.isEmpty(menuItem.subtitle)) {
      titleTextItem.setGravity(Gravity.TOP | Lang.gravity());
      titleTextItem.setPadding(textStartX, Screen.dp(5f), textRightOffset, 0);
    } else {
      titleTextItem.setGravity(Gravity.CENTER_HORIZONTAL | Lang.gravity());
      titleTextItem.setPadding(textStartX, 0, textRightOffset, 0);
      titleTextItem.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Lang.gravity()));
    }
    titleTextItem.setMaxWidth(maxWidth);

    TextView subtitleTextItem;
    if (!StringUtils.isEmpty(menuItem.subtitle)) {
      subtitleTextItem = new NoScrollTextView(getContext());
      subtitleTextItem.setTypeface(Fonts.getRobotoRegular());
      subtitleTextItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
      subtitleTextItem.setText(menuItem.subtitle);
      subtitleTextItem.setGravity(Gravity.BOTTOM | Lang.gravity());
      subtitleTextItem.setSingleLine(true);
      subtitleTextItem.setEllipsize(TextUtils.TruncateAt.END);
      subtitleTextItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
      subtitleTextItem.setPadding(textStartX, 0, textRightOffset, Screen.dp(8f));
      subtitleTextItem.setMaxWidth(maxWidth);

      if (forcedTheme != null) {
        subtitleTextItem.setTextColor(forcedTheme.getColor(ColorId.textLight));
      } else {
        subtitleTextItem.setTextColor(Theme.getColor(ColorId.textLight));
        if (themeListeners != null) {
          themeListeners.addThemeTextDecentColorListener(subtitleTextItem);
        }
      }
    } else {
      subtitleTextItem = null;
    }

    final boolean xgramExpressive = XgramExpressive.isEnabled();
    titleTextItemRef[0] = titleTextItem;
    if (xgramExpressive) {
      XgramExpressive.applyText(titleTextItem, XgramExpressive.TS_BODY_L, false); // XGram: menu item labels are bodyLarge
    }
    if (forcedTheme != null) {
      titleTextItem.setTextColor(forcedTheme.getColor(ColorId.text));
    } else if (xgramExpressive) {
      titleTextItem.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      if (themeListeners != null && !menuItem.isCheckbox) {
        // XGram: follows theme changes (ColorId.text is onSurface with Expressive design); a checkbox row sets its
        // label color while drawing (onSecondaryContainer when checked)
        themeListeners.addThemeColorListener(titleTextItem, ColorId.text);
      }
    } else {
      titleTextItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(titleTextItem);
      }
    }

    if (themeListeners != null) {
      themeListeners.addThemeInvalidateListener(frameLayout);
    }

    frameLayout.addView(titleTextItem);
    if (subtitleTextItem != null) {
      frameLayout.addView(subtitleTextItem);
    }

    Views.setClickable(frameLayout);
    if (xgramExpressive) {
      xgramSetItemBackground(frameLayout);
    } else
    RippleSupport.setTransparentSelector(frameLayout);
    itemsLayout.addView(frameLayout);
    frameLayout.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    frameLayout.setTag(frameLayout.getMeasuredWidth());
    return frameLayout;
  }

  public TextView addItem (int id, CharSequence title, int iconRes, Drawable icon, OnClickListener listener) {
    TextView menuItem = new NoScrollTextView(getContext());
    menuItem.setId(id);
    menuItem.setTypeface(Fonts.getRobotoRegular());
    menuItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    final boolean xgramExpressive = XgramExpressive.isEnabled();
    if (xgramExpressive) {
      XgramExpressive.applyText(menuItem, XgramExpressive.TS_BODY_L, false); // XGram: menu item labels are bodyLarge
    }
    if (forcedTheme != null) {
      menuItem.setTextColor(forcedTheme.getColor(ColorId.text));
    } else if (xgramExpressive) {
      menuItem.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      if (themeListeners != null) {
        // XGram: the label follows theme changes (ColorId.text is onSurface with Expressive design), as the container
        // and the icons do: set once, the labels kept the old theme's tone (dark labels on the dark container)
        themeListeners.addThemeColorListener(menuItem, ColorId.text);
      }
    } else {
      menuItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(menuItem);
      }
    }
    menuItem.setText(title);
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setSingleLine(true);
    menuItem.setEllipsize(TextUtils.TruncateAt.END);
    menuItem.setOnClickListener(listener);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
    if (xgramExpressive) {
      // XGram: Material 3 menu item spec: 12dp leading and trailing padding, 12dp between icon and label.
      menuItem.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      menuItem.setCompoundDrawablePadding(Screen.dp(12f));
    } else {
    menuItem.setPadding(Screen.dp(17f), 0, Screen.dp(17f), 0);
    menuItem.setCompoundDrawablePadding(Screen.dp(18f));
    }
    icon = iconRes != 0 ? Drawables.get(getResources(), iconRes) : icon;
    if (icon != null) {
      if (forcedTheme != null) {
        icon.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(ColorId.icon)));
      } else {
        icon.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
        if (themeListeners != null) {
          themeListeners.addThemeFilterListener(icon, ColorId.icon);
        }
      }
      if (Drawables.needMirror(iconRes)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
      }
    }
    Views.setClickable(menuItem);
    if (xgramExpressive) {
      xgramSetItemBackground(menuItem);
    } else
    RippleSupport.setTransparentSelector(menuItem);
    itemsLayout.addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  @Override
  public int getItemsWidth () {
    int padding = Screen.dp(PADDING);
    int childCount = getChildCount();
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    return Math.max(getMinimumWidth(), maxWidth + padding + padding);
  }

  private boolean shouldPivotBottom;

  public void setShouldPivotBottom (boolean shouldPivotBottom) {
    this.shouldPivotBottom = shouldPivotBottom;
  }

  @Override
  public boolean shouldPivotBottom () {
    return shouldPivotBottom;
  }

  @Override
  public int getItemsHeight () {
    int itemHeight = Screen.dp(ITEM_HEIGHT);
    int padding = Screen.dp(PADDING);
    int total = 0;
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        total += itemHeight;
      }
    }
    return total + padding + padding;
  }

  private @Nullable Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    xgramApplyAnchor(); // XGram: before the first frame (the reveal starts below)
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private final FactorAnimator factorAnimator = new FactorAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 250L);
  private int lastSelectedIndex = -1;

  public void processMoveEvent (View v, float x, float y, float startX, float startY) {
    int[] location = Views.getLocationOnScreen(v);
    int sourceX = location[0] + (int) x;
    int sourceY = location[1] + (int) y;
    location = Views.getLocationOnScreen(this);
    int innerX = sourceX - location[0];
    int innerY = sourceY - location[1];

    // XGram: the slot under the finger among the visible rows. Hidden rows (e.g. Send when online) take no room in the
    // menu (getItemsHeight), so counting them put the highlight one row off and released on the hidden item.
    int index = Math.floorDiv(innerY - Screen.dp(PADDING), Screen.dp(ITEM_HEIGHT));
    setSelectedIndex(index >= 0 && index < getVisibleItemCount() ? index : -1);

    // Log.i("HAPTIC INNER", String.format("INDEX %d", index));
  }

  /** XGram: the rows that take room in the menu. */
  private int getVisibleItemCount () {
    int count = 0;
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        count++;
      }
    }
    return count;
  }

  public void onApply () {
    if (lastSelectedIndex != -1) {
      // XGram: lastSelectedIndex is a slot among the visible rows
      int slot = 0;
      final int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        View v = getChildAt(i);
        if (v == null || v.getVisibility() == View.GONE) {
          continue;
        }
        if (slot == lastSelectedIndex) {
          v.callOnClick();
          break;
        }
        slot++;
      }
    }
  }

  // XGram: the view that opened this menu (a header), so the menu opens at it and not at the window's edge: in two panes
  // or next to the navigation rail the header is narrower than the window the menu's popup covers

  private @Nullable View xgramAnchorView;
  private int xgramAnchorButtonIndex;

  /** Anchors the menu to {@code anchor}: its end (ANCHOR_MODE_RIGHT, less {@code buttonIndex} buttons) or start (ANCHOR_MODE_HEADER). */
  public void xgramSetAnchorView (@Nullable View anchor, int buttonIndex) {
    this.xgramAnchorView = anchor;
    this.xgramAnchorButtonIndex = buttonIndex;
  }

  /** Moves the laid out menu to its anchor view (screen positions: the menu's popup may be a window of its own). */
  private void xgramApplyAnchor () {
    final View anchor = xgramAnchorView;
    if (anchor == null || !(getParent() instanceof View) || anchor.getWindowToken() == null || anchor.getMeasuredWidth() <= 0) {
      return;
    }
    if (anchorMode != ANCHOR_MODE_RIGHT && anchorMode != ANCHOR_MODE_HEADER) {
      return;
    }
    // Views.getLocationOnScreen shares one array: each value is copied right away
    final int anchorLeft = Views.getLocationOnScreen(anchor)[0];
    final int anchorRight = anchorLeft + anchor.getMeasuredWidth();
    final int parentLeft = Views.getLocationOnScreen((View) getParent())[0];
    final int left = parentLeft + getLeft(), right = parentLeft + getRight(); // the menu's place without translation
    final boolean rtl = Lang.rtl();
    final float translationX;
    if (anchorMode == ANCHOR_MODE_RIGHT) {
      final int buttons = Screen.dp(49f) * xgramAnchorButtonIndex;
      translationX = rtl ? anchorLeft + buttons - left : anchorRight - buttons - right;
    } else {
      translationX = rtl ? anchorRight - Screen.dp(46f) - right : anchorLeft + Screen.dp(46f) - left;
    }
    if (getTranslationX() != translationX) {
      setTranslationX(translationX);
    }
  }

  private void setSelectedIndex (int index) {
    if (index == lastSelectedIndex) return;
    if (lastSelectedIndex == -1 || index == -1) {
      factorAnimator.forceFactor(index);
    } else {
      factorAnimator.animateTo(index);
    }
    lastSelectedIndex = index;
    invalidate();
  }

  // XGram: Material 3 Expressive menu item shapes: a 4dp inset with medium (12dp) corners,
  // used for the press ripple, the drag highlight and the checked pill.

  private static void xgramDrawItemPill (Canvas canvas, int width, int height, int color) {
    final int inset = Screen.dp(4f);
    final float radius = Screen.dp(XgramExpressive.CORNER_M);
    RectF rectF = Paints.getRectF();
    rectF.set(inset, inset, width - inset, height - inset);
    canvas.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
  }

  private void xgramSetItemBackground (View view) {
    final float radius = Screen.dp(XgramExpressive.CORNER_M);
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(radius);
    final int baseColor = forcedTheme != null ? forcedTheme.getColor(ColorId.text) : XgramExpressive.color(XgramExpressive.ON_SURFACE);
    final int inset = Screen.dp(4f);
    RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(XgramExpressive.withAlpha(baseColor, XgramExpressive.STATE_PRESSED)), null, new InsetDrawable(mask, inset));
    // XGram: setBackground copies the drawable's padding (the mask's 4dp inset) onto the view,
    // which pushed the labels to 4dp from the container edge. Keep the item's own padding.
    final int paddingLeft = view.getPaddingLeft(), paddingTop = view.getPaddingTop(), paddingRight = view.getPaddingRight(), paddingBottom = view.getPaddingBottom();
    ViewUtils.setBackground(view, ripple);
    view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
  }

  private int bubbleTailX = -1;

  public void setBubbleTailX (int bubbleTailX) {
    this.bubbleTailX = bubbleTailX;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (bubbleTailX > 0 && !XgramExpressive.isEnabled()) { // XGram: Material 3 menus have no tail
      float cx = bubbleTailX;
      float cy = getMeasuredHeight() - Screen.dp(PADDING);
      canvas.save();
      canvas.rotate(45f, cx, cy);
      canvas.drawRect(
        cx - Screen.dp(4.5f),
        cy - Screen.dp(4.5f),
        cx + Screen.dp(4.5f),
        cy + Screen.dp(4.5f),
        Paints.fillingPaint(Theme.fillingColor())
      );
      canvas.restore();
    }

    if (lastSelectedIndex != -1 && itemsLayout == this && XgramExpressive.isEnabled()) {
      // XGram: the drag-to-select highlight is the same rounded state layer as a press.
      final int baseColor = forcedTheme != null ? forcedTheme.getColor(ColorId.text) : XgramExpressive.color(XgramExpressive.ON_SURFACE);
      final int itemWidth = getMeasuredWidth() - Screen.dp(PADDING) * 2;
      int childCount = getVisibleItemCount(); // XGram: slots of the visible rows (see processMoveEvent)
      for (int i = 0; i < childCount; i++) {
        final float alpha = MathUtils.clamp(1f - Math.abs(factorAnimator.getFactor() - i));
        if (alpha > 0f) {
          canvas.save();
          canvas.translate(Screen.dp(PADDING), Screen.dp(PADDING + ITEM_HEIGHT * i));
          xgramDrawItemPill(canvas, itemWidth, Screen.dp(ITEM_HEIGHT), XgramExpressive.withAlpha(baseColor, alpha * XgramExpressive.STATE_PRESSED));
          canvas.restore();
        }
      }
    } else if (lastSelectedIndex != -1 && itemsLayout == this) {
      int childCount = getVisibleItemCount(); // XGram: slots of the visible rows (see processMoveEvent)
      for (int i = 0; i < childCount; i++) {
        final float alpha = MathUtils.clamp(1f - Math.abs(factorAnimator.getFactor() - i));
        if (alpha > 0f) {
          canvas.drawRect(Screen.dp(PADDING), Screen.dp(PADDING + ITEM_HEIGHT * i), getMeasuredWidth() - Screen.dp(PADDING), Screen.dp(PADDING + ITEM_HEIGHT * (i + 1)), Paints.fillingPaint(ColorUtils.alphaColor(alpha * 0.05f, Theme.getColor(ColorId.text))));
        }
      }
    }


    super.onDraw(canvas);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    complexAvatarReceiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    complexAvatarReceiver.detach();
  }
}