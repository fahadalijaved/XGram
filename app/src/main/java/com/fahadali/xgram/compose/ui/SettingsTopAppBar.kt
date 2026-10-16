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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The top app bar of a settings page: Material 3 Expressive large flexible (medium flexible with [large] false), driven
 * by TopAppBarDefaults.exitUntilCollapsedScrollBehavior through the page's nested scroll.
 *
 * Sizes, type and colors are the M3E tokens of LargeFlexibleTopAppBar and MediumFlexibleTopAppBar: 64dp collapsed;
 * 120dp expanded (152dp with a subtitle) for large, 112dp (136dp) for medium; the expanded title in displaySmall
 * (large) or headlineMedium (medium) with its last baseline 28dp (24dp) above the bar's bottom, 16dp from the start;
 * the collapsed title in titleLarge after the navigation icon; subtitles in titleMedium / labelLarge, collapsed
 * labelMedium; onSurface title and navigation icon, onSurfaceVariant subtitle and actions.
 *
 * As the bar collapses the one title glides and scales from the expanded row into the collapsed row (the way
 * Android 16 Settings' collapsing toolbar does it), so there is always one readable title: it never slides under the
 * navigation row and it is never clipped. A title that wraps, or would not fit next to the navigation icon, fades out
 * on its way up while the one-line titleLarge title fades in. The container lerps from the page's color (surfaceContainer)
 * to one tone above it (surfaceContainerHigh) as the bar collapses, as the M3 bar does from surface to surfaceContainer,
 * so content scrolled under the collapsed bar sits below a tonal band. Every frame of a collapse is layout and layer
 * properties only: nothing is composed or allocated.
 */
@Composable
internal fun SettingsTopAppBar(
  title: String,
  subtitle: String?,
  large: Boolean,
  scrollBehavior: TopAppBarScrollBehavior,
  topInset: () -> Int,
  navigationIcon: @Composable () -> Unit,
  actions: @Composable RowScope.() -> Unit
) {
  val cs = MaterialTheme.colorScheme
  val type = MaterialTheme.typography
  val state = scrollBehavior.state
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

  val titleStyle = if (large) type.displaySmall else type.headlineMedium
  val subtitleStyle = if (large) type.titleMedium else type.labelLarge
  val smallTitleStyle = type.titleLarge
  val smallSubtitleStyle = type.labelMedium
  val titleScale = fontRatio(smallTitleStyle.fontSize.value, titleStyle.fontSize.value, smallTitleStyle.fontSize.isSp && titleStyle.fontSize.isSp)
  val subtitleScale = fontRatio(
    smallSubtitleStyle.fontSize.value, subtitleStyle.fontSize.value, smallSubtitleStyle.fontSize.isSp && subtitleStyle.fontSize.isSp
  )

  val collapsedHeight = if (large) TopAppBarDefaults.LargeAppBarCollapsedHeight else TopAppBarDefaults.MediumAppBarCollapsedHeight
  val expandedHeight = when {
    large && subtitle != null -> TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
    large -> TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
    subtitle != null -> TopAppBarDefaults.MediumFlexibleAppBarWithSubtitleExpandedHeight
    else -> TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight
  }
  val bottomPadding = if (large) LargeTitleBottomPadding else MediumTitleBottomPadding

  val morph = remember { BarMorph() }
  val origin = if (rtl) TransformOrigin(1f, 0f) else TransformOrigin(0f, 0f)
  // Layer blocks read the collapse in the layer phase: a collapse frame changes layer properties, nothing recomposes.
  val titleLayer = remember(state, morph, titleScale, origin) { scaleLayer(state, morph, titleScale, origin) }
  val subtitleLayer = remember(state, morph, subtitleScale, origin) { scaleLayer(state, morph, subtitleScale, origin) }
  val smallLayer = remember(state, morph) {
    val block: GraphicsLayerScope.() -> Unit = {
      alpha = if (morph.crossfade) fadeIn(state.collapsedFraction) else 0f
    }
    block
  }

  val policy = remember(
    state, morph, topInset, collapsedHeight, expandedHeight, bottomPadding, titleScale, subtitleScale,
    titleLayer, subtitleLayer, smallLayer
  ) {
    settingsBarMeasurePolicy(
      state, morph, topInset, collapsedHeight, expandedHeight, bottomPadding, titleScale, subtitleScale,
      titleLayer, subtitleLayer, smallLayer
    )
  }

  // Dragging the bar itself collapses or expands it, then it settles as TopAppBar's own drag does.
  val dragState = rememberDraggableState { delta -> state.heightOffset += delta }
  val rest = cs.surfaceContainer
  val scrolled = cs.surfaceContainerHigh

  Layout(
    content = {
      Box(Modifier.layoutId(BarNavigation).padding(start = HorizontalPadding)) {
        CompositionLocalProvider(LocalContentColor provides cs.onSurface, content = navigationIcon)
      }
      Row(
        modifier = Modifier.layoutId(BarActions).padding(end = HorizontalPadding),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant) {
          actions()
        }
      }
      Text(
        title,
        modifier = Modifier.layoutId(BarTitle).semantics { heading() },
        color = cs.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = titleStyle
      )
      if (subtitle != null) {
        Text(
          subtitle,
          modifier = Modifier.layoutId(BarSubtitle),
          color = cs.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = subtitleStyle
        )
      }
      // The one-line collapsed title, shown only for titles that cannot glide into the row (see BarMorph).
      Text(
        title,
        modifier = Modifier.layoutId(BarSmallTitle).clearAndSetSemantics { },
        color = cs.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = smallTitleStyle
      )
      if (subtitle != null) {
        Text(
          subtitle,
          modifier = Modifier.layoutId(BarSmallSubtitle).clearAndSetSemantics { },
          color = cs.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = smallSubtitleStyle
        )
      }
    },
    modifier = Modifier
      .fillMaxWidth()
      .then(
        if (!scrollBehavior.isPinned) {
          Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
              settleBar(state, velocity, scrollBehavior.flingAnimationSpec, scrollBehavior.snapAnimationSpec)
            }
          )
        } else {
          Modifier
        }
      )
      .drawBehind { drawRect(lerp(rest, scrolled, FastOutLinearInEasing.transform(state.collapsedFraction))) }
      // The bar takes the touches over it: the rows scrolled under it are not tapped through it.
      .pointerInput(Unit) { }
      .clipToBounds(),
    measurePolicy = policy
  )
}

/**
 * Whether the title cannot glide into the collapsed row (it wraps, or is wider than the room next to the navigation
 * icon once scaled): it then fades out on the way up and the one-line collapsed title fades in. Written while the bar
 * is measured, read by the layer blocks.
 */
@Stable
private class BarMorph {
  var crossfade by mutableStateOf(false)
}

private fun settingsBarMeasurePolicy(
  state: TopAppBarState,
  morph: BarMorph,
  topInset: () -> Int,
  collapsedHeight: Dp,
  expandedHeight: Dp,
  bottomPadding: Dp,
  titleScale: Float,
  subtitleScale: Float,
  titleLayer: GraphicsLayerScope.() -> Unit,
  subtitleLayer: GraphicsLayerScope.() -> Unit,
  smallLayer: GraphicsLayerScope.() -> Unit
): MeasurePolicy = MeasurePolicy { measurables, constraints ->
  val width = constraints.maxWidth
  val inset = topInset().coerceAtLeast(0)
  val collapsedPx = collapsedHeight.roundToPx()
  val paddingPx = HorizontalPadding.roundToPx()
  val sidePx = TitleSide.roundToPx()

  val navigation = measurables.first { it.layoutId == BarNavigation }.measure(Constraints(maxWidth = width, maxHeight = collapsedPx))
  val actions = measurables.first { it.layoutId == BarActions }
    .measure(Constraints(maxWidth = (width - navigation.width).coerceAtLeast(0), maxHeight = collapsedPx))
  val expandedWidth = (width - 2 * sidePx).coerceAtLeast(0)
  val title = measurables.first { it.layoutId == BarTitle }.measure(Constraints(maxWidth = expandedWidth))
  val subtitle = measurables.firstOrNull { it.layoutId == BarSubtitle }?.measure(Constraints(maxWidth = expandedWidth))
  // M3 TopAppBarLayout: the title starts after the navigation icon, at least 12dp in, plus its own 4dp padding.
  val collapsedStart = max(TitleInset.roundToPx(), navigation.width) + paddingPx
  val collapsedWidth = (width - collapsedStart - actions.width - paddingPx).coerceAtLeast(0)
  val smallTitle = measurables.first { it.layoutId == BarSmallTitle }.measure(Constraints(maxWidth = collapsedWidth))
  val smallSubtitle = measurables.firstOrNull { it.layoutId == BarSmallSubtitle }?.measure(Constraints(maxWidth = collapsedWidth))

  // The expanded row (M3 TwoRowsTopAppBar's bottom row): the last baseline [bottomPadding] above its bottom, moved up
  // as far as needed when the title does not fit; the row grows for a title taller than it (large font sizes).
  val subtitleHeight = subtitle?.height ?: 0
  val blockHeight = title.height + subtitleHeight
  val lastBaseline = if (subtitle != null) title.height + baselineOf(subtitle, LastBaseline) else baselineOf(title, LastBaseline)
  val rowHeight = max((expandedHeight - collapsedHeight).roundToPx(), blockHeight)
  val fromBottom = bottomPadding.roundToPx() - (blockHeight - lastBaseline)
  val fitted = if (fromBottom + blockHeight > rowHeight) fromBottom - (fromBottom + blockHeight - rowHeight) else fromBottom
  val blockTop = rowHeight - blockHeight - max(0, fitted)

  // exitUntilCollapsed hides exactly the expanded row.
  val limit = -rowHeight.toFloat()
  if (state.heightOffsetLimit != limit) {
    state.heightOffsetLimit = limit
  }
  val offset = state.heightOffset.coerceIn(limit, 0f)
  val f = if (rowHeight > 0) -offset / rowHeight else 0f
  val height = inset + collapsedPx + rowHeight + offset.roundToInt()

  val oneLine = baselineOf(title, FirstBaseline) == baselineOf(title, LastBaseline)
  val crossfade = !oneLine || title.width * titleScale > collapsedWidth ||
    (subtitle != null && subtitle.width * subtitleScale > collapsedWidth)
  if (morph.crossfade != crossfade) {
    morph.crossfade = crossfade
  }

  // From the expanded row to the collapsed row, centered in it at the collapsed title's start. The title moves sideways
  // early (FastOutSlowIn) and up linearly, so it clears the navigation icon before it reaches the icon's row; both ends
  // are inside the bar, so the whole way is too.
  val fromTop = (inset + collapsedPx + blockTop).toFloat()
  val toTop = inset + (collapsedPx - (title.height * titleScale + subtitleHeight * subtitleScale)) / 2f
  val fx = FastOutSlowInEasing.transform(f)
  val titleX = (sidePx + (collapsedStart - sidePx) * fx).roundToInt()
  val titleY = fromTop + (toTop - fromTop) * f
  val subtitleY = titleY + title.height * (1f + (titleScale - 1f) * f)
  val smallHeight = smallTitle.height + (smallSubtitle?.height ?: 0)
  val smallTop = inset + (collapsedPx - smallHeight) / 2

  layout(width, height) {
    navigation.placeRelative(0, inset + (collapsedPx - navigation.height) / 2)
    actions.placeRelative(width - actions.width, inset + (collapsedPx - actions.height) / 2)
    smallTitle.placeRelativeWithLayer(collapsedStart, smallTop, layerBlock = smallLayer)
    smallSubtitle?.placeRelativeWithLayer(collapsedStart, smallTop + smallTitle.height, layerBlock = smallLayer)
    title.placeRelativeWithLayer(titleX, titleY.roundToInt(), layerBlock = titleLayer)
    subtitle?.placeRelativeWithLayer(titleX, subtitleY.roundToInt(), layerBlock = subtitleLayer)
  }
}

/**
 * The layer of the expanded title (or subtitle): scaled from 1 to [endScale] (the collapsed type size over the expanded
 * one) about its start edge as the bar collapses; faded out on the way when it cannot glide into the row.
 */
private fun scaleLayer(state: TopAppBarState, morph: BarMorph, endScale: Float, origin: TransformOrigin): GraphicsLayerScope.() -> Unit = {
  val f = state.collapsedFraction
  val s = 1f + (endScale - 1f) * f
  scaleX = s
  scaleY = s
  transformOrigin = origin
  alpha = if (morph.crossfade) fadeOut(f) else 1f
}

/** A Text's baseline, or its bottom when it has none. */
private fun baselineOf(p: Placeable, line: AlignmentLine): Int {
  val b = p[line]
  return if (b == AlignmentLine.Unspecified) p.height else b
}

private fun fontRatio(small: Float, big: Float, valid: Boolean): Float =
  if (valid && big > 0f && small > 0f) small / big else 1f

/** The wrapping title is gone by 60% of the collapse. */
private fun fadeOut(f: Float): Float = (1f - f / 0.6f).coerceIn(0f, 1f)

/** The collapsed title comes in over the last 60%. */
private fun fadeIn(f: Float): Float = ((f - 0.4f) / 0.6f).coerceIn(0f, 1f)

/**
 * After a drag on the bar (TopAppBar's settleAppBar): the fling carries on, then a bar left half way snaps to
 * collapsed or expanded.
 */
private suspend fun settleBar(
  state: TopAppBarState,
  velocity: Float,
  fling: DecayAnimationSpec<Float>?,
  snap: AnimationSpec<Float>?
) {
  if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
    return
  }
  if (fling != null && abs(velocity) > 1f) {
    var last = 0f
    AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(fling) {
      val delta = value - last
      val before = state.heightOffset
      state.heightOffset = before + delta
      val consumed = abs(before - state.heightOffset)
      last = value
      if (abs(delta - consumed) > 0.5f) {
        cancelAnimation()
      }
    }
  }
  if (snap != null && state.heightOffset < 0f && state.heightOffset > state.heightOffsetLimit) {
    AnimationState(initialValue = state.heightOffset).animateTo(
      if (state.collapsedFraction < 0.5f) 0f else state.heightOffsetLimit,
      animationSpec = snap
    ) {
      state.heightOffset = value
    }
  }
}

private const val BarNavigation = "navigation"
private const val BarActions = "actions"
private const val BarTitle = "title"
private const val BarSubtitle = "subtitle"
private const val BarSmallTitle = "smallTitle"
private const val BarSmallSubtitle = "smallSubtitle"

/** M3 AppBar: TopAppBarHorizontalPadding, TopAppBarTitleInset (16dp - 4dp), Large/MediumTitleBottomPadding. */
private val HorizontalPadding: Dp = 4.dp
private val TitleInset: Dp = 12.dp
private val TitleSide: Dp = TitleInset + HorizontalPadding
private val LargeTitleBottomPadding: Dp = 28.dp
private val MediumTitleBottomPadding: Dp = 24.dp
