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
 * File created on 20/08/2015 at 02:59
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class RecordButton extends View implements FactorAnimator.Target, ClickHelper.Delegate {

  private static final float MAX_FACTOR = 3f;

  private int radiusAdd;
  private int radius;
  private int center;

  private final ClickHelper helper = new ClickHelper(this);

  public RecordButton (Context context) {
    super(context);

    radiusAdd = Screen.dp(20f);
    radius = Screen.dp(41f);

    center = (int) (radius + radiusAdd * MAX_FACTOR);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(center * 2, center * 2, Gravity.TOP | Gravity.LEFT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          if (expand <= 0f) {
            outline.setEmpty();
          } else {
            int radius = (int) (RecordButton.this.radius * expand);
            outline.setRoundRect(center - radius, center - radius, center + radius, center + radius, radius);
          }
        }
      });
      setElevation(Screen.dp(1f));
      setTranslationZ(Screen.dp(2f));
    }

    setLayoutParams(params);
  }

  private View.OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    this.onClickListener = l;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return onClickListener != null && (e.getAction() != MotionEvent.ACTION_DOWN || (expand == 1f && U.isInside(e.getX(), e.getY(), getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand))) && helper.onTouchEvent(this, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand)) {
      onClickListener.onClick(this);
    }
  }

  public float getCenter () {
    return center;
  }

  public int getSize () {
    return center * 2;
  }

  // Animators

  private float expand;

  public void setExpand (float expand) {
    if (this.expand != expand) {
      this.expand = expand;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      invalidate();
    }
  }

  public float getExpand () {
    return expand;
  }

  private float volume;
  private FactorAnimator animator;

  private boolean scheduledToVolume;
  private float currentToVolume;

  public void setVolume (float toVolume, boolean animated) {
    toVolume = Math.min(MAX_FACTOR, toVolume / 150f);
    if (animated) {
      if (Math.round((float) radiusAdd * currentToVolume) != Math.round((float) radiusAdd * toVolume)) {
        if (animator == null) {
          if (volume == toVolume) {
            return;
          }
          this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 190l, volume);
          com.fahadali.xgram.appearance.XgramAppMotion.use(animator, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, false); // XGram: the halo breathes on a spring with Expressive design
        }
        currentToVolume = toVolume;
        if (toVolume >= currentToVolume || toVolume > 0 || !animator.isAnimating()) {
          scheduledToVolume = false;
          animator.animateTo(toVolume);
        } else {
          scheduledToVolume = true;
        }
      }
    } else {
      scheduledToVolume = false;
      if (animator != null) {
        animator.forceFactor(toVolume);
      }
      setVolume(toVolume);
    }
  }

  private void setVolume (float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setVolume(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (scheduledToVolume) {
      animator.animateTo(currentToVolume);
    }
  }

  // XGram: Material 3 Expressive recording button. The disk keeps the input bar's record button role, primary (the
  // filled icon button; the mic on top of it is VoiceVideoButtonView's onPrimary glyph); the volume halo is a primary
  // soft burst at 30% that turns once every 12 seconds (not with Reduce motion). The burst path is built once in unit
  // size: frames only move the canvas.
  private static final int XGRAM_DISK_ROLE = com.fahadali.xgram.appearance.XgramExpressive.PRIMARY;
  private static final long XGRAM_HALO_TURN_MS = 12000L;
  private @Nullable android.graphics.Path xgramHalo;
  private float xgramHaloCenterX, xgramHaloCenterY, xgramHaloUnit;

  private void xgramDraw (Canvas c) {
    final float haloSize = (radius + radiusAdd * volume) * expand * 2f;
    if (volume > 0f && haloSize > 0f) {
      if (xgramHalo == null) {
        xgramHalo = new android.graphics.Path();
        com.fahadali.xgram.appearance.XgramShapes.toPath(com.fahadali.xgram.appearance.XgramShapes.SOFT_BURST, xgramHalo);
        android.graphics.RectF bounds = new android.graphics.RectF();
        xgramHalo.computeBounds(bounds, true);
        xgramHaloCenterX = bounds.centerX();
        xgramHaloCenterY = bounds.centerY();
        float extent = Math.max(bounds.width(), bounds.height());
        xgramHaloUnit = extent > 0f ? 1f / extent : 1f;
      }
      final boolean reduceMotion = org.thunderdog.challegram.unsorted.Settings.instance().needReduceMotion();
      final float angle = reduceMotion ? 0f : (float) (android.os.SystemClock.uptimeMillis() % XGRAM_HALO_TURN_MS) / (float) XGRAM_HALO_TURN_MS * 360f;
      final float scale = haloSize * xgramHaloUnit;
      final int haloColor = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY), .3f);
      c.save();
      c.translate(center, center);
      if (angle != 0f) {
        c.rotate(angle);
      }
      c.scale(scale, scale);
      c.translate(-xgramHaloCenterX, -xgramHaloCenterY);
      c.drawPath(xgramHalo, Paints.fillingPaint(haloColor));
      c.restore();
      if (!reduceMotion) {
        postInvalidateOnAnimation(); // only while the halo shows, i.e. while recording with sound
      }
    }
    c.drawCircle(center, center, radius * expand, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(XGRAM_DISK_ROLE)));
  }

  @Override
  public void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDraw(c);
      return;
    }
    int color = Theme.getColor(ColorId.circleButtonRegular);
    c.drawCircle(center, center, (radius + radiusAdd * volume) * expand, Paints.fillingPaint(ColorUtils.alphaColor(.3f, color)));
    c.drawCircle(center, center, radius * expand, Paints.fillingPaint(color));
  }
}
