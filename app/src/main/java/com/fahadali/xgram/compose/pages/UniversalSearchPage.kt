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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.IconStyle
import com.fahadali.xgram.compose.ui.MaxPageWidth
import com.fahadali.xgram.compose.ui.RowIcon
import com.fahadali.xgram.compose.ui.SettingsEmpty
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.search.SearchEntry
import com.fahadali.xgram.search.SearchIndex
import com.fahadali.xgram.search.SettingsSearchEngine
import com.fahadali.xgram.search.SettingsSearchOpen
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang

/**
 * The universal settings search (Settings, and Settings > XGram), as a page of its own: an M3E search field at the
 * top and the results under it, in the right pane with two panes. Before anything is typed: the results opened last
 * and the pages to go to. A result opens its page and points at its row; back comes here with the query kept.
 * Not the full-screen SearchBar: that is a dialog window over both panes.
 */
@Composable
fun UniversalSearchPage() {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val keyboard = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val textState = rememberTextFieldState()
  val focusRequester = remember { FocusRequester() }
  var recentsVersion by remember { mutableIntStateOf(0) }

  // Built when the page opens and whenever it shows again (a page opened from here may have changed what is
  // shown), and in the new language. No TDLib requests; filtering below is in memory only.
  val index by produceState<SearchIndex?>(null, host.langVersion, host.version) {
    value = SettingsSearchEngine.build(host.controller, host.origin)
  }

  // The keyboard comes up once, when the page first shows: not again when coming back from a result.
  var focusAsked by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (!focusAsked) {
      focusAsked = true
      host.controller.addOneShotFocusListener {
        try {
          focusRequester.requestFocus()
          keyboard?.show()
        } catch (ignored: IllegalStateException) {
          // The field is not in the composition anymore.
        }
      }
    }
  }

  val scrollHidesKeyboard = remember(keyboard) { KeyboardOnScroll(keyboard) }
  val open: (SearchEntry, Boolean) -> Unit = { entry, record ->
    keyboard?.hide()
    focusManager.clearFocus()
    SettingsSearchOpen.open(host.controller, entry, record)
  }

  Box(
    modifier = Modifier.fillMaxSize().background(cs.surfaceContainer),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(modifier = Modifier.fillMaxHeight().widthIn(max = MaxPageWidth).fillMaxWidth()) {
      Spacer(Modifier.height(with(density) { host.topInset.toDp() }))
      // The M3E search field: the pill of the search bar, with back in front and Clear at the end
      Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        SearchBarDefaults.InputField(
          textState,
          onSearch = { keyboard?.hide() },
          expanded = true,
          onExpandedChange = { },
          modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
            if (it.isFocused) scrollHidesKeyboard.hidden = false
          },
          placeholder = { Text(str(R.string.XgramSettingsSearchHint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon = {
            IconButton(onClick = {
              keyboard?.hide()
              host.back()
            }) {
              Icon(tgxIcon(R.drawable.baseline_arrow_back_24), contentDescription = str(R.string.XgramComposeBack))
            }
          },
          trailingIcon = if (textState.text.isNotEmpty()) {
            {
              IconButton(onClick = {
                textState.clearText()
                focusRequester.requestFocus()
              }) {
                Icon(tgxIcon(R.drawable.baseline_close_24), contentDescription = str(R.string.Clear))
              }
            }
          } else {
            null
          }
        )
      }

      val query = textState.text.toString()
      val current = index
      val blank = query.isBlank()
      val recents = remember(current, recentsVersion, blank) {
        if (current != null && blank) current.recents() else emptyList()
      }
      val hits = remember(current, query) {
        if (current != null && !blank) SettingsSearchEngine.search(current, query) else emptyList()
      }
      val hitEntries = remember(hits) { hits.map { it.entry } }
      val hitRanges = remember(hits) { hits.map { it.ranges } }
      val padding = remember(host, density) { SearchListPadding(host, density) }
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(scrollHidesKeyboard),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
      ) {
        if (current == null) {
          return@LazyColumn
        }
        if (blank) {
          if (recents.isNotEmpty()) {
            item(key = "recentHeader", contentType = "header") {
              SearchHeader(str(R.string.XgramSearchRecent), action = str(R.string.Clear)) {
                XgramSettings.instance().clearSearchRecents()
                recentsVersion++
              }
            }
            resultRows(host, recents, null, "r:", IconStyle.PLAIN, forceIcon = R.drawable.baseline_history_24) { open(it, true) }
          }
          if (current.shortcuts.isNotEmpty()) {
            item(key = "pagesHeader", contentType = "header") {
              SearchHeader(str(R.string.XgramSearchPages))
            }
            // The pages look as they do on the main pages: icons in tonal circles
            resultRows(host, current.shortcuts, null, "p:", IconStyle.CIRCLE) { open(it, false) }
          }
        } else if (hitEntries.isEmpty()) {
          item(key = "empty", contentType = "empty") {
            SettingsEmpty(R.drawable.baseline_search_24, str(R.string.NothingFound))
          }
        } else {
          resultRows(host, hitEntries, hitRanges, "h:", IconStyle.PLAIN) { open(it, true) }
        }
      }
    }
  }
}

/**
 * Results as one segmented group: the title with what matched in primary, the path under it in the app's direction,
 * the setting's icon (pages in tonal circles), and Night mode's own switch.
 */
private fun LazyListScope.resultRows(
  host: XgramComposeHost,
  entries: List<SearchEntry>,
  ranges: List<IntArray>?,
  prefix: String,
  style: IconStyle,
  forceIcon: Int = 0,
  onClick: (SearchEntry) -> Unit
) {
  val count = entries.size
  itemsIndexed(entries, key = { _, e -> prefix + e.key }, contentType = { _, _ -> "result" }) { i, e ->
    val icon = if (forceIcon != 0) forceIcon else e.icon
    val iconStyle = if (forceIcon == 0 && e.isPage && style == IconStyle.PLAIN) IconStyle.CIRCLE else style
    val primary = MaterialTheme.colorScheme.primary
    val matched = ranges?.getOrNull(i)
    val title = remember(e, matched, primary) { highlighted(e.title, matched, primary) }
    val toggle = e.toggle
    SegmentedListItem(
      onClick = { onClick(e) },
      shapes = ListItemDefaults.segmentedShapes(index = i, count = count),
      leadingContent = if (icon != 0) {
        { RowIcon(icon, iconStyle) }
      } else {
        null
      },
      supportingContent = if (e.path.isNotEmpty()) {
        { PathText(e.path) }
      } else {
        null
      },
      trailingContent = if (toggle != null) {
        {
          // Its own tap target: flips the setting here; a tap on the rest of the row opens the page. Reading
          // isDark recomposes it when the theme changes.
          val checked = remember(host.isDark) { toggle.checked() }
          val label = str(R.string.XgramSearchNightModeToggle)
          Switch(
            checked = checked,
            onCheckedChange = {
              toggle.toggle()
              host.themeChanged()
            },
            modifier = Modifier.semantics { contentDescription = label },
            thumbContent = {
              Icon(
                tgxIcon(if (checked) R.drawable.baseline_check_24 else R.drawable.baseline_close_24),
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
              )
            }
          )
        }
      } else {
        null
      },
      colors = settingsRowColors(),
      content = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    )
  }
}

/** The path under a result: "Privacy and security > Last seen & online", in the app's direction. */
@Composable
private fun PathText(path: String) {
  val direction = if (Lang.rtl()) TextDirection.Rtl else TextDirection.Ltr
  Text(
    path,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    style = LocalTextStyle.current.copy(textDirection = direction)
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

/** [title] with the [ranges] ([start, end) pairs) in [color], medium weight. */
private fun highlighted(title: String, ranges: IntArray?, color: Color): AnnotatedString = buildAnnotatedString {
  append(title)
  if (ranges == null) {
    return@buildAnnotatedString
  }
  var i = 0
  while (i + 1 < ranges.size) {
    val start = ranges[i].coerceIn(0, title.length)
    val end = ranges[i + 1].coerceIn(start, title.length)
    if (end > start) {
      addStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium), start, end)
    }
    i += 2
  }
}

/** A drag on the results puts the keyboard away (once per focus of the field). */
private class KeyboardOnScroll(private val keyboard: SoftwareKeyboardController?) : NestedScrollConnection {
  var hidden = false

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (!hidden && source == NestedScrollSource.UserInput && available.y != 0f) {
      hidden = true
      keyboard?.hide()
    }
    return Offset.Zero
  }
}

/**
 * 16dp at the sides, 8dp on top, the navigation bar or the keyboard plus 16dp at the bottom; the inset is read when
 * the list is measured, not while composing.
 */
private class SearchListPadding(private val host: XgramComposeHost, private val density: Density) : PaddingValues {
  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateTopPadding(): Dp = 8.dp

  override fun calculateBottomPadding(): Dp = 16.dp + with(density) { host.bottomInset.toDp() }
}
