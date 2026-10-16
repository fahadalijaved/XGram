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
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.lambda.Destroyable;

public class WallpaperParametersView extends View implements ClickHelper.Delegate, Destroyable, ChatStyleChangeListener {
  private WallpaperParametersListener listener;
  private boolean isInitialBlur;

  private final Paint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final RectF blurRect = new RectF();
  private final ClickHelper helper = new ClickHelper(this);

  private final BoolAnimator isBlurEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (listener != null) listener.onBlurValueAnimated(isInitialBlur ? 1f - factor : factor);
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  private final BoolAnimator isViewShown = new BoolAnimator(1, (id, factor, fraction, callee) -> {
    if (listener != null) listener.onParametersViewScaleChanged(factor);
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  public WallpaperParametersView (Context context) {
    super(context);
    this.textPaint.setColor(Theme.textAccentColor());
    this.textPaint.setTypeface(Fonts.getRobotoRegular());
    this.textPaint.setTextSize(Screen.sp(14f));
    this.isViewShown.setValue(true, false);
    ThemeManager.instance().addChatStyleListener(this);
    setWillNotDraw(false);
  }

  public void initWith (TdApi.Background background, @Nullable WallpaperParametersListener listener) {
    this.isBlurEnabled.setValue(((TdApi.BackgroundTypeWallpaper) background.type).isBlurred, false);
    this.isInitialBlur = this.isBlurEnabled.getValue();
    this.listener = listener;
  }

  public void initWith (@Nullable TGBackground background, @Nullable WallpaperParametersListener listener) {
    this.isBlurEnabled.setValue(background != null && background.isBlurred(), false);
    this.isInitialBlur = this.isBlurEnabled.getValue();
    this.listener = listener;
  }

  public void updateBackground (TGBackground background) {
    this.isBlurEnabled.setValue(background.isBlurred(), true);
    this.isInitialBlur = this.isBlurEnabled.getValue();
  }

  public void setParametersAvailability (boolean value, boolean animate) {
    this.isViewShown.setValue(value, animate);
  }

  public boolean isBlurred () {
    return isBlurEnabled.getValue();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDrawFilterChip(c, getWidth() / 2f, getHeight() / 2f, Lang.getString(R.string.ChatBackgroundBlur), isBlurEnabled.getFloatValue());
      return;
    }
    drawButton(c, getWidth() / 2, getHeight() / 2, blurRect, Lang.getString(R.string.ChatBackgroundBlur), isBlurEnabled);
  }

  // XGram M3E: Blur is an M3 filter chip over the wallpaper preview: 32dp, 8dp corners, labelLarge; unselected an
  // elevated chip (surfaceContainerLow with an outlineVariant border, opaque so it reads over any photo), selected
  // secondaryContainer with a leading 18dp check in onSecondaryContainer. The touch target is 48dp tall.

  private static android.text.TextPaint xgramLabelPaint;
  private final Paint xgramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.Path xgramCheckPath = new android.graphics.Path();

  private static android.text.TextPaint xgramLabelPaint () {
    if (xgramLabelPaint == null) {
      xgramLabelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      xgramLabelPaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L, false));
    }
    xgramLabelPaint.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L)));
    return xgramLabelPaint;
  }

  private void xgramDrawFilterChip (Canvas c, float centerX, float centerY, String text, float selected) {
    final float f = Math.max(0f, Math.min(1f, selected));
    final android.text.TextPaint label = xgramLabelPaint();
    final float textWidth = label.measureText(text);
    final float icon = Screen.dp(18f);
    final boolean rtl = Lang.rtl();
    final float leading = Screen.dp(16f) + icon * f; // 16dp, or 8dp + the check + 8dp
    final float width = leading + textWidth + Screen.dp(16f), height = Screen.dp(32f);
    final float left = centerX - width / 2f, top = centerY - height / 2f;
    blurRect.set(left, centerY - Screen.dp(24f), left + width, centerY + Screen.dp(24f)); // the 48dp touch target
    final RectF chip = Paints.getRectF();
    chip.set(left, top, left + width, top + height);
    final float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_S);
    final int low = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_LOW);
    final int tonal = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
    xgramPaint.setStyle(Paint.Style.FILL);
    xgramPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(low, tonal, f));
    c.drawRoundRect(chip, radius, radius, xgramPaint);
    if (f < 1f) {
      final float stroke = Math.max(1f, Screen.dp(1f));
      xgramPaint.setStyle(Paint.Style.STROKE);
      xgramPaint.setStrokeWidth(stroke);
      xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.OUTLINE_VARIANT), 1f - f));
      chip.inset(stroke / 2f, stroke / 2f);
      c.drawRoundRect(chip, radius, radius, xgramPaint);
    }
    final int content = me.vkryl.core.ColorUtils.fromToArgb(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT),
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER), f);
    if (f > 0f) {
      final float iconLeft = rtl ? left + width - Screen.dp(8f) - icon : left + Screen.dp(8f), iconTop = centerY - icon / 2f;
      xgramCheckPath.rewind();
      xgramCheckPath.moveTo(iconLeft + icon * .18f, iconTop + icon * .53f);
      xgramCheckPath.lineTo(iconLeft + icon * .40f, iconTop + icon * .74f);
      xgramCheckPath.lineTo(iconLeft + icon * .82f, iconTop + icon * .30f);
      xgramPaint.setStyle(Paint.Style.STROKE);
      xgramPaint.setStrokeWidth(Screen.dp(2f));
      xgramPaint.setStrokeCap(Paint.Cap.ROUND);
      xgramPaint.setStrokeJoin(Paint.Join.ROUND);
      xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, f));
      c.drawPath(xgramCheckPath, xgramPaint);
    }
    label.setColor(content);
    c.drawText(text, rtl ? left + Screen.dp(16f) : left + leading, centerY - (label.ascent() + label.descent()) / 2f, label);
  }

  private void drawButton (Canvas c, int centerX, int centerY, RectF buttonRect, String text, BoolAnimator selectAnimator) {
    float textWidth = this.textPaint.measureText(text);
    float checkboxScale = .75f;
    float checkboxSize = (SimplestCheckBox.size() * checkboxScale);
    float offset = (textWidth / 2) - checkboxSize;
    int checkboxX = centerX - (int) (checkboxSize) / 2 - Screen.dp(8f) + (int) (Screen.dp(2f) * checkboxScale) - (int) offset;
    int checkboxY = centerY - (int) (Screen.dp(2f) * checkboxScale);

    buttonRect.top = checkboxY - checkboxSize;
    buttonRect.bottom = checkboxY + checkboxSize;
    buttonRect.left = checkboxX - checkboxSize;
    buttonRect.right = centerX + textWidth + (int) (checkboxSize / 1.5) - offset;
    c.drawRoundRect(buttonRect, Screen.dp(16f), Screen.dp(16f), Paints.fillingPaint(Theme.getColor(ColorId.previewBackground)));

    c.drawText(text, centerX - offset, centerY + Screen.sp(4f), textPaint);

    c.save();
    c.scale(checkboxScale, checkboxScale, checkboxX, centerY);
    c.drawCircle(checkboxX, checkboxY, checkboxSize / 2, Paints.getProgressPaint(Theme.getColor(ColorId.text), Screen.dp(2f)));
    SimplestCheckBox.draw(c, checkboxX, checkboxY, selectAnimator.getFloatValue(), null);
    c.restore();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return helper.onTouchEvent(this, event);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return blurRect.contains(x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (blurRect.contains(x, y)) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // XGram M3E: a tick on the toggle
      }
      isBlurEnabled.toggleValue(true);
      if (listener != null) listener.onBlurValueChanged(isBlurred());
    }
  }

  @Override
  public void performDestroy () {
    ThemeManager.instance().removeChatStyleListener(this);
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) {

  }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageIdentifier) {
    setParametersAvailability(wallpaper != null && wallpaper.isWallpaper(), true);
  }

  public interface WallpaperParametersListener {
    default void onBlurValueAnimated (float factor) {}
    default void onBlurValueChanged (boolean newValue) {}
    default void onParametersViewScaleChanged (float factor) {}
  }
}
