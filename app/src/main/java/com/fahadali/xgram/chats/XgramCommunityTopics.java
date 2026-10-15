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

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;

import com.fahadali.xgram.topics.XgramForums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.lambda.RunnableLong;

/**
 * The first topics of each forum on a community page, for the "General, Off-topic, Updates" line of its row. A page-local
 * copy: one GetForumTopics per forum, kept current from the page's own chat updates. Nothing here touches the forum's
 * badge in the chat list or the topic cache.
 * <p>
 * UI thread only.
 */
public final class XgramCommunityTopics {
  private static final int FETCH_LIMIT = 10;
  private static final int LINE_LIMIT = 6;
  private static final long STAGGER_MS = 150;
  private static final long REFETCH_DELAY_MS = 2000;

  private final Tdlib tdlib;
  /** Told, on the UI thread, which forum's line changed. */
  private final RunnableLong onChanged;

  private final Map<Long, List<TdApi.ForumTopic>> topics = new HashMap<>();
  /** Forums asked for at least once (a page asks once; updates ask again). */
  private final Set<Long> requested = new HashSet<>();
  private final Set<Long> inFlight = new HashSet<>();
  /** Forums whose first request failed (no topics to wait for). */
  private final Set<Long> failed = new HashSet<>();
  /** Forums whose answer is in flight and that must be asked again once it is here. */
  private final Set<Long> fetchAgain = new HashSet<>();
  private final Map<Long, Runnable> delayedFetches = new HashMap<>();
  private final Map<Long, Long> lastReadInbox = new HashMap<>();
  /** Uptime at which the next request may go out: they are 150 ms apart. */
  private long nextSendAt;
  private boolean destroyed;

  public XgramCommunityTopics (Tdlib tdlib, RunnableLong onChanged) {
    this.tdlib = tdlib;
    this.onChanged = onChanged;
  }

  /** Asks for the forum's topics the first time it is on the page. */
  @UiThread
  public void ensure (long chatId) {
    if (!destroyed && requested.add(chatId)) {
      fetch(chatId, 0);
    }
  }

  /** Asked for and not here yet (nor failed): the row shows a placeholder for its topics line meanwhile. */
  @UiThread
  public boolean isLoading (long chatId) {
    return !destroyed && requested.contains(chatId) && !topics.containsKey(chatId) && !failed.contains(chatId);
  }

  /** The chat left the page. */
  @UiThread
  public void forget (long chatId) {
    requested.remove(chatId);
    failed.remove(chatId);
    topics.remove(chatId);
    lastReadInbox.remove(chatId);
    fetchAgain.remove(chatId);
    Runnable delayed = delayedFetches.remove(chatId);
    if (delayed != null) {
      tdlib.ui().removeCallbacks(delayed);
    }
  }

  @UiThread
  public void destroy () {
    destroyed = true;
    for (Runnable delayed : delayedFetches.values()) {
      tdlib.ui().removeCallbacks(delayed);
    }
    delayedFetches.clear();
    topics.clear();
    requested.clear();
  }

  // Fetching

  private void fetch (long chatId, long delayMs) {
    if (destroyed) {
      return;
    }
    Runnable previous = delayedFetches.remove(chatId);
    if (previous != null) {
      tdlib.ui().removeCallbacks(previous);
    }
    long now = SystemClock.uptimeMillis();
    long sendAt = Math.max(now + delayMs, nextSendAt);
    if (delayMs == 0) {
      nextSendAt = sendAt + STAGGER_MS; // the page's first requests go out 150 ms apart; a refetch due in 2 s doesn't hold the others back
    }
    Runnable send = () -> {
      delayedFetches.remove(chatId);
      sendNow(chatId);
    };
    delayedFetches.put(chatId, send);
    tdlib.ui().postDelayed(send, Math.max(0, sendAt - now));
  }

  private void sendNow (long chatId) {
    if (destroyed || !requested.contains(chatId)) {
      return;
    }
    if (!inFlight.add(chatId)) {
      fetchAgain.add(chatId); // one request per forum at a time
      return;
    }
    tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, FETCH_LIMIT), (result, error) -> tdlib.ui().post(() -> {
      inFlight.remove(chatId);
      if (destroyed) {
        return;
      }
      if (result != null && requested.contains(chatId)) {
        List<TdApi.ForumTopic> list = new ArrayList<>(result.topics.length);
        for (TdApi.ForumTopic topic : result.topics) {
          list.add(XgramForums.normalizeUnread(tdlib, topic));
        }
        failed.remove(chatId);
        topics.put(chatId, list);
        onChanged.runWithLong(chatId);
      } else if (result == null && requested.contains(chatId) && !topics.containsKey(chatId) && failed.add(chatId)) {
        onChanged.runWithLong(chatId); // the row stops waiting for them
      }
      if (fetchAgain.remove(chatId)) {
        fetch(chatId, 0);
      }
    }));
  }

  // Live updates, from the page's chat listener (already on the UI thread)

  @UiThread
  public void onTopMessage (long chatId, @Nullable TdApi.Message message) {
    List<TdApi.ForumTopic> list = topics.get(chatId);
    if (list == null || message == null || !(message.topicId instanceof TdApi.MessageTopicForum)) {
      return;
    }
    int forumTopicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
    int index = indexOf(list, forumTopicId);
    if (index == -1) {
      fetch(chatId, REFETCH_DELAY_MS); // a topic past the first ten, or a new one
      return;
    }
    TdApi.ForumTopic topic = list.remove(index);
    if (topic.lastMessage == null || message.id > topic.lastMessage.id) {
      topic.lastMessage = message;
      if (!message.isOutgoing && message.id > topic.lastReadInboxMessageId) {
        topic.unreadCount++;
      }
    }
    // To the front, after the pinned topics (which keep their order).
    int at = 0;
    if (!topic.isPinned) {
      while (at < list.size() && list.get(at).isPinned) {
        at++;
      }
    }
    list.add(at, topic);
    onChanged.runWithLong(chatId);
  }

  @UiThread
  public void onTopicInfo (TdApi.ForumTopicInfo info) {
    List<TdApi.ForumTopic> list = topics.get(info.chatId);
    int index = list != null ? indexOf(list, info.forumTopicId) : -1;
    if (index == -1) {
      return;
    }
    list.get(index).info = info;
    onChanged.runWithLong(info.chatId);
  }

  @UiThread
  public void onTopicUpdated (TdApi.UpdateForumTopic update) {
    List<TdApi.ForumTopic> list = topics.get(update.chatId);
    int index = list != null ? indexOf(list, update.forumTopicId) : -1;
    if (index == -1) {
      return;
    }
    TdApi.ForumTopic topic = list.get(index);
    boolean pinChanged = topic.isPinned != update.isPinned;
    topic.isPinned = update.isPinned;
    topic.lastReadInboxMessageId = update.lastReadInboxMessageId;
    boolean changed = pinChanged;
    if (topic.unreadCount != 0 && topic.lastMessage != null && topic.lastReadInboxMessageId >= topic.lastMessage.id) {
      topic.unreadCount = 0;
      changed = true;
    }
    if (changed) {
      onChanged.runWithLong(update.chatId);
    }
    if (pinChanged) {
      fetch(update.chatId, REFETCH_DELAY_MS); // the order comes from the server
    }
  }

  /** The forum's read position moved (the "All" view, another device): the counters are fetched again. */
  @UiThread
  public void onReadInbox (long chatId, long lastReadInboxMessageId) {
    if (!topics.containsKey(chatId)) {
      return;
    }
    Long previous = lastReadInbox.put(chatId, lastReadInboxMessageId);
    if (previous != null && previous != lastReadInboxMessageId) {
      fetch(chatId, REFETCH_DELAY_MS);
    }
  }

  private static int indexOf (List<TdApi.ForumTopic> list, int forumTopicId) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).info.forumTopicId == forumTopicId) {
        return i;
      }
    }
    return -1;
  }

  // The line

  /** "General, Off-topic, Updates", unread ones highlighted through {@code outEntities}; null until the topics are here. */
  @Nullable
  @UiThread
  public String line (ViewController<?> context, long chatId, List<TextEntity> outEntities) {
    List<TdApi.ForumTopic> list = topics.get(chatId);
    if (list == null || list.isEmpty()) {
      return null;
    }
    StringBuilder b = new StringBuilder();
    int count = 0;
    for (TdApi.ForumTopic topic : list) {
      if (count++ >= LINE_LIMIT) {
        break;
      }
      if (b.length() > 0) {
        b.append(Lang.getConcatSeparator());
      }
      int start = b.length();
      b.append(XgramForums.topicTitle(topic.info));
      if (topic.unreadCount > 0 || topic.unreadMentionCount > 0) {
        outEntities.add(new TextEntityCustom(context, tdlib, null, start, b.length(), 0, null)
          .setCustomColorSet(TextColorSets.Regular.NORMAL));
      }
    }
    return b.toString();
  }
}
