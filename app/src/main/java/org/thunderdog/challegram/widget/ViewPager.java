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
    if (xgramScroller != null) {
      xgramTrackVelocity(ev);
    }
    if (xgramPastEnd != null) {
      xgramTrackPastEnd(ev);
    }
    return super.dispatchTouchEvent(ev);
  }

  // XGram M3E: a drag on past the last page (the chat list's last folder, or Calls without folders): the floating
  // navigation toolbar's next tab, as official Telegram's tabs. Its own pages always come first (no fight between the
  // two swipes): only from the last page, leftwards, far enough or thrown. Left to right languages only.

  public interface XgramPastEndListener {
    /** A drag past the last page may switch now (the toolbar on screen with a tab after this one). */
    boolean xgramCanSwipePastEnd ();

    void xgramOnSwipePastEnd ();
  }

  private static final float XGRAM_PAST_END_DP = 96f, XGRAM_PAST_END_FLING_DP = 900f, XGRAM_PAST_END_MIN_DP = 32f;
  private @Nullable XgramPastEndListener xgramPastEnd;
  private boolean xgramPastEndTracking;
  private float xgramPastEndX, xgramPastEndY;
  private long xgramPastEndTime;

  public void xgramSetPastEndListener (@Nullable XgramPastEndListener listener) {
    this.xgramPastEnd = listener;
  }

  private boolean xgramOnLastPage () {
    androidx.viewpager.widget.PagerAdapter adapter = getAdapter();
    return adapter != null && adapter.getCount() > 0 && getCurrentItem() == adapter.getCount() - 1;
  }

  private void xgramTrackPastEnd (MotionEvent ev) {
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        xgramPastEndTracking = pagingEnabled && !org.thunderdog.challegram.core.Lang.rtl() && xgramOnLastPage() && xgramPastEnd.xgramCanSwipePastEnd();
        xgramPastEndX = ev.getX();
        xgramPastEndY = ev.getY();
        xgramPastEndTime = ev.getEventTime();
        break;
      }
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_CANCEL: {
        xgramPastEndTracking = false;
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (xgramPastEndTracking) {
          xgramPastEndTracking = false;
          final float dx = ev.getX() - xgramPastEndX, dy = ev.getY() - xgramPastEndY;
          final long dt = Math.max(1L, ev.getEventTime() - xgramPastEndTime);
          final boolean far = -dx >= org.thunderdog.challegram.tool.Screen.dp(XGRAM_PAST_END_DP);
          final boolean thrown = -dx >= org.thunderdog.challegram.tool.Screen.dp(XGRAM_PAST_END_MIN_DP) && -dx * 1000f / dt >= org.thunderdog.challegram.tool.Screen.dp(XGRAM_PAST_END_FLING_DP);
          if ((far || thrown) && Math.abs(dy) < -dx / 2f && xgramOnLastPage()) {
            final XgramPastEndListener listener = xgramPastEnd;
            post(() -> {
              if (listener == xgramPastEnd && listener.xgramCanSwipePastEnd()) {
                listener.xgramOnSwipePastEnd(); // after the pager's own end of the gesture (its edge springs back)
              }
            });
          }
        }
        break;
      }
    }
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
