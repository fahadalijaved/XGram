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

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.Log;

/**
 * The foreground service (type dataSync) of a running chat export. It only keeps XGram in the foreground, shows the
 * progress notification and forwards its Cancel: the job ({@link ExportJob}) holds the TDLib reference and the wake
 * lock itself, and works the same when Android refuses the service (it then runs in the app's process only, and the
 * user is told to keep XGram open). On Android 15's dataSync time limit the job is told to stop and finish what it
 * has as a partial .zip; the service gives it a few seconds to do that while still in the foreground, then stops as
 * Android requires.
 */
public final class XgramChatExportService extends Service {
  private static final String ACTION_START = "com.fahadali.xgram.export.START";
  private static final String ACTION_CANCEL = "com.fahadali.xgram.export.CANCEL";
  private static final String EXTRA_JOB = "job";
  /**
   * After onTimeout Android crashes the app if the service has not stopped within a few seconds (about 10 s in AOSP).
   * The job finishes its partial .zip in the first half of that, while the service is still in the foreground.
   */
  private static final long TIMEOUT_GRACE_MS = 5_000L;

  @Nullable
  private static XgramChatExportService instance;

  /**
   * Starts the service for the export that just started, from the main thread while XGram is in front. False when
   * Android refuses it: the export then goes on in the app's process only.
   */
  @UiThread
  static boolean start (Context context, ExportJob job) {
    final Intent intent = new Intent(context, XgramChatExportService.class)
      .setAction(ACTION_START)
      .putExtra(EXTRA_JOB, job.id);
    try {
      ContextCompat.startForegroundService(context, intent);
      return true;
    } catch (Throwable t) {
      Log.w("XGram export: Android refused the foreground service, the export runs in the app only", t);
      return false;
    }
  }

  /** The notification's Cancel: it only reaches the job it was made for. */
  static Intent cancelIntent (Context context, ExportJob job) {
    return new Intent(context, XgramChatExportService.class)
      .setAction(ACTION_CANCEL)
      .putExtra(EXTRA_JOB, job.id);
  }

  /**
   * The export ended (any way): out of the foreground with its progress notification, and gone. A service whose
   * start is still queued is left to it: stopping before startForeground crashes the app, and the start sees the
   * export is over and stops then.
   */
  @UiThread
  static void onJobFinished () {
    final XgramChatExportService service = instance;
    if (service != null && service.startHandled) {
      service.finish();
    }
  }

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable timeoutStop = this::stopAfterTimeout;
  private boolean foreground, startHandled;

  @Override
  public void onCreate () {
    super.onCreate();
    instance = this;
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand (@Nullable Intent intent, int flags, int startId) {
    final ExportJob job = ExportJob.current();
    if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
      // Only a flag for the worker: it stops and deletes the unfinished .zip itself
      final String jobId = intent.getStringExtra(EXTRA_JOB);
      if (job != null && job.id.equals(jobId)) {
        job.cancel();
      } else if (job == null) {
        // A progress notification a killed process left (service refused or detached): with no export running it can
        // only be a leftover, and a live foreground service's own one can't be removed by the app anyway
        ExportNotifications.cancelProgress(this);
      }
      if (!foreground && !startHandled) {
        stopSelf(startId); // started by the Cancel alone (the export runs without the service)
      }
      return START_NOT_STICKY;
    }
    // Started with startForegroundService: Android requires startForeground, even when the export already ended
    startHandled = true;
    final Notification notification = ExportNotifications.progress(this, job);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(ExportNotifications.ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
      } else {
        startForeground(ExportNotifications.ID_PROGRESS, notification);
      }
      foreground = true;
    } catch (Throwable t) {
      // Background start restrictions, or Android 15's dataSync time already used up today: the job goes on in the
      // app's process, and the service must still stop at once
      Log.w("XGram export: startForeground refused, the export runs in the app only", t);
      if (job != null) {
        job.onForegroundChanged(ExportJob.FOREGROUND_REFUSED);
      }
      stopSelf();
      return START_NOT_STICKY;
    }
    if (job == null || job.phase() >= ExportJob.PHASE_DONE) {
      finish(); // it ended before the service started
      return START_NOT_STICKY;
    }
    job.onForegroundChanged(ExportJob.FOREGROUND_ON);
    return START_NOT_STICKY;
  }

  /**
   * Android 15+: the dataSync foreground time (6 hours a day, shared with the contacts sync) is used up. Finalizing
   * after stopSelf could hang (a background process is frozen within seconds), so the job stops taking pages and
   * commits a partial .zip now, with result.json stored uncompressed; the service stops when it is done, or after
   * {@link #TIMEOUT_GRACE_MS} at the latest.
   */
  @Override
  public void onTimeout (int startId, int fgsType) {
    final ExportJob job = ExportJob.current();
    Log.w("XGram export: Android's dataSync time limit, job %s", job != null ? job.id : "none");
    if (job == null || job.phase() >= ExportJob.PHASE_DONE) {
      finish();
      return;
    }
    job.stopForTimeLimit();
    handler.removeCallbacks(timeoutStop);
    handler.postDelayed(timeoutStop, TIMEOUT_GRACE_MS);
  }

  /** The partial .zip took longer than Android allows: the service goes now, the notification stays until the job ends. */
  private void stopAfterTimeout () {
    final ExportJob job = ExportJob.current();
    if (job != null) {
      job.onForegroundChanged(ExportJob.FOREGROUND_ENDED);
    }
    if (foreground) {
      foreground = false;
      ServiceCompat.stopForeground(this, job != null ? ServiceCompat.STOP_FOREGROUND_DETACH : ServiceCompat.STOP_FOREGROUND_REMOVE);
    }
    stopSelf();
  }

  private void finish () {
    handler.removeCallbacks(timeoutStop);
    if (foreground) {
      foreground = false;
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }
    stopSelf();
  }

  @Override
  public void onDestroy () {
    if (instance == this) {
      instance = null;
    }
    handler.removeCallbacks(timeoutStop);
    final ExportJob job = ExportJob.current();
    if (foreground) {
      foreground = false;
      // Destroyed while the export still runs: its notification stays (no longer ongoing) until the job ends
      ServiceCompat.stopForeground(this, job != null ? ServiceCompat.STOP_FOREGROUND_DETACH : ServiceCompat.STOP_FOREGROUND_REMOVE);
    }
    if (job != null && job.isForeground()) {
      job.onForegroundChanged(ExportJob.FOREGROUND_ENDED);
    }
    super.onDestroy();
  }
}
