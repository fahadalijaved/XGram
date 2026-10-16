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
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.chats.XgramTranslateTargetController
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.translate.XgramTranslator
import org.thunderdog.challegram.R
import org.thunderdog.challegram.ui.SettingsLanguageTranslateController

/** Translation: the service, auto-translate and its language, Translate in the message menu, the translation style. */
@Composable
fun TranslationPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramTranslation)) {
    val settings = XgramSettings.instance()
    val provider = settings.translateProvider()
    // Worked out once per build of the page, not while rows are drawn.
    val styleName = s(XgramComposeBridge.translateStyleName())

    header(R.string.XgramHeaderService)
    group {
      value(R.id.btn_xgramTrProvider, s(R.string.XgramTrProvider), XgramTranslator.name(provider)) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramTrProvider),
            options = XgramTranslator.PROVIDERS.map { XgramTranslator.name(it) to it },
            selected = provider
          ) { picked ->
            pickProvider(host, settings, picked)
          }
        )
      }
      // Only a third-party service can fail over to Telegram.
      val fallbackReason = XgramSettingDeps.translateFallbackReason()
      switch(
        R.id.btn_xgramTrFallback, s(R.string.XgramTrFallback),
        s(if (fallbackReason != 0) fallbackReason else R.string.XgramTrFallbackSub),
        checked = settings.translateFallback(), enabled = fallbackReason == 0
      ) {
        settings.setTranslateFallback(it)
      }
    }
    // Who translates: changes with the service (also on Telegram X's translation page).
    description(XgramTranslator.info(provider), "trProviderInfo")

    header(R.string.XgramHeaderAutoTranslate)
    group {
      value(R.id.btn_xgramTrTarget, s(R.string.XgramTrTarget), XgramTranslateTargetController.currentName()) {
        host.openPage(XgramPage.TRANSLATE_TARGET)
      }
      switch(R.id.btn_xgramTrAutoAll, s(R.string.XgramTrAutoAll), checked = settings.translateAutoAll()) {
        settings.setTranslateAutoAll(it)
      }
    }
    description(R.string.XgramTrAutoAllInfo)

    header(R.string.XgramMessageMenu)
    group {
      switch(R.id.btn_xgramTrAlways, s(R.string.XgramTrAlways), s(R.string.XgramTrAlwaysSub), checked = settings.translateAlways()) {
        settings.setTranslateAlways(it)
      }
    }
    description(R.string.XgramTrAlwaysInfo)

    header(R.string.XgramHeaderStyle)
    group {
      value(R.id.btn_xgramTrStyle, s(R.string.XgramTrStyle), styleName) {
        host.open(SettingsLanguageTranslateController(host.activity, host.tdlib))
      }
    }
  }
}

/**
 * The same flow as XgramTranslateProviderPicker: a third-party service asks once, saying the text goes to it.
 * The sheet passes only a service other than the current one.
 */
private fun pickProvider(host: XgramComposeHost, settings: XgramSettings, provider: Int) {
  if (XgramTranslator.isThirdParty(provider) && !settings.translateNoticeShown(provider)) {
    host.confirm(
      ConfirmSpec(
        text = XgramTranslator.info(provider),
        confirm = s(R.string.XgramTrUse, XgramTranslator.name(provider))
      ) {
        settings.setTranslateNoticeShown(provider)
        settings.setTranslateProvider(provider)
      }
    )
  } else {
    settings.setTranslateProvider(provider)
  }
}
