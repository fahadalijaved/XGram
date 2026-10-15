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
package com.fahadali.xgram

import com.fahadali.xgram.compose.XgramPage
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib

/** A settings search result: the page that holds the row (null: a row of the main page itself) and the row. */
class XgramSearchHit(
  val page: XgramPage?,
  val section: Int,
  val subPage: Int,
  val id: Int,
  val key: String?,
  val isToggle: Boolean,
  val icon: Int,
  val title: String,
  val titleLower: String,
  val path: String
)

/**
 * Public doors for the Compose pages (package com.fahadali.xgram.compose) to package-private parts of the classic
 * settings: value names and the settings search index. No behavior of its own.
 */
object XgramComposeBridge {
  @JvmStatic
  fun currentThemeName(): String = XgramSettingsController.currentThemeName()

  @JvmStatic
  fun emojiSetName(): String = XgramSettingsController.emojiSetName()?.toString() ?: ""

  @JvmStatic
  fun translateStyleName(): Int = XgramSettingsController.translateStyleName()

  @JvmStatic
  fun avatarCornersName(percent: Int): String = XgramSettingsController.avatarCornersName(percent)

  @JvmStatic
  fun messageMenuHiddenCount(): Int = XgramSettingsController.messageMenuHiddenCount()

  @JvmStatic
  fun drawerHiddenCount(): Int = XgramDrawerItemsController.hiddenCount()

  /**
   * The settings search index, read from the classic pages' own row lists (so every row is found, sub-pages too).
   * The section rows don't use controller state: a throwaway controller builds them, then stops listening.
   */
  @JvmStatic
  fun searchIndex(activity: BaseActivity, tdlib: Tdlib): List<XgramSearchHit> {
    val controller = XgramSettingsController(activity, tdlib)
    try {
      return XgramSettingsSearch.buildIndex(controller).map { e ->
        val page: XgramPage? = when (e.subPage) {
          0 -> if (e.section == XgramSettingsController.SECTION_MAIN) null else XgramPage.fromSection(e.section)
          XgramSettingsController.SUB_SIDE_MENU -> XgramPage.SIDE_MENU
          XgramSettingsController.SUB_CHAT_ACTIONS -> XgramPage.CHAT_ACTIONS
          XgramSettingsController.SUB_MESSAGE_MENU -> XgramPage.MESSAGE_MENU
          XgramSettingsController.SUB_INPUT_BUTTONS -> XgramPage.INPUT_BUTTONS
          XgramSettingsController.SUB_WORD_FILTERS -> XgramPage.WORD_FILTERS
          XgramSettingsController.SUB_HIDDEN_PEOPLE -> XgramPage.HIDDEN_PEOPLE
          else -> XgramPage.fromSection(e.section)
        }
        XgramSearchHit(page, e.section, e.subPage, e.id, e.key, e.isToggle, e.iconRes, e.title, e.titleLower, e.path)
      }
    } finally {
      controller.destroy()
    }
  }

  /** The settings pages themselves, as search shortcuts (shown before anything is typed), in Main's order. */
  @JvmStatic
  fun searchShortcuts(): List<XgramSearchHit> = XgramSettingsController.SEARCH_SECTIONS
    .filter { it != XgramSettingsController.SECTION_MAIN }
    .map { section ->
      val title = Lang.getString(XgramSettingsController.sectionTitleRes(section))
      XgramSearchHit(
        XgramPage.fromSection(section), section, 0, 0, null, false,
        XgramSettingsController.sectionIcon(section), title, title.lowercase(), ""
      )
    }
}
