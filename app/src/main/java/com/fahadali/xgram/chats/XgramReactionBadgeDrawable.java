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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;

/**
 * The unread reactions badge's icon (chat and topic rows): the newest reaction itself in its own colours once known
 * (XgramUnreadReaction), else Telegram X's heart, which alone takes the badge's tint.
 */
public final class XgramReactionBadgeDrawable extends Drawable {
  private static final float SIZE_DP = 16f, EMOJI_DP = 14f;

  private final Drawable heart = Drawables.get(R.drawable.baseline_favorite_14);
  private @Nullable Drawable emoji;
  private @Nullable String emojiCode;
  private @Nullable ColorFilter colorFilter;
  private int alpha = 255;

  /** True when the icon changed (the row redraws). */
  public boolean setEmoji (@Nullable String code) {
    if (java.util.Objects.equals(code, emojiCode)) {
      return false;
    }
    emojiCode = code;
    emoji = code != null ? Drawables.emojiDrawable(code) : null;
    return true;
  }

  @Override
  public int getMinimumWidth () {
    return Screen.dp(SIZE_DP);
  }

  @Override
  public int getMinimumHeight () {
    return Screen.dp(SIZE_DP);
  }

  @Override
  public void draw (@NonNull Canvas c) {
    final int box = Screen.dp(SIZE_DP);
    if (emoji != null) {
      final int size = Screen.dp(EMOJI_DP), inset = (box - size) / 2;
      emoji.setBounds(inset, inset, inset + size, inset + size);
      emoji.setAlpha(alpha);
      emoji.draw(c);
    } else if (heart != null) {
      final int w = heart.getMinimumWidth(), h = heart.getMinimumHeight();
      final int left = (box - w) / 2, top = (box - h) / 2;
      heart.setBounds(left, top, left + w, top + h);
      heart.setColorFilter(colorFilter);
      heart.setAlpha(alpha);
      heart.draw(c);
    }
  }

  @Override
  public void setAlpha (int alpha) {
    this.alpha = alpha;
  }

  @Override
  public int getAlpha () {
    return alpha;
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {
    this.colorFilter = colorFilter; // the badge's tint: the heart only, an emoji keeps its colours
  }

  @Nullable
  @Override
  public ColorFilter getColorFilter () {
    return colorFilter;
  }

  @Override
  @SuppressWarnings("deprecation")
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }
}
