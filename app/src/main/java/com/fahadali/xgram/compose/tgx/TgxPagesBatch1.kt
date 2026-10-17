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

import android.view.View
import com.fahadali.xgram.chats.XgramCommunitySettingsController
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.EditRightsController
import org.thunderdog.challegram.ui.EditSessionController
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.PrivacyExceptionController
import org.thunderdog.challegram.ui.RequestController
import org.thunderdog.challegram.ui.Settings2FAController
import org.thunderdog.challegram.ui.SettingsArchiveChatListController
import org.thunderdog.challegram.ui.SettingsPhoneController
import org.thunderdog.challegram.ui.SettingsPrivacyKeyController
import org.thunderdog.challegram.ui.WaitForPremiumController

/**
 * Batch 1 (privacy, security and account): one line per controller, its hints after `to`
 * (TgxPageHints.DEFAULT, or an object overriding kind/trailing/largeTitle). Only this batch edits this file.
 *
 * Rows the generic mapping keeps as real views on these pages: the public photo preview (PrivacyKey), chat rows
 * (PrivacyException, EditRights) and edit fields (2FA, EditRights, Request). A community's chats are native chat rows
 * (TgxChatRowsHints).
 */
internal object TgxPagesBatch1 {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    SettingsPrivacyKeyController::class.java to TgxPageHints.DEFAULT,
    PrivacyExceptionController::class.java to TgxPageHints.DEFAULT,
    Settings2FAController::class.java to TgxPageHints.DEFAULT,
    EditRightsController::class.java to Batch1EditRightsHints,
    SettingsPhoneController::class.java to TgxPageHints.DEFAULT,
    WaitForPremiumController::class.java to TgxPageHints.DEFAULT,
    SettingsArchiveChatListController::class.java to TgxPageHints.DEFAULT,
    EditSessionController::class.java to TgxPageHints.DEFAULT,
    RequestController::class.java to TgxPageHints.DEFAULT,
    // Its hero (a 96dp avatar with a camera badge) stays the real view on the page; its chats are native rows.
    XgramCommunitySettingsController::class.java to TgxChatRowsHints.INSTANCE,
  )
}

/**
 * Admin rights, member restrictions and chat permissions.
 * - A rights group row (Send media) has two targets: its toggler toggles the whole group, the rest of the row shows or
 *   hides the group's rights. The core draws it as a split switch row (its toggler has a click of its own); the
 *   switch shows the third state while only some of the group's rights are allowed.
 * - A single right with Telegram X's negative-state toggler (red when off, green when on) is an M3 switch: its row tap
 *   toggles it, or shows why it can't be changed (a tooltip at the toggler, lock shown). It has two states only.
 */
private object Batch1EditRightsHints : TgxPageHints {
  override fun isPartial(c: ViewController<*>, item: ListItem, view: View): Boolean =
    item.id == R.id.btn_togglePermissionGroup && (c as? EditRightsController)?.xgramIsRightsGroupPartial(item) == true

  override fun kind(item: ListItem, view: View): TgxRowKind? {
    if (item.viewType == ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE && view is SettingView && view.toggler != null) {
      if (!view.drawModifiers.isNullOrEmpty()) {
        return TgxRowKind.FALLBACK
      }
      val icon = view.iconResource
      if ((icon == 0 && view.hasIcon()) || (icon != 0 && !TgxIcons.supported(view.context, icon))) {
        return TgxRowKind.FALLBACK
      }
      return TgxRowKind.SWITCH
    }
    return null
  }
}
