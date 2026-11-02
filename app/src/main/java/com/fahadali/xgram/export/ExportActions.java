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

import android.util.JsonWriter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.telegram.Tdlib;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.vkryl.core.CurrencyUtils;
import tgx.td.ChatId;
import tgx.td.MessageId;

/**
 * Service messages as Telegram Desktop exports them: the JSON "action" with its fields (export_output_json.cpp
 * SerializeMessage) and the English sentence of the HTML (export_output_html.cpp pushMessage), with the real names of
 * the people and chats ({@link ExportPeers}, never XGram's local renames). Contents Desktop has no action for are
 * written as Desktop writes a custom action: the actor and an "information_text" line. Calls are service messages in
 * the JSON and messages with a call block in the HTML, as in Desktop.
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

  /** Calls are service messages in Desktop's JSON, and messages with a call block in its HTML. */
  static boolean isCall (TdApi.MessageContent content) {
    final int constructor = content.getConstructor();
    return constructor == TdApi.MessageCall.CONSTRUCTOR || constructor == TdApi.MessageGroupCall.CONSTRUCTOR;
  }

  /** What describing one service message needs. */
  static final class Context {
    final Tdlib tdlib;
    final ExportPeers peers;
    final ExportPeers.Dialog dialog;
    final long chatId;
    final TdApi.Message message;

    Context (Tdlib tdlib, ExportPeers peers, ExportPeers.Dialog dialog, long chatId, TdApi.Message message) {
      this.tdlib = tdlib;
      this.peers = peers;
      this.dialog = dialog;
      this.chatId = chatId;
      this.message = message;
    }
  }

  /** Desktop's wrapMessageLink: {@code html} linking to the message (a TDLib id) on the page that holds it. */
  interface Links {
    String link (long messageId, String html);
  }

  /** Whether Desktop has an action for {@code content}: {@link #writeJson} and {@link #html} describe it. */
  static boolean hasAction (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
      case TdApi.MessageChatJoinFromCommunity.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR:
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR:
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
      case TdApi.MessageGiftedPremium.CONSTRUCTOR:
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
      case TdApi.MessageGiveawayCreated.CONSTRUCTOR:
      case TdApi.MessageGiveawayCompleted.CONSTRUCTOR:
      case TdApi.MessageChatBoost.CONSTRUCTOR:
      case TdApi.MessageGiftedStars.CONSTRUCTOR:
      case TdApi.MessageGiftedGrams.CONSTRUCTOR:
      case TdApi.MessageGiveawayPrizeStars.CONSTRUCTOR:
      case TdApi.MessageGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageRefundedUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOffer.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOfferRejected.CONSTRUCTOR:
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR:
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessageDirectMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR:
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR:
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApproved.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApprovalFailed.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostDeclined.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostPaid.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostRefunded.CONSTRUCTOR:
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR:
      case TdApi.MessageChatHasProtectedContentToggled.CONSTRUCTOR:
      case TdApi.MessageChatHasProtectedContentDisableRequested.CONSTRUCTOR:
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR:
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR:
      case TdApi.MessageManagedBotCreated.CONSTRUCTOR:
      case TdApi.MessageTonWalletTransfer.CONSTRUCTOR:
      case TdApi.MessageTonConnectRequest.CONSTRUCTOR:
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
      case TdApi.MessageChatShared.CONSTRUCTOR:
      case TdApi.MessageUsersShared.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  /**
   * The "information_text" of the service messages Desktop has no action for (it doesn't receive them, or ignores
   * them), in English and without names; null for contents XGram doesn't know either (the caller falls back to
   * Telegram X's preview then).
   */
  @Nullable
  static String information (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR: {
        final TdApi.MessagePaymentSuccessfulBot payment = (TdApi.MessagePaymentSuccessfulBot) content;
        return "You received a payment of " + money(payment.currency, payment.totalAmount);
      }
      case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
        return "Data received from the «" + nonNull(((TdApi.MessageWebAppDataReceived) content).buttonText) + "» button";
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
        return "Telegram Passport data received";
      case TdApi.MessageChatAddedToCommunity.CONSTRUCTOR:
        return "The group was added to a community";
      case TdApi.MessageChatRemovedFromCommunity.CONSTRUCTOR:
        return "The group was removed from its community";
      default:
        return null;
    }
  }

  // JSON (SerializeMessage's action part)

  /**
   * The keys Desktop writes for the action, after "date" ("edited"): "actor" / "actor_id" where Desktop has them,
   * "action" and its fields. A changed or suggested photo's keys follow ({@link ExportJsonWriter} writes them). Only for
   * {@link #hasAction} contents.
   */
  @WorkerThread
  static void writeJson (JsonWriter json, Context c) throws IOException {
    final TdApi.Message message = c.message;
    final TdApi.MessageContent content = message.content;
    final ExportPeers peers = c.peers;
    switch (content.getConstructor()) {
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR: {
        final TdApi.MessageBasicGroupChatCreate create = (TdApi.MessageBasicGroupChatCreate) content;
        actor(json, c);
        action(json, "create_group");
        string(json, "title", create.title);
        members(json, "members", peers, create.memberUserIds);
        break;
      }
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
        // messageActionChannelCreate, which supergroups get too
        actor(json, c);
        action(json, "create_channel");
        string(json, "title", ((TdApi.MessageSupergroupChatCreate) content).title);
        break;
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        actor(json, c);
        action(json, "edit_group_title");
        string(json, "title", ((TdApi.MessageChatChangeTitle) content).title);
        break;
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        actor(json, c);
        action(json, "edit_group_photo");
        break;
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
        actor(json, c);
        action(json, "delete_group_photo");
        break;
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
        actor(json, c);
        action(json, "invite_members");
        members(json, "members", peers, ((TdApi.MessageChatAddMembers) content).memberUserIds);
        break;
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
        actor(json, c);
        action(json, "remove_members");
        members(json, "members", peers, new long[] {((TdApi.MessageChatDeleteMember) content).userId});
        break;
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
        // TDLib doesn't say who made the link: Desktop's "inviter" (always written) is null, as for a user it doesn't know
        actor(json, c);
        action(json, "join_group_by_link");
        json.name("inviter").nullValue();
        break;
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
        actor(json, c);
        action(json, "join_group_by_request");
        break;
      case TdApi.MessageChatJoinFromCommunity.CONSTRUCTOR:
        actor(json, c);
        action(json, "join_group_via_community");
        json.name("community_id").value(((TdApi.MessageChatJoinFromCommunity) content).communityId);
        break;
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
        actor(json, c);
        action(json, "migrate_to_supergroup");
        break;
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
        actor(json, c);
        action(json, "migrate_from_group");
        string(json, "title", ((TdApi.MessageChatUpgradeFrom) content).title);
        break;
      case TdApi.MessagePinMessage.CONSTRUCTOR:
        actor(json, c);
        action(json, "pin_message");
        messageId(json, "message_id", ((TdApi.MessagePinMessage) content).messageId);
        break;
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        final TdApi.MessageGameScore score = (TdApi.MessageGameScore) content;
        actor(json, c);
        action(json, "score_in_game");
        messageId(json, "game_message_id", score.gameMessageId);
        json.name("score").value(score.score);
        break;
      }
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        final TdApi.MessagePaymentSuccessful payment = (TdApi.MessagePaymentSuccessful) content;
        action(json, "send_payment");
        json.name("amount").value(payment.totalAmount);
        string(json, "currency", payment.currency);
        if (messageId(json, "invoice_message_id", payment.invoiceMessageId) && payment.invoiceChatId != 0 && payment.invoiceChatId != c.chatId) {
          json.name("reply_to_peer_id").value(ExportPeers.peerIdOf(payment.invoiceChatId));
        }
        if (payment.isFirstRecurring) {
          json.name("recurring").value("init");
        } else if (payment.isRecurring) {
          json.name("recurring").value("used");
        }
        break;
      }
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR: {
        final TdApi.MessagePaymentRefunded refund = (TdApi.MessagePaymentRefunded) content;
        action(json, "refunded_payment");
        json.name("amount").value(refund.totalAmount);
        string(json, "currency", refund.currency);
        final ExportPeers.Peer owner = peers.sender(refund.ownerId);
        peerName(json, "peer_name", owner);
        json.name("peer_id").value(owner.peerId);
        string(json, "charge_id", refund.telegramPaymentChargeId);
        break;
      }
      case TdApi.MessageCall.CONSTRUCTOR: {
        final TdApi.MessageCall call = (TdApi.MessageCall) content;
        actor(json, c);
        action(json, "phone_call");
        if (call.duration > 0) {
          json.name("duration_seconds").value(call.duration);
        }
        string(json, "discard_reason", discardReason(call.discardReason));
        break;
      }
      case TdApi.MessageGroupCall.CONSTRUCTOR: {
        final TdApi.MessageGroupCall call = (TdApi.MessageGroupCall) content;
        actor(json, c);
        action(json, "conference_call");
        if (call.duration > 0) {
          json.name("duration_seconds").value(call.duration);
        }
        json.name("is_active").value(call.isActive);
        json.name("is_missed").value(call.wasMissed);
        break;
      }
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        actor(json, c);
        action(json, "take_screenshot");
        break;
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
        actor(json, c);
        string(json, "information_text", ((TdApi.MessageCustomServiceAction) content).text);
        break;
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR: {
        final TdApi.BotWriteAccessAllowReason reason = ((TdApi.MessageBotWriteAccessAllowed) content).reason;
        final int type = reason != null ? reason.getConstructor() : 0;
        if (type == TdApi.BotWriteAccessAllowReasonAddedToAttachmentMenu.CONSTRUCTOR) {
          action(json, "attach_menu_bot_allowed");
        } else if (type == TdApi.BotWriteAccessAllowReasonAcceptedRequest.CONSTRUCTOR) {
          action(json, "web_app_bot_allowed");
        } else if (type == TdApi.BotWriteAccessAllowReasonLaunchedWebApp.CONSTRUCTOR) {
          // TDLib has no id for the app: only Desktop's "reason_app_name"
          final TdApi.WebApp app = ((TdApi.BotWriteAccessAllowReasonLaunchedWebApp) reason).webApp;
          action(json, "allow_sending_messages");
          string(json, "reason_app_name", app != null ? app.title : "");
        } else {
          action(json, "allow_sending_messages");
          string(json, "reason_domain", type == TdApi.BotWriteAccessAllowReasonConnectedWebsite.CONSTRUCTOR ?
            ((TdApi.BotWriteAccessAllowReasonConnectedWebsite) reason).domainName : "");
        }
        break;
      }
      case TdApi.MessagePassportDataSent.CONSTRUCTOR: {
        action(json, "send_passport_values");
        json.name("values");
        json.beginArray();
        final TdApi.PassportElementType[] types = ((TdApi.MessagePassportDataSent) content).types;
        if (types != null) {
          for (TdApi.PassportElementType type : types) {
            json.value(passportType(type, true));
          }
        }
        json.endArray();
        break;
      }
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
        actor(json, c);
        action(json, "joined_telegram");
        break;
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
        final TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) content;
        action(json, "proximity_reached");
        if (alert.travelerId != null) {
          final ExportPeers.Peer from = peers.sender(alert.travelerId);
          peerName(json, "from", from);
          json.name("from_id").value(from.peerId);
        }
        if (alert.watcherId != null) {
          final ExportPeers.Peer to = peers.sender(alert.watcherId);
          peerName(json, "to", to);
          json.name("to_id").value(to.peerId);
        }
        json.name("distance").value(alert.distance);
        break;
      }
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
        actor(json, c);
        action(json, "group_call");
        break;
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
        final int duration = ((TdApi.MessageVideoChatEnded) content).duration;
        actor(json, c);
        action(json, "group_call");
        if (duration > 0) {
          json.name("duration").value(duration);
        }
        break;
      }
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
        actor(json, c);
        action(json, "invite_to_group_call");
        members(json, "members", peers, ((TdApi.MessageInviteVideoChatParticipants) content).userIds);
        break;
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
        actor(json, c);
        action(json, "set_messages_ttl");
        json.name("period").value(((TdApi.MessageChatSetMessageAutoDeleteTime) content).messageAutoDeleteTime);
        break;
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
        actor(json, c);
        action(json, "group_call_scheduled");
        json.name("schedule_date").value(((TdApi.MessageVideoChatScheduled) content).startDate);
        break;
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        actor(json, c);
        action(json, "edit_chat_theme");
        string(json, "emoticon", themeEmoji(((TdApi.MessageChatSetTheme) content).theme));
        break;
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
        action(json, "send_webview_data");
        string(json, "text", ((TdApi.MessageWebAppDataSent) content).buttonText); // Desktop's key, before the message's own "text"
        break;
      case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
        final TdApi.MessageGiftedPremium gift = (TdApi.MessageGiftedPremium) content;
        actor(json, c);
        action(json, "send_premium_gift");
        string(json, "cost", cost(gift.currency, gift.amount));
        if (gift.dayCount != 0) {
          json.name("days").value(gift.dayCount);
        }
        break;
      }
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR: {
        final TdApi.MessagePremiumGiftCode code = (TdApi.MessagePremiumGiftCode) content;
        action(json, "gift_code_prize");
        string(json, "gift_code", code.code);
        if (code.creatorId != null) {
          json.name("boost_peer_id").value(peers.sender(code.creatorId).peerId);
        }
        json.name("days").value(code.dayCount);
        json.name("unclaimed").value(code.isUnclaimed);
        json.name("via_giveaway").value(code.isFromGiveaway);
        break;
      }
      case TdApi.MessageGiveawayCreated.CONSTRUCTOR:
        action(json, "giveaway_launch");
        break;
      case TdApi.MessageGiveawayCompleted.CONSTRUCTOR: {
        final TdApi.MessageGiveawayCompleted completed = (TdApi.MessageGiveawayCompleted) content;
        action(json, "giveaway_results");
        json.name("winners").value(completed.winnerCount);
        json.name("unclaimed").value(completed.unclaimedPrizeCount);
        json.name("stars").value(completed.isStarGiveaway);
        break;
      }
      case TdApi.MessageChatBoost.CONSTRUCTOR:
        actor(json, c);
        action(json, "boost_apply");
        json.name("boosts").value(((TdApi.MessageChatBoost) content).boostCount);
        break;
      case TdApi.MessageGiftedStars.CONSTRUCTOR: {
        final TdApi.MessageGiftedStars gift = (TdApi.MessageGiftedStars) content;
        actor(json, c);
        action(json, "send_stars_gift");
        string(json, "cost", cost(gift.currency, gift.amount));
        if (gift.starCount != 0) {
          json.name("amount_whole").value(gift.starCount);
          json.name("amount_nano").value(0);
        }
        break;
      }
      case TdApi.MessageGiftedGrams.CONSTRUCTOR: {
        final long nanos = ((TdApi.MessageGiftedGrams) content).gramAmount;
        actor(json, c);
        action(json, "send_ton_gift");
        if (nanos != 0) {
          json.name("amount_whole").value(nanos / NANOS_IN_GRAM);
          json.name("amount_nano").value(nanos % NANOS_IN_GRAM);
        }
        break;
      }
      case TdApi.MessageGiveawayPrizeStars.CONSTRUCTOR: {
        final TdApi.MessageGiveawayPrizeStars prize = (TdApi.MessageGiveawayPrizeStars) content;
        actor(json, c);
        action(json, "stars_prize");
        if (prize.boostedChatId != 0) {
          json.name("boost_peer_id").value(ExportPeers.peerIdOf(prize.boostedChatId));
          peerName(json, "boost_peer_name", peers.chat(prize.boostedChatId));
        }
        json.name("stars").value(prize.starCount);
        json.name("is_unclaimed").value(prize.isUnclaimed);
        json.name("giveaway_msg_id").value(MessageId.toServerMessageId(prize.giveawayMessageId));
        string(json, "transaction_id", prize.transactionId);
        break;
      }
      case TdApi.MessageGift.CONSTRUCTOR: {
        final TdApi.MessageGift gift = (TdApi.MessageGift) content;
        starGift(json, c, gift.gift != null ? gift.gift.id : 0, gift.gift != null ? gift.gift.starCount : 0,
          gift.gift != null && gift.gift.overallLimits != null, gift.isPrivate, gift.text);
        break;
      }
      case TdApi.MessageUpgradedGift.CONSTRUCTOR: {
        // Desktop's unique gifts: only the id
        final TdApi.MessageUpgradedGift gift = (TdApi.MessageUpgradedGift) content;
        starGift(json, c, gift.gift != null ? gift.gift.id : 0, 0, false, gift.isPrivate, gift.text);
        break;
      }
      case TdApi.MessageRefundedUpgradedGift.CONSTRUCTOR: {
        final TdApi.MessageRefundedUpgradedGift gift = (TdApi.MessageRefundedUpgradedGift) content;
        starGift(json, c, gift.gift != null ? gift.gift.id : 0, 0, false, false, null);
        break;
      }
      case TdApi.MessageUpgradedGiftPurchaseOffer.CONSTRUCTOR: {
        final TdApi.MessageUpgradedGiftPurchaseOffer offer = (TdApi.MessageUpgradedGiftPurchaseOffer) content;
        starGift(json, c, offer.gift != null ? offer.gift.id : 0, 0, false, false, null);
        break;
      }
      case TdApi.MessageUpgradedGiftPurchaseOfferRejected.CONSTRUCTOR: {
        final TdApi.MessageUpgradedGiftPurchaseOfferRejected offer = (TdApi.MessageUpgradedGiftPurchaseOfferRejected) content;
        starGift(json, c, offer.gift != null ? offer.gift.id : 0, 0, false, false, null);
        break;
      }
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR: {
        final TdApi.MessagePaidMessagesRefunded refund = (TdApi.MessagePaidMessagesRefunded) content;
        actor(json, c);
        action(json, "paid_messages_refund");
        json.name("messages_count").value(refund.messageCount);
        json.name("stars_count").value(refund.starCount);
        break;
      }
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
        actor(json, c);
        action(json, "paid_messages_price_change");
        json.name("price_stars").value(((TdApi.MessagePaidMessagePriceChanged) content).paidMessageStarCount);
        json.name("is_broadcast_messages_allowed").value(false);
        break;
      case TdApi.MessageDirectMessagePriceChanged.CONSTRUCTOR: {
        final TdApi.MessageDirectMessagePriceChanged price = (TdApi.MessageDirectMessagePriceChanged) content;
        actor(json, c);
        action(json, "paid_messages_price_change");
        json.name("price_stars").value(price.paidMessageStarCount);
        json.name("is_broadcast_messages_allowed").value(price.isEnabled);
        break;
      }
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR: {
        final TdApi.MessageChecklistTasksDone done = (TdApi.MessageChecklistTasksDone) content;
        actor(json, c);
        action(json, "todo_completions");
        ints(json, "completed", done.markedAsDoneTaskIds);
        ints(json, "incompleted", done.markedAsNotDoneTaskIds);
        break;
      }
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR: {
        actor(json, c);
        action(json, "todo_append_tasks");
        json.name("items");
        tasks(json, ((TdApi.MessageChecklistTasksAdded) content).tasks);
        break;
      }
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR:
        actor(json, c);
        action(json, "poll_append_answer");
        string(json, "option", ExportText.plain(((TdApi.MessagePollOptionAdded) content).text));
        break;
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR:
        actor(json, c);
        action(json, "poll_delete_answer");
        string(json, "option", ExportText.plain(((TdApi.MessagePollOptionDeleted) content).text));
        break;
      case TdApi.MessageSuggestedPostApproved.CONSTRUCTOR: {
        final TdApi.MessageSuggestedPostApproved approved = (TdApi.MessageSuggestedPostApproved) content;
        actor(json, c);
        action(json, "process_suggested_post");
        price(json, approved.price);
        json.name("scheduled_date").value(approved.sendDate);
        break;
      }
      case TdApi.MessageSuggestedPostApprovalFailed.CONSTRUCTOR:
        // messageActionSuggestedPostApproval with balance_too_low: Desktop writes it as an approval, without a date
        actor(json, c);
        action(json, "process_suggested_post");
        price(json, ((TdApi.MessageSuggestedPostApprovalFailed) content).price);
        json.name("scheduled_date").value(0);
        break;
      case TdApi.MessageSuggestedPostDeclined.CONSTRUCTOR:
        actor(json, c);
        action(json, "process_suggested_post");
        json.name("rejected").value(true);
        string(json, "comment", ((TdApi.MessageSuggestedPostDeclined) content).comment);
        break;
      case TdApi.MessageSuggestedPostPaid.CONSTRUCTOR: {
        final TdApi.MessageSuggestedPostPaid paid = (TdApi.MessageSuggestedPostPaid) content;
        actor(json, c);
        action(json, "suggested_post_success");
        if (paid.starAmount != null && paid.starAmount.starCount != 0) {
          json.name("price_amount_whole").value(String.valueOf(paid.starAmount.starCount));
          json.name("price_amount_nano").value(String.valueOf(paid.starAmount.nanostarCount));
          json.name("price_currency").value("Stars");
        } else {
          json.name("price_amount_whole").value(String.valueOf(paid.gramAmount / NANOS_IN_GRAM));
          json.name("price_amount_nano").value(String.valueOf(paid.gramAmount % NANOS_IN_GRAM));
          json.name("price_currency").value("TON");
        }
        break;
      }
      case TdApi.MessageSuggestedPostRefunded.CONSTRUCTOR: {
        final TdApi.SuggestedPostRefundReason reason = ((TdApi.MessageSuggestedPostRefunded) content).reason;
        actor(json, c);
        action(json, "suggested_post_refund");
        json.name("user_initiated").value(reason instanceof TdApi.SuggestedPostRefundReasonPaymentRefunded);
        break;
      }
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR: {
        final TdApi.Birthdate birthdate = ((TdApi.MessageSuggestBirthdate) content).birthdate;
        actor(json, c);
        action(json, "suggest_birthday");
        if (birthdate != null) {
          json.name("day").value(birthdate.day);
          json.name("month").value(birthdate.month);
          if (birthdate.year != 0) {
            json.name("year").value(birthdate.year);
          }
        }
        break;
      }
      case TdApi.MessageChatHasProtectedContentToggled.CONSTRUCTOR:
        actor(json, c);
        action(json, "no_forwards_toggle");
        json.name("new_value").value(((TdApi.MessageChatHasProtectedContentToggled) content).newHasProtectedContent);
        break;
      case TdApi.MessageChatHasProtectedContentDisableRequested.CONSTRUCTOR:
        actor(json, c);
        action(json, "no_forwards_request");
        json.name("expired").value(((TdApi.MessageChatHasProtectedContentDisableRequested) content).isExpired);
        json.name("new_value").value(false); // a request to turn protection off
        break;
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR: {
        final long userId = ((TdApi.MessageChatOwnerLeft) content).newOwnerUserId;
        actor(json, c);
        action(json, "new_creator_pending");
        if (userId != 0) {
          peerName(json, "new_creator", peers.user(userId));
        }
        break;
      }
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR:
        actor(json, c);
        action(json, "change_creator");
        peerName(json, "new_creator", peers.user(((TdApi.MessageChatOwnerChanged) content).newOwnerUserId));
        break;
      case TdApi.MessageManagedBotCreated.CONSTRUCTOR:
        actor(json, c);
        action(json, "managed_bot_created");
        peerName(json, "bot", peers.user(((TdApi.MessageManagedBotCreated) content).botUserId));
        break;
      case TdApi.MessageTonWalletTransfer.CONSTRUCTOR: {
        final TdApi.MessageTonWalletTransfer transfer = (TdApi.MessageTonWalletTransfer) content;
        actor(json, c);
        action(json, "gram_transfer");
        json.name("amount_whole").value(transfer.amount / NANOS_IN_GRAM);
        json.name("amount_nano").value(transfer.amount % NANOS_IN_GRAM);
        string(json, "peer_address", transfer.peerAddress);
        string(json, "transaction_id", transfer.transactionId);
        json.name("comment_encrypted").value(transfer.isCommentEncrypted);
        string(json, transfer.isCommentEncrypted ? "encrypted_comment_base64" : "comment", transfer.comment);
        break;
      }
      case TdApi.MessageTonConnectRequest.CONSTRUCTOR: {
        final TdApi.MessageTonConnectRequest request = (TdApi.MessageTonConnectRequest) content;
        actor(json, c);
        action(json, "ton_connect_request");
        json.name("session_id").value(request.sessionId);
        json.name("expires").value(request.state instanceof TdApi.TonConnectRequestStatePending ?
          ((TdApi.TonConnectRequestStatePending) request.state).expirationDate : 0);
        string(json, "topic", request.topic);
        string(json, "trace_id", request.traceId);
        json.name("accepted").value(request.state instanceof TdApi.TonConnectRequestStateAccepted);
        json.name("declined").value(request.state instanceof TdApi.TonConnectRequestStateRejected);
        break;
      }
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
        actor(json, c);
        action(json, "topic_created");
        string(json, "title", ((TdApi.MessageForumTopicCreated) content).name);
        break;
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR: {
        final TdApi.MessageForumTopicEdited edited = (TdApi.MessageForumTopicEdited) content;
        actor(json, c);
        action(json, "topic_edit");
        string(json, "new_title", edited.name);
        if (edited.editIconCustomEmojiId) {
          json.name("new_icon_emoji_id").value(edited.iconCustomEmojiId);
        }
        break;
      }
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
        // Desktop writes a bare "topic_edit" for these; XGram adds what changed
        actor(json, c);
        action(json, "topic_edit");
        json.name("is_closed").value(((TdApi.MessageForumTopicIsClosedToggled) content).isClosed);
        break;
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
        actor(json, c);
        action(json, "topic_edit");
        json.name("is_hidden").value(((TdApi.MessageForumTopicIsHiddenToggled) content).isHidden);
        break;
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
        actor(json, c);
        action(json, "suggest_profile_photo");
        break;
      case TdApi.MessageChatSetBackground.CONSTRUCTOR: {
        final long old = ((TdApi.MessageChatSetBackground) content).oldBackgroundMessageId;
        actor(json, c);
        action(json, old != 0 ? "set_same_chat_wallpaper" : "set_chat_wallpaper");
        messageId(json, "message_id", old);
        break;
      }
      case TdApi.MessageChatShared.CONSTRUCTOR: {
        final TdApi.MessageChatShared shared = (TdApi.MessageChatShared) content;
        actor(json, c);
        action(json, "requested_peer");
        json.name("button_id").value(shared.buttonId);
        // A string holding the array, as Desktop pushes it (push("peers", SerializeArray(...)))
        json.name("peers").value(shared.chat != null ? "[" + desktopPeerId(shared.chat.chatId) + "]" : "[]");
        break;
      }
      case TdApi.MessageUsersShared.CONSTRUCTOR: {
        final TdApi.MessageUsersShared shared = (TdApi.MessageUsersShared) content;
        actor(json, c);
        action(json, "requested_peer");
        json.name("button_id").value(shared.buttonId);
        // A string holding the array, as Desktop pushes it (push("peers", SerializeArray(...)))
        final StringBuilder ids = new StringBuilder("[");
        if (shared.users != null) {
          for (TdApi.SharedUser user : shared.users) {
            if (ids.length() > 1) {
              ids.append(',');
            }
            ids.append(user.userId); // Desktop's PeerId value of a user is its id
          }
        }
        json.name("peers").value(ids.append(']').toString());
        break;
      }
      default:
        actor(json, c);
        break;
    }
  }

  private static void starGift (JsonWriter json, Context c, long giftId, long stars, boolean limited, boolean anonymous, @Nullable TdApi.FormattedText text) throws IOException {
    actor(json, c);
    action(json, "send_star_gift");
    json.name("gift_id").value(giftId);
    json.name("stars").value(stars);
    json.name("is_limited").value(limited);
    json.name("is_anonymous").value(anonymous);
    json.name("gift_text");
    ExportText.writeValue(json, text);
  }

  /** Desktop's todo items: [{text, id}]. */
  static void tasks (JsonWriter json, @Nullable TdApi.ChecklistTask[] tasks) throws IOException {
    json.beginArray();
    if (tasks != null) {
      for (TdApi.ChecklistTask task : tasks) {
        json.beginObject();
        json.name("text");
        ExportText.writeValue(json, task.text);
        json.name("id").value(task.id);
        json.endObject();
      }
    }
    json.endArray();
  }

  private static void price (JsonWriter json, @Nullable TdApi.SuggestedPostPrice price) throws IOException {
    long whole = 0, nano = 0;
    String currency = "Stars";
    if (price instanceof TdApi.SuggestedPostPriceStar) {
      whole = ((TdApi.SuggestedPostPriceStar) price).starCount;
    } else if (price instanceof TdApi.SuggestedPostPriceGram) {
      final long cents = ((TdApi.SuggestedPostPriceGram) price).gramCentCount;
      whole = cents / 100;
      nano = (cents % 100) * (NANOS_IN_GRAM / 100);
      currency = "TON";
    }
    // Desktop writes these two as strings
    json.name("price_amount_whole").value(String.valueOf(whole));
    json.name("price_amount_nano").value(String.valueOf(nano));
    json.name("price_currency").value(currency);
  }

  private static void actor (JsonWriter json, Context c) throws IOException {
    final ExportPeers.Peer actor = c.peers.sender(c.message.senderId);
    peerName(json, "actor", actor);
    json.name("actor_id").value(actor.peerId);
  }

  private static void action (JsonWriter json, String action) throws IOException {
    json.name("action").value(action);
  }

  /** Desktop's StringAllowNull: a deleted account's name is null. */
  static void peerName (JsonWriter json, String key, ExportPeers.Peer peer) throws IOException {
    json.name(key);
    final String name = peer.name();
    if (name.isEmpty()) {
      json.nullValue();
    } else {
      json.value(name);
    }
  }

  /** Desktop's push for strings: an empty one is left out. */
  private static void string (JsonWriter json, String key, @Nullable String value) throws IOException {
    if (value != null && !value.isEmpty()) {
      json.name(key).value(value);
    }
  }

  private static void members (JsonWriter json, String key, ExportPeers peers, @Nullable long[] userIds) throws IOException {
    json.name(key);
    json.beginArray();
    if (userIds != null) {
      for (long userId : userIds) {
        final String name = peers.user(userId).name();
        if (name.isEmpty()) {
          json.nullValue();
        } else {
          json.value(name);
        }
      }
    }
    json.endArray();
  }

  private static void ints (JsonWriter json, String key, @Nullable int[] values) throws IOException {
    json.name(key);
    json.beginArray();
    if (values != null) {
      for (int value : values) {
        json.value(value);
      }
    }
    json.endArray();
  }

  /** The server id of a message of this chat under {@code key}; nothing for none. */
  private static boolean messageId (JsonWriter json, String key, long messageId) throws IOException {
    final long serverId = messageId != 0 ? MessageId.toServerMessageId(messageId) : 0;
    if (serverId == 0) {
      return false;
    }
    json.name(key).value(serverId);
    return true;
  }

  /**
   * Desktop's PeerId value, as its "requested_peer" writes it: a user's id, a basic group's id + 2^48, a channel's id
   * + 2^49.
   */
  private static long desktopPeerId (long chatId) {
    long id = ChatId.toSupergroupId(chatId);
    if (id != 0) {
      return id | (2L << 48);
    }
    id = ChatId.toBasicGroupId(chatId);
    if (id != 0) {
      return id | (1L << 48);
    }
    return ChatId.toUserId(chatId);
  }

  /** Desktop's discard reasons: TDLib's "declined" is the server's "busy". */
  @NonNull
  private static String discardReason (@Nullable TdApi.CallDiscardReason reason) {
    if (reason == null) {
      return "";
    }
    switch (reason.getConstructor()) {
      case TdApi.CallDiscardReasonMissed.CONSTRUCTOR:
        return "missed";
      case TdApi.CallDiscardReasonDeclined.CONSTRUCTOR:
        return "busy";
      case TdApi.CallDiscardReasonDisconnected.CONSTRUCTOR:
        return "disconnect";
      case TdApi.CallDiscardReasonHungUp.CONSTRUCTOR:
        return "hangup";
      case TdApi.CallDiscardReasonUpgradeToGroupCall.CONSTRUCTOR:
        return "migrate_conference_all"; // sic, Desktop
      default:
        return "";
    }
  }

  @NonNull
  private static String themeEmoji (@Nullable TdApi.ChatTheme theme) {
    if (theme instanceof TdApi.ChatThemeEmoji) {
      return nonNull(((TdApi.ChatThemeEmoji) theme).name);
    }
    return ""; // a gift's theme, or none: Desktop has no emoji for them either
  }

  // HTML (pushMessage's service texts)

  /**
   * Desktop's sentence for a {@link #hasAction} service message, as HTML (names escaped, « » as entities, links to
   * other messages through {@code links}); "" for calls, which are written as messages with a call block.
   */
  @NonNull
  @WorkerThread
  static String html (Context c, Links links) {
    final TdApi.Message message = c.message;
    final TdApi.MessageContent content = message.content;
    final ExportPeers peers = c.peers;
    final boolean isChannel = c.dialog.isChannel;
    final String from = peerHtml(peers.sender(message.senderId));
    switch (content.getConstructor()) {
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR: {
        final TdApi.MessageBasicGroupChatCreate create = (TdApi.MessageBasicGroupChatCreate) content;
        return from + " created group " + quoted(create.title) +
          (create.memberUserIds == null || create.memberUserIds.length == 0 ? "" : " with members " + usersHtml(peers, create.memberUserIds));
      }
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
        return "Channel " + quoted(((TdApi.MessageSupergroupChatCreate) content).title) + " created";
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR: {
        final String title = quoted(((TdApi.MessageChatChangeTitle) content).title);
        return isChannel ? "Channel title changed to " + title : from + " changed group title to " + title;
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        return isChannel ? "Channel photo changed" : from + " changed group photo";
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
        return isChannel ? "Channel photo removed" : from + " removed group photo";
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
        return from + " invited " + usersHtml(peers, ((TdApi.MessageChatAddMembers) content).memberUserIds);
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
        return from + " removed " + userHtml(peers, ((TdApi.MessageChatDeleteMember) content).userId);
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
        return from + " joined group by link"; // Desktop adds " from <inviter>", which TDLib doesn't give
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
        return from + " joined group by request";
      case TdApi.MessageChatJoinFromCommunity.CONSTRUCTOR:
        return from + " joined group via a community";
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
        return from + " converted this group to a supergroup";
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
        return from + " converted a basic group to this supergroup " + quoted(((TdApi.MessageChatUpgradeFrom) content).title);
      case TdApi.MessagePinMessage.CONSTRUCTOR:
        return from + " pinned " + link(links, ((TdApi.MessagePinMessage) content).messageId, "this message");
      case TdApi.MessageGameScore.CONSTRUCTOR: {
        final TdApi.MessageGameScore score = (TdApi.MessageGameScore) content;
        return from + " scored " + score.score + " in " + link(links, score.gameMessageId, "this game");
      }
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
        final TdApi.MessagePaymentSuccessful payment = (TdApi.MessagePaymentSuccessful) content;
        final String amount = ExportHtmlWriter.escape(money(payment.currency, payment.totalAmount));
        if (payment.isRecurring && !payment.isFirstRecurring) {
          return "You were charged " + amount + " via recurring payment";
        }
        final boolean sameChat = payment.invoiceChatId == 0 || payment.invoiceChatId == c.chatId;
        return "You have successfully transferred " + amount + " for " +
          (sameChat ? link(links, payment.invoiceMessageId, "this invoice") : "this invoice") +
          (payment.isFirstRecurring ? " and allowed future recurring payments" : "");
      }
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR: {
        final TdApi.MessagePaymentRefunded refund = (TdApi.MessagePaymentRefunded) content;
        return peerHtml(peers.sender(refund.ownerId)) + " refunded back " + ExportHtmlWriter.escape(money(refund.currency, refund.totalAmount));
      }
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR:
        return "";
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        return from + " took a screenshot";
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
        return ExportHtmlWriter.escape(((TdApi.MessageCustomServiceAction) content).text);
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR: {
        final TdApi.BotWriteAccessAllowReason reason = ((TdApi.MessageBotWriteAccessAllowed) content).reason;
        final int type = reason != null ? reason.getConstructor() : 0;
        if (type == TdApi.BotWriteAccessAllowReasonAddedToAttachmentMenu.CONSTRUCTOR) {
          return "You allowed this bot to message you when you added it in the attachment menu.";
        }
        if (type == TdApi.BotWriteAccessAllowReasonAcceptedRequest.CONSTRUCTOR) {
          return "You allowed this bot to message you in his web-app.";
        }
        if (type == TdApi.BotWriteAccessAllowReasonLaunchedWebApp.CONSTRUCTOR) {
          // Desktop swaps the app and the domain sentences (it checks app.isEmpty() the wrong way round)
          final TdApi.WebApp app = ((TdApi.BotWriteAccessAllowReasonLaunchedWebApp) reason).webApp;
          return "You allowed this bot to message you when you opened " + ExportHtmlWriter.escape(app != null ? app.title : "");
        }
        final String domain = type == TdApi.BotWriteAccessAllowReasonConnectedWebsite.CONSTRUCTOR ?
          ((TdApi.BotWriteAccessAllowReasonConnectedWebsite) reason).domainName : "";
        return "You allowed this bot to message you when you logged in on " + ExportHtmlWriter.escape(domain);
      }
      case TdApi.MessagePassportDataSent.CONSTRUCTOR: {
        final TdApi.PassportElementType[] types = ((TdApi.MessagePassportDataSent) content).types;
        final List<String> names = new ArrayList<>();
        if (types != null) {
          for (TdApi.PassportElementType type : types) {
            names.add(passportType(type, false));
          }
        }
        return "You have sent the following documents: " + list(names);
      }
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
        return from + " joined Telegram";
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
        final TdApi.MessageProximityAlertTriggered alert = (TdApi.MessageProximityAlertTriggered) content;
        final String distance = distance(alert.distance);
        if (c.tdlib.isSelfSender(alert.travelerId)) {
          return "You are now within " + distance + " from " + peerHtml(peers.sender(alert.watcherId));
        }
        final String traveler = peerHtml(peers.sender(alert.travelerId));
        if (c.tdlib.isSelfSender(alert.watcherId)) {
          return traveler + " is now within " + distance + " from you";
        }
        return traveler + " is now within " + distance + " from " + peerHtml(peers.sender(alert.watcherId));
      }
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
        return isChannel ? "Voice chat" : from + " started voice chat";
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
        // Desktop's text for the ended call (the server's action carries its duration)
        final int duration = ((TdApi.MessageVideoChatEnded) content).duration;
        final String durationText = duration > 0 ? " (" + duration + " seconds)" : "";
        return isChannel ? "Voice chat" + durationText : from + " started voice chat" + durationText;
      }
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
        return from + " invited " + usersHtml(peers, ((TdApi.MessageInviteVideoChatParticipants) content).userIds) + " to the voice chat";
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR: {
        final int period = ((TdApi.MessageChatSetMessageAutoDeleteTime) content).messageAutoDeleteTime;
        if (isChannel) {
          return period != 0 ? "New messages will auto-delete in " + ttl(period) : "New messages will not auto-delete";
        }
        return period != 0 ? from + " has set messages to auto-delete in " + ttl(period) : from + " has set messages not to auto-delete";
      }
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR: {
        final String date = dateTime(((TdApi.MessageVideoChatScheduled) content).startDate);
        return isChannel ? "Voice chat scheduled for " + date : from + " scheduled a voice chat for " + date;
      }
      case TdApi.MessageChatSetTheme.CONSTRUCTOR: {
        final String emoji = themeEmoji(((TdApi.MessageChatSetTheme) content).theme);
        if (emoji.isEmpty()) {
          return isChannel ? "Channel theme was disabled" : from + " disabled chat theme";
        }
        return isChannel ? "Channel theme was changed to " + ExportHtmlWriter.escape(emoji) : from + " changed chat theme to " + ExportHtmlWriter.escape(emoji);
      }
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
        return "You have just successfully transferred data from the " + quoted(((TdApi.MessageWebAppDataSent) content).buttonText) + " button to the bot";
      case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
        final TdApi.MessageGiftedPremium gift = (TdApi.MessageGiftedPremium) content;
        final String cost = cost(gift.currency, gift.amount);
        if (gift.dayCount == 0 || cost.isEmpty()) {
          return from + " sent you a gift.";
        }
        return from + " sent you a gift for " + ExportHtmlWriter.escape(cost) + ": Telegram Premium for " + gift.dayCount + " days.";
      }
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR: {
        final TdApi.MessagePremiumGiftCode code = (TdApi.MessagePremiumGiftCode) content;
        final String premium = "Telegram Premium for " + code.dayCount + (code.dayCount > 1 ? " days" : " day");
        return code.isUnclaimed ? "This is an unclaimed " + premium + " prize in a giveaway organized by a channel." :
          code.isFromGiveaway ? "You won a " + premium + " prize in a giveaway organized by a channel." :
          "You've received a " + premium + " gift from a channel.";
      }
      case TdApi.MessageGiveawayCreated.CONSTRUCTOR:
        return from + " just started a giveaway of Telegram Premium subscriptions to its followers.";
      case TdApi.MessageGiveawayCompleted.CONSTRUCTOR: {
        final TdApi.MessageGiveawayCompleted completed = (TdApi.MessageGiveawayCompleted) content;
        final boolean stars = completed.isStarGiveaway, unclaimed = completed.unclaimedPrizeCount != 0;
        if (completed.winnerCount == 0) {
          return "No winners of the giveaway could be selected.";
        }
        if (stars && unclaimed) {
          return "Some winners of the giveaway were randomly selected by Telegram and received their prize.";
        }
        if (unclaimed) {
          return "Some winners of the giveaway were randomly selected by Telegram and received private messages with giftcodes.";
        }
        return completed.winnerCount + " of the giveaway was randomly selected by Telegram and received " +
          (stars ? "their prize." : "private messages with giftcodes.");
      }
      case TdApi.MessageChatBoost.CONSTRUCTOR: {
        final int boosts = ((TdApi.MessageChatBoost) content).boostCount;
        return from + " boosted the group " + boosts + (boosts > 1 ? " times" : " time");
      }
      case TdApi.MessageGiftedStars.CONSTRUCTOR: {
        final TdApi.MessageGiftedStars gift = (TdApi.MessageGiftedStars) content;
        final String cost = cost(gift.currency, gift.amount);
        if (gift.starCount == 0 || cost.isEmpty()) {
          return from + " sent you a gift.";
        }
        return from + " sent you a gift for " + ExportHtmlWriter.escape(cost) + ": " + gift.starCount + " Telegram Stars.";
      }
      case TdApi.MessageGiftedGrams.CONSTRUCTOR:
        return from + " sent you a gift."; // Desktop adds the price, which TDLib doesn't give for TON gifts
      case TdApi.MessageGiveawayPrizeStars.CONSTRUCTOR: {
        final TdApi.MessageGiveawayPrizeStars prize = (TdApi.MessageGiveawayPrizeStars) content;
        return "You won a prize in a giveaway organized by " + peerHtml(peers.chat(prize.boostedChatId)) +
          ".\n Your prize is " + prize.starCount + " Telegram Stars.";
      }
      case TdApi.MessageGift.CONSTRUCTOR: {
        final TdApi.MessageGift gift = (TdApi.MessageGift) content;
        final long stars = gift.gift != null ? gift.gift.starCount : 0;
        return stars > 0 ? from + " sent you a gift of " + stars + " Telegram Stars." : from + " sent you a gift.";
      }
      case TdApi.MessageUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageRefundedUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOffer.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOfferRejected.CONSTRUCTOR:
        return from + " sent you a gift."; // Desktop's "a gift of 0 Telegram Stars" for unique gifts
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR: {
        final TdApi.MessagePaidMessagesRefunded refund = (TdApi.MessagePaidMessagesRefunded) content;
        final String chat = ExportHtmlWriter.escape(c.dialog.htmlName);
        final String what = refund.starCount + " Stars for " + refund.messageCount + " messages";
        return message.isOutgoing ? "You refunded " + what + " to " + chat : chat + " refunded " + what + " to you";
      }
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
        return "Price per message changed to " + ((TdApi.MessagePaidMessagePriceChanged) content).paidMessageStarCount + " Telegram Stars.";
      case TdApi.MessageDirectMessagePriceChanged.CONSTRUCTOR: {
        final TdApi.MessageDirectMessagePriceChanged price = (TdApi.MessageDirectMessagePriceChanged) content;
        return !price.isEnabled ? "Direct messages were disabled." :
          "Price per direct message changed to " + price.paidMessageStarCount + " Telegram Stars.";
      }
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR: {
        final TdApi.MessageChecklistTasksDone done = (TdApi.MessageChecklistTasksDone) content;
        final String completed = numbers(done.markedAsDoneTaskIds), incompleted = numbers(done.markedAsNotDoneTaskIds);
        final String list = link(links, done.checklistMessageId, "this todo list");
        if (completed.isEmpty() && !incompleted.isEmpty()) {
          return from + " marked " + incompleted + " as not done yet in " + list + ".";
        }
        if (!completed.isEmpty() && incompleted.isEmpty()) {
          return from + " marked " + completed + " as done in " + list + ".";
        }
        return from + " marked " + completed + " as done and " + incompleted + " as not done yet in " + list + ".";
      }
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR: {
        final TdApi.ChecklistTask[] tasks = ((TdApi.MessageChecklistTasksAdded) content).tasks;
        final StringBuilder b = new StringBuilder(from).append(" added tasks: ");
        if (tasks != null) {
          for (int i = 0; i < tasks.length; i++) {
            if (i > 0) {
              b.append(", ");
            }
            b.append("&quot;").append(ExportText.html(tasks[i].text)).append("&quot;");
          }
        }
        return b.toString();
      }
      case TdApi.MessagePollOptionAdded.CONSTRUCTOR:
        return from + " added &quot;" + ExportHtmlWriter.escape(ExportText.plain(((TdApi.MessagePollOptionAdded) content).text)) + "&quot; to the poll.";
      case TdApi.MessagePollOptionDeleted.CONSTRUCTOR:
        return from + " removed &quot;" + ExportHtmlWriter.escape(ExportText.plain(((TdApi.MessagePollOptionDeleted) content).text)) + "&quot; from the poll.";
      case TdApi.MessageSuggestedPostApproved.CONSTRUCTOR: {
        final TdApi.MessageSuggestedPostApproved approved = (TdApi.MessageSuggestedPostApproved) content;
        final String price = priceText(approved.price);
        return from + " approved your suggested post" + (price.isEmpty() ? "" : ", for " + price) +
          (approved.sendDate != 0 ? ", " + dayText(approved.sendDate) + " at " + timeText(approved.sendDate) : "") + ".";
      }
      case TdApi.MessageSuggestedPostApprovalFailed.CONSTRUCTOR: {
        // Desktop's sentence for the approval (balance_too_low isn't mentioned)
        final String price = priceText(((TdApi.MessageSuggestedPostApprovalFailed) content).price);
        return from + " approved your suggested post" + (price.isEmpty() ? "" : ", for " + price) + ".";
      }
      case TdApi.MessageSuggestedPostDeclined.CONSTRUCTOR: {
        final String comment = ((TdApi.MessageSuggestedPostDeclined) content).comment;
        return from + " rejected your suggested post" +
          (comment == null || comment.isEmpty() ? "." : ", with comment: &quot;" + ExportHtmlWriter.escape(comment) + "&quot;");
      }
      case TdApi.MessageSuggestedPostPaid.CONSTRUCTOR: {
        final TdApi.MessageSuggestedPostPaid paid = (TdApi.MessageSuggestedPostPaid) content;
        final String amount = paid.starAmount != null && paid.starAmount.starCount != 0 ? paid.starAmount.starCount + " stars" :
          gramsNumber(paid.gramAmount) + " TON";
        return "The paid post was shown for 24 hours and " + amount + " were transferred to the channel.";
      }
      case TdApi.MessageSuggestedPostRefunded.CONSTRUCTOR:
        return ((TdApi.MessageSuggestedPostRefunded) content).reason instanceof TdApi.SuggestedPostRefundReasonPaymentRefunded ?
          "The user refunded the payment, post was deleted." :
          "The admin deleted the post early, the payment was refunded.";
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR: {
        final TdApi.Birthdate birthdate = ((TdApi.MessageSuggestBirthdate) content).birthdate;
        if (birthdate == null) {
          return from + " suggests to add a date of birth";
        }
        return from + " suggests to add a date of birth: " + birthdate.day + " " + month(birthdate.month) +
          (birthdate.year != 0 ? " " + birthdate.year : "");
      }
      case TdApi.MessageChatHasProtectedContentToggled.CONSTRUCTOR:
        return from + (((TdApi.MessageChatHasProtectedContentToggled) content).newHasProtectedContent ?
          " disabled sharing in this chat" : " enabled sharing in this chat");
      case TdApi.MessageChatHasProtectedContentDisableRequested.CONSTRUCTOR:
        return from + " requested to enable sharing in this chat";
      case TdApi.MessageChatOwnerLeft.CONSTRUCTOR: {
        final long userId = ((TdApi.MessageChatOwnerLeft) content).newOwnerUserId;
        if (userId == 0) {
          return from + " left the group"; // no one takes over: Desktop has no sentence for that
        }
        return userHtml(peers, userId) + " will become the new main admin in 7 days if " + from + " does not return";
      }
      case TdApi.MessageChatOwnerChanged.CONSTRUCTOR:
        return from + " made " + userHtml(peers, ((TdApi.MessageChatOwnerChanged) content).newOwnerUserId) + " the new main admin of the group";
      case TdApi.MessageManagedBotCreated.CONSTRUCTOR:
        return from + " created a bot " + userHtml(peers, ((TdApi.MessageManagedBotCreated) content).botUserId);
      case TdApi.MessageTonWalletTransfer.CONSTRUCTOR: {
        final TdApi.MessageTonWalletTransfer transfer = (TdApi.MessageTonWalletTransfer) content;
        final String amount = grams(transfer.amount);
        final String address = transfer.peerAddress == null || transfer.peerAddress.isEmpty() ? "" : " (" + ExportHtmlWriter.escape(transfer.peerAddress) + ")";
        // Telegram's service accounts send these: Desktop names no one then
        final boolean hidden = c.dialog.type.equals("personal_chat") && (c.dialog.bareId == 333000 || c.dialog.bareId == 777000);
        final StringBuilder b = new StringBuilder(message.isOutgoing ?
          "You sent " + amount + " to " + ExportHtmlWriter.escape(c.dialog.htmlName) + address :
          (hidden ? "Someone" : from) + address + " sent you " + amount);
        if (transfer.transactionId != null && !transfer.transactionId.isEmpty()) {
          b.append(", transaction ").append(ExportHtmlWriter.escape(transfer.transactionId));
        }
        if (transfer.isCommentEncrypted) {
          b.append(", with an <span class=\"gram_transfer_encrypted_comment\" data-encrypted-comment=\"")
            .append(ExportHtmlWriter.escape(transfer.comment)).append("\">encrypted comment</span>");
        } else if (transfer.comment != null && !transfer.comment.isEmpty()) {
          b.append(", with comment: ").append(quoted(transfer.comment));
        }
        return b.toString();
      }
      case TdApi.MessageTonConnectRequest.CONSTRUCTOR: {
        final TdApi.MessageTonConnectRequest request = (TdApi.MessageTonConnectRequest) content;
        final String topic = request.topic == null || request.topic.isEmpty() ? "" : " " + quoted(request.topic);
        return request.state instanceof TdApi.TonConnectRequestStateAccepted ? "You approved a TON Connect request" + topic + "." :
          request.state instanceof TdApi.TonConnectRequestStateRejected ? "You declined a TON Connect request" + topic + "." :
          "You received a TON Connect request" + topic + ".";
      }
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
        return from + " created topic " + quoted(((TdApi.MessageForumTopicCreated) content).name);
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR: {
        final TdApi.MessageForumTopicEdited edited = (TdApi.MessageForumTopicEdited) content;
        final List<String> parts = new ArrayList<>();
        if (edited.name != null && !edited.name.isEmpty()) {
          parts.add("title to " + quoted(edited.name));
        }
        if (edited.editIconCustomEmojiId) {
          parts.add("icon to &laquo;" + edited.iconCustomEmojiId + "&raquo;");
        }
        final StringBuilder b = new StringBuilder(from).append(" changed topic ");
        for (int i = 0; i < parts.size(); i++) {
          b.append(i > 0 ? "," : "").append(parts.get(i)); // sic, Desktop joins them with a bare comma
        }
        return b.toString();
      }
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
        // Desktop writes "changed topic " and nothing else for these
        return from + (((TdApi.MessageForumTopicIsClosedToggled) content).isClosed ? " closed the topic" : " reopened the topic");
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
        return from + (((TdApi.MessageForumTopicIsHiddenToggled) content).isHidden ? " hid the topic" : " unhid the topic");
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
        return from + " suggests to use this photo";
      case TdApi.MessageChatSetBackground.CONSTRUCTOR: {
        final long old = ((TdApi.MessageChatSetBackground) content).oldBackgroundMessageId;
        return from + (old != 0 ? " set " + link(links, old, "the same background") + " for this chat" : " set a new background for this chat");
      }
      case TdApi.MessageChatShared.CONSTRUCTOR: {
        // Desktop writes "requested: " alone (the peer is commented out there); XGram names it
        final TdApi.SharedChat chat = ((TdApi.MessageChatShared) content).chat;
        return "requested: " + (chat != null ? peerHtml(peers.chat(chat.chatId)) : "");
      }
      case TdApi.MessageUsersShared.CONSTRUCTOR: {
        final TdApi.SharedUser[] users = ((TdApi.MessageUsersShared) content).users;
        final long[] ids = new long[users != null ? users.length : 0];
        for (int i = 0; i < ids.length; i++) {
          ids[i] = users[i].userId;
        }
        return "requested: " + usersHtml(peers, ids);
      }
      default:
        return "";
    }
  }

  /** Desktop's call block (prepareMediaData): {classes, title, status}. */
  @NonNull
  @WorkerThread
  static String[] callBlock (Context c) {
    final TdApi.Message message = c.message;
    final boolean out = message.isOutgoing;
    // The other side of the call: the chat for an outgoing call, the account for an incoming one, as Desktop
    final String title = out ? c.peers.chat(c.chatId).name() : c.peers.user(c.tdlib.myUserId()).name();
    final int duration;
    final String state;
    if (message.content.getConstructor() == TdApi.MessageGroupCall.CONSTRUCTOR) {
      final TdApi.MessageGroupCall call = (TdApi.MessageGroupCall) message.content;
      duration = call.duration;
      state = call.wasMissed ? "missed" : call.isActive ? "active" : call.duration > 0 ? "hangup" : "invitation";
    } else {
      final TdApi.MessageCall call = (TdApi.MessageCall) message.content;
      duration = call.duration;
      state = discardReason(call.discardReason);
    }
    String status;
    switch (state) {
      case "invitation":
        status = "Invitation";
        break;
      case "active":
        status = "Ongoing";
        break;
      default:
        if (out) {
          status = state.equals("missed") ? "Cancelled" : "Outgoing";
        } else {
          status = state.equals("missed") ? "Missed" : state.equals("busy") ? "Declined" : "Incoming";
        }
        break;
    }
    String classes = "media_call";
    if (duration > 0) {
      classes += " success";
      status += " (" + duration + " seconds)";
    }
    return new String[] {classes, title, status};
  }

  // Shared helpers

  private static final long NANOS_IN_GRAM = 1_000_000_000L;

  @NonNull
  private static String nonNull (@Nullable String value) {
    return value != null ? value : "";
  }

  /** Desktop's PeersMap::wrapPeerName: the escaped name, "Deleted" for a deleted account. */
  private static String peerHtml (ExportPeers.Peer peer) {
    final String name = peer.name();
    return name.isEmpty() ? "Deleted" : ExportHtmlWriter.escape(name);
  }

  /** Desktop's PeersMap::wrapUserName: the escaped name, "Deleted Account" for a deleted account. */
  private static String userHtml (ExportPeers peers, long userId) {
    final String name = peers.user(userId).name();
    return name.isEmpty() ? ExportPeers.DELETED_ACCOUNT : ExportHtmlWriter.escape(name);
  }

  private static String usersHtml (ExportPeers peers, @Nullable long[] userIds) {
    final List<String> names = new ArrayList<>();
    if (userIds != null) {
      for (long userId : userIds) {
        names.add(userHtml(peers, userId));
      }
    }
    return list(names);
  }

  /** Desktop's SerializeList: "Alice", "Alice and Bob", "Alice, Bob and Carol". */
  private static String list (List<String> values) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        b.append(i == values.size() - 1 ? " and " : ", ");
      }
      b.append(values.get(i));
    }
    return b.toString();
  }

  private static String numbers (@Nullable int[] values) {
    final List<String> list = new ArrayList<>();
    if (values != null) {
      for (int value : values) {
        list.add(String.valueOf(value));
      }
    }
    return list(list);
  }

  private static String quoted (@Nullable String text) {
    return "&laquo;" + ExportHtmlWriter.escape(text) + "&raquo;";
  }

  private static String link (Links links, long messageId, String html) {
    return messageId != 0 && MessageId.toServerMessageId(messageId) != 0 ? links.link(messageId, html) : html;
  }

  /** Desktop's FormatMoneyAmount: Stars as "⭐" and the count, other currencies as Telegram formats them. */
  @NonNull
  static String money (@Nullable String currency, long amount) {
    if (currency == null || currency.isEmpty()) {
      return String.valueOf(amount);
    }
    if ("XTR".equals(currency)) {
      return "⭐" + String.format(Locale.US, "%,d", Math.abs(amount));
    }
    try {
      return CurrencyUtils.buildAmount(currency, amount);
    } catch (Throwable t) {
      return amount + " " + currency;
    }
  }

  /** Desktop's "cost" (FillAmountAndCurrency), "" without a currency. */
  @NonNull
  private static String cost (@Nullable String currency, long amount) {
    return currency == null || currency.isEmpty() ? "" : money(currency, amount);
  }

  /** Desktop's FormatGramsAmount: "1.5 Grams", "1 Gram". */
  private static String grams (long nanos) {
    return gramsNumber(nanos) + (Math.abs(nanos) == NANOS_IN_GRAM ? " Gram" : " Grams");
  }

  private static String gramsNumber (long nanos) {
    return BigDecimal.valueOf(nanos, 9).stripTrailingZeros().toPlainString();
  }

  private static String priceText (@Nullable TdApi.SuggestedPostPrice price) {
    if (price instanceof TdApi.SuggestedPostPriceStar && ((TdApi.SuggestedPostPriceStar) price).starCount != 0) {
      return ((TdApi.SuggestedPostPriceStar) price).starCount + " stars";
    }
    if (price instanceof TdApi.SuggestedPostPriceGram && ((TdApi.SuggestedPostPriceGram) price).gramCentCount != 0) {
      return BigDecimal.valueOf(((TdApi.SuggestedPostPriceGram) price).gramCentCount, 2).stripTrailingZeros().toPlainString() + " TON";
    }
    return "";
  }

  /** Desktop's auto-delete periods: its own texts for a day and a week, the plain length otherwise. */
  private static String ttl (int seconds) {
    if (seconds == 86400) {
      return "24 hours";
    }
    if (seconds == 7 * 86400) {
      return "7 days";
    }
    if (seconds % (365 * 86400) == 0) {
      return plural(seconds / (365 * 86400), "year");
    }
    if (seconds % (30 * 86400) == 0) {
      return plural(seconds / (30 * 86400), "month");
    }
    if (seconds % 86400 == 0) {
      return plural(seconds / 86400, "day");
    }
    if (seconds % 3600 == 0) {
      return plural(seconds / 3600, "hour");
    }
    if (seconds % 60 == 0) {
      return plural(seconds / 60, "minute");
    }
    return plural(seconds, "second");
  }

  private static String plural (int count, String unit) {
    return count + " " + unit + (count == 1 ? "" : "s");
  }

  /** Desktop's FormatDateTime without the time zone: "01.11.2026 12:00:00". */
  private static String dateTime (int date) {
    return date == 0 ? "" : new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(new Date(date * 1000L));
  }

  /** Desktop's FormatDateText: "1 November 2026". */
  private static String dayText (int date) {
    return new SimpleDateFormat("d MMMM yyyy", Locale.US).format(new Date(date * 1000L));
  }

  /** Desktop's FormatTimeText: "12:00". */
  private static String timeText (int date) {
    return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(date * 1000L));
  }

  private static String month (int month) {
    final String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    return month >= 1 && month <= 12 ? months[month - 1] : "";
  }

  /** Desktop's names of Telegram Passport documents: the JSON value, or the HTML text. */
  private static String passportType (TdApi.PassportElementType type, boolean json) {
    switch (type.getConstructor()) {
      case TdApi.PassportElementTypePersonalDetails.CONSTRUCTOR:
        return json ? "personal_details" : "Personal details";
      case TdApi.PassportElementTypePassport.CONSTRUCTOR:
        return json ? "passport" : "Passport";
      case TdApi.PassportElementTypeDriverLicense.CONSTRUCTOR:
        return json ? "driver_license" : "Driver license";
      case TdApi.PassportElementTypeIdentityCard.CONSTRUCTOR:
        return json ? "identity_card" : "Identity card";
      case TdApi.PassportElementTypeInternalPassport.CONSTRUCTOR:
        return json ? "internal_passport" : "Internal passport";
      case TdApi.PassportElementTypeAddress.CONSTRUCTOR:
        return json ? "address_information" : "Address information";
      case TdApi.PassportElementTypeUtilityBill.CONSTRUCTOR:
        return json ? "utility_bill" : "Utility bill";
      case TdApi.PassportElementTypeBankStatement.CONSTRUCTOR:
        return json ? "bank_statement" : "Bank statement";
      case TdApi.PassportElementTypeRentalAgreement.CONSTRUCTOR:
        return json ? "rental_agreement" : "Rental agreement";
      case TdApi.PassportElementTypePassportRegistration.CONSTRUCTOR:
        return json ? "passport_registration" : "Passport registration";
      case TdApi.PassportElementTypeTemporaryRegistration.CONSTRUCTOR:
        return json ? "temporary_registration" : "Temporary registration";
      case TdApi.PassportElementTypePhoneNumber.CONSTRUCTOR:
        return json ? "phone_number" : "Phone number";
      case TdApi.PassportElementTypeEmailAddress.CONSTRUCTOR:
        return json ? "email" : "Email";
      default:
        return "";
    }
  }

  /** Desktop's distance: "1 meter", "250 meters", "1.25 km" (whole tens of meters). */
  private static String distance (int meters) {
    if (meters >= 1000) {
      return BigDecimal.valueOf(10L * (meters / 10), 3).stripTrailingZeros().toPlainString() + " km";
    }
    return meters == 1 ? "1 meter" : meters + " meters";
  }
}
