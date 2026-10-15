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
 *
 * The shape definitions (point lists, roundings, repeat and mirror rules) and the scale factor
 * calculation are ported from AOSP androidx.compose.material3 (MaterialShapes.kt, LoadingIndicator.kt):
 *
 *   Copyright 2024 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.fahadali.xgram.appearance

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.transformed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.graphics.shapes.toPath as shapeToPath

/**
 * The 35 Material 3 Expressive shapes (Compose MaterialShapes), every one normalized to the unit square,
 * plus the helpers the loading indicator needs. Draw them with [toPath] and [fit] on a Canvas.
 */
object XgramShapes {
  private class PointNRound(val x: Float, val y: Float, val r: CornerRounding = CornerRounding.Unrounded)

  private fun p(x: Float, y: Float, r: CornerRounding = CornerRounding.Unrounded) = PointNRound(x, y, r)

  private val r15 = CornerRounding(radius = .15f)
  private val r20 = CornerRounding(radius = .2f)
  private val r30 = CornerRounding(radius = .3f)
  private val r50 = CornerRounding(radius = .5f)
  private val r100 = CornerRounding(radius = 1f)

  private fun round(radius: Float, smoothing: Float = 0f) = CornerRounding(radius, smoothing)

  private fun rotation(degrees: Float) = Matrix().apply { setRotate(degrees) }
  private val rotN45 = rotation(-45f)
  private val rotN90 = rotation(-90f)
  private val rotN135 = rotation(-135f)

  private fun Float.toRadians(): Float = this / 360f * 2f * PI.toFloat()

  private fun angleDegrees(x: Float, y: Float): Float = atan2(y, x) * 180f / PI.toFloat()

  private fun doRepeat(points: List<PointNRound>, reps: Int, cx: Float, cy: Float, mirroring: Boolean): List<PointNRound> {
    val result = ArrayList<PointNRound>()
    if (mirroring) {
      val angles = FloatArray(points.size) { angleDegrees(points[it].x - cx, points[it].y - cy) }
      val distances = FloatArray(points.size) {
        val dx = points[it].x - cx
        val dy = points[it].y - cy
        sqrt(dx * dx + dy * dy)
      }
      val actualReps = reps * 2
      val sectionAngle = 360f / actualReps
      for (rep in 0 until actualReps) {
        for (index in points.indices) {
          val i = if (rep % 2 == 0) index else points.lastIndex - index
          if (i > 0 || rep % 2 == 0) {
            val a = (sectionAngle * rep + if (rep % 2 == 0) angles[i] else sectionAngle - angles[i] + 2 * angles[0]).toRadians()
            result.add(PointNRound(cx + cos(a) * distances[i], cy + sin(a) * distances[i], points[i].r))
          }
        }
      }
    } else {
      val np = points.size
      for (k in 0 until np * reps) {
        val source = points[k % np]
        val a = ((k / np) * 360f / reps).toRadians()
        val dx = source.x - cx
        val dy = source.y - cy
        result.add(PointNRound(cx + dx * cos(a) - dy * sin(a), cy + dx * sin(a) + dy * cos(a), source.r))
      }
    }
    return result
  }

  private fun customPolygon(pnr: List<PointNRound>, reps: Int, mirroring: Boolean = false): RoundedPolygon {
    val cx = .5f
    val cy = .5f
    val actual = doRepeat(pnr, reps, cx, cy, mirroring)
    val vertices = FloatArray(actual.size * 2)
    for (i in actual.indices) {
      vertices[i * 2] = actual[i].x
      vertices[i * 2 + 1] = actual[i].y
    }
    return RoundedPolygon(vertices, perVertexRounding = actual.map { it.r }, centerX = cx, centerY = cy)
  }

  /** A shape that fails to build falls back to a circle instead of taking the whole object down. */
  private inline fun shape(name: String, builder: () -> RoundedPolygon): RoundedPolygon {
    return try {
      builder().normalized()
    } catch (t: Throwable) {
      Log.e("XGram", "Expressive shape $name failed", t)
      RoundedPolygon.circle(numVertices = 10).normalized()
    }
  }

  @JvmField val CIRCLE: RoundedPolygon = shape("Circle") { RoundedPolygon.circle(numVertices = 10) }
  @JvmField val SQUARE: RoundedPolygon = shape("Square") { RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = r30) }
  @JvmField val SLANTED: RoundedPolygon = shape("Slanted") {
    customPolygon(listOf(p(.926f, .970f, round(.189f, .811f)), p(-.021f, .967f, round(.187f, .057f))), reps = 2)
  }
  @JvmField val ARCH: RoundedPolygon = shape("Arch") {
    RoundedPolygon(numVertices = 4, perVertexRounding = listOf(r100, r100, r20, r20)).transformed(rotN135)
  }
  @JvmField val FAN: RoundedPolygon = shape("Fan") {
    customPolygon(listOf(
      p(1.004f, 1.000f, round(.148f, .417f)),
      p(0f, 1f, round(.151f)),
      p(0f, -.003f, round(.148f)),
      p(.978f, .020f, round(.803f))
    ), reps = 1)
  }
  @JvmField val ARROW: RoundedPolygon = shape("Arrow") {
    customPolygon(listOf(
      p(.5f, .892f, round(.313f)),
      p(-.216f, 1.050f, round(.207f)),
      p(.499f, -.160f, round(.215f, 1f)),
      p(1.225f, 1.060f, round(.211f))
    ), reps = 1)
  }
  @JvmField val SEMI_CIRCLE: RoundedPolygon = shape("SemiCircle") {
    RoundedPolygon.rectangle(width = 1.6f, height = 1f, perVertexRounding = listOf(r20, r20, r100, r100))
  }
  @JvmField val OVAL: RoundedPolygon = shape("Oval") {
    RoundedPolygon.circle().transformed(Matrix().apply { setScale(1f, .64f) }).transformed(rotN45)
  }
  @JvmField val PILL: RoundedPolygon = shape("Pill") {
    customPolygon(listOf(
      p(.961f, .039f, round(.426f)),
      p(1.001f, .428f),
      p(1.000f, .609f, round(1f))
    ), reps = 2, mirroring = true)
  }
  @JvmField val TRIANGLE: RoundedPolygon = shape("Triangle") {
    RoundedPolygon(numVertices = 3, rounding = r20).transformed(rotN90)
  }
  @JvmField val DIAMOND: RoundedPolygon = shape("Diamond") {
    customPolygon(listOf(p(.5f, 1.096f, round(.151f, .524f)), p(.040f, .5f, round(.159f))), reps = 2)
  }
  @JvmField val CLAM_SHELL: RoundedPolygon = shape("ClamShell") {
    customPolygon(listOf(
      p(.171f, .841f, round(.159f)),
      p(-.020f, .5f, round(.140f)),
      p(.170f, .159f, round(.159f))
    ), reps = 2)
  }
  @JvmField val PENTAGON: RoundedPolygon = shape("Pentagon") {
    customPolygon(listOf(
      p(.5f, -.009f, round(.172f)),
      p(1.030f, .365f, round(.164f)),
      p(.828f, .970f, round(.169f))
    ), reps = 1, mirroring = true)
  }
  @JvmField val GEM: RoundedPolygon = shape("Gem") {
    customPolygon(listOf(
      p(.499f, 1.023f, round(.241f, .778f)),
      p(-.005f, .792f, round(.208f)),
      p(.073f, .258f, round(.228f)),
      p(.433f, -.000f, round(.491f))
    ), reps = 1, mirroring = true)
  }
  @JvmField val SUNNY: RoundedPolygon = shape("Sunny") {
    RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = .8f, rounding = r15)
  }
  @JvmField val VERY_SUNNY: RoundedPolygon = shape("VerySunny") {
    customPolygon(listOf(p(.5f, 1.080f, round(.085f)), p(.358f, .843f, round(.085f))), reps = 8)
  }
  @JvmField val COOKIE_4: RoundedPolygon = shape("Cookie4Sided") {
    customPolygon(listOf(p(1.237f, 1.236f, round(.258f)), p(.5f, .918f, round(.233f))), reps = 4)
  }
  @JvmField val COOKIE_6: RoundedPolygon = shape("Cookie6Sided") {
    customPolygon(listOf(p(.723f, .884f, round(.394f)), p(.5f, 1.099f, round(.398f))), reps = 6)
  }
  @JvmField val COOKIE_7: RoundedPolygon = shape("Cookie7Sided") {
    RoundedPolygon.star(numVerticesPerRadius = 7, innerRadius = .75f, rounding = r50).transformed(rotN90)
  }
  @JvmField val COOKIE_9: RoundedPolygon = shape("Cookie9Sided") {
    RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .8f, rounding = r50).transformed(rotN90)
  }
  @JvmField val COOKIE_12: RoundedPolygon = shape("Cookie12Sided") {
    RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = .8f, rounding = r50).transformed(rotN90)
  }
  @JvmField val GHOSTISH: RoundedPolygon = shape("Ghostish") {
    customPolygon(listOf(
      p(.5f, 0f, round(1f)),
      p(1f, 0f, round(1f)),
      p(1f, 1.140f, round(.254f, .106f)),
      p(.575f, .906f, round(.253f))
    ), reps = 1, mirroring = true)
  }
  @JvmField val CLOVER_4: RoundedPolygon = shape("Clover4Leaf") {
    customPolygon(listOf(p(.5f, .074f), p(.725f, -.099f, round(.476f))), reps = 4, mirroring = true)
  }
  @JvmField val CLOVER_8: RoundedPolygon = shape("Clover8Leaf") {
    customPolygon(listOf(p(.5f, .036f), p(.758f, -.101f, round(.209f))), reps = 8)
  }
  @JvmField val BURST: RoundedPolygon = shape("Burst") {
    customPolygon(listOf(p(.5f, -.006f, round(.006f)), p(.592f, .158f, round(.006f))), reps = 12)
  }
  @JvmField val SOFT_BURST: RoundedPolygon = shape("SoftBurst") {
    customPolygon(listOf(p(.193f, .277f, round(.053f)), p(.176f, .055f, round(.053f))), reps = 10)
  }
  @JvmField val BOOM: RoundedPolygon = shape("Boom") {
    customPolygon(listOf(p(.457f, .296f, round(.007f)), p(.5f, -.051f, round(.007f))), reps = 15)
  }
  @JvmField val SOFT_BOOM: RoundedPolygon = shape("SoftBoom") {
    customPolygon(listOf(
      p(.733f, .454f),
      p(.839f, .437f, round(.532f)),
      p(.949f, .449f, round(.439f, 1f)),
      p(.998f, .478f, round(.174f))
    ), reps = 16, mirroring = true)
  }
  @JvmField val FLOWER: RoundedPolygon = shape("Flower") {
    customPolygon(listOf(
      p(.370f, .187f),
      p(.416f, .049f, round(.381f)),
      p(.479f, .001f, round(.095f))
    ), reps = 8, mirroring = true)
  }
  @JvmField val PUFFY: RoundedPolygon = shape("Puffy") {
    customPolygon(listOf(
      p(.5f, .053f),
      p(.545f, -.040f, round(.405f)),
      p(.670f, -.035f, round(.426f)),
      p(.717f, .066f, round(.574f)),
      p(.722f, .128f),
      p(.777f, .002f, round(.360f)),
      p(.914f, .149f, round(.660f)),
      p(.926f, .289f, round(.660f)),
      p(.881f, .346f),
      p(.940f, .344f, round(.126f)),
      p(1.003f, .437f, round(.255f))
    ), reps = 2, mirroring = true).transformed(Matrix().apply { setScale(1f, .742f) })
  }
  @JvmField val PUFFY_DIAMOND: RoundedPolygon = shape("PuffyDiamond") {
    customPolygon(listOf(
      p(.870f, .130f, round(.146f)),
      p(.818f, .357f),
      p(1.0f, .332f, round(.853f))
    ), reps = 4, mirroring = true)
  }
  @JvmField val PIXEL_CIRCLE: RoundedPolygon = shape("PixelCircle") {
    customPolygon(listOf(
      p(.5f, 0f), p(.704f, 0f), p(.704f, .065f), p(.843f, .065f),
      p(.843f, .148f), p(.926f, .148f), p(.926f, .296f), p(1f, .296f)
    ), reps = 2, mirroring = true)
  }
  @JvmField val PIXEL_TRIANGLE: RoundedPolygon = shape("PixelTriangle") {
    customPolygon(listOf(
      p(.110f, .5f), p(.113f, 0f), p(.287f, 0f), p(.287f, .087f), p(.421f, .087f),
      p(.421f, .170f), p(.560f, .170f), p(.560f, .265f), p(.674f, .265f), p(.675f, .344f),
      p(.789f, .344f), p(.789f, .439f), p(.888f, .439f)
    ), reps = 1, mirroring = true)
  }
  @JvmField val BUN: RoundedPolygon = shape("Bun") {
    customPolygon(listOf(
      p(.796f, .5f),
      p(.853f, .518f, round(1f)),
      p(.992f, .631f, round(1f)),
      p(.968f, 1.0f, round(1f))
    ), reps = 2, mirroring = true)
  }
  @JvmField val HEART: RoundedPolygon = shape("Heart") {
    customPolygon(listOf(
      p(.5f, .268f, round(.016f)),
      p(.792f, -.066f, round(.958f)),
      p(1.064f, .276f, round(1f)),
      p(.501f, .946f, round(.129f))
    ), reps = 1, mirroring = true)
  }

  // Loading indicator (Compose LoadingIndicatorDefaults.IndeterminateIndicatorPolygons)

  private val sequence: Array<RoundedPolygon> by lazy {
    arrayOf(SOFT_BURST, COOKIE_9, PENTAGON, PILL, SUNNY, COOKIE_4, OVAL)
  }

  private val morphs: Array<Morph> by lazy {
    val polygons = sequence
    Array(polygons.size) { Morph(polygons[it], polygons[(it + 1) % polygons.size]) }
  }

  private val sequenceScale: Float by lazy { scaleFactor(sequence) }

  /** SoftBurst, Cookie9Sided, Pentagon, Pill, Sunny, Cookie4Sided, Oval: the indeterminate loading sequence. */
  @JvmStatic fun loadingSequence(): Array<RoundedPolygon> = sequence

  /** One morph per pair of the sequence, the last one going back to the first. Built once. */
  @JvmStatic fun loadingMorphs(): Array<Morph> = morphs

  /** [scaleFactor] of [loadingSequence], cached. */
  @JvmStatic fun loadingScaleFactor(): Float = sequenceScale

  /**
   * How much the shapes have to shrink so that, rotating, none of them grows out of the box
   * (Compose LoadingIndicator calculateScaleFactor).
   */
  @JvmStatic fun scaleFactor(polys: Array<RoundedPolygon>): Float {
    var scaleFactor = 1f
    val bounds = FloatArray(4)
    val maxBounds = FloatArray(4)
    for (polygon in polys) {
      polygon.calculateBounds(bounds)
      polygon.calculateMaxBounds(maxBounds)
      val maxWidth = maxBounds[2] - maxBounds[0]
      val maxHeight = maxBounds[3] - maxBounds[1]
      if (maxWidth <= 0f || maxHeight <= 0f) continue
      val scaleX = (bounds[2] - bounds[0]) / maxWidth
      val scaleY = (bounds[3] - bounds[1]) / maxHeight
      // max(scaleX, scaleY) handles shapes like a pill that would throw the whole calculation off.
      scaleFactor = min(scaleFactor, max(scaleX, scaleY))
    }
    return scaleFactor
  }

  /** The morph at [progress] as a path in unit coordinates; [out] is rewound first. */
  @JvmStatic fun toPath(m: Morph, progress: Float, out: Path): Path = m.shapeToPath(progress, out)

  /** The polygon as a path in unit coordinates; [out] is rewound first. */
  @JvmStatic fun toPath(p: RoundedPolygon, out: Path): Path = p.shapeToPath(out)

  private val fitBounds = RectF()

  /**
   * Scales a unit path by [sizePx], centers it on (cx, cy) by its own bounds center and rotates it about
   * (cx, cy). [matrix] is a reusable scratch matrix. Main thread only.
   */
  @Suppress("DEPRECATION")
  @JvmStatic fun fit(path: Path, cx: Float, cy: Float, sizePx: Float, rotationDeg: Float, matrix: Matrix) {
    matrix.setScale(sizePx, sizePx)
    path.transform(matrix)
    path.computeBounds(fitBounds, true)
    matrix.setTranslate(cx - fitBounds.centerX(), cy - fitBounds.centerY())
    if (rotationDeg != 0f) {
      matrix.postRotate(rotationDeg, cx, cy)
    }
    path.transform(matrix)
  }
}
