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
package com.fahadali.xgram.export;

import android.util.JsonWriter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formatted text (a message's text or caption, a poll's question, a task) as Telegram Desktop exports it.
 * <ul>
 *   <li>JSON (export_data_types.cpp ParseText, export_output_json.cpp SerializeText): the entities flattened as Desktop
 *   does it, so an entity that starts inside the one before it is dropped. "text" is a plain string when nothing is
 *   formatted, else an array of plain strings and {type, text, …} objects; "text_entities" is always an array of
 *   objects, plain runs as "plain". Desktop's type names, with "href", "language", "user_id", "document_id" (a custom
 *   emoji is its alt emoji: not downloaded, so "") and "collapsed".</li>
 *   <li>HTML: Desktop's tags (export_output_html.cpp FormatText) but properly nested, so bold inside a link stays bold.
 *   Links get only safe schemes (http, https, tg, ton, mailto, tel; SafeMessageHref): a "javascript:" text link would
 *   run when the page is opened. Hashtags, cashtags, commands and mentions by name call Desktop's script.js.</li>
 * </ul>
 * Offsets are UTF-16, the same as Java strings.
 */
final class ExportText {
  private ExportText () { }

  static final TdApi.FormattedText EMPTY = new TdApi.FormattedText("", new TdApi.TextEntity[0]);

  /** Telegram's internal links (Desktop's internalLinksDomain). */
  static final String LINKS_DOMAIN = "https://t.me/";
  /** Desktop's kRichTargetMaxBytes, roughly: longer targets are not linked. */
  private static final int MAX_TARGET_LENGTH = 4096;
  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");
  private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]+$");
  private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z0-9_+#.\\-]{1,32}$");

  static boolean isEmpty (@Nullable TdApi.FormattedText text) {
    return text == null || text.text == null || text.text.isEmpty();
  }

  @NonNull
  static TdApi.FormattedText orEmpty (@Nullable TdApi.FormattedText text) {
    return isEmpty(text) ? EMPTY : text;
  }

  /** The plain string, "" for none. */
  @NonNull
  static String plain (@Nullable TdApi.FormattedText text) {
    return isEmpty(text) ? "" : text.text;
  }

  // JSON: Desktop's TextPart list

  /** One run of Desktop's TextPart list. */
  static final class Part {
    static final String PLAIN = "plain";

    @NonNull
    final String type;
    @NonNull
    final String text;
    /** "href", "language", "user_id", "document_id" or "collapsed"; null for none. */
    @Nullable
    final String extraKey;
    /** A String, a Long or a Boolean. */
    @Nullable
    final Object extraValue;

    Part (@NonNull String type, @NonNull String text, @Nullable String extraKey, @Nullable Object extraValue) {
      this.type = type;
      this.text = text;
      this.extraKey = extraKey;
      this.extraValue = extraValue;
    }

    boolean isPlain () {
      return PLAIN.equals(type);
    }
  }

  /** TDLib's order (offset, then the longer first), which is also the server's. */
  private static TdApi.TextEntity[] sorted (@Nullable TdApi.TextEntity[] entities) {
    if (entities == null || entities.length == 0) {
      return new TdApi.TextEntity[0];
    }
    final TdApi.TextEntity[] result = Arrays.copyOf(entities, entities.length);
    Arrays.sort(result, (a, b) -> a.offset != b.offset ? Integer.compare(a.offset, b.offset) : Integer.compare(b.length, a.length));
    return result;
  }

  /**
   * Desktop's ParseText: plain runs and entities in order; an entity that starts before the previous one ended (a
   * nested one) is dropped, as are entities Desktop doesn't know (TDLib's media timestamps, which it derives from the
   * text: they stay plain text). Empty for an empty text.
   */
  @NonNull
  static List<Part> parts (@Nullable TdApi.FormattedText text) {
    if (isEmpty(text)) {
      return Collections.emptyList();
    }
    final String s = text.text;
    final int size = s.length();
    final List<Part> result = new ArrayList<>();
    int offset = 0;
    for (TdApi.TextEntity entity : sorted(text.entities)) {
      if (entity == null || entity.type == null) {
        continue;
      }
      final int start = entity.offset, length = entity.length;
      if (start < offset || length <= 0 || start + length > size) {
        continue;
      }
      final String type = jsonType(entity.type);
      if (type == null) {
        continue;
      }
      if (start > offset) {
        result.add(new Part(Part.PLAIN, s.substring(offset, start), null, null));
      }
      final String part = s.substring(start, start + length);
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypePre.CONSTRUCTOR:
          result.add(new Part(type, part, "language", ""));
          break;
        case TdApi.TextEntityTypePreCode.CONSTRUCTOR: {
          final String language = ((TdApi.TextEntityTypePreCode) entity.type).language;
          result.add(new Part(type, part, "language", language != null ? language : ""));
          break;
        }
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
          final String url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          result.add(new Part(type, part, "href", url != null ? url : ""));
          break;
        }
        case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
          result.add(new Part(type, part, "user_id", ((TdApi.TextEntityTypeMentionName) entity.type).userId));
          break;
        case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
          result.add(new Part(type, part, "document_id", "")); // Desktop's value for a custom emoji it didn't download
          break;
        case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
          result.add(new Part(type, part, "collapsed", false));
          break;
        case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR:
          result.add(new Part(type, part, "collapsed", true));
          break;
        default:
          result.add(new Part(type, part, null, null));
          break;
      }
      offset = start + length;
    }
    if (size > offset) {
      result.add(new Part(Part.PLAIN, s.substring(offset), null, null));
    }
    return result;
  }

  /** Desktop's entity type names; null for one that stays plain text. Unknown types are "unknown", as in Desktop. */
  @Nullable
  private static String jsonType (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
        return "mention";
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
        return "hashtag";
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
        return "cashtag";
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
        return "bot_command";
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return "link";
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
        return "email";
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
        return "phone";
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
        return "bank_card";
      case TdApi.TextEntityTypeTonAddress.CONSTRUCTOR:
        return "ton_address";
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        return "bold";
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        return "italic";
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        return "underline";
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        return "strikethrough";
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        return "spoiler";
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        return "code";
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
        return "pre";
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
      case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR:
        return "blockquote";
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return "text_link";
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return "mention_name";
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        return "custom_emoji";
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
        return null; // TDLib finds these in the text itself; the server (and so Desktop) has no such entity
      case TdApi.TextEntityTypeDateTime.CONSTRUCTOR:
      default:
        return "unknown"; // Desktop: messageEntityFormattedDate and unknown entities
    }
  }

  /** Desktop's SerializeText in its text form: "" for none, a string when nothing is formatted, else an array. */
  static void writeValue (JsonWriter json, @Nullable TdApi.FormattedText text) throws IOException {
    writeValue(json, parts(text));
  }

  private static void writeValue (JsonWriter json, List<Part> parts) throws IOException {
    if (parts.isEmpty()) {
      json.value("");
      return;
    }
    if (parts.size() == 1 && parts.get(0).isPlain()) {
      json.value(parts.get(0).text);
      return;
    }
    json.beginArray();
    for (Part part : parts) {
      if (part.isPlain()) {
        json.value(part.text);
      } else {
        writePart(json, part);
      }
    }
    json.endArray();
  }

  /** A message's "text" and "text_entities" (both always there: "" and [] for none). */
  static void writeMessageText (JsonWriter json, @Nullable TdApi.FormattedText text) throws IOException {
    final List<Part> parts = parts(text);
    json.name("text");
    writeValue(json, parts);
    json.name("text_entities");
    json.beginArray();
    for (Part part : parts) {
      writePart(json, part);
    }
    json.endArray();
  }

  private static void writePart (JsonWriter json, Part part) throws IOException {
    json.beginObject();
    json.name("type").value(part.type);
    json.name("text").value(part.text);
    if (part.extraKey != null && part.extraValue != null) {
      json.name(part.extraKey);
      if (part.extraValue instanceof Long) {
        json.value((long) (Long) part.extraValue);
      } else if (part.extraValue instanceof Boolean) {
        json.value((boolean) (Boolean) part.extraValue);
      } else {
        json.value(part.extraValue.toString());
      }
    }
    json.endObject();
  }

  // HTML

  /** The text as HTML, entities nested: "" for none. Line breaks become &lt;br&gt;, as Desktop writes them. */
  @NonNull
  static String html (@Nullable TdApi.FormattedText text) {
    if (isEmpty(text)) {
      return "";
    }
    final String s = text.text;
    final int size = s.length();
    final TdApi.TextEntity[] entities = sorted(text.entities);
    final StringBuilder b = new StringBuilder(size + 32);
    final int[] stackEnd = new int[entities.length];
    final String[] stackClose = new String[entities.length];
    final boolean[] stackAnchor = new boolean[entities.length];
    int depth = 0, anchors = 0, pos = 0;
    for (TdApi.TextEntity entity : entities) {
      if (entity == null || entity.type == null) {
        continue;
      }
      final int start = entity.offset;
      int end = entity.offset + entity.length;
      if (start < 0 || entity.length <= 0 || end > size) {
        continue;
      }
      // Close what ends before this one starts
      while (depth > 0 && stackEnd[depth - 1] <= start) {
        depth--;
        b.append(ExportHtmlWriter.escape(s.substring(pos, stackEnd[depth])));
        pos = stackEnd[depth];
        b.append(stackClose[depth]);
        if (stackAnchor[depth]) {
          anchors--;
        }
      }
      if (start < pos) {
        continue;
      }
      b.append(ExportHtmlWriter.escape(s.substring(pos, start)));
      pos = start;
      if (depth > 0) {
        end = Math.min(end, stackEnd[depth - 1]); // TDLib nests entities; one that crosses its parent is cut there
      }
      if (end <= start) {
        continue;
      }
      final String[] tag = tag(entity.type, s.substring(entity.offset, entity.offset + entity.length), anchors > 0);
      if (tag == null) {
        continue; // plain: its text goes out with the text around it
      }
      b.append(tag[0]);
      stackEnd[depth] = end;
      stackClose[depth] = tag[1];
      stackAnchor[depth] = tag.length > 2;
      if (stackAnchor[depth]) {
        anchors++;
      }
      depth++;
    }
    while (depth > 0) {
      depth--;
      b.append(ExportHtmlWriter.escape(s.substring(pos, stackEnd[depth])));
      pos = stackEnd[depth];
      b.append(stackClose[depth]);
    }
    if (pos < size) {
      b.append(ExportHtmlWriter.escape(s.substring(pos)));
    }
    return b.toString();
  }

  private static String[] anchor (String open) {
    return new String[] {open, "</a>", ""}; // a third element marks a link: no link goes inside another one
  }

  /**
   * Desktop's FormatText tags for one entity, {open, close} (three elements for a link), or null when it stays plain
   * text: bank cards, TON addresses, dates, media timestamps, a link inside another link, a target that is not safe.
   */
  @Nullable
  private static String[] tag (TdApi.TextEntityType type, String part, boolean insideLink) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        return new String[] {"<strong>", "</strong>"};
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        return new String[] {"<em>", "</em>"};
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        return new String[] {"<u>", "</u>"};
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        return new String[] {"<s>", "</s>"};
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
        return new String[] {"<code>", "</code>"};
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
        return new String[] {"<pre>", "</pre>"};
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR: {
        // The language as the class code highlighters read; Desktop's style.css styles any pre
        final String language = ((TdApi.TextEntityTypePreCode) type).language;
        if (language != null && LANGUAGE.matcher(language).matches()) {
          return new String[] {"<pre class=\"language-" + ExportHtmlWriter.escape(language.toLowerCase(Locale.ROOT)) + "\">", "</pre>"};
        }
        return new String[] {"<pre>", "</pre>"};
      }
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
      case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR:
        return new String[] {"<blockquote>", "</blockquote>"};
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        return new String[] {"<span class=\"spoiler hidden\" onclick=\"ShowSpoiler(this)\"><span aria-hidden=\"true\">", "</span></span>"};
      default:
        break;
    }
    if (insideLink) {
      return null;
    }
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return link(safeHref(((TdApi.TextEntityTypeTextUrl) type).url));
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return link(safeHref(part));
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
        return link(safeEmailHref(part));
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
        return link(safePhoneHref(part));
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
        return part.length() > 1 && part.charAt(0) == '@' && USERNAME.matcher(part.substring(1)).matches() ?
          link(LINKS_DOMAIN + part.substring(1)) : null;
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:
        return anchor("<a href=\"\" onclick=\"return ShowMentionName()\">");
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
        return script("ShowHashtag", part);
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
        return script("ShowCashtag", part);
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
        return script("ShowBotCommand", part);
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
        return anchor(NOT_LOADED_EMOJI); // its alt emoji, as Desktop shows a custom emoji it didn't download
      default:
        return null;
    }
  }

  /** Desktop's FormatCustomEmoji for an emoji that was not downloaded (reactions use it too). */
  static final String NOT_LOADED_EMOJI = "<a href=\"\" onclick=\"return ShowNotLoadedEmoji();\">";

  @Nullable
  private static String[] link (@Nullable String href) {
    return href != null ? anchor("<a href=\"" + ExportHtmlWriter.escape(href) + "\">") : null;
  }

  /** Desktop's onclick for hashtags, cashtags and commands: the text without its first character, as a JS string. */
  private static String[] script (String function, String part) {
    final String value = part.length() > 1 ? part.substring(1) : "";
    return anchor("<a href=\"\" onclick=\"return " + function + "(" + ExportHtmlWriter.escape(jsString(value)) + ")\">");
  }

  /** A double-quoted JavaScript string literal. */
  static String jsString (String value) {
    final StringBuilder b = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '\\':
          b.append("\\\\");
          break;
        case '"':
          b.append("\\\"");
          break;
        case '\n':
          b.append("\\n");
          break;
        case '\r':
          b.append("\\r");
          break;
        case ' ':
          b.append("\\u2028");
          break;
        case ' ':
          b.append("\\u2029");
          break;
        default:
          b.append(c);
          break;
      }
    }
    return b.append('"').toString();
  }

  // Safe link targets (Desktop's SafeMessageHref, SafeEmailHref, SafePhoneHref)

  /** Desktop's IsStrictTarget: no control codes or line separators, no surrounding spaces, not too long. */
  private static boolean isStrictTarget (@Nullable String value) {
    if (value == null || value.isEmpty() || value.length() > MAX_TARGET_LENGTH || !value.trim().equals(value)) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (c < 32 || c == 127 || (c >= 0x80 && c <= 0x9F) || c == ' ' || c == ' ') {
        return false;
      }
    }
    return true;
  }

  /** A percent-encoded control code ("%0A", "%7F") would come back as one in the browser. */
  private static boolean hasEncodedControl (String value) {
    for (int i = 0; i + 2 < value.length(); i++) {
      if (value.charAt(i) != '%') {
        continue;
      }
      final int high = Character.digit(value.charAt(i + 1), 16), low = Character.digit(value.charAt(i + 2), 16);
      if (high < 0 || low < 0) {
        continue;
      }
      final int decoded = (high << 4) | low;
      if (decoded < 32 || decoded == 127) {
        return true;
      }
    }
    return false;
  }

  /**
   * A link target that is safe to put in the page, or null: http and https (with a host), tg, ton, mailto and tel; no
   * scheme means https, as in Desktop. Characters a URL can't hold are percent-encoded.
   */
  @Nullable
  static String safeHref (@Nullable String value) {
    if (!isStrictTarget(value) || value.startsWith("//") || hasEncodedControl(value)) {
      return null;
    }
    String url = value;
    if (!SCHEME.matcher(url).find()) {
      url = "https://" + url;
    }
    final int colon = url.indexOf(':');
    final String scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
    switch (scheme) {
      case "http":
      case "https": {
        if (!url.startsWith("//", colon + 1)) {
          return null;
        }
        final int hostStart = colon + 3;
        int hostEnd = hostStart;
        while (hostEnd < url.length() && "/?#".indexOf(url.charAt(hostEnd)) < 0) {
          hostEnd++;
        }
        String host = url.substring(hostStart, hostEnd);
        final int at = host.lastIndexOf('@');
        if (at >= 0) {
          host = host.substring(at + 1);
        }
        final int port = host.startsWith("[") ? host.indexOf(']') + 1 : host.indexOf(':');
        if (port > 0 && port < host.length() && host.charAt(port) == ':') {
          host = host.substring(0, port);
        }
        if (host.isEmpty() || host.indexOf(' ') >= 0) {
          return null;
        }
        break;
      }
      case "tg":
      case "ton":
      case "mailto":
      case "tel":
        if (url.length() == colon + 1) {
          return null;
        }
        break;
      default:
        return null;
    }
    return scheme + encodeUnsafe(url.substring(colon));
  }

  /** Characters a URL can't hold as they are, percent-encoded; letters of any script stay (the page is UTF-8). */
  private static String encodeUnsafe (String value) {
    final StringBuilder b = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (c == ' ' || c == '"' || c == '<' || c == '>' || c == '`' || c == '{' || c == '}' || c == '|' || c == '\\' || c == '^') {
        b.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16))).append(Character.toUpperCase(Character.forDigit(c & 0x0F, 16)));
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  /** Desktop's SafeEmailHref: one "@", inside the address, no spaces or URL characters. */
  @Nullable
  private static String safeEmailHref (String address) {
    if (!isStrictTarget(address)) {
      return null;
    }
    final int at = address.indexOf('@');
    if (at <= 0 || at != address.lastIndexOf('@') || at + 1 == address.length()) {
      return null;
    }
    for (int i = 0; i < address.length(); i++) {
      final char c = address.charAt(i);
      if (Character.isWhitespace(c) || "/\\:%?#&=".indexOf(c) >= 0) {
        return null;
      }
    }
    return "mailto:" + address;
  }

  /** Desktop's SafePhoneHref: digits with + - . ( ) and spaces, at least one digit. */
  @Nullable
  private static String safePhoneHref (String phone) {
    if (!isStrictTarget(phone)) {
      return null;
    }
    boolean digit = false;
    for (int i = 0; i < phone.length(); i++) {
      final char c = phone.charAt(i);
      if (c >= '0' && c <= '9') {
        digit = true;
      } else if ("+-.() ".indexOf(c) < 0) {
        return null;
      }
    }
    return digit ? "tel:" + phone : null;
  }
}
