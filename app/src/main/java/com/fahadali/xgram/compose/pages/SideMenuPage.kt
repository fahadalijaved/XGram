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

import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramDrawerItemsController
import com.fahadali.xgram.XgramReorder
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.ReorderRow
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.reorderGroup
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/**
 * Side Menu: the header's phone number, then which items the drawer shows and in what order. Night mode, Expressive
 * design (Android 13+) and Ghost mode sit below the drawer's line, so they are a group of their own and move only
 * among themselves.
 */
@Composable
fun SideMenuPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramSideMenu)) {
    val settings = XgramSettings.instance()
    val mainKeys = XgramReorder.ordered(XgramReorder.DRAWER, XgramDrawerItemsController.KEYS)
      .filter { XgramDrawerItemsController.isAvailable(it) }
    val toggleKeys = XgramReorder.ordered(XgramReorder.DRAWER, XgramDrawerItemsController.TOGGLE_KEYS)
      .filter { XgramDrawerItemsController.isAvailable(it) }

    // The same option as in Privacy: the header shows the @username instead of the number.
    group {
      switch(R.id.btn_xgramHidePhone, s(R.string.XgramHidePhone), s(R.string.XgramHidePhoneSub), checked = settings.hidePhone()) {
        settings.setHidePhone(it)
        host.activity.getDrawer()?.xgramRefreshHeader()
      }
    }

    header(R.string.XgramSideMenuItems)
    reorderGroup(
      key = "drawerMain",
      id = R.id.btn_xgramDrawerItem,
      rows = drawerRows(settings, mainKeys, XgramDrawerItemsController.KEYS, XgramDrawerItemsController.NAMES),
      onToggle = { key, shown -> setShown(host, settings, key, shown) }
    ) { keys ->
      XgramReorder.save(XgramReorder.DRAWER, keys + toggleKeys)
      updateDrawer(host)
    }
    reorderGroup(
      key = "drawerToggles",
      id = R.id.btn_xgramDrawerItem,
      rows = drawerRows(settings, toggleKeys, XgramDrawerItemsController.TOGGLE_KEYS, XgramDrawerItemsController.TOGGLE_NAMES),
      onToggle = { key, shown -> setShown(host, settings, key, shown) }
    ) { keys ->
      XgramReorder.save(XgramReorder.DRAWER, mainKeys + keys)
      updateDrawer(host)
    }
    description(R.string.XgramSideMenuInfo)

    group {
      action(R.id.btn_xgramResetOrder, s(R.string.XgramResetOrder), icon = R.drawable.baseline_replay_24) {
        XgramReorder.reset(XgramReorder.DRAWER)
        updateDrawer(host)
      }
    }
  }
}

/** Rows in the user's order; Settings has no switch (always shown, it can only move). */
private fun drawerRows(settings: XgramSettings, ordered: List<String>, keys: Array<String>, names: IntArray): List<ReorderRow> =
  ordered.map { key ->
    val index = keys.indexOf(key)
    ReorderRow(
      key = key,
      title = if (index >= 0) s(names[index]) else key,
      checked = if (key == XgramDrawerItemsController.KEY_SETTINGS) null else !settings.drawerItemHidden(key)
    )
  }

private fun setShown(host: XgramComposeHost, settings: XgramSettings, key: String, shown: Boolean) {
  if (key == XgramDrawerItemsController.KEY_SETTINGS) {
    return
  }
  settings.setDrawerItemHidden(key, !shown)
  updateDrawer(host)
}

private fun updateDrawer(host: XgramComposeHost) {
  host.activity.getDrawer()?.xgramUpdateMenuItems()
  host.activity.xgramTwoPane()?.update() // XGram M3E: the navigation rail's destinations follow at once
}
