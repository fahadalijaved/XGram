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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramM3Bars
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.appearance.XgramR5Lists
import com.fahadali.xgram.appearance.XgramR6B5Pages
import com.fahadali.xgram.appearance.XgramShapes
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    // The page's content color (onSurface on surfaceContainer, as SettingsPage's Scaffold gives): ripples of controls
    // on the page itself (header buttons, the build number) are the M3 content color, not the default black.
    CompositionLocalProvider(
      LocalXgramHost provides r.composeHost,
      LocalLayoutDirection provides direction,
      LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
      val structure = r.structureVersion.intValue
      val blocks = remember(structure, lang) { r.buildBlocks() }
      val density = LocalDensity.current
      val padding = remember(r, density) { TgxListPadding(r, density) }
      Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.TopCenter
      ) {
        // A form's column stops at 480dp (XgramR5People.FORM_MAX_WIDTH), a page's at 720dp
        SettingsList(blocks, r.listState, padding, modifier = if (r.formWidth) Modifier.widthIn(max = 480.dp) else Modifier, animateItems = true)
      }
      val request = r.scrollRequest.intValue
      LaunchedEffect(request, blocks) {
        r.applyPendingScroll()
      }
      LaunchedEffect(r) {
        snapshotFlow {
          if (r.listState.firstVisibleItemIndex == 0) r.listState.firstVisibleItemScrollOffset else Int.MAX_VALUE
        }.collect { offset ->
          r.onListScrolled(offset)
        }
      }
      val pulse = r.pulseRequest.intValue
      if (pulse > 0) {
        // A settings search result: once the list is at the row, it pulses twice (held once with Reduce motion)
        val pulseSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
        LaunchedEffect(pulse) {
          r.applyPendingScroll()
          val row = r.pulseRow.value
          val view = row?.holder?.itemView
          if (row != null && row.kind == TgxRowKind.FALLBACK && view != null) {
            view.isPressed = true
            delay(650)
            view.isPressed = false
          } else if (row != null) {
            val progress = r.pulseProgress
            if (XgramMotion.reduce()) {
              progress.snapTo(1f)
              delay(1200)
              progress.snapTo(0f)
            } else {
              repeat(2) {
                progress.animateTo(1f, pulseSpec)
                progress.animateTo(0f, pulseSpec)
              }
            }
          }
          r.pulseRow.value = null
        }
      }
      if (r.largeTitle) {
        // A title the list left half collapsed snaps to the nearer end (as the classic list and the Compose app bar)
        val snapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        LaunchedEffect(r, snapSpec) {
          snapshotFlow { r.listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
              // Its own coroutine: a finger taking over cancels the snap, never this effect
              launch { r.snapTitle(snapSpec) }
            }
          }
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

/**
 * The page title as the list's first item, where XgramM3Bars.LargeTitleDecoration draws it in the classic list:
 * displaySmall as SettingsTopAppBar(large = true) (headlineMedium for a page whose hints ask for TITLE_MEDIUM), with the
 * page's bodyMedium subtitle under it. It fades out over the first 60% of its own height as the list scrolls, while
 * Telegram X's small title fades in (TgxSettingsRenderer.onListScrolled); the scroll is read in the layer only.
 */
@Composable
internal fun TgxLargeTitle(r: TgxSettingsRenderer) {
  r.langVersion.intValue
  val name = r.controller.name?.toString().orEmpty()
  val subtitle = r.hints.largeSubtitle(r.controller)?.toString()?.takeIf { it.isNotEmpty() }
  val medium = r.titleStyle == XgramM3Bars.TITLE_MEDIUM
  val type = MaterialTheme.typography
  val cs = MaterialTheme.colorScheme
  val fade = remember(r) {
    Modifier
      .onSizeChanged { r.titleHeightPx = it.height }
      .graphicsLayer {
        val state = r.listState
        val offset = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset else Int.MAX_VALUE
        alpha = (1f - r.collapseOf(offset) / TgxSettingsRenderer.TITLE_FADE_SPAN).coerceIn(0f, 1f)
      }
  }
  Column(
    // The title's last baseline sits where the classic list's does: 64dp of page under the header, 96dp with a subtitle
    modifier = Modifier.fillMaxWidth().then(fade).padding(top = if (medium) 8.dp else 0.dp, bottom = if (subtitle != null) 24.dp else 12.dp)
  ) {
    Text(
      name,
      modifier = Modifier.semantics { heading() },
      style = if (medium) type.headlineMedium else type.displaySmall,
      color = cs.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    if (subtitle != null) {
      Text(subtitle, style = type.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}

// Shared row content for the pages' hints (TgxPagesBatch1..3)

/**
 * A chat's avatar as a row's 40dp leading content (the M3 list item's leading avatar): Telegram X's avatar receiver in
 * a small view, [bind] asks it for the chat (it skips a chat it already shows).
 */
@Composable
internal fun TgxChatAvatar(bind: (XgramR6B5Pages.AvatarView) -> Unit) {
  AndroidView(
    factory = { context -> XgramR6B5Pages.AvatarView(context) },
    modifier = Modifier.size(40.dp),
    update = { view -> bind(view) },
    onRelease = { view -> view.destroy() }
  )
}

/** A row's trailing time or count: labelMedium in onSurfaceVariant, tabular figures. */
@Composable
internal fun TgxTrailingLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1
  )
}

/** A trailing badge chip: labelSmall with full corners in [container] ("Deleted" in errorContainer). */
@Composable
internal fun TgxBadge(text: String, container: Color, content: Color) {
  Box(
    modifier = Modifier.heightIn(min = 20.dp).clip(CircleShape).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
  }
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
      TgxRowKind.EMPTY_ICON -> TgxEmptyState(s.icon, s.title, s.subtitle)
      // A page's call to action on the page itself: centred, filled (primary for the page's main action) or tonal
      TgxRowKind.BUTTON -> TgxCallToAction(r, row, s, placed)
      TgxRowKind.LOADING -> SettingsLoading()
      // The ripple stays in a card-shaped area (CornerLarge), not a full-bleed rectangle.
      TgxRowKind.BUILD_NO -> Text(
        s.title?.toString().orEmpty(),
        modifier = placed.fillMaxWidth().padding(bottom = 12.dp).clip(MaterialTheme.shapes.large).clickable(enabled = s.enabled) { r.click(row) }
          .padding(vertical = 12.dp),
        // labelMedium with tabular figures, 24dp of page below it
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
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
    TgxRowKind.CHAT -> TgxChatRow(r, row, s, shapes, placed)
    TgxRowKind.SLIDER -> TgxSliderRow(r, row, s, shapes, placed)
    TgxRowKind.HIDDEN -> Unit
    TgxRowKind.HEADER -> SettingsHeader(s.title?.toString().orEmpty(), 0, 8.dp)
    TgxRowKind.HEADER_ACTION -> TgxHeaderRow(r, row, s, 8.dp, placed)
    TgxRowKind.DESCRIPTION -> TgxDescription(row, s)
    TgxRowKind.LOADING -> SettingsLoading()
    TgxRowKind.EMPTY_ICON -> TgxEmptyState(s.icon, s.title, s.subtitle)
    TgxRowKind.EMPTY, TgxRowKind.BUILD_NO, TgxRowKind.FALLBACK -> TgxFallback(
      r, row, placed.clip(shapes.shape).background(MaterialTheme.colorScheme.surfaceBright), segment
    )
  }
}

/**
 * An empty state with an icon, as XgramListStateView draws it in the classic list: the icon (48dp, onPrimaryContainer)
 * on a 112dp soft burst in primaryContainer, the headline in titleLarge, the text in bodyMedium onSurfaceVariant,
 * centred and at most 320dp wide.
 */
@Composable
private fun TgxEmptyState(icon: Int, title: CharSequence?, text: CharSequence?) {
  val cs = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val burst = remember(density) {
    val sizePx = with(density) { 112.dp.toPx() }
    val path = android.graphics.Path()
    XgramShapes.toPath(XgramShapes.SOFT_BURST, path)
    XgramShapes.fit(path, sizePx / 2f, sizePx / 2f, sizePx, 0f, android.graphics.Matrix())
    path.asComposePath()
  }
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val container = cs.primaryContainer
    Box(
      modifier = Modifier.size(112.dp).drawBehind { drawPath(burst, container) },
      contentAlignment = Alignment.Center
    ) {
      if (icon != 0) {
        Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(48.dp), tint = cs.onPrimaryContainer)
      }
    }
    if (!title.isNullOrEmpty()) {
      Spacer(Modifier.height(16.dp))
      Text(
        title.toString(),
        modifier = Modifier.widthIn(max = 320.dp),
        style = MaterialTheme.typography.titleLarge,
        color = cs.onSurface,
        textAlign = TextAlign.Center
      )
    }
    if (!text.isNullOrEmpty()) {
      Spacer(Modifier.height(if (title.isNullOrEmpty()) 16.dp else 8.dp))
      val styled = remember(text) { if (text is Spanned) TgxSpans.styled(text) else AnnotatedString(text.toString()) }
      Text(
        styled,
        modifier = Modifier.widthIn(max = 320.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

/** Telegram X's full-width text button as an M3 filled button (40dp, full corners morphing on press), centred. */
@Composable
private fun TgxCallToAction(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, placed: Modifier) {
  val onClick = remember(r, row) { { r.click(row) } }
  val label = s.title?.toString().orEmpty()
  Box(modifier = placed.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
    if (XgramR5Lists.isPrimaryButton(row.item.id)) {
      Button(onClick = onClick, shapes = ButtonDefaults.shapes(), enabled = s.enabled, modifier = Modifier.widthIn(max = 400.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    } else {
      FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), enabled = s.enabled, modifier = Modifier.widthIn(max = 400.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
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
    val colors = tgxRowColors(r, row, dimmed = s.dimmed)
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
      colors = settingsRowColors(container = pulseContainer(r, row)),
      content = content
    )
  }
}

/**
 * The tile color of [row]: surfaceBright, toward secondaryContainer while a settings search result pulses it (only
 * the pulsed row reads the progress, so only it recomposes per frame).
 */
@Composable
private fun pulseContainer(r: TgxSettingsRenderer, row: TgxRow): Color {
  val base = MaterialTheme.colorScheme.surfaceBright
  if (r.pulseRow.value !== row) {
    return base
  }
  val p = r.pulseProgress.value
  return if (p > 0f) lerp(base, MaterialTheme.colorScheme.secondaryContainer, p) else base
}

/** The row colors; [dimmed]: a row that looks disabled but still takes taps keeps its tile, its content is dimmed. */
@Composable
private fun tgxRowColors(r: TgxSettingsRenderer, row: TgxRow, dimmed: Boolean): ListItemColors {
  val container = pulseContainer(r, row)
  if (!dimmed) {
    return settingsRowColors(container = container)
  }
  val cs = MaterialTheme.colorScheme
  val off = cs.onSurface.copy(alpha = DisabledContentAlpha)
  return ListItemDefaults.segmentedColors(
    containerColor = container,
    contentColor = off,
    leadingContentColor = off,
    trailingContentColor = off,
    overlineContentColor = off,
    supportingContentColor = off,
    disabledContainerColor = container
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

/**
 * A row's text with Telegram X's bold and italic (Spanned), or plain; cut with an ellipsis after [maxLines], in
 * tabular figures when [tabular] (IDs, times). The list item's own style (bodyLarge, bodyMedium) is kept.
 */
@Composable
private fun TgxText(text: CharSequence?, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, tabular: Boolean = false) {
  val style = if (tabular) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current
  val overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
  if (text is Spanned) {
    val styled = remember(text) { TgxSpans.styled(text) }
    Text(styled, color = color, maxLines = maxLines, overflow = overflow, style = style)
  } else {
    Text(text?.toString().orEmpty(), color = color, maxLines = maxLines, overflow = overflow, style = style)
  }
}

/** The title's color: red for Telegram X's destructive rows, primary for its text buttons; dimmed rows keep theirs. */
@Composable
private fun TgxTitle(s: TgxRowSnap, maxLines: Int = Int.MAX_VALUE) {
  val cs = MaterialTheme.colorScheme
  val color = when {
    s.dimmed -> Color.Unspecified
    s.destructive -> cs.error
    s.kind == TgxRowKind.BUTTON -> cs.primary
    else -> Color.Unspecified
  }
  TgxText(s.title, color, maxLines)
}

/**
 * The value line, in the role of Telegram X's value color (red: error, accent: primary), with the page's line limit
 * and figures; a shimmer bar while the page is still loading it.
 */
private fun supportingOf(s: TgxRowSnap): (@Composable () -> Unit)? {
  if (s.textLoading) {
    return TgxShimmerLine
  }
  val subtitle = s.subtitle ?: return null
  return { TgxText(subtitle, toneColor(s), s.subtitleLines, s.tabular) }
}

private val TgxShimmerLine: @Composable () -> Unit = { TgxShimmer() }

/**
 * A supporting line that is still loading: a 12dp bar with full corners in onSurfaceVariant whose opacity breathes
 * (drawn in the draw phase only); still with Reduce motion.
 */
@Composable
private fun TgxShimmer() {
  val base = MaterialTheme.colorScheme.onSurfaceVariant
  val still = remember { XgramMotion.reduce() }
  val pulse = if (still) null else rememberInfiniteTransition(label = "tgxShimmer").animateFloat(
    initialValue = .08f,
    targetValue = .20f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "tgxShimmerAlpha"
  )
  Box(
    modifier = Modifier
      .padding(vertical = 4.dp)
      .size(width = 168.dp, height = 12.dp)
      .clip(CircleShape)
      .drawBehind { drawRect(base.copy(alpha = pulse?.value ?: .14f)) }
  )
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
 * The trailing content of a row: the busy indicator, the page's progress ring ([TgxRowText.progress]) or what the page
 * puts there.
 */
private fun trailingOf(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap): (@Composable () -> Unit)? {
  if (s.busy) {
    return TgxBusy
  }
  val progress = s.progress
  if (progress != -1f) {
    return { TgxProgressRing(progress) }
  }
  val view = row.holder?.itemView ?: return null
  return r.hints.trailing(r.controller, row.item, view)
}

/** A download's progress as a 24dp M3E wavy ring; indeterminate while it is unknown (NaN), an install. */
@Composable
private fun TgxProgressRing(progress: Float) {
  if (progress.isNaN()) {
    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
  } else {
    CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp))
  }
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
    // A language pack downloading, a pack being applied; else the page's preview (an emoji set's)
    trailingContent = trailingOf(r, row, s)
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
  val leading = (if (view != null) r.hints.leading(r.controller, row.item, view) else null) ?: leadingOf(s)
  TgxListItem(r, row, s, shapes, modifier, leadingContent = leading, trailingContent = trailingOf(r, row, s)) {
    TgxTitle(s)
  }
}

/**
 * A chat, or a message of one ([TgxRowKind.CHAT]): the page's leading avatar (the row's icon as a tonal circle
 * without one), a one-line bodyLarge headline, the supporting text with the page's line limit, the page's trailing
 * date or badge. Taps and long presses reach the real row.
 */
@Composable
private fun TgxChatRow(r: TgxSettingsRenderer, row: TgxRow, s: TgxRowSnap, shapes: ListItemShapes, modifier: Modifier) {
  val view = row.holder?.itemView
  val leading = (if (view != null) r.hints.leading(r.controller, row.item, view) else null) ?: leadingOf(s)
  TgxListItem(r, row, s, shapes, modifier, leadingContent = leading, trailingContent = trailingOf(r, row, s)) {
    TgxTitle(s, maxLines = 1)
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
    colors = settingsRowColors(container = pulseContainer(r, row)),
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
