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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.List;

/**
 * Material 3 Expressive shared media on the profile page: one docked sheet (surfaceContainerLow, 28dp top corners
 * that flatten once the tabs dock under the app bar) holding the tab strip and the pages, segmented list tiles for
 * the Docs / Links / Music / Voice / Groups / Members rows, the list item metrics and the tab switch motion.
 * Everything here is only used while {@link XgramExpressive#isEnabled()}.
 */
public final class XgramM3SharedMedia {
  private XgramM3SharedMedia () { }

  /** The docked sheet's color role (tabs and pages). */
  public static final int SHEET_ROLE = XgramExpressive.SURFACE_CONTAINER_LOW;

  public static int sheetColor () {
    return XgramExpressive.color(SHEET_ROLE);
  }

  // List item metrics (dp): 16dp padding, a 40dp leading element, 16dp to the text

  public static final float ROW_PADDING = 16f, LEADING_SIZE = 40f, LEADING_CORNER = 12f, TEXT_START = 72f;

  /** Headline: titleMedium. */
  public static TextStyleProvider titleProvider () {
    return XgramM3Profile.flexProvider(XgramExpressive.TS_TITLE_M, false, XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M));
  }

  /** Supporting text and links: bodyMedium. */
  public static TextStyleProvider bodyProvider () {
    return XgramM3Profile.flexTextProvider(XgramExpressive.TS_BODY_M, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M));
  }

  // Tonal leading tiles

  private static final int[] TONAL_CONTAINER = {XgramExpressive.PRIMARY_CONTAINER, XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.TERTIARY_CONTAINER};
  private static final int[] TONAL_CONTENT = {XgramExpressive.ON_PRIMARY_CONTAINER, XgramExpressive.ON_SECONDARY_CONTAINER, XgramExpressive.ON_TERTIARY_CONTAINER};

  /** One of the three container roles, stable for a seed (an accent color id, a color id). */
  public static int tonalIndex (int seed) {
    int i = seed % TONAL_CONTAINER.length;
    return i < 0 ? i + TONAL_CONTAINER.length : i;
  }

  private static final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static @Nullable TextPaint letterPaint;

  private static TextPaint letterPaint () {
    TextPaint paint = letterPaint;
    if (paint == null) {
      paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_TITLE_M, false));
      paint.setTextSize(Screen.sp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
      letterPaint = paint;
    }
    return paint;
  }

  /**
   * A tonal tile with letters in its content role ({@code cornerPx} half the size draws a circle). Returns the letters'
   * width, so the caller measures them once: pass the returned value back as {@code lettersWidth} (negative to measure).
   */
  public static float drawLetterTile (Canvas c, RectF rect, float cornerPx, int tonal, @Nullable String letters, float lettersWidth) {
    tilePaint.setColor(XgramExpressive.color(TONAL_CONTAINER[tonal]));
    c.drawRoundRect(rect, cornerPx, cornerPx, tilePaint);
    if (letters == null || letters.isEmpty()) {
      return lettersWidth;
    }
    TextPaint paint = letterPaint();
    if (lettersWidth < 0f) {
      lettersWidth = paint.measureText(letters);
    }
    paint.setColor(XgramExpressive.color(TONAL_CONTENT[tonal]));
    float baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2f;
    c.drawText(letters, rect.centerX() - lettersWidth / 2f, baseline, paint);
    return lettersWidth;
  }

  /** A tonal tile with an icon in its content role. */
  public static void drawIconTile (Canvas c, RectF rect, float cornerPx, int tonal, @Nullable Drawable icon) {
    tilePaint.setColor(XgramExpressive.color(TONAL_CONTAINER[tonal]));
    c.drawRoundRect(rect, cornerPx, cornerPx, tilePaint);
    if (icon != null) {
      Drawables.draw(c, icon, rect.centerX() - icon.getMinimumWidth() / 2f, rect.centerY() - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(XgramExpressive.color(TONAL_CONTENT[tonal])));
    }
  }

  // Counter footer ("2 links")

  /** bodySmall in onSurfaceVariant. Set at bind time; nothing changes when it already is. */
  public static void styleFooter (@NonNull ViewGroup infoView) {
    final float sizePx = Screen.dpf(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_S));
    final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    for (int i = 0; i < infoView.getChildCount(); i++) {
      View child = infoView.getChildAt(i);
      if (child instanceof TextView) {
        TextView text = (TextView) child;
        if (Math.abs(text.getTextSize() - sizePx) > .5f) {
          XgramExpressive.applyText(text, XgramExpressive.TS_BODY_S, false);
        }
        if (text.getCurrentTextColor() != color) {
          text.setTextColor(color);
        }
      }
    }
  }

  // The docked sheet

  /**
   * The top of the docked sheet, drawn behind the tab strip: {@link #SHEET_ROLE} with top corners of
   * {@link #setRadius} pixels (square at 0), inset from the host's top and bottom.
   */
  public static final class DockedSheetDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int topInset, bottomInset;
    private float radius;

    public DockedSheetDrawable (int topInsetPx, int bottomInsetPx) {
      this.topInset = topInsetPx;
      this.bottomInset = bottomInsetPx;
    }

    public int getTopInset () {
      return topInset;
    }

    public float getRadius () {
      return radius;
    }

    public void setRadius (float radiusPx) {
      radiusPx = Math.max(0f, radiusPx);
      if (this.radius != radiusPx) {
        this.radius = radiusPx;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      float top = bounds.top + topInset, bottom = bounds.bottom - bottomInset;
      if (bottom <= top) {
        return;
      }
      paint.setColor(sheetColor());
      float r = Math.min(radius, (bottom - top) / 2f);
      if (r < .5f) {
        c.drawRect(bounds.left, top, bounds.right, bottom, paint);
        return;
      }
      rect.set(bounds.left, top, bounds.right, bottom);
      c.drawRoundRect(rect, r, r, paint);
      c.drawRect(bounds.left, top + r, bounds.right, bottom, paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * The rest of the sheet, behind the pages: {@link #SHEET_ROLE} from {@link #setTop} down. The pages draw no fill of
   * their own, so they can fade on a tab switch without the page color showing through.
   */
  public static final class BackdropDrawable extends Drawable {
    private final Paint paint = new Paint();
    private float top = Float.MAX_VALUE;

    public void setTop (float y) {
      if (this.top != y) {
        this.top = y;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      float y = Math.max(bounds.top, bounds.top + top);
      if (y < bounds.bottom) {
        paint.setColor(sheetColor());
        c.drawRect(bounds.left, y, bounds.right, bounds.bottom, paint);
      }
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  // Tab switch

  /**
   * M3 shared axis X for a tab tap: the pages stay in place, the outgoing one slides 30dp away and fades out while the
   * incoming one slides in from 30dp and fades in (fade through at the midpoint). Only while {@link #setActive}; a
   * finger swipe keeps the plain slide. View properties only, no layers.
   */
  public static final class SharedAxisTransformer implements androidx.viewpager.widget.ViewPager.PageTransformer {
    private boolean active;

    public void setActive (boolean active) {
      this.active = active;
    }

    public boolean isActive () {
      return active;
    }

    @Override
    public void transformPage (@NonNull View page, float position) {
      if (!active || position <= -1f || position >= 1f) {
        if (page.getAlpha() != 1f) {
          page.setAlpha(1f);
        }
        if (page.getTranslationX() != 0f) {
          page.setTranslationX(0f);
        }
        return;
      }
      final float width = page.getWidth();
      page.setTranslationX(position * (Screen.dp(30f) - width));
      page.setAlpha(XgramMotion.clamp01(1f - Math.abs(position) * 2f));
    }
  }

  // Segmented lists hosted by the profile

  /**
   * The segmented list look for shared media lists inside the profile, without any vertical offset (the profile's
   * scroll sync measures items by type): rows inside a group become surfaceBright tiles inset 16dp (their own
   * MorphDrawable background gets the segment, as in the settings lists), the 2dp gaps between directly adjoining
   * rows are painted in the sheet color, and date headers stay in primary.
   */
  public static final class ProfileListDecoration extends RecyclerView.ItemDecoration {
    private final Paint gapPaint = new Paint();

    @Nullable
    private static List<ListItem> itemsOf (RecyclerView parent) {
      RecyclerView.Adapter<?> adapter = parent.getAdapter();
      return adapter instanceof SettingsAdapter ? ((SettingsAdapter) adapter).getItems() : null;
    }

    private static int positionOf (RecyclerView parent, View view, List<ListItem> items) {
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION) {
        Object tag = view.getTag();
        if (tag instanceof ListItem) {
          position = items.indexOf(tag);
        }
      }
      return position >= 0 && position < items.size() ? position : RecyclerView.NO_POSITION;
    }

    /** A list row inside a group; the edge-to-edge media grid and the full-page states stay as they are. */
    private static boolean isTile (List<ListItem> items, int position) {
      int type = items.get(position).getViewType();
      switch (type) {
        case ListItem.TYPE_SMALL_MEDIA:
        case ListItem.TYPE_SMART_EMPTY:
        case ListItem.TYPE_SMART_PROGRESS:
        case ListItem.TYPE_LIST_INFO_VIEW:
        case ListItem.TYPE_FAKE_PAGER_TOPVIEW:
          return false;
      }
      return XgramExpressive.isRowType(type) && XgramExpressive.isInGroup(items, position);
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      List<ListItem> items = itemsOf(parent);
      if (items == null) {
        return;
      }
      int position = positionOf(parent, view, items);
      if (position == RecyclerView.NO_POSITION) {
        return;
      }
      int type = items.get(position).getViewType();
      if (isTile(items, position) || XgramExpressive.isHeaderType(type) || type == ListItem.TYPE_DESCRIPTION) {
        int inset = Screen.dp(XgramExpressive.LIST_INSET);
        outRect.left = inset;
        outRect.right = inset;
      }
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      List<ListItem> items = itemsOf(parent);
      if (items == null) {
        return;
      }
      final int childCount = parent.getChildCount();
      int primary = 0;
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        int position = positionOf(parent, child, items);
        if (position == RecyclerView.NO_POSITION) {
          continue;
        }
        int type = items.get(position).getViewType();
        if (XgramExpressive.isHeaderType(type)) {
          if (primary == 0) {
            primary = XgramExpressive.color(XgramExpressive.PRIMARY);
          }
          TextView text = findText(child);
          if (text != null && text.getCurrentTextColor() != primary) {
            text.setTextColor(primary);
          }
          continue;
        }
        XgramExpressive.MorphDrawable morph = XgramExpressive.findMorph(child.getBackground());
        if (morph == null) {
          continue;
        }
        if (isTile(items, position)) {
          morph.setSegment(XgramExpressive.segmentOf(items, position));
        } else if (morph.getSegment() != XgramExpressive.SEGMENT_NONE) {
          morph.setSegment(XgramExpressive.SEGMENT_NONE);
        }
      }
    }

    @Override
    public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      List<ListItem> items = itemsOf(parent);
      if (items == null) {
        return;
      }
      final int childCount = parent.getChildCount();
      final float half = Screen.dp(XgramExpressive.LIST_GAP) / 2f;
      boolean colorSet = false;
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        int position = positionOf(parent, child, items);
        if (position <= 0 || !isTile(items, position) || !isTile(items, position - 1)) {
          continue;
        }
        if (!colorSet) {
          gapPaint.setColor(sheetColor());
          colorSet = true;
        }
        float top = child.getTop() + child.getTranslationY();
        float left = child.getLeft() + child.getTranslationX();
        c.drawRect(left, top - half, left + child.getWidth(), top + half, gapPaint);
      }
    }

    @Nullable
    private static TextView findText (View child) {
      if (child instanceof TextView) {
        return (TextView) child;
      }
      if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0 && ((ViewGroup) child).getChildAt(0) instanceof TextView) {
        return (TextView) ((ViewGroup) child).getChildAt(0);
      }
      return null;
    }
  }
}
