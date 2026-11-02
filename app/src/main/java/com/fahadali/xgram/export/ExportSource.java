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
import androidx.annotation.WorkerThread;

import com.fahadali.xgram.chats.XgramSavedTopics;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.TdlibException;

import java.io.IOException;

import tgx.td.ChatId;
import tgx.td.MessageId;

/**
 * Pass 1: pages the history newest → oldest (the direction TDLib pages reliably) and writes the ids of the messages to
 * export into the index; the chat's, or one topic's: a forum topic, a Saved Messages topic (the chats things were saved
 * from) or a channel's direct messages topic. The media the options include are counted on the way, for the progress
 * and the space check. Only the server history is read: XGram's saved deleted messages and its filters are not
 * applied, so the export is what Telegram has.
 * <p>
 * A supergroup upgraded from a basic group: its whole history also takes in the old group's, before the supergroup's
 * messages, as Desktop does. Desktop exports those under {@code id - 1_000_000_000} ({@code kMigratedMessagesIdShift},
 * its {@code AdjustMigrateMessageIds}); here the same shift is applied to TDLib's ids ({@link #MIGRATED_SHIFT}), so the
 * old messages are negative, sort before every supergroup message in the one index, and never share an id with them.
 */
final class ExportSource {
  private ExportSource () { }

  private static final int LIMIT = 100;

  /** Desktop's -1,000,000,000 on server ids, in TDLib's id space (server id << 20). */
  static final long MIGRATED_SHIFT = 1_000_000_000L << 20;

  /**
   * Pass 1. Returns the chat id of the basic group the chat was upgraded from when its history went into the index
   * too (0: none), which pass 2 fetches the shifted ids from ({@link #migratedIds}, {@link #adoptMigrated}).
   */
  @WorkerThread
  static long index (ExportTd td, ExportOptions options, ExportIndex index, ExportJob job) throws IOException {
    if (indexChat(td, options, index, job, options.chatId, 0) || options.scope != ExportOptions.SCOPE_WHOLE) {
      return 0; // the date range starts in this history, or a topic: nothing older to add
    }
    final long oldChatId = migratedFromChatId(td, options.chatId);
    if (oldChatId == 0) {
      return 0;
    }
    final int count = index.count();
    try {
      indexChat(td, options, index, job, oldChatId, MIGRATED_SHIFT);
    } catch (TdlibException e) {
      if (index.count() != count || !isNoAccess(e)) {
        throw e; // failing halfway through is a failure, as in the supergroup's own history
      }
      return 0; // Telegram doesn't let this account read the old group: the supergroup's history alone
    }
    return oldChatId;
  }

  /**
   * One chat's history into the index, newest first, each id less {@code shift}. True when the date range's start was
   * reached in it, so nothing older is needed.
   */
  private static boolean indexChat (ExportTd td, ExportOptions options, ExportIndex index, ExportJob job, long chatId, long shift) throws IOException {
    long from = 0;
    if (options.tillDate != 0) {
      // The newest message before the range's end, in the whole chat: topic histories page from any message id
      try {
        from = td.call(new TdApi.GetChatMessageByDate(chatId, options.tillDate - 1)).id;
      } catch (TdlibException e) {
        if (e.getError().code == 404) {
          return false; // nothing before the end of the range here (an upgraded group's old history can still have some)
        }
        throw e;
      }
      if (from == 0) {
        return false;
      }
    }
    long lastMin = Long.MAX_VALUE; // the oldest id seen: the index stays strictly newest first
    int idlePages = 0;
    while (true) {
      job.checkInterrupted();
      final TdApi.Messages page = td.call(history(options, chatId, from));
      if (page.messages == null || page.messages.length == 0) {
        break;
      }
      boolean progressed = false, reachedStart = false;
      for (TdApi.Message message : page.messages) {
        if (message == null || message.id >= lastMin) {
          continue; // TDLib may repeat fromMessageId itself
        }
        lastMin = message.id;
        progressed = true;
        if (options.fromDate != 0 && message.date < options.fromDate) {
          reachedStart = true;
          break;
        }
        final long id = message.id - shift;
        if (shift != 0 && id >= 0) {
          continue; // an old server id of a billion or more (none in practice): it would sort among the supergroup's
        }
        if (accept(message, options)) {
          index.append(id);
          final ExportMedia.Plan plan = ExportMedia.plan(message, options);
          if (plan != null && plan.included()) {
            job.onMediaPlanned(plan.size);
          }
          job.onIndexed(index.count(), id);
        }
      }
      if (reachedStart) {
        return true;
      }
      if (!progressed) {
        // A page can come back with nothing new (the first one often holds only the last message): ask once more
        if (++idlePages >= 2) {
          break;
        }
        continue;
      }
      idlePages = 0;
      from = lastMin;
    }
    return false;
  }

  /**
   * The chat of the basic group {@code chatId} was upgraded from, 0 for none or when this account can't reach it. The
   * supergroup's full info names it (Desktop reads the same field, {@code migrated_from_chat_id}).
   */
  private static long migratedFromChatId (ExportTd td, long chatId) {
    if (!ChatId.isSupergroup(chatId)) {
      return 0;
    }
    try {
      final TdApi.SupergroupFullInfo full = td.call(new TdApi.GetSupergroupFullInfo(ChatId.toSupergroupId(chatId)));
      if (full.upgradedFromBasicGroupId == 0) {
        return 0;
      }
      return td.call(new TdApi.CreateBasicGroupChat(full.upgradedFromBasicGroupId, false)).id;
    } catch (TdlibException e) {
      if (!isNoAccess(e)) {
        throw e;
      }
      return 0;
    }
  }

  /** Telegram or TDLib saying the chat is unknown or out of reach, as opposed to a failure worth reporting. */
  private static boolean isNoAccess (TdlibException e) {
    final int code = e.getError().code;
    return code == 400 || code == 403 || code == 404;
  }

  /** How many of {@code ids} (ascending) are the old group's: they are negative, so they come first. */
  static int migratedCount (long[] ids) {
    int n = 0;
    while (n < ids.length && ids[n] < 0) {
      n++;
    }
    return n;
  }

  /** The old group's own TDLib ids of the first {@code count} of {@code ids}, to fetch them from it. */
  static long[] migratedIds (long[] ids, int count) {
    final long[] result = new long[count];
    for (int i = 0; i < count; i++) {
      result[i] = ids[i] + MIGRATED_SHIFT;
    }
    return result;
  }

  /** The id an old group's message (or a message it points to) is exported under; 0 stays 0. */
  static long shift (long messageId) {
    return messageId != 0 ? messageId - MIGRATED_SHIFT : 0;
  }

  /**
   * Makes an old group's message, fresh from {@code GetMessages} (nothing else holds it), one of the export's: its id
   * and the ids of the old group's messages it points to (the reply, a pinned message, a game, an invoice and its
   * receipt, a checklist, a poll) are shifted, and it belongs to the exported chat. The writers' ids, page lookups and
   * links then need nothing else. Desktop shifts only the id and the reply; it leaves e.g. a pinned message's id as it
   * was, which then names a different message of the supergroup.
   */
  static void adoptMigrated (TdApi.Message message, long chatId, long oldChatId) {
    message.id = shift(message.id);
    message.chatId = chatId;
    if (message.replyTo instanceof TdApi.MessageReplyToMessage) {
      final TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
      if (reply.chatId == oldChatId || (reply.chatId == 0 && reply.origin == null)) {
        if (reply.chatId == oldChatId) {
          reply.chatId = chatId;
        }
        reply.messageId = shift(reply.messageId);
      }
    }
    final TdApi.MessageContent content = message.content;
    switch (content.getConstructor()) {
      case TdApi.MessagePinMessage.CONSTRUCTOR: {
        final TdApi.MessagePinMessage pin = (TdApi.MessagePinMessage) content;
        pin.messageId = shift(pin.messageId);
        break;
      }
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        final TdApi.MessageGameScore score = (TdApi.MessageGameScore) content;
        score.gameMessageId = shift(score.gameMessageId);
        break;
      }
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        final TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) content;
        invoice.receiptMessageId = shift(invoice.receiptMessageId);
        break;
      }
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        final TdApi.MessagePaymentSuccessful payment = (TdApi.MessagePaymentSuccessful) content;
        if (payment.invoiceChatId == oldChatId || payment.invoiceChatId == 0) {
          if (payment.invoiceChatId == oldChatId) {
            payment.invoiceChatId = chatId;
          }
          payment.invoiceMessageId = shift(payment.invoiceMessageId);
        }
        break;
      }
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR: {
        final TdApi.MessageChecklistTasksDone done = (TdApi.MessageChecklistTasksDone) content;
        done.checklistMessageId = shift(done.checklistMessageId);
        break;
      }
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR: {
        final TdApi.MessageChecklistTasksAdded added = (TdApi.MessageChecklistTasksAdded) content;
        added.checklistMessageId = shift(added.checklistMessageId);
        break;
      }
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR: {
        final TdApi.MessagePollOptionAdded added = (TdApi.MessagePollOptionAdded) content;
        added.pollMessageId = shift(added.pollMessageId);
        break;
      }
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR: {
        final TdApi.MessagePollOptionDeleted deleted = (TdApi.MessagePollOptionDeleted) content;
        deleted.pollMessageId = shift(deleted.pollMessageId);
        break;
      }
      default:
        break;
    }
  }

  /** One page of the scope's history of {@code chatId} before {@code from} (0: the newest). Topic histories always ask the server. */
  @NonNull
  private static TdApi.Function<TdApi.Messages> history (ExportOptions options, long chatId, long from) {
    switch (options.scope) {
      case ExportOptions.SCOPE_FORUM_TOPIC:
        return new TdApi.GetForumTopicHistory(options.chatId, (int) options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_SAVED_TOPIC:
        return new TdApi.GetSavedMessagesTopicHistory(options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_DM_TOPIC:
        return new TdApi.GetDirectMessagesChatTopicHistory(options.chatId, options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_WHOLE:
      default:
        return new TdApi.GetChatHistory(chatId, from, 0, LIMIT, false);
    }
  }

  /**
   * Sent, unscheduled messages in the date range with a server id. Telegram X adds local-only messages (the Telegram
   * service chat, {@code AddLocalMessage}) that have no server id: they would all be "message0" and break the page
   * lookups, and Telegram has no copy of them.
   */
  private static boolean accept (TdApi.Message message, ExportOptions options) {
    return message.sendingState == null &&
      message.schedulingState == null &&
      MessageId.toServerMessageId(message.id) != 0 &&
      options.inRange(message.date);
  }

  /**
   * The exported topic's name as Telegram has it (no local renames: senders and chats through {@code peers}), "" for
   * the whole chat or a topic that can't be read. The page header and result.json carry it.
   */
  @NonNull
  @WorkerThread
  static String topicTitle (ExportTd td, ExportPeers peers, ExportOptions options) {
    try {
      switch (options.scope) {
        case ExportOptions.SCOPE_FORUM_TOPIC: {
          final TdApi.ForumTopic topic = td.call(new TdApi.GetForumTopic(options.chatId, (int) options.topicId));
          return topic.info != null && topic.info.name != null ? topic.info.name : "";
        }
        case ExportOptions.SCOPE_DM_TOPIC: {
          final TdApi.DirectMessagesChatTopic topic = td.call(new TdApi.GetDirectMessagesChatTopic(options.chatId, options.topicId));
          return topic.senderId != null ? peers.sender(topic.senderId).displayName() : "";
        }
        case ExportOptions.SCOPE_SAVED_TOPIC: {
          // Telegram X keeps the topics it was told about; there is no request for a single one
          final TdApi.SavedMessagesTopic topic = XgramSavedTopics.find(td.tdlib(), options.topicId);
          if (topic == null || topic.type == null) {
            return "";
          }
          switch (topic.type.getConstructor()) {
            case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR:
              return "My Notes";
            case TdApi.SavedMessagesTopicTypeAuthorHidden.CONSTRUCTOR:
              return "Author Hidden";
            case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR:
              return peers.chat(((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId).displayName();
            default:
              return "";
          }
        }
        default:
          return "";
      }
    } catch (TdlibException e) {
      return "";
    }
  }
}
