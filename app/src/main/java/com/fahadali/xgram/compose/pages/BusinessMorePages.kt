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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.tool.UI

private const val TAG = "XgramBusiness"

/**
 * Intro (Telegram Business): the title and text people see in an empty chat with you, before they write (Telegram's
 * apps show it with your greeting sticker, which XGram keeps but can't pick yet).
 */
@Composable
fun BusinessIntroPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var loaded by remember { mutableStateOf(false) }
  var page by remember { mutableStateOf<TdApi.BusinessStartPage?>(null) }
  var edited by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var keepSticker by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  DisposableEffect(tdlib) {
    var active = true
    tdlib.send(TdApi.GetUserFullInfo(tdlib.myUserId())) { result, _ ->
      UI.post {
        if (active) {
          page = result?.businessInfo?.startPage
          loaded = true
        }
      }
    }
    onDispose { active = false }
  }
  LaunchedEffect(loaded, page) {
    if (loaded && !edited) {
      title = page?.title.orEmpty()
      message = page?.message.orEmpty()
      keepSticker = true
    }
  }
  val titleMax = tdlib.options().businessStartPageTitleLengthMax.coerceAtLeast(1)
  val messageMax = tdlib.options().businessStartPageMessageLengthMax.coerceAtLeast(1)

  SettingsPage(title = str(R.string.XgramBusinessIntro)) {
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    group {
      value(1, s(R.string.XgramBusinessIntroTitle), if (!loaded) "…" else title.ifBlank { s(R.string.XgramBusinessIntroTitleDefault) }, icon = R.drawable.baseline_format_text_24, enabled = enabled) {
        host.prompt(PromptSpec(title = s(R.string.XgramBusinessIntroTitle), hint = s(R.string.XgramBusinessIntroTitleDefault), initial = title) { text ->
          edited = true
          title = text.trim().take(titleMax)
        })
      }
      value(2, s(R.string.XgramBusinessIntroMessage), if (!loaded) "…" else message.ifBlank { s(R.string.XgramBusinessIntroMessageDefault) }, icon = R.drawable.baseline_chat_bubble_24, enabled = enabled) {
        host.prompt(PromptSpec(title = s(R.string.XgramBusinessIntroMessage), hint = s(R.string.XgramBusinessIntroMessageDefault), initial = message, multiLine = true) { text ->
          edited = true
          message = text.trim().take(messageMax)
        })
      }
      if (page?.sticker != null && keepSticker) {
        action(3, s(R.string.XgramBusinessIntroRemoveSticker), s(R.string.XgramBusinessIntroStickerInfo), icon = R.drawable.baseline_delete_24, enabled = enabled) {
          edited = true
          keepSticker = false
        }
      }
    }
    description(R.string.XgramBusinessIntroInfo)
    if (page != null) {
      group {
        action(4, s(R.string.XgramBusinessIntroReset), icon = R.drawable.baseline_delete_24, destructive = true, enabled = enabled) {
          busy = true
          businessSave(tdlib, TdApi.SetBusinessStartPage(null)) { ok ->
            busy = false
            if (ok) {
              page = null
              edited = false
              title = ""
              message = ""
            }
          }
        }
      }
    }
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }
    item("save") {
      SaveButton(enabled && edited, busy) {
        val sticker = page?.sticker?.takeIf { keepSticker }?.sticker?.remote?.id?.takeIf { it.isNotEmpty() }?.let { TdApi.InputFileRemote(it) }
        busy = true
        // Nothing left: Telegram's default intro again
        val input = if (title.isBlank() && message.isBlank() && sticker == null) null else TdApi.InputBusinessStartPage(title, message, sticker)
        businessSave(tdlib, TdApi.SetBusinessStartPage(input)) { ok ->
          busy = false
          if (ok) {
            edited = false
            host.changed()
          }
        }
      }
    }
  }
}

/**
 * Chat links (Telegram Business): links that open a chat with you with a message already typed; each with a title,
 * the text and how many times it was used. Tap one to copy, share, edit or delete it.
 */
@Composable
fun BusinessLinksPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var links by remember { mutableStateOf<Array<TdApi.BusinessChatLink>?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  DisposableEffect(tdlib, reload) {
    var active = true
    tdlib.send(TdApi.GetBusinessChatLinks()) { result, error ->
      UI.post {
        if (active) {
          links = result?.links ?: emptyArray()
          if (error != null) {
            Log.w(TAG, "GetBusinessChatLinks: ${error.code} ${error.message}")
          }
        }
      }
    }
    onDispose { active = false }
  }
  val max = tdlib.options().businessChatLinkCountMax.coerceAtLeast(1)

  SettingsPage(title = str(R.string.XgramBusinessLinks)) {
    val premium = tdlib.hasPremium()
    val list = links
    group {
      action(1, s(R.string.XgramBusinessLinkNew), if (premium) s(R.string.XgramBusinessLinkNewInfo) else s(R.string.XgramPremiumNeeds), icon = R.drawable.baseline_add_24, enabled = premium && list != null && !busy) {
        if ((list?.size ?: 0) >= max) {
          UI.showToast(s(R.string.XgramBusinessLinksFull, max), Toast.LENGTH_LONG)
          return@action
        }
        editLink(host, null) { info ->
          busy = true
          tdlib.send(TdApi.CreateBusinessChatLink(info)) { _, error ->
            UI.post {
              busy = false
              if (error != null) {
                Log.w(TAG, "CreateBusinessChatLink: ${error.code} ${error.message}")
                UI.showToast(s(R.string.XgramBusinessSaveFailed), Toast.LENGTH_LONG)
              }
              reload++
            }
          }
        }
      }
    }
    description(R.string.XgramBusinessLinksInfo)
    when {
      list == null -> loading()
      list.isEmpty() -> empty(R.drawable.baseline_link_24, s(R.string.XgramBusinessLinksEmpty))
      else -> {
        header(R.string.XgramBusinessLinksYours)
        group("links") {
          list.forEach { link ->
            val name = link.title.ifBlank { link.link.removePrefix("https://") }
            val text = link.text.text.replace('\n', ' ').trim()
            link(0, name, listOf(text.ifEmpty { null }, Lang.plural(R.string.xViews, link.viewCount.toLong())).filterNotNull().joinToString(" · "), icon = R.drawable.baseline_link_24, key = "link:" + link.link) {
              linkOptions(host, link) { reload++ }
            }
          }
        }
      }
    }
  }
}

/** Asks for a link's title, then its message; [onDone] gets them as Telegram's InputBusinessChatLink. */
private fun editLink(host: XgramComposeHost, link: TdApi.BusinessChatLink?, onDone: (TdApi.InputBusinessChatLink) -> Unit) {
  host.prompt(PromptSpec(title = s(R.string.XgramBusinessLinkMessage), hint = s(R.string.XgramBusinessLinkMessageHint), initial = link?.text?.text.orEmpty(), confirm = s(R.string.XgramQuickReplyNameConfirm), multiLine = true) { text ->
    val message = text.trim()
    host.prompt(PromptSpec(title = s(R.string.XgramBusinessLinkTitle), hint = s(R.string.XgramBusinessLinkTitleHint), initial = link?.title.orEmpty()) { title ->
      onDone(TdApi.InputBusinessChatLink(TdApi.FormattedText(message, emptyArray()), title.trim().take(32)))
    })
  })
}

private fun linkOptions(host: XgramComposeHost, link: TdApi.BusinessChatLink, changed: () -> Unit) {
  val tdlib = host.tdlib
  host.controller.showOptions(
    link.link,
    intArrayOf(R.id.btn_copyLink, R.id.btn_share, R.id.btn_edit, R.id.btn_delete),
    arrayOf(s(R.string.CopyLink), s(R.string.Share), s(R.string.XgramBusinessLinkEdit), s(R.string.Delete)),
    intArrayOf(ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED),
    intArrayOf(R.drawable.baseline_content_copy_24, R.drawable.baseline_share_24, R.drawable.baseline_edit_24, R.drawable.baseline_delete_24)
  ) { _, id ->
    when (id) {
      R.id.btn_copyLink -> {
        (host.activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(link.title, link.link))
        UI.showToast(R.string.CopiedLink, Toast.LENGTH_SHORT)
      }
      R.id.btn_share -> {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link.link)
        host.activity.startActivity(Intent.createChooser(intent, s(R.string.Share)))
      }
      R.id.btn_edit -> editLink(host, link) { info ->
        tdlib.send(TdApi.EditBusinessChatLink(link.link, info)) { _, error ->
          UI.post {
            if (error != null) {
              Log.w(TAG, "EditBusinessChatLink: ${error.code} ${error.message}")
              UI.showToast(s(R.string.XgramBusinessSaveFailed), Toast.LENGTH_LONG)
            }
            changed()
          }
        }
      }
      R.id.btn_delete -> host.confirm(ConfirmSpec(title = s(R.string.XgramBusinessLinkDelete), text = s(R.string.XgramBusinessLinkDeleteInfo), confirm = s(R.string.Delete), destructive = true) {
        tdlib.send(TdApi.DeleteBusinessChatLink(link.link)) { _, error ->
          UI.post {
            if (error != null) {
              Log.w(TAG, "DeleteBusinessChatLink: ${error.code} ${error.message}")
              UI.showToast(s(R.string.XgramBusinessSaveFailed), Toast.LENGTH_LONG)
            }
            changed()
          }
        }
      })
    }
    true
  }
}

/** A bot's rights, in the order of Telegram's apps: the switch titles and how each reads and changes the object. */
private val BOT_RIGHTS: List<Triple<Int, (TdApi.BusinessBotRights) -> Boolean, (TdApi.BusinessBotRights, Boolean) -> Unit>> = listOf(
  Triple(R.string.XgramBotCanReply, { r -> r.canReply }, { r, v -> r.canReply = v }),
  Triple(R.string.XgramBotCanRead, { r -> r.canReadMessages }, { r, v -> r.canReadMessages = v }),
  Triple(R.string.XgramBotCanDeleteSent, { r -> r.canDeleteSentMessages }, { r, v -> r.canDeleteSentMessages = v }),
  Triple(R.string.XgramBotCanDeleteAll, { r -> r.canDeleteAllMessages }, { r, v -> r.canDeleteAllMessages = v }),
  Triple(R.string.XgramBotCanEditName, { r -> r.canEditName }, { r, v -> r.canEditName = v }),
  Triple(R.string.XgramBotCanEditBio, { r -> r.canEditBio }, { r, v -> r.canEditBio = v }),
  Triple(R.string.XgramBotCanEditPhoto, { r -> r.canEditProfilePhoto }, { r, v -> r.canEditProfilePhoto = v }),
  Triple(R.string.XgramBotCanEditUsername, { r -> r.canEditUsername }, { r, v -> r.canEditUsername = v }),
  Triple(R.string.XgramBotCanViewGifts, { r -> r.canViewGiftsAndStars }, { r, v -> r.canViewGiftsAndStars = v }),
  Triple(R.string.XgramBotCanSellGifts, { r -> r.canSellGifts }, { r, v -> r.canSellGifts = v }),
  Triple(R.string.XgramBotCanGiftSettings, { r -> r.canChangeGiftSettings }, { r, v -> r.canChangeGiftSettings = v }),
  Triple(R.string.XgramBotCanTransferGifts, { r -> r.canTransferAndUpgradeGifts }, { r, v -> r.canTransferAndUpgradeGifts = v }),
  Triple(R.string.XgramBotCanTransferStars, { r -> r.canTransferStars }, { r, v -> r.canTransferStars = v }),
  Triple(R.string.XgramBotCanStories, { r -> r.canManageStories }, { r, v -> r.canManageStories = v })
)

private fun copyRights(r: TdApi.BusinessBotRights): TdApi.BusinessBotRights = TdApi.BusinessBotRights(
  r.canReply, r.canReadMessages, r.canDeleteSentMessages, r.canDeleteAllMessages, r.canEditName, r.canEditBio, r.canEditProfilePhoto,
  r.canEditUsername, r.canViewGiftsAndStars, r.canSellGifts, r.canChangeGiftSettings, r.canTransferAndUpgradeGifts, r.canTransferStars, r.canManageStories
)

/** What Telegram's apps start a new bot with: it may reply and read messages, nothing else. */
private fun defaultRights(): TdApi.BusinessBotRights = TdApi.BusinessBotRights(true, true, false, false, false, false, false, false, false, false, false, false, false, false)

/**
 * Chatbots (Telegram Business): a bot that answers in your private chats for you (one at a time), with the chats it
 * may see and what it may do. Only bots made for Telegram Business can be connected.
 */
@Composable
fun BusinessBotPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var loaded by remember { mutableStateOf(false) }
  var connected by remember { mutableStateOf<TdApi.BusinessConnectedBot?>(null) }
  var edited by remember { mutableStateOf(false) }
  var botUserId by remember { mutableStateOf(0L) }
  var recipients by remember { mutableStateOf(defaultRecipients()) }
  var rights by remember { mutableStateOf(defaultRights()) }
  var busy by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  DisposableEffect(tdlib, reload) {
    var active = true
    tdlib.send(TdApi.GetBusinessConnectedBot()) { result, _ ->
      UI.post {
        if (active) {
          connected = result?.bot // an error: none connected
          loaded = true
        }
      }
    }
    onDispose { active = false }
  }
  LaunchedEffect(loaded, connected) {
    val current = connected
    if (loaded && !edited) {
      botUserId = current?.botUserId ?: 0L
      recipients = current?.recipients ?: defaultRecipients()
      rights = current?.rights?.let { copyRights(it) } ?: defaultRights()
    }
  }

  SettingsPage(title = str(R.string.XgramBusinessBot)) {
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    group {
      val bot = if (botUserId != 0L) tdlib.cache().user(botUserId) else null
      value(
        1, s(R.string.XgramBusinessBotName),
        if (!loaded) "…" else if (bot == null) s(R.string.XgramBusinessBotNone) else "@" + (bot.usernames?.editableUsername ?: bot.firstName),
        icon = R.drawable.deproko_baseline_bots_24, enabled = enabled
      ) {
        host.prompt(PromptSpec(title = s(R.string.XgramBusinessBotName), hint = s(R.string.XgramBusinessBotNameHint), confirm = s(R.string.XgramQuickReplyNameConfirm)) { text ->
          val username = text.trim().removePrefix("@").removePrefix("https://t.me/").removePrefix("t.me/")
          if (username.isEmpty()) {
            return@PromptSpec
          }
          tdlib.send(TdApi.SearchPublicChat(username)) { chat, _ ->
            val user = chat?.let { tdlib.chatUser(it) }
            val type = user?.type as? TdApi.UserTypeBot
            UI.post {
              when {
                user == null -> UI.showToast(s(R.string.XgramBusinessBotNotFound, username), Toast.LENGTH_LONG)
                type == null || !type.canConnectToBusiness -> UI.showToast(s(R.string.XgramBusinessBotCantConnect, username), Toast.LENGTH_LONG)
                else -> {
                  edited = true
                  botUserId = user.id
                }
              }
            }
          }
        })
      }
    }
    description(R.string.XgramBusinessBotInfo)
    if (botUserId != 0L) {
      recipientRows(host, recipients, enabled) { edited = true; recipients = it }
      header(R.string.XgramBusinessBotRights)
      group("rights") {
        BOT_RIGHTS.forEachIndexed { index, (title, read, write) ->
          switch(60 + index, s(title), checked = read(rights), enabled = enabled) { on ->
            edited = true
            rights = copyRights(rights).also { write(it, on) }
          }
        }
      }
      if (connected != null) {
        group {
          action(80, s(R.string.XgramBusinessBotDisconnect), icon = R.drawable.baseline_delete_24, destructive = true, enabled = enabled) {
            val id = connected?.botUserId ?: return@action
            host.confirm(ConfirmSpec(title = s(R.string.XgramBusinessBotDisconnect), text = s(R.string.XgramBusinessBotDisconnectInfo), confirm = s(R.string.XgramBusinessBotDisconnectConfirm), destructive = true) {
              busy = true
              businessSave(tdlib, TdApi.DeleteBusinessConnectedBot(id)) { ok ->
                busy = false
                if (ok) {
                  edited = false
                  connected = null
                  botUserId = 0L
                  reload++
                }
              }
            })
          }
        }
      }
    }
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }
    item("save") {
      SaveButton(enabled && edited && botUserId != 0L, busy) {
        busy = true
        businessSave(tdlib, TdApi.SetBusinessConnectedBot(TdApi.BusinessConnectedBot(botUserId, recipients, rights))) { ok ->
          busy = false
          if (ok) {
            edited = false
            reload++
          }
        }
      }
    }
  }
}
