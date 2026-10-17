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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

import me.vkryl.core.StringUtils;

/**
 * M3E round 6, batch 2: the chat list's chrome. The floating toolbar of chat selection (pin or unpin, mute or unmute,
 * archive or unarchive, delete in error), the "update ready" snackbar text in XGram's own words. Only used while
 * {@link XgramExpressive#isEnabled()} is true. Main thread only.
 */
public final class XgramR6B2Chat {
  private XgramR6B2Chat () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Chat selection

  /** Height of the floating toolbar, and its gap above the bottom inset (dp). */
  public static final float TOOLBAR_HEIGHT = 64f, TOOLBAR_GAP = 16f;

  /**
   * The chat list's selection toolbar: XgramM3Chat's vibrant floating toolbar on SPATIAL_FAST (a fade with Reduce
   * motion) with pin, mute, archive and delete (error). Every button calls {@code onClick} with its own id
   * (R.id.menu_btn_pinUnpin, R.id.menu_btn_muteUnmute, R.id.more_btn_archive, R.id.menu_btn_delete); the controller
   * sets what each shows with {@link XgramM3Chat.FloatingToolbar#setButton}.
   */
  @NonNull
  public static XgramM3Chat.FloatingToolbar newChatSelectionToolbar (@NonNull Context context, @NonNull View.OnClickListener onClick) {
    XgramM3Chat.FloatingToolbar toolbar = new XgramM3Chat.FloatingToolbar(context, XgramExpressive.SPATIAL_FAST);
    toolbar.addButton(R.id.menu_btn_pinUnpin, R.drawable.deproko_baseline_pin_24, Lang.getString(R.string.Pin), onClick);
    toolbar.addButton(R.id.menu_btn_muteUnmute, R.drawable.baseline_notifications_off_24, Lang.getString(R.string.Mute), onClick);
    toolbar.addButton(R.id.more_btn_archive, R.drawable.baseline_archive_24, Lang.getString(R.string.Archive), onClick);
    toolbar.addButton(R.id.menu_btn_delete, R.drawable.baseline_delete_24, Lang.getString(R.string.Delete), onClick, true);
    return toolbar;
  }

  /** The selection toolbar's bottom margin over {@code bottomInset} px (the navigation bar, the bottom folder tabs). */
  public static int toolbarBottomMargin (int bottomInset) {
    return Math.max(0, bottomInset) + Screen.dp(TOOLBAR_GAP);
  }

  // Update ready

  /**
   * The "update ready" snackbar's text: "XGram 1.2.3 is ready to install" (or "XGram update downloaded" without a
   * version) over Telegram X's bundled English, which names Telegram X; a translation keeps its own text.
   */
  @NonNull
  public static String updateReadyText (@Nullable String version) {
    final String tgx = Lang.getString(R.string.AppUpdateReady);
    if (!on()) {
      return tgx;
    }
    boolean bundledEnglish;
    try {
      bundledEnglish = tgx.equals(AppContext.get().getString(R.string.AppUpdateReady));
    } catch (Throwable t) {
      bundledEnglish = false;
    }
    if (!bundledEnglish) {
      return tgx;
    }
    return StringUtils.isEmpty(version) ? Lang.getString(R.string.XgramR6B2UpdateDownloaded) : Lang.getString(R.string.XgramR6B2UpdateReadyVersion, version);
  }
}
