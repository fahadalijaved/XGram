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

import org.json.JSONObject;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Iterator;

import me.vkryl.leveldb.LevelDB;

/**
 * Export, import and reset of XGram's settings (NagramX / exteraGram backup). Settings are stored untyped, so every
 * key prefix is listed here with its type. Local data (message history, local reads, last seen) is not included.
 */
public final class XgramBackup {
  private XgramBackup () { }

  private static final String[] BOOLEAN_PREFIXES = {
    // XgramSettings toggles
    "xgram_ghost_dont_read", "xgram_ghost_dont_type", "xgram_ghost_offline", "xgram_ghost_schedule_send", "xgram_ghost_silent", "xgram_ghost_read_on_send",
    "xgram_ghost_local_read", "xgram_history_save_deleted", "xgram_history_save_edits", "xgram_history_save_media",
    "xgram_hide_sponsored", "xgram_show_peer_ids", "xgram_two_pane", "xgram_icon_theme", "xgram_merge_communities", "xgram_save_last_seen", "xgram_show_seconds", "xgram_tap_time_details",
    "xgram_clean_links", "xgram_fix_link_previews", "xgram_ghost_typing_for_stickers", "xgram_allow_screenshots", "xgram_save_protected", "xgram_proxy_off_vpn", "xgram_proxy_off_wifi", "xgram_proxy_off_mobile", "xgram_hide_phone", "xgram_block_content_capture", "xgram_prefer_ipv6", "xgram_limit_message_width", "xgram_delete_for_everyone", "xgram_hide_blocked", "xgram_forward_without_author", "xgram_ai_tools", "xgram_hide_fab", "xgram_pull_to_next", "xgram_bubble_tail",
    // per chat / per sender
    "xgram_sb_", "xgram_ghost_ex_", "xgram_tabs_side_", "xgram_drawer_hide_"
  };
  private static final String[] STRING_PREFIXES = {"xgram_filters", "xgram_ln_", "xgram_menu_hidden"};
  private static final String[] INT_PREFIXES = {"xgram_tab_", "xgram_history_retention", "xgram_avatar_corners", "xgram_look_", "xgram_icon_pack"};
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
          if (isBooleanKey(entry.key())) {
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
          if (key.startsWith("xgram_") && isBooleanKey(key)) {
            pmc().putBoolean(key, booleans.getBoolean(key));
            count++;
          }
        }
      }
      JSONObject strings = root.optJSONObject("strings");
      if (strings != null) {
        for (Iterator<String> it = strings.keys(); it.hasNext(); ) {
          String key = it.next();
          if (key.equals("xgram_filters") || key.startsWith("xgram_ln_")) {
            pmc().putString(key, strings.getString(key));
            count++;
          }
        }
      }
      JSONObject ints = root.optJSONObject("ints");
      if (ints != null) {
        for (Iterator<String> it = ints.keys(); it.hasNext(); ) {
          String key = it.next();
          if (key.startsWith("xgram_tab_") || key.equals("xgram_history_retention") || key.equals("xgram_avatar_corners")) {
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
  }
}
