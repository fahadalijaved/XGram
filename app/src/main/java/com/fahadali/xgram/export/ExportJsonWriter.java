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

import android.util.Base64;
import android.util.JsonWriter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tgx.td.MessageId;

/**
 * result.json in Telegram Desktop's format (export_output_json.cpp), streamed into a spool file that goes into the
 * .zip last: one-space indents, keys in Desktop's order, empty keys left out, "text" and "text_entities" always there
 * (formatted as Desktop flattens them, {@link ExportText}), local ISO dates without an offset and "*_unixtime" as
 * strings, peer ids as "user…", "chat…", "channel…". Service messages carry Desktop's "action" and its fields
 * ({@link ExportActions}); polls, contacts, locations, venues, games, invoices, to-do lists, giveaways and paid media
 * their own keys; inline keyboards and reactions follow the text. XGram adds a few keys Desktop's parsers skip:
 * "topic_id" for messages of a forum topic (whole-forum exports), "dice_emoji" / "dice_value" and "story_id" /
 * "story_from" / "story_from_id", which Desktop doesn't export at all.
 */
final class ExportJsonWriter implements Closeable {
  private final Writer out;
  private final JsonWriter json;
  private final Tdlib tdlib;
  private final ExportPeers peers;
  private final ExportPeers.Dialog dialog;
  private final long chatId;
  /** A whole-chat export: messages of a forum topic say which one ("topic_id"). */
  private final boolean topicIds;
  private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
  private boolean finished;

  ExportJsonWriter (File spool, Tdlib tdlib, ExportPeers peers, ExportPeers.Dialog dialog, long chatId, boolean topicIds) throws IOException {
    this.out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(spool), StandardCharsets.UTF_8), 64 * 1024);
    this.json = new JsonWriter(out);
    this.json.setIndent(" ");
    this.tdlib = tdlib;
    this.peers = peers;
    this.dialog = dialog;
    this.chatId = chatId;
    this.topicIds = topicIds;
  }

  /**
   * The chat's keys (writeDialogStart) and the start of "messages". A topic export adds "topic_id" and "topic_title"
   * (XGram's keys: Desktop's parsers skip keys they don't know).
   */
  void begin (ExportPeers.Dialog dialog, long topicId, String topicTitle) throws IOException {
    json.beginObject();
    if (!dialog.omitName) {
      json.name("name");
      if (dialog.jsonName != null) {
        json.value(dialog.jsonName);
      } else {
        json.nullValue();
      }
    }
    json.name("type").value(dialog.type);
    json.name("id").value(dialog.bareId);
    if (topicId != 0) {
      json.name("topic_id").value(topicId);
      if (!topicTitle.isEmpty()) {
        json.name("topic_title").value(topicTitle);
      }
    }
    json.name("messages");
    json.beginArray();
  }

  @WorkerThread
  void writeItems (List<ExportJob.Item> items) throws IOException {
    for (ExportJob.Item item : items) {
      writeItem(item);
    }
  }

  private void writeItem (ExportJob.Item item) throws IOException {
    final TdApi.Message message = item.message;
    json.beginObject();
    json.name("id").value(item.serverId);
    if (item.kind == ExportJob.Item.KIND_UNSUPPORTED) {
      json.name("type").value("unsupported");
      json.endObject();
      return;
    }
    final boolean service = item.kind == ExportJob.Item.KIND_SERVICE;
    json.name("type").value(service ? "service" : "message");
    json.name("date").value(isoDate.format(new Date(message.date * 1000L)));
    json.name("date_unixtime").value(String.valueOf(message.date));
    if (message.editDate != 0) {
      json.name("edited").value(isoDate.format(new Date(message.editDate * 1000L)));
      json.name("edited_unixtime").value(String.valueOf(message.editDate));
    }
    if (service) {
      if (ExportActions.hasAction(message.content)) {
        ExportActions.writeJson(json, new ExportActions.Context(tdlib, peers, dialog, chatId, message));
        writeMedia(item.media); // the photo of edit_group_photo and suggest_profile_photo
      } else {
        // As Desktop writes a custom action: the actor and a line
        writePeer("actor", peers.sender(message.senderId));
        if (!item.fallback.isEmpty()) {
          json.name("information_text").value(item.fallback);
        }
      }
      writeTopic(message);
      ExportText.writeMessageText(json, null); // "" and [], as Desktop writes them for service messages too
    } else {
      writePeer("from", peers.sender(message.senderId));
      if (message.authorSignature != null && !message.authorSignature.isEmpty()) {
        json.name("author").value(message.authorSignature);
      }
      writeForward(message.forwardInfo);
      writeReply(message);
      writeTopic(message);
      if (message.viaBotUserId != 0) {
        final ExportPeers.Peer bot = peers.user(message.viaBotUserId);
        if (!bot.username.isEmpty()) {
          json.name("via_bot").value("@" + bot.username);
        }
      }
      writeMedia(item.media);
      writeContent(item);
      ExportText.writeMessageText(json, item.text);
      writeButtons(message.replyMarkup);
      writeReactions(message);
    }
    json.endObject();
  }

  /** The forum topic of a message (TDLib's id is the server id of the topic's first message), 0 for none. */
  static int forumTopicId (TdApi.Message message) {
    return message.topicId instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) message.topicId).forumTopicId : 0;
  }

  /** XGram's "topic_id" for a message of a forum topic other than General, in a whole-chat export. */
  private void writeTopic (TdApi.Message message) throws IOException {
    final int topicId = forumTopicId(message);
    if (topicIds && topicId > GENERAL_TOPIC_ID) {
      json.name("topic_id").value(topicId);
    }
  }

  /** TDLib's id of a forum's General topic (ForumTopicId::general()). */
  static final int GENERAL_TOPIC_ID = 1;

  /**
   * Desktop's keys for the contents that are not files (SerializeMessage's media part): contact_information and the
   * vCard, location_information, venues, games, invoice_information, poll, todo_list, giveaways, paid_stars_amount;
   * XGram's keys for dice and stories, and Desktop's "unavailable" file for expired self-destructing media.
   */
  private void writeContent (ExportJob.Item item) throws IOException {
    final TdApi.Message message = item.message;
    final TdApi.MessageContent content = message.content;
    switch (content.getConstructor()) {
      case TdApi.MessageContact.CONSTRUCTOR: {
        final TdApi.Contact contact = ((TdApi.MessageContact) content).contact;
        if (contact == null) {
          break;
        }
        json.name("contact_information");
        json.beginObject();
        json.name("first_name").value(nonNull(contact.firstName));
        json.name("last_name").value(nonNull(contact.lastName));
        json.name("phone_number").value(phone(contact.phoneNumber));
        json.endObject();
        if (contact.vcard != null && !contact.vcard.isEmpty()) {
          writePath("contact_vcard", item.vcardPath, item.vcardSkip);
          json.name("contact_vcard_file_size").value(contact.vcard.getBytes(StandardCharsets.UTF_8).length);
        }
        break;
      }
      case TdApi.MessageLocation.CONSTRUCTOR:
        json.name("location_information");
        writeLocation(((TdApi.MessageLocation) content).location);
        break;
      case TdApi.MessageLiveLocation.CONSTRUCTOR: {
        final TdApi.LiveLocation live = ((TdApi.MessageLiveLocation) content).location;
        json.name("location_information");
        writeLocation(live != null ? live.location : null);
        if (live != null && live.livePeriod != 0) {
          json.name("live_location_period_seconds").value(live.livePeriod);
        }
        break;
      }
      case TdApi.MessageVenue.CONSTRUCTOR: {
        final TdApi.Venue venue = ((TdApi.MessageVenue) content).venue;
        if (venue == null) {
          break;
        }
        string("place_name", venue.title);
        string("address", venue.address);
        if (isValid(venue.location)) {
          json.name("location_information");
          writeLocation(venue.location);
        }
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        final TdApi.Game game = ((TdApi.MessageGame) content).game;
        if (game == null) {
          break;
        }
        string("game_title", game.title);
        string("game_description", game.description);
        string("game_link", gameLink(peers, message, game));
        break;
      }
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        // A string holding the object's JSON, as Desktop pushes it (push(key, SerializeObject(...))), so that parsers of
        // Desktop's result.json read it the same way
        final TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) content;
        final StringWriter text = new StringWriter();
        final JsonWriter object = new JsonWriter(text);
        object.setIndent(" ");
        object.beginObject();
        object.name("title").value(invoice.productInfo != null ? nonNull(invoice.productInfo.title) : "");
        object.name("description").value(invoice.productInfo != null ? ExportText.plain(invoice.productInfo.description) : "");
        object.name("amount").value(invoice.totalAmount);
        object.name("currency").value(nonNull(invoice.currency));
        final long receipt = invoice.receiptMessageId != 0 ? MessageId.toServerMessageId(invoice.receiptMessageId) : 0;
        if (receipt != 0) {
          object.name("receipt_message_id").value(receipt);
        }
        object.endObject();
        object.flush();
        json.name("invoice_information").value(text.toString());
        break;
      }
      case TdApi.MessagePoll.CONSTRUCTOR: {
        final TdApi.Poll poll = ((TdApi.MessagePoll) content).poll;
        if (poll == null) {
          break;
        }
        json.name("poll");
        json.beginObject();
        json.name("question");
        ExportText.writeValue(json, poll.question);
        json.name("closed").value(poll.isClosed);
        json.name("total_voters").value(poll.totalVoterCount);
        json.name("answers");
        json.beginArray();
        if (poll.options != null) {
          for (TdApi.PollOption option : poll.options) {
            json.beginObject();
            json.name("text");
            ExportText.writeValue(json, option.text);
            json.name("voters").value(option.voterCount);
            json.name("chosen").value(option.isChosen);
            json.endObject();
          }
        }
        json.endArray();
        json.endObject();
        break;
      }
      case TdApi.MessageChecklist.CONSTRUCTOR: {
        final TdApi.Checklist list = ((TdApi.MessageChecklist) content).list;
        if (list == null) {
          break;
        }
        json.name("todo_list");
        json.beginObject();
        json.name("title");
        ExportText.writeValue(json, list.title);
        json.name("others_can_append").value(list.othersCanAddTasks);
        json.name("others_can_complete").value(list.othersCanMarkTasksAsDone);
        json.name("answers"); // sic, Desktop's key for the tasks
        ExportActions.tasks(json, list.tasks);
        json.endObject();
        break;
      }
      case TdApi.MessageGiveaway.CONSTRUCTOR: {
        final TdApi.MessageGiveaway giveaway = (TdApi.MessageGiveaway) content;
        final TdApi.GiveawayParameters parameters = giveaway.parameters;
        json.name("giveaway_information");
        json.beginObject();
        json.name("quantity").value(giveaway.winnerCount);
        json.name("months").value(giveaway.prize instanceof TdApi.GiveawayPrizePremium ? ((TdApi.GiveawayPrizePremium) giveaway.prize).monthCount : 0);
        json.name("until_date").value(parameters != null ? isoDate.format(new Date(parameters.winnersSelectionDate * 1000L)) : "");
        json.name("channels");
        json.beginArray();
        if (parameters != null) {
          if (parameters.boostedChatId != 0) {
            json.value(ExportPeers.bareIdOf(parameters.boostedChatId));
          }
          if (parameters.additionalChatIds != null) {
            for (long chatId : parameters.additionalChatIds) {
              json.value(ExportPeers.bareIdOf(chatId));
            }
          }
        }
        json.endArray();
        json.name("countries");
        json.beginArray();
        if (parameters != null && parameters.countryCodes != null) {
          for (String code : parameters.countryCodes) {
            json.value(code);
          }
        }
        json.endArray();
        json.name("additional_prize").value(parameters != null ? nonNull(parameters.prizeDescription) : "");
        json.name("stars").value(giveaway.prize instanceof TdApi.GiveawayPrizeStars ? ((TdApi.GiveawayPrizeStars) giveaway.prize).starCount : 0);
        json.name("is_only_new_subscribers").value(parameters != null && parameters.onlyNewMembers);
        json.endObject();
        break;
      }
      case TdApi.MessageGiveawayWinners.CONSTRUCTOR: {
        final TdApi.MessageGiveawayWinners winners = (TdApi.MessageGiveawayWinners) content;
        json.name("giveaway_results");
        json.beginObject();
        json.name("channel").value(ExportPeers.bareIdOf(winners.boostedChatId));
        json.name("winners");
        json.beginArray();
        if (winners.winnerUserIds != null) {
          for (long userId : winners.winnerUserIds) {
            json.value(userId);
          }
        }
        json.endArray();
        json.name("additional_prize").value(nonNull(winners.prizeDescription));
        json.name("until_date").value(isoDate.format(new Date(winners.actualWinnersSelectionDate * 1000L)));
        json.name("launch_message_id").value(MessageId.toServerMessageId(winners.giveawayMessageId));
        json.name("additional_peers_count").value(winners.additionalChatCount);
        json.name("winners_count").value(winners.winnerCount);
        json.name("unclaimed_count").value(winners.unclaimedPrizeCount);
        json.name("months").value(winners.prize instanceof TdApi.GiveawayPrizePremium ? ((TdApi.GiveawayPrizePremium) winners.prize).monthCount : 0);
        json.name("stars").value(winners.prize instanceof TdApi.GiveawayPrizeStars ? ((TdApi.GiveawayPrizeStars) winners.prize).starCount : 0);
        json.name("is_refunded").value(winners.wasRefunded);
        json.name("is_only_new_subscribers").value(winners.onlyNewMembers);
        json.endObject();
        break;
      }
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        json.name("paid_stars_amount").value(((TdApi.MessagePaidMedia) content).starCount);
        break;
      case TdApi.MessageDice.CONSTRUCTOR: {
        final TdApi.MessageDice dice = (TdApi.MessageDice) content;
        string("dice_emoji", dice.emoji);
        json.name("dice_value").value(dice.value);
        break;
      }
      case TdApi.MessageStakeDice.CONSTRUCTOR:
        json.name("dice_emoji").value(DICE);
        json.name("dice_value").value(((TdApi.MessageStakeDice) content).value);
        break;
      case TdApi.MessageStory.CONSTRUCTOR: {
        final TdApi.MessageStory story = (TdApi.MessageStory) content;
        json.name("story_id").value(story.storyId);
        if (story.storyPosterChatId != 0) {
          final ExportPeers.Peer poster = peers.chat(story.storyPosterChatId);
          ExportActions.peerName(json, "story_from", poster);
          json.name("story_from_id").value(poster.peerId);
        }
        break;
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
        writePath("photo", null, ExportMedia.SKIP_UNAVAILABLE);
        json.name("photo_file_size").value(0);
        break;
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
      case TdApi.MessageExpiredVideoNote.CONSTRUCTOR:
      case TdApi.MessageExpiredVoiceNote.CONSTRUCTOR:
        writePath("file", null, ExportMedia.SKIP_UNAVAILABLE);
        json.name("file_size").value(0);
        json.name("media_type").value(content.getConstructor() == TdApi.MessageExpiredVideo.CONSTRUCTOR ? "video_file" :
          content.getConstructor() == TdApi.MessageExpiredVideoNote.CONSTRUCTOR ? "video_message" : "voice_message");
        break;
      default:
        break;
    }
  }

  /** The emoji of a dice with a stake (TDLib gives none: it is the die). */
  static final String DICE = "🎲";

  /** Desktop's GeoPoint: {latitude, longitude} with 6 decimals, or null when the point is not valid. */
  private void writeLocation (@Nullable TdApi.Location location) throws IOException {
    if (!isValid(location)) {
      json.nullValue();
      return;
    }
    json.beginObject();
    json.name("latitude").value(new BigDecimal(coordinate(location.latitude)));
    json.name("longitude").value(new BigDecimal(coordinate(location.longitude)));
    json.endObject();
  }

  /** Desktop's ValidGeoCoordinates. */
  static boolean isValid (@Nullable TdApi.Location location) {
    return location != null && location.latitude >= -90 && location.latitude <= 90 && location.longitude >= -180 && location.longitude <= 180;
  }

  /** Desktop's NumberToString for a coordinate (std::to_string): 6 decimals. */
  static String coordinate (double value) {
    return String.format(Locale.US, "%f", value);
  }

  /** Desktop's FormatPhoneNumber: "+1 234 567 8900"; a number starting with 0 stays as it is. */
  static String phone (@Nullable String phoneNumber) {
    if (phoneNumber == null || phoneNumber.isEmpty()) {
      return "";
    }
    if (phoneNumber.charAt(0) == '0') {
      return phoneNumber;
    }
    try {
      final String formatted = Strings.formatPhone(phoneNumber);
      return formatted != null && !formatted.isEmpty() ? formatted : phoneNumber;
    } catch (Throwable t) {
      return phoneNumber;
    }
  }

  /**
   * Desktop's game link, "https://t.me/bot?game=name": the bot is the message's via bot, else the forward's sender,
   * else the sender, when it is a bot with a username.
   */
  @NonNull
  @WorkerThread
  static String gameLink (ExportPeers peers, TdApi.Message message, TdApi.Game game) {
    if (game.shortName == null || game.shortName.isEmpty()) {
      return "";
    }
    long botId = message.viaBotUserId;
    if (botId == 0 && message.forwardInfo != null && message.forwardInfo.origin instanceof TdApi.MessageOriginUser) {
      botId = ((TdApi.MessageOriginUser) message.forwardInfo.origin).senderUserId;
    }
    if (botId == 0 && message.forwardInfo == null && message.senderId instanceof TdApi.MessageSenderUser) {
      botId = ((TdApi.MessageSenderUser) message.senderId).userId;
    }
    if (botId == 0) {
      return "";
    }
    final ExportPeers.Peer bot = peers.user(botId);
    return bot.isBot && !bot.username.isEmpty() ? ExportText.LINKS_DOMAIN + bot.username + "?game=" + game.shortName : "";
  }

  /** Desktop's "inline_bot_buttons": rows of {type, text, data (dataBase64 for callbacks), forward_text, button_id}. */
  private void writeButtons (@Nullable TdApi.ReplyMarkup markup) throws IOException {
    if (!(markup instanceof TdApi.ReplyMarkupInlineKeyboard)) {
      return;
    }
    final TdApi.InlineKeyboardButton[][] rows = ((TdApi.ReplyMarkupInlineKeyboard) markup).rows;
    if (rows == null || rows.length == 0) {
      return;
    }
    boolean any = false;
    for (TdApi.InlineKeyboardButton[] row : rows) {
      any |= row != null && row.length > 0;
    }
    if (!any) {
      return;
    }
    json.name("inline_bot_buttons");
    json.beginArray();
    for (TdApi.InlineKeyboardButton[] row : rows) {
      if (row == null || row.length == 0) {
        continue;
      }
      json.beginArray();
      for (TdApi.InlineKeyboardButton button : row) {
        final Button b = Button.of(button);
        json.beginObject();
        json.name("type").value(b.type);
        string("text", button.text);
        if (!b.data.isEmpty()) {
          if (b.callback) {
            json.name("dataBase64").value(b.data);
            json.name("data").value("");
          } else {
            json.name("data").value(b.data);
          }
        }
        string("forward_text", b.forwardText);
        if (b.buttonId != 0) {
          json.name("button_id").value(b.buttonId);
        }
        json.endObject();
      }
      json.endArray();
    }
    json.endArray();
  }

  /** An inline keyboard button in Desktop's terms (HistoryMessageMarkupButton). */
  static final class Button {
    @NonNull
    final String type;
    /** The url, query, user id or text to copy; for callbacks their data in URL-safe Base64 without padding. */
    @NonNull
    final String data;
    /** A callback's data as text, for the page's "Data: …" line. */
    @NonNull
    final String rawData;
    @NonNull
    final String forwardText;
    final long buttonId;
    final boolean callback;

    private Button (@NonNull String type, @Nullable String data, @Nullable String rawData, @Nullable String forwardText, long buttonId, boolean callback) {
      this.type = type;
      this.data = data != null ? data : "";
      this.rawData = rawData != null ? rawData : this.data;
      this.forwardText = forwardText != null ? forwardText : "";
      this.buttonId = buttonId;
      this.callback = callback;
    }

    static Button of (TdApi.InlineKeyboardButton button) {
      final TdApi.InlineKeyboardButtonType type = button.type;
      if (type == null) {
        return new Button("disabled", null, null, null, 0, false);
      }
      switch (type.getConstructor()) {
        case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR:
          return new Button("url", ((TdApi.InlineKeyboardButtonTypeUrl) type).url, null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR: {
          final TdApi.InlineKeyboardButtonTypeLoginUrl login = (TdApi.InlineKeyboardButtonTypeLoginUrl) type;
          return new Button("auth", login.url, null, login.forwardText, login.id, false);
        }
        case TdApi.InlineKeyboardButtonTypeWebApp.CONSTRUCTOR:
          return new Button("web_view", ((TdApi.InlineKeyboardButtonTypeWebApp) type).url, null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR:
          return callback("callback", ((TdApi.InlineKeyboardButtonTypeCallback) type).data);
        case TdApi.InlineKeyboardButtonTypeCallbackWithPassword.CONSTRUCTOR:
          return callback("callback_with_password", ((TdApi.InlineKeyboardButtonTypeCallbackWithPassword) type).data);
        case TdApi.InlineKeyboardButtonTypeCallbackGame.CONSTRUCTOR:
          return new Button("game", null, null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR: {
          final TdApi.InlineKeyboardButtonTypeSwitchInline inline = (TdApi.InlineKeyboardButtonTypeSwitchInline) type;
          return new Button(inline.targetChat instanceof TdApi.TargetChatCurrent ? "switch_inline_same" : "switch_inline", inline.query, null, null, 0, false);
        }
        case TdApi.InlineKeyboardButtonTypeBuy.CONSTRUCTOR:
          return new Button("buy", null, null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeUser.CONSTRUCTOR:
          return new Button("user_profile", String.valueOf(((TdApi.InlineKeyboardButtonTypeUser) type).userId), null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeCopyText.CONSTRUCTOR:
          return new Button("copy_text", ((TdApi.InlineKeyboardButtonTypeCopyText) type).text, null, null, 0, false);
        case TdApi.InlineKeyboardButtonTypeDisabled.CONSTRUCTOR:
        default:
          return new Button("disabled", null, null, null, 0, false);
      }
    }

    private static Button callback (String type, @Nullable byte[] data) {
      if (data == null || data.length == 0) {
        return new Button(type, null, null, null, 0, true);
      }
      final String base64 = Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
      return new Button(type, base64, new String(data, StandardCharsets.UTF_8), null, 0, true);
    }
  }

  /**
   * Desktop's "reactions": {type (emoji, custom_emoji, paid), count, emoji or document_id ("": custom emoji are not
   * downloaded), recent: [{from, from_id}]}. TDLib doesn't give the dates of the recent reactions Desktop adds.
   */
  private void writeReactions (TdApi.Message message) throws IOException {
    final TdApi.MessageReaction[] reactions = reactionsOf(message);
    if (reactions.length == 0) {
      return;
    }
    json.name("reactions");
    json.beginArray();
    for (TdApi.MessageReaction reaction : reactions) {
      json.beginObject();
      json.name("type").value(reactionType(reaction.type));
      json.name("count").value(reaction.totalCount);
      if (reaction.type instanceof TdApi.ReactionTypeEmoji) {
        json.name("emoji").value(nonNull(((TdApi.ReactionTypeEmoji) reaction.type).emoji));
      } else if (reaction.type instanceof TdApi.ReactionTypeCustomEmoji) {
        json.name("document_id").value("");
      }
      if (reaction.recentSenderIds != null && reaction.recentSenderIds.length > 0) {
        json.name("recent");
        json.beginArray();
        for (TdApi.MessageSender sender : reaction.recentSenderIds) {
          final ExportPeers.Peer peer = peers.sender(sender);
          json.beginObject();
          ExportActions.peerName(json, "from", peer);
          json.name("from_id").value(peer.peerId);
          json.endObject();
        }
        json.endArray();
      }
      json.endObject();
    }
    json.endArray();
  }

  @NonNull
  static TdApi.MessageReaction[] reactionsOf (TdApi.Message message) {
    final TdApi.MessageInteractionInfo info = message.interactionInfo;
    if (info == null || info.reactions == null || info.reactions.reactions == null) {
      return new TdApi.MessageReaction[0];
    }
    return info.reactions.reactions;
  }

  static String reactionType (@Nullable TdApi.ReactionType type) {
    if (type instanceof TdApi.ReactionTypeEmoji) {
      return "emoji";
    }
    if (type instanceof TdApi.ReactionTypeCustomEmoji) {
      return "custom_emoji";
    }
    if (type instanceof TdApi.ReactionTypePaid) {
      return "paid";
    }
    return "empty";
  }

  /** Desktop's push for strings: an empty one is left out. */
  private void string (String key, @Nullable String value) throws IOException {
    if (value != null && !value.isEmpty()) {
      json.name(key).value(value);
    }
  }

  @NonNull
  private static String nonNull (@Nullable String value) {
    return value != null ? value : "";
  }

  /**
   * Desktop's media keys (SerializeMessage): the file's path in the export or why it is not there, the size (also of a
   * file that was left out), and what it is. Photos: photo, photo_file_size, width, height. Documents: file,
   * file_name, file_size, thumbnail, thumbnail_file_size, media_type (with sticker_emoji, or performer and title),
   * mime_type, duration_seconds, width, height. Then media_spoiler and self_destruct_period_seconds.
   */
  private void writeMedia (@Nullable ExportMedia.Result media) throws IOException {
    if (media == null) {
      return;
    }
    final ExportMedia.Plan plan = media.plan;
    if (plan.kind == ExportMedia.KIND_PHOTO || plan.kind == ExportMedia.KIND_CHAT_PHOTO) {
      writePath("photo", media.path, media.skip);
      json.name("photo_file_size").value(plan.selfDestructing ? 0 : plan.size); // Desktop clears self-destructing files
      if (plan.width > 0 && plan.height > 0) {
        json.name("width").value(plan.width);
        json.name("height").value(plan.height);
      }
    } else {
      writePath("file", media.path, media.skip);
      if (!plan.fileName.isEmpty()) {
        json.name("file_name").value(plan.fileName);
      }
      json.name("file_size").value(plan.selfDestructing ? 0 : plan.size);
      if (media.hasThumbnail()) {
        writePath("thumbnail", media.thumbPath, media.thumbSkip);
        json.name("thumbnail_file_size").value(media.thumbnailSize());
      }
      switch (plan.kind) {
        case ExportMedia.KIND_STICKER:
          json.name("media_type").value("sticker");
          if (!plan.emoji.isEmpty()) {
            json.name("sticker_emoji").value(plan.emoji);
          }
          break;
        case ExportMedia.KIND_VIDEO_NOTE:
          json.name("media_type").value("video_message");
          break;
        case ExportMedia.KIND_VOICE:
          json.name("media_type").value("voice_message");
          break;
        case ExportMedia.KIND_ANIMATION:
          json.name("media_type").value("animation");
          break;
        case ExportMedia.KIND_VIDEO:
          json.name("media_type").value("video_file");
          break;
        case ExportMedia.KIND_AUDIO:
          json.name("media_type").value("audio_file");
          if (!plan.performer.isEmpty()) {
            json.name("performer").value(plan.performer);
          }
          if (!plan.title.isEmpty()) {
            json.name("title").value(plan.title);
          }
          break;
        default:
          break; // a plain file has no media_type
      }
      if (!plan.mimeType.isEmpty()) {
        json.name("mime_type").value(plan.mimeType);
      }
      if (plan.duration > 0) {
        json.name("duration_seconds").value(plan.duration);
      }
      if (plan.width > 0 && plan.height > 0) {
        json.name("width").value(plan.width);
        json.name("height").value(plan.height);
      }
    }
    if (plan.spoiler) {
      json.name("media_spoiler").value(true);
    }
    if (plan.selfDestructSeconds > 0) {
      json.name("self_destruct_period_seconds").value(plan.selfDestructSeconds);
    }
  }

  /** The path in the export, or Desktop's text for a file that is not there. */
  private void writePath (String key, @Nullable String path, int skip) throws IOException {
    final String value;
    if (path != null) {
      value = path;
    } else if (skip == ExportMedia.SKIP_TYPE) {
      value = "(File not included. Change data exporting settings to download.)";
    } else if (skip == ExportMedia.SKIP_SIZE) {
      value = "(File exceeds maximum size. Change data exporting settings to download.)";
    } else {
      value = "(File unavailable, please try again later)";
    }
    json.name(key).value(value);
  }

  /** "from"/"actor" with its "_id": the name is null for a deleted account, as Desktop's StringAllowNull. */
  private void writePeer (String key, ExportPeers.Peer peer) throws IOException {
    writePeerName(key, peer);
    json.name(key + "_id").value(peer.peerId);
  }

  private void writePeerName (String key, ExportPeers.Peer peer) throws IOException {
    ExportActions.peerName(json, key, peer);
  }

  /**
   * "forwarded_from" (a hidden user's name, or the sender's real name with "forwarded_from_id"), then "saved_from":
   * the chat a Saved Messages (or Replies, or discussion group) copy came from, as Desktop's saved_from_peer.
   */
  private void writeForward (@Nullable TdApi.MessageForwardInfo forward) throws IOException {
    if (forward == null) {
      return;
    }
    if (forward.origin != null) {
      final ExportPeers.Peer origin = peers.origin(forward.origin);
      if (origin != null) {
        writePeer("forwarded_from", origin);
      } else if (forward.origin instanceof TdApi.MessageOriginHiddenUser) {
        final String name = ((TdApi.MessageOriginHiddenUser) forward.origin).senderName;
        if (name != null && !name.isEmpty()) {
          json.name("forwarded_from").value(name);
        }
      }
    }
    final long savedFrom = savedFromChatId(forward);
    if (savedFrom != 0) {
      writePeerName("saved_from", peers.chat(savedFrom));
    }
  }

  /** Desktop's savedFromChatId: the source chat of a copy in Saved Messages (and the like), 0 for none. */
  static long savedFromChatId (@Nullable TdApi.MessageForwardInfo forward) {
    return forward != null && forward.source != null ? forward.source.chatId : 0;
  }

  /**
   * "reply_to_message_id", plus "reply_to_peer_id" for a reply to another chat. A forum message that replies to
   * nothing gets its topic's first message, as the server's reply header has it and so Desktop's JSON.
   */
  private void writeReply (TdApi.Message message) throws IOException {
    if (message.replyTo instanceof TdApi.MessageReplyToMessage) {
      final TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
      final long serverId = reply.messageId != 0 ? MessageId.toServerMessageId(reply.messageId) : 0;
      if (serverId != 0) {
        json.name("reply_to_message_id").value(serverId);
        if (reply.chatId != 0 && reply.chatId != chatId) {
          json.name("reply_to_peer_id").value(ExportPeers.peerIdOf(reply.chatId));
        }
        return;
      }
    }
    final int topicId = forumTopicId(message);
    if (topicId > GENERAL_TOPIC_ID && topicId != MessageId.toServerMessageId(message.id)) {
      json.name("reply_to_message_id").value(topicId);
    }
  }

  /** Ends "messages" and the object, and closes the spool. */
  void finish () throws IOException {
    json.endArray();
    json.endObject();
    json.flush();
    finished = true;
    out.close();
  }

  /** On abort: closes the spool as it is (JsonWriter.close() would complain about the unfinished document). */
  @Override
  public void close () {
    if (!finished) {
      try {
        out.close();
      } catch (IOException ignored) { }
    }
  }
}
