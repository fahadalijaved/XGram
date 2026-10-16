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
import com.fahadali.xgram.XgramSettingDeps
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
import com.fahadali.xgram.layout.XgramWindow
import org.thunderdog.challegram.R
import org.thunderdog.challegram.ui.MessagesController
import org.thunderdog.challegram.ui.SettingsCloudEmojiController
import org.thunderdog.challegram.ui.SettingsThemeController

/** Appearance: theme, Material You, Expressive design, icons and emoji; chat look; layout. */
@Composable
fun AppearancePage() {
  val host = LocalXgramHost.current
  // Material You rebuilds the themes: a loading indicator stands in for its switch until that is done.
  var materialYouBusy by remember { mutableStateOf(false) }
  SettingsPage(title = str(R.string.XgramAppearance)) {
    val settings = XgramSettings.instance()
    // Values from Telegram X, worked out once per build of the page, not while rows are drawn.
    val themeName = XgramComposeBridge.currentThemeName()
    val emojiSetName = XgramComposeBridge.emojiSetName()
    val avatarCornersName = XgramComposeBridge.avatarCornersName(settings.avatarCorners())

    header(R.string.XgramHeaderTheme)
    if (XgramMaterialYou.isAvailable()) {
      // Expressive design draws with the Material You colors whatever the theme: Themes and Material You are locked
      // (shown on, nothing saved), and night mode is what is left to pick. Expressive needs Android 13, so only here.
      val themesLocked = XgramSettingDeps.themesLocked()
      group {
        themes(host, themeName)
        switch(
          R.id.btn_xgramMaterialYou, s(R.string.XgramMaterialYou),
          s(if (themesLocked) R.string.XgramMaterialYouExpressive else R.string.XgramMaterialYouSub),
          checked = themesLocked || XgramMaterialYou.isEnabled(), enabled = !themesLocked, busy = materialYouBusy
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
        if (themesLocked) {
          // Telegram X's night mode: its Themes page has Auto night mode (the side menu has the switch).
          value(R.id.btn_xgramAutoNightMode, s(R.string.XgramAutoNightMode), s(XgramSettingDeps.autoNightModeName())) {
            host.open(SettingsThemeController(host.activity, host.tdlib))
          }
        }
      }
      description(if (themesLocked) R.string.XgramThemeExpressiveInfo else R.string.XgramMaterialYouInfo)
      expressive(host)
      group {
        iconPack(host)
        appIcon(host)
        emojiSet(host, emojiSetName)
      }
    } else {
      group {
        themes(host, themeName)
        iconPack(host)
        appIcon(host)
        emojiSet(host, emojiSetName)
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
      value(R.id.btn_xgramAvatarCorners, s(R.string.XgramAvatarCorners), avatarCornersName) {
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
    // How wide the panes are: half each, or the width set with the handle on the line between them. Both only with
    // two panes; Split evenly leaves nothing to resize.
    val twoPaneReason = XgramSettingDeps.twoPaneOptionReason()
    val resizeReason = XgramSettingDeps.paneResizableReason()
    group {
      switch(
        R.id.btn_xgramPaneSplitEvenly, s(R.string.XgramPaneSplitEvenly),
        s(if (twoPaneReason != 0) twoPaneReason else R.string.XgramPaneSplitEvenlySub),
        checked = settings.paneSplitEvenly(), enabled = XgramSettingDeps.twoPaneOptionsEnabled()
      ) {
        settings.setPaneSplitEvenly(it)
        host.activity.xgramTwoPane()?.update()
      }
      switch(
        R.id.btn_xgramPaneResizable, s(R.string.XgramPaneResizable),
        s(if (resizeReason != 0) resizeReason else R.string.XgramPaneResizableSub),
        checked = settings.paneResizable(), enabled = XgramSettingDeps.paneResizableEnabled()
      ) {
        settings.setPaneResizable(it)
        host.activity.xgramTwoPane()?.update()
      }
    }
    description(XgramSettingDeps.paneInfo())
    group {
      switch(R.id.btn_xgramLimitMessageWidth, s(R.string.XgramLimitMessageWidth), s(R.string.XgramLimitMessageWidthSub), checked = settings.limitMessageWidth()) {
        settings.setLimitMessageWidth(it)
      }
    }
    // Foldables: only where a fold can be reported (as the classic page).
    if (XgramWindow.isPostureSupported()) {
      group {
        switch(
          R.id.btn_xgramFoldHingeSplit, s(R.string.XgramFoldHingeSplit),
          s(if (twoPaneReason != 0) twoPaneReason else R.string.XgramFoldHingeSplitSub),
          checked = settings.foldHingeSplit(), enabled = XgramSettingDeps.twoPaneOptionsEnabled()
        ) {
          settings.setFoldHingeSplit(it)
          host.activity.xgramTwoPane()?.update()
        }
        switch(R.id.btn_xgramFoldTabletop, s(R.string.XgramFoldTabletop), s(R.string.XgramFoldTabletopSub), checked = settings.foldTabletop()) {
          settings.setFoldTabletop(it)
        }
      }
      description(R.string.XgramFoldInfo)
    }
    group {
      switch(R.id.btn_xgramDragDrop, s(R.string.XgramDragDrop), s(R.string.XgramDragDropSub), checked = settings.dragDrop()) {
        settings.setDragDrop(it)
      }
      switch(R.id.btn_xgramOpenBeside, s(R.string.XgramOpenBeside), s(R.string.XgramOpenBesideSub), checked = settings.openBeside()) {
        settings.setOpenBeside(it)
      }
    }
    description(R.string.XgramDragDropInfo)
  }
}

private fun GroupScope.themes(host: XgramComposeHost, name: String) {
  // Locked with Expressive design (XgramSettingDeps.themesLocked): the reason instead of the theme's name.
  val locked = XgramSettingDeps.themesLocked()
  value(R.id.btn_xgramThemes, s(R.string.XgramThemes), if (locked) s(R.string.XgramThemesExpressive) else name, enabled = !locked) {
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

private fun GroupScope.emojiSet(host: XgramComposeHost, name: String) {
  // Telegram X's emoji set picker (with XGram's System set), as in Settings → Stickers and emoji.
  value(R.id.btn_xgramEmojiSet, s(R.string.XgramEmoji), name) {
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
    if (XgramExpressive.isEnabled()) {
      // Telegram X's own settings pages drawn in Compose (com.fahadali.xgram.compose.tgx); applies to pages opened next.
      val settings = XgramSettings.instance()
      switch(R.id.btn_xgramComposeTgx, s(R.string.XgramComposeTgx), s(R.string.XgramComposeTgxSub), checked = settings.composeTgxPages()) {
        settings.setComposeTgxPages(it)
      }
    }
  }
  description(R.string.XgramExpressiveInfo)
}
