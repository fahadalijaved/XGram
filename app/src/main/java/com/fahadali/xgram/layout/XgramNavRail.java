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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import com.fahadali.xgram.XgramReorder;
import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.GlobalCallListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibBadgeCounter;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.CallListController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.PeopleController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * The Material 3 Expressive navigation rail of wide windows (a Fold unfolded, tablets, desktop windows), at the start
 * of the window, right before the chat list pane. {@link XgramTwoPane} owns it: it decides when it shows (Expressive
 * design, the Navigation rail setting, a window that gets two panes or is at least 600 x 480dp), lays it out and tells
 * it when the screens or the chat list's page change.
 * <ul>
 *   <li>Collapsed rail, 72dp: the menu button in the app bar row, then icon-only destinations (labels are their
 *   descriptions and tooltips). Chats and Calls are the chat list's two pages (they replace its Chats / Calls tabs, so
 *   the list shows a plain title) and the active one sits in a 56 x 32dp tonal pill that springs between them.
 *   Contacts, Saved messages and Settings open a screen, as from the side menu, and never take the pill. Chats has an
 *   unread badge, Calls a missed calls one.</li>
 *   <li>Modal expanded rail (the menu button): slides in over the content with a scrim, below the status bar, and
 *   holds the whole side menu: the destinations with their labels, the accounts, XGram's own pages and the switches.
 *   Back, the scrim or the menu button close it.</li>
 * </ul>
 * There is no FAB on the rail: the chat list keeps its own extended "New chat" FAB and FAB menu at the bottom end of
 * its pane. Values are androidx.compose.material3 tokens: NavigationRailCollapsedTokens and
 * NavigationRailVerticalItemTokens (indicator, icon), NavigationRailExpandedTokens and
 * NavigationRailHorizontalItemTokens (expanded rail), BadgeTokens and SmallIconButtonTokens.
 */
public final class XgramNavRail {
  // Collapsed rail

  /**
   * Rail width: the 56dp indicator with 8dp on each side. Material 3 rails are at most 80dp (96dp for the Expressive
   * one with labels); without labels or a FAB nothing needs more, and the chat list pane starts right after it.
   */
  static final float WIDTH = 72f;
  /** NavigationRailVerticalItemTokens: 56 x 32dp full-corner indicator, 24dp icon. */
  private static final float INDICATOR_WIDTH = 56f, INDICATOR_HEIGHT = 32f, ICON = 24f;
  /** A destination: the whole 72 x 56dp cell is the touch target, the indicator centered in it (12dp above and below). */
  private static final float ITEM_HEIGHT = 56f;
  /** The menu button sits in the 56dp app bar row of the panes, with the 40dp state layer of a small icon button. */
  private static final float BAND = 56f, MENU_STATE = 40f;
  /** From the app bar row to the first destination, so the menu button doesn't read as one of them. */
  private static final float HEADER_GAP = 8f;
  /** Kept free under the last destination (with the navigation bar) for the rail to show at all. */
  private static final float BOTTOM_GAP = 16f;
  /**
   * BadgeTokens: the small badge is a 6dp dot at the icon's top end corner (18dp from its start edge); the large one
   * is 16dp tall, at least 16dp wide, 4dp of padding around labelSmall, 12dp from the icon's start and 2dp above it.
   */
  private static final float BADGE_SMALL = 6f, BADGE_SMALL_START = 18f, BADGE_LARGE = 16f, BADGE_PADDING = 4f, BADGE_LARGE_START = 12f, BADGE_LARGE_TOP = -2f;

  // Modal expanded rail

  /** NavigationRailExpandedTokens: 220 to 360dp, surfaceContainer, CornerLarge, level 2 (3dp). */
  private static final float EXPANDED_MIN = 220f, EXPANDED_MAX = 360f, EXPANDED_CORNER = 16f;
  /** At least this much scrim stays visible next to the panel on a narrow window. */
  private static final float EXPANDED_SCRIM_MIN = 56f;
  /**
   * Rows start where the collapsed indicator does (8dp), so their 24dp icons sit exactly over the collapsed rail's
   * (36dp from the start); NavigationRailHorizontalItemTokens: 56dp full-corner rows, 16dp leading, 8dp icon to
   * labelLarge, 16dp trailing.
   */
  private static final float ROW_PADDING = (WIDTH - INDICATOR_WIDTH) / 2f;
  private static final float ROW_HEIGHT = 56f, ROW_LEADING = 16f, ROW_ICON_LABEL = 8f, ROW_TRAILING = 16f;
  private static final float AVATAR = 32f, SECTION_HEIGHT = 48f, SECTION_GAP = 8f, DIVIDER_HEIGHT = 17f, SWITCH_RESERVE = 52f + 8f, SWITCH_END = 20f;
  private static final float TEXT_START = ROW_PADDING + ROW_LEADING + ICON + ROW_ICON_LABEL;
  private static final float AVATAR_TEXT_START = ROW_PADDING + ROW_LEADING + ICON / 2f + AVATAR / 2f + ROW_ICON_LABEL;
  /** The panel's background runs this far past its start edge, so the opening spring's overshoot never shows a gap. */
  private static final float OVERSHOOT_COVER = 64f;

  // Destinations

  static final int DEST_CHATS = 0, DEST_CALLS = 1, DEST_CONTACTS = 2, DEST_SAVED = 3, DEST_SETTINGS = 4;

  /** Filled (the active destination) Material icons; with another icon pack, that pack's drawing of them. */
  private static final int[] DEST_FILLED = {
    R.drawable.baseline_chat_bubble_24, R.drawable.baseline_call_24, R.drawable.baseline_perm_contact_calendar_24,
    R.drawable.baseline_bookmark_24, R.drawable.baseline_settings_24
  };
  /** Outlined (every other destination): the XGram Line family, as the rest of Material 3 Expressive with the Classic pack. */
  private static final int[] DEST_OUTLINED = {
    R.drawable.xgl_chat_bubble, R.drawable.xgl_phone, R.drawable.xgl_contacts,
    R.drawable.xgl_bookmark, R.drawable.xgl_settings
  };
  private static final int[] DEST_LABEL = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramNavRailSavedMessages, R.string.Settings};
  /** Side menu keys (XgramDrawerItemsController): hidden there, hidden here, and in the same order. */
  private static final String[] DEST_KEY = {null, "calls", "contacts", "saved", "settings"};

  /** Missed calls are looked up again at most this often while screens change (a missed call looks them up at once). */
  private static final long MISSED_CHECK_INTERVAL_MS = 30_000L;
  private static final int MISSED_FETCH = 20;

  private final BaseActivity context;
  private final XgramTwoPane owner;
  private final TextPaint labelLarge, labelSmall, titleSmall;
  private final float labelLargeCenter, labelSmallCenter, titleSmallCenter;
  private final RailView rail;

  private int[] destinations = new int[0];
  private int selected = DEST_CHATS;
  private boolean destroyed;

  @Nullable private PopupLayout expandedPopup;
  @Nullable private ExpandedLayout expandedLayout;

  // Badges: the account they count for, its unread chats and its missed calls since the call list was last seen.

  @Nullable private Tdlib badgeTdlib;
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

  // A call that ended unanswered: look the missed calls up again once its message is in the history.
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

  // The chat folder badge settings (count messages, muted, archived) changed: the Chats badge counts again. They are
  // global (Config.CHAT_FOLDERS_APPEARANCE_IS_GLOBAL), so they come as a global badge reset, not a chat list change.
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

  XgramNavRail (BaseActivity context, XgramTwoPane owner) {
    this.context = context;
    this.owner = owner;
    this.labelLarge = newPaint(XgramExpressive.TS_LABEL_L);
    this.labelSmall = newPaint(XgramExpressive.TS_LABEL_S);
    this.titleSmall = newPaint(XgramExpressive.TS_TITLE_S);
    this.labelLargeCenter = centerOffset(labelLarge);
    this.labelSmallCenter = centerOffset(labelSmall);
    this.titleSmallCenter = centerOffset(titleSmall);
    this.rail = new RailView(context);
    ThemeManager.instance().addThemeListener(themeListener);
    TdlibManager.instance().global().addCallListener(callListener);
    TdlibManager.instance().global().addCountersListener(badgeSettingsListener);
  }

  // API for XgramTwoPane and the settings pages

  View view () {
    return rail;
  }

  /** Width of the rail view: the 72dp rail and the system inset on its side (a cutout, a side navigation bar). */
  int width () {
    return Screen.dp(WIDTH) + rail.startInset;
  }

  void setStartInset (int px) {
    rail.setStartInset(px);
  }

  /** Why the Navigation rail setting can't be changed (Appearance > Layout), or 0. */
  public static @StringRes int settingReason () {
    return XgramExpressive.isEnabled() ? 0 : R.string.XgramNavRailNeedsExpressive;
  }

  /**
   * The destinations, in this order: Chats and Calls (the chat list's pages; with folders Calls is a screen, as in the
   * side menu, and the side menu settings can hide it then), then Contacts, Saved messages and Settings as the side
   * menu has them (hidden there, hidden here; in its order).
   */
  static int[] destinations (@Nullable MainController list) {
    XgramSettings xgram = XgramSettings.instance();
    ArrayList<Integer> result = new ArrayList<>();
    result.add(DEST_CHATS);
    boolean callsPage = list == null || list.xgramHasCallsPage();
    if (callsPage || !xgram.drawerItemHidden("calls")) {
      result.add(DEST_CALLS);
    }
    ArrayList<Integer> screens = new ArrayList<>();
    if (!xgram.drawerItemHidden("contacts")) {
      screens.add(DEST_CONTACTS);
    }
    if (!xgram.drawerItemHidden("saved")) {
      screens.add(DEST_SAVED);
    }
    screens.add(DEST_SETTINGS);
    XgramReorder.sort(XgramReorder.DRAWER, screens, dest -> DEST_KEY[dest]);
    result.addAll(screens);
    int[] array = new int[result.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = result.get(i);
    }
    return array;
  }

  /** Whether the menu button and {@code count} destinations fit a window {@code heightDp} tall above its bottom inset. */
  static boolean fits (int count, int heightDp, int bottomInsetDp) {
    float statusBarDp = HeaderView.getTopOffset() / Screen.density();
    float needed = statusBarDp + BAND + HEADER_GAP + count * ITEM_HEIGHT + BOTTOM_GAP + Math.max(0, bottomInsetDp);
    return heightDp >= needed;
  }

  /** New destinations (when they changed), the one on screen now, and the badges' account. */
  void sync (int[] destinations) {
    if (!Arrays.equals(this.destinations, destinations)) {
      this.destinations = destinations;
      rail.setDestinations(destinations);
    } else {
      rail.updateLabels();
    }
    MainController root = owner.railRoot();
    setBadgeTdlib(root != null ? root.tdlib() : null);
    updateUnreadBadge(); // cheap, repaints only on a change (badge settings changed on a screen that just closed)
    setSelected(computeSelected(), rail.isLaidOut());
    refreshMissedCalls(false);
  }

  void onThemeChanged () {
    invalidateAll();
  }

  /** Closes the expanded rail at once (the rail is going away). */
  void dismissPopups () {
    if (expandedPopup != null) {
      expandedPopup.hideWindow(false);
      expandedPopup = null;
      expandedLayout = null;
    }
  }

  void destroy () {
    destroyed = true;
    dismissPopups();
    setBadgeTdlib(null);
    UI.cancel(missedCheck);
    UI.cancel(badgeUpdate);
    ThemeManager.instance().removeThemeListener(themeListener);
    TdlibManager.instance().global().removeCallListener(callListener);
    TdlibManager.instance().global().removeCountersListener(badgeSettingsListener);
  }

  private void invalidateAll () {
    invalidateTree(rail);
    if (expandedLayout != null) {
      invalidateTree(expandedLayout);
    }
  }

  private static void invalidateTree (View view) {
    view.invalidate();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        invalidateTree(group.getChildAt(i));
      }
    }
  }

  // Destination on screen: the chat list's page (Chats or Calls). Screens opened from the rail never take the pill.

  private void setSelected (int dest, boolean animated) {
    selected = dest;
    rail.setSelected(dest, animated);
    // The pill can stay on Calls under a screen, and a call list opened as a screen never takes it: what counts as
    // seen is the call list being on screen
    if (owner.isCallListOnScreen()) {
      markCallsSeen();
    }
  }

  private int computeSelected () {
    MainController list = owner.railChatList();
    int dest = list != null && list.xgramOnCallsPage() ? DEST_CALLS : DEST_CHATS;
    return indexOf(destinations, dest) != -1 ? dest : DEST_CHATS;
  }

  private static int indexOf (int[] array, int value) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return -1;
  }

  // Badges

  private void setBadgeTdlib (@Nullable Tdlib tdlib) {
    if (badgeTdlib == tdlib) {
      return;
    }
    if (badgeTdlib != null) {
      badgeTdlib.listeners().removeTotalChatCounterListener(counterListener);
    }
    badgeTdlib = tdlib;
    newestMissed = 0;
    missedCount = 0;
    missedCheckedAt = 0;
    missedRequest++;
    if (tdlib != null) {
      tdlib.listeners().addTotalChatCounterListener(counterListener);
    }
    updateUnreadBadge();
    updateMissedBadge();
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
   * As the chat list's own counters count (MainController#updateCounter, the folder badge settings): a count while
   * there are unread chats (or messages) that aren't muted, a dot when only muted ones are unread.
   */
  private void updateUnreadBadge () {
    Tdlib tdlib = badgeTdlib;
    int count = 0;
    boolean dot = false;
    if (tdlib != null) {
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
    }
    if (count != unreadCount || dot != unreadDot) {
      unreadCount = count;
      unreadDot = dot;
      rail.setBadge(DEST_CHATS, count, dot);
    }
  }

  private void updateMissedBadge () {
    rail.setBadge(DEST_CALLS, missedCount, false);
  }

  /** The newest missed calls: those after the last one seen in the call list count. The first look counts none. */
  private void refreshMissedCalls (boolean force) {
    final Tdlib tdlib = badgeTdlib;
    if (tdlib == null || destroyed || indexOf(destinations, DEST_CALLS) == -1) {
      return;
    }
    long now = SystemClock.uptimeMillis();
    if (!force && missedCheckedAt != 0 && now - missedCheckedAt < MISSED_CHECK_INTERVAL_MS) {
      return;
    }
    missedCheckedAt = now;
    final int request = ++missedRequest;
    tdlib.send(new TdApi.SearchCallMessages(null, MISSED_FETCH, true), (found, error) -> {
      if (found == null || found.messages == null) {
        return;
      }
      int newest = 0;
      for (TdApi.Message message : found.messages) {
        if (isMissedCall(message)) {
          newest = Math.max(newest, message.date);
        }
      }
      final int newestDate = newest;
      final TdApi.Message[] messages = found.messages;
      UI.post(() -> {
        if (destroyed || tdlib != badgeTdlib || request != missedRequest) {
          return;
        }
        XgramSettings settings = XgramSettings.instance();
        int seen = settings.railCallsSeen(tdlib.id());
        if (seen == 0) {
          // Never looked before: what is in the history now was there before the rail. With no missed call at all
          // yet, now (the server's clock, as message dates), so the first one to come in still counts.
          seen = newestDate > 0 ? newestDate : (int) (tdlib.currentTimeMillis() / 1000L);
          settings.setRailCallsSeen(tdlib.id(), seen);
        }
        int count = 0;
        for (TdApi.Message message : messages) {
          if (isMissedCall(message) && message.date > seen) {
            count++;
          }
        }
        newestMissed = Math.max(newestMissed, newestDate);
        if (owner.isCallListOnScreen()) {
          markCallsSeen();
        } else if (count != missedCount) {
          missedCount = count;
          updateMissedBadge();
        }
      });
    });
  }

  /** An incoming call nobody answered (not one declined here). */
  private static boolean isMissedCall (@Nullable TdApi.Message message) {
    if (message == null || message.isOutgoing || message.content == null || message.content.getConstructor() != TdApi.MessageCall.CONSTRUCTOR) {
      return false;
    }
    TdApi.CallDiscardReason reason = ((TdApi.MessageCall) message.content).discardReason;
    return reason != null && reason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR;
  }

  /** The call list is on screen: every missed call so far is seen. */
  private void markCallsSeen () {
    Tdlib tdlib = badgeTdlib;
    if (tdlib != null) {
      XgramSettings settings = XgramSettings.instance();
      int stored = settings.railCallsSeen(tdlib.id());
      // Everything up to now is seen: the newest missed call known, or the server's clock (message dates are the
      // server's) if that is later, as the list just shown may hold calls the last lookup has not seen yet
      int seen = Math.max(newestMissed, (int) (tdlib.currentTimeMillis() / 1000L));
      if (seen > stored) {
        settings.setRailCallsSeen(tdlib.id(), seen);
      }
    }
    if (missedCount != 0) {
      missedCount = 0;
      updateMissedBadge();
    }
  }

  // Actions (the side menu's, see DrawerController#onClick)

  private boolean canNavigate () {
    NavigationController navigation = context.navigation();
    return navigation != null && !navigation.isAnimating() && !navigation.isDestroyed();
  }

  /** A screen replaces whatever the right pane (or the single pane) shows above the chat list. */
  private boolean open (ViewController<?> c) {
    NavigationController navigation = context.navigation();
    if (navigation == null || navigation.isAnimating()) {
      return false;
    }
    if (navigation.getStackSize() > 1) {
      navigation.setControllerAnimated(c, true, true);
      return true;
    }
    return navigation.navigateTo(c);
  }

  /** Back to {@code index} in {@code navigation}: the screens between are dropped, the top one animates away. */
  private static boolean popTo (NavigationController navigation, int index) {
    if (navigation.isAnimating()) {
      return false;
    }
    NavigationStack stack = navigation.getStack();
    int current = stack.getCurrentIndex();
    if (current <= index) {
      return false;
    }
    for (int i = current - 1; i > index; i--) {
      stack.destroy(i);
    }
    navigation.navigateBack();
    return true;
  }

  void onDestinationClick (int dest) {
    MainController root = owner.railRoot();
    NavigationController navigation = context.navigation();
    if (root == null || navigation == null || !canNavigate()) {
      return;
    }
    final Tdlib tdlib = root.tdlib();
    switch (dest) {
      case DEST_CHATS:
      case DEST_CALLS: {
        final boolean calls = dest == DEST_CALLS;
        MainController list = owner.railChatList();
        if (list == null) {
          return;
        }
        if (calls && !list.xgramHasCallsPage()) {
          // Folders: the chat list's pages are the folders, so Calls is a screen, as in the side menu
          if (open(new CallListController(context, tdlib))) {
            markCallsSeen();
          }
          return;
        }
        // Back to the chat list first: the archive open in the chat list pane, or with one pane what is above it
        NavigationController left = owner.railLeftNavigation();
        boolean popped = left != null ? popTo(left, 0) : popTo(navigation, 0);
        // The same page again scrolls it to the top, as a second tap on its tab did
        list.xgramShowRailPage(calls, !popped);
        setSelected(computeSelected(), true); // the page the pager went to (none while chats are selected)
        break;
      }
      case DEST_CONTACTS: {
        tdlib.contacts().startSyncIfNeeded(context, true, () -> {
          PeopleController c = new PeopleController(context, tdlib);
          c.setNeedSearch();
          open(c);
        });
        break;
      }
      case DEST_SAVED: {
        openSavedMessages(tdlib);
        break;
      }
      case DEST_SETTINGS: {
        open(new SettingsController(context, tdlib));
        break;
      }
    }
  }

  /** The chat list's page changed (a tab, a swipe, the rail): the pill follows it. */
  void onPageChanged () {
    setSelected(computeSelected(), rail.isLaidOut());
  }

  private void openSavedMessages (Tdlib tdlib) {
    final long userId = tdlib.myUserId();
    final ViewController<?> from = owner.railChatList();
    if (userId == 0 || from == null) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      tdlib.ui().openChat(from, chat.id, new TdlibUi.ChatOpenParameters());
      return;
    }
    tdlib.send(new TdApi.CreatePrivateChat(userId, true), (remoteChat, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        final long chatId = remoteChat.id;
        UI.post(() -> {
          if (!from.isDestroyed()) {
            tdlib.ui().openChat(from, chatId, new TdlibUi.ChatOpenParameters());
          }
        });
      }
    });
  }

  /** The side menu entries of the expanded rail that are not destinations (R.id values: no switch). */
  private void onMenuAction (int id) {
    MainController root = owner.railRoot();
    if (root == null) {
      return;
    }
    final Tdlib tdlib = root.tdlib();
    NavigationController navigation = context.navigation();
    ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
    if (id == R.id.btn_xgramHub) {
      if (canNavigate()) {
        open(com.fahadali.xgram.compose.XgramSettingsPages.main(context, tdlib));
      }
    } else if (id == R.id.btn_xgramBookmarks) {
      if (canNavigate()) {
        open(new com.fahadali.xgram.chats.XgramBookmarksController(context, tdlib));
      }
    } else if (id == R.id.btn_xgramSavedByChat) {
      if (canNavigate()) {
        open(new com.fahadali.xgram.chats.XgramSavedTopicsController(context, tdlib));
      }
    } else if (id == R.id.btn_invite) {
      tdlib.cache().getInviteText(text -> {
        if (text != null) {
          final String value = text.text;
          UI.post(() -> Intents.shareText(value));
        }
      });
    } else if (id == R.id.btn_proxy) {
      if (current != null) {
        tdlib.ui().openProxySettings(current, false);
      }
    } else if (id == R.id.btn_help) {
      if (current != null) {
        tdlib.ui().openSupport(current);
      }
    } else if (id == R.id.btn_addAccount) {
      tdlib.ui().addAccount(context, true, false);
    } else if (id == R.id.btn_xgramGhostMode) {
      // Long press: Ghost mode's settings (Privacy, Ghost mode first)
      if (canNavigate()) {
        open(com.fahadali.xgram.compose.XgramSettingsPages.ghost(context, tdlib));
      }
    }
  }

  // Expanded rail

  private static boolean isShowing (@Nullable PopupLayout popup) {
    return popup != null && !popup.isWindowHidden() && !popup.isDestroyed();
  }

  /** The menu button: the modal expanded rail. */
  void expand () {
    if (isShowing(expandedPopup) || owner.railRoot() == null || context.isActivityBusyWithSomething()) {
      return;
    }
    PopupLayout popup = new PopupLayout(context);
    popup.setOverlayStatusBar(true);
    popup.init(true);
    popup.setNeedFullScreen(Settings.instance().useEdgeToEdge());
    ExpandedLayout layout = new ExpandedLayout(context);
    popup.setDismissListener(p -> {
      if (expandedPopup == p) {
        expandedPopup = null;
        expandedLayout = null;
      }
    });
    expandedPopup = popup;
    expandedLayout = layout;
    // A modal side menu: the keyboard of the field under it (the chat's input, the list's search) goes, in either pane
    View focus = context.getCurrentFocus();
    if (focus != null) {
      org.thunderdog.challegram.tool.Keyboard.hide(focus);
    }
    popup.showAnimatedPopupView(layout, layout);
  }

  // Drawing helpers

  private static TextPaint newPaint (int ts) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    paint.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(ts)));
    paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    return paint;
  }

  /** Distance from a line's vertical center to its baseline. */
  private static float centerOffset (Paint paint) {
    Paint.FontMetrics metrics = paint.getFontMetrics();
    return -(metrics.ascent + metrics.descent) / 2f;
  }

  private static int color (int role) {
    return XgramExpressive.color(role);
  }

  private static boolean classicPack () {
    return XgramIconPack.current() == XgramIconPack.PACK_TELEGRAM_X;
  }

  /** A rest-state icon: outlined (XGram Line) with the Classic pack, the pack's own drawing otherwise. */
  private static Drawable restIcon (Resources res, int filled, int outlined) {
    return Drawables.get(res, classicPack() ? outlined : filled);
  }

  /** The active destination's icon: the filled Material icon with the Classic pack, the pack's own drawing otherwise. */
  private static Drawable activeIcon (Resources res, int filled, int outlined) {
    if (classicPack()) {
      Drawable drawable = ResourcesCompat.getDrawable(res, filled, null); // not through the icon pack table
      if (drawable != null) {
        return drawable.mutate();
      }
      return Drawables.get(res, outlined);
    }
    return Drawables.get(res, filled);
  }

  /** Content description and (Android 8+) tooltip: the rail shows no labels. */
  private static void setLabel (View view, String label) {
    view.setContentDescription(label);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      view.setTooltipText(label);
    }
  }

  private static final Paint ICON_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private static final Paint GLYPH_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

  static {
    GLYPH_PAINT.setStyle(Paint.Style.STROKE);
    GLYPH_PAINT.setStrokeCap(Paint.Cap.ROUND);
    GLYPH_PAINT.setStrokeJoin(Paint.Join.ROUND);
  }

  private static void drawIcon (Canvas c, @Nullable Drawable drawable, float cx, float cy, float sizePx, int color, float alpha) {
    if (drawable == null || alpha <= 0f) {
      return;
    }
    ICON_PAINT.setColorFilter(Paints.getColorFilter(color)); // the color's own alpha is in the filter
    ICON_PAINT.setAlpha(Math.round(255f * MathUtils.clamp(alpha)));
    int width = Math.max(1, drawable.getMinimumWidth()), height = Math.max(1, drawable.getMinimumHeight());
    float scale = sizePx / Math.max(width, height);
    if (Math.abs(scale - 1f) < .02f) {
      Drawables.drawCentered(c, drawable, cx, cy, ICON_PAINT);
      return;
    }
    int saveCount = c.save();
    c.translate(cx, cy);
    c.scale(scale, scale);
    Drawables.draw(c, drawable, -width / 2f, -height / 2f, ICON_PAINT);
    c.restoreToCount(saveCount);
  }

  /**
   * Material Symbols "menu" ({@code open} 0) and "menu_open" ({@code open} 1) in a 24dp box centered on (cx, cy): 2dp
   * round lines from 4 to 20, shortened to 15 and 12, and the arrow towards the start edge.
   */
  private static void drawMenuGlyph (Canvas c, float cx, float cy, float open, int color, boolean rtl) {
    final float u = Screen.dpf(1f);
    GLYPH_PAINT.setStrokeWidth(2f * u);
    GLYPH_PAINT.setColor(color);
    final float left = cx - 8f * u;
    final float outer = 16f * u - 5f * u * open, middle = 16f * u - 8f * u * open;
    int saveCount = c.save();
    if (rtl) {
      c.scale(-1f, 1f, cx, cy);
    }
    c.drawLine(left, cy - 5f * u, left + outer, cy - 5f * u, GLYPH_PAINT);
    c.drawLine(left, cy, left + middle, cy, GLYPH_PAINT);
    c.drawLine(left, cy + 5f * u, left + outer, cy + 5f * u, GLYPH_PAINT);
    if (open > 0f) {
      GLYPH_PAINT.setAlpha(Math.round(Color.alpha(color) * MathUtils.clamp(open)));
      float tipX = cx + 4.5f * u, armX = cx + 8f * u;
      c.drawLine(armX, cy - 3.5f * u, tipX, cy, GLYPH_PAINT);
      c.drawLine(tipX, cy, armX, cy + 3.5f * u, GLYPH_PAINT);
    }
    c.restoreToCount(saveCount);
  }

  private static void measureExactly (View view, int width, int height) {
    view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Math.max(0, height), View.MeasureSpec.EXACTLY));
  }

  /** A badge count as the expanded rail's trailing text (and the descriptions): null for none. */
  @Nullable
  private static String badgeText (int count) {
    return count > 0 ? Strings.buildCounter(count) : null;
  }

  /** "Chats, 5 unread" / "Calls, 2 missed" for screen readers. */
  private static String describe (int dest, int count, boolean dot) {
    String label = Lang.getString(DEST_LABEL[dest]);
    if (count > 0) {
      return Lang.getString(dest == DEST_CALLS ? R.string.XgramNavRailMissed : R.string.XgramNavRailUnread, label, Strings.buildCounter(count));
    }
    if (dot) {
      return Lang.getString(R.string.XgramNavRailUnreadMuted, label);
    }
    return label;
  }

  /**
   * A view with an M3 state layer (ripple: hover, focus and press) on a shape inside it: the indicator of a
   * destination, the circle of an icon button, a row. The state layer is the content color at 10%.
   */
  private abstract static class StateView extends View {
    private final GradientDrawable mask;
    private final RippleDrawable ripple;
    private int rippleColor;
    private float maskRadius = -1f;

    StateView (Context context) {
      super(context);
      mask = new GradientDrawable();
      mask.setColor(0xffffffff);
      ripple = new RippleDrawable(ColorStateList.valueOf(0), null, mask);
      ripple.setCallback(this);
      setClickable(true);
      setFocusable(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Keyboard focus shows in the shape (the ripple's focused layer), not as the framework's rectangle
        setDefaultFocusHighlightEnabled(false);
      }
    }

    /** The content color the state layer is made of. */
    protected abstract int stateLayerColor ();

    protected final void setStateShape (float left, float top, float right, float bottom, float radius) {
      if (ripple == null) {
        return;
      }
      ripple.setBounds(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
      if (maskRadius != radius) {
        maskRadius = radius;
        mask.setCornerRadius(radius);
      }
    }

    protected final void drawState (Canvas c) {
      if (ripple == null) {
        return;
      }
      int color = XgramExpressive.withAlpha(stateLayerColor(), XgramExpressive.STATE_PRESSED);
      if (color != rippleColor) {
        rippleColor = color;
        ripple.setColor(ColorStateList.valueOf(color));
      }
      ripple.draw(c);
    }

    @Override
    protected boolean verifyDrawable (@NonNull Drawable who) {
      return (ripple != null && who == ripple) || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged () {
      super.drawableStateChanged();
      if (ripple != null) {
        ripple.setState(getDrawableState());
      }
    }

    @Override
    public void jumpDrawablesToCurrentState () {
      super.jumpDrawablesToCurrentState();
      if (ripple != null) {
        ripple.jumpToCurrentState();
      }
    }

    @Override
    public void drawableHotspotChanged (float x, float y) {
      super.drawableHotspotChanged(x, y);
      if (ripple != null) {
        ripple.setHotspot(x, y);
      }
    }
  }

  // Collapsed rail

  /**
   * The 72dp rail: surfaceContainer (the app bar's layer, so it runs on from the panes' app bars and the status bar),
   * the menu button in the app bar row below the status bar, then a 56dp cell per destination 8dp lower.
   */
  private final class RailView extends ViewGroup {
    private final MenuView menu;
    private final ArrayList<ItemView> items = new ArrayList<>();
    private final RectF indicator = new RectF();
    /** Position of the active indicator, in items (springs between them: spatial fast). */
    private final XgramExpressive.Spring indicatorSpring;
    private int startInset;
    private int selectedDest = DEST_CHATS;

    RailView (Context context) {
      super(context);
      setWillNotDraw(false);
      menu = new MenuView(context, false);
      setLabel(menu, Lang.getString(R.string.XgramNavRailOpenMenu));
      menu.setOnClickListener(v -> expand());
      addView(menu);
      indicatorSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate()).setThreshold(.002f);
    }

    /** Set while XgramTwoPane measures the container: it measures and places the rail right after, so no new layout. */
    void setStartInset (int px) {
      startInset = Math.max(0, px);
    }

    void setDestinations (int[] destinations) {
      for (ItemView item : items) {
        removeView(item);
      }
      items.clear();
      for (int dest : destinations) {
        ItemView item = new ItemView(getContext(), dest);
        if (dest == DEST_CHATS) {
          item.setBadge(unreadCount, unreadDot);
        } else if (dest == DEST_CALLS) {
          item.setBadge(missedCount, false);
        }
        items.add(item);
        addView(item);
      }
      requestLayout();
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

    private int contentLeft (int width) {
      return Lang.rtl() ? width - startInset - Screen.dp(WIDTH) : startInset;
    }

    private int itemsTop () {
      return HeaderView.getTopOffset() + Screen.dp(BAND + HEADER_GAP);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      int contentWidth = Screen.dp(WIDTH);
      measureExactly(menu, contentWidth, Screen.dp(BAND));
      for (ItemView item : items) {
        measureExactly(item, contentWidth, Screen.dp(ITEM_HEIGHT));
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int contentWidth = Screen.dp(WIDTH);
      int left = contentLeft(r - l);
      int top = HeaderView.getTopOffset();
      menu.layout(left, top, left + contentWidth, top + Screen.dp(BAND));
      int y = itemsTop(), itemHeight = Screen.dp(ITEM_HEIGHT);
      for (ItemView item : items) {
        item.layout(left, y, left + contentWidth, y + itemHeight);
        y += itemHeight;
      }
    }

    @Override
    public boolean dispatchTouchEvent (MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
        // A swipe that starts on the rail is the rail's: it never drags a pane back or opens the side menu
        getParent().requestDisallowInterceptTouchEvent(true);
      }
      return super.dispatchTouchEvent(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      return true; // the empty parts of the rail too
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      c.drawRect(0, 0, getWidth(), getHeight(), Paints.fillingPaint(color(XgramExpressive.SURFACE_CONTAINER)));
      if (!items.isEmpty() && indexOfItem(selectedDest) != -1) {
        ItemView first = items.get(0);
        float left = first.getLeft() + (Screen.dpf(WIDTH) - Screen.dpf(INDICATOR_WIDTH)) / 2f;
        float top = first.getTop() + (Screen.dpf(ITEM_HEIGHT) - Screen.dpf(INDICATOR_HEIGHT)) / 2f + indicatorSpring.getValue() * Screen.dp(ITEM_HEIGHT);
        indicator.set(left, top, left + Screen.dpf(INDICATOR_WIDTH), top + Screen.dpf(INDICATOR_HEIGHT));
        float radius = indicator.height() / 2f;
        c.drawRoundRect(indicator, radius, radius, Paints.fillingPaint(color(XgramExpressive.SECONDARY_CONTAINER)));
      }
      super.dispatchDraw(c);
    }

    private int indexOfItem (int dest) {
      for (int i = 0; i < items.size(); i++) {
        if (items.get(i).dest == dest) {
          return i;
        }
      }
      return -1;
    }
  }

  /** The menu button (open: the expanded rail's, which closes it): a 24dp glyph in a 40dp state layer, 36dp from the start. */
  private final class MenuView extends StateView {
    private final boolean open;

    MenuView (Context context, boolean open) {
      super(context);
      this.open = open;
    }

    @Override
    protected int stateLayerColor () {
      return color(XgramExpressive.ON_SURFACE_VARIANT);
    }

    @Override
    protected void onDraw (Canvas c) {
      boolean rtl = Lang.rtl();
      float center = Screen.dpf(WIDTH / 2f);
      float cx = rtl ? getWidth() - center : center, cy = getHeight() / 2f;
      float r = Screen.dpf(MENU_STATE / 2f);
      setStateShape(cx - r, cy - r, cx + r, cy + r, r);
      drawState(c);
      drawMenuGlyph(c, cx, cy, open ? 1f : 0f, color(XgramExpressive.ON_SURFACE_VARIANT), rtl);
    }
  }

  /**
   * A destination of the collapsed rail: the 24dp icon centered in the 56 x 32dp indicator area (filled and
   * onSecondaryContainer when active, outlined and onSurfaceVariant otherwise), and its badge. No label. The rail draws
   * the indicator itself, so it can spring.
   */
  private final class ItemView extends StateView {
    final int dest;
    private final Drawable icon, activeIcon;
    private final RectF badgeRect = new RectF();
    private String label = "";
    private boolean active;
    private int badgeCount;
    private boolean badgeDot;
    @Nullable private String badge;
    private float badgeWidth;

    ItemView (Context context, int dest) {
      super(context);
      this.dest = dest;
      Resources res = context.getResources();
      this.icon = restIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]);
      this.activeIcon = activeIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]);
      setOnClickListener(v -> onDestinationClick(this.dest));
      if (dest == DEST_CHATS) {
        // The Chats tab's filters (Unread, Groups, Archive...) had only its slide-off menu, gone with the tabs: a long
        // press opens them (instead of the label's tooltip) while the list has no folders
        setOnLongClickListener(v -> {
          MainController list = owner.railChatList();
          return list != null && canNavigate() && list.xgramShowRailFilters();
        });
      }
      updateLabel();
    }

    void updateLabel () {
      String label = Lang.getString(DEST_LABEL[dest]);
      if (!label.equals(this.label)) {
        this.label = label;
        setLabel(this, label);
        setContentDescription(describe(dest, badgeCount, badgeDot));
      }
    }

    void setBadge (int count, boolean dot) {
      count = Math.max(0, count);
      dot = dot && count == 0;
      if (badgeCount != count || badgeDot != dot) {
        badgeCount = count;
        badgeDot = dot;
        badge = badgeText(count);
        badgeWidth = badge != null ? labelSmall.measureText(badge) : 0f;
        setContentDescription(describe(dest, count, dot));
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

    @Override
    protected int stateLayerColor () {
      // M3 state layer: onSecondaryContainer on the active indicator, onSurface on the other destinations
      return color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      float halfWidth = Screen.dpf(INDICATOR_WIDTH) / 2f, halfHeight = Screen.dpf(INDICATOR_HEIGHT) / 2f;
      setStateShape(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight, halfHeight);
      drawState(c);
      drawIcon(c, active ? activeIcon : icon, cx, cy, Screen.dpf(ICON), color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT), 1f);

      float iconStart = rtl ? cx + Screen.dpf(ICON / 2f) : cx - Screen.dpf(ICON / 2f);
      float iconTop = cy - Screen.dpf(ICON / 2f);
      if (badge != null) {
        float height = Screen.dpf(BADGE_LARGE);
        float width = Math.max(height, badgeWidth + Screen.dpf(BADGE_PADDING * 2f));
        float start = Screen.dpf(BADGE_LARGE_START);
        float left = rtl ? iconStart - start - width : iconStart + start;
        float top = iconTop + Screen.dpf(BADGE_LARGE_TOP);
        badgeRect.set(left, top, left + width, top + height);
        c.drawRoundRect(badgeRect, height / 2f, height / 2f, Paints.fillingPaint(color(XgramExpressive.ERROR)));
        labelSmall.setColor(color(XgramExpressive.ON_ERROR));
        c.drawText(badge, badgeRect.centerX() - badgeWidth / 2f, badgeRect.centerY() + labelSmallCenter, labelSmall);
      } else if (badgeDot) {
        // Only muted chats are unread: the small badge, in the muted tone
        float size = Screen.dpf(BADGE_SMALL);
        float start = Screen.dpf(BADGE_SMALL_START);
        float left = rtl ? iconStart - start - size : iconStart + start;
        c.drawCircle(left + size / 2f, iconTop + size / 2f, size / 2f, Paints.fillingPaint(color(XgramExpressive.OUTLINE)));
      }
    }
  }

  // Modal expanded rail

  private static final class Entry {
    final String key;
    final int id, filled, outlined, label;

    Entry (String key, int id, int filled, int outlined, int label) {
      this.key = key;
      this.id = id;
      this.filled = filled;
      this.outlined = outlined;
      this.label = label;
    }
  }

  /**
   * The modal expanded rail: the panel over the collapsed rail, from below the status bar to the bottom, with the
   * PopupLayout's scrim over everything else (and the status bar). The panel slides in from the start edge.
   */
  private final class ExpandedLayout extends FrameLayout implements PopupLayout.AnimatedPopupProvider, RootFrameLayout.MarginModifier {
    private final PanelView panel;
    private final LinearLayout list;
    private final XgramExpressive.Spring spring;
    @Nullable private PopupLayout popup;
    private float factor;
    private int panelWidth, panelTop, railLeft;
    private boolean closing, finished, shown;

    ExpandedLayout (Context context) {
      super(context);
      setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      setClipChildren(false);
      panel = new PanelView(context);
      ScrollView scroll = new ScrollView(context);
      scroll.setVerticalScrollBarEnabled(false);
      scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
      scroll.setClipToPadding(false);
      list = new LinearLayout(context);
      list.setOrientation(LinearLayout.VERTICAL);
      scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      panel.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addView(panel, new FrameLayout.LayoutParams(Screen.dp(EXPANDED_MAX), ViewGroup.LayoutParams.MATCH_PARENT));
      // SPATIAL_DEFAULT both ways; the panel moves with translation only
      spring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> setFactor(value)).setThreshold(.001f);
      buildRows(context);
    }

    @Override
    public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
      Views.setMargins(params, 0, 0, 0, 0); // placed over the rail itself, insets included
    }

    private LinearLayout.LayoutParams params (int width, int height, int topMargin, boolean startAligned) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
      params.topMargin = topMargin;
      if (startAligned) {
        params.gravity = Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT;
      }
      return params;
    }

    /**
     * The rows start past the rail's start inset (a cutout, a side navigation bar) and end above the navigation bar.
     * Both move on rotation, which this popup outlives: set again on every measure, applied only when they changed.
     */
    private void applyListPadding () {
      final boolean rtl = Lang.rtl();
      final int inset = rail.startInset;
      int bottom = 0;
      androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(rail);
      if (insets != null) {
        bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
      }
      int left = rtl ? 0 : inset, right = rtl ? inset : 0, paddingBottom = bottom + Screen.dp(16f);
      if (list.getPaddingLeft() != left || list.getPaddingRight() != right || list.getPaddingBottom() != paddingBottom || list.getPaddingTop() != 0) {
        list.setPadding(left, 0, right, paddingBottom);
      }
    }

    private void buildRows (Context context) {
      final Resources res = context.getResources();
      final int match = ViewGroup.LayoutParams.MATCH_PARENT;
      applyListPadding();

      // The menu button (closes), where the rail has it: the panel starts below the status bar
      MenuView menu = new MenuView(context, true);
      setLabel(menu, Lang.getString(R.string.XgramNavRailCloseMenu));
      menu.setOnClickListener(v -> close());
      list.addView(menu, params(Screen.dp(WIDTH), Screen.dp(BAND), 0, true));

      // Destinations: each row exactly over its collapsed one
      for (int i = 0; i < destinations.length; i++) {
        final int dest = destinations[i];
        String badge = dest == DEST_CHATS ? badgeText(unreadCount) : dest == DEST_CALLS ? badgeText(missedCount) : null;
        RowView row = new RowView(context, true, restIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]), activeIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]), Lang.getString(DEST_LABEL[dest]), badge);
        row.setContentDescription(describe(dest, dest == DEST_CHATS ? unreadCount : dest == DEST_CALLS ? missedCount : 0, dest == DEST_CHATS && unreadDot));
        row.setActive(dest == selected);
        row.setOnClickListener(v -> {
          markShown();
          onDestinationClick(dest);
          close();
        });
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), i == 0 ? Screen.dp(HEADER_GAP) : 0, false));
      }

      // Accounts
      list.addView(new SectionView(context, Lang.getString(R.string.XgramNavRailAccounts)), params(match, Screen.dp(SECTION_HEIGHT), Screen.dp(SECTION_GAP), false));
      ArrayList<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
      for (final TdlibAccount account : accounts) {
        boolean current = account.id == account.context().preferredAccountId();
        TdlibBadgeCounter badge = account.getUnreadBadge();
        int count = badge != null ? badge.getCount() : 0;
        RowView row = new RowView(context, account, !current && count > 0 ? Strings.buildCounter(count) : null);
        row.setActive(current);
        row.setOnClickListener(v -> {
          markShown();
          if (account.id != account.context().preferredAccountId()) {
            account.context().changePreferredAccountId(account.id, TdlibManager.SWITCH_REASON_USER_CLICK);
          }
          close();
        });
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), 0, false));
      }
      list.addView(actionRow(context, new Entry(null, R.id.btn_addAccount, R.drawable.baseline_add_24, R.drawable.xgl_add, R.string.XgramNavRailAddAccount)), params(match, Screen.dp(ROW_HEIGHT), 0, false));

      // The rest of the side menu, in its order (destinations are above), then its switches
      XgramSettings xgram = XgramSettings.instance();
      ArrayList<Entry> entries = new ArrayList<>();
      if (!xgram.drawerItemHidden("hub")) {
        entries.add(new Entry("hub", R.id.btn_xgramHub, R.drawable.baseline_stars_24, R.drawable.xgl_stars, R.string.XgramHub));
      }
      if (!xgram.drawerItemHidden("bookmarks")) {
        entries.add(new Entry("bookmarks", R.id.btn_xgramBookmarks, R.drawable.baseline_star_24, R.drawable.xgl_star, R.string.XgramBookmarks));
      }
      if (!xgram.drawerItemHidden("saved_by_chat")) {
        entries.add(new Entry("saved_by_chat", R.id.btn_xgramSavedByChat, R.drawable.baseline_forum_24, R.drawable.xgl_forum, R.string.XgramSavedByChat));
      }
      if (!xgram.drawerItemHidden("invite")) {
        entries.add(new Entry("invite", R.id.btn_invite, R.drawable.baseline_person_add_24, R.drawable.xgl_person_add, R.string.XgramNavRailInviteFriends));
      }
      if (Settings.instance().getAvailableProxyCount() > 0 && !xgram.drawerItemHidden("proxy")) {
        entries.add(new Entry("proxy", R.id.btn_proxy, R.drawable.baseline_security_24, R.drawable.xgl_security, R.string.Proxy));
      }
      if (!xgram.drawerItemHidden("help")) {
        entries.add(new Entry("help", R.id.btn_help, R.drawable.baseline_help_24, R.drawable.xgl_help, R.string.Help));
      }
      XgramReorder.sort(XgramReorder.DRAWER, entries, entry -> entry.key);
      ArrayList<Entry> toggles = new ArrayList<>();
      if (!xgram.drawerItemHidden("night")) {
        toggles.add(new Entry("night", R.id.btn_night, R.drawable.baseline_brightness_2_24, R.drawable.xgl_night, R.string.XgramNavRailNightMode));
      }
      // Expressive design, between Night mode and Ghost mode as in the side menu (Android 13+)
      if (XgramExpressive.isAvailable() && !xgram.drawerItemHidden("expressive")) {
        toggles.add(new Entry("expressive", R.id.btn_xgramExpressive, R.drawable.baseline_palette_24, R.drawable.xgl_palette, R.string.XgramExpressive));
      }
      if (!xgram.drawerItemHidden("ghost")) {
        toggles.add(new Entry("ghost", R.id.btn_xgramGhostMode, R.drawable.baseline_ghost_24, R.drawable.xgl_ghost, R.string.XgramGhostMode));
      }
      XgramReorder.sort(XgramReorder.DRAWER, toggles, entry -> entry.key);
      if (!entries.isEmpty() || !toggles.isEmpty()) {
        list.addView(new DividerView(context), params(match, Screen.dp(DIVIDER_HEIGHT), Screen.dp(SECTION_GAP), false));
      }
      for (Entry entry : entries) {
        View row;
        if (entry.id == R.id.btn_proxy) {
          row = switchRow(context, entry, Settings.instance().getEffectiveProxyId() != Settings.PROXY_ID_NONE);
        } else {
          row = actionRow(context, entry);
        }
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), 0, false));
      }
      for (Entry entry : toggles) {
        boolean checked = entry.id == R.id.btn_night ? Theme.isDark() : entry.id == R.id.btn_xgramExpressive ? XgramExpressive.isEnabled() : xgram.isAnyGhostEnabled();
        list.addView(switchRow(context, entry, checked), params(match, Screen.dp(ROW_HEIGHT), 0, false));
      }
    }

    /** A side menu entry that opens something (and closes the rail). */
    private RowView actionRow (Context context, Entry entry) {
      Resources res = context.getResources();
      RowView row = new RowView(context, false, restIcon(res, entry.filled, entry.outlined), null, Lang.getString(entry.label), null);
      final int id = entry.id;
      row.setOnClickListener(v -> {
        markShown();
        onMenuAction(id);
        close();
      });
      return row;
    }

    /**
     * A side menu switch: the row toggles it (Night mode, Ghost mode; a long press on Ghost mode opens its settings),
     * except Proxy, whose row opens the proxy settings and whose switch turns the proxy on or off. The rail stays open.
     */
    private View switchRow (Context context, Entry entry, boolean checked) {
      Resources res = context.getResources();
      final boolean rtl = Lang.rtl();
      final int id = entry.id;
      FrameLayout wrap = new FrameLayout(context);
      RowView row = new RowView(context, false, restIcon(res, entry.filled, entry.outlined), null, Lang.getString(entry.label), null);
      row.setTrailingReserve(Screen.dp(SWITCH_RESERVE));
      if (id != R.id.btn_proxy) {
        // The whole row is the switch (below): the row's state layer shows its press, hover and keyboard focus, in the
        // row's pill instead of the framework's focus rectangle. Set before it is added, so the row follows the state.
        row.setDuplicateParentStateEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          wrap.setDefaultFocusHighlightEnabled(false);
        }
      }
      wrap.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      final TogglerView toggler = new TogglerView(context);
      toggler.init(checked);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Screen.dp(74f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT));
      if (rtl) {
        params.leftMargin = Screen.dp(SWITCH_END);
      } else {
        params.rightMargin = Screen.dp(SWITCH_END);
      }
      wrap.addView(toggler, params);
      if (id == R.id.btn_proxy) {
        row.setOnClickListener(v -> {
          markShown();
          onMenuAction(id);
          close();
        });
        toggler.setOnClickListener(v -> toggler.setRadioEnabled(Settings.instance().toggleProxySetting(Settings.PROXY_FLAG_ENABLED), true));
        toggler.setContentDescription(Lang.getString(entry.label));
      } else {
        // The whole row is the switch: the press reaches the row and the switch (ViewGroup#dispatchSetPressed)
        row.setClickable(false);
        row.setFocusable(false);
        toggler.setClickable(false);
        wrap.setClickable(true);
        wrap.setFocusable(true);
        wrap.setContentDescription(Lang.getString(entry.label));
        wrap.setOnClickListener(v -> {
          if (id == R.id.btn_night) {
            toggler.setRadioEnabled(!toggler.isEnabled(), true);
            ThemeManager.instance().toggleNightMode();
          } else if (id == R.id.btn_xgramExpressive) {
            // The screen under the rail (it has a Tdlib: rebuilding the Material You themes needs one). The switch
            // flips only once the restart is confirmed, as in the side menu and Appearance.
            final NavigationController navigation = XgramNavRail.this.context.navigation(); // the activity (the parameter is a plain Context)
            final ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
            final ViewController<?> c = current != null && current.tdlib() != null ? current : owner.railRoot();
            if (c == null) {
              return;
            }
            final boolean on = XgramExpressive.isEnabled();
            c.openAlert(R.string.XgramExpressive, Lang.getString(R.string.XgramExpressiveInfo), Lang.getString(R.string.AppUpdateRestart), (dialog, which) -> {
              XgramExpressive.setEnabled(c, !on);
              toggler.setRadioEnabled(!on, true);
            }, 0);
          } else {
            XgramSettings settings = XgramSettings.instance();
            boolean enable = !settings.isAnyGhostEnabled();
            settings.setGhostModeEnabled(enable);
            com.fahadali.xgram.ghost.XgramGhost.applyOnlineState();
            toggler.setRadioEnabled(enable, true);
          }
        });
        if (id == R.id.btn_xgramGhostMode) {
          wrap.setOnLongClickListener(v -> {
            markShown();
            onMenuAction(id);
            close();
            return true;
          });
        }
      }
      return wrap;
    }

    void close () {
      if (popup != null) {
        popup.hideWindow(true);
      }
    }

    private void markShown () {
      if (!shown && popup != null) {
        shown = true;
        popup.onCustomShowComplete(); // screens open from here at once: the window no longer counts as animating
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      // Where the rail is, seen from this window (edge to edge or not): the panel lies exactly over it, below the
      // status bar. Views.getLocationOnScreen hands out one shared array: copy each value before the next call.
      int[] location = Views.getLocationOnScreen(this);
      int ownX = location[0], ownY = location[1];
      location = Views.getLocationOnScreen(rail);
      int railX = location[0], railY = location[1];
      railLeft = railX - ownX;
      panelTop = Math.max(0, Math.min(height, railY - ownY + HeaderView.getTopOffset()));
      int inset = rail.startInset;
      int content = Math.max(Screen.dp(EXPANDED_MIN), Math.min(Screen.dp(EXPANDED_MAX), width - inset - Screen.dp(EXPANDED_SCRIM_MIN)));
      panelWidth = Math.min(width, content + inset);
      applyListPadding(); // after a rotation the cutout and the navigation bar may be elsewhere
      measureExactly(panel, panelWidth, height - panelTop);
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int left = Lang.rtl() ? railLeft + rail.getWidth() - panelWidth : railLeft;
      panel.layout(left, panelTop, left + panelWidth, b - t);
      applyFactor();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      // The scrim: a touch anywhere off the panel closes the rail, even while it is still opening
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !closing) {
        float x = e.getX(), y = e.getY();
        float left = panel.getLeft() + panel.getTranslationX();
        boolean onPanel = x >= left && x < left + panel.getWidth() && y >= panel.getTop() && y < panel.getBottom();
        if (!onPanel) {
          close();
        }
      }
      return true;
    }

    private void applyFactor () {
      float offset = (panelWidth + Screen.dpf(EXPANDED_CORNER)) * (1f - factor);
      panel.setTranslationX(Lang.rtl() ? offset : -offset);
    }

    private void setFactor (float value) {
      if (finished) {
        return;
      }
      factor = value;
      applyFactor();
      if (popup != null) {
        popup.setRevealFactor(MathUtils.clamp(value)); // the M3 scrim: black at 32%
      }
      if (!closing && value >= .9f) {
        markShown();
      }
      if (closing && value <= 0f) {
        finishHide();
      }
    }

    private void onSpringEnd () {
      if (closing) {
        finishHide();
      } else if (popup != null && !finished) {
        popup.setRevealFactor(1f);
        markShown();
      }
    }

    private void finishHide () {
      if (!finished) {
        finished = true;
        if (popup != null) {
          popup.onCustomHideAnimationComplete();
        }
      }
    }

    @Override
    public void prepareShowAnimation () {
      factor = 0f;
      applyFactor();
    }

    @Override
    public void launchShowAnimation (PopupLayout popup) {
      this.popup = popup;
      spring.setEndListener(this::onSpringEnd);
      spring.animateTo(1f);
    }

    @Override
    public boolean launchHideAnimation (PopupLayout popup, me.vkryl.android.animator.FactorAnimator originalAnimator) {
      this.popup = popup;
      closing = true;
      spring.setEndListener(this::onSpringEnd);
      if (!spring.isRunning() && spring.getValue() <= 0f) {
        finishHide(); // never opened: nothing would move, and the window must still go
      } else {
        spring.animateTo(0f);
      }
      return true;
    }
  }

  /** The expanded rail's container: surfaceContainer, 16dp end corners, level 2 shadow. */
  private final class PanelView extends FrameLayout {
    private final RectF rect = new RectF();

    PanelView (Context context) {
      super(context);
      setWillNotDraw(false);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          int radius = Screen.dp(EXPANDED_CORNER);
          int cover = Screen.dp(OVERSHOOT_COVER);
          if (Lang.rtl()) {
            outline.setRoundRect(0, 0, view.getWidth() + cover, view.getHeight(), radius);
          } else {
            outline.setRoundRect(-cover, 0, view.getWidth(), view.getHeight(), radius);
          }
        }
      });
    }

    @Override
    protected void onDraw (Canvas c) {
      float radius = Screen.dpf(EXPANDED_CORNER), cover = Screen.dpf(OVERSHOOT_COVER);
      if (Lang.rtl()) {
        rect.set(0, 0, getWidth() + cover, getHeight());
      } else {
        rect.set(-cover, 0, getWidth(), getHeight());
      }
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color(XgramExpressive.SURFACE_CONTAINER)));
    }
  }

  /**
   * A row of the expanded rail, 56dp: the 24dp icon (or a 32dp avatar) 16dp into the indicator, the labelLarge label
   * 8dp after it, an optional trailing count. A destination's indicator hugs its content; other rows fill the width.
   * The active one (the chat list's page, the current account) is secondaryContainer with onSecondaryContainer.
   */
  private final class RowView extends StateView implements Destroyable {
    private final boolean destination;
    @Nullable private final Drawable icon, activeIcon;
    @Nullable private final AvatarReceiver avatar;
    private final String label;
    @Nullable private final String badge;
    private final float badgeWidth;
    private final RectF rect = new RectF();
    private String drawnLabel = "";
    private float drawnLabelWidth;
    private boolean active;
    private int trailingReserve;

    RowView (Context context, boolean destination, @Nullable Drawable icon, @Nullable Drawable activeIcon, String label, @Nullable String badge) {
      super(context);
      this.destination = destination;
      this.icon = icon;
      this.activeIcon = activeIcon;
      this.avatar = null;
      this.label = label;
      this.badge = badge;
      this.badgeWidth = badge != null ? labelLarge.measureText(badge) : 0f;
      setContentDescription(label);
    }

    RowView (Context context, TdlibAccount account, @Nullable String badge) {
      super(context);
      this.destination = false;
      this.icon = null;
      this.activeIcon = null;
      this.label = Lang.getDebugString(account.getName(), account.isDebug());
      this.badge = badge;
      this.badgeWidth = badge != null ? labelLarge.measureText(badge) : 0f;
      this.avatar = new AvatarReceiver(this);
      this.avatar.requestAccount(null, account.id, AvatarReceiver.Options.NONE);
      setContentDescription(label);
    }

    void setActive (boolean active) {
      if (this.active != active) {
        this.active = active;
        setSelected(active);
        invalidate();
      }
    }

    void setTrailingReserve (int px) {
      trailingReserve = px;
      layoutLabel(getWidth());
    }

    private float textStart () {
      return Screen.dpf(avatar != null ? AVATAR_TEXT_START : TEXT_START);
    }

    private void layoutLabel (int width) {
      if (width <= 0) {
        return;
      }
      float available = width - textStart() - Screen.dpf(ROW_PADDING + ROW_TRAILING) - trailingReserve - (badge != null ? badgeWidth + Screen.dpf(ROW_ICON_LABEL) : 0f);
      drawnLabel = available > 0 ? TextUtils.ellipsize(label, labelLarge, available, TextUtils.TruncateAt.END).toString() : "";
      drawnLabelWidth = labelLarge.measureText(drawnLabel);
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      layoutLabel(w);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      if (avatar != null) {
        avatar.attach();
      }
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      if (avatar != null) {
        avatar.detach();
      }
    }

    @Override
    public void performDestroy () {
      if (avatar != null) {
        avatar.destroy();
      }
    }

    @Override
    protected int stateLayerColor () {
      return color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE); // M3 state layer, as the collapsed rail
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      final int width = getWidth(), height = getHeight();
      final float padding = Screen.dpf(ROW_PADDING);
      float end = destination ? textStart() + drawnLabelWidth + (badge != null ? Screen.dpf(ROW_ICON_LABEL) + badgeWidth : 0f) + Screen.dpf(ROW_TRAILING) : width - padding;
      end = Math.min(end, width - padding);
      rect.set(rtl ? width - end : padding, 0, rtl ? width - padding : end, height);
      float radius = height / 2f;
      if (active) {
        c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color(XgramExpressive.SECONDARY_CONTAINER)));
      }
      setStateShape(rect.left, rect.top, rect.right, rect.bottom, radius);
      drawState(c);

      float iconCenter = padding + Screen.dpf(ROW_LEADING + ICON / 2f);
      float cx = rtl ? width - iconCenter : iconCenter, cy = height / 2f;
      int content = color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
      if (avatar != null) {
        int size = Screen.dp(AVATAR);
        int left = Math.round(cx - size / 2f), top = Math.round(cy - size / 2f);
        avatar.setBounds(left, top, left + size, top + size);
        if (avatar.needPlaceholder()) {
          avatar.drawPlaceholder(c);
        }
        avatar.draw(c);
      } else {
        drawIcon(c, active && activeIcon != null ? activeIcon : icon, cx, cy, Screen.dpf(ICON), content, 1f);
      }
      labelLarge.setColor(content);
      float textStart = textStart();
      float x = rtl ? width - textStart - drawnLabelWidth : textStart;
      c.drawText(drawnLabel, x, cy + labelLargeCenter, labelLarge);
      if (badge != null) {
        labelLarge.setColor(color(destination ? content : XgramExpressive.ON_SURFACE_VARIANT));
        float badgeStart = destination ? textStart + drawnLabelWidth + Screen.dpf(ROW_ICON_LABEL) : width - padding - Screen.dpf(ROW_TRAILING) - badgeWidth;
        c.drawText(badge, rtl ? width - badgeStart - badgeWidth : badgeStart, cy + labelLargeCenter, labelLarge);
      }
    }
  }

  /** A section label of the expanded rail: titleSmall in onSurfaceVariant, where the labels' icons start. */
  private final class SectionView extends View {
    private final String text;

    SectionView (Context context, String text) {
      super(context);
      this.text = text;
    }

    @Override
    protected void onDraw (Canvas c) {
      titleSmall.setColor(color(XgramExpressive.ON_SURFACE_VARIANT));
      float start = Screen.dpf(ROW_PADDING + ROW_LEADING);
      float width = titleSmall.measureText(text);
      c.drawText(text, Lang.rtl() ? getWidth() - start - width : start, getHeight() / 2f + titleSmallCenter, titleSmall);
    }
  }

  /** The M3 divider between the accounts and the rest of the menu: 1dp outlineVariant, inset like the labels. */
  private static final class DividerView extends View {
    DividerView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      float inset = Screen.dpf(ROW_PADDING + ROW_LEADING);
      float thickness = Math.max(1f, Screen.dpf(1f));
      float top = (getHeight() - thickness) / 2f;
      c.drawRect(inset, top, getWidth() - inset, top + thickness, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT)));
    }
  }
}
