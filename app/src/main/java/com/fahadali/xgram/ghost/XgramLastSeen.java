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
package com.fahadali.xgram.ghost;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local last seen (AyuGram): people who hide their last seen show "last seen recently". XGram remembers when it last
 * saw them active (online, typing, sending a message) and shows that time instead. Only on this device.
 */
public final class XgramLastSeen {
  private XgramLastSeen () { }

  private static final String PREFIX = "xgram_ls_";
  private static final Map<String, Integer> cache = new ConcurrentHashMap<>();
  /** Users whose status came hidden from the server and was replaced. */
  private static final Set<String> hidden = ConcurrentHashMap.newKeySet();

  public static void forgetAccount (int accountId) {
    String prefix = PREFIX + accountId + "_";
    com.fahadali.xgram.XgramAccountData.removeKeys(cache.keySet(), prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(hidden, prefix);
    com.fahadali.xgram.XgramAccountData.removeKeys(persisted.keySet(), prefix);
  }

  private static String key (Tdlib tdlib, long userId) {
    return PREFIX + tdlib.id() + "_" + userId;
  }

  private static boolean isEnabled () {
    return XgramSettings.instance().saveLastSeen();
  }

  private static volatile boolean loaded;

  /** One prefix scan instead of a database read for every user status TDLib sends at startup. */
  private static void ensureLoaded () {
    if (loaded) {
      return;
    }
    synchronized (cache) {
      if (loaded) {
        return;
      }
      for (me.vkryl.leveldb.LevelDB.Entry entry : Settings.instance().pmc().find(PREFIX)) {
        cache.put(entry.key(), entry.asInt());
      }
      loaded = true;
    }
  }

  private static int get (Tdlib tdlib, long userId) {
    ensureLoaded();
    Integer value = cache.get(key(tdlib, userId));
    return value != null ? value : 0;
  }

  private static final Map<String, Integer> persisted = new ConcurrentHashMap<>();

  /** Returns true if the stored time moved forward. Written to disk at most once a minute per person. */
  private static boolean record (Tdlib tdlib, long userId, int time) {
    int old = get(tdlib, userId);
    if (time <= old) {
      return false;
    }
    String key = key(tdlib, userId);
    cache.put(key, time);
    Integer lastWritten = persisted.get(key);
    if (lastWritten == null || time - lastWritten >= 60) {
      persisted.put(key, time);
      Settings.instance().pmc().putInt(key, time);
    }
    return true;
  }

  private static int now () {
    return (int) (System.currentTimeMillis() / 1000L);
  }

  /** TDLib thread: a status from the server, replaced if it's hidden and XGram saw the user active. */
  @AnyThread
  @Nullable
  public static TdApi.UserStatus onStatus (Tdlib tdlib, long userId, @Nullable TdApi.UserStatus status) {
    if (status == null || !isEnabled() || userId == tdlib.myUserId()) {
      return status;
    }
    switch (status.getConstructor()) {
      case TdApi.UserStatusOnline.CONSTRUCTOR:
        record(tdlib, userId, now());
        hidden.remove(key(tdlib, userId));
        return status;
      case TdApi.UserStatusRecently.CONSTRUCTOR:
      case TdApi.UserStatusLastWeek.CONSTRUCTOR:
      case TdApi.UserStatusLastMonth.CONSTRUCTOR: {
        int seen = get(tdlib, userId);
        if (seen > 0) {
          hidden.add(key(tdlib, userId));
          return new TdApi.UserStatusOffline(seen);
        }
        return status;
      }
      default:
        hidden.remove(key(tdlib, userId));
        return status;
    }
  }

  /** TDLib thread: the user did something (sent a message, typed). Refreshes a replaced status. */
  @AnyThread
  public static void onActivity (Tdlib tdlib, @Nullable TdApi.MessageSender sender, int time) {
    if (!(sender instanceof TdApi.MessageSenderUser) || !isEnabled()) {
      return;
    }
    long userId = ((TdApi.MessageSenderUser) sender).userId;
    if (userId == tdlib.myUserId()) {
      return;
    }
    if (record(tdlib, userId, Math.min(time, now())) && hidden.contains(key(tdlib, userId))) {
      tdlib.xgramRefreshUserStatus(userId);
    }
  }

  public static void onActivity (Tdlib tdlib, long userId) {
    onActivity(tdlib, new TdApi.MessageSenderUser(userId), now());
  }
}
