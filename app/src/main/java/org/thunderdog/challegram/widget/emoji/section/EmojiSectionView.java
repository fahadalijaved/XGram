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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget.emoji.section;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

public class EmojiSectionView extends View {
  private int forceWidth = -1;

  public EmojiSectionView (Context context) {
    super(context);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive: the category presses in its 40 x 32dp pill (the active category's shape)
      com.fahadali.xgram.appearance.XgramPillStateLayer.attach(this, new com.fahadali.xgram.appearance.XgramPillStateLayer.Shape() {
        @Override
        public float getStateLayerShape (@NonNull View view, @NonNull RectF outRect) {
          if (section == null || !section.xgramHasPillLook()) {
            outRect.setEmpty();
          } else {
            // Centered where EmojiSection.draw centers it
            final float cx = getMeasuredWidth() / 2, cy = getMeasuredHeight() / 2;
            final float halfWidth = Math.min(Screen.dp(20f), getMeasuredWidth() / 2f), halfHeight = Math.min(Screen.dp(16f), getMeasuredHeight() / 2f);
            outRect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
          }
          return com.fahadali.xgram.appearance.XgramPillStateLayer.CORNER_FULL;
        }

        @Override
        public boolean isStateLayerOnIndicator (@NonNull View view) {
          return section != null && section.xgramIsPillSelected();
        }
      });
    }
  }

  private EmojiSection section;

  public void setSection (EmojiSection section) {
    if (this.section != null) {
      this.section.setCurrentView(null);
    }
    this.section = section;
    if (section != null) {
      section.setCurrentView(this);
    }
  }

  public EmojiSection getSection () {
    return section;
  }

  public void setForceWidth (int width) {
    forceWidth = width;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int itemWidth = forceWidth > 0 ? forceWidth : Screen.dp(44);
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (section != null) {
      section.draw(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    }
  }
}
