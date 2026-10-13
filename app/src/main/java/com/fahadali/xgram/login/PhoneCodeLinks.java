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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;

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

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * The link under the phone-code login screen (PasswordController.MODE_CODE).
 *
 * It names the next way Telegram can send the code (SMS, call, ...) and counts down codeInfo.timeout, because TDLib
 * refuses a resend before that. When there is no other way, or the timer is still running, it opens a help sheet with
 * QR login and an email to Telegram support. After PHONE_CODE_EXPIRED it offers a new code.
 *
 * Everything here runs on the UI thread; TDLib results are posted back with runOnUiThreadOptional. Every request goes
 * to the screen's own tdlib, which is the account being logged in, never another account.
 */
public final class PhoneCodeLinks implements Handler.Callback {
  public interface Host {
    /** Text of the link under the code field; null hides it. */
    void setLinkText (@Nullable CharSequence text);
    void setHintText (@NonNull CharSequence text, boolean isError);
    void setLinkInProgress (boolean inProgress);
    @NonNull View linkView ();
    /** True while a code check or another login request runs. A second auth query would make TDLib abort it. */
    boolean isBusy ();
    /** ResendAuthenticationCode through the screen, which also handles the Firebase path. */
    void requestNextCode ();
    void requestQrLogin ();
    void emailSupport ();
  }

  public static final String PHONE_CODE_EXPIRED = "PHONE_CODE_EXPIRED";
  public static final String SEND_CODE_UNAVAILABLE = "SEND_CODE_UNAVAILABLE";

  private static final int MSG_TICK = 1;

  private final ViewController<?> controller;
  private final Tdlib tdlib;
  private final Host host;
  private final Handler handler = new Handler(Looper.getMainLooper(), this);

  private @Nullable TdApi.AuthorizationStateWaitCode state;
  private long stateReceivedAt;
  private boolean codeExpired;
  /** A resend failed for good (expired code, or no other way left), so codeInfo.nextType can't be used until a new state. */
  private boolean resendUnavailable;
  private boolean inProgress;
  private boolean destroyed;
  /** The phone number was already sent again after AUTH_RESTART for this state, so a second one doesn't loop. */
  private boolean authRestartHandled;

  public PhoneCodeLinks (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull Host host, @Nullable TdApi.AuthorizationStateWaitCode state) {
    this.controller = controller;
    this.tdlib = tdlib;
    this.host = host;
    this.state = state;
    this.stateReceivedAt = SystemClock.elapsedRealtime();
  }

  /** The number TDLib is sending the code to, formatted, or "" if unknown. */
  public static String formatPhone (@Nullable TdApi.AuthorizationState state) {
    if (state instanceof TdApi.AuthorizationStateWaitCode) {
      TdApi.AuthenticationCodeInfo info = ((TdApi.AuthorizationStateWaitCode) state).codeInfo;
      String number = info != null ? Strings.getNumber(info.phoneNumber) : null;
      if (!StringUtils.isEmpty(number)) {
        return Strings.formatPhone("+" + number, false, true);
      }
    }
    return "";
  }

  // State

  /** Called for every new authorization state. A new WaitCode (after a resend) restarts the countdown. */
  public void onStateChanged (@Nullable TdApi.AuthorizationState newState) {
    if (destroyed) {
      return;
    }
    if (newState != null && newState.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
      this.state = (TdApi.AuthorizationStateWaitCode) newState;
      this.stateReceivedAt = SystemClock.elapsedRealtime();
      this.codeExpired = false;
      this.resendUnavailable = false;
      this.authRestartHandled = false;
      refresh();
      return;
    }
    // The login moved on (password, sign-up, ready, ...). MainActivity opens that screen; stop here.
    this.state = null;
    handler.removeMessages(MSG_TICK);
    host.setLinkText(null);
  }

  private @Nullable TdApi.AuthenticationCodeType nextType () {
    return state != null && state.codeInfo != null && !resendUnavailable ? state.codeInfo.nextType : null;
  }

  private int secondsLeft () {
    if (state == null || state.codeInfo == null || state.codeInfo.timeout <= 0) {
      return 0;
    }
    long passed = (SystemClock.elapsedRealtime() - stateReceivedAt) / 1000L;
    return (int) Math.max(0, state.codeInfo.timeout - passed);
  }

  private static String nextTypeText (@NonNull TdApi.AuthenticationCodeType type) {
    switch (type.getConstructor()) {
      case TdApi.AuthenticationCodeTypeSms.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeSmsWord.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeSmsPhrase.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeFirebaseAndroid.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeFirebaseIos.CONSTRUCTOR:
        return Lang.getString(R.string.xgram_login_NextCodeSms);
      case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR:
        return Lang.getString(R.string.xgram_login_NextCodeCall);
      case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR:
        return Lang.getString(R.string.xgram_login_NextCodeMissedCall);
      case TdApi.AuthenticationCodeTypeFragment.CONSTRUCTOR:
        return Lang.getString(R.string.xgram_login_NextCodeFragment);
      case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR:
        return Lang.getString(R.string.xgram_login_NextCodeTelegram);
    }
    return Lang.getString(R.string.xgram_login_NextCodeOther);
  }

  /** The action of the link once the timer is over. */
  private @Nullable String actionText () {
    TdApi.AuthenticationCodeType next = nextType();
    if (codeExpired) {
      return Lang.getString(R.string.xgram_login_SendNewCode);
    }
    return next != null ? nextTypeText(next) : null;
  }

  /** Rebuilds the link and the countdown from the current state. */
  public void refresh () {
    handler.removeMessages(MSG_TICK);
    if (destroyed || state == null) {
      return;
    }
    String action = actionText();
    int left = nextType() != null ? secondsLeft() : 0;
    if (action == null) {
      host.setLinkText(Lang.getString(R.string.DidNotGetTheCode));
    } else if (left > 0) {
      host.setLinkText(Lang.getString(R.string.xgram_login_NextCodeIn, action, EmailCodeLinks.formatCountdown(left)));
      handler.sendEmptyMessageDelayed(MSG_TICK, 1000L);
    } else {
      host.setLinkText(action);
    }
  }

  @Override
  public boolean handleMessage (@NonNull Message msg) {
    if (msg.what == MSG_TICK) {
      refresh();
    }
    return true;
  }

  public void destroy () {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
  }

  // Actions

  /** Handles a failed code check. Returns true when it was PHONE_CODE_EXPIRED or AUTH_RESTART and the error is already shown. */
  public boolean onCodeCheckError (@Nullable TdApi.Error error) {
    if (onAuthRestart(error)) {
      return true;
    }
    if (destroyed || state == null || error == null || !PHONE_CODE_EXPIRED.equals(error.message)) {
      return false;
    }
    codeExpired = true;
    host.setHintText(Lang.getString(R.string.xgram_login_PhoneCodeExpired), true);
    refresh();
    return true;
  }

  /**
   * Handles a failed ResendAuthenticationCode. The server refuses a resend once the code has expired, and answers
   * SEND_CODE_UNAVAILABLE when every way was used, so tapping the same link again would only repeat the error.
   * Then the link falls back to sending the phone number again (expired) or to the help sheet.
   * Returns true when the error is already shown.
   */
  public boolean onResendError (@Nullable TdApi.Error error) {
    if (destroyed || state == null || error == null) {
      return false;
    }
    if (onAuthRestart(error)) {
      return true;
    }
    if (PHONE_CODE_EXPIRED.equals(error.message)) {
      codeExpired = true;
      resendUnavailable = true;
      host.setHintText(Lang.getString(R.string.xgram_login_PhoneCodeExpired), true);
      refresh();
      return true;
    }
    if (codeExpired || SEND_CODE_UNAVAILABLE.equals(error.message)) {
      resendUnavailable = true;
      refresh();
    }
    return false;
  }

  /**
   * AUTH_RESTART: the server dropped this login, so the old code and the next type are gone. Sends the phone number
   * again once, which starts a new login with a new code. A second AUTH_RESTART is shown as an error, and the link
   * then sends the number again on tap.
   */
  private boolean onAuthRestart (@Nullable TdApi.Error error) {
    if (destroyed || state == null || !LoginErrors.is(error, LoginErrors.AUTH_RESTART)) {
      return false;
    }
    codeExpired = true;
    resendUnavailable = true;
    refresh();
    // Another login request still running (a code check during a resend) would be aborted by a second one
    host.setHintText(Lang.getString(R.string.xgram_login_AuthRestart), true);
    if (authRestartHandled || inProgress || host.isBusy()) {
      return true;
    }
    authRestartHandled = true;
    if (sendPhoneNumberAgain()) { // offline or no number: the error above stays and the link sends it on tap
      host.setHintText(Lang.getString(R.string.xgram_login_AuthRestartNewCode), false);
    }
    return true;
  }

  /** The link under the code field was pressed. */
  public void onLinkClick () {
    if (destroyed || state == null || inProgress || host.isBusy()) {
      return;
    }
    TdApi.AuthenticationCodeType next = nextType();
    int left = next != null ? secondsLeft() : 0;
    if (next != null && left <= 0) {
      host.requestNextCode();
    } else if (next == null && codeExpired) {
      sendPhoneNumberAgain();
    } else {
      showHelp(next, left);
    }
  }

  private void showHelp (@Nullable TdApi.AuthenticationCodeType next, int secondsLeft) {
    List<Integer> ids = new ArrayList<>(3);
    List<String> titles = new ArrayList<>(3);
    List<Integer> colors = new ArrayList<>(3);
    List<Integer> icons = new ArrayList<>(3);

    if (next != null) {
      String action = actionText();
      ids.add(R.id.btn_xgramCodeNext);
      titles.add(secondsLeft > 0 ?
        Lang.getString(R.string.xgram_login_NextCodeIn, action, EmailCodeLinks.formatCountdown(secondsLeft)) :
        action);
      colors.add(secondsLeft > 0 ? ViewController.OptionColor.INACTIVE : ViewController.OptionColor.NORMAL);
      icons.add(R.drawable.baseline_replay_24);
    }
    if (QrLogin.canRequest(state)) {
      ids.add(R.id.btn_xgramQrLogin);
      titles.add(Lang.getString(R.string.xgram_login_QrButton));
      colors.add(ViewController.OptionColor.NORMAL);
      icons.add(R.drawable.xt3000_baseline_qrcode_scan_24);
    }
    if (next == null) {
      ids.add(R.id.btn_xgramCodeSupport);
      titles.add(Lang.getString(R.string.xgram_login_EmailSupport));
      colors.add(ViewController.OptionColor.NORMAL);
      icons.add(R.drawable.baseline_help_24);
    }

    CharSequence info = Lang.getString(next != null ? R.string.xgram_login_NoCodeWait : R.string.xgram_login_NoCodeHelp);
    controller.showOptions(info, toArray(ids), titles.toArray(new String[0]), toArray(colors), toArray(icons), (itemView, id) -> {
      if (id == R.id.btn_xgramCodeNext) {
        if (nextType() != null && secondsLeft() <= 0 && !inProgress && !host.isBusy()) {
          host.requestNextCode();
        }
      } else if (id == R.id.btn_xgramQrLogin) {
        host.requestQrLogin();
      } else if (id == R.id.btn_xgramCodeSupport) {
        host.emailSupport();
      }
      return true;
    });
  }

  private static int[] toArray (List<Integer> list) {
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      host.setLinkInProgress(inProgress);
    }
  }

  /**
   * An expired code with no other way to send it: send the same number again, as the phone screen does. TDLib accepts
   * SetAuthenticationPhoneNumber in WaitCode when no other login request is pending, and answers with a new WaitCode.
   */
  private boolean sendPhoneNumberAgain () {
    String number = state != null && state.codeInfo != null ? Strings.getNumber(state.codeInfo.phoneNumber) : null;
    if (StringUtils.isEmpty(number)) {
      host.setHintText(Lang.getString(R.string.xgram_login_PhoneCodeExpiredBack), true);
      return false;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return false;
    }
    setInProgress(true);
    tdlib.send(new TdApi.SetAuthenticationPhoneNumber("+" + number, tdlib.phoneNumberAuthenticationSettings(controller.context())), (ok, error) -> controller.runOnUiThreadOptional(() -> {
      if (destroyed) {
        return;
      }
      setInProgress(false);
      if (error != null) {
        host.setHintText(TD.toErrorString(error), true);
      } else {
        controller.showInfoTooltip(host.linkView(), Lang.getString(R.string.xgram_login_PhoneCodeSent));
      }
    }));
    return true;
  }
}
