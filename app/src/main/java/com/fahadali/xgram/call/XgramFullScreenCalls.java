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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;

/**
 * Incoming calls ring full screen (over the lock screen and other apps) only with Android's "Full screen notifications"
 * allowed for XGram (USE_FULL_SCREEN_INTENT, declared in the manifest). From Android 14 the user can turn it off per
 * app, and Android may turn it off again on every update of a sideloaded app (the installer sets it per install): then
 * a call that comes while XGram is closed is only a small notification.
 * <p>
 * This class is the one place that knows the state. While it is off: a prompt on the chat list (once per version, at
 * most once a day), a dot on the ☰ (MainActivity#updateCounter), a row at the top of the side menu, a banner on the
 * Calls page and a row in Settings → Notifications. {@link #refresh} reads it again (on every resume and chat list
 * focus) and tells the {@link Listener}s when it changed, so everything goes once it is allowed.
 */
public final class XgramFullScreenCalls {
  private XgramFullScreenCalls () { }

  /** Before the per-state memory: set once the prompt had shown, never cleared. Removed on the first look. */
  private static final String KEY_ASKED_LEGACY = "xgram_fullscreen_calls_asked";
  /**
   * Whether they were allowed the last time XGram looked, and the version code and the install ({@link #installTime})
   * it looked in. Local, not in backups.
   */
  private static final String KEY_LAST_ALLOWED = "xgram_fsc_last_allowed", KEY_LAST_CODE = "xgram_fsc_last_code", KEY_LAST_INSTALL = "xgram_fsc_last_install";
  /** Allowed before an install and off since: the install turned it off. Cleared once it is allowed again. */
  private static final String KEY_RESET_BY_UPDATE = "xgram_fsc_reset_by_update";
  /** The version code, the install and the time of the last prompt. */
  private static final String KEY_PROMPT_CODE = "xgram_fsc_prompt_code", KEY_PROMPT_INSTALL = "xgram_fsc_prompt_install", KEY_PROMPT_TIME = "xgram_fsc_prompt_time";

  private static final long PROMPT_INTERVAL_MS = 24L * 60L * 60L * 1000L;
  private static final long PROMPT_DELAY_MS = 2000L;
  /**
   * While the passcode or a navigation is in the way the prompt waits, up to this many times. Behind a card or a menu
   * (the update's welcome card, the update card while it downloads) it waits as long as that stays open.
   */
  private static final int PROMPT_RETRIES = 15;

  public interface Listener {
    /** Main thread. */
    void onFullScreenCallsChanged (boolean off);
  }

  private static final ReferenceList<Listener> listeners = new ReferenceList<>();
  @Nullable private static Boolean cachedOff;
  private static boolean promptPosted;

  /** Listeners are held weakly: keep a reference (a field, or the object itself). */
  public static void addListener (@NonNull Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (@NonNull Listener listener) {
    listeners.remove(listener);
  }

  /**
   * Full-screen calls are off: Android 14 and later, with "Full screen notifications" not allowed for XGram. As last
   * read by {@link #refresh} (the first call reads it). Always false below Android 14, where declaring it is enough.
   */
  public static boolean isOff (@NonNull Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return false;
    }
    Boolean off = cachedOff;
    if (off == null) {
      off = readOff(context);
      cachedOff = off;
    }
    return off;
  }

  /** Calls may ring full screen (always below Android 14). */
  public static boolean allowed (@NonNull Context context) {
    return !isOff(context);
  }

  @Nullable private static Boolean cachedReset;

  /** It was allowed in an older version and has been off since the update (the prompt and the banner say so). */
  public static boolean resetByUpdate () {
    Boolean reset = cachedReset;
    if (reset == null) {
      reset = pmc().getBoolean(KEY_RESET_BY_UPDATE, false);
      cachedReset = reset;
    }
    return reset;
  }

  /**
   * An update XGram installs itself may keep them allowed (XgramSelfInstaller): when they are allowed now, or the last
   * update turned them off. Never when the user turned them off in this version.
   */
  public static boolean keepOnUpdate (@NonNull Context context) {
    return !isOff(context) || resetByUpdate();
  }

  private static boolean readOff (@NonNull Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return false;
    }
    try {
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      return manager != null && !manager.canUseFullScreenIntent();
    } catch (Throwable t) {
      return false;
    }
  }

  private static LevelDB pmc () {
    return org.thunderdog.challegram.unsorted.Settings.instance().pmc();
  }

  private static long cachedInstallTime;

  /**
   * When this build was installed (PackageInfo#lastUpdateTime), 0 when unknown. A reinstall of the same version code
   * (a debug build of the same commit, the same APK again by hand) is an install too, and Android sets the permission on
   * it the same way. Read once per process: an install kills it. The user's own switch doesn't change it.
   */
  private static long installTime (@NonNull Context context) {
    long time = cachedInstallTime;
    if (time == 0L) {
      try {
        time = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
      } catch (Throwable t) {
        time = 0L;
      }
      cachedInstallTime = time;
    }
    return time;
  }

  /**
   * Reads the state again (one binder call): on every resume, so it is right after Android's page, and when the chat
   * list gets the focus. Remembers it with the version code, and tells the listeners when it changed.
   */
  public static void refresh (@NonNull Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return;
    }
    if (!UI.inUiThread()) {
      UI.post(() -> refresh(context));
      return;
    }
    final boolean off = readOff(context);
    remember(context, off);
    final Boolean was = cachedOff;
    cachedOff = off;
    if (was != null && was != off) {
      for (Listener listener : listeners) {
        listener.onFullScreenCallsChanged(off);
      }
    }
  }

  private static void remember (@NonNull Context context, boolean off) {
    final LevelDB pmc = pmc();
    final int code = BuildConfig.ORIGINAL_VERSION_CODE;
    final long install = installTime(context);
    final boolean known = pmc.contains(KEY_LAST_ALLOWED);
    final boolean lastAllowed = pmc.getBoolean(KEY_LAST_ALLOWED, false);
    final int lastCode = pmc.getInt(KEY_LAST_CODE, 0);
    final long lastInstall = pmc.getLong(KEY_LAST_INSTALL, 0L);
    final boolean reset = pmc.getBoolean(KEY_RESET_BY_UPDATE, false);
    // Installed since XGram last looked: another version, or the same version code again (a debug build of the same
    // commit, the same APK by hand). Before the install time was kept, by the version code alone
    final boolean newInstall = (lastCode != 0 && lastCode != code) || (lastInstall != 0L && install != 0L && lastInstall != install);
    if (off) {
      // Allowed when XGram last looked before an install, off now: the install turned it off (not the user)
      if (!reset && known && lastAllowed && newInstall) {
        pmc.putBoolean(KEY_RESET_BY_UPDATE, true);
        cachedReset = true;
      }
    } else if (reset) {
      pmc.remove(KEY_RESET_BY_UPDATE);
      cachedReset = false;
    }
    if (!known || lastAllowed == off || lastCode != code || newInstall) {
      pmc.putBoolean(KEY_LAST_ALLOWED, !off);
      pmc.putInt(KEY_LAST_CODE, code);
    }
    if (install != 0L && lastInstall != install) {
      pmc.putLong(KEY_LAST_INSTALL, install);
    }
    if (pmc.contains(KEY_ASKED_LEGACY)) {
      // The old once-ever flag: with no prompt recorded yet, the first chat list of this version asks again
      pmc.remove(KEY_ASKED_LEGACY);
    }
  }

  /** Android's page with XGram's "Full screen notifications" switch (the app's settings when that page is missing). */
  public static void openSettings (@NonNull Context context) {
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
   * Off, not asked in this version yet (or asked, and an install of the same version since turned them off again), and
   * not asked in the last 24 hours (or the clock went back since).
   */
  private static final String KEY_NEVER_ASK = "xgram_fullscreen_calls_never_ask";

  private static boolean promptDue (@NonNull Context context) {
    if (!isOff(context) || pmc().getBoolean(KEY_NEVER_ASK, false)) {
      return false; // Don't ask again: the menu's dot and the drawer row still say it's off
    }
    final LevelDB pmc = pmc();
    if (pmc.getInt(KEY_PROMPT_CODE, 0) == BuildConfig.ORIGINAL_VERSION_CODE) {
      final long install = installTime(context);
      if (!resetByUpdate() || install == 0L || pmc.getLong(KEY_PROMPT_INSTALL, 0L) == install) {
        return false;
      }
    }
    final long last = pmc.getLong(KEY_PROMPT_TIME, 0L);
    final long now = System.currentTimeMillis();
    return last <= 0L || now < last || now - last >= PROMPT_INTERVAL_MS;
  }

  /**
   * On the chat list: while full-screen calls are off, a prompt to allow them, at most once per version and once a
   * day (an update that turned them off again asks again). Two seconds after the list shows, once nothing else is open
   * (the update's welcome card, a menu, the passcode, the call screen over the lock screen).
   */
  public static void promptIfNeeded (@NonNull ViewController<?> c) {
    if (promptPosted || !promptDue(c.context())) {
      return;
    }
    promptPosted = true;
    UI.post(() -> tryPrompt(c, PROMPT_RETRIES), PROMPT_DELAY_MS);
  }

  private static void tryPrompt (@NonNull ViewController<?> c, int retries) {
    final BaseActivity context = c.context();
    if (c.isDestroyed() || !c.isFocused() || !promptDue(context)) {
      promptPosted = false;
      return;
    }
    if (context.getActivityState() != UI.State.RESUMED || XgramCallOverLock.hidesApp(context)) {
      promptPosted = false; // the next time the chat list gets the focus
      return;
    }
    final boolean busy = context.isPasscodeShowing() || context.isNavigationBusy();
    final boolean covered = context.getCurrentPopupWindow() != null || context.xgramAnyDialogShowing();
    if (busy || covered) {
      // Behind a card or a menu (the welcome card read for a while, the update card while it downloads) it waits as
      // long as that stays open, so it follows as it closes (a Dialog closing doesn't focus the chat list again). The
      // checks above still end it when the chat list loses the focus, the app goes to the background or it is allowed
      final int next = busy ? retries - 1 : retries;
      if (next >= 0) {
        UI.post(() -> tryPrompt(c, next), PROMPT_DELAY_MS);
      } else {
        promptPosted = false;
      }
      return;
    }
    promptPosted = false;
    final int text = resetByUpdate() ? R.string.XgramFullScreenCallsPromptReset : R.string.XgramFullScreenCallsPrompt;
    final Object popup = c.showOptions(Lang.getString(text),
      new int[] {R.id.btn_xgramFullScreenCallsAllow, R.id.btn_xgramFullScreenCallsNever, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramFullScreenCallsAllow), Lang.getString(R.string.XgramFullScreenCallsNever), Lang.getString(R.string.XgramFullScreenCallsLater)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_call_24, R.drawable.baseline_notifications_off_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramFullScreenCallsAllow) {
          openSettings(c.context());
        } else if (id == R.id.btn_xgramFullScreenCallsNever) {
          pmc().putBoolean(KEY_NEVER_ASK, true);
        }
        return true;
      });
    if (popup != null) {
      // Recorded only once it is on screen: a prompt that couldn't show asks the next time
      final LevelDB pmc = pmc();
      pmc.putInt(KEY_PROMPT_CODE, BuildConfig.ORIGINAL_VERSION_CODE);
      pmc.putLong(KEY_PROMPT_INSTALL, installTime(context));
      pmc.putLong(KEY_PROMPT_TIME, System.currentTimeMillis());
    }
  }
}
