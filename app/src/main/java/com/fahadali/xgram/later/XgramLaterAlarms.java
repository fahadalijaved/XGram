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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Intents;

/**
 * One alarm for the earliest pending time of every account: it wakes {@link XgramLaterReceiver}, which handles all
 * that is due and arms the next. An in-process timer on the Later thread backs it up while the app runs, and every
 * process start catches up (a force stop clears alarms without telling anyone).
 */
final class XgramLaterAlarms {
  private XgramLaterAlarms () { }

  private static final int REQUEST_CODE = 0x4C41;

  private static final Runnable tick = () -> {
    XgramLaterEngine.processDue(System.currentTimeMillis(), true);
    arm(XgramLaterEngine.context());
  };

  private static PendingIntent wakeIntent (Context ctx) {
    Intent intent = new Intent(ctx, XgramLaterReceiver.class);
    intent.setAction(XgramLaterReceiver.ACTION_WAKE);
    return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false));
  }

  /** Whether exact alarms may be set (always below Android 12; USE_EXACT_ALARM covers Android 13+). */
  static boolean canExact (Context ctx) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    AlarmManager manager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    try {
      return manager == null || manager.canScheduleExactAlarms();
    } catch (Throwable t) {
      return false;
    }
  }

  /** Android 12+: the system page that allows exact alarms for XGram. */
  static void openExactSettings (ViewController<?> c) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return;
    }
    try {
      Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.context().getPackageName()));
      c.context().startActivity(intent);
    } catch (Throwable t) {
      Log.w(XgramLaterStore.TAG, "Cannot open the exact alarm settings", t);
    }
  }

  /**
   * The app's own settings page, where Battery → Unrestricted turns battery optimization off (Samsung and stock
   * Android 12+); the system's battery optimization list when that page can't open.
   */
  static void openBatterySettings (Context ctx) {
    Intent[] intents = {
      new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.getPackageName(), null)),
      new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    };
    for (Intent intent : intents) {
      try {
        if (!(ctx instanceof android.app.Activity)) {
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(intent);
        return;
      } catch (Throwable t) {
        Log.w(XgramLaterStore.TAG, "Cannot open the battery settings", t);
      }
    }
  }

  /** Arms (or cancels) the alarm for the next pending time. Any thread; the work runs on the Later thread. */
  static void arm (Context ctx) {
    if (!XgramLaterEngine.onExecutor()) {
      XgramLaterEngine.post(() -> arm(ctx));
      return;
    }
    XgramLaterEngine.EXECUTOR.removeCallbacks(tick);
    long next = 0;
    for (XgramLaterStore.AccountIndex index : XgramLaterStore.allSnapshots()) {
      if (index.nextWakeAt != 0 && (next == 0 || index.nextWakeAt < next)) {
        next = index.nextWakeAt;
      }
    }
    AlarmManager manager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    if (manager == null) {
      return;
    }
    PendingIntent pi = wakeIntent(ctx);
    if (next == 0) {
      manager.cancel(pi);
      return;
    }
    long now = System.currentTimeMillis();
    long at = Math.max(next, now + 1000);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
          manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
          manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        manager.setExact(AlarmManager.RTC_WAKEUP, at, pi);
      } else {
        manager.set(AlarmManager.RTC_WAKEUP, at, pi);
      }
    } catch (Throwable t) {
      Log.e(XgramLaterStore.TAG, "Cannot set the alarm", t);
    }
    // The same time on this thread while the process lives (uptime: it doesn't run in deep sleep, the alarm does)
    XgramLaterEngine.EXECUTOR.postAtTime(tick, SystemClock.uptimeMillis() + (at - now));
  }
}
