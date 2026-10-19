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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.MessagesController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One send that waits (see {@link XgramUndoSend}): the exact requests Telegram X built, in order (a long text split
 * into parts, or files sent together, is one item), and what Undo puts back. The state and the result fields are
 * guarded by XgramUndoSend's lock; {@link #settled} and the owner are only touched on the main thread.
 */
final class XgramUndoSendItem {
  static final int HELD = 0, FLUSHING = 1, DONE = 2, UNDONE = 3, FAILED = 4;
  static final int KIND_TEXT = 0, KIND_VOICE = 1, KIND_VIDEO = 2, KIND_FILES = 3;

  private static final AtomicLong SEQUENCE = new AtomicLong();

  /** Process-wide send order: older items have smaller values, whatever the clock does. */
  final long order;
  /** The journal key: unique over launches. */
  final long id;
  final Tdlib tdlib;
  final int accountId;
  final int kind;
  /** The chat the requests go to. */
  final long chatId;
  /** Where the composer saves its draft: the thread's chat and the composer's topic. */
  final long draftChatId;
  @Nullable final TdApi.MessageTopic draftTopic;
  /**
   * The requests, in the order they are sent: SendMessage, or SendMessageAlbum for files. Files are made on Telegram X's
   * media thread: empty while {@link #preparing}, set once (under the lock) when they are ready.
   */
  List<TdApi.Function<?>> parts;
  /** What the message box held (the caption of files), before the split and the link preview fix; empty for recordings. */
  final TdApi.FormattedText typed;
  /** Memory only: the message the reply bar showed. */
  @Nullable final MessagesController.ReplyInfo reply;
  /** Persisted. */
  @Nullable final TdApi.InputMessageReplyTo replyTo;
  final boolean silent;
  /** Voice and video messages: their length, for the bar. */
  final int durationSeconds;
  /** Files: the tray's items (memory only) and their names (persisted), in the order the requests carry them. */
  @Nullable final ArrayList<InlineResult<?>> files;
  @Nullable final String[] fileNames;
  final long createdWall;
  final long dueUptime;
  final long totalMs;

  int state = HELD;
  /** Files whose requests aren't made yet. */
  boolean preparing;
  /** While preparing: sending was asked for up to this order (0: not yet), done once the requests are ready. */
  long flushUpToOrder;
  /** Main thread: parts with a result. */
  int settled;
  /** Parts TDLib refused, by index; null while none. */
  @Nullable boolean[] failed;
  @Nullable TdApi.Error error;
  boolean ghostScheduled;
  /** A part sent long ago without a result yet: no longer shown in the bar (the journal keeps it). */
  boolean hidden;
  /** Main thread: the composer that sent it, cleared once that composer closes. */
  @Nullable WeakReference<MessagesController> owner;
  @Nullable Runnable dueTask;
  /** Main thread: what the bar shows, made once. */
  @Nullable CharSequence preview;
  @Nullable String replyName;
  boolean previewReady;

  XgramUndoSendItem (Tdlib tdlib, int kind, long chatId, long draftChatId, @Nullable TdApi.MessageTopic draftTopic,
                     List<TdApi.Function<?>> parts, TdApi.FormattedText typed,
                     @Nullable MessagesController.ReplyInfo reply, @Nullable TdApi.InputMessageReplyTo replyTo,
                     boolean silent, int durationSeconds, @Nullable ArrayList<InlineResult<?>> files, @Nullable String[] fileNames,
                     long totalMs, MessagesController owner) {
    this.order = SEQUENCE.incrementAndGet();
    this.createdWall = System.currentTimeMillis();
    this.id = (createdWall << 8) | (order & 0xff);
    this.tdlib = tdlib;
    this.accountId = tdlib.id();
    this.kind = kind;
    this.chatId = chatId;
    this.draftChatId = draftChatId;
    this.draftTopic = draftTopic;
    this.parts = parts != null ? parts : Collections.emptyList();
    this.typed = typed;
    this.reply = reply;
    this.replyTo = replyTo;
    this.silent = silent;
    this.durationSeconds = durationSeconds;
    this.files = files;
    this.fileNames = fileNames;
    this.totalMs = totalMs;
    this.dueUptime = android.os.SystemClock.uptimeMillis() + totalMs;
    this.owner = new WeakReference<>(owner);
  }

  @Nullable
  MessagesController owner () {
    WeakReference<MessagesController> ref = owner;
    return ref != null ? ref.get() : null;
  }

  boolean isOwnedBy (MessagesController controller) {
    return controller != null && owner() == controller;
  }

  /** A voice or video message: it can't go back into the message box. */
  boolean isNote () {
    return kind == KIND_VOICE || kind == KIND_VIDEO;
  }

  boolean isFiles () {
    return kind == KIND_FILES;
  }

  int fileCount () {
    return fileNames != null ? fileNames.length : 0;
  }

  /** How many files (or contents) part {@code index} carries. */
  static int contentCount (TdApi.Function<?> part) {
    if (part instanceof TdApi.SendMessageAlbum) {
      TdApi.InputMessageContent[] contents = ((TdApi.SendMessageAlbum) part).inputMessageContents;
      return contents != null ? contents.length : 0;
    }
    return 1;
  }
}
