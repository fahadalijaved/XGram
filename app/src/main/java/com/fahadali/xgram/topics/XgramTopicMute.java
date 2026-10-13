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
package com.fahadali.xgram.topics;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which forum topics are muted, for notifications. TDLib builds local notifications from the chat's mute state only
 * and never looks at the topic's own notification settings, so a muted topic keeps notifying while the app is
 * connected. This remembers the time until which each topic is muted, fed by updateForumTopic, loaded topics and
 * the mute actions themselves. The time is stored, not the duration, because TDLib sends nothing when a topic
 * mute expires.
 * <p>
 * What stays as it is: an unmuted topic inside a muted forum never notifies, because TDLib does not create the
 * notification at all.
 */
public final class XgramTopicMute {
  private XgramTopicMute () { }

  /** Key: account, chat and topic. Value: muted until this time (ms), 0 when the topic is known and not muted. */
  private static final Map<String, Long> mutedUntil = new ConcurrentHashMap<>();

  private static String key (Tdlib tdlib, long chatId, int forumTopicId) {
    return tdlib.id() + "_" + chatId + "_" + forumTopicId;
  }

  /** Remembers the topic's notification settings; returns the time until which it is muted (0 when it is not). */
  public static long onSettings (Tdlib tdlib, long chatId, int forumTopicId, @Nullable TdApi.ChatNotificationSettings settings) {
    long until;
    if (settings == null || settings.useDefaultMuteFor || settings.muteFor <= 0) {
      until = 0;
    } else {
      until = System.currentTimeMillis() + settings.muteFor * 1000L;
    }
    mutedUntil.put(key(tdlib, chatId, forumTopicId), until);
    return until;
  }

  public static void onSettings (Tdlib tdlib, long chatId, @Nullable TdApi.ForumTopic topic) {
    if (topic != null && topic.info != null) {
      onSettings(tdlib, chatId, topic.info.forumTopicId, topic.notificationSettings);
    }
  }

  /** Whether the topic's settings have been seen since the app started. */
  public static boolean isKnown (Tdlib tdlib, long chatId, int forumTopicId) {
    return mutedUntil.containsKey(key(tdlib, chatId, forumTopicId));
  }

  /** Whether the topic is muted by its own settings. An unknown topic counts as not muted. */
  public static boolean isMuted (Tdlib tdlib, long chatId, int forumTopicId) {
    Long until = mutedUntil.get(key(tdlib, chatId, forumTopicId));
    return until != null && until > System.currentTimeMillis();
  }

  /**
   * Whether the message's topic is muted. Safe on any thread. When the topic was never seen, its settings are
   * asked for so that the next messages of that topic are filtered; this one is let through.
   */
  public static boolean isMutedMessage (Tdlib tdlib, @Nullable TdApi.Message message) {
    if (message == null || !(message.topicId instanceof TdApi.MessageTopicForum)) {
      return false;
    }
    final long chatId = message.chatId;
    final int forumTopicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
    Long until = mutedUntil.get(key(tdlib, chatId, forumTopicId));
    if (until == null) {
      TdlibForumTopicManager.Key key = new TdlibForumTopicManager.Key(chatId, forumTopicId);
      TdlibForumTopicManager.Entry entry = tdlib.topics().find(key); // synchronized, fine off the UI thread
      if (entry != null && entry.value != null) {
        until = onSettings(tdlib, chatId, forumTopicId, entry.value.notificationSettings);
      } else {
        tdlib.ui().post(() -> tdlib.topics().findOrRequest(key, (loadedEntry, inPlace) -> {
          if (loadedEntry.value != null) {
            onSettings(tdlib, chatId, forumTopicId, loadedEntry.value.notificationSettings);
          }
        }));
        return false;
      }
    }
    return until > System.currentTimeMillis();
  }

  public static void forgetAccount (int accountId) {
    com.fahadali.xgram.XgramAccountData.removeKeys(mutedUntil.keySet(), accountId + "_");
  }
}
