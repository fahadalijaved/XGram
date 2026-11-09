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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive actions for selected messages (MessagesController), in the composer's own footprint: while
 * messages are selected the composer row turns into an action pill of the same size, place and shape as its text field
 * (40dp, 20dp corners, surfaceContainerHigh, 8dp from the sides; the send circle's room is the pill's too), the icon
 * buttons spread evenly in it (onSurfaceVariant, a destructive one in error). The row behind it is covered in the
 * surface color, so nothing of the composer shows. It is placed where the composer row is drawn (the host reads that
 * from the row itself on every change: the keyboard, the gap, more lines), never from insets, so it can't be left
 * stretched. It fades in over the composer and out again (EFFECTS_FAST), and takes every touch while it is up.
 */
public final class XgramDockedToolbar extends LinearLayout {
  /** The composer row, and the field in it. */
  private static final float ROW = 49f, FIELD = 40f, SIDE = 8f;

  private final SparseBooleanArray destructive = new SparseBooleanArray();
  private final RectF pill = new RectF();
  private @ColorInt int contentColor, errorColor;
  private boolean shown;
  private int coverTop = -1, coverHeight;

  public XgramDockedToolbar (Context context) {
    super(context);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.BOTTOM);
    setWillNotDraw(false);
    setClickable(true); // the composer under it takes no touch while it is up
    setVisibility(View.GONE);
    setAlpha(0f);
    final int side = Screen.dp(SIDE + 4f);
    setPadding(side, 0, side, 0);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(null); // above the composer's views, without a shadow
      setTranslationZ(Screen.dp(16f));
    }
    contentColor = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    errorColor = XgramExpressive.color(XgramExpressive.ERROR);
  }

  /** An icon button ({@code iconRes} through the icon pack); {@code isDestructive} in error (delete). */
  public View addButton (int id, int iconRes, CharSequence description, View.OnClickListener onClick, boolean isDestructive) {
    final ImageView button = new ImageView(getContext());
    button.setId(id);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setImageResource(XgramIconPack.map(iconRes));
    destructive.put(id, isDestructive);
    final int color = isDestructive ? errorColor : contentColor;
    button.setColorFilter(color);
    button.setContentDescription(description);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      button.setTooltipText(description);
    }
    button.setBackground(XgramExpressiveC.iconButtonRipple(color, 40f));
    button.setOnClickListener(onClick);
    final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Screen.dp(FIELD), 1f);
    params.bottomMargin = Screen.dp((ROW - FIELD) / 2f);
    addView(button, params);
    return button;
  }

  /**
   * Covers the composer from {@code top} (its top in the parent) down {@code height} px: the action pill on the row's
   * last 49dp (where the send button is), the rest covered in the surface color.
   */
  public void setCover (int top, int height) {
    height = Math.max(height, Screen.dp(ROW));
    if (coverHeight != height) {
      coverHeight = height;
      final android.view.ViewGroup.LayoutParams params = getLayoutParams();
      if (params != null && params.height != height) {
        params.height = height;
        setLayoutParams(params);
      }
    }
    if (coverTop != top) {
      coverTop = top;
      setTranslationY(top);
    }
  }

  public boolean isToolbarShown () {
    return shown;
  }

  /** A crossfade with the composer (effects fast). */
  public void setShown (boolean shown, boolean animated) {
    if (this.shown == shown) {
      return;
    }
    this.shown = shown;
    animate().cancel();
    if (shown) {
      if (getVisibility() != View.VISIBLE) {
        setAlpha(0f);
        setVisibility(View.VISIBLE);
      }
      if (!animated) {
        setAlpha(1f);
        return;
      }
      animate()
        .alpha(1f)
        .setStartDelay(0)
        .setDuration(XgramAppMotion.springDuration(XgramExpressive.EFFECTS_FAST))
        .setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_FAST))
        .start();
    } else {
      if (getVisibility() == View.GONE) {
        return;
      }
      if (!animated) {
        setAlpha(0f);
        setVisibility(View.GONE);
        return;
      }
      animate()
        .alpha(0f)
        .setStartDelay(0)
        .setDuration(XgramAppMotion.springDuration(XgramExpressive.EFFECTS_FAST))
        .setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_FAST))
        .withEndAction(() -> {
          if (!this.shown) {
            setVisibility(View.GONE);
          }
        })
        .start();
    }
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    // A leaving pill takes no new touch (it goes to the composer under it); a gesture already on it still ends
    if (!shown && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      return false;
    }
    return super.dispatchTouchEvent(e);
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    // The composer behind it, covered; then the pill in the field's place, across the row
    c.drawRect(0, 0, getWidth(), getHeight(), Paints.fillingPaint(XgramExpressive.color(XgramExpressive.SURFACE)));
    final float rowTop = getHeight() - Screen.dp(ROW);
    final float inset = Screen.dpf((ROW - FIELD) / 2f);
    pill.set(Screen.dpf(SIDE), rowTop + inset, getWidth() - Screen.dpf(SIDE), getHeight() - inset);
    final float radius = pill.height() / 2f;
    c.drawRoundRect(pill, radius, radius, Paints.fillingPaint(XgramM3Composer.fieldColor(false)));
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas c) {
    final int content = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    final int error = XgramExpressive.color(XgramExpressive.ERROR);
    if (content != contentColor || error != errorColor) {
      // A theme change: the icons and their state layers take the new colors
      contentColor = content;
      errorColor = error;
      for (int i = 0; i < getChildCount(); i++) {
        final View child = getChildAt(i);
        if (child instanceof ImageView) {
          final int tint = destructive.get(child.getId()) ? error : content;
          ((ImageView) child).setColorFilter(tint);
          child.setBackground(XgramExpressiveC.iconButtonRipple(tint, 40f));
        }
      }
    }
    super.dispatchDraw(c);
  }
}
