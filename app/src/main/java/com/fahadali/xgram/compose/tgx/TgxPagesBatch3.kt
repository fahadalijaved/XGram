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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.appearance.XgramR6B5Pages
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsCloudController
import org.thunderdog.challegram.unsorted.Settings
import org.thunderdog.challegram.widget.RadioView
import org.thunderdog.challegram.ui.EditBioController
import org.thunderdog.challegram.ui.EditChatFolderInviteLinkController
import org.thunderdog.challegram.ui.EditChatLinkController
import org.thunderdog.challegram.ui.EditDeleteAccountReasonController
import org.thunderdog.challegram.ui.EditLanguageController
import org.thunderdog.challegram.ui.EditNameController
import org.thunderdog.challegram.ui.EditProxyController
import org.thunderdog.challegram.ui.EditTextController
import org.thunderdog.challegram.ui.LanguageController
import org.thunderdog.challegram.ui.SettingsCloudEmojiController
import org.thunderdog.challegram.ui.SettingsCloudIconController
import org.thunderdog.challegram.ui.SettingsLanguageController
import org.thunderdog.challegram.ui.SettingsStickersAndEmojiController

/**
 * Batch 3 (language, stickers and edit forms): one line per controller, its hints after `to`
 * (TgxPageHints.DEFAULT, or an object overriding kind/trailing/largeTitle). Only this batch edits this file.
 *
 * Every page here is drawn by the generic mapping; what it cannot draw natively stays the real Telegram X row:
 * edit fields (EDITTEXT*), chats (CHAT_SMALL, CHAT_BETTER), LIST_INFO_VIEW and rows with a preview drawn into them
 * (emoji, reactions, emoji and icon packs). The core draws the rest natively, among them the folder link's header
 * checkbox (HEADER_WITH_CHECKBOX), the invite link sliders whose values the page sets on the view itself
 * (EditChatLinkController) and the language download progress. Taps, long presses, Done and the controllers' own
 * update paths are unchanged.
 */
internal object TgxPagesBatch3 {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    // Language list: radio rows (long press deletes a custom language; the swipe to delete is classic only).
    SettingsLanguageController::class.java to TgxPageHints.DEFAULT,
    // Strings of a language pack: untranslated strings keep their red title; search rebuilds the rows.
    LanguageController::class.java to TgxPageHints.DEFAULT,
    EditLanguageController::class.java to TgxPageHints.DEFAULT,
    SettingsStickersAndEmojiController::class.java to TgxPageHints.DEFAULT,
    // SettingsCloudController is abstract: each pack page is listed by its own class.
    SettingsCloudEmojiController::class.java to CloudSets,
    SettingsCloudIconController::class.java to CloudSets,
    EditBioController::class.java to TgxPageHints.DEFAULT,
    // EditTextController's one page in the app is its subclass (Delete account reason).
    EditTextController::class.java to TgxPageHints.DEFAULT,
    EditDeleteAccountReasonController::class.java to TgxPageHints.DEFAULT,
    EditNameController::class.java to EditName,
    EditProxyController::class.java to TgxPageHints.DEFAULT,
    EditChatLinkController::class.java to TgxPageHints.DEFAULT,
    EditChatFolderInviteLinkController::class.java to TgxPageHints.DEFAULT,
  )
}

/**
 * Names (sign-up, your name, a contact's): a form, its column at most 480dp wide; the sign-up step says under its
 * title what the name is for, in bodyMedium.
 */
private object EditName : TgxPageHints {
  override fun largeSubtitle(c: ViewController<*>): CharSequence? =
    if ((c as? EditNameController)?.isUnauthorized() == true) Lang.getString(R.string.XgramR6B5SignupHint) else null

  override fun formWidth(c: ViewController<*>): Boolean = true
}

/**
 * Emoji sets and icon sets (SettingsCloudController): each set is a radio row (the set in use, or the one being
 * installed), its name in bodyLarge and its state in bodyMedium, its preview (the set's image; the phone's own emoji
 * for System) 40dp at the end. While a set downloads its state reads "Downloading" and a 24dp wavy ring shows the
 * progress at the end, in place of the percentage.
 */
private object CloudSets : TgxPageHints {
  override fun kind(item: ListItem, view: View): TgxRowKind? =
    if (item.id == R.id.btn_settings && view is SettingView && view.childCount > 0 && view.getChildAt(0) is RadioView) {
      TgxRowKind.RADIO
    } else {
      null
    }

  override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
    val page = c as? SettingsCloudController<*> ?: return null
    val setting = item.data as? Settings.CloudSetting ?: return null
    val progress = page.xgramPendingProgress(setting)
    if (progress == -1f || view !is SettingView) {
      return null
    }
    return TgxRowText(view.name, page.xgramPendingLabel(), progress = progress)
  }

  override fun trailing(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
    val page = c as? SettingsCloudController<*> ?: return null
    val setting = item.data as? Settings.CloudSetting ?: return null
    return { TgxSetPreview(page, setting) }
  }
}

@Composable
private fun TgxSetPreview(page: SettingsCloudController<*>, setting: Settings.CloudSetting) {
  AndroidView(
    factory = { context -> XgramR6B5Pages.PackPreviewView(context) },
    modifier = Modifier.size(40.dp),
    update = { view -> view.setSetting(page, setting) },
    onRelease = { view -> view.destroy() }
  )
}
