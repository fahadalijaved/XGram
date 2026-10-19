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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.thunderdog.challegram.R

/**
 * The settings search at the top of a settings page: the collapsed M3E search bar (a full-round pill in
 * surfaceContainerHigh with the search icon and "Search settings"), as a plain button. It opens the universal
 * settings search page (XgramSettingsPages.search), a page of its own in the same pane: not a dialog window over the
 * whole screen, so with two panes the chat list stays in view.
 */
@Composable
fun SettingsSearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    shape = SearchBarDefaults.inputFieldShape,
    color = cs.surfaceContainerHigh,
    contentColor = cs.onSurfaceVariant,
    modifier = modifier.fillMaxWidth().height(SearchBarDefaults.InputFieldHeight)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(tgxIcon(R.drawable.baseline_search_24), contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(Modifier.width(16.dp))
      Text(
        str(R.string.XgramSettingsSearchHint),
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
