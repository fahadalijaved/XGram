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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;

import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * Alarms: "Are you OK?" at the back-by time and the last call 2 minutes before the alert are exact (an inexact alarm on
 * a sleeping phone can come up to 45 minutes late and miss both; inexact only when exact alarms aren't allowed), and a
 * check 90 seconds after the alert time (closing wording, the watch record) is inexact. Set again on every save, from
 * server times through the record's clock difference.
 */
final class XgramCheckInAlarms {
  private XgramCheckInAlarms () { }

  private static final int RC_PROMPT = 0x5C10, RC_LAST_CALL = 0x5C11, RC_AFTER = 0x5C12, RC_WATCH = 0x5C13;
  static final long AFTER_DELAY = 90;

  @Nullable
  private static AlarmManager alarms (Context context) {
    return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
  }

  private static Intent intent (Context context, String action) {
    Intent intent = new Intent(context, XgramCheckInReceiver.class);
    intent.setAction(action);
    intent.setPackage(context.getPackageName());
    return intent;
  }

  /** Exact alarms may be set: always below Android 12, USE_EXACT_ALARM covers 13+ (12 asks for SCHEDULE_EXACT_ALARM). */
  private static boolean canExact (AlarmManager am) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    try {
      return am.canScheduleExactAlarms();
    } catch (Throwable t) {
      return false;
    }
  }

  private static void set (Context context, String action, int requestCode, CheckInRecord r, long serverTime, boolean exact) {
    AlarmManager am = alarms(context);
    if (am == null) {
      return;
    }
    Intent intent = intent(context, action);
    intent.putExtra(XgramCheckInReceiver.EXTRA_ACC, r.acc);
    intent.putExtra(XgramCheckInReceiver.EXTRA_REF, r.ref);
    try {
      PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
      long at = r.toDeviceMillis(serverTime);
      if (exact && canExact(am)) {
        try {
          AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, at, pi);
          return;
        } catch (SecurityException e) {
          XgramCheckIn.log("alarm %s not exact: %s", action, e.toString()); // the permission went (Android 12)
        }
      }
      AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, at, pi);
    } catch (Throwable t) {
      XgramCheckIn.log("alarm %s not set: %s", action, t.toString());
    }
  }

  private static void cancel (Context context, String action, int requestCode) {
    AlarmManager am = alarms(context);
    try {
      PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent(context, action), Intents.mutabilityFlags(false) | PendingIntent.FLAG_NO_CREATE);
      if (pi != null) {
        if (am != null) {
          am.cancel(pi);
        }
        pi.cancel();
      }
    } catch (Throwable ignored) { }
  }

  static void arm (CheckInRecord r) {
    Context context = AppContext.get();
    long now = r.nowFromDevice();
    switch (r.state) {
      case CheckInRecord.STARTING:
      case CheckInRecord.ARMED: {
        if (!r.prompted && r.eta > now) {
          set(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT, r, r.eta, true);
        } else {
          cancel(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT);
        }
        long lastCall = r.sendAt - CheckInRules.LAST_CALL_LEAD;
        if (!r.lastCall && lastCall > now) {
          set(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL, r, lastCall, true);
        } else {
          cancel(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL);
        }
        set(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER, r, Math.max(now + 30, r.sendAt + AFTER_DELAY), false);
        break;
      }
      case CheckInRecord.CLOSING: {
        cancel(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT);
        cancel(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL);
        if (r.sendAt + AFTER_DELAY > now) {
          set(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER, r, r.sendAt + AFTER_DELAY, false);
        } else {
          cancel(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER);
        }
        break;
      }
      default:
        cancelActive();
        break;
    }
  }

  static void armWatch (CheckInRecord w) {
    Context context = AppContext.get();
    long now = w.nowFromDevice();
    long at = w.sendAt + AFTER_DELAY;
    set(context, XgramCheckInReceiver.ACTION_WATCH, RC_WATCH, w, Math.max(now + 30, at), false);
  }

  static void cancelActive () {
    Context context = AppContext.get();
    cancel(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT);
    cancel(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL);
    cancel(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER);
  }

  static void cancelWatch () {
    cancel(AppContext.get(), XgramCheckInReceiver.ACTION_WATCH, RC_WATCH);
  }
}
