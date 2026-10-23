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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import kotlinx.coroutines.launch
import org.thunderdog.challegram.R

/** The host's sheet or dialog, if any. Sheets and dialogs are windows of their own and handle back themselves. */
@Composable
fun XgramDialogSlot() {
  val host = LocalXgramHost.current
  when (val dialog = host.dialog) {
    is ChoiceSpec<*> -> ChoiceSheet(dialog) { host.dismissDialog() }
    is ConfirmSpec -> ConfirmDialog(dialog) { host.dismissDialog() }
    is PromptSpec -> PromptDialog(dialog) { host.dismissDialog() }
    else -> Unit
  }
}

/** A modal bottom sheet with one choice: a tap selects, the sheet slides away, then the choice is applied. */
@Composable
fun ChoiceSheet(spec: ChoiceSpec<*>, onDismiss: () -> Unit) {
  val host = LocalXgramHost.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val labels = spec.labels
  val selectedIndex = spec.selectedIndex
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
      // 16dp in, at the tiles' edge, as the View sheets' titles (OptionsLayout)
      Text(
        spec.title,
        modifier = Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge
      )
      val info = spec.info
      if (info != null) {
        Text(
          info,
          modifier = Modifier.padding(bottom = 16.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        labels.forEachIndexed { index, label ->
          SettingsRadioRow(
            title = label,
            subtitle = spec.subtitles.getOrNull(index),
            selected = index == selectedIndex,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = labels.size),
            fontFamily = spec.fonts.getOrNull(index)
          ) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
              onDismiss()
              if (index != selectedIndex) {
                spec.pick(index)
                host.changed()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ConfirmDialog(spec: ConfirmSpec, onDismiss: () -> Unit) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          spec.onConfirm()
          host.changed()
        },
        colors = if (spec.destructive) ButtonDefaults.textButtonColors(contentColor = cs.error) else ButtonDefaults.textButtonColors()
      ) {
        Text(spec.confirm)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(str(R.string.Cancel))
      }
    },
    title = spec.title?.let { title -> { Text(title) } },
    text = { Text(spec.text) }
  )
}

@Composable
fun PromptDialog(spec: PromptSpec, onDismiss: () -> Unit) {
  val host = LocalXgramHost.current
  var value by remember { mutableStateOf(TextFieldValue(spec.initial, TextRange(spec.initial.length))) }
  val focusRequester = remember { FocusRequester() }
  val done = {
    onDismiss()
    spec.onDone(value.text)
    host.changed()
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = done) {
        Text(spec.confirm)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(str(R.string.Cancel))
      }
    },
    title = { Text(spec.title) },
    text = {
      val keyboard = LocalSoftwareKeyboardController.current
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = spec.hint?.let { hint -> { Text(hint) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { done() })
      )
      // In the dialog's own composition, so the field is attached when this runs.
      LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
      }
    }
  )
}
