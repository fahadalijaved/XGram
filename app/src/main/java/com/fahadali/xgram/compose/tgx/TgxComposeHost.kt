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
package com.fahadali.xgram.compose.tgx

import android.graphics.Rect
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeController
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import org.thunderdog.challegram.BaseActivity
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.telegram.Tdlib

/**
 * The toolkit's host (LocalXgramHost) on a Telegram X page drawn in Compose. The page's values live in its views, so
 * [changed] reads every row again; sheets and dialogs stay Telegram X's own (the controller shows them), so the dialog
 * calls do nothing.
 */
internal class TgxComposeHost(private val r: TgxSettingsRenderer) : XgramComposeHost {
  override val controller: ViewController<*>
    get() = r.controller

  override val activity: BaseActivity
    get() = r.controller.context()

  override val tdlib: Tdlib
    get() = r.controller.tdlib()

  // Not an XGram page.
  override val page: XgramPage
    get() = XgramPage.MAIN

  override val version: Int
    get() = 0

  override fun changed() {
    r.rereadAllSoon()
  }

  override val langVersion: Int
    get() = r.langVersion.intValue

  override val themeVersion: Int
    get() = r.themeVersion.intValue

  override val isDark: Boolean
    get() = r.isDark.value

  // The page sits below Telegram X's header.
  override val topInset: Int
    get() = 0

  override val bottomInset: Int
    get() = r.bottomInset.intValue

  override val highlightId: Int
    get() = 0

  override val highlightKey: String?
    get() = null

  override fun consumeHighlight() = Unit

  override fun open(c: ViewController<*>) {
    r.controller.navigateTo(c)
  }

  override fun openPage(p: XgramPage, highlightId: Int, key: String?) {
    r.controller.navigateTo(XgramComposeController.create(activity, tdlib, p, highlightId, key))
  }

  override fun back() {
    r.controller.navigateBack()
  }

  override val canGoBack: Boolean
    get() = true

  override fun choose(spec: ChoiceSpec<*>) = Unit

  override fun confirm(spec: ConfirmSpec) = Unit

  override fun prompt(spec: PromptSpec) = Unit

  override val dialog: Any?
    get() = null

  override fun dismissDialog() = Unit

  // Back is Telegram X's: the controller handles it.
  override fun setBackConsumer(f: (() -> Unit)?) = Unit

  override fun noSwipeBack(key: Any, bounds: (() -> Rect?)?) {
    r.noSwipeBack(key, bounds)
  }

  override fun themeChanged() {
    r.themeChanged()
  }

  override fun restartActivity() {
    r.controller.scheduleActivityRestart()
  }
}
