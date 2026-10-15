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
import com.fahadali.xgram.chats.XgramMessageMenuController
import com.fahadali.xgram.compose.ClassicStubPage
import com.fahadali.xgram.compose.LocalXgramHost
import org.thunderdog.challegram.R

/** Stub until PAGES-1 builds this page (spec section 5): it opens the classic page. */
@Composable
fun MessageMenuPage() {
  val host = LocalXgramHost.current
  ClassicStubPage(R.string.XgramMessageMenu) { highlightId ->
    XgramMessageMenuController(host.activity, host.tdlib)
  }
}
