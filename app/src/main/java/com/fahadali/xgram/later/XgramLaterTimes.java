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
package com.fahadali.xgram.later;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Times of Later: the presets of the sheet (device clock, recomputed every time it opens) and the absolute labels
 * ("Today, 21:00", "Tue, 9:00", "12 Oct, 9:00"), so nothing on screen ticks. The calendar math takes the clock, the
 * time zone and the locale as arguments, for the self-test.
 */
public final class XgramLaterTimes {
  private XgramLaterTimes () { }

  static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR, WEEK = 7 * DAY;

  /** "Tonight" is offered until this hour. */
  static final int TONIGHT_UNTIL_HOUR = 19, TONIGHT_HOUR = 20;
  /** Before this hour "Tomorrow, 9:00" is this morning. */
  static final int EARLY_MORNING_UNTIL_HOUR = 5, MORNING_HOUR = 9;

  static Calendar calendar (long at, TimeZone tz, Locale locale) {
    Calendar c = Calendar.getInstance(tz, locale);
    c.setTimeInMillis(at);
    return c;
  }

  static Calendar calendar (long at) {
    return calendar(at, TimeZone.getDefault(), locale());
  }

  static Locale locale () {
    Locale locale = null;
    try {
      locale = Lang.locale();
    } catch (Throwable ignored) { }
    return locale != null ? locale : Locale.getDefault();
  }

  /** {@code ms} rounded up to the next whole minute. */
  static long ceilToMinute (long ms) {
    long rest = ms % MINUTE;
    return rest == 0 ? ms : ms + (MINUTE - rest);
  }

  static long in20Minutes (long now) {
    return ceilToMinute(now + 20 * MINUTE);
  }

  static long in1Hour (long now) {
    return ceilToMinute(now + HOUR);
  }

  /** 20:00 today, or 0 from 19:00 on (no "Tonight" then). */
  static long tonight (long now, TimeZone tz, Locale locale) {
    Calendar c = calendar(now, tz, locale);
    if (c.get(Calendar.HOUR_OF_DAY) >= TONIGHT_UNTIL_HOUR) {
      return 0;
    }
    atHour(c, TONIGHT_HOUR);
    return c.getTimeInMillis();
  }

  /** Whether "Tomorrow, 9:00" is this morning (between midnight and 5:00). */
  static boolean isEarlyMorning (long now, TimeZone tz, Locale locale) {
    return calendar(now, tz, locale).get(Calendar.HOUR_OF_DAY) < EARLY_MORNING_UNTIL_HOUR;
  }

  /** 9:00 tomorrow, or 9:00 today before 5:00. DST safe (calendar fields, not millisecond sums). */
  static long tomorrowMorning (long now, TimeZone tz, Locale locale) {
    Calendar c = calendar(now, tz, locale);
    if (c.get(Calendar.HOUR_OF_DAY) >= EARLY_MORNING_UNTIL_HOUR) {
      c.add(Calendar.DAY_OF_MONTH, 1);
    }
    atHour(c, MORNING_HOUR);
    return c.getTimeInMillis();
  }

  /** 9:00 on the next first day of the week of {@code locale}, at least one day ahead (today's that day: a week on). */
  static long nextWeek (long now, TimeZone tz, Locale locale) {
    Calendar c = calendar(now, tz, locale);
    int first = c.getFirstDayOfWeek();
    int today = c.get(Calendar.DAY_OF_WEEK);
    int days = (first - today + 7) % 7;
    if (days == 0) {
      days = 7;
    }
    c.add(Calendar.DAY_OF_MONTH, days);
    atHour(c, MORNING_HOUR);
    return c.getTimeInMillis();
  }

  private static void atHour (Calendar c, int hour) {
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
  }

  /** Whole calendar days from {@code from}'s date to {@code to}'s date in {@code tz} (negative when before). */
  static int daysBetween (long from, long to, TimeZone tz, Locale locale) {
    return (int) (epochDay(calendar(to, tz, locale)) - epochDay(calendar(from, tz, locale)));
  }

  private static long epochDay (Calendar c) {
    Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
    utc.clear();
    utc.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    return utc.getTimeInMillis() / DAY; // midnight UTC of the date: an exact multiple, after 1970
  }

  // Labels (app locale and 24-hour setting, as the rest of XGram)

  /** "21:00" (or "9:00 PM"). */
  public static String time (long at) {
    return Lang.time(calendar(at));
  }

  /**
   * An absolute label: "Today, 21:00", "Tomorrow, 9:00", "Yesterday, 9:00", "Tue, 9:00" within the next 6 days,
   * "12 Oct, 9:00", "12 Oct '27, 9:00" in another year.
   */
  public static String formatWake (long at) {
    return formatWake(at, System.currentTimeMillis());
  }

  public static String formatWake (long at, long now) {
    Calendar c = calendar(at);
    String time = Lang.time(c);
    int days = daysBetween(now, at, TimeZone.getDefault(), locale());
    if (days == 0) {
      return Lang.getString(R.string.XgramLaterTodayAt, time);
    } else if (days == 1) {
      return Lang.getString(R.string.XgramLaterTomorrow, time);
    } else if (days == -1) {
      return Lang.getString(R.string.XgramLaterYesterdayAt, time);
    } else if (days > 1 && days <= 6) {
      return Lang.getString(R.string.XgramLaterDayAt, Lang.weekShort(c), time);
    } else {
      return Lang.getString(R.string.XgramLaterDayAt, Lang.dateFullShort(c), time);
    }
  }

  /** "5m", "5h", "2d", "3w": how long ago (or how long it took). */
  public static String formatAge (long ms) {
    ms = Math.max(0, ms);
    if (ms < HOUR) {
      return Lang.getString(R.string.XgramLaterAgeM, (int) Math.max(1, ms / MINUTE));
    } else if (ms < DAY) {
      return Lang.getString(R.string.XgramLaterAgeH, (int) (ms / HOUR));
    } else if (ms < WEEK) {
      return Lang.getString(R.string.XgramLaterAgeD, (int) (ms / DAY));
    } else {
      return Lang.getString(R.string.XgramLaterAgeW, (int) (ms / WEEK));
    }
  }

  /** "Tue" of a time, for "Next week, Mon 9:00". */
  static String weekday (long at) {
    return Lang.weekShort(calendar(at));
  }
}
