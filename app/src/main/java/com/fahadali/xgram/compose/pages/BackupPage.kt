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

import androidx.compose.runtime.Composable
import com.fahadali.xgram.XgramApp
import com.fahadali.xgram.XgramBackup
import com.fahadali.xgram.XgramBackupFile
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import org.thunderdog.challegram.R
import org.thunderdog.challegram.tool.UI

/** Backup: export every XGram setting as text, import it from the clipboard, or reset them all. */
@Composable
fun BackupPage() {
  val host = LocalXgramHost.current
  SettingsPage(title = str(R.string.XgramBackup)) {
    group {
      link(R.id.btn_xgramExportSettings, s(R.string.XgramExportSettings), s(R.string.XgramExportSettingsSub), icon = R.drawable.baseline_share_24) {
        XgramBackupFile.export() // a .xgram file: a copy in Downloads/XGram, then the share sheet
      }
      link(R.id.btn_xgramImportSettings, s(R.string.XgramImportSettings), s(R.string.XgramImportSettingsSub), icon = R.drawable.baseline_file_download_24) {
        XgramBackupFile.pickAndImport(host.activity) // Android's file picker, then a confirmation
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
