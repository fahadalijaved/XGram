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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramComposeBridge
import com.fahadali.xgram.XgramSearchHit
import com.fahadali.xgram.compose.LocalXgramHost
import kotlinx.coroutines.launch
import org.thunderdog.challegram.R

/**
 * The search bar at the top of the main page. Tapping it opens a full-screen search (a window of its own: it handles
 * back and the keyboard). The index is built when it opens, so it follows the current language. A result opens its
 * page and points at the row; a row of the main page itself runs [onMainHit] with the row's id.
 */
@Composable
fun SettingsSearchBar(onMainHit: (Int) -> Unit) {
  val host = LocalXgramHost.current
  val textState = rememberTextFieldState()
  val searchState = rememberSearchBarState()
  val scope = rememberCoroutineScope()
  var index by remember { mutableStateOf<List<XgramSearchHit>?>(null) }

  val expanded = searchState.currentValue == SearchBarValue.Expanded
  LaunchedEffect(expanded, host.langVersion) {
    if (expanded) {
      index = XgramComposeBridge.searchIndex(host.activity, host.tdlib)
    }
  }

  val inputField = @Composable {
    SearchBarDefaults.InputField(
      textFieldState = textState,
      searchBarState = searchState,
      onSearch = { },
      placeholder = { Text(str(R.string.XgramSettingsSearchHint)) },
      leadingIcon = { Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = null) },
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

  SearchBar(
    state = searchState,
    inputField = inputField,
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
  )
  ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
    val query = textState.text.toString()
    val hits = remember(query, index) { filterHits(index, query) }
    val open: (XgramSearchHit) -> Unit = { hit ->
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
    if (query.isNotBlank() && hits.isEmpty() && index != null) {
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
        itemsIndexed(hits, key = { _, hit -> "${hit.section}/${hit.subPage}/${hit.id}:${hit.key ?: ""}" }) { i, hit ->
          SegmentedListItem(
            onClick = { open(hit) },
            shapes = ListItemDefaults.segmentedShapes(index = i, count = hits.size),
            leadingContent = if (hit.icon != 0) {
              { RowIcon(hit.icon) }
            } else {
              null
            },
            supportingContent = { Text(hit.path) },
            colors = settingsRowColors(),
            content = { Text(highlightWords(hit.title, query, MaterialTheme.colorScheme.primary)) }
          )
        }
      }
    }
  }
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
