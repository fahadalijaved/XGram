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
package com.fahadali.xgram.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.fahadali.xgram.appearance.XgramColorSource

/**
 * A Compose ColorScheme from [XgramColorSource.currentComposeColors] (Colours > Custom): every role is given, in the
 * order of lightColorScheme's parameters, so neither the light nor the dark defaults show anywhere.
 */
internal fun xgramSeedColorScheme(colors: IntArray): ColorScheme {
  require(colors.size == XgramColorSource.COMPOSE_COUNT)
  fun c(index: Int) = Color(colors[index])
  return lightColorScheme(
    primary = c(0),
    onPrimary = c(1),
    primaryContainer = c(2),
    onPrimaryContainer = c(3),
    inversePrimary = c(4),
    secondary = c(5),
    onSecondary = c(6),
    secondaryContainer = c(7),
    onSecondaryContainer = c(8),
    tertiary = c(9),
    onTertiary = c(10),
    tertiaryContainer = c(11),
    onTertiaryContainer = c(12),
    background = c(13),
    onBackground = c(14),
    surface = c(15),
    onSurface = c(16),
    surfaceVariant = c(17),
    onSurfaceVariant = c(18),
    surfaceTint = c(19),
    inverseSurface = c(20),
    inverseOnSurface = c(21),
    error = c(22),
    onError = c(23),
    errorContainer = c(24),
    onErrorContainer = c(25),
    outline = c(26),
    outlineVariant = c(27),
    scrim = c(28),
    surfaceBright = c(29),
    surfaceContainer = c(30),
    surfaceContainerHigh = c(31),
    surfaceContainerHighest = c(32),
    surfaceContainerLow = c(33),
    surfaceContainerLowest = c(34),
    surfaceDim = c(35),
    primaryFixed = c(36),
    primaryFixedDim = c(37),
    onPrimaryFixed = c(38),
    onPrimaryFixedVariant = c(39),
    secondaryFixed = c(40),
    secondaryFixedDim = c(41),
    onSecondaryFixed = c(42),
    onSecondaryFixedVariant = c(43),
    tertiaryFixed = c(44),
    tertiaryFixedDim = c(45),
    onTertiaryFixed = c(46),
    onTertiaryFixedVariant = c(47)
  )
}
