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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Collapsed expandable quotes (official Telegram's TextEntityTypeExpandableBlockQuote): the text with each collapsed
 * quote cut at its first hidden line (Text#xgramQuoteCuts), the entities after a cut moved back, those inside one
 * dropped, those across one shortened. The quote keeps its expandable type, so its chevron is drawn and a tap expands
 * it again (TextWrapper lays the whole text out then).
 */
public final class XgramQuoteCollapse {
  private XgramQuoteCollapse () { }

  /** Lines a collapsed quote shows, as official Telegram. */
  public static final int LINES = 3;

  /** The text has an expandable quote. */
  public static boolean hasExpandable (@Nullable TdApi.FormattedText text) {
    if (text == null || text.entities == null) {
      return false;
    }
    for (TdApi.TextEntity entity : text.entities) {
      if (entity.type.getConstructor() == TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR) {
        return true;
      }
    }
    return false;
  }

  /**
   * The expandable quotes of {@code text} in order (their ordinals): [offset, end) of each.
   */
  @NonNull
  public static int[][] expandableRanges (@NonNull TdApi.FormattedText text) {
    ArrayList<int[]> ranges = new ArrayList<>();
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.type.getConstructor() == TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR) {
          ranges.add(new int[] {entity.offset, entity.offset + entity.length});
        }
      }
    }
    int[][] result = ranges.toArray(new int[0][]);
    Arrays.sort(result, Comparator.comparingInt(range -> range[0]));
    return result;
  }

  /**
   * {@code text} without [cut, end) of each collapsed quote: {@code cuts[i]} for the expandable quote {@code i}
   * (by ordinal), -1 to keep it whole. The space before a cut goes too (no empty last line in the quote).
   */
  @NonNull
  public static TdApi.FormattedText cut (@NonNull TdApi.FormattedText text, @NonNull int[] cuts) {
    final int[][] quotes = expandableRanges(text);
    final ArrayList<int[]> removed = new ArrayList<>(); // [start, end) in the original text, in order
    for (int i = 0; i < quotes.length && i < cuts.length; i++) {
      int start = cuts[i];
      final int end = quotes[i][1];
      if (start <= quotes[i][0] || start >= end) {
        continue;
      }
      while (start > quotes[i][0] + 1 && Character.isWhitespace(text.text.charAt(start - 1))) {
        start--;
      }
      removed.add(new int[] {start, end});
    }
    if (removed.isEmpty()) {
      return text;
    }
    final StringBuilder b = new StringBuilder(text.text.length());
    int from = 0;
    for (int[] range : removed) {
      b.append(text.text, from, range[0]);
      from = range[1];
    }
    b.append(text.text, from, text.text.length());

    final ArrayList<TdApi.TextEntity> entities = new ArrayList<>();
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        final int start = map(removed, entity.offset, false);
        final int end = map(removed, entity.offset + entity.length, true);
        if (end > start) {
          entities.add(new TdApi.TextEntity(start, end - start, entity.type));
        }
      }
    }
    return new TdApi.FormattedText(b.toString(), entities.toArray(new TdApi.TextEntity[0]));
  }

  /** Where {@code index} of the original text is in the cut one (inside a removed range: its start). */
  private static int map (ArrayList<int[]> removed, int index, boolean isEnd) {
    int shift = 0;
    for (int[] range : removed) {
      if (index <= range[0]) {
        break;
      }
      if (index < range[1] || (isEnd && index == range[1])) {
        return range[0] - shift; // inside the removed part: up to where it begins
      }
      shift += range[1] - range[0];
    }
    return index - shift;
  }
}
