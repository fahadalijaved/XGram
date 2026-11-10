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
package com.fahadali.xgram.business;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * XGram: "/" in a private chat offers your quick replies (Telegram Business, with Premium), as bots' commands are
 * offered in a chat with a bot: "/name" and its first message. A tap sends all of the shortcut's messages.
 */
public final class XgramQuickReplyCommands implements InlineSearchContext.CommandListProvider {
  private static final String TAG = "XgramQuickReplies";

  /** One quick reply in the "/" list. */
  public static final class Result extends InlineResultCommand {
    private final int shortcutId;

    Result (BaseActivity context, Tdlib tdlib, TdApi.QuickReplyShortcut shortcut) {
      super(context, tdlib, tdlib.myUserId(), new TdApi.BotCommand(shortcut.name, describe(shortcut), false));
      this.shortcutId = shortcut.id;
    }

    public int getShortcutId () {
      return shortcutId;
    }

    @Override
    public boolean matchesPrefix (String prefix) {
      return data.command.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @Override
    public @Nullable String getUsername () {
      return null; // a long press puts "/name " in the box, never "/name@you"
    }
  }

  private final BaseActivity context;
  private final Tdlib tdlib;
  private XgramQuickReplies.Listener waiting;

  private XgramQuickReplyCommands (BaseActivity context, Tdlib tdlib) {
    this.context = context;
    this.tdlib = tdlib;
  }

  /** The provider for {@code chat}, or null where quick replies can't be sent (or the account has no Premium). */
  public static @Nullable XgramQuickReplyCommands forChat (BaseActivity context, Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (!tdlib.hasPremium() || !XgramQuickReplies.canSendTo(tdlib, chat)) {
      return null;
    }
    tdlib.xgramQuickReplies().load();
    return new XgramQuickReplyCommands(context, tdlib);
  }

  @Override
  public ArrayList<InlineResult<?>> searchCommands (String prefix, String currentQuery, InlineSearchContext.QueryResultsChangeListener changeListener) {
    if (!tdlib.hasPremium()) {
      return null;
    }
    final XgramQuickReplies store = tdlib.xgramQuickReplies();
    if (!store.isListKnown()) {
      // Not there yet: the list is asked for, and the search runs again once it comes
      store.load();
      if (waiting != null) {
        store.removeListener(waiting);
      }
      waiting = () -> {
        if (store.isListKnown()) {
          store.removeListener(waiting);
          waiting = null;
          changeListener.onQueryResultsChanged(currentQuery);
        }
      };
      store.addListener(waiting);
      return null;
    }
    final List<TdApi.QuickReplyShortcut> found = store.search(prefix);
    if (found.isEmpty()) {
      return null;
    }
    final ArrayList<InlineResult<?>> results = new ArrayList<>(found.size());
    for (TdApi.QuickReplyShortcut shortcut : found) {
      results.add(new Result(context, tdlib, shortcut));
    }
    return results;
  }

  /** Sends a quick reply's messages to {@code chatId}; {@code after} runs once Telegram took them. */
  public static void send (Tdlib tdlib, long chatId, int shortcutId, @Nullable Runnable after) {
    tdlib.send(new TdApi.SendQuickReplyShortcutMessages(chatId, shortcutId, 0), (messages, error) -> {
      if (error != null) {
        Log.w(TAG, "SendQuickReplyShortcutMessages: " + error.code + " " + error.message);
        UI.post(() -> UI.showToast(Lang.getString(R.string.XgramQuickReplySendFailed), Toast.LENGTH_LONG));
      } else if (after != null) {
        UI.post(after);
      }
    });
  }

  /** A line about a quick reply: its first message (text, or its kind and caption) and how many there are. */
  public static String describe (TdApi.QuickReplyShortcut shortcut) {
    final String first = shortcut.firstMessage != null ? preview(shortcut.firstMessage.content) : "";
    if (shortcut.messageCount > 1) {
      return Lang.getString(R.string.XgramQuickReplyMore, first, shortcut.messageCount - 1);
    }
    return first;
  }

  /** One line for a quick reply message: its text, or its kind with its caption. */
  public static String preview (@Nullable TdApi.MessageContent content) {
    if (content == null) {
      return "";
    }
    final String kind;
    final TdApi.FormattedText caption;
    switch (content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
        return oneLine(((TdApi.MessageText) content).text.text);
      case TdApi.MessagePhoto.CONSTRUCTOR:
        kind = Lang.getString(R.string.ChatContentPhoto);
        caption = ((TdApi.MessagePhoto) content).caption;
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        kind = Lang.getString(R.string.ChatContentVideo);
        caption = ((TdApi.MessageVideo) content).caption;
        break;
      case TdApi.MessageDocument.CONSTRUCTOR:
        kind = Lang.getString(R.string.ChatContentFile);
        caption = ((TdApi.MessageDocument) content).caption;
        break;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        kind = Lang.getString(R.string.ChatContentAnimation);
        caption = ((TdApi.MessageAnimation) content).caption;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        kind = Lang.getString(R.string.ChatContentVoice);
        caption = ((TdApi.MessageVoiceNote) content).caption;
        break;
      default:
        return Lang.getString(R.string.XgramQuickReplyOther);
    }
    final String text = caption != null ? oneLine(caption.text) : "";
    return text.isEmpty() ? kind : kind + ", " + text;
  }

  private static String oneLine (@Nullable String text) {
    return text == null ? "" : text.replace('\n', ' ').trim();
  }
}
