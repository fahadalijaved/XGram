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
package com.fahadali.xgram.layout;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.tool.UI;

/**
 * "Open links side by side" (adaptive plan 2.4): external links and files opened with another app start in a window
 * next to XGram instead of on top of it. In split screen the other app takes the adjacent half; on a large screen in
 * full screen (Android 12L and later) the system starts split screen with it. Elsewhere the flag is ignored.
 */
public final class XgramOpenBeside {
  private XgramOpenBeside () { }

  /** Adds the adjacent-window flags to an {@link Intent#ACTION_VIEW} intent when the setting is on and there is room. */
  public static void apply (@Nullable Intent intent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
      return;
    }
    if (!XgramSettings.instance().openBeside()) {
      return;
    }
    Activity activity = UI.getUiContext();
    if (activity == null || activity.isFinishing()) {
      return;
    }
    boolean sideBySide = activity.isInMultiWindowMode() ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && activity.getResources().getConfiguration().smallestScreenWidthDp >= 600);
    if (sideBySide) {
      intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
    }
  }
}
