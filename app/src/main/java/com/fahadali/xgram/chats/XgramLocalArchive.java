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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Archive on this phone: Telegram won't archive its own "Telegram" chat (login codes, service messages), so XGram
 * keeps it in the Archive locally. It leaves Chats, the folders and the share and forward pickers, and shows in the
 * Archive with the archived chats. Nothing changes on Telegram's side or on other devices.
 */
public final class XgramLocalArchive {
  private XgramLocalArchive () { }

  public static final String PREFIX = "xgram_la_";

  public interface Listener {
    /** Any thread. */
    void onLocalArchiveChanged (int accountId, long chatId, boolean archived);
  }

  private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private static Set<String> keys;

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private static synchronized Set<String> keys () {
    if (keys == null) {
      keys = ConcurrentHashMap.newKeySet();
      for (me.vkryl.leveldb.LevelDB.Entry entry : Settings.instance().pmc().find(PREFIX)) {
        keys.add(entry.key());
      }
    }
    return keys;
  }

  private static String key (int accountId, long chatId) {
    return PREFIX + accountId + "_" + chatId;
  }

  public static void forgetAccount (int accountId) {
    com.fahadali.xgram.XgramAccountData.removeKeys(keys(), PREFIX + accountId + "_");
  }

  /** A chat Telegram refuses to archive, which XGram archives on this phone instead. */
  public static boolean canArchiveLocally (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    return chat != null && tdlib.isServiceNotificationsChat(chat.id);
  }

  public static boolean isArchived (Tdlib tdlib, long chatId) {
    return chatId != 0 && keys().contains(key(tdlib.id(), chatId)) && tdlib.isServiceNotificationsChat(chatId);
  }

  /** Whether the account keeps any chat in the Archive on this phone. */
  public static boolean hasAny (Tdlib tdlib) {
    String prefix = PREFIX + tdlib.id() + "_";
    for (String k : keys()) {
      if (k.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** Archived by Telegram or on this phone. */
  public static boolean chatArchived (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    return chat != null && (tdlib.chatArchived(chat) || isArchived(tdlib, chat.id));
  }

  public static boolean chatArchived (Tdlib tdlib, long chatId) {
    return chatArchived(tdlib, tdlib.chat(chatId));
  }

  public static void setArchived (Tdlib tdlib, long chatId, boolean archived) {
    String key = key(tdlib.id(), chatId);
    if (archived) {
      keys().add(key);
      Settings.instance().pmc().putBoolean(key, true);
    } else {
      keys().remove(key);
      Settings.instance().pmc().remove(key);
    }
    for (Listener listener : listeners) {
      listener.onLocalArchiveChanged(tdlib.id(), chatId, archived);
    }
  }
}
