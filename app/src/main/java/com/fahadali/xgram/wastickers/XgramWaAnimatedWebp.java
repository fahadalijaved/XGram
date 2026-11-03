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

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * XGram: an animated WebP put together from still WebP frames (each one {@code Bitmap.compress}'s output), for
 * WhatsApp's animated stickers, with no native encoder. It follows the WebP container spec
 * (developers.google.com/speed/webp/docs/riff_container); every number is little-endian, a "u24 n-1" field holds the
 * value minus one, and a chunk with an odd payload is followed by one 0 byte that its size doesn't count.
 * <pre>
 * 'RIFF' u32 file size - 8   'WEBP'
 * 'VP8X' u32 10              u8 flags 0x12 (0x10 alpha, 0x02 animation)  u24 0 (reserved)
 *                            u24 canvas width - 1   u24 canvas height - 1
 * 'ANIM' u32 6               u32 background colour 0x00000000 (B, G, R, A: transparent)   u16 loop count 0 (forever)
 * then for every frame:
 * 'ANMF' u32 16 + n          u24 x / 2   u24 y / 2   u24 width - 1   u24 height - 1   u24 duration (ms)
 *                            u8 flags (0x02 don't blend, 0x01 dispose to background)
 *                            n bytes: the frame's 'ALPH' chunk (when it has one) and 'VP8 ' chunk, or its 'VP8L'
 *                            chunk, copied from the still file with their headers and padding
 * </pre>
 * The still file's other chunks ('VP8X', 'ICCP', 'EXIF', 'XMP ') are left out: a frame may only hold its image
 * chunks, and without an ICC profile the frames are sRGB, which is what Android's bitmaps are.
 */
final class XgramWaAnimatedWebp {
  private XgramWaAnimatedWebp () { }

  /** RIFF header (12) + VP8X chunk (8 + 10) + ANIM chunk (8 + 6). */
  static final int HEADER_BYTES = 12 + 18 + 14;
  /** An ANMF chunk's header (8) and its frame fields (16), before the frame's image chunks. */
  static final int FRAME_HEADER_BYTES = 8 + 16;

  private static final int FLAG_ALPHA = 0x10, FLAG_ANIMATION = 0x02;
  private static final int FRAME_NO_BLEND = 0x02, FRAME_DISPOSE = 0x01;
  private static final int MAX_24 = 0xFFFFFF;
  /** VP8 and VP8L store 14-bit sizes. */
  private static final int MAX_SIZE = 16383;

  /** A still WebP's image: its image chunks as they go inside an ANMF chunk, and its size. */
  static final class Image {
    final byte[] data;
    final int width, height;
    final boolean lossless, hasAlphaChunk;

    Image (byte[] data, int width, int height, boolean lossless, boolean hasAlphaChunk) {
      this.data = data;
      this.width = width;
      this.height = height;
      this.lossless = lossless;
      this.hasAlphaChunk = hasAlphaChunk;
    }
  }

  /** One frame: where it goes on the canvas, how long it shows and what happens to it after. */
  static final class Frame {
    final byte[] data;
    final int x, y, width, height;
    int durationMs;
    final boolean noBlend, dispose;

    Frame (byte[] data, int x, int y, int width, int height, int durationMs, boolean noBlend, boolean dispose) {
      this.data = data;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.durationMs = durationMs;
      this.noBlend = noBlend;
      this.dispose = dispose;
    }

    /** The bytes this frame adds to the file. */
    int fileBytes () {
      return FRAME_HEADER_BYTES + data.length;
    }
  }

  // Reading a still WebP

  /**
   * The image chunks of the still WebP in {@code webp[0, length)}: a simple lossy file ('VP8 '), a simple lossless one
   * ('VP8L') or an extended one ('VP8X' with 'ALPH' + 'VP8 ' or 'VP8L', and maybe 'ICCP', 'EXIF', 'XMP ').
   */
  @NonNull
  static Image readStill (@NonNull byte[] webp, int length) throws IOException {
    if (length > webp.length || length < 12 + 8 || !fourcc(webp, 0, "RIFF") || !fourcc(webp, 8, "WEBP")) {
      throw new IOException("Not a WebP file");
    }
    final long riffEnd = le32(webp, 4) + 8L;
    if (riffEnd > length || riffEnd < 12 + 8) {
      throw new IOException("Truncated WebP file: " + riffEnd + " > " + length);
    }
    final int end = (int) riffEnd;
    int alphaPos = -1, imagePos = -1;
    boolean lossless = false;
    int pos = 12;
    while (pos + 8 <= end) {
      final long size = le32(webp, pos + 4);
      if (pos + 8L + size > end) {
        throw new IOException("Chunk past the end of the file");
      }
      if (fourcc(webp, pos, "ANIM") || fourcc(webp, pos, "ANMF")) {
        throw new IOException("Already animated");
      } else if (fourcc(webp, pos, "ALPH")) {
        if (alphaPos < 0 && imagePos < 0) { // the spec: ALPH comes before the bitstream; one only
          alphaPos = pos;
        }
      } else if (fourcc(webp, pos, "VP8 ") || fourcc(webp, pos, "VP8L")) {
        if (imagePos >= 0) {
          throw new IOException("Two bitstreams");
        }
        imagePos = pos;
        lossless = webp[pos + 3] == 'L';
      }
      // VP8X, ICCP, EXIF, XMP and unknown chunks: not a frame's
      pos += 8 + (int) size + (int) (size & 1);
    }
    if (imagePos < 0) {
      throw new IOException("No VP8 or VP8L bitstream");
    }
    if (lossless) {
      alphaPos = -1; // VP8L carries its own alpha, and an ALPH chunk next to it is invalid
    }
    final int[] size = bitstreamSize(webp, imagePos, lossless);
    final int alphaBytes = alphaPos >= 0 ? paddedChunkBytes(webp, alphaPos) : 0;
    final int imageBytes = paddedChunkBytes(webp, imagePos);
    final byte[] data = new byte[alphaBytes + imageBytes];
    if (alphaPos >= 0) {
      copyChunk(webp, alphaPos, data, 0);
    }
    copyChunk(webp, imagePos, data, alphaBytes);
    return new Image(data, size[0], size[1], lossless, alphaPos >= 0);
  }

  /** The width and height a VP8 key frame or a VP8L bitstream says it has. */
  private static int[] bitstreamSize (byte[] webp, int chunkPos, boolean lossless) throws IOException {
    final long payload = le32(webp, chunkPos + 4);
    final int p = chunkPos + 8;
    final int width, height;
    if (lossless) {
      // 0x2f, then 14 bits width - 1, 14 bits height - 1, 1 bit alpha used, 3 bits version (0)
      if (payload < 5 || (webp[p] & 0xff) != 0x2f) {
        throw new IOException("Bad VP8L header");
      }
      final long bits = le32(webp, p + 1);
      width = (int) (bits & 0x3fff) + 1;
      height = (int) ((bits >>> 14) & 0x3fff) + 1;
      if ((bits >>> 29) != 0) {
        throw new IOException("VP8L version " + (bits >>> 29));
      }
    } else {
      // 3-byte frame tag (bit 0 clear: a key frame), start code 9d 01 2a, then 14 bits width and 14 bits height (2 bits scale each)
      if (payload < 10 || (webp[p] & 0x01) != 0 || (webp[p + 3] & 0xff) != 0x9d || (webp[p + 4] & 0xff) != 0x01 || (webp[p + 5] & 0xff) != 0x2a) {
        throw new IOException("Bad VP8 key frame header");
      }
      width = le16(webp, p + 6) & 0x3fff;
      height = le16(webp, p + 8) & 0x3fff;
    }
    if (width <= 0 || height <= 0) {
      throw new IOException("Empty bitstream");
    }
    return new int[] {width, height};
  }

  /** A chunk's bytes with its header and its padding byte (added when the file lacks it). */
  private static int paddedChunkBytes (byte[] webp, int chunkPos) {
    final long size = le32(webp, chunkPos + 4);
    return 8 + (int) size + (int) (size & 1);
  }

  /** The header and payload; the padding byte after an odd payload stays 0, as the spec wants (new arrays are zeroed). */
  private static void copyChunk (byte[] webp, int chunkPos, byte[] out, int outPos) {
    final int size = (int) le32(webp, chunkPos + 4);
    System.arraycopy(webp, chunkPos, out, outPos, 8 + size);
  }

  // Writing the animation

  /** The size of the file {@link #build} makes from {@code frames}. */
  static long fileBytes (@NonNull List<Frame> frames) {
    long bytes = HEADER_BYTES;
    for (Frame frame : frames) {
      bytes += frame.fileBytes();
    }
    return bytes;
  }

  /** The animated WebP: a {@code canvasWidth} x {@code canvasHeight} transparent canvas, looping forever. */
  @NonNull
  static byte[] build (@NonNull List<Frame> frames, int canvasWidth, int canvasHeight) throws IOException {
    if (frames.isEmpty()) {
      throw new IOException("No frames");
    }
    if (canvasWidth <= 0 || canvasHeight <= 0 || canvasWidth > MAX_SIZE || canvasHeight > MAX_SIZE) {
      throw new IOException("Bad canvas " + canvasWidth + "x" + canvasHeight);
    }
    for (Frame frame : frames) {
      if ((frame.x & 1) != 0 || (frame.y & 1) != 0 || frame.x < 0 || frame.y < 0) {
        throw new IOException("Frame offsets must be even: " + frame.x + "," + frame.y);
      }
      if (frame.width <= 0 || frame.height <= 0 || frame.x + frame.width > canvasWidth || frame.y + frame.height > canvasHeight) {
        throw new IOException("Frame outside the canvas");
      }
      if (frame.durationMs < 0 || frame.durationMs > MAX_24) {
        throw new IOException("Bad frame duration " + frame.durationMs);
      }
      if ((frame.data.length & 1) != 0) {
        throw new IOException("Unpadded frame data");
      }
    }
    final long total = fileBytes(frames);
    if (total - 8 > 0xFFFFFFFEL || total > Integer.MAX_VALUE - 8) {
      throw new IOException("Too big");
    }
    final byte[] out = new byte[(int) total];
    int p = 0;

    // RIFF header
    p = putFourcc(out, p, "RIFF");
    p = putLe32(out, p, total - 8);
    p = putFourcc(out, p, "WEBP");

    // VP8X: flags, 3 reserved bytes, canvas size minus one
    p = putFourcc(out, p, "VP8X");
    p = putLe32(out, p, 10);
    out[p++] = (byte) (FLAG_ALPHA | FLAG_ANIMATION);
    out[p++] = 0;
    out[p++] = 0;
    out[p++] = 0;
    p = putLe24(out, p, canvasWidth - 1);
    p = putLe24(out, p, canvasHeight - 1);

    // ANIM: transparent background, loop forever
    p = putFourcc(out, p, "ANIM");
    p = putLe32(out, p, 6);
    out[p++] = 0; // blue
    out[p++] = 0; // green
    out[p++] = 0; // red
    out[p++] = 0; // alpha
    out[p++] = 0; // loop count, low byte
    out[p++] = 0; // loop count, high byte

    for (Frame frame : frames) {
      p = putFourcc(out, p, "ANMF");
      p = putLe32(out, p, 16 + frame.data.length);
      p = putLe24(out, p, frame.x / 2);
      p = putLe24(out, p, frame.y / 2);
      p = putLe24(out, p, frame.width - 1);
      p = putLe24(out, p, frame.height - 1);
      p = putLe24(out, p, frame.durationMs);
      out[p++] = (byte) ((frame.noBlend ? FRAME_NO_BLEND : 0) | (frame.dispose ? FRAME_DISPOSE : 0));
      System.arraycopy(frame.data, 0, out, p, frame.data.length);
      p += frame.data.length;
    }
    if (p != out.length) {
      throw new IllegalStateException(p + " != " + out.length);
    }
    return out;
  }

  /**
   * A still sticker (WhatsApp's 512x512 WebP) as an animation of two equal frames, {@code frameMs} each, for an animated
   * pack (WhatsApp wants more than one frame there). It looks just like the still one; the frames share no data, so
   * the file is about twice the still one (100 KB at most, so well under the 500 KB an animated sticker may have).
   */
  @NonNull
  static byte[] stillToAnimated (@NonNull byte[] still, int canvasSize, int frameMs) throws IOException {
    final Image image = readStill(still, still.length);
    if (image.width > canvasSize || image.height > canvasSize) {
      throw new IOException("Still sticker " + image.width + "x" + image.height + " is bigger than " + canvasSize);
    }
    // Centred, at even offsets; a smaller image leaves a transparent border
    final int x = ((canvasSize - image.width) / 2) & ~1, y = ((canvasSize - image.height) / 2) & ~1;
    final List<Frame> frames = new ArrayList<>(2);
    frames.add(new Frame(image.data, x, y, image.width, image.height, frameMs, true, false));
    frames.add(new Frame(image.data, x, y, image.width, image.height, frameMs, true, false));
    return build(frames, canvasSize, canvasSize);
  }

  // Bytes

  static boolean fourcc (byte[] data, int pos, String tag) {
    if (pos < 0 || pos + 4 > data.length) {
      return false;
    }
    for (int i = 0; i < 4; i++) {
      if (data[pos + i] != (byte) tag.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static long le32 (byte[] data, int pos) {
    return (data[pos] & 0xffL) | ((data[pos + 1] & 0xffL) << 8) | ((data[pos + 2] & 0xffL) << 16) | ((data[pos + 3] & 0xffL) << 24);
  }

  private static int le16 (byte[] data, int pos) {
    return (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8);
  }

  private static int putFourcc (byte[] out, int p, String tag) {
    for (int i = 0; i < 4; i++) {
      out[p + i] = (byte) tag.charAt(i);
    }
    return p + 4;
  }

  private static int putLe32 (byte[] out, int p, long value) {
    out[p] = (byte) value;
    out[p + 1] = (byte) (value >>> 8);
    out[p + 2] = (byte) (value >>> 16);
    out[p + 3] = (byte) (value >>> 24);
    return p + 4;
  }

  private static int putLe24 (byte[] out, int p, int value) {
    if (value < 0 || value > MAX_24) {
      throw new IllegalArgumentException(Integer.toString(value));
    }
    out[p] = (byte) value;
    out[p + 1] = (byte) (value >>> 8);
    out[p + 2] = (byte) (value >>> 16);
    return p + 3;
  }
}
