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
package com.fahadali.xgram.activity;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.SnackBar;

/**
 * Telegram X's look: an Undo snackbar (widget.SnackBar, as MainController's) at the bottom of a screen's content, one
 * at a time, gone after 5 seconds. Expressive design uses XgramSnackbar instead.
 */
final class XgramActivityUndoBar {
  private static final long SHOW_MS = 5000L;

  private final ViewController<?> owner;
  @Nullable private SnackBar bar;
  private final Runnable dismiss = () -> {
    if (bar != null) {
      bar.dismissSnackBar(true);
    }
  };

  XgramActivityUndoBar (@NonNull ViewController<?> owner) {
    this.owner = owner;
  }

  /** False when the screen has no content to show it in (the caller shows a toast). */
  @UiThread
  boolean show (@NonNull String text, @NonNull Runnable onUndo, int bottomInset) {
    View value = owner.getValue();
    if (!(value instanceof FrameLayout)) {
      return false;
    }
    final FrameLayout content = (FrameLayout) value;
    if (bar != null) {
      bar.removeThemeListeners(owner);
      content.removeView(bar);
      bar = null;
    }
    SnackBar snackBar = new SnackBar(owner.context());
    snackBar.setCallback(new SnackBar.Callback() {
      @Override
      public void onSnackBarTransition (SnackBar v, float factor) { }

      @Override
      public void onDestroySnackBar (SnackBar v) {
        v.removeThemeListeners(owner);
        content.removeView(v);
        if (bar == v) {
          bar = null;
        }
      }
    });
    snackBar.addThemeListeners(owner);
    snackBar.setText(text);
    snackBar.setAction(Lang.getString(R.string.XgramR5SheetsUndo), onUndo, true);
    snackBar.setPadding(0, 0, 0, bottomInset);
    content.addView(snackBar);
    snackBar.showSnackBar(true);
    bar = snackBar;
    UI.cancel(dismiss);
    UI.post(dismiss, SHOW_MS);
    return true;
  }

  @UiThread
  void destroy () {
    UI.cancel(dismiss);
  }
}
