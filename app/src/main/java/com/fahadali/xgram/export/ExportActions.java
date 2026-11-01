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
package com.fahadali.xgram.export;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.telegram.Tdlib;

import java.math.BigDecimal;

import me.vkryl.core.CurrencyUtils;
import tgx.td.Td;

/**
 * Service messages. For now each one is written as one line ("information_text" in the JSON, a centred line in the
 * HTML): Telegram X's preview text, or Desktop's sentence where the preview would name people; Desktop's named actions
 * with their fields come later.
 */
final class ExportActions {
  private ExportActions () { }

  /**
   * Whether {@code content} is a service message (Desktop's "type": "service"). An explicit list, after Telegram X's
   * routing to TGMessageService (TGMessage.java) plus the service-like contents it draws with other classes. Calls are
   * service messages in Desktop's JSON too.
   */
  static boolean isService (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
      case TdApi.MessageGiftedPremium.CONSTRUCTOR:
      case TdApi.MessageGiftedStars.CONSTRUCTOR:
      case TdApi.MessageGiftedGrams.CONSTRUCTOR:
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR:
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
      case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
      case TdApi.MessageChatJoinFromCommunity.CONSTRUCTOR:
      case TdApi.MessageChatAddedToCommunity.CONSTRUCTOR:
      case TdApi.MessageChatRemovedFromCommunity.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessageChatBoost.CONSTRUCTOR:
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR:
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR:
      case TdApi.MessageChatHasProtectedContentDisableRequested.CONSTRUCTOR:
      case TdApi.MessageChatHasProtectedContentToggled.CONSTRUCTOR:
      case TdApi.MessageChatShared.CONSTRUCTOR:
      case TdApi.MessageUsersShared.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR:
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR:
      case TdApi.MessageDirectMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR:
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
      case TdApi.MessageManagedBotCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
      case TdApi.MessageGiveawayCreated.CONSTRUCTOR:
      case TdApi.MessageGiveawayCompleted.CONSTRUCTOR:
      case TdApi.MessageGiveawayPrizeStars.CONSTRUCTOR:
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR:
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR:
      case TdApi.MessageGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageRefundedUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOffer.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOfferRejected.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApprovalFailed.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApproved.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostDeclined.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostPaid.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostRefunded.CONSTRUCTOR:
      case TdApi.MessageTonWalletTransfer.CONSTRUCTOR: // Desktop's ActionGramTransfer
      case TdApi.MessageTonConnectRequest.CONSTRUCTOR: // ActionWalletTonConnectRequest
        return true;
      default:
        return false;
    }
  }

  /**
   * Telegram X's one-line description of a message ("Joined the group", "Poll: …"), in the app language. Only on the
   * worker thread: for pinned messages and game scores it reads the other message synchronously, which throws on the
   * TDLib thread. Null when it can't be built.
   */
  @Nullable
  @WorkerThread
  static ContentPreview preview (Tdlib tdlib, long chatId, TdApi.Message message) {
    try {
      return ContentPreview.getChatListPreview(tdlib, chatId, message, false);
    } catch (Throwable t) {
      Log.w("XGram export: no preview for a message", t);
      return null;
    }
  }

  /** The preview's text, "" when there is none. */
  static String previewText (@Nullable ContentPreview preview) {
    if (preview == null) {
      return "";
    }
    try {
      final String text = preview.buildText(false);
      return text != null ? text : "";
    } catch (Throwable t) {
      return "";
    }
  }

  /** A line built from real names by {@link #namedLine}. */
  static final class Line {
    /** Desktop's sentence, after the actor's name ("invited Bob") or whole ("Bob is now within 50 meters from you"). */
    @NonNull
    final String text;
    final boolean afterActor;

    Line (@NonNull String text, boolean afterActor) {
      this.text = text;
      this.afterActor = afterActor;
    }
  }

  /**
   * The line for the contents whose Telegram X preview names people: the preview reads names from Telegram X's cache,
   * where XGram's local renames are written in, and the export carries the real names. Desktop's English sentences
   * (export_output_html.cpp) with names from {@code peers}; null when the preview names nobody (the writers add the
   * actor themselves). Step 4's action mapping replaces this.
   */
  @Nullable
  @WorkerThread
  static Line namedLine (Tdlib tdlib, ExportPeers peers, TdApi.Message message) {
    final TdApi.MessageContent content = message.content;
    switch (content.getConstructor()) {
      case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
        final long[] userIds = ((TdApi.MessageChatAddMembers) content).memberUserIds;
        if (userIds.length == 1 && userIds[0] == Td.getSenderUserId(message)) {
          return new Line("joined group", true);
        }
        return new Line("invited " + userNames(peers, userIds), true);
      }
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
        final long userId = ((TdApi.MessageChatDeleteMember) content).userId;
        return new Line(userId == Td.getSenderUserId(message) ? "left group" : "removed " + peers.user(userId).displayName(), true);
      }
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
        return new Line("invited " + userNames(peers, ((TdApi.MessageInviteVideoChatParticipants) content).userIds) + " to the voice chat", true);
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
        return new Line("joined Telegram", true);
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR:
        return new Line("made " + peers.user(((TdApi.MessageChatOwnerChanged) content).newOwnerUserId).displayName() + " the new main admin of the group", true);
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR: {
        final long userId = ((TdApi.MessageChatOwnerLeft) content).newOwnerUserId;
        if (userId == 0) {
          return null; // "Owner left" names nobody
        }
        return new Line(peers.user(userId).displayName() + " will become the new main admin in 7 days if " + peers.sender(message.senderId).displayName() + " does not return", false);
      }
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
        final TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) content;
        final String distance = distance(alert.distance);
        if (tdlib.isSelfSender(alert.travelerId)) {
          return new Line("You are now within " + distance + " from " + peers.sender(alert.watcherId).displayName(), false);
        }
        final String traveler = peers.sender(alert.travelerId).displayName();
        if (tdlib.isSelfSender(alert.watcherId)) {
          return new Line(traveler + " is now within " + distance + " from you", false);
        }
        return new Line(traveler + " is now within " + distance + " from " + peers.sender(alert.watcherId).displayName(), false);
      }
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        // The preview names the chat (a local rename too)
        final TdApi.MessagePaymentSuccessful payment = (TdApi.MessagePaymentSuccessful) content;
        final String amount = CurrencyUtils.buildAmount(payment.currency, payment.totalAmount);
        if (payment.isRecurring && !payment.isFirstRecurring) {
          return new Line("You were charged " + amount + " via recurring payment", false);
        }
        return new Line("You have successfully transferred " + amount + " for this invoice" + (payment.isFirstRecurring ? " and allowed future recurring payments" : ""), false);
      }
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR: {
        final TdApi.MessagePaymentRefunded refund = (TdApi.MessagePaymentRefunded) content;
        return new Line(peers.sender(refund.ownerId).displayName() + " refunded back " + CurrencyUtils.buildAmount(refund.currency, refund.totalAmount), false);
      }
      case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
        final TdApi.MessageGiftedPremium gift = (TdApi.MessageGiftedPremium) content;
        if (gift.dayCount == 0 || gift.currency == null || gift.currency.isEmpty()) {
          return new Line("sent you a gift.", true);
        }
        return new Line("sent you a gift for " + CurrencyUtils.buildAmount(gift.currency, gift.amount) + ": Telegram Premium for " + gift.dayCount + " days.", true);
      }
      case TdApi.MessageGiftedStars.CONSTRUCTOR: {
        final TdApi.MessageGiftedStars gift = (TdApi.MessageGiftedStars) content;
        if (gift.starCount == 0 || gift.currency == null || gift.currency.isEmpty()) {
          return new Line("sent you a gift.", true);
        }
        return new Line("sent you a gift for " + CurrencyUtils.buildAmount(gift.currency, gift.amount) + ": " + gift.starCount + " Telegram Stars.", true);
      }
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR: {
        final TdApi.MessagePremiumGiftCode code = (TdApi.MessagePremiumGiftCode) content;
        final String premium = "Telegram Premium for " + code.dayCount + (code.dayCount > 1 ? " days" : " day");
        return new Line(code.isUnclaimed ? "This is an unclaimed " + premium + " prize in a giveaway organized by a channel." :
          code.isFromGiveaway ? "You won a " + premium + " prize in a giveaway organized by a channel." :
          "You've received a " + premium + " gift from a channel.", false);
      }
      case TdApi.MessageStory.CONSTRUCTOR:
        return new Line("", false); // the preview names the poster; Desktop writes nothing for a story either
      default:
        return null;
    }
  }

  /** Desktop's SerializeList: "Alice", "Alice and Bob", "Alice, Bob and Carol". */
  private static String userNames (ExportPeers peers, long[] userIds) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < userIds.length; i++) {
      if (i > 0) {
        b.append(i == userIds.length - 1 ? " and " : ", ");
      }
      b.append(peers.user(userIds[i]).displayName());
    }
    return b.toString();
  }

  /** Desktop's distance: "1 meter", "250 meters", "1.25 km" (whole tens of meters). */
  private static String distance (int meters) {
    if (meters >= 1000) {
      return BigDecimal.valueOf(10L * (meters / 10), 3).stripTrailingZeros().toPlainString() + " km";
    }
    return meters == 1 ? "1 meter" : meters + " meters";
  }
}
