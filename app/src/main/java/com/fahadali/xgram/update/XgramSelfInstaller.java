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
package com.fahadali.xgram.update;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.fahadali.xgram.call.XgramFullScreenCalls;

import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs a verified update of XGram itself through a PackageInstaller session (Android 14+), instead of handing the
 * APK to the system installer (ACTION_INSTALL_PACKAGE). The confirmation is the same system "Update this app?" dialog
 * ({@link XgramInstallReceiver} opens it); the one difference is what the session may say about full-screen
 * notifications.
 * <p>
 * From Android 14 USE_FULL_SCREEN_INTENT is set by the installer on every install, an update included; given nothing
 * (as by ACTION_INSTALL_PACKAGE, adb or a file opened from a chat) the device's default policy applies, which for a
 * sideloaded app is "denied" on some ROMs: so full-screen calls went off after every update. It is the one special
 * permission any installer may grant (SessionParams#setPermissionState), no new permission needed (REQUEST_INSTALL_PACKAGES
 * is declared already). Granted only when it is allowed now or the last update turned it off
 * ({@link XgramFullScreenCalls#keepOnUpdate}), never against the user's own "off".
 * <p>
 * Anything that fails (no "Install unknown apps" yet, a session error) falls back to the system installer, as before.
 * It only helps updates installed by a build that has it: the first update into it still resets.
 */
public final class XgramSelfInstaller {
  private XgramSelfInstaller () { }

  static final String TAG = "XGram";
  static final String EXTRA_PATH = "com.fahadali.xgram.update.APK_PATH";
  static final String APK_MIME = "application/vnd.android.package-archive";

  /**
   * Main thread. Starts the session install of {@code apk} and returns true, or returns false (Android 13 and older,
   * no "Install unknown apps" permission yet: the system installer's own flow asks for it) and the caller opens the
   * system installer itself. {@code fallback} runs on the main thread when the session can't be made or written.
   */
  public static boolean install (@NonNull Context context, @NonNull File apk, @NonNull Runnable fallback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return false;
    }
    if (!apk.isFile() || apk.length() <= 0) {
      return false;
    }
    try {
      if (!context.getPackageManager().canRequestPackageInstalls()) {
        return false;
      }
    } catch (Throwable t) {
      return false;
    }
    final Context appContext = context.getApplicationContext();
    final boolean keepFullScreen = XgramFullScreenCalls.keepOnUpdate(context);
    new Thread(() -> {
      // The copy of a large APK takes a moment: off the main thread
      if (!commit(appContext, apk, keepFullScreen)) {
        UI.post(fallback);
      }
    }, "XgramSelfInstaller").start();
    return true;
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  private static boolean commit (@NonNull Context context, @NonNull File apk, boolean keepFullScreen) {
    final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
    abandonOlderSessions(context, installer);
    int sessionId = -1;
    PackageInstaller.Session session = null;
    try {
      PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
      params.setAppPackageName(context.getPackageName());
      params.setSize(apk.length());
      params.setInstallReason(PackageManager.INSTALL_REASON_USER);
      params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE);
      if (keepFullScreen) {
        params.setPermissionState(Manifest.permission.USE_FULL_SCREEN_INTENT, PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED);
      }
      sessionId = installer.createSession(params);
      session = installer.openSession(sessionId);
      try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
        final byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        session.fsync(out);
      }
      // The system fills in the status (and the confirmation to open): mutable, explicit, not exported
      Intent intent = new Intent(context, XgramInstallReceiver.class);
      intent.putExtra(EXTRA_PATH, apk.getAbsolutePath());
      PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
      session.commit(pending.getIntentSender());
      session.close();
      Log.i(TAG, "update install session " + sessionId + " committed, full-screen calls kept: " + keepFullScreen);
      return true;
    } catch (Throwable t) {
      Log.e(TAG, "update install session failed, the system installer instead", t);
      try {
        if (session != null) {
          session.abandon();
        } else if (sessionId != -1) {
          installer.abandonSession(sessionId);
        }
      } catch (Throwable ignored) { }
      return false;
    }
  }

  /**
   * An earlier update of XGram that never got its "Update" (its confirmation was closed unseen, or couldn't open) keeps
   * a staged copy of the APK until the system expires it: one per try. Abandoned before the next one (its status,
   * "aborted", does nothing).
   */
  private static void abandonOlderSessions (@NonNull Context context, @NonNull PackageInstaller installer) {
    try {
      final String packageName = context.getPackageName();
      for (PackageInstaller.SessionInfo info : installer.getMySessions()) {
        if (packageName.equals(info.getAppPackageName())) {
          try {
            installer.abandonSession(info.getSessionId());
          } catch (Throwable ignored) { }
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "cannot list the earlier update install sessions", t);
    }
  }
}
