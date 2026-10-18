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
package com.fahadali.xgram.compose.pages

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramLogs
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.GroupScope
import com.fahadali.xgram.compose.ui.IconStyle
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.SettingsSearchBar
import com.fahadali.xgram.compose.ui.SettingsSearchOverlay
import com.fahadali.xgram.compose.ui.rememberSettingsSearchState
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.TdlibUi
import org.thunderdog.challegram.tool.UI

private const val URL_BASED_ON = "https://github.com/TGX-Android/Telegram-X"
private const val URL_SUPPORT = "https://paypal.me/fahadalijaved23"

/** Settings → XGram: search, What's new, the categories and About. */
@Composable
fun MainPage() {
  val host = LocalXgramHost.current
  val search = rememberSettingsSearchState()
  SettingsPage(
    title = str(R.string.XgramSettings),
    // The full-screen search stays out of the list: only the bar scrolls with the page.
    overlay = { SettingsSearchOverlay(search, onMainHit = { id -> mainPageAction(host, id) }) }
  ) {
    val settings = XgramSettings.instance()
    // Values from Telegram X, worked out once per build of the page, not while rows are drawn.
    val hidden = XgramComposeBridge.drawerHiddenCount()
    val ghost = settings.isAnyGhostEnabled()

    item("search") {
      SettingsSearchBar(search)
    }

    group {
      link(R.id.btn_xgramWhatsNew, s(R.string.XgramWhatsNew), icon = R.drawable.baseline_whatshot_24, iconStyle = IconStyle.CIRCLE) {
        mainPageAction(host, R.id.btn_xgramWhatsNew)
      }
    }

    group {
      category(host, R.id.btn_xgramSectionAppearance, R.drawable.baseline_palette_24, R.string.XgramAppearance, s(R.string.XgramAppearanceSummary))
      category(host, R.id.btn_xgramSectionChatList, R.drawable.baseline_forum_24, R.string.XgramChatList, s(R.string.XgramChatListSummary))
      category(host, R.id.btn_xgramSectionChats, R.drawable.baseline_chat_bubble_24, R.string.XgramMessages, s(R.string.XgramMessagesSummary))
      category(host, R.id.btn_xgramSectionTranslation, R.drawable.baseline_translate_24, R.string.XgramTranslation, s(R.string.XgramTranslationSummary))
      category(
        host, R.id.btn_xgramSideMenu, R.drawable.baseline_format_list_bulleted_type_24, R.string.XgramSideMenu,
        if (hidden > 0) s(R.string.XgramHiddenCount, hidden) else s(R.string.XgramSideMenuSummary)
      )
    }

    group {
      category(
        host, R.id.btn_xgramSectionPrivacy, R.drawable.baseline_lock_24, R.string.XgramPrivacy,
        s(if (ghost) R.string.XgramPrivacySummaryGhostOn else R.string.XgramPrivacySummary)
      )
      category(host, R.id.btn_xgramSectionFilters, R.drawable.baseline_filter_variant_remove_24, R.string.XgramFilters, s(R.string.XgramFiltersSummary))
      category(host, R.id.btn_xgramSectionHistory, R.drawable.baseline_history_24, R.string.XgramHistory, s(R.string.XgramHistorySummary))
    }

    group {
      category(host, R.id.btn_xgramSectionNetwork, R.drawable.baseline_public_24, R.string.XgramNetwork, s(R.string.XgramNetworkSummary))
      category(host, R.id.btn_xgramSectionUpdates, R.drawable.baseline_system_update_24, R.string.XgramUpdates, s(R.string.XgramUpdatesSummary))
      category(host, R.id.btn_xgramSectionBackup, R.drawable.baseline_import_export_24, R.string.XgramBackup, s(R.string.XgramBackupSummary))
    }

    header(R.string.XgramAbout)
    group {
      value(R.id.btn_xgramAboutVersion, s(R.string.XgramAboutVersion), Lang.getAppBuildAndVersion(host.tdlib), icon = R.drawable.baseline_info_24) {
        mainPageAction(host, R.id.btn_xgramAboutVersion)
      }
      value(R.id.btn_xgramLogsShare, s(R.string.XgramLogsShare), s(R.string.XgramLogsShareInfo), icon = R.drawable.baseline_share_24) {
        mainPageAction(host, R.id.btn_xgramLogsShare)
      }
      value(R.id.btn_xgramAboutBasedOn, s(R.string.XgramAboutBasedOn), s(R.string.XgramAboutBasedOnInfo), icon = R.drawable.baseline_code_24) {
        mainPageAction(host, R.id.btn_xgramAboutBasedOn)
      }
      value(R.id.btn_xgramAboutSupport, s(R.string.XgramAboutSupport), s(R.string.XgramAboutSupportInfo), icon = R.drawable.baseline_favorite_24) {
        mainPageAction(host, R.id.btn_xgramAboutSupport)
      }
    }
    description(R.string.XgramAboutCredits)
  }
}

/** A category: an icon in a primary container circle and a summary of what is inside. */
private fun GroupScope.category(host: XgramComposeHost, id: Int, @DrawableRes icon: Int, @StringRes title: Int, summary: String) {
  link(id, s(title), summary, icon, IconStyle.CIRCLE) {
    mainPageAction(host, id)
  }
}

/** What a row of the main page does; settings search runs it for main page results too. */
internal fun mainPageAction(host: XgramComposeHost, id: Int) {
  when (id) {
    R.id.btn_xgramWhatsNew -> host.openPage(XgramPage.WHATS_NEW)
    R.id.btn_xgramSectionAppearance -> host.openPage(XgramPage.APPEARANCE)
    R.id.btn_xgramSectionChatList -> host.openPage(XgramPage.CHAT_LIST)
    R.id.btn_xgramSectionChats -> host.openPage(XgramPage.MESSAGES)
    R.id.btn_xgramSectionTranslation -> host.openPage(XgramPage.TRANSLATION)
    R.id.btn_xgramSideMenu -> host.openPage(XgramPage.SIDE_MENU)
    R.id.btn_xgramSectionPrivacy -> host.openPage(XgramPage.PRIVACY)
    R.id.btn_xgramSectionFilters -> host.openPage(XgramPage.FILTERS)
    R.id.btn_xgramSectionHistory -> host.openPage(XgramPage.HISTORY)
    R.id.btn_xgramSectionNetwork -> host.openPage(XgramPage.NETWORK)
    R.id.btn_xgramSectionUpdates -> host.openPage(XgramPage.UPDATES)
    R.id.btn_xgramSectionBackup -> host.openPage(XgramPage.BACKUP)
    R.id.btn_xgramAboutVersion -> UI.copyText(Lang.getAppBuildAndVersion(host.tdlib), R.string.CopiedText)
    // The privacy note as this page's dialog, then the file and the share sheet (nothing is uploaded by itself)
    R.id.btn_xgramLogsShare -> host.confirm(
      ConfirmSpec(title = s(R.string.XgramLogsShareConfirmTitle), text = s(R.string.XgramLogsShareConfirmText), confirm = s(R.string.XgramLogsShareAction)) {
        XgramLogs.share(host.controller)
      }
    )
    R.id.btn_xgramAboutBasedOn -> host.tdlib.ui().openUrl(host.controller, URL_BASED_ON, TdlibUi.UrlOpenParameters().disableInstantView())
    R.id.btn_xgramAboutSupport -> host.tdlib.ui().openUrl(host.controller, URL_SUPPORT, TdlibUi.UrlOpenParameters().disableInstantView())
    else -> Unit
  }
}
