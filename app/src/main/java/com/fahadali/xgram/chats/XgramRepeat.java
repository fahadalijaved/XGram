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
package com.fahadali.xgram.chats;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeDelegate;

import java.util.function.IntConsumer;

/**
 * XGram: repeating scheduled messages (Telegram Premium). A scheduled message can come back after a period; Telegram
 * accepts only these periods (MessageSchedulingStateSendAtDate.repeatPeriod, in seconds).
 */
public final class XgramRepeat {
  private XgramRepeat () { }

  private static final int DAY = 24 * 60 * 60;

  public static final int[] PERIODS = {DAY, 7 * DAY, 14 * DAY, 30 * DAY, 91 * DAY, 182 * DAY, 365 * DAY};

  private static final int[] IDS = {
    R.id.btn_xgramRepeatDaily, R.id.btn_xgramRepeatWeekly, R.id.btn_xgramRepeatBiweekly, R.id.btn_xgramRepeatMonthly,
    R.id.btn_xgramRepeat3Months, R.id.btn_xgramRepeat6Months, R.id.btn_xgramRepeatYearly
  };

  private static final int[] NAMES = {
    R.string.XgramRepeatDaily, R.string.XgramRepeatWeekly, R.string.XgramRepeatBiweekly, R.string.XgramRepeatMonthly,
    R.string.XgramRepeat3Months, R.string.XgramRepeat6Months, R.string.XgramRepeatYearly
  };

  /** "Every week" and the like for a repeat period, or null when the message doesn't repeat. */
  public static @Nullable String name (int repeatPeriod) {
    if (repeatPeriod <= 0) {
      return null;
    }
    for (int i = 0; i < PERIODS.length; i++) {
      if (PERIODS[i] == repeatPeriod) {
        return Lang.getString(NAMES[i]);
      }
    }
    // A period only a test server uses (1 or 5 minutes), or one Telegram adds later
    return Lang.getString(R.string.XgramRepeatEvery, Lang.getDuration(repeatPeriod));
  }

  /** The periods Telegram allows, as a sheet; the chosen one in seconds. */
  public static void pick (ViewController<?> context, @Nullable ThemeDelegate forcedTheme, IntConsumer onPicked) {
    final String[] titles = new String[NAMES.length];
    final int[] icons = new int[NAMES.length];
    for (int i = 0; i < NAMES.length; i++) {
      titles[i] = Lang.getString(NAMES[i]);
      icons[i] = R.drawable.baseline_repeat_24;
    }
    context.showOptions(Lang.getString(R.string.XgramRepeatTitle), IDS, titles, null, icons, (v, optionId) -> {
      for (int i = 0; i < IDS.length; i++) {
        if (IDS[i] == optionId) {
          onPicked.accept(PERIODS[i]);
          break;
        }
      }
      return true;
    }, forcedTheme);
  }
}
