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
package com.fahadali.xgram;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fahadali.xgram.ghost.XgramGhost;
import com.fahadali.xgram.update.XgramUpdateScreen;
import com.fahadali.xgram.update.XgramUpdates;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * XGram's own settings: a short list of categories, each opening its own page (the same controller with a section).
 * Every setting has one home page: Settings › XGram › category › page at most. A row that also fits elsewhere (Telegram
 * X's pages, another category) is a link there that opens its home and points at it. The Compose pages
 * (com.fahadali.xgram.compose.pages) show the same rows with the same words.
 */
public final class XgramSettingsController extends RecyclerViewController<Integer> implements View.OnClickListener, View.OnLongClickListener, AppUpdater.Listener {
  // Never renumber: new sections get the next number (search recents and deep links keep them).
  public static final int SECTION_MAIN = 0, SECTION_APPEARANCE = 1, SECTION_GHOST = 2, SECTION_HISTORY = 3,
    SECTION_CHATS = 4, SECTION_PRIVACY = 5, SECTION_NETWORK = 6, SECTION_BACKUP = 7, SECTION_UPDATES = 8,
    SECTION_CHAT_LIST = 9, SECTION_TRANSLATION = 10, SECTION_FILTERS = 11, SECTION_ACTIVITY = 12, SECTION_LARGE_SCREENS = 13;
  /** Chats and messages: the page that was Chats, then Messages (same id). */
  public static final int SECTION_MESSAGES = SECTION_CHATS;
  // SECTION_GHOST: Ghost mode is the top of Privacy now. Opening it opens Privacy (see section() and ghost()).

  /** Pages the settings search reads, in the main page's order. Add a new section here. */
  static final int[] SEARCH_SECTIONS = {
    SECTION_MAIN, SECTION_APPEARANCE, SECTION_LARGE_SCREENS, SECTION_CHAT_LIST, SECTION_MESSAGES, SECTION_PRIVACY,
    SECTION_ACTIVITY, SECTION_TRANSLATION, SECTION_FILTERS, SECTION_HISTORY, SECTION_NETWORK, SECTION_BACKUP,
    SECTION_UPDATES
  };

  /**
   * The section whose page opens [section] (Large screens and foldables is a page of Appearance), or SECTION_MAIN for the
   * categories (App updates too: About › Version and Telegram X's App updates open it).
   */
  static int sectionParent (int section) {
    return section == SECTION_LARGE_SCREENS ? SECTION_APPEARANCE : SECTION_MAIN;
  }

  /** Pages further in (their own controllers) that the settings search reads too. */
  static final int SUB_SIDE_MENU = 1, SUB_CHAT_ACTIONS = 2, SUB_MESSAGE_MENU = 3, SUB_INPUT_BUTTONS = 4,
    SUB_WORD_FILTERS = 5, SUB_HIDDEN_PEOPLE = 6, SUB_HEADER_BUTTONS = 7;
  static final int[] SEARCH_SUB_PAGES = {
    SUB_SIDE_MENU, SUB_CHAT_ACTIONS, SUB_MESSAGE_MENU, SUB_INPUT_BUTTONS, SUB_WORD_FILTERS, SUB_HIDDEN_PEOPLE, SUB_HEADER_BUTTONS
  };

  private SettingsAdapter adapter;

  // Settings search (the universal one: XGram's rows first, then Telegram X's)
  private @IdRes int highlightId; // row to scroll to and flash once shown
  private com.fahadali.xgram.search.SearchIndex searchIndex;
  private boolean showingResults;
  private View lastTouchView; // where a result row was last touched (switch vs. row)
  private float lastTouchX;

  private boolean manualUpdateCheck; // Updates: show the result of "Check for updates"

  public XgramSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static XgramSettingsController section (Context context, Tdlib tdlib, int section) {
    XgramSettingsController c = new XgramSettingsController(context, tdlib);
    c.setArguments(section == SECTION_GHOST ? SECTION_PRIVACY : section);
    return c;
  }

  /**
   * A section page that scrolls to one row and flashes it once shown: settings search, and shortcuts from Telegram X's
   * pages (for example Network at R.id.btn_xgramProxyOffVpn, the first Turn proxy off row).
   */
  public static XgramSettingsController section (Context context, Tdlib tdlib, int section, @IdRes int highlightId) {
    XgramSettingsController c = section(context, tdlib, section);
    c.highlightId = highlightId;
    return c;
  }

  /**
   * Ghost mode settings: Privacy, whose first part is Ghost mode. Opened by the main screen's ghost button, a long
   * press on the side menu's Ghost mode switch, a long press on the Quick Settings tile and What's new.
   */
  public static XgramSettingsController ghost (Context context, Tdlib tdlib) {
    return section(context, tdlib, SECTION_PRIVACY);
  }

  private int section () {
    Integer section = getArguments();
    return section != null ? section : SECTION_MAIN;
  }

  @Override
  public int getId () {
    return section() == SECTION_MAIN ? R.id.controller_xgramSettings : R.id.controller_xgramSettingsSection;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(sectionTitleRes(section()));
  }

  static @StringRes int sectionTitleRes (int section) {
    switch (section) {
      case SECTION_APPEARANCE: return R.string.XgramAppearance;
      case SECTION_CHAT_LIST: return R.string.XgramChatList;
      case SECTION_MESSAGES: return R.string.XgramMessages;
      case SECTION_TRANSLATION: return R.string.XgramTranslation;
      case SECTION_GHOST:
      case SECTION_PRIVACY: return R.string.XgramPrivacy;
      case SECTION_FILTERS: return R.string.XgramFilters;
      case SECTION_HISTORY: return R.string.XgramHistory;
      case SECTION_NETWORK: return R.string.XgramNetwork;
      case SECTION_BACKUP: return R.string.XgramBackup;
      case SECTION_UPDATES: return R.string.XgramUpdates;
      case SECTION_ACTIVITY: return R.string.XgramActivitySection;
      case SECTION_LARGE_SCREENS: return R.string.XgramLargeScreens;
      default: return R.string.XgramSettings;
    }
  }

  /** Same icons as the main page's category rows; search results without their own icon use them. */
  static @DrawableRes int sectionIcon (int section) {
    switch (section) {
      case SECTION_APPEARANCE: return R.drawable.baseline_palette_24;
      case SECTION_CHAT_LIST: return R.drawable.baseline_forum_24;
      case SECTION_MESSAGES: return R.drawable.baseline_chat_bubble_24;
      case SECTION_TRANSLATION: return R.drawable.baseline_translate_24;
      case SECTION_GHOST:
      case SECTION_PRIVACY: return R.drawable.baseline_lock_24;
      case SECTION_FILTERS: return R.drawable.baseline_filter_variant_remove_24;
      case SECTION_HISTORY: return R.drawable.baseline_history_24;
      case SECTION_NETWORK: return R.drawable.baseline_public_24;
      case SECTION_BACKUP: return R.drawable.baseline_import_export_24;
      case SECTION_UPDATES: return R.drawable.baseline_system_update_24;
      case SECTION_ACTIVITY: return R.drawable.baseline_notifications_active_24;
      case SECTION_LARGE_SCREENS: return R.drawable.baseline_devices_other_24;
      default: return R.drawable.baseline_settings_24;
    }
  }

  // Pages further in, for the settings search

  /** The section whose page opens it. */
  static int subPageParent (int subPage) {
    switch (subPage) {
      case SUB_SIDE_MENU:
      case SUB_CHAT_ACTIONS:
      case SUB_HEADER_BUTTONS: return SECTION_CHAT_LIST;
      case SUB_MESSAGE_MENU:
      case SUB_INPUT_BUTTONS: return SECTION_MESSAGES;
      case SUB_WORD_FILTERS:
      case SUB_HIDDEN_PEOPLE: return SECTION_FILTERS;
      default: return SECTION_MAIN;
    }
  }

  static @StringRes int subPageTitleRes (int subPage) {
    switch (subPage) {
      case SUB_CHAT_ACTIONS: return R.string.XgramChatActionsOrder;
      case SUB_HEADER_BUTTONS: return R.string.XgramHeaderButtons;
      case SUB_MESSAGE_MENU: return R.string.XgramMessageMenu;
      case SUB_INPUT_BUTTONS: return R.string.XgramInputButtons;
      case SUB_WORD_FILTERS: return R.string.XgramWordFilters;
      case SUB_HIDDEN_PEOPLE: return R.string.XgramShadowBanned;
      case SUB_SIDE_MENU:
      default: return R.string.XgramSideMenu;
    }
  }

  static @DrawableRes int subPageIcon (int subPage) {
    return sectionIcon(subPageParent(subPage));
  }

  /** Rows of a page further in, read from that page's own list. */
  List<ListItem> buildSubPageItems (int subPage) {
    switch (subPage) {
      case SUB_CHAT_ACTIONS: return com.fahadali.xgram.chats.XgramChatActionsController.buildItems();
      case SUB_HEADER_BUTTONS: return com.fahadali.xgram.chats.XgramHeaderButtonsController.buildItems();
      case SUB_MESSAGE_MENU: return com.fahadali.xgram.chats.XgramMessageMenuController.buildItems();
      case SUB_INPUT_BUTTONS: return com.fahadali.xgram.chats.XgramInputButtonsController.buildItems();
      case SUB_WORD_FILTERS: {
        List<ListItem> items = com.fahadali.xgram.chats.XgramFiltersController.buildItems(false);
        List<ListItem> rows = new ArrayList<>(items.size());
        for (ListItem item : items) {
          if (item.getId() != R.id.btn_xgramFilter) { // the words themselves are the user's, not settings
            rows.add(item);
          }
        }
        return rows;
      }
      case SUB_HIDDEN_PEOPLE: return com.fahadali.xgram.chats.XgramFiltersController.buildItems(true);
      case SUB_SIDE_MENU: return XgramDrawerItemsController.buildItems();
      default: return new ArrayList<>();
    }
  }

  private RecyclerViewController<?> openSubPage (int subPage) {
    return subPage(context, tdlib, subPage);
  }

  /** A page further in (SUB_*), as its row opens it; the settings search opens it the same way. */
  public static RecyclerViewController<?> subPage (Context context, Tdlib tdlib, int subPage) {
    switch (subPage) {
      case SUB_CHAT_ACTIONS: return new com.fahadali.xgram.chats.XgramChatActionsController(context, tdlib);
      case SUB_HEADER_BUTTONS: return new com.fahadali.xgram.chats.XgramHeaderButtonsController(context, tdlib);
      case SUB_MESSAGE_MENU: return new com.fahadali.xgram.chats.XgramMessageMenuController(context, tdlib);
      case SUB_INPUT_BUTTONS: return new com.fahadali.xgram.chats.XgramInputButtonsController(context, tdlib);
      case SUB_WORD_FILTERS: return com.fahadali.xgram.chats.XgramFiltersController.words(context, tdlib);
      case SUB_HIDDEN_PEOPLE: return com.fahadali.xgram.chats.XgramFiltersController.people(context, tdlib);
      case SUB_SIDE_MENU:
      default: return new XgramDrawerItemsController(context, tdlib);
    }
  }

  /** The section a category row of the main page opens; SECTION_MAIN for its other rows. */
  static int sectionOfMainRow (@IdRes int id) {
    if (id == R.id.btn_xgramSectionAppearance) {
      return SECTION_APPEARANCE;
    } else if (id == R.id.btn_xgramSectionChatList) {
      return SECTION_CHAT_LIST;
    } else if (id == R.id.btn_xgramSectionChats) {
      return SECTION_MESSAGES;
    } else if (id == R.id.btn_xgramSectionTranslation) {
      return SECTION_TRANSLATION;
    } else if (id == R.id.btn_xgramSectionActivity) {
      return SECTION_ACTIVITY;
    } else if (id == R.id.btn_xgramSectionPrivacy) {
      return SECTION_PRIVACY;
    } else if (id == R.id.btn_xgramSectionFilters) {
      return SECTION_FILTERS;
    } else if (id == R.id.btn_xgramSectionHistory) {
      return SECTION_HISTORY;
    } else if (id == R.id.btn_xgramSectionNetwork) {
      return SECTION_NETWORK;
    } else if (id == R.id.btn_xgramSectionUpdates) {
      return SECTION_UPDATES;
    } else if (id == R.id.btn_xgramSectionBackup) {
      return SECTION_BACKUP;
    }
    return SECTION_MAIN;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        XgramSettings settings = XgramSettings.instance();
        @IdRes int id = item.getId();
        // A fixed line under the title (toggle(), link() with a subtitle); values below replace it.
        CharSequence subtitle = item.getCharSequenceValue();
        if (subtitle != null) {
          view.setData(subtitle);
        }
        if (id == R.id.btn_xgramSectionAppearance) {
          view.setData(R.string.XgramAppearanceSummary);
        } else if (id == R.id.btn_xgramSectionChatList) {
          view.setData(R.string.XgramChatListSummary);
        } else if (id == R.id.btn_xgramSectionChats) {
          view.setData(R.string.XgramMessagesSummary);
        } else if (id == R.id.btn_xgramSectionTranslation) {
          view.setData(R.string.XgramTranslationSummary);
        } else if (id == R.id.btn_xgramSectionActivity) {
          view.setData(R.string.XgramActivitySectionSummary);
        } else if (id == R.id.btn_xgramSideMenu) {
          int hidden = XgramDrawerItemsController.hiddenCount();
          view.setData(hidden > 0 ? Lang.getString(R.string.XgramHiddenCount, hidden) : Lang.getString(R.string.XgramSideMenuSummary));
        } else if (id == R.id.btn_xgramSectionPrivacy) {
          view.setData(settings.isAnyGhostEnabled() ? R.string.XgramPrivacySummaryGhostOn : R.string.XgramPrivacySummary);
        } else if (id == R.id.btn_xgramSectionFilters) {
          view.setData(R.string.XgramFiltersSummary);
        } else if (id == R.id.btn_xgramSectionHistory) {
          view.setData(R.string.XgramHistorySummary);
        } else if (id == R.id.btn_xgramSectionNetwork) {
          view.setData(R.string.XgramNetworkSummary);
        } else if (id == R.id.btn_xgramSectionBackup) {
          view.setData(R.string.XgramBackupSummary);
        } else if (id == R.id.btn_xgramSectionUpdates) {
          view.setData(R.string.XgramUpdatesSummary);
        } else if (id == R.id.btn_xgramUpdateChannel) {
          view.setData(XgramUpdates.channelName(XgramUpdates.channel()));
        } else if (id == R.id.btn_xgramUpdateMode) {
          view.setData(XgramUpdates.modeName(Settings.instance().getAutoUpdateMode()));
          view.setEnabled(XgramUpdates.channel() != XgramUpdates.CHANNEL_OFF);
        } else if (id == R.id.btn_xgramUpdateInstalled) {
          // The build's own kind, from its post; the channel picked above is a separate setting.
          int kind = XgramUpdates.installedKind();
          view.setData(kind == -1 ? BuildConfig.VERSION_NAME : Lang.getString(R.string.XgramUpdateInstalledValue, BuildConfig.VERSION_NAME, Lang.getString(kind == 1 ? R.string.XgramUpdateKindStable : R.string.XgramUpdateKindCi)));
        } else if (id == R.id.btn_xgramUpdateCheck) {
          if (!(item.getData() instanceof XgramSettingsSearch.Entry)) { // a search result keeps its title
            bindUpdateCheck(view, isUpdate);
          }
        } else if (id == R.id.btn_xgramThemes) {
          // Expressive design draws with the Material You colors whatever the theme: locked, the reason instead.
          boolean locked = XgramSettingDeps.themesLocked();
          view.setData(locked ? Lang.getString(R.string.XgramThemesExpressive) : currentThemeName());
          view.setEnabled(!locked);
        } else if (id == R.id.btn_xgramMaterialYou) {
          // Shown on and locked with Expressive design; the saved choice is kept for when it is off.
          boolean locked = XgramSettingDeps.themesLocked();
          view.getToggler().setRadioEnabled(locked || com.fahadali.xgram.appearance.XgramMaterialYou.isEnabled(), isUpdate);
          if (locked) {
            view.setData(R.string.XgramMaterialYouExpressive);
          }
          view.setEnabled(!locked);
        } else if (id == R.id.btn_xgramAutoNightMode) {
          view.setData(XgramSettingDeps.autoNightModeName());
        } else if (id == R.id.btn_xgramPureBlack) {
          view.getToggler().setRadioEnabled(com.fahadali.xgram.appearance.XgramAmoled.isSaved(), isUpdate);
          view.setEnabled(com.fahadali.xgram.appearance.XgramAmoled.isAvailable());
        } else if (id == R.id.btn_xgramExpressive) {
          view.getToggler().setRadioEnabled(com.fahadali.xgram.appearance.XgramExpressive.isEnabled(), isUpdate);
        } else if (id == R.id.btn_xgramComposeTgx) {
          view.getToggler().setRadioEnabled(settings.composeTgxPages(), isUpdate);
        } else if (id == R.id.btn_xgramColorSource) {
          view.setData(com.fahadali.xgram.appearance.XgramColorSource.summary());
          view.setEnabled(com.fahadali.xgram.appearance.XgramColorSource.isAvailable());
        } else if (id == R.id.btn_xgramEmojiSet) {
          view.setData(emojiSetName());
        } else if (id == R.id.btn_xgramFont) {
          view.setData(com.fahadali.xgram.appearance.XgramFonts.name(settings.appFont()));
        } else if (id == R.id.btn_xgramHistoryRetention) {
          int days = settings.historyRetentionDays();
          view.setData(days == 0 ? Lang.getString(R.string.XgramRetentionForever) : Lang.getString(R.string.XgramRetentionDays, days));
        } else if (id == R.id.btn_xgramAppIcon) {
          view.setData(Lang.getString(com.fahadali.xgram.appicons.XgramAppIcons.current(context).nameRes));
        } else if (id == R.id.btn_xgramNotificationIcon) {
          view.setData(notificationIconName(settings.notificationIcon()));
        } else if (id == R.id.btn_xgramAboutVersion) {
          view.setData(aboutVersionValue(context().appUpdater()));
        } else if (id == R.id.btn_xgramLogsShare) {
          view.setData(R.string.XgramLogsShareInfo);
        } else if (id == R.id.btn_xgramAboutBasedOn) {
          view.setData(R.string.XgramAboutBasedOnInfo);
        } else if (id == R.id.btn_xgramAboutSupport) {
          view.setData(R.string.XgramAboutSupportInfo);
        } else if (id == R.id.btn_xgramAvatarCorners) {
          view.setData(avatarCornersName(settings.avatarCorners()));
        } else if (id == R.id.btn_xgramChatFabsAvatarShape) {
          view.getToggler().setRadioEnabled(settings.chatFabsAvatarShape(), isUpdate);
        } else if (id == R.id.btn_xgramChatListView) {
          view.setData(com.fahadali.xgram.chats.XgramChatListMode.name(org.thunderdog.challegram.unsorted.Settings.instance().getChatListMode()));
        } else if (id == R.id.btn_xgramChatListBigText) {
          // Only three lines have bigger text; otherwise the switch keeps the choice for when they are picked
          boolean bigText = com.fahadali.xgram.chats.XgramChatListMode.isBigTextEnabled();
          view.getToggler().setRadioEnabled(com.fahadali.xgram.chats.XgramChatListMode.isBigTextChecked(), isUpdate);
          view.setData(bigText ? R.string.XgramChatListBigTextSub : R.string.XgramChatListBigTextOff);
          view.setEnabled(bigText);
        } else if (id == R.id.btn_xgramChatLongPress) {
          view.setData(com.fahadali.xgram.chats.XgramChatLongPress.modeName(settings.chatLongPress()));
          setReason(view, XgramSettingDeps.chatLongPressReason()); // all three need Telegram X's Chat Previews
        } else if (id == R.id.btn_xgramChatActionsOrder) {
          view.setData(XgramReorder.hasOrder(XgramReorder.CHAT_ACTIONS) ? R.string.XgramOrderCustom : R.string.XgramOrderDefault);
        } else if (id == R.id.btn_xgramMessageMenu) {
          int hidden = messageMenuHiddenCount();
          view.setData(hidden > 0 ? Lang.getString(R.string.XgramHiddenCount, hidden) : Lang.getString(XgramReorder.hasOrder(XgramReorder.MESSAGE_MENU) ? R.string.XgramOrderCustom : R.string.XgramOrderDefault));
        } else if (id == R.id.btn_xgramInputButtons) {
          int hidden = com.fahadali.xgram.chats.XgramInputButtonsController.hiddenCount();
          view.setData(hidden > 0 ? Lang.getString(R.string.XgramHiddenCount, hidden) : Lang.getString(R.string.XgramAllShown));
        } else if (id == R.id.btn_xgramHeaderButtons) {
          int hidden = com.fahadali.xgram.chats.XgramHeaderButtonsController.hiddenCount();
          view.setData(hidden > 0 ? Lang.getString(R.string.XgramHiddenCount, hidden) : Lang.getString(R.string.XgramAllShown));
        } else if (id == R.id.btn_xgramFilters) {
          int count = com.fahadali.xgram.chats.XgramFilters.getFilters().size();
          view.setData(count == 0 ? Lang.getString(R.string.XgramNone) : count == 1 ? Lang.getString(R.string.XgramFilterCountOne) : Lang.getString(R.string.XgramFilterCount, count));
        } else if (id == R.id.btn_xgramHiddenPeople) {
          int count = com.fahadali.xgram.chats.XgramFilters.shadowBannedCount(tdlib);
          view.setData(count == 0 ? Lang.getString(R.string.XgramNone) : count == 1 ? Lang.getString(R.string.XgramPeopleCountOne) : Lang.getString(R.string.XgramPeopleCount, count));
        } else if (id == R.id.btn_xgramSwipeLeft || id == R.id.btn_xgramSwipeRight) {
          view.setData(com.fahadali.xgram.chats.XgramSwipeActions.name(settings.swipeAction(id == R.id.btn_xgramSwipeRight)));
        } else if (id == R.id.btn_xgramIconPack) {
          view.setData(com.fahadali.xgram.appearance.XgramIconPack.name(com.fahadali.xgram.appearance.XgramIconPack.current()));
        } else if (id == R.id.btn_xgramGhostMode) {
          view.getToggler().setRadioEnabled(settings.isGhostModeEnabled(), isUpdate);
        } else if (id == R.id.btn_xgramGhostDontRead) {
          view.getToggler().setRadioEnabled(settings.ghostDontRead(), isUpdate);
        } else if (id == R.id.btn_xgramGhostReadOnSend) {
          view.getToggler().setRadioEnabled(settings.ghostReadOnSend(), isUpdate);
          view.setEnabled(settings.ghostDontRead());
        } else if (id == R.id.btn_xgramGhostLocalRead) {
          view.getToggler().setRadioEnabled(settings.ghostLocalRead(), isUpdate);
          view.setEnabled(settings.ghostDontRead());
        } else if (id == R.id.btn_xgramGhostTypingForStickers) {
          view.getToggler().setRadioEnabled(settings.ghostTypingForStickers(), isUpdate);
          view.setEnabled(!settings.ghostDontType());
        } else if (id == R.id.btn_xgramGhostSilent) {
          view.getToggler().setRadioEnabled(settings.ghostSilent(), isUpdate);
          setReason(view, XgramSettingDeps.ghostSilentReason());
        } else if (id == R.id.btn_xgramGhostScheduleSend) {
          view.getToggler().setRadioEnabled(settings.ghostScheduleSend(), isUpdate);
          view.setEnabled(settings.ghostOffline());
        } else if (id == R.id.btn_xgramGhostDontType) {
          view.getToggler().setRadioEnabled(settings.ghostDontType(), isUpdate);
        } else if (id == R.id.btn_xgramGhostOffline) {
          view.getToggler().setRadioEnabled(settings.ghostOffline(), isUpdate);
        } else if (id == R.id.btn_xgramHistorySaveDeleted) {
          view.getToggler().setRadioEnabled(settings.historySaveDeleted(), isUpdate);
        } else if (id == R.id.btn_xgramHistorySaveMedia) {
          view.getToggler().setRadioEnabled(settings.historySaveMedia(), isUpdate);
          view.setEnabled(settings.historySaveDeleted());
        } else if (id == R.id.btn_xgramHistorySaveEdits) {
          view.getToggler().setRadioEnabled(settings.historySaveEdits(), isUpdate);
        } else if (id == R.id.btn_xgramHistoryRedDeleted) {
          view.getToggler().setRadioEnabled(settings.historyRedDeleted(), isUpdate);
          setReason(view, XgramSettingDeps.historyRedDeletedReason());
        } else if (id == R.id.btn_xgramHideSponsored) {
          view.getToggler().setRadioEnabled(settings.hideSponsored(), isUpdate);
        } else if (id == R.id.btn_xgramShowPeerIds) {
          view.getToggler().setRadioEnabled(settings.showPeerIds(), isUpdate);
        } else if (id == R.id.btn_xgramLimitMessageWidth) {
          view.getToggler().setRadioEnabled(settings.limitMessageWidth(), isUpdate);
        } else if (id == R.id.btn_xgramFoldHingeSplit) {
          view.getToggler().setRadioEnabled(settings.foldHingeSplit(), isUpdate);
          setReason(view, XgramSettingDeps.twoPaneOptionReason());
        } else if (id == R.id.btn_xgramFoldTabletop) {
          view.getToggler().setRadioEnabled(settings.foldTabletop(), isUpdate);
        } else if (id == R.id.btn_xgramPaneSplitEvenly) {
          view.getToggler().setRadioEnabled(settings.paneSplitEvenly(), isUpdate);
          setReason(view, XgramSettingDeps.twoPaneOptionReason());
        } else if (id == R.id.btn_xgramPaneResizable) {
          view.getToggler().setRadioEnabled(settings.paneResizable(), isUpdate);
          setReason(view, XgramSettingDeps.paneResizableReason());
        } else if (id == R.id.btn_xgramDragDrop) {
          view.getToggler().setRadioEnabled(settings.dragDrop(), isUpdate);
        } else if (id == R.id.btn_xgramOpenBeside) {
          view.getToggler().setRadioEnabled(settings.openBeside(), isUpdate);
        } else if (id == R.id.btn_xgramTwoPane) {
          view.getToggler().setRadioEnabled(settings.twoPaneLayout(), isUpdate);
        } else if (id == R.id.btn_xgramNavRail) {
          view.getToggler().setRadioEnabled(settings.navRail(), isUpdate);
          setReason(view, com.fahadali.xgram.layout.XgramNavRail.settingReason());
        } else if (id == R.id.btn_xgramBottomNav) {
          if (view.getToggler() != null) {
            view.getToggler().setRadioEnabled(settings.bottomNav(), isUpdate);
          } else {
            view.setData(com.fahadali.xgram.layout.XgramNavToolbar.summary()); // Expressive design: its page's link
          }
        } else if (id == R.id.btn_xgramHideFab) {
          view.setData(XgramSettingDeps.newChatButtonName(XgramSettingDeps.newChatButton()));
        } else if (id == R.id.btn_xgramConnectionStatusStart) {
          view.getToggler().setRadioEnabled(settings.connectionStatusAtStart(), isUpdate);
          setReason(view, XgramSettingDeps.connectionStatusPositionReason());
        } else if (id == R.id.btn_xgramAllChatsLabel) {
          view.getToggler().setRadioEnabled(settings.allChatsLabel(), isUpdate);
          setReason(view, XgramSettingDeps.allChatsLabelReason(tdlib));
        } else if (id == R.id.btn_xgramHideFolders) {
          view.getToggler().setRadioEnabled(settings.hideFolders(tdlib.accountId()), isUpdate);
        } else if (id == R.id.btn_xgramHideAllChats) {
          view.getToggler().setRadioEnabled(settings.hideAllChats(tdlib.accountId()), isUpdate);
          setReason(view, XgramSettingDeps.hideAllChatsReason(tdlib));
        } else if (id == R.id.btn_xgramPullToNext) {
          view.getToggler().setRadioEnabled(settings.pullToNext(), isUpdate);
        } else if (id == R.id.btn_xgramActivityShow) {
          view.getToggler().setRadioEnabled(settings.activityOn(), isUpdate);
        } else if (id == R.id.btn_xgramActivityReactions) {
          view.getToggler().setRadioEnabled(settings.activityReactions(), isUpdate);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityMuted) {
          view.getToggler().setRadioEnabled(settings.activityMuted(), isUpdate);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityDiscussion) {
          view.getToggler().setRadioEnabled(settings.activityDiscussion(), isUpdate);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityLookBack) {
          view.getToggler().setRadioEnabled(settings.activityLookBack(), isUpdate);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityBadge) {
          view.getToggler().setRadioEnabled(settings.activityBadge(), isUpdate);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityNavBar) {
          // Expressive design: the floating bar's Activity tab, chosen with the other tabs on the Navigation bar page
          boolean tab = (settings.navTabs() & (1 << com.fahadali.xgram.layout.XgramNavToolbar.DEST_ACTIVITY)) != 0;
          view.setData(tab ? R.string.XgramActivityNavBarTab : R.string.XgramActivityBarOff);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityBar) {
          view.setData(settings.activityBar() == XgramSettings.ACTIVITY_BAR_CALLS ? R.string.XgramActivityBarCalls : R.string.XgramActivityBarOff);
          setReason(view, XgramSettingDeps.activityOptionsReason());
        } else if (id == R.id.btn_xgramActivityHidden) {
          int hidden = com.fahadali.xgram.activity.XgramActivityStore.instance().summary(tdlib.id()).hiddenCount();
          view.setData(hidden == 0 ? Lang.getString(R.string.XgramNone) : hidden == 1 ? Lang.getString(R.string.XgramActivityHiddenCountOne) : Lang.getString(R.string.XgramActivityHiddenCountMany, hidden));
        } else if (id == R.id.btn_xgramLaterRow) {
          view.getToggler().setRadioEnabled(com.fahadali.xgram.later.XgramLater.showRow(), isUpdate);
        } else if (id == R.id.btn_xgramLaterRaise) {
          view.getToggler().setRadioEnabled(com.fahadali.xgram.later.XgramLater.raiseWoken(), isUpdate);
        } else if (id == R.id.btn_xgramLaterWakeEarly) {
          view.setData(com.fahadali.xgram.later.XgramLater.wakeEarlyName(com.fahadali.xgram.later.XgramLater.wakeEarlyMode()));
        } else if (id == R.id.btn_xgramLaterExact) {
          view.setData(com.fahadali.xgram.later.XgramLater.exactRowValue(context()));
        } else if (id == R.id.btn_xgramR7ChannelReactions) {
          view.getToggler().setRadioEnabled(settings.channelReactions(), isUpdate);
        } else if (id == R.id.btn_xgramSaveLastSeen) {
          view.getToggler().setRadioEnabled(settings.saveLastSeen(), isUpdate);
        } else if (id == R.id.btn_xgramShowSeconds) {
          view.getToggler().setRadioEnabled(settings.showSeconds(), isUpdate);
        } else if (id == R.id.btn_xgramTapTimeDetails) {
          view.getToggler().setRadioEnabled(settings.tapTimeForDetails(), isUpdate);
        } else if (id == R.id.btn_xgramTextSelectDrag) {
          view.getToggler().setRadioEnabled(settings.textSelectDrag(), isUpdate);
        } else if (id == R.id.btn_xgramCleanLinks) {
          view.getToggler().setRadioEnabled(settings.cleanLinks(), isUpdate);
        } else if (id == R.id.btn_xgramFixLinkPreviews) {
          view.getToggler().setRadioEnabled(settings.fixLinkPreviews(), isUpdate);
        } else if (id == R.id.btn_xgramAllowScreenshots) {
          view.getToggler().setRadioEnabled(settings.allowScreenshots(), isUpdate);
        } else if (id == R.id.btn_xgramSaveProtected) {
          view.getToggler().setRadioEnabled(settings.saveProtected(), isUpdate);
        } else if (id == R.id.btn_xgramHidePhone) {
          view.getToggler().setRadioEnabled(settings.hidePhone(), isUpdate);
        } else if (id == R.id.btn_xgramBlockContentCapture) {
          view.getToggler().setRadioEnabled(settings.blockContentCapture(), isUpdate);
        } else if (id == R.id.btn_xgramPreferIpv6) {
          view.getToggler().setRadioEnabled(settings.preferIpv6(), isUpdate);
        } else if (id == R.id.btn_xgramDownloadBoost) {
          view.getToggler().setRadioEnabled(settings.downloadBoost(), isUpdate);
        } else if (id == R.id.btn_xgramForwardSender) {
          view.setData(com.fahadali.xgram.chats.XgramForwardOptions.defaultName(settings.forwardSender()));
        } else if (id == R.id.btn_xgramMergeCommunities) {
          view.getToggler().setRadioEnabled(settings.mergeCommunities(), isUpdate);
        } else if (id == R.id.btn_xgramHideBlocked) {
          view.getToggler().setRadioEnabled(settings.hideBlocked(), isUpdate);
        } else if (id == R.id.btn_xgramDeleteForEveryone) {
          view.getToggler().setRadioEnabled(settings.deleteForEveryoneByDefault(), isUpdate);
        } else if (id == R.id.btn_xgramAiTools) {
          view.getToggler().setRadioEnabled(settings.aiTools(), isUpdate);
        } else if (id == R.id.btn_xgramTrAlways) {
          view.getToggler().setRadioEnabled(settings.translateAlways(), isUpdate);
        } else if (id == R.id.btn_xgramTrFallback) {
          view.getToggler().setRadioEnabled(settings.translateFallback(), isUpdate);
          // Only a third-party service can fail over to Telegram.
          setReason(view, XgramSettingDeps.translateFallbackReason());
        } else if (id == R.id.btn_xgramTrProvider) {
          view.setData(com.fahadali.xgram.translate.XgramTranslator.name(settings.translateProvider()));
        } else if (id == R.id.btn_xgramTrTarget) {
          view.setData(com.fahadali.xgram.chats.XgramTranslateTargetController.currentName());
        } else if (id == R.id.btn_xgramTrAutoAll) {
          view.getToggler().setRadioEnabled(settings.translateAutoAll(), isUpdate);
        } else if (id == R.id.btn_xgramTrStyle) {
          view.setData(translateStyleName());
        } else if (id == R.id.btn_xgramTapToRecord) {
          view.getToggler().setRadioEnabled(settings.tapToRecord(), isUpdate);
          setReason(view, XgramSettingDeps.tapToRecordReason());
        } else if (id == R.id.btn_xgramInlineVideo) {
          view.getToggler().setRadioEnabled(settings.inlineVideo(), isUpdate);
        } else if (id == R.id.btn_xgramVideoAutoHide) {
          view.getToggler().setRadioEnabled(settings.videoAutoHide(), isUpdate);
        } else if (id == R.id.btn_xgramInlineVideoStart) {
          view.setData(settings.inlineVideoStart() == XgramSettings.INLINE_VIDEO_START_TAP ? R.string.XgramInlineVideoStartTap : R.string.XgramInlineVideoStartAuto);
          view.setEnabled(XgramSettingDeps.inlineVideoStartEnabled());
        } else if (id == R.id.btn_xgramCheckInTimer) {
          view.setData(com.fahadali.xgram.checkin.XgramCheckInSheet.durationName(settings.checkInTimer()));
        } else if (id == R.id.btn_xgramCheckInGrace) {
          view.setData(com.fahadali.xgram.checkin.XgramCheckInSheet.durationName(settings.checkInGrace()));
        } else if (id == R.id.btn_xgramCheckInRadius) {
          view.setData(com.fahadali.xgram.checkin.XgramCheckInSheet.radiusName(settings.checkInRadius()));
        } else if (id == R.id.btn_xgramCheckInLocation) {
          view.getToggler().setRadioEnabled(settings.checkInLocation(), isUpdate);
        } else if (id == R.id.btn_xgramCheckInBattery) {
          view.getToggler().setRadioEnabled(settings.checkInBattery(), isUpdate);
        } else if (id == R.id.btn_xgramCheckInFollowUp) {
          view.getToggler().setRadioEnabled(settings.checkInFollowUp(), isUpdate);
          // Only applies while the alert carries a location.
          setReason(view, settings.checkInLocation() ? 0 : R.string.XgramCheckInFollowUpNeeds);
        } else if (id == R.id.btn_xgramEnterToSend) {
          view.setData(com.fahadali.xgram.layout.XgramKeyboard.enterToSendName(com.fahadali.xgram.layout.XgramKeyboard.enterToSend()));
        } else if (id == R.id.btn_xgramUndoSend) {
          view.setData(com.fahadali.xgram.chats.XgramUndoSend.delayName(settings.undoSendSeconds()));
        } else if (id == R.id.btn_xgramUndoSendBubbles) {
          view.getToggler().setRadioEnabled(settings.undoSendBubbles(), isUpdate);
          setReason(view, XgramSettingDeps.undoSendOptionsReason());
        } else if (id == R.id.btn_xgramUndoSendNewest) {
          view.getToggler().setRadioEnabled(settings.undoSendNewestOnly(), isUpdate);
          setReason(view, XgramSettingDeps.undoSendOptionsReason());
        } else if (id == R.id.btn_xgramUndoSendVoice) {
          view.getToggler().setRadioEnabled(settings.undoSendVoice(), isUpdate);
          setReason(view, XgramSettingDeps.undoSendVoiceReason());
        } else if (id == R.id.btn_xgramProxyOffVpn) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnVpn(), isUpdate);
          view.setEnabled(XgramSettingDeps.proxyRulesEnabled()); // the note under them says why
        } else if (id == R.id.btn_xgramProxyOffWifi) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnWifi(), isUpdate);
          view.setEnabled(XgramSettingDeps.proxyRulesEnabled());
        } else if (id == R.id.btn_xgramProxyOffMobile) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnMobile(), isUpdate);
          view.setEnabled(XgramSettingDeps.proxyRulesEnabled());
        }
        // Settings search: results show where they live; locked switches stay tappable. Keep this last.
        XgramSettingsSearch.bindResult(item, view, isUpdate);
      }

      @Override
      protected void onSliderValueChanged (ListItem item, org.thunderdog.challegram.widget.SliderWrapView view, int newValue, int oldValue) {
        if (item.getId() == R.id.btn_xgramDownloadBoostLevel) {
          XgramSettings.instance().setDownloadBoostLevel(XgramDownloadBoost.LEVEL_LOW + newValue);
          updateDownloadBoostInfo();
        }
      }

      @Override
      protected void setSliderValues (ListItem item, org.thunderdog.challegram.widget.SliderWrapView view) {
        super.setSliderValues(item, view);
        // The strength is only read while boost is on: the knob can't be moved and the row is faded until then.
        // Every slider row sets its state, so a recycled view never keeps another row's.
        boolean enabled = item.getId() != R.id.btn_xgramDownloadBoostLevel || XgramSettingDeps.downloadBoostLevelEnabled();
        boolean animated = androidx.core.view.ViewCompat.isAttachedToWindow(view);
        for (int i = 0; i < view.getChildCount(); i++) {
          View child = view.getChildAt(i);
          if (child instanceof org.thunderdog.challegram.mediaview.SliderView) {
            ((org.thunderdog.challegram.mediaview.SliderView) child).setSlideEnabled(enabled, animated);
          } else {
            child.setAlpha(enabled ? 1f : .5f);
          }
        }
      }

      @SuppressLint("ClickableViewAccessibility")
      @Override
      public void modifySettingView (int viewType, SettingView settingView) {
        if (viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER) {
          // Search results: a tap on the switch flips it, a tap elsewhere opens the row's page.
          settingView.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
              lastTouchView = v;
              lastTouchX = e.getX();
            }
            return false;
          });
        }
      }
    };

    if (section() != SECTION_MAIN) {
      adapter.setOnLongClickListener(null); // only the main page's About › Version has a long press
    }
    adapter.setItems(buildSectionItems(section()), false);
    if (section() == SECTION_UPDATES || section() == SECTION_MAIN) { // the main page's About › Version follows it too
      context().appUpdater().addListener(this);
    }

    recyclerView.setItemAnimator(null); // search swaps the whole list on every keystroke
    recyclerView.setAdapter(adapter);
    if (highlightId != 0) {
      int index = adapter.indexOfViewById(highlightId);
      if (index != -1) {
        ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(index, Screen.dp(72f));
      }
    }
  }

  // Pages

  /** Rows of one page. The settings search indexes these same lists, so every row a page shows is searchable. */
  List<ListItem> buildSectionItems (int section) {
    List<ListItem> items = new ArrayList<>();
    switch (section) {
      case SECTION_APPEARANCE: buildAppearance(items); break;
      case SECTION_LARGE_SCREENS: buildLargeScreens(items); break;
      case SECTION_CHAT_LIST: buildChatList(items); break;
      case SECTION_MESSAGES: buildMessages(items); break;
      case SECTION_ACTIVITY: buildActivity(items); break;
      case SECTION_TRANSLATION: buildTranslation(items); break;
      case SECTION_GHOST:
      case SECTION_PRIVACY: buildPrivacy(items); break;
      case SECTION_FILTERS: buildFilters(items); break;
      case SECTION_HISTORY: buildHistory(items); break;
      case SECTION_NETWORK: buildNetwork(items); break;
      case SECTION_BACKUP: buildBackup(items); break;
      case SECTION_UPDATES: buildUpdates(items); break;
      default: buildMain(items); break;
    }
    return items;
  }

  private static void addRows (List<ListItem> items, ListItem... rows) {
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < rows.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(rows[i]);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  private static ListItem header (@StringRes int title) {
    return new ListItem(ListItem.TYPE_HEADER, 0, 0, title);
  }

  private static ListItem description (@StringRes int text) {
    return new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, text);
  }

  /** A switch with a short line under its title: its scope or effect. */
  private static ListItem toggle (@IdRes int id, @StringRes int title, @StringRes int subtitle) {
    return toggle(id, 0, title, subtitle);
  }

  private static ListItem toggle (@IdRes int id, int icon, @StringRes int title, @StringRes int subtitle) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, id, icon, title).setStringValue(Lang.getString(subtitle));
  }

  /** A switch without a line under its title. */
  private static ListItem toggle (@IdRes int id, @StringRes int title) {
    return new ListItem(ListItem.TYPE_RADIO_SETTING, id, 0, title);
  }

  /** A row that opens a page or a choice; setValuedSetting puts its current value under the title. */
  private static ListItem link (@IdRes int id, @StringRes int title) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, id, 0, title);
  }

  /** A row that opens a page, with a fixed line under its title. */
  private static ListItem link (@IdRes int id, int icon, @StringRes int title, @StringRes int subtitle) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, id, icon, title).setStringValue(Lang.getString(subtitle));
  }

  private static ListItem category (@IdRes int id, int icon, int title) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, id, icon, title);
  }

  private void buildMain (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramWhatsNew, R.drawable.baseline_whatshot_24, R.string.XgramWhatsNew));
    // The look and the everyday screens, then XGram's own features, then the plumbing
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      category(R.id.btn_xgramSectionAppearance, R.drawable.baseline_palette_24, R.string.XgramAppearance),
      category(R.id.btn_xgramSectionChatList, R.drawable.baseline_forum_24, R.string.XgramChatList),
      category(R.id.btn_xgramSectionChats, R.drawable.baseline_chat_bubble_24, R.string.XgramMessages),
      category(R.id.btn_xgramSectionPrivacy, R.drawable.baseline_lock_24, R.string.XgramPrivacy)
    );
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      category(R.id.btn_xgramSectionActivity, R.drawable.baseline_notifications_active_24, R.string.XgramActivitySection),
      category(R.id.btn_xgramSectionTranslation, R.drawable.baseline_translate_24, R.string.XgramTranslation),
      category(R.id.btn_xgramSectionFilters, R.drawable.baseline_filter_variant_remove_24, R.string.XgramFilters),
      category(R.id.btn_xgramSectionHistory, R.drawable.baseline_history_24, R.string.XgramHistory)
    );
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      category(R.id.btn_xgramSectionNetwork, R.drawable.baseline_public_24, R.string.XgramNetwork),
      category(R.id.btn_xgramSectionBackup, R.drawable.baseline_import_export_24, R.string.XgramBackup)
    );
    items.add(header(R.string.XgramAbout));
    addRows(items,
      // A tap opens App updates (also on Telegram X's main Settings page), a long press copies the version
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutVersion, R.drawable.baseline_info_24, R.string.XgramAboutVersion),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramLogsShare, R.drawable.baseline_share_24, R.string.XgramLogsShare),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutBasedOn, R.drawable.baseline_code_24, R.string.XgramAboutBasedOn),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutSupport, R.drawable.baseline_favorite_24, R.string.XgramAboutSupport)
    );
    items.add(description(R.string.XgramAboutCredits));
    // Debug builds' test switches (the send menu's, Activity's) are in Telegram X's Testing Utilities.
  }

  /**
   * Appearance: Expressive design first (it decides what the rest offers), the colors, text and icons, messages and
   * avatars, then the page for large screens. Rows a look can't use are left out, not greyed: Colors, Night mode and
   * Pure black are Expressive design's, Themes and Material You the classic look's.
   */
  private void buildAppearance (List<ListItem> items) {
    final boolean expressive = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (com.fahadali.xgram.appearance.XgramExpressive.isAvailable()) { // Android 13+
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      // One switch: Telegram X's own pages follow Expressive design (XgramSettings.composeTgxPages)
      addRows(items, toggle(R.id.btn_xgramExpressive, R.string.XgramExpressive, R.string.XgramExpressiveSub));
      items.add(description(expressive ? R.string.XgramExpressiveNote : R.string.XgramExpressiveNoteClassic));
    }

    items.add(header(R.string.XgramColors));
    if (expressive) {
      // Expressive design's colors: the wallpaper's or a picked one (XgramColorSource). The Compose page is the one shown
      // then; these rows are what the settings search finds (the custom color rows: XgramSearchIndex.composeRows).
      addRows(items, link(R.id.btn_xgramColorSource, R.string.XgramColorsSource));
      items.add(description(R.string.XgramColorsWallpaperInfo));
      List<ListItem> night = new ArrayList<>();
      // Telegram X's night mode settings (Auto night mode) are on its Themes page.
      night.add(link(R.id.btn_xgramAutoNightMode, R.string.XgramAutoNightMode));
      if (com.fahadali.xgram.appearance.XgramAmoled.isAvailable()) {
        night.add(toggle(R.id.btn_xgramPureBlack, R.string.XgramPureBlack, R.string.XgramPureBlackSub)); // black Material You surfaces at night
      }
      addRows(items, night.toArray(new ListItem[0]));
      items.add(description(R.string.XgramThemeExpressiveInfo));
    } else if (com.fahadali.xgram.appearance.XgramMaterialYou.isAvailable()) {
      // Android 12+: Material You's note goes right under it.
      addRows(items,
        link(R.id.btn_xgramThemes, R.string.XgramThemes),
        toggle(R.id.btn_xgramMaterialYou, R.string.XgramMaterialYou, R.string.XgramMaterialYouSub)
      );
      items.add(description(R.string.XgramMaterialYouInfo));
    } else {
      addRows(items, link(R.id.btn_xgramThemes, R.string.XgramThemes));
    }

    items.add(header(R.string.XgramHeaderTextIcons));
    List<ListItem> text = new ArrayList<>();
    if (com.fahadali.xgram.appearance.XgramFonts.isAvailable()) {
      text.add(link(R.id.btn_xgramFont, R.string.XgramFont)); // the app font (Android 8+: font variations)
    }
    text.add(link(R.id.btn_xgramIconPack, R.string.XgramIconPack));
    text.add(link(R.id.btn_xgramEmojiSet, R.string.XgramEmoji));
    text.add(link(R.id.btn_xgramAppIcon, R.string.XgramAppIcon));
    text.add(link(R.id.btn_xgramNotificationIcon, R.string.XgramNotificationIcon));
    addRows(items, text.toArray(new ListItem[0]));

    items.add(header(R.string.XgramHeaderMessagesAvatars));
    List<ListItem> chats = new ArrayList<>();
    chats.add(link(R.id.btn_xgramChatLook, 0, R.string.XgramChatLookRow, R.string.XgramChatLookSub));
    chats.add(link(R.id.btn_xgramAvatarCorners, R.string.XgramAvatarCorners));
    if (expressive) {
      // XGram M3E: the chat's floating buttons in the avatars' shape (only Expressive design draws them as M3 FABs)
      chats.add(toggle(R.id.btn_xgramChatFabsAvatarShape, R.string.XgramChatFabsAvatarShape, R.string.XgramChatFabsAvatarShapeSub));
    }
    addRows(items, chats.toArray(new ListItem[0]));

    // Two panes, the fold, drag and drop: mostly for tablets and unfolded phones, so a page of their own
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, link(R.id.btn_xgramLargeScreens, R.drawable.baseline_devices_other_24, R.string.XgramLargeScreens, R.string.XgramLargeScreensSummary));
  }

  /** Appearance › Large screens and foldables: two panes, the fold's postures, drag and drop. */
  private void buildLargeScreens (List<ListItem> items) {
    items.add(header(R.string.XgramHeaderTwoPanes));
    addRows(items,
      toggle(R.id.btn_xgramTwoPane, R.string.XgramTwoPane, R.string.XgramTwoPaneSub),
      toggle(R.id.btn_xgramPaneSplitEvenly, R.string.XgramPaneSplitEvenly, R.string.XgramPaneSplitEvenlySub),
      toggle(R.id.btn_xgramPaneResizable, R.string.XgramPaneResizable, R.string.XgramPaneResizableSub)
    );
    // How to drag the handle, or why there is none while split evenly (updated with the switches).
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramPaneInfo, 0, XgramSettingDeps.paneInfo()));
    addRows(items, toggle(R.id.btn_xgramLimitMessageWidth, R.string.XgramLimitMessageWidth, R.string.XgramLimitMessageWidthSub));
    // Foldables: the posture rows only where a fold can be reported (androidx.window, minSdk 23 builds).
    if (com.fahadali.xgram.layout.XgramWindow.isPostureSupported()) {
      items.add(header(R.string.XgramHeaderFoldables));
      addRows(items,
        toggle(R.id.btn_xgramFoldHingeSplit, R.string.XgramFoldHingeSplit, R.string.XgramFoldHingeSplitSub),
        toggle(R.id.btn_xgramFoldTabletop, R.string.XgramFoldTabletop, R.string.XgramFoldTabletopSub)
      );
      items.add(description(R.string.XgramFoldInfo));
    }
    items.add(header(R.string.XgramHeaderDragDrop));
    addRows(items,
      toggle(R.id.btn_xgramDragDrop, R.string.XgramDragDrop, R.string.XgramDragDropSub),
      toggle(R.id.btn_xgramOpenBeside, R.string.XgramOpenBeside, R.string.XgramOpenBesideSub)
    );
    items.add(description(R.string.XgramDragDropInfo));
  }

  private void buildChatList (List<ListItem> items) {
    final boolean expressive = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    // One line, two lines or three lines: Telegram X's popup with One line added. No header: the row says it (the
    // Compose page's header names its card of tiles, which has no title of its own).
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (expressive) {
      // Bigger text for three lines: the Compose page's switch under the card, here for the settings search
      addRows(items,
        link(R.id.btn_xgramChatListView, R.string.XgramChatListView),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramChatListBigText, 0, R.string.XgramChatListBigText)
      );
    } else {
      addRows(items, link(R.id.btn_xgramChatListView, R.string.XgramChatListView));
    }
    items.add(description(R.string.XgramChatListViewInfo));

    items.add(header(R.string.XgramHeaderFolders));
    addRows(items, toggle(R.id.btn_xgramHideFolders, R.string.XgramHideFolders, R.string.XgramHideFoldersSub));
    items.add(description(R.string.XgramChatListFoldersInfo));
    addRows(items,
      toggle(R.id.btn_xgramHideAllChats, R.string.XgramHideAllChats, R.string.XgramHideAllChatsSub),
      toggle(R.id.btn_xgramAllChatsLabel, R.string.XgramAllChatsLabel, R.string.XgramAllChatsLabelSub)
    );

    // Getting around: the bars, the side menu, the buttons above the list and the new chat button
    items.add(header(R.string.XgramHeaderNavigation));
    List<ListItem> navigation = new ArrayList<>();
    if (expressive) {
      // Expressive design's floating bar has a page of its own (tabs, colors, profile); the rail is only there
      navigation.add(link(R.id.btn_xgramBottomNav, R.string.XgramBottomNavOption));
      navigation.add(toggle(R.id.btn_xgramNavRail, R.string.XgramNavRailOption, R.string.XgramNavRailOptionSub));
    } else {
      navigation.add(toggle(R.id.btn_xgramBottomNav, R.string.XgramBottomNavOption, R.string.XgramBottomNavOptionSub));
    }
    navigation.add(link(R.id.btn_xgramSideMenu, R.string.XgramSideMenu));
    navigation.add(link(R.id.btn_xgramHeaderButtons, R.string.XgramHeaderButtons)); // the chat list's top row: lock, ghost mode, Activity, Search
    navigation.add(link(R.id.btn_xgramHideFab, R.string.XgramNavBarFab)); // at the side, with the navigation bar or hidden
    if (XgramSettingDeps.connectionStatusPositionEnabled()) {
      // Only the status bar text reads it, and Expressive design shows the connection in the app instead.
      navigation.add(toggle(R.id.btn_xgramConnectionStatusStart, R.string.XgramConnectionStatusStart, R.string.XgramConnectionStatusStartSub));
    }
    addRows(items, navigation.toArray(new ListItem[0]));

    items.add(header(R.string.XgramChats));
    addRows(items,
      toggle(R.id.btn_xgramMergeCommunities, R.string.XgramMergeCommunities, R.string.XgramMergeCommunitiesSub),
      link(R.id.btn_xgramChatLongPress, R.string.XgramChatLongPress),
      link(R.id.btn_xgramChatActionsOrder, R.string.XgramChatActionsOrder)
    );
  }

  private void buildMessages (List<ListItem> items) {
    // No header: the row is called Message menu already.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, link(R.id.btn_xgramMessageMenu, R.string.XgramMessageMenu));

    items.add(header(R.string.XgramHeaderGestures));
    addRows(items,
      link(R.id.btn_xgramSwipeLeft, R.string.XgramSwipeLeft),
      link(R.id.btn_xgramSwipeRight, R.string.XgramSwipeRight)
    );
    items.add(description(R.string.XgramSwipeInfo));
    addRows(items,
      toggle(R.id.btn_xgramTextSelectDrag, R.string.XgramSelTextDrag, R.string.XgramSelTextDragSub), // Select text without the menu
      toggle(R.id.btn_xgramPullToNext, R.string.XgramPullToNext, R.string.XgramPullToNextSub)
    );

    items.add(header(R.string.XgramHeaderSending));
    addRows(items,
      link(R.id.btn_xgramInputButtons, R.string.XgramInputButtons),
      toggle(R.id.btn_xgramTapToRecord, R.string.XgramTapToRecord, R.string.XgramTapToRecordSub)
    );
    items.add(description(R.string.XgramTapToRecordInfo));
    // The only Enter control: Telegram X's Interface › Send with Enter is hidden (XgramKeyboard.enterToSend)
    addRows(items, link(R.id.btn_xgramEnterToSend, R.string.XgramEnterToSend));
    items.add(description(R.string.XgramEnterToSendInfo));
    addRows(items,
      link(R.id.btn_xgramUndoSend, R.string.XgramUndoSend),
      toggle(R.id.btn_xgramUndoSendBubbles, R.string.XgramUndoSendBubbles, R.string.XgramUndoSendBubblesSub),
      toggle(R.id.btn_xgramUndoSendNewest, R.string.XgramUndoSendNewest, R.string.XgramUndoSendNewestSub),
      toggle(R.id.btn_xgramUndoSendVoice, R.string.XgramUndoSendVoice, R.string.XgramUndoSendVoiceSub)
    );
    items.add(description(R.string.XgramUndoSendInfo));

    items.add(header(R.string.XgramHeaderForwardDelete));
    addRows(items,
      link(R.id.btn_xgramForwardSender, R.string.XgramFwdSetting), // Show sender, Hide sender or the last choice
      toggle(R.id.btn_xgramDeleteForEveryone, R.string.XgramDeleteForEveryone, R.string.XgramCheckedByDefault)
    );

    // The page's media part: the photo editor and the camera have no settings of their own
    items.add(header(R.string.XgramHeaderVideos));
    addRows(items,
      toggle(R.id.btn_xgramInlineVideo, R.string.XgramInlineVideo, R.string.XgramInlineVideoSub),
      link(R.id.btn_xgramInlineVideoStart, R.string.XgramInlineVideoStart)
    );
    // What the rows do, or that Telegram X's Autoplay GIFs keeps videos from starting by themselves (updated with them).
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramInlineVideoInfo, 0, XgramSettingDeps.inlineVideoInfo()));
    addRows(items, toggle(R.id.btn_xgramVideoAutoHide, R.string.XgramVideoAutoHide, R.string.XgramVideoAutoHideSub));
    items.add(description(R.string.XgramVideoAutoHideInfo));

    items.add(header(R.string.XgramHeaderLinks));
    addRows(items,
      toggle(R.id.btn_xgramCleanLinks, R.string.XgramCleanLinks, R.string.XgramCleanLinksSub),
      toggle(R.id.btn_xgramFixLinkPreviews, R.string.XgramFixLinkPreviews, R.string.XgramFixLinkPreviewsSub)
    );
    items.add(description(R.string.XgramFixLinkPreviewsInfo));

    items.add(header(R.string.XgramR7ChReactHeader));
    addRows(items, toggle(R.id.btn_xgramR7ChannelReactions, R.string.XgramR7ChReactTitle, R.string.XgramR7ChReactSub));
    items.add(description(R.string.XgramR7ChReactInfo));

    // Showing more about a message or a profile
    items.add(header(R.string.XgramHeaderDetails));
    addRows(items,
      toggle(R.id.btn_xgramShowSeconds, R.string.XgramShowSeconds, R.string.XgramShowSecondsSub),
      toggle(R.id.btn_xgramTapTimeDetails, R.string.XgramTapTimeDetails, R.string.XgramTapTimeDetailsSub),
      toggle(R.id.btn_xgramShowPeerIds, R.string.XgramShowPeerIds, R.string.XgramShowPeerIdsSub)
    );

    items.add(header(R.string.XgramHeaderTelegramAi));
    addRows(items, toggle(R.id.btn_xgramAiTools, R.string.XgramAiTools, R.string.XgramAiToolsSub));
    items.add(description(R.string.XgramAiToolsInfo));
  }

  /**
   * Activity and reminders: Activity (com.fahadali.xgram.activity, every mention, reply and reaction in one place),
   * Later's snoozed chats (com.fahadali.xgram.later) and Check In's timer (com.fahadali.xgram.checkin, started from a
   * chat's ⋮ menu).
   */
  private void buildActivity (List<ListItem> items) {
    // No header: the switch says what it is.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, toggle(R.id.btn_xgramActivityShow, R.string.XgramActivityShow, R.string.XgramActivityShowInfo));
    items.add(header(R.string.XgramHeaderInclude));
    addRows(items,
      toggle(R.id.btn_xgramActivityReactions, R.string.XgramActivityIncludeReactions, R.string.XgramActivityIncludeReactionsInfo),
      toggle(R.id.btn_xgramActivityMuted, R.string.XgramActivityIncludeMuted, R.string.XgramActivityIncludeMutedInfo),
      toggle(R.id.btn_xgramActivityDiscussion, R.string.XgramActivityIncludeDiscussion, R.string.XgramActivityIncludeDiscussionInfo),
      toggle(R.id.btn_xgramActivityLookBack, R.string.XgramActivityLookBack, R.string.XgramActivityLookBackInfo)
    );
    items.add(header(R.string.XgramHeaderWhereItShows));
    final boolean expressive = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    addRows(items,
      toggle(R.id.btn_xgramActivityBadge, R.string.XgramActivityBadge, R.string.XgramActivityBadgeInfo),
      // Expressive design: a tab of the floating bar, chosen on its page; the classic bottom bar: in Calls' place
      expressive ? link(R.id.btn_xgramActivityNavBar, R.string.XgramActivityNavBar) : link(R.id.btn_xgramActivityBar, R.string.XgramActivityBar)
    );
    // The bell above the chat list: its only switch is on Header buttons
    items.add(description(expressive ? R.string.XgramActivityNavBarInfo : R.string.XgramActivityWhereInfo));
    addRows(items,
      link(R.id.btn_xgramActivityHidden, R.string.XgramActivityHiddenChats),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramActivityClearAll, 0, R.string.XgramActivityClearAll)
        .setStringValue(Lang.getString(R.string.XgramActivityClearAllInfo))
        .setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative)
    );
    items.add(description(R.string.XgramActivityLocalNote));

    // Later: snoozed chats and reminders
    items.add(header(R.string.XgramLaterHeader));
    addRows(items,
      toggle(R.id.btn_xgramLaterRow, R.string.XgramLaterRowSetting, R.string.XgramLaterRowSettingSub),
      toggle(R.id.btn_xgramLaterRaise, R.string.XgramLaterRaise, R.string.XgramLaterRaiseSub),
      link(R.id.btn_xgramLaterWakeEarly, R.string.XgramLaterWakeEarly),
      link(R.id.btn_xgramLaterExact, R.string.XgramLaterExact),
      link(R.id.btn_xgramLaterChannel, 0, R.string.XgramLaterChannelLink, R.string.XgramLaterChannelLinkSub)
    );

    // Check In: a safety timer, started from a chat's ⋮ menu
    items.add(header(R.string.XgramCheckInHeader));
    addRows(items,
      link(R.id.btn_xgramCheckInTimer, R.string.XgramCheckInTimer),
      link(R.id.btn_xgramCheckInGrace, R.string.XgramCheckInGrace),
      link(R.id.btn_xgramCheckInRadius, R.string.XgramCheckInRadius),
      toggle(R.id.btn_xgramCheckInLocation, R.string.XgramCheckInIncludeLocation, R.string.XgramCheckInLocationSub),
      toggle(R.id.btn_xgramCheckInBattery, R.string.XgramCheckInIncludeBattery),
      toggle(R.id.btn_xgramCheckInFollowUp, R.string.XgramCheckInFollowUp, R.string.XgramCheckInFollowUpSub)
    );
    items.add(description(R.string.XgramCheckInInfo));
  }

  private void buildTranslation (List<ListItem> items) {
    items.add(header(R.string.XgramHeaderService));
    addRows(items,
      link(R.id.btn_xgramTrProvider, R.string.XgramTrProvider),
      toggle(R.id.btn_xgramTrFallback, R.string.XgramTrFallback, R.string.XgramTrFallbackSub)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramTrProviderInfo, 0, com.fahadali.xgram.translate.XgramTranslator.info(XgramSettings.instance().translateProvider()), false));
    // Telegram X's translation page: the style (popup, inline, off) and Do not translate
    addRows(items, link(R.id.btn_xgramTrStyle, R.string.XgramTrStyle));

    items.add(header(R.string.XgramHeaderAutoTranslate));
    addRows(items,
      link(R.id.btn_xgramTrTarget, R.string.XgramTrTarget),
      toggle(R.id.btn_xgramTrAutoAll, R.string.XgramTrAutoAll)
    );
    items.add(description(R.string.XgramTrAutoAllInfo));

    items.add(header(R.string.XgramHeaderInMessageMenu));
    addRows(items, toggle(R.id.btn_xgramTrAlways, R.string.XgramTrAlways, R.string.XgramTrAlwaysSub));
    items.add(description(R.string.XgramTrAlwaysInfo));
  }

  private void buildPrivacy (List<ListItem> items) {
    // Ghost mode first: the main screen's ghost button, the side menu switch and the Quick Settings tile open this page.
    // No header: the switch is called Ghost mode already.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, toggle(R.id.btn_xgramGhostMode, R.drawable.baseline_ghost_24, R.string.XgramGhostModeToggle, R.string.XgramGhostModeSub));
    items.add(description(R.string.XgramGhostModeInfo));
    items.add(header(R.string.XgramHeaderReading));
    addRows(items,
      toggle(R.id.btn_xgramGhostDontRead, R.string.XgramGhostDontRead, R.string.XgramGhostDontReadSub),
      toggle(R.id.btn_xgramGhostReadOnSend, R.string.XgramGhostReadOnSend, R.string.XgramGhostReadOnSendSub),
      toggle(R.id.btn_xgramGhostLocalRead, R.string.XgramGhostLocalRead, R.string.XgramGhostLocalReadSub)
    );
    items.add(header(R.string.XgramHeaderTyping));
    addRows(items,
      toggle(R.id.btn_xgramGhostDontType, R.string.XgramGhostDontType, R.string.XgramGhostDontTypeSub),
      toggle(R.id.btn_xgramGhostTypingForStickers, R.string.XgramGhostTypingForStickers, R.string.XgramGhostTypingForStickersSub)
    );
    items.add(header(R.string.XgramHeaderOnlineStatus));
    addRows(items,
      toggle(R.id.btn_xgramGhostOffline, R.string.XgramGhostOffline, R.string.XgramGhostOfflineSub),
      toggle(R.id.btn_xgramGhostScheduleSend, R.string.XgramGhostScheduleSend, R.string.XgramGhostScheduleSendSub)
    );
    items.add(header(R.string.XgramHeaderSending));
    addRows(items, toggle(R.id.btn_xgramGhostSilent, R.string.XgramGhostSilent, R.string.XgramGhostSilentSub));

    items.add(header(R.string.XgramHeaderProtectedContent));
    addRows(items,
      toggle(R.id.btn_xgramAllowScreenshots, R.string.XgramAllowScreenshots, R.string.XgramAllowScreenshotsSub),
      toggle(R.id.btn_xgramSaveProtected, R.string.XgramSaveProtected, R.string.XgramSaveProtectedSub)
    );
    items.add(header(R.string.XgramHeaderDevice));
    addRows(items,
      toggle(R.id.btn_xgramHidePhone, R.string.XgramHidePhone, R.string.XgramHidePhoneSub), // its only switch (the side menu's header)
      toggle(R.id.btn_xgramBlockContentCapture, R.string.XgramBlockContentCapture, R.string.XgramBlockContentCaptureSub)
    );
    items.add(description(R.string.XgramPrivacyInfo));
  }

  private void buildFilters (List<ListItem> items) {
    items.add(header(R.string.XgramHeaderHide));
    addRows(items,
      toggle(R.id.btn_xgramHideSponsored, R.string.XgramHideSponsored, R.string.XgramHideSponsoredSub),
      toggle(R.id.btn_xgramHideBlocked, R.string.XgramHideBlocked, R.string.XgramHideBlockedSub)
    );
    items.add(header(R.string.XgramHeaderWords));
    addRows(items, link(R.id.btn_xgramFilters, R.string.XgramWordFilters));
    items.add(description(R.string.XgramFiltersInfo));
    items.add(header(R.string.XgramHeaderPeople));
    addRows(items, link(R.id.btn_xgramHiddenPeople, R.string.XgramShadowBanned));
    items.add(description(R.string.XgramShadowBanInfo));
  }

  private void buildHistory (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, link(R.id.btn_xgramHistoryView, R.drawable.baseline_history_24, R.string.XgramHistoryView, R.string.XgramHistoryViewSub));
    items.add(header(R.string.XgramHeaderSaving));
    addRows(items,
      toggle(R.id.btn_xgramHistorySaveDeleted, R.string.XgramHistorySaveDeleted),
      toggle(R.id.btn_xgramHistorySaveMedia, R.string.XgramHistorySaveMedia, R.string.XgramHistorySaveMediaSub),
      toggle(R.id.btn_xgramHistorySaveEdits, R.string.XgramHistorySaveEdits),
      link(R.id.btn_xgramHistoryRetention, R.string.XgramHistoryRetention)
    );
    items.add(description(R.string.XgramHistorySettingsInfo));
    items.add(header(R.string.XgramHeaderDisplay));
    addRows(items, toggle(R.id.btn_xgramHistoryRedDeleted, R.string.XgramHistoryRedDeleted, R.string.XgramHistoryRedDeletedSub));
    items.add(header(R.string.XgramHeaderLastSeen));
    addRows(items, toggle(R.id.btn_xgramSaveLastSeen, R.string.XgramSaveLastSeen, R.string.XgramSaveLastSeenSub));
    items.add(description(R.string.XgramLastSeenInfo));
  }

  private void buildNetwork (List<ListItem> items) {
    items.add(header(R.string.XgramSpeed));
    String[] levels = new String[XgramDownloadBoost.LEVEL_NAMES.length];
    for (int i = 0; i < levels.length; i++) {
      levels[i] = Lang.getString(XgramDownloadBoost.LEVEL_NAMES[i]);
    }
    addRows(items,
      toggle(R.id.btn_xgramDownloadBoost, R.string.XgramDownloadBoost, R.string.XgramDownloadBoostSub),
      new ListItem(ListItem.TYPE_SLIDER, R.id.btn_xgramDownloadBoostLevel, 0, R.string.XgramBoostStrength)
        .setSliderInfo(levels, XgramSettings.instance().downloadBoostLevel() - XgramDownloadBoost.LEVEL_LOW)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramDownloadBoostInfo, 0, downloadBoostInfo(), false));
    items.add(header(R.string.XgramProxyRules));
    addRows(items,
      toggle(R.id.btn_xgramProxyOffVpn, R.string.XgramProxyOffVpn),
      toggle(R.id.btn_xgramProxyOffWifi, R.string.XgramProxyOffWifi),
      toggle(R.id.btn_xgramProxyOffMobile, R.string.XgramProxyOffMobile)
    );
    // What the rules do, or that there is no proxy for them yet (they are greyed then).
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramProxyRulesInfo, 0, XgramSettingDeps.proxyRulesInfo()));
    items.add(header(R.string.XgramHeaderConnection));
    addRows(items, toggle(R.id.btn_xgramPreferIpv6, R.string.XgramPreferIpv6, R.string.XgramPreferIpv6Sub));
  }

  private static CharSequence downloadBoostInfo () {
    return Lang.getString(XgramDownloadBoost.description(XgramDownloadBoost.level())) + "\n\n" + Lang.getString(R.string.XgramDownloadBoostInfo);
  }

  /** Telegram, Google Translate or Yandex Translate; a third-party service asks once, saying the text goes to it. */
  private void showTranslateProviderOptions () {
    com.fahadali.xgram.translate.XgramTranslateProviderPicker.show(this, this::setTranslateProvider);
  }

  private void setTranslateProvider (int provider) {
    XgramSettings.instance().setTranslateProvider(provider);
    adapter.updateValuedSettingById(R.id.btn_xgramTrProvider);
    adapter.updateValuedSettingById(R.id.btn_xgramTrFallback);
    updateTranslateProviderInfo();
  }

  /** The note under Translation service says who translates. */
  private void updateTranslateProviderInfo () {
    int index = adapter.indexOfViewById(R.id.btn_xgramTrProviderInfo);
    if (index != -1) {
      CharSequence info = com.fahadali.xgram.translate.XgramTranslator.info(XgramSettings.instance().translateProvider());
      if (adapter.getItems().get(index).setStringIfChanged(info)) {
        adapter.updateItem(index);
      }
    }
  }

  private void updateDownloadBoostInfo () {
    int index = adapter.indexOfViewById(R.id.btn_xgramDownloadBoostInfo);
    if (index != -1) {
      adapter.getItems().get(index).setString(downloadBoostInfo());
      adapter.updateItem(index);
    }
  }

  // Settings that depend on other settings (XgramSettingDeps)

  /** A row that only applies while another setting allows it: greyed, with the reason under its title (0: usable). */
  private static void setReason (SettingView view, @StringRes int reason) {
    if (reason != 0) {
      view.setData(reason);
    }
    view.setEnabled(reason == 0);
  }

  /** A note whose text follows other settings. */
  private void updateDescription (@IdRes int id, @StringRes int text) {
    int index = adapter.indexOfViewById(id);
    if (index != -1 && adapter.getItems().get(index).setStringIfChanged(text)) {
      adapter.updateItem(index);
    }
  }

  /** Activity locks its other rows while it is off. */
  private void updateActivityRows () {
    adapter.updateValuedSettingById(R.id.btn_xgramActivityReactions);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityMuted);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityDiscussion);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityLookBack);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityBadge);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityNavBar);
    adapter.updateValuedSettingById(R.id.btn_xgramActivityBar);
  }

  /** Activity on the classic bottom bar: not there, or in Calls' place (the bar follows at once). */
  private void showActivityBarOptions () {
    int mode = XgramSettings.instance().activityBar();
    showSettings(new SettingsWrapBuilder(R.id.btn_xgramActivityBar)
      .setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramActivityBarOff, 0, R.string.XgramActivityBarOff, R.id.btn_xgramActivityBar, mode != XgramSettings.ACTIVITY_BAR_CALLS),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramActivityBarCalls, 0, R.string.XgramActivityBarCalls, R.id.btn_xgramActivityBar, mode == XgramSettings.ACTIVITY_BAR_CALLS)
      })
      .setIntDelegate((settingsId, result) -> {
        int picked = result.get(R.id.btn_xgramActivityBar);
        XgramSettings.instance().setActivityBar(picked == R.id.btn_xgramActivityBarCalls ? XgramSettings.ACTIVITY_BAR_CALLS : XgramSettings.ACTIVITY_BAR_OFF);
        if (adapter != null && !isDestroyed()) {
          adapter.updateValuedSettingById(R.id.btn_xgramActivityBar);
        }
      }));
  }

  /** Two-pane layout and Split evenly lock the pane rows below them. */
  private void updatePaneRows () {
    adapter.updateValuedSettingById(R.id.btn_xgramPaneSplitEvenly);
    adapter.updateValuedSettingById(R.id.btn_xgramPaneResizable);
    adapter.updateValuedSettingById(R.id.btn_xgramFoldHingeSplit);
    updateDescription(R.id.btn_xgramPaneInfo, XgramSettingDeps.paneInfo());
  }

  private void buildBackup (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      link(R.id.btn_xgramExportSettings, R.drawable.baseline_share_24, R.string.XgramExportSettings, R.string.XgramExportSettingsSub),
      link(R.id.btn_xgramImportSettings, R.drawable.baseline_content_paste_24, R.string.XgramImportSettings, R.string.XgramImportSettingsSub)
    );
    items.add(description(R.string.XgramBackupInfo));
    addRows(items, new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramResetSettings, R.drawable.baseline_delete_24, R.string.XgramResetSettings).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
  }

  private void buildUpdates (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      link(R.id.btn_xgramUpdateChannel, R.string.XgramUpdateChannel),
      link(R.id.btn_xgramUpdateMode, R.string.XgramUpdateMode)
    );
    items.add(description(R.string.XgramUpdateChannelInfo));
    addRows(items,
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramUpdateCheck, R.drawable.baseline_system_update_24, R.string.CheckForUpdates),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramUpdateInstalled, R.drawable.baseline_info_24, R.string.XgramUpdateInstalled)
    );
    items.add(description(R.string.XgramUpdateCheckInfo));
  }

  // Values

  /** The theme in use: a built-in one's name, or the name of an installed theme (XGram's themes are installed ones). */
  static String currentThemeName () { // XGram: package, for XgramComposeBridge
    try {
      int themeId = org.thunderdog.challegram.theme.ThemeManager.instance().currentThemeId();
      if (org.thunderdog.challegram.theme.ThemeManager.isCustomTheme(themeId)) {
        String name = Settings.instance().getCustomThemeName(org.thunderdog.challegram.theme.ThemeManager.resolveCustomThemeId(themeId));
        return name != null && !name.isEmpty() ? name : Lang.getString(R.string.ThemeCustom);
      }
      return Lang.getString(org.thunderdog.challegram.theme.ThemeManager.getBuiltinThemeName(themeId));
    } catch (RuntimeException e) { // a theme being edited has no name
      return Lang.getString(R.string.ThemeCustom);
    }
  }

  /** The emoji set, as Settings → Stickers and emoji shows it. */
  static CharSequence emojiSetName () { // XGram: package, for XgramComposeBridge
    if (com.fahadali.xgram.appearance.XgramSystemEmoji.isEnabled()) {
      return Lang.getString(R.string.XgramEmojiSystem);
    }
    Settings.EmojiPack emojiPack = Settings.instance().getEmojiPack();
    if (emojiPack == null || BuildConfig.EMOJI_BUILTIN_ID.equals(emojiPack.identifier)) {
      return Lang.getString(R.string.EmojiBuiltIn);
    }
    return emojiPack.displayName;
  }

  /** Telegram X's translation style, with the names its own page uses. */
  static @StringRes int translateStyleName () { // XGram: package, for XgramComposeBridge
    switch (Settings.instance().getChatTranslateMode()) {
      case Settings.TRANSLATE_MODE_INLINE: return R.string.ChatTranslateStyle2;
      case Settings.TRANSLATE_MODE_NONE: return R.string.ChatTranslateStyle3;
      case Settings.TRANSLATE_MODE_POPUP:
      default: return R.string.ChatTranslateStyle1;
    }
  }

  private static @StringRes int notificationIconName (int icon) {
    return icon == XgramSettings.NOTIFICATION_ICON_TELEGRAM_X ? R.string.XgramNotificationIconTelegramX : R.string.XgramNotificationIconXgram;
  }

  /** XGram's X or Telegram X's original glyph in the status bar, apart from the app icon. New notifications use it. */
  private void showNotificationIconOptions () {
    boolean telegramX = XgramSettings.instance().notificationIcon() == XgramSettings.NOTIFICATION_ICON_TELEGRAM_X;
    showSettings(new SettingsWrapBuilder(R.id.btn_xgramNotificationIcon)
      .addHeaderItem(Lang.getString(R.string.XgramNotificationIconInfo))
      .setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramNotificationIconXgram, 0, R.string.XgramNotificationIconXgram, R.id.btn_xgramNotificationIcon, !telegramX),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramNotificationIconTelegramX, 0, R.string.XgramNotificationIconTelegramX, R.id.btn_xgramNotificationIcon, telegramX)
      })
      .setIntDelegate((settingsId, result) -> {
        int picked = result.get(R.id.btn_xgramNotificationIcon);
        XgramSettings.instance().setNotificationIcon(picked == R.id.btn_xgramNotificationIconTelegramX ? XgramSettings.NOTIFICATION_ICON_TELEGRAM_X : XgramSettings.NOTIFICATION_ICON_XGRAM);
        if (adapter != null && !isDestroyed()) {
          adapter.updateValuedSettingById(R.id.btn_xgramNotificationIcon);
        }
      }));
  }

  static int messageMenuHiddenCount () { // XGram: package, for XgramComposeBridge
    int count = 0;
    for (com.fahadali.xgram.chats.XgramMessageMenu.Option option : com.fahadali.xgram.chats.XgramMessageMenu.OPTIONS) {
      // Only options the Message menu page lists (XDrive's Add to folder: while XDrive's test is on), so the count
      // never names a hidden option the user can't see there to show again
      if (com.fahadali.xgram.chats.XgramMessageMenu.isAvailable(option.key) && com.fahadali.xgram.chats.XgramMessageMenu.isHidden(option.key)) {
        count++;
      }
    }
    return count;
  }

  /**
   * About › Version: this build's version and how updates stand, the states Telegram X's main Settings shows on its
   * App updates row (XGram's own way in to App updates).
   */
  static String aboutVersionValue (AppUpdater updater) { // XGram: package, for XgramComposeBridge
    @StringRes int status;
    if (XgramUpdates.channel() == XgramUpdates.CHANNEL_OFF) {
      status = R.string.XgramAboutUpdatesOff;
    } else {
      switch (updater.state()) {
        case AppUpdater.State.CHECKING:
          status = R.string.XgramAboutUpdateChecking;
          break;
        case AppUpdater.State.AVAILABLE:
        case AppUpdater.State.DOWNLOADING:
          status = R.string.XgramAboutUpdateAvailable;
          break;
        case AppUpdater.State.READY_TO_INSTALL:
        case AppUpdater.State.INSTALLING:
          status = R.string.XgramAboutUpdateReady;
          break;
        case AppUpdater.State.NONE:
        default:
          status = R.string.XgramAboutUpToDate;
          break;
      }
    }
    return Lang.getString(R.string.XgramAboutVersionValue, BuildConfig.VERSION_NAME, Lang.getString(status));
  }

  /** The "Check for updates" row follows the updater, as in Telegram X's own settings. */
  private void bindUpdateCheck (SettingView v, boolean isUpdate) {
    AppUpdater updater = context().appUpdater();
    if (XgramUpdates.channel() == XgramUpdates.CHANNEL_OFF) {
      v.setEnabledAnimated(false, isUpdate);
      v.setName(R.string.CheckForUpdates);
      return;
    }
    switch (updater.state()) {
      case AppUpdater.State.CHECKING:
        v.setEnabledAnimated(false, isUpdate);
        v.setName(R.string.CheckingForUpdates);
        break;
      case AppUpdater.State.AVAILABLE: {
        v.setEnabledAnimated(true, isUpdate);
        long bytesToDownload = updater.totalBytesToDownload() - updater.bytesDownloaded();
        if (bytesToDownload > 0) {
          v.setName(Lang.getStringBold(R.string.DownloadUpdateSize, Strings.buildSize(bytesToDownload)));
        } else {
          v.setName(R.string.DownloadUpdate);
        }
        break;
      }
      case AppUpdater.State.DOWNLOADING:
        v.setEnabledAnimated(false, isUpdate);
        v.setName(Lang.getDownloadProgress(updater.bytesDownloaded(), updater.totalBytesToDownload(), true));
        break;
      case AppUpdater.State.READY_TO_INSTALL:
        v.setEnabledAnimated(true, isUpdate);
        v.setName(R.string.InstallUpdate);
        break;
      case AppUpdater.State.NONE:
      case AppUpdater.State.INSTALLING:
      default:
        v.setEnabledAnimated(true, isUpdate);
        v.setName(R.string.CheckForUpdates);
        break;
    }
  }

  private void onUpdateCheckClick () {
    if (XgramUpdates.channel() == XgramUpdates.CHANNEL_OFF) {
      return;
    }
    AppUpdater updater = context().appUpdater();
    switch (updater.state()) {
      case AppUpdater.State.NONE:
        manualUpdateCheck = true;
        updater.checkForUpdatesNow();
        break;
      case AppUpdater.State.AVAILABLE:
        // XGram's update screen: the notes, the download and the install in one card
        if (!XgramUpdateScreen.show(this)) {
          updater.downloadUpdate();
        }
        break;
      case AppUpdater.State.READY_TO_INSTALL:
        if (!XgramUpdateScreen.install(this)) {
          updater.installUpdate();
        }
        break;
      default:
        break;
    }
  }

  private void showUpdateChannelOptions () {
    int[] channels = {XgramUpdates.CHANNEL_STABLE, XgramUpdates.CHANNEL_CI, XgramUpdates.CHANNEL_OFF};
    String[] titles = new String[channels.length];
    int[] ids = new int[channels.length];
    for (int i = 0; i < channels.length; i++) {
      titles[i] = Lang.getString(XgramUpdates.channelName(channels[i]));
      ids[i] = i + 1;
    }
    showOptions(Lang.getString(R.string.XgramUpdateChannelInfo), ids, titles, null, null, (itemView, optionId) -> {
      int channel = channels[optionId - 1];
      if (channel != XgramUpdates.channel()) {
        XgramUpdates.setChannel(channel);
        AppUpdater updater = context().appUpdater();
        updater.resetXgramUpdate();
        if (channel != XgramUpdates.CHANNEL_OFF && updater.state() == AppUpdater.State.NONE) {
          manualUpdateCheck = true;
          updater.checkForUpdatesNow();
        }
        updateUpdateRows();
      }
      return true;
    });
  }

  private void showUpdateModeOptions () {
    if (XgramUpdates.channel() == XgramUpdates.CHANNEL_OFF) {
      return;
    }
    int[] modes = {Settings.AUTO_UPDATE_MODE_PROMPT, Settings.AUTO_UPDATE_MODE_WIFI_ONLY, Settings.AUTO_UPDATE_MODE_ALWAYS, Settings.AUTO_UPDATE_MODE_NEVER};
    String[] titles = new String[modes.length];
    int[] ids = new int[modes.length];
    for (int i = 0; i < modes.length; i++) {
      titles[i] = Lang.getString(XgramUpdates.modeName(modes[i]));
      ids[i] = i + 1;
    }
    showOptions(null, ids, titles, null, null, (itemView, optionId) -> {
      Settings.instance().setAutoUpdateMode(modes[optionId - 1]);
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateMode);
      return true;
    });
  }

  private void updateUpdateRows () {
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateChannel);
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateMode);
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateCheck);
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateInstalled);
    }
  }

  @Override
  public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) {
    if (isDestroyed()) {
      return;
    }
    if (manualUpdateCheck && oldState == AppUpdater.State.CHECKING) {
      manualUpdateCheck = false;
      if (state == AppUpdater.State.NONE) {
        // Not a toast: the card says it's up to date (or why the check failed) and shows what's in this build
        com.fahadali.xgram.update.XgramUpdateScreen.upToDate(this, XgramUpdates.lastError());
      }
    }
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateCheck);
      adapter.updateValuedSettingById(R.id.btn_xgramAboutVersion);
    }
  }

  @Override
  public void onAppUpdateDownloadProgress (long bytesDownloaded, long totalBytesToDownload) {
    if (adapter != null && !isDestroyed()) {
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateCheck);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    context().appUpdater().removeListener(this);
  }

  private void updateGhostToggles () {
    adapter.updateValuedSettingById(R.id.btn_xgramGhostMode);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostDontRead);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostReadOnSend);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostLocalRead);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostDontType);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostOffline);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostTypingForStickers);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostScheduleSend);
    adapter.updateValuedSettingById(R.id.btn_xgramGhostSilent);
  }

  private static boolean isValueRow (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
        return true;
    }
    return false;
  }

  /**
   * Every value and switch the page shows, again: a page further in, another screen (the side menu, Telegram X's own
   * pages, the Quick Settings tile) or the result of a search may have changed them. Covers search results too.
   */
  private void refreshValues () {
    List<ListItem> shown = adapter.getItems();
    for (int i = 0; i < shown.size(); i++) {
      ListItem item = shown.get(i);
      if (item.getId() != 0 && isValueRow(item.getViewType())) {
        adapter.updateValuedSettingByPosition(i);
      }
    }
    if (!showingResults) {
      updateTranslateProviderInfo();
      if (adapter.indexOfViewById(R.id.btn_xgramDownloadBoostInfo) != -1) {
        updateDownloadBoostInfo();
      }
      // A proxy may have been added on Telegram X's proxy page.
      updateDescription(R.id.btn_xgramPaneInfo, XgramSettingDeps.paneInfo());
      updateDescription(R.id.btn_xgramProxyRulesInfo, XgramSettingDeps.proxyRulesInfo());
      // Autoplay GIFs may have been changed on Telegram X's settings page.
      updateDescription(R.id.btn_xgramInlineVideoInfo, XgramSettingDeps.inlineVideoInfo());
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      refreshValues();
      if (highlightId != 0) {
        int id = highlightId;
        highlightId = 0;
        XgramSettingsSearch.flash(getRecyclerView(), adapter.indexOfViewById(id));
      }
    }
  }

  // Search

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getSearchHint () {
    return R.string.XgramSettingsSearchHint;
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    searchIndex = buildSearchIndex();
  }

  /** Every setting, Telegram X's too; XGram's rows read from this controller's own page lists. */
  private com.fahadali.xgram.search.SearchIndex buildSearchIndex () {
    return com.fahadali.xgram.search.SettingsSearchEngine.build(this, com.fahadali.xgram.search.SettingsSearch.ORIGIN_XGRAM, XgramComposeBridge.searchHits(this));
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    if (adapter == null) {
      return;
    }
    if (query.trim().isEmpty()) {
      showPage();
      return;
    }
    if (searchIndex == null) {
      searchIndex = buildSearchIndex();
    }
    showingResults = true;
    adapter.setItems(XgramSettingsSearch.buildHitResults(com.fahadali.xgram.search.SettingsSearchEngine.search(searchIndex, query)), false);
    scrollToTop();
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    showPage();
    searchIndex = null;
  }

  private void showPage () {
    if (showingResults && adapter != null) {
      showingResults = false;
      adapter.setItems(buildSectionItems(section()), false);
      scrollToTop();
    }
  }

  private void scrollToTop () {
    ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(0, 0);
  }

  /** A tap on a search result. Returns false to let onClick run the row's own action (the result's switch, or main-page rows). */
  private boolean onSearchResultClick (View v) {
    if (!showingResults || !(v.getTag() instanceof ListItem)) {
      return false;
    }
    ListItem item = (ListItem) v.getTag();
    if (item.getData() instanceof com.fahadali.xgram.search.SearchEntry) {
      // A Telegram X setting: its page opens in this stack and points at the row (Night mode's switch flips here)
      com.fahadali.xgram.search.SearchEntry result = (com.fahadali.xgram.search.SearchEntry) item.getData();
      com.fahadali.xgram.search.InlineToggle toggle = result.getToggle();
      if (toggle != null && isSwitchTouch(v)) {
        adapter.toggleView(v);
        toggle.getToggle().invoke();
        return true;
      }
      preventLeavingSearchMode();
      com.fahadali.xgram.search.SettingsSearchOpen.open(this, result, true);
      return true;
    }
    if (!(item.getData() instanceof XgramSettingsSearch.Entry)) {
      return false;
    }
    XgramSettingsSearch.Entry entry = (XgramSettingsSearch.Entry) item.getData();
    if (entry.isToggle && !item.getBoolValue() && isSwitchTouch(v)) {
      return false; // flip it right here (getBoolValue = locked by another switch)
    }
    XgramSettings.instance().addSearchRecent(XgramSettingsSearch.recentKey(entry));
    if (entry.subPage != 0) {
      // A row of a page further in (Side Menu, Message menu, ...): open that page and point at the row.
      preventLeavingSearchMode();
      RecyclerViewController<?> c = openSubPage(entry.subPage);
      navigateTo(c);
      XgramSettingsSearch.flashWhenShown(c, entry);
      return true;
    }
    if (entry.section == SECTION_MAIN) {
      preventLeavingSearchMode(); // come back to the results
      return false; // sections, What's new, About: same action as on the main page
    }
    if (entry.section == section()) {
      closeSearchMode(() -> {
        if (!isDestroyed()) {
          XgramSettingsSearch.flash(getRecyclerView(), adapter.indexOfViewById(entry.id));
        }
      });
      return true;
    }
    preventLeavingSearchMode();
    navigateTo(section(context, tdlib, entry.section, entry.id));
    return true;
  }

  private boolean isSwitchTouch (View v) {
    if (v != lastTouchView || !(v instanceof SettingView)) {
      return false;
    }
    TogglerView toggler = ((SettingView) v).getToggler();
    if (toggler == null) {
      return false;
    }
    int slop = Screen.dp(12f);
    return lastTouchX >= toggler.getLeft() - slop && lastTouchX <= toggler.getRight() + slop;
  }

  @Override
  public void onClick (View v) {
    if (onSearchResultClick(v)) { // settings search: opened the row's page
      return;
    }
    XgramSettings settings = XgramSettings.instance();
    @IdRes int id = v.getId();
    boolean wasOffline = settings.ghostOffline();
    if (id == R.id.btn_xgramGhostMode) {
      settings.setGhostModeEnabled(!settings.isGhostModeEnabled());
    } else if (id == R.id.btn_xgramGhostDontRead) {
      settings.setGhostDontRead(!settings.ghostDontRead());
    } else if (id == R.id.btn_xgramGhostReadOnSend) {
      if (!settings.ghostDontRead()) {
        return;
      }
      settings.setGhostReadOnSend(!settings.ghostReadOnSend());
    } else if (id == R.id.btn_xgramGhostLocalRead) {
      if (!settings.ghostDontRead()) {
        return;
      }
      settings.setGhostLocalRead(!settings.ghostLocalRead());
    } else if (id == R.id.btn_xgramGhostTypingForStickers) {
      settings.setGhostTypingForStickers(!settings.ghostTypingForStickers());
    } else if (id == R.id.btn_xgramGhostDontType) {
      settings.setGhostDontType(!settings.ghostDontType());
    } else if (id == R.id.btn_xgramGhostOffline) {
      settings.setGhostOffline(!settings.ghostOffline());
    } else if (id == R.id.btn_xgramGhostSilent) {
      if (!XgramSettingDeps.ghostSilentEnabled()) {
        return;
      }
      settings.setGhostSilent(!settings.ghostSilent());
    } else if (id == R.id.btn_xgramGhostScheduleSend) {
      if (!settings.ghostOffline()) {
        return;
      }
      settings.setGhostScheduleSend(!settings.ghostScheduleSend());
    } else if (id == R.id.btn_xgramHistorySaveDeleted) {
      settings.setHistorySaveDeleted(adapter.toggleView(v));
      adapter.updateValuedSettingById(R.id.btn_xgramHistorySaveMedia);
      adapter.updateValuedSettingById(R.id.btn_xgramHistoryRedDeleted);
      return;
    } else if (id == R.id.btn_xgramHistorySaveMedia) {
      if (settings.historySaveDeleted()) {
        settings.setHistorySaveMedia(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramHistorySaveEdits) {
      settings.setHistorySaveEdits(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramHistoryRedDeleted) {
      if (XgramSettingDeps.historyRedDeletedEnabled()) {
        settings.setHistoryRedDeleted(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramHideSponsored) {
      settings.setHideSponsored(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramShowPeerIds) {
      settings.setShowPeerIds(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramSaveLastSeen) {
      settings.setSaveLastSeen(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramShowSeconds) {
      settings.setShowSeconds(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramChatFabsAvatarShape) {
      settings.setChatFabsAvatarShape(adapter.toggleView(v));
      // Open chats (the other pane) redraw now; their buttons take the shape before they draw (CircleButton.draw)
      com.fahadali.xgram.XgramUi.invalidateAll(context);
      return;
    } else if (id == R.id.btn_xgramTapTimeDetails) {
      settings.setTapTimeForDetails(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTextSelectDrag) {
      settings.setTextSelectDrag(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramCleanLinks) {
      settings.setCleanLinks(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramFixLinkPreviews) {
      settings.setFixLinkPreviews(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramAllowScreenshots) {
      settings.setAllowScreenshots(adapter.toggleView(v));
      context.checkDisallowScreenshots();
      return;
    } else if (id == R.id.btn_xgramSaveProtected) {
      settings.setSaveProtected(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramHidePhone) {
      settings.setHidePhone(adapter.toggleView(v));
      if (context.getDrawer() != null) {
        context.getDrawer().xgramRefreshHeader(); // the side menu shows the @username instead
      }
      return;
    } else if (id == R.id.btn_xgramBlockContentCapture) {
      settings.setBlockContentCapture(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramLimitMessageWidth) {
      settings.setLimitMessageWidth(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramFoldHingeSplit) {
      if (!XgramSettingDeps.twoPaneOptionsEnabled()) {
        return;
      }
      settings.setFoldHingeSplit(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update();
      }
      return;
    } else if (id == R.id.btn_xgramFoldTabletop) {
      settings.setFoldTabletop(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramPaneSplitEvenly) {
      if (!XgramSettingDeps.twoPaneOptionsEnabled()) {
        return;
      }
      settings.setPaneSplitEvenly(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update();
      }
      updatePaneRows();
      return;
    } else if (id == R.id.btn_xgramPaneResizable) {
      if (!XgramSettingDeps.paneResizableEnabled()) {
        return;
      }
      settings.setPaneResizable(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update();
      }
      return;
    } else if (id == R.id.btn_xgramDragDrop) {
      settings.setDragDrop(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramOpenBeside) {
      settings.setOpenBeside(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramPreferIpv6) {
      settings.setPreferIpv6(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramDownloadBoost) {
      settings.setDownloadBoost(adapter.toggleView(v));
      updateDownloadBoostInfo();
      adapter.updateItem(adapter.indexOfViewById(R.id.btn_xgramDownloadBoostLevel)); // the slider follows the switch
      return;
    } else if (id == R.id.btn_xgramDeleteForEveryone) {
      settings.setDeleteForEveryoneByDefault(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramAiTools) {
      settings.setAiTools(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTrAlways) {
      settings.setTranslateAlways(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTrFallback) {
      if (!com.fahadali.xgram.translate.XgramTranslator.isThirdParty(settings.translateProvider())) {
        return; // Telegram itself has nothing to fall back to
      }
      settings.setTranslateFallback(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTrProvider) {
      showTranslateProviderOptions();
      return;
    } else if (id == R.id.btn_xgramTrTarget) {
      navigateTo(new com.fahadali.xgram.chats.XgramTranslateTargetController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramTrAutoAll) {
      settings.setTranslateAutoAll(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTrStyle) {
      navigateTo(new org.thunderdog.challegram.ui.SettingsLanguageTranslateController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramTapToRecord) {
      if (XgramSettingDeps.tapToRecordEnabled()) {
        settings.setTapToRecord(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramInlineVideo) {
      settings.setInlineVideo(adapter.toggleView(v));
      // Open chats are rebuilt too: whether a video message gets the player is decided when the message is made.
      com.fahadali.xgram.chats.XgramInlineVideo.postSettingChanged();
      adapter.updateValuedSettingById(R.id.btn_xgramInlineVideoStart);
      updateDescription(R.id.btn_xgramInlineVideoInfo, XgramSettingDeps.inlineVideoInfo());
      return;
    } else if (id == R.id.btn_xgramVideoAutoHide) {
      settings.setVideoAutoHide(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramInlineVideoStart) {
      if (!XgramSettingDeps.inlineVideoStartEnabled()) {
        return;
      }
      int[] modes = {XgramSettings.INLINE_VIDEO_START_AUTO, XgramSettings.INLINE_VIDEO_START_TAP};
      String[] titles = {Lang.getString(R.string.XgramInlineVideoStartAuto), Lang.getString(R.string.XgramInlineVideoStartTap)};
      showOptions(null, new int[] {1, 2}, titles, null, null, (itemView, optionId) -> {
        settings.setInlineVideoStart(modes[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramInlineVideoStart);
        updateDescription(R.id.btn_xgramInlineVideoInfo, XgramSettingDeps.inlineVideoInfo());
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramCheckInTimer || id == R.id.btn_xgramCheckInGrace) {
      final boolean timer = id == R.id.btn_xgramCheckInTimer;
      final int[] choices = timer ? XgramSettings.CHECK_IN_TIMERS : XgramSettings.CHECK_IN_GRACES;
      int[] optionIds = new int[choices.length];
      String[] titles = new String[choices.length];
      for (int i = 0; i < choices.length; i++) {
        optionIds[i] = i + 1;
        titles[i] = com.fahadali.xgram.checkin.XgramCheckInSheet.durationName(choices[i]);
      }
      // The row's line shows the value, so the alert delay's subtitle heads its choices.
      showOptions(timer ? null : Lang.getString(R.string.XgramCheckInGraceSub), optionIds, titles, null, null, (itemView, optionId) -> {
        if (timer) {
          settings.setCheckInTimer(choices[optionId - 1]);
        } else {
          settings.setCheckInGrace(choices[optionId - 1]);
        }
        adapter.updateValuedSettingById(timer ? R.id.btn_xgramCheckInTimer : R.id.btn_xgramCheckInGrace);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramCheckInRadius) {
      final int[] radii = XgramSettings.CHECK_IN_RADII;
      int[] optionIds = new int[radii.length];
      String[] titles = new String[radii.length];
      for (int i = 0; i < radii.length; i++) {
        optionIds[i] = i + 1;
        titles[i] = com.fahadali.xgram.checkin.XgramCheckInSheet.radiusName(radii[i]);
      }
      showOptions(null, optionIds, titles, null, null, (itemView, optionId) -> {
        settings.setCheckInRadius(radii[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramCheckInRadius);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramCheckInLocation) {
      settings.setCheckInLocation(adapter.toggleView(v));
      adapter.updateValuedSettingById(R.id.btn_xgramCheckInFollowUp); // follows the switch
      return;
    } else if (id == R.id.btn_xgramCheckInBattery) {
      settings.setCheckInBattery(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramCheckInFollowUp) {
      if (settings.checkInLocation()) {
        settings.setCheckInFollowUp(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramEnterToSend) {
      int[] modes = {XgramSettings.ENTER_TO_SEND_AUTO, XgramSettings.ENTER_TO_SEND_ON, XgramSettings.ENTER_TO_SEND_OFF};
      String[] titles = new String[modes.length];
      for (int i = 0; i < modes.length; i++) {
        titles[i] = com.fahadali.xgram.layout.XgramKeyboard.enterToSendName(modes[i]);
      }
      showOptions(null, new int[] {1, 2, 3}, titles, null, null, (itemView, optionId) -> {
        settings.setEnterToSend(modes[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramEnterToSend);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramUndoSend) {
      final int[] delays = XgramSettings.UNDO_SEND_SECONDS;
      String[] titles = new String[delays.length];
      int[] ids = new int[delays.length];
      for (int i = 0; i < delays.length; i++) {
        titles[i] = com.fahadali.xgram.chats.XgramUndoSend.delayName(delays[i]);
        ids[i] = i + 1;
      }
      showOptions(Lang.getString(R.string.XgramUndoSendChoiceInfo), ids, titles, null, null, (itemView, optionId) -> {
        settings.setUndoSendSeconds(delays[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramUndoSend);
        adapter.updateValuedSettingById(R.id.btn_xgramUndoSendBubbles); // locked while Undo send is off
        adapter.updateValuedSettingById(R.id.btn_xgramUndoSendNewest);
        adapter.updateValuedSettingById(R.id.btn_xgramUndoSendVoice);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramUndoSendBubbles) {
      if (XgramSettingDeps.undoSendOptionsEnabled()) {
        settings.setUndoSendBubbles(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramUndoSendNewest) {
      if (XgramSettingDeps.undoSendOptionsEnabled()) {
        settings.setUndoSendNewestOnly(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramUndoSendVoice) {
      if (XgramSettingDeps.undoSendVoiceEnabled()) {
        settings.setUndoSendVoice(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramInputButtons) {
      navigateTo(new com.fahadali.xgram.chats.XgramInputButtonsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramHeaderButtons) {
      navigateTo(new com.fahadali.xgram.chats.XgramHeaderButtonsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramMergeCommunities) {
      settings.setMergeCommunities(adapter.toggleView(v));
      com.fahadali.xgram.chats.XgramCommunities.notifyAllAccounts(); // the setting is shared by every account
      return;
    } else if (id == R.id.btn_xgramLaterRow) {
      com.fahadali.xgram.later.XgramLater.setShowRow(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramLaterRaise) {
      com.fahadali.xgram.later.XgramLater.setRaiseWoken(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramLaterWakeEarly) {
      com.fahadali.xgram.later.XgramLater.showWakeEarlyPicker(this, () -> adapter.updateValuedSettingById(R.id.btn_xgramLaterWakeEarly));
      return;
    } else if (id == R.id.btn_xgramLaterExact) {
      com.fahadali.xgram.later.XgramLater.openExactRow(this);
      return;
    } else if (id == R.id.btn_xgramLaterChannel) {
      com.fahadali.xgram.later.XgramLater.openReminderChannelSettings(context());
      return;
    } else if (id == R.id.btn_xgramHideBlocked) {
      settings.setHideBlocked(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramForwardSender) {
      // Forwarding: how the forward sheet starts (com.fahadali.xgram.chats.XgramForwardOptions)
      final int[] modes = com.fahadali.xgram.chats.XgramForwardOptions.DEFAULTS;
      String[] titles = new String[modes.length];
      int[] ids = new int[modes.length];
      for (int i = 0; i < modes.length; i++) {
        titles[i] = com.fahadali.xgram.chats.XgramForwardOptions.defaultName(modes[i]);
        ids[i] = i + 1;
      }
      showOptions(Lang.getString(R.string.XgramFwdSettingInfo), ids, titles, null, null, (itemView, optionId) -> {
        settings.setForwardSender(modes[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramForwardSender);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramSideMenu) {
      navigateTo(new XgramDrawerItemsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramProxyOffVpn || id == R.id.btn_xgramProxyOffWifi || id == R.id.btn_xgramProxyOffMobile) {
      if (!XgramSettingDeps.proxyRulesEnabled()) {
        return; // no proxy for the rules to turn off
      }
      if (id == R.id.btn_xgramProxyOffVpn) {
        settings.setProxyOffOnVpn(adapter.toggleView(v));
      } else if (id == R.id.btn_xgramProxyOffWifi) {
        settings.setProxyOffOnWifi(adapter.toggleView(v));
      } else {
        settings.setProxyOffOnMobile(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramHideFab) {
      // New chat button: at the side, with the navigation bar (Expressive design's, while it is on) or hidden
      final int[] modes = XgramSettingDeps.newChatButtonChoices();
      String[] titles = new String[modes.length];
      int[] ids = new int[modes.length];
      for (int i = 0; i < modes.length; i++) {
        titles[i] = Lang.getString(XgramSettingDeps.newChatButtonName(modes[i]));
        ids[i] = i + 1;
      }
      showOptions(null, ids, titles, null, null, (itemView, optionId) -> {
        XgramSettingDeps.setNewChatButton(modes[optionId - 1]);
        if (context.xgramTwoPane() != null) {
          context.xgramTwoPane().update(); // where the button goes; Hide reaches the chat list by itself
        }
        adapter.updateValuedSettingById(R.id.btn_xgramHideFab);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramConnectionStatusStart) {
      if (XgramSettingDeps.connectionStatusPositionEnabled()) {
        settings.setConnectionStatusAtStart(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramAllChatsLabel) {
      if (XgramSettingDeps.allChatsLabelEnabled(tdlib)) {
        settings.setAllChatsLabel(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramHideFolders) {
      settings.setHideFolders(tdlib.accountId(), adapter.toggleView(v));
      adapter.updateValuedSettingById(R.id.btn_xgramAllChatsLabel);
      adapter.updateValuedSettingById(R.id.btn_xgramHideAllChats);
      return;
    } else if (id == R.id.btn_xgramHideAllChats) {
      if (XgramSettingDeps.hideAllChatsEnabled(tdlib)) {
        settings.setHideAllChats(tdlib.accountId(), adapter.toggleView(v));
        adapter.updateValuedSettingById(R.id.btn_xgramAllChatsLabel);
      }
      return;
    } else if (id == R.id.btn_xgramChatLook) {
      org.thunderdog.challegram.ui.MessagesController c = new org.thunderdog.challegram.ui.MessagesController(context, tdlib);
      c.setArguments(new org.thunderdog.challegram.ui.MessagesController.Arguments(org.thunderdog.challegram.ui.MessagesController.PREVIEW_MODE_FONT_SIZE, null, null));
      navigateTo(c);
      return;
    } else if (id == R.id.btn_xgramEmojiSet) {
      // Telegram X's emoji set picker (with XGram's System set), the same as in Settings → Stickers and emoji.
      navigateTo(new org.thunderdog.challegram.ui.SettingsCloudEmojiController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramPullToNext) {
      settings.setPullToNext(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramActivityShow) {
      settings.setActivityOn(adapter.toggleView(v));
      updateActivityRows();
      return;
    } else if (id == R.id.btn_xgramActivityBar) {
      if (XgramSettingDeps.activityOptionsEnabled()) {
        showActivityBarOptions(); // off: the reason is under the title
      }
      return;
    } else if (id == R.id.btn_xgramActivityHidden) {
      navigateTo(new com.fahadali.xgram.activity.XgramActivityHiddenController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramActivityClearAll) {
      showOptions(Lang.getString(R.string.XgramActivityClearAllConfirm) + "\n\n" + Lang.getString(R.string.XgramActivityClearAllInfo),
        new int[] {R.id.btn_xgramActivityClearAllConfirm, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramActivityClearAllButton), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_xgramActivityClearAllConfirm) {
            com.fahadali.xgram.activity.XgramActivityStore.instance().clearAll(tdlib.id());
            org.thunderdog.challegram.tool.UI.showToast(Lang.getString(R.string.XgramActivityAllCleared), android.widget.Toast.LENGTH_SHORT);
          }
          return true;
        });
      return;
    } else if (id == R.id.btn_xgramActivityNavBar) {
      // Expressive design's floating bar: its Activity tab is chosen with the other tabs, on the Navigation bar page
      if (XgramSettingDeps.activityOptionsEnabled()) {
        navigateTo(com.fahadali.xgram.compose.XgramComposeController.create(context, tdlib, com.fahadali.xgram.compose.XgramPage.NAV_BAR));
      }
      return;
    } else if (id == R.id.btn_xgramActivityReactions || id == R.id.btn_xgramActivityMuted || id == R.id.btn_xgramActivityDiscussion ||
      id == R.id.btn_xgramActivityLookBack || id == R.id.btn_xgramActivityBadge) {
      if (!XgramSettingDeps.activityOptionsEnabled()) {
        return; // the reason is under the title
      }
      boolean value = adapter.toggleView(v);
      if (id == R.id.btn_xgramActivityReactions) {
        settings.setActivityReactions(value);
      } else if (id == R.id.btn_xgramActivityMuted) {
        settings.setActivityMuted(value);
      } else if (id == R.id.btn_xgramActivityDiscussion) {
        settings.setActivityDiscussion(value);
      } else if (id == R.id.btn_xgramActivityLookBack) {
        settings.setActivityLookBack(value);
      } else {
        settings.setActivityBadge(value);
      }
      return;
    } else if (id == R.id.btn_xgramR7ChannelReactions) {
      settings.setChannelReactions(adapter.toggleView(v)); // open chats follow (MessagesController listens)
      return;
    } else if (id == R.id.btn_xgramTwoPane) {
      settings.setTwoPaneLayout(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update();
      }
      updatePaneRows();
      return;
    } else if (id == R.id.btn_xgramNavRail) {
      if (com.fahadali.xgram.layout.XgramNavRail.settingReason() != 0) {
        return;
      }
      settings.setNavRail(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update(); // the rail comes or goes at once, and the chat list's header with it
      }
      return;
    } else if (id == R.id.btn_xgramBottomNav) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // Expressive design's floating bar has its own page (tabs, colors, profile, the side menu)
        navigateTo(com.fahadali.xgram.compose.XgramComposeController.create(context, tdlib, com.fahadali.xgram.compose.XgramPage.NAV_BAR));
        return;
      }
      settings.setBottomNav(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update(); // the bottom navigation bar comes or goes at once (the chat list hears it too)
      }
      return;
    } else if (id == R.id.btn_xgramExportSettings) {
      String json = XgramBackup.export();
      org.thunderdog.challegram.tool.UI.copyText(json, R.string.XgramSettingsCopied);
      org.thunderdog.challegram.tool.Intents.shareText(json);
      return;
    } else if (id == R.id.btn_xgramImportSettings) {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      CharSequence text = null;
      if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
        text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
      }
      int count = text != null ? XgramBackup.importJson(text.toString()) : -1;
      if (count < 0) {
        org.thunderdog.challegram.tool.UI.showToast(R.string.XgramImportFailed, android.widget.Toast.LENGTH_LONG);
      } else {
        org.thunderdog.challegram.tool.UI.showToast(R.string.XgramImportDone, android.widget.Toast.LENGTH_SHORT);
        org.thunderdog.challegram.tool.UI.post(() -> XgramApp.restart(context), 800);
      }
      return;
    } else if (id == R.id.btn_xgramAboutSupport) {
      tdlib.ui().openUrl(this, "https://paypal.me/fahadalijaved23", new org.thunderdog.challegram.telegram.TdlibUi.UrlOpenParameters().disableInstantView());
      return;
    } else if (id == R.id.btn_xgramAboutBasedOn) {
      tdlib.ui().openUrl(this, "https://github.com/TGX-Android/Telegram-X", new org.thunderdog.challegram.telegram.TdlibUi.UrlOpenParameters().disableInstantView());
      return;
    } else if (id == R.id.btn_xgramAboutVersion) {
      navigateTo(section(context, tdlib, SECTION_UPDATES)); // a long press copies the version (onLongClick)
      return;
    } else if (id == R.id.btn_xgramLogsShare) {
      XgramLogs.confirmAndShare(this);
      return;
    } else if (id == R.id.btn_xgramResetSettings) {
      showOptions(Lang.getString(R.string.XgramResetSettingsConfirm), new int[] {R.id.btn_xgramResetSettings, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramResetSettings), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_xgramResetSettings) {
            XgramBackup.reset();
            org.thunderdog.challegram.tool.UI.post(() -> XgramApp.restart(context), 300);
          }
          return true;
        });
      return;
    } else if (id == R.id.btn_xgramHistoryRetention) {
      int[] days = {7, 30, 365, 0};
      String[] titles = new String[days.length];
      int[] ids = new int[days.length];
      for (int i = 0; i < days.length; i++) {
        titles[i] = days[i] == 0 ? Lang.getString(R.string.XgramRetentionForever) : Lang.getString(R.string.XgramRetentionDays, days[i]);
        ids[i] = i + 1;
      }
      showOptions(Lang.getString(R.string.XgramHistoryRetentionInfo), ids, titles, null, null, (itemView, optionId) -> {
        settings.setHistoryRetentionDays(days[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramHistoryRetention);
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramMessageMenu) {
      navigateTo(new com.fahadali.xgram.chats.XgramMessageMenuController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramFilters) {
      navigateTo(com.fahadali.xgram.chats.XgramFiltersController.words(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramHiddenPeople) {
      navigateTo(com.fahadali.xgram.chats.XgramFiltersController.people(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramMaterialYou) {
      if (XgramSettingDeps.themesLocked()) {
        return; // Expressive design: always the Material You colors
      }
      adapter.toggleView(v);
      com.fahadali.xgram.appearance.XgramMaterialYou.toggle(this, () -> {
        if (!isDestroyed()) {
          adapter.updateValuedSettingById(R.id.btn_xgramMaterialYou);
          adapter.updateValuedSettingById(R.id.btn_xgramThemes);
        }
      });
      return;
    } else if (id == R.id.btn_xgramExpressive) {
      adapter.toggleView(v);
      com.fahadali.xgram.appearance.XgramExpressive.setEnabled(this, !com.fahadali.xgram.appearance.XgramExpressive.isEnabled());
      return;
    } else if (id == R.id.btn_xgramComposeTgx) {
      settings.setComposeTgxPages(adapter.toggleView(v)); // applies to Telegram X pages opened next
      return;
    } else if (id == R.id.btn_xgramChatListBigText) {
      if (com.fahadali.xgram.chats.XgramChatListMode.isBigTextEnabled()) {
        com.fahadali.xgram.chats.XgramChatListMode.setBigText(adapter.toggleView(v));
      }
      return;
    } else if (id == R.id.btn_xgramColorSource) {
      // Only with Expressive design, whose settings are the Compose pages: Appearance there, at this row
      if (com.fahadali.xgram.appearance.XgramColorSource.isAvailable()) {
        navigateTo(com.fahadali.xgram.compose.XgramComposeController.create(context, tdlib, com.fahadali.xgram.compose.XgramPage.APPEARANCE, R.id.btn_xgramColorSource));
      }
      return;
    } else if (id == R.id.btn_xgramThemes) {
      if (!XgramSettingDeps.themesLocked()) {
        navigateTo(new com.fahadali.xgram.appearance.XgramThemesController(context, tdlib));
      }
      return;
    } else if (id == R.id.btn_xgramAutoNightMode) {
      // Telegram X's night mode settings (Auto night mode) are on its Themes page.
      navigateTo(new org.thunderdog.challegram.ui.SettingsThemeController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramPureBlack) {
      if (!com.fahadali.xgram.appearance.XgramAmoled.isAvailable()) {
        return; // only with Expressive design
      }
      // Applies at once: the role colors are dropped and the app repaints (this list too).
      com.fahadali.xgram.appearance.XgramAmoled.setEnabled(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramSectionAppearance) {
      navigateTo(section(context, tdlib, SECTION_APPEARANCE));
      return;
    } else if (id == R.id.btn_xgramSectionChatList) {
      navigateTo(section(context, tdlib, SECTION_CHAT_LIST));
      return;
    } else if (id == R.id.btn_xgramSectionChats) {
      navigateTo(section(context, tdlib, SECTION_MESSAGES));
      return;
    } else if (id == R.id.btn_xgramSectionTranslation) {
      navigateTo(section(context, tdlib, SECTION_TRANSLATION));
      return;
    } else if (id == R.id.btn_xgramSectionActivity) {
      navigateTo(section(context, tdlib, SECTION_ACTIVITY));
      return;
    } else if (id == R.id.btn_xgramLargeScreens) {
      navigateTo(section(context, tdlib, SECTION_LARGE_SCREENS));
      return;
    } else if (id == R.id.btn_xgramSectionPrivacy) {
      navigateTo(section(context, tdlib, SECTION_PRIVACY));
      return;
    } else if (id == R.id.btn_xgramSectionFilters) {
      navigateTo(section(context, tdlib, SECTION_FILTERS));
      return;
    } else if (id == R.id.btn_xgramSectionHistory) {
      navigateTo(section(context, tdlib, SECTION_HISTORY));
      return;
    } else if (id == R.id.btn_xgramSectionNetwork) {
      navigateTo(section(context, tdlib, SECTION_NETWORK));
      return;
    } else if (id == R.id.btn_xgramSectionBackup) {
      navigateTo(section(context, tdlib, SECTION_BACKUP));
      return;
    } else if (id == R.id.btn_xgramSectionUpdates) {
      navigateTo(section(context, tdlib, SECTION_UPDATES));
      return;
    } else if (id == R.id.btn_xgramUpdateChannel) {
      showUpdateChannelOptions();
      return;
    } else if (id == R.id.btn_xgramUpdateMode) {
      showUpdateModeOptions();
      return;
    } else if (id == R.id.btn_xgramUpdateCheck) {
      onUpdateCheckClick();
      return;
    } else if (id == R.id.btn_xgramUpdateInstalled) {
      org.thunderdog.challegram.tool.UI.copyText(BuildConfig.VERSION_NAME, R.string.CopiedText);
      return;
    } else if (id == R.id.btn_xgramAppIcon) {
      navigateTo(new com.fahadali.xgram.appicons.XgramAppIconsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramNotificationIcon) {
      showNotificationIconOptions();
      return;
    } else if (id == R.id.btn_xgramFont) {
      com.fahadali.xgram.appearance.XgramFonts.showPicker(this); // OK with a new font restarts the app
      return;
    } else if (id == R.id.btn_xgramWhatsNew) {
      navigateTo(new XgramWhatsNewController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramIconPack) {
      navigateTo(new com.fahadali.xgram.appearance.XgramIconPacksController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramChatListView) {
      com.fahadali.xgram.chats.XgramChatListMode.showModePicker(this, () -> adapter.updateValuedSettingById(R.id.btn_xgramChatListView));
      return;
    } else if (id == R.id.btn_xgramChatLongPress) {
      if (XgramSettingDeps.chatLongPressEnabled()) { // off: the reason is under the title
        com.fahadali.xgram.chats.XgramChatLongPress.showModePicker(this, () -> adapter.updateValuedSettingById(R.id.btn_xgramChatLongPress));
      }
      return;
    } else if (id == R.id.btn_xgramChatActionsOrder) {
      navigateTo(new com.fahadali.xgram.chats.XgramChatActionsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramSwipeLeft || id == R.id.btn_xgramSwipeRight) {
      com.fahadali.xgram.chats.XgramSwipeActions.showPicker(this, id == R.id.btn_xgramSwipeRight, () -> adapter.updateValuedSettingById(id));
      return;
    } else if (id == R.id.btn_xgramAvatarCorners) {
      int[] values = XgramSettings.AVATAR_CORNERS;
      int[] ids = {R.id.btn_xgramAvatarCornersOption, R.id.btn_xgramAvatarCornersOption1, R.id.btn_xgramAvatarCornersOption2, R.id.btn_xgramAvatarCornersOption3, R.id.btn_xgramAvatarCornersOption4};
      String[] names = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        names[i] = avatarCornersName(values[i]);
      }
      showOptions(null, ids, names, (itemView, optionId) -> {
        int index = -1;
        for (int i = 0; i < ids.length; i++) {
          if (ids[i] == optionId) {
            index = i;
          }
        }
        if (index >= 0 && index < values.length) {
          settings.setAvatarCorners(values[index]);
          adapter.updateValuedSettingById(R.id.btn_xgramAvatarCorners);
          // Redraw every avatar on screen now (lists only redraw rows when scrolled otherwise).
          com.fahadali.xgram.XgramUi.invalidateAll(context);
        }
        return true;
      });
      return;
    } else if (id == R.id.btn_xgramHistoryView) {
      navigateTo(new com.fahadali.xgram.history.XgramHistoryController(context, tdlib));
      return;
    } else {
      return;
    }
    if (wasOffline != settings.ghostOffline()) {
      XgramGhost.applyOnlineState();
    }
    updateGhostToggles();
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_xgramAboutVersion && !showingResults) {
      org.thunderdog.challegram.tool.UI.copyText(Lang.getAppBuildAndVersion(tdlib), R.string.CopiedText);
      return true;
    }
    return false;
  }

  static String avatarCornersName (int percent) { // XGram: package, for XgramComposeBridge
    if (percent < 0) {
      return Lang.getString(R.string.XgramAvatarCornersTheme);
    } else if (percent >= 100) {
      return Lang.getString(R.string.XgramAvatarCornersCircle);
    } else if (percent >= 50) {
      return Lang.getString(R.string.XgramAvatarCornersRounded);
    } else if (percent >= 30) {
      return Lang.getString(R.string.XgramAvatarCornersSquircle);
    } else {
      return Lang.getString(R.string.XgramAvatarCornersSquare);
    }
  }
}
