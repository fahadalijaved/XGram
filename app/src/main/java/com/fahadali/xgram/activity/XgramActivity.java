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

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceList;

/**
 * Activity (Slack's Activity tab, Discord's Inbox): every mention of you, every reply to your messages and every
 * reaction to them, from all your chats, newest first. This is the entry to it: the switches, attaching an account,
 * and the hooks other XGram code calls.
 * <ul>
 *   <li>{@link XgramActivityCollector} collects from TDLib's updates (one per account).</li>
 *   <li>{@link XgramActivityStore} keeps the items, on its own thread.</li>
 * </ul>
 * Nothing here sends anything to Telegram: reading is the user's action alone.
 */
public final class XgramActivity {
  private XgramActivity () { }

  static final String TAG = XgramActivityStore.TAG;

  // Switches (XgramSettings, cached there)

  public static boolean isOn () {
    return XgramSettings.instance().activityOn();
  }

  public static boolean reactions () {
    return XgramSettings.instance().activityReactions();
  }

  public static boolean muted () {
    return XgramSettings.instance().activityMuted();
  }

  public static boolean discussion () {
    return XgramSettings.instance().activityDiscussion();
  }

  public static boolean lookBack () {
    return XgramSettings.instance().activityLookBack();
  }

  public static boolean badge () {
    return XgramSettings.instance().activityBadge();
  }

  public static boolean inHeader () {
    return XgramSettings.instance().activityHeader();
  }

  // Accounts

  /**
   * TDLib's listener lists hold their listeners weakly: the collectors are kept here, for as long as the process
   * lives (a Tdlib does too).
   */
  private static final List<XgramActivityCollector> collectors = new ArrayList<>();

  /** Called once for every account when its {@link Tdlib} is created (its cache does not exist yet). */
  @AnyThread
  public static void attach (Tdlib tdlib) {
    try {
      XgramActivityCollector collector = new XgramActivityCollector(tdlib);
      synchronized (collectors) {
        collectors.add(collector);
      }
      tdlib.listeners().subscribeForGlobalUpdates(collector);
    } catch (Throwable t) {
      Log.e(TAG, "Cannot attach", t);
    }
  }

  /** TDLib thread: the account in this slot logged out (XgramAccountData.eraseAccount). */
  @AnyThread
  public static void forgetAccount (int accountId) {
    XgramActivityStore.instance().clearAccount(accountId);
    onAccountForgotten(accountId);
  }

  /** The parts that come later (the sync, the screen's state) forget the account too. */
  private static void onAccountForgotten (int accountId) {
    // Nothing else keeps account state yet.
  }

  // Hooks

  /**
   * A message was on screen while ghost mode keeps it unread on the server (XgramLocalRead.onMessageViewed, before it
   * checks anything): the items about it are read here, on this device only. Cheap when the chat has nothing new.
   */
  @UiThread
  public static void onViewedLocally (Tdlib tdlib, TdApi.Message message) {
    try {
      if (message == null || !isOn()) {
        return;
      }
      final int accountId = tdlib.id();
      XgramActivityStore store = XgramActivityStore.instance();
      if (!store.summary(accountId).chatHasNew(message.chatId)) {
        return;
      }
      if (message.isOutgoing) {
        // Your message on screen: the reactions to it are seen
        store.markRead(accountId, message.chatId, message.id, XgramActivityItem.MASK_REACTION, true);
      } else {
        store.markRead(accountId, message.chatId, message.id, XgramActivityItem.MASK_MENTION_GROUP, true);
        if (message.mediaAlbumId != 0) {
          store.markReadAlbum(accountId, message.chatId, message.mediaAlbumId, XgramActivityItem.MASK_MENTION_GROUP, true);
        }
      }
    } catch (Throwable t) {
      Log.e(TAG, "onViewedLocally", t);
    }
  }

  /**
   * Store thread (or a TDLib result): a chat's server counter and what Activity has New differ. A paced search
   * settles it (XgramActivitySync).
   */
  @AnyThread
  static void onCountMismatch (Tdlib tdlib, long chatId, int kindMask, int serverCount) {
    // The paced sync comes later.
  }

  // What is shown changed without the store changing (mute, settings): badges and the screen filter again

  public interface DisplayListener {
    @UiThread
    void onActivityDisplayChanged (int accountId);
  }

  private static final ReferenceList<DisplayListener> displayListeners = new ReferenceList<>();

  /** UI thread. Held weakly: keep a reference to {@code listener}. */
  @UiThread
  public static void addDisplayListener (DisplayListener listener) {
    displayListeners.add(listener);
  }

  @UiThread
  public static void removeDisplayListener (DisplayListener listener) {
    displayListeners.remove(listener);
  }

  /** Any thread: -1 for every account. */
  @AnyThread
  public static void onDisplayFiltersChanged (int accountId) {
    UI.post(() -> {
      for (DisplayListener listener : displayListeners) {
        try {
          listener.onActivityDisplayChanged(accountId);
        } catch (Throwable t) {
          Log.e(TAG, "Display listener failed", t);
        }
      }
    });
  }
}
