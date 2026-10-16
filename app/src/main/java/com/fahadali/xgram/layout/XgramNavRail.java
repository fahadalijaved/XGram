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
import android.text.TextPaint;
import android.text.TextUtils;
import android.annotation.SuppressLint;
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
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibBadgeCounter;
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
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ContactsController;
import org.thunderdog.challegram.ui.CreateChannelController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.PeopleController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * The Material 3 Expressive navigation rail of wide windows (a Fold unfolded, tablets, desktop windows), at the start
 * of the window, before the chat list pane. {@link XgramTwoPane} owns it: it decides when it shows (Expressive design,
 * a window at least 600dp wide or two panes), lays it out and keeps its destination in step with what is on screen.
 * <ul>
 *   <li>Collapsed rail: the menu button, the "New chat" FAB (the chat list's own compose button moves here), then the
 *   destinations (Chats, Contacts, Calls, Saved, Settings, Archive, as the side menu settings allow), the active one in
 *   a tonal pill that springs between them.</li>
 *   <li>Modal expanded rail (the menu button): slides in over the content with a scrim and holds the whole side
 *   menu: the destinations with their labels, the accounts, XGram's own pages and the switches.</li>
 *   <li>FAB menu (the FAB): new chat, group, channel or secret chat, unfolding below the FAB.</li>
 * </ul>
 * Values are androidx.compose.material3 1.5.0-beta01 tokens: NavigationRailCollapsedTokens, NavigationRailExpandedTokens,
 * NavigationRailVerticalItemTokens, NavigationRailHorizontalItemTokens, NavigationRailColorTokens, WideNavigationRail,
 * FabBaselineTokens, ExtendedFabSmallTokens, FabMenuBaselineTokens and SmallIconButtonTokens. Everything is drawn with
 * properties and springs: nothing is laid out again while it moves.
 */
public final class XgramNavRail {
  // Collapsed rail

  /** NavigationRailCollapsedTokens.ContainerWidth. */
  static final float WIDTH = 96f;
  /** WideNavigationRail item horizontal padding: the indicator starts 20dp in, collapsed and expanded alike. */
  private static final float ITEM_PADDING = 20f;
  /** NavigationRailVerticalItemTokens: 56 x 32dp full-corner indicator, 24dp icon, 4dp to the labelMedium label. */
  private static final float INDICATOR_WIDTH = 56f, INDICATOR_HEIGHT = 32f, ICON = 24f, ICON_LABEL_SPACE = 4f, LABEL_LINE = 16f;
  /** WideNavigationRail TopIconItemMinHeight, and NavigationRailCollapsedTokens.ItemVerticalSpace. */
  private static final float ITEM_HEIGHT = 64f, ITEM_SPACE = 4f;
  private static final float ITEM_CONTENT = INDICATOR_HEIGHT + ICON_LABEL_SPACE + LABEL_LINE;
  /** WideNavigationRail header padding: from the header (menu button, FAB) to the first destination. */
  private static final float HEADER_PADDING = 40f;
  /** The menu button sits in the 56dp app bar band of the panes, with the 40dp state layer of a small icon button. */
  private static final float BAND = 56f, MENU_STATE = 40f;
  /** FabBaselineTokens: 56dp, CornerLarge. Lowered (level 0) inside the rail; 4dp under the app bar band. */
  private static final float FAB_SIZE = 56f, FAB_CORNER = 16f, FAB_GAP = 4f;

  // Modal expanded rail

  /** NavigationRailExpandedTokens: 220 to 360dp, surfaceContainer, CornerLarge, level 2 (3dp). */
  private static final float EXPANDED_MIN = 220f, EXPANDED_MAX = 360f, EXPANDED_CORNER = 16f;
  /** At least this much scrim stays visible next to the panel on a narrow window. */
  private static final float EXPANDED_SCRIM_MIN = 56f;
  /** NavigationRailHorizontalItemTokens: 56dp full-corner indicator, 16dp leading, 8dp icon to labelLarge, 16dp trailing. */
  private static final float ROW_HEIGHT = 56f, ROW_LEADING = 16f, ROW_ICON_LABEL = 8f, ROW_TRAILING = 16f;
  private static final float AVATAR = 32f, SECTION_HEIGHT = 48f, SECTION_GAP = 8f, DIVIDER_HEIGHT = 17f, SWITCH_RESERVE = 52f + 8f;
  private static final float TEXT_START = ITEM_PADDING + ROW_LEADING + ICON + ROW_ICON_LABEL;
  private static final float AVATAR_TEXT_START = ITEM_PADDING + ROW_LEADING + ICON / 2f + AVATAR / 2f + ROW_ICON_LABEL;
  /** ExtendedFabSmallTokens: 16dp leading, 24dp icon, 8dp to the labelLarge label, 16dp trailing. */
  private static final float EXTENDED_FAB_LEADING = 16f, EXTENDED_FAB_ICON_LABEL = 8f, EXTENDED_FAB_TRAILING = 16f;
  /** The panel's background runs this far past its start edge, so the opening spring's overshoot never shows a gap. */
  private static final float OVERSHOOT_COVER = 64f;

  // FAB menu: FabMenuBaselineTokens

  private static final float MENU_ITEM_HEIGHT = 56f, MENU_ITEM_SPACE = 4f, MENU_CLOSE_GAP = 8f, MENU_ITEM_PADDING = 24f, MENU_ICON_LABEL = 8f, MENU_CLOSE_ICON = 20f;
  /** Each item starts unfolding this much (of the spring's travel) after the one above it. */
  private static final float MENU_STAGGER = .08f;

  // Destinations

  static final int DEST_CHATS = 0, DEST_CONTACTS = 1, DEST_CALLS = 2, DEST_SAVED = 3, DEST_SETTINGS = 4, DEST_ARCHIVE = 5;

  /** Filled (the active destination) Material icons; with another icon pack, that pack's drawing of them. */
  private static final int[] DEST_FILLED = {
    R.drawable.baseline_chat_bubble_24, R.drawable.baseline_perm_contact_calendar_24, R.drawable.baseline_call_24,
    R.drawable.baseline_bookmark_24, R.drawable.baseline_settings_24, R.drawable.baseline_archive_24
  };
  /** Outlined (every other destination): the XGram Line family, as the rest of Material 3 Expressive with the Classic pack. */
  private static final int[] DEST_OUTLINED = {
    R.drawable.xgl_chat_bubble, R.drawable.xgl_contacts, R.drawable.xgl_phone,
    R.drawable.xgl_bookmark, R.drawable.xgl_settings, R.drawable.xgl_archive
  };
  /** Collapsed labels: short enough for a 96dp rail. */
  private static final int[] DEST_SHORT = {R.string.Chats, R.string.Contacts, R.string.Calls, R.string.XgramNavRailSaved, R.string.Settings, R.string.XgramNavRailArchive};
  private static final int[] DEST_LONG = {R.string.Chats, R.string.Contacts, R.string.Calls, R.string.XgramNavRailSavedMessages, R.string.Settings, R.string.XgramNavRailArchivedChats};
  /** Side menu keys (XgramDrawerItemsController): hidden there, hidden here, and in the same order. */
  private static final String[] DEST_KEY = {null, "contacts", "calls", "saved", "settings", null};

  private static final int[] FAB_MENU_IDS = {R.id.btn_float_newChat, R.id.btn_float_newGroup, R.id.btn_float_newChannel, R.id.btn_float_newSecretChat};
  private static final int[] FAB_MENU_FILLED = {R.drawable.baseline_person_24, R.drawable.baseline_group_24, R.drawable.baseline_bullhorn_24, R.drawable.baseline_lock_24};
  private static final int[] FAB_MENU_OUTLINED = {R.drawable.xgl_person, R.drawable.xgl_group, R.drawable.xgl_bullhorn, R.drawable.xgl_lock};
  private static final int[] FAB_MENU_LABELS = {R.string.XgramFabNewChat, R.string.XgramNavRailNewGroup, R.string.XgramNavRailNewChannel, R.string.XgramNavRailNewSecretChat};

  private final BaseActivity context;
  private final XgramTwoPane owner;
  private final TextPaint labelMedium, labelLarge, titleSmall, titleMedium;
  private final float labelMediumCenter, labelLargeCenter, titleSmallCenter, titleMediumCenter;
  private final RailView rail;

  private int[] destinations = new int[0];
  private int selected = DEST_CHATS;

  @Nullable private PopupLayout expandedPopup, fabMenuPopup;
  @Nullable private ExpandedLayout expandedLayout;
  @Nullable private FabMenuLayout fabMenuLayout;

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
    this.labelMedium = newPaint(XgramExpressive.TS_LABEL_M);
    this.labelLarge = newPaint(XgramExpressive.TS_LABEL_L);
    this.titleSmall = newPaint(XgramExpressive.TS_TITLE_S);
    this.titleMedium = newPaint(XgramExpressive.TS_TITLE_M);
    this.labelMediumCenter = centerOffset(labelMedium);
    this.labelLargeCenter = centerOffset(labelLarge);
    this.titleSmallCenter = centerOffset(titleSmall);
    this.titleMediumCenter = centerOffset(titleMedium);
    this.rail = new RailView(context);
    ThemeManager.instance().addThemeListener(themeListener);
  }

  // API for XgramTwoPane

  View view () {
    return rail;
  }

  /** Width of the rail view: the 96dp rail and the system inset on its side (a cutout, a side navigation bar). */
  int width () {
    return Screen.dp(WIDTH) + rail.startInset;
  }

  void setStartInset (int px) {
    rail.setStartInset(px);
  }

  /** The destinations this account and the side menu settings allow, in the side menu's order (Chats first). */
  static int[] destinations (@Nullable Tdlib tdlib, boolean withArchive) {
    XgramSettings xgram = XgramSettings.instance();
    ArrayList<Integer> list = new ArrayList<>();
    list.add(DEST_CHATS);
    if (!xgram.drawerItemHidden("contacts")) {
      list.add(DEST_CONTACTS);
    }
    // As in the side menu: without folders Calls is a tab of the chat list itself.
    if (Settings.instance().chatFoldersEnabled() && !xgram.drawerItemHidden("calls")) {
      list.add(DEST_CALLS);
    }
    if (!xgram.drawerItemHidden("saved")) {
      list.add(DEST_SAVED);
    }
    list.add(DEST_SETTINGS);
    XgramReorder.sort(XgramReorder.DRAWER, list, dest -> DEST_KEY[dest]);
    if (withArchive && tdlib != null && tdlib.hasArchivedChats()) {
      list.add(DEST_ARCHIVE);
    }
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  /** Whether the header and {@code count} destinations fit a window {@code heightDp} tall. */
  static boolean fits (int count, int heightDp) {
    float statusBarDp = HeaderView.getTopOffset() / Screen.density();
    float needed = statusBarDp + BAND + FAB_GAP + FAB_SIZE + HEADER_PADDING + count * ITEM_HEIGHT + Math.max(0, count - 1) * ITEM_SPACE + 24f;
    return heightDp >= needed;
  }

  /** New destinations (when they changed) and the one on screen now. */
  void sync (int[] destinations) {
    if (!Arrays.equals(this.destinations, destinations)) {
      this.destinations = destinations;
      rail.setDestinations(destinations);
    } else {
      rail.updateLabels();
    }
    setSelected(computeSelected(), rail.isLaidOut());
  }

  void onThemeChanged () {
    invalidateAll();
  }

  /** Closes the expanded rail and the FAB menu at once (the rail is going away). */
  void dismissPopups () {
    if (expandedPopup != null) {
      expandedPopup.hideWindow(false);
      expandedPopup = null;
      expandedLayout = null;
    }
    if (fabMenuPopup != null) {
      fabMenuPopup.hideWindow(false);
      fabMenuPopup = null;
      fabMenuLayout = null;
    }
    rail.fab.setVisibility(View.VISIBLE);
  }

  void destroy () {
    dismissPopups();
    ThemeManager.instance().removeThemeListener(themeListener);
  }

  private void invalidateAll () {
    invalidateTree(rail);
    if (expandedLayout != null) {
      invalidateTree(expandedLayout);
    }
    if (fabMenuLayout != null) {
      invalidateTree(fabMenuLayout);
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

  // Destination on screen

  private void setSelected (int dest, boolean animated) {
    selected = dest;
    rail.setSelected(dest, animated);
  }

  private int computeSelected () {
    MainController root = owner.railRoot();
    Tdlib tdlib = root != null ? root.tdlib() : null;
    NavigationController navigation = context.navigation();
    NavigationStack stack = navigation != null ? navigation.getStack() : null;
    ViewController<?> first = stack != null && stack.size() > 1 ? stack.get(1) : null;
    int dest = destinationOf(first, tdlib);
    if (dest == DEST_CHATS) {
      // Two panes: the archive open in the chat list pane
      NavigationController left = owner.railLeftNavigation();
      if (left != null && isArchive(left.getCurrentStackItem())) {
        dest = DEST_ARCHIVE;
      }
    }
    return indexOf(destinations, dest) != -1 ? dest : DEST_CHATS;
  }

  /** The destination a screen opened above the chat list belongs to (anything else, a chat for one, is Chats). */
  private static int destinationOf (@Nullable ViewController<?> c, @Nullable Tdlib tdlib) {
    if (c == null) {
      return DEST_CHATS;
    }
    if (c instanceof PeopleController) {
      return DEST_CONTACTS;
    }
    if (c instanceof CallListController) {
      return DEST_CALLS;
    }
    if (c instanceof SettingsController) {
      return DEST_SETTINGS;
    }
    if (isArchive(c)) {
      return DEST_ARCHIVE;
    }
    long chatId = c.getChatId();
    if (chatId != 0 && tdlib != null && tdlib.isSelfChat(chatId)) {
      return DEST_SAVED;
    }
    return DEST_CHATS;
  }

  private static boolean isArchive (@Nullable ViewController<?> c) {
    return c instanceof ChatsController && TD.isChatListArchive(((ChatsController) c).chatList());
  }

  private static int indexOf (int[] array, int value) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return -1;
  }

  // Actions (the side menu's, see DrawerController#onClick)

  private boolean canNavigate () {
    NavigationController navigation = context.navigation();
    return navigation != null && !navigation.isAnimating() && !navigation.isDestroyed();
  }

  /** A destination screen replaces whatever the right pane (or the single pane) shows above the chat list. */
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

  /** The indicator moves on the tap; the stack change that follows confirms it (or puts it back). */
  private void openDestination (int dest, ViewController<?> c) {
    if (open(c)) {
      setSelected(dest, true);
    }
  }

  /** Back to {@code index} in {@code navigation}: the screens between are dropped, the top one animates away. */
  private static void popTo (NavigationController navigation, int index) {
    if (navigation.isAnimating()) {
      return;
    }
    NavigationStack stack = navigation.getStack();
    int current = stack.getCurrentIndex();
    if (current <= index) {
      return;
    }
    for (int i = current - 1; i > index; i--) {
      stack.destroy(i);
    }
    navigation.navigateBack();
  }

  void onDestinationClick (int dest) {
    MainController root = owner.railRoot();
    NavigationController navigation = context.navigation();
    if (root == null || navigation == null || !canNavigate()) {
      return;
    }
    final Tdlib tdlib = root.tdlib();
    final NavigationController left = owner.railLeftNavigation();
    if (dest == selected && dest != DEST_CHATS) {
      // The destination again: back to its first screen
      if (dest == DEST_ARCHIVE && left != null) {
        popTo(left, 1);
      } else {
        popTo(navigation, 1);
      }
      return;
    }
    switch (dest) {
      case DEST_CHATS: {
        if (left != null && left.getStackSize() > 1) {
          popTo(left, 0); // the archive in the chat list pane
          setSelected(DEST_CHATS, true);
          return;
        }
        NavigationStack stack = navigation.getStack();
        ViewController<?> first = stack.size() > 1 ? stack.get(1) : null;
        boolean chatBeside = left != null && first != null && destinationOf(first, tdlib) == DEST_CHATS;
        if (stack.size() > 1 && !chatBeside) {
          popTo(navigation, 0);
          setSelected(DEST_CHATS, true);
        } else {
          MainController list = owner.railChatList();
          if (list != null) {
            list.scrollToFirstPosition();
          }
        }
        break;
      }
      case DEST_CONTACTS: {
        tdlib.contacts().startSyncIfNeeded(context, true, () -> {
          PeopleController c = new PeopleController(context, tdlib);
          c.setNeedSearch();
          openDestination(DEST_CONTACTS, c);
        });
        break;
      }
      case DEST_CALLS: {
        openDestination(DEST_CALLS, new CallListController(context, tdlib));
        break;
      }
      case DEST_SAVED: {
        openSavedMessages(tdlib);
        setSelected(DEST_SAVED, true);
        break;
      }
      case DEST_SETTINGS: {
        openDestination(DEST_SETTINGS, new SettingsController(context, tdlib));
        break;
      }
      case DEST_ARCHIVE: {
        ChatsController c = new ChatsController(context, tdlib);
        c.setArguments(new ChatsController.Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setNeedMessagesSearch(true));
        if (left != null) {
          // A chat list: it opens in the chat list pane, as from the Archived chats row
          if (!left.isAnimating()) {
            if (left.getStackSize() > 1) {
              left.setControllerAnimated(c, true, true);
            } else {
              left.navigateTo(c);
            }
            setSelected(DEST_ARCHIVE, true);
          }
        } else {
          openDestination(DEST_ARCHIVE, c);
        }
        break;
      }
    }
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

  private void onFabMenuAction (int id) {
    MainController root = owner.railRoot();
    if (root == null || !canNavigate()) {
      return;
    }
    Tdlib tdlib = root.tdlib();
    if (id == R.id.btn_float_newChannel) {
      open(new CreateChannelController(context, tdlib));
      return;
    }
    int mode;
    if (id == R.id.btn_float_newGroup) {
      mode = ContactsController.MODE_NEW_GROUP;
    } else if (id == R.id.btn_float_newSecretChat) {
      mode = ContactsController.MODE_NEW_SECRET_CHAT;
    } else {
      mode = ContactsController.MODE_NEW_CHAT;
    }
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(mode);
    open(c);
  }

  // Popups

  private static boolean isShowing (@Nullable PopupLayout popup) {
    return popup != null && !popup.isWindowHidden() && !popup.isDestroyed();
  }

  private PopupLayout newPopup () {
    PopupLayout popup = new PopupLayout(context);
    popup.setOverlayStatusBar(true);
    popup.init(true);
    popup.setNeedFullScreen(Settings.instance().useEdgeToEdge());
    return popup;
  }

  /** The menu button: the modal expanded rail. */
  void expand () {
    if (isShowing(expandedPopup) || owner.railRoot() == null || context.isActivityBusyWithSomething()) {
      return;
    }
    if (isShowing(fabMenuPopup)) {
      fabMenuPopup.hideWindow(true);
    }
    PopupLayout popup = newPopup();
    ExpandedLayout layout = new ExpandedLayout(context);
    popup.setDismissListener(p -> {
      if (expandedPopup == p) {
        expandedPopup = null;
        expandedLayout = null;
      }
    });
    expandedPopup = popup;
    expandedLayout = layout;
    popup.showAnimatedPopupView(layout, layout);
  }

  /** The FAB: what to start, unfolding below it. */
  void openFabMenu () {
    if (isShowing(fabMenuPopup) || owner.railRoot() == null || context.isActivityBusyWithSomething()) {
      return;
    }
    PopupLayout popup = newPopup();
    popup.setHideBackground(true); // no scrim (Compose FloatingActionButtonMenu): a touch outside closes it
    FabMenuLayout layout = new FabMenuLayout(context);
    popup.setDismissListener(p -> {
      if (fabMenuPopup == p) {
        fabMenuPopup = null;
        fabMenuLayout = null;
      }
      rail.fab.setVisibility(View.VISIBLE);
    });
    fabMenuPopup = popup;
    fabMenuLayout = layout;
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

  /**
   * A view with an M3 state layer (ripple: hover, focus and press) on a shape inside it: the indicator of a
   * destination, the circle of an icon button, the container of a FAB. The state layer is the content color at 10%.
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

  private final class RailView extends ViewGroup {
    private final MenuView menu;
    private final FabView fab;
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
      menu.setContentDescription(Lang.getString(R.string.XgramNavRailOpenMenu));
      menu.setOnClickListener(v -> expand());
      addView(menu);
      fab = new FabView(context);
      fab.setContentDescription(Lang.getString(R.string.XgramFabNewChat));
      fab.setOnClickListener(v -> openFabMenu());
      addView(fab);
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
      return HeaderView.getTopOffset() + Screen.dp(BAND + FAB_GAP + FAB_SIZE + HEADER_PADDING);
    }

    private int pitch () {
      return Screen.dp(ITEM_HEIGHT) + Screen.dp(ITEM_SPACE);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      int contentWidth = Screen.dp(WIDTH);
      measureExactly(menu, contentWidth, Screen.dp(BAND));
      measureExactly(fab, Screen.dp(FAB_SIZE), Screen.dp(FAB_SIZE));
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
      int fabTop = top + Screen.dp(BAND + FAB_GAP);
      int fabLeft = left + Screen.dp((WIDTH - FAB_SIZE) / 2f);
      fab.layout(fabLeft, fabTop, fabLeft + Screen.dp(FAB_SIZE), fabTop + Screen.dp(FAB_SIZE));
      int y = itemsTop(), pitch = pitch(), itemHeight = Screen.dp(ITEM_HEIGHT);
      for (ItemView item : items) {
        item.layout(left, y, left + contentWidth, y + itemHeight);
        y += pitch;
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
        float top = first.getTop() + Screen.dpf((ITEM_HEIGHT - ITEM_CONTENT) / 2f) + indicatorSpring.getValue() * pitch();
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

  /** The menu button (open: the expanded rail's, which closes it): a 24dp glyph in a 40dp state layer, 48dp from the start. */
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

  /** The rail's FAB: 56dp, 16dp corners, primaryContainer with the compose icon in onPrimaryContainer. */
  private final class FabView extends StateView {
    private final Drawable icon;
    private final RectF rect = new RectF();

    FabView (Context context) {
      super(context);
      icon = restIcon(context.getResources(), R.drawable.baseline_create_24, R.drawable.xgl_edit);
    }

    @Override
    protected int stateLayerColor () {
      return color(XgramExpressive.ON_PRIMARY_CONTAINER);
    }

    @Override
    protected void onDraw (Canvas c) {
      float radius = Screen.dpf(FAB_CORNER);
      rect.set(0, 0, getWidth(), getHeight());
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color(XgramExpressive.PRIMARY_CONTAINER)));
      setStateShape(0, 0, getWidth(), getHeight(), radius);
      drawState(c);
      drawIcon(c, icon, getWidth() / 2f, getHeight() / 2f, Screen.dpf(ICON), color(XgramExpressive.ON_PRIMARY_CONTAINER), 1f);
    }
  }

  /**
   * A destination of the collapsed rail: the 24dp icon in the 56 x 32dp indicator area, the labelMedium label 4dp
   * below (secondary when active, onSurfaceVariant otherwise). The rail draws the indicator itself, so it can spring.
   */
  private final class ItemView extends StateView {
    final int dest;
    private final Drawable icon, activeIcon;
    private String label = "", drawnLabel = "";
    private float drawnLabelWidth;
    private boolean active;

    ItemView (Context context, int dest) {
      super(context);
      this.dest = dest;
      Resources res = context.getResources();
      this.icon = restIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]);
      this.activeIcon = activeIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]);
      setOnClickListener(v -> onDestinationClick(this.dest));
      updateLabel();
    }

    void updateLabel () {
      String label = Lang.getString(DEST_SHORT[dest]);
      if (!label.equals(this.label)) {
        this.label = label;
        setContentDescription(Lang.getString(DEST_LONG[dest]));
        layoutLabel(getWidth());
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

    private void layoutLabel (int width) {
      float available = (width > 0 ? width : Screen.dp(WIDTH)) - Screen.dpf(8f);
      drawnLabel = TextUtils.ellipsize(label, labelMedium, available, TextUtils.TruncateAt.END).toString();
      drawnLabelWidth = labelMedium.measureText(drawnLabel);
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      layoutLabel(w);
    }

    @Override
    protected int stateLayerColor () {
      return color(XgramExpressive.ON_SECONDARY_CONTAINER); // NavigationRailColorTokens pressed / hovered state layer
    }

    @Override
    protected void onDraw (Canvas c) {
      float cx = getWidth() / 2f;
      float top = Screen.dpf((ITEM_HEIGHT - ITEM_CONTENT) / 2f);
      float halfWidth = Screen.dpf(INDICATOR_WIDTH) / 2f, height = Screen.dpf(INDICATOR_HEIGHT);
      setStateShape(cx - halfWidth, top, cx + halfWidth, top + height, height / 2f);
      drawState(c);
      drawIcon(c, active ? activeIcon : icon, cx, top + height / 2f, Screen.dpf(ICON), color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT), 1f);
      labelMedium.setColor(color(active ? XgramExpressive.SECONDARY : XgramExpressive.ON_SURFACE_VARIANT));
      float lineTop = top + height + Screen.dpf(ICON_LABEL_SPACE);
      c.drawText(drawnLabel, cx - drawnLabelWidth / 2f, lineTop + Screen.dpf(LABEL_LINE) / 2f + labelMediumCenter, labelMedium);
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

  private final class ExpandedLayout extends FrameLayout implements PopupLayout.AnimatedPopupProvider, RootFrameLayout.MarginModifier {
    private final PanelView panel;
    private final LinearLayout list;
    private final XgramExpressive.Spring spring;
    @Nullable private PopupLayout popup;
    /** Above the menu button: the rail's own top inset, as seen from this window. */
    @Nullable private View topSpacer;
    private float factor;
    private int panelWidth, railLeft;
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

    private LinearLayout.LayoutParams params (int width, int height, int topMargin, boolean startAligned, int startMargin) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
      params.topMargin = topMargin;
      if (startAligned) {
        params.gravity = Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT;
      }
      if (Lang.rtl()) {
        params.rightMargin = startMargin;
      } else {
        params.leftMargin = startMargin;
      }
      return params;
    }

    private void buildRows (Context context) {
      final boolean rtl = Lang.rtl();
      final int inset = rail.startInset;
      final Resources res = context.getResources();
      final int match = ViewGroup.LayoutParams.MATCH_PARENT;
      int bottom = 0;
      androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(rail);
      if (insets != null) {
        bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
      }
      list.setPadding(rtl ? 0 : inset, 0, rtl ? inset : 0, bottom + Screen.dp(16f));

      // Status bar, then the header: the menu button (closes) and the extended FAB, where the rail has them
      topSpacer = new View(context);
      list.addView(topSpacer, params(match, HeaderView.getTopOffset(), 0, false, 0));
      MenuView menu = new MenuView(context, true);
      menu.setContentDescription(Lang.getString(R.string.XgramNavRailCloseMenu));
      menu.setOnClickListener(v -> close());
      list.addView(menu, params(Screen.dp(WIDTH), Screen.dp(BAND), 0, true, 0));
      ExtendedFabView fab = new ExtendedFabView(context);
      fab.setOnClickListener(v -> {
        close();
        openFabMenu();
      });
      list.addView(fab, params(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(FAB_SIZE), Screen.dp(FAB_GAP), true, Screen.dp(ITEM_PADDING)));

      // Destinations
      for (int i = 0; i < destinations.length; i++) {
        final int dest = destinations[i];
        RowView row = new RowView(context, dest, true, restIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]), activeIcon(res, DEST_FILLED[dest], DEST_OUTLINED[dest]), Lang.getString(DEST_LONG[dest]), null);
        row.setActive(dest == selected);
        row.setOnClickListener(v -> {
          markShown();
          onDestinationClick(dest);
          close();
        });
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), Screen.dp(i == 0 ? HEADER_PADDING : ITEM_SPACE), false, 0));
      }

      // Accounts
      list.addView(new SectionView(context, Lang.getString(R.string.XgramNavRailAccounts)), params(match, Screen.dp(SECTION_HEIGHT), Screen.dp(SECTION_GAP), false, 0));
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
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), 0, false, 0));
      }
      list.addView(actionRow(context, new Entry(null, R.id.btn_addAccount, R.drawable.baseline_add_24, R.drawable.xgl_add, R.string.XgramNavRailAddAccount), true), params(match, Screen.dp(ROW_HEIGHT), 0, false, 0));

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
      if (!xgram.drawerItemHidden("ghost")) {
        toggles.add(new Entry("ghost", R.id.btn_xgramGhostMode, R.drawable.baseline_ghost_24, R.drawable.xgl_ghost, R.string.XgramGhostMode));
      }
      XgramReorder.sort(XgramReorder.DRAWER, toggles, entry -> entry.key);
      if (!entries.isEmpty() || !toggles.isEmpty()) {
        list.addView(new DividerView(context), params(match, Screen.dp(DIVIDER_HEIGHT), Screen.dp(SECTION_GAP), false, 0));
      }
      for (Entry entry : entries) {
        View row;
        if (entry.id == R.id.btn_proxy) {
          row = switchRow(context, entry, Settings.instance().getEffectiveProxyId() != Settings.PROXY_ID_NONE);
        } else {
          row = actionRow(context, entry, true);
        }
        list.addView(row, params(match, Screen.dp(ROW_HEIGHT), 0, false, 0));
      }
      for (Entry entry : toggles) {
        boolean checked = entry.id == R.id.btn_night ? Theme.isDark() : xgram.isAnyGhostEnabled();
        list.addView(switchRow(context, entry, checked), params(match, Screen.dp(ROW_HEIGHT), 0, false, 0));
      }
    }

    /** A side menu entry that opens something (and closes the rail). */
    private RowView actionRow (Context context, Entry entry, boolean closes) {
      Resources res = context.getResources();
      RowView row = new RowView(context, entry.id, false, restIcon(res, entry.filled, entry.outlined), null, Lang.getString(entry.label), null);
      final int id = entry.id;
      row.setOnClickListener(v -> {
        markShown();
        onMenuAction(id);
        if (closes) {
          close();
        }
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
      RowView row = new RowView(context, id, false, restIcon(res, entry.filled, entry.outlined), null, Lang.getString(entry.label), null);
      row.setTrailingReserve(Screen.dp(SWITCH_RESERVE));
      wrap.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      final TogglerView toggler = new TogglerView(context);
      toggler.init(checked);
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Screen.dp(74f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT));
      if (rtl) {
        params.leftMargin = Screen.dp(ITEM_PADDING);
      } else {
        params.rightMargin = Screen.dp(ITEM_PADDING);
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
      // Where the rail is, seen from this window (edge to edge or not): the panel lies exactly over it
      int[] own = Views.getLocationOnScreen(this);
      int[] railLocation = Views.getLocationOnScreen(rail);
      railLeft = railLocation[0] - own[0];
      int spacer = Math.max(0, railLocation[1] - own[1] + HeaderView.getTopOffset());
      if (topSpacer != null && topSpacer.getLayoutParams() != null && topSpacer.getLayoutParams().height != spacer) {
        topSpacer.getLayoutParams().height = spacer; // before the measure below, so no new layout
      }
      int inset = rail.startInset;
      int content = Math.max(Screen.dp(EXPANDED_MIN), Math.min(Screen.dp(EXPANDED_MAX), width - inset - Screen.dp(EXPANDED_SCRIM_MIN)));
      panelWidth = Math.min(width, content + inset);
      measureExactly(panel, panelWidth, height);
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int left = Lang.rtl() ? railLeft + rail.getWidth() - panelWidth : railLeft;
      panel.layout(left, 0, left + panelWidth, b - t);
      applyFactor();
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
   * The active one (the destination on screen, the current account) is secondaryContainer with onSecondaryContainer.
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

    RowView (Context context, int id, boolean destination, @Nullable Drawable icon, @Nullable Drawable activeIcon, String label, @Nullable String badge) {
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
      float available = width - textStart() - Screen.dpf(ITEM_PADDING + ROW_TRAILING) - trailingReserve - (badge != null ? badgeWidth + Screen.dpf(ROW_ICON_LABEL) : 0f);
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
      return color(active ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      final int width = getWidth(), height = getHeight();
      final float padding = Screen.dpf(ITEM_PADDING);
      float end = destination ? padding + Screen.dpf(ROW_LEADING + ICON + ROW_ICON_LABEL) + drawnLabelWidth + Screen.dpf(ROW_TRAILING) : width - padding;
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
        labelLarge.setColor(color(XgramExpressive.ON_SURFACE_VARIANT));
        float trailing = padding + Screen.dpf(ROW_TRAILING);
        c.drawText(badge, rtl ? trailing : width - trailing - badgeWidth, cy + labelLargeCenter, labelLarge);
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
      float start = Screen.dpf(ITEM_PADDING + ROW_LEADING);
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
      float inset = Screen.dpf(ITEM_PADDING + ROW_LEADING);
      float thickness = Math.max(1f, Screen.dpf(1f));
      float top = (getHeight() - thickness) / 2f;
      c.drawRect(inset, top, getWidth() - inset, top + thickness, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT)));
    }
  }

  /** The expanded rail's extended FAB: 56dp, 16dp corners, primaryContainer, the icon where the rail's FAB has it. */
  private final class ExtendedFabView extends StateView {
    private final Drawable icon;
    private final String label;
    private final float labelWidth;
    private final RectF rect = new RectF();

    ExtendedFabView (Context context) {
      super(context);
      icon = restIcon(context.getResources(), R.drawable.baseline_create_24, R.drawable.xgl_edit);
      label = Lang.getString(R.string.XgramFabNewChat);
      labelWidth = labelLarge.measureText(label);
      setContentDescription(label);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = (int) Math.ceil(Screen.dpf(EXTENDED_FAB_LEADING + ICON + EXTENDED_FAB_ICON_LABEL + EXTENDED_FAB_TRAILING) + labelWidth);
      setMeasuredDimension(Math.max(width, Screen.dp(FAB_SIZE)), Screen.dp(FAB_SIZE));
    }

    @Override
    protected int stateLayerColor () {
      return color(XgramExpressive.ON_PRIMARY_CONTAINER);
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean rtl = Lang.rtl();
      final int width = getWidth(), height = getHeight();
      float radius = Screen.dpf(FAB_CORNER);
      rect.set(0, 0, width, height);
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color(XgramExpressive.PRIMARY_CONTAINER)));
      setStateShape(0, 0, width, height, radius);
      drawState(c);
      int content = color(XgramExpressive.ON_PRIMARY_CONTAINER);
      float iconCenter = Screen.dpf(EXTENDED_FAB_LEADING + ICON / 2f);
      drawIcon(c, icon, rtl ? width - iconCenter : iconCenter, height / 2f, Screen.dpf(ICON), content, 1f);
      labelLarge.setColor(content);
      float textStart = Screen.dpf(EXTENDED_FAB_LEADING + ICON + EXTENDED_FAB_ICON_LABEL);
      c.drawText(label, rtl ? width - textStart - labelWidth : textStart, height / 2f + labelLargeCenter, labelLarge);
    }
  }

  // FAB menu

  private final class FabMenuLayout extends FrameLayout implements PopupLayout.AnimatedPopupProvider, RootFrameLayout.MarginModifier {
    private final CloseView close;
    private final FabMenuItemView[] items;
    private final XgramExpressive.Spring spatial, effects;
    @Nullable private PopupLayout popup;
    private boolean closing, finished, shown;

    FabMenuLayout (Context context) {
      super(context);
      setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      Resources res = context.getResources();
      items = new FabMenuItemView[FAB_MENU_IDS.length];
      for (int i = 0; i < items.length; i++) {
        final int id = FAB_MENU_IDS[i];
        FabMenuItemView item = new FabMenuItemView(context, restIcon(res, FAB_MENU_FILLED[i], FAB_MENU_OUTLINED[i]), Lang.getString(FAB_MENU_LABELS[i]));
        item.setOnClickListener(v -> {
          if (!closing) {
            markShown();
            onFabMenuAction(id);
            dismiss();
          }
        });
        items[i] = item;
        addView(item);
      }
      close = new CloseView(context);
      close.setContentDescription(Lang.getString(R.string.XgramNavRailCloseFabMenu));
      close.setOnClickListener(v -> dismiss());
      addView(close);
      // FloatingActionButtonMenu: the items unfold with the fast spatial spring and fade with the fast effects one
      spatial = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> apply()).setThreshold(.002f);
      effects = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> apply()).setThreshold(.004f);
    }

    @Override
    public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
      Views.setMargins(params, 0, 0, 0, 0);
    }

    void dismiss () {
      if (popup != null) {
        popup.hideWindow(true);
      }
    }

    private void markShown () {
      if (!shown && popup != null) {
        shown = true;
        popup.onCustomShowComplete();
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      measureExactly(close, Screen.dp(FAB_SIZE), Screen.dp(FAB_SIZE));
      for (FabMenuItemView item : items) {
        measureExactly(item, item.desiredWidth(), Screen.dp(MENU_ITEM_HEIGHT));
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int[] own = Views.getLocationOnScreen(this);
      int[] fab = Views.getLocationOnScreen(rail.fab);
      int size = Screen.dp(FAB_SIZE);
      int fabLeft = fab[0] - own[0], fabTop = fab[1] - own[1];
      close.layout(fabLeft, fabTop, fabLeft + size, fabTop + size);
      boolean rtl = Lang.rtl();
      int y = fabTop + size + Screen.dp(MENU_CLOSE_GAP);
      int itemHeight = Screen.dp(MENU_ITEM_HEIGHT), space = Screen.dp(MENU_ITEM_SPACE);
      for (FabMenuItemView item : items) {
        int width = item.getMeasuredWidth();
        int left = rtl ? fabLeft + size - width : fabLeft;
        item.layout(left, y, left + width, y + itemHeight);
        y += itemHeight + space;
      }
    }

    private void apply () {
      if (finished) {
        return;
      }
      float s = spatial.getValue(), e = MathUtils.clamp(effects.getValue());
      close.setMorph(MathUtils.clamp(s));
      int count = items.length;
      float span = 1f - MENU_STAGGER * (count - 1);
      for (int i = 0; i < count; i++) {
        float reveal = MathUtils.clamp((s - MENU_STAGGER * i) / span);
        items[i].setReveal(reveal, Math.min(e, reveal > 0f ? 1f : 0f));
      }
      if (popup != null) {
        popup.setRevealFactor(MathUtils.clamp(s));
      }
      if (!closing && s >= .9f) {
        markShown();
      }
      if (closing && s <= 0f && e <= 0f) {
        finishHide();
      }
    }

    private void onSpringEnd () {
      if (closing) {
        if (!spatial.isRunning() && !effects.isRunning()) {
          finishHide();
        }
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
      apply();
    }

    @Override
    public void launchShowAnimation (PopupLayout popup) {
      this.popup = popup;
      rail.fab.setVisibility(View.INVISIBLE); // the close button takes its place, looking the same at the start
      spatial.setEndListener(this::onSpringEnd);
      effects.setEndListener(null);
      spatial.animateTo(1f);
      effects.animateTo(1f);
    }

    @Override
    public boolean launchHideAnimation (PopupLayout popup, me.vkryl.android.animator.FactorAnimator originalAnimator) {
      this.popup = popup;
      closing = true;
      spatial.setEndListener(this::onSpringEnd);
      effects.setEndListener(this::onSpringEnd);
      if (!spatial.isRunning() && !effects.isRunning() && spatial.getValue() <= 0f && effects.getValue() <= 0f) {
        finishHide(); // never opened: nothing would move, and the window must still go
      } else {
        spatial.animateTo(0f);
        effects.animateTo(0f);
      }
      return true;
    }
  }

  /** The FAB turning into the menu's close button: 16dp corners to a circle, primaryContainer to primary, pen to 20dp X. */
  private final class CloseView extends StateView {
    private final Drawable fabIcon, closeIcon;
    private final RectF rect = new RectF();
    private float morph;

    CloseView (Context context) {
      super(context);
      Resources res = context.getResources();
      fabIcon = restIcon(res, R.drawable.baseline_create_24, R.drawable.xgl_edit);
      closeIcon = restIcon(res, R.drawable.baseline_close_24, R.drawable.xgl_close);
    }

    void setMorph (float morph) {
      if (this.morph != morph) {
        this.morph = morph;
        invalidate();
      }
    }

    @Override
    protected int stateLayerColor () {
      return ColorUtils.fromToArgb(color(XgramExpressive.ON_PRIMARY_CONTAINER), color(XgramExpressive.ON_PRIMARY), morph);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      float radius = Screen.dpf(FAB_CORNER) + (Math.min(width, height) / 2f - Screen.dpf(FAB_CORNER)) * morph;
      rect.set(0, 0, width, height);
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(ColorUtils.fromToArgb(color(XgramExpressive.PRIMARY_CONTAINER), color(XgramExpressive.PRIMARY), morph)));
      setStateShape(0, 0, width, height, radius);
      drawState(c);
      float cx = width / 2f, cy = height / 2f;
      drawIcon(c, fabIcon, cx, cy, Screen.dpf(ICON), color(XgramExpressive.ON_PRIMARY_CONTAINER), 1f - morph);
      int saveCount = c.save();
      c.rotate(-90f * (1f - morph), cx, cy);
      drawIcon(c, closeIcon, cx, cy, Screen.dpf(MENU_CLOSE_ICON), color(XgramExpressive.ON_PRIMARY), morph);
      c.restoreToCount(saveCount);
    }
  }

  /**
   * An item of the FAB menu: a 56dp full-corner pill in primaryContainer, 24dp padding, the 24dp icon 8dp before a
   * titleMedium label, level 3 shadow. It unfolds from the start edge, under the FAB.
   */
  private final class FabMenuItemView extends StateView {
    private final Drawable icon;
    private final String label;
    private final float labelWidth;
    private final RectF rect = new RectF();
    private float reveal;

    FabMenuItemView (Context context, Drawable icon, String label) {
      super(context);
      this.icon = icon;
      this.label = label;
      this.labelWidth = titleMedium.measureText(label);
      setContentDescription(label);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
      setAlpha(0f);
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          updateRect();
          if (rect.width() <= 0f || rect.height() <= 0f) {
            outline.setEmpty();
          } else {
            outline.setRoundRect(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom), rect.height() / 2f);
          }
        }
      });
    }

    int desiredWidth () {
      return (int) Math.ceil(Screen.dpf(MENU_ITEM_PADDING + ICON + MENU_ICON_LABEL + MENU_ITEM_PADDING) + labelWidth);
    }

    void setReveal (float reveal, float alpha) {
      setAlpha(alpha);
      setEnabled(reveal >= 1f);
      if (this.reveal != reveal) {
        this.reveal = reveal;
        invalidate();
        invalidateOutline();
      }
    }

    private void updateRect () {
      float width = getWidth() * reveal;
      if (Lang.rtl()) {
        rect.set(getWidth() - width, 0, getWidth(), getHeight());
      } else {
        rect.set(0, 0, width, getHeight());
      }
    }

    @Override
    protected int stateLayerColor () {
      return color(XgramExpressive.ON_PRIMARY_CONTAINER);
    }

    @Override
    protected void onDraw (Canvas c) {
      updateRect();
      if (rect.width() <= 0f) {
        return;
      }
      final boolean rtl = Lang.rtl();
      final int width = getWidth(), height = getHeight();
      float radius = height / 2f;
      int saveCount = c.save();
      c.clipRect(rect);
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color(XgramExpressive.PRIMARY_CONTAINER)));
      setStateShape(rect.left, rect.top, rect.right, rect.bottom, radius);
      drawState(c);
      int content = color(XgramExpressive.ON_PRIMARY_CONTAINER);
      float iconCenter = Screen.dpf(MENU_ITEM_PADDING + ICON / 2f);
      drawIcon(c, icon, rtl ? width - iconCenter : iconCenter, height / 2f, Screen.dpf(ICON), content, 1f);
      titleMedium.setColor(content);
      float textStart = Screen.dpf(MENU_ITEM_PADDING + ICON + MENU_ICON_LABEL);
      c.drawText(label, rtl ? width - textStart - labelWidth : textStart, height / 2f + titleMediumCenter, titleMedium);
      c.restoreToCount(saveCount);
    }
  }
}
