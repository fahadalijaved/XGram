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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import java.util.concurrent.TimeUnit;

/**
 * XGram: boosting a channel or group, like official Telegram: its level and how many boosts the next level needs,
 * whether you boost it already, Boost with a free boost slot (Telegram Premium gives them), and the boost link.
 */
public final class XgramBoosts {
  private XgramBoosts () { }

  private static final int ID_BOOST = 1, ID_LINK = 2;

  public static void open (ViewController<?> c, long chatId) {
    c.tdlib().send(new TdApi.GetChatBoostStatus(chatId), (status, error) -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      c.tdlib().send(new TdApi.GetAvailableChatBoostSlots(), (slots, slotsError) -> c.runOnUiThreadOptional(() -> show(c, chatId, status, slots)));
    });
  }

  private static @Nullable TdApi.ChatBoostSlot freeSlot (@Nullable TdApi.ChatBoostSlots slots, long chatId) {
    if (slots == null || slots.slots == null) {
      return null;
    }
    final long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    TdApi.ChatBoostSlot reusable = null;
    for (TdApi.ChatBoostSlot slot : slots.slots) {
      if (slot.currentlyBoostedChatId == 0) {
        return slot;
      }
      if (slot.currentlyBoostedChatId != chatId && slot.cooldownUntilDate <= now && reusable == null) {
        reusable = slot;
      }
    }
    return reusable;
  }

  private static void show (ViewController<?> c, long chatId, TdApi.ChatBoostStatus status, @Nullable TdApi.ChatBoostSlots slots) {
    final boolean boosting = status.appliedSlotIds != null && status.appliedSlotIds.length > 0;
    final StringBuilder info = new StringBuilder(Lang.getString(R.string.XgramBoostLevel, status.level, status.boostCount));
    if (status.nextLevelBoostCount > status.boostCount) {
      info.append('\n').append(Lang.getString(R.string.XgramBoostNext, status.nextLevelBoostCount - status.boostCount, status.level + 1));
    }
    if (boosting) {
      info.append('\n').append(Lang.getString(R.string.XgramBoostYours));
    }
    final TdApi.ChatBoostSlot slot = freeSlot(slots, chatId);
    if (slot == null && !boosting) {
      info.append('\n').append(Lang.getString(R.string.XgramBoostPremium));
    }
    final java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    final java.util.ArrayList<String> titles = new java.util.ArrayList<>();
    final java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();
    if (slot != null) {
      ids.add(ID_BOOST);
      titles.add(Lang.getString(R.string.XgramBoost));
      icons.add(R.drawable.baseline_flash_on_24);
    }
    if (status.boostUrl != null && !status.boostUrl.isEmpty()) {
      ids.add(ID_LINK);
      titles.add(Lang.getString(R.string.XgramBoostCopyLink));
      icons.add(R.drawable.baseline_link_24);
    }
    ids.add(R.id.btn_cancel);
    titles.add(Lang.getString(R.string.Cancel));
    icons.add(R.drawable.baseline_cancel_24);
    final int[] idArray = new int[ids.size()];
    final int[] iconArray = new int[icons.size()];
    for (int i = 0; i < idArray.length; i++) {
      idArray[i] = ids.get(i);
      iconArray[i] = icons.get(i);
    }
    c.showOptions(info.toString(), idArray, titles.toArray(new String[0]), null, iconArray, (itemView, id) -> {
      if (id == ID_BOOST && slot != null) {
        c.tdlib().send(new TdApi.BoostChat(chatId, new int[] {slot.slotId}), (result, error) -> {
          if (error != null) {
            UI.showError(error);
          } else {
            UI.showToast(R.string.XgramBoosted, android.widget.Toast.LENGTH_SHORT);
          }
        });
      } else if (id == ID_LINK) {
        UI.copyText(status.boostUrl, R.string.CopiedLink);
      }
      return true;
    });
  }
}
