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

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.List;

/**
 * Material 3 Expressive pieces for the chat list, the drawer, the profile page and the topic list (stage 2, part 3).
 * Everything here is only used while {@link XgramExpressive#isEnabled()}.
 */
public final class XgramM3Profile {
  private XgramM3Profile () { }

  // Typography

  private static final SparseArray<TextStyleProvider> providers = new SparseArray<>();

  /**
   * A title style provider in Google Sans Flex (or the app font) at the type scale style {@code ts}: regular and bold
   * paints both use the style's weight ({@code emphasized} picks the emphasized one), so allBold() titles keep it.
   */
  public static TextStyleProvider flexProvider (int ts, boolean emphasized, float sizeDp) {
    return provider(ts, emphasized, true, sizeDp);
  }

  /** A text style provider for texts with entities: the style's weight, its emphasized weight for bold. */
  public static TextStyleProvider flexTextProvider (int ts, float sizeDp) {
    return provider(ts, false, false, sizeDp);
  }

  private static TextStyleProvider provider (int ts, boolean emphasized, boolean sameBold, float sizeDp) {
    int key = ((ts * 2 + (emphasized ? 1 : 0)) * 2 + (sameBold ? 1 : 0)) * 10000 + Math.round(sizeDp * 10f);
    TextStyleProvider provider = providers.get(key);
    if (provider == null) {
      Typeface regular = XgramExpressive.typeface(ts, emphasized);
      Typeface bold = sameBold ? regular : XgramExpressive.typeface(ts, true);
      Fonts.TextPaintStorage storage = new Fonts.TextPaintStorage(regular, bold, Fonts.getRobotoItalic(), null, Fonts.getRobotoMono(), bold, 0);
      provider = new TextStyleProvider(storage).setTextSize(sizeDp);
      providers.put(key, provider);
    }
    return provider;
  }

  // Segmented list grouping (the same rules as the list decoration, with EMPTY_OFFSET opening a group)

  private static boolean isFrameType (int type) {
    switch (type) {
      case ListItem.TYPE_SHADOW_TOP:
      case ListItem.TYPE_SHADOW_BOTTOM:
      case ListItem.TYPE_SEPARATOR:
      case ListItem.TYPE_SEPARATOR_FULL:
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_PADDED:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_WITH_ACTION:
      case ListItem.TYPE_HEADER_WITH_TEXT_BUTTON:
      case ListItem.TYPE_HEADER_WITH_CHECKBOX:
      case ListItem.TYPE_DESCRIPTION:
      case ListItem.TYPE_DESCRIPTION_CENTERED:
        return true;
    }
    return false;
  }

  private static boolean isRowType (int type) {
    if (isFrameType(type)) {
      return false;
    }
    switch (type) {
      case ListItem.TYPE_EMPTY_OFFSET:
      case ListItem.TYPE_EMPTY_OFFSET_SMALL:
      case ListItem.TYPE_EMPTY_OFFSET_NO_HEAD:
      case ListItem.TYPE_PADDING:
      case ListItem.TYPE_FAKE_PAGER_TOPVIEW:
        return false;
    }
    return true;
  }

  private static boolean isGroupOpener (int type) {
    return type == ListItem.TYPE_SHADOW_TOP || type == ListItem.TYPE_EMPTY_OFFSET || type == ListItem.TYPE_EMPTY_OFFSET_SMALL;
  }

  /** A tile of a group: a row between a group opener (SHADOW_TOP, EMPTY_OFFSET) and a SHADOW_BOTTOM. */
  public static boolean isGroupedRow (List<ListItem> items, int position) {
    if (position < 0 || position >= items.size() || !isRowType(items.get(position).getViewType())) {
      return false;
    }
    boolean top = false;
    for (int i = position - 1; i >= 0; i--) {
      int type = items.get(i).getViewType();
      if (isGroupOpener(type)) {
        top = true;
        break;
      }
      if (type == ListItem.TYPE_SHADOW_BOTTOM) {
        return false;
      }
    }
    if (!top) {
      return false;
    }
    for (int i = position + 1; i < items.size(); i++) {
      int type = items.get(i).getViewType();
      if (type == ListItem.TYPE_SHADOW_BOTTOM) {
        return true;
      }
      if (type == ListItem.TYPE_SHADOW_TOP) {
        return false;
      }
    }
    return false;
  }

  /** The 2dp gap the segmented decoration adds above a grouped row that directly follows another row. */
  public static int groupGapPx (List<ListItem> items, int position) {
    if (position > 0 && isGroupedRow(items, position) && isRowType(items.get(position - 1).getViewType())) {
      return Screen.dp(XgramExpressive.LIST_GAP);
    }
    return 0;
  }

  /** Width a row gets inside a segmented list: grouped rows are inset 16dp on both sides. */
  public static int rowWidth (List<ListItem> items, int position, int width) {
    return isGroupedRow(items, position) ? Math.max(0, width - Screen.dp(XgramExpressive.LIST_INSET) * 2) : width;
  }

  // Chat list and topic list rows

  private static final RectF tileRect = new RectF();
  private static final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /** Selected or open chat: a SECONDARY_CONTAINER tile inset 8dp / 2dp with 16dp corners, at {@code factor} alpha. */
  public static void drawSelectionTile (Canvas c, int width, int height, float factor) {
    if (factor <= 0f) {
      return;
    }
    float insetX = Screen.dp(8f), insetY = Screen.dp(2f), radius = Screen.dp(XgramExpressive.CORNER_L);
    tileRect.set(insetX, insetY, width - insetX, height - insetY);
    tilePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), Math.min(1f, factor)));
    c.drawRoundRect(tileRect, radius, radius, tilePaint);
  }

  // Drawer

  /** Drawer item: an ON_SURFACE state layer in a full-corner pill inset 12dp horizontally. */
  public static Drawable drawerItemBackground (@ColorInt int containerColor) {
    XgramExpressive.MorphDrawable pill = new XgramExpressive.MorphDrawable(containerColor, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_FULL);
    int inset = Screen.dp(12f);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return new InsetDrawable(pill, inset, 0, inset, 0);
    }
    int ripple = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.STATE_PRESSED);
    RippleDrawable drawable = new RippleDrawable(ColorStateList.valueOf(ripple), pill, pill.newMask());
    return new InsetDrawable(drawable, inset, 0, inset, 0);
  }

  /** Rounds the end corners of a drawer panel by {@code radiusDp} (the start edge sits off screen). */
  public static void roundEndCorners (View view, float radiusDp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    final int radius = Screen.dp(radiusDp);
    view.setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline (View v, Outline outline) {
        int w = v.getWidth(), h = v.getHeight();
        if (Lang.rtl()) {
          outline.setRoundRect(0, 0, w + radius, h, radius);
        } else {
          outline.setRoundRect(-radius, 0, w, h, radius);
        }
      }
    });
    view.setClipToOutline(true);
  }

  // Profile

  /** Clips the top corners of {@code view} to {@code radiusDp} (the bottom edge stays square). */
  public static void roundTopCorners (View view, float radiusDp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    final int radius = Screen.dp(radiusDp);
    view.setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline (View v, Outline outline) {
        outline.setRoundRect(0, 0, v.getWidth(), v.getHeight() + radius, radius);
      }
    });
    view.setClipToOutline(true);
  }

  /**
   * Shared media lists hosted by a profile: the profile's own segmented decoration (tiles, insets and painted gaps,
   * but no vertical offsets: the profile's scroll sync measures items by type) in place of the settings one, and no
   * large-title decoration. The pages draw no fill: the docked sheet behind the pager does.
   */
  public static void prepareProfileList (@NonNull RecyclerView recyclerView) {
    XgramExpressive.detachListDecoration(recyclerView);
    for (int i = recyclerView.getItemDecorationCount() - 1; i >= 0; i--) {
      RecyclerView.ItemDecoration decoration = recyclerView.getItemDecorationAt(i);
      String name = decoration.getClass().getName();
      if (name.startsWith("com.fahadali.xgram.appearance.XgramM3Bars")) {
        recyclerView.removeItemDecorationAt(i);
      }
    }
    if (recyclerView.getTag(org.thunderdog.challegram.R.id.xgram_m3e_p3_surface) == null) {
      XgramM3SharedMedia.ProfileListDecoration decoration = new XgramM3SharedMedia.ProfileListDecoration();
      recyclerView.addItemDecoration(decoration, 0);
      recyclerView.setTag(org.thunderdog.challegram.R.id.xgram_m3e_p3_surface, decoration);
    }
  }
}
