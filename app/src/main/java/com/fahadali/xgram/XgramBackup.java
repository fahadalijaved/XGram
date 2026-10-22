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

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Iterator;

import me.vkryl.leveldb.LevelDB;

/**
 * Export, import and reset of XGram's settings (NagramX / exteraGram backup). Settings are stored untyped, so every
 * key prefix is listed here with its type. Local data (message history, local reads, last seen, Activity's items) is
 * not included.
 */
public final class XgramBackup {
  private XgramBackup () { }

  private static final String[] BOOLEAN_PREFIXES = {
    // XgramSettings toggles
    "xgram_ghost_dont_read", "xgram_ghost_dont_type", "xgram_ghost_offline", "xgram_ghost_schedule_send", "xgram_ghost_silent", "xgram_ghost_read_on_send",
    "xgram_ghost_local_read", "xgram_history_save_deleted", "xgram_history_save_edits", "xgram_history_save_media", "xgram_history_red_deleted",
    "xgram_hide_sponsored", "xgram_show_peer_ids", "xgram_two_pane", "xgram_icon_theme", "xgram_merge_communities", "xgram_save_last_seen", "xgram_show_seconds", "xgram_tap_time_details",
    "xgram_clean_links", "xgram_fix_link_previews", "xgram_ghost_typing_for_stickers", "xgram_allow_screenshots", "xgram_save_protected", "xgram_proxy_off_vpn", "xgram_proxy_off_wifi", "xgram_proxy_off_mobile", "xgram_hide_phone", "xgram_block_content_capture", "xgram_prefer_ipv6", "xgram_limit_message_width", "xgram_delete_for_everyone", "xgram_hide_blocked", "xgram_forward_without_author", "xgram_ai_tools", "xgram_hide_fab", "xgram_all_chats_label", "xgram_pull_to_next", "xgram_bubble_tail", "xgram_download_boost_on", "xgram_tap_to_record", "xgram_system_emoji", "xgram_tr_always", "xgram_tr_fallback", XgramSettings.KEY_TR_AUTO_ALL,
    XgramSettings.KEY_INLINE_VIDEO, XgramSettings.KEY_VIDEO_AUTO_HIDE,
    // Foldables (Appearance > Layout)
    XgramSettings.KEY_FOLD_HINGE_SPLIT, XgramSettings.KEY_FOLD_TABLETOP,
    XgramSettings.KEY_PANE_SPLIT_EVENLY, XgramSettings.KEY_PANE_RESIZABLE, XgramSettings.KEY_NAV_RAIL, XgramSettings.KEY_BOTTOM_NAV, XgramSettings.KEY_NAV_HIDE_DRAWER,
    // Pure black night mode (Appearance, with Expressive design)
    XgramSettings.KEY_PURE_BLACK,
    // Chat buttons match avatar shape (Appearance, with Expressive design)
    XgramSettings.KEY_CHAT_FABS_AVATAR_SHAPE,
    XgramSettings.KEY_DRAG_DROP, XgramSettings.KEY_OPEN_BESIDE,
    // Reactions in channels (Messages)
    XgramSettings.KEY_CHANNEL_REACTIONS,
    // Bigger text, kept for three lines (Chat list view)
    XgramSettings.KEY_CHAT_LIST_BIG_TEXT,
    // Undo send: voice and video messages wait too, faded bubbles in the chat, only the newest waits (Messages > Message box)
    XgramSettings.KEY_UNDO_SEND_VOICE, XgramSettings.KEY_UNDO_SEND_BUBBLES, XgramSettings.KEY_UNDO_SEND_NEWEST,
    // Activity (Chat list), one by one: no prefix may match the per-viewer filter "xgram_actview_filter" (not a setting)
    XgramSettings.KEY_ACTIVITY_ON, XgramSettings.KEY_ACTIVITY_REACTIONS, XgramSettings.KEY_ACTIVITY_MUTED, XgramSettings.KEY_ACTIVITY_DISCUSSION,
    XgramSettings.KEY_ACTIVITY_LOOKBACK, XgramSettings.KEY_ACTIVITY_BADGE, XgramSettings.KEY_ACTIVITY_HEADER,
    // Later (Chat list)
    com.fahadali.xgram.later.XgramLater.KEY_ROW, com.fahadali.xgram.later.XgramLater.KEY_RAISE,
    // Check In defaults (Messages); the live Check Ins ("xgram_ci_") are not settings and never listed here
    XgramSettings.KEY_CHECKIN_LOCATION, XgramSettings.KEY_CHECKIN_BATTERY, XgramSettings.KEY_CHECKIN_FOLLOW,
    // Developer (debug builds): test items in the send menu; a release build never shows them, whatever is imported
    XgramSettings.KEY_SEND_MENU_TESTS,
    // Attach button while typing (Message box buttons), avatar next to the last message (Chat look)
    XgramSettings.KEY_INPUT_ATTACH_TYPING, XgramSettings.KEY_AVATAR_AT_LAST,
    // Lock button in the chat list (Passcode lock), Popup menu (Message menu)
    XgramSettings.KEY_HIDE_LOCK_BUTTON, XgramSettings.KEY_MESSAGE_POPUP,
    // Add to group or channel (a bot's profile): as an admin, with every right
    XgramSettings.KEY_ADD_BOT_ADMIN, XgramSettings.KEY_ADD_BOT_ALL_RIGHTS,
    // per chat / per sender
    "xgram_sb_", "xgram_ghost_ex_", "xgram_tabs_side_", "xgram_drawer_hide_", "xgram_input_hide_", XgramSettings.KEY_UNDO_SEND_EXCLUDE,
    // per account: removed by reset, but exported by Telegram user in "accounts" (see exportAccounts)
    XgramSettings.KEY_HIDE_FOLDERS, XgramSettings.KEY_HIDE_ALL_CHATS, com.fahadali.xgram.chats.XgramAutoTranslate.KEY_PREFIX
  };

  /** Kept per account slot, so exported by Telegram user in "accounts" instead of as they are. */
  private static boolean isPerAccountKey (String key) {
    return key.startsWith(XgramSettings.KEY_HIDE_FOLDERS) || key.startsWith(XgramSettings.KEY_HIDE_ALL_CHATS) || key.startsWith(com.fahadali.xgram.chats.XgramAutoTranslate.KEY_PREFIX);
  }
  /** Telegram X's own per account list of folders hidden on this device ("Hide in XGram" in Chat folders). */
  private static final String HIDDEN_FOLDERS_KEY = "disabled_chat_filter_ids";

  /** The Telegram user logged in to an account slot, or null: per account settings follow the user, not the slot. */
  private static String accountUser (TdlibAccount account) {
    long userId = account.isUnauthorized() ? 0 : account.getKnownUserId();
    return userId != 0 ? (account.isDebug() ? "test_" : "") + userId : null;
  }

  /** Every logged in account's per account settings, empty ones too, so a restore brings back what was shown. */
  private static JSONObject exportAccounts () throws org.json.JSONException {
    JSONObject accounts = new JSONObject();
    for (TdlibAccount account : TdlibManager.instance()) {
      String user = accountUser(account);
      if (user == null || accounts.has(user)) {
        continue;
      }
      JSONArray hiddenFolders = new JSONArray();
      int[] ids = Settings.instance().getIntArray(TdlibSettingsManager.key(HIDDEN_FOLDERS_KEY, account.id));
      if (ids != null) {
        for (int id : ids) {
          hiddenFolders.put(id);
        }
      }
      JSONObject values = new JSONObject();
      values.put("hideFolders", XgramSettings.instance().hideFolders(account.id));
      values.put("hideAllChats", XgramSettings.instance().hideAllChats(account.id));
      values.put("hiddenFolders", hiddenFolders);
      values.put("autoTranslate", com.fahadali.xgram.chats.XgramAutoTranslate.exportAccount(account.id)); // chats set in their menu
      values.put("later", com.fahadali.xgram.later.XgramLater.exportAccount(account.id)); // Later: pending and due items
      accounts.put(user, values);
    }
    return accounts;
  }

  /** Sets the per account settings of each account here whose user is in the backup; returns how many. */
  private static int importAccounts (JSONObject accounts) throws org.json.JSONException {
    int count = 0;
    for (TdlibAccount account : TdlibManager.instance()) {
      String user = accountUser(account);
      JSONObject values = user != null ? accounts.optJSONObject(user) : null;
      if (values == null) {
        continue;
      }
      if (values.optBoolean("hideFolders", false)) {
        pmc().putBoolean(XgramSettings.hideFoldersKey(account.id), true);
      } else {
        pmc().remove(XgramSettings.hideFoldersKey(account.id));
      }
      if (values.optBoolean("hideAllChats", false)) {
        pmc().putBoolean(XgramSettings.hideAllChatsKey(account.id), true);
      } else {
        pmc().remove(XgramSettings.hideAllChatsKey(account.id));
      }
      JSONArray hiddenFolders = values.optJSONArray("hiddenFolders");
      String key = TdlibSettingsManager.key(HIDDEN_FOLDERS_KEY, account.id);
      if (hiddenFolders == null || hiddenFolders.length() == 0) {
        Settings.instance().remove(key);
      } else {
        int[] ids = new int[hiddenFolders.length()];
        for (int i = 0; i < ids.length; i++) {
          ids[i] = hiddenFolders.getInt(i);
        }
        Settings.instance().putIntArray(key, ids);
      }
      if (values.has("autoTranslate")) {
        com.fahadali.xgram.chats.XgramAutoTranslate.importAccount(account.id, values.optJSONObject("autoTranslate"));
      }
      com.fahadali.xgram.later.XgramLater.importAccount(account.id, values.optJSONArray("later")); // Later: merged, local items win
      count++;
    }
    return count;
  }
  private static final String[] STRING_PREFIXES = {"xgram_filters", "xgram_ln_", "xgram_menu_hidden", com.fahadali.xgram.chats.XgramMessageMenu.KEY_MORE, com.fahadali.xgram.chats.XgramMessageMenu.KEY_IN_MENU, XgramReorder.KEY_PREFIX, XgramSettings.KEY_TR_TARGET};
  private static final String[] INT_PREFIXES = {"xgram_tab_", "xgram_history_retention", "xgram_avatar_corners", "xgram_chat_long_press", "xgram_look_", "xgram_icon_pack", "xgram_cmode_", "xgram_update_channel", "xgram_download_boost_level", "xgram_swipe_", "xgram_tr_provider", XgramSettings.KEY_INLINE_VIDEO_START, XgramSettings.KEY_ENTER_TO_SEND, XgramSettings.KEY_PANE_LIST_WIDTH, XgramSettings.KEY_NOTIFICATION_ICON, XgramSettings.KEY_REACTION_BAR, XgramSettings.KEY_NAV_TABS, XgramSettings.KEY_NAV_PROFILE, XgramSettings.KEY_NAV_STYLE, XgramSettings.KEY_NAV_FAB, XgramSettings.KEY_UNDO_SEND, XgramSettings.KEY_ACTIVITY_BAR,
    com.fahadali.xgram.later.XgramLater.KEY_WAKE_EARLY, // Later
    XgramSettings.KEY_CHECKIN_TIMER, XgramSettings.KEY_CHECKIN_GRACE, XgramSettings.KEY_CHECKIN_RADIUS}; // Check In
  /** Data rather than settings: exported and imported, but kept by {@link #reset}. */
  private static final String[] LONG_PREFIXES = {"xgram_bm_"};

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  /** All settings as JSON. */
  public static String export () {
    try {
      JSONObject booleans = new JSONObject(), strings = new JSONObject(), ints = new JSONObject();
      for (String prefix : BOOLEAN_PREFIXES) {
        for (LevelDB.Entry entry : pmc().find(prefix)) {
          if (isBooleanKey(entry.key()) && !isPerAccountKey(entry.key())) {
            booleans.put(entry.key(), entry.asBoolean());
          }
        }
      }
      for (String prefix : STRING_PREFIXES) {
        for (LevelDB.Entry entry : pmc().find(prefix)) {
          strings.put(entry.key(), pmc().getString(entry.key(), ""));
        }
      }
      for (String prefix : INT_PREFIXES) {
        for (LevelDB.Entry entry : pmc().find(prefix)) {
          if (!entry.key().startsWith("xgram_tabs_side_")) {
            ints.put(entry.key(), entry.asInt());
          }
        }
      }
      JSONObject longs = new JSONObject();
      for (String prefix : LONG_PREFIXES) {
        for (LevelDB.Entry entry : pmc().find(prefix)) {
          longs.put(entry.key(), entry.asLong());
        }
      }
      JSONObject root = new JSONObject();
      root.put("app", "XGram");
      root.put("version", 1);
      root.put("booleans", booleans);
      root.put("strings", strings);
      root.put("ints", ints);
      root.put("longs", longs);
      root.put("accounts", exportAccounts());
      return root.toString(2);
    } catch (Throwable t) {
      return "{}";
    }
  }

  private static boolean hasPrefix (String key, String[] prefixes) {
    for (String prefix : prefixes) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBooleanKey (String key) {
    for (String prefix : BOOLEAN_PREFIXES) {
      if (prefix.endsWith("_") ? key.startsWith(prefix) : key.equals(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** Imports settings exported by {@link #export}. Returns the number of values set, or -1 if it isn't a backup. */
  public static int importJson (String json) {
    try {
      JSONObject root = new JSONObject(json.trim());
      if (!"XGram".equals(root.optString("app"))) {
        return -1;
      }
      int count = 0;
      JSONObject booleans = root.optJSONObject("booleans");
      if (booleans != null) {
        for (Iterator<String> it = booleans.keys(); it.hasNext(); ) {
          String key = it.next();
          if (key.startsWith("xgram_") && isBooleanKey(key) && !isPerAccountKey(key)) {
            pmc().putBoolean(key, booleans.getBoolean(key));
            count++;
          }
        }
        if (booleans.has(XgramSettings.KEY_INLINE_VIDEO)) {
          // The restored Play videos in chat choice counts as one made after the one-time switch-on
          // (XgramSettings.loadInlineVideo), which would otherwise turn it back on at the next start.
          pmc().putBoolean(XgramSettings.KEY_INLINE_VIDEO_V2, true);
        }
      }
      JSONObject strings = root.optJSONObject("strings");
      if (strings != null) {
        for (Iterator<String> it = strings.keys(); it.hasNext(); ) {
          String key = it.next();
          if (key.equals("xgram_filters") || key.startsWith("xgram_ln_") || key.equals("xgram_menu_hidden") || key.equals(com.fahadali.xgram.chats.XgramMessageMenu.KEY_MORE) || key.equals(com.fahadali.xgram.chats.XgramMessageMenu.KEY_IN_MENU) || key.startsWith(XgramReorder.KEY_PREFIX) || key.equals(XgramSettings.KEY_TR_TARGET)) {
            pmc().putString(key, strings.getString(key));
            count++;
          }
        }
      }
      JSONObject ints = root.optJSONObject("ints");
      if (ints != null) {
        for (Iterator<String> it = ints.keys(); it.hasNext(); ) {
          String key = it.next();
          if (key.startsWith("xgram_tab_") || key.startsWith("xgram_cmode_") || key.equals("xgram_history_retention") || key.equals("xgram_avatar_corners") || key.equals("xgram_chat_long_press") || key.equals("xgram_download_boost_level") || key.equals("xgram_swipe_left") || key.equals("xgram_swipe_right") || key.equals("xgram_tr_provider") || key.equals(XgramSettings.KEY_INLINE_VIDEO_START) || key.equals(XgramSettings.KEY_ENTER_TO_SEND) || key.equals(XgramSettings.KEY_PANE_LIST_WIDTH) || key.equals(XgramSettings.KEY_NOTIFICATION_ICON) || key.equals(XgramSettings.KEY_REACTION_BAR) || key.equals(XgramSettings.KEY_NAV_TABS) || key.equals(XgramSettings.KEY_NAV_PROFILE) || key.equals(XgramSettings.KEY_NAV_STYLE) || key.equals(XgramSettings.KEY_NAV_FAB) || key.equals(XgramSettings.KEY_UNDO_SEND) || key.equals(XgramSettings.KEY_ACTIVITY_BAR) ||
            key.equals(com.fahadali.xgram.later.XgramLater.KEY_WAKE_EARLY) || // Later
            key.equals(XgramSettings.KEY_CHECKIN_TIMER) || key.equals(XgramSettings.KEY_CHECKIN_GRACE) || key.equals(XgramSettings.KEY_CHECKIN_RADIUS)) { // Check In
            pmc().putInt(key, ints.getInt(key));
            count++;
          }
        }
      }
      JSONObject longs = root.optJSONObject("longs");
      if (longs != null) {
        for (Iterator<String> it = longs.keys(); it.hasNext(); ) {
          String key = it.next();
          if (hasPrefix(key, LONG_PREFIXES)) {
            pmc().putLong(key, longs.getLong(key));
            count++;
          }
        }
      }
      JSONObject accounts = root.optJSONObject("accounts");
      if (accounts != null) {
        count += importAccounts(accounts);
      }
      return count;
    } catch (Throwable t) {
      return -1;
    }
  }

  /** Removes every XGram setting (not the message history or the login). The app restarts to apply defaults. */
  public static void reset () {
    for (String prefix : BOOLEAN_PREFIXES) {
      pmc().removeByPrefix(prefix);
    }
    for (String prefix : STRING_PREFIXES) {
      pmc().removeByPrefix(prefix);
    }
    for (String prefix : INT_PREFIXES) {
      pmc().removeByPrefix(prefix);
    }
    // Ghost mode is off after a reset: counters lowered on this device go back to the server's.
    com.fahadali.xgram.ghost.XgramLocalRead.clearAll();
    com.fahadali.xgram.chats.XgramAutoTranslate.reload();
  }
}
