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

import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.List;

/**
 * Save with XDrive on: the folder in the same step. A message with a clear topic (receipts, code, places… the rules
 * Organize uses) goes straight into that folder while Sort new saves is on; else, when there are folders, a sheet asks
 * which one (or none) before the copy is made. Then the copy goes to Saved Messages and into the folder once posted.
 */
public final class XDriveSave {
  private XDriveSave () { }

  public interface Saver {
    /** Copies the messages to Saved Messages; {@code onCopies} gets the copies (their temporary ids), null on error. */
    void save (Callback onCopies);
  }

  public interface Callback {
    void onCopies (@Nullable TdApi.Messages copies);
  }

  public static void save (ViewController<?> c, TdApi.Message source, Saver saver) {
    final Tdlib tdlib = c.tdlib();
    chooseFolder(c, source, folder -> copyInto(tdlib, XDriveIndex.of(tdlib), saver, folder));
  }

  public interface FolderCallback {
    /** The folder picked, or null for none. */
    void onFolder (@Nullable XDriveIndex.Folder folder);
  }

  /**
   * The folder for what is about to be saved: the folder of {@code source}'s topic while Sort new saves is on, else the
   * user's pick (none when there are no folders yet). Nothing runs when the sheet is dismissed.
   */
  public static void chooseFolder (ViewController<?> c, @Nullable TdApi.Message source, FolderCallback callback) {
    final Tdlib tdlib = c.tdlib();
    final XDriveIndex index = XDriveIndex.of(tdlib);
    index.load(() -> {
      if (c.isDestroyed()) {
        return;
      }
      if (source != null && XgramSettings.instance().xdriveAutoSort()) {
        final XDriveTopic topic = XDriveRules.INSTANCE.topicOf(source, XDriveItems.INSTANCE.kindOf(source));
        if (topic != null) {
          final String name = Lang.getString(topic.getTitle());
          XDriveIndex.Folder folder = index.findFolderByName(name);
          if (folder == null) {
            folder = index.createFolder(name, topic.getIcon());
          }
          callback.onFolder(folder);
          return;
        }
      }
      final List<XDriveIndex.Folder> folders = index.folders();
      if (folders.isEmpty()) {
        callback.onFolder(null);
        return;
      }
      final int count = folders.size() + 2;
      final int[] ids = new int[count];
      final String[] titles = new String[count];
      final int[] colors = new int[count];
      final int[] icons = new int[count];
      final SparseArray<XDriveIndex.Folder> byId = new SparseArray<>();
      int i = 0;
      for (XDriveIndex.Folder folder : folders) {
        ids[i] = 1000 + i;
        titles[i] = folder.emoji != null ? folder.emoji + " " + folder.name : folder.name;
        colors[i] = ViewController.OptionColor.NORMAL;
        icons[i] = R.drawable.baseline_folder_24;
        byId.put(ids[i], folder);
        i++;
      }
      ids[i] = R.id.btn_xgramXDriveNewFolder;
      titles[i] = Lang.getString(R.string.XgramXDriveNewFolder);
      colors[i] = ViewController.OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_create_new_folder_24;
      i++;
      ids[i] = R.id.btn_xgramXDriveSaveUnsorted;
      titles[i] = Lang.getString(R.string.XgramXDriveSaveUnsorted);
      colors[i] = ViewController.OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_bookmark_24;
      c.showOptions(Lang.getString(R.string.XgramXDriveSaveWhere), ids, titles, colors, icons, (itemView, optionId) -> {
        if (optionId == R.id.btn_xgramXDriveNewFolder) {
          c.openInputAlert(Lang.getString(R.string.XgramXDriveNewFolder), Lang.getString(R.string.XgramXDriveFolderName), R.string.XgramXDriveCreate, R.string.Cancel, null, (inputView, result) -> {
            final String name = result.trim();
            if (name.isEmpty()) {
              return false;
            }
            XDriveIndex.Folder folder = index.findFolderByName(name);
            if (folder == null) {
              folder = index.createFolder(name);
            }
            callback.onFolder(folder);
            return true;
          }, true);
        } else if (optionId == R.id.btn_xgramXDriveSaveUnsorted) {
          callback.onFolder(null);
        } else {
          callback.onFolder(byId.get(optionId));
        }
        return true;
      });
    });
  }

  /** After a share into XDrive: the sent messages (their temporary ids) go into {@code folder} once posted. */
  public static void intoFolderWhenSent (Tdlib tdlib, long[] tempIds, @Nullable XDriveIndex.Folder folder) {
    if (folder != null) {
      XDriveIndex.of(tdlib).setFolderWhenSent(tempIds, folder.id);
      UI.showToast(Lang.getString(R.string.XgramXDriveSavedIn, folder.name), Toast.LENGTH_SHORT);
    } else {
      UI.showToast(R.string.XgramSavedToSaved, Toast.LENGTH_SHORT);
    }
  }

  private static void copyInto (Tdlib tdlib, XDriveIndex index, Saver saver, @Nullable XDriveIndex.Folder folder) {
    saver.save(copies -> tdlib.ui().post(() -> {
      if (copies == null) {
        return; // the saver showed the error
      }
      if (folder != null && copies.messages != null) {
        final long[] ids = new long[copies.messages.length];
        for (int i = 0; i < ids.length; i++) {
          ids[i] = copies.messages[i] != null ? copies.messages[i].id : 0;
        }
        index.setFolderWhenSent(ids, folder.id);
        UI.showToast(Lang.getString(R.string.XgramXDriveSavedIn, folder.name), Toast.LENGTH_SHORT);
      } else {
        UI.showToast(R.string.XgramSavedToSaved, Toast.LENGTH_SHORT);
      }
    }));
  }
}
