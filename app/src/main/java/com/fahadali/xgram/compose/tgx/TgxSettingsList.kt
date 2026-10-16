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

import android.text.Spanned
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.tgxIcon
import com.fahadali.xgram.compose.ui.xgramNoSwipeBack
import org.thunderdog.challegram.R
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

/**
 * 16dp at the sides, 8dp on top (under Telegram X's header), the navigation bar or keyboard plus 16dp at the bottom.
 * Pages with Telegram X's floating Done button (EditBaseController) get room for it below their last row, as the
 * classic list has: the button never covers a row's switch or value. The inset is read when the list is measured.
 */
private class TgxListPadding(private val r: TgxSettingsRenderer, private val density: Density) : PaddingValues {
  private val bottom: Dp = if (r.floatingClearance) 16.dp + FloatingButtonClearance else 16.dp

  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp

  override fun calculateTopPadding(): Dp = 8.dp

  override fun calculateBottomPadding(): Dp = bottom + with(density) { r.bottomInset.intValue.toDp() }
}

/** The Done button (56dp, 16dp above the navigation bar) plus the 16dp gap between it and the last row. */
private val FloatingButtonClearance: Dp = 72.dp

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
  val placed = remember(row) { Modifier.onPlaced { row.coordinates = it } }
  if (shapes == null) {
    when (s.kind) {
      TgxRowKind.HIDDEN -> Unit
      TgxRowKind.HEADER -> SettingsHeader(s.title?.toString().orEmpty(), 0, headerTop)
      TgxRowKind.HEADER_ACTION -> TgxHeaderRow(r, row, s, headerTop, placed)
      TgxRowKind.DESCRIPTION -> TgxDescription(row, s)
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
  when (s.kind) {
    TgxRowKind.SWITCH -> TgxSwitchRow(r, row, s, shapes, placed)
    TgxRowKind.SPLIT_SWITCH -> TgxSplitSwitchRow(r, row, s, shapes, placed)
    TgxRowKind.RADIO -> TgxRadioRow(r, row, s, shapes, placed)
    TgxRowKind.CHECKBOX -> TgxCheckboxRow(r, row, s, shapes, placed)
    TgxRowKind.VALUE, TgxRowKind.BUTTON -> TgxValueRow(r, row, s, shapes, placed)
    TgxRowKind.SLIDER -> TgxSliderRow(r, row, s, shapes, placed)
    TgxRowKind.HIDDEN -> Unit
    TgxRowKind.HEADER -> SettingsHeader(s.title?.toString().orEmpty(), 0, 8.dp)
    TgxRowKind.HEADER_ACTION -> TgxHeaderRow(r, row, s, 8.dp, placed)
    TgxRowKind.DESCRIPTION -> TgxDescription(row, s)
    TgxRowKind.LOADING -> SettingsLoading()
    TgxRowKind.EMPTY, TgxRowKind.BUILD_NO, TgxRowKind.FALLBACK -> TgxFallback(
      r, row, placed.clip(shapes.shape).background(MaterialTheme.colorScheme.surfaceBright), segment
    )
  }
}

// Rows of a group

/**
 * A row of a group with Telegram X's two states kept apart (SettingView: isEnabled takes taps, isVisuallyEnabled is
 * the look):
 * - takes taps, looks enabled: a normal row;
 * - takes taps, looks disabled (a right that can't be changed, which explains why on tap): its normal tile with
 *   disabled content colors, still tappable;
 * - takes no taps, looks enabled (setIgnoreEnabled, then setEnabled(false): counters, statistics): normal colors, no
 *   ripple;
 * - takes no taps, looks disabled: the M3 disabled row, dimmed once.
 */
@Composable
private fun TgxListItem(
  r: TgxSettingsRenderer,
  row: TgxRow,
  s: TgxRowSnap,
  shapes: ListItemShapes,
  modifier: Modifier,
  interactive: Boolean = s.enabled,
  radio: Boolean = false,
  leadingContent: (@Composable () -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit
) {
  val supporting = supportingOf(s)
  if (interactive) {
    val onClick = remember(r, row) { { r.click(row) } }
    val onLongClick = remember(r, row) { { r.longClick(row) } }
    val colors = tgxRowColors(dimmed = s.dimmed)
    if (radio) {
      SegmentedListItem(
        selected = s.checked,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supporting,
        onLongClick = if (s.longClickable) onLongClick else null,
        colors = colors,
        content = content
      )
    } else {
      SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supporting,
        onLongClick = if (s.longClickable) onLongClick else null,
        colors = colors,
        content = content
      )
    }
  } else {
    SegmentedListItem(
      shapes = shapes,
      modifier = modifier,
      enabled = !s.dimmed,
      leadingContent = leadingContent,
      trailingContent = trailingContent,
      supportingContent = supporting,
      colors = settingsRowColors(),
      content = content
    )
  }
}

/** The row colors; [dimmed]: a row that looks disabled but still takes taps keeps its tile, its content is dimmed. */
@Composable
private fun tgxRowColors(dimmed: Boolean): ListItemColors {
  if (!dimmed) {
    return settingsRowColors()
  }
  val cs = MaterialTheme.colorScheme
  val off = cs.onSurface.copy(alpha = DisabledContentAlpha)
  return ListItemDefaults.segmentedColors(
    containerColor = cs.surfaceBright,
    contentColor = off,
    leadingContentColor = off,
    trailingContentColor = off,
    overlineContentColor = off,
    supportingContentColor = off,
    disabledContainerColor = cs.surfaceBright
  )
}

/** M3's disabled content and container opacities. */
private const val DisabledContentAlpha = 0.38f
private const val DisabledContainerAlpha = 0.12f

private fun toggleStateOf(s: TgxRowSnap): ToggleableState = when {
  !s.checked -> ToggleableState.Off
  s.partial -> ToggleableState.Indeterminate
  else -> ToggleableState.On
}

/** The leading icon of a Telegram X row: a tonal circle (as Telegram X rows have with Expressive design). */
private fun leadingOf(s: TgxRowSnap): (@Composable () -> Unit)? =
  if (s.icon != 0) {
    { TgxRowIcon(s.icon, s.iconRotated, s.destructive, !s.dimmed) }
  } else {
    null
  }

@Composable
private fun TgxRowIcon(icon: Int, rotated: Boolean, destructive: Boolean, enabled: Boolean) {
  val cs = MaterialTheme.colorScheme
  val angle by animateFloatAsState(if (rotated) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec<Float>(), label = "tgxIcon")
  val container = when {
    !enabled -> cs.onSurface.copy(alpha = DisabledContainerAlpha)
    destructive -> cs.errorContainer
    else -> cs.primaryContainer
  }
  val tint = when {
    !enabled -> cs.onSurface.copy(alpha = DisabledContentAlpha)
    destructive -> cs.onErrorContainer
    else -> cs.onPrimaryContainer
  }
  Box(
    modifier = Modifier.size(40.dp).clip(CircleShape).background(container),
    contentAlignment = Alignment.Center
  ) {
    Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(24.dp).rotate(angle), tint = tint)
  }
}

/** A row's text with Telegram X's bold and italic (Spanned), or plain. */
@Composable
private fun TgxText(text: CharSequence?, color: Color = Color.Unspecified) {
  if (text is Spanned) {
    val styled = remember(text) { TgxSpans.styled(text) }
    Text(styled, color = color)
  } else {
    Text(text?.toString().orEmpty(), color = color)
  }
}

/** The title's color: red for Telegram X's destructive rows, primary for its text buttons; dimmed rows keep theirs. */
@Composable
private fun TgxTitle(s: TgxRowSnap) {
  val cs = MaterialTheme.colorScheme
  val color = when {
    s.dimmed -> Color.Unspecified
    s.destructive -> cs.error
    s.kind == TgxRowKind.BUTTON -> cs.primary
    else -> Color.Unspecified
  }
  TgxText(s.title, color)
}

/** The value line, in the role of Telegram X's value color (red: error, accent: primary). */
private fun supportingOf(s: TgxRowSnap): (@Composable () -> Unit)? {
  val subtitle = s.subtitle ?: return null
  return { TgxText(subtitle, toneColor(s)) }
}

@Composable
private fun toneColor(s: TgxRowSnap): Color {
  if (s.dimmed) {
    return Color.Unspecified
  }
  return when (s.tone) {
    TgxTone.ERROR -> MaterialTheme.colorScheme.error
    TgxTone.PRIMARY -> MaterialTheme.colorScheme.primary
    else -> Color.Unspecified
  }
}

/** A row's progress (a download, a request): Telegram X's spinner as an M3 Expressive loading indicator. */
private val TgxBusy: @Composable () -> Unit = {
  ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
}

/**
 * The switch of a row; the row (or its own tap area) is the toggle target, so the switch takes no clicks. Its thumb
 * shows Telegram X's lock while the option can't be changed, and a dash while only part of a group is on.
 */
@Composable
private fun TgxSwitch(checked: Boolean, partial: Boolean, locked: Boolean, enabled: Boolean) {
  Switch(
    checked = checked,
    onCheckedChange = null,
    enabled = enabled,
    thumbContent = {
      when {
        locked -> Icon(tgxIcon(R.drawable.baseline_lock_24), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
        partial -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(LocalContentColor.current, CircleShape))
        else -> Icon(
          tgxIcon(if (checked) R.drawable.baseline_check_24 else R.drawable.baseline_close_24),
          contentDescription = null,
          modifier = Modifier.size(SwitchDefaults.IconSize)
        )
      }
    }
  )
}

@Composable
private fun TgxSwitchRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val switch: @Composable () -> Unit = { TgxSwitch(s.checked, s.partial, s.locked, !s.dimmed && !s.toggleDisabled) }
  TgxListItem(
    r, row, s, shapes,
    modifier = modifier.semantics {
      role = Role.Switch
      toggleableState = toggleStateOf(s)
    },
    interactive = s.enabled && !s.busy,
    leadingContent = leadingOf(s),
    trailingContent = if (s.busy) TgxBusy else switch
  ) {
    TgxTitle(s)
  }
}

/**
 * A switch row with two targets (EditRights' Send media group): the row expands or opens, the switch after a divider
 * toggles the whole group. A locked switch still takes its tap, so Telegram X can explain why in a tooltip.
 */
@Composable
private fun TgxSplitSwitchRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val onToggle = remember(r, row) { { r.clickToggler(row) } }
  val divider = MaterialTheme.colorScheme.outlineVariant
  TgxListItem(
    r, row, s, shapes, modifier,
    leadingContent = leadingOf(s),
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(32.dp).background(divider))
        Box(
          modifier = Modifier
            .padding(start = 12.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(enabled = s.enabled, role = Role.Switch, onClick = onToggle)
            .semantics { toggleableState = toggleStateOf(s) },
          contentAlignment = Alignment.Center
        ) {
          TgxSwitch(s.checked, s.partial, s.locked, !s.dimmed && !s.toggleDisabled)
        }
      }
    }
  ) {
    TgxTitle(s)
  }
}

@Composable
private fun TgxRadioRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val enabled = !s.dimmed
  TgxListItem(
    r, row, s, shapes,
    modifier = modifier.semantics { role = Role.RadioButton },
    radio = true,
    leadingContent = { RadioButton(selected = s.checked, onClick = null, enabled = enabled, colors = tgxRadioColors(s)) },
    // A language pack downloading, a pack being applied.
    trailingContent = if (s.busy) TgxBusy else null
  ) {
    TgxTitle(s)
  }
}

/**
 * A radio in its own color (Themes: each theme's color): the ring in that color, the selected dot in primary, or in
 * that color too when Telegram X applies it (RadioView.setApplyColor).
 */
@Composable
private fun tgxRadioColors(s: TgxRowSnap): RadioButtonColors {
  if (s.radioColor == 0) {
    return RadioButtonDefaults.colors()
  }
  val own = Color(s.radioColor)
  return RadioButtonDefaults.colors(
    selectedColor = if (s.radioApplied) own else MaterialTheme.colorScheme.primary,
    unselectedColor = own
  )
}

@Composable
private fun TgxCheckboxRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val state = toggleStateOf(s)
  TgxListItem(
    r, row, s, shapes,
    modifier = modifier.semantics {
      role = Role.Checkbox
      toggleableState = state
    },
    leadingContent = { TriStateCheckbox(state = state, onClick = null, enabled = !s.dimmed && !s.toggleDisabled) },
    trailingContent = if (s.busy) TgxBusy else null
  ) {
    TgxTitle(s)
  }
}

@Composable
private fun TgxValueRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val view = row.holder?.itemView
  val trailing = if (s.busy) TgxBusy else if (view != null) r.hints.trailing(row.item, view) else null
  TgxListItem(r, row, s, shapes, modifier, leadingContent = leadingOf(s), trailingContent = trailing) {
    TgxTitle(s)
  }
}

/** While the thumb is dragged: where it is (NaN: not dragged, the page's value shows). Not a row state: per frame. */
private class TgxSliderDrag {
  val position = mutableFloatStateOf(Float.NaN)
}

/**
 * A stepped slider. While it is dragged every new step goes to the page at once (Telegram X's pages follow their
 * sliders live), and the value line follows the thumb; only the slider itself recomposes per frame.
 */
@Composable
private fun TgxSliderRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val labels = s.sliderLabels
  val drag = remember(row) { TgxSliderDrag() }
  val pageIndex = rememberUpdatedState(s.sliderIndex)
  // Changes per step, not per frame: the label and its semantics recompose only then.
  val shown by remember(drag) {
    derivedStateOf {
      val p = drag.position.floatValue
      if (p.isNaN()) pageIndex.value else p.roundToInt()
    }
  }
  val label = labels.getOrElse(shown) { "" }
  val sliderModifier = remember(label) {
    Modifier.fillMaxWidth().xgramNoSwipeBack().semantics { stateDescription = label }
  }
  val title = s.title
  // Pages that show only the value (EditChatLinkController) have no title: the value is the row's headline.
  val hasTitle = !title.isNullOrEmpty()
  SegmentedListItem(
    shapes = shapes,
    modifier = modifier,
    enabled = s.enabled,
    supportingContent = {
      if (hasTitle) {
        Column {
          Text(label)
          TgxSlider(r, row, labels.size, pageIndex, s.enabled, drag, sliderModifier)
        }
      } else {
        TgxSlider(r, row, labels.size, pageIndex, s.enabled, drag, sliderModifier)
      }
    },
    colors = settingsRowColors(),
    content = {
      if (hasTitle) {
        TgxText(title)
      } else {
        Text(label)
      }
    }
  )
}

@Composable
private fun TgxSlider(
  r: TgxSettingsRenderer,
  row: TgxRow,
  count: Int,
  pageIndex: State<Int>,
  enabled: Boolean,
  drag: TgxSliderDrag,
  modifier: Modifier
) {
  val last = (count - 1).coerceAtLeast(1)
  val range = remember(last) { 0f..last.toFloat() }
  val onChange = remember(r, row, drag, count) {
    { value: Float ->
      drag.position.floatValue = value
      r.slideTo(row, value.roundToInt().coerceIn(0, maxOf(count - 1, 0)))
    }
  }
  val onFinished = remember(r, row, drag, count) {
    {
      val p = drag.position.floatValue
      val at = if (p.isNaN()) pageIndex.value.toFloat() else p
      r.slideEnd(row, at.roundToInt().coerceIn(0, maxOf(count - 1, 0)))
      drag.position.floatValue = Float.NaN
    }
  }
  val p = drag.position.floatValue
  Slider(
    value = (if (p.isNaN()) pageIndex.value.toFloat() else p).coerceIn(0f, last.toFloat()),
    onValueChange = onChange,
    modifier = modifier,
    enabled = enabled,
    valueRange = range,
    steps = (count - 2).coerceAtLeast(0),
    onValueChangeFinished = onFinished
  )
}

// Rows on the page

/**
 * A header with a control at its end (Telegram X's HEADER_WITH_*): an icon button, a text button, or a checkbox that
 * selects the whole list below (with its third state while only part of it is selected). The control is the header's
 * real one: its tap reaches the page's onClick with the real view.
 */
@Composable
private fun TgxHeaderRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, top: Dp, placed: Modifier) {
  val cs = MaterialTheme.colorScheme
  val onClick = remember(r, row) { { r.click(row) } }
  // The 48dp control replaces the header's bottom space and part of its top, so the title sits where a plain
  // header's does.
  Row(
    modifier = placed.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = maxOf(top - 14.dp, 0.dp)),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      s.title?.toString().orEmpty(),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.titleSmall,
      color = cs.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    when (s.action) {
      TgxHeaderAction.ICON -> IconButton(onClick = onClick, enabled = s.enabled) {
        Icon(tgxIcon(s.icon), contentDescription = null, tint = if (s.enabled) cs.onSurfaceVariant else cs.onSurface.copy(alpha = DisabledContentAlpha))
      }
      TgxHeaderAction.TEXT -> TextButton(onClick = onClick, enabled = s.enabled) {
        Text(s.subtitle?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      TgxHeaderAction.CHECKBOX -> TriStateCheckbox(state = toggleStateOf(s), onClick = onClick, enabled = s.enabled && !s.toggleDisabled)
      else -> Unit
    }
  }
}

/** A description with Telegram X's styles; its links tap the real spans (on the bound text view). */
@Composable
private fun TgxDescription(row: TgxRow, s: TgxRowSnap) {
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
