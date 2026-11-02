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

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.FileUpdateListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tgx.td.Td;

/**
 * Puts a page's media into the .zip before the page that shows them. Files are downloaded the way Telegram X's own
 * screens do it, through TdlibFilesManager's shared downloads (a cloud reference): the chat the user has open keeps
 * its downloads, its own cancel doesn't stop the export's, and the export's priority (4) stays below what is on
 * screen. While one file is copied the next three (at most 256 MB) are already downloading. A download that makes no
 * progress for 2 minutes while connected is given up; offline nothing counts and the job says "Waiting for network".
 * What the export downloaded itself is deleted from Telegram's cache right after it is copied (the thumbnails are made
 * first), so a chat with 100k files doesn't fill the phone; what was there before stays. Media are downloaded even
 * when Data Saver or automatic download is off: the export is the user's explicit choice (the sheet says so).
 */
final class ExportDownloader implements Closeable {
  private static final int PRIORITY = 4;
  private static final int LOOK_AHEAD_FILES = 3;
  private static final long LOOK_AHEAD_BYTES = 256L * 1024L * 1024L;
  private static final long STALL_MS = 120_000L;
  /** A download that stopped (someone canceled it) while the export still wants it is asked for again. */
  private static final long RESTART_AFTER_MS = 3_000L;
  private static final int MAX_RESTARTS = 3;
  private static final long WAIT_SLICE_MS = 250L;
  /** Free space kept on top of what a file needs. */
  private static final long SPACE_MARGIN = 100L * 1024L * 1024L;
  /** Files already in the .zip, by file id: the same file in many messages goes in once. */
  private static final int DONE_CACHE = 4096;

  private static final int OK = 0, UNAVAILABLE = 1, TOO_BIG = 2, STOPPED = 3;

  private final Tdlib tdlib;
  private final ExportJob job;
  private final ExportZip zip;
  private final String root;
  private final long sizeLimit;
  @Nullable
  private final File zipVolume;
  @Nullable
  private final File appVolume;
  private final ExportMedia names = new ExportMedia();
  private final Map<Integer, Download> active = new HashMap<>();
  private final Map<Long, ExportMedia.Result> done = new LinkedHashMap<Long, ExportMedia.Result>(256, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry (Map.Entry<Long, ExportMedia.Result> eldest) {
      return size() > DONE_CACHE;
    }
  };
  /** No more media: the storage is nearly full, the .zip reached Android 6's limits, or the time limit came. */
  private boolean outOfSpace, zipFull, stopped;

  ExportDownloader (Tdlib tdlib, ExportJob job, ExportZip zip, String root, long sizeLimit, @Nullable File zipVolume, @Nullable File appVolume) {
    this.tdlib = tdlib;
    this.job = job;
    this.zip = zip;
    this.root = root;
    this.sizeLimit = sizeLimit;
    this.zipVolume = zipVolume;
    this.appVolume = appVolume;
  }

  // One shared download (TdlibFilesManager's cloud reference) and the updates of its file

  private static final class Download implements FileUpdateListener {
    final int id;
    /** Complete and on disk before the export looked: nothing to download, and never deleted. */
    final boolean cached;
    /** Not downloaded, not downloading, nothing of it there (or gone before its copy): the export started it and may delete it. */
    final boolean ours;
    final long size;
    boolean referenced;
    private final Object lock = new Object();
    @NonNull
    private TdApi.File latest;

    Download (@NonNull TdApi.File file, boolean cached, boolean ours) {
      this.id = file.id;
      this.cached = cached;
      this.ours = ours;
      this.size = ExportMedia.sizeOf(file);
      this.latest = file;
    }

    @NonNull
    TdApi.File latest () {
      synchronized (lock) {
        return latest;
      }
    }

    @Override
    public void onUpdateFile (TdApi.UpdateFile update) {
      if (update.file == null || update.file.id != id) {
        return;
      }
      synchronized (lock) {
        latest = update.file;
        lock.notifyAll();
      }
    }

    /** Waits for an update after {@code seen}, at most {@code timeoutMs}. */
    void waitForUpdate (TdApi.File seen, long timeoutMs) throws InterruptedException {
      synchronized (lock) {
        if (latest == seen) {
          lock.wait(timeoutMs);
        }
      }
    }
  }

  /**
   * The page's media, in message order, before the page is written. Each result gets its path or why it was left out.
   * {@code reserveBytes} / {@code reserveEntries}: what the rest of the export still needs in the .zip (Android 6's
   * limits). Throws {@link ExportJob.Canceled}; a time-limit stop leaves the page's remaining media out instead.
   */
  @WorkerThread
  void writePage (List<ExportJob.Item> items, long reserveBytes, int reserveEntries) throws IOException {
    final List<ExportJob.Item> work = new ArrayList<>();
    for (ExportJob.Item item : items) {
      if (item.media != null && item.media.plan.included()) {
        work.add(item);
      }
    }
    try {
      for (int i = 0; i < work.size(); i++) {
        job.checkCanceled();
        final ExportJob.Item item = work.get(i);
        final ExportMedia.Result result = item.media;
        //noinspection ConstantConditions
        if (!stopped && job.shouldStop()) {
          stopped = true; // the page is finished without the rest of its media, then the job saves what it has
        }
        if (stopped) {
          leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
        } else {
          prefetch(work, i + 1);
          write(item.message, result, reserveBytes, reserveEntries);
        }
        job.onMediaDone(result.plan.size);
      }
    } finally {
      releaseAll(); // what a stop or a cancel left downloading
    }
  }

  private static long keyOf (ExportMedia.Plan plan) {
    // A chat photo and the same photo in a message get different pictures
    return ((long) plan.file.id << 4) | plan.kind;
  }

  private void write (TdApi.Message message, ExportMedia.Result result, long reserveBytes, int reserveEntries) throws IOException {
    final ExportMedia.Plan plan = result.plan;
    final ExportMedia.Result previous = done.get(keyOf(plan));
    if (previous != null) {
      result.copyFrom(previous);
      return;
    }
    if (outOfSpace || zipFull) {
      leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
      return;
    }
    final int extraEntries = 1 + (plan.thumbnail != null ? 1 : 0);
    if (!zip.canAddMedia(plan.size + result.thumbnailSize(), extraEntries, reserveBytes, reserveEntries)) {
      zipFull = true;
      job.onLegacyZipLimit();
      leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
      return;
    }
    final long timeMs = message.date * 1000L;
    Download download = start(plan.file, false, false);
    String path = null;
    for (int attempt = 0; ; attempt++) {
      if (download == null) {
        leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
        return;
      }
      final int state = await(download, sizeLimit);
      if (state != OK) {
        release(download, true);
        if (state == TOO_BIG) {
          leaveOut(result, ExportMedia.SKIP_SIZE, false); // an unknown size that turned out over the limit
        } else {
          if (state == STOPPED) {
            stopped = true;
          }
          leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
        }
        if (state != STOPPED) {
          done.put(keyOf(plan), result); // the same file in later messages isn't waited for again
        }
        return;
      }
      final File local = new File(download.latest().local.path);
      if (!hasRoom(local.length() + SPACE_MARGIN, false)) {
        outOfSpace = true;
        job.onLowSpace();
        release(download, true);
        leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
        return;
      }
      if (path == null) {
        path = zip.uniquePath(root + names.pathOf(plan, message.date)); // its counter counts once, as in Desktop
      }
      try {
        zip.putFileStored(path, local, timeMs);
      } catch (ExportZip.SourceException e) {
        // The file went away before its entry started (a cache cleanup): download it once more
        Log.w("XGram export: file %d unreadable, %s", plan.file.id, e.getMessage());
        release(download, false);
        if (attempt == 0) {
          download = start(Td.copyOf(download.latest()), false, true); // a copy: start() refreshes it, and listeners got this one
          continue;
        }
        leaveOut(result, ExportMedia.SKIP_UNAVAILABLE, true);
        return;
      }
      result.skip = ExportMedia.SKIP_NONE;
      result.path = relative(path);
      writePicture(result, local, path, timeMs); // from the downloaded file, before it is deleted
      release(download, true);
      break;
    }
    writeThumbnail(result, timeMs);
    done.put(keyOf(plan), result);
  }

  /**
   * The page's picture of a photo, a WEBP sticker or a chat photo, made from the file (Desktop's WriteImageThumb); for a
   * video or GIF with only an animated preview, its JPEG thumbnail.
   */
  private void writePicture (ExportMedia.Result result, File local, String path, long timeMs) throws IOException {
    final ExportMedia.Thumb thumb;
    switch (result.plan.kind) {
      case ExportMedia.KIND_PHOTO:
        thumb = ExportMedia.imageThumb(local, ExportMedia.PHOTO_MAX, false);
        break;
      case ExportMedia.KIND_STICKER:
        thumb = result.plan.staticSticker ? ExportMedia.imageThumb(local, ExportMedia.STICKER_MAX, true) : null;
        break;
      case ExportMedia.KIND_CHAT_PHOTO:
        thumb = ExportMedia.servicePhotoThumb(local);
        break;
      case ExportMedia.KIND_VIDEO:
      case ExportMedia.KIND_ANIMATION:
        if (result.plan.frameThumb) {
          writeFrameThumbnail(result, local, path, timeMs);
        }
        return;
      default:
        return;
    }
    if (thumb == null) {
      return; // Desktop's generic block, linking to the file
    }
    if (thumb.data == null) {
      result.imagePath = result.path; // already small enough: the page shows the file itself
    } else {
      final String thumbPath = zip.uniquePath(ExportMedia.thumbPathOf(path));
      zip.putBytesStored(thumbPath, thumb.data, timeMs);
      result.imagePath = relative(thumbPath);
    }
    result.imageWidth = thumb.width;
    result.imageHeight = thumb.height;
  }

  /**
   * Desktop's "<file>_thumb.jpg" of a video or GIF whose sender added an animated preview: TDLib then gives only that
   * MPEG4, not the JPEG, so a frame of the downloaded file stands in for it.
   */
  private void writeFrameThumbnail (ExportMedia.Result result, File local, String path, long timeMs) throws IOException {
    final byte[] data = ExportMedia.videoFrameThumb(local);
    if (data == null) {
      result.thumbSkip = ExportMedia.SKIP_UNAVAILABLE;
      return;
    }
    final String thumbPath = zip.uniquePath(path + "_thumb.jpg");
    zip.putBytesStored(thumbPath, data, timeMs);
    result.thumbPath = relative(thumbPath);
    result.thumbSkip = ExportMedia.SKIP_NONE;
    result.thumbBytes = data.length;
  }

  /** Telegram's thumbnail of a document as "<file>_thumb.jpg" (Desktop's ParseDocumentThumb), after its file. */
  private void writeThumbnail (ExportMedia.Result result, long timeMs) throws IOException {
    final TdApi.Thumbnail thumbnail = result.plan.thumbnail;
    if (thumbnail == null || result.path == null || result.plan.frameThumb) {
      return; // frameThumb: made from the file in writePicture
    }
    if (outOfSpace || zipFull || stopped) {
      result.thumbSkip = ExportMedia.SKIP_UNAVAILABLE;
      return;
    }
    final Download download = start(thumbnail.file, false, false);
    if (download == null) {
      result.thumbSkip = ExportMedia.SKIP_UNAVAILABLE;
      return;
    }
    try {
      final int state = await(download, 0);
      if (state != OK) {
        if (state == STOPPED) {
          stopped = true;
        }
        result.thumbSkip = ExportMedia.SKIP_UNAVAILABLE;
        return;
      }
      final String path = zip.uniquePath(root + result.path + "_thumb.jpg");
      try {
        zip.putFileStored(path, new File(download.latest().local.path), timeMs);
      } catch (ExportZip.SourceException e) {
        result.thumbSkip = ExportMedia.SKIP_UNAVAILABLE;
        return;
      }
      result.thumbPath = relative(path);
      result.thumbSkip = ExportMedia.SKIP_NONE;
    } finally {
      release(download, true);
    }
  }

  /** Why the file is not in the export; its thumbnail shares the reason (Desktop skips thumbnails of skipped files). */
  private void leaveOut (ExportMedia.Result result, int reason, boolean failed) {
    result.skip = reason;
    result.thumbSkip = reason;
    result.path = null;
    if (failed) {
      job.onMediaUnavailable();
    }
  }

  private String relative (String path) {
    return path.startsWith(root) ? path.substring(root.length()) : path;
  }

  // Downloads

  /** Downloads of the next files of the page, while the current one is fetched or copied. */
  private void prefetch (List<ExportJob.Item> work, int from) {
    if (outOfSpace || zipFull || stopped) {
      return;
    }
    long ahead = 0;
    int count = 0;
    for (int j = from; j < work.size() && count < LOOK_AHEAD_FILES; j++) {
      //noinspection ConstantConditions
      final ExportMedia.Plan plan = work.get(j).media.plan;
      if (done.containsKey(keyOf(plan)) || TD.isFileLoaded(plan.file)) {
        continue; // already in the .zip, or nothing to download
      }
      if (plan.size <= 0 || ahead + plan.size > LOOK_AHEAD_BYTES) {
        break; // an unknown size may pass the limit: it waits for its turn
      }
      if (start(plan.file, true, false) == null) {
        break;
      }
      ahead += plan.size;
      count++;
    }
  }

  /**
   * The file's download, started when it isn't there yet; null when the storage can't take it. A file needs about
   * twice its size while it is copied (Telegram's copy and the .zip's), plus what is downloading ahead.
   */
  @Nullable
  private Download start (TdApi.File file, boolean prefetch, boolean refetch) {
    Download download = active.get(file.id);
    if (download != null) {
      return download;
    }
    // The page's copy can be old: Telegram X may have downloaded the file since (and it must not be deleted), or a cleanup removed it
    tdlib.files().syncFile(file, null, 5_000L);
    final boolean cached = TD.isFileLoadedAndExists(file);
    final TdlibFilesManager files = tdlib.files();
    if (!cached) {
      final long size = ExportMedia.sizeOf(file);
      if (!hasRoom(2L * size + inFlightBytes() + SPACE_MARGIN, true)) {
        if (!prefetch) {
          outOfSpace = true;
          job.onLowSpace();
        }
        return null;
      }
    }
    final TdApi.LocalFile local = file.local;
    // refetch: the file went away before its copy, so this download is the export's whatever the old state says
    final boolean ours = !cached && (refetch || (local != null && !local.isDownloadingActive && local.downloadedSize == 0 && !files.hasPendingOperation(file.id)));
    download = new Download(file, cached, ours);
    active.put(file.id, download);
    if (!cached) {
      if (local != null && local.isDownloadingCompleted) {
        // Telegram has it as downloaded but the file is gone: a request makes TDLib look again and download it anew
        tdlib.client().send(new TdApi.DownloadFile(file.id, PRIORITY, 0, 0, false), tdlib.silentHandler());
      }
      tdlib.listeners().addFileListener(file.id, download);
      files.addCloudReference(file, PRIORITY, download, false);
      download.referenced = true;
    }
    return download;
  }

  /** Bytes still to download of the files in flight. */
  private long inFlightBytes () {
    long bytes = 0;
    for (Download download : active.values()) {
      if (!download.cached) {
        final TdApi.File file = download.latest();
        bytes += Math.max(0, ExportMedia.sizeOf(file) - (file.local != null ? file.local.downloadedSize : 0));
      }
    }
    return bytes;
  }

  /** Until the file is on disk, over {@code limit} (0: none), unavailable, or the job stops. Throws Canceled. */
  private int await (Download download, long limit) {
    if (download.cached) {
      return OK;
    }
    long lastBytes = -1, lastProgressAt = SystemClock.elapsedRealtime(), inactiveSince = 0;
    int restarts = 0;
    try {
      while (true) {
        job.checkCanceled();
        if (job.shouldStop()) {
          return STOPPED;
        }
        final TdApi.File file = download.latest();
        final TdApi.LocalFile local = file.local;
        if (local == null) {
          return UNAVAILABLE;
        }
        if (local.isDownloadingCompleted && local.path != null && !local.path.isEmpty() && new File(local.path).exists()) {
          return OK;
        }
        if (!local.canBeDownloaded && !local.isDownloadingCompleted) {
          return UNAVAILABLE;
        }
        if (limit > 0 && (ExportMedia.sizeOf(file) > limit || local.downloadedSize > limit)) {
          return TOO_BIG;
        }
        final long now = SystemClock.elapsedRealtime();
        final boolean connected = tdlib.isConnected();
        job.setWaitingForNetwork(!connected);
        if (local.downloadedSize != lastBytes) {
          lastBytes = local.downloadedSize;
          lastProgressAt = now;
          job.setMediaFileBytes(lastBytes);
        } else if (!connected) {
          lastProgressAt = now; // offline the stall timer stands still
        } else if (now - lastProgressAt >= STALL_MS) {
          Log.w("XGram export: file %d made no progress for %d s", download.id, STALL_MS / 1000);
          return UNAVAILABLE;
        }
        if (!local.isDownloadingActive && !local.isDownloadingCompleted) {
          if (inactiveSince == 0) {
            inactiveSince = now;
          } else if (connected && now - inactiveSince >= RESTART_AFTER_MS) {
            if (restarts >= MAX_RESTARTS) {
              // A queued download counts as active: this one stopped (TDLib gave up on it), no use waiting for the stall
              return UNAVAILABLE;
            }
            restart(download); // canceled from elsewhere (Data Saver turning on cancels Telegram X's own downloads)
            restarts++;
            inactiveSince = 0;
          }
        } else {
          inactiveSince = 0;
        }
        try {
          download.waitForUpdate(file, WAIT_SLICE_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ExportJob.Canceled();
        }
      }
    } finally {
      job.setWaitingForNetwork(false);
      job.setMediaFileBytes(0);
    }
  }

  /** Asks for the file again: the export's reference is dropped and taken anew, which sends a new request. */
  private void restart (Download download) {
    if (!download.referenced) {
      return;
    }
    final TdApi.File file = download.latest();
    tdlib.files().removeCloudReference(file, download);
    tdlib.files().addCloudReference(file, PRIORITY, download, false);
  }

  /**
   * Ends the export's part in a download. With {@code cleanUp}, a file the export started is deleted from Telegram's
   * cache (also a partial one), unless Telegram X's screens wanted it meanwhile: they keep it.
   */
  private void release (Download download, boolean cleanUp) {
    if (active.get(download.id) == download) {
      active.remove(download.id);
    }
    final TdlibFilesManager files = tdlib.files();
    if (download.referenced) {
      download.referenced = false;
      files.removeCloudReference(download.latest(), download);
      tdlib.listeners().removeFileListener(download.id, download);
    }
    // Not hasPendingOperation: the export's own download sets it, and the cancel above clears it only later. A tap on
    // the file's download button (or automatic download) is downloadFile, not a cloud reference: that file stays too.
    // DeleteFile also stops a download that is still running and removes its part.
    if (cleanUp && download.ours && !files.xgramHasCloudReference(download.id) && !files.xgramWasDownloadRequested(download.id)) {
      tdlib.client().send(new TdApi.DeleteFile(download.id), tdlib.silentHandler());
    }
  }

  private void releaseAll () {
    if (active.isEmpty()) {
      return;
    }
    for (Download download : new ArrayList<>(active.values())) {
      release(download, true);
    }
  }

  /** Whether both the .zip's storage and Telegram's (usually one partition) have {@code bytes} free. */
  private boolean hasRoom (long bytes, boolean includeApp) {
    final long zipFree = ExportJob.freeBytes(zipVolume);
    if (zipFree >= 0 && zipFree < bytes) {
      return false;
    }
    if (includeApp) {
      final long appFree = ExportJob.freeBytes(appVolume);
      return appFree < 0 || appFree >= bytes;
    }
    return true;
  }

  /** Stops the downloads still running and deletes what the export started of them. */
  @Override
  public void close () {
    try {
      releaseAll();
    } catch (Throwable t) {
      Log.w("XGram export: releasing downloads", t);
    }
  }
}
