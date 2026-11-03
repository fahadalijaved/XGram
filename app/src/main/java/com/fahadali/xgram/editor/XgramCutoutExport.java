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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.Permissions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;

/**
 * A photo edit with a cut-out ({@link XgramCutout}, the Cut out tool) on its way out of the editor. A photo can't keep a
 * transparent background (Telegram makes it a JPEG, and TdlibFileGenerationManager paints it white), so the viewer's
 * Send shows choices instead of sending it ({@link #showChoices}, from MediaViewController.send):
 * <ul>
 *   <li>Send as sticker: trimmed to what is left of the photo (with a little margin), 512 px on its long side, sent as
 *   InputView sends a pasted transparent image (PhotoGenerationInfo.newFile(path, 0, time, true, 512): WEBP made by the
 *   generation) in an InputMessageSticker. Only one photo, without a timer, where stickers may be sent; the caption
 *   can't go with it (the choice says so).</li>
 *   <li>Send as PNG file: the whole edit, transparent, up to {@link #MAX_SIZE} px, as a document.</li>
 *   <li>Send as photo on white: the cut-out on a white background (and the only send choice as a profile photo and in
 *   place of a message's photo, where it reads "Use on a white background").</li>
 *   <li>Save PNG to gallery: the viewer stays open.</li>
 * </ul>
 * Each bakes the edit (the filters, the crop, the drawing and layers, the cut-out applied, all as the generation draws
 * them: {@link #render}) into a file of its own in the app's files, which then goes through the viewer's own send path
 * as an unedited photo in the edited one's place (with its caption and timer). The sticker's file is in a folder of its
 * own, from which MessagesController sends a photo as a sticker ({@link #isStickerFile}). With "Save edited media to
 * gallery" on, a sent cut-out's PNG is saved too. Baked files are kept {@link #KEEP_MS} (a message waiting to be sent
 * reads them), then cleared at the next bake.
 */
public final class XgramCutoutExport {
  private XgramCutoutExport () { }

  /** The long side of a baked edit at most (a photo on white is made smaller: PhotoGenerationInfo.SIZE_LIMIT). */
  private static final int MAX_SIZE = 2560;
  /** A sticker's long side, Telegram's. */
  private static final int STICKER_SIZE = 512;
  /** Around a sticker's subject this share of its long side stays transparent. */
  private static final float STICKER_MARGIN = .03f;
  /**
   * Pixels at least this opaque (of 255) are what is left of the photo. Fainter ones (the wand's soft edge, far out in
   * the background) don't make a sticker bigger.
   */
  private static final int VISIBLE_ALPHA = 24;
  /** A photo on white's JPEG quality (the generation compresses it again). */
  private static final int PHOTO_QUALITY = 95;
  /** How long a baked file is kept. */
  private static final long KEEP_MS = TimeUnit.DAYS.toMillis(7);

  private static final int OUTPUT_STICKER = 1, OUTPUT_FILE = 2, OUTPUT_PHOTO = 3, OUTPUT_GALLERY = 4;

  /** The viewer the choices send through. */
  public interface Host {
    /** Whether the viewer can still send (open, nothing sent yet). */
    boolean canSend ();

    /** The viewer is blocked while the cut-out is being made (busy), and not after. */
    void setBusy (boolean busy);

    /** Sends {@code files} (baked ones in the cut-outs' places) as the viewer's Send would, as documents with asFiles. */
    void send (@NonNull ArrayList<ImageFile> files, boolean asFiles);
  }

  // What has a cut-out

  /** Whether {@code file} is a photo with a cut-out that removes some of it. */
  public static boolean hasCutout (@Nullable ImageFile file) {
    if (!(file instanceof ImageGalleryFile) || ((ImageGalleryFile) file).isVideo()) {
      return false;
    }
    final PaintState state = file.getPaintState();
    return state != null && state.hasCutout();
  }

  /** Whether any of {@code files} is a photo with a cut-out. */
  public static boolean hasCutout (@Nullable List<? extends ImageFile> files) {
    if (files != null) {
      for (ImageFile file : files) {
        if (hasCutout(file)) {
          return true;
        }
      }
    }
    return false;
  }

  // Files

  private static File folder () {
    return new File(AppContext.get().getFilesDir(), "xgram_cutout");
  }

  /** Where the cut-outs to send as stickers are baked. */
  private static File stickerFolder () {
    return new File(folder(), "sticker");
  }

  /** Whether {@code file} is a cut-out baked to be sent as a sticker (MessagesController sends it as one). */
  public static boolean isStickerFile (@Nullable ImageGalleryFile file) {
    if (file == null || file.isVideo()) {
      return false;
    }
    final String path = file.getFilePath();
    if (StringUtils.isEmpty(path)) {
      return false;
    }
    final File parent = new File(path).getParentFile();
    return parent != null && parent.getAbsolutePath().equals(stickerFolder().getAbsolutePath());
  }

  /** The message content of a cut-out baked as a sticker ({@link #isStickerFile}): a sticker, made a 512 px WEBP. */
  @NonNull
  public static TdApi.InputMessageContent newStickerContent (@NonNull Tdlib tdlib, @NonNull ImageGalleryFile file, boolean isSecretChat) {
    final String path = file.getFilePath();
    final TdApi.InputFileGenerated generated = PhotoGenerationInfo.newFile(path, 0, new File(path).lastModified(), true, STICKER_SIZE);
    return tdlib.filegen().createThumbnail(new TdApi.InputMessageSticker(new TdApi.InputSticker(generated, null, file.getWidth(), file.getHeight()), null), isSecretChat);
  }

  // The choices

  private static boolean canSend (@NonNull Tdlib tdlib, @Nullable TdApi.Chat chat, @RightId int rightId) {
    return chat == null || tdlib.canSendMessage(chat, rightId);
  }

  /**
   * The viewer's Send with a cut-out among {@code files}: the choices, then the chosen one is baked and sent through
   * {@code host}. {@code sendsMessages} false: the photo becomes a profile photo or replaces a message's photo (only the
   * photo on white, and saving). {@code chat}, when known, hides what can't be sent there.
   */
  @UiThread
  public static void showChoices (@NonNull ViewController<?> context, @Nullable ThemeDelegate forcedTheme, @Nullable TdApi.Chat chat, boolean sendsMessages, @NonNull List<ImageFile> files, @NonNull Host host) {
    final Tdlib tdlib = context.tdlib();
    final ArrayList<ImageFile> items = new ArrayList<>(files);
    final int count = items.size();
    // the timers as they are now: "Send as view once" puts them on only while Send runs
    final TdApi.MessageSelfDestructType[] selfDestructTypes = new TdApi.MessageSelfDestructType[count];
    int cutouts = 0;
    boolean selfDestructs = false, othersAsFiles = true, captions = false;
    for (int i = 0; i < count; i++) {
      final ImageFile file = items.get(i);
      selfDestructTypes[i] = file.getSelfDestructType();
      if (selfDestructTypes[i] != null) {
        selfDestructs = true;
      }
      if (hasCutout(file)) {
        cutouts++;
        if (((ImageGalleryFile) file).hasCaption()) {
          captions = true;
        }
      } else if (!(file instanceof ImageGalleryFile) || !((ImageGalleryFile) file).canSendAsFile()) {
        othersAsFiles = false;
      }
    }
    if (cutouts == 0) {
      return;
    }

    final boolean sticker = sendsMessages && count == 1 && !selfDestructs && canSend(tdlib, chat, RightId.SEND_OTHER_MESSAGES);
    final boolean asFile = sendsMessages && !selfDestructs && othersAsFiles && canSend(tdlib, chat, RightId.SEND_DOCS);
    final boolean photo = !sendsMessages || canSend(tdlib, chat, RightId.SEND_PHOTOS);

    final int[] ids = new int[4], icons = new int[4];
    final String[] titles = new String[4];
    int size = 0;
    if (sticker) {
      ids[size] = R.id.xgram_cutout_sticker;
      titles[size] = Lang.getString(captions ? R.string.XgramCutoutSendStickerNoCaption : R.string.XgramCutoutSendSticker);
      icons[size] = R.drawable.deproko_baseline_stickers_24;
      size++;
    }
    if (asFile) {
      ids[size] = R.id.xgram_cutout_file;
      titles[size] = Lang.getString(count > 1 ? R.string.XgramCutoutSendFiles : R.string.XgramCutoutSendFile);
      icons[size] = R.drawable.baseline_insert_drive_file_24;
      size++;
    }
    if (photo) {
      ids[size] = R.id.xgram_cutout_photo;
      titles[size] = Lang.getString(sendsMessages ? R.string.XgramCutoutSendPhoto : R.string.XgramCutoutUsePhoto);
      icons[size] = R.drawable.baseline_image_24;
      size++;
    }
    ids[size] = R.id.xgram_cutout_save;
    titles[size] = Lang.getString(cutouts > 1 ? R.string.XgramCutoutSaveAll : R.string.XgramCutoutSave);
    icons[size] = R.drawable.baseline_file_download_24;
    size++;

    final int[] outIds = new int[size], outIcons = new int[size];
    final String[] outTitles = new String[size];
    System.arraycopy(ids, 0, outIds, 0, size);
    System.arraycopy(icons, 0, outIcons, 0, size);
    System.arraycopy(titles, 0, outTitles, 0, size);

    final CharSequence info = Lang.getString(sendsMessages ? R.string.XgramCutoutInfo : R.string.XgramCutoutInfoNoTransparency);
    context.showOptions(info, outIds, outTitles, null, outIcons, (itemView, id) -> {
      final int output =
        id == R.id.xgram_cutout_sticker ? OUTPUT_STICKER :
        id == R.id.xgram_cutout_file ? OUTPUT_FILE :
        id == R.id.xgram_cutout_photo ? OUTPUT_PHOTO :
        id == R.id.xgram_cutout_save ? OUTPUT_GALLERY : 0;
      if (output != 0) {
        start(context, items, selfDestructTypes, output, host);
      }
      return true;
    }, forcedTheme);
  }

  @UiThread
  private static void start (@NonNull ViewController<?> context, @NonNull ArrayList<ImageFile> items, @NonNull TdApi.MessageSelfDestructType[] selfDestructTypes, int output, @NonNull Host host) {
    if (output != OUTPUT_GALLERY && !host.canSend()) {
      return;
    }
    // the PNG goes to the gallery when asked, or with "Save edited media to gallery" on a send; the permission first
    // where Android still has it (a send goes on without the PNG saved when it's refused)
    final boolean toGallery = output == OUTPUT_GALLERY || Settings.instance().needSaveEditedMediaToGallery();
    if (toGallery && context.context().permissions().requestWriteExternalStorage(Permissions.WriteType.GALLERY, granted -> {
      if (granted || output != OUTPUT_GALLERY) {
        bake(items, selfDestructTypes, output, granted, host);
      }
    })) {
      return;
    }
    bake(items, selfDestructTypes, output, toGallery, host);
  }

  @UiThread
  private static void bake (@NonNull ArrayList<ImageFile> items, @NonNull TdApi.MessageSelfDestructType[] selfDestructTypes, int output, boolean toGallery, @NonNull Host host) {
    if (output != OUTPUT_GALLERY && !host.canSend()) {
      return;
    }
    host.setBusy(true);
    final Runnable slowHint = () -> UI.showToast(R.string.XgramCutoutPreparing, Toast.LENGTH_SHORT);
    UI.post(slowHint, 500);
    // after a filtered photo still being written (TdlibFileGenerationManager.saveFilteredBitmap, on ImageReader's thread)
    ImageReader.instance().post(() ->
      Background.instance().post(() -> bakeAll(items, selfDestructTypes, output, toGallery, slowHint, host))
    );
  }

  @WorkerThread
  private static void bakeAll (@NonNull ArrayList<ImageFile> items, @NonNull TdApi.MessageSelfDestructType[] selfDestructTypes, int output, boolean toGallery, @NonNull Runnable slowHint, @NonNull Host host) {
    removeOldFiles();
    final ArrayList<ImageFile> result = new ArrayList<>(items.size());
    final ArrayList<File> made = new ArrayList<>();
    int error = 0;
    for (int i = 0; i < items.size() && error == 0; i++) {
      final ImageFile item = items.get(i);
      if (!hasCutout(item)) {
        result.add(item);
        continue;
      }
      final ImageGalleryFile source = (ImageGalleryFile) item;
      Bitmap rendered = null;
      try {
        rendered = render(source);
        if (rendered == null) {
          error = R.string.XgramCutoutFailed;
          break;
        }
        final Rect visible = visibleBounds(rendered);
        if (visible == null) {
          error = R.string.XgramCutoutNothingLeft;
          break;
        }
        if (output == OUTPUT_GALLERY) {
          saveToGallery(rendered);
          continue;
        }
        final File file;
        final int width, height;
        switch (output) {
          case OUTPUT_STICKER: {
            final Bitmap sticker = makeSticker(rendered, visible);
            try {
              file = U.newFile(stickerFolder(), "sticker_" + U.getFileTimestamp(), "png");
              write(sticker, Bitmap.CompressFormat.PNG, 100, file);
              width = sticker.getWidth();
              height = sticker.getHeight();
            } finally {
              sticker.recycle();
            }
            break;
          }
          case OUTPUT_FILE: {
            file = U.newFile(folder(), "Cutout_" + U.getFileTimestamp(), "png");
            write(rendered, Bitmap.CompressFormat.PNG, 100, file);
            width = rendered.getWidth();
            height = rendered.getHeight();
            break;
          }
          case OUTPUT_PHOTO: {
            final Bitmap photo = onWhite(rendered, PhotoGenerationInfo.SIZE_LIMIT);
            try {
              file = U.newFile(folder(), "photo_" + U.getFileTimestamp(), "jpg");
              write(photo, Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, file);
              width = photo.getWidth();
              height = photo.getHeight();
            } finally {
              photo.recycle();
            }
            break;
          }
          default:
            throw new IllegalArgumentException(Integer.toString(output));
        }
        made.add(file);
        final ImageGalleryFile baked = new ImageGalleryFile(-1, file.getPath(), file.lastModified(), width, height, -1, false);
        baked.setCaption(source.getCaption(false, false));
        baked.setSelfDestructType(selfDestructTypes[i]);
        result.add(baked);
        if (toGallery) {
          // "Save edited media to gallery": the sent cut-out's PNG too (the PNG file itself when that is what goes)
          try {
            if (output == OUTPUT_FILE) {
              copyToGallery(file);
            } else {
              saveToGallery(rendered);
            }
          } catch (Throwable t) {
            Log.e("Unable to save the photo editor's cut-out", t);
          }
        }
      } catch (Throwable t) {
        Log.e("Unable to make the photo editor's cut-out", t);
        error = R.string.XgramCutoutFailed;
      } finally {
        if (rendered != null) {
          rendered.recycle();
        }
      }
    }

    final int errorRes = error;
    if (errorRes != 0) {
      deleteAll(made);
    }
    UI.post(() -> {
      UI.cancel(slowHint);
      host.setBusy(false);
      if (errorRes != 0) {
        UI.showToast(errorRes, Toast.LENGTH_SHORT);
      } else if (output == OUTPUT_GALLERY) {
        UI.showToast(R.string.XgramCutoutSaved, Toast.LENGTH_SHORT);
      } else if (host.canSend()) {
        // the timers Send had ("Send as view once" put the photos' own back as it returned) on the photos without a
        // cut-out too: without an error each item has its entry in result, in order (a baked file got its timer above)
        final TdApi.MessageSelfDestructType[] previous = new TdApi.MessageSelfDestructType[items.size()];
        for (int k = 0; k < result.size() && k < items.size(); k++) {
          final ImageFile original = items.get(k);
          previous[k] = original.getSelfDestructType();
          if (result.get(k) == original) {
            original.setSelfDestructType(selfDestructTypes[k]);
          }
        }
        host.send(result, output == OUTPUT_FILE);
        if (host.canSend()) {
          // not sent: the photos keep the timers they had
          for (int k = 0; k < result.size() && k < items.size(); k++) {
            items.get(k).setSelfDestructType(previous[k]);
          }
        }
      } else {
        Background.instance().post(() -> deleteAll(made));
      }
    });
  }

  // Baking

  /**
   * The edit of {@code file} as the generation makes a sent photo of it (TdlibFileGenerationManager.generatePhoto:
   * the filtered photo, turned and cropped, the drawing and the layers on it), with the cut-out cut out of the photo
   * (transparent there, under the drawing), at most {@link #MAX_SIZE} on its long side. Null when it can't be read.
   */
  @WorkerThread
  @Nullable
  static Bitmap render (@NonNull ImageGalleryFile file) throws Throwable {
    final PaintState paintState = file.getPaintState();
    final String path = file.getTargetPath();
    if (paintState == null || StringUtils.isEmpty(path)) {
      return null;
    }
    final PhotoGenerationInfo info = new PhotoGenerationInfo(-1, path, null, PhotoGenerationInfo.xgramMakeConversionWithoutPaint(file));
    info.xgramSetPaintState(paintState);
    info.xgramSetCutoutMode(PaintState.CUTOUT_APPLY);

    final BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;
    if (!info.isFiltered()) {
      try (InputStream is = U.openInputStream(path)) {
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, opts);
      }
      final int side = Math.max(opts.outWidth, opts.outHeight);
      while (side / (opts.inSampleSize * 2) >= MAX_SIZE) {
        opts.inSampleSize *= 2;
      }
    }
    opts.inJustDecodeBounds = false;
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
    final Bitmap bitmap;
    try (InputStream is = U.openInputStream(path)) {
      bitmap = info.readImage(is, opts, path);
    }
    if (bitmap == null) {
      return null;
    }
    final Bitmap processed = info.process(bitmap, true);
    if (processed != bitmap) {
      bitmap.recycle();
    }
    if (processed == null) {
      return null;
    }
    final int side = Math.max(processed.getWidth(), processed.getHeight());
    if (side <= MAX_SIZE) {
      return processed;
    }
    final float scale = (float) MAX_SIZE / (float) side;
    final Bitmap scaled = scale(processed, Math.round(processed.getWidth() * scale), Math.round(processed.getHeight() * scale));
    if (scaled != processed) {
      processed.recycle();
    }
    return scaled;
  }

  /** The rect of {@code bitmap} with what is left of the photo in it (pixels at least VISIBLE_ALPHA opaque), or null. */
  @Nullable
  private static Rect visibleBounds (@NonNull Bitmap bitmap) {
    final int width = bitmap.getWidth(), height = bitmap.getHeight();
    final int[] row = new int[width];
    int left = width, top = -1, right = -1, bottom = -1;
    for (int y = 0; y < height; y++) {
      bitmap.getPixels(row, 0, width, 0, y, width, 1);
      int first = -1;
      for (int x = 0; x < width; x++) {
        if ((row[x] >>> 24) >= VISIBLE_ALPHA) {
          first = x;
          break;
        }
      }
      if (first == -1) {
        continue;
      }
      int last = first;
      for (int x = width - 1; x > first; x--) {
        if ((row[x] >>> 24) >= VISIBLE_ALPHA) {
          last = x;
          break;
        }
      }
      left = Math.min(left, first);
      right = Math.max(right, last);
      if (top == -1) {
        top = y;
      }
      bottom = y;
    }
    return right == -1 ? null : new Rect(left, top, right + 1, bottom + 1);
  }

  /** {@code visible} of {@code rendered} with a little margin, its long side STICKER_SIZE (smaller or bigger). */
  @NonNull
  private static Bitmap makeSticker (@NonNull Bitmap rendered, @NonNull Rect visible) {
    final int contentWidth = visible.width(), contentHeight = visible.height();
    final float margin = Math.max(contentWidth, contentHeight) * STICKER_MARGIN;
    final float fullWidth = contentWidth + margin * 2f, fullHeight = contentHeight + margin * 2f;
    final float scale = (float) STICKER_SIZE / Math.max(fullWidth, fullHeight);
    final int width = Math.max(1, Math.min(STICKER_SIZE, Math.round(fullWidth * scale)));
    final int height = Math.max(1, Math.min(STICKER_SIZE, Math.round(fullHeight * scale)));
    final int scaledWidth = Math.max(1, Math.min(width, Math.round(contentWidth * scale)));
    final int scaledHeight = Math.max(1, Math.min(height, Math.round(contentHeight * scale)));

    // (createBitmap may give rendered itself back, scale() content itself: neither is recycled twice, rendered never)
    final Bitmap content = Bitmap.createBitmap(rendered, visible.left, visible.top, contentWidth, contentHeight);
    final Bitmap scaled = scale(content, scaledWidth, scaledHeight);
    final Bitmap sticker = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(sticker);
    c.drawBitmap(scaled, Math.round((width - scaledWidth) / 2f), Math.round((height - scaledHeight) / 2f), new Paint(Paint.FILTER_BITMAP_FLAG));
    if (scaled != content && scaled != rendered) {
      scaled.recycle();
    }
    if (content != rendered) {
      content.recycle();
    }
    return sticker;
  }

  /** {@code rendered} on white, its long side at most {@code maxSize}. */
  @NonNull
  private static Bitmap onWhite (@NonNull Bitmap rendered, int maxSize) {
    final float scale = Math.min(1f, (float) maxSize / (float) Math.max(rendered.getWidth(), rendered.getHeight()));
    final int width = Math.max(1, Math.round(rendered.getWidth() * scale));
    final int height = Math.max(1, Math.round(rendered.getHeight() * scale));
    final Bitmap scaled = scale(rendered, width, height);
    final Bitmap photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(photo);
    c.drawColor(Color.WHITE);
    c.drawBitmap(scaled, 0, 0, null);
    if (scaled != rendered) {
      scaled.recycle();
    }
    photo.setHasAlpha(false);
    return photo;
  }

  /**
   * {@code bitmap} at width x height: halved first while it is twice that or more (so a big shrink stays smooth), then
   * filtered to the size. {@code bitmap} itself when it is that size already; it is never recycled here.
   */
  @NonNull
  private static Bitmap scale (@NonNull Bitmap bitmap, int width, int height) {
    Bitmap current = bitmap;
    while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
      final Bitmap half = Bitmap.createScaledBitmap(current, Math.max(1, current.getWidth() / 2), Math.max(1, current.getHeight() / 2), true);
      if (current != bitmap && half != current) {
        current.recycle();
      }
      current = half;
    }
    if (current.getWidth() != width || current.getHeight() != height) {
      final Bitmap scaled = Bitmap.createScaledBitmap(current, width, height, true);
      if (current != bitmap && scaled != current) {
        current.recycle();
      }
      current = scaled;
    }
    return current;
  }

  private static void write (@NonNull Bitmap bitmap, @NonNull Bitmap.CompressFormat format, int quality, @NonNull File file) throws IOException {
    final File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
      throw new IOException("Unable to make " + parent.getName());
    }
    boolean written = false;
    try (FileOutputStream out = new FileOutputStream(file)) {
      written = bitmap.compress(format, quality, out);
    } finally {
      if (!written && file.exists() && !file.delete()) {
        Log.w("Unable to delete %s", file.getName());
      }
    }
    if (!written) {
      throw new IOException("Unable to write " + file.getName());
    }
  }

  /** {@code bitmap} as a PNG in the gallery (Pictures, the app's album). */
  @WorkerThread
  private static void saveToGallery (@NonNull Bitmap bitmap) throws IOException {
    final File file = U.generateMediaPath(false, "png");
    write(bitmap, Bitmap.CompressFormat.PNG, 100, file);
    U.addToGallery(file);
  }

  /** The PNG {@code png} copied to the gallery. */
  @WorkerThread
  private static void copyToGallery (@NonNull File png) throws IOException {
    final File file = U.generateMediaPath(false, "png");
    if (!FileUtils.copy(png, file)) {
      throw new IOException("Unable to copy " + png.getName());
    }
    U.addToGallery(file);
  }

  private static void deleteAll (@NonNull List<File> files) {
    for (File file : files) {
      if (file.exists() && !file.delete()) {
        Log.w("Unable to delete %s", file.getName());
      }
    }
  }

  /** Clears baked files older than KEEP_MS. */
  @WorkerThread
  private static void removeOldFiles () {
    final long before = System.currentTimeMillis() - KEEP_MS;
    for (File folder : new File[] {folder(), stickerFolder()}) {
      final File[] files = folder.listFiles();
      if (files == null) {
        continue;
      }
      for (File file : files) {
        if (file.isFile() && file.lastModified() < before && !file.delete()) {
          Log.w("Unable to delete %s", file.getName());
        }
      }
    }
  }
}
