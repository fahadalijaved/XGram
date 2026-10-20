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
package com.fahadali.xgram.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the screen shows of the store's items, on the UI thread: the display filters that need TDLib's state (mute)
 * and the rows (replies to the same message of yours within a day are one row).
 */
final class XgramActivityRows {
  private XgramActivityRows () { }

  /**
   * Hidden by Muted chats (off): mentions and replies only when the chat is muted and its mention notifications are
   * off too, or the forum topic is muted here; reactions whenever the chat is muted.
   */
  @UiThread
  static boolean isMutedOut (Tdlib tdlib, long chatId, int topicId, int kind) {
    if (XgramActivity.muted()) {
      return false;
    }
    try {
      if (kind == XgramActivityItem.KIND_REACTION) {
        return tdlib.chat(chatId) != null && !tdlib.chatNotificationsEnabled(chatId);
      }
      if (topicId != 0 && com.fahadali.xgram.topics.XgramTopicMute.isMuted(tdlib, chatId, topicId)) {
        return true;
      }
      return tdlib.chat(chatId) != null && !tdlib.chatNotificationsEnabled(chatId) && tdlib.notifications().areMentionsDisabled(chatId);
    } catch (Throwable t) {
      return false;
    }
  }

  /**
   * The chats of {@code chatIds} whose rows Muted chats (off) hides, for the query to leave out: {reactions, mentions
   * and replies}. Forum topics muted here are left to {@link #visible}. Null while Muted chats is on.
   */
  @UiThread
  @Nullable
  static long[][] mutedChats (Tdlib tdlib, long[] chatIds) {
    if (XgramActivity.muted() || chatIds.length == 0) {
      return null;
    }
    long[] reactions = new long[chatIds.length], mentions = new long[chatIds.length];
    int reactionCount = 0, mentionCount = 0;
    for (long chatId : chatIds) {
      try {
        if (tdlib.chat(chatId) == null || tdlib.chatNotificationsEnabled(chatId)) {
          continue;
        }
        reactions[reactionCount++] = chatId;
        if (tdlib.notifications().areMentionsDisabled(chatId)) {
          mentions[mentionCount++] = chatId;
        }
      } catch (Throwable ignored) { }
    }
    return new long[][] {java.util.Arrays.copyOf(reactions, reactionCount), java.util.Arrays.copyOf(mentions, mentionCount)};
  }

  @UiThread
  static boolean visible (Tdlib tdlib, XgramActivityItem item) {
    if (item.isReaction() && !XgramActivity.reactions()) {
      return false;
    }
    if ((item.hasFlag(XgramActivityItem.FLAG_DISCUSSION) || item.hasFlag(XgramActivityItem.FLAG_REPLIES_CHAT)) && !XgramActivity.discussion()) {
      return false;
    }
    return !isMutedOut(tdlib, item.chatId, item.topicId, item.kind);
  }

  /** One row of the list: one item, or the replies to one of your messages within a day of the newest. */
  static final class Row {
    /** Newest first. */
    @NonNull final List<XgramActivityItem> items = new ArrayList<>();

    @NonNull
    XgramActivityItem newest () {
      return items.get(0);
    }

    @NonNull
    String key () {
      return newest().key;
    }

    /**
     * The row's identity across rebuilds: its oldest item (a newer reply joining a group changes {@link #key}, not
     * this), so the list updates the row in place.
     */
    @NonNull
    String stableKey () {
      return items.get(items.size() - 1).key;
    }

    int eventDate () {
      return newest().eventDate;
    }

    int kind () {
      return newest().kind;
    }

    boolean isNew () {
      for (XgramActivityItem item : items) {
        if (item.isNew()) {
          return true;
        }
      }
      return false;
    }

    /** What a tap opens: the oldest New reply of a group (the newest when none is New). */
    @NonNull
    XgramActivityItem target () {
      for (int i = items.size() - 1; i >= 0; i--) {
        if (items.get(i).isNew()) {
          return items.get(i);
        }
      }
      return newest();
    }

    /** The people who wrote, newest first, each once. */
    @NonNull
    List<XgramActivityItem> distinctSenders () {
      List<XgramActivityItem> result = new ArrayList<>();
      for (XgramActivityItem item : items) {
        boolean seen = false;
        for (XgramActivityItem other : result) {
          if (other.sender == item.sender && (item.sender != 0 || equal(other.senderName, item.senderName))) {
            seen = true;
            break;
          }
        }
        if (!seen) {
          result.add(item);
        }
      }
      return result;
    }

    boolean contains (String key) {
      for (XgramActivityItem item : items) {
        if (item.key.equals(key)) {
          return true;
        }
      }
      return false;
    }

    @Nullable private String signature;

    /**
     * For change detection (what the row shows from the store): the items, their states, texts, names and who
     * reacted. Rows don't change once grouped, so it is made once.
     */
    @NonNull
    String signature () {
      if (signature == null) {
        StringBuilder b = new StringBuilder();
        for (XgramActivityItem item : items) {
          b.append(item.key).append(':').append(item.state).append(':').append(item.flags).append(':').append(item.eventDate)
            .append(':').append(hash(item.text)).append(':').append(hash(item.quoteText)).append(':').append(hash(item.senderName))
            .append(':').append(hash(item.chatTitle)).append(':').append(hash(item.topicTitle)).append(':').append(item.topicId)
            .append(':').append(item.mentionFrom).append(':').append(item.mentionLength).append(':').append(item.reactorExtra);
          for (XgramActivityItem.Reactor reactor : item.reactors) {
            b.append(',').append(reactor.sender).append('|').append(reactor.reaction);
          }
          b.append(';');
        }
        signature = b.toString();
      }
      return signature;
    }

    private static int hash (@Nullable String value) {
      return value != null ? value.hashCode() : 0;
    }
  }

  private static boolean equal (@Nullable String a, @Nullable String b) {
    return a == null ? b == null : a.equals(b);
  }

  /** Mentions and reactions are a row each; replies to the same message of yours within a day are one. */
  @NonNull
  static List<Row> group (@NonNull List<XgramActivityItem> items) {
    List<Row> rows = new ArrayList<>();
    Map<String, Row> openGroups = new HashMap<>();
    for (XgramActivityItem item : items) {
      if (item.kind == XgramActivityItem.KIND_REPLY && item.replyMessageId != 0 && !item.isDeleted()) {
        String groupKey = item.replyChatId + "_" + item.replyMessageId;
        Row open = openGroups.get(groupKey);
        if (open != null && XgramActivityClassifier.joinsReplyGroup(open.eventDate(), item.eventDate)) {
          open.items.add(item);
          continue;
        }
        Row row = new Row();
        row.items.add(item);
        rows.add(row);
        openGroups.put(groupKey, row);
        continue;
      }
      Row row = new Row();
      row.items.add(item);
      rows.add(row);
    }
    return rows;
  }

  /** Every item of {@code rows}, in order. */
  @NonNull
  static List<XgramActivityItem> itemsOf (@NonNull List<Row> rows) {
    if (rows.isEmpty()) {
      return Collections.emptyList();
    }
    List<XgramActivityItem> items = new ArrayList<>();
    for (Row row : rows) {
      items.addAll(row.items);
    }
    return items;
  }
}
