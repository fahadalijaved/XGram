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

  // M3 list item text (Compose ListTokens): label text bodyLarge in onSurface, supporting text bodyMedium in
  // onSurfaceVariant, trailing supporting text labelSmall in onSurfaceVariant. Used by every shared media row on the
  // profile (Docs, Music, Voice, Groups in common, Members) so all tabs read the same.

  /** List item headline: bodyLarge (16sp, weight 400); allBold() keeps the weight. */
  public static TextStyleProvider listHeadlineProvider () {
    return flexProvider(XgramExpressive.TS_BODY_L, false, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L));
  }

  /** List item supporting text: bodyMedium (14sp, weight 400; entities in the emphasized weight). */
  public static TextStyleProvider listSupportingProvider () {
    return flexTextProvider(XgramExpressive.TS_BODY_M, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M));
  }

  /** Trailing supporting text and badge labels: labelSmall (11sp, weight 500). */
  public static TextStyleProvider listTrailingProvider () {
    return flexProvider(XgramExpressive.TS_LABEL_S, false, XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_S));
  }

  /** onSurface, resolved at draw time. */
  public static final org.thunderdog.challegram.util.text.TextColorSet TEXT_ON_SURFACE = () -> XgramExpressive.color(XgramExpressive.ON_SURFACE);
  /** onSurfaceVariant, resolved at draw time. */
  public static final org.thunderdog.challegram.util.text.TextColorSet TEXT_ON_SURFACE_VARIANT = () -> XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
  /** primary (online status, the owner's tag), resolved at draw time. */
  public static final org.thunderdog.challegram.util.text.TextColorSet TEXT_PRIMARY = () -> XgramExpressive.color(XgramExpressive.PRIMARY);
  /** onError (the label of a large badge), resolved at draw time. */
  public static final org.thunderdog.challegram.util.text.TextColorSet TEXT_ON_ERROR = () -> XgramExpressive.color(XgramExpressive.ON_ERROR);

  private static @androidx.annotation.Nullable android.text.TextPaint trailingPaint;

  /** labelSmall for trailing supporting text drawn with Canvas.drawText (a track's duration); the color is set per call. */
  public static android.text.TextPaint trailingPaint (@ColorInt int color) {
    android.text.TextPaint paint = trailingPaint;
    if (paint == null) {
      paint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_S, false));
      paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_S)));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        paint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_S) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_S));
      }
      trailingPaint = paint;
    }
    paint.setColor(color);
    return paint;
  }

  // M3 badge (Compose BadgeTokens): large badge 16dp tall, full corners, error container, labelSmall in onError,
  // 4dp horizontal padding

  public static final float BADGE_LARGE_SIZE = 16f, BADGE_PADDING = 4f;

  private static final RectF badgeRect = new RectF();
  private static final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /** The width of a large badge around a label {@code labelWidth} wide. */
  public static int badgeWidth (int labelWidth) {
    return Math.max(Screen.dp(BADGE_LARGE_SIZE), labelWidth + Screen.dp(BADGE_PADDING) * 2);
  }

  /** Fills a large badge starting at {@code left}, centered on {@code centerY}; the caller draws the label in onError. */
  public static void drawBadge (Canvas c, float left, float centerY, int labelWidth) {
    final float half = Screen.dp(BADGE_LARGE_SIZE) / 2f;
    badgeRect.set(left, centerY - half, left + badgeWidth(labelWidth), centerY + half);
    badgePaint.setColor(XgramExpressive.color(XgramExpressive.ERROR));
    c.drawRoundRect(badgeRect, half, half, badgePaint);
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

  /**
   * A tile of a group, by the list decoration's own rule (XgramExpressive.isInGroup): a row between a group opener
   * (SHADOW_TOP, EMPTY_OFFSET) or the list start and a SHADOW_BOTTOM.
   */
  public static boolean isGroupedRow (List<ListItem> items, int position) {
    if (position < 0 || position >= items.size() || !isRowType(items.get(position).getViewType())) {
      return false;
    }
    return XgramExpressive.isInGroup(items, position);
  }

  /**
   * The top offset the segmented decoration gives the row at {@code position}, exactly: 2dp after another row, 24dp
   * under the collapsing header's offset, 8dp for a group opened by the list start (an edit page starts with its
   * rows), 0 otherwise.
   */
  public static int groupGapPx (List<ListItem> items, int position) {
    return XgramExpressive.listTopOffsetPx(items, position);
  }

  /** Width a row gets inside a segmented list: grouped rows, headers and descriptions are inset 16dp on both sides. */
  public static int rowWidth (List<ListItem> items, int position, int width) {
    final boolean inset = isGroupedRow(items, position) ||
      (position >= 0 && position < items.size() && isFrameType(items.get(position).getViewType()));
    return inset ? Math.max(0, width - Screen.dp(XgramExpressive.LIST_INSET) * 2) : width;
  }

  // Chat list and topic list rows

  private static final RectF tileRect = new RectF();
  private static final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /**
   * Selected or open chat: a SECONDARY_CONTAINER tile inset 4dp / 2dp with 16dp corners, at {@code factor} alpha. The
   * avatar starts 7dp in and a community avatar's ring 5dp in, so both stay inside the tile's straight start edge.
   * XgramExpressiveC's list tile press mask has the same shape.
   */
  public static void drawSelectionTile (Canvas c, int width, int height, float factor) {
    if (factor <= 0f) {
      return;
    }
    float insetX = Screen.dp(4f), insetY = Screen.dp(2f), radius = Screen.dp(XgramExpressive.CORNER_L);
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
