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
package com.fahadali.xgram.compose.tgx

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import me.vkryl.android.widget.FrameLayoutFix

/** How a row view is measured in a container that is not a RecyclerView: the list's rules for its layout params. */
internal fun measureRowView(child: View, width: Int, viewportHeight: Int, fixedHeight: Int = -1) {
  val lp = child.layoutParams
  val heightSpec = when {
    fixedHeight >= 0 -> View.MeasureSpec.makeMeasureSpec(fixedHeight, View.MeasureSpec.EXACTLY)
    lp != null && lp.height >= 0 -> View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY)
    // A row that fills the list (empty states, progress) gets the page's height, as the list gave it.
    lp != null && lp.height == ViewGroup.LayoutParams.MATCH_PARENT && viewportHeight > 0 ->
      View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.AT_MOST)
    else -> View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
  }
  child.measure(View.MeasureSpec.makeMeasureSpec(maxOf(width, 0), View.MeasureSpec.EXACTLY), heightSpec)
}

/**
 * Keeps the bound views of a Telegram X page whose rows Compose draws ("headless real views"): each controller keeps
 * clicking, toggling and updating its own views, and Compose shows what they say.
 *
 * It lies under the Compose list, fully transparent, takes no touches and is hidden from accessibility and autofill.
 * Its views are still drawn (at alpha 0, which costs nothing on screen), because a view that was never drawn reports
 * only its first invalidate: drawn, every change a view makes (a toggle, a checkbox, progress, new data) reaches
 * [onDescendantInvalidated], and the page reads that row again.
 *
 * Views keep the layout params the adapter gave them (RecyclerView's); rows are measured at the page width and laid out
 * at the top, or at a row's size for tooltips and menus anchored to them ([fixSize]).
 */
@SuppressLint("ViewConstructor")
internal class TgxHeadlessHost(
  context: Context,
  private val onInvalidated: (View) -> Unit
) : ViewGroup(context) {
  private val fixed = HashMap<View, IntArray>()

  init {
    alpha = 0f
    clipChildren = false
    isFocusable = false
    descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
    layoutParams = FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  /** The width rows are measured at: the page's, or the screen's before the page is measured. */
  private fun rowWidth(): Int = if (measuredWidth > 0) measuredWidth else resources.displayMetrics.widthPixels

  private fun viewportHeight(): Int = if (measuredHeight > 0) measuredHeight else resources.displayMetrics.heightPixels

  /** Adds [view] without a layout pass of the page (this may run while Compose measures its list). */
  fun park(view: View) {
    if (view.parent === this) {
      return
    }
    addViewInLayout(view, -1, view.layoutParams ?: generateDefaultLayoutParams(), true)
  }

  fun unpark(view: View) {
    fixed.remove(view)
    if (view.parent === this) {
      removeViewInLayout(view)
    }
  }

  /** Measures and lays out [view] now (after a bind, so its text is laid out and its setters invalidate). */
  fun measureRow(view: View) {
    if (view.parent !== this) {
      return
    }
    val size = fixed[view]
    measureRowView(view, size?.get(0) ?: rowWidth(), viewportHeight(), size?.get(1) ?: -1)
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
  }

  /** The row's size on screen, so a tooltip or menu anchored to [view] lines up with the Compose row. */
  fun fixSize(view: View, width: Int, height: Int) {
    val size = fixed[view]
    if (size != null && size[0] == width && size[1] == height && view.width == width && view.height == height) {
      return
    }
    fixed[view] = intArrayOf(width, height)
    measureRow(view)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(width, height)
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      val size = fixed[child]
      measureRowView(child, size?.get(0) ?: width, height, size?.get(1) ?: -1)
    }
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      child.layout(0, 0, child.measuredWidth, child.measuredHeight)
    }
  }

  override fun checkLayoutParams(p: LayoutParams?): Boolean = p != null

  override fun generateDefaultLayoutParams(): LayoutParams =
    MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

  // Called on Android 8 and newer; the renderer runs only on Android 13 and newer (Expressive design).
  @RequiresApi(Build.VERSION_CODES.O)
  override fun onDescendantInvalidated(child: View, target: View) {
    super.onDescendantInvalidated(child, target)
    onInvalidated(child)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false

  override fun hasOverlappingRendering(): Boolean = false

  override fun shouldDelayChildPressedState(): Boolean = false
}

/**
 * A fallback row in the Compose list: the real Telegram X view (users, chats, edit fields, previews, custom rows) at
 * the list's width and its own height. Touches, IME and images work as in the list.
 */
@SuppressLint("ViewConstructor")
internal class TgxFallbackFrame(context: Context) : ViewGroup(context) {
  /** The page's height, for rows that fill the list (as the list gave them). */
  var viewportHeight: Int = 0

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    var height = 0
    val child = if (childCount > 0) getChildAt(0) else null
    if (child != null && child.visibility != GONE) {
      measureRowView(child, width, viewportHeight)
      height = child.measuredHeight
    }
    setMeasuredDimension(width, height)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val child = if (childCount > 0) getChildAt(0) else null
    child?.layout(0, 0, child.measuredWidth, child.measuredHeight)
  }

  override fun checkLayoutParams(p: LayoutParams?): Boolean = p != null

  override fun generateDefaultLayoutParams(): LayoutParams =
    MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

  override fun shouldDelayChildPressedState(): Boolean = false
}
