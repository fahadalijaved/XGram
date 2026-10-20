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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Not exported. Boot and app updates re-arm a running Check In; the alarms and the notification actions (I'm safe,
 * Add 15 min) carry the account and the ref, and a stale ref is ignored.
 */
public final class XgramCheckInReceiver extends BroadcastReceiver {
  static final String ACTION_PROMPT = "com.fahadali.xgram.checkin.PROMPT";
  static final String ACTION_LAST_CALL = "com.fahadali.xgram.checkin.LAST_CALL";
  static final String ACTION_AFTER = "com.fahadali.xgram.checkin.AFTER";
  static final String ACTION_WATCH = "com.fahadali.xgram.checkin.WATCH";
  static final String ACTION_SAFE = "com.fahadali.xgram.checkin.SAFE";
  static final String ACTION_ADD15 = "com.fahadali.xgram.checkin.ADD15";

  static final String EXTRA_ACC = "xgram_checkin_acc", EXTRA_REF = "xgram_checkin_ref";

  @Override
  public void onReceive (Context context, Intent intent) {
    String action = intent != null ? intent.getAction() : null;
    if (action == null) {
      return;
    }
    final boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
    int acc = intent.getIntExtra(EXTRA_ACC, -1);
    String ref = intent.getStringExtra(EXTRA_REF);
    if (!boot && (acc < 0 || ref == null)) {
      return;
    }
    // The process may have been started just for this broadcast: it is kept until the engine thread is done with it.
    final PendingResult pending = goAsync();
    final AtomicBoolean finished = new AtomicBoolean();
    Runnable finish = () -> {
      if (finished.compareAndSet(false, true)) {
        try {
          pending.finish();
        } catch (Throwable ignored) { }
      }
    };
    new Handler(Looper.getMainLooper()).postDelayed(finish, 8_000);
    if (boot) {
      XgramCheckIn.onBoot(finish);
    } else {
      XgramCheckIn.onAction(action, acc, ref, finish);
    }
  }
}
