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
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * QR code login: the request, and the check that keeps one user from ending up in two account slots.
 *
 * Every request here goes to the tdlib of the account being logged in, never to another account.
 */
public final class QrLogin {
  private QrLogin () { }

  /** States from which TDLib accepts RequestQrCodeAuthentication (when no other login request is pending). */
  public static boolean canRequest (@Nullable TdApi.AuthorizationState state) {
    if (state == null) {
      return false;
    }
    switch (state.getConstructor()) {
      case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitPremiumPurchase.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  /** Users already logged in on this device on the same servers, so the server doesn't log one of them in again. */
  public static long[] otherUserIds (@NonNull Tdlib tdlib) {
    TdlibAccount self = tdlib.account();
    List<Long> ids = new ArrayList<>();
    for (TdlibAccount account : tdlib.context()) {
      if (account.id == self.id || account.isUnauthorized() || account.tdlibInstanceMode() != self.tdlibInstanceMode()) {
        continue;
      }
      long userId = account.getKnownUserId();
      if (userId != 0 && !ids.contains(userId)) {
        ids.add(userId);
      }
    }
    long[] result = new long[ids.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = ids.get(i);
    }
    return result;
  }

  /**
   * Asks TDLib for a QR code login on the screen's own tdlib. TDLib then switches to WaitOtherDeviceConfirmation and
   * MainActivity opens {@link QrLoginController}. {@code after} runs on the UI thread with null on success or the error.
   *
   * @return false when nothing was sent (offline or a state that doesn't allow it)
   */
  @UiThread
  public static boolean request (@NonNull ViewController<?> controller, @NonNull Tdlib tdlib, @NonNull RunnableData<TdApi.Error> after) {
    if (!tdlib.isUnauthorized()) {
      return false;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return false;
    }
    TdApi.AuthorizationState state = tdlib.authorizationState();
    if (state instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
      // Already waiting for a scan (MainActivity didn't show it, e.g. a reused slot): just show the code
      QrLoginController c = new QrLoginController(controller.context(), tdlib);
      c.setArguments((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) state);
      controller.navigateTo(c);
      return false;
    }
    if (state != null && !canRequest(state)) {
      return false;
    }
    final long[] otherUserIds = otherUserIds(tdlib);
    tdlib.awaitReadyOrWaitingForData(() ->
      tdlib.send(new TdApi.RequestQrCodeAuthentication(otherUserIds), (ok, error) ->
        controller.runOnUiThreadOptional(() -> after.runWithData(error))
      )
    );
    return true;
  }

  /**
   * A QR login skips the phone-number check that stops a user from being added twice. When a slot that is logging in
   * right now becomes ready as a user another slot already has, returns that other account; otherwise null.
   */
  @Nullable
  public static TdlibAccount findExistingAccount (@NonNull TdlibAccount account) {
    long userId = account.getKnownUserId();
    if (userId == 0) {
      userId = account.tdlib().myUserId();
    }
    if (userId == 0) {
      return null;
    }
    for (TdlibAccount other : account.context()) {
      if (other.id != account.id && !other.isUnauthorized() && other.tdlibInstanceMode() == account.tdlibInstanceMode() && other.getKnownUserId() == userId) {
        return other;
      }
    }
    return null;
  }

  /** Ends only the session that just logged in on this slot. The other slot of the same user has its own session and stays logged in. */
  public static void discardNewSession (@NonNull TdlibAccount account) {
    Tdlib tdlib = account.tdlib();
    if (tdlib.isAuthorized()) {
      tdlib.send(new TdApi.LogOut(), (ok, error) -> { });
    }
  }

  /** Cancels a QR login that is still waiting for a scan. The only way out of that state is LogOut on this same tdlib. */
  public static boolean cancelPending (@NonNull Tdlib tdlib) {
    TdApi.AuthorizationState state = tdlib.authorizationState();
    if (state != null && state.getConstructor() == TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR) {
      tdlib.send(new TdApi.LogOut(), (ok, error) -> { });
      return true;
    }
    return false;
  }
}
