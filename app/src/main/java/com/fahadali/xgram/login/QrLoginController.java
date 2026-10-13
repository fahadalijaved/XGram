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

import android.content.Context;
import android.graphics.Bitmap;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
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
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.EnumMap;
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
      } else if (!cancelled && state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
        // TDLib ended the QR login by itself. MainActivity keeps this screen for that state, so don't leave a dead code here.
        cancelled = true;
        encodeGeneration++;
        shownLink = null;
        qrView.setImageBitmap(null);
        statusView.setText(Lang.getString(R.string.xgram_login_QrEnded));
        if (isFocused()) {
          UI.showToast(R.string.xgram_login_QrEnded, Toast.LENGTH_SHORT);
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
    final int generation = ++encodeGeneration;
    final int size = qrSize;
    Background.instance().post(() -> {
      Bitmap bitmap = encode(link, size);
      runOnUiThreadOptional(() -> {
        if (generation == encodeGeneration && bitmap != null) {
          qrView.setImageBitmap(bitmap);
        }
      });
    });
  }

  @Nullable
  private static Bitmap encode (String text, int size) {
    try {
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.MARGIN, 1);
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

  // Leaving

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_xgramQrCancel) {
      cancel();
      navigateBack();
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
    encodeGeneration++;
    // Back button, swipe back or the button above: if it still waits for a scan, end it, or this slot stays stuck.
    cancel();
  }
}
