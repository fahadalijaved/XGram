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

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import com.fahadali.xgram.history.XgramMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Message filters (AyuGram): messages matching a filter, or sent by someone you shadow-banned, are not shown. Your own
 * messages are never hidden. Applied to history pages and to new messages of an open chat.
 */
public final class XgramFilters {
  private XgramFilters () { }

  private static final String BAN_PREFIX = "xgram_sb_";
  private static final String FILTERS_KEY = "xgram_filters";

  private static Set<String> banned;
  private static volatile List<Pattern> patterns;
  private static volatile List<String> filters;

  private static synchronized Set<String> banned () {
    if (banned == null) {
      banned = ConcurrentHashMap.newKeySet();
      for (me.vkryl.leveldb.LevelDB.Entry entry : Settings.instance().pmc().find(BAN_PREFIX)) {
        banned.add(entry.key());
      }
    }
    return banned;
  }

  public static void forgetAccount (int accountId) {
    com.fahadali.xgram.XgramAccountData.removeKeys(banned(), BAN_PREFIX + accountId + "_");
  }

  private static long senderId (@Nullable TdApi.MessageSender sender) {
    if (sender instanceof TdApi.MessageSenderUser) {
      return ((TdApi.MessageSenderUser) sender).userId;
    }
    if (sender instanceof TdApi.MessageSenderChat) {
      return ((TdApi.MessageSenderChat) sender).chatId;
    }
    return 0;
  }

  private static String banKey (Tdlib tdlib, long senderId) {
    return BAN_PREFIX + tdlib.id() + "_" + senderId;
  }

  // Shadow ban

  public static boolean isShadowBanned (Tdlib tdlib, @Nullable TdApi.MessageSender sender) {
    long id = senderId(sender);
    return id != 0 && banned().contains(banKey(tdlib, id));
  }

  public static void setShadowBanned (Tdlib tdlib, TdApi.MessageSender sender, boolean isBanned) {
    String key = banKey(tdlib, senderId(sender));
    if (isBanned) {
      banned().add(key);
      Settings.instance().pmc().putBoolean(key, true);
    } else {
      banned().remove(key);
      Settings.instance().pmc().remove(key);
    }
  }

  public static int shadowBannedCount (Tdlib tdlib) {
    String prefix = BAN_PREFIX + tdlib.id() + "_";
    int count = 0;
    for (String key : banned()) {
      if (key.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }

  public static void clearShadowBans (Tdlib tdlib) {
    String prefix = BAN_PREFIX + tdlib.id() + "_";
    List<String> keys = new ArrayList<>();
    for (String key : banned()) {
      if (key.startsWith(prefix)) {
        keys.add(key);
      }
    }
    for (String key : keys) {
      banned().remove(key);
      Settings.instance().pmc().remove(key);
    }
  }

  // Text filters

  public static List<String> getFilters () {
    List<String> result = filters;
    if (result == null) {
      String stored = Settings.instance().pmc().getString(FILTERS_KEY, "");
      result = new ArrayList<>();
      if (stored != null && !stored.isEmpty()) {
        Collections.addAll(result, stored.split("\n"));
      }
      filters = result;
      compile();
    }
    return new ArrayList<>(result);
  }

  private static void compile () {
    List<Pattern> compiled = new ArrayList<>();
    for (String filter : filters) {
      try {
        compiled.add(Pattern.compile(filter, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      } catch (PatternSyntaxException e) {
        // Not a valid regex: match it as plain text.
        compiled.add(Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      }
    }
    patterns = compiled;
  }

  public static void setFilters (List<String> newFilters) {
    List<String> clean = new ArrayList<>();
    for (String filter : newFilters) {
      String trimmed = filter.replace('\n', ' ').trim();
      if (!trimmed.isEmpty()) {
        clean.add(trimmed);
      }
    }
    filters = clean;
    compile();
    Settings.instance().pmc().putString(FILTERS_KEY, android.text.TextUtils.join("\n", clean));
  }

  // Blocked senders (AyuGram, NagramX "hide messages from blocked users in groups")

  private static final int BLOCKED_PAGE = 100;
  // Keyed by the logged in user: account slots are reused after logging out.
  private static final Map<Long, Set<Long>> blockedSenders = new ConcurrentHashMap<>();

  /** Blocked senders of an account, loaded once in the background; empty until then. */
  private static Set<Long> blocked (Tdlib tdlib) {
    long myUserId = tdlib.myUserId();
    if (myUserId == 0) {
      return Collections.emptySet();
    }
    Set<Long> set = blockedSenders.get(myUserId);
    if (set == null) {
      set = ConcurrentHashMap.newKeySet();
      Set<Long> existing = blockedSenders.putIfAbsent(myUserId, set);
      if (existing != null) {
        return existing;
      }
      loadBlocked(tdlib, set, 0);
    }
    return set;
  }

  private static void loadBlocked (Tdlib tdlib, Set<Long> set, int offset) {
    tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), offset, BLOCKED_PAGE), (senders, error) -> {
      if (senders == null) {
        return;
      }
      for (TdApi.MessageSender sender : senders.senders) {
        set.add(senderId(sender));
      }
      if (senders.senders.length == BLOCKED_PAGE && offset + BLOCKED_PAGE < senders.totalCount) {
        loadBlocked(tdlib, set, offset + BLOCKED_PAGE);
      }
    });
  }

  /** TDLib thread, {@code updateChatBlockList}. A private chat has the id of its user. */
  public static void onChatBlockListChanged (Tdlib tdlib, long chatId, @Nullable TdApi.BlockList blockList) {
    Set<Long> set = blockedSenders.get(tdlib.myUserId());
    if (set != null) {
      if (blockList instanceof TdApi.BlockListMain) {
        set.add(chatId);
      } else {
        set.remove(chatId);
      }
    }
  }

  private static boolean isBlockedInGroup (Tdlib tdlib, TdApi.Message message) {
    return com.fahadali.xgram.XgramSettings.instance().hideBlocked() && message.senderId != null && message.chatId < 0 &&
      blocked(tdlib).contains(senderId(message.senderId)) && !tdlib.isChannel(message.chatId);
  }

  // Applying

  @AnyThread
  public static boolean isHidden (Tdlib tdlib, TdApi.Message message) {
    if (message.isOutgoing) {
      return false;
    }
    if (isShadowBanned(tdlib, message.senderId) || isBlockedInGroup(tdlib, message)) {
      return true;
    }
    if (patterns == null) {
      getFilters();
    }
    List<Pattern> current = patterns;
    if (current == null || current.isEmpty()) {
      return false;
    }
    TdApi.FormattedText text = XgramMedia.formattedTextOf(message.content);
    if (text == null || text.text.isEmpty()) {
      return false;
    }
    for (Pattern pattern : current) {
      if (pattern.matcher(text.text).find()) {
        return true;
      }
    }
    return false;
  }

  /**
   * TDLib thread: a page of history without hidden messages. The page's oldest message is kept when everything would
   * be hidden, as Telegram X takes an empty page for the start of the chat and pages on from the oldest message.
   */
  @AnyThread
  public static TdApi.Message[] filter (Tdlib tdlib, TdApi.Message[] messages) {
    if (messages.length == 0 || (banned().isEmpty() && getFilters().isEmpty() && !com.fahadali.xgram.XgramSettings.instance().hideBlocked())) {
      return messages;
    }
    List<TdApi.Message> result = new ArrayList<>(messages.length);
    for (TdApi.Message message : messages) {
      if (!isHidden(tdlib, message)) {
        result.add(message);
      }
    }
    if (result.size() == messages.length) {
      return messages;
    }
    if (result.isEmpty()) {
      result.add(messages[messages.length - 1]);
    }
    return result.toArray(new TdApi.Message[0]);
  }
}
