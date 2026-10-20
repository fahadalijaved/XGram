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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.reference.ReferenceList;

/**
 * Activity's own store (xgram_activity.db, as XgramHistory has its own): the items of every account, the people who
 * reacted, the sync cursors, the flood wait and the hidden chats. One thread ("XgramActivity") does all reading and
 * writing: events from the TDLib thread and user actions from the UI thread are queued and applied in order (FIFO,
 * so a new message is always in before the counter update that follows it), a batch per transaction.
 * <p>
 * An item's state only goes forward (New, Read, Cleared) except by the user (Mark as unread, Undo) and when a new
 * person reacts while it is shown live: then a reaction row is New again. Cleared rows stay as tombstones (without
 * their text after Clear Activity), so a later search never brings them back.
 */
public final class XgramActivityStore {
  static final String TAG = "XGramActivity";

  /** Nothing older than this is stored; Read and Cleared rows go once they are this old. */
  public static final long HORIZON_SECONDS = 90L * 24L * 60L * 60L;
  static final int MAX_REACTORS = 50;
  /** Per account: the oldest Read and Cleared rows go above this many rows, New ones only above {@link #NEW_ROW_CAP}. */
  static final int ROW_CAP = 10_000, NEW_ROW_CAP = 12_000;
  /** Saved text, UTF-16 units. */
  static final int TEXT_MAX = 300;
  private static final long PRUNE_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);
  private static final long NOTIFY_DELAY_MS = 250L;

  private static volatile XgramActivityStore instance;

  public static XgramActivityStore instance () {
    if (instance == null) {
      synchronized (XgramActivityStore.class) {
        if (instance == null) {
          instance = new XgramActivityStore(AppContext.get());
        }
      }
    }
    return instance;
  }

  /** The store's thread: SQLite, the state machine, and the paced sync (XgramActivitySync) all run here. */
  final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "XgramActivity");
    thread.setPriority(Thread.MIN_PRIORITY);
    return thread;
  });

  @Nullable private final Helper helper;

  private XgramActivityStore (Context context) {
    Helper h = null;
    try {
      h = new Helper(context.getApplicationContext());
    } catch (Throwable t) {
      Log.e(TAG, "Cannot open the store", t);
    }
    this.helper = h;
    executor.execute(this::onOpen);
    executor.scheduleWithFixedDelay(() -> {
      SQLiteDatabase db = db();
      if (db != null) {
        prune(db);
      }
    }, PRUNE_INTERVAL_MS, PRUNE_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  @Nullable
  @WorkerThread
  SQLiteDatabase db () {
    if (helper == null) {
      return null;
    }
    try {
      return helper.getWritableDatabase();
    } catch (Throwable t) {
      Log.e(TAG, "Cannot open the database", t);
      return null;
    }
  }

  @WorkerThread
  private void onOpen () {
    SQLiteDatabase db = db();
    if (db == null) {
      return;
    }
    prune(db);
    List<Integer> accounts = new ArrayList<>();
    try (Cursor c = db.rawQuery("SELECT DISTINCT account FROM items UNION SELECT DISTINCT account FROM hidden", null)) {
      while (c.moveToNext()) {
        accounts.add(c.getInt(0));
      }
    } catch (Throwable t) {
      Log.e(TAG, "Cannot read accounts", t);
    }
    Set<Integer> touched = new HashSet<>(accounts);
    for (int accountId : accounts) {
      rebuildSummary(db, accountId);
    }
    if (!touched.isEmpty()) {
      scheduleNotify(touched);
    }
    if (org.thunderdog.challegram.BuildConfig.DEBUG) {
      try {
        XgramActivitySelfTest.run(this);
      } catch (Throwable t) {
        Log.e(TAG, "Self test crashed", t);
      }
    }
  }

  // Epochs: an account's events made before it logged out (or its data was erased) are dropped.

  private final ConcurrentHashMap<Integer, Integer> epochs = new ConcurrentHashMap<>();

  int epochOf (int accountId) {
    Integer epoch = epochs.get(accountId);
    return epoch != null ? epoch : 0;
  }

  // Event queue

  interface DbAction {
    void run (SQLiteDatabase db) throws Exception;
  }

  private static final class Pending {
    final int accountId, epoch;
    final DbAction action;

    Pending (int accountId, int epoch, DbAction action) {
      this.accountId = accountId;
      this.epoch = epoch;
      this.action = action;
    }
  }

  private final ConcurrentLinkedQueue<Pending> queue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean drainPosted = new AtomicBoolean();

  /** Any thread: {@code action} runs on the store thread, in order, in the next batch's transaction. */
  void post (int accountId, DbAction action) {
    queue.add(new Pending(accountId, epochOf(accountId), action));
    if (drainPosted.compareAndSet(false, true)) {
      executor.execute(this::drain);
    }
  }

  @WorkerThread
  private void drain () {
    drainPosted.set(false);
    List<Pending> batch = new ArrayList<>();
    Pending pending;
    while (batch.size() < 500 && (pending = queue.poll()) != null) {
      batch.add(pending);
    }
    if (!queue.isEmpty() && drainPosted.compareAndSet(false, true)) {
      executor.execute(this::drain);
    }
    if (batch.isEmpty()) {
      return;
    }
    SQLiteDatabase db = db();
    if (db == null) {
      return;
    }
    Set<Integer> touched = new HashSet<>();
    try {
      db.beginTransaction();
      try {
        for (Pending p : batch) {
          if (p.epoch != epochOf(p.accountId)) {
            continue; // made before the account's data was erased
          }
          try {
            // Only an action that changed rows tells the screens and badges (counter checks and repeated reads don't)
            final long changesBefore = totalChanges(db);
            p.action.run(db);
            if (totalChanges(db) != changesBefore) {
              touched.add(p.accountId);
            }
          } catch (Throwable t) {
            Log.e(TAG, "Event failed", t);
          }
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    } catch (Throwable t) {
      Log.e(TAG, "Batch failed", t);
    }
    for (int accountId : touched) {
      rebuildSummary(db, accountId);
    }
    if (!touched.isEmpty()) {
      scheduleNotify(touched);
    }
  }

  /** Rows inserted, updated or deleted on this connection so far (the batch runs in one transaction, on one connection). */
  private static long totalChanges (SQLiteDatabase db) {
    return android.database.DatabaseUtils.longForQuery(db, "SELECT total_changes()", null);
  }

  // Change notification: debounced, then once on the UI thread

  public interface ChangeListener {
    @UiThread
    void onActivityChanged (int accountId);
  }

  private final ReferenceList<ChangeListener> listeners = new ReferenceList<>();
  private final Set<Integer> pendingNotify = new HashSet<>(); // store thread
  private boolean notifyScheduled; // store thread

  /** UI thread. Held weakly: keep a reference to {@code listener}. */
  public void addListener (ChangeListener listener) {
    listeners.add(listener);
  }

  public void removeListener (ChangeListener listener) {
    listeners.remove(listener);
  }

  @WorkerThread
  private void scheduleNotify (Set<Integer> accounts) {
    pendingNotify.addAll(accounts);
    if (!notifyScheduled) {
      notifyScheduled = true;
      executor.schedule(this::flushNotify, NOTIFY_DELAY_MS, TimeUnit.MILLISECONDS);
    }
  }

  /** Store thread: the next notification also tells about {@code accountId} (sync status changes). */
  @WorkerThread
  void notifyChanged (int accountId) {
    scheduleNotify(Collections.singleton(accountId));
  }

  @WorkerThread
  private void flushNotify () {
    notifyScheduled = false;
    if (pendingNotify.isEmpty()) {
      return;
    }
    final int[] accounts = new int[pendingNotify.size()];
    int i = 0;
    for (int accountId : pendingNotify) {
      accounts[i++] = accountId;
    }
    pendingNotify.clear();
    UI.post(() -> {
      for (int accountId : accounts) {
        for (ChangeListener listener : listeners) {
          try {
            listener.onActivityChanged(accountId);
          } catch (Throwable t) {
            Log.e(TAG, "Listener failed", t);
          }
        }
      }
    });
  }

  // Summary: what the TDLib thread and the UI read without asking the database

  /** New items of a (chat, topic, kind) for the badge and the chip counts. */
  public static final class Entry {
    public final long chatId;
    public final int topicId, kind;
    /** A discussion group or the Replies chat: follows Channel comments. */
    public final boolean discussion;
    /** Of {@link #count}, mentions that are also replies (counted under Replies too). */
    public final int alsoReplyCount;
    /** Of {@link #count}, the ones the user marked unread (read on the server, so TDLib doesn't count them). */
    public final int userUnreadCount;
    public final int count;

    Entry (long chatId, int topicId, int kind, boolean discussion, int alsoReplyCount, int userUnreadCount, int count) {
      this.chatId = chatId;
      this.topicId = topicId;
      this.kind = kind;
      this.discussion = discussion;
      this.alsoReplyCount = alsoReplyCount;
      this.userUnreadCount = userUnreadCount;
      this.count = count;
    }
  }

  /** Paused sync, as the status tile shows it. */
  public static final class SyncStatus {
    public static final SyncStatus NONE = new SyncStatus(0, 0, 0, false, false);
    public final int done, total;
    /** Wall clock ms until which Telegram asked to wait, 0 when it did not. */
    public final long pausedUntil;
    public final boolean offline, running;

    public SyncStatus (int done, int total, long pausedUntil, boolean offline, boolean running) {
      this.done = done;
      this.total = total;
      this.pausedUntil = pausedUntil;
      this.offline = offline;
      this.running = running;
    }

    public boolean isPaused () {
      return pausedUntil > System.currentTimeMillis();
    }
  }

  /** Immutable; published through {@link #summaries} after every batch. */
  public static final class Summary {
    public final int accountId;
    @NonNull public final Entry[] entries;
    /** Sorted. */
    private final long[] knownChats, newChats, hiddenChats;
    @NonNull public final SyncStatus sync;
    public final boolean hasAny;

    Summary (int accountId, @NonNull Entry[] entries, long[] knownChats, long[] newChats, long[] hiddenChats, @NonNull SyncStatus sync, boolean hasAny) {
      this.accountId = accountId;
      this.entries = entries;
      this.knownChats = knownChats;
      this.newChats = newChats;
      this.hiddenChats = hiddenChats;
      this.sync = sync;
      this.hasAny = hasAny;
    }

    Summary withSync (@NonNull SyncStatus sync) {
      return new Summary(accountId, entries, knownChats, newChats, hiddenChats, sync, hasAny);
    }

    /** Any item (of any state) of this chat. No allocation: called for every message viewed in ghost mode. */
    public boolean knows (long chatId) {
      return Arrays.binarySearch(knownChats, chatId) >= 0;
    }

    public boolean chatHasNew (long chatId) {
      return Arrays.binarySearch(newChats, chatId) >= 0;
    }

    public boolean isHidden (long chatId) {
      return Arrays.binarySearch(hiddenChats, chatId) >= 0;
    }

    public int hiddenCount () {
      return hiddenChats.length;
    }

    /** New items of the chat in {@code kindMask} (XgramActivityItem.MASK_*). */
    public int newCount (long chatId, int kindMask) {
      int count = 0;
      for (Entry entry : entries) {
        if (entry.chatId == chatId && (kindMask & (1 << entry.kind)) != 0) {
          count += entry.count;
        }
      }
      return count;
    }

    /** {@link #newCount} without the ones the user marked unread: what TDLib's counter of the chat should match. */
    public int syncCount (long chatId, int kindMask) {
      int count = 0;
      for (Entry entry : entries) {
        if (entry.chatId == chatId && (kindMask & (1 << entry.kind)) != 0) {
          count += entry.count - entry.userUnreadCount;
        }
      }
      return count;
    }

    /** The chats with New items, sorted. Shared: not to be changed. */
    long[] chatsWithNew () {
      return newChats;
    }

    /** The chats with items of any state, sorted. Shared: not to be changed. */
    long[] knownChats () {
      return knownChats;
    }
  }

  private static final Summary EMPTY = new Summary(-1, new Entry[0], new long[0], new long[0], new long[0], SyncStatus.NONE, false);
  private final ConcurrentHashMap<Integer, Summary> summaries = new ConcurrentHashMap<>();

  /** Any thread. */
  @NonNull
  public Summary summary (int accountId) {
    Summary summary = summaries.get(accountId);
    return summary != null ? summary : EMPTY;
  }

  /** Store thread: the sync status of {@code accountId} changed. */
  @WorkerThread
  void setSyncStatus (int accountId, @NonNull SyncStatus status) {
    Summary current = summaries.get(accountId);
    if (current == null) {
      current = new Summary(accountId, new Entry[0], new long[0], new long[0], new long[0], status, false);
    } else {
      current = current.withSync(status);
    }
    summaries.put(accountId, current);
    notifyChanged(accountId);
  }

  @WorkerThread
  void rebuildSummary (SQLiteDatabase db, int accountId) {
    try {
      Summary summary = buildSummary(db, accountId);
      Summary old = summaries.get(accountId);
      summaries.put(accountId, old != null ? summary.withSync(old.sync) : summary);
    } catch (Throwable t) {
      Log.e(TAG, "Cannot build the summary", t);
    }
  }

  @WorkerThread
  static Summary buildSummary (SQLiteDatabase db, int accountId) {
    String[] args = {String.valueOf(accountId)};
    List<Entry> entries = new ArrayList<>();
    Set<Long> newChats = new HashSet<>();
    try (Cursor c = db.rawQuery("SELECT chat, topic, kind, (flags & 24) != 0, SUM((flags & 4) != 0), SUM((flags & 64) != 0), COUNT(*) FROM items " +
      "WHERE account = ? AND state = 0 GROUP BY chat, topic, kind, (flags & 24) != 0", args)) {
      while (c.moveToNext()) {
        entries.add(new Entry(c.getLong(0), c.getInt(1), c.getInt(2), c.getInt(3) != 0, c.getInt(4), c.getInt(5), c.getInt(6)));
        newChats.add(c.getLong(0));
      }
    }
    List<Long> known = new ArrayList<>();
    try (Cursor c = db.rawQuery("SELECT DISTINCT chat FROM items WHERE account = ?", args)) {
      while (c.moveToNext()) {
        known.add(c.getLong(0));
      }
    }
    List<Long> hidden = new ArrayList<>();
    try (Cursor c = db.rawQuery("SELECT chat FROM hidden WHERE account = ?", args)) {
      while (c.moveToNext()) {
        hidden.add(c.getLong(0));
      }
    }
    boolean hasAny;
    try (Cursor c = db.rawQuery("SELECT 1 FROM items WHERE account = ? AND state != 2 LIMIT 1", args)) {
      hasAny = c.moveToFirst();
    }
    return new Summary(accountId, entries.toArray(new Entry[0]), sorted(known), sorted(newChats), sorted(hidden), SyncStatus.NONE, hasAny);
  }

  private static long[] sorted (java.util.Collection<Long> values) {
    long[] array = new long[values.size()];
    int i = 0;
    for (long value : values) {
      array[i++] = value;
    }
    Arrays.sort(array);
    return array;
  }

  // SQL helpers

  static long nowSeconds () {
    return System.currentTimeMillis() / 1000L;
  }

  static String kindsSql (int kindMask) {
    StringBuilder b = new StringBuilder("kind IN (");
    boolean first = true;
    for (int kind = XgramActivityItem.KIND_MENTION; kind <= XgramActivityItem.KIND_REACTION; kind++) {
      if ((kindMask & (1 << kind)) != 0) {
        if (!first) {
          b.append(',');
        }
        b.append(kind);
        first = false;
      }
    }
    if (first) {
      b.append("0");
    }
    return b.append(')').toString();
  }

  private static String[] keyArgs (int accountId, long chatId, long messageId, int kind) {
    return new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(messageId), String.valueOf(kind)};
  }

  /** The state of an item, or -1 when there is none. */
  static int stateOf (SQLiteDatabase db, int accountId, long chatId, long messageId, int kind) {
    try (Cursor c = db.rawQuery("SELECT state FROM items WHERE account = ? AND chat = ? AND msg = ? AND kind = ?", keyArgs(accountId, chatId, messageId, kind))) {
      return c.moveToFirst() ? c.getInt(0) : -1;
    }
  }

  // Writes (store thread, inside the batch's transaction). Static over a database, so the self test runs them too.

  /** What a collected message (or found one) is, as it is saved. */
  static final class Snapshot {
    int accountId;
    long chatId, messageId;
    int kind;
    int topicId;
    long albumId;
    long sender;
    String senderName, chatTitle, topicTitle;
    int messageDate, eventDate;
    int state = XgramActivityItem.STATE_NEW;
    int flags;
    String text;
    int mentionFrom = -1, mentionLength;
    long replyChatId, replyMessageId;
    String quoteText;
    int source = XgramActivityItem.SOURCE_LIVE;
  }

  /** Flags a later upsert may add to an existing item (the others are state). */
  private static final int ADDITIVE_FLAGS = XgramActivityItem.FLAG_ALSO_REPLY | XgramActivityItem.FLAG_DISCUSSION |
    XgramActivityItem.FLAG_REPLIES_CHAT | XgramActivityItem.FLAG_PRIVATE;

  /**
   * Inserts an item, or fills in what an existing one does not have yet (its state never changes here). A message is
   * one item as a mention or a reply: whichever came first stays.
   */
  static void upsert (SQLiteDatabase db, Snapshot s) {
    long now = nowSeconds();
    if (s.eventDate <= 0) {
      s.eventDate = (int) now;
    }
    if (s.eventDate < now - HORIZON_SECONDS) {
      return; // older than what is kept: a pruned tombstone must not come back
    }
    int existingKind = 0;
    if (s.kind == XgramActivityItem.KIND_MENTION || s.kind == XgramActivityItem.KIND_REPLY) {
      try (Cursor c = db.rawQuery("SELECT kind FROM items WHERE account = ? AND chat = ? AND msg = ? AND kind IN (1,2) LIMIT 1",
        new String[] {String.valueOf(s.accountId), String.valueOf(s.chatId), String.valueOf(s.messageId)})) {
        if (c.moveToFirst()) {
          existingKind = c.getInt(0);
        }
      }
    } else if (stateOf(db, s.accountId, s.chatId, s.messageId, s.kind) != -1) {
      existingKind = s.kind;
    }
    if (existingKind != 0) {
      fillSnapshot(db, s, existingKind);
      return;
    }
    ContentValues v = new ContentValues();
    v.put("account", s.accountId);
    v.put("chat", s.chatId);
    v.put("msg", s.messageId);
    v.put("kind", s.kind);
    v.put("topic", s.topicId);
    v.put("album", s.albumId);
    v.put("sender", s.sender);
    v.put("sender_name", s.senderName);
    v.put("chat_title", s.chatTitle);
    v.put("topic_title", s.topicTitle);
    v.put("msg_date", s.messageDate);
    v.put("event_date", s.eventDate);
    v.put("state", s.state);
    v.put("flags", s.flags);
    v.put("text", s.text);
    v.put("mention_from", s.mentionFrom);
    v.put("mention_len", s.mentionLength);
    v.put("reply_chat", s.replyChatId);
    v.put("reply_msg", s.replyMessageId);
    v.put("quote_text", s.quoteText);
    v.put("source", s.source);
    v.put("updated", System.currentTimeMillis());
    db.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  /** Fills the empty parts of an existing item (not a Cleared one: Clear Activity wiped its text on purpose). */
  static void fillSnapshot (SQLiteDatabase db, Snapshot s, int kind) {
    db.execSQL("UPDATE items SET text = COALESCE(text, ?), sender_name = COALESCE(sender_name, ?), chat_title = COALESCE(chat_title, ?), " +
        "topic_title = COALESCE(topic_title, ?), quote_text = COALESCE(quote_text, ?), " +
        "msg_date = CASE WHEN msg_date = 0 THEN ? ELSE msg_date END, " +
        "reply_chat = CASE WHEN reply_msg = 0 THEN ? ELSE reply_chat END, reply_msg = CASE WHEN reply_msg = 0 THEN ? ELSE reply_msg END, " +
        "mention_len = CASE WHEN mention_from < 0 THEN ? ELSE mention_len END, mention_from = CASE WHEN mention_from < 0 THEN ? ELSE mention_from END, " +
        "album = CASE WHEN album = 0 THEN ? ELSE album END, sender = CASE WHEN sender = 0 THEN ? ELSE sender END, " +
        "flags = flags | ?, updated = ? " +
        "WHERE account = ? AND chat = ? AND msg = ? AND kind = ? AND state != 2",
      new Object[] {s.text, s.senderName, s.chatTitle, s.topicTitle, s.quoteText,
        s.messageDate, s.replyChatId, s.replyMessageId, s.mentionLength, s.mentionFrom,
        s.albumId, s.sender, s.flags & ADDITIVE_FLAGS, System.currentTimeMillis(),
        s.accountId, s.chatId, s.messageId, kind});
  }

  /** The message was read (here or elsewhere): its items are Read, a Mark as unread of them included. */
  static void markRead (SQLiteDatabase db, int accountId, long chatId, long messageId, int kindMask, boolean local) {
    db.execSQL("UPDATE items SET state = 1, flags = (flags | ?) & ~64, updated = ? WHERE account = ? AND chat = ? AND msg = ? AND state = 0 AND " + kindsSql(kindMask),
      new Object[] {local ? XgramActivityItem.FLAG_READ_LOCAL : 0, System.currentTimeMillis(), accountId, chatId, messageId});
  }

  static void markReadAlbum (SQLiteDatabase db, int accountId, long chatId, long albumId, int kindMask, boolean local) {
    if (albumId == 0) {
      return;
    }
    db.execSQL("UPDATE items SET state = 1, flags = (flags | ?) & ~64, updated = ? WHERE account = ? AND chat = ? AND album = ? AND state = 0 AND " + kindsSql(kindMask),
      new Object[] {local ? XgramActivityItem.FLAG_READ_LOCAL : 0, System.currentTimeMillis(), accountId, chatId, albumId});
  }

  /**
   * A chat's counter went to 0: its New items in {@code kindMask} are read. Not the ones the user marked unread here,
   * and never Replies chat items (the Replies chat reads by its read position, {@link #markReadUpTo}).
   */
  static void markReadChat (SQLiteDatabase db, int accountId, long chatId, int kindMask) {
    markReadChat(db, accountId, chatId, kindMask, 0);
  }

  /** The same, only for items stored before {@code beforeMs} (0: any): newer ones may be ahead of their counter update. */
  static void markReadChat (SQLiteDatabase db, int accountId, long chatId, int kindMask, long beforeMs) {
    db.execSQL("UPDATE items SET state = 1, updated = ? WHERE account = ? AND chat = ? AND state = 0 AND (flags & 80) = 0 AND updated < ? AND " + kindsSql(kindMask),
      new Object[] {System.currentTimeMillis(), accountId, chatId, beforeMs > 0 ? beforeMs : Long.MAX_VALUE});
  }

  static void markReadUpTo (SQLiteDatabase db, int accountId, long chatId, long upToMessageId, int kindMask) {
    db.execSQL("UPDATE items SET state = 1, updated = ? WHERE account = ? AND chat = ? AND msg <= ? AND state = 0 AND (flags & 64) = 0 AND " + kindsSql(kindMask),
      new Object[] {System.currentTimeMillis(), accountId, chatId, upToMessageId});
  }

  /**
   * User actions on rows: {@code toState} for the rows that are in {@code fromState} (-1: any but Cleared). A read or
   * a clear ends a Mark as unread; {@code local} marks a read on this device only.
   */
  static void setState (SQLiteDatabase db, List<XgramActivityItem> items, int fromState, int toState, boolean local) {
    setState(db, items, fromState, toState, local ? XgramActivityItem.FLAG_READ_LOCAL : 0,
      toState == XgramActivityItem.STATE_NEW ? XgramActivityItem.FLAG_READ_LOCAL : XgramActivityItem.FLAG_USER_UNREAD);
  }

  static void setState (SQLiteDatabase db, List<XgramActivityItem> items, int fromState, int toState, int addFlags, int clearFlags) {
    final String where = fromState == -1 ? "state != 2" : "state = " + fromState;
    for (XgramActivityItem item : items) {
      db.execSQL("UPDATE items SET state = ?, flags = (flags & ~?) | ?, updated = ? WHERE account = ? AND chat = ? AND msg = ? AND kind = ? AND " + where,
        new Object[] {toState, clearFlags, addFlags, System.currentTimeMillis(), item.accountId, item.chatId, item.messageId, item.kind});
    }
  }

  /** Undo of a Clear: every row back to the state it had (the token's), a Mark as unread included. */
  static void restoreStates (SQLiteDatabase db, List<XgramActivityItem> items) {
    for (XgramActivityItem item : items) {
      db.execSQL("UPDATE items SET state = ?, flags = flags | ?, updated = ? WHERE account = ? AND chat = ? AND msg = ? AND kind = ? AND state = 2",
        new Object[] {item.state, item.state == XgramActivityItem.STATE_NEW ? item.flags & XgramActivityItem.FLAG_USER_UNREAD : 0,
          System.currentTimeMillis(), item.accountId, item.chatId, item.messageId, item.kind});
    }
  }

  static void deleteItem (SQLiteDatabase db, int accountId, long chatId, long messageId, int kind) {
    String[] args = keyArgs(accountId, chatId, messageId, kind);
    db.delete("items", "account = ? AND chat = ? AND msg = ? AND kind = ?", args);
    if (kind == XgramActivityItem.KIND_REACTION) {
      db.delete("reactors", "account = ? AND chat = ? AND msg = ?", new String[] {args[0], args[1], args[2]});
    }
  }

  static void markDeleted (SQLiteDatabase db, int accountId, long chatId, long[] messageIds) {
    for (long messageId : messageIds) {
      db.execSQL("UPDATE items SET flags = flags | 1, updated = ? WHERE account = ? AND chat = ? AND msg = ? AND (flags & 1) = 0",
        new Object[] {System.currentTimeMillis(), accountId, chatId, messageId});
    }
  }

  /** An edited message: its saved text (or, for your own message, the quote of the rows about it) follows. */
  static void refreshText (SQLiteDatabase db, int accountId, long chatId, long messageId, @Nullable String text) {
    if (text == null) {
      return;
    }
    db.execSQL("UPDATE items SET text = ? WHERE account = ? AND chat = ? AND msg = ? AND kind IN (1,2) AND state != 2",
      new Object[] {text, accountId, chatId, messageId});
    db.execSQL("UPDATE items SET quote_text = ? WHERE account = ? AND state != 2 AND ((chat = ? AND msg = ? AND kind = 3) OR " +
        "(reply_chat = ? AND reply_msg = ? AND kind IN (1,2)))",
      new Object[] {text, accountId, chatId, messageId, chatId, messageId});
  }

  /** The reactions to one of your messages TDLib reported (live) or a search found (sync). */
  static final class Reactions {
    int accountId;
    long chatId, messageId;
    int source;
    @NonNull List<XgramActivityItem.Reactor> reactors = new ArrayList<>();
    int messageDate;
    String quoteText, chatTitle;
    int topicId;
    int flags;
  }

  /**
   * Reactors are added; a live update with someone new makes the row New again (even a Read or Cleared one) and moves
   * it to the top. TDLib's list has the unread reactions only, so each reactor keeps whether it is still unread (a
   * read of the row ends that, see the reactors_read trigger): a live list without one of the unread ones means it was
   * taken back, and it goes; read ones stay. Search results never change a row's state, and a row they create starts
   * at the message's date.
   */
  static void reactions (SQLiteDatabase db, Reactions r) {
    final long now = nowSeconds();
    final boolean live = r.source == XgramActivityItem.SOURCE_LIVE;
    int state = stateOf(db, r.accountId, r.chatId, r.messageId, XgramActivityItem.KIND_REACTION);
    if (state == -1) {
      if (r.reactors.isEmpty()) {
        return;
      }
      int eventDate = live ? (int) now : (r.messageDate > 0 ? r.messageDate : (int) now);
      if (eventDate < now - HORIZON_SECONDS) {
        return;
      }
      ContentValues v = new ContentValues();
      v.put("account", r.accountId);
      v.put("chat", r.chatId);
      v.put("msg", r.messageId);
      v.put("kind", XgramActivityItem.KIND_REACTION);
      v.put("topic", r.topicId);
      v.put("chat_title", r.chatTitle);
      v.put("msg_date", r.messageDate);
      v.put("event_date", eventDate);
      v.put("state", XgramActivityItem.STATE_NEW);
      v.put("flags", r.flags & ADDITIVE_FLAGS);
      v.put("quote_text", r.quoteText);
      v.put("source", r.source);
      v.put("updated", System.currentTimeMillis());
      db.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    } else if (r.quoteText != null || r.messageDate > 0 || r.chatTitle != null) {
      db.execSQL("UPDATE items SET quote_text = COALESCE(quote_text, ?), chat_title = COALESCE(chat_title, ?), " +
          "msg_date = CASE WHEN msg_date = 0 THEN ? ELSE msg_date END WHERE account = ? AND chat = ? AND msg = ? AND kind = 3 AND state != 2",
        new Object[] {r.quoteText, r.chatTitle, r.messageDate, r.accountId, r.chatId, r.messageId});
    }
    if (r.reactors.isEmpty()) {
      return;
    }
    String[] itemArgs = {String.valueOf(r.accountId), String.valueOf(r.chatId), String.valueOf(r.messageId)};
    if (live) {
      // Taken back: the unread reactors the list no longer has go (read ones are not in TDLib's list at all)
      Set<String> listed = new HashSet<>();
      for (XgramActivityItem.Reactor reactor : r.reactors) {
        listed.add(reactor.sender + "|" + reactor.reaction);
      }
      List<String[]> gone = new ArrayList<>();
      try (Cursor c = db.rawQuery("SELECT sender, reaction FROM reactors WHERE account = ? AND chat = ? AND msg = ? AND unread != 0", itemArgs)) {
        while (c.moveToNext()) {
          if (!listed.contains(c.getLong(0) + "|" + c.getString(1))) {
            gone.add(new String[] {itemArgs[0], itemArgs[1], itemArgs[2], String.valueOf(c.getLong(0)), c.getString(1)});
          }
        }
      }
      for (String[] args : gone) {
        db.delete("reactors", "account = ? AND chat = ? AND msg = ? AND sender = ? AND reaction = ?", args);
      }
    }
    // Who TDLib lists is unread (again, for one read here only, as in ghost mode)
    for (XgramActivityItem.Reactor reactor : r.reactors) {
      db.execSQL("UPDATE reactors SET unread = 1 WHERE account = ? AND chat = ? AND msg = ? AND sender = ? AND reaction = ? AND unread = 0",
        new Object[] {r.accountId, r.chatId, r.messageId, reactor.sender, reactor.reaction});
    }
    int stored;
    try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM reactors WHERE account = ? AND chat = ? AND msg = ?", itemArgs)) {
      stored = c.moveToFirst() ? c.getInt(0) : 0;
    }
    int added = 0;
    for (XgramActivityItem.Reactor reactor : r.reactors) {
      if (stored >= MAX_REACTORS) {
        break;
      }
      ContentValues v = new ContentValues();
      v.put("account", r.accountId);
      v.put("chat", r.chatId);
      v.put("msg", r.messageId);
      v.put("sender", reactor.sender);
      v.put("reaction", reactor.reaction);
      v.put("is_big", reactor.isBig ? 1 : 0);
      v.put("seen", reactor.seen > 0 ? reactor.seen : (int) now);
      if (db.insertWithOnConflict("reactors", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) {
        added++;
        stored++;
      }
    }
    int extra = Math.max(0, r.reactors.size() - MAX_REACTORS);
    if (extra > 0) {
      db.execSQL("UPDATE items SET reactor_extra = MAX(reactor_extra, ?) WHERE account = ? AND chat = ? AND msg = ? AND kind = 3",
        new Object[] {extra, r.accountId, r.chatId, r.messageId});
    }
    if (live && added > 0) {
      // Unread on the server again: no longer only the user's Mark as unread
      db.execSQL("UPDATE items SET state = 0, flags = flags & ~64, event_date = ?, updated = ? WHERE account = ? AND chat = ? AND msg = ? AND kind = 3",
        new Object[] {(int) now, System.currentTimeMillis(), r.accountId, r.chatId, r.messageId});
    }
  }

  /**
   * After a search: New items of the chat in {@code kindMask} stored before the request went out ({@code sentAtMs})
   * that the results don't have are read elsewhere. A page covers the messages below {@code fromMessageId} (0: from the
   * newest); with an incomplete search only items at or after the oldest result ({@code minMessageId}) can be judged.
   * What the user marked unread here stays New (the server has it read, that is why it is missing).
   */
  static void reconcile (SQLiteDatabase db, int accountId, long chatId, int kindMask, long[] resultIds, long sentAtMs, boolean complete, long minMessageId, long fromMessageId) {
    StringBuilder sql = new StringBuilder("UPDATE items SET state = 1, updated = ? WHERE account = ? AND chat = ? AND state = 0 AND (flags & 64) = 0 AND updated < ? AND ")
      .append(kindsSql(kindMask));
    if (fromMessageId > 0) {
      sql.append(" AND msg < ").append(fromMessageId);
    }
    if (resultIds.length > 0) {
      sql.append(" AND msg NOT IN (");
      for (int i = 0; i < resultIds.length; i++) {
        if (i > 0) {
          sql.append(',');
        }
        sql.append(resultIds[i]);
      }
      sql.append(')');
    }
    if (!complete) {
      sql.append(" AND msg >= ").append(minMessageId);
    }
    db.execSQL(sql.toString(), new Object[] {System.currentTimeMillis(), accountId, chatId, sentAtMs});
  }

  /**
   * Clear Activity: every row becomes a tombstone without its text. Who reacted stays as ids only (no names, nothing
   * of the message): a later live update that lists the same people (one of them took theirs back, say) must not
   * bring the row back as if someone new had reacted.
   */
  static void clearAllRows (SQLiteDatabase db, int accountId) {
    db.execSQL("UPDATE items SET state = 2, flags = flags & ~64, text = NULL, quote_text = NULL, sender_name = NULL, chat_title = NULL, topic_title = NULL, reactor_extra = 0, updated = ? WHERE account = ?",
      new Object[] {System.currentTimeMillis(), accountId});
  }

  static void setHidden (SQLiteDatabase db, int accountId, long chatId, boolean hidden) {
    if (hidden) {
      ContentValues v = new ContentValues();
      v.put("account", accountId);
      v.put("chat", chatId);
      db.insertWithOnConflict("hidden", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    } else {
      db.delete("hidden", "account = ? AND chat = ?", new String[] {String.valueOf(accountId), String.valueOf(chatId)});
    }
  }

  @WorkerThread
  static void prune (SQLiteDatabase db) {
    try {
      long minDate = nowSeconds() - HORIZON_SECONDS;
      String[] args = {String.valueOf(minDate)};
      db.beginTransaction();
      try {
        db.execSQL("DELETE FROM reactors WHERE EXISTS (SELECT 1 FROM items i WHERE i.account = reactors.account AND i.chat = reactors.chat " +
          "AND i.msg = reactors.msg AND i.kind = 3 AND i.state != 0 AND i.event_date < ?)", args);
        db.delete("items", "state != 0 AND event_date < ?", args);
        List<Integer> accounts = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT account, COUNT(*) FROM items GROUP BY account HAVING COUNT(*) > " + ROW_CAP, null)) {
          while (c.moveToNext()) {
            accounts.add(c.getInt(0));
          }
        }
        for (int accountId : accounts) {
          String account = String.valueOf(accountId);
          int total = count(db, "SELECT COUNT(*) FROM items WHERE account = ?", account);
          if (total > ROW_CAP) {
            db.execSQL("DELETE FROM items WHERE rowid IN (SELECT rowid FROM items WHERE account = ? AND state != 0 ORDER BY event_date ASC LIMIT ?)",
              new Object[] {accountId, total - ROW_CAP});
          }
          total = count(db, "SELECT COUNT(*) FROM items WHERE account = ?", account);
          if (total > NEW_ROW_CAP) {
            db.execSQL("DELETE FROM items WHERE rowid IN (SELECT rowid FROM items WHERE account = ? ORDER BY event_date ASC LIMIT ?)",
              new Object[] {accountId, total - NEW_ROW_CAP});
          }
        }
        if (!accounts.isEmpty()) {
          db.execSQL("DELETE FROM reactors WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.account = reactors.account AND i.chat = reactors.chat " +
            "AND i.msg = reactors.msg AND i.kind = 3)");
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    } catch (Throwable t) {
      Log.e(TAG, "Prune failed", t);
    }
  }

  static int count (SQLiteDatabase db, String sql, String... args) {
    try (Cursor c = db.rawQuery(sql, args)) {
      return c.moveToFirst() ? c.getInt(0) : 0;
    }
  }

  // Public API: any thread, applied in order on the store thread

  void upsert (Snapshot s) {
    post(s.accountId, db -> upsert(db, s));
  }

  void markRead (int accountId, long chatId, long messageId, int kindMask, boolean local) {
    post(accountId, db -> markRead(db, accountId, chatId, messageId, kindMask, local));
  }

  void markReadAlbum (int accountId, long chatId, long albumId, int kindMask, boolean local) {
    post(accountId, db -> markReadAlbum(db, accountId, chatId, albumId, kindMask, local));
  }

  void markReadChat (int accountId, long chatId, int kindMask) {
    post(accountId, db -> markReadChat(db, accountId, chatId, kindMask));
  }

  void markReadChat (int accountId, long chatId, int kindMask, long beforeMs) {
    post(accountId, db -> markReadChat(db, accountId, chatId, kindMask, beforeMs));
  }

  void markReadUpTo (int accountId, long chatId, long upToMessageId, int kindMask) {
    post(accountId, db -> markReadUpTo(db, accountId, chatId, upToMessageId, kindMask));
  }

  void deleteItem (int accountId, long chatId, long messageId, int kind) {
    post(accountId, db -> deleteItem(db, accountId, chatId, messageId, kind));
  }

  void markDeleted (int accountId, long chatId, long[] messageIds) {
    post(accountId, db -> markDeleted(db, accountId, chatId, messageIds));
  }

  void refreshText (int accountId, long chatId, long messageId, @Nullable String text) {
    post(accountId, db -> refreshText(db, accountId, chatId, messageId, text));
  }

  void reactions (Reactions r) {
    post(r.accountId, db -> reactions(db, r));
  }

  /** UI thread: the rows were read here (a tap, Mark as read, Mark all as read). */
  public void markItemsRead (int accountId, List<XgramActivityItem> items, boolean local) {
    final List<XgramActivityItem> copy = new ArrayList<>(items);
    post(accountId, db -> setState(db, copy, XgramActivityItem.STATE_NEW, XgramActivityItem.STATE_READ, local));
  }

  /**
   * Read rows New again. {@code byUser}: Mark as unread, of rows read on the server, so counters and searches leave
   * them New ({@link XgramActivityItem#FLAG_USER_UNREAD}); false for the Undo of a read on this device only (ghost
   * mode), whose items are still unread on the server.
   */
  public void markItemsUnread (int accountId, List<XgramActivityItem> items, boolean byUser) {
    final List<XgramActivityItem> copy = new ArrayList<>(items);
    post(accountId, db -> setState(db, copy, XgramActivityItem.STATE_READ, XgramActivityItem.STATE_NEW,
      byUser ? XgramActivityItem.FLAG_USER_UNREAD : 0, XgramActivityItem.FLAG_READ_LOCAL));
  }

  /** The rows go (as tombstones). Undo with {@link #restoreItems} and the same list: the items keep their old state. */
  public void clearItems (int accountId, List<XgramActivityItem> items) {
    final List<XgramActivityItem> copy = new ArrayList<>(items);
    post(accountId, db -> setState(db, copy, -1, XgramActivityItem.STATE_CLEARED, false));
  }

  public void restoreItems (int accountId, List<XgramActivityItem> items) {
    final List<XgramActivityItem> copy = new ArrayList<>(items);
    post(accountId, db -> restoreStates(db, copy));
  }

  /**
   * Clear read items, step one: the Read rows of {@code filter} as the screen shows them (Reactions and Channel
   * comments as set, hidden chats left out; the screen applies the muted rule). In order after what was posted before.
   */
  public void queryRead (int accountId, int filter, boolean reactions, boolean discussion, Callback<List<XgramActivityItem>> callback) {
    post(accountId, db -> {
      List<XgramActivityItem> read = new ArrayList<>();
      for (XgramActivityItem item : queryItems(db, accountId, filter, false, reactions, discussion, null, null, ROW_CAP)) {
        if (item.state == XgramActivityItem.STATE_READ) {
          read.add(item);
        }
      }
      UI.post(() -> callback.onResult(read));
    });
  }

  /** Step two: those rows become Cleared if they are still Read. Undo with {@link #restoreItems} and the same list. */
  public void clearReadItems (int accountId, List<XgramActivityItem> items) {
    final List<XgramActivityItem> copy = new ArrayList<>(items);
    post(accountId, db -> setState(db, copy, XgramActivityItem.STATE_READ, XgramActivityItem.STATE_CLEARED, false));
  }

  public void setChatHidden (int accountId, long chatId, boolean hidden) {
    post(accountId, db -> setHidden(db, accountId, chatId, hidden));
  }

  /** Clear Activity of an account. */
  public void clearAll (int accountId) {
    post(accountId, db -> clearAllRows(db, accountId));
  }

  /** The account in this slot logged out: everything of it goes, and events made before now are dropped. */
  public void clearAccount (int accountId) {
    epochs.put(accountId, epochOf(accountId) + 1);
    executor.execute(() -> {
      SQLiteDatabase db = db();
      if (db == null) {
        return;
      }
      String[] args = {String.valueOf(accountId)};
      try {
        db.beginTransaction();
        try {
          db.delete("items", "account = ?", args);
          db.delete("reactors", "account = ?", args);
          db.delete("sync", "account = ?", args);
          db.delete("meta", "account = ?", args);
          db.delete("hidden", "account = ?", args);
          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
        }
      } catch (Throwable t) {
        Log.e(TAG, "Cannot erase the account", t);
      }
      summaries.remove(accountId);
      scheduleNotify(Collections.singleton(accountId));
    });
  }

  // Reading (results on the UI thread)

  public interface Callback<T> {
    @UiThread
    void onResult (T result);
  }

  public static final int FILTER_ALL = 0, FILTER_MENTIONS = 1, FILTER_REPLIES = 2, FILTER_REACTIONS = 3;

  private static final String ITEM_COLUMNS = "account, chat, msg, kind, topic, album, sender, sender_name, chat_title, topic_title, " +
    "msg_date, event_date, state, flags, text, mention_from, mention_len, reply_chat, reply_msg, quote_text, source, reactor_extra";

  /**
   * The newest {@code limit} rows to show: not Cleared, in the filter, without hidden chats, without reactions while
   * they are off and without discussion groups while Channel comments is off. The muted rule needs TDLib's state, so
   * the screen passes the chats it hides: reactions of {@code mutedReactionChats}, mentions and replies of
   * {@code mutedMentionChats} (null: none). So the limit counts what is shown, and paging never stalls on hidden rows.
   */
  public void query (int accountId, int filter, boolean unreadOnly, boolean reactions, boolean discussion,
                     @Nullable long[] mutedReactionChats, @Nullable long[] mutedMentionChats, int limit, Callback<List<XgramActivityItem>> callback) {
    executor.execute(() -> {
      List<XgramActivityItem> result = new ArrayList<>();
      SQLiteDatabase db = db();
      if (db != null) {
        try {
          result = queryItems(db, accountId, filter, unreadOnly, reactions, discussion, mutedReactionChats, mutedMentionChats, limit);
        } catch (Throwable t) {
          Log.e(TAG, "Query failed", t);
        }
      }
      final List<XgramActivityItem> items = result;
      UI.post(() -> callback.onResult(items));
    });
  }

  static List<XgramActivityItem> queryItems (SQLiteDatabase db, int accountId, int filter, boolean unreadOnly, boolean reactions, boolean discussion, int limit) {
    return queryItems(db, accountId, filter, unreadOnly, reactions, discussion, null, null, limit);
  }

  private static void appendChatList (StringBuilder sql, long[] chatIds) {
    sql.append('(');
    for (int i = 0; i < chatIds.length; i++) {
      if (i > 0) {
        sql.append(',');
      }
      sql.append(chatIds[i]);
    }
    sql.append(')');
  }

  static List<XgramActivityItem> queryItems (SQLiteDatabase db, int accountId, int filter, boolean unreadOnly, boolean reactions, boolean discussion,
                                             @Nullable long[] mutedReactionChats, @Nullable long[] mutedMentionChats, int limit) {
    StringBuilder sql = new StringBuilder("SELECT ").append(ITEM_COLUMNS).append(" FROM items WHERE account = ? AND state != 2");
    if (unreadOnly) {
      sql.append(" AND state = 0");
    }
    switch (filter) {
      case FILTER_MENTIONS:
        sql.append(" AND kind = 1");
        break;
      case FILTER_REPLIES:
        sql.append(" AND (kind = 2 OR (kind = 1 AND (flags & 4) != 0))");
        break;
      case FILTER_REACTIONS:
        sql.append(" AND kind = 3");
        break;
    }
    if (!reactions) {
      sql.append(" AND kind != 3");
    }
    if (!discussion) {
      sql.append(" AND (flags & 24) = 0");
    }
    if (mutedReactionChats != null && mutedReactionChats.length > 0) {
      sql.append(" AND NOT (kind = 3 AND chat IN ");
      appendChatList(sql, mutedReactionChats);
      sql.append(')');
    }
    if (mutedMentionChats != null && mutedMentionChats.length > 0) {
      sql.append(" AND NOT (kind IN (1,2) AND chat IN ");
      appendChatList(sql, mutedMentionChats);
      sql.append(')');
    }
    sql.append(" AND chat NOT IN (SELECT chat FROM hidden WHERE account = ?) ORDER BY event_date DESC, msg DESC LIMIT ").append(Math.max(1, limit));
    String account = String.valueOf(accountId);
    List<Object[]> rows = new ArrayList<>();
    try (Cursor c = db.rawQuery(sql.toString(), new String[] {account, account})) {
      while (c.moveToNext()) {
        rows.add(readRow(c));
      }
    }
    // Who reacted, for every reaction row at once (message ids repeat across chats: matched by chat too)
    Map<String, List<XgramActivityItem.Reactor>> reactorsByItem = new HashMap<>();
    StringBuilder ids = null;
    for (Object[] row : rows) {
      if ((Integer) row[3] == XgramActivityItem.KIND_REACTION) {
        reactorsByItem.put(row[1] + "_" + row[2], new ArrayList<>());
        ids = ids == null ? new StringBuilder() : ids.append(',');
        ids.append((long) (Long) row[2]);
      }
    }
    if (ids != null) {
      try (Cursor c = db.rawQuery("SELECT chat, msg, sender, reaction, is_big, seen FROM reactors WHERE account = ? AND msg IN (" + ids + ") ORDER BY seen DESC, rowid DESC",
        new String[] {account})) {
        while (c.moveToNext()) {
          List<XgramActivityItem.Reactor> list = reactorsByItem.get(c.getLong(0) + "_" + c.getLong(1));
          if (list != null && list.size() < MAX_REACTORS) {
            list.add(new XgramActivityItem.Reactor(c.getLong(2), c.getString(3), c.getInt(4) != 0, c.getInt(5)));
          }
        }
      }
    }
    List<XgramActivityItem> result = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      List<XgramActivityItem.Reactor> reactors = (Integer) row[3] == XgramActivityItem.KIND_REACTION ? reactorsByItem.get(row[1] + "_" + row[2]) : null;
      result.add(toItem(row, reactors));
    }
    return result;
  }

  private static Object[] readRow (Cursor c) {
    return new Object[] {
      c.getInt(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4), c.getLong(5), c.getLong(6),
      c.isNull(7) ? null : c.getString(7), c.isNull(8) ? null : c.getString(8), c.isNull(9) ? null : c.getString(9),
      c.getInt(10), c.getInt(11), c.getInt(12), c.getInt(13), c.isNull(14) ? null : c.getString(14),
      c.getInt(15), c.getInt(16), c.getLong(17), c.getLong(18), c.isNull(19) ? null : c.getString(19), c.getInt(20), c.getInt(21)
    };
  }

  private static XgramActivityItem toItem (Object[] r, @Nullable List<XgramActivityItem.Reactor> reactors) {
    return new XgramActivityItem((Integer) r[0], (Long) r[1], (Long) r[2], (Integer) r[3], (Integer) r[4], (Long) r[5], (Long) r[6],
      (String) r[7], (String) r[8], (String) r[9], (Integer) r[10], (Integer) r[11], (Integer) r[12], (Integer) r[13], (String) r[14],
      (Integer) r[15], (Integer) r[16], (Long) r[17], (Long) r[18], (String) r[19], (Integer) r[20], reactors, (Integer) r[21]);
  }

  /** A chat hidden from Activity, with the title its items saved (null when none has one, after Clear Activity). */
  public static final class HiddenChat {
    public final long chatId;
    @Nullable public final String savedTitle;

    HiddenChat (long chatId, @Nullable String savedTitle) {
      this.chatId = chatId;
      this.savedTitle = savedTitle;
    }
  }

  /** The chats hidden from Activity, newest first. */
  public void hiddenChats (int accountId, Callback<List<HiddenChat>> callback) {
    executor.execute(() -> {
      List<HiddenChat> chats = new ArrayList<>();
      SQLiteDatabase db = db();
      if (db != null) {
        try (Cursor c = db.rawQuery("SELECT h.chat, (SELECT i.chat_title FROM items i WHERE i.account = h.account AND i.chat = h.chat AND i.chat_title IS NOT NULL " +
          "ORDER BY i.event_date DESC LIMIT 1) FROM hidden h WHERE h.account = ? ORDER BY h.rowid DESC", new String[] {String.valueOf(accountId)})) {
          while (c.moveToNext()) {
            chats.add(new HiddenChat(c.getLong(0), c.isNull(1) ? null : c.getString(1)));
          }
        } catch (Throwable t) {
          Log.e(TAG, "Cannot read hidden chats", t);
        }
      }
      UI.post(() -> callback.onResult(chats));
    });
  }

  // Schema

  static void createSchema (SQLiteDatabase db) {
    db.execSQL("CREATE TABLE IF NOT EXISTS items (account INTEGER NOT NULL, chat INTEGER NOT NULL, msg INTEGER NOT NULL, kind INTEGER NOT NULL, " +
      "topic INTEGER NOT NULL DEFAULT 0, album INTEGER NOT NULL DEFAULT 0, sender INTEGER NOT NULL DEFAULT 0, sender_name TEXT, chat_title TEXT, topic_title TEXT, " +
      "msg_date INTEGER NOT NULL DEFAULT 0, event_date INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL DEFAULT 0, flags INTEGER NOT NULL DEFAULT 0, " +
      "text TEXT, mention_from INTEGER NOT NULL DEFAULT -1, mention_len INTEGER NOT NULL DEFAULT 0, reply_chat INTEGER NOT NULL DEFAULT 0, " +
      "reply_msg INTEGER NOT NULL DEFAULT 0, quote_text TEXT, source INTEGER NOT NULL DEFAULT 1, reactor_extra INTEGER NOT NULL DEFAULT 0, " +
      "updated INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (account, chat, msg, kind))");
    db.execSQL("CREATE INDEX IF NOT EXISTS items_feed ON items (account, state, event_date DESC)");
    db.execSQL("CREATE INDEX IF NOT EXISTS items_chat ON items (account, chat, kind, state)");
    db.execSQL("CREATE TABLE IF NOT EXISTS reactors (account INTEGER NOT NULL, chat INTEGER NOT NULL, msg INTEGER NOT NULL, sender INTEGER NOT NULL, " +
      "reaction TEXT NOT NULL, is_big INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL DEFAULT 0, unread INTEGER NOT NULL DEFAULT 1, " +
      "PRIMARY KEY (account, chat, msg, sender, reaction))");
    createReactorsReadTrigger(db);
    db.execSQL("CREATE TABLE IF NOT EXISTS sync (account INTEGER NOT NULL, chat INTEGER NOT NULL, job INTEGER NOT NULL, next_from INTEGER NOT NULL DEFAULT 0, " +
      "done INTEGER NOT NULL DEFAULT 0, last_run INTEGER NOT NULL DEFAULT 0, last_count INTEGER NOT NULL DEFAULT -1, error TEXT, retry_at INTEGER NOT NULL DEFAULT 0, " +
      "PRIMARY KEY (account, chat, job))");
    db.execSQL("CREATE TABLE IF NOT EXISTS meta (account INTEGER PRIMARY KEY, flood_until INTEGER NOT NULL DEFAULT 0, flood_count INTEGER NOT NULL DEFAULT 0, " +
      "last_flood INTEGER NOT NULL DEFAULT 0, day INTEGER NOT NULL DEFAULT 0, requests_today INTEGER NOT NULL DEFAULT 0, lookback_done INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE TABLE IF NOT EXISTS hidden (account INTEGER NOT NULL, chat INTEGER NOT NULL, PRIMARY KEY (account, chat))");
  }

  /** A reaction row read (or cleared) ends its reactors' unread state: only unread ones can be taken back later. */
  private static void createReactorsReadTrigger (SQLiteDatabase db) {
    db.execSQL("CREATE TRIGGER IF NOT EXISTS reactors_read AFTER UPDATE OF state ON items " +
      "WHEN NEW.kind = 3 AND OLD.state = 0 AND NEW.state != 0 BEGIN " +
      "UPDATE reactors SET unread = 0 WHERE account = NEW.account AND chat = NEW.chat AND msg = NEW.msg AND unread != 0; END");
  }

  private static boolean hasColumn (SQLiteDatabase db, String table, String column) {
    try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      int name = c.getColumnIndex("name");
      while (c.moveToNext()) {
        if (name >= 0 && column.equals(c.getString(name))) {
          return true;
        }
      }
    }
    return false;
  }

  private static final class Helper extends SQLiteOpenHelper {
    Helper (Context context) {
      super(context, "xgram_activity.db", null, 2);
      setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate (SQLiteDatabase db) {
      createSchema(db);
    }

    @Override
    public void onUpgrade (SQLiteDatabase db, int oldVersion, int newVersion) {
      // 2: whether each reactor is still unread (the reactors of rows read already count as read)
      if (oldVersion < 2 && !hasColumn(db, "reactors", "unread")) {
        db.execSQL("ALTER TABLE reactors ADD COLUMN unread INTEGER NOT NULL DEFAULT 1");
        db.execSQL("UPDATE reactors SET unread = 0 WHERE EXISTS (SELECT 1 FROM items i WHERE i.account = reactors.account AND i.chat = reactors.chat " +
          "AND i.msg = reactors.msg AND i.kind = 3 AND i.state != 0)");
      }
      createSchema(db);
    }
  }

  // Debug helpers

  /** Debug builds: a map of the store's rows per state, for the log. */
  @WorkerThread
  Map<Integer, Integer> debugCounts (SQLiteDatabase db, int accountId) {
    Map<Integer, Integer> counts = new HashMap<>();
    try (Cursor c = db.rawQuery("SELECT state, COUNT(*) FROM items WHERE account = ? GROUP BY state", new String[] {String.valueOf(accountId)})) {
      while (c.moveToNext()) {
        counts.put(c.getInt(0), c.getInt(1));
      }
    }
    return counts;
  }
}
