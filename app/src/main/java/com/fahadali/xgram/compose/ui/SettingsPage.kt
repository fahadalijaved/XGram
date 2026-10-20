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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import kotlinx.coroutines.flow.first
import org.thunderdog.challegram.R

/**
 * A settings page in the Android 16 Settings look: an M3E large flexible top app bar ([SettingsTopAppBar], medium
 * flexible with [large] false) that collapses on scroll (exitUntilCollapsed), its title gliding into the collapsed
 * bar, and below it headers, descriptions and segmented card groups built by [content].
 *
 * [content] runs during composition after the page has read the host's version, so it reads settings singletons
 * directly: every setter path calls host.changed(), which builds the page again (a few dozen rows, cheap). Rows that
 * depend on other rows (enabled only while a switch is on) need no extra state. Every row of a group is an item of its
 * own in the list, so scrolling composes one row at a time.
 *
 * [overlay] is composed over the page, outside the list (the main page's full-screen search).
 */
@Composable
fun SettingsPage(
  title: String,
  subtitle: String? = null,
  large: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
  overlay: @Composable () -> Unit = {},
  content: PageScope.() -> Unit
) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  host.readVersions()
  val blocks = PageScope().apply(content).blocks
  val currentBlocks by rememberUpdatedState(blocks)

  // A quick settle: the bar never rests half collapsed after a fling.
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
    snapAnimationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
  )
  val listState = com.fahadali.xgram.compose.rememberXgramListState() // no prefetch: Compose 1.12 RectList crash
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

  // The status bar height, read by the app bar while it is measured.
  val topInset = remember(host) { { host.topInset } }

  Scaffold(
    // The list's scroll reaches the bar first (exitUntilCollapsed): it collapses before the rows move under it.
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = cs.surfaceContainer,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      SettingsTopAppBar(
        title = title,
        subtitle = subtitle,
        large = large,
        scrollBehavior = scrollBehavior,
        topInset = topInset,
        navigationIcon = { BackButton(host) },
        actions = actions
      )
    }
  ) { padding ->
    // Read only when the list is measured: the bar collapsing remeasures the list instead of recomposing it.
    val contentPadding = remember(padding, host, density) { PagePadding(padding, host, density) }
    CompositionLocalProvider(LocalXgramHighlight provides highlight) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        SettingsList(blocks, listState, contentPadding)
        overlay()
      }
    }
  }
}

/**
 * The list of a settings page: one lazy item per block, at most [MaxPageWidth] wide (center it in a Box). Shared by
 * [SettingsPage] and the Compose renderer of Telegram X pages (compose.tgx), which keeps Telegram X's header and has no
 * app bar of its own. [animateItems]: blocks fade in and out and move when the page adds or removes rows.
 */
@Composable
fun SettingsList(
  blocks: List<PageBlock>,
  listState: LazyListState,
  contentPadding: PaddingValues,
  modifier: Modifier = Modifier,
  animateItems: Boolean = false
) {
  LazyColumn(
    modifier = modifier.fillMaxHeight().widthIn(max = MaxPageWidth).fillMaxWidth(),
    state = listState,
    contentPadding = contentPadding
  ) {
    if (animateItems) {
      items(blocks, key = { it.key }, contentType = { it.type }) { block ->
        Box(Modifier.fillMaxWidth().animateItem()) {
          block.content()
        }
      }
    } else {
      items(blocks, key = { it.key }, contentType = { it.type }) { block ->
        block.content()
      }
    }
  }
}

/**
 * The list's padding: 16dp at the sides, the Scaffold's top padding (the collapsing app bar) plus 8dp at the top, and
 * the navigation bar or keyboard plus 16dp at the bottom. The Scaffold's padding and the inset are read when the list
 * asks for them while it is measured, never during composition.
 */
private class PagePadding(
  private val scaffold: PaddingValues,
  private val host: XgramComposeHost,
  private val density: Density
) : PaddingValues {
  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateTopPadding(): Dp = scaffold.calculateTopPadding() + 8.dp

  override fun calculateBottomPadding(): Dp = 16.dp + with(density) { host.bottomInset.toDp() }
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

/** One item of a page's list: a header, a description, one row of a card group, or anything else. */
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
    const val TYPE_ITEM = 4

    // Rows of card groups: one content type per kind of row, so the list only reuses a row for one of the same kind.
    // 10 is SettingsReorder's reorderable group.
    const val TYPE_SWITCH = 5
    const val TYPE_MAIN_SWITCH = 6
    const val TYPE_VALUE = 7
    const val TYPE_LINK = 8
    const val TYPE_ACTION = 9
    const val TYPE_BUTTONS = 11
    const val TYPE_SLIDER = 12
    const val TYPE_RADIO = 13
    const val TYPE_CUSTOM = 14
    const val TYPE_CHECKBOX = 15
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

  /** Adds an item to the list under [base] (made unique on the page). */
  internal fun add(base: String, type: Int, rows: List<GroupRow>, content: @Composable () -> Unit) {
    blocks.add(PageBlock(uniqueKey(base), type, rows, content))
  }

  /** The space above a card group: none at the top of the page or under a header, 16dp after anything else. */
  internal fun groupGap(): Dp =
    if (blocks.isEmpty() || blocks[blocks.size - 1].type == PageBlock.TYPE_HEADER) 0.dp else 16.dp

  /** titleSmall in the primary color; [icon] in front of it (the Ghost Mode header). */
  fun header(@StringRes title: Int, @DrawableRes icon: Int = 0) = header(s(title), icon)

  fun header(title: String, @DrawableRes icon: Int = 0) {
    val top = if (blocks.isEmpty()) 8.dp else 20.dp
    add("h:$title", PageBlock.TYPE_HEADER, emptyList()) { SettingsHeader(title, icon, top) }
  }

  /** A header whose text may change (Telegram X pages): [key] keeps it the same item. */
  fun header(title: String, key: Any) {
    val top = if (blocks.isEmpty()) 8.dp else 20.dp
    add("h:$key", PageBlock.TYPE_HEADER, emptyList()) { SettingsHeader(title, 0, top) }
  }

  /** One or two sentences under a group, in onSurfaceVariant. */
  fun description(@StringRes text: Int) = description(s(text), "d:$text")

  /** A description whose text changes: give it a stable [key]. */
  fun description(text: CharSequence, key: Any) {
    val string = text.toString()
    add("d:$key", PageBlock.TYPE_DESCRIPTION, emptyList()) { SettingsDescription(string) }
  }

  /** A description with styles and links (LinkAnnotation.Clickable): Telegram X's spanned texts. */
  fun description(text: AnnotatedString, key: Any) {
    add("d:$key", PageBlock.TYPE_DESCRIPTION, emptyList()) { SettingsDescription(text) }
  }

  /** The empty state of a list: an icon (0: none) over a line of text, centered. */
  fun empty(@DrawableRes icon: Int, text: String, key: Any = "empty") {
    add("e:$key", PageBlock.TYPE_ITEM, emptyList()) { SettingsEmpty(icon, text) }
  }

  /** A list that is still loading: an M3 Expressive LoadingIndicator, centered. */
  fun loading(key: Any = "loading") {
    add("l:$key", PageBlock.TYPE_ITEM, emptyList()) { SettingsLoading() }
  }

  /**
   * One segmented card group: rows with 2dp gaps, large outer corners on the first and last row. Each row is an item of
   * its own; its place in the group (so its shape) and the space above it are fixed here.
   */
  fun group(key: Any? = null, rows: GroupScope.() -> Unit) {
    val list = GroupScope().apply(rows).rows
    if (list.isEmpty()) {
      return
    }
    val gap = groupGap()
    val prefix = if (key != null) "g:$key/" else "row:"
    val count = list.size
    list.forEachIndexed { index, row ->
      val top = if (index == 0) gap else ListItemDefaults.SegmentedGap
      add(prefix + row.rowKey, row.type, listOf(row)) { SettingsGroupRow(row, index, count, top) }
    }
  }

  /** Anything else, full width (the search bar, previews). */
  fun item(key: Any, content: @Composable () -> Unit) {
    add("i:$key", PageBlock.TYPE_ITEM, emptyList(), content)
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

@Composable
fun SettingsDescription(text: AnnotatedString, centered: Boolean = false) {
  Text(
    text,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = if (centered) TextAlign.Center else TextAlign.Start
  )
}

@Composable
fun SettingsEmpty(@DrawableRes icon: Int, text: String) {
  val cs = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (icon != 0) {
      Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(48.dp), tint = cs.onSurfaceVariant)
      Spacer(Modifier.height(16.dp))
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}

@Composable
fun SettingsLoading() {
  Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
    LoadingIndicator()
  }
}
