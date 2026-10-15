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
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.leveldb.LevelDB;

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
  /** TDLib's own count, before ghost mode lowers it: the app's chat object may already hold the lowered one. */
  private static final Map<String, Integer> rawCounts = new ConcurrentHashMap<>();

  private static String key (Tdlib tdlib, long chatId) {
    return tdlib.id() + "_" + chatId;
  }

  // Kept on disk, so a cold start shows the last counted badge instead of TDLib's stale one.

  private static final String PREFIX = "xgram_fu_";
  private static volatile boolean loaded;
  /** Loaded from disk and not counted again yet in this process. */
  private static final Set<String> restored = ConcurrentHashMap.newKeySet();
  private static final Set<String> dirty = ConcurrentHashMap.newKeySet();
  private static final java.util.concurrent.ScheduledExecutorService writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "XgramForumUnread");
    thread.setPriority(Thread.MIN_PRIORITY);
    return thread;
  });

  private static void ensureLoaded () {
    if (loaded) {
      return;
    }
    synchronized (XgramForumUnread.class) {
      if (loaded) {
        return;
      }
      for (LevelDB.Entry stored : Settings.instance().pmc().find(PREFIX)) {
        long[] values = stored.asLongArray();
        if (values == null || values.length < 2) {
          continue;
        }
        String key = stored.key().substring(PREFIX.length());
        if (counts.putIfAbsent(key, (int) values[0]) == null) {
          tdlibCounts.put(key, (int) values[1]);
          restored.add(key);
        }
      }
      loaded = true;
    }
  }

  private static void save (String key) {
    if (dirty.add(key)) {
      writer.schedule(XgramForumUnread::flush, 1, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  /** The app goes to the background: writes pending badges now. */
  public static void flushNow () {
    writer.execute(XgramForumUnread::flush);
  }

  private static void flush () {
    for (String key : dirty.toArray(new String[0])) {
      dirty.remove(key);
      Integer count = counts.get(key);
      Integer tdlibCount = tdlibCounts.get(key);
      if (count == null || tdlibCount == null) {
        Settings.instance().pmc().remove(PREFIX + key);
      } else {
        Settings.instance().pmc().putLongArray(PREFIX + key, new long[] {count, tdlibCount});
      }
    }
  }

  /**
   * TDLib thread, {@code updateNewChat}, before ghost mode: keeps TDLib's own count and, for a forum counted before,
   * puts the stored badge in its place (plus what TDLib's count changed by meanwhile). Returns whether it did.
   */
  public static boolean rewriteChat (Tdlib tdlib, TdApi.Chat chat) {
    ensureLoaded();
    String key = key(tdlib, chat.id);
    rawCounts.put(key, chat.unreadCount);
    Integer count = counts.get(key);
    if (count == null) {
      return false;
    }
    Integer previous = tdlibCounts.put(key, chat.unreadCount);
    int delta = previous != null ? chat.unreadCount - previous : 0;
    if (delta != 0) {
      count = Math.max(0, count + delta);
      counts.put(key, count);
    }
    if (delta != 0 || previous == null) {
      save(key);
    }
    chat.unreadCount = count;
    return true;
  }

  /**
   * TDLib thread, {@code updateChatReadInbox}: replaces TDLib's count of a forum with XGram's. Returns false when
   * XGram doesn't know the forum's topics yet (TDLib's count stays).
   */
  public static boolean rewrite (Tdlib tdlib, TdApi.UpdateChatReadInbox update) {
    ensureLoaded();
    String key = key(tdlib, update.chatId);
    if (!Boolean.TRUE.equals(applying.get())) {
      rawCounts.put(key, update.unreadCount);
    }
    Integer count = counts.get(key);
    if (count == null) {
      return false;
    }
    if (!tdlib.isForum(update.chatId)) {
      counts.remove(key);
      tdlibCounts.remove(key);
      restored.remove(key);
      save(key);
      return false;
    }
    if (!Boolean.TRUE.equals(applying.get())) {
      Integer previous = tdlibCounts.put(key, update.unreadCount);
      int delta = previous != null ? update.unreadCount - previous : 0;
      if (delta != 0) {
        count = Math.max(0, count + delta);
        counts.put(key, count);
      }
      if (previous == null || previous != update.unreadCount) {
        save(key);
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
      Integer raw = rawCounts.get(key);
      tdlibCounts.put(key, raw != null ? raw : chat.unreadCount);
    }
    Integer previous = counts.put(key, unreadCount);
    restored.remove(key);
    if (previous == null || previous != unreadCount) {
      save(key);
    }
    // Always: after a TDLib restart the chat holds TDLib's count again even when the sum hasn't changed.
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

  /**
   * TDLib thread: TDLib's own unread count of any chat, before XGram changes it (topic sums here, ghost mode's local
   * reads); -1 when no count was seen yet.
   */
  public static int rawCount (Tdlib tdlib, long chatId) {
    Integer raw = rawCounts.get(key(tdlib, chatId));
    return raw != null ? raw : -1;
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
    long delay = last == null && !counts.containsKey(key) ? 0 : Math.max(REFRESH_DELAY_MS, last != null ? last + REFRESH_DELAY_MS - now : 0);
    tdlib.ui().postDelayed(() -> {
      refreshPending.remove(key);
      if (ForumTopicList.isFullyLoaded(tdlib, chatId) || !tdlib.isForum(chatId)) {
        return;
      }
      long floodWait = XgramTopicFlood.waitMs(tdlib);
      if (floodWait > 0) {
        // The server asked to wait: counted once it may be asked again.
        tdlib.ui().postDelayed(() -> refresh(tdlib, chatId), floodWait);
        return;
      }
      lastRefreshAt.put(key, SystemClock.uptimeMillis());
      tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), (topics, error) -> {
        if (topics == null) {
          XgramTopicFlood.onError(tdlib, error, 0);
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
    String key = key(tdlib, chat.id);
    Integer raw = rawCounts.get(key);
    if ((raw != null ? raw : chat.unreadCount) <= 0 || !tdlib.isForum(chat.id) || !requested.add(key)) {
      return;
    }
    // Spaced out: a chat list full of unread forums would otherwise ask for all of them at once.
    long chatId = chat.id;
    tdlib.ui().postDelayed(() -> sendRequest(tdlib, chatId, key), nextSlotDelay(0));
  }

  private static final long REQUEST_SPACING_MS = 300;
  private static long nextRequestAt;

  /** Reserves the next free request slot at least {@code minDelayMs} from now; returns the delay until it. */
  private static long nextSlotDelay (long minDelayMs) {
    long now = SystemClock.uptimeMillis();
    long at;
    synchronized (XgramForumUnread.class) {
      at = Math.max(now + minDelayMs, nextRequestAt);
      nextRequestAt = at + REQUEST_SPACING_MS;
    }
    return at - now;
  }

  private static void sendRequest (Tdlib tdlib, long chatId, String key) {
    long floodWait = XgramTopicFlood.waitMs(tdlib);
    if (floodWait > 0) {
      // The server asked to wait: back in the queue after the wait, still spaced out from the others.
      tdlib.ui().postDelayed(() -> sendRequest(tdlib, chatId, key), nextSlotDelay(floodWait));
      return;
    }
    // Topics come sorted by their last message, so the unread ones are in the first page.
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), (topics, error) -> {
      if (topics == null) {
        // The key stays requested until the retry, so rebuilt rows don't ask again meanwhile.
        tdlib.ui().postDelayed(() -> {
          requested.remove(key);
          TdApi.Chat chat = tdlib.chat(chatId);
          if (chat != null) {
            requestIfNeeded(tdlib, chat);
          }
        }, XgramTopicFlood.onError(tdlib, error, 30_000));
        return;
      }
      int sum = 0;
      for (TdApi.ForumTopic topic : topics.topics) {
        sum += XgramForums.normalizeUnread(tdlib, topic).unreadCount;
      }
      update(tdlib, chatId, sum);
    });
  }

  /** TDLib thread: the client restarts and sends its chats again, so each forum is counted once more. */
  public static void onChatsReset (Tdlib tdlib) {
    com.fahadali.xgram.XgramAccountData.removeKeys(requested, tdlib.id() + "_");
  }

  public static void forgetAccount (int accountId) {
    String prefix = accountId + "_";
    com.fahadali.xgram.XgramAccountData.removeKeys(counts.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(tdlibCounts.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(requested, prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(readIds.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(lastRefreshAt.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(restored, prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(rawCounts.keySet(), prefix);
    // On the writer thread, after any write still in flight.
    writer.execute(() -> Settings.instance().pmc().removeByPrefix(PREFIX + prefix));
  }
}
