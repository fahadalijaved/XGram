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

import android.util.SparseArray;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;
import tgx.td.ChatId;

/**
 * The icon a chat folder shows. TDLib calls nearly every folder made by hand "Custom" (any folder with chats of its own,
 * or with an emoji outside its 30), and Telegram X draws Custom as the same folder glyph for every folder. In order:
 * <ol>
 *   <li>the icon chosen (or TDLib's default for the folder), unless it is Custom;</li>
 *   <li>a custom emoji at the start of the name (or at its end);</li>
 *   <li>an emoji at the start of the name (or at its end): one of TDLib's 30 as its glyph, any other drawn as it is;</li>
 *   <li>a word in the name (Work, Games, News...);</li>
 *   <li>what the folder holds, when nearly all of it is one kind of chat (asked once per folder, then kept);</li>
 *   <li>the folder glyph.</li>
 * </ol>
 * Custom chosen by hand stops after 3. On a tab, the emoji that became the icon leaves the label. Menus and lists (a
 * glyph only, the whole name next to it) take the emoji's glyph only when it is one of the 30. Nothing here waits on
 * TDLib: what a folder holds comes later, and {@link Listener}s hear when it changes a folder's icon.
 */
public final class XgramFolderIcons {
  private XgramFolderIcons () { }

  private static final @DrawableRes int FOLDER = R.drawable.baseline_folder_24;
  private static final String CUSTOM = "Custom";

  /** What a folder's tab shows. */
  public static final class Icon {
    /** The glyph: the icon itself, or what stands in while an emoji can't be drawn. Never 0. */
    public final @DrawableRes int iconRes;
    /** An emoji drawn in the glyph's place, or a custom emoji's own emoji while it loads; null for a glyph. */
    public final @Nullable String emoji;
    /** A custom emoji drawn in the glyph's place, or 0. */
    public final long customEmojiId;
    /** Whether the custom emoji may play (the folder's own setting). */
    public final boolean animate;
    /** The name without the emoji that became the icon (empty when the name was only that). */
    public final CharSequence label;
    /** The whole name as text, for TalkBack. */
    public final String description;

    Icon (@DrawableRes int iconRes, @Nullable String emoji, long customEmojiId, boolean animate, CharSequence label, String description) {
      this.iconRes = iconRes;
      this.emoji = emoji;
      this.customEmojiId = customEmojiId;
      this.animate = animate;
      this.label = label;
      this.description = description;
    }
  }

  public interface Listener {
    /** What {@code chatFolderId} holds became known and changes its icon: build its tab again. */
    @UiThread
    void onFolderIconChanged (Tdlib tdlib, int chatFolderId);
  }

  private static final ReferenceList<Listener> listeners = new ReferenceList<>(true);

  /** Held weakly: keep a reference to {@code listener}. */
  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  // Resolving

  /** The tab of a folder: its icon (a glyph, an emoji or a custom emoji) and its label without that emoji. */
  @UiThread
  public static Icon resolve (Tdlib tdlib, TdApi.ChatFolderInfo info) {
    final CharSequence wholeName = TD.toCharSequence(info.name);
    final String description = info.name.text.text;
    final String iconName = info.icon != null ? info.icon.name : null;
    if (!StringUtils.isEmpty(iconName) && !CUSTOM.equals(iconName)) {
      return new Icon(TD.iconByName(iconName, FOLDER), null, 0, false, wholeName, description);
    }
    NameIcon nameIcon = findNameIcon(info.name.text);
    if (nameIcon != null) {
      CharSequence label = TD.toCharSequence(new TdApi.ChatFolderName(nameIcon.strip(info.name.text), info.name.animateCustomEmoji));
      if (nameIcon.customEmojiId != 0) {
        String fallback = nameIcon.drawable ? nameIcon.emoji : null;
        return new Icon(nameIcon.glyph(), fallback, nameIcon.customEmojiId, info.name.animateCustomEmoji, label, description);
      }
      if (nameIcon.tableIconName != null) {
        return new Icon(nameIcon.glyph(), null, 0, false, label, description);
      }
      if (nameIcon.drawable) {
        return new Icon(FOLDER, nameIcon.emoji, 0, false, label, description);
      }
    }
    return new Icon(guessIcon(tdlib, info, description), null, 0, false, wholeName, description);
  }

  /** A glyph for a folder in a menu or a list, where the whole name stands next to it. */
  @UiThread
  public static @DrawableRes int iconRes (Tdlib tdlib, TdApi.ChatFolderInfo info) {
    final String iconName = info.icon != null ? info.icon.name : null;
    if (!StringUtils.isEmpty(iconName) && !CUSTOM.equals(iconName)) {
      return TD.iconByName(iconName, FOLDER);
    }
    NameIcon nameIcon = findNameIcon(info.name.text);
    if (nameIcon != null && nameIcon.tableIconName != null) {
      return nameIcon.glyph();
    }
    return guessIcon(tdlib, info, info.name.text.text);
  }

  /**
   * A glyph for a folder whose settings are at hand (the folder editor, Add to folder), as it is now, edits included:
   * nothing is asked of TDLib.
   */
  @AnyThread
  public static @DrawableRes int iconRes (Tdlib tdlib, TdApi.ChatFolder folder) {
    final boolean chosen = folder.icon != null && !StringUtils.isEmpty(folder.icon.name);
    final String iconName = chosen ? folder.icon.name : defaultIconName(folder);
    if (!CUSTOM.equals(iconName)) {
      return TD.iconByName(iconName, FOLDER);
    }
    NameIcon nameIcon = folder.name != null ? findNameIcon(folder.name.text) : null;
    if (nameIcon != null && nameIcon.tableIconName != null) {
      return nameIcon.glyph();
    }
    if (chosen) {
      return FOLDER; // the folder glyph, chosen by hand
    }
    String keywordIcon = folder.name != null ? keywordIconName(folder.name.text.text) : null;
    if (keywordIcon != null) {
      return TD.iconByName(keywordIcon, FOLDER);
    }
    String contentIcon = contentIconName(tdlib, folder);
    return contentIcon != null ? TD.iconByName(contentIcon, FOLDER) : FOLDER;
  }

  /** Steps 4 to 6 for a folder known by its info: a word in the name, then what it holds (asked once, then kept). */
  private static @DrawableRes int guessIcon (Tdlib tdlib, TdApi.ChatFolderInfo info, String name) {
    Content content = content(tdlib, info);
    if (content != null && content.chosenCustom) {
      return FOLDER;
    }
    String keywordIcon = keywordIconName(name);
    if (keywordIcon != null) {
      return TD.iconByName(keywordIcon, FOLDER);
    }
    if (content != null && content.iconName != null) {
      return TD.iconByName(content.iconName, FOLDER);
    }
    return FOLDER;
  }

  /** TDLib's default icon for a folder without a chosen one (DialogFilter::get_chosen_or_default_icon_name), without asking it. */
  @AnyThread
  public static String defaultIconName (TdApi.ChatFolder folder) {
    if (notEmpty(folder.pinnedChatIds) || notEmpty(folder.includedChatIds) || notEmpty(folder.excludedChatIds)) {
      return CUSTOM;
    }
    if (folder.includeContacts || folder.includeNonContacts) {
      if (!folder.includeBots && !folder.includeGroups && !folder.includeChannels) {
        return "Private";
      }
    } else {
      if (!folder.includeBots && !folder.includeChannels) {
        return folder.includeGroups ? "Groups" : CUSTOM;
      }
      if (!folder.includeBots && !folder.includeGroups) {
        return "Channels";
      }
      if (!folder.includeGroups && !folder.includeChannels) {
        return "Bots";
      }
    }
    if (folder.excludeRead && !folder.excludeMuted) {
      return "Unread";
    }
    if (folder.excludeMuted && !folder.excludeRead) {
      return "Unmuted";
    }
    return CUSTOM;
  }

  private static boolean notEmpty (@Nullable long[] ids) {
    return ids != null && ids.length > 0;
  }

  // The name's emoji

  /** TDLib's 30 folder emoji (DialogFilter::init_icon_names, variation selectors dropped) and their icons. */
  private static final SparseArray<String> TABLE = new SparseArray<>();

  static {
    final int[] emoji = {
      0x1F4AC, 0x2705, 0x1F514, 0x1F916, 0x1F4E2, 0x1F465, 0x1F464, 0x1F4C1, 0x1F4CB, 0x1F431,
      0x1F451, 0x2B50, 0x1F339, 0x1F3AE, 0x1F3E0, 0x2764, 0x1F3AD, 0x1F378, 0x26BD, 0x1F393,
      0x1F4C8, 0x2708, 0x1F4BC, 0x1F6EB, 0x1F4D5, 0x1F4A1, 0x1F44D, 0x1F4B0, 0x1F3B5, 0x1F3A8
    };
    final String[] names = {
      "All", "Unread", "Unmuted", "Bots", "Channels", "Groups", "Private", CUSTOM, "Setup", "Cat",
      "Crown", "Favorite", "Flower", "Game", "Home", "Love", "Mask", "Party", "Sport", "Study",
      "Trade", "Travel", "Work", "Airplane", "Book", "Light", "Like", "Money", "Note", "Palette"
    };
    for (int i = 0; i < emoji.length; i++) {
      TABLE.put(emoji[i], names[i]);
    }
  }

  /** An emoji or custom emoji at the start of a folder's name (or at its end), and where it is. */
  private static final class NameIcon {
    final int start, end; // the emoji itself
    final String emoji; // its text: a custom emoji's own emoji
    final long customEmojiId;
    final @Nullable String tableIconName; // one of TDLib's 30
    final boolean drawable; // Telegram X can draw the emoji

    NameIcon (int start, int end, String emoji, long customEmojiId) {
      this.start = start;
      this.end = end;
      this.emoji = emoji;
      this.customEmojiId = customEmojiId;
      this.tableIconName = tableIconName(emoji);
      this.drawable = Emoji.instance().getEmojiInfo(emoji) != null;
    }

    @DrawableRes int glyph () {
      return tableIconName != null ? TD.iconByName(tableIconName, FOLDER) : FOLDER;
    }

    /** The name without the emoji and the spaces between it and the rest. */
    TdApi.FormattedText strip (TdApi.FormattedText text) {
      final String s = text.text;
      int cutStart = start, cutEnd = end;
      if (start == firstVisible(s)) {
        cutStart = 0;
        while (cutEnd < s.length() && Character.isWhitespace(s.charAt(cutEnd))) {
          cutEnd++;
        }
      } else {
        cutEnd = s.length();
        while (cutStart > 0 && Character.isWhitespace(s.charAt(cutStart - 1))) {
          cutStart--;
        }
      }
      final int cutLength = cutEnd - cutStart;
      ArrayList<TdApi.TextEntity> entities = new ArrayList<>();
      if (text.entities != null) {
        for (TdApi.TextEntity entity : text.entities) {
          if (entity.offset + entity.length <= cutStart) {
            entities.add(entity);
          } else if (entity.offset >= cutEnd) {
            entities.add(new TdApi.TextEntity(entity.offset - cutLength, entity.length, entity.type));
          } // one across the cut goes (a folder name has only custom emoji)
        }
      }
      return new TdApi.FormattedText(s.substring(0, cutStart) + s.substring(cutEnd), entities.toArray(new TdApi.TextEntity[0]));
    }
  }

  private static int firstVisible (String s) {
    int i = 0;
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int lastVisibleEnd (String s) {
    int i = s.length();
    while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) {
      i--;
    }
    return i;
  }

  /** The name's icon: a custom emoji, then an emoji, at its start, else at its end; null when it has none. */
  private static @Nullable NameIcon findNameIcon (@Nullable TdApi.FormattedText text) {
    if (text == null || StringUtils.isEmpty(text.text)) {
      return null;
    }
    final String s = text.text;
    final int start = firstVisible(s), end = lastVisibleEnd(s);
    if (start >= end) {
      return null;
    }
    TdApi.TextEntity first = null, last = null;
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.type.getConstructor() != TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR || entity.length <= 0 || entity.offset + entity.length > s.length()) {
          continue;
        }
        if (entity.offset == start) {
          first = entity;
        } else if (entity.offset + entity.length == end) {
          last = entity;
        }
      }
    }
    if (first != null) {
      return customEmojiIcon(s, first);
    }
    int emojiEnd = emojiEnd(s, start);
    if (emojiEnd != -1 && isEmojiIcon(s, start, emojiEnd)) {
      return new NameIcon(start, emojiEnd, s.substring(start, emojiEnd), 0);
    }
    if (last != null) {
      return customEmojiIcon(s, last);
    }
    int emojiStart = lastEmojiStart(s, start, end);
    if (emojiStart != -1 && isEmojiIcon(s, emojiStart, end)) {
      return new NameIcon(emojiStart, end, s.substring(emojiStart, end), 0);
    }
    return null;
  }

  private static NameIcon customEmojiIcon (String s, TdApi.TextEntity entity) {
    return new NameIcon(entity.offset, entity.offset + entity.length, s.substring(entity.offset, entity.offset + entity.length), ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId);
  }

  /** An emoji that can be an icon: one of TDLib's 30, or one Telegram X draws. */
  private static boolean isEmojiIcon (String s, int start, int end) {
    String emoji = s.substring(start, end);
    return tableIconName(emoji) != null || Emoji.instance().getEmojiInfo(emoji) != null;
  }

  /** The icon of one of TDLib's 30 folder emoji, with or without variation selectors and skin tones; null for any other. */
  private static @Nullable String tableIconName (String emoji) {
    int found = 0;
    for (int i = 0; i < emoji.length(); ) {
      int codePoint = emoji.codePointAt(i);
      i += Character.charCount(codePoint);
      if (codePoint == 0xFE0F || codePoint == 0xFE0E || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF)) {
        continue;
      }
      if (found != 0) {
        return null; // more than one emoji: a sequence of its own
      }
      found = codePoint;
    }
    return found != 0 ? TABLE.get(found) : null;
  }

  /** The end of the emoji starting at {@code start}: a pictograph with its modifiers and joined ones, a flag or a keycap; -1 for none. */
  private static int emojiEnd (String s, int start) {
    final int length = s.length();
    if (start >= length) {
      return -1;
    }
    final int codePoint = s.codePointAt(start);
    int i = start + Character.charCount(codePoint);
    if (isRegionalIndicator(codePoint)) {
      return i < length && isRegionalIndicator(s.codePointAt(i)) ? i + Character.charCount(s.codePointAt(i)) : -1;
    }
    if ((codePoint >= '0' && codePoint <= '9') || codePoint == '#' || codePoint == '*') {
      if (i < length && s.charAt(i) == '️') {
        i++;
      }
      return i < length && s.charAt(i) == '⃣' ? i + 1 : -1;
    }
    if (!isPictograph(codePoint)) {
      return -1;
    }
    // A symbol that is text unless asked otherwise (©, ↔, ❤) counts only with its emoji variation selector
    if (codePoint < 0x10000 && !isEmojiPresentation(codePoint) && !(i < length && s.charAt(i) == '️')) {
      return -1;
    }
    while (i < length) {
      final int next = s.codePointAt(i);
      if (next == 0xFE0F || next == 0xFE0E || next == 0x20E3 || (next >= 0x1F3FB && next <= 0x1F3FF) || (next >= 0xE0020 && next <= 0xE007F)) {
        i += Character.charCount(next);
      } else if (next == 0x200D && i + 1 < length && isPictograph(s.codePointAt(i + 1))) {
        i += 1 + Character.charCount(s.codePointAt(i + 1));
      } else {
        break;
      }
    }
    return i;
  }

  /** The start of the emoji that ends the name at {@code end} (after {@code from}); -1 for none. */
  private static int lastEmojiStart (String s, int from, int end) {
    // At most a few code points back: a ZWJ sequence is the longest (a family, a flag of a region)
    int candidate = end;
    for (int steps = 0; steps < 12 && candidate > from; steps++) {
      candidate = s.offsetByCodePoints(candidate, -1);
      if (emojiEnd(s, candidate) == end) {
        int earlier = candidate;
        // keep the longest: a sequence starts before its own parts
        for (int more = 0; more < 12 && earlier > from; more++) {
          earlier = s.offsetByCodePoints(earlier, -1);
          if (emojiEnd(s, earlier) == end) {
            candidate = earlier;
          }
        }
        return candidate;
      }
    }
    return -1;
  }

  private static boolean isRegionalIndicator (int codePoint) {
    return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
  }

  private static boolean isPictograph (int codePoint) {
    return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF && !isRegionalIndicator(codePoint) && !(codePoint >= 0x1F3FB && codePoint <= 0x1F3FF)) ||
      (codePoint >= 0x2600 && codePoint <= 0x27BF) || (codePoint >= 0x2300 && codePoint <= 0x23FF) ||
      (codePoint >= 0x2B00 && codePoint <= 0x2BFF) || (codePoint >= 0x2190 && codePoint <= 0x21FF) ||
      (codePoint >= 0x2900 && codePoint <= 0x297F) || (codePoint >= 0x25A0 && codePoint <= 0x25FF) ||
      codePoint == 0x00A9 || codePoint == 0x00AE || codePoint == 0x203C || codePoint == 0x2049 || codePoint == 0x2122 ||
      codePoint == 0x2139 || codePoint == 0x24C2 || codePoint == 0x3030 || codePoint == 0x303D || codePoint == 0x3297 || codePoint == 0x3299;
  }

  /** The symbols below U+10000 shown as emoji without a variation selector (Unicode's Emoji_Presentation). */
  private static boolean isEmojiPresentation (int c) {
    return c == 0x231A || c == 0x231B || (c >= 0x23E9 && c <= 0x23EC) || c == 0x23F0 || c == 0x23F3 || c == 0x25FD || c == 0x25FE ||
      c == 0x2614 || c == 0x2615 || (c >= 0x2648 && c <= 0x2653) || c == 0x267F || c == 0x2693 || c == 0x26A1 || c == 0x26AA ||
      c == 0x26AB || c == 0x26BD || c == 0x26BE || c == 0x26C4 || c == 0x26C5 || c == 0x26CE || c == 0x26D4 || c == 0x26EA ||
      c == 0x26F2 || c == 0x26F3 || c == 0x26F5 || c == 0x26FA || c == 0x26FD || c == 0x2705 || c == 0x270A || c == 0x270B ||
      c == 0x2728 || c == 0x274C || c == 0x274E || (c >= 0x2753 && c <= 0x2755) || c == 0x2757 || (c >= 0x2795 && c <= 0x2797) ||
      c == 0x27B0 || c == 0x27BF || c == 0x2B1B || c == 0x2B1C || c == 0x2B50 || c == 0x2B55;
  }

  // Words in the name

  private static final Map<String, String> KEYWORDS = new HashMap<>();

  private static void keywords (String iconName, String... words) {
    for (String word : words) {
      KEYWORDS.put(word, iconName);
    }
  }

  static {
    keywords("Work", "work", "job", "jobs", "office", "business", "company", "team", "clients");
    keywords("Home", "home", "family", "house");
    keywords("Channels", "news", "channel", "channels", "feed", "feeds");
    keywords("Money", "crypto", "bitcoin", "btc", "money", "finance", "bank", "banking");
    keywords("Trade", "trade", "trades", "trading", "stocks", "invest", "investing", "market", "markets");
    keywords("Game", "game", "games", "gaming", "gamers");
    keywords("Note", "music", "songs", "song");
    keywords("Study", "study", "studies", "school", "university", "uni", "college", "class", "classes", "education", "courses");
    keywords("Travel", "travel", "travels", "trip", "trips");
    keywords("Groups", "friends", "friend", "group", "groups", "community", "communities");
    keywords("Private", "personal", "private", "people", "contacts");
    keywords("Bots", "bot", "bots");
    keywords("Favorite", "favorites", "favourites", "favorite", "favourite", "fav", "favs", "starred", "important");
    keywords("Palette", "art", "arts", "design", "drawing");
    keywords("Sport", "sport", "sports", "football", "soccer", "gym", "fitness");
    keywords("Book", "book", "books", "reading", "library");
    keywords("Party", "party", "parties", "events", "fun");
    keywords("Love", "love", "dating");
    keywords("Light", "ideas", "idea");
    keywords("Unread", "unread");
  }

  /** The icon of the first word of the name that names one; null when none does. */
  private static @Nullable String keywordIconName (@Nullable String name) {
    if (StringUtils.isEmpty(name)) {
      return null;
    }
    final String lower = name.toLowerCase(Locale.ROOT);
    int i = 0;
    while (i < lower.length()) {
      while (i < lower.length() && !Character.isLetter(lower.charAt(i))) {
        i++;
      }
      int wordStart = i;
      while (i < lower.length() && Character.isLetter(lower.charAt(i))) {
        i++;
      }
      if (i > wordStart) {
        String iconName = KEYWORDS.get(lower.substring(wordStart, i));
        if (iconName != null) {
          return iconName;
        }
      }
    }
    return null;
  }

  // What a folder holds

  private static final int KIND_PRIVATE = 0, KIND_GROUPS = 1, KIND_CHANNELS = 2, KIND_BOTS = 3;
  private static final String[] KIND_ICONS = {"Private", "Groups", "Channels", "Bots"};

  /** What one folder holds, as it was when its info was {@link #info}: a newer info (any change to the folders) asks again. */
  private static final class Content {
    final TdApi.ChatFolderInfo info;
    final @Nullable String iconName;
    final boolean chosenCustom;

    Content (TdApi.ChatFolderInfo info, @Nullable String iconName, boolean chosenCustom) {
      this.info = info;
      this.iconName = iconName;
      this.chosenCustom = chosenCustom;
    }

    boolean sameIcon (@Nullable Content other) {
      return other != null ? StringUtils.equalsOrBothEmpty(iconName, other.iconName) && chosenCustom == other.chosenCustom : iconName == null && !chosenCustom;
    }
  }

  private static final ConcurrentHashMap<Long, Content> contents = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Long, TdApi.ChatFolderInfo> requested = new ConcurrentHashMap<>();

  private static long key (Tdlib tdlib, int chatFolderId) {
    return ((long) tdlib.id() << 32) | (chatFolderId & 0xFFFFFFFFL);
  }

  /**
   * What the folder holds: the answer for this very info, else the last one while the new one is asked (GetChatFolder,
   * once per info), or null before the first.
   */
  private static @Nullable Content content (Tdlib tdlib, TdApi.ChatFolderInfo info) {
    final long key = key(tdlib, info.id);
    final Content content = contents.get(key);
    if (content != null && content.info == info) {
      return content;
    }
    if (requested.get(key) != info) {
      requested.put(key, info);
      tdlib.send(new TdApi.GetChatFolder(info.id), (folder, error) -> {
        Content result = new Content(info,
          folder != null ? contentIconName(tdlib, folder) : null,
          folder != null && folder.icon != null && CUSTOM.equals(folder.icon.name)
        );
        Content old = contents.put(key, result);
        requested.remove(key, info); // after the answer is kept, so it is never asked twice
        if (!result.sameIcon(old)) {
          UI.post(() -> {
            for (Listener listener : listeners) {
              listener.onFolderIconChanged(tdlib, info.id);
            }
          });
        }
      });
    }
    return content;
  }

  /**
   * The icon of the kind of chat a folder holds: its pinned and included chats, three in four of one kind, and no type
   * of chat included by its settings but that one. Null for a mix, or nothing known yet.
   */
  @AnyThread
  private static @Nullable String contentIconName (Tdlib tdlib, TdApi.ChatFolder folder) {
    final int[] counts = new int[KIND_ICONS.length];
    final int known = count(tdlib, folder.pinnedChatIds, counts) + count(tdlib, folder.includedChatIds, counts);
    int typeKinds = 0;
    if (folder.includeContacts || folder.includeNonContacts) {
      typeKinds |= 1 << KIND_PRIVATE;
    }
    if (folder.includeGroups) {
      typeKinds |= 1 << KIND_GROUPS;
    }
    if (folder.includeChannels) {
      typeKinds |= 1 << KIND_CHANNELS;
    }
    if (folder.includeBots) {
      typeKinds |= 1 << KIND_BOTS;
    }
    if (known == 0) {
      return Integer.bitCount(typeKinds) == 1 ? KIND_ICONS[Integer.numberOfTrailingZeros(typeKinds)] : null;
    }
    int kind = 0;
    for (int i = 1; i < counts.length; i++) {
      if (counts[i] > counts[kind]) {
        kind = i;
      }
    }
    if (counts[kind] * 4 < known * 3 || (typeKinds & ~(1 << kind)) != 0) {
      return null;
    }
    return KIND_ICONS[kind];
  }

  private static int count (Tdlib tdlib, @Nullable long[] chatIds, int[] counts) {
    if (chatIds == null) {
      return 0;
    }
    int known = 0;
    for (long chatId : chatIds) {
      final int kind;
      if (ChatId.isUserChat(chatId)) {
        kind = tdlib.isBotChat(chatId) ? KIND_BOTS : KIND_PRIVATE;
      } else if (tdlib.isChannel(chatId)) {
        kind = KIND_CHANNELS;
      } else if (tdlib.isMultiChat(chatId)) {
        kind = KIND_GROUPS;
      } else {
        continue; // a chat not loaded yet
      }
      counts[kind]++;
      known++;
    }
    return known;
  }
}
