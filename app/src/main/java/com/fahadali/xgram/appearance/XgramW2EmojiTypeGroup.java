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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E wave 2: the Emoji / Stickers / GIFs switch of the emoji panel as a Material 3 Expressive connected button
 * group of icon toggle buttons (ButtonGroup, size S), floating at the panel's bottom start like the backspace button at
 * its end.
 * <ul>
 *   <li>Segments: 52 x 40dp (the small icon button, wide), 20dp icons, 2dp apart (ButtonGroupDefaults.ConnectedSpaceBetween).</li>
 *   <li>Shape: full outer corners, 8dp inner corners; the checked segment springs to full corners (checkedShape) and a
 *   pressed one to 4dp (pressedShape), on the spatial fast spring.</li>
 *   <li>Colors: the tonal toggle button, unchecked secondaryContainer / onSecondaryContainer, checked secondary /
 *   onSecondary, crossfaded on the same spring (clamped); a pressed segment adds its content color at 10%.</li>
 *   <li>Icons: outlined when unchecked, filled when checked where a filled drawing exists.</li>
 * </ul>
 * Nothing is allocated while drawing: two icon paints per segment state are rebuilt only when the theme colors change.
 */
@SuppressLint("ViewConstructor")
public final class XgramW2EmojiTypeGroup extends View {
  public static final int TYPE_EMOJI = 0, TYPE_STICKERS = 1, TYPE_GIFS = 2;
  private static final int COUNT = 3;

  public interface Callback {
    void onEmojiTypeSelected (int type);
  }

  public static final float HEIGHT_DP = 40f, SEGMENT_WIDTH_DP = 52f;
  private static final float ICON_DP = 20f, INNER_CORNER_DP = XgramExpressive.CORNER_S, PRESSED_CORNER_DP = XgramExpressive.CORNER_XS;

  private static final int[] ICONS = {R.drawable.baseline_emoticon_outline_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_24};
  private static final int[] ICONS_CHECKED = {R.drawable.baseline_emoticon_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_filled_24};

  private final Drawable[] icons = new Drawable[COUNT], checkedIcons = new Drawable[COUNT];
  private final String[] labels = new String[COUNT];
  private final XgramExpressive.Spring[] checkSprings = new XgramExpressive.Spring[COUNT];
  private final XgramExpressive.Spring[] pressSprings = new XgramExpressive.Spring[COUNT];

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Path path = new Path();
  private final float[] radii = new float[8];
  private @Nullable Paint uncheckedIconPaint, checkedIconPaint;
  private int uncheckedIconColor, checkedIconColor;

  private int selectedType = TYPE_EMOJI;
  private int pressedIndex = -1;
  private float downX, downY;
  private float hideFactor;
  private @Nullable Callback callback;

  public XgramW2EmojiTypeGroup (@NonNull Context context) {
    super(context);
    for (int i = 0; i < COUNT; i++) {
      icons[i] = Drawables.get(getResources(), ICONS[i]);
      checkedIcons[i] = ICONS_CHECKED[i] == ICONS[i] ? icons[i] : Drawables.get(getResources(), ICONS_CHECKED[i]);
      checkSprings[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, i == selectedType ? 1f : 0f, (s, value) -> invalidate());
      pressSprings[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    }
    labels[TYPE_EMOJI] = Lang.getString(R.string.XgramW2EmojiTypeEmoji);
    labels[TYPE_STICKERS] = Lang.getString(R.string.Stickers);
    labels[TYPE_GIFS] = Lang.getString(R.string.GIFs);
    updateDescription();
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public int getSelectedType () {
    return selectedType;
  }

  public void setSelectedType (int type, boolean animated) {
    if (type < 0 || type >= COUNT) {
      return;
    }
    final boolean changed = this.selectedType != type;
    this.selectedType = type;
    for (int i = 0; i < COUNT; i++) {
      final float target = i == type ? 1f : 0f;
      if (animated) {
        checkSprings[i].animateTo(target);
      } else {
        checkSprings[i].setValue(target);
      }
    }
    if (changed) {
      updateDescription();
    }
    invalidate();
  }

  private void updateDescription () {
    setContentDescription(labels[selectedType]);
  }

  /** 0 shown, 1 hidden (it slides down and fades with the panel header). Properties only, no layout. */
  public void setHideFactor (float factor) {
    factor = MathUtils.clamp(factor);
    if (this.hideFactor != factor) {
      this.hideFactor = factor;
      setAlpha(1f - factor);
      setTranslationY(Screen.dp(HEIGHT_DP + 16f) * factor);
      if (factor >= 1f) {
        releasePress();
      }
    }
  }

  // Layout

  private int segmentWidth () {
    return Screen.dp(SEGMENT_WIDTH_DP);
  }

  private int gap () {
    return Screen.dp(XgramM3Connected.GAP);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = getPaddingLeft() + getPaddingRight() + segmentWidth() * COUNT + gap() * (COUNT - 1);
    final int height = getPaddingTop() + getPaddingBottom() + Screen.dp(HEIGHT_DP);
    setMeasuredDimension(width, height);
  }

  /** The segment's slot from the start edge (mirrored under RTL). */
  private int slotOf (int index) {
    return Lang.rtl() ? COUNT - 1 - index : index;
  }

  private void segmentRect (int index, RectF out) {
    final int slot = slotOf(index);
    final float left = getPaddingLeft() + slot * (segmentWidth() + gap());
    final float top = getPaddingTop();
    out.set(left, top, left + segmentWidth(), top + Screen.dp(HEIGHT_DP));
  }

  private int findIndex (float x, float y) {
    if (y < getPaddingTop() - Screen.dp(4f) || y > getPaddingTop() + Screen.dp(HEIGHT_DP) + Screen.dp(4f)) {
      return -1;
    }
    for (int i = 0; i < COUNT; i++) {
      segmentRect(i, rect);
      // The gaps belong to the nearer segment, so every touch on the group lands on one
      if (x >= rect.left - gap() && x < rect.right + gap()) {
        return i;
      }
    }
    return -1;
  }

  // Touch

  private void releasePress () {
    if (pressedIndex != -1) {
      pressSprings[pressedIndex].animateTo(0f);
      pressedIndex = -1;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (hideFactor >= 1f || getAlpha() == 0f || !isEnabled()) {
      return false;
    }
    final float x = e.getX(), y = e.getY();
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        releasePress();
        final int index = findIndex(x, y);
        if (index == -1) {
          return false;
        }
        downX = x;
        downY = y;
        pressedIndex = index;
        pressSprings[index].animateTo(1f);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressedIndex != -1 && Math.max(Math.abs(x - downX), Math.abs(y - downY)) > Screen.getTouchSlop() && findIndex(x, y) != pressedIndex) {
          releasePress();
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        releasePress();
        return true;
      }
      case MotionEvent.ACTION_UP: {
        final int index = pressedIndex;
        releasePress();
        if (index != -1 && findIndex(x, y) == index) {
          ViewUtils.onClick(this);
          if (callback != null) {
            callback.onEmojiTypeSelected(index);
          }
        }
        return true;
      }
    }
    return true;
  }

  // Drawing

  private static float lerp (float from, float to, float factor) {
    return from + (to - from) * factor;
  }

  @Override
  protected void onDraw (Canvas c) {
    final int height = Screen.dp(HEIGHT_DP);
    final float full = height / 2f;
    final float inner = Math.min(full, Screen.dpf(INNER_CORNER_DP));
    final float pressedCorner = Math.min(full, Screen.dpf(PRESSED_CORNER_DP));

    final int uncheckedContainer = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
    final int uncheckedContent = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    final int checkedContainer = XgramExpressive.color(XgramExpressive.SECONDARY);
    final int checkedContent = XgramExpressive.color(XgramExpressive.ON_SECONDARY);
    if (uncheckedIconPaint == null || uncheckedIconColor != uncheckedContent) {
      uncheckedIconPaint = Paints.createPorterDuffPaint(uncheckedIconPaint, uncheckedIconColor = uncheckedContent);
    }
    if (checkedIconPaint == null || checkedIconColor != checkedContent) {
      checkedIconPaint = Paints.createPorterDuffPaint(checkedIconPaint, checkedIconColor = checkedContent);
    }

    final float iconScale = Screen.dpf(ICON_DP) / Screen.dpf(24f);
    for (int i = 0; i < COUNT; i++) {
      segmentRect(i, rect);
      final int slot = slotOf(i);
      final boolean firstSlot = slot == 0, lastSlot = slot == COUNT - 1;
      final float checkRaw = checkSprings[i].getValue();
      final float check = MathUtils.clamp(checkRaw);
      final float press = pressSprings[i].getValue();

      // Corners: resting (outer full, inner 8dp) -> checked (full) -> pressed (4dp); physical order tl, tr, br, bl
      final float restLeft = firstSlot ? full : inner, restRight = lastSlot ? full : inner;
      float left = lerp(restLeft, full, checkRaw);
      float right = lerp(restRight, full, checkRaw);
      left = MathUtils.clamp(lerp(left, pressedCorner, press), 0f, full);
      right = MathUtils.clamp(lerp(right, pressedCorner, press), 0f, full);
      radii[0] = radii[1] = left;
      radii[2] = radii[3] = right;
      radii[4] = radii[5] = right;
      radii[6] = radii[7] = left;
      path.rewind();
      path.addRoundRect(rect, radii, Path.Direction.CW);

      final int container = ColorUtils.fromToArgb(uncheckedContainer, checkedContainer, check);
      final int content = ColorUtils.fromToArgb(uncheckedContent, checkedContent, check);
      fillPaint.setColor(container);
      c.drawPath(path, fillPaint);
      if (press > 0f) {
        fillPaint.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * Math.min(1f, press)));
        c.drawPath(path, fillPaint);
      }

      // Icon: the unchecked drawing in the unchecked color crossfades to the checked drawing in the checked color
      final float cx = rect.centerX(), cy = rect.centerY();
      final int save = c.save();
      c.scale(iconScale, iconScale, cx, cy);
      final Drawable icon = icons[i], checkedIcon = checkedIcons[i];
      if (check < 1f && icon != null) {
        uncheckedIconPaint.setAlpha((int) (255f * (1f - check)));
        Drawables.drawCentered(c, icon, cx, cy, uncheckedIconPaint);
      }
      if (check > 0f && checkedIcon != null) {
        checkedIconPaint.setAlpha((int) (255f * check));
        Drawables.drawCentered(c, checkedIcon, cx, cy, checkedIconPaint);
      }
      c.restoreToCount(save);
    }
    uncheckedIconPaint.setAlpha(255);
    checkedIconPaint.setAlpha(255);
  }
}
