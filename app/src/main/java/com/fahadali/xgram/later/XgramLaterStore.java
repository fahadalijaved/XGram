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
package com.fahadali.xgram.later;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.leveldb.LevelDB;

/**
 * The single source of truth of Later: every record in LevelDB (key {@link XgramLaterRecord#key}, JSON value) and an
 * immutable index per account that the chat list reads without locks. Writes run under {@link #LOCK}: the LevelDB put,
 * then a new index replaces the old one. Nothing is removed implicitly except by {@link #prune}.
 */
final class XgramLaterStore {
  private XgramLaterStore () { }

  static final String TAG = "XGramLater";
  static final Object LOCK = new Object();

  private static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;
  private static final int RETENTION_COUNT = 200;

  /** key → record, all accounts. Guarded by LOCK. */
  private static final Map<String, XgramLaterRecord> records = new HashMap<>();
  /** account id → immutable index, replaced on every change of that account. */
  private static final ConcurrentHashMap<Integer, AccountIndex> indexes = new ConcurrentHashMap<>();
  private static volatile boolean loaded;

  /** What the chat list and the hot paths read: built once per change, never modified. */
  static final class AccountIndex {
    static final AccountIndex EMPTY = new AccountIndex(-1, Collections.emptyList());

    final int accountId;

    /** Every record of the account. */
    final Map<String, XgramLaterRecord> byKey;
    /** chat id → its pending snooze. */
    final Map<Long, XgramLaterRecord> chatSnooze;
    /** chat id → number of due records not seen yet (the bell). */
    final Map<Long, Integer> bells;
    /** chat id → the newest due time among its bells. */
    final Map<Long, Long> raised;
    /** Chats whose new messages may change something: a wake-capable snooze, or a follow-up. */
    final Set<Long> watchers;
    /** Chats with any record: deleted messages are only looked at there. */
    final Set<Long> chats;
    /** The earliest wake time of a pending timed record, or 0. */
    final long nextWakeAt;

    AccountIndex (int accountId, Iterable<XgramLaterRecord> source) {
      this.accountId = accountId;
      Map<String, XgramLaterRecord> byKey = new HashMap<>();
      Map<Long, XgramLaterRecord> chatSnooze = new HashMap<>();
      Map<Long, Integer> bells = new HashMap<>();
      Map<Long, Long> raised = new HashMap<>();
      Set<Long> watchers = new HashSet<>();
      Set<Long> chats = new HashSet<>();
      long nextWakeAt = 0;
      for (XgramLaterRecord r : source) {
        byKey.put(r.key, r);
        chats.add(r.chatId);
        if (r.isPending() && r.isTimed() && (nextWakeAt == 0 || r.wakeAt < nextWakeAt)) {
          nextWakeAt = r.wakeAt;
        }
        if (r.isSnooze() && r.isPending()) {
          chatSnooze.put(r.chatId, r);
          if (r.wakeMode != XgramLaterRecord.WAKE_TIME_ONLY) {
            watchers.add(r.chatId);
          }
        }
        if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && (r.isPending() || r.isDue())) {
          watchers.add(r.chatId);
        }
        if (r.hasBell()) {
          Integer count = bells.get(r.chatId);
          bells.put(r.chatId, count != null ? count + 1 : 1);
          Long at = raised.get(r.chatId);
          if (at == null || r.dueAt > at) {
            raised.put(r.chatId, r.dueAt);
          }
        }
      }
      this.byKey = Collections.unmodifiableMap(byKey);
      this.chatSnooze = Collections.unmodifiableMap(chatSnooze);
      this.bells = Collections.unmodifiableMap(bells);
      this.raised = Collections.unmodifiableMap(raised);
      this.watchers = Collections.unmodifiableSet(watchers);
      this.chats = Collections.unmodifiableSet(chats);
      this.nextWakeAt = nextWakeAt;
    }

    /** Records of one chat (its snooze and its messages'). */
    List<XgramLaterRecord> ofChat (long chatId) {
      if (!chats.contains(chatId)) {
        return Collections.emptyList();
      }
      List<XgramLaterRecord> list = new ArrayList<>();
      for (XgramLaterRecord r : byKey.values()) {
        if (r.chatId == chatId) {
          list.add(r);
        }
      }
      return list;
    }
  }

  // Loading

  /** Loads every account's records once per process. The first caller (any thread) waits for it. */
  static void ensureLoaded () {
    if (loaded) {
      return;
    }
    synchronized (LOCK) {
      if (loaded) {
        return;
      }
      List<String> keys = new ArrayList<>();
      try {
        for (LevelDB.Entry entry : Settings.instance().pmc().find(XgramLaterRecord.PREFIX)) {
          keys.add(entry.key());
        }
      } catch (Throwable t) {
        Log.e(TAG, "Cannot list the records", t);
      }
      Set<Integer> accounts = new HashSet<>();
      for (String key : keys) {
        long[] parts = XgramLaterRecord.parseKey(key);
        if (parts == null) {
          Log.w(TAG, "Skipped a record with an unknown key: " + key);
          continue;
        }
        String json = Settings.instance().getString(key, null);
        XgramLaterRecord r = XgramLaterRecord.fromJson((int) parts[0], parts[1], parts[2], json);
        if (r == null) {
          // Unreadable or from a newer version: kept as it is, never deleted
          Log.w(TAG, "Skipped a record that can't be read: " + key);
          continue;
        }
        records.put(r.key, r);
        accounts.add(r.accountId);
      }
      for (int accountId : accounts) {
        rebuildLocked(accountId);
      }
      loaded = true;
      countTdlibThreadAccess(keys.size() + 1, "load");
    }
  }

  static boolean isLoaded () {
    return loaded;
  }

  // Debug builds: LevelDB work that ran on the TDLib thread (it belongs on the Later thread), counted and logged

  private static int tdlibThreadAccess;

  static int tdlibThreadAccess () {
    return tdlibThreadAccess;
  }

  private static void countTdlibThreadAccess (int count, String what) {
    if (org.thunderdog.challegram.BuildConfig.DEBUG && "TDLib thread".equals(Thread.currentThread().getName())) {
      tdlibThreadAccess += count;
      Log.w(TAG, "LevelDB on the TDLib thread: " + what + " (" + tdlibThreadAccess + " so far)");
    }
  }

  // Reading (no locks once loaded)

  @NonNull
  static AccountIndex snapshot (int accountId) {
    ensureLoaded();
    AccountIndex index = indexes.get(accountId);
    return index != null ? index : AccountIndex.EMPTY;
  }

  /** The indexes of every account with records. */
  static List<AccountIndex> allSnapshots () {
    ensureLoaded();
    return new ArrayList<>(indexes.values());
  }

  @Nullable
  static XgramLaterRecord get (int accountId, String key) {
    return snapshot(accountId).byKey.get(key);
  }

  // Writing (under LOCK)

  /** Stores {@code r} (replacing the record under its key) and rebuilds its account's index. Caller holds LOCK. */
  static void putLocked (XgramLaterRecord r) {
    records.put(r.key, r);
    countTdlibThreadAccess(1, "put");
    try {
      Settings.instance().putString(r.key, r.toJson());
    } catch (Throwable t) {
      Log.e(TAG, "Cannot store " + r.key, t);
    }
  }

  /** Caller holds LOCK. */
  static void removeLocked (String key) {
    records.remove(key);
    countTdlibThreadAccess(1, "remove");
    try {
      Settings.instance().remove(key);
    } catch (Throwable t) {
      Log.e(TAG, "Cannot remove " + key, t);
    }
  }

  /** Caller holds LOCK. */
  @Nullable
  static XgramLaterRecord getLocked (String key) {
    return records.get(key);
  }

  /** A new index for the account, after its records changed. Caller holds LOCK. */
  static void rebuildLocked (int accountId) {
    List<XgramLaterRecord> list = new ArrayList<>();
    for (XgramLaterRecord r : records.values()) {
      if (r.accountId == accountId) {
        list.add(r);
      }
    }
    if (list.isEmpty()) {
      indexes.remove(accountId);
    } else {
      indexes.put(accountId, new AccountIndex(accountId, list));
    }
  }

  /**
   * Removes done and dismissed records older than 30 days, or beyond the newest 200 of an account. Pending and due
   * records are never removed. Returns how many went.
   */
  static int prune (long now) {
    ensureLoaded();
    int removed = 0;
    synchronized (LOCK) {
      Map<Integer, List<XgramLaterRecord>> byAccount = new HashMap<>();
      for (XgramLaterRecord r : records.values()) {
        List<XgramLaterRecord> list = byAccount.get(r.accountId);
        if (list == null) {
          byAccount.put(r.accountId, list = new ArrayList<>());
        }
        list.add(r);
      }
      for (Map.Entry<Integer, List<XgramLaterRecord>> entry : byAccount.entrySet()) {
        List<XgramLaterRecord> prunable = pruneSelection(entry.getValue(), now);
        for (XgramLaterRecord r : prunable) {
          removeLocked(r.key);
          removed++;
        }
        if (!prunable.isEmpty()) {
          rebuildLocked(entry.getKey());
        }
      }
    }
    return removed;
  }

  /**
   * What {@link #prune} removes of one account's records: done and dismissed ones older than 30 days or beyond the
   * newest 200 of them, never a pending or due one. Pure (the self-test runs it).
   */
  static List<XgramLaterRecord> pruneSelection (List<XgramLaterRecord> accountRecords, long now) {
    List<XgramLaterRecord> done = new ArrayList<>();
    for (XgramLaterRecord r : accountRecords) {
      if (r.isDone() || r.kind == XgramLaterRecord.KIND_DISMISS) {
        done.add(r);
      }
    }
    Collections.sort(done, (a, b) -> Long.compare(doneTime(b), doneTime(a)));
    List<XgramLaterRecord> selected = new ArrayList<>();
    for (int i = 0; i < done.size(); i++) {
      XgramLaterRecord r = done.get(i);
      if (r.isPending() || r.isDue()) {
        continue;
      }
      if (i >= RETENTION_COUNT || now - doneTime(r) > RETENTION_MS) {
        selected.add(r);
      }
    }
    return selected;
  }

  private static long doneTime (XgramLaterRecord r) {
    return r.doneAt != 0 ? r.doneAt : r.createdAt;
  }

  /** Removes the records of done items of an account ("Clear done"). Returns the removed records. */
  static List<XgramLaterRecord> clearDoneLocked (int accountId) {
    List<XgramLaterRecord> removed = new ArrayList<>();
    for (XgramLaterRecord r : new ArrayList<>(records.values())) {
      if (r.accountId == accountId && r.isDone()) {
        removeLocked(r.key);
        removed.add(r);
      }
    }
    rebuildLocked(accountId);
    return removed;
  }

  /** The account logged out: its records are gone from LevelDB (XgramAccountData); forget them in memory too. */
  static List<XgramLaterRecord> forgetAccount (int accountId) {
    ensureLoaded();
    List<XgramLaterRecord> removed = new ArrayList<>();
    synchronized (LOCK) {
      for (XgramLaterRecord r : new ArrayList<>(records.values())) {
        if (r.accountId == accountId) {
          records.remove(r.key);
          removed.add(r);
        }
      }
      try {
        Settings.instance().pmc().removeByPrefix(XgramLaterRecord.PREFIX + accountId + "_");
      } catch (Throwable t) {
        Log.e(TAG, "Cannot erase the account's records", t);
      }
      indexes.remove(accountId);
    }
    return removed;
  }

  // Backup

  /** Pending and due records of an account, for the per-user backup. */
  static JSONArray exportAccount (int accountId) {
    JSONArray array = new JSONArray();
    for (XgramLaterRecord r : snapshot(accountId).byKey.values()) {
      if ((r.isPending() || r.isDue()) && r.isVisibleItem()) {
        try {
          array.put(new JSONObject(r.toJson()));
        } catch (Throwable ignored) { }
      }
    }
    return array;
  }

  /**
   * Adds the backed up records missing here to the account (local records win). Overdue pending ones come back due
   * and silent. Returns the added records.
   */
  static List<XgramLaterRecord> importAccountLocked (int accountId, @Nullable JSONArray array, long now) {
    List<XgramLaterRecord> added = new ArrayList<>();
    if (array == null) {
      return added;
    }
    for (int i = 0; i < array.length(); i++) {
      JSONObject o = array.optJSONObject(i);
      if (o == null) {
        continue;
      }
      long chatId = o.optLong("chat", 0);
      long messageId = o.optLong("msg", -1);
      if (chatId == 0 || messageId < 0) {
        continue;
      }
      XgramLaterRecord parsed = XgramLaterRecord.fromJsonObject(accountId, chatId, messageId, o);
      XgramLaterRecord r = importConversion(parsed, parsed != null && records.containsKey(parsed.key), now);
      if (r == null) {
        continue;
      }
      putLocked(r);
      added.add(r);
    }
    if (!added.isEmpty()) {
      rebuildLocked(accountId);
    }
    return added;
  }

  /**
   * A backed up record as it is imported, or null when it isn't: a record already here wins, and only pending and due
   * ones come. An overdue pending one comes back due and silent. Pure (the self-test runs it).
   */
  @Nullable
  static XgramLaterRecord importConversion (@Nullable XgramLaterRecord r, boolean existsHere, long now) {
    if (r == null || existsHere || !(r.isPending() || r.isDue())) {
      return null;
    }
    if (r.isPending() && r.isTimed() && r.wakeAt <= now) {
      return r.buildUpon()
        .state(XgramLaterRecord.STATE_DUE)
        .dueAt(now)
        .addFlags(XgramLaterRecord.FLAG_SILENT)
        .build();
    }
    return r;
  }
}
