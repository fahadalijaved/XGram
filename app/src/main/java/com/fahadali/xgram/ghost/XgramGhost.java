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
      XgramSettings settings = XgramSettings.instance();
      if (!dontReadMessages(tdlib, message.chatId) || !settings.ghostReadOnSend()) {
        return;
      }
      TdApi.MessageSource source = message.topicId instanceof TdApi.MessageTopicForum ?
        new TdApi.MessageSourceForumTopicHistory() :
        new TdApi.MessageSourceChatHistory();
      tdlib.send(new TdApi.ViewMessages(message.chatId, new long[] {message.id}, source, true), tdlib.typedOkHandler());
    }
  }
}
