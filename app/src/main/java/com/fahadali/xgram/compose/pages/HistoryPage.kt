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
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.history.XgramHistoryController
import org.thunderdog.challegram.R

/** Keeping days, 0 = forever (the classic page's choices). */
private val RETENTION_DAYS = listOf(7, 30, 365, 0)

private fun retentionName(days: Int): String =
  if (days == 0) s(R.string.XgramRetentionForever) else s(R.string.XgramRetentionDays, days)

/** History: the deleted and edited messages list, what XGram keeps, how it shows deleted ones, last seen. */
@Composable
fun HistoryPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramHistory)) {
    val settings = XgramSettings.instance()

    group {
      link(R.id.btn_xgramHistoryView, s(R.string.XgramHistoryView), s(R.string.XgramHistoryViewSub), icon = R.drawable.baseline_history_24) {
        host.open(XgramHistoryController(host.activity, host.tdlib))
      }
    }

    header(R.string.XgramHeaderSaving)
    group {
      switch(R.id.btn_xgramHistorySaveDeleted, s(R.string.XgramHistorySaveDeleted), checked = settings.historySaveDeleted()) {
        settings.setHistorySaveDeleted(it)
      }
      switch(
        R.id.btn_xgramHistorySaveMedia, s(R.string.XgramHistorySaveMedia), s(R.string.XgramHistorySaveMediaSub),
        checked = settings.historySaveMedia(), enabled = settings.historySaveDeleted()
      ) {
        if (settings.historySaveDeleted()) {
          settings.setHistorySaveMedia(it)
        }
      }
      switch(R.id.btn_xgramHistorySaveEdits, s(R.string.XgramHistorySaveEdits), checked = settings.historySaveEdits()) {
        settings.setHistorySaveEdits(it)
      }
      val days = settings.historyRetentionDays()
      value(R.id.btn_xgramHistoryRetention, s(R.string.XgramHistoryRetention), retentionName(days)) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramHistoryRetention),
            info = s(R.string.XgramHistoryRetentionInfo),
            options = RETENTION_DAYS.map { retentionName(it) to it },
            selected = days
          ) { picked ->
            settings.setHistoryRetentionDays(picked)
          }
        )
      }
    }
    description(R.string.XgramHistorySettingsInfo)

    header(R.string.XgramHeaderDisplay)
    group {
      switch(R.id.btn_xgramHistoryRedDeleted, s(R.string.XgramHistoryRedDeleted), s(R.string.XgramHistoryRedDeletedSub), checked = settings.historyRedDeleted()) {
        settings.setHistoryRedDeleted(it)
      }
    }

    header(R.string.XgramHeaderLastSeen)
    group {
      switch(R.id.btn_xgramSaveLastSeen, s(R.string.XgramSaveLastSeen), s(R.string.XgramSaveLastSeenSub), checked = settings.saveLastSeen()) {
        settings.setSaveLastSeen(it)
      }
    }
    description(R.string.XgramLastSeenInfo)
  }
}
