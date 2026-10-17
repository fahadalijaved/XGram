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
 * File created on 22/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class MediaLocationPointView extends View implements FactorAnimator.Target {
  private static final int ANIMATOR_GPS = 0;
  private static final int ANIMATOR_CUSTOM = 1;
  private static final int ANIMATOR_PLACE = 2;

  private long initializationTime = SystemClock.uptimeMillis();
  private final BoolAnimator gpsLocated = new BoolAnimator(ANIMATOR_GPS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator isCustom = new BoolAnimator(ANIMATOR_CUSTOM, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator isPlace = new BoolAnimator(ANIMATOR_PLACE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private final ProgressComponent progressComponent = ProgressComponent.simpleInstance(this, 5f, Screen.dp(20f), Screen.dp(8f), Screen.dp(40f), Screen.dp(40));

  private final Drawable locationIcon, sendIcon;

  public MediaLocationPointView (Context context) {
    super(context);
    locationIcon = Drawables.get(getResources(), R.drawable.baseline_location_on_24);
    sendIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_send_24);
    progressComponent.setAlpha(1f);
    if (xgram) {
      xgramLocationIcon = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_location_on_24));
      xgramSendIcon = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_send_24));
      // XGram M3E: springs instead of the 180ms decelerate fades: colour on effects, the icon morph on spatial
      com.fahadali.xgram.appearance.XgramAppMotion.use(gpsLocated, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, true);
      com.fahadali.xgram.appearance.XgramAppMotion.use(isCustom, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, true);
      com.fahadali.xgram.appearance.XgramAppMotion.use(isPlace, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, true);
    }
  }

  // XGram M3E: the send tile's leading visual: a 40dp primaryContainer container whose 12dp corners round into a
  // circle as the location pin turns into the send arrow (a place picked), both in onPrimaryContainer.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private Drawable xgramLocationIcon, xgramSendIcon;

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (xgram && w > 0 && h > 0) {
      final int size = Screen.dp(com.fahadali.xgram.appearance.XgramR6B7Places.LEADING);
      final int left = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight() - size) / 2;
      final int top = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom() - size) / 2;
      progressComponent.setBounds(left, top, left + size, top + size);
    }
  }

  private void xgramDraw (Canvas c) {
    final float cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
    final float cy = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    final float activeFactor = Math.max(gpsLocated.getFloatValue(), isCustom.getFloatValue());
    final float place = isPlace.getFloatValue(); // may overshoot on its spring
    final float p = me.vkryl.core.MathUtils.clamp(place);
    final float leading = com.fahadali.xgram.appearance.XgramR6B7Places.LEADING;
    final float corner = com.fahadali.xgram.appearance.XgramR6B7Places.LEADING_CORNER + (leading / 2f - com.fahadali.xgram.appearance.XgramR6B7Places.LEADING_CORNER) * p;
    com.fahadali.xgram.appearance.XgramR6B7Places.drawContainer(c, cx, cy, leading, corner, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER));
    final int content = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER);
    progressComponent.forceColor(content);
    progressComponent.draw(c);
    if (activeFactor > 0f && p < 1f) {
      final int saveCount = c.save();
      final float scale = 1f - .4f * p;
      c.scale(scale, scale, cx, cy);
      com.fahadali.xgram.appearance.XgramR6B7Places.drawIcon(c, xgramLocationIcon, cx, cy, content, activeFactor * (1f - p));
      c.restoreToCount(saveCount);
    }
    if (p > 0f) {
      final int saveCount = c.save();
      final float scale = .6f + .4f * Math.max(0f, place);
      c.rotate(-45f * (1f - p), cx, cy);
      c.scale(scale * .85f, scale * .85f, cx, cy);
      com.fahadali.xgram.appearance.XgramR6B7Places.drawIcon(c, xgramSendIcon, cx, cy, content, p);
      c.restoreToCount(saveCount);
    }
  }

  private boolean needAnimation () {
    if (initializationTime == 0) {
      return true;
    }
    if (SystemClock.uptimeMillis() - initializationTime >= 100l) {
      initializationTime = 0;
      return true;
    }
    return false;
  }

  public void setShowProgress (boolean show) {
    this.gpsLocated.setValue(!show, needAnimation());
  }

  public void setIsCustom (boolean isCustom) {
    this.isCustom.setValue(isCustom, needAnimation());
  }

  public void setIsPlace (boolean isPlace) {
    this.isPlace.setValue(isPlace, true);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    progressComponent.setAlpha(1f - Math.max(gpsLocated.getFloatValue(), Math.max(isCustom.getFloatValue(), isPlace.getFloatValue())));
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  protected void onDraw (Canvas c) {
    if (xgram) {
      xgramDraw(c);
      return;
    }
    float cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;
    float cy = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / 2;

    float gpsFactor = gpsLocated.getFloatValue();
    float customFactor = isCustom.getFloatValue();
    float placeFactor = isPlace.getFloatValue();
    float activeFactor = Math.max(gpsFactor, customFactor);

    int color = Theme.getColor(ColorId.file);

    c.drawCircle(cx, cy, Screen.dp(20f), Paints.fillingPaint(color));
    progressComponent.draw(c);
    if (activeFactor > 0f && placeFactor < 1f) {
      Paint paint = Paints.whitePorterDuffPaint();
      paint.setAlpha((int) (255f * activeFactor * (1f - placeFactor)));
      Drawables.draw(c, locationIcon, cx - locationIcon.getMinimumWidth() / 2, cy - locationIcon.getMinimumHeight() / 2, paint);
      paint.setAlpha(255);
    }
    if (placeFactor > 0f) {
      Paint paint = Paints.whitePorterDuffPaint();
      paint.setAlpha((int) (255f * placeFactor));
      c.save();
      c.scale(.7f, .7f, cx, cy);
      Drawables.draw(c, sendIcon, cx + Screen.dp(2f) - sendIcon.getMinimumWidth() / 2, cy - sendIcon.getMinimumHeight() / 2, paint);
      c.restore();
      paint.setAlpha(255);
    }
  }
}
