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

import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tgx.td.ChatId;

/**
 * A community's chats as this device can best tell. TDLib keeps the community's chat list from the first time it was
 * loaded and never asks the server again, so it is combined with what each chat's own info says (supergroup and bot
 * full info, which TDLib does refresh) and checked again from time to time.
 */
public final class XgramCommunityData {
  private XgramCommunityData () { }

  private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;
  private static final int REFRESH_MAX_CHATS = 30;
  /** "account_community" → uptime of the last refresh */
  private static final Map<String, Long> lastRefresh = new ConcurrentHashMap<>();

  /** TDLib's list minus chats that are known to have left or moved, plus the chats known to have joined. */
  public static LinkedHashSet<Long> chatIds (Tdlib tdlib, long communityId, @Nullable TdApi.CommunityFullInfo full) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (full != null) {
      for (TdApi.CommunityChat communityChat : full.chats) {
        Long confirmed = XgramCommunities.confirmedCommunity(tdlib, communityChat.chatId);
        if (confirmed == null || confirmed == communityId) {
          ids.add(communityChat.chatId);
        }
      }
    }
    ids.addAll(XgramCommunities.chatsOf(tdlib, communityId));
    return ids;
  }

  /**
   * Asks for the full info of the community's chats (unconfirmed ones first), at most every 10 minutes per community
   * and for at most 30 chats. TDLib answers from its cache and reloads expired info in the background; a changed
   * community arrives as an update and reaches {@link XgramCommunities}, whose listeners rebuild the lists.
   */
  public static void refresh (Tdlib tdlib, long communityId, Collection<Long> chatIds) {
    String key = tdlib.id() + "_" + communityId;
    long now = SystemClock.uptimeMillis();
    Long last = lastRefresh.get(key);
    if (last != null && now - last < REFRESH_INTERVAL_MS) {
      return;
    }
    lastRefresh.put(key, now);
    List<Long> ordered = new ArrayList<>(chatIds.size());
    for (long chatId : chatIds) {
      if (!XgramCommunities.isConfirmed(tdlib, chatId)) {
        ordered.add(chatId);
      }
    }
    for (long chatId : chatIds) {
      if (XgramCommunities.isConfirmed(tdlib, chatId)) {
        ordered.add(chatId);
      }
    }
    int count = 0;
    for (long chatId : ordered) {
      if (count >= REFRESH_MAX_CHATS) {
        break;
      }
      if (ChatId.isSupergroup(chatId)) {
        tdlib.cache().supergroupFull(ChatId.toSupergroupId(chatId), true);
        count++;
      } else if (ChatId.isPrivate(chatId)) {
        tdlib.cache().userFull(ChatId.toUserId(chatId), true); // communities hold bot chats
        count++;
      }
    }
  }

  /** Whether you are in the chat: a member of a group or channel, or a bot chat that's in your chat list. */
  public static boolean isMember (Tdlib tdlib, TdApi.Chat chat) {
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      return chat.positions != null && chat.positions.length > 0;
    }
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    return status != null && TD.isMember(status);
  }

  public static boolean isAdmin (Tdlib tdlib, long communityId) {
    TdApi.Community community = tdlib.cache().community(communityId);
    if (community == null || community.status == null) {
      return false;
    }
    int constructor = community.status.getConstructor();
    return constructor == TdApi.CommunityMemberStatusCreator.CONSTRUCTOR || constructor == TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR;
  }

  /**
   * Whether the "hidden" flags mean something: only for admins, and only when not every chat is flagged (TDLib flags
   * every chat when the server leaves the flag out).
   */
  public static boolean honourHidden (@Nullable TdApi.CommunityFullInfo full, boolean admin) {
    if (!admin || full == null) {
      return false;
    }
    for (TdApi.CommunityChat communityChat : full.chats) {
      if (!communityChat.isHidden) {
        return true;
      }
    }
    return false;
  }

  public static boolean isHidden (@Nullable TdApi.CommunityFullInfo full, long chatId) {
    if (full != null) {
      for (TdApi.CommunityChat communityChat : full.chats) {
        if (communityChat.chatId == chatId) {
          return communityChat.isHidden;
        }
      }
    }
    return false;
  }
}
