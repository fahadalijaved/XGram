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
 * File created on 08/02/2016 at 08:02
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.widget.EmojiEditText;

import me.vkryl.core.ColorUtils;

public class HeaderEditText extends EmojiEditText implements ActionMode.Callback, RtlCheckListener {
  public HeaderEditText (Context context) {
    super(context);
    init();
  }

  public HeaderEditText (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public HeaderEditText (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  @Override
  public boolean onCreateActionMode (ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
    return false;
  }

  @Override
  public void onDestroyActionMode (ActionMode mode) {

  }

  @Override
  public void checkRtl () {
    Views.setTextGravity(this, Lang.gravity() | Gravity.CENTER_VERTICAL);
  }

  private void init () {
    setTypeface(Fonts.getRobotoRegular());
    setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    setHighlightColor(Theme.fillingTextSelectionColor());
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      setCustomSelectionActionModeCallback(this);
    }
    setFilters(new InputFilter[] {
      new CharacterStyleFilter()
    });
  }

  public static HeaderEditText create (@NonNull ViewGroup parent, boolean light, @Nullable ViewController<?> themeProvider) {
    HeaderEditText editText = (HeaderEditText) Views.inflate(parent.getContext(), light ? R.layout.input_header_light : R.layout.input_header, parent);
    editText.setTextColor(Theme.getColor(ColorId.headerText));
    editText.setHintTextColor(ColorUtils.alphaColor(Theme.HEADER_TEXT_DECENT_ALPHA, Theme.getColor(ColorId.headerText)));
    editText.checkRtl();
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(editText, ColorId.headerText);
      themeProvider.addThemeHintTextColorListener(editText, ColorId.headerText).setAlpha(Theme.HEADER_TEXT_DECENT_ALPHA);
    }
    return editText;
  }

  public static HeaderEditText createStyled (@NonNull ViewGroup parent, boolean light) {
    HeaderEditText view = (HeaderEditText) Views.inflate(parent.getContext(), light ? R.layout.input_header_light : R.layout.input_header, parent);
    view.setImeOptions(EditorInfo.IME_ACTION_DONE);
    Views.setCursorDrawable(view, R.drawable.cursor_white);
    return view;
  }

  public static HeaderEditText createGreyStyled (@NonNull ViewGroup parent) {
    HeaderEditText view = (HeaderEditText) Views.inflate(parent.getContext(), R.layout.input_header_grey, parent);
    view.setImeOptions(EditorInfo.IME_ACTION_DONE);
    Views.setCursorDrawable(view, R.drawable.cursor_grey);
    return view;
  }

  // XGram: Material 3 Expressive search bar. A full-corner pill, 48dp tall inside the 56dp header
  // (a deliberate deviation from the 56dp M3 search bar), bodyLarge text with 16dp side padding.
  // On a surface: surfaceContainerHigh pill, onSurface text, onSurfaceVariant hint, primary cursor.
  // On a colored header: a translucent pill of the header text color; the text keeps its colors.

  public static void xgramApplySearchBar (@NonNull HeaderEditText view, boolean surface, @Nullable ViewController<?> themeProvider) {
    XgramExpressive.applyText(view, XgramExpressive.TS_BODY_L, false);
    if (surface) {
      view.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      view.setHintTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(XgramExpressive.color(XgramExpressive.PRIMARY));
        cursor.setSize(Screen.dp(2f), Screen.dp(20f));
        view.setTextCursorDrawable(cursor);
      }
    }
    view.setBackground(new XgramSearchPill(surface));
    final int padding = Screen.dp(16f);
    view.setPadding(padding, 0, padding, 0);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  private static final class XgramSearchPill extends Drawable {
    private final boolean surface;
    private final RectF rect = new RectF();

    XgramSearchPill (boolean surface) {
      this.surface = surface;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      float height = Math.min(Screen.dp(48f), bounds.height());
      float top = bounds.exactCenterY() - height / 2f;
      rect.set(bounds.left, top, bounds.right, top + height);
      int color = surface ? XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH) : ColorUtils.alphaColor(.12f, Theme.getColor(ColorId.headerText));
      c.drawRoundRect(rect, height / 2f, height / 2f, Paints.fillingPaint(color));
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
