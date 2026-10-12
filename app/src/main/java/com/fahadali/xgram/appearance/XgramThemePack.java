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

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperties;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import me.vkryl.core.ColorUtils;

/**
 * XGram's themes pack: ready-made themes shipped as Telegram X theme files ({@code assets/xgram_themes}). Applying one
 * installs it once as an ordinary custom theme (so it can be edited in Settings → Themes) and switches to it.
 */
public final class XgramThemePack {
  private XgramThemePack () { }

  /** Raise when the theme files change, so installed copies are replaced by the new colors. */
  private static final int PACK_VERSION = 2;
  private static final String KEY_ID = "xgram_pack_id_", KEY_VERSION = "xgram_pack_v_";

  /** The pack, as files in {@code assets/xgram_themes} (written by tools/xgram/gen_themes.py). */
  public static final String[] PACK_KEYS = {"pack_amoled", "pack_nord", "pack_dracula", "pack_mocha", "pack_sakura", "pack_latte", "pack_paper"};
  /** Themes matching the app icons: {@code icon_<manifest alias>}; the Classic icon goes with Telegram X's Blue. */
  public static final String[] ICON_KEYS = {
    "icon_default", "icon_aqua", "icon_sunset", "icon_mono", "icon_emerald", "icon_gold", "icon_violet", "icon_rose",
    "icon_night", "icon_light", "icon_neon", "icon_turbo", "icon_retro", "icon_pixel", "icon_origami", "icon_stardust"
  };
  /** Names of the first pack (before the icon themes); their installed copies are replaced. */
  private static final String[] OLD_KEYS = {"amoled", "midnight", "nord", "dracula", "mocha", "sakura", "latte", "paper"};

  /** The theme that goes with an app icon, or null for Classic (Telegram X's own Blue theme). */
  @Nullable
  public static String themeKeyForIcon (com.fahadali.xgram.appicons.XgramAppIcons.Icon icon) {
    String key = "icon_" + icon.id.toLowerCase(java.util.Locale.ROOT);
    for (String k : ICON_KEYS) {
      if (k.equals(key)) {
        return k;
      }
    }
    return null;
  }

  /** Switches to the theme of an app icon (Telegram X's Blue for Classic). Reads the file in the background. */
  public static void applyForIcon (ViewController<?> context, com.fahadali.xgram.appicons.XgramAppIcons.Icon icon) {
    String key = themeKeyForIcon(icon);
    if (key == null) {
      ThemeManager themes = ThemeManager.instance();
      themes.changeGlobalTheme(context.tdlib(), themes.getTheme(ThemeId.BLUE), false, null);
      return;
    }
    Context appContext = context.context();
    org.thunderdog.challegram.core.Background.instance().post(() -> {
      TdlibUi.ImportedTheme theme = read(appContext, key);
      if (theme != null) {
        context.runOnUiThreadOptional(() -> apply(context, key, theme, null));
      }
    });
  }

  /** Removes the copies installed by the first pack, unless one is in use. */
  public static void cleanUpOldPack (org.thunderdog.challegram.telegram.Tdlib tdlib) {
    ThemeManager themes = ThemeManager.instance();
    for (String key : OLD_KEYS) {
      int themeId = installedId(key);
      if (themeId != ThemeId.NONE && themes.currentThemeId() != themeId && tdlib.settings().globalDaylightTheme() != themeId && tdlib.settings().globalNightTheme() != themeId) {
        themes.removeCustomTheme(tdlib, themeId, ThemeId.BLUE, null);
      }
      if (themeId == ThemeId.NONE || themes.currentThemeId() != themeId) {
        Settings.instance().remove(KEY_ID + key);
        Settings.instance().remove(KEY_VERSION + key);
      }
    }
  }

  /** Reads a theme of the pack. Call on a background thread. */
  @Nullable
  public static TdlibUi.ImportedTheme read (Context context, String key) {
    Map<String, Integer> propertyMap = ThemeProperties.getMap();
    Map<String, Integer> colorMap = ThemeColors.getMap();
    TdlibUi.ImportedTheme theme = new TdlibUi.ImportedTheme();
    int section = 0; // 1 main, 2 properties, 3 colors, as in Telegram X's own reader
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("xgram_themes/" + key + ".tgx-theme"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        switch (line.charAt(0)) {
          case '!': section = 1; continue;
          case '@': section = 2; continue;
          case '#': section = 3; continue;
        }
        int split = line.indexOf(':');
        if (section == 0 || split == -1) {
          continue;
        }
        String[] names = line.substring(0, split).split(",");
        String value = line.substring(split + 1).trim();
        if (section == 1) {
          String field = names[0].trim();
          if ("name".equals(field)) {
            theme.name = Strings.unwrap("name", value).trim();
          } else if ("wallpaper".equals(field)) {
            theme.wallpaper = Strings.unwrap("wallpaper", value).trim();
          }
        } else if (section == 2) {
          float number = Float.parseFloat(value);
          for (String name : names) {
            Integer id = propertyMap.get(name.trim());
            if (id != null) {
              theme.addProperty(name.trim(), id, number);
            }
          }
        } else {
          int color = ColorUtils.parseHexColor(value.startsWith("#") ? value.substring(1) : value, true);
          for (String name : names) {
            Integer id = colorMap.get(name.trim());
            if (id != null) {
              theme.addColor(name.trim(), id, color);
            }
          }
        }
      }
      theme.checkValidnessAndPrepare();
      return theme;
    } catch (Throwable t) {
      Log.e("XGram: cannot read pack theme %s", t, key);
      return null;
    }
  }

  private static int installedId (String key) {
    int themeId = Settings.instance().getInt(KEY_ID + key, ThemeId.NONE);
    if (themeId == ThemeId.NONE || !ThemeManager.isCustomTheme(themeId) || !Settings.instance().hasCustomTheme(ThemeManager.resolveCustomThemeId(themeId))) {
      return ThemeId.NONE;
    }
    return themeId;
  }

  public static boolean isCurrent (String key) {
    int themeId = installedId(key);
    return themeId != ThemeId.NONE && ThemeManager.instance().currentThemeId() == themeId;
  }

  /** Switches to the theme, installing it first if needed. {@code theme} is the parsed file. */
  public static void apply (ViewController<?> context, String key, TdlibUi.ImportedTheme theme, @Nullable Runnable after) {
    ThemeManager themes = ThemeManager.instance();
    int themeId = installedId(key);
    if (themeId != ThemeId.NONE && themes.currentThemeId() == themeId) {
      if (after != null) {
        after.run();
      }
      return;
    }
    boolean upToDate = Settings.instance().getInt(KEY_VERSION + key, 0) == PACK_VERSION;
    if (themeId != ThemeId.NONE && upToDate) {
      // Installed already: use the stored copy, which keeps any edits made in Settings → Themes.
      themes.changeGlobalTheme(context.tdlib(), themes.getTheme(themeId), false, after);
      return;
    }
    // Like Telegram X's own theme install: have the wallpaper ready first (a gradient is built locally, at once).
    context.tdlib().wallpaper().loadWallpaper(theme.wallpaper, 1000, () -> {
      if (!context.isDestroyed()) {
        install(context, key, theme, themeId, after);
      }
    });
  }

  private static void install (ViewController<?> context, String key, TdlibUi.ImportedTheme theme, int themeId, @Nullable Runnable after) {
    ThemeManager themes = ThemeManager.instance();
    int newThemeId = themes.installCustomTheme(theme);
    if (newThemeId == ThemeId.NONE || theme.theme == null) {
      org.thunderdog.challegram.tool.UI.showToast(org.thunderdog.challegram.R.string.XgramThemeFailed, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    Settings.instance().putInt(KEY_ID + key, newThemeId);
    Settings.instance().putInt(KEY_VERSION + key, PACK_VERSION);
    final int oldThemeId = themeId;
    themes.changeGlobalTheme(context.tdlib(), theme.theme, false, () -> {
      if (oldThemeId != ThemeId.NONE && oldThemeId != newThemeId) {
        // The pack's colors changed: the older copy goes.
        themes.removeCustomTheme(context.tdlib(), oldThemeId, newThemeId, null);
      }
      if (after != null) {
        after.run();
      }
    });
  }
}
