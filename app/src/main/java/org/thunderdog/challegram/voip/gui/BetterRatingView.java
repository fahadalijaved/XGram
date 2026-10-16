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
 * File created on 10/06/2017
 */
package org.thunderdog.challegram.voip.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class BetterRatingView extends View {
  private Drawable filledStar, hollowStar;
  private Paint paint=new Paint();
  private int numStars=5;
  private int selectedRating=0;
  private OnRatingChangeListener listener;

  public BetterRatingView(Context context){
    super(context);
    filledStar = Drawables.get(R.drawable.baseline_star_24);
    hollowStar = Drawables.get(R.drawable.baseline_star_border_24);
    if (xgram) {
      // XGram M3E: one drawable per star, so each can be tinted and scaled on its own
      xgramFilled = new Drawable[numStars];
      xgramHollow = new Drawable[numStars];
      for (int i = 0; i < numStars; i++) {
        xgramFilled[i] = Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_star_24));
        xgramHollow[i] = Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_star_border_24));
      }
      xgramPop = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 1f, (s, value) -> invalidate());
      setContentDescription(Lang.getString(R.string.VoipRateCallAlert));
    }
  }

  // XGram M3E: 48dp star toggles, filled in primary, outlined in onSurfaceVariant; the stars a tap fills pop in on a
  // SPATIAL_FAST spring (snapped with Reduce motion), with a tick

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private static final float XGRAM_CELL = 48f, XGRAM_STAR = 32f;
  private Drawable[] xgramFilled, xgramHollow;
  private int[] xgramTintFilled, xgramTintHollow;
  private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPop;
  private int xgramPopFrom, xgramPopTo;

  private void xgramDraw (Canvas c) {
    if (xgramTintFilled == null) {
      xgramTintFilled = new int[numStars];
      xgramTintHollow = new int[numStars];
    }
    final int cell = Screen.dp(XGRAM_CELL), star = Screen.dp(XGRAM_STAR);
    final int filledColor = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
    final int hollowColor = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    final float pop = xgramPop.getValue();
    final boolean rtl = Lang.rtl();
    for (int i = 0; i < numStars; i++) {
      final boolean filled = i < selectedRating;
      final Drawable d = filled ? xgramFilled[i] : xgramHollow[i];
      if (d == null) {
        continue;
      }
      final int color = filled ? filledColor : hollowColor;
      final int[] tints = filled ? xgramTintFilled : xgramTintHollow;
      if (tints[i] != color) {
        tints[i] = color;
        d.setColorFilter(Paints.getColorFilter(color));
      }
      final int slot = rtl ? numStars - 1 - i : i;
      final float cx = slot * cell + cell / 2f, cy = getMeasuredHeight() / 2f;
      final boolean popping = i >= xgramPopFrom && i < xgramPopTo;
      final float scale = popping ? .6f + .4f * pop : 1f;
      final int saveCount = c.save();
      if (scale != 1f) {
        c.scale(scale, scale, cx, cy);
      }
      d.setBounds((int) (cx - star / 2f), (int) (cy - star / 2f), (int) (cx + star / 2f), (int) (cy + star / 2f));
      d.draw(c);
      c.restoreToCount(saveCount);
    }
  }

  private void xgramSetRating (int rating) {
    final int previous = selectedRating;
    selectedRating = rating;
    // the stars this tap filled pop; going down, only the tapped star does
    xgramPopFrom = rating > previous ? previous : rating - 1;
    xgramPopTo = rating;
    xgramPop.setValue(0f);
    xgramPop.animateTo(1f);
    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    setContentDescription(Lang.getString(R.string.XgramR5CallsStar, rating));
    if (listener != null) {
      listener.onRatingChanged(selectedRating);
    }
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
    if (xgram) {
      setMeasuredDimension(numStars * Screen.dp(XGRAM_CELL), Screen.dp(XGRAM_CELL));
      return;
    }
    setMeasuredDimension(numStars * Screen.dp(32f)+(numStars-1) * Screen.dp(16), Screen.dp(32));
  }

  @Override
  protected void onDraw(Canvas c){
    if (xgram) {
      xgramDraw(c);
      return;
    }
    for(int i=0;i<numStars;i++){
      paint.setColor(Theme.iconColor());
      Drawables.draw(c, i<selectedRating ? filledStar : hollowStar, i*Screen.dp(32+16), 0, Paints.getIconGrayPorterDuffPaint());
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event){
    if (xgram) {
      final int cell = Screen.dp(XGRAM_CELL);
      int slot = (int) (event.getX() / cell);
      if (slot >= 0 && slot < numStars) {
        final int index = Lang.rtl() ? numStars - 1 - slot : slot;
        if (selectedRating != index + 1) {
          xgramSetRating(index + 1);
        }
      }
      return true;
    }
    float offset=Screen.dp(-8);
    for(int i=0;i<numStars;i++){
      if(event.getX()>offset && event.getX()<offset+Screen.dp(32+16)){
        if(selectedRating!=i+1){
          selectedRating=i+1;
          if(listener!=null)
            listener.onRatingChanged(selectedRating);
          invalidate();
          break;
        }
      }
      offset+=Screen.dp(32+16);
    }
    return true;
  }

  public int getRating(){
    return selectedRating;
  }

  public void setOnRatingChangeListener(OnRatingChangeListener l){
    listener=l;
  }

  public interface OnRatingChangeListener{
    void onRatingChanged(int newRating);
  }
}
