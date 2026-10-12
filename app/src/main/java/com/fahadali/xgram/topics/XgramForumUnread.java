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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat list badge of groups with topics. The server doesn't lower a forum's unread count when its topics are read
 * one by one, and TDLib sends no update for it, so the badge would stay forever. Like the official apps, XGram counts
 * the unread messages of the topics instead.
 *
 * TDLib's own count still changes when new messages arrive: those changes are carried over to XGram's number.
 */
public final class XgramForumUnread {
  private XgramForumUnread () { }

  /** The badge XGram shows. */
  private static final Map<String, Integer> counts = new ConcurrentHashMap<>();
  /** The last count TDLib reported, to carry its changes (new messages) over. */
  private static final Map<String, Integer> tdlibCounts = new ConcurrentHashMap<>();
  private static final Set<String> requested = ConcurrentHashMap.newKeySet();
  private static final ThreadLocal<Boolean> applying = new ThreadLocal<>();

  private static String key (Tdlib tdlib, long chatId) {
    return tdlib.id() + "_" + chatId;
  }

  /**
   * TDLib thread, {@code updateChatReadInbox}: replaces TDLib's count of a forum with XGram's. Returns false when
   * XGram doesn't know the forum's topics yet (TDLib's count stays).
   */
  public static boolean rewrite (Tdlib tdlib, TdApi.UpdateChatReadInbox update) {
    String key = key(tdlib, update.chatId);
    Integer count = counts.get(key);
    if (count == null) {
      return false;
    }
    if (!tdlib.isForum(update.chatId)) {
      counts.remove(key);
      tdlibCounts.remove(key);
      return false;
    }
    if (!Boolean.TRUE.equals(applying.get())) {
      Integer previous = tdlibCounts.put(key, update.unreadCount);
      int delta = previous != null ? update.unreadCount - previous : 0;
      if (delta != 0) {
        count = Math.max(0, count + delta);
        counts.put(key, count);
      }
    }
    update.unreadCount = count;
    return true;
  }

  /** The topics of a forum changed: shows the sum of their unread messages. */
  public static void update (Tdlib tdlib, long chatId, int unreadCount) {
    String key = key(tdlib, chatId);
    if (!counts.containsKey(key)) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        return;
      }
      tdlibCounts.put(key, chat.unreadCount);
    }
    Integer previous = counts.put(key, unreadCount);
    if (previous == null || previous != unreadCount) {
      tdlib.runOnTdlibThread(() -> {
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat != null && chat.unreadCount != unreadCount) {
          applying.set(true);
          try {
            tdlib.xgramApplyReadInbox(chatId, chat.lastReadInboxMessageId, unreadCount);
          } finally {
            applying.set(false);
          }
        }
      });
    }
  }

  /** Chat list: counts the topics of a forum with unread messages once, so its badge is right without opening it. */
  public static void requestIfNeeded (Tdlib tdlib, TdApi.Chat chat) {
    if (chat.unreadCount <= 0) {
      return;
    }
    String key = key(tdlib, chat.id);
    if (counts.containsKey(key) || !tdlib.isForum(chat.id) || !requested.add(key)) {
      return;
    }
    long chatId = chat.id;
    // Topics come sorted by their last message, so the unread ones are in the first page.
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), (topics, error) -> {
      if (topics == null) {
        requested.remove(key);
        return;
      }
      int sum = 0;
      for (TdApi.ForumTopic topic : topics.topics) {
        sum += XgramForums.normalizeUnread(tdlib, topic).unreadCount;
      }
      update(tdlib, chatId, sum);
    });
  }

  public static void forgetAccount (int accountId) {
    String prefix = accountId + "_";
    com.fahadali.xgram.XgramAccountData.removeKeys(counts.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(tdlibCounts.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(requested, prefix);
  }
}
