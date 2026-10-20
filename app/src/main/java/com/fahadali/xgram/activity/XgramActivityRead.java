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

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.ghost.XgramGhost;
import com.fahadali.xgram.ghost.XgramLocalRead;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The only place in Activity that tells Telegram something was read (ReadAllChatMentions, ReadAllChatReactions, the
 * Replies chat's read position). Always the user's action, never in a chat where ghost mode applies (decided per chat
 * when it runs): there only this device marks it read (and ghost mode's local read drops the chat's @ badge).
 */
final class XgramActivityRead {
  private XgramActivityRead () { }

  private static final String TAG = XgramActivityStore.TAG;
  private static final long SPACING_MS = 300L;
  /** Chats one Mark all as read reads on the server; the rest stay unread (here too) for the next tap. */
  static final int MAX_CHATS = 100;

  // One row

  /**
   * Mark as read on one row: read here, and on the server only when one call reads exactly this row (the chat's
   * server count for its group is 1 and this is the only New item of that group for the chat).
   */
  @UiThread
  static void markOne (@NonNull Tdlib tdlib, @NonNull XgramActivityRows.Row row) {
    final int accountId = tdlib.id();
    final XgramActivityStore store = XgramActivityStore.instance();
    final XgramActivityStore.Summary summary = store.summary(accountId); // before this read lands
    List<XgramActivityItem> unread = new ArrayList<>();
    for (XgramActivityItem item : row.items) {
      if (item.isNew()) {
        unread.add(item);
      }
    }
    if (unread.isEmpty()) {
      return;
    }
    final XgramActivityItem item = unread.get(0);
    final long chatId = item.chatId;
    final boolean ghosted = XgramGhost.dontReadMessages(tdlib, chatId);
    store.markItemsRead(accountId, unread, ghosted);
    if (ghosted) {
      viewMentionsLocally(tdlib, unread);
      return;
    }
    if (unread.size() != 1 || item.hasFlag(XgramActivityItem.FLAG_REPLIES_CHAT) || item.isUserUnread()) {
      return; // a Mark as unread is read on the server already: the one unread there is another message
    }
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return;
    }
    if (item.isReaction()) {
      if (chat.unreadReactionCount == 1 && summary.syncCount(chatId, XgramActivityItem.MASK_REACTION) == 1) {
        tdlib.send(new TdApi.ReadAllChatReactions(chatId), tdlib.typedOkHandler());
      }
    } else if (chat.unreadMentionCount == 1 && summary.syncCount(chatId, XgramActivityItem.MASK_MENTION_GROUP) == 1) {
      tdlib.send(new TdApi.ReadAllChatMentions(chatId), tdlib.typedOkHandler());
    }
  }

  // Mark all as read

  /** What Mark all as read would do with the rows shown. */
  static final class Plan {
    /** Chat by chat, in the order shown. */
    final LinkedHashMap<Long, ChatPlan> chats = new LinkedHashMap<>();
    final List<XgramActivityItem> unread = new ArrayList<>();
    int ghosted;

    int serverChats () {
      return chats.size() - ghosted;
    }
  }

  static final class ChatPlan {
    final long chatId;
    boolean mentions, reactions;
    final List<Long> repliesChatIds = new ArrayList<>();
    final List<XgramActivityItem> mentionItems = new ArrayList<>();
    boolean ghosted;

    ChatPlan (long chatId) {
      this.chatId = chatId;
    }
  }

  /**
   * The plan for the New items among {@code items} (what the filter shows): per chat, the calls that read exactly
   * what is shown there. Mentions and replies are one server flag (one call reads both); the Mentions and Replies
   * filters only read mentions, the Reactions filter only reactions.
   */
  @UiThread
  @NonNull
  static Plan plan (@NonNull Tdlib tdlib, @NonNull List<XgramActivityItem> items, int filter) {
    Plan plan = new Plan();
    for (XgramActivityItem item : items) {
      if (!item.isNew()) {
        continue;
      }
      plan.unread.add(item);
      ChatPlan chat = plan.chats.get(item.chatId);
      if (chat == null) {
        chat = new ChatPlan(item.chatId);
        chat.ghosted = XgramGhost.dontReadMessages(tdlib, item.chatId);
        plan.chats.put(item.chatId, chat);
        if (chat.ghosted) {
          plan.ghosted++;
        }
      }
      if (item.hasFlag(XgramActivityItem.FLAG_REPLIES_CHAT)) {
        chat.repliesChatIds.add(item.messageId);
      } else if (item.isReaction()) {
        if (filter == XgramActivityStore.FILTER_ALL || filter == XgramActivityStore.FILTER_REACTIONS) {
          chat.reactions = true;
        }
      } else {
        if (filter != XgramActivityStore.FILTER_REACTIONS) {
          chat.mentions = true;
          chat.mentionItems.add(item);
        }
      }
    }
    return plan;
  }

  interface Progress {
    /**
     * @param stoppedChats chats still unread on the server: after a flood wait ({@code flood}), or beyond the 100 one
     * tap reads (a second tap reads them)
     */
    @UiThread
    void onDone (int stoppedChats, boolean flood);
  }

  /**
   * Reads the plan: here at once, then chat by chat on the server (at most 100 chats, 300 ms apart), stopping at the
   * first flood wait. Ghosted chats are read on this device only. Chats beyond the 100 stay New here as they are on
   * the server. Returns what the read put into ghost mode's local mentions, for an Undo.
   */
  @UiThread
  @NonNull
  static LocalMarks run (@NonNull Tdlib tdlib, @NonNull Plan plan, @Nullable Progress progress) {
    final int accountId = tdlib.id();
    final XgramActivityStore store = XgramActivityStore.instance();
    // The chats read on the server this time, in the order shown
    final List<ChatPlan> steps = new ArrayList<>();
    final java.util.Set<Long> queued = new java.util.HashSet<>();
    int left = 0;
    for (ChatPlan chat : plan.chats.values()) {
      if (chat.ghosted) {
        continue;
      }
      if (steps.size() < MAX_CHATS) {
        steps.add(chat);
        queued.add(chat.chatId);
      } else {
        left++;
      }
    }
    // Here first (read locally where ghost mode applies)
    List<XgramActivityItem> local = new ArrayList<>(), server = new ArrayList<>();
    for (XgramActivityItem item : plan.unread) {
      ChatPlan chat = plan.chats.get(item.chatId);
      if (chat != null && chat.ghosted) {
        local.add(item);
      } else if (queued.contains(item.chatId)) {
        server.add(item);
      }
    }
    LocalMarks marks = new LocalMarks(tdlib);
    if (!local.isEmpty()) {
      store.markItemsRead(accountId, local, true);
      marks = viewMentionsLocally(tdlib, local);
    }
    if (!server.isEmpty()) {
      store.markItemsRead(accountId, server, false);
    }
    if (steps.isEmpty()) {
      if (progress != null) {
        progress.onDone(0, false);
      }
      return marks;
    }
    final int notQueued = left;
    store.executor.execute(() -> step(tdlib, steps, 0, notQueued, progress));
    return marks;
  }

  /** Store thread: one chat's calls, then the next one 300 ms later, unless Telegram asked to wait. */
  private static void step (Tdlib tdlib, List<ChatPlan> queue, int index, int left, @Nullable Progress progress) {
    if (index >= queue.size()) {
      finish(progress, left, false);
      return;
    }
    final ChatPlan chat = queue.get(index);
    if (debugFlood(tdlib)) {
      finish(progress, queue.size() - index + left, true); // a simulated wait: as a 429 on this chat, nothing sent
      return;
    }
    try {
      // Decided again now: ghost mode may have come on meanwhile
      if (XgramGhost.dontReadMessages(tdlib, chat.chatId)) {
        next(tdlib, queue, index, left, progress);
        return;
      }
      if (!chat.repliesChatIds.isEmpty() && tdlib.isRepliesChat(chat.chatId)) {
        long[] ids = new long[chat.repliesChatIds.size()];
        for (int i = 0; i < ids.length; i++) {
          ids[i] = chat.repliesChatIds.get(i);
        }
        tdlib.readMessages(chat.chatId, ids, new TdApi.MessageSourceChatList());
      }
      TdApi.Chat current = tdlib.chat(chat.chatId);
      final boolean mentions = chat.mentions && current != null && current.unreadMentionCount > 0;
      final boolean reactions = chat.reactions && current != null && current.unreadReactionCount > 0;
      if (mentions) {
        tdlib.send(new TdApi.ReadAllChatMentions(chat.chatId), (ok, error) -> {
          if (isFlood(error)) {
            finish(progress, queue.size() - index + left, true);
            return;
          }
          if (reactions) {
            XgramActivityStore.instance().executor.schedule(() -> readReactions(tdlib, queue, index, left, progress), SPACING_MS, TimeUnit.MILLISECONDS);
          } else {
            next(tdlib, queue, index, left, progress);
          }
        });
      } else if (reactions) {
        readReactions(tdlib, queue, index, left, progress);
      } else {
        next(tdlib, queue, index, left, progress);
      }
    } catch (Throwable t) {
      Log.e(TAG, "Mark all as read", t);
      next(tdlib, queue, index, left, progress);
    }
  }

  private static void readReactions (Tdlib tdlib, List<ChatPlan> queue, int index, int left, @Nullable Progress progress) {
    final ChatPlan chat = queue.get(index);
    if (debugFlood(tdlib)) {
      finish(progress, queue.size() - index + left, true);
      return;
    }
    tdlib.send(new TdApi.ReadAllChatReactions(chat.chatId), (ok, error) -> {
      if (isFlood(error)) {
        finish(progress, queue.size() - index + left, true);
      } else {
        next(tdlib, queue, index, left, progress);
      }
    });
  }

  private static void next (Tdlib tdlib, List<ChatPlan> queue, int index, int left, @Nullable Progress progress) {
    XgramActivityStore.instance().executor.schedule(() -> step(tdlib, queue, index + 1, left, progress), SPACING_MS, TimeUnit.MILLISECONDS);
  }

  private static boolean isFlood (@Nullable TdApi.Error error) {
    return error != null && (error.code == 429 || XgramActivityClassifier.retryAfterSeconds(error) >= 0);
  }

  // Debug builds: Simulate Telegram wait (Chat list > Activity) stops Mark all as read too

  private static volatile int debugFloodAccountId = -1;
  /** SystemClock.elapsedRealtime() until which the account's calls are answered with a wait. */
  private static volatile long debugFloodUntil;

  /** Debug builds: for the next {@code ms}, Mark all as read on this account stops as at a 429, before sending. */
  @AnyThread
  static void injectFlood (int accountId, long ms) {
    if (!BuildConfig.DEBUG) {
      return;
    }
    debugFloodAccountId = accountId;
    debugFloodUntil = SystemClock.elapsedRealtime() + ms;
  }

  private static boolean debugFlood (Tdlib tdlib) {
    return BuildConfig.DEBUG && debugFloodAccountId == tdlib.id() && SystemClock.elapsedRealtime() < debugFloodUntil;
  }

  private static void finish (@Nullable Progress progress, int stoppedChats, boolean flood) {
    if (progress != null) {
      UI.post(() -> progress.onDone(stoppedChats, flood));
    }
  }

  /** What a local read put into ghost mode's mentions set, so that an Undo takes exactly that out again. */
  static final class LocalMarks {
    private final Tdlib tdlib;
    private final List<Mark> applied = new ArrayList<>();
    private boolean undone;

    LocalMarks (@NonNull Tdlib tdlib) {
      this.tdlib = tdlib;
    }

    /** UI thread: the mentions count as unread here again, and checks still running put nothing in. */
    @UiThread
    void undo () {
      undone = true;
      for (Mark mark : applied) {
        XgramLocalRead.unmarkMentionsViewed(tdlib, mark.chatId, mark.topicId, mark.ids);
      }
      applied.clear();
    }
  }

  private static final class Mark {
    final long chatId;
    final int topicId;
    final long[] ids;

    Mark (long chatId, int topicId, long[] ids) {
      this.chatId = chatId;
      this.topicId = topicId;
      this.ids = ids;
    }
  }

  /**
   * Ghost mode's local read: the mentions count as viewed here, so the chat list's @ badge drops (nothing is sent).
   * Only messages TDLib still has as an unread mention (GetMessageLocally, no network): one read elsewhere, or marked
   * unread here only, would keep a new mention off the badge for good.
   */
  @UiThread
  @NonNull
  static LocalMarks viewMentionsLocally (@NonNull Tdlib tdlib, @NonNull List<XgramActivityItem> items) {
    final LocalMarks marks = new LocalMarks(tdlib);
    if (!XgramLocalRead.isActive()) {
      return marks;
    }
    Map<String, List<Long>> byTopic = new HashMap<>();
    Map<String, long[]> keys = new HashMap<>();
    for (XgramActivityItem item : items) {
      if (item.isReaction() || item.hasFlag(XgramActivityItem.FLAG_REPLIES_CHAT) || item.isUserUnread()) {
        continue;
      }
      String key = item.chatId + "_" + item.topicId;
      List<Long> ids = byTopic.get(key);
      if (ids == null) {
        ids = new ArrayList<>();
        byTopic.put(key, ids);
        keys.put(key, new long[] {item.chatId, item.topicId});
      }
      ids.add(item.messageId);
    }
    for (Map.Entry<String, List<Long>> entry : byTopic.entrySet()) {
      long[] key = keys.get(entry.getKey());
      List<Long> list = entry.getValue();
      long[] ids = new long[list.size()];
      for (int i = 0; i < ids.length; i++) {
        ids[i] = list.get(i);
      }
      if (key != null) {
        markIfStillUnread(tdlib, key[0], (int) key[1], ids, marks);
      }
    }
    return marks;
  }

  /** Asks TDLib (locally) which of {@code ids} are still unread mentions, then marks those viewed in one go. */
  @UiThread
  private static void markIfStillUnread (@NonNull Tdlib tdlib, long chatId, int topicId, @NonNull long[] ids, @NonNull LocalMarks marks) {
    final long[] unread = new long[ids.length];
    final int[] counts = {ids.length, 0}; // answers pending, unread found (UI thread only)
    for (long id : ids) {
      tdlib.send(new TdApi.GetMessageLocally(chatId, id), (message, error) -> UI.post(() -> {
        if (message != null && message.containsUnreadMention) {
          unread[counts[1]++] = id;
        }
        if (--counts[0] == 0 && counts[1] > 0 && !marks.undone) {
          long[] added = XgramLocalRead.markMentionsViewed(tdlib, chatId, topicId, java.util.Arrays.copyOf(unread, counts[1]));
          if (added != null && added.length > 0) {
            marks.applied.add(new Mark(chatId, topicId, added));
          }
        }
      }));
    }
  }
}
