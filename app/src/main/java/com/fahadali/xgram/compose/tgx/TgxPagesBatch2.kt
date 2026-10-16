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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramMessageDetailsController
import com.fahadali.xgram.chats.XgramBookmarksController
import com.fahadali.xgram.chats.XgramSavedTopicsController
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.history.XgramHistoryController
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.theme.ColorId
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.ui.FeatureToggles
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsCacheController
import org.thunderdog.challegram.ui.SettingsLogFilesController
import org.thunderdog.challegram.ui.SettingsNetworkStatsController
import org.thunderdog.challegram.ui.SettingsNotificationController

/**
 * Batch 2 (notifications, storage, debug and XGram lists): one line per controller, its hints after `to`
 * (TgxPageHints.DEFAULT, or an object overriding kind/trailing/largeTitle). Only this batch edits this file.
 *
 * SettingsBugController stays classic: it is also the crash-recovery screen (MainActivity), which must not depend on
 * the Compose renderer. It can join once TgxPageHints lets a page decline per instance (crash mode).
 */
internal object TgxPagesBatch2 {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    // Overrides the core's DEFAULT line for Notifications.
    SettingsNotificationController::class.java to Notifications,
    SettingsCacheController::class.java to TgxPageHints.DEFAULT,
    SettingsNetworkStatsController::class.java to NetworkStats,
    SettingsLogFilesController::class.java to TgxPageHints.DEFAULT,
    FeatureToggles.Controller::class.java to TgxPageHints.DEFAULT,
    XgramMessageDetailsController::class.java to TgxPageHints.DEFAULT,
    XgramBookmarksController::class.java to TgxPageHints.DEFAULT,
    XgramHistoryController::class.java to TgxPageHints.DEFAULT,
    XgramSavedTopicsController::class.java to TgxPageHints.DEFAULT,
  )

  /**
   * Notifications: the LED row stays a Compose row with its color as a swatch (the color dot alone would keep the
   * real view, and only from the bind on), and rows whose value Telegram X writes in red (notifications blocked by
   * Android, importance "None") get an error mark. Both are read on every recomposition of the row, so they follow
   * live changes.
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
      if (view.dataColorId == ColorId.textNegative) {
        val icon = if (TgxIcons.supported(view.context, R.drawable.baseline_error_24)) R.drawable.baseline_error_24 else 0
        return { TgxErrorMark(icon) }
      }
      return null
    }
  }

  /**
   * Network usage: its counters are inert rows in normal colors (setIgnoreEnabled, then setEnabled(false)). The
   * generic VALUE row would draw them disabled, so they keep the real view until the core draws such rows
   * enabled-looking; then this hint can go (DEFAULT).
   */
  private object NetworkStats : TgxPageHints {
    override fun kind(item: ListItem, view: View): TgxRowKind? =
      if (view is SettingView && !view.isEnabled && view.isVisuallyEnabled) TgxRowKind.FALLBACK else null
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

/** The trailing mark of a row whose value Telegram X shows in red. */
@Composable
private fun TgxErrorMark(icon: Int) {
  val error = MaterialTheme.colorScheme.error
  if (icon != 0) {
    Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = error)
  } else {
    Box(modifier = Modifier.size(10.dp).background(error, CircleShape))
  }
}
