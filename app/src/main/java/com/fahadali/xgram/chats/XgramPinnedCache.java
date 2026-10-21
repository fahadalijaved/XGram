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
package com.fahadali.xgram.chats;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.MessageListManager;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The pinned messages bar of a chat, kept in memory: the newest pinned messages of a chat or of one forum topic (as
 * many as the bar loads first) and how many there are, for the 64 chats and topics opened last. Telegram X loaded the
 * pinned list again from nothing on every open, so the bar came in late (mid-slide, or after it) and was built again
 * each time. With an entry here a chat opens with its bar already in place; the list loads again once the chat is on
 * screen and only what changed moves.
 *
 * An entry is written by the chat's live pinned list whenever it changes and before it goes. It goes when the account
 * logs out, and when one of its messages is deleted or edited, or a message of the chat is pinned or unpinned while
 * no list of that chat writes it again. Callers don't use an entry whose newest message is dismissed, nor for a
 * restricted chat (MessagesManager checks both).
 *
 * The messages are the live list's own objects (Telegram X changes them in place as updates come); the cache only reads
 * their chat and message identifiers off the UI thread.
 */
public final class XgramPinnedCache {
  private XgramPinnedCache () { }

  /** The most messages an entry keeps: what the bar loads first (MessagesManager.openChat). */
  public static final int MAX_MESSAGES = 10;
  private static final int MAX_ENTRIES = 64;

  public static final class Entry {
    /** Newest first, never empty. */
    public final TdApi.Message[] messages;
    public final int totalCount;

    Entry (TdApi.Message[] messages, int totalCount) {
      this.messages = messages;
      this.totalCount = totalCount;
    }

    public long newestMessageId () {
      return messages[0].id;
    }
  }

  private static final class Key {
    final int accountId;
    final long chatId;
    final long topicKey;

    Key (int accountId, long chatId, long topicKey) {
      this.accountId = accountId;
      this.chatId = chatId;
      this.topicKey = topicKey;
    }

    @Override
    public boolean equals (Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Key)) {
        return false;
      }
      Key b = (Key) obj;
      return accountId == b.accountId && chatId == b.chatId && topicKey == b.topicKey;
    }

    @Override
    public int hashCode () {
      int result = accountId;
      result = 31 * result + (int) (chatId ^ (chatId >>> 32));
      result = 31 * result + (int) (topicKey ^ (topicKey >>> 32));
      return result;
    }
  }

  // Access order: the eldest entry is the one used least recently.
  private static final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<Key, Entry>(16, .75f, true) {
    @Override
    protected boolean removeEldestEntry (Map.Entry<Key, XgramPinnedCache.Entry> eldest) {
      return size() > MAX_ENTRIES;
    }
  };

  /** The forum topic of a pinned list, 0 for the whole chat (the list of any other topic kind is the chat's). */
  public static long topicKey (@Nullable TdApi.MessageTopic topicId) {
    return topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) topicId).forumTopicId : 0;
  }

  public static synchronized @Nullable Entry get (Tdlib tdlib, long chatId, long topicKey) {
    if (chatId == 0) {
      return null;
    }
    return entries.get(new Key(tdlib.id(), chatId, topicKey));
  }

  /**
   * Keeps what {@code list} holds now: its newest messages and its total count. A list with no messages, or with no
   * known count, removes the entry instead (the next open then loads as Telegram X does).
   */
  @UiThread
  public static void put (Tdlib tdlib, long chatId, long topicKey, MessageListManager list) {
    if (chatId == 0) {
      return;
    }
    final int totalCount = list.getTotalCount();
    final int count = Math.min(MAX_MESSAGES, list.getCount());
    if (count == 0 || totalCount == ListManager.COUNT_UNKNOWN) {
      remove(tdlib.id(), chatId, topicKey);
      return;
    }
    TdApi.Message[] messages = new TdApi.Message[count];
    Iterator<TdApi.Message> it = list.iterator();
    for (int i = 0; i < count && it.hasNext(); i++) {
      messages[i] = it.next();
    }
    watch(tdlib);
    synchronized (XgramPinnedCache.class) {
      entries.put(new Key(tdlib.id(), chatId, topicKey), new Entry(messages, Math.max(totalCount, count)));
    }
  }

  public static synchronized void remove (int accountId, long chatId, long topicKey) {
    entries.remove(new Key(accountId, chatId, topicKey));
  }

  /** Every entry of the chat (the chat's own and those of its topics). */
  public static synchronized void forgetChat (int accountId, long chatId) {
    Iterator<Key> it = entries.keySet().iterator();
    while (it.hasNext()) {
      Key key = it.next();
      if (key.accountId == accountId && key.chatId == chatId) {
        it.remove();
      }
    }
  }

  /** Every entry of the chat that holds one of these messages. */
  private static synchronized void forgetMessages (int accountId, long chatId, long[] messageIds) {
    Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Key, Entry> e = it.next();
      Key key = e.getKey();
      if (key.accountId != accountId || key.chatId != chatId) {
        continue;
      }
      boolean found = false;
      for (TdApi.Message message : e.getValue().messages) {
        for (long messageId : messageIds) {
          if (message.id == messageId) {
            found = true;
            break;
          }
        }
        if (found) {
          break;
        }
      }
      if (found) {
        it.remove();
      }
    }
  }

  public static synchronized void forgetAccount (int accountId) {
    Iterator<Key> it = entries.keySet().iterator();
    while (it.hasNext()) {
      if (it.next().accountId == accountId) {
        it.remove();
      }
    }
  }

  // Updates of chats that are not open (an open chat's list writes its entry again after the same update)

  private static final class Watcher implements MessageListener {
    private final int accountId;

    Watcher (int accountId) {
      this.accountId = accountId;
    }

    @Override
    public void onMessagesDeleted (long chatId, long[] messageIds) {
      forgetMessages(accountId, chatId, messageIds);
    }

    @Override
    public void onMessagePinned (long chatId, long messageId, boolean isPinned) {
      forgetChat(accountId, chatId);
    }

    @Override
    public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
      forgetMessages(accountId, chatId, new long[] {messageId});
    }

    @Override
    public void onMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
      forgetMessages(accountId, chatId, new long[] {messageId});
    }
  }

  // Held here: Telegram X keeps its listeners weakly.
  private static final WeakHashMap<Tdlib, Watcher> watchers = new WeakHashMap<>();
  private static boolean watchingAccounts;

  private static final GlobalAccountListener accountListener = new GlobalAccountListener() {
    @Override
    public void onAuthorizationStateChanged (TdlibAccount account, TdApi.AuthorizationState authorizationState, int status) {
      if (status == Tdlib.Status.UNAUTHORIZED) {
        forgetAccount(account.id); // logging out, or logged out: the slot goes to the next login
      }
    }

    @Override
    public void onActiveAccountRemoved (TdlibAccount account, int position) {
      forgetAccount(account.id);
    }
  };

  @UiThread
  private static void watch (Tdlib tdlib) {
    Watcher watcher = null;
    boolean watchAccounts = false;
    synchronized (XgramPinnedCache.class) {
      if (!watchers.containsKey(tdlib)) {
        watcher = new Watcher(tdlib.id());
        watchers.put(tdlib, watcher);
      }
      if (!watchingAccounts) {
        watchingAccounts = true;
        watchAccounts = true;
      }
    }
    if (watcher != null) {
      tdlib.listeners().subscribeForGlobalUpdates(watcher);
    }
    if (watchAccounts) {
      TdlibManager.instance().global().addAccountListener(accountListener);
    }
  }

  /** Messages of an entry as a list the pinned list can take (MessageListManager.xgramSeed). */
  public static List<TdApi.Message> messagesOf (Entry entry) {
    List<TdApi.Message> list = new ArrayList<>(entry.messages.length);
    for (TdApi.Message message : entry.messages) {
      list.add(message);
    }
    return list;
  }
}
