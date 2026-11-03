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
package com.fahadali.xgram.chats;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;

/**
 * Swipe between emoji categories (Messages > Gestures, XgramSettings.emojiCategorySwipe), on the emoji panel's grid
 * of emoji (EmojiLayoutRecyclerController with EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID; the grid calls in first from
 * its touch methods and its canScrollHorizontally).
 *
 * <p>A sideways swipe jumps to the next or the previous category with the animated scroll a tap on its icon makes,
 * Recent, the six emoji categories and then each custom emoji pack in turn. It goes once the finger lets go, after a
 * fling (400dp/s and 24dp at least, the pager's own numbers) or a slower drag a quarter of the grid's width long (48 to
 * 120dp); a shorter slow drag does nothing. The finger moving left goes to the next category and moving right to the
 * previous one; with RTL it is the other way round, as the pages are. The category it steps from is the one the tabs
 * light (where the last jump went, until the grid is scrolled), so quick swipes keep stepping, and near the grid's end,
 * where a jump may not move it, each swipe still lights the next pack.
 *
 * <p>A touch turns into a swipe once it has moved the touch slop sideways, under 34 degrees from horizontal, with a
 * category that way: the grid then owns it (the emoji under the finger gets a cancel; the pager and the back swipe no
 * longer see it). The grid also tells the pager it can scroll that way (canScrollHorizontally), so the pager leaves such
 * swipes alone. With no category that way nothing changes: past the last one the pager takes the swipe to Stickers and
 * follows the finger as before; before the first one nothing happens. If the pager stays out of a swipe to Stickers
 * (it does for a whole gesture when the finger first went the other way, toward a category, by half the touch slop;
 * a pixel of jitter does not count, see canScrollHorizontally), the grid takes
 * it past the pager's slop and opens Stickers on a fling or a long enough drag. A vertical scroll, a long press (the
 * skin tone picker and the custom emoji preview take the touch), two fingers and a swipe toward the previous category
 * from the screen's left edge (12dp, the back swipe's) stay as they were. Off, none of this runs.
 */
public final class XgramEmojiCategorySwipe {
  public interface Delegate {
    /** Swipes may jump now: the setting is on and the grid of emoji is at rest on screen. */
    boolean canSwipeCategories ();
    /** There is a category {@code step} (+1 the next, -1 the previous) from the current one, and a jump gets there. */
    boolean hasCategory (int step);
    /** Jumps there, as a tap on its icon. */
    void showCategory (int step);
    /** Opens Stickers after the last category (a swipe the pager did not take); false where there is no such page. */
    boolean showNextPage ();
  }

  /** The back swipe's edge, the screen's left one: a swipe toward the previous category starting there is left to it. */
  public static final float BACK_EDGE_DP = 12f;

  private static final float FLING_VELOCITY_DP = 400f, FLING_DISTANCE_DP = 24f;
  private static final float DRAG_MIN_DP = 48f, DRAG_MAX_DP = 120f;
  /** The steepest a move can go and still start a swipe: about 34 degrees from horizontal. */
  private static final float ANGLE_RATIO = .66f;

  private static final int IDLE = 0, TRACKING = 1, SWIPING = 2, PAST_LAST = 3, IGNORED = 4;
  private static final int NO_POINTER = -1;

  private final CustomRecyclerView grid;
  private final Delegate delegate;
  private final int touchSlop, pagingSlop, maxVelocity;

  private int state = IDLE;
  private int pointerId = NO_POINTER;
  private long downTime = Long.MIN_VALUE;
  private float downX, downY;
  /** The finger's sideways move since the down, at the last move seen (canScrollHorizontally on the last category). */
  private float lastDx;
  /** The touch caught the grid's fling: RecyclerView grabs it for a scroll at once, and it can still be a swipe. */
  private boolean caughtFling;
  private boolean toNextPage, multiTouch, claimed;
  private @Nullable VelocityTracker velocity;

  public XgramEmojiCategorySwipe (@NonNull CustomRecyclerView grid, @NonNull Delegate delegate) {
    this.grid = grid;
    this.delegate = delegate;
    final ViewConfiguration config = ViewConfiguration.get(grid.getContext());
    this.touchSlop = config.getScaledTouchSlop();
    this.pagingSlop = config.getScaledPagingTouchSlop();
    this.maxVelocity = config.getScaledMaximumFlingVelocity();
  }

  /** The step of a finger moving by {@code dx}: left is the next category, right the previous one (mirrored with RTL). */
  private static int stepOfMove (float dx) {
    return (dx < 0) != Lang.rtl() ? 1 : -1;
  }

  /**
   * For the grid's canScrollHorizontally: a category that way, so the pager leaves such a swipe to the grid. On the
   * last category the pager asks this on every move until it drags (its {@code direction} is the finger's way and
   * distance since the down), and one yes locks it out for the whole gesture; so the previous category is claimed only
   * once the finger really goes that way (half the touch slop, before the pager's paging slop), not for the pixel of
   * jitter that often starts a swipe to Stickers, which the pager then follows as before.
   */
  public boolean canScrollHorizontally (int direction) {
    // direction > 0 asks about more to the right, which a finger moving left brings
    if (direction == 0 || !delegate.canSwipeCategories()) {
      return false;
    }
    final int step = stepOfMove(-direction);
    if (!delegate.hasCategory(step)) {
      return false;
    }
    if (step < 0 && !delegate.hasCategory(1)) {
      if (state == SWIPING) {
        return true;
      }
      final float moved = Math.max(Math.abs(direction), stepOfMove(lastDx) < 0 ? Math.abs(lastDx) : 0f);
      return state == TRACKING && moved >= touchSlop / 2f;
    }
    return true;
  }

  /** From the grid's onInterceptTouchEvent, before its own: true once a sideways swipe is the grid's. */
  public boolean onInterceptTouchEvent (@NonNull MotionEvent e) {
    return process(e, false);
  }

  /**
   * From the grid's onTouchEvent, before its own: true while the swipe is the grid's, and for a down the grid would
   * drop while a jump runs (its touch handling is off then), so a quick next swipe is not the pager's.
   */
  public boolean onTouchEvent (@NonNull MotionEvent e) {
    return process(e, true);
  }

  /** Once, right after the swipe became the grid's: the grid then cancels its own touch handling (no scroll, no fling). */
  public boolean takeClaimed () {
    if (claimed) {
      claimed = false;
      return true;
    }
    return false;
  }

  private boolean process (MotionEvent e, boolean fromTouch) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        // The same down comes twice when the grid intercepts it or no emoji takes it (intercept, then touch)
        if (e.getDownTime() != downTime) {
          start(e);
        }
        return fromTouch && state == TRACKING && grid.isScrollDisabled();
      }
      case MotionEvent.ACTION_MOVE: {
        if (velocity != null && state != IGNORED) {
          velocity.addMovement(e);
        }
        if (state == SWIPING) {
          return true;
        }
        if (state != TRACKING && state != PAST_LAST) {
          return false;
        }
        final int index = e.findPointerIndex(pointerId);
        if (index < 0) {
          state = IGNORED;
          return false;
        }
        final float dx = e.getX(index) - downX, dy = e.getY(index) - downY;
        lastDx = dx;
        final float adx = Math.abs(dx), ady = Math.abs(dy);
        if (state == PAST_LAST) {
          if (stepOfMove(dx) != 1 || ady > adx) {
            state = IGNORED;
          } else if (adx > pagingSlop + touchSlop && adx * .5f > ady) {
            // The pager would have taken this move (its slop, its angle) and did not: it sits this gesture out
            toNextPage = true;
            return claim();
          }
          return false;
        }
        if (grid.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING && !caughtFling) {
          state = IGNORED; // the grid scrolls
          return false;
        }
        if (adx > touchSlop && adx * ANGLE_RATIO > ady) {
          final int step = stepOfMove(dx);
          if (step < 0 && downX <= Screen.dp(BACK_EDGE_DP)) {
            state = IGNORED; // the back swipe's
            return false;
          }
          if (delegate.hasCategory(step)) {
            return claim();
          }
          // Past the last category: the pager's swipe to Stickers, as before. Before the first one: nothing.
          state = step > 0 ? PAST_LAST : IGNORED;
          return false;
        }
        if (ady > touchSlop) {
          state = IGNORED;
        }
        return false;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (state == SWIPING) {
          multiTouch = true; // kept to its end, without a jump
          return true;
        }
        if (state != IDLE) {
          state = IGNORED;
        }
        return false;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (state == SWIPING) {
          if (e.getPointerId(e.getActionIndex()) == pointerId) {
            multiTouch = true;
          }
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
        if (state != SWIPING) {
          reset();
          return false;
        }
        if (velocity != null) {
          velocity.addMovement(e);
        }
        final int index = e.findPointerIndex(pointerId);
        if (!multiTouch && index >= 0) {
          final float dx = e.getX(index) - downX;
          float vx = 0f;
          if (velocity != null) {
            velocity.computeCurrentVelocity(1000, maxVelocity);
            vx = velocity.getXVelocity(pointerId);
          }
          final boolean fling = Math.abs(vx) >= Screen.dp(FLING_VELOCITY_DP) && (vx < 0) == (dx < 0) && Math.abs(dx) >= Screen.dp(FLING_DISTANCE_DP);
          if (fling || Math.abs(dx) >= commitDistance()) {
            final int step = stepOfMove(dx);
            if (toNextPage) {
              if (step > 0) {
                delegate.showNextPage();
              }
            } else if (delegate.hasCategory(step)) {
              delegate.showCategory(step);
            }
          }
        }
        reset();
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        final boolean wasSwiping = state == SWIPING;
        reset();
        return wasSwiping;
      }
    }
    return state == SWIPING;
  }

  private void start (MotionEvent e) {
    reset();
    downTime = e.getDownTime();
    pointerId = e.getPointerId(0);
    downX = e.getX();
    downY = e.getY();
    if (!delegate.canSwipeCategories()) {
      state = IGNORED;
      return;
    }
    caughtFling = grid.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING;
    state = TRACKING;
    velocity = VelocityTracker.obtain();
    velocity.addMovement(e);
  }

  private boolean claim () {
    state = SWIPING;
    claimed = true;
    final ViewParent parent = grid.getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true); // the pager and the back swipe stay out of it
    }
    return true;
  }

  /** How far a slow drag goes to jump: a quarter of the grid's width, 48 to 120dp. */
  private float commitDistance () {
    return Math.max(Screen.dp(DRAG_MIN_DP), Math.min(grid.getWidth() / 4f, Screen.dp(DRAG_MAX_DP)));
  }

  private void reset () {
    state = IDLE;
    pointerId = NO_POINTER;
    lastDx = 0f;
    caughtFling = toNextPage = multiTouch = claimed = false;
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
  }
}
