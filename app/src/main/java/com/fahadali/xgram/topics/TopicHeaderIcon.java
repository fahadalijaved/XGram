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
package com.fahadali.xgram.topics;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramShapes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.emoji.AnimatedEmojiDrawable;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * A forum topic's icon drawn instead of a header cell's avatar, as in Telegram: in the topic's chat and in its
 * profile. Both header cells must show the same thing, because Telegram X swaps one for the other during back
 * gestures (shared header), so a group photo in one of them would flash in the other.
 *
 * <p>XGram M3E: with Expressive design every topic icon sits in the General topic's tonal container (a 9-sided
 * cookie in secondaryContainer, the avatar's size) with the icon centred at 60% of it, so a custom emoji icon (often
 * a dark glyph such as the music note, invisible on a dark app bar) and a default bubble read like the General
 * topic's "#" instead of a bare glyph.
 */
public final class TopicHeaderIcon implements Destroyable {
  private final ComplexHeaderView cell;
  private boolean active;
  private long emojiId;
  @Nullable private AnimatedEmojiDrawable emoji;
  @Nullable private Drawable emojiIcon; // the emoji as set on the cell (in its container with Expressive design)

  public TopicHeaderIcon (ComplexHeaderView cell) {
    this.cell = cell;
  }

  /** info == null: the default bubble until the topic is known. */
  public void set (Tdlib tdlib, @Nullable TdApi.ForumTopicInfo info) {
    active = true;
    long customEmojiId = info != null && info.icon != null && !info.isGeneral ? info.icon.customEmojiId : 0;
    if (customEmojiId != 0 && customEmojiId == emojiId && emoji != null) {
      cell.xgramSetAvatarDrawable(emojiIcon != null ? emojiIcon : emoji); // same emoji: no bubble in between
      return;
    }
    destroyEmoji();
    emojiId = customEmojiId;
    final boolean isGeneral = info != null && info.isGeneral;
    final Drawable bubble = info != null ? new ForumTopicIconDrawable(info) : new ForumTopicIconDrawable(false, 0x6FB9F0, null);
    // The General topic's icon draws its own container
    cell.xgramSetAvatarDrawable(isGeneral ? bubble : wrap(bubble));
    if (customEmojiId == 0) {
      return;
    }
    tdlib.emoji().findOrRequest(customEmojiId, (entry, inPlace) -> {
      Runnable act = () -> {
        if (!active || emojiId != customEmojiId || emoji != null || entry.value == null) {
          return;
        }
        emoji = new AnimatedEmojiDrawable(cell);
        emoji.setSticker(new TGStickerObj(tdlib, entry.value, null, entry.value.fullType), false);
        emoji.attach();
        emojiIcon = wrap(emoji);
        cell.xgramSetAvatarDrawable(emojiIcon);
      };
      if (inPlace) {
        act.run();
      } else {
        tdlib.ui().post(act);
      }
    });
  }

  /** The avatar again. */
  public void clear () {
    if (!active) {
      return;
    }
    active = false;
    emojiId = 0;
    destroyEmoji();
    cell.xgramSetAvatarDrawable(null);
  }

  private void destroyEmoji () {
    if (emoji != null) {
      emoji.performDestroy();
      emoji = null;
    }
    emojiIcon = null;
  }

  @Override
  public void performDestroy () {
    clear();
  }

  private static Drawable wrap (Drawable icon) {
    return XgramExpressive.isEnabled() ? new TonalContainer(icon) : icon;
  }

  /**
   * The General topic's container around any topic icon: the 9-sided cookie in secondaryContainer filling the
   * bounds, the icon at {@link #ICON_FRACTION} of them in the middle. The cookie is built once at a base size and
   * scaled on the canvas, so a header growing or moving (profile expand, back gesture) allocates nothing per frame.
   */
  private static final class TonalContainer extends Drawable {
    private static final float ICON_FRACTION = .6f;
    private static final float BASE_SIZE = 100f;
    @Nullable private static Path baseShape;

    private final Drawable icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;

    TonalContainer (Drawable icon) {
      this.icon = icon;
    }

    private static Path shape () {
      if (baseShape == null) {
        Path path = new Path();
        XgramShapes.toPath(XgramShapes.COOKIE_9, path);
        XgramShapes.fit(path, 0f, 0f, BASE_SIZE, 0f, new Matrix());
        baseShape = path;
      }
      return baseShape;
    }

    @Override
    protected void onBoundsChange (@NonNull Rect bounds) {
      super.onBoundsChange(bounds);
      final int size = Math.min(bounds.width(), bounds.height());
      final int iconSize = Math.round(size * ICON_FRACTION);
      final int left = bounds.centerX() - iconSize / 2, top = bounds.centerY() - iconSize / 2;
      icon.setBounds(left, top, left + iconSize, top + iconSize);
    }

    @Override
    public void draw (@NonNull Canvas c) {
      final Rect bounds = getBounds();
      final float size = Math.min(bounds.width(), bounds.height());
      if (size <= 0f) {
        return;
      }
      paint.setColor(ColorUtils.alphaColor(alpha / 255f, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER)));
      final int save = c.save();
      c.translate(bounds.exactCenterX(), bounds.exactCenterY());
      final float scale = size / BASE_SIZE;
      c.scale(scale, scale);
      c.drawPath(shape(), paint);
      c.restoreToCount(save);
      icon.draw(c);
    }

    @Override
    public void setAlpha (int alpha) {
      if (this.alpha != alpha) {
        this.alpha = alpha;
        icon.setAlpha(alpha);
        invalidateSelf();
      }
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
