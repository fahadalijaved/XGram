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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Lifecycle, ViewModelStore and SavedState of one Compose page. RESUMED while the page has focus, STARTED while
 * another page covers it and DESTROYED with the page, which disposes its composition. Telegram X detaches the view of
 * a covered page, so the composition must not follow the window: scroll position and state are kept on the way back.
 * Main thread only.
 */
class XgramComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
  private val registry = LifecycleRegistry(this)
  private val saved = SavedStateRegistryController.create(this)
  private val store = ViewModelStore()

  override val lifecycle: Lifecycle
    get() = registry

  override val viewModelStore: ViewModelStore
    get() = store

  override val savedStateRegistry: SavedStateRegistry
    get() = saved.savedStateRegistry

  init {
    saved.performAttach()
    saved.performRestore(null)
    // A ComposeView composes only once its lifecycle is at least CREATED.
    registry.currentState = Lifecycle.State.CREATED
  }

  fun moveTo(state: Lifecycle.State) {
    if (registry.currentState != Lifecycle.State.DESTROYED && state != Lifecycle.State.DESTROYED) {
      registry.currentState = state
    }
  }

  fun destroy() {
    if (registry.currentState != Lifecycle.State.DESTROYED) {
      registry.currentState = Lifecycle.State.DESTROYED
      store.clear()
    }
  }
}
