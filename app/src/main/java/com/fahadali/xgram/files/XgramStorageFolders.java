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
package com.fahadali.xgram.files;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * XGram: folders from other storage apps pinned in the attach menu's File tab, to browse and attach from like the
 * phone's own folders. Any app that offers its files to Android does (OneDrive, Dropbox, Nextcloud, file managers
 * with SFTP or SMB, and so on: Android's folder picker lists them); Google Drive gives files only, through Other
 * apps. A pinned folder is Android's lasting read access to it (a tree URI), kept until it is removed.
 */
public final class XgramStorageFolders {
  private XgramStorageFolders () { }

  private static final String TAG = "XgramStorageFolders";
  private static final String KEY = "xgram_storage_folders";

  /** One entry of a folder: a subfolder or a file. */
  public static final class Entry {
    public final String documentId;
    public final String name;
    public final String mimeType;
    public final long size, modified;

    Entry (String documentId, String name, String mimeType, long size, long modified) {
      this.documentId = documentId;
      this.name = name;
      this.mimeType = mimeType;
      this.size = size;
      this.modified = modified;
    }

    public boolean isFolder () {
      return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }
  }

  /** A pinned folder: its tree URI, its name and the app it comes from, as they were when it was pinned. */
  public static final class Pinned {
    public final Uri tree;
    public final String name;
    public final @Nullable String app;

    Pinned (Uri tree, String name, @Nullable String app) {
      this.tree = tree;
      this.name = name;
      this.app = app;
    }
  }

  /** The pinned folders, in the order they were added (only those Android still lets XGram read). */
  public static List<Pinned> list (Context context) {
    final String saved = Settings.instance().pmc().getString(KEY, null);
    if (saved == null || saved.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Uri> granted = new ArrayList<>();
    for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
      if (permission.isReadPermission()) {
        granted.add(permission.getUri());
      }
    }
    final List<Pinned> result = new ArrayList<>();
    for (String line : saved.split("\n")) {
      final String[] parts = line.split("\t", -1);
      if (parts.length >= 2 && !parts[0].isEmpty()) {
        final Uri uri = Uri.parse(parts[0]);
        if (granted.contains(uri)) {
          result.add(new Pinned(uri, parts[1], parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null));
        }
      }
    }
    return result;
  }

  private static void save (List<Pinned> folders) {
    final StringBuilder b = new StringBuilder();
    for (Pinned folder : folders) {
      if (b.length() > 0) {
        b.append('\n');
      }
      b.append(folder.tree.toString()).append('\t').append(folder.name.replace('\t', ' ').replace('\n', ' ')).append('\t');
      if (folder.app != null) {
        b.append(folder.app.replace('\t', ' ').replace('\n', ' '));
      }
    }
    Settings.instance().pmc().putString(KEY, b.toString());
  }

  /** Pins a folder picked with Android's folder picker (keeps read access to it); asks its app for its name. */
  @WorkerThread
  public static void add (Context context, Uri tree) {
    try {
      context.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Throwable t) {
      Log.w(TAG, "takePersistableUriPermission", t);
    }
    final String name = folderName(context, tree, rootDocumentId(tree));
    final String app = appName(context, tree);
    final List<Pinned> folders = new ArrayList<>(list(context));
    for (int i = folders.size() - 1; i >= 0; i--) {
      if (folders.get(i).tree.equals(tree)) {
        folders.remove(i);
      }
    }
    folders.add(new Pinned(tree, name, app));
    save(folders);
  }

  /** Unpins a folder (and gives its access back to Android). */
  public static void remove (Context context, Uri tree) {
    final List<Pinned> folders = new ArrayList<>(list(context));
    for (int i = folders.size() - 1; i >= 0; i--) {
      if (folders.get(i).tree.equals(tree)) {
        folders.remove(i);
      }
    }
    save(folders);
    try {
      context.getContentResolver().releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Throwable t) {
      Log.w(TAG, "releasePersistableUriPermission", t);
    }
  }

  /** The document id of a pinned folder itself. */
  public static String rootDocumentId (Uri tree) {
    return DocumentsContract.getTreeDocumentId(tree);
  }

  /** A folder's name, as its app shows it. */
  @WorkerThread
  public static String folderName (Context context, Uri tree, String documentId) {
    final Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
    try (Cursor cursor = context.getContentResolver().query(uri, new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        final String name = cursor.getString(0);
        if (name != null && !name.isEmpty()) {
          return name;
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "folderName", t);
    }
    return documentId;
  }

  /** The app a pinned folder comes from ("OneDrive", "Material Files"), or null. */
  @WorkerThread
  public static @Nullable String appName (Context context, Uri tree) {
    try {
      final PackageManager pm = context.getPackageManager();
      final ProviderInfo provider = pm.resolveContentProvider(tree.getAuthority(), 0);
      if (provider != null) {
        final CharSequence label = provider.applicationInfo.loadLabel(pm);
        return label != null ? label.toString() : null;
      }
    } catch (Throwable t) {
      Log.w(TAG, "appName", t);
    }
    return null;
  }

  /** A folder's entries, folders first, then by name; null when the app didn't answer (signed out, offline). */
  @WorkerThread
  public static @Nullable List<Entry> children (Context context, Uri tree, String documentId) {
    final ContentResolver resolver = context.getContentResolver();
    final Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
    final String[] columns = {
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };
    try (Cursor cursor = resolver.query(uri, columns, null, null, null)) {
      if (cursor == null) {
        return null;
      }
      final List<Entry> entries = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        final String id = cursor.getString(0);
        final String name = cursor.getString(1);
        if (id == null || name == null || name.startsWith(".")) {
          continue; // hidden files stay hidden, as in the phone's folders
        }
        entries.add(new Entry(id, name, cursor.getString(2), cursor.isNull(3) ? -1 : cursor.getLong(3), cursor.isNull(4) ? 0 : cursor.getLong(4)));
      }
      Collections.sort(entries, (a, b) -> {
        if (a.isFolder() != b.isFolder()) {
          return a.isFolder() ? -1 : 1;
        }
        return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
      });
      return entries;
    } catch (Throwable t) {
      Log.w(TAG, "children", t);
      return null;
    }
  }

  /** The content URI of an entry, for sending it (TDLib copies it when it uploads). */
  public static Uri documentUri (Uri tree, String documentId) {
    return DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
  }
}
