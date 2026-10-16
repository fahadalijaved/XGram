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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;

import java.util.List;

/**
 * Material 3 Expressive media grid (the shared Media, GIFs and Video messages tabs). The grid is inset 16dp like the
 * segmented list tiles, so no tile meets the screen edge; tiles are squares with 2dp gaps and 4dp corners; each
 * month's group takes the segmented list's 20dp outer corners on its convex corners only (a partial last row keeps
 * its step). The outer corners are painted over in the color behind the grid (the docked sheet on the profile, the
 * page background elsewhere): no clipping, no layers, nothing allocated while drawing.
 * Only used while {@link XgramExpressive#isEnabled()}; Telegram X keeps GridSpacingItemDecoration.
 */
public final class XgramW2ProfileGrid extends RecyclerView.ItemDecoration {
  /** Grid side inset, gap, tile corner and group corner (dp): LIST_INSET, LIST_GAP, CORNER_XS, LIST_OUTER_CORNER. */
  public static final float INSET = XgramExpressive.LIST_INSET, GAP = XgramExpressive.LIST_GAP,
    TILE_CORNER = XgramExpressive.CORNER_XS, GROUP_CORNER = XgramExpressive.LIST_OUTER_CORNER;

  private static final int TOP_START = 1, TOP_END = 1 << 1, BOTTOM_END = 1 << 2, BOTTOM_START = 1 << 3;

  private final int viewType;
  private final boolean onSheet;

  private int[] borders = new int[4];
  private int bordersSpace = -1, bordersCount = -1;

  private final Path cornerPath = new Path();
  private float cornerPathRadius = -1f;
  private final RectF arcRect = new RectF();
  private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /**
   * @param viewType the grid tiles' view type (ListItem.TYPE_SMALL_MEDIA)
   * @param onSheet  the grid sits on the profile's docked media sheet (surfaceContainerLow); otherwise on the page
   *                 background
   */
  public XgramW2ProfileGrid (int viewType, boolean onSheet) {
    this.viewType = viewType;
    this.onSheet = onSheet;
  }

  // Metrics, shared with the controller's scroll position math

  /** Side of a square tile in a grid {@code width} pixels wide: 16dp each side, 2dp between tiles. */
  public static int tileSize (int width, int spanCount) {
    if (spanCount <= 0) {
      return 0;
    }
    int avail = width - Screen.dp(INSET) * 2 - Screen.dp(GAP) * (spanCount - 1);
    return Math.max(0, avail / spanCount);
  }

  /** Height of one grid row: the tile, plus the 2dp gap above it unless it is the first row of its group. */
  public static int rowHeight (int width, int spanCount, boolean firstRowOfGroup) {
    return tileSize(width, spanCount) + (firstRowOfGroup ? 0 : Screen.dp(GAP));
  }

  // Layout

  /** GridLayoutManager's span borders, computed the same way (calculateItemBorders), so tiles land exactly. */
  private int[] borders (int spanCount, int space) {
    if (bordersCount != spanCount || bordersSpace != space) {
      if (borders.length != spanCount + 1) {
        borders = new int[spanCount + 1];
      }
      borders[0] = 0;
      final int sizePerSpan = space / spanCount;
      final int remainder = space % spanCount;
      int consumed = 0, additional = 0;
      for (int i = 1; i <= spanCount; i++) {
        int itemSize = sizePerSpan;
        additional += remainder;
        if (additional > 0 && (spanCount - additional) < remainder) {
          itemSize += 1;
          additional -= spanCount;
        }
        consumed += itemSize;
        borders[i] = consumed;
      }
      bordersCount = spanCount;
      bordersSpace = space;
    }
    return borders;
  }

  @Nullable
  private static List<ListItem> itemsOf (RecyclerView parent) {
    RecyclerView.Adapter<?> adapter = parent.getAdapter();
    return adapter instanceof SettingsAdapter ? ((SettingsAdapter) adapter).getItems() : null;
  }

  private static int positionOf (RecyclerView parent, View view) {
    int position = parent.getChildAdapterPosition(view);
    if (position == RecyclerView.NO_POSITION) {
      position = parent.getChildLayoutPosition(view);
    }
    return position;
  }

  private boolean isTile (List<ListItem> items, int position) {
    return position >= 0 && position < items.size() && items.get(position).getViewType() == viewType;
  }

  /** The tile's row is not the first of its group (the item before the row is a tile too). */
  private boolean hasRowAbove (List<ListItem> items, int position, int span) {
    return isTile(items, position - span - 1);
  }

  /** A tile right under this one in the same group (every item up to it is a tile). */
  private boolean hasTileBelow (List<ListItem> items, int position, int spanCount) {
    for (int i = position + 1; i <= position + spanCount; i++) {
      if (!isTile(items, i)) {
        return false;
      }
    }
    return true;
  }

  /** The group's convex corners this tile carries, in start / end terms. */
  private int outerCorners (List<ListItem> items, int position, int span, int spanCount) {
    final boolean above = hasRowAbove(items, position, span);
    final boolean below = hasTileBelow(items, position, spanCount);
    final boolean start = span > 0;
    final boolean end = span < spanCount - 1 && isTile(items, position + 1);
    int flags = 0;
    if (!above && !start) {
      flags |= TOP_START;
    }
    if (!above && !end) {
      flags |= TOP_END;
    }
    if (!below && !end) {
      flags |= BOTTOM_END;
    }
    if (!below && !start) {
      flags |= BOTTOM_START;
    }
    return flags;
  }

  @Override
  public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    outRect.set(0, 0, 0, 0);
    final RecyclerView.LayoutManager manager = parent.getLayoutManager();
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (!(manager instanceof GridLayoutManager) || !(params instanceof GridLayoutManager.LayoutParams)) {
      return;
    }
    final RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
    final GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) params;
    final int spanCount = ((GridLayoutManager) manager).getSpanCount();
    final int span = lp.getSpanIndex();
    if (holder == null || holder.getItemViewType() != viewType || lp.getSpanSize() != 1 || span < 0 || span >= spanCount) {
      return;
    }
    final int space = manager.getWidth() - manager.getPaddingLeft() - manager.getPaddingRight();
    if (space <= 0) {
      return;
    }
    // RtlGridLayoutManager lays span 0 out at the right edge in RTL
    final int column = Lang.rtl() ? spanCount - 1 - span : span;
    final int[] b = borders(spanCount, space);
    final int tile = tileSize(space, spanCount);
    final int gap = Screen.dp(GAP);
    final int total = tile * spanCount + gap * (spanCount - 1);
    final int tileLeft = (space - total) / 2 + column * (tile + gap);
    outRect.left = tileLeft - b[column];
    outRect.right = b[column + 1] - (tileLeft + tile);
    final List<ListItem> items = itemsOf(parent);
    final int position = positionOf(parent, view);
    if (items != null && isTile(items, position) && hasRowAbove(items, position, span)) {
      outRect.top = gap;
    }
  }

  // Group corners

  @ColorInt
  private int maskColor () {
    return onSheet ? XgramM3SharedMedia.sheetColor() : Theme.backgroundColor();
  }

  /** The area outside a quarter circle of radius {@code r} in the top left corner of (0, 0). */
  private void ensureCornerPath (float r) {
    if (cornerPathRadius == r) {
      return;
    }
    cornerPathRadius = r;
    cornerPath.rewind();
    cornerPath.moveTo(0f, 0f);
    cornerPath.lineTo(r, 0f);
    arcRect.set(0f, 0f, r * 2f, r * 2f);
    cornerPath.arcTo(arcRect, 270f, -90f, false);
    cornerPath.close();
  }

  private void drawCorner (Canvas c, float x, float y, float scaleX, float scaleY) {
    final int save = c.save();
    c.translate(x, y);
    c.scale(scaleX, scaleY);
    c.drawPath(cornerPath, maskPaint);
    c.restoreToCount(save);
  }

  @Override
  public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    final List<ListItem> items = itemsOf(parent);
    final RecyclerView.LayoutManager manager = parent.getLayoutManager();
    if (items == null || !(manager instanceof GridLayoutManager)) {
      return;
    }
    final int spanCount = ((GridLayoutManager) manager).getSpanCount();
    final float groupRadius = Screen.dp(GROUP_CORNER);
    final boolean rtl = Lang.rtl();
    boolean colorSet = false;
    final int childCount = parent.getChildCount();
    for (int i = 0; i < childCount; i++) {
      final View child = parent.getChildAt(i);
      final ViewGroup.LayoutParams params = child.getLayoutParams();
      if (!(params instanceof GridLayoutManager.LayoutParams)) {
        continue;
      }
      final GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) params;
      final int span = lp.getSpanIndex();
      if (lp.getSpanSize() != 1 || span < 0 || span >= spanCount) {
        continue;
      }
      final int position = positionOf(parent, child);
      if (!isTile(items, position)) {
        continue;
      }
      final int flags = outerCorners(items, position, span, spanCount);
      if (flags == 0 || child.getWidth() <= 0 || child.getHeight() <= 0) {
        continue;
      }
      if (!colorSet) {
        maskPaint.setColor(maskColor());
        colorSet = true;
      }
      ensureCornerPath(Math.min(groupRadius, Math.min(child.getWidth(), child.getHeight()) / 2f));
      final float left = child.getLeft() + child.getTranslationX();
      final float top = child.getTop() + child.getTranslationY();
      final float right = left + child.getWidth();
      final float bottom = top + child.getHeight();
      final boolean topLeft = (flags & (rtl ? TOP_END : TOP_START)) != 0;
      final boolean topRight = (flags & (rtl ? TOP_START : TOP_END)) != 0;
      final boolean bottomRight = (flags & (rtl ? BOTTOM_START : BOTTOM_END)) != 0;
      final boolean bottomLeft = (flags & (rtl ? BOTTOM_END : BOTTOM_START)) != 0;
      if (topLeft) {
        drawCorner(c, left, top, 1f, 1f);
      }
      if (topRight) {
        drawCorner(c, right, top, -1f, 1f);
      }
      if (bottomRight) {
        drawCorner(c, right, bottom, -1f, -1f);
      }
      if (bottomLeft) {
        drawCorner(c, left, bottom, 1f, -1f);
      }
    }
  }
}
