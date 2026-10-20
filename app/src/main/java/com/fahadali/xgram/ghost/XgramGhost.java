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
package com.fahadali.xgram.ghost;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;

/**
 * Ghost mode (as in AyuGram and NagramX), built on TDLib: messages are viewed without being marked as read,
 * no typing status is sent, and the account stays offline. Hooks in Telegram X code call these checks.
 */
public final class XgramGhost {
  private XgramGhost () { }

  /** TileService.ACTION_QS_TILE_PREFERENCES (API 24): a long press on the Quick Settings tile opens the app with it. */
  public static final String ACTION_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES";

  public static boolean dontReadMessages () {
    return XgramSettings.instance().ghostDontRead();
  }

  public static boolean dontSendTyping () {
    return XgramSettings.instance().ghostDontType();
  }

  public static boolean hideOnline () {
    return XgramSettings.instance().ghostOffline();
  }

  // Per chat

  private static final String EXCLUDED_PREFIX = "xgram_ghost_ex_";
  private static java.util.Set<String> excluded;

  private static synchronized java.util.Set<String> excluded () {
    if (excluded == null) {
      excluded = java.util.concurrent.ConcurrentHashMap.newKeySet();
      for (me.vkryl.leveldb.LevelDB.Entry entry : org.thunderdog.challegram.unsorted.Settings.instance().pmc().find(EXCLUDED_PREFIX)) {
        excluded.add(entry.key());
      }
    }
    return excluded;
  }

  public static void forgetAccount (int accountId) {
    com.fahadali.xgram.XgramAccountData.removeKeys(excluded(), EXCLUDED_PREFIX + accountId + "_");
  }

  private static String excludedKey (Tdlib tdlib, long chatId) {
    return EXCLUDED_PREFIX + tdlib.id() + "_" + chatId;
  }

  /** Chats excluded from ghost mode read and typing (NagramX "Exclude Read" / "Exclude Typing"). */
  public static boolean isExcluded (Tdlib tdlib, long chatId) {
    return excluded().contains(excludedKey(tdlib, chatId));
  }

  public static void setExcluded (Tdlib tdlib, long chatId, boolean isExcluded) {
    String key = excludedKey(tdlib, chatId);
    if (isExcluded) {
      excluded().add(key);
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putBoolean(key, true);
    } else {
      excluded().remove(key);
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().remove(key);
    }
  }

  /** Ghost read applies to this chat. Channels have no read receipts, so they are always read normally. */
  public static boolean dontReadMessages (Tdlib tdlib, long chatId) {
    return dontReadMessages() && chatId != 0 && !tdlib.isChannel(chatId) && !isExcluded(tdlib, chatId);
  }

  public static boolean dontSendTyping (Tdlib tdlib, long chatId) {
    return dontSendTyping() && !isExcluded(tdlib, chatId);
  }

  /** Whether the chat menu should offer to exclude or include the chat. */
  public static boolean canExclude (Tdlib tdlib, long chatId) {
    return (dontReadMessages() || dontSendTyping()) && chatId != 0 && !tdlib.isChannel(chatId) && !tdlib.isSelfChat(chatId);
  }

  /** "Read up to here": marks the chat (or forum topic) read on the server up to the message. */
  public static void readUpTo (Tdlib tdlib, TdApi.Message message, long messageId) {
    TdApi.MessageSource source = message.topicId instanceof TdApi.MessageTopicForum ?
      new TdApi.MessageSourceForumTopicHistory() :
      new TdApi.MessageSourceChatHistory();
    tdlib.send(new TdApi.ViewMessages(message.chatId, new long[] {messageId}, source, true), tdlib.typedOkHandler());
  }

  // Undo send: a message that waited while its chat was out of sight reads only up to what was on screen, as an
  // immediate send would have. One-shot caps, by the message being sent (read on send) or by the request (send as
  // scheduled, which reads while the request is made).

  private static final Object CAP_LOCK = new Object();
  private static final java.util.HashMap<String, Long> messageReadCaps = new java.util.HashMap<>();
  private static final java.util.IdentityHashMap<TdApi.Function<?>, Long> queryReadCaps = new java.util.IdentityHashMap<>();

  /** True when sending a message to the chat reads it (ghost "don't read" with "read on send"). */
  public static boolean readsOnSend (Tdlib tdlib, long chatId) {
    return XgramSettings.instance().ghostReadOnSend() && dontReadMessages(tdlib, chatId);
  }

  /** The newest message where a message to {@code topicId} goes (the chat, or its forum topic); 0 when unknown. */
  public static long lastMessageIdOf (Tdlib tdlib, long chatId, @androidx.annotation.Nullable TdApi.MessageTopic topicId) {
    TdApi.Message last = XgramGhostSend.lastMessageOf(tdlib, chatId, topicId);
    return last != null ? last.id : 0;
  }

  /** The send of the pending message {@code messageId} reads only up to {@code capId}. */
  public static void capReadOnSend (Tdlib tdlib, long chatId, long messageId, long capId) {
    if (capId == 0 || messageId == 0) {
      return;
    }
    synchronized (CAP_LOCK) {
      messageReadCaps.put(readCapKey(tdlib, chatId, messageId), capId);
    }
  }

  /** While {@code query} is sent, the read of "send as scheduled" goes only up to {@code capId}; 0 removes the cap. */
  public static void capReadOnSend (TdApi.Function<?> query, long capId) {
    synchronized (CAP_LOCK) {
      if (capId != 0) {
        queryReadCaps.put(query, capId);
      } else {
        queryReadCaps.remove(query);
      }
    }
  }

  static long takeQueryReadCap (TdApi.Function<?> query) {
    synchronized (CAP_LOCK) {
      if (queryReadCaps.isEmpty()) {
        return 0;
      }
      Long cap = queryReadCaps.remove(query);
      return cap != null ? cap : 0;
    }
  }

  private static long takeMessageReadCap (Tdlib tdlib, long chatId, long messageId) {
    synchronized (CAP_LOCK) {
      if (messageReadCaps.isEmpty()) {
        return 0;
      }
      Long cap = messageReadCaps.remove(readCapKey(tdlib, chatId, messageId));
      return cap != null ? cap : 0;
    }
  }

  private static String readCapKey (Tdlib tdlib, long chatId, long messageId) {
    return tdlib.id() + "_" + chatId + "_" + messageId;
  }

  /** The id to read up to: {@code messageId}, or the cap when that is older. */
  static long capped (long messageId, long capId) {
    return capId != 0 && capId < messageId ? capId : messageId;
  }

  /** Called once for every account when its {@link Tdlib} is created. */
  public static void attach (Tdlib tdlib) {
    tdlib.listeners().subscribeForGlobalUpdates(new ReadOnSend(tdlib));
  }

  /** Re-sends the online status of every active account, e.g. after "stay offline" was toggled. */
  public static void applyOnlineState () {
    for (TdlibAccount account : TdlibManager.instance()) {
      Tdlib tdlib = account.activeTdlib();
      if (tdlib != null) {
        tdlib.xgramApplyOnlineState();
      }
    }
  }

  /** Marks the chat (or forum topic) as read up to a message you sent, so not reading isn't noticeable. */
  private static final class ReadOnSend implements MessageListener {
    private final Tdlib tdlib;

    ReadOnSend (Tdlib tdlib) {
      this.tdlib = tdlib;
    }

    @Override
    public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
      // Undo send: a message that waited while its chat was out of sight reads only up to what was on screen
      final long capId = takeMessageReadCap(tdlib, message.chatId, oldMessageId);
      if (com.fahadali.xgram.checkin.XgramCheckIn.takeOwnSend(tdlib, message.chatId, oldMessageId)) {
        return; // XGram Check In: its messages never read the chat
      }
      XgramSettings settings = XgramSettings.instance();
      if (!dontReadMessages(tdlib, message.chatId) || !settings.ghostReadOnSend()) {
        return;
      }
      TdApi.MessageSource source = message.topicId instanceof TdApi.MessageTopicForum ?
        new TdApi.MessageSourceForumTopicHistory() :
        new TdApi.MessageSourceChatHistory();
      tdlib.send(new TdApi.ViewMessages(message.chatId, new long[] {capped(message.id, capId)}, source, true), tdlib.typedOkHandler());
    }

    @Override
    public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
      takeMessageReadCap(tdlib, message.chatId, oldMessageId); // nothing to read: the cap goes
      com.fahadali.xgram.checkin.XgramCheckIn.takeOwnSend(tdlib, message.chatId, oldMessageId);
    }
  }
}
