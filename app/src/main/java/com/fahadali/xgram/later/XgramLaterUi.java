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
package com.fahadali.xgram.later;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;

/** Small pieces of the chat list for Later: the bell's colors, the quick pill's room, the Snoozed row's positions. */
public final class XgramLaterUi {
  private XgramLaterUi () { }

  /**
   * The bell next to the @ and the heart: a tertiaryContainer pill with an onTertiaryContainer icon with Expressive
   * design; Telegram X's badge colors otherwise (muted for a muted chat). Read when drawn, so a theme switch follows.
   */
  public static final class BellColors implements TextColorSet {
    @Nullable private Counter counter;

    /** The bell counter whose muted state the classic colors follow. */
    public void attach (Counter counter) {
      this.counter = counter;
    }

    private float muteFactor () {
      return counter != null ? counter.getMuteFactor() : 0f;
    }

    @Override
    public int defaultTextColor () {
      if (XgramExpressive.isEnabled()) {
        return XgramExpressive.color(XgramExpressive.ON_TERTIARY_CONTAINER);
      }
      return counter != null ? counter.getColor(muteFactor(), ColorId.badgeText, ColorId.badgeMutedText) : Theme.getColor(ColorId.badgeText);
    }

    @Override
    public int iconColor () {
      return defaultTextColor();
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      if (XgramExpressive.isEnabled()) {
        return XgramExpressive.color(XgramExpressive.TERTIARY_CONTAINER);
      }
      return counter != null ? counter.getColor(muteFactor(), ColorId.badge, ColorId.badgeMuted) : Theme.getColor(ColorId.badge);
    }
  }

  /** The quick actions pill measures exactly 2 x padding + 48dp per item, with 8dp margins on each side. */
  public static boolean barHasRoom (int finalItemCount) {
    float padding = XgramExpressive.isEnabled() ? 8f : 4f;
    return Screen.currentWidth() >= Screen.dp(2 * 8f + 2 * padding + 48f * finalItemCount);
  }

  // The Snoozed row between the archive and the chats: pure position math (the self-test runs every combination).
  // s: the folder's new chats card (0 or 1), a: the archive row (0 or 1), r: the Snoozed row (0 or 1).

  /** The row's adapter position, or -1. */
  public static int rowPosition (int s, int a, int r) {
    return r == 1 ? s + a : -1;
  }

  public static int itemCount (int s, int r, int chatCount) {
    return s + r + (chatCount > 0 ? chatCount + 1 : 0);
  }

  /** The adapter position of the chat at {@code chatIndex} of the adapter's list (the archive is index 0 when shown). */
  public static int positionOfChatIndex (int chatIndex, int s, int a, int r) {
    if (chatIndex == -1) {
      return -1;
    }
    return s + chatIndex + (r == 1 && chatIndex >= a ? 1 : 0);
  }

  /** The chat index at an adapter position: -1 for the card and the row. */
  public static int chatIndexOfPosition (int position, int s, int a, int r) {
    if (position < 0) {
      return -1;
    }
    int q = position - s;
    if (r == 1) {
      if (q == a) {
        return -1;
      }
      if (q > a) {
        return q - 1;
      }
    }
    return q;
  }
}
