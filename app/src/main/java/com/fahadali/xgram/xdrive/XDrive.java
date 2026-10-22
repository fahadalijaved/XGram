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
package com.fahadali.xgram.xdrive;

import android.util.SparseIntArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;

/**
 * XDrive: the account's Saved Messages as its drive (Settings → Testing utilities → XDrive, a test for now). Its name
 * is XDrive everywhere (one string remap in Lang), its icon a cloud, and opening it shows the drive (compose/pages/XDrivePage.kt):
 * sections over what is already there, the user's folders, the chat one tap away. Nothing in the messages changes.
 */
public final class XDrive {
  private XDrive () { }

  /** Read on every string lookup (Lang): a plain field, refreshed when the switch changes. */
  private static volatile boolean on;
  private static boolean initialized;

  private static final SparseIntArray REMAP = new SparseIntArray();

  static {
    REMAP.put(R.string.SavedMessages, R.string.XgramXDrive);
    REMAP.put(R.string.ChatWithYourself, R.string.XgramXDriveSubtitle);
    REMAP.put(R.string.ClearSavedMessages, R.string.XgramXDriveClear);
    REMAP.put(R.string.ClearSavedMessagesConfirm, R.string.XgramXDriveClearConfirm);
    REMAP.put(R.string.ClearSavedMessagesSecondaryConfirm, R.string.XgramXDriveClearSecondaryConfirm);
    REMAP.put(R.string.DeleteSavedMessagesConfirm, R.string.XgramXDriveDeleteConfirm);
    REMAP.put(R.string.XgramSaveToSaved, R.string.XgramSaveToXDrive);
    REMAP.put(R.string.XgramSavedToSaved, R.string.XgramSavedToXDrive);
    REMAP.put(R.string.XgramSavedByChat, R.string.XgramXDriveFromChats);
    REMAP.put(R.string.XgramNewSavedByChat, R.string.XgramXDriveByChat);
    REMAP.put(R.string.XgramNavRailSavedMessages, R.string.XgramXDrive);
    REMAP.put(R.string.XgramBottomNavSaved, R.string.XgramXDriveShort);
    REMAP.put(R.string.XgramNavBarSavedSub, R.string.XgramXDriveNavSub);
    REMAP.put(R.string.XgramLaterSelfNo, R.string.XgramXDriveLaterNo);
  }

  /** Whether Saved Messages is XDrive now (the test switch). */
  public static boolean on () {
    if (!initialized) {
      initialized = true;
      on = XgramSettings.instance().xdriveTest();
    }
    return on;
  }

  /** The switch changed (XgramSettings): names and icons follow from the next lookup. */
  public static void onSettingChanged (boolean value) {
    initialized = true;
    on = value;
  }

  /** Lang: a Saved Messages string becomes its XDrive twin while XDrive is on (cloud language packs too). */
  public static int remapString (int resId) {
    if (!on()) {
      return resId;
    }
    int mapped = REMAP.get(resId, 0);
    return mapped != 0 ? mapped : resId;
  }

  /** The Saved Messages icon (the bookmark) as XDrive's cloud while it is on. */
  @DrawableRes
  public static int icon (@DrawableRes int bookmarkRes) {
    return on() ? R.drawable.baseline_cloud_24 : bookmarkRes;
  }

  /**
   * TdlibUi.openChat's routing of Saved Messages: XDrive placed as the chat would be. Nothing on screen yet (a cold
   * start): XDrive with the chat list under it; from a notification over other screens: the chat list under it too.
   */
  public static void openRouted (org.thunderdog.challegram.telegram.TdlibDelegate context, boolean fromNotification) {
    NavigationController navigation = context.context().navigation();
    if (navigation == null || navigation.isDestroyed()) {
      return;
    }
    if (!(navigation.isEmpty() || (fromNotification && navigation.getStackSize() > 1))) {
      ViewController<?> current = context instanceof ViewController<?> ? (ViewController<?>) context : navigation.getCurrentStackItem();
      if (current != null) {
        open(current);
      } else {
        navigation.navigateTo(newScreen(context.context(), context.tdlib()));
      }
      return;
    }
    if (!navigation.isEmpty() && context.context().isNavigationBusy()) {
      return;
    }
    ViewController<?> c = newScreen(context.context(), context.tdlib());
    if (navigation.isEmpty()) {
      navigation.initController(c);
      org.thunderdog.challegram.ui.MainController main = new org.thunderdog.challegram.ui.MainController(context.context(), context.tdlib());
      main.getValue();
      navigation.getStack().insert(main, 0);
    } else {
      navigation.setControllerAnimated(c, true, true);
    }
  }

  /** XDrive's screen: on the right pane with two panes, a push otherwise. */
  public static void open (@Nullable ViewController<?> context) {
    if (context == null) {
      return;
    }
    ViewController<?> c = newScreen(context.context(), context.tdlib());
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

  /** XDrive's screen (compose/pages/XDrivePage.kt). */
  public static ViewController<?> newScreen (android.content.Context context, org.thunderdog.challegram.telegram.Tdlib tdlib) {
    return com.fahadali.xgram.compose.XgramComposeController.create(context, tdlib, com.fahadali.xgram.compose.XgramPage.XDRIVE);
  }

  /** Add to folder from Saved Messages' message menu: the folders, a new one, or out of the folder they are in. */
  public static void pickFolder (ViewController<?> context, long[] messageIds) {
    final XDriveIndex index = XDriveIndex.of(context.tdlib());
    index.load(() -> {
      if (context.isDestroyed()) {
        return;
      }
      final java.util.List<XDriveIndex.Folder> folders = index.folders();
      final String current = messageIds.length > 0 ? index.folderOf(messageIds[0]) : null;
      final int count = folders.size() + 1 + (current != null ? 1 : 0);
      int[] ids = new int[count];
      String[] titles = new String[count];
      int[] colors = new int[count];
      int[] icons = new int[count];
      final android.util.SparseArray<XDriveIndex.Folder> byId = new android.util.SparseArray<>();
      int i = 0;
      for (XDriveIndex.Folder folder : folders) {
        ids[i] = 1000 + i;
        titles[i] = folder.name;
        colors[i] = ViewController.OptionColor.NORMAL;
        icons[i] = folder.id.equals(current) ? R.drawable.baseline_check_24 : R.drawable.baseline_folder_24;
        byId.put(ids[i], folder);
        i++;
      }
      ids[i] = R.id.btn_xgramXDriveNewFolder;
      titles[i] = org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveNewFolder);
      colors[i] = ViewController.OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_create_new_folder_24;
      i++;
      if (current != null) {
        ids[i] = R.id.btn_xgramXDriveRemove;
        titles[i] = org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveRemoveFromFolder);
        colors[i] = ViewController.OptionColor.RED;
        icons[i] = R.drawable.baseline_delete_24;
      }
      context.showOptions(org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveChooseFolder), ids, titles, colors, icons, (itemView, optionId) -> {
        if (optionId == R.id.btn_xgramXDriveNewFolder) {
          context.openInputAlert(org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveNewFolder), org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveFolderName), R.string.XgramXDriveCreate, R.string.Cancel, null, (inputView, result) -> {
            String name = result.trim();
            if (name.isEmpty()) {
              return false;
            }
            XDriveIndex.Folder folder = index.findFolderByName(name);
            if (folder == null) {
              folder = index.createFolder(name);
            }
            index.setFolder(messageIds, folder.id);
            org.thunderdog.challegram.tool.UI.showToast(org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveAddedTo, folder.name), android.widget.Toast.LENGTH_SHORT);
            return true;
          }, true);
        } else if (optionId == R.id.btn_xgramXDriveRemove) {
          index.setFolder(messageIds, null);
        } else {
          XDriveIndex.Folder folder = byId.get(optionId);
          if (folder != null) {
            index.setFolder(messageIds, folder.id);
            org.thunderdog.challegram.tool.UI.showToast(org.thunderdog.challegram.core.Lang.getString(R.string.XgramXDriveAddedTo, folder.name), android.widget.Toast.LENGTH_SHORT);
          }
        }
        return true;
      });
    });
  }
}
