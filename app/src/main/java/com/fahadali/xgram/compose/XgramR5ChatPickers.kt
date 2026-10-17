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
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fahadali.xgram.compose

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fahadali.xgram.appearance.XgramAmoled
import com.fahadali.xgram.appearance.XgramAppMotion
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramIconPack
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.appearance.XgramSnackbar
import com.fahadali.xgram.compose.theme.XgramM3Theme
import org.drinkless.tdlib.TdApi
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
 *
 * Round 6 (batch 1): the birthday date-input dialog ([showBirthdayPicker]) and the date then time dialog of the
 * schedule, restrict / ban until, invite link expiry and emoji status pickers ([showDateTimePicker]), which replace
 * Telegram X's wheels with Expressive design on.
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

  /** True inside a dialog drawn with the Pure black dark scheme: floating surfaces (menus) take the chip step. */
  private val LocalPureBlack = staticCompositionLocalOf { false }

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

  /**
   * A dialog window with [content] centred over the 32% scrim; a tap on the scrim or back dismisses it. [forceDark]
   * draws it with the dark scheme whatever the app theme (the media viewer's forced night theme). The window resizes
   * for the keyboard, so a dialog with text fields stays centred above it.
   */
  private fun show(context: Context, forceDark: Boolean = false, content: @Composable (dismiss: () -> Unit) -> Unit): Boolean {
    return try {
      val owner = XgramComposeOwner()
      val dialog = Dialog(context)
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
      val dismiss: () -> Unit = {
        if (dialog.isShowing) {
          dialog.dismiss()
        }
      }
      val dark = forceDark || Theme.isDark()
      val pureBlack = dark && XgramAmoled.isEnabled()
      val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
          XgramM3Theme(dark = dark, themeVersion = 0) {
            CompositionLocalProvider(LocalPureBlack provides pureBlack) {
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
      }
      dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
      dialog.setCanceledOnTouchOutside(true)
      dialog.window?.let { window ->
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(SCRIM)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
      Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        buttons()
      }
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

  // Round 6: motion of the dialogs' step changes (Reduce motion: no animation)

  @Composable
  private fun <T> effectsSpec(): FiniteAnimationSpec<T> =
    if (XgramMotion.reduce()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

  /** Swallows taps on the dialog's container, so only the scrim around it dismisses. */
  @Composable
  private fun Modifier.consumeTaps(): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})

  // Round 6: date, then time (schedule message, restrict / ban until, invite link expiry, emoji status until)

  /** A label for the confirm button of [showDateTimePicker] for the picked moment ("Send today at 10:00"). */
  fun interface MomentLabel {
    fun label(millis: Long): String
  }

  fun interface MomentCallback {
    fun onPicked(millis: Long)
  }

  /**
   * The M3 date picker (today to [maxDays] days ahead) and then, in the same dialog, the M3 time picker (dial, with the
   * toggle to the input fields), with [title] above both. The confirm button says [label] of the picked moment and
   * keeps it up to date; a moment that has already passed shows a snackbar and moves the clock to the earliest time
   * left. [nowMillis] is the server time (Tdlib.currentTimeMillis); [forceDark] uses the dark scheme (media viewer).
   * Returns false when the dialog could not be shown: the caller then shows Telegram X's wheel. Main thread.
   */
  @JvmStatic
  fun showDateTimePicker(
    context: Context,
    title: String?,
    nowMillis: Long,
    maxDays: Int,
    is24Hour: Boolean,
    forceDark: Boolean,
    label: MomentLabel,
    onPick: MomentCallback
  ): Boolean {
    if (!XgramExpressive.isEnabled()) {
      return false
    }
    val offset = nowMillis - System.currentTimeMillis()
    val max = Calendar.getInstance()
    max.timeInMillis = nowMillis
    max.add(Calendar.DAY_OF_MONTH, Math.max(1, maxDays))
    val minDay = utcDay(nowMillis)
    val maxDay = utcDay(max.timeInMillis)
    val years = yearOf(nowMillis)..yearOf(max.timeInMillis)
    return show(context, forceDark) { dismiss ->
      DateTimeContent(title, minDay, maxDay, years, offset, is24Hour, label, dismiss) { millis -> onPick.onPicked(millis) }
    }
  }

  /** The local moment of the UTC day [utcDayMillis] (the date picker's selection) at [hour]:[minute]. */
  private fun momentOf(utcDayMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = utcDayMillis
    val local = Calendar.getInstance()
    local.clear()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    return local.timeInMillis
  }

  @Composable
  private fun DateTimeContent(
    title: String?,
    minDay: Long,
    maxDay: Long,
    years: IntRange,
    offset: Long,
    is24Hour: Boolean,
    label: MomentLabel,
    dismiss: () -> Unit,
    onPick: (Long) -> Unit
  ) {
    val selectable = remember(minDay, maxDay, years) {
      object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minDay..maxDay
        override fun isSelectableYear(year: Int): Boolean = year in years
      }
    }
    val dateState = rememberDatePickerState(
      initialSelectedDateMillis = minDay,
      initialDisplayedMonthMillis = minDay,
      yearRange = years,
      initialDisplayMode = DisplayMode.Picker,
      selectableDates = selectable
    )
    // The clock opens two minutes ahead, as Telegram X's wheels
    val start = remember {
      val c = Calendar.getInstance()
      c.timeInMillis = System.currentTimeMillis() + offset
      c.add(Calendar.MINUTE, 2)
      c
    }
    val timeState = rememberTimePickerState(
      initialHour = start.get(Calendar.HOUR_OF_DAY),
      initialMinute = start.get(Calendar.MINUTE),
      is24Hour = is24Hour
    )
    var timeStep by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(true) }
    var past by remember { mutableStateOf(false) }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val effects = effectsSpec<Float>()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
      val scale = if (maxWidth < DATE_PICKER_WIDTH) maxWidth / DATE_PICKER_WIDTH else 1f
      Surface(
        modifier = Modifier
          .requiredWidth(DATE_PICKER_WIDTH)
          .wrapContentHeight()
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
          .consumeTaps(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container
      ) {
        AnimatedContent(
          targetState = timeStep,
          transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(clip = false) },
          label = "xgramDateTimeStep"
        ) { onTime ->
          if (!onTime) {
            Column {
              DatePicker(
                state = dateState,
                colors = DatePickerDefaults.colors(containerColor = container),
                title = if (title != null) {
                  {
                    Text(
                      text = title,
                      modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                } else null,
                showModeToggle = true
              )
              DialogButtons(neutral = null) {
                TextButton(onClick = dismiss, shapes = ButtonDefaults.shapes()) {
                  Text(Lang.getString(R.string.Cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                  onClick = {
                    past = false
                    timeStep = true
                  },
                  shapes = ButtonDefaults.shapes(),
                  enabled = dateState.selectedDateMillis != null,
                  contentPadding = ButtonDefaults.SmallContentPadding
                ) {
                  Text(Lang.getString(R.string.XgramR6B1Next))
                }
              }
            }
          } else {
            val day = dateState.selectedDateMillis ?: minDay
            val moment = momentOf(day, timeState.hour, timeState.minute)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = title ?: Lang.getString(R.string.XgramR6B1SelectTime),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)
              )
              Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                if (dial) {
                  TimePicker(state = timeState)
                } else {
                  TimeInput(state = timeState)
                }
              }
              if (past) {
                Text(
                  text = Lang.getString(R.string.XgramR6B1PastTime),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                )
              }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(onClick = { dial = !dial }) {
                  Icon(
                    painter = painterResource(XgramIconPack.map(if (dial) R.drawable.baseline_keyboard_24 else R.drawable.baseline_schedule_24)),
                    contentDescription = Lang.getString(if (dial) R.string.XgramR5ChatTimeTypeIn else R.string.XgramR5ChatTimeDial)
                  )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { timeStep = false }, shapes = ButtonDefaults.shapes()) {
                  Text(Lang.getString(R.string.XgramR6B1Back))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                  onClick = {
                    val now = System.currentTimeMillis() + offset
                    val picked = momentOf(dateState.selectedDateMillis ?: minDay, timeState.hour, timeState.minute)
                    if (picked > now) {
                      dismiss()
                      onPick(picked)
                    } else {
                      // Already passed: the clock moves to the earliest time left and the dialog stays
                      past = true
                      XgramSnackbar.show(Lang.getString(R.string.XgramR6B1PastTime), android.widget.Toast.LENGTH_SHORT)
                      val earliest = Calendar.getInstance()
                      earliest.timeInMillis = now
                      earliest.add(Calendar.MINUTE, 2)
                      if (utcDay(earliest.timeInMillis) == (dateState.selectedDateMillis ?: minDay)) {
                        timeState.hour = earliest.get(Calendar.HOUR_OF_DAY)
                        timeState.minute = earliest.get(Calendar.MINUTE)
                      } else {
                        // Two minutes from now is already tomorrow: back to the calendar
                        timeStep = false
                      }
                    }
                  },
                  shapes = ButtonDefaults.shapes(),
                  modifier = Modifier.weight(1f, fill = false),
                  contentPadding = ButtonDefaults.SmallContentPadding
                ) {
                  Text(label.label(moment), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
          }
        }
      }
    }
  }

  // Round 6: birthday

  /** The result of a birthday request: null closes the dialog, an error message keeps it open and shows it. Main thread. */
  fun interface BirthdayResult {
    fun done(error: String?)
  }

  fun interface BirthdaySave {
    /** [month] is 1-based (TdApi.Birthdate), [year] 0 when it was left empty. Call [result] when the request ends. */
    fun onSave(day: Int, month: Int, year: Int, result: BirthdayResult)
  }

  fun interface BirthdayRemove {
    fun onRemove(result: BirthdayResult)
  }

  /** The earliest birth year Telegram X's picker offers. */
  private const val MIN_BIRTH_YEAR = 1875

  /**
   * The birthday as an M3 date-input dialog: a filled "Month" dropdown, then the filled numeric "Day" and optional
   * "Year" fields, with errors for a day the month doesn't have, 29 February of a common year, a year out of range or a
   * date in the future. Save (or the keyboard's Done) asks to confirm, as Telegram X does (there is a time frame before
   * the date can change again), then runs [onSave]; "Remove birthday" ([onRemove], when [canRemove]) sits at the
   * start. [day] and [month] (1-based) may be 0 for empty fields, [year] 0 for none. Returns false when it could not
   * be shown (the caller then shows Telegram X's wheel). Main thread.
   */
  @JvmStatic
  fun showBirthdayPicker(
    context: Context,
    day: Int,
    month: Int,
    year: Int,
    canRemove: Boolean,
    onSave: BirthdaySave,
    onRemove: BirthdayRemove?
  ): Boolean {
    if (!XgramExpressive.isEnabled()) {
      return false
    }
    return show(context) { dismiss ->
      BirthdayContent(day, month, year, canRemove && onRemove != null, dismiss, onSave, onRemove)
    }
  }

  /** Stand-alone month names ("October", not the genitive form some languages use in dates), capitalized. */
  private fun monthNames(): Array<String> {
    val locale = Lang.locale()
    var names: Array<String>? = null
    try {
      names = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        android.icu.text.DateFormatSymbols.getInstance(locale)
          .getMonths(android.icu.text.DateFormatSymbols.STANDALONE, android.icu.text.DateFormatSymbols.WIDE)
      } else {
        java.text.DateFormatSymbols.getInstance(locale).months
      }
    } catch (ignored: Throwable) { }
    val raw: Array<String> = if (names != null && names.size >= 12) names else Lang.getMonths(locale)
    return Array(12) { i ->
      val name = if (i < raw.size) raw[i] else (i + 1).toString()
      if (name.isNotEmpty() && name[0].isLowerCase()) name.substring(0, 1).uppercase(locale) + name.substring(1) else name
    }
  }

  /** Days in [month] (1-based) of [year]; with no year, of a leap year, so 29 February stays possible. */
  private fun daysIn(month: Int, year: Int): Int {
    val c = Calendar.getInstance()
    c.clear()
    c.set(if (year > 0) year else 2024, month - 1, 1)
    return c.getActualMaximum(Calendar.DAY_OF_MONTH)
  }

  @Composable
  private fun BirthdayContent(
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    canRemove: Boolean,
    dismiss: () -> Unit,
    onSave: BirthdaySave,
    onRemove: BirthdayRemove?
  ) {
    val months = remember { monthNames() }
    val today = remember { Calendar.getInstance() }
    val thisYear = today.get(Calendar.YEAR)
    val thisMonth = today.get(Calendar.MONTH) + 1
    val thisDay = today.get(Calendar.DAY_OF_MONTH)

    var month by remember { mutableStateOf(if (initialMonth in 1..12) initialMonth else 0) }
    var dayText by remember { mutableStateOf(if (initialDay in 1..31) initialDay.toString() else "") }
    var yearText by remember { mutableStateOf(if (initialYear in MIN_BIRTH_YEAR..thisYear) initialYear.toString() else "") }
    var monthExpanded by remember { mutableStateOf(false) }
    var dayFocused by remember { mutableStateOf(false) }
    var yearFocused by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val day = dayText.toIntOrNull() ?: 0
    val year = yearText.toIntOrNull() ?: 0
    val yearInRange = yearText.length == 4 && year in MIN_BIRTH_YEAR..thisYear
    val yearValid = yearText.isEmpty() || yearInRange
    val maxDay = if (month == 0) 31 else daysIn(month, if (yearInRange) year else 0)
    val dayValid = day in 1..maxDay
    val notLeap = month == 2 && day == 29 && yearInRange && maxDay < 29
    val future = yearInRange && month != 0 && dayValid &&
      (year > thisYear || (year == thisYear && (month > thisMonth || (month == thisMonth && day > thisDay))))
    val canSave = month != 0 && dayValid && yearValid && !future && !busy

    val dayError = dayText.isNotEmpty() && !dayValid && (notLeap || dayText.length >= 2 || !dayFocused)
    val yearError = future || (yearText.isNotEmpty() && !yearValid && (yearText.length >= 4 || !yearFocused))
    val dayMessage = when {
      !dayError -> ""
      notLeap -> Lang.getString(R.string.XgramR6B1BirthdayNotLeap, year)
      else -> Lang.getString(R.string.XgramR6B1BirthdayDayRange, maxDay)
    }
    val yearMessage = when {
      future -> Lang.getString(R.string.XgramR6B1BirthdayFuture)
      yearError -> Lang.getString(R.string.XgramR6B1BirthdayYearRange, MIN_BIRTH_YEAR, thisYear)
      else -> Lang.getString(R.string.XgramR6B1Optional)
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val toConfirm: () -> Unit = {
      if (canSave) {
        focusManager.clearFocus()
        keyboard?.hide()
        error = null
        confirming = true
      }
    }
    val effects = effectsSpec<Float>()
    val menuColor = if (LocalPureBlack.current) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer

    Surface(
      modifier = Modifier
        .padding(24.dp)
        .widthIn(min = 280.dp, max = 400.dp)
        .fillMaxWidth()
        .wrapContentHeight()
        .consumeTaps(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
      Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
          text = Lang.getString(R.string.BirthdayPopupTitle),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
          targetState = confirming,
          transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(clip = false) },
          label = "xgramBirthdayStep"
        ) { onConfirm ->
          if (!onConfirm) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
              ExposedDropdownMenuBox(
                expanded = monthExpanded,
                onExpandedChange = { if (!busy) monthExpanded = it }
              ) {
                TextField(
                  value = if (month in 1..12) months[month - 1] else "",
                  onValueChange = { },
                  readOnly = true,
                  singleLine = true,
                  enabled = !busy,
                  label = { Text(Lang.getString(R.string.XgramR6B1BirthdayMonth)) },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                  colors = ExposedDropdownMenuDefaults.textFieldColors(),
                  modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                )
                ExposedDropdownMenu(
                  expanded = monthExpanded,
                  onDismissRequest = { monthExpanded = false },
                  containerColor = menuColor
                ) {
                  months.forEachIndexed { index, name ->
                    DropdownMenuItem(
                      text = { Text(name) },
                      onClick = {
                        month = index + 1
                        monthExpanded = false
                      },
                      contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                  }
                }
              }
              Spacer(Modifier.height(8.dp))
              Row {
                TextField(
                  value = dayText,
                  onValueChange = { value -> dayText = value.filter { it in '0'..'9' }.take(2) },
                  singleLine = true,
                  enabled = !busy,
                  label = { Text(Lang.getString(R.string.XgramR6B1BirthdayDay)) },
                  isError = dayError,
                  supportingText = { Text(dayMessage, maxLines = 2) },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                  modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { dayFocused = it.isFocused }
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                  value = yearText,
                  onValueChange = { value -> yearText = value.filter { it in '0'..'9' }.take(4) },
                  singleLine = true,
                  enabled = !busy,
                  label = { Text(Lang.getString(R.string.XgramR6B1BirthdayYear)) },
                  isError = yearError,
                  supportingText = { Text(yearMessage, maxLines = 2) },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                  keyboardActions = KeyboardActions(onDone = { toConfirm() }),
                  modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { yearFocused = it.isFocused }
                )
              }
            }
          } else {
            val warning = remember(day, month, year) { birthdayWarning(day, month, if (yearInRange) year else 0) }
            Text(
              text = warning,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
            )
          }
        }
        val message = error
        if (message != null) {
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 24.dp, end = 24.dp, top = 8.dp)
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (canRemove && !confirming && onRemove != null) {
            TextButton(
              onClick = {
                busy = true
                error = null
                onRemove.onRemove { failure ->
                  busy = false
                  if (failure == null) {
                    dismiss()
                  } else {
                    error = failure
                  }
                }
              },
              enabled = !busy,
              shapes = ButtonDefaults.shapes(),
              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.weight(1f, fill = false)
            ) {
              Text(Lang.getString(R.string.XgramM3eR4preSbRemoveBirthday), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          Spacer(Modifier.weight(1f))
          TextButton(
            onClick = {
              if (confirming) {
                confirming = false
                error = null
              } else {
                dismiss()
              }
            },
            enabled = !busy,
            shapes = ButtonDefaults.shapes()
          ) {
            Text(Lang.getString(if (confirming) R.string.XgramR6B1Back else R.string.Cancel))
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              if (!confirming) {
                toConfirm()
              } else {
                busy = true
                error = null
                onSave.onSave(day, month, if (yearInRange) year else 0) { failure ->
                  busy = false
                  if (failure == null) {
                    dismiss()
                  } else {
                    error = failure
                  }
                }
              }
            },
            enabled = canSave,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.SmallContentPadding
          ) {
            Text(Lang.getString(if (confirming) R.string.SetBirthdateOk else R.string.Save))
          }
        }
      }
    }
  }

  /** Telegram X's "Set birthdate to %1$s? There's a time frame…" with the date in medium weight. */
  private fun birthdayWarning(day: Int, month: Int, year: Int): AnnotatedString {
    val date = Lang.getBirthdate(TdApi.Birthdate(day, month, year), false, true).toString()
    val text = Lang.getString(R.string.SetBirthdateWarning, date)
    val start = text.indexOf(date)
    return buildAnnotatedString {
      append(text)
      if (start >= 0) {
        addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, start + date.length)
      }
    }
  }
}
