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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.XgramUi
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramIconPack
import com.fahadali.xgram.appearance.XgramIconPacksController
import com.fahadali.xgram.appearance.XgramMaterialYou
import com.fahadali.xgram.appearance.XgramThemesController
import com.fahadali.xgram.appicons.XgramAppIcons
import com.fahadali.xgram.appicons.XgramAppIconsController
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.GroupScope
import com.fahadali.xgram.compose.ui.PageScope
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.ui.MessagesController
import org.thunderdog.challegram.ui.SettingsCloudEmojiController

/** Appearance: theme, Material You, Expressive design, icons and emoji; chat look; layout. */
@Composable
fun AppearancePage() {
  val host = LocalXgramHost.current
  // Material You rebuilds the themes: a loading indicator stands in for its switch until that is done.
  var materialYouBusy by remember { mutableStateOf(false) }
  SettingsPage(title = str(R.string.XgramAppearance)) {
    val settings = XgramSettings.instance()

    header(R.string.XgramHeaderTheme)
    if (XgramMaterialYou.isAvailable()) {
      group {
        themes(host)
        switch(
          R.id.btn_xgramMaterialYou, s(R.string.XgramMaterialYou), s(R.string.XgramMaterialYouSub),
          checked = XgramMaterialYou.isEnabled(), busy = materialYouBusy
        ) {
          materialYouBusy = true
          XgramMaterialYou.toggle(host.controller) {
            materialYouBusy = false
            if (!host.controller.isDestroyed) {
              host.changed()
              host.themeChanged()
            }
          }
        }
      }
      description(R.string.XgramMaterialYouInfo)
      expressive(host)
      group {
        iconPack(host)
        appIcon(host)
        emojiSet(host)
      }
    } else {
      group {
        themes(host)
        iconPack(host)
        appIcon(host)
        emojiSet(host)
      }
      expressive(host)
    }

    header(R.string.XgramChats)
    group {
      link(R.id.btn_xgramChatLook, s(R.string.XgramChatLookRow), s(R.string.XgramChatLookSub)) {
        val c = MessagesController(host.activity, host.tdlib)
        c.setArguments(MessagesController.Arguments(MessagesController.PREVIEW_MODE_FONT_SIZE, null, null))
        host.open(c)
      }
      value(R.id.btn_xgramAvatarCorners, s(R.string.XgramAvatarCorners), XgramComposeBridge.avatarCornersName(settings.avatarCorners())) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramAvatarCorners),
            options = XgramSettings.AVATAR_CORNERS.map { XgramComposeBridge.avatarCornersName(it) to it },
            selected = settings.avatarCorners()
          ) { percent ->
            settings.setAvatarCorners(percent)
            // Redraw every avatar on screen now (lists only redraw rows when scrolled otherwise).
            XgramUi.invalidateAll(host.activity)
          }
        )
      }
      switch(R.id.btn_xgramShowSeconds, s(R.string.XgramShowSeconds), s(R.string.XgramShowSecondsSub), checked = settings.showSeconds()) {
        settings.setShowSeconds(it)
      }
      switch(R.id.btn_xgramShowPeerIds, s(R.string.XgramShowPeerIds), s(R.string.XgramShowPeerIdsSub), checked = settings.showPeerIds()) {
        settings.setShowPeerIds(it)
      }
    }

    header(R.string.XgramLayout)
    group {
      switch(R.id.btn_xgramTwoPane, s(R.string.XgramTwoPane), s(R.string.XgramTwoPaneSub), checked = settings.twoPaneLayout()) {
        settings.setTwoPaneLayout(it)
        host.activity.xgramTwoPane()?.update()
      }
    }
    description(R.string.XgramTwoPaneInfo)
    group {
      switch(R.id.btn_xgramLimitMessageWidth, s(R.string.XgramLimitMessageWidth), s(R.string.XgramLimitMessageWidthSub), checked = settings.limitMessageWidth()) {
        settings.setLimitMessageWidth(it)
      }
    }
  }
}

private fun GroupScope.themes(host: XgramComposeHost) {
  value(R.id.btn_xgramThemes, s(R.string.XgramThemes), XgramComposeBridge.currentThemeName()) {
    host.open(XgramThemesController(host.activity, host.tdlib))
  }
}

private fun GroupScope.iconPack(host: XgramComposeHost) {
  value(R.id.btn_xgramIconPack, s(R.string.XgramIconPack), s(XgramIconPack.name(XgramIconPack.current()))) {
    host.open(XgramIconPacksController(host.activity, host.tdlib))
  }
}

private fun GroupScope.appIcon(host: XgramComposeHost) {
  value(R.id.btn_xgramAppIcon, s(R.string.XgramAppIcon), s(XgramAppIcons.current(host.activity).nameRes)) {
    host.open(XgramAppIconsController(host.activity, host.tdlib))
  }
}

private fun GroupScope.emojiSet(host: XgramComposeHost) {
  // Telegram X's emoji set picker (with XGram's System set), as in Settings → Stickers and emoji.
  value(R.id.btn_xgramEmojiSet, s(R.string.XgramEmoji), XgramComposeBridge.emojiSetName()) {
    host.open(SettingsCloudEmojiController(host.activity, host.tdlib))
  }
}

/** Expressive design (Android 13+): turning it off restarts the app with the classic pages. */
private fun PageScope.expressive(host: XgramComposeHost) {
  if (!XgramExpressive.isAvailable()) {
    return
  }
  group {
    switch(R.id.btn_xgramExpressive, s(R.string.XgramExpressive), s(R.string.XgramExpressiveSub), checked = XgramExpressive.isEnabled()) {
      host.confirm(
        ConfirmSpec(
          title = s(R.string.XgramExpressive),
          text = s(R.string.XgramExpressiveInfo),
          confirm = s(R.string.AppUpdateRestart)
        ) {
          XgramExpressive.setEnabled(host.controller, !XgramExpressive.isEnabled())
        }
      )
    }
  }
  description(R.string.XgramExpressiveInfo)
}
