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
package com.fahadali.xgram.wastickers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * XGram: Telegram's animated stickers (Lottie .tgs and VP9 .webm) made into WhatsApp's animated stickers: an animated
 * WebP, 512x512, 500 KB at most, frames of 8 ms or more, 10 s at most. Nothing native is added: the frames come from
 * the decoders Telegram X plays stickers with (rlottie through {@link N#getLottieFrame}, FFmpeg through
 * {@link N#getVideoFrame}), each frame is encoded on its own with {@link Bitmap#compress} (lossy WebP) and
 * {@link XgramWaAnimatedWebp} puts the frames together.
 * <ul>
 *   <li>Frames are taken at a fixed rate (15 a second at first); a frame that stays on screen longer (a slower source)
 *   is written once with the longer duration.</li>
 *   <li>Each frame is cut to the part that has something in it (at even offsets, as WebP needs), and is cleared from
 *   the canvas after it shows (dispose to background, no blending), so every frame is whole on its own. The first
 *   frame covers the whole canvas, for players that look only at it.</li>
 *   <li>Too big: the quality steps down, then the rate (12, then 10 a second), then frames are dropped evenly (8, 6,
 *   5 a second), until the file fits; one that never fits is left out ({@link Result#webp} null). A pass gives up as
 *   soon as it is over 500 KB, or clearly on its way there.</li>
 *   <li>The tray icon comes from the fullest frame, so an animation that starts empty still gets a proper icon.</li>
 * </ul>
 * One sticker at a time, on the export's worker thread; it stops at {@link CancelCheck#check}, and every bitmap and
 * native decoder is let go in {@code finally}.
 */
final class XgramWaStickerAnimator {
  private XgramWaStickerAnimator () { }

  static final int KIND_LOTTIE = 1, KIND_VIDEO = 2;

  static final int SIZE = 512;
  static final int MAX_BYTES = 500 * 1024;
  static final int MAX_DURATION_MS = 10_000;
  /** WhatsApp wants 8 ms or more a frame; a last frame shorter than this joins the one before it. */
  private static final int MIN_FRAME_MS = 20;
  /** A single-frame animation becomes two equal frames (WhatsApp wants more than one), this long each. */
  static final int SINGLE_FRAME_MS = 500;
  /** Video: frames read at most, and the longest believable gap between two frames' times. */
  private static final int MAX_SOURCE_FRAMES = 1200;
  private static final long MAX_GAP_MS = 2000;
  private static final int MAX_VIDEO_SIZE = 2048;
  /** A pass gives up early when its frames so far, spread over the whole animation, come to this many times the limit. */
  private static final double GIVE_UP_PROJECTION = 1.4;
  /** The fullest frame is kept this big for the tray icon (96x96 in the end). */
  private static final int TRAY_FRAME_SIZE = 256;

  /** Frames a second and lossy quality, best first. */
  private static final int[][] LADDER = {
    {15, 75}, {15, 65}, {12, 60}, {12, 50}, {10, 50}, {10, 40}, {10, 30}, {10, 20},
    {8, 20}, {6, 20}, {5, 20} // frames dropped evenly
  };

  /** Stops the work by throwing (the export's cancel). */
  interface CancelCheck {
    void check ();
  }

  static final class Result {
    /** The animated WebP, or null when it couldn't be made to fit 500 KB. */
    final @Nullable byte[] webp;
    /** A 96x96 PNG of the fullest frame, or null. */
    final @Nullable byte[] tray;
    /** The {@link #LADDER} step that fit, for {@link #startRung} of the next sticker. */
    final int rung;
    final int fps, quality, frames;

    Result (@Nullable byte[] webp, @Nullable byte[] tray, int rung, int fps, int quality, int frames) {
      this.webp = webp;
      this.tray = tray;
      this.rung = rung;
      this.fps = fps;
      this.quality = quality;
      this.frames = frames;
    }
  }

  /**
   * Where the next sticker's search starts: one step better than what the last one of the set needed (a set's
   * stickers are much alike), so most stickers take one or two passes rather than walking down from the top.
   */
  static int startRung (int lastRung) {
    return Math.max(0, Math.min(lastRung, LADDER.length) - 1);
  }

  /**
   * {@code file} ({@link #KIND_LOTTIE} or {@link #KIND_VIDEO}) as WhatsApp's animated sticker: null when it can't be
   * decoded, a {@link Result} with no {@code webp} when it can't be made small enough.
   */
  @Nullable
  @WorkerThread
  static Result convert (@NonNull File file, int kind, int firstRung, @NonNull CancelCheck cancel) {
    final Source source;
    switch (kind) {
      case KIND_LOTTIE:
        source = LottieSource.open(file);
        break;
      case KIND_VIDEO:
        source = new VideoSource(file.getPath());
        break;
      default:
        return null;
    }
    if (source == null) {
      return null;
    }
    Work work = null;
    try {
      work = new Work();
      for (int rung = Math.max(0, Math.min(firstRung, LADDER.length - 1)); rung < LADDER.length; rung++) {
        cancel.check();
        final int fps = LADDER[rung][0], quality = LADDER[rung][1];
        // Giving up early only when there's a lower step to go to: the last one always gets a full pass
        final Encoder encoder = new Encoder(work, quality, rung < LADDER.length - 1 ? source.durationMs : 0);
        try {
          if (!source.render(fps, encoder, work, cancel)) {
            return null;
          }
        } catch (TooBig e) {
          continue;
        }
        final byte[] webp = XgramWaAnimatedWebp.build(encoder.frames, SIZE, SIZE);
        if (webp.length > MAX_BYTES) {
          continue; // the encoder counts the same bytes, so this doesn't happen
        }
        return new Result(webp, work.tray(), rung, fps, quality, encoder.frames.size());
      }
      Log.w("XGram WhatsApp stickers: %s is over %d bytes even at %d fps", file.getName(), MAX_BYTES, LADDER[LADDER.length - 1][0]);
      return new Result(null, null, LADDER.length, 0, 0, 0);
    } catch (IOException | OutOfMemoryError e) {
      Log.w("XGram WhatsApp stickers: cannot animate %s", e, file.getName());
      return null;
    } finally {
      source.close();
      if (work != null) {
        work.recycle();
      }
    }
  }

  /** When frame {@code slot} starts at {@code fps} frames a second. */
  private static int slotStartMs (int slot, int fps) {
    return (int) Math.round(slot * 1000.0 / fps);
  }

  /** Thrown to end a pass that can't fit; no stack trace, it's only a jump. */
  private static final class TooBig extends RuntimeException {
    static final TooBig INSTANCE = new TooBig();

    @Override
    public synchronized Throwable fillInStackTrace () {
      return this;
    }
  }

  // Sources

  private abstract static class Source {
    /** How long the animation is (capped at 10 s) once known: Lottie at once, a video after its first pass (even one over 500 KB); else 0. */
    int durationMs;

    /**
     * Feeds one pass at {@code fps} to {@code encoder}: each frame that starts a slot, drawn on a 512x512 canvas, with
     * its start time, then the end. False when decoding fails.
     */
    abstract boolean render (int fps, @NonNull Encoder encoder, @NonNull Work work, @NonNull CancelCheck cancel) throws IOException;

    abstract void close ();
  }

  /** A .tgs (gzipped Lottie JSON), rendered by rlottie at 512x512 (keeping its aspect), as Telegram X plays it. */
  private static final class LottieSource extends Source {
    private long ptr;
    private final long frameCount;
    private final double frameRate;

    @Nullable
    static LottieSource open (@NonNull File file) {
      final String path = file.getPath();
      final String json = U.gzipFileToString(path);
      if (StringUtils.isEmpty(json)) {
        return null;
      }
      final double[] metadata = new double[3]; // frame count, frames a second, duration (s)
      final long ptr = N.createLottieDecoder(path, json, metadata, 0);
      if (ptr == 0) {
        return null;
      }
      final long frameCount = (long) metadata[0];
      final double frameRate = metadata[1];
      if (frameCount <= 0 || !(frameRate > 0)) {
        N.destroyLottieDecoder(ptr);
        return null;
      }
      return new LottieSource(ptr, frameCount, frameRate);
    }

    private LottieSource (long ptr, long frameCount, double frameRate) {
      this.ptr = ptr;
      this.frameCount = frameCount;
      this.frameRate = frameRate;
      this.durationMs = (int) Math.max(1, Math.min(MAX_DURATION_MS, Math.round(frameCount * 1000.0 / frameRate)));
    }

    @Override
    boolean render (int fps, @NonNull Encoder encoder, @NonNull Work work, @NonNull CancelCheck cancel) throws IOException {
      final Bitmap canvas = work.canvas;
      long previous = -1;
      for (int slot = 0; ; slot++) {
        final int startMs = slotStartMs(slot, fps);
        if (slot > 0 && startMs + MIN_FRAME_MS > durationMs) {
          break; // too short a tail: the frame before shows until the end
        }
        final long frameNo = Math.min(frameCount - 1, (long) Math.floor(startMs * frameRate / 1000.0 + 1e-6));
        if (frameNo == previous) {
          continue; // slower than the rate: the same frame shows longer
        }
        cancel.check();
        canvas.eraseColor(0);
        if (!N.getLottieFrame(ptr, canvas, frameNo)) {
          return false;
        }
        encoder.frame(canvas, startMs);
        previous = frameNo;
      }
      encoder.end(durationMs);
      return true;
    }

    @Override
    void close () {
      if (ptr != 0) {
        N.destroyLottieDecoder(ptr);
        ptr = 0;
      }
    }
  }

  /** A .webm (VP9 with alpha), decoded by FFmpeg as Telegram X plays it; a new decoder for each pass. */
  private static final class VideoSource extends Source {
    private final String path;

    VideoSource (@NonNull String path) {
      this.path = path;
    }

    @Override
    boolean render (int fps, @NonNull Encoder encoder, @NonNull Work work, @NonNull CancelCheck cancel) throws IOException {
      // [0], [1]: the size frames come in; [2]: frames per 1000 s; [3]: a frame's time (ms)
      final long[] metadata = new long[N.DECODER_METADATA_ARRAY_SIZE];
      final long ptr = N.createDecoder(path, metadata, 0);
      if (ptr == 0) {
        return false;
      }
      Bitmap current = null, next = null;
      try {
        final int width = (int) metadata[0], height = (int) metadata[1];
        if (width <= 0 || height <= 0 || width > MAX_VIDEO_SIZE || height > MAX_VIDEO_SIZE) {
          return false;
        }
        long intervalMs = metadata[2] > 0 ? Math.max(1, Math.round(1_000_000.0 / metadata[2])) : 40;
        // The decoder writes only into a bitmap of exactly its size
        current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (N.getVideoFrame(ptr, current, metadata) != 1) {
          return false;
        }
        final long baseMs = metadata[3];
        long currentMs = 0;
        int slot = 0;
        // First pass over 500 KB: encoding stops, decoding goes on to learn the length, so later passes can give up early
        TooBig overBudget = null;
        for (int decoded = 1; ; decoded++) {
          cancel.check();
          // 1: the next frame; 2: the video started over, so this is the end; 0: no more frames (the end too)
          final int status = decoded < MAX_SOURCE_FRAMES ? N.getVideoFrame(ptr, next, metadata) : 0;
          boolean last = status != 1;
          long nextMs = currentMs + intervalMs;
          if (!last) {
            final long timeMs = metadata[3] - baseMs;
            if (timeMs > currentMs && timeMs - currentMs <= MAX_GAP_MS) {
              intervalMs = timeMs - currentMs;
              nextMs = timeMs;
            } // else a missing or odd timestamp: one frame interval later
          }
          if (nextMs >= MAX_DURATION_MS) {
            nextMs = MAX_DURATION_MS;
            last = true;
          }
          // `current` shows from currentMs until nextMs: it goes in once, at the first slot starting in that time
          boolean added = false;
          int startMs;
          while ((startMs = slotStartMs(slot, fps)) < nextMs) {
            if (!added) {
              if (overBudget == null) {
                try {
                  encoder.frame(work.fit(current), startMs);
                } catch (TooBig e) {
                  if (durationMs > 0) {
                    throw e;
                  }
                  overBudget = e;
                }
              }
              added = true;
            }
            slot++;
          }
          if (last) {
            durationMs = (int) nextMs;
            if (overBudget != null) {
              throw overBudget;
            }
            encoder.end((int) nextMs);
            return true;
          }
          final Bitmap swap = current;
          current = next;
          next = swap;
          currentMs = nextMs;
        }
      } finally {
        N.destroyDecoder(ptr);
        if (current != null) {
          current.recycle();
        }
        if (next != null) {
          next.recycle();
        }
      }
    }

    @Override
    void close () { }
  }

  // Encoding

  /** A ByteArrayOutputStream whose buffer can be read without a copy. */
  private static final class Buffer extends ByteArrayOutputStream {
    Buffer (int size) {
      super(size);
    }

    byte[] array () {
      return buf;
    }
  }

  /** What one sticker's passes share: the canvas, buffers, and the tray icon's frame. */
  private static final class Work {
    final Bitmap canvas = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    private final Canvas canvasDrawer = new Canvas(canvas);
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF fitRect = new RectF(), trayRect = new RectF(0, 0, TRAY_FRAME_SIZE, TRAY_FRAME_SIZE);
    final int[] pixels = new int[SIZE * SIZE];
    final Buffer out = new Buffer(128 * 1024);
    @SuppressWarnings("deprecation")
    final Bitmap.CompressFormat lossy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

    private @Nullable Bitmap trayFrame;
    private @Nullable Canvas trayDrawer;
    private long trayArea = -1;

    /** A video frame centred on the 512x512 canvas and scaled to fit; a 512x512 one as it is. */
    Bitmap fit (Bitmap frame) {
      if (frame.getWidth() == SIZE && frame.getHeight() == SIZE) {
        return frame;
      }
      canvas.eraseColor(0);
      final float scale = Math.min((float) SIZE / frame.getWidth(), (float) SIZE / frame.getHeight());
      final float width = frame.getWidth() * scale, height = frame.getHeight() * scale;
      fitRect.set((SIZE - width) / 2f, (SIZE - height) / 2f, (SIZE + width) / 2f, (SIZE + height) / 2f);
      canvasDrawer.drawBitmap(frame, null, fitRect, paint);
      return canvas;
    }

    void startPass () {
      trayArea = -1;
    }

    /** Keeps a small copy of {@code frame} when it is the fullest of the pass so far. */
    void offerTrayFrame (Bitmap frame, long area) {
      if (area <= trayArea) {
        return;
      }
      trayArea = area;
      if (trayFrame == null) {
        trayFrame = Bitmap.createBitmap(TRAY_FRAME_SIZE, TRAY_FRAME_SIZE, Bitmap.Config.ARGB_8888);
        trayDrawer = new Canvas(trayFrame);
      }
      trayFrame.eraseColor(0);
      //noinspection ConstantConditions
      trayDrawer.drawBitmap(frame, null, trayRect, paint);
    }

    @Nullable
    byte[] tray () {
      return trayFrame != null && trayArea > 0 ? XgramWaStickerImages.toTray(trayFrame) : null;
    }

    void recycle () {
      canvas.recycle();
      if (trayFrame != null) {
        trayFrame.recycle();
        trayFrame = null;
      }
    }
  }

  /** One pass: frames encoded as they come, their size counted against the limit. */
  private static final class Encoder {
    private final Work work;
    private final int quality;
    /** The animation's length when known (for giving up early), else 0. */
    private final int expectedMs;
    final List<XgramWaAnimatedWebp.Frame> frames = new ArrayList<>();
    private long bytes = XgramWaAnimatedWebp.HEADER_BYTES;
    private int lastStartMs;

    Encoder (Work work, int quality, int expectedMs) {
      this.work = work;
      this.quality = quality;
      this.expectedMs = expectedMs;
      work.startPass();
    }

    /** The next frame (512x512), showing from {@code startMs} until the next one starts. */
    void frame (@NonNull Bitmap frame, int startMs) throws IOException {
      if (!frames.isEmpty()) {
        frames.get(frames.size() - 1).durationMs = startMs - lastStartMs;
      }
      lastStartMs = startMs;

      // The rectangle with something in it
      final int[] pixels = work.pixels;
      frame.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
      int top = -1, bottom = -1, left = SIZE, right = -1;
      for (int y = 0; y < SIZE; y++) {
        final int row = y * SIZE;
        int first = -1;
        for (int x = 0; x < SIZE; x++) {
          if ((pixels[row + x] >>> 24) != 0) {
            first = x;
            break;
          }
        }
        if (first < 0) {
          continue;
        }
        if (top < 0) {
          top = y;
        }
        bottom = y;
        left = Math.min(left, first);
        for (int x = SIZE - 1; x > right && x >= first; x--) {
          if ((pixels[row + x] >>> 24) != 0) {
            right = x;
            break;
          }
        }
      }
      work.offerTrayFrame(frame, top < 0 ? 0 : (long) (right - left + 1) * (bottom - top + 1));

      // WebP frame offsets are even; the first frame covers the whole canvas; an empty frame is a clear 2x2
      final int x, y, width, height;
      if (frames.isEmpty()) {
        x = y = 0;
        width = height = SIZE;
      } else if (top < 0) {
        x = y = 0;
        width = height = 2;
      } else {
        x = left & ~1;
        y = top & ~1;
        width = right + 1 - x;
        height = bottom + 1 - y;
      }
      final Bitmap part = width == SIZE && height == SIZE ? frame : Bitmap.createBitmap(frame, x, y, width, height);
      final XgramWaAnimatedWebp.Image image;
      try {
        work.out.reset();
        if (!part.compress(work.lossy, quality, work.out)) {
          throw new IOException("Bitmap.compress failed");
        }
        image = XgramWaAnimatedWebp.readStill(work.out.array(), work.out.size());
      } finally {
        if (part != frame) {
          part.recycle();
        }
      }
      if (image.width != width || image.height != height) {
        throw new IOException("Encoded " + image.width + "x" + image.height + ", expected " + width + "x" + height);
      }
      // Cleared after it shows, and drawn without blending: each frame stands alone
      final XgramWaAnimatedWebp.Frame result = new XgramWaAnimatedWebp.Frame(image.data, x, y, width, height, 0, true, true);
      frames.add(result);
      bytes += result.fileBytes();

      if (bytes > MAX_BYTES) {
        throw TooBig.INSTANCE;
      }
      if (expectedMs > 0 && frames.size() >= 4 && startMs >= expectedMs / 3) {
        // The time covered so far, counting this frame as an average one
        final double coveredMs = startMs * (double) frames.size() / (frames.size() - 1);
        final double projected = XgramWaAnimatedWebp.HEADER_BYTES + (bytes - XgramWaAnimatedWebp.HEADER_BYTES) * (expectedMs / coveredMs);
        if (projected > MAX_BYTES * GIVE_UP_PROJECTION) {
          throw TooBig.INSTANCE;
        }
      }
    }

    /** The animation ends at {@code totalMs}. */
    void end (int totalMs) throws IOException {
      if (frames.isEmpty()) {
        throw new IOException("No frames");
      }
      final XgramWaAnimatedWebp.Frame last = frames.get(frames.size() - 1);
      last.durationMs = totalMs - lastStartMs;
      if (frames.size() >= 2 && last.durationMs < MIN_FRAME_MS) {
        // Too short a tail: the frame before shows until the end
        frames.remove(frames.size() - 1);
        bytes -= last.fileBytes();
        frames.get(frames.size() - 1).durationMs += Math.max(0, last.durationMs);
      }
      if (frames.size() == 1) {
        // One frame: shown twice, as WhatsApp wants an animated sticker to have more than one
        final XgramWaAnimatedWebp.Frame only = frames.get(0);
        only.durationMs = SINGLE_FRAME_MS;
        final XgramWaAnimatedWebp.Frame again = new XgramWaAnimatedWebp.Frame(only.data, only.x, only.y, only.width, only.height, SINGLE_FRAME_MS, true, true);
        frames.add(again);
        bytes += again.fileBytes();
        if (bytes > MAX_BYTES) {
          throw TooBig.INSTANCE;
        }
      }
      long total = 0;
      for (XgramWaAnimatedWebp.Frame frame : frames) {
        if (frame.durationMs < 8) {
          throw new IOException("Frame of " + frame.durationMs + " ms");
        }
        total += frame.durationMs;
      }
      if (total > MAX_DURATION_MS) {
        throw new IOException("Animation of " + total + " ms");
      }
    }
  }
}
