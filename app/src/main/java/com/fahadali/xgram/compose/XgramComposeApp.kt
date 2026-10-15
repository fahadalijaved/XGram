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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.fahadali.xgram.compose.theme.XgramM3Theme
import org.thunderdog.challegram.core.Lang

/** Root of a Compose page: theme, host, layout direction of Telegram X's language. */
@Composable
internal fun XgramComposeApp(host: XgramComposeHost) {
  XgramM3Theme(dark = host.isDark, themeVersion = host.themeVersion) {
    val direction = if (host.langVersion >= 0 && Lang.rtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalXgramHost provides host, LocalLayoutDirection provides direction) {
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Text(host.page.name)
      }
    }
  }
}
