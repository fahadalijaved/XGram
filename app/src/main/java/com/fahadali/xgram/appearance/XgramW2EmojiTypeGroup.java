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
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

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
 * XGram M3E: the Emoji / Stickers / GIFs switch of the emoji panel as one Material 3 Expressive floating toolbar,
 * centred at the top of the panel just under the section tabs, floating over the grids.
 * <ul>
 *   <li>Container: one surfaceContainerHigh pill (full corners), elevation level 3, 4dp padding, so 48dp tall. The
 *   grids start clear of it (EmojiLayout gives their top spacer {@link #CONTENT_CLEARANCE_DP} more).</li>
 *   <li>Segments: 48 x 40dp, 2dp apart, 20dp icons, outlined in onSurfaceVariant at rest.</li>
 *   <li>Selection: a secondaryContainer pill under the selected segment, sliding to a newly selected segment on the
 *   spatial fast spring. Only the two segments of a change crossfade, each on its own effects spring: the outlined
 *   drawing in onSurfaceVariant to the filled one in onSecondaryContainer and back (XgramIconCrossfade, one drawable per
 *   state). A segment the pill slides over stays as it is.</li>
 *   <li>Press: the pressed segment's shape (the pill, or its state layer) springs from full to 8dp corners under the
 *   10% pressed state layer.</li>
 * </ul>
 * It follows the panel header as that slides up ({@link #setFollowY}), and hides on scroll down and comes back on
 * scroll up ({@link #setHidden}, spatial default spring): it slides up behind the section tabs and fades. Nothing is
 * allocated while drawing: the two icon paints are rebuilt only when the theme colors change.
 */
@SuppressLint("ViewConstructor")
public final class XgramW2EmojiTypeGroup extends View {
  public static final int TYPE_EMOJI = 0, TYPE_STICKERS = 1, TYPE_GIFS = 2;
  private static final int COUNT = 3;

  public interface Callback {
    void onEmojiTypeSelected (int type);
  }

  /** The toolbar's height (40dp segments in 4dp padding) and the segment size, in dp. */
  public static final float HEIGHT_DP = 48f, SEGMENT_WIDTH_DP = 48f, SEGMENT_HEIGHT_DP = 40f, PADDING_DP = 4f;
  /** The gap between the section tabs and the toolbar, in dp. */
  public static final float TOP_GAP_DP = 8f;
  /** How much further down the grids start under the toolbar: the gap above it, its height and 4dp under it. */
  public static final float CONTENT_CLEARANCE_DP = TOP_GAP_DP + HEIGHT_DP + 4f;
  private static final float ICON_DP = 20f, PRESSED_CORNER_DP = XgramExpressive.CORNER_S;

  private static final int[] ICONS = {R.drawable.baseline_emoticon_outline_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_24};
  private static final int[] ICONS_CHECKED = {R.drawable.baseline_emoticon_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_filled_24};

  /** One drawable per state, also where both states use the same resource (see XgramIconCrossfade). */
  private final Drawable[] icons = new Drawable[COUNT], checkedIcons = new Drawable[COUNT];
  private final String[] labels = new String[COUNT];
  /** The selection pill's position in segment indexes (0 .. COUNT - 1), springing between them. */
  private final XgramExpressive.Spring indicator;
  /** Each segment's selected state (0 .. 1): its icon and content color. */
  private final XgramExpressive.Spring[] checkSprings = new XgramExpressive.Spring[COUNT];
  private final XgramExpressive.Spring[] pressSprings = new XgramExpressive.Spring[COUNT];
  /** 0 shown, 1 hidden. */
  private final XgramExpressive.Spring hideSpring;

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private @Nullable Paint uncheckedIconPaint, checkedIconPaint;
  private int uncheckedIconColor, checkedIconColor;

  private int selectedType = TYPE_EMOJI;
  private int pressedIndex = -1;
  private float downX, downY;
  private boolean hidden;
  private float hideFactor, followY;
  private @Nullable Callback callback;

  public XgramW2EmojiTypeGroup (@NonNull Context context) {
    super(context);
    for (int i = 0; i < COUNT; i++) {
      icons[i] = Drawables.get(getResources(), ICONS[i]);
      checkedIcons[i] = Drawables.get(getResources(), ICONS_CHECKED[i]);
      checkSprings[i] = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, i == selectedType ? 1f : 0f, (s, value) -> invalidate());
      pressSprings[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    }
    indicator = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, selectedType, (s, value) -> invalidate());
    hideSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyHide(value));
    hideSpring.setThreshold(.002f);
    labels[TYPE_EMOJI] = Lang.getString(R.string.XgramW2EmojiTypeEmoji);
    labels[TYPE_STICKERS] = Lang.getString(R.string.Stickers);
    labels[TYPE_GIFS] = Lang.getString(R.string.GIFs);
    updateDescription();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // Level 3: the floating toolbar's elevation; the shadow follows the pill
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          final int width = view.getWidth(), height = view.getHeight();
          if (width <= 0 || height <= 0) {
            outline.setEmpty();
          } else {
            outline.setRoundRect(0, 0, width, height, height / 2f);
          }
        }
      });
      setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    }
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
    animated = animated && getWidth() > 0;
    if (animated) {
      indicator.animateTo(type);
    } else {
      indicator.setValue(type);
    }
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

  // Placement

  /** The panel header's translation: the toolbar sits under the section tabs, so it moves up with them. */
  public void setFollowY (float y) {
    if (this.followY != y) {
      this.followY = y;
      updatePlacement();
    }
  }

  /** Hides (on scroll down) or shows (on scroll up) the toolbar, on the spatial default spring. */
  public void setHidden (boolean hidden, boolean animated) {
    final float target = hidden ? 1f : 0f;
    if (this.hidden == hidden && (hideSpring.getTarget() == target) && (animated || hideSpring.getValue() == target)) {
      return;
    }
    this.hidden = hidden;
    if (hidden) {
      releasePress();
    }
    if (animated && getWidth() > 0 && getVisibility() == View.VISIBLE) {
      hideSpring.animateTo(target);
    } else {
      hideSpring.setValue(target);
      applyHide(target);
    }
  }

  public boolean isHidden () {
    return hidden;
  }

  private void applyHide (float value) {
    if (this.hideFactor != value) {
      this.hideFactor = value;
      updatePlacement();
    }
  }

  /** Properties only, no layout: it slides up behind the section tabs (the gap and its own height) and fades. */
  private void updatePlacement () {
    final float hide = MathUtils.clamp(hideFactor, -.2f, 1f); // a little past its place on the spring's way back
    setAlpha(1f - MathUtils.clamp(hideFactor));
    setTranslationY(followY - Screen.dp(TOP_GAP_DP + HEIGHT_DP) * hide);
  }

  // Layout

  private int segmentWidth () {
    return Screen.dp(SEGMENT_WIDTH_DP);
  }

  private int segmentHeight () {
    return Screen.dp(SEGMENT_HEIGHT_DP);
  }

  private int padding () {
    return Screen.dp(PADDING_DP);
  }

  private int gap () {
    return Screen.dp(XgramM3Connected.GAP);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = padding() * 2 + segmentWidth() * COUNT + gap() * (COUNT - 1);
    final int height = padding() * 2 + segmentHeight();
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
  }

  /** Left edge of the segment at (fractional) index {@code position}, mirrored under RTL. */
  private float segmentLeft (float position) {
    final float slot = Lang.rtl() ? (COUNT - 1) - position : position;
    return padding() + slot * (segmentWidth() + gap());
  }

  private void segmentRect (float position, RectF out) {
    final float left = segmentLeft(position);
    final float top = (getHeight() - segmentHeight()) / 2f;
    out.set(left, top, left + segmentWidth(), top + segmentHeight());
  }

  private int findIndex (float x, float y) {
    final int slop = Screen.dp(4f);
    if (x < -slop || x > getWidth() + slop || y < -slop || y > getHeight() + slop) {
      return -1;
    }
    // The padding and the gaps belong to the nearer segment, so every touch on the toolbar lands on one
    final float step = segmentWidth() + gap();
    final int slot = MathUtils.clamp((int) Math.floor((x - padding() + gap() / 2f) / step), 0, COUNT - 1);
    return Lang.rtl() ? (COUNT - 1) - slot : slot;
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
    if ((hidden && e.getAction() == MotionEvent.ACTION_DOWN) || hideFactor >= 1f || getAlpha() == 0f || !isEnabled()) {
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

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }

    final int container = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH);
    final int indicatorColor = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
    final int uncheckedContent = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    final int checkedContent = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    if (uncheckedIconPaint == null || uncheckedIconColor != uncheckedContent) {
      uncheckedIconPaint = Paints.createPorterDuffPaint(uncheckedIconPaint, uncheckedIconColor = uncheckedContent);
    }
    if (checkedIconPaint == null || checkedIconColor != checkedContent) {
      checkedIconPaint = Paints.createPorterDuffPaint(checkedIconPaint, checkedIconColor = checkedContent);
    }

    // One container for the three segments
    rect.set(0, 0, width, height);
    fillPaint.setColor(container);
    c.drawRoundRect(rect, height / 2f, height / 2f, fillPaint);

    final float full = segmentHeight() / 2f;
    final float pressedCorner = Math.min(full, Screen.dpf(PRESSED_CORNER_DP));
    final float position = MathUtils.clamp(indicator.getValue(), 0f, COUNT - 1);

    // The selection pill, squashed while the segment under it is pressed
    final int nearest = Math.round(position);
    final float indicatorPress = pressSprings[nearest].getValue();
    final float indicatorCorner = MathUtils.clamp(full + (pressedCorner - full) * indicatorPress, 0f, full);
    segmentRect(position, rect);
    fillPaint.setColor(indicatorColor);
    c.drawRoundRect(rect, indicatorCorner, indicatorCorner, fillPaint);

    final float iconScale = Screen.dpf(ICON_DP) / Screen.dpf(24f);
    for (int i = 0; i < COUNT; i++) {
      final float check = MathUtils.clamp(checkSprings[i].getValue());
      final float press = pressSprings[i].getValue();
      segmentRect(i, rect);

      if (press > 0f) {
        // The pressed state layer in the segment's own shape (content color at 10%)
        final float corner = MathUtils.clamp(full + (pressedCorner - full) * press, 0f, full);
        final int content = ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.ON_SURFACE), checkedContent, check); // M3: onSurface, onSecondaryContainer on the pill
        fillPaint.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * Math.min(1f, press)));
        c.drawRoundRect(rect, corner, corner, fillPaint);
      }

      // Icon: the outlined drawing in onSurfaceVariant crossfades to the filled one in onSecondaryContainer
      final float cx = rect.centerX(), cy = rect.centerY();
      final int save = c.save();
      c.scale(iconScale, iconScale, cx, cy);
      XgramIconCrossfade.draw(c, icons[i], uncheckedIconPaint, checkedIcons[i], checkedIconPaint, cx, cy, check, 1f);
      c.restoreToCount(save);
    }
  }
}
