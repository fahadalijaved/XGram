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

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.leveldb.LevelDB;

/** Local message bookmarks (NagramX): stored on this device only, unlike Saved Messages. */
public final class XgramBookmarks {
  private XgramBookmarks () { }

  private static final String PREFIX = "xgram_bm_";

  public static final class Bookmark {
    public final long chatId, messageId;
    public final long addedAt;

    Bookmark (long chatId, long messageId, long addedAt) {
      this.chatId = chatId;
      this.messageId = messageId;
      this.addedAt = addedAt;
    }
  }

  private static String key (Tdlib tdlib, long chatId, long messageId) {
    return PREFIX + tdlib.id() + "_" + chatId + "_" + messageId;
  }

  public static boolean isBookmarked (Tdlib tdlib, long chatId, long messageId) {
    return Settings.instance().pmc().contains(key(tdlib, chatId, messageId));
  }

  public static void setBookmarked (Tdlib tdlib, long chatId, long messageId, boolean bookmarked) {
    if (bookmarked) {
      Settings.instance().pmc().putLong(key(tdlib, chatId, messageId), System.currentTimeMillis());
    } else {
      Settings.instance().pmc().remove(key(tdlib, chatId, messageId));
    }
  }

  /** Bookmarks of an account, newest first. */
  public static List<Bookmark> list (Tdlib tdlib) {
    String prefix = PREFIX + tdlib.id() + "_";
    List<Bookmark> result = new ArrayList<>();
    for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
      String[] parts = entry.key().substring(prefix.length()).split("_");
      if (parts.length != 2) {
        continue;
      }
      try {
        result.add(new Bookmark(Long.parseLong(parts[0]), Long.parseLong(parts[1]), entry.asLong()));
      } catch (NumberFormatException ignored) { }
    }
    Collections.sort(result, (a, b) -> Long.compare(b.addedAt, a.addedAt));
    return result;
  }
}
