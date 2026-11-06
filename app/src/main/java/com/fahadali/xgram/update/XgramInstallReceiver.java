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
package com.fahadali.xgram.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

import java.io.File;

/**
 * The status of an update install session ({@link XgramSelfInstaller}). Not exported: only the system's PendingIntent
 * reaches it. "Waiting for the user" opens the system's confirmation ("Update this app?"); a failure other than the
 * user's Cancel opens the APK in the system installer as before, which shows what is wrong.
 * <p>
 * Two things the system installer did that a session doesn't: with XGram off screen when the confirmation is due
 * (Home, the screen off while the APK was copied) Android blocks its start without an error, so it waits for XGram's
 * next resume ({@link #onResume}) and a notification opens it now; and after "Update" XGram just closes, with no "App
 * installed / Open", so the new version (this receiver runs in its process: STATUS_SUCCESS starts it) comes back on
 * its own when it may start from the background (Display over other apps), and posts "XGram updated, tap to open"
 * otherwise. Main thread, as every receiver of the manifest.
 */
public final class XgramInstallReceiver extends BroadcastReceiver {
  /** No group, so Telegram X's per-account channel cleanup leaves it alone. Silent, but it pops up (heads-up). */
  private static final String CHANNEL = "xgram_self_update_v1";
  /** "Tap to finish updating" (the session's id as id) and "XGram updated" (one id): tagged, apart from the others. */
  private static final String TAG_CONFIRM = "xgram_self_update_confirm", TAG_DONE = "xgram_self_update_done";
  private static final int ID_DONE = 1;
  private static final long DONE_TIMEOUT_MS = 2L * 60L * 1000L;
  /** After the direct start: how long XGram has to come up before the notification shows instead. */
  private static final long REOPEN_CHECK_MS = 4000L;

  /** The confirmation that couldn't open while XGram was off screen, its session and APK: opened on the next resume. */
  @Nullable private static Intent pendingConfirm;
  private static int pendingSessionId = -1;
  @Nullable private static String pendingPath;
  /** This process posted "XGram updated": it goes once XGram is on screen. */
  private static boolean donePosted;

  @Override
  public void onReceive (Context context, Intent intent) {
    if (intent == null) {
      return;
    }
    final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    final int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
    final String path = intent.getStringExtra(XgramSelfInstaller.EXTRA_PATH);
    switch (status) {
      case PackageInstaller.STATUS_PENDING_USER_ACTION: {
        final Intent confirm = confirmIntent(intent);
        if (confirm == null) {
          abandon(context, sessionId); // no staged copy of the APK left behind
          openWithSystemInstaller(path);
          return;
        }
        final BaseActivity activity = UI.getUiContext();
        if (activity != null && !activity.isFinishing() && activity.getActivityState() == UI.State.RESUMED) {
          // From the activity on screen (the user just tapped Install)
          try {
            activity.startActivity(confirm);
          } catch (Throwable t) {
            Log.e(XgramSelfInstaller.TAG, "cannot open the install confirmation", t);
            abandon(context, sessionId);
            openWithSystemInstaller(path);
          }
        } else {
          // Off screen: a start from the background is blocked without an error. It waits for the next resume, and the
          // notification (a tap on it may start it) opens it now
          pendingConfirm = confirm;
          pendingSessionId = sessionId;
          pendingPath = path;
          notifyConfirm(context.getApplicationContext(), confirm, sessionId);
        }
        break;
      }
      case PackageInstaller.STATUS_SUCCESS: {
        finished(context, sessionId);
        onUpdated(context.getApplicationContext());
        break;
      }
      case PackageInstaller.STATUS_FAILURE_ABORTED: // Cancel (or abandoned): nothing, as when the system installer is closed
        finished(context, sessionId);
        break;
      default: {
        finished(context, sessionId);
        Log.w(XgramSelfInstaller.TAG, "update install session failed: " + status + " " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        openWithSystemInstaller(path);
        break;
      }
    }
  }

  /**
   * Main thread, MainActivity#onResume: the confirmation that couldn't open while XGram was off screen opens now, and
   * "XGram updated" goes.
   */
  public static void onResume (@NonNull BaseActivity activity) {
    if (donePosted) {
      donePosted = false;
      cancel(activity, TAG_DONE, ID_DONE);
    }
    final Intent confirm = pendingConfirm;
    if (confirm == null) {
      return;
    }
    final int sessionId = pendingSessionId;
    final String path = pendingPath;
    pendingConfirm = null;
    pendingSessionId = -1;
    pendingPath = null;
    cancel(activity, TAG_CONFIRM, sessionId);
    try {
      activity.startActivity(confirm);
    } catch (Throwable t) {
      Log.e(XgramSelfInstaller.TAG, "cannot open the install confirmation", t);
      abandon(activity, sessionId);
      openWithSystemInstaller(path);
    }
  }

  /** The session ended (installed, canceled, abandoned or failed): its confirmation no longer waits. */
  private static void finished (@NonNull Context context, int sessionId) {
    if (pendingConfirm != null && pendingSessionId == sessionId) {
      pendingConfirm = null;
      pendingSessionId = -1;
      pendingPath = null;
    }
    cancel(context, TAG_CONFIRM, sessionId);
  }

  /**
   * The update is installed and this is the new version's process. The system showed no "App installed / Open" and
   * XGram just closed: it comes back, or says how to.
   */
  private void onUpdated (@NonNull Context context) {
    final BaseActivity activity = UI.getUiContext();
    if (activity != null && activity.getActivityState() == UI.State.RESUMED) {
      return; // on screen already
    }
    boolean started = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
      // "Display over other apps" lets XGram start from the background: straight back, as Open did
      try {
        context.startActivity(launchIntent(context));
        started = true;
      } catch (Throwable t) {
        Log.w(XgramSelfInstaller.TAG, "cannot reopen XGram after the update", t);
      }
    }
    if (!started) {
      notifyDone(context);
      return;
    }
    // A start Android still blocks shows nothing and throws nothing: the notification after all, unless XGram came up
    final PendingResult result = goAsync();
    UI.post(() -> {
      try {
        if (UI.getUiContext() == null) {
          notifyDone(context);
        }
      } finally {
        result.finish();
      }
    }, REOPEN_CHECK_MS);
  }

  @NonNull
  private static Intent launchIntent (@NonNull Context context) {
    return new Intent(context, MainActivity.class)
      .setAction(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  // Notifications

  private static void ensureChannel (@NonNull Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null || manager.getNotificationChannel(CHANNEL) != null) {
      return;
    }
    final NotificationChannel channel = new NotificationChannel(CHANNEL, Lang.getString(R.string.XgramUpdSelfChannel), NotificationManager.IMPORTANCE_HIGH);
    channel.setSound(null, null);
    channel.enableVibration(false);
    channel.enableLights(false);
    channel.setShowBadge(false);
    manager.createNotificationChannel(channel);
  }

  @NonNull
  private static NotificationCompat.Builder builder (@NonNull Context context) {
    ensureChannel(context);
    return new NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setSilent(true)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
  }

  /** "Tap to finish updating XGram": the tap opens the system's confirmation. */
  private static void notifyConfirm (@NonNull Context context, @NonNull Intent confirm, int sessionId) {
    try {
      final Intent start = new Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      final PendingIntent open = PendingIntent.getActivity(context, sessionId, start, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      final NotificationCompat.Builder b = builder(context)
        .setContentTitle(Lang.getString(R.string.XgramUpdSelfConfirmTitle))
        .setContentText(Lang.getString(R.string.XgramUpdSelfConfirmText))
        .setContentIntent(open);
      final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.notify(TAG_CONFIRM, sessionId, b.build());
      }
    } catch (Throwable t) {
      Log.w(XgramSelfInstaller.TAG, "cannot post the install confirmation", t);
    }
  }

  /** "XGram updated to <version>. Tap to open", for a couple of minutes. */
  private static void notifyDone (@NonNull Context context) {
    try {
      final PendingIntent open = PendingIntent.getActivity(context, ID_DONE, launchIntent(context), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      final NotificationCompat.Builder b = builder(context)
        .setContentTitle(Lang.getString(R.string.XgramUpdSelfDoneTitle, BuildConfig.VERSION_NAME))
        .setContentText(Lang.getString(R.string.XgramUpdSelfDoneText))
        .setContentIntent(open)
        .setTimeoutAfter(DONE_TIMEOUT_MS);
      final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.notify(TAG_DONE, ID_DONE, b.build());
        donePosted = true;
      }
    } catch (Throwable t) {
      Log.w(XgramSelfInstaller.TAG, "cannot post the update notification", t);
    }
  }

  private static void cancel (@NonNull Context context, @NonNull String tag, int id) {
    try {
      final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.cancel(tag, id);
      }
    } catch (Throwable ignored) { }
  }

  // Session

  /** A committed session the confirmation can't be shown for: abandoned, so its staged copy of the APK goes now. */
  private static void abandon (@NonNull Context context, int sessionId) {
    if (sessionId == -1) {
      return;
    }
    try {
      context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
    } catch (Throwable t) {
      Log.w(XgramSelfInstaller.TAG, "cannot abandon the update install session " + sessionId, t);
    }
  }

  @Nullable
  @SuppressWarnings("deprecation")
  private static Intent confirmIntent (Intent intent) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
      }
      return intent.getParcelableExtra(Intent.EXTRA_INTENT);
    } catch (Throwable t) {
      return null;
    }
  }

  private static void openWithSystemInstaller (@Nullable String path) {
    final BaseActivity activity = UI.getUiContext();
    if (path == null || activity == null || activity.isFinishing()) {
      return;
    }
    final File apk = new File(path);
    if (apk.isFile()) {
      UI.post(() -> Intents.openFile(activity, apk, XgramSelfInstaller.APK_MIME));
    }
  }
}
