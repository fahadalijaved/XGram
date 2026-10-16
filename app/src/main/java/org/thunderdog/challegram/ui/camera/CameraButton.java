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
 * File created on 22/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class CameraButton extends View implements FactorAnimator.Target, Runnable {
  private CameraController parent;
  private CameraBlurView blurView;

  private Bitmap cameraIcon, stopIcon, closeIcon;

  public CameraButton (Context context) {
    super(context);
    Views.setClickable(this);
    RippleSupport.setCircleBackground(this, 56f, 4f, ColorId.white, true, null);
    setLayerType(LAYER_TYPE_HARDWARE, null);

    int padding = Screen.dp(4f);
    setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2));
    setPadding(padding, padding, padding, padding);

    // cameraIcon = new EraseDrawable(R.drawable.baseline_camera_alt_24, ColorId.white);
    // stopIcon = new EraseDrawable(R.drawable.baseline_stop_24, ColorId.white);
    cameraIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_camera_alt_24));
    closeIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_close_24));
    stopIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_stop_24));

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: the M3E (Pixel camera) shutter: 80dp, no shadowed white circle behind it
      setBackground(null);
      setPadding(0, 0, 0, 0);
      setLayoutParams(FrameLayoutFix.newParams(Screen.dp(XGRAM_SIZE), Screen.dp(XGRAM_SIZE)));
      xgramPress = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      setContentDescription(org.thunderdog.challegram.core.Lang.getString(R.string.XgramR5MediaShutter));
    }
  }

  // XGram: Material 3 Expressive shutter. An 80dp control: a 4dp white ring (outer edge at 40dp) around a 64dp white
  // disc; pressed, the disc shrinks a little (spatial fast); while recording it morphs into a 36dp error square with
  // 12dp (CORNER_M) corners, on the record animator, which runs on the spatial default spring (clamped). In QR scan
  // mode the control is the close button: a 56dp on-media container with the close icon.

  public static final float XGRAM_SIZE = 80f;
  private static final float XGRAM_RING_WIDTH = 4f, XGRAM_DISC = 64f, XGRAM_RECORD_SQUARE = 36f, XGRAM_PRESSED_SCALE = .9f;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPress;
  private final Paint xgramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();

  @Override
  public void setPressed (boolean pressed) {
    super.setPressed(pressed);
    if (xgramPress != null) {
      xgramPress.animateTo(pressed && !isInRecordMode ? 1f : 0f);
    }
  }

  private void xgramDraw (Canvas c, int cx, int cy) {
    if (parent.isInQrScanMode()) {
      final float alpha = Math.max(0f, 1f - recordFactor / .5f);
      xgramPaint.setStyle(Paint.Style.FILL);
      xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramM3MediaChrome.onMedia(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH), .6f * alpha));
      c.drawCircle(cx, cy, Screen.dp(28f), xgramPaint);
      Paint iconPaint = Paints.whitePorterDuffPaint();
      int iconAlpha = iconPaint.getAlpha();
      iconPaint.setAlpha((int) (255f * alpha));
      c.drawBitmap(closeIcon, cx - closeIcon.getWidth() / 2f, cy - closeIcon.getHeight() / 2f, iconPaint);
      iconPaint.setAlpha(iconAlpha);
      return;
    }
    final float ring = Screen.dp(XGRAM_RING_WIDTH);
    xgramPaint.setStyle(Paint.Style.STROKE);
    xgramPaint.setStrokeWidth(ring);
    xgramPaint.setColor(0xffffffff);
    c.drawCircle(cx, cy, Screen.dp(XGRAM_SIZE) / 2f - ring / 2f, xgramPaint);

    final float record = MathUtils.clamp(recordFactor);
    final float press = xgramPress != null ? MathUtils.clamp(xgramPress.getValue()) : 0f;
    final float discSize = Screen.dp(XGRAM_DISC) * (1f - (1f - XGRAM_PRESSED_SCALE) * press);
    final float size = discSize + (Screen.dp(XGRAM_RECORD_SQUARE) - discSize) * record;
    final float radius = size / 2f + (Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M) - size / 2f) * record;
    xgramRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    xgramPaint.setStyle(Paint.Style.FILL);
    xgramPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(0xffffffff, com.fahadali.xgram.appearance.XgramM3MediaChrome.onMedia(com.fahadali.xgram.appearance.XgramExpressive.ERROR), record));
    c.drawRoundRect(xgramRect, radius, radius, xgramPaint);
  }

  public void setParent (CameraController parent) {
    this.parent = parent;
  }

  public void setBlurView (CameraBlurView blurView) {
    this.blurView = blurView;
  }

  private boolean isActive;

  private void dropActive (@Nullable MotionEvent e) {
    if (isActive) {
      isActive = false;
      if (e != null) {
        super.onTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_UP, e.getX(), e.getY(), e.getMetaState()));
      } else {
        long eTime = SystemClock.uptimeMillis();
        super.onTouchEvent(MotionEvent.obtain(eTime, eTime, MotionEvent.ACTION_UP, x, y, 0));
      }
      cancelScheduledVideoRecord();
    }
  }

  private void scheduleVideoRecord () {
    postDelayed(this, ViewConfiguration.getLongPressTimeout());
  }

  private void cancelScheduledVideoRecord () {
    removeCallbacks(this);
  }

  private boolean inVideoCapture;

  public boolean finishVideoCapture (boolean needPhoto) {
    cancelScheduledVideoRecord();
    if (inVideoCapture) {
      inVideoCapture = false;
      needPhoto = needPhoto && listener != null && !isInRecordMode;
      if (listener != null) {
        listener.onFinishVideoCapture(this);
      } else {
        blurView.performSuccessHint();
        setInRecordMode(false);
      }
      if (!needPhoto)
        return true;
    }
    return needPhoto && takePhoto();
  }

  public boolean takePhoto () {
    if (!inVideoCapture) {
      blurView.performSuccessHint();
      if (listener != null) {
        listener.onTakePicture(this);
      }
      return true;
    }
    return false;
  }

  private boolean takeVideoRequested;

  public void takeVideo () {
    takeVideoRequested = true;
    if (listener != null) {
      inVideoCapture = listener.onStartVideoCapture(this);
    } else {
      inVideoCapture = true;
      setInRecordMode(true);
    }
  }

  private boolean isInRecordMode;
  private float recordFactor;
  private FactorAnimator recordAnimator;
  private static final int ANIMATOR_RECORD = 0;

  public void setInRecordMode (boolean inRecordMode) {
    if (this.isInRecordMode != inRecordMode && (inVideoCapture || !inRecordMode)) {
      this.isInRecordMode = inRecordMode;
      if (recordAnimator == null) {
        recordAnimator = new FactorAnimator(ANIMATOR_RECORD, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.recordFactor);
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          // XGram: the shutter morphs on the spatial default spring (clamped: the controls fade with the same factor)
          recordAnimator.setInterpolator(com.fahadali.xgram.appearance.XgramMotion.interpClamped(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR));
          recordAnimator.setDuration(com.fahadali.xgram.appearance.XgramMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT));
        }
      }
      if (inRecordMode && xgramPress != null) {
        xgramPress.animateTo(0f);
      }
      recordAnimator.animateTo(inRecordMode ? 1f : 0f);
      if (inRecordMode) {
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_RECORD_VIDEO);
        dropActive(null);
      } else if (listener != null) {
        blurView.performSuccessHint();
      }
    }
  }

  private void setRecordFactor (float factor) {
    if (this.recordFactor != factor) {
      this.recordFactor = factor;
      blurView.setExpandFactor(factor);
      if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) { // XGram: the disc morphs instead
      float scale = .85f + .15f * (1f - factor);
      setScaleX(scale);
      setScaleY(scale);
      }
      parent.setVideoFactor(factor);
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_RECORD:
        setRecordFactor(factor);
        break;
    }
  }

  /*@Override
  public void draw (Canvas c) {
    if (recordFactor != 0f) {
      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();
      c.save();
      float scale = .8f + (1f - recordFactor) * .2f;
      c.scale(scale, scale, viewWidth / 2, viewHeight / 2);
    }
    super.draw(c);
    if (recordFactor != 0f) {
      c.restore();
    }
  }*/

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private float x, y;
  private boolean isZooming;

  private float actualZoom;

  public void setActualZoom (float zoom) {
    this.actualZoom = zoom;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    x = e.getX();
    y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        isZooming = false;
        super.onTouchEvent(e);
        if (!isInRecordMode) {
          scheduleVideoRecord();
        }
        isActive = true;
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isActive) {
          if (x < 0 || y < 0 || x > viewWidth || y > viewHeight) {
            dropActive(e);
          } else {
            super.onTouchEvent(e);
          }
        }

        final float zoomFactor = MathUtils.clamp(-y / Screen.dpf(150f));
        float minZoom = parent.getManager().getMinZoom();
        float maxZoom = parent.getManager().getMaxZoom();

        if (!isZooming && y < 0 && (minZoom + (maxZoom - minZoom) * zoomFactor) >= actualZoom) {
          isZooming = true;
        }

        if (isZooming) {
          parent.getManager().onRequestZoom(minZoom + (maxZoom - minZoom) * zoomFactor);
        }

        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (isActive) {
          super.onTouchEvent(e);
        }
        finishVideoCapture(!isInRecordMode && e.getAction() == MotionEvent.ACTION_UP && isActive && !takeVideoRequested);
        isActive = false;
        takeVideoRequested = false;
        break;

      default: {
        if (isActive) {
          super.onTouchEvent(e);
        }
        break;
      }
    }

    return true;
  }

  @Override
  public void run () {
    if (isActive) {
      takeVideo();
    }
  }

  private float componentRotation;
  public void setComponentRotation (float componentRotation) {
    if (this.componentRotation != componentRotation) {
      this.componentRotation = componentRotation;
      setRotation(componentRotation);
      isZooming = false;
    }
  }

  private static void drawBitmap (Canvas c, Bitmap d, int cx, int cy, float alpha) {
    if (alpha == 0f)
      return;
    cx -= d.getWidth() / 2;
    cy -= d.getHeight() / 2;
    Paint paint = Paints.getXorPaint();
    paint.setAlpha((int) (255f * alpha));
    c.drawBitmap(d, cx, cy, paint);
    // d.draw(c, cx, cy, alpha);
  }

  @Override
  protected void onDraw (Canvas c) {
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDraw(c, cx, cy); // XGram
      return;
    }

    /*if (componentRotation != 0f) {
      c.save();
      c.rotate(componentRotation, cx, cy);
    }*/

    if (parent.isInQrScanMode()) {
      drawBitmap(c, closeIcon, cx, cy, 1f - recordFactor / .5f);
      return;
    }

    if (recordFactor > .3f) {
      float alpha = ((recordFactor - .3f) / .7f);
      drawBitmap(c, stopIcon, cx, cy, alpha);
    }
    if (recordFactor <= .5f) {
      float alpha = 1f - recordFactor / .5f;
      drawBitmap(c, cameraIcon, cx, cy, alpha);
    }
    /*if (componentRotation != 0) {
      c.restore();
    }*/
  }

  // API

  public interface RecordListener {
    /**
     * Called when user requests to take photo shot.
     *
     * This method can be called instantly after {@link #onFinishVideoCapture(CameraButton)}
     * in cases when {@link CameraButton#setInRecordMode(boolean true)} has not been called.
     * */
    void onTakePicture (CameraButton v);

    /**
     * Called when user requests to start video capture.
     *
     * If method returns true, {@link #onFinishVideoCapture(CameraButton)} will be called later.
     * */
    boolean onStartVideoCapture (CameraButton v);

    /**
     * Called when user requested to stop video capture or in case when activity has been paused.
     * */
    void onFinishVideoCapture (CameraButton v);
  }

  @Nullable
  private RecordListener listener;

  public void setRecordListener (RecordListener listener) {
    this.listener = listener;
  }


}
