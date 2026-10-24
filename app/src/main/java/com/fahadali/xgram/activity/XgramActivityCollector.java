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

import android.database.Cursor;
import android.util.Log;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

/**
 * Collects an account's Activity from TDLib's updates, on the TDLib thread: it classifies and posts events to the
 * store, nothing else (no blocking work, no I/O). TDLib runs every global listener in turn without catching what they
 * throw, so every callback here catches everything: a bug here must never keep an update from Telegram X itself.
 */
final class XgramActivityCollector implements MessageListener, ChatListener, NotificationSettingsListener {
  private static final String TAG = XgramActivityStore.TAG;

  private final Tdlib tdlib;

  /** Trivial: called while the Tdlib is being built (its cache does not exist yet). */
  XgramActivityCollector (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  private static XgramActivityStore store () {
    return XgramActivityStore.instance();
  }

  /**
   * Debug builds (Testing Utilities): every update callback throws first thing, inside its try, so that its catch
   * and the listeners TDLib calls after it are tested for real.
   */
  static volatile boolean debugThrow;

  private static void debugFail (String callback) {
    if (BuildConfig.DEBUG && debugThrow) {
      throw new IllegalStateException(callback); // only the switch's test failure
    }
  }

  // Which chats count

  static final int CHAT_NONE = 0, CHAT_GROUP = 1, CHAT_REPLIES = 2, CHAT_PRIVATE = 3;

  private int chatKind (@Nullable TdApi.Chat chat) {
    return chatKind(tdlib, chat);
  }

  private int baseFlags (TdApi.Chat chat) {
    return baseFlags(tdlib, chat);
  }

  private XgramActivityStore.Snapshot snapshot (TdApi.Message m, TdApi.Chat chat, int kind, int flags, int state, int source) {
    return snapshot(tdlib, m, chat, kind, flags, state, source);
  }

  /** Groups and supergroups (not channels, not channel direct messages), the Replies chat, other private chats. */
  static int chatKind (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null || chat.type == null) {
      return CHAT_NONE;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return CHAT_GROUP;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (((TdApi.ChatTypeSupergroup) chat.type).isChannel) {
          return CHAT_NONE;
        }
        TdApi.Supergroup supergroup = tdlib.chatToSupergroup(chat.id);
        if (supergroup != null && (supergroup.isChannel || supergroup.isDirectMessagesGroup)) {
          return CHAT_NONE;
        }
        return CHAT_GROUP;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
        if (tdlib.isSelfChat(chat.id)) {
          return CHAT_NONE;
        }
        return tdlib.isRepliesChat(chat.id) ? CHAT_REPLIES : CHAT_PRIVATE;
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
      default:
        return CHAT_NONE;
    }
  }

  /** A discussion group (a group linked to a channel) or a private chat, as item flags. */
  static int baseFlags (Tdlib tdlib, TdApi.Chat chat) {
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      return XgramActivityItem.FLAG_PRIVATE;
    }
    if (chat.type instanceof TdApi.ChatTypeSupergroup) {
      TdApi.Supergroup supergroup = tdlib.chatToSupergroup(chat.id);
      if (supergroup != null && supergroup.hasLinkedChat && !supergroup.isChannel) {
        return XgramActivityItem.FLAG_DISCUSSION;
      }
    }
    return 0;
  }

  @Nullable
  static String[] activeUsernames (@Nullable TdApi.User user) {
    return user != null && user.usernames != null ? user.usernames.activeUsernames : null;
  }

  // Snapshots

  static int topicOf (Tdlib tdlib, TdApi.Message m) {
    if (m.topicId instanceof TdApi.MessageTopicForum) {
      return ((TdApi.MessageTopicForum) m.topicId).forumTopicId;
    }
    if (m.topicId == null && tdlib.isForum(m.chatId)) {
      return com.fahadali.xgram.topics.XgramForums.GENERAL_TOPIC_ID; // the General topic's messages may come without one
    }
    return 0;
  }

  /**
   * The topic's name as Telegram X has it loaded, on the UI thread only: ForumTopicList is the UI thread's (its cache
   * reorders itself on every read). Elsewhere null: {@link #resolveTopicTitle} looks it up there.
   */
  @Nullable
  static String topicTitle (Tdlib tdlib, long chatId, int topicId) {
    if (topicId == 0 || !UI.inUiThread()) {
      return null;
    }
    try {
      TdApi.ForumTopic topic = com.fahadali.xgram.topics.ForumTopicList.peekTopic(tdlib, chatId, topicId);
      return topic != null && topic.info != null ? topic.info.name : null;
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Any thread, after the item was posted to the store: a forum topic's name is looked up on the UI thread and saved
   * for the item if it has none yet (the store applies it after the item, in order).
   */
  static void resolveTopicTitle (Tdlib tdlib, int accountId, long chatId, long messageId, int topicId, @Nullable String known) {
    if (topicId == 0 || known != null) {
      return;
    }
    UI.post(() -> {
      final String title = topicTitle(tdlib, chatId, topicId);
      if (title != null) {
        store().post(accountId, db -> db.execSQL("UPDATE items SET topic_title = ? WHERE account = ? AND chat = ? AND msg = ? AND topic_title IS NULL AND state != 2",
          new Object[] {title, accountId, chatId, messageId}));
      }
    });
  }

  /** Saves {@code s} (any thread), its topic's name looked up afterwards where that is allowed. */
  static void upsert (Tdlib tdlib, XgramActivityStore.Snapshot s) {
    store().upsert(s);
    resolveTopicTitle(tdlib, s.accountId, s.chatId, s.messageId, s.topicId, s.topicTitle);
  }

  /** Who wrote it: in the Replies chat, the person who replied (the message is a forwarded copy). */
  @Nullable
  static TdApi.MessageSender senderOf (Tdlib tdlib, TdApi.Message m) {
    if (tdlib.isRepliesChat(m.chatId) && m.forwardInfo != null && m.forwardInfo.origin != null) {
      switch (m.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageOriginUser.CONSTRUCTOR:
          return new TdApi.MessageSenderUser(((TdApi.MessageOriginUser) m.forwardInfo.origin).senderUserId);
        case TdApi.MessageOriginChat.CONSTRUCTOR:
          return new TdApi.MessageSenderChat(((TdApi.MessageOriginChat) m.forwardInfo.origin).senderChatId);
        case TdApi.MessageOriginChannel.CONSTRUCTOR:
          return new TdApi.MessageSenderChat(((TdApi.MessageOriginChannel) m.forwardInfo.origin).chatId);
      }
    }
    return m.senderId;
  }

  @Nullable
  static String senderName (Tdlib tdlib, TdApi.Message m, @Nullable TdApi.MessageSender sender) {
    if (sender == null && m.forwardInfo != null && m.forwardInfo.origin instanceof TdApi.MessageOriginHiddenUser) {
      return ((TdApi.MessageOriginHiddenUser) m.forwardInfo.origin).senderName;
    }
    if (sender == null) {
      return null;
    }
    try {
      String name = tdlib.senderName(sender);
      return name != null && !name.isEmpty() ? name : null;
    } catch (Throwable t) {
      return null;
    }
  }

  /** The text saved for a message: its text or caption, else what the chat list would say ("Photo"). */
  @Nullable
  static String contentText (Tdlib tdlib, TdApi.Message m) {
    String text = XgramActivityClassifier.snapshotText(XgramActivityClassifier.textOf(m.content), XgramActivityStore.TEXT_MAX);
    if (text != null) {
      return text;
    }
    try {
      if (com.fahadali.xgram.chats.XgramContentText.isDescribed(m.content)) {
        TdApi.FormattedText described = com.fahadali.xgram.chats.XgramContentText.describe(tdlib, m.content);
        String value = XgramActivityClassifier.snapshotText(described, XgramActivityStore.TEXT_MAX);
        if (value != null) {
          return value;
        }
      }
      org.thunderdog.challegram.data.ContentPreview preview = org.thunderdog.challegram.data.ContentPreview.getChatListPreview(tdlib, m.chatId, m, false);
      String value = preview != null ? preview.buildText(true) : null;
      if (value != null && !value.isEmpty()) {
        return value.length() > XgramActivityStore.TEXT_MAX ? value.substring(0, XgramActivityStore.TEXT_MAX) : value;
      }
    } catch (Throwable ignored) { }
    return null;
  }

  /** The quote of a reply: the part of your message it quotes, else your message's text. */
  @Nullable
  static String quoteOf (Tdlib tdlib, @Nullable TdApi.MessageReplyToMessage replyTo, @Nullable TdApi.Message target) {
    if (replyTo != null && replyTo.quote != null && replyTo.quote.text != null) {
      String quote = XgramActivityClassifier.snapshotText(replyTo.quote.text, XgramActivityStore.TEXT_MAX);
      if (quote != null) {
        return quote;
      }
    }
    if (target != null) {
      return contentText(tdlib, target);
    }
    if (replyTo != null && replyTo.content != null) {
      return XgramActivityClassifier.snapshotText(XgramActivityClassifier.textOf(replyTo.content), XgramActivityStore.TEXT_MAX);
    }
    return null;
  }

  /** A row for {@code m}, as it is now. */
  static XgramActivityStore.Snapshot snapshot (Tdlib tdlib, TdApi.Message m, TdApi.Chat chat, int kind, int flags, int state, int source) {
    XgramActivityStore.Snapshot s = new XgramActivityStore.Snapshot();
    s.accountId = tdlib.id();
    s.chatId = m.chatId;
    s.messageId = m.id;
    s.kind = kind;
    s.flags = flags;
    s.state = state;
    s.source = source;
    s.topicId = topicOf(tdlib, m);
    s.albumId = m.mediaAlbumId;
    TdApi.MessageSender sender = senderOf(tdlib, m);
    s.sender = XgramActivityClassifier.encodeSender(sender);
    s.senderName = senderName(tdlib, m, sender);
    s.chatTitle = tdlib.chatTitle(chat.id);
    s.topicTitle = topicTitle(tdlib, m.chatId, s.topicId);
    s.messageDate = m.date;
    s.eventDate = m.date;
    s.text = contentText(tdlib, m);
    if (kind == XgramActivityItem.KIND_MENTION && s.text != null) {
      int[] span = XgramActivityClassifier.findMentionSpan(XgramActivityClassifier.textOf(m.content), tdlib.myUserId(), activeUsernames(tdlib.myUser()));
      if (span != null && span[0] + span[1] <= s.text.length()) {
        s.mentionFrom = span[0];
        s.mentionLength = span[1];
      }
    }
    return s;
  }

  // Catch-up lookups of reply targets for messages that arrive already read: at most 10 a second, the rest dropped.

  private long bucketSecond;
  private int bucketCount;

  private synchronized boolean takeLookup () {
    long second = System.currentTimeMillis() / 1000L;
    if (second != bucketSecond) {
      bucketSecond = second;
      bucketCount = 0;
    }
    if (bucketCount >= 10) {
      return false;
    }
    bucketCount++;
    return true;
  }

  // New messages

  @Override
  public void onNewMessage (TdApi.Message m) {
    try {
      debugFail("onNewMessage");
      if (m == null || !XgramActivity.isOn()) {
        return;
      }
      collect(m);
    } catch (Throwable t) {
      Log.e(TAG, "onNewMessage", t);
    }
  }

  private void collect (TdApi.Message m) {
    if (m.sendingState != null || m.schedulingState != null || m.isOutgoing || tdlib.isSelfSender(m)) {
      return;
    }
    final TdApi.Chat chat = tdlib.chat(m.chatId);
    final int chatKind = chatKind(chat);
    if (chatKind == CHAT_NONE || chatKind == CHAT_PRIVATE) {
      return; // private chats: reactions only
    }
    final XgramActivityStore store = store();
    final int accountId = tdlib.id();
    if (chatKind == CHAT_REPLIES) {
      // Replies to your comments in discussion groups you have not joined
      boolean isNew = m.id > chat.lastReadInboxMessageId;
      XgramActivityStore.Snapshot s = snapshot(m, chat, XgramActivityItem.KIND_REPLY, XgramActivityItem.FLAG_REPLIES_CHAT,
        isNew ? XgramActivityItem.STATE_NEW : XgramActivityItem.STATE_READ, XgramActivityItem.SOURCE_LIVE);
      TdApi.MessageReplyToMessage replyTo = XgramActivityClassifier.replyToOf(m);
      s.quoteText = quoteOf(tdlib, replyTo, null);
      if (replyTo != null) {
        s.replyChatId = replyTo.chatId;
        s.replyMessageId = replyTo.messageId;
      }
      store.upsert(s);
      return;
    }
    final long me = tdlib.myUserId();
    final int classified = XgramActivityClassifier.classify(m, me, activeUsernames(tdlib.myUser()), false);
    final TdApi.MessageReplyToMessage replyTo = XgramActivityClassifier.replyToOf(m);
    final boolean sameChatReply = XgramActivityClassifier.isSameChatReply(m);
    final int base = baseFlags(chat);
    if (classified != 0) {
      final int kind = XgramActivityClassifier.kindOf(classified);
      final int flags = XgramActivityClassifier.flagsOf(classified) | base;
      XgramActivityStore.Snapshot s = snapshot(m, chat, kind, flags,
        m.containsUnreadMention ? XgramActivityItem.STATE_NEW : XgramActivityItem.STATE_READ, XgramActivityItem.SOURCE_LIVE);
      final boolean asReply = kind == XgramActivityItem.KIND_REPLY || (flags & XgramActivityItem.FLAG_ALSO_REPLY) != 0;
      if (asReply && sameChatReply && replyTo != null) {
        s.replyChatId = m.chatId;
        s.replyMessageId = replyTo.messageId;
        s.quoteText = quoteOf(tdlib, replyTo, null);
      }
      upsert(tdlib, s); // now, in order with the counter update that follows
      if (asReply && sameChatReply && replyTo != null) {
        // Is it really your message? Only what TDLib has locally (no network)
        final long chatId = m.chatId, messageId = m.id, targetId = replyTo.messageId;
        tdlib.send(new TdApi.GetMessageLocally(chatId, targetId), (target, error) -> {
          try {
            if (target != null) {
              final boolean mine = target.isOutgoing;
              final String quote = mine ? quoteOf(tdlib, replyTo, target) : null;
              store.post(accountId, db -> resolveReply(db, accountId, chatId, messageId, mine, quote));
            }
          } catch (Throwable t) {
            Log.e(TAG, "reply target", t);
          }
        });
      }
      return;
    }
    // Arrived already read (read on another device while this one was offline): a reply to your message at or
    // below the read position. Stored as Read.
    if (!m.containsUnreadMention && sameChatReply && replyTo != null && chat != null && m.id <= chat.lastReadInboxMessageId && takeLookup()) {
      final TdApi.Message message = m;
      tdlib.send(new TdApi.GetMessageLocally(m.chatId, replyTo.messageId), (target, error) -> {
        try {
          if (target != null && target.isOutgoing) {
            XgramActivityStore.Snapshot s = snapshot(message, chat, XgramActivityItem.KIND_REPLY, base, XgramActivityItem.STATE_READ, XgramActivityItem.SOURCE_CATCH_UP);
            s.replyChatId = message.chatId;
            s.replyMessageId = target.id;
            s.quoteText = quoteOf(tdlib, replyTo, target);
            upsert(tdlib, s);
          }
        } catch (Throwable t) {
          Log.e(TAG, "catch-up", t);
        }
      });
    }
  }

  /** The reply's target is known now: a reply to someone else becomes a mention, one to you gets its quote. */
  static void resolveReply (android.database.sqlite.SQLiteDatabase db, int accountId, long chatId, long messageId, boolean mine, @Nullable String quote) {
    if (mine) {
      db.execSQL("UPDATE items SET quote_text = COALESCE(?, quote_text) WHERE account = ? AND chat = ? AND msg = ? AND kind IN (1,2) AND state != 2",
        new Object[] {quote, accountId, chatId, messageId});
      return;
    }
    db.execSQL("UPDATE OR IGNORE items SET kind = 1, flags = flags & ~4, reply_chat = 0, reply_msg = 0, quote_text = NULL WHERE account = ? AND chat = ? AND msg = ? AND kind = 2",
      new Object[] {accountId, chatId, messageId});
    db.execSQL("UPDATE items SET flags = flags & ~4, reply_chat = 0, reply_msg = 0, quote_text = NULL WHERE account = ? AND chat = ? AND msg = ? AND kind = 1",
      new Object[] {accountId, chatId, messageId});
  }

  // Read elsewhere, edited, deleted

  @Override
  public void onMessageMentionRead (long chatId, long messageId) {
    try {
      debugFail("onMessageMentionRead");
      if (!XgramActivity.isOn()) {
        return;
      }
      store().markRead(tdlib.id(), chatId, messageId, XgramActivityItem.MASK_MENTION_GROUP, false);
    } catch (Throwable t) {
      Log.e(TAG, "onMessageMentionRead", t);
    }
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    try {
      debugFail("onMessagesDeleted");
      if (!XgramActivity.isOn() || messageIds == null || messageIds.length == 0 || !store().summary(tdlib.id()).knows(chatId)) {
        return;
      }
      store().markDeleted(tdlib.id(), chatId, messageIds.clone());
    } catch (Throwable t) {
      Log.e(TAG, "onMessagesDeleted", t);
    }
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    try {
      debugFail("onMessageContentChanged");
      if (!XgramActivity.isOn() || newContent == null || !store().summary(tdlib.id()).knows(chatId)) {
        return;
      }
      String text = XgramActivityClassifier.snapshotText(XgramActivityClassifier.textOf(newContent), XgramActivityStore.TEXT_MAX);
      if (text != null) {
        store().refreshText(tdlib.id(), chatId, messageId, text);
      }
    } catch (Throwable t) {
      Log.e(TAG, "onMessageContentChanged", t);
    }
  }

  // Reactions

  private static boolean hasAnyReaction (@Nullable TdApi.Message message) {
    return message != null && message.interactionInfo != null && message.interactionInfo.reactions != null &&
      message.interactionInfo.reactions.reactions != null && message.interactionInfo.reactions.reactions.length > 0;
  }

  @Override
  public void onMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
    try {
      debugFail("onMessageUnreadReactionsChanged");
      if (!XgramActivity.isOn() || !XgramActivity.reactions()) {
        return;
      }
      final TdApi.Chat chat = tdlib.chat(chatId);
      final int chatKind = chatKind(chat);
      if (chatKind == CHAT_NONE || chatKind == CHAT_REPLIES) {
        return;
      }
      final int accountId = tdlib.id();
      final XgramActivityStore store = store();
      if (unreadReactions != null && unreadReactions.length > 0) {
        XgramActivityStore.Reactions r = new XgramActivityStore.Reactions();
        r.accountId = accountId;
        r.chatId = chatId;
        r.messageId = messageId;
        r.source = XgramActivityItem.SOURCE_LIVE;
        r.chatTitle = tdlib.chatTitle(chatId);
        r.flags = baseFlags(chat);
        int now = (int) XgramActivityStore.nowSeconds();
        for (TdApi.UnreadReaction reaction : unreadReactions) {
          if (reaction == null || reaction.senderId == null) {
            continue;
          }
          r.reactors.add(new XgramActivityItem.Reactor(XgramActivityClassifier.encodeSender(reaction.senderId),
            XgramActivityClassifier.reactionKey(reaction.type), reaction.isBig, now));
        }
        store.reactions(r);
        // Your message's text for the quote, its date and topic: only what TDLib has locally
        tdlib.send(new TdApi.GetMessageLocally(chatId, messageId), (message, error) -> {
          try {
            if (message != null) {
              final String quote = contentText(tdlib, message);
              final int date = message.date;
              final int topicId = topicOf(tdlib, message);
              store.post(accountId, db -> fillReaction(db, accountId, chatId, messageId, date, quote, topicId, null));
              resolveTopicTitle(tdlib, accountId, chatId, messageId, topicId, null); // TDLib thread: the name on the UI thread
            }
          } catch (Throwable t) {
            Log.e(TAG, "reaction message", t);
          }
        });
      } else if (store.summary(accountId).knows(chatId)) {
        // Read, or every reaction taken back: the message tells which
        tdlib.send(new TdApi.GetMessageLocally(chatId, messageId), (message, error) -> {
          try {
            if (message != null && !hasAnyReaction(message)) {
              store.deleteItem(accountId, chatId, messageId, XgramActivityItem.KIND_REACTION);
            } else {
              store.markRead(accountId, chatId, messageId, XgramActivityItem.MASK_REACTION, false);
            }
          } catch (Throwable t) {
            Log.e(TAG, "reactions read", t);
          }
        });
      }
    } catch (Throwable t) {
      Log.e(TAG, "onMessageUnreadReactionsChanged", t);
    }
  }

  static void fillReaction (android.database.sqlite.SQLiteDatabase db, int accountId, long chatId, long messageId, int date, @Nullable String quote, int topicId, @Nullable String topicTitle) {
    db.execSQL("UPDATE items SET quote_text = COALESCE(quote_text, ?), msg_date = CASE WHEN msg_date = 0 THEN ? ELSE msg_date END, " +
        "topic = CASE WHEN topic = 0 THEN ? ELSE topic END, topic_title = COALESCE(topic_title, ?) " +
        "WHERE account = ? AND chat = ? AND msg = ? AND kind = 3 AND state != 2",
      new Object[] {quote, date, topicId, topicTitle, accountId, chatId, messageId});
  }

  // Counters

  @Override
  public void onChatUnreadMentionCount (long chatId, int unreadMentionCount, boolean availabilityChanged) {
    onCount(chatId, XgramActivityItem.MASK_MENTION_GROUP, unreadMentionCount);
  }

  @Override
  public void onChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged) {
    onCount(chatId, XgramActivityItem.MASK_REACTION, unreadReactionCount);
  }

  /**
   * A chat's counter changed. Compared on the store thread (after the events before it): 0 reads the chat's items
   * (in ghost mode only once TDLib's own count is 0 too: ghost mode lowers what Telegram X sees); any other difference
   * is for a paced search to settle.
   */
  private void onCount (long chatId, int kindMask, int count) {
    try {
      debugFail("onCount");
      if (kindMask == XgramActivityItem.MASK_REACTION && !XgramActivity.reactions()) {
        return;
      }
      if (!XgramActivity.isOn() || tdlib.isRepliesChat(chatId)) {
        // The Replies chat has no mention counter (it is always 0, and ghost mode's refresh repeats it): its items
        // are read by its read position alone (onChatReadInbox)
        return;
      }
      final int accountId = tdlib.id();
      final XgramActivityStore store = store();
      store.post(accountId, db -> {
        int stored = newCount(db, accountId, chatId, kindMask);
        if (count <= 0) {
          if (stored == 0) {
            return;
          }
          if (!com.fahadali.xgram.ghost.XgramLocalRead.isActive() || kindMask == XgramActivityItem.MASK_REACTION) {
            XgramActivityStore.markReadChat(db, accountId, chatId, kindMask);
            return;
          }
          tdlib.send(new TdApi.GetChat(chatId), (raw, error) -> {
            if (raw == null) {
              return;
            }
            if (raw.unreadMentionCount == 0) {
              store.markReadChat(accountId, chatId, kindMask);
            } else {
              XgramActivity.onCountMismatch(tdlib, chatId, kindMask, raw.unreadMentionCount);
            }
          });
        } else if (count != stored) {
          XgramActivity.onCountMismatch(tdlib, chatId, kindMask, count);
        }
      });
    } catch (Throwable t) {
      Log.e(TAG, "onCount", t);
    }
  }

  /** New items TDLib's counter should match: not the ones the user marked unread here (read on the server). */
  static int newCount (android.database.sqlite.SQLiteDatabase db, int accountId, long chatId, int kindMask) {
    try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM items WHERE account = ? AND chat = ? AND state = 0 AND (flags & 64) = 0 AND " + XgramActivityStore.kindsSql(kindMask),
      new String[] {String.valueOf(accountId), String.valueOf(chatId)})) {
      return c.moveToFirst() ? c.getInt(0) : 0;
    }
  }

  @Override
  public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    try {
      debugFail("onChatReadInbox");
      if (!XgramActivity.isOn() || !tdlib.isRepliesChat(chatId)) {
        return;
      }
      store().markReadUpTo(tdlib.id(), chatId, lastReadInboxMessageId, XgramActivityItem.MASK_REPLY);
    } catch (Throwable t) {
      Log.e(TAG, "onChatReadInbox", t);
    }
  }

  // Notification settings: the muted-chat rule of the badge

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    try {
      debugFail("onNotificationSettingsChanged");
      XgramActivity.onDisplayFiltersChanged(tdlib.id());
    } catch (Throwable t) {
      Log.e(TAG, "onNotificationSettingsChanged", t);
    }
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    try {
      debugFail("onScopeNotificationSettingsChanged");
      XgramActivity.onDisplayFiltersChanged(tdlib.id());
    } catch (Throwable t) {
      Log.e(TAG, "onScopeNotificationSettingsChanged", t);
    }
  }
}
