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
import com.fahadali.xgram.chats.XgramFilters
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.tool.UI

/**
 * Hidden people: senders whose messages XGram hides in every chat of this account, without blocking them (they are not
 * told). Add one by username here (or from a message's menu), see who is hidden and show someone again with a tap.
 */
@Composable
fun HiddenPeoplePage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramShadowBanned)) {
    @Suppress("UNUSED_VARIABLE") val version = host.version // the list again after a change
    val tdlib = host.tdlib
    val ids = XgramFilters.shadowBannedIds(tdlib)
    group {
      action(R.id.btn_xgramShadowBanAdd, s(R.string.XgramShadowBanAdd), s(R.string.XgramShadowBanAddSub), icon = R.drawable.baseline_person_add_24) {
        host.prompt(PromptSpec(title = s(R.string.XgramShadowBanAdd), hint = s(R.string.XgramShadowBanAddHint), confirm = s(R.string.XgramShadowBanHide)) { text ->
          val username = text.trim().substringAfterLast("t.me/").removePrefix("@").trim()
          if (username.isNotEmpty()) {
            tdlib.send(TdApi.SearchPublicChat(username)) { chat, _ ->
              UI.post {
                if (chat == null) {
                  UI.showToast(s(R.string.XgramShadowBanNotFound, username), android.widget.Toast.LENGTH_SHORT)
                } else {
                  val userId = tdlib.chatUserId(chat)
                  val sender: TdApi.MessageSender = if (userId != 0L) TdApi.MessageSenderUser(userId) else TdApi.MessageSenderChat(chat.id)
                  XgramFilters.setShadowBanned(tdlib, sender, true)
                  UI.showToast(s(R.string.XgramShadowBanHidden, chat.title), android.widget.Toast.LENGTH_SHORT)
                  host.changed()
                }
              }
            }
          }
        })
      }
    }
    if (ids.isNotEmpty()) {
      header(R.string.XgramShadowBanList)
      group {
        ids.forEach { id ->
          val name = if (id > 0) tdlib.senderName(TdApi.MessageSenderUser(id), false) else tdlib.chatTitle(id)
          val shown = if (name.isNullOrEmpty()) id.toString() else name
          action(R.id.btn_xgramShadowBanEntry, shown, s(R.string.XgramShadowBanTapToShow), key = "hidden_$id") {
            host.confirm(
              ConfirmSpec(text = s(R.string.XgramShadowBanShowAgain, shown), confirm = s(R.string.XgramShadowBanShow)) {
                val sender: TdApi.MessageSender = if (id > 0) TdApi.MessageSenderUser(id) else TdApi.MessageSenderChat(id)
                XgramFilters.setShadowBanned(tdlib, sender, false)
                host.changed()
              }
            )
          }
        }
      }
      group {
        action(R.id.btn_xgramClearShadowBans, s(R.string.XgramClearShadowBans), destructive = true) {
          host.confirm(
            ConfirmSpec(text = s(R.string.XgramClearShadowBansConfirm), confirm = s(R.string.XgramClearShadowBans), destructive = true) {
              XgramFilters.clearShadowBans(tdlib)
              host.changed()
            }
          )
        }
      }
    }
    description(R.string.XgramShadowBanInfo)
  }
}
