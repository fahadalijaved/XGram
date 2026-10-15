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

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramApp
import com.fahadali.xgram.XgramBackup
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.tool.Intents
import org.thunderdog.challegram.tool.UI

/** Backup: export every XGram setting as text, import it from the clipboard, or reset them all. */
@Composable
fun BackupPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramBackup)) {
    group {
      link(R.id.btn_xgramExportSettings, s(R.string.XgramExportSettings), s(R.string.XgramExportSettingsSub), icon = R.drawable.baseline_share_24) {
        val json = XgramBackup.export()
        UI.copyText(json, R.string.XgramSettingsCopied)
        Intents.shareText(json)
      }
      link(R.id.btn_xgramImportSettings, s(R.string.XgramImportSettings), s(R.string.XgramImportSettingsSub), icon = R.drawable.baseline_content_paste_24) {
        importFromClipboard(host)
      }
    }
    description(R.string.XgramBackupInfo)

    group {
      action(R.id.btn_xgramResetSettings, s(R.string.XgramResetSettings), icon = R.drawable.baseline_delete_24, destructive = true) {
        host.confirm(
          ConfirmSpec(text = s(R.string.XgramResetSettingsConfirm), confirm = s(R.string.XgramResetSettings), destructive = true) {
            XgramBackup.reset()
            UI.post(Runnable { XgramApp.restart(host.activity) }, 300L)
          }
        )
      }
    }
  }
}

/** Reads the exported text from the clipboard; on success the app restarts with the imported settings. */
private fun importFromClipboard(host: XgramComposeHost) {
  val context = host.activity
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  val clip = if (clipboard != null && clipboard.hasPrimaryClip()) clipboard.primaryClip else null
  val text: CharSequence? = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context) else null
  val count = if (text != null) XgramBackup.importJson(text.toString()) else -1
  if (count < 0) {
    UI.showToast(R.string.XgramImportFailed, Toast.LENGTH_LONG)
  } else {
    UI.showToast(R.string.XgramImportDone, Toast.LENGTH_SHORT)
    UI.post(Runnable { XgramApp.restart(context) }, 800L)
  }
}
