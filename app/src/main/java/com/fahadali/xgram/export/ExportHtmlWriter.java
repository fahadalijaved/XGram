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

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
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
 * that holds the message. Desktop's English page texts are kept as they are.
 */
final class ExportHtmlWriter {
  private static final int JOIN_WITHIN_SECONDS = 900; // kJoinWithinSeconds
  private static final int USERPIC_SIZE = 42; // kHistoryUserpicSize
  private static final int SERVICE_PHOTO_SIZE = 60; // kServiceMessagePhotoSize
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final ExportZip zip;
  private final String root;
  private final ExportPeers peers;
  private final ExportIndex index;
  private final ExportPeers.Dialog dialog;
  /** The page header: the chat's name, and the topic's for a topic export. */
  private final String headerName;
  private final long chatId;
  private final long entryTimeMs;

  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
  private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
  private final SimpleDateFormat dayFormat = new SimpleDateFormat("d MMMM yyyy", Locale.US);
  private final Calendar calendar = Calendar.getInstance();

  @Nullable
  private Writer w;
  private int depth;
  /** Desktop's _dateMessageId: date lines are "message-1", "message-2", … through the whole chat. */
  private int dateMessageId;

  ExportHtmlWriter (ExportZip zip, String root, ExportPeers peers, ExportIndex index, ExportPeers.Dialog dialog, @NonNull String topicTitle, long chatId, long entryTimeMs) {
    this.zip = zip;
    this.root = root;
    this.peers = peers;
    this.index = index;
    this.dialog = dialog;
    this.headerName = topicTitle.isEmpty() ? dialog.htmlName : dialog.htmlName + " · " + topicTitle;
    this.chatId = chatId;
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
    final boolean forwarded, forwardedFromUser;
    final long forwardedKey;
    @NonNull
    final String forwardedName;
    final int forwardedDate;

    Info (boolean service, long fromKey, long viaBotId, int date, int dayKey, boolean forwarded, long forwardedKey, @NonNull String forwardedName, int forwardedDate, boolean forwardedFromUser) {
      this.service = service;
      this.fromKey = fromKey;
      this.viaBotId = viaBotId;
      this.date = date;
      this.dayKey = dayKey;
      this.forwarded = forwarded;
      this.forwardedKey = forwardedKey;
      this.forwardedName = forwardedName;
      this.forwardedDate = forwardedDate;
      this.forwardedFromUser = forwardedFromUser;
    }

    static Info service (int date, int dayKey) {
      return new Info(true, 0, 0, date, dayKey, false, 0, "", 0, false);
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
        writeService(String.valueOf(item.serverId), escape(serviceText(item)), item.media);
        return Info.service(message.date, dayKey);
      default:
        return writeMessage(item, previous, dayKey, page);
    }
  }

  /**
   * For now Telegram X's preview line after "actor: ", unless the line names the people itself (or it is a channel);
   * Desktop's sentences built from real names follow the actor with a space ("Alice invited Bob").
   */
  private String serviceText (ExportJob.Item item) {
    final String text = item.fallback.isEmpty() ? "Service message" : item.fallback;
    if (item.hideAuthor || item.fallback.isEmpty() || (dialog.isChannel && !item.afterActor)) {
      return text;
    }
    return peers.sender(item.message.senderId).displayName() + (item.afterActor ? " " : ": ") + text;
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
    final Info info = new Info(false, from.key, message.viaBotUserId, message.date, dayKey, forwarded,
      origin != null ? origin.key : 0, hiddenName, forwarded ? forward.date : 0, origin != null && origin.isUser);
    final boolean wrap = needsWrap(info, previous);
    final String via = message.viaBotUserId != 0 ? peers.user(message.viaBotUserId).username : "";

    open("<div class=\"message default clearfix" + (wrap ? "" : " joined") + "\" id=\"message" + item.serverId + "\">");
    if (wrap) {
      open("<div class=\"pull_left userpic_wrap\">");
      userpic(from.colorClass(), from.initials());
      close("div");
    }
    open("<div class=\"body\">");
    line("<div class=\"pull_right date details\" title=\"" + escape(dateTime(message.date, true)) + "\">" + timeFormat.format(new Date(message.date * 1000L)) + "</div>");
    if (wrap) {
      line("<div class=\"from_name\">" + escape(from.displayName()) + (!via.isEmpty() && !forwarded ? " via @" + escape(via) : "") + "</div>");
    }
    if (forwarded) {
      final boolean forwardedWrap = wrap || forwardedNeedsWrap(info, previous);
      if (forwardedWrap) {
        final String name;
        final int color;
        final String initials;
        if (origin != null) {
          name = origin.displayName();
          color = origin.colorClass();
          initials = origin.initials();
        } else {
          name = hiddenName.isEmpty() ? ExportPeers.DELETED_ACCOUNT : hiddenName;
          color = ExportPeers.colorClass(item.serverId); // Desktop colours a hidden sender by the message id
          initials = initialsOf(hiddenName);
        }
        open("<div class=\"pull_left forwarded userpic_wrap\">");
        userpic(color, initials);
        close("div");
        open("<div class=\"forwarded body\">");
        line("<div class=\"from_name\">" + escape(name) + (!via.isEmpty() ? " via @" + escape(via) : "") +
          "<span class=\"date details\" title=\"" + escape(dateTime(forward.date, true)) + "\"> " + escape(dateTime(forward.date, false)) + "</span></div>");
      } else {
        open("<div class=\"forwarded body\">");
      }
    }
    writeReply(message, page);
    writeMedia(item.media);
    final String text = !item.text.isEmpty() ? item.text : item.fallback;
    if (!text.isEmpty()) {
      line("<div class=\"text\">" + escape(text) + "</div>");
    }
    if (message.authorSignature != null && !message.authorSignature.isEmpty()) {
      line("<div class=\"signature details\">" + escape(message.authorSignature) + "</div>");
    }
    if (forwarded) {
      close("div"); // forwarded body
    }
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
    if (info.forwarded != previous.forwarded || info.forwardedKey != previous.forwardedKey || !info.forwardedName.equals(previous.forwardedName)) {
      return true;
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

  private void userpic (int colorClass, String initials) throws IOException {
    final String size = USERPIC_SIZE + "px";
    open("<div class=\"userpic userpic" + colorClass + "\" style=\"width: " + size + "; height: " + size + "\">");
    line("<div class=\"initials\" style=\"line-height: " + size + "\">" + escape(initials) + "</div>");
    close("div");
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
    open("<div class=\"media_wrap clearfix\">");
    if (link != null) {
      open("<a class=\"media clearfix pull_left block_link " + classes + "\" href=\"" + url(link) + "\">");
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
