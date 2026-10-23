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

import android.content.Context;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.fahadali.xgram.chats.XgramBookmarksController;
import com.fahadali.xgram.chats.XgramSavedTopicsController;
import com.fahadali.xgram.history.XgramHistoryController;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * What XGram adds to Telegram X, each row opening the feature (Settings → XGram → What's new). After an update the
 * welcome card shows the release's highlights instead (com.fahadali.xgram.update.XgramUpdateScreen.welcomeIfNeeded).
 */
public final class XgramWhatsNewController extends RecyclerViewController<Void> implements View.OnClickListener {
  public static final class Entry { // XGram: public, for the Compose What's new page
    public final int title, subtitle;
    public final int action;
    @DrawableRes private final int icon, expressiveIcon;

    Entry (@DrawableRes int icon, @StringRes int title, @StringRes int subtitle, int action) {
      this(icon, title, subtitle, action, 0);
    }

    /** {@code expressiveIcon}: another icon with Expressive design on (0: the same one). */
    Entry (@DrawableRes int icon, @StringRes int title, @StringRes int subtitle, int action, @DrawableRes int expressiveIcon) {
      this.icon = icon;
      this.title = title;
      this.subtitle = subtitle;
      this.action = action;
      this.expressiveIcon = expressiveIcon;
    }

    /**
     * Chosen when it is shown, not when the class loads: turning Expressive design on or off only recreates the
     * activity, and this array outlives it.
     */
    @DrawableRes
    public int icon () {
      return expressiveIcon != 0 && com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? expressiveIcon : icon;
    }
  }

  public static final int OPEN_NONE = 0, OPEN_SETTINGS = 1, OPEN_HISTORY = 2, OPEN_BOOKMARKS = 3, OPEN_SAVED_BY_CHAT = 4,
    OPEN_ICONS = 5, OPEN_FILTERS = 6, OPEN_GHOST = 8, OPEN_APPEARANCE = 9, OPEN_PRIVACY = 10, OPEN_THEMES = 11, OPEN_ACTIVITY = 12,
    OPEN_UNDO_SEND = 13, OPEN_CHECK_IN = 14, OPEN_LATER = 15;

  public static final Entry[] ENTRIES = {
    new Entry(R.drawable.baseline_notifications_24, R.string.XgramNewActivity, R.string.XgramNewActivityInfo, OPEN_ACTIVITY, R.drawable.outline_notifications_24),
    new Entry(R.drawable.baseline_undo_24, R.string.XgramUndoSend, R.string.XgramNewUndoSendInfo, OPEN_UNDO_SEND),
    new Entry(R.drawable.baseline_location_on_24, R.string.XgramCheckIn, R.string.XgramNewCheckInInfo, OPEN_CHECK_IN),
    new Entry(R.drawable.baseline_watch_later_24, R.string.XgramLater, R.string.XgramNewLaterInfo, OPEN_LATER),
    new Entry(R.drawable.baseline_palette_24, R.string.XgramNewThemes, R.string.XgramNewThemesInfo, OPEN_THEMES),
    new Entry(R.drawable.baseline_apps_24, R.string.XgramNewIconStyles, R.string.XgramNewIconStylesInfo, OPEN_ICONS),
    new Entry(R.drawable.baseline_settings_24, R.string.XgramNewSettings, R.string.XgramNewSettingsInfo, OPEN_SETTINGS),
    new Entry(R.drawable.baseline_ghost_24, R.string.XgramNewGhost, R.string.XgramNewGhostInfo, OPEN_GHOST),
    new Entry(R.drawable.baseline_forum_24, R.string.XgramNewTopics, R.string.XgramNewTopicsInfo, OPEN_NONE),
    new Entry(R.drawable.baseline_history_24, R.string.XgramNewHistory, R.string.XgramNewHistoryInfo, OPEN_HISTORY),
    new Entry(R.drawable.baseline_apps_24, R.string.XgramNewIcons, R.string.XgramNewIconsInfo, OPEN_ICONS),
    new Entry(R.drawable.baseline_star_24, R.string.XgramNewBookmarks, R.string.XgramNewBookmarksInfo, OPEN_BOOKMARKS, R.drawable.baseline_star_border_24),
    new Entry(R.drawable.baseline_bookmark_24, R.string.XgramNewSavedByChat, R.string.XgramNewSavedByChatInfo, OPEN_SAVED_BY_CHAT),
    new Entry(R.drawable.baseline_flash_on_24, R.string.XgramNewMessageTools, R.string.XgramNewMessageToolsInfo, OPEN_NONE),
    new Entry(R.drawable.baseline_block_24, R.string.XgramNewFilters, R.string.XgramNewFiltersInfo, OPEN_FILTERS),
    new Entry(R.drawable.baseline_palette_24, R.string.XgramNewLook, R.string.XgramNewLookInfo, OPEN_APPEARANCE),
    new Entry(R.drawable.baseline_devices_other_24, R.string.XgramNewTwoPane, R.string.XgramNewTwoPaneInfo, OPEN_NONE),
    new Entry(R.drawable.baseline_identifier_24, R.string.XgramNewProfiles, R.string.XgramNewProfilesInfo, OPEN_NONE),
    new Entry(R.drawable.baseline_lock_24, R.string.XgramNewPrivacy, R.string.XgramNewPrivacyInfo, OPEN_PRIVACY),
  };

  public XgramWhatsNewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramWhatsNew;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramWhatsNew);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof Entry) {
          view.setData(Lang.getString(((Entry) data).subtitle));
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramWhatsNewIntro));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (Entry entry : ENTRIES) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      first = false;
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_xgramWhatsNewItem, entry.icon(), entry.title).setData(entry));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramWhatsNewOutro));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (!(tag instanceof ListItem) || !(((ListItem) tag).getData() instanceof Entry)) {
      return;
    }
    switch (((Entry) ((ListItem) tag).getData()).action) {
      case OPEN_ACTIVITY:
        com.fahadali.xgram.activity.XgramActivity.open(this);
        break;
      case OPEN_UNDO_SEND:
        com.fahadali.xgram.update.XgramFeature.UNDO_SEND.open(this);
        break;
      case OPEN_CHECK_IN:
        com.fahadali.xgram.update.XgramFeature.CHECK_IN.open(this);
        break;
      case OPEN_LATER:
        com.fahadali.xgram.update.XgramFeature.LATER.open(this);
        break;
      case OPEN_SETTINGS:
        navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.main(context, tdlib));
        break;
      case OPEN_THEMES:
        navigateTo(new com.fahadali.xgram.appearance.XgramThemesController(context, tdlib));
        break;
      case OPEN_GHOST:
        navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.ghost(context, tdlib)); // Privacy, Ghost mode first
        break;
      case OPEN_APPEARANCE:
        navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_APPEARANCE));
        break;
      case OPEN_PRIVACY:
        navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_PRIVACY));
        break;
      case OPEN_HISTORY:
        navigateTo(new XgramHistoryController(context, tdlib));
        break;
      case OPEN_BOOKMARKS:
        navigateTo(new XgramBookmarksController(context, tdlib));
        break;
      case OPEN_SAVED_BY_CHAT:
        navigateTo(new XgramSavedTopicsController(context, tdlib));
        break;
      case OPEN_ICONS:
        navigateTo(new com.fahadali.xgram.appicons.XgramAppIconsController(context, tdlib));
        break;
      case OPEN_FILTERS:
        navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_FILTERS));
        break;
      default:
        break;
    }
  }
}
