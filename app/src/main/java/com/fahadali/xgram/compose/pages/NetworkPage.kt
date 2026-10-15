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
import com.fahadali.xgram.XgramDownloadBoost
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R

/**
 * Network: download boost (switch and strength), the proxy rules (Telegram X's proxy page points at the first one)
 * and IPv6.
 */
@Composable
fun NetworkPage() {
  SettingsPage(title = str(R.string.XgramNetwork)) {
    val settings = XgramSettings.instance()

    header(R.string.XgramSpeed)
    group {
      switch(R.id.btn_xgramDownloadBoost, s(R.string.XgramDownloadBoost), s(R.string.XgramDownloadBoostSub), checked = settings.downloadBoost()) {
        settings.setDownloadBoost(it)
      }
      // Always movable, as on the classic page: the strength is kept for when boost is on.
      slider(
        R.id.btn_xgramDownloadBoostLevel, s(R.string.XgramBoostStrength),
        labels = XgramDownloadBoost.LEVEL_NAMES.map { s(it) },
        index = settings.downloadBoostLevel() - XgramDownloadBoost.LEVEL_LOW
      ) { index ->
        settings.setDownloadBoostLevel(XgramDownloadBoost.LEVEL_LOW + index)
      }
    }
    // What the level in effect does (Off when boost is off), then how boost works.
    description(
      s(XgramDownloadBoost.description(XgramDownloadBoost.level())) + "\n\n" + s(R.string.XgramDownloadBoostInfo),
      "downloadBoostInfo"
    )

    header(R.string.XgramProxyRules)
    group {
      switch(R.id.btn_xgramProxyOffVpn, s(R.string.XgramProxyOffVpn), checked = settings.proxyOffOnVpn()) {
        settings.setProxyOffOnVpn(it)
      }
      switch(R.id.btn_xgramProxyOffWifi, s(R.string.XgramProxyOffWifi), checked = settings.proxyOffOnWifi()) {
        settings.setProxyOffOnWifi(it)
      }
      switch(R.id.btn_xgramProxyOffMobile, s(R.string.XgramProxyOffMobile), checked = settings.proxyOffOnMobile()) {
        settings.setProxyOffOnMobile(it)
      }
    }
    description(R.string.XgramProxyRulesInfo)

    header(R.string.XgramHeaderConnection)
    group {
      switch(R.id.btn_xgramPreferIpv6, s(R.string.XgramPreferIpv6), s(R.string.XgramPreferIpv6Sub), checked = settings.preferIpv6()) {
        settings.setPreferIpv6(it)
      }
    }
  }
}
