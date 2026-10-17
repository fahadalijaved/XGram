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
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E: a toggle of the call screen's row (mute, speaker or flip, video, message, and the answer-with-video
 * toggle), XgramM3CallButton's STYLE_TOGGLE in three size tiers, so four toggles fit on the 360dp cover:
 * <ul>
 * <li>{@link #TIER_REGULAR} (a slot of 88dp or more): a 72dp box, 28dp icon, selected corner 20dp, pressed 16dp.</li>
 * <li>{@link #TIER_COMPACT} (72 to 88dp): 64dp, 24dp icon, corners 16 / 12dp.</li>
 * <li>{@link #TIER_DENSE} (under 72dp): 56dp, 24dp icon, corners 16 / 12dp.</li>
 * </ul>
 * Unselected it is a tonal secondaryContainer circle, selected a rounded square in primary / onPrimary; a slash can be
 * drawn across the icon while it is on (mute) or off (the crossed camera of video), the disabled look stays tappable
 * (it explains why), the label turns white over video (the media factor), and the icon can spin to another one (the
 * flip), drawing each icon instance at most once per frame. The view is as wide as its slot (the whole slot takes the
 * touch); the box is centered in it. No allocation per frame. Main thread only.
 */
public final class XgramM3CallVideoToggle extends View {
  public static final int TIER_REGULAR = 0, TIER_COMPACT = 1, TIER_DENSE = 2;
  public static final int CROSS_NONE = 0, CROSS_WHEN_CHECKED = 1, CROSS_WHEN_UNCHECKED = 2;

  private static final float[] BOX = {72f, 64f, 56f};
  private static final float[] ICON = {28f, 24f, 24f};
  private static final float[] SELECTED_CORNER = {20f, XgramExpressive.CORNER_L, XgramExpressive.CORNER_L};
  private static final float[] PRESSED_CORNER = {16f, XgramExpressive.CORNER_M, XgramExpressive.CORNER_M};
  private static final float SIDE_PADDING = 8f, LABEL_GAP = 8f, LABEL_LINE = 16f, LABEL_INSET = 8f;

  private int tier = TIER_REGULAR;

  private @DrawableRes int iconRes;
  private @Nullable Drawable icon, iconSelected;
  private int iconColor, iconSelectedColor;
  private int crossMode = CROSS_NONE;
  private boolean disabledLook;
  private float mediaFactor;

  // Spin: the icon turns on its vertical axis into another one (two separate instances)
  private @DrawableRes int spinFromRes, spinToRes;
  private @Nullable Drawable spinFrom, spinTo;
  private int spinFromColor, spinToColor;
  private final XgramExpressive.Spring spin;

  private @Nullable String label, labelDrawn;
  private float labelWidth;
  private int labelMeasuredFor = -1;
  private final TextPaint labelPaint;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  private final XgramExpressive.Spring pressSpring, selectSpring;
  private boolean checked;
  private boolean touchable = true;

  public XgramM3CallVideoToggle (Context context) {
    super(context);
    fill.setStyle(Paint.Style.FILL);
    labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    labelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
    labelPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      labelPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M));
    }
    pressSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    selectSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    spin = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 1f, (s, value) -> invalidate());
    spin.setEndListener(this::finishSpin);
    setClickable(true);
    setFocusable(true);
  }

  // Content

  public void setIcon (@DrawableRes int res) {
    if (spinTo != null) {
      if (res == spinToRes) {
        return; // already turning into it
      }
      spin.setValue(1f);
      finishSpin();
    }
    if (this.iconRes == res && icon != null) {
      return;
    }
    this.iconRes = res;
    // Through the icon pack, as every glyph with Expressive design
    this.icon = res != 0 ? Drawables.get(XgramIconPack.map(res)) : null;
    this.iconSelected = res != 0 ? Drawables.get(XgramIconPack.map(res)) : null;
    this.iconColor = 0;
    this.iconSelectedColor = 0;
    invalidate();
  }

  public @DrawableRes int getIconRes () {
    return spinToRes != 0 ? spinToRes : iconRes;
  }

  /**
   * Turns the icon on its vertical axis into {@code res} (EFFECTS_DEFAULT; instant with Reduce motion): the current
   * icon until half way, the new one after. Spinning again from the middle starts from the icon on screen.
   */
  public void spinTo (@DrawableRes int res) {
    if (res == 0 || res == getIconRes()) {
      return;
    }
    if (!laidOut() || XgramMotion.reduce()) {
      setIcon(res);
      return;
    }
    // From the icon on screen (a spin can start again from the middle of another one)
    final int fromRes = spinTo != null ? (spin.getValue() >= .5f ? spinToRes : spinFromRes) : iconRes;
    spinFromRes = fromRes;
    spinFrom = Drawables.get(XgramIconPack.map(fromRes));
    spinFromColor = 0;
    spinToRes = res;
    spinTo = Drawables.get(XgramIconPack.map(res));
    spinToColor = 0;
    spin.setValue(0f);
    spin.animateTo(1f);
    invalidate();
  }

  private boolean laidOut () {
    return getWidth() > 0 && getHeight() > 0;
  }

  private void finishSpin () {
    if (spinTo == null) {
      return;
    }
    final int res = spinToRes;
    spinFrom = spinTo = null;
    spinFromRes = spinToRes = 0;
    if (res != 0 && res != iconRes) {
      this.iconRes = res;
      this.icon = Drawables.get(XgramIconPack.map(res));
      this.iconSelected = Drawables.get(XgramIconPack.map(res));
      this.iconColor = 0;
      this.iconSelectedColor = 0;
    }
    invalidate();
  }

  /** {@link #CROSS_WHEN_CHECKED}: the muted microphone; {@link #CROSS_WHEN_UNCHECKED}: the camera that is off. */
  public void setCrossMode (int mode) {
    if (this.crossMode != mode) {
      this.crossMode = mode;
      invalidate();
    }
  }

  /** The disabled look (the other side cannot do it); the button stays tappable, so it can explain. */
  public void setDisabledLook (boolean disabled) {
    if (this.disabledLook != disabled) {
      this.disabledLook = disabled;
      this.iconColor = this.iconSelectedColor = this.spinFromColor = this.spinToColor = 0;
      invalidate();
    }
  }

  /** 0: the label in onSurface, 1: white (over video). */
  public void setMediaFactor (float factor) {
    factor = MathUtils.clamp(factor);
    if (this.mediaFactor != factor) {
      this.mediaFactor = factor;
      invalidate();
    }
  }

  public void setTier (int tier) {
    if (this.tier != tier) {
      this.tier = tier;
      labelMeasuredFor = -1;
      requestLayout();
      invalidate();
    }
  }

  /** The label under the box (also the content description); null for none. */
  public void setLabel (@Nullable CharSequence label) {
    String text = label != null ? label.toString() : null;
    if (TextUtils.equals(this.label, text)) {
      return;
    }
    boolean hadLabel = this.label != null;
    this.label = text;
    setContentDescription(text);
    labelMeasuredFor = -1;
    measureLabel();
    if (hadLabel != (text != null)) {
      requestLayout();
    }
    invalidate();
  }

  /** The selected (on) state; springs the shape and the colors. */
  public void setChecked (boolean checked, boolean animated) {
    if (this.checked == checked && selectSpring.getTarget() == (checked ? 1f : 0f)) {
      return;
    }
    this.checked = checked;
    setSelected(checked);
    if (animated && laidOut()) {
      selectSpring.animateTo(checked ? 1f : 0f);
    } else {
      selectSpring.setValue(checked ? 1f : 0f);
      invalidate();
    }
  }

  public boolean isChecked () {
    return checked;
  }

  /** False while the button fades out (or the chrome hides over video), so it takes no taps. */
  public void setTouchable (boolean touchable) {
    this.touchable = touchable;
    if (!touchable && isPressed()) {
      setPressed(false);
    }
  }

  // Colors

  @ColorInt
  private int containerColor (float selected) {
    if (disabledLook) {
      // Opaque, so the slash's gap still cuts through
      return androidx.core.graphics.ColorUtils.compositeColors(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.DISABLED_CONTAINER), XgramExpressive.color(XgramExpressive.SURFACE));
    }
    return ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.color(XgramExpressive.PRIMARY), selected);
  }

  @ColorInt
  private int contentColor (boolean selected) {
    if (disabledLook) {
      return XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.DISABLED_CONTENT);
    }
    return XgramExpressive.color(selected ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_SECONDARY_CONTAINER);
  }

  private void tintIcons () {
    final int off = contentColor(false);
    if (icon != null && iconColor != off) {
      iconColor = off;
      icon.setColorFilter(Paints.getColorFilter(off));
    }
    if (iconSelected != null) {
      final int on = contentColor(true);
      if (iconSelectedColor != on) {
        iconSelectedColor = on;
        iconSelected.setColorFilter(Paints.getColorFilter(on));
      }
    }
    if (spinFrom != null && spinFromColor != off) {
      spinFromColor = off;
      spinFrom.setColorFilter(Paints.getColorFilter(off));
    }
    if (spinTo != null && spinToColor != off) {
      spinToColor = off;
      spinTo.setColorFilter(Paints.getColorFilter(off));
    }
  }

  // Layout

  private int boxPx () {
    return Screen.dp(BOX[tier]);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = boxPx() + Screen.dp(SIDE_PADDING) * 2;
    int height = boxPx() + (label != null ? Screen.dp(LABEL_GAP) + Screen.dp(LABEL_LINE) : 0);
    setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    labelMeasuredFor = -1;
    measureLabel();
  }

  private void measureLabel () {
    if (label == null) {
      labelDrawn = null;
      labelWidth = 0f;
      return;
    }
    final int available = Math.max(0, (getWidth() > 0 ? getWidth() : boxPx() + Screen.dp(SIDE_PADDING) * 2) - Screen.dp(LABEL_INSET));
    if (labelMeasuredFor == available && labelDrawn != null) {
      return;
    }
    labelMeasuredFor = available;
    labelDrawn = TextUtils.ellipsize(label, labelPaint, available, TextUtils.TruncateAt.END).toString();
    labelWidth = labelPaint.measureText(labelDrawn);
  }

  // Touch

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN && !touchable) {
      return false;
    }
    return super.onTouchEvent(event);
  }

  @Override
  protected void drawableStateChanged () {
    super.drawableStateChanged();
    if (pressSpring == null) {
      return; // called from View's constructor
    }
    float target = isPressed() ? 1f : 0f;
    if (pressSpring.getTarget() != target) {
      pressSpring.animateTo(target);
    }
  }

  // Drawing

  private static void drawIcon (Canvas c, Drawable d, int left, int top, int size, int alpha) {
    if (alpha <= 0) {
      return;
    }
    d.setBounds(left, top, left + size, top + size);
    d.setAlpha(alpha);
    d.draw(c);
  }

  @Override
  protected void onDraw (Canvas c) {
    final float selectedRaw = selectSpring.getValue();
    final float selected = MathUtils.clamp(selectedRaw);
    final float press = MathUtils.clamp(pressSpring.getValue(), 0f, 1.2f);
    final int box = boxPx();
    final float cx = getWidth() / 2f;
    rect.set(cx - box / 2f, 0, cx + box / 2f, box);
    final float full = box / 2f;
    final float rest = full + (Screen.dpf(SELECTED_CORNER[tier]) - full) * selectedRaw;
    final float corner = MathUtils.clamp(rest + (Screen.dpf(PRESSED_CORNER[tier]) - rest) * press, 0f, full);

    final int container = containerColor(selected);
    fill.setColor(container);
    c.drawRoundRect(rect, corner, corner, fill);

    final int content = contentColor(selected >= .5f);
    if (press > 0f) {
      fill.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * MathUtils.clamp(press)));
      c.drawRoundRect(rect, corner, corner, fill);
    }

    tintIcons();
    final int iconSize = Screen.dp(ICON[tier]);
    final int left = (int) (rect.centerX() - iconSize / 2f), top = (int) (rect.centerY() - iconSize / 2f);
    if (spinTo != null && spinFrom != null) {
      // Each instance once per frame: the old icon until half way, the new one after
      final float s = MathUtils.clamp(spin.getValue());
      final int save = c.save();
      c.scale(Math.abs((float) Math.cos(Math.PI * s)), 1f, rect.centerX(), rect.centerY());
      drawIcon(c, s < .5f ? spinFrom : spinTo, left, top, iconSize, 255);
      c.restoreToCount(save);
    } else {
      if (icon != null) {
        drawIcon(c, icon, left, top, iconSize, iconSelected != null ? Math.round(255f * (1f - selected)) : 255);
      }
      if (iconSelected != null && selected > 0f) {
        drawIcon(c, iconSelected, left, top, iconSize, Math.round(255f * selected));
      }
    }
    if (crossMode == CROSS_WHEN_CHECKED && selected > 0f) {
      DrawAlgorithms.drawCross(c, rect.centerX(), rect.centerY(), selected, contentColor(true), container, Screen.dp(ICON[tier] - 1f));
    } else if (crossMode == CROSS_WHEN_UNCHECKED && selected < 1f) {
      DrawAlgorithms.drawCross(c, rect.centerX(), rect.centerY(), 1f - selected, contentColor(false), container, Screen.dp(ICON[tier] - 1f));
    }

    measureLabel();
    if (labelDrawn != null) {
      final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
      labelPaint.setColor(mediaFactor > 0f ? ColorUtils.fromToArgb(onSurface, 0xffffffff, mediaFactor) : onSurface);
      final float baseline = box + Screen.dp(LABEL_GAP) + Screen.dp(LABEL_LINE) / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f;
      c.drawText(labelDrawn, cx - labelWidth / 2f, baseline, labelPaint);
    }
  }
}
