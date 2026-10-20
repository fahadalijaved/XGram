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

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable

/**
 * Compose 1.12 crashed with "LayoutNode N not found in RectList" (RectManager.recalculateRectIfDirty) while a lazy
 * list's prefetch measured a Material 3 ListItem it had composed ahead of time (the owner's Fold, 2026-10-20). XGram's
 * lazy lists are settings and search pages: prefetching buys nothing there, so pausable composition in prefetch is off
 * for the whole app and the settings lists don't prefetch at all.
 */
@OptIn(ExperimentalFoundationApi::class)
object XgramComposeFlags {
  init {
    ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
  }

  /** Applies the flags; called before a ComposeView is made, so it runs before the first composition. */
  @JvmStatic
  fun ensure () = Unit
}

/** A lazy list strategy that never prefetches. */
@OptIn(ExperimentalFoundationApi::class)
object XgramNoPrefetch : LazyListPrefetchStrategy {
  override fun LazyListPrefetchScope.onScroll (delta: Float, layoutInfo: LazyListLayoutInfo) = Unit
  override fun LazyListPrefetchScope.onVisibleItemsUpdated (layoutInfo: LazyListLayoutInfo) = Unit
  override fun NestedPrefetchScope.onNestedPrefetch (firstVisibleItemIndex: Int) = Unit
}

/** The list state of an XGram settings list: no prefetch (see [XgramComposeFlags]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberXgramListState (): LazyListState = rememberLazyListState(prefetchStrategy = XgramNoPrefetch)

/** [rememberXgramListState] outside a composition. */
@OptIn(ExperimentalFoundationApi::class)
fun xgramListState (): LazyListState = LazyListState(0, 0, XgramNoPrefetch)
