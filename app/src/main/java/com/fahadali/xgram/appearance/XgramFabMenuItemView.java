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
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.core.MathUtils;

/**
 * One item of the Material 3 Expressive FAB menu (FloatingActionButtonMenuItem): a 56dp full-corner pill
 * in primaryContainer with a 24dp icon and a titleMedium label, 24dp side padding and an 8dp gap.
 * When it becomes visible its width grows from the end edge (fast spatial spring) with the content
 * anchored to that edge, and it fades in (fast effects spring). No scale.
 */
public class XgramFabMenuItemView extends NoScrollTextView {
  public static final float HEIGHT = 56f;
  public static final float PADDING = 24f;
  public static final float ICON = 24f;
  public static final float ICON_GAP = 8f;
  /** Space between two items. */
  public static final float SPACING = 4f;
  /** Space between the last item and the FAB. */
  public static final float FAB_GAP = 8f;

  private final XgramExpressive.Spring widthSpring, alphaSpring;
  private final RectF pillRect = new RectF();
  private final Path clipPath = new Path();
  private final RippleDrawable ripple;
  private @Nullable Drawable icon;
  private boolean itemVisible;
  private float widthFactor;
  private int textColor, rippleColor;

  public XgramFabMenuItemView (Context context) {
    super(context);
    setSingleLine(true);
    setEllipsize(TextUtils.TruncateAt.END);
    setGravity(Gravity.CENTER_VERTICAL);
    setIncludeFontPadding(false);
    XgramExpressive.applyText(this, XgramExpressive.TS_TITLE_M, false);
    setMinWidth(Screen.dp(HEIGHT));
    updatePaddings();

    textColor = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
    setTextColor(textColor);
    rippleColor = XgramExpressive.withAlpha(textColor, XgramExpressive.STATE_PRESSED);
    ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), new PillDrawable(), null);
    setBackground(ripple);

    setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline (View view, Outline outline) {
        updatePillRect();
        if (pillRect.width() <= 0f || pillRect.height() <= 0f) {
          outline.setEmpty();
          return;
        }
        final float radius = Math.min(pillRect.width(), pillRect.height()) / 2f;
        outline.setRoundRect(Math.round(pillRect.left), Math.round(pillRect.top), Math.round(pillRect.right), Math.round(pillRect.bottom), radius);
      }
    });

    widthSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> setWidthFactor(value)).setThreshold(.002f);
    alphaSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> setAlpha(MathUtils.clamp(value))).setThreshold(.002f);
    setAlpha(0f);
  }

  public void setIcon (@DrawableRes int iconRes) {
    this.icon = iconRes != 0 ? Drawables.get(getResources(), iconRes) : null;
    invalidate();
  }

  /** Paddings follow the layout direction: the icon is leading, the label trailing. */
  public void updatePaddings () {
    final int edge = Screen.dp(PADDING);
    final int iconSpace = Screen.dp(PADDING + ICON + ICON_GAP);
    if (Lang.rtl()) {
      setPadding(edge, 0, iconSpace, 0);
    } else {
      setPadding(iconSpace, 0, edge, 0);
    }
    invalidate();
  }

  public boolean isItemVisible () {
    return itemVisible;
  }

  public void setItemVisible (boolean visible, boolean animated) {
    if (this.itemVisible == visible) {
      return;
    }
    this.itemVisible = visible;
    final float target = visible ? 1f : 0f;
    if (animated) {
      widthSpring.animateTo(target);
      alphaSpring.animateTo(target);
      // Reduce motion snaps the springs
      if (!widthSpring.isRunning()) {
        setWidthFactor(widthSpring.getValue());
      }
      if (!alphaSpring.isRunning()) {
        setAlpha(MathUtils.clamp(alphaSpring.getValue()));
      }
    } else {
      widthSpring.setValue(target);
      alphaSpring.setValue(target);
      setWidthFactor(target);
      setAlpha(target);
    }
  }

  public void cancelAnimations () {
    widthSpring.cancel();
    alphaSpring.cancel();
  }

  private void setWidthFactor (float factor) {
    factor = Math.max(0f, factor);
    if (this.widthFactor != factor) {
      this.widthFactor = factor;
      // Rebuilds the ripple mask too (it is masked by the pill)
      ripple.invalidateSelf();
      invalidate();
      invalidateOutline();
    }
  }

  private void updatePillRect () {
    final float width = getWidth();
    final float height = getHeight();
    final float pillWidth = width * widthFactor;
    if (Lang.rtl()) {
      pillRect.set(0f, 0f, pillWidth, height);
    } else {
      pillRect.set(width - pillWidth, 0f, width, height);
    }
  }

  private void updateColors () {
    final int color = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
    if (color != textColor) {
      textColor = color;
      setTextColor(color);
    }
    final int stateColor = XgramExpressive.withAlpha(color, XgramExpressive.STATE_PRESSED);
    if (stateColor != rippleColor) {
      rippleColor = stateColor;
      ripple.setColor(ColorStateList.valueOf(stateColor));
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN && (!itemVisible || !isEnabled() || getAlpha() == 0f)) {
      return false;
    }
    return super.onTouchEvent(e);
  }

  @Override
  protected void onDraw (Canvas c) {
    updateColors();
    updatePillRect();
    if (pillRect.width() <= 0f) {
      return;
    }
    final float radius = Math.min(pillRect.width(), pillRect.height()) / 2f;
    clipPath.reset();
    clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW);
    final int saveCount = c.save();
    c.clipPath(clipPath);
    super.onDraw(c);
    if (icon != null) {
      final float size = Screen.dpf(ICON);
      final float left = Lang.rtl() ? getWidth() - Screen.dpf(PADDING) - size : Screen.dpf(PADDING);
      final float top = (getHeight() - size) / 2f;
      final int iconWidth = Math.max(1, icon.getMinimumWidth());
      final int iconHeight = Math.max(1, icon.getMinimumHeight());
      c.save();
      c.translate(left, top);
      c.scale(size / iconWidth, size / iconHeight);
      Drawables.draw(c, icon, 0, 0, Paints.getPorterDuffPaint(textColor));
      c.restore();
    }
    c.restoreToCount(saveCount);
  }

  /** The container: a full-corner pill in primaryContainer, as wide as the current width factor, anchored to the end edge. */
  private class PillDrawable extends Drawable {
    @Override
    public void draw (@NonNull Canvas c) {
      updatePillRect();
      if (pillRect.width() <= 0f) {
        return;
      }
      final float radius = Math.min(pillRect.width(), pillRect.height()) / 2f;
      c.drawRoundRect(pillRect, radius, radius, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER)));
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
