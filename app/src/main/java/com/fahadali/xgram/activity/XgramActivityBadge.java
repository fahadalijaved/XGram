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

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.reference.ReferenceList;

/**
 * Activity's unread count, as every way into it shows it: the header bell's dot, the side menu, the rail, the
 * navigation bars and the chips. New items that pass what is shown (Reactions, Muted chats, Channel comments, hidden
 * chats). Listeners hear (debounced) when it may have changed and read it again.
 */
public final class XgramActivityBadge implements XgramActivityStore.ChangeListener, XgramActivity.DisplayListener, XgramSettings.Listener, GlobalAccountListener {
  public interface Listener {
    @UiThread
    void onActivityBadgeChanged ();
  }

  private static final long DEBOUNCE_MS = 150L;
  private static XgramActivityBadge instance;

  @UiThread
  public static XgramActivityBadge instance () {
    if (instance == null) {
      instance = new XgramActivityBadge();
    }
    return instance;
  }

  private final ReferenceList<Listener> listeners = new ReferenceList<>();
  private boolean posted;
  private final Runnable notify = () -> {
    posted = false;
    for (Listener listener : listeners) {
      try {
        listener.onActivityBadgeChanged();
      } catch (Throwable t) {
        Log.e(XgramActivity.TAG, "Badge listener failed", t);
      }
    }
  };

  private XgramActivityBadge () {
    XgramActivityStore.instance().addListener(this);
    XgramActivity.addDisplayListener(this);
    XgramSettings.instance().addListener(this);
    TdlibManager.instance().global().addAccountListener(this);
  }

  /** Held weakly: keep a reference to {@code listener}. */
  @UiThread
  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  @UiThread
  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  /** Any change that may move a count: posted once per {@link #DEBOUNCE_MS}. */
  @UiThread
  public void invalidate () {
    if (!posted) {
      posted = true;
      UI.post(notify, DEBOUNCE_MS);
    }
  }

  @Override
  public void onActivityChanged (int accountId) {
    invalidate();
  }

  @Override
  public void onActivityDisplayChanged (int accountId) {
    invalidate();
  }

  @Override
  public void onXgramSettingChanged (String key) {
    if (key != null && key.startsWith(XgramSettings.KEY_ACTIVITY_PREFIX)) {
      UI.post(this::invalidate);
    }
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    invalidate();
  }

  // Counts

  /** New items of {@code tdlib}'s account Activity shows (filters applied); 0 while Activity is off. */
  @UiThread
  public static int count (@Nullable Tdlib tdlib) {
    return count(tdlib, XgramActivityStore.FILTER_ALL);
  }

  /** The same for one filter (the chips). */
  @UiThread
  public static int count (@Nullable Tdlib tdlib, int filter) {
    return count(tdlib, filter, null);
  }

  /** The same within the chats the page shows ({@code onlyChats}, sorted; null or empty: all chats). */
  @UiThread
  static int count (@Nullable Tdlib tdlib, int filter, @Nullable long[] onlyChats) {
    if (tdlib == null || !XgramActivity.isOn()) {
      return 0;
    }
    XgramActivityStore.Summary summary = XgramActivityStore.instance().summary(tdlib.id());
    final boolean reactions = XgramActivity.reactions(), discussion = XgramActivity.discussion();
    final boolean someChats = onlyChats != null && onlyChats.length > 0;
    int count = 0;
    for (XgramActivityStore.Entry entry : summary.entries) {
      if (summary.isHidden(entry.chatId) || (entry.discussion && !discussion)) {
        continue;
      }
      if (someChats && !XgramActivityChatFilter.contains(onlyChats, entry.chatId)) {
        continue;
      }
      final boolean isReaction = entry.kind == XgramActivityItem.KIND_REACTION;
      if (isReaction && !reactions) {
        continue;
      }
      int add;
      switch (filter) {
        case XgramActivityStore.FILTER_MENTIONS:
          add = entry.kind == XgramActivityItem.KIND_MENTION ? entry.count : 0;
          break;
        case XgramActivityStore.FILTER_REPLIES:
          add = entry.kind == XgramActivityItem.KIND_REPLY ? entry.count : entry.kind == XgramActivityItem.KIND_MENTION ? entry.alsoReplyCount : 0;
          break;
        case XgramActivityStore.FILTER_REACTIONS:
          add = isReaction ? entry.count : 0;
          break;
        case XgramActivityStore.FILTER_ALL:
        default:
          add = entry.count;
          break;
      }
      if (add == 0 || XgramActivityRows.isMutedOut(tdlib, entry.chatId, entry.topicId, entry.kind)) {
        continue;
      }
      count += add;
    }
    return count;
  }

  /** {@link #count}, or 0 while Unread badge is off: what the badges show. */
  @UiThread
  public static int badgeCount (@Nullable Tdlib tdlib) {
    return XgramActivity.badge() ? count(tdlib) : 0;
  }
}
