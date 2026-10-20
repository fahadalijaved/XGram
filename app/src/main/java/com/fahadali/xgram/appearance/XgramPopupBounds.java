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

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.RootFrameLayout;

/**
 * XGram: where a popup (a menu, a card, a tooltip) may be drawn in the window that holds it, and where its anchor is.
 * <p>
 * The safe area keeps a popup's visible card {@link #MARGIN} dp away from the status bar or the display cutout at the
 * top, the navigation bar or the keyboard (whichever is taller) at the bottom, and the side bars and cutouts. Anchor
 * bounds leave out the views' own running transform (a menu that is still scaling open, a pressed button), which put
 * popups placed with {@code getLocationInWindow} off by the scale.
 * <p>
 * Main thread only.
 */
public final class XgramPopupBounds {
  private XgramPopupBounds () { }

  /** The gap (dp) between a popup's visible card and the system bars, the keyboard, the window's sides and its anchor. */
  public static final float MARGIN = 8f;

  /**
   * {@link #placeVertically} and {@link #opensAbove}: above the anchor when the popup fits there, else below when it
   * fits there, else on the side with more room. For anchors anywhere on the screen (rows in a list).
   */
  public static final int PLACE_FIT = 0;

  /**
   * {@link #placeVertically} and {@link #opensAbove}: above the anchor unless not even two menu rows fit there and
   * below has more room; the rest scrolls. For anchors at the bottom (Send, the composer): the in-app emoji panel under
   * them is not room for a menu.
   */
  public static final int PLACE_PREFER_ABOVE = 1;

  private static final int[] location = new int[2];

  /** {@link #safeRect(View, int, Rect)} in a new rect. */
  @NonNull
  public static Rect safeRect (@NonNull View popup, int extraBottomPx) {
    return safeRect(popup, extraBottomPx, new Rect());
  }

  /**
   * The area of {@code popup} (a window-wide popup, e.g. a PopupLayout, or any view) where a popup's visible card may
   * be drawn, in {@code popup}'s own coordinates: {@link #MARGIN} dp inside the status bar or the top cutout, the side
   * bars and cutouts, and the navigation bar or the keyboard (whichever is taller), plus {@code extraBottomPx} more at
   * the bottom (e.g. the composer, or the in-app emoji panel, which are not insets).
   * <p>
   * The insets come from {@code popup} when it is a {@link RootFrameLayout} that got them (always current while it is
   * measured and laid out), else from the window's root insets, else the status bar height alone. Works before the
   * popup's first layout: it then takes the size of what holds it (a popup covers its window).
   *
   * @return {@code out}
   */
  @NonNull
  public static Rect safeRect (@NonNull View popup, int extraBottomPx, @NonNull Rect out) {
    final View root = popup.getRootView();
    int width = popup.getWidth(), height = popup.getHeight();
    if (width <= 0 || height <= 0) {
      final ViewParent parent = popup.getParent();
      if (parent instanceof View && ((View) parent).getWidth() > 0 && ((View) parent).getHeight() > 0) {
        width = ((View) parent).getWidth();
        height = ((View) parent).getHeight();
      } else if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
        width = root.getWidth();
        height = root.getHeight();
      } else {
        width = Screen.currentWidth();
        height = Screen.currentHeight();
      }
    }
    popup.getLocationInWindow(location);
    final int x = location[0], y = location[1];
    int windowWidth = root != null ? root.getWidth() : 0, windowHeight = root != null ? root.getHeight() : 0;
    if (windowWidth <= 0 || windowHeight <= 0) {
      windowWidth = x + width;
      windowHeight = y + height;
    }

    // The insets, in window coordinates
    int left = 0, top = 0, right = 0, bottom = 0;
    boolean known = false;
    if (popup instanceof RootFrameLayout && ((RootFrameLayout) popup).hasWindowInsets()) {
      final Rect bars = ((RootFrameLayout) popup).getSystemInsetsWithoutIme(); // system bars, display cutout
      final Rect all = ((RootFrameLayout) popup).getSystemInsets(); // the same and the keyboard
      left = Math.max(bars.left, all.left);
      top = Math.max(bars.top, all.top);
      right = Math.max(bars.right, all.right);
      bottom = Math.max(bars.bottom, all.bottom);
      known = true;
    }
    // The window's root insets: when the popup has none, and for a cutout only reported as one (before API 30 the
    // popup's insets may leave it out; from API 30 on they hold it)
    final WindowInsetsCompat rootInsets = !known || Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? ViewCompat.getRootWindowInsets(popup) : null;
    if (rootInsets != null) {
      if (!known) {
        final Insets bars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        final Insets ime = rootInsets.getInsets(WindowInsetsCompat.Type.ime());
        left = bars.left;
        top = bars.top;
        right = bars.right;
        bottom = Math.max(bars.bottom, ime.bottom);
        known = true;
      }
      // A cutout that only reports itself as a cutout (API 28-29)
      final DisplayCutoutCompat cutout = rootInsets.getDisplayCutout();
      if (cutout != null) {
        left = Math.max(left, cutout.getSafeInsetLeft());
        top = Math.max(top, cutout.getSafeInsetTop());
        right = Math.max(right, cutout.getSafeInsetRight());
        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
      }
    }
    if (!known) {
      top = HeaderView.getTopOffset();
    }

    // Into the popup's coordinates: only the part of each inset the popup overlaps
    final int margin = Screen.dp(MARGIN);
    out.left = Math.max(0, left - x) + margin;
    out.top = Math.max(0, top - y) + margin;
    out.right = width - Math.max(0, x + width - (windowWidth - right)) - margin;
    out.bottom = height - Math.max(0, y + height - (windowHeight - bottom)) - margin - Math.max(0, extraBottomPx);
    if (out.right < out.left) {
      out.right = out.left;
    }
    if (out.bottom < out.top) {
      out.bottom = out.top;
    }
    return out;
  }

  /**
   * {@code view}'s bounds in its window without its own scale or rotation (a menu that is still scaling open, a pressed
   * button): its parent's place, plus its left and top, less the parent's scroll, plus its translation; its laid out
   * width and height. Transforms of its ancestors count.
   *
   * @return {@code out}
   */
  @NonNull
  public static Rect boundsInWindow (@NonNull View view, @NonNull Rect out) {
    return bounds(view, out, false);
  }

  /** {@link #boundsInWindow} on the screen: for a view and a popup in different windows. */
  @NonNull
  public static Rect boundsOnScreen (@NonNull View view, @NonNull Rect out) {
    return bounds(view, out, true);
  }

  private static Rect bounds (View view, Rect out, boolean onScreen) {
    int x, y;
    final ViewParent parent = view.getParent();
    if (parent instanceof View) {
      final View p = (View) parent;
      if (onScreen) {
        p.getLocationOnScreen(location);
      } else {
        p.getLocationInWindow(location);
      }
      x = location[0] - p.getScrollX() + view.getLeft() + Math.round(view.getTranslationX());
      y = location[1] - p.getScrollY() + view.getTop() + Math.round(view.getTranslationY());
    } else {
      // A window's root view: where the window is
      if (onScreen) {
        view.getLocationOnScreen(location);
      } else {
        view.getLocationInWindow(location);
      }
      x = location[0];
      y = location[1];
    }
    out.set(x, y, x + view.getWidth(), y + view.getHeight());
    return out;
  }

  /**
   * {@code anchor}'s bounds ({@code boundsInAnchor}, in the anchor's own coordinates, or the whole anchor when null)
   * in {@code target}'s coordinates, through the screen, so it works across windows. The anchor's own scale is left
   * out (see {@link #boundsInWindow}); {@code target} is taken as untransformed (a popup).
   *
   * @return false, leaving {@code out} as it was, when the anchor is not attached or not laid out
   */
  public static boolean anchorRectIn (@NonNull View anchor, @Nullable Rect boundsInAnchor, @NonNull View target, @NonNull Rect out) {
    if (anchor.getWindowToken() == null || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
      return false;
    }
    bounds(anchor, out, true);
    if (boundsInAnchor != null) {
      final int x = out.left, y = out.top;
      out.set(x + boundsInAnchor.left, y + boundsInAnchor.top, x + boundsInAnchor.right, y + boundsInAnchor.bottom);
    }
    target.getLocationOnScreen(location);
    out.offset(-location[0], -location[1]);
    return true;
  }

  /**
   * Whether a popup {@code contentHeight} tall opens above its anchor, given the room above and below it (heights the
   * popup may take on each side) and the {@code policy}: {@link #PLACE_FIT} or {@link #PLACE_PREFER_ABOVE} (two rows:
   * {@link #twoMenuRowsHeight()}).
   */
  public static boolean opensAbove (int roomAbove, int roomBelow, int contentHeight, int policy) {
    if (policy == PLACE_PREFER_ABOVE) {
      return roomAbove >= Math.min(contentHeight, twoMenuRowsHeight()) || roomAbove >= roomBelow;
    }
    return contentHeight <= roomAbove || !(contentHeight <= roomBelow || roomBelow > roomAbove);
  }

  /** Two 48dp menu rows and the menu's 8dp padding above and below them. */
  public static int twoMenuRowsHeight () {
    return Screen.dp(48f * 2 + 8f * 2);
  }

  /** {@link #placeVertically(int, int, int, int, Rect, int, int[])} with {@link #PLACE_FIT}. */
  public static int placeVertically (int anchorTop, int anchorBottom, int height, int gap, @NonNull Rect safe, @Nullable int[] outMaxHeight) {
    return placeVertically(anchorTop, anchorBottom, height, gap, safe, PLACE_FIT, outMaxHeight);
  }

  /**
   * The top of a popup {@code height} tall placed {@code gap} above the anchor span [{@code anchorTop},
   * {@code anchorBottom}] or {@code gap} below it (by {@code policy}, see {@link #opensAbove}), kept inside
   * {@code safe} (the top edge wins when it is too tall). All values in one coordinate space, e.g. the popup's with
   * {@link #safeRect}.
   *
   * @param outMaxHeight when not null: [0] gets the room on the chosen side (cap the popup's height to it and let the
   *                     rest scroll, then place it again), [1] (if there) 1 when it opens above, 0 when below; put the
   *                     pivot on the side facing the anchor
   */
  public static int placeVertically (int anchorTop, int anchorBottom, int height, int gap, @NonNull Rect safe, int policy, @Nullable int[] outMaxHeight) {
    final int roomAbove = Math.max(0, anchorTop - gap - safe.top);
    final int roomBelow = Math.max(0, safe.bottom - (anchorBottom + gap));
    final boolean above = opensAbove(roomAbove, roomBelow, height, policy);
    int top = above ? anchorTop - gap - height : anchorBottom + gap;
    top = Math.max(safe.top, Math.min(top, safe.bottom - height));
    if (outMaxHeight != null) {
      if (outMaxHeight.length > 0) {
        outMaxHeight[0] = above ? roomAbove : roomBelow;
      }
      if (outMaxHeight.length > 1) {
        outMaxHeight[1] = above ? 1 : 0;
      }
    }
    return top;
  }
}
