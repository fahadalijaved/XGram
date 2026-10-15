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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tgx.td.Td;

/**
 * Folder badges, the main and archive counters and the app badge come from TDLib's totals per chat list, which know
 * nothing of the counts XGram shows in the chat rows instead of TDLib's: ghost mode's local reads
 * ({@link com.fahadali.xgram.ghost.XgramLocalRead}) and the topic sums of forums
 * ({@link com.fahadali.xgram.topics.XgramForumUnread}). Chats read that way still counted in the badges. The difference
 * of each such chat is taken off TDLib's totals of the lists it is in, so the badges match the rows.
 *
 * Everything runs on the TDLib thread of the account.
 */
public final class XgramUnreadTotals {
  private XgramUnreadTotals () { }

  private static final class State {
    /** Chats whose row shows another unread count than TDLib's: chat id to TDLib's count and the shown one. */
    final Map<Long, int[]> chats = new HashMap<>();
    /** TDLib's own totals by chat list key: messages {unread, unmuted}. */
    final Map<String, int[]> messageTotals = new HashMap<>();
    /** TDLib's own totals by chat list key: chats {total, unread, unmuted, marked, marked unmuted}. */
    final Map<String, int[]> chatTotals = new HashMap<>();
    final Map<String, TdApi.ChatList> lists = new HashMap<>();
    boolean refreshPending;
  }

  private static final Map<Integer, State> states = new ConcurrentHashMap<>();

  private static State state (Tdlib tdlib) {
    State state = states.get(tdlib.id());
    if (state == null) {
      state = new State();
      State existing = states.putIfAbsent(tdlib.id(), state);
      if (existing != null) {
        state = existing;
      }
    }
    return state;
  }

  // Totals

  /** {@code updateUnreadMessageCount}: keeps TDLib's totals and puts the ones matching the rows in the update. */
  public static void rewrite (Tdlib tdlib, TdApi.UpdateUnreadMessageCount update) {
    State state = state(tdlib);
    synchronized (state) {
      String key = TD.makeChatListKey(update.chatList);
      state.lists.put(key, update.chatList);
      state.messageTotals.put(key, new int[] {update.unreadCount, update.unreadUnmutedCount});
      if (state.chats.isEmpty()) {
        return;
      }
      int[] delta = delta(tdlib, state, update.chatList);
      update.unreadCount = Math.max(0, update.unreadCount - delta[0]);
      update.unreadUnmutedCount = Math.max(0, update.unreadUnmutedCount - delta[1]);
    }
  }

  /** Same for {@code updateUnreadChatCount}. */
  public static void rewrite (Tdlib tdlib, TdApi.UpdateUnreadChatCount update) {
    State state = state(tdlib);
    synchronized (state) {
      String key = TD.makeChatListKey(update.chatList);
      state.lists.put(key, update.chatList);
      state.chatTotals.put(key, new int[] {update.totalCount, update.unreadCount, update.unreadUnmutedCount, update.markedAsUnreadCount, update.markedAsUnreadUnmutedCount});
      if (state.chats.isEmpty()) {
        return;
      }
      int[] delta = delta(tdlib, state, update.chatList);
      update.unreadCount = Math.max(0, update.unreadCount - delta[2]);
      update.unreadUnmutedCount = Math.max(0, update.unreadUnmutedCount - delta[3]);
    }
  }

  /**
   * How much TDLib's totals of the list are above what the rows show: {messages, unmuted messages, chats, unmuted
   * chats}. TDLib counts a chat marked as unread with no unread messages as one message.
   */
  private static int[] delta (Tdlib tdlib, State state, TdApi.ChatList chatList) {
    int[] delta = new int[4];
    for (Map.Entry<Long, int[]> entry : state.chats.entrySet()) {
      TdApi.Chat chat = tdlib.chat(entry.getKey());
      if (chat == null || !isInList(chat, chatList)) {
        continue;
      }
      int raw = entry.getValue()[0], shown = chat.unreadCount;
      boolean marked = chat.isMarkedAsUnread;
      int messages = messageCount(raw, marked) - messageCount(shown, marked);
      int chats = (raw > 0 || marked ? 1 : 0) - (shown > 0 || marked ? 1 : 0);
      if (messages == 0 && chats == 0) {
        continue;
      }
      delta[0] += messages;
      delta[2] += chats;
      if (tdlib.chatMuteFor(chat) <= 0) {
        delta[1] += messages;
        delta[3] += chats;
      }
    }
    return delta;
  }

  private static int messageCount (int unreadCount, boolean isMarkedAsUnread) {
    return unreadCount > 0 ? unreadCount : isMarkedAsUnread ? 1 : 0;
  }

  private static boolean isInList (TdApi.Chat chat, TdApi.ChatList chatList) {
    TdApi.ChatPosition[] positions = chat.positions;
    if (positions != null) {
      for (TdApi.ChatPosition position : positions) {
        if (position.order != 0 && Td.equalsTo(position.list, chatList)) {
          return true;
        }
      }
    }
    return false;
  }

  // Chats

  /**
   * The unread count of a chat was set (a new chat, a read update or XGram's own counts): remembers TDLib's count of
   * chats whose row shows another one, and puts the totals right when that changes.
   */
  public static void onChatCount (Tdlib tdlib, TdApi.Chat chat) {
    int raw = com.fahadali.xgram.topics.XgramForumUnread.rawCount(tdlib, chat.id);
    int shown = chat.unreadCount;
    State state = state(tdlib);
    synchronized (state) {
      int[] previous;
      if (raw < 0 || raw == shown) {
        previous = state.chats.remove(chat.id);
        if (previous == null) {
          return;
        }
      } else {
        previous = state.chats.put(chat.id, new int[] {raw, shown});
        if (previous != null && previous[0] == raw && previous[1] == shown) {
          return;
        }
      }
      scheduleRefresh(tdlib, state);
    }
  }

  /** The chat moved between lists, was marked as unread or (un)muted: the totals change if it is one of these chats. */
  public static void onChatChanged (Tdlib tdlib, long chatId) {
    State state = states.get(tdlib.id());
    if (state == null) {
      return;
    }
    synchronized (state) {
      if (state.chats.containsKey(chatId)) {
        scheduleRefresh(tdlib, state);
      }
    }
  }

  /** Default notification settings changed: these chats may be muted or unmuted now. */
  public static void onScopeChanged (Tdlib tdlib) {
    State state = states.get(tdlib.id());
    if (state == null) {
      return;
    }
    synchronized (state) {
      if (!state.chats.isEmpty()) {
        scheduleRefresh(tdlib, state);
      }
    }
  }

  /** TDLib restarts and sends its chats again. */
  public static void onChatsReset (Tdlib tdlib) {
    State state = states.get(tdlib.id());
    if (state != null) {
      synchronized (state) {
        state.chats.clear();
      }
    }
  }

  public static void forgetAccount (int accountId) {
    states.remove(accountId);
  }

  /** Caller holds the state. Once per batch of updates: TDLib's totals go through the update path again. */
  private static void scheduleRefresh (Tdlib tdlib, State state) {
    if (state.refreshPending) {
      return;
    }
    state.refreshPending = true;
    tdlib.postOnTdlibThread(() -> refresh(tdlib));
  }

  private static void refresh (Tdlib tdlib) {
    State state = states.get(tdlib.id());
    if (state == null) {
      return;
    }
    List<TdApi.Update> updates = new ArrayList<>();
    synchronized (state) {
      state.refreshPending = false;
      for (Map.Entry<String, int[]> entry : state.messageTotals.entrySet()) {
        TdApi.ChatList chatList = state.lists.get(entry.getKey());
        int[] totals = entry.getValue();
        if (chatList != null) {
          updates.add(new TdApi.UpdateUnreadMessageCount(chatList, totals[0], totals[1]));
        }
      }
      for (Map.Entry<String, int[]> entry : state.chatTotals.entrySet()) {
        TdApi.ChatList chatList = state.lists.get(entry.getKey());
        int[] totals = entry.getValue();
        if (chatList != null) {
          updates.add(new TdApi.UpdateUnreadChatCount(chatList, totals[0], totals[1], totals[2], totals[3], totals[4]));
        }
      }
    }
    for (TdApi.Update update : updates) {
      tdlib.xgramApplyUnreadTotals(update);
    }
  }
}
