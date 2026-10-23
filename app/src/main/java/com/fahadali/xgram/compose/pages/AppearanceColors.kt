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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.appearance.XgramColorSource
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.compose.LocalXgramHost
import com.fahadali.xgram.compose.XgramComposeHost
import com.fahadali.xgram.compose.ui.PageScope
import com.fahadali.xgram.compose.ui.s
import com.fahadali.xgram.compose.ui.settingsRowColors
import com.fahadali.xgram.compose.ui.str
import com.fahadali.xgram.compose.ui.tgxIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.thunderdog.challegram.R
import kotlin.math.roundToInt

/**
 * Appearance > Colours (XgramColorSource), under Expressive design: Wallpaper or Custom as a connected button group;
 * with Custom, the swatches (each split into the primary, secondary and tertiary of the scheme it makes; the picked
 * one morphs into a cookie with a check), the style chips and More colours ([XgramMoreColorsDialog]). Every change
 * repaints the app at once, as a wallpaper change does. With Expressive design off the choice is disabled and the
 * description says why. Before Android 13 (no Expressive design) there is nothing.
 */
internal fun PageScope.colorSource(host: XgramComposeHost, onMore: () -> Unit) {
  if (!XgramExpressive.isAvailable()) {
    return
  }
  val settings = XgramSettings.instance()
  val available = XgramColorSource.isAvailable()
  val isCustom = settings.colorSource() == XgramSettings.COLOR_SOURCE_CUSTOM
  group(key = "colors") {
    buttons(
      R.id.btn_xgramColorSource, s(R.string.XgramColors),
      listOf(
        s(R.string.XgramColorsWallpaper) to XgramSettings.COLOR_SOURCE_WALLPAPER,
        s(R.string.XgramColorsCustom) to XgramSettings.COLOR_SOURCE_CUSTOM
      ),
      selected = settings.colorSource(), enabled = available
    ) { source ->
      XgramColorSource.setSource(host.controller, source)
    }
    if (available && isCustom) {
      custom("colors_seed", R.id.btn_xgramColorSeed, TYPE_COLOR_SWATCHES) { shapes -> SwatchesRow(shapes) }
      custom("colors_style", R.id.btn_xgramColorStyle, TYPE_COLOR_STYLE) { shapes -> StyleRow(shapes) }
      link(R.id.btn_xgramColorMore, s(R.string.XgramColorsMore), s(R.string.XgramColorsMoreSub), icon = R.drawable.baseline_palette_24) {
        onMore()
      }
    }
  }
  description(
    when {
      !available -> R.string.XgramColorsNeedsExpressive
      isCustom -> R.string.XgramColorsCustomInfo
      else -> R.string.XgramColorsWallpaperInfo
    }
  )
}

// List content types of the two custom rows (PageBlock.TYPE_* go up to 15, SettingsReorder's is 10)
private const val TYPE_COLOR_SWATCHES = 40
private const val TYPE_COLOR_STYLE = 41

private val SWATCH_GAP = 12.dp
private val SWATCH_MIN = 52.dp
private val SWATCH_MAX = 64.dp

/**
 * The previews of [seeds] in [style], light or [dark]. Made off the main thread (a few milliseconds each); what is
 * cached shows at once, and the previous style's stay on screen until the new ones are ready.
 */
@Composable
private fun rememberPreviews(seeds: List<Int>, style: Int, dark: Boolean): Map<Int, IntArray> {
  var made by remember { mutableStateOf(emptyMap<Int, IntArray>()) }
  val cached = remember(seeds, style, dark) {
    val map = HashMap<Int, IntArray>()
    seeds.forEach { seed -> XgramColorSource.cachedPreview(seed, style, dark)?.let { map[seed] = it } }
    map
  }
  val complete = cached.size == seeds.size
  LaunchedEffect(seeds, style, dark) {
    made = if (complete) cached else withContext(Dispatchers.Default) {
      seeds.associateWith { XgramColorSource.preview(it, style, dark) }
    }
  }
  return if (complete) cached else made
}

/** The swatches: four to a line on a phone (the Fold's cover screen too), six where they fit at 52dp or more. */
@Composable
private fun SwatchesRow(shapes: ListItemShapes) {
  val host = LocalXgramHost.current
  host.version // the picked colour and style are read again after every change
  val settings = XgramSettings.instance()
  val seed = settings.colorSeed()
  val style = settings.colorStyle()
  val dark = host.isDark
  // A colour from More colours comes first, picked, ahead of the twelve swatches
  val seeds = remember(seed) {
    if (XgramColorSource.presetIndex(seed) == -1) listOf(seed) + XgramColorSource.PRESETS.toList() else XgramColorSource.PRESETS.toList()
  }
  val previews = rememberPreviews(seeds, style, dark)
  SegmentedListItem(
    shapes = shapes,
    supportingContent = {
      BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        val columns = if (maxWidth >= SWATCH_MIN * 6 + SWATCH_GAP * 5) 6 else 4
        val size = ((maxWidth - SWATCH_GAP * (columns - 1)) / columns).coerceAtMost(SWATCH_MAX)
        FlowRow(
          modifier = Modifier.fillMaxWidth().selectableGroup(),
          horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
          maxItemsInEachRow = columns
        ) {
          seeds.forEach { option ->
            Swatch(option, previews[option], selected = option == seed, size = size) {
              if (option != seed) {
                XgramColorSource.setSeed(host.controller, option, -1)
                host.changed()
              }
            }
          }
        }
      }
    },
    colors = settingsRowColors(R.id.btn_xgramColorSeed),
    content = { Text(str(R.string.XgramColorsSeed)) }
  )
}

/**
 * One swatch: a circle split into the scheme's primary (the top half), secondary and tertiary (the bottom quarters);
 * surfaceContainerHighest until its preview is made. Picked, it morphs into a nine-sided cookie (Material 3 Expressive
 * shapes) with a check on a primary container disc. Reduce motion: no morph animation, the picked shape at once.
 */
@Composable
private fun Swatch(seed: Int, colors: IntArray?, selected: Boolean, size: Dp, onClick: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val still = remember { XgramMotion.reduce() }
  val progress by animateFloatAsState(
    if (selected) 1f else 0f,
    if (still) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "xgramSwatch"
  )
  val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
  val name = str(XgramColorSource.nameOf(seed))
  val placeholder = cs.surfaceContainerHighest
  Box(
    Modifier
      .size(size)
      // Drawn ahead of the clip: the cookie's lobes may reach past the circle the ripple is clipped to
      .drawWithCache {
        val path = Path()
        val matrix = Matrix()
        val primary = colors?.let { Color(it[XgramColorSource.PREVIEW_PRIMARY]) } ?: placeholder
        val secondary = colors?.let { Color(it[XgramColorSource.PREVIEW_SECONDARY]) } ?: placeholder
        val tertiary = colors?.let { Color(it[XgramColorSource.PREVIEW_TERTIARY]) } ?: placeholder
        onDrawBehind {
          // The morph's path is in unit coordinates: scaled to the swatch. The spring may overshoot; the shape does not.
          path.rewind()
          morph.toPath(progress.coerceIn(0f, 1f), path)
          matrix.reset()
          matrix.scale(this.size.width, this.size.height)
          path.transform(matrix)
          drawSplit(path, primary, secondary, tertiary)
        }
      }
      .clip(CircleShape)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = name },
    contentAlignment = Alignment.Center
  ) {
    if (progress > 0f) {
      val disc = colors?.let { Color(it[XgramColorSource.PREVIEW_PRIMARY_CONTAINER]) } ?: cs.primaryContainer
      val check = colors?.let { Color(it[XgramColorSource.PREVIEW_ON_PRIMARY_CONTAINER]) } ?: cs.onPrimaryContainer
      Box(
        Modifier
          .size(size * .44f)
          .graphicsLayer {
            scaleX = progress
            scaleY = progress
            alpha = progress.coerceIn(0f, 1f)
          }
          .clip(CircleShape)
          .background(disc),
        contentAlignment = Alignment.Center
      ) {
        Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, modifier = Modifier.size(size * .3f), tint = check)
      }
    }
  }
}

/** The three parts of a swatch inside [path]: primary on top, secondary and tertiary below. */
private fun DrawScope.drawSplit(path: Path, primary: Color, secondary: Color, tertiary: Color) {
  val w = size.width
  val h = size.height
  clipPath(path) {
    drawRect(primary, size = Size(w, h / 2f))
    drawRect(secondary, topLeft = Offset(0f, h / 2f), size = Size(w / 2f, h / 2f))
    drawRect(tertiary, topLeft = Offset(w / 2f, h / 2f), size = Size(w / 2f, h / 2f))
  }
}

/** The style chips on the page: a tap repaints the app at once. */
@Composable
private fun StyleRow(shapes: ListItemShapes) {
  val host = LocalXgramHost.current
  host.version
  val style = XgramSettings.instance().colorStyle()
  SegmentedListItem(
    shapes = shapes,
    supportingContent = {
      StyleChips(style, Modifier.padding(top = 4.dp)) {
        XgramColorSource.setStyle(host.controller, it)
        host.changed()
      }
    },
    colors = settingsRowColors(R.id.btn_xgramColorStyle),
    content = { Text(str(R.string.XgramColorsStyle)) }
  )
}

/** Tonal spot, Vibrant, Expressive and Neutral as filter chips; they wrap with large fonts and on the cover screen. */
@Composable
private fun StyleChips(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
  FlowRow(
    modifier = modifier.fillMaxWidth().selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    XgramColorSource.STYLES.forEachIndexed { index, style ->
      val isSelected = style == selected
      FilterChip(
        selected = isSelected,
        onClick = {
          if (!isSelected) {
            onSelect(style)
          }
        },
        label = { Text(str(XgramColorSource.STYLE_NAMES[index])) },
        modifier = Modifier.semantics { role = Role.RadioButton },
        leadingIcon = if (isSelected) {
          {
            Icon(tgxIcon(R.drawable.baseline_check_24), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
          }
        } else {
          null
        }
      )
    }
  }
}

/**
 * More colours: any hue on an HCT hue slider and the style chips, with a live preview of the scheme they make (the
 * picked swatch and the scheme's primary, secondary and tertiary on its own surface). Only the hue is picked: the style
 * sets how colourful the scheme is, as on Android. The preview is made off the main thread, the newest hue first, so
 * the slider never waits for it. Apply saves both and repaints the app.
 */
@Composable
internal fun XgramMoreColorsDialog(onDismiss: () -> Unit) {
  val host = LocalXgramHost.current
  val settings = XgramSettings.instance()
  val startSeed = remember { settings.colorSeed() }
  var hue by remember { mutableFloatStateOf(XgramColorSource.hueOf(startSeed)) }
  var style by remember { mutableIntStateOf(settings.colorStyle()) }
  val dark = host.isDark
  // Whole degrees: a new preview only when the hue really moves
  val degrees = hue.roundToInt().mod(360)
  // The colour in use while the hue stays where it was, so a swatch only given a new style stays that swatch
  val startDegrees = remember { XgramColorSource.hueOf(startSeed).roundToInt().mod(360) }
  fun seedOf(d: Int) = if (d == startDegrees) startSeed else XgramColorSource.seedForHue(d.toFloat())
  var preview by remember { mutableStateOf(XgramColorSource.cachedPreview(seedOf(degrees), style, dark)) }
  LaunchedEffect(Unit) {
    snapshotFlow { Triple(hue.roundToInt().mod(360), style, host.isDark) }.collectLatest { (d, st, dk) ->
      val seed = seedOf(d)
      preview = XgramColorSource.cachedPreview(seed, st, dk) ?: withContext(Dispatchers.Default) {
        XgramColorSource.preview(seed, st, dk)
      }
    }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          XgramColorSource.setSeed(host.controller, seedOf(degrees), style)
          host.changed()
        }
      ) {
        Text(str(R.string.Apply))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(str(R.string.Cancel))
      }
    },
    title = { Text(str(R.string.XgramColorsMore)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        ColorsPreview(preview, str(R.string.XgramColorsHueValue, degrees))
        Text(str(R.string.XgramColorsHue), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
        HueSlider(hue) { hue = it }
        Text(str(R.string.XgramColorsStyle), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
        StyleChips(style, Modifier.padding(top = 4.dp)) { style = it }
        Text(
          str(R.string.XgramColorsMoreInfo),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  )
}

/**
 * The preview card, drawn in the scheme being picked (the dialog's own roles until the first preview is made). TalkBack
 * reads it as one item: "Preview, N degrees".
 */
@Composable
private fun ColorsPreview(colors: IntArray?, hueText: String) {
  val cs = MaterialTheme.colorScheme
  fun pick(index: Int, fallback: Color) = colors?.let { Color(it[index]) } ?: fallback
  val label = str(R.string.XgramColorsPreview)
  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(pick(XgramColorSource.PREVIEW_SURFACE_CONTAINER_HIGH, cs.surfaceContainerHighest))
      .clearAndSetSemantics { contentDescription = "$label, $hueText" }
      .padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PreviewSwatch(
        pick(XgramColorSource.PREVIEW_PRIMARY, cs.surfaceContainerHigh),
        pick(XgramColorSource.PREVIEW_SECONDARY, cs.surfaceContainerHigh),
        pick(XgramColorSource.PREVIEW_TERTIARY, cs.surfaceContainerHigh),
        64.dp
      )
      FlowRow(
        modifier = Modifier.padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        PreviewPill(str(R.string.XgramColorsPreviewPrimary), pick(XgramColorSource.PREVIEW_PRIMARY, cs.primary), pick(XgramColorSource.PREVIEW_ON_PRIMARY, cs.onPrimary))
        PreviewPill(
          str(R.string.XgramColorsPreviewSecondary),
          pick(XgramColorSource.PREVIEW_SECONDARY_CONTAINER, cs.secondaryContainer),
          pick(XgramColorSource.PREVIEW_ON_SECONDARY_CONTAINER, cs.onSecondaryContainer)
        )
        PreviewPill(
          str(R.string.XgramColorsPreviewTertiary),
          pick(XgramColorSource.PREVIEW_TERTIARY_CONTAINER, cs.tertiaryContainer),
          pick(XgramColorSource.PREVIEW_ON_TERTIARY_CONTAINER, cs.onTertiaryContainer)
        )
      }
    }
  }
}

/** The preview's swatch: the picked cookie, as on the page. */
@Composable
private fun PreviewSwatch(primary: Color, secondary: Color, tertiary: Color, size: Dp) {
  val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
  Box(
    Modifier
      .size(size)
      .drawWithCache {
        val path = Path()
        val matrix = Matrix()
        onDrawBehind {
          path.rewind()
          morph.toPath(1f, path)
          matrix.reset()
          matrix.scale(this.size.width, this.size.height)
          path.transform(matrix)
          drawSplit(path, primary, secondary, tertiary)
        }
      }
  )
}

@Composable
private fun PreviewPill(text: String, container: Color, content: Color) {
  Box(
    Modifier
      .clip(CircleShape)
      .background(container)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(text, color = content, style = MaterialTheme.typography.labelLarge)
  }
}

/**
 * The hue slider: an M3 slider over a band of every HCT hue (at one chroma and tone, so the band is even), its handle in
 * onSurface. The band runs the slider's way: right to left in RTL.
 */
@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
  val cs = MaterialTheme.colorScheme
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val band = remember(rtl) {
    val stops = (0..12).map { Color(XgramColorSource.hueColor(it * 30f)) }
    Brush.horizontalGradient(if (rtl) stops.reversed() else stops)
  }
  val value = str(R.string.XgramColorsHueValue, hue.roundToInt().mod(360))
  Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 2.dp)
        .height(16.dp)
        .clip(CircleShape)
        .background(band)
    )
    Slider(
      value = hue,
      onValueChange = onChange,
      modifier = Modifier.fillMaxWidth().semantics { stateDescription = value },
      valueRange = 0f..359f,
      colors = SliderDefaults.colors(
        thumbColor = cs.onSurface,
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
      )
    )
  }
}
