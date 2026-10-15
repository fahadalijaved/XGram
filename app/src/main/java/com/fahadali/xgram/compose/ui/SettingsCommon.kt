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
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fahadali.xgram.appearance.XgramIconPack
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import kotlin.math.roundToInt

// Strings, icons and small parts shared by every page.

/** A string from Telegram X's language pack (never stringResource). Plain function: usable in page builders. */
fun s(@StringRes id: Int, vararg args: Any): String =
  if (args.isEmpty()) Lang.getString(id) else Lang.getString(id, *args)

/** [s] that recomposes when the language pack changes. */
@Composable
fun str(@StringRes id: Int, vararg args: Any): String {
  LocalXgramHost.current.langVersion
  return s(id, *args)
}

/** A Telegram X drawable through the user's icon pack. */
@Composable
fun tgxIcon(@DrawableRes id: Int): Painter = painterResource(XgramIconPack.map(id))

/**
 * Keeps Telegram X's swipe-back from starting on this element (sliders, drag handles, horizontal scrollers). The
 * element registers with the page's controller while it is attached; its bounds in the window are worked out only when
 * a swipe could start, never while the page scrolls.
 */
fun Modifier.xgramNoSwipeBack(): Modifier = this then NoSwipeBackElement

private data object NoSwipeBackElement : ModifierNodeElement<NoSwipeBackNode>() {
  override fun create(): NoSwipeBackNode = NoSwipeBackNode()

  override fun update(node: NoSwipeBackNode) = Unit
}

private class NoSwipeBackNode : Modifier.Node(), CompositionLocalConsumerModifierNode, LayoutAwareModifierNode {
  private var host: XgramComposeHost? = null
  // Placing only keeps the coordinates (the same object every frame); nothing is measured or allocated then.
  private var coordinates: LayoutCoordinates? = null

  override fun onAttach() {
    val h = currentValueOf(LocalXgramHost)
    host = h
    h.noSwipeBack(this, ::bounds)
  }

  override fun onDetach() {
    host?.noSwipeBack(this, null)
    host = null
    coordinates = null
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
  }

  private fun bounds(): android.graphics.Rect? {
    val c = coordinates?.takeIf { it.isAttached } ?: return null
    val b = c.boundsInWindow()
    return android.graphics.Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
  }
}

/** Leading icon look: a plain 24dp icon, or (main page categories) one in a 40dp primary container circle. */
enum class IconStyle { PLAIN, CIRCLE }

@Composable
fun RowIcon(@DrawableRes icon: Int, style: IconStyle = IconStyle.PLAIN, tint: Color = Color.Unspecified) {
  if (style == IconStyle.CIRCLE) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
      Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = cs.onPrimaryContainer)
    }
  } else {
    Icon(tgxIcon(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = if (tint.isSpecified) tint else LocalContentColor.current)
  }
}

/** The trailing switch of a row; the row is the toggle target, so the switch itself takes no clicks. */
@Composable
fun SettingsSwitch(checked: Boolean, enabled: Boolean = true) {
  Switch(
    checked = checked,
    onCheckedChange = null,
    enabled = enabled,
    thumbContent = {
      Icon(
        tgxIcon(if (checked) R.drawable.baseline_check_24 else R.drawable.baseline_close_24),
        contentDescription = null,
        modifier = Modifier.size(SwitchDefaults.IconSize)
      )
    }
  )
}

// Highlight: a row opened from search or a Telegram X shortcut pulses twice.

class XgramHighlight(val id: Int, val key: String?) {
  val progress = Animatable(0f)

  fun matches(id: Int, key: String?): Boolean = id != 0 && id == this.id && key == this.key
}

val LocalXgramHighlight = staticCompositionLocalOf<XgramHighlight?> { null }

/**
 * Colors of a settings row (Android 16 Settings): a card lighter than the page (surfaceBright on surfaceContainer),
 * pulsing to secondaryContainer while it is the highlighted row.
 */
@Composable
fun settingsRowColors(id: Int = 0, key: String? = null, container: Color = Color.Unspecified): ListItemColors {
  val cs = MaterialTheme.colorScheme
  val base = if (container.isSpecified) container else cs.surfaceBright
  val highlight = LocalXgramHighlight.current
  val p = if (highlight != null && highlight.matches(id, key)) highlight.progress.value else 0f
  val color = if (p > 0f) lerp(base, cs.secondaryContainer, p) else base
  return ListItemDefaults.segmentedColors(containerColor = color, disabledContainerColor = color)
}
