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
 * File created on 15/05/2015 at 19:25
 */
package org.thunderdog.challegram.component.base;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramLoadingIndicatorDrawable;
import com.fahadali.xgram.appearance.XgramR5Sheets;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SpinnerView;

import me.vkryl.android.widget.FrameLayoutFix;

public class ProgressWrap extends FrameLayoutFix {
  private TextView textView;
  private ProgressBar progressBar;

  public void addThemeListeners (ThemeListenerList themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeTextAccentColorListener(textView);
    }
  }

  public ProgressWrap (Context context) {
    super(context);

    if (XgramExpressive.isEnabled()) {
      xgramBuild(context);
      return;
    }

    int width = Math.min(Screen.smallestSide() - Screen.dp(56f), Screen.dp(300f));
    int height = Screen.dp(94f);

    ViewSupport.setThemedBackground(this, ColorId.filling);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(36f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
    params.setMargins(Screen.dp(12f), 0, 0, 0);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progressBar = new ProgressBar(getContext());
      progressBar.setIndeterminate(true);
      progressBar.setLayoutParams(params);
      addView(progressBar);
    } else {
      SpinnerView spinnerView = new SpinnerView(getContext());
      spinnerView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.spinner_48_inner));
      spinnerView.setLayoutParams(params);

      addView(spinnerView);
    }

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
    params.setMargins(Screen.dp(60f), Screen.dp(1f), 0, 0);

    textView = new NoScrollTextView(context);
    textView.setTextColor(Theme.textAccentColor());
    textView.setGravity(Gravity.LEFT);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setMaxWidth(width - Screen.dp(64f));
    textView.setMaxLines(2);
    textView.setLayoutParams(params);

    addView(textView);
    setLayoutParams(FrameLayoutFix.newParams(width, height, Gravity.CENTER));
  }

  public void setMessage (String message) {
    textView.setText(message);
  }

  public @Nullable ProgressBar getProgress () {
    return progressBar;
  }

  // XGram M3E r5: the progress popup as an M3 dialog. A 28dp surfaceContainerHigh card (the card step with Pure black)
  // with 24dp padding: the 48dp loading indicator in primary, the message in bodyLarge onSurface beside it, and a
  // "Cancel" text button at the end while the caller can cancel (xgramSetCancel).

  private @Nullable TextView xgramCancel;

  private static final class IndicatorView extends ImageView {
    private final XgramLoadingIndicatorDrawable indicator;

    IndicatorView (Context context) {
      super(context);
      indicator = new XgramLoadingIndicatorDrawable(48f, XgramExpressive.PRIMARY);
      setImageDrawable(indicator);
      setScaleType(ScaleType.CENTER);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      indicator.start();
    }

    @Override
    protected void onDetachedFromWindow () {
      indicator.stop();
      super.onDetachedFromWindow();
    }
  }

  private void xgramBuild (Context context) {
    final int padding = Screen.dp(24f);
    final int width = Math.min(Screen.smallestSide() - Screen.dp(48f), Screen.dp(400f));

    XgramR5Sheets.applyCard(this, XgramExpressive.SURFACE_CONTAINER_HIGH, XgramExpressive.CORNER_XL);

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(padding, padding, padding, padding);
    column.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    IndicatorView indicator = new IndicatorView(context);
    row.addView(indicator, new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));

    textView = new NoScrollTextView(context);
    XgramExpressive.applyText(textView, XgramExpressive.TS_BODY_L, false);
    textView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
    textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setMaxLines(3);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    textParams.setMarginStart(Screen.dp(16f));
    row.addView(textView, textParams);
    column.addView(row);

    xgramCancel = XgramR5Sheets.textButton(context, Lang.getString(R.string.Cancel), XgramExpressive.PRIMARY, null);
    xgramCancel.setVisibility(View.GONE);
    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f));
    cancelParams.gravity = Gravity.END;
    cancelParams.topMargin = Screen.dp(16f);
    // the label's own 12dp padding stays inside the 24dp card padding: the pill ends at the card's content edge
    cancelParams.setMarginEnd(-Screen.dp(12f));
    column.addView(xgramCancel, cancelParams);

    addView(column);
    setLayoutParams(FrameLayoutFix.newParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
  }

  /** XGram M3E: shows the "Cancel" text button while {@code onCancel} is set (a cancellable progress). */
  public void xgramSetCancel (@Nullable Runnable onCancel) {
    if (xgramCancel == null) {
      return;
    }
    if (onCancel != null) {
      xgramCancel.setOnClickListener(v -> onCancel.run());
      xgramCancel.setVisibility(View.VISIBLE);
    } else {
      xgramCancel.setOnClickListener(null);
      xgramCancel.setVisibility(View.GONE);
    }
  }
}
