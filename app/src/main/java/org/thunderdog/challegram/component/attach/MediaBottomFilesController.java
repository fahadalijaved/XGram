/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.SessionSnapshot;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.Permissions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.StorageUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public class MediaBottomFilesController extends MediaBottomBaseController<Void> implements View.OnClickListener, Menu, View.OnLongClickListener, Comparator<File>, TGPlayerController.PlayListBuilder {
  public MediaBottomFilesController (MediaLayout context) {
    super(context, R.string.File);
  }

  @Override
  public int getId () {
    return R.id.controller_media_files;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      // XGram: Other apps (Android's picker, as before) and Arrange this list
      if (!stack.isEmpty()) {
        showSystemPicker(false);
        return;
      }
      showOptions(null, new int[] {R.id.btn_xgramFilesOtherApps, R.id.btn_xgramFilesArrange},
        new String[] {Lang.getString(R.string.XgramFilesOtherApps), Lang.getString(R.string.XgramFilesArrange)},
        null, new int[] {R.drawable.baseline_cloud_24, R.drawable.baseline_sort_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_xgramFilesOtherApps) {
            showSystemPicker(false);
          } else if (optionId == R.id.btn_xgramFilesArrange) {
            xgramArrange();
          }
          return true;
        });
    }
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void showSystemPicker (boolean forceDownloads) {
    RunnableData<Set<Uri>> callback = uris -> {
      if (uris != null && !uris.isEmpty()) {
        Media.instance().post(() -> {
          final ArrayList<InlineResult<?>> results = new ArrayList<>(uris.size());
          final TD.FileInfo fileInfo = new TD.FileInfo();
          for (Uri uri : uris) {
            String filePath = U.tryResolveFilePath(uri);
            if (!StringUtils.isEmpty(filePath) && U.canReadFile(filePath)) {
              results.add(createItem(context, tdlib, new File(filePath), null));
            } else {
              final String path = uri.toString();
              TD.createInputFile(path, null, fileInfo);
              results.add(createItem(context, tdlib, path, R.drawable.baseline_insert_drive_file_24, fileInfo.title, Strings.buildSize(fileInfo.knownSize)));
            }
          }
          UI.post(() -> mediaLayout.getFilesControllerDelegate().onFilesSelected(results, false));
        });
      }
    };
    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("*/*")
      .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      .putExtra("android.content.extra.SHOW_ADVANCED", true);
    context.putActivityResultHandler(Intents.ACTIVITY_RESULT_FILES, (requestCode, resultCode, data) -> {
      if (resultCode == Activity.RESULT_OK) {
        // Use a LinkedHashSet to maintain any ordering that may be
        // present in the ClipData
        LinkedHashSet<Uri> uris = new LinkedHashSet<>();
        Uri dataUri = data.getData();
        if (dataUri != null) {
          uris.add(dataUri);
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
          for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            if (uri != null) {
              uris.add(uri);
            }
          }
        }
        if (!uris.isEmpty()) {
          callback.runWithData(uris);
        }
      }
    });
    try {
      context.startActivityForResult(intent, Intents.ACTIVITY_RESULT_FILES);
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
      Log.i(e);
    }
  }

  private SettingsAdapter adapter;

  private static final String KEY_GALLERY = "gallery";
  private static final String KEY_BUCKET = "bucket";
  private static final String KEY_MUSIC = "music";
  private static final String KEY_DOWNLOADS = "downloads";
  private static final String KEY_SNAPSHOT = "snapshot";
  private static final String KEY_FOLDER = "dir://";
  private static final String KEY_FILE = "file://";
  private static final String KEY_UPPER = "..";
  // XGram: a folder of another storage app: "tree://" + its tree URI + "\n" + the document id inside it
  private static final String KEY_TREE = "tree://";
  private static final String KEY_OTHER_APPS = "xgram_other_apps";
  private static final String KEY_ADD_FOLDER = "xgram_add_folder";
  private static final int REQUEST_STORAGE_FOLDER = 10095;
  // XGram: a folder of an SFTP server: "sftp://" + the server's id + "\n" + the folder (empty: where it opens)
  private static final String KEY_SFTP = "sftp://";
  private static final String KEY_ADD_SFTP = "xgram_add_sftp";
  private static final String KEY_ADD_STORAGE = "xgram_add_storage";
  private static final int REQUEST_SFTP_KEY = 10096;
  private View xgramLastView;
  private InlineResultCommon xgramLastResult;
  /** XGram: SFTP servers and other apps' folders may wait on the network: their own thread, not the shared one. */
  private static final java.util.concurrent.ExecutorService xgramNetworkQueue = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "XgramFilesNetwork"));

  private static java.util.concurrent.Executor xgramQueue (String path) {
    return path != null && (path.startsWith(KEY_SFTP) || path.startsWith(KEY_TREE)) ? xgramNetworkQueue : runnable -> Background.instance().post(runnable);
  }

  private int initialItemsCount;

  private void buildCells () {
    navigateToPath(null, null, null, false, null, null, null);
  }

  private void navigateToPath (final View view, final String currentPath, final String parentPath, boolean isUpper, final InlineResultCommon data, Runnable onDone, Runnable onError) {
    cancelCurrentLoadOperation();

    ArrayList<ListItem> items = new ArrayList<>();

    if (currentPath != null && !currentPath.isEmpty()) {
      if (!isUpper) {
        boolean isMusic = KEY_MUSIC.equals(currentPath);
        if (mediaLayout.getFilesControllerDelegate().showRestriction(view, isMusic ? RightId.SEND_AUDIO : RightId.SEND_DOCS)) {
          return;
        }
      }
      LoadOperation operation;
      RunnableData<Runnable> before = null;
      if (KEY_GALLERY.equals(currentPath)) {
        operation = buildGallery();
      } else if (KEY_MUSIC.equals(currentPath)) {
        operation = buildMusic();
      } else if (KEY_DOWNLOADS.equals(currentPath)) {
        operation = buildDownloads();
      } else if (KEY_BUCKET.equals(currentPath)) {
        operation = buildBucket(data);
      } else if (KEY_SNAPSHOT.equals(currentPath)) {
        operation = buildSnapshot(view);
        before = after -> showOptions(Lang.getMarkdownString(this, R.string.SessionSnapshotWarning), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ApplicationFolderWarningConfirm), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_done) {
            after.run();
          }
          return true;
        });
      } else if (currentPath.startsWith(KEY_TREE)) {
        operation = xgramBuildTree(currentPath, parentPath); // XGram: a folder of another storage app
      } else if (currentPath.startsWith(KEY_SFTP)) {
        operation = xgramBuildSftp(currentPath); // XGram: a folder of an SFTP server
      } else if (currentPath.startsWith(KEY_FOLDER)) {
        String path = currentPath.substring(KEY_FOLDER.length());
        operation = buildFolder(path, parentPath);
        String internalPath = AppContext.get().getFilesDir().getPath();
        File external = AppContext.get().getExternalFilesDir(null);
        String externalPath = external != null ? external.getPath() : null;
        if (!isUpper && (path.equals(internalPath) || path.equals(externalPath))) {
          before = after -> showOptions(Lang.getMarkdownString(this, R.string.ApplicationFolderWarning), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ApplicationFolderWarningConfirm), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_done) {
              after.run();
            }
            return true;
          });
        }
      } else {
        operation = null;
      }
      if (operation != null) {
        if (currentPath.startsWith(KEY_SFTP) || currentPath.startsWith(KEY_TREE)) {
          // XGram: a server or another app may take a while: after a moment a "Connecting…" pill says so, and it
          // goes when the folder opens, fails or is left
          final LoadOperation networkOperation = operation;
          final Runnable done = onDone, failed = onError;
          final boolean[] finished = {false};
          operation.setCallbacks(() -> {
            finished[0] = true;
            xgramShowBusy(null);
            if (done != null) {
              done.run();
            }
          }, () -> {
            finished[0] = true;
            xgramShowBusy(null);
            if (failed != null) {
              failed.run();
            }
          });
          final String busyText = xgramBusyText(currentPath, data);
          UI.post(() -> {
            if (!finished[0] && currentLoadOperation == networkOperation && !networkOperation.isCancelled() && !isDestroyed()) {
              xgramShowBusy(busyText);
            }
          }, 250);
        } else {
          operation.setCallbacks(onDone, onError);
        }
        this.currentLoadOperation = operation;
        if (before != null) {
          before.runWithData(() -> xgramQueue(currentPath).execute(operation));
        } else {
          xgramQueue(currentPath).execute(operation);
        }
      }
      return;
    }

    try {
      final File baseExternalDir = Environment.getExternalStorageDirectory();
      if (baseExternalDir != null) {
        final String environmentPath = baseExternalDir.getPath();
        final boolean isRemovable = Environment.isExternalStorageRemovable();
        StatFs fs = new StatFs(environmentPath);
        String text = Lang.getString(R.string.FreeXofY, Strings.buildSize(StorageUtils.freeMemorySize(fs)), Strings.buildSize(StorageUtils.totalMemorySize(fs)));
        InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + environmentPath, ColorId.fileAttach, isRemovable ? R.drawable.baseline_sd_storage_24 : R.drawable.baseline_storage_24, Lang.getString(isRemovable ? R.string.SdCard : R.string.InternalStorage), text).setDisableProgressInteract(true);
        items.add(createItem(internalStorage, R.id.btn_internalStorage));
      }

      final ArrayList<String> externalStorageFiles = U.getExternalStorageDirectories(baseExternalDir != null ? baseExternalDir.getPath() : null, false);
      if (externalStorageFiles != null) {
        for (String dir : externalStorageFiles) {
          InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + dir, ColorId.fileAttach, R.drawable.baseline_storage_24, Lang.getString(R.string.Storage), dir).setDisableProgressInteract(true);
          items.add(createItem(internalStorage, R.id.btn_internalStorage));
        }
      }

    } catch (Throwable t) {
      Log.e("Cannot add storage directory", t);
    }

    xgramAddStorageItems(items); // XGram: the pinned folders and SFTP servers, Other apps, Add storage

    InlineResultCommon galleryItem = createItem(context, tdlib, KEY_GALLERY, R.drawable.baseline_image_24, Lang.getString(R.string.Gallery), Lang.getString(R.string.SendMediaHint));
    items.add(createItem(galleryItem, R.id.btn_galleryFiles));

    InlineResultCommon musicItem = createItem(context, tdlib, KEY_MUSIC, R.drawable.baseline_music_note_24, Lang.getString(R.string.Music), Lang.getString(R.string.SendMusicHint));
    items.add(createItem(musicItem, R.id.btn_musicFiles));

    boolean addedDownloads = false;
    boolean downloadsEmpty = false;
    if (context.permissions().canManageStorage()) {
      try {
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (file.exists() && file.isDirectory()) {
          File[] files = file.listFiles();
          if (files != null && !(downloadsEmpty = files.length == 0)) {
            InlineResultCommon common = createItem(context, tdlib, KEY_FOLDER + file.getPath(), R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), Lang.plural(R.string.xFiles, files.length));
            items.add(createItem(common, file.isDirectory() ? R.id.btn_folder : R.id.btn_file));
            addedDownloads = true;
          }
        }
      } catch (Throwable t) {
        Log.e("Cannot add Downloads directory", t);
      }
    }
    if (!addedDownloads && !downloadsEmpty && context().permissions().canRequestDownloadsAccess()) {
      InlineResultCommon downloadsItem = createItem(context, tdlib, KEY_DOWNLOADS, R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), Lang.getString(R.string.Files));
      items.add(createItem(downloadsItem, R.id.btn_downloads));
    }

    boolean hasRoot = false;
    try {
      final File rootDir = new File("/");
      File[] files = rootDir.listFiles();
      if (files != null && files.length > 0) {
        int filesCount = 0;
        int foldersCount = 0;
        for (File file : files) {
          if (file.isDirectory())
            foldersCount++;
          else
            filesCount++;
        }
        String text;
        if (filesCount != 0 && foldersCount != 0)
          text = Lang.getString(R.string.format_filesAndFolders, Lang.plural(R.string.xFolders, foldersCount), Lang.plural(R.string.xFiles, filesCount));
        else if (foldersCount != 0)
          text = Lang.plural(R.string.xFolders, foldersCount);
        else
          text = Lang.plural(R.string.xFiles, filesCount);
        InlineResultCommon rootDirectory = new InlineResultCommon(context, tdlib, KEY_FOLDER + rootDir.getPath(), ColorId.fileAttach, R.drawable.baseline_folder_24, Lang.getString(R.string.RootDirectory), text).setDisableProgressInteract(true);
        items.add(createItem(rootDirectory, R.id.btn_folder));
        hasRoot = true;
      }
    } catch (Throwable t) {
      Log.i("Cannot add root directory", t);
    }

    initialItemsCount = items.size();

    if (!hasRoot) {
      addApplicationFolders(items);
    }

    /*if (Settings.instance().inDeveloperMode()) {
      String internalPath = TD.getTGDir(false);
      String externalPath = TD.getTGDir(true);

      InlineResultCommon internal = createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_folder_24, "[TDLib] Internal", internalPath);
      items.add(createItem(internal, R.id.btn_folder));

      if (!internalPath.equals(externalPath)) {
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_folder_24, "[TDLib] External", externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }

      File externalFile = UI.getContext().getExternalFilesDir(null);
      if (externalFile != null && !Strings.compare(externalPath, externalFile.getPath())) {
        externalPath = externalFile.getPath();
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_folder_24, "[Challegram] External", externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }

      File internalFile = UI.getContext().getFilesDir();
      if (internalFile != null) {
        internalPath = internalFile.getPath();
        if (!externalPath.equals(internalPath)) {
          internal = createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_folder_24, "[Challegram] Internal", internalPath);
          items.add(createItem(internal, R.id.btn_folder));
        }
      }
    }*/

    xgramApplyOrder(items); // XGram: the order the user arranged
    xgramRootItems = new ArrayList<>(items);
    setFilesItems(null, items, false);
  }

  // XGram: the File tab's rows in the user's order (⋮ → Arrange this list), by their paths; rows that came later
  // (a new pinned folder or server) follow the arranged ones in the app's order

  private static final String KEY_ORDER = "xgram_files_order";
  private ArrayList<ListItem> xgramRootItems;

  private static @Nullable String xgramKeyOf (ListItem item) {
    return item.getData() instanceof InlineResultCommon ? ((InlineResultCommon) item.getData()).getId() : null;
  }

  private void xgramApplyOrder (ArrayList<ListItem> items) {
    final String saved = org.thunderdog.challegram.unsorted.Settings.instance().pmc().getString(KEY_ORDER, null);
    if (saved == null || saved.isEmpty()) {
      return;
    }
    final java.util.List<String> order = java.util.Arrays.asList(saved.split("\u0001"));
    final ArrayList<ListItem> original = new ArrayList<>(items);
    Collections.sort(items, (a, b) -> {
      final int ia = order.indexOf(xgramKeyOf(a)), ib = order.indexOf(xgramKeyOf(b));
      final int ka = ia != -1 ? ia : order.size() + original.indexOf(a);
      final int kb = ib != -1 ? ib : order.size() + original.indexOf(b);
      return Integer.compare(ka, kb);
    });
  }

  private void xgramArrange () {
    if (xgramRootItems == null || !stack.isEmpty()) {
      return;
    }
    final ArrayList<com.fahadali.xgram.compose.XgramArrangeDialog.Item> rows = new ArrayList<>();
    for (ListItem item : xgramRootItems) {
      final String key = xgramKeyOf(item);
      if (key != null) {
        final InlineResultCommon result = (InlineResultCommon) item.getData();
        rows.add(new com.fahadali.xgram.compose.XgramArrangeDialog.Item(key, result.xgramTitle(), result.xgramDescription()));
      }
    }
    com.fahadali.xgram.compose.XgramArrangeDialog.show(context, Lang.getString(R.string.XgramFilesArrange), rows, keys -> {
      final StringBuilder b = new StringBuilder();
      for (String key : keys) {
        if (b.length() > 0) {
          b.append('\u0001');
        }
        b.append(key);
      }
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().putString(KEY_ORDER, b.toString());
      buildCells();
    }, () -> {
      org.thunderdog.challegram.unsorted.Settings.instance().pmc().remove(KEY_ORDER);
      buildCells();
    });
  }

  @Override
  public boolean supportsMediaGrouping () {
    return true;
  }

  private void setFilesItems (final LoadOperation context, final ArrayList<ListItem> items, final boolean extend) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      UI.post(() -> setFilesItems(context, items, extend));
      return;
    }

    if (context != null && (this.currentLoadOperation != context || context.isCancelled() || isDestroyed())) {
      return;
    }

    adapter.setItems(items, false);

    if (extend && !isExpanded()) {
      expandFully();
    }
  }

  public static class FileEntry {
    private final Uri uri;
    private final long _id;
    private final String displayName;
    private final long size;
    private final String data;
    private final String mimeType;
    private final long dateAdded, dateModified;

    public FileEntry (Uri uri, long _id, String displayName, long size, String data, String mimeType, long dateAdded, long dateModified) {
      this.uri = uri;
      this._id = _id;
      this.displayName = displayName;
      this.size = size;
      this.data = data;
      this.mimeType = mimeType;
      this.dateAdded = dateAdded;
      this.dateModified = dateModified;
    }

    public Uri getUri () {
      return uri;
    }

    public long getId () {
      return _id;
    }

    public String getDisplayName () {
      return displayName;
    }

    public long getSize () {
      return size;
    }

    public String getData () {
      return data;
    }

    public String getMimeType () {
      return mimeType;
    }

    public long getDateAdded () {
      return dateAdded;
    }

    public long getDateModified () {
      return dateModified;
    }
  }

  public static class MusicEntry {
    private final long _id;
    private final String artist, title, path;
    private final long duration;
    private final String mime;
    private final long albumId;

    public MusicEntry (long _id, String artist, String title, String path, long duration, String mime, long albumId) {
      this._id = _id;
      this.artist = artist;
      this.title = title;
      this.path = path;
      this.duration = duration;
      this.mime = mime;
      this.albumId = albumId;
    }

    public String getMimeType () {
      return mime;
    }

    public long getId () {
      return _id;
    }

    public String getArtist () {
      return artist;
    }

    public String getTitle () {
      return title;
    }

    public String getPath () {
      return path;
    }

    public long getDuration () {
      return duration;
    }

    public long getAlbumId () {
      return albumId;
    }

    public boolean probablyHasArtwork () {
      return getAlbumId() != 0;
    }

    public Uri getArtwork () {
      return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), getAlbumId());
    }
  }

  private static abstract class LoadOperation implements Runnable {
    private final MediaBottomFilesController context;

    public static class Result {
      ArrayList<ListItem> items;
      boolean needExpand;

      public Result (ArrayList<ListItem> items, boolean needExpand) {
        this.items = items;
        this.needExpand = needExpand;
      }

      public boolean isEmpty () {
        return items == null || items.isEmpty();
      }
    }

    public LoadOperation (MediaBottomFilesController context) {
      this.context = context;
    }

    private volatile boolean isCancelled;

    abstract Result act ();

    // XGram: no lock (the field is volatile): run() holds the lock through act(), which may now wait on the network
    // (an SFTP server or another app's folder), and cancelling from the main thread froze the app meanwhile
    public void cancel () {
      isCancelled = true;
    }

    public boolean isCancelled () {
      return isCancelled;
    }

    private Runnable onDone, onError;

    public void setCallbacks (Runnable onDone, Runnable onError) {
      this.onDone = onDone;
      this.onError = onError;
    }

    @Override
    public void run () {
      synchronized (this) {
        if (!isCancelled) {
          final Result result = act();
          final Runnable callback = result != null && !result.isEmpty() ? onDone : onError;
          if (callback != null) {
            UI.post(() -> {
              if (!context.isDestroyed() && context.currentLoadOperation == LoadOperation.this && !isCancelled()) {
                if (result != null && !result.isEmpty()) {
                  context.setFilesItems(LoadOperation.this, result.items, result.needExpand);
                }
                callback.run();
              }
            });
          }
        }
      }
    }
  }

  private LoadOperation buildGallery () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        Media.Gallery gallery = Media.instance().getGallery();

        if (gallery == null) {
          openAlert(this, R.string.AppName, R.string.AccessError);
          return null;
        }

        if (gallery.isEmpty()) {
          openAlert(this, R.string.AppName, R.string.NothingFound);
          return null;
        }

        ArrayList<ListItem> items = new ArrayList<>(gallery.getBucketCount() - 1 + gallery.getAllMediaBucket().size());
        InlineResult<?> home = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_image_24, "..", Lang.getString(R.string.AttachFolderHome));
        items.add(createItem(home, R.id.btn_folder_upper));

        ArrayList<Media.GalleryBucket> buckets = gallery.getBuckets();
        Media.GalleryBucket allBucket = gallery.getAllMediaBucket();

        boolean hasRecentImages = false;
        if (allBucket != null && allBucket.size() > 0) {
          boolean first = true;
          int recentCount = 0;
          for (ImageFile file : allBucket.getMedia()) {
            if (file instanceof ImageGalleryFile) {
              ImageGalleryFile galleryFile = (ImageGalleryFile) file;
              if (!DateUtils.isToday(galleryFile.getDateTaken() / 1000l, TimeUnit.SECONDS) && !DateUtils.isYesterday(galleryFile.getDateTaken() / 1000l, TimeUnit.SECONDS)) {
                break;
              }
              if (first) {
                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Recent));
                first = false;
              }
              InlineResult<?> result = createItem(context, tdlib, (ImageGalleryFile) file);
              ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setLongId(((ImageGalleryFile) file).getGalleryId()).setData(result);
              items.add(item);
              hasRecentImages = true;
              recentCount++;
              if (recentCount == 10) {
                break;
              }
            }
          }
        }

        if (hasRecentImages) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Folders));
        }

        for (Media.GalleryBucket bucket : buckets) {
          if (bucket != allBucket) {
            InlineResult<?> result = createItem(context, tdlib, bucket);
            ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_bucket).setLongId(bucket.getId()).setData(result);
            items.add(item);
          }
        }

        if (!items.isEmpty()) {
          return new Result(items, true);
        }

        return null;
      }
    };
  }

  private LoadOperation buildBucket (final InlineResultCommon data) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        Media.GalleryBucket bucket = (Media.GalleryBucket) data.getTag();
        if (bucket == null || bucket.size() == 0) {
          return null;
        }

        int size = bucket.size();
        ArrayList<ListItem> items = new ArrayList<>( size + 1);
        InlineResult<?> home = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_image_24, "..", Lang.getString(R.string.Gallery));
        items.add(createItem(home, R.id.btn_folder_upper));

        for (ImageFile file : bucket.getMedia()) {
          if (file instanceof ImageGalleryFile) {
            InlineResult<?> result = createItem(context, tdlib, (ImageGalleryFile) file);
            ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setLongId(((ImageGalleryFile) file).getGalleryId()).setData(result);
            items.add(item);
          }
        }

        return new Result(items, true);
      }
    };
  }

  private LoadOperation buildDownloads () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
          return null;
        }
        final String[] projection = {
          MediaStore.Downloads._ID,
          MediaStore.Downloads.DISPLAY_NAME,
          MediaStore.Downloads.SIZE,
          MediaStore.Downloads.DATA,
          MediaStore.Downloads.RELATIVE_PATH,
          MediaStore.Downloads.MIME_TYPE,
          MediaStore.Downloads.DATE_ADDED,
          MediaStore.Downloads.DATE_MODIFIED,
          MediaStore.Downloads.IS_PENDING
        };
        try {
          try (Cursor c = AppContext.get().getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, MediaStore.Downloads.IS_PENDING + " != 1", null, MediaStore.Downloads.DATE_MODIFIED + " desc, " + MediaStore.Downloads.DATE_ADDED + " desc")) {
            if (c == null) {
              openAlert(this, R.string.AppName, R.string.AccessError);
              return null;
            }

            int count = c.getCount();
            ArrayList<FileEntry> entries = new ArrayList<>(count);
            while (c.moveToNext()) {
              long id = c.getLong(0);
              String displayName = c.getString(1);
              long size = c.getLong(2);
              String data = c.getString(3);
              String relativePath = c.getString(4);
              String mimeType = c.getString(5);
              long dateAdded = c.getLong(6);
              long dateModified = c.getLong(7);
              if (!StringUtils.isEmpty(data)) {
                entries.add(new FileEntry(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL, id), id, displayName, size, data, mimeType, dateAdded, dateModified));
              }
            }

            if (entries.isEmpty()) {
              openAlert(this, R.string.AppName, R.string.NoDownloadFilesFound);
              return null;
            }

            ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
            InlineResult<?> result = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", Lang.getString(R.string.AttachFolderHome));
            items.add(createItem(result, R.id.btn_folder_upper));

            for (FileEntry entry : entries) {
              final String subtitle = Lang.getFileTimestamp(Math.max(entry.getDateModified(), entry.getDateAdded()), TimeUnit.SECONDS, entry.getSize());
              InlineResultCommon fileItem = new InlineResultCommon(context, tdlib, new File(entry.getData()), entry.getDisplayName(), subtitle, entry, false).setDisableProgressInteract(true);
              items.add(createItem(fileItem, R.id.btn_file));
            }

            return new Result(items, true);

          }
        } catch (Throwable t) {
          Log.e("Cannot build downloads", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  private LoadOperation buildMusic () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        final String[] projection = {
          MediaStore.Audio.Media._ID,
          MediaStore.Audio.Media.ARTIST,
          MediaStore.Audio.Media.TITLE,
          MediaStore.Audio.Media.DATA,
          MediaStore.Audio.Media.DURATION,
          MediaStore.Audio.Media.DATE_ADDED,
          MediaStore.Audio.Media.MIME_TYPE,
          MediaStore.Audio.Media.ALBUM_ID,
        };
        try {
          Cursor c = AppContext.get().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.DATE_ADDED + " desc");
          if (c == null) {
            openAlert(this, R.string.AppName, R.string.AccessError);
            return null;
          }

          int count = c.getCount();

          ArrayList<MusicEntry> entries = new ArrayList<>(count);
          while (c.moveToNext()) {
            long id = c.getLong(0);
            String artist = c.getString(1);
            String title = c.getString(2);
            String data = c.getString(3);
            long duration = c.getInt(4);
            String mime = c.getString(6);
            long albumId = c.getLong(7);
            if (!StringUtils.isEmpty(data)) {
              entries.add(new MusicEntry(id, artist != null ? artist : "", title != null ? title : "", data, duration, mime, albumId));
            }
          }
          U.closeCursor(c);

          if (entries.isEmpty()) {
            openAlert(this, R.string.AppName, R.string.NoMusicFilesFound);
            return null;
          }

          Collections.sort(entries, (o1, o2) -> {
            int cmp = o1.artist.compareToIgnoreCase(o2.artist);
            return cmp != 0 ? cmp : o1.title.compareTo(o2.title);
          });

          ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
          InlineResult<?> result = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", Lang.getString(R.string.AttachFolderHome));
          items.add(createItem(result, R.id.btn_folder_upper));

          for (MusicEntry entry : entries) {
            items.add(new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setData(new InlineResultCommon(context, tdlib, entry, MediaBottomFilesController.this).setDisableProgressInteract(true)));
          }

          return new Result(items, true);
        } catch (Throwable t) {
          Log.e("Cannot build music", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    List<ListItem> items = adapter.getItems();
    ArrayList<TdApi.Message> out = null;
    int foundIndex = -1;
    for (ListItem item : items) {
      if (item.getId() != R.id.btn_file || item.getViewType() != ListItem.TYPE_CUSTOM_INLINE || !(item.getData() instanceof InlineResultCommon)) {
        continue;
      }
      InlineResultCommon inlineResult = (InlineResultCommon) item.getData();
      if (inlineResult.getType() == InlineResult.TYPE_AUDIO) {
        TdApi.Message fakeMessage = inlineResult.getPlayPauseMessage();
        if (fakeMessage == null) {
          continue;
        }
        if (foundIndex == -1 && TGPlayerController.compareTracks(fakeMessage, fromMessage)) {
          foundIndex = out != null ? out.size() : 0;
        }
        if (out == null) {
          out = new ArrayList<>();
        }
        out.add(fakeMessage);
      }
    }
    if (out == null || foundIndex == -1) {
      return null;
    }
    return new TGPlayerController.PlayList(out, foundIndex).setReachedEnds(true, true);
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return false;
  }

  private LoadOperation currentLoadOperation;

  private void cancelCurrentLoadOperation () {
    if (currentLoadOperation != null) {
      currentLoadOperation.cancel();
      currentLoadOperation = null;
    }
    xgramShowBusy(null); // XGram: a server that didn't answer was left
  }

  /** XGram: "Connecting to Server…" for a server itself, "Opening Photos…" for a folder, "Loading…" going back up. */
  private String xgramBusyText (String path, @Nullable InlineResultCommon data) {
    final String title = data != null ? data.xgramTitle() : null;
    if (StringUtils.isEmpty(title) || "..".equals(title)) {
      return Lang.getString(R.string.XgramFilesLoading);
    }
    final boolean serverRoot = path.startsWith(KEY_SFTP) && path.endsWith("\n");
    return Lang.getString(serverRoot ? R.string.XgramFilesConnecting : R.string.XgramFilesOpening, title);
  }

  private void openAlert (final LoadOperation context, final int title, final int content) {
    UI.post(() -> {
      if (!isDestroyed() && currentLoadOperation == context && !context.isCancelled()) {
        openAlert(title, content);
      }
    });
  }

  private LoadOperation buildSnapshot (View view) {
    return new LoadOperation(this) {
      @Override
      Result act () {
        try {
          File snapshot = SessionSnapshot.createSnapshotWithAllAccounts();
          UI.post(() -> {
            if (!isDestroyed()) {
              mediaLayout.sendFile(view, snapshot.getAbsolutePath());
            }
          });
          return null;
          // TODO send snapshot
        } catch (IOException e) {
          Log.w("Unable to create snapshot", e);
          return null;
        }
      }
    };
  }

  private LoadOperation buildFolder (final String path, final String parent) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        try {
          final File dir = new File(path);
          if (!dir.exists() || !dir.isDirectory()) {
            openAlert(this, R.string.AppName, R.string.FolderDoesNotExist);
            return null;
          }

          if (!dir.canRead()) {
            openAlert(this, R.string.AppName, R.string.AccessError);
            return null;
          }

          File[] files = dir.listFiles();

          if (files == null || files.length == 0) {
            openAlert(this, R.string.AppName, R.string.FolderEmpty);
            return null;
          }

          ArrayList<File> filesList = new ArrayList<>(files.length);
          Collections.addAll(filesList, files);
          Collections.sort(filesList, MediaBottomFilesController.this);

          ArrayList<ListItem> items = new ArrayList<>(filesList.size() + 1);
          items.add(createItem(createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", StringUtils.isEmpty(parent) ? Lang.getString(R.string.AttachFolderHome) : parent), R.id.btn_folder_upper));

          if ("/".equals(path)) {
            addApplicationFolders(items);
          }

          for (File file : filesList) {
            items.add(createItem(createItem(context, tdlib, file, null), file.isDirectory() ? R.id.btn_folder : R.id.btn_file));
          }

          return new Result(items, true);
        } catch (Throwable t) {
          Log.e("Cannot build folder", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  private void addApplicationFolders (List<ListItem> items) {
    try {
      String internalPath = UI.getContext().getFilesDir().getPath();
      File externalFile = UI.getContext().getExternalFilesDir(null);
      String externalPath = externalFile != null ? externalFile.getPath() : null;

      if (externalFile != null && !StringUtils.equalsOrBothEmpty(externalPath, internalPath)) {
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolderExternal), externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }
      items.add(createItem(createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolder), internalPath), R.id.btn_folder));
    } catch (Throwable t) {
      Log.e(t);
    }
    if (Config.ENABLE_BASELINE_PROFILE_HOOKS || BuildConfig.DEBUG) {
      InlineResultCommon snapshot = createItem(context, tdlib, KEY_SNAPSHOT, R.drawable.baseline_lock_24, Lang.getString(R.string.SessionSnapshot), Lang.getString(R.string.SessionSnapshotDesc));
      items.add(createItem(snapshot, R.id.btn_folder));
    }
  }

  private static String normalizePath (String path) {
    return path.startsWith(KEY_FOLDER) ? path.substring(KEY_FOLDER.length()) : path.startsWith(KEY_FILE) ? path.substring(KEY_FILE.length()) : path;
  }

  @Override
  public int compare (File o1, File o2) {
    final boolean d1 = o1.isDirectory();
    final boolean d2 = o2.isDirectory();

    if (d1 != d2) {
      return d1 ? -1 : 1;
    }

    if (d1) {
      int res = o1.compareTo(o2);
      return Integer.compare(res, 0);
    }

    final long t1 = o1.lastModified();
    final long t2 = o2.lastModified();
    if (t1 > 0 && t2 > 0 && t1 != t2) {
      return Long.compare(t2, t1);
    }

    final String n1 = o1.getName();
    final String n2 = o2.getName();

    String e1 = U.getExtension(n1);
    String e2 = U.getExtension(n2);

    if (e1 == null && e2 == null) {
      int res = n1.compareTo(n2);
      return Integer.compare(res, 0);
    }
    if (e1 == null) {
      return -1; // files without extension are higher
    }
    if (e2 == null) {
      return 1;
    }

    e1 = e1.toLowerCase(Locale.ROOT);
    e2 = e2.toLowerCase(Locale.ROOT);

    return Integer.compare(e1.equals(e2) ? n1.compareTo(n2) : e1.compareTo(e2), 0);
  }

  private void init () {
    if (adapter == null) {
      adapter = new SettingsAdapter(this);
      adapter.setOnLongClickListener(this);
      buildCells();
    }
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);

    init();

    setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    setAdapter(adapter);

    return contentView;
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, File file, @Nullable Object tag) {
    return createItem(context, tdlib, file, tag, null, file.lastModified(), null, false);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, File file, @Nullable Object tag, String title, long lastModifiedTime, String subtitle, boolean isFolder) {
    if (file.isDirectory()) {
      return new InlineResultCommon(context, tdlib, KEY_FOLDER + file.getPath(), ColorId.fileAttach, R.drawable.baseline_folder_24, file.getName(), Lang.getString(R.string.Folder)).setDisableProgressInteract(true);
    } else {
      if (subtitle == null) {
        subtitle = Lang.getFileTimestamp(lastModifiedTime, TimeUnit.MILLISECONDS, file.length());
      }
      return new InlineResultCommon(context, tdlib, file, title != null ? title : file.getName(), subtitle, tag, isFolder).setDisableProgressInteract(true);
    }
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, ImageGalleryFile file) {
    return createItem(context, tdlib, new File(file.getFilePath()), file, null, file.getDateTaken(), null, false);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, String path, int iconRes, String title, String subtitle) {
    return new InlineResultCommon(context, tdlib, path, ColorId.fileAttach, iconRes, title, subtitle).setDisableProgressInteract(true);
  }

  public static ListItem createItem (InlineResult<?> result, int id) {
    return new ListItem(ListItem.TYPE_CUSTOM_INLINE, id).setData(result);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, Media.GalleryBucket bucket) {
    File file = new File(bucket.getPreviewImage().getFilePath());
    String bucketContent = Lang.pluralPhotosAndVideos(bucket.getPhotosCount(), bucket.getVideosCount()).toString();
    String str;
    if (DateUtils.isToday(bucket.getModifyTime(), TimeUnit.MILLISECONDS)) {
      String date = Lang.getModifiedTimestamp(bucket.getModifyTime(), TimeUnit.MILLISECONDS);
      str = Lang.getString(R.string.format_contentAndModifyDate2, bucketContent, date);
    } else {
      str = bucketContent;
    }
    return createItem(context, tdlib, file, bucket, bucket.getName(), 0, str, true);
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (super.performOnBackPressed(fromTop, commit)) {
      return true;
    }
    if (inFileSelectMode) {
      if (commit) {
        mediaLayout.cancelMultiSelection();
      }
      return true;
    }
    if (!stack.isEmpty()) {
      if (commit) {
        navigateUpper();
      }
      return true;
    }
    return false;
  }

  @Override
  protected void addCustomItems (View view, @NonNull List<HapticMenuHelper.MenuItem> hapticItems) {
    hapticItems.add(0, new HapticMenuHelper.MenuItem(R.id.btn_addCaption, Lang.getString(R.string.AddCaption), R.drawable.baseline_file_caption_24).setOnClickListener(this::onHapticMenuItemClick));
  }

  private boolean onHapticMenuItemClick (View view, View parentView, HapticMenuHelper.MenuItem item) {
    final int id = view.getId();
    if (id == R.id.btn_addCaption) {
      mediaLayout.getFilesControllerDelegate().onFilesSelected(new ArrayList<>(selectedItems), true);
    }
    return true;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_folder_upper) {
      navigateUpper();
      return;
    }
    Object tag = v.getTag();
    if (tag == null || !(tag instanceof ListItem)) {
      return;
    }

    ListItem item = (ListItem) tag;
    if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE) {
      InlineResultCommon result = (InlineResultCommon) item.getData();
      final int itemId = item.getId();
      if (itemId == R.id.btn_file || itemId == R.id.btn_music) {
        if (inFileSelectMode) {
          selectItem(item, result);
        } else {
          mediaLayout.getFilesControllerDelegate().onFilesSelected(new ArrayList<>(Collections.singleton(result)), false);
        }
      } else if (itemId == R.id.btn_bucket) {
        navigateInside(v, KEY_BUCKET, result);
      } else if (KEY_OTHER_APPS.equals(result.getId())) {
        if (!mediaLayout.getFilesControllerDelegate().showRestriction(v, RightId.SEND_DOCS)) {
          showSystemPicker(false); // XGram: Android's picker: Drive, OneDrive, Dropbox and any other storage app
        }
      } else if (KEY_ADD_FOLDER.equals(result.getId())) {
        xgramPickFolder();
      } else if (KEY_ADD_SFTP.equals(result.getId())) {
        xgramEditServer(null);
      } else if (KEY_ADD_STORAGE.equals(result.getId())) {
        xgramAddStorage();
      } else {
        String path = result.getId();
        boolean isMusic = KEY_MUSIC.equals(path);
        if (mediaLayout.getFilesControllerDelegate().showRestriction(v, isMusic ? RightId.SEND_AUDIO : RightId.SEND_DOCS)) {
          return;
        }
        boolean isDownloads = KEY_DOWNLOADS.equals(path);
        if (v.getId() == R.id.btn_internalStorage || isDownloads) {
          if (!context.permissions().canManageStorage()) {
            showSystemPicker(isDownloads);
            return;
          }
          if (context.permissions().requestReadExternalStorage(Permissions.ReadType.ALL, grantType -> {
            if (grantType != Permissions.GrantResult.ALL || !context.permissions().canManageStorage()) {
              showSystemPicker(isDownloads);
            } else {
              navigateTo(v, result);
            }
          })) {
            return;
          }
        }

        if (path != null) {
          switch (path) {
            case KEY_GALLERY: {
              if (context.permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES_AND_VIDEOS, grantType -> {
                if (grantType == Permissions.GrantResult.ALL) {
                  navigateTo(v, result);
                } else {
                  // TODO 1-tap access to privacy settings?
                  context.tooltipManager().builder(v).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingGalleryPermission).hideDelayed();
                }
              })) {
                return;
              }
              break;
            }
            case KEY_MUSIC: {
              if (context.permissions().requestReadExternalStorage(Permissions.ReadType.AUDIO, grantType -> {
                if (grantType == Permissions.GrantResult.ALL) {
                  navigateTo(v, result);
                } else {
                  // TODO 1-tap access to privacy settings?
                  context.tooltipManager().builder(v).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingAudioPermission).hideDelayed();
                }
              })) {
                return;
              }
              break;
            }
          }
        }
        navigateTo(v, result);
      }
    }
  }

  private void navigateTo (View view, InlineResultCommon result) {
    String path = result.getId();
    if (path != null) {
      if (
        KEY_GALLERY.equals(path) ||
        KEY_MUSIC.equals(path) ||
        KEY_DOWNLOADS.equals(path) ||
        KEY_BUCKET.equals(path) ||
        KEY_SNAPSHOT.equals(path) ||
        path.startsWith(KEY_TREE) ||
        path.startsWith(KEY_SFTP) ||
        path.startsWith(KEY_FOLDER)) {
        navigateInside(view, path, result);
      } else if (KEY_UPPER.equals(path)) {
        navigateUpper();
      }
    }
  }

  @Override
  public boolean canMoveRecycler () {
    return super.canMoveRecycler() && stack.isEmpty();
  }

  @Override
  public boolean onLongClick (View v) {
    if (mediaLayout.inSingleMediaMode()) {
      return false;
    }

    Object tag = v.getTag();
    if (tag instanceof ListItem && stack.isEmpty() && ((ListItem) tag).getData() instanceof InlineResultCommon) {
      // XGram: a long press on a pinned folder offers to remove it
      final String id = ((InlineResultCommon) ((ListItem) tag).getData()).getId();
      if (id != null && id.startsWith(KEY_TREE)) {
        xgramOfferUnpin(id);
        return true;
      }
      if (id != null && id.startsWith(KEY_SFTP)) {
        xgramServerOptions(id.substring(KEY_SFTP.length(), id.indexOf('\n')));
        return true;
      }
    }
    if (tag != null && tag instanceof ListItem) {
      ListItem item = (ListItem) tag;
      if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE && (item.getId() == R.id.btn_file || item.getId() == R.id.btn_music)) {
        InlineResult<?> result = (InlineResult<?>) item.getData();
        if (result != null) {
          selectItem(item, result);
          return true;
        }
      }
    }
    return false;
  }

  private ArrayList<InlineResult<?>> selectedItems;

  private boolean inFileSelectMode;

  private void setInFileSelectMode (boolean inFileSelectMode) {
    if (this.inFileSelectMode != inFileSelectMode) {
      this.inFileSelectMode = inFileSelectMode;
      adapter.setInSelectMode(inFileSelectMode, false, null);
      if (!inFileSelectMode && !selectedItems.isEmpty()) {
        selectedItems.clear();
        adapter.clearSelectedItems();
      }
    }
  }

  private void selectItem (ListItem item, InlineResult<?> inlineResult) {
    int existingIndex;
    if (selectedItems == null) {
      existingIndex = -1;
      selectedItems = new ArrayList<>();
    } else {
      existingIndex = selectedItems.indexOf(inlineResult);
    }

    int selectionIndex;
    boolean isSelected;
    if (existingIndex != -1) {
      isSelected = false;
      selectionIndex = existingIndex;

      selectedItems.remove(existingIndex);
      item.setSelected(false, existingIndex);

      int remainingCount = selectedItems.size();
      for (int i = existingIndex; i < remainingCount; i++) {
        InlineResult<?> nextData = selectedItems.get(i);
        int foundIndex = adapter.indexOfViewByData(nextData);
        if (foundIndex == -1) {
          throw new IllegalStateException();
        }
        ListItem nextItem = adapter.getItems().get(foundIndex);
        int newIndex = nextItem.decrementSelectionIndex();
        adapter.setIsSelected(foundIndex, true, newIndex);
      }
    } else {
      isSelected = true;
      selectionIndex = selectedItems.size();

      selectedItems.add(inlineResult);
      item.setSelected(true, selectionIndex);
    }

    int i = 0;
    for (ListItem listItem : adapter.getItems()) {
      if (listItem.getId() == item.getId() && listItem.getData() == item.getData()) {
        adapter.setIsSelected(i, isSelected, selectionIndex);
        break;
      }
      i++;
    }
    setInFileSelectMode(!selectedItems.isEmpty());
    mediaLayout.setCounter(selectedItems.size());
  }

  @Override
  protected void onMultiSendPress (View view, @NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    if (selectedItems == null || selectedItems.isEmpty()) {
      return;
    }

    ArrayList<MusicEntry> musicEntries = new ArrayList<>();
    ArrayList<String> files = new ArrayList<>();

    for (InlineResult<?> result : selectedItems) {
      switch (result.getType()) {
        case InlineResult.TYPE_AUDIO: {
          musicEntries.add((MusicEntry) ((InlineResultCommon) result).getTag());
          break;
        }
        case InlineResult.TYPE_DOCUMENT: {
          files.add(result.getId());
          break;
        }
      }
    }

    if (musicEntries.isEmpty() && files.isEmpty()) {
      return;
    }

    mediaLayout.sendFilesMixed(view, files, musicEntries, options, true);
  }

  @Override
  protected void onCancelMultiSelection () {
    if (selectedItems != null) {
      setInFileSelectMode(false);
    }
  }

  private ArrayList<StackItem> stack = new ArrayList<>();

  private static class StackItem {
    private final String path;
    private final int position, positionOffset;

    public StackItem (String path, int position, int positionOffset) {
      this.path = path;
      this.position = position;
      this.positionOffset = positionOffset;
    }
  }

  private void navigateUpper () {
    if (!stack.isEmpty()) {
      if (inFileSelectMode) {
        mediaLayout.cancelMultiSelection();
      }
      final StackItem removedItem = stack.remove(stack.size() - 1);
      if (stack.isEmpty()) {
        buildCells();
        collapseToStart();
      } else {
        StackItem item = stack.get(stack.size() - 1);
        navigateToPath(null, item.path, getLastPath(2), true, null, () -> {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          manager.scrollToPositionWithOffset(removedItem.position, removedItem.positionOffset);
        }, this::navigateUpper);
      }
    }
  }

  private String getLastPath (int skipCount) {
    return stack.size() < skipCount ? null : normalizePath(stack.get(stack.size() - skipCount).path);
  }

  private void navigateInside (final View view, final String path, final InlineResultCommon data) {
    xgramLastView = view; // XGram: to open it again once a server's key is trusted
    xgramLastResult = data;
    if (inFileSelectMode) {
      mediaLayout.cancelMultiSelection();
    }

    final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    final int firstPosition = manager.findFirstVisibleItemPosition();
    final View firstView = firstPosition != RecyclerView.NO_POSITION ? manager.findViewByPosition(firstPosition) : null;
    final int firstPositionOffset = firstView != null ? firstView.getTop() : 0;

    navigateToPath(view, path, getLastPath(1), false, data, () -> {
      stack.add(new StackItem(path, firstPosition != RecyclerView.NO_POSITION ? firstPosition : 0, firstPositionOffset));
      manager.scrollToPositionWithOffset(0, 0);
    }, null);
  }

  @Override
  protected int getInitialContentHeight () {
    init();
    return Screen.dp(72f) * (initialItemsCount != 0 ? initialItemsCount : U.isExternalMemoryAvailable() && !Environment.isExternalStorageEmulated() ? 5 : 4);
  }

  @Override
  protected int getRecyclerHeaderOffset () {
    return Screen.dp(101f);
  }

  // XGram: other storage apps. "Other apps" opens Android's picker (Drive, OneDrive, Dropbox, file managers with SFTP
  // or SMB…); a folder picked with Android's folder picker is pinned here and browsed like the phone's own folders

  private static String xgramTreePath (Uri tree, String documentId) {
    return KEY_TREE + tree + "\n" + documentId;
  }

  private void xgramAddStorageItems (ArrayList<ListItem> items) {
    for (com.fahadali.xgram.files.XgramStorageFolders.Pinned folder : com.fahadali.xgram.files.XgramStorageFolders.list(context)) {
      final String path = xgramTreePath(folder.tree, com.fahadali.xgram.files.XgramStorageFolders.rootDocumentId(folder.tree));
      items.add(createItem(createItem(context, tdlib, path, R.drawable.baseline_folder_24, folder.name, folder.app != null ? folder.app : Lang.getString(R.string.XgramFilesPinned)), R.id.btn_folder));
    }
    for (com.fahadali.xgram.files.XgramSftp.Server server : com.fahadali.xgram.files.XgramSftp.servers()) {
      items.add(createItem(createItem(context, tdlib, KEY_SFTP + server.id + "\n", R.drawable.baseline_router_24, server.label(), "SFTP · " + server.address()), R.id.btn_folder));
    }
    items.add(createItem(createItem(context, tdlib, KEY_OTHER_APPS, R.drawable.baseline_cloud_24, Lang.getString(R.string.XgramFilesOtherApps), Lang.getString(R.string.XgramFilesOtherAppsInfo)), R.id.btn_folder));
    items.add(createItem(createItem(context, tdlib, KEY_ADD_STORAGE, R.drawable.baseline_create_new_folder_24, Lang.getString(R.string.XgramFilesAddStorage), Lang.getString(R.string.XgramFilesAddStorageInfo)), R.id.btn_folder));
  }

  /** Add storage: a folder of another app, or an SFTP server. */
  private void xgramAddStorage () {
    showOptions(null, new int[] {R.id.btn_xgramAddStorageFolder, R.id.btn_xgramAddStorageSftp},
      new String[] {Lang.getString(R.string.XgramFilesAddFolder), Lang.getString(R.string.XgramSftpAdd)},
      null, new int[] {R.drawable.baseline_create_new_folder_24, R.drawable.baseline_router_24}, (itemView, id) -> {
        if (id == R.id.btn_xgramAddStorageFolder) {
          xgramPickFolder();
        } else if (id == R.id.btn_xgramAddStorageSftp) {
          xgramEditServer(null);
        }
        return true;
      });
  }

  private void xgramPickFolder () {
    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
      .putExtra("android.content.extra.SHOW_ADVANCED", true);
    context.putActivityResultHandler(REQUEST_STORAGE_FOLDER, (requestCode, resultCode, data) -> {
      final Uri tree = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
      if (tree == null) {
        return;
      }
      Background.instance().post(() -> {
        com.fahadali.xgram.files.XgramStorageFolders.add(context, tree);
        UI.post(() -> {
          if (!isDestroyed() && stack.isEmpty()) {
            buildCells(); // the list with the new folder
          }
        });
      });
    });
    try {
      context.startActivityForResult(intent, REQUEST_STORAGE_FOLDER);
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
    }
  }

  private void xgramOfferUnpin (String path) {
    final int split = path.indexOf('\n');
    if (split == -1) {
      return;
    }
    final Uri tree = Uri.parse(path.substring(KEY_TREE.length(), split));
    showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.XgramFilesUnpin), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          com.fahadali.xgram.files.XgramStorageFolders.remove(context, tree);
          buildCells();
        }
        return true;
      });
  }

  private LoadOperation xgramBuildTree (final String path, final String parent) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        final int split = path.indexOf('\n');
        if (split == -1) {
          return null;
        }
        final Uri tree = Uri.parse(path.substring(KEY_TREE.length(), split));
        final String documentId = path.substring(split + 1);
        final java.util.List<com.fahadali.xgram.files.XgramStorageFolders.Entry> entries = com.fahadali.xgram.files.XgramStorageFolders.children(context, tree, documentId);
        if (entries == null) {
          openAlert(this, R.string.AppName, R.string.XgramFilesFolderUnavailable);
          return null;
        }
        final ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
        final String upper = parent != null && parent.startsWith(KEY_TREE) ? com.fahadali.xgram.files.XgramStorageFolders.folderName(context, tree, parent.substring(parent.indexOf('\n') + 1)) : null;
        items.add(createItem(createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", StringUtils.isEmpty(upper) ? Lang.getString(R.string.AttachFolderHome) : upper), R.id.btn_folder_upper));
        if (entries.isEmpty()) {
          openAlert(this, R.string.AppName, R.string.FolderEmpty);
          return null;
        }
        for (com.fahadali.xgram.files.XgramStorageFolders.Entry entry : entries) {
          if (entry.isFolder()) {
            items.add(createItem(createItem(context, tdlib, xgramTreePath(tree, entry.documentId), R.drawable.baseline_folder_24, entry.name, Lang.getString(R.string.Folder)), R.id.btn_folder));
          } else {
            final String size = entry.size >= 0 ? Strings.buildSize(entry.size) : "";
            items.add(createItem(createItem(context, tdlib, com.fahadali.xgram.files.XgramStorageFolders.documentUri(tree, entry.documentId).toString(), R.drawable.baseline_insert_drive_file_24, entry.name, size), R.id.btn_file));
          }
        }
        return new Result(items, true);
      }
    };
  }

  // XGram: SFTP servers (XgramSftp). A server folder lists like the phone's; a file is sent as one XGram downloads
  // when Telegram uploads it. The first connection shows the server's key fingerprint to trust; a changed key is refused

  private LoadOperation xgramBuildSftp (final String path) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        final int split = path.indexOf('\n');
        final String serverId = path.substring(KEY_SFTP.length(), split);
        final com.fahadali.xgram.files.XgramSftp.Server server = com.fahadali.xgram.files.XgramSftp.server(serverId);
        if (server == null) {
          openAlert(this, R.string.AppName, R.string.FolderDoesNotExist);
          return null;
        }
        final String requested = path.substring(split + 1);
        try {
          final String folder = requested.isEmpty() ? com.fahadali.xgram.files.XgramSftp.startFolder(server) : requested;
          final java.util.List<com.fahadali.xgram.files.XgramSftp.Entry> entries = com.fahadali.xgram.files.XgramSftp.list(server, folder);
          final ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
          items.add(createItem(createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", folder), R.id.btn_folder_upper));
          if (entries.isEmpty()) {
            openAlert(this, R.string.AppName, R.string.FolderEmpty);
            return null;
          }
          for (com.fahadali.xgram.files.XgramSftp.Entry entry : entries) {
            if (entry.folder) {
              items.add(createItem(createItem(context, tdlib, KEY_SFTP + server.id + "\n" + entry.path, R.drawable.baseline_folder_24, entry.name, Lang.getString(R.string.Folder)), R.id.btn_folder));
            } else {
              items.add(createItem(createItem(context, tdlib, com.fahadali.xgram.files.XgramSftp.path(server, entry), R.drawable.baseline_insert_drive_file_24, entry.name, Strings.buildSize(entry.size)), R.id.btn_file));
            }
          }
          return new Result(items, true);
        } catch (com.fahadali.xgram.files.XgramSftp.UnknownHostKeyException e) {
          final LoadOperation operation = this;
          UI.post(() -> {
            if (!isDestroyed() && currentLoadOperation == operation) {
              xgramAskTrust(server, e, () -> navigateInside(xgramLastView, path, xgramLastResult));
            }
          });
        } catch (com.fahadali.xgram.files.XgramSftp.ChangedHostKeyException e) {
          openAlert(this, R.string.AppName, R.string.XgramSftpKeyChanged);
        } catch (Throwable t) {
          android.util.Log.w("XgramSftp", "list", t);
          final String message = t.getMessage() != null ? t.getMessage() : "";
          openAlert(this, R.string.AppName, message.contains("Auth") ? R.string.XgramSftpSignInFailed : R.string.XgramSftpUnreachable);
        }
        return null;
      }
    };
  }

  private void xgramAskTrust (com.fahadali.xgram.files.XgramSftp.Server server, com.fahadali.xgram.files.XgramSftp.UnknownHostKeyException key, Runnable after) {
    showOptions(Lang.getString(R.string.XgramSftpTrust, server.address(), key.type, key.fingerprint),
      new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.XgramSftpTrustConfirm), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL}, new int[] {R.drawable.baseline_vpn_key_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          com.fahadali.xgram.files.XgramSftp.trust(server.id, key);
          after.run();
        }
        return true;
      });
  }

  private void xgramServerOptions (String serverId) {
    final com.fahadali.xgram.files.XgramSftp.Server server = com.fahadali.xgram.files.XgramSftp.server(serverId);
    if (server == null) {
      return;
    }
    showOptions(server.label() + "\n" + server.address(), new int[] {R.id.btn_edit, R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramSftpEdit), Lang.getString(R.string.XgramFilesUnpin), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_edit) {
          xgramEditServer(server);
        } else if (id == R.id.btn_delete) {
          xgramNetworkQueue.execute(() -> {
            com.fahadali.xgram.files.XgramSftp.remove(server.id); // closes its connection: not on the main thread
            UI.post(() -> {
              if (!isDestroyed() && stack.isEmpty()) {
                buildCells();
              }
            });
          });
        }
        return true;
      });
  }

  /** Adds ({@code server} null) or edits a server, then connects once: the key to trust, or what went wrong. */
  private void xgramEditServer (@Nullable com.fahadali.xgram.files.XgramSftp.Server server) {
    com.fahadali.xgram.compose.XgramSftpDialog.show(context, server, this::xgramPickKey, (name, host, port, user, folder, password, privateKey) ->
      xgramNetworkQueue.execute(() -> {
        final com.fahadali.xgram.files.XgramSftp.Server saved;
        try {
          saved = com.fahadali.xgram.files.XgramSftp.save(server != null ? server.id : null, name, host, port, user, folder, password, privateKey);
        } catch (IllegalStateException e) {
          UI.post(() -> UI.showToast(R.string.XgramSftpNotStored, Toast.LENGTH_LONG));
          return;
        }
        UI.post(() -> {
          if (!isDestroyed() && stack.isEmpty()) {
            buildCells();
          }
        });
        xgramTestServer(saved.id);
      })
    );
  }

  private void xgramTestServer (String serverId) {
    final com.fahadali.xgram.files.XgramSftp.Server server = com.fahadali.xgram.files.XgramSftp.server(serverId);
    if (server == null) {
      return;
    }
    try {
      com.fahadali.xgram.files.XgramSftp.startFolder(server);
      UI.post(() -> UI.showToast(Lang.getString(R.string.XgramSftpConnected, server.label()), Toast.LENGTH_SHORT));
    } catch (com.fahadali.xgram.files.XgramSftp.UnknownHostKeyException e) {
      UI.post(() -> {
        if (!isDestroyed()) {
          xgramAskTrust(server, e, () -> xgramNetworkQueue.execute(() -> xgramTestServer(serverId)));
        }
      });
    } catch (com.fahadali.xgram.files.XgramSftp.ChangedHostKeyException e) {
      UI.post(() -> UI.showToast(R.string.XgramSftpKeyChanged, Toast.LENGTH_LONG));
    } catch (Throwable t) {
      android.util.Log.w("XgramSftp", "test", t);
      final String message = t.getMessage() != null ? t.getMessage() : "";
      UI.post(() -> UI.showToast(message.contains("Auth") ? R.string.XgramSftpSignInFailed : R.string.XgramSftpUnreachable, Toast.LENGTH_LONG));
    }
  }

  /** A private key file picked with Android's picker, read as text (keys are small). */
  private void xgramPickKey (kotlin.jvm.functions.Function1<? super String, kotlin.Unit> onKey) {
    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
    context.putActivityResultHandler(REQUEST_SFTP_KEY, (requestCode, resultCode, data) -> {
      final Uri uri = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
      if (uri == null) {
        return;
      }
      Background.instance().post(() -> {
        String text = null;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
          if (in != null) {
            final byte[] buffer = new byte[64 * 1024];
            int total = 0, read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) > 0) {
              total += read;
            }
            text = new String(buffer, 0, total, java.nio.charset.StandardCharsets.UTF_8);
          }
        } catch (Throwable t) {
          android.util.Log.w("XgramSftp", "key", t);
        }
        final String key = text;
        UI.post(() -> {
          if (key != null && key.contains("PRIVATE KEY")) {
            onKey.invoke(key);
          } else {
            UI.showToast(R.string.XgramSftpKeyInvalid, Toast.LENGTH_LONG);
          }
        });
      });
    });
    try {
      context.startActivityForResult(intent, REQUEST_SFTP_KEY);
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
    }
  }

  public interface Delegate {
    boolean showRestriction (View view, @RightId int rightId);
    void onFilesSelected (ArrayList<InlineResult<?>> results, boolean needShowKeyboard);
  }
}

