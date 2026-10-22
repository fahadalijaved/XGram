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
 * is XDrive everywhere (one string remap in Lang), its icon a cloud, and opening it shows the drive (XDriveController):
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
        navigation.navigateTo(new XDriveController(context.context(), context.tdlib()));
      }
      return;
    }
    if (!navigation.isEmpty() && context.context().isNavigationBusy()) {
      return;
    }
    XDriveController c = new XDriveController(context.context(), context.tdlib());
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
    XDriveController c = new XDriveController(context.context(), context.tdlib());
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
}
