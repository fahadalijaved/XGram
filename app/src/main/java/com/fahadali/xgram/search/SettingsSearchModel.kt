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
package com.fahadali.xgram.search

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.fahadali.xgram.XgramSearchHit
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsController

/** Where a search was opened from: results of that part rank a little higher, and the shortcuts differ. */
object SettingsSearch {
  /** The main Settings screen. */
  const val ORIGIN_SETTINGS = 0
  /** Settings > XGram. */
  const val ORIGIN_XGRAM = 1

  /** [RowRef.longId]: any value. */
  const val ANY = Long.MIN_VALUE
}

/**
 * A row of a Telegram X page, as its SettingsAdapter list holds it: the id, plus what tells apart rows that share it
 * (the long id, the string resource or the data). With [id] 0, the first row under the header [afterHeader].
 * [reveal]: a row the page must expand first (Data and storage: Advanced).
 */
class RowRef(
  @IdRes val id: Int = 0,
  val longId: Long = SettingsSearch.ANY,
  @StringRes val stringRes: Int = 0,
  val data: ((Any?) -> Boolean)? = null,
  @StringRes val afterHeader: Int = 0,
  @IdRes val reveal: Int = 0
) {
  /** The row's position in [items], or -1 while the page does not show it (yet). */
  fun indexIn(items: List<ListItem>): Int {
    if (id != 0) {
      for (i in items.indices) {
        val item = items[i]
        if (item.id == id && matches(item)) {
          return i
        }
      }
      return -1
    }
    if (afterHeader != 0) {
      var header = -1
      for (i in items.indices) {
        val item = items[i]
        val type = item.viewType
        if (isHeader(type)) {
          if (header >= 0) {
            break // the next group: the header had no row under it
          }
          if (item.stringResource == afterHeader) {
            header = i
          }
          continue
        }
        if (header >= 0 && item.id != 0 && isRow(type)) {
          return i
        }
      }
      return header
    }
    return -1
  }

  private fun matches(item: ListItem): Boolean {
    if (longId != SettingsSearch.ANY && item.longId != longId) {
      return false
    }
    if (stringRes != 0 && item.stringResource != stringRes) {
      return false
    }
    val test = data
    return test == null || test(item.data)
  }

  private fun isHeader(type: Int): Boolean =
    type == ListItem.TYPE_HEADER || type == ListItem.TYPE_HEADER_PADDED || type == ListItem.TYPE_HEADER_WITH_ACTION ||
      type == ListItem.TYPE_HEADER_WITH_TEXT_BUTTON || type == ListItem.TYPE_HEADER_WITH_CHECKBOX

  private fun isRow(type: Int): Boolean = when (type) {
    ListItem.TYPE_SHADOW_TOP, ListItem.TYPE_SHADOW_BOTTOM, ListItem.TYPE_SEPARATOR, ListItem.TYPE_SEPARATOR_FULL,
    ListItem.TYPE_DESCRIPTION, ListItem.TYPE_EMPTY_OFFSET, ListItem.TYPE_EMPTY_OFFSET_SMALL -> false
    else -> true
  }
}

/** What the index knows about the account and the look while it is built (never sent to TDLib). */
class SearchEnv(val tdlib: Tdlib, val expressive: Boolean)

/** Where a result opens: the search page (the top of the stack) and its account. */
class OpenCtx(val from: ViewController<*>) {
  val context: BaseActivity = from.context()
  val tdlib: Tdlib = from.tdlib()

  /** The main Settings screen below the search page, if any (the Stickers page reads its sticker sets). */
  fun settings(): SettingsController? {
    val count = from.stackSize()
    for (i in 0 until count) {
      val c = from.stackItemAt(i)
      if (c is SettingsController && !c.isDestroyed) {
        return c
      }
    }
    return null
  }
}

/** How a page opened. */
sealed class Opened {
  /** A page to push on the search page's stack. */
  class Push(val c: ViewController<*>) : Opened()

  /** [start] shows something first (the passcode lock): the row is pointed at once a page that [match]es shows. */
  class Gated(val match: (ViewController<*>) -> Boolean, val start: () -> Unit) : Opened()

  /** The page can't open from here: open its parent and point at the row that opens it. */
  object Parent : Opened()

  /** The main Settings screen (back to it, or a new one). */
  object MainSettings : Opened()

  /** The page opens by itself later (a tester check, then a warning): nothing to point at. */
  object Done : Opened()
}

/** A switch on a result (Night mode): [toggle] flips it; a tap on the rest of the row opens the page. */
class InlineToggle(val checked: () -> Boolean, val toggle: () -> Unit)

/** A Telegram X settings page: its title, where it is ([parent], [parentRow]) and how it opens. */
class TgxPage internal constructor(
  val key: String,
  @StringRes val title: Int,
  @StringRes val sentence: Int,
  @DrawableRes val icon: Int,
  val parent: TgxPage?,
  val parentRow: RowRef?,
  val kw: IntArray,
  val visible: (SearchEnv) -> Boolean,
  val open: (OpenCtx) -> Opened
)

/**
 * One result of the Telegram X part of the index. Listed on [page] (its path); it opens [target] (a page entry) or
 * [page], then points at [row]. [open] replaces the page's own opening (an editor the row opens).
 */
class TgxRow internal constructor(
  val page: TgxPage,
  val key: String,
  @StringRes val title: Int,
  @StringRes val sentence: Int,
  val row: RowRef?,
  @DrawableRes val icon: Int,
  @StringRes val group: Int,
  val kw: IntArray,
  val visible: (SearchEnv) -> Boolean,
  val soft: Boolean,
  val target: TgxPage?,
  val open: ((OpenCtx) -> Opened)?,
  val toggle: InlineToggle?,
  val titleText: (() -> String)?
)

/** What a result opens. */
sealed class SearchTarget {
  class Tgx(val row: TgxRow) : SearchTarget()
  class Xgram(val hit: XgramSearchHit) : SearchTarget()
}

/** A text as the search compares it (see SettingsSearchEngine.normalize) and, per character, where it came from. */
class NormText(val text: String, val map: IntArray) {
  /** Where each word starts in [text]. */
  val wordStarts: IntArray = run {
    var n = 0
    for (i in text.indices) {
      if (text[i] != ' ' && (i == 0 || text[i - 1] == ' ')) n++
    }
    val starts = IntArray(n)
    var k = 0
    for (i in text.indices) {
      if (text[i] != ' ' && (i == 0 || text[i - 1] == ' ')) starts[k++] = i
    }
    starts
  }
}

/** One searchable setting or page, built once per index (titles in the app language). */
class SearchEntry internal constructor(
  /** Kept in the recent searches: "t:..." for Telegram X, "section/subPage/id:key" for XGram. */
  val key: String,
  val title: String,
  /** Where it is: the pages below Settings, then the group. Bidi-isolated per page in RTL. */
  val path: String,
  @DrawableRes val icon: Int,
  val isPage: Boolean,
  val isXgram: Boolean,
  val order: Int,
  val target: SearchTarget,
  internal val norm: NormText,
  internal val altWords: Array<String>,
  internal val pathWords: Array<String>
) {
  val toggle: InlineToggle?
    get() = (target as? SearchTarget.Tgx)?.row?.toggle

  /** The classic XGram search's own row data (XgramSettingsSearch.Entry), when this is one of its rows. */
  val xgramEntry: Any?
    get() = (target as? SearchTarget.Xgram)?.hit?.entry
}

/** A matching entry: its score and the parts of its title that matched, as [start, end) pairs. */
class SearchHit(val entry: SearchEntry, val score: Int, val ranges: IntArray)
