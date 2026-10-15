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
import com.fahadali.xgram.chats.XgramFilters
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Filters → Word filters: add a word, phrase or pattern; tap one to remove it. Same rules as the classic page. */
@Composable
fun WordFiltersPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramWordFilters)) {
    header(R.string.XgramFilterWords)
    group {
      action(R.id.btn_xgramAddFilter, s(R.string.XgramAddFilter), icon = R.drawable.baseline_add_24) {
        host.prompt(
          PromptSpec(title = s(R.string.XgramAddFilter), hint = s(R.string.XgramFilterHint), confirm = s(R.string.Save)) { result ->
            val text = result.trim()
            if (text.isNotEmpty()) {
              val filters = XgramFilters.getFilters()
              if (!filters.contains(text)) {
                filters.add(text)
                XgramFilters.setFilters(filters)
              }
            }
          }
        )
      }
      for (filter in XgramFilters.getFilters()) {
        // The words are the user's own text: one row each, tap to remove.
        action(R.id.btn_xgramFilter, filter, key = filter) {
          host.confirm(
            ConfirmSpec(text = filter, confirm = s(R.string.XgramRemoveFilter), destructive = true) {
              val filters = XgramFilters.getFilters()
              filters.remove(filter)
              XgramFilters.setFilters(filters)
            }
          )
        }
      }
    }
    description(R.string.XgramFiltersInfo)
  }
}
