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

import com.fahadali.xgram.chats.XgramFilters;
import com.fahadali.xgram.chats.XgramLocalNames;
import com.fahadali.xgram.ghost.XgramGhost;
import com.fahadali.xgram.ghost.XgramLastSeen;
import com.fahadali.xgram.ghost.XgramLocalRead;
import com.fahadali.xgram.history.XgramDeleted;
import com.fahadali.xgram.history.XgramHistory;

import org.thunderdog.challegram.unsorted.Settings;

import java.util.Collection;
import java.util.Iterator;

import me.vkryl.leveldb.LevelDB;

/**
 * XGram data kept per account slot. Telegram X gives a logged out slot to the next login, so everything an account
 * left behind (saved deleted messages, local names, last seen, local reads, bans, bookmarks…) is erased on logout.
 */
public final class XgramAccountData {
  private XgramAccountData () { }

  private static final String[] PREFIXES = {
    "xgram_bm_", "xgram_sb_", "xgram_ln_", "xgram_ghost_ex_", "xgram_ls_", "xgram_lr_", "xgram_tab_", "xgram_tabs_side_",
    "xgram_cm_", "xgram_cmode_", com.fahadali.xgram.chats.XgramAutoTranslate.KEY_PREFIX
  };

  /** TDLib thread: the account in this slot logged out. */
  public static void eraseAccount (int accountId) {
    try {
      String suffix = accountId + "_";
      LevelDB pmc = Settings.instance().pmc();
      for (String prefix : PREFIXES) {
        pmc.removeByPrefix(prefix + suffix);
      }
      pmc.remove(XgramSettings.hideFoldersKey(accountId));
      XgramLocalNames.forgetAccount(accountId);
      XgramLastSeen.forgetAccount(accountId);
      XgramGhost.forgetAccount(accountId);
      com.fahadali.xgram.chats.XgramAutoTranslate.forgetAccount(accountId);
      XgramFilters.forgetAccount(accountId);
      XgramDeleted.forgetAccount(accountId);
      XgramLocalRead.forgetAccount(accountId);
      com.fahadali.xgram.topics.XgramForumUnread.forgetAccount(accountId);
      com.fahadali.xgram.chats.XgramUnreadTotals.forgetAccount(accountId);
      com.fahadali.xgram.topics.XgramTopicMute.forgetAccount(accountId);
      com.fahadali.xgram.chats.XgramAiTools.forgetAccount(accountId);
      com.fahadali.xgram.chats.XgramCommunities.forgetAccount(accountId);
      XgramHistory.instance().clearAccount(accountId);
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Cannot erase account data", t);
    }
  }

  /** Removes the keys of a cache that start with {@code prefix}. */
  public static void removeKeys (Collection<String> keys, String prefix) {
    Iterator<String> it = keys.iterator();
    while (it.hasNext()) {
      if (it.next().startsWith(prefix)) {
        it.remove();
      }
    }
  }
}
