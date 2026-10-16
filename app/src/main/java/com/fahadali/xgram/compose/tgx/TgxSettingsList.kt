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

package com.fahadali.xgram.compose.tgx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.theme.XgramM3Theme
import com.fahadali.xgram.compose.ui.SettingsDescription
import com.fahadali.xgram.compose.ui.SettingsEmpty
import com.fahadali.xgram.compose.ui.SettingsHeader
import com.fahadali.xgram.compose.ui.SettingsList
import com.fahadali.xgram.compose.ui.SettingsLoading
import com.fahadali.xgram.compose.ui.SettingsSwitch
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.compose.ui.xgramNoSwipeBack
import org.thunderdog.challegram.core.Lang
import kotlin.math.roundToInt

/**
 * A Telegram X page in Compose: the M3E theme and layout direction, the per-row list (SettingsList, shared with
 * XGram's own pages) under Telegram X's header, and the effects that tie the list to the page (scroll requests,
 * the large title and the header lift).
 */
@Composable
internal fun TgxSettingsScreen(r: TgxSettingsRenderer) {
  XgramM3Theme(dark = r.isDark.value, themeVersion = r.themeVersion.intValue) {
    val lang = r.langVersion.intValue
    val direction = if (lang >= 0 && Lang.rtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalXgramHost provides r.composeHost, LocalLayoutDirection provides direction) {
      val structure = r.structureVersion.intValue
      val blocks = remember(structure, lang) { r.buildBlocks() }
      val density = LocalDensity.current
      val padding = remember(r, density) { TgxListPadding(r, density) }
      Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.TopCenter
      ) {
        SettingsList(blocks, r.listState, padding, animateItems = true)
      }
      val request = r.scrollRequest.intValue
      LaunchedEffect(request, blocks) {
        r.applyPendingScroll()
      }
      LaunchedEffect(r, density) {
        val start = with(density) { 24.dp.toPx() }
        val distance = with(density) { 32.dp.toPx() }
        snapshotFlow {
          if (r.listState.firstVisibleItemIndex == 0) r.listState.firstVisibleItemScrollOffset else Int.MAX_VALUE
        }.collect { offset ->
          r.onListScrolled(offset, start, distance)
        }
      }
    }
  }
}

/** 16dp at the sides, 8dp on top (under Telegram X's header), the navigation bar or keyboard plus 16dp at the bottom. */
private class TgxListPadding(private val r: TgxSettingsRenderer, private val density: Density) : PaddingValues {
  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateTopPadding(): Dp = 8.dp

  override fun calculateBottomPadding(): Dp = 16.dp + with(density) { r.bottomInset.intValue.toDp() }
}

/** The page title as the list's first item (XgramM3Bars.LargeTitleDecoration's place): headlineMedium. */
@Composable
internal fun TgxLargeTitle(r: TgxSettingsRenderer) {
  r.langVersion.intValue
  val name = r.controller.name?.toString().orEmpty()
  Text(
    name,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
    style = MaterialTheme.typography.headlineMedium,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis
  )
}

/**
 * One row of the page: [shapes] is its place in a segmented group (null: on the page itself, as headers,
 * descriptions, empty states and progress are). The row's view is bound on its first composition.
 */
@Composable
internal fun TgxRowBlock(r: TgxSettingsRenderer, row: TgxRow, shapes: ListItemShapes?, segment: Int, headerTop: Dp) {
  DisposableEffect(row) {
    row.composed++
    onDispose {
      row.composed--
    }
  }
  val s = r.snapOf(row)
  val placed = Modifier.onPlaced { row.coordinates = it }
  if (shapes == null) {
    when (s.kind) {
      TgxRowKind.HIDDEN -> Unit
      TgxRowKind.HEADER -> SettingsHeader(s.title?.toString().orEmpty(), 0, headerTop)
      TgxRowKind.DESCRIPTION -> TgxDescription(r, row, s)
      TgxRowKind.EMPTY -> SettingsEmpty(0, s.title?.toString().orEmpty())
      TgxRowKind.LOADING -> SettingsLoading()
      TgxRowKind.BUILD_NO -> Text(
        s.title?.toString().orEmpty(),
        modifier = placed.fillMaxWidth().clickable(enabled = s.enabled) { r.click(row) }.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      TgxRowKind.FALLBACK -> TgxFallback(r, row, placed, XgramExpressive.SEGMENT_NONE)
      // A row drawn natively outside any group: a card of its own.
      else -> TgxGroupRow(r, row, s, ListItemDefaults.segmentedShapes(index = 0, count = 1), XgramExpressive.SEGMENT_SINGLE, placed)
    }
  } else {
    TgxGroupRow(r, row, s, shapes, segment, placed)
  }
}

@Composable
private fun TgxGroupRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, segment: Int, placed: Modifier) {
  val modifier = if (s.dimmed) placed.alpha(0.5f) else placed
  when (s.kind) {
    TgxRowKind.SWITCH -> TgxSwitchRow(r, row, s, shapes, modifier)
    TgxRowKind.RADIO -> TgxRadioRow(r, row, s, shapes, modifier)
    TgxRowKind.CHECKBOX -> TgxCheckboxRow(r, row, s, shapes, modifier)
    TgxRowKind.VALUE, TgxRowKind.BUTTON -> TgxValueRow(r, row, s, shapes, modifier)
    TgxRowKind.SLIDER -> TgxSliderRow(r, row, s, shapes, modifier)
    TgxRowKind.HIDDEN -> Unit
    TgxRowKind.HEADER -> SettingsHeader(s.title?.toString().orEmpty(), 0, 8.dp)
    TgxRowKind.DESCRIPTION -> TgxDescription(r, row, s)
    TgxRowKind.LOADING -> SettingsLoading()
    TgxRowKind.EMPTY, TgxRowKind.BUILD_NO, TgxRowKind.FALLBACK -> TgxFallback(
      r, row, placed.clip(shapes.shape).background(MaterialTheme.colorScheme.surfaceBright), segment
    )
  }
}

private fun longClickOf(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap): (() -> Unit)? =
  if (s.longClickable) {
    { r.longClick(row) }
  } else {
    null
  }

/** The leading icon of a Telegram X row: a tonal circle (as Telegram X rows have with Expressive design). */
private fun leadingOf(s: TgxRowSnap): (@Composable () -> Unit)? =
  if (s.icon != 0) {
    { TgxRowIcon(s.icon, s.iconRotated, s.destructive) }
  } else {
    null
  }

@Composable
private fun TgxRowIcon(icon: Int, rotated: Boolean, destructive: Boolean) {
  val cs = MaterialTheme.colorScheme
  val angle by animateFloatAsState(if (rotated) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec<Float>(), label = "tgxIcon")
  Box(
    modifier = Modifier.size(40.dp).clip(CircleShape).background(if (destructive) cs.errorContainer else cs.primaryContainer),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      tgxIcon(icon),
      contentDescription = null,
      modifier = Modifier.size(24.dp).rotate(angle),
      tint = if (destructive) cs.onErrorContainer else cs.onPrimaryContainer
    )
  }
}

@Composable
private fun TgxTitle(s: TgxRowSnap, color: Color = Color.Unspecified) {
  Text(s.title?.toString().orEmpty(), color = color)
}

private fun supportingOf(s: TgxRowSnap): (@Composable () -> Unit)? {
  val subtitle = s.subtitle ?: return null
  return { Text(subtitle.toString()) }
}

@Composable
private fun TgxSwitchRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  SegmentedListItem(
    onClick = { r.click(row) },
    shapes = shapes,
    modifier = modifier.semantics {
      role = Role.Switch
      toggleableState = ToggleableState(s.checked)
    },
    enabled = s.enabled && !s.busy,
    leadingContent = leadingOf(s),
    trailingContent = {
      if (s.busy) {
        ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
      } else {
        SettingsSwitch(s.checked, !s.toggleDisabled && !s.locked)
      }
    },
    supportingContent = supportingOf(s),
    onLongClick = longClickOf(r, row, s),
    colors = settingsRowColors(),
    content = { TgxTitle(s) }
  )
}

@Composable
private fun TgxRadioRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  SegmentedListItem(
    selected = s.checked,
    onClick = { r.click(row) },
    shapes = shapes,
    modifier = modifier.semantics { role = Role.RadioButton },
    enabled = s.enabled,
    leadingContent = { RadioButton(selected = s.checked, onClick = null, enabled = s.enabled) },
    supportingContent = supportingOf(s),
    onLongClick = longClickOf(r, row, s),
    colors = settingsRowColors(),
    content = { TgxTitle(s) }
  )
}

@Composable
private fun TgxCheckboxRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  SegmentedListItem(
    onClick = { r.click(row) },
    shapes = shapes,
    modifier = modifier.semantics {
      role = Role.Checkbox
      toggleableState = ToggleableState(s.checked)
    },
    enabled = s.enabled,
    leadingContent = { Checkbox(checked = s.checked, onCheckedChange = null, enabled = s.enabled) },
    supportingContent = supportingOf(s),
    onLongClick = longClickOf(r, row, s),
    colors = settingsRowColors(),
    content = { TgxTitle(s) }
  )
}

@Composable
private fun TgxValueRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  val view = row.holder?.itemView
  val trailing = if (view != null) r.hints.trailing(row.item, view) else null
  val titleColor = when {
    s.destructive -> cs.error
    s.kind == TgxRowKind.BUTTON -> cs.primary
    else -> Color.Unspecified
  }
  SegmentedListItem(
    onClick = { r.click(row) },
    shapes = shapes,
    modifier = modifier,
    enabled = s.enabled,
    leadingContent = leadingOf(s),
    trailingContent = trailing,
    supportingContent = supportingOf(s),
    onLongClick = longClickOf(r, row, s),
    colors = settingsRowColors(),
    content = { TgxTitle(s, titleColor) }
  )
}

@Composable
private fun TgxSliderRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val labels = s.sliderLabels
  val last = (labels.size - 1).coerceAtLeast(1)
  var position by remember(s.sliderIndex, labels) { mutableFloatStateOf(s.sliderIndex.toFloat()) }
  val shown = position.roundToInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0))
  SegmentedListItem(
    shapes = shapes,
    modifier = modifier,
    enabled = s.enabled,
    supportingContent = {
      Column {
        Text(labels.getOrElse(shown) { "" })
        Slider(
          value = position,
          onValueChange = { position = it },
          modifier = Modifier.fillMaxWidth().xgramNoSwipeBack(),
          enabled = s.enabled,
          valueRange = 0f..last.toFloat(),
          steps = (labels.size - 2).coerceAtLeast(0),
          onValueChangeFinished = {
            val index = position.roundToInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0))
            position = index.toFloat()
            if (index != s.sliderIndex) {
              r.slide(row, index)
            }
          }
        )
      }
    },
    colors = settingsRowColors(),
    content = { TgxTitle(s) }
  )
}

/** A description with Telegram X's styles; its links tap the real spans (on the bound text view). */
@Composable
private fun TgxDescription(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap) {
  val linkColor = MaterialTheme.colorScheme.primary
  val text = remember(s.title, linkColor) { TgxSpans.annotated(s.title, row.holder?.itemView, linkColor) }
  SettingsDescription(text, s.centered)
}

/**
 * The real Telegram X view in the row's place. It moves from the headless host into the list while the row is on
 * screen and back when it leaves (never reused for another row). In a group it is clipped to the row's shape, and its
 * M3E tile takes the row's corners.
 */
@Composable
private fun TgxFallback(r: TgxSettingsRenderer, row: TgxRow, modifier: Modifier, segment: Int) {
  AndroidView(
    factory = { context -> TgxFallbackFrame(context).also { r.adopt(row, it, segment) } },
    modifier = modifier.fillMaxWidth(),
    onRelease = { frame -> r.release(row, frame) },
    update = { frame ->
      if (row.frame !== frame) {
        r.adopt(row, frame, segment)
      } else {
        r.setSegment(row, segment)
      }
    }
  )
}
