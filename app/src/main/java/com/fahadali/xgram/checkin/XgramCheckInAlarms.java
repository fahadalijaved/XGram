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

import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;

import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * Inexact alarms (no exact-alarm permission): "Are you OK?" at the back-by time, the last call 2 minutes before the
 * alert, and a check 90 seconds after the alert time (closing wording, the watch record). Set again on every save,
 * from server times through the record's clock difference.
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

  private static void set (Context context, String action, int requestCode, CheckInRecord r, long serverTime) {
    AlarmManager am = alarms(context);
    if (am == null) {
      return;
    }
    Intent intent = intent(context, action);
    intent.putExtra(XgramCheckInReceiver.EXTRA_ACC, r.acc);
    intent.putExtra(XgramCheckInReceiver.EXTRA_REF, r.ref);
    try {
      PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
      AlarmManagerCompat.setAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, r.toDeviceMillis(serverTime), pi);
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
          set(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT, r, r.eta);
        } else {
          cancel(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT);
        }
        long lastCall = r.sendAt - CheckInRules.LAST_CALL_LEAD;
        if (!r.lastCall && lastCall > now) {
          set(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL, r, lastCall);
        } else {
          cancel(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL);
        }
        set(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER, r, Math.max(now + 30, r.sendAt + AFTER_DELAY));
        break;
      }
      case CheckInRecord.CLOSING: {
        cancel(context, XgramCheckInReceiver.ACTION_PROMPT, RC_PROMPT);
        cancel(context, XgramCheckInReceiver.ACTION_LAST_CALL, RC_LAST_CALL);
        if (r.sendAt + AFTER_DELAY > now) {
          set(context, XgramCheckInReceiver.ACTION_AFTER, RC_AFTER, r, r.sendAt + AFTER_DELAY);
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
    set(context, XgramCheckInReceiver.ACTION_WATCH, RC_WATCH, w, Math.max(now + 30, at));
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
