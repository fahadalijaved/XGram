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
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.fahadali.xgram.call.XgramCallVideo;
import com.fahadali.xgram.call.XgramCallVideoLayer;
import com.fahadali.xgram.call.XgramVideoSurface;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

/**
 * XGram: the small video of a call (your camera under the other side's video, theirs once swapped), in a corner of the
 * call screen. A rounded rectangle (16dp, elevation 3dp; Telegram X's screen: 8dp, flat) on black, its picture fading
 * in on the first frame. Drag it anywhere inside its bounds; let go and it flings to the corner its velocity points at
 * (the corner is kept for the whole process). A tap swaps the videos. The parent lays it out at (0, 0); it moves by
 * translation only, from pixel springs, so nothing is laid out per frame. With the key card open it fades and takes
 * no taps. Main thread only.
 */
public final class XgramM3CallVideoTile extends FrameLayoutFix implements XgramCallVideoLayer.Tile {
  public interface Callback {
    /** A tap: swap the videos. */
    void onTileTap ();

    /** A drag started or ended (the chrome does not hide meanwhile). */
    default void onTileDragging (boolean dragging) { }
  }

  private static final float PROJECTION_SECONDS = .15f;
  private static final float MIN_WIDTH = 64f;

  private final boolean expressive;
  private final Callback callback;
  private final float cornerRadius;
  private final int touchSlop, maxFling;
  private @Nullable XgramVideoSurface surface;
  private @Nullable String peerName;
  private boolean swapped;

  private int tileWidth, tileHeight;
  private final Rect bounds = new Rect();
  private boolean positioned;
  private int corner = XgramCallVideo.lastTileCorner;

  private final Motion posX, posY, enter, enterAlpha, press, content, pulse;
  private boolean wantShown, fits = true;
  private float dim;

  // Touch
  private boolean dragging, touchActive;
  private float downX, downY, startX, startY;
  private @Nullable VelocityTracker velocity;

  public XgramM3CallVideoTile (Context context, boolean expressive, Callback callback) {
    super(context);
    this.expressive = expressive;
    this.callback = callback;
    this.cornerRadius = Screen.dp(expressive ? XgramExpressive.CORNER_L : 8f);
    ViewConfiguration configuration = ViewConfiguration.get(context);
    this.touchSlop = configuration.getScaledTouchSlop();
    this.maxFling = configuration.getScaledMaximumFlingVelocity();

    posX = new Motion(XgramExpressive.SPATIAL_DEFAULT, 0f, .5f);
    posY = new Motion(XgramExpressive.SPATIAL_DEFAULT, 0f, .5f);
    enter = new Motion(XgramExpressive.SPATIAL_DEFAULT, 0f, .001f);
    enterAlpha = new Motion(XgramExpressive.EFFECTS_DEFAULT, 0f, .001f);
    press = new Motion(XgramExpressive.SPATIAL_FAST, 1f, .001f);
    content = new Motion(XgramExpressive.EFFECTS_DEFAULT, 0f, .001f);
    pulse = new Motion(XgramExpressive.SPATIAL_FAST, 1f, .001f);

    setBackgroundColor(0xff000000);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
        }
      });
      setClipToOutline(true);
      setElevation(expressive ? Screen.dp(XgramExpressive.ELEVATION_2) : 0f);
    }
    setLayoutParams(FrameLayoutFix.newParams(Screen.dp(96f), Screen.dp(128f), Gravity.LEFT | Gravity.TOP));
    setVisibility(View.INVISIBLE);
    setAlpha(0f);
    setClickable(true);
    setFocusable(true);
    setContentDescription(Lang.getString(R.string.XgramVideoYours));
    ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, Lang.getString(R.string.XgramVideoSwap), null);
    addMoveAction(R.string.XgramVideoMoveTopLeft, false, false);
    addMoveAction(R.string.XgramVideoMoveTopRight, true, false);
    addMoveAction(R.string.XgramVideoMoveBottomLeft, false, true);
    addMoveAction(R.string.XgramVideoMoveBottomRight, true, true);
  }

  private void addMoveAction (int label, boolean right, boolean bottom) {
    ViewCompat.addAccessibilityAction(this, Lang.getString(label), (view, arguments) -> {
      moveToCorner(cornerFor(right, bottom), true, 0f, 0f);
      return true;
    });
  }

  public void setPeerName (@Nullable String name) {
    this.peerName = name;
    updateDescription();
  }

  private void updateDescription () {
    setContentDescription(swapped ? Lang.getString(R.string.XgramVideoFrom, peerName != null ? peerName : "") : Lang.getString(R.string.XgramVideoYours));
  }

  // Corners (RTL aware: start and end follow the layout direction)

  private int cornerFor (boolean right, boolean bottom) {
    final boolean end = right != Lang.rtl();
    if (bottom) {
      return end ? XgramCallVideo.CORNER_BOTTOM_END : XgramCallVideo.CORNER_BOTTOM_START;
    }
    return end ? XgramCallVideo.CORNER_TOP_END : XgramCallVideo.CORNER_TOP_START;
  }

  private static boolean isEnd (int corner) {
    return corner == XgramCallVideo.CORNER_TOP_END || corner == XgramCallVideo.CORNER_BOTTOM_END;
  }

  private static boolean isBottom (int corner) {
    return corner == XgramCallVideo.CORNER_BOTTOM_START || corner == XgramCallVideo.CORNER_BOTTOM_END;
  }

  private boolean isRight (int corner) {
    return isEnd(corner) != Lang.rtl();
  }

  private float cornerX (int corner) {
    return isRight(corner) ? bounds.right - tileWidth : bounds.left;
  }

  private float cornerY (int corner) {
    return isBottom(corner) ? bounds.bottom - tileHeight : bounds.top;
  }

  // Geometry (the screen's onLayout)

  /**
   * The tile's size, from the parent's onLayout (which then measures and lays the tile out itself, so the layout params
   * change without another layout pass); a size change (rotation, fold, pane resize) snaps it to its corner.
   */
  public void setTileSize (int width, int height) {
    if (tileWidth == width && tileHeight == height) {
      return;
    }
    tileWidth = width;
    tileHeight = height;
    ViewGroup.LayoutParams lp = getLayoutParams();
    if (lp != null) {
      lp.width = Math.max(1, width);
      lp.height = Math.max(1, height);
    }
    final boolean nowFits = width >= Screen.dp(MIN_WIDTH) && height > 0;
    if (fits != nowFits) {
      fits = nowFits;
      updateShown(false);
    }
    positioned = false;
    snapToCorner();
  }

  /** Where the tile may go; a change springs it to the same corner (the first one snaps). */
  public void setTileBounds (int left, int top, int right, int bottom, boolean animated) {
    if (bounds.left == left && bounds.top == top && bounds.right == right && bounds.bottom == bottom) {
      if (!positioned) {
        snapToCorner();
      }
      return;
    }
    bounds.set(left, top, right, bottom);
    if (!positioned || !animated || dragging || getVisibility() != View.VISIBLE) {
      snapToCorner();
    } else {
      posX.animateTo(cornerX(corner), 0f);
      posY.animateTo(cornerY(corner), 0f);
    }
  }

  private void snapToCorner () {
    if (tileWidth <= 0 || bounds.isEmpty() || dragging) {
      return;
    }
    posX.snap(cornerX(corner));
    posY.snap(cornerY(corner));
    positioned = true;
    applyTransform();
  }

  private void moveToCorner (int newCorner, boolean animated, float velocityX, float velocityY) {
    final boolean changed = corner != newCorner;
    corner = newCorner;
    XgramCallVideo.lastTileCorner = newCorner;
    if (animated && positioned) {
      posX.animateTo(cornerX(newCorner), velocityX);
      posY.animateTo(cornerY(newCorner), velocityY);
    } else {
      snapToCorner();
    }
    if (changed && expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
  }

  // XgramCallVideoLayer.Tile

  @Override
  public void setVideoSurface (@Nullable XgramVideoSurface surface) {
    if (this.surface == surface) {
      return;
    }
    if (this.surface != null) {
      removeView(this.surface.view());
    }
    this.surface = surface;
    if (surface != null) {
      View view = surface.view();
      view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      view.setAlpha(content.value());
      addView(view);
    }
  }

  @Override
  public void setTileShown (boolean shown, boolean animated) {
    if (wantShown != shown) {
      wantShown = shown;
      updateShown(animated);
    }
  }

  private void updateShown (boolean animated) {
    final boolean visible = wantShown && fits;
    if (visible) {
      setVisibility(View.VISIBLE);
      if (!positioned) {
        snapToCorner();
      }
    }
    animated = animated && getWidth() > 0 && ViewCompat.isAttachedToWindow(this);
    if (animated) {
      enter.animateTo(visible ? 1f : 0f, 0f);
      enterAlpha.animateTo(visible ? 1f : 0f, 0f);
    } else {
      enter.snap(visible ? 1f : 0f);
      enterAlpha.snap(visible ? 1f : 0f);
      settleVisibility();
    }
    applyTransform();
  }

  private void settleVisibility () {
    if (!(wantShown && fits) && enterAlpha.value() <= 0f && !enterAlpha.isRunning()) {
      setVisibility(View.INVISIBLE);
    }
  }

  @Override
  public void onTileFeedChanged (boolean swapped, boolean pulse) {
    this.swapped = swapped;
    updateDescription();
    content.snap(0f); // the new feed fades in on its first frame
    applyContent();
    if (pulse && getVisibility() == View.VISIBLE) {
      this.pulse.snap(.92f);
      this.pulse.animateTo(1f, 0f);
    }
  }

  @Override
  public void onTileFirstFrame () {
    content.animateTo(1f, 0f, swapped ? XgramExpressive.EFFECTS_FAST : XgramExpressive.EFFECTS_DEFAULT);
  }

  /** The key card's progress: the tile fades under it and takes no taps. */
  public void setDim (float dim) {
    dim = MathUtils.clamp(dim);
    if (this.dim != dim) {
      this.dim = dim;
      applyTransform();
    }
  }

  public boolean isDragging () {
    return dragging;
  }

  // Drawing state

  private void applyContent () {
    if (surface != null) {
      surface.view().setAlpha(MathUtils.clamp(content.value()));
    }
  }

  private void applyTransform () {
    final float e = enter.value();
    setTranslationX(posX.value());
    setTranslationY(posY.value());
    final float scale = (.8f + .2f * e) * press.value() * pulse.value();
    setScaleX(scale);
    setScaleY(scale);
    // The pivot moves from the screen corner (entering) to the center (resting)
    final float w = tileWidth, h = tileHeight;
    final float cornerPx = isRight(corner) ? w : 0f, cornerPy = isBottom(corner) ? h : 0f;
    final float k = MathUtils.clamp(1f - e);
    setPivotX(w / 2f + (cornerPx - w / 2f) * k);
    setPivotY(h / 2f + (cornerPy - h / 2f) * k);
    setAlpha(MathUtils.clamp(enterAlpha.value()) * (1f - dim));
  }

  private boolean isTouchable () {
    return wantShown && fits && dim == 0f && enterAlpha.target() == 1f;
  }

  // Touch

  @Override
  public boolean performClick () {
    super.performClick();
    callback.onTileTap();
    return true;
  }

  private void trackVelocity (MotionEvent e) {
    if (velocity == null) {
      velocity = VelocityTracker.obtain();
    }
    // In the parent's coordinates: the tile moves with the finger
    final float dx = getTranslationX(), dy = getTranslationY();
    e.offsetLocation(dx, dy);
    velocity.addMovement(e);
    e.offsetLocation(-dx, -dy);
  }

  private void recycleVelocity () {
    if (velocity != null) {
      velocity.recycle();
      velocity = null;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!isTouchable()) {
          return false;
        }
        touchActive = true;
        dragging = false;
        downX = e.getRawX();
        downY = e.getRawY();
        startX = posX.value();
        startY = posY.value();
        recycleVelocity();
        trackVelocity(e);
        press.animateTo(.96f, 0f);
        setPressed(true);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!touchActive) {
          return false;
        }
        trackVelocity(e);
        final float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        if (!dragging && (float) Math.hypot(dx, dy) > touchSlop) {
          dragging = true;
          setPressed(false);
          press.animateTo(1.04f, 0f);
          if (expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(Screen.dp(XgramExpressive.ELEVATION_4));
          }
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          callback.onTileDragging(true);
        }
        if (dragging && !bounds.isEmpty()) {
          posX.snap(MathUtils.clamp(startX + dx, bounds.left, Math.max(bounds.left, bounds.right - tileWidth)));
          posY.snap(MathUtils.clamp(startY + dy, bounds.top, Math.max(bounds.top, bounds.bottom - tileHeight)));
          applyTransform();
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (!touchActive) {
          return false;
        }
        touchActive = false;
        final boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
        if (dragging) {
          float vx = 0f, vy = 0f;
          if (velocity != null && up) {
            trackVelocity(e);
            velocity.computeCurrentVelocity(1000, maxFling);
            vx = velocity.getXVelocity();
            vy = velocity.getYVelocity();
          }
          dragging = false;
          final float cx = posX.value() + tileWidth / 2f + vx * PROJECTION_SECONDS;
          final float cy = posY.value() + tileHeight / 2f + vy * PROJECTION_SECONDS;
          final int target = cornerFor(cx > bounds.exactCenterX(), cy > bounds.exactCenterY());
          moveToCorner(target, true, vx, vy);
          if (expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
          }
          callback.onTileDragging(false);
        } else if (up && isTouchable()) {
          performClick();
        }
        setPressed(false);
        press.animateTo(1f, 0f);
        recycleVelocity();
        return true;
      }
    }
    return touchActive;
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    recycleVelocity();
    if (dragging) {
      dragging = false;
      callback.onTileDragging(false);
    }
    touchActive = false;
  }

  // Motion

  private void onMotion (Motion motion) {
    if (motion == content) {
      applyContent();
    } else {
      applyTransform();
    }
  }

  private void onMotionEnd (Motion motion) {
    if (motion == enterAlpha) {
      settleVisibility();
    }
  }

  /**
   * One animated value: a spring with the Expressive token (with a start velocity for the fling; snapping with Reduce
   * motion), or Telegram X's 180ms decelerate on its screen.
   */
  private final class Motion implements FactorAnimator.Target {
    private final int token;
    private final @Nullable FloatValueHolder holder;
    private final @Nullable SpringAnimation spring;
    private final @Nullable FactorAnimator animator;
    private float from, to, value;

    Motion (int token, float initial, float threshold) {
      this.token = token;
      this.value = this.from = this.to = initial;
      if (expressive) {
        holder = new FloatValueHolder(initial);
        spring = new SpringAnimation(holder);
        spring.setSpring(new SpringForce(initial).setStiffness(XgramExpressive.stiffness(token)).setDampingRatio(XgramExpressive.damping(token)));
        spring.setMinimumVisibleChange(threshold);
        spring.addUpdateListener((animation, v, speed) -> {
          value = v;
          onMotion(this);
        });
        spring.addEndListener((animation, canceled, v, speed) -> {
          if (!canceled) {
            value = v;
            onMotionEnd(this);
          }
        });
        animator = null;
      } else {
        holder = null;
        spring = null;
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, 1f);
      }
    }

    float value () {
      return value;
    }

    float target () {
      return to;
    }

    boolean isRunning () {
      return spring != null ? spring.isRunning() : animator != null && animator.isAnimating();
    }

    void snap (float v) {
      to = from = value = v;
      if (spring != null) {
        if (spring.isRunning()) {
          spring.cancel();
        }
        holder.setValue(v);
      } else if (animator != null) {
        animator.forceFactor(1f);
      }
    }

    void animateTo (float target, float startVelocity) {
      animateTo(target, startVelocity, token);
    }

    void animateTo (float target, float startVelocity, int springToken) {
      if (to == target && (isRunning() || value == target)) {
        return;
      }
      to = target;
      if (spring != null) {
        if (XgramMotion.reduce()) {
          spring.cancel();
          holder.setValue(target);
          value = target;
          onMotion(this);
          onMotionEnd(this);
          return;
        }
        spring.getSpring().setStiffness(XgramExpressive.stiffness(springToken)).setDampingRatio(XgramExpressive.damping(springToken));
        if (!spring.isRunning()) {
          holder.setValue(value);
        }
        if (startVelocity != 0f) {
          spring.setStartVelocity(startVelocity);
        }
        spring.animateToFinalPosition(target);
      } else if (animator != null) {
        from = value;
        animator.forceFactor(0f);
        animator.animateTo(1f);
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      value = from + (to - from) * factor;
      onMotion(this);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (finalFactor == 1f) {
        value = to;
        onMotionEnd(this);
      }
    }
  }
}
