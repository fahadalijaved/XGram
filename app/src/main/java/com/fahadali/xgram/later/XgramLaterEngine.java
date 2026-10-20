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
    return commit(accountId, puts, removes, null);
  }

  /**
   * {@link #commit} where each put only applies while its key still holds {@code expected} (same index; the record a
   * change was computed from), so a change made meanwhile is never overwritten.
   */
  static List<Change> commit (int accountId, @Nullable List<XgramLaterRecord> puts, @Nullable List<String> removes, @Nullable List<XgramLaterRecord> expected) {
    XgramLaterStore.ensureLoaded();
    List<Change> changes = new ArrayList<>();
    synchronized (XgramLaterStore.LOCK) {
      if (puts != null) {
        for (int i = 0; i < puts.size(); i++) {
          XgramLaterRecord r = puts.get(i);
          XgramLaterRecord before = XgramLaterStore.getLocked(r.key);
          if (expected != null && before != expected.get(i)) {
            continue;
          }
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
      if (XgramLaterStore.snapshot(accountId).bells.isEmpty()) {
        // Nothing due and unseen is left: the "n reminders are due" summary goes too
        XgramLaterNotifications.cancelSummary(ctx, accountId);
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
    return qualifies(r, m.isOutgoing, m.id, m.containsUnreadMention);
  }

  /**
   * {@link #qualifies} from a message's parts: also for a notification TDLib built from a push, which has no message
   * object ({@code mention}: a mention or a reply to you).
   */
  static boolean qualifies (XgramLaterRecord r, boolean outgoing, long messageId, boolean mention) {
    if (outgoing || messageId <= r.since) {
      return false;
    }
    switch (r.wakeMode) {
      case XgramLaterRecord.WAKE_ANY:
        return true;
      case XgramLaterRecord.WAKE_TO_ME:
        return ChatId.isUserChat(r.chatId) || mention;
      case XgramLaterRecord.WAKE_TIME_ONLY:
      default:
        return false;
    }
  }

  /** The chat's state shows an answer the snooze should have woken on (a reply that came while XGram was closed). */
  static boolean wokenByState (XgramLaterRecord r, TdApi.Chat chat) {
    if (r.hasFlag(XgramLaterRecord.FLAG_RESYNC)) {
      return false; // its last message isn't known yet: anything in the chat would look new
    }
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

  /**
   * The wake rule of a new snooze of {@code chat}: until-snoozes wake on messages to you (any post in channels), timed
   * ones follow the setting. Without the chat an until-snooze wakes on messages to you.
   */
  static int wakeModeFor (@Nullable Tdlib tdlib, @Nullable TdApi.Chat chat, boolean timed) {
    if (!timed) {
      return tdlib != null && chat != null && tdlib.isChannelChat(chat) ? XgramLaterRecord.WAKE_ANY : XgramLaterRecord.WAKE_TO_ME;
    }
    int mode = XgramLater.wakeEarlyMode();
    return mode == XgramLaterRecord.WAKE_ANY || mode == XgramLaterRecord.WAKE_TIME_ONLY ? mode : XgramLaterRecord.WAKE_TO_ME;
  }

  /**
   * A snooze that starts again (Snooze again, Change time, Remind again, "Snooze 1 h"): the wake rule, the last message
   * and the mention count are taken from the chat now, as for a new snooze, so an answer that is already there doesn't
   * wake it at once. Without the chat (no TDLib in this process) they are taken when the chat list first sees it.
   */
  static XgramLaterRecord restartSnooze (XgramLaterRecord r, @Nullable Tdlib tdlib) {
    TdApi.Chat chat = tdlib != null ? tdlib.chat(r.chatId) : null;
    if (chat == null) {
      int mode = r.isTimed() ? wakeModeFor(null, null, true) : r.wakeMode == XgramLaterRecord.WAKE_TIME_ONLY ? XgramLaterRecord.WAKE_TO_ME : r.wakeMode;
      return r.buildUpon().wakeMode(mode).addFlags(XgramLaterRecord.FLAG_RESYNC).build();
    }
    return r.buildUpon()
      .since(chat.lastMessage != null ? chat.lastMessage.id : r.since)
      .mentionsAt(chat.unreadMentionCount)
      .wakeMode(wakeModeFor(tdlib, chat, r.isTimed()))
      .clearFlags(XgramLaterRecord.FLAG_RESYNC)
      .build();
  }

  private static final Set<String> resyncing = ConcurrentHashMap.newKeySet();

  /** A snooze marked {@link XgramLaterRecord#FLAG_RESYNC} takes its chat's state now (Later thread, once per key). */
  static void resyncAsync (Tdlib tdlib, XgramLaterRecord r) {
    if (!resyncing.add(r.key)) {
      return;
    }
    post(() -> {
      try {
        XgramLaterRecord cur = XgramLaterStore.get(tdlib.id(), r.key);
        if (cur == null || !cur.isSnooze() || !cur.isPending() || !cur.hasFlag(XgramLaterRecord.FLAG_RESYNC) || tdlib.chat(cur.chatId) == null) {
          return;
        }
        List<XgramLaterRecord> puts = new ArrayList<>(1);
        puts.add(restartSnooze(cur, tdlib));
        List<XgramLaterRecord> expected = new ArrayList<>(1);
        expected.add(cur);
        commit(tdlib.id(), puts, null, expected);
      } finally {
        resyncing.remove(r.key);
      }
    });
  }

  /** Four or more items due in one pass give one summary per account instead of a notification each. */
  static boolean needsSummary (int dueCount) {
    return dueCount >= 4;
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
    if (tdlib != null && isOnScreen(tdlib, cur.chatId, 0)) {
      b = toDone(b.build(), XgramLaterRecord.DONE_WOKE, now).buildUpon();
    }
    List<XgramLaterRecord> puts = new ArrayList<>(1);
    puts.add(b.build());
    List<XgramLaterRecord> expected = new ArrayList<>(1);
    expected.add(cur);
    commit(accountId, puts, null, expected);
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
          boolean onScreen = tdlib != null && isOnScreen(tdlib, r.chatId, r.isSnooze() ? 0 : r.topicId);
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
      // Only those still unchanged since they were read (a change made meanwhile wins)
      List<XgramLaterRecord> applied = new ArrayList<>(puts.size());
      for (Change change : commit(accountId, puts, null, candidates)) {
        applied.add(change.after);
      }
      notify.retainAll(applied);
      inChat.retainAll(applied);
      if (needsSummary(notify.size())) {
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

  /**
   * The chat (its forum topic {@code topicId}, 0: any) is open and visible, with the app in front: the chat screen on
   * top of its stack, on either pane. A chat the right pane only has underneath another screen (Later, Settings) is not.
   */
  static boolean isOnScreen (Tdlib tdlib, long chatId, int topicId) {
    if (!UI.isResumed()) {
      return false;
    }
    WeakReference<MessagesController> ref = focused;
    MessagesController c = ref != null ? ref.get() : null;
    return c != null && !c.isDestroyed() && c.isFocused() && c.tdlib() == tdlib && c.getChatId() == chatId && !c.inPreviewMode() && !c.isInForceTouchMode() && showsTopic(c, topicId);
  }

  /** Whether the chat screen shows the forum topic {@code topicId} (0, or the whole chat: yes). */
  static boolean showsTopic (MessagesController c, int topicId) {
    if (topicId == 0) {
      return true;
    }
    TdApi.MessageTopic topic = c.getMessageTopicId();
    if (topic == null) {
      return true;
    }
    return topic instanceof TdApi.MessageTopicForum && ((TdApi.MessageTopicForum) topic).forumTopicId == topicId;
  }

  // Live updates (posted here from the TDLib thread). Each change applies only to the record it was computed from, so
  // a change made meanwhile (a delete, an unsnooze, a logout) is never overwritten or brought back.

  static void onIncoming (Tdlib tdlib, TdApi.Message m) {
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    long now = System.currentTimeMillis();
    XgramLaterRecord snooze = index.chatSnooze.get(m.chatId);
    if (snooze != null && qualifies(snooze, m)) {
      wakeByMessage(tdlib.id(), snooze.key, snooze.createdAt);
    }
    List<XgramLaterRecord> puts = null, expected = null;
    for (XgramLaterRecord r : index.ofChat(m.chatId)) {
      if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && (r.isPending() || r.isDue()) && answers(r, m)) {
        if (puts == null) {
          puts = new ArrayList<>();
          expected = new ArrayList<>();
        }
        puts.add(toDone(r, XgramLaterRecord.DONE_REPLIED, now));
        expected.add(r);
      }
    }
    if (puts != null) {
      commit(tdlib.id(), puts, null, expected);
    }
  }

  static void onMentionCount (Tdlib tdlib, long chatId, int unreadMentionCount) {
    XgramLaterRecord snooze = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chatId);
    if (snooze != null && snooze.mentionsAt > unreadMentionCount) {
      List<XgramLaterRecord> puts = new ArrayList<>(1);
      puts.add(snooze.buildUpon().mentionsAt(unreadMentionCount).build());
      List<XgramLaterRecord> expected = new ArrayList<>(1);
      expected.add(snooze);
      commit(tdlib.id(), puts, null, expected);
    }
  }

  static void onDeleted (Tdlib tdlib, long chatId, long[] messageIds) {
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    List<XgramLaterRecord> puts = null, expected = null;
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
        expected = new ArrayList<>();
      }
      puts.add(next);
      expected.add(r);
    }
    if (puts != null) {
      commit(tdlib.id(), puts, null, expected);
    }
  }

  /**
   * The chat's last message changed: also when it came without updateNewMessage (a gap after a long time offline). A
   * hidden snooze wakes when its chat now shows an answer; a private follow-up whose chat shows one is done.
   */
  static void onTopMessage (Tdlib tdlib, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return;
    }
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    XgramLaterRecord snooze = index.chatSnooze.get(chatId);
    if (snooze != null && wokenByState(snooze, chat)) {
      wakeByMessage(tdlib.id(), snooze.key, snooze.createdAt);
    }
    completeAnswered(tdlib, index.ofChat(chatId));
  }

  /** Private follow-ups answered while XGram was closed, from the cached chats (the list's first load, Later opening). Any thread. */
  static void reconcile (Tdlib tdlib) {
    post(() -> completeAnswered(tdlib, XgramLaterStore.snapshot(tdlib.id()).byKey.values()));
  }

  /** Pending or due private follow-ups whose chat already shows an answer: done as replied. Later thread. */
  private static void completeAnswered (Tdlib tdlib, Iterable<XgramLaterRecord> records) {
    List<XgramLaterRecord> puts = null, expected = null;
    long now = System.currentTimeMillis();
    for (XgramLaterRecord r : records) {
      if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && (r.isPending() || r.isDue()) && answeredByState(r, tdlib.chat(r.chatId))) {
        if (puts == null) {
          puts = new ArrayList<>();
          expected = new ArrayList<>();
        }
        puts.add(toDone(r, XgramLaterRecord.DONE_REPLIED, now));
        expected.add(r);
      }
    }
    if (puts != null) {
      commit(tdlib.id(), puts, null, expected);
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
      } else if (r.isDue() && r.isVisibleItem() && showsTopic(c, r.topicId)) {
        // Only what this screen shows: a reminder in another forum topic stays due
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
      next = restartSnooze(next.buildUpon().kind(wakeAt != 0 ? XgramLaterRecord.KIND_SNOOZE : XgramLaterRecord.KIND_SNOOZE_UNTIL).build(), tdlib(accountId));
    } else if (r.kind == XgramLaterRecord.KIND_FOLLOWUP && r.isDone()) {
      // "Remind again" on a finished follow-up: a plain reminder (as a follow-up the reply already there would end it)
      next = next.buildUpon().kind(XgramLaterRecord.KIND_REMIND).build();
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
