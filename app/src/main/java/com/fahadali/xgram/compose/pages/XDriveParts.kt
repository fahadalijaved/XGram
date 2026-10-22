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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.xdrive.XDriveIcons
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveOrganizer
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveKind
import com.fahadali.xgram.xdrive.XDriveScope
import com.fahadali.xgram.xdrive.XDriveThumbs
import com.fahadali.xgram.xdrive.XDriveView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import kotlin.math.abs

/** Drop targets that are not a folder: a new folder, out of any folder. */
internal const val DROP_NEW = "\u0000new"
internal const val DROP_OUT = "\u0000out"

/** XDrive's tabs, in the navigation bar's order. */
internal enum class XDriveTab(val title: Int, val icon: Int) {
  HOME(R.string.XgramXDriveHome, R.drawable.baseline_home_24),
  FOLDERS(R.string.XgramXDriveFolders, R.drawable.baseline_folder_24),
  MEDIA(R.string.XgramXDriveMedia, R.drawable.baseline_image_24),
  FILES(R.string.XgramXDriveFiles, R.drawable.baseline_insert_drive_file_24)
}

/**
 * A drag of items onto a folder: it starts with a long press on an item (which selects it and takes the whole
 * selection along) and shows its ghost once the finger moves. Positions are in the page's root coordinates.
 */
@Stable
internal class XDriveDrag {
  var items by mutableStateOf<List<TdApi.Message>>(emptyList())
  var active by mutableStateOf(false)
  var moved by mutableStateOf(false)
  var position by mutableStateOf(Offset.Zero)
  var hovered by mutableStateOf<String?>(null)
  private var start = Offset.Zero
  /** Where each drop target is, by key (a folder id, [DROP_NEW], [DROP_OUT]); kept as they lay out. */
  val targets = HashMap<String, Rect>()

  fun begin(items: List<TdApi.Message>, at: Offset) {
    this.items = items
    start = at
    position = at
    moved = false
    hovered = null
    active = true
  }

  /** Moves the ghost; true when the hovered target changed (a haptic tick). */
  fun moveBy(delta: Offset, slop: Float): Boolean {
    position += delta
    if (!moved && (abs(position.x - start.x) > slop || abs(position.y - start.y) > slop)) moved = true
    if (!moved) return false
    val hit = targets.entries.firstOrNull { it.value.contains(position) }?.key
    if (hit != hovered) {
      hovered = hit
      return hit != null
    }
    return false
  }

  fun end() {
    active = false
    moved = false
    hovered = null
    items = emptyList()
  }
}

/** What XDrive's screen keeps between its tabs: the selection, the drag, the open folder, the search. Main thread. */
@Stable
internal class XDriveState(val tdlib: Tdlib, val host: XgramComposeHost, val scope: CoroutineScope) {
  val index: XDriveIndex = XDriveIndex.of(tdlib)
  var indexVersion by mutableIntStateOf(0)
  val selected = mutableStateMapOf<Long, TdApi.Message>()
  val selecting: Boolean get() = selected.isNotEmpty()
  var folder by mutableStateOf<XDriveScope?>(null)
  var searching by mutableStateOf(false)
  var query by mutableStateOf("")
  var organizing by mutableStateOf(false)
  var mediaView by mutableStateOf(XDriveView.MEDIA)
  var filesView by mutableStateOf(XDriveView.FILES)
  val drag = XDriveDrag()
  val snackbar = SnackbarHostState()
  var root: LayoutCoordinates? = null
  /** The navigation bar's tab, set by the pager. */
  var tab by mutableStateOf(XDriveTab.HOME)
  /** Organize with AI: as long as the screen, so closing its sheet doesn't stop it. */
  val organizer = XDriveOrganizer(tdlib)
  private var organizeJob: Job? = null
  /** The folder whose icon is being picked (the sheet), or null. */
  var iconPicker by mutableStateOf<XDriveIndex.Folder?>(null)

  fun startOrganize() {
    organizeJob?.cancel()
    organizeJob = scope.launch { organizer.run() }
  }

  fun cancelOrganize() {
    organizeJob?.cancel()
    organizeJob = null
    if (organizer.running) organizer.reset()
  }

  fun folders(): List<XDriveIndex.Folder> {
    indexVersion
    return if (index.isLoaded) index.folders() else emptyList()
  }

  fun counts(): Map<String, Int> {
    indexVersion
    return index.counts()
  }

  fun folderOf(message: TdApi.Message): XDriveIndex.Folder? {
    indexVersion
    return index.folderOf(message.id)?.let { index.findFolder(it) }
  }

  fun toggle(message: TdApi.Message) {
    if (selected.containsKey(message.id)) selected.remove(message.id) else selected[message.id] = message
  }

  /** Into [folderId] (null: out of any folder), with Undo; the selection is cleared. */
  fun move(messages: List<TdApi.Message>, folderId: String?) {
    if (messages.isEmpty()) return
    val before = messages.associate { it.id to index.folderOf(it.id) }
    index.setFolder(messages.map { it.id }.toLongArray(), folderId)
    selected.clear()
    val name = folderId?.let { index.findFolder(it)?.name }
    val text = if (name != null) Lang.getString(R.string.XgramXDriveMovedTo, name) else Lang.getString(R.string.XgramXDriveTakenOut)
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      val result = snackbar.showSnackbar(text, actionLabel = Lang.getString(R.string.XgramXDriveUndo), duration = SnackbarDuration.Short)
      if (result == SnackbarResult.ActionPerformed) {
        before.entries.groupBy({ it.value }, { it.key }).forEach { (folder, ids) -> index.setFolder(ids.toLongArray(), folder) }
      }
    }
  }

  fun dropOn(key: String, messages: List<TdApi.Message>) {
    when (key) {
      DROP_OUT -> move(messages, null)
      DROP_NEW -> newFolderFor(messages)
      else -> move(messages, key)
    }
  }

  /** A new folder (its name asked), then [messages] into it. */
  fun newFolderFor(messages: List<TdApi.Message>) {
    host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveNewFolder), hint = Lang.getString(R.string.XgramXDriveFolderName), confirm = Lang.getString(R.string.XgramXDriveCreate)) { text ->
      val name = text.trim()
      if (name.isNotEmpty()) {
        val folder = index.findFolderByName(name) ?: index.createFolder(name)
        if (messages.isNotEmpty()) move(messages, folder.id)
      }
    })
  }

  fun toast(text: String) {
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
    }
  }
}

/** Everything else about the window: the drag's way to keep Telegram X's swipe-back off while it runs. */
private val NO_SWIPE_ANYWHERE: () -> android.graphics.Rect? = {
  android.graphics.Rect(Int.MIN_VALUE / 2, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
}

/**
 * An item's touch: a tap opens it (or toggles it while selecting); a long press selects it and, as the finger moves,
 * drags the selection onto a folder.
 */
@Composable
internal fun Modifier.xdriveItem(state: XDriveState, message: TdApi.Message, onOpen: () -> Unit): Modifier {
  val haptic = LocalHapticFeedback.current
  val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
  return this
    .onPlaced { coordinates[0] = it }
    .combinedClickable(onClick = { if (state.selecting) state.toggle(message) else onOpen() })
    .pointerInput(message.id) {
      detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          if (!state.selected.containsKey(message.id)) state.selected[message.id] = message
          val root = state.root
          val local = coordinates[0]
          val at = if (root != null && local != null && local.isAttached && root.isAttached) root.localPositionOf(local, offset) else offset
          state.drag.begin(state.selected.values.sortedByDescending { it.id }, at)
          state.host.noSwipeBack(state.drag, NO_SWIPE_ANYWHERE)
        },
        onDrag = { change, amount ->
          change.consume()
          if (state.drag.moveBy(amount, viewConfiguration.touchSlop)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        },
        onDragEnd = { finishDrag(state, haptic, true) },
        onDragCancel = { finishDrag(state, haptic, false) }
      )
    }
}

private fun finishDrag(state: XDriveState, haptic: HapticFeedback, drop: Boolean) {
  val drag = state.drag
  val target = if (drop && drag.moved) drag.hovered else null
  val items = drag.items
  drag.end()
  state.host.noSwipeBack(drag, null)
  if (target != null && items.isNotEmpty()) {
    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    state.dropOn(target, items)
  }
}

/** A drop target: registers where it is while it is on screen. */
@Composable
internal fun Modifier.xdriveDropTarget(state: XDriveState, key: String): Modifier {
  DisposableEffect(key) {
    onDispose { state.drag.targets.remove(key) }
  }
  return this.onGloballyPositioned { c ->
    val root = state.root
    if (root != null && root.isAttached && c.isAttached) state.drag.targets[key] = root.localBoundingBoxOf(c, clipBounds = true)
  }
}

/** The thumbnail of an item (its tiny preview first), or its kind's icon on a tile. */
@Composable
internal fun XDriveThumb(message: TdApi.Message, modifier: Modifier, iconSize: Int = 24) {
  val tdlib = LocalXgramHost.current.tdlib
  val (file, mini) = remember(message.id, message.content) { XDriveItems.thumbOf(message) }
  var bitmap by remember(message.id, message.content) { mutableStateOf(XDriveThumbs.cached(file?.id) ?: XDriveThumbs.mini(mini)) }
  LaunchedEffect(message.id, file?.id) {
    if (file != null && XDriveThumbs.cached(file.id) == null) {
      XDriveThumbs.load(tdlib, file, 360)?.let { bitmap = it }
    }
  }
  val image = bitmap
  if (image != null) {
    Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
  } else {
    val kind = XDriveItems.kindOf(message)
    val (container, content) = kindColors(kind)
    Box(modifier.background(container), contentAlignment = Alignment.Center) {
      if (kind == XDriveKind.FILE) {
        Text(extensionOf(message), color = content, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
      } else {
        Icon(tgxIcon(iconOf(kind)), contentDescription = null, tint = content, modifier = Modifier.size(iconSize.dp))
      }
    }
  }
}

/** A file's extension, for its badge ("PDF", "ZIP"). */
internal fun extensionOf(message: TdApi.Message): String {
  val name = (message.content as? TdApi.MessageDocument)?.document?.fileName ?: return "FILE"
  val dot = name.lastIndexOf('.')
  return if (dot in 1 until name.length - 1 && name.length - dot <= 6) name.substring(dot + 1).uppercase() else "FILE"
}

@Composable
internal fun kindColors(kind: XDriveKind): Pair<Color, Color> {
  val c = MaterialTheme.colorScheme
  return when (kind) {
    XDriveKind.LINK, XDriveKind.MUSIC -> Pair(c.tertiaryContainer, c.onTertiaryContainer)
    XDriveKind.NOTE, XDriveKind.VOICE -> Pair(c.secondaryContainer, c.onSecondaryContainer)
    XDriveKind.FILE -> Pair(c.primaryContainer, c.onPrimaryContainer)
    else -> Pair(c.surfaceContainerHighest, c.onSurfaceVariant)
  }
}

internal fun iconOf(kind: XDriveKind): Int = when (kind) {
  XDriveKind.PHOTO -> R.drawable.baseline_image_24
  XDriveKind.VIDEO -> R.drawable.baseline_videocam_24
  XDriveKind.GIF -> R.drawable.deproko_baseline_gif_24
  XDriveKind.FILE -> R.drawable.baseline_insert_drive_file_24
  XDriveKind.MUSIC -> R.drawable.baseline_music_note_24
  XDriveKind.VOICE -> R.drawable.baseline_mic_24
  XDriveKind.LINK -> R.drawable.baseline_link_24
  XDriveKind.NOTE -> R.drawable.baseline_format_text_24
  XDriveKind.OTHER -> R.drawable.baseline_cloud_24
}

/** A row: the thumbnail, the name, its size or site and date, the folder it is in; selected rows tint and round. */
@Composable
internal fun XDriveRow(state: XDriveState, message: TdApi.Message, showFolder: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val isSelected = state.selected.containsKey(message.id)
  val motion = MaterialTheme.motionScheme
  val background by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, motion.defaultEffectsSpec(), label = "rowBackground")
  val corner by animateDpAsState(if (isSelected) 28.dp else 0.dp, motion.defaultSpatialSpec(), label = "rowCorner")
  val thumbCorner by animateDpAsState(if (isSelected) 26.dp else 14.dp, motion.fastSpatialSpec(), label = "thumbCorner")
  Row(
    modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clip(RoundedCornerShape(corner.coerceAtLeast(0.dp))).background(background)
      .xdriveItem(state, message, onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(52.dp)) {
      XDriveThumb(message, Modifier.fillMaxSize().clip(RoundedCornerShape(thumbCorner.coerceAtLeast(0.dp))))
      RowCheck(isSelected)
    }
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      Text(XDriveItems.title(message), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(XDriveItems.subtitle(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val folder = if (showFolder) state.folderOf(message) else null
    if (folder != null) {
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(start = 8.dp)) {
        Row(Modifier.widthIn(max = 120.dp).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          if (folder.emoji != null) {
            Text(folder.emoji!!, fontSize = 12.sp)
          } else {
            Icon(tgxIcon(XDriveIcons.drawableOf(folder.icon)), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
          }
          Text(
            folder.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
          )
        }
      }
    }
  }
}

/** The check over a selected row's thumbnail (outside the row's scope, for the plain AnimatedVisibility). */
@Composable
private fun RowCheck(visible: Boolean) {
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(visible, enter = scaleIn(motion.fastSpatialSpec()), exit = scaleOut(motion.fastSpatialSpec())) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
  }
}

/** A square of a photo and video grid: it shrinks and rounds when selected, a check on it. */
@Composable
internal fun XDriveCell(state: XDriveState, message: TdApi.Message, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val isSelected = state.selected.containsKey(message.id)
  val motion = MaterialTheme.motionScheme
  val scale by animateFloatAsState(if (isSelected) 0.86f else 1f, motion.fastSpatialSpec(), label = "cellScale")
  val corner by animateDpAsState(if (isSelected) 24.dp else 6.dp, motion.fastSpatialSpec(), label = "cellCorner")
  Box(modifier.aspectRatio(1f).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
    Box(Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(corner.coerceAtLeast(0.dp))).xdriveItem(state, message, onOpen)) {
      XDriveThumb(message, Modifier.fillMaxSize())
      val kind = XDriveItems.kindOf(message)
      if (kind == XDriveKind.VIDEO || kind == XDriveKind.GIF) {
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
          Icon(tgxIcon(if (kind == XDriveKind.GIF) R.drawable.deproko_baseline_gif_24 else R.drawable.baseline_play_arrow_36_white), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
      }
    }
    AnimatedVisibility(isSelected, modifier = Modifier.align(Alignment.TopStart).padding(4.dp), enter = scaleIn(motion.fastSpatialSpec()), exit = scaleOut(motion.fastSpatialSpec())) {
      Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
        Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
      }
    }
  }
}

/** Expressive shapes for folder badges, one per folder (by its id), so folders tell apart at a glance. */
@Composable
internal fun folderShape(key: String): Shape {
  val shapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie6Sided,
    MaterialShapes.Sunny, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.Pill, MaterialShapes.Arch, MaterialShapes.Puffy
  )
  val polygon = shapes[Math.floorMod(key.hashCode(), shapes.size)]
  return polygon.toShape()
}

@Composable
internal fun folderColors(key: String): Pair<Color, Color> {
  val c = MaterialTheme.colorScheme
  return when (Math.floorMod(key.hashCode() / 7, 3)) {
    0 -> Pair(c.primaryContainer, c.onPrimaryContainer)
    1 -> Pair(c.secondaryContainer, c.onSecondaryContainer)
    else -> Pair(c.tertiaryContainer, c.onTertiaryContainer)
  }
}

/** A folder's badge: its emoji (or an icon) in its shape. */
@Composable
internal fun FolderBadge(key: String, emoji: String?, icon: Int, size: Int, hovered: Boolean = false) {
  val (container, content) = folderColors(key)
  val motion = MaterialTheme.motionScheme
  val rotation by animateFloatAsState(if (hovered) 30f else 0f, motion.defaultSpatialSpec(), label = "badgeTurn")
  Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }.clip(folderShape(key)).background(container))
    if (emoji != null) {
      Text(emoji, fontSize = (size * 0.46f).sp)
    } else {
      Icon(tgxIcon(icon), contentDescription = null, tint = content, modifier = Modifier.size((size * 0.46f).dp))
    }
  }
}

/**
 * A folder tile: badge, name, how many are in it. It is a drop target ([dropKey]): while a drag hovers it, it grows and
 * its corners tighten; a press tightens them too.
 */
@Composable
internal fun FolderTile(
  state: XDriveState, key: String, title: String, subtitle: String?, emoji: String?, icon: Int, dropKey: String?,
  onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier
) {
  val motion = MaterialTheme.motionScheme
  val hovered = dropKey != null && state.drag.hovered == dropKey
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val corner by animateDpAsState(if (hovered) 40.dp else if (pressed) 16.dp else 28.dp, motion.fastSpatialSpec(), label = "tileCorner")
  val scale by animateFloatAsState(if (hovered) 1.06f else if (pressed) 0.97f else 1f, motion.fastSpatialSpec(), label = "tileScale")
  val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "tileColor")
  Box(modifier.then(if (dropKey != null) Modifier.xdriveDropTarget(state, dropKey) else Modifier).scale(scale)) {
    Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(corner.coerceAtLeast(0.dp))).background(color)
        .combinedClickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
        .padding(16.dp)
    ) {
      FolderBadge(key, emoji, icon, 48, hovered)
      Spacer(Modifier.height(14.dp))
      Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle ?: " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** The dragged items under the finger: the first one's picture, tilted, with how many there are. */
@Composable
internal fun DragGhost(state: XDriveState) {
  val drag = state.drag
  val items = drag.items
  if (items.isEmpty()) return
  val motion = MaterialTheme.motionScheme
  val lift by animateFloatAsState(if (drag.hovered != null) 0.8f else 1f, motion.fastSpatialSpec(), label = "ghostLift")
  val half = with(LocalDensity.current) { 36.dp.toPx() }
  Box(
    Modifier
      .offset { IntOffset((drag.position.x - half).toInt(), (drag.position.y - half * 1.6f).toInt()) }
      .graphicsLayer {
        scaleX = lift
        scaleY = lift
        rotationZ = -6f
        alpha = 0.95f
      }
  ) {
    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 12.dp, tonalElevation = 2.dp, modifier = Modifier.size(72.dp)) {
      XDriveThumb(items[0], Modifier.fillMaxSize(), iconSize = 28)
    }
    if (items.size > 1) {
      Box(
        Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
      ) {
        Text(items.size.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

/** One chip of the drop dock: a folder, a new folder or out of the folder; it grows while a drag hovers it. */
@Composable
internal fun DropChip(state: XDriveState, key: String, label: String, emoji: String?, icon: Int) {
  val motion = MaterialTheme.motionScheme
  val hovered = state.drag.hovered == key
  val scale by animateFloatAsState(if (hovered) 1.12f else 1f, motion.fastSpatialSpec(), label = "chipScale")
  val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, motion.fastEffectsSpec(), label = "chipColor")
  val content by animateColorAsState(if (hovered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, motion.fastEffectsSpec(), label = "chipContent")
  Row(
    Modifier.xdriveDropTarget(state, key).scale(scale).clip(CircleShape).background(color).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (emoji != null) {
      Text(emoji, fontSize = 18.sp)
    } else {
      Icon(tgxIcon(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A section's title on XDrive's pages, with an action at its end. */
@Composable
internal fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) {
      androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
    }
  }
}

@Composable
internal fun EmptyNote(text: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(R.drawable.baseline_cloud_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}
