/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.chats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

/**
 * XGram: a folder's tag color, as Telegram's folder editor offers it: "none" and the 7 built-in name colors as
 * swatches in one row, the chosen one ringed. Its colors are the ones the chat list draws the tags with
 * ({@code tdlib.accentColor(colorId).getNameColor()}), read when it draws, so they follow the theme.
 */
public class XgramFolderColorRow extends View {
  /** Telegram's folder tag colors: -1 (none) and the built-in accent colors 0..6. */
  public static final int[] COLOR_IDS = {-1, 0, 1, 2, 3, 4, 5, 6};

  public interface Listener {
    void onColorPicked (XgramFolderColorRow row, int colorId);
  }

  private final Tdlib tdlib;
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int selectedColorId = -1;
  private boolean locked;
  private @Nullable Listener listener;
  private float downX, downY;

  public XgramFolderColorRow (@NonNull Context context, @NonNull Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    stroke.setStyle(Paint.Style.STROKE);
    Views.setClickable(this);
    setContentDescription(Lang.getString(R.string.XgramFolderTagColor));
  }

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  public void setSelectedColorId (int colorId) {
    if (this.selectedColorId != colorId) {
      this.selectedColorId = colorId;
      invalidate();
    }
  }

  /** Without Telegram Premium the swatches are dimmed; a tap still reaches the listener, which explains why. */
  public void setLocked (boolean locked) {
    if (this.locked != locked) {
      this.locked = locked;
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), Screen.dp(64f));
  }

  private float slotWidth () {
    return (getWidth() - Screen.dp(16f) * 2) / (float) COLOR_IDS.length;
  }

  private float centerX (int index) {
    final int slot = Lang.rtl() ? COLOR_IDS.length - 1 - index : index;
    return Screen.dp(16f) + slotWidth() * (slot + .5f);
  }

  private int colorOf (int colorId) {
    return colorId < 0 ? Theme.getColor(ColorId.textLight) : tdlib.accentColor(colorId).getNameColor();
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final float cy = getHeight() / 2f;
    final float radius = Math.min(Screen.dp(16f), slotWidth() / 2f - Screen.dp(6f));
    final int alpha = locked ? 97 /* .38 */ : 255;
    for (int index = 0; index < COLOR_IDS.length; index++) {
      final int colorId = COLOR_IDS[index];
      final float cx = centerX(index);
      final int color = colorOf(colorId);
      if (colorId < 0) {
        // "None": an outlined circle crossed by a line
        stroke.setColor(color);
        stroke.setAlpha(alpha);
        stroke.setStrokeWidth(Screen.dp(1.5f));
        c.drawCircle(cx, cy, radius - Screen.dp(1f), stroke);
        final float d = (radius - Screen.dp(1f)) * .7071f;
        c.drawLine(cx - d, cy + d, cx + d, cy - d, stroke);
      } else {
        fill.setColor(color);
        fill.setAlpha(alpha);
        c.drawCircle(cx, cy, colorId == selectedColorId ? radius - Screen.dp(5f) : radius, fill);
      }
      if (colorId == selectedColorId) {
        // The chosen swatch: a ring in its own color, with a gap
        stroke.setColor(colorId < 0 ? Theme.getColor(ColorId.text) : color);
        stroke.setAlpha(alpha);
        stroke.setStrokeWidth(Screen.dp(2.5f));
        c.drawCircle(cx, cy, radius + Screen.dp(1.5f), stroke);
      }
    }
  }

  private int indexAt (float x) {
    final float slot = slotWidth();
    if (slot <= 0) {
      return -1;
    }
    int index = (int) Math.floor((x - Screen.dp(16f)) / slot);
    if (index < 0 || index >= COLOR_IDS.length) {
      return -1;
    }
    return Lang.rtl() ? COLOR_IDS.length - 1 - index : index;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        downX = e.getX();
        downY = e.getY();
        return true;
      case MotionEvent.ACTION_UP: {
        final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if (Math.abs(e.getX() - downX) <= slop && Math.abs(e.getY() - downY) <= slop) {
          final int index = indexAt(e.getX());
          if (index != -1 && listener != null) {
            org.thunderdog.challegram.tool.UI.hapticVibrate(this, false);
            listener.onColorPicked(this, COLOR_IDS[index]);
          }
        }
        return true;
      }
    }
    return super.onTouchEvent(e);
  }
}
