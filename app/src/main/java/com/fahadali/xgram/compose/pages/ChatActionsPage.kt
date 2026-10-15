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
import com.fahadali.xgram.chats.XgramChatActionsController
import com.fahadali.xgram.compose.ui.ReorderRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.reorderGroup
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/**
 * Chat actions: the order of the actions a long press on a chat shows, in the chat preview's bar and in the quick
 * actions pill. No switches: a chat shows the actions it has.
 */
@Composable
fun ChatActionsPage() {
  SettingsPage(title = str(R.string.XgramChatActionsOrder)) {
    val keys = XgramChatActionsController.KEYS

    description(R.string.XgramChatActionsOrderInfo)
    reorderGroup(
      key = "chatActions",
      id = R.id.btn_xgramChatAction,
      rows = XgramReorder.ordered(XgramReorder.CHAT_ACTIONS, keys).mapNotNull { key ->
        val index = keys.indexOf(key)
        if (index < 0) {
          null
        } else {
          ReorderRow(key = key, title = s(XgramChatActionsController.NAMES[index]), icon = XgramChatActionsController.ICONS[index])
        }
      }
    ) { order ->
      XgramReorder.save(XgramReorder.CHAT_ACTIONS, order)
    }

    group {
      action(R.id.btn_xgramResetOrder, s(R.string.XgramResetOrder), icon = R.drawable.baseline_replay_24) {
        XgramReorder.reset(XgramReorder.CHAT_ACTIONS)
      }
    }
  }
}
