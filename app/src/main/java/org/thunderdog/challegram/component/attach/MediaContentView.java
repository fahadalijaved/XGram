/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class MediaContentView extends FrameLayoutFix implements GestureDetector.OnGestureListener, NestedScrollingParent3 {
  private static final boolean TRACE_INTERCEPT = false;
  private static final boolean TRACE_TOUCHES = false;

  private MediaBottomBaseController<?> base;

  private final float touchSlop;
  private final GestureDetector flingDetector;

  public MediaContentView (Context context) {
    super(context);
    this.flingDetector = new GestureDetector(context, this);
    this.touchSlop = Screen.getTouchSlop();
  }

  public void setBoundController (MediaBottomBaseController<?> controller) {
    this.base = controller;
  }

  @Override
  protected void dispatchDraw (android.graphics.Canvas c) {
    if (base != null) {
      base.xgramDrawSheetTop(c, getMeasuredWidth()); // XGram: Material 3 Expressive sheet top (corners and drag handle)
    }
    super.dispatchDraw(c);
  }

  private int lastMeasuredWidth;
  private int lastMeasuredHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (lastMeasuredWidth != width || lastMeasuredHeight != height) {
      lastMeasuredWidth = width;
      lastMeasuredHeight = height;
      base.onViewportChanged(width, height);
    }
  }

  private float touchStartY;
  private boolean shouldIntercept;
  private boolean intercepting;

  private float interceptStartY;

  private boolean isAnimating;

  private int currentScrollY;

  private void prepareIntercept (MotionEvent e) {
    currentScrollY = base.getRecyclerScrollY();

    scrollStartY = 0;
    scrolling = false;

    intercepting = false;
    isAnimating = base.isAnimating();
    touchStartY = e.getY();
    shouldIntercept = !isAnimating && base.canMoveRecycler() && base.isInsideRecyclerView(e.getX(), e.getY());
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        prepareIntercept(e);
        if (xgram) {
          xgramBandDown = false;
        }
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        return isAnimating || super.onInterceptTouchEvent(e);
      }
      case MotionEvent.ACTION_MOVE: {
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        if (intercepting || isAnimating) {
          return true;
        } else if (shouldIntercept) {
          float y = e.getY();
          float yDiff = y - touchStartY;
          if (Math.abs(yDiff) >= touchSlop && currentScrollY == 0) {
            //scrolling = currentScrollY != 0 && yDiff <= currentScrollY; //(currentScrollY == 0 || yDiff > currentScrollY)
            //scrollStartY = y + currentScrollY;

            intercepting = true;
            interceptStartY = y;
            base.onRecyclerMovementStarted();
            ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, true);
            return true;
          }
        }
        break;
      }
      default: {
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        break;
      }
    }
    return super.onInterceptTouchEvent(e);
  }

  private boolean scrolling;
  private float scrollStartY;

  private String getState () {
    StringBuilder b = new StringBuilder();
    if (shouldIntercept) {
      b.append("shouldIntercept ");
    }
    if (intercepting) {
      b.append("intercepting ");
    }
    if (scrolling) {
      b.append("scrolling ");
    }
    if (isAnimating) {
      b.append("animating ");
    }
    return b.toString();
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (TRACE_TOUCHES) {
      Log.v("touch: %s %s", ViewUtils.getActionName(e), getState());
    }
    if (isAnimating) {
      return true;
    }

    if (xgram) {
      return xgramOnTouchEvent(e); // XGram M3E
    }

    if (!intercepting) {
      return super.onTouchEvent(e);
    }

    flingDetector.onTouchEvent(e);

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        // Nothing to do?
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (scrolling) {
          if (e.getY() <= scrollStartY) {
            base.dispatchRecyclerTouchEvent(e);
            return true;
          } else {
            base.forceScrollRecyclerToTop();
            scrolling = false;
          }
        }
        if (base.moveRecyclerView(e.getY() - interceptStartY)) {
          if (!scrolling) {
            scrolling = true;
            scrollStartY = e.getY();
          }
        }
        break;
      }

      case MotionEvent.ACTION_UP: {
        intercepting = false;
        base.dispatchRecyclerTouchEvent(e);
        base.onRecyclerMovementFinished();
        ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, false);
        return true;
      }
    }

    if (scrolling) {
      base.dispatchRecyclerTouchEvent(e);
    }

    return true;
  }

  // XGram M3E (Expressive design): the attach sheet's drag through XgramSheetDrag, the one sheet drag behaviour of the
  // app. Besides the list at its top (as in Telegram X) it starts on the handle band the sheet top draws above the list:
  // nothing covers the band, so its touch fell through to the popup's scrim, which closed the sheet when the finger
  // lifted, whichever way it had gone (a swipe up on the handle closed the sheet instead of expanding it). The
  // navigation bar drags the sheet too (MediaLayout hands those drags over through xgramStartMove, xgramMove and
  // xgramEndMove). A drag that starts on the list while it is scrolled (full height) reaches the sheet when the list
  // gets to its top (nested scrolling, touch only): the sheet then shrinks with the finger, grows back before the list
  // scrolls again, and is released as any other. The release takes the finger's velocity from all the events of the
  // gesture (the GestureDetector fling never saw its down event); a cancelled gesture settles without closing the
  // sheet. Expressive design off keeps Telegram X's handling.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private final com.fahadali.xgram.appearance.XgramSheetDrag.Tracker xgramTracker = new com.fahadali.xgram.appearance.XgramSheetDrag.Tracker();
  private boolean xgramBandDown;
  private int xgramDispatchAction = -1;
  private boolean xgramNested, xgramNestedMoving;
  private int xgramNestedOffset;
  private final int[] xgramNestedConsumed = new int[2];

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    if (!xgram) {
      return super.dispatchTouchEvent(e);
    }
    // XGram M3E: every event of the gesture, whoever takes it (the list, a nested scroll, this view)
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      xgramTracker.down(e);
    } else {
      xgramTracker.add(e);
    }
    xgramDispatchAction = action;
    final boolean result = super.dispatchTouchEvent(e);
    xgramDispatchAction = -1;
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      xgramTracker.end();
    }
    return result;
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    xgramTracker.recycle(); // XGram M3E: a page off the sheet (a tab switch, the sheet closed) holds no VelocityTracker
  }

  private boolean xgramOnTouchEvent (MotionEvent e) {
    final int action = e.getActionMasked();
    if (!intercepting) {
      if (action == MotionEvent.ACTION_DOWN) {
        // a touch nothing in the page took: on the handle band it drags the sheet (and never closes it)
        xgramBandDown = base.canMoveRecycler() && base.xgramIsOnHandleBand(e.getY());
        return xgramBandDown || super.onTouchEvent(e);
      }
      if (!xgramBandDown) {
        return super.onTouchEvent(e);
      }
      switch (action) {
        case MotionEvent.ACTION_MOVE: {
          if (Math.abs(e.getY() - touchStartY) >= touchSlop) {
            xgramStartMove(e.getY());
          }
          break;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          xgramBandDown = false;
          break;
        }
      }
      return true;
    }
    switch (action) {
      case MotionEvent.ACTION_MOVE: {
        xgramMove(e);
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        base.dispatchRecyclerTouchEvent(e); // the list ends its scroll (or flings) when the sheet was already full height
        xgramEndMove(xgramTracker.velocityY(), action == MotionEvent.ACTION_UP);
        break;
      }
    }
    return true;
  }

  /**
   * XGram M3E: starts moving the sheet from {@code y} (this view's coordinates) for a drag that did not start on the
   * list: the handle band, the navigation bar. False while the sheet cannot move (a tab switch, a search header).
   */
  public boolean xgramStartMove (float y) {
    if (base.isAnimating() || !base.canMoveRecycler()) {
      return false;
    }
    scrolling = false;
    scrollStartY = 0;
    intercepting = true;
    interceptStartY = y;
    base.onRecyclerMovementStarted();
    ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, true);
    return true;
  }

  /**
   * XGram M3E: the sheet follows the finger at {@code e} (this view's coordinates): its height first, then, at full
   * height, the list (Telegram X's handover, unchanged).
   */
  public void xgramMove (MotionEvent e) {
    if (!intercepting) {
      return;
    }
    if (scrolling) {
      if (e.getY() <= scrollStartY) {
        base.dispatchRecyclerTouchEvent(e);
        return;
      } else {
        base.forceScrollRecyclerToTop();
        scrolling = false;
      }
    }
    if (base.moveRecyclerView(e.getY() - interceptStartY)) {
      if (!scrolling) {
        scrolling = true;
        scrollStartY = e.getY();
      }
    }
    if (scrolling) {
      base.dispatchRecyclerTouchEvent(e);
    }
  }

  /** XGram M3E: the release of a sheet drag with the finger's {@code velocity} (px/s, positive downwards). */
  public void xgramEndMove (float velocity, boolean commit) {
    if (!intercepting) {
      return;
    }
    intercepting = false;
    xgramBandDown = false;
    base.xgramReleaseDrag(velocity, commit);
    ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, false);
  }

  // XGram M3E: nested scrolling of the page's list (androidx NestedScrollingParent3, touches only)

  @Override
  public boolean onStartNestedScroll (@NonNull View child, @NonNull View target, int axes, int type) {
    xgramNested = xgram && type == ViewCompat.TYPE_TOUCH && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 && !intercepting &&
      base.xgramIsList(target) && !base.isAnimating() && base.canMoveRecycler();
    xgramNestedMoving = false;
    xgramNestedOffset = 0;
    return xgramNested;
  }

  @Override
  public void onNestedScrollAccepted (@NonNull View child, @NonNull View target, int axes, int type) {
    // the sheet moves only once the list reaches its top
  }

  @Override
  public void onNestedPreScroll (@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
    if (type == ViewCompat.TYPE_TOUCH && xgramNestedMoving && dy > 0 && xgramNestedOffset > 0) {
      // the finger goes back up: the sheet grows back before the list scrolls
      final int by = Math.min(xgramNestedOffset, dy);
      xgramNestedOffset -= by;
      base.moveRecyclerView(xgramNestedOffset);
      consumed[1] += by;
    }
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
    if (type != ViewCompat.TYPE_TOUCH || !xgramNested || dyUnconsumed >= 0) {
      return;
    }
    // the list is at its top and the finger goes on down: the sheet shrinks with it
    if (!xgramNestedMoving) {
      if (base.isAnimating() || !base.canMoveRecycler()) {
        return;
      }
      xgramNestedMoving = true;
      xgramNestedOffset = 0;
      base.onRecyclerMovementStarted();
      ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, true);
    }
    xgramNestedOffset -= dyUnconsumed;
    base.moveRecyclerView(xgramNestedOffset);
    consumed[1] += dyUnconsumed;
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
    xgramNestedConsumed[0] = xgramNestedConsumed[1] = 0;
    onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, xgramNestedConsumed);
  }

  @Override
  public boolean onNestedPreFling (@NonNull View target, float velocityX, float velocityY) {
    // the sheet moved: its release decides (onStopNestedScroll), the list does not fling. Not ViewGroup's: this view
    // has no nested scrolling parent of its own (and ViewGroup has none of these before Android 5)
    return xgramNestedMoving && xgramNestedOffset > 0;
  }

  @Override
  public boolean onNestedFling (@NonNull View target, float velocityX, float velocityY, boolean consumed) {
    return false;
  }

  @Override
  public void onStopNestedScroll (@NonNull View target, int type) {
    if (type != ViewCompat.TYPE_TOUCH || !xgramNested) {
      return;
    }
    xgramNested = false;
    if (xgramNestedMoving) {
      xgramNestedMoving = false;
      base.xgramReleaseDrag(xgramTracker.velocityY(), xgramDispatchAction == MotionEvent.ACTION_UP);
      ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, false);
    }
  }

  // fling

  @Override
  public boolean onDown (MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return false;
  }

  @Override
  public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    return false;
  }

  @Override
  public void onLongPress (MotionEvent e) {

  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return Math.abs(velocityY) > Screen.dp(250, 1f) && base.handleFling(velocityY < 0);
  }
}
