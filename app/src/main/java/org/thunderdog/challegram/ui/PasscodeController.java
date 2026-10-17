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
 * File created on 07/08/2015 at 19:33
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.content.res.Configuration;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricPrompt;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;
import com.fahadali.xgram.appearance.XgramR5Security;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.passcode.InvisibleTransformationMethod;
import org.thunderdog.challegram.component.passcode.PasscodeView;
import org.thunderdog.challegram.component.passcode.PinInputLayout;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.BiometricAuthentication;
import org.thunderdog.challegram.core.GesturePassword;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ToggleHeaderView;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatPasscode;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.PasscodeBuilder;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.ToggleDelegate;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.SwirlView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class PasscodeController extends ViewController<PasscodeController.Args> implements Menu, TextView.OnEditorActionListener, PasscodeView.Callback, PinInputLayout.Callback, ToggleDelegate, Unlockable, GestureOverlayView.OnGesturePerformedListener, GesturePassword.Callback, BiometricAuthentication.Callback, BaseActivity.SimpleStateListener, Settings.PasscodeTickListener {
  public static class Args {
    public final TdApi.Chat chat;
    public final ChatPasscode passcode;
    public final TdlibUi.ChatOpenParameters chatOpenParameters;

    public Args (TdApi.Chat chat, ChatPasscode passcode, TdlibUi.ChatOpenParameters parameters) {
      this.chat = chat;
      this.passcode = passcode != null ? passcode : new ChatPasscode(Passcode.MODE_NONE, 0, "", null);
      this.chatOpenParameters = parameters;
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    if (specificChat == null)
      return false;
    if (chatOpenParameters != null && !chatOpenParameters.saveInstanceState(outState, keyPrefix))
      return false;
    super.saveInstanceState(outState, keyPrefix);
    outState.putLong(keyPrefix + "chat_id", specificChat.id);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    long chatId = in.getLong(keyPrefix + "chat_id");
    if (chatId == 0 || tdlib == null)
      return false;
    TdApi.Chat chat = tdlib.chatSync(chatId);
    ChatPasscode passcode = tdlib.chatPasscode(chat);
    if (passcode == null)
      return false;
    super.restoreInstanceState(in, keyPrefix);
    TdlibUi.ChatOpenParameters parameters = TdlibUi.ChatOpenParameters.restoreInstanceState(tdlib, in, keyPrefix);
    if (parameters == null)
      return false;
    setArguments(new Args(chat, passcode, parameters));
    return true;
  }

  public static final int MODE_UNLOCK = 0x00;
  public static final int MODE_SETUP = 0x01;
  public static final int MODE_UNLOCK_SETUP = 0x02;

  public PasscodeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private int controllerMode = MODE_UNLOCK;
  private int mode;

  private FrameLayoutFix contentView;
  private PasscodeView passcodeView;

  private TdApi.Chat specificChat;
  private ChatPasscode chatPasscode;
  private TdlibUi.ChatOpenParameters chatOpenParameters;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.specificChat = args.chat;
    this.chatPasscode = args.passcode;
    this.chatOpenParameters = args.chatOpenParameters;
  }

  @Override
  public long getChatId () {
    return specificChat != null ? specificChat.id : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_passcode;
  }

  public void setPasscodeMode (int mode) {
    controllerMode = mode;
  }

  private boolean inBiometricsSetup;
  public void setInBiometricsSetup () {
    inBiometricsSetup = true;
    forcedMode = Passcode.MODE_BIOMETRICS;
  }

  private int forcedMode;
  public void forceMode (int mode) {
    this.forcedMode = mode;
  }

  private SwirlView swirlView;

  private boolean inGlobalUnlockMode () {
    return controllerMode == MODE_UNLOCK && specificChat == null;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.passcodeIcon;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.passcode;
  }

  // XGram M3E (round 5): unlocking (the app lock, a locked chat, the check before the passcode settings) is the
  // Material 3 Expressive lock screen (XgramR5Security.LockView), the way XgramM3CallScreen replaced the call screen:
  // hero, headlineSmall title, an inline supporting / error line, PIN dots over a round keypad, the filled password
  // field, the M3E pattern or the fingerprint button. The logic below (codes, attempts, lockout, biometrics) is
  // Telegram X's; PasscodeView stays as the state holder and is not shown. Setting a passcode keeps Telegram X's page.

  private @Nullable XgramR5Security.LockView xgramLock;
  private @Nullable org.thunderdog.challegram.widget.MaterialEditTextGroup xgramPasswordGroup;
  private boolean xgramPasswordRevealed;

  private final XgramR5Security.LockView.Callback xgramLockCallback = new XgramR5Security.LockView.Callback() {
    @Override
    public void onLockKey (int digit) {
      onPinAppend(digit);
    }

    @Override
    public void onLockBackspace () {
      onPinRemove();
    }

    @Override
    public boolean onLockBackspaceLong () {
      return onPinRemoveAll();
    }

    @Override
    public void onLockBiometrics () {
      checkBiometricsNeeded();
    }

    @Override
    public void onLockPattern (PasscodeBuilder pattern) {
      handlePattern(pattern);
    }
  };

  private boolean xgramUseLock () {
    return XgramExpressive.isEnabled();
  }

  /** The fingerprint as a second way in (Telegram X's SwirlView at the bottom). */
  private boolean xgramExtraBiometrics () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !inSetupMode() && mode != Passcode.MODE_BIOMETRICS && needUnlockWithBiometrics();
  }

  // Setting a passcode (round 5, P2): a plain "Set passcode" app bar, the modes as a connected button group under it
  // (Telegram X's toggle title is not used), the step ("Choose your PIN", "Confirm your PIN") as the headlineSmall
  // title with a fade-through, mismatches and short codes inline, and the same keypad, dots and pattern.

  private @Nullable XgramR5Security.SegmentedRow xgramModes;
  private @Nullable com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton xgramPasswordButton;

  private boolean xgramSetup () {
    return xgramLock != null && controllerMode == MODE_SETUP;
  }

  /** An inline setup error (mismatch, too short) on the main thread. */
  private void xgramSetupError (@StringRes int res) {
    UI.post(() -> {
      if (xgramLock != null && !isDestroyed()) {
        if (pincode != null) {
          pincode.clear();
        }
        xgramLock.showError(Lang.getString(res), true);
      }
    });
  }

  private View xgramCreateLock (Context context) {
    contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        return (ev.getAction() == MotionEvent.ACTION_DOWN && !inSetupMode() && Settings.instance().isPasscodeBlocked(mode, makeBruteForceSuffix())) || super.onInterceptTouchEvent(ev);
      }
    };
    if (inSetupMode()) {
      return xgramCreateSetup(context);
    }
    if (inGlobalUnlockMode()) {
      // The app lock is its own page: surface (black with Pure black), read when drawn
      XgramExpressive.MorphDrawable page = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, Float.NaN);
      page.setColorRole(XgramExpressive.SURFACE);
      contentView.setBackground(page);
    } else {
      ViewSupport.setThemedBackground(contentView, ColorId.passcode, this); // the app bar's surface, as the page under it
    }
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    getCustomHeaderCell();

    passcodeView = new PasscodeView(context);
    passcodeView.setCallback(this);

    xgramLock = new XgramR5Security.LockView(context, xgramLockCallback, inGlobalUnlockMode());
    xgramLock.setTitle(Lang.getString(R.string.XgramR5SecurityEnterPasscode));
    contentView.addView(xgramLock, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    setMode(getPasscodeMode());
    if (xgramExtraBiometrics()) {
      setNeedBiometrics(true);
    }
    xgramUpdateSupporting();

    this.context.addSimpleStateListener(this);
    Settings.instance().addPasscodeTickLister(this);
    return contentView;
  }

  private View xgramCreateSetup (Context context) {
    ViewSupport.setThemedBackground(contentView, ColorId.passcode, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    passcodeView = new PasscodeView(context);
    passcodeView.setCallback(this);

    xgramLock = new XgramR5Security.LockView(context, xgramLockCallback, false);
    FrameLayoutFix.LayoutParams lockParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    if (!inBiometricsSetup) {
      final int initial = forcedMode != 0 ? forcedMode : isPasscodeEnabled() ? getPasscodeMode() : Passcode.MODE_PINCODE;
      xgramModes = new XgramR5Security.SegmentedRow(context, getToggleSections(), initial - 1, index -> {
        setMode(index + 1);
        if (xgramModes != null) {
          xgramModes.setSelectedSegment(mode - 1, true); // stays put when the mode cannot be used (no fingerprint)
        }
      });
      contentView.addView(xgramModes, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.TOP));
      lockParams.topMargin = Screen.dp(56f);
    }
    contentView.addView(xgramLock, lockParams);

    setMode(forcedMode != 0 ? forcedMode : isPasscodeEnabled() ? getPasscodeMode() : Passcode.MODE_PINCODE);
    if (xgramModes != null && mode > 0) {
      xgramModes.setSelectedSegment(mode - 1, false);
    }
    xgramUpdateSupporting();
    this.context.addSimpleStateListener(this);
    return contentView;
  }

  /** The resting supporting line: the instruction, or the lockout countdown in error. */
  private void xgramUpdateSupporting () {
    if (xgramLock == null || passcodeView == null) {
      return;
    }
    if (controllerMode == MODE_SETUP) {
      // The step is the title; the supporting line is kept for mismatches and short codes
      passcodeView.updateTextAndInvalidate();
      final String step = passcodeView.getText();
      xgramLock.setTitleAnimated(step != null ? step : "");
      xgramLock.setSupporting("", false);
      if (xgramPasswordButton != null) {
        xgramPasswordButton.setText(Lang.getString(passcodeView.getState() == Passcode.STATE_CONFIRM ? R.string.Done : R.string.Continue));
      }
      return;
    }
    passcodeView.updateTextAndInvalidate();
    final boolean blocked = Settings.instance().getPasscodeBlockSeconds(makeBruteForceSuffix()) > 0;
    final String text = passcodeView.getText();
    xgramLock.setSupporting(text != null ? text : "", blocked);
  }

  private String xgramInvalidText (int mode) {
    switch (mode) {
      case Passcode.MODE_PASSWORD:
        return XgramR5Security.sentence(R.string.PasscodeInvalidPassword, R.string.XgramR5SecurityInvalidPassword);
      case Passcode.MODE_PATTERN:
        return XgramR5Security.sentence(R.string.PasscodeInvalidPattern, R.string.XgramR5SecurityInvalidPattern);
      case Passcode.MODE_GESTURE:
        return XgramR5Security.sentence(R.string.PasscodeInvalidGesture, R.string.XgramR5SecurityInvalidGesture);
      default:
        return Lang.getString(getInvalidString(mode));
    }
  }

  /** A wrong code (main thread): the error inline, the dots shaken and cleared; a lockout that starts shows its countdown. */
  private void xgramShowError (int mode) {
    if (xgramLock == null || isDestroyed()) {
      return;
    }
    if (pincode != null) {
      pincode.clear();
    }
    xgramUpdateSupporting();
    if (xgramPasswordGroup != null && mode == Passcode.MODE_PASSWORD) {
      xgramPasswordGroup.setInErrorState(true);
    }
    final boolean blocked = Settings.instance().getPasscodeBlockSeconds(makeBruteForceSuffix()) > 0;
    xgramLock.showError(blocked ? passcodeView.getText() : xgramInvalidText(mode), true);
  }

  private void xgramPinAppend (int digit) {
    if (xgramLock == null || xgramLock.getFilled() >= Passcode.PINCODE_SIZE || Settings.instance().isPasscodeBlocked(Passcode.MODE_PINCODE, makeBruteForceSuffix())) {
      return; // full (being checked) or locked out
    }
    if (pincode == null) {
      pincode = new PasscodeBuilder();
    }
    if (pincode.getSize() >= Passcode.PINCODE_SIZE) {
      return;
    }
    pincode.append(digit);
    xgramLock.setFilled(pincode.getSize(), true);
    if (pincode.getSize() == Passcode.PINCODE_SIZE) {
      PasscodeBuilder code = new PasscodeBuilder(pincode);
      pincode.clear();
      handlePincode(code);
    }
  }

  private void xgramApplyPasswordTransformation () {
    if (passwordView == null) {
      return;
    }
    final int selection = passwordView.getSelectionEnd();
    passwordView.setTransformationMethod(xgramPasswordRevealed ? null : isPasscodeVisible() ? PasswordTransformationMethod.getInstance() : InvisibleTransformationMethod.instance());
    if (selection >= 0 && selection <= passwordView.length()) {
      passwordView.setSelection(selection);
    }
  }

  private void xgramShowPassword () {
    if (xgramLock == null) {
      return;
    }
    if (xgramPasswordGroup == null) {
      final boolean rtl = Lang.rtl();
      org.thunderdog.challegram.widget.MaterialEditTextGroup group = new org.thunderdog.challegram.widget.MaterialEditTextGroup(context(), tdlib);
      group.setHint(R.string.login_Password);
      group.applyRtl(rtl);
      passwordView = group.getEditText();
      passwordView.setUseIncognitoKeyboard(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      passwordView.setImeOptions(EditorInfo.IME_ACTION_DONE);
      passwordView.setOnEditorActionListener(this);
      passwordView.setPadding(passwordView.getPaddingLeft() + (rtl ? Screen.dp(40f) : 0), passwordView.getPaddingTop(), passwordView.getPaddingRight() + (rtl ? 0 : Screen.dp(40f)), passwordView.getPaddingBottom());
      xgramApplyPasswordTransformation();
      group.setTextListener((v, text) -> {
        v.setInErrorState(false);
        if (xgramLock != null) {
          xgramLock.onUserInput();
        }
      });

      // The trailing visibility toggle (a standard icon button on the field)
      android.widget.ImageView toggle = new android.widget.ImageView(context()) {
        private int tint;

        @Override
        protected void onDraw (android.graphics.Canvas c) {
          final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
          if (tint != color) {
            tint = color;
            setColorFilter(color);
          }
          super.onDraw(c);
        }
      };
      toggle.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      toggle.setBackground(com.fahadali.xgram.appearance.XgramExpressiveC.iconButtonRipple(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), 40f));
      final Runnable syncToggle = () -> {
        toggle.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(xgramPasswordRevealed ? R.drawable.baseline_eye_off_24 : R.drawable.baseline_visibility_24));
        toggle.setContentDescription(Lang.getString(xgramPasswordRevealed ? R.string.XgramR5SecurityHidePassword : R.string.XgramR5SecurityShowPassword));
      };
      syncToggle.run();
      toggle.setOnClickListener(v -> {
        xgramPasswordRevealed = !xgramPasswordRevealed;
        syncToggle.run();
        xgramApplyPasswordTransformation();
        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
      });
      FrameLayoutFix.LayoutParams toggleParams = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | (rtl ? Gravity.LEFT : Gravity.RIGHT));
      toggleParams.topMargin = Screen.dp(16f);
      toggleParams.leftMargin = toggleParams.rightMargin = Screen.dp(4f);
      group.addView(toggle, toggleParams);

      com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton unlock = new com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton(context(), XgramExpressive.PRIMARY, XgramExpressive.ON_PRIMARY);
      unlock.setText(Lang.getString(inSetupMode() ? R.string.Continue : R.string.XgramR5SecurityUnlock));
      unlock.setOnClickListener(v -> processPasscode());

      xgramPasswordGroup = group;
      xgramPasswordButton = unlock;
    }
    if (xgramPasswordButton != null) {
      xgramLock.setPasswordViews(xgramPasswordGroup, xgramPasswordButton, xgramExtraBiometrics());
    }
    if (inGlobalUnlockMode()) {
      UI.showKeyboardDelayed(passwordView);
    } else if (!isNavigationAnimating()) {
      Keyboard.show(passwordView);
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, org.thunderdog.challegram.theme.ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (xgramLock != null) {
      xgramLock.refreshColors();
    }
    if (xgramModes != null) {
      xgramModes.refreshColors();
    }
    if (xgramLock != null && gestureView != null) {
      final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
      gestureView.setGestureColor(primary);
      gestureView.setUncertainGestureColor(primary);
    }
  }

  @Override
  protected View onCreateView (Context context) {
    if (xgramUseLock()) {
      return xgramCreateLock(context);
    }
    contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        return (ev.getAction() == MotionEvent.ACTION_DOWN && !inSetupMode() && Settings.instance().isPasscodeBlocked(mode, makeBruteForceSuffix())) || super.onInterceptTouchEvent(ev);
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.passcode, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    getCustomHeaderCell();

    passcodeView = new PasscodeView(context);
    passcodeView.setCallback(this);
    if (inGlobalUnlockMode()) {
      passcodeView.setPadding(0, HeaderView.getSize(true), 0, 0);
      passcodeView.setDisplayLogo();
    }
    if (inSetupMode()) {
      setMode(forcedMode != 0 ? forcedMode : isPasscodeEnabled() ? getPasscodeMode() : Passcode.MODE_PINCODE);
    } else {
      setMode(getPasscodeMode());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mode != Passcode.MODE_BIOMETRICS && needUnlockWithBiometrics()) {
        swirlView = new SwirlView(context);
        swirlView.setOnClickListener(v -> checkBiometricsNeeded());
        swirlView.setColorFilter(ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.passcodeIcon)));
        addThemeFilterListener(swirlView, ColorId.passcodeIcon).setIsSubtitle(true);
        swirlView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(36f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, Screen.dp(18f)));
        setNeedBiometrics(true);
        contentView.addView(swirlView);
      }
    }
    contentView.addView(passcodeView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    this.context.addSimpleStateListener(this);

    if (!inSetupMode()) {
      Settings.instance().addPasscodeTickLister(this);
    }

    return contentView;
  }

  public void fadeOut (Animator.AnimatorListener listener) {
    Views.animateAlpha(getValue(), 0f, 100l, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  private void setMode (int mode) {
    if (this.mode == mode) return;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (mode == Passcode.MODE_BIOMETRICS && BiometricAuthentication.isMissingEnrolledData(needStrongBiometrics())) {
        UI.showToast(BiometricAuthentication.ONLY_FINGERPRINT ? R.string.fingerprint_hint3 : R.string.biometrics_hint3, Toast.LENGTH_SHORT);
        if (controllerMode == MODE_SETUP) {
          return;
        }
      }
    }

    if (xgramSetup() && !inBiometricsSetup) {
      setName(Lang.getString(R.string.XgramR5SecuritySetPasscode)); // XGram M3E: a plain app bar; the modes are under it
    } else {
      setName(Passcode.getModeName(mode));
    }
    updateHeaderText();

    if (this.mode != 0) {
      switch (this.mode) {
        case Passcode.MODE_PASSWORD: {
          hidePasswordWrap();
          break;
        }
        case Passcode.MODE_PINCODE: {
          hidePincodeWrap();
          break;
        }
        case Passcode.MODE_GESTURE: {
          hideGestureWrap();
          break;
        }
        case Passcode.MODE_BIOMETRICS: {
          hideBiometricsIcon();
          break;
        }
      }
    }

    this.mode = mode;

    if (mode == Passcode.MODE_BIOMETRICS) {
      passcodeView.setExtraData(BitwiseUtils.optional(Passcode.BIOMETRICS_OPTION_ONLY_STRONG, needStrongBiometrics()));
    }
    passcodeView.setModeAndState(mode, controllerMode == MODE_SETUP ? Passcode.STATE_CHOOSE : Passcode.STATE_UNLOCK);

    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        showPasswordWrap();
        break;
      }
      case Passcode.MODE_PINCODE: {
        showPincodeWrap();
        break;
      }
      case Passcode.MODE_GESTURE: {
        showGestureWrap();
        break;
      }
      case Passcode.MODE_BIOMETRICS: {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          showBiometricsIcon();
        }
        break;
      }
      case Passcode.MODE_PATTERN: {
        if (xgramLock != null) {
          // XGram M3E: the lock's own pattern (PasscodeView draws Telegram X's)
          xgramLock.setContent(XgramR5Security.LockView.CONTENT_PATTERN, 0, isPasscodeVisible(), xgramExtraBiometrics());
        }
        break;
      }
    }

    if (controllerMode == MODE_SETUP) {
      updateConfirmMode();
    }
  }

  private String confirmPassword;
  private EditText passwordView;

  private void showPasswordWrap () {
    if (xgramLock != null) {
      xgramShowPassword();
      return;
    }
    if (passwordView == null) {
      passwordView = (EditText) Views.inflate(context(), R.layout.input_password, contentView);
      passwordView.setTypeface(Fonts.getRobotoRegular());
      passwordView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      passwordView.setUseIncognitoKeyboard(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      passwordView.setTransformationMethod(isPasscodeVisible() ? PasswordTransformationMethod.getInstance() : InvisibleTransformationMethod.instance());
      passwordView.setGravity(Gravity.CENTER);
      passwordView.setTextColor(Theme.getColor(ColorId.passcodeText));
      passwordView.setOnEditorActionListener(this);
      passwordView.setImeOptions(EditorInfo.IME_ACTION_DONE);
      addThemeTextColorListener(passwordView, ColorId.passcodeText);
      // Views.setCursorColor(passwordView, 0xffadcae0, R.drawable.cursor_blue);

      ViewUtils.setBackground(passwordView, null);

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(43f));
      params.setMargins(Screen.dp(44f), Screen.dp(127f), Screen.dp(44f), 0);

      if (inGlobalUnlockMode()) {
        params.topMargin += HeaderView.getSize(true);
      }

      passwordView.setLayoutParams(params);
    }

    contentView.addView(passwordView);

    if (inGlobalUnlockMode()) {
      UI.showKeyboardDelayed(passwordView);
    } else if (!isNavigationAnimating()) {
      Keyboard.show(passwordView);
    }
  }

  private void hidePasswordWrap () {
    Keyboard.hide(passwordView);
    contentView.removeView(passwordView);
  }

  private PinInputLayout pinLayout;

  private void showPincodeWrap () {
    if (xgramLock != null) {
      xgramLock.setContent(XgramR5Security.LockView.CONTENT_PIN, Passcode.PINCODE_SIZE, isPasscodeVisible(), xgramExtraBiometrics());
      return;
    }
    if (pinLayout == null) {
      pinLayout = new PinInputLayout(context());
      pinLayout.initWithFeedback(isPasscodeVisible());
      pinLayout.setCallback(this);
    }
    updatePincodeOrientation();

    contentView.addView(pinLayout);
  }

  private void updatePincodeOrientation () {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(pinLayout.getLayoutParams());
    if (UI.getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
      params.gravity = Gravity.RIGHT | Gravity.TOP;
      params.topMargin = 0;
    } else {
      params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      params.topMargin = Screen.dp(156f);
    }
    if (inGlobalUnlockMode()) {
      params.topMargin += HeaderView.getSize(true);
    }
    pinLayout.updateHeights();
    pinLayout.setLayoutParams(params);
  }

  private void hidePincodeWrap () {
    if (pincode != null) {
      pincode.clear();
    }
    if (xgramLock != null) {
      xgramLock.setFilled(0, false);
      return;
    }
    passcodeView.getPincodeOutput().clear();
    contentView.removeView(pinLayout);
  }

  private PasscodeBuilder pincode;

  @Override
  public void onPinRemove () {
    if (xgramLock != null) {
      if (pincode != null && pincode.getSize() > 0 && xgramLock.getFilled() < Passcode.PINCODE_SIZE) {
        pincode.removeLast();
        xgramLock.setFilled(pincode.getSize(), true);
      }
      return;
    }
    if (passcodeView.getPincodeOutput().isAnimating()) return;
    if (pincode != null) {
      pincode.removeLast();
    }
    passcodeView.getPincodeOutput().remove();
  }

  @Override
  public boolean onPinRemoveAll () {
    if (xgramLock != null) {
      if (pincode != null && pincode.getSize() > 0 && xgramLock.getFilled() < Passcode.PINCODE_SIZE) {
        pincode.clear();
        xgramLock.setFilled(0, true);
        return true;
      }
      return false;
    }
    if (passcodeView.getPincodeOutput().isAnimating()) return false;
    if (passcodeView.getPincodeOutput().removeAll()) {
      if (pincode != null) {
        pincode.clear();
      }
      return true;
    }
    return false;
  }

  @Override
  public void onPinAppend (int digit) {
    if (xgramLock != null) {
      xgramPinAppend(digit);
      return;
    }
    if (passcodeView.getPincodeOutput().isAnimating() || (pincode != null && pincode.getSize() >= Passcode.PINCODE_SIZE)) return;
    if (pincode == null) {
      pincode = new PasscodeBuilder();
    }
    pincode.append(digit);
    passcodeView.getPincodeOutput().append();

    if (pincode.getSize() == Passcode.PINCODE_SIZE) {
      handlePincode(new PasscodeBuilder(pincode));
      pincode.clear();
    }
  }

  // Gesture

  private GestureOverlayView gestureView;

  private void showGestureWrap () {
    if (gestureView == null) {
      gestureView = new GestureOverlayView(context()) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent e) {
          // XGram M3E: a touch starting on the lock's fingerprint button (under this overlay) goes to the button
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN && xgramLock != null && xgramLock.isOnBiometricsButton(e.getRawX(), e.getRawY())) {
            return false;
          }
          return super.dispatchTouchEvent(e);
        }
      };
      gestureView.setGestureStrokeWidth(Screen.dp(3f));
      gestureView.setOrientation(GestureOverlayView.ORIENTATION_VERTICAL);
      gestureView.setGestureColor(Theme.getColor(ColorId.passcodeIcon));
      gestureView.setUncertainGestureColor(Theme.getColor(ColorId.passcodeIcon));
      gestureView.setGestureVisible(isPasscodeVisible());
      gestureView.setFadeEnabled(true);
      gestureView.addOnGesturePerformedListener(this);
      gestureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    if (xgramLock != null) {
      // XGram M3E: the stroke in primary, over the lock column
      final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
      gestureView.setGestureColor(primary);
      gestureView.setUncertainGestureColor(primary);
      gestureView.setGestureStrokeWidth(Screen.dp(4f));
      // The modes stay usable: the overlay starts under them
      FrameLayoutFix.LayoutParams gestureParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      gestureParams.topMargin = xgramModes != null ? Screen.dp(56f) : 0;
      gestureView.setLayoutParams(gestureParams);
      xgramLock.setContent(XgramR5Security.LockView.CONTENT_GESTURE, 0, isPasscodeVisible(), xgramExtraBiometrics());
    }

    contentView.addView(gestureView);
  }

  private void hideGestureWrap () {
    contentView.removeView(gestureView);
  }

  private boolean ignoreGesture;

  @Override
  public void onGesturePerformed (GestureOverlayView overlay, Gesture gesture) {
    if (!ignoreGesture) {
      processGesture(gesture);
    }
  }

  private void processGestureSetup (final Gesture gesture) {
    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      Background.instance().post(() -> {
        if (!getGesturePassword().compare(gesture, true, null)) {
          if (xgramLock != null) {
            xgramSetupError(getMismatchString(Passcode.MODE_GESTURE)); // XGram M3E: inline
          } else {
            UI.showToast(getMismatchString(Passcode.MODE_GESTURE), Toast.LENGTH_SHORT);
          }
          return;
        }

        if (getGesturePassword().save(gesture)) {
          setGesture();
          postNavigateBack();
        } else {
          UI.showToast("Error saving gesture file", Toast.LENGTH_SHORT);
        }
      });
      return;
    }

    passcodeView.setState(Passcode.STATE_CONFIRM);
    ignoreGesture = true;

    Background.instance().post(() -> {
      getGesturePassword().saveTemp(gesture);
      ignoreGesture = false;
    });
  }

  @Override
  public void onGestureLoadError () {
    UI.showToast("Error loading an existing gesture", Toast.LENGTH_SHORT);
    ignoreGesture = false;
  }

  // Biometrics

  private SwirlView biometricsView;

  private void updateBiometricsViewParams (FrameLayoutFix.LayoutParams params, int orientation) {
    params.gravity = orientation == Configuration.ORIENTATION_LANDSCAPE ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL | Gravity.TOP;
    params.leftMargin = params.rightMargin = Screen.dp(44f);
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      params.topMargin = 0;
    } else {
      params.topMargin = Screen.dp(118f);
      if (inGlobalUnlockMode()) {
        params.topMargin += HeaderView.getSize(true);
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private void showBiometricsIcon () {
    if (xgramLock != null) {
      xgramLock.setContent(XgramR5Security.LockView.CONTENT_BIOMETRICS, 0, true, false);
      setNeedBiometrics(true);
      return;
    }
    if (biometricsView == null) {
      biometricsView = new SwirlView(context);
      biometricsView.setOnClickListener(v -> checkBiometricsNeeded());
      biometricsView.setColorFilter(ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.passcodeIcon)));
      addThemeFilterListener(biometricsView, ColorId.passcodeIcon).setIsSubtitle(true);

      int orientation = UI.getOrientation();

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(Screen.dp(82f), Screen.dp(82f));
      updateBiometricsViewParams(params, orientation);

      biometricsView.setLayoutParams(params);
    } else if (inSetupMode()) {
      biometricsView.setState(SwirlView.State.OFF, false);
    }
    contentView.addView(biometricsView);
    setNeedBiometrics(true);
  }

  @Override
  public boolean needsTempUpdates () {
    return context.isPasscodeShowing();
  }

  private long confirmBiometricsId;
  private boolean confirmBiometricsStrong;
  private boolean biometricsScanCompleted;

  @Override
  @RequiresApi(Build.VERSION_CODES.M)
  public void onAuthenticated (BiometricPrompt.AuthenticationResult result, boolean strong) {
    final int biometricsId = 0; // strong ? 1 : 0;
    isBiometricAuthenticationActive = false;
    if (controllerMode != MODE_SETUP) {
      if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().compareBiometrics(biometricsId, strong)) {
        biometricsScanCompleted = true;
        navigateTo(new PasscodeSetupController(context, tdlib));
      } else {
        if (controllerMode == MODE_UNLOCK && unlockByBiometrics(biometricsId, strong)) {
          biometricsScanCompleted = true;
          UI.unlock(PasscodeController.this);
        } else if (xgramLock != null) {
          final int failRes = BiometricAuthentication.ONLY_FINGERPRINT ? R.string.fingerprint_fail : R.string.biometrics_fail;
          runOnUiThreadOptional(() -> {
            if (xgramLock != null) {
              xgramLock.showError(Lang.getString(failRes), false); // XGram M3E: inline
            }
          });
        } else {
          UI.showToast(BiometricAuthentication.ONLY_FINGERPRINT ? R.string.fingerprint_fail : R.string.biometrics_fail, Toast.LENGTH_SHORT);
        }
        checkBiometricsNeeded();
      }
    } else {
      if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
        if (confirmBiometricsId == biometricsId && confirmBiometricsStrong == strong) {
          biometricsScanCompleted = true;
          Background.instance().post(() -> {
            if (inBiometricsSetup) {
              enableUnlockByBiometrics(biometricsId, strong);
            } else {
              setBiometrics(biometricsId, strong);
            }
            postNavigateBack();
          });
        } else {
          if (xgramLock != null) {
            xgramSetupError(getMismatchString(Passcode.MODE_BIOMETRICS)); // XGram M3E: inline
          } else {
            UI.showToast(getMismatchString(Passcode.MODE_BIOMETRICS), Toast.LENGTH_SHORT);
          }
          checkBiometricsNeeded();
        }
      } else {
        this.confirmBiometricsId = biometricsId;
        this.confirmBiometricsStrong = strong;
        passcodeView.setExtraData(BitwiseUtils.optional(Passcode.BIOMETRICS_OPTION_ONLY_STRONG, strong));
        passcodeView.setState(Passcode.STATE_CONFIRM);
        setNeedBiometrics(true);
        if (biometricsView != null) {
          biometricsView.showDelayed(0);
        }
        checkBiometricsNeeded();
      }
    }
  }

  private void hideBiometricsIcon () {
    setNeedBiometrics(false);
    contentView.removeView(biometricsView);
  }

  @Override
  public void onRequestPermissionResult (int requestCode, boolean success) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && requestCode == BaseActivity.REQUEST_USE_FINGERPRINT) {
      if (controllerMode == MODE_SETUP) {
        if (success) {
          if (BiometricAuthentication.isAvailable()) {
            setMode(Passcode.MODE_BIOMETRICS);
          } else {
            // UI.showToast(R.string.fingerprint_unavailable, Toast.LENGTH_LONG);
          }
        } else {
          // UI.showToast(R.string.fingerprint_hint, Toast.LENGTH_LONG);
        }
      } else {
        if (!success) {
          // UI.showToast(R.string.fingerprint_hint2, Toast.LENGTH_LONG);
        }
      }
    }
  }

  public void updateBiometricsViewOrientation () {
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) biometricsView.getLayoutParams();
    updateBiometricsViewParams(params, UI.getOrientation());
    biometricsView.setLayoutParams(params);
  }

  // Other stuff

  @Override
  public boolean inSetupMode () {
    return controllerMode == MODE_SETUP;
  }

  @Override
  public void updateConfirmMode () {
    if (xgramLock != null) {
      // XGram M3E: the confirm step may hide what is typed (an invisible passcode): rebuild the content for it
      switch (mode) {
        case Passcode.MODE_PINCODE:
          if (pincode != null) {
            pincode.clear();
          }
          xgramLock.setContent(XgramR5Security.LockView.CONTENT_PIN, Passcode.PINCODE_SIZE, isPasscodeVisible(), xgramExtraBiometrics());
          break;
        case Passcode.MODE_PATTERN:
          xgramLock.setContent(XgramR5Security.LockView.CONTENT_PATTERN, 0, isPasscodeVisible(), xgramExtraBiometrics());
          break;
        case Passcode.MODE_PASSWORD:
          xgramApplyPasswordTransformation();
          break;
        case Passcode.MODE_GESTURE:
          if (gestureView != null) {
            gestureView.setGestureVisible(isPasscodeVisible());
          }
          break;
      }
      xgramUpdateSupporting();
      return;
    }
    switch (mode) {
      case Passcode.MODE_PINCODE: {
        pinLayout.setHasFeedback(isPasscodeVisible());
        break;
      }
      case Passcode.MODE_PASSWORD: {
        passwordView.setTransformationMethod(isPasscodeVisible() ? PasswordTransformationMethod.getInstance() : InvisibleTransformationMethod.instance());
        break;
      }
      case Passcode.MODE_GESTURE: {
        gestureView.setGestureVisible(isPasscodeVisible());
        break;
      }
    }
  }

  @Override
  public boolean isPasscodeVisible () {
    if (Config.DISABLE_PASSWORD_INVISIBILITY && mode == Passcode.MODE_PASSWORD)
      return true;
    boolean isVisible;
    if (specificChat != null) {
      isVisible = chatPasscode == null || chatPasscode.isVisible();
    } else {
      isVisible = Passcode.instance().isVisible();
    }
    return isVisible || (inSetupMode() && passcodeView.getState() != Passcode.STATE_CONFIRM);
  }

  private boolean processPasswordSetup (String password) {
    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (Passcode.isValidPassword(password) && password.equals(confirmPassword)) {
        setPassword(password);
        Keyboard.hide(passwordView);
        navigateBack();
        return true;
      } else if (xgramLock != null) {
        if (xgramPasswordGroup != null) {
          xgramPasswordGroup.setInErrorState(true);
        }
        xgramSetupError(getMismatchString(Passcode.MODE_PASSWORD)); // XGram M3E: the field's error, inline
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PASSWORD), Toast.LENGTH_SHORT);
      }
      return false;
    }

    if (password.length() < Passcode.MIN_PASSWORD_SIZE) {
      if (xgramLock != null) {
        xgramSetupError(R.string.passcode_password_tooshort);
        return false;
      }
      UI.showToast(R.string.passcode_password_tooshort, Toast.LENGTH_SHORT);
      return false;
    }

    this.confirmPassword = password;

    passcodeView.setState(Passcode.STATE_CONFIRM);
    passwordView.setText("");

    return false;
  }

  private PasscodeBuilder confirmPasscode;

  private static @StringRes int getInvalidString (int mode) {
    switch (mode) {
      case Passcode.MODE_GESTURE:
        return R.string.PasscodeInvalidGesture;
      case Passcode.MODE_PINCODE:
        return R.string.PasscodeInvalidPin;
      case Passcode.MODE_PASSWORD:
        return R.string.PasscodeInvalidPassword;
      case Passcode.MODE_PATTERN:
        return R.string.PasscodeInvalidPattern;
    }
    throw new IllegalArgumentException("mode == " + mode);
  }

  private static @StringRes int getMismatchString (int mode) {
    switch (mode) {
      case Passcode.MODE_GESTURE:
        return R.string.PasscodeMismatchGesture;
      case Passcode.MODE_PINCODE:
        return R.string.PasscodeMismatchPin;
      case Passcode.MODE_PASSWORD:
        return R.string.PasscodeMismatchPassword;
      case Passcode.MODE_PATTERN:
        return R.string.PasscodeMismatchPattern;
      case Passcode.MODE_BIOMETRICS:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          if (BiometricAuthentication.ONLY_FINGERPRINT) {
            return R.string.PasscodeMismatchFingerprint;
          } else {
            return R.string.PasscodeMismatchBiometrics;
          }
        }
        break;
    }
    throw new IllegalArgumentException("mode == " + mode);
  }

  @Override
  public void handlePattern (final PasscodeBuilder pattern) {
    if (!inSetupMode()) {
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PATTERN, makeBruteForceSuffix())) {
        return;
      }
      Background.instance().post(() -> {
        String patternStr = pattern.toString();
        if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePattern(patternStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PATTERN, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && unlockByPattern(patternStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PATTERN, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_PATTERN, patternStr, makeBruteForceSuffix());
          if (xgramLock != null) {
            UI.post(() -> xgramShowError(Passcode.MODE_PATTERN)); // XGram M3E: inline, not a toast
          } else {
            UI.showToast(getInvalidString(Passcode.MODE_PATTERN), Toast.LENGTH_SHORT);
          }
        }
      });
      return;
    }

    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (pattern.compare(confirmPasscode)) {
        Background.instance().post(() -> {
          String patternStr = pattern.toString();
          if (Passcode.isValidPattern(patternStr)) {
            setPattern(patternStr);
            postNavigateBack();
          } else {
            UI.showToast("Error setting up pattern", Toast.LENGTH_SHORT);
          }
        });
      } else if (xgramLock != null) {
        xgramSetupError(getMismatchString(Passcode.MODE_PATTERN)); // XGram M3E: inline
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PATTERN), Toast.LENGTH_SHORT);
      }
      return;
    }

    if (pattern.getSize() < Passcode.MIN_PATTERN_SIZE) {
      if (xgramLock != null) {
        xgramSetupError(R.string.passcode_pattern_tooshort);
        return;
      }
      UI.showToast(R.string.passcode_pattern_tooshort, Toast.LENGTH_SHORT);
      return;
    }

    this.confirmPasscode = new PasscodeBuilder(pattern);

    passcodeView.setState(Passcode.STATE_CONFIRM);
  }

  @Override
  public void handlePincode (final PasscodeBuilder pincode) {
    if (!inSetupMode()) {
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PINCODE, makeBruteForceSuffix())) {
        if (xgramLock != null) {
          xgramLock.setFilled(0, true); // XGram M3E: the dots never stay full while locked out
        }
        return;
      }
      Background.instance().post(() -> {
        String pincodeStr = pincode.toString();
        if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePincode(pincodeStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PINCODE, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && unlockByPincode(pincodeStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PINCODE, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_PINCODE, pincodeStr, makeBruteForceSuffix());
          if (xgramLock != null) {
            UI.post(() -> xgramShowError(Passcode.MODE_PINCODE)); // XGram M3E: inline error, the dots shake and clear
            return;
          }
          UI.showToast(getInvalidString(Passcode.MODE_PINCODE), Toast.LENGTH_SHORT);
          UI.post(() -> passcodeView.getPincodeOutput().removeAll());
        }
      });
      return;
    }

    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (pincode.compare(confirmPasscode)) {
        Background.instance().post(() -> {
          String pincodeStr = pincode.toString();
          if (Passcode.isValidPincode(pincodeStr)) {
            setPincode(pincodeStr);
            postNavigateBack();
          } else {
            UI.showToast("Error setting up pincode", Toast.LENGTH_SHORT);
          }
        });
      } else if (xgramLock != null) {
        xgramSetupError(getMismatchString(Passcode.MODE_PINCODE)); // XGram M3E: inline, the dots shake and clear
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PINCODE), Toast.LENGTH_SHORT);
        passcodeView.getPincodeOutput().removeAll();
      }
      return;
    }

    if (pincode.getSize() != Passcode.PINCODE_SIZE) {
      if (xgramLock != null) {
        xgramSetupError(R.string.passcode_pattern_tooshort);
        return;
      }
      UI.showToast(R.string.passcode_pattern_tooshort, Toast.LENGTH_SHORT);
      return;
    }

    this.confirmPasscode = new PasscodeBuilder(pincode);

    passcodeView.setState(Passcode.STATE_CONFIRM);
    if (xgramLock != null) {
      return; // XGram M3E: updateConfirmMode gave the confirm step empty dots
    }
    passcodeView.getPincodeOutput().removeAll();
  }

  private void processGesture (final Gesture gesture) {
    if (this.mode == Passcode.MODE_GESTURE) {
      if (inSetupMode()) {
        processGestureSetup(gesture);
        return;
      }
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_GESTURE, makeBruteForceSuffix())) {
        return;
      }
      Background.instance().post(() -> {
        if (controllerMode == MODE_UNLOCK_SETUP && getGesturePassword().compare(gesture, false, PasscodeController.this)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_GESTURE, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && getGesturePassword().compare(gesture, false, PasscodeController.this)) {
          unlockByGesture();
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_GESTURE, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_GESTURE, null, makeBruteForceSuffix());
          if (xgramLock != null) {
            UI.post(() -> xgramShowError(Passcode.MODE_GESTURE));
          } else {
            UI.showToast(getInvalidString(Passcode.MODE_GESTURE), Toast.LENGTH_SHORT);
          }
        }
      });
    }
  }

  private boolean processPasscode () {
    switch (this.mode) {
      case Passcode.MODE_PINCODE:
      case Passcode.MODE_PATTERN:
      case Passcode.MODE_GESTURE: {
        UI.showToast(passcodeView.getText(), Toast.LENGTH_SHORT);
        break;
      }
      case Passcode.MODE_PASSWORD: {
        final String password = passwordView.getText().toString();

        if (inSetupMode()) {
          return processPasswordSetup(password);
        }

        if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PASSWORD, makeBruteForceSuffix())) {
          return false;
        }

        Background.instance().post(() -> {
          if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePassword(password)) {
            Settings.instance().forgetPasscodeErrors(Passcode.MODE_PASSWORD, makeBruteForceSuffix());
            navigateTo(new PasscodeSetupController(context, tdlib));
          } else if (controllerMode == MODE_UNLOCK && unlockByPassword(password)) {
            Settings.instance().forgetPasscodeErrors(Passcode.MODE_PASSWORD, makeBruteForceSuffix());
            UI.unlock(PasscodeController.this);
          } else {
            Settings.instance().tracePasscodeError(Passcode.MODE_PASSWORD, password, makeBruteForceSuffix());
            if (xgramLock != null) {
              UI.post(() -> xgramShowError(Passcode.MODE_PASSWORD)); // XGram M3E: the field's error, inline
            } else {
              UI.showToast(getInvalidString(Passcode.MODE_PASSWORD), Toast.LENGTH_SHORT);
            }
          }
        });

        break;
      }
    }
    return false;
  }

  public void unlockInterface () {
    hideTooltipsAndPopUps();
    if (specificChat != null) {
      tdlib.ui().openChat(this, specificChat, (chatOpenParameters != null ? chatOpenParameters : new TdlibUi.ChatOpenParameters()).passcodeUnlocked());
    } else if (xgramLock != null && !XgramMotion.reduce() && contentView != null && contentView.isAttachedToWindow()) {
      // XGram M3E: the column scales to .92 and fades on SPATIAL_DEFAULT; the page fades out (BaseActivity) as it goes
      if (xgramLock.isExiting()) {
        return;
      }
      xgramLock.playExit();
      contentView.postDelayed(() -> context().hidePasscode(), 140L);
    } else {
      context().hidePasscode();
    }
  }

  @Override
  public int getRootColorId () {
    return ColorId.passcode;
  }

  @Override
  public void unlock () {
    unlockInterface();
  }

  @Override
  protected int getMenuId () {
    return 0; // controllerMode == MODE_UNLOCK || (controllerMode == MODE_UNLOCK_SETUP && mode != Passcode.MODE_PASSWORD) ? 0 : R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    header.addDoneButton(menu, this);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) {
      processPasscode();
    }
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    return actionId == EditorInfo.IME_ACTION_DONE && !processPasscode();
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !passcodeView.isIntercepted() && mode != Passcode.MODE_GESTURE && (xgramLock == null || !xgramLock.isTracking());
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    passcodeView.setOrientation(newConfig.orientation);
    if (xgramLock != null) {
      xgramLock.requestLayout(); // XGram M3E: the column centres itself (and its keys fit) in the new size
      return;
    }
    switch (mode) {
      case Passcode.MODE_PINCODE: {
        updatePincodeOrientation();
        break;
      }
      case Passcode.MODE_BIOMETRICS: {
        updateBiometricsViewOrientation();
        break;
      }
    }
  }

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    checkBiometricsNeeded();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (xgramLock != null && xgramLock.getFilled() >= Passcode.PINCODE_SIZE) {
      xgramLock.setFilled(0, false); // back from the passcode settings: an empty PIN again
    }
    if (mode == Passcode.MODE_PASSWORD) {
      UI.showKeyboardDelayed(passwordView);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      int biometricsDelay = controllerMode == MODE_UNLOCK_SETUP || specificChat != null ? 0 : controllerMode == MODE_UNLOCK ? 300 : 100;
      if (swirlView != null) {
        swirlView.showDelayed(biometricsDelay);
      }
      if (biometricsView != null && controllerMode != MODE_SETUP) {
        biometricsView.showDelayed(biometricsDelay);
      }
    }
  }

  private TooltipOverlayView.TooltipInfo tooltipInfo;

  @Override
  public void onAuthenticationError (CharSequence error, boolean isFatal) {
    if (xgramLock != null) {
      // XGram M3E: the error in the supporting line, no tooltip or toast; the fingerprint button retries
      final CharSequence text = error;
      runOnUiThreadOptional(() -> {
        if (xgramLock != null && text != null && text.length() > 0) {
          xgramLock.showError(text, false);
        }
      });
      runOnUiThreadOptional(() -> {
        if (isFatal && (controllerMode == MODE_SETUP || controllerMode == MODE_UNLOCK_SETUP)) {
          navigateBack();
        } else {
          isBiometricAuthenticationActive = false;
        }
      }, null, 2000L);
      return;
    }
    if (tooltipInfo != null) {
      tooltipInfo.hideNow();
      tooltipInfo = null;
    }
    View target = Views.isValid(swirlView) ? swirlView : Views.isValid(biometricsView) ? biometricsView : null;
    if (target != null && isFocused()) {
      tooltipInfo = context.tooltipManager().builder(target).icon(R.drawable.baseline_error_24).show(tdlib, error);
    } else {
      UI.showToast(error, Toast.LENGTH_SHORT);
      tooltipInfo = null;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (swirlView != null) {
        swirlView.showError(isFatal);
      }
      if (biometricsView != null) {
        biometricsView.showError(isFatal);
      }
    }
    runOnUiThreadOptional(() -> {
      if (tooltipInfo != null) {
        tooltipInfo.hideNow();
      }
      if (isFatal && (controllerMode == MODE_SETUP || controllerMode == MODE_UNLOCK_SETUP)) {
        navigateBack();
      } else {
        isBiometricAuthenticationActive = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          if (swirlView != null) {
            swirlView.setState(SwirlView.State.ON, true);
          }
          if (biometricsView != null) {
            biometricsView.setState(SwirlView.State.ON, true);
          }
        }
      }
    }, null, 2000L);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    hideTooltipsAndPopUps();
  }

  private void hideTooltipsAndPopUps () {
    if (mode == Passcode.MODE_PASSWORD) {
      Keyboard.hide(passwordView);
    }
    if (tooltipInfo != null) {
      tooltipInfo.hideNow();
      tooltipInfo = null;
    }
  }

  // Toggler

  private View headerCell;

  private void updateHeaderText () {
    if (headerCell instanceof ToggleHeaderView) {
      ((ToggleHeaderView) headerCell).setText(getName());
    } else if (headerCell instanceof DoubleHeaderView) {
      ((DoubleHeaderView) headerCell).setTitle(getName());
    }
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.passcodeText;
  }

  @Override
  public View getCustomHeaderCell () {
    if (controllerMode == MODE_UNLOCK && specificChat != null) {
      if (headerCell == null) {
        DoubleHeaderView headerCell = new DoubleHeaderView(context);
        headerCell.setThemedTextColor(this);
        headerCell.initWithMargin(Screen.dp(49f), true);
        headerCell.setSubtitle(Lang.getStringBold(R.string.SecretChatWithUser, tdlib.chatTitle(specificChat)));
        this.headerCell = headerCell;
      }
    } else if (controllerMode == MODE_SETUP && !inBiometricsSetup) {
      if (XgramExpressive.isEnabled()) {
        return null; // XGram M3E: "Set passcode" in the small app bar, the modes in a connected button group under it
      }
      if (headerCell == null) {
        headerCell = this.context.navigation().getHeaderView().genToggleTitle(context(), this);
      }
    } else {
      return null;
    }
    updateHeaderText();
    return headerCell;
  }

  @Override
  public void onToggle (int section) {
    setMode(section + 1);
  }

  private boolean needBiometrics;

  private void setNeedBiometrics (boolean need) {
    if (this.needBiometrics != need) {
      this.needBiometrics = need;
      checkBiometricsNeeded();
    }
  }

  private boolean isBiometricAuthenticationActive;

  private boolean needStrongBiometrics () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (controllerMode == MODE_SETUP) {
        return passcodeView.getState() == Passcode.STATE_CONFIRM ?
          confirmBiometricsStrong :
          BiometricAuthentication.isStrongAvailable(true);
      } else if (specificChat != null) {
        return chatPasscode != null && chatPasscode.requireStrongBiometrics();
      } else {
        return Passcode.instance().useStrongBiometrics();
      }
    }
    return false;
  }

  private void checkBiometricsNeeded () {
    boolean needBiometricsAuthentication = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !this.biometricsScanCompleted && this.needBiometrics && context.getActivityState() == UI.State.RESUMED/* && isFocused()*/;
    if (isBiometricAuthenticationActive != needBiometricsAuthentication) {
      if (needBiometricsAuthentication) {
        boolean isStrong = needStrongBiometrics();
        String title = Passcode.getActionName(Passcode.MODE_BIOMETRICS, passcodeView.getState(), false, BitwiseUtils.optional(Passcode.BIOMETRICS_OPTION_ONLY_STRONG, isStrong));
        String cancel = Lang.getString(R.string.CancelBiometrics);
        BiometricAuthentication.authenticate(context, title, cancel, isStrong, this);
      } else {
        BiometricAuthentication.cancelAuthentication();
      }
      isBiometricAuthenticationActive = needBiometricsAuthentication;
    }
  }

  @Override
  public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
    checkBiometricsNeeded();
  }

  @Override
  public void destroy () {
    setNeedBiometrics(false);
    context.removeSimpleStateListener(this);
    Settings.instance().removePasscodeTickListener(this);
    super.destroy();
  }

  private String[] sections;

  @Override
  public String[] getToggleSections () {
    if (sections == null) {
      StringList strings;
      boolean biometricsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && BiometricAuthentication.isAvailable();
      strings = new StringList(biometricsAvailable ? 5 : 4);
      strings.append(R.string.PasscodePIN);
      strings.append(R.string.login_Password);
      strings.append(R.string.PasscodePattern);
      strings.append(R.string.PasscodeGesture);
      if (biometricsAvailable) {
        strings.append(BiometricAuthentication.ONLY_FINGERPRINT ? R.string.PasscodeFingerprint : R.string.PasscodeBiometrics);
      }
      sections = strings.get();
    }
    return sections;
  }

  // Settings

  private int getPasscodeMode () {
    if (specificChat != null) {
      return chatPasscode != null ? chatPasscode.mode : Passcode.MODE_NONE;
    } else {
      return Passcode.instance().getMode();
    }
  }

  private boolean isPasscodeEnabled () {
    if (specificChat != null) {
      return chatPasscode != null && chatPasscode.mode != Passcode.MODE_NONE;
    } else {
      return Passcode.instance().isEnabled();
    }
  }

  private boolean needUnlockWithBiometrics () {
    if (specificChat != null) {
      return chatPasscode != null && chatPasscode.mode != Passcode.MODE_BIOMETRICS && !StringUtils.isEmpty(chatPasscode.biometricsHash);
    } else {
      return Passcode.instance().needUnlockWithBiometrics();
    }
  }

  private boolean unlockByBiometrics (long biometricsId, boolean strong) {
    if (specificChat != null) {
      String hash = Passcode.getPasscodeHash(String.valueOf(biometricsId));
      if (chatPasscode.mode == Passcode.MODE_BIOMETRICS) {
        return chatPasscode.hash.equals(hash) && (!chatPasscode.requireStrongBiometrics() || strong);
      } else {
        return chatPasscode.unlockWitBiometrics(hash, strong);
      }
    } else {
      return Passcode.instance().unlockByBiometrics(biometricsId, strong);
    }
  }

  private void enableUnlockByBiometrics (long biometricsId, boolean strong) {
    if (specificChat != null) {
      chatPasscode.setBiometrics(Passcode.getPasscodeHash(String.valueOf(biometricsId)), strong);
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().enableUnlockByBiometrics(biometricsId, strong);
    }
  }

  private void setBiometrics (long biometricsId, boolean strong) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(String.valueOf(biometricsId));
      chatPasscode.mode = Passcode.MODE_BIOMETRICS;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setBiometrics(biometricsId, strong);
    }
  }

  private void setPassword (String password) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(password);
      chatPasscode.mode = Passcode.MODE_PASSWORD;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPassword(password);
    }
  }

  private boolean unlockByPattern (String pattern) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PATTERN && chatPasscode.hash.equals(Passcode.getPasscodeHash(pattern));
    } else {
      return Passcode.instance().unlockByPattern(pattern);
    }
  }

  private void setPattern (String pattern) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(pattern);
      chatPasscode.mode = Passcode.MODE_PATTERN;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPattern(pattern);
    }
  }

  private boolean unlockByPincode (String pincode) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PINCODE && chatPasscode.hash.equals(Passcode.getPasscodeHash(pincode));
    } else {
      return Passcode.instance().unlockByPincode(pincode);
    }
  }

  private void setPincode (String pincode) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(pincode);
      chatPasscode.mode = Passcode.MODE_PINCODE;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPincode(pincode);
    }
  }

  private boolean unlockByPassword (String password) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PASSWORD && chatPasscode.hash.equals(Passcode.getPasscodeHash(password));
    } else {
      return Passcode.instance().unlockByPassword(password);
    }
  }

  private GesturePassword gesturePassword;

  private GesturePassword getGesturePassword () {
    if (gesturePassword == null) {
      String suffix;
      if (specificChat != null) {
        suffix = tdlib.uniqueSuffix() + "." + specificChat.id;
      } else {
        suffix = null;
      }
      gesturePassword = new GesturePassword(suffix);
    }
    return gesturePassword;
  }

  private void setGesture () {
    if (specificChat != null) {
      chatPasscode.mode = Passcode.MODE_GESTURE;
      chatPasscode.hash = "";
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setGesture();
    }
  }

  private void unlockByGesture () {
    if (specificChat != null) {
      // Do nothing?
    } else {
      Passcode.instance().unlock();
    }
  }

  private String uniqueSuffix;

  @Override
  public @Nullable String makeBruteForceSuffix () {
    return specificChat != null ? (uniqueSuffix != null ? uniqueSuffix : (uniqueSuffix = tdlib.uniqueSuffix(specificChat.id))) : null;
  }

  @Override
  public void onPasscodeTick (String suffix) {
    if (StringUtils.equalsOrBothEmpty(suffix, makeBruteForceSuffix())) {
      if (passcodeView != null) {
        passcodeView.updateTextAndInvalidate();
      }
      xgramUpdateSupporting(); // XGram M3E: the lockout countdown, inline
    }
  }
}
