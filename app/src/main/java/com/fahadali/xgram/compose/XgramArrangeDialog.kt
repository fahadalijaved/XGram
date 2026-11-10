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
package com.fahadali.xgram.compose

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.ui.tgxIcon
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * XGram: arranges a list's rows (the File tab's), each with Up and Down; Save keeps the new order, Reset goes back to
 * the app's own.
 */
object XgramArrangeDialog {
  fun interface SaveCallback {
    fun onSave(keys: List<String>)
  }

  class Item(val key: String, val title: String, val subtitle: String?)

  @JvmStatic
  fun show(context: Context, title: String, rows: List<Item>, onSave: SaveCallback, onReset: Runnable): Boolean {
    return XgramR5ChatPickers.show(context) { dismiss ->
      Content(title, rows, dismiss, onSave, onReset)
    }
  }

  @Composable
  private fun Content(title: String, rows: List<Item>, dismiss: () -> Unit, onSave: SaveCallback, onReset: Runnable) {
    val cs = MaterialTheme.colorScheme
    val order = remember { mutableStateListOf<Item>().apply { addAll(rows) } }
    Surface(
      modifier = Modifier
        .widthIn(max = 460.dp)
        .padding(horizontal = 24.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
      shape = MaterialTheme.shapes.extraLarge,
      color = cs.surfaceContainerHigh
    ) {
      Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
          order.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!row.subtitle.isNullOrEmpty()) {
                  Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
              IconButton(onClick = { order.add(index - 1, order.removeAt(index)) }, enabled = index > 0) {
                Icon(tgxIcon(R.drawable.baseline_keyboard_arrow_up_24), contentDescription = Lang.getString(R.string.XgramArrangeUp))
              }
              IconButton(onClick = { order.add(index + 1, order.removeAt(index)) }, enabled = index < order.size - 1) {
                Icon(tgxIcon(R.drawable.baseline_keyboard_arrow_down_24), contentDescription = Lang.getString(R.string.XgramArrangeDown))
              }
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = {
            dismiss()
            onReset.run()
          }) { Text(Lang.getString(R.string.XgramArrangeReset)) }
          Spacer(Modifier.weight(1f))
          TextButton(onClick = dismiss) { Text(Lang.getString(R.string.Cancel)) }
          TextButton(onClick = {
            dismiss()
            onSave.onSave(order.map { it.key })
          }) { Text(Lang.getString(R.string.Save)) }
        }
      }
    }
  }
}
