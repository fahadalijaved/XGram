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
package com.fahadali.xgram.export;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.fahadali.xgram.activity.XgramActivityClassifier;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Blocking TDLib calls for the export's worker thread. Each request is sent once and waited for in short slices, so
 * a cancel is seen within a quarter second and a slow reply never gets a duplicate request stacked on it
 * ({@code Tdlib.clientExecuteT} returns null on its timeout while the request stays in flight). Time without a reply
 * only counts while connected: offline, the job shows "Waiting for network" instead of failing. Flood errors wait
 * what Telegram asks, at least an exponential back-off (a 429 without a number would otherwise retry at once).
 */
final class ExportTd {
  private static final long SLICE_MS = 250;
  /** Connected time without a reply before the request is given up. */
  private static final long STALL_MS = 3 * 60_000L;
  private static final long MAX_BACKOFF_SECONDS = 300;

  private final Tdlib tdlib;
  private final ExportJob job;
  private int floodStreak;

  ExportTd (Tdlib tdlib, ExportJob job) {
    this.tdlib = tdlib;
    this.job = job;
  }

  Tdlib tdlib () {
    return tdlib;
  }

  /**
   * The result, or a {@link TdlibException} for a TDLib error that is not a flood wait (and for a request that got no
   * reply in {@link #STALL_MS} of connected time). Throws {@link ExportJob.Canceled} once the job is canceled.
   */
  @NonNull
  @WorkerThread
  @SuppressWarnings("unchecked")
  <T extends TdApi.Object> T call (TdApi.Function<T> function) {
    while (true) {
      job.checkCanceled();
      TdApi.Object result = await(function);
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        TdApi.Error error = (TdApi.Error) result;
        long retryAfter = XgramActivityClassifier.retryAfterSeconds(error);
        if (retryAfter >= 0) {
          waitFlood(retryAfter);
          continue;
        }
        throw new TdlibException(error);
      }
      floodStreak = 0;
      return (T) result;
    }
  }

  private TdApi.Object await (TdApi.Function<?> function) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();
    tdlib.awaitInitialization(() -> tdlib.client().send(function, object -> {
      response.set(object);
      latch.countDown();
    }));
    long connectedWaitMs = 0;
    try {
      while (!latch.await(SLICE_MS, TimeUnit.MILLISECONDS)) {
        job.checkCanceled(); // a late reply goes to the handler above and is dropped
        if (tdlib.isConnected()) {
          job.setWaitingForNetwork(false);
          connectedWaitMs += SLICE_MS;
          if (connectedWaitMs >= STALL_MS) {
            throw new TdlibException(new TdApi.Error(408, "XGRAM_EXPORT_NO_REPLY " + function.getClass().getSimpleName()));
          }
        } else {
          job.setWaitingForNetwork(true);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExportJob.Canceled();
    }
    job.setWaitingForNetwork(false);
    return response.get();
  }

  /** Sleeps out a flood wait in 1 s slices that check cancel, publishing the seconds left. */
  private void waitFlood (long retryAfterSeconds) {
    floodStreak++;
    final long backoff = Math.min(MAX_BACKOFF_SECONDS, 2L << Math.min(floodStreak - 1, 8));
    long left = Math.max(retryAfterSeconds + 1, backoff);
    try {
      while (left > 0) {
        job.checkCanceled();
        job.setWaitSeconds((int) Math.min(Integer.MAX_VALUE, left));
        Thread.sleep(1000);
        left--;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExportJob.Canceled();
    } finally {
      job.setWaitSeconds(0);
    }
  }
}
