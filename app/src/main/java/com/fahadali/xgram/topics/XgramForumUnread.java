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

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

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

  /** The badge XGram shows for the forum, or TDLib's count when XGram hasn't counted it yet; -1 if the chat is unknown. */
  public static int current (Tdlib tdlib, long chatId) {
    Integer count = counts.get(key(tdlib, chatId));
    if (count != null) {
      return count;
    }
    TdApi.Chat chat = tdlib.chat(chatId);
    return chat != null ? chat.unreadCount : -1;
  }

  /** Whether XGram counts the forum's badge itself (TDLib's count is replaced). */
  public static boolean isCounted (Tdlib tdlib, long chatId) {
    return counts.containsKey(key(tdlib, chatId));
  }

  /** {@code GetForumTopics} always asks the server, so a forum's badge is counted again at most this often. */
  private static final long REFRESH_DELAY_MS = 5000;
  /** Read positions of topics in forums whose badge XGram counts, by account_chat_topic. */
  private static final Map<String, Long> readIds = new ConcurrentHashMap<>();
  private static final Set<String> refreshPending = ConcurrentHashMap.newKeySet();
  private static final Map<String, Long> lastRefreshAt = new ConcurrentHashMap<>();

  /**
   * TDLib thread, {@code updateForumTopic}, before TDLib's topic cache takes it ({@code previous} is the cached topic,
   * if any): a topic read here, from a notification or on another device lowers the badge of a forum whose list isn't
   * open. Only a moved read position counts, so the refresh's own updates can't loop.
   */
  public static void onTopicUpdate (Tdlib tdlib, TdApi.UpdateForumTopic update, @Nullable TdApi.ForumTopic previous) {
    String key = key(tdlib, update.chatId);
    if (!counts.containsKey(key)) {
      return;
    }
    String topicKey = key + "_" + update.forumTopicId;
    Long known = readIds.put(topicKey, update.lastReadInboxMessageId);
    long previousRead = previous != null ? previous.lastReadInboxMessageId : known != null ? known : -1;
    if (previousRead != update.lastReadInboxMessageId) {
      long chatId = update.chatId;
      tdlib.ui().post(() -> refresh(tdlib, chatId));
    }
  }

  /**
   * Counts the forum's topics again from the server, at least {@link #REFRESH_DELAY_MS} after the call and after the
   * last count, one request for all calls in between. Skipped while a fully loaded topic list keeps the badge itself.
   */
  @UiThread
  public static void refresh (Tdlib tdlib, long chatId) {
    String key = key(tdlib, chatId);
    if (!refreshPending.add(key)) {
      return;
    }
    long now = SystemClock.uptimeMillis();
    Long last = lastRefreshAt.get(key);
    long delay = Math.max(REFRESH_DELAY_MS, last != null ? last + REFRESH_DELAY_MS - now : 0);
    tdlib.ui().postDelayed(() -> {
      refreshPending.remove(key);
      if (ForumTopicList.isFullyLoaded(tdlib, chatId) || !tdlib.isForum(chatId)) {
        return;
      }
      lastRefreshAt.put(key, SystemClock.uptimeMillis());
      tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), (topics, error) -> {
        if (topics == null) {
          return;
        }
        int sum = 0;
        for (TdApi.ForumTopic topic : topics.topics) {
          readIds.put(key + "_" + topic.info.forumTopicId, topic.lastReadInboxMessageId);
          sum += XgramForums.normalizeUnread(tdlib, topic).unreadCount;
        }
        final int unreadCount = sum;
        tdlib.ui().post(() -> {
          // A list that finished loading meanwhile knows more than these 100 topics.
          if (!ForumTopicList.isFullyLoaded(tdlib, chatId)) {
            update(tdlib, chatId, unreadCount);
          }
        });
      });
    }, delay);
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
    com.fahadali.xgram.XgramAccountData.removeKeys(readIds.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(lastRefreshAt.keySet(), prefix);
  }
}
