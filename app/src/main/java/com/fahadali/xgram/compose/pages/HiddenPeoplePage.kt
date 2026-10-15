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
package com.fahadali.xgram.compose.pages

import androidx.compose.runtime.Composable
import com.fahadali.xgram.chats.XgramFilters
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Filters → Hidden people: how many senders this account hides in groups, and unhiding them all. */
@Composable
fun HiddenPeoplePage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramShadowBanned)) {
    val count = XgramFilters.shadowBannedCount(host.tdlib)
    group {
      value(R.id.btn_xgramShadowBanList, s(R.string.XgramShadowBanned), count.toString()) {
        // A count only, as on the classic page.
      }
      action(R.id.btn_xgramClearShadowBans, s(R.string.XgramClearShadowBans), destructive = true, enabled = count > 0) {
        host.confirm(
          ConfirmSpec(text = s(R.string.XgramClearShadowBansConfirm), confirm = s(R.string.XgramClearShadowBans), destructive = true) {
            XgramFilters.clearShadowBans(host.tdlib)
          }
        )
      }
    }
    description(R.string.XgramShadowBanInfo)
  }
}
