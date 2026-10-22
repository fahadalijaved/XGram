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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramBackProgress
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.compose.ui.xgramNoSwipeBack
import com.fahadali.xgram.xdrive.XDriveActions
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveScope
import com.fahadali.xgram.xdrive.XDriveView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R

/**
 * XDrive: Saved Messages as a drive, in four tabs (Home, Folders, Media, Files) under a search bar. Home leads to
 * Organize with AI, the kinds, what was saved lately, the folders and suggested ones; Folders is the sorting desk
 * (folder tiles over what is in none); Media and Files list by kind. Anything can be long-pressed and dragged onto a
 * folder (its tile, or the dock that rises while dragging); a selection gets the floating toolbar. A folder, the search
 * and Organize open over the tabs, each growing out of what opened it; the tabs stay as they were underneath.
 */
@Composable
fun XDrivePage() {
  val host = LocalXgramHost.current
  val scope = rememberCoroutineScope()
  val state = remember(host.tdlib) { XDriveState(host.tdlib, host, scope) }
  DisposableEffect(state) {
    val listener = XDriveIndex.Listener { state.indexChanged() }
    state.index.addListener(listener)
    state.index.load { state.indexChanged() }
    onDispose {
      state.index.removeListener(listener)
      state.dispose()
    }
  }
  val pager = rememberPagerState { XDriveTab.entries.size }
  LaunchedEffect(pager) {
    snapshotFlow { pager.settledPage }.collect {
      state.tab = XDriveTab.entries[it]
      state.selected.clear() // a selection belongs to the tab it was made on
      state.fabShown = true // hidden by a scroll on the tab before (this one may not scroll at all)
    }
  }
  val motion = MaterialTheme.motionScheme
  val openTab: (XDriveTab) -> Unit = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal, animationSpec = motion.defaultSpatialSpec()) } }

  // Back: the menu, Organize, the selection, the search, the folder; then Home; the gesture's progress plays on the
  // top state, and Telegram X's swipe-back stays off while one is open
  val backAction = state.backAction(pager.currentPage == 0) { openTab(XDriveTab.HOME) }
  val currentBack by rememberUpdatedState(backAction)
  val hasBack = backAction != null
  DisposableEffect(hasBack) {
    host.setBackConsumer(if (hasBack) {
      {
        currentBack?.invoke()
        if (state.backLayer == null) {
          // No layer closes (a selection, the menu): nothing to shrink away
          state.backProgress = 0f
        } else {
          // The layer resets it as it finishes closing; in case it never does (a new gesture stops this, see onStart)
          state.backJob?.cancel()
          state.backJob = scope.launch {
            delay(800)
            state.backProgress = 0f
            state.backLayer = null
          }
        }
      }
    } else null)
    onDispose { host.setBackConsumer(null) }
  }
  val inPageState = state.fabOpen || state.organizing || state.selecting || state.searching || state.folder != null
  val progressListener = remember(state) {
    object : XgramBackProgress {
      override fun onStart(fromRightEdge: Boolean) {
        // What the last gesture left running (its cancel animation, its reset) would take this one's layer away
        state.backJob?.cancel()
        state.backJob = null
        state.backFromRight = fromRightEdge
        // The layer this gesture closes, fixed for its whole length
        state.backLayer = state.backLayerNow()
        state.backProgress = 0f
      }

      override fun onProgress(progress: Float) {
        // Eased like the system's: quick at first, slowing toward the end
        state.backProgress = 1f - (1f - progress) * (1f - progress)
      }

      override fun onCancel() {
        state.backJob?.cancel()
        state.backJob = scope.launch {
          animate(state.backProgress, 0f) { value, _ -> state.backProgress = value }
          state.backLayer = null
        }
      }
    }
  }
  DisposableEffect(inPageState) {
    if (inPageState) {
      host.noSwipeBack(state, NO_SWIPE_ANYWHERE)
      host.setBackProgress(progressListener)
    }
    onDispose {
      host.noSwipeBack(state, null)
      host.setBackProgress(null)
    }
  }

  val density = LocalDensity.current
  val bottomInset = with(density) { host.bottomInset.toDp() }
  val overlay = state.organizing || state.searching || state.folder != null
  val showNavigation = !state.selecting && !state.drag.moved
  // The floating button hides while a list scrolls down
  val fabScroll = remember(state) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -6f) state.fabShown = false else if (available.y > 6f) state.fabShown = true
        return Offset.Zero
      }
    }
  }
  val cs = MaterialTheme.colorScheme
  var lastFolder by remember { mutableStateOf<XDriveScope?>(null) }
  state.folder?.let { lastFolder = it }

  Surface(color = cs.surfaceContainer, contentColor = cs.onSurface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().nestedScroll(fabScroll).onPlaced { state.root = it }) {
      Column(Modifier.fillMaxSize().then(if (overlay) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
        AnimatedContent(
          targetState = state.selecting && !overlay,
          transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
          label = "xdriveTopBar"
        ) { selecting ->
          if (selecting) SelectionBar(state) { visibleItems(state) } else SearchAppBar(state)
        }
        HorizontalPager(
          state = pager,
          modifier = Modifier.weight(1f).then(if (pager.currentPage > 0) Modifier.xgramNoSwipeBack() else Modifier),
          userScrollEnabled = !state.drag.active && !state.selecting,
          beyondViewportPageCount = 1,
          key = { it }
        ) { page ->
          when (XDriveTab.entries[page]) {
            XDriveTab.HOME -> HomeTab(state, openTab)
            XDriveTab.FOLDERS -> FoldersTab(state)
            XDriveTab.MEDIA -> MediaTab(state)
            XDriveTab.FILES -> FilesTab(state)
          }
        }
        AnimatedVisibility(
          visible = showNavigation,
          enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
          exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        ) {
          ShortNavigationBar(windowInsets = WindowInsets(bottom = host.bottomInset)) {
            XDriveTab.entries.forEach { tab ->
              ShortNavigationBarItem(
                selected = pager.currentPage == tab.ordinal,
                onClick = { openTab(tab) },
                icon = { Icon(tgxIcon(tab.icon), contentDescription = null) },
                label = { Text(str(tab.title)) }
              )
            }
          }
        }
        // Where the navigation bar was, the system inset stays free (growing as the bar shrinks, no jump)
        val navSpace by animateDpAsState(if (showNavigation) 0.dp else bottomInset, motion.defaultSpatialSpec(), label = "navSpace")
        Spacer(Modifier.height(navSpace.coerceAtLeast(0.dp)))
      }

      // Over the tabs: a folder, the search, Organize; each grows out of what opened it
      RevealLayer(state, LAYER_FOLDER, visible = state.folder != null, originKey = { state.folderOrigin }) {
        lastFolder?.let { FolderScreen(state, it, openTab) }
      }
      RevealLayer(state, LAYER_SEARCH, visible = state.searching, originKey = { if (state.folder == null) BOUNDS_SEARCH else null }) {
        SearchScreen(state)
      }
      // A run that ended starts over only once its layer is gone, so the layer doesn't change while it shrinks away
      RevealLayer(state, LAYER_ORGANIZE, visible = state.organizing, originKey = { state.organizeOrigin }, onHidden = { state.organizeHidden() }) {
        XDriveOrganizeScreen(
          state,
          onClose = { state.closeOrganize() },
          onSeeFolders = {
            state.closeOrganize()
            state.closeFolder()
            openTab(XDriveTab.FOLDERS)
          }
        )
      }

      // New: a note, photos and videos, files, a folder (into the folder shown); a scrim under it while open
      val navHeight = if (state.folder == null && !state.searching && showNavigation) 64.dp + bottomInset else bottomInset
      val fabBottom by animateDpAsState(navHeight, motion.defaultSpatialSpec(), label = "fabBottom")
      val fabVisible = !state.selecting && !state.drag.active && !state.searching && !state.organizing
      AnimatedVisibility(state.fabOpen, enter = fadeIn(motion.defaultEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
        Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = .32f)).plainClick { state.fabOpen = false })
      }
      NewMenu(
        state,
        Modifier.align(Alignment.BottomEnd).padding(bottom = fabBottom.coerceAtLeast(0.dp))
          .animateFloatingActionButton(visible = fabVisible && (state.fabShown || state.fabOpen), alignment = Alignment.BottomEnd)
      )

      // The selection's toolbar
      AnimatedVisibility(
        visible = state.selecting && !state.drag.moved,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + bottomInset),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it * 2 } + fadeIn(),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it * 2 } + fadeOut()
      ) {
        SelectionToolbar(state)
      }

      // Above whatever floats at the bottom: the floating button, or the selection's toolbar
      val snackBottom = when {
        state.selecting -> bottomInset + 16.dp + 64.dp + 8.dp
        fabVisible && state.fabShown -> fabBottom + 16.dp + 56.dp + 12.dp
        else -> fabBottom + 16.dp
      }
      val snackPadding by animateDpAsState(snackBottom, motion.defaultSpatialSpec(), label = "snackBottom")
      SnackbarHost(state.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = snackPadding.coerceAtLeast(0.dp)))

      // While dragging: the folders to drop on, and the items under the finger
      AnimatedVisibility(
        visible = state.drag.moved,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut()
      ) {
        DropDock(state, bottomInset)
      }
      if (state.drag.moved) {
        DragGhost(state)
      }

      val picking = state.iconPicker
      if (picking != null) {
        IconSheet(state, picking)
      }
    }
  }
}

/** What is on screen now (Select all): the open folder's items, or the current tab's. */
private fun visibleItems(state: XDriveState): List<TdApi.Message> {
  val folder = state.folder
  return when {
    folder != null -> state.feed(XDriveView.ALL, folder).items
    state.tab == XDriveTab.HOME -> state.feed(XDriveView.ALL, XDriveScope.Everything).items.take(24)
    state.tab == XDriveTab.FOLDERS -> state.feed(XDriveView.ALL, XDriveScope.Unsorted).items
    state.tab == XDriveTab.MEDIA -> state.feed(state.mediaView, XDriveScope.Everything).items
    else -> state.feed(state.filesView, XDriveScope.Everything).items
  }
}

/** The tabs' top: back, the search bar (search grows out of it), and the menu. */
@Composable
private fun SearchAppBar(state: XDriveState) {
  val host = state.host
  val cs = MaterialTheme.colorScheme
  var menu by remember { mutableStateOf(false) }
  Row(
    Modifier.fillMaxWidth().padding(top = with(LocalDensity.current) { host.topInset.toDp() }).heightIn(min = 72.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = { host.back() }) { Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramXDriveBack)) }
    // The search bar: a pill with the search icon and what it searches
    Row(
      Modifier.weight(1f).heightIn(min = 56.dp).xdriveBounds(state, BOUNDS_SEARCH).padding(vertical = 4.dp)
        .background(cs.surfaceContainerHigh, CircleShape)
        .clickable(role = Role.Button, onClickLabel = str(R.string.XgramXDriveSearch)) { state.openSearch() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = null, tint = cs.onSurfaceVariant)
      Text(
        str(R.string.XgramXDriveSearch), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)
      )
    }
    Box {
      IconButton(onClick = { menu = true }) { Icon(tgxIcon(R.drawable.baseline_more_vert_24), contentDescription = str(R.string.XgramXDriveMore)) }
      MainMenu(state, menu) { menu = false }
    }
  }
}

/** XDrive's menu, in groups: organize and make; the chat; about. */
@Composable
private fun MainMenu(state: XDriveState, expanded: Boolean, onDismiss: () -> Unit) {
  val host = state.host
  val opensChat = XgramSettings.instance().xdriveOpensChat()
  DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 3)) {
      DropdownMenuItem(
        onClick = { onDismiss(); state.openOrganize(null) },
        text = { Text(str(R.string.XgramXDriveOrganize)) },
        shape = MenuDefaults.itemShape(0, 2).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), null) }
      )
      DropdownMenuItem(
        onClick = { onDismiss(); XDriveActions.newFolder(host) },
        text = { Text(str(R.string.XgramXDriveNewFolder)) },
        shape = MenuDefaults.itemShape(1, 2).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), null) }
      )
    }
    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 3)) {
      DropdownMenuItem(
        onClick = { onDismiss(); XDriveActions.showInChat(host.controller, 0) },
        text = { Text(str(R.string.XgramXDriveOpenChat)) },
        shape = MenuDefaults.itemShape(0, 2).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_chat_bubble_24), null) }
      )
      // Saved Messages as the classic chat first (XDrive then is the cloud button of its header)
      CheckableDropdownMenuItem(
        opensChat,
        { checked ->
          onDismiss()
          XgramSettings.instance().setXdriveOpensChat(checked)
          if (checked) {
            // The chat takes XDrive's place: back from it doesn't come here (it says so in a toast: this screen goes)
            com.fahadali.xgram.xdrive.XDrive.openChatInstead(host.controller)
          }
        },
        { Text(str(R.string.XgramXDriveOpensChat)) },
        MenuDefaults.itemShape(1, 2),
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_forum_24), null) }
      )
    }
    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(2, 3)) {
      DropdownMenuItem(
        onClick = { onDismiss(); confirmAbout(state) },
        text = { Text(str(R.string.XgramXDriveAbout)) },
        shape = MenuDefaults.itemShape(0, 1).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_info_24), null) }
      )
    }
  }
}

/** The top while selecting: clear, how many (announced as it changes), select all. */
@Composable
internal fun SelectionBar(state: XDriveState, items: () -> List<TdApi.Message>) {
  Row(
    Modifier.fillMaxWidth().padding(top = with(LocalDensity.current) { state.host.topInset.toDp() }).heightIn(min = 72.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = { state.selected.clear() }) { Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.XgramXDriveClearSelection)) }
    Text(
      str(R.string.XgramXDriveSelected, state.selected.size), style = MaterialTheme.typography.titleLarge, maxLines = 1,
      modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
    )
    IconButton(onClick = { state.selectAll(items()) }) { Icon(tgxIcon(R.drawable.baseline_done_all_24), contentDescription = str(R.string.XgramXDriveSelectAll)) }
  }
}

/** Expressive's FAB menu: the + turns into ×, the choices rise above it. */
@Composable
private fun NewMenu(state: XDriveState, modifier: Modifier) {
  val host = state.host
  val folderId = (state.folder as? XDriveScope.InFolder)?.folderId
  FloatingActionButtonMenu(
    expanded = state.fabOpen,
    modifier = modifier,
    button = {
      ToggleFloatingActionButton(checked = state.fabOpen, onCheckedChange = { state.fabOpen = it }) {
        val progress = checkedProgress
        Icon(
          tgxIcon(if (progress > 0.5f) R.drawable.baseline_close_24 else R.drawable.baseline_add_24),
          contentDescription = str(if (state.fabOpen) R.string.XgramXDriveCloseMenu else R.string.XgramXDriveNew),
          modifier = Modifier.animateIcon({ checkedProgress })
        )
      }
    }
  ) {
    FloatingActionButtonMenuItem(
      onClick = { state.fabOpen = false; XDriveActions.newNote(host, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_format_text_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveNewNote)) }
    )
    FloatingActionButtonMenuItem(
      onClick = { state.fabOpen = false; XDriveActions.upload(host, true, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_image_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveUploadMedia)) }
    )
    FloatingActionButtonMenuItem(
      onClick = { state.fabOpen = false; XDriveActions.upload(host, false, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_insert_drive_file_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveUploadFiles)) }
    )
    if (folderId == null) {
      FloatingActionButtonMenuItem(
        onClick = { state.fabOpen = false; XDriveActions.newFolder(host) },
        icon = { Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = null) },
        text = { Text(str(R.string.XgramXDriveNewFolder)) }
      )
    }
  }
}

/** What a selection can do: into a folder (with Undo), out of it, share, the chat, delete. */
@Composable
private fun SelectionToolbar(state: XDriveState) {
  val host = state.host
  val controller = host.controller
  val list = state.selected.values.sortedByDescending { it.id }
  val inFolder = list.any { state.index.folderOf(it.id) != null }
  HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
    IconButton(onClick = { state.chooseFolderFor(list) }) {
      Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = str(R.string.XgramXDriveMoveTo))
    }
    if (inFolder) {
      IconButton(onClick = { state.move(list, null) }) {
        Icon(tgxIcon(R.drawable.baseline_archive_24), contentDescription = str(R.string.XgramXDriveRemoveFromFolder))
      }
    }
    IconButton(onClick = { XDriveActions.share(controller, list); state.selected.clear() }) {
      Icon(tgxIcon(R.drawable.baseline_forward_24), contentDescription = str(R.string.Share))
    }
    if (list.size == 1) {
      IconButton(onClick = { val id = list[0].id; state.selected.clear(); XDriveActions.showInChat(controller, id) }) {
        Icon(tgxIcon(R.drawable.baseline_chat_bubble_24), contentDescription = str(R.string.XgramXDriveShowInChat))
      }
    }
    IconButton(onClick = { XDriveActions.delete(host, list) { state.selected.clear() } }) {
      Icon(tgxIcon(R.drawable.baseline_delete_24), contentDescription = str(R.string.Delete))
    }
  }
}
