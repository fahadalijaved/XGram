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
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

/**
 * XGram: Telegram gifts, like official Telegram: Send a gift (pick one of the gifts for sale with its Star price, an
 * optional message, confirm, and it is bought with your Stars) and the gifts someone has received.
 */
public final class XgramGifts {
  private XgramGifts () { }

  private static final int MAX_LISTED = 40;

  private static String emojiOf (TdApi.Gift gift) {
    return gift != null && gift.sticker != null && gift.sticker.emoji != null && !gift.sticker.emoji.isEmpty() ? gift.sticker.emoji : "🎁";
  }

  public static void send (ViewController<?> c, TdApi.MessageSender owner, String ownerName) {
    c.tdlib().send(new TdApi.GetAvailableGifts(), (gifts, error) -> c.runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      final ArrayList<TdApi.AvailableGift> forSale = new ArrayList<>();
      for (TdApi.AvailableGift available : gifts.gifts) {
        if (available.gift != null && available.gift.starCount > 0 && forSale.size() < MAX_LISTED) {
          forSale.add(available);
        }
      }
      if (forSale.isEmpty()) {
        UI.showToast(R.string.XgramGiftsNone, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      final int[] ids = new int[forSale.size() + 1];
      final String[] titles = new String[forSale.size() + 1];
      final int[] icons = new int[forSale.size() + 1];
      for (int i = 0; i < forSale.size(); i++) {
        TdApi.AvailableGift available = forSale.get(i);
        ids[i] = i + 1;
        String name = available.title != null && !available.title.isEmpty() ? available.title + " " : "";
        titles[i] = emojiOf(available.gift) + "  " + name + "· " + Strings.buildCounter(available.gift.starCount) + " ⭐";
        icons[i] = R.drawable.baseline_gift_outline_24;
      }
      ids[forSale.size()] = R.id.btn_cancel;
      titles[forSale.size()] = Lang.getString(R.string.Cancel);
      icons[forSale.size()] = R.drawable.baseline_cancel_24;
      c.showOptions(Lang.getString(R.string.XgramGiftsPick, ownerName), ids, titles, null, icons, (itemView, id) -> {
        if (id >= 1 && id <= forSale.size()) {
          askMessage(c, owner, forSale.get(id - 1).gift);
        }
        return true;
      });
    }));
  }

  private static void askMessage (ViewController<?> c, TdApi.MessageSender owner, TdApi.Gift gift) {
    c.openInputAlert(Lang.getString(R.string.XgramGiftsMessage), Lang.getString(R.string.XgramGiftsMessageHint), R.string.XgramGiftsSendButton, R.string.Cancel, null, (inputView, result) -> {
      final String text = result != null ? result.trim() : "";
      c.tdlib().send(new TdApi.SendGift(gift.id, owner, new TdApi.FormattedText(text, new TdApi.TextEntity[0]), false, false), (ok, error) -> {
        if (error != null) {
          if (error.message != null && error.message.contains("BALANCE_TOO_LOW")) {
            UI.showToast(R.string.XgramPaidNoStars, android.widget.Toast.LENGTH_LONG);
          } else {
            UI.showError(error);
          }
        } else {
          UI.showToast(R.string.XgramGiftsSent, android.widget.Toast.LENGTH_SHORT);
        }
      });
      return true;
    }, true);
  }

  public static void showReceived (ViewController<?> c, TdApi.MessageSender owner, String ownerName) {
    c.tdlib().send(new TdApi.GetReceivedGifts("", owner, 0, false, false, false, false, false, false, false, false, false, "", MAX_LISTED), (received, error) -> c.runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (received.gifts.length == 0) {
        UI.showToast(Lang.getString(R.string.XgramGiftsNoneReceived, ownerName), android.widget.Toast.LENGTH_SHORT);
        return;
      }
      final int[] ids = new int[received.gifts.length + 1];
      final String[] titles = new String[received.gifts.length + 1];
      final int[] icons = new int[received.gifts.length + 1];
      for (int i = 0; i < received.gifts.length; i++) {
        TdApi.ReceivedGift gift = received.gifts[i];
        StringBuilder b = new StringBuilder();
        if (gift.gift instanceof TdApi.SentGiftUpgraded) {
          TdApi.UpgradedGift upgraded = ((TdApi.SentGiftUpgraded) gift.gift).gift;
          b.append("✨ ").append(upgraded.title).append(upgraded.number > 0 ? " #" + upgraded.number : "");
        } else if (gift.gift instanceof TdApi.SentGiftRegular) {
          TdApi.Gift regular = ((TdApi.SentGiftRegular) gift.gift).gift;
          b.append(emojiOf(regular)).append("  ").append(Strings.buildCounter(regular.starCount)).append(" ⭐");
        }
        if (gift.senderId != null && !gift.isPrivate) {
          b.append(" · ").append(c.tdlib().senderName(gift.senderId, true));
        }
        ids[i] = i + 1;
        titles[i] = b.toString();
        icons[i] = R.drawable.baseline_gift_outline_24;
      }
      ids[received.gifts.length] = R.id.btn_cancel;
      titles[received.gifts.length] = Lang.getString(R.string.Cancel);
      icons[received.gifts.length] = R.drawable.baseline_close_24;
      c.showOptions(Lang.getString(R.string.XgramGiftsReceived, ownerName, received.totalCount), ids, titles, null, icons, (itemView, id) -> true);
    }));
  }
}
