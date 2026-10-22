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
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.chats.XgramMessageMenu
import com.fahadali.xgram.chats.XgramReactionBar
import com.fahadali.xgram.compose.ui.ReorderRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.reorderGroup
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Message menu: which options a long press on a message shows, and in what order (Telegram X's and XGram's mixed). */
@Composable
fun MessageMenuPage() {
  SettingsPage(title = str(R.string.XgramMessageMenu)) {
    val settings = XgramSettings.instance()
    val options = XgramMessageMenu.OPTIONS.associateBy { it.key }
    val keys = XgramMessageMenu.OPTIONS.map { it.key }.toTypedArray()

    // The menu as a popup next to the message (Telegram) or as the sheet
    group {
      switch(
        R.id.btn_xgramMessagePopup, s(R.string.XgramMessagePopup), s(R.string.XgramMessagePopupInfo),
        checked = settings.messagePopup()
      ) {
        settings.setMessagePopup(it)
      }
    }

    // Where the quick reactions go: above the list of options, which can be long.
    // The popup menu always has them above the message, so there the bar is only on or off. On keeps the stored Top
    // or Bottom, which the sheet still follows when the popup is off.
    val popup = settings.messagePopup()
    val reactionBar = settings.reactionBar()
    group {
      buttons(
        R.id.btn_xgramReactionBar, s(R.string.XgramReactionBar),
        if (popup) {
          listOf(
            XgramReactionBar.popupModeName(XgramSettings.REACTION_BAR_TOP) to
              (if (reactionBar != XgramSettings.REACTION_BAR_OFF) reactionBar else XgramSettings.REACTION_BAR_TOP),
            XgramReactionBar.popupModeName(XgramSettings.REACTION_BAR_OFF) to XgramSettings.REACTION_BAR_OFF
          )
        } else {
          listOf(
            XgramSettings.REACTION_BAR_TOP,
            XgramSettings.REACTION_BAR_BOTTOM,
            XgramSettings.REACTION_BAR_OFF
          ).map { XgramReactionBar.modeName(it) to it }
        },
        selected = reactionBar
      ) {
        settings.setReactionBar(it)
      }
    }
    description(if (popup) R.string.XgramReactionBarPopupInfo else R.string.XgramReactionBarInfo)

    description(R.string.XgramMessageMenuInfo)
    reorderGroup(
      key = "messageMenu",
      id = R.id.btn_xgramMenuItem,
      rows = XgramReorder.ordered(XgramReorder.MESSAGE_MENU, keys).filter { XgramMessageMenu.isAvailable(it) }.mapNotNull { key ->
        options[key]?.let { option ->
          // Summarize and Edit history are only added while their setting is on.
          val reason = XgramSettingDeps.messageMenuOptionReason(key)
          val hidden = XgramMessageMenu.isHidden(key)
          // Under More: the option leaves the menu and waits under its More option (More itself can't go there).
          // Report and Restrict are there by default, as in Telegram X; Restrict can't leave it (no toggle).
          // Under a hidden More that the menu leaves out, Report and Restrict go with it: not under More (⋯ off), as
          // in the menu, and the subtitle says why.
          val hiddenWithMore = !hidden && XgramMessageMenu.isHiddenWithMore(key)
          val underMore = !hidden && !hiddenWithMore && XgramMessageMenu.showsUnderMore(key)
          val inMore = if (XgramMessageMenu.canChoosePlace(key)) underMore else null
          // A hidden More still shows while options wait under it (XgramMessageMenu.filter): say so, as the menu does
          val moreKept = key == XgramMessageMenu.KEY_MORE_OPTION && hidden && XgramMessageMenu.hasOptionsUnderMore()
          ReorderRow(
            key = key, title = s(option.name), icon = option.icon(), checked = !hidden,
            enabled = reason == 0,
            subtitle = if (reason != 0) s(reason) else if (underMore) s(R.string.XgramMenuUnderMore)
              else if (hiddenWithMore) s(R.string.XgramMenuHiddenWithMore)
              else if (moreKept) s(R.string.XgramMenuMoreKept) else null,
            inMore = inMore
          )
        }
      },
      onToggle = { key, shown ->
        XgramMessageMenu.setHidden(key, !shown)
        // Shown again where it is by default, under More (Report, Restrict), while More is hidden: More comes back on to
        // reach it, as choosing Under More does on the classic page
        if (shown && XgramMessageMenu.showsUnderMore(key) && XgramMessageMenu.isHidden(XgramMessageMenu.KEY_MORE_OPTION)) {
          XgramMessageMenu.setUnderMore(key, true)
        }
      },
      onMore = { key, inMore ->
        XgramMessageMenu.setUnderMore(key, inMore)
        if (inMore) {
          XgramMessageMenu.setHidden(key, false) // under More is shown: just one tap away
        }
      }
    ) { order ->
      XgramReorder.save(XgramReorder.MESSAGE_MENU, order)
    }

    group {
      action(R.id.btn_xgramResetOrder, s(R.string.XgramResetOrder), icon = R.drawable.baseline_replay_24) {
        XgramReorder.reset(XgramReorder.MESSAGE_MENU)
      }
    }
  }
}
