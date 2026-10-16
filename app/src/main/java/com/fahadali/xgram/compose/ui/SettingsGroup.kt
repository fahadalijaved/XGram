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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.compose.LocalXgramHost
import kotlin.math.roundToInt

/**
 * One row of a card group. [id] is the R.id.btn_xgram* of the classic row (search and highlight use it). [type] is its
 * kind (PageBlock.TYPE_*), the row's content type in the page's list.
 */
class GroupRow internal constructor(
  val id: Int,
  val key: String?,
  val rowKey: String,
  val type: Int,
  val content: @Composable (ListItemShapes) -> Unit
)

/**
 * The rows of one segmented group (Android 16 Settings). Every row is a SegmentedListItem in a card lighter than the
 * page. Rows that change a setting call host.changed() after their callback, so the page reads every value again.
 */
class GroupScope internal constructor() {
  internal val rows = ArrayList<GroupRow>()
  private val keys = HashSet<String>()

  private fun add(id: Int, key: String?, type: Int, content: @Composable (ListItemShapes) -> Unit) {
    var rowKey = "$id:${key ?: ""}"
    var n = 1
    while (!keys.add(rowKey)) {
      rowKey = "$id:${key ?: ""}#${n++}"
    }
    rows.add(GroupRow(id, key, rowKey, type, content))
  }

  /** A switch: the whole row toggles it. */
  fun switch(
    id: Int,
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int = 0,
    checked: Boolean,
    enabled: Boolean = true,
    key: String? = null,
    busy: Boolean = false,
    onChange: (Boolean) -> Unit
  ) = add(id, key, PageBlock.TYPE_SWITCH) { shapes ->
    SwitchRow(id, key, title, subtitle, icon, checked, enabled, busy, shapes, onChange)
  }

  /** The prominent "Use ..." card of Android 16: primaryContainer when on. Ghost mode's master switch. */
  fun mainSwitch(
    id: Int,
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int = 0,
    checked: Boolean,
    key: String? = null,
    onChange: (Boolean) -> Unit
  ) = add(id, key, PageBlock.TYPE_MAIN_SWITCH) { shapes ->
    MainSwitchRow(id, key, title, subtitle, icon, checked, shapes, onChange)
  }

  /** A row whose second line is its current value; opens a choice or a page. */
  fun value(
    id: Int,
    title: String,
    value: String?,
    @DrawableRes icon: Int = 0,
    enabled: Boolean = true,
    key: String? = null,
    onClick: () -> Unit
  ) = add(id, key, PageBlock.TYPE_VALUE) { shapes ->
    ClickRow(id, key, title, value, icon, IconStyle.PLAIN, enabled, false, null, null, shapes, onClick)
  }

  /** A row that opens a page, with a fixed line under its title. No chevron (Android 16). */
  fun link(
    id: Int,
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int = 0,
    iconStyle: IconStyle = IconStyle.PLAIN,
    enabled: Boolean = true,
    key: String? = null,
    onClick: () -> Unit
  ) = add(id, key, PageBlock.TYPE_LINK) { shapes ->
    ClickRow(id, key, title, subtitle, icon, iconStyle, enabled, false, null, null, shapes, onClick)
  }

  /**
   * An action (Reset, Check for updates, Add word). [destructive] colors it as an error. [trailing] goes at the end
   * (a LoadingIndicator while busy); [below] goes under the subtitle (a progress bar).
   */
  fun action(
    id: Int,
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int = 0,
    destructive: Boolean = false,
    enabled: Boolean = true,
    key: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
  ) = add(id, key, PageBlock.TYPE_ACTION) { shapes ->
    ClickRow(id, key, title, subtitle, icon, IconStyle.PLAIN, enabled, destructive, trailing, below, shapes, onClick)
  }

  /** 2 to 4 short options as a connected button group under the title. Longer lists: [value] and host.choose. */
  fun <T> buttons(
    id: Int,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean = true,
    key: String? = null,
    onSelect: (T) -> Unit
  ) = add(id, key, PageBlock.TYPE_BUTTONS) { shapes ->
    ButtonsRow(id, key, title, options, selected, enabled, shapes, onSelect)
  }

  /** A stepped slider; the value line shows labels[index]. The new index is saved when the drag ends. */
  fun slider(
    id: Int,
    title: String,
    labels: List<String>,
    index: Int,
    enabled: Boolean = true,
    key: String? = null,
    onChange: (Int) -> Unit
  ) = add(id, key, PageBlock.TYPE_SLIDER) { shapes ->
    SliderRow(id, key, title, labels, index, enabled, shapes, onChange)
  }

  /** One option of a single choice: a leading radio button; the selected row is tinted. */
  fun radio(
    id: Int,
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    key: String? = null,
    onClick: () -> Unit
  ) = add(id, key, PageBlock.TYPE_RADIO) { shapes ->
    val host = LocalXgramHost.current
    SettingsRadioRow(title, subtitle, selected, shapes, enabled, id, key) {
      onClick()
      host.changed()
    }
  }

  /** One option of a multiple choice: a leading checkbox; the whole row toggles it. */
  fun checkbox(
    id: Int,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    key: String? = null,
    onChange: (Boolean) -> Unit
  ) = add(id, key, PageBlock.TYPE_CHECKBOX) { shapes ->
    val host = LocalXgramHost.current
    SettingsCheckboxRow(title, subtitle, checked, shapes, enabled, id, key) {
      onChange(!checked)
      host.changed()
    }
  }

  /**
   * Anything else in the group's shape (use the given shapes for the corners). [id] lets search highlight it. [type] is
   * its content type in the list: rows drawn alike share one, so scrolling reuses them.
   */
  fun custom(key: Any, id: Int = 0, type: Int = PageBlock.TYPE_CUSTOM, content: @Composable (ListItemShapes) -> Unit) =
    add(id, key.toString(), type, content)
}

/**
 * Draws one row of a group (an item of the page's list): its shape comes from its place in the group, so the outer
 * corners follow the order. [top] is the group's gap above its first row and the segmented gap above the others.
 */
@Composable
internal fun SettingsGroupRow(row: GroupRow, index: Int, count: Int, top: Dp) {
  Box(Modifier.fillMaxWidth().padding(top = top)) {
    row.content(ListItemDefaults.segmentedShapes(index = index, count = count))
  }
}

private fun rowLeading(@DrawableRes icon: Int, style: IconStyle, tint: Color = Color.Unspecified): (@Composable () -> Unit)? =
  if (icon != 0) {
    { RowIcon(icon, style, tint) }
  } else {
    null
  }

@Composable
private fun SwitchRow(
  id: Int, key: String?, title: String, subtitle: String?, @DrawableRes icon: Int, checked: Boolean, enabled: Boolean,
  busy: Boolean, shapes: ListItemShapes, onChange: (Boolean) -> Unit
) {
  val host = LocalXgramHost.current
  SegmentedListItem(
    onClick = {
      onChange(!checked)
      host.changed()
    },
    shapes = shapes,
    modifier = Modifier.semantics {
      role = Role.Switch
      toggleableState = ToggleableState(checked)
    },
    enabled = enabled && !busy,
    leadingContent = rowLeading(icon, IconStyle.PLAIN),
    trailingContent = {
      if (busy) {
        ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
      } else {
        SettingsSwitch(checked, enabled)
      }
    },
    supportingContent = subtitle?.let { { Text(it) } },
    colors = settingsRowColors(id, key),
    content = { Text(title) }
  )
}

@Composable
private fun MainSwitchRow(
  id: Int, key: String?, title: String, subtitle: String?, @DrawableRes icon: Int, checked: Boolean,
  shapes: ListItemShapes, onChange: (Boolean) -> Unit
) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  val target = if (checked) cs.primaryContainer else cs.surfaceContainerHighest
  val animated by animateColorAsState(target, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "xgramMainSwitch")
  val highlight = LocalXgramHighlight.current
  val p = if (highlight != null && highlight.matches(id, key)) highlight.progress.value else 0f
  val container = if (p > 0f) lerp(animated, cs.secondaryContainer, p) else animated
  val onColor = if (checked) cs.onPrimaryContainer else cs.onSurface
  SegmentedListItem(
    onClick = {
      onChange(!checked)
      host.changed()
    },
    shapes = shapes,
    modifier = Modifier.heightIn(min = 72.dp).semantics {
      role = Role.Switch
      toggleableState = ToggleableState(checked)
    },
    leadingContent = rowLeading(icon, IconStyle.PLAIN, onColor),
    trailingContent = { SettingsSwitch(checked) },
    supportingContent = subtitle?.let { { Text(it) } },
    colors = ListItemDefaults.segmentedColors(
      containerColor = container,
      contentColor = onColor,
      leadingContentColor = onColor,
      supportingContentColor = onColor
    ),
    content = { Text(title, style = MaterialTheme.typography.titleLarge) }
  )
}

@Composable
private fun ClickRow(
  id: Int, key: String?, title: String, subtitle: String?, @DrawableRes icon: Int, iconStyle: IconStyle, enabled: Boolean,
  destructive: Boolean, trailing: (@Composable () -> Unit)?, below: (@Composable () -> Unit)?, shapes: ListItemShapes,
  onClick: () -> Unit
) {
  val host = LocalXgramHost.current
  val cs = MaterialTheme.colorScheme
  val supporting: (@Composable () -> Unit)? = if (subtitle != null || below != null) {
    {
      if (below == null) {
        Text(subtitle!!)
      } else {
        Column {
          if (subtitle != null) {
            Text(subtitle)
          }
          below()
        }
      }
    }
  } else {
    null
  }
  SegmentedListItem(
    onClick = {
      onClick()
      host.changed()
    },
    shapes = shapes,
    enabled = enabled,
    leadingContent = rowLeading(icon, iconStyle, if (destructive) cs.error else Color.Unspecified),
    trailingContent = trailing,
    supportingContent = supporting,
    colors = settingsRowColors(id, key),
    content = {
      if (destructive) Text(title, color = cs.error) else Text(title)
    }
  )
}

@Composable
private fun <T> ButtonsRow(
  id: Int, key: String?, title: String, options: List<Pair<String, T>>, selected: T, enabled: Boolean,
  shapes: ListItemShapes, onSelect: (T) -> Unit
) {
  val host = LocalXgramHost.current
  SegmentedListItem(
    shapes = shapes,
    enabled = enabled,
    supportingContent = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
      ) {
        val last = options.size - 1
        options.forEachIndexed { index, option ->
          val isSelected = option.second == selected
          ToggleButton(
            checked = isSelected,
            onCheckedChange = {
              if (!isSelected) {
                onSelect(option.second)
                host.changed()
              }
            },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            enabled = enabled,
            shapes = when (index) {
              0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
              last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
              else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
          ) {
            Text(option.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    },
    colors = settingsRowColors(id, key),
    content = { Text(title) }
  )
}

@Composable
private fun SliderRow(
  id: Int, key: String?, title: String, labels: List<String>, index: Int, enabled: Boolean, shapes: ListItemShapes,
  onChange: (Int) -> Unit
) {
  val host = LocalXgramHost.current
  val last = (labels.size - 1).coerceAtLeast(1)
  var position by remember(index) { mutableFloatStateOf(index.toFloat()) }
  val shown = position.roundToInt().coerceIn(0, labels.size - 1)
  SegmentedListItem(
    shapes = shapes,
    enabled = enabled,
    supportingContent = {
      Column {
        Text(labels.getOrElse(shown) { "" })
        Slider(
          value = position,
          onValueChange = { position = it },
          modifier = Modifier.fillMaxWidth().xgramNoSwipeBack().semantics { stateDescription = labels.getOrElse(shown) { "" } },
          enabled = enabled,
          valueRange = 0f..last.toFloat(),
          steps = (labels.size - 2).coerceAtLeast(0),
          onValueChangeFinished = {
            val newIndex = position.roundToInt().coerceIn(0, labels.size - 1)
            position = newIndex.toFloat()
            if (newIndex != index) {
              onChange(newIndex)
              host.changed()
            }
          }
        )
      }
    },
    colors = settingsRowColors(id, key),
    content = { Text(title) }
  )
}

/** A radio row (also used by the choice sheet and long lists such as Translate target). */
@Composable
fun SettingsRadioRow(
  title: String,
  subtitle: String?,
  selected: Boolean,
  shapes: ListItemShapes,
  enabled: Boolean = true,
  id: Int = 0,
  key: String? = null,
  onClick: () -> Unit
) {
  SegmentedListItem(
    selected = selected,
    onClick = onClick,
    shapes = shapes,
    modifier = Modifier.semantics { role = Role.RadioButton },
    enabled = enabled,
    leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    supportingContent = subtitle?.let { { Text(it) } },
    colors = settingsRowColors(id, key),
    content = { Text(title) }
  )
}

/** A checkbox row: the row is the toggle target, so the checkbox itself takes no clicks. */
@Composable
fun SettingsCheckboxRow(
  title: String,
  subtitle: String?,
  checked: Boolean,
  shapes: ListItemShapes,
  enabled: Boolean = true,
  id: Int = 0,
  key: String? = null,
  onClick: () -> Unit
) {
  SegmentedListItem(
    onClick = onClick,
    shapes = shapes,
    modifier = Modifier.semantics {
      role = Role.Checkbox
      toggleableState = ToggleableState(checked)
    },
    enabled = enabled,
    leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
    supportingContent = subtitle?.let { { Text(it) } },
    colors = settingsRowColors(id, key),
    content = { Text(title) }
  )
}
