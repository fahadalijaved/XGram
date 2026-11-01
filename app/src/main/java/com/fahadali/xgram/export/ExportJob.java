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
import tgx.td.Td;

/**
 * One chat export, run on its own background thread from start to finish (one at a time, in any account):
 * <ol>
 *   <li>pass 1 reads the history newest → oldest and keeps only the message ids, on disk;</li>
 *   <li>a free space check;</li>
 *   <li>pass 2 reads the ids oldest → newest, 1000 at a time, fetches those messages and writes messagesN.html into
 *   the .zip and the JSON into a spool file;</li>
 *   <li>result.json goes in last, the .zip is published.</li>
 * </ol>
 * Memory does not grow with the chat. The job holds a TDLib reference and a partial wake lock while it runs (TDLib
 * pauses 5 s after the UI is gone otherwise), and only this thread touches the output: a cancel just sets a flag, and
 * the worker deletes the unfinished .zip itself.
 */
public final class ExportJob {
  public static final int PHASE_INDEXING = 0;
  public static final int PHASE_CHECKING_SPACE = 1;
  public static final int PHASE_WRITING = 2;
  public static final int PHASE_FINALIZING = 3;
  public static final int PHASE_DONE = 4;
  public static final int PHASE_FAILED = 5;
  public static final int PHASE_CANCELED = 6;

  private static final String JOB_REFERENCE = "xgramExport";
  private static final long WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L;
  private static final long PUBLISH_INTERVAL_MS = 250;
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
    /** The text or caption, "" when there is none. */
    @NonNull
    final String text;
    /**
     * The line for service messages and for contents with neither text nor a media block: Telegram X's preview, or
     * Desktop's sentence with real names where the preview would name people ({@link ExportActions#namedLine}).
     */
    @NonNull
    final String fallback;
    /** The line already names the people. */
    final boolean hideAuthor;
    /** The line is Desktop's sentence that follows the actor's name ("invited Bob"). */
    final boolean afterActor;

    Item (TdApi.Message message, long serverId, int kind, @NonNull String text, @NonNull String fallback, boolean hideAuthor, boolean afterActor) {
      this.message = message;
      this.serverId = serverId;
      this.kind = kind;
      this.text = text;
      this.fallback = fallback;
      this.hideAuthor = hideAuthor;
      this.afterActor = afterActor;
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
  private volatile int waitSeconds;
  private volatile boolean waitingForNetwork;
  private volatile long newestServerId, oldestServerId;
  private long lastPublishMs;

  @Nullable
  private volatile Uri resultUri;
  @Nullable
  private volatile String resultName, resultLocation, errorText, realTitle;

  private ExportJob (Tdlib tdlib, ExportOptions options, boolean publicDownloads, @NonNull String title, Listener listener) {
    this.id = Long.toString(System.currentTimeMillis(), 36);
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
    return new ExportProgress(phase, indexed, written, total, 0, 0, 0, waitSeconds, waitingForNetwork, fraction(), title);
  }

  /** Asks the worker to stop; it deletes the unfinished .zip. Any thread. */
  public void cancel () {
    canceled.set(true);
  }

  // Worker callbacks

  void checkCanceled () {
    if (canceled.get()) {
      throw new Canceled();
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

  /**
   * Reading the history is the first fifth of the bar, estimated from server ids (consecutive in groups and channels,
   * still growing elsewhere); writing is the rest, exact.
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
      case PHASE_WRITING:
        return total > 0 ? 0.2f + 0.8f * Math.min(1f, (float) written / (float) total) : 0.2f;
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
    boolean committed = false;
    int result = PHASE_FAILED;
    try {
      if (!dir.isDirectory() && !dir.mkdirs()) {
        throw new IOException("Cannot create " + dir);
      }
      final ExportTd td = new ExportTd(tdlib, this);
      final ExportPeers peers = new ExportPeers(td);
      final ExportPeers.Dialog dialog = peers.dialog(options.chatId);
      realTitle = dialog.title;

      // Pass 1: the ids, newest first
      setPhase(PHASE_INDEXING);
      index = new ExportIndex(new File(dir, "ids.bin"));
      ExportSource.index(td, options, index, this);
      index.finishWriting();
      total = index.count();
      checkCanceled();

      // Room for the .zip and the JSON spool before anything is written: app storage and Download share one partition
      // on almost every device, and both are full size when result.json goes into the .zip
      setPhase(PHASE_CHECKING_SPACE);
      final long now = System.currentTimeMillis();
      output = new ExportOutput(context, id, zipName(dialog.title, now), publicDownloads);
      checkSpace(output.volume(), 2L * total * BYTES_PER_MESSAGE + SPACE_MARGIN);
      checkSpace(dir, (long) total * BYTES_PER_MESSAGE); // the spool, when app storage is elsewhere

      // Pass 2: pages oldest first
      setPhase(PHASE_WRITING);
      final String root = "ChatExport_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now)) + "/";
      zip = new ExportZip(output.open(), this);
      ExportAssets.write(zip, root, now);
      final File spool = new File(dir, "result.json.part");
      json = new ExportJsonWriter(spool, peers, options.chatId);
      json.begin(dialog);
      final ExportHtmlWriter html = new ExportHtmlWriter(zip, root, peers, index, dialog, options.chatId, now);
      final int pages = index.pageCount();
      for (int page = 0; page < pages; page++) {
        checkCanceled();
        final long[] ids = index.readPage(page);
        final List<Item> items = fetch(td, peers, ids);
        html.writePage(page, pages, items);
        json.writeItems(items);
        written += ids.length;
        publish(false);
      }
      if (pages == 0) {
        html.writeEmpty();
      }

      // result.json last, then the .zip is published
      setPhase(PHASE_FINALIZING);
      json.finish();
      json = null;
      checkCanceled();
      zip.putFile(root + "result.json", spool, now, true);
      zip.finish();
      zip.close();
      zip = null;
      checkCanceled();
      output.commit();
      committed = true;
      resultUri = output.shareUri();
      resultName = output.displayName();
      resultLocation = output.locationLabel();
      result = PHASE_DONE;
    } catch (Canceled e) {
      result = PHASE_CANCELED;
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
      synchronized (LOCK) {
        if (current == this) {
          current = null;
        }
      }
      UI.post(() -> listener.onFinished(this));
    }
  }

  /** The messages of one page, oldest first; ones deleted since pass 1 are left out. */
  private List<Item> fetch (ExportTd td, ExportPeers peers, long[] ids) {
    final List<Item> items = new ArrayList<>(ids.length);
    for (int from = 0; from < ids.length; from += FETCH_BATCH) {
      final long[] batch = Arrays.copyOfRange(ids, from, Math.min(ids.length, from + FETCH_BATCH));
      final TdApi.Messages messages = td.call(new TdApi.GetMessages(options.chatId, batch));
      if (messages.messages != null) {
        for (TdApi.Message message : messages.messages) {
          if (message != null && message.content != null) {
            items.add(item(peers, message));
          }
        }
      }
      checkCanceled();
    }
    return items;
  }

  private Item item (ExportPeers peers, TdApi.Message message) {
    final long serverId = MessageId.toServerMessageId(message.id);
    final int constructor = message.content.getConstructor();
    final int kind = constructor == TdApi.MessageUnsupported.CONSTRUCTOR ? Item.KIND_UNSUPPORTED :
      ExportActions.isService(message.content) ? Item.KIND_SERVICE :
      Item.KIND_MESSAGE;
    String text = "";
    if (kind == Item.KIND_MESSAGE) {
      final TdApi.FormattedText formatted = Td.textOrCaption(message.content);
      if (formatted != null && formatted.text != null) {
        text = formatted.text;
      }
    }
    String fallback = "";
    boolean hideAuthor = false, afterActor = false;
    final boolean needsLine = kind == Item.KIND_SERVICE ||
      (kind == Item.KIND_MESSAGE && text.isEmpty() && constructor != TdApi.MessageText.CONSTRUCTOR && ExportHtmlWriter.placeholderOf(message.content) == null);
    if (needsLine) {
      // A preview that names people would carry XGram's local renames: those lines are built from the real names
      final ExportActions.Line line = ExportActions.namedLine(tdlib, peers, message);
      if (line != null) {
        fallback = line.text;
        afterActor = line.afterActor;
        hideAuthor = !line.afterActor;
      } else {
        final ContentPreview preview = ExportActions.preview(tdlib, options.chatId, message);
        fallback = ExportActions.previewText(preview);
        hideAuthor = preview != null && preview.hideAuthor;
      }
    }
    return new Item(message, serverId, kind, text, fallback, hideAuthor, afterActor);
  }

  private static void checkSpace (@Nullable File path, long needed) throws Failure {
    final long free = freeBytes(path);
    if (free >= 0 && free < needed) {
      throw new Failure(Lang.getString(R.string.XgramExportNoSpace, Strings.buildSize(needed), Strings.buildSize(free)));
    }
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
