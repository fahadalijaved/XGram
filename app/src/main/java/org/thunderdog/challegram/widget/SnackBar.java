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
 * File created on 30/06/2019
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class SnackBar extends RelativeLayout {
  public interface Callback {
    void onSnackBarTransition (SnackBar v, float factor);
    default void onDestroySnackBar (SnackBar v) { }
  }

  private TextView textView;
  private TextView actionView;

  private final BoolAnimator isShowing;

  public SnackBar (Context context) {
    super(context);

    RelativeLayout.LayoutParams rp;

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.addRule(RelativeLayout.LEFT_OF, R.id.text_title);
    rp.topMargin = rp.bottomMargin = Screen.dp(2f);

    textView = new TextView(context);
    textView.setTextColor(Theme.getColor(ColorId.snackbarUpdateText));
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    textView.setPadding(Screen.dp(12f), Screen.dp(12f), 0, Screen.dp(12f));
    textView.setLayoutParams(rp);
    addView(textView);

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.leftMargin = rp.rightMargin = rp.topMargin = rp.bottomMargin = Screen.dp(2f);
    rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    actionView = new TextView(context);
    actionView.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    actionView.setTextColor(Theme.getColor(ColorId.snackbarUpdateAction));
    actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    actionView.setLayoutParams(rp);
    Views.setClickable(actionView);
    addView(actionView);

    if (!xgram) {
      ViewSupport.setThemedBackground(this, ColorId.snackbarUpdate);
    }

    isShowing = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        updateTranslation();
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        updateTranslation();
        if (finalFactor == 0f && !isShowing.getValue() && callback != null) {
          callback.onDestroySnackBar(SnackBar.this);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    setOnTouchListener((v, e) -> true);
    if (xgram) {
      xgramStyle();
    }
  }

  // XGram M3E: floating Material 3 snackbar (inverse surface, 4dp corners, 12dp margins, sentence case action)

  private final boolean xgram = XgramExpressive.isEnabled();
  private int xgramBottomInset;

  private void xgramStyle () {
    textView.setTextColor(XgramExpressive.color(XgramExpressive.INVERSE_ON_SURFACE));
    XgramExpressive.applyText(textView, XgramExpressive.TS_BODY_M, false);
    textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(8f), Screen.dp(14f));
    textView.setGravity(Gravity.CENTER_VERTICAL);
    actionView.setId(R.id.text_title);
    actionView.setTextColor(XgramExpressive.color(XgramExpressive.INVERSE_PRIMARY));
    XgramExpressive.applyText(actionView, XgramExpressive.TS_LABEL_L, false);
    actionView.setPadding(Screen.dp(12f), Screen.dp(10f), Screen.dp(12f), Screen.dp(10f));
    RelativeLayout.LayoutParams actionParams = (RelativeLayout.LayoutParams) actionView.getLayoutParams();
    actionParams.addRule(RelativeLayout.CENTER_VERTICAL);
    actionParams.rightMargin = Screen.dp(8f);
    GradientDrawable actionMask = new GradientDrawable();
    actionMask.setColor(0xffffffff);
    actionMask.setCornerRadius(Screen.dp(100f));
    actionView.setBackground(new RippleDrawable(ColorStateList.valueOf(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.INVERSE_PRIMARY), XgramExpressive.STATE_PRESSED)), null, actionMask));

    XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_XS, Float.NaN);
    background.setColorRole(XgramExpressive.INVERSE_SURFACE);
    setBackground(background);
    setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
    setMinimumHeight(Screen.dp(48f));
    xgramUpdateMargins();
  }

  private void xgramUpdateMargins () {
    if (getLayoutParams() instanceof MarginLayoutParams) {
      MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
      int margin = Screen.dp(12f);
      int bottom = margin + xgramBottomInset;
      if (params.leftMargin != margin || params.rightMargin != margin || params.bottomMargin != bottom) {
        params.leftMargin = params.rightMargin = margin;
        params.bottomMargin = bottom;
        setLayoutParams(params);
      }
    }
  }

  @Override
  public void setPadding (int left, int top, int right, int bottom) {
    if (xgram) {
      // The bottom inset becomes a margin, so the floating container stays above the navigation bar
      if (xgramBottomInset != bottom) {
        xgramBottomInset = bottom;
        xgramUpdateMargins();
      }
      bottom = 0;
    }
    super.setPadding(left, top, right, bottom);
  }

  private Callback callback;

  public SnackBar setCallback (Callback callback) {
    this.callback = callback;
    return this;
  }

  public SnackBar setText (String text) {
    textView.setText(text);
    return this;
  }

  public SnackBar setAction (String action, Runnable callback, boolean dismissAutomatically) {
    if (xgram) {
      actionView.setText(action);
    } else {
      Views.setMediumText(actionView, Lang.uppercase(action));
    }
    actionView.setOnClickListener(v -> {
      callback.run();
      if (dismissAutomatically) {
        dismissSnackBar(true);
      }
    });
    return this;
  }

  public SnackBar showSnackBar (boolean animated) {
    isShowing.setValue(true, animated);
    return this;
  }

  public SnackBar dismissSnackBar (boolean animated) {
    isShowing.setValue(false, animated);
    return this;
  }

  public SnackBar addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(actionView, ColorId.snackbarUpdateAction);
      themeProvider.addThemeTextColorListener(textView, ColorId.snackbarUpdateText);
      themeProvider.addThemeInvalidateListener(this);
    }
    return this;
  }

  public SnackBar removeThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(textView);
      themeProvider.removeThemeListenerByTarget(actionView);
      themeProvider.removeThemeListenerByTarget(this);
    }
    return this;
  }

  private void updateTranslation () {
    float y = getMeasuredHeight() * (1f - isShowing.getFloatValue());
    if (xgram) {
      y = (getMeasuredHeight() + Screen.dp(12f) + xgramBottomInset) * (1f - isShowing.getFloatValue());
      setAlpha(MathUtils.clamp(isShowing.getFloatValue()));
    }
    if (getTranslationY() != y || y == 0) {
      if (callback != null) {
        callback.onSnackBarTransition(this, isShowing.getFloatValue());
      }
      setTranslationY(y);
    }
  }

  public float getVisibilityFactor () {
    return isShowing.getFloatValue();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateTranslation();
  }
}
