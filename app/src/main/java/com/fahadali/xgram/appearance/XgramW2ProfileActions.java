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
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 Expressive connected button group for the profile's actions (Message, Call, Mute, Search, Share).
 * Compose ButtonGroupDefaults / ConnectedButtonGroup tokens, Medium size:
 * <ul>
 * <li>56dp tall buttons sharing the row's width, 2dp between them (ConnectedSpaceBetween), 16dp side insets (the list
 * tiles' inset);</li>
 * <li>outer corners full, inner corners 8dp (CornerSmall), 4dp while pressed (CornerExtraSmall); a checked toggle
 * (Mute while muted) becomes fully round (checked shape = CircleShape);</li>
 * <li>tonal colors: secondaryContainer / onSecondaryContainer, checked too (only the shape morphs: a secondary
 * block is near white in the dark scheme);</li>
 * <li>a 24dp icon above a labelLarge label, the pressed state layer is the content color at 10%;</li>
 * <li>shape springs: fast spatial (stiffness 800, damping 0.6), colors and the state layer: fast effects.</li>
 * </ul>
 * Only used while {@link XgramExpressive#isEnabled()}.
 */
public class XgramW2ProfileActions extends View {
  public static final float TOP = 8f, BUTTON_HEIGHT = 56f, BOTTOM = 8f, SIDE = 16f, BETWEEN = 2f;
  public static final float INNER_CORNER = XgramExpressive.CORNER_S, INNER_CORNER_PRESSED = XgramExpressive.CORNER_XS;
  private static final float ICON = 24f, ICON_LABEL_GAP = 2f, LABEL_PADDING = 8f;

  public interface Callback {
    void onActionClick (XgramW2ProfileActions view, int actionId);
  }

  private static final class Button {
    XgramButtonGroupView.Action action;
    @Nullable Drawable icon;
    final XgramExpressive.Spring press, pressLayer, checkedShape, checkedColor;
    final RectF rect = new RectF();
    final float[] radii = new float[8];
    final Path path = new Path();
    @Nullable String label;
    float labelWidth;
    int labelMaxWidth = -1;

    Button (View host, XgramButtonGroupView.Action action) {
      this.action = action;
      this.icon = Drawables.get(action.iconRes);
      final XgramExpressive.Spring.Listener invalidate = (s, value) -> host.invalidate();
      final float checked = action.selected ? 1f : 0f;
      this.press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate);
      this.pressLayer = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
      this.checkedShape = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, checked, invalidate);
      this.checkedColor = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, checked, invalidate);
    }

    void bind (XgramButtonGroupView.Action action) {
      if (this.action.iconRes != action.iconRes) {
        this.icon = Drawables.get(action.iconRes);
      }
      if (!TextUtils.equals(this.action.label, action.label)) {
        labelMaxWidth = -1;
      }
      this.action = action;
    }
  }

  private final ArrayList<Button> buttons = new ArrayList<>(4);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private @Nullable Callback callback;
  private int pressedIndex = -1;

  public XgramW2ProfileActions (Context context) {
    super(context);
    textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
    }
    textPaint.getFontMetrics(fontMetrics);
    setClickable(true);
  }

  public static int getViewHeight () {
    return Screen.dp(TOP + BUTTON_HEIGHT + BOTTOM);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Rebinds the buttons; a changed checked state (Mute) springs to its shape and colors. */
  public void setActions (@NonNull List<XgramButtonGroupView.Action> actions) {
    boolean changed = actions.size() != buttons.size();
    if (!changed) {
      for (int i = 0; i < actions.size(); i++) {
        if (buttons.get(i).action.id != actions.get(i).id) {
          changed = true;
          break;
        }
      }
    }
    if (changed) {
      for (Button b : buttons) {
        b.press.cancel();
        b.pressLayer.cancel();
        b.checkedShape.cancel();
        b.checkedColor.cancel();
      }
      buttons.clear();
      for (XgramButtonGroupView.Action action : actions) {
        buttons.add(new Button(this, action));
      }
      pressedIndex = -1;
      updateContentDescription();
      invalidate();
      return;
    }
    boolean any = false;
    for (int i = 0; i < actions.size(); i++) {
      Button b = buttons.get(i);
      XgramButtonGroupView.Action action = actions.get(i);
      if (b.action.sameAs(action)) {
        continue;
      }
      any = true;
      boolean checkedChanged = b.action.selected != action.selected;
      b.bind(action);
      if (checkedChanged) {
        float target = action.selected ? 1f : 0f;
        b.checkedShape.animateTo(target);
        b.checkedColor.animateTo(target);
      }
    }
    if (any) {
      updateContentDescription();
      invalidate();
    }
  }

  private void updateContentDescription () {
    StringBuilder b = new StringBuilder();
    for (Button button : buttons) {
      if (!TextUtils.isEmpty(button.action.label)) {
        if (b.length() > 0) {
          b.append(", ");
        }
        b.append(button.action.label);
      }
    }
    setContentDescription(b.toString());
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getViewHeight(), MeasureSpec.EXACTLY));
  }

  // Layout: equal widths (the group fills the row), in reading order

  private void layoutButtons () {
    final int count = buttons.size();
    if (count == 0) {
      return;
    }
    final float side = Screen.dp(SIDE), between = Screen.dp(BETWEEN);
    final float width = Math.max(0f, (getMeasuredWidth() - side * 2f - between * (count - 1)) / count);
    final float top = Screen.dp(TOP), bottom = top + Screen.dp(BUTTON_HEIGHT);
    final boolean rtl = Lang.rtl();
    for (int i = 0; i < count; i++) {
      final int visual = rtl ? count - 1 - i : i;
      final float left = side + visual * (width + between);
      buttons.get(i).rect.set(left, top, left + width, bottom);
    }
  }

  /** Corners of button {@code i}: outer full, inner 8dp (4dp pressed), all full once checked. */
  private void computeShape (Button b, int index, int count) {
    final RectF r = b.rect;
    final float full = r.height() / 2f;
    final float pressed = clamp(b.press.getValue(), 0f, 1f);
    final float checked = clamp(b.checkedShape.getValue(), 0f, 1.2f); // fast spatial overshoots a little
    final float inner = Screen.dp(INNER_CORNER) + (Screen.dp(INNER_CORNER_PRESSED) - Screen.dp(INNER_CORNER)) * pressed;
    final boolean rtl = Lang.rtl();
    final int visual = rtl ? count - 1 - index : index;
    final float leftRest = visual == 0 ? full : inner;
    final float rightRest = visual == count - 1 ? full : inner;
    final float left = clamp(leftRest + (full - leftRest) * checked, 0f, full);
    final float right = clamp(rightRest + (full - rightRest) * checked, 0f, full);
    final float[] radii = b.radii;
    radii[0] = radii[1] = left; // top left
    radii[2] = radii[3] = right; // top right
    radii[4] = radii[5] = right; // bottom right
    radii[6] = radii[7] = left; // bottom left
  }

  private static float clamp (float v, float min, float max) {
    return v < min ? min : (v > max ? max : v);
  }

  // Touch

  private void setPressedIndex (int index) {
    if (pressedIndex == index) {
      return;
    }
    pressedIndex = index;
    for (int i = 0; i < buttons.size(); i++) {
      Button b = buttons.get(i);
      float target = i == index ? 1f : 0f;
      b.press.animateTo(target);
      b.pressLayer.animateTo(target);
    }
  }

  private int findButton (float x, float y) {
    layoutButtons();
    final float slop = Screen.dp(BETWEEN) / 2f;
    for (int i = 0; i < buttons.size(); i++) {
      RectF r = buttons.get(i).rect;
      if (x >= r.left - slop && x < r.right + slop && y >= r.top - slop && y < r.bottom + slop) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!isEnabled()) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        int index = findButton(e.getX(), e.getY());
        setPressedIndex(index);
        return index != -1;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressedIndex != -1 && findButton(e.getX(), e.getY()) != pressedIndex) {
          setPressedIndex(-1);
        }
        return pressedIndex != -1;
      }
      case MotionEvent.ACTION_UP: {
        int index = pressedIndex;
        setPressedIndex(-1);
        if (index != -1 && index < buttons.size()) {
          playSoundEffect(SoundEffectConstants.CLICK);
          if (callback != null) {
            callback.onActionClick(this, buttons.get(index).action.id);
          }
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_CANCEL: {
        setPressedIndex(-1);
        return false;
      }
    }
    return super.onTouchEvent(e);
  }

  // Drawing

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int count = buttons.size();
    if (count == 0) {
      return;
    }
    layoutButtons();
    final int container = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
    final int content = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    final int checkedContainer = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER); // XGram: a checked toggle keeps the tonal colors
    final int checkedContent = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    final int iconSize = Screen.dp(ICON);
    final float labelHeight = fontMetrics.descent - fontMetrics.ascent;
    final float blockHeight = iconSize + Screen.dp(ICON_LABEL_GAP) + labelHeight;
    final float labelPadding = Screen.dp(LABEL_PADDING);
    for (int i = 0; i < count; i++) {
      final Button b = buttons.get(i);
      final RectF r = b.rect;
      if (r.width() <= 0f) {
        continue;
      }
      computeShape(b, i, count);
      final float k = clamp(b.checkedColor.getValue(), 0f, 1f);
      final int fill = me.vkryl.core.ColorUtils.fromToArgb(container, checkedContainer, k);
      final int ink = me.vkryl.core.ColorUtils.fromToArgb(content, checkedContent, k);
      b.path.rewind();
      b.path.addRoundRect(r, b.radii, Path.Direction.CW);
      fillPaint.setColor(fill);
      c.drawPath(b.path, fillPaint);
      final float layer = clamp(b.pressLayer.getValue(), 0f, 1f);
      if (layer > 0f) {
        fillPaint.setColor(XgramExpressive.withAlpha(ink, XgramExpressive.STATE_PRESSED * layer));
        c.drawPath(b.path, fillPaint);
      }
      final float blockTop = r.top + (r.height() - blockHeight) / 2f;
      if (b.icon != null) {
        Drawables.draw(c, b.icon, r.centerX() - b.icon.getMinimumWidth() / 2f, blockTop + (iconSize - b.icon.getMinimumHeight()) / 2f, Paints.getPorterDuffPaint(ink));
      }
      final int maxWidth = Math.max(0, Math.round(r.width() - labelPadding * 2f));
      if (b.labelMaxWidth != maxWidth) {
        b.labelMaxWidth = maxWidth;
        CharSequence ellipsized = TextUtils.isEmpty(b.action.label) ? null : TextUtils.ellipsize(b.action.label, textPaint, maxWidth, TextUtils.TruncateAt.END);
        b.label = ellipsized != null && ellipsized.length() > 0 ? ellipsized.toString() : null;
        b.labelWidth = b.label != null ? textPaint.measureText(b.label) : 0f;
      }
      if (b.label != null) {
        textPaint.setColor(ink);
        c.drawText(b.label, r.centerX() - b.labelWidth / 2f, blockTop + iconSize + Screen.dp(ICON_LABEL_GAP) - fontMetrics.ascent, textPaint);
      }
    }
  }
}
