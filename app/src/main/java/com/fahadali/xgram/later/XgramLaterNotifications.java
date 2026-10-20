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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.unsorted.Passcode;

/**
 * Later's own notifications: "Reminders" (high importance, message reminders and follow-ups) and "Snoozed chats"
 * (low, silent: a timed snooze ended). Both channels are ungrouped, so Telegram X's channel cleanup leaves them alone.
 * Posting needs no TDLib: the record carries the title and preview it shows.
 */
final class XgramLaterNotifications {
  private XgramLaterNotifications () { }

  static final String CHANNEL_REMINDERS = "xgram_later_reminders_v1";
  static final String CHANNEL_SNOOZED = "xgram_later_snoozed_v1";
  static final String TAG = "xgram_later";

  static int idOf (String key) {
    return key.hashCode();
  }

  private static int summaryId (int accountId) {
    return ("summary_" + accountId).hashCode();
  }

  static void ensureChannels (Context ctx) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }
    try {
      if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDERS, Lang.getString(R.string.XgramLaterChannelReminders), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(Lang.getString(R.string.XgramLaterChannelRemindersDesc));
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
      }
      if (manager.getNotificationChannel(CHANNEL_SNOOZED) == null) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_SNOOZED, Lang.getString(R.string.XgramLaterChannelSnoozed), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(Lang.getString(R.string.XgramLaterChannelSnoozedDesc));
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
      }
    } catch (Throwable t) {
      Log.e(XgramLaterStore.TAG, "Cannot create the channels", t);
    }
  }

  /** Whether a notification on {@code channelId} would show: notifications allowed and the channel not blocked. */
  static boolean canPost (Context ctx, String channelId) {
    NotificationManagerCompat compat = NotificationManagerCompat.from(ctx);
    if (!compat.areNotificationsEnabled()) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel = manager != null ? manager.getNotificationChannel(channelId) : null;
      return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    return true;
  }

  /** For the Later page's status row: reminders can't ring. */
  static boolean remindersBlocked (Context ctx) {
    ensureChannels(ctx);
    return !canPost(ctx, CHANNEL_REMINDERS);
  }

  /** Whether the record's content may show: not secret, protected or preview-off, and not behind a locked passcode. */
  private static boolean contentHidden (XgramLaterRecord r) {
    return r.hasFlag(XgramLaterRecord.FLAG_SECRET) || r.hasFlag(XgramLaterRecord.FLAG_PROTECTED) || r.hasFlag(XgramLaterRecord.FLAG_NO_PREVIEW) || passcodeHides();
  }

  private static boolean passcodeHides () {
    try {
      Passcode passcode = Passcode.instance();
      return passcode.isLocked() && !passcode.displayNotifications();
    } catch (Throwable t) {
      return true;
    }
  }

  private static String generic (XgramLaterRecord r) {
    return Lang.getString(r.hasFlag(XgramLaterRecord.FLAG_SECRET) ? R.string.XgramLaterNotifSecret : R.string.XgramLaterNotifGeneric);
  }

  static Intent openIntent (Context ctx, XgramLaterRecord r) {
    Intent intent = new Intent(ctx, MainActivity.class);
    Intents.secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_CHAT + ".later." + idOf(r.key));
    intent.putExtra("account_id", r.accountId);
    intent.putExtra("chat_id", r.chatId);
    intent.putExtra("message_id", r.hasFlag(XgramLaterRecord.FLAG_MSG_GONE) ? 0L : r.messageId);
    if (r.topicId != 0) {
      intent.putExtra(com.fahadali.xgram.topics.XgramForums.EXTRA_NOTIFICATION_TOPIC_ID, r.topicId);
    }
    return intent;
  }

  /** A broadcast to the receiver for "Snooze 1 h" or "Done": the key is the data URI, so each notification's intent is its own. */
  private static PendingIntent action (Context ctx, String action, String key) {
    Intent intent = new Intent(ctx, XgramLaterReceiver.class);
    intent.setAction(action);
    intent.setData(Uri.fromParts(XgramLaterReceiver.SCHEME, key, null));
    return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false));
  }

  /** The notification of one record that became due. Skipped when it couldn't show. */
  static void post (Context ctx, XgramLaterRecord r) {
    ensureChannels(ctx);
    final String channelId = r.isSnooze() ? CHANNEL_SNOOZED : CHANNEL_REMINDERS;
    if (!canPost(ctx, channelId)) {
      return;
    }
    final boolean hidden = contentHidden(r);
    final boolean hideTitle = r.hasFlag(XgramLaterRecord.FLAG_SECRET) || passcodeHides();
    // A message in a forum topic: "Chat › Topic", as the chat's own notifications
    final String chatTitle = r.title.isEmpty() || hideTitle ? Lang.getString(R.string.XgramLater) : r.topic.isEmpty() ? r.title : r.title + " › " + r.topic;
    String title, text;
    @Nullable String bigText = null;
    if (r.isSnooze()) {
      title = chatTitle;
      text = !hidden && !r.note.isEmpty() ? r.note : Lang.getString(R.string.XgramLaterNotifBack);
    } else if (r.kind == XgramLaterRecord.KIND_FOLLOWUP) {
      title = hideTitle ? Lang.getString(R.string.XgramLater) : Lang.getString(R.string.XgramLaterNotifNoReply, chatTitle);
      if (hidden) {
        text = generic(r);
      } else {
        String preview = r.preview.isEmpty() ? r.note : r.preview;
        text = preview.isEmpty() ? generic(r) : Lang.getString(R.string.XgramLaterYou, preview);
        if (!r.note.isEmpty() && !r.preview.isEmpty()) {
          bigText = r.note + "\n" + Lang.getString(R.string.XgramLaterYou, r.preview);
        }
      }
    } else {
      title = chatTitle;
      if (hidden) {
        text = generic(r);
      } else if (!r.note.isEmpty()) {
        text = r.note;
        if (!r.preview.isEmpty()) {
          bigText = r.note + "\n“" + r.preview + "”";
        }
      } else {
        text = r.preview.isEmpty() ? generic(r) : r.preview;
      }
    }

    NotificationCompat.Builder b = base(ctx, channelId, r.dueAt != 0 ? r.dueAt : r.wakeAt)
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(PendingIntent.getActivity(ctx, idOf(r.key), openIntent(ctx, r), PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false)))
      .addAction(R.drawable.baseline_watch_later_24, Lang.getString(R.string.XgramLaterNotifSnooze1h), action(ctx, XgramLaterReceiver.ACTION_SNOOZE, r.key))
      .addAction(R.drawable.baseline_check_24, Lang.getString(R.string.XgramLaterNotifDone), action(ctx, XgramLaterReceiver.ACTION_DONE, r.key))
      .setPublicVersion(base(ctx, channelId, r.dueAt != 0 ? r.dueAt : r.wakeAt)
        .setContentTitle(Lang.getString(R.string.XgramLater))
        .setContentText(Lang.getString(R.string.XgramLaterNotifGeneric))
        .build());
    if (!r.isSnooze()) {
      b.setSubText(Lang.getString(R.string.XgramLaterNotifReminder));
    }
    if (bigText != null) {
      b.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));
    }
    notify(ctx, idOf(r.key), b);
  }

  /** Four or more items came due in one pass (after a restart, a restore or a long time off): one notification. */
  static void postSummary (Context ctx, int accountId, int count) {
    ensureChannels(ctx);
    if (!canPost(ctx, CHANNEL_REMINDERS)) {
      return;
    }
    // A restored backup posts it for any number due, so one item needs the singular
    String text = count == 1 ? Lang.getString(R.string.XgramLaterNotifSummaryOne) : Lang.getString(R.string.XgramLaterNotifSummary, count);
    Intent intent = Intents.valueOfMain(accountId);
    NotificationCompat.Builder b = base(ctx, CHANNEL_REMINDERS, System.currentTimeMillis())
      .setContentTitle(Lang.getString(R.string.XgramLater))
      .setContentText(text)
      .setContentIntent(PendingIntent.getActivity(ctx, summaryId(accountId), intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false)))
      .setPublicVersion(base(ctx, CHANNEL_REMINDERS, System.currentTimeMillis())
        .setContentTitle(Lang.getString(R.string.XgramLater))
        .setContentText(text)
        .build());
    notify(ctx, summaryId(accountId), b);
  }

  private static NotificationCompat.Builder base (Context ctx, String channelId, long when) {
    return new NotificationCompat.Builder(ctx, channelId)
      .setSmallIcon(com.fahadali.xgram.XgramSettings.notificationSmallIcon())
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(CHANNEL_REMINDERS.equals(channelId) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW)
      .setWhen(when)
      .setShowWhen(true)
      .setAutoCancel(true)
      .setColor(Theme.getColor(ColorId.notification));
  }

  private static void notify (Context ctx, int id, NotificationCompat.Builder b) {
    try {
      NotificationManagerCompat.from(ctx).notify(TAG, id, b.build());
    } catch (Throwable t) {
      // No permission (Android 13+ without POST_NOTIFICATIONS) or a broken builder: the record stays due in Later
      Log.w(XgramLaterStore.TAG, "Cannot post a notification", t);
    }
  }

  static void cancel (Context ctx, String key) {
    try {
      NotificationManagerCompat.from(ctx).cancel(TAG, idOf(key));
    } catch (Throwable ignored) { }
  }

  static void cancelSummary (Context ctx, int accountId) {
    try {
      NotificationManagerCompat.from(ctx).cancel(TAG, summaryId(accountId));
    } catch (Throwable ignored) { }
  }

  /** The system settings of the Reminders channel (or the app's notification settings before Android 8). */
  static void openChannelSettings (Context ctx) {
    ensureChannels(ctx);
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
      intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
      intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_REMINDERS);
    } else {
      intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.getPackageName(), null));
    }
    openSettings(ctx, intent);
  }

  /** The app's notification settings (notifications are off for the whole app). */
  static void openAppNotificationSettings (Context ctx) {
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
    } else {
      intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.getPackageName(), null));
    }
    openSettings(ctx, intent);
  }

  private static void openSettings (Context ctx, Intent intent) {
    try {
      if (!(ctx instanceof android.app.Activity)) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      }
      ctx.startActivity(intent);
    } catch (Throwable t) {
      Log.w(XgramLaterStore.TAG, "Cannot open the settings", t);
    }
  }
}
