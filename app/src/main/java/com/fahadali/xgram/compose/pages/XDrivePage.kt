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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fahadali.xgram.compose.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.compose.ui.xgramNoSwipeBack
import com.fahadali.xgram.xdrive.XDriveActions
import com.fahadali.xgram.xdrive.XDriveIcons
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveScope
import kotlinx.coroutines.launch
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

private enum class XDriveScreen { TABS, FOLDER, SEARCH }

/**
 * XDrive: Saved Messages as a drive, in four tabs (Home, Folders, Media, Files). Home leads to Organize with AI, the
 * kinds, what was saved lately, the folders and suggested ones; Folders is the sorting desk (folder tiles over what is
 * in none); Media and Files list by kind. Anything can be long-pressed and dragged onto a folder (tiles, or the dock
 * that rises while dragging); a selection gets the floating toolbar. Search and a folder open over the tabs.
 */
@Composable
fun XDrivePage() {
  val host = LocalXgramHost.current
  val scope = rememberCoroutineScope()
  val state = remember(host.tdlib) { XDriveState(host.tdlib, host, scope) }
  DisposableEffect(state) {
    val listener = XDriveIndex.Listener { state.indexVersion++ }
    state.index.addListener(listener)
    state.index.load { state.indexVersion++ }
    onDispose { state.index.removeListener(listener) }
  }
  val pager = rememberPagerState { XDriveTab.entries.size }
  LaunchedEffect(pager) {
    snapshotFlow { pager.currentPage }.collect { state.tab = XDriveTab.entries[it] }
  }
  val motion = MaterialTheme.motionScheme
  val openTab: (XDriveTab) -> Unit = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal, animationSpec = motion.defaultSpatialSpec()) } }

  // Back: Organize, then the search, the selection, the folder, then Home
  val backAction: (() -> Unit)? = when {
    state.organizing -> { { state.organizing = false } }
    state.searching -> { { state.searching = false; state.query = "" } }
    state.selecting -> { { state.selected.clear() } }
    state.folder != null -> { { state.folder = null } }
    pager.currentPage != 0 -> { { openTab(XDriveTab.HOME) } }
    else -> null
  }
  DisposableEffect(backAction) {
    host.setBackConsumer(backAction)
    onDispose { host.setBackConsumer(null) }
  }

  val bottomInset = with(LocalDensity.current) { host.bottomInset.toDp() }
  val screen = when {
    state.searching -> XDriveScreen.SEARCH
    state.folder != null -> XDriveScreen.FOLDER
    else -> XDriveScreen.TABS
  }
  val showNavigation = screen == XDriveScreen.TABS && !state.selecting && !state.drag.moved
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).onPlaced { state.root = it }) {
    Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
      TopBar(state, screen, scrollBehavior, backAction)
      Box(Modifier.weight(1f)) {
        AnimatedContent(
          targetState = Pair(screen, state.folder),
          transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
              (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.02f))
          },
          label = "xdriveScreen"
        ) { (current, folder) ->
          when (current) {
            XDriveScreen.TABS -> HorizontalPager(
              state = pager,
              modifier = Modifier.fillMaxSize().then(if (pager.currentPage > 0) Modifier.xgramNoSwipeBack() else Modifier),
              userScrollEnabled = !state.drag.active,
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
            XDriveScreen.FOLDER -> if (folder != null) FolderScreen(state, folder)
            XDriveScreen.SEARCH -> SearchScreen(state)
          }
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
    }

    val above = if (showNavigation) 80.dp + bottomInset else 16.dp + bottomInset

    // New: a note, photos and videos, files, a folder (into the folder shown)
    AnimatedVisibility(
      visible = !state.selecting && !state.drag.active && screen != XDriveScreen.SEARCH,
      modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = above - 16.dp),
      enter = scaleIn(motion.defaultSpatialSpec()) + fadeIn(),
      exit = scaleOut(motion.fastSpatialSpec()) + fadeOut()
    ) {
      NewMenu(state)
    }

    // The selection's toolbar
    AnimatedVisibility(
      visible = state.selecting && !state.drag.moved,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + bottomInset),
      enter = slideInVertically(motion.defaultSpatialSpec()) { it * 2 } + fadeIn(),
      exit = slideOutVertically(motion.fastSpatialSpec()) { it * 2 } + fadeOut()
    ) {
      SelectionToolbar(state)
    }

    SnackbarHost(state.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = above + if (state.selecting) 72.dp else 0.dp))

    // While dragging: the folders to drop on, and the items under the finger
    AnimatedVisibility(
      visible = state.drag.moved,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(),
      exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut()
    ) {
      DropDock(state)
    }
    if (state.drag.moved) {
      DragGhost(state)
    }

    val picking = state.iconPicker
    if (picking != null) {
      IconSheet(state, picking)
    }

    AnimatedVisibility(
      visible = state.organizing,
      enter = slideInVertically(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec()),
      exit = slideOutVertically(motion.fastSpatialSpec()) { it / 3 } + fadeOut(motion.fastEffectsSpec())
    ) {
      XDriveOrganizeScreen(
        state,
        onClose = { state.organizing = false },
        onSeeFolders = {
          state.organizing = false
          state.folder = null
          openTab(XDriveTab.FOLDERS)
        }
      )
    }
  }
}

@Composable
private fun TopBar(state: XDriveState, screen: XDriveScreen, scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior, backAction: (() -> Unit)?) {
  val host = state.host
  val motion = MaterialTheme.motionScheme
  AnimatedContent(
    targetState = screen == XDriveScreen.SEARCH,
    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
    label = "xdriveTopBar"
  ) { searching ->
    if (searching) {
      SearchBar(state)
    } else {
      val folder = (state.folder as? XDriveScope.InFolder)?.let { state.index.findFolder(it.folderId) }
      val counts = state.counts()
      val title = when {
        state.selecting -> str(R.string.XgramXDriveSelected, state.selected.size)
        folder != null -> (folder.emoji?.let { "$it " } ?: "") + folder.name
        state.folder == XDriveScope.Unsorted -> str(R.string.XgramXDriveUnsorted)
        else -> str(R.string.XgramXDrive)
      }
      val subtitle = when {
        state.selecting -> null
        folder != null -> Lang.plural(R.string.xMessages, (counts[folder.id] ?: 0).toLong())
        state.folder == XDriveScope.Unsorted -> str(R.string.XgramXDriveUnsortedInfo)
        else -> str(R.string.XgramXDriveSummary, Lang.plural(R.string.xFolders, state.folders().size.toLong()), counts.values.sum())
      }
      var menu by remember { mutableStateOf(false) }
      LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = if (subtitle != null) { { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) } } else null,
        navigationIcon = {
          IconButton(onClick = { backAction?.invoke() ?: host.back() }) {
            Icon(tgxIcon(if (state.selecting) R.drawable.baseline_close_24 else R.drawable.baseline_arrow_back_24), contentDescription = null)
          }
        },
        actions = {
          if (!state.selecting) {
            IconButton(onClick = { state.searching = true }) {
              Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = str(R.string.XgramXDriveSearch))
            }
          }
          if (!state.selecting) Box {
            IconButton(onClick = { menu = true }) {
              Icon(tgxIcon(R.drawable.baseline_more_vert_24), contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
              if (folder != null) {
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveRename)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_edit_24), null) }, onClick = { menu = false; XDriveActions.renameFolder(host, folder) })
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveFolderIcon)) }, leadingIcon = { Icon(tgxIcon(XDriveIcons.drawableOf(folder.icon)), null) }, onClick = { menu = false; state.iconPicker = folder })
                DropdownMenuItem(
                  text = { Text(str(R.string.XgramXDriveDeleteFolder), color = MaterialTheme.colorScheme.error) },
                  leadingIcon = { Icon(tgxIcon(R.drawable.baseline_delete_24), null, tint = MaterialTheme.colorScheme.error) },
                  onClick = { menu = false; XDriveActions.deleteFolder(host, folder) { state.folder = null } }
                )
              } else {
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveOrganize)) }, leadingIcon = { Icon(tgxIcon(R.drawable.xgram_auto_awesome_24), null) }, onClick = { menu = false; state.organizing = true })
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveNewFolder)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), null) }, onClick = { menu = false; XDriveActions.newFolder(host) })
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveOpenChat)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_chat_bubble_24), null) }, onClick = { menu = false; XDriveActions.showInChat(host.controller, 0) })
                DropdownMenuItem(text = { Text(str(R.string.XgramXDriveAbout)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_info_24), null) }, onClick = { menu = false; confirmAbout(state) })
              }
            }
          }
        },
        windowInsets = WindowInsets(top = host.topInset),
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        scrollBehavior = scrollBehavior
      )
    }
  }
}

/** The search's top bar: a pill field, focused as it opens. */
@Composable
private fun SearchBar(state: XDriveState) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  Row(
    Modifier.fillMaxWidth().padding(top = with(LocalDensity.current) { state.host.topInset.toDp() }).height(72.dp).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = { state.searching = false; state.query = "" }) {
      Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = null)
    }
    TextField(
      value = state.query,
      onValueChange = { state.query = it },
      placeholder = { Text(str(R.string.XgramXDriveSearch)) },
      singleLine = true,
      shape = CircleShape,
      trailingIcon = if (state.query.isNotEmpty()) {
        { IconButton(onClick = { state.query = "" }) { Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.Clear)) } }
      } else null,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
      ),
      modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus)
    )
  }
}

/** Expressive's FAB menu: the + turns into ×, the choices rise above it. */
@Composable
private fun NewMenu(state: XDriveState) {
  val host = state.host
  var open by remember { mutableStateOf(false) }
  val folderId = (state.folder as? XDriveScope.InFolder)?.folderId
  FloatingActionButtonMenu(
    expanded = open,
    button = {
      ToggleFloatingActionButton(checked = open, onCheckedChange = { open = it }) {
        val progress = checkedProgress
        Icon(
          tgxIcon(if (progress > 0.5f) R.drawable.baseline_close_24 else R.drawable.baseline_add_24),
          contentDescription = str(R.string.XgramXDriveNew),
          modifier = Modifier.animateIcon({ checkedProgress })
        )
      }
    }
  ) {
    FloatingActionButtonMenuItem(
      onClick = { open = false; XDriveActions.newNote(host, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_format_text_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveNewNote)) }
    )
    FloatingActionButtonMenuItem(
      onClick = { open = false; XDriveActions.upload(host, true, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_image_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveUploadMedia)) }
    )
    FloatingActionButtonMenuItem(
      onClick = { open = false; XDriveActions.upload(host, false, folderId) },
      icon = { Icon(tgxIcon(R.drawable.baseline_insert_drive_file_24), contentDescription = null) },
      text = { Text(str(R.string.XgramXDriveUploadFiles)) }
    )
    if (folderId == null) {
      FloatingActionButtonMenuItem(
        onClick = { open = false; XDriveActions.newFolder(host) },
        icon = { Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = null) },
        text = { Text(str(R.string.XgramXDriveNewFolder)) }
      )
    }
  }
}

/** What a selection can do: into a folder, out of it, share, the chat, delete. */
@Composable
private fun SelectionToolbar(state: XDriveState) {
  val host = state.host
  val controller = host.controller
  val list = state.selected.values.sortedByDescending { it.id }
  val inFolder = list.any { state.index.folderOf(it.id) != null }
  HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
    IconButton(onClick = { XDriveActions.chooseFolder(host, list.map { it.id }.toLongArray()) { state.selected.clear(); state.indexVersion++ } }) {
      Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = str(R.string.XgramXDriveAddToFolder))
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

/** Rises while a drag runs: every folder as a chip to drop on, a new folder, and out of the folder. */
@Composable
private fun DropDock(state: XDriveState) {
  val bottomInset = with(LocalDensity.current) { state.host.bottomInset.toDp() }
  val folders = state.folders()
  val inFolder = state.drag.items.any { state.index.folderOf(it.id) != null }
  Surface(
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 12.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp + bottomInset)) {
      Text(str(R.string.XgramXDriveDropHere), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (folder in folders) {
          DropChip(state, folder.id, folder.name, folder.emoji, XDriveIcons.drawableOf(folder.icon))
        }
        DropChip(state, DROP_NEW, str(R.string.XgramXDriveNewFolder), null, R.drawable.baseline_create_new_folder_24)
        if (inFolder) {
          DropChip(state, DROP_OUT, str(R.string.XgramXDriveOutOfFolder), null, R.drawable.baseline_archive_24)
        }
      }
    }
  }
}

/** The folder icon picker: Material icons in Expressive shapes, the chosen one filled; or an emoji instead. */
@Composable
private fun IconSheet(state: XDriveState, folder: XDriveIndex.Folder) {
  val c = MaterialTheme.colorScheme
  val bottomInset = with(LocalDensity.current) { state.host.bottomInset.toDp() }
  ModalBottomSheet(onDismissRequest = { state.iconPicker = null }) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp + bottomInset)) {
      Text(str(R.string.XgramXDriveFolderIcon), style = MaterialTheme.typography.titleLarge)
      Text(folder.name, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val current = if (folder.emoji == null) folder.icon ?: "folder" else null
        for ((key, res) in XDriveIcons.ALL) {
          val chosen = key == current
          Box(
            Modifier.size(56.dp).clip(if (chosen) MaterialShapes.Cookie9Sided.toShape() else androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
              .background(if (chosen) c.primary else c.surfaceContainerHigh)
              .clickable {
                state.index.setLook(folder.id, if (key == "folder") null else key, null)
                state.iconPicker = null
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(tgxIcon(res), contentDescription = key, tint = if (chosen) c.onPrimary else c.onSurfaceVariant)
          }
        }
      }
      TextButton(onClick = { state.iconPicker = null; askEmoji(state, folder) }, modifier = Modifier.padding(top = 12.dp)) {
        Text(str(R.string.XgramXDriveUseEmoji))
      }
    }
  }
}
