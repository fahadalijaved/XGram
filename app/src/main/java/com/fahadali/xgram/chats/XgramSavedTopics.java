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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.core.reference.ReferenceList;

/**
 * Saved Messages by chat (official Telegram): TDLib groups Saved Messages into topics, one per chat the messages were
 * saved from. Telegram X ignores {@code updateSavedMessagesTopic}; XGram keeps them here.
 */
public final class XgramSavedTopics {
  private XgramSavedTopics () { }

  public interface Listener {
    void onSavedTopicsChanged (Tdlib tdlib);
  }

  // Keyed by the logged in user: account slots are reused after logging out.
  private static final Map<Long, Map<Long, TdApi.SavedMessagesTopic>> topics = new ConcurrentHashMap<>();
  private static final ReferenceList<Listener> listeners = new ReferenceList<>(true);

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  /** TDLib thread, {@code updateSavedMessagesTopic}. Topics with order 0 are not in the list. */
  public static void onUpdate (Tdlib tdlib, TdApi.SavedMessagesTopic topic) {
    long myUserId = tdlib.myUserId();
    if (myUserId == 0) {
      return;
    }
    Map<Long, TdApi.SavedMessagesTopic> map = topics.get(myUserId);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      Map<Long, TdApi.SavedMessagesTopic> existing = topics.putIfAbsent(myUserId, map);
      if (existing != null) {
        map = existing;
      }
    }
    if (topic.order == 0) {
      map.remove(topic.id);
    } else {
      map.put(topic.id, topic);
    }
    tdlib.ui().post(() -> {
      for (Listener listener : listeners) {
        listener.onSavedTopicsChanged(tdlib);
      }
    });
  }

  /** Topics of an account, in Telegram's order. */
  public static List<TdApi.SavedMessagesTopic> list (Tdlib tdlib) {
    Map<Long, TdApi.SavedMessagesTopic> map = topics.get(tdlib.myUserId());
    List<TdApi.SavedMessagesTopic> result = map != null ? new ArrayList<>(map.values()) : new ArrayList<>();
    Collections.sort(result, (a, b) -> a.order != b.order ? Long.compare(b.order, a.order) : Long.compare(b.id, a.id));
    return result;
  }

  @Nullable
  public static TdApi.SavedMessagesTopic find (Tdlib tdlib, long savedMessagesTopicId) {
    Map<Long, TdApi.SavedMessagesTopic> map = topics.get(tdlib.myUserId());
    return map != null ? map.get(savedMessagesTopicId) : null;
  }

  /** Loads every topic; they arrive as updates. {@code after} runs on the UI thread once TDLib has sent them all. */
  public static void loadAll (Tdlib tdlib, @Nullable Runnable after) {
    tdlib.send(new TdApi.LoadSavedMessagesTopics(100), (ok, error) -> {
      if (error == null) {
        loadAll(tdlib, after);
      } else if (after != null) {
        // 404: everything is loaded.
        tdlib.ui().post(after);
      }
    });
  }

  public static String title (Tdlib tdlib, @Nullable TdApi.SavedMessagesTopic topic) {
    if (topic == null) {
      return Lang.getString(R.string.SavedMessages);
    }
    switch (topic.type.getConstructor()) {
      case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR:
        return Lang.getString(R.string.XgramMyNotes);
      case TdApi.SavedMessagesTopicTypeAuthorHidden.CONSTRUCTOR:
        return Lang.getString(R.string.XgramAuthorHidden);
      case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR:
      default: {
        long chatId = ((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId;
        return tdlib.chatTitle(chatId);
      }
    }
  }
}
