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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import com.google.common.collect.ImmutableList;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;

/**
 * Makes the GIF a sent photo edit with animated layers becomes ({@link XgramAnimatedEdit}), when TDLib asks for its
 * file (TdlibFileGenerationManager), one at a time:
 * <ol>
 *   <li>The still part is baked once as the photo is (PhotoGenerationInfo: filters, crop, turn, mirror, the drawings
 *   and the layers under the lowest animated one), at most 1280 pixels and even, on white.</li>
 *   <li>Each frame is drawn on the CPU, as PaintState draws the layers: the still, then copies of the lowest animated
 *   layer and every layer over it where the photo's processing put the drawing, the animated ones with their frame
 *   for that time (XgramFrameSource, GIFs and WebM by their timestamps, TGS at up to 30 fps), shorter loops
 *   repeating.</li>
 *   <li>Media3 Transformer encodes it: the still as an image input ({@link EditedMediaItem} with a duration and
 *   {@link #FRAME_RATE}), one {@link OverlayEffect} with one full-frame, opaque {@link BitmapOverlay} giving each
 *   frame, H.264 without audio. A full-frame overlay keeps every Media3 flavor's overlay settings out (1.2.1 to 1.11)
 *   and draws the layers' edges exactly (Media3 blends overlays as unpremultiplied, Android bitmaps are
 *   premultiplied).</li>
 * </ol>
 * The bubble shows the progress (TDLib's generation progress, {@link #getEncodingProgress}), deleting the message
 * cancels it, and if it fails the photo is sent instead ({@link XgramAnimatedEdit#onExportFailed}).
 */
public final class XgramAnimatedEditExport {
  /** Frames a second of the GIF. */
  private static final int FRAME_RATE = 30;
  /** Bits a pixel a frame, as a Telegram GIF has about, within these bitrates. */
  private static final double BITRATE_SCALE = .07;
  private static final int MIN_BITRATE = 800_000, MAX_BITRATE = 4_000_000;
  /** TGS frames are drawn at the size they show in the GIF, within these. */
  private static final int MIN_LOTTIE_SIZE = 128, MAX_LOTTIE_SIZE = 1024;
  private static final long PROGRESS_INTERVAL_MS = 250;

  private final Tdlib tdlib;

  public XgramAnimatedEditExport (@NonNull Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  // One GIF at a time (encoders are few), on a thread of its own: Transformer runs on its looper

  private static BaseThread queue;
  private static final ArrayDeque<Job> pendingJobs = new ArrayDeque<>();
  private static @Nullable Job runningJob;

  private static BaseThread queue () {
    synchronized (XgramAnimatedEditExport.class) {
      if (queue == null) {
        queue = new BaseThread("XgramGifExport");
      }
      return queue;
    }
  }

  /**
   * TDLib asked for the file of a GIF made of an edit (its conversion starts with XgramAnimatedEdit's prefix). The
   * returned info is the generation's: cancelling it stops the GIF.
   */
  @AnyThread
  public GenerationInfo start (long generationId, @NonNull String originalPath, @NonNull String destinationPath, @NonNull String conversion) {
    final Job job = new Job(tdlib, generationId, originalPath, destinationPath, conversion);
    job.setOnCancel(job::stop);
    setProgress(destinationPath, 0f);
    queue().post(() -> {
      pendingJobs.add(job);
      runNextJob();
    }, 0);
    return job;
  }

  // The queue thread
  private static void runNextJob () {
    while (runningJob == null && !pendingJobs.isEmpty()) {
      final Job job = pendingJobs.poll();
      if (!job.finished.get()) {
        runningJob = job;
        job.run();
      }
    }
  }

  // The queue thread
  private static void onJobEnded (@NonNull Job job) {
    if (runningJob == job) {
      runningJob = null;
      queue().post(XgramAnimatedEditExport::runNextJob, 0);
    }
  }

  private static final class Job extends GenerationInfo {
    private final Tdlib tdlib;
    final AtomicBoolean finished = new AtomicBoolean();
    // The queue thread's
    private @Nullable Frames frames;
    private @Nullable File stillFile;
    private @Nullable Transformer transformer;
    private long durationMs, expectedSize;

    Job (Tdlib tdlib, long generationId, String originalPath, String destinationPath, String conversion) {
      super(generationId, originalPath, destinationPath, conversion, false);
      this.tdlib = tdlib;
    }

    // The queue thread
    void run () {
      if (finished.get()) {
        onJobEnded(this);
        return;
      }
      try {
        prepare();
        if (finished.get()) { // cancelled meanwhile
          release();
          onJobEnded(this);
          return;
        }
        export();
      } catch (Throwable t) {
        fail(t);
      }
    }

    private void prepare () throws Throwable {
      final String photoConversion = XgramAnimatedEdit.photoConversionOf(conversion);
      if (StringUtils.isEmpty(photoConversion)) {
        throw new IllegalArgumentException("No photo in the conversion");
      }
      final String photoPath = XgramAnimatedEdit.photoPathOf(originalPath);
      final PhotoGenerationInfo photo = new PhotoGenerationInfo(generationId, photoPath, destinationPath, photoConversion);
      final PaintState paintState = photo.getPaintState();
      if (paintState == null) {
        throw new IllegalStateException("The edit has no drawing");
      }

      // the still part: the drawings and the layers under the lowest animated one; the others go on each frame
      final int firstAnimated = paintState.getFirstAnimatedLayerIndex();
      final List<XgramLayer> frameLayers = firstAnimated != -1 ? paintState.copyLayersFrom(firstAnimated) : Collections.emptyList();
      photo.xgramSetPaintState(paintState.copyForAnimatedExport(firstAnimated != -1 ? firstAnimated : Integer.MAX_VALUE));
      final Bitmap processed = bake(photo, photoPath);
      final Matrix matrix = new Matrix();
      final RectF paintRect = new RectF();
      if (!photo.xgramGetPaintPlacement(matrix, paintRect)) {
        processed.recycle();
        throw new IllegalStateException("The drawing wasn't placed");
      }

      // at most 1280 pixels, even, on white (the GIF has no transparency)
      final int[] size = XgramAnimatedEdit.outputSize(processed.getWidth(), processed.getHeight());
      final Bitmap still = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
      final Canvas c = new Canvas(still);
      c.drawColor(0xffffffff);
      c.drawBitmap(processed, null, new Rect(0, 0, size[0], size[1]), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
      matrix.postScale((float) size[0] / (float) processed.getWidth(), (float) size[1] / (float) processed.getHeight());
      processed.recycle();

      // the layers drawn on each frame, the animated ones with a decoder each
      final float frameScale = matrix.mapRadius(1f);
      final List<Item> items = new ArrayList<>(frameLayers.size());
      long longestLoop = -1;
      try {
        for (XgramLayer layer : frameLayers) {
          final Item item = new Item(layer);
          items.add(item);
          if (layer instanceof XgramStickerLayer && ((XgramStickerLayer) layer).isAnimated()) {
            final XgramStickerLayer sticker = (XgramStickerLayer) layer;
            final int shownSize = (int) Math.ceil(sticker.getSize() * paintRect.width() * frameScale);
            final XgramFrameSource source = XgramFrameSource.open(sticker.getFilePath(), sticker.getFormat(), Math.max(MIN_LOTTIE_SIZE, Math.min(MAX_LOTTIE_SIZE, shownSize)));
            if (source != null) {
              item.setSource(source, 0); // freed with the item
              long loop = XgramFrameSource.getKnownLoopDuration(sticker.getFilePath(), sticker.getFormat()); // the preview's, measured the same way
              if (loop <= 0) {
                loop = source.measureLoopDuration(XgramAnimatedEdit.MAX_DURATION_MS);
              }
              if (loop > 0 && !source.isStill()) {
                item.setSource(source, loop);
                longestLoop = Math.max(longestLoop, loop);
              } else {
                item.release(); // one frame: its picture shows
              }
            }
          }
        }
      } catch (Throwable t) {
        for (Item item : items) {
          item.release();
        }
        throw t;
      }
      durationMs = XgramAnimatedEdit.computeDuration(longestLoop);

      final Frames frames = new Frames(still, matrix, paintRect, items);
      this.frames = frames;
      frames.getBitmap(0); // the first frame, here: the layers' pictures load and the decoders start

      // Transformer's input: the still as a picture (each frame covers it)
      final File stillFile = new File(AppContext.get().getCacheDir(), "xgram_gif_" + generationId + ".jpg");
      this.stillFile = stillFile;
      try (FileOutputStream out = new FileOutputStream(stillFile)) {
        if (!still.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
          throw new IOException("Unable to write the GIF's still");
        }
      }
    }

    /** The photo with the still part of the edit, as TdlibFileGenerationManager.generatePhoto makes it. */
    @NonNull
    private static Bitmap bake (@NonNull PhotoGenerationInfo photo, @NonNull String path) throws Throwable {
      final BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = 1;
      if (!photo.isFiltered()) {
        try (InputStream is = U.openInputStream(path)) {
          opts.inJustDecodeBounds = true;
          BitmapFactory.decodeStream(is, null, opts);
          opts.inSampleSize = ImageReader.ceilInSampleSize(opts.outWidth, opts.outHeight, PhotoGenerationInfo.SIZE_LIMIT, PhotoGenerationInfo.SIZE_LIMIT);
        }
      }
      opts.inJustDecodeBounds = false;
      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
      Bitmap bitmap;
      try (InputStream is = U.openInputStream(path)) {
        bitmap = photo.readImage(is, opts, path);
      }
      if (bitmap == null) {
        throw new IOException("Unable to read the photo");
      }
      if (!photo.needSpecialProcessing(true)) {
        bitmap.recycle();
        throw new IllegalStateException("The edit needs no processing");
      }
      final Bitmap processed = photo.process(bitmap, true);
      if (processed == null) {
        bitmap.recycle();
        throw new IOException("Unable to process the photo");
      }
      if (processed != bitmap) {
        bitmap.recycle();
      }
      return processed;
    }

    private void export () {
      final Context context = AppContext.get();
      final File stillFile = this.stillFile;
      final Frames frames = this.frames;
      if (stillFile == null || frames == null) {
        throw new IllegalStateException();
      }
      final MediaItem mediaItem = new MediaItem.Builder()
        .setUri(Uri.fromFile(stillFile))
        .setMimeType(MimeTypes.IMAGE_JPEG)
        .setImageDurationMs(durationMs)
        .build();
      final EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
        .setRemoveAudio(true)
        .setDurationUs(durationMs * 1000L)
        .setFrameRate(FRAME_RATE)
        .setEffects(new Effects(
          Collections.emptyList(),
          ImmutableList.<Effect>of(new OverlayEffect(ImmutableList.<TextureOverlay>of(frames)))
        ))
        .build();

      final int bitrate = (int) Math.max(MIN_BITRATE, Math.min(MAX_BITRATE, Math.round((double) frames.width * frames.height * FRAME_RATE * BITRATE_SCALE)));
      expectedSize = Math.max(1, (long) bitrate * durationMs / 8000L);

      final Transformer.Builder builder = new Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setEncoderFactory(new DefaultEncoderFactory.Builder(context)
          .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
            .setBitrate(bitrate)
            .build())
          .build())
        .addListener(new Transformer.Listener() {
          @Override
          public void onCompleted (@NonNull Composition composition, @NonNull ExportResult exportResult) {
            complete();
          }

          @Override
          public void onError (@NonNull Composition composition, @NonNull ExportResult exportResult, @NonNull ExportException exportException) {
            fail(exportException);
          }
        });
      tgx.flavor.Flavor.setGifPortraitEncoding(builder); // a portrait GIF stays portrait, without a rotation tag
      final Transformer transformer = builder.build();
      this.transformer = transformer;
      transformer.start(editedMediaItem, destinationPath);
      reportProgress(0f);
      queue().post(progressRunner, PROGRESS_INTERVAL_MS);
    }

    private final Runnable progressRunner = new Runnable() {
      @Override
      public void run () {
        final Transformer transformer = Job.this.transformer;
        if (finished.get() || transformer == null) {
          return;
        }
        // the time of the last frame drawn (Transformer's progress of an image input is full once the picture is in)
        final Frames frames = Job.this.frames;
        if (frames != null && durationMs > 0) {
          reportProgress(Math.max(0f, Math.min(1f, frames.drawnTimeUs / (float) (durationMs * 1000L))));
        }
        queue().post(this, PROGRESS_INTERVAL_MS);
      }
    };

    /** The ring of the message's bubble: TDLib tells it of a new expected size (the file isn't written in order). */
    private void reportProgress (float progress) {
      setProgress(destinationPath, progress);
      final long percent = Math.round(progress * 100f);
      tdlib.client().send(new TdApi.SetFileGenerationProgress(generationId, expectedSize + percent, 0), tdlib.silentHandler());
    }

    // The queue thread (Transformer's listener)
    private void complete () {
      if (finished.getAndSet(true)) {
        return;
      }
      release();
      setProgress(destinationPath, 1f); // the ring goes on with the upload
      if (Settings.instance().needSaveEditedMediaToGallery()) {
        saveToGallery();
      }
      XgramAnimatedEdit.forget(conversion);
      tdlib.filegen().finishGeneration(this);
      onJobEnded(this);
    }

    /** "Save edited media to gallery": the GIF goes there as a video (the generated file is TDLib's). */
    private void saveToGallery () {
      final File copy = new File(AppContext.get().getCacheDir(), "xgram_gif_" + generationId + ".mp4");
      if (!FileUtils.copy(new File(destinationPath), copy)) {
        Log.w("Unable to keep the GIF of a photo edit for the gallery");
        return;
      }
      UI.post(() -> {
        final BaseActivity activity = UI.getUiContext();
        if (activity != null) {
          U.copyToGallery(activity, copy.getPath(), U.TYPE_VIDEO, false, saved -> copy.delete());
        } else if (!copy.delete()) {
          Log.w("Unable to delete the gallery copy of a GIF");
        }
      });
    }

    // The queue thread
    private void fail (@Nullable Throwable t) {
      if (finished.getAndSet(true)) {
        return;
      }
      Log.e("Unable to make the GIF of a photo edit", t);
      release();
      removeProgress(destinationPath);
      XgramAnimatedEdit.onExportFailed(tdlib, conversion, () ->
        tdlib.filegen().failGeneration(this, -1, "Unable to make the GIF of a photo edit")
      );
      onJobEnded(this);
    }

    /** TDLib stopped the generation (the message was deleted, its sending cancelled): GenerationInfo.cancel(). */
    private void stop () {
      if (finished.getAndSet(true)) {
        return;
      }
      Log.i("Cancelling the GIF of a photo edit");
      removeProgress(destinationPath);
      XgramAnimatedEdit.forget(conversion);
      tdlib.settings().forgetConversion(originalPath, conversion); // not a try that didn't end
      queue().post(() -> {
        final Transformer transformer = this.transformer;
        this.transformer = null;
        if (transformer != null) {
          try {
            transformer.cancel();
          } catch (Throwable t) {
            Log.w("Unable to cancel the GIF of a photo edit", t);
          }
        }
        release();
        onJobEnded(this);
      }, 0);
    }

    // The queue thread
    private void release () {
      final Frames frames = this.frames;
      this.frames = null;
      if (frames != null) {
        frames.releaseFrames();
      }
      final File stillFile = this.stillFile;
      this.stillFile = null;
      if (stillFile != null && stillFile.exists() && !stillFile.delete()) {
        Log.w("Unable to delete the still of a GIF");
      }
    }
  }

  /**
   * The GIF's frames, for Media3's overlay (on its GL thread): each one the still with the layers over it at that
   * time, drawn into one of two bitmaps in turn (the other may be the one uploaded last), opaque and as big as the
   * frame, so the overlay covers it with its default settings.
   */
  private static final class Frames extends BitmapOverlay {
    private final Object lock = new Object();
    private final Bitmap still;
    private final Matrix matrix;
    private final RectF paintRect;
    private final List<Item> items;
    final int width, height;
    private final Bitmap[] buffers = new Bitmap[2];
    private final Canvas[] canvases = new Canvas[2];
    private int current = -1;
    private long currentTimeUs;
    /** The time of the last frame drawn, for the progress. */
    volatile long drawnTimeUs = -1;
    private boolean released;

    Frames (@NonNull Bitmap still, @NonNull Matrix matrix, @NonNull RectF paintRect, @NonNull List<Item> items) {
      this.still = still;
      this.matrix = matrix;
      this.paintRect = paintRect;
      this.items = items;
      this.width = still.getWidth();
      this.height = still.getHeight();
    }

    @NonNull
    @Override
    public Bitmap getBitmap (long presentationTimeUs) throws VideoFrameProcessingException {
      synchronized (lock) {
        if (released) {
          throw new VideoFrameProcessingException("The GIF's frames were released", presentationTimeUs);
        }
        if (current != -1 && presentationTimeUs == currentTimeUs) {
          return buffers[current];
        }
        final int next = current == 0 ? 1 : 0;
        try {
          if (buffers[next] == null) {
            buffers[next] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvases[next] = new Canvas(buffers[next]);
          }
          drawFrame(canvases[next], Math.max(0, presentationTimeUs) / 1000L);
        } catch (Throwable t) {
          throw new VideoFrameProcessingException(t, presentationTimeUs);
        }
        current = next;
        currentTimeUs = presentationTimeUs;
        drawnTimeUs = presentationTimeUs;
        return buffers[next];
      }
    }

    private void drawFrame (@NonNull Canvas c, long timeMs) {
      c.drawBitmap(still, 0, 0, null);
      final int saveCount = c.save();
      c.concat(matrix);
      for (Item item : items) {
        item.draw(c, paintRect, timeMs);
      }
      c.restoreToCount(saveCount);
    }

    @Override
    public void release () throws VideoFrameProcessingException {
      try {
        super.release(); // its texture, on the GL thread
      } finally {
        releaseFrames();
      }
    }

    /** Frees the decoders (any thread): no frame is drawn after. */
    void releaseFrames () {
      synchronized (lock) {
        if (released) {
          return;
        }
        released = true;
        for (Item item : items) {
          item.release();
        }
      }
    }
  }

  /** A layer drawn on each frame; an animated one moves on through its frames, its loop repeating. */
  private static final class Item {
    final XgramLayer layer;
    private @Nullable XgramFrameSource source;
    private long loop;
    private @Nullable Bitmap front, back;
    private long frontTime, backTime, loopStart;
    private boolean started, backReady;

    Item (@NonNull XgramLayer layer) {
      this.layer = layer;
    }

    void setSource (@NonNull XgramFrameSource source, long loop) {
      this.source = source;
      this.loop = loop;
    }

    void draw (@NonNull Canvas c, @NonNull RectF rect, long timeMs) {
      final XgramFrameSource source = this.source;
      if (source != null) {
        advanceTo(source, timeMs);
        ((XgramStickerLayer) layer).setExportFrame(this.source != null ? front : null, source.getRotation());
      }
      layer.draw(c, rect.left, rect.top, rect.width(), rect.height());
    }

    /** The frame shown at {@code timeMs} (from the GIF's start) becomes the front one. */
    private void advanceTo (@NonNull XgramFrameSource source, long timeMs) {
      if (!started) {
        started = true;
        front = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        back = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        if (source.readNextFrame(front) < 0) {
          release(); // its picture shows
          return;
        }
        frontTime = 0;
        readBack(source);
      }
      while (backReady && backTime <= timeMs) {
        final Bitmap shown = front;
        front = back;
        back = shown;
        frontTime = backTime;
        readBack(source);
      }
    }

    private void readBack (@NonNull XgramFrameSource source) {
      final Bitmap back = this.back;
      long time = back != null ? source.readNextFrame(back) : -1;
      if (time < 0 && back != null) {
        // the end of a file that doesn't start over by itself: again from its start (a new loop)
        source.seekToStart();
        time = source.readNextFrame(back);
      }
      if (time < 0) {
        backReady = false; // the last frame read stays
        return;
      }
      if (source.isLoopStart()) {
        loopStart += loop;
      }
      // later than the frame before it, whatever the file says
      backTime = Math.max(loopStart + time, frontTime + XgramFrameSource.MIN_FRAME_DURATION);
      backReady = true;
    }

    void release () {
      final XgramFrameSource source = this.source;
      this.source = null;
      backReady = false;
      if (source != null) {
        source.release();
      }
    }
  }

  // The progress of the GIFs being made, by the generated file's path (TDLib's file.local.path while it is made) and,
  // once seen there, by its file id, for the ring of the message's bubble (FileProgressComponent)

  private static final class Progress {
    volatile float value;
  }

  private static final int MAX_PROGRESS_ENTRIES = 32;
  private static final Map<String, Progress> progressByPath = newBoundedMap();
  private static final Map<String, Progress> progressByFile = newBoundedMap();

  private static <T> Map<String, T> newBoundedMap () {
    return new LinkedHashMap<String, T>() {
      @Override
      protected boolean removeEldestEntry (Map.Entry<String, T> eldest) {
        return size() > MAX_PROGRESS_ENTRIES;
      }
    };
  }

  private static void setProgress (@NonNull String path, float value) {
    synchronized (progressByPath) {
      Progress progress = progressByPath.get(path);
      if (progress == null) {
        progress = new Progress();
        progressByPath.put(path, progress);
      }
      progress.value = value;
    }
  }

  private static void removeProgress (@NonNull String path) {
    synchronized (progressByPath) {
      final Progress progress = progressByPath.remove(path);
      if (progress != null) {
        progressByFile.values().remove(progress);
      }
    }
  }

  /**
   * How far the GIF of a photo edit sent in {@code file} has been made, 0..1 (1 once made, while it uploads), or -1
   * when {@code file} isn't one.
   */
  @AnyThread
  public static float getEncodingProgress (@Nullable Tdlib tdlib, @Nullable TdApi.File file) {
    if (tdlib == null || file == null) {
      return -1f;
    }
    final String fileKey = tdlib.id() + ":" + file.id;
    synchronized (progressByPath) {
      Progress progress = progressByFile.get(fileKey);
      if (progress == null && file.local != null && !StringUtils.isEmpty(file.local.path)) {
        progress = progressByPath.get(file.local.path);
        if (progress != null) {
          progressByFile.put(fileKey, progress);
        }
      }
      return progress != null ? progress.value : -1f;
    }
  }
}
