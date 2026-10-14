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
package com.fahadali.xgram.ghost;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;

/**
 * Ghost mode's "clear unread counters on this device". With "don't read messages" nothing is marked read on the
 * server, so TDLib keeps reporting chats as unread. Messages viewed in ghost mode are remembered here, and the unread
 * counters TDLib reports are lowered by them before Telegram X uses them: chats look read here, the sender still
 * sees them unread. Once the server read position passes what was viewed (e.g. after sending a message), the
 * entry goes away and TDLib's numbers are used again.
 */
public final class XgramLocalRead {
  private XgramLocalRead () { }

  public interface Listener {
    /** @param forumTopicId 0 for the chat itself */
    void onLocalReadChanged (Tdlib tdlib, long chatId, int forumTopicId);
  }

  private static final String PREFIX = "xgram_lr_";
  private static final int MAX_VIEWED = 5000;
  private static final long REFRESH_DELAY_MS = 250;

  private static final class Entry {
    final int accountId;
    final long chatId;
    final int forumTopicId;
    /** Last read inbox message id the server knows, from the latest update. */
    long serverReadId;
    /** Incoming messages after {@link #serverReadId} viewed in ghost mode. */
    final TreeSet<Long> viewed = new TreeSet<>();
    /**
     * Messages with an unread mention of you viewed in ghost mode. Mentions stay unread on the server after the read
     * position passes them, so they are only forgotten when TDLib reports them read.
     */
    final TreeSet<Long> mentions = new TreeSet<>();

    boolean isEmpty () {
      return viewed.isEmpty() && mentions.isEmpty();
    }

    Entry (int accountId, long chatId, int forumTopicId) {
      this.accountId = accountId;
      this.chatId = chatId;
      this.forumTopicId = forumTopicId;
    }

    String key () {
      return keyOf(accountId, chatId, forumTopicId);
    }

    long maxViewed () {
      return viewed.isEmpty() ? 0 : viewed.last();
    }

    /** Forgets messages the server already counts as read. Returns true if something was removed. */
    boolean prune (long serverReadId) {
      this.serverReadId = serverReadId;
      boolean changed = false;
      Iterator<Long> it = viewed.iterator();
      while (it.hasNext() && it.next() <= serverReadId) {
        it.remove();
        changed = true;
      }
      return changed;
    }
  }

  private static final Object lock = new Object();
  private static final Map<String, Entry> entries = new HashMap<>();
  private static boolean loaded;
  private static final ReferenceList<Listener> listeners = new ReferenceList<>();
  private static final Map<String, Runnable> pendingRefresh = new HashMap<>();

  private static String keyOf (int accountId, long chatId, int forumTopicId) {
    return PREFIX + accountId + "_" + chatId + "_" + forumTopicId;
  }

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  private static void ensureLoaded () {
    if (loaded) {
      return;
    }
    loaded = true;
    for (LevelDB.Entry stored : pmc().find(PREFIX)) {
      String[] parts = stored.key().substring(PREFIX.length()).split("_");
      long[] values = stored.asLongArray();
      if (parts.length != 3 || values == null || values.length < 2) {
        continue;
      }
      try {
        Entry entry = new Entry(Integer.parseInt(parts[0]), Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
        if (values[0] == FORMAT_V2 && values.length >= 3) {
          // {FORMAT_V2, serverReadId, viewedCount, viewed..., mentions...}
          entry.serverReadId = values[1];
          int viewedCount = (int) values[2];
          for (int i = 3; i < values.length; i++) {
            (i < 3 + viewedCount ? entry.viewed : entry.mentions).add(values[i]);
          }
        } else {
          // {serverReadId, viewed...}
          entry.serverReadId = values[0];
          for (int i = 1; i < values.length; i++) {
            entry.viewed.add(values[i]);
          }
        }
        entries.put(entry.key(), entry);
      } catch (NumberFormatException ignored) { }
    }
  }

  private static final long FORMAT_V2 = -2;

  // Writing every newly viewed message right away would rewrite the whole entry on the UI thread while scrolling:
  // changes are collected and written once a second on a background thread.
  private static final java.util.Set<String> dirty = new java.util.HashSet<>();
  private static boolean flushScheduled;
  private static final java.util.concurrent.ScheduledExecutorService writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "XgramLocalRead");
    thread.setPriority(Thread.MIN_PRIORITY);
    return thread;
  });

  /** Caller holds {@link #lock}. */
  private static void save (Entry entry) {
    if (entry.isEmpty()) {
      entries.remove(entry.key());
    }
    dirty.add(entry.key());
    if (!flushScheduled) {
      flushScheduled = true;
      writer.schedule(XgramLocalRead::flush, 1, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  /** The app goes to the background: writes pending reads now, the process may be killed before the delayed write. */
  public static void flushNow () {
    writer.execute(XgramLocalRead::flush);
  }

  private static void flush () {
    Map<String, long[]> writes = new HashMap<>();
    List<String> removals = new ArrayList<>();
    synchronized (lock) {
      flushScheduled = false;
      for (String key : dirty) {
        Entry entry = entries.get(key);
        if (entry == null || entry.isEmpty()) {
          removals.add(key);
        } else {
          writes.put(key, encode(entry));
        }
      }
      dirty.clear();
    }
    for (String key : removals) {
      pmc().remove(key);
    }
    for (Map.Entry<String, long[]> write : writes.entrySet()) {
      pmc().putLongArray(write.getKey(), write.getValue());
    }
  }

  private static long[] encode (Entry entry) {
    long[] values = new long[3 + entry.viewed.size() + entry.mentions.size()];
    values[0] = FORMAT_V2;
    values[1] = entry.serverReadId;
    values[2] = entry.viewed.size();
    int i = 3;
    for (long id : entry.viewed) {
      values[i++] = id;
    }
    for (long id : entry.mentions) {
      values[i++] = id;
    }
    return values;
  }

  public static void forgetAccount (int accountId) {
    String prefix = PREFIX + accountId + "_";
    synchronized (lock) {
      com.fahadali.xgram.XgramAccountData.removeKeys(entries.keySet(), prefix);
      com.fahadali.xgram.XgramAccountData.removeKeys(dirty, prefix);
    }
    // On the writer thread, after any write still in flight.
    writer.execute(() -> pmc().removeByPrefix(prefix));
  }

  public static boolean isActive () {
    XgramSettings settings = XgramSettings.instance();
    return settings.ghostDontRead() && settings.ghostLocalRead();
  }

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  // Recording

  /** An incoming message was on screen in ghost mode. */
  @UiThread
  public static void onMessageViewed (Tdlib tdlib, TdApi.Message message) {
    if (message.isOutgoing || !isActive()) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(message.chatId);
    if (chat == null) {
      return;
    }
    boolean chatChanged, topicChanged = false;
    int forumTopicId = message.topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) message.topicId).forumTopicId : 0;
    if (forumTopicId == 0 && message.topicId == null && tdlib.isForum(chat.id)) {
      // Messages of the General topic may come without a topic id.
      forumTopicId = com.fahadali.xgram.topics.XgramForums.GENERAL_TOPIC_ID;
    }
    synchronized (lock) {
      ensureLoaded();
      boolean isMention = message.containsUnreadMention;
      chatChanged = addViewed(tdlib.id(), chat.id, 0, chat.lastReadInboxMessageId, message.id, isMention);
      if (forumTopicId != 0) {
        TdApi.ForumTopic topic = com.fahadali.xgram.topics.ForumTopicList.peekTopic(tdlib, chat.id, forumTopicId);
        topicChanged = addViewed(tdlib.id(), chat.id, forumTopicId, topic != null ? topic.lastReadInboxMessageId : 0, message.id, isMention);
      }
    }
    if (chatChanged) {
      scheduleChatRefresh(tdlib, chat.id);
    }
    if (topicChanged) {
      notifyChanged(tdlib, chat.id, forumTopicId);
    }
  }

  private static boolean addViewed (int accountId, long chatId, int forumTopicId, long serverReadIdIfNew, long messageId, boolean isMention) {
    String key = keyOf(accountId, chatId, forumTopicId);
    Entry entry = entries.get(key);
    boolean isUnread = messageId > (entry != null ? entry.serverReadId : serverReadIdIfNew);
    if (!isUnread && !isMention) {
      return false;
    }
    if (entry == null) {
      entry = new Entry(accountId, chatId, forumTopicId);
      entry.serverReadId = serverReadIdIfNew;
      entries.put(key, entry);
    }
    boolean changed = false;
    if (isUnread && entry.viewed.add(messageId)) {
      changed = true;
      while (entry.viewed.size() > MAX_VIEWED) {
        entry.viewed.pollFirst();
      }
    }
    if (isMention && entry.mentions.add(messageId)) {
      changed = true;
      while (entry.mentions.size() > MAX_VIEWED) {
        entry.mentions.pollFirst();
      }
    }
    if (changed || entry.isEmpty()) {
      save(entry);
    }
    return changed;
  }

  // Applying

  /** TDLib thread: lowers the counters of an update for a chat with locally read messages. */
  @AnyThread
  public static void rewriteReadInbox (Tdlib tdlib, TdApi.UpdateChatReadInbox update) {
    if (!isActive()) {
      // Entries left over after ghost mode was turned off another way (settings reset or import).
      return;
    }
    synchronized (lock) {
      ensureLoaded();
      Entry entry = entries.get(keyOf(tdlib.id(), update.chatId, 0));
      if (entry == null) {
        return;
      }
      if (entry.prune(update.lastReadInboxMessageId)) {
        save(entry);
      }
      if (entry.viewed.isEmpty()) {
        return;
      }
      update.unreadCount = Math.max(0, update.unreadCount - entry.viewed.size());
      update.lastReadInboxMessageId = Math.max(update.lastReadInboxMessageId, entry.maxViewed());
    }
  }

  /** TDLib thread: same for a chat object TDLib sends whole (app start). */
  @AnyThread
  public static void rewriteChat (Tdlib tdlib, TdApi.Chat chat) {
    TdApi.UpdateChatReadInbox update = new TdApi.UpdateChatReadInbox(chat.id, chat.lastReadInboxMessageId, chat.unreadCount);
    rewriteReadInbox(tdlib, update);
    chat.lastReadInboxMessageId = update.lastReadInboxMessageId;
    chat.unreadCount = update.unreadCount;
    chat.unreadMentionCount = lowerMentions(tdlib, chat.id, 0, chat.unreadMentionCount);
  }

  /** Lowers a forum topic's unread and mention counters by its locally read messages. */
  @AnyThread
  public static void rewriteTopic (Tdlib tdlib, long chatId, TdApi.ForumTopic topic) {
    if (!isActive()) {
      return;
    }
    synchronized (lock) {
      ensureLoaded();
      Entry entry = entries.get(keyOf(tdlib.id(), chatId, topic.info.forumTopicId));
      if (entry == null) {
        return;
      }
      if (entry.prune(topic.lastReadInboxMessageId)) {
        save(entry);
      }
      // Only the counters change: the read position stays the server's, so it keeps matching updateForumTopic
      // (comparing against a rewritten one would refetch the topic on every update).
      if (!entry.viewed.isEmpty()) {
        if (topic.lastMessage != null && entry.maxViewed() >= topic.lastMessage.id) {
          topic.unreadCount = 0;
        } else {
          topic.unreadCount = Math.max(0, topic.unreadCount - entry.viewed.size());
        }
      }
      topic.unreadMentionCount = lowerMentionsLocked(entry, topic.unreadMentionCount);
    }
  }

  /**
   * The newest message of a chat or forum topic (forumTopicId 0 for the chat) viewed in ghost mode, 0 when none:
   * where it is read up to on this device, past the server's read position.
   */
  @AnyThread
  public static long viewedUpTo (Tdlib tdlib, long chatId, int forumTopicId) {
    if (!isActive()) {
      return 0;
    }
    synchronized (lock) {
      ensureLoaded();
      Entry entry = entries.get(keyOf(tdlib.id(), chatId, forumTopicId));
      return entry != null ? entry.maxViewed() : 0;
    }
  }

  /** A mention counter TDLib reports, lowered by the mentions viewed in ghost mode. */
  @AnyThread
  public static int lowerMentions (Tdlib tdlib, long chatId, int forumTopicId, int serverCount) {
    if (!isActive()) {
      return serverCount;
    }
    synchronized (lock) {
      ensureLoaded();
      Entry entry = entries.get(keyOf(tdlib.id(), chatId, forumTopicId));
      return entry != null ? lowerMentionsLocked(entry, serverCount) : serverCount;
    }
  }

  private static int lowerMentionsLocked (Entry entry, int serverCount) {
    if (entry.mentions.isEmpty()) {
      return serverCount;
    }
    if (serverCount <= 0) {
      // Nothing unread on the server any more.
      entry.mentions.clear();
      save(entry);
      return serverCount;
    }
    return Math.max(0, serverCount - entry.mentions.size());
  }

  /** TDLib thread: the server marked a mention read, so it no longer needs hiding. */
  @AnyThread
  public static void onMentionRead (Tdlib tdlib, long chatId, long messageId) {
    synchronized (lock) {
      ensureLoaded();
      List<Entry> changed = new ArrayList<>();
      for (Entry entry : entries.values()) {
        if (entry.accountId == tdlib.id() && entry.chatId == chatId && entry.mentions.remove(messageId)) {
          changed.add(entry);
        }
      }
      for (Entry entry : changed) {
        save(entry);
      }
    }
  }

  @UiThread
  private static void scheduleChatRefresh (Tdlib tdlib, long chatId) {
    String key = tdlib.id() + "_" + chatId;
    if (pendingRefresh.containsKey(key)) {
      return;
    }
    Runnable act = () -> {
      pendingRefresh.remove(key);
      refreshChat(tdlib, chatId);
    };
    pendingRefresh.put(key, act);
    tdlib.ui().postDelayed(act, REFRESH_DELAY_MS);
  }

  /**
   * Re-reads the chat's real counters from TDLib and runs them through Telegram X's normal update path, where
   * {@link #rewriteReadInbox} applies the local reads, so the chat list, badges and chat screen all update.
   */
  private static void refreshChat (Tdlib tdlib, long chatId) {
    if (isActive()) {
      tdlib.notifications().xgramRemoveChatNotifications(chatId);
    }
    tdlib.send(new TdApi.GetChat(chatId), (chat, error) -> {
      if (chat != null) {
        tdlib.xgramApplyReadInbox(chat.id, chat.lastReadInboxMessageId, chat.unreadCount);
        tdlib.xgramApplyMentionCount(chat.id, chat.unreadMentionCount);
      }
    });
    notifyChanged(tdlib, chatId, 0);
  }

  private static void notifyChanged (Tdlib tdlib, long chatId, int forumTopicId) {
    tdlib.ui().post(() -> {
      for (Listener listener : listeners) {
        listener.onLocalReadChanged(tdlib, chatId, forumTopicId);
      }
    });
  }

  /** Drops every local read and restores the server's counters, e.g. when the option is turned off. */
  public static void clearAll () {
    List<Entry> removed;
    synchronized (lock) {
      ensureLoaded();
      removed = new ArrayList<>(entries.values());
      entries.clear();
      dirty.clear();
      // On the writer thread, after any write still in flight.
      writer.execute(() -> pmc().removeByPrefix(PREFIX));
    }
    for (Entry entry : removed) {
      Tdlib tdlib = findTdlib(entry.accountId);
      if (tdlib == null) {
        continue;
      }
      if (entry.forumTopicId == 0) {
        refreshChat(tdlib, entry.chatId);
      } else {
        notifyChanged(tdlib, entry.chatId, entry.forumTopicId);
      }
    }
  }

  @Nullable
  private static Tdlib findTdlib (int accountId) {
    for (TdlibAccount account : TdlibManager.instance()) {
      if (account.id == accountId) {
        return account.activeTdlib();
      }
    }
    return null;
  }
}
