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
import com.fahadali.xgram.update.XgramUpdates;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
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
 */
public final class XgramSettingsController extends RecyclerViewController<Integer> implements View.OnClickListener, AppUpdater.Listener {
  public static final int SECTION_MAIN = 0, SECTION_APPEARANCE = 1, SECTION_GHOST = 2, SECTION_HISTORY = 3,
    SECTION_CHATS = 4, SECTION_PRIVACY = 5, SECTION_NETWORK = 6, SECTION_BACKUP = 7, SECTION_UPDATES = 8;
  /** Highest section. The settings search indexes SECTION_MAIN..SECTION_LAST, so raise this when adding a section. */
  static final int SECTION_LAST = SECTION_UPDATES;

  private SettingsAdapter adapter;

  // Settings search
  private @IdRes int highlightId; // row to scroll to and flash once shown
  private List<XgramSettingsSearch.Entry> searchIndex;
  private boolean showingResults;
  private View lastTouchView; // where a result row was last touched (switch vs. row)
  private float lastTouchX;

  private boolean manualUpdateCheck; // Updates: show the result of "Check for updates"

  public XgramSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static XgramSettingsController section (Context context, Tdlib tdlib, int section) {
    XgramSettingsController c = new XgramSettingsController(context, tdlib);
    c.setArguments(section);
    return c;
  }

  /** A section page that scrolls to one row and flashes it once shown (settings search). */
  static XgramSettingsController section (Context context, Tdlib tdlib, int section, @IdRes int highlightId) {
    XgramSettingsController c = section(context, tdlib, section);
    c.highlightId = highlightId;
    return c;
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
      case SECTION_GHOST: return R.string.XgramGhostMode;
      case SECTION_HISTORY: return R.string.XgramHistory;
      case SECTION_CHATS: return R.string.XgramChats;
      case SECTION_PRIVACY: return R.string.XgramPrivacy;
      case SECTION_NETWORK: return R.string.XgramNetwork;
      case SECTION_BACKUP: return R.string.XgramBackupAndReset;
      case SECTION_UPDATES: return R.string.XgramUpdates;
      default: return R.string.XgramSettings;
    }
  }

  /** Same icons as the main page's category rows; search results without their own icon use them. */
  static @DrawableRes int sectionIcon (int section) {
    switch (section) {
      case SECTION_APPEARANCE: return R.drawable.baseline_palette_24;
      case SECTION_GHOST: return R.drawable.baseline_ghost_24;
      case SECTION_HISTORY: return R.drawable.baseline_history_24;
      case SECTION_CHATS: return R.drawable.baseline_chat_bubble_24;
      case SECTION_PRIVACY: return R.drawable.baseline_lock_24;
      case SECTION_NETWORK: return R.drawable.baseline_public_24;
      case SECTION_BACKUP: return R.drawable.baseline_import_export_24;
      case SECTION_UPDATES: return R.drawable.baseline_system_update_24;
      default: return R.drawable.baseline_settings_24;
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        XgramSettings settings = XgramSettings.instance();
        @IdRes int id = item.getId();
        if (id == R.id.btn_xgramSectionGhost) {
          view.setData(settings.isAnyGhostEnabled() ? R.string.XgramStateOn : R.string.XgramStateOff);
        } else if (id == R.id.btn_xgramSectionHistory) {
          view.setData(settings.historySaveDeleted() || settings.historySaveEdits() ? R.string.XgramHistorySummaryOn : R.string.XgramStateOff);
        } else if (id == R.id.btn_xgramSectionAppearance) {
          view.setData(R.string.XgramAppearanceSummary);
        } else if (id == R.id.btn_xgramSectionChats) {
          view.setData(R.string.XgramChatsSummary);
        } else if (id == R.id.btn_xgramSectionPrivacy) {
          view.setData(R.string.XgramPrivacySummary);
        } else if (id == R.id.btn_xgramSectionNetwork) {
          view.setData(R.string.XgramNetworkSummary);
        } else if (id == R.id.btn_xgramSectionBackup) {
          view.setData(R.string.XgramBackupSummary);
        } else if (id == R.id.btn_xgramSectionUpdates || id == R.id.btn_xgramUpdateChannel) {
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
          view.setData(R.string.XgramThemesSummary);
        } else if (id == R.id.btn_xgramMaterialYou) {
          view.getToggler().setRadioEnabled(com.fahadali.xgram.appearance.XgramMaterialYou.isEnabled(), isUpdate);
        } else if (id == R.id.btn_xgramHistoryRetention) {
          int days = settings.historyRetentionDays();
          view.setData(days == 0 ? Lang.getString(R.string.XgramRetentionForever) : Lang.getString(R.string.XgramRetentionDays, days));
        } else if (id == R.id.btn_xgramAppIcon) {
          view.setData(Lang.getString(com.fahadali.xgram.appicons.XgramAppIcons.current(context).nameRes));
        } else if (id == R.id.btn_xgramAboutVersion) {
          view.setData(Lang.getAppBuildAndVersion(tdlib));
        } else if (id == R.id.btn_xgramAboutBasedOn) {
          view.setData(R.string.XgramAboutBasedOnInfo);
        } else if (id == R.id.btn_xgramAboutSupport) {
          view.setData(R.string.XgramAboutSupportInfo);
        } else if (id == R.id.btn_xgramAvatarCorners) {
          view.setData(avatarCornersName(settings.avatarCorners()));
        } else if (id == R.id.btn_xgramChatLongPress) {
          view.setData(com.fahadali.xgram.chats.XgramChatLongPress.modeName(settings.chatLongPress()));
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
        } else if (id == R.id.btn_xgramHideSponsored) {
          view.getToggler().setRadioEnabled(settings.hideSponsored(), isUpdate);
        } else if (id == R.id.btn_xgramShowPeerIds) {
          view.getToggler().setRadioEnabled(settings.showPeerIds(), isUpdate);
        } else if (id == R.id.btn_xgramLimitMessageWidth) {
          view.getToggler().setRadioEnabled(settings.limitMessageWidth(), isUpdate);
        } else if (id == R.id.btn_xgramTwoPane) {
          view.getToggler().setRadioEnabled(settings.twoPaneLayout(), isUpdate);
        } else if (id == R.id.btn_xgramHideFab) {
          view.getToggler().setRadioEnabled(settings.hideFab(), isUpdate);
        } else if (id == R.id.btn_xgramConnectionStatusStart) {
          view.getToggler().setRadioEnabled(settings.connectionStatusAtStart(), isUpdate);
        } else if (id == R.id.btn_xgramAllChatsLabel) {
          view.getToggler().setRadioEnabled(settings.allChatsLabel(), isUpdate);
        } else if (id == R.id.btn_xgramHideFolders) {
          view.getToggler().setRadioEnabled(settings.hideFolders(tdlib.accountId()), isUpdate);
        } else if (id == R.id.btn_xgramPullToNext) {
          view.getToggler().setRadioEnabled(settings.pullToNext(), isUpdate);
        } else if (id == R.id.btn_xgramSaveLastSeen) {
          view.getToggler().setRadioEnabled(settings.saveLastSeen(), isUpdate);
        } else if (id == R.id.btn_xgramShowSeconds) {
          view.getToggler().setRadioEnabled(settings.showSeconds(), isUpdate);
        } else if (id == R.id.btn_xgramTapTimeDetails) {
          view.getToggler().setRadioEnabled(settings.tapTimeForDetails(), isUpdate);
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
        } else if (id == R.id.btn_xgramForwardWithoutAuthor) {
          view.getToggler().setRadioEnabled(settings.forwardWithoutAuthor(), isUpdate);
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
        } else if (id == R.id.btn_xgramTrProvider) {
          view.setData(com.fahadali.xgram.translate.XgramTranslator.name(settings.translateProvider()));
        } else if (id == R.id.btn_xgramTrTarget) {
          view.setData(com.fahadali.xgram.chats.XgramTranslateTargetController.currentName());
        } else if (id == R.id.btn_xgramTrAutoAll) {
          view.getToggler().setRadioEnabled(settings.translateAutoAll(), isUpdate);
        } else if (id == R.id.btn_xgramTapToRecord) {
          view.getToggler().setRadioEnabled(settings.tapToRecord(), isUpdate);
        } else if (id == R.id.btn_xgramProxyOffVpn) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnVpn(), isUpdate);
        } else if (id == R.id.btn_xgramProxyOffWifi) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnWifi(), isUpdate);
        } else if (id == R.id.btn_xgramProxyOffMobile) {
          view.getToggler().setRadioEnabled(settings.proxyOffOnMobile(), isUpdate);
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

    adapter.setItems(buildSectionItems(section()), false);
    if (section() == SECTION_UPDATES) {
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
      case SECTION_GHOST: buildGhost(items); break;
      case SECTION_HISTORY: buildHistory(items); break;
      case SECTION_CHATS: buildChats(items); break;
      case SECTION_PRIVACY: buildPrivacy(items); break;
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

  private static ListItem toggle (@IdRes int id, int title) {
    return new ListItem(ListItem.TYPE_RADIO_SETTING, id, 0, title);
  }

  private static ListItem category (@IdRes int id, int icon, int title) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, id, icon, title);
  }

  private void buildMain (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramWhatsNew, R.drawable.baseline_whatshot_24, R.string.XgramWhatsNew));
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      category(R.id.btn_xgramSectionAppearance, R.drawable.baseline_palette_24, R.string.XgramAppearance),
      category(R.id.btn_xgramSectionGhost, R.drawable.baseline_ghost_24, R.string.XgramGhostMode),
      category(R.id.btn_xgramSectionHistory, R.drawable.baseline_history_24, R.string.XgramHistory),
      category(R.id.btn_xgramSectionChats, R.drawable.baseline_chat_bubble_24, R.string.XgramChats),
      category(R.id.btn_xgramSectionPrivacy, R.drawable.baseline_lock_24, R.string.XgramPrivacy),
      category(R.id.btn_xgramSectionNetwork, R.drawable.baseline_public_24, R.string.XgramNetwork),
      category(R.id.btn_xgramSectionUpdates, R.drawable.baseline_system_update_24, R.string.XgramUpdates),
      category(R.id.btn_xgramSectionBackup, R.drawable.baseline_import_export_24, R.string.XgramBackupAndReset)
    );
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramAbout));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutVersion, R.drawable.baseline_info_24, R.string.XgramAboutVersion),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutBasedOn, R.drawable.baseline_code_24, R.string.XgramAboutBasedOn),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAboutSupport, R.drawable.baseline_favorite_24, R.string.XgramAboutSupport)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramAboutCredits));
  }

  private void buildAppearance (List<ListItem> items) {
    boolean materialYou = com.fahadali.xgram.appearance.XgramMaterialYou.isAvailable();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    List<ListItem> rows = new ArrayList<>();
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramThemes, 0, R.string.XgramThemes));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAppIcon, 0, R.string.XgramAppIcon));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramIconPack, 0, R.string.XgramIconPack));
    if (materialYou) {
      rows.add(toggle(R.id.btn_xgramMaterialYou, R.string.XgramMaterialYou));
    }
    rows.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramChatLook, 0, R.string.XgramChatLookRow));
    rows.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramAvatarCorners, 0, R.string.XgramAvatarCorners));
    rows.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramSideMenu, 0, R.string.XgramSideMenu));
    addRows(items, rows.toArray(new ListItem[0]));
    if (materialYou) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramMaterialYouInfo));
    }
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramLayout));
    addRows(items,
      toggle(R.id.btn_xgramTwoPane, R.string.XgramTwoPane),
      toggle(R.id.btn_xgramLimitMessageWidth, R.string.XgramLimitMessageWidth),
      toggle(R.id.btn_xgramShowSeconds, R.string.XgramShowSeconds),
      toggle(R.id.btn_xgramHideFab, R.string.XgramHideFab),
      toggle(R.id.btn_xgramConnectionStatusStart, R.string.XgramConnectionStatusStart),
      toggle(R.id.btn_xgramAllChatsLabel, R.string.XgramAllChatsLabel),
      toggle(R.id.btn_xgramHideFolders, R.string.XgramHideFolders)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramTwoPaneInfo));
  }

  private void buildGhost (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items, toggle(R.id.btn_xgramGhostMode, R.string.XgramGhostModeToggle));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramGhostWhat));
    addRows(items,
      toggle(R.id.btn_xgramGhostDontRead, R.string.XgramGhostDontRead),
      toggle(R.id.btn_xgramGhostReadOnSend, R.string.XgramGhostReadOnSend),
      toggle(R.id.btn_xgramGhostLocalRead, R.string.XgramGhostLocalRead),
      toggle(R.id.btn_xgramGhostDontType, R.string.XgramGhostDontType),
      toggle(R.id.btn_xgramGhostTypingForStickers, R.string.XgramGhostTypingForStickers),
      toggle(R.id.btn_xgramGhostOffline, R.string.XgramGhostOffline),
      toggle(R.id.btn_xgramGhostScheduleSend, R.string.XgramGhostScheduleSend),
      toggle(R.id.btn_xgramGhostSilent, R.string.XgramGhostSilent)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramGhostModeInfo));
  }

  private void buildHistory (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      toggle(R.id.btn_xgramHistorySaveDeleted, R.string.XgramHistorySaveDeleted),
      toggle(R.id.btn_xgramHistorySaveMedia, R.string.XgramHistorySaveMedia),
      toggle(R.id.btn_xgramHistorySaveEdits, R.string.XgramHistorySaveEdits),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramHistoryRetention, 0, R.string.XgramHistoryRetention)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramHistorySettingsInfo));
    addRows(items, toggle(R.id.btn_xgramHistoryRedDeleted, R.string.XgramHistoryRedDeleted));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramHistoryRedDeletedInfo));
    addRows(items, new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramHistoryView, R.drawable.baseline_history_24, R.string.XgramHistoryView));
  }

  private void buildChats (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramBookmarks, R.drawable.baseline_star_24, R.string.XgramBookmarks),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramSavedByChat, R.drawable.baseline_bookmark_24, R.string.XgramSavedByChat),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramFilters, R.drawable.baseline_block_24, R.string.XgramFilters),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramMessageMenu, R.drawable.baseline_more_horiz_24, R.string.XgramMessageMenu)
    );
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramChatList));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramChatLongPress, 0, R.string.XgramChatLongPress),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramChatActionsOrder, 0, R.string.XgramChatActionsOrder),
      toggle(R.id.btn_xgramMergeCommunities, R.string.XgramMergeCommunities),
      toggle(R.id.btn_xgramHideBlocked, R.string.XgramHideBlocked),
      toggle(R.id.btn_xgramHideSponsored, R.string.XgramHideSponsored)
    );
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramMessages));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramSwipeLeft, 0, R.string.XgramSwipeLeft),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramSwipeRight, 0, R.string.XgramSwipeRight)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramSwipeInfo));
    addRows(items,
      toggle(R.id.btn_xgramDeleteForEveryone, R.string.XgramDeleteForEveryone),
      toggle(R.id.btn_xgramForwardWithoutAuthor, R.string.XgramForwardWithoutAuthor),
      toggle(R.id.btn_xgramCleanLinks, R.string.XgramCleanLinks),
      toggle(R.id.btn_xgramFixLinkPreviews, R.string.XgramFixLinkPreviews)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramFixLinkPreviewsInfo));
    addRows(items,
      toggle(R.id.btn_xgramTapTimeDetails, R.string.XgramTapTimeDetails),
      toggle(R.id.btn_xgramPullToNext, R.string.XgramPullToNext),
      toggle(R.id.btn_xgramAiTools, R.string.XgramAiTools)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramAiToolsInfo));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramTranslation));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramTrProvider, 0, R.string.XgramTrProvider),
      toggle(R.id.btn_xgramTrFallback, R.string.XgramTrFallback)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramTrProviderInfo, 0, com.fahadali.xgram.translate.XgramTranslator.info(XgramSettings.instance().translateProvider()), false));
    addRows(items,
      toggle(R.id.btn_xgramTrAlways, R.string.XgramTrAlways),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramTrStyle, 0, R.string.XgramTrStyle)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramTrAlwaysInfo));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramTrTarget, 0, R.string.XgramTrTarget),
      toggle(R.id.btn_xgramTrAutoAll, R.string.XgramTrAutoAll)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramTrAutoAllInfo));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramMessageBox));
    addRows(items,
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramInputButtons, 0, R.string.XgramInputButtons),
      toggle(R.id.btn_xgramTapToRecord, R.string.XgramTapToRecord)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramTapToRecordInfo));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramProfiles));
    addRows(items,
      toggle(R.id.btn_xgramShowPeerIds, R.string.XgramShowPeerIds),
      toggle(R.id.btn_xgramSaveLastSeen, R.string.XgramSaveLastSeen)
    );
  }

  private void buildPrivacy (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      toggle(R.id.btn_xgramAllowScreenshots, R.string.XgramAllowScreenshots),
      toggle(R.id.btn_xgramSaveProtected, R.string.XgramSaveProtected),
      toggle(R.id.btn_xgramHidePhone, R.string.XgramHidePhone),
      toggle(R.id.btn_xgramBlockContentCapture, R.string.XgramBlockContentCapture)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramPrivacyInfo));
  }

  private void buildNetwork (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramSpeed));
    String[] levels = new String[XgramDownloadBoost.LEVEL_NAMES.length];
    for (int i = 0; i < levels.length; i++) {
      levels[i] = Lang.getString(XgramDownloadBoost.LEVEL_NAMES[i]);
    }
    addRows(items,
      toggle(R.id.btn_xgramDownloadBoost, R.string.XgramDownloadBoost),
      new ListItem(ListItem.TYPE_SLIDER, R.id.btn_xgramDownloadBoostLevel, 0, R.string.XgramBoostStrength)
        .setSliderInfo(levels, XgramSettings.instance().downloadBoostLevel() - XgramDownloadBoost.LEVEL_LOW)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_xgramDownloadBoostInfo, 0, downloadBoostInfo(), false));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramProxyRules));
    addRows(items,
      toggle(R.id.btn_xgramProxyOffVpn, R.string.XgramProxyOffVpn),
      toggle(R.id.btn_xgramProxyOffWifi, R.string.XgramProxyOffWifi),
      toggle(R.id.btn_xgramProxyOffMobile, R.string.XgramProxyOffMobile)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramProxyRulesInfo));
    addRows(items, toggle(R.id.btn_xgramPreferIpv6, R.string.XgramPreferIpv6));
  }

  private static CharSequence downloadBoostInfo () {
    return Lang.getString(XgramDownloadBoost.description(XgramDownloadBoost.level())) + "\n\n" + Lang.getString(R.string.XgramDownloadBoostInfo);
  }

  /** Telegram, Google Translate or Yandex Translate; a third-party service asks once, saying the text goes to it. */
  private void showTranslateProviderOptions () {
    int[] providers = com.fahadali.xgram.translate.XgramTranslator.PROVIDERS;
    String[] titles = new String[providers.length];
    int[] ids = new int[providers.length];
    for (int i = 0; i < providers.length; i++) {
      titles[i] = com.fahadali.xgram.translate.XgramTranslator.name(providers[i]);
      ids[i] = i + 1;
    }
    showOptions(Lang.getString(R.string.XgramTrProvider), ids, titles, null, null, (itemView, optionId) -> {
      int provider = providers[optionId - 1];
      XgramSettings settings = XgramSettings.instance();
      if (provider == settings.translateProvider()) {
        return true;
      }
      if (com.fahadali.xgram.translate.XgramTranslator.isThirdParty(provider) && !settings.translateNoticeShown(provider)) {
        String name = com.fahadali.xgram.translate.XgramTranslator.name(provider);
        showConfirm(com.fahadali.xgram.translate.XgramTranslator.info(provider), Lang.getString(R.string.XgramTrUse, name), () -> {
          settings.setTranslateNoticeShown(provider);
          setTranslateProvider(provider);
        });
      } else {
        setTranslateProvider(provider);
      }
      return true;
    });
  }

  private void setTranslateProvider (int provider) {
    XgramSettings.instance().setTranslateProvider(provider);
    adapter.updateValuedSettingById(R.id.btn_xgramTrProvider);
    int index = adapter.indexOfViewById(R.id.btn_xgramTrProviderInfo);
    if (index != -1) {
      adapter.getItems().get(index).setString(com.fahadali.xgram.translate.XgramTranslator.info(provider));
      adapter.updateItem(index);
    }
  }

  private void updateDownloadBoostInfo () {
    int index = adapter.indexOfViewById(R.id.btn_xgramDownloadBoostInfo);
    if (index != -1) {
      adapter.getItems().get(index).setString(downloadBoostInfo());
      adapter.updateItem(index);
    }
  }

  private void buildBackup (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramExportSettings, R.drawable.baseline_share_24, R.string.XgramExportSettings),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramImportSettings, R.drawable.baseline_content_paste_24, R.string.XgramImportSettings)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramBackupInfo));
    addRows(items, new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramResetSettings, R.drawable.baseline_delete_24, R.string.XgramResetSettings).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
  }

  private void buildUpdates (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    addRows(items,
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramUpdateChannel, 0, R.string.XgramUpdateChannel),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramUpdateMode, 0, R.string.XgramUpdateMode)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramUpdateChannelInfo));
    addRows(items,
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramUpdateCheck, R.drawable.baseline_system_update_24, R.string.CheckForUpdates),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramUpdateInstalled, R.drawable.baseline_info_24, R.string.XgramUpdateInstalled)
    );
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramUpdateCheckInfo));
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
        if (updater.xgramUpdate() != null) {
          XgramUpdates.showWhatsNew(this, updater.xgramUpdate(), updater::downloadUpdate);
        } else {
          updater.downloadUpdate();
        }
        break;
      case AppUpdater.State.READY_TO_INSTALL:
        updater.installUpdate();
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
        String error = XgramUpdates.lastError();
        org.thunderdog.challegram.tool.UI.showToast(error != null ? Lang.getString(R.string.XgramUpdateCheckFailed, error) : Lang.getString(R.string.XgramUpdateLatest), android.widget.Toast.LENGTH_SHORT);
      }
    }
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_xgramUpdateCheck);
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

  /** Values that a page further in can change. */
  private static final int[] SUMMARY_IDS = {
    R.id.btn_xgramSectionGhost, R.id.btn_xgramSectionHistory, R.id.btn_xgramAppIcon, R.id.btn_xgramMaterialYou,
    R.id.btn_xgramSectionUpdates
  };

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

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      for (int id : SUMMARY_IDS) {
        adapter.updateValuedSettingById(id);
      }
      adapter.updateValuedSettingById(R.id.btn_xgramHidePhone); // also a toggle in Side menu
      adapter.updateValuedSettingById(R.id.btn_xgramTrTarget); // set on its own page
      if (showingResults) {
        // A page opened from the results may have changed what they show.
        List<ListItem> shown = adapter.getItems();
        for (int i = 0; i < shown.size(); i++) {
          if (shown.get(i).getData() instanceof XgramSettingsSearch.Entry) {
            adapter.updateValuedSettingByPosition(i);
          }
        }
      }
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
    searchIndex = XgramSettingsSearch.buildIndex(this);
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
      searchIndex = XgramSettingsSearch.buildIndex(this);
    }
    showingResults = true;
    adapter.setItems(XgramSettingsSearch.buildResults(searchIndex, query), false);
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
    if (!(item.getData() instanceof XgramSettingsSearch.Entry)) {
      return false;
    }
    XgramSettingsSearch.Entry entry = (XgramSettingsSearch.Entry) item.getData();
    if (entry.isToggle && !item.getBoolValue() && isSwitchTouch(v)) {
      return false; // flip it right here (getBoolValue = locked by another switch)
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
      settings.setGhostSilent(!settings.ghostSilent());
    } else if (id == R.id.btn_xgramGhostScheduleSend) {
      if (!settings.ghostOffline()) {
        return;
      }
      settings.setGhostScheduleSend(!settings.ghostScheduleSend());
    } else if (id == R.id.btn_xgramHistorySaveDeleted) {
      settings.setHistorySaveDeleted(adapter.toggleView(v));
      adapter.updateValuedSettingById(R.id.btn_xgramHistorySaveMedia);
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
      settings.setHistoryRedDeleted(adapter.toggleView(v));
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
    } else if (id == R.id.btn_xgramTapTimeDetails) {
      settings.setTapTimeForDetails(adapter.toggleView(v));
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
    } else if (id == R.id.btn_xgramPreferIpv6) {
      settings.setPreferIpv6(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramDownloadBoost) {
      settings.setDownloadBoost(adapter.toggleView(v));
      updateDownloadBoostInfo();
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
      settings.setTapToRecord(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramInputButtons) {
      navigateTo(new com.fahadali.xgram.chats.XgramInputButtonsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramMergeCommunities) {
      settings.setMergeCommunities(adapter.toggleView(v));
      com.fahadali.xgram.chats.XgramCommunities.notifyAllAccounts(); // the setting is shared by every account
      return;
    } else if (id == R.id.btn_xgramHideBlocked) {
      settings.setHideBlocked(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramForwardWithoutAuthor) {
      settings.setForwardWithoutAuthor(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramSideMenu) {
      navigateTo(new XgramDrawerItemsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramProxyOffVpn) {
      settings.setProxyOffOnVpn(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramProxyOffWifi) {
      settings.setProxyOffOnWifi(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramProxyOffMobile) {
      settings.setProxyOffOnMobile(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramHideFab) {
      settings.setHideFab(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramConnectionStatusStart) {
      settings.setConnectionStatusAtStart(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramAllChatsLabel) {
      settings.setAllChatsLabel(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramHideFolders) {
      settings.setHideFolders(tdlib.accountId(), adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramChatLook) {
      org.thunderdog.challegram.ui.MessagesController c = new org.thunderdog.challegram.ui.MessagesController(context, tdlib);
      c.setArguments(new org.thunderdog.challegram.ui.MessagesController.Arguments(org.thunderdog.challegram.ui.MessagesController.PREVIEW_MODE_FONT_SIZE, null, null));
      navigateTo(c);
      return;
    } else if (id == R.id.btn_xgramPullToNext) {
      settings.setPullToNext(adapter.toggleView(v));
      return;
    } else if (id == R.id.btn_xgramTwoPane) {
      settings.setTwoPaneLayout(adapter.toggleView(v));
      if (context.xgramTwoPane() != null) {
        context.xgramTwoPane().update();
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
      org.thunderdog.challegram.tool.UI.copyText(Lang.getAppBuildAndVersion(tdlib), R.string.CopiedText);
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
    } else if (id == R.id.btn_xgramBookmarks) {
      navigateTo(new com.fahadali.xgram.chats.XgramBookmarksController(context, tdlib));
    } else if (id == R.id.btn_xgramMessageMenu) {
      navigateTo(new com.fahadali.xgram.chats.XgramMessageMenuController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramSavedByChat) {
      navigateTo(new com.fahadali.xgram.chats.XgramSavedTopicsController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramFilters) {
      navigateTo(new com.fahadali.xgram.chats.XgramFiltersController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramMaterialYou) {
      adapter.toggleView(v);
      com.fahadali.xgram.appearance.XgramMaterialYou.toggle(this, () -> {
        if (!isDestroyed()) {
          adapter.updateValuedSettingById(R.id.btn_xgramMaterialYou);
        }
      });
      return;
    } else if (id == R.id.btn_xgramThemes) {
      navigateTo(new com.fahadali.xgram.appearance.XgramThemesController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramSectionAppearance) {
      navigateTo(section(context, tdlib, SECTION_APPEARANCE));
      return;
    } else if (id == R.id.btn_xgramSectionGhost) {
      navigateTo(section(context, tdlib, SECTION_GHOST));
      return;
    } else if (id == R.id.btn_xgramSectionHistory) {
      navigateTo(section(context, tdlib, SECTION_HISTORY));
      return;
    } else if (id == R.id.btn_xgramSectionChats) {
      navigateTo(section(context, tdlib, SECTION_CHATS));
      return;
    } else if (id == R.id.btn_xgramSectionPrivacy) {
      navigateTo(section(context, tdlib, SECTION_PRIVACY));
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
    } else if (id == R.id.btn_xgramWhatsNew) {
      navigateTo(new XgramWhatsNewController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramIconPack) {
      navigateTo(new com.fahadali.xgram.appearance.XgramIconPacksController(context, tdlib));
      return;
    } else if (id == R.id.btn_xgramChatLongPress) {
      com.fahadali.xgram.chats.XgramChatLongPress.showModePicker(this, () -> adapter.updateValuedSettingById(R.id.btn_xgramChatLongPress));
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
          // Redraw every screen with the new shape.
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

  private static String avatarCornersName (int percent) {
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
