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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;

/**
 * Material 3 Expressive look of the chat list's search results (TelegramViewController#generateChatSearchView) where
 * the shared list decoration (XgramExpressive#attachListDecoration) does not reach by itself, so every section
 * (People, Recent, Chats and contacts, Messages, Global search) is the same segmented list:
 * <ul>
 *   <li>People, the horizontal row of top chats: Telegram X paints it with a plain filling color, so it stayed a
 *   square card over the tile the decoration draws under it. It becomes a list row tile as Recent's rows are (the same
 *   16dp inset, corners and surfaceBright, see XgramExpressive#bindListRow), its avatars clipped to the corners.</li>
 *   <li>Recent's clear action: Telegram X's clear-all glyph (which reads as a sort icon) becomes a Clear text button
 *   in labelLarge and primary at the header's end, as in Telegram.</li>
 * </ul>
 * Rows are styled when they are bound and laid out (an item decoration's offsets, which RecyclerView asks again after
 * every bind), the button's color is synced while drawn; nothing is allocated or laid out per frame.
 */
public final class XgramM3ChatSearch {
  private XgramM3ChatSearch () { }

  /** Once per search list, with Expressive design. */
  public static void attach (@NonNull ViewController<?> owner, @Nullable RecyclerView searchView) {
    if (searchView == null || !XgramExpressive.isEnabled()) {
      return;
    }
    for (int i = 0; i < searchView.getItemDecorationCount(); i++) {
      if (searchView.getItemDecorationAt(i) instanceof Styler) {
        return;
      }
    }
    searchView.addItemDecoration(new Styler(owner));
  }

  private static final class Styler extends RecyclerView.ItemDecoration {
    private final ViewController<?> owner;

    Styler (ViewController<?> owner) {
      this.owner = owner;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.setEmpty(); // no offsets of its own: the list decoration places the rows
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      Object tag = view.getTag();
      if (!(tag instanceof ListItem)) {
        return;
      }
      ListItem item = (ListItem) tag;
      switch (item.getViewType()) {
        case ListItem.TYPE_RECYCLER_HORIZONTAL: {
          if (view instanceof RecyclerView) {
            styleTopChats(owner, (RecyclerView) view);
          }
          break;
        }
        case ListItem.TYPE_HEADER_WITH_ACTION: {
          if (view instanceof FrameLayout && item.getId() == R.id.search_section_local) {
            styleClearHeader((FrameLayout) view);
          }
          break;
        }
      }
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      // The Clear button follows the theme (a day/night switch included), as the list decoration does for headers
      final int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (child instanceof FrameLayout) {
          ClearButton button = findClearButton((FrameLayout) child);
          if (button != null) {
            button.syncColors();
          }
        }
      }
    }
  }

  // People

  /**
   * The top chats' row as a list tile: the filling background (and Telegram X's theme listener that puts the plain
   * color back) gives way to the list row tile, whose outline clips the scrolling avatars to its corners.
   */
  private static void styleTopChats (ViewController<?> owner, RecyclerView row) {
    if (row.getTag(R.id.xgram_m3e_row) != null) {
      return; // a tile already
    }
    owner.removeThemeListenerByTarget(row);
    ViewSupport.setThemedBackground(row, ColorId.filling, owner);
    XgramExpressive.bindListRow(row);
    if (row.getTag(R.id.xgram_m3e_row) == null) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      row.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
      row.setClipToOutline(true);
    }
    // The first and last avatars as far in from the tile's edges as a Recent row's photo
    int padding = Screen.dp(4f);
    row.setClipToPadding(false);
    row.setPadding(padding, row.getPaddingTop(), padding, row.getPaddingBottom());
  }

  // Recent

  @Nullable
  private static ClearButton findClearButton (FrameLayout header) {
    for (int i = header.getChildCount() - 1; i >= 0; i--) {
      View child = header.getChildAt(i);
      if (child instanceof ClearButton) {
        return (ClearButton) child;
      }
    }
    return null;
  }

  /**
   * Recent's header (a FrameLayout with the label and the clear glyph, SettingHolder TYPE_HEADER_WITH_ACTION): the
   * glyph goes, a Clear text button takes its place and passes its click on (the adapter keeps binding the glyph).
   */
  private static void styleClearHeader (FrameLayout header) {
    if (header.getChildCount() < 2 || !(header.getChildAt(1) instanceof ImageView)) {
      return;
    }
    final ImageView glyph = (ImageView) header.getChildAt(1);
    if (glyph.getVisibility() != View.GONE) {
      glyph.setVisibility(View.GONE);
    }
    ClearButton button = findClearButton(header);
    if (button == null) {
      button = new ClearButton(header.getContext());
      button.setOnClickListener(v -> glyph.performClick());
      header.addView(button, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    button.updateLayout();
  }

  /** A Material 3 text button: labelLarge in primary, 12dp paddings, a primary state layer in a full-corner shape. */
  private static final class ClearButton extends TextView {
    private final RippleDrawable ripple;
    private int rippleColor;

    ClearButton (Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setText(Lang.getString(R.string.Clear));
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setGravity(Gravity.CENTER);
      setMinWidth(Screen.dp(48f));
      GradientDrawable mask = new GradientDrawable();
      mask.setColor(0xffffffff);
      mask.setCornerRadius(Screen.dp(100f));
      rippleColor = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.STATE_PRESSED);
      ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
      setBackground(ripple);
      setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      setTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
    }

    /** At the header's end (either direction), its label 16dp in from that end, as the header's own label. */
    void updateLayout () {
      if (!(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
        return;
      }
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
      boolean rtl = Lang.rtl();
      int gravity = Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT);
      int margin = Screen.dp(4f);
      int left = rtl ? margin : 0, right = rtl ? 0 : margin;
      if (params.gravity != gravity || params.leftMargin != left || params.rightMargin != right) {
        params.gravity = gravity;
        params.leftMargin = left;
        params.rightMargin = right;
        setLayoutParams(params);
      }
    }

    void syncColors () {
      int color = XgramExpressive.color(XgramExpressive.PRIMARY);
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      int state = XgramExpressive.withAlpha(color, XgramExpressive.STATE_PRESSED);
      if (state != rippleColor) {
        rippleColor = state;
        ripple.setColor(ColorStateList.valueOf(state));
      }
    }
  }
}
