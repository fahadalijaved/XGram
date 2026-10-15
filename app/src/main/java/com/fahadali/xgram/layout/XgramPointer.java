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

import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.viewpager.widget.PagerAdapter;

import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

/**
 * Mouse and trackpad support (DeX, a Bluetooth mouse on a foldable or tablet):
 * <ul>
 *   <li>right-click (secondary button, two-finger tap) opens a message's menu or a chat's long-press menu</li>
 *   <li>hover highlights chat rows, list rows and messages, with the hand pointer on rows that can be clicked</li>
 *   <li>a horizontal or tilt wheel, or a sideways trackpad scroll, turns the page in pagers (folders, tabs)</li>
 * </ul>
 */
public final class XgramPointer {
  private XgramPointer () { }

  /** The secondary (right) button is down: a right-click from a mouse, or a two-finger tap on a trackpad. */
  public static boolean isSecondaryButton (MotionEvent e) {
    return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
  }

  /** Hover state after {@code e}: entering or moving over a view hovers it, leaving ends it. */
  public static boolean hoverState (MotionEvent e, boolean current) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_HOVER_ENTER:
      case MotionEvent.ACTION_HOVER_MOVE:
        return true;
      case MotionEvent.ACTION_HOVER_EXIT:
        return false;
    }
    return current;
  }

  /**
   * Hover for custom views that handle clicks themselves (not "clickable" to Android, so it never hovers them):
   * the hovered state lights up their ripple background, and clickable ones get the hand pointer.
   */
  public static void onHover (View view, MotionEvent e, boolean interactive) {
    boolean hovered = interactive && view.isEnabled() && hoverState(e, view.isHovered());
    if (view.isHovered() != hovered) {
      view.setHovered(hovered);
    }
    if (hovered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Api24.setHandPointer(view);
    }
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private static final class Api24 {
    static void setHandPointer (View view) {
      if (view.getPointerIcon() == null) {
        view.setPointerIcon(PointerIcon.getSystemIcon(view.getContext(), PointerIcon.TYPE_HAND));
      }
    }
  }

  /** A light version of the selection colour across the message row, under the bubble. */
  public static void drawMessageHover (MessageView view, Canvas c, TGMessage msg) {
    if (msg.messagesController().inSelectMode()) {
      return;
    }
    int top = msg.findTopEdge();
    int bottom = view.getMeasuredHeight();
    if (bottom > top) {
      c.drawRect(0, top, view.getMeasuredWidth(), bottom, Paints.fillingPaint(msg.getSelectionColor(.4f)));
    }
  }

  // Sideways scroll in pagers

  private static float sideScroll;
  private static long sideScrollTime, sideScrollLockedUntil;

  /** Horizontal wheel (or tilt wheel, or sideways trackpad scroll) over a pager turns one page per gesture. */
  public static boolean onPagerScroll (androidx.viewpager.widget.ViewPager pager, MotionEvent e) {
    if (e.getActionMasked() != MotionEvent.ACTION_SCROLL || (e.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0) {
      return false;
    }
    float h = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
    if (h == 0f) {
      return false;
    }
    PagerAdapter adapter = pager.getAdapter();
    if (adapter == null || adapter.getCount() < 2) {
      return false;
    }
    long now = SystemClock.uptimeMillis();
    if (now < sideScrollLockedUntil) {
      sideScrollTime = now;
      return true;
    }
    if (now - sideScrollTime > 300 || Math.signum(sideScroll) != Math.signum(h)) {
      sideScroll = 0f;
    }
    sideScrollTime = now;
    sideScroll += h;
    if (Math.abs(sideScroll) < 1f) {
      return true;
    }
    int step = sideScroll > 0f ? 1 : -1; // positive: towards the right
    sideScroll = 0f;
    sideScrollLockedUntil = now + 400;
    if (pager instanceof RtlViewPager && Lang.rtl()) {
      step = -step;
    }
    int target = pager.getCurrentItem() + step;
    if (target >= 0 && target < adapter.getCount()) {
      pager.setCurrentItem(target, true);
    }
    return true;
  }
}
