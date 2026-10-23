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

package com.fahadali.xgram.compose.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.fahadali.xgram.appearance.XgramAmoled
import com.fahadali.xgram.appearance.XgramColorSource
import com.fahadali.xgram.appearance.XgramExpressive

/**
 * Material 3 Expressive theme of XGram's Compose pages: the wallpaper (dynamic) colors, dark or light as Telegram X's
 * theme is (Theme.isDark: auto night mode and the user's choice, not the system setting), expressive motion, the
 * default type scale in the app font ([XgramFontFamilies]) and the default shapes. [themeVersion] changes after a
 * wallpaper or palette change, so the colors are read again.
 * With Pure black on ([XgramAmoled]) the dark scheme's surfaces are black and near-black, as on the rest of the app.
 */
@Composable
fun XgramM3Theme(dark: Boolean, themeVersion: Int, content: @Composable () -> Unit) {
  val context = LocalContext.current
  // Read on every composition (cached, cheap); toggling it bumps themeVersion as well.
  val pureBlack = dark && XgramAmoled.isEnabled()
  // Colours > Custom: the picked colour and style (a change bumps themeVersion as well); null with the wallpaper's
  val custom = if (XgramColorSource.isCustom()) XgramColorSource.key() else null
  val scheme = remember(dark, themeVersion, pureBlack, custom) {
    val base = if (custom != null) {
      // The scheme material-color-utilities makes from the picked colour, as the views draw with (XgramColorSource)
      xgramSeedColorScheme(XgramColorSource.currentComposeColors(dark))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      if (dark) darkColorScheme() else lightColorScheme()
    }
    if (pureBlack) pureBlackScheme(base) else base
  }
  val motion = remember { MotionScheme.expressive() }
  // The default type scale in the app font (XgramFonts, read once per process: a change restarts the app)
  val typography = remember { XgramFontFamilies.typography() }
  val shapes = remember { Shapes() }
  MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = motion,
    shapes = shapes,
    typography = typography,
    content = content
  )
}

/**
 * The dark scheme with Pure black, the same levels as the views ([XgramAmoled.pureBlack]): the page roles black
 * (background, surface, dim, lowest, low: sheets, and surfaceContainer: SettingsPage and its app bar, the navigation
 * bar), surfaceBright and surfaceContainerHigh the card step (settings rows, dialogs, the search bar) and
 * surfaceContainerHighest the chip step (switch tracks), tinted very slightly with their own Material You hue.
 * Content colors, outlines and the accent containers stay as they are. The surface tint is black too, so a component
 * with tonal elevation on a black container (black equals surface here) stays black instead of turning primary-grey.
 */
private fun pureBlackScheme(s: ColorScheme): ColorScheme {
  fun step(role: Int, c: Color) = Color(XgramAmoled.pureBlack(role, c.toArgb()))
  return s.copy(
    background = step(XgramExpressive.SURFACE, s.background),
    surface = step(XgramExpressive.SURFACE, s.surface),
    surfaceDim = step(XgramExpressive.SURFACE_DIM, s.surfaceDim),
    // For the views lowest is the chip step (a recessed chip in a bubble, see XgramAmoled); Compose has no such use,
    // and its components read lowest as the darkest page level: black.
    surfaceContainerLowest = step(XgramExpressive.SURFACE, s.surfaceContainerLowest),
    surfaceContainerLow = step(XgramExpressive.SURFACE_CONTAINER_LOW, s.surfaceContainerLow),
    surfaceContainer = step(XgramExpressive.SURFACE_CONTAINER, s.surfaceContainer),
    surfaceContainerHigh = step(XgramExpressive.SURFACE_CONTAINER_HIGH, s.surfaceContainerHigh),
    surfaceContainerHighest = step(XgramExpressive.SURFACE_CONTAINER_HIGHEST, s.surfaceContainerHighest),
    surfaceBright = step(XgramExpressive.SURFACE_BRIGHT, s.surfaceBright),
    surfaceTint = Color.Black
  )
}
