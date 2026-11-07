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
package org.thunderdog.challegram.data;

import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;

import tgx.td.Td;

/**
 * XGram: a Telegram Star gift (TdApi.MessageGift) or a collectible one (TdApi.MessageUpgradedGift), as a card like
 * official Telegram's: the gift's animated sticker, who sent it, its name or Star value, the attached message and a
 * button that opens the sender.
 */
public class TGMessageStarGift extends TGMessageGiveawayBase {
  private final @Nullable TdApi.MessageGift gift;
  private final @Nullable TdApi.MessageUpgradedGift upgraded;

  public TGMessageStarGift (MessagesManager manager, TdApi.Message msg, TdApi.MessageGift gift) {
    super(manager, msg);
    this.gift = gift;
    this.upgraded = null;
  }

  public TGMessageStarGift (MessagesManager manager, TdApi.Message msg, TdApi.MessageUpgradedGift upgraded) {
    super(manager, msg);
    this.gift = null;
    this.upgraded = upgraded;
  }

  private @Nullable TdApi.MessageSender senderId () {
    return gift != null ? gift.senderId : upgraded != null ? upgraded.senderId : null;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));
    content.padding(Screen.dp(16));
    final TdApi.Sticker sticker = gift != null ? (gift.gift != null ? gift.gift.sticker : null) :
      upgraded != null && upgraded.gift != null && upgraded.gift.model != null ? upgraded.gift.model.sticker : null;
    if (sticker != null) {
      content.add(new ContentSticker(tdlib, sticker, 120f));
    } else {
      content.add(new ContentDrawable(R.drawable.baseline_gift_72));
    }
    content.padding(Screen.dp(12));

    final TdApi.MessageSender senderId = senderId();
    final String title;
    if (msg.isOutgoing) {
      title = Lang.getString(R.string.XgramGiftSent);
    } else if (senderId != null) {
      title = Lang.getString(R.string.XgramGiftFrom, tdlib.senderName(senderId, true));
    } else {
      title = Lang.getString(R.string.Gift);
    }
    content.add(Lang.boldify(title), getTextColorSet(), currentViews);

    content.padding(Screen.dp(6));
    final String info;
    if (upgraded != null && upgraded.gift != null) {
      info = upgraded.gift.title + (upgraded.gift.number > 0 ? " #" + upgraded.gift.number : "");
    } else if (gift != null && gift.gift != null && gift.gift.starCount > 0) {
      info = Lang.getString(R.string.XgramGiftWorth, Strings.buildCounter(gift.gift.starCount));
    } else {
      info = null;
    }
    if (info != null) {
      content.add(info, getTextColorSet(), currentViews);
    }

    final TdApi.FormattedText text = gift != null ? gift.text : upgraded != null ? upgraded.text : null;
    if (text != null && text.text != null && !text.text.isEmpty()) {
      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(TD.toCharSequence(text), getTextColorSet(), currentViews);
    }
    content.padding(Screen.dp(8));
    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  @Override
  protected String getButtonText () {
    return Lang.getString(senderId() != null && !msg.isOutgoing ? R.string.XgramGiftOpenSender : R.string.XgramGiftView);
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    final TdApi.MessageSender senderId = msg.isOutgoing ? (gift != null ? gift.receiverId : upgraded != null ? upgraded.receiverId : null) : senderId();
    if (senderId != null) {
      tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
    }
  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
