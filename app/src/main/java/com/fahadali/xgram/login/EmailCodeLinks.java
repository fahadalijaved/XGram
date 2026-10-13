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
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * The links under the email-code login screen (PasswordController.MODE_CODE_EMAIL): resend the code, reset a login
 * email the user can't open any more, and go back to enter a different email while a new one is being set up.
 *
 * The screen owns the views and passes them in through {@link Host}. Everything here runs on the UI thread; TDLib
 * results are posted back with runOnUiThreadOptional. Every request goes to the screen's own tdlib, which is the
 * account being logged in, never another account.
 */
public final class EmailCodeLinks implements Handler.Callback {
  public interface Host {
    /** Text of the link under the code field. */
    void setLinkText (@Nullable CharSequence text);
    /** The grey line above the link; null hides it. */
    void setWaitText (@Nullable CharSequence text);
    void setHintText (@NonNull CharSequence text, boolean isError);
    void setLinkInProgress (boolean inProgress);
    @NonNull View linkView ();
    /** Goes back to the email screen, if it is right below this one. */
    void useDifferentEmail ();
  }

  private static final long RESEND_COOLDOWN_MS = 30_000L;
  private static final int MSG_TICK = 1;
  private static final String UNKNOWN_EMAIL = "<unknown>";

  private final ViewController<?> controller;
  private final Tdlib tdlib;
  private final Host host;
  private final boolean fromEmailSetup;
  private final Handler handler = new Handler(Looper.getMainLooper(), this);

  private @Nullable TdApi.AuthorizationStateWaitEmailCode state;
  private long stateReceivedAt;
  private long resendAvailableAt;
  private boolean inProgress;
  private boolean destroyed;

  public EmailCodeLinks (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull Host host, boolean fromEmailSetup, @Nullable TdApi.AuthorizationStateWaitEmailCode state) {
    this.controller = controller;
    this.tdlib = tdlib;
    this.host = host;
    this.fromEmailSetup = fromEmailSetup;
    this.state = state;
    this.stateReceivedAt = SystemClock.elapsedRealtime();
  }

  // Hint

  private static boolean hasKnownPattern (@Nullable TdApi.AuthorizationStateWaitEmailCode state) {
    String pattern = state != null && state.codeInfo != null ? state.codeInfo.emailAddressPattern : null;
    return !StringUtils.isEmpty(pattern) && !UNKNOWN_EMAIL.equals(pattern);
  }

  /**
   * The text under the code field. A code for a new login email (the email screen came first) and a code for the
   * account's existing login email (it has a reset state) read differently; anything else keeps Telegram X's text.
   */
  public static CharSequence buildHint (@NonNull TdApi.AuthorizationStateWaitEmailCode state, boolean fromEmailSetup) {
    boolean known = hasKnownPattern(state);
    if (fromEmailSetup) {
      return known ?
        Lang.getStringBold(R.string.xgram_login_SentSetupEmailCode, state.codeInfo.emailAddressPattern) :
        Lang.getString(R.string.xgram_login_SentSetupEmailCodeNoAddress);
    }
    if (!known) {
      return Lang.getString(R.string.xgram_login_SentEmailCodeNoAddress);
    }
    if (state.emailAddressResetState != null) {
      return Lang.getStringBold(R.string.xgram_login_SentAccountEmailCode, state.codeInfo.emailAddressPattern);
    }
    return Lang.getStringBold(R.string.SentEmailCode, state.codeInfo.emailAddressPattern);
  }

  public CharSequence buildHint () {
    return state != null ? buildHint(state, fromEmailSetup) : "";
  }

  // State

  /** Called for every new authorization state. Returns true when it was an email-code state and the screen should refresh its hint. */
  public boolean onStateChanged (@Nullable TdApi.AuthorizationState newState) {
    if (destroyed) {
      return false;
    }
    if (newState != null && newState.getConstructor() == TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR) {
      this.state = (TdApi.AuthorizationStateWaitEmailCode) newState;
      this.stateReceivedAt = SystemClock.elapsedRealtime();
      refresh();
      return true;
    }
    // The login moved on (a phone code after a reset, a password, ...). MainActivity opens that screen; stop here.
    this.state = null;
    handler.removeMessages(MSG_TICK);
    host.setWaitText(null);
    host.setLinkText(null);
    return false;
  }

  private int resendSecondsLeft () {
    long left = resendAvailableAt - SystemClock.elapsedRealtime();
    return left > 0 ? (int) ((left + 999L) / 1000L) : 0;
  }

  private int pendingResetSecondsLeft () {
    if (state == null || !(state.emailAddressResetState instanceof TdApi.EmailAddressResetStatePending)) {
      return -1;
    }
    int resetIn = ((TdApi.EmailAddressResetStatePending) state.emailAddressResetState).resetIn;
    long passed = (SystemClock.elapsedRealtime() - stateReceivedAt) / 1000L;
    return (int) Math.max(0, resetIn - passed);
  }

  private boolean hasMenu () {
    return state != null && (state.emailAddressResetState != null || fromEmailSetup);
  }

  /** Rebuilds the link and the reset countdown from the current state. */
  public void refresh () {
    handler.removeMessages(MSG_TICK);
    if (destroyed || state == null) {
      return;
    }
    boolean needTick = false;
    int resendLeft = resendSecondsLeft();
    if (state.emailAddressResetState != null) {
      host.setLinkText(Lang.getString(R.string.xgram_login_CantAccessEmailLink));
    } else if (fromEmailSetup) {
      host.setLinkText(Lang.getString(R.string.DidNotGetTheCode));
    } else if (resendLeft > 0) {
      host.setLinkText(Lang.getString(R.string.xgram_login_ResendEmailCodeIn, formatCountdown(resendLeft)));
      needTick = true;
    } else {
      host.setLinkText(Lang.getString(R.string.xgram_login_ResendEmailCode));
    }
    int resetLeft = pendingResetSecondsLeft();
    if (resetLeft > 0) {
      host.setWaitText(Lang.getString(R.string.xgram_login_ResetEmailPending, formatCountdown(resetLeft)));
      needTick = true;
    } else if (resetLeft == 0) {
      host.setWaitText(Lang.getString(R.string.xgram_login_ResetEmailReady));
    } else {
      host.setWaitText(null);
    }
    if (needTick) {
      handler.sendEmptyMessageDelayed(MSG_TICK, 1000L);
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

  /** "2 days 03:04:05", "3:04:05" or "4:05". */
  public static String formatCountdown (int seconds) {
    seconds = Math.max(0, seconds);
    int days = seconds / 86400;
    int hours = (seconds % 86400) / 3600;
    int minutes = (seconds % 3600) / 60;
    int secs = seconds % 60;
    StringBuilder b = new StringBuilder();
    if (days > 0) {
      b.append(Lang.plural(R.string.xDays, days)).append(' ');
      appendTwoDigits(b, hours).append(':');
      appendTwoDigits(b, minutes);
    } else if (hours > 0) {
      b.append(hours).append(':');
      appendTwoDigits(b, minutes);
    } else {
      b.append(minutes);
    }
    b.append(':');
    appendTwoDigits(b, secs);
    return b.toString();
  }

  private static StringBuilder appendTwoDigits (StringBuilder b, int value) {
    if (value < 10) {
      b.append('0');
    }
    return b.append(value);
  }

  // Actions

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      host.setLinkInProgress(inProgress);
    }
  }

  private boolean checkOnline () {
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return false;
    }
    return true;
  }

  /** The link under the code field was pressed. */
  public void onLinkClick () {
    if (destroyed || state == null || inProgress) {
      return;
    }
    if (!hasMenu()) {
      resend();
      return;
    }
    List<Integer> ids = new ArrayList<>(3);
    List<String> titles = new ArrayList<>(3);
    List<Integer> colors = new ArrayList<>(3);
    List<Integer> icons = new ArrayList<>(3);

    int resendLeft = resendSecondsLeft();
    ids.add(R.id.btn_xgramEmailResend);
    titles.add(resendLeft > 0 ?
      Lang.getString(R.string.xgram_login_ResendEmailCodeIn, formatCountdown(resendLeft)) :
      Lang.getString(R.string.xgram_login_ResendEmailCode));
    colors.add(resendLeft > 0 ? ViewController.OptionColor.INACTIVE : ViewController.OptionColor.NORMAL);
    icons.add(R.drawable.baseline_replay_24);

    TdApi.EmailAddressResetState resetState = state.emailAddressResetState;
    if (resetState instanceof TdApi.EmailAddressResetStateAvailable) {
      int waitPeriod = ((TdApi.EmailAddressResetStateAvailable) resetState).waitPeriod;
      ids.add(R.id.btn_xgramEmailReset);
      titles.add(waitPeriod <= 0 ?
        Lang.getString(R.string.xgram_login_ResetEmailNow) :
        Lang.getString(R.string.xgram_login_ResetEmailAfter, Lang.getDuration(waitPeriod)));
      colors.add(ViewController.OptionColor.RED);
      icons.add(R.drawable.baseline_delete_24);
    } else if (resetState instanceof TdApi.EmailAddressResetStatePending) {
      ids.add(R.id.btn_xgramEmailResetCheck);
      titles.add(Lang.getString(R.string.xgram_login_ResetEmailCheck));
      colors.add(ViewController.OptionColor.NORMAL);
      icons.add(R.drawable.baseline_history_24);
    }

    if (fromEmailSetup) {
      ids.add(R.id.btn_xgramEmailChange);
      titles.add(Lang.getString(R.string.xgram_login_UseDifferentEmail));
      colors.add(ViewController.OptionColor.NORMAL);
      icons.add(R.drawable.baseline_alternate_email_24);
    }

    CharSequence info = resetState != null ? Lang.getString(R.string.xgram_login_CantAccessEmail) : null;
    controller.showOptions(info, toArray(ids), titles.toArray(new String[0]), toArray(colors), toArray(icons), (itemView, id) -> {
      if (id == R.id.btn_xgramEmailResend) {
        resend();
      } else if (id == R.id.btn_xgramEmailReset) {
        confirmReset();
      } else if (id == R.id.btn_xgramEmailResetCheck) {
        reset(true);
      } else if (id == R.id.btn_xgramEmailChange) {
        host.useDifferentEmail();
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

  private void resend () {
    if (destroyed || state == null || inProgress || resendSecondsLeft() > 0 || !checkOnline()) {
      return;
    }
    setInProgress(true);
    tdlib.send(new TdApi.ResendAuthenticationCode(new TdApi.ResendCodeReasonUserRequest()), (ok, error) -> controller.runOnUiThreadOptional(() -> {
      if (destroyed) {
        return;
      }
      setInProgress(false);
      if (error != null) {
        int floodSeconds = EmailLoginErrors.floodSeconds(error);
        if (floodSeconds > 0) {
          resendAvailableAt = SystemClock.elapsedRealtime() + floodSeconds * 1000L;
          host.setHintText(EmailLoginErrors.toString(error), true);
        } else if (!fromEmailSetup) {
          // In WaitEmailCode TDLib re-sends to the email given in WaitEmailAddress. For the account's own login email it
          // has none, so the server rejects it (e.g. EMAIL_INVALID). Don't tell the user their email is wrong.
          host.setHintText(Lang.getString(R.string.xgram_login_ResendEmailCodeFailed), true);
        } else {
          host.setHintText(EmailLoginErrors.toString(error), true);
        }
      } else {
        // Telegram limits how often a code can be sent; a short pause keeps users away from FLOOD_WAIT.
        resendAvailableAt = SystemClock.elapsedRealtime() + RESEND_COOLDOWN_MS;
        controller.showInfoTooltip(host.linkView(), Lang.getString(R.string.xgram_login_EmailCodeResent));
      }
      refresh();
    }));
  }

  private void confirmReset () {
    if (destroyed || state == null || !(state.emailAddressResetState instanceof TdApi.EmailAddressResetStateAvailable)) {
      return;
    }
    int waitPeriod = ((TdApi.EmailAddressResetStateAvailable) state.emailAddressResetState).waitPeriod;
    CharSequence info = waitPeriod <= 0 ?
      Lang.getString(R.string.xgram_login_ResetEmailNowInfo) :
      Lang.getString(R.string.xgram_login_ResetEmailAfterInfo, Lang.getDuration(waitPeriod));
    controller.showOptions(info,
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.xgram_login_ResetEmailConfirm), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          reset(false);
        }
        return true;
      }
    );
  }

  /**
   * Sends ResetAuthenticationEmailAddress on this screen's tdlib. Success moves the login to a phone or app code,
   * which MainActivity opens. TASK_ALREADY_EXISTS means the reset is waiting; TDLib then switches this state to
   * Pending by itself and {@link #onStateChanged} shows the countdown.
   */
  private void reset (boolean isCheck) {
    if (destroyed || state == null || state.emailAddressResetState == null || inProgress || !checkOnline()) {
      return;
    }
    setInProgress(true);
    tdlib.send(new TdApi.ResetAuthenticationEmailAddress(), (ok, error) -> controller.runOnUiThreadOptional(() -> {
      if (destroyed) {
        return;
      }
      setInProgress(false);
      if (error == null) {
        return;
      }
      if (EmailLoginErrors.is(error, EmailLoginErrors.TASK_ALREADY_EXISTS)) {
        int left = pendingResetSecondsLeft();
        CharSequence text = isCheck && left > 0 ?
          Lang.getString(R.string.xgram_login_ResetEmailNotYet, formatCountdown(left)) :
          Lang.getString(R.string.xgram_login_ResetEmailRequested);
        controller.showInfoTooltip(host.linkView(), text);
      } else {
        host.setHintText(EmailLoginErrors.toString(error), true);
      }
      refresh();
    }));
  }
}
