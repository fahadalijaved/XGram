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

package com.fahadali.xgram.compose.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import kotlinx.coroutines.flow.first
import org.thunderdog.challegram.R

/**
 * A settings page in the Android 16 Settings look: a large flexible top app bar that collapses on scroll
 * (exitUntilCollapsed), and below it headers, descriptions and segmented card groups built by [content].
 *
 * [content] runs during composition after the page has read the host's version, so it reads settings singletons
 * directly: every setter path calls host.changed(), which builds the page again (a few dozen rows, cheap). Rows that
 * depend on other rows (enabled only while a switch is on) need no extra state.
 */
@Composable
fun SettingsPage(
  title: String,
  subtitle: String? = null,
  large: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
  content: PageScope.() -> Unit
) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  host.readVersions()
  val blocks = PageScope().apply(content).blocks
  val currentBlocks by rememberUpdatedState(blocks)

  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(snapAnimationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  val highlight = remember { if (host.highlightId != 0) XgramHighlight(host.highlightId, host.highlightKey) else null }

  if (highlight != null) {
    val pulseSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val offset = with(density) { 72.dp.roundToPx() }
    LaunchedEffect(highlight) {
      snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
      val index = currentBlocks.indexOfFirst { it.contains(highlight.id, highlight.key) }
      if (index >= 0) {
        listState.animateScrollToItem(index, -offset)
      }
      repeat(2) {
        highlight.progress.animateTo(1f, pulseSpec)
        highlight.progress.animateTo(0f, pulseSpec)
      }
      host.consumeHighlight()
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = cs.surfaceContainer,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      val colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surfaceContainer, scrolledContainerColor = cs.surfaceContainer)
      val insets = WindowInsets(top = host.topInset)
      val titleSlot: @Composable () -> Unit = { Text(title) }
      val navigationSlot: @Composable () -> Unit = { BackButton(host) }
      if (large) {
        if (subtitle != null) {
          LargeFlexibleTopAppBar(
            title = titleSlot, subtitle = { Text(subtitle) }, navigationIcon = navigationSlot, actions = actions,
            windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior
          )
        } else {
          LargeFlexibleTopAppBar(
            title = titleSlot, navigationIcon = navigationSlot, actions = actions,
            windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior
          )
        }
      } else {
        if (subtitle != null) {
          MediumFlexibleTopAppBar(
            title = titleSlot, subtitle = { Text(subtitle) }, navigationIcon = navigationSlot, actions = actions,
            windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior
          )
        } else {
          MediumFlexibleTopAppBar(
            title = titleSlot, navigationIcon = navigationSlot, actions = actions,
            windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior
          )
        }
      }
    }
  ) { padding ->
    val bottom = with(density) { host.bottomInset.toDp() }
    CompositionLocalProvider(LocalXgramHighlight provides highlight) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
          modifier = Modifier.fillMaxHeight().widthIn(max = MaxPageWidth).fillMaxWidth(),
          state = listState,
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 16.dp + bottom)
        ) {
          items(blocks, key = { it.key }, contentType = { it.type }) { block ->
            block.content()
          }
        }
      }
    }
  }
}

/** Unfolded and in landscape the cards stop growing at this width, centered. */
val MaxPageWidth: Dp = 720.dp

/** Reads the values a page builder depends on, so the calling composable rebuilds when they change. */
fun XgramComposeHost.readVersions(): Int = version + langVersion

@Composable
private fun BackButton(host: XgramComposeHost) {
  if (host.canGoBack) {
    IconButton(onClick = { host.back() }) {
      Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramComposeBack))
    }
  }
}

/** One item of a page's list. */
class PageBlock internal constructor(
  val key: String,
  val type: Int,
  internal val rows: List<GroupRow>,
  val content: @Composable () -> Unit
) {
  fun contains(id: Int, key: String?): Boolean = rows.any { it.id != 0 && it.id == id && it.key == key }

  companion object {
    const val TYPE_HEADER = 1
    const val TYPE_DESCRIPTION = 2
    const val TYPE_GROUP = 3
    const val TYPE_ITEM = 4
  }
}

/** Builds a page: headers, descriptions and card groups in order. Keys keep scroll position and animations stable. */
class PageScope internal constructor() {
  internal val blocks = ArrayList<PageBlock>()
  private val keys = HashSet<String>()

  private fun uniqueKey(base: String): String {
    var key = base
    var n = 1
    while (!keys.add(key)) {
      key = base + "#" + (n++)
    }
    return key
  }

  private val lastType: Int
    get() = if (blocks.isEmpty()) 0 else blocks[blocks.size - 1].type

  /** titleSmall in the primary color; [icon] in front of it (the Ghost Mode header). */
  fun header(@StringRes title: Int, @DrawableRes icon: Int = 0) = header(s(title), icon)

  fun header(title: String, @DrawableRes icon: Int = 0) {
    val top = if (blocks.isEmpty()) 8.dp else 20.dp
    blocks.add(PageBlock(uniqueKey("h:$title"), PageBlock.TYPE_HEADER, emptyList()) { SettingsHeader(title, icon, top) })
  }

  /** One or two sentences under a group, in onSurfaceVariant. */
  fun description(@StringRes text: Int) = description(s(text), "d:$text")

  /** A description whose text changes: give it a stable [key]. */
  fun description(text: CharSequence, key: Any) {
    val string = text.toString()
    blocks.add(PageBlock(uniqueKey("d:$key"), PageBlock.TYPE_DESCRIPTION, emptyList()) { SettingsDescription(string) })
  }

  /** One segmented card group: rows with 2dp gaps, large outer corners on the first and last row. */
  fun group(key: Any? = null, rows: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(rows)
    val list = scope.rows.toList()
    if (list.isEmpty()) {
      return
    }
    val base = key?.toString() ?: list.first().rowKey
    val gap = if (lastType == PageBlock.TYPE_HEADER || blocks.isEmpty()) 0.dp else 16.dp
    blocks.add(PageBlock(uniqueKey("g:$base"), PageBlock.TYPE_GROUP, list) { SettingsGroupBlock(list, gap) })
  }

  /** Anything else, full width (the search bar, previews). */
  fun item(key: Any, content: @Composable () -> Unit) {
    blocks.add(PageBlock(uniqueKey("i:$key"), PageBlock.TYPE_ITEM, emptyList(), content))
  }
}

@Composable
fun SettingsHeader(title: String, @DrawableRes icon: Int = 0, top: Dp = 20.dp) {
  val cs = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = top, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != 0) {
      Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = cs.primary)
      Spacer(Modifier.width(8.dp))
    }
    Text(title, style = MaterialTheme.typography.titleSmall, color = cs.primary)
  }
}

@Composable
fun SettingsDescription(text: String) {
  Text(
    text,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
