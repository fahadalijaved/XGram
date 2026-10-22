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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.xdrive.XDriveActions
import com.fahadali.xgram.xdrive.XDriveFeed
import com.fahadali.xgram.xdrive.XDriveIcons
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveKind
import com.fahadali.xgram.xdrive.XDriveOrganizer
import com.fahadali.xgram.xdrive.XDriveScope
import com.fahadali.xgram.xdrive.XDriveThumbs
import com.fahadali.xgram.xdrive.XDriveView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import kotlin.math.abs

/** Drop targets that are not a folder: a new folder, out of any folder. */
internal const val DROP_NEW = "\u0000new"
internal const val DROP_OUT = "\u0000out"

/** Bounds of the dock (drops there only go to its chips), the search bar and Organize's card (what grows out of them). */
internal const val BOUNDS_DOCK = "dock"
internal const val BOUNDS_SEARCH = "search"
internal const val BOUNDS_ORGANIZE = "organize"

/** The layers over the tabs, as a predictive back gesture names the one it plays on. */
internal const val LAYER_FOLDER = "folder"
internal const val LAYER_SEARCH = "search"
internal const val LAYER_ORGANIZE = "organize"

/** XDrive's tabs, in the navigation bar's order. */
internal enum class XDriveTab(val title: Int, val icon: Int) {
  HOME(R.string.XgramXDriveHome, R.drawable.baseline_home_24),
  FOLDERS(R.string.XgramXDriveFolders, R.drawable.baseline_folder_24),
  MEDIA(R.string.XgramXDriveMedia, R.drawable.baseline_image_24),
  FILES(R.string.XgramXDriveFiles, R.drawable.baseline_insert_drive_file_24)
}

/** How the Files tab orders what it shows. */
internal enum class XDriveSort(val title: Int) {
  DATE(R.string.XgramXDriveSortDate), NAME(R.string.XgramXDriveSortName), SIZE(R.string.XgramXDriveSortSize)
}

/** "1 item", "12 items". */
internal fun items(count: Int): String =
  Lang.getString(if (count == 1) R.string.XgramXDriveItemsOne else R.string.XgramXDriveItemsMany, count)

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
  /** The drop key under the finger (a folder id, [DROP_NEW], [DROP_OUT]). */
  var hovered by mutableStateOf<String?>(null)
  /** The target under the finger (its own key: one folder can be a tile and a dock chip at once). */
  var hoveredTarget by mutableStateOf<String?>(null)
  private var start = Offset.Zero

  class Target(var bounds: Rect, val drop: String, val dock: Boolean)

  /** Every drop target on screen by its own key ("dock:<id>", "tile:folders:<id>"), kept as they lay out. */
  val targets = HashMap<String, Target>()

  fun begin(items: List<TdApi.Message>, at: Offset) {
    this.items = items
    start = at
    position = at
    moved = false
    hovered = null
    hoveredTarget = null
    active = true
  }

  /**
   * The target at [p]: over the dock only its chips count (it lies on top of the tiles); [tiles] false (a folder or
   * the search covers the tabs) leaves only the dock's.
   */
  private fun hit(p: Offset, dockArea: Rect?, tiles: Boolean): Map.Entry<String, Target>? {
    if (dockArea != null && dockArea.contains(p)) {
      return targets.entries.firstOrNull { it.value.dock && it.value.bounds.contains(p) }
    }
    if (!tiles) return null
    return targets.entries.firstOrNull { !it.value.dock && it.value.bounds.contains(p) }
  }

  /** Moves the ghost; true when it went over another target (a haptic tick). */
  fun moveBy(delta: Offset, slop: Float, dockArea: Rect?, tiles: Boolean): Boolean {
    position += delta
    if (!moved && (abs(position.x - start.x) > slop || abs(position.y - start.y) > slop)) moved = true
    if (!moved) return false
    val hit = hit(position, dockArea, tiles)
    if (hit?.key != hoveredTarget) {
      hoveredTarget = hit?.key
      hovered = hit?.value?.drop
      return hit != null
    }
    return false
  }

  fun end() {
    active = false
    moved = false
    hovered = null
    hoveredTarget = null
    items = emptyList()
  }
}

/**
 * What XDrive's screen keeps while it is open: the selection, the drag, the open folder, the search, Organize, and
 * the lists of every tab with their scroll positions (so a folder or the search opened over the tabs takes nothing
 * away). Main thread.
 */
@Stable
internal class XDriveState(val tdlib: Tdlib, val host: XgramComposeHost, val scope: CoroutineScope) {
  val index: XDriveIndex = XDriveIndex.of(tdlib)
  var indexVersion by mutableIntStateOf(0)
  val selected = mutableStateMapOf<Long, TdApi.Message>()
  val selecting: Boolean get() = selected.isNotEmpty()

  /** The folder open over the tabs (Unsorted, or a folder), and the tile it grew out of. */
  var folder by mutableStateOf<XDriveScope?>(null)
  var folderOrigin: String? = null
  var folderGrid by mutableStateOf(false)

  var searching by mutableStateOf(false)
  var query by mutableStateOf("")
  var searchView by mutableStateOf(XDriveView.ALL)
  /** Where the search looks: everything, or the folder it was started from. */
  var searchIn by mutableStateOf<XDriveScope>(XDriveScope.Everything)

  var organizing by mutableStateOf(false)
  var fabOpen by mutableStateOf(false)
  /** The floating button hides while a list scrolls down, and comes back as it scrolls up. */
  var fabShown by mutableStateOf(true)

  var mediaView by mutableStateOf(XDriveView.MEDIA)
  var filesView by mutableStateOf(XDriveView.FILES)
  var filesSort by mutableStateOf(XDriveSort.DATE)

  val drag = XDriveDrag()
  val snackbar = SnackbarHostState()
  var root: LayoutCoordinates? = null
  /** The navigation bar's tab, set by the pager. */
  var tab by mutableStateOf(XDriveTab.HOME)

  /** A predictive back gesture over the top state: 0 to 1 while the finger moves, from which edge, on which layer. */
  var backProgress by mutableFloatStateOf(0f)
  var backFromRight by mutableStateOf(false)
  var backLayer by mutableStateOf<String?>(null)
  /**
   * What is left of the last gesture: its cancel animation, or the reset in case its layer never closes. A new gesture
   * stops it, so it can't take the new one's layer or progress away halfway through.
   */
  var backJob: Job? = null

  /** What Organize grew out of (its card, a button), or null: the middle. */
  var organizeOrigin: String? = null

  /** Folder tiles take drops only while nothing covers the tabs. */
  val tilesTakeDrops: Boolean get() = folder == null && !searching && !organizing

  /** The layer a back gesture would close now (its progress plays there), or null: none (a selection, the menu). */
  fun backLayerNow(): String? = when {
    fabOpen -> null
    organizing -> LAYER_ORGANIZE
    selecting -> null
    searching -> LAYER_SEARCH
    folder != null -> LAYER_FOLDER
    else -> null
  }

  /** Organize with AI: as long as the screen, so closing its sheet doesn't stop it. */
  val organizer = XDriveOrganizer(tdlib)
  private var organizeJob: Job? = null
  /** The run starts over once Organize's layer has closed (it shrinks away showing what it showed), or at its next open. */
  private var organizeStartOver = false
  /** The folder whose icon is being picked (the sheet), or null. */
  var iconPicker by mutableStateOf<XDriveIndex.Folder?>(null)

  /** Where things are on screen (folder tiles, the search bar, the dock), in root coordinates. */
  val bounds = HashMap<String, Rect>()

  private val feeds = HashMap<String, XDriveFeed>()
  private val grids = HashMap<String, LazyGridState>()
  private val lists = HashMap<String, LazyListState>()

  /** A list of XDrive, made once and kept (attached) while the screen is open; searches only the last few. */
  fun feed(view: XDriveView, scope: XDriveScope, query: String = ""): XDriveFeed {
    val key = view.name + "|" + scopeKey(scope) + "|" + query
    feeds[key]?.let { return it }
    if (query.isNotEmpty()) {
      val searches = feeds.keys.filter { !it.endsWith("|") }
      if (searches.size >= 4) searches.take(searches.size - 3).forEach { feeds.remove(it)?.detach() }
    } else if (scope is XDriveScope.InFolder) {
      // Folders opened before (not the open one): only the last few stay attached
      val opened = feeds.keys.filter { it.contains("|f:") && it.endsWith("|") }
      if (opened.size >= 3) opened.take(opened.size - 2).forEach { feeds.remove(it)?.detach() }
    }
    return XDriveFeed(tdlib, view, scope, query).also {
      feeds[key] = it
      it.attach()
    }
  }

  fun grid(key: String): LazyGridState = grids.getOrPut(key) { LazyGridState() }
  fun list(key: String): LazyListState = lists.getOrPut(key) { LazyListState() }

  fun indexChanged() {
    indexVersion++
    feeds.values.forEach { it.indexChanged() }
  }

  fun dispose() {
    feeds.values.forEach { it.detach() }
    feeds.clear()
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

  fun selectAll(list: List<TdApi.Message>) {
    for (message in list) selected[message.id] = message
  }

  // Opening or closing a layer shows the floating button again: the scroll that hid it was elsewhere, and a list that
  // can't scroll (an empty folder) could never bring it back

  fun openFolder(scope: XDriveScope, origin: String?) {
    selected.clear()
    fabOpen = false
    fabShown = true
    folderOrigin = origin
    folder = scope
  }

  fun closeFolder() {
    selected.clear()
    fabOpen = false
    fabShown = true
    folder = null
  }

  /** A new search starts empty; closing leaves the query, so the layer shrinks away with its results, not the start note. */
  fun openSearch() {
    selected.clear()
    fabOpen = false
    query = ""
    searchIn = folder ?: XDriveScope.Everything
    searching = true
  }

  fun closeSearch() {
    selected.clear()
    fabShown = true
    searching = false
  }

  fun openOrganize(origin: String?) {
    fabOpen = false
    selected.clear()
    // Reopened before the last close finished: it starts over now
    if (organizeStartOver) organizeHidden()
    organizeOrigin = origin
    organizing = true
  }

  /**
   * Organize's close (its X, Cancel, Done, See folders and back alike): a finished, empty or cancelled ([startOver]) run
   * starts over next time, once the layer has closed ([organizeHidden]).
   */
  fun closeOrganize(startOver: Boolean = false) {
    if (startOver || organizer.phase == XDriveOrganizer.Phase.DONE || (organizer.phase == XDriveOrganizer.Phase.REVIEW && organizer.folders.isEmpty())) {
      organizeStartOver = true
    }
    fabShown = true
    organizing = false
  }

  /** Organize's layer finished closing (or opens again): a run that was to start over does now. */
  fun organizeHidden() {
    if (!organizeStartOver) return
    organizeStartOver = false
    if (!organizer.running) organizer.reset()
  }

  /** What back does now, in this order: the menu, Organize, the selection, the search, the folder; then Home. */
  fun backAction(homeTab: Boolean, goHome: () -> Unit): (() -> Unit)? = when {
    fabOpen -> { { fabOpen = false } }
    organizing -> { { closeOrganize() } }
    selecting -> { { selected.clear() } }
    searching -> { { closeSearch() } }
    folder != null -> { { closeFolder() } }
    !homeTab -> goHome
    else -> null
  }

  /** Into [folderId] (null: out of any folder), with Undo; the selection is cleared. */
  fun move(messages: List<TdApi.Message>, folderId: String?) {
    if (messages.isEmpty()) return
    val before = messages.associate { it.id to index.folderOf(it.id) }
    index.setFolder(messages.map { it.id }.toLongArray(), folderId)
    selected.clear()
    val name = folderId?.let { index.findFolder(it)?.name }
    val text = if (name != null) Lang.getString(R.string.XgramXDriveMovedTo, name) else Lang.getString(R.string.XgramXDriveTakenOut)
    undoable(text) {
      before.entries.groupBy({ it.value }, { it.key }).forEach { (folder, ids) -> index.setFolder(ids.toLongArray(), folder) }
    }
  }

  /** The folder chooser for [messages] (the toolbar, TalkBack's Move to folder), then [move] with Undo. */
  fun chooseFolderFor(messages: List<TdApi.Message>) {
    if (messages.isEmpty()) return
    XDriveActions.chooseFolder(host, messages.map { it.id }.toLongArray()) { id -> move(messages, id) }
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
        val folder = index.findFolderByName(name) ?: index.createFolder(name, XDriveIcons.keyForName(name))
        if (messages.isNotEmpty()) move(messages, folder.id)
      }
    })
  }

  /** Deletes a folder after asking (its items stay in XDrive), with Undo. */
  fun deleteFolder(folder: XDriveIndex.Folder) {
    val ids = index.messagesIn(folder.id)
    val id = folder.id
    val name = folder.name
    val emoji = folder.emoji
    val icon = folder.icon
    XDriveActions.deleteFolder(host, folder) {
      if ((this.folder as? XDriveScope.InFolder)?.folderId == id) closeFolder()
      undoable(Lang.getString(R.string.XgramXDriveFolderDeleted, name)) { index.restoreFolder(id, name, emoji, icon, ids) }
    }
  }

  private fun undoable(text: String, undo: () -> Unit) {
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      val result = snackbar.showSnackbar(text, actionLabel = Lang.getString(R.string.XgramXDriveUndo), duration = SnackbarDuration.Short)
      if (result == SnackbarResult.ActionPerformed) undo()
    }
  }

  fun toast(text: String) {
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
    }
  }

  fun startOrganize() {
    organizeJob?.cancel()
    organizeJob = scope.launch { organizer.run() }
  }

  fun cancelOrganize() {
    organizeJob?.cancel()
    organizeJob = null
    if (organizer.running) organizer.reset()
  }

  companion object {
    fun scopeKey(scope: XDriveScope): String = when (scope) {
      XDriveScope.Everything -> "all"
      XDriveScope.Unsorted -> "unsorted"
      is XDriveScope.InFolder -> "f:" + scope.folderId
    }
  }
}

/** Everywhere in the window: the drag's (and in-page states') way to keep Telegram X's swipe-back off. */
internal val NO_SWIPE_ANYWHERE: () -> android.graphics.Rect? = {
  android.graphics.Rect(Int.MIN_VALUE / 2, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
}

/** Keeps where this element is ([XDriveState.bounds]) while it is there, for what grows out of it. */
@Composable
internal fun Modifier.xdriveBounds(state: XDriveState, key: String): Modifier {
  DisposableEffect(key) {
    onDispose { state.bounds.remove(key) }
  }
  return this.onGloballyPositioned { c ->
    val root = state.root
    if (root != null && root.isAttached && c.isAttached) state.bounds[key] = root.localBoundingBoxOf(c, clipBounds = false)
  }
}

/**
 * An item's touch: a tap opens it (or toggles it while selecting); a long press selects it and, as the finger moves,
 * drags the selection onto a folder. TalkBack and Switch Access get the same through actions: Select, Move to folder.
 */
@Composable
internal fun Modifier.xdriveItem(state: XDriveState, message: TdApi.Message, describe: String? = null, onOpen: () -> Unit): Modifier {
  val haptic = LocalHapticFeedback.current
  val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
  val isSelected = state.selected.containsKey(message.id)
  val selectLabel = str(if (isSelected) R.string.XgramXDriveDeselect else R.string.XgramXDriveSelect)
  val moveLabel = str(R.string.XgramXDriveMoveTo)
  val openLabel = str(R.string.XgramXDriveOpenItem)
  return this
    .onPlaced { coordinates[0] = it }
    .semantics(mergeDescendants = true) {
      selected = isSelected
      if (describe != null) contentDescription = describe
      onLongClick(selectLabel) {
        state.toggle(message)
        true
      }
      customActions = listOf(
        CustomAccessibilityAction(moveLabel) {
          state.chooseFolderFor(if (isSelected) state.selected.values.toList() else listOf(message))
          true
        }
      )
    }
    .combinedClickable(onClickLabel = if (state.selecting) selectLabel else openLabel, onClick = { if (state.selecting) state.toggle(message) else onOpen() })
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
          if (state.drag.moveBy(amount, viewConfiguration.touchSlop, state.bounds[BOUNDS_DOCK], state.tilesTakeDrops)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
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

/** A drop target: registers where it is under its own [targetKey] while on screen; drops go to [dropKey]. */
@Composable
internal fun Modifier.xdriveDropTarget(state: XDriveState, targetKey: String, dropKey: String, dock: Boolean = false): Modifier {
  DisposableEffect(targetKey, dropKey) {
    onDispose { state.drag.targets.remove(targetKey) }
  }
  return this.onGloballyPositioned { c ->
    val root = state.root
    if (root != null && root.isAttached && c.isAttached) {
      val rect = root.localBoundingBoxOf(c, clipBounds = true)
      val target = state.drag.targets[targetKey]
      if (target != null && target.drop == dropKey) target.bounds = rect else state.drag.targets[targetKey] = XDriveDrag.Target(rect, dropKey, dock)
    }
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

/** What TalkBack says for a tile: its kind (or name) and date. */
internal fun describe(message: TdApi.Message): String {
  val kind = when (XDriveItems.kindOf(message)) {
    XDriveKind.PHOTO -> Lang.getString(R.string.XgramXDriveKindPhoto)
    XDriveKind.VIDEO -> Lang.getString(R.string.XgramXDriveKindVideo)
    XDriveKind.GIF -> Lang.getString(R.string.XgramXDriveKindGif)
    else -> XDriveItems.title(message)
  }
  return kind + ", " + XDriveItems.subtitle(message)
}

/**
 * A row in Expressive's segmented style (as XGram's settings pages): [shapes] from its place in its group, the
 * thumbnail, name, size or site and date, the folder it is in. A selected row rounds off and tints.
 */
@Composable
internal fun XDriveRow(state: XDriveState, message: TdApi.Message, shapes: ListItemShapes, showFolder: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val isSelected = state.selected.containsKey(message.id)
  val cs = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  val container by animateColorAsState(if (isSelected) cs.secondaryContainer else cs.surfaceBright, motion.defaultEffectsSpec(), label = "rowContainer")
  val shape = if (isSelected) shapes.selectedShape else shapes.shape
  val thumbCorner by animateDpAsState(if (isSelected) 26.dp else 14.dp, motion.fastSpatialSpec(), label = "thumbCorner")
  SegmentedListItem(
    shapes = shapes,
    modifier = modifier.fillMaxWidth().clip(shape).xdriveItem(state, message, onOpen = onOpen),
    leadingContent = {
      Box(Modifier.size(48.dp)) {
        XDriveThumb(message, Modifier.fillMaxSize().clip(RoundedCornerShape(thumbCorner.coerceAtLeast(0.dp))))
        RowCheck(isSelected)
      }
    },
    supportingContent = { Text(XDriveItems.subtitle(message), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailingContent = if (showFolder) {
      { state.folderOf(message)?.let { FolderChip(it) } }
    } else null,
    colors = ListItemDefaults.segmentedColors(containerColor = container),
    content = { Text(XDriveItems.title(message), maxLines = 1, overflow = TextOverflow.Ellipsis) }
  )
}

/** The folder an item is in, as a small chip. */
@Composable
private fun FolderChip(folder: XDriveIndex.Folder) {
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(Modifier.widthIn(max = 120.dp).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      val emoji = folder.emoji
      if (emoji != null) {
        Text(emoji, fontSize = 12.sp)
      } else {
        Icon(tgxIcon(XDriveIcons.drawableOf(folder.icon)), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
      }
      Text(
        folder.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp)
      )
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

/**
 * A tile of a grid: a photo, video or GIF square (it shrinks and rounds when selected, a check on it), or anything
 * else as its kind's tile with the name along its bottom (the folder screen's grid view).
 */
@Composable
internal fun XDriveCell(state: XDriveState, message: TdApi.Message, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val isSelected = state.selected.containsKey(message.id)
  val motion = MaterialTheme.motionScheme
  val scale by animateFloatAsState(if (isSelected) 0.86f else 1f, motion.fastSpatialSpec(), label = "cellScale")
  val corner by animateDpAsState(if (isSelected) 24.dp else 8.dp, motion.fastSpatialSpec(), label = "cellCorner")
  val kind = XDriveItems.kindOf(message)
  val visual = kind == XDriveKind.PHOTO || kind == XDriveKind.VIDEO || kind == XDriveKind.GIF
  Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
    Box(Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(corner.coerceAtLeast(0.dp))).xdriveItem(state, message, describe(message), onOpen)) {
      XDriveThumb(message, Modifier.fillMaxSize(), iconSize = if (visual) 24 else 32)
      if (kind == XDriveKind.VIDEO || kind == XDriveKind.GIF) {
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
          Icon(tgxIcon(if (kind == XDriveKind.GIF) R.drawable.deproko_baseline_gif_24 else R.drawable.baseline_play_arrow_36_white), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
      }
      if (!visual) {
        Text(
          XDriveItems.title(message), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = .92f)).padding(horizontal = 6.dp, vertical = 4.dp)
        )
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
  return shapes[Math.floorMod(key.hashCode(), shapes.size)].toShape()
}

/** A folder badge's colors: one of the three accent roles, filled, so the badge stands out of its tile. */
@Composable
internal fun folderColors(key: String): Pair<Color, Color> {
  val c = MaterialTheme.colorScheme
  return when (Math.floorMod(key.hashCode() / 7, 3)) {
    0 -> Pair(c.primary, c.onPrimary)
    1 -> Pair(c.secondary, c.onSecondary)
    else -> Pair(c.tertiary, c.onTertiary)
  }
}

/** A folder's badge: its icon (or the user's emoji) in its shape; the shape turns while a drag hovers it. */
@Composable
internal fun FolderBadge(key: String, emoji: String?, icon: Int, size: Dp, hovered: Boolean = false) {
  val (container, content) = folderColors(key)
  val motion = MaterialTheme.motionScheme
  val rotation by animateFloatAsState(if (hovered) 30f else 0f, motion.defaultSpatialSpec(), label = "badgeTurn")
  val density = LocalDensity.current
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }.clip(folderShape(key)).background(container))
    if (emoji != null) {
      // Sized in dp, not sp: the emoji stays inside its badge at any font size
      Text(emoji, fontSize = with(density) { (size * 0.46f).toSp() })
    } else {
      Icon(tgxIcon(icon), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
  }
}

/**
 * A folder tile: badge, a ⋮ for its options, its name on up to two lines, how many are in it. It is a drop target
 * ([dropKey], as [targetKey]): while a drag hovers it, it grows and its corners round further.
 */
@Composable
internal fun FolderTile(
  state: XDriveState, key: String, targetKey: String, title: String, subtitle: String?, emoji: String?, icon: Int, dropKey: String?,
  onClick: () -> Unit, onMenu: (() -> Unit)?, modifier: Modifier = Modifier, menu: @Composable () -> Unit = {}
) {
  val motion = MaterialTheme.motionScheme
  val cs = MaterialTheme.colorScheme
  val hovered = dropKey != null && state.drag.hoveredTarget == targetKey
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val corner by animateDpAsState(if (hovered) 40.dp else if (pressed) 16.dp else 28.dp, motion.fastSpatialSpec(), label = "tileCorner")
  val scale by animateFloatAsState(if (hovered) 1.06f else if (pressed) 0.97f else 1f, motion.fastSpatialSpec(), label = "tileScale")
  val color by animateColorAsState(if (hovered) cs.primaryContainer else cs.surfaceBright, motion.defaultEffectsSpec(), label = "tileColor")
  Box(
    modifier.then(if (dropKey != null) Modifier.xdriveDropTarget(state, targetKey, dropKey) else Modifier)
      .xdriveBounds(state, targetKey).scale(scale)
  ) {
    Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(corner.coerceAtLeast(0.dp))).background(color)
        .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick, onLongClick = onMenu)
        .semantics(mergeDescendants = true) { role = Role.Button }
        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)
    ) {
      Row(verticalAlignment = Alignment.Top) {
        FolderBadge(key, emoji, icon, 48.dp, hovered)
        Spacer(Modifier.weight(1f))
        if (onMenu != null) {
          Box {
            IconButton(onClick = onMenu) {
              Icon(tgxIcon(R.drawable.baseline_more_vert_24), contentDescription = str(R.string.XgramXDriveFolderMenu, title), tint = cs.onSurfaceVariant)
            }
            menu()
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp))
      Text(subtitle ?: " ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp))
    }
  }
}

/**
 * The dragged items under the finger: the first one's picture, tilted, with how many there are. Placed in physical
 * coordinates (from the left, in right-to-left too), as the drag's position is.
 */
@Composable
internal fun BoxScope.DragGhost(state: XDriveState) {
  val drag = state.drag
  val items = drag.items
  if (items.isEmpty()) return
  val motion = MaterialTheme.motionScheme
  val lift by animateFloatAsState(if (drag.hovered != null) 0.8f else 1f, motion.fastSpatialSpec(), label = "ghostLift")
  val half = with(LocalDensity.current) { 36.dp.toPx() }
  Box(
    Modifier
      .align(AbsoluteAlignment.TopLeft)
      .absoluteOffset { IntOffset((drag.position.x - half).toInt(), (drag.position.y - half * 1.6f).toInt()) }
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
  val targetKey = "dock:$key"
  val hovered = state.drag.hoveredTarget == targetKey
  val scale by animateFloatAsState(if (hovered) 1.1f else 1f, motion.fastSpatialSpec(), label = "chipScale")
  val color by animateColorAsState(if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, motion.fastEffectsSpec(), label = "chipColor")
  val content by animateColorAsState(if (hovered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, motion.fastEffectsSpec(), label = "chipContent")
  Row(
    Modifier.xdriveDropTarget(state, targetKey, key, dock = true).scale(scale).heightIn(min = 44.dp).clip(CircleShape).background(color).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (emoji != null) {
      Text(emoji, fontSize = 18.sp)
    } else {
      Icon(tgxIcon(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
  }
}

/**
 * Rises while a drag runs: a new folder, out of the folder, then every folder as a chip to drop on, in up to two rows
 * that scroll sideways (by themselves while the finger rests near an edge).
 */
@Composable
internal fun DropDock(state: XDriveState, bottomInset: Dp) {
  val folders = state.folders()
  val inFolder = state.drag.items.any { state.index.folderOf(it.id) != null }
  val grid = rememberLazyGridState()
  val density = LocalDensity.current
  val keys = remember(folders, inFolder) {
    val list = ArrayList<Pair<String, XDriveIndex.Folder?>>()
    list.add(DROP_NEW to null)
    if (inFolder) list.add(DROP_OUT to null)
    folders.forEach { list.add(it.id to it) }
    list
  }
  val rows = if (keys.size > 5) 2 else 1
  // The finger's position is physical; right-to-left, the chips' start is on the right, so a step's sign turns
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  LaunchedEffect(state.drag.moved, rtl) {
    val edge = with(density) { 56.dp.toPx() }
    val toward = if (rtl) -1f else 1f
    while (state.drag.moved) {
      val area = state.bounds[BOUNDS_DOCK]
      val p = state.drag.position
      if (area != null && area.contains(p)) {
        val step = when {
          p.x < area.left + edge -> -18f * toward
          p.x > area.right - edge -> 18f * toward
          else -> 0f
        }
        if (step != 0f && grid.scrollBy(step) != 0f) {
          delay(16)
          // The chips moved under the resting finger: what it is over now
          state.drag.moveBy(Offset.Zero, 0f, state.bounds[BOUNDS_DOCK], state.tilesTakeDrops)
          continue
        }
      }
      delay(16)
    }
  }
  Surface(
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation = 12.dp,
    modifier = Modifier.fillMaxWidth().xdriveBounds(state, BOUNDS_DOCK)
  ) {
    Column(Modifier.padding(top = 18.dp, bottom = 18.dp + bottomInset)) {
      Text(str(R.string.XgramXDriveDropHere), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
      LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        state = grid,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(if (rows == 2) 104.dp else 48.dp)
      ) {
        items(keys.size, key = { keys[it].first }) { i ->
          val (key, folder) = keys[i]
          when {
            key == DROP_NEW -> DropChip(state, DROP_NEW, str(R.string.XgramXDriveNewFolder), null, R.drawable.baseline_create_new_folder_24)
            key == DROP_OUT -> DropChip(state, DROP_OUT, str(R.string.XgramXDriveOutOfFolder), null, R.drawable.baseline_archive_24)
            folder != null -> DropChip(state, key, folder.name, folder.emoji, XDriveIcons.drawableOf(folder.icon))
          }
        }
      }
    }
  }
}

/** A section's title on XDrive's pages, a line under it, an action at its end. */
@Composable
internal fun SectionTitle(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (action != null && onAction != null) {
      TextButton(onClick = onAction) { Text(action) }
    }
  }
}

@Composable
internal fun EmptyNote(text: String, modifier: Modifier = Modifier, title: String? = null, actions: @Composable () -> Unit = {}) {
  Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(R.drawable.baseline_cloud_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(16.dp))
    if (title != null) {
      Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
      Spacer(Modifier.height(4.dp))
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    actions()
  }
}

/** A rounded rectangle at [rect] inside the layer (the reveal's clip). */
private class RectShape(private val rect: Rect, private val radius: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(RoundRect(rect, CornerRadius(radius, radius)))
}

/**
 * A screen over the tabs that grows out of where it was opened from (a folder tile, the search bar: Expressive's
 * container transform) and shrinks back into it; while a predictive back gesture runs over it, it follows the finger
 * (smaller, rounder, toward the gesture's side). Nothing under it can be touched while it shows. [onHidden] runs once
 * it has shrunk away.
 */
@Composable
internal fun RevealLayer(state: XDriveState, id: String, visible: Boolean, originKey: () -> String?, onHidden: () -> Unit = {}, content: @Composable () -> Unit) {
  val motion = MaterialTheme.motionScheme
  val progress = remember { Animatable(0f) }
  var shown by remember { mutableStateOf(false) }
  var origin by remember { mutableStateOf<Rect?>(null) }
  LaunchedEffect(visible) {
    if (visible) {
      origin = originKey()?.let { state.bounds[it] }
      shown = true
      progress.animateTo(1f, motion.defaultSpatialSpec())
    } else if (shown) {
      // Back into where it came from, wherever that is now
      originKey()?.let { state.bounds[it] }?.let { origin = it }
      progress.animateTo(0f, motion.fastSpatialSpec())
      shown = false
      onHidden()
      if (state.backLayer == id) {
        state.backProgress = 0f
        state.backLayer = null
      }
    }
  }
  if (!shown) return
  val density = LocalDensity.current
  val cs = MaterialTheme.colorScheme
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val full = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
    // From where it was opened, when that is on screen; else from the middle
    val start = origin?.takeIf { it.overlaps(full) } ?: Rect(full.center, 1f)
    val radiusMax = with(density) { 28.dp.toPx() }
    val shift = with(density) { 12.dp.toPx() }
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          val p = progress.value
          val q = p.coerceIn(0f, 1f)
          val back = if (state.backLayer == id) state.backProgress else 0f
          val rect = Rect(
            start.left + (full.left - start.left) * p, start.top + (full.top - start.top) * p,
            start.right + (full.right - start.right) * p, start.bottom + (full.bottom - start.bottom) * p
          )
          clip = true
          shape = RectShape(rect, radiusMax * (1f - q) + radiusMax * back)
          // The predictive back gesture: the screen shrinks toward the gesture's side
          val s = 1f - 0.1f * back
          scaleX = s
          scaleY = s
          translationX = (if (state.backFromRight) -1f else 1f) * shift * back
        }
    ) {
      // A Surface keeps touches from reaching the tabs underneath
      Surface(color = cs.surfaceContainer, contentColor = cs.onSurface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((progress.value - 0.15f) / 0.6f).coerceIn(0f, 1f) }) {
          content()
        }
      }
    }
  }
}

/** The folder icon picker: Material icons (named for TalkBack) in shapes, the chosen one filled; or an emoji instead. */
@Composable
internal fun IconSheet(state: XDriveState, folder: XDriveIndex.Folder) {
  val c = MaterialTheme.colorScheme
  val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val close: (() -> Unit) -> Unit = { after ->
    scope.launch { sheet.hide() }.invokeOnCompletion {
      state.iconPicker = null
      after()
    }
  }
  ModalBottomSheet(onDismissRequest = { state.iconPicker = null }, sheetState = sheet) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
      Text(str(R.string.XgramXDriveFolderIcon), style = MaterialTheme.typography.titleLarge)
      Text(folder.name, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
      val current = if (folder.emoji == null) folder.icon ?: "folder" else null
      LazyVerticalGrid(
        columns = GridCells.Adaptive(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
      ) {
        items(XDriveIcons.ALL.size, key = { XDriveIcons.ALL[it].first }) { i ->
          val (key, res) = XDriveIcons.ALL[i]
          val chosen = key == current
          val label = XDriveIcons.LABELS[key]?.let { str(it) } ?: key
          Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Box(
              Modifier.size(56.dp).clip(if (chosen) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(18.dp))
                .background(if (chosen) c.primary else c.surfaceContainerHigh)
                .selectable(selected = chosen, role = Role.RadioButton) {
                  close { state.index.setLook(folder.id, if (key == "folder") null else key, null) }
                }
                .semantics { contentDescription = label },
              contentAlignment = Alignment.Center
            ) {
              Icon(tgxIcon(res), contentDescription = null, tint = if (chosen) c.onPrimary else c.onSurfaceVariant)
            }
          }
        }
      }
      TextButton(onClick = { close { askEmoji(state, folder) } }, modifier = Modifier.padding(top = 12.dp)) {
        Text(str(R.string.XgramXDriveUseEmoji))
      }
    }
  }
}

/** A list's scroll position key, per view and folder. */
internal fun feedKey(prefix: String, view: XDriveView, scope: XDriveScope): String = prefix + ":" + view.name + ":" + XDriveState.scopeKey(scope)

/** Clickable without a ripple (scrims). */
internal fun Modifier.plainClick(onClick: () -> Unit): Modifier = this.clickable(interactionSource = null, indication = null, onClick = onClick)
