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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Activity's paced searches, on the store's thread: unread mentions and reactions TDLib counts but Activity has not
 * seen (app started after them, read state changed elsewhere, gaps), and once a short look back for earlier mentions.
 * <p>
 * One request at a time per account, 15 seconds apart (4 while Activity is on screen), at most 300 a day, only for the
 * current account while the app is in front, connected, and not while Telegram asked to wait. A flood wait stops the
 * account's whole queue for 1.2 times what Telegram asked (at least a minute), doubling for every flood in a row up to
 * 6 hours, kept across restarts. A gate that fails pauses the queue; a cheap tick looks at the gates again, nothing is
 * retried in a loop. Results are classified as live messages are; a search never makes a Read or Cleared row New.
 */
final class XgramActivitySync {
  private XgramActivitySync () { }

  private static final String TAG = XgramActivityStore.TAG;

  static final int JOB_MENTIONS = 1, JOB_REACTIONS = 2, JOB_LOOK_BACK = 3;
  /**
   * A pull to refresh's look in a recently active group: the newest page of its mentions (as the look back searches),
   * for mentions and replies that came while the app was not running and were read elsewhere since. Only those of the
   * last day are kept (RECENT_ACTIVE_SECONDS): older ones are the look back's, which the user may have turned off.
   */
  static final int JOB_RECENT = 4;

  private static final long SPACING_MS = 15_000L, SPACING_VISIBLE_MS = 4_000L;
  private static final int DAILY_CAP = 300;
  private static final long LOST_AFTER_MS = TimeUnit.MINUTES.toMillis(5);
  private static final long FIRST_SEED_DELAY_MS = TimeUnit.SECONDS.toMillis(60), SEED_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
  private static final long TRIGGER_DEBOUNCE_MS = 5_000L, TRIGGER_MIN_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2);
  private static final long ERROR_RETRY_MS = TimeUnit.HOURS.toMillis(24);
  private static final long CHAT_RECHECK_MS = TimeUnit.HOURS.toMillis(6);
  private static final long FLOOD_RESET_MS = TimeUnit.HOURS.toMillis(1);
  private static final long TICK_MS = 30_000L;
  private static final int MENTION_PAGES = 3, REACTION_PAGES = 2, PAGE_SIZE = 100, LOOK_BACK_PAGE_SIZE = 50;
  private static final int LOOK_BACK_CHATS = 30;
  private static final long LOOK_BACK_ACTIVE_SECONDS = TimeUnit.DAYS.toSeconds(14), LOOK_BACK_KEEP_SECONDS = TimeUnit.DAYS.toSeconds(30);
  /** Reply targets looked up (locally) per page of results, for the quote. */
  private static final int TARGET_LOOKUPS_PER_PAGE = 20;

  private static XgramActivityStore store () {
    return XgramActivityStore.instance();
  }

  // Queues (store thread only)

  private static final class Job {
    final long chatId;
    final int type;
    int pages;

    Job (long chatId, int type) {
      this.chatId = chatId;
      this.type = type;
    }

    String key () {
      return chatId + ":" + type;
    }
  }

  private static final class Queue {
    final int accountId;
    LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    boolean inFlight;
    long inFlightSince, lastSentAt;
    int total, done;
    int lookBackPending;
    long firstForegroundAt, lastSeedAt;
    /** Per chat and job (Job.key): a mention mismatch never holds back a reaction one of the same chat. */
    final Map<String, Long> lastTriggerAt = new HashMap<>();

    Queue (int accountId) {
      this.accountId = accountId;
    }

    void addFront (Job job) {
      LinkedHashMap<String, Job> next = new LinkedHashMap<>();
      next.put(job.key(), job);
      for (Map.Entry<String, Job> entry : jobs.entrySet()) {
        if (!entry.getKey().equals(job.key())) {
          next.put(entry.getKey(), entry.getValue());
        }
      }
      jobs = next;
    }

    boolean addBack (Job job) {
      if (jobs.containsKey(job.key())) {
        return false;
      }
      jobs.put(job.key(), job);
      return true;
    }

    @Nullable
    Job poll () {
      if (jobs.isEmpty()) {
        return null;
      }
      String key = jobs.keySet().iterator().next();
      return jobs.remove(key);
    }
  }

  private static final Map<Integer, Queue> queues = new HashMap<>();
  private static volatile int visibleAccountId = -1;
  private static boolean tickStarted;

  private static Queue queue (int accountId) {
    Queue queue = queues.get(accountId);
    if (queue == null) {
      queue = new Queue(accountId);
      queues.put(accountId, queue);
    }
    return queue;
  }

  // Entry points (any thread)

  /** Starts the tick that seeds (a minute after the app comes to the front, then every 6 hours) and resumes queues. */
  @AnyThread
  static void start () {
    store().executor.execute(() -> {
      if (!tickStarted) {
        tickStarted = true;
        store().executor.scheduleWithFixedDelay(XgramActivitySync::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
      }
    });
  }

  /** The Activity screen is on screen (faster spacing) and a seed when it opens. */
  @AnyThread
  static void setVisible (int accountId, boolean visible) {
    if (visible) {
      visibleAccountId = accountId;
      seed(accountId, false);
    } else if (visibleAccountId == accountId) {
      visibleAccountId = -1;
    }
  }

  /** A chat's counter and Activity disagree: a targeted search after a few seconds (at most every 2 minutes). */
  @AnyThread
  static void trigger (Tdlib tdlib, long chatId, int kindMask, int serverCount) {
    if (!XgramActivity.isOn()) {
      return;
    }
    final int accountId = tdlib.id();
    final int type = kindMask == XgramActivityItem.MASK_REACTION ? JOB_REACTIONS : JOB_MENTIONS;
    if (type == JOB_REACTIONS && !XgramActivity.reactions()) {
      return;
    }
    store().executor.execute(() -> {
      if (store().summary(accountId).isHidden(chatId)) {
        return; // hidden chats are not searched (their live items are still kept)
      }
      Queue queue = queue(accountId);
      long now = System.currentTimeMillis();
      final String triggerKey = new Job(chatId, type).key();
      Long last = queue.lastTriggerAt.get(triggerKey);
      if (last != null && now - last < TRIGGER_MIN_INTERVAL_MS) {
        return;
      }
      SQLiteDatabase db = store().db();
      if (db == null) {
        return;
      }
      SyncRow row = readSync(db, accountId, chatId, type);
      if (row != null && row.retryAt > now) {
        return; // its last search failed: it waits a day
      }
      if (row != null && row.lastCount == serverCount && row.done && now - row.lastRun < CHAT_RECHECK_MS) {
        return; // this count was searched already
      }
      queue.lastTriggerAt.put(triggerKey, now);
      store().executor.schedule(() -> {
        Job job = new Job(chatId, type);
        queue.addFront(job);
        queue.total++;
        publishStatus(queue);
        pump(accountId);
      }, TRIGGER_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    });
  }

  /** Activity or Reactions came on (or Find earlier mentions): a seed now, the look back again when asked. */
  @AnyThread
  static void onSettingsChanged (boolean lookBackAgain) {
    TdlibAccount account = TdlibManager.instance().currentAccount();
    if (account == null) {
      return;
    }
    final int accountId = account.id;
    if (lookBackAgain) {
      store().executor.execute(() -> {
        SQLiteDatabase db = store().db();
        if (db != null) {
          writeMeta(db, accountId, "lookback_done", 0);
        }
      });
    }
    seed(accountId, true);
  }

  /** The account logged out: its queue goes (its rows are erased by the store). */
  @AnyThread
  static void forget (int accountId) {
    store().executor.execute(() -> queues.remove(accountId));
  }

  // Pull to refresh (the Activity page): a bounded catch-up, at most once every 30 seconds per account

  /**
   * A refresh's outcome. REFRESH_OFFLINE: no connection, so nothing could be fetched (what was queued, if anything,
   * runs once it is back). REFRESH_OFF: no authorized account, Activity off, or no store.
   */
  static final int REFRESH_QUEUED = 0, REFRESH_NOTHING = 1, REFRESH_TOO_SOON = 2, REFRESH_WAIT = 3, REFRESH_OFF = 4, REFRESH_OFFLINE = 5;
  private static final long REFRESH_MIN_INTERVAL_MS = 30_000L;
  /** Recently active groups a refresh looks in: at most this many, active within a day, each at most twice an hour. */
  private static final int RECENT_CHATS = 5;
  private static final long RECENT_ACTIVE_SECONDS = TimeUnit.DAYS.toSeconds(1), RECENT_RECHECK_MS = TimeUnit.MINUTES.toMillis(30);
  /** Store thread: when each account refreshed last. */
  private static final Map<Integer, Long> lastRefreshAt = new HashMap<>();

  interface RefreshCallback {
    /**
     * {@code result}: REFRESH_*. {@code waitMs}: how long Telegram still asks to wait (REFRESH_WAIT). {@code jobs}: the
     * searches queued (REFRESH_QUEUED); they run paced as every other search, the status tile tells their progress.
     */
    @UiThread
    void onRefreshQueued (int result, long waitMs, int jobs);
  }

  /**
   * A pull to refresh: the chats whose unread mention or reaction counters differ from what Activity has (as a seed
   * finds them, not forced: a matching count costs nothing), and up to {@link #RECENT_CHATS} groups active within a
   * day ({@code onlyChats}' ones when the page shows only some chats), queued and paced as every search (the daily
   * cap included). Nothing while Telegram asks to wait (a flood wait, or a forum's), or within 30 seconds of the last
   * refresh.
   */
  @AnyThread
  static void refresh (int accountId, @Nullable long[] onlyChats, RefreshCallback callback) {
    store().executor.execute(() -> {
      int result = REFRESH_OFF;
      long waitMs = 0;
      int jobs = 0;
      try {
        TdlibAccount account = TdlibManager.instance().currentAccount();
        Tdlib tdlib = account != null && account.id == accountId ? account.activeTdlib() : null;
        SQLiteDatabase db = store().db();
        if (tdlib != null && tdlib.isAuthorized() && XgramActivity.isOn() && db != null) {
          final long now = System.currentTimeMillis();
          final long floodWait = readMeta(db, accountId, "flood_until") - now;
          final long topicWait = com.fahadali.xgram.topics.XgramTopicFlood.waitMs(tdlib);
          final Long last = lastRefreshAt.get(accountId);
          if (floodWait > 0 || topicWait > 0) {
            result = REFRESH_WAIT;
            waitMs = Math.max(floodWait, topicWait);
          } else if (last != null && now - last < REFRESH_MIN_INTERVAL_MS) {
            result = tdlib.isConnected() ? REFRESH_TOO_SOON : REFRESH_OFFLINE;
          } else {
            lastRefreshAt.put(accountId, now);
            final Queue queue = queue(accountId);
            jobs = seedNow(queue, tdlib, false) + queueRecent(db, queue, tdlib, onlyChats, now);
            if (jobs > 0) {
              publishStatus(queue);
              pump(accountId);
            }
            // Offline, the searches wait (the status tile says so): the pull says it fetched nothing, not "Updated"
            result = !tdlib.isConnected() ? REFRESH_OFFLINE : jobs > 0 ? REFRESH_QUEUED : REFRESH_NOTHING;
          }
        }
      } catch (Throwable t) {
        Log.e(TAG, "Sync refresh", t);
      }
      final int finalResult = result, finalJobs = jobs;
      final long finalWait = waitMs;
      UI.post(() -> callback.onRefreshQueued(finalResult, finalWait, finalJobs));
    });
  }

  /** The recently active groups of a refresh ({@link #JOB_RECENT}), added after what is queued. Returns how many. */
  @WorkerThread
  private static int queueRecent (SQLiteDatabase db, Queue queue, Tdlib tdlib, @Nullable long[] onlyChats, long now) {
    final int accountId = queue.accountId;
    final XgramActivityStore.Summary summary = store().summary(accountId);
    final boolean someChats = onlyChats != null && onlyChats.length > 0;
    final int since = (int) (now / 1000L - RECENT_ACTIVE_SECONDS);
    int added = 0;
    for (long chatId : tdlib.xgramActivityChats(Tdlib.XGRAM_ACTIVITY_LOOKBACK, since, someChats ? 0 : RECENT_CHATS * 4)) {
      if (added >= RECENT_CHATS) {
        break;
      }
      if (summary.isHidden(chatId) || (someChats && !XgramActivityChatFilter.contains(onlyChats, chatId))) {
        continue;
      }
      final TdApi.Chat chat = tdlib.chat(chatId);
      if (XgramActivityCollector.chatKind(tdlib, chat) != XgramActivityCollector.CHAT_GROUP || chat.unreadMentionCount > 0) {
        continue; // not a group, or its unread mentions are the seed's
      }
      if (!XgramActivity.discussion() && (XgramActivityCollector.baseFlags(tdlib, chat) & XgramActivityItem.FLAG_DISCUSSION) != 0) {
        continue;
      }
      final SyncRow row = readSync(db, accountId, chatId, JOB_RECENT);
      if (row != null && (row.retryAt > now || now - row.lastRun < RECENT_RECHECK_MS)) {
        continue;
      }
      if (queue.addBack(new Job(chatId, JOB_RECENT))) {
        added++;
      }
    }
    queue.total += added;
    return added;
  }

  /** Debug builds: as if Telegram asked to wait two minutes (the sync, and Mark all as read). */
  @AnyThread
  static void injectFlood () {
    if (!BuildConfig.DEBUG) {
      return;
    }
    TdlibAccount account = TdlibManager.instance().currentAccount();
    if (account == null) {
      return;
    }
    final int accountId = account.id;
    XgramActivityRead.injectFlood(accountId, 120_000L);
    store().executor.execute(() -> {
      SQLiteDatabase db = store().db();
      if (db != null) {
        onFlood(db, queue(accountId), 100L); // 1.2 times that: the 2 minutes the switch says
      }
    });
  }

  // Tick: gates, seeding

  @WorkerThread
  private static void tick () {
    try {
      if (!XgramActivity.isOn()) {
        return;
      }
      TdlibAccount account = TdlibManager.instance().currentAccount();
      Tdlib tdlib = account != null ? account.activeTdlib() : null;
      if (tdlib == null || !tdlib.isAuthorized()) {
        return;
      }
      Queue queue = queue(account.id);
      long now = System.currentTimeMillis();
      if (UI.getUiState() == UI.State.RESUMED) {
        if (queue.firstForegroundAt == 0) {
          queue.firstForegroundAt = now;
        }
        if (now - queue.firstForegroundAt >= FIRST_SEED_DELAY_MS && (queue.lastSeedAt == 0 || now - queue.lastSeedAt >= SEED_INTERVAL_MS)) {
          seedNow(queue, tdlib, false);
        }
      }
      if (!queue.jobs.isEmpty()) {
        pump(account.id);
      }
    } catch (Throwable t) {
      Log.e(TAG, "Sync tick", t);
    }
  }

  @AnyThread
  private static void seed (int accountId, boolean force) {
    store().executor.execute(() -> {
      try {
        TdlibAccount account = TdlibManager.instance().currentAccount();
        Tdlib tdlib = account != null && account.id == accountId ? account.activeTdlib() : null;
        if (tdlib == null || !tdlib.isAuthorized() || !XgramActivity.isOn()) {
          return;
        }
        seedNow(queue(accountId), tdlib, force);
      } catch (Throwable t) {
        Log.e(TAG, "Sync seed", t);
      }
    });
  }

  /** The chats TDLib counts unread mentions or reactions in, and the look back once. Returns the searches queued. */
  @WorkerThread
  private static int seedNow (Queue queue, Tdlib tdlib, boolean force) {
    SQLiteDatabase db = store().db();
    if (db == null) {
      return 0;
    }
    final long now = System.currentTimeMillis();
    queue.lastSeedAt = now;
    final int accountId = queue.accountId;
    final XgramActivityStore.Summary summary = store().summary(accountId);
    final boolean reactions = XgramActivity.reactions();
    readZeroCounters(tdlib, accountId, summary, reactions, now);
    int added = 0;
    for (long chatId : tdlib.xgramActivityChats(Tdlib.XGRAM_ACTIVITY_UNREAD, 0, 0)) {
      if (summary.isHidden(chatId)) {
        continue;
      }
      TdApi.Chat chat = tdlib.chat(chatId);
      int kind = XgramActivityCollector.chatKind(tdlib, chat);
      if (chat == null || kind == XgramActivityCollector.CHAT_NONE || kind == XgramActivityCollector.CHAT_REPLIES) {
        continue;
      }
      if (kind == XgramActivityCollector.CHAT_GROUP && chat.unreadMentionCount > 0 &&
        needsRun(db, accountId, chatId, JOB_MENTIONS, chat.unreadMentionCount, summary.syncCount(chatId, XgramActivityItem.MASK_MENTION_GROUP), now, force)) {
        if (queue.addBack(new Job(chatId, JOB_MENTIONS))) {
          added++;
        }
      }
      if (reactions && chat.unreadReactionCount > 0 &&
        needsRun(db, accountId, chatId, JOB_REACTIONS, chat.unreadReactionCount, summary.syncCount(chatId, XgramActivityItem.MASK_REACTION), now, force)) {
        if (queue.addBack(new Job(chatId, JOB_REACTIONS))) {
          added++;
        }
      }
    }
    if (XgramActivity.lookBack() && readMeta(db, accountId, "lookback_done") == 0 && queue.lookBackPending == 0) {
      int since = (int) (now / 1000L - LOOK_BACK_ACTIVE_SECONDS);
      for (long chatId : tdlib.xgramActivityChats(Tdlib.XGRAM_ACTIVITY_LOOKBACK, since, LOOK_BACK_CHATS * 2)) {
        if (queue.lookBackPending >= LOOK_BACK_CHATS) {
          break;
        }
        TdApi.Chat chat = tdlib.chat(chatId);
        if (summary.isHidden(chatId) || XgramActivityCollector.chatKind(tdlib, chat) != XgramActivityCollector.CHAT_GROUP) {
          continue;
        }
        if (!XgramActivity.discussion() && (XgramActivityCollector.baseFlags(tdlib, chat) & XgramActivityItem.FLAG_DISCUSSION) != 0) {
          continue;
        }
        if (queue.addBack(new Job(chatId, JOB_LOOK_BACK))) {
          queue.lookBackPending++;
          added++;
        }
      }
      if (queue.lookBackPending == 0) {
        writeMeta(db, accountId, "lookback_done", 1); // no group to look in
      }
    }
    if (added > 0) {
      queue.total += added;
      publishStatus(queue);
      pump(accountId);
    }
    return added;
  }

  /**
   * A chat is searched again unless it failed within a day, or (not forced) a finished search left Activity with as
   * many New items as TDLib counts, or it was searched within 6 hours for the same count. Never searched: it is.
   */
  @WorkerThread
  private static boolean needsRun (SQLiteDatabase db, int accountId, long chatId, int type, int serverCount, int storedCount, long now, boolean force) {
    SyncRow row = readSync(db, accountId, chatId, type);
    if (row == null) {
      return true;
    }
    if (row.retryAt > now) {
      return false;
    }
    if (force) {
      return true;
    }
    if (row.done && storedCount == serverCount) {
      return false; // nothing to find: the budget is for real differences
    }
    return !(row.done && row.lastCount == serverCount && now - row.lastRun < CHAT_RECHECK_MS);
  }

  /** Items stored this recently may be ahead of the counter update that follows them: left for the next look. */
  private static final long ZERO_COUNT_GRACE_MS = 30_000L;

  /**
   * No request: chats Activity still has New items in while TDLib counts none any more (read elsewhere while Activity
   * or Reactions was off, or in an update the app missed) are read here. In ghost mode the chat's mention count is
   * lowered by what was viewed here, so TDLib's own count decides (GetChat, from its cache).
   */
  @WorkerThread
  private static void readZeroCounters (Tdlib tdlib, int accountId, XgramActivityStore.Summary summary, boolean reactions, long now) {
    final long before = now - ZERO_COUNT_GRACE_MS;
    final XgramActivityStore store = store();
    for (long chatId : summary.chatsWithNew()) {
      final TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        continue;
      }
      if (tdlib.isRepliesChat(chatId)) {
        store.markReadUpTo(accountId, chatId, chat.lastReadInboxMessageId, XgramActivityItem.MASK_REPLY);
        continue;
      }
      if (reactions && chat.unreadReactionCount == 0 && summary.syncCount(chatId, XgramActivityItem.MASK_REACTION) > 0) {
        store.markReadChat(accountId, chatId, XgramActivityItem.MASK_REACTION, before);
      }
      if (chat.unreadMentionCount == 0 && summary.syncCount(chatId, XgramActivityItem.MASK_MENTION_GROUP) > 0) {
        if (!com.fahadali.xgram.ghost.XgramLocalRead.isActive()) {
          store.markReadChat(accountId, chatId, XgramActivityItem.MASK_MENTION_GROUP, before);
        } else {
          tdlib.send(new TdApi.GetChat(chatId), (raw, error) -> {
            if (raw != null && raw.unreadMentionCount == 0) {
              store.markReadChat(accountId, chatId, XgramActivityItem.MASK_MENTION_GROUP, before);
            }
          });
        }
      }
    }
  }

  // Sending

  @WorkerThread
  private static void pump (int accountId) {
    Queue queue = queues.get(accountId);
    if (queue == null) {
      return;
    }
    try {
      pumpImpl(queue);
    } catch (Throwable t) {
      Log.e(TAG, "Sync pump", t);
    }
  }

  @WorkerThread
  private static void pumpImpl (Queue queue) {
    final long now = System.currentTimeMillis();
    if (queue.inFlight) {
      if (now - queue.inFlightSince < LOST_AFTER_MS) {
        return;
      }
      if (BuildConfig.DEBUG) {
        Log.w(TAG, "Sync request lost (no reply in 5 minutes)");
      }
      queue.inFlight = false;
    }
    if (queue.jobs.isEmpty()) {
      finishBatch(queue);
      return;
    }
    // Gates
    if (!XgramActivity.isOn()) {
      queue.jobs.clear();
      finishBatch(queue);
      return;
    }
    TdlibAccount account = TdlibManager.instance().currentAccount();
    Tdlib tdlib = account != null && account.id == queue.accountId ? account.activeTdlib() : null;
    if (tdlib == null || !tdlib.isAuthorized() || UI.getUiState() != UI.State.RESUMED) {
      return; // paused: the tick looks again
    }
    SQLiteDatabase db = store().db();
    if (db == null) {
      return;
    }
    long floodUntil = readMeta(db, queue.accountId, "flood_until");
    if (floodUntil > now) {
      publishStatus(queue);
      return;
    }
    if (!tdlib.isConnected()) {
      publishStatus(queue, true);
      return;
    }
    if (com.fahadali.xgram.topics.XgramTopicFlood.waitMs(tdlib) > 0) {
      return;
    }
    if (requestsToday(db, queue.accountId) >= DAILY_CAP) {
      queue.jobs.clear();
      finishBatch(queue);
      return;
    }
    long spacing = visibleAccountId == queue.accountId ? SPACING_VISIBLE_MS : SPACING_MS;
    long wait = queue.lastSentAt + spacing - now;
    if (wait > 0) {
      store().executor.schedule(() -> pump(queue.accountId), wait, TimeUnit.MILLISECONDS);
      return;
    }
    Job job = queue.poll();
    if (job == null) {
      finishBatch(queue);
      return;
    }
    send(db, queue, tdlib, job);
  }

  @WorkerThread
  private static void send (SQLiteDatabase db, Queue queue, Tdlib tdlib, Job job) {
    final long now = System.currentTimeMillis();
    TdApi.Chat chat = tdlib.chat(job.chatId);
    int chatKind = XgramActivityCollector.chatKind(tdlib, chat);
    if (chat == null || chatKind == XgramActivityCollector.CHAT_NONE || chatKind == XgramActivityCollector.CHAT_REPLIES ||
      (job.type == JOB_REACTIONS && !XgramActivity.reactions()) || (job.type != JOB_REACTIONS && chatKind != XgramActivityCollector.CHAT_GROUP)) {
      jobDone(queue, job);
      store().executor.execute(() -> pump(queue.accountId));
      return;
    }
    SyncRow row = readSync(db, queue.accountId, job.chatId, job.type);
    if (store().summary(queue.accountId).isHidden(job.chatId) || (job.pages == 0 && row != null && row.retryAt > now)) {
      // Hidden since it was queued, or its last search failed (it waits a day): dropped, nothing sent
      jobDone(queue, job);
      store().executor.execute(() -> pump(queue.accountId));
      return;
    }
    // A run continues an unfinished one from its cursor; a finished (or old) one starts again from the newest
    long fromMessageId = 0;
    if (job.pages > 0 || (row != null && !row.done && row.nextFrom != 0 && now - row.lastRun < CHAT_RECHECK_MS)) {
      fromMessageId = row != null ? row.nextFrom : 0;
    }
    final TdApi.SearchMessagesFilter filter;
    final int limit;
    switch (job.type) {
      case JOB_REACTIONS:
        filter = new TdApi.SearchMessagesFilterUnreadReaction();
        limit = PAGE_SIZE;
        break;
      case JOB_LOOK_BACK:
      case JOB_RECENT:
        filter = new TdApi.SearchMessagesFilterMention();
        limit = LOOK_BACK_PAGE_SIZE;
        fromMessageId = 0;
        break;
      case JOB_MENTIONS:
      default:
        filter = new TdApi.SearchMessagesFilterUnreadMention();
        limit = PAGE_SIZE;
        break;
    }
    queue.inFlight = true;
    queue.inFlightSince = now;
    queue.lastSentAt = now;
    countRequest(db, queue.accountId);
    final long sentAt = now;
    final long from = fromMessageId;
    final int epoch = store().epochOf(queue.accountId);
    tdlib.send(new TdApi.SearchChatMessages(job.chatId, null, "", null, from, 0, limit, filter), (found, error) ->
      store().executor.execute(() -> {
        try {
          onResult(queue, tdlib, job, found, error, sentAt, from, epoch);
        } catch (Throwable t) {
          Log.e(TAG, "Sync result", t);
          queue.inFlight = false;
        }
      })
    );
  }

  @WorkerThread
  private static void onResult (Queue queue, Tdlib tdlib, Job job, @Nullable TdApi.FoundChatMessages found, @Nullable TdApi.Error error, long sentAt, long fromMessageId, int epoch) {
    queue.inFlight = false;
    queue.lastSentAt = System.currentTimeMillis();
    SQLiteDatabase db = store().db();
    if (db == null) {
      return;
    }
    final int accountId = queue.accountId;
    if (store().epochOf(accountId) != epoch || queues.get(accountId) != queue) {
      return; // the account logged out meanwhile
    }
    if (error != null || found == null) {
      long retryAfter = XgramActivityClassifier.retryAfterSeconds(error);
      if (BuildConfig.DEBUG) {
        Log.i(TAG, "Sync chat " + job.chatId + " job " + job.type + " error " + (error != null ? error.code + " " + error.message : "none"));
      }
      if (retryAfter >= 0) {
        queue.addFront(job); // it resumes after the wait
        onFlood(db, queue, retryAfter);
        return;
      }
      ContentValues v = syncValues(accountId, job.chatId, job.type);
      v.put("error", error != null ? error.message : "empty");
      v.put("retry_at", System.currentTimeMillis() + ERROR_RETRY_MS);
      v.put("last_run", System.currentTimeMillis());
      db.insertWithOnConflict("sync", null, v, SQLiteDatabase.CONFLICT_REPLACE);
      jobDone(queue, job);
      scheduleNext(queue);
      return;
    }
    if (BuildConfig.DEBUG) {
      Log.i(TAG, "Sync chat " + job.chatId + " job " + job.type + " found " + (found.messages != null ? found.messages.length : 0) + " next " + found.nextFromMessageId);
    }
    final TdApi.Message[] messages = found.messages != null ? found.messages : new TdApi.Message[0];
    final boolean complete = messages.length == 0 || found.nextFromMessageId == 0;
    long minId = Long.MAX_VALUE;
    long[] ids = new long[messages.length];
    for (int i = 0; i < messages.length; i++) {
      ids[i] = messages[i].id;
      minId = Math.min(minId, messages[i].id);
    }
    TdApi.Chat chat = tdlib.chat(job.chatId);
    if (chat != null) {
      switch (job.type) {
        case JOB_MENTIONS:
        case JOB_LOOK_BACK:
        case JOB_RECENT:
          storeMentions(tdlib, chat, messages, job.type);
          break;
        case JOB_REACTIONS:
          storeReactions(tdlib, chat, messages);
          break;
      }
    }
    if (job.type != JOB_LOOK_BACK && job.type != JOB_RECENT) {
      // In order after the upserts above: what the results don't have was read elsewhere
      final int mask = job.type == JOB_REACTIONS ? XgramActivityItem.MASK_REACTION : XgramActivityItem.MASK_MENTION_GROUP;
      final long min = minId == Long.MAX_VALUE ? 0 : minId;
      store().post(accountId, d -> XgramActivityStore.reconcile(d, accountId, job.chatId, mask, ids, sentAt, complete, min, fromMessageId));
    }
    // The cursor, the run and the count it was for
    int serverCount = chat == null ? -1 : job.type == JOB_REACTIONS ? chat.unreadReactionCount : chat.unreadMentionCount;
    ContentValues v = syncValues(accountId, job.chatId, job.type);
    v.put("next_from", complete ? 0 : found.nextFromMessageId);
    v.put("done", complete ? 1 : 0);
    v.put("last_run", System.currentTimeMillis());
    v.put("last_count", serverCount);
    v.putNull("error");
    v.put("retry_at", 0);
    db.insertWithOnConflict("sync", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    // More pages now (a few per run), the rest from the cursor next time
    int maxPages = job.type == JOB_MENTIONS ? MENTION_PAGES : job.type == JOB_REACTIONS ? REACTION_PAGES : 1;
    if (!complete && job.pages + 1 < maxPages) {
      job.pages++;
      queue.addFront(job);
    } else {
      jobDone(queue, job);
    }
    if (floodCountReset(db, accountId)) {
      writeMeta(db, accountId, "flood_count", 0);
    }
    scheduleNext(queue);
  }

  /** Found mentions (and replies, which share their flag): stored as live ones are; a search never revives a row. */
  @WorkerThread
  private static void storeMentions (Tdlib tdlib, TdApi.Chat chat, TdApi.Message[] messages, int jobType) {
    final long me = tdlib.myUserId();
    final String[] usernames = XgramActivityCollector.activeUsernames(tdlib.myUser());
    final int base = XgramActivityCollector.baseFlags(tdlib, chat);
    // A look back and a refresh's look in a recent group search every mention, read ones too. The look back keeps a
    // month (Find earlier mentions); a refresh only what came within its day of activity: it brings in what is new,
    // not old mentions the user may have chosen not to look for
    final boolean allMentions = jobType == JOB_LOOK_BACK || jobType == JOB_RECENT;
    final int source = allMentions ? XgramActivityItem.SOURCE_LOOK_BACK : XgramActivityItem.SOURCE_UNREAD_SYNC;
    final long oldest = System.currentTimeMillis() / 1000L - (jobType == JOB_RECENT ? RECENT_ACTIVE_SECONDS : LOOK_BACK_KEEP_SECONDS);
    int lookups = 0;
    for (TdApi.Message m : messages) {
      if (m == null || m.isOutgoing || tdlib.isSelfSender(m)) {
        continue;
      }
      if (allMentions && m.date < oldest) {
        continue;
      }
      int classified = XgramActivityClassifier.classify(m, me, usernames, true);
      if (classified == 0) {
        continue;
      }
      int kind = XgramActivityClassifier.kindOf(classified);
      int flags = XgramActivityClassifier.flagsOf(classified) | base;
      int state = XgramActivityItem.STATE_NEW;
      boolean viewedHere = false;
      if (allMentions) {
        state = m.containsUnreadMention ? XgramActivityItem.STATE_NEW : XgramActivityItem.STATE_READ;
      } else if (com.fahadali.xgram.ghost.XgramLocalRead.isMentionViewed(tdlib, chat.id, m.id)) {
        // Seen here in ghost mode already
        state = XgramActivityItem.STATE_READ;
        flags |= XgramActivityItem.FLAG_READ_LOCAL;
        viewedHere = true;
      }
      XgramActivityStore.Snapshot s = XgramActivityCollector.snapshot(tdlib, m, chat, kind, flags, state, source);
      TdApi.MessageReplyToMessage replyTo = XgramActivityClassifier.replyToOf(m);
      boolean asReply = kind == XgramActivityItem.KIND_REPLY || (flags & XgramActivityItem.FLAG_ALSO_REPLY) != 0;
      if (asReply && replyTo != null && XgramActivityClassifier.isSameChatReply(m)) {
        s.replyChatId = m.chatId;
        s.replyMessageId = replyTo.messageId;
        s.quoteText = XgramActivityCollector.quoteOf(tdlib, replyTo, null);
      }
      XgramActivityCollector.upsert(tdlib, s);
      if (viewedHere) {
        // An item already stored as New (its view in ghost mode came before it was in the store) is read too
        store().markRead(tdlib.id(), chat.id, m.id, XgramActivityItem.MASK_MENTION_GROUP, true);
      }
      if (asReply && replyTo != null && XgramActivityClassifier.isSameChatReply(m) && lookups < TARGET_LOOKUPS_PER_PAGE) {
        lookups++;
        final int accountId = tdlib.id();
        final long chatId = m.chatId, messageId = m.id;
        tdlib.send(new TdApi.GetMessageLocally(chatId, replyTo.messageId), (target, error) -> {
          try {
            if (target != null) {
              final boolean mine = target.isOutgoing;
              final String quote = mine ? XgramActivityCollector.quoteOf(tdlib, replyTo, target) : null;
              store().post(accountId, db -> XgramActivityCollector.resolveReply(db, accountId, chatId, messageId, mine, quote));
            }
          } catch (Throwable t) {
            Log.e(TAG, "Sync reply target", t);
          }
        });
      }
    }
  }

  /** Your messages with unread reactions: their reactors, a row each (dated with the message). */
  @WorkerThread
  private static void storeReactions (Tdlib tdlib, TdApi.Chat chat, TdApi.Message[] messages) {
    final int accountId = tdlib.id();
    final int base = XgramActivityCollector.baseFlags(tdlib, chat);
    for (TdApi.Message m : messages) {
      if (m == null || m.unreadReactions == null || m.unreadReactions.length == 0) {
        continue;
      }
      XgramActivityStore.Reactions r = new XgramActivityStore.Reactions();
      r.accountId = accountId;
      r.chatId = m.chatId;
      r.messageId = m.id;
      r.source = XgramActivityItem.SOURCE_UNREAD_SYNC;
      r.messageDate = m.date;
      r.quoteText = XgramActivityCollector.contentText(tdlib, m);
      r.chatTitle = tdlib.chatTitle(chat.id);
      r.topicId = XgramActivityCollector.topicOf(tdlib, m);
      r.flags = base;
      for (TdApi.UnreadReaction reaction : m.unreadReactions) {
        if (reaction == null || reaction.senderId == null) {
          continue;
        }
        r.reactors.add(new XgramActivityItem.Reactor(XgramActivityClassifier.encodeSender(reaction.senderId),
          XgramActivityClassifier.reactionKey(reaction.type), reaction.isBig, m.date));
      }
      store().reactions(r);
      XgramActivityCollector.resolveTopicTitle(tdlib, accountId, r.chatId, r.messageId, r.topicId, null);
    }
  }

  @WorkerThread
  private static void jobDone (Queue queue, Job job) {
    queue.done = Math.min(queue.total, queue.done + 1);
    if (job.type == JOB_LOOK_BACK && queue.lookBackPending > 0) {
      queue.lookBackPending--;
      if (queue.lookBackPending == 0) {
        SQLiteDatabase db = store().db();
        if (db != null) {
          writeMeta(db, queue.accountId, "lookback_done", 1);
        }
      }
    }
    publishStatus(queue);
  }

  @WorkerThread
  private static void scheduleNext (Queue queue) {
    if (queue.jobs.isEmpty()) {
      finishBatch(queue);
      return;
    }
    long spacing = visibleAccountId == queue.accountId ? SPACING_VISIBLE_MS : SPACING_MS;
    store().executor.schedule(() -> pump(queue.accountId), spacing, TimeUnit.MILLISECONDS);
  }

  @WorkerThread
  private static void finishBatch (Queue queue) {
    if (queue.inFlight) {
      return;
    }
    if (queue.jobs.isEmpty() && (queue.total != 0 || queue.done != 0)) {
      queue.total = 0;
      queue.done = 0;
      publishStatus(queue);
    }
  }

  // Flood waits

  @WorkerThread
  private static void onFlood (SQLiteDatabase db, Queue queue, long retryAfterSeconds) {
    final long now = System.currentTimeMillis();
    int count = (int) readMeta(db, queue.accountId, "flood_count");
    long lastFlood = readMeta(db, queue.accountId, "last_flood");
    if (lastFlood != 0 && now - lastFlood > FLOOD_RESET_MS) {
      count = 0;
    }
    count++;
    long wait = XgramActivityClassifier.nextBackoffMs(retryAfterSeconds * 1000L, count);
    long until = now + wait;
    writeMeta(db, queue.accountId, "flood_until", until);
    writeMeta(db, queue.accountId, "flood_count", count);
    writeMeta(db, queue.accountId, "last_flood", now);
    if (BuildConfig.DEBUG) {
      Log.w(TAG, "Sync paused for " + (wait / 1000L) + "s (flood " + count + ")");
    }
    publishStatus(queue);
    store().executor.schedule(() -> pump(queue.accountId), wait + 1000L, TimeUnit.MILLISECONDS);
  }

  /** No flood for an hour: the next one starts the backoff over. */
  @WorkerThread
  private static boolean floodCountReset (SQLiteDatabase db, int accountId) {
    long lastFlood = readMeta(db, accountId, "last_flood");
    return lastFlood != 0 && System.currentTimeMillis() - lastFlood > FLOOD_RESET_MS && readMeta(db, accountId, "flood_count") != 0;
  }

  // Status

  @WorkerThread
  private static void publishStatus (Queue queue) {
    publishStatus(queue, false);
  }

  @WorkerThread
  private static void publishStatus (Queue queue, boolean offline) {
    SQLiteDatabase db = store().db();
    long floodUntil = db != null ? readMeta(db, queue.accountId, "flood_until") : 0;
    boolean pending = !queue.jobs.isEmpty() || queue.inFlight;
    // A wait Telegram asked for shows for as long as it lasts, whether anything is queued or not
    XgramActivityStore.SyncStatus status = new XgramActivityStore.SyncStatus(queue.done, queue.total,
      floodUntil > System.currentTimeMillis() ? floodUntil : 0, pending && offline, pending);
    store().setSyncStatus(queue.accountId, status);
  }

  // Sync rows and meta

  private static final class SyncRow {
    long nextFrom;
    boolean done;
    long lastRun;
    int lastCount;
    long retryAt;
  }

  @Nullable
  @WorkerThread
  private static SyncRow readSync (SQLiteDatabase db, int accountId, long chatId, int type) {
    try (Cursor c = db.rawQuery("SELECT next_from, done, last_run, last_count, retry_at FROM sync WHERE account = ? AND chat = ? AND job = ?",
      new String[] {String.valueOf(accountId), String.valueOf(chatId), String.valueOf(type)})) {
      if (!c.moveToFirst()) {
        return null;
      }
      SyncRow row = new SyncRow();
      row.nextFrom = c.getLong(0);
      row.done = c.getInt(1) != 0;
      row.lastRun = c.getLong(2);
      row.lastCount = c.getInt(3);
      row.retryAt = c.getLong(4);
      return row;
    } catch (Throwable t) {
      return null;
    }
  }

  private static ContentValues syncValues (int accountId, long chatId, int type) {
    ContentValues v = new ContentValues();
    v.put("account", accountId);
    v.put("chat", chatId);
    v.put("job", type);
    return v;
  }

  @WorkerThread
  static long readMeta (SQLiteDatabase db, int accountId, String column) {
    try (Cursor c = db.rawQuery("SELECT " + column + " FROM meta WHERE account = ?", new String[] {String.valueOf(accountId)})) {
      return c.moveToFirst() ? c.getLong(0) : 0;
    } catch (Throwable t) {
      return 0;
    }
  }

  @WorkerThread
  static void writeMeta (SQLiteDatabase db, int accountId, String column, long value) {
    try {
      db.execSQL("INSERT OR IGNORE INTO meta (account) VALUES (?)", new Object[] {accountId});
      db.execSQL("UPDATE meta SET " + column + " = ? WHERE account = ?", new Object[] {value, accountId});
    } catch (Throwable t) {
      Log.e(TAG, "Cannot write meta", t);
    }
  }

  private static long today () {
    return System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1);
  }

  @WorkerThread
  private static int requestsToday (SQLiteDatabase db, int accountId) {
    if (readMeta(db, accountId, "day") != today()) {
      return 0;
    }
    return (int) readMeta(db, accountId, "requests_today");
  }

  @WorkerThread
  private static void countRequest (SQLiteDatabase db, int accountId) {
    long day = today();
    if (readMeta(db, accountId, "day") != day) {
      writeMeta(db, accountId, "day", day);
      writeMeta(db, accountId, "requests_today", 0);
    }
    writeMeta(db, accountId, "requests_today", readMeta(db, accountId, "requests_today") + 1);
  }

  /** Debug and status: what the queue of {@code accountId} looks like. */
  @WorkerThread
  static List<String> describe (int accountId) {
    List<String> result = new ArrayList<>();
    Queue queue = queues.get(accountId);
    if (queue != null) {
      result.addAll(queue.jobs.keySet());
    }
    return result;
  }
}
