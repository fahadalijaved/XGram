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
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.chats.XgramChatMenu
import com.fahadali.xgram.compose.ui.ReorderRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.reorderGroup
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Chat menu: what a chat's ⋮ menu shows, what waits under More… at its end, and in what order. */
@Composable
fun ChatMenuPage() {
  SettingsPage(title = str(R.string.XgramChatMenu)) {
    val options = XgramChatMenu.OPTIONS.associateBy { it.key }

    description(R.string.XgramChatMenuInfo)
    reorderGroup(
      key = "chatMenu",
      id = R.id.btn_xgramChatMenuItem,
      rows = XgramReorder.ordered(XgramReorder.CHAT_MENU, XgramChatMenu.defaultKeys()).filter { XgramChatMenu.isAvailable(it) }.mapNotNull { key ->
        options[key]?.let { option ->
          val hidden = XgramChatMenu.isHidden(key)
          val underMore = XgramChatMenu.isUnderMore(key)
          ReorderRow(
            key = key, title = s(option.name), icon = option.icon, checked = !hidden,
            subtitle = if (underMore) s(R.string.XgramMenuUnderMore) else null,
            inMore = underMore
          )
        }
      },
      onToggle = { key, shown ->
        XgramChatMenu.setHidden(key, !shown)
      },
      onMore = { key, inMore ->
        XgramChatMenu.setUnderMore(key, inMore)
        if (inMore) {
          XgramChatMenu.setHidden(key, false) // under More… is shown: one tap away
        }
      }
    ) { order ->
      XgramReorder.save(XgramReorder.CHAT_MENU, order)
    }

    group {
      action(R.id.btn_xgramResetOrder, s(R.string.XgramResetOrder), icon = R.drawable.baseline_replay_24) {
        XgramReorder.reset(XgramReorder.CHAT_MENU)
      }
    }
  }
}
