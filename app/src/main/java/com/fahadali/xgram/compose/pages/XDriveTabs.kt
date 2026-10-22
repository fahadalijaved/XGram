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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.fahadali.xgram.xdrive.XDriveIndex
import com.fahadali.xgram.xdrive.XDriveItems
import com.fahadali.xgram.xdrive.XDriveKind
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

/** A list of XDrive, attached while it is on screen. */
@Composable
internal fun rememberFeed(state: XDriveState, view: XDriveView, scope: XDriveScope, query: String = ""): XDriveFeed {
  val feed = remember(view, scope, query) { XDriveFeed(state.tdlib, view, scope, query) }
  DisposableEffect(feed) {
    feed.attach()
    onDispose { feed.detach() }
  }
  // A folder's contents (or what is in none) change with the index
  LaunchedEffect(feed, state.indexVersion) {
    if (scope !is XDriveScope.Everything && state.indexVersion > 1) feed.reload()
  }
  return feed
}

/** Reads on as the end of [grid] comes near. */
@Composable
internal fun LoadMore(feed: XDriveFeed, grid: LazyGridState) {
  LaunchedEffect(feed, grid) {
    snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
      if (last >= grid.layoutInfo.totalItemsCount - 18) feed.loadMore()
    }
  }
}

/** The bottom room the lists keep free: the navigation bar's inset and the floating button above it. */
@Composable
internal fun bottomRoom(state: XDriveState): Dp = with(LocalDensity.current) { state.host.bottomInset.toDp() } + 112.dp

// Home

@Composable
internal fun HomeTab(state: XDriveState, openTab: (XDriveTab) -> Unit) {
  val recent = rememberFeed(state, XDriveView.ALL, XDriveScope.Everything)
  val counts = rememberKindCounts(state.tdlib)
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom(state))) {
    item(key = "organize") { OrganizeCard(state) }
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
        SectionTitle(str(R.string.XgramXDriveFolders), str(R.string.XgramXDriveSeeAll)) { openTab(XDriveTab.FOLDERS) }
        HomeFolders(state)
      }
    }
    item(key = "suggested") { Suggestions(state, recent.items) }
  }
}

/** The way into Organize with AI: a bright card with a slowly turning shape. */
@Composable
private fun OrganizeCard(state: XDriveState) {
  val c = MaterialTheme.colorScheme
  val turn = rememberInfiniteTransition(label = "organizeTurn")
  val rotation by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "organizeRotation")
  Box(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(32.dp))
      .background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer)))
      .clickable { state.organizing = true }
      .padding(20.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }.clip(MaterialShapes.SoftBurst.toShape()).background(c.primary))
        Icon(tgxIcon(R.drawable.xgram_auto_awesome_24), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(32.dp))
      }
      Column(Modifier.weight(1f).padding(start = 18.dp)) {
        Text(str(R.string.XgramXDriveOrganize), style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(str(R.string.XgramXDriveOrganizeInfo), style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = .8f))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { state.organizing = true }) {
          Text(str(R.string.XgramXDriveOrganizeStart))
        }
      }
    }
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
              .clickable {
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
            Text(str(view.title), style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
private fun RecentRow(state: XDriveState, recent: List<TdApi.Message>) {
  val controller = LocalXgramHost.current.controller
  LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    items(recent, key = { it.id }) { message ->
      val isSelected = state.selected.containsKey(message.id)
      Column(
        Modifier.width(128.dp).animateItem().clip(RoundedCornerShape(20.dp))
          .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
          .xdriveItem(state, message) { XDriveActions.open(controller, message) }
      ) {
        XDriveThumb(message, Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp)), iconSize = 30)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Text(XDriveItems.title(message), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(XDriveItems.subtitle(message), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun HomeFolders(state: XDriveState) {
  val folders = state.folders()
  val counts = state.counts()
  LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    items(folders, key = { it.id }) { folder ->
      FolderTile(
        state, folder.id, folder.name, Lang.plural(R.string.xMessages, (counts[folder.id] ?: 0).toLong()), folder.emoji, R.drawable.baseline_folder_24,
        dropKey = folder.id, onClick = { state.folder = XDriveScope.InFolder(folder.id) }, onLongClick = null,
        modifier = Modifier.width(156.dp).animateItem()
      )
    }
    item(key = "new") {
      FolderTile(
        state, "new", str(R.string.XgramXDriveNewFolder), null, null, R.drawable.baseline_create_new_folder_24,
        dropKey = DROP_NEW, onClick = { XDriveActions.newFolder(state.host) }, onLongClick = null, modifier = Modifier.width(156.dp)
      )
    }
  }
}

/** Topics the rules see in what was saved lately (three items or more, no folder of that name yet): one tap makes it. */
@Composable
private fun Suggestions(state: XDriveState, items: List<TdApi.Message>) {
  val version = state.indexVersion
  val suggestions = remember(items.size, version) {
    val byTopic = LinkedHashMap<XDriveTopic, MutableList<TdApi.Message>>()
    for (message in items) {
      if (state.index.folderOf(message.id) != null) continue
      val topic = XDriveRules.topicOf(message, XDriveItems.kindOf(message)) ?: continue
      byTopic.getOrPut(topic) { ArrayList() }.add(message)
    }
    byTopic.entries.filter { it.value.size >= 3 && state.index.findFolderByName(Lang.getString(it.key.title)) == null }.sortedByDescending { it.value.size }.take(4)
  }
  if (suggestions.isEmpty()) return
  Column {
    SectionTitle(str(R.string.XgramXDriveSuggested))
    Text(
      str(R.string.XgramXDriveSuggestedInfo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
    )
    for ((topic, list) in suggestions) {
      val name = str(topic.title)
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp))
          .background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        FolderBadge(topic.name, topic.emoji, R.drawable.baseline_folder_24, 44)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
          Text(name, style = MaterialTheme.typography.titleSmall)
          Text(Lang.plural(R.string.xMessages, list.size.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = {
          val folder = state.index.findFolderByName(name) ?: state.index.createFolder(name, topic.emoji)
          state.move(list, folder.id)
        }) {
          Text(str(R.string.XgramXDriveCreate))
        }
      }
    }
  }
}

// Folders

@Composable
internal fun FoldersTab(state: XDriveState) {
  val host = state.host
  val controller = host.controller
  val folders = state.folders()
  val counts = state.counts()
  val unsorted = rememberFeed(state, XDriveView.ALL, XDriveScope.Unsorted)
  val grid = rememberLazyGridState()
  LoadMore(unsorted, grid)
  LazyVerticalGrid(
    state = grid,
    columns = GridCells.Fixed(2),
    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomRoom(state)),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        FilledTonalButton(onClick = { state.organizing = true }, modifier = Modifier.weight(1.3f)) {
          Icon(tgxIcon(R.drawable.xgram_auto_awesome_24), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(str(R.string.XgramXDriveOrganize), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = { XDriveActions.newFolder(host) }, modifier = Modifier.weight(1f)) {
          Icon(tgxIcon(R.drawable.baseline_create_new_folder_24), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(str(R.string.XgramXDriveNewFolder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    item(key = "unsorted") {
      FolderTile(
        state, "unsorted", str(R.string.XgramXDriveUnsorted), str(R.string.XgramXDriveUnsortedInfo), null, R.drawable.baseline_archive_24,
        dropKey = DROP_OUT, onClick = { state.folder = XDriveScope.Unsorted }, onLongClick = null, modifier = Modifier.animateItem()
      )
    }
    items(folders.size, key = { folders[it].id }) { i ->
      val folder = folders[i]
      var menu by remember { mutableStateOf(false) }
      Box(Modifier.animateItem()) {
        FolderTile(
          state, folder.id, folder.name, Lang.plural(R.string.xMessages, (counts[folder.id] ?: 0).toLong()), folder.emoji, R.drawable.baseline_folder_24,
          dropKey = folder.id, onClick = { state.folder = XDriveScope.InFolder(folder.id) }, onLongClick = { menu = true }
        )
        FolderMenu(state, folder, menu) { menu = false }
      }
    }
    item(key = "new") {
      FolderTile(
        state, "new", str(R.string.XgramXDriveNewFolder), null, null, R.drawable.baseline_create_new_folder_24,
        dropKey = DROP_NEW, onClick = { XDriveActions.newFolder(host) }, onLongClick = null, modifier = Modifier.animateItem()
      )
    }
    if (unsorted.items.isNotEmpty()) {
      item(key = "unsortedTitle", span = { GridItemSpan(maxLineSpan) }) {
        Column {
          SectionTitle(str(R.string.XgramXDriveNotInFolder))
          Text(
            str(R.string.XgramXDriveDragHint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
          )
        }
      }
    }
    items(unsorted.items.size, key = { unsorted.items[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
      val message = unsorted.items[i]
      XDriveRow(state, message, showFolder = false, onOpen = { XDriveActions.open(controller, message) }, modifier = Modifier.animateItem())
    }
  }
}

/** A folder's long-press menu: rename, its emoji, delete. */
@Composable
internal fun FolderMenu(state: XDriveState, folder: XDriveIndex.Folder, expanded: Boolean, onDismiss: () -> Unit) {
  val host = state.host
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuItem(text = { Text(str(R.string.XgramXDriveRename)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_edit_24), null) }, onClick = { onDismiss(); XDriveActions.renameFolder(host, folder) })
    DropdownMenuItem(text = { Text(str(R.string.XgramXDriveFolderEmoji)) }, leadingIcon = { Icon(tgxIcon(R.drawable.baseline_emoticon_outline_24), null) }, onClick = { onDismiss(); askEmoji(state, folder) })
    DropdownMenuItem(
      text = { Text(str(R.string.XgramXDriveDeleteFolder), color = MaterialTheme.colorScheme.error) },
      leadingIcon = { Icon(tgxIcon(R.drawable.baseline_delete_24), null, tint = MaterialTheme.colorScheme.error) },
      onClick = {
        onDismiss()
        XDriveActions.deleteFolder(host, folder) { if ((state.folder as? XDriveScope.InFolder)?.folderId == folder.id) state.folder = null }
      }
    )
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
    state.index.setEmoji(folder.id, value.substring(0, end).takeIf { it.isNotEmpty() })
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
    FilterRow(listOf(XDriveView.MEDIA, XDriveView.PHOTOS, XDriveView.VIDEOS, XDriveView.GIFS), state.mediaView) { state.mediaView = it; state.selected.clear() }
    val feed = rememberFeed(state, state.mediaView, XDriveScope.Everything)
    val grid = rememberLazyGridState()
    LoadMore(feed, grid)
    if (feed.items.isEmpty()) {
      Loading(feed)
      return@Column
    }
    val entries = remember(feed.items.size, feed.items.firstOrNull()?.id) { byMonth(feed.items) }
    LazyVerticalGrid(
      state = grid,
      columns = GridCells.Adaptive(104.dp),
      contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomRoom(state)),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      for (entry in entries) {
        when (entry) {
          is String -> item(key = "m$entry", span = { GridItemSpan(maxLineSpan) }) {
            Text(entry, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp).animateItem())
          }
          is TdApi.Message -> item(key = entry.id) {
            XDriveCell(state, entry, onOpen = { XDriveActions.open(controller, entry) }, modifier = Modifier.animateItem())
          }
        }
      }
    }
  }
}

/** Month headers ("October 2026") between the items, newest first. */
private fun byMonth(items: List<TdApi.Message>): List<Any> {
  val result = ArrayList<Any>(items.size + 12)
  var last = -1
  val calendar = Calendar.getInstance()
  for (message in items) {
    calendar.timeInMillis = message.date * 1000L
    val key = calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    if (key != last) {
      last = key
      result.add(java.text.SimpleDateFormat("LLLL yyyy", Lang.locale()).format(calendar.time))
    }
    result.add(message)
  }
  return result
}

// Files

@Composable
internal fun FilesTab(state: XDriveState) {
  val controller = state.host.controller
  Column(Modifier.fillMaxSize()) {
    FilterRow(listOf(XDriveView.FILES, XDriveView.LINKS, XDriveView.NOTES, XDriveView.MUSIC, XDriveView.VOICE), state.filesView) { state.filesView = it; state.selected.clear() }
    val feed = rememberFeed(state, state.filesView, XDriveScope.Everything)
    val grid = rememberLazyGridState()
    LoadMore(feed, grid)
    if (feed.items.isEmpty()) {
      Loading(feed)
      return@Column
    }
    LazyVerticalGrid(state = grid, columns = GridCells.Fixed(1), contentPadding = PaddingValues(bottom = bottomRoom(state)), modifier = Modifier.fillMaxSize()) {
      items(feed.items.size, key = { feed.items[it].id }) { i ->
        val message = feed.items[i]
        XDriveRow(state, message, showFolder = true, onOpen = { XDriveActions.open(controller, message) }, modifier = Modifier.animateItem())
      }
    }
  }
}

/** The kinds as connected toggle buttons (Expressive's button group), scrolling sideways when they don't fit. */
@Composable
internal fun FilterRow(views: List<XDriveView>, selected: XDriveView, onSelect: (XDriveView) -> Unit) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    modifier = Modifier.xgramNoSwipeBack()
  ) {
    items(views.size) { i ->
      val view = views[i]
      val checked = view == selected
      ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onSelect(view) },
        shapes = when (i) {
          0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
          views.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
          else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        }
      ) {
        Icon(tgxIcon(view.icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(str(view.title), maxLines = 1)
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

// A folder (or Unsorted)

@Composable
internal fun FolderScreen(state: XDriveState, scope: XDriveScope) {
  val controller = state.host.controller
  val feed = rememberFeed(state, XDriveView.ALL, scope)
  val grid = rememberLazyGridState()
  LoadMore(feed, grid)
  if (feed.items.isEmpty()) {
    Column(Modifier.fillMaxSize()) {
      if (feed.loading.value || !feed.reachedEnd.value) Loading(feed) else EmptyNote(str(R.string.XgramXDriveDragHint))
    }
    return
  }
  val media = feed.items.filter { val k = XDriveItems.kindOf(it); k == XDriveKind.PHOTO || k == XDriveKind.VIDEO || k == XDriveKind.GIF }
  val other = feed.items.filter { val k = XDriveItems.kindOf(it); !(k == XDriveKind.PHOTO || k == XDriveKind.VIDEO || k == XDriveKind.GIF) }
  LazyVerticalGrid(
    state = grid,
    columns = GridCells.Fixed(12),
    contentPadding = PaddingValues(bottom = bottomRoom(state)),
    verticalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    if (media.isNotEmpty()) {
      item(key = "mediaTitle", span = { GridItemSpan(maxLineSpan) }) { SectionTitle(str(R.string.XgramXDriveMediaSection)) }
      items(media.size, key = { media[it].id }, span = { GridItemSpan(4) }) { i ->
        val message = media[i]
        Box(Modifier.padding(horizontal = 1.5.dp).animateItem()) {
          XDriveCell(state, message, onOpen = { XDriveActions.open(controller, message) })
        }
      }
    }
    if (other.isNotEmpty()) {
      if (media.isNotEmpty()) {
        item(key = "otherTitle", span = { GridItemSpan(maxLineSpan) }) { SectionTitle(str(R.string.XgramXDriveOtherSection)) }
      }
      items(other.size, key = { other[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
        val message = other[i]
        XDriveRow(state, message, showFolder = scope is XDriveScope.Everything, onOpen = { XDriveActions.open(controller, message) }, modifier = Modifier.animateItem())
      }
    }
  }
}

// Search

@Composable
internal fun SearchScreen(state: XDriveState) {
  val controller = state.host.controller
  var query by remember { mutableStateOf("") }
  LaunchedEffect(state.query) {
    kotlinx.coroutines.delay(if (state.query.isEmpty()) 0 else 300)
    query = state.query.trim()
  }
  if (query.isEmpty()) {
    EmptyNote(str(R.string.XgramXDriveSearchHint))
    return
  }
  val feed = rememberFeed(state, XDriveView.ALL, XDriveScope.Everything, query)
  val grid = rememberLazyGridState()
  LoadMore(feed, grid)
  if (feed.items.isEmpty()) {
    Column(Modifier.fillMaxSize()) { Loading(feed, str(R.string.XgramXDriveNoResults)) }
    return
  }
  LazyVerticalGrid(state = grid, columns = GridCells.Fixed(1), contentPadding = PaddingValues(bottom = bottomRoom(state)), modifier = Modifier.fillMaxSize()) {
    items(feed.items.size, key = { feed.items[it].id }) { i ->
      val message = feed.items[i]
      XDriveRow(state, message, showFolder = true, onOpen = { XDriveActions.open(controller, message) }, modifier = Modifier.animateItem())
    }
  }
}
