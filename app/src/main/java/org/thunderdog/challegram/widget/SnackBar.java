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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class SnackBar extends RelativeLayout implements ThemeInvalidateListener, Lang.Listener {
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

    // XGram M3E: the floating bar is centred once the window is wider than it may get (an unfolded Fold, a tablet)
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, xgram ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM));
    setOnTouchListener((v, e) -> true);
    if (xgram) {
      xgramStyle();
    }
  }

  // XGram M3E: the Material 3 Expressive floating snackbar of XgramSnackbar: its container (a tonal card, a pill on one
  // line, 24dp corners when the text wraps, elevation level 3, 16dp from the sides, at most 560dp wide), its content row
  // (an optional leading icon, two lines of bodyMedium, a tonal pill action) and its motion (it rises and grows in on
  // the spatial spring, fades, shrinks and drops out on the effects spring, fades only with Reduce motion)

  private final boolean xgram = XgramExpressive.isEnabled();
  private int xgramBottomInset;
  private XgramSnackbar.Row xgramRow;
  private XgramExpressive.MorphDrawable xgramContainer;
  private XgramSnackbar.Motion xgramMotion;
  private boolean xgramLaidOut;

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    if (xgram) {
      // The M3 roles follow the theme here: Telegram X's snackbar colors are never bound in this mode
      xgramRow.applyColors();
      xgramRow.invalidate();
    }
    invalidate();
  }

  private void xgramStyle () {
    // Telegram X's text and action stay for its own look; the row (icon, text, tonal action) takes their place
    textView.setVisibility(View.GONE);
    actionView.setVisibility(View.GONE);
    xgramRow = new XgramSnackbar.Row(getContext());
    addView(xgramRow, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    xgramContainer = XgramSnackbar.styleContainer(this, XgramSnackbar.CORNER_ONE_LINE_DP);
    xgramMotion = new XgramSnackbar.Motion(this, visibility -> {
      if (callback != null) {
        callback.onSnackBarTransition(this, visibility); // the chat list lifts its FAB by it
      }
    });
    // Hidden until shown: becoming visible makes TalkBack read the live region (and nothing is drawn while it is out)
    setVisibility(View.INVISIBLE);
    setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    xgramUpdateMargins();
  }

  /** XGram M3E: a new message for a bar that shows fades its content through; one that is out just takes it. */
  private boolean xgramAnimateContent () {
    return getVisibility() == View.VISIBLE && xgramMotion.visibility() > .5f;
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (xgram && (event == Lang.EVENT_DIRECTION_CHANGED || event == Lang.EVENT_PACK_CHANGED)) {
      xgramRow.applyDirection(); // the icon and the action swap sides
    }
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (xgram) {
      Lang.addLanguageListener(this);
      xgramRow.applyDirection();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    if (xgram) {
      // XGram M3E: its motion runs on (the chat list leaves the window while a chat is open), so it never comes back
      // stuck half in or half out
      Lang.removeLanguageListener(this);
    }
    super.onDetachedFromWindow();
  }

  private void xgramUpdateMargins () {
    if (getLayoutParams() instanceof MarginLayoutParams) {
      MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
      int side = Screen.dp(XgramSnackbar.SIDE_GAP_DP);
      int bottom = Screen.dp(XgramSnackbar.BOTTOM_GAP_DP) + xgramBottomInset;
      if (params.leftMargin != side || params.rightMargin != side || params.bottomMargin != bottom) {
        params.leftMargin = params.rightMargin = side;
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
    if (xgram) { // XGram M3E
      xgramRow.setText(text, xgramAnimateContent());
      return this;
    }
    textView.setText(text);
    return this;
  }

  public SnackBar setAction (String action, Runnable callback, boolean dismissAutomatically) {
    if (xgram) {
      xgramRow.setAction(action, () -> {
        callback.run();
        if (dismissAutomatically) {
          dismissSnackBar(true);
        }
      }, xgramAnimateContent());
      return this;
    }
    Views.setMediumText(actionView, Lang.uppercase(action));
    actionView.setOnClickListener(v -> {
      callback.run();
      if (dismissAutomatically) {
        dismissSnackBar(true);
      }
    });
    return this;
  }

  /** XGram M3E: the leading icon (a 24dp drawable, through the icon pack), 0 for none. Telegram X's look has none. */
  public SnackBar setIcon (@DrawableRes int iconRes) {
    if (xgram) {
      xgramRow.setIcon(iconRes, xgramAnimateContent());
    }
    return this;
  }

  public SnackBar showSnackBar (boolean animated) {
    if (xgram) { // XGram M3E
      setVisibility(View.VISIBLE);
      xgramMotion.show(animated);
      return this;
    }
    isShowing.setValue(true, animated);
    return this;
  }

  public SnackBar dismissSnackBar (boolean animated) {
    if (xgram) {
      // As Telegram X's: onDestroySnackBar once it is out, nothing for a bar that is not in
      xgramMotion.hide(animated, () -> {
        setVisibility(View.INVISIBLE);
        if (callback != null) {
          callback.onDestroySnackBar(this);
        }
      });
      return this;
    }
    isShowing.setValue(false, animated);
    return this;
  }

  public SnackBar addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      if (!xgram) {
        themeProvider.addThemeTextColorListener(actionView, ColorId.snackbarUpdateAction);
        themeProvider.addThemeTextColorListener(textView, ColorId.snackbarUpdateText);
      }
      themeProvider.addThemeInvalidateListener(this); // onThemeInvalidate re-applies the M3 colors in xgram mode
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
    if (xgram) {
      // XgramSnackbar.Motion moves it; a new height while it is in moves what makes room for it (the FAB) again
      if (xgramMotion.visibility() > 0f) {
        xgramMotion.apply();
      }
      return;
    }
    float y = getMeasuredHeight() * (1f - isShowing.getFloatValue());
    if (getTranslationY() != y || y == 0) {
      if (callback != null) {
        callback.onSnackBarTransition(this, isShowing.getFloatValue());
      }
      setTranslationY(y);
    }
  }

  public float getVisibilityFactor () {
    return xgram ? xgramMotion.visibility() : isShowing.getFloatValue(); // XGram M3E
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (xgram) { // XGram M3E: at most 560dp wide, centred
      final int maxWidth = Screen.dp(XgramSnackbar.MAX_WIDTH_DP);
      if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
      }
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateTranslation();
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (xgram) {
      // The corners follow the text's lines: they spring when a new message wraps (or stops wrapping) while it shows
      XgramSnackbar.updateShape(xgramContainer, this, xgramRow.getLineCount(), xgramLaidOut && xgramMotion.visibility() > 0f);
      xgramLaidOut = true;
    }
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (xgram) { // XGram M3E: it grows from the middle of its bottom edge
      xgramMotion.updatePivot();
    }
  }
}
