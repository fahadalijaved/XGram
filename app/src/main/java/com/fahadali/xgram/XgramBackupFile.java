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
package com.fahadali.xgram;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.vkryl.core.StringUtils;

/**
 * XGram's settings as a backup file (Backup and reset): Export writes "XGram-settings-<date>.xgram" (the JSON of
 * {@link XgramBackup#export}), keeps a copy in Downloads/XGram and opens the share sheet, so it can go anywhere (Saved
 * Messages, a cloud drive, the Files app). Import opens Android's file picker. Such a file opened in a chat in XGram, or
 * opened with XGram from another app, offers the import too. Every import asks first, then the app restarts.
 */
public final class XgramBackupFile {
  private XgramBackupFile () { }

  public static final String EXTENSION = ".xgram";
  /** XGram's own type for the file, so other apps can hand it to XGram; most apps send it untyped anyway. */
  public static final String MIME = "application/vnd.xgram.settings";
  private static final String SHARE_DIR = "xgram_backup";
  private static final int REQUEST_PICK = 10111;
  /** A backup is a few dozen KB; anything far larger is not one. */
  private static final int MAX_BYTES = 4 * 1024 * 1024;

  /** {@code name} is a backup file's name. */
  public static boolean isBackupName (@Nullable String name) {
    return name != null && name.toLowerCase(Locale.US).endsWith(EXTENSION);
  }

  private static String newFileName () {
    return "XGram-settings-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + EXTENSION;
  }

  // Export

  /** Writes the backup, keeps a copy in Downloads/XGram and opens the share sheet. */
  public static void export () {
    final Context context = AppContext.get();
    final String json = XgramBackup.export();
    final String name = newFileName();
    new Thread(() -> {
      File file = null;
      boolean savedCopy = false;
      try {
        File dir = new File(context.getFilesDir(), SHARE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
          throw new java.io.IOException("Cannot create " + dir);
        }
        File[] old = dir.listFiles();
        if (old != null) {
          for (File f : old) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
          }
        }
        file = new File(dir, name);
        try (OutputStream out = new FileOutputStream(file)) {
          out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        savedCopy = saveToDownloads(context, name, json);
      } catch (Throwable t) {
        Log.w("Settings backup: export failed", t);
        file = null;
      }
      final File result = file;
      final boolean copy = savedCopy;
      UI.post(() -> {
        if (result == null || !openShareSheet(result)) {
          UI.showToast(R.string.XgramBackupExportFailed, Toast.LENGTH_LONG);
          return;
        }
        if (copy) {
          UI.showToast(Lang.getString(R.string.XgramBackupSavedTo, "Downloads/XGram/" + name), Toast.LENGTH_LONG);
        }
      });
    }, "XgramBackupExport").start();
  }

  /** The copy in Downloads/XGram (Android 10+; below, the app's own Downloads folder). False when it couldn't. */
  private static boolean saveToDownloads (Context context, String name, String json) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream"); // keeps the .xgram name as it is
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/XGram");
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
          return false;
        }
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
          if (out == null) {
            return false;
          }
          out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return true;
      }
      File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
      if (base == null) {
        return false;
      }
      File dir = new File(base, "XGram");
      if (!dir.isDirectory() && !dir.mkdirs()) {
        return false;
      }
      try (OutputStream out = new FileOutputStream(new File(dir, name))) {
        out.write(json.getBytes(StandardCharsets.UTF_8));
      }
      return true;
    } catch (Throwable t) {
      Log.w("Settings backup: no copy in Downloads", t);
      return false;
    }
  }

  private static boolean openShareSheet (File file) {
    Uri uri;
    try {
      uri = org.thunderdog.challegram.FileProvider.getUriForFile(AppContext.get(), Config.FILE_PROVIDER_AUTHORITY, file);
    } catch (Throwable t) {
      return false;
    }
    if (uri == null) {
      return false;
    }
    Intent send = new Intent(Intent.ACTION_SEND)
      .setType("application/octet-stream")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, file.getName());
    send.setClipData(ClipData.newRawUri(null, uri));
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    Intent chooser = Intent.createChooser(send, Lang.getString(R.string.XgramExportSettings));
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      UI.startActivity(chooser);
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  // Import

  /** Android's file picker; the picked file is read and the import confirmed. */
  public static void pickAndImport (@NonNull BaseActivity activity) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
    activity.putActivityResultHandler(REQUEST_PICK, (requestCode, resultCode, data) -> {
      Uri uri = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
      if (uri != null) {
        importUri(uri);
      }
    });
    try {
      activity.startActivityForResult(intent, REQUEST_PICK);
    } catch (Throwable t) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
    }
  }

  /** A backup at {@code uri} (the picker, or another app's "Open with XGram"): read, then confirmed. */
  public static void importUri (@NonNull Uri uri) {
    final Context context = AppContext.get();
    new Thread(() -> {
      String name = displayName(context, uri);
      String json = null;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        json = in != null ? read(in) : null;
      } catch (Throwable t) {
        Log.w("Settings backup: cannot read the picked file", t);
      }
      final String text = json;
      final String fileName = name;
      UI.post(() -> confirmImport(text, fileName));
    }, "XgramBackupImport").start();
  }

  /** A backup file downloaded in a chat (a document named *.xgram, opened with a tap). */
  public static void importFile (@NonNull File file, @Nullable String displayName) {
    new Thread(() -> {
      String json = null;
      try (InputStream in = new FileInputStream(file)) {
        json = read(in);
      } catch (Throwable t) {
        Log.w("Settings backup: cannot read the file", t);
      }
      final String text = json;
      UI.post(() -> confirmImport(text, StringUtils.isEmpty(displayName) ? file.getName() : displayName));
    }, "XgramBackupImport").start();
  }

  /** Whether {@code uri} (an intent's data) looks like a backup: XGram's type, or a name ending in .xgram. */
  public static boolean isBackupUri (@NonNull Context context, @NonNull Uri uri, @Nullable String type) {
    if (MIME.equals(type)) {
      return true;
    }
    String scheme = uri.getScheme();
    if (!"content".equals(scheme) && !"file".equals(scheme)) {
      return false;
    }
    if (isBackupName(uri.getLastPathSegment())) {
      return true;
    }
    return "content".equals(scheme) && isBackupName(displayName(context, uri));
  }

  @Nullable
  private static String displayName (Context context, Uri uri) {
    if ("content".equals(uri.getScheme())) {
      try (Cursor c = context.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
        if (c != null && c.moveToFirst()) {
          String name = c.getString(0);
          if (!StringUtils.isEmpty(name)) {
            return name;
          }
        }
      } catch (Throwable ignored) { }
    }
    return uri.getLastPathSegment();
  }

  private static String read (InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[16 * 1024];
    int read;
    while ((read = in.read(buffer)) > 0) {
      out.write(buffer, 0, read);
      if (out.size() > MAX_BYTES) {
        throw new java.io.IOException("Too large for a settings backup");
      }
    }
    return out.toString("UTF-8");
  }

  /** Asks on the screen in front, then imports and restarts; says so when the file is not a backup. */
  private static void confirmImport (@Nullable String json, @Nullable String name) {
    if (json == null || !XgramBackup.isBackup(json)) {
      UI.showToast(R.string.XgramBackupNotABackup, Toast.LENGTH_LONG);
      return;
    }
    ViewController<?> c = UI.getCurrentStackItem();
    if (c == null) {
      UI.showToast(R.string.XgramBackupNotABackup, Toast.LENGTH_LONG);
      return;
    }
    c.showConfirm(Lang.getString(R.string.XgramBackupImportConfirm, StringUtils.isEmpty(name) ? "" : name), Lang.getString(R.string.XgramImportSettings), () -> {
      int count = XgramBackup.importJson(json);
      if (count < 0) {
        UI.showToast(R.string.XgramBackupNotABackup, Toast.LENGTH_LONG);
      } else {
        UI.showToast(R.string.XgramImportDone, Toast.LENGTH_SHORT);
        final Context context = c.context();
        UI.post(() -> XgramApp.restart(context), 800);
      }
    });
  }
}
