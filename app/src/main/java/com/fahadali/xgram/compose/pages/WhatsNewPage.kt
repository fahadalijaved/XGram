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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramWhatsNewController
import com.fahadali.xgram.appearance.XgramThemesController
import com.fahadali.xgram.appicons.XgramAppIconsController
import com.fahadali.xgram.chats.XgramBookmarksController
import com.fahadali.xgram.chats.XgramSavedTopicsController
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.history.XgramHistoryController
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
        if (action == XgramWhatsNewController.OPEN_NONE) {
          // Nothing to open: a plain row in the group's shape, not a button.
          custom(key, R.id.btn_xgramWhatsNewItem) { shapes ->
            WhatsNewInfoRow(entry.icon, title, subtitle, shapes)
          }
        } else {
          link(R.id.btn_xgramWhatsNewItem, title, subtitle, icon = entry.icon, key = key) {
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

/** A row that only informs: the look of the group's list items (icon, title, subtitle) without a click target. */
@Composable
private fun WhatsNewInfoRow(@DrawableRes icon: Int, title: String, subtitle: String, shapes: ListItemShapes) {
  val cs = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    shape = shapes.shape,
    color = cs.surfaceBright,
    contentColor = cs.onSurface
  ) {
    Row(
      modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = cs.onSurfaceVariant)
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
      }
    }
  }
}
