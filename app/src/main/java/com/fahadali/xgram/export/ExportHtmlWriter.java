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
import org.thunderdog.challegram.telegram.Tdlib;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import tgx.td.MessageId;

/**
 * The messages*.html pages in Telegram Desktop's markup (export_output_html.cpp), so Desktop's style.css and script.js
 * work on them unchanged: 1000 messages per page with Previous / Next links, a date line whenever the day changes (and
 * at the top of every page), messages from the same sender joined as Desktop joins them, replies linking to the page
 * that holds the message, formatted text ({@link ExportText}), Desktop's service sentences ({@link ExportActions}),
 * forwards (a Saved Messages copy shows its original author, as Desktop does), polls, contacts, locations, venues,
 * games, invoices, calls, to-do lists, giveaways, inline keyboards and reactions. In a whole-forum export a message
 * says "In topic «Name»" when the topic changes. Desktop's English page texts are kept as they are.
 */
final class ExportHtmlWriter {
  private static final int JOIN_WITHIN_SECONDS = 900; // kJoinWithinSeconds
  private static final int USERPIC_SIZE = 42; // kHistoryUserpicSize
  private static final int SERVICE_PHOTO_SIZE = 60; // kServiceMessagePhotoSize
  private static final int REACTION_USERPIC_SIZE = 20;
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final ExportZip zip;
  private final String root;
  private final Tdlib tdlib;
  private final ExportPeers peers;
  private final ExportIndex index;
  private final ExportPeers.Dialog dialog;
  /** The page header: the chat's name, and the topic's for a topic export. */
  private final String headerName;
  private final long chatId;
  /** A whole-chat export: messages of a forum say which topic they are in when it changes. */
  private final boolean topicChips;
  private final long entryTimeMs;
  /** The forum topic the last message on this page was in (its "In topic" line), 0 for none. */
  private int pageTopicId;

  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
  private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
  private final SimpleDateFormat dayFormat = new SimpleDateFormat("d MMMM yyyy", Locale.US);
  private final Calendar calendar = Calendar.getInstance();

  @Nullable
  private Writer w;
  private int depth;
  /** Desktop's _dateMessageId: date lines are "message-1", "message-2", … through the whole chat. */
  private int dateMessageId;

  ExportHtmlWriter (ExportZip zip, String root, Tdlib tdlib, ExportPeers peers, ExportIndex index, ExportPeers.Dialog dialog, @NonNull String topicTitle, long chatId, boolean topicChips, long entryTimeMs) {
    this.zip = zip;
    this.root = root;
    this.tdlib = tdlib;
    this.peers = peers;
    this.index = index;
    this.dialog = dialog;
    this.headerName = topicTitle.isEmpty() ? dialog.htmlName : dialog.htmlName + " · " + topicTitle;
    this.chatId = chatId;
    this.topicChips = topicChips;
    this.entryTimeMs = entryTimeMs;
  }

  /** Desktop's messagesFile(): messages.html, messages2.html, … */
  static String messagesFile (int page) {
    return "messages" + (page > 0 ? String.valueOf(page + 1) : "") + ".html";
  }

  @WorkerThread
  void writePage (int page, int pageCount, List<ExportJob.Item> items) throws IOException {
    beginFile(page);
    Info previous = null; // the first message of every page shows its full header
    for (ExportJob.Item item : items) {
      previous = writeItem(item, previous, page);
    }
    if (page + 1 < pageCount) {
      line("<a class=\"pagination block_link\" href=\"" + messagesFile(page + 1) + "\">Next messages</a>");
    }
    endFile();
  }

  /** A chat with nothing to export: one page with Desktop's "No exported messages". */
  @WorkerThread
  void writeEmpty () throws IOException {
    beginFile(0);
    writeService("-" + (++dateMessageId), "No exported messages");
    endFile();
  }

  /**
   * A partial export (Android's time limit for background work): page {@code page}, which the previous page's "Next
   * messages" links to, holds only a line saying where the export stopped, and links back.
   */
  @WorkerThread
  void writeStopped (int page) throws IOException {
    beginFile(page);
    writeService("-" + (++dateMessageId), escape("The export stopped here: Android's time limit for background work ended it. Later messages were not exported."));
    endFile();
  }

  // Page frame (composeStart, writeDialogOpening, Wrap::close)

  private void beginFile (int page) throws IOException {
    w = zip.beginText(root + messagesFile(page), entryTimeMs);
    depth = 0;
    pageTopicId = 0; // every page says which topic its first message is in
    write("<!DOCTYPE html>\n");
    open("<html>");
    open("<head>");
    line("<meta charset=\"utf-8\"/>");
    line("<title>Exported Data</title>");
    line("<meta content=\"width=device-width, initial-scale=1.0\" name=\"viewport\"/>");
    line("<link href=\"css/style.css\" rel=\"stylesheet\"/>");
    line("<script src=\"js/script.js\" type=\"text/javascript\"></script>");
    close("head");
    open("<body onload=\"CheckLocation();\">");
    open("<div class=\"page_wrap\">");
    open("<div class=\"page_header\">");
    open("<div class=\"content\">");
    line("<div class=\"text bold\">" + escape(headerName) + "</div>");
    close("div");
    close("div");
    open("<div class=\"page_body chat_page\">");
    open("<div class=\"history\">");
    if (page > 0) {
      line("<a class=\"pagination block_link\" href=\"" + messagesFile(page - 1) + "\">Previous messages</a>");
    }
  }

  private void endFile () throws IOException {
    close("div"); // history
    close("div"); // page_body
    close("div"); // page_wrap
    close("body");
    close("html");
    zip.endText();
    w = null;
  }

  // Messages

  /** What the next message needs to know about this one (Desktop's MessageInfo). */
  private static final class Info {
    final boolean service;
    final long fromKey, viaBotId;
    final int date, dayKey;
    /** Desktop's showForwardedAsOriginal: a copy that shows its original author as the sender. */
    final boolean forwarded, asOriginal, forwardedFromUser;
    final long forwardedKey;
    @NonNull
    final String forwardedName;
    final int forwardedDate;
    /** The forum topic in a whole-forum export, 0 otherwise. */
    final int topicId;

    Info (boolean service, long fromKey, long viaBotId, int date, int dayKey, boolean forwarded, boolean asOriginal, long forwardedKey,
          @NonNull String forwardedName, int forwardedDate, boolean forwardedFromUser, int topicId) {
      this.service = service;
      this.fromKey = fromKey;
      this.viaBotId = viaBotId;
      this.date = date;
      this.dayKey = dayKey;
      this.forwarded = forwarded;
      this.asOriginal = asOriginal;
      this.forwardedKey = forwardedKey;
      this.forwardedName = forwardedName;
      this.forwardedDate = forwardedDate;
      this.forwardedFromUser = forwardedFromUser;
      this.topicId = topicId;
    }

    static Info service (int date, int dayKey) {
      return new Info(true, 0, 0, date, dayKey, false, false, 0, "", 0, false, 0);
    }
  }

  private Info writeItem (ExportJob.Item item, @Nullable Info previous, int page) throws IOException {
    final TdApi.Message message = item.message;
    final int dayKey = dayKey(message.date);
    if (previous == null || previous.dayKey != dayKey) {
      writeService("-" + (++dateMessageId), escape(dayFormat.format(new Date(message.date * 1000L))));
    }
    switch (item.kind) {
      case ExportJob.Item.KIND_UNSUPPORTED:
        writeService(String.valueOf(item.serverId), escape("This message is not supported by this version of XGram. Please update the application."));
        return Info.service(message.date, dayKey);
      case ExportJob.Item.KIND_SERVICE:
        if (ExportActions.isCall(message.content)) {
          return writeMessage(item, previous, dayKey, page); // Desktop shows calls as messages with a call block
        }
        writeService(String.valueOf(item.serverId), serviceText(item, page), item.media);
        return Info.service(message.date, dayKey);
      default:
        return writeMessage(item, previous, dayKey, page);
    }
  }

  private ExportActions.Context context (TdApi.Message message) {
    return new ExportActions.Context(tdlib, peers, dialog, chatId, message);
  }

  /**
   * The line of a service message, as HTML: Desktop's sentence, or for what Desktop has no action for the line after
   * "actor: " (a whole sentence stands alone; a channel names no actor).
   */
  private String serviceText (ExportJob.Item item, int page) {
    if (ExportActions.hasAction(item.message.content)) {
      final String html = ExportActions.html(context(item.message), (messageId, text) -> messageLink(messageId, MessageId.toServerMessageId(messageId), page, text));
      if (!html.isEmpty()) {
        return html;
      }
    }
    final String text = escape(item.fallback);
    if (item.hideAuthor || dialog.isChannel) {
      return text.isEmpty() ? "Service message" : text;
    }
    final String actor = peers.sender(item.message.senderId).name();
    final String actorHtml = actor.isEmpty() ? "Deleted" : escape(actor); // Desktop's wrapPeerName
    return text.isEmpty() ? actorHtml : actorHtml + ": " + text;
  }

  private void writeService (String id, String html) throws IOException {
    writeService(id, html, null);
  }

  /**
   * Desktop's pushServiceMessage. A changed chat photo follows the line as a 60px picture linking to the photo; when it
   * was not exported, the chat's coloured circle with its initials.
   */
  private void writeService (String id, String html, @Nullable ExportMedia.Result photo) throws IOException {
    open("<div class=\"message service\" id=\"message" + id + "\">");
    line("<div class=\"body details\">" + html + "</div>");
    if (photo != null && photo.plan.kind == ExportMedia.KIND_CHAT_PHOTO) {
      final String size = SERVICE_PHOTO_SIZE + "px";
      open("<div class=\"userpic_wrap\">");
      if (photo.path != null) {
        open("<a class=\"userpic_link\" href=\"" + url(photo.path) + "\">");
      }
      if (photo.imagePath != null) {
        line("<img class=\"userpic\" src=\"" + url(photo.imagePath) + "\" style=\"width: " + size + "; height: " + size + "\"/>");
      } else {
        open("<div class=\"userpic userpic" + ExportPeers.colorClass(dialog.bareId) + "\" style=\"width: " + size + "; height: " + size + "\">");
        final String name = dialog.htmlName.trim(); // a chat's name has no last name: one letter, as Desktop
        final String initial = name.isEmpty() ? "" : new String(Character.toChars(name.codePointAt(0)));
        line("<div class=\"initials\" style=\"line-height: " + size + "\">" + escape(initial) + "</div>");
        close("div");
      }
      if (photo.path != null) {
        close("a");
      }
      close("div");
    }
    close("div");
  }

  private Info writeMessage (ExportJob.Item item, @Nullable Info previous, int dayKey, int page) throws IOException {
    final TdApi.Message message = item.message;
    final ExportPeers.Peer from = peers.sender(message.senderId);

    // The forward's origin: a known user or chat, or a hidden user's name
    final TdApi.MessageForwardInfo forward = message.forwardInfo;
    ExportPeers.Peer origin = null;
    String hiddenName = "";
    if (forward != null && forward.origin != null) {
      origin = peers.origin(forward.origin);
      if (origin == null && forward.origin instanceof TdApi.MessageOriginHiddenUser) {
        final String name = ((TdApi.MessageOriginHiddenUser) forward.origin).senderName;
        hiddenName = name != null ? name : "";
      }
    }
    // Desktop: forwarded = forwardedFromId || !forwardedFromName.isEmpty(), as the JSON writer has it
    final boolean forwarded = forward != null && (origin != null || !hiddenName.isEmpty());
    // Desktop's showForwardedAsOriginal: a copy in Saved Messages (or Replies, or a discussion group) shows its original
    // author as the sender, with no forwarded block
    final boolean asOriginal = forwarded && ExportJsonWriter.savedFromChatId(forward) != 0;
    final int topicId = topicChips ? ExportJsonWriter.forumTopicId(message) : 0;
    final Info info = new Info(false, from.key, message.viaBotUserId, message.date, dayKey, forwarded, asOriginal,
      origin != null ? origin.key : 0, hiddenName, forwarded ? forward.date : 0, origin != null && origin.isUser, topicId);
    final boolean wrap = needsWrap(info, previous);
    final String via = message.viaBotUserId != 0 ? peers.user(message.viaBotUserId).username : "";

    // The original sender of a forward: a known user or chat, or a hidden user (coloured by the message id, as Desktop)
    String forwardedName = "", forwardedInitials = "";
    int forwardedColor = 0;
    if (forwarded) {
      if (origin != null) {
        forwardedName = origin.displayName();
        forwardedColor = origin.colorClass();
        forwardedInitials = origin.initials();
      } else {
        forwardedName = hiddenName;
        forwardedColor = ExportPeers.colorClass(item.serverId);
        forwardedInitials = initialsOf(hiddenName);
      }
    }

    open("<div class=\"message default clearfix" + (wrap ? "" : " joined") + "\" id=\"message" + item.serverId + "\">");
    if (wrap) {
      open("<div class=\"pull_left userpic_wrap\">");
      if (asOriginal) {
        userpic(forwardedColor, forwardedInitials, USERPIC_SIZE, null);
      } else {
        userpic(from.colorClass(), from.initials(), USERPIC_SIZE, null);
      }
      close("div");
    }
    open("<div class=\"body\">");
    line("<div class=\"pull_right date details\" title=\"" + escape(dateTime(message.date, true)) + "\">" + timeFormat.format(new Date(message.date * 1000L)) + "</div>");
    if (wrap) {
      line("<div class=\"from_name\">" + escape(asOriginal ? forwardedName : from.displayName()) +
        (!via.isEmpty() && (!forwarded || asOriginal) ? " via @" + escape(via) : "") + "</div>");
    }
    final boolean forwardedBlock = forwarded && !asOriginal;
    if (forwardedBlock) {
      final boolean forwardedWrap = wrap || forwardedNeedsWrap(info, previous);
      if (forwardedWrap) {
        open("<div class=\"pull_left forwarded userpic_wrap\">");
        userpic(forwardedColor, forwardedInitials, USERPIC_SIZE, null);
        close("div");
      }
      open("<div class=\"forwarded body\">");
      if (forwardedWrap) {
        line("<div class=\"from_name\">" + escape(forwardedName) + (!via.isEmpty() ? " via @" + escape(via) : "") +
          "<span class=\"date details\" title=\"" + escape(dateTime(forward.date, true)) + "\"> " + escape(dateTime(forward.date, false)) + "</span></div>");
      }
    }
    writeTopic(topicId, page);
    writeReply(message, page);
    writeMedia(item.media);
    writeContent(item, page);
    final String text = !ExportText.isEmpty(item.text) ? ExportText.html(item.text) : escape(item.fallback);
    if (!text.isEmpty()) {
      line("<div class=\"text\">" + text + "</div>");
    }
    writeButtons(message.replyMarkup);
    if (message.authorSignature != null && !message.authorSignature.isEmpty()) {
      line("<div class=\"signature details\">" + escape(message.authorSignature) + "</div>");
    }
    if (forwardedBlock) {
      close("div"); // forwarded body
    }
    writeReactions(message);
    close("div"); // body
    close("div"); // message
    return info;
  }

  /** Desktop's messageNeedsWrap: whether the message shows its userpic and name, or joins the one above. */
  private static boolean needsWrap (Info info, @Nullable Info previous) {
    if (previous == null || previous.service) {
      return true;
    }
    if (info.fromKey == 0 || previous.fromKey != info.fromKey) {
      return true;
    }
    if (info.viaBotId != previous.viaBotId || info.dayKey != previous.dayKey) {
      return true;
    }
    if (info.forwarded != previous.forwarded || info.asOriginal != previous.asOriginal ||
      info.forwardedKey != previous.forwardedKey || !info.forwardedName.equals(previous.forwardedName)) {
      return true;
    }
    if (info.topicId != previous.topicId) {
      return true; // XGram: a message in another forum topic starts a new block with its "In topic" line
    }
    final boolean fromForward = info.forwardedKey != 0 || !info.forwardedName.isEmpty();
    return Math.abs(info.date - previous.date) > (fromForward ? 1 : JOIN_WITHIN_SECONDS);
  }

  /** Desktop's forwardedNeedsWrap, for a message that joins the one above. */
  private static boolean forwardedNeedsWrap (Info info, @Nullable Info previous) {
    if (previous == null || info.forwardedKey == 0 || info.forwardedKey != previous.forwardedKey || !info.forwardedFromUser) {
      return true;
    }
    return Math.abs(info.forwardedDate - previous.forwardedDate) > JOIN_WITHIN_SECONDS;
  }

  /** Desktop's pushUserpic without a photo: the coloured circle with the initials (and a tooltip, for reactions). */
  private void userpic (int colorClass, String initials, int pixelSize, @Nullable String tooltip) throws IOException {
    final String size = pixelSize + "px";
    open("<div class=\"userpic userpic" + colorClass + "\" style=\"width: " + size + "; height: " + size + "\">");
    line("<div class=\"initials\" style=\"line-height: " + size + "\"" + (tooltip != null ? " title=\"" + escape(tooltip) + "\"" : "") + ">" + escape(initials) + "</div>");
    close("div");
  }

  /**
   * XGram, in a whole-forum export: "In topic «Name»" when a message is in another topic than the message before it
   * on the page, linking to the topic's first message (General has none).
   */
  private void writeTopic (int topicId, int page) throws IOException {
    if (topicId == 0 || topicId == pageTopicId) {
      return;
    }
    pageTopicId = topicId;
    final String name = peers.topicName(chatId, topicId);
    final boolean general = topicId == ExportJsonWriter.GENERAL_TOPIC_ID;
    final String label = "&laquo;" + escape(!name.isEmpty() ? name : general ? "General" : "Deleted topic") + "&raquo;";
    line("<div class=\"reply_to details\">In topic " + (general ? label : messageLink(MessageId.fromServerMessageId(topicId), topicId, page, label)) + "</div>");
  }

  /** Desktop's FillUserpicNames for a bare name: the first word and the first letter of the rest. */
  private static String initialsOf (String fullName) {
    final String name = fullName.trim();
    if (name.isEmpty()) {
      return "DA";
    }
    final int space = name.indexOf(' ');
    final StringBuilder b = new StringBuilder().appendCodePoint(name.codePointAt(0));
    if (space > 0) {
      final String rest = name.substring(space + 1).trim();
      if (!rest.isEmpty()) {
        b.appendCodePoint(rest.codePointAt(0));
      }
    }
    return b.toString();
  }

  /** "In reply to this message", linking to the page that holds it; a reply to another chat is plain text. */
  private void writeReply (TdApi.Message message, int page) throws IOException {
    if (!(message.replyTo instanceof TdApi.MessageReplyToMessage)) {
      return;
    }
    final TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
    final boolean sameChat = reply.chatId == chatId || (reply.chatId == 0 && reply.origin == null);
    if (!sameChat) {
      if (reply.messageId != 0 || reply.origin != null) {
        line("<div class=\"reply_to details\">In reply to a message in another chat</div>");
      }
      return;
    }
    final long serverId = reply.messageId != 0 ? MessageId.toServerMessageId(reply.messageId) : 0;
    if (serverId == 0) {
      return;
    }
    line("<div class=\"reply_to details\">In reply to " + messageLink(reply.messageId, serverId, page, "this message") + "</div>");
  }

  /**
   * Desktop's wrapMessageLink, knowing every page ahead: on this page (or older than the export) GoToMessage, on
   * another page that page's anchor. A message that was not exported gets the script's "not exported" toast.
   */
  private String messageLink (long messageId, long serverId, int page, String html) {
    final int target = index.pageOf(messageId);
    if (target < 0 || target == page) {
      return "<a href=\"#go_to_message" + serverId + "\" onclick=\"return GoToMessage(" + serverId + ")\">" + html + "</a>";
    }
    return "<a href=\"" + messagesFile(target) + "#go_to_message" + serverId + "\">" + html + "</a>";
  }

  // Media (Desktop's pushMedia, prepareMediaData and the push*Media blocks)

  /** Desktop's NoFileDescription. */
  private static String noFileDescription (int skip) {
    switch (skip) {
      case ExportMedia.SKIP_TYPE:
        return "Not included, change data exporting settings to download.";
      case ExportMedia.SKIP_SIZE:
        return "Exceeds maximum size, change data exporting settings to download.";
      case ExportMedia.SKIP_UNAVAILABLE:
        return "Unavailable, please try again later.";
      default:
        return "";
    }
  }

  /**
   * The message's file: the picture of an exported photo, sticker, video or GIF, else Desktop's generic block, which
   * links to the file or says why it was left out. The size goes into the status line only for a file that is not in
   * the export, as Desktop writes it.
   */
  private void writeMedia (@Nullable ExportMedia.Result media) throws IOException {
    if (media == null || media.plan.kind == ExportMedia.KIND_CHAT_PHOTO) {
      return;
    }
    final ExportMedia.Plan plan = media.plan;
    if (plan.selfDestructing) {
      final boolean photo = plan.kind == ExportMedia.KIND_PHOTO;
      generic(photo ? "media_photo" : "media_video", null, null, photo ? "Self-destructing photo" : "Self-destructing video", "", "Please view it on your mobile");
      return;
    }
    final String link = media.path;
    final boolean hasFile = link != null;
    final String description = hasFile ? "" : noFileDescription(media.skip);
    final String sizeText = size(plan.size);
    switch (plan.kind) {
      case ExportMedia.KIND_PHOTO: {
        if (hasFile && media.imagePath != null) {
          picture("photo_wrap", "photo", link, media.imagePath, media.imageWidth, media.imageHeight);
          return;
        }
        final String dimensions = plan.width > 0 && plan.height > 0 ? plan.width + "×" + plan.height : "";
        generic("media_photo", link, null, "Photo", description, hasFile ? dimensions : join(dimensions, sizeText));
        return;
      }
      case ExportMedia.KIND_STICKER: {
        if (hasFile && media.imagePath != null) {
          picture("sticker_wrap", "sticker", link, media.imagePath, media.imageWidth, media.imageHeight);
          return;
        }
        generic("media_photo", link, null, "Sticker", description, hasFile ? plan.emoji : join(plan.emoji, sizeText));
        return;
      }
      case ExportMedia.KIND_ANIMATION:
      case ExportMedia.KIND_VIDEO: {
        final boolean animation = plan.kind == ExportMedia.KIND_ANIMATION;
        final int[] thumbSize = ExportMedia.thumbSize(plan.width, plan.height, ExportMedia.PHOTO_MAX, true);
        if (hasFile && media.thumbPath != null && thumbSize != null) {
          open("<div class=\"media_wrap clearfix\">");
          open("<a class=\"" + (animation ? "animated_wrap" : "video_file_wrap") + " clearfix pull_left\" href=\"" + url(link) + "\">");
          open("<div class=\"video_play_bg\">");
          line(animation ? "<div class=\"gif_play\">GIF</div>" : "<div class=\"video_play\"></div>");
          close("div");
          if (!animation) {
            line("<div class=\"video_duration\">" + escape(duration(plan.duration)) + "</div>");
          }
          line("<img class=\"" + (animation ? "animated" : "video_file") + "\" src=\"" + url(media.thumbPath) + "\" style=\"" + sizeStyle(thumbSize[0], thumbSize[1]) + "\"/>");
          close("a");
          close("div");
          return;
        }
        if (animation) {
          generic("media_video", link, null, "Animation", description, sizeText);
        } else {
          generic("media_video", link, null, "Video file", description, hasFile ? duration(plan.duration) : join(duration(plan.duration), sizeText));
        }
        return;
      }
      case ExportMedia.KIND_VIDEO_NOTE:
        generic("media_video", link, media.thumbPath, "Video message", description, hasFile ? duration(plan.duration) : join(duration(plan.duration), sizeText));
        return;
      case ExportMedia.KIND_VOICE:
        generic("media_voice_message", link, null, "Voice message", description, hasFile ? duration(plan.duration) : join(duration(plan.duration), sizeText));
        return;
      case ExportMedia.KIND_AUDIO: {
        final String title = !plan.performer.isEmpty() && !plan.title.isEmpty() ? plan.performer + " – " + plan.title :
          !plan.fileName.isEmpty() ? plan.fileName : "Audio file";
        generic("media_audio_file", link, null, title, description, hasFile ? duration(plan.duration) : join(duration(plan.duration), sizeText));
        return;
      }
      case ExportMedia.KIND_DOCUMENT:
      default:
        generic("media_file", link, null, plan.fileName.isEmpty() ? "File" : plan.fileName, description, sizeText);
    }
  }

  /** Desktop's photo and sticker blocks: the picture at half its pixel size (it is made for 2x screens). */
  private void picture (String wrapClass, String imageClass, String link, String image, int width, int height) throws IOException {
    open("<div class=\"media_wrap clearfix\">");
    open("<a class=\"" + wrapClass + " clearfix pull_left\" href=\"" + url(link) + "\">");
    line("<img class=\"" + imageClass + "\" src=\"" + url(image) + "\" style=\"" + sizeStyle(width, height) + "\"/>");
    close("a");
    close("div");
  }

  private static String sizeStyle (int width, int height) {
    return "width: " + (width / 2) + "px; height: " + (height / 2) + "px";
  }

  /** Desktop's pushGenericMedia: a link to the file when it is in the export, its thumbnail or the icon circle. */
  private void generic (String classes, @Nullable String link, @Nullable String thumb, String title, String description, String status) throws IOException {
    block(classes, link != null ? url(link) : null, thumb, title, description, status);
  }

  /**
   * Desktop's pushGenericMedia with {@code href} ready for the attribute (a file in the export through {@link #url},
   * or an escaped web link such as a map).
   */
  private void block (String classes, @Nullable String href, @Nullable String thumb, String title, String description, String status) throws IOException {
    final String link = href;
    open("<div class=\"media_wrap clearfix\">");
    if (link != null) {
      open("<a class=\"media clearfix pull_left block_link " + classes + "\" href=\"" + link + "\">");
    } else {
      open("<div class=\"media clearfix pull_left " + classes + "\">");
    }
    if (thumb != null) {
      line("<img class=\"thumb pull_left\" src=\"" + url(thumb) + "\"/>");
    } else {
      line("<div class=\"fill pull_left\"></div>");
    }
    open("<div class=\"body\">");
    if (!title.isEmpty()) {
      line("<div class=\"title bold\">" + escape(title) + "</div>");
    }
    if (!description.isEmpty()) {
      line("<div class=\"description\">" + escape(description) + "</div>");
    }
    if (!status.isEmpty()) {
      line("<div class=\"status details\">" + escape(status) + "</div>");
    }
    close("div");
    close(link != null ? "a" : "div");
    close("div");
  }

  private static String join (String first, String second) {
    return first.isEmpty() ? second : second.isEmpty() ? first : first + ", " + second;
  }

  // Contents that are not files (Desktop's prepareMediaData, pushPoll, pushTodoList, pushGiveaway)

  /**
   * The block of a content that is not a file: a call, contact, location, venue, game, invoice, paid media, poll, to-do
   * list or giveaway as Desktop draws them; a dice, a story and an expired self-destructing file as XGram does
   * (Desktop leaves them empty).
   */
  private void writeContent (ExportJob.Item item, int page) throws IOException {
    final TdApi.Message message = item.message;
    final TdApi.MessageContent content = message.content;
    switch (content.getConstructor()) {
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR: {
        final String[] call = ExportActions.callBlock(context(message));
        block(call[0], null, null, call[1], "", call[2]);
        break;
      }
      case TdApi.MessageContact.CONSTRUCTOR: {
        final TdApi.Contact contact = ((TdApi.MessageContact) content).contact;
        if (contact == null) {
          break;
        }
        final boolean vcard = contact.vcard != null && !contact.vcard.isEmpty();
        final String title = (contact.firstName != null ? contact.firstName : "") + " " + (contact.lastName != null ? contact.lastName : "");
        final String status = ExportJsonWriter.phone(contact.phoneNumber) + (vcard ? " - vCard" : "");
        generic("media_contact", vcard ? item.vcardPath : null, null, title.trim(), "", status);
        break;
      }
      case TdApi.MessageLocation.CONSTRUCTOR: {
        final TdApi.Location location = ((TdApi.MessageLocation) content).location;
        block("media_location", mapLink(location), null, "Location", "", coordinates(location));
        break;
      }
      case TdApi.MessageLiveLocation.CONSTRUCTOR: {
        final TdApi.LiveLocation live = ((TdApi.MessageLiveLocation) content).location;
        final TdApi.Location location = live != null ? live.location : null;
        block("media_live_location", mapLink(location), null, "Live location", "", coordinates(location));
        break;
      }
      case TdApi.MessageVenue.CONSTRUCTOR: {
        final TdApi.Venue venue = ((TdApi.MessageVenue) content).venue;
        if (venue != null) {
          block("media_venue", mapLink(venue.location), null, nonNull(venue.title), nonNull(venue.address), "");
        }
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        final TdApi.Game game = ((TdApi.MessageGame) content).game;
        if (game != null) {
          final String link = ExportJsonWriter.gameLink(peers, message, game);
          block("media_game", link.isEmpty() ? null : escape(link), null, nonNull(game.title), nonNull(game.description), link);
        }
        break;
      }
      case TdApi.MessageInvoice.CONSTRUCTOR: {
        final TdApi.MessageInvoice invoice = (TdApi.MessageInvoice) content;
        block("media_invoice", null, null, invoice.productInfo != null ? nonNull(invoice.productInfo.title) : "",
          invoice.productInfo != null ? ExportText.plain(invoice.productInfo.description) : "", ExportActions.money(invoice.currency, invoice.totalAmount));
        break;
      }
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        block("media_invoice", null, null, "", "", ExportActions.money("XTR", ((TdApi.MessagePaidMedia) content).starCount));
        break;
      case TdApi.MessagePoll.CONSTRUCTOR: {
        final TdApi.Poll poll = ((TdApi.MessagePoll) content).poll;
        if (poll != null) {
          writePoll(poll);
        }
        break;
      }
      case TdApi.MessageChecklist.CONSTRUCTOR: {
        final TdApi.Checklist list = ((TdApi.MessageChecklist) content).list;
        if (list != null) {
          writeTodoList(list);
        }
        break;
      }
      case TdApi.MessageGiveaway.CONSTRUCTOR:
        writeGiveaway((TdApi.MessageGiveaway) content);
        break;
      case TdApi.MessageGiveawayWinners.CONSTRUCTOR:
        writeGiveawayWinners((TdApi.MessageGiveawayWinners) content, page);
        break;
      case TdApi.MessageDice.CONSTRUCTOR: {
        final TdApi.MessageDice dice = (TdApi.MessageDice) content;
        line("<div class=\"text\">" + escape(nonNull(dice.emoji) + " " + dice.value) + "</div>");
        break;
      }
      case TdApi.MessageStakeDice.CONSTRUCTOR:
        line("<div class=\"text\">" + escape(ExportJsonWriter.DICE + " " + ((TdApi.MessageStakeDice) content).value) + "</div>");
        break;
      case TdApi.MessageStory.CONSTRUCTOR: {
        final TdApi.MessageStory story = (TdApi.MessageStory) content;
        block("media_photo", null, null, "Story", "", story.storyPosterChatId != 0 ? peers.chat(story.storyPosterChatId).displayName() : "");
        break;
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
        block("media_photo", null, null, "Self-destructing photo", "", "Expired");
        break;
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
      case TdApi.MessageExpiredVideoNote.CONSTRUCTOR:
        block("media_video", null, null, "Self-destructing video", "", "Expired");
        break;
      case TdApi.MessageExpiredVoiceNote.CONSTRUCTOR:
        block("media_voice_message", null, null, "Self-destructing voice message", "", "Expired");
        break;
      default:
        break;
    }
  }

  @NonNull
  private static String nonNull (@Nullable String value) {
    return value != null ? value : "";
  }

  /** "lat, lon" with Desktop's 6 decimals, "" for a point that is not valid. */
  private static String coordinates (@Nullable TdApi.Location location) {
    if (!ExportJsonWriter.isValid(location)) {
      return "";
    }
    return ExportJsonWriter.coordinate(location.latitude) + ", " + ExportJsonWriter.coordinate(location.longitude);
  }

  /** Desktop's map link, escaped for the attribute: https://maps.google.com/maps?q=lat,lon&ll=lat,lon&z=16. */
  @Nullable
  private static String mapLink (@Nullable TdApi.Location location) {
    if (!ExportJsonWriter.isValid(location)) {
      return null;
    }
    final String coords = ExportJsonWriter.coordinate(location.latitude) + "," + ExportJsonWriter.coordinate(location.longitude);
    return escape("https://maps.google.com/maps?q=" + coords + "&ll=" + coords + "&z=16");
  }

  /** Desktop's vote counts: "No votes", "1 vote", "N votes". */
  private static String votes (int count) {
    return count > 1 ? count + " votes" : count > 0 ? count + " vote" : "No votes";
  }

  /** Desktop's pushPoll; the kind of poll is named as it is (Desktop says "Anonymous poll" for every open poll). */
  private void writePoll (TdApi.Poll poll) throws IOException {
    open("<div class=\"media_wrap clearfix\">");
    open("<div class=\"media_poll\">");
    line("<div class=\"question bold\">" + ExportText.html(poll.question) + "</div>");
    final boolean quiz = poll.type instanceof TdApi.PollTypeQuiz;
    final String kind = poll.isClosed ? "Final results" :
      poll.isAnonymous ? (quiz ? "Anonymous quiz" : "Anonymous poll") : (quiz ? "Quiz" : "Public poll");
    line("<div class=\"details\">" + kind + "</div>");
    if (poll.options != null) {
      for (TdApi.PollOption option : poll.options) {
        final String details = option.voterCount == 0 ? "" :
          " <span class=\"details\">" + votes(option.voterCount) + (option.isChosen ? ", chosen vote" : "") + "</span>";
        line("<div class=\"answer\">- " + ExportText.html(option.text) + details + "</div>");
      }
    }
    line("<div class=\"total details\">" + votes(poll.totalVoterCount) + "</div>");
    close("div");
    close("div");
  }

  /** Desktop's pushTodoList, with "done" after the tasks that are (Desktop leaves that to do). */
  private void writeTodoList (TdApi.Checklist list) throws IOException {
    open("<div class=\"media_wrap clearfix\">");
    open("<div class=\"media_poll\">");
    line("<div class=\"question bold\">" + ExportText.html(list.title) + "</div>");
    line("<div class=\"details\">To-do List</div>");
    if (list.tasks != null) {
      for (TdApi.ChecklistTask task : list.tasks) {
        line("<div class=\"answer\">- " + ExportText.html(task.text) + (task.completionDate != 0 ? " <span class=\"details\">done</span>" : "") + "</div>");
      }
    }
    close("div");
    close("div");
  }

  private void section (String title, String bodyHtml) throws IOException {
    if (!title.isEmpty()) {
      line("<div class=\"section_title bold\">" + escape(title) + "</div>");
    }
    line("<div class=\"section_body\">" + bodyHtml + "</div>");
  }

  /** Desktop's pushGiveaway for a giveaway that was started. */
  private void writeGiveaway (TdApi.MessageGiveaway giveaway) throws IOException {
    final TdApi.GiveawayParameters parameters = giveaway.parameters;
    final int quantity = giveaway.winnerCount;
    final long stars = giveaway.prize instanceof TdApi.GiveawayPrizeStars ? ((TdApi.GiveawayPrizeStars) giveaway.prize).starCount : 0;
    final int months = giveaway.prize instanceof TdApi.GiveawayPrizePremium ? ((TdApi.GiveawayPrizePremium) giveaway.prize).monthCount : 0;
    open("<div class=\"media_wrap clearfix\">");
    open("<div class=\"media_giveaway\">");
    line("<div class=\"section_title bold\">" + (quantity > 1 ? "Giveaway Prizes" : "Giveaway Prize") + "</div>");
    final String extra = parameters != null ? nonNull(parameters.prizeDescription) : "";
    // Only with an additional prize, as Telegram shows it: Desktop's export writes these two lines always (a bare { }
    // block), which gives a lone "<b>N</b>" and "with" for most giveaways (a deliberate difference)
    if (!extra.isEmpty()) {
      line("<div class=\"section_body\"><b>" + quantity + "</b> " + escape(extra) + "</div>");
      line("<div class=\"section_title bold\">with</div>");
    }
    if (stars > 0) {
      line("<div class=\"section_body\"><b>" + stars + (stars == 1 ? " Star" : " Stars") + "</b> will be distributed " +
        (quantity == 1 ? "to <b>1</b> winner." : "among <b>" + quantity + "</b> winners.") + "</div>");
    } else {
      line("<div class=\"section_body\"><b>" + quantity + "</b> " + (quantity > 1 ? "Telegram Premium Subscriptions" : "Telegram Premium Subscription") +
        " for <b>" + months + "</b> " + (months > 1 ? "months." : "month.") + "</div>");
    }
    // Participants: the chats' real names (Desktop builds this list and then drops it by mistake)
    final List<String> channels = new ArrayList<>();
    boolean anyChannel = false, anyGroup = false;
    if (parameters != null) {
      final List<Long> ids = new ArrayList<>();
      if (parameters.boostedChatId != 0) {
        ids.add(parameters.boostedChatId);
      }
      if (parameters.additionalChatIds != null) {
        for (long id : parameters.additionalChatIds) {
          ids.add(id);
        }
      }
      for (long id : ids) {
        final ExportPeers.Peer chat = peers.chat(id);
        if (chat.isBroadcast) {
          anyChannel = true;
        } else {
          anyGroup = true;
        }
        channels.add("<b>" + peerHtml(chat) + "</b>");
      }
    }
    final boolean all = parameters == null || !parameters.onlyNewMembers;
    final boolean many = channels.size() > 1;
    final String who;
    if (anyGroup && anyChannel) {
      who = many ? "groups and channels" : "group";
    } else if (anyGroup) {
      who = many ? "groups" : "group";
    } else {
      who = many ? "channels" : "channel";
    }
    final String participants = all ?
      (anyChannel && !anyGroup ? "All subscribers of the " + who + ":" : "All members of the " + who + ":") :
      "All users who joined the " + who + " below after this date:";
    line("<div class=\"section_title bold\">Participants</div>");
    line("<div class=\"section_body\">" + escape(participants) + " " + joinList(channels) + "</div>");
    if (parameters != null && parameters.countryCodes != null && parameters.countryCodes.length > 0) {
      String united = "";
      for (int i = 0; i < parameters.countryCodes.length; i++) {
        final String country = country(parameters.countryCodes[i]);
        united = i == 0 ? country : united + (i + 1 == parameters.countryCodes.length ? " and " : ", ") + country;
      }
      line("<div class=\"section_body\">" + escape("from " + united) + "</div>");
    }
    line("<div class=\"section_title bold\">Winners Selection Date</div>");
    line("<div class=\"section_body\">" + (parameters != null ? escape(dateTime(parameters.winnersSelectionDate, false)) : "") + "</div>");
    close("div");
    close("div");
  }

  /** Desktop's pushGiveaway for the results. */
  private void writeGiveawayWinners (TdApi.MessageGiveawayWinners winners, int page) throws IOException {
    final int count = winners.winnerCount;
    final long stars = winners.prize instanceof TdApi.GiveawayPrizeStars ? ((TdApi.GiveawayPrizeStars) winners.prize).starCount : 0;
    open("<div class=\"media_wrap clearfix\">");
    open("<div class=\"media_giveaway\">");
    line("<div class=\"section_title bold\">" + (count > 1 ? "Winners Selected!" : "Winner Selected!") + "</div>");
    // The giveaway message is in the boosted chat: linked when that is this chat
    final String giveaway = winners.giveawayMessageId != 0 && winners.boostedChatId == chatId && MessageId.toServerMessageId(winners.giveawayMessageId) != 0 ?
      messageLink(winners.giveawayMessageId, MessageId.toServerMessageId(winners.giveawayMessageId), page, "Giveaway") : "Giveaway";
    line("<div class=\"section_body\"><b>" + count + "</b> " + (count > 1 ? "winners" : "winner") + " of the " + giveaway + " was randomly selected by Telegram.</div>");
    line("<div class=\"section_title bold\">" + (count > 1 ? "Winners" : "Winner") + "</div>");
    final List<String> names = new ArrayList<>();
    final int listed = winners.winnerUserIds != null ? winners.winnerUserIds.length : 0;
    for (int i = 0; i < listed; i++) {
      names.add("<b>" + peerHtml(peers.user(winners.winnerUserIds[i])) + "</b>");
    }
    line("<div class=\"section_body\">" + joinList(names) + (count > listed ? " and " + (count - listed) + " more!" : "") + "</div>");
    final String prize;
    if (stars > 0 && count == 1) {
      prize = "The winner received <b>" + stars + "</b>" + (stars == 1 ? " Star." : " Stars.");
    } else if (stars > 0 && count > 1) {
      prize = "All winners received <b>" + stars + "</b>" + (stars == 1 ? " Star in total." : " Stars in total.");
    } else if (winners.unclaimedPrizeCount != 0) {
      prize = "Some winners couldn&apos;t be selected.";
    } else if (count == 1) {
      prize = "The winner received their gift link in a private message.";
    } else if (count > 1) {
      prize = "All winners received gift links in private messages.";
    } else {
      prize = "";
    }
    line("<div class=\"section_body\">" + prize + "</div>");
    close("div");
    close("div");
  }

  /** Desktop's PeersMap::wrapPeerName: the escaped name, "Deleted" for none. */
  private static String peerHtml (ExportPeers.Peer peer) {
    final String name = peer.name();
    return name.isEmpty() ? "Deleted" : escape(name);
  }

  private static String joinList (List<String> values) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        b.append(", ");
      }
      b.append(values.get(i));
    }
    return b.toString();
  }

  /** Desktop's country line: the flag, a no-break space and the English name ("🇩🇪 Germany"). */
  private static String country (@Nullable String code) {
    if (code == null || code.length() != 2) {
      return code != null ? code : "";
    }
    final String upper = code.toUpperCase(Locale.ROOT);
    final StringBuilder flag = new StringBuilder();
    for (int i = 0; i < 2; i++) {
      final char c = upper.charAt(i);
      if (c < 'A' || c > 'Z') {
        return upper;
      }
      flag.appendCodePoint(0x1F1E6 + (c - 'A'));
    }
    final String name = new Locale("", upper).getDisplayCountry(Locale.ENGLISH);
    return flag + "\u00A0" + (name.isEmpty() ? upper : name);
  }

  // Inline keyboards and reactions

  /**
   * Desktop's bot_buttons_table: URL buttons link to their (safe) target; the others show what they hold through
   * script.js's ShowTextCopied.
   */
  private void writeButtons (@Nullable TdApi.ReplyMarkup markup) throws IOException {
    if (!(markup instanceof TdApi.ReplyMarkupInlineKeyboard)) {
      return;
    }
    final TdApi.InlineKeyboardButton[][] rows = ((TdApi.ReplyMarkupInlineKeyboard) markup).rows;
    boolean any = false;
    if (rows != null) {
      for (TdApi.InlineKeyboardButton[] row : rows) {
        any |= row != null && row.length > 0;
      }
    }
    if (!any) {
      return;
    }
    open("<table class=\"bot_buttons_table\">");
    open("<tbody>");
    for (TdApi.InlineKeyboardButton[] row : rows) {
      if (row == null || row.length == 0) {
        continue;
      }
      open("<tr>");
      open("<td class=\"bot_button_row\">");
      for (int i = 0; i < row.length; i++) {
        final ExportJsonWriter.Button button = ExportJsonWriter.Button.of(row[i]);
        final String content = (!button.rawData.isEmpty() ? "Data: " + button.rawData + " | " : "") +
          (!button.forwardText.isEmpty() ? "Forward text: " + button.forwardText + " | " : "") +
          "Type: " + button.type;
        final String href = button.type.equals("url") ? ExportText.safeHref(button.data) : null;
        final String onclick = !button.type.equals("url") ?
          "return ShowTextCopied('" + content.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "');" : null;
        open("<div class=\"bot_button\">");
        open("<a" + (href != null ? " href=\"" + escape(href) + "\"" : "") + (onclick != null ? " onclick=\"" + escape(onclick) + "\"" : "") + ">");
        line("<div>" + escape(row[i].text) + "</div>");
        close("a");
        close("div");
        if (i + 1 < row.length) {
          line("<div class=\"bot_button_column_separator\"></div>");
        }
      }
      close("td");
      close("tr");
    }
    close("tbody");
    close("table");
  }

  /**
   * Desktop's reactions: the emoji (a custom emoji is Desktop's not-downloaded 👋, a paid reaction ⭐), the recent
   * reactors' 20px userpics with their names as tooltips, and the count when not everyone is shown. "active" marks the
   * account's own reaction.
   */
  private void writeReactions (TdApi.Message message) throws IOException {
    final TdApi.MessageReaction[] reactions = ExportJsonWriter.reactionsOf(message);
    if (reactions.length == 0) {
      return;
    }
    open("<span class=\"reactions\">");
    for (TdApi.MessageReaction reaction : reactions) {
      final boolean paid = reaction.type instanceof TdApi.ReactionTypePaid;
      open("<span class=\"reaction" + (reaction.isChosen ? " active" : "") + (paid ? " paid" : "") + "\">");
      final String emoji;
      if (reaction.type instanceof TdApi.ReactionTypeEmoji) {
        emoji = escape(((TdApi.ReactionTypeEmoji) reaction.type).emoji);
      } else if (reaction.type instanceof TdApi.ReactionTypeCustomEmoji) {
        emoji = ExportText.NOT_LOADED_EMOJI + "\uD83D\uDC4B</a>";
      } else {
        emoji = "\u2B50";
      }
      line("<span class=\"emoji\">" + emoji + "</span>");
      final int recent = reaction.recentSenderIds != null ? reaction.recentSenderIds.length : 0;
      if (recent > 0) {
        open("<span class=\"userpics\">");
        for (TdApi.MessageSender sender : reaction.recentSenderIds) {
          final ExportPeers.Peer peer = peers.sender(sender);
          userpic(peer.colorClass(), peer.initials(), REACTION_USERPIC_SIZE, peer.displayName());
        }
        close("span");
      }
      if (recent == 0 || reaction.totalCount > recent) {
        line("<span class=\"count\">" + reaction.totalCount + "</span>");
      }
      close("span");
    }
    close("span");
  }

  /**
   * A path in the export for href / src: "%", "#" and "?" (which a file name may hold) are escaped so the browser
   * doesn't take them for an escape, a fragment or a query, then the attribute is HTML-escaped.
   */
  static String url (String path) {
    final StringBuilder b = new StringBuilder(path.length() + 8);
    for (int i = 0; i < path.length(); i++) {
      final char c = path.charAt(i);
      switch (c) {
        case '%':
          b.append("%25");
          break;
        case '#':
          b.append("%23");
          break;
        case '?':
          b.append("%3F");
          break;
        default:
          b.append(c);
          break;
      }
    }
    return escape(b.toString());
  }

  /** Desktop's FormatSizeText (format_values.cpp): "512 B", "310.5 KB", "1.2 MB". */
  static String size (@Nullable TdApi.File file) {
    return size(file == null ? 0 : file.size != 0 ? file.size : file.expectedSize);
  }

  static String size (long size) {
    if (size >= 1024 * 1024) {
      final long tenths = size * 10 / (1024 * 1024);
      return (tenths / 10) + "." + (tenths % 10) + " MB";
    }
    if (size >= 1024) {
      final long tenths = size * 10 / 1024;
      return (tenths / 10) + "." + (tenths % 10) + " KB";
    }
    return size + " B";
  }

  /** Desktop's FormatDurationText: "00:42", "12:05", "1:02:03". */
  static String duration (int seconds) {
    final int hours = seconds / 3600, minutes = seconds % 3600 / 60, rest = seconds % 60;
    return (hours > 0 ? hours + ":" : "") + twoDigits(minutes) + ":" + twoDigits(rest);
  }

  // Dates, local time like Desktop's QDateTime

  private int dayKey (int date) {
    calendar.setTimeInMillis(date * 1000L);
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
  }

  /** Desktop's FormatDateTime: "01.11.2026 12:00:00", with the UTC offset when asked. */
  private String dateTime (int date, boolean withTimeZone) {
    final long ms = date * 1000L;
    final String text = dateTimeFormat.format(new Date(ms));
    if (!withTimeZone) {
      return text;
    }
    // On purpose the offset in effect on that date (with summer time): Desktop prints the zone's standard offset
    int offset = TimeZone.getDefault().getOffset(ms) / 60000;
    final char sign = offset < 0 ? '-' : '+';
    offset = Math.abs(offset);
    return text + " UTC" + sign + twoDigits(offset / 60) + ":" + twoDigits(offset % 60);
  }

  private static String twoDigits (int value) {
    return value < 10 ? "0" + value : String.valueOf(value);
  }

  // Writing: one space per level, as Desktop indents

  private void write (String text) throws IOException {
    if (w == null) {
      throw new IllegalStateException();
    }
    w.write(text);
  }

  private void indent () throws IOException {
    for (int i = 0; i < depth; i++) {
      write(" ");
    }
  }

  private void line (String html) throws IOException {
    indent();
    write(html);
    write("\n");
  }

  private void open (String tag) throws IOException {
    line(tag);
    depth++;
  }

  private void close (String name) throws IOException {
    depth = Math.max(0, depth - 1);
    line("</" + name + ">");
  }

  /** Desktop's SerializeString for HTML: line breaks become &lt;br&gt;, markup characters and control codes entities. */
  static String escape (@Nullable String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    final StringBuilder b = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      switch (c) {
        case '\n':
        case ' ':
        case ' ':
          b.append("<br>");
          break;
        case '"':
          b.append("&quot;");
          break;
        case '&':
          b.append("&amp;");
          break;
        case '\'':
          b.append("&apos;");
          break;
        case '<':
          b.append("&lt;");
          break;
        case '>':
          b.append("&gt;");
          break;
        default:
          if (c < 32) {
            b.append("&#x").append(HEX[c >> 4]).append(HEX[c & 0x0F]).append(';');
          } else {
            b.append(c);
          }
          break;
      }
    }
    return b.toString();
  }
}
