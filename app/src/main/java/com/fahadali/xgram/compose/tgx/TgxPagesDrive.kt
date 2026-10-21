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
package com.fahadali.xgram.compose.tgx

import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.drive.XgramDrive
import com.fahadali.xgram.drive.XgramDriveController
import com.fahadali.xgram.drive.XgramDriveThumbView
import org.thunderdog.challegram.R
import org.thunderdog.challegram.component.base.SettingView
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.ui.ListItem

/**
 * XGram Drive: folders and files are list items with a 40dp leading tile (XgramDriveThumbView: a file's preview or
 * its kind's icon, a folder's icon, a selected file's check), the name as the headline, the size and date (a folder:
 * what it holds) as the supporting text, and a download's or an upload's progress as the ring at the end. A subfolder's
 * large title has its place in the drive under it.
 */
internal object TgxPagesDrive {
  val pages: Map<Class<*>, TgxPageHints> = mapOf(
    XgramDriveController::class.java to Drive,
  )

  private object Drive : TgxPageHints {
    override fun largeSubtitle(c: ViewController<*>): CharSequence? = (c as? XgramDriveController)?.xgramLargeSubtitle()

    override fun kind(item: ListItem, view: View): TgxRowKind? {
      val isRow = item.id == R.id.btn_xgramDriveFile || item.id == R.id.btn_xgramDriveFolder
      return if (isRow && view is SettingView && (item.data is XgramDrive.Entry || item.data is XgramDrive.Folder)) TgxRowKind.CHAT else null
    }

    override fun leading(c: ViewController<*>, item: ListItem, view: View): (@Composable () -> Unit)? {
      val page = c as? XgramDriveController ?: return null
      return when (val data = item.data) {
        is XgramDrive.Entry -> {
          { DriveThumb { thumb -> thumb.setEntry(page, data) } }
        }
        is XgramDrive.Folder -> {
          { DriveThumb { thumb -> thumb.setFolder(page, data.path) } }
        }
        else -> null
      }
    }

    override fun text(c: ViewController<*>, item: ListItem, view: View): TgxRowText? {
      val page = c as? XgramDriveController ?: return null
      return when (val data = item.data) {
        is XgramDrive.Entry -> TgxRowText(data.name, page.xgramSubtitleOf(data), subtitleLines = 1, progress = page.xgramProgressOf(data))
        is XgramDrive.Folder -> TgxRowText(data.name(), page.xgramFolderSubtitle(data), subtitleLines = 1)
        else -> null
      }
    }
  }

  @Composable
  private fun DriveThumb(bind: (XgramDriveThumbView) -> Unit) {
    AndroidView(
      factory = { context -> XgramDriveThumbView(context) },
      modifier = Modifier.size(40.dp),
      update = { view -> bind(view) },
      onRelease = { view -> view.destroy() }
    )
  }
}
