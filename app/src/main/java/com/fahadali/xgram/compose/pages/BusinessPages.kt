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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.business.XgramQuickReplies
import com.fahadali.xgram.business.XgramQuickReplyCommands
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramPage
import com.fahadali.xgram.compose.XgramR5ChatPickers
import com.fahadali.xgram.compose.ui.PageScope
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.user.BubbleView
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.ContactsController
import org.thunderdog.challegram.component.dialogs.SearchManager
import org.thunderdog.challegram.util.UserPickerMultiDelegate
import java.util.concurrent.TimeUnit

private const val TAG = "XgramBusiness"

/** The quick reply Telegram's greeting message uses, as official Telegram names it. */
const val GREETING_SHORTCUT = "hello"

/** The quick reply Telegram's away message uses, as official Telegram names it. */
const val AWAY_SHORTCUT = "away"

/** The account's BusinessInfo, read again whenever the page gets focus; null until Telegram answers. */
@Composable
private fun rememberBusinessInfo(host: XgramComposeHost): Pair<Boolean, TdApi.BusinessInfo?> {
  val tdlib = host.tdlib
  var loaded by remember { mutableStateOf(false) }
  var info by remember { mutableStateOf<TdApi.BusinessInfo?>(null) }
  DisposableEffect(tdlib, host.version) {
    var active = true
    tdlib.send(TdApi.GetUserFullInfo(tdlib.myUserId())) { result, _ ->
      UI.post {
        if (active) {
          info = result?.businessInfo
          loaded = true
        }
      }
    }
    onDispose { active = false }
  }
  return loaded to info
}

/** Redraws while mounted whenever the quick replies change; returns the change count to read. */
@Composable
private fun rememberShortcuts(store: XgramQuickReplies): Int {
  var version by remember { mutableStateOf(0) }
  DisposableEffect(store) {
    val listener = XgramQuickReplies.Listener { version++ }
    store.addListener(listener)
    store.load()
    onDispose { store.removeListener(listener) }
  }
  return version
}

/**
 * XGram: Telegram Business (included with Premium), like official Telegram's page: each part XGram has, with its state.
 */
@Composable
fun BusinessPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val store = tdlib.xgramQuickReplies()
  val shortcuts = rememberShortcuts(store)
  val (loaded, info) = rememberBusinessInfo(host)

  SettingsPage(title = str(R.string.XgramPremiumBusiness)) {
    shortcuts // read: the quick reply count follows Telegram's updates
    val premium = tdlib.hasPremium()
    val needs = s(R.string.XgramPremiumNeeds)
    val dots = "…"
    group {
      val count = store.list().size
      link(1, s(R.string.XgramQuickReplies), if (!premium) needs else if (count == 0) s(R.string.XgramBusinessNone) else store.list().take(3).joinToString(", ") { "/" + it.name } + if (count > 3) ", …" else "", icon = R.drawable.baseline_reply_24, enabled = premium) {
        host.openPage(XgramPage.QUICK_REPLIES)
      }
      link(2, s(R.string.XgramBusinessGreeting), if (!premium) needs else if (!loaded) dots else onOff(info?.greetingMessageSettings != null), icon = R.drawable.baseline_emoticon_outline_24, enabled = premium) {
        host.openPage(XgramPage.BUSINESS_GREETING)
      }
      link(3, s(R.string.XgramBusinessAway), if (!premium) needs else if (!loaded) dots else onOff(info?.awayMessageSettings != null), icon = R.drawable.baseline_schedule_24, enabled = premium) {
        host.openPage(XgramPage.BUSINESS_AWAY)
      }
      link(4, s(R.string.XgramBusinessHours), if (!premium) needs else if (!loaded) dots else onOff(info?.openingHours != null), icon = R.drawable.baseline_access_time_24, enabled = premium) {
        host.openPage(XgramPage.BUSINESS_HOURS)
      }
      link(5, s(R.string.XgramBusinessLocation), if (!premium) needs else if (!loaded) dots else info?.location?.address?.takeIf { it.isNotBlank() } ?: s(R.string.XgramBusinessNotSet), icon = R.drawable.baseline_location_on_24, enabled = premium) {
        host.openPage(XgramPage.BUSINESS_LOCATION)
      }
    }
    description(R.string.XgramBusinessInfo)
  }
}

private fun onOff(on: Boolean): String = s(if (on) R.string.XgramBusinessOn else R.string.XgramBusinessOff)

/** A copy of [r] with [change] applied (TDLib's objects are mutable; the page keeps its own). */
private fun TdApi.BusinessRecipients.edit(change: TdApi.BusinessRecipients.() -> Unit): TdApi.BusinessRecipients =
  TdApi.BusinessRecipients(chatIds.clone(), excludedChatIds.clone(), selectExistingChats, selectNewChats, selectContacts, selectNonContacts, excludeSelected).apply(change)

private fun defaultRecipients(): TdApi.BusinessRecipients =
  TdApi.BusinessRecipients(LongArray(0), LongArray(0), false, false, false, false, true)

private fun sameRecipients(a: TdApi.BusinessRecipients, b: TdApi.BusinessRecipients): Boolean =
  a.chatIds.contentEquals(b.chatIds) && a.selectExistingChats == b.selectExistingChats && a.selectNewChats == b.selectNewChats &&
    a.selectContacts == b.selectContacts && a.selectNonContacts == b.selectNonContacts && a.excludeSelected == b.excludeSelected

/**
 * Who gets an automatic message: all private chats except the picked ones, or only the picked ones; picked are chat
 * kinds (existing, new, contacts, non-contacts) and single chats (the people picker).
 */
private fun PageScope.recipientRows(host: XgramComposeHost, r: TdApi.BusinessRecipients, enabled: Boolean, onChange: (TdApi.BusinessRecipients) -> Unit) {
  val tdlib = host.tdlib
  header(R.string.XgramBusinessRecipients)
  group("recipients") {
    radio(20, s(R.string.XgramBusinessAllChats), selected = r.excludeSelected, subtitle = s(R.string.XgramBusinessAllChatsInfo), enabled = enabled) {
      onChange(r.edit { excludeSelected = true })
    }
    radio(21, s(R.string.XgramBusinessOnlySelected), selected = !r.excludeSelected, subtitle = s(R.string.XgramBusinessOnlySelectedInfo), enabled = enabled) {
      onChange(r.edit { excludeSelected = false })
    }
  }
  header(if (r.excludeSelected) R.string.XgramBusinessExcluded else R.string.XgramBusinessIncluded)
  group("picked") {
    checkbox(22, s(R.string.XgramBusinessExistingChats), r.selectExistingChats, enabled = enabled) { on -> onChange(r.edit { selectExistingChats = on }) }
    checkbox(23, s(R.string.XgramBusinessNewChats), r.selectNewChats, enabled = enabled) { on -> onChange(r.edit { selectNewChats = on }) }
    checkbox(24, s(R.string.XgramBusinessContacts), r.selectContacts, enabled = enabled) { on -> onChange(r.edit { selectContacts = on }) }
    checkbox(25, s(R.string.XgramBusinessNonContacts), r.selectNonContacts, enabled = enabled) { on -> onChange(r.edit { selectNonContacts = on }) }
    action(26, s(if (r.excludeSelected) R.string.XgramBusinessExcludeChats else R.string.XgramBusinessIncludeChats), icon = R.drawable.baseline_person_add_24, enabled = enabled) {
      pickChats(host, r.chatIds, if (r.excludeSelected) R.string.XgramBusinessExcludeChats else R.string.XgramBusinessIncludeChats) { ids ->
        onChange(r.edit { chatIds = ids })
      }
    }
    r.chatIds.forEach { chatId ->
      action(
        0, tdlib.chatTitle(chatId), null, icon = R.drawable.baseline_person_24, enabled = enabled, key = "chat:$chatId",
        trailing = {
          IconButton(onClick = { onChange(r.edit { chatIds = chatIds.filter { it != chatId }.toLongArray() }) }, enabled = enabled) {
            Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = s(R.string.XgramBusinessRemoveChat))
          }
        }
      ) { }
    }
  }
}

/** The people picker (private chats with users, no bots, not yourself); [onPicked] gets the chat ids as you go. */
private fun pickChats(host: XgramComposeHost, current: LongArray, hint: Int, onPicked: (LongArray) -> Unit) {
  var picked = current
  val picker = ContactsController(host.activity, host.tdlib)
  picker.setArguments(ContactsController.Args(object : UserPickerMultiDelegate {
    override fun getAlreadySelectedChatIds(): LongArray = picked

    override fun onAlreadyPickedChatsChanged(entries: List<BubbleView.Entry>) {
      picked = entries.mapNotNull { (it.senderId as? TdApi.MessageSenderUser)?.userId }.toLongArray() // a private chat's id is its user's
      onPicked(picked)
    }

    override fun provideMultiUserPickerHint(): Int = hint
  }).useGlobalSearch(SearchManager.FLAG_NEED_TOP_CHATS or SearchManager.FLAG_NEED_GLOBAL_SEARCH or SearchManager.FLAG_NO_BOTS or SearchManager.FLAG_NO_CHANNELS or SearchManager.FLAG_NO_SELF))
  host.open(picker)
}

/** The row for an automatic message's quick reply: its first message, or "Add a message"; a tap edits it. */
private fun PageScope.shortcutRow(host: XgramComposeHost, name: String, title: Int, enabled: Boolean) {
  val shortcut = host.tdlib.xgramQuickReplies().findByName(name)
  group {
    link(10, s(title), if (shortcut == null) s(R.string.XgramBusinessAddMessage) else XgramQuickReplyCommands.describe(shortcut), icon = R.drawable.baseline_chat_bubble_24, enabled = enabled) {
      host.openPage(XgramPage.QUICK_REPLY, 0, name)
    }
  }
}

/** A full-width Save button at the end of the page; [busy] shows a spinner in it. */
private fun PageScope.saveButton(enabled: Boolean, busy: Boolean, onSave: () -> Unit) {
  item("save") { SaveButton(enabled, busy, onSave) }
}

/** The Business pages' Save button: full width, [busy] shows a spinner in it. */
@Composable
internal fun SaveButton(enabled: Boolean, busy: Boolean, onSave: () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
    Button(onClick = onSave, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
      if (busy) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
      } else {
        Text(str(R.string.Save))
      }
    }
  }
}

/** Sends [function]; [done] runs on the UI thread, with a plain-words toast on an error. */
internal fun businessSave(tdlib: Tdlib, function: TdApi.Function<TdApi.Ok>, done: (Boolean) -> Unit) {
  tdlib.send(function) { _, error ->
    UI.post {
      if (error != null) {
        Log.w(TAG, "${function.javaClass.simpleName}: ${error.code} ${error.message}")
        UI.showToast(s(if (error.message == "PREMIUM_ACCOUNT_REQUIRED") R.string.XgramPremiumNeeds else R.string.XgramBusinessSaveFailed), Toast.LENGTH_LONG)
      } else {
        UI.showToast(R.string.XgramBusinessSaved, Toast.LENGTH_SHORT)
      }
      done(error == null)
    }
  }
}

/**
 * Greeting message (Telegram Business): the "hello" quick reply, sent by Telegram when someone messages you for the
 * first time or after [inactivityDays] without a message, to the recipients picked here.
 */
@Composable
fun BusinessGreetingPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val store = tdlib.xgramQuickReplies()
  val shortcuts = rememberShortcuts(store)
  val (loaded, info) = rememberBusinessInfo(host)
  var edited by remember { mutableStateOf(false) }
  var on by remember { mutableStateOf(false) }
  var recipients by remember { mutableStateOf(defaultRecipients()) }
  var days by remember { mutableStateOf(7) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(loaded, info) {
    if (loaded && !edited) {
      // Telegram's settings, until the first change here
      val current = info?.greetingMessageSettings
      on = current != null
      recipients = current?.recipients ?: defaultRecipients()
      days = current?.inactivityDays?.takeIf { it in listOf(7, 14, 21, 28) } ?: 7
    }
  }

  SettingsPage(title = str(R.string.XgramBusinessGreeting)) {
    shortcuts // read: the message row follows the quick reply
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    group {
      mainSwitch(1, s(R.string.XgramBusinessGreetingSwitch), checked = on) { value ->
        if (enabled) {
          edited = true
          on = value
        }
      }
    }
    description(R.string.XgramBusinessGreetingInfo)
    if (on) {
      shortcutRow(host, GREETING_SHORTCUT, R.string.XgramBusinessGreetingMessage, enabled)
      recipientRows(host, recipients, enabled) { edited = true; recipients = it }
      header(R.string.XgramBusinessInactivity)
      group {
        buttons(30, s(R.string.XgramBusinessInactivityTitle), listOf(7, 14, 21, 28).map { Lang.plural(R.string.xDays, it.toLong()) to it }, selected = days, enabled = enabled) {
          edited = true
          days = it
        }
      }
      description(R.string.XgramBusinessInactivityInfo)
    }
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }
    saveButton(enabled && edited, busy) {
      val function = if (!on) {
        TdApi.SetBusinessGreetingMessageSettings(null)
      } else {
        val shortcut = store.findByName(GREETING_SHORTCUT)
        if (shortcut == null) {
          UI.showToast(R.string.XgramBusinessGreetingNoMessage, Toast.LENGTH_LONG)
          return@saveButton
        }
        TdApi.SetBusinessGreetingMessageSettings(TdApi.BusinessGreetingMessageSettings(shortcut.id, recipients, days))
      }
      busy = true
      businessSave(tdlib, function) { ok ->
        busy = false
        if (ok) {
          edited = false
          host.changed()
        }
      }
    }
  }
}

/**
 * Away message (Telegram Business): the "away" quick reply, sent by Telegram while you are away (always, outside your
 * opening hours or between two dates), optionally only while you are offline, to the recipients picked here.
 */
@Composable
fun BusinessAwayPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val store = tdlib.xgramQuickReplies()
  val shortcuts = rememberShortcuts(store)
  val (loaded, info) = rememberBusinessInfo(host)
  var edited by remember { mutableStateOf(false) }
  var on by remember { mutableStateOf(false) }
  var recipients by remember { mutableStateOf(defaultRecipients()) }
  var schedule by remember { mutableStateOf<TdApi.BusinessAwayMessageSchedule>(TdApi.BusinessAwayMessageScheduleAlways()) }
  var offlineOnly by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(loaded, info) {
    if (loaded && !edited) {
      // Telegram's settings, until the first change here
      val current = info?.awayMessageSettings
      on = current != null
      recipients = current?.recipients ?: defaultRecipients()
      schedule = current?.schedule ?: TdApi.BusinessAwayMessageScheduleAlways()
      offlineOnly = current?.offlineOnly ?: true
    }
  }

  SettingsPage(title = str(R.string.XgramBusinessAway)) {
    shortcuts // read: the message row follows the quick reply
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    val hasHours = info?.openingHours != null
    group {
      mainSwitch(1, s(R.string.XgramBusinessAwaySwitch), checked = on) { value ->
        if (enabled) {
          edited = true
          on = value
        }
      }
    }
    description(R.string.XgramBusinessAwayInfo)
    if (on) {
      shortcutRow(host, AWAY_SHORTCUT, R.string.XgramBusinessAwayMessage, enabled)
      header(R.string.XgramBusinessSchedule)
      group("schedule") {
        radio(40, s(R.string.XgramBusinessScheduleAlways), selected = schedule is TdApi.BusinessAwayMessageScheduleAlways, enabled = enabled) {
          edited = true
          schedule = TdApi.BusinessAwayMessageScheduleAlways()
        }
        radio(
          41, s(R.string.XgramBusinessScheduleOutside), selected = schedule is TdApi.BusinessAwayMessageScheduleOutsideOfOpeningHours,
          subtitle = if (hasHours) null else s(R.string.XgramBusinessScheduleOutsideNoHours), enabled = enabled && hasHours
        ) {
          edited = true
          schedule = TdApi.BusinessAwayMessageScheduleOutsideOfOpeningHours()
        }
        radio(42, s(R.string.XgramBusinessScheduleCustom), selected = schedule is TdApi.BusinessAwayMessageScheduleCustom, enabled = enabled) {
          edited = true
          if (schedule !is TdApi.BusinessAwayMessageScheduleCustom) {
            val now = (tdlib.currentTimeMillis() / 1000L).toInt()
            schedule = TdApi.BusinessAwayMessageScheduleCustom(now, now + TimeUnit.DAYS.toSeconds(1).toInt())
          }
        }
      }
      val custom = schedule as? TdApi.BusinessAwayMessageScheduleCustom
      if (custom != null) {
        group("custom") {
          value(43, s(R.string.XgramBusinessScheduleStart), Lang.dateYearShortTime(custom.startDate.toLong(), TimeUnit.SECONDS), enabled = enabled) {
            pickMoment(host, s(R.string.XgramBusinessScheduleStart), custom.startDate) { date ->
              edited = true
              schedule = TdApi.BusinessAwayMessageScheduleCustom(date, maxOf(custom.endDate, date + 60))
            }
          }
          value(44, s(R.string.XgramBusinessScheduleEnd), Lang.dateYearShortTime(custom.endDate.toLong(), TimeUnit.SECONDS), enabled = enabled) {
            pickMoment(host, s(R.string.XgramBusinessScheduleEnd), custom.endDate) { date ->
              edited = true
              schedule = TdApi.BusinessAwayMessageScheduleCustom(minOf(custom.startDate, date - 60), date)
            }
          }
        }
      }
      group {
        switch(45, s(R.string.XgramBusinessOfflineOnly), s(R.string.XgramBusinessOfflineOnlyInfo), checked = offlineOnly, enabled = enabled) {
          edited = true
          offlineOnly = it
        }
      }
      recipientRows(host, recipients, enabled) { edited = true; recipients = it }
    }
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }
    saveButton(enabled && edited, busy) {
      val function = if (!on) {
        TdApi.SetBusinessAwayMessageSettings(null)
      } else {
        val shortcut = store.findByName(AWAY_SHORTCUT)
        if (shortcut == null) {
          UI.showToast(R.string.XgramBusinessAwayNoMessage, Toast.LENGTH_LONG)
          return@saveButton
        }
        TdApi.SetBusinessAwayMessageSettings(TdApi.BusinessAwayMessageSettings(shortcut.id, recipients, schedule, offlineOnly))
      }
      busy = true
      businessSave(tdlib, function) { ok ->
        busy = false
        if (ok) {
          edited = false
          host.changed()
        }
      }
    }
  }
}

/** A date and time (seconds) with the M3 pickers, up to a year ahead; [onPick] gets the picked moment in seconds. */
private fun pickMoment(host: XgramComposeHost, title: String, initial: Int, onPick: (Int) -> Unit) {
  val tdlib = host.tdlib
  val now = tdlib.currentTimeMillis()
  val shown = XgramR5ChatPickers.showDateTimePicker(
    host.activity, title, maxOf(now, initial * 1000L), 365, android.text.format.DateFormat.is24HourFormat(host.activity), false,
    { millis -> Lang.dateYearShortTime(millis, TimeUnit.MILLISECONDS) }
  ) { millis -> onPick((millis / 1000L).toInt()) }
  if (!shown) {
    // Expressive design off: the platform's date and then time dialogs
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = maxOf(now, initial * 1000L) }
    android.app.DatePickerDialog(host.activity, { _, year, month, day ->
      android.app.TimePickerDialog(host.activity, { _, hour, minute ->
        calendar.set(year, month, day, hour, minute, 0)
        onPick((calendar.timeInMillis / 1000L).toInt())
      }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(host.activity)).show()
    }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
  }
}
