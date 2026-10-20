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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.unsorted.AppContext;

import java.util.concurrent.TimeUnit;

/**
 * Check In's notifications, built from a record alone (no TDLib): the ongoing one (also the location service's), "Are
 * you OK?" and its last call, "Check In alert sent" and the one-off notices. Channels have no group, so Telegram X's
 * per-account channel cleanup leaves them alone.
 */
final class XgramCheckInNotifier {
  private XgramCheckInNotifier () { }

  /** The fixed notification ids of Telegram X count down from Integer.MAX_VALUE to MAX_VALUE - 5. */
  static final int ID_ONGOING = Integer.MAX_VALUE - 40;
  private static final String TAG = "xgram_checkin";
  private static final int ID_PROMPT = 1, ID_FIRED = 2, ID_NOTICE = 3;
  static final String CHANNEL_STATUS = "xgram_checkin_status", CHANNEL_REMINDERS = "xgram_checkin_reminders";

  private static final int RC_OPEN = 0x5C16, RC_SAFE = 0x5C14, RC_ADD15 = 0x5C15;

  @Nullable
  private static NotificationManager manager (Context context) {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  static void ensureChannels (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    NotificationManager m = manager(context);
    if (m == null) {
      return;
    }
    try {
      NotificationChannel status = new NotificationChannel(CHANNEL_STATUS, Lang.getString(R.string.XgramCheckInChannelStatus), NotificationManager.IMPORTANCE_LOW);
      status.enableVibration(false);
      status.enableLights(false);
      status.setSound(null, null);
      status.setShowBadge(false);
      m.createNotificationChannel(status);
      NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS, Lang.getString(R.string.XgramCheckInChannelReminders), NotificationManager.IMPORTANCE_HIGH);
      reminders.enableVibration(true);
      reminders.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build());
      m.createNotificationChannel(reminders);
    } catch (Throwable t) {
      XgramCheckIn.log("channels: %s", t.toString());
    }
  }

  private static String time (long serverSeconds) {
    return Lang.time(serverSeconds, TimeUnit.SECONDS);
  }

  /** Opens the chat (the right pane on an unfolded Fold), at the start message once it is known. */
  private static PendingIntent openChat (Context context, CheckInRecord r) {
    Intent intent = new Intent(context, MainActivity.class);
    Intents.secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_CHAT + ".ci." + r.acc + "." + r.chat);
    intent.putExtra("account_id", r.acc);
    intent.putExtra("chat_id", r.chat);
    intent.putExtra("message_id", r.startReal ? r.startMsgId : 0L);
    intent.putExtra(com.fahadali.xgram.topics.XgramForums.EXTRA_NOTIFICATION_TOPIC_ID, r.topic);
    return PendingIntent.getActivity(context, RC_OPEN, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static PendingIntent action (Context context, String action, int requestCode, CheckInRecord r) {
    Intent intent = new Intent(context, XgramCheckInReceiver.class);
    intent.setAction(action);
    intent.setPackage(context.getPackageName());
    intent.putExtra(XgramCheckInReceiver.EXTRA_ACC, r.acc);
    intent.putExtra(XgramCheckInReceiver.EXTRA_REF, r.ref);
    return PendingIntent.getBroadcast(context, requestCode, intent, Intents.mutabilityFlags(false) | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static NotificationCompat.Action safeAction (Context context, CheckInRecord r) {
    return new NotificationCompat.Action.Builder(R.drawable.baseline_check_24, Lang.getString(R.string.XgramCheckInImSafe), action(context, XgramCheckInReceiver.ACTION_SAFE, RC_SAFE, r))
      .setAuthenticationRequired(true)
      .build();
  }

  private static NotificationCompat.Action addAction (Context context, CheckInRecord r) {
    return new NotificationCompat.Action.Builder(R.drawable.baseline_schedule_24, Lang.getString(R.string.XgramCheckInAdd15Short), action(context, XgramCheckInReceiver.ACTION_ADD15, RC_ADD15, r))
      .setAuthenticationRequired(true)
      .build();
  }

  private static Notification publicVersion (Context context, String channel) {
    return new NotificationCompat.Builder(context, channel)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setContentTitle(Lang.getString(R.string.XgramCheckInActive))
      .build();
  }

  private static String distanceText () {
    float d = XgramCheckIn.distanceToDest;
    return d >= 0 ? Lang.shortDistance(d) : null;
  }

  /** The ongoing notification; the location service shows the same one (same id) while it runs. */
  static Notification buildOngoing (Context context, @Nullable CheckInRecord r) {
    ensureChannels(context);
    NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_STATUS)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(context, CHANNEL_STATUS));
    if (r == null) {
      return b.setContentTitle(Lang.getString(R.string.XgramCheckInActive)).build();
    }
    b.setContentTitle(Lang.getString(R.string.XgramCheckInWith, r.title));
    b.setContentIntent(openChat(context, r));
    String text, extra = null;
    switch (r.state) {
      case CheckInRecord.STARTING:
        text = Lang.getString(R.string.XgramCheckInSettingUp);
        break;
      case CheckInRecord.CLOSING:
        text = Lang.getString(R.string.XgramCheckInEnding);
        extra = Lang.getString(R.string.XgramCheckInClosingText, time(r.sendAt), r.title);
        break;
      case CheckInRecord.FIRED:
        text = Lang.getString(R.string.XgramCheckInFiredInfo, time(r.sendAt));
        b.addAction(safeAction(context, r));
        break;
      case CheckInRecord.ARMED:
      default: {
        if (r.hasDest) {
          String distance = distanceText();
          text = distance != null ?
            Lang.getString(R.string.XgramCheckInOngoingDest, CheckInText.placeLabel(r), distance, time(r.sendAt)) :
            Lang.getString(R.string.XgramCheckInOngoingDestNoDistance, CheckInText.placeLabel(r), time(r.sendAt));
        } else {
          text = Lang.getString(R.string.XgramCheckInOngoingText, time(r.eta), time(r.sendAt));
        }
        b.setWhen(r.toDeviceMillis(r.sendAt));
        b.setShowWhen(true);
        b.setUsesChronometer(true);
        b.setChronometerCountDown(true);
        b.addAction(safeAction(context, r));
        b.addAction(addAction(context, r));
        break;
      }
    }
    if (extra == null && r.state != CheckInRecord.CLOSING) {
      if (XgramCheckIn.isOffline(r.acc)) {
        extra = Lang.getString(R.string.XgramCheckInOffline);
      } else if ((r.loc || r.hasDest) && XgramCheckIn.locationPaused) {
        extra = Lang.getString(R.string.XgramCheckInPaused);
      } else if ((r.loc || r.hasDest) && XgramCheckIn.locationServicesOff) {
        extra = Lang.getString(R.string.XgramCheckInLocationServicesOff);
      }
    }
    b.setContentText(text);
    if (extra != null) {
      b.setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\n" + extra));
    }
    return b.build();
  }

  private static void notify (Context context, @Nullable String tag, int id, Notification n) {
    NotificationManager m = manager(context);
    if (m == null) {
      return;
    }
    try {
      m.notify(tag, id, n);
    } catch (Throwable t) {
      XgramCheckIn.log("notify %d: %s", id, t.toString());
    }
  }

  private static void cancel (@Nullable String tag, int id) {
    Context context = AppContext.get();
    NotificationManager m = manager(context);
    if (m != null) {
      try {
        m.cancel(tag, id);
      } catch (Throwable ignored) { }
    }
  }

  static void showOngoing (CheckInRecord r) {
    Context context = AppContext.get();
    notify(context, null, ID_ONGOING, buildOngoing(context, r));
  }

  static void cancelOngoing () {
    cancel(null, ID_ONGOING);
  }

  /** "Are you OK?" at the back-by time; [lastCall]: posted again with sound 2 minutes before the alert. */
  static void prompt (CheckInRecord r, boolean lastCall) {
    Context context = AppContext.get();
    ensureChannels(context);
    String text = lastCall ? Lang.getString(R.string.XgramCheckInLastCall) :
      r.hasDest ? Lang.getString(R.string.XgramCheckInPromptDest, CheckInText.placeLabel(r), r.title, time(r.sendAt)) :
      Lang.getString(R.string.XgramCheckInPromptText, r.title, time(r.sendAt));
    Notification n = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setContentTitle(Lang.getString(R.string.XgramCheckInAreYouOk))
      .setContentText(text)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(context, CHANNEL_REMINDERS))
      .setWhen(r.toDeviceMillis(r.sendAt))
      .setShowWhen(true)
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setAutoCancel(true)
      .setContentIntent(openChat(context, r))
      .addAction(safeAction(context, r))
      .addAction(addAction(context, r))
      .build();
    notify(context, TAG, ID_PROMPT, n);
  }

  static void cancelPrompt () {
    cancel(TAG, ID_PROMPT);
  }

  static void fired (CheckInRecord r) {
    Context context = AppContext.get();
    ensureChannels(context);
    String text = Lang.getString(R.string.XgramCheckInAlertSentText, r.title);
    Notification n = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setContentTitle(Lang.getString(R.string.XgramCheckInAlertSentTitle))
      .setContentText(text)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(context, CHANNEL_REMINDERS))
      .setAutoCancel(true)
      .setContentIntent(openChat(context, r))
      .addAction(safeAction(context, r))
      .build();
    notify(context, TAG, ID_FIRED, n);
  }

  static void cancelFired () {
    cancel(TAG, ID_FIRED);
  }

  /** A one-off notice (ended, didn't start, logged out, arrived). */
  static void notice (CheckInRecord r, String title, String text) {
    Context context = AppContext.get();
    ensureChannels(context);
    Notification n = new NotificationCompat.Builder(context, CHANNEL_STATUS)
      .setSmallIcon(XgramSettings.notificationSmallIcon())
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(context, CHANNEL_STATUS))
      .setAutoCancel(true)
      .setContentIntent(openChat(context, r))
      .build();
    notify(context, TAG, ID_NOTICE, n);
  }
}
