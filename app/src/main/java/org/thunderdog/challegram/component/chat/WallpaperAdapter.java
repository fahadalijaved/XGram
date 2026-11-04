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
 * File created on 01/04/2017
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibWallpaperManager;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.ViewHolder> implements View.OnClickListener, ChatStyleChangeListener {
  private static final int[] SOLID_COLORS = {
    0x1F2327,
    0x2D2033,
    0x882B41,
    0x604D52,
    0x5A7D7F,
    0x57626F,
    0x395586,
    0x478FB7,
    0x76BBCC,
    0x7ECFD4,
    0xC2E3E4,
    0xB5D7C9,
    0x8BC7A8,
    0xFDEFB1,
    0xFFFDAD,
    0xEB566A,
    0xEE706E,
    0xF19F9E,
    0xD07356,
    0xF8D29E,
    0xE5E0B6,
    0xD3D9C1,
    0xE7E8D4,
    0xCDDAEB,
    0xD4D1ED,
    0xCECECE,
    0xEDF0F1
  };
  
  public static final int TYPE_WALLPAPER = 0;
  public static final int TYPE_PROGRESS = 1;

  private final ViewController<?> context;

  private @Nullable ArrayList<TGBackground> wallpapers;
  private TGBackground selectedWallpaper;

  private final ArrayList<RecyclerView> attachedRecyclers;
  private final int themeId;

  public WallpaperAdapter (ViewController<?> context, int themeId) {
    this.context = context;
    this.themeId = themeId;
    this.selectedWallpaper = context.tdlib().settings().getWallpaper(Theme.getWallpaperIdentifier());
    this.attachedRecyclers = new ArrayList<>();
    context.tdlib().wallpaper().getBackgrounds(this::setWallpapers, Theme.isDarkTheme(themeId));
    ThemeManager.instance().addChatStyleListener(this);
  }

  public void destroy () {
    ThemeManager.instance().removeChatStyleListener(this);
  }

  @Override
  public void onAttachedToRecyclerView (@NonNull RecyclerView recyclerView) {
    attachedRecyclers.add(recyclerView);
    int i = indexOfWallpaper(selectedWallpaper);
    if (i != -1) {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(i, Screen.currentWidth() / 2 - xgramCellSize() / 2);
    }
  }

  @Override
  public void onDetachedFromRecyclerView (RecyclerView recyclerView) {
    attachedRecyclers.remove(recyclerView);
  }

  private TGBackground lastFoundWallpaper;
  private int lastFoundIndex = -1;

  // XGram M3E: the chat draws no wallpaper, the theme's own default and Telegram's black library patterns all as its
  // plain surface, so they share one tile: the "surface tile", labelled "No wallpaper" (or "Pure black")

  private int xgramSurfaceIndex = -1;

  private boolean xgramIsSurface (@Nullable TGBackground w) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return false;
    }
    if (w == null || w.isEmpty() || com.fahadali.xgram.appearance.XgramWallpaperDraw.drawsPlainBlack(w)) {
      return true;
    }
    return !w.isCustom() && TdlibWallpaperManager.isSameWallpaperName(w.getName(), Theme.getDefaultWallpaper(themeId));
  }

  /** XGram: {@code a} and {@code b} are shown by the same tile. */
  private boolean xgramSameTile (@Nullable TGBackground a, @Nullable TGBackground b) {
    return TGBackground.compare(a, b) || (xgramIsSurface(a) && xgramIsSurface(b));
  }

  private void xgramFindSurface () {
    xgramSurfaceIndex = -1;
    if (wallpapers == null) {
      return;
    }
    for (int i = 0; i < wallpapers.size(); i++) {
      if (xgramIsSurface(wallpapers.get(i))) {
        xgramSurfaceIndex = i;
        return;
      }
    }
  }

  private int indexOfWallpaper (TGBackground wallpaper) {
    if (wallpapers == null)
      return -1;
    if (xgramSurfaceIndex != -1 && xgramIsSurface(wallpaper))
      return xgramSurfaceIndex;
    if (lastFoundIndex != -1 && TGBackground.compare(lastFoundWallpaper, wallpaper))
      return lastFoundIndex;
    int i = 0;
    for (TGBackground w : wallpapers) {
      if (TGBackground.compare(wallpaper, w)) {
        lastFoundWallpaper = w;
        lastFoundIndex = i;
        return i;
      }
      i++;
    }
    return -1;
  }

  private void setSelected (TGBackground wallpaper, boolean isSelected) {
    int i = indexOfWallpaper(wallpaper);
    if (i != -1) {
      int foundCount = 0;

      for (RecyclerView recyclerView : attachedRecyclers) {
        View view = recyclerView.getLayoutManager().findViewByPosition(i);
        if (view != null && view instanceof WallpaperView && xgramSameTile(((WallpaperView) view).getWallpaper(), wallpaper)) {
          ((WallpaperView) view).setWallpaperSelected(isSelected);
          foundCount++;
        }
      }

      if (foundCount == 0 || foundCount < attachedRecyclers.size()) {
        notifyItemChanged(i);
      }
    }
  }

  private void setSelected (TGBackground wallpaper) {
    if (!TGBackground.compare(this.selectedWallpaper, wallpaper)) {
      TGBackground oldWallpaper = this.selectedWallpaper;
      this.selectedWallpaper = wallpaper;
      setSelected(oldWallpaper, false);
      setSelected(wallpaper, true);
      centerWallpapers(true);
    }
  }

  private static ArrayList<TGBackground> injectCustomWallpaper (Tdlib tdlib, List<TGBackground> wallpapers, int themeId) {
    TGBackground customWallpaper = null;
    try {
      TGBackground wallpaper = tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier());
      String path = null;
      if (wallpaper != null && wallpaper.isCustom()) {
        path = wallpaper.getCustomPath();
      }
      if (StringUtils.isEmpty(path)) {
        ImageFile thumb = Media.instance().getGalleryRepresentation();
        if (thumb != null) {
          path = thumb.getFilePath();
        } else {
          path = "custom";
        }
      }
      customWallpaper = new TGBackground(tdlib, path);
    } catch (Throwable t) {
      Log.w("Cannot add custom wallpaper option", t);
    }

    boolean needEmpty = Theme.getDefaultWallpaper() == null;
    boolean needCustom = customWallpaper != null;
    ArrayList<TGBackground> newList = new ArrayList<>(wallpapers.size() + (needCustom ? 1 : 0) + (needEmpty ? 1 : 0) + SOLID_COLORS.length);
    if (needCustom)
      newList.add(customWallpaper);
    if (needEmpty)
      newList.add(TGBackground.newEmptyWallpaper(tdlib));
    boolean isDark = Theme.isDarkTheme(themeId);
    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    for (TGBackground w : wallpapers) {
      // XGram: Telegram's bare pattern library comes with the installed backgrounds; with no colors and no intensity
      // each one is a plain black square (Telegram Desktop hides them too). With Expressive design a black inverted
      // pattern also draws plain black, and the surface tile stands for it. A black pattern whose lines show (one
      // installed from a t.me/bg link) stays and draws as itself. The stored one still shows through replaceWallpapers.
      if (xgramM3 ? com.fahadali.xgram.appearance.XgramWallpaperDraw.drawsPlainBlack(w) : (w.isColorlessPattern() && w.getPatternIntensity() == 0f))
        continue;
      if (xgramM3) {
        // Official Telegram hides the dark-theme (inverted) patterns in a light theme
        if (!isDark && w.isPatternInverted())
          continue;
        // No duplicate tiles: the server can list the same background twice
        boolean duplicate = false;
        for (TGBackground existing : newList) {
          if (TGBackground.compare(existing, w)) {
            duplicate = true;
            break;
          }
        }
        if (duplicate)
          continue;
      }
      newList.add(w);
    }
    int[] legacyWallpaperIds = TGBackground.getLegacyWallpaperIds();
    newList.ensureCapacity(newList.size() + legacyWallpaperIds.length);
    for (int legacyWallpaperId : legacyWallpaperIds) {
      TGBackground legacyBackground = TGBackground.newLegacyWallpaper(tdlib, legacyWallpaperId);
      if (legacyBackground == null)
        continue;
      boolean found = false;
      for (TGBackground existingBackground : newList) {
        if (StringUtils.equalsOrBothEmpty(existingBackground.getName(), legacyBackground.getName())) {
          found = true;
          existingBackground.setLegacyWallpaperId(legacyBackground.getLegacyWallpaperId());
          break;
        }
      }
      if (!found)
        newList.add(legacyBackground);
    }
    String defaultWallpaper = TdlibWallpaperManager.extractWallpaperName(Theme.getDefaultWallpaper(themeId));
    if (defaultWallpaper != null) {
      boolean found = false;
      for (TGBackground background : newList) {
        if (defaultWallpaper.equals(background.getName())) {
          found = true;
          break;
        }
      }
      if (!found) {
        if (defaultWallpaper.length() == 6) {
          try {
            int solidColor = Color.parseColor("#" + defaultWallpaper);
            newList.add(new TGBackground(tdlib, solidColor));
            found = true;
          } catch (IllegalArgumentException ignored) { }
        }
        if (!found) {
          newList.add(TGBackground.newUnknownWallpaper(tdlib, defaultWallpaper));
        }
      }
    }
    Collections.sort(newList, (a, b) -> {
      if (a.isCustom() != b.isCustom())
        return a.isCustom() ? -1 : 1;
      if (a.isEmpty() != b.isEmpty())
        return a.isEmpty() ? -1 : 1;
      if (defaultWallpaper != null) {
        boolean d1 = defaultWallpaper.equals(a.getName());
        boolean d2 = defaultWallpaper.equals(b.getName());
        if (d1 != d2)
          return d1 ? -1 : 1;
      }
      if (!isDark) {
        if (a.isBuiltIn() != b.isBuiltIn())
          return a.isBuiltIn() ? -1 : 1;
      }
      if (a.isFill() != b.isFill())
        return a.isFill() ? 1 : -1;
      if (!a.isFill()) {
        if (a.isLegacy() != b.isLegacy())
          return a.isLegacy() ? 1 : -1;
        if (a.isLegacy()) {
          if (isDark) {
            if (a.isBuiltIn() != b.isBuiltIn())
              return a.isBuiltIn() ? -1 : 1;
            if (a.isCat() != b.isCat())
              return a.isCat() ? -1 : 1;
          }
          int s1 = TGBackground.getScore(a.getLegacyWallpaperId(), isDark);
          int s2 = TGBackground.getScore(b.getLegacyWallpaperId(), isDark);
          if (s1 != s2)
            return s1 < s2 ? 1 : -1;
        }
      }
      return 0;
    });
    final int startIndex = newList.size() - 1;
    for (int i = SOLID_COLORS.length - 1; i >= 0; i--) {
      int color = SOLID_COLORS[i];
      int checkIndex = startIndex;
      boolean found = false;
      TGBackground check;
      while (checkIndex >= 0) {
        check = newList.get(checkIndex--);
        // XGram: getBackgroundColor() is opaque ARGB and the presets are RGB, so compare the RGB (no duplicate tiles)
        if (check.isFillSolid() && (xgramM3 ? (check.getBackgroundColor() & 0xFFFFFF) == color : check.getBackgroundColor() == color)) {
          found = true;
          break;
        }
      }
      if (!found) {
        newList.add(new TGBackground(tdlib, color));
      }
    }
    return newList;
  }

  private void setWallpapers (final @Nullable List<TGBackground> wallpapers) {
    if (wallpapers == null) {
      return;
    }
    Media.instance().post(() -> {
      final ArrayList<TGBackground> list = injectCustomWallpaper(context.tdlib(), wallpapers, themeId);
      UI.post(() -> replaceWallpapers(list));
    });
  }

  private void replaceWallpapers (ArrayList<TGBackground> wallpapers) {
    int oldItemCount = getItemCount();
    this.wallpapers = wallpapers;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: one surface tile; the empty and default tiles sort first, so the first one stays
      boolean surfaceSeen = false;
      for (int i = 0; i < wallpapers.size(); ) {
        if (xgramIsSurface(wallpapers.get(i))) {
          if (surfaceSeen) {
            wallpapers.remove(i);
            continue;
          }
          surfaceSeen = true;
        }
        i++;
      }
    }
    this.lastFoundIndex = -1; // XGram: the cached index belongs to the old list
    xgramFindSurface();
    this.selectedWallpaper = context.tdlib().settings().getWallpaper(Theme.getWallpaperIdentifier());
    int index = indexOfWallpaper(selectedWallpaper);
    if (index == -1) {
      index = 0;
      while (wallpapers.size() > index) {
        TGBackground wallpaper = wallpapers.get(index);
        if (!wallpaper.isCustom() && !wallpaper.isEmpty()) {
          break;
        }
        index++;
      }
      wallpapers.add(index, selectedWallpaper);
      this.lastFoundIndex = -1;
      xgramFindSurface(); // the insertion can move the surface tile
    }
    U.notifyItemsReplaced(this, oldItemCount);
    centerWallpapers(false, index);
  }

  private static int calculateTotalScrollX (RecyclerView recyclerView, LinearLayoutManager manager, int cellSize, int spacing) {
    int i = manager.findFirstVisibleItemPosition();
    int scrollX = (cellSize + spacing) * i;
    View view = manager.findViewByPosition(i);
    if (view != null) {
      if (Lang.rtl()) {
        scrollX += manager.getDecoratedRight(view) - recyclerView.getMeasuredWidth();
      } else {
        scrollX -= manager.getDecoratedLeft(view);
      }
    }
    return scrollX;
  }

  public void centerWallpapers (boolean smooth, int position) {
    for (RecyclerView recyclerView : attachedRecyclers) {
      LinearLayoutManager manager = ((LinearLayoutManager) recyclerView.getLayoutManager());
      if (recyclerView.getMeasuredWidth() == 0) {
        manager.scrollToPosition(position);
        continue;
      }
      int centerX = recyclerView.getMeasuredWidth() / 2;
      int cellSize = xgramCellSize();
      int spacing = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? Screen.dp(WallpaperView.XGRAM_TILE_GAP) : Screen.dp(3f); // XGram M3E: the 96dp tiles 8dp apart
      if (smooth) {
        int scrollX = calculateTotalScrollX(recyclerView, manager, cellSize, spacing);
        int availScrollX = spacing + (cellSize + spacing) * wallpapers.size() - recyclerView.getMeasuredWidth();
        int desiredScrollX = Math.max(0, Math.min(availScrollX, (cellSize + spacing) * position - centerX + cellSize / 2 + spacing));
        if (desiredScrollX != scrollX) {
          recyclerView.stopScroll();
          recyclerView.smoothScrollBy(Lang.rtl() ? scrollX - desiredScrollX : desiredScrollX - scrollX, 0);
        }
        continue;
      }
      manager.scrollToPositionWithOffset(position, centerX - cellSize / 2 - spacing);
    }
  }

  /** XGram: the width of a tile (Telegram X's 105dp square, or the 96dp M3E tile). */
  private static int xgramCellSize () {
    return Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? WallpaperView.XGRAM_TILE_WIDTH : 105f);
  }

  public void centerWallpapers (boolean smooth) {
    int position = indexOfWallpaper(selectedWallpaper);
    if (position != -1) {
      centerWallpapers(smooth, position);
    }
  }

  @Override
  public ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return ViewHolder.create(context.context(), viewType, this, null);
  }

  @Override
  public void onViewAttachedToWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).attach();
        break;
      }
      case TYPE_WALLPAPER: {
        ((WallpaperView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).detach();
        break;
      }
      case TYPE_WALLPAPER: {
        ((WallpaperView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public void onBindViewHolder (ViewHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case TYPE_WALLPAPER: {
        if (wallpapers != null) {
          TGBackground wallpaper = wallpapers.get(position);
          // XGram: the surface tile is selected for any background the chat draws as its plain surface
          ((WallpaperView) holder.itemView).setWallpaper(wallpaper, xgramSameTile(wallpaper, selectedWallpaper), xgramIsSurface(wallpaper));
        }
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    return wallpapers != null ? wallpapers.size() : 1;
  }

  @Override
  public int getItemViewType (int position) {
    if (wallpapers != null) {
      return TYPE_WALLPAPER;
    } else {
      return TYPE_PROGRESS;
    }
  }

  @Override
  public void onClick (View v) {
    if (!(v instanceof WallpaperView)) {
      return;
    }

    TGBackground wallpaper = ((WallpaperView) v).getWallpaper();
    if (wallpaper != null) {
      if (wallpaper.isCustom()) {
        Intents.openGallery(context.context(), false);
      } else {
        context.tdlib().settings().setWallpaper(wallpaper, true, Theme.getWallpaperIdentifier());
      }
    }
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) { }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageId) {
    if (this.context.tdlib() != tdlib || Theme.getWallpaperIdentifier() != usageId) {
      return;
    }
    if (wallpapers != null && !wallpapers.isEmpty() && wallpaper != null && wallpaper.isCustom() && !TGBackground.compare(wallpapers.get(0), wallpaper)) {
      TGBackground oldWallpaper = wallpapers.get(0);
      wallpapers.set(0, wallpaper);
      xgramFindSurface();
      int i = indexOfWallpaper(wallpaper);
      if (i != -1) {
        int foundCount = 0;
        for (RecyclerView recyclerView : attachedRecyclers) {
          View view = recyclerView.getLayoutManager().findViewByPosition(i);
          if (view != null && view instanceof WallpaperView && TGBackground.compare(((WallpaperView) view).getWallpaper(), oldWallpaper)) {
            ((WallpaperView) view).replaceWallpaper(wallpaper);
            foundCount++;
          }
        }
        if (foundCount != attachedRecyclers.size()) {
          notifyItemChanged(i);
        }
      }
    }
    setSelected(wallpaper);
  }

  public static class WallpaperView extends SparseDrawableView implements FactorAnimator.Target, Destroyable {
    private final ImageReceiver preview, receiver;
    private boolean isSelected;

    public WallpaperView (Context context) {
      super(context);
      this.preview = new ImageReceiver(this, 0);
      this.receiver = new ImageReceiver(this, 0);
    }

    public TGBackground getWallpaper () {
      return wallpaper;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (xgramM3) {
        // XGram M3E: a 96 x 136dp portrait tile
        setMeasuredDimension(Screen.dp(XGRAM_TILE_WIDTH), Screen.dp(XGRAM_TILE_HEIGHT));
      } else
      super.onMeasure(heightMeasureSpec, heightMeasureSpec);
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      preview.setBounds(0, 0, width, height);
      receiver.setBounds(0, 0, width, height);
    }

    // XGram M3E: the wallpaper picker's tiles: 16dp corners, the selected one with a 3dp primary outline and a check
    // badge, the gallery tile in surfaceContainerHighest with its icon over a labelMedium "Gallery"

    public static final float XGRAM_TILE_WIDTH = 96f, XGRAM_TILE_HEIGHT = 136f, XGRAM_TILE_GAP = 8f, XGRAM_TILE_INSET = 16f;

    private final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    private final android.graphics.Path xgramClip = new android.graphics.Path();
    private final android.graphics.Path xgramCheck = new android.graphics.Path();
    private final android.graphics.RectF xgramRect = new android.graphics.RectF();
    private final Paint xgramStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int xgramClipWidth, xgramClipHeight;
    private android.text.TextPaint xgramLabelPaint;
    private android.text.StaticLayout xgramLabelLayout;
    private String xgramLabelText;
    private android.graphics.Typeface xgramLabelTypeface;
    private int xgramLabelWidth, xgramLabelBand, xgramLabelIconBlock;
    private boolean xgramLabelShowIcon;
    private float xgramLabelSize;
    private boolean xgramSurface;

    private void xgramBuildShape (int width, int height) {
      if (xgramClipWidth == width && xgramClipHeight == height) {
        return;
      }
      xgramClipWidth = width;
      xgramClipHeight = height;
      final float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L);
      xgramRect.set(0, 0, width, height);
      xgramClip.rewind();
      xgramClip.addRoundRect(xgramRect, radius, radius, android.graphics.Path.Direction.CW);
    }

    private void xgramDrawGallery (Canvas c) {
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      c.drawColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST));
      final int content = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
      xgramDrawIconLabel(c, R.drawable.baseline_image_24, Lang.getString(R.string.Gallery), content);
    }

    /**
     * XGram M3E: the surface tile, the chat's plain surface (no wallpaper, the theme's own default, Telegram's black
     * library patterns): the surface color the chat draws, a 1dp outlineVariant edge so it still reads as a tile on
     * a strip of the same color (black with Pure black), and what it is: "Pure black" with a moon, or "No wallpaper".
     */
    private void xgramDrawSurface (Canvas c) {
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      c.drawColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE));
      final float stroke = Screen.dp(1f);
      xgramStroke.setStyle(Paint.Style.STROKE);
      xgramStroke.setStrokeWidth(stroke);
      xgramStroke.setColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.OUTLINE_VARIANT));
      xgramRect.set(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f);
      final float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L) - stroke / 2f;
      c.drawRoundRect(xgramRect, radius, radius, xgramStroke);
      final boolean pureBlack = com.fahadali.xgram.appearance.XgramAmoled.isActive();
      final int content = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
      xgramDrawIconLabel(c, pureBlack ? R.drawable.baseline_brightness_2_24 : R.drawable.baseline_block_24, Lang.getString(pureBlack ? R.string.XgramPureBlack : R.string.XgramWallpaperNone), content);
    }

    /**
     * The label centred in {@code width}, ellipsized, fitted in the {@code band} together with the icon above it
     * ({@code iconBlock} tall): two lines with the icon, then two lines without it, and only then one ellipsized
     * line (with the icon when it still fits), so the icon goes before the text is cut. Whether the icon shows is
     * left in {@link #xgramLabelShowIcon}. Cached, so it is rebuilt only when the text, the sizes or the font change.
     */
    private android.text.StaticLayout xgramLabelLayout (String text, int width, int band, int iconBlock, android.graphics.Typeface typeface, float textSize) {
      if (xgramLabelLayout != null && xgramLabelWidth == width && xgramLabelBand == band && xgramLabelIconBlock == iconBlock && xgramLabelSize == textSize && xgramLabelTypeface == typeface && text.equals(xgramLabelText)) {
        return xgramLabelLayout;
      }
      final android.text.StaticLayout two = xgramBuildLabel(text, width, 2);
      android.text.StaticLayout layout;
      final boolean showIcon;
      if (two.getHeight() <= band - iconBlock) {
        layout = two;
        showIcon = true;
      } else if (two.getHeight() <= band) {
        layout = two;
        showIcon = false;
      } else {
        layout = xgramBuildLabel(text, width, 1);
        showIcon = iconBlock + layout.getHeight() <= band;
      }
      xgramLabelLayout = layout;
      xgramLabelShowIcon = showIcon;
      xgramLabelText = text;
      xgramLabelWidth = width;
      xgramLabelBand = band;
      xgramLabelIconBlock = iconBlock;
      xgramLabelSize = textSize;
      xgramLabelTypeface = typeface;
      return layout;
    }

    private android.text.StaticLayout xgramBuildLabel (String text, int width, int maxLines) {
      final android.text.StaticLayout layout;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), xgramLabelPaint, width)
          .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
          .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
          .setIncludePad(false)
          .setMaxLines(maxLines)
          .setEllipsize(android.text.TextUtils.TruncateAt.END)
          .build();
      } else {
        // No line limit before Android 6: one ellipsized line
        final CharSequence line = android.text.TextUtils.ellipsize(text, xgramLabelPaint, width, android.text.TextUtils.TruncateAt.END);
        //noinspection deprecation
        layout = new android.text.StaticLayout(line, xgramLabelPaint, width, android.text.Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
      }
      return layout;
    }

    /**
     * A 24dp icon over a labelMedium label (sp, so it follows the font size), centred in the tile but kept between
     * the check badge (8dp inset, 24dp) and 12dp above the bottom: a long or large label takes two lines, and the
     * icon goes before the text is cut to one line, so the text never overlaps the badge and is cut only last.
     */
    private void xgramDrawIconLabel (Canvas c, int iconRes, String text, int color) {
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      if (xgramLabelPaint == null) {
        xgramLabelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      }
      final android.graphics.Typeface typeface = com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, false);
      final float textSize = Screen.sp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M));
      xgramLabelPaint.setTypeface(typeface);
      xgramLabelPaint.setTextSize(textSize);
      xgramLabelPaint.setColor(color);
      final int available = Math.max(1, width - Screen.dp(16f));
      final float bandTop = Screen.dp(40f), bandBottom = height - Screen.dp(12f);
      final Drawable icon = getSparseDrawable(iconRes, 0);
      final float iconBlock = icon.getMinimumHeight() + Screen.dp(8f);
      final android.text.StaticLayout layout = xgramLabelLayout(text, available, (int) (bandBottom - bandTop), (int) iconBlock, typeface, textSize);
      final boolean showIcon = xgramLabelShowIcon;
      final float block = (showIcon ? iconBlock : 0f) + layout.getHeight();
      float top = Math.max(bandTop, Math.min((height - block) / 2f, bandBottom - block));
      if (showIcon) {
        Drawables.draw(c, icon, width / 2f - icon.getMinimumWidth() / 2f, top, Paints.getPorterDuffPaint(color));
        top += iconBlock;
      }
      c.save();
      c.translate((width - available) / 2f, top);
      layout.draw(c);
      c.restore();
    }

    private void xgramDrawSelection (Canvas c, float factor) {
      if (factor <= 0f) {
        return;
      }
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      final int primary = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
      // The outline, inside the tile's edge
      final float strokeWidth = Screen.dp(3f);
      xgramStroke.setStyle(Paint.Style.STROKE);
      xgramStroke.setStrokeWidth(strokeWidth);
      xgramStroke.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(primary, factor));
      xgramRect.set(strokeWidth / 2f, strokeWidth / 2f, width - strokeWidth / 2f, height - strokeWidth / 2f);
      final float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L) - strokeWidth / 2f;
      c.drawRoundRect(xgramRect, radius, radius, xgramStroke);
      // The check badge: a 24dp primary circle 8dp from the top end corner, scaled in with the selection
      final float badge = Screen.dp(24f), inset = Screen.dp(8f);
      final float cx = Lang.rtl() ? inset + badge / 2f : width - inset - badge / 2f, cy = inset + badge / 2f;
      c.save();
      c.scale(factor, factor, cx, cy);
      xgramStroke.setStyle(Paint.Style.FILL);
      xgramStroke.setColor(primary);
      c.drawCircle(cx, cy, badge / 2f, xgramStroke);
      xgramCheck.rewind();
      xgramCheck.moveTo(cx - Screen.dp(5f), cy + Screen.dp(.5f));
      xgramCheck.lineTo(cx - Screen.dp(1.5f), cy + Screen.dp(4f));
      xgramCheck.lineTo(cx + Screen.dp(5.5f), cy - Screen.dp(3.5f));
      xgramStroke.setStyle(Paint.Style.STROKE);
      xgramStroke.setStrokeWidth(Screen.dp(2f));
      xgramStroke.setStrokeCap(Paint.Cap.ROUND);
      xgramStroke.setStrokeJoin(Paint.Join.ROUND);
      xgramStroke.setColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY));
      c.drawPath(xgramCheck, xgramStroke);
      c.restore();
    }

    private TGBackground wallpaper;

    public void setWallpaper (TGBackground wallpaper, boolean isSelected) {
      setWallpaper(wallpaper, isSelected, false);
    }

    /** @param surface XGram M3E: drawn as the surface tile ({@link #xgramDrawSurface}), so nothing is loaded */
    public void setWallpaper (TGBackground wallpaper, boolean isSelected, boolean surface) {
      this.wallpaper = wallpaper;
      this.xgramSurface = surface;
      if (xgramM3) {
        // The labelled tiles say what they are to TalkBack too
        setContentDescription(surface ? Lang.getString(com.fahadali.xgram.appearance.XgramAmoled.isActive() ? R.string.XgramPureBlack : R.string.XgramWallpaperNone) : wallpaper != null && wallpaper.isCustom() ? Lang.getString(R.string.Gallery) : null);
      }
      if (wallpaper != null && !surface) {
        if (wallpaper.isPattern()) {
          int color = wallpaper.getPatternColor();
          receiver.setPorterDuffColorFilter(color);
          preview.requestFile(null);
        } else {
          receiver.disablePorterDuffColorFilter();
          preview.requestFile(wallpaper.getPreview(true));
        }
        receiver.requestFile(wallpaper.getPreview(false));
      } else {
        preview.requestFile(null);
        receiver.requestFile(null);
      }
      setSelected(isSelected, false);
    }

    public void replaceWallpaper (TGBackground wallpaper) {
      this.wallpaper = wallpaper;
      this.xgramSurface = false; // only the gallery tile is replaced
      preview.requestFile(wallpaper.getPreview(true));
      receiver.requestFile(wallpaper.getPreview(false));
    }

    public void attach () {
      preview.attach();
      receiver.attach();
    }

    public void detach () {
      preview.detach();
      receiver.detach();
    }

    @Override
    public void performDestroy () {
      preview.destroy();
      receiver.destroy();
    }

    public void setWallpaperSelected (boolean isSelected) {
      setSelected(isSelected, true);
    }

    private void setSelected (boolean isSelected, boolean animated) {
      if (this.isSelected != isSelected) {
        this.isSelected = isSelected;
        if (animated) {
          animateFactor(isSelected ? 1f : 0f);
        } else {
          forceFactor(isSelected ? 1f : 0f);
        }
      }
    }

    private float factor;

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    private FactorAnimator animator;

    private void animateFactor (float factor) {
      if (this.animator == null) {
        // XGram: the selection springs in (SPATIAL_FAST, clamped: it also drives alpha) with Expressive design
        this.animator = new FactorAnimator(0, this, com.fahadali.xgram.appearance.XgramAppMotion.interpClamped(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, AnimatorUtils.DECELERATE_INTERPOLATOR), com.fahadali.xgram.appearance.XgramAppMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 180l), this.factor);
      }
      animator.animateTo(factor);
    }

    private void forceFactor (float factor) {
      if (animator != null) {
        animator.forceFactor(factor);
      }
      setFactor(factor);
    }

    private final DrawAlgorithms.GradientCache cache = new DrawAlgorithms.GradientCache();

    @Override
    protected void onDraw (Canvas c) {
      if (xgramM3) {
        xgramBuildShape(getMeasuredWidth(), getMeasuredHeight());
        c.save();
        c.clipPath(xgramClip);
        if (wallpaper != null && wallpaper.isCustom()) {
          xgramDrawGallery(c);
        } else if (xgramSurface) {
          xgramDrawSurface(c);
        } else {
          drawContent(c, true);
        }
        c.restore();
        xgramDrawSelection(c, isSelected ? factor : 0f);
        return;
      }
      drawContent(c, false);
    }

    /**
     * XGram M3E: a pattern tile drawn as the chat draws it. A dark-theme (inverted) pattern is black with its colors
     * showing through the lines, and the M3E placeholder until its pattern loads (black alone would look empty).
     */
    private void xgramDrawPattern (Canvas c) {
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      if (com.fahadali.xgram.appearance.XgramWallpaperDraw.drawsInverted(wallpaper, Theme.isDark())) {
        if (!receiver.isLoaded()) {
          com.fahadali.xgram.appearance.XgramWallpaperDraw.drawLoading(c, this, 0, 0, width, height);
        } else {
          com.fahadali.xgram.appearance.XgramWallpaperDraw.drawInverted(c, 0, 0, width, height, 1f, receiver, cache, wallpaper);
        }
        return;
      }
      com.fahadali.xgram.appearance.XgramWallpaperDraw.drawFill(c, cache, 0, 0, width, height, wallpaper, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE), 1f);
      receiver.setPorterDuffColorFilter(wallpaper.getPatternColor()); // every draw: the inverted path leaves it white
      final float alpha = wallpaper.getPatternIntensity();
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha * receiver.getPaintAlpha());
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }

    private void drawContent (Canvas c, boolean xgramM3) {
      if (wallpaper == null || wallpaper.isEmpty()) {
        c.drawColor(xgramM3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE) : Theme.backgroundColor());
      } else if (xgramM3 && wallpaper.isFill()) {
        // XGram: the chat's own fill (a freeform gradient over its base color), so the tile is what the chat shows
        com.fahadali.xgram.appearance.XgramWallpaperDraw.drawFill(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE), 1f);
      } else if (xgramM3 && wallpaper.isPattern()) {
        xgramDrawPattern(c);
      } else if (wallpaper.isFillSolid()) {
        c.drawColor(wallpaper.getBackgroundColor());
      } else if (wallpaper.isFillGradient()) {
        DrawAlgorithms.drawGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), 1f);
      } else if (wallpaper.isFillFreeformGradient()) {
        DrawAlgorithms.drawMulticolorGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getFreeformColors(), 1f);
      } else if (wallpaper.isPattern()) {
        if (wallpaper.isPatternBackgroundGradient()) {
          DrawAlgorithms.drawGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), 1f);
        } else if (wallpaper.isPatternBackgroundFreeformGradient()) {
          DrawAlgorithms.drawMulticolorGradient(c, cache, 0, 0, getMeasuredWidth(), getMeasuredHeight(), wallpaper.getFreeformColors(), 1f);
        } else {
          c.drawColor(wallpaper.getBackgroundColor());
        }
        float alpha = wallpaper.getPatternIntensity();
        if (alpha != 1f) {
          receiver.setPaintAlpha(alpha * receiver.getPaintAlpha());
        }
        receiver.draw(c);
        if (alpha != 1f) {
          receiver.restorePaintAlpha();
        }
      } else {
        if (receiver.needPlaceholder()) {
          if (preview.needPlaceholder()) {
            if (xgramM3) {
              com.fahadali.xgram.appearance.XgramWallpaperDraw.drawLoading(c, this, 0, 0, getMeasuredWidth(), getMeasuredHeight()); // XGram M3E: never a flat or black square
            } else {
              preview.drawPlaceholder(c);
            }
          }
          preview.draw(c);
        }
        receiver.draw(c);
      }
      if (xgramM3) {
        return; // the selection and the gallery tile are drawn by onDraw
      }

      float checkFactor = isSelected ? factor : 1f;

      int centerX = getMeasuredWidth() / 2;
      int centerY = getMeasuredHeight() / 2;

      boolean isCustom = wallpaper != null && wallpaper.isCustom();
      float circleFactor = isCustom ? 1f : factor;

      if (circleFactor != 0f) {
        // TODO better color
        c.drawCircle(centerX, centerY, Screen.dp(28f), Paints.fillingPaint(ColorUtils.color((int) (86f * circleFactor), 0)));

        if (isCustom) {
          Paint paint = Paints.whitePorterDuffPaint();
          paint.setAlpha((int) (255f * (1f - factor)));
          Drawable drawable = getSparseDrawable(R.drawable.baseline_image_24, 0);
          Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2, centerY - drawable.getMinimumHeight() / 2, paint);
          paint.setAlpha(255);
        }

        final float fx = checkFactor <= .3f ? 0f : (checkFactor - .3f) / .7f;

        if (fx > 0) {
          final int checkColor = ColorUtils.color((int) (255f * factor), 0xffffffff);

          final float t1;
          final float f1, f2;

          t1 = .3f;
          f1 = fx <= t1 ? fx / t1 : 1f;
          f2 = fx <= t1 ? 0f : (fx - t1) / (1f - t1);

          // check
          c.save();

          c.translate(centerX / 2 + Screen.dp(13f), centerY + Screen.dp(1f));
          c.rotate(-45f);

          final int w2max, h1max;

          w2max = Screen.dp(14f);
          h1max = Screen.dp(7f);

          final int w2 = (int) ((float) w2max * f2);
          final int h1 = (int) ((float) h1max * f1);

          final int x1, y1;

          x1 = Screen.dp(4f);
          y1 = Screen.dp(11f);

          int lineSize = Screen.dp(2f);
          c.drawRect(x1, y1 - h1max, x1 + lineSize, y1 - h1max + h1, Paints.fillingPaint(checkColor));
          c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(checkColor));

          c.restore();
        }
      }
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder (View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
      switch (viewType) {
        case TYPE_WALLPAPER: {
          WallpaperView view = new WallpaperView(context);
          view.setOnClickListener(onClickListener);
          view.setOnLongClickListener(onLongClickListener);
          return new ViewHolder(view);
        }
        case TYPE_PROGRESS: {
          ProgressComponentView view = new ProgressComponentView(context);
          view.initBig(1f);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new ViewHolder(view);
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }
}
