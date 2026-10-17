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
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Material 3 Expressive app bars beyond the bar itself: the content container with rounded top corners that sits on the
 * app bar's layer (Google Messages 2025 home), and the large flexible app bar title of settings pages (Android 16
 * Settings, as the Compose pages' SettingsTopAppBar), whose large title scrolls away under the small one.
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

  // Large and medium flexible app bar titles (Compose LargeFlexibleTopAppBar / MediumFlexibleTopAppBar)

  /**
   * The large flexible app bar of SettingsTopAppBar(large = true): the title in displaySmall over a bar expanded to 120dp
   * (152dp with a subtitle). The default of every classic page, so it matches the Compose pages next to it.
   */
  public static final int TITLE_LARGE = 0;
  /** The medium flexible app bar: headlineMedium over a bar expanded to 112dp (136dp with a subtitle), on request. */
  public static final int TITLE_MEDIUM = 1;

  /** Compose bar heights: collapsed 64dp, expanded as above; the last baseline 28dp (large) or 24dp above the bottom. */
  private static final float BAR_COLLAPSED = 64f;
  private static final float LARGE_EXPANDED = 120f, LARGE_EXPANDED_SUBTITLE = 152f, LARGE_BOTTOM = 28f;
  private static final float MEDIUM_EXPANDED = 112f, MEDIUM_EXPANDED_SUBTITLE = 136f, MEDIUM_BOTTOM = 24f;
  private static final float TITLE_START = 16f;
  /** As SettingsTopAppBar's crossfade: the large title is gone by 60% of the collapse, the small one fades in over the last 60%. */
  private static final float FADE_SPAN = .6f;

  /**
   * Adds the expanded part of the app bar above the first item (the classic header is the collapsed bar, so the area is
   * the expanded height minus the header's), draws the page title there in displaySmall (headlineMedium for
   * {@link #TITLE_MEDIUM}) 16dp from the start, with an optional bodyMedium subtitle under it, and crossfades it with the
   * app bar's small title as that area scrolls away. A title left half collapsed when the list stops snaps to the
   * nearer end on the fast spatial spring. Item heights (and so SettingHolder.measureHeightForType) are untouched.
   */
  public static final class LargeTitleDecoration extends RecyclerView.ItemDecoration {
    private final ViewController<?> controller;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private CharSequence lastName, lastEllipsized, lastSubtitle, lastSubtitleEllipsized;
    private int lastWidth = -1, lastSubtitleWidth = -1;

    /** The geometry for {@link #style} with or without a subtitle: the area's height and the baselines from its top (px). */
    private int style = -1;
    private boolean hasSubtitle;
    private int areaHeight;
    private float titleBaseline, subtitleBaseline;

    /** Snaps a half-collapsed title when the list stops. */
    final RecyclerView.OnScrollListener snapListener = new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          snap(recyclerView);
        }
      }
    };

    LargeTitleDecoration (ViewController<?> controller) {
      this.controller = controller;
    }

    private static void applyStyle (TextPaint p, int ts) {
      p.setTypeface(XgramExpressive.typeface(ts, false));
      p.setTextSize(Screen.sp(XgramExpressive.textSizeSp(ts)));
      p.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    }

    /** Builds the paints and the geometry when the style or the subtitle's presence changes (not per frame). */
    private void measure (int style, boolean hasSubtitle) {
      if (this.style == style && this.hasSubtitle == hasSubtitle && areaHeight > 0) {
        return;
      }
      this.style = style;
      this.hasSubtitle = hasSubtitle;
      this.lastEllipsized = null;
      this.lastSubtitleEllipsized = null;
      final boolean large = style != TITLE_MEDIUM;
      final int ts = large ? XgramExpressive.TS_DISPLAY_S : XgramExpressive.TS_HEADLINE_M;
      applyStyle(paint, ts);
      applyStyle(subtitlePaint, XgramExpressive.TS_BODY_M);

      final float expanded = large ? (hasSubtitle ? LARGE_EXPANDED_SUBTITLE : LARGE_EXPANDED) : (hasSubtitle ? MEDIUM_EXPANDED_SUBTITLE : MEDIUM_EXPANDED);
      // Compose's expanded row: the bar's expanded height minus its collapsed row
      final float rowBase = Screen.dpf(expanded - BAR_COLLAPSED);
      final float titleLine = Screen.sp(XgramExpressive.lineHeightSp(ts));
      paint.getFontMetrics(metrics);
      final float titleAscent = -metrics.ascent;
      final float titleLeading = Math.max(0f, (titleLine - (metrics.descent - metrics.ascent)) / 2f);
      float subtitleLine = 0f, subtitleAscent = 0f, subtitleLeading = 0f;
      if (hasSubtitle) {
        subtitleLine = Screen.sp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M));
        subtitlePaint.getFontMetrics(metrics);
        subtitleAscent = -metrics.ascent;
        subtitleLeading = Math.max(0f, (subtitleLine - (metrics.descent - metrics.ascent)) / 2f);
      }
      final float block = titleLine + subtitleLine;
      final float lastBaseline = hasSubtitle ? titleLine + subtitleLeading + subtitleAscent : titleLeading + titleAscent;
      // M3 TwoRowsTopAppBar: the last baseline that far above the row's bottom, moved up as far as the block needs to
      // fit; a block taller than the row (large font sizes) grows the row.
      final float row = Math.max(rowBase, block);
      float fromBottom = Screen.dpf(large ? LARGE_BOTTOM : MEDIUM_BOTTOM) - (block - lastBaseline);
      if (fromBottom + block > row) {
        fromBottom = row - block;
      }
      // The classic header is the collapsed bar: the area is the rest of the expanded bar below it
      areaHeight = Math.round(Screen.dpf(expanded) - Size.getHeaderPortraitSize() + (row - rowBase));
      final float blockTop = areaHeight - block - Math.max(0f, fromBottom);
      titleBaseline = blockTop + titleLeading + titleAscent;
      subtitleBaseline = blockTop + titleLine + subtitleLeading + subtitleAscent;
    }

    private void measureFor (RecyclerView parent) {
      measure(styleOf(parent), !TextUtils.isEmpty(subtitleOf(parent)));
    }

    @Nullable
    private static View firstItem (RecyclerView parent) {
      final int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (child != null && parent.getChildAdapterPosition(child) == 0) {
          return child;
        }
      }
      return null;
    }

    /** Where the area's top is now, in the list's coordinates. */
    private float areaTop (RecyclerView parent, View first) {
      RecyclerView.LayoutManager manager = parent.getLayoutManager();
      int decorTop = manager != null ? manager.getTopDecorationHeight(first) : areaHeight;
      return first.getTop() + first.getTranslationY() - decorTop;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      if (parent.getChildAdapterPosition(view) == 0) {
        measureFor(parent);
        outRect.top = areaHeight;
      }
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      if (!XgramExpressive.isEnabled() || parent.getChildCount() == 0) {
        return;
      }
      final CharSequence subtitle = subtitleOf(parent);
      measure(styleOf(parent), !TextUtils.isEmpty(subtitle));
      final View first = firstItem(parent);
      if (first == null) {
        HeaderView.xgramSetTitleAlpha(controller, 1f);
        return;
      }
      final float areaTop = areaTop(parent, first);
      final float scrolled = parent.getPaddingTop() - areaTop;
      final float collapse = areaHeight > 0 ? Math.max(0f, Math.min(1f, scrolled / areaHeight)) : 1f;
      HeaderView.xgramSetTitleAlpha(controller, (collapse - (1f - FADE_SPAN)) / FADE_SPAN);
      final float alpha = Math.max(0f, Math.min(1f, 1f - collapse / FADE_SPAN));
      if (alpha <= 0f) {
        return;
      }

      final CharSequence name = controller.getName();
      if (TextUtils.isEmpty(name)) {
        return;
      }
      final int start = Screen.dp(TITLE_START);
      final int available = parent.getWidth() - start * 2;
      if (available <= 0) {
        return;
      }
      final boolean rtl = Lang.rtl();
      if (lastEllipsized == null || lastWidth != available || !TextUtils.equals(lastName, name)) {
        lastName = name;
        lastWidth = available;
        lastEllipsized = TextUtils.ellipsize(name, paint, available, TextUtils.TruncateAt.END);
      }
      final CharSequence text = lastEllipsized;
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), alpha));
      final float textWidth = paint.measureText(text, 0, text.length());
      c.drawText(text, 0, text.length(), rtl ? parent.getWidth() - start - textWidth : start, areaTop + titleBaseline, paint);

      if (!TextUtils.isEmpty(subtitle)) {
        if (lastSubtitleEllipsized == null || lastSubtitleWidth != available || !TextUtils.equals(lastSubtitle, subtitle)) {
          lastSubtitle = subtitle;
          lastSubtitleWidth = available;
          lastSubtitleEllipsized = TextUtils.ellipsize(subtitle, subtitlePaint, available, TextUtils.TruncateAt.END);
        }
        final CharSequence line = lastSubtitleEllipsized;
        subtitlePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), alpha));
        final float lineWidth = subtitlePaint.measureText(line, 0, line.length());
        c.drawText(line, 0, line.length(), rtl ? parent.getWidth() - start - lineWidth : start, areaTop + subtitleBaseline, subtitlePaint);
      }
    }

    /** A title the list left half collapsed goes to the nearer end on the fast spatial spring (standard with Reduce motion). */
    void snap (RecyclerView recyclerView) {
      if (!XgramExpressive.isEnabled() || areaHeight <= 0) {
        return;
      }
      final View first = firstItem(recyclerView);
      if (first == null) {
        return;
      }
      final int scrolled = Math.round(recyclerView.getPaddingTop() - areaTop(recyclerView, first));
      if (scrolled <= 0 || scrolled >= areaHeight) {
        return;
      }
      final int target = scrolled * 2 >= areaHeight ? areaHeight : 0;
      if (target > scrolled && !recyclerView.canScrollVertically(1)) {
        return; // a page too short to collapse stays where the finger left it
      }
      XgramMotion.springScrollBy(recyclerView, 0, target - scrolled,
        XgramMotion.stiffness(XgramExpressive.SPATIAL_FAST), XgramMotion.damping(XgramExpressive.SPATIAL_FAST));
    }
  }

  private static int styleOf (RecyclerView recyclerView) {
    Object style = recyclerView.getTag(R.id.xgram_r6b5_title_style);
    return style instanceof Integer ? (Integer) style : TITLE_LARGE;
  }

  @Nullable
  private static CharSequence subtitleOf (RecyclerView recyclerView) {
    Object subtitle = recyclerView.getTag(R.id.xgram_r6b5_title_subtitle);
    return subtitle instanceof CharSequence ? (CharSequence) subtitle : null;
  }

  /** {@link #TITLE_LARGE} (the default) or {@link #TITLE_MEDIUM} for the large title of {@code recyclerView}'s page. */
  public static void setLargeTitleStyle (RecyclerView recyclerView, int style) {
    if (styleOf(recyclerView) != style) {
      recyclerView.setTag(R.id.xgram_r6b5_title_style, style);
      invalidateTitle(recyclerView);
    }
  }

  /** A bodyMedium line under the large title (Log out: "Alternative options"); null for none. Safe before or after attaching. */
  public static void setLargeTitleSubtitle (RecyclerView recyclerView, @Nullable CharSequence subtitle) {
    if (!TextUtils.equals(subtitleOf(recyclerView), subtitle)) {
      recyclerView.setTag(R.id.xgram_r6b5_title_subtitle, subtitle);
      invalidateTitle(recyclerView);
    }
  }

  private static void invalidateTitle (RecyclerView recyclerView) {
    if (recyclerView.getTag(R.id.xgram_m3e_large_title) instanceof LargeTitleDecoration && !recyclerView.isComputingLayout()) {
      recyclerView.invalidateItemDecorations();
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
    recyclerView.addOnScrollListener(decoration.snapListener);
    recyclerView.setTag(R.id.xgram_m3e_large_title, decoration);
  }

  /** {@link #attachLargeTitle(ViewController, RecyclerView)} in {@code style} ({@link #TITLE_LARGE} or {@link #TITLE_MEDIUM}). */
  public static void attachLargeTitle (@Nullable ViewController<?> controller, RecyclerView recyclerView, int style) {
    if (controller == null || !XgramExpressive.isEnabled()) {
      return;
    }
    setLargeTitleStyle(recyclerView, style);
    attachLargeTitle(controller, recyclerView);
  }

  /**
   * Gives a settings page's list the large flexible app bar title ({@link #TITLE_LARGE}, the Compose pages' look; see
   * {@link #setLargeTitleStyle}). The checks need the list in its window (its page view built, its parents known), so
   * they run when it is attached.
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
      recyclerView.removeOnScrollListener(((LargeTitleDecoration) decoration).snapListener);
      recyclerView.removeItemDecoration((LargeTitleDecoration) decoration);
      HeaderView.xgramSetTitleAlpha(((LargeTitleDecoration) decoration).controller, 1f);
    }
    recyclerView.setTag(R.id.xgram_m3e_large_title, null);
  }
}
