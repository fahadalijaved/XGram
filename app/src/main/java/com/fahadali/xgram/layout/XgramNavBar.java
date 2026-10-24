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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.GlobalCallListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallListController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.PeopleController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.core.BitwiseUtils;
import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * The floating navigation bar at the bottom of the chat list on narrow windows (a phone, a Fold's cover screen), as
 * in the new official Telegram, for Chat list > Navigation > Navigation bar. Wide windows keep the navigation rail
 * ({@link XgramNavRail}); with both on, the rail shows when the window is wide and the bar when it is narrow
 * ({@link XgramTwoPane#isNavBarWindow}).
 * <ul>
 *   <li>A full-corner pill, 16dp from the sides and above the navigation bar (and the folder tabs when they are at
 *   the bottom), with a level 2 shadow. surfaceContainer with Expressive design, the chat list's own filling and
 *   accent colors in the classic look; the layout is the same.</li>
 *   <li>The rail's destinations and actions: Chats and Calls are the chat list's two pages (they replace its Chats /
 *   Calls tabs, so the list shows a plain title) and the active one sits in a 64 x 32dp tonal indicator that springs
 *   between them; Contacts, Saved messages and Settings open a screen and never take the indicator. With folders,
 *   Calls is a screen too (and the side menu settings can hide it). Chats has the unread badge, Calls the missed
 *   calls one; a long press on Chats shows the Chats tab's filters.</li>
 *   <li>Scrolling a page down hides it, scrolling up brings it back (a spring), with the compose button. Search and
 *   chat selection hide it too. The pages, the compose button and the snackbars keep {@link #room()} clear above
 *   the navigation bar, so the last chat clears it.</li>
 * </ul>
 * The chat list ({@link MainController}) owns it: the bar is a view of its content, under the compose button, so it
 * slides away with the list when a chat opens. Values are androidx.compose.material3 tokens: NavigationBarTokens,
 * NavigationBarItem (64 x 32dp indicator, 24dp icon, labelMedium), BadgeTokens and the elevation level 2.
 */
public final class XgramNavBar {
  /** The pill: 6dp, the 32dp indicator, 4dp, a 16dp labelMedium line and 6dp. */
  private static final float HEIGHT = 64f;
  /** From the window's sides and from what is below the pill (the navigation bar, the bottom folder tabs). */
  private static final float SIDE_MARGIN = 16f, BOTTOM_GAP = 16f;
  /** On a wide but low window (a phone in landscape) the pill stays this wide, centered, instead of stretching. */
  private static final float MAX_WIDTH = 480f;
  /** Inside the pill, before the first destination and after the last one, so the indicators clear its corners. */
  private static final float INNER_PADDING = 8f;
  /** NavigationBarItem: 64 x 32dp full-corner indicator, 24dp icon; narrow items keep 2dp on each side of it. */
  private static final float INDICATOR_WIDTH = 64f, INDICATOR_HEIGHT = 32f, ICON = 24f, INDICATOR_SIDE = 2f;
  private static final float INDICATOR_TOP = 6f, LABEL_GAP = 4f, LABEL_LINE = 16f, LABEL_SIDE = 2f;
  /** BadgeTokens, as on the rail: a 6dp dot, or 16dp tall around labelSmall, at the icon's top end corner. */
  private static final float BADGE_SMALL = 6f, BADGE_SMALL_START = 18f, BADGE_LARGE = 16f, BADGE_PADDING = 4f, BADGE_LARGE_START = 12f, BADGE_LARGE_TOP = -2f;
  /** Scrolled away: this much further than the window's bottom edge, so its shadow goes too. */
  private static final float HIDE_EXTRA = 12f;

  /** The labels under the icons: short ones (Saved, not Saved messages; the full name stays the description). */
  private static final int[] DEST_BAR_LABEL = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramBottomNavSaved, R.string.Settings,
    R.string.XgramNavProfileLabel /* the toolbar's profile: never on this bar */, R.string.XgramActivity};

  private final BaseActivity context;
  private final MainController list;
  /** Expressive design's colors and type; the classic look draws with Telegram X's. Changing it restarts the app. */
  private final boolean m3;
  private final TextPaint labelPaint, badgePaint;
  private final float labelCenter, badgeCenter;
  private final BarView bar;
  /** 0: on screen, 1: scrolled away below the window (spatial default spring). */
  private final XgramExpressive.Spring hideSpring;

  private int[] destinations = new int[0];
  private int bottomOffset;
  private boolean destroyed, scrollHidden, forceHidden;

  // Badges: the chat list's account, its unread chats and its missed calls since the call list was last seen (the
  // same mark as the rail's, so a call seen with one of them is seen for both).

  private Tdlib badgeTdlib; // set once, in the constructor (not final: the listeners above read it)
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
    if (!destroyed) {
      updateUnreadBadge();
    }
  };

  private final Runnable missedCheck = () -> {
    if (!destroyed) {
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
          if (!destroyed && tdlib == badgeTdlib) {
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

  // Repaints with the colors of a day/night switch while it runs (every color is read when drawn).
  private final ThemeChangeListener themeListener = new ThemeChangeListener() {
    @Override
    public boolean needsTempUpdates () {
      return true;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state) {
      invalidateAll();
    }
  };

  public XgramNavBar (BaseActivity context, MainController list) {
    this.context = context;
    this.list = list;
    this.m3 = XgramExpressive.isEnabled();
    if (m3) {
      this.labelPaint = XgramNavRail.newPaint(XgramExpressive.TS_LABEL_M);
      this.badgePaint = XgramNavRail.newPaint(XgramExpressive.TS_LABEL_S);
    } else {
      this.labelPaint = classicPaint(Fonts.getRobotoMedium(), 12f);
      this.badgePaint = classicPaint(Fonts.getRobotoMedium(), 11f);
    }
    this.labelCenter = XgramNavRail.centerOffset(labelPaint);
    this.badgeCenter = XgramNavRail.centerOffset(badgePaint);
    this.bar = new BarView(context);
    this.hideSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyHide()).setThreshold(.002f);
    this.hideSpring.setEndListener(this::applyHide);
    this.badgeTdlib = list.tdlib();
    ThemeManager.instance().addThemeListener(themeListener);
    TdlibManager.instance().global().addCallListener(callListener);
    TdlibManager.instance().global().addCountersListener(badgeSettingsListener);
    badgeTdlib.listeners().addTotalChatCounterListener(counterListener);
    com.fahadali.xgram.activity.XgramActivityBadge.instance().addListener(activityBadgeListener);
  }

  // Activity's badge, while it takes Calls' place (In the bottom bar)
  private final com.fahadali.xgram.activity.XgramActivityBadge.Listener activityBadgeListener = this::updateActivityBadge;
  private int activityCount;

  private void updateActivityBadge () {
    if (destroyed) {
      return;
    }
    int count = com.fahadali.xgram.activity.XgramActivityBadge.badgeCount(badgeTdlib);
    if (count != activityCount) {
      activityCount = count;
      bar.setBadge(XgramNavRail.DEST_ACTIVITY, count, false);
    }
  }

  // API for MainController

  public View view () {
    return bar;
  }

  /** What the pages, the compose button and the snackbars keep clear above the navigation bar (and the folder tabs). */
  public static int room () {
    return Screen.dp(HEIGHT + BOTTOM_GAP);
  }

  /** Adds the bar to the chat list's content at {@code index}: under the compose button, whose menu goes over it. */
  public void attach (ViewGroup parent, int index) {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(HEIGHT), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    params.leftMargin = params.rightMargin = Screen.dp(SIDE_MARGIN);
    params.bottomMargin = bottomOffset + Screen.dp(BOTTOM_GAP);
    parent.addView(bar, Math.max(0, Math.min(index, parent.getChildCount())), params);
  }

  /** What is under the bar, in px: the navigation bar and, when they are at the bottom, the folder tabs. */
  public void setBottomOffset (int px) {
    px = Math.max(0, px);
    if (bottomOffset == px) {
      return;
    }
    bottomOffset = px;
    ViewGroup.LayoutParams params = bar.getLayoutParams();
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ((ViewGroup.MarginLayoutParams) params).bottomMargin = px + Screen.dp(BOTTOM_GAP);
      bar.setLayoutParams(params);
    }
    applyHide();
  }

  /** New destinations (when they changed), the page on screen, the badges and the labels. */
  public void sync () {
    if (destroyed) {
      return;
    }
    int[] destinations = XgramNavRail.destinations(list, true);
    if (!Arrays.equals(this.destinations, destinations)) {
      this.destinations = destinations;
      bar.setDestinations(destinations);
    } else {
      bar.updateLabels();
    }
    updateUnreadBadge(); // cheap, repaints only on a change
    updateActivityBadge();
    setSelected(computeSelected(), isLaidOut());
    refreshMissedCalls(false);
  }

  /** The chat list's page changed (a tab, a swipe, the bar): the indicator follows it. */
  public void onPageChanged () {
    if (!destroyed) {
      setSelected(computeSelected(), isLaidOut());
    }
  }

  /** The language changed: new labels, and the order of the destinations for the text direction. */
  public void onLanguageChanged () {
    if (!destroyed) {
      bar.updateLabels();
      bar.requestLayout();
      invalidateAll();
    }
  }

  /** Scrolling a page down hides the bar, scrolling it up brings the bar back. */
  public void setScrollHidden (boolean hidden) {
    if (scrollHidden != hidden) {
      scrollHidden = hidden;
      updateHidden(true);
    }
  }

  /** Search and chat selection take the bottom of the list: the bar steps aside until they end. */
  public void setForceHidden (boolean hidden, boolean animated) {
    if (forceHidden != hidden) {
      forceHidden = hidden;
      updateHidden(animated);
    }
  }

  public void destroy () {
    if (destroyed) {
      return;
    }
    destroyed = true;
    hideSpring.cancel();
    bar.indicatorSpring.cancel();
    UI.cancel(missedCheck);
    UI.cancel(badgeUpdate);
    badgeTdlib.listeners().removeTotalChatCounterListener(counterListener);
    ThemeManager.instance().removeThemeListener(themeListener);
    TdlibManager.instance().global().removeCallListener(callListener);
    TdlibManager.instance().global().removeCountersListener(badgeSettingsListener);
    com.fahadali.xgram.activity.XgramActivityBadge.instance().removeListener(activityBadgeListener);
  }

  private boolean isLaidOut () {
    return bar.getWidth() > 0 && bar.getParent() != null;
  }

  private void invalidateAll () {
    bar.invalidate();
    for (int i = 0; i < bar.getChildCount(); i++) {
      bar.getChildAt(i).invalidate();
    }
  }

  // Hiding on scroll

  private void updateHidden (boolean animated) {
    float target = scrollHidden || forceHidden ? 1f : 0f;
    if (animated && isLaidOut()) {
      hideSpring.animateTo(target);
    } else {
      hideSpring.setValue(target);
    }
    applyHide();
  }

  private void applyHide () {
    float value = hideSpring.getValue();
    float distance = Screen.dp(HEIGHT) + bottomOffset + Screen.dp(BOTTOM_GAP) + Screen.dp(HIDE_EXTRA);
    bar.setTranslationY(value * distance);
    // Settled away: no touches, no accessibility focus
    int visibility = hideSpring.getTarget() >= 1f && value >= .999f ? View.INVISIBLE : View.VISIBLE;
    if (bar.getVisibility() != visibility) {
      bar.setVisibility(visibility);
    }
  }

  // Destination on screen: the chat list's page (Chats or Calls). Screens opened from the bar never take the indicator.

  private void setSelected (int dest, boolean animated) {
    bar.setSelected(dest, animated);
    if (isCallListOnScreen()) {
      markCallsSeen();
    }
  }

  private int computeSelected () {
    int dest = list.xgramOnCallsPage() ? XgramNavRail.DEST_CALLS : XgramNavRail.DEST_CHATS;
    return indexOf(destinations, dest) != -1 ? dest : XgramNavRail.DEST_CHATS;
  }

  private static int indexOf (int[] array, int value) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return -1;
  }

  /** The Calls page with nothing over it, or a call list opened as a screen: the missed calls are seen. */
  private boolean isCallListOnScreen () {
    NavigationController navigation = context.navigation();
    ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
    return current instanceof CallListController || (current != null && current == list && list.xgramOnCallsPage());
  }

  // Badges

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
    Tdlib tdlib = badgeTdlib;
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
      bar.setBadge(XgramNavRail.DEST_CHATS, count, dot);
    }
  }

  /** The newest missed calls: those after the last one seen in the call list count. The first look counts none. */
  private void refreshMissedCalls (boolean force) {
    final Tdlib tdlib = badgeTdlib;
    if (destroyed || indexOf(destinations, XgramNavRail.DEST_CALLS) == -1) {
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
        if (destroyed || request != missedRequest) {
          return;
        }
        XgramSettings settings = XgramSettings.instance();
        int seen = settings.railCallsSeen(tdlib.id());
        if (seen == 0) {
          // Never looked before: what is in the history now was there before the bar (or the rail). With no missed
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
        if (isCallListOnScreen()) {
          markCallsSeen();
        } else if (count != missedCount) {
          missedCount = count;
          bar.setBadge(XgramNavRail.DEST_CALLS, missedCount, false);
        }
      });
    });
  }

  /** The call list is on screen: every missed call so far is seen. */
  private void markCallsSeen () {
    Tdlib tdlib = badgeTdlib;
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
      bar.setBadge(XgramNavRail.DEST_CALLS, 0, false);
    }
  }

  // Actions (the rail's, see XgramNavRail#onDestinationClick): only while the chat list is on top and nothing moves

  private boolean canNavigate () {
    NavigationController navigation = context.navigation();
    return !destroyed && !list.isDestroyed() && navigation != null && !navigation.isAnimating() && !navigation.isDestroyed() &&
      navigation.getCurrentStackItem() == list && !context.isActivityBusyWithSomething();
  }

  private void onDestinationClick (int dest) {
    if (!canNavigate()) {
      return;
    }
    final Tdlib tdlib = list.tdlib();
    switch (dest) {
      case XgramNavRail.DEST_CHATS:
      case XgramNavRail.DEST_CALLS: {
        final boolean calls = dest == XgramNavRail.DEST_CALLS;
        if (calls && !list.xgramHasCallsPage()) {
          // Folders: the chat list's pages are the folders, so Calls is a screen, as in the side menu
          if (list.navigateTo(new CallListController(context, tdlib))) {
            markCallsSeen();
          }
          return;
        }
        // Another page: the pager goes to it. The same page again scrolls it to the top, as a second tap on its tab
        // did (Chats while a filter shows: back to all chats)
        list.xgramShowRailPage(calls, true);
        setSelected(computeSelected(), true);
        break;
      }
      case XgramNavRail.DEST_CONTACTS: {
        tdlib.contacts().startSyncIfNeeded(context, true, () -> {
          if (!destroyed && !list.isDestroyed()) {
            PeopleController c = new PeopleController(context, tdlib);
            c.setNeedSearch();
            list.navigateTo(c);
          }
        });
        break;
      }
      case XgramNavRail.DEST_SAVED: {
        openSavedMessages(tdlib);
        break;
      }
      case XgramNavRail.DEST_SETTINGS: {
        list.navigateTo(new SettingsController(context, tdlib));
        break;
      }
      case XgramNavRail.DEST_ACTIVITY: {
        com.fahadali.xgram.activity.XgramActivity.open(list);
        break;
      }
    }
  }

  private void openSavedMessages (Tdlib tdlib) {
    final long userId = tdlib.myUserId();
    if (userId == 0) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      tdlib.ui().openChat(list, chat.id, new TdlibUi.ChatOpenParameters());
      return;
    }
    tdlib.send(new TdApi.CreatePrivateChat(userId, true), (remoteChat, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        final long chatId = remoteChat.id;
        UI.post(() -> {
          if (!list.isDestroyed()) {
            tdlib.ui().openChat(list, chatId, new TdlibUi.ChatOpenParameters());
          }
        });
      }
    });
  }

  // Colors: Material 3 roles with Expressive design, Telegram X's theme colors in the classic look

  private int containerColor () {
    return m3 ? XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER) : Theme.getColor(ColorId.filling);
  }

  private int indicatorColor () {
    return m3 ? XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER) : XgramExpressive.withAlpha(Theme.getColor(ColorId.iconActive), .15f);
  }

  private int iconColor (boolean active) {
    if (m3) {
      return XgramExpressive.color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
    }
    return Theme.getColor(active ? ColorId.iconActive : ColorId.icon);
  }

  private int labelColor (boolean active) {
    if (m3) {
      return XgramExpressive.color(active ? XgramExpressive.SECONDARY : XgramExpressive.ON_SURFACE_VARIANT);
    }
    return Theme.getColor(active ? ColorId.iconActive : ColorId.textLight);
  }

  /** The content color of the state layer (pressed, focused): on the indicator, or on the bar. */
  private int stateColor (boolean active) {
    if (m3) {
      return XgramExpressive.color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
    }
    return Theme.getColor(active ? ColorId.iconActive : ColorId.text);
  }

  private int badgeColor () {
    return m3 ? XgramExpressive.color(XgramExpressive.ERROR) : Theme.getColor(ColorId.badge);
  }

  private int badgeTextColor () {
    return m3 ? XgramExpressive.color(XgramExpressive.ON_ERROR) : Theme.getColor(ColorId.badgeText);
  }

  /** Only muted chats are unread: the small badge, in the muted tone. */
  private int badgeMutedColor () {
    return m3 ? XgramExpressive.color(XgramExpressive.OUTLINE) : Theme.getColor(ColorId.badgeMuted);
  }

  private static TextPaint classicPaint (Typeface typeface, float sizeDp) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(typeface);
    paint.setTextSize(Screen.dpf(sizeDp));
    return paint;
  }

  /** The indicator in an item {@code itemWidth} wide: 64dp, or 2dp narrower than the item on each side. */
  private static float indicatorWidth (float itemWidth) {
    return Math.max(Screen.dpf(ICON + 8f), Math.min(Screen.dpf(INDICATOR_WIDTH), itemWidth - Screen.dpf(INDICATOR_SIDE) * 2f));
  }

  private static void measureExactly (View view, int width, int height) {
    view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Math.max(0, height), View.MeasureSpec.EXACTLY));
  }

  // Views

  /**
   * The pill: the container and the active indicator (drawn here, so it can spring between the destinations), then
   * one item per destination, as wide as the pill allows.
   */
  private final class BarView extends ViewGroup {
    private final ArrayList<ItemView> items = new ArrayList<>();
    private final RectF rect = new RectF();
    /** Position of the active indicator, in items (springs between them: spatial fast). */
    final XgramExpressive.Spring indicatorSpring;
    private int selectedDest = XgramNavRail.DEST_CHATS;

    BarView (Context context) {
      super(context);
      setWillNotDraw(false);
      indicatorSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate()).setThreshold(.002f);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
        setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
          }
        });
      }
    }

    void setDestinations (int[] destinations) {
      for (ItemView item : items) {
        removeView(item);
      }
      items.clear();
      for (int dest : destinations) {
        ItemView item = new ItemView(getContext(), dest);
        if (dest == XgramNavRail.DEST_CHATS) {
          item.setBadge(unreadCount, unreadDot);
        } else if (dest == XgramNavRail.DEST_CALLS) {
          item.setBadge(missedCount, false);
        } else if (dest == XgramNavRail.DEST_ACTIVITY) {
          item.setBadge(activityCount, false);
        }
        items.add(item);
        addView(item);
      }
      int index = indexOfDest(selectedDest);
      indicatorSpring.setValue(Math.max(0, index));
      requestLayout();
      invalidate();
    }

    void updateLabels () {
      for (ItemView item : items) {
        item.updateLabel();
      }
    }

    void setBadge (int dest, int count, boolean dot) {
      for (ItemView item : items) {
        if (item.dest == dest) {
          item.setBadge(count, dot);
        }
      }
    }

    void setSelected (int dest, boolean animated) {
      selectedDest = dest;
      int index = -1;
      for (int i = 0; i < items.size(); i++) {
        ItemView item = items.get(i);
        item.setActive(item.dest == dest);
        if (item.dest == dest) {
          index = i;
        }
      }
      if (index != -1) {
        if (animated) {
          indicatorSpring.animateTo(index);
        } else {
          indicatorSpring.setValue(index);
        }
      }
      invalidate();
    }

    private int indexOfDest (int dest) {
      for (int i = 0; i < items.size(); i++) {
        if (items.get(i).dest == dest) {
          return i;
        }
      }
      return -1;
    }

    /** Left edge of the {@code slot}th item from the left, in a pill {@code width} wide. */
    private int slotLeft (int width, int slot) {
      int count = Math.max(1, items.size());
      float padding = Screen.dpf(INNER_PADDING);
      return Math.round(padding + (width - padding * 2f) * slot / count);
    }

    /** Right to left languages put the first destination on the right. */
    private int slotOf (int index) {
      return Lang.rtl() ? items.size() - 1 - index : index;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), Screen.dp(MAX_WIDTH));
      int height = Screen.dp(HEIGHT);
      setMeasuredDimension(width, height);
      for (int i = 0; i < items.size(); i++) {
        int slot = slotOf(i);
        measureExactly(items.get(i), slotLeft(width, slot + 1) - slotLeft(width, slot), height);
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int width = r - l, height = b - t;
      for (int i = 0; i < items.size(); i++) {
        int slot = slotOf(i);
        items.get(i).layout(slotLeft(width, slot), 0, slotLeft(width, slot + 1), height);
      }
    }

    @Override
    public boolean dispatchTouchEvent (MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
        // A swipe that starts on the bar is the bar's: it never opens the side menu or turns the list's pages
        getParent().requestDisallowInterceptTouchEvent(true);
      }
      return super.dispatchTouchEvent(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      return true; // between the destinations too: nothing under the bar gets the touch
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      float radius = height / 2f;
      rect.set(0, 0, width, height);
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(containerColor()));
      if (!items.isEmpty() && indexOfDest(selectedDest) != -1) {
        float padding = Screen.dpf(INNER_PADDING);
        float itemWidth = (width - padding * 2f) / items.size();
        float center = padding + (indicatorSpring.getValue() + .5f) * itemWidth;
        if (Lang.rtl()) {
          center = width - center;
        }
        float half = indicatorWidth(itemWidth) / 2f;
        float top = Screen.dpf(INDICATOR_TOP);
        rect.set(center - half, top, center + half, top + Screen.dpf(INDICATOR_HEIGHT));
        float indicatorRadius = rect.height() / 2f;
        c.drawRoundRect(rect, indicatorRadius, indicatorRadius, Paints.fillingPaint(indicatorColor()));
      }
      super.dispatchDraw(c);
    }
  }

  /**
   * A destination: the 24dp icon centered in the indicator area (filled and onSecondaryContainer when active,
   * outlined and onSurfaceVariant otherwise), its labelMedium label 4dp under it, and its badge. The bar draws the
   * indicator itself, so it can spring.
   */
  private final class ItemView extends XgramNavRail.StateView {
    final int dest;
    private final Drawable icon, activeIcon;
    private final RectF badgeRect = new RectF();
    private String label = "";
    /** The label as drawn: ellipsized to {@link #labelFitWidth}. */
    private String shownLabel = "";
    private float shownLabelWidth;
    private int labelFitWidth = -1;
    private boolean active;
    private int badgeCount;
    private boolean badgeDot;
    @Nullable private String badge;
    private float badgeWidth;

    ItemView (Context context, int dest) {
      super(context);
      this.dest = dest;
      Resources res = context.getResources();
      this.icon = XgramNavRail.restIcon(res, XgramNavRail.filledOf(dest), XgramNavRail.outlinedOf(dest));
      this.activeIcon = XgramNavRail.activeIcon(res, XgramNavRail.filledOf(dest), XgramNavRail.outlinedOf(dest));
      setOnClickListener(v -> onDestinationClick(this.dest));
      if (dest == XgramNavRail.DEST_CHATS) {
        // The Chats tab's filters (Unread, Groups, Archive...) had only its slide-off menu, gone with the tabs: a long
        // press opens them while the list has no folders
        setOnLongClickListener(v -> canNavigate() && list.xgramShowRailFilters());
      }
      updateLabel();
    }

    void updateLabel () {
      String label = Lang.getString(DEST_BAR_LABEL[dest]);
      if (!label.equals(this.label)) {
        this.label = label;
        this.labelFitWidth = -1;
        setContentDescription(XgramNavRail.describe(dest, badgeCount, badgeDot));
        invalidate();
      }
    }

    void setBadge (int count, boolean dot) {
      count = Math.max(0, count);
      dot = dot && count == 0;
      if (badgeCount != count || badgeDot != dot) {
        badgeCount = count;
        badgeDot = dot;
        badge = XgramNavRail.badgeText(count);
        badgeWidth = badge != null ? badgePaint.measureText(badge) : 0f;
        setContentDescription(XgramNavRail.describe(dest, count, dot));
        invalidate();
      }
    }

    void setActive (boolean active) {
      if (this.active != active) {
        this.active = active;
        setSelected(active);
        invalidate();
      }
    }

    /** Ellipsizes the label once per width (and per label). */
    private void fitLabel () {
      int available = Math.max(0, getWidth() - Screen.dp(LABEL_SIDE) * 2);
      if (labelFitWidth == available) {
        return;
      }
      labelFitWidth = available;
      CharSequence fitted = TextUtils.ellipsize(label, labelPaint, available, TextUtils.TruncateAt.END);
      shownLabel = fitted != null ? fitted.toString() : "";
      shownLabelWidth = shownLabel.isEmpty() ? 0f : labelPaint.measureText(shownLabel);
    }

    @Override
    protected int stateLayerColor () {
      // M3 state layer: onSecondaryContainer on the active indicator, onSurface on the other destinations
      return stateColor(active);
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      final float cx = getWidth() / 2f;
      final float top = Screen.dpf(INDICATOR_TOP), indicatorHeight = Screen.dpf(INDICATOR_HEIGHT);
      final float cy = top + indicatorHeight / 2f;
      final float half = indicatorWidth(getWidth()) / 2f;
      setStateShape(cx - half, top, cx + half, top + indicatorHeight, indicatorHeight / 2f);
      drawState(c);
      XgramNavRail.drawIcon(c, active ? activeIcon : icon, cx, cy, Screen.dpf(ICON), iconColor(active), 1f);

      fitLabel();
      if (!shownLabel.isEmpty()) {
        labelPaint.setColor(labelColor(active));
        float lineCenter = top + indicatorHeight + Screen.dpf(LABEL_GAP) + Screen.dpf(LABEL_LINE) / 2f;
        c.drawText(shownLabel, cx - shownLabelWidth / 2f, lineCenter + labelCenter, labelPaint);
      }

      float iconStart = rtl ? cx + Screen.dpf(ICON / 2f) : cx - Screen.dpf(ICON / 2f);
      float iconTop = cy - Screen.dpf(ICON / 2f);
      if (badge != null) {
        float height = Screen.dpf(BADGE_LARGE);
        float width = Math.max(height, badgeWidth + Screen.dpf(BADGE_PADDING * 2f));
        float start = Screen.dpf(BADGE_LARGE_START);
        float left = rtl ? iconStart - start - width : iconStart + start;
        float badgeTop = iconTop + Screen.dpf(BADGE_LARGE_TOP);
        badgeRect.set(left, badgeTop, left + width, badgeTop + height);
        c.drawRoundRect(badgeRect, height / 2f, height / 2f, Paints.fillingPaint(badgeColor()));
        badgePaint.setColor(badgeTextColor());
        c.drawText(badge, badgeRect.centerX() - badgeWidth / 2f, badgeRect.centerY() + badgeCenter, badgePaint);
      } else if (badgeDot) {
        float size = Screen.dpf(BADGE_SMALL);
        float start = Screen.dpf(BADGE_SMALL_START);
        float left = rtl ? iconStart - start - size : iconStart + start;
        c.drawCircle(left + size / 2f, iconTop + size / 2f, size / 2f, Paints.fillingPaint(badgeMutedColor()));
      }
    }
  }
}
