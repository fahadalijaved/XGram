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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramReorder;
import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramAmoled;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconCrossfade;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramNavMotion;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallListController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.PeopleController;
import org.thunderdog.challegram.ui.ProfileController;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import tgx.td.ChatId;

/**
 * Material 3 Expressive floating toolbar used for navigation on narrow windows (a phone, a Fold's cover screen, a
 * low window in landscape), as in Google Photos and Essentials (Chat list > Navigation > Navigation bar, Expressive
 * design only; the classic look keeps {@link XgramNavBar}, wide windows the rail).
 * <ul>
 *   <li>A 64dp full-corner pill with 8dp of padding holds 2 to 4 tabs (Chats, Contacts and Settings unless chosen
 *   otherwise). The selected tab is a 48dp inner pill with its filled icon and label (the icon 24dp in from its start,
 *   so the inner pill is concentric with the container); the others show their outlined icon. A tab change is a spring
 *   morph: the widths move together (they always add up to the pill's content), the inner pill's leading edge runs
 *   ahead of its tail, the icon crossfades (XgramIconCrossfade) and pops, the label is wiped in.</li>
 *   <li>A 56dp profile button (the account's photo) 8dp after the pill: a circle at rest, a squircle while the own
 *   profile is on screen. Long press: switch account. A swipe up or down on the photo (the button's or the profile
 *   tab's) switches to the next or previous account, as in Google's apps (XgramAccountSwipe).</li>
 *   <li>Contacts, Settings, the profile (and Calls with folders) are tab screens: opened by the toolbar right above the
 *   chat list (a short fade-through), no back arrow, Back fades to the chat list. One live instance of each is kept
 *   warm and shown again as it was (no rebuild, its scroll position). The toolbar lives above the navigation (in the
 *   activity's content layout, like the rail), stays on screen for a tab change, leaves with its tab screen when a
 *   screen is pushed (shared axis) and sits under a predictive back card like the live app bar.</li>
 *   <li>A page-colour band behind it fades the rows out under the bar; it takes the touches of its solid part.</li>
 *   <li>Scrolling down slides it away, or (Navigation bar > While scrolling: Shrink) shrinks it to its icons on one
 *   spring: 56dp tall with 40dp items, every tab 48dp wide, the selected label wiped out and its badge's count back,
 *   the button scaled to 48dp, the band gone (the pill floats with its shadow). The lists keep the full bar's room.
 *   Or (Collapse) it folds into the selected tab at the start edge: a 56dp pill with its icon and label, the other tabs
 *   and the button fade out and shrink, a dot at its corner tells of a badge folded away; a tap on it (or a page drag)
 *   only brings the bar back. The settings page's preview plays a new choice once (compact, held, back in full).</li>
 * </ul>
 * {@link XgramTwoPane} owns one per activity and decides when it shows (one pane, no rail, the setting on). Values are
 * the M3E floating toolbar tokens (FloatingToolbarTokens: 64dp, 8dp padding, 48dp items, ToolbarToFabGap 8dp,
 * ScreenOffset 16dp), BadgeTokens and the elevation level 3.
 */
public final class XgramNavToolbar implements NavigationController.XgramBarListener, XgramNavMotion.CardHoleListener {
  // Geometry (dp)

  /** From the pill to what is under it (the navigation bar), and the smallest margin from the window's sides. */
  private static final float SCREEN_OFFSET = 16f;
  /** The pill and its padding; its items are as tall as what the padding leaves (48dp), concentric with it. */
  private static final float HEIGHT = 64f, PAD = 8f;
  /**
   * Shrunk to its icons while scrolling (Navigation bar > While scrolling): 56dp, the padding kept, so the items are
   * 40dp (concentric), every tab at most U_COMPACT wide. Its bottom stays where it was.
   */
  private static final float HEIGHT_COMPACT = 56f;
  /** The width of an unselected tab, regular and compact. */
  private static final float U_REGULAR = 56f, U_COMPACT = 48f;
  /** The selected tab: lead, the 24dp icon, the gap, the label, trail. Dense: labelMedium, smaller gap and trail. */
  private static final float LEAD = 12f, ICON = 24f, GAP_REGULAR = 8f, TRAIL_REGULAR = 16f, GAP_DENSE = 4f, TRAIL_DENSE = 12f;
  /** Narrower labels than this are not worth showing: icons only. */
  private static final float MIN_LABEL = 24f, ICON_ONLY_S = 64f, ICON_ONLY_MIN_U = 44f;
  private static final float BUTTON = 56f, BUTTON_GAP = 8f, AVATAR = 40f, TAB_AVATAR = 24f, BUTTON_HIT_SLOP = 4f;
  /** The profile button beside the shrunk bar: scaled to this (its photo with it, about 34dp). */
  private static final float BUTTON_COMPACT = 48f;
  /**
   * Collapsed to one tab: what folds away (the other tabs, their badges, the button) fades out this many times faster
   * than the bar folds (gone by about .29) and shrinks by FOLD_SCALE (the button to BUTTON_FOLDED); the dot for a badge
   * folded away fades in from FOLDED_BADGE_FROM.
   */
  private static final float FOLD_FADE = 3.4f, FOLD_SCALE = .25f, BUTTON_FOLDED = .6f, FOLDED_BADGE_FROM = .6f;
  /** The settings page's preview shows Shrink or Collapse once when chosen: compact for this long, then back in full. */
  private static final long PREVIEW_DEMO_HOLD = 1200L;
  private static final float BUTTON_SELECTED_CORNER = XgramExpressive.CORNER_L, PRESSED_CORNER = XgramExpressive.CORNER_M;
  /** Above the pill: the band fades in over this much (and the level 3 shadow has room). */
  private static final float BAND_ABOVE = 16f;
  private static final float GROUP_MAX = 480f;
  private static final float POP_FROM = .84f, AVATAR_PRESSED_SCALE = .94f;
  /** A swipe on the photo switches accounts past this much travel (or thrown). */
  private static final float SWIPE_COMMIT = 24f;
  /** BadgeTokens, as on the rail: a 6dp dot, or 16dp tall around labelSmall, at the icon's top end corner. */
  private static final float BADGE_SMALL = 6f, BADGE_SMALL_START = 18f, BADGE_LARGE = 16f, BADGE_PADDING = 4f, BADGE_LARGE_START = 12f, BADGE_LARGE_TOP = -2f;
  /** The band: alpha at the pill's centre line; it is solid from the pill's bottom down. */
  private static final float BAND_MID_ALPHA = .9f;

  private static final int STYLE_R1 = 0, STYLE_R2 = 1, STYLE_DENSE = 2;

  // Destinations (the rail's, and the profile)

  public static final int DEST_CHATS = XgramNavRail.DEST_CHATS, DEST_CALLS = XgramNavRail.DEST_CALLS,
    DEST_CONTACTS = XgramNavRail.DEST_CONTACTS, DEST_SAVED = XgramNavRail.DEST_SAVED, DEST_SETTINGS = XgramNavRail.DEST_SETTINGS,
    DEST_PROFILE = 5, DEST_ACTIVITY = XgramNavRail.DEST_ACTIVITY;
  /** Keys by destination, as stored in the tab order (XgramReorder.NAV_BAR). Activity (6) is a tab only while it is on. */
  public static final String[] TAB_KEYS = {"chats", "calls", "contacts", "saved", "settings", "profile", "activity"};
  /**
   * The tabs' order before the user drags one (XgramReorder.NAV_BAR): Chats, Activity, Settings and the profile, the
   * default tabs; the others keep their places among them.
   */
  public static final String[] DEFAULT_ORDER = {"chats", "activity", "calls", "contacts", "saved", "settings", "profile"};
  static final int MIN_TABS = 2, MAX_TABS = 4;

  /** Why the toolbar steps aside (a bit each); scrolling down hides it too (or shrinks it: it steps aside shrunk). */
  public static final int FORCE_SEARCH = 1, FORCE_SELECT = 2, FORCE_FOLDER_MENU = 4, FORCE_FAB_MENU = 8, FORCE_IME = 16, FORCE_TRANSFORM = 32;
  /** The bits the chat list sets (setForceHidden); the others are the toolbar's own. */
  private static final int FORCE_OWN = FORCE_IME | FORCE_TRANSFORM;

  /** Labels: short ones ("Saved"); the full name stays the description. */
  private static final int[] DEST_TAB_LABEL = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramBottomNavSaved, R.string.Settings, R.string.XgramNavProfileLabel, R.string.XgramActivity};
  /** Row titles of the settings page. */
  private static final int[] DEST_TITLE = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramNavRailSavedMessages, R.string.Settings, R.string.XgramNavProfileLabel, R.string.XgramActivity};

  /** A destination that can be a tab now: Activity only while it is on (its switch, Activity and reminders). */
  private static boolean isAvailable (int dest) {
    return dest != DEST_ACTIVITY || com.fahadali.xgram.activity.XgramActivity.isOn();
  }

  /** Option ids of the account menu: this plus the account's index. */
  private static final int ACCOUNT_OPTION_ID = 7000;

  private final BaseActivity context;
  @Nullable private final XgramTwoPane owner;
  @Nullable private final NavigationController navigation;
  @Nullable private final ViewGroup container;
  /**
   * The settings page's live preview: no navigation, no badges, never hides, a tap only moves the selection (and brings
   * back a bar its demo of Shrink or Collapse made compact).
   */
  private final boolean preview;

  private final TextPaint labelLarge, labelMedium, badgePaint;
  private final float labelLargeCenter, labelMediumCenter, badgeCenter;

  private final HostView host;
  private final PillView pill;
  private final ProfileView profile;
  @Nullable private final XgramNavBadges badges;

  private Tab[] tabs = new Tab[0];
  private int[] dests = new int[0];
  private boolean buttonMode, vibrant;
  private int iconPack = -1;

  // Selection: the destination on screen; its index in the pill (-1: none, the profile button is selected)
  private int selectedDest = DEST_CHATS;
  private int selectedIndex = -1;

  // Fit (5.3): worked out when the width, the tabs, the style or the language change, never per frame
  private boolean fitDirty = true, fitAnimate;
  private int fitWidth = -1;
  private int fitStyle = STYLE_R1;
  private boolean iconOnly;
  private float fitW, fitGroupW, fitGroupLeft;
  /** Shrunk to its icons: every tab's width and the pill's (the padding and the tabs). */
  private float fitUC, fitWC;
  private final XgramExpressive.Spring widthSpring, leftSpring;
  private int notifiedEndInset = Integer.MIN_VALUE, notifiedCompactEndInset = Integer.MIN_VALUE;

  // State
  private boolean shown, destroyed;
  /** Not a tab screen on top: the toolbar is gone (alpha 0, invisible). */
  private boolean away;
  @Nullable private MainController root;
  @Nullable private Tdlib tdlib;
  private int inset;
  /** The compose button sits on the toolbar's row (a wide, low window): no band. Set by the chat list. */
  private boolean inline;
  private float followAlpha = 1f, followTranslation;

  // Hiding (8.5): 0 = on screen, 1 = slid below the window
  private final XgramExpressive.Spring hideSpring;
  /**
   * The chat list's compose button at the side follows the bar's scroll hiding only (Hide): 0 = on screen, 1 = slid
   * away, in step with hideSpring. Not its forced steps aside (search, selection, the FAB's own menu, the keyboard).
   */
  private final XgramExpressive.Spring scrollAwaySpring;
  private boolean scrollHidden;
  private int forceMask;
  // What scrolling down does (XgramSettings.NAV_SCROLL_*): Hide slides it away (hideSpring); Shrink and Collapse make
  // it compact, 0 = full, 1 = compact (laid out and drawn from it every frame, no layout pass). The kind is the mode it
  // started compacting in: it expands the same way
  private final XgramExpressive.Spring compactSpring;
  private int scrollMode = XgramSettings.NAV_SCROLL_HIDE;
  private int compactKind = XgramSettings.NAV_SCROLL_SHRINK;
  // The settings page's preview (never hides): a new choice for scrolling plays once, compact, held, back in full. The
  // choice waiting for the other compact one to come back in full first (NAV_SCROLL_HIDE: none)
  private int previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
  private final Runnable previewDemoEnd = this::endPreviewDemo;
  /** A tab screen on top searches (its search opened): set from ViewController's search hooks. */
  private boolean transformSearching;
  @Nullable private RecyclerView scrollSource;
  private boolean ignorePagerScroll, fromDrawer;

  // A swipe on the photo switched accounts away from this section (not Chats): opened again on that account (-1: none)
  private int swipeSectionDest = -1;
  private int swipeSectionAccountId = TdlibAccount.NO_ID;
  // The account a swipe on the photo switches to: in the switch's fade its new chat list counts as a tab root, so the
  // toolbar stays (the band crossfades) instead of fading out with the old screen and popping back at the bind
  private int swipeSwitchAccountId = TdlibAccount.NO_ID;

  // The side menu button's badge (MainActivity#updateCounter): another account's unread chats, or a problem to
  // resolve. While the side menu is hidden there is no menu button: the photo shows it (in the colour it had while it
  // fades out).
  @ColorId private int menuBadgeColorId = ColorId.NONE, menuBadgeDrawColorId = ColorId.NONE;
  private final XgramExpressive.Spring menuBadgeSpring;

  // Following a screen transition (9)
  private boolean transitionActive, tabL, tabR, tForward;
  private int tKind;
  @Nullable private ViewController<?> tLeft, tRight;
  private float holeOffsetX, holeOffsetY;
  private final Path holePath = new Path();
  private final RectF holeRect = new RectF();
  @Nullable private ViewController<?> lastSettledTop;

  private final Runnable settleRunnable = this::settle;
  private final Runnable layoutNotify = this::notifyRootLayout;

  // Warm tab screens (by destination): one live instance each of Calls, Contacts, Settings and the profile, kept when it
  // leaves the screen (another tab, Back to the chat list) and shown again as it was: no rebuild, no wait for its data
  // (its own listeners keep it current meanwhile), its scroll position. The stack skips their destroy()
  // (ViewController#xgramKeepsWarm); the toolbar lets go of them (destroying those off the stack) on an account or
  // chat list change, when their tab goes, when the window changes size and when it hides or goes itself.
  private final ViewController<?>[] warm = new ViewController<?>[TAB_KEYS.length];
  private final Runnable dropDetachedRunnable = this::dropDetachedWarm;
  // Settings and the profile read these once, when their view is made: a warm one would keep showing the old state
  private final Runnable staleWarmRunnable = this::dropWarmForContent;
  private final XgramSettings.Listener contentSettingsListener = key -> {
    if (XgramSettings.KEY_SHOW_PEER_IDS.equals(key) || XgramSettings.KEY_HIDE_PHONE.equals(key)) {
      UI.cancel(staleWarmRunnable);
      UI.post(staleWarmRunnable); // never inside XgramSettings' listener loop
    }
  };

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

  private final XgramNavBadges.Callback badgeCallback = new XgramNavBadges.Callback() {
    @Override
    public void onBadges (int unread, boolean dot, int missed) {
      Tab chats = tabOf(DEST_CHATS);
      if (chats != null) {
        chats.setBadge(unread, dot);
      }
      Tab calls = tabOf(DEST_CALLS);
      if (calls != null) {
        calls.setBadge(missed, false);
      }
    }

    @Override
    public boolean isCallListOnScreen () {
      if (navigation == null) {
        return false;
      }
      ViewController<?> top = navigation.getCurrentStackItem();
      return top instanceof CallListController || (top != null && top == root && root.xgramOnCallsPage());
    }

    @Override
    public boolean needsMissedCalls () {
      return hasTab(DEST_CALLS);
    }
  };

  // Activity's tab: its count of new items (XgramActivityBadge: 0 while Unread badge is off)
  private final com.fahadali.xgram.activity.XgramActivityBadge.Listener activityBadgeListener = this::updateActivityBadge;

  private void updateActivityBadge () {
    if (destroyed || preview) {
      return;
    }
    Tab tab = tabOf(DEST_ACTIVITY);
    if (tab != null) {
      tab.setBadge(com.fahadali.xgram.activity.XgramActivityBadge.badgeCount(tdlib), false);
    }
  }

  /** Any spring of a tab: the pill lays its tabs out again before the next frame is drawn (once per frame). */
  private final XgramExpressive.Spring.Listener frameListener = new XgramExpressive.Spring.Listener() {
    @Override
    public void onSpringUpdate (XgramExpressive.Spring s, float value) {
      onTabsMoved();
    }
  };

  /**
   * A tab's spring moved (or snapped): the pill lays its tabs out again. Folded (Collapse), the pill is as wide as the
   * selected tab: it is placed again too.
   */
  private void onTabsMoved () {
    if (compactKind == XgramSettings.NAV_SCROLL_COLLAPSE && compactSpring.getValue() != 0f) {
      layoutGroup(); // marks the pill dirty
    } else {
      pill.markDirty();
    }
  }

  public XgramNavToolbar (BaseActivity context, XgramTwoPane owner, NavigationController navigation, ViewGroup container) {
    this(context, owner, navigation, container, false);
  }

  private XgramNavToolbar (BaseActivity context, @Nullable XgramTwoPane owner, @Nullable NavigationController navigation, @Nullable ViewGroup container, boolean preview) {
    this.context = context;
    this.owner = owner;
    this.navigation = navigation;
    this.container = container;
    this.preview = preview;
    this.labelLarge = XgramNavRail.newPaint(XgramExpressive.TS_LABEL_L);
    this.labelMedium = XgramNavRail.newPaint(XgramExpressive.TS_LABEL_M);
    this.badgePaint = XgramNavRail.newPaint(XgramExpressive.TS_LABEL_S);
    this.labelLargeCenter = XgramNavRail.centerOffset(labelLarge);
    this.labelMediumCenter = XgramNavRail.centerOffset(labelMedium);
    this.badgeCenter = XgramNavRail.centerOffset(badgePaint);
    this.host = new HostView(context);
    this.pill = new PillView(context);
    this.profile = new ProfileView(context);
    this.host.addView(pill);
    this.host.addView(profile);
    this.widthSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> layoutGroup()).setThreshold(.5f);
    this.leftSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> layoutGroup()).setThreshold(.5f);
    this.hideSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyHide()).setThreshold(.002f);
    this.hideSpring.setEndListener(this::applyHide);
    this.scrollAwaySpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> notifyFabFollow()).setThreshold(.002f);
    this.menuBadgeSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidateMenuBadge()).setThreshold(.002f);
    // The refit springs' token: shrinking feels like a change of the tabs (both ways; Reduce motion snaps it)
    this.compactSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> {
      layoutGroup();
      notifyFabFollow(); // the compose button at the side moves with the compact bar
    }).setThreshold(.001f);
    this.compactSpring.setEndListener(this::onCompactSettled);
    this.badges = preview ? null : new XgramNavBadges(badgeCallback);
    if (preview) {
      this.tdlib = context.currentTdlib();
      this.away = false;
    } else {
      this.away = true;
      ThemeManager.instance().addThemeListener(themeListener);
      XgramSettings.instance().addListener(contentSettingsListener);
      com.fahadali.xgram.activity.XgramActivityBadge.instance().addListener(activityBadgeListener);
      navigation.xgramSetBarListener(this);
      this.inset = Math.max(0, navigation.xgramBottomInsetWithoutIme());
    }
    readSettings();
    rebuildTabs(tabDests());
    requestAvatars();
    updateProfileDescription();
  }

  // Static API

  /** What the compose button and the snackbars keep clear above the navigation bar: the pill and 16dp under it. */
  public static int room () {
    return Screen.dp(SCREEN_OFFSET + HEIGHT);
  }

  /** What the lists keep clear: the band too, so their last row ends where it starts. */
  public static int listRoom () {
    return Screen.dp(SCREEN_OFFSET + HEIGHT + BAND_ABOVE);
  }

  /** The compact bar's centre line above the navigation bar: its bottom stays 16dp above it as it shrinks to 56dp. */
  public static int compactRowCenterPx () {
    return Screen.dp(SCREEN_OFFSET + HEIGHT_COMPACT / 2f);
  }

  /** How much lower the compact bar's top is than the full one's (64dp to 56dp, the bottom kept). */
  public static int compactDropPx () {
    return Screen.dp(HEIGHT - HEIGHT_COMPACT);
  }

  @Nullable
  public static XgramNavToolbar of (@Nullable BaseActivity context) {
    XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
    return twoPane != null ? twoPane.toolbar() : null;
  }

  /** The toolbar is on screen in {@code c}'s window: a screen it opened is a tab screen (no back arrow, a fade back). */
  public static boolean isShownFor (@Nullable ViewController<?> c) {
    if (c == null) {
      return false;
    }
    XgramNavToolbar toolbar = of(c.context());
    return toolbar != null && toolbar.shown && !toolbar.destroyed;
  }

  /** A tab screen searches or selects: the toolbar steps aside until it is done. From ViewController's mode hooks. */
  public static void onTransformModeChanged (@Nullable ViewController<?> c, boolean searching) {
    XgramNavToolbar toolbar = c != null ? of(c.context()) : null;
    if (toolbar != null) {
      toolbar.onTransform(c, searching);
    }
  }

  /** A tab screen entered or left its selection mode (its search, if open, stays as it is). */
  public static void onSelectModeChanged (@Nullable ViewController<?> c) {
    XgramNavToolbar toolbar = c != null ? of(c.context()) : null;
    if (toolbar != null) {
      toolbar.onTransform(c, toolbar.transformSearching);
    }
  }

  public static int destOf (String key) {
    for (int i = 0; i < TAB_KEYS.length; i++) {
      if (TAB_KEYS[i].equals(key)) {
        return i;
      }
    }
    return -1;
  }

  public static String keyOf (int dest) {
    return TAB_KEYS[dest];
  }

  /** The settings page's row icon: the outlined drawing, a person for the profile. */
  public static int tabIcon (String key) {
    int dest = destOf(key);
    if (dest == DEST_PROFILE) {
      return R.drawable.baseline_account_circle_24;
    }
    return dest >= 0 ? XgramNavRail.outlinedOf(dest) : 0;
  }

  /** The settings page's row title ("Saved messages", not "Saved"). */
  public static String tabTitle (String key) {
    int dest = destOf(key);
    return dest >= 0 ? Lang.getString(DEST_TITLE[dest]) : key;
  }

  /** The order of the settings page: every key, as the user ordered them. */
  public static List<String> orderedKeys () {
    return XgramReorder.ordered(XgramReorder.NAV_BAR, DEFAULT_ORDER);
  }

  /** How many tabs the settings make (before the limit): Chats, the destinations that are on, the profile as a tab. */
  public static int enabledTabCount () {
    XgramSettings settings = XgramSettings.instance();
    int bits = settings.navTabs();
    int count = 1;
    for (int dest = DEST_CALLS; dest <= DEST_SETTINGS; dest++) {
      if ((bits & (1 << dest)) != 0) {
        count++;
      }
    }
    if ((bits & (1 << DEST_ACTIVITY)) != 0 && isAvailable(DEST_ACTIVITY)) {
      count++;
    }
    if (settings.navProfile() == XgramSettings.NAV_PROFILE_TAB) {
      count++;
    }
    return count;
  }

  /**
   * The tabs, in order: Chats always, the profile only as a tab, the others that are on; at most 4 (Chats and the
   * first 3 others), at least 2 (Settings, then Contacts, are added). The single resolver of the toolbar and its page.
   */
  public static String[] tabKeys () {
    int[] dests = tabDests();
    String[] keys = new String[dests.length];
    for (int i = 0; i < dests.length; i++) {
      keys[i] = TAB_KEYS[dests[i]];
    }
    return keys;
  }

  static int[] tabDests () {
    XgramSettings settings = XgramSettings.instance();
    int bits = settings.navTabs();
    boolean profileTab = settings.navProfile() == XgramSettings.NAV_PROFILE_TAB;
    ArrayList<Integer> kept = new ArrayList<>(MAX_TABS);
    int others = 0;
    for (String key : XgramReorder.ordered(XgramReorder.NAV_BAR, DEFAULT_ORDER)) {
      int dest = destOf(key);
      if (dest < 0 || kept.contains(dest)) {
        continue;
      }
      boolean keep;
      if (dest == DEST_CHATS) {
        keep = true;
      } else if (dest == DEST_PROFILE) {
        keep = profileTab;
      } else {
        keep = (bits & (1 << dest)) != 0 && isAvailable(dest);
      }
      if (!keep) {
        continue;
      }
      if (dest != DEST_CHATS) {
        if (others >= MAX_TABS - 1) {
          continue;
        }
        others++;
      }
      kept.add(dest);
    }
    if (!kept.contains(DEST_CHATS)) {
      kept.add(0, DEST_CHATS);
    }
    if (kept.size() < MIN_TABS && !kept.contains(DEST_SETTINGS)) {
      kept.add(DEST_SETTINGS);
    }
    if (kept.size() < MIN_TABS && !kept.contains(DEST_CONTACTS)) {
      kept.add(DEST_CONTACTS);
    }
    int[] result = new int[kept.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = kept.get(i);
    }
    return result;
  }

  /** Appearance's row: "Off", or the tabs ("Chats, Contacts, Settings + your photo"). */
  public static String summary () {
    XgramSettings settings = XgramSettings.instance();
    if (!settings.bottomNav()) {
      return Lang.getString(R.string.XgramNavBarOff);
    }
    StringBuilder b = new StringBuilder();
    for (int dest : tabDests()) {
      if (b.length() > 0) {
        b.append(", ");
      }
      b.append(Lang.getString(DEST_TAB_LABEL[dest]));
    }
    String tabs = b.toString();
    return settings.navProfile() == XgramSettings.NAV_PROFILE_BUTTON ? Lang.getString(R.string.XgramNavBarWithPhoto, tabs) : tabs;
  }

  /** The settings page's live preview of the toolbar (the real pill and button, no navigation). */
  public static HostView newPreview (BaseActivity context) {
    return new XgramNavToolbar(context, null, null, null, true).host;
  }

  /**
   * The pill's colour. Standard: the lowest container in light (tone 100, clear of both page colours), high in dark
   * (tone 17), the #1E1E1E chip step with pure black (where the page is black). Vibrant: primaryContainer (accent roles
   * stay coloured with pure black).
   */
  static int containerRole (boolean vibrant) {
    return vibrant ? XgramExpressive.PRIMARY_CONTAINER : XgramAmoled.floatingRole(ThemeManager.instance().isCurrentThemeDark() ? XgramExpressive.SURFACE_CONTAINER_HIGH : XgramExpressive.SURFACE_CONTAINER_LOWEST);
  }

  // API for XgramTwoPane, the chat list and the side menu

  public boolean isShown () {
    return shown && !destroyed;
  }

  /** On screen for this chat list (its tabs, room and compose button follow the toolbar). */
  public boolean isRootOf (@Nullable MainController c) {
    return c != null && c == root && shown && !destroyed;
  }

  public boolean hasTab (int dest) {
    for (int d : dests) {
      if (d == dest) {
        return true;
      }
    }
    return false;
  }

  public boolean isInline () {
    return inline;
  }

  /** How far scrolling has slid the bar away now (Hide): 0 on screen, 1 gone. Its forced steps aside do not count. */
  public float scrollAwayNow () {
    return scrollAwaySpring.getValue();
  }

  /** How compact scrolling has made the bar now (Shrink, Collapse): 0 full, 1 compact, a little past both in a bounce. */
  public float compactProgress () {
    return compactNow();
  }

  /** The chat list's compose button sits on the toolbar's row (no band), or above it. */
  public void setInline (boolean inline) {
    if (this.inline != inline) {
      this.inline = inline;
      host.invalidate();
      layoutGroup(); // a compact pill moves to its place on the row (pillTopNow)
    }
  }

  /** The photo is on the toolbar (beside it or as a tab): its long press switches accounts. */
  public boolean hasProfile () {
    return buttonMode || hasTab(DEST_PROFILE);
  }

  /**
   * The accounts and Add account, as the photo's long press shows them: the chat list's overflow menu offers them while
   * the side menu is hidden and there is no photo on the toolbar.
   */
  public boolean showAccountMenu () {
    return !destroyed && !preview && shown && showAccounts();
  }

  /**
   * From the window's end edge to the end of the group (the profile button, or the pill), in px: with the compose
   * button set to go with the bar, it stands above the button, end-aligned with it. The final layout, also while a
   * re-fit animates.
   */
  public int groupEndInsetPx () {
    if (fitWidth <= 0) {
      return Screen.dp(SCREEN_OFFSET);
    }
    float end = Lang.rtl() ? fitGroupLeft : fitWidth - (fitGroupLeft + fitGroupW);
    return Math.max(0, Math.round(end));
  }

  /**
   * groupEndInsetPx() once the bar is fully compact, from the fit (steady while it animates): shrunk, the group stays
   * centred where it was (Shrink); folded, the pill is the selected tab's width at the start edge and the profile
   * button has faded out (Collapse). Right to left mirrors both, as groupEndInsetPx does.
   */
  public int compactEndInsetPx () {
    if (fitWidth <= 0) {
      return groupEndInsetPx();
    }
    if (foldsIntoTabWhenCompact()) {
      final float pillW = 2f * Screen.dpf(PAD) + tabs[selectedIndex].s;
      return Math.max(0, Math.round(fitWidth - Screen.dpf(SCREEN_OFFSET) - pillW));
    }
    final float fullW = fitW + (buttonMode ? Screen.dpf(BUTTON + BUTTON_GAP) : 0f);
    final float compactW = fitWC + (buttonMode ? Screen.dpf(BUTTON_COMPACT + BUTTON_GAP) : 0f);
    return groupEndInsetPx() + Math.max(0, Math.round((fullW - compactW) / 2f));
  }

  /**
   * How compact the bar is (0..1) once nothing of its group is within {@code columnPx} of the window's end edge, on its
   * way to compact: 0 when nothing ever was, 1 when it never clears. Follows layoutGroup, where both are linear: the
   * shrinking group's end (Shrink); the folding pill's end, with the profile button at the group's end gone by
   * 1 / FOLD_FADE (Collapse).
   */
  public float compactClearsAt (int columnPx) {
    final int endInset = groupEndInsetPx(), compactEndInset = compactEndInsetPx();
    if (foldsIntoTabWhenCompact()) {
      final int pillEndInset = endInset + (buttonMode ? Screen.dp(BUTTON + BUTTON_GAP) : 0);
      final float pill = clearsAt(pillEndInset, compactEndInset, columnPx);
      return buttonMode && endInset < columnPx ? Math.max(pill, 1f / FOLD_FADE) : pill;
    }
    return clearsAt(endInset, compactEndInset, columnPx);
  }

  /** Where an end inset moving linearly from {@code fromInset} to {@code toInset} reaches {@code columnPx}. */
  private static float clearsAt (int fromInset, int toInset, int columnPx) {
    if (fromInset >= columnPx) {
      return 0f;
    }
    if (toInset <= columnPx) {
      return 1f;
    }
    return (columnPx - fromInset) / (float) (toInset - fromInset);
  }

  /**
   * Compact, the bar folds into its selected tab: the kind it is compact in (or the setting's, while it is full) is
   * Collapse and a tab is selected (with the profile button's own profile it shrinks instead).
   */
  private boolean foldsIntoTabWhenCompact () {
    final int kind = compactSpring.getValue() > .001f || compactSpring.getTarget() >= 1f ? compactKind : scrollMode;
    return kind == XgramSettings.NAV_SCROLL_COLLAPSE && selectedIndex >= 0 && selectedIndex < tabs.length;
  }

  /** Shows or hides the toolbar (two panes, the rail and the setting decide). Hidden, it keeps its state. */
  public void setShown (boolean shown) {
    if (destroyed || preview || this.shown == shown) {
      return;
    }
    this.shown = shown;
    if (shown) {
      inset = navigation != null ? Math.max(0, navigation.xgramBottomInsetWithoutIme()) : 0;
      attachHost();
      onLanguageChanged(); // the language may have changed while it was hidden (nothing reached it then)
      if (badges != null) {
        badges.start();
      }
      lastSettledTop = null;
      readMenuBadge(); // set while it was hidden (or before it was made)
    } else {
      UI.cancel(settleRunnable);
      endTransition();
      attachScrollSource(null);
      if (badges != null) {
        badges.stop();
      }
      detachHost();
      dropWarm(); // ordinary screens now: the one on screen goes with the stack, the others go
      clearSwipeSection(); // no section to bring back later, when the toolbar shows again
    }
    onTabScreensChanged();
    if (shown) {
      settle();
    }
  }

  /** The chat list at the root (the account), the tabs as set, and what is on screen. */
  public void sync (@Nullable MainController root) {
    if (destroyed || preview) {
      return;
    }
    bind(root);
    boolean wasButton = buttonMode, wasVibrant = vibrant;
    int wasScrollMode = scrollMode;
    readSettings();
    if (scrollMode != wasScrollMode) {
      // Another choice for scrolling: the bar is back in full at once (the compact spring snaps to 0), the next scroll
      // down does the new thing
      scrollHidden = false;
      updateHidden(false);
    }
    int[] dests = tabDests();
    boolean tabsChanged = !Arrays.equals(dests, this.dests);
    if (tabsChanged || wasButton != buttonMode || wasVibrant != vibrant || iconPack != XgramIconPack.current()) {
      rebuildTabs(dests);
      dropStaleTabScreens();
      dropStaleWarm();
      requestFit(true);
      if (tabsChanged && root != null && isRootOf(root)) {
        root.xgramOnToolbarChanged(); // the Chats / Calls header tabs follow the Calls tab
      }
    }
    if (badges != null) {
      badges.refresh();
    }
    updateActivityBadge(); // a new Activity tab, or another account
    // The menu button's badge: on the photo, or on the chat list's overflow button once there is no photo (Your
    // profile: Off), or back
    readMenuBadge();
    if (root != null && !root.isDestroyed()) {
      root.xgramUpdateMoreBadge(false);
    }
    settle();
  }

  /**
   * The side menu button's badge (MainActivity#updateCounter): another account's unread chats, or a problem to resolve
   * (notifications, the password, the phone number, the birthdate). While the side menu is hidden there is no menu
   * button: the photo (beside the bar or as a tab) shows it as a dot, or the chat list's overflow button while there is
   * no photo.
   */
  public void setMenuBadge (@ColorId int colorId, boolean animated) {
    if (destroyed || preview) {
      return;
    }
    menuBadgeColorId = colorId;
    updateMenuBadge(animated);
    if (root != null && !root.isDestroyed()) {
      root.xgramUpdateMoreBadge(animated);
    }
  }

  /** Hide the side menu changed: the photo's dot comes or goes (there is a menu button to show it again, or none). */
  public void refreshMenuBadge () {
    if (!destroyed && !preview) {
      readMenuBadge();
    }
  }

  private void readMenuBadge () {
    if (context instanceof org.thunderdog.challegram.MainActivity) {
      menuBadgeColorId = ((org.thunderdog.challegram.MainActivity) context).getXgramMenuBadgeColorId();
    }
    updateMenuBadge(false);
  }

  private void updateMenuBadge (boolean animated) {
    final boolean show = menuBadgeColorId != ColorId.NONE && XgramSettings.instance().navHideDrawer();
    if (show) {
      menuBadgeDrawColorId = menuBadgeColorId;
    }
    final float target = show ? 1f : 0f;
    if (menuBadgeSpring.getTarget() != target) {
      if (animated && isVisibleNow()) {
        menuBadgeSpring.animateTo(target);
      } else {
        menuBadgeSpring.setValue(target);
      }
    }
    invalidateMenuBadge(); // also a new colour
  }

  private void invalidateMenuBadge () {
    profile.invalidate();
    pill.invalidate();
  }

  /**
   * The menu button's badge as BackHeaderButton draws it: a 3dp dot in a 4.5dp cutout of the colour under it, at the
   * top end corner of a photo of this radius.
   */
  private void drawMenuBadge (Canvas c, float cx, float cy, float radius, int cutoutColor) {
    final float presence = MathUtils.clamp(menuBadgeSpring.getValue());
    if (presence <= 0f || menuBadgeDrawColorId == ColorId.NONE || preview) {
      return;
    }
    final float d = radius * .7071f;
    final float x = Lang.rtl() ? cx - d : cx + d, y = cy - d;
    c.drawCircle(x, y, Screen.dp(4.5f) * presence, Paints.fillingPaint(cutoutColor));
    c.drawCircle(x, y, Screen.dp(3f) * presence, Paints.fillingPaint(ColorUtils.alphaColor(presence, Theme.getColor(menuBadgeDrawColorId))));
  }

  /** The chat list's page changed (a swipe, its tabs, the toolbar): the selection follows it. */
  public void onPageChanged () {
    if (destroyed || preview || !shown || transitionActive || navigation == null) {
      return;
    }
    if (navigation.getCurrentStackItem() == root && root != null) {
      setTarget(computeSelected(root), isLaidOut());
      if (badgeCallback.isCallListOnScreen() && badges != null) {
        badges.markCallsSeen();
      }
    }
    host.invalidate(); // the band's colour
  }

  /**
   * A frame of a page swipe between Chats and Calls (no folders): {@code f} 0 on Chats, 1 on Calls. While the finger
   * drags (and settles after it) the morph follows the pager directly; the band always follows it.
   */
  public void onPagerScrolled (float f, boolean dragged) {
    if (destroyed || preview || !shown) {
      return;
    }
    host.invalidate();
    if (!dragged || ignorePagerScroll || transitionActive || navigation == null || root == null || navigation.getCurrentStackItem() != root) {
      return;
    }
    Tab chats = tabOf(DEST_CHATS), calls = tabOf(DEST_CALLS);
    if (chats == null || calls == null) {
      return;
    }
    f = MathUtils.clamp(f);
    for (Tab tab : tabs) {
      float value = tab == chats ? 1f - f : tab == calls ? f : 0f;
      tab.e.setValue(value);
      tab.t.setValue(value);
      tab.c.setValue(value);
    }
    onTabsMoved();
  }

  /**
   * A finger started dragging the pages: the morph follows it (also over a tap's page change still settling). Folded
   * into one tab (Collapse), the bar comes back: the tabs the pages move to are on it.
   */
  public void onPagerDragStarted () {
    ignorePagerScroll = false;
    if (!destroyed && !preview && shown && collapsesToTab() && compactSpring.getTarget() >= 1f) {
      expandFolded();
    }
  }

  /** The pager stopped: taps on the toolbar no longer hold back the pager-driven morph. */
  public void onPagerIdle () {
    ignorePagerScroll = false;
    if (!destroyed && !preview && shown && navigation != null && root != null && navigation.getCurrentStackItem() == root && !transitionActive) {
      setTarget(computeSelected(root), isLaidOut()); // springs to the exact targets (nothing to do when they are reached)
    }
  }

  /** The language changed: new labels and descriptions, the order for the text direction; laid out again at once. */
  public void onLanguageChanged () {
    if (destroyed) {
      return;
    }
    for (Tab tab : tabs) {
      tab.updateLabel();
    }
    updateProfileDescription();
    requestFit(false);
  }

  public void onThemeChanged () {
    invalidateAll();
  }

  /**
   * Scrolling down hides the toolbar, or shrinks it to its icons or folds it into one tab (Navigation bar > While
   * scrolling), scrolling up brings it back (never with touch exploration, nor while the photo is dragged or slides to
   * switch accounts: the list may still scroll from a fling).
   */
  public void setScrollHidden (boolean hidden) {
    if (hidden && !scrollHidden && (isTouchExploration() || isSwipingPhoto())) {
      return;
    }
    if (scrollHidden != hidden) {
      scrollHidden = hidden;
      updateHidden(true);
    }
  }

  /** The chat list's reasons to step aside (FORCE_SEARCH, FORCE_SELECT, FORCE_FOLDER_MENU, FORCE_FAB_MENU). */
  public void setForceHidden (int mask, boolean animated) {
    int newMask = (forceMask & FORCE_OWN) | (mask & ~FORCE_OWN);
    if (newMask != forceMask) {
      forceMask = newMask;
      updateHidden(animated);
    }
  }

  /**
   * The side menu's Contacts, Settings or Calls while they are tabs: the toolbar opens them as a tab change (the side
   * menu closes). False when the side menu should open them as before.
   */
  public boolean openFromDrawer (int dest) {
    if (destroyed || preview || !shown || navigation == null || navigation.isAnimating() || navigation.isDestroyed() || !hasTab(dest)) {
      return false;
    }
    if (!isTabRoot(navigation.getCurrentStackItem()) || root == null || root.isDestroyed()) {
      return false;
    }
    fromDrawer = true;
    try {
      onTabClick(dest, null);
    } finally {
      fromDrawer = false;
    }
    return true;
  }

  public void destroy () {
    if (destroyed) {
      return;
    }
    boolean wasShown = shown;
    destroyed = true;
    shown = false;
    UI.cancel(settleRunnable);
    UI.cancel(layoutNotify);
    endTransition();
    attachScrollSource(null);
    dropWarm();
    hideSpring.cancel();
    scrollAwaySpring.cancel();
    compactSpring.cancel();
    cancelPreviewDemo();
    widthSpring.cancel();
    leftSpring.cancel();
    menuBadgeSpring.cancel();
    for (Tab tab : tabs) {
      tab.destroy();
    }
    profile.destroy();
    if (badges != null) {
      badges.destroy();
    }
    if (!preview) {
      ThemeManager.instance().removeThemeListener(themeListener);
      XgramSettings.instance().removeListener(contentSettingsListener);
      com.fahadali.xgram.activity.XgramActivityBadge.instance().removeListener(activityBadgeListener);
      UI.cancel(staleWarmRunnable);
      if (navigation != null) {
        navigation.xgramSetBarListener(null);
      }
    }
    detachHost();
    // Tab screens left on the stack become ordinary screens again (back arrow, slide, no room)
    if (navigation != null && !navigation.isDestroyed()) {
      NavigationStack stack = navigation.getStack();
      ViewController<?> top = navigation.getCurrentStackItem();
      boolean topWasTab = top != null && top.xgramBarTabRaw() != -1;
      boolean any = false;
      for (int i = 0; i < stack.size(); i++) {
        ViewController<?> c = stack.get(i);
        if (c != null && c.xgramBarTabRaw() != -1) {
          c.xgramSetBarTab(-1);
          c.xgramOnBarTabChanged();
          any = true;
        }
      }
      if (topWasTab && !navigation.isAnimating() && !top.inTransformMode() && navigation.getHeaderView() != null) {
        navigation.getHeaderView().setTitle(top); // its back arrow is back
      }
      if (any || wasShown) {
        navigation.xgramReapplyBottomInsets();
      }
    }
  }

  // XgramTwoPane's layout passes: the host covers the bottom of the navigation, the inset strip included

  /** The navigation's own horizontal range in the content layout (it carries the side insets as margins). */
  private int navLeft (ViewGroup parent) {
    if (navigation == null) {
      return 0;
    }
    ViewGroup.LayoutParams params = navigation.getValue().getLayoutParams();
    return params instanceof ViewGroup.MarginLayoutParams ? Math.max(0, ((ViewGroup.MarginLayoutParams) params).leftMargin) : 0;
  }

  private int navRight (ViewGroup parent) {
    int width = parent.getMeasuredWidth();
    if (navigation == null) {
      return width;
    }
    ViewGroup.LayoutParams params = navigation.getValue().getLayoutParams();
    int right = params instanceof ViewGroup.MarginLayoutParams ? Math.max(0, ((ViewGroup.MarginLayoutParams) params).rightMargin) : 0;
    return Math.max(navLeft(parent), width - right);
  }

  void measureHost (ViewGroup parent) {
    if (destroyed || !shown || host.getParent() != parent) {
      return;
    }
    int width = Math.max(0, navRight(parent) - navLeft(parent));
    int height = Math.min(parent.getMeasuredHeight(), hostHeight());
    host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Math.max(0, height), View.MeasureSpec.EXACTLY));
  }

  void layoutHost (ViewGroup parent) {
    if (destroyed || !shown || host.getParent() != parent) {
      return;
    }
    int bottom = parent.getMeasuredHeight();
    int height = Math.min(bottom, hostHeight());
    host.layout(navLeft(parent), bottom - height, navRight(parent), bottom);
  }

  private int hostHeight () {
    return inset + listRoom();
  }

  private void attachHost () {
    if (container == null || navigation == null || host.getParent() == container) {
      return;
    }
    if (host.getParent() instanceof ViewGroup) {
      ((ViewGroup) host.getParent()).removeView(host);
    }
    int index = container.indexOfChild(navigation.getValue());
    // Above every screen (the toolbar belongs to the tab screens), under the voice / video recorder and the side menu
    container.addView(host, index != -1 ? index + 1 : container.getChildCount(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hostHeight(), Gravity.BOTTOM));
  }

  private void detachHost () {
    XgramSnackbar.setBottomObstruction(host, 0); // XGram
    ViewParent parent = host.getParent();
    if (parent instanceof ViewGroup) {
      ((ViewGroup) parent).removeView(host);
    }
  }

  // NavigationController.XgramBarListener

  @Override
  public void onXgramTransitionBegin (ViewController<?> left, ViewController<?> right, boolean forward, int kind) {
    if (destroyed || preview || !shown || navigation == null) {
      return;
    }
    UI.cancel(settleRunnable);
    transitionActive = true;
    tLeft = left;
    tRight = right;
    tForward = forward;
    tKind = kind;
    tabL = isTabRoot(left) || isSwipeSwitchRoot(left);
    tabR = isTabRoot(right) || isSwipeSwitchRoot(right);
    View navigationView = navigation.getValue();
    holeOffsetX = navigationView.getLeft() - host.getLeft();
    holeOffsetY = navigationView.getTop() - host.getTop();
    if (tabL && tabR) {
      // Between two tab roots (back to the chat list, or a tab screen open() is fading in): the pill morphs with the
      // fade-through, both from this frame (a tab screen does not wait for its data: NavigationController)
      setTarget(computeSelected(forward ? right : left), true);
    } else if (tabL && !tabR && !forward) {
      // Back to a tab root from an ordinary screen: the stack below may have changed since the toolbar last settled
      int d = computeSelected(left);
      if (d != selectedDest) {
        setTarget(d, kind == KIND_CARD); // over the card the toolbar is visible: it springs; else it is still hidden
      }
    }
    boolean enteringTab = forward ? tabR : tabL;
    if (enteringTab && (scrollHidden || (forceMask & FORCE_TRANSFORM) != 0)) {
      scrollHidden = false;
      forceMask &= ~FORCE_TRANSFORM;
      transformSearching = false;
      boolean follows = tabL != tabR; // it comes back with its screen: already in place
      updateHidden(!follows);
    }
    if (tabL && tabR) {
      away = false;
    }
    if (kind == KIND_CARD && holeKnown) {
      applyCardHole(); // the card is above the toolbar from its first frame
    }
    applyTransitionFrame();
  }

  @Override
  public void onXgramNavigationFrame () {
    if (transitionActive) {
      applyTransitionFrame();
    }
  }

  @Override
  public void onXgramTransitionEnd (boolean navigated) {
    if (!transitionActive) {
      return;
    }
    // What is on top once the stack has changed: the toolbar is in its final state in this frame (no flash over a
    // screen that stayed, a cancelled back gesture), the rest settles on the next one
    ViewController<?> finalTop = tForward ? (navigated ? tRight : tLeft) : (navigated ? tLeft : tRight);
    boolean tabRoot = isTabRoot(finalTop) || isSwipeSwitchRoot(finalTop);
    if (navigated && swipeSectionDest != -1 && !isAccountRoot(finalTop, swipeSectionAccountId)) {
      clearSwipeSection(); // the user went elsewhere before the switch landed: the new account opens on Chats
    }
    endTransition();
    away = !tabRoot;
    applyFollow(tabRoot ? 1f : 0f, 0f);
    host.invalidate();
    UI.cancel(settleRunnable);
    UI.post(settleRunnable);
  }

  @Override
  public void onXgramInsets (int bottomInset, int bottomInsetWithoutIme) {
    if (destroyed || preview) {
      return;
    }
    int inset = Math.max(0, bottomInsetWithoutIme);
    if (this.inset != inset) {
      this.inset = inset;
      ViewGroup.LayoutParams params = host.getLayoutParams();
      if (params != null && params.height != hostHeight()) {
        params.height = hostHeight();
        host.setLayoutParams(params);
      } else {
        host.requestLayout();
      }
    }
    setForceBit(FORCE_IME, bottomInset > bottomInsetWithoutIme, true);
  }

  @Override
  public int xgramBottomRoom (ViewController<?> c) {
    return shown && !destroyed && c != null && c.xgramBarTabRaw() != -1 ? listRoom() : 0;
  }

  // XgramNavMotion.CardHoleListener: the predictive back card above the toolbar, which is cut out where the card is

  @Override
  public void onCardHole (@Nullable RectF rectInRoot, float radius, float alpha) {
    if (destroyed || preview) {
      return;
    }
    if (rectInRoot == null) {
      holeKnown = false;
      host.setCardHole(null, 0f);
      return;
    }
    // Kept: the card's first frame comes before the transition's begin (it is applied there)
    holeInRoot.set(rectInRoot);
    cardHoleRadius = radius;
    cardHoleAlpha = alpha;
    holeKnown = true;
    if (transitionActive) {
      applyCardHole();
    }
  }

  private final RectF holeInRoot = new RectF();
  private float cardHoleRadius, cardHoleAlpha;
  private boolean holeKnown;

  private void applyCardHole () {
    holeRect.set(holeInRoot);
    holeRect.offset(holeOffsetX, holeOffsetY);
    holePath.rewind();
    holePath.addRoundRect(holeRect, cardHoleRadius, cardHoleRadius, Path.Direction.CW);
    host.setCardHole(holePath, cardHoleAlpha);
  }

  // Following the screens

  private void endTransition () {
    transitionActive = false;
    tLeft = tRight = null;
    tabL = tabR = false;
    holeKnown = false;
    host.setCardHole(null, 0f);
  }

  /** One frame of a transition (9.2): the toolbar goes with its tab screen. */
  private void applyTransitionFrame () {
    if (navigation == null) {
      return;
    }
    float factor = navigation.getFactor();
    float alpha, translation = 0f;
    if (tabL && tabR) {
      alpha = 1f; // a tab change, or back to the chat list: the toolbar stays, the band crossfades
    } else if (!tabL && !tabR) {
      alpha = 0f;
    } else {
      ViewController<?> tab = tabL ? tLeft : tRight;
      switch (tKind) {
        case KIND_SHARED_AXIS: {
          View view = tab != null ? tab.getValue() : null;
          alpha = view != null ? view.getAlpha() : 1f;
          translation = view != null ? view.getTranslationX() : 0f;
          break;
        }
        case KIND_CARD: {
          alpha = 1f; // as the live app bar: shown from the first frame, the card cut out of it
          break;
        }
        default: {
          alpha = tab == tLeft ? factor : 1f - factor;
          break;
        }
      }
    }
    applyFollow(MathUtils.clamp(alpha), translation);
    if (tabL && tabR) {
      host.invalidate(); // the band
    }
  }

  /** The progress of a fade between two tab screens: 0 at the screen it leaves, 1 at the one it shows. */
  private float transitionProgress () {
    if (navigation == null) {
      return 0f;
    }
    float factor = MathUtils.clamp(navigation.getFactor());
    return tForward ? 1f - factor : factor;
  }

  private void applyFollow (float alpha, float translation) {
    followAlpha = alpha;
    followTranslation = translation;
    if (host.getAlpha() != alpha) {
      host.setAlpha(alpha);
    }
    if (host.getTranslationX() != translation) {
      host.setTranslationX(translation);
    }
    updateVisibility();
  }

  /** The stack settled: what is on top decides where the toolbar is and what it shows (9.4). */
  private void settle () {
    if (destroyed || preview || !shown || navigation == null || transitionActive) {
      return;
    }
    if (navigation.isAnimating()) {
      // A navigation the toolbar did not see begin (made or shown while it ran, a rebase after sign-in): try again
      // once it is done. Delayed, so the main looper does not spin through an asynchronous wait
      UI.cancel(settleRunnable);
      UI.post(settleRunnable, 16L);
      return;
    }
    ViewController<?> top = navigation.getCurrentStackItem();
    boolean tabRoot = isTabRoot(top);
    boolean topChanged = top != lastSettledTop;
    boolean wasAway = away;
    away = !tabRoot;
    if (tabRoot && (topChanged || wasAway)) {
      scrollHidden = false;
    }
    boolean transform = false;
    if (tabRoot && top != root) {
      if (topChanged) {
        transformSearching = top.inSearchMode();
        adoptWarm(top);
      }
      transform = transformSearching || top.inSelectMode();
    } else {
      transformSearching = false;
    }
    forceMask = transform ? forceMask | FORCE_TRANSFORM : forceMask & ~FORCE_TRANSFORM;
    applyFollow(tabRoot ? 1f : 0f, 0f);
    updateHidden(true);
    // The section a swipe switched accounts from opens again below (as reopenSwipeSection checks): the selection stays
    // on it, no twitch to Chats and back
    final int section = swipeSectionDest;
    final boolean reopening = section != -1 && tdlib != null && tdlib.id() == swipeSectionAccountId && root != null && top == root && (hasTab(section) || (section == DEST_PROFILE && buttonMode));
    if (tabRoot && !reopening) {
      setTarget(computeSelected(top), isLaidOut());
    }
    attachScrollSource(tabRoot && top != root ? top : null);
    if (badges != null) {
      badges.refresh();
      if (badgeCallback.isCallListOnScreen()) {
        badges.markCallsSeen();
      }
    }
    if (topChanged) {
      lastSettledTop = top;
      navigation.xgramReapplyBottomInsets();
    }
    host.invalidate();
    if (swipeSectionDest != -1 && tdlib != null && tdlib.id() == swipeSectionAccountId) {
      reopenSwipeSection(top); // a swipe switched to this account from another section
    }
  }

  /** The chat list at the root, or a screen the toolbar opened (always right above it). */
  private boolean isTabRoot (@Nullable ViewController<?> c) {
    return c != null && !c.isDestroyed() && ((root != null && c == root) || c.xgramBarTabRaw() != -1);
  }

  /** The chat list a swipe on the photo switches to, before the toolbar binds it (the switch's fade). */
  private boolean isSwipeSwitchRoot (@Nullable ViewController<?> c) {
    return swipeSwitchAccountId != TdlibAccount.NO_ID && isAccountRoot(c, swipeSwitchAccountId);
  }

  @Override
  public boolean xgramIsSwitchRoot (ViewController<?> c) {
    return isSwipeSwitchRoot(c);
  }

  /** A chat list of {@code accountId} (an account switch's new root). */
  private static boolean isAccountRoot (@Nullable ViewController<?> c, int accountId) {
    return c instanceof MainController && !c.isDestroyed() && c.tdlib() != null && c.tdlib().id() == accountId;
  }

  /** The tab of what is on top: Chats or Calls on the chat list (by its page), a tab screen's own; else as it was. */
  private int computeSelected (@Nullable ViewController<?> top) {
    if (top != null && root != null && top == root) {
      if (!root.xgramHasCallsPage()) {
        return DEST_CHATS;
      }
      return root.xgramOnCallsPage() && hasTab(DEST_CALLS) ? DEST_CALLS : DEST_CHATS;
    }
    if (top != null && top.xgramBarTabRaw() != -1) {
      return top.xgramBarTabRaw();
    }
    return selectedDest;
  }

  /** The tab screens on the stack turned into ordinary screens or back (the toolbar came or went): 10.4. */
  private void onTabScreensChanged () {
    if (navigation == null || navigation.isDestroyed()) {
      return;
    }
    NavigationStack stack = navigation.getStack();
    for (int i = 0; i < stack.size(); i++) {
      ViewController<?> c = stack.get(i);
      if (c != null && c.xgramBarTabRaw() != -1) {
        c.xgramOnBarTabChanged();
      }
    }
    reapplyTopHeader();
    navigation.xgramReapplyBottomInsets();
  }

  /**
   * The tabs changed: a tab screen on the stack whose destination is no longer a tab (Settings removed, Profile no
   * longer in the bar) becomes an ordinary screen again (back arrow, slide, no room), as in {@link #destroy()}.
   */
  private void dropStaleTabScreens () {
    if (navigation == null || navigation.isDestroyed()) {
      return;
    }
    NavigationStack stack = navigation.getStack();
    ViewController<?> top = navigation.getCurrentStackItem();
    boolean any = false, topDropped = false;
    for (int i = 0; i < stack.size(); i++) {
      ViewController<?> c = stack.get(i);
      int d = c != null ? c.xgramBarTabRaw() : -1;
      if (d != -1 && !hasTab(d) && !(d == DEST_PROFILE && buttonMode)) {
        c.xgramSetBarTab(-1);
        c.xgramOnBarTabChanged();
        any = true;
        topDropped |= c == top;
      }
    }
    if (!any) {
      return;
    }
    if (topDropped && !navigation.isAnimating() && !top.inTransformMode() && navigation.getHeaderView() != null) {
      navigation.getHeaderView().setTitle(top); // its back arrow is back
    }
    navigation.xgramReapplyBottomInsets();
  }

  /** The app bar of a tab screen on top again: its back arrow comes or goes. */
  private void reapplyTopHeader () {
    if (navigation == null || navigation.isAnimating()) {
      return;
    }
    ViewController<?> top = navigation.getCurrentStackItem();
    if (top != null && top.xgramBarTabRaw() != -1 && !top.inTransformMode() && navigation.getHeaderView() != null) {
      navigation.getHeaderView().setTitle(top);
    }
  }

  private void bind (@Nullable MainController root) {
    if (this.root == root) {
      return;
    }
    dropWarm(); // another chat list (an account switch, a sign-in): its tab screens are made again
    this.root = root;
    Tdlib tdlib = root != null ? root.tdlib() : null;
    if (tdlib != this.tdlib) {
      this.tdlib = tdlib;
      swipeSwitchAccountId = TdlibAccount.NO_ID; // the switch's chat list is the root now, or it went elsewhere
      if (swipeSectionDest != -1 && (tdlib == null || tdlib.id() != swipeSectionAccountId)) {
        clearSwipeSection(); // the switch a swipe started went elsewhere
      }
      if (badges != null) {
        badges.setTdlib(tdlib);
      }
      requestAvatars();
      updateProfileDescription();
    }
  }

  private void readSettings () {
    XgramSettings settings = XgramSettings.instance();
    buttonMode = settings.navProfile() == XgramSettings.NAV_PROFILE_BUTTON;
    vibrant = settings.navStyle() == XgramSettings.NAV_STYLE_VIBRANT;
    scrollMode = settings.navScroll();
  }

  // Scroll hiding on the tab screens (the chat list hides it with its compose button)

  private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
    private int accumulated;

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (dy != 0 && (dy > 0) != (accumulated > 0)) {
        accumulated = 0; // the direction flipped
      }
      accumulated += dy;
      float slop = Screen.getTouchSlop();
      if (accumulated > slop) {
        accumulated = Math.round(slop) + 1;
        setScrollHidden(true);
      } else if (accumulated < -slop || !recyclerView.canScrollVertically(-1)) {
        if (accumulated < -slop) {
          accumulated = -Math.round(slop) - 1;
        }
        setScrollHidden(false);
      }
    }
  };

  private void attachScrollSource (@Nullable ViewController<?> c) {
    RecyclerView view = null;
    if (c != null) {
      view = c.xgramBarScrollView();
      if (view == null && c instanceof RecyclerViewController) {
        view = ((RecyclerViewController<?>) c).getRecyclerView(); // contacts, calls
      }
    }
    if (view == scrollSource) {
      return;
    }
    if (scrollSource != null) {
      scrollSource.removeOnScrollListener(scrollListener);
    }
    scrollSource = view;
    if (view != null) {
      view.addOnScrollListener(scrollListener);
    }
  }

  private static void scrollToTop (@Nullable ViewController<?> c) {
    if (c instanceof ViewPagerController.ScrollToTopDelegate) {
      ((ViewPagerController.ScrollToTopDelegate) c).onScrollToTopRequested();
      return;
    }
    RecyclerView view = c != null ? c.xgramBarScrollView() : null;
    if (view != null) {
      view.smoothScrollToPosition(0);
    }
  }

  private boolean isTouchExploration () {
    AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }

  // Hiding

  private void setForceBit (int bit, boolean on, boolean animated) {
    int mask = on ? forceMask | bit : forceMask & ~bit;
    if (mask != forceMask) {
      forceMask = mask;
      updateHidden(animated);
    }
  }

  private void onTransform (ViewController<?> c, boolean searching) {
    if (destroyed || preview || !shown || navigation == null || c == root || c.xgramBarTabRaw() == -1 || navigation.getCurrentStackItem() != c) {
      return;
    }
    transformSearching = searching;
    setForceBit(FORCE_TRANSFORM, searching || c.inSelectMode(), true);
  }

  private void updateHidden (boolean animated) {
    if (preview) {
      return;
    }
    final boolean hideOnScroll = scrollMode == XgramSettings.NAV_SCROLL_HIDE;
    float target = (scrollHidden && hideOnScroll) || forceMask != 0 ? 1f : 0f;
    // Hiding and showing animate only while the toolbar is on a tab screen (also back from slid away, while invisible)
    final boolean animate = animated && canAnimateHide();
    // The compose button at the side goes with the scroll hiding only, on the same spring from the same frame
    final float away = scrollHidden && hideOnScroll ? 1f : 0f;
    if (animate) {
      hideSpring.animateTo(target);
      scrollAwaySpring.animateTo(away);
    } else {
      hideSpring.setValue(target);
      scrollAwaySpring.setValue(away);
    }
    applyHide();
    // Shrunk by scrolling: it stays so while a search, a selection or the keyboard slides it away, and comes back so
    setCompact(scrollHidden && !hideOnScroll, animate);
    notifyFabFollow(); // a snap calls no spring listener
  }

  /** How compact the bar is now: 0 full, 1 shrunk or folded (a little past both ends while the spring overshoots). */
  private float compactNow () {
    return Math.max(-.05f, Math.min(1.05f, compactSpring.getValue()));
  }

  /**
   * How far the tabs are shrunk to their icons now (the widths, the labels, the button): compactNow() in Shrink. In
   * Collapse only as far as no tab is selected (the profile button's own profile): with no tab to fold into, the bar
   * shrinks instead.
   */
  private float shrinkNow () {
    final float k = compactNow();
    if (k == 0f || compactKind == XgramSettings.NAV_SCROLL_SHRINK) {
      return k;
    }
    return k * (1f - tabSelection());
  }

  /**
   * How far the bar is folded into its selected tab now (Collapse; 0 in Shrink): never past either end (the widths of
   * the tabs folding away would go below 0), and only as far as a tab is selected.
   */
  private float collapseNow () {
    if (compactKind != XgramSettings.NAV_SCROLL_COLLAPSE || compactSpring.getValue() <= 0f) {
      return 0f;
    }
    return MathUtils.clamp(compactSpring.getValue()) * tabSelection();
  }

  /** How selected the pill's tabs are together: 1 with a tab selected, 0 with the profile button (between, moving). */
  private float tabSelection () {
    float sum = 0f;
    for (Tab tab : tabs) {
      sum += MathUtils.clamp(tab.e.getValue());
    }
    return MathUtils.clamp(sum);
  }

  /** Collapse folds the bar into a tab: one is selected (the profile button's own profile shrinks it instead). */
  private boolean collapsesToTab () {
    return compactKind == XgramSettings.NAV_SCROLL_COLLAPSE && selectedIndex >= 0 && selectedIndex < tabs.length;
  }

  /** Folding into one tab, folded, or not back in full yet: the photo may be out of sight (no account switch). */
  private boolean isFolding () {
    return collapseNow() > 0f || (collapsesToTab() && compactSpring.getTarget() >= 1f);
  }

  /**
   * A tab Collapse folds away: while the bar folds or stays folded, and as long as it is out of sight while the bar
   * comes back.
   */
  private boolean isFoldedAway (Tab tab) {
    return collapsesToTab() && tabs[selectedIndex] != tab && (compactSpring.getTarget() >= 1f || tab.opacity <= 0f);
  }

  /**
   * Folded into one tab (Collapse): a tap on it or a page drag brings the bar back, with the chat list's compose button
   * when it went with the bar (as scrolling up brings both).
   */
  private void expandFolded () {
    if (preview) {
      endPreviewDemo(); // the settings page's demo: back in full now
      return;
    }
    final MainController root = this.root;
    if (root != null && !root.isDestroyed() && navigation != null && navigation.getCurrentStackItem() == root) {
      root.showComposeWrap(null); // the chat list's own way back: its compose button, then setScrollHidden(false) here
    }
    if (scrollHidden) {
      scrollHidden = false;
      updateHidden(true);
    }
  }

  /** The pill's height now, in px: 64dp, 56dp compact (exactly today's 64dp at 0). Its items are 8dp in from it. */
  private float pillHeightNow () {
    final float full = Screen.dpf(HEIGHT);
    return full + (Screen.dpf(HEIGHT_COMPACT) - full) * compactNow();
  }

  /**
   * The pill's height for what is drawn in it, in px: exactly 64dp when full (as before), its laid-out (rounded) height
   * while compact, so the icons and the inner pill move with the pill's px (no 1px jitter back and forth as it shrinks).
   */
  private float pillHeightIn (int laidOutHeight) {
    return compactSpring.getValue() == 0f ? Screen.dpf(HEIGHT) : laidOutHeight;
  }

  private void setCompact (boolean compact, boolean animated) {
    final float target = compact ? 1f : 0f;
    final boolean targetChanged = compactSpring.getTarget() != target;
    if (compact && targetChanged && compactSpring.getValue() <= .001f) {
      compactKind = scrollMode; // it starts from full: it expands back the same way, whatever the setting is by then
    }
    if (animated) {
      compactSpring.animateTo(target); // its listener lays the group out every frame
    } else if (compactSpring.getValue() != target || compactSpring.isRunning()) {
      compactSpring.setValue(target); // no listener call: laid out here
      layoutGroup();
      notifyFabFollow();
      onCompactSettled();
    }
    if (targetChanged) {
      updateTooltips(); // the selected tab's label goes or comes back
      updateFoldedAccessibility(); // the tabs folded away are back in reach as soon as it expands
    }
  }

  /** The bar settled full or compact. */
  private void onCompactSettled () {
    updateTooltips();
    updateFoldedAccessibility();
    if (preview) {
      onPreviewDemoSettled();
    }
  }

  // The settings page's preview: a new choice for scrolling, shown once

  /**
   * The settings page chose another way for scrolling: its preview (no list to scroll) shows it once. Shrink and
   * Collapse make it compact, hold it for PREVIEW_DEMO_HOLD and bring it back in full; Hide brings it back in full. From
   * one compact choice to the other, it comes back in full first (no jump between the two shapes). Not while it is off
   * screen, nor with touch exploration (the bar never gets compact then).
   */
  private void playPreviewDemo () {
    UI.cancel(previewDemoEnd);
    previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
    final boolean animated = canAnimateHide();
    if (scrollMode == XgramSettings.NAV_SCROLL_HIDE || !animated || isTouchExploration()) {
      setCompact(false, animated);
      return;
    }
    if (scrollMode == XgramSettings.NAV_SCROLL_COLLAPSE && selectedIndex == -1 && tabs.length > 0) {
      // The profile button selected (a tap on it here): Collapse would shrink the bar instead. The preview's selection
      // is only for show: it folds into a tab
      setTarget(indexOf(DEST_CHATS) != -1 ? DEST_CHATS : tabs[0].dest, true);
    }
    if (compactSpring.getValue() > .001f && compactKind != scrollMode) {
      previewDemoNext = scrollMode; // played once it is back in full (onPreviewDemoSettled)
      setCompact(false, true);
      return;
    }
    startPreviewDemo();
  }

  /** From full (or on its way back from the same choice): compact, held once it settles there. */
  private void startPreviewDemo () {
    previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
    if (compactSpring.getTarget() >= 1f && !compactSpring.isRunning()) {
      UI.cancel(previewDemoEnd);
      UI.post(previewDemoEnd, PREVIEW_DEMO_HOLD); // compact already: no settle to wait for
      return;
    }
    setCompact(true, true); // the kind is the choice, as it starts from full
  }

  /** The demo held the compact bar long enough, or a tap on it ends it now (as it brings the real bar back). */
  private void endPreviewDemo () {
    UI.cancel(previewDemoEnd);
    previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
    if (!destroyed && preview) {
      setCompact(false, canAnimateHide());
    }
  }

  /** The preview's bar settled: compact (held for a moment), or back in full (the other compact choice plays now). */
  private void onPreviewDemoSettled () {
    if (destroyed) {
      return;
    }
    if (compactSpring.getTarget() >= 1f) {
      UI.cancel(previewDemoEnd);
      UI.post(previewDemoEnd, PREVIEW_DEMO_HOLD);
    } else if (previewDemoNext != XgramSettings.NAV_SCROLL_HIDE) {
      if (previewDemoNext == scrollMode && canAnimateHide()) {
        startPreviewDemo();
      } else {
        previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
      }
    }
  }

  /** The preview left the screen (or the toolbar is destroyed): no demo pending, the bar in full when it is back. */
  private void cancelPreviewDemo () {
    UI.cancel(previewDemoEnd);
    previewDemoNext = XgramSettings.NAV_SCROLL_HIDE;
  }

  /**
   * Folded into one tab and settled (Collapse): the tabs folded away and the button are no longer read by TalkBack nor
   * reached with a keyboard. Back as soon as the bar starts to expand.
   */
  private void updateFoldedAccessibility () {
    final boolean folded = collapsesToTab() && compactSpring.getTarget() >= 1f && !compactSpring.isRunning();
    for (int i = 0; i < tabs.length; i++) {
      setReachable(tabs[i].view, !folded || i == selectedIndex);
    }
    setReachable(profile, !folded);
  }

  private static void setReachable (View view, boolean reachable) {
    final int important = reachable ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO;
    if (view.getImportantForAccessibility() != important) {
      view.setImportantForAccessibility(important);
    }
    if (view.isFocusable() != reachable) {
      view.setFocusable(reachable);
    }
  }

  private void updateTooltips () {
    for (Tab tab : tabs) {
      tab.updateTooltip();
    }
  }

  private void applyHide () {
    float y = hideSpring.getValue() * host.getHeightForHide();
    if (host.getTranslationY() != y) {
      host.setTranslationY(y);
    }
    updateVisibility();
  }

  /** Visible while shown, not away (alpha above 0) and not settled out of the window. */
  private void updateVisibility () {
    boolean visible;
    if (preview) {
      visible = true;
    } else {
      boolean hidden = hideSpring.getTarget() >= 1f && hideSpring.getValue() >= .999f;
      visible = shown && followAlpha > 0f && !hidden;
    }
    int visibility = visible ? View.VISIBLE : View.INVISIBLE;
    if (host.getVisibility() != visibility) {
      host.setVisibility(visibility);
    }
    if (!preview) {
      // XGram: snackbars (the M3E toast) float above the toolbar while it is on a tab screen and not sliding away: at
      // its band top, as the chat list's own snackbars; not over a sheet above it
      boolean obstructs = visible && !destroyed && !away && hideSpring.getTarget() < 1f;
      XgramSnackbar.setBottomObstruction(host, obstructs ? listRoom() : 0, true);
    }
  }

  private boolean isLaidOut () {
    return host.getWidth() > 0 && androidx.core.view.ViewCompat.isAttachedToWindow(host);
  }

  private boolean isVisibleNow () {
    return isLaidOut() && host.getVisibility() == View.VISIBLE && (preview || (shown && followAlpha > 0f));
  }

  /** On a tab screen and laid out: it may be slid away (invisible), it still slides back in. */
  private boolean canAnimateHide () {
    return isLaidOut() && (preview || (shown && followAlpha > 0f));
  }

  // Tabs

  private void rebuildTabs (int[] dests) {
    int pack = XgramIconPack.current();
    boolean packChanged = pack != iconPack;
    iconPack = pack;
    Tab[] old = tabs;
    Tab[] next = new Tab[dests.length];
    for (int i = 0; i < dests.length; i++) {
      Tab reuse = null;
      for (Tab tab : old) {
        if (tab != null && tab.dest == dests[i]) {
          reuse = tab;
          break;
        }
      }
      next[i] = reuse != null ? reuse : new Tab(dests[i]);
      if (reuse != null && packChanged) {
        reuse.loadIcons();
      }
    }
    for (Tab tab : old) {
      boolean kept = false;
      for (Tab n : next) {
        if (n == tab) {
          kept = true;
          break;
        }
      }
      if (!kept) {
        tab.destroy();
        pill.removeView(tab.view);
      }
    }
    for (int i = 0; i < next.length; i++) {
      if (next[i].view.getParent() == null) {
        pill.addView(next[i].view);
      }
    }
    this.tabs = next;
    this.dests = dests.clone();
    // The selection keeps its destination; gone: Chats
    int dest = selectedDest;
    if (!(dest == DEST_PROFILE && buttonMode) && indexOf(dest) == -1) {
      dest = DEST_CHATS;
    }
    profile.setVisibility(buttonMode ? View.VISIBLE : View.GONE);
    setTarget(dest, false);
    pill.updateAccessibility();
  }

  private int indexOf (int dest) {
    for (int i = 0; i < tabs.length; i++) {
      if (tabs[i].dest == dest) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  private Tab tabOf (int dest) {
    int index = indexOf(dest);
    return index != -1 ? tabs[index] : null;
  }

  /** Left to right position of tab {@code index} (right to left languages mirror the order). */
  private int visualSlot (int index) {
    return Lang.rtl() ? tabs.length - 1 - index : index;
  }

  /**
   * The destination now selected. Every tab's springs retarget (keeping their velocity), so an interrupted change is
   * smooth; the newly selected icon pops. Not animated: everything snaps (the first layout, a re-fit, a fold).
   */
  private void setTarget (int dest, boolean animated) {
    int index = dest == DEST_PROFILE && buttonMode ? -1 : indexOf(dest);
    if (index == -1 && !(dest == DEST_PROFILE && buttonMode)) {
      return; // not a tab (Saved, a destination that went away)
    }
    boolean changed = index != selectedIndex;
    selectedDest = dest;
    selectedIndex = index;
    for (int i = 0; i < tabs.length; i++) {
      Tab tab = tabs[i];
      float target = i == index ? 1f : 0f;
      if (animated) {
        tab.e.animateTo(target);
        tab.t.animateTo(target);
        tab.c.animateTo(target);
      } else {
        tab.e.setValue(target);
        tab.t.setValue(target);
        tab.c.setValue(target);
      }
      if (i == index && changed && animated) {
        tab.q.setValue(POP_FROM);
        tab.q.animateTo(1f);
      } else if (!animated) {
        tab.q.setValue(1f);
      }
      tab.setSelectedState(i == index);
    }
    profile.setSelectedState(dest == DEST_PROFILE && buttonMode, animated);
    onTabsMoved();
    updateFoldedAccessibility(); // folded into one tab: the one in reach is the selected one
    if (fitWidth > 0 && (scrollMode == XgramSettings.NAV_SCROLL_COLLAPSE || compactKind == XgramSettings.NAV_SCROLL_COLLAPSE)) {
      checkGroupEndInset(); // folded, the bar is as wide as the selected tab: the compose button may change its place
    }
  }

  // Fit (5.3)

  /** Fits the tabs into the window again: on the next layout pass (animated when asked and on screen). */
  private void requestFit (boolean animated) {
    fitDirty = true;
    fitAnimate = fitAnimate || animated;
    host.requestLayout();
    host.invalidate();
  }

  private void ensureFit (int width) {
    if (width <= 0) {
      return;
    }
    if (!fitDirty && width == fitWidth) {
      return;
    }
    boolean sameWidth = width == fitWidth;
    if (!sameWidth && fitWidth > 0) {
      // The window changed size (rotation, a fold): the warm tab screens off the stack missed the configuration change.
      // Let go of them after this layout pass; the one on screen stays (it hears it)
      UI.cancel(dropDetachedRunnable);
      UI.post(dropDetachedRunnable);
    }
    boolean animated = fitAnimate && sameWidth && fitW > 0f && isVisibleNow();
    fitAnimate = false;
    fitDirty = false;
    fitWidth = width;
    fit(width, animated);
  }

  private void fit (int width, boolean animated) {
    final int n = tabs.length;
    if (n == 0) {
      return;
    }
    if (iconPack != XgramIconPack.current()) {
      iconPack = XgramIconPack.current();
      for (Tab tab : tabs) {
        tab.loadIcons();
      }
    }
    final float pad = Screen.dpf(PAD);
    final float buttonSpace = buttonMode ? Screen.dpf(BUTTON + BUTTON_GAP) : 0f;
    final float avail = Math.min(width - 2f * Screen.dpf(SCREEN_OFFSET), Screen.dpf(GROUP_MAX));
    final float pillMax = Math.max(2f * pad + Screen.dpf(ICON_ONLY_S), avail - buttonSpace);

    int style = -1;
    float u = 0f, w = 0f;
    for (int candidate = STYLE_R1; candidate <= STYLE_DENSE; candidate++) {
      float slot = Screen.dpf(candidate == STYLE_R1 ? U_REGULAR : U_COMPACT);
      float maxS = 0f;
      for (Tab tab : tabs) {
        if (tab.dest != DEST_SAVED) {
          maxS = Math.max(maxS, selectedWidth(candidate, tab.labelWidth(candidate)));
        }
      }
      float total = 2f * pad + maxS + (n - 1) * slot;
      if (total <= pillMax + .5f) {
        style = candidate;
        u = slot;
        w = total;
        break;
      }
    }
    boolean iconOnly = false;
    float labelRoom = Float.MAX_VALUE;
    if (style == -1) {
      style = STYLE_DENSE;
      u = Screen.dpf(U_COMPACT);
      float maxS = pillMax - 2f * pad - (n - 1) * u;
      float room = maxS - selectedWidth(STYLE_DENSE, 0f);
      if (room >= Screen.dpf(MIN_LABEL)) {
        labelRoom = room; // the widest labels are ellipsized
        w = pillMax;
      } else {
        iconOnly = true; // labels go to the tooltips and descriptions
        u = Math.max(Screen.dpf(ICON_ONLY_MIN_U), (pillMax - 2f * pad - Screen.dpf(ICON_ONLY_S)) / Math.max(1, n - 1));
        w = 2f * pad + Screen.dpf(ICON_ONLY_S) + (n - 1) * u;
      }
    }
    this.fitStyle = style;
    this.iconOnly = iconOnly;
    final TextPaint paint = style == STYLE_DENSE ? labelMedium : labelLarge;
    for (Tab tab : tabs) {
      if (iconOnly || tab.dest == DEST_SAVED) {
        tab.shownLabel = "";
        tab.shownLabelWidth = 0f;
        tab.s = iconOnly ? Screen.dpf(ICON_ONLY_S) : u;
      } else {
        float labelWidth = tab.labelWidth(style);
        String label = tab.label;
        if (labelWidth > labelRoom) {
          CharSequence fitted = TextUtils.ellipsize(label, paint, labelRoom, TextUtils.TruncateAt.END);
          label = fitted != null ? fitted.toString() : "";
          labelWidth = label.isEmpty() ? 0f : paint.measureText(label);
        }
        tab.shownLabel = label;
        tab.shownLabelWidth = labelWidth;
        tab.s = selectedWidth(style, labelWidth);
      }
      tab.updateTooltip();
    }
    float groupW = w + buttonSpace;
    float groupLeft = (width - groupW) / 2f;
    this.fitW = w;
    this.fitGroupW = groupW;
    this.fitGroupLeft = groupLeft;
    // Shrunk to its icons: every tab as wide as an unselected one, at most 48dp (the 40dp items stay 4dp in)
    this.fitUC = Math.min(Screen.dpf(U_COMPACT), u);
    this.fitWC = 2f * pad + n * fitUC;
    if (animated) {
      widthSpring.animateTo(w);
      leftSpring.animateTo(groupLeft);
    } else {
      widthSpring.setValue(w);
      leftSpring.setValue(groupLeft);
    }
    checkGroupEndInset();
  }

  /** The selected tab's width for a label {@code labelWidth} wide: lead, icon, gap, label, trail. */
  private static float selectedWidth (int style, float labelWidth) {
    boolean dense = style == STYLE_DENSE;
    return Screen.dpf(LEAD + ICON + (dense ? GAP_DENSE : GAP_REGULAR) + (dense ? TRAIL_DENSE : TRAIL_REGULAR)) + labelWidth;
  }

  private void checkGroupEndInset () {
    int endInset = groupEndInsetPx();
    // Compact, the end the bar leaves free decides whether the compose button at the side goes onto its row
    int compactEndInset = compactEndInsetPx();
    if (endInset != notifiedEndInset || compactEndInset != notifiedCompactEndInset) {
      notifiedEndInset = endInset;
      notifiedCompactEndInset = compactEndInset;
      UI.cancel(layoutNotify);
      UI.post(layoutNotify); // after this layout pass: the chat list moves its compose button
    }
  }

  private void notifyRootLayout () {
    if (!destroyed && !preview && root != null && isRootOf(root)) {
      root.xgramOnToolbarLayout();
    }
  }

  /**
   * The bar slid or got compact by a frame (or snapped): the chat list's compose button at the side follows it. Only
   * translations, so it runs right away (no layout, no post).
   */
  private void notifyFabFollow () {
    if (!destroyed && !preview && root != null && isRootOf(root)) {
      root.xgramOnToolbarMoved();
    }
  }

  /**
   * The full pill's top in the host: 16dp above the inset, 64dp tall. The band and its solid part stay there while the
   * pill shrinks (pillTopNow).
   */
  private int pillTop (int hostHeight) {
    return hostHeight - (preview ? 0 : inset) - Screen.dp(SCREEN_OFFSET) - Screen.dp(HEIGHT);
  }

  /** The pill's height now, in whole px (Screen.dp(HEIGHT) when full). */
  private int pillHeightPx () {
    return Math.round(pillHeightNow());
  }

  /**
   * The pill's top now: its bottom stays 16dp above the inset while it shrinks. On the compose button's row (inline) it
   * stays centred on the row instead, as the button is.
   */
  private int pillTopNow (int hostHeight) {
    final int shrunkBy = Screen.dp(HEIGHT) - pillHeightPx();
    return pillTop(hostHeight) + (inline ? shrunkBy / 2 : shrunkBy);
  }

  /** The pill's width now: the fit's, less as it shrinks to its icons, the selected tab's as it folds into it. */
  private float pillWidthNow () {
    final float shrunk = shrunkWidthNow();
    final float collapse = collapseNow();
    return collapse > 0f ? Math.max(0f, shrunk + (collapsedWidth() - shrunk) * collapse) : shrunk;
  }

  /** The pill's width shrunk to its icons as far as it is now (the fit's when full): where folding starts from. */
  private float shrunkWidthNow () {
    final float w = Math.max(0f, widthSpring.getValue());
    return Math.max(0f, w + (fitWC - w) * shrinkNow());
  }

  /**
   * Folded into one tab: the padding around the selected tab (between two tabs' widths while the selection moves, as
   * relayoutItems weights them).
   */
  private float collapsedWidth () {
    float sum = 0f, weight = 0f;
    for (Tab tab : tabs) {
      float e = MathUtils.clamp(tab.e.getValue());
      sum += e * tab.s;
      weight += e;
    }
    return 2f * Screen.dpf(PAD) + (weight > 0f ? sum / weight : fitUC);
  }

  /** The profile button's scale beside the shrunk bar: 1, 48/56 shrunk (folding into one tab scales it further). */
  private float buttonScaleNow () {
    return 1f + (BUTTON_COMPACT / BUTTON - 1f) * shrinkNow();
  }

  /**
   * Places the pill and the button (the re-fit springs and the compact spring move them; no layout pass). Shrinking,
   * the group stays centred where the fit put it, the button scales around its centre (its view keeps its size).
   * Folding into one tab, the pill goes from there to 16dp from the window's start edge (both its edges at once), the
   * button keeps its gap to it as it shrinks and fades out.
   */
  private void layoutGroup () {
    int hostHeight = host.getHeight() > 0 ? host.getHeight() : host.getMeasuredHeight();
    if (hostHeight <= 0) {
      return;
    }
    float shrunkW = shrunkWidthNow(), w = pillWidthNow();
    float shrunkScale = buttonScaleNow();
    // What the button gives back as it scales (0 when full: every value below is exactly the fit's then)
    float buttonShrink = Screen.dpf(BUTTON) * (1f - shrunkScale);
    float buttonSpace = Screen.dpf(BUTTON + BUTTON_GAP) - buttonShrink;
    float fullGroupW = Math.max(0f, widthSpring.getValue()) + (buttonMode ? Screen.dpf(BUTTON + BUTTON_GAP) : 0f);
    float groupW = shrunkW + (buttonMode ? buttonSpace : 0f);
    float groupLeft = leftSpring.getValue() + (fullGroupW - groupW) / 2f;
    boolean rtl = Lang.rtl();
    int top = pillTopNow(hostHeight);
    int height = pillHeightPx();
    float left = rtl && buttonMode ? groupLeft + buttonSpace : groupLeft;
    final float collapse = collapseNow();
    if (collapse > 0f) {
      // Folding into one tab: towards the start edge (the right one in right to left languages)
      final float edge = Screen.dpf(SCREEN_OFFSET);
      if (rtl) {
        int hostWidth = host.getWidth() > 0 ? host.getWidth() : host.getMeasuredWidth();
        float right = left + shrunkW;
        left = right + (hostWidth - edge - right) * collapse - w;
      } else {
        left += (edge - left) * collapse;
      }
    }
    int pillLeft = Math.round(left);
    // Compact (both edges move every frame): its end rounded on its own, as the button's place is (a steady gap)
    int pillRight = compactSpring.getValue() != 0f ? Math.round(left + w) : pillLeft + Math.round(w);
    pill.layout(pillLeft, top, pillRight, top + height);
    if (buttonMode) {
      // Laid out at its full size, centred where the scaled button goes (its gap to the pill kept)
      float buttonScale = shrunkScale * (1f + (BUTTON_FOLDED - 1f) * collapse);
      float scaledAway = Screen.dpf(BUTTON) * (1f - buttonScale);
      // (Right to left, not folding: from the group's start, so it is exactly the fit's place when full)
      int buttonLeft = Math.round(!rtl ? left + w + Screen.dpf(BUTTON_GAP) - scaledAway / 2f : collapse > 0f ? left - Screen.dpf(BUTTON + BUTTON_GAP) + scaledAway / 2f : groupLeft - scaledAway / 2f);
      int buttonTop = top + (height - Screen.dp(BUTTON)) / 2;
      int slop = Screen.dp(BUTTON_HIT_SLOP); // the view is 4dp larger on each side: the touch area around the button
      profile.layout(buttonLeft - slop, buttonTop - slop, buttonLeft + Screen.dp(BUTTON) + slop, buttonTop + Screen.dp(BUTTON) + slop);
      if (profile.getScaleX() != buttonScale) {
        profile.setScaleX(buttonScale); // around its centre (no pivot set)
        profile.setScaleY(buttonScale);
      }
      // Folding into one tab: gone by about .29, its shadow with it
      float buttonAlpha = MathUtils.clamp(1f - FOLD_FADE * collapse);
      if (profile.getAlpha() != buttonAlpha) {
        profile.setAlpha(buttonAlpha);
      }
    }
    pill.markDirty();
    host.invalidate();
  }

  // Actions (4.3): only on a tab screen with nothing moving

  private boolean canNavigate (boolean fromDrawer) {
    if (destroyed || preview || !shown || root == null || root.isDestroyed() || tdlib == null) {
      return false;
    }
    if (navigation == null || navigation.isAnimating() || navigation.isDestroyed()) {
      return false;
    }
    if (!isTabRoot(navigation.getCurrentStackItem())) {
      return false;
    }
    return fromDrawer || !context.isActivityBusyWithSomething();
  }

  /**
   * A swipe on the photo may switch accounts now: where a tap could navigate, the toolbar not sliding away nor folded
   * into one tab (the photo out of sight; back once the bar is in full), and the photo's account the one in use (no
   * other switch on its way).
   */
  private boolean canSwitchAccounts () {
    return canNavigate(false) && !away && hideSpring.getTarget() < 1f && !isFolding() && tdlib.id() == TdlibManager.instance().preferredAccountId();
  }

  /** A finger drags the photo (beside the bar or as a tab) to switch accounts, or it slides to the new one. */
  private boolean isSwipingPhoto () {
    if (profile.swipe.isMoving()) {
      return true;
    }
    for (Tab tab : tabs) {
      if (tab.swipe != null && tab.swipe.isMoving()) {
        return true;
      }
    }
    return false;
  }

  /** The toolbar's side of a photo's swipe (XgramAccountSwipe); the view that draws the photo swaps its receiver. */
  private abstract class SwipeHost implements XgramAccountSwipe.Host {
    @Override
    public boolean canSwitchAccounts () {
      return XgramNavToolbar.this.canSwitchAccounts();
    }

    @Override
    public int shownAccountId () {
      Tdlib tdlib = XgramNavToolbar.this.tdlib;
      return tdlib != null ? tdlib.id() : TdlibAccount.NO_ID;
    }

    @Override
    public void onSwitchStarted (int accountId) {
      swipeSwitchAccountId = accountId;
      keepSwipeSection(accountId);
    }

    @Override
    public void onSwitchFailed (int accountId) {
      if (swipeSwitchAccountId == accountId) {
        swipeSwitchAccountId = TdlibAccount.NO_ID;
      }
      if (swipeSectionAccountId == accountId) {
        clearSwipeSection(); // the account stays: so does the section
      }
    }
  }

  // The section a swipe switched accounts from: as Google's apps keep it, the new account opens on it too

  /**
   * Remembers the section on screen (a tab screen, or Calls on the chat list) for {@code accountId}: once the toolbar
   * is bound to its chat list and settled, settle() opens it once. Chats is where a switch lands anyway.
   */
  private void keepSwipeSection (int accountId) {
    ViewController<?> top = navigation != null ? navigation.getCurrentStackItem() : null;
    int dest = isTabRoot(top) ? computeSelected(top) : DEST_CHATS;
    if (dest == DEST_CHATS || dest == DEST_SAVED) {
      clearSwipeSection();
      return;
    }
    swipeSectionDest = dest;
    swipeSectionAccountId = accountId;
  }

  private void clearSwipeSection () {
    swipeSectionDest = -1;
    swipeSectionAccountId = TdlibAccount.NO_ID;
  }

  /** The toolbar settled on the account a swipe switched to: its section again, if the new chat list is on top. */
  private void reopenSwipeSection (@Nullable ViewController<?> top) {
    final int dest = swipeSectionDest;
    final MainController root = this.root;
    clearSwipeSection(); // once, whatever happens
    if (root == null || top != root || !(hasTab(dest) || (dest == DEST_PROFILE && buttonMode))) {
      return; // another screen by now, or no longer a tab
    }
    // After this settle (the toolbar's state is final), as a tap on the tab
    UI.post(() -> {
      if (!destroyed && this.root == root && navigation != null && navigation.getCurrentStackItem() == root && canNavigate(false)) {
        onTabClick(dest, null);
      }
      if (!destroyed && this.root == root && navigation != null && !navigation.isAnimating() && navigation.getCurrentStackItem() == root) {
        setTarget(computeSelected(root), true); // nothing opened (or it opens later): the selection follows the chat list
      }
    });
  }

  private void onTabClick (int dest, @Nullable View view) {
    if (view != null && collapsesToTab()) {
      if (compactSpring.getTarget() >= 1f) {
        // Folding into one tab, or folded: a tap anywhere on it only brings the bar back (never a tab out of sight;
        // the preview's demo too)
        if (!destroyed && (shown || preview)) {
          ViewUtils.onClick(view);
          Tab selected = tabs[selectedIndex];
          selected.q.setValue(POP_FROM);
          selected.q.animateTo(1f);
          expandFolded();
        }
        return;
      }
      Tab tab = tabOf(dest);
      if (tab != null && isFoldedAway(tab)) {
        return; // the bar comes back, this tab is still out of sight
      }
    }
    if (preview) {
      if (dest != DEST_SAVED && dest != selectedDest) {
        if (view != null) {
          ViewUtils.onClick(view);
        }
        setTarget(dest, true);
        if (compactSpring.getTarget() >= 1f) {
          endPreviewDemo(); // shrunk by its demo: another tab brings the bar back, as on the real one
        }
      }
      return;
    }
    final boolean drawer = fromDrawer;
    if (!canNavigate(drawer)) {
      return; // press feedback only
    }
    final MainController root = this.root;
    final Tdlib tdlib = this.tdlib;
    final ViewController<?> top = navigation.getCurrentStackItem();
    final boolean atRoot = top == root;
    if (dest == DEST_SAVED) {
      openSaved(top);
      return;
    }
    if (dest == computeSelected(top)) {
      // The same tab again: to the top (Chats: back to all chats while a filter shows)
      if (dest == DEST_PROFILE) {
        return;
      }
      if (atRoot && (dest == DEST_CHATS || (dest == DEST_CALLS && root.xgramHasCallsPage()))) {
        root.xgramShowRailPage(dest == DEST_CALLS, true);
      } else if (!atRoot) {
        scrollToTop(top);
      }
      return;
    }
    if (view != null) {
      ViewUtils.onClick(view);
    }
    switch (dest) {
      case DEST_CHATS:
      case DEST_CALLS: {
        final boolean calls = dest == DEST_CALLS;
        if (!calls || root.xgramHasCallsPage()) {
          if (calls) {
            forgetWarm(DEST_CALLS); // Calls is the chat list's page again (no folders): no call list screen to keep
          }
          if (atRoot) {
            ignorePagerScroll = true; // the pager's own scroll follows a tap: the springs run instead
            setTarget(dest, true);
            root.xgramShowRailPage(calls, false);
          } else {
            // Back to the chat list on the page asked for: it fades in under the tab screen, the pill morphs with it
            root.xgramSetPageSilently(calls);
            navigation.navigateBack();
          }
          return;
        }
        // Folders: the chat list's pages are the folders, so Calls is a tab screen
        ViewController<?> callList = warmScreen(DEST_CALLS);
        open(DEST_CALLS, callList != null ? callList : new CallListController(context, tdlib), drawer);
        if (badges != null) {
          badges.markCallsSeen();
        }
        return;
      }
      case DEST_CONTACTS: {
        tdlib.contacts().startSyncIfNeeded(context, true, () -> {
          if (tdlib == this.tdlib && canNavigate(drawer)) {
            ViewController<?> c = warmScreen(DEST_CONTACTS);
            if (c == null) {
              PeopleController people = new PeopleController(context, tdlib);
              people.setNeedSearch();
              c = people;
            }
            open(DEST_CONTACTS, c, drawer);
          }
        });
        return;
      }
      case DEST_SETTINGS: {
        ViewController<?> settings = warmScreen(DEST_SETTINGS);
        open(DEST_SETTINGS, settings != null ? settings : new SettingsController(context, tdlib), drawer);
        return;
      }
      case DEST_PROFILE: {
        openProfile(drawer);
        return;
      }
      case DEST_ACTIVITY: {
        ViewController<?> activity = warmScreen(DEST_ACTIVITY);
        open(DEST_ACTIVITY, activity != null ? activity : new com.fahadali.xgram.activity.XgramActivityController(context, tdlib), drawer);
        return;
      }
    }
  }

  /**
   * A tab screen right above the chat list: it fades in over what is there (which goes once the fade ends), and the
   * selection moves with the fade (MODE_REBASE | SAVE_FIRST | MODE_FADE).
   */
  private void open (int dest, ViewController<?> c, boolean drawer) {
    if (!canNavigate(drawer) || navigation == null) {
      return;
    }
    c.xgramSetBarTab(dest); // before its view is made: no back arrow, its margins and room as a tab screen
    keepWarm(dest, c); // kept when another tab or the chat list replaces it, shown again next time
    // The fade starts at once (NavigationController skips a tab screen's wait for its data) and the selection moves
    // with it, in this frame (onXgramTransitionBegin)
    navigation.setControllerAnimated(c, false, true);
  }

  // Warm tab screens

  /** The live screen of {@code dest} to show again; null when there is none (or it can no longer be shown). */
  @Nullable
  private ViewController<?> warmScreen (int dest) {
    ViewController<?> c = warm[dest];
    if (c == null) {
      return null;
    }
    if (c.isDestroyed() || c.tdlib() != tdlib || c.xgramBarTabRaw() != dest || navigation == null || navigation.getStack().indexOf(c) != -1) {
      forgetWarm(dest); // gone, another account, or (never expected) on the stack already: a new one
      return null;
    }
    View view = c.getWrapUnchecked();
    if (view != null && view.getParent() instanceof ViewGroup) {
      ((ViewGroup) view.getParent()).removeView(view); // never expected: the navigation removes a screen it hides
    }
    return c;
  }

  private void keepWarm (int dest, ViewController<?> c) {
    if (warm[dest] != c) {
      forgetWarm(dest);
      warm[dest] = c;
    }
    c.xgramSetKeepWarm(true);
  }

  /** Lets go of the screen of {@code dest}: destroyed when it is off the stack; on it, the stack destroys it as usual. */
  private void forgetWarm (int dest) {
    ViewController<?> c = warm[dest];
    if (c == null) {
      return;
    }
    warm[dest] = null;
    c.xgramSetKeepWarm(false);
    if (!c.isDestroyed() && navigation != null) {
      navigation.xgramDestroyDetached(c);
    }
  }

  private void dropWarm () {
    UI.cancel(dropDetachedRunnable);
    for (int dest = 0; dest < warm.length; dest++) {
      forgetWarm(dest);
    }
  }

  /** The window changed size: the warm screens off the stack go (made again for the new size when asked for). */
  private void dropDetachedWarm () {
    if (navigation == null) {
      return;
    }
    for (int dest = 0; dest < warm.length; dest++) {
      ViewController<?> c = warm[dest];
      if (c != null && (c.isDestroyed() || navigation.getStack().indexOf(c) == -1)) {
        forgetWarm(dest);
      }
    }
  }

  /**
   * A setting the tab screens read once changed (Show peer IDs, Hide phone): the ones off the stack go; the one on the
   * stack is no longer kept (warm[dest] stays, so adoptWarm does not keep it again): the stack destroys it when it
   * leaves, warmScreen() then sees isDestroyed() and a new one is made.
   */
  private void dropWarmForContent () {
    if (destroyed || navigation == null) {
      return;
    }
    for (int dest = 0; dest < warm.length; dest++) {
      ViewController<?> c = warm[dest];
      if (c == null) {
        continue;
      }
      if (!c.isDestroyed() && navigation.getStack().indexOf(c) != -1) {
        c.xgramSetKeepWarm(false);
      } else {
        forgetWarm(dest);
      }
    }
  }

  /**
   * A tab screen on top that is not the kept one of its destination (it was on screen while the toolbar was hidden,
   * on a fold): kept from now on, so the next switch away does not destroy it.
   */
  private void adoptWarm (ViewController<?> c) {
    int dest = c.xgramBarTabRaw();
    if (dest < 0 || dest >= warm.length || warm[dest] == c || c.isDestroyed() || c.tdlib() != tdlib || (!hasTab(dest) && !(dest == DEST_PROFILE && buttonMode))) {
      return;
    }
    keepWarm(dest, c);
  }

  /** The tabs changed: a kept screen whose destination is no longer a tab (nor the profile button's) goes. */
  private void dropStaleWarm () {
    for (int dest = 0; dest < warm.length; dest++) {
      if (warm[dest] != null && !hasTab(dest) && !(dest == DEST_PROFILE && buttonMode)) {
        forgetWarm(dest);
      }
    }
  }

  private void openProfile (boolean drawer) {
    final Tdlib tdlib = this.tdlib;
    if (tdlib == null) {
      return;
    }
    final long userId = tdlib.myUserId();
    if (userId == 0) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      openProfile(tdlib, chat, drawer);
      return;
    }
    tdlib.send(new TdApi.CreatePrivateChat(userId, true), (remoteChat, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        final long chatId = remoteChat.id;
        UI.post(() -> {
          TdApi.Chat created = tdlib.chat(chatId);
          if (created != null && tdlib == this.tdlib) {
            openProfile(tdlib, created, drawer);
          }
        });
      }
    });
  }

  private void openProfile (Tdlib tdlib, TdApi.Chat chat, boolean drawer) {
    if (!canNavigate(drawer)) {
      return;
    }
    ViewController<?> c = warmScreen(DEST_PROFILE);
    if (c == null || c.getChatId() != chat.id) {
      ProfileController profile = new ProfileController(context, tdlib);
      profile.setArguments(new ProfileController.Args(chat, null, false));
      c = profile;
    }
    open(DEST_PROFILE, c, drawer);
  }

  /** Saved messages is an action: the chat opens over what is on screen (the toolbar leaves with it). */
  private void openSaved (@Nullable ViewController<?> from) {
    final Tdlib tdlib = this.tdlib;
    if (tdlib == null || from == null) {
      return;
    }
    final long userId = tdlib.myUserId();
    if (userId == 0) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      tdlib.ui().openChat(from, chat.id, new TdlibUi.ChatOpenParameters().keepStack()); // a normal push (4.3)
      return;
    }
    tdlib.send(new TdApi.CreatePrivateChat(userId, true), (remoteChat, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        final long chatId = remoteChat.id;
        UI.post(() -> {
          if (!from.isDestroyed()) {
            tdlib.ui().openChat(from, chatId, new TdlibUi.ChatOpenParameters().keepStack());
          }
        });
      }
    });
  }

  private boolean onTabLongClick (int dest) {
    if (preview || destroyed) {
      return false;
    }
    Tab tab = tabOf(dest);
    if (tab != null && isFoldedAway(tab)) {
      return true; // folded into another tab: no menu and no tooltip for a tab out of sight
    }
    if (dest == DEST_PROFILE) {
      return showAccounts();
    }
    if (dest == DEST_CHATS && canNavigate(false) && navigation.getCurrentStackItem() == root) {
      // The Chats tab's filters (Unread, Groups, Archive...) had only its slide-off menu: while there are no folders
      return root.xgramShowRailFilters();
    }
    return false; // the tooltip
  }

  /**
   * Long press on the photo: the accounts (the current one highlighted, the others with their unread count, as on the
   * rail), Manage accounts (two or more) and Add account.
   */
  private boolean showAccounts () {
    if (preview || destroyed || navigation == null || navigation.isAnimating()) {
      return false;
    }
    final ViewController<?> top = navigation.getCurrentStackItem();
    final Tdlib tdlib = this.tdlib;
    if (top == null || tdlib == null) {
      return false;
    }
    final ArrayList<TdlibAccount> accounts = new ArrayList<>(TdlibManager.instance().getActiveAccounts());
    final int count = accounts.size();
    final boolean manage = count >= 2;
    final int size = count + (manage ? 2 : 1);
    int[] ids = new int[size];
    String[] titles = new String[size];
    int[] colors = new int[size];
    int[] icons = new int[size];
    final int[] unreadCounts = new int[count];
    for (int i = 0; i < count; i++) {
      TdlibAccount account = accounts.get(i);
      boolean current = account.id == account.context().preferredAccountId();
      ids[i] = ACCOUNT_OPTION_ID + i;
      titles[i] = Lang.getDebugString(account.getName(), account.isDebug());
      if (!current) {
        org.thunderdog.challegram.telegram.TdlibBadgeCounter badge = account.getUnreadBadge();
        int unread = badge != null ? badge.getCount() : 0;
        unreadCounts[i] = unread; // a badge at the row's end, not a number after the name
      }
      colors[i] = current ? ViewController.OptionColor.BLUE : ViewController.OptionColor.NORMAL;
      icons[i] = 0; // the account's photo instead (below), like official Telegram
    }
    int row = count;
    if (manage) {
      // The side menu's drag to reorder and its preview's Log out, while the side menu is hidden
      ids[row] = R.id.btn_xgramNavManageAccounts;
      titles[row] = Lang.getString(R.string.XgramNavAccountsManage);
      colors[row] = ViewController.OptionColor.NORMAL;
      icons[row] = R.drawable.baseline_settings_24;
      row++;
    }
    ids[row] = R.id.btn_addAccount;
    titles[row] = Lang.getString(R.string.XgramNavRailAddAccount);
    colors[row] = ViewController.OptionColor.NORMAL;
    icons[row] = R.drawable.baseline_add_24;
    final ViewController.OptionItem[] items = new ViewController.OptionItem[size];
    for (int i = 0; i < size; i++) {
      items[i] = new ViewController.OptionItem(ids[i], titles[i], colors[i], icons[i]);
      if (i < count) {
        items[i].xgramLeading = com.fahadali.xgram.appearance.XgramOptionDrawables.accountAvatar(context, accounts.get(i));
        if (unreadCounts[i] > 0) {
          items[i].xgramTrailing = com.fahadali.xgram.appearance.XgramOptionDrawables.unreadBadge(context, unreadCounts[i]);
        }
      }
    }
    top.showOptions(new ViewController.Options(null, null, null, items, org.thunderdog.challegram.util.text.Text.LINE_COUNT_UNLIMITED), (itemView, id) -> {
      if (id == R.id.btn_addAccount) {
        tdlib.ui().addAccount(context, true, false);
      } else if (id == R.id.btn_xgramNavManageAccounts) {
        showManageAccounts(top);
      } else {
        int index = id - ACCOUNT_OPTION_ID;
        if (index >= 0 && index < accounts.size()) {
          TdlibAccount account = accounts.get(index);
          if (account.id != account.context().preferredAccountId()) {
            account.context().changePreferredAccountId(account.id, TdlibManager.SWITCH_REASON_USER_CLICK);
          }
        }
      }
      return true;
    });
    return true;
  }

  /** Manage accounts from the chat list's overflow button (no photo on the toolbar to long press). */
  public boolean showManageAccountsMenu () {
    if (destroyed || preview || !shown || navigation == null || navigation.isAnimating()) {
      return false;
    }
    ViewController<?> top = navigation.getCurrentStackItem();
    return top != null && showManageAccounts(top);
  }

  /**
   * Manage accounts: what the hidden side menu did with its account rows (drag to reorder, the preview's Log out). An
   * account, then Move up, Move down or Log out.
   */
  private boolean showManageAccounts (final ViewController<?> top) {
    if (destroyed || preview || top.isDestroyed()) {
      return false;
    }
    final ArrayList<TdlibAccount> accounts = new ArrayList<>(TdlibManager.instance().getActiveAccounts());
    final int count = accounts.size();
    if (count == 0) {
      return false;
    }
    int[] ids = new int[count];
    String[] titles = new String[count];
    int[] colors = new int[count];
    int[] icons = new int[count];
    for (int i = 0; i < count; i++) {
      TdlibAccount account = accounts.get(i);
      ids[i] = ACCOUNT_OPTION_ID + i;
      titles[i] = Lang.getDebugString(account.getName(), account.isDebug());
      colors[i] = account.id == account.context().preferredAccountId() ? ViewController.OptionColor.BLUE : ViewController.OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_account_circle_24;
    }
    top.showOptions(Lang.getString(R.string.XgramNavAccountsManage), ids, titles, colors, icons, (itemView, id) -> {
      int index = id - ACCOUNT_OPTION_ID;
      if (index >= 0 && index < accounts.size()) {
        showAccountActions(top, accounts.get(index));
      }
      return true;
    });
    return true;
  }

  /** One account of Manage accounts: Move up, Move down (saved at once, as the side menu's drag does) and Log out. */
  private void showAccountActions (final ViewController<?> top, final TdlibAccount account) {
    if (destroyed || top.isDestroyed()) {
      return;
    }
    final ArrayList<TdlibAccount> active = TdlibManager.instance().getActiveAccounts();
    final int index = active.indexOf(account);
    if (index == -1) {
      return;
    }
    final int count = active.size();
    final int size = (index > 0 ? 1 : 0) + (index < count - 1 ? 1 : 0) + 1;
    int[] ids = new int[size];
    String[] titles = new String[size];
    int[] colors = new int[size];
    int[] icons = new int[size];
    int row = 0;
    if (index > 0) {
      ids[row] = R.id.btn_xgramNavAccountUp;
      titles[row] = Lang.getString(R.string.XgramComposeMoveUp);
      colors[row] = ViewController.OptionColor.NORMAL;
      icons[row] = R.drawable.baseline_arrow_upward_24;
      row++;
    }
    if (index < count - 1) {
      ids[row] = R.id.btn_xgramNavAccountDown;
      titles[row] = Lang.getString(R.string.XgramComposeMoveDown);
      colors[row] = ViewController.OptionColor.NORMAL;
      icons[row] = R.drawable.baseline_arrow_downward_24;
      row++;
    }
    ids[row] = R.id.btn_removeAccount;
    titles[row] = Lang.getString(R.string.LogOut);
    colors[row] = ViewController.OptionColor.RED;
    icons[row] = R.drawable.baseline_logout_24;
    top.showOptions(Lang.getDebugString(account.getName(), account.isDebug()), ids, titles, colors, icons, (itemView, id) -> {
      if (top.isDestroyed()) {
        return true;
      }
      if (id == R.id.btn_xgramNavAccountUp || id == R.id.btn_xgramNavAccountDown) {
        TdlibManager manager = TdlibManager.instance();
        int from = manager.getActiveAccounts().indexOf(account);
        int to = from + (id == R.id.btn_xgramNavAccountUp ? -1 : 1);
        if (from != -1 && to >= 0 && to < manager.getActiveAccounts().size()) {
          manager.moveAccount(from, to);
          manager.saveAccountOrders(); // as the side menu does when a drag ends: the order is kept
          showManageAccounts(top); // the new order, ready for the next move
        }
      } else if (id == R.id.btn_removeAccount) {
        TdlibUi.removeAccount(top, account); // asks first
      }
      return true;
    });
  }

  private void onProfileClick (View view) {
    if (preview) {
      if (selectedDest != DEST_PROFILE) {
        ViewUtils.onClick(view);
        setTarget(DEST_PROFILE, true);
        if (compactSpring.getTarget() >= 1f) {
          endPreviewDemo(); // shrunk by its demo: the profile brings the bar back, as on the real one
        }
      }
      return;
    }
    if (!canNavigate(false) || selectedDest == DEST_PROFILE && navigation.getCurrentStackItem() != root) {
      return; // the profile is on screen already
    }
    ViewUtils.onClick(view);
    openProfile(false);
  }

  // Avatars and descriptions

  private void requestAvatars () {
    Tdlib tdlib = this.tdlib;
    profile.requestAvatar(tdlib);
    for (Tab tab : tabs) {
      tab.requestAvatar(tdlib);
    }
  }

  private void updateProfileDescription () {
    String name = null;
    try {
      if (tdlib != null) {
        name = tdlib.account().getName();
      }
    } catch (Throwable ignored) { }
    profile.setContentDescription(name != null && !name.isEmpty() ? Lang.getString(R.string.XgramNavProfileA11y, name) : Lang.getString(R.string.XgramNavProfileLabel));
  }

  private void invalidateAll () {
    host.invalidate();
    pill.invalidate();
    for (Tab tab : tabs) {
      tab.view.invalidate();
    }
    profile.invalidate();
  }

  // Colours (6.1), read when drawn

  private static int color (int role) {
    return XgramExpressive.color(role);
  }

  private int containerColor () {
    return color(containerRole(vibrant));
  }

  private int indicatorColor () {
    return color(vibrant ? XgramExpressive.PRIMARY : XgramExpressive.SECONDARY_CONTAINER);
  }

  private int restContentColor () {
    return color(vibrant ? XgramExpressive.ON_PRIMARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
  }

  private int selectedContentColor () {
    return color(vibrant ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_SECONDARY_CONTAINER);
  }

  private int stateColor (boolean selected) {
    if (vibrant) {
      return color(selected ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_PRIMARY_CONTAINER);
    }
    return color(selected ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
  }

  private int mutedDotColor () {
    return color(vibrant ? XgramExpressive.ON_PRIMARY_CONTAINER : XgramExpressive.OUTLINE);
  }

  // Icon paints: a colour filter each, rebuilt only when their colour changes
  @Nullable private Paint restIconPaint, activeIconPaint;
  private int restIconColor, activeIconColor;

  private void updateIconPaints () {
    int rest = restContentColor(), active = selectedContentColor();
    if (restIconPaint == null || rest != restIconColor) {
      restIconPaint = Paints.createPorterDuffPaint(restIconPaint, restIconColor = rest);
    }
    if (activeIconPaint == null || active != activeIconColor) {
      activeIconPaint = Paints.createPorterDuffPaint(activeIconPaint, activeIconColor = active);
    }
  }

  // Views

  /** A tab: its destination, label, drawables, badge and springs (8.1). */
  private final class Tab {
    final int dest;
    final TabView view;
    @Nullable Drawable rest, active;
    /** The profile as a tab: the account's photo instead of the icons. */
    @Nullable AvatarReceiver avatar;
    /** The profile as a tab: a swipe on its photo switches to the next or previous account. */
    @Nullable XgramAccountSwipe swipe;
    String label = "";
    private float labelWidthLarge = -1f, labelWidthMedium = -1f;
    String shownLabel = "";
    float shownLabelWidth;
    /** Width when selected, in px. */
    float s;
    /** This frame's geometry, in the pill: the item, the icon centre. */
    float left, right, iconX;
    /** This frame's opacity and scale of its icon and badge: below 1 as the bar folds into another tab (Collapse). */
    float opacity = 1f, scale = 1f;

    int badgeCount;
    boolean badgeDot;
    /** What the badge looks like while it springs away: 0 none, 1 a count, 2 the muted dot. */
    int drawKind;
    @Nullable String drawText;
    float drawTextWidth;

    /** E: width, position and the leading edge; T: the trailing edge; C: the icon crossfade; P: press; Q: pop. */
    final XgramExpressive.Spring e, t, c, p, q, badge;

    Tab (int dest) {
      this.dest = dest;
      this.e = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, frameListener).setThreshold(.001f);
      this.t = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, frameListener).setThreshold(.001f);
      this.c = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, frameListener).setThreshold(.001f);
      this.p = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, frameListener).setThreshold(.001f);
      this.q = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, frameListener).setThreshold(.001f);
      this.badge = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, frameListener).setThreshold(.001f);
      this.view = new TabView(context, this);
      if (dest == DEST_PROFILE) {
        avatar = new AvatarReceiver(pill);
        swipe = new XgramAccountSwipe(view, pill, TAB_AVATAR, SWIPE_COMMIT, !preview, new SwipeHost() {
          @Nullable
          @Override
          public AvatarReceiver replaceAvatar (AvatarReceiver incoming) {
            AvatarReceiver previous = Tab.this.avatar;
            Tab.this.avatar = incoming;
            return previous;
          }
        });
        if (androidx.core.view.ViewCompat.isAttachedToWindow(pill)) {
          avatar.attach();
        }
        requestAvatar(tdlib);
      } else {
        loadIcons();
      }
      updateLabel();
    }

    /** Two separate drawables (never one instance drawn twice in a frame): XgramIconCrossfade. */
    void loadIcons () {
      if (dest == DEST_PROFILE) {
        return;
      }
      Resources res = context.getResources();
      rest = XgramNavRail.restIcon(res, XgramNavRail.filledOf(dest), XgramNavRail.outlinedOf(dest));
      active = XgramNavRail.activeIcon(res, XgramNavRail.filledOf(dest), XgramNavRail.outlinedOf(dest));
      view.invalidate();
    }

    void requestAvatar (@Nullable Tdlib tdlib) {
      if (avatar == null) {
        return;
      }
      if (swipe != null && swipe.onBind(tdlib != null ? tdlib.id() : TdlibAccount.NO_ID)) {
        return; // the photo slid in with a swipe already: no flash of the old one
      }
      if (tdlib != null) {
        avatar.requestAccount(tdlib, tdlib.id(), AvatarReceiver.Options.NONE);
      } else {
        avatar.clear();
      }
    }

    void updateLabel () {
      String label = Lang.getString(DEST_TAB_LABEL[dest]);
      if (!label.equals(this.label)) {
        this.label = label;
        this.labelWidthLarge = this.labelWidthMedium = -1f;
        fitDirty = true;
      }
      updateDescription();
    }

    float labelWidth (int style) {
      if (style == STYLE_DENSE) {
        if (labelWidthMedium < 0f) {
          labelWidthMedium = labelMedium.measureText(label);
        }
        return labelWidthMedium;
      }
      if (labelWidthLarge < 0f) {
        labelWidthLarge = labelLarge.measureText(label);
      }
      return labelWidthLarge;
    }

    void updateDescription () {
      String description = dest == DEST_PROFILE ? Lang.getString(R.string.XgramNavProfileLabel) : XgramNavRail.describe(dest, badgeCount, badgeDot);
      view.setContentDescription(description);
    }

    /**
     * Unselected tabs (and every tab while labels don't fit, or the bar shrinks to its icons) tell their name in a
     * tooltip (Android 8+); not the ones folded away into another tab (out of sight).
     */
    void updateTooltip () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        boolean selected = indexOf(dest) == selectedIndex && selectedIndex != -1;
        boolean compact = compactSpring.getTarget() >= 1f;
        boolean shrunk = compact && !collapsesToTab(), foldedAway = compact && collapsesToTab() && !selected;
        view.setTooltipText(foldedAway || (selected && !iconOnly && !shrunk) ? null : Lang.getString(DEST_TITLE[dest]));
      }
    }

    void setSelectedState (boolean selected) {
      if (view.isSelected() != selected) {
        view.setSelected(selected);
      }
      updateTooltip();
      view.invalidate();
    }

    void setBadge (int count, boolean dot) {
      count = Math.max(0, count);
      dot = dot && count == 0;
      if (badgeCount == count && badgeDot == dot) {
        return;
      }
      badgeCount = count;
      badgeDot = dot;
      if (count > 0) {
        drawKind = 1;
        drawText = XgramNavRail.badgeText(count);
        drawTextWidth = drawText != null ? badgePaint.measureText(drawText) : 0f;
      } else if (dot) {
        drawKind = 2;
      }
      float target = count > 0 || dot ? 1f : 0f;
      if (isVisibleNow()) {
        badge.animateTo(target);
      } else {
        badge.setValue(target);
      }
      updateDescription();
      pill.markDirty();
    }

    void destroy () {
      e.cancel();
      t.cancel();
      c.cancel();
      p.cancel();
      q.cancel();
      badge.cancel();
      if (swipe != null) {
        swipe.destroy();
        swipe = null;
      }
      if (avatar != null) {
        avatar.destroy();
        avatar = null;
      }
    }

    void snap () {
      e.setValue(e.getTarget());
      t.setValue(t.getTarget());
      c.setValue(c.getTarget());
      p.setValue(0f);
      q.setValue(1f);
      badge.setValue(badge.getTarget());
    }
  }

  /**
   * The host: as wide as the navigation, from 16dp above the pill to the window's bottom (the navigation bar
   * included). It draws the band behind the group, cuts out a predictive back card and takes touches only on the
   * pill, the button and the band's solid part (11.2).
   */
  public final class HostView extends FrameLayout {
    private static final int TOUCH_NONE = 0, TOUCH_CHILDREN = 1, TOUCH_SWALLOW = 2;

    private final Paint bandPaint = new Paint(Paint.DITHER_FLAG);
    private final BandSlot[] slots = {new BandSlot(), new BandSlot(), new BandSlot()};
    private int nextSlot;
    @Nullable private Path hole;
    private float holeAlpha;
    @Nullable private Paint holePaint;
    private int touchMode;
    // The chat list's compose button where it is over the band (fabOverBand)
    private final RectF fabRect = new RectF();
    private final Path fabPath = new Path();
    private final int[] location = new int[2];

    HostView (Context context) {
      super(context);
      setWillNotDraw(false);
      setClipChildren(false);
      setClipToPadding(false);
      setMotionEventSplittingEnabled(false);
    }

    /** The settings page changed something: the preview follows (and the real toolbar through XgramTwoPane#update). */
    public void sync () {
      if (destroyed || !preview) {
        return;
      }
      int wasScrollMode = scrollMode;
      readSettings();
      int[] next = tabDests();
      rebuildTabs(next);
      requestFit(true);
      requestAvatars();
      if (scrollMode != wasScrollMode) {
        playPreviewDemo(); // it never scrolls: the new choice plays once (Shrink, Collapse)
      }
    }

    float getHeightForHide () {
      return getHeight() > 0 ? getHeight() : hostHeight();
    }

    void setCardHole (@Nullable Path hole, float alpha) {
      if (this.hole == null && hole == null) {
        return;
      }
      this.hole = hole;
      this.holeAlpha = hole != null ? MathUtils.clamp(alpha) : 0f;
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? (preview ? listRoom() : hostHeight()) : MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      ensureFit(width);
      pill.measure(MeasureSpec.makeMeasureSpec(Math.round(pillWidthNow()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pillHeightPx(), MeasureSpec.EXACTLY));
      int buttonSize = Screen.dp(BUTTON) + 2 * Screen.dp(BUTTON_HIT_SLOP);
      profile.measure(MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      ensureFit(right - left);
      layoutGroup();
      applyHide();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      if (preview) {
        ThemeManager.instance().addThemeListener(themeListener);
        invalidateAll();
      }
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      if (preview) {
        ThemeManager.instance().removeThemeListener(themeListener);
        // Nothing keeps running off screen; back in place when it comes back
        for (Tab tab : tabs) {
          tab.snap();
        }
        profile.snap();
        widthSpring.setValue(widthSpring.getTarget());
        leftSpring.setValue(leftSpring.getTarget());
        // A demo of the choice for scrolling stops: in full when it comes back (not left compact with no end to come)
        cancelPreviewDemo();
        compactSpring.setValue(0f);
        layoutGroup();
        onCompactSettled();
      }
    }

    // Band (6.3)

    private final class BandSlot {
      int color, height, mid, end;
      @Nullable LinearGradient shader;
    }

    /** The cached gradient of {@code color} for this height: rebuilt only when the colour or the height changes. */
    private LinearGradient shaderFor (int color, int height, int mid, int end) {
      for (BandSlot slot : slots) {
        if (slot.shader != null && slot.color == color && slot.height == height && slot.mid == mid && slot.end == end) {
          return slot.shader;
        }
      }
      BandSlot slot = slots[nextSlot];
      nextSlot = (nextSlot + 1) % slots.length;
      int opaque = color | 0xff000000;
      float h = Math.max(1, height);
      slot.color = color;
      slot.height = height;
      slot.mid = mid;
      slot.end = end;
      slot.shader = new LinearGradient(0f, 0f, 0f, h,
        new int[] {opaque & 0x00ffffff, XgramExpressive.withAlpha(opaque, BAND_MID_ALPHA), opaque, opaque},
        new float[] {0f, MathUtils.clamp(mid / h), MathUtils.clamp(end / h), 1f},
        Shader.TileMode.CLAMP);
      return slot.shader;
    }

    /**
     * The lower of two band layers' alpha, with the upper one at {@code band * upper}: the pair is then the band faded to
     * {@code band} (the lower colour never shows through as it fades). Exactly 1 at band 1, as before.
     */
    private float lowerBandAlpha (float band, float upper) {
      final float over = band * upper;
      return over >= .999f ? 1f : band * (1f - upper) / (1f - over);
    }

    private void drawBandLayer (Canvas c, int color, float alpha, int mid, int end) {
      if (alpha <= 0f) {
        return;
      }
      bandPaint.setShader(shaderFor(color, getHeight(), mid, end));
      bandPaint.setAlpha(Math.round(255f * MathUtils.clamp(alpha)));
      c.drawRect(0, 0, getWidth(), getHeight(), bandPaint);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (preview || inline || !shown || getHeight() <= 0) {
        return;
      }
      // The full pill's geometry, whatever its size now: the shrunk pill floats with its shadow, the band fades out
      // (its gradients stay cached: only the alpha moves)
      int top = pillTop(getHeight());
      int mid = top + Screen.dp(HEIGHT / 2f), end = top + Screen.dp(HEIGHT);
      float band = MathUtils.clamp(1f - compactNow());
      if (band <= 0f) {
        return;
      }
      if (!fabOverBand()) {
        drawBand(c, band, mid, end);
        return;
      }
      // The compose button going onto the compact bar's row passes through the band, which is drawn over it: its shape
      // is left out, so it is never washed with the page's colour. It is opaque, so nothing else changes; its shadow
      // stays under the band (a ring cut around it would show the rows unfaded).
      final int save = c.save();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        c.clipOutPath(fabPath);
      } else {
        c.clipPath(fabPath, Region.Op.DIFFERENCE);
      }
      drawBand(c, band, mid, end);
      c.restoreToCount(save);
    }

    /**
     * The chat list's compose button overlaps this view: fabPath is its container, in this view's coordinates. Read
     * while drawing, so it is where the FAB is now; the toolbar's compact frames (when it moves onto the row) redraw it.
     */
    private boolean fabOverBand () {
      final MainController root = XgramNavToolbar.this.root;
      if (root == null) {
        return false;
      }
      final float radius = root.xgramFabInWindow(fabRect);
      if (radius < 0f) {
        return false;
      }
      getLocationInWindow(location);
      fabRect.offset(-location[0], -location[1]);
      if (!fabRect.intersects(0f, 0f, getWidth(), getHeight())) {
        return false;
      }
      fabPath.rewind();
      fabPath.addRoundRect(fabRect, radius, radius, Path.Direction.CW);
      return true;
    }

    /** The band: the page's colour fading in above the pill, faded to {@code band} as the bar gets compact. */
    private void drawBand (Canvas c, float band, int mid, int end) {
      if (transitionActive && tabL && tabR) {
        // A fade between two tab screens: the band of the one it leaves, then the other's over it
        boolean forward = tForward;
        ViewController<?> from = forward ? tLeft : tRight, to = forward ? tRight : tLeft;
        float progress = transitionProgress();
        drawBandLayer(c, bandColorOf(from), lowerBandAlpha(band, progress), mid, end);
        drawBandLayer(c, bandColorOf(to), progress * band, mid, end);
        return;
      }
      ViewController<?> subject = transitionActive ? (tabL ? tLeft : tRight) : (navigation != null ? navigation.getCurrentStackItem() : null);
      if (subject != null && subject == root && root != null) {
        // The chat list: its two pages' colours, crossfaded by the pager (no new gradient per frame)
        float calls = root.xgramCallsFraction();
        drawBandLayer(c, Theme.getColor(ColorId.filling), lowerBandAlpha(band, calls), mid, end);
        drawBandLayer(c, Theme.getColor(ColorId.background), calls * band, mid, end);
        return;
      }
      if (subject != null) {
        drawBandLayer(c, bandColorOf(subject), band, mid, end);
      }
    }

    private int bandColorOf (@Nullable ViewController<?> c) {
      if (c == null) {
        return Theme.getColor(ColorId.background);
      }
      return c.xgramBarBandColor();
    }

    // Card hole (9.3), as HeaderView's

    @Override
    public void draw (Canvas c) {
      if (hole != null && holeAlpha > 0f) {
        if (holePaint == null) {
          holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        holePaint.setColor(Color.argb(Math.round(255f * holeAlpha), 0, 0, 0));
        int save = c.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        super.draw(c);
        c.drawPath(hole, holePaint);
        c.restoreToCount(save);
        return;
      }
      super.draw(c);
    }

    // Touches (11.2)

    private boolean inPill (float x, float y) {
      float l = pill.getLeft(), t = pill.getTop(), r = pill.getRight(), b = pill.getBottom();
      if (x < l || x >= r || y < t || y >= b) {
        return false;
      }
      float radius = (b - t) / 2f;
      float cx = x < l + radius ? l + radius : x > r - radius ? r - radius : x;
      float cy = t + radius;
      float dx = x - cx, dy = y - cy;
      return dx * dx + dy * dy <= radius * radius;
    }

    private boolean inButton (float x, float y) {
      if (!buttonMode || profile.getVisibility() != View.VISIBLE) {
        return false;
      }
      if (profile.getAlpha() <= 0f || (collapsesToTab() && compactSpring.getTarget() >= 1f)) {
        return false; // folding into one tab, or out of sight as the bar comes back: the rows under it take the touch
      }
      float cx = (profile.getLeft() + profile.getRight()) / 2f, cy = (profile.getTop() + profile.getBottom()) / 2f;
      float radius = Screen.dpf(BUTTON / 2f + BUTTON_HIT_SLOP) * profile.getScaleX(); // scaled around its centre beside the shrunk bar
      float dx = x - cx, dy = y - cy;
      return dx * dx + dy * dy <= radius * radius;
    }

    @Override
    public boolean dispatchTouchEvent (MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        touchMode = TOUCH_NONE;
        if (getVisibility() != View.VISIBLE || getAlpha() <= 0f) {
          return false;
        }
        float x = e.getX(), y = e.getY();
        if (inPill(x, y) || inButton(x, y)) {
          touchMode = TOUCH_CHILDREN;
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true); // the drawer's and the pages' swipes stay off
          }
          return super.dispatchTouchEvent(e);
        }
        if (!preview && !inline && compactSpring.getTarget() < 1f && y >= pillTop(getHeight()) + Screen.dp(HEIGHT / 2f)) {
          // The band's solid part: the rows there are covered (not beside the compact pill: the band fades out)
          touchMode = TOUCH_SWALLOW;
          return true;
        }
        return false;
      }
      switch (touchMode) {
        case TOUCH_CHILDREN:
          return super.dispatchTouchEvent(e);
        case TOUCH_SWALLOW:
          return true;
        default:
          return false;
      }
    }
  }

  /**
   * The pill: the container, the inner pill (the selection), then a view per tab (its touch area and state layer),
   * then the icons, labels and badges. Its tabs are laid out again before every frame a spring moved (no layout pass).
   */
  private final class PillView extends ViewGroup {
    private final RectF rect = new RectF(), layerRect = new RectF();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean layoutDirty = true;
    @Nullable private ViewTreeObserver observer;
    /** This frame's inner pill (in the pill) and its alpha (how selected anything is). */
    float innerL, innerR, innerAlpha, innerCorner;
    boolean innerVisible;
    private boolean reportedSumMismatch;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
      if (layoutDirty) {
        relayoutItems();
      }
      return true;
    };

    PillView (Context context) {
      super(context);
      setWillNotDraw(false);
      setClipChildren(false);
      setClipToPadding(false);
      setMotionEventSplittingEnabled(false);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
        setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
          }
        });
      }
    }

    void markDirty () {
      layoutDirty = true;
      invalidate();
    }

    void updateAccessibility () {
      for (int i = 0; i < tabs.length; i++) {
        tabs[i].updateDescription();
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      observer = getViewTreeObserver();
      observer.addOnPreDrawListener(preDrawListener);
      for (Tab tab : tabs) {
        if (tab.avatar != null) {
          tab.avatar.attach();
        }
        if (tab.swipe != null) {
          tab.swipe.attach();
        }
      }
      layoutDirty = true;
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      ViewTreeObserver observer = this.observer;
      if (observer != null && observer.isAlive()) {
        observer.removeOnPreDrawListener(preDrawListener);
      } else {
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
      }
      this.observer = null;
      for (Tab tab : tabs) {
        if (tab.avatar != null) {
          tab.avatar.detach();
        }
        if (tab.swipe != null) {
          tab.swipe.detach();
        }
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      layoutDirty = true;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      for (Tab tab : tabs) {
        int itemWidth = Math.max(1, Math.round(tab.right - tab.left));
        tab.view.measure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      relayoutItems();
    }

    /** 8.2: the tabs' widths and places from the springs (they add up to the content width), the inner pill. */
    void relayoutItems () {
      layoutDirty = false;
      final int n = tabs.length;
      final int width = getWidth(), height = getHeight();
      if (n == 0 || width <= 0) {
        innerVisible = false;
        return;
      }
      final float pad = Screen.dpf(PAD);
      final float content = width - 2f * pad;
      // Shrinking to the icons, the selected width goes to the compact one with the pill's (they still add up)
      final float k = shrinkNow(), uC = fitUC;
      float sumE = 0f, sumES = 0f;
      for (Tab tab : tabs) {
        float e = tab.e.getValue();
        sumE += e;
        sumES += e * (tab.s + (uC - tab.s) * k);
      }
      float bw = Math.max(0f, (content - sumES) / Math.max(.5f, n - sumE));
      final boolean rtl = Lang.rtl();
      float x = pad;
      for (int slot = 0; slot < n; slot++) {
        Tab tab = tabs[rtl ? n - 1 - slot : slot];
        float sEff = tab.s + (uC - tab.s) * k;
        float itemWidth = bw + tab.e.getValue() * (sEff - bw);
        tab.left = x;
        tab.right = x + itemWidth;
        x = tab.right;
      }
      if (org.thunderdog.challegram.BuildConfig.DEBUG && !reportedSumMismatch && Math.abs((x - pad) - content) > .5f && bw > 0f) {
        reportedSumMismatch = true;
        android.util.Log.w("XGram", "navigation toolbar: tab widths add up to " + (x - pad) + ", not " + content);
      }
      // Rounded at the shared edges, so there is no gap; the first and the last take the end padding as touch area
      for (int slot = 0; slot < n; slot++) {
        Tab tab = tabs[rtl ? n - 1 - slot : slot];
        int left = slot == 0 ? 0 : Math.round(tab.left);
        int right = slot == n - 1 ? width : Math.round(tab.right);
        tab.view.layout(left, 0, Math.max(left, right), height);
        tab.view.invalidate();
      }

      // Inner pill: the fast edges (E) and the slow ones (T); it spans both, so it stretches towards the new tab and
      // its tail catches up (also when a change is interrupted: no jump)
      innerVisible = sumE >= .001f;
      if (innerVisible) {
        float fastL = 0f, fastR = 0f, slowL = 0f, slowR = 0f, sumT = 0f;
        for (Tab tab : tabs) {
          float e = tab.e.getValue(), t = tab.t.getValue();
          fastL += e * tab.left;
          fastR += e * tab.right;
          slowL += t * tab.left;
          slowR += t * tab.right;
          sumT += t;
        }
        fastL /= sumE;
        fastR /= sumE;
        if (sumT < .01f) {
          slowL = fastL;
          slowR = fastR;
        } else {
          slowL /= sumT;
          slowR /= sumT;
        }
        float l = Math.min(fastL, slowL), r = Math.max(fastR, slowR);
        // At least as wide as it is tall (48dp, 40dp compact): a stadium on the icon
        final float min = pillHeightIn(height) - 2f * pad, lo = Screen.dpf(4f), hi = width - Screen.dpf(4f);
        if (r - l < min) {
          float mid = (l + r) / 2f;
          l = mid - min / 2f;
          r = mid + min / 2f;
        }
        if (l < lo) {
          r += lo - l;
          l = lo;
        }
        if (r > hi) {
          l -= r - hi;
          r = hi;
        }
        l = Math.max(lo, l);
        float p = MathUtils.clamp(sumE);
        float w = min + ((r - l) - min) * p, mid = (l + r) / 2f;
        innerL = mid - w / 2f;
        innerR = mid + w / 2f;
        innerAlpha = p;
        float press = selectedIndex != -1 ? MathUtils.clamp(tabs[selectedIndex].p.getValue()) : 0f;
        float full = min / 2f;
        innerCorner = full + (Screen.dpf(PRESSED_CORNER) - full) * press;
      }

      // Icons: from the item's centre to 24dp in from its start as it becomes selected. Shrinking to the icons, that
      // inset goes straight to half the compact width: centred at the end, never towards the fading label
      final float inset = Screen.dpf(LEAD + ICON / 2f);
      final float iconInset = inset + (uC / 2f - inset) * MathUtils.clamp(k);
      for (Tab tab : tabs) {
        float e = Math.max(-.2f, Math.min(1.2f, tab.e.getValue()));
        float centre = (tab.left + tab.right) / 2f;
        float target = iconOnly ? centre : rtl ? tab.right - iconInset : tab.left + iconInset;
        tab.iconX = centre + (target - centre) * e;
      }

      // Folding into the selected tab (Collapse): the others fade out (gone by about .29) and shrink, each as far as it
      // is unselected (a selection moving meanwhile crossfades); 1 when full
      final float collapse = collapseNow();
      final float fade = MathUtils.clamp(1f - FOLD_FADE * collapse);
      for (Tab tab : tabs) {
        float away = collapse > 0f ? 1f - MathUtils.clamp(tab.e.getValue()) : 0f;
        tab.opacity = 1f - (1f - fade) * away;
        tab.scale = 1f - FOLD_SCALE * collapse * away;
      }
      invalidate();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      if (width <= 0 || height <= 0) {
        return;
      }
      if (layoutDirty) {
        relayoutItems(); // drawn before the pre-draw listener ran (a preview's first frame)
      }
      // Container
      fillPaint.setColor(containerColor());
      rect.set(0, 0, width, height);
      c.drawRoundRect(rect, height / 2f, height / 2f, fillPaint);
      // Folding into one tab (Collapse): the tabs folding away (their state layers, icons and badges) stay inside the
      // pill as it narrows (the pill and the host draw outside their bounds otherwise)
      final float collapse = collapseNow();
      final int clip = collapse > 0f ? c.save() : -1;
      if (clip != -1) {
        c.clipRect(0, 0, width, height);
      }
      // Inner pill: 8dp in from the pill's top and bottom, whatever its height now (48dp items, 40dp compact)
      final float pillH = pillHeightIn(height), pad = Screen.dpf(PAD);
      final float itemTop = pad, itemBottom = itemTop + (pillH - 2f * pad);
      if (innerVisible && innerAlpha > 0f) {
        fillPaint.setColor(XgramExpressive.withAlpha(indicatorColor(), innerAlpha));
        rect.set(innerL, itemTop, innerR, itemBottom);
        c.drawRoundRect(rect, innerCorner, innerCorner, fillPaint);
      }
      // The tabs' state layers
      super.dispatchDraw(c);
      // Icons, labels, badges
      updateIconPaints();
      final boolean rtl = Lang.rtl();
      final float cy = pillH / 2f;
      final TextPaint labelPaint = fitStyle == STYLE_DENSE ? labelMedium : labelLarge;
      final float labelCenter = fitStyle == STYLE_DENSE ? labelMediumCenter : labelLargeCenter;
      final int labelColor = selectedContentColor();
      final float labelInset = Screen.dpf(LEAD + ICON + (fitStyle == STYLE_DENSE ? GAP_DENSE : GAP_REGULAR));
      // Shrinking to the icons: the label is wiped out by the inner pill (gone halfway), the badge's dot grows back
      // into its count
      final float shrink = shrinkNow();
      final float labelFade = MathUtils.clamp(1f - 2f * shrink), badgeFade = 1f - MathUtils.clamp(shrink);
      for (Tab tab : tabs) {
        drawIcon(c, tab, cy);
        float a = MathUtils.clamp((tab.e.getValue() - .5f) / .5f);
        float labelA = a * labelFade;
        if (labelA > 0f && innerVisible && !iconOnly && !tab.shownLabel.isEmpty()) {
          float clipL = Math.max(tab.left, innerL), clipR = Math.min(tab.right, innerR);
          if (clipR > clipL) {
            labelPaint.setColor(labelColor);
            labelPaint.setAlpha(Math.round(Color.alpha(labelColor) * labelA));
            float x = rtl ? tab.right - labelInset - tab.shownLabelWidth : tab.left + labelInset;
            int save = c.save();
            c.clipRect(clipL, itemTop, clipR, itemBottom);
            c.drawText(tab.shownLabel, x, cy + labelCenter, labelPaint);
            c.restoreToCount(save);
          }
        }
        drawBadge(c, tab, a * badgeFade, rtl, cy);
      }
      if (clip != -1) {
        drawFoldedBadge(c, width, height, collapse, rtl);
        c.restoreToCount(clip);
      }
    }

    private void drawIcon (Canvas c, Tab tab, float cy) {
      final float opacity = tab.opacity;
      if (opacity <= 0f) {
        return; // folded away (Collapse)
      }
      final float cx = tab.iconX;
      final float pop = tab.q.getValue() * tab.scale;
      if (tab.avatar != null) {
        float size = Screen.dpf(TAB_AVATAR) * pop;
        int left = Math.round(cx - size / 2f), top = Math.round(cy - size / 2f), sizePx = Math.round(size);
        // Folding away: the photo and the menu button's badge on it fade through a layer, only while they fade
        int layer = -1;
        if (opacity < 1f) {
          float slack = Screen.dpf(4.5f); // the badge's cutout, past the photo's corner
          layerRect.set(left - slack, top - slack, left + sizePx + slack, top + sizePx + slack);
          layer = c.saveLayerAlpha(layerRect, Math.round(255f * opacity));
        }
        if (tab.swipe != null) {
          tab.swipe.draw(c, tab.avatar, left, top, sizePx); // two photos sliding while a swipe switches accounts
        } else {
          tab.avatar.setBounds(left, top, left + sizePx, top + sizePx);
          if (tab.avatar.needPlaceholder()) {
            tab.avatar.drawPlaceholder(c);
          }
          tab.avatar.draw(c);
        }
        // The menu button's badge while the side menu is hidden: cut out of the pill, or of the inner pill as the tab is selected
        float selected = innerVisible ? MathUtils.clamp(tab.e.getValue()) * MathUtils.clamp(innerAlpha) : 0f;
        drawMenuBadge(c, left + sizePx / 2f, top + sizePx / 2f, sizePx / 2f, ColorUtils.fromToArgb(containerColor(), indicatorColor(), selected));
        if (layer != -1) {
          c.restoreToCount(layer);
        }
        return;
      }
      Drawable ref = tab.rest != null ? tab.rest : tab.active;
      if (ref == null) {
        return;
      }
      int intrinsic = Math.max(1, Math.max(ref.getMinimumWidth(), ref.getMinimumHeight()));
      float scale = pop * Screen.dpf(ICON) / intrinsic;
      int save = c.save();
      if (Math.abs(scale - 1f) > .001f) {
        c.scale(scale, scale, cx, cy);
      }
      XgramIconCrossfade.draw(c, tab.rest, restIconPaint, tab.active, activeIconPaint, cx, cy, tab.c.getValue(), opacity);
      c.restoreToCount(save);
    }

    /**
     * 5.6: at the icon's top end corner; on the selected tab a count becomes the 6dp dot as the label comes in. Folding
     * away into another tab, it fades and shrinks with its icon.
     */
    private void drawBadge (Canvas c, Tab tab, float a, boolean rtl, float cy) {
      final float presence = tab.badge.getValue();
      final float opacity = tab.opacity;
      if (presence <= .001f || opacity <= 0f || tab.drawKind == 0 || (tab.dest != DEST_CHATS && tab.dest != DEST_CALLS && tab.dest != DEST_ACTIVITY)) {
        return;
      }
      final float half = Screen.dpf(ICON / 2f);
      final float iconLeft = tab.iconX - half, iconRight = tab.iconX + half, iconTop = cy - half;
      final float dotSize = Screen.dpf(BADGE_SMALL);
      final float dotLeft = rtl ? iconRight - Screen.dpf(BADGE_SMALL_START) - dotSize : iconLeft + Screen.dpf(BADGE_SMALL_START);
      int save = c.save();
      if (tab.scale != 1f) {
        c.scale(tab.scale, tab.scale, tab.iconX, cy); // around the icon's centre, as the icon
      }
      if (tab.drawKind == 2) {
        float dotCx = dotLeft + dotSize / 2f, dotCy = iconTop + dotSize / 2f;
        if (presence != 1f) {
          c.scale(presence, presence, dotCx, dotCy);
        }
        fillPaint.setColor(XgramExpressive.withAlpha(mutedDotColor(), opacity));
        c.drawCircle(dotCx, dotCy, dotSize / 2f, fillPaint);
        c.restoreToCount(save);
        return;
      }
      final float height = Screen.dpf(BADGE_LARGE);
      final float width = Math.max(height, tab.drawTextWidth + Screen.dpf(BADGE_PADDING * 2f));
      final float largeLeft = rtl ? iconRight - Screen.dpf(BADGE_LARGE_START) - width : iconLeft + Screen.dpf(BADGE_LARGE_START);
      final float largeTop = iconTop + Screen.dpf(BADGE_LARGE_TOP);
      final float m = MathUtils.clamp(a);
      float left = largeLeft + (dotLeft - largeLeft) * m;
      float top = largeTop + (iconTop - largeTop) * m;
      float right = largeLeft + width + (dotLeft + dotSize - (largeLeft + width)) * m;
      float bottom = largeTop + height + (iconTop + dotSize - (largeTop + height)) * m;
      rect.set(left, top, right, bottom);
      if (presence != 1f) {
        c.scale(presence, presence, rect.centerX(), rect.centerY());
      }
      fillPaint.setColor(XgramExpressive.withAlpha(color(XgramExpressive.ERROR), opacity));
      float radius = rect.height() / 2f;
      c.drawRoundRect(rect, radius, radius, fillPaint);
      float textAlpha = (1f - MathUtils.clamp(2f * m)) * opacity;
      if (textAlpha > 0f && tab.drawText != null) {
        int textColor = color(XgramExpressive.ON_ERROR);
        badgePaint.setColor(textColor);
        badgePaint.setAlpha(Math.round(Color.alpha(textColor) * textAlpha));
        c.drawText(tab.drawText, rect.centerX() - tab.drawTextWidth / 2f, rect.centerY() + badgeCenter, badgePaint);
      }
      c.restoreToCount(save);
    }

    /**
     * Folded into one tab (Collapse): a 6dp dot at the pill's top end corner while a tab folded away has a badge (the
     * error colour for a count, the muted dot's for muted chats only), fading in over the end of the fold.
     */
    private void drawFoldedBadge (Canvas c, int width, int height, float collapse, boolean rtl) {
      float appear = MathUtils.clamp((collapse - FOLDED_BADGE_FROM) / (1f - FOLDED_BADGE_FROM));
      if (selectedIndex >= 0 && selectedIndex < tabs.length) {
        // Pressed, the inner pill's corner squares and reaches the dot's place: it fades out with the press (the tap
        // brings the bar back)
        appear *= 1f - MathUtils.clamp(tabs[selectedIndex].p.getValue());
      }
      if (appear <= 0f) {
        return;
      }
      float presence = 0f;
      boolean count = false;
      for (Tab tab : tabs) {
        if (tab.drawKind == 0 || (tab.dest != DEST_CHATS && tab.dest != DEST_CALLS && tab.dest != DEST_ACTIVITY)) {
          continue;
        }
        // As far as it has a badge and is folded away (not the selected tab's own)
        float p = MathUtils.clamp(tab.badge.getValue()) * (1f - MathUtils.clamp(tab.e.getValue()));
        if (p > .001f) {
          presence = Math.max(presence, p);
          count |= tab.drawKind == 1;
        }
      }
      if (presence <= .001f) {
        return;
      }
      // At 45° on the end's arc, in the middle of the 8dp ring between the pill's edge and the inner pill's (no overlap)
      final float dot = Screen.dpf(BADGE_SMALL), radius = height / 2f, d = (radius - Screen.dpf(PAD) / 2f) * .7071f;
      final float cx = rtl ? radius - d : width - radius + d, cy = radius - d;
      fillPaint.setColor(XgramExpressive.withAlpha(count ? color(XgramExpressive.ERROR) : mutedDotColor(), appear));
      c.drawCircle(cx, cy, dot / 2f * presence, fillPaint);
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      return true; // nothing under the pill gets a touch that started on it
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // "Chats, 5 unread, selected, 1 of 3"
        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(1, tabs.length, false, AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
      }
    }
  }

  /**
   * A view of the toolbar that may carry the account's photo: a vertical swipe on the photo switches to the next or
   * previous account (XgramAccountSwipe), its tap and long press stay the view's.
   */
  private abstract static class PhotoView extends XgramNavRail.StateView {
    PhotoView (Context context) {
      super(context);
    }

    /** The photo's swipe; null without a photo. */
    @Nullable
    abstract XgramAccountSwipe accountSwipe ();

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      XgramAccountSwipe swipe = accountSwipe();
      int touch = swipe != null ? swipe.onTouchEvent(e) : XgramAccountSwipe.TOUCH_PASS;
      if (touch == XgramAccountSwipe.TOUCH_CLAIMED) {
        // A swipe now: the press ends (its state layer, the pending long press) as if the finger had left the view
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
        return true;
      }
      return touch == XgramAccountSwipe.TOUCH_CONSUMED || super.onTouchEvent(e);
    }

    // A long press that ran (the accounts' menu) ends the swipe: the finger still down can no longer switch too
    @Override
    public boolean performLongClick () {
      return !cancelSwipeTracking() && super.performLongClick();
    }

    @Override
    public boolean performLongClick (float x, float y) {
      // Android 7+: the press's own long press comes here first
      return !cancelSwipeTracking() && super.performLongClick(x, y);
    }

    @Override
    public boolean performClick () {
      XgramAccountSwipe swipe = accountSwipe();
      if (swipe != null && swipe.blocksPress()) {
        return false; // TalkBack's or a key's click while a swipe switches: never the old account's profile
      }
      return super.performClick();
    }

    /** Ends the photo's swipe tracking; true while a swipe's switch blocks presses (no long press then: a second one). */
    private boolean cancelSwipeTracking () {
      XgramAccountSwipe swipe = accountSwipe();
      if (swipe == null) {
        return false;
      }
      swipe.cancelTracking();
      return swipe.blocksPress();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      XgramAccountSwipe swipe = accountSwipe();
      if (swipe != null) {
        swipe.addAccessibilityActions(info);
      }
    }

    @Override
    public boolean performAccessibilityAction (int action, @Nullable Bundle arguments) {
      XgramAccountSwipe swipe = accountSwipe();
      return (swipe != null && swipe.performAccessibilityAction(action)) || super.performAccessibilityAction(action, arguments);
    }
  }

  /** A tab's touch area and state layer: a 48dp circle on the icon, or the inner pill when selected (7). */
  private final class TabView extends PhotoView {
    private final Tab tab;
    private boolean pressedState;

    TabView (Context context, Tab tab) {
      super(context);
      this.tab = tab;
      setOnClickListener(v -> onTabClick(tab.dest, v));
      setOnLongClickListener(v -> onTabLongClick(tab.dest));
    }

    @Nullable
    @Override
    XgramAccountSwipe accountSwipe () {
      return tab != null ? tab.swipe : null; // the profile tab's photo
    }

    private boolean isSelectedTab () {
      return selectedIndex != -1 && selectedIndex < tabs.length && tabs[selectedIndex] == tab;
    }

    @Override
    protected void drawableStateChanged () {
      super.drawableStateChanged();
      boolean pressed = isPressed();
      if (pressed != pressedState) {
        pressedState = pressed;
        tab.p.animateTo(pressed ? 1f : 0f);
      }
    }

    @Override
    protected int stateLayerColor () {
      return stateColor(isSelectedTab());
    }

    @Override
    protected void onDraw (Canvas c) {
      if (tab.opacity <= 0f) {
        return; // folded away into another tab (Collapse): a tap there only brings the bar back
      }
      final float offset = getLeft();
      // As the inner pill: 8dp in from the pill's top and bottom (48dp, 40dp compact)
      final float pad = Screen.dpf(PAD), itemH = pillHeightIn(getHeight()) - 2f * pad;
      final float top = pad, bottom = top + itemH;
      if (isSelectedTab() && pill.innerVisible) {
        setStateShape(pill.innerL - offset, top, pill.innerR - offset, bottom, pill.innerCorner);
      } else {
        float full = itemH / 2f;
        float radius = full + (Screen.dpf(PRESSED_CORNER) - full) * MathUtils.clamp(tab.p.getValue());
        float cx = tab.iconX - offset;
        setStateShape(cx - full, top, cx + full, bottom, radius);
      }
      drawState(c);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        int index = indexOf(tab.dest);
        info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index != -1 ? visualSlot(index) : 0, 1, false, isSelectedTab()));
        if (isSelectedTab() && collapsesToTab() && compactSpring.getTarget() >= 1f) {
          // Folded into this tab (Collapse): a tap brings the bar back ("double tap to expand navigation")
          info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, Lang.getString(R.string.XgramNavExpand)));
        }
      }
    }
  }

  /**
   * The profile button (5.5): the 40dp photo in a 56dp container (8dp ring), a circle at rest, a squircle while the
   * profile is on screen or it is pressed. Level 3 shadow, its outline follows the corner.
   */
  private final class ProfileView extends PhotoView {
    /** The photo; a swipe hands it the photo it slid in (no reload, no flash). */
    private AvatarReceiver avatar;
    /** Up or down on the photo: the next or previous account. */
    private final XgramAccountSwipe swipe;
    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Selected (colour, effects default), its shape (spatial fast) and the press (the corner, the photo's scale). */
    private final XgramExpressive.Spring select, shape, press;
    private boolean pressedState;

    ProfileView (Context context) {
      super(context);
      avatar = new AvatarReceiver(this);
      swipe = new XgramAccountSwipe(this, this, AVATAR, SWIPE_COMMIT, !preview, new SwipeHost() {
        @Nullable
        @Override
        public AvatarReceiver replaceAvatar (AvatarReceiver incoming) {
          AvatarReceiver previous = ProfileView.this.avatar;
          ProfileView.this.avatar = incoming;
          return previous;
        }
      });
      select = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidate()).setThreshold(.002f);
      shape = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> onShapeChanged()).setThreshold(.002f);
      press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> onShapeChanged()).setThreshold(.002f);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
        setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            int slop = Screen.dp(BUTTON_HIT_SLOP);
            outline.setRoundRect(slop, slop, view.getWidth() - slop, view.getHeight() - slop, corner());
          }
        });
      }
      setOnClickListener(this::onClickProfile);
      setOnLongClickListener(v -> showAccounts());
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      onShapeChanged();
    }

    private void onClickProfile (View v) {
      onProfileClick(v);
    }

    @Nullable
    @Override
    XgramAccountSwipe accountSwipe () {
      return swipe;
    }

    void requestAvatar (@Nullable Tdlib tdlib) {
      if (swipe.onBind(tdlib != null ? tdlib.id() : TdlibAccount.NO_ID)) {
        invalidate();
        return; // the photo slid in with a swipe already: no flash of the old one
      }
      if (tdlib != null) {
        avatar.requestAccount(tdlib, tdlib.id(), AvatarReceiver.Options.NONE);
      } else {
        avatar.clear();
      }
      invalidate();
    }

    void setSelectedState (boolean selected, boolean animated) {
      float target = selected ? 1f : 0f;
      if (animated && XgramNavToolbar.this.isLaidOut()) {
        select.animateTo(target);
        shape.animateTo(target);
      } else {
        select.setValue(target);
        shape.setValue(target);
        onShapeChanged();
      }
      if (isSelected() != selected) {
        setSelected(selected);
      }
    }

    void snap () {
      select.setValue(select.getTarget());
      shape.setValue(shape.getTarget());
      press.setValue(0f);
      onShapeChanged();
    }

    void destroy () {
      select.cancel();
      shape.cancel();
      press.cancel();
      swipe.destroy();
      avatar.destroy();
    }

    private float corner () {
      float f = Math.max(0f, Math.min(1.15f, Math.max(shape.getValue(), press.getValue())));
      float full = Screen.dpf(BUTTON / 2f);
      return Math.max(0f, full + (Screen.dpf(BUTTON_SELECTED_CORNER) - full) * f);
    }

    private void onShapeChanged () {
      invalidate();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
    }

    @Override
    protected void drawableStateChanged () {
      super.drawableStateChanged();
      boolean pressed = isPressed();
      if (pressed != pressedState) {
        pressedState = pressed;
        if (press != null) {
          press.animateTo(pressed ? 1f : 0f);
        }
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      avatar.attach();
      swipe.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      avatar.detach();
      swipe.detach();
    }

    @Override
    protected int stateLayerColor () {
      boolean selected = select.getTarget() >= 1f;
      if (vibrant) {
        return color(selected ? XgramExpressive.ON_TERTIARY : XgramExpressive.ON_TERTIARY_CONTAINER);
      }
      return color(selected ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      if (width <= 0 || height <= 0) {
        return;
      }
      int rest = vibrant ? color(XgramExpressive.TERTIARY_CONTAINER) : containerColor();
      int selected = color(vibrant ? XgramExpressive.TERTIARY : XgramExpressive.SECONDARY_CONTAINER);
      final int fill = ColorUtils.fromToArgb(rest, selected, MathUtils.clamp(select.getValue()));
      paint.setColor(fill);
      float radius = corner();
      float slop = Screen.dpf(BUTTON_HIT_SLOP);
      rect.set(slop, slop, width - slop, height - slop);
      c.drawRoundRect(rect, radius, radius, paint);
      setStateShape(rect.left, rect.top, rect.right, rect.bottom, radius);
      drawState(c);
      float scale = 1f + (AVATAR_PRESSED_SCALE - 1f) * MathUtils.clamp(press.getValue());
      int size = Math.round(Screen.dpf(AVATAR) * scale);
      int left = Math.round((width - size) / 2f), top = Math.round((height - size) / 2f);
      swipe.draw(c, avatar, left, top, size); // two photos sliding while a swipe switches accounts
      // The menu button's badge while the side menu is hidden, cut out of the button's colour
      drawMenuBadge(c, left + size / 2f, top + size / 2f, size / 2f, fill);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !preview) {
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, Lang.getString(R.string.XgramNavSwitchAccount)));
      }
    }
  }
}
