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
package com.fahadali.xgram.compose.pages

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.ui.SettingsPage
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.data.TD
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibAccentColor
import org.thunderdog.challegram.tool.UI
import kotlin.math.sqrt

private const val TAG = "XgramNameColors"
private val SWATCH_MIN = 40.dp
private val SWATCH_MAX = 52.dp
private val SWATCH_GAP = 12.dp

/**
 * XGram: your name color and profile color (Telegram Premium), like official Telegram's "Name and profile color". The
 * name color is on your name, the quote line of replies to your messages and your link previews (one to three colors:
 * the line is striped); the profile color fills the top of your profile in the official apps. The previews follow the
 * picks, Apply saves them to the account (the background emoji already set stay as they are).
 */
@Composable
fun NameColorsPage() {
  val host = LocalXgramHost.current
  val tdlib = host.tdlib
  val me = tdlib.myUser()
  var savedName by remember { mutableIntStateOf(me?.accentColorId ?: 0) }
  var savedProfile by remember { mutableIntStateOf(me?.profileAccentColorId ?: -1) }
  var name by remember { mutableIntStateOf(savedName) }
  var profile by remember { mutableIntStateOf(savedProfile) }
  var busy by remember { mutableStateOf(false) }
  val nameIds = remember { tdlib.xgramAvailableAccentColorIds().toList() }
  val profileIds = remember { listOf(-1) + tdlib.xgramAvailableProfileAccentColorIds().toList() }
  val dark = host.isDark
  val myName = remember(me) { if (me != null) TD.getUserName(me) else "" }

  SettingsPage(title = str(R.string.XgramNameColors)) {
    // Read at use: the page unlocks as soon as Telegram says the account has Premium
    val premium = tdlib.hasPremium()
    val nameColors = nameColors(tdlib, name, dark)

    item("namePreview") { NamePreview(myName, nameColors) }
    group {
      custom("nameColors") { shapes ->
        SwatchGrid(shapes, str(R.string.XgramNameColor), "nameColors", nameIds, name, enabled = premium && !busy,
          colorsOf = { nameColors(tdlib, it, dark) }, nameOf = { colorName(it) }) { name = it }
      }
    }
    description(R.string.XgramNameColorInfo)

    item("profilePreview") { ProfilePreview(myName, profileColors(tdlib, profile, dark)) }
    group {
      custom("profileColors") { shapes ->
        SwatchGrid(shapes, str(R.string.XgramProfileColor), "profileColors", profileIds, profile, enabled = premium && !busy,
          colorsOf = { profileColors(tdlib, it, dark)?.paletteColors?.map { c -> rgb(c) } }, nameOf = { if (it == -1) s(R.string.XgramProfileColorNone) else colorName(it) }) { profile = it }
      }
    }
    description(R.string.XgramProfileColorInfo)
    if (!premium) {
      description(R.string.XgramPremiumNeeds)
    }

    item("apply") {
      val changed = name != savedName || profile != savedProfile
      Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Button(
          onClick = {
            val user = tdlib.myUser()
            busy = true
            val pickedName = name
            val pickedProfile = profile
            saveColors(
              tdlib,
              if (pickedName != savedName) TdApi.SetAccentColor(pickedName, user?.backgroundCustomEmojiId ?: 0) else null,
              if (pickedProfile != savedProfile) TdApi.SetProfileAccentColor(pickedProfile, if (pickedProfile == -1) 0 else user?.profileBackgroundCustomEmojiId ?: 0) else null
            ) { error ->
              busy = false
              if (error == null) {
                savedName = pickedName
                savedProfile = pickedProfile
                UI.showToast(R.string.XgramNameColorsSaved, Toast.LENGTH_SHORT)
                host.changed()
              } else {
                UI.showToast(s(if (error.message == "PREMIUM_ACCOUNT_REQUIRED") R.string.XgramPremiumNeeds else R.string.XgramNameColorsFailed), Toast.LENGTH_LONG)
              }
            }
          },
          enabled = premium && changed && !busy,
          modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
          if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
          } else {
            Text(str(R.string.XgramNameColorsApply))
          }
        }
      }
    }
  }
}

/** Sends the changed parts one after the other; [done] runs on the UI thread with the first error, or null. */
private fun saveColors(tdlib: Tdlib, nameCall: TdApi.SetAccentColor?, profileCall: TdApi.SetProfileAccentColor?, done: (TdApi.Error?) -> Unit) {
  val calls = listOfNotNull<TdApi.Function<TdApi.Ok>>(nameCall, profileCall)
  fun next(index: Int) {
    if (index == calls.size) {
      UI.post { done(null) }
      return
    }
    tdlib.send(calls[index]) { _, error ->
      if (error != null) {
        Log.w(TAG, "${calls[index].javaClass.simpleName}: ${error.code} ${error.message}")
        UI.post { done(error) }
      } else {
        next(index + 1)
      }
    }
  }
  next(0)
}

private fun rgb(color: Int): Color = Color(color or 0xFF000000.toInt())

/** A name color's 1-3 colors in this theme: Telegram's own for the extra colors, the app's for the built-in 0-6. */
private fun nameColors(tdlib: Tdlib, id: Int, dark: Boolean): List<Color> {
  val accent = tdlib.accentColor(id)
  val remote = accent.remoteAccentColor
  val colors = remote?.let { if (dark) it.darkThemeColors else it.lightThemeColors }
  return if (id !in 0..6 && colors != null && colors.isNotEmpty()) colors.map { rgb(it) } else listOf(Color(accent.nameColor))
}

private fun profileColors(tdlib: Tdlib, id: Int, dark: Boolean): TdApi.ProfileAccentColors? {
  if (id == -1) {
    return null
  }
  val color = tdlib.xgramProfileAccentColor(id) ?: return null
  return if (dark) color.darkThemeColors else color.lightThemeColors
}

private fun colorName(id: Int): String = if (id in 0..6) TdlibAccentColor.getTextRepresentation(id) else s(R.string.XgramColorNumber, id + 1)

/** How a message of yours and a reply to it look with [colors]: the name, and a quote with the striped line. */
@Composable
private fun NamePreview(myName: String, colors: List<Color>) {
  val cs = MaterialTheme.colorScheme
  val primary = colors.firstOrNull() ?: cs.primary
  Column(
    Modifier
      .padding(start = 16.dp, end = 16.dp, top = 8.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cs.surfaceContainer)
      .padding(16.dp)
  ) {
    Column(
      Modifier
        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
        .background(cs.surfaceContainerHighest)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      Text(myName, color = primary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(6.dp))
      Row(
        Modifier
          .height(IntrinsicSize.Min)
          .clip(RoundedCornerShape(6.dp))
          .background(primary.copy(alpha = .12f))
      ) {
        Box(Modifier.width(3.dp).fillMaxHeight().drawBehind { drawStripes(colors.ifEmpty { listOf(primary) }) })
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
          Text(myName, color = primary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(str(R.string.XgramNameColorsPreviewReply), color = cs.onSurface, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(str(R.string.XgramNameColorsPreviewText), color = cs.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

/** The quote line: one color, or its colors in 6dp dashes one after the other (as Telegram draws it). */
private fun DrawScope.drawStripes(colors: List<Color>) {
  if (colors.size == 1) {
    drawRect(colors[0])
    return
  }
  val dash = 6.dp.toPx()
  var y = 0f
  var i = 0
  while (y < size.height) {
    drawRect(colors[i % colors.size], topLeft = Offset(0f, y), size = Size(size.width, minOf(dash, size.height - y)))
    y += dash
    i++
  }
}

/** The top of your profile with a profile color (its background colors, top to bottom), or the plain surface. */
@Composable
private fun ProfilePreview(myName: String, colors: TdApi.ProfileAccentColors?) {
  val cs = MaterialTheme.colorScheme
  val background = colors?.backgroundColors?.map { rgb(it) }
  val brush = when {
    background == null || background.isEmpty() -> Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainerHigh))
    background.size == 1 -> Brush.verticalGradient(listOf(background[0], background[0]))
    else -> Brush.verticalGradient(background)
  }
  val onColor = if (background.isNullOrEmpty()) cs.onSurface else Color.White
  Box(
    Modifier
      .padding(start = 16.dp, end = 16.dp, top = 20.dp)
      .fillMaxWidth()
      .height(156.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(brush),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        Modifier.size(64.dp).clip(CircleShape).background(if (background.isNullOrEmpty()) cs.primaryContainer else Color.White.copy(alpha = .25f)),
        contentAlignment = Alignment.Center
      ) {
        Text(myName.trim().take(1).uppercase(), color = if (background.isNullOrEmpty()) cs.onPrimaryContainer else Color.White, style = MaterialTheme.typography.headlineSmall)
      }
      Spacer(Modifier.height(8.dp))
      Text(myName, color = onColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
      Text(str(R.string.XgramNameColorsPreviewOnline), color = onColor.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
    }
  }
}

/** A row of swatches under [title]: as many to a line as fit at 40dp or more, each at most 52dp. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwatchGrid(
  shapes: ListItemShapes, title: String, key: String, options: List<Int>, selected: Int, enabled: Boolean,
  colorsOf: (Int) -> List<Color>?, nameOf: (Int) -> String, onPick: (Int) -> Unit
) {
  SegmentedListItem(
    shapes = shapes,
    supportingContent = {
      BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        val columns = ((maxWidth + SWATCH_GAP) / (SWATCH_MIN + SWATCH_GAP)).toInt().coerceIn(1, 8)
        val size = ((maxWidth - SWATCH_GAP * (columns - 1)) / columns).coerceAtMost(SWATCH_MAX)
        FlowRow(
          modifier = Modifier.fillMaxWidth().selectableGroup(),
          horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
          verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
          maxItemsInEachRow = columns
        ) {
          options.forEach { id ->
            ColorSwatch(colorsOf(id), selected = id == selected, enabled = enabled, size = size, description = nameOf(id)) {
              if (id != selected) {
                onPick(id)
              }
            }
          }
        }
      }
    },
    colors = settingsRowColors(0, key),
    content = { Text(title) }
  )
}

/**
 * One swatch: its colors as diagonal bands (none: the surface with a "no" sign). Picked, it morphs into a nine-sided
 * cookie with a check, like the Appearance swatches. Reduce motion: the picked shape at once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorSwatch(colors: List<Color>?, selected: Boolean, enabled: Boolean, size: Dp, description: String, onClick: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val still = remember { XgramMotion.reduce() }
  val progress by animateFloatAsState(
    if (selected) 1f else 0f,
    if (still) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "xgramNameSwatch"
  )
  val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
  val parts = if (colors.isNullOrEmpty()) listOf(cs.surfaceContainerHighest) else colors
  Box(
    Modifier
      .size(size)
      .graphicsLayer { alpha = if (enabled || selected) 1f else .38f }
      .drawWithCache {
        val path = Path()
        val matrix = Matrix()
        onDrawBehind {
          path.rewind()
          morph.toPath(progress.coerceIn(0f, 1f), path)
          matrix.reset()
          matrix.scale(this.size.width, this.size.height)
          path.transform(matrix)
          clipPath(path) { drawBands(parts) }
        }
      }
      .clip(CircleShape)
      .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center
  ) {
    if (colors.isNullOrEmpty() && progress < 1f) {
      Icon(tgxIcon(R.drawable.baseline_block_24), contentDescription = null, modifier = Modifier.size(size * .45f), tint = cs.onSurfaceVariant)
    }
    if (progress > 0f) {
      Box(
        Modifier
          .size(size * .44f)
          .graphicsLayer {
            scaleX = progress
            scaleY = progress
            alpha = progress.coerceIn(0f, 1f)
          }
          .clip(CircleShape)
          .background(cs.surface),
        contentAlignment = Alignment.Center
      ) {
        Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, modifier = Modifier.size(size * .3f), tint = parts[0].takeIf { !colors.isNullOrEmpty() } ?: cs.onSurface)
      }
    }
  }
}

/** [colors] as equal diagonal bands across the swatch (two colors: one half each). */
private fun DrawScope.drawBands(colors: List<Color>) {
  if (colors.size == 1) {
    drawRect(colors[0])
    return
  }
  val diagonal = sqrt(size.width * size.width + size.height * size.height)
  val band = diagonal / colors.size
  val left = center.x - diagonal / 2f
  val top = center.y - diagonal / 2f
  rotate(45f) {
    colors.forEachIndexed { i, color ->
      // A hair wider than the band: no seam between two bands
      drawRect(color, topLeft = Offset(left + band * i, top), size = Size(band + 1f, diagonal))
    }
  }
}
