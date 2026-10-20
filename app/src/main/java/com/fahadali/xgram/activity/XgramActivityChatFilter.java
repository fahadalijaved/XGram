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
package com.fahadali.xgram.activity;

import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Arrays;

import me.vkryl.core.reference.ReferenceList;

/**
 * The chats an account's Activity page shows (none chosen: all of them), picked with the Chats chip, the menu or
 * the classic Chats row (XgramActivityChatsController). Kept per account in pmc like the page's filter: a view
 * state, not a setting, so never backed up; it goes when the account logs out. UI thread.
 */
final class XgramActivityChatFilter {
  private XgramActivityChatFilter () { }

  private static final String KEY_PREFIX = "xgram_actview_chats_";
  /** At most this many chats are kept chosen (the query lists them). */
  static final int MAX_CHATS = 200;
  private static final long[] NONE = new long[0];

  interface Listener {
    @UiThread
    void onActivityChatsChanged (int accountId);
  }

  private static final SparseArray<long[]> cache = new SparseArray<>();
  private static final ReferenceList<Listener> listeners = new ReferenceList<>();

  /** Held weakly: keep a reference to {@code listener}. */
  @UiThread
  static void addListener (@NonNull Listener listener) {
    listeners.add(listener);
  }

  @UiThread
  static void removeListener (@NonNull Listener listener) {
    listeners.remove(listener);
  }

  /** The chosen chats, sorted (empty: all chats). Shared: not to be changed. */
  @UiThread
  @NonNull
  static long[] get (int accountId) {
    long[] chats = cache.get(accountId);
    if (chats == null) {
      long[] stored = null;
      try {
        stored = Settings.instance().pmc().getLongArray(KEY_PREFIX + accountId);
      } catch (Throwable t) {
        Log.e(XgramActivity.TAG, "Cannot read the chosen chats", t);
      }
      chats = normalize(stored);
      cache.put(accountId, chats);
    }
    return chats;
  }

  /** Chooses {@code chats} (null or empty: all chats again); the pages hear of it at once. */
  @UiThread
  static void set (int accountId, @Nullable long[] chats) {
    final long[] next = normalize(chats);
    if (Arrays.equals(next, get(accountId))) {
      return;
    }
    cache.put(accountId, next);
    final String key = KEY_PREFIX + accountId;
    try {
      if (next.length == 0) {
        Settings.instance().pmc().remove(key);
      } else {
        Settings.instance().pmc().putLongArray(key, next);
      }
    } catch (Throwable t) {
      Log.e(XgramActivity.TAG, "Cannot save the chosen chats", t);
    }
    for (Listener listener : listeners) {
      try {
        listener.onActivityChatsChanged(accountId);
      } catch (Throwable t) {
        Log.e(XgramActivity.TAG, "Chats listener failed", t);
      }
    }
  }

  /** {@code chatId} chosen or not (the others stay as they are). */
  @UiThread
  static void toggle (int accountId, long chatId, boolean chosen) {
    final long[] current = get(accountId);
    final boolean has = contains(current, chatId);
    if (has == chosen) {
      return;
    }
    if (chosen) {
      long[] next = Arrays.copyOf(current, current.length + 1);
      next[current.length] = chatId;
      set(accountId, next);
    } else {
      long[] next = new long[current.length - 1];
      int i = 0;
      for (long id : current) {
        if (id != chatId) {
          next[i++] = id;
        }
      }
      set(accountId, next);
    }
  }

  /** Whether {@code sorted} (as {@link #get} gives it) has {@code chatId}. */
  static boolean contains (@NonNull long[] sorted, long chatId) {
    return sorted.length > 0 && Arrays.binarySearch(sorted, chatId) >= 0;
  }

  /** Every chat of {@code inner} is in {@code outer}; an empty {@code outer} is all chats. */
  static boolean containsAll (@NonNull long[] outer, @NonNull long[] inner) {
    if (outer.length == 0) {
      return true;
    }
    if (inner.length == 0) {
      return false;
    }
    for (long chatId : inner) {
      if (Arrays.binarySearch(outer, chatId) < 0) {
        return false;
      }
    }
    return true;
  }

  /** The account in this slot logged out: its choice goes too. */
  @AnyThread
  static void forget (int accountId) {
    UI.post(() -> {
      cache.remove(accountId);
      try {
        Settings.instance().pmc().remove(KEY_PREFIX + accountId);
      } catch (Throwable t) {
        Log.e(XgramActivity.TAG, "Cannot forget the chosen chats", t);
      }
    });
  }

  /** Sorted, without zeros or repeats, at most {@link #MAX_CHATS}. */
  @NonNull
  private static long[] normalize (@Nullable long[] chats) {
    if (chats == null || chats.length == 0) {
      return NONE;
    }
    long[] sorted = chats.clone();
    Arrays.sort(sorted);
    int count = 0;
    for (int i = 0; i < sorted.length && count < MAX_CHATS; i++) {
      final long chatId = sorted[i];
      if (chatId != 0 && (count == 0 || sorted[count - 1] != chatId)) {
        sorted[count++] = chatId;
      }
    }
    return count == 0 ? NONE : Arrays.copyOf(sorted, count);
  }
}
