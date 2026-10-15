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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.compose.pages.AppearancePage
import com.fahadali.xgram.compose.pages.BackupPage
import com.fahadali.xgram.compose.pages.ChatActionsPage
import com.fahadali.xgram.compose.pages.ChatListPage
import com.fahadali.xgram.compose.pages.FiltersPage
import com.fahadali.xgram.compose.pages.HiddenPeoplePage
import com.fahadali.xgram.compose.pages.HistoryPage
import com.fahadali.xgram.compose.pages.InputButtonsPage
import com.fahadali.xgram.compose.pages.MainPage
import com.fahadali.xgram.compose.pages.MessageMenuPage
import com.fahadali.xgram.compose.pages.MessagesPage
import com.fahadali.xgram.compose.pages.NetworkPage
import com.fahadali.xgram.compose.pages.PrivacyPage
import com.fahadali.xgram.compose.pages.SideMenuPage
import com.fahadali.xgram.compose.pages.TranslateTargetPage
import com.fahadali.xgram.compose.pages.TranslationPage
import com.fahadali.xgram.compose.pages.UpdatesPage
import com.fahadali.xgram.compose.pages.WhatsNewPage
import com.fahadali.xgram.compose.pages.WordFiltersPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.ViewController

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

/** Shows a page. Each page lives in its own file in compose/pages and takes no parameters. */
@Composable
internal fun XgramPageContent(page: XgramPage) {
  when (page) {
    XgramPage.MAIN -> MainPage()
    XgramPage.APPEARANCE -> AppearancePage()
    XgramPage.CHAT_LIST -> ChatListPage()
    XgramPage.MESSAGES -> MessagesPage()
    XgramPage.TRANSLATION -> TranslationPage()
    XgramPage.SIDE_MENU -> SideMenuPage()
    XgramPage.PRIVACY -> PrivacyPage()
    XgramPage.FILTERS -> FiltersPage()
    XgramPage.HISTORY -> HistoryPage()
    XgramPage.NETWORK -> NetworkPage()
    XgramPage.UPDATES -> UpdatesPage()
    XgramPage.BACKUP -> BackupPage()
    XgramPage.MESSAGE_MENU -> MessageMenuPage()
    XgramPage.CHAT_ACTIONS -> ChatActionsPage()
    XgramPage.INPUT_BUTTONS -> InputButtonsPage()
    XgramPage.TRANSLATE_TARGET -> TranslateTargetPage()
    XgramPage.WORD_FILTERS -> WordFiltersPage()
    XgramPage.HIDDEN_PEOPLE -> HiddenPeoplePage()
    XgramPage.WHATS_NEW -> WhatsNewPage()
  }
}

/**
 * A page that is not rebuilt in Compose yet: a note and a row that opens the classic page ([open] gets the row to
 * point at, from search or a shortcut). Replaced by the page's own content.
 */
@Composable
fun ClassicStubPage(@StringRes title: Int, open: (highlightId: Int) -> ViewController<*>) {
  val host = LocalXgramHost.current
  val highlightId = remember { host.highlightId }
  SettingsPage(title = str(title)) {
    description(s(R.string.XgramComposeStubInfo), "stub")
    group {
      link(id = 0, title = s(R.string.XgramComposeOpenClassic)) {
        host.open(open(highlightId))
      }
    }
  }
}
