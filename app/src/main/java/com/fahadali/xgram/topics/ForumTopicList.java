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
package com.fahadali.xgram.topics;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.IntSet;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.reference.ReferenceList;

/**
 * Live, sorted and paged list of topics of one forum chat.
 *
 * TDLib reports pin, read and notification changes through {@code updateForumTopic}, but not changes to
 * {@code order}, {@code last_message} or {@code unread_count}. Topics affected by new, sent or deleted
 * messages are therefore refreshed with {@code getForumTopic}, batched to one request per topic per
 * {@link #REFRESH_DELAY_MS}.
 */
public final class ForumTopicList implements ChatListener, MessageListener, Destroyable, com.fahadali.xgram.ghost.XgramLocalRead.Listener {
  public interface Listener {
    default void onTopicsLoaded (ForumTopicList list) { }
    default void onTopicInserted (ForumTopicList list, int index) { }
    default void onTopicRemoved (ForumTopicList list, int index) { }
    default void onTopicMoved (ForumTopicList list, int fromIndex, int toIndex) { }
    default void onTopicChanged (ForumTopicList list, int index) { }
  }

  private static final int FIRST_PAGE_SIZE = 20;
  private static final int NEXT_PAGE_SIZE = 100;
  private static final long REFRESH_DELAY_MS = 300;
  /** More pending topics than this are refreshed with one page request. */
  private static final int BATCH_THRESHOLD = 5;
  /** At most this many single-topic requests after a page request. */
  private static final int MAX_SINGLE_FETCHES = 10;

  /** The first page is fetched again at most this often (uptime ms) to follow pin and order changes made elsewhere. */
  private static final long RESYNC_INTERVAL_MS = 30_000;

  /**
   * Pinned topics in their server order (ids). TDLib's {@code order} only follows the last message and the draft,
   * never pinning, so this list is what keeps pinned topics on top and in the order the user chose.
   */
  private final IntList pinnedOrder = new IntList(8);

  private final Comparator<TdApi.ForumTopic> comparator = (a, b) -> {
    // Hidden General goes first, like the official app's pull-down row.
    boolean aHidden = a.info.isHidden, bHidden = b.info.isHidden;
    if (aHidden != bHidden) {
      return aHidden ? -1 : 1;
    }
    if (a.isPinned != b.isPinned) {
      return a.isPinned ? -1 : 1;
    }
    if (a.isPinned) {
      int aIndex = pinnedIndex(a.info.forumTopicId), bIndex = pinnedIndex(b.info.forumTopicId);
      if (aIndex != bIndex) {
        return Integer.compare(aIndex, bIndex);
      }
    }
    if (a.order != b.order) {
      return Long.compare(b.order, a.order);
    }
    return Integer.compare(b.info.forumTopicId, a.info.forumTopicId);
  };

  /** Position of a pinned topic in {@link #pinnedOrder}; topics the order doesn't know go after the known ones. */
  private int pinnedIndex (int forumTopicId) {
    int index = pinnedOrder.indexOf(forumTopicId);
    return index == -1 ? Integer.MAX_VALUE : index;
  }

  // Recently opened forums stay loaded and live-updated, so going back into a forum shows its topics
  // immediately instead of loading them again.
  private static final int CACHE_SIZE = 8;
  private static final LinkedHashMap<String, ForumTopicList> cache = new LinkedHashMap<>(16, .75f, true);

  /** Returns the cached topic list of the chat, or a new one. Call {@link #loadMore()} if it isn't loaded. */
  @UiThread
  public static ForumTopicList obtain (Tdlib tdlib, long chatId) {
    String key = tdlib.id() + "_" + chatId;
    ForumTopicList list = cache.get(key);
    if (list == null || list.isDestroyed) {
      list = new ForumTopicList(tdlib, chatId);
      cache.put(key, list);
      trimCache();
    } else if (list.loadFailed) {
      // Reset in place: screens still bound to this instance keep a list that loads and updates again. Replacing it
      // would leave them a dead, empty one, as they don't obtain the list again for the same chat.
      list.resetAfterFailure();
    }
    return list;
  }

  /** Lets a list whose first page failed try again: not loaded, nothing reached, offsets at the start. */
  @UiThread
  private void resetAfterFailure () {
    loadFailed = false;
    endReached = false;
    isLoaded = false;
    isLoading = false;
    loadRetryAfter = 0;
    nextOffsetDate = 0;
    nextOffsetMessageId = 0;
    nextOffsetForumTopicId = 0;
  }

  /** A topic from the cached list of the chat, without creating or loading a list. */
  @UiThread
  @Nullable
  public static TdApi.ForumTopic peekTopic (Tdlib tdlib, long chatId, int forumTopicId) {
    ForumTopicList list = cache.get(tdlib.id() + "_" + chatId);
    return list != null && !list.isDestroyed ? list.find(forumTopicId) : null;
  }

  /** True while a cached, live list of the chat holds all of its topics: its sum is then the forum's badge. */
  @UiThread
  public static boolean isFullyLoaded (Tdlib tdlib, long chatId) {
    ForumTopicList list = cache.get(tdlib.id() + "_" + chatId);
    return list != null && !list.isDestroyed && list.isLoaded && list.endReached && !list.loadFailed;
  }

  private static void trimCache () {
    Iterator<ForumTopicList> it = cache.values().iterator();
    while (cache.size() > CACHE_SIZE && it.hasNext()) {
      ForumTopicList eldest = it.next();
      if (eldest.listeners.isEmpty()) {
        it.remove();
        eldest.performDestroy();
      }
    }
  }

  private final Tdlib tdlib;
  private final long chatId;
  private final List<TdApi.ForumTopic> topics = new ArrayList<>();
  private final ReferenceList<Listener> listeners = new ReferenceList<>();

  private int nextOffsetDate;
  private long nextOffsetMessageId;
  private int nextOffsetForumTopicId;
  private boolean isLoading, endReached, isLoaded, isDestroyed, loadFailed;

  private final IntSet pendingRefresh = new IntSet();
  private boolean refreshScheduled;
  /** A page that failed to load isn't asked again before this (uptime ms), so scrolling can't hammer an offline server. */
  private long loadRetryAfter;
  /** Topics whose fetch failed once (not as deleted): they get one more try. */
  private final java.util.Set<Integer> retriedOnce = new java.util.HashSet<>();

  public ForumTopicList (Tdlib tdlib, long chatId) {
    this.tdlib = tdlib;
    this.chatId = chatId;
    tdlib.listeners().subscribeToChatUpdates(chatId, this);
    tdlib.listeners().subscribeToMessageUpdates(chatId, this);
    com.fahadali.xgram.ghost.XgramLocalRead.addListener(this);
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat != null) {
      // The chat's read state when the list was made: the first read update compares with it (refreshing every unread topic otherwise).
      lastChatReadInboxMessageId = chat.lastReadInboxMessageId;
    }
  }

  public long chatId () {
    return chatId;
  }

  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  public int size () {
    return topics.size();
  }

  public TdApi.ForumTopic get (int index) {
    return topics.get(index);
  }

  public boolean isLoaded () {
    return isLoaded;
  }

  public boolean isEndReached () {
    return endReached;
  }

  public boolean canLoadMore () {
    return !endReached && !isLoading && !isDestroyed && android.os.SystemClock.uptimeMillis() >= loadRetryAfter;
  }

  public int indexOf (int forumTopicId) {
    for (int i = 0; i < topics.size(); i++) {
      if (topics.get(i).info.forumTopicId == forumTopicId) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  public TdApi.ForumTopic find (int forumTopicId) {
    int index = indexOf(forumTopicId);
    return index != -1 ? topics.get(index) : null;
  }

  // Loading

  @UiThread
  public void loadMore () {
    if (!canLoadMore()) {
      return;
    }
    isLoading = true;
    int limit = topics.isEmpty() ? FIRST_PAGE_SIZE : NEXT_PAGE_SIZE;
    // The server lists pinned topics first on the first page: that is the only place their order comes from.
    final boolean firstPage = nextOffsetDate == 0 && nextOffsetMessageId == 0 && nextOffsetForumTopicId == 0;
    tdlib.send(new TdApi.GetForumTopics(chatId, "", nextOffsetDate, nextOffsetMessageId, nextOffsetForumTopicId, limit), (result, error) -> tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      isLoading = false;
      if (error != null) {
        if (topics.isEmpty()) {
          endReached = true;
          loadFailed = true;
        } else {
          // Keep what's loaded and allow scrolling to try the next page again a bit later.
          loadRetryAfter = android.os.SystemClock.uptimeMillis() + XgramForums.retryAfterMs(error, 5000);
        }
      } else {
        for (TdApi.ForumTopic topic : result.topics) {
          XgramTopicReadOutbox.set(tdlib, chatId, topic);
          XgramTopicMute.onSettings(tdlib, chatId, topic);
          if (indexOf(topic.info.forumTopicId) == -1) {
            topics.add(XgramForums.normalizeUnread(tdlib, topic));
          }
        }
        if (firstPage) {
          replacePinnedOrder(serverPinnedIds(result.topics));
        }
        Collections.sort(topics, comparator);
        nextOffsetDate = result.nextOffsetDate;
        nextOffsetMessageId = result.nextOffsetMessageId;
        nextOffsetForumTopicId = result.nextOffsetForumTopicId;
        endReached = result.topics.length == 0 ||
          (nextOffsetDate == 0 && nextOffsetMessageId == 0 && nextOffsetForumTopicId == 0) ||
          topics.size() >= result.totalCount;
      }
      isLoaded = true;
      for (Listener listener : listeners) {
        listener.onTopicsLoaded(this);
      }
      publishUnread();
    }));
  }

  // Refreshing single topics

  private void scheduleRefresh (int forumTopicId) {
    if (forumTopicId == 0 || isDestroyed) {
      return;
    }
    pendingRefresh.add(forumTopicId);
    if (!refreshScheduled) {
      refreshScheduled = true;
      tdlib.ui().postDelayed(this::performRefresh, REFRESH_DELAY_MS);
    }
  }

  private void performRefresh () {
    refreshScheduled = false;
    if (isDestroyed || pendingRefresh.isEmpty()) {
      return;
    }
    int[] forumTopicIds = pendingRefresh.toArray();
    pendingRefresh.clear();
    if (forumTopicIds.length <= BATCH_THRESHOLD) {
      for (int forumTopicId : forumTopicIds) {
        fetchTopic(forumTopicId);
      }
      return;
    }
    // Many topics at once (a read in "All", a burst of messages): one page instead of one request per topic, which
    // ends in FLOOD_WAIT. Topics not on that page are fetched one by one, a few at a time.
    lastResyncAt = android.os.SystemClock.uptimeMillis();
    final int requestedReorderVersion = localReorderVersion;
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, firstPageLimit()), (result, error) -> tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      java.util.Set<Integer> left = new java.util.LinkedHashSet<>();
      for (int forumTopicId : forumTopicIds) {
        left.add(forumTopicId);
      }
      if (result != null) {
        applyFirstPage(result, requestedReorderVersion);
        for (TdApi.ForumTopic topic : result.topics) {
          left.remove(topic.info.forumTopicId);
        }
      }
      int fetched = 0;
      List<Integer> later = new ArrayList<>();
      for (int forumTopicId : left) {
        if (fetched < MAX_SINGLE_FETCHES) {
          fetchTopic(forumTopicId);
          fetched++;
        } else {
          later.add(forumTopicId);
        }
      }
      if (!later.isEmpty()) {
        tdlib.ui().postDelayed(() -> {
          for (int forumTopicId : later) {
            scheduleRefresh(forumTopicId);
          }
        }, 1000);
      }
    }));
  }

  private void fetchTopic (int forumTopicId) {
    tdlib.send(new TdApi.GetForumTopic(chatId, forumTopicId), (topic, error) -> tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      if (error == null) {
        retriedOnce.remove(forumTopicId);
        putTopic(topic);
      } else if (XgramForums.isTopicGone(error)) {
        retriedOnce.remove(forumTopicId);
        removeTopic(forumTopicId);
      } else if (retriedOnce.add(forumTopicId)) {
        // Offline, flood wait, a server hiccup: the topic still exists. Try once more, later.
        tdlib.ui().postDelayed(() -> scheduleRefresh(forumTopicId), XgramForums.retryAfterMs(error, 5000));
      } else {
        retriedOnce.remove(forumTopicId); // gave up for now; the next update of this topic tries again
      }
    }));
  }

  /** The chat list badge of the forum: the unread messages of its topics (see {@link XgramForumUnread}). */
  @UiThread
  private void publishUnread () {
    if (!isLoaded || isDestroyed) {
      return;
    }
    int sum = 0;
    for (TdApi.ForumTopic topic : topics) {
      sum += Math.max(0, topic.unreadCount);
    }
    if (!endReached) {
      // Only some pages are loaded: their sum leaves out the topics further down. It may raise the badge, never lower
      // it; a lower sum has the badge counted from a bigger page instead (throttled).
      int current = XgramForumUnread.current(tdlib, chatId);
      if (current == -1) {
        return;
      }
      if (sum < current) {
        XgramForumUnread.refresh(tdlib, chatId);
        return;
      }
    }
    XgramForumUnread.update(tdlib, chatId, sum);
  }

  @UiThread
  private void putTopic (TdApi.ForumTopic topic) {
    putTopicImpl(topic);
    publishUnread();
  }

  @UiThread
  private void putTopicImpl (TdApi.ForumTopic topic) {
    XgramForums.normalizeUnread(tdlib, topic);
    XgramTopicReadOutbox.set(tdlib, chatId, topic);
    XgramTopicMute.onSettings(tdlib, chatId, topic);
    int oldIndex = indexOf(topic.info.forumTopicId);
    // A topic pinned or unpinned since the last fetch (or one not seen before): keep the pinned order in step before
    // the comparator places it. A fresh pin goes on top, as it does on the server.
    if (topic.isPinned) {
      if (!pinnedOrder.contains(topic.info.forumTopicId)) {
        prependPinned(topic.info.forumTopicId);
      }
    } else {
      pinnedOrder.remove(topic.info.forumTopicId);
    }
    if (oldIndex == -1) {
      if (!isLoaded) {
        return;
      }
      int newIndex = insertionIndex(topic);
      if (newIndex == topics.size() && !endReached) {
        // Belongs to a page that hasn't been loaded yet.
        return;
      }
      topics.add(newIndex, topic);
      for (Listener listener : listeners) {
        listener.onTopicInserted(this, newIndex);
      }
      return;
    }
    TdApi.ForumTopic oldTopic = topics.remove(oldIndex);
    if (oldTopic.lastMessage != null && oldTopic.lastMessage.sendingState instanceof TdApi.MessageSendingStatePending &&
      (topic.lastMessage == null || topic.lastMessage.id < oldTopic.lastMessage.id)) {
      // Still sending: the server doesn't know the message yet. Keep its preview until it is sent or fails.
      topic.lastMessage = oldTopic.lastMessage;
    }
    int newIndex = insertionIndex(topic);
    topics.add(newIndex, topic);
    boolean changed = !sameVisibleState(oldTopic, topic);
    for (Listener listener : listeners) {
      if (oldIndex != newIndex) {
        listener.onTopicMoved(this, oldIndex, newIndex);
      }
      if (changed) {
        listener.onTopicChanged(this, newIndex);
      }
    }
  }

  private static boolean sameVisibleState (TdApi.ForumTopic a, TdApi.ForumTopic b) {
    return a.order == b.order &&
      a.isPinned == b.isPinned &&
      a.unreadCount == b.unreadCount &&
      a.unreadMentionCount == b.unreadMentionCount &&
      a.unreadReactionCount == b.unreadReactionCount &&
      a.lastReadOutboxMessageId == b.lastReadOutboxMessageId &&
      sameMessage(a.lastMessage, b.lastMessage) &&
      sameDraft(a.draftMessage, b.draftMessage) &&
      sameNotificationSettings(a.notificationSettings, b.notificationSettings) &&
      a.info.isClosed == b.info.isClosed &&
      a.info.isHidden == b.info.isHidden &&
      a.info.name.equals(b.info.name) &&
      sameIcon(a.info.icon, b.info.icon);
  }

  private static boolean sameMessage (@Nullable TdApi.Message a, @Nullable TdApi.Message b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.id == b.id && a.editDate == b.editDate && a.content.getConstructor() == b.content.getConstructor();
  }

  private static boolean sameDraft (@Nullable TdApi.DraftMessage a, @Nullable TdApi.DraftMessage b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.date == b.date;
  }

  private static boolean sameIcon (@Nullable TdApi.ForumTopicIcon a, @Nullable TdApi.ForumTopicIcon b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.color == b.color && a.customEmojiId == b.customEmojiId;
  }

  private static boolean sameNotificationSettings (@Nullable TdApi.ChatNotificationSettings a, @Nullable TdApi.ChatNotificationSettings b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.useDefaultMuteFor == b.useDefaultMuteFor && a.muteFor == b.muteFor;
  }

  private int insertionIndex (TdApi.ForumTopic topic) {
    int index = Collections.binarySearch(topics, topic, comparator);
    return index >= 0 ? index : -index - 1;
  }

  // Pinned order

  /** Pinned topics, in their current order (the ids {@code SetPinnedForumTopics} takes). */
  public int[] pinnedTopicIds () {
    return pinnedOrder.get().clone(); // IntList.get() is its own array
  }

  /** Ids of the pinned topics of a server page, in the server's order. */
  private static int[] serverPinnedIds (TdApi.ForumTopic[] serverTopics) {
    IntList ids = new IntList(8);
    for (TdApi.ForumTopic topic : serverTopics) {
      if (topic.isPinned) {
        ids.append(topic.info.forumTopicId);
      }
    }
    return ids.get();
  }

  private void replacePinnedOrder (int[] ids) {
    pinnedOrder.clear();
    pinnedOrder.appendAll(ids);
  }

  private void prependPinned (int forumTopicId) {
    insertPinnedAt(0, forumTopicId);
  }

  /** Puts the id at {@code position} among the other ids (removing it first if it is already there). */
  private void insertPinnedAt (int position, int forumTopicId) {
    int[] rest = pinnedOrder.get().clone(); // IntList.get() is its own array, cleared and refilled below
    pinnedOrder.clear();
    int i = 0;
    boolean inserted = false;
    for (int id : rest) {
      if (id == forumTopicId) {
        continue;
      }
      if (i == position) {
        pinnedOrder.append(forumTopicId);
        inserted = true;
      }
      pinnedOrder.append(id);
      i++;
    }
    if (!inserted) {
      pinnedOrder.append(forumTopicId);
    }
  }

  /**
   * Makes the pinned order match the pinned rows as they are listed: every pinned row is in it, in list order, and
   * nothing else. Rows the order didn't know (sorted after the known ones) get their place that way.
   */
  private void normalizePinnedOrder () {
    IntList ids = new IntList(Math.max(8, pinnedOrder.size()));
    for (TdApi.ForumTopic topic : topics) {
      if (topic.isPinned) {
        ids.append(topic.info.forumTopicId);
      }
    }
    // Pinned topics that are known but not loaded as rows (a failed fetch) stay, so a reorder can't unpin them.
    for (int i = 0; i < pinnedOrder.size(); i++) {
      int id = pinnedOrder.get(i);
      if (!ids.contains(id)) {
        ids.append(id);
      }
    }
    replacePinnedOrder(ids.get());
  }

  /**
   * Sets the pinned order (e.g. the ids the server accepted in {@code SetPinnedForumTopics}) and moves the rows to
   * match it.
   */
  @UiThread
  public void setPinnedOrder (int[] ids) {
    if (isDestroyed) {
      return;
    }
    replacePinnedOrder(ids);
    resort();
  }

  /** Moves rows until the list is sorted by {@link #comparator} again, one {@code onTopicMoved} per moved row. */
  @UiThread
  private void resort () {
    List<TdApi.ForumTopic> sorted = new ArrayList<>(topics);
    Collections.sort(sorted, comparator);
    for (int i = 0; i < sorted.size(); i++) {
      TdApi.ForumTopic expected = sorted.get(i);
      if (topics.get(i) == expected) {
        continue;
      }
      int fromIndex = -1;
      for (int j = i + 1; j < topics.size(); j++) {
        if (topics.get(j) == expected) {
          fromIndex = j;
          break;
        }
      }
      if (fromIndex == -1) {
        continue;
      }
      topics.remove(fromIndex);
      topics.add(i, expected);
      for (Listener listener : listeners) {
        listener.onTopicMoved(this, fromIndex, i);
      }
    }
  }

  /**
   * Moves a pinned topic among the pinned ones before the server confirms it: the pinned order changes with it, so
   * the list stays sorted, and {@link #pinnedTopicIds()} gives the ids to send in {@code SetPinnedForumTopics}.
   */
  @UiThread
  public boolean movePinnedLocally (int fromIndex, int toIndex) {
    if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= topics.size() || toIndex >= topics.size()) {
      return false;
    }
    int step = toIndex > fromIndex ? 1 : -1;
    for (int i = fromIndex; ; i += step) {
      TdApi.ForumTopic topic = topics.get(i);
      if (!topic.isPinned || topic.info.isHidden) {
        // Only through pinned rows, and never across the hidden General, which stays first whatever its pin.
        return false;
      }
      if (i == toIndex) {
        break;
      }
    }
    normalizePinnedOrder();
    TdApi.ForumTopic moved = topics.get(fromIndex);
    int fromPosition = pinnedOrder.indexOf(moved.info.forumTopicId);
    int toPosition = pinnedOrder.indexOf(topics.get(toIndex).info.forumTopicId);
    if (fromPosition == -1 || toPosition == -1) {
      return false;
    }
    insertPinnedAt(toPosition, moved.info.forumTopicId);
    localReorderVersion++;
    topics.remove(fromIndex);
    topics.add(toIndex, moved);
    for (Listener listener : listeners) {
      listener.onTopicMoved(this, fromIndex, toIndex);
    }
    return true;
  }

  private long lastResyncAt;

  /**
   * Counts drag moves made here. A first page asked for before the latest move carries the old server order, so it
   * must not undo the move (mid-drag, or before {@code SetPinnedForumTopics} answers).
   */
  private int localReorderVersion;

  /** The current {@link #localReorderVersion}: pass it back to {@link #setPinnedOrder(int[], int)}. */
  public int localReorderVersion () {
    return localReorderVersion;
  }

  /** As {@link #setPinnedOrder(int[])}, unless the user moved a pinned topic again since {@code reorderVersion}. */
  @UiThread
  public void setPinnedOrder (int[] ids, int reorderVersion) {
    if (reorderVersion == localReorderVersion) {
      setPinnedOrder(ids);
    }
  }

  private int firstPageLimit () {
    return Math.min(100, Math.max(20, topics.size()));
  }

  /**
   * Fetches the first page again and takes its pin state and pinned order (at most once per
   * {@link #RESYNC_INTERVAL_MS}): the way to follow a reorder made on another device, which TDLib doesn't report.
   */
  @UiThread
  public boolean resyncFirstPage () {
    return resyncFirstPage(false);
  }

  /**
   * As {@link #resyncFirstPage()}; {@code force} skips the throttle, e.g. to undo a reorder the server refused.
   * Returns whether the page was asked for.
   */
  @UiThread
  public boolean resyncFirstPage (boolean force) {
    if (isDestroyed || !isLoaded || loadFailed) {
      return false;
    }
    long now = android.os.SystemClock.uptimeMillis();
    if (!force && lastResyncAt != 0 && now - lastResyncAt < RESYNC_INTERVAL_MS) {
      return false;
    }
    lastResyncAt = now;
    final int requestedReorderVersion = localReorderVersion;
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, firstPageLimit()), (result, error) -> tdlib.ui().post(() -> {
      if (isDestroyed || result == null) {
        return;
      }
      applyFirstPage(result, requestedReorderVersion);
    }));
    return true;
  }

  /**
   * Puts the topics of a first page in and takes the server's pinned order from it, unless a pinned topic was moved
   * here after the page was asked for (its order is then older than the one shown).
   */
  @UiThread
  private void applyFirstPage (TdApi.ForumTopics result, int requestedReorderVersion) {
    for (TdApi.ForumTopic topic : result.topics) {
      putTopicImpl(topic);
    }
    setPinnedOrder(serverPinnedIds(result.topics), requestedReorderVersion);
    publishUnread();
  }

  /** Fetches topics again, e.g. after their pinned order changed on the server. */
  @UiThread
  public void refreshTopics (int[] forumTopicIds) {
    for (int forumTopicId : forumTopicIds) {
      scheduleRefresh(forumTopicId);
    }
  }

  @UiThread
  private void removeTopic (int forumTopicId) {
    pinnedOrder.remove(forumTopicId);
    int index = indexOf(forumTopicId);
    if (index != -1) {
      topics.remove(index);
      for (Listener listener : listeners) {
        listener.onTopicRemoved(this, index);
      }
    }
  }

  @UiThread
  private void notifyChanged (int index) {
    for (Listener listener : listeners) {
      listener.onTopicChanged(this, index);
    }
  }

  private static int forumTopicId (@Nullable TdApi.MessageTopic topicId) {
    return topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) topicId).forumTopicId : 0;
  }

  // ChatListener (TDLib thread)

  private long lastChatReadInboxMessageId = -1;

  @Override
  public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    // Reading from the "All" tab, the merged view or another device marks messages read in their topics without
    // an update for each topic: fetch the topics that still look unread. Only when the chat's state changed, so a
    // refresh that makes TDLib resend the same state can't loop.
    tdlib.ui().post(() -> {
      // Only a moved read position: new messages change unreadCount too, and they arrive through onNewMessage.
      if (lastChatReadInboxMessageId == lastReadInboxMessageId) {
        return;
      }
      lastChatReadInboxMessageId = lastReadInboxMessageId;
      refreshUnreadTopics();
    });
  }

  /** Fetches the real counters of every topic that looks unread (batched with other refreshes). */
  @UiThread
  public void refreshUnreadTopics () {
    refreshUnreadTopics(0);
  }

  /** As {@link #refreshUnreadTopics()}, for the topics from {@code fromIndex} on. */
  @UiThread
  private void refreshUnreadTopics (int fromIndex) {
    if (isDestroyed) {
      return;
    }
    for (int i = Math.max(0, fromIndex); i < topics.size(); i++) {
      TdApi.ForumTopic topic = topics.get(i);
      if (topic.unreadCount > 0 || topic.unreadMentionCount > 0 || topic.unreadReactionCount > 0) {
        scheduleRefresh(topic.info.forumTopicId);
      }
    }
  }

  /**
   * A screen shows this list again (a cached list reused, or the topic list back in front): what changed meanwhile
   * without an update, such as the order, last messages and counters, is fetched again. The first page comes in one
   * request (throttled); unread topics below it are refreshed one by one.
   */
  @UiThread
  public void refreshOnReturn () {
    if (isDestroyed || !isLoaded) {
      return;
    }
    int firstPage = firstPageLimit();
    boolean resynced = resyncFirstPage();
    refreshUnreadTopics(resynced ? firstPage : 0);
  }

  @Override
  public void onChatTopMessageChanged (long chatId, @Nullable TdApi.Message topMessage) {
    // The chat's last message moved without a new message for this list, e.g. after a gap was filled on reconnect,
    // or the last message was deleted: its topic's last message and order may be stale.
    if (topMessage == null || topMessage.sendingState != null) {
      return; // messages being sent show at once through onNewMessage
    }
    int forumTopicId = forumTopicId(topMessage.topicId);
    long messageId = topMessage.id;
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      TdApi.ForumTopic topic = find(forumTopicId);
      if (topic != null && (topic.lastMessage == null || topic.lastMessage.id < messageId)) {
        scheduleRefresh(forumTopicId);
      }
    });
  }

  @Override
  public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      int index = indexOf(info.forumTopicId);
      if (index == -1) {
        // Not loaded (yet). A new topic arrives with its "topic created" message through onNewMessage; fetching here
        // too would request every topic TDLib tells about while pages are loading.
        return;
      }
      TdApi.ForumTopic topic = topics.get(index);
      boolean orderMayChange = topic.info.isHidden != info.isHidden;
      topic.info = info;
      if (orderMayChange) {
        scheduleRefresh(info.forumTopicId);
      }
      notifyChanged(index);
    });
  }

  @Override
  public void onLocalReadChanged (Tdlib tdlib, long chatId, int forumTopicId) {
    // Ghost mode local read changed a topic's counter: fetch it again so normalizeUnread applies it.
    if (tdlib == this.tdlib && chatId == this.chatId && forumTopicId != 0 && !isDestroyed && indexOf(forumTopicId) != -1) {
      scheduleRefresh(forumTopicId);
    }
  }

  @Override
  public void onForumTopicUpdated (TdApi.UpdateForumTopic update) {
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      int index = indexOf(update.forumTopicId);
      if (index == -1) {
        if (!update.isPinned) {
          // Known as pinned but not loaded as a row: forget it, or the next reorder would pin it again.
          pinnedOrder.remove(update.forumTopicId);
        }
        return;
      }
      TdApi.ForumTopic topic = topics.get(index);
      // Ghost mode: mentions viewed without reading them count as read here.
      int mentions = com.fahadali.xgram.ghost.XgramLocalRead.lowerMentions(tdlib, chatId, update.forumTopicId, update.unreadMentionCount);
      boolean readChanged = topic.lastReadInboxMessageId != update.lastReadInboxMessageId;
      boolean pinChanged = topic.isPinned != update.isPinned;
      boolean changed = readChanged || pinChanged ||
        topic.lastReadOutboxMessageId != update.lastReadOutboxMessageId ||
        topic.unreadMentionCount != mentions ||
        topic.unreadReactionCount != update.unreadReactionCount ||
        topic.unreadPollVoteCount != update.unreadPollVoteCount ||
        !sameNotificationSettings(topic.notificationSettings, update.notificationSettings) ||
        !sameDraft(topic.draftMessage, update.draftMessage);
      topic.isPinned = update.isPinned;
      topic.lastReadInboxMessageId = update.lastReadInboxMessageId;
      topic.lastReadOutboxMessageId = update.lastReadOutboxMessageId;
      topic.unreadMentionCount = mentions;
      topic.unreadReactionCount = update.unreadReactionCount;
      topic.unreadPollVoteCount = update.unreadPollVoteCount;
      topic.notificationSettings = update.notificationSettings;
      topic.draftMessage = update.draftMessage;
      // unread_count isn't part of this update. Reading up to the last message means nothing is left.
      boolean readFully = topic.lastMessage != null && topic.lastReadInboxMessageId >= topic.lastMessage.id;
      if (readFully && topic.unreadCount != 0) {
        topic.unreadCount = 0;
        changed = true;
      }
      if (pinChanged) {
        // Pinned here or elsewhere: it goes on top of the pinned ones (as on the server); unpinned, it falls back to
        // its place by date. No fetch is needed, as order doesn't follow pinning.
        if (update.isPinned) {
          prependPinned(update.forumTopicId);
        } else {
          pinnedOrder.remove(update.forumTopicId);
        }
        topics.remove(index);
        int newIndex = insertionIndex(topic);
        topics.add(newIndex, topic);
        if (newIndex != index) {
          for (Listener listener : listeners) {
            listener.onTopicMoved(this, index, newIndex);
          }
          index = newIndex;
        }
      }
      if (changed) {
        notifyChanged(index);
        publishUnread();
      }
      // Partial reads change the unread count: fetch the exact values.
      // Fetching on every update can loop, as fetching a topic may emit this update again.
      if (readChanged && !readFully) {
        scheduleRefresh(update.forumTopicId);
      }
    });
  }

  // MessageListener (TDLib thread)

  @Override
  public void onNewMessage (TdApi.Message message) {
    int forumTopicId = forumTopicId(message.topicId);
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      if (message.sendingState != null && message.schedulingState == null) {
        // Scheduled messages also arrive here, but they aren't the topic's last message.
        int index = indexOf(forumTopicId);
        if (index != -1) {
          // A message being sent: TDLib's topic last_message only comes from the server, which doesn't know it yet, so
          // a fetch would return the previous message. Show the clock and the new preview now; the server order is
          // fetched once it is sent.
          topics.get(index).lastMessage = message;
          notifyChanged(index);
          return;
        }
      }
      scheduleRefresh(forumTopicId);
    });
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    int forumTopicId = forumTopicId(message.topicId);
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      int index = indexOf(forumTopicId);
      if (index != -1) {
        TdApi.ForumTopic topic = topics.get(index);
        if (topic.lastMessage != null && topic.lastMessage.id == oldMessageId) {
          // The clock goes away at once; the fetch below brings the server order.
          topic.lastMessage = message;
          notifyChanged(index);
        }
      }
      scheduleRefresh(forumTopicId);
    });
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    int forumTopicId = forumTopicId(message.topicId);
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      int index = indexOf(forumTopicId);
      if (index != -1) {
        TdApi.ForumTopic topic = topics.get(index);
        if (topic.lastMessage != null && topic.lastMessage.id == oldMessageId) {
          // Not pending any more: left as it was, putTopicImpl would keep the pending preview over the server's for good.
          topic.lastMessage = message;
          notifyChanged(index);
        }
      }
      scheduleRefresh(forumTopicId);
    });
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      for (int i = 0; i < topics.size(); i++) {
        TdApi.Message lastMessage = topics.get(i).lastMessage;
        if (lastMessage != null && lastMessage.id == messageId) {
          lastMessage.content = newContent;
          notifyChanged(i);
          break;
        }
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    tdlib.ui().post(() -> {
      if (isDestroyed) {
        return;
      }
      for (int i = 0; i < topics.size(); i++) {
        TdApi.ForumTopic topic = topics.get(i);
        if (topic.lastMessage != null) {
          for (long messageId : messageIds) {
            if (topic.lastMessage.id == messageId) {
              if (topic.lastMessage.sendingState != null) {
                // Deleted before it was sent (or after it failed): only this list knows it. Drop it, or
                // putTopicImpl would keep its preview over the server's last message.
                topic.lastMessage = null;
                notifyChanged(i);
              }
              scheduleRefresh(topic.info.forumTopicId);
              break;
            }
          }
        }
      }
    });
  }

  @Override
  public void performDestroy () {
    if (!isDestroyed) {
      isDestroyed = true;
      tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
      com.fahadali.xgram.ghost.XgramLocalRead.removeListener(this);
      listeners.clear();
    }
  }
}
