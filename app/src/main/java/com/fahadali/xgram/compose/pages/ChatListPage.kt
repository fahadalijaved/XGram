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
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.chats.XgramChatLongPress
import com.fahadali.xgram.chats.XgramCommunities
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Chat List: folders, merged communities, what a long press on a chat does, the screen around the list. */
@Composable
fun ChatListPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramChatList)) {
    val settings = XgramSettings.instance()
    val accountId = host.tdlib.accountId()

    header(R.string.XgramHeaderFolders)
    group {
      // Per account, as on the classic page.
      switch(R.id.btn_xgramHideFolders, s(R.string.XgramHideFolders), s(R.string.XgramHideFoldersSub), checked = settings.hideFolders(accountId)) {
        settings.setHideFolders(accountId, it)
      }
    }
    description(R.string.XgramChatListFoldersInfo)
    group {
      switch(R.id.btn_xgramAllChatsLabel, s(R.string.XgramAllChatsLabel), s(R.string.XgramAllChatsLabelSub), checked = settings.allChatsLabel()) {
        settings.setAllChatsLabel(it)
      }
    }

    header(R.string.XgramChats)
    group {
      switch(R.id.btn_xgramMergeCommunities, s(R.string.XgramMergeCommunities), s(R.string.XgramMergeCommunitiesSub), checked = settings.mergeCommunities()) {
        settings.setMergeCommunities(it)
        XgramCommunities.notifyAllAccounts() // the setting is shared by every account
      }
      buttons(
        R.id.btn_xgramChatLongPress, s(R.string.XgramChatLongPress),
        listOf(
          s(R.string.XgramChatLongPressPreview) to XgramChatLongPress.MODE_PREVIEW,
          s(R.string.XgramComposeLongPressQuick) to XgramChatLongPress.MODE_QUICK,
          s(R.string.XgramComposeLongPressCompact) to XgramChatLongPress.MODE_COMPACT
        ),
        selected = settings.chatLongPress()
      ) {
        settings.setChatLongPress(it)
      }
      value(
        R.id.btn_xgramChatActionsOrder, s(R.string.XgramChatActionsOrder),
        s(if (XgramReorder.hasOrder(XgramReorder.CHAT_ACTIONS)) R.string.XgramOrderCustom else R.string.XgramOrderDefault)
      ) {
        host.openPage(XgramPage.CHAT_ACTIONS)
      }
    }

    header(R.string.XgramHeaderScreen)
    group {
      switch(R.id.btn_xgramHideFab, s(R.string.XgramHideFab), s(R.string.XgramHideFabSub), checked = settings.hideFab()) {
        settings.setHideFab(it)
      }
      switch(
        R.id.btn_xgramConnectionStatusStart, s(R.string.XgramConnectionStatusStart), s(R.string.XgramConnectionStatusStartSub),
        checked = settings.connectionStatusAtStart()
      ) {
        settings.setConnectionStatusAtStart(it)
      }
    }
  }
}
