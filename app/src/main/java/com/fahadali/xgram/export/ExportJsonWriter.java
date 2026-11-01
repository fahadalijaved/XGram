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

import android.util.JsonWriter;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tgx.td.MessageId;

/**
 * result.json in Telegram Desktop's format (export_output_json.cpp), streamed into a spool file that goes into the
 * .zip last: one-space indents, keys in Desktop's order, empty keys left out, "text" and "text_entities" always there,
 * local ISO dates without an offset and "*_unixtime" as strings, peer ids as "user…", "chat…", "channel…".
 */
final class ExportJsonWriter implements Closeable {
  private final Writer out;
  private final JsonWriter json;
  private final ExportPeers peers;
  private final long chatId;
  private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
  private boolean finished;

  ExportJsonWriter (File spool, ExportPeers peers, long chatId) throws IOException {
    this.out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(spool), StandardCharsets.UTF_8), 64 * 1024);
    this.json = new JsonWriter(out);
    this.json.setIndent(" ");
    this.peers = peers;
    this.chatId = chatId;
  }

  /** The chat's keys (writeDialogStart) and the start of "messages". */
  void begin (ExportPeers.Dialog dialog) throws IOException {
    json.beginObject();
    if (!dialog.omitName) {
      json.name("name");
      if (dialog.jsonName != null) {
        json.value(dialog.jsonName);
      } else {
        json.nullValue();
      }
    }
    json.name("type").value(dialog.type);
    json.name("id").value(dialog.bareId);
    json.name("messages");
    json.beginArray();
  }

  @WorkerThread
  void writeItems (List<ExportJob.Item> items) throws IOException {
    for (ExportJob.Item item : items) {
      writeItem(item);
    }
  }

  private void writeItem (ExportJob.Item item) throws IOException {
    final TdApi.Message message = item.message;
    json.beginObject();
    json.name("id").value(item.serverId);
    if (item.kind == ExportJob.Item.KIND_UNSUPPORTED) {
      json.name("type").value("unsupported");
      json.endObject();
      return;
    }
    final boolean service = item.kind == ExportJob.Item.KIND_SERVICE;
    json.name("type").value(service ? "service" : "message");
    json.name("date").value(isoDate.format(new Date(message.date * 1000L)));
    json.name("date_unixtime").value(String.valueOf(message.date));
    if (message.editDate != 0) {
      json.name("edited").value(isoDate.format(new Date(message.editDate * 1000L)));
      json.name("edited_unixtime").value(String.valueOf(message.editDate));
    }
    final ExportPeers.Peer from = peers.sender(message.senderId);
    if (service) {
      writePeer("actor", from);
      if (!item.fallback.isEmpty()) {
        json.name("information_text").value(item.fallback);
      }
    } else {
      writePeer("from", from);
      if (message.authorSignature != null && !message.authorSignature.isEmpty()) {
        json.name("author").value(message.authorSignature);
      }
      writeForward(message.forwardInfo);
      writeReply(message.replyTo);
      if (message.viaBotUserId != 0) {
        final ExportPeers.Peer bot = peers.user(message.viaBotUserId);
        if (!bot.username.isEmpty()) {
          json.name("via_bot").value("@" + bot.username);
        }
      }
    }
    // Rich entities come later: the text is one plain run for now
    json.name("text").value(item.text);
    json.name("text_entities");
    json.beginArray();
    if (!item.text.isEmpty()) {
      json.beginObject();
      json.name("type").value("plain");
      json.name("text").value(item.text);
      json.endObject();
    }
    json.endArray();
    json.endObject();
  }

  /** "from"/"actor" with its "_id": the name is null for a deleted account, as Desktop's StringAllowNull. */
  private void writePeer (String key, ExportPeers.Peer peer) throws IOException {
    json.name(key);
    final String name = peer.name();
    if (name.isEmpty()) {
      json.nullValue();
    } else {
      json.value(name);
    }
    json.name(key + "_id").value(peer.peerId);
  }

  private void writeForward (@Nullable TdApi.MessageForwardInfo forward) throws IOException {
    if (forward == null || forward.origin == null) {
      return;
    }
    final ExportPeers.Peer origin = peers.origin(forward.origin);
    if (origin != null) {
      writePeer("forwarded_from", origin);
    } else if (forward.origin instanceof TdApi.MessageOriginHiddenUser) {
      final String name = ((TdApi.MessageOriginHiddenUser) forward.origin).senderName;
      if (name != null && !name.isEmpty()) {
        json.name("forwarded_from").value(name);
      }
    }
  }

  /** "reply_to_message_id", plus "reply_to_peer_id" for a reply to another chat. */
  private void writeReply (@Nullable TdApi.MessageReplyTo replyTo) throws IOException {
    if (!(replyTo instanceof TdApi.MessageReplyToMessage)) {
      return;
    }
    final TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) replyTo;
    final long serverId = reply.messageId != 0 ? MessageId.toServerMessageId(reply.messageId) : 0;
    if (serverId == 0) {
      return;
    }
    json.name("reply_to_message_id").value(serverId);
    if (reply.chatId != 0 && reply.chatId != chatId) {
      json.name("reply_to_peer_id").value(ExportPeers.peerIdOf(reply.chatId));
    }
  }

  /** Ends "messages" and the object, and closes the spool. */
  void finish () throws IOException {
    json.endArray();
    json.endObject();
    json.flush();
    finished = true;
    out.close();
  }

  /** On abort: closes the spool as it is (JsonWriter.close() would complain about the unfinished document). */
  @Override
  public void close () {
    if (!finished) {
      try {
        out.close();
      } catch (IOException ignored) { }
    }
  }
}
