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
 * File created on 01/04/2017
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class WallpaperView extends View implements ThemeChangeListener, ChatStyleChangeListener, FactorAnimator.Target, TdlibCache.MyUserDataChangeListener, Destroyable {
  private final MessagesManager manager;
  private final Tdlib tdlib;

  private DoubleImageReceiver receiver;
  private DoubleImageReceiver preview;

  private @Nullable
  TGBackground wallpaper, previewWallpaper;

  private boolean inSetupMode, inSelfBlurMode, selfBlurValue;

  public WallpaperView (Context context, MessagesManager manager, Tdlib tdlib) {
    super(context);
    this.manager = manager;
    this.tdlib = tdlib;
    this.receiver = new DoubleImageReceiver(this, 0);
  }

  public void initWithSetupMode (boolean inSetupMode) {
    this.inSetupMode = inSetupMode;
    preview = new DoubleImageReceiver(this, 0);
    setWallpaper(tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier()), false);
    ThemeManager.instance().addThemeListener(this);
    ThemeManager.instance().addChatStyleListener(this);
    tdlib.cache().addMyUserListener(this);
  }

  public void initWithCustomWallpaper (TGBackground wallpaper) {
    this.inSetupMode = true;
    preview = new DoubleImageReceiver(this, 0);
    setWallpaper(wallpaper, false);
  }

  public void setSelfBlur (boolean value) {
    inSelfBlurMode = true;
    selfBlurValue = value;
    ThemeManager.instance().addChatStyleListener(this);
  }

  @Override
  public void onMyUserUpdated (final TdApi.User myUser) {
    UI.post(() -> {
      if (myUser != null && scheduledWallpaper != null) {
        setWallpaper(scheduledWallpaper, scheduledWallpaperAnimated);
      }
    });
  }

  private TGBackground scheduledWallpaper;
  private boolean scheduledWallpaperAnimated;

  private void setWallpaper (TGBackground wallpaper, boolean animated) {
    if (wallpaper != null && wallpaper.isEmpty()) {
      wallpaper = null;
    }
    animated = animated && manager.controller().isFocused();
    if (tdlib.myUserId() == 0) {
      scheduledWallpaper = wallpaper;
      scheduledWallpaperAnimated = animated;
      return;
    }
    if (this.wallpaper != wallpaper) {
      if (animated) {
        if (TGBackground.compare(this.wallpaper, wallpaper, false)) {
          return;
        }
        this.previewWallpaper = wallpaper;
        preview.setAnimationDisabled(inSetupMode);
        requestFiles(preview, wallpaper);
        animateChange();
      } else {
        this.wallpaper = wallpaper;
        requestFiles(receiver, wallpaper);
        if (manager.useBubbles() && manager.getAdapter() != null) {
          manager.getAdapter().invalidateAllMessages();
        }
      }
    }
  }

  private void requestFiles (DoubleImageReceiver receiver, TGBackground wallpaper) {
    if (wallpaper != null) {
      wallpaper.requestFiles(receiver, !wallpaper.isPattern());
    } else {
      receiver.requestFile(null, null);
    }
  }

  @Override
  public boolean needsTempUpdates () {
    return false;
  }

  @Override
  public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    int newUsageIdentifier = Theme.getWallpaperIdentifier(newTheme);
    if (Theme.getWallpaperIdentifier(oldTheme) != newUsageIdentifier) {
      setWallpaper(tdlib.settings().getWallpaper(newUsageIdentifier), true);
    }
  }

  @Override
  public void onThemePropertyChanged (int themeId, @PropertyId int propertyId, float value, boolean isDefault) {
    switch (propertyId) {
      case PropertyId.WALLPAPER_USAGE_ID:
        setWallpaper(tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier(themeId)), true);
        break;
    }
  }

  @Override
  public void onThemeAutoNightModeChanged (int autoNightMode) { }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    requestFiles(receiver, this.wallpaper);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Pure black or the dynamic colors changed: the surface under the chat and every pill on it repaint
      if (manager.useBubbles() && manager.getAdapter() != null) {
        manager.getAdapter().invalidateAllMessages();
      }
      invalidate();
    }
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) {
    if (this.tdlib == tdlib) {
      requestFiles(receiver, this.wallpaper);
      manager.controller().updateShadowColor();
    }
  }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, TGBackground wallpaper, int usageIdentifier) {
    if (this.tdlib == tdlib && usageIdentifier == Theme.getWallpaperIdentifier()) {
      if (inSelfBlurMode) {
        wallpaper = TGBackground.newBlurredWallpaper(tdlib, wallpaper, selfBlurValue);
      }

      setWallpaper(wallpaper, true);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceivers();
  }

  private void layoutReceivers () {
    int width = getMeasuredWidth();
    int height = Math.max(Screen.currentActualHeight(), getMeasuredHeight());
    receiver.setBounds(0, 0, width, height);
    if (preview != null) {
      preview.setBounds(0, 0, width, height);
    }
  }

  private float factor;
  private FactorAnimator animator;

  private void animateChange () {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else if (animator.getFactor() == 1f) {
      animator.forceFactor(factor = 0f);
    }
    animator.animateTo(1f);
  }

  private boolean isAnimatingChanges () {
    return animator != null && animator.isAnimating();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.factor != factor) {
      this.factor = factor;
      if (manager.controller().inWallpaperMode() && manager.useBubbles() && manager.getAdapter() != null) {
        manager.getAdapter().invalidateAllMessages();
      }
      invalidate();
    }
  }

  public float getBackgroundTransparency () {
    if (!manager.useBubbles())
      return 0f;
    if (xgramSolidSurface()) {
      // XGram: the theme's wallpaper is hidden behind a solid surface, so overlays take their "no wallpaper" colors
      return 1f;
    }
    if (animator != null && animator.isAnimating()) {
      // XGram: a background drawn as the solid surface (xgramCovered) counts as no wallpaper
      if (previewWallpaper == null || xgramCovered(previewWallpaper)) {
        return factor;
      } else if (wallpaper == null || xgramCovered(wallpaper)) {
        return 1f - factor;
      } else {
        return 0f;
      }
    }
    return wallpaper != null && !xgramCovered(wallpaper) ? 0f : 1f;
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      wallpaper = previewWallpaper;
      previewWallpaper = null;

      DoubleImageReceiver tempReceiver = receiver;
      receiver = preview;
      receiver.setAnimationDisabled(false);
      preview = tempReceiver;

      factor = 0f;

      preview.clear();

      if (manager.useBubbles() && manager.getAdapter() != null) {
        manager.getAdapter().invalidateAllMessages();
      }
      invalidate();
    }
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    if (preview != null) {
      preview.destroy();
    }
    tdlib.cache().removeMyUserListener(this);
    ThemeManager.instance().removeThemeListener(this);
    ThemeManager.instance().removeChatStyleListener(this);
  }

  private static int getWallpaperBackground (ThemeDelegate theme) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: the placeholder under a loading wallpaper (and an unknown fill) is the M3 surface of the theme's scheme,
      // black with Pure black, never the theme's own baked grey
      return com.fahadali.xgram.appearance.XgramExpressive.colorFor(com.fahadali.xgram.appearance.XgramExpressive.SURFACE, theme.isDark());
    }
    return ColorUtils.compositeColor(theme.getColor(ColorId.background), theme.getColor(ColorId.bubble_chatBackground));
  }

  private final DrawAlgorithms.GradientCache gradientCache = new DrawAlgorithms.GradientCache();

  private static void drawWallpaper (TGBackground wallpaper, Canvas c, DrawAlgorithms.GradientCache gradientCache, ThemeDelegate theme, DoubleImageReceiver receiver, float alpha) {
    final int defaultColor = getWallpaperBackground(theme);
    if (wallpaper == null || wallpaper.isEmpty()) {
      c.drawColor(ColorUtils.alphaColor(alpha, defaultColor));
    } else if (wallpaper.isFillSolid()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
    } else if (wallpaper.isFillGradient()) {
      DrawAlgorithms.drawGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
    } else if (wallpaper.isFillFreeformGradient()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      DrawAlgorithms.drawMulticolorGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getFreeformColors(), alpha);
    } else if (wallpaper.isPattern()) {
      if (wallpaper.isPatternBackgroundGradient()) {
        DrawAlgorithms.drawGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
      } else if (wallpaper.isPatternBackgroundFreeformGradient()) {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
        DrawAlgorithms.drawMulticolorGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getFreeformColors(), alpha);
      } else {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      }
      receiver.getReceiver().setPorterDuffColorFilter(wallpaper.getPatternColor());
      alpha *= wallpaper.getPatternIntensity();
      if (alpha != 1f)
        receiver.setPaintAlpha(alpha);
      receiver.getReceiver().draw(c);
      if (alpha != 1f)
        receiver.restorePaintAlpha();
    } else {
      if (receiver.needPlaceholder()) {
        c.drawColor(ColorUtils.alphaColor(alpha, defaultColor));
      }
      receiver.disablePorterDuffColorFilter();
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }
  }

  public int getDefaultOverlayColor (int defaultColor, boolean text) {
    if (manager.useBubbles()) {
      if (!isAnimatingChanges() || factor == 0f) {
        return getDefaultOverlayColor(wallpaper, receiver, defaultColor, text);
      } else if (factor == 1f) {
        return getDefaultOverlayColor(previewWallpaper, preview, defaultColor, text);
      } else {
        return ColorUtils.fromToArgb(getDefaultOverlayColor(wallpaper, receiver, defaultColor, text), getDefaultOverlayColor(previewWallpaper, preview, defaultColor, text), factor);
      }
    }
    return defaultColor;
  }

  private static int getDefaultOverlayColor (TGBackground background, DoubleImageReceiver receiver, int defaultColor, boolean text) {
    if (background == null || background.isEmpty())
      return defaultColor;
    int previewColor = getColor(background, receiver.getPreview().getCurrentFile(), defaultColor, text);
    int targetColor = getColor(background, receiver.getImageReceiver().getCurrentFile(), defaultColor, text);
    return ColorUtils.fromToArgb(previewColor, targetColor, receiver.getFullLoadFactor());
  }

  private static int getColor (TGBackground background, ImageFile imageFile, int defaultColor, boolean text) {
    if (text)
      return defaultColor;
    int legacyOverlayColor = TGBackground.getLegacyOverlayColor(background.getLegacyWallpaperId(), 0);
    if (legacyOverlayColor != 0)
      return legacyOverlayColor;
    if (background.isPattern())
      return ColorUtils.color((int) ((float) OVERLAY_ALPHA * (.7f + .3f * background.getPatternIntensity())), background.getPatternColor()); // (int) (255f * (.4f + .2f * background.getPatternIntensity()))
    if (background.isFill())
      return ColorUtils.color(OVERLAY_ALPHA, background.getSolidOverlayColor());
    Palette.Swatch swatch = imageFile != null ? imageFile.getPaletteSwatch() : null;
    if (swatch == null)
      return defaultColor;
    return text ? ColorUtils.color(255, swatch.getTitleTextColor()) : ColorUtils.color(OVERLAY_ALPHA, swatch.getRgb());
  }

  public static final int OVERLAY_ALPHA = 0x70;

  /**
   * XGram: Material 3 Expressive replaces the theme's own wallpaper (none, the built-in cats or the theme default) with
   * a solid surface, like Google Messages. A wallpaper the user picked still draws; previews always draw.
   */
  private boolean xgramSolidSurface () {
    if (inSetupMode || !com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return false;
    }
    if (isAnimatingChanges() && !xgramCovered(previewWallpaper)) {
      return false;
    }
    return xgramCovered(wallpaper);
  }

  /**
   * XGram: Expressive design draws {@code background} as the solid surface: the theme's own wallpaper
   * ({@link #xgramIsDefault}) and, with Pure black, every color background ({@link #xgramPureBlack}). Never in the
   * wallpaper picker or a wallpaper preview (setup mode), which show the real colors. Cheap, allocation free.
   */
  private boolean xgramCovered (@Nullable TGBackground background) {
    if (inSetupMode || !com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return false;
    }
    return xgramIsDefault(background) || xgramPureBlack(background);
  }

  /**
   * XGram: Pure black is drawing (on, and the theme is dark) and {@code background} is empty, a fill, a pattern or the
   * theme's default: the chat is then the black surface, so nothing grey or tinted shows around the bubbles. Photo
   * and custom (gallery) backgrounds still draw as picked.
   */
  private boolean xgramPureBlack (@Nullable TGBackground background) {
    if (inSetupMode || !com.fahadali.xgram.appearance.XgramAmoled.isActive()) {
      return false;
    }
    if (background == null || background.isEmpty() || background.isFill() || background.isPattern()) {
      return true;
    }
    return xgramIsDefault(background);
  }

  /**
   * XGram: the background is the theme's own (none, the built-in cats, the legacy built-ins, or the theme's default
   * wallpaper), not one the user picked. The default's name is compared without its parameters ("slug?intensity=..",
   * how themes store it) on both sides.
   */
  private static boolean xgramIsDefault (@Nullable TGBackground background) {
    if (background == null || background.isEmpty() || background.isBuiltIn() || background.isCat() || background.isLegacy()) {
      return true;
    }
    if (background.isCustom()) {
      return false;
    }
    return org.thunderdog.challegram.telegram.TdlibWallpaperManager.isSameWallpaperName(background.getName(), Theme.getDefaultWallpaper());
  }

  /** XGram: one wallpaper layer, or the solid surface in its place when Expressive design covers it. */
  private void xgramDrawLayer (Canvas c, @Nullable TGBackground background, ThemeDelegate theme, DoubleImageReceiver receiver, float alpha) {
    if (xgramCovered(background)) {
      c.drawColor(ColorUtils.alphaColor(alpha, com.fahadali.xgram.appearance.XgramExpressive.colorFor(com.fahadali.xgram.appearance.XgramExpressive.SURFACE, theme.isDark())));
    } else {
      drawWallpaper(background, c, gradientCache, theme, receiver, alpha);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (xgramSolidSurface()) {
      c.drawColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE));
      return;
    }
    if (manager.useBubbles()) {
      layoutReceivers();
      if (!isAnimatingChanges()) {
        xgramDrawLayer(c, wallpaper, ThemeManager.instance().currentTheme(), receiver, 1f);
      } else if (factor == 0f) {
        xgramDrawLayer(c, wallpaper, ThemeManager.instance().previousTheme(), receiver, 1f);
      } else if (factor == 1f) {
        xgramDrawLayer(c, previewWallpaper, ThemeManager.instance().appliedTheme(), preview, 1f);
      } else {
        xgramDrawLayer(c, wallpaper, ThemeManager.instance().previousTheme(), receiver, 1f);
        xgramDrawLayer(c, previewWallpaper, ThemeManager.instance().appliedTheme(), preview, factor);
      }
    } else {
      c.drawColor(Theme.getColor(ColorId.chatBackground));
    }
  }
}
