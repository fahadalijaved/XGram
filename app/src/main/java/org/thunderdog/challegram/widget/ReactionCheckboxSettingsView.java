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
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class ReactionCheckboxSettingsView extends LinearLayout implements ThemeInvalidateListener, FactorAnimator.Target, Destroyable, TooltipOverlayView.LocationProvider {
  private final StickerSmallView stickerSmallView;
  private final GifReceiver gifReceiver;
  private final TextView captionTextView;
  private final Counter counter;
  private final BoolAnimator checkedFactor;
  private int number = -2;
  private TGReaction reaction;

  private TGStickerObj activateAnimationSticker;

  private final float lineSize;

  public ReactionCheckboxSettingsView (Context context) {
    this(context, null, 0);
  }

  public ReactionCheckboxSettingsView (Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ReactionCheckboxSettingsView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setOrientation(LinearLayout.VERTICAL);
    setWillNotDraw(false);

    lineSize = Screen.dp(1.5f);

    stickerSmallView = new StickerSmallView(context, Screen.dp(0));
    stickerSmallView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 3));
    stickerSmallView.setIsSuggestion();
    addView(stickerSmallView);

    gifReceiver = new GifReceiver(this);

    captionTextView = new TextView(context);
    captionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    captionTextView.setTextSize(11);
    captionTextView.setMaxLines(2);
    captionTextView.setEllipsize(TextUtils.TruncateAt.END);
    addView(captionTextView, LayoutHelper.createLinear(74, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

    counter = new Counter.Builder()
      .callback(this)
      .noBackground()
      .allBold(true)
      .textSize(9f)
      .build();

    recolor();

    checkedFactor = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165L);
    checkedFactor.setValue(true, false);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramInit();
    }
  }

  // XGram M3E r6: each reaction is a selectable tile inside the grid's surfaceBright group: secondaryContainer while
  // selected, its corners morphing from full to 16dp (SPATIAL_FAST), an 18dp M3 checkbox badge (or the quick
  // reaction's number) ringed in the tile color at the sticker's top end, a labelSmall caption, and a tertiaryContainer
  // lock badge for a premium reaction that cannot be picked. No allocation while drawing.

  private static final float XGRAM_BADGE = 18f, XGRAM_BADGE_RING = 2f, XGRAM_LOCK = 20f;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable xgramTile;
  private @Nullable String xgramNumberText;
  private @Nullable android.text.TextPaint xgramNumberPaint;
  private @Nullable android.graphics.drawable.Drawable xgramLockIcon;
  private boolean xgramLocked;

  private void xgramInit () {
    com.fahadali.xgram.appearance.XgramExpressive.applyText(captionTextView, com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_S, false);
    xgramTile = com.fahadali.xgram.appearance.XgramExpressive.attachPressMorph(this,
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER),
      com.fahadali.xgram.appearance.XgramExpressive.CORNER_FULL, com.fahadali.xgram.appearance.XgramExpressive.CORNER_L);
    xgramTile.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
    xgramTile.setCornerTarget(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L, false);
    xgramNumberPaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    xgramNumberPaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_S, false));
    xgramNumberPaint.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_S)));
    xgramNumberPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
    xgramNumberPaint.setFontFeatureSettings("tnum");
    com.fahadali.xgram.appearance.XgramAppMotion.use(checkedFactor, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, false);
    xgramApplyChecked(checkedFactor.getFloatValue());
  }

  /** A premium reaction this account cannot pick: a lock badge at the sticker's bottom end. */
  public void xgramSetLocked (boolean locked) {
    if (this.xgramLocked != locked) {
      this.xgramLocked = locked;
      invalidate();
    }
  }

  private void xgramApplyChecked (float factor) {
    final float f = me.vkryl.core.MathUtils.clamp(factor);
    if (xgramTile != null) {
      xgramTile.setAlpha(Math.round(255f * f));
    }
    captionTextView.setTextColor(ColorUtils.fromToArgb(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE),
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER), f));
  }

  private void xgramDrawBadges (Canvas c) {
    final float value = checkedFactor.getFloatValue();
    final float f = me.vkryl.core.MathUtils.clamp(value);
    final float stickerTop = Screen.dp(16f), stickerSize = Screen.dp(48f);
    final boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    final float half = getWidth() / 2f;
    final float badge = Screen.dp(XGRAM_BADGE);
    final float cx = rtl ? half - Screen.dp(18f) : half + Screen.dp(18f);
    final float cy = stickerTop + badge / 2f;
    final int tileColor = ColorUtils.fromToArgb(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT),
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), f);
    // the ring in the tile color, so the badge reads over the sticker
    c.drawCircle(cx, cy, badge / 2f + Screen.dp(XGRAM_BADGE_RING), Paints.fillingPaint(tileColor));
    if (number > 0 && xgramNumberText != null && xgramNumberPaint != null) {
      // a quick reaction's place: the number on a primary dot that springs in
      com.fahadali.xgram.appearance.XgramM3Controls.drawCheckbox(c, cx - badge / 2f, cy - badge / 2f, badge, 0f, 0f, 0f, 1f, 1f - f);
      final float scale = Math.max(0f, value);
      if (scale > 0f) {
        c.drawCircle(cx, cy, badge / 2f * scale, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY)));
        xgramNumberPaint.setColor(ColorUtils.alphaColor(f, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY)));
        final android.graphics.Paint.FontMetrics metrics = xgramNumberPaint.getFontMetrics();
        c.drawText(xgramNumberText, cx, cy - (metrics.ascent + metrics.descent) / 2f, xgramNumberPaint);
      }
    } else {
      com.fahadali.xgram.appearance.XgramM3Controls.drawCheckbox(c, cx - badge / 2f, cy - badge / 2f, badge, f, 0f, 0f, 1f, 1f);
    }
    if (xgramLocked) {
      final float lock = Screen.dp(XGRAM_LOCK);
      final float lx = rtl ? half - Screen.dp(18f) : half + Screen.dp(18f);
      final float ly = stickerTop + stickerSize - lock / 2f;
      c.drawCircle(lx, ly, lock / 2f + Screen.dp(XGRAM_BADGE_RING), Paints.fillingPaint(tileColor));
      c.drawCircle(lx, ly, lock / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.TERTIARY_CONTAINER)));
      if (xgramLockIcon == null) {
        xgramLockIcon = org.thunderdog.challegram.tool.Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(org.thunderdog.challegram.R.drawable.baseline_lock_16));
      }
      if (xgramLockIcon != null) {
        org.thunderdog.challegram.tool.Drawables.draw(c, xgramLockIcon, lx - xgramLockIcon.getMinimumWidth() / 2f, ly - xgramLockIcon.getMinimumHeight() / 2f,
          Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY_CONTAINER)));
      }
    }
  }

  public StickerSmallView getStickerSmallView () {
    return stickerSmallView;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    final int bound = Screen.dp(2f);
    outRect.set(
      getMeasuredWidth() / 2 - bound,
      Screen.dp(20) - bound,
      getMeasuredWidth() / 2 + bound,
      Screen.dp(20) + bound);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    if (stickerSmallView.onTouchEvent(event)) {
      return true;
    }
    return super.onTouchEvent(event);
  }

  public void recolor () {
    captionTextView.setTextColor(Theme.getColor(ColorId.text));
    captionTextView.setHighlightColor(Theme.getColor(ColorId.text));
    if (xgramTile != null) {
      xgramApplyChecked(checkedFactor.getFloatValue()); // XGram M3E r6: onSurface / onSecondaryContainer
    }
  }

  public void attach () {
    stickerSmallView.attach();
  }

  public void detach () {
    stickerSmallView.detach();
  }

  public void setCaptionText (CharSequence text) {
    captionTextView.setText(text);
  }

  public void init (Tdlib tdlib) {
    stickerSmallView.init(tdlib);
  }

  public void setReaction (TGReaction reaction) {
    this.reaction = reaction;
    activateAnimationSticker = reaction.centerAnimationSicker();
    if (activateAnimationSticker.getPreviewAnimation() != null) {
      if (!activateAnimationSticker.isCustomReaction()) {
        activateAnimationSticker.getPreviewAnimation().setPlayOnce(true);
      }
    }
    stickerSmallView.setSticker(activateAnimationSticker);
    setCaptionText(reaction.getTitle());
  }

  public void setChecked (boolean checked, boolean animated) {
    this.setNumber(checked ? 0 : -1, animated);
  }

  public void setNumber (int number, boolean animated) {
    boolean checked = number >= 0;
    if (number == this.number) {
      return;
    }

    this.number = number;
    if (xgramTile != null) {
      // XGram M3E r6: the tile squares to 16dp while selected, back to full corners otherwise
      xgramNumberText = number > 0 ? Integer.toString(number) : null;
      xgramTile.setCornerTarget(checked ? com.fahadali.xgram.appearance.XgramExpressive.CORNER_L : com.fahadali.xgram.appearance.XgramExpressive.CORNER_FULL, animated);
    }

    if (!animated) {
      counter.setCount(Math.max(0, number), false);
      checkedFactor.setValue(checked, false);
      return;
    }

    counter.setCount(Math.max(0, number), true);
    checkedFactor.setValue(checked, true);

    if (checked && animated) {
      if (activateAnimationSticker.getPreviewAnimation() != null) {
        activateAnimationSticker.getPreviewAnimation().setLooped(false);
      }
      stickerSmallView.invalidate();
    }
  }

  public TGStickerObj getSticker () {
    return stickerSmallView.getSticker();
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);
    if (xgramTile != null) {
      xgramDrawBadges(c); // XGram M3E r6
      return;
    }

    c.save();

    float cx = getWidth() / 2f + Screen.dp(10);
    float cy = Screen.dp(47 + 6);
    float r1 = Screen.dp(7.5f);
    float r2 = Screen.dp(6f);

    c.drawCircle(cx, cy, r1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.fillingColor())));
    c.drawCircle(cx, cy, r2, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioFillingColor())));

    if (counter.getVisibility() > 0f) {
      counter.draw(c, cx, cy, Gravity.CENTER, counter.getVisibility());
    } else {
      float x1 = cx - Screen.dp(1);
      float y1 = cy + Screen.dp(3.5f);
      float w2 = Screen.dp(8f) * checkedFactor.getFloatValue();
      float h1 = Screen.dp(4f) * checkedFactor.getFloatValue();

      c.rotate(-45f, x1, y1);
      c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
      c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
    }

    c.restore();
  }

  @Override
  public void performDestroy () {
    stickerSmallView.performDestroy();
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (xgramTile != null) {
      // XGram M3E r6: the spring may overshoot; alpha and colors take it clamped
      final float clamped = me.vkryl.core.MathUtils.clamp(factor);
      stickerSmallView.setAlpha(clamped / 2f + 0.5f);
      captionTextView.setAlpha(clamped / 2f + 0.5f);
      xgramApplyChecked(factor);
      invalidate();
      return;
    }
    stickerSmallView.setAlpha(factor / 2f + 0.5f);
    captionTextView.setAlpha(factor / 2f + 0.5f);
    invalidate();
  }
}
