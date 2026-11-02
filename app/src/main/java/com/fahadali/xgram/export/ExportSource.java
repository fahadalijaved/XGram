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

import tgx.td.MessageId;

/**
 * Pass 1: pages the history newest → oldest (the direction TDLib pages reliably) and writes the ids of the messages to
 * export into the index; the chat's, or one topic's: a forum topic, a Saved Messages topic (the chats things were saved
 * from) or a channel's direct messages topic. The media the options include are counted on the way, for the progress
 * and the space check. Only the server history is read: XGram's saved deleted messages and its filters are not
 * applied, so the export is what Telegram has.
 */
final class ExportSource {
  private ExportSource () { }

  private static final int LIMIT = 100;

  @WorkerThread
  static void index (ExportTd td, ExportOptions options, ExportIndex index, ExportJob job) throws IOException {
    final long chatId = options.chatId;
    long from = 0;
    if (options.tillDate != 0) {
      // The newest message before the range's end, in the whole chat: topic histories page from any message id
      try {
        from = td.call(new TdApi.GetChatMessageByDate(chatId, options.tillDate - 1)).id;
      } catch (TdlibException e) {
        if (e.getError().code == 404) {
          return; // nothing before the end of the range
        }
        throw e;
      }
      if (from == 0) {
        return;
      }
    }
    long lastMin = Long.MAX_VALUE; // the oldest id seen: the index stays strictly newest first
    int idlePages = 0;
    while (true) {
      job.checkInterrupted();
      final TdApi.Messages page = td.call(history(options, from));
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
        if (accept(message, options)) {
          index.append(message.id);
          final ExportMedia.Plan plan = ExportMedia.plan(message, options);
          if (plan != null && plan.included()) {
            job.onMediaPlanned(plan.size);
          }
          job.onIndexed(index.count(), message.id);
        }
      }
      if (reachedStart) {
        break;
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
  }

  /** One page of the scope's history before {@code from} (0: the newest). Topic histories always ask the server. */
  @NonNull
  private static TdApi.Function<TdApi.Messages> history (ExportOptions options, long from) {
    switch (options.scope) {
      case ExportOptions.SCOPE_FORUM_TOPIC:
        return new TdApi.GetForumTopicHistory(options.chatId, (int) options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_SAVED_TOPIC:
        return new TdApi.GetSavedMessagesTopicHistory(options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_DM_TOPIC:
        return new TdApi.GetDirectMessagesChatTopicHistory(options.chatId, options.topicId, from, 0, LIMIT);
      case ExportOptions.SCOPE_WHOLE:
      default:
        return new TdApi.GetChatHistory(options.chatId, from, 0, LIMIT, false);
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
