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
 * File created on 25/04/2015 at 07:26
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramExpressiveC;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;

public class HeaderButton extends View {
  public interface OnTouchDownListener {
    void onTouchDown (HeaderButton v, MotionEvent e);
  }

  private Drawable drawable;
  private @DrawableRes int drawableRes;

  protected @ColorId int themeColorId, toThemeColorId;
  protected float colorFactor = -1f;

  private OnTouchDownListener touchDownListener;

  public HeaderButton (Context context) {
    super(context);
    Views.setClickable(this);
  }

  public void setTouchDownListener (OnTouchDownListener listener) {
    this.touchDownListener = listener;
  }

  public HeaderButton setThemeColorId (@ColorId int colorId) {
    this.themeColorId = colorId;
    this.colorFactor = -1f;
    invalidate();
    return this;
  }

  public HeaderButton setThemeColorFactor (float factor) {
    if (this.colorFactor != factor && this.colorFactor != -1f) {
      this.colorFactor = factor;
      invalidate();
    }
    return this;
  }

  public HeaderButton setThemeColorId (@ColorId int fromColorId, @ColorId int toColorId, float factor) {
    this.themeColorId = fromColorId;
    this.toThemeColorId = toColorId;
    this.colorFactor = factor;
    invalidate();
    return this;
  }

  private static boolean checkValidness (View view) {
    return view == null || (view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if ((e.getAction() != MotionEvent.ACTION_DOWN || checkValidness(this))) {
      boolean res = super.onTouchEvent(e);
      if (touchDownListener != null && e.getAction() == MotionEvent.ACTION_DOWN) {
        touchDownListener.onTouchDown(this, e);
      }
      return res;
    }
    return false;
  }

  private int currentResource;

  public void setImageResource (int resource) {
    if (currentResource != resource) {
      currentResource = resource;
      boolean invalidate = drawable != null;
      drawable = resource != 0 ? Drawables.get(getResources(), resource) : null;
      drawableRes = resource;
      if (invalidate) {
        invalidate();
      }
    }
  }

  public void setCustomDrawable (Drawable drawable) {
    this.drawable = drawable;
    this.drawableRes = 0;
  }

  public Drawable getDrawable () {
    return drawable;
  }

  private int lastBgResource;

  // XGram: Material 3 Expressive standard icon button: a 40dp state layer in the icon color that
  // morphs from full to small corners while pressed (spatial fast spring).
  private RippleDrawable xgramRipple;
  private int xgramRippleColor;

  private int xgramContentColor () {
    return getColor(XgramExpressive.surfaceHeader() ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.headerIcon));
  }

  public void setButtonBackground (int resource) {
    if (XgramExpressive.isEnabled()) {
      lastBgResource = resource;
      if (xgramRipple == null) {
        xgramRippleColor = XgramExpressiveC.stateLayerColor(xgramContentColor());
        xgramRipple = XgramExpressiveC.iconButtonRipple(xgramContentColor(), XgramExpressiveC.ICON_BUTTON_BOX);
        setBackground(xgramRipple);
      }
      return;
    }
    if (lastBgResource != resource) {
      lastBgResource = resource;
      setBackgroundResource(resource);
    }
  }

  protected final int getColor (int defaultColor) {
    if (themeColorId == 0) {
      return defaultColor;
    } else if (colorFactor != -1f) {
      return ColorUtils.fromToArgb(Theme.getColor(themeColorId), Theme.getColor(toThemeColorId), colorFactor);
    } else {
      return Theme.getColor(themeColorId);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (xgramRipple != null) {
      int rippleColor = XgramExpressiveC.stateLayerColor(xgramContentColor());
      if (rippleColor != xgramRippleColor) {
        xgramRippleColor = rippleColor;
        xgramRipple.setColor(ColorStateList.valueOf(rippleColor));
      }
    }
    if (progressFactor > 0f) {
      if (progress != null) {
        progress.forceColor(ColorUtils.alphaColor(progressFactor, getColor(Theme.progressColor())));
        progress.draw(c);
      }
    }
    if (drawable != null) {
      if (drawableRes == 0) {
        Drawables.draw(c, drawable, getMeasuredWidth() / 2f - drawable.getMinimumWidth() / 2f, getMeasuredHeight() / 2f - drawable.getMinimumHeight() / 2f, null);
        return;
      }

      Paint paint;
      if (themeColorId == 0) {
        paint = Paints.getBitmapPaint();
      } else {
        paint = Paints.getPorterDuffPaint(getColor(0));
      }
      final boolean needMirror = Drawables.needMirror(drawableRes);
      if (needMirror) {
        c.save();
        c.scale(-1f, 1f, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
      }
      float scale = .8f + .2f * (1f - progressFactor);
      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
      }
      Drawables.draw(c, drawable, getMeasuredWidth() / 2 - drawable.getMinimumWidth() / 2, getMeasuredHeight() / 2 - drawable.getMinimumHeight() / 2, paint);
      if (scale != 1f) {
        c.restore();
      }
      if (needMirror) {
        c.restore();
      }
    }
  }

  // XGram M3E: a dot on the button, as BackHeaderButton draws the ☰'s badge (a 3dp dot in a 4.5dp cutout of the app
  // bar's colour) at the icon's top end corner: the chat list's ⋮ while the side menu is hidden and the accounts are in it

  private @ColorId int xgramDotColorId = ColorId.NONE;
  private BoolAnimator xgramDotAnimator;

  /** {@link ColorId#NONE} hides the dot (it fades out in the colour it had). */
  public void xgramSetDot (@ColorId int colorId, boolean animated) {
    final boolean show = colorId != ColorId.NONE;
    if (show && xgramDotColorId != colorId) {
      xgramDotColorId = colorId;
      invalidate();
    }
    if (xgramDotAnimator == null) {
      if (!show) {
        return;
      }
      xgramDotAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    }
    xgramDotAnimator.setValue(show, animated && isShown());
  }

  @Override
  public void draw (Canvas c) {
    super.draw(c);
    final float visibility = xgramDotAnimator != null ? xgramDotAnimator.getFloatValue() : 0f;
    if (visibility <= 0f || xgramDotColorId == ColorId.NONE || drawable == null) {
      return;
    }
    final boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    final float cx = getMeasuredWidth() / 2f + (rtl ? -Screen.dp(6f) : Screen.dp(6f));
    final float cy = getMeasuredHeight() / 2f - Screen.dp(8f);
    android.view.ViewParent parent = getParent();
    while (parent != null && !(parent instanceof HeaderView)) {
      parent = parent.getParent();
    }
    HeaderFilling filling = parent != null ? ((HeaderView) parent).getFilling() : null;
    if (filling != null) {
      c.drawCircle(cx, cy, Screen.dp(4.5f) * visibility, Paints.fillingPaint(filling.getColor()));
    }
    c.drawCircle(cx, cy, Screen.dp(3f) * visibility, Paints.fillingPaint(ColorUtils.alphaColor(me.vkryl.core.MathUtils.clamp(visibility), Theme.getColor(xgramDotColorId))));
  }

  // Progress

  private BoolAnimator progressAnimator;
  private ProgressComponent progress;
  private float progressFactor;

  public void setShowProgress (boolean showProgress, float startProgress) {
    if (progressAnimator == null) {
      if (!showProgress)
        return;
      progressAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
        if (this.progressFactor != factor) {
          this.progressFactor = factor;
          invalidate();
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    if (progress == null) {
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(15));
      if (startProgress >= 0f) {
        progress.setProgress(startProgress, false);
        progress.setIsPrecise();
      }
      progress.forceColor(ColorUtils.alphaColor(progressFactor, getColor(Theme.progressColor())));
      progress.setUseLargerPaint(Screen.dp(2.5f));
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
      progress.setUseStupidInvalidate();
      progress.setViewProvider(new SingleViewProvider(this));
    }
    progressAnimator.setValue(showProgress, true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (progress != null) {
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }
  }

  public void setCurrentProgress (float progress) {
    if (this.progress != null) {
      this.progress.setProgress(progress, progressFactor > 0f);
    }
  }
}
