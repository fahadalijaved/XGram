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
 * File created on 24/06/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class RoundProgressView3 extends View {
  public static final int PADDING = 16;
  private static final int STROKE_WIDTH = 4;
  private static final int POINTER_RADIUS = 6;

  private final Paint strokePaintDefault;
  private final Paint strokePaintFilling;

  public RoundProgressView3(Context context) {
    super(context);
    strokePaintDefault = new Paint(Paints.videoStrokePaint());
    strokePaintFilling = new Paint(Paints.videoStrokePaint());
  }

  private float visualProgress;

  public void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      this.visualProgress = progress;
      invalidate();
    }
  }




  private float lastDrawProgress;
  private float lastDrawStartPointerX, lastDrawStartPointerY, lastDrawEndPointerX, lastDrawEndPointerY;

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    final float progress = visualProgress;

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDraw(c, viewWidth, viewHeight, progress);
      return;
    }

    final int defaultColor = 0xFFFFFFFF;
    final int fillingColor = ColorUtils.fromToArgb(defaultColor, Theme.getColor(ColorId.fillingPositive), editFactor);

    strokePaintDefault.setStrokeWidth(Screen.dp(STROKE_WIDTH));
    strokePaintDefault.setColor(defaultColor);

    strokePaintFilling.setStrokeWidth(Screen.dp(STROKE_WIDTH));
    strokePaintFilling.setColor(fillingColor);


    final int padding = Screen.dp(PADDING);
    final float angle = 360f * progress;

    RectF rectF = Paints.getRectF();
    rectF.set(padding, padding, viewWidth - padding, viewHeight - padding);

    c.drawArc(rectF, -90, angle * trimStartFactor, false, strokePaintDefault);
    c.drawArc(rectF, -90 + angle * trimEndFactor, angle * (1f - trimEndFactor), false, strokePaintDefault);
    c.drawArc(rectF, -90 + angle * trimStartFactor, angle * (trimEndFactor - trimStartFactor), false, strokePaintFilling);

    {
      final double angleRad = Math.toRadians(-90 + angle * trimStartFactor);
      float x = lastDrawStartPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawStartPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * editFactor, Paints.fillingPaint(fillingColor));
    }

    {
      final double angleRad = Math.toRadians(-90 + angle * trimEndFactor);
      float x = lastDrawEndPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawEndPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * editFactor, Paints.fillingPaint(fillingColor));
    }

    this.lastDrawProgress = progress;
  }

  /**
   * XGram M3E: the ring is the round video's wavy progress (XgramM3Content.drawRoundVideoRing's look): primary on a
   * white track at 24 %, 4dp, wavy while recording and turning to error over the last seconds before the limit.
   * In the preview (editFactor) it flattens: the trimmed part in primary, the rest on the track, and the two trim
   * handles as primary dots (the touch targets are Telegram X's).
   */
  private void xgramDraw (@NonNull Canvas c, int viewWidth, int viewHeight, float progress) {
    final int padding = Screen.dp(PADDING);
    final RectF rectF = Paints.getRectF();
    rectF.set(padding, padding, viewWidth - padding, viewHeight - padding);
    final float cx = rectF.centerX(), cy = rectF.centerY();
    final float radius = rectF.width() / 2f;
    final float stroke = Screen.dpf(com.fahadali.xgram.appearance.XgramM3Content.WAVY_STROKE_DP);
    final float edit = MathUtils.clamp(editFactor);
    final int track = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(0xffffffff, .24f);
    final int ring = com.fahadali.xgram.appearance.XgramR5Composer.recordRingColor(edit > 0f ? 0f : progress);
    final float wave = 1f - edit;

    if (edit < 1f) {
      // Recording: the whole recorded part, wavy
      final int alpha = Math.round(255f * (1f - edit));
      final float amplitude = Screen.dpf(com.fahadali.xgram.appearance.XgramM3Content.WAVY_AMPLITUDE_DP) * wave;
      com.fahadali.xgram.appearance.XgramWavyProgress.drawCircular(c, cx, cy, radius, stroke, progress,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(ring, alpha / 255f), com.fahadali.xgram.appearance.XgramExpressive.withAlpha(track, alpha / 255f), amplitude);
    }

    final float angle = 360f * progress;
    if (edit > 0f) {
      if (xgramStrokePaint == null) {
        xgramStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xgramStrokePaint.setStyle(Paint.Style.STROKE);
        xgramStrokePaint.setStrokeCap(Paint.Cap.ROUND);
      }
      xgramStrokePaint.setStrokeWidth(stroke);
      xgramStrokePaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(track, edit));
      c.drawArc(rectF, -90, angle * trimStartFactor, false, xgramStrokePaint);
      c.drawArc(rectF, -90 + angle * trimEndFactor, angle * (1f - trimEndFactor), false, xgramStrokePaint);
      xgramStrokePaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY), edit));
      c.drawArc(rectF, -90 + angle * trimStartFactor, angle * (trimEndFactor - trimStartFactor), false, xgramStrokePaint);
    }

    final int handleColor = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
    {
      final double angleRad = Math.toRadians(-90 + angle * trimStartFactor);
      float x = lastDrawStartPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawStartPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      if (edit > 0f) {
        c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * edit, Paints.fillingPaint(handleColor));
      }
    }
    {
      final double angleRad = Math.toRadians(-90 + angle * trimEndFactor);
      float x = lastDrawEndPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawEndPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      if (edit > 0f) {
        c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * edit, Paints.fillingPaint(handleColor));
      }
    }
    this.lastDrawProgress = progress;

    if (edit < 1f && getAlpha() > 0f && com.fahadali.xgram.appearance.XgramM3Content.needsWaveFrame(progress, wave)) {
      postInvalidateOnAnimation(); // the wave travels while recording (never with Reduce motion)
    }
  }

  private Paint xgramStrokePaint;



  private float editFactor;

  public void setEditFactor (float editFactor) {
    if (this.editFactor != editFactor){
      this.editFactor = editFactor;
      invalidate();
    }
  }

  private float trimStartFactor = 0f, trimEndFactor = 1f;

  public void setTrimFactors (float start, float end) {
    if (captured != CAPTURED_NONE) {
      return;
    }

    this.trimStartFactor = start;
    this.trimEndFactor = end;
    invalidate();
  }

  public void reset () {
    this.trimStartFactor = 0f;
    this.trimEndFactor = 1f;
    this.visualProgress = 0f;
    this.editFactor = 0f;
  }





  public interface Delegate {
    void onTrimSliderDown (RoundProgressView3 view, float start, float end, boolean isEnd);
    void onTrimSliderMove (RoundProgressView3 view, float start, float end, boolean isEnd);
    void onTrimSliderUp (RoundProgressView3 view, float start, float end, boolean isEnd);
  }


  private static final int CAPTURED_NONE = 0;
  private static final int CAPTURED_START = 1;
  private static final int CAPTURED_END = 2;

  private Delegate delegate;
  private int captured;

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    final int action = event.getAction();
    final float x = event.getX();
    final float y = event.getY();

    if (editFactor == 0) {
      captured = CAPTURED_NONE;
      return false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (MathUtils.distance(x, y, lastDrawEndPointerX, lastDrawEndPointerY) < Screen.dp(24)) {
          captured = CAPTURED_END;
          if (delegate != null) {
            delegate.onTrimSliderDown(this, trimStartFactor, trimEndFactor, true);
          }
          return true;
        }
        if (MathUtils.distance(x, y, lastDrawStartPointerX, lastDrawStartPointerY) < Screen.dp(24)) {
          captured = CAPTURED_START;
          if (delegate != null) {
            delegate.onTrimSliderDown(this, trimStartFactor, trimEndFactor, false);
          }
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        final double rad = Math.atan2(y - getMeasuredHeight() / 2f, x - getMeasuredWidth() / 2f);
        float angle = ((float) Math.toDegrees(rad) + 360 + 90) % 360;

        if (captured == CAPTURED_END) {
          final float angleStart = 360 * visualProgress * trimStartFactor;
          final float angleEnd = 360 * visualProgress;

          if (!(angleStart <= angle && angle <= angleEnd)) {
            final float distanceToStart = angleDistance(angleStart, angle);
            final float distanceToEnd = angleDistance(angleEnd, angle);
            angle = (distanceToStart < distanceToEnd) ? angleStart : angleEnd;
          }

          trimEndFactor = angle / (360f * visualProgress);
          if (delegate != null) {
            delegate.onTrimSliderMove(this, trimStartFactor, trimEndFactor, true);
          }
          invalidate();
          return true;
        }

        if (captured == CAPTURED_START) {
          final float angleStart = 0;
          final float angleEnd = 360 * visualProgress * trimEndFactor;

          if (!(angleStart <= angle && angle <= angleEnd)) {
            final float distanceToStart = angleDistance(angleStart, angle);
            final float distanceToEnd = angleDistance(angleEnd, angle);
            angle = (distanceToStart < distanceToEnd) ? angleStart : angleEnd;
          }

          trimStartFactor = angle / (360f * visualProgress);
          if (delegate != null) {
            delegate.onTrimSliderMove(this, trimStartFactor, trimEndFactor, false);
          }
          invalidate();
          return true;
        }

        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (captured != CAPTURED_NONE) {
          /*updateSeekRunnable.cancelIfScheduled();
          if (controller != null) {
            controller.seekTo(MathUtils.clamp(visualProgress), true);
          }
          if (seekCaught != null) {
            seekCaught.requestDisallowInterceptTouchEvent(false);
          }*/

          if (delegate != null) {
            delegate.onTrimSliderUp(this, trimStartFactor, trimEndFactor, captured == CAPTURED_END);
          }
          captured = CAPTURED_NONE;
          return true;
        }
    }

    return false;
  }

  private static float angleDistance (float angle1, float angle2) {
    final float distance = Math.abs(angle1 - angle2);
    return distance > 180 ? 360 - distance : distance;
  }

}
