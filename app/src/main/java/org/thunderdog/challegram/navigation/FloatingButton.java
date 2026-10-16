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
 * File created on 17/08/2015 at 02:49
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.SimpleShapeDrawable;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class FloatingButton extends View implements Destroyable, RootFrameLayout.InsetsChangeListener {
  private float center;
  private float heightDiff;

  private boolean isShowing;

  private static final float MIN_SCALE = .4f;

  // XGram: with Expressive design this is the Material 3 FAB. Values are the Compose material3 tokens:
  // FabBaselineTokens (56dp container, CornerLarge 16dp, 24dp icon), FabPrimaryContainerTokens (PrimaryContainer
  // container, OnPrimaryContainer icon, Level3 6dp elevation at rest and pressed), the ButtonMedium (56dp) press
  // morph (CornerLarge 16dp -> PressedContainerShape CornerMedium 12dp on FastSpatial) with an OnPrimaryContainer
  // state layer at .10, and Modifier.animateFloatingActionButton for show / hide (scale from
  // FloatingActionButtonDefaults.ShowHideTargetScale .2 on the FastSpatial spring, alpha on FastEffects).
  // With Expressive design off nothing here changes Telegram X.
  private final boolean xgram;
  private static final float XGRAM_HIDDEN_SCALE = .2f;
  private static final float XGRAM_SIZE_DP = 56f;
  private @Nullable Paint xgramIconPaint;
  private int xgramIconColor;
  private @Nullable XgramExpressive.Spring xgramScaleSpring, xgramAlphaSpring;

  public FloatingButton (Context context) {
    super(context);
    this.xgram = XgramExpressive.isEnabled();

    heightDiff = -Size.getMaximumHeaderSizeDifference();
    if (xgram) {
      xgramInitBackground();
    } else {
      RippleSupport.setCircleBackground(this, 56f, 4f, ColorId.headerButton, true, null);
    }

    int padding = Screen.dp(4f);
    int size = Screen.dp(56f);
    center = (float) size * .5f + padding;

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(size + padding * 2, size + padding * 2, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
    params.topMargin = HeaderView.getBigSize(true) - Screen.dp(30f) - padding;
    params.rightMargin = params.leftMargin = Screen.dp(16f) - padding;

    setLayoutParams(params);

    isShowing = true;

    setTranslationY(heightDiff);
    setAlpha(0f);
    setScaleX(minScale());
    setScaleY(minScale());
  }

  private float minScale () {
    return xgram ? XGRAM_HIDDEN_SCALE : MIN_SCALE;
  }

  /** XGram: a 56dp rounded square (16dp, 12dp while pressed) in primaryContainer, with a Level3 shadow from its outline. */
  private void xgramInitBackground () {
    XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(this, XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), XgramExpressive.CORNER_L, XgramExpressive.CORNER_M);
    morph.setColorRole(XgramExpressive.PRIMARY_CONTAINER); // follows theme changes
    morph.setSize(XGRAM_SIZE_DP, XGRAM_SIZE_DP, Gravity.CENTER); // the view keeps Telegram X's 4dp shadow padding
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setStateListAnimator(null); // Level3 at rest and pressed: no elevation change on press
      setTranslationZ(0f);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    }
    xgramIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  }

  private Paint getIconPaint () {
    if (!xgram || xgramIconPaint == null) {
      return Paints.getHeaderFloatIconPorterDuffPaint();
    }
    int color = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
    if (xgramIconPaint.getColorFilter() == null || xgramIconColor != color) {
      // Only when the theme changes
      xgramIconColor = color;
      xgramIconPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }
    return xgramIconPaint;
  }

  private RootFrameLayout rootView;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    rootView = Views.findAncestor(this, RootFrameLayout.class, true);
    if (rootView != null) {
      rootView.addInsetsChangeListener(this);
      applyTopInset(rootView.getTopInset());
    }
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) {
    applyTopInset(effectiveInsets.top);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    performDestroy();
  }

  private void applyTopInset (int topInset) {
    Views.setTopMargin(this, HeaderView.getBigSize(false) + topInset - Screen.dp(30f) - Screen.dp(4f));
  }

  @Override
  public void performDestroy () {
    if (rootView != null) {
      rootView.removeInsetsChangeListener(this);
      rootView = null;
    }
    xgramCancelSprings();
  }

  private Drawable leftIcon, rightIcon;
  private int leftIconRes, rightIconRes;
  private float leftIconTop, rightIconTop;
  private Drawable icon;
  private int iconRes;
  private float iconTop;

  public void setIcons (int leftIcon, int rightIcon) {
    if (leftIcon != 0 && rightIcon != 0 && leftIcon != rightIcon) {
      this.icon = null;
      this.iconTop = 0;
      this.iconRes = 0;

      this.leftIcon = Drawables.get(getResources(), leftIcon);
      this.leftIconRes = leftIcon;
      this.leftIconTop = getIconTop(leftIcon, this.leftIcon);

      this.rightIcon = Drawables.get(getResources(), rightIcon);
      this.rightIconRes = rightIcon;
      this.rightIconTop = getIconTop(rightIcon, this.rightIcon);

      setFactor(0f);

      return;
    }

    this.leftIcon = null;
    this.leftIconRes = 0;
    this.rightIcon = null;
    this.rightIconRes = 0;

    if (leftIcon != 0) {
      this.icon = Drawables.get(getResources(), leftIcon);
      this.iconRes = leftIcon;
      this.iconTop = getIconTop(leftIcon, icon);
    } else {
      this.icon = Drawables.get(getResources(), rightIcon);
      this.iconRes = rightIcon;
      this.iconTop = getIconTop(rightIcon, icon);
    }

    invalidate();
  }

  private float factor;

  public void setFactor (float factor) { // Switch factor. 0f - showing left icon, 1f - right
    if (this.factor != factor && leftIcon != null && rightIcon != null) {
      this.factor = factor;
      invalidate();
    }
  }

  private float heightFactor = -1f, scaleFactor = -1f;
  private float fromHeightFactor;
  private float toHeightFactor;
  private boolean translateFully;

  private Animator.AnimatorListener hideListener;

  private boolean touchEnabled;

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return touchEnabled && super.onTouchEvent(event);
  }

  public void setTouchEnabled (boolean enabled) {
    if (touchEnabled != enabled) {
      touchEnabled = enabled;
      setEnabled(enabled);
      if (Views.HARDWARE_LAYER_ENABLED) {
        Views.setLayerType(this, enabled ? (SimpleShapeDrawable.USE_SOFTWARE_SHADOW ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE) : LAYER_TYPE_NONE);
      }
    }
  }

  private static final long OVERSHOOT_DURATION = 380l;

  public void initHeightFactor (float heightFactor, float heightFactor2, float scaleFactor, boolean animate, boolean translation) {
    fromHeightFactor = heightFactor;
    toHeightFactor = heightFactor2;
    translateFully = translation;
    if (toHeightFactor == fromHeightFactor) {
      setTranslationY((1f - heightFactor) * heightDiff);
    } else {
      setHeightFactor(heightFactor, scaleFactor, animate);
    }
  }

  @SuppressWarnings ("SuspiciousNameCombination")
  public void setHeightFactor (float heightFactor, float scaleFactor, boolean animate) {
    if (this.heightFactor != heightFactor || this.scaleFactor != scaleFactor) {
      this.heightFactor = heightFactor;
      this.scaleFactor = scaleFactor;
      if (translateFully) {
        setTranslationY((1f - heightFactor) * heightDiff - Size.getHeaderPortraitSize() * fromHeightFactor * (1f - heightFactor));
      } else if (animate || fromHeightFactor != toHeightFactor) {
        setTranslationY((1f - heightFactor) * heightDiff);
      }
      if (animate) {
        if (heightFactor == 0f) {
          if (isShowing) {
            isShowing = false;
            if (xgram) {
              xgramHide();
            } else {
              Views.animate(this, 0f, 0f, 0f, OVERSHOOT_DURATION, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, getHideListener());
            }
          }
        } else {
          if (!isShowing) {
            isShowing = true;
            setTouchEnabled(true);
            if (xgram) {
              xgramAnimateVisible(true);
            } else {
              Views.animate(this, 1f, 1f, 1f, OVERSHOOT_DURATION, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, null);
            }
          }
        }
      } else {
        /*float origH = heightFactor;

        if (fromHeightFactor != toHeightFactor) {
          if (fromHeightFactor != 0f) {
            heightFactor = Math.min(heightFactor / fromHeightFactor, 1f);
          } else if (toHeightFactor != 0f) {
            heightFactor = heightFactor / toHeightFactor;
          }
        }

        if (origH == heightFactor) {
          Logger.v("%f", heightFactor);
        } else {
          Logger.w("%f %f", origH, heightFactor);
        }*/

        if (xgram) {
          xgramCancelSprings(); // the transition owns the button now
        }
        float realAlpha = MathUtils.clamp(scaleFactor);
        setAlpha(realAlpha);
        float scale = minScale() + (1f - minScale()) * scaleFactor;
        setScaleX(scale);
        setScaleY(scale);
        setTouchEnabled(isShowing = realAlpha != 0f);
      }
    }
  }

  /**
   * HeaderView: true when the button of the screen that leaves is already hidden (its header collapsed under it, or
   * {@link #hide()}), so a transition must not bring it back.
   */
  boolean isHiddenForSwitch () {
    if (xgram) {
      return !isShowing;
    }
    return getScaleX() == 0 && getScaleY() == 0;
  }

  private float getIconTop (int resource, Drawable icon) {
    if (resource == 0 || icon == null) {
      return 0;
    }
    if (resource == R.drawable.baseline_chat_bubble_24) {
      return Screen.dp(17f) + Screen.dp(4f);
      /*case R.drawable.ic_camera_gray: {
        return Screen.dp(19f) + Screen.dp(4f);
      }*/
    }
    return center - icon.getMinimumHeight() / 2f;
  }

  private static void draw (Canvas c, int res, Drawable d, float cx, float top, Paint paint, float alpha) {
    if (d != null) {
      boolean needReverse = Drawables.needMirror(res);
      if (needReverse) {
        c.save();
        c.scale(-1f, 1f, cx, top + d.getMinimumHeight() / 2);
      }
      int savedAlpha = paint.getAlpha();
      paint.setAlpha((int) (255f * alpha));
      Drawables.draw(c, d, cx - d.getMinimumWidth() / 2, top, paint);
      paint.setAlpha(savedAlpha);
      if (needReverse) {
        c.restore();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (icon != null) {
      draw(c, iconRes, icon, center, iconTop, getIconPaint(), 1f);
    } else if (leftIcon != null || rightIcon != null) {
      Paint paint = getIconPaint();
      if (leftIcon != null)
        draw(c, leftIconRes, leftIcon, center, leftIconTop, paint, 1f - MathUtils.clamp(factor));
      if (rightIcon != null)
        draw(c, rightIconRes, rightIcon, center, rightIconTop, paint, MathUtils.clamp(factor));
    }
  }

  private Animator.AnimatorListener getHideListener () {
    if (hideListener == null) {
      hideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          if (!isShowing) {
            setTouchEnabled(false);
          }
        }
      };
    }
    return hideListener;
  }

  // show/hide for EditHeaderView

  private float showFactor;

  public float getShowFactor () {
    return showFactor;
  }

  public void setShowFactor (float factor) {
    if (this.showFactor != factor) {
      this.showFactor = factor;
      setAlpha(MathUtils.clamp(factor));
      final float scale = minScale() + (1f - minScale()) * factor;
      setScaleX(scale);
      setScaleY(scale);
    }
  }

  public void updatePosition (int height) {
    float heightFactor = (float) (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();
    setTranslationY((1f - heightFactor) * heightDiff);
  }

  public void show (ViewController<?> c) {
    int icon = c.getFloatingButtonId();
    int height = c.getHeaderHeight();
    float heightFactor = (float) (height - Size.getHeaderPortraitSize()) / (float) Size.getMaximumHeaderSizeDifference();

    isShowing = true;

    setTouchEnabled(true);
    setIcons(0, icon);
    setTranslationY((1f - heightFactor) * heightDiff);

    if (xgram) {
      showFactor = 1f;
      xgramAnimateVisible(true);
      return;
    }

    showFactor = 0f;

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setShowFactor(AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR);
    obj.setDuration(OVERSHOOT_DURATION);

    obj.start();
  }

  public void hide () {
    if (isShowing) {
      isShowing = false;

      if (xgram) {
        showFactor = 0f;
        xgramHide();
        return;
      }

      showFactor = 1f;

      ValueAnimator obj;

      obj = AnimatorUtils.simpleValueAnimator();
      obj.addUpdateListener(animation -> setShowFactor(1f - AnimatorUtils.getFraction(animation)));
      obj.setInterpolator(AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR);
      obj.setDuration(OVERSHOOT_DURATION);
      obj.addListener(getHideListener());
      obj.start();
    }
  }

  // XGram: show / hide springs (Expressive design only)

  private void xgramEnsureSprings () {
    if (xgramScaleSpring == null || xgramAlphaSpring == null) {
      xgramScaleSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> xgramApplyScale(value));
      xgramAlphaSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> setAlpha(MathUtils.clamp(value)));
      xgramAlphaSpring.setEndListener(() -> {
        if (!isShowing) {
          setTouchEnabled(false);
        }
      });
    }
  }

  /** {@code visible} 0: hidden (ShowHideTargetScale), 1: full size. May overshoot (FastSpatial). */
  private void xgramApplyScale (float visible) {
    float scale = XGRAM_HIDDEN_SCALE + (1f - XGRAM_HIDDEN_SCALE) * visible;
    setScaleX(scale);
    setScaleY(scale);
  }

  private boolean xgramSpringsRunning () {
    return (xgramScaleSpring != null && xgramScaleSpring.isRunning()) || (xgramAlphaSpring != null && xgramAlphaSpring.isRunning());
  }

  /** Springs in or out from where the button is now; a running spring keeps its velocity. */
  private void xgramAnimateVisible (boolean visible) {
    xgramEnsureSprings();
    if (!xgramScaleSpring.isRunning()) {
      xgramScaleSpring.setValue(MathUtils.clamp((getScaleX() - XGRAM_HIDDEN_SCALE) / (1f - XGRAM_HIDDEN_SCALE)));
    }
    if (!xgramAlphaSpring.isRunning()) {
      xgramAlphaSpring.setValue(MathUtils.clamp(getAlpha()));
    }
    float target = visible ? 1f : 0f;
    xgramScaleSpring.animateTo(target);
    xgramAlphaSpring.animateTo(target);
  }

  private void xgramHide () {
    if (getAlpha() == 0f && !xgramSpringsRunning()) {
      // Already invisible: nothing to animate (a settled spring would not report its end)
      xgramApplyScale(0f);
      setTouchEnabled(false);
      return;
    }
    xgramAnimateVisible(false);
  }

  private void xgramCancelSprings () {
    if (xgramScaleSpring != null) {
      xgramScaleSpring.cancel();
    }
    if (xgramAlphaSpring != null) {
      xgramAlphaSpring.cancel();
    }
  }

  /** Jumps a running show / hide to its end. */
  private void xgramFinishSprings () {
    if (xgramScaleSpring == null || xgramAlphaSpring == null || !xgramSpringsRunning()) {
      return;
    }
    float scaleTarget = xgramScaleSpring.getTarget(), alphaTarget = xgramAlphaSpring.getTarget();
    xgramScaleSpring.setValue(scaleTarget);
    xgramAlphaSpring.setValue(alphaTarget);
    xgramApplyScale(scaleTarget);
    setAlpha(MathUtils.clamp(alphaTarget));
    if (!isShowing) {
      setTouchEnabled(false);
    }
  }

  /**
   * XGram: hides the button when the screen that is now current has none (NavigationController and HeaderView call
   * this after a navigation or a header reset with Expressive design on). No-op while a back card holds it.
   */
  public void xgramHideIfShown () {
    if (cardState != CARD_NONE) {
      return;
    }
    isShowing = false;
    if (getAlpha() == 0f && !xgramSpringsRunning()) {
      setTouchEnabled(false);
      return;
    }
    if (xgram) {
      xgramAnimateVisible(false);
    } else {
      setAlpha(0f);
      setScaleX(minScale());
      setScaleY(minScale());
      setTouchEnabled(false);
    }
  }

  // XGram: predictive back card (XgramNavMotion, Expressive design). The button belongs to the closing screen: while
  // the card follows the gesture the button keeps its look and moves, shrinks and fades with the card. When the gesture
  // commits it takes the state the app bar gave it for the previous screen (hidden when that screen has no button,
  // springing in when it has one); when it is cancelled it goes back exactly as it was.

  private static final int CARD_NONE = 0, CARD_CAPTURED = 1, CARD_HOLDING = 2;
  private static final int S_ALPHA = 0, S_SCALE_X = 1, S_SCALE_Y = 2, S_TX = 3, S_TY = 4, S_FACTOR = 5, S_HEIGHT = 6, S_SCALE_FACTOR = 7, S_COUNT = 8;

  private int cardState = CARD_NONE;
  private final float[] cardFrom = new float[S_COUNT], cardTo = new float[S_COUNT];
  private boolean cardFromShowing, cardFromTouch, cardToShowing, cardToTouch;

  private void captureState (float[] out) {
    out[S_ALPHA] = getAlpha();
    out[S_SCALE_X] = getScaleX();
    out[S_SCALE_Y] = getScaleY();
    out[S_TX] = getTranslationX();
    out[S_TY] = getTranslationY();
    out[S_FACTOR] = factor;
    out[S_HEIGHT] = heightFactor;
    out[S_SCALE_FACTOR] = scaleFactor;
  }

  private void restoreState (float[] in) {
    setAlpha(in[S_ALPHA]);
    setScaleX(in[S_SCALE_X]);
    setScaleY(in[S_SCALE_Y]);
    setTranslationX(in[S_TX]);
    setTranslationY(in[S_TY]);
    if (factor != in[S_FACTOR]) {
      factor = in[S_FACTOR];
      invalidate();
    }
    heightFactor = in[S_HEIGHT];
    scaleFactor = in[S_SCALE_FACTOR];
  }

  /** Before the app bar moves to the previous screen: remembers the closing screen's button. */
  public void xgramCardBegin () {
    xgramFinishSprings();
    captureState(cardFrom);
    cardFromShowing = isShowing;
    cardFromTouch = touchEnabled;
    cardState = CARD_CAPTURED;
  }

  /** After the app bar moved to the previous screen: keeps that state for the end and puts the closing screen's button back. */
  public void xgramCardHold () {
    if (cardState != CARD_CAPTURED) {
      return;
    }
    xgramFinishSprings(); // both screens with a button: the app bar may have started a show / hide
    captureState(cardTo);
    cardToShowing = isShowing;
    cardToTouch = touchEnabled;
    restoreState(cardFrom);
    isShowing = cardFromShowing;
    setTouchEnabled(false); // no taps on a closing screen
    cardState = CARD_HOLDING;
  }

  /**
   * The closing card is scaled by {@code scale} around ({@code pivotX}, {@code pivotY}) (parent coordinates), moved
   * by ({@code dx}, {@code dy}) and drawn at {@code alpha}: the button does the same.
   */
  public void xgramCardTransform (float pivotX, float pivotY, float scale, float dx, float dy, float alpha) {
    if (cardState != CARD_HOLDING) {
      return;
    }
    float tx = cardFrom[S_TX], ty = cardFrom[S_TY];
    float px = getLeft() + getPivotX() + tx, py = getTop() + getPivotY() + ty;
    float nx = pivotX + (px - pivotX) * scale + dx, ny = pivotY + (py - pivotY) * scale + dy;
    setTranslationX(tx + (nx - px));
    setTranslationY(ty + (ny - py));
    setScaleX(cardFrom[S_SCALE_X] * scale);
    setScaleY(cardFrom[S_SCALE_Y] * scale);
    setAlpha(MathUtils.clamp(cardFrom[S_ALPHA] * alpha));
  }

  /** {@code navigated}: the previous screen became current. Safe to call on every finish path. */
  public void xgramCardEnd (boolean navigated) {
    int state = cardState;
    cardState = CARD_NONE;
    if (state == CARD_NONE) {
      return;
    }
    if (navigated && state == CARD_HOLDING) {
      restoreState(cardTo);
      isShowing = cardToShowing;
      if (xgram && cardToShowing && cardTo[S_ALPHA] > 0f) {
        // The previous screen has its own button: it comes in where its app bar puts it.
        setTouchEnabled(true);
        xgramCancelSprings();
        setAlpha(0f);
        xgramApplyScale(0f);
        xgramAnimateVisible(true);
      } else {
        setTouchEnabled(cardToTouch);
      }
    } else {
      restoreState(cardFrom);
      isShowing = cardFromShowing;
      setTouchEnabled(cardFromTouch);
    }
  }
}
