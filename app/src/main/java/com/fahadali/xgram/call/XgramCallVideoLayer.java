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
package com.fahadali.xgram.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

/**
 * XGram: the video stage of a call screen, shared by the Material 3 Expressive screen (XgramM3CallScreen) and Telegram
 * X's (CallController): the full screen views of your camera and of the other side's (the other side's above yours),
 * the tile's view, and the scrims over them. With no video it draws nothing.
 * <ul>
 * <li>{@link #PRESENT_VOICE}: no video; {@link #PRESENT_LOCAL}: your camera only; {@link #PRESENT_REMOTE}: the other
 * side's video, yours in the tile (swapped: yours full screen and theirs in the tile).</li>
 * <li>A stage view fades in only after its own first frame since it was bound, so there is never a black flash; the
 * one it replaces stays under it until it covers it.</li>
 * <li>The other side's video fills the stage unless its aspect is too far from the stage's (then it fits, on black).
 * Your camera always fills, mirrored for the front camera.</li>
 * </ul>
 * Fades: EFFECTS_DEFAULT springs (Expressive), Telegram X's 180ms decelerate (classic). Scrim gradients are built only
 * when their geometry changes. Main thread only.
 */
public final class XgramCallVideoLayer extends FrameLayoutFix implements XgramVideoSurface.FrameListener {
  public static final int STYLE_EXPRESSIVE = 0, STYLE_CLASSIC = 1;
  public static final int PRESENT_VOICE = 0, PRESENT_LOCAL = 1, PRESENT_REMOTE = 2;

  /** The tile with the small video (XgramM3CallVideoTile). */
  public interface Tile {
    void setVideoSurface (@Nullable XgramVideoSurface surface);
    void setTileShown (boolean shown, boolean animated);
    /** The tile shows another feed: its content waits for the first frame ({@code pulse}: a swap). */
    void onTileFeedChanged (boolean swapped, boolean pulse);
    void onTileFirstFrame ();
  }

  public interface Listener {
    void onPresentationChanged (int presentation, boolean animated);
  }

  private static final float FIT_THRESHOLD = .7f;
  private static final float[] SCRIM_STOPS = {1f, .6f, .3f, .1f, 0f};
  private static final float[] SCRIM_POSITIONS = {0f, .25f, .5f, .75f, 1f};

  private final int style;
  private final Listener listener;
  private @Nullable Tile tile;
  private @Nullable XgramCallVideo video;
  private @Nullable String peerName;

  private @Nullable XgramVideoSurface localStage, remoteStage, tileSurface;
  private boolean localBound, remoteBound, localReady, remoteReady;
  private int tileFeed;

  private int presentation = PRESENT_VOICE;
  private boolean swapped;

  private final Fader localAlpha, remoteAlpha;
  private boolean reconciling, needReconcile;

  private int stageTop, stageBottom = -1;

  // Scrims
  private final float topScrimOpacity;
  private final Paint topPaint = new Paint(), bottomPaint = new Paint();
  private final int[] scrimColors = new int[SCRIM_STOPS.length];
  private int topStart, topEnd, bottomStart, bottomEnd;
  private int builtTopStart = Integer.MIN_VALUE, builtTopEnd, builtBottomStart = Integer.MIN_VALUE, builtBottomEnd;
  private float topAlpha, bottomAlpha;

  public XgramCallVideoLayer (Context context, int style, Listener listener) {
    super(context);
    this.style = style;
    this.listener = listener;
    this.localAlpha = new Fader(0);
    this.remoteAlpha = new Fader(1);
    this.topScrimOpacity = style == STYLE_CLASSIC ? .5f : .4f;
    setWillNotDraw(false);
    setClipChildren(true);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void setTile (@Nullable Tile tile) {
    this.tile = tile;
  }

  public int getPresentation () {
    return presentation;
  }

  public boolean isSwapped () {
    return swapped;
  }

  public @Nullable XgramCallVideo getVideo () {
    return video;
  }

  public void setPeerName (@Nullable String name) {
    this.peerName = name;
    if (remoteStage != null) {
      remoteStage.view().setContentDescription(Lang.getString(R.string.XgramVideoFrom, name != null ? name : ""));
    }
  }

  /** The call's video; another call's video drops this one's views (they belong to its backend). */
  public void setVideo (@Nullable XgramCallVideo video, boolean animated) {
    if (this.video != video) {
      releaseSurfaces();
      this.video = video;
      swapped = false;
    }
    update(animated);
  }

  /** The stage: where the video goes (tabletop: above the fold; with light status icons in two panes: under the bar). */
  public void setStageBounds (int top, int bottom) {
    if (stageTop != top || stageBottom != bottom) {
      stageTop = top;
      stageBottom = bottom;
      layoutStages(); // from the parent's onLayout: measured and placed here, no layout pass of its own
      updateRemoteScale();
      invalidate();
    }
  }

  private int stageTopPx () {
    return Math.max(0, stageTop);
  }

  private int stageBottomPx (int height) {
    return stageBottom > stageTopPx() ? Math.min(stageBottom, height) : height;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int width = getMeasuredWidth(), height = getMeasuredHeight();
    measureStage(localStage, width, stageBottomPx(height) - stageTopPx());
    measureStage(remoteStage, width, stageBottomPx(height) - stageTopPx());
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    layoutStages();
  }

  private static void measureStage (@Nullable XgramVideoSurface surface, int width, int height) {
    if (surface == null || width <= 0 || height <= 0) {
      return;
    }
    final View view = surface.view();
    if (view.getMeasuredWidth() != width || view.getMeasuredHeight() != height) {
      view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
  }

  private void layoutStages () {
    final int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    final int top = stageTopPx(), bottom = stageBottomPx(height);
    layoutStage(localStage, width, top, bottom);
    layoutStage(remoteStage, width, top, bottom);
  }

  private static void layoutStage (@Nullable XgramVideoSurface surface, int width, int top, int bottom) {
    if (surface == null || bottom <= top) {
      return;
    }
    measureStage(surface, width, bottom - top);
    final View view = surface.view();
    if (view.getLeft() != 0 || view.getTop() != top || view.getRight() != width || view.getBottom() != bottom) {
      view.layout(0, top, width, bottom);
    }
  }

  /** Swaps the stage and the tile (a tap on the tile). */
  public void toggleSwap () {
    if (presentation == PRESENT_REMOTE && video != null && video.isLocalShown()) {
      swapped = !swapped;
      reconcile(true, true);
    }
  }

  // State

  /** Recomputes the presentation from the call's video; on every change of it. */
  public void update (boolean animated) {
    int p = PRESENT_VOICE;
    final XgramCallVideo v = video;
    if (v != null && !v.isReleased()) {
      if (v.isRemoteShown()) {
        p = PRESENT_REMOTE;
      } else if (v.isLocalShown()) {
        p = PRESENT_LOCAL;
      }
    }
    if (p != PRESENT_REMOTE || v == null || !v.isLocalShown()) {
      swapped = false;
    }
    final boolean changed = p != presentation;
    presentation = p;
    if (localStage != null && v != null) {
      localStage.setMirror(v.isFront());
    }
    if (tileSurface != null && v != null && tileFeed == XgramVideoBackend.FEED_LOCAL) {
      tileSurface.setMirror(v.isFront());
    }
    if (changed) {
      listener.onPresentationChanged(p, animated);
    }
    if (v == null || v.isReleased()) {
      releaseSurfaces();
      return;
    }
    reconcile(animated, false);
  }

  private void reconcile (boolean animated, boolean pulse) {
    if (reconciling) {
      needReconcile = true;
      return;
    }
    reconciling = true;
    try {
      do {
        needReconcile = false;
        reconcileOnce(animated, pulse);
        pulse = false;
      } while (needReconcile);
    } finally {
      reconciling = false;
    }
  }

  private void reconcileOnce (boolean animated, boolean pulse) {
    final XgramCallVideo v = video;
    if (v == null) {
      return;
    }
    final boolean wantLocal = presentation == PRESENT_LOCAL || (presentation == PRESENT_REMOTE && swapped);
    final boolean wantRemote = presentation == PRESENT_REMOTE && !swapped;
    if (wantLocal) {
      bindLocal(v);
    }
    if (wantRemote) {
      bindRemote(v);
    }
    if (wantRemote) {
      if (remoteReady) {
        remoteAlpha.animateTo(1f, animated);
      }
      // Yours stays under it until theirs covers the stage
      if (localBound && remoteAlpha.isSettledAt(1f)) {
        unbindLocal(v);
      }
    } else if (wantLocal) {
      if (localReady) {
        if (remoteBound && remoteAlpha.getValue() > 0f) {
          localAlpha.setValue(1f); // under theirs, which fades away
          remoteAlpha.animateTo(0f, animated);
        } else {
          localAlpha.animateTo(1f, animated);
        }
      }
      if (remoteBound && remoteAlpha.isSettledAt(0f)) {
        unbindRemote(v);
      }
    } else {
      localAlpha.animateTo(0f, animated);
      remoteAlpha.animateTo(0f, animated);
      if (localBound && localAlpha.isSettledAt(0f)) {
        unbindLocal(v);
      }
      if (remoteBound && remoteAlpha.isSettledAt(0f)) {
        unbindRemote(v);
      }
    }
    applyAlphas();

    // The tile: yours under theirs, theirs when swapped
    int feed = 0;
    if (presentation == PRESENT_REMOTE) {
      feed = swapped ? XgramVideoBackend.FEED_REMOTE : v.isLocalShown() ? XgramVideoBackend.FEED_LOCAL : 0;
    }
    setTileFeed(v, feed, animated, pulse);
  }

  private void bindLocal (XgramCallVideo v) {
    if (localBound) {
      return;
    }
    if (localStage == null) {
      localStage = newStage(v, 0);
      localStage.view().setContentDescription(Lang.getString(R.string.XgramVideoYours));
    }
    localStage.setScaleMode(XgramVideoSurface.SCALE_FILL);
    localStage.setMirror(v.isFront());
    localBound = true;
    localReady = false;
    localAlpha.setValue(0f);
    localStage.view().setVisibility(View.VISIBLE); // drawn at alpha 0, so its texture is ready for the first frame
    localStage.clear(); // a frame that raced the last unbind would hold back this binding's first frame
    v.backend().setTarget(XgramVideoBackend.FEED_LOCAL, localStage, true);
  }

  private void unbindLocal (XgramCallVideo v) {
    if (!localBound || localStage == null) {
      return;
    }
    localBound = false;
    localReady = false;
    v.backend().setTarget(XgramVideoBackend.FEED_LOCAL, localStage, false);
    localStage.clear();
    localAlpha.setValue(0f);
    localStage.view().setVisibility(View.INVISIBLE);
  }

  private void bindRemote (XgramCallVideo v) {
    if (remoteBound) {
      return;
    }
    if (remoteStage == null) {
      remoteStage = newStage(v, -1);
      remoteStage.view().setContentDescription(Lang.getString(R.string.XgramVideoFrom, peerName != null ? peerName : ""));
    }
    remoteStage.setMirror(false);
    remoteBound = true;
    remoteReady = false;
    remoteAlpha.setValue(0f);
    remoteStage.view().setVisibility(View.VISIBLE);
    updateRemoteScale();
    remoteStage.clear(); // a frame that raced the last unbind would hold back this binding's first frame
    v.backend().setTarget(XgramVideoBackend.FEED_REMOTE, remoteStage, true);
  }

  private void unbindRemote (XgramCallVideo v) {
    if (!remoteBound || remoteStage == null) {
      return;
    }
    remoteBound = false;
    remoteReady = false;
    v.backend().setTarget(XgramVideoBackend.FEED_REMOTE, remoteStage, false);
    remoteStage.clear();
    remoteAlpha.setValue(0f);
    remoteStage.view().setVisibility(View.INVISIBLE);
  }

  private XgramVideoSurface newStage (XgramCallVideo v, int index) {
    XgramVideoSurface surface = v.backend().createSurface(getContext());
    surface.setFrameListener(this);
    View view = surface.view();
    view.setAlpha(0f);
    view.setVisibility(View.INVISIBLE);
    view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP));
    if (index >= 0) {
      addView(view, index);
    } else {
      addView(view);
    }
    return surface;
  }

  private void setTileFeed (XgramCallVideo v, int feed, boolean animated, boolean pulse) {
    if (tileFeed == feed) {
      if (pulse && tile != null && feed != 0) {
        tile.onTileFeedChanged(swapped, true);
      }
      return;
    }
    final boolean wasShown = tileFeed != 0;
    if (tileFeed != 0 && tileSurface != null) {
      v.backend().setTarget(tileFeed, tileSurface, false);
    }
    tileFeed = feed;
    if (tile == null) {
      return;
    }
    if (feed != 0) {
      if (tileSurface == null) {
        tileSurface = v.backend().createSurface(getContext());
        tileSurface.setFrameListener(this);
        tileSurface.setScaleMode(XgramVideoSurface.SCALE_FILL);
        tile.setVideoSurface(tileSurface);
      }
      tileSurface.clear();
      tileSurface.setMirror(feed == XgramVideoBackend.FEED_LOCAL && v.isFront());
      tile.onTileFeedChanged(swapped, wasShown || pulse);
      v.backend().setTarget(feed, tileSurface, true);
      tile.setTileShown(true, animated);
    } else {
      tile.setTileShown(false, animated);
    }
  }

  private void updateRemoteScale () {
    if (remoteStage == null) {
      return;
    }
    final int fw = remoteStage.frameWidth(), fh = remoteStage.frameHeight();
    final int sw = getWidth(), sh = stageBottomPx(getHeight()) - stageTopPx();
    int mode = XgramVideoSurface.SCALE_FILL;
    if (fw > 0 && fh > 0 && sw > 0 && sh > 0) {
      final float stageAspect = sw / (float) sh, frameAspect = fw / (float) fh;
      if (Math.min(stageAspect / frameAspect, frameAspect / stageAspect) < FIT_THRESHOLD) {
        mode = XgramVideoSurface.SCALE_FIT; // on black
      }
    }
    remoteStage.setScaleMode(mode);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateRemoteScale();
  }

  // XgramVideoSurface.FrameListener

  @Override
  public void onFirstFrame (XgramVideoSurface surface) {
    if (surface == tileSurface) {
      if (tile != null && tileFeed != 0) {
        tile.onTileFirstFrame();
      }
      return;
    }
    if (surface == localStage && localBound) {
      localReady = true;
    } else if (surface == remoteStage && remoteBound) {
      remoteReady = true;
    } else {
      return;
    }
    reconcile(getWidth() > 0, false);
  }

  @Override
  public void onFrameSize (XgramVideoSurface surface, int rotatedWidth, int rotatedHeight) {
    if (surface == remoteStage) {
      updateRemoteScale();
    }
  }

  // Alphas

  private void applyAlphas () {
    if (localStage != null) {
      localStage.view().setAlpha(MathUtils.clamp(localAlpha.getValue()));
    }
    if (remoteStage != null) {
      remoteStage.view().setAlpha(MathUtils.clamp(remoteAlpha.getValue()));
    }
  }

  /** A stage alpha: an EFFECTS_DEFAULT spring (Expressive) or Telegram X's 180ms decelerate (classic). */
  private final class Fader implements FactorAnimator.Target {
    private final @Nullable XgramExpressive.Spring spring;
    private final @Nullable FactorAnimator animator;
    private float target;

    Fader (int id) {
      if (style == STYLE_EXPRESSIVE) {
        spring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> applyAlphas());
        spring.setEndListener(this::onSettled);
        animator = null;
      } else {
        spring = null;
        animator = new FactorAnimator(id, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, 0f);
      }
    }

    float getValue () {
      return spring != null ? spring.getValue() : animator.getFactor();
    }

    boolean isSettledAt (float value) {
      if (target != value) {
        return false;
      }
      return spring != null ? !spring.isRunning() && spring.getValue() == value : !animator.isAnimating() && animator.getFactor() == value;
    }

    void setValue (float value) {
      target = value;
      if (spring != null) {
        spring.setValue(value);
      } else {
        animator.forceFactor(value);
      }
      applyAlphas();
    }

    void animateTo (float value, boolean animated) {
      if (!animated) {
        if (getValue() != value || target != value) {
          setValue(value);
        }
        return;
      }
      if (target == value) {
        return;
      }
      target = value;
      if (spring != null) {
        spring.animateTo(value);
      } else {
        animator.animateTo(value);
      }
    }

    private void onSettled () {
      reconcile(true, false);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      applyAlphas();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (!reconciling) {
        reconcile(true, false);
      }
    }
  }

  // Scrims

  /**
   * The scrims over the video: from the top of the stage down to {@code topEnd} and from {@code bottomStart} to the
   * bottom of the stage, each at its alpha (0 for none). Gradients are rebuilt only when their geometry changes.
   */
  public void setScrims (int topEnd, float topAlpha, int bottomStart, float bottomAlpha) {
    final int stageEnd = stageBottom > stageTop ? stageBottom : getHeight();
    boolean changed = false;
    if (this.topStart != stageTop || this.topEnd != topEnd || this.bottomStart != bottomStart || this.bottomEnd != stageEnd) {
      this.topStart = stageTop;
      this.topEnd = topEnd;
      this.bottomStart = bottomStart;
      this.bottomEnd = stageEnd;
      changed = true;
    }
    if (this.topAlpha != topAlpha || this.bottomAlpha != bottomAlpha) {
      this.topAlpha = topAlpha;
      this.bottomAlpha = bottomAlpha;
      changed = true;
    }
    if (changed) {
      invalidate();
    }
  }

  private void buildScrims () {
    if (topEnd > topStart && (builtTopStart != topStart || builtTopEnd != topEnd)) {
      builtTopStart = topStart;
      builtTopEnd = topEnd;
      for (int i = 0; i < SCRIM_STOPS.length; i++) {
        scrimColors[i] = Math.round(255f * topScrimOpacity * SCRIM_STOPS[i]) << 24;
      }
      topPaint.setShader(new LinearGradient(0, topStart, 0, topEnd, scrimColors, SCRIM_POSITIONS, Shader.TileMode.CLAMP));
    }
    if (bottomEnd > bottomStart && bottomStart >= 0 && (builtBottomStart != bottomStart || builtBottomEnd != bottomEnd)) {
      builtBottomStart = bottomStart;
      builtBottomEnd = bottomEnd;
      for (int i = 0; i < SCRIM_STOPS.length; i++) {
        scrimColors[i] = Math.round(255f * .6f * SCRIM_STOPS[SCRIM_STOPS.length - 1 - i]) << 24;
      }
      bottomPaint.setShader(new LinearGradient(0, bottomStart, 0, bottomEnd, scrimColors, SCRIM_POSITIONS, Shader.TileMode.CLAMP));
    }
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);
    if (topAlpha <= 0f && bottomAlpha <= 0f) {
      return;
    }
    buildScrims();
    final int width = getWidth();
    if (topAlpha > 0f && topEnd > topStart) {
      topPaint.setAlpha(Math.round(255f * MathUtils.clamp(topAlpha)));
      c.drawRect(0, topStart, width, topEnd, topPaint);
    }
    if (bottomAlpha > 0f && bottomStart >= 0 && bottomEnd > bottomStart) {
      bottomPaint.setAlpha(Math.round(255f * MathUtils.clamp(bottomAlpha)));
      c.drawRect(0, bottomStart, width, bottomEnd, bottomPaint);
    }
  }

  // Release

  private void releaseSurfaces () {
    final boolean wasReconciling = reconciling;
    reconciling = true; // the faders settle at 0 below: nothing may bind again
    try {
      releaseSurfacesImpl();
    } finally {
      reconciling = wasReconciling;
      needReconcile = false;
    }
  }

  private void releaseSurfacesImpl () {
    final XgramCallVideo v = video;
    if (v != null && !v.isReleased()) {
      if (localStage != null) {
        v.backend().setTarget(XgramVideoBackend.FEED_LOCAL, localStage, false);
      }
      if (remoteStage != null) {
        v.backend().setTarget(XgramVideoBackend.FEED_REMOTE, remoteStage, false);
      }
      if (tileSurface != null && tileFeed != 0) {
        v.backend().setTarget(tileFeed, tileSurface, false);
      }
    }
    localBound = remoteBound = localReady = remoteReady = false;
    localAlpha.setValue(0f);
    remoteAlpha.setValue(0f);
    if (localStage != null) {
      removeView(localStage.view());
      localStage.release();
      localStage = null;
    }
    if (remoteStage != null) {
      removeView(remoteStage.view());
      remoteStage.release();
      remoteStage = null;
    }
    if (tileSurface != null) {
      if (tile != null) {
        tile.setTileShown(false, false);
        tile.setVideoSurface(null);
      }
      tileSurface.release();
      tileSurface = null;
    } else if (tile != null && tileFeed != 0) {
      tile.setTileShown(false, false);
    }
    tileFeed = 0;
    swapped = false;
    if (presentation != PRESENT_VOICE) {
      presentation = PRESENT_VOICE;
      listener.onPresentationChanged(PRESENT_VOICE, false);
    }
  }

  /** Drops the views and the video (the screen goes, or the call is replaced). */
  public void release () {
    releaseSurfaces();
    video = null;
  }
}
