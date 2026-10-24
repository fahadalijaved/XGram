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
package com.fahadali.xgram.editor;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * While one of the photo editor's overlays is open over the viewer (the text editor, the sticker picker), TalkBack and
 * explore by touch reach only it: the viewer's other views under it (the photo, the header, the Send and edit buttons)
 * are hidden from accessibility, and get their own importance back once it closes (unless something else changed it
 * meanwhile).
 */
final class XgramOverlayUnderlay {
  private final ArrayList<View> views = new ArrayList<>();
  private final ArrayList<Integer> importance = new ArrayList<>();

  /** Hides the other children of {@code overlay}'s parent (the overlay is in it already). */
  void hide (@NonNull View overlay) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || !views.isEmpty()) {
      return;
    }
    final ViewParent parent = overlay.getParent();
    if (!(parent instanceof ViewGroup)) {
      return;
    }
    final ViewGroup group = (ViewGroup) parent;
    for (int i = 0; i < group.getChildCount(); i++) {
      final View view = group.getChildAt(i);
      final int value = view.getImportantForAccessibility();
      if (view != overlay && value != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
        views.add(view);
        importance.add(value);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      }
    }
  }

  /** Gives the hidden views back their importance. */
  void restore () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      return;
    }
    for (int i = 0; i < views.size(); i++) {
      final View view = views.get(i);
      if (view.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
        view.setImportantForAccessibility(importance.get(i));
      }
    }
    views.clear();
    importance.clear();
  }
}
