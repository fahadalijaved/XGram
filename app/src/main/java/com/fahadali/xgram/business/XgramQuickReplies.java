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
package com.fahadali.xgram.business;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * XGram: an account's quick replies (Telegram Business, with Premium), kept from TDLib's updates. A quick reply is a
 * named shortcut ("/hello") with up to 20 messages; in a private chat, "/" offers them and a tap sends them all.
 * Telegram X only had empty handlers for these updates.
 */
public final class XgramQuickReplies {
  private static final String TAG = "XgramQuickReplies";

  public interface Listener {
    /** Something changed (the list, a shortcut or a shortcut's messages); called on the UI thread. */
    @UiThread
    void onQuickRepliesChanged ();
  }

  private final Tdlib tdlib;
  private final Object lock = new Object();
  private int[] order = new int[0];
  private final Map<Integer, TdApi.QuickReplyShortcut> shortcuts = new HashMap<>();
  private final Map<Integer, TdApi.QuickReplyMessage[]> messages = new HashMap<>();
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  private boolean loadRequested, listKnown;

  public XgramQuickReplies (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  // TDLib's updates (the TDLib thread)

  @AnyThread
  public void onUpdate (TdApi.UpdateQuickReplyShortcuts update) {
    synchronized (lock) {
      order = update.shortcutIds != null ? update.shortcutIds : new int[0];
      listKnown = true;
    }
    notifyChanged();
  }

  @AnyThread
  public void onUpdate (TdApi.UpdateQuickReplyShortcut update) {
    synchronized (lock) {
      shortcuts.put(update.shortcut.id, update.shortcut);
    }
    notifyChanged();
  }

  @AnyThread
  public void onUpdate (TdApi.UpdateQuickReplyShortcutMessages update) {
    synchronized (lock) {
      messages.put(update.shortcutId, update.messages != null ? update.messages : new TdApi.QuickReplyMessage[0]);
    }
    notifyChanged();
  }

  @AnyThread
  public void onUpdate (TdApi.UpdateQuickReplyShortcutDeleted update) {
    synchronized (lock) {
      shortcuts.remove(update.shortcutId);
      messages.remove(update.shortcutId);
      final int[] next = new int[order.length];
      int count = 0;
      for (int id : order) {
        if (id != update.shortcutId) {
          next[count++] = id;
        }
      }
      order = java.util.Arrays.copyOf(next, count);
    }
    notifyChanged();
  }

  private void notifyChanged () {
    UI.post(() -> {
      for (Listener listener : listeners) {
        listener.onQuickRepliesChanged();
      }
    });
  }

  public void addListener (Listener listener) {
    listeners.addIfAbsent(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  // Reading

  /** Asks TDLib for the shortcuts once (they then come as updates). Only with Premium: Business needs it. */
  @AnyThread
  public void load () {
    synchronized (lock) {
      if (loadRequested || !tdlib.hasPremium()) {
        return;
      }
      loadRequested = true;
    }
    tdlib.send(new TdApi.LoadQuickReplyShortcuts(), (ok, error) -> {
      if (error != null) {
        Log.w(TAG, "LoadQuickReplyShortcuts: " + error.code + " " + error.message);
        synchronized (lock) {
          loadRequested = false; // asked again next time
        }
      }
    });
  }

  /** Whether TDLib has sent the list (an empty list included). */
  public boolean isListKnown () {
    synchronized (lock) {
      return listKnown;
    }
  }

  /** The shortcuts in Telegram's order. */
  public List<TdApi.QuickReplyShortcut> list () {
    synchronized (lock) {
      final List<TdApi.QuickReplyShortcut> list = new ArrayList<>(order.length);
      for (int id : order) {
        final TdApi.QuickReplyShortcut shortcut = shortcuts.get(id);
        if (shortcut != null) {
          list.add(shortcut);
        }
      }
      return list;
    }
  }

  public @Nullable TdApi.QuickReplyShortcut find (int shortcutId) {
    synchronized (lock) {
      return shortcuts.get(shortcutId);
    }
  }

  public @Nullable TdApi.QuickReplyShortcut findByName (String name) {
    synchronized (lock) {
      for (TdApi.QuickReplyShortcut shortcut : shortcuts.values()) {
        if (shortcut.name.equalsIgnoreCase(name)) {
          return shortcut;
        }
      }
      return null;
    }
  }

  /** A shortcut's messages, or null until TDLib sends them (ask with {@link #loadMessages}). */
  public @Nullable TdApi.QuickReplyMessage[] messages (int shortcutId) {
    synchronized (lock) {
      return messages.get(shortcutId);
    }
  }

  @AnyThread
  public void loadMessages (int shortcutId) {
    tdlib.send(new TdApi.LoadQuickReplyShortcutMessages(shortcutId), (ok, error) -> {
      if (error != null) {
        Log.w(TAG, "LoadQuickReplyShortcutMessages: " + error.code + " " + error.message);
      }
    });
  }

  /** The shortcuts whose names start with {@code prefix} (any case), in Telegram's order. */
  public List<TdApi.QuickReplyShortcut> search (String prefix) {
    final List<TdApi.QuickReplyShortcut> result = new ArrayList<>();
    for (TdApi.QuickReplyShortcut shortcut : list()) {
      if (shortcut.name.regionMatches(true, 0, prefix, 0, prefix.length())) {
        result.add(shortcut);
      }
    }
    return result;
  }

  /** Whether quick replies can be sent to this chat: a private chat with a regular user (not a bot, not yourself). */
  public static boolean canSendTo (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null || !(chat.type instanceof TdApi.ChatTypePrivate) || tdlib.isSelfChat(chat) || tdlib.isBotChat(chat.id)) {
      return false;
    }
    final TdApi.User user = tdlib.chatUser(chat);
    return user != null && user.type instanceof TdApi.UserTypeRegular && !user.isSupport;
  }
}
