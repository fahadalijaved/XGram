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
package com.fahadali.xgram.appearance;

import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.fahadali.xgram.chats.XgramChatMenu;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

/**
 * XGram: the leading icons and the destructive rows of the app bar's ⋮ menus when they open as compact popups
 * (Appearance › Menus › Compact popups, {@link XgramCompactMenu#showHeaderMenu}). Telegram X's ⋮ menus carry ids and
 * titles only, so each row's icon is looked up by its id: the icon the same option has elsewhere (the Chat menu
 * settings page, the side menu, the chat list's options, the message menu). Ids are global, so one id has one icon and
 * one colour in every ⋮. Main thread only (Lang).
 */
public final class XgramHeaderMenuIcons {
  private XgramHeaderMenuIcons () { }

  /** Pairs of a ⋮ item id and its icon, for the ⋮ menus outside the chat's (whose icons come from XgramChatMenu). */
  private static int[] table;

  private static int[] table () {
    if (table == null) {
      table = new int[] {
        // Profile (ProfileController.showMore)
        R.id.more_btn_edit, R.drawable.baseline_edit_24,
        R.id.more_btn_editDescription, R.drawable.baseline_description_24,
        R.id.more_btn_addToGroup, R.drawable.baseline_group_add_24,
        R.id.btn_xgramProfileSearch, R.drawable.baseline_search_24,
        R.id.btn_xgramVideoCall, R.drawable.baseline_videocam_24,
        R.id.btn_newSecretChat, R.drawable.baseline_lock_24,
        R.id.more_btn_share, R.drawable.baseline_share_arrow_24,
        R.id.more_btn_block, R.drawable.baseline_block_24,
        R.id.more_btn_unblock, R.drawable.baseline_block_24,
        R.id.more_btn_privacy, R.drawable.baseline_security_24,
        R.id.more_btn_delete, R.drawable.baseline_delete_24,
        R.id.more_btn_addToContacts, R.drawable.baseline_person_add_24,
        R.id.more_btn_addMember, R.drawable.baseline_person_add_24,
        R.id.more_btn_cloudChat, R.drawable.baseline_chat_bubble_24,
        R.id.more_btn_viewAdmins, R.drawable.baseline_stars_24,
        R.id.more_btn_viewStats, R.drawable.baseline_bar_chart_24,
        R.id.more_btn_report, R.drawable.baseline_report_24,
        R.id.btn_destroyChat, R.drawable.baseline_delete_forever_24,
        // A comments thread, a chat preview
        R.id.btn_manageGroup, R.drawable.baseline_settings_24,
        R.id.btn_deleteThread, R.drawable.baseline_delete_24,
        R.id.btn_chatFontSizeScale, R.drawable.baseline_format_text_24,
        R.id.btn_chatFontSizeReset, R.drawable.baseline_replay_24,
        R.id.btn_copyLink, R.drawable.baseline_link_24,
        R.id.btn_share, R.drawable.baseline_share_arrow_24,
        // Text, game, translation and theme editors
        R.id.btn_copyText, R.drawable.baseline_content_copy_24,
        R.id.btn_pasteText, R.drawable.baseline_content_paste_24,
        R.id.btn_openLink, R.drawable.baseline_open_in_browser_24,
        R.id.btn_close, R.drawable.baseline_close_24,
        R.id.btn_edit, R.drawable.baseline_edit_24,
        R.id.btn_wallpaper, R.drawable.baseline_image_24,
        R.id.btn_showAdvanced, R.drawable.baseline_tune_24,
        R.id.btn_color, R.drawable.baseline_palette_24,
        R.id.btn_delete, R.drawable.baseline_delete_24,
        // Settings pages
        R.id.more_btn_logout, R.drawable.baseline_logout_24,
        R.id.btn_resetLocalData, R.drawable.baseline_delete_forever_24,
        R.id.btn_resetNotifications, R.drawable.baseline_replay_24,
        R.id.btn_toggleErrors, R.drawable.baseline_error_24,
        R.id.btn_sortByPing, R.drawable.baseline_sort_24,
        R.id.btn_test, R.drawable.baseline_bug_report_24,
        // Music player
        R.id.btn_saveFile, R.drawable.baseline_file_download_24,
        R.id.btn_showInChat, R.drawable.baseline_visibility_24,
        R.id.btn_showInPlaylist, R.drawable.baseline_queue_music_24,
        R.id.btn_reverseOrder, R.drawable.baseline_sort_24,
        // Map layers
        R.id.btn_layerTypeMapDefault, R.drawable.baseline_map_24,
        R.id.btn_layerTypeMapDark, R.drawable.baseline_brightness_2_24,
        R.id.btn_layerTypeMapSatellite, R.drawable.baseline_public_24,
        R.id.btn_layerTypeMapTerrain, R.drawable.baseline_explore_24,
        R.id.btn_layerTypeMapHybrid, R.drawable.baseline_layers_24,
        // Media viewer
        R.id.btn_replace, R.drawable.dot_baseline_image_replace_24,
        R.id.btn_open, R.drawable.baseline_open_in_browser_24,
        R.id.btn_saveToGallery, R.drawable.baseline_file_download_24,
        R.id.btn_saveGif, R.drawable.deproko_baseline_gif_24,
        R.id.btn_messageReport, R.drawable.baseline_report_24,
        R.id.btn_setProfilePhoto, R.drawable.baseline_account_circle_24,
        R.id.btn_deleteProfilePhoto, R.drawable.baseline_delete_24,
        // Activity (the icons it has with Expressive design off)
        R.id.btn_xgramActivityUnreadOnly, R.drawable.baseline_mark_chat_unread_24,
        R.id.btn_xgramActivityChats, R.drawable.baseline_forum_24,
        R.id.btn_xgramActivityClearRead, R.drawable.baseline_delete_sweep_24,
        R.id.btn_xgramActivityHidden, R.drawable.baseline_eye_off_24,
        R.id.btn_xgramActivitySettings, R.drawable.baseline_settings_24,
        // Communities, forums, Later
        R.id.btn_xgramCommunitySettings, R.drawable.baseline_settings_24,
        R.id.btn_xgramCommunityMerge, R.drawable.templarian_baseline_source_merge_24,
        R.id.btn_xgramCreateTopic, R.drawable.baseline_add_24,
        R.id.btn_xgramReorderTopics, R.drawable.baseline_drag_handle_24,
        R.id.btn_xgramMarkAllTopicsRead, R.drawable.baseline_done_all_24,
        R.id.btn_xgramViewAsMessages, R.drawable.baseline_chat_bubble_24,
        R.id.btn_xgramLaterMMarkAllDone, R.drawable.baseline_done_all_24,
        R.id.btn_xgramLaterMClearDone, R.drawable.baseline_delete_sweep_24,
        R.id.btn_xgramLaterMSettings, R.drawable.baseline_settings_24,
        // The side menu's items in the main screen's ⋮ (DrawerController, XgramNavRail)
        R.id.btn_xgramFullScreenCalls, R.drawable.baseline_call_24,
        R.id.btn_xgramNavSwitchAccount, R.drawable.templarian_baseline_account_switch_24,
        R.id.btn_xgramNavManageAccounts, R.drawable.baseline_account_circle_24,
        R.id.btn_xgramActivity, R.drawable.outline_notifications_24,
        R.id.btn_contacts, R.drawable.baseline_perm_contact_calendar_24,
        R.id.btn_xgramHub, R.drawable.baseline_stars_24,
        R.id.btn_calls, R.drawable.baseline_call_24,
        R.id.btn_xgramBookmarks, R.drawable.baseline_star_border_24,
        R.id.btn_xgramLater, R.drawable.baseline_watch_later_24,
        R.id.btn_settings, R.drawable.baseline_settings_24,
        R.id.btn_invite, R.drawable.baseline_person_add_24,
        R.id.btn_proxy, R.drawable.baseline_security_24,
        R.id.btn_help, R.drawable.baseline_help_24,
        R.id.btn_night, R.drawable.baseline_brightness_2_24,
        R.id.btn_xgramExpressive, R.drawable.baseline_palette_24,
        R.id.btn_xgramGhostMode, R.drawable.baseline_ghost_24,
        R.id.btn_reportBug, R.drawable.baseline_bug_report_24,
        R.id.btn_featureToggles, R.drawable.outline_toggle_on_24,
        R.id.btn_tdlib_clearLogs, R.drawable.baseline_bug_report_24,
        R.id.btn_tdlib_shareLogs, R.drawable.baseline_bug_report_24
      };
    }
    return table;
  }

  /** The leading icon of the ⋮ item {@code id} titled {@code title}; 0: none known (the row keeps the others' alignment). */
  @DrawableRes
  public static int icon (int id, @Nullable CharSequence title) {
    // Items whose title says which of two actions they are
    if (id == R.id.btn_mute) {
      return is(title, R.string.Unmute) ? R.drawable.baseline_notifications_24 : R.drawable.baseline_notifications_off_24; // as the chat list's options
    }
    if (id == R.id.btn_removeChatFromList || id == R.id.btn_removeChatFromListAndStop) {
      return is(title, R.string.DeleteChat) || is(title, R.string.DeleteAndStop) ? R.drawable.baseline_delete_24 : R.drawable.baseline_exit_to_app_24;
    }
    if (id == R.id.btn_returnToChat) {
      return R.drawable.baseline_person_add_24; // Return to group, Join
    }
    if (id == R.id.btn_xgramChatMenuMore) {
      return R.drawable.baseline_more_horiz_24;
    }
    if (id == R.id.btn_savedMessages) {
      return com.fahadali.xgram.xdrive.XDrive.icon(R.drawable.baseline_bookmark_24); // a cloud with XDrive, as the side menu
    }
    if (id == R.id.btn_xgramSavedByChat) {
      return R.drawable.baseline_forum_24; // the side menu's (the Chat menu page has a bookmark)
    }
    // The chat's ⋮: the icons of Settings › XGram › Messages › Chat menu
    for (XgramChatMenu.Option option : XgramChatMenu.OPTIONS) {
      for (int optionId : option.ids) {
        if (optionId == id) {
          return option.icon;
        }
      }
    }
    final int[] table = table();
    for (int i = 0; i + 1 < table.length; i += 2) {
      if (table[i] == id) {
        return table[i + 1];
      }
    }
    return 0;
  }

  /**
   * Whether the ⋮ item {@code id} titled {@code title} is destructive (its label and icon in the error colour, as the
   * compact popups' red rows): leaving or deleting, reporting, blocking, logging out. Clear history, Return and Join
   * stay normal, as in Telegram X's chat options.
   */
  public static boolean isDestructive (int id, @Nullable CharSequence title) {
    if (id == R.id.more_btn_block) {
      return is(title, R.string.BlockContact) || is(title, R.string.BlockBot); // not Unblock
    }
    return id == R.id.btn_removeChatFromList ||
      id == R.id.btn_removeChatFromListAndStop ||
      id == R.id.btn_reportChat ||
      id == R.id.more_btn_report ||
      id == R.id.btn_messageReport ||
      id == R.id.btn_xgramDeleteMyMessages ||
      id == R.id.btn_deleteThread ||
      id == R.id.btn_destroyChat ||
      id == R.id.more_btn_delete ||
      id == R.id.btn_delete ||
      id == R.id.btn_deleteProfilePhoto ||
      id == R.id.more_btn_logout ||
      id == R.id.btn_resetLocalData ||
      id == R.id.btn_xgramFullScreenCalls; // calls can't ring full screen: the side menu's warning row
  }

  private static boolean is (@Nullable CharSequence title, int stringRes) {
    return title != null && TextUtils.equals(title, Lang.getString(stringRes));
  }
}
