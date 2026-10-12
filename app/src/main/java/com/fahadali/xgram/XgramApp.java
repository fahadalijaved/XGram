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
package com.fahadali.xgram;

import android.content.Context;
import android.content.Intent;

public final class XgramApp {
  private XgramApp () { }

  /** Restarts the app, for settings that are only read at startup (e.g. fonts). */
  public static void restart (Context context) {
    Context app = context.getApplicationContext();
    Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
    if (launch == null || launch.getComponent() == null) {
      return;
    }
    Intent restart = Intent.makeRestartActivityTask(launch.getComponent());
    app.startActivity(restart);
    Runtime.getRuntime().exit(0);
  }
}
