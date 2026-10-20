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

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.AppContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * State changes of Later records. Each change: a LevelDB write and a new index under {@link XgramLaterStore#LOCK},
 * listener events after the lock is released, then the alarm armed again on the "XgramLater" thread. Nothing here
 * calls the server: only local TDLib reads (the chat cache).
 */
final class XgramLaterEngine {
  private XgramLaterEngine () { }

  private static final String TAG = XgramLaterStore.TAG;

  // The "XgramLater" thread: loading, due checks, wakes, alarms, notifications, receiver work

  private static final HandlerThread thread;
  static final Handler EXECUTOR;

  static {
    thread = new HandlerThread("XgramLater");
    thread.start();
    EXECUTOR = new Handler(thread.getLooper());
  }

  static void post (Runnable r) {
    EXECUTOR.post(() -> {
      try {
        r.run();
      } catch (Throwable t) {
        Log.e(TAG, "Later task failed", t);
      }
    });
  }

  static boolean onExecutor () {
    return Thread.currentThread() == thread;
  }

  static Context context () {
    return AppContext.get();
  }

  // Accounts seen this process (for follow-up checks and on-screen checks; never needed by the alarm path)

  private static final Map<Integer, WeakReference<Tdlib>> tdlibs = new ConcurrentHashMap<>();

  static void register (Tdlib tdlib) {
    tdlibs.put(tdlib.id(), new WeakReference<>(tdlib));
  }

  @Nullable
  static Tdlib tdlib (int accountId) {
    WeakReference<Tdlib> ref = tdlibs.get(accountId);
    return ref != null ? ref.get() : null;
  }

  // Committing changes

  /** Old and new record of one key (new null: removed). */
  static final class Change {
    final String key;
    @Nullable final XgramLaterRecord before, after;

    Change (String key, @Nullable XgramLaterRecord before, @Nullable XgramLaterRecord after) {
      this.key = key;
      this.before = before;
      this.after = after;
    }
  }

  /** What a change means to the chat lists. */
  static int eventsOf (@Nullable XgramLaterRecord before, @Nullable XgramLaterRecord after) {
    int events = 0;
    boolean snoozedBefore = before != null && before.isSnooze() && before.isPending();
    boolean snoozedAfter = after != null && after.isSnooze() && after.isPending();
    if (snoozedBefore != snoozedAfter) {
      events |= XgramLater.EVENT_HIDDEN | XgramLater.EVENT_LIST;
    } else if (snoozedAfter && (before.wakeAt != after.wakeAt || before.kind != after.kind || before.mentionsAt != after.mentionsAt)) {
      events |= XgramLater.EVENT_LIST;
    }
    boolean bellBefore = before != null && before.hasBell();
    boolean bellAfter = after != null && after.hasBell();
    if (bellBefore != bellAfter || (bellAfter && before.dueAt != after.dueAt)) {
      events |= XgramLater.EVENT_BELL | XgramLater.EVENT_RAISE;
    }
    return events;
  }

  /**
   * Applies {@code puts} (records keyed by their key) and {@code removes} for one account: the store first, then the
   * listeners, then the alarm. Notifications of records that are no longer due are cancelled. Returns the changes.
   */
  static List<Change> commit (int accountId, @Nullable List<XgramLaterRecord> puts, @Nullable List<String> removes) {
    XgramLaterStore.ensureLoaded();
    List<Change> changes = new ArrayList<>();
    synchronized (XgramLaterStore.LOCK) {
      if (puts != null) {
        for (XgramLaterRecord r : puts) {
          XgramLaterRecord before = XgramLaterStore.getLocked(r.key);
          if (r.sameAs(before)) {
            continue;
          }
          XgramLaterStore.putLocked(r);
          changes.add(new Change(r.key, before, r));
        }
      }
      if (removes != null) {
        for (String key : removes) {
          XgramLaterRecord before = XgramLaterStore.getLocked(key);
          if (before == null) {
            continue;
          }
          XgramLaterStore.removeLocked(key);
          changes.add(new Change(key, before, null));
        }
      }
      if (!changes.isEmpty()) {
        XgramLaterStore.rebuildLocked(accountId);
      }
    }
    if (changes.isEmpty()) {
      return changes;
    }
    dispatch(accountId, changes);
    return changes;
  }

  private static void dispatch (int accountId, List<Change> changes) {
    int events = 0;
    List<Long> chatIds = new ArrayList<>();
    final List<XgramLaterRecord> cancel = new ArrayList<>();
    for (Change change : changes) {
      int e = eventsOf(change.before, change.after);
      if (e != 0) {
        events |= e;
        long chatId = (change.after != null ? change.after : change.before).chatId;
        if (!chatIds.contains(chatId)) {
          chatIds.add(chatId);
        }
      }
      if (change.before != null && change.before.isDue() && (change.after == null || !change.after.isDue())) {
        cancel.add(change.before);
      }
    }
    long[] ids = new long[chatIds.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = chatIds.get(i);
    }
    XgramLater.emit(accountId, ids, events | XgramLater.EVENT_RECORDS);
    post(() -> {
      Context ctx = context();
      for (XgramLaterRecord r : cancel) {
        XgramLaterNotifications.cancel(ctx, r.key);
      }
      XgramLaterAlarms.arm(ctx);
    });
  }

  /** Puts one record back exactly as it was ({@code previous}), or removes the key when there was none (Undo). */
  static void restore (int accountId, List<String> keys, List<XgramLaterRecord> previous) {
    List<XgramLaterRecord> puts = new ArrayList<>();
    List<String> removes = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      XgramLaterRecord r = previous.get(i);
      if (r != null) {
        puts.add(r);
      } else {
        removes.add(keys.get(i));
      }
    }
    commit(accountId, puts, removes);
  }

  // Pure transitions (the self-test runs these without a store)

  static XgramLaterRecord toDue (XgramLaterRecord r, long now) {
    return r.buildUpon()
      .state(XgramLaterRecord.STATE_DUE)
      .dueAt(now)
      .clearFlags(XgramLaterRecord.FLAG_SEEN)
      .build();
  }

  static XgramLaterRecord toDone (XgramLaterRecord r, int reason, long now) {
    return r.buildUpon()
      .state(XgramLaterRecord.STATE_DONE)
      .doneReason(reason)
      .doneAt(now)
      .addFlags(XgramLaterRecord.FLAG_SEEN)
      .build();
  }

  /** Back to pending at {@code wakeAt} (Snooze again, Remind again, Change time). */
  static XgramLaterRecord toPending (XgramLaterRecord r, long wakeAt) {
    return r.buildUpon()
      .state(XgramLaterRecord.STATE_PENDING)
      .wakeAt(wakeAt)
      .dueAt(0)
      .doneAt(0)
      .doneReason(XgramLaterRecord.DONE_USER)
      .clearFlags(XgramLaterRecord.FLAG_SEEN | XgramLaterRecord.FLAG_NOTIFIED | XgramLaterRecord.FLAG_SILENT)
      .build();
  }

  /** Whether a new incoming message wakes the snooze early. */
  static boolean qualifies (XgramLaterRecord r, TdApi.Message m) {
    if (m.isOutgoing || m.id <= r.since) {
      return false;
    }
    switch (r.wakeMode) {
      case XgramLaterRecord.WAKE_ANY:
        return true;
      case XgramLaterRecord.WAKE_TO_ME:
        return ChatId.isUserChat(r.chatId) || m.containsUnreadMention;
      case XgramLaterRecord.WAKE_TIME_ONLY:
      default:
        return false;
    }
  }

  /** The chat's state shows an answer the snooze should have woken on (a reply that came while XGram was closed). */
  static boolean wokenByState (XgramLaterRecord r, TdApi.Chat chat) {
    TdApi.Message last = chat.lastMessage;
    if (last == null || last.isOutgoing || last.id <= r.since) {
      return false;
    }
    switch (r.wakeMode) {
      case XgramLaterRecord.WAKE_ANY:
        return true;
      case XgramLaterRecord.WAKE_TO_ME:
        return ChatId.isUserChat(chat.id) || chat.unreadMentionCount > r.mentionsAt;
      case XgramLaterRecord.WAKE_TIME_ONLY:
      default:
        return false;
    }
  }

  /** Whether {@code m} answers the follow-up: anything incoming in a private chat, a reply to the message in groups. */
  static boolean answers (XgramLaterRecord r, TdApi.Message m) {
    if (m.isOutgoing || m.chatId != r.chatId || m.id <= r.since) {
      return false;
    }
    if (ChatId.isUserChat(r.chatId)) {
      return true;
    }
    if (m.replyTo instanceof TdApi.MessageReplyToMessage) {
      TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) m.replyTo;
      return reply.messageId == r.since && (reply.chatId == 0 || reply.chatId == r.chatId);
    }
    return false;
  }

  /** A private chat whose last message is an answer newer than the follow-up's message (read or not). */
  static boolean answeredByState (XgramLaterRecord r, @Nullable TdApi.Chat chat) {
    if (chat == null || !ChatId.isUserChat(chat.id)) {
      return false;
    }
    TdApi.Message last = chat.lastMessage;
    return last != null && !last.isOutgoing && last.id > r.since;
  }

  // Creating

  /** The snapshot of a message: its preview (none for secret or protected chats, or with previews off) and flags. */
  static XgramLaterRecord.Builder messageRecord (Tdlib tdlib, TdApi.Message msg, int kind) {
    long chatId = msg.chatId;
    TdApi.Chat chat = tdlib.chat(chatId);
    int flags = 0;
    boolean secret = ChatId.isSecret(chatId);
    boolean protectedContent = (chat != null && chat.hasProtectedContent) || !msg.canBeSaved;
    boolean previewOff = !tdlib.notifications().needContentPreview(chatId, false);
    if (secret) {
      flags |= XgramLaterRecord.FLAG_SECRET;
    }
    if (protectedContent) {
      flags |= XgramLaterRecord.FLAG_PROTECTED;
    }
    if (previewOff) {
      flags |= XgramLaterRecord.FLAG_NO_PREVIEW;
    }
    String preview = "";
    if (!secret && !protectedContent && !previewOff) {
      try {
        preview = ContentPreview.getChatListPreview(tdlib, chatId, msg, true).buildText(false);
      } catch (Throwable t) {
        preview = "";
      }
      if (preview != null) {
        preview = preview.replace('\n', ' ');
      }
    }
    int topicId = msg.topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) msg.topicId).forumTopicId : 0;
    return new XgramLaterRecord.Builder(tdlib.id(), chatId, msg.id, kind)
      .createdAt(System.currentTimeMillis())
      .since(msg.id)
      .topicId(topicId)
      .flags(flags)
      .title(tdlib.chatTitle(chatId))
      .preview(preview);
  }

  /** A reminder (or a follow-up) of a message; returns the record it replaced, for Undo. */
  @Nullable
  static XgramLaterRecord remind (Tdlib tdlib, TdApi.Message msg, int kind, long wakeAt, @Nullable String note) {
    String key = XgramLaterRecord.key(tdlib.id(), msg.chatId, msg.id);
    XgramLaterRecord previous = XgramLaterStore.get(tdlib.id(), key);
    XgramLaterRecord.Builder b = messageRecord(tdlib, msg, kind).wakeAt(wakeAt);
    if (note != null) {
      b.note(note);
    } else if (previous != null) {
      b.note(previous.note);
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(b.build());
    commit(tdlib.id(), puts, null);
    return previous;
  }

  /** The wake rule of a new snooze of {@code chat}: until-snoozes wake on messages to you (any post in channels). */
  static int wakeModeFor (Tdlib tdlib, TdApi.Chat chat, boolean timed) {
    if (!timed) {
      return tdlib.isChannelChat(chat) ? XgramLaterRecord.WAKE_ANY : XgramLaterRecord.WAKE_TO_ME;
    }
    int mode = XgramLater.wakeEarlyMode();
    return mode == XgramLaterRecord.WAKE_ANY || mode == XgramLaterRecord.WAKE_TIME_ONLY ? mode : XgramLaterRecord.WAKE_TO_ME;
  }

  /** Snoozes chats ({@code wakeAt} 0: until something happens). Returns the records each replaced (null: none). */
  static List<XgramLaterRecord> snoozeChats (Tdlib tdlib, long[] chatIds, long wakeAt, @Nullable String note) {
    long now = System.currentTimeMillis();
    List<XgramLaterRecord> previous = new ArrayList<>(chatIds.length);
    List<XgramLaterRecord> puts = new ArrayList<>(chatIds.length);
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      String key = XgramLaterRecord.key(tdlib.id(), chatId, 0);
      XgramLaterRecord old = XgramLaterStore.get(tdlib.id(), key);
      previous.add(old);
      if (chat == null) {
        continue;
      }
      XgramLaterRecord.Builder b = new XgramLaterRecord.Builder(tdlib.id(), chatId, 0, wakeAt != 0 ? XgramLaterRecord.KIND_SNOOZE : XgramLaterRecord.KIND_SNOOZE_UNTIL)
        .createdAt(now)
        .wakeAt(wakeAt)
        .wakeMode(wakeModeFor(tdlib, chat, wakeAt != 0))
        .since(chat.lastMessage != null ? chat.lastMessage.id : 0)
        .mentionsAt(chat.unreadMentionCount)
        .title(tdlib.chatTitle(chatId))
        .flags(ChatId.isSecret(chatId) ? XgramLaterRecord.FLAG_SECRET : 0);
      if (note != null) {
        b.note(note);
      } else if (old != null) {
        b.note(old.note);
      }
      puts.add(b.build());
    }
    commit(tdlib.id(), puts, null);
    return previous;
  }

  // Due

  private static final Set<String> waking = ConcurrentHashMap.newKeySet();

  /** A snooze woke up (its time passed, or an answer arrived): done on the Later thread, once per key. */
  static void wakeAsync (int accountId, XgramLaterRecord r, boolean byMessage) {
    if (!waking.add(r.key)) {
      return;
    }
    post(() -> {
      try {
        if (byMessage) {
          wakeByMessage(accountId, r.key, r.createdAt);
        } else {
          processDue(System.currentTimeMillis(), true);
        }
      } finally {
        waking.remove(r.key);
      }
    });
  }

  /** Woken by an answer: due with the bell and moved up, no notification of its own (the chat's message notifies). */
  private static void wakeByMessage (int accountId, String key, long createdAt) {
    XgramLaterRecord cur = XgramLaterStore.get(accountId, key);
    if (cur == null || !cur.isSnooze() || !cur.isPending() || cur.createdAt != createdAt) {
      return;
    }
    long now = System.currentTimeMillis();
    XgramLaterRecord.Builder b = toDue(cur, now).buildUpon().doneReason(XgramLaterRecord.DONE_WOKE).addFlags(XgramLaterRecord.FLAG_SILENT);
    Tdlib tdlib = tdlib(accountId);
    if (tdlib != null && isOnScreen(tdlib, cur.chatId)) {
      b = toDone(b.build(), XgramLaterRecord.DONE_WOKE, now).buildUpon();
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(b.build());
    commit(accountId, puts, null);
  }

  /**
   * Every pending timed record whose time came: due, with a notification unless it is on screen or silent (4 or more
   * in one pass: one summary per account). Follow-ups already answered in a private chat complete quietly instead.
   * Later thread.
   */
  static void processDue (long now, boolean interactive) {
    XgramLaterStore.ensureLoaded();
    Context ctx = context();
    for (XgramLaterStore.AccountIndex index : XgramLaterStore.allSnapshots()) {
      if (index.nextWakeAt == 0 || index.nextWakeAt > now + 2000) {
        continue;
      }
      final int accountId = index.accountId;
      List<XgramLaterRecord> candidates = new ArrayList<>();
      for (XgramLaterRecord r : index.byKey.values()) {
        if (r.isPending() && r.isTimed() && r.wakeAt <= now + 2000 && r.kind != XgramLaterRecord.KIND_DISMISS) {
          candidates.add(r);
        }
      }
      if (candidates.isEmpty()) {
        continue;
      }
      Tdlib tdlib = tdlib(accountId);
      List<XgramLaterRecord> puts = new ArrayList<>();
      final List<XgramLaterRecord> notify = new ArrayList<>();
      final List<XgramLaterRecord> inChat = new ArrayList<>();
      for (XgramLaterRecord r : candidates) {
        XgramLaterRecord next;
        if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && tdlib != null && answeredByState(r, tdlib.chat(r.chatId))) {
          next = toDone(r, XgramLaterRecord.DONE_REPLIED, now);
        } else {
          next = toDue(r, now);
          boolean onScreen = tdlib != null && isOnScreen(tdlib, r.chatId);
          if (onScreen) {
            next = r.isSnooze() ? toDone(next, XgramLaterRecord.DONE_OPENED, now) : next.buildUpon().addFlags(XgramLaterRecord.FLAG_SEEN).build();
            inChat.add(next);
          } else if (!r.hasFlag(XgramLaterRecord.FLAG_SILENT)) {
            next = next.buildUpon().addFlags(XgramLaterRecord.FLAG_NOTIFIED).build();
            notify.add(next);
          }
        }
        puts.add(next);
      }
      // Only those still unchanged since they were read
      List<XgramLaterRecord> fresh = new ArrayList<>(puts.size());
      synchronized (XgramLaterStore.LOCK) {
        for (int i = 0; i < puts.size(); i++) {
          if (XgramLaterStore.getLocked(candidates.get(i).key) == candidates.get(i)) {
            fresh.add(puts.get(i));
          }
        }
      }
      notify.retainAll(fresh);
      inChat.retainAll(fresh);
      commit(accountId, fresh, null);
      if (notify.size() >= 4) {
        XgramLaterNotifications.postSummary(ctx, accountId, notify.size());
      } else {
        for (XgramLaterRecord r : notify) {
          XgramLaterNotifications.post(ctx, r);
        }
      }
      if (!inChat.isEmpty() && tdlib != null) {
        final Tdlib finalTdlib = tdlib;
        UI.post(() -> {
          for (XgramLaterRecord r : inChat) {
            if (!r.isSnooze()) {
              XgramLaterPicker.showInChat(finalTdlib, r);
            }
          }
        });
      }
    }
  }

  // On screen

  private static volatile WeakReference<MessagesController> focused;

  static void setFocused (MessagesController c) {
    focused = new WeakReference<>(c);
  }

  /** The chat is open and visible: the focused chat screen or the right pane, with the app in front. */
  static boolean isOnScreen (Tdlib tdlib, long chatId) {
    if (!UI.isResumed()) {
      return false;
    }
    if (com.fahadali.xgram.layout.XgramTwoPane.isHighlighted(tdlib, chatId)) {
      return true;
    }
    WeakReference<MessagesController> ref = focused;
    MessagesController c = ref != null ? ref.get() : null;
    return c != null && !c.isDestroyed() && c.isFocused() && c.tdlib() == tdlib && c.getChatId() == chatId && !c.inPreviewMode() && !c.isInForceTouchMode();
  }

  // Live updates (posted here from the TDLib thread)

  static void onIncoming (Tdlib tdlib, TdApi.Message m) {
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    long now = System.currentTimeMillis();
    XgramLaterRecord snooze = index.chatSnooze.get(m.chatId);
    if (snooze != null && qualifies(snooze, m)) {
      wakeByMessage(tdlib.id(), snooze.key, snooze.createdAt);
    }
    List<XgramLaterRecord> puts = null;
    for (XgramLaterRecord r : index.ofChat(m.chatId)) {
      if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && (r.isPending() || r.isDue()) && answers(r, m)) {
        if (puts == null) {
          puts = new ArrayList<>();
        }
        puts.add(toDone(r, XgramLaterRecord.DONE_REPLIED, now));
      }
    }
    if (puts != null) {
      commit(tdlib.id(), puts, null);
    }
  }

  static void onMentionCount (Tdlib tdlib, long chatId, int unreadMentionCount) {
    XgramLaterRecord snooze = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chatId);
    if (snooze != null && snooze.mentionsAt > unreadMentionCount) {
      List<XgramLaterRecord> puts = new ArrayList<>(1);
      puts.add(snooze.buildUpon().mentionsAt(unreadMentionCount).build());
      commit(tdlib.id(), puts, null);
    }
  }

  static void onDeleted (Tdlib tdlib, long chatId, long[] messageIds) {
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    List<XgramLaterRecord> puts = null;
    long now = System.currentTimeMillis();
    for (long messageId : messageIds) {
      XgramLaterRecord r = index.byKey.get(XgramLaterRecord.key(tdlib.id(), chatId, messageId));
      if (r == null || r.hasFlag(XgramLaterRecord.FLAG_MSG_GONE)) {
        continue;
      }
      XgramLaterRecord next = r.buildUpon().addFlags(XgramLaterRecord.FLAG_MSG_GONE).build();
      if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && !r.isDone()) {
        next = toDone(next, XgramLaterRecord.DONE_EXPIRED, now);
      }
      if (puts == null) {
        puts = new ArrayList<>();
      }
      puts.add(next);
    }
    if (puts != null) {
      commit(tdlib.id(), puts, null);
    }
  }

  /** Private follow-ups answered while XGram was closed, from the cached chats (Later opening). Any thread. */
  static void reconcile (Tdlib tdlib) {
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    List<XgramLaterRecord> puts = null;
    long now = System.currentTimeMillis();
    for (XgramLaterRecord r : index.byKey.values()) {
      if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && (r.isPending() || r.isDue()) && answeredByState(r, tdlib.chat(r.chatId))) {
        if (puts == null) {
          puts = new ArrayList<>();
        }
        puts.add(toDone(r, XgramLaterRecord.DONE_REPLIED, now));
      }
    }
    if (puts != null) {
      final List<XgramLaterRecord> finalPuts = puts;
      post(() -> commit(tdlib.id(), finalPuts, null));
    }
  }

  // Opening a chat

  private static final java.util.WeakHashMap<MessagesController, Long> shownIn = new java.util.WeakHashMap<>();

  /** A chat opened for real: woken snoozes are done, due reminders seen (their bell goes). UI thread. */
  static void onChatOpened (MessagesController c) {
    TdApi.Chat chat = c.getChat();
    if (chat == null || c.inPreviewMode() || c.isInForceTouchMode()) {
      return;
    }
    setFocused(c);
    Tdlib tdlib = c.tdlib();
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    if (!index.chats.contains(chat.id)) {
      return;
    }
    long now = System.currentTimeMillis();
    List<XgramLaterRecord> puts = new ArrayList<>();
    XgramLaterRecord pendingSnooze = null, dueReminder = null;
    for (XgramLaterRecord r : index.ofChat(chat.id)) {
      if (r.isSnooze()) {
        if (r.isPending()) {
          pendingSnooze = r;
        } else if (r.isDue()) {
          puts.add(toDone(r, r.doneReason == XgramLaterRecord.DONE_WOKE ? XgramLaterRecord.DONE_WOKE : XgramLaterRecord.DONE_OPENED, now));
        }
      } else if (r.isDue() && r.isVisibleItem()) {
        if (!r.hasFlag(XgramLaterRecord.FLAG_SEEN)) {
          puts.add(r.buildUpon().addFlags(XgramLaterRecord.FLAG_SEEN).build());
          if (dueReminder == null || r.dueAt > dueReminder.dueAt) {
            dueReminder = r;
          }
        }
      }
    }
    if (!puts.isEmpty()) {
      commit(tdlib.id(), puts, null);
      final List<XgramLaterRecord> seen = new ArrayList<>(puts);
      post(() -> {
        for (XgramLaterRecord r : seen) {
          XgramLaterNotifications.cancel(context(), r.key);
        }
      });
    }
    Long shownChatId = shownIn.get(c);
    if (shownChatId != null && shownChatId == chat.id) {
      return;
    }
    if (dueReminder != null) {
      shownIn.put(c, chat.id);
      XgramLaterPicker.showInChat(tdlib, dueReminder);
    } else if (pendingSnooze != null) {
      shownIn.put(c, chat.id);
      XgramLaterPicker.showSnoozedInChat(c, pendingSnooze);
    }
  }

  // User operations (any thread; commits are synchronous)

  @Nullable
  static XgramLaterRecord current (int accountId, String key) {
    return XgramLaterStore.get(accountId, key);
  }

  @Nullable
  static XgramLaterRecord markDone (int accountId, String key) {
    XgramLaterRecord r = current(accountId, key);
    if (r == null || r.isDone()) {
      return r;
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(toDone(r, XgramLaterRecord.DONE_USER, System.currentTimeMillis()));
    commit(accountId, puts, null);
    return r;
  }

  @Nullable
  static XgramLaterRecord reschedule (int accountId, String key, long wakeAt) {
    XgramLaterRecord r = current(accountId, key);
    if (r == null) {
      return null;
    }
    XgramLaterRecord next = toPending(r, wakeAt);
    if (r.isSnooze()) {
      next = next.buildUpon().kind(wakeAt != 0 ? XgramLaterRecord.KIND_SNOOZE : XgramLaterRecord.KIND_SNOOZE_UNTIL).build();
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(next);
    commit(accountId, puts, null);
    return r;
  }

  @Nullable
  static XgramLaterRecord setNote (int accountId, String key, String note) {
    XgramLaterRecord r = current(accountId, key);
    if (r == null) {
      return null;
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(r.buildUpon().note(note).build());
    commit(accountId, puts, null);
    return r;
  }

  @Nullable
  static XgramLaterRecord delete (int accountId, String key) {
    XgramLaterRecord r = current(accountId, key);
    if (r == null) {
      return null;
    }
    List<String> removes = new ArrayList<>(1);
    removes.add(key);
    commit(accountId, null, removes);
    return r;
  }

  /** Every pending snooze of chats in {@code chatList} (null: any list) comes back now, with the bell. */
  static void wakeAllNow (Tdlib tdlib, @Nullable TdApi.ChatList chatList) {
    long now = System.currentTimeMillis();
    List<XgramLaterRecord> puts = new ArrayList<>();
    for (XgramLaterRecord r : XgramLaterStore.snapshot(tdlib.id()).chatSnooze.values()) {
      if (chatList != null) {
        TdApi.Chat chat = tdlib.chat(r.chatId);
        TdApi.ChatPosition position = chat != null ? ChatPosition.findPosition(chat, chatList) : null;
        if (position == null || position.order == 0) {
          continue;
        }
      }
      puts.add(toDue(r, now).buildUpon().addFlags(XgramLaterRecord.FLAG_SILENT).build());
    }
    commit(tdlib.id(), puts, null);
  }

  static List<XgramLaterRecord> markAllDueDone (int accountId) {
    long now = System.currentTimeMillis();
    List<XgramLaterRecord> before = new ArrayList<>();
    List<XgramLaterRecord> puts = new ArrayList<>();
    for (XgramLaterRecord r : XgramLaterStore.snapshot(accountId).byKey.values()) {
      if (r.isDue() && r.isVisibleItem()) {
        before.add(r);
        puts.add(toDone(r, XgramLaterRecord.DONE_USER, now));
      }
    }
    commit(accountId, puts, null);
    return before;
  }

  static void clearDone (int accountId) {
    List<XgramLaterRecord> removed;
    synchronized (XgramLaterStore.LOCK) {
      removed = XgramLaterStore.clearDoneLocked(accountId);
    }
    if (!removed.isEmpty()) {
      List<Change> changes = new ArrayList<>(removed.size());
      for (XgramLaterRecord r : removed) {
        changes.add(new Change(r.key, r, null));
      }
      dispatch(accountId, changes);
    }
  }

  // Lookups for the screens

  static List<XgramLaterRecord> recordsOf (int accountId) {
    return new ArrayList<>(XgramLaterStore.snapshot(accountId).byKey.values());
  }

  @NonNull
  static Map<Long, XgramLaterRecord> snoozesOf (int accountId) {
    return new HashMap<>(XgramLaterStore.snapshot(accountId).chatSnooze);
  }
}
