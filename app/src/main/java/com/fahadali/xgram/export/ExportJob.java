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

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import tgx.td.MessageId;

/**
 * One chat export, run on its own background thread from start to finish (one at a time, in any account):
 * <ol>
 *   <li>pass 1 reads the history newest → oldest and keeps only the message ids, on disk;</li>
 *   <li>a free space check;</li>
 *   <li>pass 2 reads the ids oldest → newest, 1000 at a time, fetches those messages, puts their media into the .zip
 *   ({@link ExportDownloader}), then writes messagesN.html into the .zip and the JSON into a spool file;</li>
 *   <li>result.json goes in last, the .zip is published.</li>
 * </ol>
 * Memory does not grow with the chat. The job holds a TDLib reference and a partial wake lock while it runs (TDLib
 * pauses 5 s after the UI is gone otherwise), and only this thread touches the output: a cancel just sets a flag, and
 * the worker deletes the unfinished .zip itself. {@link XgramChatExportService} keeps XGram in the foreground
 * meanwhile; the progress notification follows the job. Android 15's dataSync time limit (or a soft deadline before
 * it) stops the job between pages, and what was written is saved as a partial .zip ({@link #PHASE_PARTIAL}).
 */
public final class ExportJob {
  public static final int PHASE_INDEXING = 0;
  public static final int PHASE_CHECKING_SPACE = 1;
  public static final int PHASE_WRITING = 2;
  public static final int PHASE_FINALIZING = 3;
  public static final int PHASE_DONE = 4;
  public static final int PHASE_FAILED = 5;
  public static final int PHASE_CANCELED = 6;
  /** Android's time limit stopped it: what was written is saved, with a last page saying where it stopped. */
  public static final int PHASE_PARTIAL = 7;

  /** Where the foreground service is: not started yet, holding the job, refused by Android, or gone (time limit). */
  static final int FOREGROUND_PENDING = 0, FOREGROUND_ON = 1, FOREGROUND_REFUSED = 2, FOREGROUND_ENDED = 3;

  private static final String JOB_REFERENCE = "xgramExport";
  private static final long WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L;
  private static final long PUBLISH_INTERVAL_MS = 250;
  private static final long NOTIFY_INTERVAL_MS = 1000;
  /**
   * Android 15+ gives dataSync services 6 hours a day (reset when XGram comes to the front): the job finishes a partial
   * .zip after 5.5 hours in the background, while the service is still in the foreground, instead of waiting for
   * onTimeout. That limit is shared with the contacts sync, so onTimeout can still come first.
   */
  private static final long TIME_LIMIT_SOFT_MS = 330L * 60L * 1000L;
  private static final int FETCH_BATCH = 100;
  /** Room left free on top of the export's own estimate. */
  private static final long SPACE_MARGIN = 200L * 1024L * 1024L;
  private static final long BYTES_PER_MESSAGE = 1536;
  private static final int MAX_NAME_BYTES = 250; // 255 less ".part"

  /** Main-thread callbacks. */
  public interface Listener {
    @UiThread
    void onProgress (ExportJob job, ExportProgress progress);

    @UiThread
    void onFinished (ExportJob job);
  }

  /** Thrown on the worker once the job is canceled. */
  static final class Canceled extends RuntimeException {
    Canceled () {
      super("Export canceled");
    }
  }

  /** Thrown on the worker where it can stop safely (reading the history, fetching a page) once Android's time limit is near. */
  static final class Stopped extends RuntimeException {
    Stopped () {
      super("Export stopped by the time limit");
    }
  }

  /** A failure with a message for the user. */
  private static final class Failure extends Exception {
    Failure (String message) {
      super(message);
    }
  }

  /** One exported message with what both writers need. */
  static final class Item {
    static final int KIND_MESSAGE = 0;
    static final int KIND_SERVICE = 1;
    static final int KIND_UNSUPPORTED = 2;

    final TdApi.Message message;
    final long serverId;
    final int kind;
    /** The text or caption with its entities ({@link ExportMedia#textOf}), empty when there is none and for service messages. */
    @NonNull
    final TdApi.FormattedText text;
    /**
     * The line for what has no Desktop form: a service message Desktop has no action for (its "information_text"), or a
     * message with neither text, nor a file, nor a block of its own; "" otherwise.
     */
    @NonNull
    final String fallback;
    /** {@code fallback} is a whole sentence: the HTML doesn't put the actor before it. */
    final boolean hideAuthor;
    /** The message's file and what became of it (filled in before the page is written), null when it has none. */
    @Nullable
    final ExportMedia.Result media;
    /** A shared contact's vCard: its path in the export, or why it was left out; filled in with the page's media. */
    @Nullable
    String vcardPath;
    int vcardSkip = ExportMedia.SKIP_NONE;

    Item (TdApi.Message message, long serverId, int kind, @NonNull TdApi.FormattedText text, @NonNull String fallback, boolean hideAuthor, @Nullable ExportMedia.Result media) {
      this.message = message;
      this.serverId = serverId;
      this.kind = kind;
      this.text = text;
      this.fallback = fallback;
      this.hideAuthor = hideAuthor;
      this.media = media;
    }
  }

  private static final Object LOCK = new Object();
  private static volatile ExportJob current;

  /** The running export, if any. */
  @Nullable
  public static ExportJob current () {
    return current;
  }

  /** Starts an export; null when one is already running. {@code title} is the chat's name as the app shows it. */
  @Nullable
  @UiThread
  public static ExportJob start (Tdlib tdlib, ExportOptions options, boolean publicDownloads, @NonNull String title, @NonNull Listener listener) {
    synchronized (LOCK) {
      if (current != null) {
        return null;
      }
      final ExportJob job = new ExportJob(tdlib, options, publicDownloads, title, listener);
      current = job;
      new Thread(job::run, "XgramExport").start();
      return job;
    }
  }

  public final String id;
  /** The job's notification id and PendingIntent request code (its start second): unique per export. */
  public final int number;
  public final int accountId;
  public final long chatId;
  private final Tdlib tdlib;
  private final ExportOptions options;
  private final boolean publicDownloads;
  @NonNull
  private final String title;
  private final Listener listener;
  private final AtomicBoolean canceled = new AtomicBoolean();

  private volatile int phase = PHASE_INDEXING;
  private volatile int indexed, written, total;
  // Media: counted in pass 1 (the files the options include), done in pass 2 (copied, shared or left out)
  private volatile int mediaTotal, mediaDone, mediaUnavailable;
  private volatile long mediaBytesTotal, mediaBytesDone, mediaFileBytes;
  /** Media were left out: the storage got nearly full, or the .zip reached Android 6's limits. */
  private volatile boolean lowSpace, legacyZipLimit;
  private volatile int waitSeconds;
  private volatile boolean waitingForNetwork;
  private volatile long newestServerId, oldestServerId;
  private volatile long lastPublishMs, lastNotifyMs;
  /** Desktop's counter of contact_N.vcard files. Worker only. */
  private int contactCount;
  /** The basic group the chat was upgraded from, when its history is in the index (negative ids). Worker only. */
  private long migratedChatId;

  // Android's time limit: the service's state, the soft deadline's start, the stop it asked for
  private volatile int foregroundState = FOREGROUND_PENDING;
  private volatile long backgroundSinceMs;
  private volatile boolean stopRequested, softStopReached;
  /** The worker is where a time-limit stop is safe (nothing half written). Worker only. */
  private boolean stoppable;
  /** The worker ended: no progress notification after this (it would outlive the service's removal). */
  private final Object notifyLock = new Object();
  private boolean notificationsClosed;

  @Nullable
  private volatile Uri resultUri;
  @Nullable
  private volatile String resultName, resultLocation, errorText, realTitle;

  private ExportJob (Tdlib tdlib, ExportOptions options, boolean publicDownloads, @NonNull String title, Listener listener) {
    final long now = System.currentTimeMillis();
    this.id = Long.toString(now, 36);
    this.number = (int) (now / 1000L);
    this.accountId = options.accountId;
    this.chatId = options.chatId;
    this.tdlib = tdlib;
    this.options = options;
    this.publicDownloads = publicDownloads;
    this.title = title;
    this.listener = listener;
  }

  // State, read on the main thread

  public int phase () {
    return phase;
  }

  @NonNull
  public String title () {
    return title;
  }

  public boolean isCanceled () {
    return canceled.get();
  }

  /** The chat's name as Telegram has it (no local rename), for what leaves the device; the app's name until it is read. */
  @NonNull
  public String realTitle () {
    final String title = realTitle;
    return title != null && !title.isEmpty() ? title : this.title;
  }

  /** The finished .zip (after {@link Listener#onFinished} with {@link #PHASE_DONE}). */
  @Nullable
  public Uri resultUri () {
    return resultUri;
  }

  @Nullable
  public String resultName () {
    return resultName;
  }

  /** "Download/XGram", or the app's own folder when storage permission was denied (Android 9 and older). */
  @Nullable
  public String resultLocation () {
    return resultLocation;
  }

  /** A message for the user after {@link #PHASE_FAILED}, or null for the generic one. */
  @Nullable
  public String errorText () {
    return errorText;
  }

  @NonNull
  public ExportProgress progress () {
    return new ExportProgress(phase, indexed, written, total, mediaDone, mediaTotal, mediaBytesDone + mediaFileBytes, waitSeconds, waitingForNetwork, fraction(), title);
  }

  /**
   * What the result should add about media that are missing from a finished .zip (the storage got nearly full, the
   * .zip reached Android 6's limits, some files couldn't be downloaded), or null.
   */
  @Nullable
  public String resultNote () {
    if (lowSpace) {
      return Lang.getString(R.string.XgramExportLowSpace);
    }
    if (legacyZipLimit) {
      return Lang.getString(R.string.XgramExportLegacyLimit);
    }
    if (mediaUnavailable > 0) {
      return Lang.getString(R.string.XgramExportMediaUnavailable);
    }
    return null;
  }

  /** Asks the worker to stop; it deletes the unfinished .zip. Any thread. */
  public void cancel () {
    if (!canceled.getAndSet(true)) {
      publish(true); // the notification drops its Cancel at once
    }
  }

  // The foreground service (main thread) and Android's time limit

  /** The service holds the job in the foreground: the notification is ongoing and the soft deadline runs. */
  public boolean isForeground () {
    return foregroundState == FOREGROUND_ON;
  }

  /** Android refused the service: the export only goes on while XGram's process is not frozen. */
  public boolean isBackgroundRefused () {
    return foregroundState == FOREGROUND_REFUSED;
  }

  @UiThread
  void onForegroundChanged (int state) {
    if (foregroundState == state) {
      return;
    }
    foregroundState = state;
    if (state == FOREGROUND_ON && backgroundSinceMs == 0) {
      backgroundSinceMs = SystemClock.elapsedRealtime();
    }
    publish(true);
  }

  /** onTimeout: stop at the next safe point and save what was written as a partial .zip, quickly. Any thread. */
  void stopForTimeLimit () {
    stopRequested = true;
  }

  /**
   * Whether the job should stop and save what it has: onTimeout asked, or the soft deadline passed. The deadline counts
   * foreground-service time since XGram was last in front (Android resets its limit then), on Android 15+ only.
   */
  boolean shouldStop () {
    if (stopRequested || softStopReached) {
      return true;
    }
    if (Build.VERSION.SDK_INT < 35 || foregroundState != FOREGROUND_ON) {
      return false;
    }
    final long now = SystemClock.elapsedRealtime();
    if (UI.isResumed()) {
      backgroundSinceMs = now;
    } else if (backgroundSinceMs != 0 && now - backgroundSinceMs >= TIME_LIMIT_SOFT_MS) {
      softStopReached = true;
      Log.w("XGram export %s: near Android's dataSync time limit, saving what was exported", id);
    }
    return softStopReached;
  }

  // Worker callbacks

  void checkCanceled () {
    if (canceled.get()) {
      throw new Canceled();
    }
  }

  /** {@link #checkCanceled()}, and a time-limit stop where one is safe. TDLib waits call it every quarter second. */
  void checkInterrupted () {
    checkCanceled();
    if (stoppable && shouldStop()) {
      throw new Stopped();
    }
  }

  void onIndexed (int count, long messageId) {
    indexed = count;
    final long serverId = MessageId.toServerMessageId(messageId);
    if (newestServerId == 0) {
      newestServerId = serverId;
    }
    oldestServerId = serverId;
    publish(false);
  }

  void setWaitingForNetwork (boolean waiting) {
    if (waitingForNetwork != waiting) {
      waitingForNetwork = waiting;
      publish(true);
    }
  }

  void setWaitSeconds (int seconds) {
    if (waitSeconds != seconds) {
      waitSeconds = seconds;
      publish(true);
    }
  }

  /** Pass 1: a message whose file the options include ({@code size} 0 when unknown). */
  void onMediaPlanned (long size) {
    mediaTotal++;
    mediaBytesTotal += Math.max(0, size);
  }

  /** Pass 2: a planned file is in the .zip, shared with an earlier message, or left out. */
  void onMediaDone (long plannedSize) {
    mediaDone++;
    mediaBytesDone += Math.max(0, plannedSize);
    publish(false);
  }

  /** How much of the file being downloaded is there, for the bar (0 between files). */
  void setMediaFileBytes (long bytes) {
    if (mediaFileBytes != bytes) {
      mediaFileBytes = bytes;
      publish(false);
    }
  }

  void onMediaUnavailable () {
    mediaUnavailable++;
  }

  void onLowSpace () {
    lowSpace = true;
  }

  void onLegacyZipLimit () {
    legacyZipLimit = true;
  }

  /**
   * Reading the history is the first fifth of the bar, estimated from server ids (consecutive in groups and channels,
   * still growing elsewhere); writing is the rest: the messages, and with media mostly their bytes (a page waits for
   * its files before it is written).
   */
  private float fraction () {
    switch (phase) {
      case PHASE_INDEXING:
      case PHASE_CHECKING_SPACE: {
        final long newest = newestServerId, oldest = oldestServerId;
        if (newest <= 1) {
          return 0f;
        }
        final float read = Math.max(0f, Math.min(1f, (float) (newest - oldest) / (float) (newest - 1)));
        return 0.2f * read;
      }
      case PHASE_WRITING: {
        if (total <= 0) {
          return 0.2f;
        }
        final float messages = Math.min(1f, (float) written / (float) total);
        final long bytesTotal = mediaBytesTotal;
        if (bytesTotal <= 0) {
          return 0.2f + 0.8f * messages;
        }
        final float media = Math.min(1f, (float) (mediaBytesDone + mediaFileBytes) / (float) bytesTotal);
        return 0.2f + 0.8f * (0.2f * messages + 0.8f * media);
      }
      default:
        return 1f;
    }
  }

  private void publish (boolean force) {
    final long now = SystemClock.uptimeMillis();
    if (!force && now - lastPublishMs < PUBLISH_INTERVAL_MS) {
      return;
    }
    lastPublishMs = now;
    final ExportProgress progress = progress();
    UI.post(() -> listener.onProgress(this, progress));
    // The notification at most once a second (Android drops more than a few updates a second), and on every change of
    // phase or wait (forced), so a phase never shows the previous one's text
    if (force || now - lastNotifyMs >= NOTIFY_INTERVAL_MS) {
      lastNotifyMs = now;
      notifyProgress();
    }
  }

  /**
   * Once the service has started (or was refused), the progress notification follows the job; after the worker ended
   * nothing is posted any more, so the service's removal of it is final. Any thread.
   */
  private void notifyProgress () {
    if (foregroundState == FOREGROUND_PENDING) {
      return; // the service's startForeground posts the first one
    }
    synchronized (notifyLock) {
      if (!notificationsClosed) {
        ExportNotifications.showProgress(AppContext.get(), this);
      }
    }
  }

  private void closeNotifications () {
    synchronized (notifyLock) {
      notificationsClosed = true;
    }
  }

  private void setPhase (int phase) {
    this.phase = phase;
    publish(true);
  }

  // The job

  @WorkerThread
  private void run () {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
    final Context context = AppContext.get();
    final File dir = new File(tempRoot(context), id);
    final PowerManager.WakeLock wakeLock = acquireWakeLock(context);
    tdlib.xgramIncrementJobReferenceCount(JOB_REFERENCE);
    ExportIndex index = null;
    ExportZip zip = null;
    ExportJsonWriter json = null;
    ExportOutput output = null;
    ExportDownloader downloader = null;
    boolean committed = false;
    int result = PHASE_FAILED;
    try {
      if (!dir.isDirectory() && !dir.mkdirs()) {
        throw new IOException("Cannot create " + dir);
      }
      final ExportTd td = new ExportTd(tdlib, this);
      final ExportPeers peers = new ExportPeers(td);
      final ExportPeers.Dialog dialog = peers.dialog(options.chatId);
      final String topicTitle = options.isTopic() ? ExportSource.topicTitle(td, peers, options) : "";
      realTitle = topicTitle.isEmpty() ? dialog.title : dialog.title + " · " + topicTitle;

      // Pass 1: the ids, newest first. A time-limit stop here leaves nothing worth saving (Stopped, below)
      setPhase(PHASE_INDEXING);
      index = new ExportIndex(new File(dir, "ids.bin"));
      stoppable = true;
      migratedChatId = ExportSource.index(td, options, index, this); // an upgraded group's old history first, as Desktop
      stoppable = false;
      index.finishWriting();
      total = index.count();
      checkCanceled();

      // Room for the .zip (with the media the options include) and the JSON spool before anything is written: app
      // storage and Download share one partition on almost every device, and both are full size when result.json goes
      // into the .zip. Each file is checked again before its download
      setPhase(PHASE_CHECKING_SPACE);
      final long now = System.currentTimeMillis();
      output = new ExportOutput(context, id, zipName(realTitle(), now), publicDownloads);
      checkSpace(output.volume(), mediaBytesTotal + 2L * total * BYTES_PER_MESSAGE + SPACE_MARGIN);
      checkSpace(dir, (long) total * BYTES_PER_MESSAGE); // the spool, when app storage is elsewhere

      // Pass 2: pages oldest first, each page's media before the page
      setPhase(PHASE_WRITING);
      final String root = "ChatExport_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now)) + "/";
      zip = new ExportZip(output.open(), this);
      ExportAssets.write(zip, root, now);
      final File spool = new File(dir, "result.json.part");
      json = new ExportJsonWriter(spool, tdlib, peers, dialog, options.chatId, !options.isTopic());
      json.begin(dialog, options.isTopic() ? options.topicId : 0, topicTitle);
      final ExportHtmlWriter html = new ExportHtmlWriter(zip, root, tdlib, peers, index, dialog, topicTitle, options.chatId, !options.isTopic(), now);
      final int pages = index.pageCount();
      for (int page = 0; page <= pages; page++) {
        zip.reservePath(root + ExportHtmlWriter.messagesFile(page)); // a file named messages2.html stays a file
      }
      zip.reservePath(root + "result.json");
      downloader = new ExportDownloader(tdlib, this, zip, root, options.sizeLimitBytes, output.volume(), tdlibFilesDir(dir));
      // What the pages and result.json still need in the .zip, for Android 6's limits on media
      final long reserveBytes = 2L * total * BYTES_PER_MESSAGE + 16L * 1024L * 1024L;
      // Android's time limit stops it between pages, or while a page is fetched (nothing of it is written yet); a page
      // that is being written is finished first
      int stoppedAt = -1;
      for (int page = 0; page < pages; page++) {
        // A big export can outrun the lock's 6 h (no time limit before Android 15, or visits reset it): not reference
        // counted, so acquiring it again just re-arms the timeout
        if (wakeLock != null) {
          try {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
          } catch (Throwable ignored) { }
        }
        checkCanceled();
        if (shouldStop()) {
          stoppedAt = page;
          break;
        }
        final long[] ids = index.readPage(page);
        final List<Item> items;
        stoppable = true;
        try {
          items = fetch(td, ids);
        } catch (Stopped e) {
          stoppedAt = page;
          break;
        } finally {
          stoppable = false;
        }
        // The page's files go in first (entries can't interleave); a time-limit stop leaves the rest of them out
        downloader.writePage(items, reserveBytes, pages - page + 2);
        writeContacts(zip, root, items, reserveBytes, pages - page + 2);
        html.writePage(page, pages, items);
        json.writeItems(items);
        written += ids.length;
        publish(false);
      }
      if (stoppedAt == 0) {
        throw new Stopped(); // not one page written
      }
      if (stoppedAt > 0) {
        html.writeStopped(stoppedAt); // the page the last "Next messages" links to
      } else if (pages == 0) {
        html.writeEmpty();
      }

      // result.json last, then the .zip is published. After onTimeout every second counts: stored, not deflated
      setPhase(PHASE_FINALIZING);
      json.finish();
      json = null;
      checkCanceled();
      zip.putFile(root + "result.json", spool, now, !stopRequested);
      zip.finish();
      zip.close();
      zip = null;
      checkCanceled();
      output.commit();
      committed = true;
      resultUri = output.shareUri();
      resultName = output.displayName();
      resultLocation = output.locationLabel();
      result = stoppedAt > 0 ? PHASE_PARTIAL : PHASE_DONE;
    } catch (Canceled e) {
      result = PHASE_CANCELED;
    } catch (Stopped e) {
      errorText = Lang.getString(R.string.XgramExportStoppedEarly);
      result = PHASE_FAILED;
    } catch (Failure e) {
      errorText = e.getMessage();
      result = PHASE_FAILED;
    } catch (Throwable t) {
      Log.e("XGram export failed", t);
      result = PHASE_FAILED;
    } finally {
      // The zip stream first, then the unfinished .zip and the temp files, then what kept TDLib and the CPU awake
      if (json != null) {
        json.close();
      }
      if (zip != null) {
        try {
          zip.close();
        } catch (Throwable ignored) { }
      }
      if (output != null && !committed) {
        output.abort();
      }
      if (downloader != null) {
        downloader.close(); // downloads still running stop, and what the export started of them is deleted
      }
      if (index != null) {
        index.close();
      }
      deleteRecursively(dir);
      tdlib.xgramDecrementJobReferenceCount(JOB_REFERENCE);
      if (wakeLock != null) {
        try {
          wakeLock.release();
        } catch (Throwable ignored) { }
      }
      phase = result;
      closeNotifications(); // before the service removes the progress notification on onFinished
      synchronized (LOCK) {
        if (current == this) {
          current = null;
        }
      }
      UI.post(() -> listener.onFinished(this));
    }
  }

  /**
   * The messages of one page, oldest first; ones deleted since pass 1 are left out. An upgraded group's old messages
   * (negative ids, so first) come from the old group under their own ids and are then shifted back
   * ({@link ExportSource#adoptMigrated}).
   */
  private List<Item> fetch (ExportTd td, long[] ids) {
    final List<Item> items = new ArrayList<>(ids.length);
    for (int from = 0; from < ids.length; from += FETCH_BATCH) {
      final long[] batch = Arrays.copyOfRange(ids, from, Math.min(ids.length, from + FETCH_BATCH));
      final int migrated = migratedChatId != 0 ? ExportSource.migratedCount(batch) : 0;
      if (migrated > 0) {
        addItems(items, td.call(new TdApi.GetMessages(migratedChatId, ExportSource.migratedIds(batch, migrated))), true);
      }
      if (migrated < batch.length) {
        addItems(items, td.call(new TdApi.GetMessages(options.chatId, migrated == 0 ? batch : Arrays.copyOfRange(batch, migrated, batch.length))), false);
      }
      checkCanceled();
    }
    return items;
  }

  private void addItems (List<Item> items, TdApi.Messages messages, boolean migrated) {
    if (messages.messages == null) {
      return;
    }
    for (TdApi.Message message : messages.messages) {
      if (message != null && message.content != null) {
        if (migrated) {
          ExportSource.adoptMigrated(message, options.chatId, migratedChatId);
        }
        items.add(item(message));
      }
    }
  }

  private Item item (TdApi.Message message) {
    final long serverId = MessageId.toServerMessageId(message.id);
    final TdApi.MessageContent content = message.content;
    final int kind = content.getConstructor() == TdApi.MessageUnsupported.CONSTRUCTOR ? Item.KIND_UNSUPPORTED :
      ExportActions.isService(content) ? Item.KIND_SERVICE :
      Item.KIND_MESSAGE;
    final TdApi.FormattedText text = kind == Item.KIND_MESSAGE ? ExportMedia.textOf(content) : ExportText.EMPTY;
    final ExportMedia.Plan plan = kind != Item.KIND_UNSUPPORTED ? ExportMedia.plan(message, options) : null;
    String fallback = "";
    boolean hideAuthor = false;
    if (kind == Item.KIND_SERVICE && !ExportActions.hasAction(content)) {
      // Desktop has no action for it: written as Desktop writes a custom action, the actor and a line
      final String information = ExportActions.information(content);
      if (information != null) {
        fallback = information;
        hideAuthor = true;
      } else {
        fallback = previewLine(message);
      }
    } else if (kind == Item.KIND_MESSAGE && ExportText.isEmpty(text) && !ExportMedia.hasFile(content) && !ExportMedia.hasBlock(content)) {
      fallback = previewLine(message);
    }
    return new Item(message, serverId, kind, text, fallback, hideAuthor, plan != null ? new ExportMedia.Result(plan) : null);
  }

  /**
   * Telegram X's preview of a content XGram has no form for, in the app language; "" when the preview names people:
   * it reads their names from Telegram X's cache, where XGram's local renames are written in (critique #19), and the
   * export carries only real names. The actor is never part of it.
   */
  private String previewLine (TdApi.Message message) {
    final ContentPreview preview = ExportActions.preview(tdlib, options.chatId, message);
    return preview != null && !preview.hideAuthor ? ExportActions.previewText(preview) : "";
  }

  /**
   * Shared contacts' vCards, before the page that links them, as Desktop writes them: contacts/contact_N.vcard, when
   * Photos is checked (Desktop files them under that type) and within the size limit.
   */
  private void writeContacts (ExportZip zip, String root, List<Item> items, long reserveBytes, int reserveEntries) throws IOException {
    for (Item item : items) {
      if (item.message.content.getConstructor() != TdApi.MessageContact.CONSTRUCTOR) {
        continue;
      }
      final TdApi.Contact contact = ((TdApi.MessageContact) item.message.content).contact;
      if (contact == null || contact.vcard == null || contact.vcard.isEmpty()) {
        continue;
      }
      checkCanceled();
      final byte[] data = contact.vcard.getBytes(StandardCharsets.UTF_8);
      if (!options.includes(ExportOptions.TYPE_PHOTOS)) {
        item.vcardSkip = ExportMedia.SKIP_TYPE;
      } else if (data.length > options.sizeLimitBytes) {
        item.vcardSkip = ExportMedia.SKIP_SIZE;
      } else if (!options.canSave(item.message) || !zip.canAddMedia(data.length, 0, reserveBytes, reserveEntries)) {
        item.vcardSkip = ExportMedia.SKIP_UNAVAILABLE; // as the media of a message that can't be saved, or Android 6's zip limits
        if (options.canSave(item.message)) {
          onLegacyZipLimit();
        }
      } else {
        final String path = zip.uniquePath(root + "contacts/contact_" + (++contactCount) + ".vcard");
        zip.putBytesStored(path, data, item.message.date * 1000L);
        item.vcardPath = path.substring(root.length());
      }
    }
  }

  private static void checkSpace (@Nullable File path, long needed) throws Failure {
    final long free = freeBytes(path);
    if (free >= 0 && free < needed) {
      throw new Failure(Lang.getString(R.string.XgramExportNoSpace, Strings.buildSize(needed), Strings.buildSize(free)));
    }
  }

  /** Where TDLib keeps this account's files (the downloads' volume), else {@code fallback}. */
  private File tdlibFilesDir (File fallback) {
    try {
      final String path = TdlibManager.getTdlibDirectory(accountId, true, false);
      if (path != null && !path.isEmpty()) {
        return new File(path);
      }
    } catch (Throwable ignored) { }
    return fallback;
  }

  /** Free bytes on {@code path}'s volume, -1 when unknown. */
  static long freeBytes (@Nullable File path) {
    if (path == null) {
      return -1;
    }
    try {
      final StatFs stat = new StatFs(path.getAbsolutePath());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        return stat.getAvailableBytes();
      }
      //noinspection deprecation
      return (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
    } catch (Throwable t) {
      return -1;
    }
  }

  /** "XGram_<title>_<yyyy-MM-dd_HH-mm>.zip", cleaned for file systems and at most 250 bytes. */
  static String zipName (@Nullable String title, long timeMs) {
    String clean = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    while (clean.startsWith(".")) {
      clean = clean.substring(1);
    }
    if (clean.codePointCount(0, clean.length()) > 40) {
      clean = clean.substring(0, clean.offsetByCodePoints(0, 40)).trim();
    }
    if (clean.isEmpty()) {
      clean = "chat";
    }
    final String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date(timeMs));
    String name = "XGram_" + clean + "_" + stamp + ".zip";
    while (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES && clean.length() > 1) {
      clean = clean.substring(0, clean.offsetByCodePoints(0, clean.codePointCount(0, clean.length()) - 1));
      name = "XGram_" + clean + "_" + stamp + ".zip";
    }
    return name;
  }

  @Nullable
  private static PowerManager.WakeLock acquireWakeLock (Context context) {
    try {
      final PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      if (power == null) {
        return null;
      }
      final PowerManager.WakeLock lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XGram:export");
      lock.setReferenceCounted(false);
      lock.acquire(WAKE_LOCK_TIMEOUT_MS);
      return lock;
    } catch (Throwable t) {
      Log.w("XGram export: no wake lock", t);
      return null;
    }
  }

  /**
   * The jobs' temporary files (the id index, the JSON spool): in no-backup storage, not the cache, which Android may
   * clear while the export fills the disk.
   */
  static File tempRoot (Context context) {
    return new File(ContextCompat.getNoBackupFilesDir(context), "xgram_export");
  }

  static void deleteRecursively (@Nullable File file) {
    if (file == null || !file.exists()) {
      return;
    }
    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteRecursively(child);
        }
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }
}
