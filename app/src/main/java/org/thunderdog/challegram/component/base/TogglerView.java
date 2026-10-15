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
 * File created on 07/08/2015 at 17:08
 */
package org.thunderdog.challegram.component.base;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class TogglerView extends View implements FactorAnimator.Target, TooltipOverlayView.LocationProvider {
  private Paint circlePaint;

  private final BoolAnimator isEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    updateOrigX();
    invalidate();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private BoolAnimator isDisabled;

  private boolean useNegativeState;

  public TogglerView (Context context) {
    super(context);
  }

  public void init (boolean isEnabled) {
    setRadioEnabled(isEnabled, false);
    initDrawingItems();
    if (xgram) {
      // M3E switch: flat, no elevation, outline or background drawable
      buildLayout();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect((int) (origX - origRadius), (int) (origY - origRadius), (int) (origX + origRadius), (int) (origY + origRadius), origRadius);
        }
      });
      setElevation(Math.max(1, Screen.dp(.5f)));
      setTranslationZ(Math.max(1, Screen.dp(.5f)));
      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          final int fromBackgroundColor;
          final int toBackgroundColor;
          if (useNegativeState) {
            fromBackgroundColor = Theme.getColor(ColorId.togglerNegativeBackground);
            toBackgroundColor = Theme.getColor(ColorId.togglerPositiveBackground);
          } else {
            fromBackgroundColor = Theme.togglerInactiveFillingColor();
            toBackgroundColor = Theme.togglerActiveFillingColor();
          }
          final boolean isDisabled = !useNegativeState && TogglerView.this.isDisabled();
          c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, isDisabled ? 0f : TogglerView.this.isEnabled.getFloatValue())));
        }

        @Override
        public void setAlpha (@IntRange(from = 0, to = 255) int i) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
    } else {
      setLayerType(LAYER_TYPE_SOFTWARE, circlePaint);
    }
    buildLayout();
  }

  public TogglerView setUseNegativeState (boolean useNegativeState) {
    if (this.useNegativeState != useNegativeState) {
      this.useNegativeState = useNegativeState;
      if (xgram) {
        xgramUpdateHandle(false);
      }
      invalidate();
    }
    return this;
  }

  private void initDrawingItems () {
    circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    circlePaint.setStyle(Paint.Style.FILL);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      float radius = Math.max(1, Screen.dpf(.5f));
      circlePaint.setShadowLayer(radius, 0f, radius, 0x7a000000);
    }
  }

  private boolean isDisabled () {
    return this.isDisabled != null && this.isDisabled.getValue();
  }

  private static final int ANIMATOR_DISABLE = 1;

  public void setDisabled (boolean isDisabled, boolean animated) {
    if (this.isDisabled != null || isDisabled) {
      if (this.isDisabled == null) {
        this.isDisabled = new BoolAnimator(ANIMATOR_DISABLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 168l);
      }
      this.isDisabled.setValue(isDisabled, animated);
    }
  }

  private boolean showLock;
  private Drawable lockDrawable;

  public void setShowLock (boolean showLock) {
    if (this.showLock != showLock) {
      this.showLock = showLock;
      if (lockDrawable == null) {
        lockDrawable = Drawables.get(getResources(), R.drawable.baseline_lock_14);
      }
      if (xgram) {
        xgramUpdateHandle(false);
      }
      invalidate();
    }
  }

  public void setRadioEnabled (boolean isEnabled, boolean animated) {
    if (xgram) {
      xgramSetChecked(isEnabled, animated);
      return;
    }
    this.isEnabled.setValue(isEnabled, animated);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_DISABLE:
        invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public boolean toggle (boolean animated) {
    if (xgram) {
      final boolean value = !isEnabled.getValue();
      xgramSetChecked(value, animated);
      return value;
    }
    return isEnabled.toggleValue(animated);
  }

  /*public boolean isAnimating () {
    return isAnimating;
  }*/

  public boolean isEnabled () {
    return isEnabled.getValue();
  }

  private RectF fillingRect;
  private float fillRadius;
  private float minX, maxX, diffX, origX, origY, origRadius;

  private int currentWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (currentWidth != width) {
      currentWidth = width;
      buildLayout();
    }
  }

  public void checkRtl (boolean allowLayout) {
    if (Views.setGravity(this, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL) && allowLayout) {
      Views.updateLayoutParams(this);
      if (currentWidth > 0) {
        buildLayout();
        invalidate();
      }
    }
  }

  private void buildLayout () {
    if (fillingRect == null) {
      fillingRect = new RectF();
    }

    int start = Screen.dp(58f);
    int left = Screen.dp(4f);
    int right = left + Screen.dp(34f);

    fillingRect.left = currentWidth - start + left;
    fillingRect.right = currentWidth - start + right;

    int top = Screen.dp(20f);
    int bottom = top + Screen.dp(14f);

    fillingRect.top = top;
    fillingRect.bottom = bottom;

    origRadius = Screen.dpf(10f);
    fillRadius = Screen.dp(7f);
    float offset = Screen.dp(3f);

    minX = fillingRect.left - offset + origRadius;
    maxX = fillingRect.right + offset - origRadius;
    diffX = maxX - minX;
    origY = fillingRect.top - offset + origRadius;
    updateOrigX();
  }

  private void updateOrigX () {
    float factor = this.isEnabled.getFloatValue();
    if (Lang.rtl()) {
      origX = factor == 0f ? maxX : factor == 1f ? minX : maxX - factor * diffX;
    } else {
      origX = factor == 0f ? minX : factor == 1f ? maxX : minX + factor * diffX;
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    final int bound = Screen.dp(2f);
    if (xgram) {
      xgramLayout(getMeasuredWidth(), getMeasuredHeight());
      outRect.set((int) (xgramHandleX - xgramHandleR - bound), (int) (xgramHandleY - xgramHandleR - bound), (int) (xgramHandleX + xgramHandleR + bound), (int) (xgramHandleY + xgramHandleR + bound));
      return;
    }
    outRect.set((int) (origX - origRadius - bound), (int) (origY - origRadius - bound), (int) (origX + origRadius + bound), (int) (origY + origRadius + bound));
  }

  // XGram M3E switch (SwitchTokens): 52x32 full-corner track 16dp from the end, handle 16 (off), 24 (on or with an icon),
  // 28 (pressed). Position and handle size use the fast spatial spring, colors the default effects spring.

  private final boolean xgram = XgramExpressive.isEnabled();
  private XgramExpressive.Spring xgramPos, xgramSize, xgramColor, xgramPress;
  private final RectF xgramTrack = new RectF();
  private float xgramHandleX, xgramHandleY, xgramHandleR;
  private Drawable xgramCheck;

  private void xgramInitSprings () {
    if (xgramPos != null) {
      return;
    }
    final float checked = isEnabled.getValue() ? 1f : 0f;
    final XgramExpressive.Spring.Listener listener = (spring, value) -> invalidate();
    xgramPos = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, checked, listener).setThreshold(.002f);
    xgramSize = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, xgramHandleTargetDp(), listener).setThreshold(.05f);
    xgramColor = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, checked, listener).setThreshold(.002f);
    xgramPress = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, isPressed() ? 1f : 0f, listener).setThreshold(.002f);
  }

  private float xgramHandleTargetDp () {
    if (isPressed()) {
      return 28f;
    }
    return isEnabled.getValue() || showLock || useNegativeState ? 24f : 16f;
  }

  private void xgramSetChecked (boolean checked, boolean animated) {
    this.isEnabled.setValue(checked, false);
    xgramInitSprings();
    final float target = checked ? 1f : 0f;
    if (animated) {
      xgramPos.animateTo(target);
      xgramColor.animateTo(target);
    } else {
      xgramPos.setValue(target);
      xgramColor.setValue(target);
    }
    xgramUpdateHandle(animated);
    invalidate();
  }

  private void xgramUpdateHandle (boolean animated) {
    if (xgramSize == null) {
      return;
    }
    final float size = xgramHandleTargetDp();
    final float press = isPressed() ? 1f : 0f;
    if (animated) {
      xgramSize.animateTo(size);
      xgramPress.animateTo(press);
    } else {
      xgramSize.setValue(size);
      xgramPress.setValue(press);
    }
    invalidate();
  }

  @Override
  public void setPressed (boolean pressed) {
    final boolean changed = isPressed() != pressed;
    super.setPressed(pressed);
    // A row press reaches the switch through ViewGroup.dispatchSetPressed
    if (xgram && changed) {
      xgramInitSprings();
      xgramUpdateHandle(true);
    }
  }

  private void xgramLayout (int width, int height) {
    final boolean rtl = Lang.rtl();
    final float trackWidth = Screen.dpf(52f);
    final float trackHeight = Screen.dpf(32f);
    final float endPadding = Screen.dpf(16f);
    final float right = rtl ? endPadding + trackWidth : width - endPadding;
    final float left = right - trackWidth;
    final float top = height / 2f - trackHeight / 2f;
    xgramTrack.set(left, top, right, top + trackHeight);
    // Handle centers sit 16dp in from the track ends
    final float inset = Screen.dpf(16f);
    final float startX = rtl ? right - inset : left + inset;
    final float endX = rtl ? left + inset : right - inset;
    final float position = xgramPos != null ? xgramPos.getValue() : isEnabled.getValue() ? 1f : 0f;
    xgramHandleX = startX + (endX - startX) * position;
    xgramHandleY = top + trackHeight / 2f;
    final float sizeDp = xgramSize != null ? xgramSize.getValue() : xgramHandleTargetDp();
    xgramHandleR = Math.max(0f, Screen.dpf(sizeDp) / 2f);
  }

  private void xgramDrawIcon (Canvas c, Drawable drawable, int color) {
    if (drawable == null || Color.alpha(color) == 0) {
      return;
    }
    final int width = Math.max(1, drawable.getMinimumWidth());
    final int height = Math.max(1, drawable.getMinimumHeight());
    final float scale = Screen.dpf(16f) / Math.max(width, height);
    c.save();
    c.scale(scale, scale, xgramHandleX, xgramHandleY);
    Drawables.draw(c, drawable, xgramHandleX - width / 2f, xgramHandleY - height / 2f, Paints.getPorterDuffPaint(color));
    c.restore();
  }

  private void xgramDraw (Canvas c) {
    xgramInitSprings();
    xgramLayout(getMeasuredWidth(), getMeasuredHeight());

    final float selected = MathUtils.clamp(xgramColor.getValue());
    final float pressed = MathUtils.clamp(xgramPress.getValue());
    final float disabled = !useNegativeState && this.isDisabled != null ? this.isDisabled.getFloatValue() : 0f;

    final int trackColor, outlineColor, handleColor, iconColor;
    if (useNegativeState) {
      trackColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.togglerNegativeBackground), Theme.getColor(ColorId.togglerPositiveBackground), selected);
      outlineColor = 0;
      handleColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.togglerNegative), Theme.getColor(ColorId.togglerPositive), selected);
      iconColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.togglerNegativeContent), Theme.getColor(ColorId.togglerPositiveContent), selected);
    } else {
      final int highest = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
      final int outline = XgramExpressive.color(XgramExpressive.OUTLINE);
      int trackOn = XgramExpressive.color(XgramExpressive.PRIMARY);
      int trackOff = highest;
      int outlineOff = outline;
      int handleOn = ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.ON_PRIMARY), XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), pressed);
      int handleOff = ColorUtils.fromToArgb(outline, XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), pressed);
      int iconOn = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
      int iconOff = highest;
      if (disabled > 0f) {
        final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
        trackOn = ColorUtils.fromToArgb(trackOn, XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER), disabled);
        trackOff = ColorUtils.fromToArgb(trackOff, XgramExpressive.withAlpha(highest, XgramExpressive.DISABLED_CONTAINER), disabled);
        outlineOff = ColorUtils.fromToArgb(outlineOff, XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER), disabled);
        handleOn = ColorUtils.fromToArgb(handleOn, XgramExpressive.color(XgramExpressive.SURFACE), disabled);
        handleOff = ColorUtils.fromToArgb(handleOff, XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT), disabled);
        iconOn = ColorUtils.fromToArgb(iconOn, XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT), disabled);
        iconOff = ColorUtils.fromToArgb(iconOff, XgramExpressive.withAlpha(highest, XgramExpressive.DISABLED_CONTENT), disabled);
      }
      trackColor = ColorUtils.fromToArgb(trackOff, trackOn, selected);
      outlineColor = ColorUtils.alphaColor(1f - selected, outlineOff);
      handleColor = ColorUtils.fromToArgb(handleOff, handleOn, selected);
      iconColor = ColorUtils.fromToArgb(iconOff, iconOn, selected);
    }

    final float trackRadius = xgramTrack.height() / 2f;
    c.drawRoundRect(xgramTrack, trackRadius, trackRadius, Paints.fillingPaint(trackColor));
    if (Color.alpha(outlineColor) > 0) {
      final float stroke = Screen.dpf(2f);
      final float inset = stroke / 2f;
      final RectF rect = Paints.getRectF();
      rect.set(xgramTrack.left + inset, xgramTrack.top + inset, xgramTrack.right - inset, xgramTrack.bottom - inset);
      c.drawRoundRect(rect, trackRadius - inset, trackRadius - inset, Paints.getProgressPaint(outlineColor, stroke));
    }

    circlePaint.setColor(handleColor);
    c.drawCircle(xgramHandleX, xgramHandleY, xgramHandleR, circlePaint);

    if (showLock) {
      xgramDrawIcon(c, lockDrawable, iconColor);
    } else if (useNegativeState) {
      final Paint strokePaint = Paints.getProgressPaint(iconColor, Screen.dp(2f));
      final float factor = selected;
      final float origX = xgramHandleX, origY = xgramHandleY;
      final int lineWidth = (int) (Math.min(xgramHandleR, Screen.dpf(12f)) * .75f);
      final int linePart = lineWidth / 2;
      final int offsetGlobal = (int) (Screen.dp(.5f) * factor);
      final int offsetGlobalY = (int) (Screen.dp(.5f) * factor);
      final int offsetX1 = (int) (Screen.dp(1.5f) * factor);
      c.drawLine(origX - linePart + offsetX1, origY + linePart + offsetGlobalY, origX + linePart + offsetX1, origY - linePart + offsetGlobalY, strokePaint);
      final int offsetX = (int) (Screen.dp(-3.5f) * factor);
      final int offsetY = (int) (Screen.dp(3f) * factor);
      final int removeLeft = (int) (Screen.dp(.5f) * factor);
      c.drawLine(origX - linePart + offsetX + offsetGlobal + removeLeft, origY - linePart + offsetY + offsetGlobalY + removeLeft, origX + linePart * (1f - factor) + offsetX + offsetGlobal, origY + linePart * (1f - factor) + offsetY + offsetGlobalY, strokePaint);
    } else {
      // Pixel style: a check on the selected handle only, growing in with the handle
      final float grow = MathUtils.clamp((xgramSize.getValue() - 16f) / 8f);
      final float iconAlpha = selected * grow;
      if (iconAlpha > 0f) {
        if (xgramCheck == null) {
          xgramCheck = Drawables.get(getResources(), R.drawable.baseline_check_24);
        }
        xgramDrawIcon(c, xgramCheck, ColorUtils.alphaColor(iconAlpha, iconColor));
      }
    }
  }

  @Override
  public void onDraw (Canvas c) {
    if (xgram) {
      xgramDraw(c);
      return;
    }
    final float factor = this.isEnabled.getFloatValue();
    updateOrigX();
    final int fromBackgroundColor, fromColor;
    final int toBackgroundColor, toColor;
    int checkColor;
    if (useNegativeState) {
      fromBackgroundColor = Theme.getColor(ColorId.togglerNegativeBackground); // ThemeRed.getColor(ColorId.togglerFillingActive);
      fromColor = Theme.getColor(ColorId.togglerNegative); // ThemeRed.getColor(ColorId.togglerActive);
      toBackgroundColor = Theme.getColor(ColorId.togglerPositiveBackground); // ThemeBase.getColor(ColorId.togglerFillingActive);
      toColor = Theme.getColor(ColorId.togglerPositive); // ThemeBase.getColor(ColorId.togglerActive);
      checkColor = ColorUtils.fromToArgb(Theme.getColor(ColorId.togglerNegativeContent), Theme.getColor(ColorId.togglerPositiveContent), isEnabled.getFloatValue());
    } else {
      fromBackgroundColor = Theme.togglerInactiveFillingColor();
      fromColor = Theme.togglerInactiveColor();
      toBackgroundColor = Theme.togglerActiveFillingColor();
      toColor = Theme.togglerActiveColor();
      checkColor = 0;
    }

    final float disableFactor = !useNegativeState && this.isDisabled != null ? this.isDisabled.getFloatValue() : 0f;

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, factor * (1f - disableFactor))));
    }
    circlePaint.setColor(ColorUtils.fromToArgb(fromColor, toColor, factor * (1f - disableFactor)));
    c.drawCircle(origX, origY, origRadius, circlePaint);

    if (showLock) {
      Drawables.draw(c, lockDrawable, origX - lockDrawable.getMinimumWidth() / 2f, origY - lockDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(checkColor));
    } else if (useNegativeState) {
      Paint strokePaint = Paints.getProgressPaint(checkColor, Screen.dp(2f));

      int lineWidth = (int) (origRadius * .75f);
      int linePart = lineWidth / 2;

      /*c.save();
      c.rotate(-90f * (1f - factor), origX, origY);*/

      int offsetGlobal = (int) (Screen.dp(.5f) * factor);
      int offsetGlobalY = (int) (Screen.dp(.5f) * factor);
      int offsetX1 = (int) (Screen.dp(1.5f) * factor);
      c.drawLine(origX - linePart + offsetX1, origY + linePart + offsetGlobalY, origX + linePart + offsetX1, origY - linePart + offsetGlobalY, strokePaint);
      int offsetX = (int) (Screen.dp(-3.5f) * factor);
      int offsetY = (int) (Screen.dp(3f) * factor);
      int removeLeft = (int) (Screen.dp(.5f) * factor);
      c.drawLine(origX - linePart + offsetX + offsetGlobal + removeLeft, origY - linePart + offsetY + offsetGlobalY + removeLeft, origX + linePart * (1f - factor) + offsetX + offsetGlobal, origY + linePart * (1f - factor) + offsetY + offsetGlobalY, strokePaint);

      // c.restore();
    }
  }
}
