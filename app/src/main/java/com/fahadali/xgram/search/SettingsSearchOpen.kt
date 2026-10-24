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

import android.util.Log
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSearchHit
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.compose.XgramComposeController
import com.fahadali.xgram.compose.XgramPage
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.SettingsController

/**
 * Opens a settings search result from the search page: the Telegram X page or XGram page that holds it, in the search
 * page's own stack (the right pane with two panes), then points at the row. A result never changes a setting itself;
 * destructive and outbound rows are only pointed at.
 */
object SettingsSearchOpen {
  private const val TAG = "XGram"

  /** How long a page may take to show (a slow page, a TDLib answer). */
  private const val SHOW_TIMEOUT_MS = 10_000L

  /** The passcode lock first: the user may take a while to unlock. */
  private const val GATE_TIMEOUT_MS = 60_000L

  /** [record]: keep it in the recent searches (not for the "Go to" pages). */
  @JvmStatic
  @JvmOverloads
  fun open(from: ViewController<*>, entry: SearchEntry, record: Boolean = true) {
    if (from.isDestroyed) {
      return
    }
    if (record) {
      XgramSettings.instance().addSearchRecent(entry.key)
    }
    try {
      when (val target = entry.target) {
        is SearchTarget.Tgx -> openTgx(from, target.row)
        is SearchTarget.Xgram -> openXgram(from, target.hit)
      }
    } catch (t: Throwable) {
      XgramSearchHighlight.cancel()
      Log.e(TAG, "settings search: cannot open ${entry.key}", t)
    }
  }

  @JvmStatic
  fun open(from: ViewController<*>, hit: SearchHit) = open(from, hit.entry, true)

  // Telegram X

  private fun openTgx(from: ViewController<*>, row: TgxRow) {
    val ctx = OpenCtx(from)
    val custom = row.open
    if (custom != null) {
      handle(ctx, custom(ctx), row.page, row.row, row.key, row.soft)
      return
    }
    val target = row.target
    if (target != null) {
      openPage(ctx, target, null, row.key, false)
    } else {
      openPage(ctx, row.page, row.row, row.key, row.soft)
    }
  }

  private fun openPage(ctx: OpenCtx, page: TgxPage, pulse: RowRef?, key: String, soft: Boolean) {
    handle(ctx, page.open(ctx), page, pulse, key, soft)
  }

  private fun handle(ctx: OpenCtx, opened: Opened, page: TgxPage, pulse: RowRef?, key: String, soft: Boolean) {
    when (opened) {
      is Opened.Push -> {
        val c = opened.c
        if (pulse != null) {
          XgramSearchHighlight.expect({ it === c }, pulse, key, soft, SHOW_TIMEOUT_MS)
        }
        if (!ctx.from.navigateTo(c)) {
          XgramSearchHighlight.cancel()
        }
      }
      is Opened.Gated -> {
        if (pulse != null) {
          XgramSearchHighlight.expect(opened.match, pulse, key, soft, GATE_TIMEOUT_MS)
        }
        opened.start()
      }
      is Opened.Parent -> {
        // The page can't open from here: its parent, pointing at the row that opens it
        val parent = page.parent ?: return
        openPage(ctx, parent, page.parentRow, key, false)
      }
      is Opened.MainSettings -> openMainSettings(ctx, pulse, key, soft)
      is Opened.Done -> Unit
    }
  }

  /** Back to the Settings screen right below the search page, or a new one. */
  private fun openMainSettings(ctx: OpenCtx, pulse: RowRef?, key: String, soft: Boolean) {
    val from = ctx.from
    val size = from.stackSize()
    val below = if (size >= 2 && from.stackItemAt(size - 1) === from) from.stackItemAt(size - 2) else null
    if (below is SettingsController && !below.isDestroyed) {
      if (pulse != null) {
        XgramSearchHighlight.expect({ it === below }, pulse, key, soft, SHOW_TIMEOUT_MS)
      }
      if (!from.navigateBack()) {
        XgramSearchHighlight.cancel()
      }
      return
    }
    val c = TgxSearchOpen.mainSettings(ctx.context, ctx.tdlib)
    if (pulse != null) {
      XgramSearchHighlight.expect({ it === c }, pulse, key, soft, SHOW_TIMEOUT_MS)
    }
    if (!from.navigateTo(c)) {
      XgramSearchHighlight.cancel()
    }
  }

  // XGram

  private fun openXgram(from: ViewController<*>, hit: XgramSearchHit) {
    val open = hit.open
    if (open != null) {
      from.navigateTo(open(from)) // a page that isn't an XGram settings page (Message style), the same in both looks
      return
    }
    if (!XgramExpressive.isEnabled()) {
      XgramComposeBridge.openClassic(from, hit)
      return
    }
    val context = from.context()
    val tdlib = from.tdlib()
    val page = hit.page
    val c = if (page != null) {
      XgramComposeController.create(context, tdlib, page, hit.id, hit.key)
    } else {
      // A row of XGram's main page: its category or What's new; About rows are pointed at, never run
      val category = mainRowPage(hit.id)
      if (category != null) {
        XgramComposeController.create(context, tdlib, category)
      } else {
        XgramComposeController.create(context, tdlib, XgramPage.MAIN, hit.id, hit.key)
      }
    }
    from.navigateTo(c)
  }

  private fun mainRowPage(id: Int): XgramPage? = when (id) {
    R.id.btn_xgramWhatsNew -> XgramPage.WHATS_NEW
    R.id.btn_xgramSectionAppearance -> XgramPage.APPEARANCE
    R.id.btn_xgramSectionChatList -> XgramPage.CHAT_LIST
    R.id.btn_xgramSectionChats -> XgramPage.MESSAGES
    R.id.btn_xgramSectionTranslation -> XgramPage.TRANSLATION
    R.id.btn_xgramSectionActivity -> XgramPage.ACTIVITY
    R.id.btn_xgramSectionPrivacy -> XgramPage.PRIVACY
    R.id.btn_xgramSectionFilters -> XgramPage.FILTERS
    R.id.btn_xgramSectionHistory -> XgramPage.HISTORY
    R.id.btn_xgramSectionNetwork -> XgramPage.NETWORK
    R.id.btn_xgramSectionUpdates -> XgramPage.UPDATES
    R.id.btn_xgramSectionBackup -> XgramPage.BACKUP
    else -> null
  }
}
