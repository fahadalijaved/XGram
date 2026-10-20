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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Later's alarm, its notifications' actions (Snooze 1 h, Done) and the system events after which the alarm must be set
 * again (boot, update, clock or time zone change, exact alarm permission). Not exported: the system's broadcasts still
 * arrive, as with WorkManager. Never starts an activity and never needs TDLib.
 */
public final class XgramLaterReceiver extends BroadcastReceiver {
  static final String ACTION_WAKE = "com.fahadali.xgram.later.WAKE";
  static final String ACTION_SNOOZE = "com.fahadali.xgram.later.SNOOZE";
  static final String ACTION_DONE = "com.fahadali.xgram.later.DONE";
  static final String SCHEME = "xgramlater";

  private static final long SNOOZE_MS = XgramLaterTimes.HOUR;

  @Override
  public void onReceive (Context context, Intent intent) {
    final String action = intent != null ? intent.getAction() : null;
    if (action == null) {
      return;
    }
    final Uri data = intent.getData();
    final Context appContext = context.getApplicationContext();
    final PendingResult result = goAsync();
    XgramLaterEngine.post(() -> {
      try {
        handle(appContext, action, data);
      } catch (Throwable t) {
        Log.e(XgramLaterStore.TAG, "Cannot handle " + action, t);
      } finally {
        result.finish();
      }
    });
  }

  private static void handle (Context ctx, String action, Uri data) {
    if (ACTION_SNOOZE.equals(action) || ACTION_DONE.equals(action)) {
      String key = data != null && SCHEME.equals(data.getScheme()) ? data.getSchemeSpecificPart() : null;
      long[] parts = XgramLaterRecord.parseKey(key);
      if (parts != null) {
        XgramLaterNotifications.cancel(ctx, key);
        int accountId = (int) parts[0];
        XgramLaterRecord r = XgramLaterEngine.current(accountId, key);
        if (r != null && (r.isDue() || r.isPending())) {
          if (ACTION_SNOOZE.equals(action)) {
            // Pending again in an hour (a woken chat is hidden again for that hour)
            XgramLaterEngine.reschedule(accountId, key, XgramLaterTimes.ceilToMinute(System.currentTimeMillis() + SNOOZE_MS));
          } else {
            XgramLaterEngine.markDone(accountId, key);
          }
        }
      }
    } else {
      // The alarm, boot, update, clock, time zone or exact alarm permission: whatever is due, then the next alarm
      XgramLaterEngine.processDue(System.currentTimeMillis(), false);
    }
    XgramLaterAlarms.arm(ctx);
  }
}
