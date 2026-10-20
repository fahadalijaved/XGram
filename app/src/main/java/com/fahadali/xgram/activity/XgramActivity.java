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
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.layout.XgramTwoPane;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
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

  /** Activity on the classic bottom bar: XgramSettings.ACTIVITY_BAR_OFF, or ACTIVITY_BAR_CALLS (in Calls' place). */
  public static int barMode () {
    return XgramSettings.instance().activityBar();
  }

  /** The classic bottom bar (XgramNavBar) has Activity, in Calls' place, wherever it shows. */
  public static boolean barHasActivity () {
    return isOn() && barMode() == XgramSettings.ACTIVITY_BAR_CALLS;
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
      boolean first;
      synchronized (collectors) {
        first = collectors.isEmpty();
        collectors.add(collector);
      }
      tdlib.listeners().subscribeForGlobalUpdates(collector);
      if (first) {
        XgramSettings.instance().addListener(settingsListener);
        XgramActivitySync.start();
      }
    } catch (Throwable t) {
      Log.e(TAG, "Cannot attach", t);
    }
  }

  /** Activity or Reactions came on: a seed covers the gap; Find earlier mentions came on: the look back again. */
  private static final XgramSettings.Listener settingsListener = key -> {
    if (key == null) {
      return;
    }
    try {
      XgramSettings settings = XgramSettings.instance();
      if ((XgramSettings.KEY_ACTIVITY_ON.equals(key) && settings.activityOn()) ||
        (XgramSettings.KEY_ACTIVITY_REACTIONS.equals(key) && settings.activityReactions() && settings.activityOn())) {
        XgramActivitySync.onSettingsChanged(false);
      } else if (XgramSettings.KEY_ACTIVITY_LOOKBACK.equals(key) && settings.activityLookBack() && settings.activityOn()) {
        XgramActivitySync.onSettingsChanged(true);
      }
    } catch (Throwable t) {
      Log.e(TAG, "Settings listener", t);
    }
  };

  /** TDLib thread: the account in this slot logged out (XgramAccountData.eraseAccount). */
  @AnyThread
  public static void forgetAccount (int accountId) {
    XgramActivityStore.instance().clearAccount(accountId);
    onAccountForgotten(accountId);
  }

  /** Its queue, the screen's remembered scroll and opened row go too. */
  private static void onAccountForgotten (int accountId) {
    XgramActivitySync.forget(accountId);
    XgramActivityController.forgetAccount(accountId);
  }

  // Where Activity is in the navigation

  /**
   * Whether the window's navigation on screen has Activity: the rail (a destination unless it is hidden in the side
   * menu) or the floating toolbar (a tab, if one was chosen). The header's bell goes then. The classic bottom bar
   * belongs to its chat list, which checks {@link #barHasActivity()} itself.
   */
  @UiThread
  public static boolean navigationHasActivity (@Nullable BaseActivity context) {
    if (context == null || !isOn()) {
      return false;
    }
    XgramTwoPane twoPane = context.xgramTwoPane();
    if (twoPane == null) {
      return false;
    }
    if (twoPane.isRailShown()) {
      return com.fahadali.xgram.layout.XgramNavRail.hasActivityDestination();
    }
    return toolbarHasActivity(twoPane.toolbar());
  }

  /** Whether the floating navigation toolbar is on screen with an Activity tab. */
  public static boolean toolbarHasActivity (@Nullable com.fahadali.xgram.layout.XgramNavToolbar toolbar) {
    return toolbar != null && toolbar.isShown() && toolbar.hasTab(com.fahadali.xgram.layout.XgramNavToolbar.DEST_ACTIVITY);
  }

  // Opening

  /**
   * Every way in opens Activity here, and this decides where: with two panes in the chat list pane (over the chat
   * list, anything else there goes), otherwise over the screen it was opened from. Already on top: back to its top.
   */
  @UiThread
  public static void open (@Nullable ViewController<?> from) {
    if (from == null || from.isDestroyed()) {
      return;
    }
    final BaseActivity context = from.context();
    final Tdlib tdlib = from.tdlib();
    if (context == null || tdlib == null) {
      return;
    }
    ViewController<?> leftTop = XgramTwoPane.leftTop(context);
    if (leftTop != null) {
      // Two panes. Opened in one pane and then unfolded, Activity stayed in the right pane: it moves to the left one
      // now (its scroll position with it), so that one Activity is shown, not two.
      final XgramActivityController onRight = rightPaneActivity(context, tdlib);
      if (leftTop instanceof XgramActivityController && leftTop.tdlib() == tdlib) {
        ((XgramActivityController) leftTop).scrollToTopOrReload();
        if (onRight != null) {
          closeOnRight(context, onRight);
        }
        return;
      }
      if (onRight != null) {
        onRight.handOverScroll();
      }
      if (XgramTwoPane.showOnLeft(from, new XgramActivityController(context, tdlib))) {
        if (onRight != null) {
          closeOnRight(context, onRight);
        }
        return;
      }
      if (onRight != null) {
        XgramActivityController.uiState(tdlib.id()).restorePending = false; // it stays where it is
      }
    }
    // A tab of the floating toolbar: its tab screen (the selection moves, it fades in over the chat list), as the side
    // menu opens the other tabs. Not from a screen above the tabs: those open it over themselves below.
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = com.fahadali.xgram.layout.XgramNavToolbar.of(context);
    if (toolbarHasActivity(toolbar) && toolbar.openFromDrawer(com.fahadali.xgram.layout.XgramNavToolbar.DEST_ACTIVITY)) {
      return;
    }
    NavigationController navigation = from.navigationController();
    if (navigation == null || navigation.isDestroyed()) {
      navigation = context.navigation();
    }
    if (navigation == null || navigation.isAnimating()) {
      return;
    }
    ViewController<?> top = navigation.getCurrentStackItem();
    if (top instanceof XgramActivityController && top.tdlib() == tdlib) {
      ((XgramActivityController) top).scrollToTopOrReload();
      return;
    }
    navigation.navigateTo(new XgramActivityController(context, tdlib));
  }

  /** Two panes: this account's Activity in the right pane (the window's own navigation, above its root), if any. */
  @UiThread
  @Nullable
  private static XgramActivityController rightPaneActivity (BaseActivity context, Tdlib tdlib) {
    NavigationController right = context.navigation();
    if (right == null || right.isDestroyed()) {
      return null;
    }
    NavigationStack stack = right.getStack();
    for (int i = stack.size() - 1; i >= 1; i--) {
      ViewController<?> c = stack.get(i);
      if (c instanceof XgramActivityController && c.tdlib() == tdlib && !c.isDestroyed()) {
        return (XgramActivityController) c;
      }
    }
    return null;
  }

  /**
   * Takes {@code c} out of the right pane: on top, as Back would (what was under it shows, or the empty pane); under
   * a chat it opened there, out of the stack.
   */
  @UiThread
  private static void closeOnRight (BaseActivity context, XgramActivityController c) {
    final NavigationController right = context.navigation();
    if (right == null || right.isDestroyed() || c.isDestroyed()) {
      return;
    }
    if (right.getCurrentStackItem() != c) {
      right.getStack().destroy(c);
    } else if (!right.navigateBack()) {
      // The right pane is animating: once it is done
      UI.post(() -> {
        if (!c.isDestroyed() && !right.isDestroyed() && right.getCurrentStackItem() == c && XgramTwoPane.leftTop(context) != null) {
          right.navigateBack();
        }
      }, 400L);
    }
  }

  // Hooks

  /**
   * A message was on screen while ghost mode keeps it unread on the server (XgramLocalRead.onMessageViewed, before it
   * checks anything): the items about it are read here, on this device only. Cheap when the chat has nothing new.
   * A message that is itself unread for you (a mention, or your message with unread reactions) is read even before the
   * summary lists its chat: its item may still be on its way to the store (posted earlier, so applied first).
   */
  @UiThread
  public static void onViewedLocally (Tdlib tdlib, TdApi.Message message) {
    try {
      if (message == null || !isOn()) {
        return;
      }
      final int accountId = tdlib.id();
      XgramActivityStore store = XgramActivityStore.instance();
      final boolean unreadForYou = message.isOutgoing ?
        message.unreadReactions != null && message.unreadReactions.length > 0 :
        message.containsUnreadMention;
      if (!unreadForYou && !store.summary(accountId).chatHasNew(message.chatId)) {
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
    XgramActivitySync.trigger(tdlib, chatId, kindMask, serverCount);
  }

  /**
   * Debug builds: the sync, and Mark all as read, act as if Telegram asked to wait two minutes (Chat list > Activity).
   */
  public static void debugInjectFlood () {
    XgramActivitySync.injectFlood();
  }

  /** Debug builds: whether every update callback of Activity's collectors throws (Chat list > Activity). */
  public static boolean debugCollectorThrows () {
    return org.thunderdog.challegram.BuildConfig.DEBUG && XgramActivityCollector.debugThrow;
  }

  /**
   * Debug builds: makes every update callback of Activity's collectors throw first thing, so that their catch, and
   * Telegram X's own listeners after them, run for real. Not saved: off again when the app restarts.
   */
  public static void setDebugCollectorThrows (boolean value) {
    if (org.thunderdog.challegram.BuildConfig.DEBUG) {
      XgramActivityCollector.debugThrow = value;
    }
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
