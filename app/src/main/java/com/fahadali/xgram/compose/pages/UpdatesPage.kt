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

package com.fahadali.xgram.compose.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.ChoiceSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.update.XgramUpdateScreen
import com.fahadali.xgram.update.XgramUpdates
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.tool.Strings
import org.thunderdog.challegram.unsorted.Settings
import org.thunderdog.challegram.util.AppUpdater

private val CHANNELS = listOf(XgramUpdates.CHANNEL_STABLE, XgramUpdates.CHANNEL_CI, XgramUpdates.CHANNEL_OFF)

private val MODES = listOf(
  Settings.AUTO_UPDATE_MODE_PROMPT, Settings.AUTO_UPDATE_MODE_WIFI_ONLY, Settings.AUTO_UPDATE_MODE_ALWAYS, Settings.AUTO_UPDATE_MODE_NEVER
)

/** Whether the running check was started here: its result (latest, or the error) is shown in a toast when it ends. */
private class ManualCheck {
  var pending = false
}

/**
 * Updates: the channel (Stable, CI builds, Off) as connected buttons, when to download, and the check row that follows
 * the updater (checking, download with its size, download progress, install), as on the classic page.
 */
@Composable
fun UpdatesPage() {
  val host = LocalXgramHost.current
  val updater = host.activity.appUpdater()
  val manualCheck = remember { ManualCheck() }

  DisposableEffect(updater) {
    val listener = object : AppUpdater.Listener {
      override fun onAppUpdateStateChanged(state: Int, oldState: Int, isApk: Boolean) {
        if (manualCheck.pending && oldState == AppUpdater.State.CHECKING) {
          manualCheck.pending = false
          if (state == AppUpdater.State.NONE) {
            // Not a snackbar: the card says it's up to date (or why the check failed) and shows what's in this build
            com.fahadali.xgram.update.XgramUpdateScreen.upToDate(host.controller, XgramUpdates.lastError())
          }
        }
        host.changed()
      }

      override fun onAppUpdateDownloadProgress(bytesDownloaded: Long, totalBytesToDownload: Long) {
        host.changed()
      }
    }
    updater.addListener(listener)
    onDispose { updater.removeListener(listener) }
  }

  SettingsPage(title = str(R.string.XgramUpdates)) {
    val channel = XgramUpdates.channel()
    val state = updater.state()

    group {
      buttons(
        R.id.btn_xgramUpdateChannel, s(R.string.XgramUpdateChannel),
        options = CHANNELS.map { s(XgramUpdates.channelName(it)) to it },
        selected = channel
      ) { picked ->
        if (picked != XgramUpdates.channel()) {
          XgramUpdates.setChannel(picked)
          updater.resetXgramUpdate()
          if (picked != XgramUpdates.CHANNEL_OFF && updater.state() == AppUpdater.State.NONE) {
            manualCheck.pending = true
            updater.checkForUpdatesNow()
          }
        }
      }
      val mode = Settings.instance().getAutoUpdateMode()
      value(
        R.id.btn_xgramUpdateMode, s(R.string.XgramUpdateMode), s(XgramUpdates.modeName(mode)),
        enabled = channel != XgramUpdates.CHANNEL_OFF
      ) {
        host.choose(
          ChoiceSpec(
            title = s(R.string.XgramUpdateMode),
            options = MODES.map { s(XgramUpdates.modeName(it)) to it },
            selected = mode
          ) { picked ->
            Settings.instance().setAutoUpdateMode(picked)
          }
        )
      }
    }
    description(R.string.XgramUpdateChannelInfo)

    group {
      val icon = R.drawable.baseline_system_update_24
      if (channel == XgramUpdates.CHANNEL_OFF) {
        action(R.id.btn_xgramUpdateCheck, s(R.string.CheckForUpdates), icon = icon, enabled = false) { }
      } else {
        when (state) {
          AppUpdater.State.CHECKING -> action(
            R.id.btn_xgramUpdateCheck, s(R.string.CheckingForUpdates), icon = icon, enabled = false,
            trailing = { LoadingIndicator(modifier = Modifier.size(36.dp)) }
          ) { }

          AppUpdater.State.AVAILABLE -> {
            val bytesToDownload = updater.totalBytesToDownload() - updater.bytesDownloaded()
            val title = if (bytesToDownload > 0) s(R.string.DownloadUpdateSize, Strings.buildSize(bytesToDownload)) else s(R.string.DownloadUpdate)
            action(R.id.btn_xgramUpdateCheck, title, icon = icon) {
              // XGram's update screen: the notes, the download and the install in one card
              if (!XgramUpdateScreen.show(host.controller)) {
                updater.downloadUpdate()
              }
            }
          }

          AppUpdater.State.DOWNLOADING -> {
            val downloaded = updater.bytesDownloaded()
            val total = updater.totalBytesToDownload()
            val progress = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            action(
              R.id.btn_xgramUpdateCheck, Lang.getDownloadProgress(downloaded, total, true), icon = icon, enabled = false,
              below = {
                // XGram M3E r6: the determinate wavy indicator moves on the motion scheme's spatial spring between updates
                val shown by animateFloatAsState(
                  targetValue = progress,
                  animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                  label = "xgramUpdateProgress"
                )
                LinearWavyProgressIndicator(
                  progress = { shown.coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
              }
            ) { }
          }

          AppUpdater.State.READY_TO_INSTALL -> action(R.id.btn_xgramUpdateCheck, s(R.string.InstallUpdate), icon = icon) {
            if (!XgramUpdateScreen.install(host.controller)) {
              updater.installUpdate()
            }
          }

          else -> action(R.id.btn_xgramUpdateCheck, s(R.string.CheckForUpdates), icon = icon) {
            if (updater.state() == AppUpdater.State.NONE) {
              manualCheck.pending = true
              updater.checkForUpdatesNow()
            }
          }
        }
      }
      // The build's own kind, from its post; the channel picked above is a separate setting.
      val kind = XgramUpdates.installedKind()
      val installed = if (kind == -1) {
        BuildConfig.VERSION_NAME
      } else {
        s(R.string.XgramUpdateInstalledValue, BuildConfig.VERSION_NAME, s(if (kind == 1) R.string.XgramUpdateKindStable else R.string.XgramUpdateKindCi))
      }
      value(R.id.btn_xgramUpdateInstalled, s(R.string.XgramUpdateInstalled), installed, icon = R.drawable.baseline_info_24) {
        com.fahadali.xgram.appearance.XgramR5SheetsInfo.copy(BuildConfig.VERSION_NAME) // XGram M3E r6: the "Copied" snackbar
      }
    }

    // A found update: download or install, with the changelog and the post in the split button's menu.
    val found = updater.xgramUpdate()
    if (channel != XgramUpdates.CHANNEL_OFF && found != null && (state == AppUpdater.State.AVAILABLE || state == AppUpdater.State.READY_TO_INSTALL)) {
      val install = state == AppUpdater.State.READY_TO_INSTALL
      item("updateSplitButton") {
        UpdateSplitButton(
          host = host,
          label = str(if (install) R.string.InstallUpdate else R.string.XgramUpdateDownload),
          onPrimary = {
            if (!install) {
              updater.downloadUpdate()
            } else if (!XgramUpdateScreen.install(host.controller)) {
              updater.installUpdate()
            }
          },
          onWhatsNew = { XgramUpdates.showWhatsNew(host.controller, found) },
          onOpenPost = { XgramUpdates.openPost(host.controller, found) }
        )
      }
    }
    description(R.string.XgramUpdateCheckInfo)
  }
}

/** Material 3 Expressive split button: the main action, and a menu with What's new and the post. */
@Composable
private fun UpdateSplitButton(
  host: XgramComposeHost,
  label: String,
  onPrimary: () -> Unit,
  onWhatsNew: () -> Unit,
  onOpenPost: () -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  val rotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "xgramUpdateMenuArrow"
  )
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
    horizontalArrangement = Arrangement.End
  ) {
    Box {
      SplitButtonLayout(
        leadingButton = {
          SplitButtonDefaults.LeadingButton(onClick = {
            onPrimary()
            host.changed()
          }) {
            Text(label, maxLines = 1)
          }
        },
        trailingButton = {
          SplitButtonDefaults.TrailingButton(checked = expanded, onCheckedChange = { expanded = it }) {
            Icon(
              tgxIcon(R.drawable.baseline_keyboard_arrow_down_24),
              contentDescription = str(R.string.XgramUpdateMoreActions),
              modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }
            )
          }
        }
      )
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        // Pure black: the menu takes the chip step, as surfaceContainer is the black page under it and the card step
        // is the tiles it can float over (XgramAmoled.floatingRole)
        containerColor = if (com.fahadali.xgram.appearance.XgramAmoled.isActive()) MaterialTheme.colorScheme.surfaceContainerHighest else MenuDefaults.containerColor
      ) {
        DropdownMenuItem(
          text = { Text(str(R.string.XgramWhatsNew)) },
          leadingIcon = { Icon(tgxIcon(R.drawable.baseline_whatshot_24), contentDescription = null) },
          onClick = {
            expanded = false
            onWhatsNew()
          }
        )
        DropdownMenuItem(
          text = { Text(str(R.string.XgramUpdateOpenPost)) },
          leadingIcon = { Icon(tgxIcon(R.drawable.baseline_open_in_browser_24), contentDescription = null) },
          onClick = {
            expanded = false
            onOpenPost()
          }
        )
      }
    }
  }
}
