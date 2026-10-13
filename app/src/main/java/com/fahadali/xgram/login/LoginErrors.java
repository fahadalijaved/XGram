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
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;

import me.vkryl.core.StringUtils;

/**
 * Plain text for the login errors Telegram X used to show raw, like "#400: PHONE_CODE_EXPIRED".
 * TD.translateError asks here when neither Telegram X nor the language pack knows the error, so a translation from the
 * language pack still wins. Unknown errors keep the old "#code: MESSAGE" fallback.
 */
public final class LoginErrors {
  private LoginErrors () { }

  /** The server dropped the login on its side. Sending the phone number again starts a new one. */
  public static final String AUTH_RESTART = "AUTH_RESTART";
  public static final String UPDATE_APP_TO_LOGIN = "UPDATE_APP_TO_LOGIN";
  /** TDLib's own answer to ResendAuthenticationCode when there is no other way to send the code. */
  public static final String RESEND_UNAVAILABLE = "Authentication code can't be resend";

  private static final String FLOOD_WAIT_PREFIX = "FLOOD_WAIT_";

  public static boolean is (@Nullable TdApi.Error error, @NonNull String message) {
    return error != null && message.equals(error.message);
  }

  /** Seconds from a raw FLOOD_WAIT_X that TDLib passed through, or 0. TDLib usually turns these into code 429. */
  public static int rawFloodSeconds (@Nullable String message) {
    if (message == null || !message.startsWith(FLOOD_WAIT_PREFIX)) {
      return 0;
    }
    String seconds = message.substring(FLOOD_WAIT_PREFIX.length());
    if (seconds.isEmpty() || !seconds.matches("^[0-9]+$")) {
      return 0;
    }
    return Math.max(StringUtils.parseInt(seconds), 0);
  }

  /**
   * True for errors whose text already says what to do, so the phone screen shows it as is instead of wrapping it in
   * "Error: ... Contact us".
   */
  public static boolean hasPlainText (@Nullable TdApi.Error error) {
    if (error == null || StringUtils.isEmpty(error.message)) {
      return false;
    }
    switch (error.message) {
      case AUTH_RESTART:
      case UPDATE_APP_TO_LOGIN:
      case "PHONE_NUMBER_FLOOD":
      case "PHONE_PASSWORD_FLOOD":
      case "SEND_CODE_UNAVAILABLE":
        return true;
    }
    return rawFloodSeconds(error.message) > 0 || TD.getFloodErrorSeconds(error.code, error.message, 0) > 0;
  }

  /** Plain text for a login error, or null when it isn't one of them. */
  public static @Nullable String translate (int code, @Nullable String message) {
    if (StringUtils.isEmpty(message)) {
      return null;
    }
    @StringRes int res;
    switch (message) {
      case "PHONE_CODE_EXPIRED": res = R.string.xgram_login_PhoneCodeExpiredShort; break;
      case "PHONE_CODE_EMPTY": res = R.string.xgram_login_PhoneCodeEmpty; break;
      case "SEND_CODE_UNAVAILABLE":
      case RESEND_UNAVAILABLE: res = R.string.xgram_login_SendCodeUnavailable; break;
      case AUTH_RESTART: res = R.string.xgram_login_AuthRestart; break;
      case "PHONE_NUMBER_FLOOD": res = R.string.xgram_login_PhoneNumberFlood; break;
      case "PHONE_PASSWORD_FLOOD": res = R.string.xgram_login_PhonePasswordFlood; break;
      case "EMAIL_INVALID": res = R.string.xgram_login_EmailInvalid; break;
      case "EMAIL_NOT_ALLOWED": res = R.string.xgram_login_EmailNotAllowed; break;
      case "EMAIL_HASH_EXPIRED": res = R.string.xgram_login_EmailCodeExpired; break;
      case "CODE_INVALID": res = R.string.InvalidCode; break;
      case "TASK_ALREADY_EXISTS": res = R.string.xgram_login_TaskAlreadyExists; break;
      case "VERIFICATION_FAILED": res = R.string.xgram_login_VerificationFailed; break;
      case UPDATE_APP_TO_LOGIN: res = R.string.xgram_login_UpdateAppToLogin; break;
      case "PHONE_NUMBER_UNOCCUPIED": res = R.string.xgram_login_PhoneNumberUnoccupied; break;
      default: {
        int floodSeconds = rawFloodSeconds(message);
        if (floodSeconds > 0) {
          return Lang.getString(R.string.format_TooManyRequests, Lang.getTryAgainIn(floodSeconds));
        }
        return null;
      }
    }
    return Lang.getString(res);
  }
}
