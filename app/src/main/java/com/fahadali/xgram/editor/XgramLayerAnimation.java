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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.BaseThread;

import me.vkryl.core.StringUtils;

/**
 * A GIF or an animated sticker playing on a layer in the photo editor's preview. Its frames are decoded one ahead on a
 * thread of their own ({@link XgramFrameSource}) into two bitmaps: the one shown and the next one. The view showing
 * the drawing moves it on ({@link #update}) on the main thread, which shows the next frame once its time has come.
 * {@link #release()} frees the decoder (on its thread, after the frame it may be decoding).
 */
final class XgramLayerAnimation {
  /** TGS frames are this many pixels at most, as the layer's still picture is. */
  private static final int LOTTIE_MAX_SIZE = 512;
  /** How often the main thread looks again while the next frame is being decoded, in milliseconds. */
  private static final long POLL_DELAY = 10;
  /** A frame shown this late (the decoder fell behind) starts the timing again, not to rush the next ones. */
  private static final long MAX_LATENESS = 100;

  private static BaseThread decoderThread;

  private static BaseThread thread () {
    synchronized (XgramLayerAnimation.class) {
      if (decoderThread == null) {
        decoderThread = new BaseThread("XgramEditorFrames");
      }
      return decoderThread;
    }
  }

  final String path;
  final int format;

  // The decoder thread's
  private @Nullable XgramFrameSource source;

  private final Object lock = new Object();
  // Guarded by lock: the next frame
  private @Nullable Bitmap back;
  private long backTime;
  private boolean backLoopStart, backReady, decoding;
  private long frameDuration = 40;
  private volatile boolean released, failed, still;
  private volatile int rotation;

  // The main thread's: the frame shown
  private volatile @Nullable Bitmap front;
  private long frontTime, frontShownAt;

  XgramLayerAnimation (@NonNull String path, int format) {
    this.path = path;
    this.format = format;
    requestDecode();
  }

  boolean isFor (@Nullable String path, int format) {
    return this.format == format && StringUtils.equalsOrBothEmpty(this.path, path);
  }

  /** The frame to show (main thread), or null until the first one is decoded. */
  @Nullable
  Bitmap getFrame () {
    return released ? null : front;
  }

  /** Degrees clockwise the frames are turned to show (a video's rotation tag). */
  int getRotation () {
    return rotation;
  }

  /** Whether it can't play: the file can't be read, or it has one frame only. */
  boolean isStill () {
    return failed || still;
  }

  /**
   * Main thread: shows the next frame if it is decoded and its time ({@code now}, uptime milliseconds) has come.
   * True when the frame shown changed.
   */
  boolean update (long now) {
    boolean changed = false;
    synchronized (lock) {
      if (released) {
        return false;
      }
      if (backReady) {
        final Bitmap front = this.front;
        if (front == null) {
          // the first frame shows right away
          this.front = back;
          back = null;
          frontTime = backTime;
          frontShownAt = now;
          backReady = false;
          changed = true;
        } else {
          final long due = frontShownAt + nextFrameDuration();
          if (now >= due) {
            this.front = back;
            back = front; // decoded into next: it isn't drawn any more
            frontTime = backTime;
            frontShownAt = now - due > MAX_LATENESS ? now : due;
            backReady = false;
            changed = true;
          }
        }
      }
    }
    requestDecode();
    return changed;
  }

  /** Main thread: milliseconds until {@link #update} is needed again, or -1 when it won't play. */
  long getDelay (long now) {
    synchronized (lock) {
      if (released || failed || still) {
        return -1;
      }
      if (front == null) {
        return POLL_DELAY;
      }
      final long due = frontShownAt + nextFrameDuration();
      if (!backReady) {
        return Math.max(POLL_DELAY, due - now);
      }
      return Math.max(0, due - now);
    }
  }

  /** How long the frame shown stays: until the next frame's time, or a frame's usual length across a loop's end. */
  private long nextFrameDuration () {
    final long duration = !backLoopStart && backTime > frontTime ? backTime - frontTime : frameDuration;
    return Math.max(XgramFrameSource.MIN_FRAME_DURATION, Math.min(XgramFrameSource.MAX_FRAME_DURATION, duration));
  }

  private void requestDecode () {
    synchronized (lock) {
      if (released || failed || still || decoding || backReady) {
        return;
      }
      decoding = true;
    }
    thread().post(this::decodeNext, 0);
  }

  // The decoder thread

  private void decodeNext () {
    Bitmap bitmap;
    synchronized (lock) {
      if (released) {
        decoding = false;
        return;
      }
      bitmap = back;
    }
    XgramFrameSource source = this.source;
    long time = -1;
    boolean loopStart = false, still = false;
    long frameDuration = 0;
    try {
      if (source == null) {
        source = XgramFrameSource.open(path, format, LOTTIE_MAX_SIZE);
        this.source = source;
        if (source != null) {
          rotation = source.getRotation();
        }
      }
      if (source != null) {
        if (source.isStill()) {
          still = true;
        } else {
          if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() != source.getWidth() || bitmap.getHeight() != source.getHeight()) {
            bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
          }
          time = source.readNextFrame(bitmap);
          loopStart = source.isLoopStart();
          still = source.isStill();
          frameDuration = source.getFrameDuration();
        }
      }
    } catch (Throwable t) {
      Log.w("Unable to play a photo editor animation: %s", t, path);
      time = -1;
    }
    synchronized (lock) {
      decoding = false;
      if (released) {
        return;
      }
      if (still) {
        this.still = true;
      } else if (time < 0) {
        this.failed = true;
      } else {
        back = bitmap;
        backTime = time;
        backLoopStart = loopStart;
        backReady = true;
        if (frameDuration > 0) {
          this.frameDuration = frameDuration;
        }
      }
    }
  }

  /** Stops it and frees the decoder (any thread). The bitmaps are left to the garbage collector: one may be drawn yet. */
  void release () {
    synchronized (lock) {
      if (released) {
        return;
      }
      released = true;
      backReady = false;
      back = null;
    }
    front = null;
    thread().post(() -> {
      final XgramFrameSource source = this.source;
      this.source = null;
      if (source != null) {
        source.release();
      }
    }, 0);
  }
}
