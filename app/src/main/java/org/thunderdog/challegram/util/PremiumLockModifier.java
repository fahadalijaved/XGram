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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;

public class PremiumLockModifier extends EndIconModifier {
  public PremiumLockModifier () {
    super(R.drawable.baseline_lock_16, ColorId.text);
  }

  // XGram M3E: the lock is a badge: a 20dp tertiaryContainer circle holding the 16dp icon-pack lock in
  // onTertiaryContainer, at the row's end (mirrored in RTL); the tooltip points at it.

  private boolean xgramVisible = true;
  private Drawable xgramLock;

  @Override
  public void setVisible (boolean isVisible) {
    super.setVisible(isVisible);
    this.xgramVisible = isVisible;
  }

  private float xgramCenterX (View view) {
    final float offset = org.thunderdog.challegram.tool.Screen.dp(16f) + org.thunderdog.challegram.tool.Screen.dp(20f) / 2f;
    return org.thunderdog.challegram.core.Lang.rtl() ? offset : view.getWidth() - offset;
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      super.afterDraw(view, c);
      return;
    }
    if (!xgramVisible) {
      return;
    }
    if (xgramLock == null) {
      xgramLock = org.thunderdog.challegram.tool.Drawables.get(view.getResources(), R.drawable.baseline_lock_16);
    }
    final float cx = xgramCenterX(view);
    final float cy = view.getHeight() / 2f;
    c.drawCircle(cx, cy, org.thunderdog.challegram.tool.Screen.dp(10f), org.thunderdog.challegram.tool.Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.TERTIARY_CONTAINER)));
    if (xgramLock != null) {
      org.thunderdog.challegram.tool.Drawables.drawCentered(c, xgramLock, cx, cy, org.thunderdog.challegram.tool.Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY_CONTAINER)));
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      super.getTargetBounds(targetView, outRect);
      return;
    }
    if (xgramVisible) {
      final int half = org.thunderdog.challegram.tool.Screen.dp(10f);
      final int cx = Math.round(xgramCenterX(targetView));
      final int cy = targetView.getHeight() / 2;
      outRect.set(cx - half, cy - half, cx + half, cy + half);
      outRect.inset(0, -org.thunderdog.challegram.tool.Screen.dp(10f));
    } else {
      targetView.getDrawingRect(outRect);
    }
  }
}
