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
package com.fahadali.xgram.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Message history (AyuGram's "anti-recall"): keeps a local copy of messages so that deleted messages and previous
 * versions of edited messages stay readable.
 *
 * TDLib erases a message once {@code updateDeleteMessages} arrives and overwrites its content on edits, so XGram stores
 * incoming messages itself: text with its formatting, and which file the message has. When a message is deleted its
 * file, if downloaded, is copied out of TDLib's cache.
 */
public final class XgramHistory {
  /** Messages that were never deleted are dropped after this many days (0: never). */
  private static long retentionSeconds () {
    int days = XgramSettings.instance().historyRetentionDays();
    return days > 0 ? TimeUnit.DAYS.toSeconds(days) : 0;
  }

  private static XgramHistory instance;

  public static XgramHistory instance () {
    if (instance == null) {
      synchronized (XgramHistory.class) {
        if (instance == null) {
          instance = new XgramHistory(UI.getContext().getApplicationContext());
        }
      }
    }
    return instance;
  }

  private final Helper helper;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "XgramHistory");
    thread.setPriority(Thread.MIN_PRIORITY);
    return thread;
  });
  private int insertsSincePrune;

  private XgramHistory (Context context) {
    this.helper = new Helper(context);
    executor.execute(this::prune);
  }

  /** A saved copy of one message. */
  private static final class Row {
    final int accountId;
    final long chatId, messageId, senderId, topicId;
    final int date;
    final String text, entities, kind;
    @Nullable final XgramMedia.Info media;
    final String fallbackFileIds;

    Row (int accountId, TdApi.Message message, String text) {
      this.accountId = accountId;
      this.chatId = message.chatId;
      this.messageId = message.id;
      this.senderId = senderId(message.senderId);
      this.topicId = message.topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) message.topicId).forumTopicId : 0;
      this.date = message.date;
      this.text = text;
      TdApi.FormattedText formatted = XgramMedia.formattedTextOf(message.content);
      this.entities = formatted != null ? XgramMedia.encodeEntities(formatted.entities) : "";
      this.kind = kindOf(message.content);
      this.media = XgramMedia.infoOf(message.content);
      this.fallbackFileIds = XgramMedia.fallbackFileIdsOf(message.content);
    }
  }

  // Hooks (TDLib thread)

  public static void onNewMessage (Tdlib tdlib, TdApi.Message message) {
    if (!XgramSettings.instance().historySaveDeleted() || message.sendingState != null || message.isOutgoing) {
      return;
    }
    String text = textOf(message.content);
    if (text == null) {
      return;
    }
    Row row = new Row(tdlib.id(), message, text);
    XgramHistory history = instance();
    history.executor.execute(() -> history.insert(row));
  }

  public static void onMessageContentChanged (Tdlib tdlib, TdApi.UpdateMessageContent update) {
    if (!XgramSettings.instance().historySaveEdits()) {
      return;
    }
    String newText = textOf(update.newContent);
    if (newText == null) {
      return;
    }
    int accountId = tdlib.id();
    int editDate = (int) (System.currentTimeMillis() / 1000L);
    TdApi.FormattedText formatted = XgramMedia.formattedTextOf(update.newContent);
    String entities = formatted != null ? XgramMedia.encodeEntities(formatted.entities) : "";
    XgramHistory history = instance();
    history.executor.execute(() -> history.edit(accountId, update.chatId, update.messageId, newText, entities, editDate));
  }

  public static void onMessagesDeleted (Tdlib tdlib, TdApi.UpdateDeleteMessages update) {
    if (!update.isPermanent || update.fromCache || !XgramSettings.instance().historySaveDeleted()) {
      return;
    }
    int accountId = tdlib.id();
    int deletedDate = (int) (System.currentTimeMillis() / 1000L);
    // Your own deletions are forgotten, everything else is kept (and stays in the open chat).
    List<Long> kept = new ArrayList<>(update.messageIds.length);
    List<Long> own = new ArrayList<>();
    for (long messageId : update.messageIds) {
      (XgramDeleted.isSelfDeleted(tdlib, update.chatId, messageId) ? own : kept).add(messageId);
    }
    XgramDeleted.notePermanentDeletion(tdlib, update.chatId, update.messageIds);
    long[] keptIds = toArray(kept), ownIds = toArray(own);
    XgramHistory history = instance();
    history.executor.execute(() -> {
      history.markDeleted(accountId, update.chatId, keptIds, deletedDate);
      history.forget(accountId, update.chatId, ownIds);
      history.saveMedia(tdlib, update.chatId, keptIds);
    });
  }

  /** A message deleted while its chat was open: saved now if it was only loaded from history. */
  public static void saveDeleted (Tdlib tdlib, TdApi.Message message) {
    String text = textOf(message.content);
    Row row = new Row(tdlib.id(), message, text != null ? text : "");
    int deletedDate = (int) (System.currentTimeMillis() / 1000L);
    XgramHistory history = instance();
    history.executor.execute(() -> {
      history.insert(row);
      history.markDeleted(row.accountId, row.chatId, new long[] {row.messageId}, deletedDate);
      history.saveMedia(tdlib, row.chatId, new long[] {row.messageId});
    });
  }

  private static long[] toArray (List<Long> list) {
    long[] result = new long[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  // Content

  @Nullable
  private static String textOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageSticker.CONSTRUCTOR:
        return ((TdApi.MessageSticker) content).sticker.emoji;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return "";
      default: {
        TdApi.FormattedText text = XgramMedia.formattedTextOf(content);
        return text != null ? text.text : null;
      }
    }
  }

  private static String kindOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: return Kind.TEXT;
      case TdApi.MessagePhoto.CONSTRUCTOR: return Kind.PHOTO;
      case TdApi.MessageVideo.CONSTRUCTOR: return Kind.VIDEO;
      case TdApi.MessageAnimation.CONSTRUCTOR: return Kind.GIF;
      case TdApi.MessageDocument.CONSTRUCTOR: return Kind.FILE;
      case TdApi.MessageAudio.CONSTRUCTOR: return Kind.MUSIC;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return Kind.VOICE;
      case TdApi.MessageVideoNote.CONSTRUCTOR: return Kind.ROUND;
      case TdApi.MessageSticker.CONSTRUCTOR: return Kind.STICKER;
      default: return Kind.TEXT;
    }
  }

  public static final class Kind {
    public static final String TEXT = "text", PHOTO = "photo", VIDEO = "video", GIF = "gif", FILE = "file",
      MUSIC = "music", VOICE = "voice", ROUND = "round", STICKER = "sticker";
  }

  /** "📷 Photo" and the like, or null for text messages. */
  @Nullable
  public static String mediaLabel (String kind) {
    switch (kind) {
      case Kind.PHOTO: return "📷 " + Lang.getString(R.string.XgramKindPhoto);
      case Kind.VIDEO: return "🎬 " + Lang.getString(R.string.XgramKindVideo);
      case Kind.GIF: return Lang.getString(R.string.XgramKindGif);
      case Kind.FILE: return "📄 " + Lang.getString(R.string.XgramKindFile);
      case Kind.MUSIC: return "🎵 " + Lang.getString(R.string.XgramKindMusic);
      case Kind.VOICE: return "🎤 " + Lang.getString(R.string.XgramKindVoice);
      case Kind.ROUND: return "⭕ " + Lang.getString(R.string.XgramKindRound);
      case Kind.STICKER: return Lang.getString(R.string.XgramKindSticker);
      default: return null;
    }
  }

  /** Text for a saved message without its file: a media label, then the text or caption. */
  public static String displayText (Entry entry) {
    String label = mediaLabel(entry.kind);
    if (label == null) {
      return entry.text;
    }
    if (Kind.STICKER.equals(entry.kind)) {
      return entry.text.isEmpty() ? label : entry.text + " " + label;
    }
    return entry.text.isEmpty() ? label : label + "\n" + entry.text;
  }

  private static long senderId (TdApi.MessageSender sender) {
    if (sender instanceof TdApi.MessageSenderUser) {
      return ((TdApi.MessageSenderUser) sender).userId;
    }
    if (sender instanceof TdApi.MessageSenderChat) {
      return ((TdApi.MessageSenderChat) sender).chatId;
    }
    return 0;
  }

  // Storage (executor thread)

  @WorkerThread
  private void insert (Row row) {
    ContentValues values = new ContentValues();
    values.put("account", row.accountId);
    values.put("chat", row.chatId);
    values.put("id", row.messageId);
    values.put("sender", row.senderId);
    values.put("date", row.date);
    values.put("text", row.text);
    values.put("kind", row.kind);
    values.put("topic", row.topicId);
    values.put("entities", row.entities);
    if (row.media != null) {
      values.put("file_id", row.media.fileId);
      values.put("file_name", row.media.fileName);
      values.put("mime", row.media.mimeType);
      values.put("width", row.media.width);
      values.put("height", row.media.height);
      values.put("file_size", row.media.size);
      values.put("fallback_file_ids", row.fallbackFileIds);
    }
    try {
      helper.getWritableDatabase().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    } catch (Throwable ignored) { }
    if (++insertsSincePrune >= 1000) {
      insertsSincePrune = 0;
      prune();
    }
  }

  @WorkerThread
  private void edit (int accountId, long chatId, long messageId, String newText, String newEntities, int editDate) {
    try {
      SQLiteDatabase db = helper.getWritableDatabase();
      String[] args = {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)};
      String oldText = null;
      try (Cursor c = db.rawQuery("SELECT text FROM messages WHERE account = ? AND chat = ? AND id = ?", args)) {
        if (c.moveToFirst()) {
          oldText = c.getString(0);
        }
      }
      if (oldText == null || oldText.equals(newText)) {
        return;
      }
      ContentValues edit = new ContentValues();
      edit.put("account", accountId);
      edit.put("chat", chatId);
      edit.put("id", messageId);
      edit.put("edit_date", editDate);
      edit.put("old_text", oldText);
      db.insert("edits", null, edit);
      ContentValues update = new ContentValues();
      update.put("text", newText);
      update.put("entities", newEntities);
      db.update("messages", update, "account = ? AND chat = ? AND id = ?", args);
    } catch (Throwable ignored) { }
  }

  @WorkerThread
  private void markDeleted (int accountId, long chatId, long[] messageIds, int deletedDate) {
    if (messageIds.length == 0) {
      return;
    }
    try {
      SQLiteDatabase db = helper.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put("deleted_date", deletedDate);
      db.beginTransaction();
      try {
        for (long messageId : messageIds) {
          db.update("messages", values, "account = ? AND chat = ? AND id = ? AND deleted_date = 0",
            new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)});
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    } catch (Throwable ignored) { }
  }

  /**
   * Copies the files of deleted messages out of TDLib's cache, so they survive cache cleanups. Only files that are
   * already downloaded can be saved: after deletion the server no longer serves them.
   */
  @WorkerThread
  private void saveMedia (Tdlib tdlib, long chatId, long[] messageIds) {
    if (messageIds.length == 0 || !XgramSettings.instance().historySaveMedia()) {
      return;
    }
    int accountId = tdlib.id();
    for (long messageId : messageIds) {
      int fileId = 0;
      String fileName = "", fallbacks = null;
      try (Cursor c = helper.getReadableDatabase().rawQuery(
        "SELECT file_id, file_name, fallback_file_ids FROM messages WHERE account = ? AND chat = ? AND id = ? AND file_id != 0 AND media_path IS NULL",
        new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)})) {
        if (c.moveToFirst()) {
          fileId = c.getInt(0);
          fileName = c.getString(1);
          fallbacks = c.getString(2);
        }
      } catch (Throwable ignored) { }
      if (fileId <= 0) {
        continue;
      }
      List<Integer> fileIds = new ArrayList<>();
      fileIds.add(fileId);
      if (fallbacks != null && !fallbacks.isEmpty()) {
        for (String id : fallbacks.split(",")) {
          try {
            int value = Integer.parseInt(id.trim());
            if (value > 0 && !fileIds.contains(value)) {
              fileIds.add(value);
            }
          } catch (NumberFormatException ignored) { }
        }
      }
      saveFirstDownloaded(tdlib, accountId, chatId, messageId, fileName != null ? fileName : "", fileIds, 0);
    }
  }

  /** Copies the first of the files (a photo's sizes, biggest first) that is fully downloaded. */
  private void saveFirstDownloaded (Tdlib tdlib, int accountId, long chatId, long messageId, String name, List<Integer> fileIds, int index) {
    if (index >= fileIds.size()) {
      return;
    }
    tdlib.send(new TdApi.GetFile(fileIds.get(index)), (file, error) -> {
      if (file != null && file.local.isDownloadingCompleted && !file.local.path.isEmpty()) {
        String path = file.local.path;
        executor.execute(() -> {
          String copy = XgramMedia.copy(path, accountId, chatId, messageId, name);
          if (copy != null) {
            setMediaPath(accountId, chatId, messageId, copy);
          }
        });
      } else {
        saveFirstDownloaded(tdlib, accountId, chatId, messageId, name, fileIds, index + 1);
      }
    });
  }

  @WorkerThread
  private void setMediaPath (int accountId, long chatId, long messageId, String path) {
    try {
      ContentValues values = new ContentValues();
      values.put("media_path", path);
      helper.getWritableDatabase().update("messages", values, "account = ? AND chat = ? AND id = ?",
        new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)});
    } catch (Throwable ignored) { }
  }

  @WorkerThread
  private void forget (int accountId, long chatId, long[] messageIds) {
    if (messageIds.length == 0) {
      return;
    }
    try {
      SQLiteDatabase db = helper.getWritableDatabase();
      db.beginTransaction();
      try {
        for (long messageId : messageIds) {
          String[] args = {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)};
          try (Cursor c = db.rawQuery("SELECT media_path FROM messages WHERE account = ? AND chat = ? AND id = ?", args)) {
            if (c.moveToFirst()) {
              XgramMedia.deleteFile(c.getString(0));
            }
          }
          db.delete("messages", "account = ? AND chat = ? AND id = ?", args);
          db.delete("edits", "account = ? AND chat = ? AND id = ?", args);
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    } catch (Throwable ignored) { }
  }

  @WorkerThread
  private void prune () {
    try {
      long retention = retentionSeconds();
      if (retention == 0) {
        return;
      }
      long minDate = System.currentTimeMillis() / 1000L - retention;
      helper.getWritableDatabase().delete("messages", "deleted_date = 0 AND date < ? AND NOT EXISTS (SELECT 1 FROM edits e WHERE e.account = messages.account AND e.chat = messages.chat AND e.id = messages.id)", new String[] {String.valueOf(minDate)});
    } catch (Throwable ignored) { }
  }

  // Reading

  public static final class ChatEntry {
    public final long chatId;
    public final int count;
    public final int lastDeletedDate;

    ChatEntry (long chatId, int count, int lastDeletedDate) {
      this.chatId = chatId;
      this.count = count;
      this.lastDeletedDate = lastDeletedDate;
    }
  }

  public static final class Entry {
    public final long chatId, messageId, senderId, topicId;
    public final int date, deletedDate;
    public final String text, kind, entities;
    @Nullable public final String mediaPath;
    public final String fileName, mimeType;
    public final int width, height;
    public final List<Edit> edits = new ArrayList<>();

    Entry (long chatId, Cursor c) {
      // Columns: see ENTRY_COLUMNS.
      this.chatId = chatId;
      this.messageId = c.getLong(0);
      this.senderId = c.getLong(1);
      this.date = c.getInt(2);
      this.text = c.getString(3);
      this.kind = c.getString(4);
      this.topicId = c.getLong(5);
      this.deletedDate = c.getInt(6);
      this.entities = c.isNull(7) ? "" : c.getString(7);
      this.mediaPath = c.isNull(8) ? null : c.getString(8);
      this.fileName = c.isNull(9) ? "" : c.getString(9);
      this.mimeType = c.isNull(10) ? "" : c.getString(10);
      this.width = c.getInt(11);
      this.height = c.getInt(12);
    }

    public boolean hasSavedMedia () {
      return mediaPath != null && new java.io.File(mediaPath).isFile();
    }
  }

  private static final String ENTRY_COLUMNS = "id, sender, date, text, kind, topic, deleted_date, entities, media_path, file_name, mime, width, height";

  public static final class Edit {
    public final int editDate;
    public final String oldText;

    Edit (int editDate, String oldText) {
      this.editDate = editDate;
      this.oldText = oldText;
    }
  }

  public interface Callback<T> {
    void onResult (T result);
  }

  /** Chats that have deleted or edited messages, most recent first. Callback runs on the UI thread. */
  public void getChats (int accountId, Callback<List<ChatEntry>> callback) {
    executor.execute(() -> {
      List<ChatEntry> result = new ArrayList<>();
      try (Cursor c = helper.getReadableDatabase().rawQuery(
        "SELECT chat, COUNT(*), MAX(deleted_date) FROM messages WHERE account = ? AND (deleted_date > 0 OR " +
          "EXISTS (SELECT 1 FROM edits e WHERE e.account = messages.account AND e.chat = messages.chat AND e.id = messages.id)) " +
          "GROUP BY chat ORDER BY MAX(deleted_date) DESC, MAX(date) DESC",
        new String[] {String.valueOf(accountId)})) {
        while (c.moveToNext()) {
          result.add(new ChatEntry(c.getLong(0), c.getInt(1), c.getInt(2)));
        }
      } catch (Throwable ignored) { }
      UI.post(() -> callback.onResult(result));
    });
  }

  /** Deleted and edited messages of one chat, newest first. Callback runs on the UI thread. */
  public void getMessages (int accountId, long chatId, Callback<List<Entry>> callback) {
    executor.execute(() -> {
      List<Entry> result = new ArrayList<>();
      try {
        SQLiteDatabase db = helper.getReadableDatabase();
        String[] args = {String.valueOf(accountId), String.valueOf(chatId)};
        try (Cursor c = db.rawQuery(
          "SELECT " + ENTRY_COLUMNS + " FROM messages WHERE account = ? AND chat = ? AND (deleted_date > 0 OR " +
            "EXISTS (SELECT 1 FROM edits e WHERE e.account = messages.account AND e.chat = messages.chat AND e.id = messages.id)) " +
            "ORDER BY id DESC LIMIT 500", args)) {
          while (c.moveToNext()) {
            result.add(new Entry(chatId, c));
          }
        }
        for (Entry entry : result) {
          try (Cursor c = db.rawQuery("SELECT edit_date, old_text FROM edits WHERE account = ? AND chat = ? AND id = ? ORDER BY edit_date",
            new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(entry.messageId)})) {
            while (c.moveToNext()) {
              entry.edits.add(new Edit(c.getInt(0), c.getString(1)));
            }
          }
        }
      } catch (Throwable ignored) { }
      UI.post(() -> callback.onResult(result));
    });
  }

  /** Earlier versions of one message, oldest first. Callback runs on the UI thread. */
  public void getEdits (int accountId, long chatId, long messageId, Callback<List<Edit>> callback) {
    executor.execute(() -> {
      List<Edit> result = new ArrayList<>();
      try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT edit_date, old_text FROM edits WHERE account = ? AND chat = ? AND id = ? ORDER BY edit_date",
        new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId)})) {
        while (c.moveToNext()) {
          result.add(new Edit(c.getInt(0), c.getString(1)));
        }
      } catch (Throwable ignored) { }
      UI.post(() -> callback.onResult(result));
    });
  }

  /**
   * Deleted messages of a chat with {@code minId < id < maxId}, newest first. Runs on the calling thread (the
   * history loader merges them into a page). {@code forumTopicId} -1 means any topic.
   */
  public static final int DELETED_QUERY_LIMIT = 500;

  public List<Entry> queryDeletedSync (int accountId, long chatId, long minId, long maxId, int forumTopicId) {
    List<Entry> result = new ArrayList<>();
    try {
      String sql = "SELECT " + ENTRY_COLUMNS + " FROM messages WHERE account = ? AND chat = ? AND deleted_date > 0 AND id > ? AND id < ?" +
        // General topic messages may come without a topic id.
        (forumTopicId == 1 ? " AND (topic = ? OR topic = 0)" : forumTopicId != -1 ? " AND topic = ?" : "") + " ORDER BY id DESC LIMIT " + DELETED_QUERY_LIMIT;
      String[] args = forumTopicId != -1 ?
        new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(minId), String.valueOf(maxId), String.valueOf(forumTopicId)} :
        new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(minId), String.valueOf(maxId)};
      try (Cursor c = helper.getReadableDatabase().rawQuery(sql, args)) {
        while (c.moveToNext()) {
          result.add(new Entry(chatId, c));
        }
      }
    } catch (Throwable ignored) { }
    return result;
  }

  /** Everything saved for an account slot, e.g. after it logged out. */
  public void clearAccount (int accountId) {
    executor.execute(() -> {
      try {
        SQLiteDatabase db = helper.getWritableDatabase();
        String[] args = {String.valueOf(accountId)};
        try (Cursor c = db.rawQuery("SELECT media_path FROM messages WHERE account = ? AND media_path IS NOT NULL", args)) {
          while (c.moveToNext()) {
            XgramMedia.deleteFile(c.getString(0));
          }
        }
        db.delete("edits", "account = ?", args);
        db.delete("messages", "account = ?", args);
      } catch (Throwable ignored) { }
    });
  }

  public void clear (int accountId, long chatId, Runnable after) {
    executor.execute(() -> {
      try {
        SQLiteDatabase db = helper.getWritableDatabase();
        String[] args = {String.valueOf(accountId), String.valueOf(chatId)};
        try (Cursor c = db.rawQuery("SELECT media_path FROM messages WHERE account = ? AND chat = ? AND media_path IS NOT NULL", args)) {
          while (c.moveToNext()) {
            XgramMedia.deleteFile(c.getString(0));
          }
        }
        db.delete("edits", "account = ? AND chat = ?", args);
        db.delete("messages", "account = ? AND chat = ?", args);
      } catch (Throwable ignored) { }
      UI.post(after);
    });
  }

  private static final class Helper extends SQLiteOpenHelper {
    Helper (Context context) {
      super(context, "xgram_history.db", null, 4);
      // Reads (the history loader, on the TDLib thread) don't wait for the writes of incoming messages.
      setWriteAheadLoggingEnabled(true);
    }

    private static void addVersion3Indexes (SQLiteDatabase db) {
      // Retention pruning by date.
      db.execSQL("CREATE INDEX IF NOT EXISTS messages_date ON messages (deleted_date, date)");
    }

    @Override
    public void onCreate (SQLiteDatabase db) {
      db.execSQL("CREATE TABLE messages (account INTEGER NOT NULL, chat INTEGER NOT NULL, id INTEGER NOT NULL, " +
        "sender INTEGER NOT NULL, date INTEGER NOT NULL, text TEXT NOT NULL, kind TEXT NOT NULL, topic INTEGER NOT NULL DEFAULT 0, " +
        "deleted_date INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (account, chat, id))");
      db.execSQL("CREATE INDEX messages_deleted ON messages (account, chat, deleted_date)");
      db.execSQL("CREATE TABLE edits (account INTEGER NOT NULL, chat INTEGER NOT NULL, id INTEGER NOT NULL, " +
        "edit_date INTEGER NOT NULL, old_text TEXT NOT NULL)");
      db.execSQL("CREATE INDEX edits_message ON edits (account, chat, id)");
      addVersion2Columns(db);
      addVersion3Indexes(db);
      addVersion4Columns(db);
    }

    private static void addVersion4Columns (SQLiteDatabase db) {
      // Photos: the smaller sizes, kept when the largest one was never downloaded.
      db.execSQL("ALTER TABLE messages ADD COLUMN fallback_file_ids TEXT");
    }

    private static void addVersion2Columns (SQLiteDatabase db) {
      // Formatting and the message's file (to copy it when the message is deleted).
      db.execSQL("ALTER TABLE messages ADD COLUMN entities TEXT");
      db.execSQL("ALTER TABLE messages ADD COLUMN file_id INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE messages ADD COLUMN file_name TEXT");
      db.execSQL("ALTER TABLE messages ADD COLUMN mime TEXT");
      db.execSQL("ALTER TABLE messages ADD COLUMN width INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE messages ADD COLUMN height INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE messages ADD COLUMN file_size INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE messages ADD COLUMN media_path TEXT");
    }

    @Override
    public void onUpgrade (SQLiteDatabase db, int oldVersion, int newVersion) {
      if (oldVersion < 2) {
        addVersion2Columns(db);
      }
      if (oldVersion < 3) {
        addVersion3Indexes(db);
      }
      if (oldVersion < 4) {
        addVersion4Columns(db);
      }
    }
  }
}
