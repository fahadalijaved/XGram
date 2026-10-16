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
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class DoubleTextViewWithIcon extends FrameLayoutFix {
  private final ImageView iconView;
  private final DoubleTextView textView;
  // XGram: Material 3 Expressive, the row is a segmented list tile holding XgramM3JoinRequestTile (40dp avatar,
  // bodyLarge name, two-line supporting text, standard and filled tonal icon buttons). The Telegram X views stay
  // detached (SettingHolder still configures them). Nothing with Expressive design off.
  private final @Nullable com.fahadali.xgram.appearance.XgramM3JoinRequestTile xgramTile;

  public DoubleTextViewWithIcon (Context context) {
    super(context);
    int iconWidth = Screen.dp(56);
    iconView = new ImageView(context);
    textView = new DoubleTextView(context);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      xgramTile = new com.fahadali.xgram.appearance.XgramM3JoinRequestTile(context);
      xgramTile.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      addView(xgramTile);
      com.fahadali.xgram.appearance.XgramM3JoinRequestTile.applyTileBackground(this);
      Views.setClickable(this);
      return;
    }
    xgramTile = null;
    textView.ignoreStartOffset(true);
    textView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, Screen.dp(72), 0, iconWidth, 0, 0, 0));
    iconView.setLayoutParams(FrameLayoutFix.newParams(iconWidth, Screen.dp(72)));
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setColorFilter(Theme.getColor(ColorId.icon));
    addView(iconView);
    addView(textView);
    setBackgroundColor(Theme.fillingColor());
  }

  public ImageView icon () {
    return iconView;
  }

  public DoubleTextView text () {
    return textView;
  }

  /** XGram: the Material 3 Expressive tile (with Expressive design), or null. */
  public @Nullable com.fahadali.xgram.appearance.XgramM3JoinRequestTile xgramTile () {
    return xgramTile;
  }

  public void attach () {
    if (xgramTile != null) {
      xgramTile.attach();
      return;
    }
    textView.attach();
  }

  public void detach () {
    if (xgramTile != null) {
      xgramTile.detach();
      return;
    }
    textView.detach();
  }

  public void setIconClickListener (OnClickListener listener) {
    if (xgramTile != null) {
      return; // XGram: the tile's own buttons (XgramM3JoinRequestTile.setActions)
    }
    Views.setClickable(iconView);
    ViewUtils.setBackground(iconView, Theme.transparentSelector());
    iconView.setOnClickListener(listener);
  }

  public void setTextClickListener (OnClickListener listener) {
    if (xgramTile != null) {
      setOnClickListener(listener); // XGram: a press anywhere on the tile but its buttons
      return;
    }
    Views.setClickable(textView);
    ViewUtils.setBackground(textView, Theme.transparentSelector());
    textView.setOnClickListener(listener);
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      if (xgramTile != null) {
        // XGram: the tile and its contents read their roles while drawing
        themeProvider.addThemeInvalidateListener(this);
        themeProvider.addThemeInvalidateListener(xgramTile);
        return;
      }
      textView.addThemeListeners(themeProvider);
      themeProvider.addThemeBackgroundColorListener(this, ColorId.filling);
      themeProvider.addThemeFilterListener(iconView, ColorId.icon);
    }
  }

  public void checkRtl () {
    if (xgramTile != null) {
      xgramTile.checkRtl();
      return;
    }
    textView.checkRtl();
  }
}
