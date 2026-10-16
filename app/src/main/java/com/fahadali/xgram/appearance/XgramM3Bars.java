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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Material 3 Expressive app bars beyond the bar itself: the content container with rounded top corners that sits on the
 * app bar's layer (Google Messages 2025 home), and the medium flexible app bar title of settings pages (Android 16
 * Settings), whose large title scrolls away under the small one.
 */
public final class XgramM3Bars {
  private XgramM3Bars () { }

  // Content container with rounded top corners

  /**
   * Fills the two top corner notches of a content container with the app bar color, so the content reads as a
   * container with 28dp top corners on the app bar's layer. Drawn over the content (no outline clipping: Telegram X
   * content often starts under the header), at the y where the content starts.
   */
  public static final class ContainerCorners {
    // Paths are built at the origin for a width and radius, and translated when drawn, so scrolling does not rebuild them
    private final Path path = new Path(), bottomPath = new Path();
    private final RectF arc = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pathWidth = -1, bottomPathWidth = -1;
    private float pathRadius, bottomPathRadius;
    private int colorId = ColorId.headerBackground;
    private float radiusDp = XgramExpressive.CORNER_XL;

    /** The color the notches take: the app bar's (headerBackground, a surface container with Expressive design). */
    public void setColorId (int colorId) {
      this.colorId = colorId;
    }

    /** The corner radius in dp (a CORNER_* token); {@link XgramExpressive#CORNER_XL} by default. */
    public void setRadius (float radiusDp) {
      this.radiusDp = radiusDp;
    }

    private float radius (int width) {
      return Math.min(Screen.dpf(radiusDp), width / 2f);
    }

    /** Top corners of a container whose top edge is at {@code top}: the notches lie below it. */
    public void draw (@NonNull Canvas c, int left, int top, int right) {
      if (!XgramExpressive.isEnabled() || right <= left) {
        return;
      }
      int width = right - left;
      float r = radius(width);
      if (width != pathWidth || r != pathRadius) {
        pathWidth = width;
        pathRadius = r;
        path.rewind();
        // Top start notch: the square corner minus the quarter circle
        path.moveTo(0, 0);
        path.lineTo(r, 0);
        arc.set(0, 0, r * 2f, r * 2f);
        path.arcTo(arc, 270f, -90f, false);
        path.close();
        // Top end notch
        path.moveTo(width, 0);
        path.lineTo(width, r);
        arc.set(width - r * 2f, 0, width, r * 2f);
        path.arcTo(arc, 0f, -90f, false);
        path.close();
      }
      drawPath(c, path, left, top);
    }

    /** Bottom corners of a container whose bottom edge is at {@code bottom}: the notches lie above it. */
    public void drawBottom (@NonNull Canvas c, int left, int bottom, int right) {
      if (!XgramExpressive.isEnabled() || right <= left) {
        return;
      }
      int width = right - left;
      float r = radius(width);
      if (width != bottomPathWidth || r != bottomPathRadius) {
        bottomPathWidth = width;
        bottomPathRadius = r;
        bottomPath.rewind();
        // Bottom start notch
        bottomPath.moveTo(0, 0);
        bottomPath.lineTo(0, -r);
        arc.set(0, -r * 2f, r * 2f, 0);
        bottomPath.arcTo(arc, 180f, -90f, false);
        bottomPath.close();
        // Bottom end notch
        bottomPath.moveTo(width, 0);
        bottomPath.lineTo(width - r, 0);
        arc.set(width - r * 2f, -r * 2f, width, 0);
        bottomPath.arcTo(arc, 90f, -90f, false);
        bottomPath.close();
      }
      drawPath(c, bottomPath, left, bottom);
    }

    private void drawPath (@NonNull Canvas c, @NonNull Path path, int dx, int dy) {
      paint.setColor(Theme.getColor(colorId));
      c.save();
      c.translate(dx, dy);
      c.drawPath(path, paint);
      c.restore();
    }
  }

  /**
   * A FrameLayout whose content gets rounded top corners (see {@link ContainerCorners}) at the top of its anchor child
   * (or its own top). Children added later than the anchor (a FAB in another parent) are not covered.
   */
  public static class CornerFrameLayout extends FrameLayoutFix {
    private final ContainerCorners corners = new ContainerCorners();
    private @Nullable View anchor;

    public CornerFrameLayout (Context context) {
      super(context);
    }

    public void setCornerAnchor (@Nullable View anchor) {
      this.anchor = anchor;
      invalidate();
    }

    private int sideInset;
    private final Paint sidePaint = new Paint();

    /**
     * Keeps the container {@code px} clear of both sides (the narrow chat list pane of the two-pane layout), with the
     * app bar color in the margins, like the settings groups in the other pane. The anchor gets the margins, so its
     * content is laid out inside them.
     */
    public void setSideInset (int px) {
      px = Math.max(0, px);
      if (sideInset == px) {
        return;
      }
      sideInset = px;
      View anchor = this.anchor;
      if (anchor != null && anchor.getLayoutParams() instanceof MarginLayoutParams) {
        MarginLayoutParams params = (MarginLayoutParams) anchor.getLayoutParams();
        params.leftMargin = px;
        params.rightMargin = px;
        anchor.setLayoutParams(params);
      }
      invalidate();
    }

    /** The side margins set with {@link #setSideInset}, in px. */
    public int getSideInset () {
      return sideInset;
    }

    /**
     * The corners and side margins go right over the anchor, so children added after it (the folder filter menu, the
     * bottom folder tabs and their space) are drawn over them, not cut by them. Without an anchor in this layout, over
     * every child.
     */
    @Override
    protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
      boolean result = super.drawChild(canvas, child, drawingTime);
      if (child == anchor && XgramExpressive.isEnabled()) {
        drawDecor(canvas);
      }
      return result;
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      View anchor = this.anchor;
      if ((anchor == null || anchor.getParent() != this) && XgramExpressive.isEnabled()) {
        drawDecor(canvas);
      }
    }

    private void drawDecor (Canvas canvas) {
      int top = 0, bottom = getHeight();
      View anchor = this.anchor;
      if (anchor != null && anchor.getParent() == this) {
        if (anchor.getVisibility() != View.VISIBLE) {
          return;
        }
        int dy = Math.round(anchor.getTranslationY());
        top = anchor.getTop() + dy;
        bottom = anchor.getBottom() + dy;
      }
      int inset = sideInset;
      if (inset > 0 && getWidth() > inset * 2 && bottom > top) {
        sidePaint.setColor(Theme.getColor(ColorId.headerBackground));
        canvas.drawRect(0, top, inset, bottom, sidePaint);
        canvas.drawRect(getWidth() - inset, top, getWidth(), bottom, sidePaint);
        corners.draw(canvas, inset, top, getWidth() - inset);
      } else {
        corners.draw(canvas, 0, top, getWidth());
      }
    }
  }

  // Medium flexible app bar title (experimental, Android 16 Settings)

  private static final float TITLE_AREA = 56f, TITLE_BASELINE = 40f, TITLE_START = 16f;
  private static final float SMALL_TITLE_START = 24f, SMALL_TITLE_DISTANCE = 32f;

  /**
   * Adds a 56dp area above the first item (the app bar expanded to 112dp, Compose AppBarMediumFlexible) with the page
   * title in headlineMedium, and fades the app bar's small title in as that area scrolls away. Item heights (and so
   * SettingHolder.measureHeightForType) are untouched.
   */
  public static final class LargeTitleDecoration extends RecyclerView.ItemDecoration {
    private final ViewController<?> controller;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence lastName, lastEllipsized;
    private int lastWidth = -1;

    LargeTitleDecoration (ViewController<?> controller) {
      this.controller = controller;
      paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_HEADLINE_M, true));
      paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_HEADLINE_M)));
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      if (parent.getChildAdapterPosition(view) == 0) {
        outRect.top = Screen.dp(TITLE_AREA);
      }
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      final int childCount = parent.getChildCount();
      if (childCount == 0) {
        return;
      }
      View first = null;
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (child != null && parent.getChildAdapterPosition(child) == 0) {
          first = child;
          break;
        }
      }
      if (first == null) {
        HeaderView.xgramSetTitleAlpha(controller, 1f);
        return;
      }
      RecyclerView.LayoutManager manager = parent.getLayoutManager();
      int decorTop = manager != null ? manager.getTopDecorationHeight(first) : Screen.dp(TITLE_AREA);
      float areaTop = first.getTop() + first.getTranslationY() - decorTop;
      float scrolled = parent.getPaddingTop() - areaTop;
      HeaderView.xgramSetTitleAlpha(controller, (scrolled - Screen.dp(SMALL_TITLE_START)) / (float) Screen.dp(SMALL_TITLE_DISTANCE));

      CharSequence name = controller.getName();
      if (TextUtils.isEmpty(name)) {
        return;
      }
      int start = Screen.dp(TITLE_START);
      int available = parent.getWidth() - start * 2;
      if (available <= 0) {
        return;
      }
      if (lastEllipsized == null || lastWidth != available || !TextUtils.equals(lastName, name)) {
        lastName = name;
        lastWidth = available;
        lastEllipsized = TextUtils.ellipsize(name, paint, available, TextUtils.TruncateAt.END);
      }
      CharSequence text = lastEllipsized;
      paint.setColor(Theme.getColor(ColorId.text));
      float textWidth = paint.measureText(text, 0, text.length());
      float x = Lang.rtl() ? parent.getWidth() - start - textWidth : start;
      float baseline = areaTop + Screen.dp(TITLE_BASELINE);
      c.drawText(text, 0, text.length(), x, baseline, paint);
    }
  }

  private static boolean isInPopup (View view) {
    ViewParent parent = view.getParent();
    while (parent != null) {
      if (parent instanceof PopupLayout) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /** The list is the page's own content: the controller's view or its direct child. */
  private static boolean isMainContent (ViewController<?> controller, RecyclerView recyclerView) {
    View value = controller.getValue();
    return recyclerView == value || recyclerView.getParent() == value;
  }

  private static void attachNow (ViewController<?> controller, RecyclerView recyclerView) {
    if (!XgramExpressive.isEnabled() || recyclerView.getTag(R.id.xgram_m3e_large_title) instanceof LargeTitleDecoration) {
      return;
    }
    if (!controller.xgramAllowsLargeTitle() || isInPopup(recyclerView) || !isMainContent(controller, recyclerView)) {
      return;
    }
    LargeTitleDecoration decoration = new LargeTitleDecoration(controller);
    recyclerView.addItemDecoration(decoration);
    recyclerView.setTag(R.id.xgram_m3e_large_title, decoration);
  }

  /**
   * Gives a settings page's list the medium flexible app bar title. The checks need the list in its window (its page
   * view built, its parents known), so they run when it is attached.
   */
  public static void attachLargeTitle (@Nullable ViewController<?> controller, RecyclerView recyclerView) {
    if (controller == null || !XgramExpressive.isEnabled() || recyclerView.getTag(R.id.xgram_m3e_large_title_listener) != null) {
      return;
    }
    View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (@NonNull View v) {
        attachNow(controller, recyclerView);
      }

      @Override
      public void onViewDetachedFromWindow (@NonNull View v) { }
    };
    recyclerView.setTag(R.id.xgram_m3e_large_title_listener, listener);
    recyclerView.addOnAttachStateChangeListener(listener);
    if (recyclerView.isAttachedToWindow()) {
      attachNow(controller, recyclerView);
    }
  }

  public static void detachLargeTitle (RecyclerView recyclerView) {
    Object listener = recyclerView.getTag(R.id.xgram_m3e_large_title_listener);
    if (listener instanceof View.OnAttachStateChangeListener) {
      recyclerView.removeOnAttachStateChangeListener((View.OnAttachStateChangeListener) listener);
    }
    recyclerView.setTag(R.id.xgram_m3e_large_title_listener, null);
    Object decoration = recyclerView.getTag(R.id.xgram_m3e_large_title);
    if (decoration instanceof LargeTitleDecoration) {
      recyclerView.removeItemDecoration((LargeTitleDecoration) decoration);
      HeaderView.xgramSetTitleAlpha(((LargeTitleDecoration) decoration).controller, 1f);
    }
    recyclerView.setTag(R.id.xgram_m3e_large_title, null);
  }
}
