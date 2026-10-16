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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.EdgeEffect;

import androidx.activity.BackEventCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.thunderdog.challegram.navigation.SystemBackEventListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Material 3 Expressive motion: spring math (Compose spring() with the Expressive and Standard motion tokens),
 * spring interpolators for ValueAnimator / FactorAnimator / OverScroller, a spring value that starts with a velocity,
 * stretch overscroll, spring scrolling, scroll-linked lift / collapse values and the spring item animator core.
 * Callers check {@link XgramExpressive#isEnabled()} first; with it off Telegram X's own motion runs.
 */
public final class XgramMotion {
  private XgramMotion () { }

  /** Compose ScrollableState.animateScrollBy default spring(). */
  public static final float SCROLL_K = 1500f, SCROLL_Z = 1f;
  /** Compose exitUntilCollapsed snap (StiffnessMediumLow, no bounce). */
  public static final float SNAP_K = 400f, SNAP_Z = 1f;
  /** Sheet snap back. */
  public static final float SHEET_SNAP_K = 700f, SHEET_SNAP_Z = 1f;

  /** Settle threshold of interpolators: .5% of the distance. */
  public static final float SETTLE_EPS = .005f;
  private static final float MAX_SETTLE_SECONDS = 1.2f;

  public static boolean enabled () {
    return XgramExpressive.isEnabled();
  }

  public static boolean reduce () {
    return Settings.instance().needReduceMotion();
  }

  /** The token to use: with Reduce motion every expressive spatial token becomes the standard one of the same speed. */
  public static int token (int token) {
    if (token >= XgramExpressive.SPATIAL_FAST && token <= XgramExpressive.SPATIAL_SLOW && reduce()) {
      return XgramExpressive.STD_SPATIAL_FAST + token;
    }
    return token;
  }

  public static float stiffness (int token) {
    return XgramExpressive.stiffness(token(token));
  }

  public static float damping (int token) {
    return XgramExpressive.damping(token(token));
  }

  public static float clamp01 (float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }

  public static float lerp (float from, float to, float f) {
    return from + (to - from) * f;
  }

  // Spring math (unit mass, from 0 to 1, v0 in progress per second)

  public static float springValue (float k, float z, float v0, float t) {
    if (t <= 0f) {
      return 0f;
    }
    double w = Math.sqrt(k);
    if (z >= .9999f) {
      double b = w - v0;
      return (float) (1.0 - Math.exp(-w * t) * (1.0 + b * t));
    }
    double wd = w * Math.sqrt(1.0 - (double) z * z);
    double c = (z * w - v0) / wd;
    return (float) (1.0 - Math.exp(-z * w * t) * (Math.cos(wd * t) + c * Math.sin(wd * t)));
  }

  /** Velocity in progress per second at {@code t}. */
  public static float springVelocity (float k, float z, float v0, float t) {
    if (t <= 0f) {
      return v0;
    }
    double w = Math.sqrt(k);
    if (z >= .9999f) {
      double b = w - v0;
      return (float) (Math.exp(-w * t) * (w * (1.0 + b * t) - b));
    }
    double wd = w * Math.sqrt(1.0 - (double) z * z);
    double c = (z * w - v0) / wd;
    double e = Math.exp(-z * w * t);
    return (float) (e * ((z * w - c * wd) * Math.cos(wd * t) + (z * w * c + wd) * Math.sin(wd * t)));
  }

  /** Time until the spring stays within {@code eps} of the target (capped at 1.2 s). */
  public static float settleSeconds (float k, float z, float v0, float eps) {
    eps = Math.max(eps, 1e-5f);
    double w = Math.sqrt(k);
    double t;
    if (z >= .9999f) {
      double b = Math.abs(w - v0);
      t = 0.0;
      while (t < MAX_SETTLE_SECONDS && Math.exp(-w * t) * (1.0 + b * t) >= eps) {
        t += .001;
      }
    } else {
      double wd = w * Math.sqrt(1.0 - (double) z * z);
      double c = (z * w - v0) / wd;
      t = Math.log(Math.sqrt(1.0 + c * c) / eps) / (z * w);
    }
    return (float) Math.max(0.0, Math.min(MAX_SETTLE_SECONDS, t));
  }

  private static float durationScale () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return ValueAnimator.getDurationScale();
    }
    return 1f;
  }

  /** Milliseconds until the spring settles (rounded up), 0 when animations are off. */
  public static long duration (float k, float z, float v0, float eps) {
    if (durationScale() == 0f) {
      return 0;
    }
    return Math.max(1, (long) Math.ceil(settleSeconds(k, z, v0, eps) * 1000f));
  }

  public static long duration (int token) {
    token = token(token);
    return duration(XgramExpressive.stiffness(token), XgramExpressive.damping(token), 0f, SETTLE_EPS);
  }

  /** {@link #duration(int)} when Expressive design is on, {@code tgxFallback} otherwise. */
  public static long duration (int token, long tgxFallback) {
    return XgramExpressive.isEnabled() ? duration(token) : tgxFallback;
  }

  /** f (0..1 of {@code durationMs}) to the spring's position; 1 from the end on. Overshooting springs return values above 1. */
  public static Interpolator interpolator (float k, float z, float v0, long durationMs) {
    final float seconds = Math.max(1, durationMs) / 1000f;
    return f -> f >= 1f ? 1f : springValue(k, z, v0, f * seconds);
  }

  /** Pair with {@link #duration(int)}. */
  public static Interpolator interpolator (int token) {
    token = token(token);
    float k = XgramExpressive.stiffness(token), z = XgramExpressive.damping(token);
    return interpolator(k, z, 0f, duration(k, z, 0f, SETTLE_EPS));
  }

  /** {@link #interpolator(int)} when Expressive design is on, {@code tgxFallback} otherwise. */
  public static Interpolator interp (int token, Interpolator tgxFallback) {
    return XgramExpressive.isEnabled() ? interpolator(token) : tgxFallback;
  }

  /** {@link #interpolator(int)} clamped to [0, 1], for alpha and colors fed by an overshooting spring. */
  public static Interpolator interpClamped (int token, Interpolator tgxFallback) {
    if (!XgramExpressive.isEnabled()) {
      return tgxFallback;
    }
    final Interpolator spring = interpolator(token);
    return f -> clamp01(spring.getInterpolation(f));
  }

  /** Progress velocity (per second, towards the target) of a finger moving {@code pxPerSecond} with {@code remaining} (0..1) of {@code sizePx} to go. */
  public static float progressVelocity (float pxPerSecond, float sizePx, float remaining) {
    if (remaining <= .001f || sizePx <= 0f) {
      return 0f;
    }
    return Math.max(-25f, Math.min(25f, (pxPerSecond / sizePx) / remaining));
  }

  /** Puts a 0..1 ValueAnimator on spring {@code token} starting with {@code v0} (see {@link #progressVelocity}). */
  public static void applySpring (ValueAnimator animator, int token, float v0, long maxDurationMs) {
    token = token(token);
    float k = XgramExpressive.stiffness(token), z = XgramExpressive.damping(token);
    long duration = Math.min(maxDurationMs, duration(k, z, v0, SETTLE_EPS));
    animator.setDuration(duration);
    animator.setInterpolator(interpolator(k, z, v0, duration));
  }

  // Predictive back of sheets and side sheets (MDC MaterialBottomContainerBackHelper / MaterialSideContainerBackHelper)

  /** AOSP Interpolators.BACK_GESTURE. */
  public static final Interpolator BACK_GESTURE = new android.view.animation.PathInterpolator(.1f, .1f, 0f, 1f);

  /** Scales a sheet while the system back gesture is in progress and springs it back (SPATIAL_DEFAULT) after. */
  public static final class BackScale {
    public static final int BOTTOM = 0, SIDE_LEFT = 1, SIDE_RIGHT = 2;

    private final View view;
    private final int type;
    private boolean fromLeftEdge;
    private float progress;
    private @Nullable Value spring;

    public BackScale (@NonNull View view, int type) {
      this.view = view;
      this.type = type;
    }

    public void start (boolean fromLeftEdge) {
      if (spring != null) {
        spring.cancel();
      }
      this.fromLeftEdge = fromLeftEdge;
    }

    /** Raw BackEvent progress. */
    public void progress (float rawProgress) {
      if (spring != null) {
        spring.cancel();
      }
      set(BACK_GESTURE.getInterpolation(clamp01(rawProgress)));
    }

    /** Back to full size on a spring (cancelled, or committed while the sheet hides). */
    public void release () {
      if (progress == 0f) {
        return;
      }
      if (spring == null) {
        spring = new Value(XgramExpressive.SPATIAL_DEFAULT, progress, (v, value, velocity) -> set(value));
        spring.setThreshold(.002f);
        spring.setEndListener(() -> set(0f));
      } else {
        spring.setValue(progress);
      }
      spring.animateTo(0f);
    }

    public void reset () {
      if (spring != null) {
        spring.cancel();
      }
      set(0f);
    }

    public boolean isActive () {
      return progress != 0f;
    }

    private void set (float p) {
      this.progress = p;
      float w = view.getWidth(), h = view.getHeight();
      if (p == 0f || w <= 0f || h <= 0f) {
        view.setScaleX(1f);
        view.setScaleY(1f);
        if (p == 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          view.resetPivot();
        }
        return;
      }
      float scaleX, scaleY;
      if (type == BOTTOM) {
        view.setPivotX(w / 2f);
        view.setPivotY(h);
        scaleX = 1f - p * Screen.dp(48f) / w;
        scaleY = 1f - p * Screen.dp(24f) / h;
      } else {
        boolean leftGravity = type == SIDE_LEFT;
        view.setPivotX(leftGravity ? 0f : w);
        view.setPivotY(h / 2f);
        boolean grow = fromLeftEdge == leftGravity;
        scaleX = grow ? 1f + p * Screen.dp(12f) / w : 1f - p * Screen.dp(24f) / w;
        scaleY = 1f - p * Screen.dp(48f) / h;
      }
      view.setScaleX(scaleX);
      view.setScaleY(scaleY);
    }
  }

  /**
   * Predictive back of a bottom sheet: shrinks it while the gesture is in progress. Returns false from
   * onSystemBackPressed, so the activity then dismisses the sheet as before while it springs back to full size.
   */
  public static SystemBackEventListener sheetBackTarget (@NonNull View sheet) {
    final BackScale scale = new BackScale(sheet, BackScale.BOTTOM);
    return new SystemBackEventListener() {
      @Override
      public boolean onSystemBackStarted (@NonNull BackEventCompat backEvent) {
        scale.start(backEvent.getSwipeEdge() == BackEventCompat.EDGE_LEFT);
        scale.progress(backEvent.getProgress());
        return true;
      }

      @Override
      public void onSystemBackProgressed (@NonNull BackEventCompat backEvent) {
        scale.progress(backEvent.getProgress());
      }

      @Override
      public void onSystemBackCancelled () {
        scale.release();
      }

      @Override
      public boolean onSystemBackPressed () {
        scale.release();
        return false;
      }
    };
  }

  // A float on a spring that can start with a velocity

  /** Like {@link XgramExpressive.Spring}, plus a start velocity and a token change. Main thread only. */
  public static final class Value {
    public interface Listener {
      void onValue (Value v, float value, float velocity);
    }

    private final FloatValueHolder holder;
    private final SpringAnimation animation;
    private final Listener listener;
    private @Nullable Runnable endListener;
    private float target, velocity;

    public Value (int token, float initialValue, Listener l) {
      this(stiffness(token), damping(token), initialValue, l);
    }

    public Value (float k, float z, float initialValue, Listener l) {
      this.listener = l;
      this.target = initialValue;
      this.holder = new FloatValueHolder(initialValue);
      this.animation = new SpringAnimation(holder);
      this.animation.setSpring(new SpringForce(initialValue).setStiffness(k).setDampingRatio(z));
      this.animation.setMinimumVisibleChange(.001f);
      this.animation.addUpdateListener((a, value, v) -> {
        this.velocity = v;
        listener.onValue(this, value, v);
      });
      this.animation.addEndListener((a, canceled, value, v) -> {
        this.velocity = 0f;
        if (!canceled && endListener != null) {
          endListener.run();
        }
      });
    }

    public Value setThreshold (float minVisibleChange) {
      animation.setMinimumVisibleChange(Math.max(minVisibleChange, 1e-5f));
      return this;
    }

    public Value setEndListener (@Nullable Runnable r) {
      this.endListener = r;
      return this;
    }

    public Value setSpring (int token) {
      return setSpring(stiffness(token), damping(token));
    }

    public Value setSpring (float k, float z) {
      animation.getSpring().setStiffness(k).setDampingRatio(z);
      return this;
    }

    /** Springs to {@code target}; a running spring keeps its velocity. */
    public void animateTo (float target) {
      this.target = target;
      if (!animation.isRunning() && holder.getValue() == target) {
        if (endListener != null) {
          endListener.run();
        }
        return;
      }
      animation.animateToFinalPosition(target);
    }

    /** Springs to {@code target} starting with {@code velocity} (units per second), e.g. a finger fling. */
    public void animateTo (float target, float velocity) {
      this.target = target;
      if (animation.isRunning()) {
        animation.cancel();
      }
      if (holder.getValue() == target && velocity == 0f) {
        if (endListener != null) {
          endListener.run();
        }
        return;
      }
      animation.getSpring().setFinalPosition(target);
      animation.setStartVelocity(velocity);
      animation.start();
    }

    /** Snaps to {@code v} and cancels. The listener is not called. */
    public void setValue (float v) {
      if (animation.isRunning()) {
        animation.cancel();
      }
      holder.setValue(v);
      target = v;
      velocity = 0f;
    }

    public float getValue () {
      return holder.getValue();
    }

    public float getTarget () {
      return target;
    }

    public float getVelocity () {
      return animation.isRunning() ? velocity : 0f;
    }

    public boolean isRunning () {
      return animation.isRunning();
    }

    public void cancel () {
      if (animation.isRunning()) {
        animation.cancel();
      }
    }
  }

  // Stretch overscroll

  public static final int EDGE_TOP = 1, EDGE_BOTTOM = 2, EDGE_BOTH = 3;

  /** An edge that never pulls (for an edge that drags a sheet or collapses a header instead). */
  public static class NoEdgeEffect extends EdgeEffect {
    public NoEdgeEffect (Context context) {
      super(context);
    }

    @Override
    public void onPull (float deltaDistance) { }

    @Override
    public void onPull (float deltaDistance, float displacement) { }

    @Override
    public float onPullDistance (float deltaDistance, float displacement) {
      return 0f;
    }

    @Override
    public float getDistance () {
      return 0f;
    }

    @Override
    public void onRelease () { }

    @Override
    public void onAbsorb (int velocity) { }

    @Override
    public boolean isFinished () {
      return true;
    }

    @Override
    public boolean draw (Canvas canvas) {
      return false;
    }
  }

  private static final class EdgeFactory extends RecyclerView.EdgeEffectFactory {
    private final int edges;

    EdgeFactory (int edges) {
      this.edges = edges;
    }

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect (@NonNull RecyclerView view, int direction) {
      boolean off = (direction == DIRECTION_TOP && (edges & EDGE_TOP) == 0) ||
        (direction == DIRECTION_BOTTOM && (edges & EDGE_BOTTOM) == 0);
      return off ? new NoEdgeEffect(view.getContext()) : new EdgeEffect(view.getContext());
    }
  }

  /**
   * Android 12+ stretch overscroll on {@code edges} of a list that Telegram X set to OVER_SCROLL_NEVER
   * (Compose lists always stretch). No-op with Expressive design off.
   */
  public static void stretch (@Nullable RecyclerView rv, int edges) {
    if (rv == null || !XgramExpressive.isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return;
    }
    rv.setEdgeEffectFactory(edges == EDGE_BOTH ? new RecyclerView.EdgeEffectFactory() : new EdgeFactory(edges));
    rv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
  }

  // Spring scrolling

  /** Programmatic scroll with Compose's animateScrollBy spring (critically damped, no overshoot). */
  public static void smoothScrollBy (@Nullable RecyclerView rv, int dx, int dy) {
    springScrollBy(rv, dx, dy, SCROLL_K, SCROLL_Z);
  }

  /** {@link #smoothScrollBy} when Expressive design is on, RecyclerView's own smoothScrollBy otherwise. */
  public static void smoothScrollByOr (@Nullable RecyclerView rv, int dx, int dy) {
    if (rv == null) {
      return;
    }
    if (XgramExpressive.isEnabled()) {
      smoothScrollBy(rv, dx, dy);
    } else {
      rv.smoothScrollBy(dx, dy);
    }
  }

  /** A sheet snapping open or back (SHEET_SNAP) when Expressive design is on, RecyclerView's smoothScrollBy otherwise. */
  public static void sheetScrollByOr (@Nullable RecyclerView rv, int dx, int dy) {
    if (rv == null) {
      return;
    }
    if (XgramExpressive.isEnabled()) {
      springScrollBy(rv, dx, dy, SHEET_SNAP_K, SHEET_SNAP_Z);
    } else {
      rv.smoothScrollBy(dx, dy);
    }
  }

  /** Scroll by (dx, dy) on a spring; z below 1 can overshoot, which the layout manager may not consume. */
  public static void springScrollBy (@Nullable RecyclerView rv, int dx, int dy, float k, float z) {
    if (rv == null || (dx == 0 && dy == 0)) {
      return;
    }
    int d = Math.max(Math.abs(dx), Math.abs(dy));
    long duration = duration(k, z, 0f, .5f / d);
    if (duration <= 0) {
      rv.scrollBy(dx, dy);
      return;
    }
    rv.smoothScrollBy(dx, dy, interpolator(k, z, 0f, duration), (int) duration);
  }

  // Scroll-linked values (top app bar lift and large title collapse)

  public interface LiftListener {
    void onLiftChanged (ViewController<?> c, float lift, float collapse);
  }

  private static final WeakHashMap<ViewController<?>, WeakReference<ScrollLink>> links = new WeakHashMap<>();
  private static final ArrayList<LiftListener> liftListeners = new ArrayList<>();

  /**
   * lift: 0 at the top of the list, 1 once it has scrolled (springs with EFFECTS_DEFAULT, the M3 top app bar color change).
   * collapse: 0..1 over {@code collapseRangePx} of scroll, raw and scroll-linked, snapped to 0 or 1 when the list stops.
   */
  public static final class ScrollLink extends RecyclerView.OnScrollListener {
    private final WeakReference<ViewController<?>> owner;
    private final int collapseRange;
    private final XgramExpressive.Spring liftSpring;
    private float lift, collapse;

    ScrollLink (ViewController<?> owner, int collapseRangePx) {
      this.owner = new WeakReference<>(owner);
      this.collapseRange = Math.max(0, collapseRangePx);
      this.liftSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> {
        lift = clamp01(value);
        dispatch();
      });
      this.liftSpring.setThreshold(.002f);
    }

    public float getLift () {
      return lift;
    }

    public float getCollapse () {
      return collapse;
    }

    private void dispatch () {
      ViewController<?> c = owner.get();
      if (c == null) {
        return;
      }
      for (int i = liftListeners.size() - 1; i >= 0; i--) {
        if (i < liftListeners.size()) {
          liftListeners.get(i).onLiftChanged(c, lift, collapse);
        }
      }
    }

    private int offset (RecyclerView rv) {
      View first = rv.getChildCount() > 0 ? rv.getChildAt(0) : null;
      if (first == null) {
        return 0;
      }
      if (rv.getChildAdapterPosition(first) != 0) {
        return Integer.MAX_VALUE;
      }
      return Math.max(0, rv.getPaddingTop() - first.getTop());
    }

    void update (RecyclerView rv, boolean animated) {
      float newCollapse = 0f;
      if (collapseRange > 0) {
        int offset = offset(rv);
        newCollapse = offset == Integer.MAX_VALUE ? 1f : clamp01(offset / (float) collapseRange);
      }
      boolean collapseChanged = newCollapse != collapse;
      collapse = newCollapse;
      float liftTarget = rv.canScrollVertically(-1) ? 1f : 0f;
      if (liftSpring.getTarget() != liftTarget) {
        if (animated) {
          liftSpring.animateTo(liftTarget);
        } else {
          liftSpring.setValue(liftTarget);
          lift = liftTarget;
          dispatch();
        }
      } else if (collapseChanged) {
        dispatch();
      }
    }

    @Override
    public void onScrolled (@NonNull RecyclerView rv, int dx, int dy) {
      update(rv, true);
    }

    @Override
    public void onScrollStateChanged (@NonNull RecyclerView rv, int newState) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE || collapseRange <= 0 || collapse <= 0f || collapse >= 1f) {
        return;
      }
      int offset = offset(rv);
      if (offset == Integer.MAX_VALUE) {
        return;
      }
      int delta = collapse >= .5f ? collapseRange - offset : -offset;
      springScrollBy(rv, 0, delta, SNAP_K, SNAP_Z);
    }
  }

  /** Tracks lift / collapse of {@code rv} for {@code owner}; one link per list. Null with Expressive design off. */
  @Nullable
  public static ScrollLink attachScrollLink (@Nullable ViewController<?> owner, @Nullable RecyclerView rv, int collapseRangePx) {
    if (owner == null || rv == null || !XgramExpressive.isEnabled()) {
      return null;
    }
    WeakReference<ScrollLink> ref = links.get(owner);
    ScrollLink existing = ref != null ? ref.get() : null;
    if (existing != null) {
      rv.removeOnScrollListener(existing);
    }
    ScrollLink link = new ScrollLink(owner, collapseRangePx);
    links.put(owner, new WeakReference<>(link));
    rv.addOnScrollListener(link);
    rv.post(() -> link.update(rv, false));
    return link;
  }

  @Nullable
  private static ScrollLink linkOf (@Nullable ViewController<?> c) {
    if (c == null) {
      return null;
    }
    WeakReference<ScrollLink> ref = links.get(c);
    return ref != null ? ref.get() : null;
  }

  public static float liftOf (@Nullable ViewController<?> c) {
    ScrollLink link = linkOf(c);
    return link != null ? link.lift : 0f;
  }

  public static float collapseOf (@Nullable ViewController<?> c) {
    ScrollLink link = linkOf(c);
    return link != null ? link.collapse : 0f;
  }

  public static void addLiftListener (@NonNull LiftListener l) {
    if (!liftListeners.contains(l)) {
      liftListeners.add(l);
    }
  }

  public static void removeLiftListener (@NonNull LiftListener l) {
    liftListeners.remove(l);
  }

  /** Re-sends the values of {@code c}, e.g. when a pager shows another page under the same header. */
  public static void notifyActive (@Nullable ViewController<?> c) {
    if (c == null || !XgramExpressive.isEnabled()) {
      return;
    }
    float lift = liftOf(c), collapse = collapseOf(c);
    for (int i = liftListeners.size() - 1; i >= 0; i--) {
      if (i < liftListeners.size()) {
        liftListeners.get(i).onLiftChanged(c, lift, collapse);
      }
    }
  }

  // List items: the spring core of CustomItemAnimator

  public interface ItemAlpha {
    void setItemAlpha (View view, float alpha);
  }

  private static final float ADD_SCALE = .94f;
  private static final float MIN_PX = .5f, MIN_ALPHA = 1f / 256f, MIN_SCALE = .002f;

  /**
   * Removals, moves, changes and additions start together on springs, as Compose animateItem does: fade out with
   * EFFECTS_FAST, move with SPATIAL_DEFAULT (a retargeted move keeps its velocity), fade in with EFFECTS_DEFAULT and
   * scale in from .94. Ending an item is synchronous. Reduce motion: no scale, moves use STD_SPATIAL_FAST.
   */
  public static final class ItemSprings {
    private static final int REMOVE = 0, ADD = 1, MOVE = 2, CHANGE_OLD = 3, CHANGE_NEW = 4;

    private static final class Move {
      final RecyclerView.ViewHolder holder;
      final int dx, dy;
      final float vx, vy;

      Move (RecyclerView.ViewHolder holder, int dx, int dy, float vx, float vy) {
        this.holder = holder;
        this.dx = dx;
        this.dy = dy;
        this.vx = vx;
        this.vy = vy;
      }
    }

    private static final class Change {
      RecyclerView.ViewHolder oldHolder, newHolder;
      final int fromX, fromY, toX, toY;

      Change (RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
        this.oldHolder = oldHolder;
        this.newHolder = newHolder;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
      }
    }

    private static final class Run {
      final RecyclerView.ViewHolder holder;
      final int kind;
      final ArrayList<SpringAnimation> springs = new ArrayList<>(3);
      int remaining;
      boolean done, layered;

      Run (RecyclerView.ViewHolder holder, int kind) {
        this.holder = holder;
        this.kind = kind;
      }
    }

    private final SimpleItemAnimator owner;
    private final ItemAlpha alphaSink;
    private final FloatPropertyCompat<View> alphaProperty;

    private final ArrayList<RecyclerView.ViewHolder> pendingRemovals = new ArrayList<>();
    private final ArrayList<RecyclerView.ViewHolder> pendingAdditions = new ArrayList<>();
    private final ArrayList<Move> pendingMoves = new ArrayList<>();
    private final ArrayList<Change> pendingChanges = new ArrayList<>();
    private final ArrayList<Run> runs = new ArrayList<>();
    private final WeakHashMap<View, float[]> velocities = new WeakHashMap<>();

    public ItemSprings (@NonNull SimpleItemAnimator owner, @NonNull ItemAlpha alphaSink) {
      this.owner = owner;
      this.alphaSink = alphaSink;
      this.alphaProperty = new FloatPropertyCompat<View>("alpha") {
        @Override
        public float getValue (View view) {
          return view.getAlpha();
        }

        @Override
        public void setValue (View view, float value) {
          ItemSprings.this.alphaSink.setItemAlpha(view, clamp01(value));
        }
      };
    }

    // Recording

    public boolean animateRemove (RecyclerView.ViewHolder holder) {
      endAnimation(holder);
      pendingRemovals.add(holder);
      return true;
    }

    public boolean animateAdd (RecyclerView.ViewHolder holder) {
      endAnimation(holder);
      View view = holder.itemView;
      alphaSink.setItemAlpha(view, 0f);
      if (!reduce()) {
        view.setScaleX(ADD_SCALE);
        view.setScaleY(ADD_SCALE);
      }
      pendingAdditions.add(holder);
      return true;
    }

    public boolean animateMove (RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
      View view = holder.itemView;
      float[] velocity = runningVelocity(holder);
      fromX += (int) view.getTranslationX();
      fromY += (int) view.getTranslationY();
      endAnimation(holder);
      int dx = toX - fromX, dy = toY - fromY;
      if (dx == 0 && dy == 0) {
        owner.dispatchMoveFinished(holder);
        return false;
      }
      if (dx != 0) {
        view.setTranslationX(-dx);
      }
      if (dy != 0) {
        view.setTranslationY(-dy);
      }
      pendingMoves.add(new Move(holder, dx, dy, velocity != null ? velocity[0] : 0f, velocity != null ? velocity[1] : 0f));
      return true;
    }

    public boolean animateChange (RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
      if (oldHolder == newHolder) {
        return animateMove(oldHolder, fromX, fromY, toX, toY);
      }
      View oldView = oldHolder.itemView;
      float prevTx = oldView.getTranslationX(), prevTy = oldView.getTranslationY(), prevAlpha = oldView.getAlpha();
      endAnimation(oldHolder);
      int dx = (int) (toX - fromX - prevTx), dy = (int) (toY - fromY - prevTy);
      oldView.setTranslationX(prevTx);
      oldView.setTranslationY(prevTy);
      alphaSink.setItemAlpha(oldView, prevAlpha);
      if (newHolder != null) {
        endAnimation(newHolder);
        View newView = newHolder.itemView;
        newView.setTranslationX(-dx);
        newView.setTranslationY(-dy);
        alphaSink.setItemAlpha(newView, 0f);
      }
      pendingChanges.add(new Change(oldHolder, newHolder, fromX, fromY, toX, toY));
      return true;
    }

    // Running

    public void runPendingAnimations () {
      if (pendingRemovals.isEmpty() && pendingAdditions.isEmpty() && pendingMoves.isEmpty() && pendingChanges.isEmpty()) {
        return;
      }
      boolean reduce = reduce();
      ArrayList<RecyclerView.ViewHolder> removals = new ArrayList<>(pendingRemovals);
      ArrayList<Move> moves = new ArrayList<>(pendingMoves);
      ArrayList<Change> changes = new ArrayList<>(pendingChanges);
      ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>(pendingAdditions);
      pendingRemovals.clear();
      pendingMoves.clear();
      pendingChanges.clear();
      pendingAdditions.clear();
      for (RecyclerView.ViewHolder holder : removals) {
        Run run = newRun(holder, REMOVE);
        owner.dispatchRemoveStarting(holder);
        addSpring(run, alphaProperty, 0f, XgramExpressive.EFFECTS_FAST, MIN_ALPHA, 0f, -1);
        checkEmpty(run);
      }
      int moveToken = reduce ? XgramExpressive.STD_SPATIAL_FAST : XgramExpressive.SPATIAL_DEFAULT;
      for (Move move : moves) {
        Run run = newRun(move.holder, MOVE);
        owner.dispatchMoveStarting(move.holder);
        if (move.dx != 0) {
          addSpring(run, DynamicAnimation.TRANSLATION_X, 0f, moveToken, MIN_PX, move.vx, 0);
        }
        if (move.dy != 0) {
          addSpring(run, DynamicAnimation.TRANSLATION_Y, 0f, moveToken, MIN_PX, move.vy, 1);
        }
        checkEmpty(run);
      }
      for (Change change : changes) {
        if (change.oldHolder != null) {
          View view = change.oldHolder.itemView;
          Run run = newRun(change.oldHolder, CHANGE_OLD);
          owner.dispatchChangeStarting(change.oldHolder, true);
          addSpring(run, alphaProperty, 0f, XgramExpressive.EFFECTS_FAST, MIN_ALPHA, 0f, -1);
          float tx = change.toX - change.fromX, ty = change.toY - change.fromY;
          if (Math.abs(view.getTranslationX() - tx) >= MIN_PX) {
            addSpring(run, DynamicAnimation.TRANSLATION_X, tx, moveToken, MIN_PX, 0f, 0);
          } else {
            view.setTranslationX(tx);
          }
          if (Math.abs(view.getTranslationY() - ty) >= MIN_PX) {
            addSpring(run, DynamicAnimation.TRANSLATION_Y, ty, moveToken, MIN_PX, 0f, 1);
          } else {
            view.setTranslationY(ty);
          }
          checkEmpty(run);
        }
        if (change.newHolder != null) {
          View view = change.newHolder.itemView;
          Run run = newRun(change.newHolder, CHANGE_NEW);
          owner.dispatchChangeStarting(change.newHolder, false);
          addSpring(run, alphaProperty, 1f, XgramExpressive.EFFECTS_DEFAULT, MIN_ALPHA, 0f, -1);
          if (Math.abs(view.getTranslationX()) >= MIN_PX) {
            addSpring(run, DynamicAnimation.TRANSLATION_X, 0f, moveToken, MIN_PX, 0f, 0);
          } else {
            view.setTranslationX(0f);
          }
          if (Math.abs(view.getTranslationY()) >= MIN_PX) {
            addSpring(run, DynamicAnimation.TRANSLATION_Y, 0f, moveToken, MIN_PX, 0f, 1);
          } else {
            view.setTranslationY(0f);
          }
          checkEmpty(run);
        }
      }
      for (RecyclerView.ViewHolder holder : additions) {
        View view = holder.itemView;
        Run run = newRun(holder, ADD);
        owner.dispatchAddStarting(holder);
        addSpring(run, alphaProperty, 1f, XgramExpressive.EFFECTS_DEFAULT, MIN_ALPHA, 0f, -1);
        if (view.getScaleX() != 1f || view.getScaleY() != 1f) {
          addSpring(run, DynamicAnimation.SCALE_X, 1f, XgramExpressive.SPATIAL_DEFAULT, MIN_SCALE, 0f, -1);
          addSpring(run, DynamicAnimation.SCALE_Y, 1f, XgramExpressive.SPATIAL_DEFAULT, MIN_SCALE, 0f, -1);
        }
        checkEmpty(run);
      }
    }

    private Run newRun (RecyclerView.ViewHolder holder, int kind) {
      Run run = new Run(holder, kind);
      runs.add(run);
      return run;
    }

    private void checkEmpty (Run run) {
      if (run.remaining == 0 && !run.done) {
        finish(run);
        dispatchFinishedWhenDone();
      }
    }

    /** axis: 0 = x, 1 = y (velocity tracking), -1 = none. */
    private void addSpring (Run run, FloatPropertyCompat<View> property, float finalValue, int token, float minVisibleChange, float startVelocity, int axis) {
      final View view = run.holder.itemView;
      if (property == alphaProperty) {
        layer(run);
      }
      SpringAnimation a = new SpringAnimation(view, property, finalValue);
      a.getSpring().setStiffness(XgramExpressive.stiffness(token)).setDampingRatio(XgramExpressive.damping(token));
      a.setMinimumVisibleChange(minVisibleChange);
      if (startVelocity != 0f) {
        a.setStartVelocity(startVelocity);
      }
      if (axis >= 0) {
        a.addUpdateListener((animation, value, velocity) -> {
          float[] v = velocities.get(view);
          if (v == null) {
            v = new float[2];
            velocities.put(view, v);
          }
          v[axis] = velocity;
        });
      }
      a.addEndListener((animation, canceled, value, velocity) -> onSpringEnd(run));
      run.springs.add(a);
      run.remaining++;
      a.start();
    }

    /**
     * A fading row gets a hardware layer while the fade runs, so its alpha is a texture blend instead of an
     * offscreen pass of the whole row every frame. Not for rows over twice the list's area.
     */
    private static void layer (Run run) {
      View view = run.holder.itemView;
      if (run.layered || view.getLayerType() != View.LAYER_TYPE_NONE || !view.isHardwareAccelerated()) {
        return;
      }
      int w = view.getWidth(), h = view.getHeight();
      if (w <= 0 || h <= 0) {
        return;
      }
      android.view.ViewParent parent = view.getParent();
      if (parent instanceof View) {
        View list = (View) parent;
        if ((long) w * h > 2L * list.getWidth() * list.getHeight()) {
          return;
        }
      }
      view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
      run.layered = true;
    }

    private void onSpringEnd (Run run) {
      if (run.done) {
        return;
      }
      if (--run.remaining > 0) {
        return;
      }
      finish(run);
      dispatchFinishedWhenDone();
    }

    @Nullable
    private float[] runningVelocity (RecyclerView.ViewHolder holder) {
      for (int i = runs.size() - 1; i >= 0; i--) {
        Run run = runs.get(i);
        if (run.holder == holder && !run.done && (run.kind == MOVE || run.kind == CHANGE_NEW)) {
          float[] v = velocities.get(holder.itemView);
          return v != null ? new float[] {v[0], v[1]} : null;
        }
      }
      return null;
    }

    private void finish (Run run) {
      if (run.done) {
        return;
      }
      run.done = true;
      for (SpringAnimation a : run.springs) {
        if (a.isRunning()) {
          a.cancel();
        }
      }
      runs.remove(run);
      View view = run.holder.itemView;
      if (run.layered) {
        run.layered = false;
        view.setLayerType(View.LAYER_TYPE_NONE, null);
      }
      switch (run.kind) {
        case REMOVE:
          alphaSink.setItemAlpha(view, 1f);
          owner.dispatchRemoveFinished(run.holder);
          break;
        case ADD:
          alphaSink.setItemAlpha(view, 1f);
          view.setScaleX(1f);
          view.setScaleY(1f);
          owner.dispatchAddFinished(run.holder);
          break;
        case MOVE:
          view.setTranslationX(0f);
          view.setTranslationY(0f);
          velocities.remove(view);
          owner.dispatchMoveFinished(run.holder);
          break;
        case CHANGE_OLD:
        case CHANGE_NEW:
          alphaSink.setItemAlpha(view, 1f);
          view.setTranslationX(0f);
          view.setTranslationY(0f);
          velocities.remove(view);
          owner.dispatchChangeFinished(run.holder, run.kind == CHANGE_OLD);
          break;
      }
    }

    private void endPendingChange (RecyclerView.ViewHolder item) {
      for (int i = pendingChanges.size() - 1; i >= 0; i--) {
        if (i >= pendingChanges.size()) {
          continue;
        }
        Change change = pendingChanges.get(i);
        boolean oldItem;
        if (change.newHolder == item) {
          change.newHolder = null;
          oldItem = false;
        } else if (change.oldHolder == item) {
          change.oldHolder = null;
          oldItem = true;
        } else {
          continue;
        }
        alphaSink.setItemAlpha(item.itemView, 1f);
        item.itemView.setTranslationX(0f);
        item.itemView.setTranslationY(0f);
        owner.dispatchChangeFinished(item, oldItem);
        if (change.oldHolder == null && change.newHolder == null) {
          pendingChanges.remove(change);
        }
      }
    }

    public void endAnimation (RecyclerView.ViewHolder item) {
      View view = item.itemView;
      for (int i = pendingMoves.size() - 1; i >= 0; i--) {
        if (i < pendingMoves.size() && pendingMoves.get(i).holder == item) {
          pendingMoves.remove(i);
          view.setTranslationX(0f);
          view.setTranslationY(0f);
          owner.dispatchMoveFinished(item);
        }
      }
      endPendingChange(item);
      if (pendingRemovals.remove(item)) {
        alphaSink.setItemAlpha(view, 1f);
        owner.dispatchRemoveFinished(item);
      }
      if (pendingAdditions.remove(item)) {
        alphaSink.setItemAlpha(view, 1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        owner.dispatchAddFinished(item);
      }
      ArrayList<Run> running = null;
      for (Run run : runs) {
        if (run.holder == item) {
          if (running == null) {
            running = new ArrayList<>(2);
          }
          running.add(run);
        }
      }
      if (running != null) {
        for (Run run : running) {
          finish(run);
        }
      }
      dispatchFinishedWhenDone();
    }

    public boolean isRunning () {
      return !pendingRemovals.isEmpty() || !pendingAdditions.isEmpty() || !pendingMoves.isEmpty() || !pendingChanges.isEmpty() || !runs.isEmpty();
    }

    private void dispatchFinishedWhenDone () {
      if (!isRunning()) {
        owner.dispatchAnimationsFinished();
      }
    }

    public void endAnimations () {
      for (int i = pendingMoves.size() - 1; i >= 0; i--) {
        Move move = pendingMoves.remove(i);
        move.holder.itemView.setTranslationX(0f);
        move.holder.itemView.setTranslationY(0f);
        owner.dispatchMoveFinished(move.holder);
      }
      for (int i = pendingRemovals.size() - 1; i >= 0; i--) {
        owner.dispatchRemoveFinished(pendingRemovals.remove(i));
      }
      for (int i = pendingAdditions.size() - 1; i >= 0; i--) {
        RecyclerView.ViewHolder holder = pendingAdditions.remove(i);
        alphaSink.setItemAlpha(holder.itemView, 1f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        owner.dispatchAddFinished(holder);
      }
      ArrayList<Change> changes = new ArrayList<>(pendingChanges);
      for (Change change : changes) {
        if (change.oldHolder != null) {
          endPendingChange(change.oldHolder);
        }
        if (change.newHolder != null) {
          endPendingChange(change.newHolder);
        }
      }
      pendingChanges.clear();
      if (!isRunning()) {
        return;
      }
      ArrayList<Run> running = new ArrayList<>(runs);
      for (Run run : running) {
        finish(run);
      }
      runs.clear();
      owner.dispatchAnimationsFinished();
    }
  }
}
