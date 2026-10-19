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

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E: the settings search at the top of the main Settings list: the collapsed M3E search bar, a 56dp full-round
 * pill in surfaceContainerHigh with the search icon and "Search settings" in onSurfaceVariant (bodyLarge), 16dp from
 * the sides, a state layer on press. It sits below the header's floating button, outside any group (no tile). A tap
 * opens the universal settings search (the controller's click listener). Mirrored in RTL.
 */
public final class XgramSearchPillView extends View implements XgramPillStateLayer.Shape {
  /** Clear of the floating button that sits half below the collapsing header. */
  private static final float TOP = 32f;
  private static final float HEIGHT = 56f;
  private static final float SIDE = 16f;
  private static final float ICON_START = 16f;
  private static final float TEXT_START = 56f;
  private static final float TEXT_END = 24f;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF pill = new RectF();
  private final Drawable icon;
  private String hint = "";
  private CharSequence shownHint = "";
  private float shownHintWidth;
  private boolean rtl;

  public XgramSearchPillView (Context context) {
    super(context);
    icon = Drawables.get(getResources(), R.drawable.baseline_search_24);
    text.setTextSize(Screen.dp(16f));
    text.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_L, false));
    setClickable(true);
    setFocusable(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // The state layer over the pill (the pill itself is drawn in onDraw)
      Drawable layer = XgramPillStateLayer.attach(this, this);
      setBackground(null);
      setForeground(layer);
    }
    updateText();
  }

  /** The hint in the current language and direction. */
  public void updateText () {
    String hint = Lang.getString(R.string.XgramSettingsSearchHint);
    boolean rtl = Lang.rtl();
    if (!hint.equals(this.hint) || rtl != this.rtl) {
      this.hint = hint;
      this.rtl = rtl;
      setContentDescription(hint);
      layoutHint(getWidth());
      invalidate();
    }
  }

  private void layoutHint (int width) {
    float available = width - Screen.dp(SIDE) * 2 - Screen.dp(TEXT_START) - Screen.dp(TEXT_END);
    shownHint = available > 0 ? TextUtils.ellipsize(hint, text, available, TextUtils.TruncateAt.END) : "";
    shownHintWidth = shownHint.length() > 0 ? text.measureText(shownHint, 0, shownHint.length()) : 0f;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), Screen.dp(TOP + HEIGHT));
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    pill.set(Screen.dp(SIDE), Screen.dp(TOP), w - Screen.dp(SIDE), Screen.dp(TOP + HEIGHT));
    layoutHint(w);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !pill.contains(e.getX(), e.getY())) {
      return false; // only the pill is the button (not the band kept clear for the floating button, nor the sides); the list takes the rest
    }
    return super.onTouchEvent(e);
  }

  @Override
  public float getStateLayerShape (@NonNull View view, @NonNull RectF outRect) {
    outRect.set(pill);
    return XgramPillStateLayer.CORNER_FULL;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (pill.isEmpty()) {
      return;
    }
    // Theme roles, cached per theme by XgramExpressive: nothing is allocated here
    fill.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH));
    float radius = pill.height() / 2f;
    c.drawRoundRect(pill, radius, radius, fill);

    int content = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    float centerY = pill.centerY();
    if (icon != null) {
      int size = icon.getMinimumWidth();
      float x = rtl ? pill.right - Screen.dp(ICON_START) - size : pill.left + Screen.dp(ICON_START);
      Drawables.draw(c, icon, x, centerY - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(content));
    }
    if (shownHint.length() > 0) {
      text.setColor(content);
      float x = rtl ? pill.right - Screen.dp(TEXT_START) - shownHintWidth : pill.left + Screen.dp(TEXT_START);
      float baseline = centerY - (text.descent() + text.ascent()) / 2f;
      c.drawText(shownHint, 0, shownHint.length(), x, baseline, text);
    }
  }
}
