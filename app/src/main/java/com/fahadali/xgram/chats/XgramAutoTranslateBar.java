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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramIconPack;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * The bar under the chat header while the chat is auto-translated: "Translating from Russian". Tapping it offers
 * Stop translating and Translate to; the cross stops translating right away.
 */
public final class XgramAutoTranslateBar extends FrameLayoutFix {
  public static int height () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Screen.dp(48f); // XGram: room for the 40dp icon button and its 48dp touch target
    }
    return Screen.dp(36f);
  }

  private final TextView label;
  /** XGram: what fades and rises in on a spring when the bar appears (Expressive design only). */
  @Nullable private View[] xgramContent;
  @Nullable private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramEnter;

  public XgramAutoTranslateBar (Context context, @Nullable ViewController<?> themeProvider, View.OnClickListener onClick, View.OnClickListener onClose) {
    super(context);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height()));
    RippleSupport.setSimpleWhiteBackground(this, themeProvider);
    Views.setClickable(this);
    setOnClickListener(onClick);

    boolean rtl = Lang.rtl();
    int side = rtl ? Gravity.RIGHT : Gravity.LEFT, otherSide = rtl ? Gravity.LEFT : Gravity.RIGHT;

    ImageView icon = new ImageView(context);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setImageResource(XgramIconPack.map(R.drawable.baseline_translate_24));
    icon.setColorFilter(Theme.iconColor());
    int iconPadding = Screen.dp(8f);
    icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    icon.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(36f), ViewGroup.LayoutParams.MATCH_PARENT, side | Gravity.TOP, rtl ? 0 : Screen.dp(6f), 0, rtl ? Screen.dp(6f) : 0, 0));
    addView(icon);

    label = Views.newTextView(context, 15f, Theme.getColor(ColorId.text), side | Gravity.CENTER_VERTICAL, 0);
    label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, side | Gravity.TOP, rtl ? Screen.dp(40f) : Screen.dp(46f), 0, rtl ? Screen.dp(46f) : Screen.dp(40f), 0));
    addView(label);

    ImageView close = new ImageView(context);
    close.setScaleType(ImageView.ScaleType.CENTER);
    close.setImageResource(XgramIconPack.map(R.drawable.baseline_close_18));
    close.setColorFilter(Theme.iconColor());
    close.setBackgroundResource(R.drawable.bg_btn_header);
    close.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT, otherSide | Gravity.TOP));
    close.setContentDescription(Lang.getString(R.string.XgramStopTranslating));
    Views.setClickable(close);
    close.setOnClickListener(onClose);
    addView(close);

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // Material 3 Expressive: a card of the group under the app bar (the bar stack gives it the surfaceContainerHigh
      // card), a 24dp onSurfaceVariant icon 16dp from the start, the label in labelLarge onSurface 16dp after it, and
      // the cross as a 40dp standard icon button whose corners spring on press. The content fades and rises in on a
      // spring when the bar appears.
      int iconSize = Screen.dp(24f);
      icon.setPadding(0, 0, 0, 0);
      icon.setLayoutParams(FrameLayoutFix.newParams(iconSize, iconSize, side | Gravity.CENTER_VERTICAL, rtl ? 0 : Screen.dp(16f), 0, rtl ? Screen.dp(16f) : 0, 0));
      com.fahadali.xgram.appearance.XgramExpressive.applyText(label, com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L, false);
      label.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, side | Gravity.TOP, rtl ? Screen.dp(48f) : Screen.dp(56f), 0, rtl ? Screen.dp(56f) : Screen.dp(48f), 0));
      close.setImageResource(XgramIconPack.map(R.drawable.baseline_close_24));
      close.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), Screen.dp(40f), otherSide | Gravity.CENTER_VERTICAL, rtl ? Screen.dp(4f) : 0, 0, rtl ? 0 : Screen.dp(4f), 0));
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(close);
      xgramContent = new View[] {icon, label, close};
    }

    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(icon, ColorId.icon);
      themeProvider.addThemeFilterListener(close, ColorId.icon);
      themeProvider.addThemeTextColorListener(label, ColorId.text);
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  public void setText (CharSequence text) {
    if (!TextUtils.equals(label.getText(), text)) {
      label.setText(text);
    }
  }

  /** Where a tooltip about auto-translate points to. */
  public View anchor () {
    return label;
  }

  // XGram: Material 3 Expressive enter. The bar stack slides the card in; its content fades in and rises 8dp on a
  // spring (spatial default) with it. Only alpha and translation change per frame. Reduce motion: no motion.

  private boolean xgramShown;

  @Override
  protected void onVisibilityChanged (@NonNull View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (changedView == this && xgramContent != null) {
      boolean shown = visibility == View.VISIBLE;
      if (shown != xgramShown) {
        xgramShown = shown; // not again when the chat comes back on screen with the bar already shown
        if (shown && isAttachedToWindow()) {
          xgramPlayEnter();
        } else {
          xgramSettle();
        }
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    xgramSettle();
  }

  private void xgramPlayEnter () {
    if (xgramEnter == null) {
      xgramEnter = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 1f, (s, value) -> xgramApplyEnter(value));
    }
    xgramEnter.setValue(0f);
    xgramApplyEnter(0f);
    xgramEnter.animateTo(1f);
  }

  private void xgramSettle () {
    if (xgramEnter != null) {
      xgramEnter.setValue(1f);
      xgramApplyEnter(1f);
    }
  }

  private void xgramApplyEnter (float value) {
    View[] content = xgramContent;
    if (content == null) {
      return;
    }
    float alpha = Math.max(0f, Math.min(1f, value));
    float dy = (1f - value) * Screen.dp(8f);
    for (View v : content) {
      v.setAlpha(alpha);
      v.setTranslationY(dy);
    }
  }
}
