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
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib

/**
 * A settings search result: the page that holds the row (null: a row of the main page itself) and the row. [entry]: the
 * classic search's own row data (XgramSettingsSearch.Entry), for the classic XGram page's results. [pathParts]: the
 * crumbs of [path] one by one, so the universal search can set each in the app's direction. [open]: a page that isn't an
 * XGram settings page opens it itself (Message style), in both looks.
 */
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
  val path: String,
  val entry: Any? = null,
  val pathParts: List<String> = listOf(path),
  val open: ((ViewController<*>) -> ViewController<*>)? = null
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

  /** About › Version: the version and how updates stand. */
  @JvmStatic
  fun aboutVersionValue(activity: BaseActivity): String = XgramSettingsController.aboutVersionValue(activity.appUpdater())

  /**
   * The settings search index, read from the classic pages' own row lists (so every row is found, sub-pages too).
   * The section rows don't use controller state: a throwaway controller builds them, then stops listening.
   */
  @JvmStatic
  fun searchIndex(activity: BaseActivity, tdlib: Tdlib): List<XgramSearchHit> {
    val controller = XgramSettingsController(activity, tdlib)
    try {
      return searchHits(controller)
    } finally {
      controller.destroy()
    }
  }

  /** The index read from [controller]'s pages; each hit keeps its XgramSettingsSearch.Entry ([XgramSearchHit.entry]). */
  @JvmStatic
  fun searchHits(controller: XgramSettingsController): List<XgramSearchHit> =
    XgramSettingsSearch.buildIndex(controller).map { e ->
      val page: XgramPage? = when (e.subPage) {
        0 -> if (e.section == XgramSettingsController.SECTION_MAIN) null else XgramPage.fromSection(e.section)
        XgramSettingsController.SUB_SIDE_MENU -> XgramPage.SIDE_MENU
        XgramSettingsController.SUB_CHAT_ACTIONS -> XgramPage.CHAT_ACTIONS
        XgramSettingsController.SUB_MESSAGE_MENU -> XgramPage.MESSAGE_MENU
        XgramSettingsController.SUB_INPUT_BUTTONS -> XgramPage.INPUT_BUTTONS
        XgramSettingsController.SUB_WORD_FILTERS -> XgramPage.WORD_FILTERS
        XgramSettingsController.SUB_HIDDEN_PEOPLE -> XgramPage.HIDDEN_PEOPLE
        XgramSettingsController.SUB_HEADER_BUTTONS -> XgramPage.HEADER_BUTTONS
        XgramSettingsController.SUB_CHAT_MENU -> XgramPage.CHAT_MENU
        else -> XgramPage.fromSection(e.section)
      }
      XgramSearchHit(page, e.section, e.subPage, e.id, e.key, e.isToggle, e.iconRes, e.title, e.titleLower, e.path, e, e.pathParts)
    }

  /** The key a result is remembered by in the recent searches (the same since the first settings search). */
  @JvmStatic
  fun hitKey(hit: XgramSearchHit): String = "${hit.section}/${hit.subPage}/${hit.id}:${hit.key ?: ""}"

  /**
   * Opens a result's classic page (Expressive design off) from [from] and points at its row: a section page flashes
   * the row itself; a page further in flashes it once shown; a main page row opens its category, or the main page.
   */
  @JvmStatic
  fun openClassic(from: ViewController<*>, hit: XgramSearchHit) {
    val context = from.context()
    val tdlib = from.tdlib()
    if (hit.subPage != 0) {
      val c = XgramSettingsController.subPage(context, tdlib, hit.subPage)
      from.navigateTo(c)
      if (hit.id != 0) {
        XgramSettingsSearch.flashWhenShown(c, hit.id, hit.key)
      }
      return
    }
    if (hit.section != XgramSettingsController.SECTION_MAIN) {
      from.navigateTo(
        if (hit.id != 0) XgramSettingsController.section(context, tdlib, hit.section, hit.id) else XgramSettingsController.section(context, tdlib, hit.section)
      )
      return
    }
    val category = XgramSettingsController.sectionOfMainRow(hit.id)
    when {
      category != XgramSettingsController.SECTION_MAIN -> from.navigateTo(XgramSettingsController.section(context, tdlib, category))
      hit.id == R.id.btn_xgramWhatsNew -> from.navigateTo(XgramWhatsNewController(context, tdlib))
      else -> from.navigateTo(XgramSettingsController.section(context, tdlib, XgramSettingsController.SECTION_MAIN, hit.id))
    }
  }

  /** The settings pages themselves, as search shortcuts (shown before anything is typed), in Main's order. */
  @JvmStatic
  fun searchShortcuts(): List<XgramSearchHit> = XgramSettingsController.SEARCH_SECTIONS
    // The categories and App updates; a category's own pages (Large screens and foldables) are found by their rows
    .filter { it != XgramSettingsController.SECTION_MAIN && XgramSettingsController.sectionParent(it) == XgramSettingsController.SECTION_MAIN }
    .map { section ->
      val title = Lang.getString(XgramSettingsController.sectionTitleRes(section))
      XgramSearchHit(
        XgramPage.fromSection(section), section, 0, 0, null, false,
        XgramSettingsController.sectionIcon(section), title, title.lowercase(), ""
      )
    }
}
