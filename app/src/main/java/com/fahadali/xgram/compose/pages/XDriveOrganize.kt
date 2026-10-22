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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.xdrive.XDriveCloudConfig
import com.fahadali.xgram.xdrive.XDriveCloudProvider
import com.fahadali.xgram.xdrive.XDriveEngine
import com.fahadali.xgram.xdrive.XDriveIcons
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveNano
import com.fahadali.xgram.xdrive.XDriveOrganizer
import com.fahadali.xgram.xdrive.XDriveProposedFolder
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.tool.Strings

/**
 * Organize with AI, over XDrive: how to sort (Gemini Nano on the phone, or the rules), getting Nano ready, the reading
 * and sorting, the proposed folders to look through (rename, switch off, leave items out), then everything goes in at
 * once. The work belongs to the screen ([XDriveState.organizer]): closing this sheet doesn't stop it.
 */
@Composable
internal fun XDriveOrganizeScreen(state: XDriveState, onClose: () -> Unit, onSeeFolders: () -> Unit) {
  val organizer = state.organizer
  LaunchedEffect(organizer) { if (organizer.nanoStatus == null) organizer.checkNano() }
  val topInset = with(LocalDensity.current) { state.host.topInset.toDp() }
  val bottomInset = with(LocalDensity.current) { state.host.bottomInset.toDp() }
  val motion = MaterialTheme.motionScheme
  val close: () -> Unit = {
    if (organizer.phase == XDriveOrganizer.Phase.DONE) organizer.reset()
    onClose()
  }
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = topInset)) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = close) {
        Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.XgramXDriveClose))
      }
      Text(str(R.string.XgramXDriveOrganize), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
    AnimatedContent(
      targetState = if (organizer.running && organizer.phase != XDriveOrganizer.Phase.CONSENT) XDriveOrganizer.Phase.SORTING else organizer.phase,
      transitionSpec = {
        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 }) togetherWith
          (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 8 })
      },
      label = "organizePhase",
      modifier = Modifier.weight(1f)
    ) { phase ->
      when (phase) {
        XDriveOrganizer.Phase.READY -> ReadyStep(organizer, bottomInset) { state.startOrganize() }
        XDriveOrganizer.Phase.REVIEW -> ReviewStep(state, organizer, bottomInset, onCancel = { organizer.reset(); onClose() }) { organizer.apply() }
        XDriveOrganizer.Phase.CONSENT -> ConsentStep(organizer, bottomInset)
        XDriveOrganizer.Phase.DONE -> DoneStep(organizer, onClose = { organizer.reset(); onClose() }, onSeeFolders = { organizer.reset(); onSeeFolders() })
        else -> WorkingStep(organizer) { state.cancelOrganize() }
      }
    }
  }
}

/** What the organizer is doing, in words (the sheet and Home's card). */
@Composable
internal fun organizeStatus(organizer: XDriveOrganizer): String = when {
  organizer.phase == XDriveOrganizer.Phase.PREPARING && organizer.nanoStep == XDriveOrganizer.NanoStep.DOWNLOADING ->
    if (organizer.downloadTotal > 0) {
      str(R.string.XgramXDriveNanoDownloadingSize, Strings.buildSize(organizer.downloaded), Strings.buildSize(organizer.downloadTotal))
    } else {
      str(R.string.XgramXDriveNanoDownloading)
    }
  organizer.phase == XDriveOrganizer.Phase.PREPARING -> str(R.string.XgramXDriveNanoStarting)
  organizer.phase == XDriveOrganizer.Phase.READING -> str(R.string.XgramXDriveReadingNow, Lang.plural(R.string.xMessages, organizer.readCount.toLong()))
  organizer.nanoStep == XDriveOrganizer.NanoStep.NAMING -> str(R.string.XgramXDriveAiNaming, organizer.aiName)
  organizer.nanoStep == XDriveOrganizer.NanoStep.SORTING -> str(R.string.XgramXDriveAiSorting, organizer.aiName, organizer.sortDone, organizer.sortTotal)
  else -> str(R.string.XgramXDriveSortingNow)
}

/** How far along it is (0 to 1), or null: no way to tell. */
internal fun organizeProgress(organizer: XDriveOrganizer): Float? = when {
  organizer.phase == XDriveOrganizer.Phase.PREPARING && organizer.nanoStep == XDriveOrganizer.NanoStep.DOWNLOADING && organizer.downloadTotal > 0 ->
    (organizer.downloaded.toFloat() / organizer.downloadTotal).coerceIn(0f, 1f)
  organizer.nanoStep == XDriveOrganizer.NanoStep.SORTING && organizer.sortTotal > 0 -> organizer.sortDone.toFloat() / organizer.sortTotal
  else -> null
}

/** A turning Expressive shape with the sparkles on it: the organizer's mark. */
@Composable
private fun Mark(size: Int) {
  val c = MaterialTheme.colorScheme
  val turn = rememberInfiniteTransition(label = "markTurn")
  val rotation by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "markRotation")
  Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }.clip(MaterialShapes.Cookie12Sided.toShape()).background(c.primaryContainer))
    Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size((size * 0.42f).dp))
  }
}

@Composable
private fun ReadyStep(organizer: XDriveOrganizer, bottomInset: androidx.compose.ui.unit.Dp, onStart: () -> Unit) {
  val nano = organizer.nanoStatus
  var cloudSheet by remember { mutableStateOf(false) }
  if (cloudSheet) {
    CloudSheet(organizer, bottomInset) { cloudSheet = false }
  }
  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
      item {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Mark(120)
          Spacer(Modifier.height(20.dp))
          Text(str(R.string.XgramXDriveOrganizeTitle), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
          Spacer(Modifier.height(8.dp))
          Text(str(R.string.XgramXDriveOrganizeText), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
          Spacer(Modifier.height(24.dp))
        }
      }
      item {
        val nanoText = when (nano) {
          null -> str(R.string.XgramXDriveEngineChecking)
          XDriveNano.Status.AVAILABLE -> str(R.string.XgramXDriveEngineNanoReady)
          XDriveNano.Status.DOWNLOADABLE, XDriveNano.Status.DOWNLOADING -> str(R.string.XgramXDriveEngineNanoDownload)
          XDriveNano.Status.UNSUPPORTED -> str(R.string.XgramXDriveEngineNanoNo)
        }
        EngineOption(
          title = str(R.string.XgramXDriveEngineNano), text = nanoText, icon = R.drawable.baseline_auto_awesome_24,
          selected = organizer.engine == XDriveEngine.NANO, enabled = nano != null && nano != XDriveNano.Status.UNSUPPORTED
        ) { organizer.engine = XDriveEngine.NANO }
        Spacer(Modifier.height(10.dp))
        val cloud = organizer.cloud
        EngineOption(
          title = str(R.string.XgramXDriveEngineCloud),
          text = if (cloud != null) str(R.string.XgramXDriveEngineCloudSet, cloud.name, cloud.model) else str(R.string.XgramXDriveEngineCloudInfo),
          icon = R.drawable.baseline_cloud_24,
          selected = organizer.engine == XDriveEngine.CLOUD, enabled = true,
          action = if (cloud != null) str(R.string.XgramXDriveCloudChange) else null,
          onAction = { cloudSheet = true }
        ) { if (cloud != null) organizer.engine = XDriveEngine.CLOUD else cloudSheet = true }
        Spacer(Modifier.height(10.dp))
        EngineOption(
          title = str(R.string.XgramXDriveEngineRules), text = str(R.string.XgramXDriveEngineRulesInfo), icon = R.drawable.baseline_tune_24,
          selected = organizer.engine == XDriveEngine.RULES, enabled = true
        ) { organizer.engine = XDriveEngine.RULES }
      }
    }
    Button(
      onClick = onStart,
      enabled = nano != null || organizer.engine == XDriveEngine.RULES,
      modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomInset).height(56.dp),
      contentPadding = ButtonDefaults.contentPaddingFor(56.dp)
    ) {
      Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Text(str(R.string.XgramXDriveOrganizeStart), style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun EngineOption(
  title: String, text: String, icon: Int, selected: Boolean, enabled: Boolean,
  action: String? = null, onAction: (() -> Unit)? = null, onClick: () -> Unit
) {
  val c = MaterialTheme.colorScheme
  val border = if (selected) c.primary else c.outlineVariant
  Row(
    Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f).clip(RoundedCornerShape(24.dp))
      .background(if (selected) c.secondaryContainer else c.surfaceContainer)
      .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(24.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(if (selected) c.primary else c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(icon), contentDescription = null, tint = if (selected) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
      if (action != null && onAction != null) {
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(action) }
      }
    }
    RadioButton(selected = selected, onClick = if (enabled) onClick else null, enabled = enabled)
  }
}

@Composable
private fun WorkingStep(organizer: XDriveOrganizer, onCancel: () -> Unit) {
  val step = organizer.nanoStep
  Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    LoadingIndicator(Modifier.size(120.dp))
    Spacer(Modifier.height(28.dp))
    Text(organizeStatus(organizer), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    val progress = organizeProgress(organizer)
    if (progress != null) {
      LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(.7f))
    } else {
      LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(.7f))
    }
    if (step == XDriveOrganizer.NanoStep.DOWNLOADING) {
      Spacer(Modifier.height(20.dp))
      Text(str(R.string.XgramXDriveNanoDownloadingInfo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(12.dp))
    Text(str(R.string.XgramXDriveKeepsGoing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
    if (step != XDriveOrganizer.NanoStep.NONE) {
      FilledTonalButton(onClick = { organizer.useRulesNow() }) {
        Icon(tgxIcon(R.drawable.baseline_tune_24), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(str(R.string.XgramXDriveUseRulesNow))
      }
    }
    TextButton(onClick = onCancel) { Text(str(R.string.Cancel)) }
  }
}

@Composable
private fun ReviewStep(state: XDriveState, organizer: XDriveOrganizer, bottomInset: androidx.compose.ui.unit.Dp, onCancel: () -> Unit, onApply: () -> Unit) {
  val folders = organizer.folders
  if (folders.isEmpty()) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
      EmptyNote(str(R.string.XgramXDriveNothingToSort))
      TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(str(R.string.XgramXDriveClose)) }
    }
    return
  }
  val total = folders.filter { it.enabled }.sumOf { it.count }
  val folderCount = folders.count { it.enabled && it.count > 0 }
  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item(key = "head") {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
          Text(
            str(R.string.XgramXDriveReviewTitle, Lang.plural(R.string.xFolders, folderCount.toLong()), Lang.plural(R.string.xMessages, total.toLong())),
            style = MaterialTheme.typography.headlineSmall
          )
          Spacer(Modifier.height(6.dp))
          Text(str(R.string.XgramXDriveReviewInfo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          val note = organizer.note
          if (note != null) {
            Spacer(Modifier.height(10.dp))
            Row(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(tgxIcon(R.drawable.baseline_info_24), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
              Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(start = 12.dp))
            }
          }
          if (organizer.unsortedLeft > 0) {
            Spacer(Modifier.height(4.dp))
            Text(str(R.string.XgramXDriveReviewLeft, Lang.plural(R.string.xMessages, organizer.unsortedLeft.toLong())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      items(folders, key = { System.identityHashCode(it) }) { folder ->
        ProposalCard(state, folder, Modifier.animateItem())
      }
    }
    Button(
      onClick = onApply,
      enabled = total > 0,
      modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomInset).height(56.dp),
      contentPadding = ButtonDefaults.contentPaddingFor(56.dp)
    ) {
      Text(str(R.string.XgramXDriveReviewApply, Lang.plural(R.string.xMessages, total.toLong())), style = MaterialTheme.typography.titleMedium)
    }
  }
}

/** A proposed folder: its badge and name (tap to rename), how many go in, on or off, and what goes in. */
@Composable
private fun ProposalCard(state: XDriveState, folder: XDriveProposedFolder, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  var open by remember { mutableStateOf(false) }
  val motion = MaterialTheme.motionScheme
  Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (folder.enabled) c.surfaceContainerHigh else c.surfaceContainerLow).alpha(if (folder.enabled) 1f else .6f)) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      FolderBadge(folder.name, folder.emoji, XDriveIcons.drawableOf(folder.icon), 48)
      Column(
        Modifier.weight(1f).padding(horizontal = 14.dp).clickable {
          state.host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveRename), hint = Lang.getString(R.string.XgramXDriveFolderName), initial = folder.name) { text ->
            if (text.isNotBlank()) folder.name = text.trim()
          })
        }
      ) {
        Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          Lang.plural(R.string.xMessages, folder.count.toLong()) + if (folder.existingId != null) " · " + str(R.string.XgramXDriveReviewExisting) else "",
          style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
      }
      Switch(checked = folder.enabled, onCheckedChange = { folder.enabled = it })
    }
    // A glance at what goes in
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(folder.items.take(12), key = { it.id }) { message ->
        XDriveThumb(message, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), iconSize = 20)
      }
    }
    TextButton(onClick = { open = !open }, modifier = Modifier.padding(start = 6.dp)) {
      Text(if (open) str(R.string.XgramXDriveHideItems) else str(R.string.XgramXDriveShowItems))
    }
    AnimatedVisibility(open, enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut()) {
      Column(Modifier.padding(bottom = 8.dp)) {
        for (message in folder.items.take(200)) {
          val left = folder.left.contains(message.id)
          Row(
            Modifier.fillMaxWidth().clickable { if (left) folder.left.remove(message.id) else folder.left.add(message.id) }.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(checked = !left, onCheckedChange = { if (it) folder.left.remove(message.id) else folder.left.add(message.id) })
            XDriveThumb(message, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), iconSize = 18)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
              Text(XDriveItems.title(message), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(XDriveItems.subtitle(message), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DoneStep(organizer: XDriveOrganizer, onClose: () -> Unit, onSeeFolders: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }
  Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    AnimatedVisibility(shown, enter = scaleIn(motion.slowSpatialSpec(), initialScale = 0.3f) + fadeIn(motion.defaultEffectsSpec())) {
      Box(Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.primary), contentAlignment = Alignment.Center) {
        Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(56.dp))
      }
    }
    Spacer(Modifier.height(28.dp))
    Text(str(R.string.XgramXDriveDoneTitle), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
      str(R.string.XgramXDriveDoneText, Lang.plural(R.string.xMessages, organizer.movedCount.toLong()), Lang.plural(R.string.xFolders, organizer.folders.count { it.enabled && it.count > 0 }.toLong())),
      style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    Button(onClick = onSeeFolders, modifier = Modifier.height(56.dp), contentPadding = ButtonDefaults.contentPaddingFor(56.dp)) {
      Text(str(R.string.XgramXDriveSeeFolders), style = MaterialTheme.typography.titleMedium)
    }
    TextButton(onClick = onClose) { Text(str(R.string.XgramXDriveClose)) }
  }
}

/** Before anything goes to the cloud: what, how much, where, and a few lines exactly as they would be sent. */
@Composable
private fun ConsentStep(organizer: XDriveOrganizer, bottomInset: androidx.compose.ui.unit.Dp) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
      item {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(tgxIcon(R.drawable.baseline_cloud_24), contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(40.dp))
          }
          Spacer(Modifier.height(20.dp))
          Text(str(R.string.XgramXDriveConsentTitle, organizer.aiName), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
          Spacer(Modifier.height(10.dp))
          Text(
            str(R.string.XgramXDriveConsentText, Lang.plural(R.string.xMessages, organizer.consentCount.toLong()), organizer.aiName),
            style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(20.dp))
        }
      }
      item {
        Text(str(R.string.XgramXDriveConsentSample), style = MaterialTheme.typography.titleSmall, color = c.primary, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          for (line in organizer.consentSample) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomInset)) {
      Button(onClick = { organizer.answerConsent(true) }, modifier = Modifier.fillMaxWidth().height(56.dp), contentPadding = ButtonDefaults.contentPaddingFor(56.dp)) {
        Text(str(R.string.XgramXDriveConsentSend), style = MaterialTheme.typography.titleMedium)
      }
      TextButton(onClick = { organizer.answerConsent(false) }, modifier = Modifier.fillMaxWidth()) {
        Text(str(R.string.XgramXDriveConsentRules))
      }
    }
  }
}

/** Setting up the cloud AI: the provider (connected toggle buttons), the user's key, the model, a base URL for OpenAI-compatible. */
@Composable
private fun CloudSheet(organizer: XDriveOrganizer, bottomInset: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit) {
  val settings = com.fahadali.xgram.XgramSettings.instance()
  var provider by remember { mutableStateOf(XDriveCloudProvider.of(settings.xdriveAiProvider()) ?: XDriveCloudProvider.CLAUDE) }
  var key by remember(provider) { mutableStateOf(settings.xdriveAiKey(provider.id)) }
  var model by remember(provider) { mutableStateOf(settings.xdriveAiModel(provider.id).ifEmpty { provider.defaultModel }) }
  var baseUrl by remember { mutableStateOf(settings.xdriveAiBaseUrl().ifEmpty { XDriveCloudConfig.OPENAI_BASE_URL }) }
  val saved = remember(provider) { settings.xdriveAiKey(provider.id).isNotEmpty() }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp + bottomInset),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(str(R.string.XgramXDriveCloudTitle), style = MaterialTheme.typography.titleLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
        val all = XDriveCloudProvider.entries
        all.forEachIndexed { i, p ->
          ToggleButton(
            checked = p == provider,
            onCheckedChange = { provider = p },
            modifier = Modifier.weight(1f),
            shapes = when (i) {
              0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
              all.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
              else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
          ) {
            Text(str(p.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      Text(str(provider.keyHelp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(
        value = key, onValueChange = { key = it.trim() }, singleLine = true,
        label = { Text(str(R.string.XgramXDriveCloudKey)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = model, onValueChange = { model = it.trim() }, singleLine = true,
        label = { Text(str(R.string.XgramXDriveCloudModel)) },
        modifier = Modifier.fillMaxWidth()
      )
      if (provider == XDriveCloudProvider.OPENAI) {
        OutlinedTextField(
          value = baseUrl, onValueChange = { baseUrl = it.trim() }, singleLine = true,
          label = { Text(str(R.string.XgramXDriveCloudBaseUrl)) },
          modifier = Modifier.fillMaxWidth()
        )
      }
      Text(str(R.string.XgramXDriveCloudKeyInfo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Button(
        onClick = {
          settings.setXdriveAi(provider.id, key, model, if (provider == XDriveCloudProvider.OPENAI) baseUrl else null)
          organizer.cloudChanged()
          organizer.engine = XDriveEngine.CLOUD
          onDismiss()
        },
        enabled = key.isNotEmpty() && model.isNotEmpty() && (provider != XDriveCloudProvider.OPENAI || baseUrl.startsWith("http")),
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(56.dp)
      ) {
        Text(str(R.string.Save), style = MaterialTheme.typography.titleMedium)
      }
      if (saved) {
        TextButton(onClick = {
          settings.removeXdriveAi(provider.id)
          organizer.cloudChanged()
          onDismiss()
        }, modifier = Modifier.fillMaxWidth()) {
          Text(str(R.string.XgramXDriveCloudRemove), color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}
