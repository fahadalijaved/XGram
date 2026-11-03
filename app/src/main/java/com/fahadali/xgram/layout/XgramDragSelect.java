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
package com.fahadali.xgram.layout;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;

/**
 * XGram: long-press drag-select for a grid of selectable items, as in Google Photos (the gallery picker,
 * MediaBottomGalleryController, and the shared media grid, SharedMediaController).
 *
 * <p>The host's long press toggles the item under the finger as before, then calls {@link #start} with that item's
 * new state: selected means the drag selects, unselected means it unselects. From the next move of the same finger
 * this listener takes the gesture (the list's own scroll and every parent's drag, the media sheet's and the profile
 * pager's, are kept out through {@code requestDisallowInterceptTouchEvent}; the item gets a cancel, so no click
 * follows) and gives every item between the anchor and the item under the finger the anchor's state, in reading order
 * (an RTL grid's positions run right to left, so the range follows the grid either way). An item that drops out of
 * the range again (the finger goes back, or past the anchor to its other side) gets its own state back, the farthest
 * first, so the host can restore its place in the selection order. Items the host calls not selectable (the camera
 * tile, date headers) are skipped, and a finger over one (or over a gap) keeps the range where it was; a finger beside
 * the grid picks the nearest item of its row. A light tick plays for every move that changes items. Moves within the
 * touch slop of where the long press was change nothing (its tile may be under the caption field by then).
 *
 * <p>Near the top or bottom of the visible part of the list (the host's edges: the sheet's bar or the profile's tabs
 * cover the rest) the list scrolls by itself, faster the deeper the finger goes (56dp zone, a quadratic ramp up to
 * 16dp a 60 Hz frame, about 960dp a second, at the edge and beyond, frame-time based), and the range follows the
 * items moving under the finger; the host can move the list another way first (the picker's sheet grows to full
 * height). An edge zone the long press was already in works only once the finger heads into it past the touch slop,
 * so a jitter there neither grows the sheet nor scrolls. The host can refuse an item ({@link Host#setDragSelected} false, a selection limit): the range stops before
 * it. Lifting the finger ends it, with the selection as it is.
 */
public final class XgramDragSelect implements RecyclerView.OnItemTouchListener {
  public interface Host {
    /** Whether the item at this adapter position can be selected (false for headers, the camera tile, footers). */
    boolean isDragSelectable (int position);

    /** The item's current selection state. */
    boolean isDragSelected (int position);

    /** Selects or unselects the item; false when it can't (a selection limit), which stops the range before it. */
    boolean setDragSelected (int position, boolean isSelected);

    /** A drag-select was armed by a long press (the finger is still down). */
    default void onDragSelectStarted () { }

    /** The drag-select ended (the finger lifted, or the gesture was cancelled). */
    default void onDragSelectFinished () { }

    /** The top of the list's visible part, in the list's coordinates (what lies above is covered). */
    default int getDragSelectTop (@NonNull RecyclerView list) {
      return list.getPaddingTop();
    }

    /** The bottom of the list's visible part, in the list's coordinates (what lies below is covered or off screen). */
    default int getDragSelectBottom (@NonNull RecyclerView list) {
      return list.getHeight() - list.getPaddingBottom();
    }

    /**
     * The finger is in an edge zone ({@code direction} -1 top, 1 bottom): true when the host moves the list itself
     * on this frame instead of it scrolling (the picker's sheet grows to full height first).
     */
    default boolean onDragSelectEdge (int direction) {
      return false;
    }
  }

  /** The edge zones (dp), capped to a third of the visible part of a short list. */
  private static final float AUTO_SCROLL_ZONE = 56f;
  /** The fastest auto-scroll (dp a 60 Hz frame), at the edge and beyond it. */
  private static final float AUTO_SCROLL_MAX = 16f;
  private static final float FRAME_MS = 1000f / 60f;

  private final RecyclerView list;
  private final Host host;

  private final SparseBooleanArray original = new SparseBooleanArray(); // the state of each item the range changed
  private final Rect rect = new Rect();
  private final int[] location = new int[2];

  private boolean fingerDown;
  private int pointerId = MotionEvent.INVALID_POINTER_ID;
  private float rawX, rawY;

  // where the long press was: a move within the touch slop of it changes nothing, and the edge zone the finger is in
  // there only works once the finger heads into it (edgeGate)
  private final int touchSlop;
  private float startRawX, startRawY;
  private boolean pastSlop, edgeGate;

  private boolean armed, dragging, stale;
  private int anchor = RecyclerView.NO_POSITION, lo, hi; // the range applied now, anchor included
  private boolean select;

  private boolean autoScrollPosted;
  private long lastFrameTime;
  private final Runnable autoScroll = this::onAutoScrollFrame;

  private @Nullable RecyclerView.Adapter<?> observedAdapter;
  private final RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
    @Override
    public void onChanged () {
      invalidate();
    }

    @Override
    public void onItemRangeInserted (int positionStart, int itemCount) {
      if (positionStart <= hi) {
        invalidate(); // the range's positions moved (more items loading after it change nothing)
      }
    }

    @Override
    public void onItemRangeRemoved (int positionStart, int itemCount) {
      if (positionStart <= hi) {
        invalidate();
      }
    }

    @Override
    public void onItemRangeMoved (int fromPosition, int toPosition, int itemCount) {
      invalidate();
    }
  };

  public XgramDragSelect (@NonNull RecyclerView list, @NonNull Host host) {
    this.list = list;
    this.host = host;
    this.touchSlop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
    list.addOnItemTouchListener(this);
    list.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (@NonNull View v) { }

      @Override
      public void onViewDetachedFromWindow (@NonNull View v) {
        fingerDown = false;
        finish();
      }
    });
  }

  /** True from {@link #start} until the finger lifts. */
  public boolean isActive () {
    return armed || dragging;
  }

  /**
   * Arms a drag-select from the item at {@code position}, which the long press has just given the state
   * {@code select}. Nothing happens when that long press's finger is no longer down (an accessibility long click).
   */
  public boolean start (int position, boolean select) {
    if (!fingerDown || position == RecyclerView.NO_POSITION || !host.isDragSelectable(position)) {
      return false;
    }
    reset();
    this.startRawX = rawX;
    this.startRawY = rawY;
    this.pastSlop = false;
    this.edgeGate = true;
    this.anchor = this.lo = this.hi = position;
    this.select = select;
    this.armed = true;
    observe(list.getAdapter());
    disallowParents();
    host.onDragSelectStarted();
    return true;
  }

  /** Ends a drag-select now (a list that swallows its cancel events reports them here). */
  public void cancel () {
    fingerDown = false;
    finish();
  }

  // Touches

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (isActive()) {
          finish(); // the last gesture ended where this listener couldn't see it
        }
        fingerDown = true;
        pointerId = e.getPointerId(0);
        track(e);
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!fingerDown) {
          return false;
        }
        track(e);
        if (armed) {
          armed = false;
          dragging = true;
          disallowParents();
          update();
          return true; // the item gets a cancel, the list stops its own scroll
        }
        return false;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (e.getPointerId(e.getActionIndex()) == pointerId) {
          cancel();
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        cancel();
        return false;
      }
    }
    return false;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        if (dragging) {
          track(e);
          disallowParents(); // again: an item's long press end may have let the parents in
          update();
        }
        break;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (e.getPointerId(e.getActionIndex()) == pointerId) {
          cancel();
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        cancel();
        break;
      }
    }
  }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    if (disallowIntercept && armed) {
      cancel(); // an item kept the gesture for itself: this listener won't see its moves
    }
  }

  private void track (MotionEvent e) {
    final int index = e.findPointerIndex(pointerId);
    if (index < 0) {
      return;
    }
    rawX = e.getX(index) + (e.getRawX() - e.getX());
    rawY = e.getY(index) + (e.getRawY() - e.getY());
  }

  private void disallowParents () {
    final ViewParent parent = list.getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
  }

  // Range

  private void update () {
    if (!dragging || stale) {
      return;
    }
    if (!pastSlop) {
      if (Math.abs(rawX - startRawX) < touchSlop && Math.abs(rawY - startRawY) < touchSlop) {
        return; // a jitter on the anchor changes nothing (its tile may be under the caption field by now)
      }
      pastSlop = true;
    }
    list.getLocationOnScreen(location);
    final float x = rawX - location[0];
    final float y = rawY - location[1];
    final int top = host.getDragSelectTop(list);
    final int bottom = host.getDragSelectBottom(list);
    // only items in sight count: those under the sheet's bar or the profile's tabs are hidden
    final float hitY = bottom > top ? clamp(y, top, bottom - 1) : y;
    final float hitX = clamp(x, 0, Math.max(0, list.getWidth() - 1));
    final int position = findPositionAt(hitX, hitY);
    if (position != RecyclerView.NO_POSITION) {
      applyRange(position);
    }
    if (!autoScrollPosted && gatedEdgeSpeed(y, top, bottom) != 0f) {
      autoScrollPosted = true;
      lastFrameTime = 0;
      list.postOnAnimation(autoScroll);
    }
  }

  /** The selectable item under the point, or the nearest one in its row; none over a header, a gap or nothing. */
  private int findPositionAt (float x, float y) {
    final RecyclerView.LayoutManager manager = list.getLayoutManager();
    if (manager == null) {
      return RecyclerView.NO_POSITION;
    }
    int best = RecyclerView.NO_POSITION;
    float bestDistance = Float.MAX_VALUE;
    final int childCount = list.getChildCount();
    for (int i = 0; i < childCount; i++) {
      final View child = list.getChildAt(i);
      final int position = list.getChildAdapterPosition(child);
      if (position == RecyclerView.NO_POSITION || !host.isDragSelectable(position)) {
        continue;
      }
      manager.getDecoratedBoundsWithMargins(child, rect); // the gaps around a tile count as the tile
      final float top = rect.top + child.getTranslationY(), bottom = rect.bottom + child.getTranslationY();
      if (y < top || y >= bottom) {
        continue;
      }
      final float left = rect.left + child.getTranslationX(), right = rect.right + child.getTranslationX();
      if (x >= left && x < right) {
        return position;
      }
      final float distance = x < left ? left - x : x - right;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = position;
      }
    }
    return best;
  }

  private void applyRange (int target) {
    final int newLo = Math.min(anchor, target), newHi = Math.max(anchor, target);
    if (newLo == lo && newHi == hi) {
      return;
    }
    // items leaving the range get their own state back, the farthest from the anchor first
    if (newLo > lo) {
      for (int position = lo; position < newLo; position++) {
        restore(position);
      }
      lo = newLo;
    }
    if (newHi < hi) {
      for (int position = hi; position > newHi; position--) {
        restore(position);
      }
      hi = newHi;
    }
    // items entering it get the anchor's state, from the anchor outwards (the selection order follows the finger)
    int changed = 0;
    if (newLo < lo) {
      int position = lo - 1;
      for (; position >= newLo; position--) {
        final int result = apply(position);
        if (result < 0) {
          break;
        }
        changed += result;
      }
      lo = position + 1;
    }
    if (newHi > hi) {
      int position = hi + 1;
      for (; position <= newHi; position++) {
        final int result = apply(position);
        if (result < 0) {
          break;
        }
        changed += result;
      }
      hi = position - 1;
    }
    if (changed > 0) {
      com.fahadali.xgram.XgramHaptics.tick(list);
    }
  }

  /** 1 when the item changed, 0 when it is not selectable or had the state already, -1 when the host refused. */
  private int apply (int position) {
    if (!host.isDragSelectable(position)) {
      return 0;
    }
    final boolean current = host.isDragSelected(position);
    if (current == select) {
      return 0;
    }
    if (!host.setDragSelected(position, select)) {
      return -1;
    }
    if (original.indexOfKey(position) < 0) {
      original.put(position, current);
    }
    return 1;
  }

  private void restore (int position) {
    final int index = original.indexOfKey(position);
    if (index < 0) {
      return;
    }
    final boolean wasSelected = original.valueAt(index);
    original.removeAt(index);
    if (host.isDragSelectable(position) && host.isDragSelected(position) != wasSelected) {
      host.setDragSelected(position, wasSelected);
    }
  }

  // Auto-scroll

  /** px a 60 Hz frame, negative upwards, 0 outside the edge zones. */
  private float edgeSpeed (float y, int top, int bottom) {
    final float height = bottom - top;
    if (height <= 0f) {
      return 0f;
    }
    final float zone = Math.min(Screen.dp(AUTO_SCROLL_ZONE), height / 3f);
    final float depth;
    final int direction;
    if (y < top + zone) {
      depth = (top + zone - y) / zone;
      direction = -1;
    } else if (y > bottom - zone) {
      depth = (y - (bottom - zone)) / zone;
      direction = 1;
    } else {
      return 0f;
    }
    final float f = clamp(depth, 0f, 1f);
    return direction * Math.max(1f, f * f * Screen.dp(AUTO_SCROLL_MAX));
  }

  /** {@link #edgeSpeed}, but 0 in the zone the long press was in until the finger moves into it past the touch slop. */
  private float gatedEdgeSpeed (float y, int top, int bottom) {
    final float speed = edgeSpeed(y, top, bottom);
    if (edgeGate) {
      if (speed != 0f && (speed > 0f ? rawY - startRawY : startRawY - rawY) <= touchSlop) {
        return 0f; // still where the long press was: a jitter neither grows the sheet nor scrolls
      }
      edgeGate = false; // outside every zone, or heading into one on purpose: the zones work normally from now on
    }
    return speed;
  }

  private void onAutoScrollFrame () {
    autoScrollPosted = false;
    if (!dragging || stale) {
      lastFrameTime = 0;
      return;
    }
    list.getLocationOnScreen(location);
    final float y = rawY - location[1];
    final float speed = gatedEdgeSpeed(y, host.getDragSelectTop(list), host.getDragSelectBottom(list));
    if (speed == 0f) {
      lastFrameTime = 0;
      return;
    }
    final long now = SystemClock.uptimeMillis();
    final float frameMs = lastFrameTime == 0 ? FRAME_MS : clamp(now - lastFrameTime, 1f, FRAME_MS * 3f);
    lastFrameTime = now;
    final int direction = speed > 0f ? 1 : -1;
    boolean moved = false;
    if (host.onDragSelectEdge(direction)) {
      moved = true;
    } else if (list.canScrollVertically(direction)) {
      int dy = Math.round(speed * frameMs / FRAME_MS);
      if (dy == 0) {
        dy = direction;
      }
      list.scrollBy(0, dy);
      moved = true;
    }
    if (moved) {
      update(); // the items moved under the finger
    }
    if (!autoScrollPosted && dragging && !stale) {
      // still in the zone: keep going (or keep waiting, for more items to load or the sheet to settle)
      autoScrollPosted = true;
      list.postOnAnimation(autoScroll);
    }
  }

  // State

  /** The items moved (a reload, an item removed above the range): the rest of the gesture changes nothing. */
  private void invalidate () {
    if (!isActive()) {
      return;
    }
    stale = true;
    original.clear();
    stopAutoScroll();
  }

  private void stopAutoScroll () {
    if (autoScrollPosted) {
      autoScrollPosted = false;
      list.removeCallbacks(autoScroll);
    }
    lastFrameTime = 0;
  }

  private void observe (@Nullable RecyclerView.Adapter<?> adapter) {
    unobserve();
    if (adapter != null) {
      adapter.registerAdapterDataObserver(observer);
      observedAdapter = adapter;
    }
  }

  private void unobserve () {
    if (observedAdapter != null) {
      observedAdapter.unregisterAdapterDataObserver(observer);
      observedAdapter = null;
    }
  }

  private void reset () {
    armed = dragging = stale = false;
    stopAutoScroll();
    original.clear();
    anchor = lo = hi = RecyclerView.NO_POSITION;
    unobserve();
  }

  private void finish () {
    final boolean wasActive = isActive();
    reset();
    if (wasActive) {
      host.onDragSelectFinished();
    }
  }

  private static float clamp (float value, float min, float max) {
    return value < min ? min : Math.min(value, max);
  }
}
