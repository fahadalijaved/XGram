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
import com.fahadali.xgram.appearance.XgramAmoled
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramFonts
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
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.theme.XgramFontFamilies
import com.fahadali.xgram.compose.ui.GroupScope
import com.fahadali.xgram.compose.ui.PageScope
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.ui.MessagesController
import org.thunderdog.challegram.ui.SettingsCloudEmojiController
import org.thunderdog.challegram.ui.SettingsThemeController

/**
 * Appearance: Expressive design (and Telegram X's pages in Compose), the colors, text and icons, messages and avatars,
 * then the page for large screens. Rows a look can't use are left out, not greyed: Colors, Night mode and Pure black
 * are Expressive design's, Themes and Material You the classic look's.
 */
@Composable
fun AppearancePage() {
  val host = LocalXgramHost.current
  // Material You rebuilds the themes: a loading indicator stands in for its switch until that is done.
  var materialYouBusy by remember { mutableStateOf(false) }
  // Colors > More colors: its dialog is open
  var moreColors by remember { mutableStateOf(false) }
  SettingsPage(title = str(R.string.XgramAppearance)) {
    val settings = XgramSettings.instance()
    // Values from Telegram X, worked out once per build of the page, not while rows are drawn.
    val themeName = XgramComposeBridge.currentThemeName()
    val emojiSetName = XgramComposeBridge.emojiSetName()
    val avatarCornersName = XgramComposeBridge.avatarCornersName(settings.avatarCorners())
    val expressiveOn = XgramExpressive.isEnabled()

    expressive(host)

    header(R.string.XgramColors)
    if (expressiveOn) {
      // Expressive design's colors: the wallpaper's or a picked one (AppearanceColors.kt)
      colorSource(host) { moreColors = true }
      group {
        // Telegram X's night mode: its Themes page has Auto night mode (the side menu has the switch).
        value(R.id.btn_xgramAutoNightMode, s(R.string.XgramAutoNightMode), s(XgramSettingDeps.autoNightModeName())) {
          host.open(SettingsThemeController(host.activity, host.tdlib))
        }
        if (XgramAmoled.isAvailable()) {
          // Black Material You surfaces in night mode; the whole app (this page too) repaints at once.
          switch(
            R.id.btn_xgramPureBlack, s(R.string.XgramPureBlack), s(R.string.XgramPureBlackSub),
            checked = XgramAmoled.isSaved()
          ) {
            XgramAmoled.setEnabled(it)
            host.themeChanged()
          }
        }
      }
      description(R.string.XgramThemeExpressiveInfo)
    } else if (XgramMaterialYou.isAvailable()) {
      group {
        themes(host, themeName)
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
    } else {
      group {
        themes(host, themeName)
      }
    }

    header(R.string.XgramHeaderTextIcons)
    group {
      font(host)
      iconPack(host)
      emojiSet(host, emojiSetName)
      appIcon(host)
      notificationIcon(host)
    }

    header(R.string.XgramHeaderMessagesAvatars)
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
      // The chat's floating buttons in the avatars' shape: only Expressive design draws them as M3 FABs. Open chats
      // (the other pane) redraw now; their buttons take the shape before they draw (CircleButton.draw).
      if (expressiveOn) {
        switch(
          R.id.btn_xgramChatFabsAvatarShape, s(R.string.XgramChatFabsAvatarShape), s(R.string.XgramChatFabsAvatarShapeSub),
          checked = settings.chatFabsAvatarShape()
        ) {
          settings.setChatFabsAvatarShape(it)
          XgramUi.invalidateAll(host.activity)
        }
      }
    }

    // Two panes, the fold, drag and drop: mostly for tablets and unfolded phones, so a page of their own
    group {
      link(
        R.id.btn_xgramLargeScreens, s(R.string.XgramLargeScreens), s(R.string.XgramLargeScreensSummary),
        icon = R.drawable.baseline_devices_other_24
      ) {
        host.openPage(XgramPage.LARGE_SCREENS)
      }
    }
  }
  if (moreColors) {
    XgramMoreColorsDialog { moreColors = false }
  }
}

/** The classic look's themes (with Expressive design, its colors are Material You's whatever the theme: not shown). */
private fun GroupScope.themes(host: XgramComposeHost, name: String) {
  value(R.id.btn_xgramThemes, s(R.string.XgramThemes), name) {
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

/** XGram's X or Telegram X's original glyph in the status bar, apart from the app icon. New notifications use it. */
private fun GroupScope.notificationIcon(host: XgramComposeHost) {
  val settings = XgramSettings.instance()
  val current = settings.notificationIcon()
  val options = listOf(
    s(R.string.XgramNotificationIconXgram) to XgramSettings.NOTIFICATION_ICON_XGRAM,
    s(R.string.XgramNotificationIconTelegramX) to XgramSettings.NOTIFICATION_ICON_TELEGRAM_X
  )
  value(R.id.btn_xgramNotificationIcon, s(R.string.XgramNotificationIcon), options.first { it.second == current }.first) {
    host.choose(
      ChoiceSpec(
        title = s(R.string.XgramNotificationIcon),
        info = s(R.string.XgramNotificationIconInfo),
        options = options,
        selected = current
      ) { icon ->
        settings.setNotificationIcon(icon)
      }
    )
  }
}

private fun GroupScope.emojiSet(host: XgramComposeHost, name: String) {
  // Telegram X's emoji set picker (with XGram's System set), as in Settings → Stickers and emoji.
  value(R.id.btn_xgramEmojiSet, s(R.string.XgramEmoji), name) {
    host.open(SettingsCloudEmojiController(host.activity, host.tdlib))
  }
}

/**
 * The app font (XgramFonts, Android 8+): Google Sans Flex (the default), Google Sans Rounded or System, each option
 * with a preview line, both in its own font. A new font is confirmed, then the app restarts: Telegram X keeps its
 * paints and measured text for the whole process.
 */
private fun GroupScope.font(host: XgramComposeHost) {
  if (!XgramFonts.isAvailable()) {
    return
  }
  val saved = XgramSettings.instance().appFont()
  value(R.id.btn_xgramFont, s(R.string.XgramFont), XgramFonts.name(saved)) {
    val fonts = listOf(XgramFonts.FLEX, XgramFonts.ROUNDED, XgramFonts.SYSTEM)
    val preview = s(R.string.XgramFontPreview)
    host.choose(
      ChoiceSpec(
        title = s(R.string.XgramFont),
        info = s(R.string.XgramFontInfo),
        options = fonts.map { XgramFonts.name(it) to it },
        selected = saved,
        subtitles = fonts.map { preview },
        fonts = fonts.map { XgramFontFamilies.preview(it) }
      ) { font ->
        host.confirm(
          ConfirmSpec(
            title = s(R.string.XgramFont),
            text = s(R.string.XgramFontRestartText, XgramFonts.name(font)),
            confirm = s(R.string.AppUpdateRestart)
          ) {
            XgramFonts.apply(host.activity, font)
          }
        )
      }
    )
  }
}

/**
 * Expressive design (Android 13+), the page's first group: turning it off restarts the app with the classic pages. With
 * it, Telegram X's own pages in Compose.
 */
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
  // Without it, Colors isn't on the page: the note says custom colors come with it
  description(if (XgramExpressive.isEnabled()) R.string.XgramExpressiveNote else R.string.XgramExpressiveNoteClassic)
}
