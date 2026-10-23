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

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Material You themes (exteraGram / NagramX "Monet"): a day and a night theme built from the system's dynamic colors
 * (Android 12+), as ordinary Telegram X custom themes. A switch: turning it off goes back to the themes used before,
 * and while it's on the themes are rebuilt when the wallpaper colors change.
 */
public final class XgramMaterialYou {
  private XgramMaterialYou () { }

  private static final String KEY_DAY = "xgram_monet_day", KEY_NIGHT = "xgram_monet_night";
  private static final String KEY_PREV_DAY = "xgram_monet_prev_day", KEY_PREV_NIGHT = "xgram_monet_prev_night";
  private static final String KEY_SEED = "xgram_monet_seed";
  /** Whether the themes were last built with Expressive design's colors (it is on by default, without a switch). */
  private static final String KEY_BUILT_EXPRESSIVE = "xgram_monet_m3e";

  public static boolean isAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
  }

  /** Whether a Material You theme is the one in use. */
  public static boolean isEnabled () {
    int day = Settings.instance().getInt(KEY_DAY, 0), night = Settings.instance().getInt(KEY_NIGHT, 0);
    if (day == 0 && night == 0) {
      return false;
    }
    int current = ThemeManager.instance().currentThemeId();
    return current == day || current == night;
  }

  /** Turns Material You on or off; {@code after} runs once the theme has changed. */
  public static void toggle (ViewController<?> context, @Nullable Runnable after) {
    if (!isAvailable()) {
      UI.showToast(R.string.XgramMaterialYouUnavailable, Toast.LENGTH_SHORT);
      return;
    }
    if (isEnabled()) {
      disable(context.tdlib(), after);
    } else {
      apply(context.context(), context.tdlib(), after);
    }
  }

  /**
   * On resume: rebuilds the themes if the wallpaper (so the system palette) changed while XGram was away, or if they
   * were built for the other design (Expressive design on by default after an update: the switch never rebuilt them).
   */
  public static void checkWallpaper (BaseActivity activity) {
    XgramExpressive.checkSystemSeed(activity); // Expressive design draws with the dynamic colors whatever theme is in use
    if (!isAvailable() || !isEnabled()) {
      return;
    }
    try {
      String seed = seed(activity);
      String saved = Settings.instance().getString(KEY_SEED, null);
      boolean otherDesign = Settings.instance().getBoolean(KEY_BUILT_EXPRESSIVE, false) != XgramExpressive.isEnabled();
      if ((saved != null && !saved.equals(seed)) || otherDesign) {
        Tdlib tdlib = org.thunderdog.challegram.telegram.TdlibManager.instance().current();
        apply(activity, tdlib, null);
      } else if (saved == null) {
        Settings.instance().putString(KEY_SEED, seed);
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Material You refresh failed", t);
    }
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static String seed (Context context) {
    if (XgramColorSource.isCustom()) {
      return XgramColorSource.key(); // XGram: a colour picked in Appearance > Colours: the themes follow it, not the wallpaper
    }
    return Integer.toHexString(color(context, android.R.color.system_accent1_500)) + ":" +
      Integer.toHexString(color(context, android.R.color.system_accent2_500)) + ":" +
      Integer.toHexString(color(context, android.R.color.system_neutral1_500));
  }

  private static void apply (Context context, Tdlib tdlib, @Nullable Runnable after) {
    if (!isAvailable()) {
      return;
    }
    applyImpl(context, tdlib, after);
  }

  /** Builds the Material You themes again (Expressive design changes their colors); {@code after} always runs. */
  public static void rebuild (ViewController<?> context, @Nullable Runnable after) {
    if (!isAvailable() || !isEnabled()) {
      if (after != null) {
        after.run();
      }
      return;
    }
    apply(context.context(), context.tdlib(), after);
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static void applyImpl (Context context, Tdlib tdlib, @Nullable Runnable after) {
    ThemeManager themes = ThemeManager.instance();
    int oldDay = Settings.instance().getInt(KEY_DAY, 0), oldNight = Settings.instance().getInt(KEY_NIGHT, 0);
    if (!isEnabled()) {
      // Remember what to go back to when it's turned off.
      Settings.instance().putInt(KEY_PREV_DAY, tdlib.settings().globalDaylightTheme());
      Settings.instance().putInt(KEY_PREV_NIGHT, tdlib.settings().globalNightTheme());
    }

    int day = themes.newCustomTheme(context.getString(R.string.XgramMaterialYouDay), ThemeId.BLUE, ThemeId.NONE);
    fill(context, ThemeManager.resolveCustomThemeId(day), false);
    int night = themes.newCustomTheme(context.getString(R.string.XgramMaterialYouNight), ThemeId.NIGHT_BLACK, ThemeId.NONE);
    fill(context, ThemeManager.resolveCustomThemeId(night), true);
    Settings.instance().putInt(KEY_DAY, day);
    Settings.instance().putInt(KEY_NIGHT, night);
    Settings.instance().putString(KEY_SEED, seed(context));
    Settings.instance().putBoolean(KEY_BUILT_EXPRESSIVE, XgramExpressive.isEnabled()); // as fill() built them

    boolean dark = themes.isCurrentThemeDark();
    // Register the other one first, so night mode switching uses both.
    tdlib.settings().setGlobalTheme(dark ? day : night);
    themes.changeGlobalTheme(tdlib, themes.getTheme(dark ? night : day), true, () -> {
      if (oldDay != 0 && oldDay != day) {
        themes.removeCustomTheme(tdlib, oldDay, day, null);
      }
      if (oldNight != 0 && oldNight != night) {
        themes.removeCustomTheme(tdlib, oldNight, night, null);
      }
      if (after != null) {
        after.run();
      }
    });
  }

  private static void disable (Tdlib tdlib, @Nullable Runnable after) {
    ThemeManager themes = ThemeManager.instance();
    int day = Settings.instance().getInt(KEY_DAY, 0), night = Settings.instance().getInt(KEY_NIGHT, 0);
    int prevDay = Settings.instance().getInt(KEY_PREV_DAY, ThemeManager.DEFAULT_LIGHT_THEME);
    int prevNight = Settings.instance().getInt(KEY_PREV_NIGHT, ThemeManager.DEFAULT_DARK_THEME);
    // Never "go back" to a Material You theme, or to a custom theme that was deleted since.
    if (prevDay == 0 || prevDay == day || prevDay == night || !exists(prevDay)) {
      prevDay = ThemeManager.DEFAULT_LIGHT_THEME;
    }
    if (prevNight == 0 || prevNight == day || prevNight == night || !exists(prevNight)) {
      prevNight = ThemeManager.DEFAULT_DARK_THEME;
    }
    boolean dark = themes.isCurrentThemeDark();
    final int restoreDay = prevDay, restoreNight = prevNight;
    tdlib.settings().setGlobalTheme(dark ? restoreDay : restoreNight);
    themes.changeGlobalTheme(tdlib, themes.getTheme(dark ? restoreNight : restoreDay), true, () -> {
      Settings.instance().remove(KEY_DAY);
      Settings.instance().remove(KEY_NIGHT);
      Settings.instance().remove(KEY_SEED);
      Settings.instance().remove(KEY_BUILT_EXPRESSIVE);
      if (day != 0) {
        themes.removeCustomTheme(tdlib, day, restoreDay, null);
      }
      if (night != 0) {
        themes.removeCustomTheme(tdlib, night, restoreNight, null);
      }
      if (after != null) {
        after.run();
      }
    });
  }

  private static boolean exists (int themeId) {
    if (!ThemeManager.isCustomTheme(themeId)) {
      return true;
    }
    return Settings.instance().hasCustomTheme(ThemeManager.resolveCustomThemeId(themeId));
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static void fill (Context context, int customThemeId, boolean dark) {
    @ColorInt int accent = color(context, dark, dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
    @ColorInt int accentSoft = color(context, dark, dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
    @ColorInt int onAccent = color(context, dark, dark ? android.R.color.system_accent1_900 : android.R.color.system_accent1_0);
    @ColorInt int surface = color(context, dark, dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_10);
    @ColorInt int background = color(context, dark, dark ? android.R.color.system_neutral2_900 : android.R.color.system_neutral2_50);
    @ColorInt int header = color(context, dark, dark ? android.R.color.system_neutral1_900 : android.R.color.system_accent1_600);
    @ColorInt int onHeader = color(context, dark, dark ? android.R.color.system_neutral1_100 : android.R.color.system_accent1_0);
    @ColorInt int bubbleOut = color(context, dark, dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_100);
    @ColorInt int bubbleOutText = color(context, dark, dark ? android.R.color.system_accent1_50 : android.R.color.system_accent1_900);
    @ColorInt int bubbleIn = color(context, dark, dark ? android.R.color.system_neutral2_800 : android.R.color.system_neutral1_10);

    int[] accentIds = {
      ColorId.textNeutral, ColorId.textLink, ColorId.iconActive, ColorId.progress, ColorId.controlActive,
      ColorId.checkActive, ColorId.sliderActive, ColorId.togglerActive, ColorId.inputActive, ColorId.circleButtonRegular,
      ColorId.circleButtonChat, ColorId.seekDone, ColorId.fillingPositive, ColorId.chatListAction, ColorId.badge,
      ColorId.messageAuthor, ColorId.ticks, ColorId.ticksRead, ColorId.online, ColorId.messageVerticalLine, ColorId.inlineIcon,
      ColorId.unreadText
    };
    for (int id : accentIds) {
      set(customThemeId, id, accent);
    }
    set(customThemeId, ColorId.togglerActiveBackground, accentSoft);
    set(customThemeId, ColorId.fillingPositiveContent, onAccent);
    // Icons and text drawn on the accent (scroll-down and new-chat buttons, unread badges).
    set(customThemeId, ColorId.circleButtonRegularIcon, onAccent);
    set(customThemeId, ColorId.circleButtonChatIcon, onAccent);
    set(customThemeId, ColorId.circleButtonActive, accent);
    set(customThemeId, ColorId.circleButtonActiveIcon, onAccent);
    set(customThemeId, ColorId.circleButtonTheme, accent);
    set(customThemeId, ColorId.circleButtonThemeIcon, onAccent);
    set(customThemeId, ColorId.headerButton, accent);
    set(customThemeId, ColorId.headerButtonIcon, onAccent);
    set(customThemeId, ColorId.badgeText, onAccent);
    // Muted chats and the archive: the secondary palette instead of Telegram X's grey.
    set(customThemeId, ColorId.badgeMuted, color(context, dark, dark ? android.R.color.system_accent2_600 : android.R.color.system_accent2_300));
    set(customThemeId, ColorId.badgeMutedText, color(context, dark, dark ? android.R.color.system_accent2_50 : android.R.color.system_accent2_0));
    set(customThemeId, ColorId.chatListMute, color(context, dark, dark ? android.R.color.system_accent2_400 : android.R.color.system_accent2_400));
    set(customThemeId, ColorId.chatSendButton, accent);
    set(customThemeId, ColorId.chatKeyboardButton, accent);
    set(customThemeId, ColorId.unread, accentSoft);
    set(customThemeId, ColorId.bubbleOut_ticks, color(context, dark, dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600));
    set(customThemeId, ColorId.bubbleOut_ticksRead, color(context, dark, dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600));
    set(customThemeId, ColorId.bubbleOut_messageAuthor, bubbleOutText);
    set(customThemeId, ColorId.textSelectionHighlight, (accent & 0x00FFFFFF) | 0x40000000);
    set(customThemeId, ColorId.textLinkPressHighlight, (accent & 0x00FFFFFF) | 0x30000000);
    set(customThemeId, ColorId.messageSelection, (accent & 0x00FFFFFF) | 0x26000000);
    set(customThemeId, ColorId.filling, surface);
    set(customThemeId, ColorId.background, background);
    set(customThemeId, ColorId.headerBackground, header);
    set(customThemeId, ColorId.headerText, onHeader);
    set(customThemeId, ColorId.headerIcon, onHeader);
    set(customThemeId, ColorId.headerTabActive, onHeader);
    set(customThemeId, ColorId.headerTabActiveText, onHeader);
    set(customThemeId, ColorId.headerLightBackground, surface);
    set(customThemeId, ColorId.headerLightIcon, accent);
    set(customThemeId, ColorId.headerLightText, color(context, dark, dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900));
    set(customThemeId, ColorId.bubbleOut_background, bubbleOut);
    set(customThemeId, ColorId.bubbleOut_text, bubbleOutText);
    set(customThemeId, ColorId.bubbleIn_background, bubbleIn);
    if (XgramExpressive.isEnabled()) {
      fillExpressive(context, customThemeId, dark);
    }
  }

  /** Expressive design: Material 3 color roles for surfaces, the header, the FAB, switches and accents. */
  @RequiresApi(Build.VERSION_CODES.S)
  private static void fillExpressive (Context context, int customThemeId, boolean dark) {
    final int[][] roles = {
      {ColorId.filling, XgramExpressive.SURFACE},
      // The chat without a wallpaper: the surface, as Expressive draws it, not the night theme's baked grey
      {ColorId.chatBackground, XgramExpressive.SURFACE},
      {ColorId.bubble_chatBackground, XgramExpressive.SURFACE},
      {ColorId.background, XgramExpressive.SURFACE_CONTAINER},
      {ColorId.headerBackground, XgramExpressive.SURFACE_CONTAINER},
      {ColorId.headerText, XgramExpressive.ON_SURFACE},
      {ColorId.headerIcon, XgramExpressive.ON_SURFACE},
      {ColorId.headerTabActive, XgramExpressive.ON_SECONDARY_CONTAINER},
      {ColorId.headerTabActiveText, XgramExpressive.ON_SECONDARY_CONTAINER},
      {ColorId.headerTabInactiveText, XgramExpressive.ON_SURFACE_VARIANT},
      {ColorId.headerLightBackground, XgramExpressive.SURFACE_CONTAINER},
      {ColorId.circleButtonRegular, XgramExpressive.PRIMARY_CONTAINER},
      {ColorId.circleButtonActive, XgramExpressive.PRIMARY_CONTAINER},
      {ColorId.circleButtonRegularIcon, XgramExpressive.ON_PRIMARY_CONTAINER},
      {ColorId.circleButtonActiveIcon, XgramExpressive.ON_PRIMARY_CONTAINER},
      {ColorId.togglerActive, XgramExpressive.PRIMARY},
      {ColorId.togglerActiveBackground, XgramExpressive.PRIMARY},
      {ColorId.chatSendButton, XgramExpressive.PRIMARY},
      {ColorId.badge, XgramExpressive.PRIMARY},
      {ColorId.badgeText, XgramExpressive.ON_PRIMARY},
      {ColorId.progress, XgramExpressive.PRIMARY},
      {ColorId.sliderActive, XgramExpressive.PRIMARY}
    };
    for (int[] pair : roles) {
      set(customThemeId, pair[0], XgramExpressive.systemRole(context, pair[1], dark));
    }
    // The header is a surface now: no dark overlay on the status bar, and dark status bar icons in the day theme.
    set(customThemeId, ColorId.statusBar, 0);
    Settings.instance().setCustomThemeProperty(customThemeId, PropertyId.LIGHT_STATUS_BAR, dark ? 0f : 1f);
  }

  private static void set (int customThemeId, int colorId, @ColorInt int color) {
    Settings.instance().setCustomThemeColor(customThemeId, colorId, color);
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static int color (Context context, int res) {
    return context.getColor(res);
  }

  /**
   * A shade of the system palettes in the light or dark theme; with a colour picked in Appearance > Colours
   * (XgramColorSource), the same tone of the palette its scheme makes instead.
   */
  @RequiresApi(Build.VERSION_CODES.S)
  private static int color (Context context, boolean dark, int res) {
    if (XgramColorSource.isCustom()) {
      int custom = XgramColorSource.systemPaletteColor(res, dark);
      if (custom != 0) {
        return custom;
      }
    }
    return context.getColor(res);
  }
}
