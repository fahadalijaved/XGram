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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E: the one drag behaviour of every bottom sheet in Expressive design, as Compose's ModalBottomSheet (an
 * AnchoredDraggable sheet with a hidden, a start and an expanded state). Two kinds of sheet use it:
 * <ul>
 * <li>the popup sheets (PopupLayout: option, confirm, info, settings and picker sheets, the message menu with its
 * reaction and viewer pages, share, sticker set and emoji pack sheets, the embed preview, the folder icon sheet). The
 * popup follows the drag with its scrim; a sheet that grows by scrolling its list says so through {@link Sheet};</li>
 * <li>the attach sheet (MediaLayout and the popups built on it), whose height follows the drag.</li>
 * </ul>
 * Rules (shared): a drag starts on the handle band and the title always, on a list only once it is at its top (the
 * list scrolls first, and the sheet takes over when it reaches the top during the same drag); upwards the sheet grows
 * to full height where it can be taller, and only then does its list scroll. On release {@link #target} picks the
 * state: a fling of {@link #FLING_DP} or more goes to the next state in its direction, a slower release goes by where
 * the sheet would be {@link #PROJECTION} later (closed below half its start height). The sheet then settles on the
 * spatial default spring carrying the finger's velocity, or closes on the standard spatial fast one; Reduce motion
 * snaps. Main thread only; nothing allocates per frame.
 */
public final class XgramSheetDrag {
  private XgramSheetDrag () { }

  /** A release this fast (dp/s) goes to the next state in its direction (Compose's sheet velocity threshold). */
  public static final float FLING_DP = 125f;
  /** Where a slow release lands: the sheet's position this many seconds later (MDC BottomSheetBehavior HIDE_FRICTION). */
  public static final float PROJECTION = .1f;
  /** A slow release closes the sheet once it is this fraction of its start height below it (MDC HIDE_THRESHOLD). */
  public static final float HIDE_FRACTION = .5f;
  /** A slow release moving up expands the sheet past this fraction of the way to full height (down: 1 - this). */
  public static final float EXPAND_FRACTION = .35f;
  /** How far a sheet that cannot be taller follows an upward drag (a rubber band, dp). */
  public static final float RUBBER_DP = 16f;
  /** The fastest release velocity taken into account (ViewConfiguration's default maximum fling, dp/s). */
  private static final float MAX_VELOCITY_DP = 8000f;

  /** Sheet states for {@link #target}. */
  public static final int HIDDEN = 0, START = 1, EXPANDED = 2;

  /**
   * The state a released sheet goes to. Heights are the visible sheet heights (px): {@code height} now,
   * {@code fromHeight} where the drag started, {@code startHeight} at rest and {@code expandedHeight} at full height
   * (equal to {@code startHeight} when the sheet cannot be taller). {@code velocity} is the finger's (px/s, positive
   * downwards). {@code canHide} false (a sheet with unsaved changes, a cancelled gesture) keeps it open. A slow
   * release takes its direction from how far the drag went (Compose's AnchoredDraggable), never from the sign of a
   * near-zero velocity.
   */
  public static int target (float height, float fromHeight, float startHeight, float expandedHeight, float velocity, boolean canHide) {
    final float fling = Screen.dpf(FLING_DP);
    final boolean canExpand = expandedHeight - startHeight > 1f;
    if (velocity >= fling) {
      // a fling down: from above the start height to the start, from the start height down away
      if (canExpand && height > startHeight + 1f) {
        return START;
      }
      return canHide ? HIDDEN : START;
    }
    if (velocity <= -fling) {
      // a fling up: from below the start height back to it, from the start height up to full height
      if (height < startHeight - 1f) {
        return START;
      }
      return canExpand ? EXPANDED : START;
    }
    final float projected = height - velocity * PROJECTION;
    if (projected < startHeight) {
      return canHide && startHeight > 0f && (startHeight - projected) / startHeight >= HIDE_FRACTION ? HIDDEN : START;
    }
    if (canExpand) {
      final float f = (projected - startHeight) / (expandedHeight - startHeight);
      return f >= (height > fromHeight ? EXPAND_FRACTION : 1f - EXPAND_FRACTION) ? EXPANDED : START;
    }
    return START;
  }

  /** The rubber band distance (px) for an upward pull of {@code pull} px on a sheet that cannot be taller. */
  public static float rubber (float pull) {
    if (pull <= 0f) {
      return 0f;
    }
    final float max = Screen.dpf(RUBBER_DP);
    return max * pull / (pull + max);
  }

  /** Whether {@code list} is at its top (nothing to scroll back first), true without a list. */
  public static boolean atTop (@Nullable View list) {
    return list == null || !list.canScrollVertically(-1);
  }

  /** The touch slop (px) of {@code context}. */
  public static int touchSlop (@NonNull Context context) {
    return ViewConfiguration.get(context).getScaledTouchSlop();
  }

  /**
   * Whether ({@code x}, {@code y}) in {@code ancestor}'s coordinates is inside {@code view}, a visible descendant of
   * it (its translations and its parents' scroll included, e.g. a ViewPager page).
   */
  public static boolean isUnder (@Nullable View view, @NonNull View ancestor, float x, float y) {
    if (view == null || !view.isShown() || view.getAlpha() <= 0f) {
      return false;
    }
    float left = 0f, top = 0f;
    View v = view;
    while (v != ancestor) {
      left += v.getLeft() + v.getTranslationX();
      top += v.getTop() + v.getTranslationY();
      final ViewParent parent = v.getParent();
      if (!(parent instanceof View)) {
        return false;
      }
      final View parentView = (View) parent;
      left -= parentView.getScrollX();
      top -= parentView.getScrollY();
      v = parentView;
    }
    return x >= left && y >= top && x < left + view.getWidth() && y < top + view.getHeight();
  }

  /**
   * A bottom sheet in a PopupLayout whose drag needs more than the popup's own bounds: its bound controller or bound
   * view implements it. Coordinates are those of the popup's bound view (the sheet root).
   */
  public interface Sheet {
    /** Whether a touch at ({@code x}, {@code y}) is on the sheet; a drag never starts on the scrim above it. */
    boolean xgramSheetContains (float x, float y);

    /** The list the sheet grows with (it scrolls the sheet open and closed), or null for a sheet of a fixed height. */
    @Nullable RecyclerView xgramSheetList ();

    /** Whether ({@code x}, {@code y}) is on the list's content (below the title), where the list takes the drag first. */
    default boolean xgramSheetOnList (float x, float y) {
      return false;
    }

    /**
     * Whether an upward drag starting at ({@code x}, {@code y}) off the list grows the sheet (the handle band, a title
     * with nothing that drags upwards itself): it scrolls the list as the list would.
     */
    default boolean xgramSheetGrabsUp (float x, float y) {
      return false;
    }

    /** The popup took a drag on the sheet (its handle band, its title, or its list at the top): where it starts from. */
    default void xgramSheetDragStarted () {
      // override
    }

    /**
     * The end of a drag that moved only the list (the handle band or the title scrolled it): flings it with the
     * finger's {@code velocity} (px/s, positive downwards) or lets the sheet settle.
     */
    default void xgramSheetRelease (@NonNull RecyclerView list, float velocity) {
      list.fling(0, Math.round(-velocity));
    }
  }

  /**
   * The gesture of one sheet host: the down point and the finger's velocity. Feed it every event of the gesture in
   * one coordinate space that does not move with the sheet. One VelocityTracker per host, reused.
   */
  public static final class Tracker {
    private @Nullable VelocityTracker velocity;
    private float downX, downY;
    private boolean active;
    private int slop = -1;

    /** Starts a gesture at {@code e} (ACTION_DOWN). */
    public void down (@NonNull MotionEvent e) {
      if (velocity == null) {
        velocity = VelocityTracker.obtain();
      } else {
        velocity.clear();
      }
      downX = e.getX();
      downY = e.getY();
      active = true;
      velocity.addMovement(e);
    }

    /** Adds a later event of the gesture. */
    public void add (@NonNull MotionEvent e) {
      if (active && velocity != null) {
        velocity.addMovement(e);
      }
    }

    public boolean isActive () {
      return active;
    }

    public float dx (@NonNull MotionEvent e) {
      return e.getX() - downX;
    }

    public float dy (@NonNull MotionEvent e) {
      return e.getY() - downY;
    }

    /** The touch slop (px), read once. */
    public int slop (@NonNull Context context) {
      if (slop < 0) {
        slop = touchSlop(context);
      }
      return slop;
    }

    /** The finger's vertical velocity now (px/s, positive downwards), 0 without a gesture. */
    public float velocityY () {
      if (!active || velocity == null) {
        return 0f;
      }
      velocity.computeCurrentVelocity(1000);
      final float max = Screen.dpf(MAX_VELOCITY_DP);
      return Math.max(-max, Math.min(max, velocity.getYVelocity()));
    }

    /** Ends the gesture (the tracker is kept for the next one). */
    public void end () {
      active = false;
    }

    /** Ends the gesture and frees the VelocityTracker. */
    public void recycle () {
      active = false;
      if (velocity != null) {
        velocity.recycle();
        velocity = null;
      }
    }
  }
}
