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
import com.fahadali.xgram.chats.XgramHeaderButtonsController
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/**
 * Header buttons: which buttons the chat list's top row shows next to the folder tabs (the same rows as the classic
 * page). The lock and Activity's bell keep their own settings; the ⋮ always stays. The chat list follows at once.
 */
@Composable
fun HeaderButtonsPage() {
  SettingsPage(title = str(R.string.XgramHeaderButtons)) {
    description(R.string.XgramHeaderButtonsInfo)
    group {
      XgramHeaderButtonsController.KEYS.forEachIndexed { index, key ->
        // Activity's bell can't change while Activity is off: the reason replaces the line under the title
        val reason = XgramHeaderButtonsController.reason(key)
        switch(
          R.id.btn_xgramHeaderButton, s(XgramHeaderButtonsController.NAMES[index]),
          subtitle = s(if (reason != 0) reason else XgramHeaderButtonsController.INFOS[index]),
          icon = XgramHeaderButtonsController.ICONS[index],
          checked = XgramHeaderButtonsController.isShown(key), enabled = reason == 0, key = key
        ) {
          XgramHeaderButtonsController.setShown(key, it)
        }
      }
    }
    description(R.string.XgramHeaderMoreNote)
  }
}
