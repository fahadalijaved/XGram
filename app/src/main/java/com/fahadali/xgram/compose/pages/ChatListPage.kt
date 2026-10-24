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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.appearance.XgramChatListViewCard
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.chats.XgramChatListMode
import com.fahadali.xgram.chats.XgramChatLongPress
import com.fahadali.xgram.chats.XgramCommunities
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.LocalXgramHighlight
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramNavRail
import com.fahadali.xgram.layout.XgramNavToolbar
import org.thunderdog.challegram.R
import org.thunderdog.challegram.unsorted.Settings

/**
 * Chat list: the chat list view, folders, getting around (the bars, the side menu, the header's buttons, the new chat
 * button), merged communities, what a long press on a chat does and the order of its actions.
 */
@Composable
fun ChatListPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramChatList)) {
    val settings = XgramSettings.instance()
    val accountId = host.tdlib.accountId()
    val expressive = XgramExpressive.isEnabled()

    // One line, two lines or three lines: the tiles pick it at once, the chat list springs to it. The header names the
    // card, which has no title of its own (the classic page's row has, so it has no header).
    header(R.string.XgramChatListView)
    group {
      custom(CHAT_LIST_VIEW_KEY, R.id.btn_xgramChatListView) { shapes ->
        ChatListViewCardRow(shapes)
      }
      val bigTextEnabled = XgramChatListMode.isBigTextEnabled()
      switch(
        R.id.btn_xgramChatListBigText, s(R.string.XgramChatListBigText),
        s(if (bigTextEnabled) R.string.XgramChatListBigTextSub else R.string.XgramChatListBigTextOff),
        checked = XgramChatListMode.isBigTextChecked(), enabled = bigTextEnabled
      ) {
        XgramChatListMode.setBigText(it)
      }
    }
    description(R.string.XgramChatListViewInfo)

    header(R.string.XgramHeaderFolders)
    group {
      // Per account, as on the classic page.
      switch(R.id.btn_xgramHideFolders, s(R.string.XgramHideFolders), s(R.string.XgramHideFoldersSub), checked = settings.hideFolders(accountId)) {
        settings.setHideFolders(accountId, it)
      }
    }
    description(R.string.XgramChatListFoldersInfo)
    group {
      // All chats leaves the tabs only while another folder has one (the first tab opens instead, with the archive row).
      val hideAllChatsReason = XgramSettingDeps.hideAllChatsReason(host.tdlib)
      switch(
        R.id.btn_xgramHideAllChats, s(R.string.XgramHideAllChats),
        s(if (hideAllChatsReason != 0) hideAllChatsReason else R.string.XgramHideAllChatsSub),
        checked = settings.hideAllChats(accountId), enabled = hideAllChatsReason == 0
      ) {
        settings.setHideAllChats(accountId, it)
      }
      // The first folder tab's label: none while the tabs or All chats are hidden, or the tabs show icons only.
      val labelReason = XgramSettingDeps.allChatsLabelReason(host.tdlib)
      switch(
        R.id.btn_xgramAllChatsLabel, s(R.string.XgramAllChatsLabel),
        s(if (labelReason != 0) labelReason else R.string.XgramAllChatsLabelSub),
        checked = settings.allChatsLabel(), enabled = labelReason == 0
      ) {
        settings.setAllChatsLabel(it)
      }
    }

    // Getting around: the bars, the side menu, the buttons above the list and the new chat button
    header(R.string.XgramHeaderNavigation)
    group {
      // The floating navigation bar at the bottom of the chat list on narrow windows; with the rail on too, the rail
      // shows on wide windows and the bar on narrow ones. Expressive design: the M3E floating toolbar, whose page sets
      // it up (its tabs, the photo, the colors); the same id, so settings search still finds it
      if (expressive) {
        link(R.id.btn_xgramBottomNav, s(R.string.XgramBottomNavOption), subtitle = XgramNavToolbar.summary()) {
          host.openPage(XgramPage.NAV_BAR)
        }
        // Expressive design's navigation rail on wide windows (only there); off: the chat list's own header (tabs, ☰)
        // is back at once
        val railReason = XgramNavRail.settingReason()
        switch(
          R.id.btn_xgramNavRail, s(R.string.XgramNavRailOption),
          s(if (railReason != 0) railReason else R.string.XgramNavRailOptionSub),
          checked = settings.navRail(), enabled = railReason == 0
        ) {
          settings.setNavRail(it)
          host.activity.xgramTwoPane()?.update()
        }
      } else {
        switch(
          R.id.btn_xgramBottomNav, s(R.string.XgramBottomNavOption), s(R.string.XgramBottomNavOptionSub),
          checked = settings.bottomNav()
        ) {
          settings.setBottomNav(it)
          host.activity.xgramTwoPane()?.update()
        }
      }
      val hiddenItems = XgramComposeBridge.drawerHiddenCount()
      value(
        R.id.btn_xgramSideMenu, s(R.string.XgramSideMenu),
        if (hiddenItems > 0) s(R.string.XgramHiddenCount, hiddenItems) else s(R.string.XgramSideMenuSummary)
      ) {
        host.openPage(XgramPage.SIDE_MENU)
      }
      // The chat list's top row: the lock, ghost mode, Activity's bell and Search (the lock's and the bell's only switches)
      val hiddenButtons = com.fahadali.xgram.chats.XgramHeaderButtonsController.hiddenCount()
      value(
        R.id.btn_xgramHeaderButtons, s(R.string.XgramHeaderButtons),
        if (hiddenButtons > 0) s(R.string.XgramHiddenCount, hiddenButtons) else s(R.string.XgramAllShown)
      ) {
        host.openPage(XgramPage.HEADER_BUTTONS)
      }
      // New chat button: at the side, with the navigation bar (only while the bar is on) or hidden
      val newChat = XgramSettingDeps.newChatButton()
      value(R.id.btn_xgramHideFab, s(R.string.XgramNavBarFab), s(XgramSettingDeps.newChatButtonName(newChat))) {
        val choices = XgramSettingDeps.newChatButtonChoices().toList()
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramNavBarFab),
            options = choices.map { s(XgramSettingDeps.newChatButtonName(it)) to it },
            selected = newChat,
            subtitles = choices.map { s(XgramSettingDeps.newChatButtonInfo(it)) }
          ) { mode ->
            XgramSettingDeps.setNewChatButton(mode)
            host.activity.xgramTwoPane()?.update() // where the button goes; Hide reaches the chat list by itself
          }
        )
      }
      // Only the status bar text reads it, and Expressive design shows the connection in the app instead: not shown then.
      if (XgramSettingDeps.connectionStatusPositionEnabled()) {
        switch(
          R.id.btn_xgramConnectionStatusStart, s(R.string.XgramConnectionStatusStart), s(R.string.XgramConnectionStatusStartSub),
          checked = settings.connectionStatusAtStart()
        ) {
          settings.setConnectionStatusAtStart(it)
        }
      }
    }

    header(R.string.XgramChats)
    group {
      switch(R.id.btn_xgramMergeCommunities, s(R.string.XgramMergeCommunities), s(R.string.XgramMergeCommunitiesSub), checked = settings.mergeCommunities()) {
        settings.setMergeCommunities(it)
        XgramCommunities.notifyAllAccounts() // the setting is shared by every account
      }
      // All three start from Telegram X's Chat Previews (Interface): with it off, the reason instead
      val longPressReason = XgramSettingDeps.chatLongPressReason()
      buttons(
        R.id.btn_xgramChatLongPress, s(R.string.XgramChatLongPress),
        listOf(
          s(R.string.XgramChatLongPressPreview) to XgramChatLongPress.MODE_PREVIEW,
          s(R.string.XgramChatLongPressQuick) to XgramChatLongPress.MODE_QUICK,
          s(R.string.XgramChatLongPressCompact) to XgramChatLongPress.MODE_COMPACT
        ),
        selected = settings.chatLongPress(),
        enabled = longPressReason == 0,
        subtitle = if (longPressReason != 0) s(longPressReason) else null
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
  }
}

private const val CHAT_LIST_VIEW_KEY = "chatListView"

/**
 * The chat list view card (a native view, XgramChatListViewCard; Telegram X's Themes page has a row that opens this page)
 * in the group's shape, on the group's card color, pulsing to secondaryContainer when settings search points at it
 * (search knows the row by its id alone).
 */
@Composable
private fun ChatListViewCardRow(shapes: ListItemShapes) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  val highlight = LocalXgramHighlight.current
  val p = if (highlight != null && highlight.matches(R.id.btn_xgramChatListView, null)) highlight.progress.value else 0f
  val container = if (p > 0f) lerp(cs.surfaceBright, cs.secondaryContainer, p) else cs.surfaceBright
  Box(Modifier.fillMaxWidth().clip(shapes.shape).background(container)) {
    AndroidView(
      factory = { context ->
        XgramChatListViewCard(context).also { card ->
          card.setListener { mode ->
            Settings.instance().setChatListMode(mode)
            host.changed() // the switch below follows
          }
        }
      },
      update = { card ->
        // Read: the card follows a change made elsewhere (the classic popup) and every theme or Pure black change
        host.version
        host.themeVersion
        host.isDark
        card.setMode(Settings.instance().chatListMode, true)
        card.invalidateTiles()
      },
      modifier = Modifier.fillMaxWidth()
    )
  }
}
