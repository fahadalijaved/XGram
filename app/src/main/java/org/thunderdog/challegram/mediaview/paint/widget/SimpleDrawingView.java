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
 * File created on 11/06/2017
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.tool.Screen;

public class SimpleDrawingView extends View implements PaintState.SimpleDrawingChangeListener {
  private PaintState state;

  public SimpleDrawingView (Context context) {
    super(context);
  }

  public void setPaintState (PaintState state) {
    if (this.state != null) {
      this.state.removeSimpleDrawingChangeListener(this);
    }
    this.state = state;
    if (state != null) {
      state.addSimpleDrawingChangeListener(this);
    }
    invalidate();
  }

  @Override
  public void onSimpleDrawingsChanged (PaintState state) {
    if (this.state == state) {
      invalidate();
    }
  }

  // XGram: the photo under the drawing, for the blur and pixelate brushes (mirrored as it is shown under this view)

  private @Nullable Bitmap baseBitmap;
  private boolean baseMirrorHorizontally, baseMirrorVertically;

  public void setBaseBitmap (@Nullable Bitmap bitmap, boolean mirrorHorizontally, boolean mirrorVertically) {
    if (this.baseBitmap != bitmap || this.baseMirrorHorizontally != mirrorHorizontally || this.baseMirrorVertically != mirrorVertically) {
      this.baseBitmap = bitmap;
      this.baseMirrorHorizontally = mirrorHorizontally;
      this.baseMirrorVertically = mirrorVertically;
      invalidate();
    }
  }

  // XGram: the eraser's ring under the finger while it erases

  private float eraserX, eraserY, eraserRadius;
  private Paint eraserPaint;

  public void setEraser (float x, float y, float radius) {
    if (this.eraserX != x || this.eraserY != y || this.eraserRadius != radius) {
      this.eraserX = x;
      this.eraserY = y;
      this.eraserRadius = radius;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (state != null) {
      // a recycled base still finds its copies, made while it was there (XgramPaintBase)
      state.draw(c, 0, 0, getMeasuredWidth(), getMeasuredHeight(), baseBitmap, baseMirrorHorizontally, baseMirrorVertically);
    }
    if (eraserRadius > 0f) {
      if (eraserPaint == null) {
        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setStyle(Paint.Style.STROKE);
      }
      // a white ring with a dark edge, its lines at their width on screen whatever the zoom
      final float scale = Math.max(.01f, screenScale);
      eraserPaint.setStrokeWidth(Screen.dp(3f) / scale);
      eraserPaint.setColor(0x40000000);
      c.drawCircle(eraserX, eraserY, eraserRadius, eraserPaint);
      eraserPaint.setStrokeWidth(Screen.dp(1.5f) / scale);
      eraserPaint.setColor(0xffffffff);
      c.drawCircle(eraserX, eraserY, eraserRadius, eraserPaint);
    }
    if (heldLayer != null) {
      heldLayer.drawFrame(c, getMeasuredWidth(), getMeasuredHeight(), screenScale, com.fahadali.xgram.appearance.XgramExpressive.isEnabled());
    }
  }

  // XGram: the frame around the layer (text, emoji, sticker) a finger holds

  private @Nullable com.fahadali.xgram.editor.XgramLayer heldLayer;

  public void setHeldLayer (@Nullable com.fahadali.xgram.editor.XgramLayer layer) {
    if (this.heldLayer != layer) {
      this.heldLayer = layer;
      invalidate();
    }
  }

  private float screenScale = 1f;

  /** How much the view is scaled on screen (the crop and the zoom), for the eraser ring's line width. */
  public void setScreenScale (float scale) {
    this.screenScale = scale;
  }
}
