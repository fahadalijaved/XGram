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
package com.fahadali.xgram.appicons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;

/**
 * Alternative launcher icons (like exteraGram and AyuGram). Each icon is an {@code activity-alias} of MainActivity in
 * the manifest; exactly one is enabled.
 */
public final class XgramAppIcons {
  private XgramAppIcons () { }

  public static final class Icon {
    public final String id;
    @DrawableRes public final int iconRes;
    @StringRes public final int nameRes;
    /** A style (Neon, Pixel…) rather than a color of the standard design. */
    public final boolean isStyle;

    Icon (String id, @DrawableRes int iconRes, @StringRes int nameRes) {
      this(id, iconRes, nameRes, false);
    }

    Icon (String id, @DrawableRes int iconRes, @StringRes int nameRes, boolean isStyle) {
      this.id = id;
      this.iconRes = iconRes;
      this.nameRes = nameRes;
      this.isStyle = isStyle;
    }
  }

  public static final Icon[] ICONS = {
    // The ids are the manifest aliases (kept so a chosen icon survives updates); the names are the designs.
    new Icon("Default", R.mipmap.xgram_icon_default, R.string.XgramIconDefault),
    new Icon("Aqua", R.mipmap.xgram_icon_aqua, R.string.XgramIconAqua),
    new Icon("Sunset", R.mipmap.xgram_icon_sunset, R.string.XgramIconSunset),
    new Icon("Mono", R.mipmap.xgram_icon_mono, R.string.XgramIconMono),
    new Icon("Emerald", R.mipmap.xgram_icon_emerald, R.string.XgramIconEmerald),
    new Icon("Gold", R.mipmap.xgram_icon_gold, R.string.XgramIconGold),
    new Icon("Violet", R.mipmap.xgram_icon_violet, R.string.XgramIconViolet),
    new Icon("Rose", R.mipmap.xgram_icon_rose, R.string.XgramIconRose),
    new Icon("Night", R.mipmap.xgram_icon_night, R.string.XgramIconNight),
    new Icon("Light", R.mipmap.xgram_icon_light, R.string.XgramIconLight),
    new Icon("Classic", R.mipmap.app_launcher, R.string.XgramIconClassic),
    new Icon("Neon", R.mipmap.xgram_icon_neon, R.string.XgramIconNeon, true),
    new Icon("Turbo", R.mipmap.xgram_icon_turbo, R.string.XgramIconTurbo, true),
    new Icon("Retro", R.mipmap.xgram_icon_retro, R.string.XgramIconRetro, true),
    new Icon("Pixel", R.mipmap.xgram_icon_pixel, R.string.XgramIconPixel, true),
    new Icon("Origami", R.mipmap.xgram_icon_origami, R.string.XgramIconOrigami, true),
    new Icon("Stardust", R.mipmap.xgram_icon_stardust, R.string.XgramIconStardust, true),
  };

  private static ComponentName componentOf (Context context, Icon icon) {
    // Aliases are declared relative to Telegram X's namespace, the app id is XGram's.
    String namespace = MainActivity.class.getPackage().getName();
    return new ComponentName(context.getPackageName(), namespace + ".XgramIcon" + icon.id);
  }

  private static boolean isEnabled (Context context, Icon icon) {
    int state = context.getPackageManager().getComponentEnabledSetting(componentOf(context, icon));
    if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
      return icon == ICONS[0]; // as declared in the manifest
    }
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
  }

  public static Icon current (Context context) {
    for (Icon icon : ICONS) {
      try {
        if (isEnabled(context, icon)) {
          return icon;
        }
      } catch (Throwable ignored) { }
    }
    return ICONS[0];
  }

  /** Switches the launcher icon. Launchers may take a few seconds and remove home screen shortcuts of the old one. */
  public static void apply (Context context, Icon selected) {
    PackageManager pm = context.getPackageManager();
    // Enable the new one first, so there's never no launcher entry.
    pm.setComponentEnabledSetting(componentOf(context, selected), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    for (Icon icon : ICONS) {
      if (icon != selected) {
        pm.setComponentEnabledSetting(componentOf(context, icon), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
      }
    }
  }
}
