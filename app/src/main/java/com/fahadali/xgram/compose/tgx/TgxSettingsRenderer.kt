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

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.fahadali.xgram.XgramSettings
import com.fahadali.xgram.appearance.XgramExpressive
import com.fahadali.xgram.appearance.XgramM3Bars
import com.fahadali.xgram.appearance.XgramMotion
import com.fahadali.xgram.compose.XgramComposeOwner
import com.fahadali.xgram.compose.XgramComposeRoot
import com.fahadali.xgram.compose.ui.PageBlock
import com.fahadali.xgram.compose.ui.PageScope
import org.thunderdog.challegram.R
import org.thunderdog.challegram.navigation.HeaderView
import org.thunderdog.challegram.navigation.NavigationController
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.theme.ColorState
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.theme.ThemeChangeListener
import org.thunderdog.challegram.theme.ThemeDelegate
import org.thunderdog.challegram.theme.ThemeManager
import org.thunderdog.challegram.ui.ListItem
import org.thunderdog.challegram.ui.SettingsAdapter
import org.thunderdog.challegram.widget.SliderWrapView
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Draws a Telegram X SettingsAdapter page in Compose Material 3 Expressive, with the page's own code untouched
 * ("headless real views"). Only with Expressive design on, the "New look for Telegram X pages" setting on and the page
 * listed in [TgxComposePages]; otherwise [attachIfAllowed] returns null and the page is exactly the classic one.
 *
 * The page's RecyclerView stays attached, parked at height 0, so the adapter keeps notifying and every update path of
 * the controller still works. An AdapterDataObserver mirrors the items: rows are kept by ListItem identity; a row's view
 * is created and bound by the adapter the first time it is composed and lives in [TgxHeadlessHost]. Taps go to the view
 * (performClick), so onClick, toggles, radio groups and dialogs run as before; Compose reads every row back from its
 * view whenever the adapter notifies or the view reports a change. Rows the generic mapping does not know show the real
 * view (AndroidView). Telegram X's header (back, menus, search, Done) stays.
 *
 * Hooks: RecyclerViewController and EditBaseController (attach, inset, language, focus, swipe-back, destroy) and their
 * LayoutManagers (findViewByPosition and scrolling go to the renderer while the list is empty).
 */
class TgxSettingsRenderer private constructor(
  internal val controller: ViewController<*>,
  private val recyclerView: RecyclerView,
  private val adapter: SettingsAdapter,
  internal val hints: TgxPageHints,
  bottomInset: Int
) : ThemeChangeListener {
  companion object {
    private const val TAG = "XGram"

    /** Rows of a group share a content type per view type, so the list reuses a row only for one alike. */
    internal const val TYPE_ROW_BASE = 100_000

    /**
     * Draws [c]'s list in Compose when allowed, else returns null and changes nothing. [index]: where the Compose list
     * goes in [parent] (-1: right above the list). Call once the adapter is set.
     */
    @JvmStatic
    fun attachIfAllowed(c: ViewController<*>, parent: ViewGroup, rv: RecyclerView, index: Int, bottomInset: Int): TgxSettingsRenderer? {
      if (!XgramExpressive.isEnabled() || !XgramSettings.instance().composeTgxPages()) {
        return null
      }
      val adapter = rv.adapter as? SettingsAdapter ?: return null
      val hints = TgxComposePages.hints(c) ?: return null
      return try {
        TgxSettingsRenderer(c, rv, adapter, hints, bottomInset).also { it.attach(parent, index) }
      } catch (t: Throwable) {
        Log.e(TAG, "tgx: cannot draw ${c.javaClass.simpleName} in Compose", t)
        null
      }
    }
  }

  private val context: Context = recyclerView.context
  internal val owner = XgramComposeOwner()
  internal val host = TgxHeadlessHost(context) { child -> onViewInvalidated(child) }
  internal val composeHost = TgxComposeHost(this)
  internal val listState = LazyListState()
  internal val largeTitle: Boolean = hints.largeTitle(controller)

  internal val structureVersion = mutableIntStateOf(0)
  internal val langVersion = mutableIntStateOf(0)
  internal val themeVersion = mutableIntStateOf(0)
  internal val isDark = mutableStateOf(Theme.isDark())
  internal val bottomInset = mutableIntStateOf(bottomInset)
  internal val scrollRequest = mutableIntStateOf(0)

  private var root: XgramComposeRoot? = null
  private var destroyed = false

  // Rows in adapter order (after the last structure sync) and the items they had then (for grouping).
  internal val rows = ArrayList<TgxRow>()
  private var rowItems: List<ListItem> = emptyList()
  private val byView = HashMap<View, TgxRow>()
  private val rowBlock = IdentityHashMap<TgxRow, Int>()
  private var serial = 0L

  private var structureDirty = true
  private val pendingDirty: MutableSet<ListItem> = Collections.newSetFromMap(IdentityHashMap<ListItem, Boolean>())
  private var markAllDirty = false
  private val rereadQueue = LinkedHashSet<TgxRow>()
  private var rereadAll = false
  private var syncPosted = false
  private val syncRunnable = Runnable {
    syncPosted = false
    sync()
  }

  private val noSwipeAreas = HashMap<Any, () -> Rect?>()
  private var pendingScroll: PendingScroll? = null

  private class PendingScroll(val row: TgxRow?, val offset: Int, val smooth: Boolean)

  private val observer = object : RecyclerView.AdapterDataObserver() {
    override fun onChanged() {
      structureDirty = true
      markAllDirty = true
      post()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
      val items = adapter.items
      val start = positionStart.coerceAtLeast(0)
      val end = minOf(positionStart + itemCount, items.size)
      for (i in start until end) {
        pendingDirty.add(items[i])
      }
      post()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
      onItemRangeChanged(positionStart, itemCount)
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
      structureDirty = true
      post()
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
      structureDirty = true
      post()
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
      structureDirty = true
      post()
    }
  }

  private fun attach(parent: ViewGroup, index: Int) {
    val root = XgramComposeRoot(context, owner, {}, pullUnderHeader = false) {
      TgxSettingsScreen(this)
    }
    syncStructure()
    // From here on the page changes: Telegram X's list look goes, the rows are drawn in Compose.
    XgramExpressive.detachListDecoration(recyclerView)
    XgramM3Bars.detachLargeTitle(recyclerView)
    XgramMotion.detachScrollLinkFrom(controller, recyclerView)
    adapter.registerAdapterDataObserver(observer)
    ThemeManager.instance().addThemeListener(this)
    // Parked, not GONE: attached and laid out at height 0 (no children), so updates are still consumed.
    recyclerView.visibility = View.INVISIBLE
    val params = recyclerView.layoutParams
    if (params != null) {
      params.height = 0
      recyclerView.layoutParams = params
    }
    val at = if (index >= 0) index.coerceAtMost(parent.childCount) else parent.indexOfChild(recyclerView) + 1
    parent.addView(host, at)
    parent.addView(root, at + 1)
    this.root = root
    // Recomposition needs at least STARTED; RESUMED while the page has focus.
    owner.moveTo(Lifecycle.State.STARTED)
    if (largeTitle) {
      HeaderView.xgramSetTitleAlpha(controller, 0f)
    }
  }

  /** The Compose list's view (Telegram X moves it for the music player bar). */
  fun getView(): View? = root

  // Mirror of the adapter

  private fun post() {
    if (!syncPosted && !destroyed) {
      syncPosted = true
      host.postOnAnimation(syncRunnable)
    }
  }

  private fun prepare() {
    if (structureDirty) {
      syncStructure()
    }
    if (markAllDirty) {
      markAllDirty = false
      pendingDirty.clear()
      for (row in rows) {
        row.dirty = true
      }
    } else if (pendingDirty.isNotEmpty()) {
      for (row in rows) {
        if (pendingDirty.contains(row.item)) {
          row.dirty = true
        }
      }
      pendingDirty.clear()
    }
  }

  private fun syncStructure() {
    structureDirty = false
    val items = adapter.items
    val old = IdentityHashMap<ListItem, ArrayDeque<TgxRow>>()
    for (row in rows) {
      old.getOrPut(row.item) { ArrayDeque() }.addLast(row)
    }
    val newRows = ArrayList<TgxRow>(items.size)
    var changed = rows.size != items.size
    for (i in items.indices) {
      val item = items[i]
      val row = old[item]?.removeFirstOrNull() ?: TgxRow(item, ++serial)
      if (!changed && rows[i] !== row) {
        changed = true
      }
      row.index = i
      if (row.viewType != item.viewType) {
        row.viewType = item.viewType
        row.dirty = true
        changed = true
      }
      newRows.add(row)
    }
    for (queue in old.values) {
      for (row in queue) {
        row.index = -1
        releaseHolder(row)
      }
    }
    rows.clear()
    rows.addAll(newRows)
    rowItems = ArrayList(items)
    if (changed) {
      structureVersion.intValue++
    }
  }

  /** Once per frame: new structure, rebinds of changed rows on screen, rows whose views reported a change. */
  private fun sync() {
    if (destroyed) {
      return
    }
    prepare()
    for (row in rows) {
      if (row.dirty && row.composed > 0 && row.state != null) {
        bind(row)
        write(row)
      }
    }
    if (rereadAll) {
      rereadAll = false
      rereadQueue.clear()
      for (row in rows) {
        if (!row.dirty) {
          reread(row)
        }
      }
    } else if (rereadQueue.isNotEmpty()) {
      val queue = ArrayList(rereadQueue)
      rereadQueue.clear()
      for (row in queue) {
        if (!row.dirty) {
          reread(row)
        }
      }
    }
  }

  /** Creates (when needed) and binds the row's view through the adapter, as RecyclerView would. */
  private fun bind(row: TgxRow) {
    val index = row.index
    val items = adapter.items
    if (index < 0 || index >= items.size || items[index] !== row.item) {
      return
    }
    val type = adapter.getItemViewType(index)
    var holder = row.holder
    if (holder == null || row.holderType != type) {
      if (holder != null) {
        releaseHolder(row)
      }
      holder = adapter.createViewHolder(host, type)
      row.holder = holder
      row.holderType = type
      byView[holder.itemView] = row
      val frame = row.frame
      if (frame != null) {
        frame.addView(holder.itemView)
        adapter.onViewAttachedToWindow(holder)
      } else {
        host.park(holder.itemView)
      }
    }
    row.dirty = false
    adapter.bindViewHolder(holder, index)
    row.kind = TgxRead.kindOf(context, row.item, type, holder.itemView, hints)
    if (row.kind == TgxRowKind.LOADING && holder.itemView.parent === host) {
      // Compose shows its own indicator: the hidden spinner must not redraw every frame.
      holder.itemView.visibility = View.INVISIBLE
    }
    host.measureRow(holder.itemView)
  }

  private fun releaseHolder(row: TgxRow) {
    val holder = row.holder ?: return
    row.holder = null
    row.holderType = Int.MIN_VALUE
    val view = holder.itemView
    byView.remove(view)
    rereadQueue.remove(row)
    val parent = view.parent
    if (parent === host) {
      host.unpark(view)
    } else if (parent is ViewGroup) {
      adapter.onViewDetachedFromWindow(holder)
      parent.removeView(view)
    }
    view.translationX = 0f
    view.translationY = 0f
    adapter.onViewRecycled(holder)
  }

  private fun write(row: TgxRow) {
    val snap = readNow(row)
    val state = row.state
    if (state == null) {
      row.state = mutableStateOf(snap)
    } else if (state.value != snap) {
      state.value = snap
    }
  }

  private fun readNow(row: TgxRow): TgxRowSnap {
    val holder = row.holder ?: return TgxRowSnap.HIDDEN
    return TgxRead.read(row.kind, holder.itemView, row.item, row.holderType)
  }

  private fun reread(row: TgxRow) {
    val state = row.state ?: return
    if (row.holder == null) {
      return
    }
    val snap = readNow(row)
    if (state.value != snap) {
      state.value = snap
    }
  }

  private fun onViewInvalidated(child: View) {
    val row = byView[child] ?: return
    if (row.state != null && rereadQueue.add(row)) {
      post()
    }
  }

  /** Every row again at the next frame (after a tap: a tap may change other rows). */
  internal fun rereadAllSoon() {
    if (!destroyed) {
      rereadAll = true
      post()
    }
  }

  /**
   * What the row shows, read while it is composed: binds it on its first composition (or when it changed while off
   * screen). Reading the state subscribes the row, so a change recomposes this row only.
   */
  internal fun snapOf(row: TgxRow): TgxRowSnap {
    if (!destroyed) {
      prepare()
      if (row.holder == null || row.dirty) {
        bind(row)
        if (row.holder != null) {
          write(row)
        }
      } else if (row.state == null) {
        row.state = mutableStateOf(readNow(row))
      }
    }
    return row.state?.value ?: TgxRowSnap.HIDDEN
  }

  // Blocks of the Compose list

  /**
   * The list in Compose: headers, descriptions, and segmented groups exactly where the RecyclerView's M3E decoration
   * draws them (XgramExpressive.segmentOf). Rows outside any group form groups of their own; shadows, separators and
   * offsets are dropped. Runs when the structure or the language changes.
   */
  internal fun buildBlocks(): List<PageBlock> {
    val r = this
    if (!destroyed) {
      prepare()
    }
    rowBlock.clear()
    val scope = PageScope()
    if (largeTitle) {
      scope.add("tgx:title", PageBlock.TYPE_ITEM, emptyList()) { TgxLargeTitle(r) }
    }
    val list = rowItems
    val n = minOf(rows.size, list.size)
    var i = 0
    while (i < n) {
      val row = rows[i]
      val type = row.viewType
      when {
        TgxRead.isHiddenType(type) -> i++
        TgxRead.isHeaderType(type) -> {
          val top = if (scope.blocks.isEmpty()) 8.dp else 20.dp
          rowBlock[row] = scope.blocks.size
          scope.add(row.key, PageBlock.TYPE_HEADER, emptyList()) { TgxRowBlock(r, row, null, XgramExpressive.SEGMENT_NONE, top) }
          i++
        }
        TgxRead.isDescriptionType(type) || TgxRead.isStandaloneType(type) || !XgramExpressive.isRowType(type) -> {
          rowBlock[row] = scope.blocks.size
          val blockType = if (TgxRead.isDescriptionType(type)) PageBlock.TYPE_DESCRIPTION else PageBlock.TYPE_ITEM
          scope.add(row.key, blockType, emptyList()) { TgxRowBlock(r, row, null, XgramExpressive.SEGMENT_NONE, 0.dp) }
          i++
        }
        else -> {
          val grouped = XgramExpressive.segmentOf(list, i) != XgramExpressive.SEGMENT_NONE
          val members = ArrayList<TgxRow>()
          var j = i
          while (j < n) {
            val t = rows[j].viewType
            if (TgxRead.isSeparatorType(t) || (TgxRead.isHiddenType(t) && XgramExpressive.isRowType(t))) {
              j++
              continue
            }
            if (!XgramExpressive.isRowType(t) || TgxRead.isStandaloneType(t) || TgxRead.isHiddenType(t)) {
              break
            }
            val segment = XgramExpressive.segmentOf(list, j)
            if (grouped) {
              if (segment == XgramExpressive.SEGMENT_NONE ||
                (members.isNotEmpty() && (segment == XgramExpressive.SEGMENT_FIRST || segment == XgramExpressive.SEGMENT_SINGLE))) {
                break
              }
              members.add(rows[j])
              j++
              if (segment == XgramExpressive.SEGMENT_LAST || segment == XgramExpressive.SEGMENT_SINGLE) {
                break
              }
            } else {
              if (segment != XgramExpressive.SEGMENT_NONE) {
                break
              }
              members.add(rows[j])
              j++
            }
          }
          if (members.isEmpty()) {
            i++
            continue
          }
          val base = scope.blocks.size
          val count = members.size
          scope.group {
            members.forEachIndexed { k, m ->
              rowBlock[m] = base + k
              val segment = when {
                count == 1 -> XgramExpressive.SEGMENT_SINGLE
                k == 0 -> XgramExpressive.SEGMENT_FIRST
                k == count - 1 -> XgramExpressive.SEGMENT_LAST
                else -> XgramExpressive.SEGMENT_MIDDLE
              }
              custom(key = m.key, id = m.item.id, type = TYPE_ROW_BASE + m.viewType) { shapes ->
                TgxRowBlock(r, m, shapes, segment, 0.dp)
              }
            }
          }
          i = j
        }
      }
    }
    return scope.blocks
  }

  // Actions

  /** A tap on a row: the view's own click (controller.onClick gets the real view), then the row is read again. */
  internal fun click(row: TgxRow) {
    if (destroyed) {
      return
    }
    val view = row.holder?.itemView ?: return
    if (!view.isEnabled) {
      return
    }
    val target = TgxRead.clickTarget(view) ?: return
    syncAnchor(row, view)
    target.performClick()
    if (!destroyed) {
      reread(row)
      rereadAllSoon()
    }
  }

  internal fun longClick(row: TgxRow) {
    if (destroyed) {
      return
    }
    val view = row.holder?.itemView ?: return
    if (!view.isEnabled || !view.isLongClickable) {
      return
    }
    syncAnchor(row, view)
    view.performLongClick()
    if (!destroyed) {
      reread(row)
      rereadAllSoon()
    }
  }

  /** A slider moved to [index]: the slider view's own path (adapter.onSliderValueChanged, the page's listeners). */
  internal fun slide(row: TgxRow, index: Int) {
    if (destroyed) {
      return
    }
    val view = row.holder?.itemView as? SliderWrapView ?: return
    val count = row.item.sliderValues?.size ?: return
    if (count < 2) {
      return
    }
    view.onValueChanged(null, index.coerceIn(0, count - 1) / (count - 1f))
    view.onSetStateChanged(null, false)
    reread(row)
  }

  /**
   * Puts the headless view where the Compose row is (its size, and a translation to its place), so tooltips, popups
   * and menus anchored to it (or to its toggler) show at the row. Only when needed: never while scrolling.
   */
  private fun syncAnchor(row: TgxRow, view: View) {
    if (view.parent !== host) {
      return
    }
    val coordinates = row.coordinates?.takeIf { it.isAttached } ?: return
    host.fixSize(view, coordinates.size.width, coordinates.size.height)
    val position = coordinates.positionInWindow()
    val hostLocation = IntArray(2)
    host.getLocationInWindow(hostLocation)
    view.translationX = position.x - hostLocation[0] - view.left
    view.translationY = position.y - hostLocation[1] - view.top
  }

  // Fallback rows: the real view in the Compose list

  internal fun adopt(row: TgxRow, frame: TgxFallbackFrame, segment: Int) {
    row.frame = frame
    frame.viewportHeight = host.height
    val holder = row.holder ?: return
    val view = holder.itemView
    if (view.parent !== frame) {
      val parent = view.parent
      if (parent === host) {
        host.unpark(view)
      } else if (parent is ViewGroup) {
        parent.removeView(view)
      }
      view.translationX = 0f
      view.translationY = 0f
      frame.addView(view)
      adapter.onViewAttachedToWindow(holder)
    }
    setSegment(row, segment)
  }

  internal fun release(row: TgxRow, frame: TgxFallbackFrame) {
    if (row.frame === frame) {
      row.frame = null
    }
    val holder = row.holder
    if (holder != null && holder.itemView.parent === frame) {
      adapter.onViewDetachedFromWindow(holder)
      frame.removeView(holder.itemView)
      if (!destroyed) {
        host.park(holder.itemView)
        host.measureRow(holder.itemView)
      }
    } else {
      frame.removeAllViews()
    }
  }

  /** A fallback row's M3E tile (XgramExpressive.bindListRow) takes its place in the group: corners and color. */
  internal fun setSegment(row: TgxRow, segment: Int) {
    val view = row.holder?.itemView ?: return
    (view.getTag(R.id.xgram_m3e_row) as? XgramExpressive.MorphDrawable)?.setSegment(segment)
  }

  // Scrolling (LayoutManager seam and Telegram X's scroll-to-top)

  /** The bound view of the item at [position] (LayoutManager.findViewByPosition); null when it has none yet. */
  fun viewAt(position: Int): View? {
    if (destroyed) {
      return null
    }
    if (structureDirty) {
      syncStructure()
    }
    val row = rows.getOrNull(position) ?: return null
    if (adapter.items.getOrNull(position) !== row.item) {
      return null
    }
    return row.holder?.itemView
  }

  /** LayoutManager.scrollToPositionWithOffset: the item's top [offset] px below the list's top. */
  fun scrollTo(position: Int, offset: Int, smooth: Boolean) {
    if (destroyed) {
      return
    }
    if (structureDirty) {
      syncStructure()
    }
    val row = rows.getOrNull(position) ?: return
    pendingScroll = PendingScroll(row, offset, smooth)
    scrollRequest.intValue++
  }

  fun scrollToTop() {
    if (destroyed) {
      return
    }
    pendingScroll = PendingScroll(null, 0, true)
    scrollRequest.intValue++
  }

  internal suspend fun applyPendingScroll() {
    val request = pendingScroll ?: return
    pendingScroll = null
    val index = if (request.row == null) 0 else blockIndexOf(request.row)
    if (index < 0) {
      return
    }
    if (request.smooth) {
      listState.animateScrollToItem(index, -request.offset)
    } else {
      listState.scrollToItem(index, -request.offset)
    }
  }

  private fun blockIndexOf(row: TgxRow): Int {
    rowBlock[row]?.let { return it }
    // A shadow or a separator: the next row that is drawn.
    val start = rows.indexOf(row)
    if (start < 0) {
      return -1
    }
    for (k in start until rows.size) {
      rowBlock[rows[k]]?.let { return it }
    }
    return -1
  }

  /** The list scrolled: the large title fades Telegram X's small title in, the header lifts. */
  internal fun onListScrolled(offset: Int, titleStart: Float, titleDistance: Float) {
    if (destroyed) {
      return
    }
    if (largeTitle) {
      HeaderView.xgramSetTitleAlpha(controller, if (offset == Int.MAX_VALUE) 1f else (offset - titleStart) / titleDistance)
    }
    XgramMotion.updateScrollLink(controller, offset, offset > 0)
  }

  // Page events (from the controller's hooks)

  fun setBottomInset(px: Int) {
    bottomInset.intValue = px
  }

  /** After the adapter's own language event (it rebinds every row): titles, headers and RTL again. */
  fun onLanguageChanged() {
    if (!destroyed) {
      langVersion.intValue++
      rereadAllSoon()
    }
  }

  fun onFocus() {
    if (!destroyed) {
      owner.moveTo(Lifecycle.State.RESUMED)
      // Values may have changed on a page opened from this one.
      rereadAllSoon()
    }
  }

  fun onBlur() {
    if (!destroyed) {
      owner.moveTo(Lifecycle.State.STARTED)
    }
  }

  internal fun noSwipeBack(key: Any, bounds: (() -> Rect?)?) {
    if (bounds == null) {
      noSwipeAreas.remove(key)
    } else {
      noSwipeAreas[key] = bounds
    }
  }

  /** False inside a slider: swipe-back must not start there. */
  fun canSlideBackFrom(nav: NavigationController?, x: Float, y: Float): Boolean {
    if (nav == null || noSwipeAreas.isEmpty()) {
      return true
    }
    val location = IntArray(2)
    nav.getValue().getLocationInWindow(location)
    val wx = (location[0] + x).toInt()
    val wy = (location[1] + y).toInt()
    for (bounds in noSwipeAreas.values) {
      val rect = bounds()
      if (rect != null && rect.contains(wx, wy)) {
        return false
      }
    }
    return true
  }

  // Theme

  private fun checkDark() {
    val dark = Theme.isDark()
    if (isDark.value != dark) {
      isDark.value = dark
    }
  }

  internal fun themeChanged() {
    if (!destroyed) {
      checkDark()
      themeVersion.intValue++
    }
  }

  override fun needsTempUpdates(): Boolean = false

  override fun onThemeColorsChanged(areTemp: Boolean, state: ColorState?) {
    if (!areTemp) {
      themeChanged()
    }
  }

  override fun onThemeChanged(fromTheme: ThemeDelegate?, toTheme: ThemeDelegate?) {
    themeChanged()
  }

  fun destroy() {
    if (destroyed) {
      return
    }
    destroyed = true
    host.removeCallbacks(syncRunnable)
    try {
      adapter.unregisterAdapterDataObserver(observer)
    } catch (ignored: IllegalStateException) {
      // Not registered.
    }
    ThemeManager.instance().removeThemeListener(this)
    for (row in rows) {
      releaseHolder(row)
    }
    rows.clear()
    byView.clear()
    rereadQueue.clear()
    noSwipeAreas.clear()
    owner.destroy()
  }
}
