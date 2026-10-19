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

import android.net.Uri;

import androidx.annotation.Nullable;

import com.fahadali.xgram.history.XgramMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.vkryl.leveldb.LevelDB;
import tgx.td.Td;

/**
 * The journal of waiting sends (XgramBookmarks pattern: Telegram X's LevelDB, keys of this device only). One record per
 * item, written with a single put, so a record is always whole: written when the item starts waiting, rewritten as
 * "F" when its requests go to TDLib, removed once a result came, the composer took the text back or a draft was set.
 * A record left from an earlier launch (another boot id) is a send that never finished: {@link XgramUndoSend} makes it
 * the chat's draft. Never part of a settings backup; erased with the account (XgramAccountData).
 */
final class XgramUndoSendStore {
  private XgramUndoSendStore () { }

  static final String PREFIX = "xgram_usq_";
  /** "Your message is back" the first time the chat opens after a restore: prefix, account id, "_", chat id. */
  static final String HINT_PREFIX = "xgram_usqh_";
  /** Debug builds: the next restore of a draft fails once. Not under PREFIX: it is no record. */
  static final String DEBUG_FAIL_RESTORE = "xgram_usdbg_fail_restore";

  private static final String VERSION = "v1";
  private static final int FIELDS = 16;

  /** A record read back from the journal (or what is left of an item to give back). */
  static final class Record {
    final String key;
    final int accountId;
    final long bootId;
    final boolean flushing;
    final int kind;
    final long chatId, draftChatId, createdWall;
    @Nullable final TdApi.MessageTopic topic;
    @Nullable final TdApi.InputMessageReplyTo replyTo;
    final boolean silent;
    /** Files: their names (they can't be sent after a restart, only listed). */
    @Nullable final String[] fileNames;
    final TdApi.FormattedText text;

    Record (String key, int accountId, long bootId, boolean flushing, int kind, long chatId, long draftChatId, long createdWall,
            @Nullable TdApi.MessageTopic topic, @Nullable TdApi.InputMessageReplyTo replyTo, boolean silent,
            @Nullable String[] fileNames, TdApi.FormattedText text) {
      this.key = key;
      this.accountId = accountId;
      this.bootId = bootId;
      this.flushing = flushing;
      this.kind = kind;
      this.chatId = chatId;
      this.draftChatId = draftChatId;
      this.createdWall = createdWall;
      this.topic = topic;
      this.replyTo = replyTo;
      this.silent = silent;
      this.fileNames = fileNames;
      this.text = text;
    }
  }

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  static String key (int accountId, long itemId) {
    return PREFIX + accountId + "_" + itemId;
  }

  static String key (XgramUndoSendItem item) {
    return key(item.accountId, item.id);
  }

  static String hintKey (int accountId, long chatId) {
    return HINT_PREFIX + accountId + "_" + chatId;
  }

  /** True when the account has records (of any launch). */
  static boolean hasRecords (int accountId) {
    return pmc().findFirst(PREFIX + accountId + "_") != null;
  }

  /** Writes the record of a new item. False when it could not be written (the item then must not wait). */
  static boolean put (XgramUndoSendItem item, long bootId) {
    try {
      String key = key(item);
      String value = encode(item, bootId, false);
      pmc().putString(key, value);
      String stored = pmc().getString(key, null);
      if (!value.equals(stored)) {
        return false;
      }
      if (BuildConfig.DEBUG) {
        checkRoundTrip(item, key, stored);
      }
      return true;
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot write the journal", t);
      return false;
    }
  }

  /** The item's requests go to TDLib: after a crash its record is checked against the chat's history first. */
  static void markFlushing (XgramUndoSendItem item, long bootId) {
    try {
      pmc().putString(key(item), encode(item, bootId, true));
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot update the journal", t);
    }
  }

  /**
   * Rewrites a record as what is left to give back (a send TDLib refused): after a crash it becomes a draft as it is,
   * without the check for a message that was sent.
   */
  static void rewrite (Record record) {
    try {
      pmc().putString(record.key, encode(record.bootId, record.flushing, record.kind, record.chatId, record.draftChatId,
        record.createdWall, record.topic, record.replyTo, record.silent, record.fileNames, record.text));
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot update the journal", t);
    }
  }

  static void remove (XgramUndoSendItem item) {
    remove(key(item));
  }

  static void remove (String key) {
    try {
      pmc().remove(key);
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot update the journal", t);
    }
  }

  static void putHint (int accountId, long chatId) {
    try {
      pmc().putBoolean(hintKey(accountId, chatId), true);
    } catch (Throwable ignored) { }
  }

  /** True once: the chat has a pending "your message is back" hint, which is removed. */
  static boolean takeHint (int accountId, long chatId) {
    try {
      String key = hintKey(accountId, chatId);
      if (pmc().contains(key)) {
        pmc().remove(key);
        return true;
      }
    } catch (Throwable ignored) { }
    return false;
  }

  static boolean hasAnyHint () {
    try {
      return pmc().findFirst(HINT_PREFIX) != null;
    } catch (Throwable t) {
      return false;
    }
  }

  /** Debug builds: true once after the debug menu asked for it. */
  static boolean takeDebugFailRestore () {
    if (!BuildConfig.DEBUG) {
      return false;
    }
    try {
      if (pmc().getBoolean(DEBUG_FAIL_RESTORE, false)) {
        pmc().remove(DEBUG_FAIL_RESTORE);
        return true;
      }
    } catch (Throwable ignored) { }
    return false;
  }

  static void setDebugFailRestore () {
    if (BuildConfig.DEBUG) {
      pmc().putBoolean(DEBUG_FAIL_RESTORE, true);
    }
  }

  /** The account's records left by earlier launches, newest first, at most {@code limit}. */
  static List<Record> orphans (int accountId, long bootId, int limit) {
    String prefix = PREFIX + accountId + "_";
    List<String[]> raw = new ArrayList<>();
    try {
      for (LevelDB.Entry entry : pmc().find(prefix)) {
        raw.add(new String[] {entry.key(), entry.asString()});
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot read the journal", t);
    }
    List<Record> result = new ArrayList<>();
    for (String[] pair : raw) {
      Record record = decode(pair[0], accountId, pair[1]);
      if (record == null) {
        // Unreadable (a newer format, or damaged): left alone rather than lost.
        continue;
      }
      if (record.bootId != bootId) {
        result.add(record);
      }
    }
    Collections.sort(result, (a, b) -> Long.compare(b.createdWall, a.createdWall));
    while (result.size() > limit) {
      result.remove(result.size() - 1);
    }
    return result;
  }

  // Format: lines, the text last (it may have line breaks of its own)

  private static String encode (XgramUndoSendItem item, long bootId, boolean flushing) {
    return encode(bootId, flushing, item.kind, item.chatId, item.draftChatId, item.createdWall, item.draftTopic, item.replyTo,
      item.silent, item.fileNames, item.typed);
  }

  private static String encode (long bootId, boolean flushing, int kind, long chatId, long draftChatId, long createdWall,
                                @Nullable TdApi.MessageTopic topic, @Nullable TdApi.InputMessageReplyTo replyTo, boolean silent,
                                @Nullable String[] fileNames, @Nullable TdApi.FormattedText typed) {
    StringBuilder b = new StringBuilder();
    b.append(VERSION).append('\n');
    b.append(bootId).append('\n');
    b.append(flushing ? 'F' : 'H').append('\n');
    b.append(kindCode(kind)).append('\n');
    b.append(chatId).append('\n');
    b.append(draftChatId).append('\n');
    b.append(createdWall).append('\n');
    b.append(encodeTopic(topic)).append('\n');
    appendReply(b, replyTo);
    b.append(silent ? '1' : '0').append('\n');
    if (fileNames != null) {
      for (int i = 0; i < fileNames.length; i++) {
        if (i > 0) {
          b.append(',');
        }
        b.append(Uri.encode(fileNames[i] != null ? fileNames[i] : ""));
      }
    }
    b.append('\n');
    b.append(XgramMedia.encodeEntities(typed != null ? typed.entities : null)).append('\n');
    b.append(typed != null && typed.text != null ? typed.text : "");
    return b.toString();
  }

  private static char kindCode (int kind) {
    switch (kind) {
      case XgramUndoSendItem.KIND_VOICE: return 'V';
      case XgramUndoSendItem.KIND_VIDEO: return 'R';
      case XgramUndoSendItem.KIND_FILES: return 'D';
      default: return 'T';
    }
  }

  private static int kindOf (String code) {
    switch (code) {
      case "V": return XgramUndoSendItem.KIND_VOICE;
      case "R": return XgramUndoSendItem.KIND_VIDEO;
      case "D": return XgramUndoSendItem.KIND_FILES;
      default: return XgramUndoSendItem.KIND_TEXT;
    }
  }

  /** Four lines: the message, the quote, the checklist task, the poll option. */
  private static void appendReply (StringBuilder b, @Nullable TdApi.InputMessageReplyTo replyTo) {
    String message = "", quote = "", task = "0", option = "";
    TdApi.InputTextQuote q = null;
    if (replyTo instanceof TdApi.InputMessageReplyToMessage) {
      TdApi.InputMessageReplyToMessage r = (TdApi.InputMessageReplyToMessage) replyTo;
      message = "m:" + r.messageId;
      q = r.quote;
      task = Integer.toString(r.checklistTaskId);
      option = r.pollOptionId != null ? Uri.encode(r.pollOptionId) : "";
    } else if (replyTo instanceof TdApi.InputMessageReplyToExternalMessage) {
      TdApi.InputMessageReplyToExternalMessage r = (TdApi.InputMessageReplyToExternalMessage) replyTo;
      message = "x:" + r.chatId + ":" + r.messageId;
      q = r.quote;
      task = Integer.toString(r.checklistTaskId);
      option = r.pollOptionId != null ? Uri.encode(r.pollOptionId) : "";
    }
    if (q != null && q.text != null && q.text.text != null && !q.text.text.isEmpty()) {
      quote = q.position + "," + Uri.encode(q.text.text) + "," + XgramMedia.encodeEntities(q.text.entities);
    }
    b.append(message).append('\n');
    b.append(quote).append('\n');
    b.append(task).append('\n');
    b.append(option).append('\n');
  }

  private static String encodeTopic (@Nullable TdApi.MessageTopic topic) {
    if (topic instanceof TdApi.MessageTopicForum) {
      return "f:" + ((TdApi.MessageTopicForum) topic).forumTopicId;
    }
    if (topic instanceof TdApi.MessageTopicThread) {
      return "t:" + ((TdApi.MessageTopicThread) topic).messageThreadId;
    }
    if (topic instanceof TdApi.MessageTopicDirectMessages) {
      return "d:" + ((TdApi.MessageTopicDirectMessages) topic).directMessagesChatTopicId;
    }
    if (topic instanceof TdApi.MessageTopicSavedMessages) {
      return "s:" + ((TdApi.MessageTopicSavedMessages) topic).savedMessagesTopicId;
    }
    return "";
  }

  @Nullable
  private static TdApi.MessageTopic decodeTopic (String value) {
    if (value.length() < 3 || value.charAt(1) != ':') {
      return null;
    }
    String id = value.substring(2);
    switch (value.charAt(0)) {
      case 'f': return new TdApi.MessageTopicForum(Integer.parseInt(id));
      case 't': return new TdApi.MessageTopicThread(Long.parseLong(id));
      case 'd': return new TdApi.MessageTopicDirectMessages(Long.parseLong(id));
      case 's': return new TdApi.MessageTopicSavedMessages(Long.parseLong(id));
    }
    return null;
  }

  @Nullable
  private static TdApi.InputMessageReplyTo decodeReply (String message, String quote, String task, String option) {
    if (message.isEmpty()) {
      return null;
    }
    TdApi.InputTextQuote q = null;
    if (!quote.isEmpty()) {
      String[] fields = quote.split(",", 3);
      if (fields.length >= 2) {
        String text = Uri.decode(fields[1]);
        TdApi.TextEntity[] entities = XgramMedia.decodeEntities(fields.length > 2 ? fields[2] : null, text.length());
        q = new TdApi.InputTextQuote(new TdApi.FormattedText(text, entities), Integer.parseInt(fields[0]));
      }
    }
    int checklistTaskId = task.isEmpty() ? 0 : Integer.parseInt(task);
    String pollOptionId = option.isEmpty() ? "" : Uri.decode(option);
    if (message.startsWith("m:")) {
      return new TdApi.InputMessageReplyToMessage(Long.parseLong(message.substring(2)), q, checklistTaskId, pollOptionId);
    }
    if (message.startsWith("x:")) {
      String[] ids = message.substring(2).split(":", 2);
      if (ids.length == 2) {
        return new TdApi.InputMessageReplyToExternalMessage(Long.parseLong(ids[0]), Long.parseLong(ids[1]), q, checklistTaskId, pollOptionId);
      }
    }
    return null;
  }

  @Nullable
  private static String[] decodeFileNames (String value) {
    if (value.isEmpty()) {
      return null;
    }
    String[] names = value.split(",", -1);
    for (int i = 0; i < names.length; i++) {
      names[i] = Uri.decode(names[i]);
    }
    return names;
  }

  @Nullable
  static Record decode (String key, int accountId, @Nullable String value) {
    if (value == null) {
      return null;
    }
    String[] f = value.split("\n", FIELDS);
    if (f.length != FIELDS || !VERSION.equals(f[0])) {
      return null;
    }
    try {
      long bootId = Long.parseLong(f[1]);
      boolean flushing = "F".equals(f[2]);
      int kind = kindOf(f[3]);
      long chatId = Long.parseLong(f[4]);
      long draftChatId = Long.parseLong(f[5]);
      long createdWall = Long.parseLong(f[6]);
      TdApi.MessageTopic topic = decodeTopic(f[7]);
      TdApi.InputMessageReplyTo replyTo = decodeReply(f[8], f[9], f[10], f[11]);
      boolean silent = "1".equals(f[12]);
      String[] fileNames = decodeFileNames(f[13]);
      String text = f[15];
      TdApi.TextEntity[] entities = XgramMedia.decodeEntities(f[14], text.length());
      return new Record(key, accountId, bootId, flushing, kind, chatId, draftChatId, createdWall, topic, replyTo, silent, fileNames, new TdApi.FormattedText(text, entities));
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Debug builds: what was written reads back the same, or the log says so. */
  private static void checkRoundTrip (XgramUndoSendItem item, String key, String stored) {
    Record record = decode(key, item.accountId, stored);
    TdApi.FormattedText typed = item.typed;
    String typedText = typed != null && typed.text != null ? typed.text : "";
    boolean ok = record != null
      && record.chatId == item.chatId
      && record.draftChatId == item.draftChatId
      && record.kind == item.kind
      && typedText.equals(record.text.text)
      && XgramMedia.encodeEntities(typed != null ? typed.entities : null).equals(XgramMedia.encodeEntities(record.text.entities))
      && Td.equalsTo(record.topic, item.draftTopic)
      && Td.equalsTo(record.replyTo, item.replyTo)
      && Arrays.equals(record.fileNames, item.fileNames);
    if (!ok) {
      android.util.Log.e("XGram", "xgram_usq round-trip mismatch: " + key);
    }
  }
}
