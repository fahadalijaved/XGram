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
package com.fahadali.xgram.login;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;

import me.vkryl.core.StringUtils;

/** Plain messages for the errors the login email screens can get. Anything else falls back to TD.toErrorString. */
public final class EmailLoginErrors {
  private EmailLoginErrors () { }

  public static final String TASK_ALREADY_EXISTS = "TASK_ALREADY_EXISTS";

  public static boolean is (@Nullable TdApi.Error error, @NonNull String message) {
    return error != null && message.equals(error.message);
  }

  /** Seconds to wait for a flood error, or 0. */
  public static int floodSeconds (@Nullable TdApi.Error error) {
    if (error == null || StringUtils.isEmpty(error.message)) {
      return 0;
    }
    int seconds = TD.getFloodErrorSeconds(error.code, error.message, 0);
    if (seconds <= 0 && error.message.startsWith("FLOOD_WAIT_")) {
      seconds = StringUtils.parseInt(error.message.substring("FLOOD_WAIT_".length()));
    }
    return Math.max(seconds, 0);
  }

  public static String toString (@Nullable TdApi.Object object) {
    if (object instanceof TdApi.Error) {
      TdApi.Error error = (TdApi.Error) object;
      if (!StringUtils.isEmpty(error.message)) {
        switch (error.message) {
          case "EMAIL_INVALID":
            return Lang.getString(R.string.xgram_login_EmailInvalid);
          case "EMAIL_NOT_ALLOWED":
            return Lang.getString(R.string.xgram_login_EmailNotAllowed);
          case "CODE_INVALID":
          case "EMAIL_CODE_INVALID":
            return Lang.getString(R.string.InvalidCode);
          case "EMAIL_HASH_EXPIRED":
            return Lang.getString(R.string.xgram_login_EmailCodeExpired);
          case TASK_ALREADY_EXISTS:
            return Lang.getString(R.string.xgram_login_ResetEmailRequested);
        }
        int floodSeconds = floodSeconds(error);
        if (floodSeconds > 0) {
          return Lang.getString(R.string.format_TooManyRequests, Lang.getTryAgainIn(floodSeconds));
        }
      }
    }
    return TD.toErrorString(object);
  }
}
