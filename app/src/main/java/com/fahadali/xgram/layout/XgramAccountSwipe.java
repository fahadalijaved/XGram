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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramSheetDrag;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

/**
 * Swipe on the account's photo to switch accounts, as Google's apps do with their account disc: up for the next
 * account, down for the previous one (the user's order, round).
 * <ul>
 *   <li>While dragged, the photo follows the finger (with resistance, at most half its size) and the neighbour's photo
 *   peeks in from the other side, both inside the photo's shape (square inside it while they move: edge to edge).</li>
 *   <li>Past {@code commitDp} of travel, or thrown: the photo slides out, the other one slides in, a tick, and the account
 *   switches as the side menu switches it (the whole stack is rebuilt). The slid-in photo stays until the toolbar binds
 *   the new chat list, then becomes the host's own, so the old photo never flashes back. Below that it springs back.
 *   Until TdlibManager answers, no second switch starts; until the new chat list is bound, the photo takes no press
 *   (no tap on the old account; an answer that never comes gives the press back after a while).</li>
 *   <li>A tap and a long press keep their meaning (a long press that ran ends the swipe), a sideways drag and a second
 *   finger are left alone; with one account, the setting off or TalkBack exploring (it has the actions instead) nothing
 *   changes.</li>
 * </ul>
 * One per photo: the toolbar's profile button and its profile tab (XgramNavToolbar).
 */
final class XgramAccountSwipe {
  interface Host {
    /** A switch may start now (the toolbar on screen with a tab screen on top, nothing moving, the account settled). */
    boolean canSwitchAccounts ();

    /** The account whose photo the host shows at rest, or {@link TdlibAccount#NO_ID}. */
    int shownAccountId ();

    /** The slid-in photo becomes the host's own: returns the one it drew (kept for the next swipe). */
    @Nullable AvatarReceiver replaceAvatar (AvatarReceiver avatar);

    /** A swipe switches to {@code accountId} now (the host may keep the section it is on for it). */
    void onSwitchStarted (int accountId);

    /** That switch failed, or never reached the toolbar: the photo went back. */
    void onSwitchFailed (int accountId);
  }

  /** What the view does with a touch event: its own handling, a swipe that just began (end the press), the swipe's. */
  static final int TOUCH_PASS = 0, TOUCH_CLAIMED = 1, TOUCH_CONSUMED = 2;

  private static final int IDLE = 0, DRAGGING = 1, SWITCHING = 2, BACK = 3;
  /** Thrown this fast (dp/s), it commits whatever the distance. */
  private static final float FLING_DP = 500f;
  /** Mostly vertical: the vertical travel is this many times the sideways one. */
  private static final float VERTICAL_RATIO = 1.5f;
  /** The photo moves at most this part of its size while dragged. */
  private static final float MAX_SHIFT = .5f;
  /** The slide's start velocity, in photo sizes per second, at most (a hard throw would only overshoot). */
  private static final float MAX_VELOCITY = 12f;
  /** As the side menu's account rows: no second switch while the first one's screens fade. */
  private static final long COOLDOWN_MS = 720L;
  /**
   * The new account's chat list never reached the toolbar (it went meanwhile): the photo goes back to the bound one.
   * Counted from TdlibManager's answer (starting the account's TDLib may take a while before it).
   */
  private static final long BIND_TIMEOUT_MS = 5000L;
  /** TdlibManager has not answered by then (from the commit): the photo goes back and takes presses again. */
  private static final long ANSWER_TIMEOUT_MS = 2 * BIND_TIMEOUT_MS;

  private final View touchView, drawView;
  private final Host host;
  private final float commitDp;
  private final boolean enabled;
  /** Where the photos are, in photo sizes: 0 at rest, negative moved up (the next account comes in from below). */
  private final XgramExpressive.Spring pos;
  private final Path clip = new Path();
  private final RectF rect = new RectF();
  private final Runnable bindTimeout = this::onBindTimeout;

  /**
   * The neighbours' photos, asked for before a swipe can show them: the previous account above, the next below; and
   * the account each one shows.
   */
  private AvatarReceiver above, below;
  private int aboveId = TdlibAccount.NO_ID, belowId = TdlibAccount.NO_ID;
  private int prevId = TdlibAccount.NO_ID, nextId = TdlibAccount.NO_ID;

  // The gesture: one finger (its pointer id), in screen coordinates (the bar may slide under it)
  @Nullable private VelocityTracker velocity;
  private int pointerId = MotionEvent.INVALID_POINTER_ID;
  private float downX, downY;

  private int state = IDLE;
  /** tracking: a press that may turn into a swipe; swallow: the rest of this gesture is the swipe's (not the view's). */
  private boolean tracking, swallow, bound, destroyed;
  private long lastCommitTime;
  private float slopOffset, size;
  /** Where the photo moves: -1 up (the next account comes in from below), 1 down, 0 at rest. Only that one is drawn. */
  private int side;
  /** The account being switched to (SWITCHING). */
  private int targetId = TdlibAccount.NO_ID;
  /** The switch TdlibManager has not answered yet: no other one starts, even once the photo went back. */
  private int pendingSwitchId = TdlibAccount.NO_ID;
  /** TdlibManager switched to it, the toolbar has not bound its chat list yet (the photo may have gone back meanwhile). */
  private int committedId = TdlibAccount.NO_ID;
  /** Started from TalkBack's action: the new account is announced once its photo is the host's. */
  private boolean announce;
  /** The photos are drawn square inside the window while they move (they meet edge to edge); the host's one then. */
  private boolean square;
  @Nullable private AvatarReceiver squared;
  /** The window's corner, as a part of half the photo's size: read at rest (square photos read 0). */
  private float windowRadiusFactor = -1f;

  /**
   * @param touchView the view that gets the touches (haptics come from it)
   * @param drawView the view the photos are drawn in (their receivers invalidate it)
   * @param photoDp the photo's size at rest, until it is drawn
   * @param enabled false for the settings page's preview: it never switches
   */
  XgramAccountSwipe (View touchView, View drawView, float photoDp, float commitDp, boolean enabled, Host host) {
    this.touchView = touchView;
    this.drawView = drawView;
    this.host = host;
    this.commitDp = commitDp;
    this.enabled = enabled;
    this.size = Screen.dpf(photoDp);
    this.above = newReceiver(drawView);
    this.below = newReceiver(drawView);
    this.pos =new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> drawView.invalidate()).setThreshold(.002f);
    this.pos.setEndListener(this::onSettled);
  }

  /**
   * A photo the swipe squares while it moves (full screen): only its shape changes, an account without a photo keeps
   * its letters (full screen alone shows the profile header's person icon until the photos are round again).
   */
  private static AvatarReceiver newReceiver (View drawView) {
    AvatarReceiver receiver = new AvatarReceiver(drawView);
    receiver.xgramSetShapeOnlyFullScreen(true);
    return receiver;
  }

  // Lifecycle, with the view that draws the photos

  void attach () {
    above.attach();
    below.attach();
  }

  void detach () {
    above.detach();
    below.detach();
  }

  void destroy () {
    destroyed = true;
    UI.cancel(bindTimeout);
    pos.cancel();
    endGesture();
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
    setSquare(false);
    above.destroy();
    below.destroy();
    aboveId = belowId = TdlibAccount.NO_ID;
    state = IDLE;
    swallow = false;
    targetId = pendingSwitchId = committedId = TdlibAccount.NO_ID;
  }

  /** The finger moves the photo, or it slides to the new account: the toolbar stays where it is (no scroll hiding). */
  boolean isMoving () {
    return state == DRAGGING || state == SWITCHING;
  }

  /** A switch is on its way (sliding, or waiting for the toolbar or for TdlibManager's answer): no other one starts. */
  boolean isSwitchPending () {
    return state == SWITCHING || pendingSwitchId != TdlibAccount.NO_ID || isCommitted();
  }

  /**
   * A tap or long press on the photo would act on the old account now: the photo shows the new one, TdlibManager has
   * not answered yet (only while the photo would wait for it: an answer that never comes leaves the press working, the
   * accounts' menu with it), or it switched and the old chat list is still bound.
   */
  boolean blocksPress () {
    return state == SWITCHING || isCommitted() || (pendingSwitchId != TdlibAccount.NO_ID && SystemClock.uptimeMillis() - lastCommitTime < ANSWER_TIMEOUT_MS);
  }

  /** Switched, not bound yet; a switch back meanwhile (a notification) ends it, no bind comes then. */
  private boolean isCommitted () {
    return committedId != TdlibAccount.NO_ID && committedId == TdlibManager.instance().preferredAccountId();
  }

  private boolean isOn () {
    return enabled && !destroyed && XgramSettings.instance().navAccountSwipe();
  }

  private boolean isTouchExploration () {
    AccessibilityManager manager = (AccessibilityManager) touchView.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }

  // Binding: the host shows an account

  /**
   * The host is about to show {@code accountId}'s photo (its chat list bound). True: that photo slid in with a swipe
   * already and is kept (the host asks for nothing). Otherwise a swipe in progress ends and the neighbours are asked for.
   */
  boolean onBind (int accountId) {
    committedId = TdlibAccount.NO_ID; // a chat list is bound: the photo shows the account a press acts on
    if (state == SWITCHING && accountId != TdlibAccount.NO_ID && accountId == targetId) {
      bound = true;
      maybeFinish();
      return true;
    }
    if (state != IDLE) {
      reset(); // another account (a switch from elsewhere): no slide to finish
    }
    preload(accountId);
    return false;
  }

  /** The photos of the accounts before and after {@code accountId}, so a swipe never shows an empty one. */
  private void preload (int accountId) {
    if (destroyed || state != IDLE) {
      return;
    }
    boolean on = accountId != TdlibAccount.NO_ID && isOn();
    nextId = on ? neighbourOf(accountId, 1) : TdlibAccount.NO_ID;
    prevId = on ? neighbourOf(accountId, -1) : TdlibAccount.NO_ID;
    belowId = request(below, belowId, nextId);
    aboveId = request(above, aboveId, prevId);
  }

  /**
   * {@code accountId}'s photo in {@code receiver}, which showed {@code shownId}: read from the saved account info,
   * without starting that account (as the rail's account rows). Returns the account it shows now. Another account is
   * cleared first: the receiver reloads only for another photo or colour (two accounts without a photo in one colour
   * would keep the first one's letters).
   */
  private static int request (AvatarReceiver receiver, int shownId, int accountId) {
    if (accountId == TdlibAccount.NO_ID) {
      receiver.clear();
      return TdlibAccount.NO_ID;
    }
    if (accountId != shownId) {
      receiver.clear();
    }
    receiver.requestAccount(null, accountId, AvatarReceiver.Options.NONE);
    return accountId;
  }

  /** The account {@code step} places from {@code accountId} in the user's order (round); NO_ID with fewer than 2. */
  private static int neighbourOf (int accountId, int step) {
    ArrayList<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
    int count = accounts.size();
    if (count < 2) {
      return TdlibAccount.NO_ID;
    }
    for (int i = 0; i < count; i++) {
      if (accounts.get(i).id == accountId) {
        TdlibAccount account = accounts.get(((i + step) % count + count) % count);
        return account.id != accountId && !account.isUnauthorized() ? account.id : TdlibAccount.NO_ID;
      }
    }
    return TdlibAccount.NO_ID;
  }

  // Touches

  /** Every touch event of the view, before its own handling (see TOUCH_*). */
  int onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (state == DRAGGING) {
          springBack(); // the last gesture never ended here
        }
        endGesture();
        swallow = blocksPress();
        if (swallow) {
          // A switch is on its way: no tap (the old account's profile), no long press (a second switch) till it is done
          return TOUCH_CONSUMED;
        }
        if (state == IDLE && isOn() && !isTouchExploration() && host.canSwitchAccounts()) {
          preload(host.shownAccountId());
          if (nextId != TdlibAccount.NO_ID) { // one account: the press stays as it was
            startTracking(e);
          }
        }
        return TOUCH_PASS;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (swallow) {
          return TOUCH_CONSUMED;
        }
        if (state == DRAGGING) {
          abortDrag(); // a second finger: the photo goes back
          return TOUCH_CONSUMED;
        }
        stopTracking(); // two fingers: not a swipe
        return TOUCH_PASS;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (swallow) {
          return TOUCH_CONSUMED;
        }
        final boolean own = e.getPointerId(e.getActionIndex()) == pointerId;
        if (state == DRAGGING) {
          if (own) {
            abortDrag(); // the finger that moved it left, another one stays: the photo goes back
          }
          return TOUCH_CONSUMED;
        }
        if (own) {
          stopTracking();
        }
        return TOUCH_PASS;
      }
      case MotionEvent.ACTION_MOVE: {
        if (swallow) {
          return TOUCH_CONSUMED;
        }
        final int index = pointerId != MotionEvent.INVALID_POINTER_ID ? e.findPointerIndex(pointerId) : -1;
        if (state == DRAGGING) {
          if (index < 0) {
            abortDrag();
          } else {
            addMovement(e);
            follow(rawY(e, index) - downY);
          }
          return TOUCH_CONSUMED;
        }
        if (!tracking) {
          return TOUCH_PASS;
        }
        if (index < 0) {
          stopTracking();
          return TOUCH_PASS;
        }
        addMovement(e);
        final float dx = rawX(e, index) - downX, dy = rawY(e, index) - downY;
        final int slop = XgramSheetDrag.touchSlop(touchView.getContext());
        if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * VERTICAL_RATIO) {
          if (isSwitchPending() || !host.canSwitchAccounts()) {
            stopTracking();
            return TOUCH_PASS;
          }
          tracking = false;
          state = DRAGGING;
          slopOffset = dy > 0f ? slop : -slop; // the photo starts moving where the slop ends: no jump
          setSquare(true);
          follow(dy);
          return TOUCH_CLAIMED;
        } else if (Math.abs(dx) > slop) {
          stopTracking(); // sideways: not a swipe
        }
        return TOUCH_PASS;
      }
      case MotionEvent.ACTION_UP: {
        if (swallow) {
          swallow = false;
          return TOUCH_CONSUMED;
        }
        if (state == DRAGGING) {
          final int index = e.findPointerIndex(pointerId);
          if (index >= 0) {
            addMovement(e);
            release(rawY(e, index) - downY, velocityY());
          } else {
            springBack();
          }
          endGesture();
          return TOUCH_CONSUMED;
        }
        stopTracking();
        return TOUCH_PASS;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (swallow) {
          swallow = false;
          return TOUCH_CONSUMED;
        }
        if (state == DRAGGING) {
          endGesture();
          springBack();
          return TOUCH_CONSUMED;
        }
        stopTracking();
        return TOUCH_PASS;
      }
    }
    return swallow || state == DRAGGING ? TOUCH_CONSUMED : TOUCH_PASS;
  }

  /**
   * The view's long press ran (the accounts' menu): the finger that stays down no longer swipes. A swipe that began
   * already ended the press, so it is left as it is.
   */
  void cancelTracking () {
    if (state != DRAGGING) {
      stopTracking();
    }
  }

  private void startTracking (MotionEvent e) {
    tracking = true;
    pointerId = e.getPointerId(0);
    downX = e.getRawX();
    downY = e.getRawY();
    if (velocity == null) {
      velocity = VelocityTracker.obtain();
    } else {
      velocity.clear();
    }
    addMovement(e);
  }

  private void stopTracking () {
    endGesture();
  }

  private void endGesture () {
    tracking = false;
    pointerId = MotionEvent.INVALID_POINTER_ID;
    if (velocity != null) {
      velocity.clear();
    }
  }

  /** A second finger, or the moving one left while another stays: the photo goes back, the rest of the gesture is ours. */
  private void abortDrag () {
    endGesture();
    springBack();
    swallow = true;
  }

  /** In screen coordinates, as the deltas: the bar sliding (scroll hiding, a screen it follows) never moves the photo. */
  private void addMovement (MotionEvent e) {
    if (velocity == null) {
      return;
    }
    MotionEvent raw = MotionEvent.obtain(e);
    raw.offsetLocation(e.getRawX() - e.getX(), e.getRawY() - e.getY());
    velocity.addMovement(raw);
    raw.recycle();
  }

  private float velocityY () {
    if (velocity == null || pointerId == MotionEvent.INVALID_POINTER_ID) {
      return 0f;
    }
    velocity.computeCurrentVelocity(1000);
    return velocity.getYVelocity(pointerId);
  }

  // getRawX(int) and getRawY(int) came with Android 10: before, the view's offset on screen (the same for all pointers)
  private static float rawX (MotionEvent e, int index) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? e.getRawX(index) : e.getX(index) + e.getRawX() - e.getX();
  }

  private static float rawY (MotionEvent e, int index) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? e.getRawY(index) : e.getY(index) + e.getRawY() - e.getY();
  }

  /** The finger moved {@code dy} from where it went down: the photo follows it, rubber banded. */
  private void follow (float dy) {
    final float d = dy - slopOffset;
    final float max = size * MAX_SHIFT;
    final float shift = Math.signum(d) * max * Math.abs(d) / (Math.abs(d) + max);
    if (shift != 0f) {
      side = shift < 0f ? -1 : 1;
    }
    pos.setValue(shift / size);
    drawView.invalidate();
  }

  private void release (float dy, float velocity) {
    // From where the photo started moving, as it is drawn: a drag back inside the slop never switches the way it began
    final float d = dy - slopOffset;
    final boolean far;
    if (d == 0f) {
      far = false;
    } else if (Math.abs(velocity) >= Screen.dpf(FLING_DP)) {
      far = (velocity < 0f) == (d < 0f); // thrown back the other way: no switch
    } else {
      far = Math.abs(d) >= Screen.dpf(commitDp);
    }
    if (!far || !commit(d < 0f ? -1 : 1, velocity)) {
      springBack();
    }
  }

  // Switching

  /**
   * Slides the other account's photo in ({@code direction} -1: up, the next account; 1: down, the previous one) and
   * switches to it as the side menu does. False when that can't be done now.
   */
  private boolean commit (int direction, float velocity) {
    if (pendingSwitchId != TdlibAccount.NO_ID) {
      return false; // the last switch has not been answered yet
    }
    final int current = host.shownAccountId();
    final TdlibManager manager = TdlibManager.instance();
    final long now = SystemClock.uptimeMillis();
    if (current == TdlibAccount.NO_ID || current != manager.preferredAccountId() || (lastCommitTime != 0 && now - lastCommitTime < COOLDOWN_MS) || !host.canSwitchAccounts()) {
      return false;
    }
    // The list may have changed since the photos were asked for: the neighbour now (its photo again if it is another)
    final int target = neighbourOf(current, direction < 0 ? 1 : -1);
    if (target == TdlibAccount.NO_ID) {
      return false;
    }
    if (direction < 0) {
      belowId = request(below, belowId, nextId = target);
    } else {
      aboveId = request(above, aboveId, prevId = target);
    }
    lastCommitTime = now;
    state = SWITCHING;
    side = direction;
    targetId = target;
    pendingSwitchId = target;
    bound = false;
    setSquare(true); // also from TalkBack's action: the photos slide as one strip
    com.fahadali.xgram.XgramHaptics.segmentTick(touchView);
    pos.setToken(XgramExpressive.SPATIAL_DEFAULT); // barely overshoots (and draw() stops at the end): no gap
    pos.animateTo(direction, Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, velocity / size)));
    UI.cancel(bindTimeout);
    UI.post(bindTimeout, BIND_TIMEOUT_MS);
    host.onSwitchStarted(target);
    try {
      // after(false) may come on TDLib's thread: back on the UI thread
      manager.changePreferredAccountId(target, TdlibManager.SWITCH_REASON_USER_CLICK, ok -> UI.post(() -> onSwitched(target, ok)));
    } catch (Throwable t) {
      UI.post(() -> onSwitched(target, false));
    }
    return true;
  }

  private void onSwitched (int target, boolean ok) {
    if (pendingSwitchId == target) {
      pendingSwitchId = TdlibAccount.NO_ID; // answered: the photo takes touches again once it is at rest
      if (ok && !destroyed && host.shownAccountId() != target) {
        committedId = target; // until the new chat list is bound a press would still act on the old account
      }
    }
    if (destroyed || state != SWITCHING || targetId != target) {
      return;
    }
    if (ok) {
      UI.cancel(bindTimeout);
      UI.post(bindTimeout, BIND_TIMEOUT_MS); // from here only the toolbar's bind is waited for
      maybeFinish(); // the photo stays on the new account until the toolbar binds its chat list
      return;
    }
    springBack(); // logged out or not ready: the account stays
  }

  private void onBindTimeout () {
    if (state != SWITCHING || destroyed) {
      return;
    }
    if (pendingSwitchId == targetId && SystemClock.uptimeMillis() - lastCommitTime < ANSWER_TIMEOUT_MS) {
      UI.post(bindTimeout, BIND_TIMEOUT_MS); // no answer yet (the account's TDLib still starting): the photo waits
      return;
    }
    springBack();
  }

  private void maybeFinish () {
    if (state == SWITCHING && bound && !pos.isRunning()) {
      swap();
    }
  }

  /** The slid-in photo is the host's now; the one it drew is kept for the next swipe. */
  private void swap () {
    final boolean fromBelow = side != 0 ? side < 0 : pos.getValue() < 0f;
    setSquare(false); // at rest: both take their shape again, the window's (nothing changes on screen)
    AvatarReceiver incoming = fromBelow ? below : above;
    AvatarReceiver previous = host.replaceAvatar(incoming);
    if (previous == null) {
      previous = newReceiver(drawView);
    }
    previous.xgramSetShapeOnlyFullScreen(true); // the host's own until now
    previous.clear();
    if (fromBelow) {
      below = previous;
      belowId = TdlibAccount.NO_ID;
    } else {
      above = previous;
      aboveId = TdlibAccount.NO_ID;
    }
    final int shown = targetId;
    final boolean announce = this.announce;
    reset();
    preload(shown);
    if (announce) {
      announceAccount(shown);
    }
  }

  private void springBack () {
    UI.cancel(bindTimeout);
    final boolean committed = state == SWITCHING;
    final int target = targetId;
    state = BACK;
    targetId = TdlibAccount.NO_ID;
    bound = false;
    announce = false;
    // Near critically damped: a bounce past the rest would show the photo on the other side (draw() stops it there)
    pos.setToken(committed ? XgramExpressive.STD_SPATIAL_DEFAULT : XgramExpressive.STD_SPATIAL_FAST);
    pos.animateTo(0f);
    if (!pos.isRunning()) {
      onSettled(); // already at rest: animateTo has nothing to do
    }
    if (committed) {
      host.onSwitchFailed(target);
    }
  }

  private void onSettled () {
    if (state == BACK) {
      state = IDLE;
      side = 0;
      setSquare(false);
      drawView.invalidate();
    } else if (state == SWITCHING) {
      maybeFinish();
    }
  }

  private void reset () {
    UI.cancel(bindTimeout);
    if (state == DRAGGING) {
      swallow = true; // the finger is still down: the rest of its gesture is not a tap
    }
    state = IDLE;
    targetId = TdlibAccount.NO_ID;
    bound = false;
    announce = false;
    side = 0;
    endGesture();
    pos.setValue(0f);
    setSquare(false);
    drawView.invalidate();
  }

  /**
   * The photos go square while they move, inside the window's round shape (its corner read at rest): the two meet edge
   * to edge, no wedges of the background between them. The host's photo goes square when it is drawn next.
   */
  private void setSquare (boolean square) {
    if (this.square == square) {
      return;
    }
    this.square = square;
    above.forceFullScreen(square, square ? 1f : 0f);
    below.forceFullScreen(square, square ? 1f : 0f);
    if (!square && squared != null) {
      squared.forceFullScreen(false, 0f);
      squared = null;
    }
    drawView.invalidate();
  }

  /** TalkBack: the account a switch from its action went to. */
  private void announceAccount (int accountId) {
    String name = accountName(accountId);
    if (name != null) {
      touchView.announceForAccessibility(Lang.getString(R.string.XgramAccountSwipeDone, name));
    }
  }

  @Nullable
  private static String accountName (int accountId) {
    if (accountId == TdlibAccount.NO_ID) {
      return null;
    }
    try {
      TdlibAccount account = TdlibManager.instance().account(accountId);
      String name = account.getName();
      return name != null && !name.isEmpty() ? Lang.getDebugString(name, account.isDebug()) : null;
    } catch (Throwable ignored) {
      return null; // removed meanwhile
    }
  }

  // Drawing

  /**
   * Draws {@code avatar} at ({@code left}, {@code top}), {@code sizePx} square: as it is at rest; during a swipe moved
   * by it, with the neighbour's photo next to it, both clipped to its shape.
   */
  void draw (Canvas c, @Nullable AvatarReceiver avatar, int left, int top, int sizePx) {
    if (avatar == null) {
      return;
    }
    if (sizePx > 0) {
      this.size = sizePx;
    }
    avatar.xgramSetShapeOnlyFullScreen(true); // the host's photo goes square too: its letters stay
    final int right = left + sizePx, bottom = top + sizePx;
    avatar.setBounds(left, top, right, bottom);
    if (!square && sizePx > 0) {
      windowRadiusFactor = avatar.getDisplayRadius() / (sizePx / 2f); // at rest the photo has the window's shape
    } else if (square && squared != avatar) {
      if (squared != null) {
        squared.forceFullScreen(false, 0f); // the host swapped its photo meanwhile
      }
      avatar.forceFullScreen(true, 1f);
      squared = avatar;
    }
    // Only towards the side it moves, and no further than a photo: a spring past either end leaves the photo there
    // (never a sliver of the other neighbour, never a gap)
    final int side = this.side != 0 ? this.side : (pos.getValue() < 0f ? -1 : 1);
    final float value = state != IDLE ? pos.getValue() : 0f;
    final float p = side < 0 ? Math.max(-1f, Math.min(0f, value)) : Math.max(0f, Math.min(1f, value));
    final int shift = Math.round(p * sizePx);
    if (sizePx <= 0 || (shift == 0 && !square)) {
      drawPhoto(c, avatar);
      return;
    }
    final float radius = (windowRadiusFactor >= 0f ? windowRadiusFactor : 1f) * sizePx / 2f;
    rect.set(left, top, right, bottom);
    clip.reset();
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    final int save = c.save();
    c.clipPath(clip);
    if (Math.abs(shift) < sizePx) {
      avatar.setBounds(left, top + shift, right, bottom + shift);
      drawPhoto(c, avatar);
    }
    final int otherShift = shift + (side < 0 ? sizePx : -sizePx);
    if (Math.abs(otherShift) < sizePx) {
      AvatarReceiver other = side < 0 ? below : above;
      other.setBounds(left, top + otherShift, right, bottom + otherShift);
      drawPhoto(c, other);
    }
    c.restoreToCount(save);
    avatar.setBounds(left, top, right, bottom);
  }

  private static void drawPhoto (Canvas c, AvatarReceiver receiver) {
    if (receiver.needPlaceholder()) {
      receiver.drawPlaceholder(c);
    }
    receiver.draw(c);
  }

  // Accessibility: the swipe as actions (TalkBack's own gestures stay TalkBack's)

  void addAccessibilityActions (AccessibilityNodeInfo info) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !isOn()) {
      return;
    }
    // The accounts a swipe would go to, by name ("Switch to Anna")
    final int current = host.shownAccountId();
    final int nextAccount = current != TdlibAccount.NO_ID ? neighbourOf(current, 1) : TdlibAccount.NO_ID;
    if (nextAccount == TdlibAccount.NO_ID) {
      return;
    }
    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.btn_xgramAccountSwipeNext, actionLabel(nextAccount, R.string.XgramAccountSwipeNext)));
    final int previousAccount = neighbourOf(current, -1);
    if (previousAccount != TdlibAccount.NO_ID && previousAccount != nextAccount) {
      // With two accounts the previous one is the next one
      info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.btn_xgramAccountSwipePrevious, actionLabel(previousAccount, R.string.XgramAccountSwipePrevious)));
    }
  }

  /** "Switch to" the account's name; without one, to the next or previous account. */
  private static String actionLabel (int accountId, @StringRes int fallbackRes) {
    String name = accountName(accountId);
    return name != null ? Lang.getString(R.string.XgramAccountSwipeTo, name) : Lang.getString(fallbackRes);
  }

  boolean performAccessibilityAction (int action) {
    final boolean next = action == R.id.btn_xgramAccountSwipeNext;
    if (!next && action != R.id.btn_xgramAccountSwipePrevious) {
      return false;
    }
    if (state != IDLE || isSwitchPending() || !isOn()) {
      return false;
    }
    preload(host.shownAccountId());
    if (!commit(next ? -1 : 1, 0f)) {
      return false;
    }
    announce = true; // the new account's name, once its photo is the host's
    return true;
  }
}
