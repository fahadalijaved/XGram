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
import com.fahadali.xgram.XgramSettingDeps
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.ghost.XgramGhost
import org.thunderdog.challegram.R

/**
 * Privacy and ghost mode, with Ghost mode first (the main screen's ghost button, the side menu switch and the Quick
 * Settings tile open this page): the master switch and its parts, then protected content and this device (Hide phone
 * number's only switch).
 */
@Composable
fun PrivacyPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramPrivacy)) {
    val settings = XgramSettings.instance()

    // No header: the switch is called Ghost mode already.
    group {
      mainSwitch(
        R.id.btn_xgramGhostMode, s(R.string.XgramGhostModeToggle), s(R.string.XgramGhostModeSub), R.drawable.baseline_ghost_24,
        checked = settings.isGhostModeEnabled()
      ) {
        ghost(settings) { settings.setGhostModeEnabled(it) }
      }
    }
    description(R.string.XgramGhostModeInfo)

    header(R.string.XgramHeaderReading)
    group {
      switch(R.id.btn_xgramGhostDontRead, s(R.string.XgramGhostDontRead), s(R.string.XgramGhostDontReadSub), checked = settings.ghostDontRead()) {
        ghost(settings) { settings.setGhostDontRead(it) }
      }
      switch(
        R.id.btn_xgramGhostReadOnSend, s(R.string.XgramGhostReadOnSend), s(R.string.XgramGhostReadOnSendSub),
        checked = settings.ghostReadOnSend(), enabled = settings.ghostDontRead()
      ) {
        ghost(settings) { settings.setGhostReadOnSend(it) }
      }
      switch(
        R.id.btn_xgramGhostLocalRead, s(R.string.XgramGhostLocalRead), s(R.string.XgramGhostLocalReadSub),
        checked = settings.ghostLocalRead(), enabled = settings.ghostDontRead()
      ) {
        ghost(settings) { settings.setGhostLocalRead(it) }
      }
    }

    header(R.string.XgramHeaderTyping)
    group {
      switch(R.id.btn_xgramGhostDontType, s(R.string.XgramGhostDontType), s(R.string.XgramGhostDontTypeSub), checked = settings.ghostDontType()) {
        ghost(settings) { settings.setGhostDontType(it) }
      }
      switch(
        R.id.btn_xgramGhostTypingForStickers, s(R.string.XgramGhostTypingForStickers), s(R.string.XgramGhostTypingForStickersSub),
        checked = settings.ghostTypingForStickers(), enabled = !settings.ghostDontType()
      ) {
        ghost(settings) { settings.setGhostTypingForStickers(it) }
      }
    }

    header(R.string.XgramHeaderOnlineStatus)
    group {
      switch(R.id.btn_xgramGhostOffline, s(R.string.XgramGhostOffline), s(R.string.XgramGhostOfflineSub), checked = settings.ghostOffline()) {
        ghost(settings) { settings.setGhostOffline(it) }
      }
      switch(
        R.id.btn_xgramGhostScheduleSend, s(R.string.XgramGhostScheduleSend), s(R.string.XgramGhostScheduleSendSub),
        checked = settings.ghostScheduleSend(), enabled = settings.ghostOffline()
      ) {
        ghost(settings) { settings.setGhostScheduleSend(it) }
      }
    }

    header(R.string.XgramHeaderSending)
    group {
      // Only while not reading, not typing or offline is on.
      val silentReason = XgramSettingDeps.ghostSilentReason()
      switch(
        R.id.btn_xgramGhostSilent, s(R.string.XgramGhostSilent),
        s(if (silentReason != 0) silentReason else R.string.XgramGhostSilentSub),
        checked = settings.ghostSilent(), enabled = silentReason == 0
      ) {
        ghost(settings) { settings.setGhostSilent(it) }
      }
    }

    header(R.string.XgramHeaderProtectedContent)
    group {
      switch(R.id.btn_xgramAllowScreenshots, s(R.string.XgramAllowScreenshots), s(R.string.XgramAllowScreenshotsSub), checked = settings.allowScreenshots()) {
        settings.setAllowScreenshots(it)
        host.activity.checkDisallowScreenshots()
      }
      switch(R.id.btn_xgramSaveProtected, s(R.string.XgramSaveProtected), s(R.string.XgramSaveProtectedSub), checked = settings.saveProtected()) {
        settings.setSaveProtected(it)
      }
    }

    header(R.string.XgramHeaderDevice)
    group {
      switch(R.id.btn_xgramHidePhone, s(R.string.XgramHidePhone), s(R.string.XgramHidePhoneSub), checked = settings.hidePhone()) {
        settings.setHidePhone(it)
        host.activity.getDrawer()?.xgramRefreshHeader() // the side menu shows the @username instead
      }
      switch(R.id.btn_xgramBlockContentCapture, s(R.string.XgramBlockContentCapture), s(R.string.XgramBlockContentCaptureSub), checked = settings.blockContentCapture()) {
        settings.setBlockContentCapture(it)
      }
    }
    description(R.string.XgramPrivacyInfo)
  }
}

/** A ghost mode setter: going offline or online is sent right away, as on the classic page. */
private inline fun ghost(settings: XgramSettings, change: () -> Unit) {
  val wasOffline = settings.ghostOffline()
  change()
  if (wasOffline != settings.ghostOffline()) {
    XgramGhost.applyOnlineState()
  }
}
