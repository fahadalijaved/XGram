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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * XGram M3E round 5, the chat canvas: helpers for the chat screen and its preview modes (wallpaper picker, text size,
 * event log, search) with Expressive design. Everything here is only used while {@link XgramExpressive#isEnabled()}.
 */
public final class XgramR5Chat {
  private XgramR5Chat () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // The chat chip: one spec for the date chips, merged forum topic headers, the unread divider and service pills

  /** Height (dp) of a one-line chat chip: labelMedium centred in it, full corners. */
  public static final float CHIP_HEIGHT = 26f;
  /** Horizontal padding (dp) between the chip's edge and its label. */
  public static final float CHIP_PADDING = 12f;

  // Strings

  /**
   * Telegram X's string {@code tgxRes}, or the sentence-case {@code xgramRes} while Expressive design is on and the
   * language is the bundled English one (Telegram X's English is title case); a translation is kept as it is.
   */
  @NonNull
  public static String sentenceCase (@StringRes int tgxRes, @StringRes int xgramRes) {
    final String tgx = Lang.getString(tgxRes);
    if (!on()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(xgramRes);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  // Wallpaper picker

  /**
   * The note above the wallpaper picker while Pure black draws (on, and the theme is dark): bodySmall in
   * onSurfaceVariant on the picker's surface, 16dp from the sides. The picker itself shows the real colors; the chat
   * then shows color backgrounds as black. Null when Pure black does not draw.
   */
  @Nullable
  public static TextView newPureBlackNote (@NonNull Context context, @Nullable ViewController<?> themeProvider) {
    if (!on() || !XgramAmoled.isActive()) {
      return null;
    }
    TextView note = new TextView(context);
    XgramExpressive.applyText(note, XgramExpressive.TS_BODY_S, false);
    note.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(note, ColorId.textLight); // onSurfaceVariant
    }
    note.setText(Lang.getString(R.string.XgramR5ChatPureBlackNote));
    note.setGravity(Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    note.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    int side = Screen.dp(16f);
    note.setPadding(side, Screen.dp(8f), side, Screen.dp(4f));
    ViewSupport.setThemedBackground(note, ColorId.filling, themeProvider);
    note.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return note;
  }
}
