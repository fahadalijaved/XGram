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
package com.fahadali.xgram.activity;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a message is to Activity, and the small rules around it. Pure functions: no Android or Tdlib state, so the
 * debug self test can run them as they are.
 * <p>
 * Telegram has one server flag for "this needs you" (contains_unread_mention, and the Mention / UnreadMention search
 * filters): it is set for mentions and for replies to your messages alike. A message is a mention when it has a
 * mention entity for you, a reply when it replies to one of your messages in the same chat, and a mention otherwise
 * (a mention by an old username or by name).
 */
public final class XgramActivityClassifier {
  private XgramActivityClassifier () { }

  /** {@link #classify} packs the kind in the low 4 bits and the item flags above them. */
  public static final int KIND_MASK = 0xf, FLAGS_SHIFT = 4;

  public static int kindOf (int classified) {
    return classified & KIND_MASK;
  }

  public static int flagsOf (int classified) {
    return classified >>> FLAGS_SHIFT;
  }

  /**
   * The kind (and flags) of an incoming message for you ({@code me}, with the {@code usernames} you can be mentioned
   * by), 0 for none. {@code fromSearch}: found by a mention search, so the server counts it as one even when its
   * flag is already cleared.
   */
  public static int classify (TdApi.Message m, long me, @Nullable String[] usernames, boolean fromSearch) {
    if (m == null || m.isOutgoing) {
      return 0;
    }
    final boolean flagged = m.containsUnreadMention || fromSearch;
    final boolean reply = isSameChatReply(m);
    if (findMentionSpan(textOf(m.content), me, usernames) != null) {
      int flags = reply && flagged ? XgramActivityItem.FLAG_ALSO_REPLY : 0;
      return XgramActivityItem.KIND_MENTION | (flags << FLAGS_SHIFT);
    }
    if (reply && flagged) {
      return XgramActivityItem.KIND_REPLY;
    }
    if (flagged) {
      return XgramActivityItem.KIND_MENTION;
    }
    return 0;
  }

  /** A reply to a message of the same chat (replies to other chats are quotes from elsewhere). */
  public static boolean isSameChatReply (TdApi.Message m) {
    if (!(m.replyTo instanceof TdApi.MessageReplyToMessage)) {
      return false;
    }
    TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) m.replyTo;
    return replyTo.messageId != 0 && (replyTo.chatId == 0 || replyTo.chatId == m.chatId);
  }

  @Nullable
  public static TdApi.MessageReplyToMessage replyToOf (TdApi.Message m) {
    return m.replyTo instanceof TdApi.MessageReplyToMessage ? (TdApi.MessageReplyToMessage) m.replyTo : null;
  }

  /** The text or caption of a message, or null. */
  @Nullable
  public static TdApi.FormattedText textOf (@Nullable TdApi.MessageContent content) {
    if (content == null) {
      return null;
    }
    switch (content.getConstructor()) {
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        return ((TdApi.MessagePaidMedia) content).caption;
      default:
        return com.fahadali.xgram.history.XgramMedia.formattedTextOf(content);
    }
  }

  /**
   * Where you are mentioned in {@code text}: {offset, length} of the first mention entity for you (a mention by
   * name, or an @username of yours in any case), or null.
   */
  @Nullable
  public static int[] findMentionSpan (@Nullable TdApi.FormattedText text, long me, @Nullable String[] usernames) {
    if (text == null || text.text == null || text.entities == null) {
      return null;
    }
    for (TdApi.TextEntity entity : text.entities) {
      if (entity == null || entity.type == null || entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.text.length()) {
        continue;
      }
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: {
          if (me != 0 && ((TdApi.TextEntityTypeMentionName) entity.type).userId == me) {
            return new int[] {entity.offset, entity.length};
          }
          break;
        }
        case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
          if (usernames != null && entity.length > 1) {
            String mention = text.text.substring(entity.offset, entity.offset + entity.length);
            String name = mention.charAt(0) == '@' ? mention.substring(1) : mention;
            for (String username : usernames) {
              if (username != null && !username.isEmpty() && username.equalsIgnoreCase(name)) {
                return new int[] {entity.offset, entity.length};
              }
            }
          }
          break;
        }
      }
    }
    return null;
  }

  /**
   * The text saved for a row: spoilers masked, at most {@code max} UTF-16 units (a surrogate pair is never cut).
   * Null for an empty text.
   */
  @Nullable
  public static String snapshotText (@Nullable TdApi.FormattedText text, int max) {
    if (text == null || text.text == null || text.text.isEmpty()) {
      return null;
    }
    char[] chars = text.text.toCharArray();
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity != null && entity.type != null && entity.type.getConstructor() == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR) {
          int end = Math.min(chars.length, entity.offset + entity.length);
          for (int i = Math.max(0, entity.offset); i < end; i++) {
            if (!Character.isWhitespace(chars[i])) {
              chars[i] = '▒';
            }
          }
        }
      }
    }
    int length = chars.length;
    if (length > max) {
      length = max;
      if (length > 0 && Character.isHighSurrogate(chars[length - 1])) {
        length--;
      }
    }
    return new String(chars, 0, length);
  }

  /** "e:<emoji>", "c:<custom emoji id>" or "p" for a paid reaction (a star). */
  public static String reactionKey (@Nullable TdApi.ReactionType type) {
    if (type instanceof TdApi.ReactionTypeEmoji) {
      return "e:" + ((TdApi.ReactionTypeEmoji) type).emoji;
    }
    if (type instanceof TdApi.ReactionTypeCustomEmoji) {
      return "c:" + ((TdApi.ReactionTypeCustomEmoji) type).customEmojiId;
    }
    return "p";
  }

  /** The emoji of {@code key}, null for a custom emoji or a paid reaction. */
  @Nullable
  public static String reactionEmoji (@Nullable String key) {
    return key != null && key.startsWith("e:") ? key.substring(2) : null;
  }

  /** The custom emoji id of {@code key}, 0 for anything else. */
  public static long reactionCustomEmojiId (@Nullable String key) {
    if (key != null && key.startsWith("c:")) {
      try {
        return Long.parseLong(key.substring(2));
      } catch (NumberFormatException ignored) { }
    }
    return 0;
  }

  /**
   * A sender as one number, as TDLib numbers chats: a user is its (positive) user id, a chat (an anonymous admin, a
   * channel) its (negative) chat id. 0 when unknown.
   */
  public static long encodeSender (@Nullable TdApi.MessageSender sender) {
    if (sender instanceof TdApi.MessageSenderUser) {
      return ((TdApi.MessageSenderUser) sender).userId;
    }
    if (sender instanceof TdApi.MessageSenderChat) {
      return ((TdApi.MessageSenderChat) sender).chatId;
    }
    return 0;
  }

  @Nullable
  public static TdApi.MessageSender decodeSender (long sender) {
    if (sender > 0) {
      return new TdApi.MessageSenderUser(sender);
    }
    if (sender < 0) {
      return new TdApi.MessageSenderChat(sender);
    }
    return null;
  }

  // Flood waits

  private static final Pattern RETRY_AFTER = Pattern.compile("retry after (\\d+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern FLOOD_WAIT = Pattern.compile("FLOOD_WAIT_(\\d+)");

  /** The wait a flood error asks for, in seconds (no cap); -1 when it is not a flood error. */
  public static long retryAfterSeconds (@Nullable TdApi.Error error) {
    if (error == null || error.message == null) {
      return -1;
    }
    Matcher m = RETRY_AFTER.matcher(error.message);
    if (!m.find()) {
      m = FLOOD_WAIT.matcher(error.message);
      if (!m.find()) {
        return error.code == 429 ? 0 : -1;
      }
    }
    try {
      return Math.max(0, Long.parseLong(m.group(1)));
    } catch (NumberFormatException e) {
      return error.code == 429 ? 0 : -1;
    }
  }

  public static final long MIN_BACKOFF_MS = 60_000L, MAX_BACKOFF_MS = 6L * 60L * 60_000L;

  /**
   * How long the whole queue waits after a flood error asking for {@code retryMs}: 1.2 times that, at least a minute,
   * doubled for every flood in a row ({@code consecutive} 1 for the first), at most 6 hours.
   */
  public static long nextBackoffMs (long retryMs, int consecutive) {
    long base = Math.max((long) (Math.max(0L, retryMs) * 1.2), MIN_BACKOFF_MS);
    int doublings = Math.max(0, Math.min(16, consecutive - 1));
    long value = base;
    for (int i = 0; i < doublings && value < MAX_BACKOFF_MS; i++) {
      value *= 2;
    }
    return Math.min(MAX_BACKOFF_MS, value);
  }

  // Sections

  public static final int SECTION_TODAY = 0, SECTION_YESTERDAY = 1, SECTION_THIS_WEEK = 2, SECTION_EARLIER = 3;

  /** Today, Yesterday, This week (the last 7 days) or Earlier, in the device's time zone. Dates are unix seconds. */
  public static int sectionOf (long eventDate, long now) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(now * 1000L);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    long todayStart = c.getTimeInMillis() / 1000L;
    if (eventDate >= todayStart) {
      return SECTION_TODAY;
    }
    c.add(Calendar.DAY_OF_YEAR, -1);
    if (eventDate >= c.getTimeInMillis() / 1000L) {
      return SECTION_YESTERDAY;
    }
    c.add(Calendar.DAY_OF_YEAR, -5);
    if (eventDate >= c.getTimeInMillis() / 1000L) {
      return SECTION_THIS_WEEK;
    }
    return SECTION_EARLIER;
  }

  /** Replies to the same message of yours within this long of the newest one are one row. */
  public static final int REPLY_GROUP_SECONDS = 24 * 60 * 60;

  /** Whether a reply at {@code date} joins a row whose newest reply is at {@code newestDate}. */
  public static boolean joinsReplyGroup (int newestDate, int date) {
    return newestDate - date <= REPLY_GROUP_SECONDS && date <= newestDate;
  }
}
