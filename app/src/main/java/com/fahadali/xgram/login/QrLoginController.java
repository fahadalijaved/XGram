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

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramButtonGroupView;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramR5Lists;
import com.fahadali.xgram.appearance.XgramR5People;
import com.fahadali.xgram.appearance.XgramR5Security;
import com.fahadali.xgram.appearance.XgramR5Sheets;
import com.fahadali.xgram.appearance.XgramR6B8Login;
import com.fahadali.xgram.appearance.XgramShapes;
import com.fahadali.xgram.appearance.XgramSnackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.AuthorizationListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.IntroController;
import org.thunderdog.challegram.ui.PhoneController;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.StringUtils;

/**
 * Shows the QR code of AuthorizationStateWaitOtherDeviceConfirmation for another logged-in device to scan.
 *
 * MainActivity opens it once and keeps it while TDLib sends new links, so it redraws the code itself. After a scan
 * MainActivity opens the next screen (2FA password) or the chats. Leaving the screen while it still waits for a scan
 * sends LogOut on this screen's own tdlib, the only way out of that state; TDLib then starts again at the phone number.
 */
public final class QrLoginController extends ViewController<TdApi.AuthorizationStateWaitOtherDeviceConfirmation> implements AuthorizationListener, View.OnClickListener {
  private static final int DARK = 0xff000000, LIGHT = 0xffffffff;

  private ImageView qrView;
  private TextView statusView;
  private int qrSize;

  private @Nullable String shownLink;
  private int encodeGeneration;
  private boolean cancelled, oneShot;

  public QrLoginController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramQrLogin;
  }

  @Override
  public CharSequence getName () {
    if (XgramExpressive.isEnabled()) {
      return Lang.getString(R.string.xgram_login_QrButton); // XGram M3E r6: "Log in with QR code", the large title
    }
    return Lang.getString(R.string.xgram_login_QrTitle);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public boolean isUnauthorized () {
    return true;
  }

  @Override
  protected View onCreateView (Context context) {
    if (XgramExpressive.isEnabled()) {
      return xgramCreateView(context);
    }
    ScrollView scrollView = new ScrollView(context);
    scrollView.setFillViewport(true);
    ViewSupport.setThemedBackground(scrollView, ColorId.filling, this);
    scrollView.setKeepScreenOn(true); // the code has to stay visible while the other phone scans it

    LinearLayout contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    contentView.setGravity(Gravity.CENTER_HORIZONTAL);
    contentView.setPadding(Screen.dp(24f), Screen.dp(24f), Screen.dp(24f), Screen.dp(16f));
    scrollView.addView(contentView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // Dark on white in every theme, or scanners struggle.
    qrSize = Math.max(Screen.dp(160f), Math.min(Screen.dp(240f), Screen.smallestActualSide() - Screen.dp(112f)));
    FrameLayout qrCard = new FrameLayout(context);
    GradientDrawable cardBackground = new GradientDrawable();
    cardBackground.setColor(LIGHT);
    cardBackground.setCornerRadius(Screen.dp(12f));
    qrCard.setBackground(cardBackground);
    int cardPadding = Screen.dp(12f);
    qrCard.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
    qrView = new ImageView(context);
    qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    qrView.setContentDescription(Lang.getString(R.string.xgram_login_QrTitle));
    qrCard.addView(qrView, new FrameLayout.LayoutParams(qrSize, qrSize));
    contentView.addView(qrCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView stepsView = newText(context, 16f, ColorId.text);
    stepsView.setText(Lang.getString(R.string.xgram_login_QrSteps));
    stepsView.setLineSpacing(Screen.dp(4f), 1f);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(24f);
    contentView.addView(stepsView, params);

    statusView = newText(context, 14f, ColorId.textLight);
    statusView.setText(Lang.getString(R.string.xgram_login_QrRefresh));
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(12f);
    contentView.addView(statusView, params);

    // One phone can't scan its own screen: hand the link to Telegram on this phone, or save the code for another screen.
    TextView openAppView = newButton(context, R.id.btn_xgramQrOpenApp, R.string.xgram_login_QrOpenApp);
    openAppView.setTypeface(openAppView.getTypeface(), android.graphics.Typeface.BOLD);
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(16f);
    contentView.addView(openAppView, params);

    TextView openAppHintView = newText(context, 13f, ColorId.textLight);
    openAppHintView.setGravity(Gravity.CENTER);
    openAppHintView.setText(Lang.getString(R.string.xgram_login_QrOpenAppHint));
    contentView.addView(openAppHintView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    LinearLayout saveRow = new LinearLayout(context);
    saveRow.setOrientation(LinearLayout.HORIZONTAL);
    saveRow.setGravity(Gravity.CENTER_HORIZONTAL);
    saveRow.addView(newButton(context, R.id.btn_xgramQrSave, R.string.xgram_login_QrSave), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    saveRow.addView(newButton(context, R.id.btn_xgramQrShare, R.string.xgram_login_QrShare), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(8f);
    contentView.addView(saveRow, params);

    TextView saveHintView = newText(context, 13f, ColorId.textLight);
    saveHintView.setGravity(Gravity.CENTER);
    saveHintView.setText(Lang.getString(R.string.xgram_login_QrSaveHint));
    contentView.addView(saveHintView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView phoneView = newText(context, 15f, ColorId.textNeutral);
    phoneView.setId(R.id.btn_xgramQrCancel);
    phoneView.setGravity(Gravity.CENTER);
    phoneView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(14f));
    phoneView.setText(Lang.getString(R.string.xgram_login_QrInstead));
    phoneView.setOnClickListener(this);
    Views.setClickable(phoneView);
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(16f);
    contentView.addView(phoneView, params);

    tdlib.listeners().addAuthorizationChangeListener(this);
    TdApi.AuthorizationStateWaitOtherDeviceConfirmation state = getArguments();
    showLink(state != null ? state.link : null);
    // The link may have changed between MainActivity creating this screen and the listener being added.
    TdApi.AuthorizationState current = tdlib.authorizationState();
    if (current instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
      showLink(((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) current).link);
    }
    return scrollView;
  }

  private TextView newText (Context context, float sizeSp, int colorId) {
    TextView view = new NoScrollTextView(context);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeSp);
    view.setTextColor(Theme.getColor(colorId));
    addThemeTextColorListener(view, colorId);
    return view;
  }

  private TextView newButton (Context context, int id, int textRes) {
    TextView view = newText(context, 15f, ColorId.textNeutral);
    view.setId(id);
    view.setGravity(Gravity.CENTER);
    view.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
    view.setText(Lang.getString(textRes));
    view.setOnClickListener(this);
    Views.setClickable(view);
    return view;
  }

  // XGram M3E r6: the login column (XgramR6B8Login) with the large title; the code stays dark on its own white CORNER_XL
  // square (scanner contrast in every theme) inside a 28dp surfaceContainerHighest card, an M3E loading indicator while
  // there is no link, the card morphing into a circle with a check once scanned; the steps as three segmented tiles with
  // numbered primaryContainer badges; a filled "Open in Telegram", a connected Save | Share group and a text button
  // back to the phone number; snackbars instead of toasts; the connection pill under the app bar.

  private static final int XGRAM_LOADING = 0, XGRAM_CODE = 1, XGRAM_SCANNED = 2, XGRAM_ENDED = 3;

  private @Nullable XgramExpressive.MorphDrawable xgramCard;
  private @Nullable View xgramSquare, xgramIndicator;
  private @Nullable XgramR5Security.HeroView xgramCheck;
  private @Nullable XgramExpressive.Spring xgramCodeSpring;
  private @Nullable XgramR6B8Login.ConnectionWatch xgramConnection;
  private int xgramState = XGRAM_LOADING;

  private View xgramCreateView (Context context) {
    FrameLayout root = new FrameLayout(context);
    ViewSupport.setThemedBackground(root, ColorId.filling, this);
    root.setKeepScreenOn(true); // the code has to stay visible while the other phone scans it

    LinearLayout column = XgramR6B8Login.newColumn(context);
    ScrollView scroll = XgramR6B8Login.wrapInScroll(context, column);
    root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    final boolean rtl = Lang.rtl();

    TextView headline = XgramR6B8Login.newHeadline(context);
    headline.setText(getName());
    addThemeInvalidateListener(headline);
    column.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    statusView = XgramR6B8Login.newSupporting(context);
    statusView.setText(Lang.getString(R.string.xgram_login_QrRefresh));
    addThemeInvalidateListener(statusView);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(8f);
    column.addView(statusView, params);

    // The card, the white square with the code, the loading indicator and the check over it
    qrSize = Math.max(Screen.dp(160f), Math.min(Screen.dp(240f), Screen.smallestActualSide() - Screen.dp(144f)));
    FrameLayout card = new FrameLayout(context);
    xgramCard = XgramR5Sheets.applyCard(card, XgramExpressive.SURFACE_CONTAINER_HIGHEST, XgramExpressive.CORNER_XL);
    xgramCard.setHost(card);
    addThemeInvalidateListener(card);
    final int cardPadding = Screen.dp(16f);
    card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
    FrameLayout square = new FrameLayout(context);
    GradientDrawable white = new GradientDrawable();
    white.setColor(LIGHT);
    white.setCornerRadius(Screen.dp(XgramExpressive.CORNER_XL));
    square.setBackground(white);
    final int quietZone = Screen.dp(16f);
    square.setPadding(quietZone, quietZone, quietZone, quietZone);
    qrView = new ImageView(context);
    qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    qrView.setContentDescription(Lang.getString(R.string.xgram_login_QrTitle));
    square.addView(qrView, new FrameLayout.LayoutParams(qrSize, qrSize));
    square.setAlpha(0f);
    card.addView(square, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    xgramSquare = square;
    XgramR5Sheets.IndicatorView indicator = new XgramR5Sheets.IndicatorView(context, 48f, XgramExpressive.PRIMARY);
    card.addView(indicator, new FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER));
    xgramIndicator = indicator;
    xgramCheck = new XgramR5Security.HeroView(context, XgramShapes.COOKIE_9, R.drawable.baseline_check_24, XgramR6B8Login.HERO_ICON);
    xgramCheck.setAlpha(0f);
    addThemeInvalidateListener(xgramCheck);
    card.addView(xgramCheck, new FrameLayout.LayoutParams(Screen.dp(XgramR6B8Login.HERO_SIZE), Screen.dp(XgramR6B8Login.HERO_SIZE), Gravity.CENTER));
    xgramCodeSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> {
      final float f = Math.max(0f, Math.min(1f, value));
      square.setAlpha(f);
      indicator.setAlpha(xgramState == XGRAM_LOADING ? 1f - f : 0f);
    });
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.gravity = Gravity.CENTER_HORIZONTAL;
    params.topMargin = Screen.dp(24f);
    column.addView(card, params);

    // The steps: three segmented tiles, a numbered badge each
    final int[] steps = {R.string.XgramR6B8QrStep1, R.string.XgramR6B8QrStep2, R.string.XgramR6B8QrStep3};
    for (int i = 0; i < steps.length; i++) {
      LinearLayout tile = new LinearLayout(context);
      tile.setOrientation(LinearLayout.HORIZONTAL);
      tile.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // the children are added in visual order below
      tile.setGravity(Gravity.CENTER_VERTICAL);
      tile.setMinimumHeight(Screen.dp(56f));
      tile.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
      XgramExpressive.MorphDrawable tileBackground = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, Float.NaN);
      tileBackground.setColorRole(XgramExpressive.SURFACE_BRIGHT);
      tileBackground.setSegment(i == 0 ? XgramExpressive.SEGMENT_FIRST : i == steps.length - 1 ? XgramExpressive.SEGMENT_LAST : XgramExpressive.SEGMENT_MIDDLE);
      tile.setBackground(tileBackground);
      addThemeInvalidateListener(tile);

      XgramR5Lists.RoleText badge = new XgramR5Lists.RoleText(context, XgramExpressive.TS_LABEL_L, XgramExpressive.ON_PRIMARY_CONTAINER);
      badge.setText(String.valueOf(i + 1));
      badge.setGravity(Gravity.CENTER);
      badge.setFontFeatureSettings("tnum");
      XgramExpressive.MorphDrawable badgeBackground = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, Float.NaN);
      badgeBackground.setColorRole(XgramExpressive.PRIMARY_CONTAINER);
      badge.setBackground(badgeBackground);
      addThemeInvalidateListener(badge);
      LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(32f));

      XgramR5Lists.RoleText text = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_L, XgramExpressive.ON_SURFACE);
      text.setText(Lang.getString(steps[i]));
      text.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
      addThemeInvalidateListener(text);
      LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      if (rtl) {
        textParams.rightMargin = Screen.dp(16f);
        tile.addView(text, textParams);
        tile.addView(badge, badgeParams);
      } else {
        textParams.leftMargin = Screen.dp(16f);
        tile.addView(badge, badgeParams);
        tile.addView(text, textParams);
      }
      LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      tileParams.topMargin = Screen.dp(i == 0 ? 24f : XgramExpressive.LIST_GAP);
      column.addView(tile, tileParams);
    }

    // This phone can't scan its own screen: hand the link to Telegram here, or save the code for another screen
    FrameLayout openApp = XgramR5People.newHeroButton(context, Lang.getString(R.string.xgram_login_QrOpenApp), XgramExpressive.PRIMARY, XgramExpressive.ON_PRIMARY, v -> openInTelegram());
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(12f);
    column.addView(openApp, params);
    column.addView(xgramCaption(context, R.string.xgram_login_QrOpenAppHint), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    XgramButtonGroupView saveGroup = new XgramButtonGroupView(context);
    List<XgramButtonGroupView.Action> actions = new ArrayList<>(2);
    actions.add(new XgramButtonGroupView.Action(R.id.btn_xgramQrSave, XgramIconPack.map(R.drawable.baseline_file_download_24), Lang.getString(R.string.xgram_login_QrSave), false));
    actions.add(new XgramButtonGroupView.Action(R.id.btn_xgramQrShare, XgramIconPack.map(R.drawable.baseline_share_24), Lang.getString(R.string.xgram_login_QrShare), false));
    saveGroup.setActions(actions);
    saveGroup.setCallback((view, actionId) -> {
      if (actionId == R.id.btn_xgramQrSave) {
        saveQr(false);
      } else if (actionId == R.id.btn_xgramQrShare) {
        saveQr(true);
      }
    });
    addThemeInvalidateListener(saveGroup);
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(XgramButtonGroupView.HEIGHT_DP));
    params.topMargin = Screen.dp(8f);
    params.leftMargin = params.rightMargin = -Screen.dp(16f); // the group has its own 16dp sides
    column.addView(saveGroup, params);
    column.addView(xgramCaption(context, R.string.xgram_login_QrSaveHint), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView usePhone = XgramR5Sheets.textButton(context, Lang.getString(R.string.XgramR6B8QrUsePhone), XgramExpressive.PRIMARY, null);
    usePhone.setId(R.id.btn_xgramQrCancel);
    usePhone.setOnClickListener(this);
    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f));
    params.gravity = Gravity.CENTER_HORIZONTAL;
    params.topMargin = Screen.dp(16f);
    column.addView(usePhone, params);

    XgramR6B8Login.linkTitle(this, scroll, headline);
    xgramConnection = XgramR6B8Login.ConnectionWatch.attach(this, root, scroll);

    tdlib.listeners().addAuthorizationChangeListener(this);
    TdApi.AuthorizationStateWaitOtherDeviceConfirmation state = getArguments();
    showLink(state != null ? state.link : null);
    // The link may have changed between MainActivity creating this screen and the listener being added.
    TdApi.AuthorizationState current = tdlib.authorizationState();
    if (current instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
      showLink(((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) current).link);
    }
    return root;
  }

  /** A centred bodySmall caption in onSurfaceVariant, 4dp under the control it explains. */
  private TextView xgramCaption (Context context, int textRes) {
    XgramR5Lists.RoleText caption = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_S, XgramExpressive.ON_SURFACE_VARIANT);
    caption.setGravity(Gravity.CENTER);
    caption.setText(Lang.getString(textRes));
    caption.setPadding(Screen.dp(8f), Screen.dp(4f), Screen.dp(8f), 0);
    addThemeInvalidateListener(caption);
    return caption;
  }

  /** The card's content: the indicator while loading, the code, the check once scanned, nothing once the login ended. */
  private void xgramSetState (int state) {
    if (xgramCard == null || xgramCodeSpring == null || xgramCheck == null || xgramIndicator == null || xgramSquare == null) {
      return;
    }
    final int previous = xgramState;
    xgramState = state;
    final boolean animated = xgramCard.getBounds().width() > 0;
    if (animated) {
      xgramCodeSpring.animateTo(state == XGRAM_CODE ? 1f : 0f);
    } else {
      xgramCodeSpring.setValue(state == XGRAM_CODE ? 1f : 0f);
      xgramSquare.setAlpha(state == XGRAM_CODE ? 1f : 0f);
    }
    xgramIndicator.setAlpha(state == XGRAM_LOADING ? 1f - Math.max(0f, Math.min(1f, xgramCodeSpring.getValue())) : 0f);
    xgramCard.setCornerTarget(state == XGRAM_SCANNED ? XgramExpressive.CORNER_FULL : Float.NaN, animated);
    if (state == XGRAM_SCANNED && previous != XGRAM_SCANNED) {
      XgramR6B8Login.popIn(xgramCheck);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        xgramCheck.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
      }
    } else if (state != XGRAM_SCANNED) {
      xgramCheck.setAlpha(0f);
    }
  }

  /** XGram M3E r6: a snackbar with Expressive design, a toast without it or when no page can show one. Any thread. */
  private static void xgramNotify (CharSequence text) {
    UI.post(() -> {
      if (!XgramSnackbar.show(text, Toast.LENGTH_SHORT)) {
        UI.showToast(text, Toast.LENGTH_SHORT);
      }
    });
  }

  private static void xgramNotify (int textRes) {
    xgramNotify(Lang.getString(textRes));
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      oneShot = true;
      // Login screens of this account below this one are stale now: leaving the QR screen goes back to the phone number.
      for (int i = stackSize() - 2; i >= 0; i--) {
        ViewController<?> c = stackItemAt(i);
        if (c != null && c != this && c.isSameTdlib(tdlib) && c.isUnauthorized() && !(c instanceof PhoneController) && !(c instanceof IntroController)) {
          destroyStackItemAt(i);
        }
      }
    }
  }

  // State

  @Override
  public void onAuthorizationStateChanged (TdApi.AuthorizationState state) {
    runOnUiThreadOptional(() -> {
      if (state.getConstructor() == TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR) {
        showLink(((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) state).link);
      } else if (!cancelled && QrLogin.canRequest(state) && state.getConstructor() != TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
        // Scanned: MainActivity opens the next step on top of this screen. Don't leave a dead code behind it.
        encodeGeneration++;
        shownLink = null;
        qrView.setImageBitmap(null);
        statusView.setText(Lang.getString(R.string.xgram_login_QrScanned));
        xgramSetState(XGRAM_SCANNED); // XGram M3E r6
      } else if (!cancelled && state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
        // TDLib ended the QR login by itself. MainActivity keeps this screen for that state, so don't leave a dead code here.
        cancelled = true;
        encodeGeneration++;
        shownLink = null;
        qrView.setImageBitmap(null);
        statusView.setText(Lang.getString(R.string.xgram_login_QrEnded));
        xgramSetState(XGRAM_ENDED); // XGram M3E r6
        if (isFocused()) {
          xgramNotify(R.string.xgram_login_QrEnded);
          navigateBack();
        }
      }
    });
  }

  private void showLink (@Nullable String link) {
    if (StringUtils.isEmpty(link) || link.equals(shownLink) || qrView == null) {
      return;
    }
    shownLink = link;
    statusView.setText(Lang.getString(R.string.xgram_login_QrRefresh));
    if (xgramState != XGRAM_CODE) {
      xgramSetState(XGRAM_LOADING); // XGram M3E r6: the indicator until the code is drawn
    }
    final int generation = ++encodeGeneration;
    final int size = qrSize;
    Background.instance().post(() -> {
      Bitmap bitmap = encode(link, size, 1);
      runOnUiThreadOptional(() -> {
        if (generation == encodeGeneration && bitmap != null) {
          qrView.setImageBitmap(bitmap);
          xgramSetState(XGRAM_CODE); // XGram M3E r6
        }
      });
    });
  }

  @Nullable
  private static Bitmap encode (String text, int size, int margin) {
    try {
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.MARGIN, margin);
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
      int width = matrix.getWidth(), height = matrix.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        int offset = y * width;
        for (int x = 0; x < width; x++) {
          pixels[offset + x] = matrix.get(x, y) ? DARK : LIGHT;
        }
      }
      return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      Log.e("XGram: cannot draw the QR login code", t);
      return null;
    }
  }

  // This phone: every action reads shownLink when it runs, so it always uses the newest link.

  private static final String[] TELEGRAM_PACKAGES = {"org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta"};
  private static final int SAVED_SIZE = 1024;
  private static final String SAVED_FOLDER = "Pictures/XGram";

  @Nullable
  private String currentLink () {
    String link = cancelled ? null : shownLink;
    if (StringUtils.isEmpty(link)) {
      xgramNotify(R.string.xgram_login_QrNotReady);
      return null;
    }
    return link;
  }

  private static boolean isXgram (Context context, String packageName) {
    return packageName.equals(context.getPackageName()) || packageName.startsWith("com.fahadali.xgram");
  }

  private static List<ComponentName> handlers (Context context, Intent intent, boolean xgram) {
    List<ComponentName> result = new ArrayList<>();
    try {
      List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
      for (ResolveInfo info : list) {
        if (info.activityInfo != null && isXgram(context, info.activityInfo.packageName) == xgram) {
          result.add(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
        }
      }
    } catch (Throwable t) {
      Log.e("XGram: cannot list the apps for an intent", t);
    }
    return result;
  }

  private void openInTelegram () {
    String link = currentLink();
    if (link == null) {
      return;
    }
    Context context = context();
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
    // The official app first. XGram registers tg:// links too and must never get its own login link.
    for (String packageName : TELEGRAM_PACKAGES) {
      try {
        context.startActivity(new Intent(intent).setPackage(packageName));
        return;
      } catch (ActivityNotFoundException ignored) {
        // Not installed.
      } catch (Throwable t) {
        Log.e("XGram: cannot open the QR login link in %s", t, packageName);
      }
    }
    List<ComponentName> others = handlers(context, intent, false);
    if (others.isEmpty()) {
      xgramNotify(R.string.xgram_login_QrNoApp);
      return;
    }
    Intent target;
    if (others.size() == 1) {
      target = new Intent(intent).setComponent(others.get(0));
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      target = Intent.createChooser(intent, Lang.getString(R.string.xgram_login_QrOpenApp));
      target.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, handlers(context, intent, true).toArray(new ComponentName[0]));
    } else {
      target = Intent.createChooser(new Intent(intent).setComponent(others.get(0)), Lang.getString(R.string.xgram_login_QrOpenApp));
      Intent[] rest = new Intent[others.size() - 1];
      for (int i = 1; i < others.size(); i++) {
        rest[i - 1] = new Intent(intent).setComponent(others.get(i));
      }
      target.putExtra(Intent.EXTRA_INITIAL_INTENTS, rest);
    }
    try {
      context.startActivity(target);
    } catch (Throwable t) {
      Log.e("XGram: cannot open the QR login link", t);
      xgramNotify(R.string.xgram_login_QrNoApp);
    }
  }

  private void saveQr (boolean share) {
    String link = currentLink();
    if (link == null) {
      return;
    }
    if (!share && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && context().permissions().requestWriteExternalStorage(Permissions.WriteType.GALLERY, granted -> {
      if (granted) {
        saveQr(false);
      }
    })) {
      return;
    }
    Background.instance().post(() -> {
      Bitmap bitmap = encode(link, SAVED_SIZE, 4);
      if (bitmap == null) {
        xgramNotify(R.string.xgram_login_QrSaveFailed);
        return;
      }
      if (share) {
        Uri uri = writeShareFile(bitmap);
        bitmap.recycle();
        runOnUiThreadOptional(() -> shareQr(uri));
      } else {
        boolean saved = saveToPictures(bitmap, "XGram-login-QR-" + System.currentTimeMillis() + ".png");
        bitmap.recycle();
        if (saved) {
          xgramNotify(Lang.getString(R.string.xgram_login_QrSaved, SAVED_FOLDER));
        } else {
          xgramNotify(R.string.xgram_login_QrSaveFailed);
        }
      }
    });
  }

  private static boolean saveToPictures (Bitmap bitmap, String fileName) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContentResolver resolver = UI.getContext().getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
      values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, SAVED_FOLDER);
      values.put(MediaStore.MediaColumns.IS_PENDING, 1);
      Uri uri;
      try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      } catch (Throwable t) {
        Log.e("XGram: cannot add the QR code to Pictures", t);
        return false;
      }
      if (uri == null) {
        return false;
      }
      boolean written = false;
      try (OutputStream stream = resolver.openOutputStream(uri)) {
        written = stream != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
      } catch (Throwable t) {
        Log.e("XGram: cannot write the QR code", t);
      }
      try {
        if (!written) {
          resolver.delete(uri, null, null);
          return false;
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
      } catch (Throwable t) {
        Log.e("XGram: cannot finish the saved QR code", t);
      }
      return written;
    }
    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "XGram");
    if (!dir.exists() && !dir.mkdirs()) {
      return false;
    }
    File file = new File(dir, fileName);
    try (FileOutputStream stream = new FileOutputStream(file)) {
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
        return false;
      }
    } catch (Throwable t) {
      Log.e("XGram: cannot write the QR code", t);
      return false;
    }
    U.addToGallery(file);
    return true;
  }

  @Nullable
  private static Uri writeShareFile (Bitmap bitmap) {
    File dir = new File(UI.getContext().getFilesDir(), "xgram_qr");
    if (!dir.exists() && !dir.mkdirs()) {
      return null;
    }
    File file = new File(dir, "XGram-login-QR.png");
    try (FileOutputStream stream = new FileOutputStream(file)) {
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
        return null;
      }
    } catch (Throwable t) {
      Log.e("XGram: cannot write the QR code", t);
      return null;
    }
    return U.contentUriFromFile(file);
  }

  private void shareQr (@Nullable Uri uri) {
    if (uri == null) {
      xgramNotify(R.string.xgram_login_QrSaveFailed);
      return;
    }
    Context context = context();
    Intent intent = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri);
    intent.setClipData(ClipData.newRawUri(null, uri));
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    Intent chooser = Intent.createChooser(intent, Lang.getString(R.string.xgram_login_QrShare));
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, handlers(context, intent, true).toArray(new ComponentName[0]));
    }
    try {
      context.startActivity(chooser);
    } catch (Throwable t) {
      Log.e("XGram: cannot share the QR code", t);
      xgramNotify(R.string.XgramExportNoApp);
    }
  }

  // Leaving

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_xgramQrCancel) {
      cancel();
      navigateBack();
    } else if (id == R.id.btn_xgramQrOpenApp) {
      openInTelegram();
    } else if (id == R.id.btn_xgramQrSave) {
      saveQr(false);
    } else if (id == R.id.btn_xgramQrShare) {
      saveQr(true);
    }
  }

  private void cancel () {
    if (!cancelled) {
      cancelled = true;
      QrLogin.cancelPending(tdlib);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().removeAuthorizationChangeListener(this);
    if (xgramConnection != null) { // XGram M3E r6
      xgramConnection.destroy();
      xgramConnection = null;
    }
    encodeGeneration++;
    // Back button, swipe back or the button above: if it still waits for a scan, end it, or this slot stays stuck.
    cancel();
  }
}
