/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.gms.safetynet.SafetyNet;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.AuthorizationListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.NoUnderlineClickableSpan;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ProgressComponentView;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

// TODO: Rework properly from scratch with the app redesign.
public class PasswordController extends ViewController<PasswordController.Args> implements View.OnClickListener, FactorAnimator.Target, MaterialEditTextGroup.EmptyListener, MaterialEditTextGroup.DoneListener, MaterialEditTextGroup.TextChangeListener, AuthorizationListener, Handler.Callback, org.thunderdog.challegram.navigation.Menu /* XGram: QR login button */ {
  public static final int MODE_EDIT = 0;
  public static final int MODE_NEW = 1;
  public static final int MODE_UNLOCK_EDIT = 2;
  public static final int MODE_EMAIL_RECOVERY = 3;
  public static final int MODE_2FA_RECOVERY_EMAIL_CHANGE = 4;
  public static final int MODE_LOGIN = 5;
  public static final int MODE_LOGIN_EMAIL_RECOVERY = 6;
  public static final int MODE_CODE = 7;
  public static final int MODE_CODE_CHANGE = 8;
  public static final int MODE_CODE_PHONE_CONFIRM = 9;
  public static final int MODE_TRANSFER_OWNERSHIP_CONFIRM = 10;
  public static final int MODE_CONFIRM = 11;
  public static final int MODE_CODE_EMAIL = 12;
  public static final int MODE_EMAIL_LOGIN = 13;
  public static final int MODE_CUSTOM_CONFIRM = 14;
  public static final int MODE_LOGIN_EMAIL_CHANGE = 15;

  private final Handler handler = new Handler(this);

  public static class Args {
    public final int mode;
    public final TdApi.PasswordState state;
    public final TdApi.AuthorizationState authState;
    public @Nullable String phoneNumber;
    public @Nullable CustomConfirmDelegate confirmDelegate;

    public Args (int mode, TdApi.PasswordState state) {
      this.mode = mode;
      this.state = state;
      this.authState = null;
    }

    public Args (int mode, TdApi.AuthorizationStateWaitPassword state) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
    }

    public Args (int mode, TdApi.AuthorizationStateWaitCode state, @NonNull String phoneNumber) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
      this.phoneNumber = phoneNumber;
    }

    public Args (int mode, TdApi.AuthorizationStateWaitEmailCode state) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
      setEmail(state.codeInfo.emailAddressPattern);
      setCodeLength(state.codeInfo.length);
    }

    public Args (int mode, TdApi.AuthorizationStateWaitEmailAddress state) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
    }

    public Args (int mode, TdApi.AuthenticationCodeInfo codeInfo, @NonNull String phoneNumber) {
      this.mode = mode;
      this.state = null;
      this.authState = new TdApi.AuthorizationStateWaitCode(codeInfo);
      this.phoneNumber = phoneNumber;
    }

    public @Nullable String email;

    public Args setEmail (@Nullable String email) {
      this.email = email;
      return this;
    }

    public @Nullable String oldPassword;

    public Args setOldPassword (@Nullable String oldPassword) {
      this.oldPassword = oldPassword;
      return this;
    }

    public int codeLength;

    public Args setCodeLength (int length) {
      this.codeLength = length;
      return this;
    }

    public @Nullable String hash;

    public Args setHash (@Nullable String hash) {
      this.hash = hash;
      return this;
    }

    public @Nullable RunnableData<String> onSuccessListener;

    public Args setSuccessListener (@Nullable RunnableData<String> onSuccessListener) {
      this.onSuccessListener = onSuccessListener;
      return this;
    }

    public Args setConfirmDelegate (@Nullable CustomConfirmDelegate confirmDelegate) {
      this.confirmDelegate = confirmDelegate;
      return this;
    }

    // XGram: true when the email-code screen follows the "add a login email" screen
    public boolean fromEmailSetup;

    public Args setFromEmailSetup (boolean fromEmailSetup) {
      this.fromEmailSetup = fromEmailSetup;
      return this;
    }
  }

  private int mode;

  private @Nullable TdApi.PasswordState state;
  private @Nullable TdApi.AuthorizationState authState;
  private String formattedPhone;

  public PasswordController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean isUnauthorized () {
    switch (mode) {
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_LOGIN:
      case MODE_CODE:
      case MODE_CODE_EMAIL:
      case MODE_EMAIL_LOGIN:
        return true;
    }
    return false;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.state = args.state;
    this.authState = args.authState;
    this.formattedPhone = args.phoneNumber;
    this.confirmDelegate = args.confirmDelegate;
    fillPhoneFromAuthState(); // XGram
  }

  // XGram: after a cold start the number typed on the phone screen is gone, so hints read "****". TDLib still knows it.
  private void fillPhoneFromAuthState () {
    if (mode == MODE_CODE && StringUtils.isEmpty(formattedPhone)) {
      formattedPhone = com.fahadali.xgram.login.PhoneCodeLinks.formatPhone(authState);
    }
  }

  public interface CustomConfirmDelegate {
    default CharSequence getName () {
      return Lang.getString(R.string.EnterPassword);
    }
    boolean needNext ();
    void onPasswordConfirmed (ViewController<?> c, String password);
  }

  private CustomConfirmDelegate confirmDelegate;

  /** XGram M3E: the page titles in sentence case while the language is Telegram X's English (its own are title case). */
  private @Nullable String xgramSentenceName () {
    switch (mode) {
      case MODE_EDIT: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.ChangePassword, R.string.XgramR5SecurityChangePassword);
      case MODE_NEW: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.YourPassword, R.string.XgramR5SecurityYourPassword);
      case MODE_EMAIL_LOGIN: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.YourEmail, R.string.XgramR5SecurityYourEmail);
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.EnterPassword, R.string.XgramR5SecurityEnterPassword);
      case MODE_TRANSFER_OWNERSHIP_CONFIRM: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.TransferOwnershipPasswordAlert, R.string.XgramR5SecurityTransferOwnership);
      case MODE_LOGIN: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.TwoStepVerification, R.string.XgramR5SecurityTwoStepVerification);
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.PasswordRecovery, R.string.XgramR5SecurityPasswordRecovery);
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_EMAIL: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.ConfirmationCode, R.string.XgramR5SecurityConfirmationCode);
      case MODE_CODE_PHONE_CONFIRM: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.CancelAccountReset, R.string.XgramR5SecurityCancelAccountReset);
      case MODE_2FA_RECOVERY_EMAIL_CHANGE: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.ChangeRecoveryEmail, R.string.XgramR5SecurityChangeRecoveryEmail);
      case MODE_LOGIN_EMAIL_CHANGE: return com.fahadali.xgram.appearance.XgramR5Security.sentence(R.string.LoginEmail, R.string.XgramR5SecurityLoginEmail);
    }
    return null;
  }

  @Override
  public CharSequence getName () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      String name = xgramSentenceName();
      if (name != null) {
        return name;
      }
    }
    switch (mode) {
      case MODE_EDIT: {
        return Lang.getString(R.string.ChangePassword);
      }
      case MODE_NEW: {
        return Lang.getString(R.string.YourPassword);
      }
      case MODE_EMAIL_LOGIN: {
        return Lang.getString(R.string.YourEmail);
      }
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        return Lang.getString(R.string.EnterPassword);
      }
      case MODE_CUSTOM_CONFIRM: {
        return confirmDelegate.getName();
      }
      case MODE_TRANSFER_OWNERSHIP_CONFIRM: {
        return Lang.getString(R.string.TransferOwnershipPasswordAlert);
      }
      case MODE_LOGIN: {
        return Lang.getString(R.string.TwoStepVerification);
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        return Lang.getString(R.string.PasswordRecovery);
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_EMAIL: {
        return Lang.getString(R.string.ConfirmationCode);
      }
      case MODE_CODE_PHONE_CONFIRM: {
        return Lang.getString(R.string.CancelAccountReset);
      }
      case MODE_2FA_RECOVERY_EMAIL_CHANGE: {
        return Lang.getString(R.string.ChangeRecoveryEmail);
      }
      case MODE_LOGIN_EMAIL_CHANGE: {
        return Lang.getString(R.string.LoginEmail);
      }
    }
    return null; // UI.getString(mode == MODE_EMAIL_RECOVERY ? R.string.PasswordRecovery : mode == MODE_UNLOCK_EDIT ? R.string.EnterPassword : R.string.YourPassword);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  // XGram: "Log in with QR code" in the header of the code and email login screens, a way out when no code arrives.
  // Not on the 2FA password screen: a QR login of an account with a password ends at that same password step.
  private boolean isQrLoginMode () {
    return mode == MODE_CODE || mode == MODE_CODE_EMAIL || mode == MODE_EMAIL_LOGIN;
  }

  @Override
  protected int getMenuId () {
    return isQrLoginMode() ? R.id.menu_xgramQrLogin : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, android.widget.LinearLayout menu) {
    if (id == R.id.menu_xgramQrLogin) {
      header.addButton(menu, R.id.btn_xgramQrLogin, R.drawable.xt3000_baseline_qrcode_scan_24, ColorId.headerIcon, this, Screen.dp(48f));
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.btn_xgramQrLogin) {
      requestQrLogin();
    }
  }

  private boolean qrLoginInProgress;

  private void requestQrLogin () {
    // TDLib refuses it while a code check or another login request is pending
    if (!isQrLoginMode() || inProgress || inRecoveryProgress || qrLoginInProgress) {
      return;
    }
    if (com.fahadali.xgram.login.QrLogin.request(this, tdlib, error -> {
      qrLoginInProgress = false;
      if (error != null) {
        setHintText(TD.toErrorString(error), true);
      }
    })) {
      qrLoginInProgress = true;
    }
  }

  @Override
  public int getId () {
    switch (mode) {
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_EMAIL:
        return R.id.controller_code;
      case MODE_EMAIL_RECOVERY:
        return R.id.controller_passwordRecovery;
      case MODE_LOGIN_EMAIL_RECOVERY:
        return R.id.controller_loginPassword;
    }
    return R.id.controller_password;
  }

  private MaterialEditTextGroup editText;
  private CircleButton nextButton;
  private TextView forgotView;
  private TextView cancelResetView;
  private TextView resetWaitView;
  private @Nullable ProgressComponentView progressView;
  private TextView hintView;

  public int getMode () {
    return mode;
  }

  private int getDoneIcon () {
    switch (mode) {
      case MODE_2FA_RECOVERY_EMAIL_CHANGE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
        return R.drawable.baseline_check_24;
      case MODE_CUSTOM_CONFIRM:
        return confirmDelegate.needNext() ? R.drawable.baseline_arrow_forward_24 : R.drawable.baseline_check_24;
      case MODE_LOGIN_EMAIL_CHANGE:
        return R.drawable.baseline_arrow_forward_24;
    }
    return R.drawable.baseline_arrow_forward_24;
  }

  private void updateAuthInputType () {
    switch (mode) {
      case MODE_2FA_RECOVERY_EMAIL_CHANGE:
      case MODE_LOGIN_EMAIL_CHANGE:
      case MODE_EMAIL_LOGIN: {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        break;
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_EMAIL: {
        TdApi.AuthenticationCodeType codeType = authenticationCodeType();
        if (codeType == null) {
          codeType = new TdApi.AuthenticationCodeTypeSms();
        }
        switch (codeType.getConstructor()) {
          // Digit-only
          case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeSms.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeFragment.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeFirebaseAndroid.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeFirebaseIos.CONSTRUCTOR:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
            break;
          // Word-based
          case TdApi.AuthenticationCodeTypeSmsWord.CONSTRUCTOR:
          case TdApi.AuthenticationCodeTypeSmsPhrase.CONSTRUCTOR:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            break;
          default:
            Td.assertAuthenticationCodeType_6b7089f4();
            throw Td.unsupported(codeType);
        }
        break;
      }
      default: {
        // editText.getEditText().setInputType(InputType.TYPE_PASSWORD);
        // editText.getEditText().setIsPassword();
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.getEditText().setIsPassword(true);
        break;
      }
    }
  }

  // XGram M3E (round 5): the code and password pages as one column of at most 480dp under the app bar, with no
  // offset worked out from a phone's height: the page title in headlineMedium (the app bar's small title fades in
  // once it scrolls away), the hint as bodyMedium supporting text, the filled field (a visibility toggle on password
  // fields) and the links as M3 text buttons, the wait for a reset in bodyMedium. Errors stay in the supporting text.

  private @Nullable View xgramLinks;
  private @Nullable TextView xgramHeadline;

  private void xgramLayout (Context context, FrameLayoutFix contentView) {
    contentView.removeView(editText);
    contentView.removeView(hintView);
    if (xgramLinks != null) {
      contentView.removeView(xgramLinks);
    }

    android.widget.ScrollView scroll = new android.widget.ScrollView(context);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    FrameLayoutFix wrap = new FrameLayoutFix(context);
    scroll.addView(wrap, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    android.widget.LinearLayout column = new android.widget.LinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        final int max = Screen.dp(480f);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(widthMeasureSpec) > max) {
          widthMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    column.setOrientation(android.widget.LinearLayout.VERTICAL);
    column.setPadding(Screen.dp(16f), Screen.dp(24f), Screen.dp(16f), Screen.dp(104f)); // the FAB stays clear of the links
    wrap.addView(column, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    final boolean rtl = Lang.rtl();
    final int startGravity = rtl ? Gravity.RIGHT : Gravity.LEFT;

    xgramHeadline = new NoScrollTextView(context);
    com.fahadali.xgram.appearance.XgramExpressive.applyText(xgramHeadline, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_M, false);
    xgramHeadline.setText(getName());
    xgramHeadline.setGravity(startGravity);
    xgramHeadline.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
    addThemeTextColorListener(xgramHeadline, ColorId.text);
    column.addView(xgramHeadline, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    com.fahadali.xgram.appearance.XgramExpressive.applyText(hintView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
    hintView.setGravity(startGravity);
    android.widget.LinearLayout.LayoutParams hintParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    hintParams.topMargin = Screen.dp(8f);
    column.addView(hintView, hintParams);

    android.widget.LinearLayout.LayoutParams fieldParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    fieldParams.topMargin = Screen.dp(16f);
    column.addView(editText, fieldParams);
    editText.applyRtl(rtl);
    if ((editText.getEditText().getInputType() & android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
      com.fahadali.xgram.appearance.XgramR5Security.attachVisibilityToggle(editText);
    }

    if (xgramLinks != null) {
      for (TextView link : new TextView[] {forgotView, cancelResetView}) {
        com.fahadali.xgram.appearance.XgramM3Controls.styleButton(link);
        link.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
        link.setGravity(Gravity.CENTER_VERTICAL | startGravity);
        link.setFontFeatureSettings("tnum"); // the resend countdown keeps its width
      }
      com.fahadali.xgram.appearance.XgramExpressive.applyText(resetWaitView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      resetWaitView.setFontFeatureSettings("tnum");
      resetWaitView.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), Screen.dp(8f));
      android.widget.LinearLayout.LayoutParams linksParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      linksParams.topMargin = Screen.dp(16f);
      linksParams.gravity = startGravity;
      linksParams.leftMargin = linksParams.rightMargin = -Screen.dp(12f); // the text buttons' labels line up with the column
      column.addView(xgramLinks, linksParams);
    }
    contentView.addView(scroll, 0, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // The small title in the app bar is the headline's: it fades in as the headline scrolls away
    HeaderView.xgramSetTitleAlpha(this, 0f);
    scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
      if (xgramHeadline != null) {
        HeaderView.xgramSetTitleAlpha(this, (scroll.getScrollY() - xgramHeadline.getBottom() + Screen.dp(16f)) / (float) Screen.dp(24f));
      }
    });
  }

  @Override
  protected View onCreateView (Context context) {
    final FrameLayoutFix contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);

    final int topMargin = (Screen.smallestActualSide() - HeaderView.getSize(false) - Screen.dp(175f)) / 2;

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
    params.topMargin = topMargin;
    params.leftMargin = Screen.dp(16f);
    params.rightMargin = Screen.dp(16f);

    editText = new MaterialEditTextGroup(context, tdlib);
    editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
    editText.addThemeListeners(this);
    editText.setDoneListener(this);
    editText.setEmptyListener(this);
    editText.setTextListener(this);
    updateAuthInputType();

    switch (mode) {
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        editText.setHint(R.string.EnterCode);
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_EMAIL: {
        editText.setHint(R.string.login_Code);
        break;
      }
      case MODE_EDIT: {
        editText.setHint(R.string.EnterANewPassword);
        break;
      }
      case MODE_EMAIL_LOGIN: {
        editText.setHint(R.string.EnterEmail);
        break;
      }
      case MODE_2FA_RECOVERY_EMAIL_CHANGE: {
        editText.setHint(R.string.EnterANewEmail);
        break;
      }
      case MODE_LOGIN_EMAIL_CHANGE: {
        editText.setHint(R.string.ChangeEmailHint);
        break;
      }
      case MODE_NEW: {
        editText.setHint(R.string.EnterAPassword);
        break;
      }
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CUSTOM_CONFIRM:
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        if (state != null && state.passwordHint != null && !state.passwordHint.isEmpty()) {
          editText.setHint(Lang.getString(R.string.Hint, state.passwordHint));
        } else {
          editText.setHint(R.string.EnterAPassword);
        }
        break;
      }
      case MODE_LOGIN: {
        if (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR && !StringUtils.isEmpty(((TdApi.AuthorizationStateWaitPassword) authState).passwordHint)) {
          editText.setHint(Lang.getString(R.string.Hint, ((TdApi.AuthorizationStateWaitPassword) authState).passwordHint));
        } else {
          editText.setHint(R.string.EnterAPassword);
        }
        break;
      }
    }
    editText.setLayoutParams(params);
    contentView.addView(editText);

    int padding = Screen.dp(4f);
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    nextButton = new CircleButton(context);
    addThemeInvalidateListener(nextButton);
    nextButton.setId(R.id.btn_done);
    nextButton.init(getDoneIcon(), 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    nextButton.setOnClickListener(this);
    nextButton.setLayoutParams(params);
    nextButton.setAlpha(0f);
    nextButton.setScaleX(.6f);
    nextButton.setScaleY(.6f);
    contentView.addView(nextButton);

    CharSequence hint = null;

    forgotView = new NoScrollTextView(context);
    forgotView.setId(R.id.btn_forgotPassword);
    forgotView.setTextColor(Theme.getColor(ColorId.textNeutral));
    addThemeTextColorListener(forgotView, ColorId.textNeutral);
    forgotView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    forgotView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
    forgotView.setOnClickListener(this);
    forgotView.setAlpha(0f);
    Views.setClickable(forgotView);

    cancelResetView = new NoScrollTextView(context);
    cancelResetView.setId(R.id.btn_cancelReset);
    cancelResetView.setTextColor(Theme.getColor(ColorId.textNeutral));
    addThemeTextColorListener(cancelResetView, ColorId.textNeutral);
    cancelResetView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    cancelResetView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(6f));
    cancelResetView.setOnClickListener(this);
    Views.setClickable(cancelResetView);
    cancelResetView.setAlpha(0f);
    cancelResetView.setVisibility(View.GONE);

    resetWaitView = new NoScrollTextView(context);
    resetWaitView.setId(R.id.btn_cancelResetWait);
    resetWaitView.setTextColor(Theme.textDecentColor());
    addThemeTextColorListener(resetWaitView, ColorId.textLight);
    resetWaitView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    resetWaitView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(6f));
    resetWaitView.setAlpha(0f);
    resetWaitView.setVisibility(View.GONE);

    switch (mode) {
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CUSTOM_CONFIRM:
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT:
      case MODE_LOGIN: {
        updatePasswordResetTextViews();
        @StringRes int res;
        switch (mode) {
          case MODE_TRANSFER_OWNERSHIP_CONFIRM:
            res = R.string.TransferOwnershipPasswordAlertHint;
            break;
          case MODE_CUSTOM_CONFIRM:
            res = R.string.ConfirmPasswordAlertHint;
            break;
          default:
            res = R.string.LoginPasswordText;
            break;
        }
        hint = Lang.getString(res);
        break;
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        final String email = getArguments() != null ? getArguments().email : null;
        if (!StringUtils.isEmpty(email)) {
          hint = Lang.getStringBold(R.string.RecoveryCodeSent, email);
          forgotView.setText(Lang.getString(R.string.HavingTroubleAccessing, email));
        } else {
          hint = Lang.getString(R.string.RecoveryCodeSentEmailUnknown);
          forgotView.setText(Lang.getString(R.string.RestoreEmailTroubleUnknown));
        }
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        if (mode == MODE_CODE) {
          // XGram: the next code type with a countdown, a help sheet when there is none, and a new code after it expires
          phoneCodeLinks = newPhoneCodeLinks((TdApi.AuthorizationStateWaitCode) authState);
          phoneCodeLinks.refresh();
        } else if (hasNextCodeType()) {
          forgotView.setText(Lang.getString(R.string.DidNotGetTheCode));
        }
        hint = getCodeHint(((TdApi.AuthorizationStateWaitCode) authState).codeInfo.type, formattedPhone);
        break;
      }
      case MODE_CODE_EMAIL: {
        // XGram: resend / reset / "use a different email" links, and a hint that tells a new login email from the account's own
        emailCodeLinks = newEmailCodeLinks((TdApi.AuthorizationStateWaitEmailCode) authState);
        hint = emailCodeLinks.buildHint();
        emailCodeLinks.refresh();
        break;
      }
      case MODE_EMAIL_LOGIN: {
        hint = Lang.getString(R.string.xgram_login_SetupEmailInfo); // XGram: say why Telegram wants an email
        break;
      }
      case MODE_2FA_RECOVERY_EMAIL_CHANGE: {
        hint = Lang.getString(R.string.YourEmailInfo);
        break;
      }
      case MODE_LOGIN_EMAIL_CHANGE: {
        hint = Lang.getMarkdownString(this, R.string.ChangeEmailInfo);
        break;
      }
    }

    if (mode == MODE_TRANSFER_OWNERSHIP_CONFIRM || mode == MODE_UNLOCK_EDIT || mode == MODE_CUSTOM_CONFIRM || mode == MODE_CONFIRM || mode == MODE_LOGIN || mode == MODE_CODE || mode == MODE_CODE_CHANGE || mode == MODE_CODE_PHONE_CONFIRM || mode == MODE_CODE_EMAIL) {
      RelativeLayout forgotWrap = new RelativeLayout(context);
      forgotWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

      RelativeLayout.LayoutParams rp;

      rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      forgotView.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(12f), Screen.dp(16f));
      rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      forgotView.setLayoutParams(rp);
      forgotWrap.addView(forgotView);

      rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      rp.addRule(RelativeLayout.ABOVE, R.id.btn_forgotPassword);
      cancelResetView.setLayoutParams(rp);
      forgotWrap.addView(cancelResetView);

      rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      rp.addRule(RelativeLayout.ABOVE, R.id.btn_forgotPassword);
      resetWaitView.setLayoutParams(rp);
      forgotWrap.addView(resetWaitView);

      rp = new RelativeLayout.LayoutParams(Screen.dp(16f), Screen.dp(16f));
      rp.addRule(RelativeLayout.CENTER_VERTICAL);
      rp.addRule(RelativeLayout.RIGHT_OF, R.id.btn_forgotPassword);

      progressView = new ProgressComponentView(context);
      progressView.initMedium(0f);
      progressView.setProgressColor(Theme.getColor(ColorId.textNeutral));
      addThemeTextColorListener(progressView, ColorId.textNeutral);
      progressView.setAlpha(0f);
      progressView.setLayoutParams(rp);
      forgotWrap.addView(progressView);

      contentView.addView(forgotWrap);
      xgramLinks = forgotWrap;
    } else {
      forgotView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
      contentView.addView(forgotView);
      xgramLinks = forgotView;
    }

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = params.rightMargin = Screen.dp(16f);
    params.topMargin = topMargin + Screen.dp(60f) + Screen.dp(14f);

    hintView = new NoScrollTextView(context);
    hintView.setMovementMethod(LinkMovementMethod.getInstance());
    hintView.setLinkTextColor(Theme.textLinkColor());
    hintView.setHighlightColor(Theme.textLinkHighlightColor());
    addThemeLinkTextColorListener(hintView, ColorId.textLink);
    addThemeHighlightColorListener(hintView, ColorId.textLinkPressHighlight);
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    hintView.setTextColor(Theme.textDecentColor());
    addThemeTextColorListener(hintView, ColorId.textLight);
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setLayoutParams(params);

    if (hint != null) {
      hintView.setText(hint);
    } else {
      hintView.setAlpha(0f);
    }
    contentView.addView(hintView);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramLayout(context, contentView);
    }

    setLockFocusView(editText.getEditText());

    switch (mode) {
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_CODE:
      // FIXME? case MODE_CODE_CHANGE:
      case MODE_CODE_EMAIL:
      case MODE_EMAIL_LOGIN:
      case MODE_LOGIN: {
        tdlib.listeners().addAuthorizationChangeListener(this);
        break;
      }
    }

    sendFirebaseSmsIfNeeded(false);

    return contentView;
  }

  private void sendFirebaseSmsIfNeeded (boolean forced) {
    TdApi.AuthenticationCodeType codeType = authenticationCodeType();
    if (codeType == null || codeType.getConstructor() != TdApi.AuthenticationCodeTypeFirebaseAndroid.CONSTRUCTOR || isFirebaseSmsSent) {
      return;
    }
    TdApi.AuthenticationCodeTypeFirebaseAndroid firebase = (TdApi.AuthenticationCodeTypeFirebaseAndroid) codeType;
    RunnableData<String> onVerificationFailure = errorMessage -> {
      lastVerificationError = errorMessage;
      if (forced) {
        TDLib.Tag.integrity(firebase.deviceVerificationParameters, "Avoiding infinite loop, because verification failed twice");
        onDeadEndReached();
      } else {
        TDLib.Tag.integrity(firebase.deviceVerificationParameters, "Force resend code, ignoring whether codeInfo.nextCodeType is null or not");
        requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed(errorMessage), true);
      }
    };
    switch (firebase.deviceVerificationParameters.getConstructor()) {
      case TdApi.FirebaseDeviceVerificationParametersSafetyNet.CONSTRUCTOR: {
        sendFirebaseSmsViaSafetyNet((TdApi.FirebaseDeviceVerificationParametersSafetyNet) firebase.deviceVerificationParameters, onVerificationFailure);
        break;
      }
      case TdApi.FirebaseDeviceVerificationParametersPlayIntegrity.CONSTRUCTOR: {
        sendFirebaseSmsViaPlayIntegrity((TdApi.FirebaseDeviceVerificationParametersPlayIntegrity) firebase.deviceVerificationParameters, onVerificationFailure);
        break;
      }
      default: {
        Td.assertFirebaseDeviceVerificationParameters_21a9fc9c();
        throw Td.unsupported(firebase.deviceVerificationParameters);
      }
    }
  }

  private void handleVerificationResult (TdApi.FirebaseDeviceVerificationParameters verificationParameters, boolean isError, String result, RunnableData<String> onVerificationFailure) {
    if (isError) {
      TDLib.Tag.integrity(verificationParameters, "Verification error: %s", result);
      onVerificationFailure.runWithData(result);
      return;
    }
    if (StringUtils.isEmpty(result)) {
      TDLib.Tag.integrity(verificationParameters, "Verification successful, but empty: %s", result);
      onVerificationFailure.runWithData(result);
      return;
    }

    TDLib.Tag.integrity(verificationParameters, "Verification result: %s", result);
    TdApi.Function<TdApi.Ok> function;
    switch (mode) {
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_CHANGE:
        function = new TdApi.SendPhoneNumberFirebaseSms(result);
        break;
      default:
        function = new TdApi.SendAuthenticationFirebaseSms(result);
        break;
    }
    tdlib.send(function, (ok, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          String errorMessage = TD.toErrorString(error);
          TDLib.Tag.integrity(verificationParameters, "Verified API request failed by server, retrying once: %s", error);
          onVerificationFailure.runWithData(errorMessage);
        } else {
          isFirebaseSmsSent = true;
          updateAuthState(false);
          TDLib.Tag.integrity(verificationParameters, "Verification finished successfully");
        }
      });
    });
  }

  private String lastVerificationError;

  @SuppressWarnings("ConstantConditions")
  private void sendFirebaseSmsViaSafetyNet (TdApi.FirebaseDeviceVerificationParametersSafetyNet safetyNetParameters, RunnableData<String> onVerificationError) {
    String safetyNetApiKey = tdlib.safetyNetApiKey();
    if (StringUtils.isEmpty(safetyNetApiKey)) {
      TDLib.Tag.safetyNet("Requesting next code type, because SafetyNet API_KEY is unavailable");
      requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_API_KEY_EMPTY"), false);
      return;
    }
    if (Config.REQUIRE_FIREBASE_SERVICES_FOR_SAFETYNET && !U.isGooglePlayServicesAvailable(context)) {
      TDLib.Tag.safetyNet("Requesting next code type, because Firebase services are unavailable");
      requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("GOOGLE_PLAY_SERVICES_UNAVAILABLE"), false);
      return;
    }
    SafetyNet.getClient(context)
      .attest(safetyNetParameters.nonce, safetyNetApiKey)
      .addOnSuccessListener(attestationSuccess -> {
        String result = attestationSuccess.getJwsResult();
        if (result == null) {
          TDLib.Tag.safetyNet("Resend firebase sms because JWS = null");
          requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_NULL_JWS"), false);
          return;
        }
        String[] spl = result.split("\\.");
        if (spl.length == 0) {
          TDLib.Tag.safetyNet("Resend firebase sms because can't split JWS token");
          requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_CANT_SPLIT"), false);
          return;
        }
        try {
          JSONObject obj = new JSONObject(new String(Base64.decode(spl[1].getBytes(StringUtils.UTF_8), 0)));
          final boolean basicIntegrity = obj.optBoolean("basicIntegrity");
          final boolean ctsProfileMatch = obj.optBoolean("ctsProfileMatch");
          if (basicIntegrity && ctsProfileMatch) {
            handleVerificationResult(safetyNetParameters, false, result, onVerificationError);
          } else {
            if (!basicIntegrity && !ctsProfileMatch) {
              TDLib.Tag.safetyNet("Resend firebase sms because ctsProfileMatch = false and basicIntegrity = false");
              requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_BASICINTEGRITY_CTSPROFILEMATCH_FALSE"), false);
            } else {
              if (!basicIntegrity) {
                TDLib.Tag.safetyNet("Resend firebase sms because basicIntegrity = false");
                requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_BASICINTEGRITY_FALSE"), false);
              } else {
                TDLib.Tag.safetyNet("Resend firebase sms because ctsProfileMatch = false");
                requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_CTSPROFILEMATCH_FALSE"), false);
              }
            }
          }
        } catch (JSONException e) {
          TDLib.Tag.safetyNet("Resend firebase sms because of exception: %s", Log.toString(e));
          requestNextCodeType(new TdApi.ResendCodeReasonVerificationFailed("SAFETYNET_JSON_EXCEPTION"), false);
        }
      })
      .addOnFailureListener(attestationError -> {
        String error = attestationError.getMessage();
        TDLib.Tag.safetyNet("Attestation failed with error: %s", error);
        executeOnUiThreadOptional(() ->
          onVerificationError.runWithData(error)
        );
      });
  }

  private void sendFirebaseSmsViaPlayIntegrity (TdApi.FirebaseDeviceVerificationParametersPlayIntegrity playIntegrityParameters, RunnableData<String> onVerificationError) {
    tdlib.requestPlayIntegrity(-1, playIntegrityParameters.nonce, (data) ->
      handleVerificationResult(playIntegrityParameters, false, data, onVerificationError)
    );
  }

  @Override
  public void onAuthorizationStateChanged (TdApi.AuthorizationState authorizationState) {
    runOnUiThreadOptional(() -> {
      this.authState = authorizationState;
      this.isFirebaseSmsSent = false;
      fillPhoneFromAuthState(); // XGram
      updateAuthState(true);
      if (phoneCodeLinks != null) { // XGram: a new WaitCode after a resend restarts the countdown
        phoneCodeLinks.onStateChanged(authorizationState);
      }
      // XGram: TDLib sends WaitEmailCode again after a resend or a reset request, and MainActivity keeps this screen, so refresh it here
      if (emailCodeLinks != null && emailCodeLinks.onStateChanged(authorizationState)) {
        setHintText(emailCodeLinks.buildHint(), false);
      }
    });
  }

  @Override
  public void onAuthorizationCodeReceived (String code) {
    switch (mode) {
      case MODE_CODE:
      case MODE_LOGIN:
      case MODE_CODE_EMAIL:
        editText.setText(code);
        proceed();
        break;
    }
  }

  private boolean isFirebaseSmsSent;

  private CharSequence getCodeHint (TdApi.AuthenticationCodeType type, String formattedPhone) {
    if (mode == MODE_CODE_PHONE_CONFIRM) {
      return Strings.replaceBoldTokens(Lang.getString(R.string.CancelAccountResetInfo, formattedPhone));
    }
    editText.setHint(Lang.getString(R.string.login_Code));
    switch (type.getConstructor()) {
      case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentCallCode, formattedPhone), ColorId.textLight);
      }
      case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentCallOnly, formattedPhone), ColorId.textLight);
      }
      case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR: {
        CharSequence sentAppCode = Strings.replaceBoldTokens(Lang.getString(R.string.SentAppCode), ColorId.textLight);
        if (mode == MODE_CODE && !hasNextCodeType()) {
          // XGram: Telegram gives unofficial apps no SMS: a number without an account gets this answer and no code anywhere.
          return android.text.TextUtils.concat(sentAppCode, "\n\n", Lang.getString(R.string.XgramLoginAppCodeOnly));
        }
        return sentAppCode;
      }
      case TdApi.AuthenticationCodeTypeSms.CONSTRUCTOR:
      case TdApi.AuthenticationCodeTypeFirebaseIos.CONSTRUCTOR: // XGram: an SMS hint instead of a crash
      case TdApi.AuthenticationCodeTypeFirebaseAndroid.CONSTRUCTOR: {
        int resId = R.string.SentSmsCode;
        if (type.getConstructor() == TdApi.AuthenticationCodeTypeFirebaseAndroid.CONSTRUCTOR && !isFirebaseSmsSent) {
          resId = R.string.SendingSmsCode;
        }
        return Strings.replaceBoldTokens(Lang.getString(resId, formattedPhone), ColorId.textLight);
      }
      case TdApi.AuthenticationCodeTypeSmsWord.CONSTRUCTOR: {
        TdApi.AuthenticationCodeTypeSmsWord word = (TdApi.AuthenticationCodeTypeSmsWord) type;
        if (StringUtils.isEmpty(word.firstLetter)) {
          return Lang.getStringBold(R.string.SentSmsWord, formattedPhone);
        } else {
          return Lang.getStringBold(R.string.SentSmsWordHint, formattedPhone, word.firstLetter);
        }
      }
      case TdApi.AuthenticationCodeTypeSmsPhrase.CONSTRUCTOR: {
        TdApi.AuthenticationCodeTypeSmsPhrase phrase = (TdApi.AuthenticationCodeTypeSmsPhrase) type;
        if (StringUtils.isEmpty(phrase.firstWord)) {
          return Lang.getStringBold(R.string.SentSmsPhrase, formattedPhone);
        } else {
          return Lang.getStringBold(R.string.SentSmsPhraseHint, formattedPhone, phrase.firstWord);
        }
      }
      case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR: {
        TdApi.AuthenticationCodeTypeMissedCall missedCall = (TdApi.AuthenticationCodeTypeMissedCall) type;
        editText.setHint(Lang.pluralBold(R.string.login_LastDigits, missedCall.length));
        return Strings.replaceBoldTokens(Lang.getString(R.string.format_doubleLines, Lang.getString(R.string.SentMissedCall, Strings.formatPhone(missedCall.phoneNumberPrefix)), Lang.plural(R.string.SentMissedCallXDigits, missedCall.length)), ColorId.textLight);
      }
      case TdApi.AuthenticationCodeTypeFragment.CONSTRUCTOR: {
        TdApi.AuthenticationCodeTypeFragment fragment = (TdApi.AuthenticationCodeTypeFragment) type;
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append(Strings.replaceBoldTokens(Lang.getStringSecure(R.string.SentFragmentCode)));
        if (!StringUtils.isEmpty(fragment.url)) {
          b.append(" ");
          int start = b.length();
          b.append(Lang.getStringSecure(R.string.OpenFragment));
          b.setSpan(new NoUnderlineClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              UI.openUrl(fragment.url);
            }
          }, start, b.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
      }
      default:
        Td.assertAuthenticationCodeType_6b7089f4();
        break;
    }
    throw Td.unsupported(type);
  }

  @Override
  public boolean onTextDonePressed (MaterialEditTextGroup v) {
    proceed();
    return true;
  }

  @Override
  public void onTextChanged (MaterialEditTextGroup v, CharSequence charSequence) {
    String text = charSequence.toString();
    if ((mode == MODE_NEW && step == STEP_EMAIL_RECOVERY)) {
      setIsInputOK(Strings.isValidEmail(text));
    } else if (mode == MODE_LOGIN_EMAIL_CHANGE) {
      if (emailAddressAuthenticationCodeInfo != null) {
        setIsInputOK(!StringUtils.isEmpty(text));
      } else {
        setIsInputOK(Strings.isValidEmail(text));
      }
    } else if (mode == MODE_EMAIL_RECOVERY || mode == MODE_LOGIN_EMAIL_RECOVERY) {
      setIsInputOK(Strings.getNumber(text).length() >= EMAIL_RECOVERY_CODE_LENGTH);
    } else if ((mode == MODE_CODE || mode == MODE_CODE_CHANGE || mode == MODE_CODE_PHONE_CONFIRM || mode == MODE_CODE_EMAIL) && checkCodeLength(authState, text)) {
      proceed();
    } else if ((mode == MODE_NEW || mode == MODE_EDIT) && step == STEP_PASSWORD_HINT) {
      passwordHint = text;
    }
  }

  private static boolean checkCodeLength (TdApi.AuthorizationState state, String text) {
    int expectedCodeLength = TD.codeLength(state, 0);
    return expectedCodeLength > 0 && Strings.codePointCount(text) >= expectedCodeLength;
  }

  private boolean ignoreNextEmpty;

  @Override
  public void onTextEmptyStateChanged (MaterialEditTextGroup v, boolean isEmpty) {
    if (ignoreNextEmpty) {
      ignoreNextEmpty = false;
    } else {
      if ((mode != MODE_NEW || step != STEP_EMAIL_RECOVERY) && mode != MODE_EMAIL_RECOVERY && mode != MODE_LOGIN_EMAIL_RECOVERY && mode != MODE_LOGIN_EMAIL_CHANGE) {
        animateNextFactor(isEmpty && ((mode != MODE_NEW && mode != MODE_EDIT) || step != STEP_PASSWORD_HINT) ? 0f : 1f);
      }
    }
  }

  private float nextFactor;
  private FactorAnimator nextAnimator;

  private static final int NEXT_ANIMATOR = 0;
  private void animateNextFactor (float toFactor) {
    if (nextAnimator == null) {
      nextAnimator = new FactorAnimator(NEXT_ANIMATOR, this, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, 300, nextFactor);
    }
    nextAnimator.animateTo(toFactor);
  }

  private void setNextFactor (float factor) {
    if (this.nextFactor != factor) {
      this.nextFactor = factor;
      nextButton.setAlpha(Math.min(1f, factor));
      final float scale = .6f + .4f * factor;
      nextButton.setScaleX(scale);
      nextButton.setScaleY(scale);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case NEXT_ANIMATOR: {
        setNextFactor(factor);
        break;
      }
      case FORGET_ANIMATOR: {
        float easeFactor = (AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f + factor * .5f) / AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f)) * factor;
        forgotView.setAlpha(easeFactor);
        resetWaitView.setAlpha(easeFactor);
        cancelResetView.setAlpha(easeFactor);
        break;
      }
      case HINT_ANIMATOR: {
        float easeFactor = (AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f + factor * .5f) / AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f)) * factor;
        hintView.setAlpha(easeFactor);
        break;
      }
      case RECOVERY_PROGRESS_ANIMATOR: {
        if (progressView != null) {
          progressView.setAlpha(factor);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case FORGET_ANIMATOR: {
        if (finalFactor == 0f) {
          forgotView.setText("");
        }
        break;
      }
      case HINT_ANIMATOR: {
        if (finalFactor == 0f) {
          if (!StringUtils.isEmpty(pendingHint)) {
            setHint(pendingHint, pendingHintIsError);
            pendingHint = null;
            pendingHintIsError = false;
            hintAnimator.animateTo(1f);
          } else {
            setHint("", false);
          }
        }
        break;
      }
    }
  }

  private static final int HINT_ANIMATOR = 2;
  private FactorAnimator hintAnimator;

  private void animateHint (float toFactor) {
    if (hintAnimator == null) {
      hintAnimator = new FactorAnimator(HINT_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, 180l, hintView.getAlpha());
    }
    hintAnimator.animateTo(toFactor);
  }

  private void setHintText (@StringRes int res, boolean isError) {
    setHintText(Lang.getString(res), isError);
  }

  private void setHint (CharSequence text, boolean isError) {
    hintView.setText(text);
    hintView.setTextColor(isError ? Theme.textRedColor() : Theme.textDecentColor());
    removeThemeListenerByTarget(hintView);
    addOrUpdateThemeTextColorListener(hintView, isError ? ColorId.textNegative : ColorId.textLight);
    editText.setInErrorState(isError);
  }

  private void setHintText (@Nullable CharSequence text, boolean isError) {
    if (StringUtils.isEmpty(text)) {
      animateHint(0f);
      if (hintView.getAlpha() == 0f) {
        setHint("", false);
      }
    } else if (hintView.getAlpha() == 0f) {
      setHint(text, isError);
      animateHint(1f);
    } else {
      pendingHint = text;
      pendingHintIsError = isError;
      animateHint(0f);
    }
  }

  private CharSequence pendingHint;
  private boolean pendingHintIsError;

  private static final int FORGET_ANIMATOR = 1;
  private FactorAnimator forgetAnimator;

  private void animateForget (float toFactor) {
    if (forgetAnimator == null) {
      forgetAnimator = new FactorAnimator(FORGET_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, 180l, forgotView.getAlpha());
    }
    forgetAnimator.animateTo(toFactor);
  }

  private void setForgetText (@StringRes int res) {
    setForgetText(Lang.getString(res));
  }

  private void setForgetText (@Nullable CharSequence forgetText) {
    if (StringUtils.isEmpty(forgetText)) {
      animateForget(0f);
      if (forgotView.getAlpha() == 0f) {
        forgotView.setText("");
      }
    } else {
      forgotView.setText(forgetText);
      animateForget(1f);
    }
  }

  private boolean inRecoveryProgress;
  private void setInRecoveryProgress (boolean inProgress) {
    if (inRecoveryProgress != inProgress) {
      inRecoveryProgress = inProgress;
      animateRecoverProgressFactor(inProgress ? 1f : 0f);
    }
  }

  private static final int RECOVERY_PROGRESS_ANIMATOR = 3;
  private FactorAnimator recoverProgressAnimator;

  private void animateRecoverProgressFactor (float toFactor) {
    if (recoverProgressAnimator == null) {
      recoverProgressAnimator = new FactorAnimator(RECOVERY_PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    recoverProgressAnimator.animateTo(toFactor);
  }

  private boolean hasNextCodeType () {
    return authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR && ((TdApi.AuthorizationStateWaitCode) authState).codeInfo.nextType != null;
  }

  private TdApi.AuthenticationCodeType authenticationCodeType () {
    if (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
      return ((TdApi.AuthorizationStateWaitCode) authState).codeInfo.type;
    }
    return null;
  }

  private void updateAuthState (boolean typeChanged) {
    TdApi.AuthenticationCodeType authenticationCodeType = authenticationCodeType();
    if (authenticationCodeType != null) {
      setHint(getCodeHint(authenticationCodeType, formattedPhone), false); // XGram: a new state clears the red error look
      // XGram: on the login screen PhoneCodeLinks owns the link; elsewhere bring "Didn't get the code?" back when a next type appears
      if (phoneCodeLinks == null) {
        setForgetText(hasNextCodeType() ? Lang.getString(R.string.DidNotGetTheCode) : null);
      }
      if (typeChanged) {
        updateAuthInputType();
      }
    }
    sendFirebaseSmsIfNeeded(false);
  }

  private void onDeadEndReached () {
    TDLib.Tag.safetyNet("Dead end reached: attestation failed and codeInfo.nextType is null");
    CharSequence message = Lang.getMarkdownString(this, R.string.login_DeadEnd);
    if (message instanceof Spannable) {
      CustomTypefaceSpan[] spans = ((Spannable) message).getSpans(0, message.length(), CustomTypefaceSpan.class);
      for (CustomTypefaceSpan span : spans) {
        if (span.getTextEntityType() != null && Td.isItalic(span.getTextEntityType())) {
          span.setTypeface(null);
          span.setColorId(ColorId.textLink);
          span.setTextEntityType(new TdApi.TextEntityTypeEmailAddress());
          int start = ((Spannable) message).getSpanStart(span);
          int end = ((Spannable) message).getSpanEnd(span);
          ((Spannable) message).setSpan(new NoUnderlineClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              sendLoginHelpEmail(); // XGram: shared with the "Didn't get the code?" sheet
            }
          }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          break;
        }
      }
    }
    setHint(message, true);
  }

  private void sendLoginHelpEmail () {
    Intents.sendEmail(
      Lang.getStringSecure(R.string.email_SmsHelp),
      Lang.getStringSecure(R.string.email_LoginDeadEnd_subject, formattedPhone),
      Lang.getStringSecure(R.string.email_LoginDeadEnd_text, formattedPhone, U.getUsefulMetadata(tdlib)),
      StringUtils.isEmpty(lastVerificationError) ? "none" : lastVerificationError
    );
  }

  private void requestNextCodeType (TdApi.ResendCodeReason reason, boolean force) {
    if (inRecoveryProgress) {
      return;
    }

    boolean byUser = reason.getConstructor() == TdApi.ResendCodeReasonUserRequest.CONSTRUCTOR;

    if (!hasNextCodeType() && !force) {
      if (!byUser) {
        onDeadEndReached();
      }
      return;
    }

    if (byUser && tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInRecoveryProgress(true);
    TdApi.Function<?> function;
    switch (mode) {
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_CHANGE:
        function = new TdApi.ResendPhoneNumberCode(reason);
        break;
      default:
        function = new TdApi.ResendAuthenticationCode(reason);
        break;
    }
    tdlib.client().send(function, object -> runOnUiThreadOptional(() -> {
      setInRecoveryProgress(false);
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          break;
        }
        case TdApi.AuthenticationCodeInfo.CONSTRUCTOR: {
          // XGram: only ResendPhoneNumberCode answers with code info; a login resend answers Ok and a new state follows
          if (mode != MODE_CODE_CHANGE && mode != MODE_CODE_PHONE_CONFIRM) {
            break;
          }
          ((TdApi.AuthorizationStateWaitCode) authState).codeInfo = (TdApi.AuthenticationCodeInfo) object;
          isFirebaseSmsSent = false;
          updateAuthState(true);
          sendFirebaseSmsIfNeeded(force);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (mode == MODE_CODE && phoneCodeLinks != null && phoneCodeLinks.onResendError((TdApi.Error) object)) {
            break; // XGram: expired code, the link now sends the phone number again
          }
          setHint(TD.toErrorString(object), true);
          break;
        }
      }
    }));
  }

  private int getTimeDiff (long futureTimestamp) {
    return (int) (futureTimestamp - tdlib.currentTime(TimeUnit.SECONDS));
  }

  private boolean pendingPasswordReset () {
    return state != null && state.pendingResetDate > 0 && state.pendingResetDate > tdlib.currentTime(TimeUnit.SECONDS);
  }

  private boolean canResetPassword () {
    return state != null && state.pendingResetDate > 0 && tdlib.currentTime(TimeUnit.SECONDS) >= state.pendingResetDate;
  }

  private static final int UPDATE_TEXT_VIEWS_TIMER = 0;

  @Override
  public boolean handleMessage (Message msg) {
    switch (msg.what) {
      case UPDATE_TEXT_VIEWS_TIMER: {
        updatePasswordResetTextViews();
        break;
      }
    }
    return true;
  }

  private void updatePasswordResetTextViews () {
    if (state != null && state.pendingResetDate == 0) {
      forgotView.setPadding(Screen.dp(16f), Screen.dp(15f), Screen.dp(12f), Screen.dp(16f));
    } else {
      forgotView.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(12f), Screen.dp(16f));
    }
    if (canResetPassword()) {
      cancelResetView.setVisibility(View.VISIBLE);
      cancelResetView.setText(Lang.getString(R.string.CancelReset));
    } else {
      cancelResetView.setVisibility(View.GONE);
    }
    if (pendingPasswordReset()) {
      forgotView.setText(Lang.getString(R.string.CancelReset));
      resetWaitView.setVisibility(View.VISIBLE);
      resetWaitView.setText(Lang.getString(R.string.RestorePasswordResetIn, Lang.getDuration(getTimeDiff(state.pendingResetDate))));

      handler.sendMessageDelayed(Message.obtain(handler, UPDATE_TEXT_VIEWS_TIMER), 1000);
    } else {
      forgotView.setText(Lang.getString(canResetPassword() ? R.string.ResetPassword : R.string.ForgotPassword));
      resetWaitView.setVisibility(View.GONE);
    }
  }

  private void cancelResetPassword () {
    setInRecoveryProgress(true);
    tdlib.client().send(new TdApi.CancelPasswordReset(), object -> runOnUiThreadOptional(() -> {
      setInRecoveryProgress(false);
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          state.pendingResetDate = 0;
          updatePasswordResetTextViews();
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (cancelResetView.getVisibility() == View.VISIBLE) {
            context().tooltipManager().builder(cancelResetView).offset(outRect -> outRect.offset(0, Screen.dp(10f))).show(tdlib, TD.toErrorString(object));
          } else {
            context().tooltipManager().builder(forgotView).show(tdlib, TD.toErrorString(object));
          }
          break;
        }
      }
    }));
  }

  private void confirmResetPassword () {
    setInRecoveryProgress(true);
    tdlib.client().send(new TdApi.ResetPassword(), object -> runOnUiThreadOptional(() -> {
      setInRecoveryProgress(false);
      switch (object.getConstructor()) {
        case TdApi.ResetPasswordResultOk.CONSTRUCTOR: {
          state.pendingResetDate = 0;
          ViewController<?> c = findLastStackItemById(R.id.controller_privacySettings);
          if (c instanceof SettingsPrivacyController) {
            ((SettingsPrivacyController) c).reloadPasswordState();
          }
          navigateBack();
          openAlert(R.string.ResetPassword, R.string.RestorePasswordResetPasswordOk);
          break;
        }
        case TdApi.ResetPasswordResultPending.CONSTRUCTOR: {
          TdApi.ResetPasswordResultPending data = (TdApi.ResetPasswordResultPending) object;
          if (mode == MODE_EMAIL_RECOVERY) {
            PasswordController prev = (PasswordController) navigationController.getPreviousStackItem();
            prev.state.pendingResetDate = data.pendingResetDate;
            prev.updatePasswordResetTextViews();
            navigateBack();
          } else {
            state.pendingResetDate = data.pendingResetDate;
            updatePasswordResetTextViews();
          }
          break;
        }
        case TdApi.ResetPasswordResultDeclined.CONSTRUCTOR: {
          TdApi.ResetPasswordResultDeclined data = (TdApi.ResetPasswordResultDeclined) object;
          openAlert(R.string.ResetPassword, Lang.getString(R.string.ResetPasswordWait, Lang.getDuration(getTimeDiff(data.retryDate))));
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          context().tooltipManager().builder(forgotView).show(tdlib, TD.toErrorString(object));
          break;
        }
        default: {
          Td.assertResetPasswordResult_7d1022f2();
          break;
        }
      }
    }));
  }

  private void requestRecovery () {
    if ((state != null && !state.hasRecoveryEmailAddress && authState != null) || (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR && !((TdApi.AuthorizationStateWaitPassword) authState).hasRecoveryEmailAddress) || (state == null && authState == null)) {
      showNoRecoveryEmail(R.string.SinceNotProvided); // XGram: offers "Reset account" on the login screen
      return;
    }

    if (inRecoveryProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInRecoveryProgress(true);

    if (mode == MODE_LOGIN) {
      if (authState.getConstructor() != TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR) {
        Log.e("Can't proceed, authState: %s", authState);
        setInRecoveryProgress(false); // XGram: the spinner kept turning after an early return
        return;
      }
      if (!((TdApi.AuthorizationStateWaitPassword) authState).hasRecoveryEmailAddress) {
        setInRecoveryProgress(false); // XGram
        showNoRecoveryEmail(R.string.SinceNotProvided); // XGram
        return;
      }
      tdlib.client().send(new TdApi.RequestAuthenticationPasswordRecovery(), object -> runOnUiThreadOptional(() -> {
        setInRecoveryProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            PasswordController c = new PasswordController(context, tdlib);
            TdApi.AuthorizationStateWaitPassword state = (TdApi.AuthorizationStateWaitPassword) authState;
            c.setArguments(new Args(MODE_LOGIN_EMAIL_RECOVERY, state).setEmail(state.recoveryEmailAddressPattern));
            navigateTo(c);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if ("PASSWORD_RECOVERY_NA".equals(error.message)) {
              showNoRecoveryEmail(R.string.SinceNotProvided); // XGram
            } else {
              UI.showError(object);
            }
            break;
          }
        }
      }));
    } else {
      if (pendingPasswordReset()) {
        setInRecoveryProgress(false);
        openAlert(R.string.ResetPassword, R.string.CancelPasswordReset, Lang.getString(R.string.CancelPasswordResetYes), (dialog, which) -> { cancelResetPassword(); });
      } else if (canResetPassword()) {
        setInRecoveryProgress(false);
        confirmResetPassword();
      } else if (!state.hasRecoveryEmailAddress) {
        setInRecoveryProgress(false);
        openAlert(R.string.ResetPassword, R.string.RestorePasswordNoEmailText2, Lang.getString(R.string.Reset), (dialog, which) -> { confirmResetPassword(); });
      } else {
        tdlib.client().send(new TdApi.RequestPasswordRecovery(), object -> runOnUiThreadOptional(() -> {
          setInRecoveryProgress(false);
          switch (object.getConstructor()) {
            case TdApi.EmailAddressAuthenticationCodeInfo.CONSTRUCTOR: {
              TdApi.EmailAddressAuthenticationCodeInfo info = (TdApi.EmailAddressAuthenticationCodeInfo) object;
              PasswordController c = new PasswordController(context, tdlib);
              c.setArguments(new Args(MODE_EMAIL_RECOVERY, state).setEmail(info.emailAddressPattern).setCodeLength(info.length));
              navigateTo(c);
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              TdApi.Error error = (TdApi.Error) object;
              if ("PASSWORD_RECOVERY_NA".equals(error.message)) {
                openAlert(R.string.RestorePasswordNoEmailTitle, R.string.SinceNotProvided);
              } else {
                UI.showError(object);
              }
              break;
            }
          }
        }));
      }
    }
  }

  /*private boolean ignoreFirstOnBlur;

  @Override
  public void onBlur () {
    View lockFocusView = getLockFocusView();
    if (ignoreFirstOnBlur) {
      setLockFocusView(null);
    }
    super.onBlur();
    if (ignoreFirstOnBlur) {
      setLockFocusView(lockFocusView);
      ignoreFirstOnBlur = false;
    }
  }*/

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();

    if (!oneShot) {
      switch (mode) {
        case MODE_LOGIN: {
          destroyStackItemById(R.id.controller_code);
          break;
        }
        case MODE_CODE: {
          destroyStackItemById(R.id.controller_name);
          break;
        }
        case MODE_CODE_CHANGE: {
          destroyStackItemById(R.id.controller_phone);
          break;
        }
        case MODE_UNLOCK_EDIT: {
          destroyStackItemById(R.id.controller_2faSettings);
          break;
        }
      }
      if (UI.inTestMode()) {
        switch (mode) {
          case MODE_LOGIN: {
            editText.setText(Config.ROBOT_PASSWORD);
            proceed();
            break;
          }
          case MODE_CODE:
          case MODE_CODE_CHANGE: {
            editText.setText(tdlib.robotLoginCode());
            proceed();
            break;
          }
        }
      }
      oneShot = true;
    }

    if (forgotView != null && forgotView.getAlpha() == 0f && forgotView.getText().length() > 0) {
      forgotView.postDelayed(() -> animateForget(1f), 100);
    }
  }

  private boolean inProgress;

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      nextButton.setInProgress(inProgress);
      if (needStackLocking()) {
        if (isFocused() || (!inProgress && isStackLocked())) {
          setStackLocked(inProgress);
        } else {
          addOneShotFocusListener(() ->
            setStackLocked(this.inProgress)
          );
        }
      }
    }
  }

  private void unlockEdit (final String password) {
    if (inProgress) {
      return;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);
    tdlib.send(new TdApi.GetRecoveryEmailAddress(password), (recoveryEmailAddress, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);

      boolean success = false;
      String recoveryEmail = null;

      if (error != null) {
        setHintText(R.string.InvalidPasswordTryAgain, true);
      } else {
        success = true;
        recoveryEmail = recoveryEmailAddress.recoveryEmailAddress;
      }

      if (success) {
        ViewController<?> prev = navigationController != null ? navigationController.getPreviousStackItem() : null;
        if (prev instanceof SettingsPrivacyController) {
          Settings2FAController c = new Settings2FAController(context, tdlib);
          c.setArguments(new Settings2FAController.Args((SettingsPrivacyController) prev, password, recoveryEmail));
          navigateTo(c);
        } else if ((mode == MODE_CONFIRM || mode == MODE_TRANSFER_OWNERSHIP_CONFIRM) && getArguments() != null && getArguments().onSuccessListener != null) {
          navigateBack();
          getArgumentsStrict().onSuccessListener.runWithData(password);
        } else if (mode == MODE_CUSTOM_CONFIRM) {
          confirmDelegate.onPasswordConfirmed(this, password);
        }
      }
    }));
  }


  private static final int STEP_PASSWORD = 0;
  private static final int STEP_REPEAT_PASSWORD = 1;
  private static final int STEP_PASSWORD_HINT = 2;
  private static final int STEP_EMAIL_RECOVERY = 3;
  private static final int STEP_PASSWORD_DONT_MATCH = 4;
  private static final int STEP_EMAIL_RECOVERY_CONFIRM = 5;
  private int step;

  private String currentPassword;
  private String passwordHint;

  private void setStep (String input, int step) {
    if (this.step == step) {
      return;
    }
    switch (step) {
      case STEP_PASSWORD_DONT_MATCH: {
        this.step = STEP_PASSWORD;
        break;
      }
      default: {
        this.step = step;
        break;
      }
    }
    switch (step) {
      case STEP_PASSWORD:
      case STEP_PASSWORD_DONT_MATCH: {
        setForgetText(null);
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.EnterAPassword, true, step == STEP_PASSWORD_DONT_MATCH ? (Runnable) () -> setHintText(R.string.PasswordDoNotMatch, true) : null);
        break;
      }
      case STEP_REPEAT_PASSWORD: {
        setForgetText(null);
        setHintText(null, false);
        currentPassword = input;
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.ReEnterAPassword, true, null);
        break;
      }
      case STEP_PASSWORD_HINT: {
        setForgetText(null);
        passwordHint = Strings.generateHint(currentPassword);
        editText.resetWithHint(R.string.CreateAHintForYourPassword, passwordHint, false, null);
        if (mode == MODE_EDIT) {
          nextButton.replaceIcon(R.drawable.baseline_check_24);
        }
        break;
      }
      case STEP_EMAIL_RECOVERY: {
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.YourEmail, false, () -> {
          nextButton.setIcon(R.drawable.baseline_check_24);
          // PS: Disabled because of jumping
          // editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
          setForgetText(R.string.Skip);
          setHintText(R.string.YourEmailInfo, false);
        });
        break;
      }
    }
  }

  private boolean isInputOK;

  private void setIsInputOK (boolean isInputOK) {
    if (this.isInputOK != isInputOK) {
      this.isInputOK = isInputOK;
      animateNextFactor(isInputOK ? 1f : 0f);
    }
  }

  private void proceedOptionally (Runnable act) {
    if (inProgress) {
      return;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }
    setInProgress(true);
    act.run();
  }

  private void setNewRecoveryEmail (String email) {
    proceedOptionally(() -> {
      final String oldPassword = getArguments() != null ? getArguments().oldPassword : null;
      tdlib.send(new TdApi.SetRecoveryEmailAddress(oldPassword, email), (passwordState, error) -> runOnUiThreadOptional(() -> {
        setInProgress(false);
        if (error != null) {
          setHintText(TD.toErrorString(error), true);
        } else {
          processNewPasswordState(passwordState, oldPassword);
        }
      }));
    });
  }

  private void nextPasswordStep () {
    String input = editText.getText().toString();
    switch (step) {
      case STEP_PASSWORD: {
        if (input.length() > 0) {
          if (mode == MODE_EDIT && getArguments() != null && input.equals(getArguments().oldPassword)) {
            setHintText(R.string.PasswordMatchesOldOne, true);
          } else {
            setStep(input, STEP_REPEAT_PASSWORD);
          }
        }
        break;
      }
      case STEP_REPEAT_PASSWORD: {
        if (input.length() > 0) {
          if (currentPassword.equals(input)) {
            setStep(input, STEP_PASSWORD_HINT);
          } else {
            setStep(input, STEP_PASSWORD_DONT_MATCH);
          }
        }
        break;
      }
      case STEP_PASSWORD_HINT: {
        if (input.equalsIgnoreCase(currentPassword)) {
          setHintText(R.string.PasswordAndHintMustBeDifferent, true);
        } else if (mode == MODE_NEW) {
          setStep(input, STEP_EMAIL_RECOVERY);
        } else if (mode == MODE_EDIT) {
          setPassword(currentPassword, passwordHint, getArguments() != null ? getArguments().email : null);
        }
        break;
      }
      case STEP_EMAIL_RECOVERY: {
        String email = input.trim();
        if (email.isEmpty() || !Strings.isValidEmail(input)) {
          showNoRecoveryEmailAlert();
        } else {
          setPassword(currentPassword, passwordHint, email);
        }
        break;
      }
    }
  }

  private boolean needStackLocking () {
    return mode == MODE_CODE || mode == MODE_CODE_EMAIL || mode == MODE_LOGIN_EMAIL_RECOVERY;
  }

  private void sendCode (String code) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    TdApi.Function<?> function;
    switch (mode) {
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_CHANGE:
        function = new TdApi.CheckPhoneNumberCode(code);
        break;
      case MODE_CODE_EMAIL:
      case MODE_LOGIN_EMAIL_CHANGE:
        // TODO sign in with Google (+ Apple ID?)
        function = new TdApi.CheckAuthenticationEmailCode(new TdApi.EmailAddressAuthenticationCode(code));
        break;
      default:
        function = new TdApi.CheckAuthenticationCode(code);
        break;
    }

    tdlib.client().send(function, object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            switch (mode) {
              case MODE_CODE_PHONE_CONFIRM: {
                // TODO any pop-up?
                navigateBack();
                break;
              }
              case MODE_CODE_CHANGE: {
                openAlert(R.string.AppName, Lang.getString(R.string.SuccessfullyChangedNumber, formattedPhone), (dialog, which) -> navigateBack(), false);
                break;
              }
              case MODE_CODE: {
                if (UI.inTestMode()) {
                  tdlib.client().send(new TdApi.SetPassword(null, Config.ROBOT_PASSWORD, Strings.generateHint(Config.ROBOT_PASSWORD), false, null), tdlib.silentHandler());
                }
                break;
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            if (mode == MODE_CODE && phoneCodeLinks != null && phoneCodeLinks.onCodeCheckError((TdApi.Error) object)) {
              break; // XGram: expired code, the link now offers a new one
            }
            setHintText(mode == MODE_CODE_EMAIL ? com.fahadali.xgram.login.EmailLoginErrors.toString(object) : TD.toErrorString(object), true); // XGram: plain email-login errors
            break;
          }
        }
      }
    }));
  }

  private void recover (String recoveryCode) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    tdlib.client().send(mode == MODE_LOGIN_EMAIL_RECOVERY ? new TdApi.RecoverAuthenticationPassword(recoveryCode, null, null) : new TdApi.RecoverPassword(recoveryCode, null, null), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.PasswordState.CONSTRUCTOR: {
            processNewPasswordState((TdApi.PasswordState) object, null);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if (error.code == 400 && "CODE_INVALID".equals(error.message)) {
              setHintText(R.string.RecoveryCodeInvalid, true);
            } else {
              setHintText(TD.toErrorString(error), true);
            }
            break;
          }
        }
      }
    }));
  }

  private void login (TdApi.Function<?> function) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    tdlib.client().send(function, object -> runOnUiThreadOptional(() -> {
      setInProgress(false);

      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          TdApi.Error error = (TdApi.Error) object;
          if (error.code == 400 && "PASSWORD_HASH_INVALID".equals(error.message)) {
            Views.selectAll(editText.getEditText());
            Keyboard.show(editText);
            setHintText(R.string.InvalidPasswordTryAgain, true);
          } else {
            setHintText(mode == MODE_EMAIL_LOGIN ? com.fahadali.xgram.login.EmailLoginErrors.toString(object) : TD.toErrorString(object), true); // XGram: plain email-login errors
          }
          break;
        }
      }
    }));
  }

  private static final int EMAIL_RECOVERY_CODE_LENGTH = 6;

  private void proceed () {
    String input = editText.getText().toString();
    switch (mode) {
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CUSTOM_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        if (!input.isEmpty()) {
          unlockEdit(input);
        }
        break;
      }
      case MODE_LOGIN: {
        if (!input.isEmpty()) {
          login(new TdApi.CheckAuthenticationPassword(input));
        }
        break;
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        String number = Strings.getNumber(input);
        if (number.length() >= EMAIL_RECOVERY_CODE_LENGTH) {
          recover(number);
        }
        break;
      }
      case MODE_2FA_RECOVERY_EMAIL_CHANGE: {
        if (Strings.isValidEmail(input) && getArguments() != null) {
          if (input.equals(getArguments().email) && (state == null || state.recoveryEmailAddressCodeInfo == null)) {
            setHintText(R.string.EmailMatchesOldOne, true);
          } else {
            setNewRecoveryEmail(input);
          }
        }
        break;
      }
      case MODE_LOGIN_EMAIL_CHANGE: {
        if (!isInputOK) {
          return;
        }
        proceedOptionally(() -> {
          if (emailAddressAuthenticationCodeInfo != null) {
            tdlib.send(new TdApi.CheckLoginEmailAddressCode(new TdApi.EmailAddressAuthenticationCode(input)), (ok, error) -> runOnUiThreadOptional(() -> {
              setInProgress(false);
              if (error != null) {
                setHintText(TD.toErrorString(error), true);
              } else {
                ViewController<?> c = findLastStackItemById(R.id.controller_privacySettings);
                if (c instanceof SettingsPrivacyController) {
                  ((SettingsPrivacyController) c).reloadPasswordState();
                }
                navigateBack();
              }
            }));
          } else {
            tdlib.send(new TdApi.SetLoginEmailAddress(input), (emailAddressAuthenticationCodeInfo, error) -> runOnUiThreadOptional(() -> {
              setInProgress(false);
              if (error != null) {
                setHintText(TD.toErrorString(error), true);
              } else {
                this.emailAddressAuthenticationCodeInfo = emailAddressAuthenticationCodeInfo;
                ignoreNextEmpty = true;
                animateNextFactor(0f);
                editText.resetWithHint(R.string.EnterCode, false, () -> {
                  nextButton.setIcon(R.drawable.baseline_check_24);
                  editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
                  setForgetText(R.string.ChangeEmailResend);
                  setHintText(Lang.getStringBold(R.string.ChangeEmailConfirmCode, input), false);
                });
              }
            }));
          }
        });
        break;
      }
      case MODE_EMAIL_LOGIN: {
        if (Strings.isValidEmail(input)) {
          login(new TdApi.SetAuthenticationEmailAddress(input));
        } else {
          setHintText(R.string.EmailInvalid, true);
        }
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_EMAIL: {
        sendCode(input);
        break;
      }
      case MODE_NEW:
      case MODE_EDIT: {
        nextPasswordStep();
        break;
      }
    }
  }

  private boolean hasUnsavedChanges () {
    return (mode == MODE_LOGIN_EMAIL_CHANGE && emailAddressAuthenticationCodeInfo != null);
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (hasUnsavedChanges()) {
      if (commit) {
        showUnsavedChangesPromptBeforeLeaving(this::navigateBack);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private TdApi.EmailAddressAuthenticationCodeInfo emailAddressAuthenticationCodeInfo;

  private void showNoRecoveryEmailAlert () {
    openAlert(R.string.Warning, R.string.YourEmailSkipWarningText, (dialog, which) -> setPassword(currentPassword, passwordHint, null));
  }

  // XGram: links of the email-code login screen, see com.fahadali.xgram.login.EmailCodeLinks
  private @Nullable com.fahadali.xgram.login.EmailCodeLinks emailCodeLinks;

  private com.fahadali.xgram.login.EmailCodeLinks newEmailCodeLinks (TdApi.AuthorizationStateWaitEmailCode state) {
    boolean fromEmailSetup = getArguments() != null && getArguments().fromEmailSetup;
    return new com.fahadali.xgram.login.EmailCodeLinks(this, tdlib, new com.fahadali.xgram.login.EmailCodeLinks.Host() {
      @Override
      public void setLinkText (@Nullable CharSequence text) {
        setForgetText(text);
      }

      @Override
      public void setWaitText (@Nullable CharSequence text) {
        if (StringUtils.isEmpty(text)) {
          resetWaitView.setVisibility(View.GONE);
        } else {
          resetWaitView.setText(text);
          resetWaitView.setVisibility(View.VISIBLE);
        }
      }

      @Override
      public void setHintText (@NonNull CharSequence text, boolean isError) {
        PasswordController.this.setHintText(text, isError);
      }

      @Override
      public void setLinkInProgress (boolean inProgress) {
        setInRecoveryProgress(inProgress);
      }

      @NonNull
      @Override
      public View linkView () {
        return forgotView;
      }

      @Override
      public void useDifferentEmail () {
        ViewController<?> previous = previousStackItem();
        if (!inProgress && previous instanceof PasswordController && previous.isSameTdlib(tdlib) && ((PasswordController) previous).getMode() == MODE_EMAIL_LOGIN) {
          navigateBack();
        }
      }
    }, fromEmailSetup, state);
  }

  // XGram: link of the phone-code login screen, see com.fahadali.xgram.login.PhoneCodeLinks
  private @Nullable com.fahadali.xgram.login.PhoneCodeLinks phoneCodeLinks;

  private com.fahadali.xgram.login.PhoneCodeLinks newPhoneCodeLinks (TdApi.AuthorizationStateWaitCode state) {
    return new com.fahadali.xgram.login.PhoneCodeLinks(this, tdlib, new com.fahadali.xgram.login.PhoneCodeLinks.Host() {
      @Override
      public void setLinkText (@Nullable CharSequence text) {
        setForgetText(text);
      }

      @Override
      public void setHintText (@NonNull CharSequence text, boolean isError) {
        PasswordController.this.setHintText(text, isError);
      }

      @Override
      public void setLinkInProgress (boolean inProgress) {
        setInRecoveryProgress(inProgress);
      }

      @NonNull
      @Override
      public View linkView () {
        return forgotView;
      }

      @Override
      public boolean isBusy () {
        return inProgress || inRecoveryProgress || qrLoginInProgress;
      }

      @Override
      public void requestNextCode () {
        requestNextCodeType(new TdApi.ResendCodeReasonUserRequest(), false);
      }

      @Override
      public void requestQrLogin () {
        PasswordController.this.requestQrLogin();
      }

      @Override
      public void emailSupport () {
        sendLoginHelpEmail();
      }
    }, state);
  }

  // XGram: on the 2FA login screen the "no recovery email" text comes with "Reset account", see com.fahadali.xgram.login.ResetAccount.
  // Not "Log in with QR code": a QR login of an account with a password ends at this same password step.
  private void showNoRecoveryEmail (@StringRes int textRes) {
    boolean isLogin = (mode == MODE_LOGIN || mode == MODE_LOGIN_EMAIL_RECOVERY) && authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR;
    if (!isLogin || !com.fahadali.xgram.login.ResetAccount.isAvailable(tdlib)) {
      openAlert(R.string.RestorePasswordNoEmailTitle, textRes);
      return;
    }
    com.fahadali.xgram.login.ResetAccount.showOptions(this, tdlib, Lang.getString(textRes), new com.fahadali.xgram.login.ResetAccount.Host() {
      @Override
      public boolean isBusy () {
        return inProgress || inRecoveryProgress;
      }

      @Override
      public void setInProgress (boolean inProgress) {
        setInRecoveryProgress(inProgress);
      }
    });
  }

  private void proceedForgot () {
    // XGram: the email-code screen has its own resend / reset menu
    if (mode == MODE_CODE_EMAIL && emailCodeLinks != null) {
      if (!inProgress) { // a second auth query would make TDLib abort the code check
        emailCodeLinks.onLinkClick();
      }
      return;
    }
    // XGram: the phone-code login screen sends the next code type when the timer is over, or opens a help sheet
    if (mode == MODE_CODE && phoneCodeLinks != null) {
      phoneCodeLinks.onLinkClick();
      return;
    }
    switch (mode) {
      case MODE_EMAIL_RECOVERY: {
        openAlert(R.string.ResetPassword, R.string.RestoreEmailTroubleText2, Lang.getString(R.string.Reset), (dialog, which) -> { confirmResetPassword(); });
        break;
      }
      case MODE_LOGIN_EMAIL_RECOVERY: {
        showNoRecoveryEmail(R.string.RestoreEmailTroubleText); // XGram: offers "Reset account"
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CODE_EMAIL: {
        requestNextCodeType(new TdApi.ResendCodeReasonUserRequest(), false);
        break;
      }
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CUSTOM_CONFIRM:
      case MODE_UNLOCK_EDIT:
      case MODE_LOGIN: {
        requestRecovery();
        break;
      }
      case MODE_NEW: {
        showNoRecoveryEmailAlert();
        break;
      }
      case MODE_LOGIN_EMAIL_CHANGE: {
        if (emailAddressAuthenticationCodeInfo != null) {
          tdlib.send(new TdApi.ResendLoginEmailAddressCode(), (emailAddressAuthenticationCodeInfo, error) -> runOnUiThreadOptional(() -> {
            if (error != null) {
              showErrorTooltip(forgotView, TD.toErrorString(error));
            } else {
              showInfoTooltip(forgotView, Lang.getStringBold(R.string.ChangeEmailResendConfirm, emailAddressAuthenticationCodeInfo.emailAddressPattern));
            }
          }));
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_done) {
      proceed();
    } else if (viewId == R.id.btn_cancelReset) {
      openAlert(R.string.ResetPassword, R.string.CancelPasswordReset, Lang.getString(R.string.CancelPasswordResetYes), (dialog, which) -> {
        cancelResetPassword();
      });
    } else if (viewId == R.id.btn_forgotPassword) {
      proceedForgot();
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return !inProgress && !hasUnsavedChanges();
  }

  private void setPassword (final String password, final String passwordHint, String email) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    tdlib.send(new TdApi.SetPassword(mode == MODE_NEW || getArguments() == null ? null : getArguments().oldPassword, password, passwordHint, mode != MODE_EDIT, email), (passwordState, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showError(error);
      } else {
        processNewPasswordState(passwordState, password);
      }
    }));
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().removeAuthorizationChangeListener(this);
    if (nextButton != null) {
      nextButton.destroy();
    }
    if (emailCodeLinks != null) { // XGram
      emailCodeLinks.destroy();
    }
    if (phoneCodeLinks != null) { // XGram
      phoneCodeLinks.destroy();
    }
  }

  private void processNewPasswordState (TdApi.PasswordState state, @Nullable String password) {
    this.state = state;
    ViewController<?> c;

    if (mode == MODE_NEW) {
      c = removeStackItemById(R.id.controller_2faSettings);
      if (c instanceof Settings2FAController) {
        ((Settings2FAController) c).updatePasswordState(state, password);
        navigateTo(c);
        return;
      }
    } else if (mode == MODE_EDIT || mode == MODE_2FA_RECOVERY_EMAIL_CHANGE) {
      c = findLastStackItemById(R.id.controller_2faSettings);
      if (c instanceof Settings2FAController) {
        ((Settings2FAController) c).updatePasswordState(state, password);
        navigateBack();
        return;
      }
    }
    c = findLastStackItemById(R.id.controller_privacySettings);
    if (c instanceof SettingsPrivacyController) {
      ((SettingsPrivacyController) c).updatePasswordState(state);
      Settings2FAController settings = new Settings2FAController(context, tdlib);
      settings.setArguments(new Settings2FAController.Args((SettingsPrivacyController) c, null, null));
      navigateTo(settings);
    }
  }
}
