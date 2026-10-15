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
import com.fahadali.xgram.chats.XgramFilters
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Filters: sponsored messages and blocked users, then word filters and hidden people (pages further in). */
@Composable
fun FiltersPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramFilters)) {
    val settings = XgramSettings.instance()

    header(R.string.XgramHeaderHide)
    group {
      switch(R.id.btn_xgramHideSponsored, s(R.string.XgramHideSponsored), s(R.string.XgramHideSponsoredSub), checked = settings.hideSponsored()) {
        settings.setHideSponsored(it)
      }
      switch(R.id.btn_xgramHideBlocked, s(R.string.XgramHideBlocked), s(R.string.XgramHideBlockedSub), checked = settings.hideBlocked()) {
        settings.setHideBlocked(it)
      }
    }

    header(R.string.XgramHeaderWords)
    group {
      val count = XgramFilters.getFilters().size
      val countText = when (count) {
        0 -> s(R.string.XgramNone)
        1 -> s(R.string.XgramFilterCountOne)
        else -> s(R.string.XgramFilterCount, count)
      }
      value(R.id.btn_xgramFilters, s(R.string.XgramWordFilters), countText) {
        host.openPage(XgramPage.WORD_FILTERS)
      }
    }
    description(R.string.XgramFiltersInfo)

    header(R.string.XgramHeaderPeople)
    group {
      val count = XgramFilters.shadowBannedCount(host.tdlib)
      val countText = when (count) {
        0 -> s(R.string.XgramNone)
        1 -> s(R.string.XgramPeopleCountOne)
        else -> s(R.string.XgramPeopleCount, count)
      }
      value(R.id.btn_xgramHiddenPeople, s(R.string.XgramShadowBanned), countText) {
        host.openPage(XgramPage.HIDDEN_PEOPLE)
      }
    }
    description(R.string.XgramShadowBanInfo)
  }
}
