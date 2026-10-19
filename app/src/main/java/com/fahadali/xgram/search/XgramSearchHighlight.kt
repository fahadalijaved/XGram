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
package com.fahadali.xgram.search

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.SettingsAdapter

/**
 * Points at the row a settings search result opened: once the page shows (ViewController.onFocus calls [onFocus]), it
 * scrolls the row into view and pulses it, on the classic list (a press on the row) or on a page drawn in Compose
 * (TgxSettingsRenderer.pulse). Rows that come later (TDLib answers, an expanded part) are waited for up to 3 s.
 */
object XgramSearchHighlight {
  private const val TAG = "XGram"
  private const val WAIT_MS = 3000L
  private const val PRESS_MS = 650L

  private class Pending(
    val match: (ViewController<*>) -> Boolean,
    val row: RowRef,
    val key: String,
    val soft: Boolean,
    val deadline: Long
  )

  private var pending: Pending? = null

  /** The next page that shows and [match]es gets [row] pointed at, within [timeoutMs]. */
  @JvmStatic
  fun expect(match: (ViewController<*>) -> Boolean, row: RowRef, key: String, soft: Boolean, timeoutMs: Long) {
    pending = Pending(match, row, key, soft, SystemClock.uptimeMillis() + timeoutMs)
  }

  @JvmStatic
  fun cancel() {
    pending = null
  }

  /** From ViewController.onFocus: returns at once unless a result is waiting for this page. */
  @JvmStatic
  fun onFocus(c: ViewController<*>) {
    val p = pending ?: return
    if (SystemClock.uptimeMillis() > p.deadline) {
      pending = null
      return
    }
    if (!p.match(c)) {
      return
    }
    pending = null
    // After the page's own onFocus (values refreshed, Compose resumed)
    UI.post {
      if (!c.isDestroyed) {
        run(c, p)
      }
    }
  }

  private fun run(c: ViewController<*>, p: Pending) {
    val list = c.xgramSearchList()
    if (list == null) {
      // A page without a settings list (the classic passcode page): the row's own view
      val id = p.row.id
      val view = if (id != 0) c.getValue().findViewById<View>(id) else null
      if (view != null && view.isShown) {
        // The classic passcode page is a ScrollView: bring the row into view (a jump), then press it there
        view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height), true)
        view.post {
          if (!c.isDestroyed) {
            press(view)
          }
        }
      } else {
        giveUp(c, p)
      }
      return
    }
    val adapter = list.adapter as? SettingsAdapter
    if (adapter == null) {
      giveUp(c, p)
      return
    }
    Attempt(c, list, adapter, p).start()
  }

  private fun giveUp(c: ViewController<*>, p: Pending) {
    if (BuildConfig.DEBUG && !p.soft) {
      Log.w(TAG, "settings search: no row for ${p.key} on ${c.javaClass.simpleName}")
    }
  }

  /** Finds the row now, or once the page adds it (an expanded part, rows after a TDLib answer). */
  private class Attempt(
    val c: ViewController<*>,
    val list: RecyclerView,
    val adapter: SettingsAdapter,
    val p: Pending
  ) : RecyclerView.AdapterDataObserver() {
    private var done = false
    private var registered = false
    private var revealed = false
    private val timeout = Runnable {
      if (!done) {
        finish()
        XgramSearchHighlight.giveUp(c, p)
      }
    }
    private val recheck = Runnable { attempt() }

    fun start() {
      if (attempt()) {
        return
      }
      adapter.registerAdapterDataObserver(this)
      registered = true
      list.postDelayed(timeout, XgramSearchHighlight.WAIT_MS)
    }

    private fun attempt(): Boolean {
      if (done) {
        return true
      }
      if (c.isDestroyed) {
        finish()
        return true
      }
      var index = p.row.indexIn(adapter.items)
      if (index < 0 && !revealed && p.row.reveal != 0) {
        revealed = true
        if (c.xgramSearchReveal(p.row.reveal)) {
          index = p.row.indexIn(adapter.items)
        }
      }
      if (index < 0) {
        return false
      }
      finish()
      val at = index
      // After the layout the change scheduled
      list.post {
        if (!c.isDestroyed) {
          XgramSearchHighlight.point(c, list, at)
        }
      }
      return true
    }

    private fun finish() {
      done = true
      list.removeCallbacks(timeout)
      list.removeCallbacks(recheck)
      if (registered) {
        registered = false
        try {
          adapter.unregisterAdapterDataObserver(this)
        } catch (ignored: IllegalStateException) {
          // Not registered.
        }
      }
    }

    private fun changed() {
      if (!done) {
        list.removeCallbacks(recheck)
        list.post(recheck)
      }
    }

    override fun onChanged() = changed()

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = changed()

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = changed()

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = changed()
  }

  /** Scrolls to the row and pulses it: in Compose on a page drawn by compose/tgx, else a press on the row. */
  private fun point(c: ViewController<*>, list: RecyclerView, position: Int) {
    val renderer = c.xgramSearchRenderer()
    if (renderer != null) {
      renderer.pulse(position)
    } else {
      flash(list, position, c.xgramSearchTopOffset(), c.xgramSearchMinTop())
    }
  }

  /** The row in view between [minTop] and the list's end: pulse it; else scroll it to [offset] first. */
  private fun flash(list: RecyclerView, position: Int, offset: Int, minTop: Int) {
    val manager = list.layoutManager as? LinearLayoutManager ?: return
    val view = manager.findViewByPosition(position)
    if (view != null && view.top >= minTop && view.bottom <= list.height) {
      press(view)
      return
    }
    manager.scrollToPositionWithOffset(position, offset)
    list.post {
      val target = manager.findViewByPosition(position)
      if (target != null) {
        press(target)
      }
    }
  }

  /** The row's pressed state for a moment: its ripple from the middle (the same pulse as XGram's own pages). */
  private fun press(view: View) {
    val background = view.background
    if (background != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      background.setHotspot(view.width / 2f, view.height / 2f)
    }
    view.isPressed = true
    view.postDelayed({ view.isPressed = false }, PRESS_MS)
  }
}
