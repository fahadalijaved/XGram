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

import org.drinkless.tdlib.TdApi;

import java.util.Collections;
import java.util.List;

/**
 * One saved Activity item (a row of the store): a mention of you, a reply to your message, or the reactions to one of
 * your messages. Immutable: the store hands out new objects after every change.
 */
public final class XgramActivityItem {
  public static final int KIND_MENTION = 1, KIND_REPLY = 2, KIND_REACTION = 3;
  public static final int STATE_NEW = 0, STATE_READ = 1, STATE_CLEARED = 2;
  public static final int FLAG_DELETED = 1, FLAG_READ_LOCAL = 2, FLAG_ALSO_REPLY = 4, FLAG_DISCUSSION = 8,
    FLAG_REPLIES_CHAT = 16, FLAG_PRIVATE = 32;
  public static final int SOURCE_LIVE = 1, SOURCE_UNREAD_SYNC = 2, SOURCE_LOOK_BACK = 3, SOURCE_CATCH_UP = 4;

  /** Kind masks (a bit per kind) for queries and read events. */
  public static final int MASK_MENTION = 1 << KIND_MENTION, MASK_REPLY = 1 << KIND_REPLY, MASK_REACTION = 1 << KIND_REACTION,
    MASK_MENTION_GROUP = MASK_MENTION | MASK_REPLY, MASK_ALL = MASK_MENTION | MASK_REPLY | MASK_REACTION;

  /** Someone who reacted to your message: their sender (see {@link XgramActivityClassifier#encodeSender}) and reaction. */
  public static final class Reactor {
    public final long sender;
    /** {@link XgramActivityClassifier#reactionKey}: "e:<emoji>", "c:<custom emoji id>" or "p" (paid). */
    public final String reaction;
    public final boolean isBig;
    /** When it was first seen, unix seconds. */
    public final int seen;

    public Reactor (long sender, String reaction, boolean isBig, int seen) {
      this.sender = sender;
      this.reaction = reaction;
      this.isBig = isBig;
      this.seen = seen;
    }
  }

  public final int accountId;
  public final long chatId, messageId;
  public final int kind;
  public final int topicId;
  public final long albumId;
  public final long sender;
  @Nullable public final String senderName, chatTitle, topicTitle;
  public final int messageDate, eventDate;
  public final int state, flags;
  @Nullable public final String text;
  public final int mentionFrom, mentionLength;
  public final long replyChatId, replyMessageId;
  @Nullable public final String quoteText;
  public final int source;
  /** Newest first; at most 50, {@link #reactorExtra} more are only counted. */
  @NonNull public final List<Reactor> reactors;
  public final int reactorExtra;
  @NonNull public final String key;

  public XgramActivityItem (int accountId, long chatId, long messageId, int kind, int topicId, long albumId, long sender,
                            @Nullable String senderName, @Nullable String chatTitle, @Nullable String topicTitle,
                            int messageDate, int eventDate, int state, int flags, @Nullable String text,
                            int mentionFrom, int mentionLength, long replyChatId, long replyMessageId,
                            @Nullable String quoteText, int source, @Nullable List<Reactor> reactors, int reactorExtra) {
    this.accountId = accountId;
    this.chatId = chatId;
    this.messageId = messageId;
    this.kind = kind;
    this.topicId = topicId;
    this.albumId = albumId;
    this.sender = sender;
    this.senderName = senderName;
    this.chatTitle = chatTitle;
    this.topicTitle = topicTitle;
    this.messageDate = messageDate;
    this.eventDate = eventDate;
    this.state = state;
    this.flags = flags;
    this.text = text;
    this.mentionFrom = mentionFrom;
    this.mentionLength = mentionLength;
    this.replyChatId = replyChatId;
    this.replyMessageId = replyMessageId;
    this.quoteText = quoteText;
    this.source = source;
    this.reactors = reactors != null ? Collections.unmodifiableList(reactors) : Collections.emptyList();
    this.reactorExtra = Math.max(0, reactorExtra);
    this.key = keyOf(accountId, chatId, messageId, kind);
  }

  @NonNull
  public static String keyOf (int accountId, long chatId, long messageId, int kind) {
    return accountId + "_" + chatId + "_" + messageId + "_" + kind;
  }

  public boolean isNew () {
    return state == STATE_NEW;
  }

  public boolean hasFlag (int flag) {
    return (flags & flag) != 0;
  }

  public boolean isDeleted () {
    return hasFlag(FLAG_DELETED);
  }

  /** Under the Mentions filter. */
  public boolean isMention () {
    return kind == KIND_MENTION;
  }

  /** Under the Replies filter: replies, and mentions that are also replies to your message. */
  public boolean isReply () {
    return kind == KIND_REPLY || (kind == KIND_MENTION && hasFlag(FLAG_ALSO_REPLY));
  }

  public boolean isReaction () {
    return kind == KIND_REACTION;
  }

  /** The forum topic of the message, or null outside forums. */
  @Nullable
  public TdApi.MessageTopicForum topic () {
    return topicId != 0 ? new TdApi.MessageTopicForum(topicId) : null;
  }

  /** The sender as TDLib has it (decoded from {@link #sender}), or null when unknown. */
  @Nullable
  public TdApi.MessageSender senderId () {
    return XgramActivityClassifier.decodeSender(sender);
  }

  /** Number of people who reacted, the ones beyond the saved 50 included. */
  public int reactorCount () {
    return reactorSenders().size() + reactorExtra;
  }

  /** The people who reacted, newest first, each once (a person with several reactions is one of them). */
  @NonNull
  public List<Long> reactorSenders () {
    if (reactors.isEmpty()) {
      return Collections.emptyList();
    }
    java.util.LinkedHashSet<Long> senders = new java.util.LinkedHashSet<>();
    for (Reactor reactor : reactors) {
      senders.add(reactor.sender);
    }
    return new java.util.ArrayList<>(senders);
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof XgramActivityItem)) {
      return false;
    }
    XgramActivityItem other = (XgramActivityItem) o;
    return key.equals(other.key) && state == other.state && flags == other.flags && eventDate == other.eventDate &&
      reactors.size() == other.reactors.size() && reactorExtra == other.reactorExtra &&
      equalStrings(text, other.text) && equalStrings(quoteText, other.quoteText) && equalStrings(senderName, other.senderName) &&
      equalStrings(chatTitle, other.chatTitle) && equalStrings(topicTitle, other.topicTitle);
  }

  @Override
  public int hashCode () {
    return key.hashCode();
  }

  private static boolean equalStrings (@Nullable String a, @Nullable String b) {
    return a == null ? b == null : a.equals(b);
  }
}
