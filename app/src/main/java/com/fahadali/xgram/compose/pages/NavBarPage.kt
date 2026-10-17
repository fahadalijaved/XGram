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

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.ReorderRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.reorderGroup
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.layout.XgramNavToolbar
import org.thunderdog.challegram.R
import org.thunderdog.challegram.tool.UI

/**
 * Appearance > Layout > Navigation bar (Expressive design): the floating navigation toolbar of narrow windows. A live
 * preview of it (the real pill and photo, a tap moves the selection), the switch and the colours, the tabs (up to 4,
 * Chats always, in the user's order), where the profile photo goes, and a reset to Telegram's layout. Every change
 * reaches the toolbar at once.
 */
@Composable
fun NavBarPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramBottomNavOption)) {
    val settings = XgramSettings.instance()

    item("navPreview") {
      AndroidView(
        factory = { XgramNavToolbar.newPreview(host.activity) },
        update = { preview ->
          host.version // read: the preview follows every change on this page
          preview.sync()
        },
        modifier = Modifier.fillMaxWidth().height(96.dp)
      )
    }

    group {
      switch(R.id.btn_xgramBottomNav, s(R.string.XgramBottomNavOption), s(R.string.XgramNavBarSub), checked = settings.bottomNav()) {
        settings.setBottomNav(it)
        updateToolbar(host)
      }
      buttons(
        R.id.btn_xgramNavBarStyle, s(R.string.XgramNavBarStyle),
        listOf(
          s(R.string.XgramNavBarStyleStandard) to XgramSettings.NAV_STYLE_STANDARD,
          s(R.string.XgramNavBarStyleVibrant) to XgramSettings.NAV_STYLE_VIBRANT
        ),
        selected = settings.navStyle()
      ) {
        settings.setNavStyle(it)
        updateToolbar(host)
      }
    }

    header(R.string.XgramNavBarTabs)
    val profileMode = settings.navProfile()
    val allKeys = XgramNavToolbar.orderedKeys()
    val shownKeys = allKeys.filter { it != PROFILE || profileMode == XgramSettings.NAV_PROFILE_TAB }
    val count = XgramNavToolbar.enabledTabCount()
    reorderGroup(
      key = "navTabs",
      id = R.id.btn_xgramNavBarTab,
      rows = shownKeys.map { key -> tabRow(settings, key, count) },
      onToggle = { key, on -> setTabOn(host, settings, key, on) }
    ) { keys ->
      // The profile's place is kept while its row is not shown (photo beside the bar, or off)
      val order = keys.toMutableList()
      if (!order.contains(PROFILE)) {
        val index = allKeys.indexOf(PROFILE).coerceIn(0, order.size)
        order.add(index, PROFILE)
      }
      XgramReorder.save(XgramReorder.NAV_BAR, order)
      updateToolbar(host)
    }
    description(R.string.XgramNavBarInfo)

    group {
      buttons(
        R.id.btn_xgramNavBarProfile, s(R.string.XgramNavBarProfile),
        listOf(
          s(R.string.XgramNavBarProfileButton) to XgramSettings.NAV_PROFILE_BUTTON,
          s(R.string.XgramNavBarProfileTab) to XgramSettings.NAV_PROFILE_TAB,
          s(R.string.XgramNavBarProfileOff) to XgramSettings.NAV_PROFILE_OFF
        ),
        selected = profileMode
      ) { mode ->
        setProfileMode(host, settings, mode)
      }
    }
    description(R.string.XgramNavBarProfileInfo)

    group {
      action(R.id.btn_xgramNavBarReset, s(R.string.XgramNavBarReset), icon = R.drawable.baseline_replay_24) {
        settings.resetNavBar()
        updateToolbar(host)
      }
    }
  }
}

private const val CHATS = "chats"
private const val PROFILE = "profile"

/**
 * One tab's row. Chats and the profile (shown only as a tab) have no switch: they only move. With 4 tabs on, the off
 * ones can't be turned on; with 2, the on ones (but Chats) can't be turned off.
 */
private fun tabRow(settings: XgramSettings, key: String, count: Int): ReorderRow {
  val dest = XgramNavToolbar.destOf(key)
  val title = XgramNavToolbar.tabTitle(key)
  val icon = XgramNavToolbar.tabIcon(key)
  if (key == CHATS || key == PROFILE) {
    return ReorderRow(key = key, title = title, icon = icon, checked = null)
  }
  val on = (settings.navTabs() and (1 shl dest)) != 0
  val atMax = !on && count >= 4
  val atMin = on && count <= 2
  val subtitle = when {
    atMax -> s(R.string.XgramNavBarMax)
    atMin -> s(R.string.XgramNavBarMin)
    dest == XgramNavToolbar.DEST_SAVED -> s(R.string.XgramNavBarSavedSub)
    else -> null
  }
  return ReorderRow(key = key, title = title, icon = icon, checked = on, enabled = !atMax && !atMin, subtitle = subtitle)
}

private fun setTabOn(host: XgramComposeHost, settings: XgramSettings, key: String, on: Boolean) {
  val dest = XgramNavToolbar.destOf(key)
  if (dest < 0 || key == CHATS || key == PROFILE) {
    return
  }
  val count = XgramNavToolbar.enabledTabCount()
  if ((on && count >= 4) || (!on && count <= 2)) {
    return
  }
  settings.setNavTabOn(dest, on)
  updateToolbar(host)
}

/** The photo as a tab while 4 tabs are on: the last of them (not Chats) makes room, and a toast says which. */
private fun setProfileMode(host: XgramComposeHost, settings: XgramSettings, mode: Int) {
  if (mode == settings.navProfile()) {
    return
  }
  if (mode == XgramSettings.NAV_PROFILE_TAB && XgramNavToolbar.enabledTabCount() >= 4) {
    val last = XgramNavToolbar.orderedKeys().lastOrNull { key ->
      val dest = XgramNavToolbar.destOf(key)
      key != CHATS && key != PROFILE && dest >= 0 && (settings.navTabs() and (1 shl dest)) != 0
    }
    if (last != null) {
      settings.setNavTabOn(XgramNavToolbar.destOf(last), false)
      UI.showToast(s(R.string.XgramNavBarMadeRoom, XgramNavToolbar.tabTitle(last)), Toast.LENGTH_SHORT)
    }
  }
  settings.setNavProfile(mode)
  updateToolbar(host)
}

private fun updateToolbar(host: XgramComposeHost) {
  host.activity.xgramTwoPane()?.update()
}
