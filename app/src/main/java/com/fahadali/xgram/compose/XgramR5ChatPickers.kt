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
@file:OptIn(ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fahadali.xgram.appearance.XgramAppMotion
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramIconPack
import com.fahadali.xgram.compose.theme.XgramM3Theme
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.theme.Theme
import java.util.Calendar
import java.util.TimeZone

/**
 * XGram M3E round 5: the chat's calendar and clock pickers as Compose material3 dialogs (Expressive design only; with
 * it off Telegram X keeps the platform DatePickerDialog and TimePickerDialog, ViewSupport.showDatePicker and
 * showTimePicker). Each dialog is a platform [Dialog] window holding a ComposeView with its own lifecycle
 * ([XgramComposeOwner]), so the M3 dialog window motion ([XgramAppMotion.dialogWindow]) and the 32% scrim apply:
 * a surfaceContainerHigh container with 28dp corners (the card step with Pure black, [XgramM3Theme]), and
 * sentence-case text buttons at the end.
 */
object XgramR5ChatPickers {
  /** A picked day: [month] is 0-based (Calendar.MONTH), as Telegram X's date picker callbacks. */
  fun interface DateCallback {
    fun onDatePicked(year: Int, month: Int, dayOfMonth: Int)
  }

  fun interface TimeCallback {
    fun onTimePicked(hourOfDay: Int, minute: Int)
  }

  /** The M3 date picker's width: seven 48dp days and 12dp on both sides (DatePickerModalTokens.ContainerWidth). */
  private val DATE_PICKER_WIDTH = 360.dp

  private const val SCRIM = .32f

  /** UTC midnight of the local day of [localMillis]: the M3 date picker counts days in UTC. */
  private fun utcDay(localMillis: Long): Long {
    val local = Calendar.getInstance()
    local.timeInMillis = localMillis
    return utcDay(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
  }

  private fun utcDay(year: Int, month: Int, dayOfMonth: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(year, month, dayOfMonth, 0, 0, 0)
    return utc.timeInMillis
  }

  private fun yearOf(localMillis: Long): Int {
    val local = Calendar.getInstance()
    local.timeInMillis = localMillis
    return local.get(Calendar.YEAR)
  }

  /**
   * Jump to date: a Compose material3 date picker limited to [minMillis]..[maxMillis] (local time), opening on
   * [initialMillis], with "Beginning" (when [onBeginning] is set), "Cancel" and "Go". Returns false when it could not
   * be shown (the caller then shows Telegram X's). Main thread.
   */
  @JvmStatic
  fun showDatePicker(
    context: Context,
    initialMillis: Long,
    minMillis: Long,
    maxMillis: Long,
    onPick: DateCallback,
    onBeginning: Runnable?
  ): Boolean {
    if (!XgramExpressive.isEnabled()) {
      return false
    }
    val minDay = utcDay(Math.min(minMillis, maxMillis))
    val maxDay = utcDay(maxMillis)
    val initialDay = utcDay(Math.max(minMillis, Math.min(maxMillis, initialMillis))).coerceIn(minDay, maxDay)
    val years = yearOf(Math.min(minMillis, maxMillis))..yearOf(maxMillis)
    return show(context) { dismiss ->
      DatePickerContent(initialDay, minDay, maxDay, years, onBeginning != null, dismiss,
        onGo = { utcMillis ->
          val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
          utc.timeInMillis = utcMillis
          onPick.onDatePicked(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        },
        onBeginning = { onBeginning?.run() })
    }
  }

  /**
   * A time: the M3 time picker dial, with a toggle to the time input fields, a labelMedium [title], "Cancel" and
   * "OK". Returns false when it could not be shown. Main thread.
   */
  @JvmStatic
  fun showTimePicker(context: Context, title: String?, hourOfDay: Int, minute: Int, is24Hour: Boolean, onPick: TimeCallback): Boolean {
    if (!XgramExpressive.isEnabled()) {
      return false
    }
    return show(context) { dismiss ->
      TimePickerContent(title, hourOfDay, minute, is24Hour, dismiss) { h, m -> onPick.onTimePicked(h, m) }
    }
  }

  /** A dialog window with [content] centred over the 32% scrim; a tap on the scrim or back dismisses it. */
  private fun show(context: Context, content: @Composable (dismiss: () -> Unit) -> Unit): Boolean {
    return try {
      val owner = XgramComposeOwner()
      val dialog = Dialog(context)
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
      val dismiss: () -> Unit = {
        if (dialog.isShowing) {
          dialog.dismiss()
        }
      }
      val dark = Theme.isDark()
      val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
          XgramM3Theme(dark = dark, themeVersion = 0) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
              contentAlignment = Alignment.Center
            ) {
              content(dismiss)
            }
          }
        }
      }
      dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
      dialog.setCanceledOnTouchOutside(true)
      dialog.window?.let { window ->
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(SCRIM)
      }
      XgramAppMotion.dialogWindow(dialog)
      dialog.setOnDismissListener { owner.destroy() }
      owner.moveTo(Lifecycle.State.RESUMED)
      dialog.show()
      true
    } catch (t: Throwable) {
      android.util.Log.e("XGram", "Compose picker failed", t)
      false
    }
  }

  /** The dialog's buttons: an optional neutral one at the start, then the others at the end, 8dp apart. */
  @Composable
  private fun DialogButtons(neutral: (@Composable () -> Unit)?, buttons: @Composable () -> Unit) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (neutral != null) {
        neutral()
      }
      Spacer(Modifier.weight(1f))
      buttons()
    }
  }

  @Composable
  private fun DatePickerContent(
    initialDay: Long,
    minDay: Long,
    maxDay: Long,
    years: IntRange,
    hasBeginning: Boolean,
    dismiss: () -> Unit,
    onGo: (Long) -> Unit,
    onBeginning: () -> Unit
  ) {
    val selectable = remember(minDay, maxDay, years) {
      object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minDay..maxDay
        override fun isSelectableYear(year: Int): Boolean = year in years
      }
    }
    val state = rememberDatePickerState(
      initialSelectedDateMillis = initialDay,
      initialDisplayedMonthMillis = initialDay,
      yearRange = years,
      initialDisplayMode = DisplayMode.Picker,
      selectableDates = selectable
    )
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    // The picker is 360dp wide: on a narrower window (the cover screen with a large display size) it scales down
    // instead of cutting the week's last day.
    BoxWithConstraints(contentAlignment = Alignment.Center) {
      val scale = if (maxWidth < DATE_PICKER_WIDTH) maxWidth / DATE_PICKER_WIDTH else 1f
      Surface(
        modifier = Modifier
          .requiredWidth(DATE_PICKER_WIDTH)
          .wrapContentHeight()
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          },
        shape = MaterialTheme.shapes.extraLarge,
        color = container
      ) {
        Column {
          DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(containerColor = container),
            showModeToggle = true
          )
          DialogButtons(
            neutral = if (hasBeginning) {
              {
                TextButton(onClick = {
                  dismiss()
                  onBeginning()
                }) {
                  Text(Lang.getString(R.string.Beginning))
                }
              }
            } else null
          ) {
            TextButton(onClick = dismiss) {
              Text(Lang.getString(R.string.Cancel))
            }
            TextButton(
              onClick = {
                val picked = state.selectedDateMillis
                dismiss()
                if (picked != null) {
                  onGo(picked)
                }
              },
              enabled = state.selectedDateMillis != null
            ) {
              Text(Lang.getString(R.string.XgramR5ChatGo))
            }
          }
        }
      }
    }
  }

  @Composable
  private fun TimePickerContent(
    title: String?,
    hourOfDay: Int,
    minute: Int,
    is24Hour: Boolean,
    dismiss: () -> Unit,
    onPick: (Int, Int) -> Unit
  ) {
    val state = rememberTimePickerState(initialHour = hourOfDay, initialMinute = minute, is24Hour = is24Hour)
    var dial by rememberSaveable { mutableStateOf(true) }
    Surface(
      modifier = Modifier.wrapContentHeight(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
      Column(
        modifier = Modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = title ?: Lang.getString(R.string.XgramR5ChatSelectTime),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
        )
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
          if (dial) {
            TimePicker(state = state)
          } else {
            TimeInput(state = state)
          }
        }
        DialogButtons(
          neutral = {
            IconButton(onClick = { dial = !dial }) {
              Icon(
                painter = painterResource(XgramIconPack.map(if (dial) R.drawable.baseline_keyboard_24 else R.drawable.baseline_schedule_24)),
                contentDescription = Lang.getString(if (dial) R.string.XgramR5ChatTimeTypeIn else R.string.XgramR5ChatTimeDial)
              )
            }
          }
        ) {
          TextButton(onClick = dismiss) {
            Text(Lang.getString(R.string.Cancel))
          }
          TextButton(onClick = {
            val h = state.hour
            val m = state.minute
            dismiss()
            onPick(h, m)
          }) {
            Text(Lang.getString(R.string.OK))
          }
        }
      }
    }
  }
}
