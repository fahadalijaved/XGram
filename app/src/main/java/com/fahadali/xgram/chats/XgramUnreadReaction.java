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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The newest unread reaction to the user's messages in a chat or a forum topic, so the badges show that reaction (😂,
 * 👍) instead of a plain heart: one search for the newest message with an unread reaction per chat or topic, asked again
 * only when its unread reaction count changes. A custom emoji reaction (or none found) leaves the heart.
 */
public final class XgramUnreadReaction {
  private XgramUnreadReaction () { }

  private static final class Entry {
    int count;
    boolean loading;
    @Nullable String emoji;
    final List<Runnable> listeners = new ArrayList<>(2);
  }

  /** Rows are built on the UI thread and off it: entries are only touched under the class lock. */
  private static final Map<String, Entry> entries = new HashMap<>();

  private static String key (Tdlib tdlib, long chatId, int forumTopicId) {
    return tdlib.id() + ":" + chatId + ":" + forumTopicId;
  }

  /**
   * The emoji of the newest unread reaction in the chat (forumTopicId 0) or the topic, as known now, or null (none, not
   * known yet, or a custom emoji). When it may change (first ask, or {@code unreadCount} changed), a search starts and
   * {@code onChanged} runs on the UI thread once it ends with another emoji.
   */
  @Nullable
  public static synchronized String emoji (Tdlib tdlib, long chatId, int forumTopicId, int unreadCount, @Nullable Runnable onChanged) {
    final String key = key(tdlib, chatId, forumTopicId);
    if (unreadCount <= 0) {
      entries.remove(key);
      return null;
    }
    Entry entry = entries.get(key);
    if (entry == null) {
      entry = new Entry();
      entries.put(key, entry);
    } else if (entry.count == unreadCount) {
      if (entry.loading && onChanged != null && !entry.listeners.contains(onChanged)) {
        entry.listeners.add(onChanged);
      }
      return entry.emoji;
    }
    entry.count = unreadCount;
    if (onChanged != null && !entry.listeners.contains(onChanged)) {
      entry.listeners.add(onChanged);
    }
    if (!entry.loading) {
      entry.loading = true;
      final Entry loading = entry;
      final TdApi.MessageTopic topic = forumTopicId != 0 ? new TdApi.MessageTopicForum(forumTopicId) : null;
      tdlib.send(new TdApi.SearchChatMessages(chatId, topic, "", null, 0, 0, 1, new TdApi.SearchMessagesFilterUnreadReaction()), (found, error) -> {
        final String emoji = found != null && found.messages != null && found.messages.length > 0 ? emojiOf(found.messages[0]) : null;
        UI.post(() -> {
          final boolean changed;
          final List<Runnable> listeners;
          synchronized (XgramUnreadReaction.class) {
            loading.loading = false;
            changed = emoji == null ? loading.emoji != null : !emoji.equals(loading.emoji);
            loading.emoji = emoji;
            listeners = new ArrayList<>(loading.listeners);
            loading.listeners.clear();
          }
          if (changed) {
            for (Runnable listener : listeners) {
              listener.run();
            }
          }
        });
      });
    }
    return entry.emoji;
  }

  /** The newest of the message's unread reactions: an emoji, ⭐ for a paid one, null for a custom emoji. */
  @Nullable
  private static String emojiOf (TdApi.Message message) {
    if (message.unreadReactions == null || message.unreadReactions.length == 0) {
      return null;
    }
    TdApi.ReactionType type = message.unreadReactions[message.unreadReactions.length - 1].type;
    if (type instanceof TdApi.ReactionTypeEmoji) {
      return ((TdApi.ReactionTypeEmoji) type).emoji;
    }
    if (type instanceof TdApi.ReactionTypePaid) {
      return "⭐";
    }
    return null;
  }
}
