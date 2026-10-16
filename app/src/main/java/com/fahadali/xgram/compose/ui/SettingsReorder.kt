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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.fahadali.xgram.compose.ui

import android.os.SystemClock
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.thunderdog.challegram.R
import kotlin.math.abs

// A card group whose rows can be dragged into a new order (side menu items, message menu, chat actions): the
// Android 16 Settings look of SettingsGroup, plus a drag handle on every row. Lists are short, so a plain Column.

/**
 * One row of a reorderable group. [checked] null: the row has no switch, it can only move. [enabled] false greys
 * the switch out (it does nothing until another setting is on; [subtitle] can say which); the row still moves.
 */
class ReorderRow(
  val key: String,
  val title: String,
  @DrawableRes val icon: Int = 0,
  val checked: Boolean? = null,
  val enabled: Boolean = true,
  val subtitle: String? = null
)

/** Content type of a reorderable group in the page's list (PageBlock.TYPE_* leave 10 free for it). */
private const val TYPE_REORDER = 10

/** While a row is held, Telegram X's swipe-back stays off everywhere on the page. */
private val NO_SWIPE_EVERYWHERE: () -> android.graphics.Rect? = {
  android.graphics.Rect(Int.MIN_VALUE / 2, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
}

/**
 * A reorderable card group. [id] is the classic rows' id (search and highlight use it with the row's key).
 * [onToggle] gets a row's new switch state, [onDrop] the keys in their new order after a row was moved; both are
 * followed by host.changed(). Rows move only inside their own group.
 */
fun PageScope.reorderGroup(
  key: String,
  id: Int,
  rows: List<ReorderRow>,
  onToggle: (key: String, checked: Boolean) -> Unit = { _, _ -> },
  onDrop: (keys: List<String>) -> Unit
) {
  if (rows.isEmpty()) {
    return
  }
  // Same spacing as PageScope.group: none under a header, 16dp after anything else.
  val gap = groupGap()
  // The rows are listed for the page's highlight lookup (search results scroll to this group); the block draws them.
  // Unlike PageScope.group the group stays one item: rows are dragged inside it.
  val lookup = rows.map { row -> GroupRow(id, row.key, "$id:${row.key}", TYPE_REORDER) { _ -> } }
  add("r:$key", TYPE_REORDER, lookup) { ReorderGroupBlock(id, rows, gap, onToggle, onDrop) }
}

private const val OWNER_HANDLE = 1
private const val OWNER_ROW = 2

/** Drag state of one group. Changed on the main thread only (pointer input, frame callbacks). */
@Stable
private class ReorderState(val host: XgramComposeHost, val scope: CoroutineScope) {
  /** The order while a row is being dragged; null: the order the page passes in. */
  var local by mutableStateOf<List<String>?>(null)
  var dragged by mutableStateOf<String?>(null)
  /** The row gliding into its slot after it was let go. */
  var settling by mutableStateOf<String?>(null)
  /** Offset of the dragged (or settling) row from its slot. */
  var dragOffset by mutableFloatStateOf(0f)
  var owner = 0
  private var droppedAt = 0L
  private var moved = false
  /** How far the finger moved the held row (not counting scrolling): picking a row up never scrolls by itself. */
  private var travel = 0f
  private var settleJob: Job? = null

  /**
   * Where each row is laid out. Only the coordinates are kept as rows are placed (scrolling places them every frame);
   * sizes and positions are read from them while a row is held.
   */
  private val slots = HashMap<String, Slot>()
  private val offsets = HashMap<String, MutableFloatState>()
  private val jobs = HashMap<String, Job>()

  // Updated after every composition.
  var haptic: HapticFeedback? = null
  var spec: FiniteAnimationSpec<Float> = spring()
  var gapPx = 0f
  var incoming: List<String> = emptyList()
  var onDrop: (List<String>) -> Unit = {}

  fun offset(key: String): MutableFloatState = offsets.getOrPut(key) { mutableFloatStateOf(0f) }

  fun slot(key: String): Slot = slots.getOrPut(key) { Slot() }

  private fun attached(key: String): LayoutCoordinates? = slots[key]?.coordinates?.takeIf { it.isAttached }

  /** The row's height in px, null while it is not laid out. */
  private fun height(key: String): Int? = attached(key)?.size?.height

  /** The top of the row's slot in the window (before the drag offset), null while it is not laid out. */
  private fun slotTop(key: String): Float? = attached(key)?.positionInWindow()?.y

  fun isMoving(key: String): Boolean = dragged == key || settling == key

  /** A tap on the row flips its switch, unless it is the release of a row that was held (or of its handle). */
  fun acceptsClick(): Boolean = dragged == null && SystemClock.uptimeMillis() - droppedAt > 250

  fun start(key: String, by: Int): Boolean {
    if (dragged != null) {
      return false
    }
    settleJob?.cancel()
    jobs.remove(key)?.cancel()
    val shift = offset(key)
    val from = if (settling == key) dragOffset else shift.floatValue
    shift.floatValue = 0f
    settling = null
    if (local == null) {
      local = incoming
    }
    dragOffset = from
    owner = by
    moved = false
    travel = 0f
    dragged = key
    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    host.noSwipeBack(this, NO_SWIPE_EVERYWHERE)
    return true
  }

  /**
   * Moves the held row by [dy] px; it trades places with a neighbor once past half of it. [byFinger] false: the page
   * scrolled under the row.
   */
  fun drag(dy: Float, by: Int, byFinger: Boolean = true) {
    val key = dragged ?: return
    if (by != owner) {
      return
    }
    if (byFinger) {
      travel += abs(dy)
    }
    val list = (local ?: return).toMutableList()
    var index = list.indexOf(key)
    if (index < 0) {
      return
    }
    val self = (height(key) ?: 0) + gapPx
    var offset = dragOffset + dy
    var swapped = false
    while (true) {
      if (offset > 0f && index < list.size - 1) {
        val next = list[index + 1]
        val step = (height(next) ?: 0) + gapPx
        if (offset > step / 2f) {
          list[index] = next
          list[index + 1] = key
          offset -= step
          slide(next, self) // it moves up into the held row's old slot
          index++
          swapped = true
          continue
        }
      } else if (offset < 0f && index > 0) {
        val previous = list[index - 1]
        val step = (height(previous) ?: 0) + gapPx
        if (-offset > step / 2f) {
          list[index] = previous
          list[index - 1] = key
          offset += step
          slide(previous, -self)
          index--
          swapped = true
          continue
        }
      }
      break
    }
    // The row stays inside its card.
    if (index == 0 && offset < 0f) {
      offset = 0f
    }
    if (index == list.size - 1 && offset > 0f) {
      offset = 0f
    }
    dragOffset = offset
    if (swapped) {
      local = list
      moved = true
      haptic?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
  }

  fun end(by: Int) {
    val key = dragged ?: return
    if (by != owner) {
      return
    }
    val order = local
    val from = dragOffset
    owner = 0
    droppedAt = SystemClock.uptimeMillis()
    dragged = null
    host.noSwipeBack(this, null)
    if (moved && order != null) {
      onDrop(order)
    }
    local = null
    settling = key
    settleJob?.cancel()
    settleJob = scope.launch {
      animate(from, 0f, animationSpec = spec) { value, _ -> dragOffset = value }
      if (settling == key) {
        settling = null
      }
    }
  }

  /** Accessibility: moves a row one place up or down. */
  fun moveBy(key: String, delta: Int) {
    if (dragged != null) {
      return
    }
    val list = incoming.toMutableList()
    val from = list.indexOf(key)
    val to = from + delta
    if (from < 0 || to < 0 || to >= list.size) {
      return
    }
    list.removeAt(from)
    list.add(to, key)
    onDrop(list)
  }

  /** A neighbor that traded places starts from where it was ([by] px away) and springs into its new slot. */
  private fun slide(key: String, by: Float) {
    val shift = offset(key)
    jobs.remove(key)?.cancel()
    shift.floatValue += by
    jobs[key] = scope.launch {
      animate(shift.floatValue, 0f, animationSpec = spec) { value, _ -> shift.floatValue = value }
    }
  }

  /**
   * px per second to scroll the page while the held row reaches the top app bar (positive) or the bottom of the
   * screen (negative), faster the further it goes.
   */
  fun autoScrollVelocity(view: View, density: Density): Float {
    val key = dragged ?: return 0f
    val slot = slotTop(key) ?: return 0f
    val height = height(key) ?: return 0f
    if (travel < with(density) { 8.dp.toPx() }) {
      return 0f
    }
    val location = IntArray(2)
    view.getLocationInWindow(location)
    val edge = with(density) { 16.dp.toPx() }
    val ramp = with(density) { 64.dp.toPx() }
    val bar = with(density) { 64.dp.toPx() } // the collapsed top app bar
    val max = with(density) { 900.dp.toPx() }
    val top = location[1] + host.topInset + bar + edge
    val bottom = location[1] + view.height - host.bottomInset - edge
    val rowTop = slot + dragOffset
    val rowBottom = rowTop + height
    return when {
      rowTop < top -> max * ((top - rowTop) / ramp).coerceIn(0.1f, 1f)
      rowBottom > bottom -> -max * ((rowBottom - bottom) / ramp).coerceIn(0.1f, 1f)
      else -> 0f
    }
  }
}

/** Holds the coordinates of one row's slot (set as it is placed, read only while a row is held). */
private class Slot {
  var coordinates: LayoutCoordinates? = null
}

@Composable
private fun ReorderGroupBlock(
  id: Int,
  rows: List<ReorderRow>,
  topGap: Dp,
  onToggle: (String, Boolean) -> Unit,
  onDrop: (List<String>) -> Unit
) {
  val host = LocalXgramHost.current
  val density = LocalDensity.current
  val view = LocalView.current
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
  val state = remember { ReorderState(host, scope) }
  val currentOnDrop by rememberUpdatedState(onDrop)
  val incoming = rows.map { it.key }
  val gapPx = with(density) { ListItemDefaults.SegmentedGap.toPx() }

  SideEffect {
    state.haptic = haptic
    state.spec = spec
    state.gapPx = gapPx
    state.incoming = incoming
    state.onDrop = { keys ->
      currentOnDrop(keys)
      host.changed()
    }
  }
  DisposableEffect(state) {
    onDispose { host.noSwipeBack(state, null) }
  }

  // Dragging near the top or bottom of the screen scrolls the page: the scroll goes to the page's list (and the
  // collapsing app bar) through nested scrolling, and the held row is moved back by what scrolled, so it stays
  // under the finger.
  val dispatcher = remember { NestedScrollDispatcher() }
  val connection = remember { object : NestedScrollConnection {} }
  if (state.dragged != null) {
    LaunchedEffect(Unit) {
      var last = 0L
      while (true) {
        val now = withFrameNanos { it }
        if (last != 0L) {
          val velocity = state.autoScrollVelocity(view, density)
          if (velocity != 0f) {
            val delta = Offset(0f, velocity * ((now - last) / 1_000_000_000f))
            val pre = dispatcher.dispatchPreScroll(delta, NestedScrollSource.UserInput)
            val post = dispatcher.dispatchPostScroll(pre, delta - pre, NestedScrollSource.UserInput)
            val scrolled = pre.y + post.y
            if (scrolled != 0f) {
              state.drag(-scrolled, state.owner, byFinger = false)
            }
          }
        }
        last = now
      }
    }
  }

  val order = state.local ?: incoming
  val byKey = rows.associateBy { it.key }
  val shown = order.filter { byKey.containsKey(it) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = topGap)
      .nestedScroll(connection, dispatcher),
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
  ) {
    shown.forEachIndexed { index, rowKey ->
      key(rowKey) {
        ReorderItem(id, byKey.getValue(rowKey), index, shown.size, state, onToggle)
      }
    }
  }
}

@Composable
private fun ReorderItem(
  id: Int,
  row: ReorderRow,
  index: Int,
  count: Int,
  state: ReorderState,
  onToggle: (String, Boolean) -> Unit
) {
  val host = LocalXgramHost.current
  val rowKey = row.key
  val isDragged = state.dragged == rowKey
  val slide = state.offset(rowKey)
  val slot = remember(rowKey) { state.slot(rowKey) }
  // A held row is a card of its own; the others keep the group's corners for their place in it.
  val shapes = if (isDragged) {
    ListItemDefaults.segmentedShapes(index = 0, count = 1)
  } else {
    ListItemDefaults.segmentedShapes(index = index, count = count)
  }

  // The held row is the M3E dragged list item: the row reports the drag as a DragInteraction, and SegmentedListItem
  // draws its dragged state (tertiaryContainer and onTertiaryContainer, CornerLarge, 8dp elevation) and animates in and
  // out of it itself.
  val interactions = remember { MutableInteractionSource() }
  if (isDragged) {
    LaunchedEffect(interactions) {
      val start = DragInteraction.Start()
      interactions.emit(start)
      try {
        awaitCancellation()
      } finally {
        interactions.tryEmit(DragInteraction.Stop(start))
      }
    }
  }

  // A search result for this row: once the page has scrolled to the group, make sure the row itself is on screen.
  val requester = remember { BringIntoViewRequester() }
  val highlight = LocalXgramHighlight.current
  if (highlight != null && highlight.matches(id, rowKey)) {
    LaunchedEffect(highlight) {
      snapshotFlow { highlight.progress.value > 0f }.first { it }
      requester.bringIntoView()
    }
  }

  val moveUp = str(R.string.XgramComposeMoveUp)
  val moveDown = str(R.string.XgramComposeMoveDown)
  val modifier = Modifier
    .zIndex(if (state.isMoving(rowKey)) 1f else 0f)
    .bringIntoViewRequester(requester)
    .onPlaced { coordinates -> slot.coordinates = coordinates }
    .pointerInput(rowKey) {
      // Holding a row anywhere picks it up too.
      detectDragGesturesAfterLongPress(
        onDragStart = { state.start(rowKey, OWNER_ROW) },
        onDragEnd = { state.end(OWNER_ROW) },
        onDragCancel = { state.end(OWNER_ROW) },
        onDrag = { change, amount ->
          if (state.owner == OWNER_ROW && state.dragged == rowKey) {
            change.consume()
            state.drag(amount.y, OWNER_ROW)
          }
        }
      )
    }
    .graphicsLayer {
      translationY = if (state.isMoving(rowKey)) state.dragOffset else slide.floatValue
    }
    .semantics {
      val actions = ArrayList<CustomAccessibilityAction>(2)
      if (index > 0) {
        actions.add(CustomAccessibilityAction(moveUp) { state.moveBy(rowKey, -1); true })
      }
      if (index < count - 1) {
        actions.add(CustomAccessibilityAction(moveDown) { state.moveBy(rowKey, 1); true })
      }
      customActions = actions
    }

  val leading: @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically) {
      DragHandle(rowKey, state)
      if (row.icon != 0) {
        Spacer(Modifier.width(8.dp))
        RowIcon(row.icon)
      }
    }
  }
  val colors = settingsRowColors(id, rowKey)
  val checked = row.checked
  val subtitle = row.subtitle
  val supporting: (@Composable () -> Unit)? = if (subtitle != null) {
    { Text(subtitle) }
  } else {
    null
  }
  if (checked != null) {
    // A disabled row only stops the switch: holding it (and its handle) still moves it.
    SegmentedListItem(
      onClick = {
        // Letting go of a row that was held without moving it must not flip its switch.
        if (state.acceptsClick()) {
          onToggle(rowKey, !checked)
          host.changed()
        }
      },
      shapes = shapes,
      modifier = modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
      },
      enabled = row.enabled,
      leadingContent = leading,
      trailingContent = { SettingsSwitch(checked, row.enabled) },
      supportingContent = supporting,
      colors = colors,
      interactionSource = interactions,
      content = { Text(row.title) }
    )
  } else {
    // A row that can only move: the interactive item too, so its press and dragged states match the rows around it. A
    // tap does nothing; screen readers read its text as one label, with the move actions.
    val description = if (subtitle != null) row.title + ", " + subtitle else row.title
    SegmentedListItem(
      onClick = { },
      shapes = shapes,
      modifier = modifier.clearAndSetSemantics { contentDescription = description },
      leadingContent = leading,
      supportingContent = supporting,
      colors = colors,
      interactionSource = interactions,
      content = { Text(row.title) }
    )
  }
}

/** The handle picks the row up at once; its touches never reach the row (no switch flip, no page scroll). */
@Composable
private fun DragHandle(rowKey: String, state: ReorderState) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .xgramNoSwipeBack()
      .pointerInput(rowKey) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          down.consume()
          if (!state.start(rowKey, OWNER_HANDLE)) {
            return@awaitEachGesture
          }
          try {
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                break
              }
              val dy = change.positionChangeIgnoreConsumed().y
              change.consume()
              if (dy != 0f) {
                state.drag(dy, OWNER_HANDLE)
              }
            }
          } finally {
            state.end(OWNER_HANDLE)
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      tgxIcon(R.drawable.baseline_drag_handle_24),
      contentDescription = null,
      modifier = Modifier.size(24.dp),
      // the leading slot's content color: onSurfaceVariant at rest, the dragged and disabled colors like the row icon
      tint = LocalContentColor.current
    )
  }
}
