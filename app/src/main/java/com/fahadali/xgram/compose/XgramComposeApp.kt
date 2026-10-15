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
package com.fahadali.xgram.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.fahadali.xgram.compose.theme.XgramM3Theme
import com.fahadali.xgram.compose.ui.XgramDialogSlot
import org.thunderdog.challegram.core.Lang

/** Root of a Compose page: theme, host, Telegram X's layout direction, the page and its sheet or dialog. */
@Composable
internal fun XgramComposeApp(host: XgramComposeHost) {
  XgramM3Theme(dark = host.isDark, themeVersion = host.themeVersion) {
    val direction = if (host.langVersion >= 0 && Lang.rtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalXgramHost provides host, LocalLayoutDirection provides direction) {
      XgramPageContent(host.page)
      XgramDialogSlot()
    }
  }
}
