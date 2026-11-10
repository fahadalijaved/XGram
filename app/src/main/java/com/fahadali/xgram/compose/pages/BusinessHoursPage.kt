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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.XgramR5ChatPickers
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.tool.UI
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

private const val TAG = "XgramBusinessHours"
private const val DAY = 24 * 60
private const val WEEK = 7 * DAY

/** One day's opening: closed, open all day, or from [open] to [close] (minutes in the day; [close] may pass midnight). */
private data class DayHours(val closed: Boolean, val open: Int = 0, val close: Int = DAY) {
  val allDay: Boolean get() = !closed && open == 0 && close >= DAY
}

/** Telegram's week of intervals (minutes from Monday 00:00) as seven days, Monday first; a day's first interval wins. */
private fun toDays(hours: TdApi.BusinessOpeningHours?): List<DayHours> {
  val days = MutableList(7) { DayHours(closed = true) }
  hours?.openingHours?.sortedBy { it.startMinute }?.forEach { interval ->
    val day = (interval.startMinute / DAY).coerceIn(0, 6)
    if (days[day].closed) {
      val start = interval.startMinute - day * DAY
      days[day] = DayHours(closed = false, open = start, close = interval.endMinute - day * DAY)
    } else {
      // A second interval the same day (set in another app): kept as one from the first opening to the last closing
      days[day] = days[day].copy(close = maxOf(days[day].close, interval.endMinute - day * DAY))
    }
  }
  return days
}

/** Seven days back to Telegram's intervals: touching ones joined (a day open until midnight and the next from it). */
private fun toIntervals(days: List<DayHours>): Array<TdApi.BusinessOpeningHoursInterval> {
  val raw = days.mapIndexedNotNull { index, day ->
    if (day.closed) null else (index * DAY + day.open) to (index * DAY + day.close).coerceAtMost(index * DAY + 2 * DAY - 1)
  }.sortedBy { it.first }
  val merged = ArrayList<Pair<Int, Int>>()
  raw.forEach { (start, end) ->
    val last = merged.lastOrNull()
    if (last != null && start <= last.second) {
      merged[merged.size - 1] = last.first to maxOf(last.second, end)
    } else {
      merged.add(start to end)
    }
  }
  return merged.map { (start, end) -> TdApi.BusinessOpeningHoursInterval(start, end.coerceAtMost(8 * DAY)) }.toTypedArray()
}

private fun time(minutes: Int): String {
  val m = ((minutes % DAY) + DAY) % DAY
  val calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, m / 60)
    set(Calendar.MINUTE, m % 60)
  }
  return android.text.format.DateFormat.getTimeFormat(UI.getContext()).format(calendar.time)
}

private fun dayText(day: DayHours): String = when {
  day.closed -> s(R.string.XgramBusinessClosed)
  day.allDay -> s(R.string.XgramBusinessOpenAllDay)
  else -> s(R.string.XgramBusinessFromTo, time(day.open), time(day.close))
}

/** Monday first, in the app's language. */
private fun dayNames(): List<String> {
  val names = DateFormatSymbols.getInstance(Locale.getDefault()).weekdays // Sunday is 1
  return listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    .map { names[it].replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
}

private fun offsetText(seconds: Int): String {
  val sign = if (seconds < 0) "−" else "+"
  val minutes = Math.abs(seconds) / 60
  return if (minutes % 60 == 0) "UTC$sign${minutes / 60}" else "UTC$sign${minutes / 60}:${"%02d".format(minutes % 60)}"
}

/**
 * Opening hours (Telegram Business): a time zone and each day's hours (closed, all day, or from and to), shown on your
 * profile in Telegram's apps; the away message can follow them.
 */
@Composable
fun BusinessHoursPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var loaded by remember { mutableStateOf(false) }
  var saved by remember { mutableStateOf<TdApi.BusinessOpeningHours?>(null) }
  var zones by remember { mutableStateOf<Array<TdApi.TimeZone>?>(null) }
  var edited by remember { mutableStateOf(false) }
  var on by remember { mutableStateOf(false) }
  var zoneId by remember { mutableStateOf(java.util.TimeZone.getDefault().id) }
  var days by remember { mutableStateOf(List(7) { index -> if (index < 5) DayHours(false, 9 * 60, 18 * 60) else DayHours(closed = true) }) }
  var busy by remember { mutableStateOf(false) }

  DisposableEffect(tdlib) {
    var active = true
    tdlib.send(TdApi.GetUserFullInfo(tdlib.myUserId())) { result, _ ->
      UI.post {
        if (active) {
          saved = result?.businessInfo?.openingHours
          loaded = true
        }
      }
    }
    tdlib.send(TdApi.GetTimeZones()) { result, _ -> UI.post { if (active && result != null) zones = result.timeZones } }
    onDispose { active = false }
  }
  LaunchedEffect(loaded, saved) {
    val current = saved
    if (loaded && !edited && current != null) {
      on = true
      zoneId = current.timeZoneId
      days = toDays(current)
    }
  }

  SettingsPage(title = str(R.string.XgramBusinessHours)) {
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    group {
      mainSwitch(1, s(R.string.XgramBusinessHoursSwitch), checked = on) { value ->
        if (enabled) {
          edited = true
          on = value
        }
      }
    }
    description(R.string.XgramBusinessHoursInfo)
    if (on) {
      group {
        val zone = zones?.firstOrNull { it.id == zoneId }
        value(2, s(R.string.XgramBusinessTimeZone), zone?.let { "${it.name} (${offsetText(it.utcTimeOffset)})" } ?: zoneId, icon = R.drawable.baseline_access_time_24, enabled = enabled && zones != null) {
          val list = zones ?: return@value
          host.choose(ChoiceSpec(
            title = s(R.string.XgramBusinessTimeZone),
            options = list.sortedBy { it.utcTimeOffset }.map { "${it.name} (${offsetText(it.utcTimeOffset)})" to it.id },
            selected = zoneId
          ) { id ->
            edited = true
            zoneId = id
          })
        }
      }
      header(R.string.XgramBusinessDaysHeader)
      group("days") {
        val names = dayNames()
        days.forEachIndexed { index, day ->
          value(10 + index, names[index], dayText(day), enabled = enabled) {
            editDay(host, names[index], day) { changed ->
              edited = true
              days = days.toMutableList().also { it[index] = changed }
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
        val function = if (on) {
          val intervals = toIntervals(days)
          if (intervals.isEmpty()) {
            UI.showToast(R.string.XgramBusinessHoursAllClosed, Toast.LENGTH_LONG)
            return@SaveButton
          }
          TdApi.SetBusinessOpeningHours(TdApi.BusinessOpeningHours(zoneId, intervals))
        } else {
          TdApi.SetBusinessOpeningHours(null)
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
}

/** A day's options: Closed, Open all day, or Set hours (the opening time, then the closing time). */
private fun editDay(host: XgramComposeHost, name: String, day: DayHours, onChange: (DayHours) -> Unit) {
  host.controller.showOptions(
    name,
    intArrayOf(R.id.btn_xgramBusinessClosed, R.id.btn_xgramBusinessAllDay, R.id.btn_xgramBusinessSetHours),
    arrayOf(s(R.string.XgramBusinessClosed), s(R.string.XgramBusinessOpenAllDay), s(R.string.XgramBusinessSetHours)),
    intArrayOf(ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL),
    intArrayOf(R.drawable.baseline_block_24, R.drawable.baseline_access_time_24, R.drawable.baseline_schedule_24)
  ) { _, id ->
    when (id) {
      R.id.btn_xgramBusinessClosed -> onChange(DayHours(closed = true))
      R.id.btn_xgramBusinessAllDay -> onChange(DayHours(closed = false, open = 0, close = DAY))
      R.id.btn_xgramBusinessSetHours -> {
        val from = if (day.closed || day.allDay) 9 * 60 else day.open
        val to = if (day.closed || day.allDay) 18 * 60 else day.close % DAY
        pickTime(host, s(R.string.XgramBusinessOpensAt, name), from) { open ->
          pickTime(host, s(R.string.XgramBusinessClosesAt, name), to) { close ->
            // Closing at or before the opening time means after midnight
            onChange(DayHours(closed = false, open = open, close = if (close <= open) close + DAY else close))
          }
        }
      }
    }
    true
  }
}

/** A time of day (minutes) with the M3 time picker, or the platform's when Expressive design is off. */
private fun pickTime(host: XgramComposeHost, title: String, minutes: Int, onPick: (Int) -> Unit) {
  val is24 = android.text.format.DateFormat.is24HourFormat(host.activity)
  val m = minutes % DAY
  if (!XgramR5ChatPickers.showTimePicker(host.activity, title, m / 60, m % 60, is24) { hour, minute -> onPick(hour * 60 + minute) }) {
    android.app.TimePickerDialog(host.activity, { _, hour, minute -> onPick(hour * 60 + minute) }, m / 60, m % 60, is24).apply { setTitle(title) }.show()
  }
}

/**
 * Location (Telegram Business): an address shown on your profile in Telegram's apps (no map pin from XGram yet).
 */
@Composable
fun BusinessLocationPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  var loaded by remember { mutableStateOf(false) }
  var location by remember { mutableStateOf<TdApi.BusinessLocation?>(null) }
  var busy by remember { mutableStateOf(false) }
  DisposableEffect(tdlib, host.version) {
    var active = true
    tdlib.send(TdApi.GetUserFullInfo(tdlib.myUserId())) { result, _ ->
      UI.post {
        if (active) {
          location = result?.businessInfo?.location
          loaded = true
        }
      }
    }
    onDispose { active = false }
  }

  SettingsPage(title = str(R.string.XgramBusinessLocation)) {
    val premium = tdlib.hasPremium()
    val enabled = premium && loaded && !busy
    val current = location
    group {
      value(1, s(R.string.XgramBusinessAddress), if (!loaded) "…" else current?.address?.takeIf { it.isNotBlank() } ?: s(R.string.XgramBusinessNotSet), icon = R.drawable.baseline_location_on_24, enabled = enabled) {
        host.prompt(PromptSpec(title = s(R.string.XgramBusinessAddress), hint = s(R.string.XgramBusinessAddressHint), initial = current?.address.orEmpty(), multiLine = true) { text ->
          val address = text.trim().take(96)
          if (address.isEmpty() || address == current?.address) {
            return@PromptSpec
          }
          busy = true
          businessSave(tdlib, TdApi.SetBusinessLocation(TdApi.BusinessLocation(current?.location, address))) { ok ->
            busy = false
            if (ok) {
              location = TdApi.BusinessLocation(current?.location, address)
            }
          }
        })
      }
      if (current != null) {
        action(2, s(R.string.XgramBusinessLocationRemove), icon = R.drawable.baseline_delete_24, destructive = true, enabled = enabled) {
          host.confirm(ConfirmSpec(title = s(R.string.XgramBusinessLocationRemove), text = s(R.string.XgramBusinessLocationRemoveInfo), confirm = s(R.string.XgramBusinessRemove), destructive = true) {
            busy = true
            businessSave(tdlib, TdApi.SetBusinessLocation(null)) { ok ->
              busy = false
              if (ok) {
                location = null
              }
            }
          })
        }
      }
    }
    description(R.string.XgramBusinessLocationInfo)
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }
  }
}
