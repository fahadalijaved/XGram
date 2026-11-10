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
package com.fahadali.xgram.business;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * XGram: a Telegram Business account's opening hours and location on its profile, like official Telegram: "Open now,
 * until 18:00" or "Closed, opens Monday at 9:00" (in this phone's time), the week on a tap, and the address that
 * opens in a maps app.
 */
public final class XgramBusinessProfile {
  private XgramBusinessProfile () { }

  private static final int DAY = 24 * 60, WEEK = 7 * DAY;

  private static @Nullable TdApi.BusinessOpeningHours hours (@Nullable TdApi.BusinessInfo info) {
    if (info == null) {
      return null;
    }
    final TdApi.BusinessOpeningHours hours = info.localOpeningHours != null ? info.localOpeningHours : info.openingHours;
    return hours != null && hours.openingHours != null && hours.openingHours.length > 0 ? hours : null;
  }

  public static boolean hasHours (@Nullable TdApi.BusinessInfo info) {
    return hours(info) != null;
  }

  public static boolean hasLocation (@Nullable TdApi.BusinessInfo info) {
    return info != null && info.location != null && (info.location.location != null || (info.location.address != null && !info.location.address.trim().isEmpty()));
  }

  private static String time (int minuteOfWeek) {
    final int m = ((minuteOfWeek % DAY) + DAY) % DAY;
    final Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, m / 60);
    c.set(Calendar.MINUTE, m % 60);
    return android.text.format.DateFormat.getTimeFormat(UI.getContext()).format(c.getTime());
  }

  /** Monday is 0. */
  private static String dayName (int day) {
    final int[] days = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
    final String name = DateFormatSymbols.getInstance(Locale.getDefault()).getWeekdays()[days[((day % 7) + 7) % 7]];
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
  }

  /** Now as minutes from Monday 00:00, in this phone's time (as localOpeningHours are). */
  private static int nowMinute () {
    final Calendar c = Calendar.getInstance();
    final int day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Sunday is 1, Monday 2
    return day * DAY + c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
  }

  /** "Open now, until 18:00", "Closed, opens at 9:00" or "Closed, opens Monday at 9:00"; null without hours. */
  public static @Nullable String state (@Nullable TdApi.BusinessInfo info) {
    final TdApi.BusinessOpeningHours hours = hours(info);
    if (hours == null) {
      return null;
    }
    final int now = nowMinute();
    for (TdApi.BusinessOpeningHoursInterval interval : hours.openingHours) {
      for (int shift = -WEEK; shift <= 0; shift += WEEK) {
        // An interval may run past Sunday midnight into Monday: checked a week earlier too
        if (now >= interval.startMinute + shift && now < interval.endMinute + shift) {
          if (interval.endMinute - interval.startMinute >= WEEK) {
            return Lang.getString(R.string.XgramBusinessOpen24);
          }
          return Lang.getString(R.string.XgramBusinessOpenUntil, time(interval.endMinute));
        }
      }
    }
    int next = -1;
    int best = Integer.MAX_VALUE;
    for (TdApi.BusinessOpeningHoursInterval interval : hours.openingHours) {
      final int wait = ((interval.startMinute - now) % WEEK + WEEK) % WEEK;
      if (wait < best) {
        best = wait;
        next = interval.startMinute;
      }
    }
    if (next == -1) {
      return Lang.getString(R.string.XgramBusinessClosed);
    }
    if (next / DAY == now / DAY && next > now) {
      return Lang.getString(R.string.XgramBusinessClosedOpensAt, time(next));
    }
    return Lang.getString(R.string.XgramBusinessClosedOpensDay, dayName(next / DAY), time(next));
  }

  /** The week, a line a day: "Monday: 9:00 – 18:00", "Sunday: Closed". */
  public static String week (@Nullable TdApi.BusinessInfo info) {
    final TdApi.BusinessOpeningHours hours = hours(info);
    final StringBuilder b = new StringBuilder();
    for (int day = 0; day < 7; day++) {
      final StringBuilder line = new StringBuilder();
      if (hours != null) {
        for (TdApi.BusinessOpeningHoursInterval interval : hours.openingHours) {
          if (interval.startMinute / DAY != day) {
            continue;
          }
          if (line.length() > 0) {
            line.append(", ");
          }
          if (interval.startMinute % DAY == 0 && interval.endMinute - interval.startMinute >= DAY) {
            line.append(Lang.getString(R.string.XgramBusinessOpenAllDay));
          } else {
            line.append(Lang.getString(R.string.XgramBusinessFromTo, time(interval.startMinute), time(interval.endMinute)));
          }
        }
      }
      if (b.length() > 0) {
        b.append('\n');
      }
      b.append(dayName(day)).append(": ").append(line.length() > 0 ? line : Lang.getString(R.string.XgramBusinessClosed));
    }
    return b.toString();
  }

  public static String address (TdApi.BusinessInfo info) {
    final String address = info.location != null ? info.location.address : null;
    return address != null && !address.trim().isEmpty() ? address.trim() : Lang.getString(R.string.XgramBusinessLocation);
  }

  /** Opens the location (its pin, or a search for its address) in a maps app. */
  public static void openMap (ViewController<?> c, TdApi.BusinessInfo info) {
    if (info.location == null) {
      return;
    }
    final String address = info.location.address != null ? info.location.address.trim() : "";
    final Uri uri;
    if (info.location.location != null) {
      final TdApi.Location l = info.location.location;
      uri = Uri.parse("geo:" + l.latitude + "," + l.longitude + "?q=" + l.latitude + "," + l.longitude + (address.isEmpty() ? "" : "(" + Uri.encode(address) + ")"));
    } else {
      uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
    }
    try {
      c.context().startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.XgramBusinessNoMaps, android.widget.Toast.LENGTH_SHORT);
    }
  }
}
