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

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackPressMode;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MainController;

/**
 * Two-pane layout for wide screens (foldables unfolded, tablets): the chat list stays on the left and chats open on
 * the right, like official Telegram on tablets.
 *
 * Telegram X's own navigation stays untouched and becomes the right pane. A second chat list in its own
 * {@link NavigationController} is shown on the left, and while the right pane is at its root (Telegram X's chat list)
 * a "select a chat" placeholder covers it. Folding or unfolding only shows or hides the left pane.
 *
 * With Material 3 Expressive the window also gets the navigation rail ({@link XgramNavRail}) at its start when it
 * shows two panes or is at least 600 x 480dp (never a phone or a Fold's cover screen, in either orientation), unless
 * the Navigation rail setting is off: rail, chat list pane, the 24dp pane spacer with its drag handle, chat. The rail
 * takes over the side menu and its swipe, and the chat list's Chats and Calls tabs; the list keeps its compose button.
 */
public final class XgramTwoPane implements NavigationStack.ChangeListener {
  /**
   * Smallest window (dp) that gets two panes. The height rule keeps one pane on a phone in landscape and on a Fold's
   * cover screen in landscape (both are 800 to 900dp wide but under 480dp high), where the list squashed the chat.
   * Also the smallest window (any height) that gets the navigation rail (Material 3 medium width class).
   */
  private static final int MIN_WIDTH_DP = 600, MIN_HEIGHT_DP = 480;
  /** Wide enough for two panes whatever the height (desktop windows). */
  private static final int ANY_HEIGHT_WIDTH_DP = 1200;
  /**
   * Two panes stay on until the window is this much below the line, so dragging a window (DeX, freeform) across
   * it doesn't tear the chat list down and rebuild it on every frame.
   */
  private static final int HYSTERESIS_DP = 20;
  /** Book posture: the hinge only becomes the divider when it leaves both panes at least this wide (dp). */
  private static final int HINGE_MIN_LIST_DP = 260, HINGE_MIN_CHAT_DP = 300;
  /**
   * Material 3 Expressive: the chat list pane keeps its content this far from both of its sides (the screen edge or
   * the rail, and the chat), on the app bar's layer (MainController#xgramUpdatePaneInset).
   */
  public static final float PANE_INSET_DP = 16f;
  /**
   * Material 3 pane spacer (DragHandleTokens.ContainerWidth, the adaptive scaffold's partition spacer): 24dp between
   * the list's content and the chat. The list's own inset gives 16dp of it.
   */
  private static final float SPACER_DP = 24f;

  private final BaseActivity context;
  private final NavigationController navigation;
  private final ViewGroup container;

  @Nullable private NavigationController leftNavigation;
  @Nullable private MainController leftMain;
  @Nullable private View divider, placeholder;
  /** The placeholder again, behind the right pane: what shows through while the hidden chat list is clipped. */
  @Nullable private View underlay;
  /**
   * The chat list at the root of the right pane (Telegram X's own) is clipped to nothing while two panes are shown,
   * so it never appears while Telegram X animates it (swipe back, opening a chat), when the overlay can't be shown.
   * Telegram X itself changes its visibility, alpha and translation during those animations, but not its clip.
   */
  @Nullable private View hiddenRootView, hiddenHeaderCell;
  private boolean isActive;
  private int leftWidth;

  /** Material 3 Expressive navigation rail, while it shows. */
  @Nullable private XgramNavRail rail;
  private boolean railShown;

  /**
   * Material 3 Expressive floating navigation toolbar of narrow windows (one pane, no rail), above the screens. Made the
   * first time it is needed, hidden (not destroyed) while two panes or the rail take over, so it keeps its state.
   */
  @Nullable private XgramNavToolbar toolbar;
  /** The chat list the toolbar was last shown for, and whether it was: it hears when that changes. */
  @Nullable private MainController toolbarRoot;
  private boolean toolbarRootShown;

  public XgramTwoPane (BaseActivity context, NavigationController navigation, ViewGroup container) {
    this.context = context;
    this.navigation = navigation;
    this.container = container;
    navigation.getStack().addChangeListener(this);
    org.thunderdog.challegram.theme.ThemeManager.instance().addThemeListener(themeListener);
    XgramWindow window = context.xgramWindow();
    if (window != null) {
      window.addListener(windowListener);
    }
  }

  // Folding, unfolding, resizing and book posture: re-check the panes and where they split.
  private final XgramWindow.Listener windowListener = (window, changes) -> {
    if ((changes & (XgramWindow.CHANGE_SIZE | XgramWindow.CHANGE_POSTURE | XgramWindow.CHANGE_MULTI_WINDOW)) != 0) {
      update();
    }
  };

  // The placeholder, its header strip and the divider take their colours when created: recolor them on every frame of
  // an animated day/night switch (as the list pane and the rail crossfade), rebuild them once the theme has changed
  // (e.g. Material You night, an icon pack), or they stay in the old theme.
  private final org.thunderdog.challegram.theme.ThemeChangeListener themeListener = new org.thunderdog.challegram.theme.ThemeChangeListener() {
    @Override
    public boolean needsTempUpdates () {
      return true;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, @Nullable org.thunderdog.challegram.theme.ColorState state) {
      if (areTemp) {
        XgramTwoPane.this.recolorPlaceholders();
      } else {
        XgramTwoPane.this.onThemeChanged();
      }
    }

    @Override
    public void onThemeChanged (org.thunderdog.challegram.theme.ThemeDelegate fromTheme, org.thunderdog.challegram.theme.ThemeDelegate toTheme) {
      // At the start of a switch: built in the colors on screen (the temporary theme's), then recolored as it runs
      XgramTwoPane.this.onThemeChanged();
    }
  };

  /** The colored parts of a placeholder (or its underlay), kept as its tag so a theme switch recolors it in place. */
  private static final class PlaceholderParts {
    final boolean m3;
    final View layout;
    @Nullable final GradientDrawable surface;
    final ImageView icon;
    final TextView text;
    @Nullable final View header;

    PlaceholderParts (boolean m3, View layout, @Nullable GradientDrawable surface, ImageView icon, TextView text, @Nullable View header) {
      this.m3 = m3;
      this.layout = layout;
      this.surface = surface;
      this.icon = icon;
      this.text = text;
      this.header = header;
    }

    void recolor () {
      if (m3) {
        layout.setBackgroundColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER));
        if (surface != null) {
          surface.setColor(XgramExpressive.color(XgramExpressive.SURFACE));
        }
        int onSurfaceVariant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
        icon.setColorFilter(org.thunderdog.challegram.tool.Paints.getColorFilter(onSurfaceVariant));
        text.setTextColor(onSurfaceVariant);
      } else {
        layout.setBackgroundColor(Theme.getColor(ColorId.background));
        icon.setColorFilter(org.thunderdog.challegram.tool.Paints.getColorFilter(Theme.getColor(ColorId.iconLight)));
        text.setTextColor(Theme.getColor(ColorId.textLight));
        if (header != null) {
          header.setBackgroundColor(Theme.headerColor());
        }
      }
    }
  }

  private static void recolorPlaceholder (@Nullable View view) {
    if (view != null && view.getTag() instanceof PlaceholderParts) {
      ((PlaceholderParts) view.getTag()).recolor();
    }
  }

  /** A frame of a day/night switch, or its end: the placeholder, its underlay, the spacer and the handle follow it. */
  private void recolorPlaceholders () {
    recolorPlaceholder(placeholder);
    recolorPlaceholder(underlay);
    if (divider != null) {
      divider.invalidate();
    }
    if (handle != null) {
      handle.invalidate();
    }
  }

  public boolean isActive () {
    return isActive;
  }

  public static boolean isWideEnough (BaseActivity context) {
    return isWideEnough(context, false);
  }

  /** @param isActive two panes are shown now: they stay a little below the line (see {@link #HYSTERESIS_DP}) */
  private static boolean isWideEnough (BaseActivity context, boolean isActive) {
    int width = windowWidthDp(context), height = windowHeightDp(context);
    int slack = isActive ? HYSTERESIS_DP : 0;
    if (width >= ANY_HEIGHT_WIDTH_DP - slack) {
      return true;
    }
    return width >= MIN_WIDTH_DP - slack && height >= MIN_HEIGHT_DP - slack;
  }

  private static int windowWidthDp (BaseActivity context) {
    XgramWindow window = context.xgramWindow();
    if (window != null && window.widthDp() > 0 && window.heightDp() > 0) {
      return window.widthDp();
    }
    return context.getResources().getConfiguration().screenWidthDp;
  }

  private static int windowHeightDp (BaseActivity context) {
    XgramWindow window = context.xgramWindow();
    if (window != null && window.widthDp() > 0 && window.heightDp() > 0) {
      return window.heightDp();
    }
    return context.getResources().getConfiguration().screenHeightDp;
  }

  @Nullable
  private MainController mainRoot () {
    NavigationStack stack = navigation.getStack();
    ViewController<?> root = stack.size() > 0 ? stack.get(0) : null;
    return root instanceof MainController ? (MainController) root : null;
  }

  /**
   * The right pane's own chat list, kept under the placeholder. It shows no ☰ and no menu: otherwise they slide
   * into the header for a moment whenever a chat on the right is opened or swiped away.
   */
  public boolean isHiddenRoot (@Nullable ViewController<?> c) {
    return isActive && c != null && c != leftMain && c == mainRoot();
  }

  @Nullable private MainController headerRoot;
  private boolean headerRootHidden, headerRootRail;

  /**
   * Re-applies the root's header when it starts or stops being hidden, or the rail comes or goes, while it is on
   * screen. When a chat is on top, the header picks it up by itself on the way back.
   */
  private void syncRootHeader () {
    MainController root = mainRoot();
    boolean hidden = isHiddenRoot(root);
    if (root == headerRoot && hidden == headerRootHidden && railShown == headerRootRail) {
      return;
    }
    if (root != null && navigation.getCurrentStackItem() == root) {
      if (navigation.isAnimating()) {
        return; // the next stack change tries again
      }
      org.thunderdog.challegram.navigation.HeaderView header = navigation.getHeaderView();
      // Not while it searches or selects: that would put its title and menu over the search field or the counter.
      // Leaving those modes builds the header again, with the rail as it is then.
      if (header != null && !root.inTransformMode()) {
        header.setTitle(root);
      }
    }
    headerRoot = root;
    headerRootHidden = hidden;
    headerRootRail = railShown;
  }

  /** The chat list pane whose header {@link #leftHeaderRail} was applied to (kept while it is folded away). */
  @Nullable private MainController leftHeaderMain;
  private boolean leftHeaderRail;

  /**
   * The chat list pane's ☰ goes while the rail shows (MainController#getBackButton): re-applies its header when the
   * rail came or went since it was set (a new pane's header is set up with the rail as it is).
   */
  private void syncLeftHeader () {
    MainController main = isActive ? leftMain : null;
    if (main == null) {
      return;
    }
    if (main != leftHeaderMain) {
      leftHeaderMain = main;
      leftHeaderRail = railShown;
      return;
    }
    if (railShown == leftHeaderRail) {
      return;
    }
    if (leftNavigation != null && leftNavigation.getCurrentStackItem() == main) {
      if (leftNavigation.isAnimating()) {
        return; // the next stack change tries again
      }
      org.thunderdog.challegram.navigation.HeaderView header = leftNavigation.getHeaderView();
      if (header != null && !main.inTransformMode()) { // see syncRootHeader
        header.setTitle(main);
      }
    }
    leftHeaderRail = railShown;
  }

  /** Re-evaluates the layout: setting, window width and whether the app is past login. */
  public void update () {
    MainController root = mainRoot();
    boolean isAllowed = root != null && XgramSettings.instance().twoPaneLayout() && !navigation.isDestroyed();
    boolean needActive = isAllowed && isWideEnough(context, isActive);
    // The rail first: the chat list pane built below already knows it has no ☰ and no Chats / Calls tabs
    try {
      setRailShown(needRail(root, needActive));
    } catch (Throwable t) {
      Log.e("XGram: navigation rail failed", t);
      android.util.Log.e("XGram", "navigation rail failed", t);
      try {
        setRailShown(false);
      } catch (Throwable ignored) { }
    }
    try {
      if (needActive) {
        if (!isActive || leftMain == null || leftMain.tdlib() != root.tdlib()) {
          activate(root.tdlib());
        } else {
          leftWidth = computeLeftWidth(); // the pane settings may have changed
          layout();
        }
        hideRoot(root);
      } else if (isActive) {
        // Folded or too small: the chat list is kept (scroll position, folder, open archive) for the next unfold.
        // Setting off, logged out: it goes.
        deactivate(isAllowed);
      } else if (leftNavigation != null && (!isAllowed || leftMain == null || leftMain.tdlib() != root.tdlib())) {
        removeLeft();
      }
    } catch (Throwable t) {
      // Never take the app down for a layout feature: fall back to Telegram X's single pane.
      Log.e("XGram: two-pane layout failed", t);
      android.util.Log.e("XGram", "two-pane layout failed", t);
      try {
        deactivate(false);
      } catch (Throwable ignored) { }
    }
    updatePlaceholder();
    updateHighlight();
    if (root != null) {
      root.xgramUpdatePaneInset(); // beside the rail the root keeps clear of it, unless it is hidden under two panes
    }
    syncRootHeader();
    syncLeftHeader();
    syncRail();
    // The floating navigation toolbar of narrow windows (XgramNavToolbar, Expressive design) comes or goes with the
    // window, two panes and the rail
    try {
      syncToolbar(root);
    } catch (Throwable t) {
      Log.e("XGram: navigation toolbar failed", t);
      android.util.Log.e("XGram", "navigation toolbar failed", t);
    }
    // The bottom navigation bar of narrow windows (XgramNavBar) comes or goes with the window and the rail
    try {
      if (root != null) {
        root.xgramUpdateNavBar();
      }
      if (leftMain != null && leftMain != root) {
        leftMain.xgramUpdateNavBar(); // kept for the next unfold: never has one
      }
    } catch (Throwable t) {
      Log.e("XGram: bottom navigation bar failed", t);
      android.util.Log.e("XGram", "bottom navigation bar failed", t);
    }
  }

  // Navigation rail (Material 3 Expressive)

  /**
   * Expressive design and the Navigation rail setting, past login, two panes or a window two panes would fit by size
   * (at least 600 x 480dp: so never a phone or the cover screen, also in landscape where they are wide but low), tall
   * enough for the rail's destinations above the navigation bar.
   */
  private boolean needRail (@Nullable MainController root, boolean twoPanes) {
    if (root == null || navigation.isDestroyed() || !XgramExpressive.isEnabled() || !XgramSettings.instance().navRail()) {
      return false;
    }
    if (!twoPanes) {
      int slack = railShown ? HYSTERESIS_DP : 0;
      if (windowWidthDp(context) < MIN_WIDTH_DP - slack || windowHeightDp(context) < MIN_HEIGHT_DP - slack) {
        return false;
      }
    }
    return XgramNavRail.fits(XgramNavRail.destinations(root).length, windowHeightDp(context), bottomInsetDp());
  }

  /** The navigation bar (or other system bar) at the bottom of the window, in dp. */
  private int bottomInsetDp () {
    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(container);
    if (insets == null) {
      return 0;
    }
    return Math.round(insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom / Screen.density());
  }

  private void setRailShown (boolean show) {
    if (show) {
      if (rail == null) {
        rail = new XgramNavRail(context, this);
      }
      View view = rail.view();
      if (view.getParent() == null) {
        // After the focus view, under the panes, the placeholder, the handle and the drawer
        container.addView(view, Math.min(1, container.getChildCount()), new FrameLayout.LayoutParams(rail.width(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
      }
    } else if (rail != null) {
      rail.destroy();
      container.removeView(rail.view());
      rail = null;
    }
    if (railShown != show) {
      railShown = show;
      // The rail stands in for the side menu's edge swipe; its menu button still opens the menu.
      org.thunderdog.challegram.navigation.DrawerController drawer = context.getDrawer();
      if (drawer != null) {
        if (show && (drawer.isVisible() || drawer.getShowFactor() > 0f)) {
          drawer.close(0f, null); // a side menu left open (unfolded with it open): the rail's own takes over
        }
        drawer.xgramSetSwipeEnabled(!show);
      }
      container.requestLayout();
      // The ☰, the Chats and Calls tabs and the side insets of the chat lists come or go
      MainController root = mainRoot();
      if (root != null) {
        root.xgramOnNavRailChanged();
      }
      if (leftMain != null && leftMain != root) {
        leftMain.xgramOnNavRailChanged();
      }
    }
  }

  /** The rail's destinations, the one on screen and its badges' account. */
  private void syncRail () {
    XgramNavRail rail = this.rail;
    MainController root = mainRoot();
    if (!railShown || rail == null || root == null) {
      return;
    }
    rail.sync(XgramNavRail.destinations(railChatList()));
  }

  /**
   * The rail's destinations changed without a stack change (folders came or went: Calls is a page of the chat list,
   * or a screen the side menu settings may hide).
   */
  public void onRailDestinationsChanged () {
    syncRail();
  }

  /**
   * The call list is on screen: the chat list's Calls page with nothing over it (in its pane, or the only one), or a
   * call list opened as a screen. The rail's missed calls badge counts only calls that came in while it is not.
   */
  boolean isCallListOnScreen () {
    MainController list = railChatList();
    NavigationController listNavigation = isActive && leftNavigation != null ? leftNavigation : navigation;
    if (list != null && list.xgramOnCallsPage() && listNavigation.getCurrentStackItem() == list) {
      return true;
    }
    return navigation.getCurrentStackItem() instanceof org.thunderdog.challegram.ui.CallListController;
  }

  /** The chat list {@code c} shows another page (Chats or Calls, a folder): the rail's pill follows the one on screen. */
  public void onRailPageChanged (@Nullable MainController c) {
    XgramNavRail rail = this.rail;
    if (railShown && rail != null && c != null && c == railChatList()) {
      rail.onPageChanged();
    }
  }

  /** The navigation rail is on screen: the side menu, its swipe and the Chats and Calls tabs are on it. */
  public boolean isRailShown () {
    return railShown;
  }

  public static boolean isRailShown (@Nullable BaseActivity context) {
    XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
    return twoPane != null && twoPane.railShown;
  }

  /**
   * A narrow window (a phone or a Fold's cover screen, in either orientation: the rail's 600 x 480dp not reached) with
   * one pane and no rail: where the chat list gets the bottom navigation bar ({@link XgramNavBar}) when it is on.
   * With both on, the rail shows on wide windows and the bar on narrow ones.
   */
  public static boolean isNavBarWindow (@Nullable BaseActivity context) {
    if (context == null) {
      return false;
    }
    XgramTwoPane twoPane = context.xgramTwoPane();
    if (twoPane != null && (twoPane.isActive || twoPane.railShown)) {
      return false;
    }
    return !isWideEnough(context, false);
  }

  // Floating navigation toolbar (Material 3 Expressive)

  /**
   * One pane and no rail, whatever the width: where the floating navigation toolbar ({@link XgramNavToolbar}) shows
   * when it is on (a phone, the cover screen in either orientation, the inner screen with the rail and two panes off
   * or the rail too tall for it).
   */
  public static boolean isToolbarWindow (@Nullable BaseActivity context) {
    XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
    return twoPane != null && !(twoPane.isActive || twoPane.railShown);
  }

  /** The floating navigation toolbar (while it exists; it may be hidden). */
  @Nullable
  public XgramNavToolbar toolbar () {
    return toolbar;
  }

  /** Expressive design and the Navigation bar setting, past login, one pane and no rail. */
  private boolean toolbarNeeded (@Nullable MainController root) {
    return wantsToolbar(root) && !isActive && !railShown;
  }

  private boolean wantsToolbar (@Nullable MainController root) {
    return root != null && !navigation.isDestroyed() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP &&
      XgramExpressive.isEnabled() && XgramSettings.instance().bottomNav();
  }

  /**
   * Makes the toolbar the first time it is needed, shows or hides it, binds it to the chat list at the root (an
   * account switch rebinds its badges and photo) and tells that chat list when the toolbar came or went for it (its
   * folder tabs, header tabs, FAB and room follow). The setting off, or Expressive design off: it goes.
   */
  private void syncToolbar (@Nullable MainController root) {
    if (!wantsToolbar(root)) {
      if (toolbar != null) {
        XgramNavToolbar old = toolbar;
        toolbar = null;
        old.destroy();
      }
      notifyToolbarRoot(root, false);
      return;
    }
    boolean need = toolbarNeeded(root);
    if (toolbar == null) {
      if (!need) {
        notifyToolbarRoot(root, false);
        return;
      }
      toolbar = new XgramNavToolbar(context, this, navigation, container);
    }
    toolbar.sync(root);
    toolbar.setShown(need);
    notifyToolbarRoot(root, toolbar.isRootOf(root));
  }

  private void notifyToolbarRoot (@Nullable MainController root, boolean shown) {
    if (root == toolbarRoot && shown == toolbarRootShown) {
      return;
    }
    MainController oldRoot = toolbarRoot;
    boolean oldShown = toolbarRootShown;
    toolbarRoot = root;
    toolbarRootShown = shown;
    if (oldRoot != null && oldRoot != root && oldShown && !oldRoot.isDestroyed()) {
      oldRoot.xgramOnToolbarChanged();
    }
    if (root != null && !root.isDestroyed() && (shown || (root == oldRoot && oldShown))) {
      root.xgramOnToolbarChanged();
    }
  }

  /**
   * Opens the modal expanded rail: the side menu while the rail shows (for DrawerController#open, keyboard shortcuts).
   * False when there is no rail, so the caller opens the drawer as usual.
   */
  public boolean expandRail () {
    if (!railShown || rail == null) {
      return false;
    }
    rail.expand();
    return true;
  }

  /** Whether {@code x} (in the activity's content layout) is over the navigation rail: no pane gesture starts there. */
  public boolean isOnRail (float x) {
    return railShown && rail != null && x >= railStart && x < railEnd;
  }

  /** Whether {@code x} (in the activity's content layout) is over the chat list pane. */
  public boolean isInListPane (float x) {
    return isActive && leftNavigation != null && x >= listStart && x < listEnd;
  }

  /** Whether {@code x} (in the activity's content layout) is over the chat pane (the only pane in one-pane layouts). */
  public boolean isInChatPane (float x) {
    if (!isActive && !railShown) {
      return true;
    }
    return x >= chatStart && x < chatEnd;
  }

  /** The current account's chat list at the root of the right pane (Telegram X's own). */
  @Nullable
  MainController railRoot () {
    return mainRoot();
  }

  /** The chat list pane's navigation while two panes are shown. */
  @Nullable
  NavigationController railLeftNavigation () {
    return isActive ? leftNavigation : null;
  }

  /** The chat list on screen: the chat list pane's with two panes, else the root. */
  @Nullable
  MainController railChatList () {
    return isActive && leftMain != null ? leftMain : mainRoot();
  }

  // The chat open on the right is highlighted in the left chat list, as on tablets.

  private static volatile long highlightedChatId;
  private static volatile int highlightedAccountId = -1;

  /** Draw path of chat list rows: one comparison when nothing is highlighted. */
  public static boolean isHighlighted (Tdlib tdlib, long chatId) {
    return chatId != 0 && chatId == highlightedChatId && tdlib != null && tdlib.id() == highlightedAccountId;
  }

  private void updateHighlight () {
    long chatId = 0;
    int accountId = -1;
    if (isActive) {
      NavigationStack stack = navigation.getStack();
      for (int i = stack.size() - 1; i >= 1; i--) {
        ViewController<?> c = stack.get(i);
        long id = c != null ? c.getChatId() : 0;
        if (id != 0) {
          chatId = id;
          accountId = c.tdlib() != null ? c.tdlib().id() : -1;
          break;
        }
      }
    }
    if (chatId != highlightedChatId || accountId != highlightedAccountId) {
      highlightedChatId = chatId;
      highlightedAccountId = accountId;
      if (leftNavigation != null) {
        invalidateChatViews(leftNavigation.getValue());
      }
    }
  }

  /** A list row (not a ChatView) that draws the chat open on the right as selected: repainted when that changes. */
  public interface HighlightAware { }

  private static void invalidateChatViews (@Nullable View view) {
    if (view instanceof org.thunderdog.challegram.component.dialogs.ChatView || view instanceof HighlightAware) {
      view.invalidate();
    } else if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        invalidateChatViews(group.getChildAt(i));
      }
    }
  }

  /** The chat list pane width: the one last set with the handle, or 38% of the screen within 320 to 420dp. */
  private int computeLeftWidth () {
    XgramSettings settings = XgramSettings.instance();
    int saved = settings.paneResizable() ? settings.paneListWidthDp() : 0;
    if (saved > 0) {
      return Screen.dp(saved);
    }
    int width = context.getResources().getConfiguration().screenWidthDp;
    int dp = Math.max(320, Math.min(420, Math.round(width * .38f)));
    return Screen.dp(dp);
  }

  /**
   * The chat list's own pane, while two panes are shown or kept for the next unfold: it keeps clear of the screen
   * edge and the divider (see MainController#xgramUpdatePaneInset).
   */
  public static boolean isPaneChatList (@Nullable ViewController<?> c) {
    if (c == null) {
      return false;
    }
    XgramTwoPane twoPane = c.context().xgramTwoPane();
    return twoPane != null && twoPane.leftMain != null && twoPane.leftMain == c;
  }

  /**
   * Space under the last chat of a chat list page in the narrow pane, so the compose button doesn't cover it (also
   * with the navigation rail: the chat list keeps its own extended FAB at the bottom end of its pane).
   */
  public static int fabSpace (@Nullable ViewController<?> c) {
    if (c == null) {
      return 0;
    }
    return isPaneChatList(c.getParentOrSelf()) ? Screen.dp(56f + 16f + 8f) : 0;
  }

  private void activate (Tdlib tdlib) {
    // The chat list kept from the last fold comes back as it was, if it is still the same account's.
    boolean reuse = leftNavigation != null && leftMain != null && leftMain.tdlib() == tdlib && !leftNavigation.isDestroyed() && !leftMain.isDestroyed();
    if (reuse) {
      detachLeft();
    } else if (leftNavigation != null) {
      removeLeft();
    }
    leftWidth = computeLeftWidth();

    if (!reuse) {
      leftNavigation = new NavigationController(context);
      leftNavigation.getStack().addChangeListener(stack -> {
        context.notifyBackPressAvailabilityChanged();
        context.runOnUiThread(this::syncRail); // the archive opened or closed in the chat list pane
      });
      leftMain = new MainController(context, tdlib);
    }
    View leftView = leftNavigation.getValue();
    leftView.setLayoutParams(new FrameLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    // Behind the main navigation and the drawer, after the focus view (and the rail).
    View railView = rail != null ? rail.view() : null;
    int railIndex = railView != null ? container.indexOfChild(railView) : -1;
    container.addView(leftView, railIndex != -1 ? railIndex + 1 : Math.min(1, container.getChildCount()));
    if (!reuse) {
      leftNavigation.initController(leftMain);
    }

    divider = new DividerView(context);
    divider.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1, Screen.dp(.5f)), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    container.addView(divider, container.indexOfChild(leftView) + 1);

    placeholder = createPlaceholder();
    container.addView(placeholder, container.indexOfChild(navigation.getValue()) + 1);
    underlay = createPlaceholder();
    container.addView(underlay, container.indexOfChild(navigation.getValue()));

    // Over both panes (and the placeholder), so it gets touches before them.
    handle = new HandleView(context);
    handle.setLayoutParams(new FrameLayout.LayoutParams(Screen.dp(HANDLE_TOUCH_WIDTH_DP), Screen.dp(HANDLE_TOUCH_HEIGHT_DP), Gravity.LEFT | Gravity.TOP));
    container.addView(handle, container.indexOfChild(placeholder) + 1);

    isActive = true;
    leftMain.xgramUpdatePaneInset();
    layout();
    android.util.Log.i("XGram", "two-pane on, chat list " + leftWidth + "px of " + context.getResources().getConfiguration().screenWidthDp + "dp");
  }

  private void layout () {
    // The panes are measured and placed by onMeasure / onLayout below.
    container.requestLayout();
  }

  // Where the rail and the panes go, in the container: set by computeSplit in onMeasure and used by onLayout.
  private int splitWidth = -1;
  private int railStart, railEnd;
  private int listStart, listEnd, chatStart, chatEnd;
  /** Book posture with a physical hinge: the space it hides, kept empty. Equal values: no gutter. */
  private int gutterStart, gutterEnd;
  /**
   * The space between the list pane and the chat: Material 3 Expressive's pane spacer, or with Expressive design off
   * the gutter the drag handle sits in. Equal values: none (a line).
   */
  private int spacerStart, spacerEnd;
  /**
   * The chat pane's width from the last split while two panes or the rail are shown, else 0. Read off the main thread
   * (MessagesController#makeGuessAboutWidth: a chat's history is laid out on the loader's thread).
   */
  private volatile int chatPaneWidth;

  /**
   * The width a chat gets on the right while two panes or the navigation rail are shown (the window less the rail, the
   * list and the spacer, and the side insets), else 0: then the chat takes the window. Any thread.
   */
  public static int chatPaneWidth (@Nullable BaseActivity context) {
    XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
    return twoPane != null ? twoPane.chatPaneWidth : 0;
  }

  /**
   * The spacer's own width. Expressive design: the 24dp spacer less the 16dp the list pane keeps clear on that side.
   * Otherwise the panes meet at a line, unless they can be resized: then the handle gets a gutter of its own width, so
   * it never covers (and never takes the touches of) either pane's content.
   */
  private static int spacerWidth () {
    if (XgramExpressive.isEnabled()) {
      return Screen.dp(SPACER_DP - PANE_INSET_DP);
    }
    XgramSettings settings = XgramSettings.instance();
    return settings.paneResizable() && !settings.paneSplitEvenly() ? Screen.dp(HANDLE_TOUCH_WIDTH_DP) : 0;
  }

  /**
   * Splits {@code width} between the rail, the chat list and the chat.
   * <ul>
   *   <li>The navigation views carry the left and right system bar and cutout insets as margins
   *   (RootFrameLayout#applyNavigationInsets): the list gets the inset on its side, the chat the one on its side,
   *   so neither runs under a side navigation bar or a cutout. The rail spans the inset on its side.</li>
   *   <li>Right-to-left languages put the rail and the list on the right. The root is always laid out left to right,
   *   so this is mirrored here by hand.</li>
   *   <li>Book posture (half open, vertical fold): the panes meet at the hinge instead of at the usual width.</li>
   *   <li>One pane (no two panes, but the rail): the chat pane takes everything after the rail.</li>
   * </ul>
   */
  private void computeSplit (ViewGroup parent, int width) {
    int insetLeft = 0, insetRight = 0;
    ViewGroup.LayoutParams params = navigation.getValue().getLayoutParams();
    if (params instanceof ViewGroup.MarginLayoutParams) {
      insetLeft = Math.max(0, ((ViewGroup.MarginLayoutParams) params).leftMargin);
      insetRight = Math.max(0, ((ViewGroup.MarginLayoutParams) params).rightMargin);
    }
    int start = Math.min(insetLeft, width), end = Math.max(start, width - insetRight);
    boolean rtl = Lang.rtl();
    splitWidth = width;
    spacerStart = spacerEnd = 0;

    if (railShown && rail != null) {
      int railWidth = Screen.dp(XgramNavRail.WIDTH);
      if (rtl) {
        rail.setStartInset(insetRight);
        railEnd = width;
        railStart = Math.max(start, end - railWidth);
        end = railStart;
      } else {
        rail.setStartInset(insetLeft);
        railStart = 0;
        railEnd = Math.min(end, start + railWidth);
        start = railEnd;
      }
    } else {
      railStart = railEnd = 0;
    }

    if (!isActive || leftNavigation == null) {
      // One pane, after the rail
      listStart = listEnd = rtl ? end : start;
      chatStart = start;
      chatEnd = end;
      gutterStart = gutterEnd = listEnd;
      return;
    }

    android.graphics.Rect hinge = hingeSplit(parent);
    if (hinge != null && hinge.left >= start && hinge.right <= end) {
      int before = hinge.left - start, after = end - hinge.right;
      int listSize = rtl ? after : before, chatSize = rtl ? before : after;
      if (listSize >= Screen.dp(HINGE_MIN_LIST_DP) && chatSize >= Screen.dp(HINGE_MIN_CHAT_DP)) {
        if (rtl) {
          chatStart = start;
          chatEnd = hinge.left;
          listStart = hinge.right;
          listEnd = end;
        } else {
          listStart = start;
          listEnd = hinge.left;
          chatStart = hinge.right;
          chatEnd = end;
        }
        gutterStart = hinge.left;
        gutterEnd = hinge.right;
        return;
      }
    }

    int spacer = spacerWidth();
    int total = Math.max(0, end - start - spacer);
    XgramSettings settings = XgramSettings.instance();
    int list;
    if (settings.paneSplitEvenly()) {
      list = evenListWidth(parent, start, end, rtl);
      if (spacer > 0) {
        // The visible 24dp gap (16dp of the list's inset and the spacer) is centered where the panes meet
        list += Screen.dp((PANE_INSET_DP - (SPACER_DP - PANE_INSET_DP)) / 2f);
      }
      list = Math.max(0, Math.min(total, list));
    } else if (dragListWidth >= 0 || (settings.paneResizable() && settings.paneListWidthDp() > 0)) {
      // Set with the handle: anything that leaves both panes usable.
      int min = Math.min(Screen.dp(HINGE_MIN_LIST_DP), total / 2);
      int max = Math.max(total / 2, total - Screen.dp(HINGE_MIN_CHAT_DP));
      list = Math.max(min, Math.min(max, dragListWidth >= 0 ? dragListWidth : leftWidth));
    } else {
      list = Math.min(leftWidth, total / 2);
    }
    if (rtl) {
      listEnd = end;
      listStart = end - list;
      chatStart = start;
      chatEnd = Math.max(start, listStart - spacer);
      gutterStart = gutterEnd = chatEnd;
      if (spacer > 0) {
        spacerStart = chatEnd;
        spacerEnd = listStart;
      }
    } else {
      listStart = start;
      listEnd = start + list;
      chatStart = Math.min(end, listEnd + spacer);
      chatEnd = end;
      gutterStart = gutterEnd = listEnd;
      if (spacer > 0) {
        spacerStart = listEnd;
        spacerEnd = chatStart;
      }
    }
  }

  /**
   * Split evenly: half the width each, meeting at the fold when the screen folds down the middle (a Fold lying flat
   * reports its fold too), so the line between the panes sits on the crease.
   */
  private int evenListWidth (ViewGroup parent, int start, int end, boolean rtl) {
    XgramWindow window = context.xgramWindow();
    XgramWindow.Fold fold = window != null ? window.fold() : null;
    if (fold != null && !fold.horizontal) {
      android.graphics.Rect hinge = window.hingeBoundsIn(parent);
      if (hinge != null) {
        int center = (hinge.left + hinge.right) / 2;
        int total = end - start;
        int list = rtl ? end - center : center - start;
        // Only a fold near the middle: anything else is not "even".
        if (list > total * 2 / 5 && list < total * 3 / 5) {
          return list;
        }
      }
    }
    return (end - start) / 2;
  }

  /** Book posture: the hinge in {@code parent}, when the panes should meet there. */
  @Nullable
  private android.graphics.Rect hingeSplit (ViewGroup parent) {
    XgramWindow window = context.xgramWindow();
    XgramWindow.Fold fold = window != null ? window.fold() : null;
    if (fold == null || !fold.separating || fold.horizontal || !XgramSettings.instance().foldHingeSplit()) {
      return null;
    }
    return window.hingeBoundsIn(parent);
  }

  private int dividerWidth () {
    if (gutterEnd > gutterStart) {
      return gutterEnd - gutterStart;
    }
    if (spacerEnd > spacerStart) {
      return spacerEnd - spacerStart;
    }
    return Math.max(1, Screen.dp(.5f));
  }

  /** Called by the activity's content layout after its own measure pass. */
  public void onMeasure (ViewGroup parent) {
    if (toolbar != null) {
      toolbar.measureHost(parent); // over the navigation's width, from above the pill to the window's bottom
    }
    boolean panes = isActive && leftNavigation != null;
    XgramNavRail rail = railShown ? this.rail : null;
    if (!panes && rail == null) {
      chatPaneWidth = 0; // the chat takes the window
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    computeSplit(parent, width);
    chatPaneWidth = Math.max(0, chatEnd - chatStart);
    int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
    int chatSpec = View.MeasureSpec.makeMeasureSpec(Math.max(0, chatEnd - chatStart), View.MeasureSpec.EXACTLY);
    navigation.getValue().measure(chatSpec, heightSpec);
    if (rail != null) {
      rail.view().measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, railEnd - railStart), View.MeasureSpec.EXACTLY), heightSpec);
    }
    if (!panes) {
      return;
    }
    leftNavigation.getValue().measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, listEnd - listStart), View.MeasureSpec.EXACTLY), heightSpec);
    if (placeholder != null) {
      placeholder.measure(chatSpec, heightSpec);
    }
    if (underlay != null) {
      underlay.measure(chatSpec, heightSpec);
    }
    if (divider != null) {
      divider.measure(View.MeasureSpec.makeMeasureSpec(dividerWidth(), View.MeasureSpec.EXACTLY), heightSpec);
    }
    if (handle != null) {
      handle.measure(
        View.MeasureSpec.makeMeasureSpec(Screen.dp(HANDLE_TOUCH_WIDTH_DP), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(Screen.dp(HANDLE_TOUCH_HEIGHT_DP), View.MeasureSpec.EXACTLY)
      );
    }
  }

  /** Called by the activity's content layout after its own layout pass. */
  public void onLayout (ViewGroup parent) {
    if (toolbar != null) {
      toolbar.layoutHost(parent); // the navigation bar's strip included (its inset is not a margin here)
    }
    boolean panes = isActive && leftNavigation != null;
    XgramNavRail rail = railShown ? this.rail : null;
    if (!panes && rail == null) {
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    if (splitWidth != width) {
      computeSplit(parent, width);
      chatPaneWidth = Math.max(0, chatEnd - chatStart);
    }
    navigation.getValue().layout(chatStart, 0, chatEnd, height);
    if (rail != null) {
      rail.view().layout(railStart, 0, railEnd, height);
    }
    if (!panes) {
      return;
    }
    leftNavigation.getValue().layout(listStart, 0, listEnd, height);
    if (placeholder != null) {
      placeholder.layout(chatStart, 0, chatEnd, height);
    }
    if (underlay != null) {
      underlay.layout(chatStart, 0, chatEnd, height);
    }
    if (divider != null) {
      int dividerWidth = divider.getMeasuredWidth();
      if (gutterEnd > gutterStart) {
        divider.layout(gutterStart, 0, gutterEnd, height); // covers the hinge
      } else if (spacerEnd > spacerStart) {
        divider.layout(spacerStart, 0, spacerEnd, height); // the pane spacer
      } else if (listStart < chatStart) {
        divider.layout(listEnd - dividerWidth, 0, listEnd, height); // list on the left
      } else {
        divider.layout(listStart, 0, listStart + dividerWidth, height); // list on the right
      }
    }
    if (handle != null) {
      // Centred in the gap between the panes, in the middle of the space under the headers.
      int line = handleCenterX();
      int w = handle.getMeasuredWidth(), h = handle.getMeasuredHeight();
      int headerHeight = org.thunderdog.challegram.navigation.HeaderView.getSize(true);
      int top = headerHeight + Math.max(0, (height - headerHeight - h) / 2);
      handle.layout(line - w / 2, top, line - w / 2 + w, top + h);
      boolean visible = canResize();
      handle.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
      if (!visible) {
        handle.cancelDrag();
      }
    }
  }

  /**
   * Where the drag handle sits: the middle of the visible gap between the list's content and the chat (the 24dp
   * spacer with Expressive design, where the list pane's own inset is part of the gap; the handle's gutter without
   * it), else on the line.
   */
  private int handleCenterX () {
    boolean listFirst = listStart < chatStart;
    if (spacerEnd > spacerStart) {
      int inset = XgramExpressive.isEnabled() ? Screen.dp(PANE_INSET_DP) : 0;
      return listFirst ? (listEnd - inset + chatStart) / 2 : (chatEnd + listStart + inset) / 2;
    }
    return listFirst ? listEnd : listStart;
  }

  // Resizing: the handle on the line between the panes drags it; the width is kept (in dp) for the next time.

  /** DragHandleTokens.ContainerWidth (24dp) across, 72dp along. */
  private static final float HANDLE_TOUCH_WIDTH_DP = 24f, HANDLE_TOUCH_HEIGHT_DP = 72f;

  @Nullable private HandleView handle;
  /** The chat list width while the handle is being dragged, else -1. */
  private int dragListWidth = -1;

  /** Not when split evenly, nor in book posture (the hinge is the line), nor with the setting off. */
  private boolean canResize () {
    XgramSettings settings = XgramSettings.instance();
    return isActive && settings.paneResizable() && !settings.paneSplitEvenly() && gutterEnd <= gutterStart;
  }

  private void onDragged (int listWidth) {
    if (dragListWidth != listWidth) {
      dragListWidth = listWidth;
      container.requestLayout();
    }
  }

  private void onDragEnd (boolean commit) {
    if (dragListWidth < 0) {
      return;
    }
    dragListWidth = -1;
    if (commit && listEnd > listStart) {
      XgramSettings.instance().setPaneListWidthDp(Math.round((listEnd - listStart) / Screen.density()));
    }
    leftWidth = computeLeftWidth();
    container.requestLayout();
  }

  /** Double tap on the handle: back to the usual width. */
  private void resetWidth () {
    dragListWidth = -1;
    XgramSettings.instance().setPaneListWidthDp(0);
    leftWidth = computeLeftWidth();
    container.requestLayout();
  }

  /**
   * The Material 3 pane drag handle: a 4dp pill in the gap between the panes (wider while held), in a 24 x 72dp touch
   * area. It takes the gesture from the panes and the back swipe as soon as it is touched.
   */
  private final class HandleView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF pill = new android.graphics.RectF();
    private final int touchSlop;
    private float downRawX;
    private int downList;
    private boolean tracking, dragging;
    private long lastTapUp;

    HandleView (android.content.Context context) {
      super(context);
      touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
      setContentDescription(Lang.getString(R.string.XgramPaneResizable));
    }

    void cancelDrag () {
      if (tracking) {
        tracking = dragging = false;
        setPressed(false);
        xgramSetHeld(false);
        onDragEnd(false);
        invalidate();
      }
    }

    // XGram: Material 3 Expressive adaptive pane drag handle (DragHandleTokens): 4 x 48dp, full corner, in outline,
    // springing (spatial fast) to 12 x 52dp with medium (12dp) corners in onSurface while pressed or dragged.
    // Nothing with Expressive design off.
    @Nullable private XgramExpressive.Spring xgramHeld;

    private void xgramSetHeld (boolean held) {
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      if (xgramHeld == null) {
        xgramHeld = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      }
      xgramHeld.animateTo(held ? 1f : 0f);
    }

    private void xgramDraw (android.graphics.Canvas c) {
      float f = xgramHeld != null ? xgramHeld.getValue() : 0f; // may overshoot a little
      float w = Math.max(Screen.dpf(2f), Screen.dpf(4f + 8f * f)), h = Screen.dpf(48f + 4f * f);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      pill.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
      paint.setColor(me.vkryl.core.ColorUtils.fromToArgb(
        XgramExpressive.color(XgramExpressive.OUTLINE),
        XgramExpressive.color(XgramExpressive.ON_SURFACE),
        Math.max(0f, Math.min(1f, f))));
      float radius = Math.min(w / 2f, Screen.dpf(XgramExpressive.CORNER_M));
      c.drawRoundRect(pill, radius, radius, paint);
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      if (XgramExpressive.isEnabled()) {
        xgramDraw(c);
        return;
      }
      boolean held = tracking;
      float w = Screen.dpf(held ? 8f : 4f), h = Screen.dpf(held ? 52f : 48f);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      pill.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
      paint.setColor(Theme.getColor(held ? ColorId.text : ColorId.iconLight));
      c.drawRoundRect(pill, w / 2f, w / 2f, paint);
    }

    @Override
    public boolean onTouchEvent (android.view.MotionEvent e) {
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN: {
          if (!canResize()) {
            return false;
          }
          ViewGroup parent = (ViewGroup) getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          downRawX = e.getRawX();
          downList = listEnd - listStart;
          tracking = true;
          dragging = false;
          setPressed(true);
          xgramSetHeld(true);
          invalidate();
          return true;
        }
        case android.view.MotionEvent.ACTION_MOVE: {
          if (!tracking) {
            return false;
          }
          float dx = e.getRawX() - downRawX;
          if (!dragging && Math.abs(dx) > touchSlop) {
            dragging = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
          }
          if (dragging) {
            onDragged(Math.round(Lang.rtl() ? downList - dx : downList + dx));
          }
          return true;
        }
        case android.view.MotionEvent.ACTION_UP: {
          if (!tracking) {
            return false;
          }
          tracking = false;
          setPressed(false);
          xgramSetHeld(false);
          if (dragging) {
            dragging = false;
            onDragEnd(true);
          } else {
            long now = android.os.SystemClock.uptimeMillis();
            if (lastTapUp != 0 && now - lastTapUp <= android.view.ViewConfiguration.getDoubleTapTimeout()) {
              lastTapUp = 0;
              performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
              resetWidth();
            } else {
              lastTapUp = now;
            }
          }
          invalidate();
          return true;
        }
        case android.view.MotionEvent.ACTION_CANCEL: {
          cancelDrag();
          return true;
        }
      }
      return tracking;
    }
  }

  /** @param keepLeft keep the chat list (detached) for the next time two panes are shown */
  private void deactivate (boolean keepLeft) {
    if (keepLeft) {
      detachLeft();
    } else {
      removeLeft();
    }
    isActive = false;
    chatPaneWidth = 0; // until the next split (with the rail), the chat takes the window
    container.requestLayout();
  }

  private void hideRoot (MainController root) {
    View rootView = root.getValue();
    View headerCell = root.getCustomHeaderCell();
    if (rootView == hiddenRootView && headerCell == hiddenHeaderCell) {
      return;
    }
    showRoot();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
      rootView.setClipBounds(new android.graphics.Rect());
      if (headerCell != null) {
        headerCell.setClipBounds(new android.graphics.Rect());
      }
    }
    hiddenRootView = rootView;
    hiddenHeaderCell = headerCell;
  }

  private void showRoot () {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (hiddenRootView != null) {
        hiddenRootView.setClipBounds(null);
      }
      if (hiddenHeaderCell != null) {
        hiddenHeaderCell.setClipBounds(null);
      }
    }
    hiddenRootView = null;
    hiddenHeaderCell = null;
  }

  private void removeLeft () {
    detachLeft();
    if (leftNavigation != null) {
      leftNavigation.destroy();
      leftNavigation = null;
    }
    leftMain = null;
    leftHeaderMain = null; // nor the destroyed chat list's pages and views kept through it
    leftHeaderRail = false;
  }

  /** Takes the panes off screen. The chat list itself stays alive, so unfolding brings it back where it was. */
  private void detachLeft () {
    showRoot();
    if (leftNavigation != null) {
      container.removeView(leftNavigation.getValue());
    }
    if (divider != null) {
      container.removeView(divider);
      divider = null;
    }
    if (placeholder != null) {
      container.removeView(placeholder);
      placeholder = null;
    }
    if (underlay != null) {
      container.removeView(underlay);
      underlay = null;
    }
    if (handle != null) {
      handle.cancelDrag();
      container.removeView(handle);
      handle = null;
    }
  }

  // Placeholder

  private View createPlaceholder () {
    if (XgramExpressive.isEnabled()) {
      return createM3Placeholder();
    }
    FrameLayout layout = new FrameLayout(context);
    layout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    layout.setBackgroundColor(Theme.getColor(ColorId.background));
    layout.setClickable(true); // the hidden chat list behind must not get touches

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    content.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

    ImageView icon = new ImageView(context);
    icon.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_forum_96));
    icon.setColorFilter(Theme.getColor(ColorId.iconLight));
    content.addView(icon, new LinearLayout.LayoutParams(Screen.dp(96f), Screen.dp(96f)));

    TextView text = new TextView(context);
    text.setText(Lang.getString(R.string.XgramSelectChat));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    text.setTextColor(Theme.getColor(ColorId.textLight));
    text.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    textParams.topMargin = Screen.dp(12f);
    content.addView(text, textParams);

    layout.addView(content);

    // An empty header bar, so the status bar area matches the chat list pane's header.
    View header = new View(context);
    header.setBackgroundColor(Theme.headerColor());
    layout.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, org.thunderdog.challegram.navigation.HeaderView.getSize(true), Gravity.TOP));
    layout.setTag(new PlaceholderParts(false, layout, null, icon, text, header));
    return layout;
  }

  /**
   * Material 3 Expressive empty detail pane, built like a chat: the app bar band on surfaceContainer, then a surface
   * container with 28dp (extra large) top corners, and in it the outlined forum icon and a bodyLarge line in
   * onSurfaceVariant.
   */
  private View createM3Placeholder () {
    FrameLayout layout = new FrameLayout(context);
    layout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    layout.setBackgroundColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER));
    layout.setClickable(true); // the hidden chat list behind must not get touches

    FrameLayout surface = new FrameLayout(context);
    GradientDrawable background = new GradientDrawable();
    background.setColor(XgramExpressive.color(XgramExpressive.SURFACE));
    float radius = Screen.dpf(XgramExpressive.CORNER_XL);
    background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
    surface.setBackground(background);
    FrameLayout.LayoutParams surfaceParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    surfaceParams.topMargin = org.thunderdog.challegram.navigation.HeaderView.getSize(true);
    layout.addView(surface, surfaceParams);

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    int padding = Screen.dp(24f);
    content.setPadding(padding, padding, padding, padding);

    int onSurfaceVariant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    ImageView icon = new ImageView(context);
    boolean classic = com.fahadali.xgram.appearance.XgramIconPack.current() == com.fahadali.xgram.appearance.XgramIconPack.PACK_TELEGRAM_X;
    icon.setImageResource(classic ? R.drawable.xgl_forum_96 : com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_forum_96));
    icon.setColorFilter(onSurfaceVariant);
    content.addView(icon, new LinearLayout.LayoutParams(Screen.dp(96f), Screen.dp(96f)));

    TextView text = new TextView(context);
    text.setText(Lang.getString(R.string.XgramSelectChat));
    XgramExpressive.applyText(text, XgramExpressive.TS_BODY_L, false);
    text.setTextColor(onSurfaceVariant);
    text.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    textParams.topMargin = Screen.dp(16f);
    content.addView(text, textParams);

    surface.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    layout.setTag(new PlaceholderParts(true, layout, background, icon, text, null));
    return layout;
  }

  private void updatePlaceholder () {
    if (placeholder != null) {
      boolean atRoot = navigation.getStack().size() <= 1 && navigation.getCurrentStackItem() instanceof MainController;
      placeholder.setVisibility(isActive && atRoot ? View.VISIBLE : View.GONE);
    }
  }

  public void onConfigurationChanged (Configuration newConfig) {
    if (leftNavigation != null) {
      leftNavigation.configurationChanged(newConfig);
    }
    leftWidth = computeLeftWidth();
    update();
  }

  public void onThemeChanged () {
    if (placeholder != null) {
      boolean visible = placeholder.getVisibility() == View.VISIBLE;
      container.removeView(placeholder);
      placeholder = createPlaceholder();
      placeholder.setVisibility(visible ? View.VISIBLE : View.GONE);
      container.addView(placeholder, container.indexOfChild(navigation.getValue()) + 1);
      layout();
    }
    if (divider != null) {
      divider.invalidate();
    }
    if (handle != null) {
      handle.invalidate();
    }
    if (underlay != null) {
      container.removeView(underlay);
      underlay = createPlaceholder();
      container.addView(underlay, container.indexOfChild(navigation.getValue()));
      layout();
    }
    if (rail != null) {
      rail.onThemeChanged();
    }
    if (toolbar != null) {
      toolbar.onThemeChanged();
    }
  }

  @Override
  public void onStackChanged (NavigationStack stack) {
    // The stack also changes on login, logout and account switches: re-check everything.
    context.runOnUiThread(this::update);
  }

  // Back press

  /** Back press when the right pane has nothing left to close: go back in the left pane. */
  public @BackPressMode int performOnBackPressed (boolean fromTop, boolean commit) {
    if (isActive && leftNavigation != null) {
      return leftNavigation.performOnBackPressed(fromTop, commit);
    }
    return BackPressMode.SYSTEM_ACTION_REQUIRED;
  }

  public boolean isLeftNavigation (@Nullable NavigationController navigation) {
    return navigation != null && navigation == leftNavigation;
  }

  /** The chat list in the left pane while two panes are shown (keyboard shortcuts act on it), else null. */
  @Nullable
  public MainController leftMain () {
    return isActive ? leftMain : null;
  }

  /**
   * Whether a screen opened from {@code context} should replace what the right pane shows instead of stacking on
   * it: in two panes the chat list stays on the left, so Back never needs to return to an earlier chat or topic
   * list opened from there. True for the left pane's screens and for the right pane's own chat list.
   */
  public static boolean replacesRightPane (@Nullable ViewController<?> context) {
    if (context == null) {
      return false;
    }
    XgramTwoPane twoPane = context.context().xgramTwoPane();
    if (twoPane == null || !twoPane.isActive) {
      return false;
    }
    // A chat list inside the main screen's pager has no navigation of its own: its parent (the MainController) does.
    ViewController<?> owner = context.getParentOrSelf();
    NavigationController own = owner.navigationController();
    return twoPane.isLeftNavigation(own) || twoPane.isHiddenRoot(owner);
  }

  /**
   * Puts {@code c} in the chat list pane, over its chat list: anything else above the chat list there (the archive,
   * a screen opened from the rail) goes first. False when two panes are not shown (or the pane is busy): the caller
   * opens it as usual.
   */
  public static boolean showOnLeft (ViewController<?> context, ViewController<?> c) {
    XgramTwoPane twoPane = context.context().xgramTwoPane();
    if (twoPane == null || !twoPane.isActive) {
      return false;
    }
    NavigationController left = twoPane.leftNavigation;
    if (left == null || left.isDestroyed() || left.isAnimating() || left.getStackSize() == 0) {
      return false;
    }
    if (left.getStackSize() > 1) {
      left.setControllerAnimated(c, true, true); // keeps the chat list at the root
    } else {
      left.navigateTo(c);
    }
    return true;
  }

  /** The screen on top of the chat list pane while two panes are shown, else null. */
  @Nullable
  public static ViewController<?> leftTop (@Nullable BaseActivity context) {
    XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
    if (twoPane == null || !twoPane.isActive || twoPane.leftNavigation == null || twoPane.leftNavigation.isDestroyed()) {
      return null;
    }
    return twoPane.leftNavigation.getCurrentStackItem();
  }

  /** Whether the chat list pane (two panes shown) has a {@code type} screen on top. */
  public static boolean isOnLeftTop (@Nullable BaseActivity context, Class<?> type) {
    ViewController<?> top = leftTop(context);
    return top != null && type.isInstance(top);
  }

  /** Puts {@code c} on the right pane, replacing anything above the chat list there. */
  public static void showOnRight (ViewController<?> context, ViewController<?> c) {
    NavigationController navigation = context.context().navigation();
    if (navigation == null) {
      return;
    }
    if (navigation.getStackSize() > 1) {
      navigation.setControllerAnimated(c, true, true);
    } else {
      navigation.navigateTo(c);
    }
  }

  public void destroy () {
    navigation.getStack().removeChangeListener(this);
    org.thunderdog.challegram.theme.ThemeManager.instance().removeThemeListener(themeListener);
    XgramWindow window = context.xgramWindow();
    if (window != null) {
      window.removeListener(windowListener);
    }
    if (rail != null) {
      rail.destroy();
      container.removeView(rail.view());
      rail = null;
    }
    if (toolbar != null) {
      XgramNavToolbar old = toolbar;
      toolbar = null;
      old.destroy();
    }
    toolbarRoot = null;
    toolbarRootShown = false;
    if (railShown) {
      org.thunderdog.challegram.navigation.DrawerController drawer = context.getDrawer();
      if (drawer != null) {
        drawer.xgramSetSwipeEnabled(true);
      }
    }
    railShown = false;
    removeLeft();
    isActive = false;
    chatPaneWidth = 0;
  }

  /** The left chat list's screens hear about split screen too (the activity only tells the right pane's). */
  public void onMultiWindowModeChanged (boolean inMultiWindowMode) {
    if (leftNavigation != null) {
      for (ViewController<?> c : leftNavigation.getStack().getAll()) {
        c.onMultiWindowModeChanged(inMultiWindowMode);
      }
    }
  }

  /**
   * Between the panes. Material 3 Expressive: the pane spacer (or the hinge), on the app bar's layer, no line.
   * Otherwise a line: across the status bar and headers a slightly darker header colour (a light separator there
   * looked like a white line between the two headers); below them, the usual separator. As wide as the drag handle's
   * gutter: the same header strip across it, the window background below with the separator down the middle.
   */
  private static final class DividerView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint();

    DividerView (android.content.Context context) {
      super(context);
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      if (XgramExpressive.isEnabled()) {
        paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        return;
      }
      int headerHeight = Math.min(getHeight(), org.thunderdog.challegram.navigation.HeaderView.getSize(true));
      int headerShade = me.vkryl.core.ColorUtils.fromToArgb(Theme.getColor(ColorId.headerBackground), 0xFF000000, .14f);
      int line = Math.max(1, Screen.dp(.5f));
      if (getWidth() > Math.max(line, Screen.dp(1f))) {
        // A gutter (the drag handle's, or the hinge): the header colour and the window background, so the two headers
        // read as one bar, with the usual thin line down the middle (no dark band across the gutter)
        int lineStart = (getWidth() - line) / 2;
        paint.setColor(Theme.getColor(ColorId.headerBackground));
        c.drawRect(0, 0, getWidth(), headerHeight, paint);
        paint.setColor(headerShade);
        c.drawRect(lineStart, 0, lineStart + line, headerHeight, paint);
        paint.setColor(Theme.getColor(ColorId.background));
        c.drawRect(0, headerHeight, getWidth(), getHeight(), paint);
        paint.setColor(Theme.getColor(ColorId.separator));
        c.drawRect(lineStart, headerHeight, lineStart + line, getHeight(), paint);
        return;
      }
      paint.setColor(headerShade);
      c.drawRect(0, 0, getWidth(), headerHeight, paint);
      paint.setColor(Theme.getColor(ColorId.separator));
      c.drawRect(0, headerHeight, getWidth(), getHeight(), paint);
    }
  }
}
