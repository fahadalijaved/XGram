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
package com.fahadali.xgram.history;

import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deleted messages stay in the chat, marked as deleted (AyuGram's "save deleted messages").
 *
 * While a chat is open the message keeps its bubble with everything in it. When the chat is opened again TDLib no
 * longer has the message, so its saved text from {@link XgramHistory} is put back into the loaded history.
 * Messages you delete yourself are not kept.
 */
public final class XgramDeleted {
  private XgramDeleted () { }

  /** Deletions you started count as yours for this long, until TDLib reports them. */
  private static final long SELF_DELETE_WINDOW_MS = 60_000;

  private static final Set<String> deleted = ConcurrentHashMap.newKeySet();
  private static final Set<String> permanentlyDeleted = ConcurrentHashMap.newKeySet();
  private static final Map<String, Long> selfDeleted = new ConcurrentHashMap<>();
  private static final Map<String, Long> selfClearedChats = new ConcurrentHashMap<>();

  private static String key (int accountId, long chatId, long messageId) {
    return accountId + "_" + chatId + "_" + messageId;
  }

  public static void forgetAccount (int accountId) {
    String prefix = accountId + "_";
    com.fahadali.xgram.XgramAccountData.removeKeys(deleted, prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(permanentlyDeleted, prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(selfDeleted.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(selfClearedChats.keySet(), prefix);
  }

  private static String chatKey (int accountId, long chatId) {
    return accountId + "_" + chatId;
  }

  public static boolean isEnabled () {
    return XgramSettings.instance().historySaveDeleted();
  }

  /** Whether the message was deleted and is only kept by XGram. */
  @AnyThread
  public static boolean isDeleted (Tdlib tdlib, long chatId, long messageId) {
    return !deleted.isEmpty() && deleted.contains(key(tdlib.id(), chatId, messageId));
  }

  // Your own deletions

  /** Called before Telegram X asks TDLib to delete messages. */
  @AnyThread
  public static void noteSelfDeleted (Tdlib tdlib, long chatId, long[] messageIds) {
    long now = SystemClock.uptimeMillis();
    for (long messageId : messageIds) {
      selfDeleted.put(key(tdlib.id(), chatId, messageId), now);
      deleted.remove(key(tdlib.id(), chatId, messageId));
    }
  }

  /** Called before clearing a chat's history or deleting all messages of a sender. */
  @AnyThread
  public static void noteSelfClearedChat (Tdlib tdlib, long chatId) {
    selfClearedChats.put(chatKey(tdlib.id(), chatId), SystemClock.uptimeMillis());
  }

  @AnyThread
  public static boolean isSelfDeleted (Tdlib tdlib, long chatId, long messageId) {
    long now = SystemClock.uptimeMillis();
    Long cleared = selfClearedChats.get(chatKey(tdlib.id(), chatId));
    if (cleared != null && now - cleared < SELF_DELETE_WINDOW_MS) {
      return true;
    }
    Long time = selfDeleted.get(key(tdlib.id(), chatId, messageId));
    return time != null && now - time < SELF_DELETE_WINDOW_MS;
  }

  /** TDLib thread, before listeners: a real deletion (not a cache eviction) of messages. */
  @AnyThread
  public static void notePermanentDeletion (Tdlib tdlib, long chatId, long[] messageIds) {
    for (long messageId : messageIds) {
      if (!isSelfDeleted(tdlib, chatId, messageId)) {
        permanentlyDeleted.add(key(tdlib.id(), chatId, messageId));
      }
    }
  }

  // Open chat

  /**
   * Instead of removing messages others deleted, marks them as deleted. Returns the ids Telegram X should still
   * remove (your own deletions, cache evictions, outgoing messages).
   */
  @UiThread
  public static long[] keepInChat (MessagesManager manager, Tdlib tdlib, long chatId, long[] messageIds) {
    if (!isEnabled()) {
      return messageIds;
    }
    MessagesAdapter adapter = manager.getAdapter();
    long[] remaining = new long[messageIds.length];
    int remainingCount = 0;
    for (long messageId : messageIds) {
      String key = key(tdlib.id(), chatId, messageId);
      boolean keep = false;
      if (permanentlyDeleted.remove(key)) {
        int index = adapter.indexOfMessageContainer(messageId);
        TGMessage msg = index != -1 ? adapter.getMessage(index) : null;
        if (msg != null && !msg.isOutgoing() && !msg.isSponsoredMessage() && containsAll(messageIds, msg)) {
          keep = true;
          markDeleted(tdlib, msg);
        }
      }
      if (!keep) {
        remaining[remainingCount++] = messageId;
      }
    }
    return remainingCount == messageIds.length ? messageIds : Arrays.copyOf(remaining, remainingCount);
  }

  /** Albums are kept only when all of their messages are deleted at once. */
  private static boolean containsAll (long[] messageIds, TGMessage msg) {
    for (TdApi.Message message : msg.getAllMessages()) {
      boolean found = false;
      for (long id : messageIds) {
        if (id == message.id) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private static void markDeleted (Tdlib tdlib, TGMessage msg) {
    for (TdApi.Message message : msg.getAllMessages()) {
      deleted.add(key(tdlib.id(), message.chatId, message.id));
      // Messages loaded from history (not received while the app ran) aren't in the history yet.
      XgramHistory.saveDeleted(tdlib, message);
    }
    msg.xgramOnDeleted();
  }

  // Reopened chat

  /**
   * TDLib thread: puts saved deleted messages back into a page of history. {@code messages} is newest first, like
   * TDLib returns it. {@code includeNewer} also adds deleted messages after the newest one (the page is the chat's
   * bottom).
   */
  @AnyThread
  public static TdApi.Message[] merge (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, TdApi.Message[] messages, boolean includeNewer, long olderThanId, boolean isLocal) {
    if (!isEnabled()) {
      return messages;
    }
    // olderThanId: the page continues older than this (already shown) message, so deleted messages between the two
    // belong to this page. A page with nothing older than it is the start of the chat (TDLib includes the start
    // message itself), or the page of a chat the other side deleted completely: then everything older belongs to it.
    // Local pages are only TDLib's cache, never the start.
    boolean atStart = !isLocal && olderThanId != 0 && (messages.length == 0 || messages[messages.length - 1].id >= olderThanId);
    long minId, maxId;
    if (atStart) {
      minId = 0;
      maxId = olderThanId;
    } else if (messages.length == 0) {
      if (includeNewer && !isLocal) {
        minId = 0;
        maxId = Long.MAX_VALUE;
      } else {
        return messages;
      }
    } else {
      minId = messages[messages.length - 1].id;
      maxId = includeNewer ? Long.MAX_VALUE : olderThanId != 0 ? Math.max(olderThanId, messages[0].id) : messages[0].id;
    }
    int forumTopicId = topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) topicId).forumTopicId : -1;
    List<XgramHistory.Entry> entries = XgramHistory.instance().queryDeletedSync(tdlib.id(), chatId, minId, maxId, forumTopicId);
    if (entries.isEmpty()) {
      return messages;
    }
    if (!atStart && messages.length > 0 && entries.size() >= XgramHistory.DELETED_QUERY_LIMIT) {
      // More deleted messages than one query returns: end this page at the oldest returned one, so the next page
      // (which starts below it) picks up the rest.
      long oldestEntry = entries.get(entries.size() - 1).messageId;
      List<TdApi.Message> kept = new ArrayList<>(messages.length);
      for (TdApi.Message message : messages) {
        if (message.id > oldestEntry) {
          kept.add(message);
        }
      }
      messages = kept.toArray(new TdApi.Message[0]);
    }
    List<TdApi.Message> result = new ArrayList<>(messages.length + entries.size());
    int entryIndex = 0; // entries are newest first too
    for (TdApi.Message message : messages) {
      while (entryIndex < entries.size() && entries.get(entryIndex).messageId > message.id) {
        result.add(toMessage(tdlib, entries.get(entryIndex++)));
      }
      while (entryIndex < entries.size() && entries.get(entryIndex).messageId == message.id) {
        // TDLib still has it (e.g. deleted only from a cache): use TDLib's copy.
        entryIndex++;
      }
      result.add(message);
    }
    while (entryIndex < entries.size()) {
      // Only reachable for ids between the page's oldest message and minId, which the query excludes.
      result.add(toMessage(tdlib, entries.get(entryIndex++)));
    }
    return result.toArray(new TdApi.Message[0]);
  }

  private static TdApi.Message toMessage (Tdlib tdlib, XgramHistory.Entry entry) {
    deleted.add(key(tdlib.id(), entry.chatId, entry.messageId));
    TdApi.Message message = new TdApi.Message();
    message.id = entry.messageId;
    message.chatId = entry.chatId;
    message.senderId = entry.senderId > 0 ? new TdApi.MessageSenderUser(entry.senderId) : new TdApi.MessageSenderChat(entry.senderId != 0 ? entry.senderId : entry.chatId);
    message.date = entry.date;
    // A copy kept on this device: nothing on the server protects it (and it must not turn on FLAG_SECURE).
    message.canBeSaved = true;
    message.unreadReactions = new TdApi.UnreadReaction[0];
    message.senderTag = "";
    message.authorSignature = "";
    message.summaryLanguageCode = "";
    if (entry.topicId != 0) {
      message.topicId = new TdApi.MessageTopicForum((int) entry.topicId);
    }
    message.content = XgramMedia.buildContent(entry);
    return message;
  }
}
