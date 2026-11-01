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

import org.thunderdog.challegram.unsorted.AppContext;

import java.io.IOException;
import java.io.InputStream;

/** Telegram Desktop's stylesheet and script (bundled in assets/xgram_export) at the top of the export's folder. */
final class ExportAssets {
  private ExportAssets () { }

  private static final String[] FILES = {
    "css/style.css",
    "js/script.js"
  };

  static void write (ExportZip zip, String root, long timeMs) throws IOException {
    final AssetManager assets = AppContext.get().getAssets();
    for (String path : FILES) {
      try (InputStream in = assets.open("xgram_export/" + path)) {
        zip.putStream(root + path, in, timeMs, true);
      }
    }
  }
}
