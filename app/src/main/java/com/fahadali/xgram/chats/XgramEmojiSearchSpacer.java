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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

/**
 * The emoji page's top spacer (MediaStickersAdapter TYPE_KEYBOARD_TOP) with Telegram's "Search emoji" field at its
 * bottom, under the panel's tabs: it scrolls away with the emoji. A tap starts the search in the message box
 * (EmojiLayout.Listener.xgramOnEmojiSearch).
 */
public final class XgramEmojiSearchSpacer extends View {
  /** The room it adds to the spacer. */
  public static final float ROW_DP = 52f;
  private static final float PILL_DP = 40f, SIDE_DP = 12f, BOTTOM_DP = 6f;

  private final RectF pill = new RectF();
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Drawable icon = Drawables.get(R.drawable.baseline_search_24);
  private final String hint = Lang.getString(R.string.XgramEmojiSearch);

  public XgramEmojiSearchSpacer (Context context, Runnable onSearch) {
    super(context);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.sp(16f));
    setOnClickListener(v -> onSearch.run());
    setContentDescription(hint);
  }

  @Override
  protected void onDraw (Canvas c) {
    final boolean m3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    final float bottom = getHeight() - Screen.dp(BOTTOM_DP);
    pill.set(Screen.dp(SIDE_DP), bottom - Screen.dp(PILL_DP), getWidth() - Screen.dp(SIDE_DP), bottom);
    final int fill = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH) : Theme.getColor(ColorId.background);
    final int content = m3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.textPlaceholder);
    final float radius = pill.height() / 2f;
    c.drawRoundRect(pill, radius, radius, Paints.fillingPaint(fill));
    final boolean rtl = Lang.rtl();
    final float iconX = rtl ? pill.right - Screen.dp(12f) - icon.getMinimumWidth() : pill.left + Screen.dp(12f);
    Drawables.draw(c, icon, iconX, pill.centerY() - icon.getMinimumHeight() / 2f, PorterDuffPaint.get(ColorId.textPlaceholder));
    textPaint.setColor(content);
    final float textWidth = textPaint.measureText(hint);
    final float textX = rtl ? iconX - Screen.dp(12f) - textWidth : iconX + icon.getMinimumWidth() + Screen.dp(12f);
    final Paint.FontMetrics metrics = textPaint.getFontMetrics();
    c.drawText(hint, textX, pill.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint);
  }
}
