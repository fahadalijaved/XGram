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
package com.fahadali.xgram;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/** Small UI helpers for XGram settings that change how existing views draw. */
public final class XgramUi {
  private XgramUi () { }

  /** Redraws every view of the activity, e.g. after a setting changed how avatars are drawn. */
  public static void invalidateAll (@Nullable Context context) {
    if (context instanceof Activity) {
      invalidateTree(((Activity) context).getWindow().getDecorView());
    }
  }

  private static void invalidateTree (@Nullable View view) {
    if (view == null) {
      return;
    }
    view.invalidate();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        invalidateTree(group.getChildAt(i));
      }
    }
  }
}
