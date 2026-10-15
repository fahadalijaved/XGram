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

/** XGram's Compose settings pages. Never renumber: add new pages at the end. */
enum class XgramPage {
  MAIN, APPEARANCE, CHAT_LIST, MESSAGES, TRANSLATION, SIDE_MENU, PRIVACY, FILTERS, HISTORY, NETWORK, UPDATES, BACKUP,
  MESSAGE_MENU, CHAT_ACTIONS, INPUT_BUTTONS, TRANSLATE_TARGET, WORD_FILTERS, HIDDEN_PEOPLE, WHATS_NEW;

  companion object {
    /** The page of an XgramSettingsController.SECTION_* (Ghost mode is the top of Privacy). */
    @JvmStatic
    fun fromSection(section: Int): XgramPage = when (section) {
      XgramSettingsController.SECTION_APPEARANCE -> APPEARANCE
      XgramSettingsController.SECTION_GHOST, XgramSettingsController.SECTION_PRIVACY -> PRIVACY
      XgramSettingsController.SECTION_HISTORY -> HISTORY
      XgramSettingsController.SECTION_MESSAGES -> MESSAGES
      XgramSettingsController.SECTION_NETWORK -> NETWORK
      XgramSettingsController.SECTION_BACKUP -> BACKUP
      XgramSettingsController.SECTION_UPDATES -> UPDATES
      XgramSettingsController.SECTION_CHAT_LIST -> CHAT_LIST
      XgramSettingsController.SECTION_TRANSLATION -> TRANSLATION
      XgramSettingsController.SECTION_FILTERS -> FILTERS
      else -> MAIN
    }
  }
}
