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

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Telegram Desktop's stylesheet and script (bundled in assets/xgram_export) at the top of the export's folder, and the
 * images/ icons its stylesheet draws in the coloured circles of media blocks: Desktop ships white glyphs on a clear
 * background, here they are drawn from XGram's own icons at 24 px and 48 px (@2x). The section_* icons of Desktop's
 * full-account export are not used by a single chat.
 */
final class ExportAssets {
  private ExportAssets () { }

  private static final String[] FILES = {
    "css/style.css",
    "js/script.js"
  };

  /** The images style.css refers to for chat pages, and the icon each is drawn from. */
  private static final String[] IMAGE_NAMES = {
    "back", "media_call", "media_contact", "media_file", "media_game", "media_location",
    "media_music", "media_photo", "media_shop", "media_video", "media_voice"
  };
  private static final int[] IMAGE_ICONS = {
    R.drawable.baseline_arrow_back_24, R.drawable.baseline_call_24, R.drawable.baseline_person_24, R.drawable.baseline_insert_drive_file_24,
    R.drawable.baseline_videogame_asset_16, R.drawable.baseline_location_on_24, R.drawable.baseline_music_note_24, R.drawable.baseline_camera_alt_24,
    R.drawable.baseline_shopping_bag_24, R.drawable.baseline_videocam_24, R.drawable.baseline_mic_24
  };
  private static final int ICON_SIZE = 24;

  static void write (ExportZip zip, String root, long timeMs) throws IOException {
    final AssetManager assets = AppContext.get().getAssets();
    for (String path : FILES) {
      try (InputStream in = assets.open("xgram_export/" + path)) {
        zip.putStream(root + path, in, timeMs, true);
      }
    }
    for (int i = 0; i < IMAGE_NAMES.length; i++) {
      for (int scale = 1; scale <= 2; scale++) {
        final String path = root + "images/" + IMAGE_NAMES[i] + (scale == 2 ? "@2x" : "") + ".png";
        final byte[] png = renderIcon(IMAGE_ICONS[i], ICON_SIZE * scale);
        if (png != null) {
          zip.putBytesStored(path, png, timeMs);
        } else {
          zip.reservePath(path); // no media file takes its name
        }
      }
    }
  }

  /** A white glyph on a clear background, {@code size} px square, as PNG; null when the icon can't be drawn. */
  @Nullable
  private static byte[] renderIcon (@DrawableRes int icon, int size) {
    Bitmap bitmap = null;
    try {
      final Drawable drawable = Drawables.get(AppContext.get().getResources(), icon); // a mutated copy: the tint stays here
      if (drawable == null) {
        return null;
      }
      drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
      bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      final Canvas canvas = new Canvas(bitmap);
      drawable.setBounds(0, 0, size, size);
      drawable.draw(canvas);
      final ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
        return null;
      }
      return out.toByteArray();
    } catch (Throwable t) {
      Log.w("XGram export: icon %d", t, icon);
      return null;
    } finally {
      if (bitmap != null) {
        bitmap.recycle();
      }
    }
  }
}
