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

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E: a button of the call screen (XgramM3CallScreen), drawn on a Canvas with no allocation per frame.
 * <ul>
 * <li>{@link #STYLE_TOGGLE}: a 72dp toggle icon button (mute, speaker, message). Unselected it is a tonal
 * secondaryContainer circle; selected it morphs to a 20dp rounded square in primary / onPrimary. A press springs the
 * corners to 16dp under a 10% state layer.</li>
 * <li>{@link #STYLE_END}: the 96 x 64dp end call pill in error / onError.</li>
 * <li>{@link #STYLE_ACCEPT}: the same pill in the accept green (a custom color role harmonized to primary, see
 * {@link #acceptColor}).</li>
 * <li>{@link #STYLE_TONAL}: the same pill in surfaceContainerHighest / onSurface (close).</li>
 * </ul>
 * An optional labelMedium label sits 8dp under the container. Main thread only.
 */
public final class XgramM3CallButton extends View {
  public static final int STYLE_TOGGLE = 0, STYLE_END = 1, STYLE_ACCEPT = 2, STYLE_TONAL = 3;

  private static final float TOGGLE_BOX = 72f, TOGGLE_SELECTED_CORNER = 20f, TOGGLE_PRESSED_CORNER = 16f, TOGGLE_ICON = 28f;
  private static final float PILL_W = 96f, PILL_H = 64f, PILL_PRESSED_CORNER = 20f, PILL_ICON = 28f;
  private static final float SIDE_PADDING = 8f, LABEL_GAP = 8f, LABEL_LINE = 16f;

  private final int style;
  private final float boxWDp, boxHDp, iconDp;

  private @DrawableRes int iconRes;
  private @Nullable Drawable icon, iconSelected;
  private int iconColor, iconSelectedColor;
  private boolean needCross;

  private @Nullable String label, labelDrawn;
  private float labelWidth;
  private final TextPaint labelPaint;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  private final XgramExpressive.Spring pressSpring, selectSpring;
  private boolean checked;
  private boolean touchable = true;

  public XgramM3CallButton (Context context, int style) {
    super(context);
    this.style = style;
    if (style == STYLE_TOGGLE) {
      boxWDp = TOGGLE_BOX;
      boxHDp = TOGGLE_BOX;
      iconDp = TOGGLE_ICON;
    } else {
      boxWDp = PILL_W;
      boxHDp = PILL_H;
      iconDp = PILL_ICON;
    }
    fill.setStyle(Paint.Style.FILL);
    labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    labelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
    labelPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      labelPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M));
    }
    pressSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    selectSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    setClickable(true);
    setFocusable(true);
  }

  // Content

  public void setIcon (@DrawableRes int res) {
    if (this.iconRes == res && icon != null) {
      return;
    }
    this.iconRes = res;
    // Through the icon pack, as every glyph with Expressive design
    this.icon = res != 0 ? Drawables.get(XgramIconPack.map(res)) : null;
    this.iconSelected = res != 0 && style == STYLE_TOGGLE ? Drawables.get(XgramIconPack.map(res)) : null;
    this.iconColor = 0;
    this.iconSelectedColor = 0;
    invalidate();
  }

  /** The muted look: a slash drawn across the icon while selected. */
  public void setNeedCross (boolean needCross) {
    this.needCross = needCross;
    invalidate();
  }

  /** Label under the container (also the content description); null for none. */
  public void setLabel (@Nullable CharSequence label) {
    String text = label != null ? label.toString() : null;
    if (TextUtils.equals(this.label, text)) {
      return;
    }
    boolean hadLabel = this.label != null;
    this.label = text;
    setContentDescription(text);
    measureLabel();
    if (hadLabel != (text != null)) {
      requestLayout();
    }
    invalidate();
  }

  /** Content description only (a pill with no label). */
  public void setDescription (@Nullable CharSequence description) {
    setContentDescription(description);
  }

  /** Selected (on) state of a toggle; springs the shape and the colors. */
  public void setChecked (boolean checked, boolean animated) {
    if (this.checked == checked && selectSpring.getTarget() == (checked ? 1f : 0f)) {
      return;
    }
    this.checked = checked;
    setSelected(checked);
    if (animated) {
      selectSpring.animateTo(checked ? 1f : 0f);
    } else {
      selectSpring.setValue(checked ? 1f : 0f);
      invalidate();
    }
  }

  public boolean isChecked () {
    return checked;
  }

  /** False while the button fades out, so a fading button takes no taps. */
  public void setTouchable (boolean touchable) {
    this.touchable = touchable;
    if (!touchable && isPressed()) {
      setPressed(false);
    }
  }

  // Colors

  @ColorInt
  private int containerColor (float selected) {
    switch (style) {
      case STYLE_TOGGLE:
        return ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.color(XgramExpressive.PRIMARY), selected);
      case STYLE_END:
        return XgramExpressive.color(XgramExpressive.ERROR);
      case STYLE_ACCEPT:
        return acceptColor(false);
      case STYLE_TONAL:
      default:
        return XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    }
  }

  @ColorInt
  private int contentColor (boolean selected) {
    switch (style) {
      case STYLE_TOGGLE:
        return XgramExpressive.color(selected ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_SECONDARY_CONTAINER);
      case STYLE_END:
        return XgramExpressive.color(XgramExpressive.ON_ERROR);
      case STYLE_ACCEPT:
        return acceptColor(true);
      case STYLE_TONAL:
      default:
        return XgramExpressive.color(XgramExpressive.ON_SURFACE);
    }
  }

  private static int acceptSource, acceptFill, acceptContent;
  private static boolean acceptDark, acceptValid;
  private static final float[] acceptHct = new float[3];

  /**
   * The accept green of the call screen: a custom color role in the hue of the theme's positive color (harmonized to
   * primary by XgramM3Colors), at the tones of a filled role pair (40 / 100 light, 80 / 20 dark). Rebuilt only when the
   * theme or its colors change. Main thread only.
   */
  @ColorInt
  public static int acceptColor (boolean content) {
    int source = Theme.getColor(ColorId.iconPositive);
    boolean dark = Theme.isDark();
    if (!acceptValid || source != acceptSource || dark != acceptDark) {
      acceptSource = source;
      acceptDark = dark;
      acceptValid = true;
      androidx.core.graphics.ColorUtils.colorToM3HCT(source | 0xff000000, acceptHct);
      float hue = acceptHct[0];
      float chroma = Math.max(36f, Math.min(acceptHct[1], 56f));
      acceptFill = androidx.core.graphics.ColorUtils.M3HCTToColor(hue, chroma, dark ? 80f : 40f) | 0xff000000;
      acceptContent = androidx.core.graphics.ColorUtils.M3HCTToColor(hue, Math.min(chroma, 24f), dark ? 20f : 100f) | 0xff000000;
    }
    return content ? acceptContent : acceptFill;
  }

  private void tintIcons () {
    int off = contentColor(false);
    if (icon != null && iconColor != off) {
      iconColor = off;
      icon.setColorFilter(Paints.getColorFilter(off));
    }
    if (iconSelected != null) {
      int on = contentColor(true);
      if (iconSelectedColor != on) {
        iconSelectedColor = on;
        iconSelected.setColorFilter(Paints.getColorFilter(on));
      }
    }
  }

  // Layout

  private int boxW () {
    return Screen.dp(boxWDp);
  }

  private int boxH () {
    return Screen.dp(boxHDp);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = boxW() + Screen.dp(SIDE_PADDING) * 2;
    int height = boxH() + (label != null ? Screen.dp(LABEL_GAP) + Screen.dp(LABEL_LINE) : 0);
    setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    measureLabel();
  }

  private void measureLabel () {
    if (label == null) {
      labelDrawn = null;
      labelWidth = 0f;
      return;
    }
    int available = Math.max(getWidth(), boxW() + Screen.dp(SIDE_PADDING) * 2);
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

  @Override
  protected void onDraw (Canvas c) {
    final float selectedRaw = selectSpring.getValue();
    final float selected = MathUtils.clamp(selectedRaw);
    final float press = MathUtils.clamp(pressSpring.getValue(), 0f, 1.2f);
    final int bw = boxW(), bh = boxH();
    final float cx = getWidth() / 2f;
    rect.set(cx - bw / 2f, 0, cx + bw / 2f, bh);
    final float full = Math.min(bw, bh) / 2f;
    float rest = full;
    if (style == STYLE_TOGGLE) {
      rest = full + (Screen.dpf(TOGGLE_SELECTED_CORNER) - full) * selectedRaw;
    }
    final float pressed = Screen.dpf(style == STYLE_TOGGLE ? TOGGLE_PRESSED_CORNER : PILL_PRESSED_CORNER);
    final float corner = MathUtils.clamp(rest + (pressed - rest) * press, 0f, full);

    final int container = containerColor(selected);
    fill.setColor(container);
    c.drawRoundRect(rect, corner, corner, fill);

    final int content = contentColor(selected >= .5f);
    if (press > 0f) {
      fill.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * MathUtils.clamp(press)));
      c.drawRoundRect(rect, corner, corner, fill);
    }

    tintIcons();
    final int iconSize = Screen.dp(iconDp);
    final int left = (int) (rect.centerX() - iconSize / 2f), top = (int) (rect.centerY() - iconSize / 2f);
    if (icon != null) {
      int alpha = iconSelected != null ? Math.round(255f * (1f - selected)) : 255;
      if (alpha > 0) {
        icon.setBounds(left, top, left + iconSize, top + iconSize);
        icon.setAlpha(alpha);
        icon.draw(c);
      }
    }
    if (iconSelected != null && selected > 0f) {
      iconSelected.setBounds(left, top, left + iconSize, top + iconSize);
      iconSelected.setAlpha(Math.round(255f * selected));
      iconSelected.draw(c);
    }
    if (needCross && selected > 0f) {
      DrawAlgorithms.drawCross(c, rect.centerX(), rect.centerY(), selected, contentColor(true), container, Screen.dp(iconDp - 1f));
    }

    if (labelDrawn != null) {
      labelPaint.setColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      float baseline = bh + Screen.dp(LABEL_GAP) + Screen.dp(LABEL_LINE) / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f;
      c.drawText(labelDrawn, cx - labelWidth / 2f, baseline, labelPaint);
    }
  }
}
