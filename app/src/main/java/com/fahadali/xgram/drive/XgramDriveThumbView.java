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
package com.fahadali.xgram.drive;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;

/**
 * XGram Drive (Expressive design): a row's 40dp leading element in the Compose list. A file's preview (photos,
 * videos, covers) as a 12dp-corner tile, or its kind's icon on a secondaryContainer tile; a folder's icon on
 * primaryContainer; a selected row's check on primary. The page tells it when its selection changes (refresh).
 */
public final class XgramDriveThumbView extends android.view.View implements DrawableProvider {
  private static final float SIZE = 40f, CORNER = 12f;

  private final ComplexReceiver receiver;
  private final SparseArrayCompat<Drawable> drawables = new SparseArrayCompat<>();
  private final RectF rect = new RectF();

  private @Nullable XgramDriveController controller;
  private @Nullable XgramDrive.Entry entry;
  private @Nullable String folderPath;
  private @Nullable MediaPreview preview;
  private long previewKey;
  private boolean selected;

  public XgramDriveThumbView (Context context) {
    super(context);
    this.receiver = new ComplexReceiver(this);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  /** A file of the page. */
  public void setEntry (XgramDriveController controller, XgramDrive.Entry entry) {
    this.controller = controller;
    this.folderPath = null;
    this.entry = entry;
    long key = entry.id();
    TdApi.File file = entry.file();
    if (file != null) {
      key = key * 31 + file.id; // a copy that is up gets its own key
    }
    if (preview == null || previewKey != key) {
      previewKey = key;
      preview = entry.document != null ? MediaPreview.valueOf(controller.tdlib(), entry.document, Screen.dp(SIZE), Screen.dp(CORNER)) : null;
      if (preview != null) {
        preview.requestFiles(receiver, false);
      } else {
        receiver.clear();
      }
    }
    refresh();
  }

  /** A folder of the page. */
  public void setFolder (XgramDriveController controller, String path) {
    this.controller = controller;
    this.entry = null;
    this.folderPath = path;
    if (preview != null) {
      preview = null;
      previewKey = 0;
      receiver.clear();
    }
    refresh();
  }

  /** Reads the row's selection again. */
  public void refresh () {
    boolean selected = controller != null && entry != null && controller.xgramIsSelected(entry);
    if (this.selected != selected) {
      this.selected = selected;
    }
    invalidate();
  }

  public void destroy () {
    if (controller != null) {
      controller.xgramUnregisterThumb(this);
    }
    controller = null;
    entry = null;
    preview = null;
    receiver.performDestroy();
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    receiver.attach();
    if (controller != null) {
      controller.xgramRegisterThumb(this);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    receiver.detach();
    if (controller != null) {
      controller.xgramUnregisterThumb(this);
    }
  }

  @Override
  public SparseArrayCompat<Drawable> getSparseDrawableHolder () {
    return drawables;
  }

  @Override
  public Resources getSparseDrawableResources () {
    return getResources();
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int width = getWidth(), height = getHeight();
    final int size = Math.min(width, height);
    if (size <= 0) {
      return;
    }
    final float left = (width - size) / 2f, top = (height - size) / 2f;
    final float corner = Screen.dp(CORNER);
    rect.set(left, top, left + size, top + size);
    final float cx = rect.centerX(), cy = rect.centerY();
    if (selected) {
      c.drawRoundRect(rect, corner, corner, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.PRIMARY)));
      drawIcon(c, R.drawable.baseline_check_24, cx, cy, XgramExpressive.color(XgramExpressive.ON_PRIMARY));
      return;
    }
    if (folderPath != null) {
      c.drawRoundRect(rect, corner, corner, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER)));
      drawIcon(c, R.drawable.baseline_folder_24, cx, cy, XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER));
      return;
    }
    if (preview != null) {
      if (preview.needPlaceholder(receiver)) {
        c.drawRoundRect(rect, corner, corner, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST)));
      }
      preview.draw(this, c, receiver, rect.left, rect.top, rect.width(), rect.height(), (int) corner, 1f);
      return;
    }
    final boolean failed = entry != null && entry.isFailed();
    c.drawRoundRect(rect, corner, corner, Paints.fillingPaint(XgramExpressive.color(failed ? XgramExpressive.ERROR_CONTAINER : XgramExpressive.SECONDARY_CONTAINER)));
    drawIcon(c, failed ? R.drawable.baseline_error_24 : iconOf(entry), cx, cy, XgramExpressive.color(failed ? XgramExpressive.ON_ERROR_CONTAINER : XgramExpressive.ON_SECONDARY_CONTAINER));
  }

  private void drawIcon (Canvas c, int iconRes, float cx, float cy, int color) {
    Drawable icon = getSparseDrawable(iconRes, 0);
    if (icon != null) {
      Drawables.drawCentered(c, icon, cx, cy, Paints.getPorterDuffPaint(color));
    }
  }

  /** The kind of a file without a preview, by its type. */
  static int iconOf (@Nullable XgramDrive.Entry entry) {
    if (entry == null) {
      return R.drawable.baseline_insert_drive_file_24;
    }
    String mimeType = entry.mimeType();
    if (mimeType.startsWith("image/")) {
      return R.drawable.baseline_image_24;
    }
    if (mimeType.startsWith("video/")) {
      return R.drawable.baseline_videocam_24;
    }
    if (mimeType.startsWith("audio/")) {
      return R.drawable.baseline_music_note_24;
    }
    return R.drawable.baseline_insert_drive_file_24;
  }
}
