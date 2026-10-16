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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class ColorPickerWrap extends FrameLayoutFix implements View.OnClickListener {
  private MediaFilterNameView nameView;

  private LinearLayout colorsWrap;
  private int activeIndex = -1;

  public ColorPickerWrap (Context context) {
    super(context);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(86f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.bottomMargin = Screen.dp(2.5f);

    this.nameView = new MediaFilterNameView(context);
    this.nameView.setLayoutParams(params);
    addView(nameView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.leftMargin = Screen.dp(64f) + Screen.dp(22f) + Screen.dp(18f) - Screen.dp(12f);
    params.rightMargin = Screen.dp(22f) - Screen.dp(12f);

    colorsWrap = new LinearLayout(context);
    colorsWrap.setOrientation(LinearLayout.HORIZONTAL);

    final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (xgram) {
      nameView.xgramStyleOnMedia(); // XGram
    }
    for (int ignored : FiltersState.SHADOWS_TINT_COLOR_IDS) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      // XGram: M3E 32dp swatches (the selected one morphs to 12dp corners with a ring) instead of check circles
      View circle = xgram ? new com.fahadali.xgram.appearance.XgramR5Media.Swatch(context) : new CheckCircle(context);
      circle.setLayoutParams(lp);
      circle.setOnClickListener(this);
      colorsWrap.addView(circle);
    }

    colorsWrap.setLayoutParams(params);
    addView(colorsWrap);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f)));
  }

  public interface Listener {
    void onColorChanged (ColorPickerWrap wrap, @ColorId int newColorId);
    boolean allowColorChanges ();
  }

  private Listener listener;

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  private int[] colorIds;

  public void setData (String name, @ColorId int[] colorIds, int selectedColor) {
    nameView.setName(name);
    this.colorIds = colorIds;

    int i = 0;
    int foundActiveIndex = -1;
    for (int colorId : colorIds) {
      View circle = colorsWrap.getChildAt(i);
      boolean isChecked = colorId == selectedColor;
      if (circle instanceof com.fahadali.xgram.appearance.XgramR5Media.Swatch) {
        ((com.fahadali.xgram.appearance.XgramR5Media.Swatch) circle).setColorId(colorId == 0 ? ColorId.white : colorId);
        ((com.fahadali.xgram.appearance.XgramR5Media.Swatch) circle).setChecked(isChecked, false);
      } else {
      ((CheckCircle) circle).setColorId(colorId == 0 ? ColorId.white : colorId);
      ((CheckCircle) circle).setChecked(isChecked, false);
      }
      if (isChecked) {
        foundActiveIndex = i;
      }
      i++;
    }
    activeIndex = foundActiveIndex;
  }

  /** XGram: checks a Telegram X check circle or an M3E swatch. */
  private static void xgramSetChecked (View v, boolean checked) {
    if (v instanceof com.fahadali.xgram.appearance.XgramR5Media.Swatch) {
      ((com.fahadali.xgram.appearance.XgramR5Media.Swatch) v).setChecked(checked, true);
      if (checked) {
        me.vkryl.android.ViewUtils.onClick(v); // a tick on the toggle
      }
    } else if (v instanceof CheckCircle) {
      ((CheckCircle) v).setChecked(checked, true);
    }
  }

  @Override
  public void onClick (View v) {
    if (listener != null && !listener.allowColorChanges()) {
      return;
    }
    int i = colorsWrap.indexOfChild(v);
    if (i != -1 && i != activeIndex) {
      if (activeIndex != -1) {
        xgramSetChecked(colorsWrap.getChildAt(activeIndex), false);
      }
      activeIndex = i;
      xgramSetChecked(v, true);
      if (listener != null) {
        listener.onColorChanged(this, colorIds[i]);
      }
    }
  }
}
