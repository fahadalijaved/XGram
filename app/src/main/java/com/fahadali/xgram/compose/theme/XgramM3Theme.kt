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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive theme of XGram's Compose pages: the wallpaper (dynamic) colors, dark or light as Telegram X's
 * theme is (Theme.isDark: auto night mode and the user's choice, not the system setting), expressive motion, and the
 * default type scale and shapes. [themeVersion] changes after a wallpaper or palette change, so the colors are read again.
 */
@Composable
fun XgramM3Theme(dark: Boolean, themeVersion: Int, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val scheme = remember(dark, themeVersion) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      if (dark) darkColorScheme() else lightColorScheme()
    }
  }
  val motion = remember { MotionScheme.expressive() }
  val typography = remember { Typography() }
  val shapes = remember { Shapes() }
  MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = motion,
    shapes = shapes,
    typography = typography,
    content = content
  )
}
