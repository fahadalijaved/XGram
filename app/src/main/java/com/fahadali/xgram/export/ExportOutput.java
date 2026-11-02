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

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Where the .zip goes, written once while the export runs. Android 10+: a pending MediaStore item in Download/XGram
 * (no permission needed), published on commit. Below that: {@code <name>.zip.part} in the public Download/XGram when
 * the storage permission was granted (renamed and added to the Downloads app on commit), else in the app's own
 * Download folder. The item or .part file is remembered until commit or abort, so one left by a killed process can be
 * removed later ({@link #cleanupStale}). Only the export's worker thread opens, commits or aborts it.
 */
final class ExportOutput {
  static final String MIME = "application/zip";
  static final String FOLDER = "XGram";
  static final String PUBLIC_LABEL = "Download/" + FOLDER;

  // A string key outside the "xgram_export_" settings prefix, which settings backups read as ints
  private static final String KEY_PENDING = "xgram_exportjob_pending";
  private static final Object PENDING_LOCK = new Object();

  private final Context context;
  private final String jobId;
  private final String requestedName;
  private final boolean publicDownloads;

  @Nullable
  private Uri uri;
  @Nullable
  private File partFile, finalFile;
  private String finalName;
  private boolean inAppFolder;

  /** {@code publicDownloads}: below Android 10, whether the storage permission was granted. */
  ExportOutput (Context context, String jobId, String requestedName, boolean publicDownloads) {
    this.context = context.getApplicationContext();
    this.jobId = jobId;
    this.requestedName = requestedName;
    this.finalName = requestedName;
    this.publicDownloads = publicDownloads;
  }

  /** The folder whose volume the .zip lands on, for the free space check; null when unknown. */
  @Nullable
  File volume () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || publicDownloads) {
      //noinspection deprecation
      return Environment.getExternalStorageDirectory();
    }
    return context.getExternalFilesDir(null);
  }

  @WorkerThread
  OutputStream open () throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return openMediaStore();
    }
    return openFile();
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private OutputStream openMediaStore () throws IOException {
    final ContentResolver resolver = context.getContentResolver();
    final ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, requestedName);
    values.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
    final Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) {
      throw new IOException("MediaStore insert failed");
    }
    this.uri = uri;
    savePending(jobId, uri.toString());
    final OutputStream stream = resolver.openOutputStream(uri, "w");
    if (stream == null) {
      throw new IOException("No output stream for " + uri);
    }
    return stream;
  }

  private OutputStream openFile () throws IOException {
    File dir = null;
    if (publicDownloads) {
      //noinspection deprecation
      final File downloads = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
      if (downloads.isDirectory() || downloads.mkdirs()) {
        dir = downloads;
      }
    }
    if (dir == null) {
      final File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
      if (base == null) {
        throw new IOException("No external storage");
      }
      final File own = new File(base, FOLDER);
      if (!own.isDirectory() && !own.mkdirs()) {
        throw new IOException("Cannot create " + own);
      }
      dir = own;
      inAppFolder = true;
    }
    final File part = new File(dir, requestedName + ".part");
    partFile = part;
    savePending(jobId, part.getAbsolutePath());
    return new FileOutputStream(part);
  }

  /** Publishes the finished .zip; the zip stream must be closed first. */
  @WorkerThread
  void commit () throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      commitMediaStore();
    } else {
      commitFile();
    }
    clearPending(jobId);
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private void commitMediaStore () throws IOException {
    final Uri uri = this.uri;
    if (uri == null) {
      throw new IOException("Not open");
    }
    final ContentResolver resolver = context.getContentResolver();
    final ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
    resolver.update(uri, values, null, null);
    // MediaStore renames on a clash ("name (1).zip"): show the name it really has
    try (Cursor cursor = resolver.query(uri, new String[] {MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        final String name = cursor.getString(0);
        if (name != null && !name.isEmpty()) {
          finalName = name;
        }
      }
    } catch (Throwable t) {
      Log.w("XGram export: cannot read the saved name", t);
    }
  }

  @SuppressWarnings("deprecation")
  private void commitFile () throws IOException {
    final File part = partFile;
    if (part == null || part.getParentFile() == null) {
      throw new IOException("Not open");
    }
    final File target = uniqueFile(part.getParentFile(), requestedName);
    if (!part.renameTo(target)) {
      throw new IOException("Cannot rename " + part);
    }
    partFile = null;
    finalFile = target;
    finalName = target.getName();
    if (!inAppFolder) {
      try {
        final DownloadManager downloads = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloads != null) {
          downloads.addCompletedDownload(target.getName(), target.getName(), true, MIME, target.getAbsolutePath(), target.length(), true);
        }
      } catch (Throwable t) {
        Log.w("XGram export: cannot add the .zip to Downloads", t);
      }
    }
  }

  /** Deletes the unfinished .zip; the zip stream must be closed first. */
  @WorkerThread
  void abort () {
    final Uri uri = this.uri;
    if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        context.getContentResolver().delete(uri, null, null);
      } catch (Throwable t) {
        Log.w("XGram export: cannot delete the unfinished .zip", t);
      }
      this.uri = null;
    }
    final File part = partFile;
    if (part != null && part.exists() && !part.delete()) {
      Log.w("XGram export: cannot delete %s", part.getAbsolutePath());
    }
    partFile = null;
    clearPending(jobId);
  }

  /** A uri to share the committed .zip with; null when there is none. */
  @Nullable
  Uri shareUri () {
    if (uri != null) {
      return uri;
    }
    return finalFile != null ? U.contentUriFromFile(finalFile) : null;
  }

  String displayName () {
    return finalName;
  }

  /** Where the .zip is, as the done message says it: "Download/XGram", or the app's own folder. */
  String locationLabel () {
    if (inAppFolder && finalFile != null && finalFile.getParentFile() != null) {
      return finalFile.getParentFile().getAbsolutePath();
    }
    return PUBLIC_LABEL;
  }

  /** {@code name}, or Desktop's "name (k).ext" when it is taken. */
  private static File uniqueFile (File dir, String name) {
    File file = new File(dir, name);
    if (!file.exists()) {
      return file;
    }
    final int dot = name.lastIndexOf('.'); // the extension: chat titles can have dots
    final String base = dot > 0 ? name.substring(0, dot) : name;
    final String extension = dot > 0 ? name.substring(dot) : "";
    for (int k = 1; ; k++) {
      file = new File(dir, base + " (" + k + ")" + extension);
      if (!file.exists()) {
        return file;
      }
    }
  }

  // Leftovers of a killed process

  private static void savePending (String jobId, String target) {
    synchronized (PENDING_LOCK) {
      Settings.instance().pmc().putString(KEY_PENDING, jobId + "\n" + target);
    }
  }

  private static void clearPending (String jobId) {
    synchronized (PENDING_LOCK) {
      final String value = Settings.instance().pmc().getString(KEY_PENDING, null);
      if (value != null && value.startsWith(jobId + "\n")) {
        Settings.instance().pmc().remove(KEY_PENDING);
      }
    }
  }

  /**
   * Removes the .zip and the temporary folders an export left when its process was killed. Never touches a live
   * export's: the saved key and the running job are read together under the key's lock, and a job clears its key
   * (under that lock, on commit or abort) before it stops being {@link ExportJob#current()}, while it is current
   * before its worker saves anything. So a key that doesn't match the running job (or with none running) belongs to
   * a dead process; it is dropped under the lock and its file deleted after. Off the main thread.
   */
  @WorkerThread
  static void cleanupStale (Context context) {
    String target = null;
    synchronized (PENDING_LOCK) {
      final String value = Settings.instance().pmc().getString(KEY_PENDING, null);
      if (value != null) {
        final int split = value.indexOf('\n');
        final String jobId = split >= 0 ? value.substring(0, split) : "";
        final ExportJob running = ExportJob.current();
        if (running == null || !running.id.equals(jobId)) {
          target = split >= 0 ? value.substring(split + 1) : value;
          Settings.instance().pmc().remove(KEY_PENDING);
        }
      }
    }
    if (target != null) {
      try {
        if (target.startsWith("content:")) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getContentResolver().delete(Uri.parse(target), null, null);
          }
        } else if (!target.isEmpty()) {
          //noinspection ResultOfMethodCallIgnored
          new File(target).delete();
        }
      } catch (Throwable t) {
        Log.w("XGram export: cannot remove a stale .zip", t);
      }
    }
    final File[] dirs = ExportJob.tempRoot(context).listFiles();
    if (dirs != null) {
      final ExportJob running = ExportJob.current();
      for (File dir : dirs) {
        if (running == null || !dir.getName().equals(running.id)) {
          ExportJob.deleteRecursively(dir);
        }
      }
    }
  }
}
