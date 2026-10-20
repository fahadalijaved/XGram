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
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.appearance.XgramChatListViewCard
import com.fahadali.xgram.chats.XgramChatListMode
import com.fahadali.xgram.chats.XgramChatLongPress
import com.fahadali.xgram.chats.XgramCommunities
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.LocalXgramHighlight
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramNavToolbar
import org.thunderdog.challegram.R
import org.thunderdog.challegram.unsorted.Settings

/** Chat List: the chat list view, folders, merged communities, what a long press on a chat does, the screen around the list. */
@Composable
fun ChatListPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramChatList)) {
    val settings = XgramSettings.instance()
    val accountId = host.tdlib.accountId()

    // One line, two lines or three lines: the tiles pick it at once, the chat list springs to it
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

    // Activity: every mention, reply and reaction in one place (com.fahadali.xgram.activity)
    header(R.string.XgramActivity)
    group {
      switch(R.id.btn_xgramActivityShow, s(R.string.XgramActivityShow), s(R.string.XgramActivityShowInfo), checked = settings.activityOn()) {
        settings.setActivityOn(it)
      }
    }
    group {
      val activityReason = XgramSettingDeps.activityOptionsReason()
      val activityEnabled = activityReason == 0
      fun sub(info: Int): String = s(if (activityEnabled) info else activityReason)
      switch(R.id.btn_xgramActivityReactions, s(R.string.XgramActivityIncludeReactions), sub(R.string.XgramActivityIncludeReactionsInfo),
        checked = settings.activityReactions(), enabled = activityEnabled) {
        settings.setActivityReactions(it)
      }
      switch(R.id.btn_xgramActivityMuted, s(R.string.XgramActivityIncludeMuted), sub(R.string.XgramActivityIncludeMutedInfo),
        checked = settings.activityMuted(), enabled = activityEnabled) {
        settings.setActivityMuted(it)
      }
      switch(R.id.btn_xgramActivityDiscussion, s(R.string.XgramActivityIncludeDiscussion), sub(R.string.XgramActivityIncludeDiscussionInfo),
        checked = settings.activityDiscussion(), enabled = activityEnabled) {
        settings.setActivityDiscussion(it)
      }
      switch(R.id.btn_xgramActivityLookBack, s(R.string.XgramActivityLookBack), sub(R.string.XgramActivityLookBackInfo),
        checked = settings.activityLookBack(), enabled = activityEnabled) {
        settings.setActivityLookBack(it)
      }
      switch(R.id.btn_xgramActivityBadge, s(R.string.XgramActivityBadge), sub(R.string.XgramActivityBadgeInfo),
        checked = settings.activityBadge(), enabled = activityEnabled) {
        settings.setActivityBadge(it)
      }
      switch(R.id.btn_xgramActivityHeader, s(R.string.XgramActivityHeader), sub(R.string.XgramActivityHeaderInfo),
        checked = settings.activityHeader(), enabled = activityEnabled) {
        settings.setActivityHeader(it)
      }
      // The floating toolbar's Activity tab is chosen with the other tabs, on the Navigation bar page
      val activityTab = (settings.navTabs() and (1 shl XgramNavToolbar.DEST_ACTIVITY)) != 0
      value(R.id.btn_xgramActivityNavBar, s(R.string.XgramActivityNavBar),
        s(if (activityTab) R.string.XgramActivityNavBarTab else R.string.XgramActivityBarOff), enabled = activityEnabled) {
        host.openPage(XgramPage.NAV_BAR)
      }
    }
    description(R.string.XgramActivityNavBarInfo)
    group {
      val hidden = com.fahadali.xgram.activity.XgramActivityStore.instance().summary(accountId).hiddenCount()
      value(
        R.id.btn_xgramActivityHidden, s(R.string.XgramActivityHiddenChats),
        when (hidden) {
          0 -> s(R.string.XgramNone)
          1 -> s(R.string.XgramActivityHiddenCountOne)
          else -> s(R.string.XgramActivityHiddenCountMany, hidden)
        }
      ) {
        host.open(com.fahadali.xgram.activity.XgramActivityHiddenController(host.activity, host.tdlib))
      }
      action(R.id.btn_xgramActivityClearAll, s(R.string.XgramActivityClearAll), s(R.string.XgramActivityClearAllInfo), destructive = true) {
        host.confirm(ConfirmSpec(
          title = s(R.string.XgramActivityClearAllConfirm),
          text = s(R.string.XgramActivityClearAllInfo),
          confirm = s(R.string.XgramActivityClearAllButton),
          destructive = true
        ) {
          com.fahadali.xgram.activity.XgramActivityStore.instance().clearAll(accountId)
        })
      }
    }
    description(R.string.XgramActivityLocalNote)
    if (org.thunderdog.challegram.BuildConfig.DEBUG) {
      // Debug builds: a flood wait without asking Telegram for one (the sync, Mark all as read), and collector errors
      group {
        action(R.id.btn_xgramActivityDebugFlood, s(R.string.XgramActivityDebugFlood)) {
          com.fahadali.xgram.activity.XgramActivity.debugInjectFlood()
        }
        // Not a setting: a switch of this run only, so the page is told to build again
        switch(
          R.id.btn_xgramActivityDebugThrow, s(R.string.XgramActivityDebugThrow), s(R.string.XgramActivityDebugThrowInfo),
          checked = com.fahadali.xgram.activity.XgramActivity.debugCollectorThrows()
        ) {
          com.fahadali.xgram.activity.XgramActivity.setDebugCollectorThrows(it)
          host.changed()
        }
      }
    }

    header(R.string.XgramHeaderScreen)
    group {
      switch(R.id.btn_xgramHideFab, s(R.string.XgramHideFab), s(R.string.XgramHideFabSub), checked = settings.hideFab()) {
        settings.setHideFab(it)
      }
      // Only the status bar text reads it, and Expressive design shows the connection in the app instead.
      val statusReason = XgramSettingDeps.connectionStatusPositionReason()
      switch(
        R.id.btn_xgramConnectionStatusStart, s(R.string.XgramConnectionStatusStart),
        s(if (statusReason != 0) statusReason else R.string.XgramConnectionStatusStartSub),
        checked = settings.connectionStatusAtStart(), enabled = statusReason == 0
      ) {
        settings.setConnectionStatusAtStart(it)
      }
    }
  }
}

private const val CHAT_LIST_VIEW_KEY = "chatListView"

/**
 * The chat list view card (the same native view as on the Themes page) in the group's shape, on the group's card color,
 * pulsing to secondaryContainer when settings search points at it (search knows the row by its id alone).
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
        // Read: the card follows a change made elsewhere (Themes, the popup) and every theme or Pure black change
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
