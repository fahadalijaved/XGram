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

import com.fahadali.xgram.XgramSearchHit
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.layout.XgramNavToolbar
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * The XGram part of the settings search. Its rows are read from XGram's own page lists when the index is built
 * (XgramComposeBridge.searchIndex, every row a page shows); this adds the words they are also found by, and the rows of
 * the Navigation bar page, which only exists in Compose (Expressive design).
 */
internal object XgramSearchIndex {
  val NONE = IntArray(0)

  /** Extra words of an XGram row, by its id. */
  fun keywords(id: Int): IntArray = when (id) {
    R.id.btn_xgramPureBlack -> intArrayOf(R.string.XgramSearchKwDark, R.string.XgramSearchKwPower)
    R.id.btn_xgramMaterialYou -> intArrayOf(R.string.XgramSearchKwDark, R.string.XgramSearchKwMaterialYou)
    R.id.btn_xgramAutoNightMode, R.id.btn_xgramThemes -> intArrayOf(R.string.XgramSearchKwDark)
    R.id.btn_xgramGhostMode, R.id.btn_xgramGhostOffline, R.id.btn_xgramGhostDontRead, R.id.btn_xgramGhostReadOnSend,
    R.id.btn_xgramSaveLastSeen -> intArrayOf(R.string.XgramSearchKwLastSeen)
    R.id.btn_xgramHidePhone -> intArrayOf(R.string.XgramSearchKwPhone, R.string.XgramSearchKwScreenshot)
    R.id.btn_xgramIconPack -> intArrayOf(R.string.XgramSearchKwIcons)
    R.id.btn_xgramAppIcon -> intArrayOf(R.string.XgramSearchKwAppIcon, R.string.XgramSearchKwIcons)
    R.id.btn_xgramNotificationIcon -> intArrayOf(R.string.XgramSearchKwNotify, R.string.XgramSearchKwIcons)
    R.id.btn_xgramTwoPane, R.id.btn_xgramNavRail -> intArrayOf(R.string.XgramSearchKwTwoPane)
    R.id.btn_xgramBottomNav -> intArrayOf(R.string.XgramSearchKwNavBar)
    R.id.btn_xgramDownloadBoost, R.id.btn_xgramDownloadBoostLevel -> intArrayOf(R.string.XgramSearchKwDownload, R.string.XgramSearchKwSpeed)
    R.id.btn_xgramProxyOffVpn, R.id.btn_xgramProxyOffWifi, R.id.btn_xgramProxyOffMobile -> intArrayOf(R.string.XgramSearchKwProxy)
    R.id.btn_xgramHistorySaveDeleted, R.id.btn_xgramHistorySaveEdits -> intArrayOf(R.string.XgramSearchKwDeleted)
    R.id.btn_xgramTrProvider -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwTranslateProvider)
    R.id.btn_xgramTrTarget -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwLanguage)
    R.id.btn_xgramTrStyle -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwTranslateStyle)
    R.id.btn_xgramHideSponsored -> intArrayOf(R.string.XgramSearchKwAds)
    R.id.btn_xgramExportSettings, R.id.btn_xgramImportSettings -> intArrayOf(R.string.XgramSearchKwBackup)
    R.id.btn_xgramUpdateChannel, R.id.btn_xgramUpdateCheck -> intArrayOf(R.string.XgramSearchKwVersion, R.string.XgramSearchKwUpdateChannel)
    R.id.btn_xgramExpressive -> intArrayOf(R.string.XgramSearchKwExpressive)
    R.id.btn_xgramAllowScreenshots, R.id.btn_xgramSaveProtected, R.id.btn_xgramBlockContentCapture -> intArrayOf(R.string.XgramSearchKwScreenshot)
    R.id.btn_xgramChatListView -> intArrayOf(R.string.XgramSearchKwChatListView)
    R.id.btn_xgramEmojiSet -> intArrayOf(R.string.XgramSearchKwEmoji, R.string.XgramSearchKwEmojiSet)
    R.id.btn_xgramHideFolders -> intArrayOf(R.string.XgramSearchKwFolders)
    R.id.btn_xgramShowPeerIds -> intArrayOf(R.string.XgramSearchKwProfile)
    else -> NONE
  }

  /** A row of a Compose-only XGram page: its key in the recent searches, the result, its words. */
  class Item(val key: String, val hit: XgramSearchHit, val kw: IntArray)

  /** The Navigation bar page (Appearance > Navigation bar), titles in the app language. */
  fun navBar(): List<Item> {
    val section = XgramSettingsController.SECTION_APPEARANCE
    val path = Lang.getString(R.string.XgramSettingsSearchPath, Lang.getString(R.string.XgramAppearance), Lang.getString(R.string.XgramBottomNavOption))
    val kw = intArrayOf(R.string.XgramSearchKwNavBar)
    val icon = R.drawable.baseline_palette_24
    val firstTab = try {
      XgramNavToolbar.orderedKeys().firstOrNull()
    } catch (t: Throwable) {
      null
    }
    fun item(name: String, id: Int, title: Int, key: String?): Item {
      val text = Lang.getString(title)
      return Item("x:navbar/$name", XgramSearchHit(XgramPage.NAV_BAR, section, 0, id, key, false, icon, text, text.lowercase(), path), kw)
    }
    return listOf(
      item("switch", R.id.btn_xgramBottomNav, R.string.XgramBottomNavOption, null),
      item("style", R.id.btn_xgramNavBarStyle, R.string.XgramNavBarStyle, null),
      item("tabs", R.id.btn_xgramNavBarTab, R.string.XgramNavBarTabs, firstTab),
      item("profile", R.id.btn_xgramNavBarProfile, R.string.XgramNavBarProfile, null),
      item("fab", R.id.btn_xgramNavBarFab, R.string.XgramNavBarFab, "side"),
      item("hideDrawer", R.id.btn_xgramNavBarHideDrawer, R.string.XgramNavBarHideDrawer, null),
      item("reset", R.id.btn_xgramNavBarReset, R.string.XgramNavBarReset, null)
    )
  }
}
