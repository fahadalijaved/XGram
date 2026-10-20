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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.AppContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Finishes what can't wait for the app: ending a Check In while offline, adopting the alert after the app died, the
 * check after the alert time. Runs when there is a network, keeps TDLib awake while it works, and retries until the
 * Check In is in a stable state.
 */
public final class XgramCheckInWork extends Worker {
  private static final String NAME = "xgram_checkin_reconcile";
  private static final String KEY_ACC = "acc";

  public XgramCheckInWork (@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  private static String name (int acc) {
    return NAME + "_" + acc;
  }

  static void enqueue (int acc) {
    try {
      OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(XgramCheckInWork.class)
        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(new Data.Builder().putInt(KEY_ACC, acc).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .addTag(NAME)
        .build();
      WorkManager.getInstance(AppContext.get()).enqueueUniqueWork(name(acc), ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    } catch (Throwable t) {
      XgramCheckIn.log("work not enqueued: %s", t.toString());
    }
  }

  static void cancel (int acc) {
    try {
      WorkManager.getInstance(AppContext.get()).cancelUniqueWork(name(acc));
    } catch (Throwable ignored) { }
  }

  @NonNull
  @Override
  public Result doWork () {
    int acc = getInputData().getInt(KEY_ACC, -1);
    if (acc < 0) {
      return Result.success();
    }
    if (!TdlibManager.instance().hasAccount(acc)) {
      XgramCheckIn.onAccountMissing(acc);
      return Result.success();
    }
    Tdlib tdlib = XgramCheckIn.tdlibFor(acc);
    if (tdlib == null) {
      XgramCheckIn.onAccountMissing(acc);
      return Result.success();
    }
    tdlib.changeLocationReferenceCount(1);
    try {
      CountDownLatch connected = new CountDownLatch(1);
      tdlib.awaitConnection(connected::countDown);
      if (!connected.await(60, TimeUnit.SECONDS)) {
        return retryOrGiveUp();
      }
      CountDownLatch done = new CountDownLatch(1);
      final boolean[] stable = {false};
      XgramCheckIn.reconcile(acc, () -> {
        stable[0] = XgramCheckIn.isStable(acc);
        done.countDown();
      });
      if (!done.await(60, TimeUnit.SECONDS)) {
        return retryOrGiveUp();
      }
      XgramCheckIn.log("work acc=%d stable=%b attempt=%d", acc, stable[0], getRunAttemptCount());
      return stable[0] ? Result.success() : retryOrGiveUp();
    } catch (InterruptedException e) {
      return retryOrGiveUp();
    } finally {
      tdlib.changeLocationReferenceCount(-1);
    }
  }

  private Result retryOrGiveUp () {
    return getRunAttemptCount() > 50 ? Result.success() : Result.retry();
  }
}
