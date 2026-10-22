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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.ConfirmSpec
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.PromptSpec
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.compose.ui.xgramNoSwipeBack
import com.fahadali.xgram.xdrive.XDriveActions
import com.fahadali.xgram.xdrive.XDriveFeed
import com.fahadali.xgram.xdrive.XDriveIcons
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveOrganizer
import com.fahadali.xgram.xdrive.XDriveRules
import com.fahadali.xgram.xdrive.XDriveScope
import com.fahadali.xgram.xdrive.XDriveTopic
import com.fahadali.xgram.xdrive.XDriveView
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.tool.UI
import java.util.Calendar

/** Reads on as the end of [grid] comes near. */
@Composable
internal fun LoadMore(feed: XDriveFeed, grid: LazyGridState) {
  LaunchedEffect(feed, grid) {
    snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
      if (last >= grid.layoutInfo.totalItemsCount - 18) feed.loadMore()
    }
  }
}

/** Reads on as the end of [list] comes near. */
@Composable
internal fun LoadMore(feed: XDriveFeed, list: LazyListState) {
  LaunchedEffect(feed, list) {
    snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
      if (last >= list.layoutInfo.totalItemsCount - 12) feed.loadMore()
    }
  }
}

/** Room under a list: the floating button (and, on a screen without the navigation bar, the system inset). */
@Composable
internal fun bottomRoom(state: XDriveState, withInset: Boolean): Dp =
  (if (withInset) with(LocalDensity.current) { state.host.bottomInset.toDp() } else 0.dp) + 96.dp

// Home

@Composable
internal fun HomeTab(state: XDriveState, openTab: (XDriveTab) -> Unit) {
  val recent = state.feed(XDriveView.ALL, XDriveScope.Everything)
  val counts = rememberKindCounts(state.tdlib)
  // The big Organize card until XDrive has been organized (or while it runs, or waits for a review); after that only
  // a small one, once enough new things are in no folder
  val organizer = state.organizer
  val organizedUpTo = remember(state.indexVersion) { state.index.organizedUpTo() }
  val bigCard = organizedUpTo == 0L || organizer.running || organizer.phase == XDriveOrganizer.Phase.REVIEW
  val fresh = remember(recent.version.intValue, state.indexVersion, organizedUpTo) {
    if (organizedUpTo == 0L) 0 else recent.items.count { it.id > organizedUpTo && state.index.folderOf(it.id) == null }
  }
  val folders = state.folders()
  val folderCounts = state.counts()
  LazyColumn(Modifier.fillMaxSize(), state = state.list("home"), contentPadding = PaddingValues(top = 4.dp, bottom = bottomRoom(state, false))) {
    if (bigCard) {
      item(key = "organize") { Box(Modifier.animateItem()) { OrganizeCard(state) } }
    } else if (fresh >= NEW_SINCE_ORGANIZE) {
      item(key = "organizeNew") { Box(Modifier.animateItem()) { OrganizeNew(state, fresh) } }
    }
    item(key = "quick") {
      Column {
        SectionTitle(str(R.string.XgramXDriveQuickAccess))
        QuickAccess(state, counts, openTab)
      }
    }
    if (recent.items.isNotEmpty()) {
      item(key = "recent") {
        Column {
          SectionTitle(str(R.string.XgramXDriveRecent))
          RecentRow(state, recent.items.take(24))
        }
      }
    }
    item(key = "folders") {
      Column {
        SectionTitle(
          str(R.string.XgramXDriveFolders),
          subtitle = str(R.string.XgramXDriveFoldersSummary, Lang.plural(R.string.xFolders, folders.size.toLong()), folderCounts.values.sum()),
          action = str(R.string.XgramXDriveSeeAll)
        ) { openTab(XDriveTab.FOLDERS) }
        HomeFolders(state, folders, folderCounts)
      }
    }
    item(key = "suggested") { Suggestions(state, recent.items) }
  }
}

/** New things in no folder (among the latest) before Home suggests organizing again. */
private const val NEW_SINCE_ORGANIZE = 8

/**
 * The way into Organize with AI: a bright card with a slowly turning shape. While it runs (its screen closed) the
 * card shows how far it is; once folders are proposed, it offers to look through them.
 */
@Composable
private fun OrganizeCard(state: XDriveState) {
  val c = MaterialTheme.colorScheme
  val organizer = state.organizer
  // Waiting for the user's OK to send to the cloud is not working: the slow turn, no progress, a way to answer
  val asking = organizer.phase == XDriveOrganizer.Phase.CONSENT
  val turn = rememberInfiniteTransition(label = "organizeTurn")
  val rotation by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(if (organizer.running && !asking) 4000 else 16000, easing = LinearEasing), RepeatMode.Restart), label = "organizeRotation")
  Box(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).xdriveBounds(state, BOUNDS_ORGANIZE).clip(RoundedCornerShape(32.dp))
      .background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer)))
      .padding(20.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }.clip(MaterialShapes.SoftBurst.toShape()).background(c.primary))
        Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(32.dp))
      }
      Column(Modifier.weight(1f).padding(start = 18.dp)) {
        Text(str(R.string.XgramXDriveOrganize), style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        when {
          asking -> {
            Text(organizeStatus(organizer), style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = .85f))
            Spacer(Modifier.height(12.dp))
            Button(onClick = { state.openOrganize(BOUNDS_ORGANIZE) }) { Text(str(R.string.XgramXDriveConsentReview)) }
          }
          organizer.running -> {
            Text(organizeStatus(organizer), style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = .85f))
            Spacer(Modifier.height(12.dp))
            val progress = organizeProgress(organizer)
            if (progress != null) {
              LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
              LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { state.openOrganize(BOUNDS_ORGANIZE) }) { Text(str(R.string.XgramXDriveOpenItem)) }
          }
          organizer.phase == XDriveOrganizer.Phase.REVIEW -> {
            Text(str(R.string.XgramXDriveReadyToReview), style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = .85f))
            Spacer(Modifier.height(12.dp))
            Button(onClick = { state.openOrganize(BOUNDS_ORGANIZE) }) { Text(str(R.string.XgramXDriveReview)) }
          }
          else -> {
            Text(str(R.string.XgramXDriveOrganizeInfo), style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = .85f))
            Spacer(Modifier.height(12.dp))
            Button(onClick = { state.openOrganize(BOUNDS_ORGANIZE) }) { Text(str(R.string.XgramXDriveOrganizeStart)) }
          }
        }
      }
    }
  }
}

/** The small way back into Organize with AI, once new things have piled up since the last time. */
@Composable
private fun OrganizeNew(state: XDriveState, count: Int) {
  val c = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).xdriveBounds(state, BOUNDS_ORGANIZE).clip(RoundedCornerShape(28.dp))
      .background(c.secondaryContainer).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(40.dp).clip(MaterialShapes.SoftBurst.toShape()).background(c.primary), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
    }
    Text(
      str(R.string.XgramXDriveNewSinceOrganize, items(count)), style = MaterialTheme.typography.bodyMedium, color = c.onSecondaryContainer,
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
    )
    FilledTonalButton(onClick = { state.openOrganize(BOUNDS_ORGANIZE) }) { Text(str(R.string.XgramXDriveSortThem)) }
  }
}

/** How many of each kind XDrive has (the server counts). */
@Composable
private fun rememberKindCounts(tdlib: Tdlib): Map<XDriveView, Int> {
  val counts = remember { mutableStateMapOf<XDriveView, Int>() }
  LaunchedEffect(tdlib) {
    for (view in QUICK) {
      tdlib.send(TdApi.GetChatMessageCount(tdlib.selfChatId(), null, view.filter(), false), Tdlib.ResultHandler<TdApi.Count> { count, _ ->
        if (count != null) UI.post { counts[view] = count.count }
      })
    }
  }
  return counts
}

private val QUICK = listOf(XDriveView.PHOTOS, XDriveView.VIDEOS, XDriveView.FILES, XDriveView.LINKS, XDriveView.MUSIC, XDriveView.VOICE)

@Composable
private fun QuickAccess(state: XDriveState, counts: Map<XDriveView, Int>, openTab: (XDriveTab) -> Unit) {
  val c = MaterialTheme.colorScheme
  val colors = listOf(
    Pair(c.primaryContainer, c.onPrimaryContainer), Pair(c.tertiaryContainer, c.onTertiaryContainer), Pair(c.secondaryContainer, c.onSecondaryContainer)
  )
  Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    QUICK.chunked(3).forEachIndexed { row, views ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        views.forEachIndexed { i, view ->
          val (container, content) = colors[(row + i) % colors.size]
          Column(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(container)
              .clickable(role = Role.Button) {
                if (view == XDriveView.PHOTOS || view == XDriveView.VIDEOS) {
                  state.mediaView = view
                  openTab(XDriveTab.MEDIA)
                } else {
                  state.filesView = view
                  openTab(XDriveTab.FILES)
                }
              }
              .padding(14.dp)
          ) {
            Icon(tgxIcon(view.icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(counts[view]?.toString() ?: "–", style = MaterialTheme.typography.titleLarge, color = content)
            Text(str(view.title), style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
private fun RecentRow(state: XDriveState, recent: List<TdApi.Message>) {
  val controller = LocalXgramHost.current.controller
  LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.xgramNoSwipeBack()) {
    items(recent, key = { it.id }) { message ->
      val isSelected = state.selected.containsKey(message.id)
      Column(
        Modifier.width(132.dp).animateItem().clip(RoundedCornerShape(20.dp))
          .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright)
          .xdriveItem(state, message) { XDriveActions.open(controller, message) }
      ) {
        // The picture fills the card's top: only the card's own corners round it
        XDriveThumb(message, Modifier.fillMaxWidth().height(96.dp), iconSize = 30)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Text(XDriveItems.title(message), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(XDriveItems.subtitle(message), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun HomeFolders(state: XDriveState, folders: List<XDriveIndex.Folder>, counts: Map<String, Int>) {
  LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.xgramNoSwipeBack()) {
    items(folders, key = { it.id }) { folder ->
      var menu by remember { mutableStateOf(false) }
      val target = "tile:home:" + folder.id
      FolderTile(
        state, folder.id, target, folder.name, items(counts[folder.id] ?: 0), folder.emoji, XDriveIcons.drawableOf(folder.icon),
        dropKey = folder.id, onClick = { state.openFolder(XDriveScope.InFolder(folder.id), target) }, onMenu = { menu = true },
        modifier = Modifier.width(168.dp).animateItem(),
        menu = { FolderMenu(state, folder, menu) { menu = false } }
      )
    }
    item(key = "new") {
      FolderTile(
        state, "new", "tile:home:new", str(R.string.XgramXDriveNewFolder), null, null, R.drawable.baseline_create_new_folder_24,
        dropKey = DROP_NEW, onClick = { XDriveActions.newFolder(state.host) }, onMenu = null, modifier = Modifier.width(168.dp)
      )
    }
  }
}

/** Topics the rules see in what was saved lately (three items or more, no folder of that name yet): one tap makes it. */
@Composable
private fun Suggestions(state: XDriveState, recent: List<TdApi.Message>) {
  val version = state.indexVersion
  val suggestions = remember(recent.size, recent.firstOrNull()?.id, recent.lastOrNull()?.id, version) {
    val byTopic = LinkedHashMap<XDriveTopic, MutableList<TdApi.Message>>()
    for (message in recent) {
      if (state.index.folderOf(message.id) != null) continue
      val topic = XDriveRules.topicOf(message, XDriveItems.kindOf(message)) ?: continue
      byTopic.getOrPut(topic) { ArrayList() }.add(message)
    }
    byTopic.entries.filter { it.value.size >= 3 && state.index.findFolderByName(Lang.getString(it.key.title)) == null }
      .sortedByDescending { it.value.size }.take(4).map { it.key to it.value.toList() }
  }
  if (suggestions.isEmpty()) return
  Column {
    SectionTitle(str(R.string.XgramXDriveSuggested), subtitle = str(R.string.XgramXDriveSuggestedInfo))
    suggestions.forEachIndexed { i, (topic, list) ->
      val name = str(topic.title)
      Box(Modifier.padding(horizontal = 16.dp).padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
          shapes = ListItemDefaults.segmentedShapes(index = i, count = suggestions.size),
          leadingContent = { FolderBadge(topic.name, null, XDriveIcons.drawableOf(topic.icon), 44.dp) },
          supportingContent = { Text(items(list.size)) },
          trailingContent = {
            FilledTonalButton(onClick = {
              val folder = state.index.findFolderByName(name) ?: state.index.createFolder(name, topic.icon)
              state.move(list, folder.id)
            }) { Text(str(R.string.XgramXDriveCreate)) }
          },
          colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
          content = { Text(name) }
        )
      }
    }
  }
}

// Folders: the sorting desk (tiles over what is in no folder yet)

@Composable
internal fun FoldersTab(state: XDriveState) {
  val host = state.host
  val controller = host.controller
  val folders = state.folders()
  val counts = state.counts()
  val unsorted = state.feed(XDriveView.ALL, XDriveScope.Unsorted)
  val grid = state.grid("folders")
  LoadMore(unsorted, grid)
  LazyVerticalGrid(
    state = grid,
    columns = GridCells.Adaptive(156.dp),
    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomRoom(state, false)),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    modifier = Modifier.fillMaxSize()
  ) {
    item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
      Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { state.openOrganize("organize:folders") }, modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).xdriveBounds(state, "organize:folders")) {
          Icon(tgxIcon(R.drawable.baseline_auto_awesome_24), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(str(R.string.XgramXDriveOrganize), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = { XDriveActions.newFolder(host) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
          Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(str(R.string.XgramXDriveNewFolder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item(key = "unsorted") {
      FolderTile(
        state, "unsorted", "tile:folders:unsorted", str(R.string.XgramXDriveUnsorted), str(R.string.XgramXDriveUnsortedInfo), null, R.drawable.baseline_archive_24,
        dropKey = DROP_OUT, onClick = { state.openFolder(XDriveScope.Unsorted, "tile:folders:unsorted") }, onMenu = null, modifier = Modifier.animateItem().padding(bottom = 8.dp)
      )
    }
    items(folders.size, key = { folders[it].id }) { i ->
      val folder = folders[i]
      var menu by remember { mutableStateOf(false) }
      val target = "tile:folders:" + folder.id
      FolderTile(
        state, folder.id, target, folder.name, items(counts[folder.id] ?: 0), folder.emoji, XDriveIcons.drawableOf(folder.icon),
        dropKey = folder.id, onClick = { state.openFolder(XDriveScope.InFolder(folder.id), target) }, onMenu = { menu = true },
        modifier = Modifier.animateItem().padding(bottom = 8.dp),
        menu = { FolderMenu(state, folder, menu) { menu = false } }
      )
    }
    item(key = "new") {
      FolderTile(
        state, "new", "tile:folders:new", str(R.string.XgramXDriveNewFolder), null, null, R.drawable.baseline_create_new_folder_24,
        dropKey = DROP_NEW, onClick = { XDriveActions.newFolder(host) }, onMenu = null, modifier = Modifier.animateItem().padding(bottom = 8.dp)
      )
    }
    val list = unsorted.items
    if (list.isNotEmpty()) {
      item(key = "unsortedTitle", span = { GridItemSpan(maxLineSpan) }) {
        SectionTitle(str(R.string.XgramXDriveNotInFolder), subtitle = str(R.string.XgramXDriveDragHint))
      }
    }
    items(list.size, key = { list[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
      val message = list[i]
      // The grid's 10 dp row gap less: the rows sit as one segmented card
      XDriveRow(
        state, message, ListItemDefaults.segmentedShapes(index = i, count = list.size), showFolder = false,
        onOpen = { XDriveActions.open(controller, message) },
        modifier = Modifier.animateItem()
      )
    }
  }
}

/** A folder's options: rename and its icon, then delete on its own (Expressive's grouped menu). */
@Composable
internal fun FolderMenu(state: XDriveState, folder: XDriveIndex.Folder, expanded: Boolean, onDismiss: () -> Unit) {
  val host = state.host
  DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
      DropdownMenuItem(
        onClick = { onDismiss(); XDriveActions.renameFolder(host, folder) },
        text = { Text(str(R.string.XgramXDriveRename)) },
        shape = MenuDefaults.itemShape(0, 2).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_edit_24), null) }
      )
      DropdownMenuItem(
        onClick = { onDismiss(); state.iconPicker = folder },
        text = { Text(str(R.string.XgramXDriveFolderIcon)) },
        shape = MenuDefaults.itemShape(1, 2).shape,
        leadingIcon = { Icon(tgxIcon(XDriveIcons.drawableOf(folder.icon)), null) }
      )
    }
    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
      DropdownMenuItem(
        onClick = { onDismiss(); state.deleteFolder(folder) },
        text = { Text(str(R.string.XgramXDriveDeleteFolder), color = MaterialTheme.colorScheme.error) },
        shape = MenuDefaults.itemShape(0, 1).shape,
        leadingIcon = { Icon(tgxIcon(R.drawable.baseline_delete_24), null, tint = MaterialTheme.colorScheme.error) }
      )
    }
  }
}

internal fun askEmoji(state: XDriveState, folder: XDriveIndex.Folder) {
  state.host.prompt(PromptSpec(title = Lang.getString(R.string.XgramXDriveFolderEmoji), hint = Lang.getString(R.string.XgramXDriveFolderEmojiHint), initial = folder.emoji ?: "") { text ->
    val value = text.trim()
    var end = 0
    while (end < value.length) {
      val cp = value.codePointAt(end)
      if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp)) break
      end += Character.charCount(cp)
    }
    val emoji = value.substring(0, end).takeIf { it.isNotEmpty() }
    if (emoji != null) state.index.setLook(folder.id, null, emoji)
  })
}

internal fun confirmAbout(state: XDriveState) {
  state.host.confirm(ConfirmSpec(title = Lang.getString(R.string.XgramXDriveAbout), text = Lang.getString(R.string.XgramXDriveInfo), confirm = Lang.getString(R.string.OK)) {})
}

// Media

@Composable
internal fun MediaTab(state: XDriveState) {
  val controller = state.host.controller
  Column(Modifier.fillMaxSize()) {
    FilterBar(listOf(XDriveView.MEDIA, XDriveView.PHOTOS, XDriveView.VIDEOS, XDriveView.GIFS), state.mediaView) { state.mediaView = it; state.selected.clear() }
    val feed = state.feed(state.mediaView, XDriveScope.Everything)
    val grid = state.grid(feedKey("media", state.mediaView, XDriveScope.Everything))
    LoadMore(feed, grid)
    if (feed.items.isEmpty()) {
      FabBack(state)
      Loading(feed)
      return@Column
    }
    val entries = remember(feed, feed.version.intValue) { byMonth(feed.items) }
    LazyVerticalGrid(
      state = grid,
      columns = GridCells.Adaptive(104.dp),
      contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomRoom(state, false)),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      for (entry in entries) {
        when (entry) {
          is MonthHeader -> item(key = "m" + entry.firstId, span = { GridItemSpan(maxLineSpan) }) {
            Text(
              entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp).animateItem().semantics { heading() }
            )
          }
          is TdApi.Message -> item(key = entry.id) {
            XDriveCell(state, entry, onOpen = { XDriveActions.open(controller, entry) }, modifier = Modifier.animateItem())
          }
        }
      }
    }
  }
}

private fun monthOf(message: TdApi.Message, calendar: Calendar): Int {
  calendar.timeInMillis = message.date * 1000L
  return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
}

private fun monthTitle(message: TdApi.Message): String {
  val calendar = Calendar.getInstance()
  calendar.timeInMillis = message.date * 1000L
  return java.text.SimpleDateFormat("LLLL yyyy", Lang.locale()).format(calendar.time)
}

private class MonthHeader(val title: String, val firstId: Long)

/** Month headers ("October 2026") between the items, newest first. */
private fun byMonth(items: List<TdApi.Message>): List<Any> {
  val result = ArrayList<Any>(items.size + 12)
  var last = -1
  val calendar = Calendar.getInstance()
  for (message in items) {
    val key = monthOf(message, calendar)
    if (key != last) {
      last = key
      result.add(MonthHeader(monthTitle(message), message.id))
    }
    result.add(message)
  }
  return result
}

/** Items in groups by month (newest first): each group one segmented card under its month. */
private fun groupsByMonth(items: List<TdApi.Message>): List<Pair<String?, List<TdApi.Message>>> {
  val result = ArrayList<Pair<String?, List<TdApi.Message>>>()
  val calendar = Calendar.getInstance()
  var last = -1
  var current = ArrayList<TdApi.Message>()
  for (message in items) {
    val key = monthOf(message, calendar)
    if (key != last) {
      last = key
      current = ArrayList()
      result.add(monthTitle(message) to current)
    }
    current.add(message)
  }
  return result
}

/** Rows in segmented groups, each under its title (or one untitled group). */
internal fun LazyListScope.rowGroups(
  state: XDriveState, groups: List<Pair<String?, List<TdApi.Message>>>, showFolder: Boolean, onOpen: (TdApi.Message) -> Unit
) {
  for ((title, list) in groups) {
    if (title != null && list.isNotEmpty()) {
      item(key = "g:" + list[0].id) {
        Text(
          title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp).animateItem().semantics { heading() }
        )
      }
    }
    items(list.size, key = { list[it].id }) { i ->
      val message = list[i]
      XDriveRow(
        state, message, ListItemDefaults.segmentedShapes(index = i, count = list.size), showFolder = showFolder,
        onOpen = { onOpen(message) },
        modifier = Modifier.animateItem().padding(horizontal = 16.dp).padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap)
      )
    }
  }
}

// Files

private val FILES_VIEWS = listOf(XDriveView.FILES, XDriveView.LINKS, XDriveView.NOTES, XDriveView.MUSIC, XDriveView.VOICE)

private fun sortSize(message: TdApi.Message): Long = XDriveItems.fileOf(message)?.size ?: 0L

@Composable
internal fun FilesTab(state: XDriveState) {
  val controller = state.host.controller
  Column(Modifier.fillMaxSize()) {
    FilterBar(FILES_VIEWS, state.filesView, mapOf(XDriveView.VOICE to R.string.XgramXDriveVoiceShort)) { state.filesView = it; state.selected.clear() }
    val feed = state.feed(state.filesView, XDriveScope.Everything)
    SortBar(state.filesSort) { state.filesSort = it }
    LaunchedEffect(feed, state.filesSort) {
      if (state.filesSort != XDriveSort.DATE) feed.loadAll() else feed.stopLoadingAll()
    }
    val list = state.list(feedKey("files", state.filesView, XDriveScope.Everything) + ":" + state.filesSort.name)
    LoadMore(feed, list)
    if (feed.items.isEmpty()) {
      FabBack(state)
      Loading(feed)
      return@Column
    }
    val sort = state.filesSort
    if (sort != XDriveSort.DATE && !feed.readAll.value) {
      // By name or size the list shows once every item is read, sorted once and from its top: sorted page by page it
      // reshuffled for seconds, and the rows held by key ended up away from the top
      FabBack(state)
      ReadingAll(feed)
      return@Column
    }
    val groups: List<Pair<String?, List<TdApi.Message>>> = remember(feed, feed.version.intValue, sort) {
      when (sort) {
        XDriveSort.DATE -> groupsByMonth(feed.items)
        XDriveSort.NAME -> listOf(null to feed.items.sortedBy { XDriveItems.title(it).lowercase() })
        XDriveSort.SIZE -> listOf(null to feed.items.sortedByDescending { sortSize(it) })
      }
    }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomRoom(state, false)), modifier = Modifier.fillMaxSize()) {
      rowGroups(state, groups, showFolder = true) { XDriveActions.open(controller, it) }
    }
  }
}

/** The order of a list: Expressive's connected toggle buttons for date, name and size. */
@Composable
private fun SortBar(sort: XDriveSort, onSort: (XDriveSort) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(tgxIcon(R.drawable.baseline_sort_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    Text(str(R.string.XgramXDriveSort), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, end = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
      val all = XDriveSort.entries
      all.forEachIndexed { i, s ->
        ToggleButton(
          checked = s == sort,
          onCheckedChange = { if (s != sort) onSort(s) },
          shapes = when (i) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            all.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
          },
          colors = ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondary, checkedContentColor = MaterialTheme.colorScheme.onSecondary
          ),
          modifier = Modifier.semantics { role = Role.RadioButton }
        ) {
          Text(str(s.title), maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

/**
 * The kinds as Expressive's connected button group: the chosen one widens to show its name, the others keep just their
 * icon (named for TalkBack), so every kind fits whole on any width; each button has a tonal container of its own.
 */
@Composable
internal fun FilterBar(views: List<XDriveView>, selected: XDriveView, names: Map<XDriveView, Int> = emptyMap(), onSelect: (XDriveView) -> Unit) {
  val cs = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  val colors = ToggleButtonDefaults.colors(
    containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurfaceVariant,
    checkedContainerColor = cs.primary, checkedContentColor = cs.onPrimary
  )
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
  ) {
    views.forEachIndexed { i, view ->
      val checked = view == selected
      val label = str(names[view] ?: view.title)
      val weight by animateFloatAsState(if (checked) 2.8f else 1f, motion.defaultSpatialSpec(), label = "filterWeight")
      ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onSelect(view) },
        shapes = when (i) {
          0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
          views.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
          else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.weight(weight.coerceAtLeast(0.5f)).heightIn(min = 48.dp)
      ) {
        Icon(tgxIcon(view.icon), contentDescription = if (checked) null else label, modifier = Modifier.size(20.dp))
        AnimatedVisibility(
          checked,
          enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
          exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        ) {
          Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
  }
}

/** A list's empty state: Expressive's loading indicator while it reads, a note once it has. */
@Composable
internal fun Loading(feed: XDriveFeed, empty: String? = null) {
  if (feed.loading.value || (!feed.reachedEnd.value && feed.items.isEmpty())) {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
      LoadingIndicator(Modifier.size(64.dp))
    }
  } else {
    EmptyNote(empty ?: str(R.string.XgramXDriveEmpty))
  }
}

/** Every item being read (for a sort by name or size): Expressive's loading indicator and how many so far. */
@Composable
private fun ReadingAll(feed: XDriveFeed) {
  Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    LoadingIndicator(Modifier.size(64.dp))
    Text(
      str(R.string.XgramXDriveReadingNow, items(feed.items.size)), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)
    )
  }
}

/** Where nothing scrolls (an empty list, its loading), no scroll can bring a hidden floating button back: it shows. */
@Composable
internal fun FabBack(state: XDriveState) {
  LaunchedEffect(Unit) { state.fabShown = true }
}

/** What a folder screen showed last, kept while its folder, deleted, closes. */
private class FolderShown {
  var folder: XDriveIndex.Folder? = null
  var count = 0
  var items: List<TdApi.Message> = emptyList()
}

// A folder (or Unsorted), over the tabs

@Composable
internal fun FolderScreen(state: XDriveState, scope: XDriveScope, openTab: (XDriveTab) -> Unit) {
  val controller = state.host.controller
  val host = state.host
  val feed = state.feed(XDriveView.ALL, scope)
  state.indexVersion
  val found = (scope as? XDriveScope.InFolder)?.let { state.index.findFolder(it.folderId) }
  // Deleted while open: the layer closes showing what the folder was (its name, count, items), not as an empty Unsorted
  val last = remember(scope) { FolderShown() }
  val gone = found == null && last.folder != null
  val folder = if (gone) last.folder else found
  val title = folder?.name ?: str(R.string.XgramXDriveUnsorted)
  val count = if (gone) last.count else folder?.let { state.counts()[it.id] ?: 0 }
  // The list's snapshot (no copy): the feed's own list empties as the folder goes
  val shownItems: List<TdApi.Message> = if (gone) last.items else feed.items.toList()
  if (found != null) {
    SideEffect {
      last.folder = found
      last.count = count ?: 0
      last.items = shownItems
    }
  }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var menu by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
    if (state.selecting) {
      SelectionBar(state) { feed.items }
    } else LargeFlexibleTopAppBar(
      title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
      subtitle = { Text(if (count != null) items(count) else str(R.string.XgramXDriveUnsortedInfo)) },
      navigationIcon = {
        IconButton(onClick = { state.closeFolder() }) { Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramXDriveBack)) }
      },
      actions = {
        IconButton(onClick = { state.openSearch() }) {
          Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = str(R.string.XgramXDriveSearchIn, title))
        }
        IconButton(onClick = { state.folderGrid = !state.folderGrid }) {
          Icon(
            tgxIcon(if (state.folderGrid) R.drawable.baseline_view_list_24 else R.drawable.baseline_grid_view_24),
            contentDescription = str(if (state.folderGrid) R.string.XgramXDriveViewList else R.string.XgramXDriveViewGrid)
          )
        }
        if (folder != null) {
          Box {
            // A deleted folder (its layer closing) has nothing left to rename or delete
            IconButton(onClick = { menu = true }, enabled = !gone) { Icon(tgxIcon(R.drawable.baseline_more_vert_24), contentDescription = str(R.string.XgramXDriveFolderMenu, title)) }
            FolderMenu(state, folder, menu && !gone) { menu = false }
          }
        }
      },
      windowInsets = WindowInsets(top = host.topInset),
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
      scrollBehavior = scrollBehavior
    )
    if (shownItems.isEmpty()) {
      FabBack(state)
      if (feed.loading.value || !feed.reachedEnd.value) {
        Loading(feed)
      } else {
        // An empty folder: what can fill it
        EmptyNote(str(R.string.XgramXDriveFolderEmptyInfo), title = str(R.string.XgramXDriveFolderEmpty)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val folderId = (scope as? XDriveScope.InFolder)?.folderId
            Button(onClick = { XDriveActions.upload(host, false, folderId) }) { Text(str(R.string.XgramXDriveUploadFiles)) }
            OutlinedButton(onClick = { state.closeFolder(); openTab(XDriveTab.FOLDERS) }) { Text(str(R.string.XgramXDriveMoveItemsHere)) }
          }
        }
      }
      return@Column
    }
    if (state.folderGrid) {
      val grid = state.grid(feedKey("folderGrid", XDriveView.ALL, scope))
      LoadMore(feed, grid)
      LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomRoom(state, true)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        val list = shownItems
        items(list.size, key = { list[it].id }) { i ->
          val message = list[i]
          XDriveCell(state, message, onOpen = { XDriveActions.open(controller, message) }, modifier = Modifier.animateItem())
        }
      }
    } else {
      val list = state.list(feedKey("folderList", XDriveView.ALL, scope))
      LoadMore(feed, list)
      val groups = remember(feed, feed.version.intValue, gone) { groupsByMonth(shownItems) }
      LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomRoom(state, true)), modifier = Modifier.fillMaxSize()) {
        rowGroups(state, groups, showFolder = false) { XDriveActions.open(controller, it) }
      }
    }
  }
}

// Search, over the tabs (or a folder)

private val SEARCH_VIEWS = listOf(XDriveView.ALL, XDriveView.MEDIA, XDriveView.FILES, XDriveView.LINKS, XDriveView.NOTES)

@Composable
internal fun SearchScreen(state: XDriveState) {
  val controller = state.host.controller
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  // Opening (again too, while it still shrinks away: this screen stays composed): the field takes the focus.
  // Closing (its back arrow too): the keyboard goes as the layer starts shrinking, so the tabs under it show their
  // navigation bar and button where they stay, not above the keyboard first and then jumping down
  LaunchedEffect(state.searching) {
    if (state.searching) {
      focus.requestFocus()
    } else {
      focusManager.clearFocus(force = true)
      keyboard?.hide()
    }
  }
  var query by remember { mutableStateOf("") }
  LaunchedEffect(state.query) {
    kotlinx.coroutines.delay(if (state.query.isEmpty()) 0 else 300)
    query = state.query.trim()
  }
  val scope = state.searchIn
  val folderName = when (scope) {
    is XDriveScope.InFolder -> state.index.findFolder(scope.folderId)?.name
    XDriveScope.Unsorted -> str(R.string.XgramXDriveUnsorted)
    XDriveScope.Everything -> null
  }
  Column(Modifier.fillMaxSize()) {
    if (state.selecting) {
      SelectionBar(state) { if (query.isEmpty()) emptyList() else state.feed(state.searchView, scope, query).items }
    } else Row(
      Modifier.fillMaxWidth().padding(top = with(LocalDensity.current) { state.host.topInset.toDp() }).heightIn(min = 72.dp).padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = { state.closeSearch() }) { Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramXDriveBack)) }
      TextField(
        value = state.query,
        onValueChange = { state.query = it },
        placeholder = { Text(if (folderName != null) str(R.string.XgramXDriveSearchIn, folderName) else str(R.string.XgramXDriveSearch), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
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
    if (folderName != null) {
      // Searching in a folder: one tap widens it to everything
      Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        InputChip(
          selected = true,
          onClick = { state.searchIn = XDriveScope.Everything },
          label = { Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          trailingIcon = { Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.XgramXDriveSearchEverywhere), modifier = Modifier.size(18.dp)) }
        )
      }
    }
    FilterBar(SEARCH_VIEWS, state.searchView, mapOf(XDriveView.MEDIA to R.string.XgramXDriveMedia)) { state.searchView = it }
    if (query.isEmpty()) {
      EmptyNote(str(R.string.XgramXDriveSearchStart))
      return@Column
    }
    // The server searches by kind and words; inside a folder its items are matched here
    val feed = state.feed(state.searchView, scope, query)
    val list = state.list("search:" + state.searchView.name + ":" + XDriveState.scopeKey(scope) + ":" + query)
    LoadMore(feed, list)
    if (feed.items.isEmpty()) {
      Loading(feed, str(R.string.XgramXDriveNoResults))
      return@Column
    }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomRoom(state, true)), modifier = Modifier.fillMaxSize()) {
      rowGroups(state, listOf<Pair<String?, List<TdApi.Message>>>(null to feed.items), showFolder = scope is XDriveScope.Everything) { message ->
        XDriveActions.open(controller, message)
      }
    }
  }
}
