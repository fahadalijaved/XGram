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
 * File created on 20/03/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class LiveLocationView extends View implements Destroyable {
  private Drawable liveLocationBmp;

  public LiveLocationView (Context context) {
    super(context);
    liveLocationBmp = Drawables.get(getResources(), R.drawable.baseline_location_on_48);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(132f)));
  }

  @Override
  public void performDestroy () {
    if (liveLocationBmp != null) {
      liveLocationBmp = null;
    }
  }

  private long nextScheduleTime;

  @Override
  protected void onDraw (Canvas c) {
    if (liveLocationBmp == null) {
      return;
    }
    if (xgram) {
      xgramDraw(c);
      return;
    }
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;
    Drawables.draw(c, liveLocationBmp, cx - liveLocationBmp.getMinimumWidth() / 2, cy - liveLocationBmp.getMinimumHeight() / 2, Paints.whitePorterDuffPaint());
    long delay = DrawAlgorithms.drawWaves(c, cx, cy - Screen.dp(4f), 0xffffffff, true, nextScheduleTime);
    if (delay != -1) {
      nextScheduleTime = SystemClock.uptimeMillis() + delay;
      postInvalidateDelayed(delay);
    }
  }

  // XGram M3E: the header of the "Share live location" sheet (ViewController#openLiveLocationAlert). No blue band: on
  // the sheet colour, a 96dp nine-sided cookie in primaryContainer holds the location pin in onPrimaryContainer, with
  // Telegram X's broadcast waves in the same colour, and the headline "Share live location" (headlineSmall, onSurface)
  // under it; the explanation follows as the sheet's own supporting text. Laid out in Telegram X's 132dp.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private static final float XGRAM_HERO = 96f, XGRAM_ICON = 40f;
  private final android.graphics.Path xgramShape = new android.graphics.Path();
  private final android.graphics.Matrix xgramMatrix = new android.graphics.Matrix();
  private final android.graphics.Paint xgramPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private @androidx.annotation.Nullable android.text.TextPaint xgramTitlePaint;
  private @androidx.annotation.Nullable String xgramTitle;
  private @androidx.annotation.Nullable Drawable xgramIcon;
  private int xgramShapeWidth = -1;

  @Override
  public void setBackground (Drawable background) {
    // XGram M3E: the header sits on the sheet colour (Telegram X gives it ColorId.file)
    super.setBackground(xgram ? null : background);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setBackgroundDrawable (Drawable background) {
    super.setBackgroundDrawable(xgram ? null : background);
  }

  private void xgramDraw (Canvas c) {
    final int width = getWidth();
    final float cx = width / 2f;
    final float cy = Screen.dpf(XGRAM_HERO) / 2f;
    if (xgramIcon == null) {
      xgramIcon = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_location_on_48));
    }
    if (xgramShapeWidth != width) {
      xgramShapeWidth = width;
      com.fahadali.xgram.appearance.XgramShapes.toPath(com.fahadali.xgram.appearance.XgramShapes.COOKIE_9, xgramShape);
      com.fahadali.xgram.appearance.XgramShapes.fit(xgramShape, cx, cy, Screen.dpf(XGRAM_HERO), 0f, xgramMatrix);
    }
    xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER));
    c.drawPath(xgramShape, xgramPaint);
    final int content = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER);
    final float iconScale = Screen.dpf(XGRAM_ICON) / Math.max(1, xgramIcon.getMinimumWidth());
    final int saveCount = c.save();
    c.scale(iconScale, iconScale, cx, cy);
    com.fahadali.xgram.appearance.XgramR6B7Places.drawIcon(c, xgramIcon, cx, cy, content, 1f);
    c.restoreToCount(saveCount);
    if (!com.fahadali.xgram.appearance.XgramMotion.reduce()) {
      // Telegram X's broadcast waves on both sides of the pin's head, inside the cookie
      long delay = DrawAlgorithms.drawWaves(c, cx, cy - Screen.dp(3f), content, true, nextScheduleTime);
      if (delay != -1) {
        nextScheduleTime = SystemClock.uptimeMillis() + delay;
        postInvalidateDelayed(delay);
      }
    }
    if (xgramTitlePaint == null) {
      xgramTitlePaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      xgramTitlePaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S, false));
      xgramTitlePaint.setTextSize(Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S)));
      xgramTitlePaint.setTextAlign(android.graphics.Paint.Align.CENTER);
    }
    if (xgramTitle == null) {
      xgramTitle = com.fahadali.xgram.appearance.XgramR5Lists.sentence(R.string.ShareLiveLocation, R.string.XgramR6B7ShareLiveLocation);
      final float maxWidth = width - Screen.dpf(48f);
      if (maxWidth > 0 && xgramTitlePaint.measureText(xgramTitle) > maxWidth) {
        xgramTitle = android.text.TextUtils.ellipsize(xgramTitle, xgramTitlePaint, maxWidth, android.text.TextUtils.TruncateAt.END).toString();
      }
    }
    xgramTitlePaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
    // The headline's 32dp line box starts 4dp under the hero and ends at the view's 132dp
    final float lineTop = Screen.dpf(XGRAM_HERO + 4f), lineHeight = Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.lineHeightSp(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S));
    final float baseline = lineTop + lineHeight / 2f - (xgramTitlePaint.ascent() + xgramTitlePaint.descent()) / 2f;
    c.drawText(xgramTitle, cx, baseline, xgramTitlePaint);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    xgramTitle = null; // ellipsized for the new width
  }
}
