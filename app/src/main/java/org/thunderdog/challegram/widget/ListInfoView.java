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
 * File created on 27/04/2015 at 15:36
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramListStateView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class ListInfoView extends FrameLayoutFix {
  private TextView textView;

  private ProgressBar progress;
  private SpinnerView spinnerView;

  private boolean isEmptyView;

  // XGram M3E: the list footer ("12 chats") centred in bodySmall and onSurfaceVariant (XgramM3SharedMedia.styleFooter's
  // look, applied here), the M3E loading indicator (plain, 24dp) in place of the Holo spinner, and the empty list as
  // the M3E empty state (an icon on a soft burst and a titleLarge headline). The row stays 42dp tall.
  private final boolean xgram = XgramExpressive.isEnabled();
  private @Nullable XgramListStateView xgramState;
  private int xgramTextStyle = XgramExpressive.TS_BODY_S;
  private @DrawableRes int xgramEmptyIcon = R.drawable.baseline_forum_96;

  public ListInfoView (Context context) {
    super(context);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    textView = new NoScrollTextView(context);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    textView.setTextColor(Theme.textDecent2Color());
    textView.setVisibility(View.GONE);

    if (xgram) {
      XgramExpressive.applyText(textView, xgramTextStyle, false);
      textView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      textView.setGravity(Gravity.CENTER);
      textView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
      xgramState = new XgramListStateView(context);
      xgramState.setLoading(false, 24f);
      addView(xgramState, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progress = new ProgressBar(getContext());
      progress.setIndeterminate(true);
      addView(progress, new LayoutParams(Screen.dp(32f), Screen.dp(32f), Gravity.CENTER));
    } else {
      spinnerView = new SpinnerView(context);
      spinnerView.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.spinner_48_inner));
      addView(spinnerView, new LayoutParams(Screen.dp(32f), Screen.dp(32f), Gravity.CENTER));
    }

    addView(textView, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(textView, ColorId.background_textLight);
    }
  }

  /** XGram M3E: the type scale style of the footer text (bodySmall by default; the call list's is labelMedium). */
  public void xgramSetTextStyle (int ts) {
    if (xgram && xgramTextStyle != ts) {
      xgramTextStyle = ts;
      if (!isEmptyView) {
        XgramExpressive.applyText(textView, ts, false);
      }
    }
  }

  /** XGram M3E: the icon of the empty state ({@link #showEmpty}), the chat list's by default. */
  public void xgramSetEmptyIcon (@DrawableRes int icon) {
    this.xgramEmptyIcon = icon;
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    if (xgram) {
      // Theme switches: the role is read here and set only when it changed
      final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (textView.getCurrentTextColor() != color) {
        textView.setTextColor(color);
      }
    }
    super.dispatchDraw(canvas);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCurrentHeight(), MeasureSpec.EXACTLY));
  }

  private int getCurrentHeight () {
    return isEmptyView ? Math.max(getParent() == null ? 0 : ((ViewGroup) getParent()).getMeasuredHeight(), Screen.dp(42f)) : Screen.dp(42f);
  }

  private void setProgressVisible (boolean visible) {
    if (xgramState != null) {
      if (visible) {
        xgramState.setLoading(false, 24f);
      }
      xgramState.setVisibility(visible ? View.VISIBLE : View.GONE);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progress.setVisibility(visible ? View.VISIBLE : View.GONE);
    } else {
      spinnerView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  public void showProgress () {
    isEmptyView = false;
    setProgressVisible(true);
    textView.setVisibility(View.GONE);
    int height = getMeasuredHeight();
    if (height != 0 && height != getCurrentHeight()) {
      requestLayout();
    }
  }

  public void showInfo (CharSequence message) {
    if (isEmptyView) {
      if (xgram) {
        XgramExpressive.applyText(textView, xgramTextStyle, false);
      } else {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      }
      isEmptyView = false;
    }
    setText(message);
  }

  public void showEmpty (CharSequence empty) {
    if (xgramState != null) {
      // XGram M3E: the empty state, as tall as the list
      isEmptyView = true;
      xgramState.setEmpty(xgramEmptyIcon, empty, null);
      xgramState.setVisibility(View.VISIBLE);
      textView.setText(empty);
      textView.setVisibility(View.GONE);
      int height = getMeasuredHeight();
      if (height != 0 && height != getCurrentHeight()) {
        requestLayout();
      }
      return;
    }
    if (!isEmptyView) {
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      isEmptyView = true;
    }
    setText(empty);
  }

  private void setText (CharSequence text) {
    setProgressVisible(false);
    textView.setText(text);
    textView.setVisibility(View.VISIBLE);
    int height = getMeasuredHeight();
    if (height != 0 && height != getCurrentHeight()) {
      requestLayout();
    }
  }

  public CharSequence getText () {
    return textView.getText();
  }
}
