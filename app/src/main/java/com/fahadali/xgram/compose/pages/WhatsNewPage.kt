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

import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.fahadali.xgram.XgramWhatsNewController
import com.fahadali.xgram.appearance.XgramThemesController
import com.fahadali.xgram.appicons.XgramAppIconsController
import com.fahadali.xgram.chats.XgramBookmarksController
import com.fahadali.xgram.chats.XgramSavedTopicsController
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.RowIcon
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.history.XgramHistoryController
import com.fahadali.xgram.later.XgramLaterController
import org.thunderdog.challegram.R

/** What XGram adds to Telegram X: each row opens the feature (settings pages here, the rest as Telegram X pages). */
@Composable
fun WhatsNewPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramWhatsNew)) {
    description(R.string.XgramWhatsNewIntro)
    group {
      for (entry in XgramWhatsNewController.ENTRIES) {
        val title = s(entry.title)
        val subtitle = s(entry.subtitle)
        val key = entry.title.toString()
        val action = entry.action
        val icon = entry.icon()
        if (action == XgramWhatsNewController.OPEN_NONE) {
          // Nothing to open: the group's list item without a click target.
          custom(key, R.id.btn_xgramWhatsNewItem) { shapes ->
            WhatsNewInfoRow(icon, title, subtitle, shapes, key)
          }
        } else {
          link(R.id.btn_xgramWhatsNewItem, title, subtitle, icon = icon, key = key) {
            openEntry(host, action)
          }
        }
      }
    }
    description(R.string.XgramWhatsNewOutro)
  }
}

/** The classic page's actions; settings pages open as Compose pages. */
private fun openEntry(host: XgramComposeHost, action: Int) {
  val activity = host.activity
  val tdlib = host.tdlib
  when (action) {
    XgramWhatsNewController.OPEN_ACTIVITY -> com.fahadali.xgram.activity.XgramActivity.open(host.controller)
    XgramWhatsNewController.OPEN_UNDO_SEND -> host.openPage(XgramPage.MESSAGES, R.id.btn_xgramUndoSend)
    XgramWhatsNewController.OPEN_CHECK_IN -> host.openPage(XgramPage.ACTIVITY, R.id.btn_xgramCheckInTimer) // it starts from a chat's menu
    XgramWhatsNewController.OPEN_LATER -> host.open(XgramLaterController(activity, tdlib))
    XgramWhatsNewController.OPEN_SETTINGS -> host.openPage(XgramPage.MAIN)
    XgramWhatsNewController.OPEN_THEMES -> host.open(XgramThemesController(activity, tdlib))
    XgramWhatsNewController.OPEN_GHOST, XgramWhatsNewController.OPEN_PRIVACY -> host.openPage(XgramPage.PRIVACY) // Ghost mode first
    XgramWhatsNewController.OPEN_APPEARANCE -> host.openPage(XgramPage.APPEARANCE)
    XgramWhatsNewController.OPEN_HISTORY -> host.open(XgramHistoryController(activity, tdlib))
    XgramWhatsNewController.OPEN_BOOKMARKS -> host.open(XgramBookmarksController(activity, tdlib))
    XgramWhatsNewController.OPEN_SAVED_BY_CHAT -> host.open(XgramSavedTopicsController(activity, tdlib))
    XgramWhatsNewController.OPEN_ICONS -> host.open(XgramAppIconsController(activity, tdlib))
    XgramWhatsNewController.OPEN_FILTERS -> host.openPage(XgramPage.FILTERS)
    else -> Unit
  }
}

/**
 * A row that only informs: the same SegmentedListItem as the rows that open something (icon, title, subtitle, the
 * card's color and the search highlight), without a click target, so the group is one list item style.
 */
@Composable
private fun WhatsNewInfoRow(@DrawableRes icon: Int, title: String, subtitle: String, shapes: ListItemShapes, key: String) {
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.semantics(mergeDescendants = true) { },
    leadingContent = if (icon != 0) {
      { RowIcon(icon) }
    } else {
      null
    },
    supportingContent = { Text(subtitle) },
    colors = settingsRowColors(R.id.btn_xgramWhatsNewItem, key),
    content = { Text(title) }
  )
}
