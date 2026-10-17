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
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 Expressive connected button group for the profile page (Google Contacts style): two to four tonal
 * buttons with an icon above a label. Containers are SECONDARY_CONTAINER pills; the pressed button grows by 15% of
 * its width while its neighbours shrink, and its corners morph from full to 16dp. A selected toggle (Mute) is
 * PRIMARY with 16dp corners. Height 88dp: 8dp top, 64dp buttons, 16dp bottom; 16dp side padding, 8dp gaps.
 */
public class XgramButtonGroupView extends View {
  public static final float HEIGHT_DP = 88f;
  private static final float TOP_DP = 8f, BUTTON_DP = 64f, SIDE_DP = 16f, GAP_DP = 8f, SQUARE_DP = 16f;
  private static final float GROW = .15f;

  public static final class Action {
    public final int id;
    public final @DrawableRes int iconRes;
    public final String label;
    public final boolean selected;

    public Action (int id, @DrawableRes int iconRes, String label, boolean selected) {
      this.id = id;
      this.iconRes = iconRes;
      this.label = label;
      this.selected = selected;
    }

    boolean sameAs (Action other) {
      return other != null && other.id == id && other.iconRes == iconRes && other.selected == selected && TextUtils.equals(other.label, label);
    }
  }

  public interface Callback {
    void onActionClick (XgramButtonGroupView view, int actionId);
  }

  private static final class Button {
    Action action;
    @Nullable Drawable icon;
    final XgramExpressive.Spring weight, corner, selected, pressLayer;
    final RectF rect = new RectF();
    CharSequence ellipsized;
    int ellipsizedWidth = -1;

    Button (View host, Action action) {
      this.action = action;
      this.icon = Drawables.get(action.iconRes);
      XgramExpressive.Spring.Listener invalidate = (s, value) -> host.invalidate();
      this.weight = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, invalidate).setThreshold(.002f);
      this.corner = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, action.selected ? 1f : 0f, invalidate);
      this.selected = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, action.selected ? 1f : 0f, invalidate);
      this.pressLayer = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
    }

    void bind (Action action) {
      if (this.action.iconRes != action.iconRes) {
        this.icon = Drawables.get(action.iconRes);
      }
      if (!TextUtils.equals(this.action.label, action.label)) {
        ellipsizedWidth = -1;
      }
      this.action = action;
    }
  }

  private final ArrayList<Button> buttons = new ArrayList<>(4);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private @Nullable Callback callback;
  private int pressedIndex = -1;

  // XGram M3E round 6: the connected style (a segmented choice over media, e.g. the photo editor's blur type): 40dp
  // label-only buttons 2dp apart, full outer corners and 8dp inner ones that tighten to 4dp while pressed; the selected
  // button rounds fully and fills with the dark scheme's secondaryContainer, the others sit on its surfaceContainerHigh
  // at 60%. Colours, corners and widths spring as in the profile style.
  private static final float CONNECTED_HEIGHT_DP = 40f, CONNECTED_GAP_DP = 2f, CONNECTED_INNER_DP = 8f, CONNECTED_PRESSED_DP = 4f;
  private boolean connectedOnMedia;
  private final android.graphics.Path connectedPath = new android.graphics.Path();
  private final float[] connectedRadii = new float[8];

  /** Switches to the connected style over media (call once, before the actions are set). */
  public void setConnectedOnMedia () {
    if (!connectedOnMedia) {
      connectedOnMedia = true;
      requestLayout();
      invalidate();
    }
  }

  public XgramButtonGroupView (Context context) {
    super(context);
    textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
    }
    setClickable(true);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Rebinds the buttons; a changed selection animates (Mute toggles). */
  public void setActions (@NonNull List<Action> actions) {
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
      buttons.clear();
      for (Action action : actions) {
        buttons.add(new Button(this, action));
      }
      pressedIndex = -1;
      invalidate();
      return;
    }
    for (int i = 0; i < actions.size(); i++) {
      Button b = buttons.get(i);
      Action action = actions.get(i);
      if (b.action.sameAs(action)) {
        continue;
      }
      boolean selectionChanged = b.action.selected != action.selected;
      b.bind(action);
      if (selectionChanged) {
        b.selected.animateTo(action.selected ? 1f : 0f);
        b.corner.animateTo(action.selected || (i == pressedIndex && !connectedOnMedia) ? 1f : 0f);
      }
    }
    invalidate();
  }

  public static int getViewHeight () {
    return Screen.dp(HEIGHT_DP);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(connectedOnMedia ? Screen.dp(CONNECTED_HEIGHT_DP) : getViewHeight(), MeasureSpec.EXACTLY));
  }

  private void layoutButtons () {
    final int count = buttons.size();
    if (count == 0) {
      return;
    }
    float side = connectedOnMedia ? 0f : Screen.dp(SIDE_DP), gap = Screen.dp(connectedOnMedia ? CONNECTED_GAP_DP : GAP_DP);
    float avail = getMeasuredWidth() - side * 2f - gap * (count - 1);
    float base = avail / count;
    float totalWeight = 0f;
    for (Button b : buttons) {
      totalWeight += Math.max(.1f, b.weight.getValue());
    }
    float top = connectedOnMedia ? 0f : Screen.dp(TOP_DP), bottom = top + (connectedOnMedia ? getMeasuredHeight() : Screen.dp(BUTTON_DP));
    boolean rtl = Lang.rtl();
    float x = rtl ? getMeasuredWidth() - side : side;
    for (Button b : buttons) {
      float w = avail * Math.max(.1f, b.weight.getValue()) / totalWeight;
      if (totalWeight <= 0f) {
        w = base;
      }
      if (rtl) {
        b.rect.set(x - w, top, x, bottom);
        x -= w + gap;
      } else {
        b.rect.set(x, top, x + w, bottom);
        x += w + gap;
      }
    }
  }

  private void setPressed (int index) {
    if (pressedIndex == index) {
      return;
    }
    pressedIndex = index;
    final int count = buttons.size();
    for (int i = 0; i < count; i++) {
      Button b = buttons.get(i);
      float target = 1f;
      if (index != -1 && count > 1) {
        if (i == index) {
          target = 1f + GROW;
        } else if (Math.abs(i - index) == 1) {
          int neighbours = (index > 0 ? 1 : 0) + (index < count - 1 ? 1 : 0);
          target = 1f - GROW / neighbours;
        }
      }
      b.weight.animateTo(target);
      b.corner.animateTo((i == index && !connectedOnMedia) || b.action.selected ? 1f : 0f);
      b.pressLayer.animateTo(i == index ? 1f : 0f);
    }
  }

  private int findButton (float x, float y) {
    layoutButtons();
    float slop = Screen.dp(GAP_DP) / 2f;
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
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        int index = findButton(e.getX(), e.getY());
        setPressed(index);
        return index != -1;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressedIndex != -1 && findButton(e.getX(), e.getY()) != pressedIndex) {
          setPressed(-1);
        }
        return pressedIndex != -1;
      }
      case MotionEvent.ACTION_UP: {
        int index = pressedIndex;
        setPressed(-1);
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
        setPressed(-1);
        return false;
      }
    }
    return super.onTouchEvent(e);
  }

  private void drawConnected (@NonNull Canvas c) {
    final int containerColor = XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .6f);
    final int contentColor = XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE);
    final int selectedColor = XgramM3MediaChrome.onMedia(XgramExpressive.SECONDARY_CONTAINER);
    final int selectedContentColor = XgramM3MediaChrome.onMedia(XgramExpressive.ON_SECONDARY_CONTAINER);
    final float inner = Screen.dp(CONNECTED_INNER_DP), pressedInner = Screen.dp(CONNECTED_PRESSED_DP);
    final float labelPadding = Screen.dp(12f);
    final int count = buttons.size();
    final boolean rtl = Lang.rtl();
    for (int i = 0; i < count; i++) {
      Button b = buttons.get(i);
      RectF r = b.rect;
      final float full = r.height() / 2f;
      final float sel = Math.max(0f, Math.min(1f, b.selected.getValue()));
      final float round = Math.max(0f, Math.min(1.2f, b.corner.getValue()));
      final float press = Math.max(0f, Math.min(1f, b.pressLayer.getValue()));
      float innerRadius = inner + (pressedInner - inner) * press;
      innerRadius = Math.max(0f, Math.min(full, innerRadius + (full - innerRadius) * round));
      // the visual first button is at the start: in RTL it is the right one
      final boolean leftOuter = rtl ? i == count - 1 : i == 0;
      final boolean rightOuter = rtl ? i == 0 : i == count - 1;
      final float left = leftOuter ? full : innerRadius, right = rightOuter ? full : innerRadius;
      connectedRadii[0] = connectedRadii[1] = left; // top left
      connectedRadii[2] = connectedRadii[3] = right; // top right
      connectedRadii[4] = connectedRadii[5] = right; // bottom right
      connectedRadii[6] = connectedRadii[7] = left; // bottom left
      connectedPath.rewind();
      connectedPath.addRoundRect(r, connectedRadii, android.graphics.Path.Direction.CW);
      final int content = me.vkryl.core.ColorUtils.fromToArgb(contentColor, selectedContentColor, sel);
      fillPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(containerColor, selectedColor, sel));
      c.drawPath(connectedPath, fillPaint);
      if (press > 0f) {
        fillPaint.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * press));
        c.drawPath(connectedPath, fillPaint);
      }
      int maxWidth = Math.max(0, Math.round(r.width() - labelPadding * 2f));
      if (b.ellipsizedWidth != maxWidth) {
        b.ellipsizedWidth = maxWidth;
        b.ellipsized = TextUtils.ellipsize(b.action.label, textPaint, maxWidth, TextUtils.TruncateAt.END);
      }
      if (b.ellipsized != null && b.ellipsized.length() > 0) {
        textPaint.setColor(content);
        String text = b.ellipsized.toString();
        float textWidth = textPaint.measureText(text);
        c.drawText(text, r.centerX() - textWidth / 2f, r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
      }
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (buttons.isEmpty()) {
      return;
    }
    layoutButtons();
    if (connectedOnMedia) {
      drawConnected(c);
      return;
    }
    final int containerColor = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
    final int contentColor = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
    final int selectedColor = XgramExpressive.color(XgramExpressive.PRIMARY);
    final int selectedContentColor = XgramExpressive.color(XgramExpressive.ON_PRIMARY);
    final float square = Screen.dp(SQUARE_DP);
    final int iconSize = Screen.dp(24f);
    final float labelBaseline = Screen.dp(52f);
    final float labelPadding = Screen.dp(8f);
    for (Button b : buttons) {
      RectF r = b.rect;
      float full = r.height() / 2f;
      float cornerFactor = Math.max(0f, Math.min(1.2f, b.corner.getValue()));
      float radius = Math.max(0f, Math.min(full, full + (square - full) * cornerFactor));
      float sel = Math.max(0f, Math.min(1f, b.selected.getValue()));
      int container = me.vkryl.core.ColorUtils.fromToArgb(containerColor, selectedColor, sel);
      int content = me.vkryl.core.ColorUtils.fromToArgb(contentColor, selectedContentColor, sel);
      fillPaint.setColor(container);
      c.drawRoundRect(r, radius, radius, fillPaint);
      float layer = Math.max(0f, Math.min(1f, b.pressLayer.getValue()));
      if (layer > 0f) {
        fillPaint.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * layer));
        c.drawRoundRect(r, radius, radius, fillPaint);
      }
      if (b.icon != null) {
        float left = r.centerX() - b.icon.getMinimumWidth() / 2f;
        float top = r.top + Screen.dp(12f) + (iconSize - b.icon.getMinimumHeight()) / 2f;
        Drawables.draw(c, b.icon, left, top, Paints.getPorterDuffPaint(content));
      }
      int maxWidth = Math.max(0, Math.round(r.width() - labelPadding * 2f));
      if (b.ellipsizedWidth != maxWidth) {
        b.ellipsizedWidth = maxWidth;
        b.ellipsized = TextUtils.ellipsize(b.action.label, textPaint, maxWidth, TextUtils.TruncateAt.END);
      }
      if (b.ellipsized != null && b.ellipsized.length() > 0) {
        textPaint.setColor(content);
        String text = b.ellipsized.toString();
        float textWidth = textPaint.measureText(text);
        c.drawText(text, r.centerX() - textWidth / 2f, r.top + labelBaseline, textPaint);
      }
    }
  }
}
