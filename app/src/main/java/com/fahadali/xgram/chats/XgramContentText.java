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
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.List;

/**
 * Messages Telegram X can't draw yet (checklists, stories, gifts, paid media, invoices, group calls) shown as text
 * instead of "unsupported message", so their content can still be read.
 */
public final class XgramContentText {
  private XgramContentText () { }

  private static final class Builder {
    private final StringBuilder text = new StringBuilder();
    private final List<TdApi.TextEntity> entities = new ArrayList<>();

    Builder bold (String value) {
      int start = text.length();
      text.append(value);
      entities.add(new TdApi.TextEntity(start, value.length(), new TdApi.TextEntityTypeBold()));
      return this;
    }

    Builder italic (String value) {
      int start = text.length();
      text.append(value);
      entities.add(new TdApi.TextEntity(start, value.length(), new TdApi.TextEntityTypeItalic()));
      return this;
    }

    Builder plain (String value) {
      text.append(value);
      return this;
    }

    Builder formatted (@Nullable TdApi.FormattedText value) {
      if (value == null || value.text == null || value.text.isEmpty()) {
        return this;
      }
      int start = text.length();
      text.append(value.text);
      if (value.entities != null) {
        for (TdApi.TextEntity entity : value.entities) {
          entities.add(new TdApi.TextEntity(start + entity.offset, entity.length, entity.type));
        }
      }
      return this;
    }

    Builder newLine () {
      if (text.length() > 0) {
        text.append('\n');
      }
      return this;
    }

    TdApi.FormattedText build () {
      return new TdApi.FormattedText(text.toString(), entities.toArray(new TdApi.TextEntity[0]));
    }
  }

  /** Whether {@link #describe} handles this content. */
  public static boolean isDescribed (@Nullable TdApi.MessageContent content) {
    if (content == null) {
      return false;
    }
    switch (content.getConstructor()) {
      case TdApi.MessageChecklist.CONSTRUCTOR:
      case TdApi.MessageStory.CONSTRUCTOR:
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
      case TdApi.MessageGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageInvoice.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  private static boolean isEmpty (@Nullable TdApi.FormattedText text) {
    return text == null || text.text == null || text.text.isEmpty();
  }

  /** Text for content Telegram X has no bubble for, or null to keep the generic "unsupported" bubble. */
  @Nullable
  public static TdApi.FormattedText describe (Tdlib tdlib, TdApi.MessageContent content) {
    Builder b = new Builder();
    switch (content.getConstructor()) {
      case TdApi.MessageChecklist.CONSTRUCTOR: {
        TdApi.Checklist list = ((TdApi.MessageChecklist) content).list;
        b.plain("📋 ").formatted(list.title);
        if (isEmpty(list.title)) {
          b.bold(Lang.getString(R.string.XgramChecklist));
        }
        for (TdApi.ChecklistTask task : list.tasks) {
          b.newLine().plain(task.completionDate != 0 ? "☑ " : "☐ ").formatted(task.text);
        }
        return b.build();
      }
      case TdApi.MessageStory.CONSTRUCTOR: {
        TdApi.MessageStory story = (TdApi.MessageStory) content;
        b.plain("📖 ").bold(Lang.getString(R.string.XgramStory));
        String poster = tdlib.chatTitle(story.storyPosterChatId);
        if (poster != null && !poster.isEmpty()) {
          b.plain(" · ").plain(poster);
        }
        b.newLine().italic(Lang.getString(R.string.XgramOpenInOfficialApp));
        return b.build();
      }
      case TdApi.MessagePaidMedia.CONSTRUCTOR: {
        TdApi.MessagePaidMedia paid = (TdApi.MessagePaidMedia) content;
        b.plain("⭐ ").bold(Lang.getString(R.string.PaidMedia)).plain(" · " + paid.starCount + " ⭐");
        if (!isEmpty(paid.caption)) {
          b.newLine().formatted(paid.caption);
        }
        return b.build();
      }
      case TdApi.MessageGift.CONSTRUCTOR: {
        TdApi.MessageGift gift = (TdApi.MessageGift) content;
        b.plain("🎁 ").bold(Lang.getString(R.string.Gift));
        if (gift.gift != null && gift.gift.starCount > 0) {
          b.plain(" · " + gift.gift.starCount + " ⭐");
        }
        if (!isEmpty(gift.text)) {
          b.newLine().formatted(gift.text);
        }
        return b.build();
      }
      case TdApi.MessageUpgradedGift.CONSTRUCTOR: {
        TdApi.MessageUpgradedGift gift = (TdApi.MessageUpgradedGift) content;
        b.plain("🎁 ");
        if (gift.gift != null && gift.gift.title != null && !gift.gift.title.isEmpty()) {
          b.bold(gift.gift.title + (gift.gift.number > 0 ? " #" + gift.gift.number : ""));
        } else {
          b.bold(Lang.getString(R.string.Gift));
        }
        if (!isEmpty(gift.text)) {
          b.newLine().formatted(gift.text);
        }
        return b.build();
      }
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) content;
        b.plain("🧾 ");
        if (invoice.productInfo != null) {
          b.bold(invoice.productInfo.title);
          if (!isEmpty(invoice.productInfo.description)) {
            b.newLine().formatted(invoice.productInfo.description);
          }
        } else {
          b.bold(Lang.getString(R.string.XgramInvoice));
        }
        b.newLine().italic(Lang.getString(R.string.XgramOpenInOfficialApp));
        return b.build();
      }
      case TdApi.MessageGroupCall.CONSTRUCTOR: {
        TdApi.MessageGroupCall call = (TdApi.MessageGroupCall) content;
        b.plain(call.isVideo ? "📹 " : "📞 ").bold(Lang.getString(R.string.XgramGroupCall));
        if (call.duration > 0) {
          b.plain(" · " + android.text.format.DateUtils.formatElapsedTime(call.duration));
        } else if (call.isActive) {
          b.plain(" · ").italic(Lang.getString(R.string.XgramGroupCallOngoing));
        }
        return b.build();
      }
      default:
        return null;
    }
  }
}
