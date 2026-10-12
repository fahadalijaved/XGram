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
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.vkryl.leveldb.LevelDB;
import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * Which chats belong to which community, as far as this device has seen: from groups' full info (communityId), the
 * community's full info and "added to the community" messages (TDLib only lists a community's chats in its full
 * info, which isn't always there). Stored per account, kept in memory for the chat list.
 *
 * The chat list shows one row per community, like the official apps: its "front" chat, the member most recently
 * active in that list, drawn as the community. The other members are left out of the list.
 */
public final class XgramCommunities {
  private XgramCommunities () { }

  public interface Listener {
    /**
     * A chat joined or left a community (chatId), or merging was switched (chatId 0): lists check that community
     * again, and show the chat if it's no longer hidden.
     */
    void onCommunityMembersChanged (Tdlib tdlib, long communityId, long chatId);
  }

  private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
  /** account id → (chat id → community id) */
  private static final Map<Integer, Map<Long, Long>> memberships = new ConcurrentHashMap<>();

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private static String prefix (Tdlib tdlib) {
    return "xgram_cm_" + tdlib.id() + "_";
  }

  private static Map<Long, Long> map (Tdlib tdlib) {
    Map<Long, Long> map = memberships.get(tdlib.id());
    if (map == null) {
      map = new ConcurrentHashMap<>();
      String prefix = prefix(tdlib);
      for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
        try {
          long communityId = entry.asLong();
          if (communityId != 0) {
            map.put(Long.parseLong(entry.key().substring(prefix.length())), communityId);
          }
        } catch (Throwable ignored) { }
      }
      Map<Long, Long> existing = memberships.putIfAbsent(tdlib.id(), map);
      if (existing != null) {
        map = existing;
      }
    }
    return map;
  }

  public static void remember (Tdlib tdlib, long chatId, long communityId) {
    if (chatId == 0) {
      return;
    }
    Map<Long, Long> map = map(tdlib);
    Long old = map.get(chatId);
    long oldId = old != null ? old : 0;
    if (oldId == communityId) {
      return;
    }
    String key = prefix(tdlib) + chatId;
    if (communityId == 0) {
      map.remove(chatId);
      Settings.instance().remove(key);
    } else {
      map.put(chatId, communityId);
      Settings.instance().putLong(key, communityId);
    }
    for (Listener listener : listeners) {
      if (oldId != 0) {
        listener.onCommunityMembersChanged(tdlib, oldId, chatId);
      }
      if (communityId != 0) {
        listener.onCommunityMembersChanged(tdlib, communityId, chatId);
      }
    }
  }

  /** Merging was switched on or off: every community is checked again. */
  public static void notifyAllChanged (Tdlib tdlib) {
    List<Long> communities = new ArrayList<>();
    for (Long id : map(tdlib).values()) {
      if (!communities.contains(id)) {
        communities.add(id);
      }
    }
    for (Listener listener : listeners) {
      for (long id : communities) {
        listener.onCommunityMembersChanged(tdlib, id, 0);
      }
    }
  }

  public static long communityOf (Tdlib tdlib, long chatId) {
    Long id = map(tdlib).get(chatId);
    return id != null ? id : 0;
  }

  /** Chats known to be in the community. */
  public static List<Long> chatsOf (Tdlib tdlib, long communityId) {
    List<Long> chats = new ArrayList<>();
    for (Map.Entry<Long, Long> entry : map(tdlib).entrySet()) {
      if (entry.getValue() == communityId) {
        chats.add(entry.getKey());
      }
    }
    return chats;
  }

  // Merging in the chat list

  public static boolean isMergeEnabled () {
    return com.fahadali.xgram.XgramSettings.instance().mergeCommunities();
  }

  /**
   * The member of the community most recently active in this list (pinned ones first), or 0 when fewer than two
   * members are in the list: then nothing is merged.
   */
  public static long frontChat (Tdlib tdlib, long communityId, TdApi.ChatList chatList) {
    if (communityId == 0 || !isMergeEnabled()) {
      return 0;
    }
    long bestId = 0;
    boolean bestPinned = false;
    long bestOrder = 0;
    int present = 0;
    for (Map.Entry<Long, Long> entry : map(tdlib).entrySet()) {
      if (entry.getValue() != communityId) {
        continue;
      }
      TdApi.Chat chat = tdlib.chat(entry.getKey());
      TdApi.ChatPosition position = chat != null ? ChatPosition.findPosition(chat, chatList) : null;
      if (position == null || position.order == 0) {
        continue;
      }
      present++;
      if (bestId == 0 || (position.isPinned && !bestPinned) || (position.isPinned == bestPinned && position.order > bestOrder)) {
        bestId = chat.id;
        bestPinned = position.isPinned;
        bestOrder = position.order;
      }
    }
    return present >= 2 ? bestId : 0;
  }

  /** Whether the chat is left out of the list, its community being shown by another member. */
  public static boolean isHidden (Tdlib tdlib, TdApi.Chat chat, TdApi.ChatList chatList) {
    long communityId = communityOf(tdlib, chat.id);
    if (communityId == 0) {
      return false;
    }
    long front = frontChat(tdlib, communityId, chatList);
    return front != 0 && front != chat.id;
  }

  /** The community this row stands for, if the chat is the front of a merged community. */
  public static long mergedCommunity (Tdlib tdlib, long chatId, TdApi.ChatList chatList) {
    long communityId = communityOf(tdlib, chatId);
    return communityId != 0 && frontChat(tdlib, communityId, chatList) == chatId ? communityId : 0;
  }

  /** Members of a merged community present in the list, the front first. */
  public static List<TdApi.Chat> presentMembers (Tdlib tdlib, long communityId, TdApi.ChatList chatList) {
    List<TdApi.Chat> chats = new ArrayList<>();
    for (Map.Entry<Long, Long> entry : map(tdlib).entrySet()) {
      if (entry.getValue() != communityId) {
        continue;
      }
      TdApi.Chat chat = tdlib.chat(entry.getKey());
      TdApi.ChatPosition position = chat != null ? ChatPosition.findPosition(chat, chatList) : null;
      if (position != null && position.order != 0) {
        chats.add(chat);
      }
    }
    return chats;
  }

  // Sources of membership

  /** TdlibCache, on every supergroup full info. */
  public static void onSupergroupFull (Tdlib tdlib, long supergroupId, @Nullable TdApi.SupergroupFullInfo full) {
    if (full != null) {
      remember(tdlib, ChatId.fromSupergroupId(supergroupId), full.communityId);
    }
  }

  /** A message: "added to / joined from the community" tells which community the chat is in. */
  public static void onMessage (Tdlib tdlib, @Nullable TdApi.Message message) {
    if (message == null || message.content == null) {
      return;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageChatAddedToCommunity.CONSTRUCTOR:
        remember(tdlib, message.chatId, ((TdApi.MessageChatAddedToCommunity) message.content).communityId);
        break;
      case TdApi.MessageChatJoinFromCommunity.CONSTRUCTOR:
        remember(tdlib, message.chatId, ((TdApi.MessageChatJoinFromCommunity) message.content).communityId);
        break;
      case TdApi.MessageChatRemovedFromCommunity.CONSTRUCTOR:
        remember(tdlib, message.chatId, 0);
        break;
    }
  }
}
