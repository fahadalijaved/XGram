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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

/**
 * XGram: Star reactions (TdApi.ReactionTypePaid), like official Telegram: a tap on ⭐ asks how many Stars to send
 * (1, 5, 10 or 50), then adds them as a pending paid reaction and commits it at once. Not enough Stars says where to
 * buy them.
 */
public final class XgramPaidReactions {
  private XgramPaidReactions () { }

  private static final int[] AMOUNTS = {1, 5, 10, 50};

  public static void ask (TGMessage parent, TdApi.Message message) {
    final ViewController<?> c = parent.controller();
    if (c == null) {
      return;
    }
    final int[] ids = new int[AMOUNTS.length + 1];
    final String[] titles = new String[AMOUNTS.length + 1];
    final int[] icons = new int[AMOUNTS.length + 1];
    for (int i = 0; i < AMOUNTS.length; i++) {
      ids[i] = i + 1;
      titles[i] = Lang.getString(R.string.XgramPaidReactionSend, AMOUNTS[i]);
      icons[i] = R.drawable.baseline_star_24;
    }
    ids[AMOUNTS.length] = R.id.btn_cancel;
    titles[AMOUNTS.length] = Lang.getString(R.string.Cancel);
    icons[AMOUNTS.length] = R.drawable.baseline_cancel_24;
    c.showOptions(Lang.getString(R.string.XgramPaidReactionInfo), ids, titles, null, icons, (itemView, id) -> {
      if (id >= 1 && id <= AMOUNTS.length) {
        send(parent.tdlib(), message.chatId, message.id, AMOUNTS[id - 1]);
      }
      return true;
    });
  }

  private static void send (Tdlib tdlib, long chatId, long messageId, int stars) {
    tdlib.send(new TdApi.AddPendingPaidMessageReaction(chatId, messageId, stars, new TdApi.PaidReactionTypeRegular()), (ok, error) -> {
      if (error != null) {
        if (error.message != null && error.message.contains("BALANCE_TOO_LOW")) {
          UI.showToast(R.string.XgramPaidNoStars, android.widget.Toast.LENGTH_LONG);
        } else {
          UI.showError(error);
        }
        return;
      }
      tdlib.send(new TdApi.CommitPendingPaidMessageReactions(chatId, messageId), (done, commitError) -> {
        if (commitError != null) {
          if (commitError.message != null && commitError.message.contains("BALANCE_TOO_LOW")) {
            UI.showToast(R.string.XgramPaidNoStars, android.widget.Toast.LENGTH_LONG);
          } else {
            UI.showError(commitError);
          }
        }
      });
    });
  }
}
