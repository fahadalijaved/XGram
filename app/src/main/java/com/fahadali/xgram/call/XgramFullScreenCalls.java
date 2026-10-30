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
package com.fahadali.xgram.call;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

/**
 * Incoming calls ring full screen (over the lock screen and other apps) only with Android's "Full screen notifications"
 * allowed for XGram (USE_FULL_SCREEN_INTENT, declared in the manifest). From Android 14 the user can turn it off per
 * app: then a call that comes while XGram is closed is only a small notification. This asks once (on the chat list)
 * and keeps a row in Settings → Notifications while it is off.
 */
public final class XgramFullScreenCalls {
  private XgramFullScreenCalls () { }

  private static final String KEY_ASKED = "xgram_fullscreen_calls_asked";

  /** Calls may ring full screen (always below Android 14, where declaring the permission is enough). */
  public static boolean allowed (Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return true;
    }
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    return manager == null || manager.canUseFullScreenIntent();
  }

  /** Android's page with XGram's "Full screen notifications" switch (the app's settings when that page is missing). */
  public static void openSettings (Context context) {
    Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
      new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.getPackageName())) :
      new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
    } catch (Throwable t) {
      Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
      details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        context.startActivity(details);
      } catch (Throwable ignored) { }
    }
  }

  /**
   * Once, on the chat list: while full-screen calls are off, a prompt to allow them. Two seconds after the list shows,
   * and only if nothing else is open then (the update's welcome card); otherwise the next time.
   */
  public static void promptIfNeeded (ViewController<?> c) {
    if (allowed(c.context()) || org.thunderdog.challegram.unsorted.Settings.instance().getBoolean(KEY_ASKED, false)) {
      return;
    }
    UI.post(() -> {
      if (c.isDestroyed() || !c.isFocused() || c.context().getCurrentPopupWindow() != null || allowed(c.context())) {
        return;
      }
      org.thunderdog.challegram.unsorted.Settings.instance().putBoolean(KEY_ASKED, true);
      c.showOptions(Lang.getString(R.string.XgramFullScreenCallsPrompt),
        new int[] {R.id.btn_xgramFullScreenCallsAllow, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramFullScreenCallsAllow), Lang.getString(R.string.XgramFullScreenCallsLater)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_call_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_xgramFullScreenCallsAllow) {
            openSettings(c.context());
          }
          return true;
        });
    }, 2000);
  }
}
