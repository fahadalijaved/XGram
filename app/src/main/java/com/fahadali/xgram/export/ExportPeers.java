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
package com.fahadali.xgram.export;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import tgx.td.ChatId;

/**
 * Names and ids of the people and chats in an export, as Telegram has them. They are fetched from TDLib on the worker
 * thread ({@code GetUser} / {@code GetChat} return fresh objects), not read from Telegram X's cache, where XGram's
 * local renames are written in: the export is meant to leave the device, so it carries the real names. The last 512
 * users and chats are kept.
 */
final class ExportPeers {
  static final String DELETED_ACCOUNT = "Deleted Account";
  /** Telegram's "Verification Codes" account (Desktop's isVerifyCodes). */
  private static final long VERIFICATION_CODES_USER_ID = 489000;
  private static final int CACHE_SIZE = 512;
  /** Desktop's PeerColorIndex map: userpic{1 + MAP[bareId % 7]}. */
  private static final int[] COLOR_MAP = {0, 7, 4, 1, 6, 3, 5};

  /** A user or a chat. Chats keep their title in {@link #firstName}. */
  static final class Peer {
    /** What tells senders apart: the user id, or the chat id. */
    final long key;
    /** Desktop's peer id: "user123", "chat123" (basic group), "channel123" (supergroup or channel). */
    @NonNull
    final String peerId;
    final long bareId;
    @NonNull
    final String firstName, lastName;
    /** Users only, without "@"; empty when there is none. */
    @NonNull
    final String username;
    final boolean isUser, isBot;
    /** A channel (not a group): Desktop's giveaway block says "subscribers" for these. */
    final boolean isBroadcast;

    Peer (long key, @NonNull String peerId, long bareId, @Nullable String firstName, @Nullable String lastName, @Nullable String username, boolean isUser, boolean isBot) {
      this(key, peerId, bareId, firstName, lastName, username, isUser, isBot, false);
    }

    Peer (long key, @NonNull String peerId, long bareId, @Nullable String firstName, @Nullable String lastName, @Nullable String username, boolean isUser, boolean isBot, boolean isBroadcast) {
      this.key = key;
      this.peerId = peerId;
      this.bareId = bareId;
      this.firstName = firstName != null ? firstName.trim() : "";
      this.lastName = lastName != null ? lastName.trim() : "";
      this.username = username != null ? username : "";
      this.isUser = isUser;
      this.isBot = isBot;
      this.isBroadcast = isBroadcast;
    }

    /** The full name, or "" for a deleted account (JSON writes null then). */
    @NonNull
    String name () {
      if (firstName.isEmpty()) {
        return lastName;
      }
      return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }

    /** The name as the HTML shows it. */
    @NonNull
    String displayName () {
      final String name = name();
      return name.isEmpty() ? DELETED_ACCOUNT : name;
    }

    /** Desktop's userpic colour, 1..8. */
    int colorClass () {
      return ExportPeers.colorClass(bareId); // qualified: the no-argument one here would hide it
    }

    /** The first letter of the first and the last name ("DA" for a deleted account). */
    @NonNull
    String initials () {
      if (firstName.isEmpty() && lastName.isEmpty()) {
        return "DA";
      }
      return firstLetter(firstName) + firstLetter(lastName);
    }
  }

  /** The exported chat itself: Desktop's dialog type, bare id and name. */
  static final class Dialog {
    /** saved_messages, replies, verification_codes, personal_chat, bot_chat, private_group, private/public_supergroup, private/public_channel. */
    @NonNull
    final String type;
    final long bareId;
    /** Desktop leaves "name" out for Saved Messages, Replies and Verification Codes. */
    final boolean omitName;
    /** JSON "name": the first name of a user (as Desktop writes it) or the title; null for a deleted account. */
    @Nullable
    final String jsonName;
    /** The HTML page header. */
    @NonNull
    final String htmlName;
    /** For the file name. */
    @NonNull
    final String title;
    final boolean isChannel;

    Dialog (@NonNull String type, long bareId, boolean omitName, @Nullable String jsonName, @NonNull String htmlName, @NonNull String title, boolean isChannel) {
      this.type = type;
      this.bareId = bareId;
      this.omitName = omitName;
      this.jsonName = jsonName;
      this.htmlName = htmlName;
      this.title = title;
      this.isChannel = isChannel;
    }
  }

  private final ExportTd td;
  private final Map<Long, Peer> users = lru(), chats = lru();
  private final Map<Integer, String> topicNames = new HashMap<>();

  ExportPeers (ExportTd td) {
    this.td = td;
  }

  private static Map<Long, Peer> lru () {
    return new LinkedHashMap<Long, Peer>(64, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry (Map.Entry<Long, Peer> eldest) {
        return size() > CACHE_SIZE;
      }
    };
  }

  @NonNull
  @WorkerThread
  Peer sender (@Nullable TdApi.MessageSender sender) {
    if (sender instanceof TdApi.MessageSenderUser) {
      return user(((TdApi.MessageSenderUser) sender).userId);
    }
    if (sender instanceof TdApi.MessageSenderChat) {
      return chat(((TdApi.MessageSenderChat) sender).chatId);
    }
    return new Peer(0, "user0", 0, null, null, null, true, false);
  }

  @NonNull
  @WorkerThread
  Peer user (long userId) {
    Peer peer = users.get(userId);
    if (peer != null) {
      return peer;
    }
    TdApi.User user = null;
    try {
      user = td.call(new TdApi.GetUser(userId));
    } catch (TdlibException e) {
      // An unknown user is shown as a deleted account
    }
    if (user == null || user.type instanceof TdApi.UserTypeDeleted) {
      peer = new Peer(userId, "user" + userId, userId, null, null, null, true, false);
    } else {
      final String username = user.usernames != null && user.usernames.activeUsernames != null && user.usernames.activeUsernames.length > 0 ? user.usernames.activeUsernames[0] : null;
      peer = new Peer(userId, "user" + userId, userId, user.firstName, user.lastName, username, true, user.type instanceof TdApi.UserTypeBot);
    }
    users.put(userId, peer);
    return peer;
  }

  @NonNull
  @WorkerThread
  Peer chat (long chatId) {
    if (ChatId.isUserChat(chatId)) {
      return user(ChatId.toUserId(chatId));
    }
    Peer peer = chats.get(chatId);
    if (peer != null) {
      return peer;
    }
    String title = null;
    boolean broadcast = false;
    try {
      final TdApi.Chat chat = td.call(new TdApi.GetChat(chatId));
      title = chat.title;
      broadcast = chat.type instanceof TdApi.ChatTypeSupergroup && ((TdApi.ChatTypeSupergroup) chat.type).isChannel;
    } catch (TdlibException e) {
      // An unknown chat has no name
    }
    peer = new Peer(chatId, peerIdOf(chatId), bareIdOf(chatId), title, null, null, false, false, broadcast);
    chats.put(chatId, peer);
    return peer;
  }

  /**
   * The name of a forum topic of {@code chatId} as Telegram has it, "" when it can't be read (a deleted topic). Kept
   * for the whole export: there are only so many topics.
   */
  @NonNull
  @WorkerThread
  String topicName (long chatId, int forumTopicId) {
    final String cached = topicNames.get(forumTopicId);
    if (cached != null) {
      return cached;
    }
    String name = "";
    try {
      final TdApi.ForumTopic topic = td.call(new TdApi.GetForumTopic(chatId, forumTopicId));
      if (topic.info != null && topic.info.name != null) {
        name = topic.info.name;
      }
    } catch (TdlibException e) {
      // A deleted topic: no name
    }
    topicNames.put(forumTopicId, name);
    return name;
  }

  /** The original sender of a forward, or null for a hidden user. */
  @Nullable
  @WorkerThread
  Peer origin (TdApi.MessageOrigin origin) {
    switch (origin.getConstructor()) {
      case TdApi.MessageOriginUser.CONSTRUCTOR:
        return user(((TdApi.MessageOriginUser) origin).senderUserId);
      case TdApi.MessageOriginChat.CONSTRUCTOR:
        return chat(((TdApi.MessageOriginChat) origin).senderChatId);
      case TdApi.MessageOriginChannel.CONSTRUCTOR:
        return chat(((TdApi.MessageOriginChannel) origin).chatId);
      default:
        return null;
    }
  }

  /** Desktop's peer id string for a chat id. */
  @NonNull
  static String peerIdOf (long chatId) {
    long id = ChatId.toSupergroupId(chatId);
    if (id != 0) {
      return "channel" + id;
    }
    id = ChatId.toBasicGroupId(chatId);
    if (id != 0) {
      return "chat" + id;
    }
    return "user" + ChatId.toUserId(chatId);
  }

  static long bareIdOf (long chatId) {
    long id = ChatId.toSupergroupId(chatId);
    if (id != 0) {
      return id;
    }
    id = ChatId.toBasicGroupId(chatId);
    if (id != 0) {
      return id;
    }
    return ChatId.toUserId(chatId);
  }

  static int colorClass (long bareId) {
    return 1 + COLOR_MAP[(int) Math.abs(bareId % COLOR_MAP.length)];
  }

  @NonNull
  private static String firstLetter (@NonNull String name) {
    if (name.isEmpty()) {
      return "";
    }
    return new String(Character.toChars(name.codePointAt(0)));
  }

  /** The exported chat, read fresh from TDLib (its real title). */
  @NonNull
  @WorkerThread
  Dialog dialog (long chatId) {
    final Tdlib tdlib = td.tdlib();
    final TdApi.Chat chat = td.call(new TdApi.GetChat(chatId));
    final String title = chat.title != null ? chat.title : "";
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        final long userId = chat.type instanceof TdApi.ChatTypePrivate ? ((TdApi.ChatTypePrivate) chat.type).userId : ((TdApi.ChatTypeSecret) chat.type).userId;
        if (tdlib.isSelfChat(chatId)) {
          // Desktop's single-chat export heads Saved Messages with the account's own name (DialogInfoFromUser)
          return new Dialog("saved_messages", userId, true, null, user(userId).displayName(), "Saved Messages", false);
        }
        final Peer user = user(userId);
        if (tdlib.isRepliesChat(chatId)) {
          return new Dialog("replies", userId, true, null, "Replies", "Replies", false);
        }
        if (userId == VERIFICATION_CODES_USER_ID) {
          // Desktop's VerifyCodes dialog: no "name" either
          return new Dialog("verification_codes", userId, true, null, user.displayName(), user.displayName(), false);
        }
        final String jsonName = user.firstName.isEmpty() ? null : user.firstName;
        return new Dialog(user.isBot ? "bot_chat" : "personal_chat", userId, false, jsonName, user.displayName(), user.displayName(), false);
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        final long groupId = ((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId;
        return new Dialog("private_group", groupId, false, title.isEmpty() ? null : title, title.isEmpty() ? DELETED_ACCOUNT : title, title, false);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        final TdApi.ChatTypeSupergroup type = (TdApi.ChatTypeSupergroup) chat.type;
        boolean isPublic = false;
        try {
          final TdApi.Supergroup supergroup = td.call(new TdApi.GetSupergroup(type.supergroupId));
          isPublic = supergroup.usernames != null && supergroup.usernames.activeUsernames != null && supergroup.usernames.activeUsernames.length > 0;
        } catch (TdlibException e) {
          // Treated as private
        }
        final String kind = (isPublic ? "public_" : "private_") + (type.isChannel ? "channel" : "supergroup");
        return new Dialog(kind, type.supergroupId, false, title.isEmpty() ? null : title, title.isEmpty() ? DELETED_ACCOUNT : title, title, type.isChannel);
      }
      default:
        return new Dialog("personal_chat", bareIdOf(chatId), false, title.isEmpty() ? null : title, title.isEmpty() ? DELETED_ACCOUNT : title, title, false);
    }
  }
}
