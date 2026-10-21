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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5Chat;
import com.fahadali.xgram.appearance.XgramR6B5Pages;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.OverlayButtonWrap;
import org.thunderdog.challegram.navigation.SelectDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tgx.td.MessageId;

/**
 * XGram Drive: a folder of the drive (XgramDrive), folders first, then its files. A tap opens a folder or a file
 * (downloaded first), a long press has the item's actions (Select starts picking several); the button at the end
 * makes a folder or uploads files, photos and videos into the folder shown. The same page picks a folder for Move and
 * Save to XGram Drive (MODE_PICK: folders only, with "Move here" / "Save here").
 *
 * Telegram X's look: folders are setting rows, files Telegram X's file rows (InlineResultCommon: preview, download
 * state, open). Expressive design: Compose rows (TgxPagesDrive) with an XgramDriveThumbView in front.
 */
public final class XgramDriveController extends RecyclerViewController<XgramDriveController.Args> implements View.OnClickListener, View.OnLongClickListener, XgramDrive.Listener, OverlayButtonWrap.Callback, SelectDelegate {
  public static final int MODE_BROWSE = 0, MODE_PICK = 1;
  private static final int REQUEST_FILES = 10071, REQUEST_MEDIA = 10072;
  private static final int PICK_MEDIA_MAX = 100;
  /** The New button and its margin, kept clear under the last row. */
  private static final float FAB_ROOM = 88f;

  /** What a folder picker does with the folder it picks (Move, Save to XGram Drive). */
  public interface PickDelegate {
    /** The pick button ("Move here"). */
    String pickLabel ();

    /** Whether {@code folder} can be picked (not a folder that is moved, or one inside it). */
    boolean canPick (String folder);

    /** {@code folder} was picked (the pickers have closed). */
    void onPick (XgramDrive drive, String folder);
  }

  public static final class Args {
    final String folder;
    final int mode;
    final @Nullable PickDelegate pick;

    public Args (String folder) {
      this(folder, MODE_BROWSE, null);
    }

    Args (String folder, int mode, @Nullable PickDelegate pick) {
      this.folder = XgramDrive.normalize(folder);
      this.mode = mode;
      this.pick = pick;
    }
  }

  /** Where the last Save to XGram Drive went, per account (the next one starts there). */
  private static final Map<Integer, String> lastSaveFolder = new HashMap<>();

  private SettingsAdapter adapter;
  private XgramDrive drive;
  private @Nullable OverlayButtonWrap fab;
  private @Nullable String query;
  private boolean shown, viewCreated, rowsCompose;
  private final LinkedHashMap<Long, XgramDrive.Entry> selected = new LinkedHashMap<>();
  /** The latest state of the files shown (downloads, uploads), by file id. */
  private final Map<Integer, TdApi.File> liveFiles = new HashMap<>();
  private final Map<Integer, TdlibFilesManager.FileListener> watching = new HashMap<>();
  private final Set<Integer> openWhenLoaded = new HashSet<>();
  private final Set<Integer> refreshPosted = new HashSet<>();
  private final Set<XgramDriveThumbView> thumbs = new HashSet<>();
  /** Telegram X's file rows, kept across rebuilds (their download state stays), by post. */
  private final Map<Long, CachedResult> results = new HashMap<>();

  private static final class CachedResult {
    final XgramDrive.Entry entry;
    final InlineResult<?> result;

    CachedResult (XgramDrive.Entry entry, InlineResult<?> result) {
      this.entry = entry;
      this.result = result;
    }
  }

  public XgramDriveController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** XGram Drive at its top (the side menu): on the right pane with two panes, a push otherwise. */
  public static void open (@Nullable ViewController<?> context) {
    if (context == null) {
      return;
    }
    XgramDriveController c = new XgramDriveController(context.context(), context.tdlib());
    c.setArguments(new Args("/"));
    if (com.fahadali.xgram.layout.XgramTwoPane.replacesRightPane(context)) {
      com.fahadali.xgram.layout.XgramTwoPane.showOnRight(context, c);
      return;
    }
    NavigationController navigation = context.navigationController();
    if (navigation == null) {
      navigation = context.context().navigation();
    }
    if (navigation != null) {
      navigation.navigateTo(c);
    }
  }

  /** A folder picker starting at {@code folder}. */
  public static void pickFolder (ViewController<?> context, String folder, PickDelegate pick) {
    XgramDriveController c = new XgramDriveController(context.context(), context.tdlib());
    c.setArguments(new Args(folder, MODE_PICK, pick));
    context.navigateTo(c);
  }

  /** Whether a chat's post (all of an album) has something to save to the drive: not in secret chats or the drive itself. */
  public static boolean canSaveFromChat (Tdlib tdlib, long chatId, TdApi.Message[] messages) {
    if (!com.fahadali.xgram.XgramSettings.instance().driveTest() || tgx.td.ChatId.isSecret(chatId) || chatId == XgramDrive.of(tdlib).chatId()) {
      return false;
    }
    for (TdApi.Message message : messages) {
      if (XgramDrive.canSave(message)) {
        return true;
      }
    }
    return false;
  }

  /** A chat's post → Save to XGram Drive: pick a folder (the drive is set up first if needed), then copies go there. */
  public static void saveFromChat (ViewController<?> context, TdApi.Message[] messages) {
    final Tdlib tdlib = context.tdlib();
    final List<TdApi.Message> savable = new ArrayList<>();
    for (TdApi.Message message : messages) {
      if (XgramDrive.canSave(message)) {
        savable.add(message);
      }
    }
    if (savable.isEmpty()) {
      return;
    }
    final String start = lastSaveFolder.containsKey(tdlib.id()) ? lastSaveFolder.get(tdlib.id()) : "/";
    pickFolder(context, start, new PickDelegate() {
      @Override
      public String pickLabel () {
        return Lang.getString(R.string.XgramDriveSaveHere);
      }

      @Override
      public boolean canPick (String folder) {
        return true;
      }

      @Override
      public void onPick (XgramDrive drive, String folder) {
        lastSaveFolder.put(tdlib.id(), folder);
        final int[] pending = {savable.size()}, failed = {0};
        final TdApi.Error[] lastError = {null};
        for (TdApi.Message message : savable) {
          drive.saveFromChat(message, folder, (failedCount, error) -> {
            if (failedCount > 0) {
              failed[0]++;
              if (error != null) {
                lastError[0] = error;
              }
            }
            if (--pending[0] == 0) {
              onSaved(tdlib, folder, failed[0], lastError[0]);
            }
          });
        }
      }
    });
  }

  private static void onSaved (Tdlib tdlib, String folder, int failed, @Nullable TdApi.Error error) {
    if (failed > 0) {
      if (error != null) {
        UI.showError(error);
      } else {
        showNotice(Lang.getString(R.string.XgramDriveNotSaved));
      }
      return;
    }
    String text = Lang.getString(R.string.XgramDriveSaved, folderTitle(folder));
    if (!XgramSnackbar.show(text, Toast.LENGTH_LONG, Lang.getString(R.string.Open), () -> {
      ViewController<?> current = UI.getCurrentStackItem();
      if (current != null && current.tdlib() == tdlib) {
        openFolder(current, folder);
      }
    })) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  /** {@code folder} of the drive, pushed on top of the top folder's page when it is shown (Open after Save). */
  private static void openFolder (ViewController<?> context, String folder) {
    XgramDriveController c = new XgramDriveController(context.context(), context.tdlib());
    c.setArguments(new Args(folder));
    context.navigateTo(c);
  }

  /** "XGram Drive" for the top, the folder's name otherwise. */
  static String folderTitle (String folder) {
    return "/".equals(folder) ? Lang.getString(R.string.XgramDrive) : XgramDrive.nameOf(folder);
  }

  private static void showNotice (CharSequence text) {
    if (!XgramSnackbar.show(text, Toast.LENGTH_LONG)) {
      UI.showToast(text, Toast.LENGTH_LONG);
    }
  }

  /**
   * Whether the page is drawn in Compose (Expressive design with "New look for Telegram X pages"): its rows are then
   * setting rows the hints draw (TgxPagesDrive); Telegram X's file rows otherwise, also with Expressive design.
   */
  private boolean composeRows () {
    if (viewCreated) {
      return xgramComposeRenderer() != null;
    }
    return XgramExpressive.isEnabled() && com.fahadali.xgram.XgramSettings.instance().composeTgxPages();
  }

  private Args args () {
    Args args = getArguments();
    return args != null ? args : new Args("/");
  }

  private String folder () {
    return args().folder;
  }

  private boolean isPicker () {
    return args().mode == MODE_PICK;
  }

  @Override
  public int getId () {
    return isPicker() ? R.id.controller_xgramDrivePick : R.id.controller_xgramDrive;
  }

  @Override
  public CharSequence getName () {
    if (isPicker() && "/".equals(folder())) {
      return Lang.getString(R.string.XgramDriveChooseFolder);
    }
    return folderTitle(folder());
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    drive = XgramDrive.of(tdlib);
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof XgramDrive.Folder) {
          XgramDrive.Folder folder = (XgramDrive.Folder) data;
          view.setName(folder.name());
          view.setData(xgramFolderSubtitle(folder));
        } else if (data instanceof XgramDrive.Entry) {
          XgramDrive.Entry entry = (XgramDrive.Entry) data;
          view.setName(entry.name);
          view.setData(xgramSubtitleOf(entry));
        }
      }
    };
    adapter.setOnLongClickListener(this);
    drive.addListener(this);
    rebuild();
    recyclerView.setAdapter(adapter);
    drive.start();
  }

  @Override
  protected View onCreateView (Context context) {
    View view = super.onCreateView(context);
    viewCreated = true;
    if (rowsCompose != composeRows()) {
      rebuild(); // the Compose list could not be made: Telegram X's rows
    }
    if (!isPicker() && view instanceof FrameLayout) {
      // The New button: a folder, photos and videos, files (nearest the button)
      fab = new OverlayButtonWrap(context);
      fab.initWithList(this,
        ColorId.circleButtonActive,
        ColorId.circleButtonActiveIcon,
        new int[] {R.id.btn_xgramDriveFab, R.id.btn_xgramDriveNewFolder, R.id.btn_xgramDriveUploadMedia, R.id.btn_xgramDriveUploadFiles},
        new int[] {R.drawable.baseline_add_24, R.drawable.baseline_create_new_folder_24, R.drawable.baseline_image_24, R.drawable.baseline_insert_drive_file_24},
        new int[] {ColorId.circleButtonRegular, ColorId.circleButtonNewGroup, ColorId.circleButtonNewChannel, ColorId.circleButtonNewChat},
        new int[] {ColorId.circleButtonRegularIcon, ColorId.circleButtonNewGroupIcon, ColorId.circleButtonNewChannelIcon, ColorId.circleButtonNewChatIcon},
        new int[] {R.string.XgramDriveNewFolder, R.string.XgramDriveUploadMedia, R.string.XgramDriveUploadFiles},
        false);
      fab.setCallback(this);
      if (XgramExpressive.isEnabled()) {
        fab.xgramSetExtendedLabel(Lang.getString(R.string.XgramDriveNew)); // an extended FAB: "+ New"
      }
      ((FrameLayout) view).addView(fab);
      fab.setPadding(0, 0, 0, extraBottomInsetWithoutIme);
      com.fahadali.xgram.compose.tgx.TgxSettingsRenderer renderer = xgramComposeRenderer();
      if (renderer != null) {
        renderer.setBottomInset(extraBottomInset + Screen.dp(FAB_ROOM));
      }
      updateFab();
    }
    return view;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    if (fab != null) {
      fab.setPadding(0, 0, 0, extraBottomInsetWithoutIme);
      com.fahadali.xgram.compose.tgx.TgxSettingsRenderer renderer = xgramComposeRenderer();
      if (renderer != null) {
        renderer.setBottomInset(extraBottomInset + Screen.dp(FAB_ROOM));
      }
    }
  }

  private void updateFab () {
    if (fab == null) {
      return;
    }
    boolean show = drive != null && drive.state() == XgramDrive.STATE_READY && !inSelectMode() && !inSearchMode();
    if (show) {
      fab.show();
    } else {
      fab.hide();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (!shown && drive != null) {
      shown = true;
      drive.onScreenShown();
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (fab != null) {
      fab.close();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (drive != null) {
      drive.removeListener(this);
      if (shown) {
        shown = false;
        drive.onScreenHidden();
      }
    }
    for (Map.Entry<Integer, TdlibFilesManager.FileListener> watch : watching.entrySet()) {
      tdlib.files().unsubscribe(watch.getKey(), watch.getValue());
    }
    watching.clear();
    for (XgramDriveThumbView thumb : new ArrayList<>(thumbs)) {
      thumb.destroy();
    }
    thumbs.clear();
    if (fab != null) {
      fab.performDestroy();
    }
  }

  @Override
  public void onDriveChanged () {
    if (isDestroyed()) {
      return;
    }
    // Posts that went away leave the selection
    if (!selected.isEmpty()) {
      boolean changed = false;
      for (Long id : new ArrayList<>(selected.keySet())) {
        XgramDrive.Entry entry = drive.entry(id);
        if (entry == null) {
          selected.remove(id);
          changed = true;
        } else {
          selected.put(id, entry);
        }
      }
      if (changed) {
        syncSelectMode();
      }
    }
    rebuild();
    updateFab();
  }

  // Rows

  private void rebuild () {
    if (adapter == null || drive == null) {
      return;
    }
    final boolean m3 = XgramExpressive.isEnabled();
    final boolean compose = rowsCompose = composeRows();
    final String folder = folder();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    switch (drive.state()) {
      case XgramDrive.STATE_NONE: {
        addIntro(items, m3);
        break;
      }
      case XgramDrive.STATE_ERROR: {
        TdApi.Error error = drive.error();
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.XgramDriveError, error != null ? TD.toErrorString(error) : "?"), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_xgramDriveRetry, 0, R.string.XgramDriveRetry));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case XgramDrive.STATE_READY: {
        if (query != null) {
          addSearchResults(items, compose);
        } else if (isPicker()) {
          addPicker(items, folder, m3);
        } else {
          addFolder(items, folder, m3, compose);
        }
        break;
      }
      default: {
        // Finding, setting up or loading the drive
        items.add(new ListItem(ListItem.TYPE_PROGRESS));
        int text = drive.state() == XgramDrive.STATE_CREATING ? R.string.XgramDriveCreating : drive.state() == XgramDrive.STATE_LOADING ? R.string.XgramDriveLoading : R.string.XgramDriveFinding;
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, 0, 0, text));
        break;
      }
    }
    adapter.setItems(items, false);
    refreshThumbs();
  }

  private void addIntro (List<ListItem> items, boolean m3) {
    String limit = Strings.buildSize((tdlib.hasPremium() ? 4000L : 2000L) << 20);
    if (m3) {
      items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_cloud_24, R.string.XgramDriveIntroText));
    } else {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramDriveIntroText));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_xgramDriveCreate, 0, R.string.XgramDriveCreate));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.XgramDriveIntroNote, limit), false));
  }

  private void addFolder (List<ListItem> items, String folder, boolean m3, boolean compose) {
    List<XgramDrive.Folder> folders = drive.foldersIn(folder);
    List<XgramDrive.Entry> files = drive.filesIn(folder, XgramDrive.sortOrder());
    if (folders.isEmpty() && files.isEmpty()) {
      if (m3) {
        items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_folder_open_96, R.string.XgramDriveEmpty));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, 0, 0, R.string.XgramDriveEmpty));
      }
    } else {
      addFolderRows(items, folders);
      addFileRows(items, files, compose, R.string.XgramDriveFiles);
    }
    if ("/".equals(folder)) {
      String summary = Lang.getString(R.string.XgramDriveSummary, Lang.plural(R.string.xFiles, drive.fileCount()), Strings.buildSize(drive.totalSize()));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, summary, false));
    }
    if (!compose) {
      // Room for the New button over the last row (in Compose: the list's bottom inset)
      items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(FAB_ROOM)));
    }
  }

  private void addPicker (List<ListItem> items, String folder, boolean m3) {
    PickDelegate pick = args().pick;
    if (pick != null && pick.canPick(folder)) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_xgramDrivePickHere, 0, pick.pickLabel(), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.XgramDrivePickHint, folderTitle(folder)), false));
    } else {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramDriveCantPick));
    }
    List<XgramDrive.Folder> folders = drive.foldersIn(folder);
    if (folders.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramDriveNoFolders));
    } else {
      addFolderRows(items, folders);
    }
  }

  private void addSearchResults (List<ListItem> items, boolean compose) {
    List<XgramDrive.Folder> folders = drive.searchFolders(query);
    List<XgramDrive.Entry> files = drive.searchFiles(query, XgramDrive.sortOrder());
    if (folders.isEmpty() && files.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, 0, 0, query.trim().isEmpty() ? R.string.XgramDriveSearchHint : R.string.XgramDriveNoResults));
      return;
    }
    addFolderRows(items, folders);
    addFileRows(items, files, compose, R.string.XgramDriveFiles);
  }

  private void addFolderRows (List<ListItem> items, List<XgramDrive.Folder> folders) {
    if (folders.isEmpty()) {
      return;
    }
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramDriveFolders));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (XgramDrive.Folder folder : folders) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      first = false;
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramDriveFolder, R.drawable.baseline_folder_24, folder.name(), false).setData(folder));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  private void addFileRows (List<ListItem> items, List<XgramDrive.Entry> files, boolean compose, int header) {
    if (files.isEmpty()) {
      return;
    }
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, header));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    Set<Long> shownIds = new HashSet<>();
    for (XgramDrive.Entry entry : files) {
      shownIds.add(entry.id());
      watch(entry.file());
      if (compose) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramDriveFile, XgramDriveThumbView.iconOf(entry), entry.name, false).setData(entry).setLongId(entry.id()).setSelected(selected.containsKey(entry.id())));
      } else {
        InlineResult<?> result = resultOf(entry);
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_xgramDriveFile).setData(result).setLongId(entry.id()).setSelected(selected.containsKey(entry.id())));
      }
      first = false;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    results.keySet().retainAll(shownIds);
  }

  /** Telegram X's row for a file, under its name in the drive (music and voice play in the app when tapped). */
  private InlineResult<?> resultOf (XgramDrive.Entry entry) {
    CachedResult cached = results.get(entry.id());
    if (cached != null && cached.entry.name.equals(entry.name) && cached.entry.message == entry.message && cached.entry.message.content == entry.message.content) {
      return cached.result;
    }
    InlineResultCommon result = new InlineResultCommon(context(), tdlib, entry.message, entry.document);
    result.setMessage(entry.message);
    result.setQueryId(entry.id());
    result.setDate(entry.message.date);
    if (XgramExpressive.isEnabled()) {
      result.setXgramListInset(Screen.dp(XgramExpressive.LIST_INSET)); // the row is a tile inset 16dp, as Shared media's
    }
    results.put(entry.id(), new CachedResult(entry, result));
    return result;
  }

  /** "3 files · 20 MB"; an empty folder: "Empty". */
  public String xgramFolderSubtitle (XgramDrive.Folder folder) {
    if (folder.files == 0) {
      return Lang.getString(R.string.XgramDriveFolderEmpty);
    }
    return Lang.plural(R.string.xFiles, folder.files) + " · " + Strings.buildSize(folder.size);
  }

  /** "1.2 MB · 22 Oct" (in a search, the folder instead of the date); uploads say how they are going. */
  public String xgramSubtitleOf (XgramDrive.Entry entry) {
    if (entry.isFailed()) {
      return Lang.getString(R.string.XgramDriveUploadFailed);
    }
    String size = Strings.buildSize(entry.size());
    if (entry.isSending()) {
      return Lang.getString(R.string.XgramDriveUploading, size);
    }
    if (query != null) {
      return size + " · " + ("/".equals(entry.folder) ? Lang.getString(R.string.XgramDrive) : entry.folder.substring(1));
    }
    String date = XgramR6B5Pages.shortDate(entry.message.date);
    return date.isEmpty() ? size : size + " · " + date;
  }

  /** A download or an upload of the row's file: 0..1 (NaN: size unknown); -1: none. */
  public float xgramProgressOf (XgramDrive.Entry entry) {
    TdApi.File file = liveFile(entry);
    if (file == null) {
      return -1f;
    }
    long size = Math.max(file.size, file.expectedSize);
    if (entry.isSending() || file.remote.isUploadingActive) {
      return size > 0 ? Math.min(1f, file.remote.uploadedSize / (float) size) : Float.NaN;
    }
    if (file.local.isDownloadingActive) {
      return size > 0 ? Math.min(1f, file.local.downloadedSize / (float) size) : Float.NaN;
    }
    return -1f;
  }

  public boolean xgramIsSelected (XgramDrive.Entry entry) {
    return selected.containsKey(entry.id());
  }

  void xgramRegisterThumb (XgramDriveThumbView view) {
    thumbs.add(view);
  }

  void xgramUnregisterThumb (XgramDriveThumbView view) {
    thumbs.remove(view);
  }

  private void refreshThumbs () {
    for (XgramDriveThumbView thumb : thumbs) {
      thumb.refresh();
    }
  }

  // Files' progress

  private @Nullable TdApi.File liveFile (XgramDrive.Entry entry) {
    TdApi.File file = entry.file();
    if (file == null) {
      return null;
    }
    TdApi.File live = liveFiles.get(file.id);
    return live != null ? live : file;
  }

  private void watch (@Nullable TdApi.File file) {
    if (file == null || watching.containsKey(file.id)) {
      return;
    }
    final int fileId = file.id;
    TdlibFilesManager.FileListener listener = new TdlibFilesManager.FileListener() {
      @Override
      public void onFileLoadProgress (TdApi.File file) {
        runOnUiThreadOptional(() -> onFileChanged(file));
      }

      @Override
      public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
        runOnUiThreadOptional(() -> {
          if (downloadedFile != null) {
            onFileChanged(downloadedFile);
          }
          if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED && downloadedFile != null && TD.isFileLoaded(downloadedFile)) {
            if (openWhenLoaded.remove(fileId)) {
              XgramDrive.Entry entry = entryOfFile(fileId);
              if (entry != null) {
                openLoaded(entry, downloadedFile);
              }
            }
          } else if (state == TdlibFilesManager.STATE_FAILED || state == TdlibFilesManager.STATE_PAUSED) {
            openWhenLoaded.remove(fileId);
          }
          postRefresh(fileId);
        });
      }
    };
    watching.put(fileId, listener);
    tdlib.files().subscribe(file, listener);
  }

  private void onFileChanged (TdApi.File file) {
    liveFiles.put(file.id, file);
    postRefresh(file.id);
  }

  private @Nullable XgramDrive.Entry entryOfFile (int fileId) {
    for (XgramDrive.Entry entry : drive.filesIn(folder(), XgramDrive.SORT_NAME)) {
      TdApi.File file = entry.file();
      if (file != null && file.id == fileId) {
        return entry;
      }
    }
    for (XgramDrive.Entry entry : selected.values()) {
      TdApi.File file = entry.file();
      if (file != null && file.id == fileId) {
        return entry;
      }
    }
    if (query != null) {
      for (XgramDrive.Entry entry : drive.searchFiles(query, XgramDrive.SORT_NAME)) {
        TdApi.File file = entry.file();
        if (file != null && file.id == fileId) {
          return entry;
        }
      }
    }
    return null;
  }

  /** The rows of a file get its progress once per frame. */
  private void postRefresh (int fileId) {
    if (!refreshPosted.add(fileId)) {
      return;
    }
    runOnUiThreadOptional(() -> {
      refreshPosted.remove(fileId);
      if (adapter == null) {
        return;
      }
      List<ListItem> items = adapter.getItems();
      for (int i = 0; i < items.size(); i++) {
        ListItem item = items.get(i);
        Object data = item.getData();
        if (data instanceof XgramDrive.Entry) {
          TdApi.File file = ((XgramDrive.Entry) data).file();
          if (file != null && file.id == fileId) {
            adapter.notifyItemChanged(i); // the Compose list reads the row again (its ring); Telegram X's rows draw their own
          }
        }
      }
    });
  }

  // Taps

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_xgramDriveCreate) {
      drive.create((failed, error) -> {
        if (error != null) {
          UI.showError(error);
        }
      });
      return;
    }
    if (id == R.id.btn_xgramDriveRetry) {
      drive.refresh();
      return;
    }
    if (id == R.id.btn_xgramDrivePickHere) {
      finishPick();
      return;
    }
    Object tag = v.getTag();
    ListItem item = tag instanceof ListItem ? (ListItem) tag : null;
    if (item == null) {
      return;
    }
    Object data = item.getData();
    if (data instanceof XgramDrive.Folder) {
      if (!selected.isEmpty()) {
        return; // picking files: folders stay
      }
      openSubfolder(((XgramDrive.Folder) data).path);
      return;
    }
    XgramDrive.Entry entry = entryOf(item);
    if (entry == null) {
      return;
    }
    if (!selected.isEmpty()) {
      toggleSelected(entry, item);
      return;
    }
    if (entry.isSending() || entry.isFailed()) {
      showUploadMenu(v, entry);
      return;
    }
    int contentType = entry.message.content.getConstructor();
    if (data instanceof InlineResultCommon && contentType != TdApi.MessageAudio.CONSTRUCTOR && contentType != TdApi.MessageVoiceNote.CONSTRUCTOR) {
      ((InlineResultCommon) data).performClick(v); // Telegram X's own: download, then open (photos and videos in the viewer)
      return;
    }
    openEntry(entry); // music and voice in the player; in Compose, everything
  }

  private @Nullable XgramDrive.Entry entryOf (ListItem item) {
    Object data = item.getData();
    if (data instanceof XgramDrive.Entry) {
      return (XgramDrive.Entry) data;
    }
    if (item.getId() == R.id.btn_xgramDriveFile && item.getLongId() != 0) {
      return drive.entry(item.getLongId());
    }
    return null;
  }

  private void openSubfolder (String path) {
    XgramDriveController c = new XgramDriveController(context(), tdlib);
    c.setArguments(new Args(path, args().mode, args().pick));
    navigateTo(c);
  }

  /** Opens a file as a chat would: music and voice in the player, photos and videos in the viewer, others in an app. */
  private void openEntry (XgramDrive.Entry entry) {
    int type = entry.message.content.getConstructor();
    if (type == TdApi.MessageAudio.CONSTRUCTOR || type == TdApi.MessageVoiceNote.CONSTRUCTOR) {
      tdlib.context().player().playPauseMessage(tdlib, entry.message, null);
      return;
    }
    TdApi.File file = liveFile(entry);
    if (file == null) {
      return;
    }
    if (TD.isFileLoaded(file)) {
      openLoaded(entry, file);
      return;
    }
    if (file.local.isDownloadingActive || openWhenLoaded.contains(file.id)) {
      // A second tap stops the download
      openWhenLoaded.remove(file.id);
      tdlib.files().cancelDownloadOrUploadFile(file.id, false, true);
      return;
    }
    openWhenLoaded.add(file.id);
    watch(file);
    tdlib.files().downloadFile(file, com.fahadali.xgram.XgramDownloadBoost.tappedPriority(), null);
    postRefresh(file.id);
  }

  private void openLoaded (XgramDrive.Entry entry, TdApi.File file) {
    final String mimeType = entry.mimeType();
    final TdApi.Document document = entry.document != null ? new TdApi.Document(entry.name, entry.document.mimeType, entry.document.minithumbnail, entry.document.thumbnail, file) : null;
    if (document != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
      Background.instance().post(() -> {
        MediaItem item = MediaItem.valueOf(context(), tdlib, document, null);
        runOnUiThreadOptional(() -> {
          if (item != null) {
            item.setSourceMessageId(entry.message.chatId, entry.id());
            MediaViewController.openFromMedia(this, item, null, true);
          } else {
            U.openFile(this, entry.name, new File(file.local.path), mimeType, 0);
          }
        });
      });
      return;
    }
    U.openFile(this, entry.name, new File(file.local.path), mimeType, 0);
  }

  // Long presses and menus

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    ListItem item = tag instanceof ListItem ? (ListItem) tag : null;
    if (item == null) {
      return false;
    }
    if (item.getData() instanceof XgramDrive.Folder) {
      if (isPicker() || !selected.isEmpty()) {
        return false;
      }
      showFolderMenu(v, (XgramDrive.Folder) item.getData());
      return true;
    }
    XgramDrive.Entry entry = entryOf(item);
    if (entry == null || isPicker()) {
      return false;
    }
    if (!selected.isEmpty()) {
      toggleSelected(entry, item);
      return true;
    }
    if (entry.isSending() || entry.isFailed()) {
      showUploadMenu(v, entry);
      return true;
    }
    showFileMenu(v, entry, item);
    return true;
  }

  /** One menu for both looks: Expressive design's M3 menu at the row, Telegram X's options sheet otherwise. */
  private static final class Action {
    final int id, icon;
    final String title;
    final boolean destructive;
    final Runnable run;

    Action (int id, int icon, int title, boolean destructive, Runnable run) {
      this.id = id;
      this.icon = icon;
      this.title = Lang.getString(title);
      this.destructive = destructive;
      this.run = run;
    }
  }

  private void showActions (View anchor, @Nullable CharSequence title, List<Action> actions) {
    if (XgramExpressive.isEnabled()) {
      List<XgramR5Chat.MenuEntry> entries = new ArrayList<>(actions.size());
      for (Action action : actions) {
        XgramR5Chat.MenuEntry entry = new XgramR5Chat.MenuEntry(action.id, action.title, action.icon, () -> {
          if (!isDestroyed()) {
            action.run.run();
          }
        });
        entries.add(action.destructive ? entry.destructive() : entry);
      }
      XgramR5Chat.showMenu(anchor, getThemeListeners(), entries);
      return;
    }
    int[] ids = new int[actions.size()];
    String[] titles = new String[actions.size()];
    int[] colors = new int[actions.size()];
    int[] icons = new int[actions.size()];
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      ids[i] = action.id;
      titles[i] = action.title;
      colors[i] = action.destructive ? OptionColor.RED : OptionColor.NORMAL;
      icons[i] = action.icon;
    }
    showOptions(title, ids, titles, colors, icons, (itemView, id) -> {
      for (Action action : actions) {
        if (action.id == id) {
          action.run.run();
          break;
        }
      }
      return true;
    });
  }

  private void showFileMenu (View anchor, XgramDrive.Entry entry, ListItem item) {
    List<Action> actions = new ArrayList<>();
    actions.add(new Action(R.id.btn_xgramDriveShare, R.drawable.baseline_forward_24, R.string.Share, false, () -> share(Collections.singletonList(entry))));
    actions.add(new Action(R.id.btn_xgramDriveDownload, R.drawable.baseline_file_download_24, R.string.SaveToDownloads, false, () -> saveToDownloads(Collections.singletonList(entry))));
    actions.add(new Action(R.id.btn_xgramDriveRename, R.drawable.baseline_edit_24, R.string.XgramDriveRename, false, () -> renameFile(entry)));
    actions.add(new Action(R.id.btn_xgramDriveMove, R.drawable.baseline_drive_file_move_24, R.string.XgramDriveMove, false, () -> moveFiles(Collections.singletonList(entry))));
    if (!inSearchMode()) {
      actions.add(new Action(R.id.btn_xgramDriveSelect, R.drawable.baseline_check_circle_24, R.string.Select, false, () -> toggleSelected(entry, item)));
    }
    actions.add(new Action(R.id.btn_xgramDriveInfo, R.drawable.baseline_info_24, R.string.XgramDriveDetails, false, () -> showDetails(entry)));
    actions.add(new Action(R.id.btn_xgramDriveShowInChat, R.drawable.baseline_visibility_24, R.string.ShowInChat, false, () -> showInChannel(entry)));
    actions.add(new Action(R.id.btn_xgramDriveDelete, R.drawable.baseline_delete_24, R.string.Delete, true, () -> confirmDelete(Collections.singletonList(entry), Collections.emptyList())));
    showActions(anchor, entry.name, actions);
  }

  private void showFolderMenu (View anchor, XgramDrive.Folder folder) {
    List<Action> actions = new ArrayList<>();
    actions.add(new Action(R.id.btn_xgramDriveRename, R.drawable.baseline_edit_24, R.string.XgramDriveRename, false, () -> renameFolder(folder)));
    actions.add(new Action(R.id.btn_xgramDriveMove, R.drawable.baseline_drive_file_move_24, R.string.XgramDriveMove, false, () -> moveFolder(folder)));
    actions.add(new Action(R.id.btn_xgramDriveDelete, R.drawable.baseline_delete_24, R.string.Delete, true, () -> confirmDelete(Collections.emptyList(), Collections.singletonList(folder.path))));
    showActions(anchor, folder.name(), actions);
  }

  private void showUploadMenu (View anchor, XgramDrive.Entry entry) {
    List<Action> actions = new ArrayList<>();
    if (entry.isFailed()) {
      actions.add(new Action(R.id.btn_xgramDriveRetry, R.drawable.baseline_sync_24, R.string.XgramDriveUploadAgain, false, () -> drive.retry(entry)));
      actions.add(new Action(R.id.btn_xgramDriveCancel, R.drawable.baseline_delete_24, R.string.XgramDriveRemove, true, () -> drive.delete(Collections.singletonList(entry), Collections.emptyList(), (failed, error) -> { })));
    } else {
      actions.add(new Action(R.id.btn_xgramDriveCancel, R.drawable.baseline_cancel_24, R.string.XgramDriveCancelUpload, true, () -> drive.delete(Collections.singletonList(entry), Collections.emptyList(), (failed, error) -> { })));
    }
    showActions(anchor, entry.name, actions);
  }

  // Actions

  private XgramDrive.Done reportTo (@Nullable String success) {
    return (failed, error) -> {
      if (isDestroyed()) {
        return;
      }
      if (failed > 0) {
        if (error != null && XgramDrive.ERROR_PATH_TOO_LONG.equals(error.message)) {
          showNotice(Lang.getString(R.string.XgramDrivePathTooLong));
        } else if (error != null) {
          UI.showError(error);
        } else {
          showNotice(Lang.getString(R.string.XgramDriveNotDone, Lang.plural(R.string.xFiles, failed)));
        }
      } else if (success != null) {
        showNotice(success);
      }
    };
  }

  private void share (List<XgramDrive.Entry> entries) {
    List<TdApi.Message> messages = new ArrayList<>();
    for (XgramDrive.Entry entry : entries) {
      if (!entry.isSending() && !entry.isFailed()) {
        messages.add(entry.message);
      }
    }
    if (messages.isEmpty()) {
      return;
    }
    ShareController c = new ShareController(context(), tdlib);
    c.setArguments(new ShareController.Args(messages.toArray(new TdApi.Message[0])).xgramSetSendAsCopy(true));
    c.show();
  }

  private void saveToDownloads (List<XgramDrive.Entry> entries) {
    boolean downloading = false;
    for (XgramDrive.Entry entry : entries) {
      TdApi.File file = liveFile(entry);
      if (file != null && !TD.isFileLoaded(file)) {
        downloading = true;
      }
      drive.saveToDownloads(entry);
    }
    if (downloading) {
      showNotice(Lang.getString(R.string.XgramDriveSaving));
    }
  }

  private void renameFile (XgramDrive.Entry entry) {
    openInputAlert(Lang.getString(R.string.XgramDriveRename), Lang.getString(R.string.XgramDriveFileName), R.string.Save, R.string.Cancel, entry.name, (inputView, result) -> {
      String name = XgramDrive.cleanName(result);
      if (name == null || result.contains("/")) {
        showNotice(Lang.getString(R.string.XgramDriveBadName));
        return false;
      }
      if (!name.equalsIgnoreCase(entry.name) && drive.fileNameTaken(entry.folder, name, entry.id())) {
        showNotice(Lang.getString(R.string.XgramDriveFileExists, name));
        return false;
      }
      drive.renameFile(entry, name, reportTo(null));
      return true;
    }, true);
  }

  private void renameFolder (XgramDrive.Folder folder) {
    openInputAlert(Lang.getString(R.string.XgramDriveRename), Lang.getString(R.string.XgramDriveFolderName), R.string.Save, R.string.Cancel, folder.name(), (inputView, result) -> {
      String name = XgramDrive.cleanName(result);
      if (name == null || result.contains("/")) {
        showNotice(Lang.getString(R.string.XgramDriveBadName));
        return false;
      }
      if (!name.equalsIgnoreCase(folder.name()) && drive.folderExists(XgramDrive.join(XgramDrive.parentOf(folder.path), name))) {
        showNotice(Lang.getString(R.string.XgramDriveFolderExists, name));
        return false;
      }
      drive.renameFolder(folder.path, name, reportTo(null));
      return true;
    }, true);
  }

  private void newFolder () {
    final String parent = folder();
    openInputAlert(Lang.getString(R.string.XgramDriveNewFolder), Lang.getString(R.string.XgramDriveFolderName), R.string.XgramDriveCreateFolder, R.string.Cancel, null, (inputView, result) -> {
      String name = XgramDrive.cleanName(result);
      if (name == null || result.contains("/")) {
        showNotice(Lang.getString(R.string.XgramDriveBadName));
        return false;
      }
      if (drive.folderExists(XgramDrive.join(parent, name))) {
        showNotice(Lang.getString(R.string.XgramDriveFolderExists, name));
        return false;
      }
      drive.createFolder(parent, name, reportTo(null));
      return true;
    }, true);
  }

  private void moveFiles (List<XgramDrive.Entry> files) {
    final List<XgramDrive.Entry> moving = new ArrayList<>(files);
    clearSelection();
    pickFolder(this, folder(), new PickDelegate() {
      @Override
      public String pickLabel () {
        return Lang.getString(R.string.XgramDriveMoveHere);
      }

      @Override
      public boolean canPick (String folder) {
        return true;
      }

      @Override
      public void onPick (XgramDrive drive, String folder) {
        drive.moveFiles(moving, folder, reportTo(Lang.getString(R.string.XgramDriveMoved, folderTitle(folder))));
      }
    });
  }

  private void moveFolder (XgramDrive.Folder moved) {
    final String path = moved.path;
    pickFolder(this, XgramDrive.parentOf(path), new PickDelegate() {
      @Override
      public String pickLabel () {
        return Lang.getString(R.string.XgramDriveMoveHere);
      }

      @Override
      public boolean canPick (String folder) {
        return !folder.equals(path) && !XgramDrive.isInside(folder, path) && !folder.equals(XgramDrive.parentOf(path)) && !drive.folderExists(XgramDrive.join(folder, XgramDrive.nameOf(path)));
      }

      @Override
      public void onPick (XgramDrive drive, String folder) {
        drive.moveFolder(path, folder, reportTo(Lang.getString(R.string.XgramDriveMoved, folderTitle(folder))));
      }
    });
  }

  private void confirmDelete (List<XgramDrive.Entry> files, List<String> folders) {
    final String text;
    if (folders.isEmpty()) {
      text = files.size() == 1 ? Lang.getString(R.string.XgramDriveDeleteFile, files.get(0).name) : Lang.getString(R.string.XgramDriveDeleteFiles, Lang.plural(R.string.xFiles, files.size()));
    } else {
      int count = 0;
      for (String folder : folders) {
        for (XgramDrive.Entry entry : drive.entriesUnder(folder)) {
          if (!entry.isFolder) {
            count++;
          }
        }
      }
      text = Lang.getString(R.string.XgramDriveDeleteFolder, XgramDrive.nameOf(folders.get(0)), Lang.plural(R.string.xFiles, count));
    }
    showOptions(text, new int[] {R.id.btn_xgramDriveDelete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_xgramDriveDelete) {
        drive.delete(files, folders, reportTo(null));
        clearSelection();
      }
      return true;
    });
  }

  private void showDetails (XgramDrive.Entry entry) {
    String folder = "/".equals(entry.folder) ? Lang.getString(R.string.XgramDrive) : entry.folder.substring(1);
    String added = Lang.getRelativeTimestamp(entry.message.date, java.util.concurrent.TimeUnit.SECONDS);
    String text = Lang.getString(R.string.XgramDriveDetailsText, folder, Strings.buildSize(entry.size()), entry.mimeType(), added);
    openAlert(R.string.XgramDriveDetails, entry.name + "\n\n" + text);
  }

  private void showInChannel (XgramDrive.Entry entry) {
    long chatId = entry.message.chatId;
    tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().highlightMessage(new MessageId(chatId, entry.id())).ensureHighlightAvailable());
  }

  // Upload

  @Override
  public boolean onOverlayButtonClick (int id, View view) {
    if (id == R.id.btn_xgramDriveFab) {
      NavigationController navigation = navigationController();
      if (fab != null && (navigation == null || !navigation.isAnimating())) {
        fab.toggle();
      }
      return false;
    }
    if (id == R.id.btn_xgramDriveUploadFiles) {
      pickFiles(false);
    } else if (id == R.id.btn_xgramDriveUploadMedia) {
      pickFiles(true);
    } else if (id == R.id.btn_xgramDriveNewFolder) {
      newFolder();
    }
    return true;
  }

  private void pickFiles (boolean media) {
    final int request = media ? REQUEST_MEDIA : REQUEST_FILES;
    final Intent intent;
    if (media && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
      intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, PICK_MEDIA_MAX);
    } else {
      intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("*/*");
      if (media) {
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
      }
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }
    final String target = folder();
    context().putActivityResultHandler(request, (requestCode, resultCode, data) -> {
      if (resultCode != Activity.RESULT_OK || data == null || isDestroyed()) {
        return;
      }
      LinkedHashSet<Uri> uris = new LinkedHashSet<>();
      if (data.getData() != null) {
        uris.add(data.getData());
      }
      ClipData clip = data.getClipData();
      if (clip != null) {
        for (int i = 0; i < clip.getItemCount(); i++) {
          Uri uri = clip.getItemAt(i).getUri();
          if (uri != null) {
            uris.add(uri);
          }
        }
      }
      if (!uris.isEmpty()) {
        drive.upload(new ArrayList<>(uris), target, XgramDriveController::showNotice);
      }
    });
    try {
      context().startActivityForResult(intent, request);
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
    }
  }

  // Picker

  private void finishPick () {
    final PickDelegate pick = args().pick;
    final String target = folder();
    if (pick == null || !pick.canPick(target)) {
      return;
    }
    NavigationController navigation = navigationController();
    if (navigation != null) {
      navigation.getStack().destroyAllById(R.id.controller_xgramDrivePick);
      navigation.navigateBack();
    }
    pick.onPick(drive, target);
  }

  // Selection

  private void toggleSelected (XgramDrive.Entry entry, @Nullable ListItem item) {
    if (isPicker() || entry.isSending() || (selected.isEmpty() && inSearchMode())) {
      return;
    }
    boolean isSelected;
    if (selected.remove(entry.id()) != null) {
      isSelected = false;
    } else {
      selected.put(entry.id(), entry);
      isSelected = true;
    }
    syncSelectMode();
    int index = adapter.indexOfViewByLongId(entry.id());
    if (index != -1) {
      adapter.getItems().get(index).setSelected(isSelected);
      if (rowsCompose) {
        adapter.notifyItemChanged(index);
      } else {
        adapter.setIsSelected(index, isSelected, -1);
      }
    }
    refreshThumbs();
  }

  private void syncSelectMode () {
    if (selected.isEmpty()) {
      if (inSelectMode()) {
        closeSelectMode();
      }
      adapter.setInSelectMode(false, true, null);
    } else {
      adapter.setInSelectMode(true, true, null);
      if (!inSelectMode()) {
        openSelectMode(selected.size());
      } else {
        setSelectedCount(selected.size());
      }
    }
    updateFab();
  }

  private void clearSelection () {
    if (selected.isEmpty()) {
      return;
    }
    selected.clear();
    if (inSelectMode()) {
      closeSelectMode();
    }
    if (adapter != null) {
      adapter.setInSelectMode(false, true, null);
      rebuild();
    }
    updateFab();
  }

  @Override
  public void finishSelectMode (int position) {
    // The header's close button or Back
    clearSelection();
  }

  @Override
  public void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    if (!selected.isEmpty()) {
      selected.clear();
      if (adapter != null) {
        adapter.setInSelectMode(false, true, null);
        rebuild();
      }
    }
    refreshThumbs();
    updateFab();
  }

  private void selectAll () {
    List<XgramDrive.Entry> files = query != null ? drive.searchFiles(query, XgramDrive.sortOrder()) : drive.filesIn(folder(), XgramDrive.sortOrder());
    for (XgramDrive.Entry entry : files) {
      if (!entry.isSending()) {
        selected.put(entry.id(), entry);
      }
    }
    syncSelectMode();
    rebuild();
  }

  // Header

  @Override
  protected int getMenuId () {
    return isPicker() ? R.id.menu_xgramDrivePick : R.id.menu_xgramDrive;
  }

  @Override
  protected int getSelectMenuId () {
    return R.id.menu_xgramDriveSelect;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getSearchHint () {
    return R.string.XgramDriveSearch;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_xgramDrive) {
      header.addSearchButton(menu, this);
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_xgramDrivePick) {
      header.addButton(menu, R.id.menu_btn_xgramDriveNewFolder, R.drawable.baseline_create_new_folder_24, getHeaderIconColorId(), this, Screen.dp(49f));
    } else if (id == R.id.menu_xgramDriveSelect) {
      header.addButton(menu, R.id.menu_btn_xgramDriveMove, R.drawable.baseline_drive_file_move_24, getSelectHeaderIconColorId(), this, Screen.dp(52f));
      header.addButton(menu, R.id.menu_btn_xgramDriveShare, R.drawable.baseline_forward_24, getSelectHeaderIconColorId(), this, Screen.dp(52f));
      header.addDeleteButton(menu, this, getSelectHeaderIconColorId());
      header.addMoreButton(menu, this, getSelectHeaderIconColorId());
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      if (headerView != null && drive.state() == XgramDrive.STATE_READY) {
        headerView.openSearchMode();
      }
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_xgramDriveNewFolder) {
      newFolder();
    } else if (id == R.id.menu_btn_xgramDriveMove) {
      moveFiles(new ArrayList<>(selected.values()));
    } else if (id == R.id.menu_btn_xgramDriveShare) {
      List<XgramDrive.Entry> sharing = new ArrayList<>(selected.values());
      clearSelection();
      share(sharing);
    } else if (id == R.id.menu_btn_delete) {
      if (!selected.isEmpty()) {
        confirmDelete(new ArrayList<>(selected.values()), Collections.emptyList());
      }
    } else if (id == R.id.menu_btn_more) {
      if (inSelectMode()) {
        showSelectionMore(view);
      } else {
        showDriveMore(view);
      }
    }
  }

  private void showSelectionMore (View anchor) {
    List<Action> actions = new ArrayList<>();
    actions.add(new Action(R.id.btn_xgramDriveDownload, R.drawable.baseline_file_download_24, R.string.SaveToDownloads, false, () -> {
      saveToDownloads(new ArrayList<>(selected.values()));
      clearSelection();
    }));
    actions.add(new Action(R.id.btn_xgramDriveSelectAll, R.drawable.baseline_check_circle_24, R.string.SelectAll, false, this::selectAll));
    showActions(anchor, null, actions);
  }

  private void showDriveMore (View anchor) {
    List<Action> actions = new ArrayList<>();
    if (drive.state() == XgramDrive.STATE_READY) {
      actions.add(new Action(R.id.btn_xgramDriveSort, R.drawable.baseline_import_export_24, R.string.XgramDriveSortBy, false, () -> showSort(anchor)));
      actions.add(new Action(R.id.btn_xgramDriveRefresh, R.drawable.baseline_sync_24, R.string.XgramDriveRefresh, false, () -> drive.refresh()));
    }
    if (drive.chatId() != 0) {
      actions.add(new Action(R.id.btn_xgramDriveOpenChannel, R.drawable.baseline_bullhorn_24, R.string.XgramDriveOpenChannel, false, () -> tdlib.ui().openChat(this, drive.chatId(), null)));
    }
    if (!actions.isEmpty()) {
      showActions(anchor, null, actions);
    }
  }

  private void showSort (View anchor) {
    final int current = XgramDrive.sortOrder();
    final int[] orders = {XgramDrive.SORT_NAME, XgramDrive.SORT_DATE, XgramDrive.SORT_SIZE, XgramDrive.SORT_TYPE};
    final int[] titles = {R.string.XgramDriveSortName, R.string.XgramDriveSortDate, R.string.XgramDriveSortSize, R.string.XgramDriveSortType};
    final int[] ids = {R.id.btn_xgramDriveSortName, R.id.btn_xgramDriveSortDate, R.id.btn_xgramDriveSortSize, R.id.btn_xgramDriveSortType};
    List<Action> actions = new ArrayList<>();
    for (int i = 0; i < orders.length; i++) {
      final int order = orders[i];
      actions.add(new Action(ids[i], order == current ? R.drawable.baseline_check_24 : R.drawable.baseline_import_export_24, titles[i], false, () -> {
        XgramDrive.setSortOrder(order);
        rebuild();
      }));
    }
    showActions(anchor, Lang.getString(R.string.XgramDriveSortBy), actions);
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    this.query = query;
    rebuild();
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    this.query = "";
    rebuild();
    updateFab();
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    clearSelection();
    this.query = null;
    rebuild();
    updateFab();
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (fab != null && fab.xgramIsOpen()) {
      if (commit) {
        fab.close();
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  /** Expressive design: the page's large title has the folder's place under it. */
  public @Nullable CharSequence xgramLargeSubtitle () {
    String folder = folder();
    if ("/".equals(folder)) {
      return null;
    }
    String parent = XgramDrive.parentOf(folder);
    return "/".equals(parent) ? Lang.getString(R.string.XgramDrive) : Lang.getString(R.string.XgramDrive) + parent.replace("/", " › ");
  }
}
