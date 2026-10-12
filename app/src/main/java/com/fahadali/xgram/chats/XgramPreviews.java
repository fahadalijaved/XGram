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
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.StringUtils;

/**
 * Chat and topic list previews for service messages Telegram X draws in chats but shows as "Unsupported message" in
 * lists: topic changes, owner changes, sharing, wallpapers, suggestions, checklists, gifts… The sender comes first in
 * the list ("You: Created the topic Updates"), so these don't name who did it.
 */
public final class XgramPreviews {
  private XgramPreviews () { }

  @Nullable
  public static ContentPreview preview (Tdlib tdlib, TdApi.Message message) {
    TdApi.MessageContent content = message.content;
    switch (content.getConstructor()) {
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR: {
        String name = ((TdApi.MessageForumTopicCreated) content).name;
        return text(ContentPreview.EMOJI_GROUP, Lang.getString(R.string.XgramPreviewTopicCreated, name));
      }
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR: {
        TdApi.MessageForumTopicEdited edited = (TdApi.MessageForumTopicEdited) content;
        if (!StringUtils.isEmpty(edited.name)) {
          return text(ContentPreview.EMOJI_GROUP, Lang.getString(R.string.XgramPreviewTopicRenamed, edited.name));
        }
        return text(ContentPreview.EMOJI_GROUP, Lang.getString(R.string.XgramPreviewTopicIcon));
      }
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_LOCK, Lang.getString(((TdApi.MessageForumTopicIsClosedToggled) content).isClosed ? R.string.XgramPreviewTopicClosed : R.string.XgramPreviewTopicReopened));
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_GROUP, Lang.getString(((TdApi.MessageForumTopicIsHiddenToggled) content).isHidden ? R.string.XgramPreviewGeneralHidden : R.string.XgramPreviewGeneralShown));
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR: {
        long userId = ((TdApi.MessageChatOwnerChanged) content).newOwnerUserId;
        return text(ContentPreview.EMOJI_GROUP, Lang.getString(R.string.XgramPreviewNewOwner, tdlib.cache().userName(userId)));
      }
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR: {
        long userId = ((TdApi.MessageChatOwnerLeft) content).newOwnerUserId;
        return text(ContentPreview.EMOJI_GROUP, userId != 0 ? Lang.getString(R.string.XgramPreviewNewOwner, tdlib.cache().userName(userId)) : Lang.getString(R.string.XgramPreviewOwnerLeft));
      }
      case TdApi.MessageChatHasProtectedContentToggled.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_LOCK, Lang.getString(((TdApi.MessageChatHasProtectedContentToggled) content).newHasProtectedContent ? R.string.XgramPreviewSharingOff : R.string.XgramPreviewSharingOn));
      case TdApi.MessageChatHasProtectedContentDisableRequested.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_LOCK, Lang.getString(R.string.XgramPreviewSharingRequested));
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_THEME, Lang.getString(R.string.XgramPreviewWallpaper));
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_INFO, Lang.getString(R.string.XgramPreviewSuggestPhoto));
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_INFO, Lang.getString(R.string.XgramPreviewSuggestBirthday));
      case TdApi.MessageUsersShared.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_CONTACT, Lang.getString(R.string.XgramPreviewUsersShared));
      case TdApi.MessageChatShared.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_GROUP, Lang.getString(R.string.XgramPreviewChatShared));
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_POLL_OPTION, Lang.getString(R.string.XgramPreviewPollOptionAdded));
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_POLL_OPTION, Lang.getString(R.string.XgramPreviewPollOptionDeleted));
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_POLL, Lang.getString(R.string.XgramPreviewTasksDone));
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_POLL, Lang.getString(R.string.XgramPreviewTasksAdded));
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_STARS, Lang.getString(R.string.XgramPreviewMessagePrice));
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR:
        return text(ContentPreview.EMOJI_STARS, Lang.getString(R.string.XgramPreviewStarsRefunded));
      default: {
        // Checklists, stories, gifts, paid media, invoices, group calls: the text XGram shows for them in chats.
        if (XgramContentText.isDescribed(content)) {
          TdApi.FormattedText text = XgramContentText.describe(tdlib, content);
          if (text != null && !StringUtils.isEmpty(text.text)) {
            String line = text.text.split("\n", 2)[0];
            return text(content.getConstructor() == TdApi.MessageGift.CONSTRUCTOR || content.getConstructor() == TdApi.MessageUpgradedGift.CONSTRUCTOR ? ContentPreview.EMOJI_GIFT : ContentPreview.EMOJI_INFO, line);
          }
        }
        return null;
      }
    }
  }

  private static ContentPreview text (ContentPreview.Emoji emoji, String text) {
    return new ContentPreview(emoji, 0, text, false);
  }
}
