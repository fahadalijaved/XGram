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

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;

/**
 * The export's notifications: the progress one (also the foreground service's), and one result per export (done,
 * stopped by Android's time limit, failed, canceled). Two channels with no group, so Telegram X's per-account channel
 * cleanup leaves them alone: a silent LOW one for progress (and a cancel, which the user just asked for) and a
 * DEFAULT one for results. Every PendingIntent of a job uses the job's own request code with FLAG_UPDATE_CURRENT, so
 * a later export's Share never sends an older .zip (chooser intents differ only in their extras).
 */
final class ExportNotifications {
  private ExportNotifications () { }

  /** Telegram X's fixed ids count down from Integer.MAX_VALUE to MAX_VALUE - 5; Check In's ongoing one is MAX_VALUE - 40. */
  static final int ID_PROGRESS = Integer.MAX_VALUE - 41;
  /** Results are tagged, with the job's number as id: one per export, apart from every other notification. */
  private static final String TAG_RESULT = "xgram_export";
  static final String CHANNEL_PROGRESS = "xgram_export_progress_v1", CHANNEL_DONE = "xgram_export_done_v1";
  private static final long CANCELED_TIMEOUT_MS = 10_000L;

  @Nullable
  private static NotificationManager manager (Context context) {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  /** The channels exist: once per process, not two binder calls with every progress update. */
  private static volatile boolean channelsReady;

  static void ensureChannels (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || channelsReady) {
      return;
    }
    final NotificationManager m = manager(context);
    if (m == null) {
      return;
    }
    try {
      final NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS, Lang.getString(R.string.XgramExportChannelProgress), NotificationManager.IMPORTANCE_LOW);
      progress.enableVibration(false);
      progress.enableLights(false);
      progress.setSound(null, null);
      progress.setShowBadge(false);
      m.createNotificationChannel(progress);
      final NotificationChannel done = new NotificationChannel(CHANNEL_DONE, Lang.getString(R.string.XgramExportChannelDone), NotificationManager.IMPORTANCE_DEFAULT);
      done.setShowBadge(false);
      m.createNotificationChannel(done);
      channelsReady = true;
    } catch (Throwable t) {
      Log.w("XGram export: notification channels", t);
    }
  }

  // PendingIntents

  /** Opens the exported chat, where the progress bar shows (Expressive design). */
  private static PendingIntent openChat (Context context, ExportJob job) {
    final Intent intent = new Intent(context, MainActivity.class);
    Intents.secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_CHAT + ".export." + job.accountId + "." + job.chatId);
    intent.putExtra("account_id", job.accountId);
    intent.putExtra("chat_id", job.chatId);
    return PendingIntent.getActivity(context, job.number, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /** Cancel, handled by the service (it only forwards it to the worker). */
  private static PendingIntent cancel (Context context, ExportJob job) {
    final Intent intent = XgramChatExportService.cancelIntent(context, job);
    return PendingIntent.getService(context, job.number, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /**
   * Tap on a result: the .zip in the app that opens zips (with read access), else the Downloads app, else the share
   * sheet, so a tap never does nothing.
   */
  private static PendingIntent openZip (Context context, ExportJob job, Uri uri) {
    Intent intent = new Intent(Intent.ACTION_VIEW)
      .setDataAndType(uri, ExportOutput.MIME)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.setClipData(ClipData.newRawUri(null, uri));
    if (!canStart(context, intent)) {
      final Intent downloads = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent = canStart(context, downloads) ? downloads : shareChooser(uri, job);
    }
    return PendingIntent.getActivity(context, job.number, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static PendingIntent share (Context context, ExportJob job, Uri uri) {
    return PendingIntent.getActivity(context, job.number, shareChooser(uri, job), Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static Intent shareChooser (Uri uri, ExportJob job) {
    return XgramExport.shareChooser(uri, job.realTitle()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  private static boolean canStart (Context context, Intent intent) {
    try {
      return intent.resolveActivity(context.getPackageManager()) != null;
    } catch (Throwable t) {
      return false;
    }
  }

  private static Notification publicVersion (Context context, String channel, CharSequence title) {
    return new NotificationCompat.Builder(context, channel)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setContentTitle(title)
      .build();
  }

  // Progress

  /**
   * The progress notification: indeterminate while the history is read and while the .zip is finished, N of M while
   * writing, the flood or network wait when there is one. Ongoing only while the foreground service holds it; when
   * Android refused the service it says to keep XGram open. {@code job} null: a placeholder the service shows when
   * the export ended before it started.
   */
  @NonNull
  static Notification progress (Context context, @Nullable ExportJob job) {
    ensureChannels(context);
    final NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setShowWhen(false)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(context, CHANNEL_PROGRESS, Lang.getString(R.string.XgramExportChannelProgress)))
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
    if (job == null) {
      return b.setContentTitle(Lang.getString(R.string.XgramExportChannelProgress)).build();
    }
    final ExportProgress p = job.progress();
    final boolean foreground = job.isForeground();
    b.setOngoing(foreground);
    b.setContentTitle(Lang.getString(R.string.XgramExportRunning, job.title()));
    final String text = p.text();
    b.setContentText(text);
    if (job.isBackgroundRefused()) {
      b.setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\n" + Lang.getString(R.string.XgramExportKeepOpen)));
    }
    final boolean indeterminate = p.phase == ExportJob.PHASE_INDEXING || p.phase == ExportJob.PHASE_CHECKING_SPACE ||
      p.phase == ExportJob.PHASE_FINALIZING || p.total <= 0;
    b.setProgress(1000, indeterminate ? 0 : Math.round(Math.max(0f, Math.min(1f, p.fraction)) * 1000f), indeterminate);
    b.setContentIntent(openChat(context, job));
    if (p.phase < ExportJob.PHASE_FINALIZING && !job.isCanceled()) {
      b.addAction(R.drawable.baseline_cancel_24, Lang.getString(R.string.Cancel), cancel(context, job));
    }
    return b.build();
  }

  /** Updates the progress notification; the job calls it at most once a second, and never after it ended. */
  static void showProgress (Context context, ExportJob job) {
    final NotificationManager m = manager(context);
    if (m == null) {
      return;
    }
    try {
      m.notify(ID_PROGRESS, progress(context, job));
    } catch (Throwable t) {
      Log.w("XGram export: progress notification", t);
    }
  }

  /** Removes a progress notification the service no longer holds (refused or stopped, or left by a killed process). */
  static void cancelProgress (Context context) {
    final NotificationManager m = manager(context);
    if (m != null) {
      try {
        m.cancel(ID_PROGRESS);
      } catch (Throwable ignored) { }
    }
  }

  // Results

  /** Whether a result notification can show: notifications on for XGram, and the results channel not turned off. */
  static boolean canShowResult (Context context) {
    try {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        final NotificationManager m = manager(context);
        final NotificationChannel channel = m != null ? m.getNotificationChannel(CHANNEL_DONE) : null;
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
      }
      return true;
    } catch (Throwable t) {
      return true;
    }
  }

  /** The result of a finished export, for when XGram was not in front to show it. */
  static void showResult (Context context, ExportJob job) {
    ensureChannels(context);
    final int phase = job.phase();
    final String channel = phase == ExportJob.PHASE_CANCELED ? CHANNEL_PROGRESS : CHANNEL_DONE;
    final NotificationCompat.Builder b = new NotificationCompat.Builder(context, channel)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
    final String title, text;
    final Uri uri = job.resultUri();
    switch (phase) {
      case ExportJob.PHASE_DONE:
      case ExportJob.PHASE_PARTIAL: {
        final boolean partial = phase == ExportJob.PHASE_PARTIAL;
        title = Lang.getString(partial ? R.string.XgramExportPartialTitle : R.string.XgramExportDone);
        final String name = job.resultName();
        final String location = job.resultLocation();
        final String saved = location == null || ExportOutput.PUBLIC_LABEL.equals(location) ?
          Lang.getString(R.string.XgramExportSavedDownloads) :
          Lang.getString(R.string.XgramExportSavedIn, location);
        final StringBuilder big = new StringBuilder();
        if (partial) {
          big.append(Lang.getString(R.string.XgramExportPartial)).append('\n');
        }
        big.append(name != null ? name : job.realTitle()).append('\n').append(saved);
        text = partial ? Lang.getString(R.string.XgramExportPartial) : job.title() + " · " + saved;
        b.setCategory(NotificationCompat.CATEGORY_STATUS);
        b.setStyle(new NotificationCompat.BigTextStyle().bigText(big));
        if (uri != null) {
          b.setContentIntent(openZip(context, job, uri));
          b.addAction(R.drawable.baseline_share_24, Lang.getString(R.string.Share), share(context, job, uri));
        } else {
          b.setContentIntent(openChat(context, job));
        }
        break;
      }
      case ExportJob.PHASE_CANCELED:
        title = Lang.getString(R.string.XgramExportCanceled);
        text = job.title();
        b.setSilent(true);
        b.setTimeoutAfter(CANCELED_TIMEOUT_MS);
        b.setContentIntent(openChat(context, job));
        break;
      default: {
        title = Lang.getString(R.string.XgramExportFailed);
        final String error = job.errorText();
        text = error != null ? error : job.title();
        b.setCategory(NotificationCompat.CATEGORY_ERROR);
        b.setStyle(new NotificationCompat.BigTextStyle().bigText(error != null ? job.title() + "\n" + error : text));
        b.setContentIntent(openChat(context, job));
        break;
      }
    }
    b.setContentTitle(title);
    b.setContentText(text);
    b.setPublicVersion(publicVersion(context, channel, title));
    final NotificationManager m = manager(context);
    if (m == null) {
      return;
    }
    try {
      m.notify(TAG_RESULT, job.number, b.build());
    } catch (Throwable t) {
      Log.w("XGram export: result notification", t);
    }
  }
}
