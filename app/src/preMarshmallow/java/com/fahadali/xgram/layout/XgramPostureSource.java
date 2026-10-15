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

/**
 * Legacy and lollipop builds (minSdk 16 and 21): Jetpack WindowManager needs minSdk 23, and no foldable runs
 * Android 5, so {@link XgramWindow} has size classes but never a fold.
 */
final class XgramPostureSource {
  XgramPostureSource (Activity activity, XgramWindow window) { }

  static boolean isSupported () {
    return false;
  }

  void start () { }

  void stop () { }
}
