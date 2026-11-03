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
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;

import java.io.File;

import me.vkryl.core.StringUtils;

/**
 * The frames of an animated picture layer of the photo editor, one after another, as the native decoders give them:
 * a GIF or a WebM sticker through {@link N#createDecoder} ({@code metadata[3]} is each frame's time) and a TGS
 * sticker through {@link N#createLottieDecoder} (frame by frame at no more than {@link #LOTTIE_MAX_FPS}), as
 * GifActor reads them for the chats. Each frame comes with its time in milliseconds from the start of its loop; at
 * the end it starts over ({@link #isLoopStart()}).
 *
 * Not thread safe: open it, read it and release it on one thread at a time (never the main one: decoding takes a
 * while). The loop lengths found are kept by file ({@link #getKnownLoopDuration}), for the GIF made of an edit.
 */
public final class XgramFrameSource {
  /** TGS stickers are read at no more than this many frames a second (the editor's preview and the GIF). */
  public static final int LOTTIE_MAX_FPS = 30;
  /** Bounds of a frame's time on screen, in milliseconds, whatever the file says. */
  public static final long MIN_FRAME_DURATION = 10, MAX_FRAME_DURATION = 5000;
  private static final long DEFAULT_FRAME_DURATION = 40;

  private final int format;
  private final String path;
  private long ptr;
  private int width, height, rotation;
  private long frameDuration = DEFAULT_FRAME_DURATION, loopDuration = -1;
  private boolean still;
  private int framesRead;
  private boolean loopStart;

  // A GIF or a WebM sticker
  private final long[] metadata = new long[N.DECODER_METADATA_ARRAY_SIZE];
  private long firstTimestamp = Long.MIN_VALUE, lastTimestamp = Long.MIN_VALUE;
  private int framesInFirstLoop;
  private boolean inFirstLoop = true, pendingLoopStart;

  // A TGS sticker
  private long totalFrames;
  private double frameRate, frameStep, nextFrameNo;
  private boolean wrapped;

  private XgramFrameSource (@NonNull String path, int format) {
    this.path = path;
    this.format = format;
  }

  // The loop lengths found so far, by file: sending an edit and making its GIF don't read a GIF through again
  private static final LruCache<String, Long> knownLoops = new LruCache<>(64);

  private static String loopKey (@NonNull String path, int format) {
    return format + ":" + path;
  }

  /** The loop length in milliseconds of {@code path} read as {@code format}, once a source found it, or -1. */
  public static long getKnownLoopDuration (@Nullable String path, int format) {
    if (StringUtils.isEmpty(path)) {
      return -1;
    }
    final Long duration;
    synchronized (knownLoops) {
      duration = knownLoops.get(loopKey(path, format));
    }
    return duration != null ? duration : -1;
  }

  private void rememberLoop (long duration) {
    if (duration > 0) {
      synchronized (knownLoops) {
        knownLoops.put(loopKey(path, format), duration);
      }
    }
  }

  /**
   * Opens {@code path}, read as {@code format} ({@link XgramStickerLayer#FORMAT_LOTTIE} or
   * {@link XgramStickerLayer#FORMAT_VIDEO}); null when it can't be read. TGS frames are at most {@code maxSize}
   * pixels on their long side; video frames are as the decoder makes them (at most 920 pixels).
   */
  @Nullable
  public static XgramFrameSource open (@Nullable String path, int format, int maxSize) {
    if (StringUtils.isEmpty(path) || (format != XgramStickerLayer.FORMAT_LOTTIE && format != XgramStickerLayer.FORMAT_VIDEO) || !new File(path).exists()) {
      return null;
    }
    XgramFrameSource source = new XgramFrameSource(path, format);
    boolean ok;
    try {
      ok = format == XgramStickerLayer.FORMAT_LOTTIE ? source.openLottie(path, maxSize) : source.openVideo(path);
    } catch (Throwable t) {
      Log.w("Unable to open a photo editor animation: %s", t, path);
      ok = false;
    }
    if (!ok) {
      source.release();
      return null;
    }
    return source;
  }

  private boolean openVideo (@NonNull String path) {
    ptr = N.createDecoder(path, metadata, 0);
    if (ptr == 0) {
      return false;
    }
    width = (int) metadata[0];
    height = (int) metadata[1];
    if (width <= 0 || height <= 0) {
      return false;
    }
    rotation = normalizeRotation(U.getVideoRotation(path));
    final double fps = metadata[2] / 1000.0;
    if (fps > 0) {
      frameDuration = clampDuration(Math.round(1000.0 / fps));
    }
    return true;
  }

  private boolean openLottie (@NonNull String path, int maxSize) {
    final String json = U.gzipFileToString(path);
    if (StringUtils.isEmpty(json)) {
      return false;
    }
    final double[] lottieMetadata = new double[3];
    ptr = N.createLottieDecoder(path, json, lottieMetadata, 0);
    if (ptr == 0) {
      return false;
    }
    totalFrames = (long) lottieMetadata[0];
    frameRate = lottieMetadata[1];
    if (totalFrames <= 0 || !(frameRate > 0)) {
      return false;
    }
    final int[] size = new int[2];
    N.getLottieSize(ptr, size);
    int width = size[0], height = size[1];
    if (width <= 0 || height <= 0) {
      return false;
    }
    if (maxSize > 0 && Math.max(width, height) > maxSize) {
      final float ratio = Math.min((float) maxSize / (float) width, (float) maxSize / (float) height);
      width = Math.max(1, (int) (width * ratio));
      height = Math.max(1, (int) (height * ratio));
    }
    this.width = width;
    this.height = height;
    frameStep = Math.max(1.0, frameRate / LOTTIE_MAX_FPS);
    frameDuration = clampDuration(Math.round(frameStep * 1000.0 / frameRate));
    loopDuration = Math.max(1, Math.round(totalFrames * 1000.0 / frameRate));
    still = totalFrames == 1;
    if (!still) {
      rememberLoop(loopDuration);
    }
    return true;
  }

  private static int normalizeRotation (int rotation) {
    rotation = ((rotation % 360) + 360) % 360;
    return rotation == 90 || rotation == 180 || rotation == 270 ? rotation : 0;
  }

  private static long clampDuration (long duration) {
    return Math.max(MIN_FRAME_DURATION, Math.min(MAX_FRAME_DURATION, duration));
  }

  public int getFormat () {
    return format;
  }

  /** A frame's width: the bitmaps given to {@link #readNextFrame} must be exactly this size. */
  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  /** Degrees clockwise the frames are turned to show (a video's rotation tag), 0, 90, 180 or 270. */
  public int getRotation () {
    return rotation;
  }

  /** Whether it has one frame only (known for a GIF or a WebM once its first loop has been read). */
  public boolean isStill () {
    return still;
  }

  /** A loop's length in milliseconds, or -1 while not known (a GIF or a WebM before its first loop is read). */
  public long getLoopDuration () {
    return loopDuration;
  }

  /** How long a frame shows, when its next one's time can't tell (the last frame of a loop). */
  public long getFrameDuration () {
    return frameDuration;
  }

  /** Whether the frame {@link #readNextFrame} read last began a new loop (the first frame read doesn't). */
  public boolean isLoopStart () {
    return loopStart;
  }

  /**
   * Decodes the next frame into {@code bitmap} ({@link #getWidth()} x {@link #getHeight()}, ARGB_8888): its time in
   * milliseconds from the start of its loop, or -1 when it can't be read.
   */
  public long readNextFrame (@NonNull Bitmap bitmap) {
    if (ptr == 0 || bitmap.isRecycled() || bitmap.getWidth() != width || bitmap.getHeight() != height) {
      return -1;
    }
    return format == XgramStickerLayer.FORMAT_LOTTIE ? readLottieFrame(bitmap) : readVideoFrame(bitmap);
  }

  private long readVideoFrame (@NonNull Bitmap bitmap) {
    final int ret = N.getVideoFrame(ptr, bitmap, metadata);
    if (ret == 0) {
      return -1;
    }
    final long timestamp = metadata[3];
    final boolean loopStart = ret == 2 || pendingLoopStart;
    pendingLoopStart = false;
    if (firstTimestamp == Long.MIN_VALUE) {
      firstTimestamp = timestamp;
    }
    if (loopStart) {
      if (inFirstLoop) {
        inFirstLoop = false;
        loopDuration = Math.max(1, lastTimestamp - firstTimestamp + frameDuration);
        still = framesInFirstLoop <= 1;
        if (!still) {
          rememberLoop(loopDuration);
        }
      }
    } else if (lastTimestamp != Long.MIN_VALUE && timestamp > lastTimestamp) {
      frameDuration = clampDuration(timestamp - lastTimestamp);
    }
    if (inFirstLoop) {
      framesInFirstLoop++;
    }
    lastTimestamp = timestamp;
    this.loopStart = loopStart;
    framesRead++;
    return Math.max(0, timestamp - firstTimestamp);
  }

  private long readLottieFrame (@NonNull Bitmap bitmap) {
    // the position keeps its fraction: a frame rate that isn't a multiple of LOTTIE_MAX_FPS still skips frames
    double position = nextFrameNo;
    if (position < 0 || (long) position >= totalFrames) {
      position = 0;
    }
    final long frameNo = (long) position;
    if (!N.getLottieFrame(ptr, bitmap, frameNo)) {
      return -1;
    }
    loopStart = wrapped && framesRead > 0;
    wrapped = false;
    nextFrameNo = position + frameStep;
    if ((long) nextFrameNo >= totalFrames) {
      nextFrameNo = 0;
      wrapped = true;
    }
    framesRead++;
    return Math.round(frameNo * 1000.0 / frameRate);
  }

  /** Back to the first frame: the next {@link #readNextFrame} reads it (and tells a new loop, unless none was read). */
  public void seekToStart () {
    if (ptr == 0) {
      return;
    }
    if (format == XgramStickerLayer.FORMAT_LOTTIE) {
      nextFrameNo = 0;
      wrapped = framesRead > 0;
    } else if (N.seekVideoToStart(ptr)) {
      pendingLoopStart = framesRead > 0;
    }
  }

  /**
   * A loop's length in milliseconds, reading a GIF or a WebM through once when it isn't known yet (at most
   * {@code maxDuration} of it, which is returned for a longer one), then back to its start; -1 when it can't be read.
   */
  public long measureLoopDuration (long maxDuration) {
    if (loopDuration >= 0 || ptr == 0) {
      return ptr != 0 ? loopDuration : -1;
    }
    final Bitmap scratch;
    try {
      scratch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      Log.w("Unable to measure a photo editor animation", t);
      return -1;
    }
    long time = 0;
    do {
      final long frameTime = readNextFrame(scratch);
      if (frameTime < 0) {
        break;
      }
      if (loopDuration >= 0) {
        break;
      }
      time = frameTime;
    } while (time < maxDuration);
    seekToStart();
    if (loopDuration >= 0) {
      return loopDuration;
    }
    if (framesRead <= 0) {
      return -1;
    }
    final long duration = Math.max(1, Math.min(maxDuration, time + frameDuration));
    rememberLoop(duration); // a longer loop: this much of it is all a GIF made of an edit plays
    return duration;
  }

  /** Frees the decoder. */
  public void release () {
    final long ptr = this.ptr;
    this.ptr = 0;
    if (ptr != 0) {
      if (format == XgramStickerLayer.FORMAT_LOTTIE) {
        N.destroyLottieDecoder(ptr);
      } else {
        N.destroyDecoder(ptr);
      }
    }
  }
}
