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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.ui.PhoneController;

import me.vkryl.core.StringUtils;

/**
 * The "Telegram Premium required" login screen (AuthorizationStateWaitPremiumPurchase).
 *
 * A third-party app can't sell Telegram's store product, so the screen explains where to buy it and offers the ways out
 * TDLib allows from this state: QR login, another phone number, and the support email the server gave.
 */
public final class PremiumRequired {
  private PremiumRequired () { }

  /** The latest premium state of this tdlib: TDLib can send the state again with new values, and MainActivity keeps the screen. */
  @Nullable
  public static TdApi.AuthorizationStateWaitPremiumPurchase currentState (@NonNull Tdlib tdlib, @Nullable TdApi.AuthorizationStateWaitPremiumPurchase fallback) {
    TdApi.AuthorizationState state = tdlib.authorizationState();
    if (state instanceof TdApi.AuthorizationStateWaitPremiumPurchase) {
      return (TdApi.AuthorizationStateWaitPremiumPurchase) state;
    }
    return fallback;
  }

  public static CharSequence message (@NonNull ViewController<?> screen, @Nullable TdApi.AuthorizationStateWaitPremiumPurchase state) {
    if (state != null && state.premiumDayCount > 0) {
      return Lang.getMarkdownString(screen, R.string.xgram_login_PremiumRequiredDays, Lang.plural(R.string.xDays, state.premiumDayCount));
    }
    return Lang.getMarkdownString(screen, R.string.xgram_login_PremiumRequired);
  }

  public static boolean hasSupportEmail (@Nullable TdApi.AuthorizationStateWaitPremiumPurchase state) {
    return state != null && !StringUtils.isEmpty(state.supportEmailAddress);
  }

  /** Opens the mail app with the support address and subject from the server. */
  public static void sendSupportEmail (@NonNull Tdlib tdlib, @Nullable TdApi.AuthorizationStateWaitPremiumPurchase state) {
    if (!hasSupportEmail(state)) {
      return;
    }
    String subject = StringUtils.isEmpty(state.supportEmailSubject) ? Lang.getStringSecure(R.string.login_PremiumRequiredTitle) : state.supportEmailSubject;
    String phone = tdlib.authPhoneNumberFormatted();
    String metadata = U.getUsefulMetadata(tdlib);
    String text = StringUtils.isEmpty(phone) ?
      Lang.getStringSecure(R.string.xgram_login_PremiumSupportTextNoPhone, metadata) :
      Lang.getStringSecure(R.string.xgram_login_PremiumSupportText, phone, metadata);
    Intents.sendEmail(state.supportEmailAddress, subject, text);
  }

  /**
   * Goes back to the phone number screen of this login, where SetAuthenticationPhoneNumber is sent again (TDLib allows it
   * from this state). Login screens of this account between the two are dropped. If there is no phone screen below, a new
   * one is put under this screen first.
   */
  @UiThread
  public static void useDifferentNumber (@NonNull ViewController<?> screen, @NonNull Tdlib tdlib) {
    NavigationController navigation = screen.navigationController();
    if (navigation == null || screen.isStackLocked() || navigation.isAnimating()) {
      return;
    }
    int self = screen.stackSize() - 1;
    if (self < 0 || screen.stackItemAt(self) != screen) {
      return;
    }
    int phoneIndex = -1;
    for (int i = self - 1; i >= 0; i--) {
      ViewController<?> c = screen.stackItemAt(i);
      if (c == null || !c.isSameTdlib(tdlib) || !c.isUnauthorized()) {
        break;
      }
      if (c instanceof PhoneController) {
        phoneIndex = i;
        break;
      }
    }
    if (phoneIndex != -1) {
      for (int i = self - 1; i > phoneIndex; i--) {
        screen.destroyStackItemAt(i);
      }
    } else {
      PhoneController c = new PhoneController(screen.context(), tdlib);
      ViewController<?> below = self > 0 ? screen.stackItemAt(self - 1) : null;
      if (below != null && !(below.isSameTdlib(tdlib) && below.isUnauthorized())) {
        // An added account on top of a logged-in one: keep the back button and the existing-number check
        c.setIsAccountAdd(true);
      }
      navigation.insertController(c, self);
    }
    screen.navigateBack();
  }
}
