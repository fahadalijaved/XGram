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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

/**
 * Turns a text selection into the quote TDLib takes: exactly that part of the message text (UTF-16 offsets), trimmed
 * like official Telegram does, never splitting a character, a custom emoji or a date, with only the entities TDLib
 * keeps in quotes.
 */
public final class XgramQuoteText {
  private XgramQuoteText () { }

  public static final int DEFAULT_LENGTH_MAX = 1024;

  public static final class Quote {
    public final TdApi.InputTextQuote quote;
    /** Longer than TDLib allows: the end was left out. */
    public final boolean wasCut;

    Quote (TdApi.InputTextQuote quote, boolean wasCut) {
      this.quote = quote;
      this.wasCut = wasCut;
    }
  }

  /** Entities TDLib keeps in a quote, and expects there. */
  private static boolean isKept (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeDateTime.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  /** Quoted whole or not at all. */
  private static boolean isWhole (TdApi.TextEntityType type) {
    int constructor = type.getConstructor();
    return constructor == TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR || constructor == TdApi.TextEntityTypeDateTime.CONSTRUCTOR;
  }

  private static boolean isSpace (char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  /** Nothing to quote: empty or only spaces. */
  public static boolean isBlank (String text) {
    if (text != null) {
      for (int i = 0; i < text.length(); i++) {
        if (!isSpace(text.charAt(i))) {
          return false;
        }
      }
    }
    return true;
  }

  private static TdApi.TextEntity[] entitiesOf (TdApi.FormattedText text) {
    return text.entities != null ? text.entities : new TdApi.TextEntity[0];
  }

  private static BreakIterator characters (String text) {
    BreakIterator iterator = BreakIterator.getCharacterInstance();
    iterator.setText(text);
    return iterator;
  }

  private static boolean isBoundary (BreakIterator characters, int offset, int length) {
    if (offset <= 0 || offset >= length) {
      return true;
    }
    try {
      return characters.isBoundary(offset);
    } catch (IllegalArgumentException e) {
      return true;
    }
  }

  private static boolean splitsSurrogates (String text, int offset) {
    return offset > 0 && offset < text.length() && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset));
  }

  /** Moves the start back to where a character, custom emoji or date begins. */
  private static int snapStart (String text, TdApi.TextEntity[] entities, BreakIterator characters, int start) {
    for (int pass = 0; pass < 3; pass++) {
      int before = start;
      for (TdApi.TextEntity entity : entities) {
        if (isWhole(entity.type) && entity.offset < start && start < entity.offset + entity.length) {
          start = entity.offset;
        }
      }
      if (!isBoundary(characters, start, text.length())) {
        start = Math.max(0, characters.preceding(start));
      }
      if (splitsSurrogates(text, start)) {
        start--;
      }
      if (start == before) {
        break;
      }
    }
    return start;
  }

  /** Moves the end forward to where a character, custom emoji or date ends. */
  private static int snapEnd (String text, TdApi.TextEntity[] entities, BreakIterator characters, int end) {
    for (int pass = 0; pass < 3; pass++) {
      int before = end;
      for (TdApi.TextEntity entity : entities) {
        if (isWhole(entity.type) && entity.offset < end && end < entity.offset + entity.length) {
          end = entity.offset + entity.length;
        }
      }
      if (!isBoundary(characters, end, text.length())) {
        int next = characters.following(end);
        end = next == BreakIterator.DONE ? text.length() : next;
      }
      if (splitsSurrogates(text, end)) {
        end++;
      }
      if (end == before) {
        break;
      }
    }
    return Math.min(end, text.length());
  }

  /** Moves a cut end back so it leaves out a split character, custom emoji or date. */
  private static int cutEnd (String text, TdApi.TextEntity[] entities, BreakIterator characters, int start, int end) {
    int cut = end;
    for (int pass = 0; pass < 3; pass++) {
      int before = cut;
      for (TdApi.TextEntity entity : entities) {
        if (isWhole(entity.type) && entity.offset < cut && cut < entity.offset + entity.length) {
          cut = entity.offset;
        }
      }
      if (!isBoundary(characters, cut, text.length())) {
        int previous = characters.preceding(cut);
        cut = previous == BreakIterator.DONE ? start : previous;
      }
      if (splitsSurrogates(text, cut)) {
        cut--;
      }
      if (cut == before) {
        break;
      }
    }
    if (cut <= start) {
      // One character longer than the limit: only keep the surrogate pair whole.
      cut = splitsSurrogates(text, end) ? end - 1 : end;
    }
    return cut;
  }

  /**
   * The selected range [start, end) after snapping it to whole characters, custom emoji and dates, and trimming
   * spaces at both ends. Nothing selected (or only spaces) means the whole text. Null when the text is only spaces.
   */
  public static int[] range (TdApi.FormattedText text, int selectionStart, int selectionEnd) {
    String s = text.text != null ? text.text : "";
    int length = s.length();
    int start = Math.max(0, Math.min(length, Math.min(selectionStart, selectionEnd)));
    int end = Math.max(0, Math.min(length, Math.max(selectionStart, selectionEnd)));
    int[] range = start < end ? trimmedRange(s, entitiesOf(text), start, end) : null;
    if (range == null) {
      range = trimmedRange(s, entitiesOf(text), 0, length);
    }
    return range;
  }

  private static int[] trimmedRange (String s, TdApi.TextEntity[] entities, int start, int end) {
    BreakIterator characters = characters(s);
    start = snapStart(s, entities, characters, start);
    end = snapEnd(s, entities, characters, end);
    while (start < end && isSpace(s.charAt(start))) {
      start++;
    }
    while (end > start && isSpace(s.charAt(end - 1))) {
      end--;
    }
    return start < end ? new int[] {start, end} : null;
  }

  /** The quote of the selection (see {@link #range}), at most maxLength UTF-16 code units long. Null for an empty text. */
  public static Quote quote (TdApi.FormattedText text, int selectionStart, int selectionEnd, int maxLength) {
    int[] range = range(text, selectionStart, selectionEnd);
    if (range == null) {
      return null;
    }
    String s = text.text;
    TdApi.TextEntity[] entities = entitiesOf(text);
    int start = range[0], end = range[1];
    boolean wasCut = false;
    if (maxLength <= 0) {
      maxLength = DEFAULT_LENGTH_MAX;
    }
    if (end - start > maxLength) {
      end = cutEnd(s, entities, characters(s), start, start + maxLength);
      while (end > start && isSpace(s.charAt(end - 1))) {
        end--;
      }
      wasCut = true;
      if (end <= start) {
        return null;
      }
    }
    List<TdApi.TextEntity> kept = new ArrayList<>();
    for (TdApi.TextEntity entity : entities) {
      if (entity.type == null || !isKept(entity.type)) {
        continue;
      }
      int entityEnd = entity.offset + entity.length;
      int from = Math.max(entity.offset, start), to = Math.min(entityEnd, end);
      if (to <= from || (isWhole(entity.type) && (from != entity.offset || to != entityEnd))) {
        continue;
      }
      kept.add(new TdApi.TextEntity(from - start, to - from, entity.type));
    }
    TdApi.FormattedText quoteText = new TdApi.FormattedText(s.substring(start, end), kept.toArray(new TdApi.TextEntity[0]));
    return new Quote(new TdApi.InputTextQuote(quoteText, start), wasCut);
  }

  /**
   * The quote, taken from quotedText, for the message text as it is now: unchanged if the text is the same, else moved
   * to where the edited text has the same quote. Null when it no longer has it, as Telegram would refuse the reply.
   */
  @Nullable
  public static TdApi.InputTextQuote relocate (TdApi.InputTextQuote quote, TdApi.FormattedText quotedText, @Nullable TdApi.FormattedText currentText) {
    if (currentText == null || isBlank(currentText.text)) {
      return null;
    }
    if (Td.equalsTo(quotedText, currentText)) {
      return quote;
    }
    TdApi.FoundPosition found = Tdlib.executeOrNull(new TdApi.SearchQuote(currentText, quote.text, quote.position));
    return found != null && found.position >= 0 ? new TdApi.InputTextQuote(quote.text, found.position) : null;
  }
}
