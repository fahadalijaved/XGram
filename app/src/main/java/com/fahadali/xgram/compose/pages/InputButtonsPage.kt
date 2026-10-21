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
import com.fahadali.xgram.chats.XgramInputButtonsController
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/** Message box buttons: which buttons the message box shows. Open chats update at once. */
@Composable
fun InputButtonsPage() {
  SettingsPage(title = str(R.string.XgramInputButtons)) {
    val settings = XgramSettings.instance()

    description(R.string.XgramInputButtonsInfo)
    group {
      XgramInputButtonsController.KEYS.forEachIndexed { index, key ->
        switch(
          R.id.btn_xgramInputButton, s(XgramInputButtonsController.NAMES[index]),
          icon = XgramInputButtonsController.ICONS[index], checked = !settings.inputButtonHidden(key), key = key
        ) {
          settings.setInputButtonHidden(key, !it)
        }
      }
    }
    group {
      switch(
        R.id.btn_xgramInputAttachTyping, s(R.string.XgramInputAttachTyping), subtitle = s(R.string.XgramInputAttachTypingInfo),
        icon = R.drawable.deproko_baseline_attach_26, checked = settings.inputAttachWhileTyping()
      ) {
        settings.setInputAttachWhileTyping(it)
      }
    }
    description(R.string.XgramInputButtonsNote)
  }
}
