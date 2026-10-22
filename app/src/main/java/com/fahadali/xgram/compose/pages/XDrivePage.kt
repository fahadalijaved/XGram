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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.xdrive.XDriveActions
import com.fahadali.xgram.xdrive.XDriveFeed
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveKind
import com.fahadali.xgram.xdrive.XDriveScope
import com.fahadali.xgram.xdrive.XDriveThumbs
import com.fahadali.xgram.xdrive.XDriveView
import kotlinx.coroutines.delay
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * XDrive: Saved Messages as a drive. Search across everything; one view per kind (photos and videos as a grid, the
 * rest as rows); folders (and what is in none, Unsorted) as cards; a long press selects (Add to folder, Share,
 * Delete, Show in chat); a tap opens the item itself; New makes a note, uploads files, photos and videos, or a folder.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XDrivePage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val controller = host.controller
  val index = remember(tdlib) { XDriveIndex.of(tdlib) }
  var indexVersion by remember { mutableIntStateOf(0) }
  DisposableEffect(index) {
    val listener = XDriveIndex.Listener { indexVersion++ }
    index.addListener(listener)
    index.load { indexVersion++ }
    onDispose { index.removeListener(listener) }
  }

  var view by remember { mutableStateOf(XDriveView.ALL) }
  var scope by remember { mutableStateOf<XDriveScope>(XDriveScope.Everything) }
  var query by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  LaunchedEffect(query) {
    delay(if (query.isEmpty()) 0 else 350)
    searchQuery = query
  }
  val feed = remember(view, scope, searchQuery) { XDriveFeed(tdlib, view, scope, searchQuery) }
  DisposableEffect(feed) {
    feed.attach()
    onDispose { feed.detach() }
  }
  // A folder's contents change with the index (moved in or out)
  LaunchedEffect(indexVersion) {
    if (scope !is XDriveScope.Everything) feed.reload()
  }

  val selected = remember { mutableStateMapOf<Long, TdApi.Message>() }
  val selecting = selected.isNotEmpty()

  // Back: out of the selection, then the search, then the folder, then the page
  val backAction: (() -> Unit)? = when {
    selecting -> { { selected.clear() } }
    query.isNotEmpty() -> { { query = "" } }
    scope !is XDriveScope.Everything -> { { scope = XDriveScope.Everything; view = XDriveView.ALL } }
    else -> null
  }
  DisposableEffect(backAction) {
    host.setBackConsumer(backAction)
    onDispose { host.setBackConsumer(null) }
  }

  val folderTitle: String? = when (val s = scope) {
    is XDriveScope.InFolder -> index.findFolder(s.folderId)?.name
    XDriveScope.Unsorted -> str(R.string.XgramXDriveUnsorted)
    XDriveScope.Everything -> null
  }
  val folderId = (scope as? XDriveScope.InFolder)?.folderId
  val topInset = with(LocalDensity.current) { host.topInset.toDp() }
  val bottomInset = with(LocalDensity.current) { host.bottomInset.toDp() }

  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Column(Modifier.fillMaxSize()) {
      // Top bar: the title (or how many are selected) and its actions
      Row(
        Modifier.fillMaxWidth().padding(top = topInset).height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = { backAction?.invoke() ?: host.back() }) {
          Icon(tgxIcon(if (selecting) R.drawable.baseline_close_24 else R.drawable.baseline_arrow_back_24), contentDescription = null)
        }
        Text(
          if (selecting) str(R.string.XgramXDriveSelected, selected.size) else folderTitle ?: str(R.string.XgramXDrive),
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        if (selecting) {
          val list = selected.values.toList()
          IconButton(onClick = { XDriveActions.chooseFolder(host, list.map { it.id }.toLongArray()) { selected.clear() } }) {
            Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = str(R.string.XgramXDriveAddToFolder))
          }
          if (folderId != null) {
            IconButton(onClick = { index.setFolder(list.map { it.id }.toLongArray(), null); selected.clear() }) {
              Icon(tgxIcon(R.drawable.baseline_folder_24), contentDescription = str(R.string.XgramXDriveRemoveFromFolder))
            }
          }
          IconButton(onClick = { XDriveActions.share(controller, list); selected.clear() }) {
            Icon(tgxIcon(R.drawable.baseline_forward_24), contentDescription = str(R.string.Share))
          }
          IconButton(onClick = { XDriveActions.delete(host, list) { selected.clear() } }) {
            Icon(tgxIcon(R.drawable.baseline_delete_24), contentDescription = str(R.string.Delete))
          }
        } else {
          IconButton(onClick = { XDriveActions.showInChat(controller, 0) }) {
            Icon(tgxIcon(R.drawable.baseline_chat_bubble_24), contentDescription = str(R.string.XgramXDriveOpenChat))
          }
        }
      }

      // Search
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(if (folderTitle != null) str(R.string.XgramXDriveSearchIn, folderTitle) else str(R.string.XgramXDriveSearch)) },
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
          { IconButton(onClick = { query = "" }) { Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.Clear)) } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
          unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
        )
      )

      // One view per kind
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(XDriveView.entries.toList()) { v ->
          FilterChip(
            selected = view == v,
            onClick = { view = v; selected.clear() },
            label = { Text(str(v.title)) },
            leadingIcon = { Icon(tgxIcon(v.icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors()
          )
        }
      }

      if (feed.loading.value) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp))
      } else {
        Spacer(Modifier.height(2.dp))
      }

      val gridState = rememberLazyGridState()
      LaunchedEffect(feed, gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
          if (last >= gridState.layoutInfo.totalItemsCount - 12) feed.loadMore()
        }
      }
      val showFolders = scope is XDriveScope.Everything && searchQuery.isEmpty() && !selecting
      LazyVerticalGrid(
        state = gridState,
        columns = if (view.grid) GridCells.Adaptive(104.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = if (view.grid) 12.dp else 0.dp, end = if (view.grid) 12.dp else 0.dp, bottom = 96.dp + bottomInset),
        horizontalArrangement = Arrangement.spacedBy(if (view.grid) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (view.grid) 4.dp else 0.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        if (showFolders) {
          item(span = { GridItemSpan(maxLineSpan) }, key = "folders") {
            FoldersRow(index, indexVersion, onOpen = { scope = it; selected.clear() })
          }
        }
        val items = feed.items
        if (items.isEmpty() && !feed.loading.value) {
          item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            Text(
              if (searchQuery.isNotEmpty()) str(R.string.XgramXDriveNoResults) else str(R.string.XgramXDriveEmpty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
          }
        }
        items(items, key = { it.id }) { message ->
          val isSelected = selected.containsKey(message.id)
          val onClick: () -> Unit = {
            if (selecting) {
              if (isSelected) selected.remove(message.id) else selected[message.id] = message
            } else {
              XDriveActions.open(controller, message)
            }
          }
          val onLongClick: () -> Unit = {
            if (isSelected) selected.remove(message.id) else selected[message.id] = message
          }
          if (view.grid) {
            GridCell(message, isSelected, onClick, onLongClick)
          } else {
            ItemRow(message, isSelected, index.folderOf(message.id)?.let { index.findFolder(it)?.name }.takeIf { scope is XDriveScope.Everything }, onClick, onLongClick)
          }
        }
      }
    }

    // New: a note, files, photos and videos, a folder (into the folder shown)
    if (!selecting) {
      var menu by remember { mutableStateOf(false) }
      Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + bottomInset)) {
        ExtendedFloatingActionButton(
          onClick = { menu = true },
          icon = { Icon(tgxIcon(R.drawable.baseline_add_24), contentDescription = null) },
          text = { Text(str(R.string.XgramXDriveNew)) }
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(str(R.string.XgramXDriveNewNote)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_format_text_24), null) }, onClick = { menu = false; XDriveActions.newNote(host, folderId) })
          DropdownMenuItem(text = { Text(str(R.string.XgramXDriveUploadMedia)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_image_24), null) }, onClick = { menu = false; XDriveActions.upload(host, true, folderId) })
          DropdownMenuItem(text = { Text(str(R.string.XgramXDriveUploadFiles)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_insert_drive_file_24), null) }, onClick = { menu = false; XDriveActions.upload(host, false, folderId) })
          if (folderId == null) {
            DropdownMenuItem(text = { Text(str(R.string.XgramXDriveNewFolder)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), null) }, onClick = { menu = false; XDriveActions.newFolder(host) })
          }
        }
      }
    }
  }
}

/** The folders as cards: Unsorted (in no folder) first, then the user's, then New folder. A long press edits one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoldersRow(index: XDriveIndex, version: Int, onOpen: (XDriveScope) -> Unit) {
  val host = LocalXgramHost.current
  val folders = remember(version) { if (index.isLoaded) index.folders() else emptyList() }
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(
      str(R.string.XgramXDriveFolders),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item(key = "unsorted") {
        FolderCard(str(R.string.XgramXDriveUnsorted), str(R.string.XgramXDriveUnsortedInfo), R.drawable.baseline_archive_24, onClick = { onOpen(XDriveScope.Unsorted) }, onLongClick = null)
      }
      items(folders, key = { it.id }) { folder ->
        var menu by remember { mutableStateOf(false) }
        Box {
          FolderCard(folder.name, Lang.plural(R.string.xMessages, index.count(folder.id).toLong()), R.drawable.baseline_folder_24, onClick = { onOpen(XDriveScope.InFolder(folder.id)) }, onLongClick = { menu = true })
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(str(R.string.XgramXDriveRename)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_edit_24), null) }, onClick = { menu = false; XDriveActions.renameFolder(host, folder) })
            DropdownMenuItem(text = { Text(str(R.string.XgramXDriveDeleteFolder), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_delete_24), null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; XDriveActions.deleteFolder(host, folder) {} })
          }
        }
      }
      item(key = "new") {
        FolderCard(str(R.string.XgramXDriveNewFolder), null, R.drawable.baseline_create_new_folder_24, onClick = { XDriveActions.newFolder(host) }, onLongClick = null)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(title: String, subtitle: String?, icon: Int, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
  ) {
    Column(Modifier.padding(14.dp)) {
      Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(tgxIcon(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.height(10.dp))
      Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** A row: the thumbnail (or the kind's icon), the name, size or site and date, the folder it is in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(message: TdApi.Message, selected: Boolean, folderName: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
  val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
  Row(
    Modifier.fillMaxWidth().background(background).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(48.dp)) {
      Thumb(message, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
      if (selected) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
          Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
      }
    }
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      Text(XDriveItems.title(message), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val subtitle = XDriveItems.subtitle(message)
      Text(
        if (folderName != null) "$subtitle · $folderName" else subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/** A square of the photo and video grid, a check over it when selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(message: TdApi.Message, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
  Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
    Thumb(message, Modifier.fillMaxSize())
    if (XDriveItems.kindOf(message) == XDriveKind.VIDEO) {
      Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0x99000000)), contentAlignment = Alignment.Center) {
        Icon(tgxIcon(R.drawable.baseline_play_arrow_36_white), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
      }
    }
    if (selected) {
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .35f)))
      Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
      }
    }
  }
}

/** The item's thumbnail (its tiny preview first), or its kind's icon on a tile. */
@Composable
private fun Thumb(message: TdApi.Message, modifier: Modifier) {
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
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(iconOf(XDriveItems.kindOf(message))), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
    }
  }
}

private fun iconOf(kind: XDriveKind): Int = when (kind) {
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
