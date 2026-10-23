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

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.fahadali.xgram.appearance.XgramFonts
import kotlin.math.roundToInt

/**
 * Compose's side of the app font ([XgramFonts]): families whose fonts are the views' own cached typefaces (nothing is
 * loaded twice, and both look alike), one per optical size so each style of the type scale gets the opsz of its size,
 * as XgramExpressive.typeface does for the views. System keeps the platform's default family, as before.
 */
object XgramFontFamilies {
  private val families = HashMap<Int, FontFamily>()

  /**
   * Google Sans [font] (XgramFonts.FLEX or ROUNDED) at optical size [opsz] (sp; 0 keeps the font's default), every
   * weight from 100 to 900 upright and italic, so Compose never fakes bold or italic; null for System.
   */
  @JvmStatic
  @Synchronized
  fun family(font: Int, opsz: Float): FontFamily? {
    if (!XgramFonts.isGoogleSans(font)) {
      return null
    }
    val key = font * 10000 + (opsz * 10f).roundToInt()
    return families.getOrPut(key) {
      FontFamily((100..900 step 100).flatMap { weight ->
        listOf(
          AppFont(font, opsz, FontWeight(weight), FontStyle.Normal),
          AppFont(font, opsz, FontWeight(weight), FontStyle.Italic)
        )
      })
    }
  }

  /** The family a Font picker's row previews [font] in: Google Sans, or Telegram X's own regular font for System. */
  @JvmStatic
  fun preview(font: Int): FontFamily = family(font, 0f) ?: FontFamily(XgramFonts.previewTypeface(font))

  /** The default type scale in the font in use ([XgramFonts.current]): every style, the emphasized ones too. */
  @JvmStatic
  fun typography(): Typography {
    val t = Typography()
    val font = XgramFonts.current()
    if (!XgramFonts.isGoogleSans(font)) {
      return t
    }
    fun TextStyle.inFont() = copy(fontFamily = family(font, if (fontSize.isSp) fontSize.value else 0f))
    return t.copy(
      displayLarge = t.displayLarge.inFont(),
      displayMedium = t.displayMedium.inFont(),
      displaySmall = t.displaySmall.inFont(),
      headlineLarge = t.headlineLarge.inFont(),
      headlineMedium = t.headlineMedium.inFont(),
      headlineSmall = t.headlineSmall.inFont(),
      titleLarge = t.titleLarge.inFont(),
      titleMedium = t.titleMedium.inFont(),
      titleSmall = t.titleSmall.inFont(),
      bodyLarge = t.bodyLarge.inFont(),
      bodyMedium = t.bodyMedium.inFont(),
      bodySmall = t.bodySmall.inFont(),
      labelLarge = t.labelLarge.inFont(),
      labelMedium = t.labelMedium.inFont(),
      labelSmall = t.labelSmall.inFont(),
      displayLargeEmphasized = t.displayLargeEmphasized.inFont(),
      displayMediumEmphasized = t.displayMediumEmphasized.inFont(),
      displaySmallEmphasized = t.displaySmallEmphasized.inFont(),
      headlineLargeEmphasized = t.headlineLargeEmphasized.inFont(),
      headlineMediumEmphasized = t.headlineMediumEmphasized.inFont(),
      headlineSmallEmphasized = t.headlineSmallEmphasized.inFont(),
      titleLargeEmphasized = t.titleLargeEmphasized.inFont(),
      titleMediumEmphasized = t.titleMediumEmphasized.inFont(),
      titleSmallEmphasized = t.titleSmallEmphasized.inFont(),
      bodyLargeEmphasized = t.bodyLargeEmphasized.inFont(),
      bodyMediumEmphasized = t.bodyMediumEmphasized.inFont(),
      bodySmallEmphasized = t.bodySmallEmphasized.inFont(),
      labelLargeEmphasized = t.labelLargeEmphasized.inFont(),
      labelMediumEmphasized = t.labelMediumEmphasized.inFont(),
      labelSmallEmphasized = t.labelSmallEmphasized.inFont()
    )
  }
}

/**
 * One weight and style of the app font: loaded by [XgramFonts] (its cache, the variations already set), so no
 * variation settings of its own. Families are kept by [XgramFontFamilies], so each instance lives as long as the app.
 */
private class AppFont(
  val font: Int,
  val opsz: Float,
  override val weight: FontWeight,
  override val style: FontStyle
) : AndroidFont(FontLoadingStrategy.Blocking, AppFontLoader, FontVariation.Settings()) {
  override fun equals(other: Any?): Boolean =
    other is AppFont && other.font == font && other.opsz == opsz && other.weight == weight && other.style == style

  override fun hashCode(): Int = ((font * 31 + opsz.hashCode()) * 31 + weight.hashCode()) * 31 + style.hashCode()

  override fun toString(): String = "AppFont(font=$font, opsz=$opsz, weight=$weight, style=$style)"
}

private object AppFontLoader : AndroidFont.TypefaceLoader {
  override fun loadBlocking(context: Context, font: AndroidFont): android.graphics.Typeface? {
    val f = font as AppFont
    return XgramFonts.typeface(f.font, f.weight.weight, f.style == FontStyle.Italic, f.opsz)
  }

  // Not called for Blocking fonts; the platform default rather than nothing, should it ever be
  override suspend fun awaitLoad(context: Context, font: AndroidFont): android.graphics.Typeface =
    loadBlocking(context, font) ?: android.graphics.Typeface.DEFAULT
}
