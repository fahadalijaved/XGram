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
package com.fahadali.xgram.compose.tgx

import android.view.View
import androidx.compose.runtime.Composable
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsDataController
import org.thunderdog.challegram.ui.SettingsLanguageTranslateController
import org.thunderdog.challegram.ui.SettingsLogOutController
import org.thunderdog.challegram.ui.SettingsNotificationController
import org.thunderdog.challegram.ui.SettingsPrivacyController
import org.thunderdog.challegram.ui.SettingsThemeController

/** How one row of a Telegram X page is drawn in Compose. */
enum class TgxRowKind {
  /** Shadows, separators and offsets: not drawn (groups and gaps replace them). */
  HIDDEN,
  SWITCH, RADIO, CHECKBOX,
  /**
   * A switch row with two targets (its toggler has a click of its own): the row opens or expands something, the
   * switch after a divider toggles (EditRights' Send media group).
   */
  SPLIT_SWITCH,
  /** A row that opens something or shows a value (also destructive actions, by text color). */
  VALUE,
  HEADER, DESCRIPTION,
  /** A header with an icon button, a text button or a checkbox at its end (Telegram X's HEADER_WITH_* rows). */
  HEADER_ACTION,
  /** Telegram X's full-width text button: an action row in the primary color. */
  BUTTON,
  SLIDER, BUILD_NO, LOADING, EMPTY,
  /** The real Telegram X view in the row's place (AndroidView): users, chats, edit fields, previews, custom rows. */
  FALLBACK
}

/**
 * What a page may change about its Compose rows. Every method has a default: [TgxPageHints.DEFAULT] opts a page in as
 * it is. Hints are Kotlin only; the controller itself stays untouched.
 */
interface TgxPageHints {
  /**
   * Whether this instance of the page is drawn in Compose (false: the classic list). For pages that are also used
   * where they must not depend on Compose, such as the crash recovery screen.
   */
  fun allow(c: ViewController<*>): Boolean = true

  /** The page title as the first list item, fading Telegram X's small title in as it scrolls away. */
  fun largeTitle(c: ViewController<*>): Boolean = c.xgramAllowsLargeTitle()

  /** How to draw [item] (bound to [view]); null: the generic mapping. FALLBACK keeps the real view. */
  fun kind(item: ListItem, view: View): TgxRowKind? = null

  /** Extra trailing content of a VALUE row (an avatar stack, a preview); null: none. */
  fun trailing(item: ListItem, view: View): (@Composable () -> Unit)? = null

  /**
   * Whether a switch row is only partly on (a group with some of its options on): drawn as the switch's third state.
   * Read with the row, after every change the page makes to it.
   */
  fun isPartial(c: ViewController<*>, item: ListItem, view: View): Boolean = false

  companion object {
    @JvmField
    val DEFAULT: TgxPageHints = object : TgxPageHints {}
  }
}

/**
 * The Telegram X pages drawn in Compose with Expressive design on. A page is listed by its exact class (a subclass is
 * a page of its own). Each batch adds one line per controller to its own file (TgxPagesBatch1..3):
 *
 *     SettingsPhoneController::class.java to TgxPageHints.DEFAULT,
 */
object TgxComposePages {
  // The core's pages: the pilots and the most visible Settings pages.
  private val core: Map<Class<*>, TgxPageHints> = mapOf(
    SettingsPrivacyController::class.java to TgxPageHints.DEFAULT,
    SettingsDataController::class.java to TgxPageHints.DEFAULT,
    SettingsNotificationController::class.java to TgxPageHints.DEFAULT,
    SettingsThemeController::class.java to TgxPageHints.DEFAULT,
    SettingsLanguageTranslateController::class.java to TgxPageHints.DEFAULT,
    SettingsLogOutController::class.java to TgxPageHints.DEFAULT,
  )

  private val pages: Map<Class<*>, TgxPageHints> by lazy {
    HashMap<Class<*>, TgxPageHints>().apply {
      putAll(core)
      putAll(TgxPagesBatch1.pages)
      putAll(TgxPagesBatch2.pages)
      putAll(TgxPagesBatch3.pages)
    }
  }

  /** The hints of [c]'s page, or null when the page keeps its classic list. */
  @JvmStatic
  fun hints(c: ViewController<*>): TgxPageHints? = pages[c.javaClass]
}
