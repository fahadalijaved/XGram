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
package com.fahadali.xgram.editor;

import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.StringUtils;

/**
 * A photo edit with a GIF or an animated sticker on it ({@link XgramStickerLayer#isAnimated()}) is sent as a GIF: a
 * muted MP4 that TDLib asks the app to make from the edit ({@link XgramAnimatedEditExport}, an InputFileGenerated with
 * {@link #CONVERSION_PREFIX} and the photo's own conversion), in an InputMessageAnimation whose thumbnail is the edited
 * photo. It plays as long as its longest animated layer's loop ({@link #computeDuration}). A GIF can't be in an album:
 * among several photos it goes after them, as a message of its own ({@link #toFunctions}). As a file, with a
 * self-destruct timer, where GIFs aren't allowed, as a profile photo, when replacing a message's media and in the
 * legacy flavor, the edit stays a still photo (the hints say so). If the GIF can't be made, the photo is sent in its
 * place ({@link #onExportFailed}).
 */
public final class XgramAnimatedEdit {
  private XgramAnimatedEdit () { }

  /** A GIF's length: its longest animated layer's loop, repeated to a second at least, cut at ten seconds. */
  public static final long MIN_DURATION_MS = 1000, MAX_DURATION_MS = 10000;

  /** The conversion of the GIF's generated file: this, a stamp, ':' and the edited photo's conversion. */
  static final String CONVERSION_PREFIX = "xganim1_";
  private static final char CONVERSION_SEPARATOR = ':';
  /**
   * The GIF's original path is the photo's with this added: TDLib takes the file's name and type from it (it needn't
   * exist), and the photo is read from the path without it.
   */
  private static final String PATH_SUFFIX = ".mp4";

  /** Whether an edit can become a GIF in this build (Media3 Transformer: Android 5 or later, not the legacy flavor). */
  public static boolean isSupported () {
    return Config.MODERN_VIDEO_TRANSCODING_ENABLED && tgx.flavor.Flavor.isAnimatedPhotoEditSupported();
  }

  /** Whether {@code file} is a photo whose edit has a GIF or an animated sticker on it. */
  public static boolean hasAnimatedLayers (@Nullable ImageGalleryFile file) {
    if (file == null || file.isVideo()) {
      return false;
    }
    final PaintState state = file.getPaintState();
    return state != null && state.hasAnimatedLayers();
  }

  public static boolean isConversion (@Nullable String conversion) {
    return conversion != null && conversion.startsWith(CONVERSION_PREFIX);
  }

  /** The edited photo's conversion inside a GIF's, or null. */
  @Nullable
  static String photoConversionOf (@Nullable String conversion) {
    if (!isConversion(conversion)) {
      return null;
    }
    final int index = conversion.indexOf(CONVERSION_SEPARATOR, CONVERSION_PREFIX.length());
    return index != -1 ? conversion.substring(index + 1) : null;
  }

  /** The edited photo's path, from the GIF's original path. */
  @NonNull
  static String photoPathOf (@NonNull String originalPath) {
    return originalPath.endsWith(PATH_SUFFIX) ? originalPath.substring(0, originalPath.length() - PATH_SUFFIX.length()) : originalPath;
  }

  /** The GIF's size for a photo of width x height (at most PhotoGenerationInfo.SIZE_LIMIT): even, as H.264 likes. */
  static int[] outputSize (int width, int height) {
    width = Math.max(1, width);
    height = Math.max(1, height);
    final float scale = Math.min(1f, (float) PhotoGenerationInfo.SIZE_LIMIT / (float) Math.max(width, height));
    final int outWidth = Math.max(2, Math.round(width * scale) & ~1);
    final int outHeight = Math.max(2, Math.round(height * scale) & ~1);
    return new int[] {outWidth, outHeight};
  }

  /**
   * The GIF's length in milliseconds when its longest animated layer loops in {@code longestLoop} milliseconds: whole
   * loops of it up to a second at least (so it loops cleanly), at most {@link #MAX_DURATION_MS}.
   */
  static long computeDuration (long longestLoop) {
    if (longestLoop <= 0) {
      return MIN_DURATION_MS;
    }
    long duration = longestLoop;
    if (duration < MIN_DURATION_MS) {
      duration *= (MIN_DURATION_MS + longestLoop - 1) / longestLoop;
    }
    return Math.min(MAX_DURATION_MS, duration);
  }

  /**
   * The loop of a layer's file in milliseconds (known from its preview), or -1 when it doesn't play (one frame,
   * unreadable). Not the main thread. A GIF or a WebM whose loop isn't known yet isn't read through here (sending
   * would wait for it): two frames tell that it moves, and its length is a guess (the GIF's maker measures it).
   */
  @WorkerThread
  static long loopDurationOf (@NonNull XgramStickerLayer layer) {
    final String path = layer.getFilePath();
    final int format = layer.getFormat();
    final long known = XgramFrameSource.getKnownLoopDuration(path, format);
    if (known > 0) {
      return known;
    }
    final XgramFrameSource source = XgramFrameSource.open(path, format, 64);
    if (source == null) {
      return -1;
    }
    try {
      final Bitmap scratch = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
      try {
        if (source.readNextFrame(scratch) < 0 || source.readNextFrame(scratch) < 0 || source.isStill() || source.isLoopStart()) {
          return -1;
        }
      } finally {
        scratch.recycle();
      }
      return source.getLoopDuration() > 0 ? source.getLoopDuration() : MIN_DURATION_MS;
    } finally {
      source.release();
    }
  }

  /** The longest loop of {@code state}'s animated layers in milliseconds, or -1 when none plays. Not the main thread. */
  @WorkerThread
  static long measureLongestLoop (@NonNull PaintState state) {
    long longest = -1;
    final int first = state.getFirstAnimatedLayerIndex();
    if (first != -1) {
      for (XgramLayer layer : state.copyLayersFrom(first)) {
        if (layer instanceof XgramStickerLayer && ((XgramStickerLayer) layer).isAnimated()) {
          longest = Math.max(longest, loopDurationOf((XgramStickerLayer) layer));
        }
      }
    }
    return longest;
  }

  /**
   * The Media thread, sending {@code file} (a photo with animated layers, not as a file, without a timer): the GIF
   * made of its edit, or null when it can't be one (nothing in it moves). {@code photo} is the edited photo's generated
   * file (PhotoGenerationInfo.newFile), sent instead if the GIF fails; {@code width} x {@code height} its size.
   */
  @WorkerThread
  @Nullable
  public static TdApi.InputMessageAnimation newContent (@NonNull Tdlib tdlib, @NonNull ImageGalleryFile file, @NonNull TdApi.InputFileGenerated photo, int width, int height, @Nullable TdApi.FormattedText caption, boolean showCaptionAboveMedia, boolean hasSpoiler, boolean isSecretChat) {
    if (!isSupported() || StringUtils.isEmpty(photo.conversion) || !photo.conversion.startsWith("photo")) {
      return null;
    }
    final PaintState state = file.getPaintState();
    if (state == null || !state.hasAnimatedLayers()) {
      return null;
    }
    final long longestLoop;
    try {
      longestLoop = measureLongestLoop(state);
    } catch (Throwable t) {
      Log.w("Unable to measure the GIF of a photo edit", t);
      return null;
    }
    if (longestLoop <= 0) {
      return null; // its GIFs and animated stickers have one frame each, or can't be read: a photo
    }
    final long durationMs = computeDuration(longestLoop);
    final String conversion = CONVERSION_PREFIX + GenerationInfo.randomStamp() + CONVERSION_SEPARATOR + photo.conversion;
    final TdApi.InputFileGenerated animation = new TdApi.InputFileGenerated(photo.originalPath + PATH_SUFFIX, conversion, 0);
    final int[] size = outputSize(width, height);
    final int seconds = (int) Math.max(1, (durationMs + 999) / 1000);
    TdApi.InputMessageAnimation content = new TdApi.InputMessageAnimation(new TdApi.InputAnimation(animation, null, null, seconds, size[0], size[1]), caption, showCaptionAboveMedia, hasSpoiler);
    return tdlib.filegen().createThumbnail(content, isSecretChat);
  }

  /** The conversion of a GIF made of an edit sent as {@code content}, or null. */
  @Nullable
  static String conversionOf (@Nullable TdApi.InputMessageContent content) {
    if (content == null || content.getConstructor() != TdApi.InputMessageAnimation.CONSTRUCTOR) {
      return null;
    }
    final TdApi.InputAnimation animation = ((TdApi.InputMessageAnimation) content).animation;
    final TdApi.InputFile file = animation != null ? animation.animation : null;
    if (file == null || file.getConstructor() != TdApi.InputFileGenerated.CONSTRUCTOR) {
      return null;
    }
    final String conversion = ((TdApi.InputFileGenerated) file).conversion;
    return isConversion(conversion) ? conversion : null;
  }

  /** The thumbnail of a GIF made of an edit sent as {@code file}: the edited photo at {@code resolution}, or null. */
  @Nullable
  public static TdApi.InputFileGenerated newThumbnailFile (@Nullable TdApi.InputFile file, int resolution) {
    if (file == null || file.getConstructor() != TdApi.InputFileGenerated.CONSTRUCTOR) {
      return null;
    }
    final TdApi.InputFileGenerated generated = (TdApi.InputFileGenerated) file;
    final String photoConversion = photoConversionOf(generated.conversion);
    if (StringUtils.isEmpty(photoConversion) || StringUtils.isEmpty(generated.originalPath)) {
      return null;
    }
    try {
      return new TdApi.InputFileGenerated(photoPathOf(generated.originalPath), PhotoGenerationInfo.editResolutionLimit(photoConversion, resolution), 0);
    } catch (Throwable t) {
      Log.w("Unable to make the thumbnail of a photo edit's GIF", t);
      return null;
    }
  }

  /**
   * The send requests for {@code content}, as TD.toFunctions makes them, except that the GIFs made of edits go out of
   * an album (Telegram groups photos and videos only): after it, each its own message.
   */
  public static List<TdApi.Function<?>> toFunctions (long chatId, @Nullable TdApi.MessageTopic topicId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.InputMessageContent[] content, boolean needGroupMedia) {
    if (needGroupMedia && content.length > 1) {
      final List<TdApi.InputMessageContent> others = new ArrayList<>(content.length);
      final List<TdApi.InputMessageContent> animations = new ArrayList<>();
      for (TdApi.InputMessageContent item : content) {
        if (conversionOf(item) != null) {
          animations.add(item);
        } else {
          others.add(item);
        }
      }
      if (!animations.isEmpty()) {
        final List<TdApi.Function<?>> functions = new ArrayList<>(TD.toFunctions(chatId, topicId, replyTo, options, others.toArray(new TdApi.InputMessageContent[0]), true));
        for (TdApi.InputMessageContent animation : animations) {
          functions.add(new TdApi.SendMessage(chatId, topicId, functions.isEmpty() ? replyTo : null, options, null, animation));
        }
        return functions;
      }
    }
    return TD.toFunctions(chatId, topicId, replyTo, options, content, needGroupMedia);
  }

  // The still photo sent in place of a GIF that couldn't be made: kept from the send until the GIF is made, the message
  // goes or its sending is cancelled; the failed GIF's message is deleted (while it is still being sent)

  private static final class Fallback {
    final int accountId;
    final TdApi.InputMessageContent still;
    long chatId, messageId;
    @Nullable TdApi.MessageTopic topicId;
    @Nullable TdApi.InputMessageReplyTo replyTo;
    @Nullable TdApi.MessageSendOptions options;
    boolean bound;
    /** The GIF failed before its message was known: fails the generation once the photo replaced it. */
    @Nullable Runnable pendingFailure;

    Fallback (int accountId, TdApi.InputMessageContent still) {
      this.accountId = accountId;
      this.still = still;
    }
  }

  private static final int MAX_FALLBACKS = 64;
  private static final LinkedHashMap<String, Fallback> fallbacks = new LinkedHashMap<String, Fallback>() {
    @Override
    protected boolean removeEldestEntry (Map.Entry<String, Fallback> eldest) {
      return size() > MAX_FALLBACKS;
    }
  };

  /** Keeps {@code still} (the edit as a photo) to send in place of {@code animation} if its GIF can't be made. */
  @AnyThread
  public static void registerFallback (@NonNull Tdlib tdlib, @NonNull TdApi.InputMessageAnimation animation, @NonNull TdApi.InputMessageContent still) {
    final String conversion = conversionOf(animation);
    if (conversion != null) {
      synchronized (fallbacks) {
        fallbacks.put(conversion, new Fallback(tdlib.id(), still));
      }
    }
  }

  /**
   * {@code handler} for {@code function}, which notes the message a GIF made of an edit goes in (SendMessage), so a
   * still photo can replace it.
   */
  @AnyThread
  public static Client.ResultHandler wrapHandler (@NonNull Tdlib tdlib, @NonNull TdApi.Function<?> function, @NonNull Client.ResultHandler handler) {
    if (function.getConstructor() != TdApi.SendMessage.CONSTRUCTOR) {
      return handler;
    }
    final TdApi.SendMessage send = (TdApi.SendMessage) function;
    final String conversion = conversionOf(send.inputMessageContent);
    if (conversion == null) {
      return handler;
    }
    synchronized (fallbacks) {
      final Fallback fallback = fallbacks.get(conversion);
      if (fallback == null || fallback.accountId != tdlib.id()) {
        return handler;
      }
      fallback.bound = true;
      fallback.chatId = send.chatId;
      fallback.topicId = send.topicId;
      fallback.replyTo = send.replyTo;
      fallback.options = send.options;
    }
    return result -> {
      onSendResult(tdlib, conversion, result);
      handler.onResult(result);
    };
  }

  private static void onSendResult (@NonNull Tdlib tdlib, @NonNull String conversion, @NonNull TdApi.Object result) {
    final Fallback fallback;
    final Runnable pendingFailure;
    synchronized (fallbacks) {
      fallback = fallbacks.get(conversion);
      if (fallback == null) {
        return;
      }
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        fallback.messageId = ((TdApi.Message) result).id;
        if (fallback.pendingFailure == null) {
          return;
        }
      }
      // sent nowhere (an error), or the GIF failed already
      fallbacks.remove(conversion);
      pendingFailure = fallback.pendingFailure;
    }
    if (pendingFailure != null) {
      if (fallback.messageId != 0) {
        sendStill(tdlib, fallback);
      }
      pendingFailure.run();
    }
  }

  /**
   * The GIF made of an edit sent with {@code conversion} couldn't be made: its message gives way to the still photo
   * (when it was sent from here in this app run), then {@code failGeneration} runs.
   */
  @AnyThread
  public static void onExportFailed (@NonNull Tdlib tdlib, @NonNull String conversion, @NonNull Runnable failGeneration) {
    final Fallback fallback;
    synchronized (fallbacks) {
      fallback = fallbacks.get(conversion);
      if (fallback != null && fallback.accountId == tdlib.id() && fallback.bound && fallback.messageId == 0) {
        fallback.pendingFailure = failGeneration; // once the message is known (onSendResult)
        return;
      }
      fallbacks.remove(conversion);
    }
    if (fallback != null && fallback.accountId == tdlib.id() && fallback.messageId != 0) {
      sendStill(tdlib, fallback);
    }
    failGeneration.run();
  }

  /** The GIF made of an edit sent with {@code conversion} was made, or its sending was cancelled. */
  @AnyThread
  public static void forget (@Nullable String conversion) {
    if (conversion != null) {
      synchronized (fallbacks) {
        fallbacks.remove(conversion);
      }
    }
  }

  private static void sendStill (@NonNull Tdlib tdlib, @NonNull Fallback fallback) {
    Log.i("Sending a photo edit as a photo: its GIF couldn't be made");
    // the GIF's message is still being sent (its file is being made): deleting it stops that
    tdlib.client().send(new TdApi.DeleteMessages(fallback.chatId, new long[] {fallback.messageId}, true), tdlib.silentHandler());
    tdlib.client().send(new TdApi.SendMessage(fallback.chatId, fallback.topicId, fallback.replyTo, fallback.options, null, fallback.still), tdlib.messageHandler());
    UI.showToast(R.string.XgramEditorGifFailed, Toast.LENGTH_LONG);
  }

  // Hints where an edit stays a still photo

  private static boolean stillHintShown;

  /** Says (once per app run) that GIFs and animated stickers go on this photo as a still image. */
  public static void showStillHintOnce () {
    if (!stillHintShown) {
      stillHintShown = true;
      UI.showToast(R.string.XgramEditorAnimatedAsStill, Toast.LENGTH_LONG);
    }
  }

  /** Says that an edit with GIFs or animated stickers on it went as a still photo (as a file, with a timer). */
  public static void showSentAsStillHint () {
    UI.showToast(R.string.XgramEditorSentAsStill, Toast.LENGTH_LONG);
  }
}
