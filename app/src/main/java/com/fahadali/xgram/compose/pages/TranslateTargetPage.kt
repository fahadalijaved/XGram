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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.chats.XgramAutoTranslate
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsRadioRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * Translate to: the app's language, or one of Telegram's translation languages. A long list, so every language is an
 * item of the page's lazy list (a segmented row with its place's corners) instead of one big group.
 */
@Composable
fun TranslateTargetPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramTrTarget), large = false) {
    val settings = XgramSettings.instance()
    val current = settings.translateTarget()

    description(R.string.XgramTrTargetInfo)
    group {
      radio(
        R.id.btn_xgramTrTargetLanguage, s(R.string.XgramTrTargetAppTitle), selected = current.isEmpty(),
        subtitle = XgramAutoTranslate.languageName(XgramAutoTranslate.appLanguage()), key = ""
      ) {
        if (current.isNotEmpty()) {
          settings.setTranslateTarget("")
        }
      }
    }

    val codes = Lang.getSupportedLanguagesForTranslate()
    val count = codes.size
    codes.forEachIndexed { index, code ->
      val title = XgramAutoTranslate.languageName(code)
      val selected = code == current
      item("lang:$code") {
        // 16dp between the two cards, the segmented gap between rows of the list.
        Box(Modifier.fillMaxWidth().padding(top = if (index == 0) 16.dp else ListItemDefaults.SegmentedGap)) {
          val nativeName = remember(code) { nativeName(code) }
          SettingsRadioRow(
            title = title,
            subtitle = nativeName,
            selected = selected,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            id = R.id.btn_xgramTrTargetLanguage,
            key = code
          ) {
            if (code != XgramSettings.instance().translateTarget()) {
              XgramSettings.instance().setTranslateTarget(code)
              host.changed()
            }
          }
        }
      }
    }
  }
}

/** The language's own name, as on the classic page (its code when Telegram X doesn't know it). */
private fun nativeName(code: String): String {
  val info = TdApi.LanguagePackInfo()
  val name: String? = if (Lang.fixLanguageCode(code, info)) info.nativeName else null
  return if (name.isNullOrEmpty()) code else name
}
