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
package com.fahadali.xgram.checkin;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The texts Check In sends: the start message, the alert, the closing reply and the correction. Plain text in the app
 * language; every time also carries a DateTime entity (unless the server refused them), so official apps show it in the
 * reader's own time zone. The alert's ref is always its last token.
 */
final class CheckInText {
  private CheckInText () { }

  /** A time argument: its text, shown as a DateTime entity of [unix]. */
  static final class Time {
    final String text;
    final long unix;
    final boolean sameDay;

    Time (long unix, long now) {
      this.unix = unix;
      this.sameDay = sameDay(unix, now);
      this.text = sameDay ? Lang.time(unix, TimeUnit.SECONDS) : Lang.getTimestamp(unix, TimeUnit.SECONDS);
    }

    Time (String text, long unix, boolean sameDay) {
      this.text = text;
      this.unix = unix;
      this.sameDay = sameDay;
    }
  }

  static boolean sameDay (long a, long b) {
    Calendar ca = Calendar.getInstance(), cb = Calendar.getInstance();
    ca.setTimeInMillis(a * 1000L);
    cb.setTimeInMillis(b * 1000L);
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
  }

  /** Builds text and its DateTime entities; offsets are UTF-16 units, as TDLib counts them. */
  static final class Builder {
    final StringBuilder text = new StringBuilder();
    final List<TdApi.TextEntity> entities = new ArrayList<>();
    private final boolean useEntities;

    Builder (boolean useEntities) {
      this.useEntities = useEntities;
    }

    Builder line () {
      text.append('\n');
      return this;
    }

    Builder plain (String s) {
      text.append(s);
      return this;
    }

    /** [template] with %1$s.. replaced by [args] (String or {@link Time}); %% is a percent sign. */
    Builder format (String template, Object... args) {
      int i = 0, n = template.length();
      int nextIndex = 0;
      while (i < n) {
        char c = template.charAt(i);
        if (c != '%' || i + 1 >= n) {
          text.append(c);
          i++;
          continue;
        }
        char d = template.charAt(i + 1);
        if (d == '%') {
          text.append('%');
          i += 2;
          continue;
        }
        int j = i + 1;
        int index = -1;
        while (j < n && Character.isDigit(template.charAt(j))) {
          j++;
        }
        if (j > i + 1 && j < n && template.charAt(j) == '$') {
          index = Integer.parseInt(template.substring(i + 1, j)) - 1;
          j++;
        } else {
          j = i + 1;
          index = nextIndex++;
        }
        if (j < n && (template.charAt(j) == 's' || template.charAt(j) == 'd')) {
          Object arg = index >= 0 && index < args.length ? args[index] : "";
          append(arg);
          i = j + 1;
        } else {
          text.append(c);
          i++;
        }
      }
      return this;
    }

    private void append (Object arg) {
      if (arg instanceof Time) {
        Time t = (Time) arg;
        int start = text.length();
        text.append(t.text);
        if (useEntities && t.unix > 0 && t.text.length() > 0) {
          TdApi.DateTimeFormattingType type = new TdApi.DateTimeFormattingTypeAbsolute(
            new TdApi.DateTimePartPrecisionShort(),
            t.sameDay ? new TdApi.DateTimePartPrecisionNone() : new TdApi.DateTimePartPrecisionShort(),
            false
          );
          entities.add(new TdApi.TextEntity(start, t.text.length(), new TdApi.TextEntityTypeDateTime((int) t.unix, type)));
        }
      } else {
        text.append(String.valueOf(arg));
      }
    }

    TdApi.FormattedText build () {
      return new TdApi.FormattedText(text.toString(), entities.toArray(new TdApi.TextEntity[0]));
    }
  }

  static TdApi.InputMessageText input (TdApi.FormattedText text) {
    return new TdApi.InputMessageText(text, new TdApi.LinkPreviewOptions(true, "", false, false, false), false);
  }

  static String mapsLink (double lat, double lng) {
    return String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lng);
  }

  private static String s (@StringRes int res) {
    return Lang.getString(res);
  }

  /** What the alert carries, for the start message ("…you'll automatically get a message with my …"). */
  static String tail (CheckInRecord r) {
    return s(r.loc && r.bat ? R.string.XgramCheckInTailBoth : r.loc ? R.string.XgramCheckInTailLocation : r.bat ? R.string.XgramCheckInTailBattery : R.string.XgramCheckInTailNone);
  }

  /** The same, for the sheets ("…gets an automatic message with your …"). */
  static String contents (boolean loc, boolean bat) {
    return s(loc && bat ? R.string.XgramCheckInContentsBoth : loc ? R.string.XgramCheckInContentsLocation : bat ? R.string.XgramCheckInContentsBattery : R.string.XgramCheckInContentsNone);
  }

  static String placeLabel (CheckInRecord r) {
    return r.destLabel != null && !r.destLabel.isEmpty() ? r.destLabel : s(R.string.XgramCheckInPinnedPlace);
  }

  /** The visible message sent once the alert is confirmed. */
  static TdApi.FormattedText start (CheckInRecord r, long now) {
    Builder b = new Builder(!r.entitiesOff);
    Time eta = new Time(r.eta, now), sendAt = new Time(r.sendAt, now);
    if (r.hasDest) {
      b.format(s(R.string.XgramCheckInMsgStartDest), placeLabel(r), eta, sendAt, tail(r));
    } else {
      b.format(s(R.string.XgramCheckInMsgStart), eta, sendAt, tail(r));
    }
    return b.build();
  }

  /**
   * The alert: who hasn't checked in, the back-by time, the last known location and battery (when included), whether
   * the phone switched off, when it was last updated after going out, and the ref.
   */
  static TdApi.FormattedText alert (CheckInRecord r, long now) {
    Builder b = new Builder(!r.entitiesOff);
    if (r.me != null && !r.me.isEmpty()) {
      b.format(s(R.string.XgramCheckInAlertTitle), r.me);
    } else {
      b.plain(s(R.string.XgramCheckInAlertTitleMe));
    }
    b.line();
    Time eta = new Time(r.eta, now);
    if (r.hasDest) {
      b.format(s(R.string.XgramCheckInAlertDest), placeLabel(r), eta);
    } else {
      b.format(s(R.string.XgramCheckInAlertTimer), eta);
    }
    if (r.loc) {
      b.line();
      if (r.lastFixAt != 0) {
        Time fixTime = new Time(r.lastFixAt, now);
        String link = mapsLink(r.lastLat, r.lastLng);
        if (r.coarse) {
          b.format(s(R.string.XgramCheckInAlertLocationCoarse), fixTime, link);
        } else {
          b.format(s(R.string.XgramCheckInAlertLocation), fixTime, Lang.getString(R.string.XgramCheckInMeters, Math.max(1, Math.round(r.lastAcc))), link);
        }
      } else {
        b.plain(s(R.string.XgramCheckInAlertNoLocation));
      }
    }
    if (r.bat && r.lastBat >= 0) {
      b.line();
      // The percent sign and the digits come from the string and the app language, as the meters above.
      b.plain(Lang.getString(r.lastCharging ? R.string.XgramCheckInAlertBatteryCharging : R.string.XgramCheckInAlertBattery, r.lastBat));
    }
    if (r.offAt != 0) {
      b.line();
      b.format(s(R.string.XgramCheckInAlertOff), new Time(r.offAt, now));
    }
    if (r.updatedAt != 0) {
      b.line();
      b.format(s(R.string.XgramCheckInAlertUpdated), new Time(r.updatedAt, now));
    }
    b.line().line();
    b.plain(s(R.string.XgramCheckInAlertLabel)).plain(" · ").plain(r.ref);
    return b.build();
  }

  /** The closing reply: [fired] is what the server says about the alert. */
  static TdApi.FormattedText closing (CheckInRecord r, boolean fired) {
    Builder b = new Builder(false);
    switch (r.closeKind) {
      case CheckInRecord.KIND_ARRIVED:
        b.format(s(fired ? R.string.XgramCheckInMsgArrivedAfter : R.string.XgramCheckInMsgArrived), placeLabel(r));
        break;
      case CheckInRecord.KIND_CANCELLED:
        b.plain(s(fired ? R.string.XgramCheckInMsgFalseAlarm : R.string.XgramCheckInMsgCancelled));
        break;
      case CheckInRecord.KIND_SAFE:
      default:
        b.plain(s(fired ? R.string.XgramCheckInMsgFalseAlarm : R.string.XgramCheckInMsgSafe));
        break;
    }
    return b.build();
  }

  /** Sent when the alert went out although the Check In had been ended in time. */
  static TdApi.FormattedText correction () {
    return new TdApi.FormattedText(s(R.string.XgramCheckInMsgCorrection), new TdApi.TextEntity[0]);
  }

  /** The text of a message, or null when it isn't a text message. */
  @Nullable
  static String textOf (@Nullable TdApi.Message message) {
    if (message != null && message.content instanceof TdApi.MessageText) {
      TdApi.FormattedText t = ((TdApi.MessageText) message.content).text;
      return t != null ? t.text : null;
    }
    return null;
  }
}
