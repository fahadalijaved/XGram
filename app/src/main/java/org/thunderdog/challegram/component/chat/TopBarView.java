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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class TopBarView extends FrameLayoutFix {
  private final ImageView topDismissButton;
  private final LinearLayout actionsList;

  private boolean canDismiss;

  public interface DismissListener {
    void onDismissRequest (TopBarView barView);
  }

  public static class Item {
    final int id;
    final int stringRes;
    final View.OnClickListener onClickListener;

    boolean isNegative;
    boolean noDismiss;

    public Item (int id, int stringRes, View.OnClickListener onClickListener) {
      this.id = id;
      this.stringRes = stringRes;
      this.onClickListener = onClickListener;
    }

    public Item setIsNegative () {
      this.isNegative = true;
      return this;
    }

    public Item setNoDismiss () {
      this.noDismiss = true;
      return this;
    }
  }

  private DismissListener dismissListener;

  public TopBarView (@NonNull Context context) {
    super(context);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? 48f : 36f))); // XGram: a 48dp card with Expressive design
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) { // XGram: a card of the Material 3 Expressive group draws the background
      ViewSupport.setThemedBackground(this, ColorId.filling, null);
    }

    actionsList = new LinearLayout(context);
    actionsList.setOrientation(LinearLayout.HORIZONTAL);
    actionsList.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    addView(actionsList);

    topDismissButton = new ImageView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return Views.isValid(this) && super.onTouchEvent(event);
      }
    };
    topDismissButton.setOnClickListener(view -> {
      if (dismissListener != null) {
        dismissListener.onDismissRequest(this);
      }
    });
    topDismissButton.setScaleType(ImageView.ScaleType.CENTER);
    topDismissButton.setColorFilter(Theme.iconColor());
    topDismissButton.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_close_18));
    topDismissButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(topDismissButton); // XGram: the 40dp state layer, following theme switches
    } else
    topDismissButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(topDismissButton);
    topDismissButton.setVisibility(View.INVISIBLE);
    addView(topDismissButton);
  }

  public void setDismissListener (DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(topDismissButton, ColorId.icon);
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  public void setCanDismiss (boolean canDismiss) {
    if (this.canDismiss != canDismiss) {
      this.canDismiss = canDismiss;
      topDismissButton.setVisibility(canDismiss ? View.VISIBLE : View.GONE);
    }
  }

  public void setItems (Item... items) {
    for (int i = 0; i < actionsList.getChildCount(); i++) {
      View view = actionsList.getChildAt(i);
      if (view != null && themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(view);
      }
    }
    actionsList.removeAllViews();
    if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    boolean canDismiss = false;
    // XGram: Material 3 Expressive text buttons are 40dp tall, centred in the 48dp card, so their full-corner state
    // layer stays clear of the card's outline (a full-height pill was cut flat by it)
    final ViewGroup.LayoutParams barParams = getLayoutParams();
    final int xgramButtonInset = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && barParams != null && barParams.height > 0 ?
      Math.max(0, (barParams.height - Screen.dp(40f)) / 2) : 0;
    for (Item item : items) {
      if (!item.noDismiss) {
        canDismiss = true;
      }
      int textColorId = item.isNegative ? ColorId.textNegative : ColorId.textNeutral;
      TextView button = Views.newTextView(getContext(), 15f, Theme.getColor(textColorId), Gravity.CENTER, Views.TEXT_FLAG_BOLD | Views.TEXT_FLAG_HORIZONTAL_PADDING);
      button.setId(item.id);
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(button, textColorId);
      }
      button.setEllipsize(TextUtils.TruncateAt.END);
      button.setSingleLine(true);
      button.setOnClickListener(item.onClickListener);
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram: Material 3 Expressive text button: labelLarge in sentence case, full-corner state layer
        com.fahadali.xgram.appearance.XgramM3Chat.setThemedTextButtonBackground(button, textColorId); // its state layer follows theme switches
        com.fahadali.xgram.appearance.XgramExpressive.applyText(button, com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L, false);
        button.setText(Lang.getString(item.stringRes));
      } else {
      button.setBackgroundResource(R.drawable.bg_btn_header);
      Views.setMediumText(button, Lang.uppercase(Lang.getString(item.stringRes)));
      }
      Views.setClickable(button);
      LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2f);
      buttonParams.topMargin = buttonParams.bottomMargin = xgramButtonInset;
      button.setLayoutParams(buttonParams);
      actionsList.addView(button);
    }
    if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    setCanDismiss(canDismiss);
  }
}
