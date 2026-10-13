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

import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import me.vkryl.core.StringUtils;

/**
 * "Reset account" on the 2FA password login screen, for users who forgot the password and have no recovery email or
 * can't open it. It sends DeleteAccount with no password, which TDLib allows only in authorizationStateWaitPassword,
 * before the account is logged in here.
 *
 * The server either deletes the account at once, or, when the account is in use and protected by a password, answers
 * 2FA_CONFIRM_WAIT_X: the reset is scheduled and DeleteAccount works after X seconds. Asking again during the wait
 * returns the time that is left.
 *
 * Every request goes to the login screen's own tdlib, and only while that tdlib is in WaitPassword, so an account
 * that is already logged in can never be deleted from here. The user confirms twice, the second time by typing the
 * phone number (or a word when the number isn't known, e.g. after a QR login).
 */
public final class ResetAccount {
  public interface Host {
    /** True while the screen waits for another auth query; TDLib would cancel one of them. */
    boolean isBusy ();
    void setInProgress (boolean inProgress);
  }

  private static final String CONFIRM_WAIT_PREFIX = "2FA_CONFIRM_WAIT_";
  private static final String REASON = "Forgot password";
  private static final int MIN_NATIONAL_DIGITS = 6;

  private ResetAccount () { }

  /** Only before login, with the password step on screen. */
  public static boolean isAvailable (@NonNull Tdlib tdlib) {
    TdApi.AuthorizationState state = tdlib.authorizationState();
    return state != null && state.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR;
  }

  /** The "no recovery email" sheet: the explanation plus "Reset account". */
  public static void showOptions (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull CharSequence info, @NonNull Host host) {
    controller.showOptions(info,
      new int[] {R.id.btn_xgramResetAccount, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.xgram_login_ResetAccount), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_alert_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramResetAccount) {
          confirmFirst(controller, tdlib, host);
        }
        return true;
      }
    );
  }

  private static void confirmFirst (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull Host host) {
    if (controller.isDestroyed() || !isAvailable(tdlib)) {
      return;
    }
    controller.showOptions(Lang.getString(R.string.xgram_login_ResetAccountInfo),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Continue), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_alert_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          confirmTyped(controller, tdlib, host);
        }
        return true;
      }
    );
  }

  private static String nz (@Nullable String s) {
    return s != null ? s : "";
  }

  /** Digits of the number typed on the phone screen of this tdlib, or "" when it isn't known. */
  private static String knownPhoneDigits (@NonNull Tdlib tdlib) {
    if (!tdlib.hasAuthPhoneNumber()) {
      return "";
    }
    String digits = Strings.getNumber(nz(tdlib.authPhoneCode()) + nz(tdlib.authPhoneNumber()));
    return digits != null ? digits : "";
  }

  private static boolean matches (@NonNull Tdlib tdlib, @Nullable String input) {
    if (StringUtils.isEmptyOrBlank(input)) {
      return false;
    }
    String word = Lang.getString(R.string.xgram_login_ResetAccountWord);
    if (input.trim().equalsIgnoreCase(word)) {
      // Also accepted when a number is known: it may be from an earlier attempt, before a QR login of another account
      return true;
    }
    String full = knownPhoneDigits(tdlib);
    String typed = Strings.getNumber(input);
    if (StringUtils.isEmpty(full) || StringUtils.isEmpty(typed)) {
      return false;
    }
    if (typed.equals(full)) {
      return true;
    }
    String national = Strings.getNumber(nz(tdlib.authPhoneNumber()));
    return national != null && national.length() >= MIN_NATIONAL_DIGITS && typed.equals(national);
  }

  private static void confirmTyped (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull Host host) {
    if (controller.isDestroyed() || !isAvailable(tdlib)) {
      return;
    }
    boolean byPhone = !StringUtils.isEmpty(knownPhoneDigits(tdlib));
    String word = Lang.getString(R.string.xgram_login_ResetAccountWord);
    CharSequence title = byPhone ?
      Lang.getString(R.string.xgram_login_ResetAccountTypePhone) :
      Lang.getString(R.string.xgram_login_ResetAccountTypeWord, word);
    CharSequence placeholder = byPhone ? Lang.getString(R.string.login_PhoneNumber) : word;
    MaterialEditTextGroup inputView = controller.openInputAlert(title, placeholder, R.string.xgram_login_ResetAccount, R.string.Cancel, null, (view, result) -> {
      if (!matches(tdlib, result)) {
        return false;
      }
      send(controller, tdlib, host);
      return true;
    }, true);
    if (byPhone && inputView != null) {
      inputView.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
    }
  }

  /** Seconds from 2FA_CONFIRM_WAIT_X, or -1. */
  private static int confirmWaitSeconds (@Nullable TdApi.Error error) {
    if (error == null || error.message == null || !error.message.startsWith(CONFIRM_WAIT_PREFIX)) {
      return -1;
    }
    String seconds = error.message.substring(CONFIRM_WAIT_PREFIX.length());
    if (seconds.isEmpty() || !seconds.matches("^[0-9]+$")) {
      return -1;
    }
    return Math.max(StringUtils.parseInt(seconds), 0);
  }

  private static void send (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull Host host) {
    if (controller.isDestroyed() || host.isBusy()) {
      return;
    }
    // Checked again right before sending: the login may have moved on while the dialogs were open
    if (!isAvailable(tdlib)) {
      return;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }
    host.setInProgress(true);
    tdlib.send(new TdApi.DeleteAccount(REASON, ""), (ok, error) -> {
      if (error == null) {
        // TDLib now logs this slot out and starts it again at the phone screen, which may close this screen first
        UI.showToast(R.string.xgram_login_ResetAccountDone, Toast.LENGTH_LONG);
      }
      controller.runOnUiThreadOptional(() -> {
        host.setInProgress(false);
        if (error == null) {
          return;
        }
        int waitSeconds = confirmWaitSeconds(error);
        if (waitSeconds >= 0) {
          controller.openAlert(R.string.xgram_login_ResetAccount, Lang.getString(R.string.xgram_login_ResetAccountWait, Lang.getDuration(Math.max(waitSeconds, 1))));
        } else {
          controller.openAlert(R.string.xgram_login_ResetAccount, TD.toErrorString(error));
        }
      });
    });
  }
}
