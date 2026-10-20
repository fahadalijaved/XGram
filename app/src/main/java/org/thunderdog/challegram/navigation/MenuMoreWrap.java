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
import android.graphics.Rect;
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
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Controls;
import com.fahadali.xgram.appearance.XgramPopupBounds;
import com.fahadali.xgram.appearance.XgramR5Composer;

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
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;
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
  private final @Nullable ScrollView scrollView; // XGram: the scrollable mode's scroll view (itemsLayout is its column)

  public MenuMoreWrap (Context context) {
    this(context, false);
  }

  public MenuMoreWrap (Context context, boolean scrollable) {
    super(context);
    setWillNotDraw(false);
    complexAvatarReceiver = new ComplexReceiver(this);
    factorAnimator.forceFactor(-1);
    com.fahadali.xgram.appearance.XgramAppMotion.use(factorAnimator, XgramExpressive.SPATIAL_FAST, false); // XGram: the drag highlight slides on a spring
    if (scrollable) {
      itemsLayout = new LinearLayout(context) {
        @Override
        protected void dispatchDraw (Canvas canvas) {
          // XGram: the drag highlight is drawn in the scrolling column, under its rows: it scrolls and clips with them
          xgramDrawSelection(canvas, 0, 0, getWidth());
          super.dispatchDraw(canvas);
        }
      };
      itemsLayout.setOrientation(LinearLayout.VERTICAL);
      scrollView = new ScrollView(context);
      scrollView.addView(itemsLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      scrollView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      // XGram: rows cut off at a capped menu's edge fade out, so it shows that it scrolls
      scrollView.setVerticalFadingEdgeEnabled(true);
      scrollView.setFadingEdgeLength(Screen.dp(16f));
      addView(scrollView);
    } else {
      itemsLayout = this;
      scrollView = null;
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
    xgramFlushCard = XgramExpressive.isEnabled();
    if (XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive menu container: surfaceContainer (the chip step with Pure black, as every
      // floating container), large (16dp) corners, elevation level 2, 8dp vertical padding inside the container. The
      // 8dp side insets keep the old position on screen. Over media (a forced theme) the same roles in that theme's
      // scheme, never its Telegram X overlay color.
      XgramExpressive.MorphDrawable container = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_L, XgramExpressive.CORNER_L);
      if (forcedTheme != null) {
        container.setColor(com.fahadali.xgram.appearance.XgramR6B3Shell.floatingColor(XgramExpressive.SURFACE_CONTAINER, forcedTheme));
      } else {
        container.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER);
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
    TextView menuItem = (TextView) itemsLayout.getChildAt(index); // XGram: the scroll view's column when scrollable
    menuItem.setId(id);
    menuItem.setOnClickListener(listener);
    menuItem.setText(title);
    Drawable drawable = icon != 0 ? Drawables.get(getResources(), icon) : null;
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setVisibility(View.VISIBLE);
    if (XgramExpressive.isEnabled() && xgramCheckable.remove(menuItem)) {
      // XGram M3E r6: the row was a checkable one last time this menu was shown: a plain row again
      xgramSetItemBackground(menuItem);
    }
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
    // XGram M3E: a single choice (no checkbox): the label 12dp in (48dp with an icon), a trailing check 12dp from the end
    final boolean xgramChoice = xgramM3Layout && menuItem.isChoice;
    final boolean xgramChoiceNoIcon = xgramChoice && menuItem.iconResId == 0 && menuItem.icon == null;
    final int textRightOffset = xgramM3Layout ? Screen.dp(menuItem.isLocked ? 40 : xgramChoice ? 48 : 12) : Screen.dp(menuItem.isLocked ? 41 : 17);
    final int iconCenterX = Screen.dp(xgramM3Layout ? 24f : 29f);
    final int textStartX = Screen.dp(xgramChoiceNoIcon ? 12f : xgramM3Layout ? 48f : 59f);
    final Drawable xgramCheck = xgramChoice ? Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_check_24)) : null;
    final int lockEndOffset = Screen.dp(xgramM3Layout ? 12 + 16 : 17 + 16);
    final Drawable finalIcon = menuItem.iconResId != 0 ? Drawables.get(getResources(), menuItem.iconResId) : menuItem.icon;
    final Drawable lockIcon = menuItem.isLocked ? Drawables.get(getResources(), R.drawable.baseline_lock_16) : null; // XGram: once per row, not per frame
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
          // XGram: checked items sit on a secondaryContainer pill (the standard menu colors; medium corners, 4dp inset),
          // over media in the forced theme's scheme (M3E r6)
          float checkedFactor = menuItem.isCheckboxSelectedAnimated != null ? MathUtils.clamp(menuItem.isCheckboxSelectedAnimated.getFloatValue()) : menuItem.isCheckboxSelected ? 1f : 0f;
          int textColor = ColorUtils.fromToArgb(xgramRole(XgramExpressive.ON_SURFACE), xgramRole(XgramExpressive.ON_SECONDARY_CONTAINER), checkedFactor);
          if (titleTextItemRef[0] != null && titleTextItemRef[0].getCurrentTextColor() != textColor) {
            titleTextItemRef[0].setTextColor(textColor);
          }
          if (checkedFactor > 0f) {
            xgramDrawItemPill(canvas, getMeasuredWidth(), getMeasuredHeight(), ColorUtils.alphaColor(checkedFactor, xgramRole(XgramExpressive.SECONDARY_CONTAINER)));
          }
        }
        if (receiver != null) {
          receiver.draw(canvas);
        }

        if (lockIcon != null) {
          float x = getMeasuredWidth() - lockEndOffset;
          float y = (getMeasuredHeight() - lockIcon.getMinimumHeight()) / 2f;
          Drawables.draw(canvas, lockIcon, x, y, XgramExpressive.isEnabled() ? Paints.getPorterDuffPaint(xgramRole(XgramExpressive.ON_SURFACE_VARIANT)) : PorterDuffPaint.get(ColorId.text));
        }

        if (xgramChoice) {
          final boolean rtl = Lang.rtl();
          if (finalIcon != null) {
            final float iconX = rtl ? getMeasuredWidth() - iconCenterX : iconCenterX;
            Drawables.draw(canvas, finalIcon, iconX - finalIcon.getMinimumWidth() / 2f, (getMeasuredHeight() - finalIcon.getMinimumHeight()) / 2f,
              forcedTheme != null ? PorterDuffPaint.get(ColorId.icon) : Paints.getPorterDuffPaint(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT)));
          }
          final float chosen = menuItem.isCheckboxSelectedAnimated != null ? menuItem.isCheckboxSelectedAnimated.getFloatValue() : menuItem.isCheckboxSelected ? 1f : 0f;
          if (xgramCheck != null && chosen > 0f) {
            final int color = forcedTheme != null ? forcedTheme.getColor(ColorId.text) : XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
            final float x = rtl ? Screen.dp(12f) : getMeasuredWidth() - Screen.dp(12f) - xgramCheck.getMinimumWidth();
            Drawables.draw(canvas, xgramCheck, x, (getMeasuredHeight() - xgramCheck.getMinimumHeight()) / 2f, Paints.getPorterDuffPaint(ColorUtils.alphaColor(chosen, color)));
          }
          return;
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

          if (XgramExpressive.isEnabled()) {
            // XGram M3E: Material 3 checkbox (primary / onPrimary), in the forced theme's scheme over media
            float boxSize = Screen.dp(18f);
            float checked = menuItem.isCheckboxSelectedAnimated != null ? MathUtils.clamp(menuItem.isCheckboxSelectedAnimated.getFloatValue()) : menuItem.isCheckboxSelected ? 1f : 0f;
            XgramM3Controls.drawCheckbox(canvas, iconCenterX - boxSize / 2f, getMeasuredHeight() / 2f - boxSize / 2f, boxSize, checked, 0f, 0f, 0f, 1f, forcedTheme);
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
          } else if (XgramExpressive.isEnabled()) {
            Drawables.draw(canvas, finalIcon, 0, 0, Paints.getPorterDuffPaint(xgramRole(XgramExpressive.ON_SURFACE_VARIANT))); // XGram: onSurfaceVariant, forced theme included
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
      com.fahadali.xgram.appearance.XgramAppMotion.use(menuItem.isCheckboxSelectedAnimated, XgramExpressive.EFFECTS_DEFAULT, true); // XGram: the check and the pill on a spring
      if (xgramChoice) {
        frameLayout.setOnClickListener(listener); // a choice is not toggled: the menu closes on it
      } else {
        frameLayout.setOnClickListener(v -> {
          menuItem.isCheckboxSelected = menuItem.isCheckboxSelectedAnimated.toggleValue(true);
          listener.onClick(v);
        });
      }
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
    if (xgramChoice && Lang.rtl()) {
      // XGram M3E: the trailing check is on the left in RTL
      titleTextItem.setPadding(textRightOffset, titleTextItem.getPaddingTop(), textStartX, titleTextItem.getPaddingBottom());
    }

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
      if (XgramExpressive.isEnabled()) {
        XgramExpressive.applyText(subtitleTextItem, XgramExpressive.TS_BODY_S, false); // XGram: supporting text, bodySmall
      }

      if (forcedTheme != null && XgramExpressive.isEnabled()) {
        subtitleTextItem.setTextColor(xgramRole(XgramExpressive.ON_SURFACE_VARIANT));
      } else if (forcedTheme != null) {
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
    if (forcedTheme != null && xgramExpressive) {
      titleTextItem.setTextColor(xgramRole(XgramExpressive.ON_SURFACE)); // XGram M3E r6: onSurface in the forced theme's scheme
    } else if (forcedTheme != null) {
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
    if (forcedTheme != null && xgramExpressive) {
      menuItem.setTextColor(xgramRole(XgramExpressive.ON_SURFACE)); // XGram M3E r6: onSurface in the forced theme's scheme
    } else if (forcedTheme != null) {
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
      if (forcedTheme != null && xgramExpressive) {
        icon.setColorFilter(Paints.getColorFilter(xgramRole(XgramExpressive.ON_SURFACE_VARIANT))); // XGram M3E r6
      } else if (forcedTheme != null) {
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
    int width = getItemsWidth();
    if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(widthMeasureSpec) > 0) {
      width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec)); // XGram: never wider than its popup (long labels end with an ellipsis)
    }
    // XGram: the height cap (anchored, bound to the safe area or set by the owner): the rows that don't fit scroll
    final int maxHeight = xgramMeasureCap();
    if (xgramWidthCap != Integer.MAX_VALUE) {
      width = Math.min(width, Math.max(getMinimumWidth(), xgramWidthCap));
    }
    if (maxHeight != Integer.MAX_VALUE) {
      final int size = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
    }
    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  @Override
  public int getItemsWidth () {
    int padding = Screen.dp(PADDING);
    int childCount = itemsLayout.getChildCount(); // XGram: the rows (in the scroll view's column when scrollable)
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = itemsLayout.getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    final int width = Math.max(getMinimumWidth(), maxWidth + padding + padding);
    return xgramMaxItemsWidth > 0 ? Math.min(width, Math.max(getMinimumWidth(), xgramMaxItemsWidth)) : width;
  }

  /** XGram: the widest the menu may get (0: as wide as its widest item); longer titles end with an ellipsis. */
  private int xgramMaxItemsWidth;

  public void xgramSetMaxItemsWidth (int maxWidth) {
    this.xgramMaxItemsWidth = maxWidth;
  }

  private boolean shouldPivotBottom;

  public void setShouldPivotBottom (boolean shouldPivotBottom) {
    this.shouldPivotBottom = shouldPivotBottom;
  }

  @Override
  public boolean shouldPivotBottom () {
    return xgramPopupAnchor != null ? xgramOpensUp : shouldPivotBottom; // XGram: anchored, the side facing the anchor
  }

  /** XGram: the height of all the rows and the padding; the menu itself may be shorter (capped: the rest scrolls). */
  @Override
  public int getItemsHeight () {
    int itemHeight = Screen.dp(ITEM_HEIGHT);
    int padding = Screen.dp(PADDING);
    int total = 0;
    int childCount = itemsLayout.getChildCount(); // XGram: the rows (in the scroll view's column when scrollable)
    for (int i = 0; i < childCount; i++) {
      View v = itemsLayout.getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        total += itemHeight;
      }
    }
    return total + padding + padding;
  }

  @Override
  public float getRevealRadius () {
    // XGram: the menu as measured (its height may be capped, the rest scrolls)
    final int width = getMeasuredWidth() > 0 ? getMeasuredWidth() : getItemsWidth();
    final int height = getMeasuredHeight() > 0 ? getMeasuredHeight() : getItemsHeight();
    return (float) Math.hypot(width, height);
  }

  /** XGram: the rows added to this menu, hidden ones included (in the scroll view's column when scrollable). */
  public int getItemCount () {
    return itemsLayout.getChildCount();
  }

  /** XGram: removes the rows from {@code index} on (a reused menu with fewer rows this time). */
  public void removeItemsFrom (int index) {
    for (int i = itemsLayout.getChildCount() - 1; i >= Math.max(0, index); i--) {
      xgramCheckable.remove(itemsLayout.getChildAt(i));
      itemsLayout.removeViewAt(i);
    }
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
    xgramPlaceAtPopupAnchor(); // XGram: likewise; it wins over xgramApplyAnchor
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private final FactorAnimator factorAnimator = new FactorAnimator(0, (a, b, c, d) -> xgramInvalidateSelection(), AnimatorUtils.DECELERATE_INTERPOLATOR, 250L);
  private int lastSelectedIndex = -1;

  public void processMoveEvent (View v, float x, float y, float startX, float startY) {
    int[] location = Views.getLocationOnScreen(v);
    final int sourceX = location[0] + (int) x;
    final int sourceY = location[1] + (int) y;

    // XGram: the finger in the menu's own coordinates, from its laid out place (its parent's, plus its left, top and
    // translation) with the opening scale around its pivot undone: getLocationOnScreen(this) counted the running scale,
    // so the highlight sat rows above the finger while the menu sprang open (and a release there sent another item)
    final float localX, localY;
    if (getParent() instanceof View) {
      location = Views.getLocationOnScreen((View) getParent());
      final float parentX = location[0], parentY = location[1];
      final float scaleX = getScaleX() > 0f ? getScaleX() : 1f, scaleY = getScaleY() > 0f ? getScaleY() : 1f;
      final float pivotX = getPivotX(), pivotY = getPivotY();
      localX = pivotX + (sourceX - (parentX + getLeft() + getTranslationX() + pivotX)) / scaleX;
      localY = pivotY + (sourceY - (parentY + getTop() + getTranslationY() + pivotY)) / scaleY;
    } else {
      location = Views.getLocationOnScreen(this);
      localX = sourceX - location[0];
      localY = sourceY - location[1];
    }

    // XGram: the slot under the finger among the visible rows. Hidden rows (e.g. Send when online) take no room in the
    // menu (getItemsHeight), so counting them put the highlight one row off and released on the hidden item.
    int index;
    if (scrollView != null) {
      // Scrollable: the rows under the scroll view's viewport; held on its top or bottom 16dp, it scrolls on (not while
      // the finger is still outside the menu, e.g. on the button under it)
      final float viewportY = localY - scrollView.getTop();
      final int viewportHeight = scrollView.getHeight();
      final int edge = Screen.dp(16f);
      final boolean inside = localX >= 0f && localX < getWidth() && viewportY >= 0f && viewportY < viewportHeight;
      if (inside && viewportY < edge && scrollView.canScrollVertically(-1)) {
        scrollView.scrollBy(0, -Screen.dp(8f));
      } else if (inside && viewportY > viewportHeight - edge && scrollView.canScrollVertically(1)) {
        scrollView.scrollBy(0, Screen.dp(8f));
      }
      if (viewportY < 0f || viewportY >= viewportHeight) {
        index = -1;
      } else {
        index = Math.floorDiv((int) Math.floor(viewportY + scrollView.getScrollY() - itemsLayout.getTop()), Screen.dp(ITEM_HEIGHT));
      }
    } else {
      index = Math.floorDiv((int) Math.floor(localY - Screen.dp(PADDING)), Screen.dp(ITEM_HEIGHT));
    }
    setSelectedIndex(index >= 0 && index < getVisibleItemCount() ? index : -1);

    // Log.i("HAPTIC INNER", String.format("INDEX %d", index));
  }

  /** XGram: the rows that take room in the menu. */
  private int getVisibleItemCount () {
    int count = 0;
    final int childCount = itemsLayout.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = itemsLayout.getChildAt(i);
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
      final int childCount = itemsLayout.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View v = itemsLayout.getChildAt(i);
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

  // XGram: anchored placement. A menu opened from a view (HapticMenuHelper: Send and the other send buttons, Send as;
  // XgramR5Chat.showMenu: list rows, the reply bar) measures and places itself: its visible card 8dp above the anchor
  // (or below it, see the placements), never above the status bar or the display cutout + 8dp, never under the
  // navigation bar, the keyboard or a chat's in-app emoji panel + 8dp, inside the window's sides (and the chat pane's)
  // + 8dp; the rows that don't fit scroll (a scrollable menu). The height is capped while measuring and checked again
  // before every frame (a measure in the pass that moves the anchor still sees its old place); the place is set in
  // onLayout from the menu's laid out (untransformed) place, before the reveal starts, so the opening scale can't move
  // it, and again on every frame its anchor moves (the composer while the keyboard or the emoji panel opens or closes).

  /** {@link #xgramSetPopupAnchor(View, Rect, int)}: above the anchor unless not even two rows fit there and below has more room. */
  public static final int XGRAM_PLACE_PREFER_ABOVE = XgramPopupBounds.PLACE_PREFER_ABOVE;
  /** {@link #xgramSetPopupAnchor(View, Rect, int)}: above the anchor when the whole menu fits there, else below when it does there, else the side with more room. */
  public static final int XGRAM_PLACE_FIT = XgramPopupBounds.PLACE_FIT;

  private @Nullable View xgramPopupAnchor;
  private @Nullable Rect xgramPopupAnchorBounds;
  private int xgramPlacement = XGRAM_PLACE_PREFER_ABOVE;
  private boolean xgramOpensUp = true, xgramDirectionLatched;
  private int xgramLatchedWidth, xgramLatchedHeight;
  private boolean xgramBoundToSafeArea;
  private int xgramMaxHeight;
  private int xgramMeasuredCap = Integer.MAX_VALUE, xgramWidthCap = Integer.MAX_VALUE;
  private float xgramMeasuredTranslationY;
  /** The visible card is flush with the view's top and bottom (Expressive design); the legacy 9-patch has ~8dp of shadow there. */
  private boolean xgramFlushCard = XgramExpressive.isEnabled();
  private final Rect xgramAnchorRect = new Rect(), xgramSafeRect = new Rect();
  private final Rect xgramPlacedAnchorRect = new Rect(), xgramPlacedSafeRect = new Rect();
  private final int[] xgramPane = new int[2], xgramLocation = new int[2];
  /** The chat the anchor is in (its in-app emoji panel is no room for the menu), looked up once the anchor is measured. */
  private @Nullable MessagesController xgramAnchorChat;
  private boolean xgramAnchorChatResolved, xgramAnchorLost;

  /**
   * XGram: {@link #xgramSetPopupAnchor(View, Rect, int)} at the whole {@code anchor} with
   * {@link #XGRAM_PLACE_PREFER_ABOVE}: for buttons at the bottom of the screen (Send and the other send buttons).
   */
  public void xgramSetPopupAnchor (@Nullable View anchor) {
    xgramSetPopupAnchor(anchor, null, XGRAM_PLACE_PREFER_ABOVE);
  }

  /**
   * XGram: the menu measures and places itself at {@code anchor} (null: back to its gravity and translation). Use it
   * with a scrollable menu ({@code new MenuMoreWrap(context, true)}): the rows that don't fit scroll. Call before
   * {@code PopupLayout.showMoreView}; the menu's translation and pivots are its own from then on (PopupLayout keeps
   * them).
   * <p>
   * Vertically: the visible card's bottom {@link XgramPopupBounds#MARGIN} dp above the anchor's top, or its top that
   * far below the anchor's bottom, by {@code placement} ({@link #XGRAM_PLACE_PREFER_ABOVE} or {@link #XGRAM_PLACE_FIT});
   * never above the status bar or the display cutout + 8dp, nor under the navigation bar, the keyboard or (an anchor in
   * a chat) the in-app emoji panel + 8dp (the height is capped to the room on its side, at least one row). The
   * direction stays while the menu is open (chosen again when its popup's size changes: folding, rotation).
   * <p>
   * Horizontally by the anchor mode: ANCHOR_MODE_RIGHT puts the card's end edge on the anchor's end edge,
   * ANCHOR_MODE_HEADER the start edges, ANCHOR_MODE_CENTER centres it on the anchor (inside the anchor's span when the
   * menu is narrower than it, e.g. a row in its pane; with Expressive design off, a menu wider than its anchor shows
   * its tail under the anchor's centre when it opens above). Always inside the window's sides + 8dp, and inside the
   * chat pane's (two panes, the navigation rail) + 8dp when the anchor is in it and the menu fits there.
   * <p>
   * The pivots face the anchor. The menu follows the anchor while it moves (the composer while the keyboard or the
   * in-app emoji panel opens or closes); its popup hides when the anchor goes away while it shows (the chat list's
   * pane removed on folding).
   *
   * @param boundsInAnchor the part of the anchor to open at, in the anchor's own coordinates (e.g. one bubble in a full
   *                       width row), or null for the whole anchor
   */
  public void xgramSetPopupAnchor (@Nullable View anchor, @Nullable Rect boundsInAnchor, int placement) {
    this.xgramPopupAnchor = anchor;
    this.xgramPopupAnchorBounds = boundsInAnchor != null ? new Rect(boundsInAnchor) : null;
    this.xgramPlacement = placement;
    this.xgramDirectionLatched = false;
    this.xgramAnchorChat = null;
    this.xgramAnchorChatResolved = false;
    this.xgramAnchorLost = false;
    requestLayout();
  }

  /** XGram: whether the menu places itself at a popup anchor ({@link #xgramSetPopupAnchor(View, Rect, int)}). */
  public boolean xgramHasPopupAnchor () {
    return xgramPopupAnchor != null;
  }

  /** XGram: whether the anchored menu opens above its anchor (as last measured). */
  public boolean xgramOpensAbove () {
    return xgramOpensUp;
  }

  /** XGram: the tallest the menu may get, in px (0: no cap of its own); the rows that don't fit scroll (scrollable menus). */
  public void xgramSetMaxHeight (int maxHeight) {
    if (this.xgramMaxHeight != maxHeight) {
      this.xgramMaxHeight = maxHeight;
      requestLayout();
    }
  }

  /**
   * XGram: for a menu placed by its gravity and translation (the app bar's ⋮ menus): its height is capped so its visible
   * card ends 8dp above the navigation bar or the keyboard (and its width to the window less the side insets); the rows
   * that don't fit scroll (scrollable menus). Its translationY counts, also when it changes later.
   */
  public void xgramSetBoundToSafeArea (boolean bound) {
    if (this.xgramBoundToSafeArea != bound) {
      this.xgramBoundToSafeArea = bound;
      requestLayout();
    }
  }

  /** The visible card's inset from the view's sides: the Expressive container's InsetDrawable, the 9-patch's shadow. */
  private int xgramCardInsetX () {
    return Screen.dp(PADDING);
  }

  /** The visible card's inset from the view's top and bottom. */
  private int xgramCardInsetY () {
    return xgramFlushCard ? 0 : Screen.dp(PADDING);
  }

  private static int xgramMinHeight () {
    return Screen.dp(ITEM_HEIGHT + PADDING * 2);
  }

  /** The anchor's and the safe area's rects in the parent's (popup's) coordinates; false when there is nothing to anchor to. */
  private boolean xgramMeasurePopupAnchor () {
    final View anchor = xgramPopupAnchor;
    if (anchor == null || !(getParent() instanceof View)) {
      return false;
    }
    final View parent = (View) getParent();
    if (!XgramPopupBounds.anchorRectIn(anchor, xgramPopupAnchorBounds, parent, xgramAnchorRect)) {
      return false;
    }
    XgramPopupBounds.safeRect(parent, 0, xgramSafeRect);
    xgramExcludeInAppPanel(anchor, parent);
    return true;
  }

  /**
   * An anchor in a chat: the safe area ends 8dp above Telegram X's in-app emoji / sticker / formatting panel, a view of
   * the chat and not an inset, so the panel is no room for the menu (it opens above Send, as with the system keyboard).
   */
  private void xgramExcludeInAppPanel (View anchor, View parent) {
    if (!xgramAnchorChatResolved) {
      final ViewController<?> c = ViewController.findAncestor(anchor);
      xgramAnchorChat = c instanceof MessagesController ? (MessagesController) c : null;
      xgramAnchorChatResolved = true;
    }
    final int panelTop = xgramAnchorChat != null ? xgramAnchorChat.xgramInAppKeyboardTop() : -1; // in the chat's window
    if (panelTop <= 0) {
      return;
    }
    // Into the popup's coordinates through the screen (the popup may be a window of its own)
    anchor.getLocationOnScreen(xgramLocation);
    final int anchorScreenY = xgramLocation[1];
    anchor.getLocationInWindow(xgramLocation);
    final int windowScreenY = anchorScreenY - xgramLocation[1];
    parent.getLocationOnScreen(xgramLocation);
    final int bottom = windowScreenY + panelTop - xgramLocation[1] - Screen.dp(XgramPopupBounds.MARGIN);
    xgramSafeRect.bottom = Math.max(xgramSafeRect.top, Math.min(xgramSafeRect.bottom, bottom));
  }

  /** The view height that fits above the anchor (its card 8dp above it, the card's top at the safe top). */
  private int xgramRoomAbove () {
    final int sy = xgramCardInsetY();
    return (xgramAnchorRect.top - Screen.dp(XgramPopupBounds.MARGIN) + sy) - (xgramSafeRect.top - sy);
  }

  /** The view height that fits below the anchor. */
  private int xgramRoomBelow () {
    final int sy = xgramCardInsetY();
    return (xgramSafeRect.bottom + sy) - (xgramAnchorRect.bottom + Screen.dp(XgramPopupBounds.MARGIN) - sy);
  }

  /** The height cap of this measure (Integer.MAX_VALUE: none); also sets the width cap and, anchored, the direction. */
  private int xgramMeasureCap () {
    int cap = xgramMaxHeight > 0 ? xgramMaxHeight : Integer.MAX_VALUE;
    xgramWidthCap = Integer.MAX_VALUE;
    if (xgramMeasurePopupAnchor()) {
      // Folded, unfolded or rotated: the direction is chosen again in xgramPlaceAtPopupAnchor (here the popup and the
      // anchor still have their old size and place), and xgramTrack measures again for the new side's room
      final int roomAbove = xgramRoomAbove(), roomBelow = xgramRoomBelow();
      if (!xgramDirectionLatched) {
        xgramOpensUp = XgramPopupBounds.opensAbove(roomAbove, roomBelow, getItemsHeight(), xgramPlacement);
      }
      cap = Math.min(cap, Math.max(xgramMinHeight(), xgramOpensUp ? roomAbove : roomBelow));
      xgramWidthCap = xgramSafeRect.width() + 2 * xgramCardInsetX();
    } else if (xgramBoundToSafeArea && getParent() instanceof View) {
      final View parent = (View) getParent();
      XgramPopupBounds.safeRect(parent, 0, xgramSafeRect);
      // Gravity TOP: the menu's laid out top is its top margin; its translation moves it down from there
      final int layoutTop = parent.getPaddingTop() + (getLayoutParams() instanceof MarginLayoutParams ? ((MarginLayoutParams) getLayoutParams()).topMargin : 0);
      xgramMeasuredTranslationY = getTranslationY();
      cap = Math.min(cap, Math.max(xgramMinHeight(), xgramSafeRect.bottom + xgramCardInsetY() - layoutTop - Math.round(getTranslationY())));
      xgramWidthCap = xgramSafeRect.width() + 2 * xgramCardInsetX();
    }
    xgramMeasuredCap = cap;
    return cap;
  }

  /** Places the laid out menu at its popup anchor (translation from its laid out place: no scale error, safe to repeat). */
  private void xgramPlaceAtPopupAnchor () {
    if (!xgramMeasurePopupAnchor()) {
      if (xgramDirectionLatched) {
        xgramDismissForLostAnchor(); // placed before: the anchor went away while the menu shows
      }
      return;
    }
    final View parent = (View) getParent();
    if (xgramDirectionLatched && (parent.getWidth() != xgramLatchedWidth || parent.getHeight() != xgramLatchedHeight)) {
      // Folded, unfolded or rotated: the direction is chosen again from the new layout (the popup's and the anchor's);
      // xgramTrack then measures again for the room on that side
      xgramOpensUp = XgramPopupBounds.opensAbove(xgramRoomAbove(), xgramRoomBelow(), getItemsHeight(), xgramPlacement);
    }
    xgramDirectionLatched = true;
    xgramLatchedWidth = parent.getWidth();
    xgramLatchedHeight = parent.getHeight();
    xgramPlacedAnchorRect.set(xgramAnchorRect);
    xgramPlacedSafeRect.set(xgramSafeRect);
    final int width = getMeasuredWidth(), height = getMeasuredHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    final int sx = xgramCardInsetX(), sy = xgramCardInsetY();
    final int gap = Screen.dp(XgramPopupBounds.MARGIN);
    final boolean rtl = Lang.rtl();

    int top = xgramOpensUp ? xgramAnchorRect.top - gap + sy - height : xgramAnchorRect.bottom + gap - sy;
    top = Math.max(xgramSafeRect.top - sy, Math.min(top, xgramSafeRect.bottom + sy - height)); // the top edge wins

    int minLeft = xgramSafeRect.left - sx, maxLeft = xgramSafeRect.right + sx - width;
    if (xgramChatPaneRange(parent, width, sx) && Math.max(minLeft, xgramPane[0]) <= Math.min(maxLeft, xgramPane[1])) {
      // An anchor in the chat pane (two panes, the navigation rail): the card stays in that pane too (Send as)
      minLeft = Math.max(minLeft, xgramPane[0]);
      maxLeft = Math.min(maxLeft, xgramPane[1]);
    }
    int left;
    switch (anchorMode) {
      case ANCHOR_MODE_CENTER: {
        left = xgramAnchorRect.centerX() - width / 2;
        if (width <= xgramAnchorRect.width()) {
          // Inside the anchor's own span (a row in its pane with two panes open)
          minLeft = Math.max(minLeft, xgramAnchorRect.left);
          maxLeft = Math.min(maxLeft, xgramAnchorRect.right - width);
        }
        break;
      }
      case ANCHOR_MODE_HEADER: {
        left = rtl ? xgramAnchorRect.right + sx - width : xgramAnchorRect.left - sx; // the start edges
        break;
      }
      case ANCHOR_MODE_RIGHT:
      default: {
        left = rtl ? xgramAnchorRect.left - sx : xgramAnchorRect.right + sx - width; // the end edges
        break;
      }
    }
    left = Math.max(minLeft, Math.min(left, maxLeft));

    setTranslationX(left - getLeft());
    setTranslationY(top - getTop());
    setPivotX(Math.max(sx, Math.min(width - sx, xgramAnchorRect.centerX() - left)));
    setPivotY(xgramOpensUp ? height - sy : sy);

    int tailX = -1;
    if (anchorMode == ANCHOR_MODE_CENTER && xgramOpensUp && !xgramFlushCard && xgramAnchorRect.width() < width) {
      tailX = Math.max(sx + Screen.dp(16f), Math.min(width - sx - Screen.dp(16f), xgramAnchorRect.centerX() - left));
    }
    if (bubbleTailX != tailX) {
      bubbleTailX = tailX;
      invalidate();
    }
  }

  /**
   * The menu's left edges ({@link #xgramPane}: [0] the least, [1] the most, in the popup's coordinates) that keep its
   * card ({@code width} wide, {@code sx} inside its sides) 8dp inside the chat pane; false when the anchor is not in
   * the chat pane, there is no pane (one pane, no navigation rail) or the menu is wider than the pane.
   */
  private boolean xgramChatPaneRange (View parent, int width, int sx) {
    final View anchor = xgramPopupAnchor;
    if (anchor == null) {
      return false;
    }
    XgramR5Composer.paneInsets(anchor, xgramPane);
    if (xgramPane[0] <= 0 && xgramPane[1] <= 0) {
      return false;
    }
    final RootFrameLayout root = Views.findAncestor(anchor, RootFrameLayout.class, true);
    if (root == null) {
      return false;
    }
    // paneInsets is from the root's sides: into the popup's coordinates through the screen
    final int insetLeft = xgramPane[0], insetRight = xgramPane[1];
    root.getLocationOnScreen(xgramLocation);
    final int rootX = xgramLocation[0];
    parent.getLocationOnScreen(xgramLocation);
    final int paneLeft = rootX + insetLeft - xgramLocation[0];
    final int paneRight = rootX + root.getMeasuredWidth() - insetRight - xgramLocation[0];
    final int anchorX = xgramAnchorRect.centerX();
    if (anchorX < paneLeft || anchorX > paneRight) {
      return false; // the anchor is in the other pane (the chat list)
    }
    final int margin = Screen.dp(XgramPopupBounds.MARGIN);
    xgramPane[0] = paneLeft + margin - sx;
    xgramPane[1] = paneRight - margin + sx - width;
    return xgramPane[0] <= xgramPane[1];
  }

  /** The anchor went away while the menu shows (the chat list's pane removed on folding): its popup hides, once. */
  private void xgramDismissForLostAnchor () {
    if (xgramPopupAnchor == null || xgramAnchorLost) {
      return;
    }
    final PopupLayout popup = PopupLayout.parentOf(this);
    if (popup != null) {
      xgramAnchorLost = true;
      post(() -> popup.hideWindow(true)); // not from inside a layout or a pre-draw pass
    }
  }

  private final ViewTreeObserver.OnPreDrawListener xgramTracker = () -> {
    xgramTrack();
    return true;
  };

  /** Before each frame: follows a moving anchor, measuring again when the room on the menu's side changes its height. */
  private void xgramTrack () {
    if (xgramPopupAnchor != null) {
      if (!xgramDirectionLatched || isLayoutRequested()) {
        return;
      }
      if (!xgramMeasurePopupAnchor()) {
        xgramDismissForLostAnchor();
        return;
      }
      if (!xgramAnchorRect.equals(xgramPlacedAnchorRect) || !xgramSafeRect.equals(xgramPlacedSafeRect)) {
        xgramPlaceAtPopupAnchor(); // now, at the current height (a new measure places it again)
      }
      // The cap from the current layout, also when the anchor is where the last layout put the menu: the measure in
      // the pass that moved it (the keyboard hiding, a fold or a rotation) still saw its old place and the old size
      final int cap = Math.min(xgramMaxHeight > 0 ? xgramMaxHeight : Integer.MAX_VALUE, Math.max(xgramMinHeight(), xgramOpensUp ? xgramRoomAbove() : xgramRoomBelow()));
      if (cap != xgramMeasuredCap && Math.min(cap, getItemsHeight()) != getMeasuredHeight()) {
        requestLayout();
      }
    } else if (xgramBoundToSafeArea && getTranslationY() != xgramMeasuredTranslationY && !isLayoutRequested()) {
      requestLayout();
    }
  }

  private void xgramInvalidateSelection () {
    invalidate();
    if (itemsLayout != null && itemsLayout != this) {
      itemsLayout.invalidate(); // the wrap is a hardware layer: the scroll view's column redraws its own highlight
    }
  }

  /** XGram: the drag-to-select highlight on the visible rows' slots: the first at ({@code x}, {@code y}), each {@code width} wide. */
  private void xgramDrawSelection (Canvas canvas, int x, int y, int width) {
    if (lastSelectedIndex == -1) {
      return;
    }
    final int itemHeight = Screen.dp(ITEM_HEIGHT);
    final int count = getVisibleItemCount(); // slots of the visible rows (see processMoveEvent)
    final float factor = factorAnimator.getFactor();
    if (XgramExpressive.isEnabled()) {
      // The same rounded state layer as a press
      final int baseColor = xgramRole(XgramExpressive.ON_SURFACE); // M3E r6: the forced theme's scheme over media
      for (int i = 0; i < count; i++) {
        final float alpha = MathUtils.clamp(1f - Math.abs(factor - i));
        if (alpha > 0f) {
          canvas.save();
          canvas.translate(x, y + itemHeight * i);
          xgramDrawItemPill(canvas, width, itemHeight, XgramExpressive.withAlpha(baseColor, alpha * XgramExpressive.STATE_PRESSED));
          canvas.restore();
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        final float alpha = MathUtils.clamp(1f - Math.abs(factor - i));
        if (alpha > 0f) {
          canvas.drawRect(x, y + itemHeight * i, x + width, y + itemHeight * (i + 1), Paints.fillingPaint(ColorUtils.alphaColor(alpha * 0.05f, Theme.getColor(ColorId.text))));
        }
      }
    }
  }

  private void setSelectedIndex (int index) {
    if (index == lastSelectedIndex) return;
    if (index != -1 && XgramExpressive.isEnabled()) {
      performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // XGram M3E r6: a tick per row under the finger
    }
    if (lastSelectedIndex == -1 || index == -1) {
      factorAnimator.forceFactor(index);
    } else {
      factorAnimator.animateTo(index);
    }
    lastSelectedIndex = index;
    xgramInvalidateSelection(); // XGram: the scroll view's column too
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

  /** XGram M3E r6: {@code role} in the current theme, or in the forced theme's scheme (a menu over media). */
  private int xgramRole (int role) {
    return com.fahadali.xgram.appearance.XgramR6B3Shell.color(role, forcedTheme);
  }

  /** XGram M3E r6: rows made checkable since this menu was last filled (updateItem makes them plain again). */
  private final java.util.ArrayList<View> xgramCheckable = new java.util.ArrayList<>();

  /**
   * XGram M3E r6: makes the row {@code id} a checkable menu item (a selection menu's "Show as one chat"): checked, it
   * sits on a secondaryContainer pill with a leading check, the label in onSecondaryContainer; unchecked, it is a
   * plain row. Call after the menu is filled (before it is laid out). Nothing with Expressive design off.
   */
  public void xgramSetCheckable (int id, boolean checked) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    View view = null;
    for (int i = 0; i < itemsLayout.getChildCount(); i++) {
      if (itemsLayout.getChildAt(i).getId() == id) {
        view = itemsLayout.getChildAt(i);
        break;
      }
    }
    if (!(view instanceof TextView)) {
      return;
    }
    final TextView row = (TextView) view;
    if (!xgramCheckable.contains(row)) {
      xgramCheckable.add(row);
    }
    if (checked) {
      Drawable check = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_check_24));
      if (check != null) {
        check.setColorFilter(Paints.getColorFilter(xgramRole(XgramExpressive.ON_SECONDARY_CONTAINER)));
      }
      if (Lang.rtl()) {
        row.setCompoundDrawablesWithIntrinsicBounds(null, null, check, null);
      } else {
        row.setCompoundDrawablesWithIntrinsicBounds(check, null, null, null);
      }
      row.setTextColor(xgramRole(XgramExpressive.ON_SECONDARY_CONTAINER));
      final int paddingLeft = row.getPaddingLeft(), paddingTop = row.getPaddingTop(), paddingRight = row.getPaddingRight(), paddingBottom = row.getPaddingBottom();
      final float radius = Screen.dp(XgramExpressive.CORNER_M);
      final int inset = Screen.dp(4f);
      GradientDrawable pill = new GradientDrawable();
      pill.setColor(xgramRole(XgramExpressive.SECONDARY_CONTAINER));
      pill.setCornerRadius(radius);
      GradientDrawable mask = new GradientDrawable();
      mask.setColor(0xffffffff);
      mask.setCornerRadius(radius);
      ViewUtils.setBackground(row, new RippleDrawable(ColorStateList.valueOf(XgramExpressive.withAlpha(xgramRole(XgramExpressive.ON_SECONDARY_CONTAINER), XgramExpressive.STATE_PRESSED)),
        new InsetDrawable(pill, inset), new InsetDrawable(mask, inset)));
      row.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    } else {
      row.setTextColor(xgramRole(XgramExpressive.ON_SURFACE));
      xgramSetItemBackground(row);
    }
    row.setSelected(checked);
    row.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    row.setTag(row.getMeasuredWidth());
    requestLayout();
  }

  private void xgramSetItemBackground (View view) {
    final float radius = Screen.dp(XgramExpressive.CORNER_M);
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(radius);
    final int baseColor = xgramRole(XgramExpressive.ON_SURFACE); // XGram M3E r6: the forced theme's scheme over media
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

    if (itemsLayout == this) {
      // XGram: the drag-to-select highlight (a scrollable menu draws it in its scroll view's column)
      xgramDrawSelection(canvas, Screen.dp(PADDING), Screen.dp(PADDING), getMeasuredWidth() - Screen.dp(PADDING) * 2);
    }


    super.onDraw(canvas);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    complexAvatarReceiver.attach();
    getViewTreeObserver().addOnPreDrawListener(xgramTracker); // XGram: follows a moving anchor
    if (scrollView != null) {
      scrollView.scrollTo(0, 0); // XGram: a reused menu shows its first rows again
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    getViewTreeObserver().removeOnPreDrawListener(xgramTracker); // XGram: still the window's observer here
    super.onDetachedFromWindow();
    complexAvatarReceiver.detach();
  }
}