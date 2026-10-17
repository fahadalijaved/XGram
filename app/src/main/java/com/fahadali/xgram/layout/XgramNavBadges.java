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
package com.fahadali.xgram.layout;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.GlobalCallListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.BitwiseUtils;
import tgx.td.ChatPosition;

/**
 * The badges of the floating navigation toolbar ({@link XgramNavToolbar}): the account's unread chats on Chats and its
 * missed calls since the call list was last seen on Calls. The same counting as the classic bar's
 * ({@link XgramNavBar}) and the rail's, which keep their own copies; the "seen" mark is the rail's, shared by all three.
 * Bound to one account at a time ({@link #setTdlib}); listens only between {@link #start} and {@link #stop}.
 * Main thread only.
 */
final class XgramNavBadges {
  interface Callback {
    /** The counts changed: {@code unread} chats (or messages) that aren't muted, {@code dot} only muted ones. */
    void onBadges (int unread, boolean dot, int missed);

    /** The call list is on screen now: the chat list's Calls page with nothing over it, or a call list screen. */
    boolean isCallListOnScreen ();

    /** Calls is one of the tabs: only then are missed calls looked up. */
    boolean needsMissedCalls ();
  }

  private final Callback callback;
  @Nullable private Tdlib tdlib;
  private boolean started, destroyed;

  private int unreadCount, missedCount, newestMissed, missedRequest;
  private boolean unreadDot;
  private long missedCheckedAt;
  private volatile boolean badgePosted;

  private final CounterChangeListener counterListener = new CounterChangeListener() {
    @Override
    public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
      onCounterChanged(chatList);
    }

    @Override
    public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
      onCounterChanged(chatList);
    }
  };

  private final Runnable badgeUpdate = () -> {
    badgePosted = false;
    if (!destroyed && started) {
      updateUnreadBadge();
    }
  };

  private final Runnable missedCheck = () -> {
    if (!destroyed && started) {
      refreshMissedCalls(true);
    }
  };

  // A call that ended unanswered (TDLib's thread): look the missed calls up again once its message is in the history.
  private final GlobalCallListener callListener = new GlobalCallListener() {
    @Override
    public void onUpdateCall (Tdlib tdlib, TdApi.UpdateCall update) {
      TdApi.Call call = update != null ? update.call : null;
      if (call != null && !call.isOutgoing && call.state != null && call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR) {
        UI.post(() -> {
          if (!destroyed && started && tdlib == XgramNavBadges.this.tdlib) {
            UI.cancel(missedCheck);
            UI.post(missedCheck, 1500L);
          }
        });
      }
    }
  };

  // The chat folder badge settings changed (global, so a global badge reset): the Chats badge counts again.
  private final GlobalCountersListener badgeSettingsListener = new GlobalCountersListener() {
    @Override
    public void onBadgeSettingsChanged () {
      onCounterChanged(ChatPosition.CHAT_LIST_MAIN); // posted, at most once per frame
    }
  };

  XgramNavBadges (Callback callback) {
    this.callback = callback;
  }

  int unread () {
    return unreadCount;
  }

  boolean dot () {
    return unreadDot;
  }

  int missed () {
    return missedCount;
  }

  /** The account the badges count for (an account switch rebinds them; the counts start over). */
  void setTdlib (@Nullable Tdlib tdlib) {
    if (destroyed || this.tdlib == tdlib) {
      return;
    }
    if (started && this.tdlib != null) {
      this.tdlib.listeners().removeTotalChatCounterListener(counterListener);
    }
    this.tdlib = tdlib;
    missedCheckedAt = 0;
    newestMissed = 0;
    missedRequest++;
    boolean changed = unreadCount != 0 || unreadDot || missedCount != 0;
    unreadCount = 0;
    unreadDot = false;
    missedCount = 0;
    if (started && tdlib != null) {
      tdlib.listeners().addTotalChatCounterListener(counterListener);
    }
    if (changed) {
      callback.onBadges(unreadCount, unreadDot, missedCount);
    }
    if (started) {
      refresh();
    }
  }

  /** The toolbar is on screen: the badges listen and count. */
  void start () {
    if (destroyed || started) {
      return;
    }
    started = true;
    TdlibManager.instance().global().addCallListener(callListener);
    TdlibManager.instance().global().addCountersListener(badgeSettingsListener);
    if (tdlib != null) {
      tdlib.listeners().addTotalChatCounterListener(counterListener);
    }
    refresh();
  }

  /** The toolbar is hidden (two panes, the rail): no listening; the counts are kept for the next time. */
  void stop () {
    if (!started) {
      return;
    }
    started = false;
    UI.cancel(missedCheck);
    UI.cancel(badgeUpdate);
    badgePosted = false;
    TdlibManager.instance().global().removeCallListener(callListener);
    TdlibManager.instance().global().removeCountersListener(badgeSettingsListener);
    if (tdlib != null) {
      tdlib.listeners().removeTotalChatCounterListener(counterListener);
    }
  }

  void destroy () {
    if (destroyed) {
      return;
    }
    stop();
    destroyed = true;
    tdlib = null;
  }

  /** The unread badge now (cheap: reports only a change) and the missed calls, at most every 30 seconds. */
  void refresh () {
    if (destroyed || !started) {
      return;
    }
    updateUnreadBadge();
    refreshMissedCalls(false);
  }

  private void onCounterChanged (TdApi.ChatList chatList) {
    int constructor = chatList.getConstructor();
    if (constructor != TdApi.ChatListMain.CONSTRUCTOR && constructor != TdApi.ChatListArchive.CONSTRUCTOR) {
      return;
    }
    if (!badgePosted) {
      badgePosted = true;
      UI.post(badgeUpdate);
    }
  }

  /**
   * As the chat list's own counters count (the folder badge settings): a count while there are unread chats (or
   * messages) that aren't muted, a dot when only muted ones are unread. The same as the rail's.
   */
  private void updateUnreadBadge () {
    Tdlib tdlib = this.tdlib;
    if (tdlib == null) {
      return;
    }
    int count = 0;
    boolean dot = false;
    int flags = tdlib.settings().getChatFolderBadgeFlags();
    boolean messages = BitwiseUtils.hasFlag(flags, Settings.BADGE_FLAG_MESSAGES);
    TdlibCounter main = tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN);
    int total = Math.max(0, messages ? main.messageCount : main.chatCount);
    int unmuted = Math.max(0, messages ? main.messageUnmutedCount : main.chatUnmutedCount);
    if (BitwiseUtils.hasFlag(flags, Settings.BADGE_FLAG_ARCHIVED)) {
      TdlibCounter archive = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      total += Math.max(0, messages ? archive.messageCount : archive.chatCount);
      unmuted += Math.max(0, messages ? archive.messageUnmutedCount : archive.chatUnmutedCount);
    }
    if (unmuted > 0) {
      count = BitwiseUtils.hasFlag(flags, Settings.BADGE_FLAG_MUTED) ? total : unmuted;
    } else {
      dot = total > 0;
    }
    if (count != unreadCount || dot != unreadDot) {
      unreadCount = count;
      unreadDot = dot;
      callback.onBadges(unreadCount, unreadDot, missedCount);
    }
  }

  /** The newest missed calls: those after the last one seen in the call list count. The first look counts none. */
  void refreshMissedCalls (boolean force) {
    final Tdlib tdlib = this.tdlib;
    if (destroyed || !started || tdlib == null || !callback.needsMissedCalls()) {
      return;
    }
    long now = SystemClock.uptimeMillis();
    if (!force && missedCheckedAt != 0 && now - missedCheckedAt < XgramNavRail.MISSED_CHECK_INTERVAL_MS) {
      return;
    }
    missedCheckedAt = now;
    final int request = ++missedRequest;
    tdlib.send(new TdApi.SearchCallMessages(null, XgramNavRail.MISSED_FETCH, true), (found, error) -> {
      if (found == null || found.messages == null) {
        return;
      }
      int newest = 0;
      for (TdApi.Message message : found.messages) {
        if (XgramNavRail.isMissedCall(message)) {
          newest = Math.max(newest, message.date);
        }
      }
      final int newestDate = newest;
      final TdApi.Message[] messages = found.messages;
      UI.post(() -> {
        if (destroyed || request != missedRequest || tdlib != this.tdlib) {
          return;
        }
        XgramSettings settings = XgramSettings.instance();
        int seen = settings.railCallsSeen(tdlib.id());
        if (seen == 0) {
          // Never looked before: what is in the history now was there before the toolbar (or the rail). With no missed
          // call at all yet, now (the server's clock, as message dates), so the first one to come in still counts.
          seen = newestDate > 0 ? newestDate : (int) (tdlib.currentTimeMillis() / 1000L);
          settings.setRailCallsSeen(tdlib.id(), seen);
        }
        int count = 0;
        for (TdApi.Message message : messages) {
          if (XgramNavRail.isMissedCall(message) && message.date > seen) {
            count++;
          }
        }
        newestMissed = Math.max(newestMissed, newestDate);
        if (callback.isCallListOnScreen()) {
          markCallsSeen();
        } else if (count != missedCount) {
          missedCount = count;
          callback.onBadges(unreadCount, unreadDot, missedCount);
        }
      });
    });
  }

  /** The call list is on screen: every missed call so far is seen. */
  void markCallsSeen () {
    Tdlib tdlib = this.tdlib;
    if (tdlib == null || destroyed) {
      return;
    }
    XgramSettings settings = XgramSettings.instance();
    int stored = settings.railCallsSeen(tdlib.id());
    // Up to now: the newest missed call known, or the server's clock if that is later (the list may show calls the
    // last lookup has not seen yet)
    int seen = Math.max(newestMissed, (int) (tdlib.currentTimeMillis() / 1000L));
    if (seen > stored) {
      settings.setRailCallsSeen(tdlib.id(), seen);
    }
    if (missedCount != 0) {
      missedCount = 0;
      callback.onBadges(unreadCount, unreadDot, missedCount);
    }
  }
}
