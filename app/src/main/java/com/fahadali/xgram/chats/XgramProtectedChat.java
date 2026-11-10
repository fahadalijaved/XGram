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

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import tgx.td.ChatId;

/**
 * XGram: sharing in a private chat, like official Telegram. With Telegram Premium a user can disable sharing in a
 * chat with another person: neither side can forward, save or copy its messages, or take screenshots. Enabling it
 * again is open to both: from the one who disabled it, it applies at once; from the other person, Telegram sends
 * them a request, which they answer by tapping it in the chat (Enable sharing or keep it disabled).
 */
public final class XgramProtectedChat {
  private XgramProtectedChat () { }

  private static final String TAG = "XgramProtectedChat";

  /** Requests answered while the app runs, as "chatId_messageId": a second tap doesn't ask again. */
  private static final Set<String> answered = Collections.synchronizedSet(new HashSet<>());

  /** The profile menu's item for {@code chat}, or 0: Disable sharing (Premium) or Enable sharing (anyone). */
  public static int menuItem (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null || !(chat.type instanceof TdApi.ChatTypePrivate) || tdlib.isSelfChat(chat) || tdlib.isBotChat(chat.id)) {
      return 0; // not in Saved Messages, with bots, in secret chats (they protect themselves) or in groups
    }
    final TdApi.User user = tdlib.chatUser(chat);
    if (user == null || user.isSupport || user.type instanceof TdApi.UserTypeDeleted) {
      return 0;
    }
    if (chat.hasProtectedContent) {
      return R.id.btn_xgramEnableSharing;
    }
    return tdlib.hasPremium() ? R.id.btn_xgramDisableSharing : 0;
  }

  public static int menuTitle (int itemId) {
    return itemId == R.id.btn_xgramEnableSharing ? R.string.XgramEnableSharing : R.string.XgramDisableSharing;
  }

  /** The profile menu's item was tapped: confirm, then disable or enable sharing in the chat. */
  public static void toggle (ViewController<?> c, long chatId, int itemId) {
    final Tdlib tdlib = c.tdlib();
    final boolean disable = itemId == R.id.btn_xgramDisableSharing;
    final String name = tdlib.chatTitle(chatId);
    final int actionId = disable ? R.id.btn_xgramDisableSharing : R.id.btn_xgramEnableSharing;
    c.xgramShowConfirm(
      Lang.getString(disable ? R.string.XgramDisableSharingInfo : R.string.XgramEnableSharingInfo, name),
      Lang.getString(disable ? R.string.XgramDisableSharingTitle : R.string.XgramEnableSharingTitle),
      Lang.getString(disable ? R.string.XgramDisableSharingInfo : R.string.XgramEnableSharingInfo, name),
      new int[] {actionId, R.id.btn_cancel},
      new String[] {Lang.getString(disable ? R.string.XgramDisableSharing : R.string.XgramEnableSharing), Lang.getString(R.string.Cancel)},
      new int[] {disable ? ViewController.OptionColor.RED : ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {disable ? R.drawable.baseline_lock_24 : R.drawable.baseline_share_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == actionId) {
          tdlib.send(new TdApi.ToggleChatHasProtectedContent(chatId, disable), (ok, error) -> {
            if (error != null) {
              Log.w(TAG, "ToggleChatHasProtectedContent: " + error.code + " " + error.message);
              UI.post(() -> UI.showToast(Lang.getString(
                "PREMIUM_ACCOUNT_REQUIRED".equals(error.message) ? R.string.XgramSharingNeedsPremium : R.string.XgramSharingFailed
              ), Toast.LENGTH_LONG));
            }
          });
        }
        return true;
      }
    );
  }

  /** An incoming request to enable sharing that can still be answered (not expired, not answered from here). */
  public static boolean canAnswer (TdApi.Message message) {
    return message != null && !message.isOutgoing && message.id > 0 && message.sendingState == null &&
      message.content instanceof TdApi.MessageChatHasProtectedContentDisableRequested &&
      !((TdApi.MessageChatHasProtectedContentDisableRequested) message.content).isExpired &&
      ChatId.isUserChat(message.chatId) && !answered.contains(message.chatId + "_" + message.id);
  }

  /** The request was tapped: Enable sharing, or keep it disabled. {@code onAnswered} runs once an option is picked. */
  public static void answer (ViewController<?> c, TdApi.Message message, Runnable onAnswered) {
    if (!canAnswer(message)) {
      return;
    }
    final Tdlib tdlib = c.tdlib();
    final String key = message.chatId + "_" + message.id;
    c.xgramShowConfirm(
      Lang.getString(R.string.XgramSharingRequestInfo, tdlib.chatTitle(message.chatId)),
      Lang.getString(R.string.XgramSharingRequestTitle),
      Lang.getString(R.string.XgramSharingRequestInfo, tdlib.chatTitle(message.chatId)),
      new int[] {R.id.btn_xgramSharingAllow, R.id.btn_xgramSharingKeep},
      new String[] {Lang.getString(R.string.XgramEnableSharing), Lang.getString(R.string.XgramSharingKeepDisabled)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_share_24, R.drawable.baseline_lock_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramSharingAllow || id == R.id.btn_xgramSharingKeep) {
          final boolean approve = id == R.id.btn_xgramSharingAllow;
          answered.add(key);
          onAnswered.run();
          tdlib.send(new TdApi.ProcessChatHasProtectedContentDisableRequest(message.chatId, message.id, approve), (ok, error) -> {
            if (error != null) {
              Log.w(TAG, "ProcessChatHasProtectedContentDisableRequest: " + error.code + " " + error.message);
              UI.post(() -> UI.showToast(Lang.getString(R.string.XgramSharingRequestGone), Toast.LENGTH_LONG));
            }
          });
        }
        return true;
      }
    );
  }
}
