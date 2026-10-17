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
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
 * low window in landscape), as in Google Photos and Essentials (Appearance > Layout > Navigation bar, Expressive
 * design only; the classic look keeps {@link XgramNavBar}, wide windows the rail).
 * <ul>
 *   <li>A 64dp full-corner pill with 8dp of padding holds 2 to 4 tabs (Chats, Contacts and Settings unless chosen
 *   otherwise). The selected tab is a 48dp inner pill with its filled icon and label (the icon 24dp in from its start,
 *   so the inner pill is concentric with the container); the others show their outlined icon. A tab change is a spring
 *   morph: the widths move together (they always add up to the pill's content), the inner pill's leading edge runs
 *   ahead of its tail, the icon crossfades (XgramIconCrossfade) and pops, the label is wiped in.</li>
 *   <li>A 56dp profile button (the account's photo) 8dp after the pill: a circle at rest, a squircle while the own
 *   profile is on screen. Long press: switch account.</li>
 *   <li>Contacts, Settings, the profile (and Calls with folders) are tab screens: opened by the toolbar right above the
 *   chat list (a fade), no back arrow, Back fades to the chat list. The toolbar lives above the navigation (in the
 *   activity's content layout, like the rail), stays on screen for a tab change, leaves with its tab screen when a
 *   screen is pushed (shared axis) and sits under a predictive back card like the live app bar.</li>
 *   <li>A page-colour band behind it fades the rows out under the bar; it takes the touches of its solid part.</li>
 * </ul>
 * {@link XgramTwoPane} owns one per activity and decides when it shows (one pane, no rail, the setting on). Values are
 * the M3E floating toolbar tokens (FloatingToolbarTokens: 64dp, 8dp padding, 48dp items, ToolbarToFabGap 8dp,
 * ScreenOffset 16dp), BadgeTokens and the elevation level 3.
 */
public final class XgramNavToolbar implements NavigationController.XgramBarListener, XgramNavMotion.CardHoleListener {
  // Geometry (dp)

  /** From the pill to what is under it (the navigation bar), and the smallest margin from the window's sides. */
  private static final float SCREEN_OFFSET = 16f;
  private static final float HEIGHT = 64f, PAD = 8f, ITEM_H = 48f;
  /** The width of an unselected tab, regular and compact. */
  private static final float U_REGULAR = 56f, U_COMPACT = 48f;
  /** The selected tab: lead, the 24dp icon, the gap, the label, trail. Dense: labelMedium, smaller gap and trail. */
  private static final float LEAD = 12f, ICON = 24f, GAP_REGULAR = 8f, TRAIL_REGULAR = 16f, GAP_DENSE = 4f, TRAIL_DENSE = 12f;
  /** Narrower labels than this are not worth showing: icons only. */
  private static final float MIN_LABEL = 24f, ICON_ONLY_S = 64f, ICON_ONLY_MIN_U = 44f;
  private static final float BUTTON = 56f, BUTTON_GAP = 8f, AVATAR = 40f, TAB_AVATAR = 24f, BUTTON_HIT_SLOP = 4f;
  private static final float BUTTON_SELECTED_CORNER = XgramExpressive.CORNER_L, PRESSED_CORNER = XgramExpressive.CORNER_M;
  /** Above the pill: the band fades in over this much (and the level 3 shadow has room). */
  private static final float BAND_ABOVE = 16f;
  private static final float GROUP_MAX = 480f;
  private static final float POP_FROM = .84f, AVATAR_PRESSED_SCALE = .94f;
  /** BadgeTokens, as on the rail: a 6dp dot, or 16dp tall around labelSmall, at the icon's top end corner. */
  private static final float BADGE_SMALL = 6f, BADGE_SMALL_START = 18f, BADGE_LARGE = 16f, BADGE_PADDING = 4f, BADGE_LARGE_START = 12f, BADGE_LARGE_TOP = -2f;
  /** The band: alpha at the pill's centre line; it is solid from the pill's bottom down. */
  private static final float BAND_MID_ALPHA = .9f;

  private static final int STYLE_R1 = 0, STYLE_R2 = 1, STYLE_DENSE = 2;

  // Destinations (the rail's, and the profile)

  public static final int DEST_CHATS = XgramNavRail.DEST_CHATS, DEST_CALLS = XgramNavRail.DEST_CALLS,
    DEST_CONTACTS = XgramNavRail.DEST_CONTACTS, DEST_SAVED = XgramNavRail.DEST_SAVED, DEST_SETTINGS = XgramNavRail.DEST_SETTINGS,
    DEST_PROFILE = 5;
  /** Keys by destination, as stored in the tab order (XgramReorder.NAV_BAR). */
  public static final String[] TAB_KEYS = {"chats", "calls", "contacts", "saved", "settings", "profile"};
  static final int MIN_TABS = 2, MAX_TABS = 4;

  /** Why the toolbar steps aside (a bit each); scrolling down hides it too. */
  public static final int FORCE_SEARCH = 1, FORCE_SELECT = 2, FORCE_FOLDER_MENU = 4, FORCE_FAB_MENU = 8, FORCE_IME = 16, FORCE_TRANSFORM = 32;
  /** The bits the chat list sets (setForceHidden); the others are the toolbar's own. */
  private static final int FORCE_OWN = FORCE_IME | FORCE_TRANSFORM;

  /** Labels: short ones ("Saved"); the full name stays the description. */
  private static final int[] DEST_TAB_LABEL = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramBottomNavSaved, R.string.Settings, R.string.XgramNavProfileLabel};
  /** Row titles of the settings page. */
  private static final int[] DEST_TITLE = {R.string.Chats, R.string.Calls, R.string.Contacts, R.string.XgramNavRailSavedMessages, R.string.Settings, R.string.XgramNavProfileLabel};

  /** Option ids of the account menu: this plus the account's index. */
  private static final int ACCOUNT_OPTION_ID = 7000;

  private final BaseActivity context;
  @Nullable private final XgramTwoPane owner;
  @Nullable private final NavigationController navigation;
  @Nullable private final ViewGroup container;
  /** The settings page's live preview: no navigation, no badges, a tap only moves the selection. */
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
  private final XgramExpressive.Spring widthSpring, leftSpring;
  private int notifiedEndInset = Integer.MIN_VALUE;

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
  private boolean scrollHidden;
  private int forceMask;
  /** A tab screen on top searches (its search opened): set from ViewController's search hooks. */
  private boolean transformSearching;
  @Nullable private RecyclerView scrollSource;
  private boolean ignorePagerScroll, fromDrawer;

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

  /** Any spring of a tab: the pill lays its tabs out again before the next frame is drawn (once per frame). */
  private final XgramExpressive.Spring.Listener frameListener = new XgramExpressive.Spring.Listener() {
    @Override
    public void onSpringUpdate (XgramExpressive.Spring s, float value) {
      pill.markDirty();
    }
  };

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
    this.badges = preview ? null : new XgramNavBadges(badgeCallback);
    if (preview) {
      this.tdlib = context.currentTdlib();
      this.away = false;
    } else {
      this.away = true;
      ThemeManager.instance().addThemeListener(themeListener);
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
    return dest >= 0 ? XgramNavRail.DEST_OUTLINED[dest] : 0;
  }

  /** The settings page's row title ("Saved messages", not "Saved"). */
  public static String tabTitle (String key) {
    int dest = destOf(key);
    return dest >= 0 ? Lang.getString(DEST_TITLE[dest]) : key;
  }

  /** The order of the settings page: every key, as the user ordered them. */
  public static List<String> orderedKeys () {
    return XgramReorder.ordered(XgramReorder.NAV_BAR, TAB_KEYS);
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
    for (String key : XgramReorder.ordered(XgramReorder.NAV_BAR, TAB_KEYS)) {
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
        keep = (bits & (1 << dest)) != 0;
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

  /** The chat list's compose button sits on the toolbar's row (no band), or above it. */
  public void setInline (boolean inline) {
    if (this.inline != inline) {
      this.inline = inline;
      host.invalidate();
    }
  }

  /**
   * From the window's end edge to the end of the group (the profile button, or the pill), in px: the compose button
   * stands above the button, end-aligned with it. The final layout, also while a re-fit animates.
   */
  public int groupEndInsetPx () {
    if (fitWidth <= 0) {
      return Screen.dp(SCREEN_OFFSET);
    }
    float end = Lang.rtl() ? fitGroupLeft : fitWidth - (fitGroupLeft + fitGroupW);
    return Math.max(0, Math.round(end));
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
    } else {
      UI.cancel(settleRunnable);
      endTransition();
      attachScrollSource(null);
      if (badges != null) {
        badges.stop();
      }
      detachHost();
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
    readSettings();
    int[] dests = tabDests();
    boolean tabsChanged = !Arrays.equals(dests, this.dests);
    if (tabsChanged || wasButton != buttonMode || wasVibrant != vibrant || iconPack != XgramIconPack.current()) {
      rebuildTabs(dests);
      dropStaleTabScreens();
      requestFit(true);
      if (tabsChanged && root != null && isRootOf(root)) {
        root.xgramOnToolbarChanged(); // the Chats / Calls header tabs follow the Calls tab
      }
    }
    if (badges != null) {
      badges.refresh();
    }
    settle();
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
    pill.markDirty();
  }

  /** A finger started dragging the pages: the morph follows it (also over a tap's page change still settling). */
  public void onPagerDragStarted () {
    ignorePagerScroll = false;
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

  /** Scrolling down hides the toolbar, scrolling up brings it back (never with touch exploration). */
  public void setScrollHidden (boolean hidden) {
    if (hidden && !scrollHidden && isTouchExploration()) {
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
    hideSpring.cancel();
    widthSpring.cancel();
    leftSpring.cancel();
    for (Tab tab : tabs) {
      tab.destroy();
    }
    profile.destroy();
    if (badges != null) {
      badges.destroy();
    }
    if (!preview) {
      ThemeManager.instance().removeThemeListener(themeListener);
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
    tabL = isTabRoot(left);
    tabR = isTabRoot(right);
    View navigationView = navigation.getValue();
    holeOffsetX = navigationView.getLeft() - host.getLeft();
    holeOffsetY = navigationView.getTop() - host.getTop();
    if (tabL && tabR) {
      // Between two tab roots (back to the chat list, or a tab screen open() is fading in): the pill morphs with the
      // fade, which starts only once the incoming screen is ready (its asynchronous wait)
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
    boolean tabRoot = isTabRoot(finalTop);
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
      }
      transform = transformSearching || top.inSelectMode();
    } else {
      transformSearching = false;
    }
    forceMask = transform ? forceMask | FORCE_TRANSFORM : forceMask & ~FORCE_TRANSFORM;
    applyFollow(tabRoot ? 1f : 0f, 0f);
    updateHidden(true);
    if (tabRoot) {
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
  }

  /** The chat list at the root, or a screen the toolbar opened (always right above it). */
  private boolean isTabRoot (@Nullable ViewController<?> c) {
    return c != null && !c.isDestroyed() && ((root != null && c == root) || c.xgramBarTabRaw() != -1);
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
    this.root = root;
    Tdlib tdlib = root != null ? root.tdlib() : null;
    if (tdlib != this.tdlib) {
      this.tdlib = tdlib;
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
    float target = scrollHidden || forceMask != 0 ? 1f : 0f;
    // Hiding and showing animate only while the toolbar is on a tab screen (also back from slid away, while invisible)
    if (animated && canAnimateHide()) {
      hideSpring.animateTo(target);
    } else {
      hideSpring.setValue(target);
    }
    applyHide();
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
    pill.markDirty();
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
    if (endInset != notifiedEndInset) {
      notifiedEndInset = endInset;
      UI.cancel(layoutNotify);
      UI.post(layoutNotify); // after this layout pass: the chat list moves its compose button
    }
  }

  private void notifyRootLayout () {
    if (!destroyed && !preview && root != null && isRootOf(root)) {
      root.xgramOnToolbarLayout();
    }
  }

  /** The pill's top in the host: 16dp above the inset, 64dp tall. */
  private int pillTop (int hostHeight) {
    return hostHeight - (preview ? 0 : inset) - Screen.dp(SCREEN_OFFSET) - Screen.dp(HEIGHT);
  }

  /** Places the pill and the button (the re-fit springs move them; no layout pass). */
  private void layoutGroup () {
    int hostHeight = host.getHeight() > 0 ? host.getHeight() : host.getMeasuredHeight();
    if (hostHeight <= 0) {
      return;
    }
    float w = Math.max(0f, widthSpring.getValue());
    float groupLeft = leftSpring.getValue();
    boolean rtl = Lang.rtl();
    int top = pillTop(hostHeight);
    int pillLeft = Math.round(rtl && buttonMode ? groupLeft + Screen.dpf(BUTTON + BUTTON_GAP) : groupLeft);
    int pillWidth = Math.round(w);
    pill.layout(pillLeft, top, pillLeft + pillWidth, top + Screen.dp(HEIGHT));
    if (buttonMode) {
      int buttonLeft = Math.round(rtl ? groupLeft : groupLeft + w + Screen.dpf(BUTTON_GAP));
      int buttonTop = top + (Screen.dp(HEIGHT) - Screen.dp(BUTTON)) / 2;
      int slop = Screen.dp(BUTTON_HIT_SLOP); // the view is 4dp larger on each side: the touch area around the button
      profile.layout(buttonLeft - slop, buttonTop - slop, buttonLeft + Screen.dp(BUTTON) + slop, buttonTop + Screen.dp(BUTTON) + slop);
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

  private void onTabClick (int dest, @Nullable View view) {
    if (preview) {
      if (dest != DEST_SAVED && dest != selectedDest) {
        if (view != null) {
          ViewUtils.onClick(view);
        }
        setTarget(dest, true);
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
        open(DEST_CALLS, new CallListController(context, tdlib), drawer);
        if (badges != null) {
          badges.markCallsSeen();
        }
        return;
      }
      case DEST_CONTACTS: {
        tdlib.contacts().startSyncIfNeeded(context, true, () -> {
          if (tdlib == this.tdlib && canNavigate(drawer)) {
            PeopleController c = new PeopleController(context, tdlib);
            c.setNeedSearch();
            open(DEST_CONTACTS, c, drawer);
          }
        });
        return;
      }
      case DEST_SETTINGS: {
        open(DEST_SETTINGS, new SettingsController(context, tdlib), drawer);
        return;
      }
      case DEST_PROFILE: {
        openProfile(drawer);
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
    // The selection moves when the fade begins (onXgramTransitionBegin), after the screen's asynchronous wait
    navigation.setControllerAnimated(c, false, true);
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
    ProfileController c = new ProfileController(context, tdlib);
    c.setArguments(new ProfileController.Args(chat, null, false));
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
    if (dest == DEST_PROFILE) {
      return showAccounts();
    }
    if (dest == DEST_CHATS && canNavigate(false) && navigation.getCurrentStackItem() == root) {
      // The Chats tab's filters (Unread, Groups, Archive...) had only its slide-off menu: while there are no folders
      return root.xgramShowRailFilters();
    }
    return false; // the tooltip
  }

  /** Long press on the photo: the accounts (the current one highlighted) and Add account. */
  private boolean showAccounts () {
    if (preview || destroyed || navigation == null || navigation.isAnimating()) {
      return false;
    }
    final ViewController<?> top = navigation.getCurrentStackItem();
    final Tdlib tdlib = this.tdlib;
    if (top == null || tdlib == null) {
      return false;
    }
    final ArrayList<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
    final int count = accounts.size();
    int[] ids = new int[count + 1];
    String[] titles = new String[count + 1];
    int[] colors = new int[count + 1];
    int[] icons = new int[count + 1];
    for (int i = 0; i < count; i++) {
      TdlibAccount account = accounts.get(i);
      ids[i] = ACCOUNT_OPTION_ID + i;
      titles[i] = Lang.getDebugString(account.getName(), account.isDebug());
      colors[i] = account.id == account.context().preferredAccountId() ? ViewController.OptionColor.BLUE : ViewController.OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_account_circle_24;
    }
    ids[count] = R.id.btn_addAccount;
    titles[count] = Lang.getString(R.string.XgramNavRailAddAccount);
    colors[count] = ViewController.OptionColor.NORMAL;
    icons[count] = R.drawable.baseline_add_24;
    top.showOptions(null, ids, titles, colors, icons, (itemView, id) -> {
      if (id == R.id.btn_addAccount) {
        tdlib.ui().addAccount(context, true, false);
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

  private void onProfileClick (View view) {
    if (preview) {
      if (selectedDest != DEST_PROFILE) {
        ViewUtils.onClick(view);
        setTarget(DEST_PROFILE, true);
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
    String label = "";
    private float labelWidthLarge = -1f, labelWidthMedium = -1f;
    String shownLabel = "";
    float shownLabelWidth;
    /** Width when selected, in px. */
    float s;
    /** This frame's geometry, in the pill: the item, the icon centre. */
    float left, right, iconX;

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
      rest = XgramNavRail.restIcon(res, XgramNavRail.DEST_FILLED[dest], XgramNavRail.DEST_OUTLINED[dest]);
      active = XgramNavRail.activeIcon(res, XgramNavRail.DEST_FILLED[dest], XgramNavRail.DEST_OUTLINED[dest]);
      view.invalidate();
    }

    void requestAvatar (@Nullable Tdlib tdlib) {
      if (avatar == null) {
        return;
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

    /** Unselected tabs (and every tab while labels don't fit) tell their name in a tooltip (Android 8+). */
    void updateTooltip () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        boolean selected = indexOf(dest) == selectedIndex && selectedIndex != -1;
        view.setTooltipText(selected && !iconOnly ? null : Lang.getString(DEST_TITLE[dest]));
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
      readSettings();
      int[] next = tabDests();
      rebuildTabs(next);
      requestFit(true);
      requestAvatars();
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
      pill.measure(MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(widthSpring.getValue())), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(HEIGHT), MeasureSpec.EXACTLY));
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
      int top = pillTop(getHeight());
      int mid = top + Screen.dp(HEIGHT / 2f), end = top + Screen.dp(HEIGHT);
      if (transitionActive && tabL && tabR) {
        // A fade between two tab screens: the band of the one it leaves, then the other's over it
        boolean forward = tForward;
        ViewController<?> from = forward ? tLeft : tRight, to = forward ? tRight : tLeft;
        drawBandLayer(c, bandColorOf(from), 1f, mid, end);
        drawBandLayer(c, bandColorOf(to), transitionProgress(), mid, end);
        return;
      }
      ViewController<?> subject = transitionActive ? (tabL ? tLeft : tRight) : (navigation != null ? navigation.getCurrentStackItem() : null);
      if (subject != null && subject == root && root != null) {
        // The chat list: its two pages' colours, crossfaded by the pager (no new gradient per frame)
        float calls = root.xgramCallsFraction();
        drawBandLayer(c, Theme.getColor(ColorId.filling), 1f, mid, end);
        drawBandLayer(c, Theme.getColor(ColorId.background), calls, mid, end);
        return;
      }
      if (subject != null) {
        drawBandLayer(c, bandColorOf(subject), 1f, mid, end);
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
      float cx = (profile.getLeft() + profile.getRight()) / 2f, cy = (profile.getTop() + profile.getBottom()) / 2f;
      float radius = Screen.dpf(BUTTON / 2f + BUTTON_HIT_SLOP);
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
        if (!preview && !inline && y >= pillTop(getHeight()) + Screen.dp(HEIGHT / 2f)) {
          touchMode = TOUCH_SWALLOW; // the band's solid part: the rows there are covered
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
    private final RectF rect = new RectF();
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
      float sumE = 0f, sumES = 0f;
      for (Tab tab : tabs) {
        float e = tab.e.getValue();
        sumE += e;
        sumES += e * tab.s;
      }
      float bw = Math.max(0f, (content - sumES) / Math.max(.5f, n - sumE));
      final boolean rtl = Lang.rtl();
      float x = pad;
      for (int slot = 0; slot < n; slot++) {
        Tab tab = tabs[rtl ? n - 1 - slot : slot];
        float itemWidth = bw + tab.e.getValue() * (tab.s - bw);
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
        final float min = Screen.dpf(ITEM_H), lo = Screen.dpf(4f), hi = width - Screen.dpf(4f);
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
        float full = Screen.dpf(ITEM_H / 2f);
        innerCorner = full + (Screen.dpf(PRESSED_CORNER) - full) * press;
      }

      // Icons: from the item's centre to 24dp in from its start as it becomes selected
      final float inset = Screen.dpf(LEAD + ICON / 2f);
      for (Tab tab : tabs) {
        float e = Math.max(-.2f, Math.min(1.2f, tab.e.getValue()));
        float centre = (tab.left + tab.right) / 2f;
        float target = iconOnly ? centre : rtl ? tab.right - inset : tab.left + inset;
        tab.iconX = centre + (target - centre) * e;
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
      // Inner pill
      final float itemTop = Screen.dpf((HEIGHT - ITEM_H) / 2f), itemBottom = itemTop + Screen.dpf(ITEM_H);
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
      final float cy = Screen.dpf(HEIGHT / 2f);
      final TextPaint labelPaint = fitStyle == STYLE_DENSE ? labelMedium : labelLarge;
      final float labelCenter = fitStyle == STYLE_DENSE ? labelMediumCenter : labelLargeCenter;
      final int labelColor = selectedContentColor();
      final float labelInset = Screen.dpf(LEAD + ICON + (fitStyle == STYLE_DENSE ? GAP_DENSE : GAP_REGULAR));
      for (Tab tab : tabs) {
        drawIcon(c, tab, cy);
        float a = MathUtils.clamp((tab.e.getValue() - .5f) / .5f);
        if (a > 0f && innerVisible && !iconOnly && !tab.shownLabel.isEmpty()) {
          float clipL = Math.max(tab.left, innerL), clipR = Math.min(tab.right, innerR);
          if (clipR > clipL) {
            labelPaint.setColor(labelColor);
            labelPaint.setAlpha(Math.round(Color.alpha(labelColor) * a));
            float x = rtl ? tab.right - labelInset - tab.shownLabelWidth : tab.left + labelInset;
            int save = c.save();
            c.clipRect(clipL, itemTop, clipR, itemBottom);
            c.drawText(tab.shownLabel, x, cy + labelCenter, labelPaint);
            c.restoreToCount(save);
          }
        }
        drawBadge(c, tab, a, rtl, cy);
      }
    }

    private void drawIcon (Canvas c, Tab tab, float cy) {
      final float cx = tab.iconX;
      final float pop = tab.q.getValue();
      if (tab.avatar != null) {
        float size = Screen.dpf(TAB_AVATAR) * pop;
        int left = Math.round(cx - size / 2f), top = Math.round(cy - size / 2f), sizePx = Math.round(size);
        tab.avatar.setBounds(left, top, left + sizePx, top + sizePx);
        if (tab.avatar.needPlaceholder()) {
          tab.avatar.drawPlaceholder(c);
        }
        tab.avatar.draw(c);
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
      XgramIconCrossfade.draw(c, tab.rest, restIconPaint, tab.active, activeIconPaint, cx, cy, tab.c.getValue(), 1f);
      c.restoreToCount(save);
    }

    /** 5.6: at the icon's top end corner; on the selected tab a count becomes the 6dp dot as the label comes in. */
    private void drawBadge (Canvas c, Tab tab, float a, boolean rtl, float cy) {
      final float presence = tab.badge.getValue();
      if (presence <= .001f || tab.drawKind == 0 || (tab.dest != DEST_CHATS && tab.dest != DEST_CALLS)) {
        return;
      }
      final float half = Screen.dpf(ICON / 2f);
      final float iconLeft = tab.iconX - half, iconRight = tab.iconX + half, iconTop = cy - half;
      final float dotSize = Screen.dpf(BADGE_SMALL);
      final float dotLeft = rtl ? iconRight - Screen.dpf(BADGE_SMALL_START) - dotSize : iconLeft + Screen.dpf(BADGE_SMALL_START);
      int save = c.save();
      if (tab.drawKind == 2) {
        float dotCx = dotLeft + dotSize / 2f, dotCy = iconTop + dotSize / 2f;
        if (presence != 1f) {
          c.scale(presence, presence, dotCx, dotCy);
        }
        fillPaint.setColor(mutedDotColor());
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
      fillPaint.setColor(color(XgramExpressive.ERROR));
      float radius = rect.height() / 2f;
      c.drawRoundRect(rect, radius, radius, fillPaint);
      float textAlpha = 1f - MathUtils.clamp(2f * m);
      if (textAlpha > 0f && tab.drawText != null) {
        int textColor = color(XgramExpressive.ON_ERROR);
        badgePaint.setColor(textColor);
        badgePaint.setAlpha(Math.round(Color.alpha(textColor) * textAlpha));
        c.drawText(tab.drawText, rect.centerX() - tab.drawTextWidth / 2f, rect.centerY() + badgeCenter, badgePaint);
      }
      c.restoreToCount(save);
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

  /** A tab's touch area and state layer: a 48dp circle on the icon, or the inner pill when selected (7). */
  private final class TabView extends XgramNavRail.StateView {
    private final Tab tab;
    private boolean pressedState;

    TabView (Context context, Tab tab) {
      super(context);
      this.tab = tab;
      setOnClickListener(v -> onTabClick(tab.dest, v));
      setOnLongClickListener(v -> onTabLongClick(tab.dest));
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
      final float offset = getLeft();
      final float top = Screen.dpf((HEIGHT - ITEM_H) / 2f), bottom = top + Screen.dpf(ITEM_H);
      if (isSelectedTab() && pill.innerVisible) {
        setStateShape(pill.innerL - offset, top, pill.innerR - offset, bottom, pill.innerCorner);
      } else {
        float full = Screen.dpf(ITEM_H / 2f);
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
      }
    }
  }

  /**
   * The profile button (5.5): the 40dp photo in a 56dp container (8dp ring), a circle at rest, a squircle while the
   * profile is on screen or it is pressed. Level 3 shadow, its outline follows the corner.
   */
  private final class ProfileView extends XgramNavRail.StateView {
    private final AvatarReceiver avatar;
    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Selected (colour, effects default), its shape (spatial fast) and the press (the corner, the photo's scale). */
    private final XgramExpressive.Spring select, shape, press;
    private boolean pressedState;

    ProfileView (Context context) {
      super(context);
      avatar = new AvatarReceiver(this);
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

    void requestAvatar (@Nullable Tdlib tdlib) {
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
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      avatar.detach();
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
      paint.setColor(ColorUtils.fromToArgb(rest, selected, MathUtils.clamp(select.getValue())));
      float radius = corner();
      float slop = Screen.dpf(BUTTON_HIT_SLOP);
      rect.set(slop, slop, width - slop, height - slop);
      c.drawRoundRect(rect, radius, radius, paint);
      setStateShape(rect.left, rect.top, rect.right, rect.bottom, radius);
      drawState(c);
      float scale = 1f + (AVATAR_PRESSED_SCALE - 1f) * MathUtils.clamp(press.getValue());
      int size = Math.round(Screen.dpf(AVATAR) * scale);
      int left = Math.round((width - size) / 2f), top = Math.round((height - size) / 2f);
      avatar.setBounds(left, top, left + size, top + size);
      if (avatar.needPlaceholder()) {
        avatar.drawPlaceholder(c);
      }
      avatar.draw(c);
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
