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
package com.fahadali.xgram.history;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Media and formatting of saved messages: which file a message has, copies of files of deleted messages (TDLib may
 * clean its cache up later), formatting stored as text, and message content rebuilt from all that.
 */
public final class XgramMedia {
  private XgramMedia () { }

  /** The main file of a message and what is needed to show it again. */
  public static final class Info {
    public final int fileId;
    public final String fileName, mimeType;
    public final int width, height;
    public final long size;

    Info (int fileId, String fileName, String mimeType, int width, int height, long size) {
      this.fileId = fileId;
      this.fileName = fileName != null ? fileName : "";
      this.mimeType = mimeType != null ? mimeType : "";
      this.width = width;
      this.height = height;
      this.size = size;
    }
  }

  /**
   * Photos: the file ids of the other sizes, biggest first, comma separated. A photo seen in the chat but never opened
   * full size only has a smaller size downloaded, which is kept when the largest one isn't there.
   */
  public static String fallbackFileIdsOf (TdApi.MessageContent content) {
    if (content.getConstructor() != TdApi.MessagePhoto.CONSTRUCTOR) {
      return "";
    }
    TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) content).photo.sizes;
    if (sizes.length < 2) {
      return "";
    }
    TdApi.PhotoSize[] others = java.util.Arrays.copyOf(sizes, sizes.length - 1);
    java.util.Arrays.sort(others, (a, b) -> Long.compare((long) b.width * b.height, (long) a.width * a.height));
    StringBuilder b = new StringBuilder();
    for (TdApi.PhotoSize size : others) {
      if (size.photo == null || size.photo.id <= 0) {
        continue;
      }
      if (b.length() > 0) {
        b.append(',');
      }
      b.append(size.photo.id);
    }
    return b.toString();
  }

  @Nullable
  public static Info infoOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) content).photo.sizes;
        if (sizes.length == 0) {
          return null;
        }
        TdApi.PhotoSize size = sizes[sizes.length - 1];
        return new Info(size.photo.id, "photo.jpg", "image/jpeg", size.width, size.height, size.photo.size);
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video video = ((TdApi.MessageVideo) content).video;
        return new Info(video.video.id, orDefault(video.fileName, "video.mp4"), orDefault(video.mimeType, "video/mp4"), video.width, video.height, video.video.size);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) content).animation;
        return new Info(animation.animation.id, orDefault(animation.fileName, "animation.mp4"), orDefault(animation.mimeType, "video/mp4"), animation.width, animation.height, animation.animation.size);
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) content).document;
        return new Info(document.document.id, orDefault(document.fileName, "file"), orDefault(document.mimeType, "application/octet-stream"), 0, 0, document.document.size);
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) content).audio;
        return new Info(audio.audio.id, orDefault(audio.fileName, "audio.mp3"), orDefault(audio.mimeType, "audio/mpeg"), 0, 0, audio.audio.size);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) content).voiceNote;
        return new Info(voiceNote.voice.id, "voice.ogg", orDefault(voiceNote.mimeType, "audio/ogg"), 0, 0, voiceNote.voice.size);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) content).videoNote;
        return new Info(videoNote.video.id, "video_message.mp4", "video/mp4", videoNote.length, videoNote.length, videoNote.video.size);
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) content).sticker;
        if (sticker.format.getConstructor() != TdApi.StickerFormatWebp.CONSTRUCTOR) {
          return null; // animated stickers come back as their emoji
        }
        return new Info(sticker.sticker.id, "sticker.webp", "image/webp", sticker.width, sticker.height, sticker.sticker.size);
      }
      default:
        return null;
    }
  }

  private static String orDefault (@Nullable String value, String defaultValue) {
    return value == null || value.isEmpty() ? defaultValue : value;
  }

  @Nullable
  public static TdApi.FormattedText formattedTextOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: return ((TdApi.MessageText) content).text;
      case TdApi.MessagePhoto.CONSTRUCTOR: return ((TdApi.MessagePhoto) content).caption;
      case TdApi.MessageVideo.CONSTRUCTOR: return ((TdApi.MessageVideo) content).caption;
      case TdApi.MessageAnimation.CONSTRUCTOR: return ((TdApi.MessageAnimation) content).caption;
      case TdApi.MessageDocument.CONSTRUCTOR: return ((TdApi.MessageDocument) content).caption;
      case TdApi.MessageAudio.CONSTRUCTOR: return ((TdApi.MessageAudio) content).caption;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) content).caption;
      default: return null;
    }
  }

  // Files

  public static File savedDir () {
    Context context = UI.getContext().getApplicationContext();
    File dir = new File(context.getFilesDir(), "xgram_deleted_media");
    if (!dir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
    }
    return dir;
  }

  /** Copies a downloaded file of a deleted message. Returns the copy's path, or null. */
  @Nullable
  public static String copy (String sourcePath, int accountId, long chatId, long messageId, String fileName) {
    File source = new File(sourcePath);
    if (!source.isFile()) {
      return null;
    }
    String extension = "";
    int dot = fileName.lastIndexOf('.');
    if (dot != -1 && dot < fileName.length() - 1 && fileName.length() - dot <= 6) {
      extension = fileName.substring(dot);
    }
    File target = new File(savedDir(), accountId + "_" + chatId + "_" + messageId + extension);
    try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return target.getPath();
    } catch (Throwable t) {
      //noinspection ResultOfMethodCallIgnored
      target.delete();
      return null;
    }
  }

  public static void deleteFile (@Nullable String path) {
    if (path != null && !path.isEmpty()) {
      //noinspection ResultOfMethodCallIgnored
      new File(path).delete();
    }
  }

  /** A file id Telegram X's loaders treat as a local file, stable for the same path. */
  private static int localFileId (String path) {
    return Integer.MIN_VALUE / 2 - (path.hashCode() & 0x3FFFFFFF);
  }

  private static TdApi.File localFile (String path) {
    long size = new File(path).length();
    return new TdApi.File(localFileId(path), size, size,
      new TdApi.LocalFile(path, false, false, false, true, 0, size, size),
      new TdApi.RemoteFile("", "", false, false, 0));
  }

  /**
   * Content for a deleted message put back into a chat. With a saved file: a photo for photos and static stickers,
   * a document (tap to open) for everything else. Without one: text with a media label.
   */
  public static TdApi.MessageContent buildContent (XgramHistory.Entry entry) {
    TdApi.FormattedText text = new TdApi.FormattedText(entry.text, decodeEntities(entry.entities, entry.text.length()));
    if (entry.mediaPath != null && !entry.mediaPath.isEmpty() && new File(entry.mediaPath).isFile()) {
      TdApi.File file = localFile(entry.mediaPath);
      switch (entry.kind) {
        case XgramHistory.Kind.PHOTO:
        case XgramHistory.Kind.STICKER: {
          int width = entry.width > 0 ? entry.width : 512, height = entry.height > 0 ? entry.height : 512;
          TdApi.Photo photo = new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("x", file, width, height, new int[0])});
          TdApi.FormattedText caption = XgramHistory.Kind.STICKER.equals(entry.kind) ? new TdApi.FormattedText("", new TdApi.TextEntity[0]) : text;
          return new TdApi.MessagePhoto(photo, null, caption, false, false, false);
        }
        default: {
          TdApi.Document document = new TdApi.Document(entry.fileName, entry.mimeType, null, null, file);
          return new TdApi.MessageDocument(document, text);
        }
      }
    }
    String label = XgramHistory.mediaLabel(entry.kind);
    if (label == null) {
      return new TdApi.MessageText(text, null, null);
    }
    String prefix = entry.text.isEmpty() ? label : label + "\n";
    // Keep the formatting: shift entities past the label.
    TdApi.TextEntity[] entities = text.entities;
    for (TdApi.TextEntity entity : entities) {
      entity.offset += prefix.length();
    }
    return new TdApi.MessageText(new TdApi.FormattedText(prefix + entry.text, entities), null, null);
  }

  // Formatting

  /** Entities as "type,offset,length[,value]" separated by ";", values URL-encoded. */
  public static String encodeEntities (@Nullable TdApi.TextEntity[] entities) {
    if (entities == null || entities.length == 0) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    for (TdApi.TextEntity entity : entities) {
      String code, value = null;
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeBold.CONSTRUCTOR: code = "b"; break;
        case TdApi.TextEntityTypeItalic.CONSTRUCTOR: code = "i"; break;
        case TdApi.TextEntityTypeUnderline.CONSTRUCTOR: code = "u"; break;
        case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR: code = "s"; break;
        case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR: code = "sp"; break;
        case TdApi.TextEntityTypeCode.CONSTRUCTOR: code = "c"; break;
        case TdApi.TextEntityTypePre.CONSTRUCTOR: code = "p"; break;
        case TdApi.TextEntityTypePreCode.CONSTRUCTOR: code = "pc"; value = ((TdApi.TextEntityTypePreCode) entity.type).language; break;
        case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR: code = "q"; break;
        case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR: code = "eq"; break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: code = "tu"; value = ((TdApi.TextEntityTypeTextUrl) entity.type).url; break;
        case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: code = "mn"; value = Long.toString(((TdApi.TextEntityTypeMentionName) entity.type).userId); break;
        case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR: code = "ce"; value = Long.toString(((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId); break;
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR: code = "url"; break;
        case TdApi.TextEntityTypeMention.CONSTRUCTOR: code = "m"; break;
        case TdApi.TextEntityTypeHashtag.CONSTRUCTOR: code = "h"; break;
        case TdApi.TextEntityTypeCashtag.CONSTRUCTOR: code = "ch"; break;
        case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR: code = "bc"; break;
        case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: code = "e"; break;
        case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR: code = "ph"; break;
        default: continue;
      }
      if (b.length() > 0) {
        b.append(';');
      }
      b.append(code).append(',').append(entity.offset).append(',').append(entity.length);
      if (value != null) {
        b.append(',').append(Uri.encode(value));
      }
    }
    return b.toString();
  }

  public static TdApi.TextEntity[] decodeEntities (@Nullable String encoded, int textLength) {
    if (encoded == null || encoded.isEmpty()) {
      return new TdApi.TextEntity[0];
    }
    List<TdApi.TextEntity> result = new ArrayList<>();
    for (String part : encoded.split(";")) {
      String[] fields = part.split(",", 4);
      if (fields.length < 3) {
        continue;
      }
      try {
        int offset = Integer.parseInt(fields[1]), length = Integer.parseInt(fields[2]);
        if (offset < 0 || length <= 0 || offset + length > textLength) {
          continue;
        }
        String value = fields.length > 3 ? Uri.decode(fields[3]) : null;
        TdApi.TextEntityType type;
        switch (fields[0]) {
          case "b": type = new TdApi.TextEntityTypeBold(); break;
          case "i": type = new TdApi.TextEntityTypeItalic(); break;
          case "u": type = new TdApi.TextEntityTypeUnderline(); break;
          case "s": type = new TdApi.TextEntityTypeStrikethrough(); break;
          case "sp": type = new TdApi.TextEntityTypeSpoiler(); break;
          case "c": type = new TdApi.TextEntityTypeCode(); break;
          case "p": type = new TdApi.TextEntityTypePre(); break;
          case "pc": type = new TdApi.TextEntityTypePreCode(value != null ? value : ""); break;
          case "q": type = new TdApi.TextEntityTypeBlockQuote(); break;
          case "eq": type = new TdApi.TextEntityTypeExpandableBlockQuote(); break;
          case "tu": type = new TdApi.TextEntityTypeTextUrl(value != null ? value : ""); break;
          case "mn": type = new TdApi.TextEntityTypeMentionName(value != null ? Long.parseLong(value) : 0); break;
          case "ce": type = new TdApi.TextEntityTypeCustomEmoji(value != null ? Long.parseLong(value) : 0); break;
          case "url": type = new TdApi.TextEntityTypeUrl(); break;
          case "m": type = new TdApi.TextEntityTypeMention(); break;
          case "h": type = new TdApi.TextEntityTypeHashtag(); break;
          case "ch": type = new TdApi.TextEntityTypeCashtag(); break;
          case "bc": type = new TdApi.TextEntityTypeBotCommand(); break;
          case "e": type = new TdApi.TextEntityTypeEmailAddress(); break;
          case "ph": type = new TdApi.TextEntityTypePhoneNumber(); break;
          default: continue;
        }
        result.add(new TdApi.TextEntity(offset, length, type));
      } catch (NumberFormatException ignored) { }
    }
    return result.toArray(new TdApi.TextEntity[0]);
  }
}
