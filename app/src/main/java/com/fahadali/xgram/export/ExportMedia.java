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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import tgx.td.Td;

/**
 * The file of a message as Telegram Desktop exports it (export_data_types.cpp, export_api_wrap.cpp): which checkbox it
 * needs, its folder (photos/, video_files/, animations/, stickers/, voice_messages/, round_video_messages/, files/),
 * its name (the original one cleaned, else photo_N@date.jpg, video_N@…, audio_N@…, file_N@…, with a counter per kind)
 * and why it is left out. Thumbnails for the pages are made here from the downloaded file (photos, stickers, chat
 * photos), as Desktop does, so no second size is downloaded; documents keep Telegram's own JPEG thumbnail.
 */
final class ExportMedia {
  static final int KIND_PHOTO = 1;
  static final int KIND_VIDEO = 2;
  static final int KIND_ANIMATION = 3;
  static final int KIND_STICKER = 4;
  static final int KIND_VOICE = 5;
  static final int KIND_VIDEO_NOTE = 6;
  static final int KIND_AUDIO = 7;
  static final int KIND_DOCUMENT = 8;
  /** The photo of a "changed group photo" or "suggests to use this photo" service message. */
  static final int KIND_CHAT_PHOTO = 9;

  // Desktop's Data::File::SkipReason
  static final int SKIP_NONE = 0;
  /** The type is not checked in the options. */
  static final int SKIP_TYPE = 1;
  /** Over the size limit. */
  static final int SKIP_SIZE = 2;
  /** Not downloadable (can't be saved, failed, no space, the zip's limit, stopped). */
  static final int SKIP_UNAVAILABLE = 3;

  // Desktop's thumbnail sizes (export_output_html.cpp)
  static final int PHOTO_MAX = 520, STICKER_MAX = 384, THUMB_MIN = 80;
  /** The 60px photo of a service message, at 2x (kServiceMessagePhotoSize). */
  static final int SERVICE_PHOTO = 120;
  /** The side of Telegram's video thumbnails ("m"), for the ones made from a frame. */
  private static final int VIDEO_THUMB_MAX = 320;
  /** Desktop's kMaxImageSize: bigger images get no thumbnail. */
  private static final int MAX_IMAGE_SIDE = 10000;

  /** What a message's file is and whether the options include it; planned in pass 1 (totals) and pass 2. */
  static final class Plan {
    final int kind;
    /** The ExportOptions type it needs. */
    final int type;
    @NonNull
    final TdApi.File file;
    /** {@code size}, else {@code expectedSize}; 0 when unknown. */
    final long size;
    final int width, height, duration;
    /** The original file name ("" when there is none): JSON "file_name". */
    @NonNull
    final String fileName;
    @NonNull
    final String mimeType;
    @NonNull
    final String performer, title, emoji;
    /** Telegram's JPEG thumbnail of a document (a sticker's is WEBP, as in Desktop), or null. */
    @Nullable
    final TdApi.Thumbnail thumbnail;
    /**
     * {@code thumbnail} is the animated MPEG4 preview of a video or GIF (TDLib gives no JPEG then): its JPEG is a frame
     * of the downloaded file.
     */
    final boolean frameThumb;
    /** A WEBP sticker (TGS and WEBM stickers get Desktop's generic block). */
    final boolean staticSticker;
    final boolean spoiler;
    /** Self-destructing photo or video: Desktop's "Please view it on your mobile". */
    final boolean selfDestructing;
    /** The self-destruct timer in seconds, 0 when there is none. */
    final int selfDestructSeconds;
    /** Before any download: the type, the size limit, or content that can't be saved. */
    final int skip;

    Plan (int kind, int type, @NonNull TdApi.File file, int width, int height, int duration, @Nullable String fileName, @Nullable String mimeType,
          @Nullable String performer, @Nullable String title, @Nullable String emoji, @Nullable TdApi.Thumbnail thumbnail, boolean staticSticker,
          boolean spoiler, boolean selfDestructing, int selfDestructSeconds, int skip) {
      this.kind = kind;
      this.type = type;
      this.file = file;
      this.size = sizeOf(file);
      this.width = width;
      this.height = height;
      this.duration = duration;
      this.fileName = fileName != null ? fileName : "";
      this.mimeType = mimeType != null ? mimeType : "";
      this.performer = performer != null ? performer : "";
      this.title = title != null ? title : "";
      this.emoji = emoji != null ? emoji : "";
      this.thumbnail = thumbnail;
      this.frameThumb = thumbnail != null && thumbnail.format != null && thumbnail.format.getConstructor() == TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR;
      this.staticSticker = staticSticker;
      this.spoiler = spoiler;
      this.selfDestructing = selfDestructing;
      this.selfDestructSeconds = selfDestructSeconds;
      this.skip = skip;
    }

    boolean included () {
      return skip == SKIP_NONE;
    }
  }

  /** What became of a planned file: the writers read it after the page's media are in the .zip. Worker only. */
  static final class Result {
    @NonNull
    final Plan plan;
    int skip;
    /** The file in the export ("photos/photo_1@….jpg"), null when it was left out. */
    @Nullable
    String path;
    /** Photos, stickers, chat photos: the picture the page shows (a thumbnail, or the file itself) and its size. */
    @Nullable
    String imagePath;
    int imageWidth, imageHeight;
    /** Telegram's thumbnail of a document: JSON "thumbnail", the picture of videos, GIFs and video messages. */
    @Nullable
    String thumbPath;
    int thumbSkip;
    /** The size of the JPEG made from the file ({@link Plan#frameThumb}), 0 when there is none. */
    int thumbBytes;

    Result (@NonNull Plan plan) {
      this.plan = plan;
      this.skip = plan.skip;
      this.thumbSkip = plan.skip;
    }

    boolean hasThumbnail () {
      return plan.thumbnail != null && plan.thumbnail.width > 0;
    }

    long thumbnailSize () {
      if (thumbBytes > 0) {
        return thumbBytes;
      }
      return plan.thumbnail != null ? sizeOf(plan.thumbnail.file) : 0;
    }

    /** Copies what an earlier message with the same file got (the file goes into the .zip once). */
    void copyFrom (Result done) {
      skip = done.skip;
      path = done.path;
      imagePath = done.imagePath;
      imageWidth = done.imageWidth;
      imageHeight = done.imageHeight;
      thumbPath = done.thumbPath;
      thumbSkip = done.thumbSkip;
      thumbBytes = done.thumbBytes;
    }
  }

  static long sizeOf (@Nullable TdApi.File file) {
    return file == null ? 0 : file.size != 0 ? file.size : file.expectedSize;
  }

  /** Whether {@code content} has a file the export handles (and so a media block instead of a text line). */
  static boolean hasFile (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageSticker.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageVideoNote.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  /**
   * Whether {@code content} has a block of its own in the pages and keys in result.json (Desktop's polls, contacts,
   * locations, venues, games, invoices, paid media, giveaways, to-do lists; XGram's dice and stories; expired
   * self-destructing media), so it needs no text line when it has no text.
   */
  static boolean hasBlock (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePoll.CONSTRUCTOR:
      case TdApi.MessageChecklist.CONSTRUCTOR:
      case TdApi.MessageContact.CONSTRUCTOR:
      case TdApi.MessageLocation.CONSTRUCTOR:
      case TdApi.MessageLiveLocation.CONSTRUCTOR:
      case TdApi.MessageVenue.CONSTRUCTOR:
      case TdApi.MessageGame.CONSTRUCTOR:
      case TdApi.MessageInvoice.CONSTRUCTOR:
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
      case TdApi.MessageGiveaway.CONSTRUCTOR:
      case TdApi.MessageGiveawayWinners.CONSTRUCTOR:
      case TdApi.MessageDice.CONSTRUCTOR:
      case TdApi.MessageStakeDice.CONSTRUCTOR:
      case TdApi.MessageStory.CONSTRUCTOR:
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
      case TdApi.MessageExpiredVideoNote.CONSTRUCTOR:
      case TdApi.MessageExpiredVoiceNote.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }

  /**
   * The text Desktop exports for {@code content}: the text or caption, also of paid media, a game (its text, usually
   * the scores) and a poll (its description); an animated emoji is its emoji; a rich message its blocks' plain text.
   * Empty when there is none.
   */
  @NonNull
  static TdApi.FormattedText textOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePaidMedia.CONSTRUCTOR:
        return ExportText.orEmpty(((TdApi.MessagePaidMedia) content).caption);
      case TdApi.MessageGame.CONSTRUCTOR: {
        final TdApi.Game game = ((TdApi.MessageGame) content).game;
        return ExportText.orEmpty(game != null ? game.text : null);
      }
      case TdApi.MessagePoll.CONSTRUCTOR:
        return ExportText.orEmpty(((TdApi.MessagePoll) content).description);
      case TdApi.MessageRichMessage.CONSTRUCTOR: {
        // Desktop renders the blocks (RenderRichMessage, "rich_message"); XGram writes their text, one block a line
        final TdApi.RichMessage rich = ((TdApi.MessageRichMessage) content).message;
        final StringBuilder b = new StringBuilder();
        if (rich != null) {
          appendRichBlocks(b, rich.blocks);
        }
        return b.length() > 0 ? new TdApi.FormattedText(b.toString(), new TdApi.TextEntity[0]) : ExportText.EMPTY;
      }
      default:
        return ExportText.orEmpty(Td.textOrCaption(content));
    }
  }

  /** The text of {@code blocks} for {@link #textOf}, a line for each block with text; quotes, lists and details opened. */
  private static void appendRichBlocks (StringBuilder b, @Nullable TdApi.PageBlock[] blocks) {
    if (blocks == null) {
      return;
    }
    for (TdApi.PageBlock block : blocks) {
      if (block == null) {
        continue;
      }
      switch (block.getConstructor()) {
        case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
          final TdApi.PageBlockBlockQuote quote = (TdApi.PageBlockBlockQuote) block;
          appendRichBlocks(b, quote.blocks);
          appendRichLine(b, quote.credit);
          break;
        }
        case TdApi.PageBlockList.CONSTRUCTOR: {
          final TdApi.PageBlockListItem[] items = ((TdApi.PageBlockList) block).items;
          if (items != null) {
            for (TdApi.PageBlockListItem item : items) {
              if (item != null) {
                appendRichBlocks(b, item.blocks);
              }
            }
          }
          break;
        }
        case TdApi.PageBlockDetails.CONSTRUCTOR: {
          final TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block;
          appendRichLine(b, details.header);
          appendRichBlocks(b, details.blocks);
          break;
        }
        case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR: {
          final TdApi.PageBlockExpandableBlockQuote quote = (TdApi.PageBlockExpandableBlockQuote) block;
          appendRichLine(b, quote.text);
          appendRichLine(b, quote.credit);
          break;
        }
        case TdApi.PageBlockPullQuote.CONSTRUCTOR: {
          final TdApi.PageBlockPullQuote quote = (TdApi.PageBlockPullQuote) block;
          appendRichLine(b, quote.text);
          appendRichLine(b, quote.credit);
          break;
        }
        case TdApi.PageBlockTitle.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockTitle) block).title);
          break;
        case TdApi.PageBlockSubtitle.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockSubtitle) block).subtitle);
          break;
        case TdApi.PageBlockHeader.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockHeader) block).header);
          break;
        case TdApi.PageBlockSubheader.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockSubheader) block).subheader);
          break;
        case TdApi.PageBlockSectionHeading.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockSectionHeading) block).text);
          break;
        case TdApi.PageBlockKicker.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockKicker) block).kicker);
          break;
        case TdApi.PageBlockParagraph.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockParagraph) block).text);
          break;
        case TdApi.PageBlockPreformatted.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockPreformatted) block).text);
          break;
        case TdApi.PageBlockThinking.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockThinking) block).text);
          break;
        case TdApi.PageBlockFooter.CONSTRUCTOR:
          appendRichLine(b, ((TdApi.PageBlockFooter) block).footer);
          break;
        default:
          break;
      }
    }
  }

  private static void appendRichLine (StringBuilder b, @Nullable TdApi.RichText text) {
    final String line = text != null ? TD.getText(text) : "";
    if (!line.isEmpty()) {
      if (b.length() > 0) {
        b.append('\n');
      }
      b.append(line);
    }
  }

  /**
   * The file of {@code message} and whether it goes into the .zip, or null when it has none. Desktop's order:
   * self-destructing media, then a type that is not checked, then the size limit (every type, photos too,
   * export_api_wrap.cpp), then content that can't be saved. A file of unknown size is planned in; the download stops it
   * once it passes the limit.
   */
  @Nullable
  static Plan plan (TdApi.Message message, ExportOptions options) {
    final TdApi.MessageContent content = message.content;
    if (content == null) {
      return null;
    }
    final boolean timer = message.selfDestructType != null;
    final int timerSeconds = message.selfDestructType instanceof TdApi.MessageSelfDestructTypeTimer ?
      ((TdApi.MessageSelfDestructTypeTimer) message.selfDestructType).selfDestructTime : 0;
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        final TdApi.MessagePhoto photo = (TdApi.MessagePhoto) content;
        final TdApi.PhotoSize biggest = photo.photo != null && photo.photo.sizes != null ? Td.findBiggest(photo.photo.sizes) : null;
        if (biggest == null || biggest.photo == null) {
          return null;
        }
        final boolean secret = photo.isSecret || timer;
        return make(message, options, KIND_PHOTO, ExportOptions.TYPE_PHOTOS, biggest.photo, biggest.width, biggest.height, 0, null, "image/jpeg",
          null, null, null, null, false, photo.hasSpoiler, secret, timerSeconds);
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        final TdApi.MessageVideo video = (TdApi.MessageVideo) content;
        if (video.video == null || video.video.video == null) {
          return null;
        }
        final boolean secret = video.isSecret || timer;
        return make(message, options, KIND_VIDEO, ExportOptions.TYPE_VIDEOS, video.video.video, video.video.width, video.video.height, video.video.duration,
          video.video.fileName, video.video.mimeType, null, null, null, videoThumb(video.video.thumbnail), false, video.hasSpoiler, secret, timerSeconds);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        final TdApi.MessageAnimation animation = (TdApi.MessageAnimation) content;
        if (animation.animation == null || animation.animation.animation == null) {
          return null;
        }
        return make(message, options, KIND_ANIMATION, ExportOptions.TYPE_GIFS, animation.animation.animation, animation.animation.width, animation.animation.height,
          animation.animation.duration, animation.animation.fileName, animation.animation.mimeType, null, null, null, videoThumb(animation.animation.thumbnail),
          false, animation.hasSpoiler, animation.isSecret || timer, timerSeconds);
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        final TdApi.Sticker sticker = ((TdApi.MessageSticker) content).sticker;
        if (sticker == null || sticker.sticker == null) {
          return null;
        }
        final boolean webp = sticker.format == null || sticker.format.getConstructor() == TdApi.StickerFormatWebp.CONSTRUCTOR;
        return make(message, options, KIND_STICKER, ExportOptions.TYPE_STICKERS, sticker.sticker, sticker.width, sticker.height, 0,
          stickerFileName(sticker), stickerMimeType(sticker), null, null, sticker.emoji, stickerThumb(sticker), webp, false, false, 0);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        final TdApi.MessageVoiceNote voice = (TdApi.MessageVoiceNote) content;
        if (voice.voiceNote == null || voice.voiceNote.voice == null) {
          return null;
        }
        return make(message, options, KIND_VOICE, ExportOptions.TYPE_VOICE, voice.voiceNote.voice, 0, 0, voice.voiceNote.duration,
          null, voice.voiceNote.mimeType, null, null, null, null, false, false, timer, timerSeconds);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        final TdApi.MessageVideoNote note = (TdApi.MessageVideoNote) content;
        if (note.videoNote == null || note.videoNote.video == null) {
          return null;
        }
        final int side = note.videoNote.length;
        return make(message, options, KIND_VIDEO_NOTE, ExportOptions.TYPE_VIDEO_NOTES, note.videoNote.video, side, side, note.videoNote.duration,
          null, "video/mp4", null, null, null, jpeg(note.videoNote.thumbnail), false, false, note.isSecret || timer, timerSeconds);
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        final TdApi.Audio audio = ((TdApi.MessageAudio) content).audio;
        if (audio == null || audio.audio == null) {
          return null;
        }
        return make(message, options, KIND_AUDIO, ExportOptions.TYPE_FILES, audio.audio, 0, 0, audio.duration,
          audio.fileName, audio.mimeType, audio.performer, audio.title, null, jpeg(audio.albumCoverThumbnail), false, false, false, 0);
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        final TdApi.Document document = ((TdApi.MessageDocument) content).document;
        if (document == null || document.document == null) {
          return null;
        }
        return make(message, options, KIND_DOCUMENT, ExportOptions.TYPE_FILES, document.document, 0, 0, 0,
          document.fileName, document.mimeType, null, null, null, jpeg(document.thumbnail), false, false, false, 0);
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR: {
        // Desktop writes both photos the same way: "photo" keys after the action, a 60px picture after the line
        final TdApi.ChatPhoto photo = content.getConstructor() == TdApi.MessageChatChangePhoto.CONSTRUCTOR ?
          ((TdApi.MessageChatChangePhoto) content).photo : ((TdApi.MessageSuggestProfilePhoto) content).photo;
        final TdApi.PhotoSize biggest = photo != null && photo.sizes != null ? Td.findBiggest(photo.sizes) : null;
        if (biggest == null || biggest.photo == null) {
          return null;
        }
        return make(message, options, KIND_CHAT_PHOTO, ExportOptions.TYPE_PHOTOS, biggest.photo, biggest.width, biggest.height, 0,
          null, "image/jpeg", null, null, null, null, false, false, false, 0);
      }
      default:
        return null;
    }
  }

  private static Plan make (TdApi.Message message, ExportOptions options, int kind, int type, TdApi.File file, int width, int height, int duration,
                            @Nullable String fileName, @Nullable String mimeType, @Nullable String performer, @Nullable String title, @Nullable String emoji,
                            @Nullable TdApi.Thumbnail thumbnail, boolean staticSticker, boolean spoiler, boolean selfDestructing, int selfDestructSeconds) {
    final long size = sizeOf(file);
    final int skip;
    if (selfDestructing) {
      skip = SKIP_UNAVAILABLE; // Desktop clears these files, so they are unavailable before the type is checked
    } else if (!options.includes(type)) {
      skip = SKIP_TYPE;
    } else if (size > options.sizeLimitBytes) {
      skip = SKIP_SIZE;
    } else if (!options.canSave(message) || (file.local != null && !file.local.canBeDownloaded && !file.local.isDownloadingCompleted)) {
      skip = SKIP_UNAVAILABLE;
    } else {
      skip = SKIP_NONE;
    }
    return new Plan(kind, type, file, width, height, duration, fileName, mimeType, performer, title, emoji, thumbnail, staticSticker,
      spoiler, selfDestructing, selfDestructSeconds, skip);
  }

  /** Desktop names a document's thumbnail "…_thumb.jpg": only Telegram's JPEG thumbnails are kept. */
  @Nullable
  private static TdApi.Thumbnail jpeg (@Nullable TdApi.Thumbnail thumbnail) {
    return thumbnail != null && thumbnail.file != null && thumbnail.format != null &&
      thumbnail.format.getConstructor() == TdApi.ThumbnailFormatJpeg.CONSTRUCTOR ? thumbnail : null;
  }

  /**
   * A video's or GIF's thumbnail: the JPEG, or the animated MPEG4 preview TDLib gives instead when the sender added
   * one (Plan.frameThumb: a frame of the file then makes the JPEG).
   */
  @Nullable
  private static TdApi.Thumbnail videoThumb (@Nullable TdApi.Thumbnail thumbnail) {
    if (thumbnail != null && thumbnail.file != null && thumbnail.format != null &&
      thumbnail.format.getConstructor() == TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR) {
      return thumbnail;
    }
    return jpeg(thumbnail);
  }

  /** A sticker's thumbnail (WEBP, JPEG in secret chats): Desktop keeps it as "<file>_thumb.jpg" whatever its format. */
  @Nullable
  private static TdApi.Thumbnail stickerThumb (TdApi.Sticker sticker) {
    final TdApi.Thumbnail thumbnail = sticker.thumbnail;
    if (thumbnail == null || thumbnail.file == null || thumbnail.format == null) {
      return null;
    }
    switch (thumbnail.format.getConstructor()) {
      case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
      case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
        return thumbnail;
      default:
        return null;
    }
  }

  /** The names Telegram's sticker files carry (Desktop takes them from the file name attribute). */
  private static String stickerFileName (TdApi.Sticker sticker) {
    if (sticker.format != null) {
      switch (sticker.format.getConstructor()) {
        case TdApi.StickerFormatTgs.CONSTRUCTOR:
          return "AnimatedSticker.tgs";
        case TdApi.StickerFormatWebm.CONSTRUCTOR:
          return "sticker.webm";
      }
    }
    return "sticker.webp";
  }

  private static String stickerMimeType (TdApi.Sticker sticker) {
    if (sticker.format != null) {
      switch (sticker.format.getConstructor()) {
        case TdApi.StickerFormatTgs.CONSTRUCTOR:
          return "application/x-tgsticker";
        case TdApi.StickerFormatWebm.CONSTRUCTOR:
          return "video/webm";
      }
    }
    return "image/webp";
  }

  // Names: one instance per export (the counters), worker only

  private final SimpleDateFormat datePart = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US);
  private int photos, videos, audios, files;

  /** The file's path in the export, before {@link ExportZip#uniquePath}. */
  @NonNull
  String pathOf (Plan plan, int messageDate) {
    final String date = "@" + datePart.format(new Date(messageDate * 1000L));
    switch (plan.kind) {
      case KIND_PHOTO:
      case KIND_CHAT_PHOTO:
        return "photos/photo_" + (++photos) + date + ".jpg";
      case KIND_VIDEO:
        return "video_files/" + named(plan, () -> "video_" + (++videos) + date + extension(plan.mimeType, ".mov"));
      case KIND_ANIMATION:
        // Desktop: an MP4 GIF carries the video attribute too, which puts it with the videos; a real .gif is animated only
        if (plan.mimeType.startsWith("video/")) {
          return "video_files/" + named(plan, () -> "video_" + (++videos) + date + extension(plan.mimeType, ".mov"));
        }
        return "animations/" + named(plan, () -> "file_" + (++files) + date + extension(plan.mimeType, ".unknown"));
      case KIND_STICKER:
        // Desktop: a WEBM sticker carries the video attribute, which puts it with the videos (it stays a sticker otherwise)
        return (plan.mimeType.startsWith("video/") ? "video_files/" : "stickers/") +
          named(plan, () -> "file_" + (++files) + date + extension(plan.mimeType, ".unknown"));
      case KIND_VOICE:
        return "voice_messages/audio_" + (++audios) + date + ("audio/mp3".equalsIgnoreCase(plan.mimeType) ? ".mp3" : ".ogg");
      case KIND_VIDEO_NOTE:
        return "round_video_messages/" + named(plan, () -> "file_" + (++files) + date + extension(plan.mimeType, ".unknown"));
      case KIND_AUDIO:
      case KIND_DOCUMENT:
      default:
        return "files/" + named(plan, () -> "file_" + (++files) + date + extension(plan.mimeType, ".unknown"));
    }
  }

  private interface NameSupplier {
    String get ();
  }

  /** The original name, cleaned; the numbered one (which counts) only when there is none. */
  private static String named (Plan plan, NameSupplier generated) {
    final String clean = cleanFileName(plan.fileName);
    return clean.isEmpty() ? cleanFileName(generated.get()) : clean;
  }

  /** ".mp4" for "video/mp4" (Desktop takes the MIME type's first pattern), else {@code fallback}. */
  private static String extension (String mimeType, String fallback) {
    if (mimeType.isEmpty()) {
      return fallback;
    }
    String ext = null;
    try {
      ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.toLowerCase(Locale.ROOT));
    } catch (Throwable ignored) { }
    return ext != null && !ext.isEmpty() ? "." + ext : fallback;
  }

  /** UTF-8 bytes a name may take: ext4 and f2fs refuse more than 255, and " (2)" or "_thumb.jpg" may still be added. */
  private static final int MAX_NAME_BYTES = 200;
  /** Bidi marks, embeddings, overrides and isolates, as Desktop: with U+202E "invoice?fdp.exe" shows as "invoiceexe.pdf". */
  private static final String BIDI_CONTROLS = "\u200E\u200F\u202A\u202B\u202D\u202E\u2066\u2067";
  /** Windows' device names: such a file can't be unpacked there, so Desktop puts "_" before it. */
  private static final Pattern RESERVED_NAME = Pattern.compile("^(CON|PRN|AUX|NUL|COM\\d|LPT\\d)([\\s.]|$)", Pattern.CASE_INSENSITIVE);

  /**
   * Desktop's FileNameFromUserString: characters file systems refuse ({@code \/:*?"<>|} and control codes) and bidi
   * controls become "_", leading spaces and trailing dots and spaces go, Windows' device names get "_" before them,
   * and a long name is cut to 200 UTF-8 bytes keeping its extension (".gitignore" keeps its dot).
   */
  @NonNull
  static String cleanFileName (@Nullable String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }
    final StringBuilder b = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0 || BIDI_CONTROLS.indexOf(c) >= 0) {
        b.append('_');
      } else {
        b.append(c);
      }
    }
    int start = 0, end = b.length();
    while (start < end && b.charAt(start) == ' ') {
      start++;
    }
    while (end > start && (b.charAt(end - 1) == '.' || b.charAt(end - 1) == ' ')) {
      end--;
    }
    String clean = b.substring(start, end);
    if (utf8Length(clean) > MAX_NAME_BYTES) {
      final int dot = clean.lastIndexOf('.');
      final String ext = dot > 0 && clean.length() - dot <= 16 ? clean.substring(dot) : "";
      final int budget = MAX_NAME_BYTES - utf8Length(ext), baseEnd = clean.length() - ext.length();
      int cut = 0, bytes = 0;
      while (cut < baseEnd) {
        final int codePoint = clean.codePointAt(cut); // whole code points: never half a surrogate pair
        bytes += utf8Length(codePoint);
        if (bytes > budget) {
          break;
        }
        cut += Character.charCount(codePoint);
      }
      clean = clean.substring(0, cut).trim() + ext;
    }
    if (RESERVED_NAME.matcher(clean).find()) {
      clean = "_" + clean;
    }
    return clean;
  }

  private static int utf8Length (String s) {
    int bytes = 0;
    for (int i = 0; i < s.length(); ) {
      final int codePoint = s.codePointAt(i);
      bytes += utf8Length(codePoint);
      i += Character.charCount(codePoint);
    }
    return bytes;
  }

  private static int utf8Length (int codePoint) {
    return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
  }

  // Thumbnails

  /** A picture for the page: encoded bytes (null: the file itself is small enough) and its pixel size. */
  static final class Thumb {
    @Nullable
    final byte[] data;
    final int width, height;

    Thumb (@Nullable byte[] data, int width, int height) {
      this.data = data;
      this.width = width;
      this.height = height;
    }
  }

  /**
   * Desktop's CalculateThumbSize: {@code width}×{@code height} (doubled for retina when asked) fit into
   * {@code max}×{@code max} keeping the aspect ratio, rounded down to even numbers; null below 80×80.
   */
  @Nullable
  static int[] thumbSize (int width, int height, int max, boolean retina) {
    if (width <= 0 || height <= 0) {
      return null;
    }
    final int factor = retina ? 2 : 1;
    int w = width, h = height;
    if ((long) width * factor > max || (long) height * factor > max) {
      // QSize::scaled(max, max, Qt::KeepAspectRatio)
      final long rw = (long) max * width / height;
      if (rw <= max) {
        w = (int) rw;
        h = max;
      } else {
        w = max;
        h = (int) ((long) max * height / width);
      }
    }
    w &= ~1;
    h &= ~1;
    if (w < THUMB_MIN || h < THUMB_MIN) {
      return null;
    }
    return new int[] {w, h};
  }

  /**
   * The page's picture of a downloaded photo (max 520) or WEBP sticker (max 384, PNG as Desktop writes it): null when
   * it can't be read or is too small (Desktop's generic block then); no data when the file itself already fits.
   */
  @Nullable
  @WorkerThread
  static Thumb imageThumb (File file, int max, boolean png) {
    final BitmapFactory.Options bounds = readBounds(file);
    if (bounds == null) {
      return null;
    }
    final int[] size = thumbSize(bounds.outWidth, bounds.outHeight, max, false);
    if (size == null) {
      return null;
    }
    if (size[0] == bounds.outWidth && size[1] == bounds.outHeight) {
      return new Thumb(null, size[0], size[1]); // no smaller copy needed: the page shows the file
    }
    final byte[] data = scaled(file, bounds, size[0], size[1], png);
    return data != null ? new Thumb(data, size[0], size[1]) : null;
  }

  /** A service message's chat photo: 120×120, as Desktop's WriteUserpicThumb. */
  @Nullable
  @WorkerThread
  static Thumb servicePhotoThumb (File file) {
    final BitmapFactory.Options bounds = readBounds(file);
    if (bounds == null) {
      return null;
    }
    final byte[] data = scaled(file, bounds, SERVICE_PHOTO, SERVICE_PHOTO, false);
    return data != null ? new Thumb(data, SERVICE_PHOTO, SERVICE_PHOTO) : null;
  }

  /**
   * The JPEG thumbnail of a video or GIF that has only an animated preview ({@link Plan#frameThumb}): a frame of the
   * file at most 320 px wide or high (Telegram's own thumbnail size), JPEG 80. Null when the file can't be read.
   */
  @Nullable
  @WorkerThread
  static byte[] videoFrameThumb (File file) {
    Bitmap frame = null, result = null;
    try {
      frame = U.tryDecodeVideoThumb(file.getPath(), -1, VIDEO_THUMB_MAX, VIDEO_THUMB_MAX, null); // scaled on Android 8.1+
      if (frame == null) {
        return null;
      }
      final int width = frame.getWidth(), height = frame.getHeight();
      final float scale = Math.min(1f, (float) VIDEO_THUMB_MAX / Math.max(width, height));
      final int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
      result = w == width && h == height ? frame : Bitmap.createScaledBitmap(frame, w, h, true);
      final ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
      return result.compress(Bitmap.CompressFormat.JPEG, 80, out) ? out.toByteArray() : null;
    } catch (Throwable t) {
      return null; // out of memory (a full frame below Android 8.1) or a broken file: the generic block
    } finally {
      if (result != null && result != frame) {
        result.recycle();
      }
      if (frame != null) {
        frame.recycle();
      }
    }
  }

  @Nullable
  private static BitmapFactory.Options readBounds (File file) {
    final BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    try {
      BitmapFactory.decodeFile(file.getPath(), bounds);
    } catch (Throwable t) {
      return null;
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth >= MAX_IMAGE_SIDE || bounds.outHeight >= MAX_IMAGE_SIDE) {
      return null;
    }
    return bounds;
  }

  /** Decodes at the smallest power-of-two sample that is still at least the target, then scales to it exactly. */
  @Nullable
  private static byte[] scaled (File file, BitmapFactory.Options bounds, int width, int height, boolean png) {
    int sample = 1;
    while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
      sample *= 2;
    }
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    Bitmap decoded = null, result = null;
    try {
      decoded = BitmapFactory.decodeFile(file.getPath(), options);
      if (decoded == null) {
        return null;
      }
      result = decoded.getWidth() == width && decoded.getHeight() == height ? decoded : Bitmap.createScaledBitmap(decoded, width, height, true);
      final ByteArrayOutputStream out = new ByteArrayOutputStream(png ? 64 * 1024 : 32 * 1024);
      if (!result.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 80, out)) {
        return null;
      }
      return out.toByteArray();
    } catch (Throwable t) {
      return null; // out of memory or a broken file: the generic block
    } finally {
      if (result != null && result != decoded) {
        result.recycle();
      }
      if (decoded != null) {
        decoded.recycle();
      }
    }
  }

  /** Desktop's thumbnail path: "_thumb" before the first dot of the file name ("sticker_thumb.webp"). */
  static String thumbPathOf (String path) {
    final int slash = path.lastIndexOf('/');
    final int dot = path.indexOf('.', slash + 1);
    return dot >= 0 ? path.substring(0, dot) + "_thumb" + path.substring(dot) : path + "_thumb";
  }
}
