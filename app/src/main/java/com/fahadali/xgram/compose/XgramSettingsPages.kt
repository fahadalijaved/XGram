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

import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.appearance.XgramExpressive
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib

/**
 * Opens XGram's settings: the Jetpack Compose pages when Expressive design is on, the classic pages
 * ([XgramSettingsController]) otherwise. Sections are XgramSettingsController.SECTION_* (Ghost mode opens Privacy).
 */
object XgramSettingsPages {
  @JvmStatic
  fun open(context: BaseActivity, tdlib: Tdlib, section: Int): ViewController<*> = open(context, tdlib, section, 0)

  /** [highlightId]: a row to scroll to and flash once shown (for example R.id.btn_xgramProxyOffVpn on Network). */
  @JvmStatic
  fun open(context: BaseActivity, tdlib: Tdlib, section: Int, highlightId: Int): ViewController<*> {
    return if (XgramExpressive.isEnabled()) {
      XgramComposeController.create(context, tdlib, XgramPage.fromSection(section), highlightId)
    } else if (highlightId != 0) {
      XgramSettingsController.section(context, tdlib, section, highlightId)
    } else {
      XgramSettingsController.section(context, tdlib, section)
    }
  }

  /** Settings → XGram. */
  @JvmStatic
  fun main(context: BaseActivity, tdlib: Tdlib): ViewController<*> = open(context, tdlib, XgramSettingsController.SECTION_MAIN)

  /** Ghost mode settings: Privacy, whose first part is Ghost mode. */
  @JvmStatic
  fun ghost(context: BaseActivity, tdlib: Tdlib): ViewController<*> = open(context, tdlib, XgramSettingsController.SECTION_PRIVACY)
}
