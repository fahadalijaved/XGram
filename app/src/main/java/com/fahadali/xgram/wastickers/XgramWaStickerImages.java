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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;

/**
 * XGram: Telegram's static stickers made into what WhatsApp takes. A sticker is exactly 512x512 WebP on a transparent
 * background, 100 KB at most; the tray icon is a 96x96 PNG, 50 KB at most. Telegram's static stickers are WebP with
 * 512 px on one side only, so they are centred on a 512x512 canvas; one that is already a 512x512 still WebP of 100 KB
 * or less is used as it is (no second lossy pass).
 */
final class XgramWaStickerImages {
  private XgramWaStickerImages () { }

  static final int STICKER_SIZE = 512;
  static final int STICKER_MAX_BYTES = 100 * 1024;
  static final int TRAY_SIZE = 96;
  static final int TRAY_MAX_BYTES = 50 * 1024;

  /** Lossy qualities tried in turn until the file fits. */
  private static final int[] QUALITIES = {95, 90, 85, 80, 75, 70, 60, 50, 40, 30, 20, 10};

  private static final int WEBP_NONE = 0, WEBP_LOSSY = 1, WEBP_LOSSLESS = 2, WEBP_ANIMATED = 3;

  /** What a WebP file holds: still lossy (VP8), still lossless (VP8L), animated (VP8X with ANIM), or not a WebP. */
  private static int webpKind (byte[] data) {
    if (data.length < 20 || !fourcc(data, 0, "RIFF") || !fourcc(data, 8, "WEBP")) {
      return WEBP_NONE;
    }
    int pos = 12;
    while (pos + 8 <= data.length) {
      final long size = (data[pos + 4] & 0xffL) | ((data[pos + 5] & 0xffL) << 8) | ((data[pos + 6] & 0xffL) << 16) | ((data[pos + 7] & 0xffL) << 24);
      if (fourcc(data, pos, "VP8X")) {
        if (pos + 8 < data.length && (data[pos + 8] & 0x02) != 0) {
          return WEBP_ANIMATED;
        }
      } else if (fourcc(data, pos, "ANIM") || fourcc(data, pos, "ANMF")) {
        return WEBP_ANIMATED;
      } else if (fourcc(data, pos, "VP8L")) {
        return WEBP_LOSSLESS;
      } else if (fourcc(data, pos, "VP8 ")) {
        return WEBP_LOSSY;
      }
      final long next = pos + 8 + size + (size & 1);
      if (next > Integer.MAX_VALUE) {
        break;
      }
      pos = (int) next;
    }
    return WEBP_NONE;
  }

  private static boolean fourcc (byte[] data, int pos, String tag) {
    if (pos + 4 > data.length) {
      return false;
    }
    for (int i = 0; i < 4; i++) {
      if (data[pos + i] != (byte) tag.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  /** A Telegram static sticker's file as WhatsApp's 512x512 WebP, or null when it can't be decoded or made to fit. */
  @Nullable
  @WorkerThread
  static byte[] toSticker (byte[] source) {
    final int kind = webpKind(source);
    if (kind == WEBP_ANIMATED) {
      return null; // a still pack must not hold an animated WebP (animated stickers: XgramWaStickerAnimator)
    }
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null;
    }
    if ((kind == WEBP_LOSSY || kind == WEBP_LOSSLESS) && bounds.outWidth == STICKER_SIZE && bounds.outHeight == STICKER_SIZE && source.length <= STICKER_MAX_BYTES) {
      return source;
    }
    Bitmap bitmap = decode(source, bounds, STICKER_SIZE);
    if (bitmap == null) {
      return null;
    }
    Bitmap canvas = fit(bitmap, STICKER_SIZE);
    bitmap.recycle();
    try {
      return encodeWebp(canvas, kind == WEBP_LOSSLESS, STICKER_MAX_BYTES);
    } finally {
      canvas.recycle();
    }
  }

  /** The tray icon: {@code source} (a still image) on a 96x96 transparent PNG, or null. */
  @Nullable
  @WorkerThread
  static byte[] toTray (byte[] source) {
    if (webpKind(source) == WEBP_ANIMATED) {
      return null;
    }
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null;
    }
    Bitmap bitmap = decode(source, bounds, TRAY_SIZE);
    if (bitmap == null) {
      return null;
    }
    try {
      return toTray(bitmap);
    } finally {
      bitmap.recycle();
    }
  }

  /** The tray icon: {@code bitmap} (any size, kept as it is) fitted on a 96x96 transparent PNG, or null. */
  @Nullable
  @WorkerThread
  static byte[] toTray (Bitmap bitmap) {
    if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
      return null;
    }
    Bitmap canvas = fit(bitmap, TRAY_SIZE);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
      if (!canvas.compress(Bitmap.CompressFormat.PNG, 100, out) || out.size() > TRAY_MAX_BYTES) {
        return null; // 96x96x4 bytes is 36 KB before compression, so it always fits
      }
      return out.toByteArray();
    } finally {
      canvas.recycle();
    }
  }

  @Nullable
  private static Bitmap decode (byte[] source, BitmapFactory.Options bounds, int targetSize) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    int sampleSize = 1;
    final int larger = Math.max(bounds.outWidth, bounds.outHeight);
    while (larger / (sampleSize * 2) >= targetSize * 2) {
      sampleSize *= 2;
    }
    options.inSampleSize = sampleSize;
    try {
      return BitmapFactory.decodeByteArray(source, 0, source.length, options);
    } catch (OutOfMemoryError e) {
      return null;
    }
  }

  /** {@code bitmap} scaled to fit a size x size transparent square, centred. */
  private static Bitmap fit (Bitmap bitmap, int size) {
    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    result.eraseColor(0);
    final float scale = Math.min((float) size / bitmap.getWidth(), (float) size / bitmap.getHeight());
    final float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
    final float left = (size - width) / 2f, top = (size - height) / 2f;
    Canvas c = new Canvas(result);
    c.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG));
    return result;
  }

  /**
   * WebP of at most {@code maxBytes}: lossless first when the source was lossless (flat art stays crisp, usually small),
   * then lossy from quality 95 down. Lossy WebP keeps the alpha channel lossless, so the edges stay clean.
   */
  @Nullable
  @SuppressWarnings("deprecation")
  private static byte[] encodeWebp (Bitmap bitmap, boolean tryLossless, int maxBytes) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    if (tryLossless && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 75, out) && out.size() <= maxBytes) {
        return out.toByteArray();
      }
    }
    final Bitmap.CompressFormat lossy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
    for (int quality : QUALITIES) {
      out.reset();
      if (bitmap.compress(lossy, quality, out) && out.size() <= maxBytes) {
        return out.toByteArray();
      }
    }
    return null;
  }
}
