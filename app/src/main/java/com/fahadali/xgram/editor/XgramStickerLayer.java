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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.tool.UI;

import java.io.File;

import me.vkryl.core.StringUtils;
import me.vkryl.core.util.Blob;

/**
 * A picture layer of the photo editor: an emoji (drawn with the phone's emoji font), or a sticker or a GIF from the
 * picker, kept as its file's path. Animated stickers and GIFs give their first frame. The picture is decoded once,
 * off the main thread in the editor (the layer shows once it is ready) and right away while the photo is made, and
 * kept for the layer and its copies (undo).
 */
public final class XgramStickerLayer extends XgramLayer {
  public static final int KIND_EMOJI = 0, KIND_FILE = 1;
  /** How a file is read: a picture (a WebP sticker), a Lottie animation (TGS) or a video (WebM stickers, GIFs). */
  public static final int FORMAT_STATIC = 0, FORMAT_LOTTIE = 1, FORMAT_VIDEO = 2;

  /** Emoji are drawn this many pixels tall, once; stickers are at most 512 pixels, GIF frames this many. */
  private static final int EMOJI_SIZE = 384, STICKER_MAX_SIZE = 512, VIDEO_MAX_SIZE = 960;

  private int kind = KIND_EMOJI;
  private String emoji = "", path = "";
  private int format = FORMAT_STATIC;
  /** Width / height. */
  private float aspect = 1f;

  private @Nullable Bitmap bitmap;
  private boolean loading, failed;
  private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
  private final RectF rect = new RectF();

  public XgramStickerLayer () { }

  public static XgramStickerLayer newEmoji (@NonNull String emoji) {
    XgramStickerLayer layer = new XgramStickerLayer();
    layer.kind = KIND_EMOJI;
    layer.emoji = emoji;
    layer.aspect = 1f;
    return layer;
  }

  public static XgramStickerLayer newFile (@NonNull String path, int format, float aspect) {
    XgramStickerLayer layer = new XgramStickerLayer();
    layer.kind = KIND_FILE;
    layer.path = path;
    layer.format = clampFormat(format);
    layer.aspect = aspect > 0f && !Float.isInfinite(aspect) ? aspect : 1f;
    return layer;
  }

  /** The way {@code format} of a sticker is read. */
  public static int formatOf (@Nullable TdApi.StickerFormat format) {
    if (format != null) {
      switch (format.getConstructor()) {
        case TdApi.StickerFormatTgs.CONSTRUCTOR:
          return FORMAT_LOTTIE;
        case TdApi.StickerFormatWebm.CONSTRUCTOR:
          return FORMAT_VIDEO;
      }
    }
    return FORMAT_STATIC;
  }

  private static int clampFormat (int format) {
    return format >= FORMAT_STATIC && format <= FORMAT_VIDEO ? format : FORMAT_STATIC;
  }

  @Override
  public int getType () {
    return TYPE_STICKER;
  }

  public boolean isEmoji () {
    return kind == KIND_EMOJI;
  }

  @NonNull
  @Override
  public XgramLayer copy () {
    XgramStickerLayer copy = new XgramStickerLayer();
    copyPositionTo(copy);
    copy.setContent(this);
    return copy;
  }

  @Override
  protected void setContent (@NonNull XgramLayer other) {
    XgramStickerLayer source = (XgramStickerLayer) other;
    synchronized (this) {
      final boolean samePicture = kind == source.kind && StringUtils.equalsOrBothEmpty(emoji, source.emoji) && StringUtils.equalsOrBothEmpty(path, source.path) && format == source.format;
      this.kind = source.kind;
      this.emoji = source.emoji;
      this.path = source.path;
      this.format = source.format;
      this.aspect = source.aspect;
      if (!samePicture) {
        this.bitmap = source.bitmap;
        this.failed = false;
      } else if (bitmap == null) {
        this.bitmap = source.bitmap;
      }
    }
  }

  @Override
  protected boolean contentEquals (@NonNull XgramLayer other) {
    XgramStickerLayer b = (XgramStickerLayer) other;
    return kind == b.kind && StringUtils.equalsOrBothEmpty(emoji, b.emoji) && StringUtils.equalsOrBothEmpty(path, b.path) && format == b.format && aspect == b.aspect;
  }

  @Override
  protected void getContentBounds (@NonNull RectF out) {
    final float width = aspect >= 1f ? 1f : aspect, height = aspect >= 1f ? 1f / aspect : 1f;
    out.set(-width / 2f, -height / 2f, width / 2f, height / 2f);
  }

  @Override
  protected void drawContent (@NonNull Canvas c, float unit, float alpha) {
    Bitmap bitmap = obtainBitmap();
    if (bitmap == null || bitmap.isRecycled()) {
      return;
    }
    final float width = unit * (aspect >= 1f ? 1f : aspect), height = unit * (aspect >= 1f ? 1f / aspect : 1f);
    rect.set(-width / 2f, -height / 2f, width / 2f, height / 2f);
    bitmapPaint.setAlpha((int) (255f * Math.max(0f, Math.min(1f, alpha))));
    c.drawBitmap(bitmap, null, rect, bitmapPaint);
  }

  // The picture

  /** Pictures already made, for the copies of a layer and the same emoji or sticker added twice. */
  private static final LruCache<String, Bitmap> cache = new LruCache<>(12);

  private String cacheKey () {
    return kind == KIND_EMOJI ? "e:" + emoji : "f:" + format + ":" + path;
  }

  /** Starts making the picture (the editor, as the layer is added). */
  public void preload () {
    if (obtainBitmapNow() == null) {
      requestLoad();
    }
  }

  @Nullable
  private Bitmap obtainBitmapNow () {
    Bitmap bitmap = this.bitmap;
    if (bitmap == null) {
      synchronized (cache) {
        bitmap = cache.get(cacheKey());
      }
      if (bitmap != null) {
        this.bitmap = bitmap;
      }
    }
    return bitmap;
  }

  @Nullable
  private Bitmap obtainBitmap () {
    Bitmap bitmap = obtainBitmapNow();
    if (bitmap != null || failed) {
      return bitmap;
    }
    if (UI.inUiThread()) {
      requestLoad();
      return null;
    }
    // making the photo: wait for it
    bitmap = decode();
    if (bitmap != null) {
      synchronized (cache) {
        cache.put(cacheKey(), bitmap);
      }
      this.bitmap = bitmap;
    } else {
      failed = true;
    }
    return bitmap;
  }

  private void requestLoad () {
    if (loading || failed || !UI.inUiThread()) {
      return;
    }
    loading = true;
    final String key = cacheKey();
    Background.instance().post(() -> {
      final Bitmap result = decode();
      UI.post(() -> {
        loading = false;
        if (!key.equals(cacheKey())) {
          return; // the layer became another picture (undo)
        }
        if (result != null) {
          synchronized (cache) {
            cache.put(key, result);
          }
          bitmap = result;
        } else {
          failed = true;
        }
        invalidateOwner();
      });
    });
  }

  @Nullable
  private Bitmap decode () {
    try {
      if (kind == KIND_EMOJI) {
        return drawEmoji(emoji);
      }
      if (StringUtils.isEmpty(path) || !new File(path).exists()) {
        return null;
      }
      switch (format) {
        case FORMAT_LOTTIE:
          return ImageReader.decodeLottieFrame(path, STICKER_MAX_SIZE);
        case FORMAT_VIDEO:
          return ImageReader.decodeVideoFrame(path, VIDEO_MAX_SIZE);
        default: {
          BitmapFactory.Options opts = new BitmapFactory.Options();
          opts.inJustDecodeBounds = true;
          BitmapFactory.decodeFile(path, opts);
          if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            return null;
          }
          opts.inSampleSize = ImageReader.calculateInSampleSize(opts, STICKER_MAX_SIZE, STICKER_MAX_SIZE);
          opts.inJustDecodeBounds = false;
          opts.inPreferredConfig = Bitmap.Config.ARGB_8888; // a software bitmap: the photo is made on a software canvas
          return BitmapFactory.decodeFile(path, opts);
        }
      }
    } catch (Throwable t) {
      Log.w("Unable to read a photo editor sticker: %s", t, kind == KIND_EMOJI ? emoji : path);
      return null;
    }
  }

  /** The emoji, as the phone draws it, filling a square bitmap. */
  @Nullable
  private static Bitmap drawEmoji (@Nullable String emoji) {
    if (StringUtils.isEmpty(emoji)) {
      return null;
    }
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setTextSize(100f);
    float width = paint.measureText(emoji);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float height = metrics.descent - metrics.ascent;
    if (width <= 0f || height <= 0f) {
      return null;
    }
    paint.setTextSize(100f * EMOJI_SIZE * .92f / Math.max(width, height));
    width = paint.measureText(emoji);
    metrics = paint.getFontMetrics();
    Bitmap bitmap = Bitmap.createBitmap(EMOJI_SIZE, EMOJI_SIZE, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    c.drawText(emoji, (EMOJI_SIZE - width) / 2f, EMOJI_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }

  // Blob

  @Override
  protected void saveContent (@NonNull Blob blob) {
    blob.writeByte((byte) kind);
    blob.writeString(emoji != null ? emoji : "");
    blob.writeString(path != null ? path : "");
    blob.writeByte((byte) format);
    blob.writeFloat(aspect);
  }

  @Override
  protected void restoreContent (@NonNull Blob blob) {
    kind = blob.readByte() == KIND_FILE ? KIND_FILE : KIND_EMOJI;
    emoji = blob.readString();
    path = blob.readString();
    format = clampFormat(blob.readByte());
    final float aspect = blob.readFloat();
    this.aspect = aspect > 0f && !Float.isInfinite(aspect) ? aspect : 1f;
  }
}
