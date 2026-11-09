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
 * tabs. The screen follows the finger with resistance (less towards a side with no tab); past {@link #COMMIT_DP}, or
 * thrown, the toolbar switches tabs with its usual fade-through, else the screen springs back.
 * <p>
 * Fed every touch by NavigationGestureController before Telegram X's own gestures (a tab screen has no swipe back: it
 * fades). Lists keep their own scrolling: a vertical move first, or a horizontal list that claims the drag, ends it.
 * The chat list's own pager keeps its swipes; past its last page the pager asks the toolbar itself.
 */
public final class XgramTabSwipe {
  /** Dragged this far (dp), it switches. */
  private static final float COMMIT_DP = 72f;
  /** Thrown this fast (dp/s) the way it was dragged, it switches whatever the distance. */
  private static final float FLING_DP = 900f;
  /** The screen moves at most this part of its width (towards a side with no tab: a third of it). */
  private static final float MAX_SHIFT = .18f;

  private final BaseActivity context;
  @Nullable private View view;
  @Nullable private VelocityTracker velocity;
  private boolean listening, swiping;
  private float startX, startY;
  @Nullable private ViewController<?> screen;

  public XgramTabSwipe (BaseActivity context) {
    this.context = context;
  }

  /** True while the gesture is the tab swipe's: the navigation's own gestures leave it alone then. */
  public boolean onTouchEvent (NavigationController navigation, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
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
          follow(e.getX() - startX);
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
        this.view = view;
        view.animate().cancel();
        startX = e.getX(); // the screen starts moving where the slop ends: no jump
        follow(0f);
        return true;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (swiping) {
          end(true); // a second finger: the screen goes back, the rest of this gesture is nobody's
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
        final float dx = e.getX() - startX;
        float vx = 0f;
        if (velocity != null) {
          velocity.computeCurrentVelocity(1000);
          vx = velocity.getXVelocity();
        }
        final int step = dx < 0f ? 1 : -1;
        final boolean far = Math.abs(dx) >= Screen.dpf(COMMIT_DP);
        final boolean thrown = dx != 0f && Math.abs(vx) >= Screen.dpf(FLING_DP) && (vx < 0f) == (dx < 0f);
        final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
        if ((far || thrown) && toolbar != null) {
          toolbar.swipeToTab(step); // its fade-through takes the screen away as it settles back
        }
        end(true);
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        final boolean was = swiping;
        end(true);
        return was;
      }
    }
    return swiping;
  }

  /** The finger moved {@code dx} since the screen started moving: the screen follows it, rubber banded. */
  private void follow (float dx) {
    final View view = this.view;
    if (view == null) {
      return;
    }
    final XgramNavToolbar toolbar = XgramNavToolbar.of(context);
    final boolean open = dx != 0f && toolbar != null && toolbar.hasSwipeTarget(dx < 0f ? 1 : -1);
    final float max = Math.max(1f, view.getWidth() * (open ? MAX_SHIFT : MAX_SHIFT / 3f));
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
    listening = swiping = false;
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
  }
}
