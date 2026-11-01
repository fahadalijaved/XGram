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

import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.TdlibException;

import java.io.IOException;

import tgx.td.MessageId;

/**
 * Pass 1: pages the history newest → oldest (the direction TDLib pages reliably) and writes the ids of the messages to
 * export into the index. Only the server history is read: XGram's saved deleted messages and its filters are not
 * applied, so the export is what Telegram has.
 */
final class ExportSource {
  private ExportSource () { }

  private static final int LIMIT = 100;

  @WorkerThread
  static void index (ExportTd td, ExportOptions options, ExportIndex index, ExportJob job) throws IOException {
    if (options.scope != ExportOptions.SCOPE_WHOLE) {
      throw new UnsupportedOperationException("scope " + options.scope); // topic scopes come with the options sheet
    }
    final long chatId = options.chatId;
    long from = 0;
    if (options.tillDate != 0) {
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
      job.checkCanceled();
      final TdApi.Messages page = td.call(new TdApi.GetChatHistory(chatId, from, 0, LIMIT, false));
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
}
