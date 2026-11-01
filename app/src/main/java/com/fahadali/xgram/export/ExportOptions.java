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

/**
 * What one chat export covers: the chat (and later a topic), which media types go into the .zip, their size limit and
 * the date range. Immutable; the options sheet builds it, the job only reads it.
 */
public final class ExportOptions {
  // Scope: the whole chat, or one forum / Saved Messages / channel direct messages topic
  public static final int SCOPE_WHOLE = 0;
  public static final int SCOPE_FORUM_TOPIC = 1;
  public static final int SCOPE_SAVED_TOPIC = 2;
  public static final int SCOPE_DM_TOPIC = 3;

  // Media types, Desktop's checkboxes (export_settings.h)
  public static final int TYPE_PHOTOS = 1;
  public static final int TYPE_VIDEOS = 1 << 1;
  public static final int TYPE_VOICE = 1 << 2;
  public static final int TYPE_VIDEO_NOTES = 1 << 3;
  public static final int TYPE_STICKERS = 1 << 4;
  public static final int TYPE_GIFS = 1 << 5;
  public static final int TYPE_FILES = 1 << 6;

  /** Desktop's default size limit (8 MB). */
  public static final long DEFAULT_SIZE_LIMIT = 8L * 1024L * 1024L;

  public final int accountId;
  public final long chatId;
  public final int scope;
  /** The topic for a topic scope: forum topic id, Saved Messages topic id or direct messages topic id; 0 otherwise. */
  public final long topicId;
  public final int typesMask;
  public final long sizeLimitBytes;
  /** Inclusive, unix seconds, 0 = from the beginning. */
  public final int fromDate;
  /** Exclusive, unix seconds, 0 = till now. */
  public final int tillDate;

  public ExportOptions (int accountId, long chatId, int scope, long topicId, int typesMask, long sizeLimitBytes, int fromDate, int tillDate) {
    this.accountId = accountId;
    this.chatId = chatId;
    this.scope = scope;
    this.topicId = topicId;
    this.typesMask = typesMask;
    this.sizeLimitBytes = sizeLimitBytes;
    this.fromDate = fromDate;
    this.tillDate = tillDate;
  }

  /** The whole history, text only, no date range: what the export does until the options sheet exists. */
  public static ExportOptions textOnly (int accountId, long chatId) {
    return new ExportOptions(accountId, chatId, SCOPE_WHOLE, 0, 0, DEFAULT_SIZE_LIMIT, 0, 0);
  }

  public boolean includes (int type) {
    return (typesMask & type) != 0;
  }

  /** Desktop's date filter: {@code fromDate <= date < tillDate}, a 0 bound is open. */
  public boolean inRange (int date) {
    return (fromDate == 0 || date >= fromDate) && (tillDate == 0 || date < tillDate);
  }
}
