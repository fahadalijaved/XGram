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
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.appearance.XgramColorSource
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.layout.XgramNavToolbar
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.MessagesController

/**
 * The XGram part of the settings search. Its rows are read from XGram's own page lists when the index is built
 * (XgramComposeBridge.searchIndex, every row a page shows); this adds the words they are also found by, and the rows
 * only Compose draws: the Navigation bar page and Appearance's custom colors (Expressive design), and Message style's
 * own rows (both looks: Telegram X's preview page with XGram's rows, XgramChatLook).
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
    R.id.btn_xgramTwoPane, R.id.btn_xgramNavRail, R.id.btn_xgramLargeScreens, R.id.btn_xgramPaneSplitEvenly, R.id.btn_xgramPaneResizable,
    R.id.btn_xgramFoldHingeSplit, R.id.btn_xgramFoldTabletop -> intArrayOf(R.string.XgramSearchKwTwoPane)
    R.id.btn_xgramBottomNav -> intArrayOf(R.string.XgramSearchKwNavBar)
    R.id.btn_xgramDownloadBoost, R.id.btn_xgramDownloadBoostLevel -> intArrayOf(R.string.XgramSearchKwDownload, R.string.XgramSearchKwSpeed)
    R.id.btn_xgramProxyOffVpn, R.id.btn_xgramProxyOffWifi, R.id.btn_xgramProxyOffMobile -> intArrayOf(R.string.XgramSearchKwProxy)
    R.id.btn_xgramHistorySaveDeleted, R.id.btn_xgramHistorySaveEdits -> intArrayOf(R.string.XgramSearchKwDeleted)
    R.id.btn_xgramTrProvider -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwTranslateProvider)
    R.id.btn_xgramTrTarget -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwLanguage)
    R.id.btn_xgramTrStyle -> intArrayOf(R.string.XgramSearchKwTranslate, R.string.XgramSearchKwTranslateStyle)
    R.id.btn_xgramHideSponsored -> intArrayOf(R.string.XgramSearchKwAds)
    R.id.btn_xgramExportSettings, R.id.btn_xgramImportSettings -> intArrayOf(R.string.XgramSearchKwBackup)
    R.id.btn_xgramUpdateChannel, R.id.btn_xgramUpdateCheck, R.id.btn_xgramAboutVersion -> intArrayOf(R.string.XgramSearchKwVersion, R.string.XgramSearchKwUpdateChannel)
    R.id.btn_xgramExpressive -> intArrayOf(R.string.XgramSearchKwExpressive)
    R.id.btn_xgramColorSource -> intArrayOf(R.string.XgramSearchKwColors, R.string.XgramSearchKwMaterialYou, R.string.XgramSearchKwExpressive)
    R.id.btn_xgramAllowScreenshots, R.id.btn_xgramSaveProtected, R.id.btn_xgramBlockContentCapture -> intArrayOf(R.string.XgramSearchKwScreenshot)
    R.id.btn_xgramChatListView -> intArrayOf(R.string.XgramSearchKwChatListView)
    R.id.btn_xgramChatListBigText -> intArrayOf(R.string.XgramSearchKwChatListView, R.string.XgramSearchKwFont)
    R.id.btn_xgramChatLook -> intArrayOf(R.string.XgramSearchKwFont, R.string.XgramSearchKwBubbles, R.string.XgramSearchKwStickers)
    // Telegram X's Chat Previews and Send with Enter: their rows are XGram's now (Long press on a chat, Enter to send)
    R.id.btn_xgramChatLongPress -> intArrayOf(R.string.XgramSearchKwPeek, R.string.HoldToPreview)
    R.id.btn_xgramEnterToSend -> intArrayOf(R.string.XgramSearchKwKeyboard, R.string.SendByEnter)
    R.id.btn_xgramEmojiSet -> intArrayOf(R.string.XgramSearchKwEmoji, R.string.XgramSearchKwEmojiSet)
    R.id.btn_xgramFont -> intArrayOf(R.string.XgramSearchKwFont, R.string.XgramSearchKwAppFont)
    R.id.btn_xgramHideFolders, R.id.btn_xgramHideAllChats -> intArrayOf(R.string.XgramSearchKwFolders)
    R.id.btn_xgramShowPeerIds -> intArrayOf(R.string.XgramSearchKwProfile)
    R.id.btn_xgramUndoSend, R.id.btn_xgramUndoSendVoice, R.id.btn_xgramUndoSendBubbles, R.id.btn_xgramUndoSendNewest -> intArrayOf(R.string.XgramSearchKwUnsend, R.string.XgramSearchKwDelaySend)
    R.id.btn_xgramForwardSender -> intArrayOf(R.string.XgramSearchKwForward)
    R.id.btn_xgramTextSelectDrag -> intArrayOf(R.string.XgramSearchKwSelectText)
    R.id.btn_xgramActivityShow, R.id.btn_xgramActivityReactions, R.id.btn_xgramActivityMuted, R.id.btn_xgramActivityDiscussion,
    R.id.btn_xgramActivityLookBack, R.id.btn_xgramActivityBadge,
    R.id.btn_xgramActivityHidden, R.id.btn_xgramActivityClearAll -> intArrayOf(R.string.XgramSearchKwActivity)
    R.id.btn_xgramSectionActivity -> intArrayOf(R.string.XgramSearchKwActivity, R.string.XgramSearchKwLater, R.string.XgramSearchKwCheckIn)
    R.id.btn_xgramActivityBar, R.id.btn_xgramActivityNavBar -> intArrayOf(R.string.XgramSearchKwActivity, R.string.XgramSearchKwNavBar)
    R.id.btn_xgramLaterRow, R.id.btn_xgramLaterRaise, R.id.btn_xgramLaterWakeEarly, R.id.btn_xgramLaterExact,
    R.id.btn_xgramLaterChannel -> intArrayOf(R.string.XgramSearchKwLater)
    R.id.btn_xgramCheckInTimer, R.id.btn_xgramCheckInGrace, R.id.btn_xgramCheckInRadius, R.id.btn_xgramCheckInLocation,
    R.id.btn_xgramCheckInBattery, R.id.btn_xgramCheckInFollowUp -> intArrayOf(R.string.XgramSearchKwCheckIn)
    else -> NONE
  }

  /** A row of a Compose-only XGram page: its key in the recent searches, the result, its words. */
  class Item(val key: String, val hit: XgramSearchHit, val kw: IntArray)

  /** The Navigation bar page (Chat list > Navigation bar), titles in the app language. */
  fun navBar(): List<Item> {
    val section = XgramSettingsController.SECTION_CHAT_LIST
    val pathParts = listOf(Lang.getString(R.string.XgramChatList), Lang.getString(R.string.XgramBottomNavOption))
    val path = Lang.getString(R.string.XgramSettingsSearchPath, pathParts[0], pathParts[1])
    val kw = intArrayOf(R.string.XgramSearchKwNavBar)
    val icon = R.drawable.baseline_forum_24
    val firstTab = try {
      XgramNavToolbar.orderedKeys().firstOrNull()
    } catch (t: Throwable) {
      null
    }
    fun item(name: String, id: Int, title: Int, key: String?, words: IntArray = kw): Item {
      val text = Lang.getString(title)
      return Item("x:navbar/$name", XgramSearchHit(XgramPage.NAV_BAR, section, 0, id, key, false, icon, text, text.lowercase(), path, pathParts = pathParts), words)
    }
    // Activity's tab row, while it is on (the page shows it only then)
    val activity = if (com.fahadali.xgram.activity.XgramActivity.isOn()) {
      listOf(item("activity", R.id.btn_xgramNavBarTab, R.string.XgramActivity, "activity", intArrayOf(R.string.XgramSearchKwNavBar, R.string.XgramSearchKwActivity)))
    } else {
      emptyList()
    }
    return listOf(
      item("switch", R.id.btn_xgramBottomNav, R.string.XgramBottomNavOption, null),
      item("style", R.id.btn_xgramNavBarStyle, R.string.XgramNavBarStyle, null),
      item("tabs", R.id.btn_xgramNavBarTab, R.string.XgramNavBarTabs, firstTab),
      item("profile", R.id.btn_xgramNavBarProfile, R.string.XgramNavBarProfile, null),
      item("hideDrawer", R.id.btn_xgramNavBarHideDrawer, R.string.XgramNavBarHideDrawer, null),
      item("reset", R.id.btn_xgramNavBarReset, R.string.XgramNavBarReset, null)
    ) + activity
  }

  /**
   * Appearance > Colors' custom rows (the swatches, the style, More colors), drawn only by the Compose page and only
   * while Custom is picked.
   */
  fun customColors(): List<Item> {
    if (!XgramColorSource.isAvailable() || XgramSettings.instance().colorSource() != XgramSettings.COLOR_SOURCE_CUSTOM) {
      return emptyList()
    }
    val section = XgramSettingsController.SECTION_APPEARANCE
    val pathParts = listOf(Lang.getString(R.string.XgramAppearance), Lang.getString(R.string.XgramColors))
    val path = Lang.getString(R.string.XgramSettingsSearchPath, pathParts[0], pathParts[1])
    val kw = intArrayOf(R.string.XgramSearchKwColors, R.string.XgramSearchKwMaterialYou)
    // [key]: the custom row's key on the page (AppearanceColors.colorSource), so the result points at it
    fun item(name: String, id: Int, title: Int, key: String?): Item {
      val text = Lang.getString(title)
      return Item("x:colors/$name", XgramSearchHit(XgramPage.APPEARANCE, section, 0, id, key, false, R.drawable.baseline_palette_24, text, text.lowercase(), path, pathParts = pathParts), kw)
    }
    return listOf(
      item("seed", R.id.btn_xgramColorSeed, R.string.XgramColorsSeed, "colors_seed"),
      item("style", R.id.btn_xgramColorStyle, R.string.XgramColorsStyle, "colors_style"),
      item("more", R.id.btn_xgramColorMore, R.string.XgramColorsMore, null)
    )
  }

  /**
   * Message style's own rows (Appearance > Message style: Telegram X's text size page with XGram's rows, XgramChatLook),
   * in both looks. Not an XGram settings page: a result opens it itself ([XgramSearchHit.open]).
   */
  fun messageStyle(): List<Item> {
    val section = XgramSettingsController.SECTION_APPEARANCE
    val pathParts = listOf(Lang.getString(R.string.XgramAppearance), Lang.getString(R.string.XgramChatLookRow))
    val path = Lang.getString(R.string.XgramSettingsSearchPath, pathParts[0], pathParts[1])
    val open: (ViewController<*>) -> ViewController<*> = { from ->
      val c = MessagesController(from.context(), from.tdlib())
      c.setArguments(MessagesController.Arguments(MessagesController.PREVIEW_MODE_FONT_SIZE, null, null))
      c
    }
    fun item(name: String, title: Int, words: IntArray): Item {
      val text = Lang.getString(title)
      // The page's row on Appearance stands for them: without a page to open, a result points at it
      return Item(
        "x:look/$name",
        XgramSearchHit(XgramPage.APPEARANCE, section, 0, R.id.btn_xgramChatLook, null, false, R.drawable.baseline_palette_24, text, text.lowercase(), path, pathParts = pathParts, open = open),
        words
      )
    }
    val bubbles = intArrayOf(R.string.XgramSearchKwBubbles)
    return listOf(
      item("stickerSize", R.string.XgramStickerSize, intArrayOf(R.string.XgramSearchKwStickers)),
      item("emojiSize", R.string.XgramEmojiSize, intArrayOf(R.string.XgramSearchKwEmoji)),
      item("bubbleCorners", R.string.XgramBubbleCorners, bubbles),
      item("mergedCorners", R.string.XgramBubbleMergedCorners, bubbles),
      item("bubbleTail", R.string.XgramBubbleTail, bubbles),
      item("avatarAtLast", R.string.XgramAvatarAtLast, intArrayOf(R.string.XgramSearchKwProfile))
    )
  }
}
