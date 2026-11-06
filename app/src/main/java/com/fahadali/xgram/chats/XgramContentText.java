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
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
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
      case TdApi.MessageRichMessage.CONSTRUCTOR:
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
      case TdApi.MessageRichMessage.CONSTRUCTOR: {
        // A post made of Instant View blocks: headings, paragraphs, lists, quotes, code and media, as one readable text
        TdApi.RichMessage message = ((TdApi.MessageRichMessage) content).message;
        if (message != null && message.blocks != null) {
          appendBlocks(b, message.blocks, "", false);
        }
        return b.text.length() > 0 ? b.build() : null;
      }
      default:
        return null;
    }
  }

  /** A blank line between blocks (a single line break inside lists). */
  private static void blockBreak (Builder b, boolean tight) {
    if (b.text.length() > 0) {
      b.text.append(tight ? "\n" : "\n\n");
    }
  }

  private static String richText (@Nullable TdApi.RichText text) {
    return text != null ? TD.getText(text).trim() : "";
  }

  private static void textLine (Builder b, String value, boolean italic) {
    if (value.isEmpty()) {
      return;
    }
    if (italic) {
      b.italic(value);
    } else {
      b.plain(value);
    }
  }

  private static void mediaLine (Builder b, String icon, @StringRes int name, @Nullable TdApi.PageBlockCaption caption, boolean tight) {
    blockBreak(b, tight);
    final String text = caption != null ? richText(caption.text) : "";
    b.plain(icon + " ");
    if (text.isEmpty()) {
      b.italic(Lang.getString(name));
    } else {
      b.plain(text);
    }
  }

  private static void appendBlocks (Builder b, TdApi.PageBlock[] blocks, String indent, boolean italic) {
    final boolean tight = !indent.isEmpty();
    for (TdApi.PageBlock block : blocks) {
      if (block == null) {
        continue;
      }
      switch (block.getConstructor()) {
        case TdApi.PageBlockTitle.CONSTRUCTOR: {
          String value = richText(((TdApi.PageBlockTitle) block).title);
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.bold(value);
          }
          break;
        }
        case TdApi.PageBlockHeader.CONSTRUCTOR:
        case TdApi.PageBlockSubheader.CONSTRUCTOR: {
          String value = richText(block.getConstructor() == TdApi.PageBlockHeader.CONSTRUCTOR ? ((TdApi.PageBlockHeader) block).header : ((TdApi.PageBlockSubheader) block).subheader);
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.bold(value);
          }
          break;
        }
        case TdApi.PageBlockSubtitle.CONSTRUCTOR:
        case TdApi.PageBlockKicker.CONSTRUCTOR:
        case TdApi.PageBlockFooter.CONSTRUCTOR: {
          String value = richText(block.getConstructor() == TdApi.PageBlockSubtitle.CONSTRUCTOR ? ((TdApi.PageBlockSubtitle) block).subtitle :
            block.getConstructor() == TdApi.PageBlockKicker.CONSTRUCTOR ? ((TdApi.PageBlockKicker) block).kicker : ((TdApi.PageBlockFooter) block).footer);
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.italic(value);
          }
          break;
        }
        case TdApi.PageBlockParagraph.CONSTRUCTOR: {
          String value = richText(((TdApi.PageBlockParagraph) block).text);
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.plain(indent);
            textLine(b, value, italic);
          }
          break;
        }
        case TdApi.PageBlockPreformatted.CONSTRUCTOR: {
          TdApi.PageBlockPreformatted pre = (TdApi.PageBlockPreformatted) block;
          String value = pre.text != null ? TD.getText(pre.text) : "";
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.formatted(new TdApi.FormattedText(value, new TdApi.TextEntity[] {
              new TdApi.TextEntity(0, value.length(), new TdApi.TextEntityTypePreCode(pre.language != null ? pre.language : ""))
            }));
          }
          break;
        }
        case TdApi.PageBlockList.CONSTRUCTOR: {
          int number = 0;
          for (TdApi.PageBlockListItem item : ((TdApi.PageBlockList) block).items) {
            number++;
            blockBreak(b, true);
            String label = item.hasCheckbox ? (item.isChecked ? "☑" : "☐") : (item.label != null && !item.label.isEmpty() ? item.label : "•");
            b.plain(indent + label + " ");
            TdApi.PageBlock[] children = item.blocks != null ? item.blocks : new TdApi.PageBlock[0];
            int first = 0;
            if (children.length > 0 && children[0].getConstructor() == TdApi.PageBlockParagraph.CONSTRUCTOR) {
              textLine(b, richText(((TdApi.PageBlockParagraph) children[0]).text), italic);
              first = 1;
            }
            if (children.length > first) {
              TdApi.PageBlock[] rest = new TdApi.PageBlock[children.length - first];
              System.arraycopy(children, first, rest, 0, rest.length);
              appendBlocks(b, rest, indent + "    ", italic);
            }
          }
          break;
        }
        case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
          TdApi.PageBlockBlockQuote quote = (TdApi.PageBlockBlockQuote) block;
          if (quote.blocks != null) {
            blockBreak(b, tight);
            appendBlocks(b, quote.blocks, indent, true);
          }
          String credit = richText(quote.credit);
          if (!credit.isEmpty()) {
            blockBreak(b, true);
            b.plain(indent + "— " + credit);
          }
          break;
        }
        case TdApi.PageBlockPullQuote.CONSTRUCTOR: {
          TdApi.PageBlockPullQuote quote = (TdApi.PageBlockPullQuote) block;
          String value = richText(quote.text);
          if (!value.isEmpty()) {
            blockBreak(b, tight);
            b.italic(value);
          }
          String credit = richText(quote.credit);
          if (!credit.isEmpty()) {
            blockBreak(b, true);
            b.plain("— " + credit);
          }
          break;
        }
        case TdApi.PageBlockPhoto.CONSTRUCTOR:
          mediaLine(b, "🖼", R.string.Photo, ((TdApi.PageBlockPhoto) block).caption, tight);
          break;
        case TdApi.PageBlockVideo.CONSTRUCTOR:
          mediaLine(b, "🎬", R.string.Video, ((TdApi.PageBlockVideo) block).caption, tight);
          break;
        case TdApi.PageBlockAnimation.CONSTRUCTOR:
          mediaLine(b, "🎞", R.string.Gif, ((TdApi.PageBlockAnimation) block).caption, tight);
          break;
        case TdApi.PageBlockCollage.CONSTRUCTOR:
        case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
          boolean collage = block.getConstructor() == TdApi.PageBlockCollage.CONSTRUCTOR;
          TdApi.PageBlock[] children = collage ? ((TdApi.PageBlockCollage) block).blocks : ((TdApi.PageBlockSlideshow) block).blocks;
          TdApi.PageBlockCaption caption = collage ? ((TdApi.PageBlockCollage) block).caption : ((TdApi.PageBlockSlideshow) block).caption;
          blockBreak(b, tight);
          b.plain("🖼 ×" + (children != null ? children.length : 0));
          String text = caption != null ? richText(caption.text) : "";
          if (!text.isEmpty()) {
            b.plain(" " + text);
          }
          break;
        }
        case TdApi.PageBlockDetails.CONSTRUCTOR: {
          TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block;
          String header = richText(details.header);
          if (!header.isEmpty()) {
            blockBreak(b, tight);
            b.bold(header);
          }
          if (details.blocks != null) {
            appendBlocks(b, details.blocks, indent, italic);
          }
          break;
        }
        case TdApi.PageBlockCover.CONSTRUCTOR: {
          TdApi.PageBlock cover = ((TdApi.PageBlockCover) block).cover;
          if (cover != null) {
            appendBlocks(b, new TdApi.PageBlock[] {cover}, indent, italic);
          }
          break;
        }
        default:
          break; // dividers, anchors, embeds, maps and tables: left out of the text
      }
    }
  }
}
