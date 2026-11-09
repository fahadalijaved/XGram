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

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;

/**
 * Sideways on a tab screen of the floating navigation toolbar (Contacts, Settings, Activity, the profile, the call
 * list): to the left the next tab, to the right the previous one (Chats back to the chat list), as official Telegram's
 * tabs, as seamless as the chat list's Chats and Calls pages: the two screens side by side under the finger
 * (XgramNavToolbar#beginTabDrag), settling on either one when released. Towards a side with no tab (or one that can't
 * be shown now) the screen only follows with resistance and springs back.
 * <p>
 * Fed every touch by NavigationGestureController before Telegram X's own gestures (a tab screen has no swipe back: it
 * fades). Lists keep their own scrolling: a vertical move first, or a horizontal list that claims the drag, ends it.
 * The chat list's own pager keeps its swipes; past its last page the pager asks the toolbar itself.
 */
public final class XgramTabSwipe {
  /** With no tab that way, the screen moves at most this part of its width. */
  private static final float MAX_SHIFT = .06f;

  private final BaseActivity context;
  @Nullable private View view;
  @Nullable private VelocityTracker velocity;
  private boolean listening, swiping;
  /** The drag moves the two screens (the toolbar's tab pager); else the screen alone, rubber banded. */
  private boolean paging;
  private float startX, startY;
  @Nullable private ViewController<?> screen;

  public XgramTabSwipe (BaseActivity context) {
    this.context = context;
  }

  /** True while the gesture is the tab swipe's: the navigation's own gestures leave it alone then. */
  public boolean onTouchEvent (NavigationController navigation, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        endPaging(0f, true); // the last gesture's end never came here: its drag goes back
        end(false);
        final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
        final ViewController<?> top = navigation.isAnimating() ? null : navigation.getCurrentStackItem();
        final org.thunderdog.challegram.navigation.DrawerController drawer = context.getDrawer();
        listening = top != null && (drawer == null || !drawer.isVisible()) && top.xgramIsBarTab() && !top.inTransformMode() && !top.isIntercepted() && toolbar != null && toolbar.canSwipeTabs() && !toolbar.isOnBar(e.getRawX(), e.getRawY());
        if (listening) {
          screen = top;
          startX = e.getX();
          startY = e.getY();
          velocity = VelocityTracker.obtain();
          velocity.addMovement(e);
        }
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (velocity != null) {
          velocity.addMovement(e);
        }
        if (swiping) {
          if (paging) {
            final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
            if (toolbar != null) {
              toolbar.dragTab(e.getX() - startX);
            }
          } else {
            follow(e.getX() - startX);
          }
          return true;
        }
        if (!listening) {
          return false;
        }
        final float dx = e.getX() - startX, dy = e.getY() - startY;
        if (Math.abs(dy) >= Screen.getTouchSlopY() && Math.abs(dy) >= Math.abs(dx)) {
          listening = false; // the list scrolls
          return false;
        }
        if (Math.abs(dx) < Screen.getTouchSlopBig() || Math.abs(dy) > Math.abs(dx) / 2f) {
          return false;
        }
        final ViewController<?> screen = this.screen;
        final View view = screen != null && !screen.isDestroyed() ? screen.getValue() : null;
        if (view == null || navigation.isAnimating() || navigation.getCurrentStackItem() != screen) {
          listening = false;
          return false;
        }
        listening = false;
        swiping = true;
        startX = e.getX(); // the screens start moving where the slop ends: no jump
        final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
        paging = toolbar != null && toolbar.beginTabDrag(dx < 0f ? 1 : -1);
        if (!paging) {
          this.view = view; // no tab that way: the screen alone, rubber banded
          view.animate().cancel();
          follow(0f);
        }
        return true;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (swiping) {
          endPaging(e.getX() - startX, true);
          end(true); // a second finger: the screen goes back, the rest of this gesture is nobody's
          listening = false;
          return true;
        }
        listening = false;
        return false;
      }
      case MotionEvent.ACTION_UP: {
        if (!swiping) {
          end(false);
          return false;
        }
        float vx = 0f;
        if (velocity != null) {
          velocity.computeCurrentVelocity(1000);
          vx = velocity.getXVelocity();
        }
        if (paging) {
          final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
          if (toolbar != null) {
            toolbar.endTabDrag(e.getX() - startX, vx, false); // on to the tab, or back
          }
          paging = false;
        }
        end(true);
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        final boolean was = swiping;
        endPaging(e.getX() - startX, true);
        end(true);
        return was;
      }
    }
    return swiping;
  }

  /** The gesture was taken away (NavigationGestureController#onCancel): a drag goes back. */
  public void cancel () {
    endPaging(0f, true);
    end(true);
  }

  /** The pager's drag ends where it is ({@code cancel}: back to the screen it started on). */
  private void endPaging (float dx, boolean cancel) {
    if (paging) {
      paging = false;
      final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
      if (toolbar != null) {
        toolbar.endTabDrag(dx, 0f, cancel);
      }
    }
  }

  /** The finger moved {@code dx} since the screen started moving (no tab that way): it follows, rubber banded. */
  private void follow (float dx) {
    final View view = this.view;
    if (view == null) {
      return;
    }
    final float max = Math.max(1f, view.getWidth() * MAX_SHIFT);
    view.setTranslationX(Math.signum(dx) * max * Math.abs(dx) / (Math.abs(dx) + max));
  }

  /** The gesture is over: the screen settles back (the fade-through of a switch takes it away meanwhile). */
  private void end (boolean settle) {
    final View view = this.view;
    if (settle && view != null && view.getTranslationX() != 0f) {
      view.animate().translationX(0f).setDuration(180L).setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR).start();
    }
    this.view = null;
    this.screen = null;
    listening = swiping = paging = false;
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
  }
}
