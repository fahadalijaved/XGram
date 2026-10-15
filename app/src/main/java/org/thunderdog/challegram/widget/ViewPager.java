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
    return super.dispatchTouchEvent(ev);
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
