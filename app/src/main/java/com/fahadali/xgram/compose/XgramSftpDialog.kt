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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.files.XgramSftp
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * XGram: the dialog that adds or edits an SFTP server of the File tab: a name, the host and port, the user, a
 * password and/or a private key file, and the folder it opens at. Saved secrets are not shown: an empty password
 * field keeps the saved one.
 */
object XgramSftpDialog {
  fun interface SaveCallback {
    /** [password] and [privateKey]: null keeps the saved one, empty removes it. */
    fun onSave(name: String, host: String, port: Int, user: String, folder: String, password: String?, privateKey: String?)
  }

  fun interface KeyPicker {
    /** Lets the user pick a private key file; [onKey] gets its text. */
    fun pick(onKey: (String) -> Unit)
  }

  @JvmStatic
  fun show(context: Context, server: XgramSftp.Server?, keyPicker: KeyPicker, onSave: SaveCallback): Boolean {
    return XgramR5ChatPickers.show(context) { dismiss ->
      Content(server, keyPicker, dismiss, onSave)
    }
  }

  @Composable
  private fun Content(server: XgramSftp.Server?, keyPicker: KeyPicker, dismiss: () -> Unit, onSave: SaveCallback) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(server?.name.orEmpty()) }
    var host by remember { mutableStateOf(server?.host.orEmpty()) }
    var port by remember { mutableStateOf((server?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(server?.user.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var removePassword by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(server?.folder.orEmpty()) }
    // null: keep the saved key (or none), "": remove it, text: a new key
    var key by remember { mutableStateOf<String?>(null) }
    val hasKey = if (key != null) key!!.isNotEmpty() else server?.hasKey() == true
    val ready = host.isNotBlank() && user.isNotBlank()
    Surface(
      modifier = Modifier
        .widthIn(max = 460.dp)
        .padding(horizontal = 24.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
      shape = MaterialTheme.shapes.extraLarge,
      color = cs.surfaceContainerHigh
    ) {
      Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
          Lang.getString(if (server == null) R.string.XgramSftpAddTitle else R.string.XgramSftpEdit),
          style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Column(
          Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(host, { host = it.trim() }, label = { Text(Lang.getString(R.string.XgramSftpHost)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(user, { user = it.trim() }, label = { Text(Lang.getString(R.string.XgramSftpUser)) }, singleLine = true,
              modifier = Modifier.weight(1f))
            OutlinedTextField(port, { value -> port = value.filter { it.isDigit() }.take(5) }, label = { Text(Lang.getString(R.string.XgramSftpPort)) }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.widthIn(max = 96.dp))
          }
          OutlinedTextField(
            password, { password = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            label = { Text(Lang.getString(R.string.XgramSftpPassword)) },
            placeholder = { if (server?.hasPassword() == true && !removePassword) Text(Lang.getString(R.string.XgramSftpPasswordKept)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
          )
          if (server?.hasPassword() == true && password.isEmpty() && !removePassword) {
            TextButton(onClick = { removePassword = true }) { Text(Lang.getString(R.string.XgramSftpPasswordRemove)) }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              Lang.getString(if (hasKey) R.string.XgramSftpKeySet else R.string.XgramSftpKeyNone),
              style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)
            )
            if (hasKey) {
              TextButton(onClick = { key = "" }) { Text(Lang.getString(R.string.XgramSftpKeyRemove)) }
            }
            TextButton(onClick = { keyPicker.pick { text -> key = text } }) { Text(Lang.getString(R.string.XgramSftpKeyChoose)) }
          }
          OutlinedTextField(folder, { folder = it }, label = { Text(Lang.getString(R.string.XgramSftpFolder)) }, singleLine = true,
            placeholder = { Text(Lang.getString(R.string.XgramSftpFolderHint)) }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(name, { name = it }, label = { Text(Lang.getString(R.string.XgramSftpName)) }, singleLine = true,
            placeholder = { Text(if (user.isNotBlank() && host.isNotBlank()) "$user@$host" else Lang.getString(R.string.XgramSftpNameHint)) },
            modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = dismiss) { Text(Lang.getString(R.string.Cancel)) }
          TextButton(enabled = ready, onClick = {
            dismiss()
            onSave.onSave(name, host, port.toIntOrNull() ?: 22, user, folder, if (password.isNotEmpty()) password else if (removePassword) "" else null, key)
          }) { Text(Lang.getString(R.string.Save)) }
        }
      }
    }
  }
}
