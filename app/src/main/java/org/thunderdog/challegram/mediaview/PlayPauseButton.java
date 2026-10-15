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
 * File created on 13/07/2018
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

class PlayPauseButton extends View implements FactorAnimator.Target {
  private final Path playPausePath = new Path();
  private float playPauseDrawFactor = -1f;
  private final BoolAnimator playPauseAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l);

  public PlayPauseButton (Context context) {
    super(context);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setIsPlaying (boolean isPlaying, boolean animated) {
    playPauseAnimator.setValue(isPlaying, animated);
  }

  // XGram: Material 3 Expressive play/pause: a 40dp primaryContainer button whose shape springs from a circle
  // (paused) to a 12dp rounded square (playing), onPrimaryContainer icon
  private @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramShape;
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();

  private float xgramShapeFactor () {
    float target = playPauseAnimator.getValue() ? 1f : 0f;
    if (xgramShape == null) {
      xgramShape = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, target, (s, value) -> invalidate());
    } else if (xgramShape.getTarget() != target) {
      xgramShape.animateTo(target);
    }
    return xgramShape.getValue();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      float cx = getMeasuredWidth() / 2f, cy = getMeasuredHeight() / 2f;
      float half = Math.min(Screen.dp(20f), Math.min(getMeasuredWidth(), getMeasuredHeight()) / 2f);
      float shape = xgramShapeFactor();
      float radius = half + (Screen.dp(12f) - half) * Math.max(0f, Math.min(1.2f, shape));
      xgramRect.set(cx - half, cy - half, cx + half, cy + half);
      c.drawRoundRect(xgramRect, radius, radius, org.thunderdog.challegram.tool.Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER)));
      DrawAlgorithms.drawPlayPause(c, (int) cx, (int) cy, Screen.dp(9f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseAnimator.getFloatValue(), 1f, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER));
      return;
    }
    DrawAlgorithms.drawPlayPause(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2, Screen.dp(12f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseAnimator.getFloatValue(), 1f, 0xffffffff);
  }
}
