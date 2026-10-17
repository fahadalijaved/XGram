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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class MediaBottomBar extends FrameLayoutFix implements GestureDetector.OnGestureListener {
  static class BarItem {
    private final int iconResource;
    private final String name;
    private final int backgroundColorId;
    private final int paddingTop;

    // internal
    private float measuredWidth;
    private Drawable icon;
    private float factor;

    public BarItem (@DrawableRes int icon, int name, int backgroundColorId) {
      this.iconResource = icon;
      this.name = Lang.getString(name);
      this.backgroundColorId = backgroundColorId;
      this.paddingTop = 0;
    }

    public BarItem (@DrawableRes int icon, int name, int backgroundColorId, int paddingTop) {
      this.iconResource = icon;
      this.name = Lang.getString(name);
      this.backgroundColorId = backgroundColorId;
      this.paddingTop = paddingTop;
    }
  }

  interface Callback {
    boolean onBottomPrepareSectionChange (int fromIndex, int toIndex, boolean ignorePermissionsRequest);
    void onBottomFactorChanged (float factor);
    void onBottomSectionChanged (int index);

    void onBottomTopRequested (int currentIndex);
  }

  // Data
  private BarItem[] items;
  private int index;
  private Callback callback;

  // Paints
  // private final TextPaint textPaint;

  // Metrics
  private final int barHeight;
  private final int textY;
  private final int iconActivePadding;

  private int lastBarWidth, lastScreenHeight;
  private float maxTextWidth;
  private int itemNormalWidth;
  private int itemActiveDiff;
  private int itemStartX;
  private float circleRadius;

  private final GestureDetector flingDetector;

  private static final float TEXT_SIZE = 14f;

  public MediaBottomBar (Context context) {
    super(context);

    flingDetector = new GestureDetector(context, this);

    barHeight = getBarHeight();
    iconActivePadding = Screen.dp(10f);
    textY = barHeight - Screen.dp(9f);

    setWillNotDraw(false);
    ViewUtils.setBackground(this, new MediaBottomBarBackground());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM));
  }

  public void setBottomInset (int inset) {
    Views.setPaddingBottom(this, inset);
    Views.setLayoutHeight(this, barHeight + inset);
  }

  // Getters

  public static int getBarHeight () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Screen.dp(XGRAM_BAR_HEIGHT); // XGram: the Material 3 Expressive (flexible) navigation bar
    }
    return Screen.dp(56f);
  }

  /** XGram: the Expressive navigation bar is 64dp tall; the bar items and the selection bar are laid out in it. */
  public static final float XGRAM_BAR_HEIGHT = 64f;

  /** XGram: how far the 56dp selection bar contents move down to stay centred in the taller bar. */
  public static int xgramContentOffset () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? (Screen.dp(XGRAM_BAR_HEIGHT) - Screen.dp(56f)) / 2 : 0;
  }

  public int getCurrentBarWidth () {
    /*if (lastBarWidth != 0 && lastBarWidth == getMeasuredWidth() && false) {
      return lastBarWidth;
    }*/
    return getCurrentBarWidth(Screen.currentWidth());
  }

  /** XGram: the same for a bar {@code barWidth} wide (the sheet kept within its pane, see MediaLayout#xgramPaneView). */
  public int getCurrentBarWidth (int barWidth) {
    int maxItemWidth = Screen.dp(168f);
    int itemWidth = barWidth / items.length;
    return itemWidth > maxItemWidth ? maxItemWidth * items.length : barWidth;
  }

  public int getCurrentIndex () {
    return index;
  }

  // Setters

  public void setItems (BarItem[] items, int currentIndex) {
    float maxTextWidth = 0;
    int index = 0;
    for (BarItem item : items) {
      if (item.name == null || item.name.isEmpty()) {
        throw new IllegalArgumentException("item.itemIconResource == 0 || item.itemName == null || item.itemName.isEmpty()");
      }
      item.measuredWidth = U.measureText(item.name, Paints.getRegularTextPaint(TEXT_SIZE));
      if (item.measuredWidth > maxTextWidth) {
        maxTextWidth = item.measuredWidth;
      }
      item.icon = item.iconResource == 0 ? null : Drawables.get(getResources(), item.iconResource);
      if (index == currentIndex) {
        item.factor = 1f;
      }
      index++;
    }
    this.index = currentIndex;
    this.maxTextWidth = maxTextWidth;
    this.items = items;
    this.lastBarWidth = 0; // Resetting all related metrics
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramLabels = null; // XGram: re-ellipsize for the new items
      if (xgramIndicator == null) {
        xgramIndicator = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, currentIndex, (s, value) -> invalidate());
      } else {
        xgramIndicator.setValue(currentIndex);
      }
    }
  }

  // XGram: Material 3 Expressive (flexible) navigation bar, the M3E ShortNavigationBar with vertical items: a 64dp
  // surfaceContainer bar; each item has 6dp above a 56x32dp full-corner secondaryContainer indicator behind the 24dp
  // icon, 4dp, then a labelMedium label on a 16dp line and 6dp below. Active icon onSecondaryContainer, active label
  // secondary, inactive onSurfaceVariant. The indicator springs between the items (spatial fast). While items are
  // selected the bar becomes the selection toolbar in the same surfaceContainer: the items fade out (no reveal circle)
  // and MediaLayout fades the toolbar in.
  private @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramIndicator;
  private @androidx.annotation.Nullable android.text.TextPaint xgramLabelPaint;
  private @androidx.annotation.Nullable Paint xgramIconPaint, xgramIconActivePaint;
  private int xgramIconColor, xgramIconActiveColor;
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();
  private final Paint.FontMetrics xgramLabelMetrics = new Paint.FontMetrics();
  private @androidx.annotation.Nullable String[] xgramLabels;
  private int xgramLabelsWidth = -1;

  private static boolean xgramM3 () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  }

  private static final float XGRAM_ITEM_TOP = 6f, XGRAM_INDICATOR_WIDTH = 56f, XGRAM_INDICATOR_HEIGHT = 32f,
    XGRAM_ICON_LABEL_SPACE = 4f, XGRAM_LABEL_LINE = 16f;

  /** Labels ellipsized to the item width, rebuilt only when the width or the items change. */
  private String[] xgramLabels (int itemWidth) {
    if (xgramLabels == null || xgramLabels.length != items.length || xgramLabelsWidth != itemWidth) {
      xgramLabels = new String[items.length];
      xgramLabelsWidth = itemWidth;
      final float maxWidth = itemWidth - Screen.dp(8f);
      for (int i = 0; i < items.length; i++) {
        String label = items[i].name;
        if (xgramLabelPaint.measureText(label) > maxWidth) {
          label = android.text.TextUtils.ellipsize(label, xgramLabelPaint, maxWidth, android.text.TextUtils.TruncateAt.END).toString();
        }
        xgramLabels[i] = label;
      }
    }
    return xgramLabels;
  }

  private void xgramDraw (Canvas c, int barWidth, int fullHeight, float itemsAlpha) {
    final int surface = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER);
    c.drawRect(0, 0, barWidth, fullHeight, Paints.fillingPaint(surface));
    final int itemWidth = itemNormalWidth;
    if (itemWidth <= 0 || itemsAlpha <= 0f) {
      return;
    }
    final float position = xgramIndicator != null ? xgramIndicator.getValue() : index;
    final float indicatorTop = Screen.dp(XGRAM_ITEM_TOP), indicatorHeight = Screen.dp(XGRAM_INDICATOR_HEIGHT), indicatorWidth = Screen.dp(XGRAM_INDICATOR_WIDTH);
    final float iconCenterY = indicatorTop + indicatorHeight / 2f;
    float indicatorCx = itemStartX + itemWidth * (position + .5f);
    xgramRect.set(indicatorCx - indicatorWidth / 2f, indicatorTop, indicatorCx + indicatorWidth / 2f, indicatorTop + indicatorHeight);
    c.drawRoundRect(xgramRect, indicatorHeight / 2f, indicatorHeight / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), itemsAlpha)));
    if (xgramPressedIndex >= 0 && xgramPressedIndex < items.length) {
      // The pressed item's state layer in the indicator's shape: its content color at 10%
      final float pressedCx = itemStartX + itemWidth * (xgramPressedIndex + .5f);
      final int content = com.fahadali.xgram.appearance.XgramExpressive.color(xgramPressedIndex == index ? com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE);
      xgramRect.set(pressedCx - indicatorWidth / 2f, indicatorTop, pressedCx + indicatorWidth / 2f, indicatorTop + indicatorHeight);
      c.drawRoundRect(xgramRect, indicatorHeight / 2f, indicatorHeight / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(content, com.fahadali.xgram.appearance.XgramExpressive.STATE_PRESSED * itemsAlpha)));
    }

    if (xgramLabelPaint == null) {
      xgramLabelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      xgramLabelPaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, false));
      xgramLabelPaint.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M)));
      xgramLabelPaint.setTextAlign(Paint.Align.CENTER);
    }
    // The label's line box starts 4dp under the indicator; its baseline centres the text in the 16dp line
    xgramLabelPaint.getFontMetrics(xgramLabelMetrics);
    final float labelTop = indicatorTop + indicatorHeight + Screen.dp(XGRAM_ICON_LABEL_SPACE);
    final float labelY = labelTop + (Screen.dp(XGRAM_LABEL_LINE) - (xgramLabelMetrics.descent - xgramLabelMetrics.ascent)) / 2f - xgramLabelMetrics.ascent;
    final int inactive = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    final int activeIcon = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER);
    final int activeLabel = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY);
    // Two icon paints (inactive, active) crossfaded by alpha, so no color filter is made per frame
    if (xgramIconPaint == null || xgramIconColor != inactive) {
      xgramIconPaint = Paints.createPorterDuffPaint(xgramIconPaint, xgramIconColor = inactive);
    }
    if (xgramIconActivePaint == null || xgramIconActiveColor != activeIcon) {
      xgramIconActivePaint = Paints.createPorterDuffPaint(xgramIconActivePaint, xgramIconActiveColor = activeIcon);
    }
    final String[] labels = xgramLabels(itemWidth);
    for (int i = 0; i < items.length; i++) {
      BarItem item = items[i];
      float cx = itemStartX + itemWidth * (i + .5f);
      final float iconPosition = xgramSwitchTo != -1 ? Math.max(Math.min(index, xgramSwitchTo), Math.min(Math.max(index, xgramSwitchTo), position)) : Math.round(position);
      float selected = me.vkryl.core.MathUtils.clamp(1f - Math.abs(iconPosition - i)); // XGram: the indicator may overshoot, the icons do not
      if (item.icon != null) {
        final float iconX = cx - item.icon.getMinimumWidth() / 2f, iconY = iconCenterY - item.icon.getMinimumHeight() / 2f;
        if (selected < 1f) {
          xgramIconPaint.setAlpha((int) (255f * itemsAlpha)); // XGram: the inactive icon stays opaque under the active one (no see-through dip mid-switch)
          Drawables.draw(c, item.icon, iconX, iconY, xgramIconPaint);
        }
        if (selected > 0f) {
          xgramIconActivePaint.setAlpha((int) (255f * selected * itemsAlpha));
          Drawables.draw(c, item.icon, iconX, iconY, xgramIconActivePaint);
        }
      }
      xgramLabelPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(ColorUtils.fromToArgb(inactive, activeLabel, selected), itemsAlpha));
      c.drawText(labels[i], cx, labelY, xgramLabelPaint);
    }
    xgramIconPaint.setAlpha(255);
    xgramIconActivePaint.setAlpha(255);
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  // Metrics

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateBarMetrics();
  }

  private void updateBarMetrics () {
    int barWidth = getMeasuredWidth();
    if (barWidth <= 0) {
      return;
    }
    if (lastBarWidth == barWidth && lastScreenHeight == Screen.currentHeight()) {
      return;
    }

    lastBarWidth = barWidth;
    lastScreenHeight = Screen.currentHeight();

    int maxItemWidth = Screen.dp(168f);
    int itemWidth = barWidth / items.length;

    int totalWidth;
    if (itemWidth > maxItemWidth) {
      this.itemStartX = (barWidth - maxItemWidth * items.length) / 2;
      totalWidth = maxItemWidth * items.length;
      itemWidth = maxItemWidth;
    } else {
      this.itemStartX = 0;
      totalWidth = barWidth;
    }

    int itemActiveWidth = Math.max(itemWidth, (int) (maxTextWidth + Screen.dp(40f)));
    int itemNormalWidth = (totalWidth - itemActiveWidth) / (items.length - 1);

    this.itemNormalWidth = itemNormalWidth;
    this.itemActiveDiff = itemActiveWidth - itemNormalWidth;
    if (xgramM3()) {
      // XGram: navigation bar items share the width equally (the active item does not grow)
      this.itemNormalWidth = totalWidth / items.length;
      this.itemActiveDiff = 0;
    }

    this.circleRadius = (float) Math.sqrt((barWidth * barWidth) + (barHeight * barHeight)) * .5f;
  }

  // Event handler

  private float touchStartX, touchStartY;
  private int touchIndex = -1, touchCenterX = -1;

  private int findCenterX (int lookupIndex) {
    if (items == null || items.length == 0) {
      return -1;
    }
    int cx = itemStartX;
    int index = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
      if (index == lookupIndex) {
        return cx + itemWidth / 2;
      }
      index++;
      cx += itemWidth;
    }
    return -1;
  }

  private int findTouchIndex (float x) {
    if (items == null || items.length == 0) {
      return -1;
    }
    int cx = itemStartX;
    int index = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
      if (x >= cx && x < cx + itemWidth) {
        return index;
      }
      index++;
      cx +=  itemWidth;
    }
    return -1;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    flingDetector.onTouchEvent(e);

    if (items == null || items.length == 0 || overlayFactor != 0f) {
      return true;
    }

    float x = e.getX();
    float y = e.getY();
    if (xgramM3()) {
      xgramTrackPress(e, x, y);
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchIndex = -1;
        touchCenterX = -1;
        touchStartX = x;
        touchStartY = y;

        int cx = itemStartX;
        int index = 0;
        for (BarItem item : items) {
          int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
          if (x >= cx && x < cx + itemWidth) {
            touchIndex = index;
            touchCenterX = cx + (int) ((float) itemWidth * .5f);
            break;
          }
          index++;
          cx += itemWidth;
        }

        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touchIndex != -1) {
          float xDiff = Math.abs(touchStartX - x);
          float yDiff = Math.abs(touchStartY - y);
          if (Math.max(xDiff, yDiff) > Screen.getTouchSlopBig()) {
            touchIndex = -1;
          }
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        touchIndex = -1;
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (touchIndex != -1 && findTouchIndex(x) == touchIndex) {
          performSelection(touchIndex, false);
          return true;
        }
        break;
      }
    }
    return true;
  }

  // XGram: the pressed item, for its state layer

  private int xgramPressedIndex = -1;

  private void xgramTrackPress (MotionEvent e, float x, float y) {
    int pressed = xgramPressedIndex;
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        pressed = findTouchIndex(x);
        break;
      case MotionEvent.ACTION_MOVE:
        if (pressed != -1 && Math.max(Math.abs(touchStartX - x), Math.abs(touchStartY - y)) > Screen.getTouchSlopBig()) {
          pressed = -1;
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        pressed = -1;
        break;
    }
    if (pressed != xgramPressedIndex) {
      xgramPressedIndex = pressed;
      invalidate();
    }
  }

  // Animation

  public boolean setSelectedIndex (int index) {
   return setSelectedIndex(index, false);
  }

  public boolean setSelectedIndex (int index, boolean ignorePermissionsRequest) {
    int centerX = findCenterX(index);
    if (centerX == -1) {
      return false;
    }
    touchCenterX = centerX;
    touchIndex = index;
    return performSelection(index, ignorePermissionsRequest);
  }

  private boolean isAnimating;
  private BarItem fromItem, toItem;
  private float factor;

  private boolean performSelection (final int index, boolean ignorePermissionsRequest) {
    if (isAnimating) {
      return false;
    }
    ViewUtils.onClick(this);
    if (callback != null) {
      if (this.index == index) {
        callback.onBottomTopRequested(index);
      } else if (!callback.onBottomPrepareSectionChange(this.index, index, ignorePermissionsRequest)) {
        return false;
      }
    }
    if (this.index == index) {
      return false;
    }
    this.isAnimating = true;
    this.fromItem = items[this.index];
    this.toItem = items[index];

    if (xgramM3()) {
      xgramStartSwitch(index); // XGram: one spring drives the page switch and the indicator
      return true;
    }

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        factor = 0f;
        MediaBottomBar.this.index = index;
        if (callback != null) {
          callback.onBottomSectionChanged(index);
        }
        isAnimating = false;
      }
    });
    obj.setDuration(240l);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();
    if (xgramIndicator != null && xgramM3()) {
      xgramIndicator.animateTo(index); // XGram: the indicator springs to the new item
    }

    return true;
  }

  // XGram: Material 3 Expressive tab switch. One spring (standard spatial default) moves the page fraction and the
  // indicator together, so the indicator lands as the incoming page settles. Reduce motion: a 150ms linear crossfade.

  private @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramSwitchSpring;
  private int xgramSwitchTo = -1;

  private void xgramStartSwitch (final int toIndex) {
    xgramSwitchTo = toIndex;
    if (com.fahadali.xgram.appearance.XgramMotion.reduce()) {
      ValueAnimator obj = AnimatorUtils.simpleValueAnimator();
      obj.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
      obj.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          xgramFinishSwitch(toIndex);
        }
      });
      obj.setDuration(150L);
      obj.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
      obj.start();
      return;
    }
    if (xgramSwitchSpring == null) {
      xgramSwitchSpring = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, 0f, (s, value) -> setFactor(value));
    }
    xgramSwitchSpring.setEndListener(() -> xgramFinishSwitch(toIndex));
    xgramSwitchSpring.setValue(0f);
    xgramSwitchSpring.animateTo(1f);
  }

  private void xgramFinishSwitch (int toIndex) {
    if (!isAnimating || xgramSwitchTo != toIndex) {
      return;
    }
    if (fromItem != null) {
      fromItem.factor = 0f;
    }
    if (toItem != null) {
      toItem.factor = 1f;
    }
    factor = 0f;
    MediaBottomBar.this.index = toIndex;
    xgramSwitchTo = -1;
    if (xgramIndicator != null) {
      xgramIndicator.setValue(toIndex);
    }
    invalidate();
    if (callback != null) {
      callback.onBottomSectionChanged(toIndex);
    }
    isAnimating = false;
  }

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      this.fromItem.factor = 1f - factor;
      this.toItem.factor = factor;
      if (xgramSwitchTo != -1 && xgramIndicator != null) {
        xgramIndicator.setValue(index + (xgramSwitchTo - index) * factor); // XGram: on the page switch's spring
      }
      invalidate();
      if (callback != null) {
        callback.onBottomFactorChanged(factor);
      }
    }
  }

  private float overlayFactor, overlayRadius;
  private int overlayCenterX, overlayCenterY;

  public void prepareOverlayAnimation () {
    overlayCenterX = itemStartX;
    overlayCenterY = barHeight / 2 - (int) ((float) (iconActivePadding - items[index].paddingTop) * factor);
    overlayRadius = (float) Math.sqrt((getMeasuredWidth() * getMeasuredWidth()) + (barHeight * barHeight)) * .5f;
    int cx = itemStartX;
    int i = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + Math.round((float) itemActiveDiff * item.factor);
      if (i == index) {
        overlayCenterX = cx + itemWidth / 2;
        break;
      }
      cx += itemWidth;
      i++;
    }
  }

  public void setOverlayFactor (float factor) {
    if (this.overlayFactor != factor) {
      this.overlayFactor = factor;
      invalidate();
    }
  }

  // Drawing

  private static final float TEXT_THRESHOLD = .55f;
  private static final float TEXT_SCALE = .2f;

  private class MediaBottomBarBackground extends Drawable {
    private Paint iconPaint;
    private int iconColor;

    @Override
    public void draw (@NonNull Canvas c) {
      updateBarMetrics();
      int barWidth = getMeasuredWidth();
      if (items == null || items.length == 0 || barWidth == 0) {
        return;
      }

      int centerX = barWidth / 2;
      int centerY = barHeight / 2;
      int barHeight = MediaBottomBar.this.barHeight + MediaBottomBar.this.getPaddingBottom();

      if (xgramM3()) {
        // XGram: the selection toolbar shares the bar's surfaceContainer: the items fade out, no reveal circle
        xgramDraw(c, barWidth, barHeight, 1f - overlayFactor);
        return;
      }
      if (overlayFactor != 1f) {
        int backgroundColor = Theme.getColor(items[index].backgroundColorId);


        if (factor == 0f) {
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(backgroundColor));
        } else {
          int toColor = Theme.getColor(toItem.backgroundColorId);

          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(ColorUtils.compositeColor(backgroundColor, ColorUtils.alphaColor(factor, toColor))));

          float cx = touchCenterX;
          float cy = centerY - (int) ((float) (iconActivePadding - fromItem.paddingTop) * factor);

          float x = cx + (centerX - cx) * factor;
          float y = cy + (centerY - cy) * factor;

          c.drawCircle(x, y, circleRadius * factor, Paints.fillingPaint(toColor));
        }

        int cx = itemStartX;
        for (BarItem item : items) {
          int itemWidth = itemNormalWidth + Math.round((float) itemActiveDiff * item.factor);
          int itemCenterX = cx + itemWidth / 2;

          int iconCenterY = item.factor == 0f ? centerY : centerY - (int) ((float) (iconActivePadding - item.paddingTop) * item.factor);
          int iconAlpha = item.factor == 1f ? 255 : 255 - (int) ((255f * .25f) * (1f - item.factor));
          int color = Theme.getColor(ColorId.attachText);
          Paint bitmapPaint = iconPaint != null && iconColor == color ? iconPaint : (iconPaint = Paints.createPorterDuffPaint(iconPaint, iconColor = color));
          bitmapPaint.setAlpha(iconAlpha);
          if (item.icon != null) {
            Drawables.draw(c, item.icon, itemCenterX - item.icon.getMinimumWidth() / 2, iconCenterY - (int) ((float) item.icon.getMinimumHeight() * .5f), bitmapPaint);
          } else {
            c.drawCircle(itemCenterX, iconCenterY, Screen.dp(12f), Paints.fillingPaint(0xffffffff));
          }

          if (item.factor == 1f) {
            int textX = itemCenterX - (int) (item.measuredWidth * .5f);
            c.drawText(item.name, textX, textY, Paints.getRegularTextPaint(TEXT_SIZE, color));
          } else if (item.factor >= TEXT_THRESHOLD) {
            c.save();
            c.translate(itemCenterX, textY);

            float scale = (1f - TEXT_SCALE) + TEXT_SCALE * item.factor;
            c.scale(scale, scale);

            int textX = -(int) (item.measuredWidth * .5f);
            float textFactor = (item.factor - TEXT_THRESHOLD) / (1f - TEXT_THRESHOLD);

            c.drawText(item.name, textX, 0, Paints.getRegularTextPaint(TEXT_SIZE, ColorUtils.alphaColor(textFactor, color)));
            c.restore();
          }

          cx += itemWidth;
        }
      }

      if (overlayFactor != 0f) {
        final int fillingColor = Theme.fillingColor();
        if (overlayFactor != 1f) {
          c.drawCircle(overlayCenterX + (centerX - overlayCenterX) * overlayFactor, overlayCenterY + (centerY - overlayCenterY) * overlayFactor, overlayRadius * overlayFactor, Paints.fillingPaint(fillingColor));
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(ColorUtils.color((int) (255f * overlayFactor), fillingColor)));
        } else {
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(fillingColor));
        }
      }
    }

    @Override
    public void setAlpha (int alpha) {

    }

    @Override
    public void setColorFilter (ColorFilter colorFilter) {

    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }

  public int getCurrentColor ()  {
    if (xgramM3()) {
      return com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER); // XGram
    }
    int backgroundColor = Theme.getColor(items[index].backgroundColorId);
    if (factor != 0f) {
      int toColor = Theme.getColor(toItem.backgroundColorId);
      backgroundColor = ColorUtils.compositeColor(backgroundColor, ColorUtils.alphaColor(factor, toColor));
    }
    return backgroundColor;
  }

  // Gestures


  @Override
  public boolean onDown (MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return false;
  }

  @Override
  public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    return false;
  }

  @Override
  public void onLongPress (MotionEvent e) {

  }

  private boolean handleFling (boolean left) {
    if (items == null || items.length == 0 || overlayFactor != 0f) {
      return false;
    }
    int toIndex = left ? Math.max(0, index - 1) : Math.min(index + 1, items.length - 1);
    return toIndex != index && setSelectedIndex(toIndex);
  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return Math.abs(velocityX) > Screen.dp(250, 1f) && handleFling(velocityX >= 0);
  }
}
