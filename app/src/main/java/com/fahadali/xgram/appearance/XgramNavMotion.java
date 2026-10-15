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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Screen;

/**
 * Screen transitions of the navigation stack with Expressive design:
 * <ul>
 *   <li>Push / pop: Material shared axis X (96dp slide, fade through at .35) on SPATIAL_DEFAULT.</li>
 *   <li>System back gesture: the Android 16 predictive back card (closing screen shrinks to .9 with rounded corners and
 *   follows the finger's height, the previous screen waits at .9 behind a scrim; after commit the card slides away and
 *   fades while the previous screen springs to full size).</li>
 *   <li>Swipe back anywhere: the same card that follows the finger.</li>
 * </ul>
 * The closing card is the closing screen plus the live header (still showing it); the entering card is the previous
 * screen plus a snapshot of its header, drawn in its overlay. NavigationController feeds the raw factor and calls
 * {@link #end()} on every finish path.
 */
public final class XgramNavMotion {
  public static final int MODE_NONE = 0, MODE_SHARED_AXIS = 1, MODE_CARD_SYSTEM = 2, MODE_CARD_FINGER = 3;

  public static final float SHARED_AXIS_DP = 96f, MIN_SCALE = .9f, EDGE_GAP_DP = 8f, SHRINK_TRAVEL = .3f;
  public static final float FADE_THRESHOLD = .35f;
  /** Swipe back anywhere commits past a third of the width (the card is fully formed at 30% of travel). */
  public static final float FINGER_COMMIT = .33f;
  /** Release spring of back gestures (SPATIAL_DEFAULT). */
  public static final float RELEASE_K = 380f, RELEASE_Z = .8f;

  private static final Interpolator BACK = new PathInterpolator(.1f, .1f, 0f, 1f);
  private static final Interpolator DECELERATE = new DecelerateInterpolator();

  // Shared axis X numbers, also for the top app bar. p: 0..1 progress of the transition (raw, may overshoot).

  /** Alpha of the screen that leaves. */
  public static float outAlpha (float p) {
    return 1f - XgramMotion.clamp01(p / FADE_THRESHOLD);
  }

  /** Alpha of the screen that comes in. */
  public static float inAlpha (float p) {
    return XgramMotion.clamp01((p - FADE_THRESHOLD) / (1f - FADE_THRESHOLD));
  }

  /** translationX of the screen that leaves; e = +1 when new screens come from the right (LTR). */
  public static float outOffset (float p, boolean forward, int e, float distancePx) {
    return (forward ? -1f : 1f) * e * distancePx * p;
  }

  /** translationX of the screen that comes in. */
  public static float inOffset (float p, boolean forward, int e, float distancePx) {
    return (forward ? 1f : -1f) * e * distancePx * (1f - p);
  }

  public static float sharedAxisDistance () {
    return Screen.dpf(SHARED_AXIS_DP);
  }

  // State

  private static final class Saved {
    float alpha = 1f, scaleX = 1f, scaleY = 1f, translationX, translationY, pivotX, pivotY;
    boolean pivotSet, clipToOutline;
    ViewOutlineProvider outlineProvider;
  }

  private static final class CardOutline extends ViewOutlineProvider {
    final Rect rect = new Rect();
    float radius;

    @Override
    public void getOutline (View view, Outline outline) {
      outline.setRoundRect(rect, radius);
    }
  }

  /** Drawn over the entering screen: the snapshot of its header and the scrim. */
  private static final class EnteringOverlay extends Drawable {
    private final Paint scrimPaint = new Paint();
    private @Nullable Picture header;
    private float headerX, headerY;
    private float scrim;

    void setScrim (float scrim) {
      if (this.scrim != scrim) {
        this.scrim = scrim;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      if (header != null) {
        int save = c.save();
        c.translate(headerX, headerY);
        c.drawPicture(header);
        c.restoreToCount(save);
      }
      if (scrim > 0f) {
        scrimPaint.setColor(XgramExpressive.withAlpha(0xff000000, scrim));
        c.drawRect(getBounds(), scrimPaint);
      }
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  private final ViewGroup rootView, contentWrapper;
  private final HeaderView headerView;
  private final @Nullable View floatingButton;
  private final ColorDrawable backdrop;

  private boolean active, released, commit;
  private int mode = MODE_NONE;
  private boolean forward;
  private @Nullable View leftWrap, rightWrap;
  private float width, height, distance, edgeGap, radius, scrimMax;
  private int e = 1;
  private float touchStartY, touchY;
  private float p0, closingScale0, closingX0, closingY0, enteringX0, enteringY0, scrim0;
  private float rightTx, leftTx;
  private float fabAlpha = 1f;
  private boolean clipChildrenWas = true;

  private final Saved leftSaved = new Saved(), rightSaved = new Saved(), headerSaved = new Saved();
  private final CardOutline leftOutline = new CardOutline(), rightOutline = new CardOutline(), headerOutline = new CardOutline();
  private @Nullable EnteringOverlay overlay;

  public XgramNavMotion (@NonNull ViewGroup rootView, @NonNull ViewGroup contentWrapper, @NonNull HeaderView headerView, @Nullable View floatingButton) {
    this.rootView = rootView;
    this.contentWrapper = contentWrapper;
    this.headerView = headerView;
    this.floatingButton = floatingButton;
    this.backdrop = new ColorDrawable(0);
    if (contentWrapper.getBackground() == null) {
      // Transparent (draws nothing) until a transition shows the surface behind the screens.
      contentWrapper.setBackground(backdrop);
    }
  }

  public boolean isActive () {
    return active;
  }

  public boolean isCard () {
    return active && (mode == MODE_CARD_SYSTEM || mode == MODE_CARD_FINGER);
  }

  public boolean isReleased () {
    return released;
  }

  public int getMode () {
    return active ? mode : MODE_NONE;
  }

  /** translationX of the closing (right) screen, for onTranslationChanged and overlays that follow it. */
  public float getRightTx () {
    return rightTx;
  }

  public float getLeftTx () {
    return leftTx;
  }

  public void setTouchY (float y) {
    this.touchY = y;
  }

  /**
   * Starts a transition between {@code left} (the previous screen) and {@code right}. Returns false when this mode
   * cannot run here, so Telegram X's own transition runs. Call at the end of prepareFactorAnimation.
   */
  public boolean begin (int mode, @NonNull View left, @NonNull View right, boolean forward, boolean rtl, boolean fromRightEdge, float touchY) {
    if (active) {
      end();
    }
    if (mode == MODE_NONE || !XgramExpressive.isEnabled()) {
      return false;
    }
    float w = rootView.getMeasuredWidth(), h = rootView.getMeasuredHeight();
    if (w <= 0f || h <= 0f) {
      return false;
    }
    boolean card = mode == MODE_CARD_SYSTEM || mode == MODE_CARD_FINGER;
    if (card) {
      if (XgramMotion.reduce()) {
        return false;
      }
      if (containsSurfaceView(right) || containsSurfaceView(left)) {
        // A SurfaceView cannot be clipped to the card: slide and fade it with the gesture instead.
        mode = MODE_SHARED_AXIS;
        card = false;
      }
    }
    try {
      this.mode = mode;
      this.forward = forward;
      this.leftWrap = left;
      this.rightWrap = right;
      this.width = w;
      this.height = h;
      this.distance = Screen.dpf(SHARED_AXIS_DP);
      this.edgeGap = Screen.dpf(EDGE_GAP_DP);
      this.e = mode == MODE_CARD_SYSTEM ? (fromRightEdge ? -1 : 1) : (rtl ? -1 : 1);
      this.scrimMax = ThemeManager.instance().isCurrentThemeDark() ? .8f : .2f;
      this.touchStartY = this.touchY = touchY;
      this.released = false;
      this.commit = false;
      this.rightTx = this.leftTx = 0f;
      save(left, leftSaved);
      save(right, rightSaved);
      int surface = XgramExpressive.color(XgramExpressive.SURFACE) | 0xff000000;
      if (!card) {
        backdrop.setColor(surface);
      } else {
        backdrop.setColor(androidx.core.graphics.ColorUtils.compositeColors(XgramExpressive.withAlpha(0xff000000, scrimMax), surface));
        this.radius = cornerRadius();
        save(headerView, headerSaved);
        fabAlpha = floatingButton != null ? floatingButton.getAlpha() : 1f;

        EnteringOverlay overlay = new EnteringOverlay();
        int[] leftPos = positionInRoot(left), headerPos = positionInRoot(headerView);
        overlay.setBounds(-leftPos[0], -leftPos[1], Math.round(w) - leftPos[0], Math.round(h) - leftPos[1]);
        overlay.header = snapshotEnteringHeader();
        overlay.headerX = headerPos[0] - leftPos[0];
        overlay.headerY = headerPos[1] - leftPos[1];
        left.getOverlay().add(overlay);
        this.overlay = overlay;

        // The entering header snapshot sits above the entering screen's own bounds.
        clipChildrenWas = contentWrapper.getClipChildren();
        contentWrapper.setClipChildren(false);
        left.post(() -> {
          if (this.active && this.leftWrap == left) {
            left.setLayerType(View.LAYER_TYPE_NONE, null);
          }
        });

        prepareCard(left, leftOutline, radius);
        prepareCard(right, rightOutline, radius);
        prepareCard(headerView, headerOutline, radius);
      }
      this.active = true;
      if (card) {
        apply(0f);
      } else {
        apply(forward ? 1f : 0f);
      }
      return true;
    } catch (Throwable t) {
      Log.w(t);
      this.active = true;
      end();
      return false;
    }
  }

  /** Raw (unclamped) navigation factor: 0 = right screen in place, 1 = right screen gone. */
  public void apply (float factor) {
    if (!active) {
      return;
    }
    switch (mode) {
      case MODE_SHARED_AXIS:
        applySharedAxis(forward ? 1f - factor : factor);
        break;
      case MODE_CARD_SYSTEM:
        applySystemGesture(XgramMotion.clamp01(factor), touchY - touchStartY);
        break;
      case MODE_CARD_FINGER:
        applyFinger(factor);
        break;
    }
  }

  /** The finger is lifted (or the system committed / cancelled) at factor {@code p0}; {@link #applyRelease} follows. */
  public void release (boolean commit, float p0) {
    if (!active) {
      return;
    }
    this.released = true;
    this.commit = commit;
    this.p0 = XgramMotion.clamp01(p0);
    if (rightWrap != null && leftWrap != null) {
      closingScale0 = rightWrap.getScaleX();
      closingX0 = rightWrap.getTranslationX();
      closingY0 = rightWrap.getTranslationY();
      enteringX0 = leftWrap.getTranslationX();
      enteringY0 = leftWrap.getTranslationY();
    }
    scrim0 = overlay != null ? overlay.scrim : 0f;
  }

  /** c: 0..1 (spring, may overshoot) of the release animation. */
  public void applyRelease (float c) {
    if (!active || !released) {
      return;
    }
    float f = commit ? p0 + (1f - p0) * c : p0 * (1f - c);
    switch (mode) {
      case MODE_SHARED_AXIS:
        applySharedAxis(forward ? 1f - f : f);
        break;
      case MODE_CARD_FINGER:
        applyFinger(f);
        break;
      case MODE_CARD_SYSTEM:
        if (commit) {
          applySystemCommit(c);
        } else {
          applySystemGesture(XgramMotion.clamp01(f), (touchY - touchStartY) * XgramMotion.clamp01(1f - c));
        }
        break;
    }
  }

  // Geometry

  private void applySharedAxis (float p) {
    View in = forward ? rightWrap : leftWrap;
    View out = forward ? leftWrap : rightWrap;
    float inX = inOffset(p, forward, e, distance), outX = outOffset(p, forward, e, distance);
    if (in != null) {
      in.setTranslationX(inX);
      in.setAlpha(inAlpha(p));
    }
    if (out != null) {
      out.setTranslationX(outX);
      out.setAlpha(outAlpha(p));
    }
    rightTx = forward ? inX : outX;
    leftTx = forward ? outX : inX;
  }

  private void applySystemGesture (float p, float dy) {
    float back = BACK.getInterpolation(p);
    float closingScale = 1f - (1f - MIN_SCALE) * back;
    float closingX = e * back * Math.max(0f, .05f * width - edgeGap);
    float maxY = Math.max(0f, (1f - closingScale) * height / 2f - edgeGap);
    float closingY = Math.signum(dy) * maxY * DECELERATE.getInterpolation(Math.min(1f, Math.abs(dy) / (height / 2f)));
    setClosing(closingScale, closingX, closingY, 1f);
    setEntering(MIN_SCALE, closingX - e * distance, closingY, radius, scrimMax);
  }

  private void applySystemCommit (float c) {
    float cc = XgramMotion.clamp01(c);
    setClosing(closingScale0, closingX0 + e * distance * c, closingY0, 1f - XgramMotion.clamp01(c / .5f));
    setEntering(XgramMotion.lerp(MIN_SCALE, 1f, c), XgramMotion.lerp(enteringX0, 0f, c), XgramMotion.lerp(enteringY0, 0f, c), radius * (1f - cc), scrim0 * (1f - cc));
  }

  private void applyFinger (float f) {
    float fc = XgramMotion.clamp01(f);
    float closingScale = 1f - (1f - MIN_SCALE) * BACK.getInterpolation(XgramMotion.clamp01(f / SHRINK_TRAVEL));
    float closingX = e * Math.max(f, -.02f) * width;
    setClosing(closingScale, closingX, 0f, 1f);
    setEntering(XgramMotion.lerp(MIN_SCALE, 1f, fc), -e * distance * (1f - f), 0f, radius * (1f - fc), scrimMax * (1f - fc));
  }

  private void setClosing (float scale, float x, float y, float alpha) {
    if (rightWrap != null) {
      transform(rightWrap, scale, x, y);
      rightWrap.setAlpha(rightSaved.alpha * alpha);
    }
    transform(headerView, scale, x, y);
    headerView.setAlpha(headerSaved.alpha * alpha);
    if (floatingButton != null) {
      floatingButton.setAlpha(fabAlpha * alpha * (1f - XgramMotion.clamp01((1f - scale) / ((1f - MIN_SCALE) / 2f))));
    }
    rightTx = x;
  }

  private void setEntering (float scale, float x, float y, float cornerRadius, float scrim) {
    if (leftWrap != null) {
      transform(leftWrap, scale, x, y);
      if (leftOutline.radius != cornerRadius) {
        leftOutline.radius = cornerRadius;
        leftWrap.invalidateOutline();
      }
    }
    if (overlay != null) {
      overlay.setScrim(scrim);
    }
    leftTx = x;
  }

  private static void transform (View v, float scale, float x, float y) {
    v.setScaleX(scale);
    v.setScaleY(scale);
    v.setTranslationX(x);
    v.setTranslationY(y);
  }

  // Setup and cleanup

  private int[] positionInRoot (View v) {
    int x = 0, y = 0;
    View cur = v;
    while (cur != null && cur != rootView) {
      x += cur.getLeft();
      y += cur.getTop();
      ViewParent parent = cur.getParent();
      cur = parent instanceof View ? (View) parent : null;
    }
    return new int[] {x, y};
  }

  /** Card pivot at the screen centre and an outline of the whole screen, so every part transforms as one card. */
  private void prepareCard (View v, CardOutline outline, float cornerRadius) {
    int[] pos = positionInRoot(v);
    v.setPivotX(width / 2f - pos[0]);
    v.setPivotY(height / 2f - pos[1]);
    outline.rect.set(-pos[0], -pos[1], Math.round(width) - pos[0], Math.round(height) - pos[1]);
    outline.radius = cornerRadius;
    v.setOutlineProvider(outline);
    v.setClipToOutline(true);
    v.invalidateOutline();
  }

  @Nullable
  private Picture snapshotEnteringHeader () {
    int w = headerView.getWidth(), h = headerView.getHeight();
    if (w <= 0 || h <= 0) {
      return null;
    }
    float translation = headerView.getTranslation();
    try {
      headerView.setTranslation(1f);
      Picture picture = new Picture();
      Canvas c = picture.beginRecording(w, h);
      headerView.draw(c);
      picture.endRecording();
      return picture;
    } catch (Throwable t) {
      Log.w(t);
      return null;
    } finally {
      headerView.setTranslation(translation);
    }
  }

  private float cornerRadius () {
    float fallback = Screen.dpf(XgramExpressive.CORNER_XL);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      try {
        WindowInsets insets = rootView.getRootWindowInsets();
        View window = rootView.getRootView();
        int[] location = new int[2];
        rootView.getLocationInWindow(location);
        if (insets != null && window != null && location[0] == 0 && rootView.getWidth() >= window.getWidth()) {
          RoundedCorner corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
          if (corner != null && corner.getRadius() > 0) {
            return corner.getRadius();
          }
        }
      } catch (Throwable ignored) { }
    }
    return fallback;
  }

  private static boolean containsSurfaceView (View v) {
    if (v instanceof SurfaceView) {
      return v.getVisibility() == View.VISIBLE;
    }
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        if (containsSurfaceView(group.getChildAt(i))) {
          return true;
        }
      }
    }
    return false;
  }

  private static void save (View v, Saved s) {
    s.alpha = v.getAlpha();
    s.scaleX = v.getScaleX();
    s.scaleY = v.getScaleY();
    s.translationX = v.getTranslationX();
    s.translationY = v.getTranslationY();
    s.pivotSet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && v.isPivotSet();
    s.pivotX = v.getPivotX();
    s.pivotY = v.getPivotY();
    s.outlineProvider = v.getOutlineProvider();
    s.clipToOutline = v.getClipToOutline();
  }

  private static void restore (View v, Saved s, boolean card, boolean keepTranslation) {
    v.setAlpha(s.alpha);
    v.setScaleX(s.scaleX);
    v.setScaleY(s.scaleY);
    if (keepTranslation) {
      v.setTranslationX(s.translationX);
      v.setTranslationY(s.translationY);
    } else {
      v.setTranslationX(0f);
      v.setTranslationY(0f);
    }
    if (card) {
      if (s.pivotSet) {
        v.setPivotX(s.pivotX);
        v.setPivotY(s.pivotY);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        v.resetPivot();
      }
      v.setOutlineProvider(s.outlineProvider);
      v.setClipToOutline(s.clipToOutline);
      v.invalidateOutline();
    }
  }

  /** Puts every view back (screens at rest, translation 0). Safe to call on every finish path. */
  public void end () {
    if (!active) {
      return;
    }
    active = false;
    boolean card = mode == MODE_CARD_SYSTEM || mode == MODE_CARD_FINGER;
    try {
      if (leftWrap != null) {
        if (overlay != null) {
          leftWrap.getOverlay().remove(overlay);
        }
        restore(leftWrap, leftSaved, card, false);
      }
      if (rightWrap != null) {
        restore(rightWrap, rightSaved, card, false);
      }
      if (card) {
        restore(headerView, headerSaved, true, true);
        if (floatingButton != null) {
          floatingButton.setAlpha(fabAlpha);
        }
        if (contentWrapper.getClipChildren() != clipChildrenWas) {
          contentWrapper.setClipChildren(clipChildrenWas);
        }
      }
    } catch (Throwable t) {
      Log.w(t);
    }
    backdrop.setColor(0);
    overlay = null;
    leftWrap = rightWrap = null;
    released = false;
    commit = false;
    mode = MODE_NONE;
    rightTx = leftTx = 0f;
  }
}
