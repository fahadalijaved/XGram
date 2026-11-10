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

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.business.XgramQuickReplies
import com.fahadali.xgram.business.XgramQuickReplyCommands
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.tool.UI

private const val TAG = "XgramQuickReplies"

/** The most messages a quick reply holds (Telegram's option, 20 when it hasn't said). */
private fun maxMessages(host: XgramComposeHost): Int =
  host.tdlib.options().quickReplyShortcutMessageCountMax.coerceAtLeast(1)

/** The most quick replies an account has (Telegram's option, 100 when it hasn't said). */
private fun maxShortcuts(host: XgramComposeHost): Int =
  host.tdlib.options().quickReplyShortcutCountMax.coerceAtLeast(1)

/** Redraws while mounted whenever the quick replies change; returns the change count to read. */
@Composable
private fun rememberQuickReplies(store: XgramQuickReplies): Int {
  var version by remember { mutableIntStateOf(0) }
  DisposableEffect(store) {
    val listener = XgramQuickReplies.Listener { version++ }
    store.addListener(listener)
    store.load()
    onDispose { store.removeListener(listener) }
  }
  return version
}

/**
 * XGram: Quick replies (Telegram Business, with Premium), like official Telegram's. Shortcuts with messages to send in
 * one tap: type "/" in a private chat and pick one. Each opens its own page to add, edit and delete its messages.
 */
@Composable
fun QuickRepliesPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val store = tdlib.xgramQuickReplies()
  val version = rememberQuickReplies(store)

  SettingsPage(title = str(R.string.XgramQuickReplies)) {
    version // read: the list follows Telegram's updates
    val premium = tdlib.hasPremium()
    val list = store.list()
    group {
      action(1, s(R.string.XgramQuickReplyNew), if (premium) s(R.string.XgramQuickReplyNewInfo) else s(R.string.XgramPremiumNeeds), icon = R.drawable.baseline_add_24, enabled = premium) {
        if (list.size >= maxShortcuts(host)) {
          UI.showToast(s(R.string.XgramQuickRepliesFull, maxShortcuts(host)), Toast.LENGTH_LONG)
        } else {
          askName(host, s(R.string.XgramQuickReplyNew), "") { name ->
            host.openPage(XgramPage.QUICK_REPLY, 0, name)
          }
        }
      }
    }
    description(R.string.XgramQuickRepliesInfo)
    if (premium) {
      when {
        !store.isListKnown -> loading()
        list.isEmpty() -> empty(R.drawable.baseline_reply_24, s(R.string.XgramQuickRepliesEmpty))
        else -> {
          header(R.string.XgramQuickRepliesYours)
          group("shortcuts") {
            list.forEach { shortcut ->
              link(0, "/" + shortcut.name, XgramQuickReplyCommands.describe(shortcut), icon = R.drawable.baseline_reply_24, key = "sc:" + shortcut.id) {
                host.openPage(XgramPage.QUICK_REPLY, 0, shortcut.name)
              }
            }
          }
        }
      }
    }
  }
}

/**
 * One quick reply: its messages (tap to edit a text, long-press for more), Add a message, Rename and Delete. A new
 * quick reply has no messages yet: Telegram makes it with the first one. The name comes in the page's key.
 */
@Composable
fun QuickReplyPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val store = tdlib.xgramQuickReplies()
  val version = rememberQuickReplies(store)
  var name by remember { mutableStateOf(host.highlightKey.orEmpty()) }
  var busy by remember { mutableStateOf(false) }
  var askedId by remember { mutableIntStateOf(0) }

  SettingsPage(title = "/$name") {
    version // read: the messages follow Telegram's updates
    val premium = tdlib.hasPremium()
    val shortcut = store.findByName(name)
    val messages = shortcut?.let { store.messages(it.id) }
    if (shortcut != null && messages == null && askedId != shortcut.id) {
      askedId = shortcut.id
      store.loadMessages(shortcut.id)
    }

    header(R.string.XgramQuickReplyMessages)
    when {
      shortcut != null && messages == null -> loading()
      messages.isNullOrEmpty() -> description(R.string.XgramQuickReplyNoMessages)
      else -> group("messages") {
        messages.forEachIndexed { index, message ->
          val text = XgramQuickReplyCommands.preview(message.content)
          val editable = message.canBeEdited && message.content is TdApi.MessageText
          value(
            0, text.ifEmpty { s(R.string.XgramQuickReplyOther) }, if (message.sendingState is TdApi.MessageSendingStateFailed) s(R.string.XgramQuickReplyNotSaved) else null,
            icon = if (message.content is TdApi.MessageText) R.drawable.baseline_chat_bubble_24 else R.drawable.baseline_insert_drive_file_24,
            enabled = premium && !busy, key = "m:" + message.id,
            longClickLabel = s(R.string.XgramQuickReplyMessageOptions),
            onLongClick = { messageOptions(host, shortcut!!.id, message, editable, index, messages.size) }
          ) {
            if (editable) {
              editMessage(host, shortcut!!.id, message)
            } else {
              messageOptions(host, shortcut!!.id, message, false, index, messages.size)
            }
          }
        }
      }
    }
    group {
      val count = messages?.size ?: 0
      action(1, s(R.string.XgramQuickReplyAdd), if (premium) s(R.string.XgramQuickReplyAddInfo) else s(R.string.XgramPremiumNeeds), icon = R.drawable.baseline_add_24, enabled = premium && !busy) {
        if (count >= maxMessages(host)) {
          UI.showToast(s(R.string.XgramQuickReplyFull, maxMessages(host)), Toast.LENGTH_LONG)
          return@action
        }
        host.prompt(PromptSpec(title = s(R.string.XgramQuickReplyAdd), hint = s(R.string.XgramQuickReplyMessageHint), confirm = s(R.string.XgramQuickReplyAddConfirm), multiLine = true) { text ->
          val value = text.trim()
          if (value.isEmpty()) {
            return@PromptSpec
          }
          busy = true
          tdlib.send(TdApi.AddQuickReplyShortcutMessage(name, 0, TdApi.InputMessageText(TdApi.FormattedText(value, emptyArray()), null, false))) { _, error ->
            UI.post {
              busy = false
              if (error != null) {
                Log.w(TAG, "AddQuickReplyShortcutMessage: ${error.code} ${error.message}")
                UI.showToast(s(R.string.XgramQuickReplyFailed), Toast.LENGTH_LONG)
              } else {
                host.changed()
              }
            }
          }
        })
      }
    }
    description(s(R.string.XgramQuickReplyUse, name), "use")

    if (shortcut != null) {
      group {
        action(2, s(R.string.XgramQuickReplyRename), "/" + shortcut.name, icon = R.drawable.baseline_edit_24, enabled = premium && !busy) {
          askName(host, s(R.string.XgramQuickReplyRename), shortcut.name) { newName ->
            if (newName == shortcut.name) {
              return@askName
            }
            tdlib.send(TdApi.SetQuickReplyShortcutName(shortcut.id, newName)) { _, error ->
              UI.post {
                if (error != null) {
                  Log.w(TAG, "SetQuickReplyShortcutName: ${error.code} ${error.message}")
                  UI.showToast(s(R.string.XgramQuickReplyFailed), Toast.LENGTH_LONG)
                } else {
                  name = newName
                }
              }
            }
          }
        }
        action(3, s(R.string.XgramQuickReplyDelete), null, icon = R.drawable.baseline_delete_24, destructive = true, enabled = !busy) {
          host.confirm(ConfirmSpec(title = s(R.string.XgramQuickReplyDelete), text = s(R.string.XgramQuickReplyDeleteInfo, shortcut.name), confirm = s(R.string.Delete), destructive = true) {
            tdlib.send(TdApi.DeleteQuickReplyShortcut(shortcut.id)) { _, error ->
              UI.post {
                if (error != null) {
                  Log.w(TAG, "DeleteQuickReplyShortcut: ${error.code} ${error.message}")
                  UI.showToast(s(R.string.XgramQuickReplyFailed), Toast.LENGTH_LONG)
                } else {
                  host.back()
                }
              }
            }
          })
        }
      }
    }
  }
}

/** Asks for a shortcut name and checks it with Telegram (1-32 letters, digits or _, not taken) before [onName]. */
private fun askName(host: XgramComposeHost, title: String, initial: String, onName: (String) -> Unit) {
  val tdlib = host.tdlib
  host.prompt(PromptSpec(title = title, hint = s(R.string.XgramQuickReplyNameHint), confirm = s(R.string.XgramQuickReplyNameConfirm), initial = initial) { text ->
    val name = text.trim().removePrefix("/")
    if (name.isEmpty()) {
      return@PromptSpec
    }
    if (name == initial) {
      onName(name)
      return@PromptSpec
    }
    val taken = tdlib.xgramQuickReplies().findByName(name)
    if (taken != null) {
      UI.showToast(s(R.string.XgramQuickReplyNameTaken, name), Toast.LENGTH_LONG)
      return@PromptSpec
    }
    tdlib.send(TdApi.CheckQuickReplyShortcutName(name)) { _, error ->
      UI.post {
        if (error != null) {
          Log.w(TAG, "CheckQuickReplyShortcutName: ${error.code} ${error.message}")
          UI.showToast(s(R.string.XgramQuickReplyNameInvalid), Toast.LENGTH_LONG)
        } else {
          onName(name)
        }
      }
    }
  })
}

/** Edits a text message of a quick reply (its formatting goes: the text is plain). */
private fun editMessage(host: XgramComposeHost, shortcutId: Int, message: TdApi.QuickReplyMessage) {
  val tdlib = host.tdlib
  val current = (message.content as? TdApi.MessageText)?.text?.text.orEmpty()
  host.prompt(PromptSpec(title = s(R.string.XgramQuickReplyEdit), hint = s(R.string.XgramQuickReplyMessageHint), initial = current, multiLine = true) { text ->
    val value = text.trim()
    if (value.isEmpty() || value == current) {
      return@PromptSpec
    }
    tdlib.send(TdApi.EditQuickReplyMessage(shortcutId, message.id, TdApi.InputMessageText(TdApi.FormattedText(value, emptyArray()), null, false))) { _, error ->
      if (error != null) {
        Log.w(TAG, "EditQuickReplyMessage: ${error.code} ${error.message}")
        UI.post { UI.showToast(s(R.string.XgramQuickReplyFailed), Toast.LENGTH_LONG) }
      }
    }
  })
}

/** A message's options sheet: Edit (texts) and Delete. */
private fun messageOptions(host: XgramComposeHost, shortcutId: Int, message: TdApi.QuickReplyMessage, editable: Boolean, index: Int, count: Int) {
  val tdlib = host.tdlib
  val ids = if (editable) intArrayOf(R.id.btn_edit, R.id.btn_delete) else intArrayOf(R.id.btn_delete)
  val titles = if (editable) arrayOf(s(R.string.XgramQuickReplyEdit), s(R.string.Delete)) else arrayOf(s(R.string.Delete))
  val colors = if (editable) intArrayOf(ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED) else intArrayOf(ViewController.OptionColor.RED)
  val icons = if (editable) intArrayOf(R.drawable.baseline_edit_24, R.drawable.baseline_delete_24) else intArrayOf(R.drawable.baseline_delete_24)
  host.controller.showOptions(s(R.string.XgramQuickReplyMessageNumber, index + 1, count), ids, titles, colors, icons) { _, id ->
    when (id) {
      R.id.btn_edit -> editMessage(host, shortcutId, message)
      R.id.btn_delete -> tdlib.send(TdApi.DeleteQuickReplyShortcutMessages(shortcutId, longArrayOf(message.id))) { _, error ->
        if (error != null) {
          Log.w(TAG, "DeleteQuickReplyShortcutMessages: ${error.code} ${error.message}")
          UI.post { UI.showToast(s(R.string.XgramQuickReplyFailed), Toast.LENGTH_LONG) }
        }
      }
    }
    true
  }
}
