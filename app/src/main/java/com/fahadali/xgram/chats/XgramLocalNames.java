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

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tgx.td.ChatId;

/**
 * Local names (AyuGram / NagramX "name override"): rename a person or chat on this device only. The new name is
 * written into the user and chat objects TDLib sends before Telegram X keeps them, so it shows everywhere.
 */
public final class XgramLocalNames {
  private XgramLocalNames () { }

  private static final String PREFIX = "xgram_ln_";
  private static final String EMPTY = "\u0000";
  private static final Map<String, String> cache = new ConcurrentHashMap<>();

  // Real names of renamed users, for anything that leaves the device (sharing a contact).
  private static final Map<String, String[]> originals = new ConcurrentHashMap<>();

  public static void forgetAccount (int accountId) {
    com.fahadali.xgram.XgramAccountData.removeKeys(cache.keySet(), PREFIX + accountId + "_");
    com.fahadali.xgram.XgramAccountData.removeKeys(originals.keySet(), PREFIX + accountId + "_");
  }

  /** A contact card with the user's real name, not the local one. */
  public static TdApi.Contact contactOf (Tdlib tdlib, TdApi.User user, long userId) {
    String[] original = originals.get(key(tdlib, user.id));
    String firstName = original != null ? original[0] : user.firstName;
    String lastName = original != null ? original[1] : user.lastName;
    return new TdApi.Contact(user.phoneNumber, firstName, lastName, null, userId);
  }

  private static String key (Tdlib tdlib, long chatId) {
    return PREFIX + tdlib.id() + "_" + chatId;
  }

  private static volatile boolean loaded;

  /** One prefix scan instead of a database read for every user and chat TDLib sends at startup. */
  private static void ensureLoaded () {
    if (loaded) {
      return;
    }
    synchronized (cache) {
      if (loaded) {
        return;
      }
      java.util.List<String> keys = new java.util.ArrayList<>();
      for (me.vkryl.leveldb.LevelDB.Entry entry : Settings.instance().pmc().find(PREFIX)) {
        keys.add(entry.key());
      }
      for (String key : keys) {
        String value = Settings.instance().pmc().getString(key, null);
        if (value != null) {
          cache.put(key, value);
        }
      }
      loaded = true;
    }
  }

  /** Local name for a chat (a private chat's id is the user id), or null. */
  @Nullable
  @AnyThread
  public static String get (Tdlib tdlib, long chatId) {
    ensureLoaded();
    String value = cache.get(key(tdlib, chatId));
    return value == null || EMPTY.equals(value) ? null : value;
  }

  // Hooks (TDLib thread)

  public static void rewriteUser (Tdlib tdlib, TdApi.User user) {
    String name = get(tdlib, user.id);
    if (name != null) {
      originals.put(key(tdlib, user.id), new String[] {user.firstName, user.lastName});
      user.firstName = name;
      user.lastName = "";
    } else {
      originals.remove(key(tdlib, user.id));
    }
  }

  public static void rewriteChat (Tdlib tdlib, TdApi.Chat chat) {
    String name = get(tdlib, chat.id);
    if (name != null) {
      chat.title = name;
    }
  }

  public static void rewriteChatTitle (Tdlib tdlib, TdApi.UpdateChatTitle update) {
    String name = get(tdlib, update.chatId);
    if (name != null) {
      update.title = name;
    }
  }

  // Editing

  public static boolean canRename (Tdlib tdlib, long chatId) {
    return chatId != 0 && !tdlib.isSelfChat(chatId) && !ChatId.isSecret(chatId);
  }

  public static void promptRename (ViewController<?> context, long chatId) {
    Tdlib tdlib = context.tdlib();
    String current = get(tdlib, chatId);
    context.openInputAlert(Lang.getString(R.string.XgramRenameLocally), Lang.getString(R.string.XgramRenameLocallyHint), R.string.Save, R.string.Cancel,
      current != null ? current : tdlib.chatTitle(chatId), (inputView, result) -> {
        set(tdlib, chatId, result.trim());
        return true;
      }, true);
  }

  /** Empty name removes the local name. */
  public static void set (Tdlib tdlib, long chatId, String name) {
    String key = key(tdlib, chatId);
    if (name.isEmpty()) {
      Settings.instance().pmc().remove(key);
      cache.put(key, EMPTY);
    } else {
      Settings.instance().pmc().putString(key, name);
      cache.put(key, name);
    }
    // Run the real objects through Telegram X's update path again, where the name is applied (or not).
    if (ChatId.isUserChat(chatId)) {
      long userId = ChatId.toUserId(chatId);
      tdlib.send(new TdApi.GetUser(userId), (user, error) -> {
        if (user != null) {
          tdlib.xgramApplyUser(user);
        }
      });
    }
    tdlib.send(new TdApi.GetChat(chatId), (chat, error) -> {
      if (chat != null) {
        tdlib.xgramApplyChatTitle(chat.id, chat.title);
      }
    });
  }
}
