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
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramPagerScroller;

import org.thunderdog.challegram.Log;

import me.vkryl.core.MathUtils;

public class ViewPager extends androidx.viewpager.widget.ViewPager {
  public ViewPager (Context context) {
    super(context);
    xgramInstallScroller(context);
  }

  public ViewPager (Context context, AttributeSet attrs) {
    super(context, attrs);
    xgramInstallScroller(context);
  }

  // XGram: Expressive design settles pages on a spring with the finger's velocity.

  private XgramPagerScroller xgramScroller;
  private VelocityTracker xgramVelocityTracker;
  private int xgramMaxFling;

  private void xgramInstallScroller (Context context) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    try {
      java.lang.reflect.Field field = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mScroller");
      field.setAccessible(true);
      XgramPagerScroller scroller = new XgramPagerScroller(context, this);
      field.set(this, scroller);
      xgramScroller = scroller;
      xgramMaxFling = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
    } catch (Throwable t) {
      Log.w(t);
      xgramScroller = null;
    }
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent ev) {
    if (xgramPastEnd != null && xgramTrackPastEnd(ev)) {
      return true; // XGram M3E: the drag went on past the last page: the toolbar's tab pager has it
    }
    if (xgramScroller != null) {
      xgramTrackVelocity(ev);
    }
    return super.dispatchTouchEvent(ev);
  }

  // XGram M3E: a drag on past the last page (the chat list's last folder, or Calls without folders) goes on to the
  // floating navigation toolbar's next tab, as seamless as the pages: past the touch slop the pager lets go (its own
  // drag is cancelled) and the toolbar's tab pager moves the chat list and the next tab's screen with the finger. The
  // pages always come first: only from the last page at rest, leftwards. Left to right languages only.

  public interface XgramPastEndListener {
    /** A drag past the last page may begin now (the toolbar on screen with a tab after this one). */
    boolean xgramCanSwipePastEnd ();

    /** The drag goes on past the last page: true once the next tab's screen moves with it. */
    boolean xgramBeginPastEnd ();

    /** The finger moved {@code dx} (px, negative: on, leftwards) since the drag began. */
    void xgramDragPastEnd (float dx);

    /** Released ({@code cancel}: taken away), {@code velocityX} in px/s. */
    void xgramEndPastEnd (float dx, float velocityX, boolean cancel);
  }

  private @Nullable XgramPastEndListener xgramPastEnd;
  /** A press that may go on past the last page; past the slop the toolbar's drag (xgramPastEndActive). */
  private boolean xgramPastEndTracking, xgramPastEndActive;
  /** In screen coordinates: the chat list moves with the drag, its own coordinates with it. */
  private float xgramPastEndX, xgramPastEndY;
  private @Nullable VelocityTracker xgramPastEndVelocity;
  /** The pager's own drag runs (its scroll state): only then is the gesture the pager's, not a child's (a list's row). */
  private boolean xgramDragging;
  private boolean xgramStateListened;

  public void xgramSetPastEndListener (@Nullable XgramPastEndListener listener) {
    this.xgramPastEnd = listener;
    if (listener != null && !xgramStateListened) {
      xgramStateListened = true;
      super.addOnPageChangeListener(new SimpleOnPageChangeListener() {
        @Override
        public void onPageScrollStateChanged (int state) {
          xgramDragging = state == SCROLL_STATE_DRAGGING;
        }
      });
    }
  }

  private void xgramAddPastEndMovement (MotionEvent ev) {
    if (xgramPastEndVelocity == null) {
      return;
    }
    final MotionEvent raw = MotionEvent.obtain(ev);
    raw.offsetLocation(ev.getRawX() - ev.getX(), ev.getRawY() - ev.getY());
    xgramPastEndVelocity.addMovement(raw);
    raw.recycle();
  }

  /** On the last page, at rest (not between two pages). */
  private boolean xgramOnLastPage () {
    androidx.viewpager.widget.PagerAdapter adapter = getAdapter();
    return adapter != null && adapter.getCount() > 0 && getCurrentItem() == adapter.getCount() - 1 && getWidth() > 0 && getScrollX() == getCurrentItem() * (getWidth() + getPageMargin());
  }

  /** True: the event is the toolbar drag's (the pager doesn't see it). */
  private boolean xgramTrackPastEnd (MotionEvent ev) {
    final XgramPastEndListener listener = xgramPastEnd;
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (xgramPastEndActive) {
          listener.xgramEndPastEnd(0f, 0f, true); // the last gesture's end never came: back to the chat list
        }
        xgramPastEndActive = false;
        xgramPastEndTracking = pagingEnabled && !org.thunderdog.challegram.core.Lang.rtl() && xgramOnLastPage() && listener.xgramCanSwipePastEnd();
        xgramPastEndX = ev.getRawX();
        xgramPastEndY = ev.getRawY();
        if (xgramPastEndVelocity != null) {
          xgramPastEndVelocity.clear();
        }
        if (xgramPastEndTracking) {
          if (xgramPastEndVelocity == null) {
            xgramPastEndVelocity = VelocityTracker.obtain();
          }
          xgramAddPastEndMovement(ev);
        }
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (xgramPastEndTracking || xgramPastEndActive) {
          xgramAddPastEndMovement(ev);
        }
        if (xgramPastEndActive) {
          listener.xgramDragPastEnd(ev.getRawX() - xgramPastEndX);
          return true;
        }
        if (!xgramPastEndTracking) {
          return false;
        }
        final float dx = ev.getRawX() - xgramPastEndX, dy = ev.getRawY() - xgramPastEndY;
        final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
          xgramPastEndTracking = false; // a list scroll
          return false;
        }
        if (dx > slop) {
          xgramPastEndTracking = false; // back to the pages before: the pager's
          return false;
        }
        if (-dx <= slop || Math.abs(dy) > -dx / 2f || !xgramDragging) {
          return false; // not yet the pager's own drag (a child's, such as a sideways list, keeps it)
        }
        xgramPastEndTracking = false;
        if (!xgramOnLastPage() || !listener.xgramBeginPastEnd()) {
          return false;
        }
        xgramPastEndActive = true;
        xgramPastEndX = ev.getRawX(); // the screens start moving where the slop ends: no jump
        if (getParent() != null) {
          // The navigation intercepts every touch while a transition runs: the rest of this gesture stays here
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        // The pager lets go: its own drag (and its edge effect) end here
        final MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        return true;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        xgramPastEndTracking = false;
        if (xgramPastEndActive) {
          xgramPastEndActive = false;
          listener.xgramEndPastEnd(ev.getRawX() - xgramPastEndX, 0f, true); // a second finger: back to the chat list
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        xgramPastEndTracking = false;
        if (!xgramPastEndActive) {
          return false;
        }
        xgramPastEndActive = false;
        float vx = 0f;
        if (xgramPastEndVelocity != null) {
          xgramAddPastEndMovement(ev);
          xgramPastEndVelocity.computeCurrentVelocity(1000);
          vx = xgramPastEndVelocity.getXVelocity();
          xgramPastEndVelocity.clear();
        }
        listener.xgramEndPastEnd(ev.getRawX() - xgramPastEndX, vx, ev.getActionMasked() == MotionEvent.ACTION_CANCEL);
        return true;
      }
    }
    return xgramPastEndActive;
  }

  private void xgramTrackVelocity (MotionEvent ev) {
    int action = ev.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      if (xgramVelocityTracker != null) {
        xgramVelocityTracker.clear();
      }
    }
    if (xgramVelocityTracker == null) {
      xgramVelocityTracker = VelocityTracker.obtain();
    }
    xgramVelocityTracker.addMovement(ev);
    if (action == MotionEvent.ACTION_UP) {
      xgramVelocityTracker.computeCurrentVelocity(1000, xgramMaxFling);
      // A finger moving right scrolls back (scrollX decreases).
      xgramScroller.setPendingVelocity(-xgramVelocityTracker.getXVelocity());
      xgramVelocityTracker.clear();
    } else if (action == MotionEvent.ACTION_CANCEL) {
      xgramVelocityTracker.clear();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (xgramVelocityTracker != null) {
      xgramVelocityTracker.recycle();
      xgramVelocityTracker = null;
    }
    if (xgramPastEndVelocity != null) {
      xgramPastEndVelocity.recycle();
      xgramPastEndVelocity = null;
    }
  }

  private boolean oneShot;
  private java.lang.reflect.Field mFirstLayoutField;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (oneShot) {
      try {
        if (mFirstLayoutField == null) {
          mFirstLayoutField = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mFirstLayout");
          mFirstLayoutField.setAccessible(true);
        }
        mFirstLayoutField.set(this, false);
      } catch (Throwable t) {
        Log.w(t);
      }
    } else {
      oneShot = true;
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    try {
      return pagingEnabled && super.onInterceptTouchEvent(ev);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean pagingEnabled = true;

  public void setPagingEnabled (boolean isEnabled) {
    this.pagingEnabled = isEnabled;
  }

  public boolean isPagingEnabled () {
    return pagingEnabled;
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    return pagingEnabled && super.onTouchEvent(ev);
  }

  // XGram: a horizontal mouse wheel or sideways trackpad scroll turns the page
  @Override
  public boolean onGenericMotionEvent (MotionEvent event) {
    if (pagingEnabled && com.fahadali.xgram.layout.XgramPointer.onPagerScroll(this, event)) {
      return true;
    }
    return super.onGenericMotionEvent(event);
  }

  @CheckResult
  public static float clampPositionOffset (float positionOffset) {
    if (Float.isNaN(positionOffset)) return 0f;
    return MathUtils.clamp(positionOffset);
  }
}
