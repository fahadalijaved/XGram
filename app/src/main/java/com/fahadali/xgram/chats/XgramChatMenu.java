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
package com.fahadali.xgram.chats;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramReorder;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The chat's ⋮ menu, trimmed to what the user wants, like the Message menu (XgramMessageMenu): each option shows, waits
 * under a "More…" item at the end, or is hidden, and the options keep the user's order. Settings → XGram → Messages →
 * Chat menu. An option still only appears where it applies (Leave group in groups, Recent Actions for admins, Show
 * topics as… in forums); the chat's thread menu (comments) is left as it is.
 */
public final class XgramChatMenu {
  private XgramChatMenu () { }

  public static final String KEY_HIDDEN = "xgram_chat_menu_hidden";
  /** The options the user put under More… (a comma separated list of keys, like {@link #KEY_HIDDEN}). */
  public static final String KEY_MORE = "xgram_chat_menu_more";

  public static final class Option {
    /** Stored when hidden or under More: stays the same across builds, unlike the ids. */
    public final String key;
    public final int[] ids;
    @StringRes public final int name;
    @DrawableRes public final int icon;

    Option (String key, @StringRes int name, @DrawableRes int icon, @IdRes int... ids) {
      this.key = key;
      this.name = name;
      this.icon = icon;
      this.ids = ids;
    }
  }

  /** In the order the menu adds them (the default order of the settings list). */
  public static final Option[] OPTIONS = {
    new Option("search", R.string.Search, R.drawable.baseline_search_24, R.id.btn_search),
    new Option("scheduled", R.string.ScheduledMessages, R.drawable.baseline_date_range_24, R.id.btn_viewScheduled),
    new Option("topics_layout", R.string.XgramTopicsLayoutMenu, R.drawable.baseline_view_list_24, R.id.btn_xgramTopicsLayout),
    new Option("view_as_topics", R.string.XgramViewAsTopics, R.drawable.baseline_forum_24, R.id.btn_xgramViewAsTopics),
    new Option("community", R.string.XgramOpenCommunity, R.drawable.baseline_group_24, R.id.btn_xgramOpenCommunity),
    new Option("mute", R.string.XgramChatMenuMute, R.drawable.baseline_notifications_off_24, R.id.btn_mute),
    new Option("translate", R.string.XgramTranslateChat, R.drawable.baseline_translate_24, R.id.btn_xgramAutoTranslate),
    new Option("history", R.string.XgramChatHistory, R.drawable.baseline_history_24, R.id.btn_xgramChatHistory),
    new Option("xdrive", R.string.XgramXDriveOpenXDrive, R.drawable.baseline_cloud_24, R.id.btn_xgramOpenXDrive),
    new Option("saved_by_chat", R.string.XgramSavedByChat, R.drawable.baseline_bookmark_24, R.id.btn_xgramSavedByChat),
    new Option("add_to_folder", R.string.AddToFolder, R.drawable.baseline_create_new_folder_24, R.id.btn_xgramAddToFolder),
    new Option("later", R.string.XgramLaterSnoozeChat, R.drawable.baseline_watch_later_24, R.id.btn_xgramLaterSnoozeChat, R.id.btn_xgramLaterUnsnooze),
    new Option("rename", R.string.XgramRenameLocally, R.drawable.baseline_edit_24, R.id.btn_xgramRenameLocally),
    new Option("first_message", R.string.XgramGoToFirstMessage, R.drawable.baseline_arrow_upward_24, R.id.btn_xgramGoToFirstMessage),
    new Option("export", R.string.XgramExportChat, R.drawable.baseline_file_download_24, R.id.btn_xgramExportChat),
    new Option("recent_actions", R.string.EventLog, R.drawable.baseline_assignment_24, R.id.btn_xgramRecentActions),
    new Option("delete_my_messages", R.string.XgramDeleteMyMessages, R.drawable.baseline_delete_sweep_24, R.id.btn_xgramDeleteMyMessages),
    new Option("ghost", R.string.XgramGhostExcludeChat, R.drawable.baseline_ghost_24, R.id.btn_xgramGhostExclude),
    new Option("undo_send", R.string.XgramUndoSendExclude, R.drawable.baseline_undo_24, R.id.btn_xgramUndoSendExclude),
    new Option("check_in", R.string.XgramCheckIn, R.drawable.baseline_location_on_24, R.id.btn_xgramCheckIn),
    new Option("report", R.string.Report, R.drawable.baseline_report_24, R.id.btn_reportChat),
    new Option("passcode", R.string.PasscodeTitle, R.drawable.baseline_lock_24, R.id.btn_setPasscode),
    new Option("home_screen", R.string.XgramHomeScreen, R.drawable.baseline_home_24, R.id.btn_xgramAddToHomeScreen),
    new Option("share_contact", R.string.ShareMyContactInfo, R.drawable.baseline_contact_phone_24, R.id.btn_shareMyContact),
    new Option("pinned", R.string.PinnedMessage, R.drawable.deproko_baseline_pin_24, R.id.btn_showPinnedMessage),
    new Option("bot_help", R.string.BotHelp, R.drawable.baseline_help_24, R.id.btn_botHelp),
    new Option("bot_settings", R.string.BotSettings, R.drawable.baseline_settings_24, R.id.btn_botSettings),
    new Option("linked_chat", R.string.XgramChatMenuLinked, R.drawable.baseline_link_24, R.id.btn_openLinkedChat),
    new Option("direct_messages", R.string.DirectMessages, R.drawable.baseline_chat_bubble_24, R.id.btn_openDirectMessages),
    new Option("clear_history", R.string.ClearHistory, R.drawable.templarian_baseline_broom_24, R.id.btn_clearChatHistory),
    new Option("leave", R.string.XgramChatMenuLeave, R.drawable.baseline_exit_to_app_24, R.id.btn_removeChatFromList, R.id.btn_removeChatFromListAndStop, R.id.btn_returnToChat)
  };

  /** Whether the option exists in this build and setup (Open XDrive: only while XDrive's test is on). */
  public static boolean isAvailable (String key) {
    return !"xdrive".equals(key) || com.fahadali.xgram.xdrive.XDrive.on();
  }

  private static volatile Set<String> hidden, more;

  private static Set<String> readSet (String settingKey) {
    Set<String> set = new HashSet<>();
    for (String key : Settings.instance().getString(settingKey, "").split(",")) {
      if (!key.isEmpty()) {
        set.add(key);
      }
    }
    return set;
  }

  private static void writeSet (String settingKey, Set<String> set) {
    if (set.isEmpty()) {
      Settings.instance().remove(settingKey);
    } else {
      Settings.instance().putString(settingKey, String.join(",", set));
    }
  }

  private static Set<String> hidden () {
    if (hidden == null) {
      hidden = readSet(KEY_HIDDEN);
    }
    return hidden;
  }

  private static Set<String> more () {
    if (more == null) {
      more = readSet(KEY_MORE);
    }
    return more;
  }

  public static boolean isHidden (String key) {
    return hidden().contains(key);
  }

  /** Hidden drops the option's place under More: shown again, it comes back in the menu itself. */
  public static void setHidden (String key, boolean isHidden) {
    Set<String> set = new HashSet<>(hidden());
    if (isHidden) {
      set.add(key);
    } else {
      set.remove(key);
    }
    hidden = set;
    writeSet(KEY_HIDDEN, set);
    if (isHidden) {
      setUnderMore(key, false);
    }
  }

  public static boolean isUnderMore (String key) {
    return more().contains(key) && !isHidden(key);
  }

  public static void setUnderMore (String key, boolean underMore) {
    Set<String> set = new HashSet<>(more());
    if (underMore) {
      set.add(key);
    } else {
      set.remove(key);
    }
    more = set;
    writeSet(KEY_MORE, set);
  }

  /** Options hidden, of those the settings page lists. */
  public static int hiddenCount () {
    int count = 0;
    for (Option option : OPTIONS) {
      if (isAvailable(option.key) && isHidden(option.key)) {
        count++;
      }
    }
    return count;
  }

  /** After a backup import or reset. */
  public static void reload () {
    hidden = null;
    more = null;
  }

  public static String[] defaultKeys () {
    String[] keys = new String[OPTIONS.length];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = OPTIONS[i].key;
    }
    return keys;
  }

  /** The menu as the user wants it: {@link #ids}/{@link #titles}, and what waits under More… ({@link #moreIds}). */
  public static final class Menu {
    public final int[] ids, moreIds;
    public final String[] titles, moreTitles;

    Menu (int[] ids, String[] titles, int[] moreIds, String[] moreTitles) {
      this.ids = ids;
      this.titles = titles;
      this.moreIds = moreIds;
      this.moreTitles = moreTitles;
    }
  }

  private static int optionIndexOf (int id) {
    for (int i = 0; i < OPTIONS.length; i++) {
      for (int optionId : OPTIONS[i].ids) {
        if (optionId == id) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * The chat's ⋮ menu as built (only the options that apply here), in the user's order, without the hidden ones and
   * with those under More taken out: "More…" ({@code R.id.btn_xgramChatMenuMore}) ends the menu while any wait there.
   * Ids the user can't choose about (debug rows) keep their place at the end.
   */
  @NonNull
  public static Menu apply (@NonNull int[] ids, @NonNull String[] titles) {
    List<String> order = XgramReorder.ordered(XgramReorder.CHAT_MENU, defaultKeys());
    List<Integer> outIds = new ArrayList<>(ids.length + 1), moreIds = new ArrayList<>();
    List<String> outTitles = new ArrayList<>(ids.length + 1), moreTitles = new ArrayList<>();
    boolean[] placed = new boolean[ids.length];
    for (String key : order) {
      for (int i = 0; i < ids.length; i++) {
        if (placed[i]) {
          continue;
        }
        int index = optionIndexOf(ids[i]);
        if (index == -1 || !OPTIONS[index].key.equals(key)) {
          continue;
        }
        placed[i] = true;
        if (isHidden(key)) {
          continue;
        }
        if (isUnderMore(key)) {
          moreIds.add(ids[i]);
          moreTitles.add(titles[i]);
        } else {
          outIds.add(ids[i]);
          outTitles.add(titles[i]);
        }
      }
    }
    for (int i = 0; i < ids.length; i++) {
      if (!placed[i]) {
        outIds.add(ids[i]);
        outTitles.add(titles[i]);
      }
    }
    if (!moreIds.isEmpty()) {
      outIds.add(R.id.btn_xgramChatMenuMore);
      outTitles.add(Lang.getString(R.string.XgramChatMenuMoreItem));
    }
    return new Menu(toArray(outIds), outTitles.toArray(new String[0]), toArray(moreIds), moreTitles.toArray(new String[0]));
  }

  private static int[] toArray (List<Integer> list) {
    int[] array = new int[list.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = list.get(i);
    }
    return array;
  }
}
