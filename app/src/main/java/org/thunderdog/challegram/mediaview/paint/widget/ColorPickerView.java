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
 * File created on 11/04/2017
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class ColorPickerView extends View implements FactorAnimator.Target {
  private final Paint colorPaint;

  private ColorPreviewView preview;
  private ColorToneView tone;
  private ShadowView toneShadow;
  private ColorDirectionView direction;

  public ColorPickerView (Context context) {
    super(context);
    this.colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  }

  public void setPreview (ColorPreviewView preview) {
    this.preview = preview;
  }

  public void setTone (ColorToneView tone, ShadowView shadow) {
    this.tone = tone;
    this.toneShadow = shadow;
  }

  public void setDirection (ColorDirectionView direction) {
    this.direction = direction;
  }

  private int prevWidth, prevHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    int height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    if (prevWidth != width || prevHeight != height) {
      prevWidth = width;
      prevHeight = height;
      colorPaint.setShader(new LinearGradient(height / 2, 0, width, height / 2, DrawAlgorithms.COLOR_PICKER_COLORS_NICE, null, Shader.TileMode.MIRROR));
    }
    if (preview != null) {
      preview.setTargetWidth(width);
    }
    if (direction != null) {
      direction.setPickerWidth(getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (direction != null) {
      direction.setPickerLeft(left + getPaddingLeft());
    }
  }

  private boolean caught;
  private BoolAnimator caughtAnimator;
  private static final int ANIMATOR_CAUGHT = 0;

  private float caughtFactor;

  private static final float MIN_Y = 72f;
  private static final float MAX_Y = 216f;

  private boolean canForceApply;

  private void setCaught (final boolean isCaught) {
    if (this.caught != isCaught) {
      this.caught = isCaught;
      cancelLongTap();
      if (isCaught) {
        canForceApply = true;
        setBaseY(-Screen.dp(MIN_Y), false);
        cancelTapMovement();
      } else {
        setInLongTap(false);
        setPickingTone(false);
      }
      if (caughtAnimator == null) {
        caughtAnimator = new BoolAnimator(ANIMATOR_CAUGHT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      // XGram: the swatch pops up over the handle on the fast spatial spring and settles back without overshoot
      com.fahadali.xgram.appearance.XgramAppMotion.use(caughtAnimator, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, !isCaught);
      caughtAnimator.setValue(isCaught, true);
    }
  }

  public ColorPreviewView getPreview () {
    return preview;
  }

  private float baseY = -Screen.dp(MIN_Y);

  private void setBaseY (float y, boolean applySize) {
    y = Math.max(-Screen.dp(MAX_Y), Math.min(y, -Screen.dp(MIN_Y)));
    if (baseY != y) {
      baseY = y;
      preview.setBaseY(y * caughtFactor);
      if (applySize) {
        float factor = (y + Screen.dp(MIN_Y)) / (float) -(Screen.dp(MAX_Y) - Screen.dp(MIN_Y));
        preview.setRadiusFactor(factor);
        canForceApply = false;
      }
    }
  }

  private void setCaught (float factor) {
    if (this.caughtFactor != factor) {
      this.caughtFactor = factor;
      if (canForceApply) {
        preview.setForceRadiusFactor(factor, false);
      }
      preview.setBaseY(baseY * factor);
      preview.setScaleFactor(factor);
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        invalidate(); // XGram: the handle narrows while it is held
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CAUGHT:
        setCaught(factor);
        break;
      case ANIMATOR_TONE:
        setToneFactor(factor);
        break;
      case ANIMATOR_PICK:
        setDesireFactor(factor);
        break;
      case ANIMATOR_TAP:
        setTapFactor(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private boolean movingHorizontally, movingVertically, listenTap;
  private float downX, downY;
  private float startHue;

  private CancellableRunnable longTap;

  private void scheduleLongTap () {
    cancelLongTap();
    if (tone == null) {
      return;
    }
    longTap = new CancellableRunnable() {
      @Override
      public void act () {
        if (longTap == this) {
          onLongTap();
        }
      }
    };
    postDelayed(longTap, ViewConfiguration.getLongPressTimeout() * 2);
  }

  private void cancelLongTap () {
    if (longTap != null) {
      longTap.cancel();
      longTap = null;
    }
  }

  private boolean inLongTap;
  private BoolAnimator longTapAnimator;
  private static final int ANIMATOR_TONE = 1;

  private void onLongTap () {
    if (!caught || movingHorizontally || movingVertically) {
      return;
    }

    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    setInLongTap(true);
  }

  private final float[] startHsv = new float[3];
  private final float[] tempHsv = new float[3];

  private float desireFactor;
  private float desiredS, desiredV;

  private void updateColor () {
    tempHsv[0] = startHsv[0];
    tempHsv[1] = startHsv[1] + (desiredS - startHsv[1]) * desireFactor;
    tempHsv[2] = startHsv[2] + (desiredV - startHsv[2]) * desireFactor;
    isCustomTone = true;
    preview.setColor(tempHsv[0], tempHsv[1], tempHsv[2]);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && tone != null) {
      tone.xgramSetThumb(preview.getBrushColor(), tempHsv[1], tempHsv[2]); // XGram: the tone card's ring thumb
    }
  }

  private void setDesiredPositions (float desiredS, float desiredV) {
    if (this.desiredS != desiredS || this.desiredV != desiredV) {
      this.desiredS = desiredS;
      this.desiredV = desiredV;
      if (desireFactor > 0f) {
        updateColor();
      }
    }
  }

  private void setDesireFactor (float desireFactor) {
    if (this.desireFactor != desireFactor) {
      this.desireFactor = desireFactor;
      updateColor();
    }
  }

  private boolean pickingTone;
  private BoolAnimator pickingAnimator;
  private static final int ANIMATOR_PICK = 3;

  private void setPickingTone (boolean picking) {
    if (this.pickingTone != picking) {
      this.pickingTone = picking;
      if (pickingAnimator == null) {
        pickingAnimator = new BoolAnimator(ANIMATOR_PICK, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        com.fahadali.xgram.appearance.XgramAppMotion.use(pickingAnimator, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, true); // XGram: a colour blend
      }
      if (picking) {
        pickingAnimator.setValue(true, true);
      } else {
        desiredV = startHsv[2] = tempHsv[2];
        desiredS = startHsv[1] = tempHsv[1];
        pickingAnimator.setValue(false, false);
      }
      if (toneEventListener != null) {
        toneEventListener.onTonePicking(this, picking);
      }
    }
  }

  public interface ToneEventListener {
    void onLongTapStateChanged (ColorPickerView v, boolean inLongTap);
    void onTonePicking (ColorPickerView v, boolean pickingTone);
  }

  private ToneEventListener toneEventListener;

  public void setToneEventListener (ToneEventListener toneEventListener) {
    this.toneEventListener = toneEventListener;
  }

  public boolean isPickingTone () {
    return pickingTone;
  }

  public boolean isInLongTap () {
    return inLongTap;
  }

  private void setInLongTap (boolean inLongTap) {
    if (this.inLongTap != inLongTap) {
      this.inLongTap = inLongTap;
      if (inLongTap) {
        tone.setHue(preview.getHue());
        preview.getHsv(startHsv);
        preview.getHsv(tempHsv);
        desiredS = tempHsv[1];
        desiredV = tempHsv[2];
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          tone.xgramSetThumb(preview.getBrushColor(), tempHsv[1], tempHsv[2]); // XGram: the ring thumb starts at the current tone
        }
      }
      if (longTapAnimator == null) {
        longTapAnimator = new BoolAnimator(ANIMATOR_TONE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      // XGram: the tone card rises on the default spatial spring and leaves without overshoot
      com.fahadali.xgram.appearance.XgramAppMotion.use(longTapAnimator, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, !inLongTap);
      longTapAnimator.setValue(inLongTap, true);
      if (this.toneEventListener != null) {
        this.toneEventListener.onLongTapStateChanged(this, inLongTap);
      }
    }
  }

  private float toneFactor;

  private void setToneFactor (float factor) {
    if (this.toneFactor != factor) {
      this.toneFactor = factor;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram: the floating tone card fades in and grows from 92% at its bottom edge; no shadow view
        final float alpha = MathUtils.clamp(factor);
        tone.setAlpha(alpha);
        final float scale = .92f + .08f * Math.max(0f, factor);
        tone.setPivotX(tone.getWidth() / 2f);
        tone.setPivotY(tone.getHeight());
        tone.setScaleX(scale);
        tone.setScaleY(scale);
        if (toneShadow != null) {
          toneShadow.setAlpha(alpha);
        }
        preview.setInToneFactor(factor);
        direction.setFactor(alpha);
        return;
      }
      tone.setAlpha(factor);
      toneShadow.setAlpha(factor);
      preview.setInToneFactor(factor);
      direction.setFactor(factor);
    }
  }

  private FactorAnimator tapAnimator;
  private static final int ANIMATOR_TAP = 4;
  private final float[] tapColor = new float[3];

  private void cancelTapMovement () {
    if (tapAnimator != null) {
      tapAnimator.cancel();
    }
  }

  private boolean isCustomTone;

  private void setColorFactor (float factor) {
    isCustomTone = false;
    preview.setHue(factor); // U.hslToRgb(factor, .82f, .54f)
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      invalidate(); // XGram: the handle and the track's gap follow the hue
    }
  }

  private void setTapFactor (float factor) {
    preview.setColor(ColorUtils.hslToRgb(factor, tapColor[1], tapColor[2]), factor);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      invalidate(); // XGram: the handle and the track's gap follow the hue
    }
  }

  private void performTap (float x) {
    float toFactor = MathUtils.clamp((x - getPaddingLeft()) / (float) (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()));
    if (tapAnimator == null) {
      tapAnimator = new FactorAnimator(ANIMATOR_TAP, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, preview.getHue());
      com.fahadali.xgram.appearance.XgramAppMotion.use(tapAnimator, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, true); // XGram: the handle glides to the tap
    } else {
      tapAnimator.forceFactor(preview.getHue());
    }
    if (isCustomTone) {
      ColorUtils.rgbToHsl(preview.getBrushColor(), tapColor);
    } else {
      tapColor[1] = .82f;
      tapColor[2] = .54f;
    }
    tapAnimator.animateTo(toFactor);
    ViewUtils.onClick(this);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (preview == null) {
      return false;
    }

    float x = e.getX();
    float y = e.getY();

    float startX = getPaddingLeft();
    float startY = getPaddingTop();

    float viewWidth = getMeasuredWidth();
    float viewHeight = getMeasuredHeight();

    float width = viewWidth - startX - getPaddingRight();
    float height = viewHeight - startY - getPaddingBottom();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float hue = preview.getHue();
        float targetX = startX + width * hue;
        float targetY = startY + height / 2;

        downX = x;
        downY = y;

        startHue = preview.getHue();

        movingHorizontally = false;
        movingVertically = false;
        listenTap = false;

        cancelLongTap();

        int padding = Screen.dp(24f);
        boolean caughtX = Math.abs(x - targetX) <= padding;
        boolean caughtY = Math.abs(y - targetY) < padding;
        boolean caught = caughtX && caughtY;
        setCaught(caught);
        if (caught) {
          scheduleLongTap();
        } else if (caughtY && x >= startX  && x <= startX + width) {
          listenTap = true;
        }

        return caught || listenTap;
      }
      case MotionEvent.ACTION_MOVE:
        if (!caught) {

          break;
        }
        if (!movingVertically && downY - y > Screen.getTouchSlop() * 1.5) {
          movingVertically = true;
          cancelLongTap();
          downY = y;
        }

        if (!movingHorizontally && !movingVertically && Math.abs(downX - x) > Screen.getTouchSlop()) {
          movingHorizontally = true;
          cancelLongTap();
          downX = x;
        }

        if (inLongTap) {
          float toneAreaWidth = tone.getMeasuredWidth();
          float saturation = MathUtils.clamp((x + getLeft()) / toneAreaWidth);

          float toneAreaHeight = tone.getMeasuredHeight();
          float value = MathUtils.clamp(y < 0 ? -y / toneAreaHeight : 0f);
          if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
            // XGram: the tone square is inset in its card: map the finger to the square itself
            saturation = tone.xgramSaturationAt(x + getLeft());
            value = tone.xgramValueAt(y + getTop());
          }

          setDesiredPositions(saturation, value);

          if (!pickingTone && value != 0f) {
            setPickingTone(true);
          }
        } else {
          if (movingVertically) {
            float dy = y - downY;
            setBaseY(-Screen.dp(MIN_Y) + dy, true);
          }
          if (movingHorizontally) {
            float factor = MathUtils.clamp(startHue + (x - downX) / width);
            setColorFactor(factor);
          }
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        setCaught(false);
        break;
      case MotionEvent.ACTION_UP:
        setCaught(false);
        if (listenTap) {
          performTap(e.getX());
        }
        break;
    }

    return true;
  }

  // XGram M3E: the hue track is an Expressive slider track (full corners, its height from the padding) split by a 4dp
  // gap on both sides of a 4 x 44dp white handle at the hue, which narrows to 2dp while held.

  private final android.graphics.Path xgramPath = new android.graphics.Path();
  private final float[] xgramRadii = new float[8];
  private final RectF xgramRect = new RectF();
  private final Paint xgramHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private void xgramSegment (Canvas c, float left, float right, float height, float leftRadius, float rightRadius) {
    if (right - left <= 0f) {
      return;
    }
    xgramRect.set(left, 0, right, height);
    xgramRadii[0] = xgramRadii[1] = xgramRadii[6] = xgramRadii[7] = leftRadius;
    xgramRadii[2] = xgramRadii[3] = xgramRadii[4] = xgramRadii[5] = rightRadius;
    xgramPath.rewind();
    xgramPath.addRoundRect(xgramRect, xgramRadii, android.graphics.Path.Direction.CW);
    c.drawPath(xgramPath, colorPaint);
  }

  private void xgramDraw (Canvas c) {
    final int startX = getPaddingLeft(), startY = getPaddingTop();
    final int width = getMeasuredWidth() - startX - getPaddingRight();
    final int height = getMeasuredHeight() - startY - getPaddingBottom();
    if (width <= 0 || height <= 0) {
      return;
    }
    final float hue = preview != null ? MathUtils.clamp(preview.getHue()) : 0f;
    final float held = MathUtils.clamp(caughtFactor);
    final float handleWidth = Screen.dpf(4f) - Screen.dpf(2f) * held;
    final float handleHeight = Math.min(Screen.dpf(44f), getMeasuredHeight());
    final float gap = Screen.dpf(4f);
    final float full = height / 2f, inner = Screen.dpf(2f);
    final float handleX = width * hue; // in the track's coordinates

    c.save();
    c.translate(startX, startY);
    xgramSegment(c, 0f, handleX - handleWidth / 2f - gap, height, full, inner);
    xgramSegment(c, handleX + handleWidth / 2f + gap, width, height, inner, full);
    c.restore();

    final float cx = startX + handleX, cy = startY + height / 2f;
    xgramRect.set(cx - handleWidth / 2f, cy - handleHeight / 2f, cx + handleWidth / 2f, cy + handleHeight / 2f);
    xgramHandlePaint.setColor(com.fahadali.xgram.appearance.XgramM3MediaChrome.white(1f));
    c.drawRoundRect(xgramRect, handleWidth / 2f, handleWidth / 2f, xgramHandlePaint);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDraw(c);
      return;
    }
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    int startX = getPaddingLeft();
    int startY = getPaddingTop();

    int width = viewWidth - startX - getPaddingRight();
    int height = viewHeight - startY - getPaddingBottom();

    final boolean saved = startX != 0 || startY != 0;
    if (saved) {
      c.save();
      c.translate(startX, startY);
    }

    RectF rectF = Paints.getRectF();
    rectF.set(0, 0, width, height);
    c.drawRoundRect(rectF, Screen.dp(6f), Screen.dp(6f), colorPaint);

    if (saved) {
      c.restore();
    }
  }
}
