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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramMessageDetailsController
import com.fahadali.xgram.chats.XgramBookmarksController
import com.fahadali.xgram.chats.XgramSavedTopicsController
import com.fahadali.xgram.history.XgramHistoryController
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
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
    XgramMessageDetailsController::class.java to TgxPageHints.DEFAULT,
    XgramBookmarksController::class.java to TgxPageHints.DEFAULT,
    XgramHistoryController::class.java to TgxPageHints.DEFAULT,
    XgramSavedTopicsController::class.java to TgxPageHints.DEFAULT,
    SettingsBugController::class.java to Bug,
  )

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
