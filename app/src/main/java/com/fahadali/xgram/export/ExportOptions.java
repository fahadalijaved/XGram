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

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * What one chat export covers: the chat or one of its topics, which media types go into the .zip, their size limit and
 * the date range. Immutable; the options sheet builds it, the job only reads it. The media types and the size limit
 * are remembered for the next export, as Desktop does (the dates are not).
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
  public static final int TYPE_ALL = TYPE_PHOTOS | TYPE_VIDEOS | TYPE_VOICE | TYPE_VIDEO_NOTES | TYPE_STICKERS | TYPE_GIFS | TYPE_FILES;

  private static final long MB = 1024L * 1024L;

  /** The size limit slider, in MB: Desktop's range (1..4000 MB, export_settings.cpp SizeLimitByIndex) in 13 steps. */
  public static final int[] SIZE_STEPS_MB = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4000};
  /** Desktop's default size limit (8 MB, export_settings.h). */
  public static final int DEFAULT_SIZE_MB = 8;
  public static final long DEFAULT_SIZE_LIMIT = DEFAULT_SIZE_MB * MB;

  // Exact keys, read and written as ints by settings backups (XgramBackup); the size is in MB, not a slider index,
  // so a backup stays right if the steps change
  public static final String KEY_TYPES = "xgram_export_types";
  public static final String KEY_SIZE_MB = "xgram_export_size";

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
  /**
   * XGram's Privacy → Allow saving (protected media and text) as it was when the export started: the media and vCards
   * of messages that can't be saved go into the .zip too. Read once, so switching it halfway doesn't split an export.
   */
  public final boolean saveProtected;

  public ExportOptions (int accountId, long chatId, int scope, long topicId, int typesMask, long sizeLimitBytes, int fromDate, int tillDate) {
    this.accountId = accountId;
    this.chatId = chatId;
    this.scope = scope;
    this.topicId = topicId;
    this.typesMask = typesMask;
    this.sizeLimitBytes = sizeLimitBytes;
    this.fromDate = fromDate;
    this.tillDate = tillDate;
    this.saveProtected = XgramSettings.instance().saveProtected();
  }

  public boolean includes (int type) {
    return (typesMask & type) != 0;
  }

  /** Whether {@code message}'s media (or vCard) may go into the .zip: it can be saved, or Allow saving is on. */
  public boolean canSave (TdApi.Message message) {
    return message.canBeSaved || saveProtected;
  }

  public boolean isTopic () {
    return scope != SCOPE_WHOLE;
  }

  /** Desktop's date filter: {@code fromDate <= date < tillDate}, a 0 bound is open. */
  public boolean inRange (int date) {
    return (fromDate == 0 || date >= fromDate) && (tillDate == 0 || date < tillDate);
  }

  // The last choices (main thread)

  /** The media types of the last export; Desktop starts with Photos only. */
  public static int lastTypes () {
    return Settings.instance().getInt(KEY_TYPES, TYPE_PHOTOS) & TYPE_ALL;
  }

  /** The slider step of the last size limit (the nearest one when a backup holds another value). */
  public static int lastSizeIndex () {
    return sizeIndexOf(Settings.instance().getInt(KEY_SIZE_MB, DEFAULT_SIZE_MB));
  }

  public static void saveLast (int typesMask, int sizeIndex) {
    Settings.instance().putInt(KEY_TYPES, typesMask & TYPE_ALL);
    Settings.instance().putInt(KEY_SIZE_MB, sizeMbAt(sizeIndex));
  }

  public static int sizeMbAt (int index) {
    return SIZE_STEPS_MB[Math.max(0, Math.min(SIZE_STEPS_MB.length - 1, index))];
  }

  public static long sizeBytesAt (int index) {
    return sizeMbAt(index) * MB;
  }

  static int sizeIndexOf (int mb) {
    int best = 0;
    for (int i = 0; i < SIZE_STEPS_MB.length; i++) {
      if (Math.abs(SIZE_STEPS_MB[i] - mb) < Math.abs(SIZE_STEPS_MB[best] - mb)) {
        best = i;
      }
    }
    return best;
  }
}
