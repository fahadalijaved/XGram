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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSearchHit
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.thunderdog.challegram.R

/**
 * The settings search of the main page. The bar is an item of the page's list ([SettingsSearchBar]); the full-screen
 * search it opens is composed by the page outside the list ([SettingsSearchOverlay]). Both share this state.
 */
@Stable
class SettingsSearchState internal constructor(
  internal val text: TextFieldState,
  internal val bar: SearchBarState
)

@Composable
fun rememberSettingsSearchState(): SettingsSearchState {
  val text = rememberTextFieldState()
  val bar = rememberSearchBarState()
  return remember(text, bar) { SettingsSearchState(text, bar) }
}

/** The search bar at the top of the main page, an item of its list. Tapping it opens [SettingsSearchOverlay]. */
@Composable
fun SettingsSearchBar(state: SettingsSearchState) {
  val scope = rememberCoroutineScope()
  SearchBar(
    state = state.bar,
    inputField = { SearchInputField(state.text, state.bar, scope, expandedLook = false) },
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
  )
}

/**
 * The full-screen search (a window of its own: it handles back and the keyboard), passed to SettingsPage as its
 * overlay so it lives outside the list. Its leading icon is a back arrow that closes it. The index is built when it
 * opens, so it follows the current language. Before anything is typed it shows the results opened last and the
 * settings pages. A result opens its page and points at the row; a row of the main page itself runs [onMainHit] with
 * the row's id.
 */
@Composable
fun SettingsSearchOverlay(state: SettingsSearchState, onMainHit: (Int) -> Unit) {
  val host = LocalXgramHost.current
  val searchState = state.bar
  val textState = state.text
  val scope = rememberCoroutineScope()
  var index by remember { mutableStateOf<List<XgramSearchHit>?>(null) }
  var recents by remember { mutableStateOf<List<String>>(emptyList()) }

  val expanded = searchState.currentValue == SearchBarValue.Expanded
  LaunchedEffect(expanded, host.langVersion) {
    if (expanded) {
      index = XgramComposeBridge.searchIndex(host.activity, host.tdlib)
      recents = XgramSettings.instance().searchRecents()
    }
  }

  // The full-screen search view: the same container colour as the pill, and no line under the field.
  ExpandedFullScreenSearchBar(
    state = searchState,
    inputField = { SearchInputField(textState, searchState, scope, expandedLook = true) },
    colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, dividerColor = Color.Transparent)
  ) {
    val query = textState.text.toString()
    val hits = remember(query, index) { filterHits(index, query) }
    val open: (XgramSearchHit, Boolean) -> Unit = { hit, record ->
      if (record) {
        XgramSettings.instance().addSearchRecent(hitKey(hit))
      }
      scope.launch {
        searchState.animateToCollapsed()
        val page = hit.page
        if (page == null) {
          onMainHit(hit.id)
        } else {
          host.openPage(page, hit.id, hit.key)
        }
      }
    }
    if (query.isBlank()) {
      val recentHits = remember(recents, index) { resolveRecents(index, recents) }
      val shortcuts = remember(host.langVersion) { XgramComposeBridge.searchShortcuts() }
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
      ) {
        if (recentHits.isNotEmpty()) {
          item(key = "recent", contentType = "header") {
            SearchHeader(str(R.string.XgramSearchRecent), action = str(R.string.Clear)) {
              XgramSettings.instance().clearSearchRecents()
              recents = emptyList()
            }
          }
          hitRows(recentHits, "r", query = "", iconOf = { R.drawable.baseline_history_24 }) { open(it, true) }
        }
        item(key = "pages", contentType = "header") {
          SearchHeader(str(R.string.XgramSearchPages))
        }
        // The pages look as they do on the main page: icons in tonal circles.
        hitRows(shortcuts, "p", query = "", iconStyle = IconStyle.CIRCLE) { open(it, false) }
      }
    } else if (hits.isEmpty() && index != null) {
      Text(
        str(R.string.NothingFound),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
      ) {
        hitRows(hits, "h", query) { open(it, true) }
      }
    }
  }
}

/** The pill shows a search icon; the full-screen view a back arrow that closes it. */
@Composable
private fun SearchInputField(textState: TextFieldState, searchState: SearchBarState, scope: CoroutineScope, expandedLook: Boolean) {
  SearchBarDefaults.InputField(
    textFieldState = textState,
    searchBarState = searchState,
    onSearch = { },
    placeholder = { Text(str(R.string.XgramSettingsSearchHint)) },
    leadingIcon = if (expandedLook) {
      {
        IconButton(onClick = {
          textState.clearText()
          scope.launch { searchState.animateToCollapsed() }
        }) {
          Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramComposeBack))
        }
      }
    } else {
      { Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = null) }
    },
    trailingIcon = if (textState.text.isNotEmpty()) {
      {
        IconButton(onClick = { textState.clearText() }) {
          Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.Clear))
        }
      }
    } else {
      null
    }
  )
}

@Composable
private fun SearchHeader(title: String, action: String? = null, onAction: () -> Unit = { }) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      title,
      modifier = Modifier.weight(1f).padding(vertical = 12.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary
    )
    if (action != null) {
      TextButton(onClick = onAction) { Text(action) }
    }
  }
}

/** Result rows as one segmented group; [iconOf] gives each row's leading icon (0: none). */
private fun LazyListScope.hitRows(
  hits: List<XgramSearchHit>,
  prefix: String,
  query: String,
  iconStyle: IconStyle = IconStyle.PLAIN,
  iconOf: (XgramSearchHit) -> Int = { it.icon },
  onClick: (XgramSearchHit) -> Unit
) {
  itemsIndexed(hits, key = { _, hit -> "$prefix:${hitKey(hit)}" }, contentType = { _, _ -> "hit" }) { i, hit ->
    val icon = iconOf(hit)
    SegmentedListItem(
      onClick = { onClick(hit) },
      shapes = ListItemDefaults.segmentedShapes(index = i, count = hits.size),
      leadingContent = if (icon != 0) {
        { RowIcon(icon, iconStyle) }
      } else {
        null
      },
      supportingContent = if (hit.path.isNotEmpty()) {
        { Text(hit.path) }
      } else {
        null
      },
      colors = settingsRowColors(),
      content = { Text(highlightWords(hit.title, query, MaterialTheme.colorScheme.primary)) }
    )
  }
}

/** Identifies a result across index rebuilds (the language may change in between). */
private fun hitKey(hit: XgramSearchHit): String = "${hit.section}/${hit.subPage}/${hit.id}:${hit.key ?: ""}"

/** The remembered results that are still in the index, newest first. */
private fun resolveRecents(index: List<XgramSearchHit>?, recents: List<String>): List<XgramSearchHit> {
  if (index == null || recents.isEmpty()) {
    return emptyList()
  }
  val byKey = HashMap<String, XgramSearchHit>(index.size)
  for (hit in index) {
    byKey.putIfAbsent(hitKey(hit), hit)
  }
  return recents.mapNotNull { byKey[it] }
}

private fun words(query: String): List<String> =
  query.lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() }

/** Titles that contain every word; titles with a word starting a word come first (as XgramSettingsSearch). */
private fun filterHits(index: List<XgramSearchHit>?, query: String): List<XgramSearchHit> {
  val words = words(query)
  if (index == null || words.isEmpty()) {
    return emptyList()
  }
  val first = ArrayList<XgramSearchHit>()
  val more = ArrayList<XgramSearchHit>()
  for (hit in index) {
    if (words.all { hit.titleLower.contains(it) }) {
      val w = words[0]
      if (hit.titleLower.startsWith(w) || hit.titleLower.contains(" $w")) first.add(hit) else more.add(hit)
    }
  }
  first.addAll(more)
  return first
}

private fun highlightWords(title: String, query: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
  append(title)
  val lower = title.lowercase()
  if (lower.length != title.length) {
    return@buildAnnotatedString
  }
  for (w in words(query)) {
    var from = 0
    while (true) {
      val at = lower.indexOf(w, from)
      if (at < 0) break
      addStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium), at, at + w.length)
      from = at + w.length
    }
  }
}
