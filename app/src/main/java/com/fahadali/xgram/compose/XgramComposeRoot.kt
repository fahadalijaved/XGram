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
package com.fahadali.xgram.compose

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.vkryl.android.widget.FrameLayoutFix
import org.thunderdog.challegram.navigation.HeaderView
import org.thunderdog.challegram.tool.Views
import org.thunderdog.challegram.widget.RootFrameLayout

/**
 * The view of a Compose page. Telegram X puts a page below its header (NavigationLayout's top padding is the header
 * plus the status bar); this view pulls itself up by that much, so the page spans the whole height and draws its own
 * Material 3 app bar behind the status bar and the see-through header. The top inset comes from the same place and
 * formula as NavigationLayout.applyTopInset, so both stay in step. The view has no background: Compose paints it.
 */
@SuppressLint("ViewConstructor")
class XgramComposeRoot(
  context: Context,
  owner: XgramComposeOwner,
  private val onTopInset: (Int) -> Unit,
  content: @Composable () -> Unit
) : FrameLayoutFix(context), RootFrameLayout.InsetsChangeListener {
  private var rootFrame: RootFrameLayout? = null

  val composeView: ComposeView = ComposeView(context).apply {
    // Kept while Telegram X detaches the view of a covered page; disposed with the page.
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    // OnBackPressedDispatcherOwner: the activity's, from the decor view.
    setContent(content)
  }

  init {
    addView(composeView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    layoutParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    val root = Views.findAncestor(this, RootFrameLayout::class.java, true)
    rootFrame = root
    if (root != null) {
      root.addInsetsChangeListener(this)
      applyTopInset(root.topInset)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    rootFrame?.removeInsetsChangeListener(this)
    rootFrame = null
  }

  override fun onInsetsChanged(
    viewGroup: RootFrameLayout?,
    effectiveInsets: Rect?,
    effectiveInsetsWithoutIme: Rect?,
    systemInsets: Rect?,
    systemInsetsWithoutIme: Rect?,
    isUpdate: Boolean
  ) {
    if (effectiveInsets != null) {
      applyTopInset(effectiveInsets.top)
    }
  }

  private fun applyTopInset(top: Int) {
    onTopInset(top)
    val pull = HeaderView.getSize(false) + top
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    if (params.topMargin != -pull) {
      params.topMargin = -pull
      layoutParams = params
    }
  }
}
