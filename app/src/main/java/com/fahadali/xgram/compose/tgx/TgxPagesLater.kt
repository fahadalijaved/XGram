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
package com.fahadali.xgram.compose.tgx

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.fahadali.xgram.later.XgramLaterController
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.ListItem

/**
 * Later (XGram): each item is a chat row: the chat's avatar, its title, what the item is about on up to two lines (a
 * shimmer while the message's text loads) and at the end its time or a badge chip: Due in tertiaryContainer, Replied in
 * secondaryContainer, Deleted and Unavailable in errorContainer. Headers, the status rows and the Done link keep the
 * generic mapping. A long press opens the M3 menu (the controller).
 */
internal object TgxPagesLater {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    XgramLaterController::class.java to Later,
  )

  private object Later : TgxPageHints {
    override fun kind(item: ListItem, view: View): TgxRowKind? =
      if (item.id == R.id.btn_xgramLaterItem && item.data is XgramLaterController.Row && view is SettingView) TgxRowKind.CHAT else null

    override fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val row = item.data as? XgramLaterController.Row ?: return null
      val tdlib = c.tdlib()
      val chatId = row.record.chatId
      return { TgxChatAvatar { avatar -> avatar.setChat(tdlib, chatId) } }
    }

    override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
      val page = c as? XgramLaterController ?: return null
      val row = item.data as? XgramLaterController.Row ?: return null
      val loading = page.xgramIsLoading(row)
      return TgxRowText(page.xgramTitleOf(row), page.xgramSubtitleOf(row), subtitleLines = 2, loading = loading)
    }

    override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val page = c as? XgramLaterController ?: return null
      val row = item.data as? XgramLaterController.Row ?: return null
      val badge = page.xgramBadgeOf(row)
      if (badge != XgramLaterController.BADGE_NONE) {
        val label = XgramLaterController.badgeText(badge)
        if (badge == XgramLaterController.BADGE_DUE) {
          return { TgxBadge(label, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
        }
        if (badge == XgramLaterController.BADGE_REPLIED) {
          return { TgxBadge(label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        return { TgxBadge(label, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
      }
      val time = page.xgramTrailingOf(row) ?: return null
      if (time.isEmpty()) {
        return null
      }
      return { TgxTrailingLabel(time) }
    }
  }
}
