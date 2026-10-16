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
    SettingsCloudEmojiController::class.java to TgxPageHints.DEFAULT,
    SettingsCloudIconController::class.java to TgxPageHints.DEFAULT,
    EditBioController::class.java to TgxPageHints.DEFAULT,
    // EditTextController's one page in the app is its subclass (Delete account reason).
    EditTextController::class.java to TgxPageHints.DEFAULT,
    EditDeleteAccountReasonController::class.java to TgxPageHints.DEFAULT,
    EditNameController::class.java to TgxPageHints.DEFAULT,
    EditProxyController::class.java to TgxPageHints.DEFAULT,
    EditChatLinkController::class.java to TgxPageHints.DEFAULT,
    EditChatFolderInviteLinkController::class.java to TgxPageHints.DEFAULT,
  )
}
