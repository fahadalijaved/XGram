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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.emoji.EmojiToneListView;

/**
 * XGram M3E wave 2: the emoji tone picker (the long press on an emoji) as a Material 3 Expressive menu surface instead
 * of Telegram X's bubble with a pointer: one surfaceContainer container around all rows (tones, then the matching
 * custom emoji), 16dp corners (the Expressive menu shape, clamped to a pill), elevation level 2 (3dp) as a real view
 * elevation; the item under the finger is the selected menu item, secondaryContainer with 12dp corners.
 * <p>
 * The geometry (item size, rows, paddings, hit testing) is Telegram X's, so EmojiToneHelper positions and drives it
 * unchanged; only the drawing differs. Nothing is allocated while drawing.
 */
@SuppressLint("ViewConstructor")
public final class XgramW2ToneListView extends EmojiToneListView {
  /** Menu container corner (dp). */
  public static final float MENU_CORNER = XgramExpressive.CORNER_L;
  /** Selected item corner (dp). */
  public static final float ITEM_CORNER = XgramExpressive.CORNER_M;

  private final EmojiInfo[] xgramTones = new EmojiInfo[EmojiData.emojiColors.length];
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF menuRect = new RectF(), itemRect = new RectF(), outlineRect = new RectF();
  private final Rect emojiRect = new Rect();

  public XgramW2ToneListView (@NonNull Context context) {
    super(context);
    fillPaint.setStyle(Paint.Style.FILL);
    setWillNotDraw(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          if (!menuBounds(outlineRect)) {
            outline.setEmpty();
            return;
          }
          outline.setRoundRect(
            Math.round(outlineRect.left), Math.round(outlineRect.top), Math.round(outlineRect.right), Math.round(outlineRect.bottom),
            menuRadius(outlineRect)
          );
        }
      });
      setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
    }
  }

  @Override
  public void setEmoji (String emoji, String currentTone, int emojiColorState) {
    super.setEmoji(emoji, currentTone, emojiColorState);
    for (int i = 0; i < xgramTones.length; i++) {
      xgramTones[i] = Emoji.instance().getEmojiInfo(EmojiData.instance().colorize(emoji, EmojiData.emojiColors[i]));
    }
    xgramInvalidateOutline();
  }

  @Override
  public void setCustomEmoji (@Nullable org.drinkless.tdlib.TdApi.Sticker[] stickersFromLocal, @Nullable org.drinkless.tdlib.TdApi.Sticker[] stickersFromServer) {
    super.setCustomEmoji(stickersFromLocal, stickersFromServer);
    xgramInvalidateOutline();
  }

  @Override
  public void setAnchorView (View view, int offsetLeft) {
    super.setAnchorView(view, offsetLeft);
    xgramInvalidateOutline(); // the rows' x depends on the anchor
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    xgramInvalidateOutline();
  }

  private void xgramInvalidateOutline () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
  }

  private static float menuRadius (@NonNull RectF bounds) {
    return Math.min(Screen.dpf(MENU_CORNER), Math.min(bounds.width(), bounds.height()) / 2f);
  }

  /**
   * The menu container: the union of the rows' visible areas (Telegram X's bubbles without their shadow margins and
   * pointer): 4dp around the 36dp items.
   */
  private boolean menuBounds (@NonNull RectF out) {
    final int rows = getRowsCount();
    if (rows <= 0) {
      return false;
    }
    final int itemSize = Screen.dp(ITEM_SIZE), itemPadding = Screen.dp(ITEM_PADDING);
    float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
    for (int a = 0; a < rows; a++) {
      final int size = getRowSize(a);
      if (size <= 0) {
        continue;
      }
      final float x = getRowX(a) - itemPadding;
      left = Math.min(left, x);
      right = Math.max(right, x + itemSize * size + itemPadding * 2);
    }
    if (right <= left) {
      return false;
    }
    final float top = getRowY(0) - itemPadding;
    final float bottom = getRowY(rows - 1) + itemSize + itemPadding;
    out.set(left, top, right, bottom);
    return true;
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    if (menuBounds(menuRect)) {
      // Container
      fillPaint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER));
      final float radius = menuRadius(menuRect);
      c.drawRoundRect(menuRect, radius, radius, fillPaint);

      // Selected item
      final int itemSize = Screen.dp(ITEM_SIZE);
      final int row = getToneIndexVertical(), column = getToneIndex();
      if (row >= 0 && row < getRowsCount() && column >= 0 && column < getRowSize(row)) {
        final float x = getRowX(row) + itemSize * column;
        final float y = getRowY(row);
        itemRect.set(x, y, x + itemSize, y + itemSize);
        final float itemRadius = Math.min(Screen.dpf(ITEM_CORNER), itemSize / 2f);
        fillPaint.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
        c.drawRoundRect(itemRect, itemRadius, itemRadius, fillPaint);
      }

      // Tones (first row)
      if (hasToneEmoji()) {
        int x = getRowX(0);
        final int y = getRowY(0);
        final int drawSize = Screen.dp(EMOJI_DRAW_SIZE);
        for (EmojiInfo info : xgramTones) {
          final int cx = x + itemSize / 2, cy = y + itemSize / 2;
          emojiRect.set(cx - drawSize / 2, cy - drawSize / 2, cx - drawSize / 2 + drawSize, cy - drawSize / 2 + drawSize);
          Emoji.instance().draw(c, info, emojiRect);
          x += itemSize;
        }
      }
    }

    // Custom emoji rows (the sticker views)
    final long drawingTime = getDrawingTime();
    for (int i = 0; i < getChildCount(); i++) {
      final View child = getChildAt(i);
      if (child.getVisibility() == View.VISIBLE || child.getAnimation() != null) {
        drawChild(c, child, drawingTime);
      }
    }
  }
}
