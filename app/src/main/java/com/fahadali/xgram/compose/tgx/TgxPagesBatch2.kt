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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramMessageDetailsController
import com.fahadali.xgram.appearance.XgramR5SheetsInfo
import com.fahadali.xgram.appearance.XgramR6B5Pages
import com.fahadali.xgram.chats.XgramBookmarks
import com.fahadali.xgram.chats.XgramBookmarksController
import com.fahadali.xgram.chats.XgramSavedTopicsController
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.history.XgramHistory
import com.fahadali.xgram.history.XgramHistoryController
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.theme.ColorId
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.ui.FeatureToggles
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsBugController
import org.thunderdog.challegram.ui.SettingsCacheController
import org.thunderdog.challegram.ui.SettingsLogFilesController
import org.thunderdog.challegram.ui.SettingsNetworkStatsController
import org.thunderdog.challegram.ui.SettingsNotificationController

/**
 * Batch 2 (notifications, storage, debug and XGram lists): one line per controller, its hints after `to`
 * (TgxPageHints.DEFAULT, or an object overriding kind/trailing/largeTitle). Only this batch edits this file.
 *
 * SettingsBugController is drawn in Compose except as the crash recovery screen (MainActivity), which must not
 * depend on the Compose renderer: that instance declines through TgxPageHints.allow.
 */
internal object TgxPagesBatch2 {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    // Overrides the core's DEFAULT line for Notifications.
    SettingsNotificationController::class.java to Notifications,
    SettingsCacheController::class.java to TgxPageHints.DEFAULT,
    // Its counters are inert rows in normal colors (setIgnoreEnabled, then setEnabled(false)): the core draws them so.
    SettingsNetworkStatsController::class.java to TgxPageHints.DEFAULT,
    SettingsLogFilesController::class.java to TgxPageHints.DEFAULT,
    FeatureToggles.Controller::class.java to TgxPageHints.DEFAULT,
    XgramMessageDetailsController::class.java to MessageDetails,
    XgramBookmarksController::class.java to Bookmarks,
    XgramHistoryController::class.java to History,
    XgramSavedTopicsController::class.java to TgxChatRowsHints.INSTANCE,
    SettingsBugController::class.java to Bug,
  )

  /**
   * Bookmarks: each bookmark is a chat row: the chat's avatar, its title, the message's text on up to two lines (a
   * shimmer bar while it loads) and its date at the end. A long press opens the M3 menu (the controller).
   */
  private object Bookmarks : TgxPageHints {
    override fun kind(item: ListItem, view: View): TgxRowKind? =
      if (item.id == R.id.btn_xgramBookmarkItem && item.data is XgramBookmarks.Bookmark && view is SettingView) TgxRowKind.CHAT else null

    override fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val bookmark = item.data as? XgramBookmarks.Bookmark ?: return null
      val tdlib = c.tdlib()
      return { TgxChatAvatar { avatar -> avatar.setChat(tdlib, bookmark.chatId) } }
    }

    override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
      val page = c as? XgramBookmarksController ?: return null
      val bookmark = item.data as? XgramBookmarks.Bookmark ?: return null
      val preview = page.xgramPreviewOf(bookmark)
      return TgxRowText(c.tdlib().chatTitle(bookmark.chatId), preview, subtitleLines = 2, loading = preview == null)
    }

    override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val page = c as? XgramBookmarksController ?: return null
      val bookmark = item.data as? XgramBookmarks.Bookmark ?: return null
      val date = XgramR6B5Pages.shortDate(page.xgramDateOf(bookmark))
      if (date.isEmpty()) {
        return null
      }
      return { TgxTrailingLabel(date) }
    }
  }

  /**
   * History: a chat row has the chat's avatar, its title, "N messages" and the last deletion's date; a message row has
   * its sender's avatar, the sender as the headline, the text on up to two lines and a badge chip at the end:
   * "Deleted" in errorContainer or "Edited 2×" in secondaryContainer. A tap opens the versions sheet (the controller).
   */
  private object History : TgxPageHints {
    override fun kind(item: ListItem, view: View): TgxRowKind? =
      if (item.id == R.id.btn_xgramHistoryMessage && item.data is XgramHistory.Entry && view is SettingView) TgxRowKind.CHAT else null

    override fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val tdlib = c.tdlib()
      val data = item.data
      if (data is XgramHistory.ChatEntry) {
        val chatId = data.chatId
        return { TgxChatAvatar { avatar -> avatar.setChat(tdlib, chatId) } }
      }
      if (data is XgramHistory.Entry && data.senderId != 0L) {
        val senderId = data.senderId
        return { TgxChatAvatar { avatar -> avatar.setSender(tdlib, senderId) } }
      }
      return null
    }

    override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
      val page = c as? XgramHistoryController ?: return null
      val data = item.data
      if (data is XgramHistory.ChatEntry) {
        return TgxRowText(c.tdlib().chatTitle(data.chatId), Lang.plural(R.string.xMessages, data.count.toLong()))
      }
      if (data is XgramHistory.Entry) {
        return TgxRowText(page.xgramHeadlineOf(data), XgramHistoryController.xgramPreviewOf(data), subtitleLines = 2)
      }
      return null
    }

    override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val data = item.data
      if (data is XgramHistory.Entry) {
        if (data.deletedDate != 0) {
          val label = Lang.getString(R.string.XgramR6B5HistoryDeleted)
          return { TgxBadge(label, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
        }
        val label = Lang.getString(R.string.XgramR6B5HistoryEdited, data.edits.size)
        return { TgxBadge(label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
      }
      if (data is XgramHistory.ChatEntry && data.lastDeletedDate > 0) {
        val date = XgramR6B5Pages.shortDate(data.lastDeletedDate.toLong())
        return { TgxTrailingLabel(date) }
      }
      return null
    }
  }

  /**
   * Message details: a tap copies (the controller); a 40dp copy icon button at the end does the same. A value of two
   * parts (a title and its ID) is the headline and a supporting line with the row's label, in tabular figures.
   */
  private object MessageDetails : TgxPageHints {
    override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
      if (item.id != R.id.btn_xgramDetailsRow || view !is SettingView) {
        return null
      }
      val value = item.data as? String ?: return null
      val split = value.indexOf('\n')
      if (split <= 0) {
        return null // the value, its label under it, as read
      }
      val head = value.substring(0, split)
      val tail = value.substring(split + 1).replace('\n', ' ')
      val label = view.name?.toString().orEmpty()
      return TgxRowText(head, if (label.isEmpty()) tail else "$label · $tail", tabular = true)
    }

    override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      if (item.id != R.id.btn_xgramDetailsRow) {
        return null
      }
      val value = item.data as? String ?: return null
      return { TgxCopyButton(value) }
    }
  }

  /**
   * Notifications: the LED row stays a Compose row with its color as a swatch (the color dot alone would keep the
   * real view, and only from the bind on). It is read on every recomposition of the row, so it follows live changes.
   * Values Telegram X writes in red (notifications blocked by Android, importance "None") are red in the core.
   */
  private object Notifications : TgxPageHints {
    override fun kind(item: ListItem, view: View): TgxRowKind? {
      val id = item.id
      if ((id == R.id.btn_notifications_led || id == R.id.btn_customChat_led) && view is SettingView &&
        view.toggler == null && view.childCount == 0 && view.iconResource == 0 && !view.hasIcon() &&
        view.drawModifiers.isNullOrEmpty() && !view.hasUnreadCounter()) {
        return TgxRowKind.VALUE
      }
      return null
    }

    override fun trailing(item: ListItem, view: View): (@Composable () -> Unit)? {
      if (view !is SettingView) {
        return null
      }
      val color = view.colorDataId
      if (color != ColorId.NONE) {
        return { TgxColorSwatch(color) }
      }
      return null
    }
  }

  /** Debug and test settings; not the crash recovery screen, which has its own header (the crash's name). */
  private object Bug : TgxPageHints {
    override fun allow(c: ViewController<*>): Boolean = c.customHeaderCell == null
  }
}

/** A row's 40dp copy icon button (48dp target) in the trailing color: copies [value] and confirms in a snackbar. */
@Composable
private fun TgxCopyButton(value: String) {
  IconButton(onClick = { XgramR5SheetsInfo.copy(value) }) {
    Icon(tgxIcon(R.drawable.baseline_content_copy_24), contentDescription = Lang.getString(R.string.XgramR5SheetsCopyValue))
  }
}

/** A Telegram X theme color (LED colors) as a round swatch with a hairline, visible on any tile color. */
@Composable
private fun TgxColorSwatch(colorId: Int) {
  val outline = MaterialTheme.colorScheme.outlineVariant
  Box(
    modifier = Modifier
      .size(20.dp)
      .background(Color(Theme.getColor(colorId)), CircleShape)
      .border(1.dp, outline, CircleShape)
  )
}
